<?php
namespace Cephra\Monitor;

use Ratchet\MessageComponentInterface;
use Ratchet\ConnectionInterface;
use PDO;

class MonitorWebSocketServer implements MessageComponentInterface {
    protected $clients;
    protected $db;
    protected $lastData = null;
    protected $updateInterval = 1; // seconds
    protected $timer = null;
    protected $lastNotificationId = 0; // tracks highest processed notification id

    public function __construct() {
        $this->clients = new \SplObjectStorage;
        $this->connectToDatabase();
        echo "Monitor WebSocket Server started\n";
    }

    public function onOpen(ConnectionInterface $conn) {
        // Store the new connection
        $this->clients->attach($conn);
        echo "New connection! ({$conn->resourceId})\n";

        // Send initial data to the new client
        $data = $this->fetchMonitorData();
        $conn->send(json_encode($data));

        // Start the timer if this is the first client
        if (count($this->clients) === 1) {
            $this->startUpdateTimer();
        }
    }

    public function onMessage(ConnectionInterface $from, $msg) {
        // We don't expect messages from clients in this application
        // But we could handle specific commands here if needed
        echo "Message from {$from->resourceId}: {$msg}\n";
    }

    public function onClose(ConnectionInterface $conn) {
        // Remove the connection
        $this->clients->detach($conn);
        echo "Connection {$conn->resourceId} has disconnected\n";

        // Stop the timer if no clients are connected
        if (count($this->clients) === 0) {
            $this->stopUpdateTimer();
        }
    }

    public function onError(ConnectionInterface $conn, \Exception $e) {
        echo "An error has occurred: {$e->getMessage()}\n";
        $conn->close();
    }

    protected function connectToDatabase() {
        try {
            require_once __DIR__ . '/../../Admin/config/database.php';
            $database = new \Database();
            $this->db = $database->getConnection();
            echo "Database connection established\n";
        } catch (\Exception $e) {
            echo "Database connection failed: {$e->getMessage()}\n";
        }
    }

    protected function fetchMonitorData() {
        try {
            if (!$this->db) {
                $this->connectToDatabase();
                if (!$this->db) {
                    throw new \Exception('Database connection failed');
                }
            }
            
            // Get bays data
            $stmt = $this->db->query("
                SELECT 
                    bay_number,
                    bay_type,
                    status,
                    current_username,
                    current_ticket_id,
                    start_time
                FROM charging_bays 
                ORDER BY bay_number
            ");
            $bays = $stmt->fetchAll(PDO::FETCH_ASSOC);
            
            // Get queue data (waiting tickets)
            $stmt = $this->db->query("
                SELECT 
                    ticket_id,
                    username,
                    service_type,
                    created_at
                FROM queue_tickets 
                WHERE status = 'Waiting'
                ORDER BY created_at ASC
            ");
            $queue = $stmt->fetchAll(PDO::FETCH_ASSOC);
            
            // Get recent activity (last 10 tickets)
            $stmt = $this->db->query("
                SELECT 
                    ticket_id,
                    username,
                    service_type,
                    status,
                    created_at
                FROM queue_tickets 
                ORDER BY created_at DESC 
                LIMIT 10
            ");
            $recent_activity = $stmt->fetchAll(PDO::FETCH_ASSOC);
            
            // Get statistics
            $stats = [
                'total_bays' => count($bays),
                'available_bays' => count(array_filter($bays, function($bay) { return $bay['status'] === 'Available'; })),
                'occupied_bays' => count(array_filter($bays, function($bay) { return $bay['status'] === 'Occupied'; })),
                'maintenance_bays' => count(array_filter($bays, function($bay) { return $bay['status'] === 'Maintenance'; })),
                'queue_count' => count($queue),
                'timestamp' => date('Y-m-d H:i:s')
            ];
            
            return [
                'success' => true,
                'message' => 'Monitor data loaded',
                'bays' => $bays,
                'queue' => $queue,
                'recent_activity' => $recent_activity,
                'stats' => $stats
            ];
            
        } catch (\Exception $e) {
            return [
                'success' => false,
                'message' => 'Monitor error: ' . $e->getMessage(),
                'error' => $e->getMessage()
            ];
        }
    }

    public function startUpdateTimer() {
        if ($this->timer === null) {
            echo "Starting update timer\n";
            $loop = \React\EventLoop\Loop::get();
            $this->timer = $loop->addPeriodicTimer($this->updateInterval, function() {
                $this->broadcastUpdates();
            });
        }
    }

    public function stopUpdateTimer() {
        if ($this->timer !== null) {
            echo "Stopping update timer\n";
            $loop = \React\EventLoop\Loop::get();
            $loop->cancelTimer($this->timer);
            $this->timer = null;
        }
    }

    protected function broadcastUpdates() {
        // Check if any new notifications arrived since last broadcast
        $hasNew = $this->hasNewNotifications();

        if (!$hasNew) {
            return; // Nothing changed — skip the full fetch
        }

        // Fetch the latest data
        $data = $this->fetchMonitorData();
        
        // Check if data has changed
        $currentDataHash = md5(json_encode($data));
        $lastDataHash = $this->lastData ? md5(json_encode($this->lastData)) : null;
        
        // Only broadcast if data has changed
        if ($currentDataHash !== $lastDataHash) {
            echo "Data changed, broadcasting to " . count($this->clients) . " clients\n";
            $this->lastData = $data;
            
            // Broadcast to all connected clients
            foreach ($this->clients as $client) {
                $client->send(json_encode($data));
            }
        }
    }

    /**
     * Returns true if there are unprocessed notifications newer than
     * the last one we saw, and advances lastNotificationId.
     */
    protected function hasNewNotifications(): bool {
        try {
            if (!$this->db) {
                return true; // Can't check — assume yes
            }
            $stmt = $this->db->prepare(
                "SELECT MAX(id) AS max_id FROM notifications WHERE id > ?"
            );
            $stmt->execute([$this->lastNotificationId]);
            $row = $stmt->fetch(\PDO::FETCH_ASSOC);
            if ($row && $row['max_id'] !== null) {
                $this->lastNotificationId = (int) $row['max_id'];
                return true;
            }
            return false;
        } catch (\Exception $e) {
            return true; // On error, broadcast anyway
        }
    }
}