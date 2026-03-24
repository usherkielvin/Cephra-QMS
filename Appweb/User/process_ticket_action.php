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

// Get ticketId from POST request
$requestedTicketId = $_POST['ticketId'] ?? '';

if (!isset($_SESSION['pendingTicket'])) {
    echo json_encode(['error' => 'No pending ticket found. Please try again.']);
    exit();
}

$pendingTicket = $_SESSION['pendingTicket'];

// Validate that the ticket info is still valid
$ticketId = $pendingTicket['ticketId'];
$serviceType = $pendingTicket['serviceType'];
$queueServiceType = $pendingTicket['queueServiceType'];
$batteryLevel = $pendingTicket['batteryLevel'];
$priority = $pendingTicket['priority'];
$initialStatus = $pendingTicket['initialStatus'];

// Validate that the requested ticket ID matches the pending ticket ID
if ($requestedTicketId !== $ticketId) {
    unset($_SESSION['pendingTicket']);
    echo json_encode(['error' => 'Ticket ID mismatch. Please try again.']);
    exit();
}

// Re-check under a lock to prevent race conditions before inserting
try {
    $conn->beginTransaction();

    $stmt = $conn->prepare("SELECT username FROM users WHERE username = :username FOR UPDATE");
    $stmt->bindParam(':username', $username);
    $stmt->execute();

    $stmt = $conn->prepare("SELECT COUNT(*) FROM active_tickets WHERE username = :username");
    $stmt->bindParam(':username', $username);
    $stmt->execute();
    if ((int)$stmt->fetchColumn() > 0) {
        $conn->rollBack();
        unset($_SESSION['pendingTicket']);
        echo json_encode(['error' => 'You already have an active charging ticket. Please complete your current session first.']);
        exit();
    }

    $stmt = $conn->prepare(
        "SELECT COUNT(*) FROM queue_tickets
         WHERE username = :username AND status IN ('Waiting','Pending','Processing','In Progress')"
    );
    $stmt->bindParam(':username', $username);
    $stmt->execute();
    if ((int)$stmt->fetchColumn() > 0) {
        $conn->rollBack();
        unset($_SESSION['pendingTicket']);
        echo json_encode(['error' => 'You already have a ticket in queue. Please wait until it is processed.']);
        exit();
    }
} catch (Exception $e) {
    $conn->rollBack();
    unset($_SESSION['pendingTicket']);
    http_response_code(500);
    echo json_encode(['error' => 'Server error during validation.']);
    exit();
}

// Re-check battery level
$stmt = $conn->prepare("SELECT battery_level FROM battery_levels WHERE username = :username");
$stmt->bindParam(':username', $username);
$stmt->execute();
$result = $stmt->fetch(PDO::FETCH_ASSOC);
$currentBatteryLevel = (int)($result['battery_level'] ?? 0);

if ($currentBatteryLevel >= 100) {
    unset($_SESSION['pendingTicket']); // Clear pending ticket
    echo json_encode(['error' => 'Your battery is already 100%.']);
    exit();
}

// Create the ticket in queue_tickets (inside the open transaction)
$stmt = $conn->prepare("INSERT INTO queue_tickets (ticket_id, username, service_type, status, payment_status, initial_battery_level, priority) VALUES (:ticket_id, :username, :service_type, :status, '', :battery_level, :priority)");
$stmt->bindParam(':ticket_id', $ticketId);
$stmt->bindParam(':username', $username);
$stmt->bindParam(':service_type', $queueServiceType);
$stmt->bindParam(':status', $initialStatus);
$stmt->bindParam(':battery_level', $batteryLevel);
$stmt->bindParam(':priority', $priority);

if (!$stmt->execute()) {
    $errorInfo = $stmt->errorInfo();
    error_log("Failed to insert ticket: " . $errorInfo[2]);
    $conn->rollBack();
    unset($_SESSION['pendingTicket']);
    http_response_code(500);
    echo json_encode(['error' => 'Failed to create ticket', 'db_error' => $errorInfo[2]]);
    exit();
}

error_log("Successfully created ticket $ticketId in queue_tickets");

// If this is a priority ticket (Waiting status), add to waiting grid
if ($priority == 1) {
    try {
        $stmt = $conn->prepare("SELECT slot_number FROM waiting_grid WHERE ticket_id IS NULL ORDER BY slot_number LIMIT 1");
        $stmt->execute();
        $availableSlot = $stmt->fetchColumn();
        
        if ($availableSlot) {
            $stmt = $conn->prepare("UPDATE waiting_grid SET ticket_id = :ticket_id, username = :username, service_type = :service_type, initial_battery_level = :battery_level, position_in_queue = :slot WHERE slot_number = :slot");
            $stmt->bindParam(':ticket_id', $ticketId);
            $stmt->bindParam(':username', $username);
            $stmt->bindParam(':service_type', $queueServiceType);
            $stmt->bindParam(':battery_level', $batteryLevel);
            $stmt->bindParam(':slot', $availableSlot);
            $stmt->execute();
        }
    } catch (Exception $e) {
        error_log("Failed to add priority ticket to waiting grid: " . $e->getMessage());
    }
}

error_log("Successfully created ticket $ticketId");

// Set current service in session
$_SESSION['currentService'] = $serviceType;
$_SESSION['currentTicketId'] = $ticketId;

// Commit the transaction
$conn->commit();

// Clear the pending ticket from session since it's now created
unset($_SESSION['pendingTicket']);

// Respond success
echo json_encode([
    'success' => true,
    'message' => 'Ticket created successfully',
    'ticketId' => $ticketId,
    'serviceType' => $serviceType,
    'batteryLevel' => $batteryLevel
]);
exit();
?>
