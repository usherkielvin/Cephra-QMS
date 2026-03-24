<?php
/**
 * events.php — Server-Sent Events stream for the Admin dashboard.
 *
 * The browser connects once and receives push updates whenever a new
 * notification lands in the notifications table (written by MySQL
 * triggers or Java's HttpNotifier).
 *
 * Usage in JS:
 *   const es = new EventSource('api/events.php');
 *   es.addEventListener('queue_update', e => { ... JSON.parse(e.data) ... });
 */

session_start();
if (!isset($_SESSION['admin_username'])) {
    http_response_code(401);
    exit();
}

require_once '../config/database.php';

// SSE headers
header('Content-Type: text/event-stream');
header('Cache-Control: no-cache');
header('X-Accel-Buffering: no'); // disable Nginx buffering if present
header('Connection: keep-alive');

// Disable output buffering
if (ob_get_level()) ob_end_clean();

$db   = new Database();
$conn = $db->getConnection();

if (!$conn) {
    echo "event: error\ndata: {\"error\":\"db_unavailable\"}\n\n";
    flush();
    exit();
}

$lastId = (int)($_GET['lastEventId'] ?? 0);

// Send a heartbeat comment every 20s so the connection stays alive
// and proxies don't time it out.
$heartbeatInterval = 20;
$pollInterval      = 1;   // seconds between DB checks
$maxRuntime        = 55;  // Apache/PHP default max_execution_time is 60s; stay under it
$start             = time();
$lastHeartbeat     = time();

while (true) {
    // Check for new notifications
    try {
        $stmt = $conn->prepare(
            "SELECT id, event_type, payload, created_at
             FROM notifications
             WHERE id > ?
             ORDER BY id ASC
             LIMIT 20"
        );
        $stmt->execute([$lastId]);
        $rows = $stmt->fetchAll(PDO::FETCH_ASSOC);

        foreach ($rows as $row) {
            $lastId = (int)$row['id'];
            $eventType = match($row['event_type']) {
                'ticket_status_changed', 'ticket_created' => 'queue_update',
                'bay_status_changed'                      => 'bay_update',
                'charging_completed'                      => 'charging_complete',
                default                                   => 'update',
            };
            echo "id: {$lastId}\n";
            echo "event: {$eventType}\n";
            echo "data: " . json_encode([
                'event_type' => $row['event_type'],
                'payload'    => json_decode($row['payload'], true),
                'created_at' => $row['created_at'],
            ]) . "\n\n";
        }
    } catch (Exception $e) {
        echo "event: error\ndata: {\"error\":\"db_error\"}\n\n";
    }

    // Heartbeat
    if ((time() - $lastHeartbeat) >= $heartbeatInterval) {
        echo ": heartbeat\n\n";
        $lastHeartbeat = time();
    }

    flush();

    // Stop before PHP's max_execution_time kills us — browser auto-reconnects
    if ((time() - $start) >= $maxRuntime) {
        echo "event: reconnect\ndata: {}\n\n";
        flush();
        break;
    }

    sleep($pollInterval);
}
