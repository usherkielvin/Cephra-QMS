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

$ticketId = $_POST['ticketId'] ?? '';
$cancelPending = $_POST['cancelPending'] ?? false;

// Handle pending ticket cancellation (when user cancels the preview popup)
if ($cancelPending) {
    if (isset($_SESSION['pendingTicket'])) {
        unset($_SESSION['pendingTicket']);
        echo json_encode(['success' => true, 'message' => 'Pending ticket cancelled']);
        exit();
    } else {
        echo json_encode(['success' => true, 'message' => 'No pending ticket to cancel']);
        exit();
    }
}

// If no ticketId provided, return error
if (empty($ticketId)) {
    http_response_code(400);
    echo json_encode(['error' => 'Ticket ID not provided']);
    exit();
}

// Check if the ticket belongs to the user
$stmt = $conn->prepare("SELECT COUNT(*) FROM queue_tickets WHERE ticket_id = :ticket_id AND username = :username");
$stmt->bindParam(':ticket_id', $ticketId);
$stmt->bindParam(':username', $username);
$stmt->execute();
$ticketExists = $stmt->fetchColumn();

if ($ticketExists == 0) {
    http_response_code(404);
    echo json_encode(['error' => 'Ticket not found or does not belong to user']);
    exit();
}

// Delete from waiting_grid if exists
$stmt = $conn->prepare("DELETE FROM waiting_grid WHERE ticket_id = :ticket_id");
$stmt->bindParam(':ticket_id', $ticketId);
$stmt->execute();

// Delete from queue_tickets
$stmt = $conn->prepare("DELETE FROM queue_tickets WHERE ticket_id = :ticket_id AND username = :username");
$stmt->bindParam(':ticket_id', $ticketId);
$stmt->bindParam(':username', $username);
if (!$stmt->execute()) {
    $errorInfo = $stmt->errorInfo();
    http_response_code(500);
    echo json_encode(['error' => 'Failed to cancel ticket', 'db_error' => $errorInfo[2]]);
    exit();
}

// Clear session if this was the current ticket
if (isset($_SESSION['currentTicketId']) && $_SESSION['currentTicketId'] === $ticketId) {
    unset($_SESSION['currentTicketId']);
    unset($_SESSION['currentService']);
}

// Respond success
echo json_encode([
    'success' => true,
    'message' => 'Ticket cancelled successfully'
]);
exit();
?>
