<?php
/**
 * notify.php — Internal notification endpoint.
 * Called by the Java desktop app (HttpNotifier) to push events
 * into the notifications table so the web side reacts immediately.
 *
 * Only accepts requests from localhost.
 */

// Restrict to localhost only
$remote = $_SERVER['REMOTE_ADDR'] ?? '';
if ($remote !== '127.0.0.1' && $remote !== '::1') {
    http_response_code(403);
    echo json_encode(['success' => false, 'error' => 'Forbidden']);
    exit();
}

header('Content-Type: application/json');
ini_set('display_errors', 0);
ini_set('log_errors', 1);

require_once __DIR__ . '/shared/database.php';

$input = json_decode(file_get_contents('php://input'), true);

if (empty($input['event_type']) || empty($input['payload'])) {
    http_response_code(400);
    echo json_encode(['success' => false, 'error' => 'Missing event_type or payload']);
    exit();
}

$event_type = $input['event_type'];
$payload    = is_array($input['payload'])
    ? json_encode($input['payload'])
    : $input['payload'];

try {
    $db   = new Database();
    $conn = $db->getConnection();

    $stmt = $conn->prepare(
        "INSERT INTO notifications (event_type, payload) VALUES (?, ?)"
    );
    $stmt->execute([$event_type, $payload]);

    echo json_encode(['success' => true, 'id' => $conn->lastInsertId()]);
} catch (Exception $e) {
    error_log("notify.php error: " . $e->getMessage());
    http_response_code(500);
    echo json_encode(['success' => false, 'error' => 'Internal error']);
}
