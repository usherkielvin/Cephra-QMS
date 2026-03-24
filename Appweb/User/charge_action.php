<?php
ini_set('display_errors', 0);
ini_set('log_errors', 1);
error_reporting(E_ALL);

session_start();
if (!isset($_SESSION['username'])) {
    http_response_code(401);
    echo json_encode(['error' => 'Unauthorized']);
    exit();
}

require_once 'config/database.php';

header('Content-Type: application/json');

$db = new Database();
$conn = $db->getConnection();

if (!$conn) {
    http_response_code(500);
    echo json_encode(['error' => 'Database connection failed', 'details' => 'Check database configuration']);
    exit();
}

$username = $_SESSION['username'];

$serviceType = $_POST['serviceType'] ?? '';

// Debug logging
error_log("Charge action called - Username: $username, ServiceType: $serviceType");
error_log("POST data: " . json_encode($_POST));

// If no serviceType provided, return error
if (empty($serviceType)) {
    http_response_code(400);
    echo json_encode(['error' => 'Service type not provided']);
    exit();
}

if (!in_array($serviceType, ['Normal Charging', 'Fast Charging'])) {
    http_response_code(400);
    echo json_encode(['error' => 'Invalid service type']);
    exit();
}

// --- Rate limiting: one active or queued ticket per user at a time ---
// Use a transaction + SELECT FOR UPDATE to prevent race conditions from double-submits.
$conn->beginTransaction();

try {
    // Lock the user row to prevent concurrent submissions
    $stmt = $conn->prepare("SELECT username FROM users WHERE username = :username FOR UPDATE");
    $stmt->bindParam(':username', $username);
    $stmt->execute();

    // Check active_tickets
    $stmt = $conn->prepare("SELECT COUNT(*) FROM active_tickets WHERE username = :username");
    $stmt->bindParam(':username', $username);
    $stmt->execute();
    if ((int)$stmt->fetchColumn() > 0) {
        $conn->rollBack();
        echo json_encode(['error' => 'You already have an active charging ticket. Please complete your current session first.']);
        exit();
    }

    // Check queue_tickets for any non-terminal status
    $stmt = $conn->prepare(
        "SELECT COUNT(*) FROM queue_tickets
         WHERE username = :username AND status IN ('Waiting','Pending','Processing','In Progress')"
    );
    $stmt->bindParam(':username', $username);
    $stmt->execute();
    if ((int)$stmt->fetchColumn() > 0) {
        $conn->rollBack();
        echo json_encode(['error' => 'You already have a ticket in queue. Please wait until it is processed.']);
        exit();
    }
} catch (Exception $e) {
    $conn->rollBack();
    http_response_code(500);
    echo json_encode(['error' => 'Server error during validation.']);
    exit();
}
// Transaction stays open; we commit after inserting the ticket below.

// Check if car is linked by verifying car_index in users table
$stmt = $conn->prepare("SELECT car_index FROM users WHERE username = :username");
$stmt->bindParam(':username', $username);
$stmt->execute();
$result = $stmt->fetch(PDO::FETCH_ASSOC);
$carIndex = $result['car_index'] ?? null;

if ($carIndex === null || !is_numeric($carIndex) || $carIndex < 0 || $carIndex > 8) {
    echo json_encode(['error' => 'Please link your car first before charging.']);
    exit();
}

// Check battery level
$stmt = $conn->prepare("SELECT battery_level FROM battery_levels WHERE username = :username");
$stmt->bindParam(':username', $username);
$stmt->execute();
$result = $stmt->fetch(PDO::FETCH_ASSOC);
$batteryLevel = (int)($result['battery_level'] ?? 0);

if ($batteryLevel >= 100) {
    echo json_encode(['error' => 'Your battery is already 100%.']);
    exit();
}

// Determine service type for database storage
$bayType = ($serviceType === 'Fast Charging') ? 'Fast' : 'Normal';
// Queue ticket should mirror Phone Java panel labels
$queueServiceType = ($serviceType === 'Fast Charging') ? 'Fast Charging' : 'Normal Charging';

// Determine priority based on battery level (priority 1 for <20%, priority 0 for >=20%)
$priority = ($batteryLevel < 20) ? 1 : 0;

// Generate ticket ID using a single shared counter per service (Fast/Normal),
// counting across priority/non-priority and across queue, active, and history
$basePrefix = ($serviceType === 'Fast Charging') ? 'FCH' : 'NCH';
$ticketPrefix = ($priority == 1) ? $basePrefix . 'P' : $basePrefix;
$prefixPatternBase = $basePrefix . '%'; // includes both e.g. FCH### and FCHP###

// Compute the next number using the last 3 digits across all relevant tables
$sql = "SELECT GREATEST(
            IFNULL((SELECT MAX(CAST(RIGHT(ticket_id, 3) AS UNSIGNED)) FROM queue_tickets WHERE ticket_id LIKE :p1), 0),
            IFNULL((SELECT MAX(CAST(RIGHT(ticket_id, 3) AS UNSIGNED)) FROM active_tickets WHERE ticket_id LIKE :p2), 0),
            IFNULL((SELECT MAX(CAST(RIGHT(ticket_id, 3) AS UNSIGNED)) FROM charging_history WHERE ticket_id LIKE :p3), 0)
        ) AS max_num";
$stmt = $conn->prepare($sql);
$stmt->bindParam(':p1', $prefixPatternBase);
$stmt->bindParam(':p2', $prefixPatternBase);
$stmt->bindParam(':p3', $prefixPatternBase);
$stmt->execute();
$row = $stmt->fetch(PDO::FETCH_ASSOC);
$nextNum = (isset($row['max_num']) ? (int)$row['max_num'] : 0) + 1;
$ticketId = $ticketPrefix . str_pad($nextNum, 3, '0', STR_PAD_LEFT);

// Do NOT assign a bay at ticket creation time; mirror Java flow
// Ticket stays in Pending state until routed to a bay by Admin
$bayNumber = null;

// Determine initial status (mirror Java): priority -> Waiting, others -> Pending
$initialStatus = ($priority == 1) ? 'Waiting' : 'Pending';

// DO NOT create the ticket yet - just return validation info for preview popup
// Ticket will be created only when "Process Ticket" is clicked

// Store ticket info in session for later creation
$_SESSION['pendingTicket'] = [
    'ticketId' => $ticketId,
    'serviceType' => $serviceType,
    'queueServiceType' => $queueServiceType,
    'batteryLevel' => $batteryLevel,
    'priority' => $priority,
    'initialStatus' => $initialStatus
];

// Release the lock — no DB write yet (ticket created on confirm)
$conn->rollBack();

// Respond success with ticket details for preview popup
echo json_encode([
    'success' => true,
    'ticketId' => $ticketId,
    'serviceType' => $serviceType,
    'batteryLevel' => $batteryLevel,
    'isPreview' => true  // Indicate this is just a preview
]);
exit();
?>
