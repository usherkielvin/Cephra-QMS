<?php
// Mobile API for User Interface
header("Content-Type: application/json");
header("Access-Control-Allow-Origin: *");
header("Access-Control-Allow-Methods: GET, POST");
header("Access-Control-Allow-Headers: Content-Type");

error_reporting(E_ALL);
ini_set('display_errors', 0);
ini_set('log_errors', 1);

require_once "../config/database.php";

$db = (new Database())->getConnection();
if (!$db) {
    echo json_encode([
        "success" => false,
        "error" => "Database connection failed",
        "details" => "Check database configuration and XAMPP MySQL service"
    ]);
    exit();
}

$method = $_SERVER["REQUEST_METHOD"];

// Get action from POST data or query string
$action = "";
if ($method === "POST") {
    $action = $_POST["action"] ?? "";
} else {
    $action = $_GET["action"] ?? "";
}

try {
    switch ($action) {
        case "test":
            echo json_encode([
                "success" => true,
                "message" => "Mobile API is working",
                "timestamp" => date("Y-m-d H:i:s")
            ]);
            break;

        case "user-profile":
            // Get user profile data
            $username = $_GET["username"] ?? "";
            if (!$username) {
                echo json_encode([
                    "success" => false,
                    "error" => "Username required"
                ]);
                break;
            }
            
            $stmt = $db->prepare("
                SELECT 
                    username,
                    firstname,
                    lastname,
                    email,
                    created_at
                FROM users 
                WHERE username = ?
            ");
            $stmt->execute([$username]);
            $user = $stmt->fetch(PDO::FETCH_ASSOC);
            
            if ($user) {
                echo json_encode([
                    "success" => true,
                    "user" => $user
                ]);
            } else {
                echo json_encode([
                    "success" => false,
                    "error" => "User not found"
                ]);
            }
            break;

        case "user-history":
            // Get user charging history
            $username = $_GET["username"] ?? "";
            if (!$username) {
                echo json_encode([
                    "success" => false,
                    "error" => "Username required"
                ]);
                break;
            }
            
            $stmt = $db->prepare("
                SELECT 
                    ticket_id,
                    service_type,
                    status,
                    payment_status,
                    initial_battery_level,
                    created_at
                FROM queue_tickets 
                WHERE username = ?
                ORDER BY created_at DESC
            ");
            $stmt->execute([$username]);
            $history = $stmt->fetchAll(PDO::FETCH_ASSOC);
            
            echo json_encode([
                "success" => true,
                "history" => $history
            ]);
            break;

        case "wallet-balance":
            // Returns wallet balance and latest transactions
            $username = $_GET["username"] ?? $_POST["username"] ?? "";
            if (!$username) {
                echo json_encode(["success" => false, "error" => "Username required"]);
                break;
            }
            try {
                // Balance
                $stmt = $db->prepare("SELECT balance FROM wallet_balance WHERE username = ?");
                $stmt->execute([$username]);
                $balRow = $stmt->fetch(PDO::FETCH_ASSOC);
                $balance = $balRow ? (float)$balRow['balance'] : 0.0;

                // Latest 10 transactions
                $stmt = $db->prepare("SELECT transaction_type, amount, description, reference_id, transaction_date FROM wallet_transactions WHERE username = ? ORDER BY transaction_date DESC LIMIT 10");
                $stmt->execute([$username]);
                $transactions = $stmt->fetchAll(PDO::FETCH_ASSOC);

                echo json_encode(["success" => true, "balance" => $balance, "transactions" => $transactions]);
            } catch (Exception $e) {
                echo json_encode(["success" => false, "error" => $e->getMessage()]);
            }
            break;

        case "wallet-history":
            // Returns full wallet history (paginated in future)
            $username = $_GET["username"] ?? $_POST["username"] ?? "";
            if (!$username) {
                echo json_encode(["success" => false, "error" => "Username required"]);
                break;
            }
            try {
                $stmt = $db->prepare("SELECT transaction_type, amount, description, reference_id, transaction_date FROM wallet_transactions WHERE username = ? ORDER BY transaction_date DESC LIMIT 50");
                $stmt->execute([$username]);
                echo json_encode(["success" => true, "transactions" => $stmt->fetchAll(PDO::FETCH_ASSOC)]);
            } catch (Exception $e) {
                echo json_encode(["success" => false, "error" => $e->getMessage()]);
            }
            break;

        case "wallet-topup":
            if ($method !== "POST") {
                echo json_encode(["success" => false, "error" => "Method not allowed"]);
                break;
            }
            $username = $_POST["username"] ?? "";
            $amount = (float)($_POST["amount"] ?? 0);
            if (!$username || $amount <= 0) {
                echo json_encode(["success" => false, "error" => "Username and positive amount required"]);
                break;
            }
            try {
                // Fetch current balance
                $db->beginTransaction();
                $stmt = $db->prepare("SELECT balance FROM wallet_balance WHERE username = ? FOR UPDATE");
                $stmt->execute([$username]);
                $balRow = $stmt->fetch(PDO::FETCH_ASSOC);
                $current = $balRow ? (float)$balRow['balance'] : 0.0;
                $new = $current + $amount;

                // Update or insert balance
                if ($balRow) {
                    $stmt = $db->prepare("UPDATE wallet_balance SET balance = ?, updated_at = CURRENT_TIMESTAMP WHERE username = ?");
                    $stmt->execute([$new, $username]);
                } else {
                    $stmt = $db->prepare("INSERT INTO wallet_balance (username, balance) VALUES (?, ?)");
                    $stmt->execute([$username, $new]);
                }

                // Insert wallet transaction
                $reference = 'WT' . date('YmdHis') . rand(100, 999);
                $stmt = $db->prepare("INSERT INTO wallet_transactions (username, transaction_type, amount, previous_balance, new_balance, description, reference_id, transaction_date) VALUES (?, 'TOPUP', ?, ?, ?, 'Wallet top-up', ?, NOW())");
                $stmt->execute([$username, $amount, $current, $new, $reference]);

                $db->commit();
                echo json_encode(["success" => true, "balance" => $new, "reference" => $reference]);
            } catch (Exception $e) {
                if ($db->inTransaction()) { $db->rollBack(); }
                echo json_encode(["success" => false, "error" => $e->getMessage()]);
            }
            break;

        case "available-bays":
            // Get available charging bays
            $stmt = $db->query("
                SELECT 
                    bay_number,
                    bay_type,
                    status
                FROM charging_bays 
                WHERE status = 'Available'
                ORDER BY bay_number
            ");
            $bays = $stmt->fetchAll(PDO::FETCH_ASSOC);
            
            echo json_encode([
                "success" => true,
                "bays" => $bays
            ]);
            break;

        case "live-status":
            // Public live status for user dashboard (no admin session required)
            try {
                // Current queue: Waiting + Processing (matches Admin dashboard semantics)
                $q = $db->query("SELECT COUNT(*) AS c FROM queue_tickets WHERE status IN ('Waiting','Processing')");
                $queue_count = (int)($q->fetch(PDO::FETCH_ASSOC)['c'] ?? 0);

                // Active sessions: charging bays in use derived from charging_grid
                $a = $db->query("SELECT COUNT(*) AS c FROM charging_grid WHERE ticket_id IS NOT NULL");
                $active_bays = (int)($a->fetch(PDO::FETCH_ASSOC)['c'] ?? 0);

                // Simple ETA heuristic: 1 minute per vehicle waiting
                $eta_minutes = max(0, $queue_count);

                echo json_encode([
                    "success" => true,
                    "queue_count" => $queue_count,
                    "active_bays" => $active_bays,
                    "estimated_wait_minutes" => $eta_minutes
                ]);
            } catch (Exception $e) {
                echo json_encode(["success" => false, "error" => $e->getMessage()]);
            }
            break;

        case "create-ticket":
            if ($method !== "POST") {
                echo json_encode([
                    "success" => false,
                    "error" => "Method not allowed"
                ]);
                break;
            }
            
            $username = $_POST["username"] ?? "";
            $service_type = $_POST["service_type"] ?? "";
            $battery_level = $_POST["battery_level"] ?? 0;
            
            if (!$username || !$service_type) {
                echo json_encode([
                    "success" => false,
                    "error" => "Username and service type required"
                ]);
                break;
            }

            // Block if user already has active or queued ticket
            // Active ticket check
            $stmt = $db->prepare("SELECT COUNT(*) FROM active_tickets WHERE username = ?");
            $stmt->execute([$username]);
            if ((int)$stmt->fetchColumn() > 0) {
                echo json_encode([
                    "success" => false,
                    "error" => "You already have an active charging ticket. Please complete your current session first."
                ]);
                break;
            }

            // Queued ticket check (Waiting/Pending/Processing)
            $stmt = $db->prepare("SELECT COUNT(*) FROM queue_tickets WHERE username = ? AND status IN ('Waiting','Pending','Processing')");
            $stmt->execute([$username]);
            if ((int)$stmt->fetchColumn() > 0) {
                echo json_encode([
                    "success" => false,
                    "error" => "You already have a ticket in queue. Please wait until it is processed."
                ]);
                break;
            }
            
            // Normalize service type to expected labels
            $queueServiceType = ($service_type === 'Fast Charging') ? 'Fast Charging' : 'Normal Charging';

            // Determine priority based on battery level (<20% => priority)
            $battery_level = (int)$battery_level;
            $priority = ($battery_level < 20) ? 1 : 0;

            // Build ticket prefix consistent with web + Java logic
            $basePrefix = ($queueServiceType === 'Fast Charging') ? 'FCH' : 'NCH';
            $ticketPrefix = ($priority === 1) ? ($basePrefix . 'P') : $basePrefix; // e.g., FCH, FCHP, NCH, NCHP
            $prefixPatternBase = $basePrefix . '%';

            // Compute next incremental number across queue_tickets, active_tickets, and charging_history
            $sql = "SELECT GREATEST(
                        IFNULL((SELECT MAX(CAST(RIGHT(ticket_id, 3) AS UNSIGNED)) FROM queue_tickets WHERE ticket_id LIKE :p1), 0),
                        IFNULL((SELECT MAX(CAST(RIGHT(ticket_id, 3) AS UNSIGNED)) FROM active_tickets WHERE ticket_id LIKE :p2), 0),
                        IFNULL((SELECT MAX(CAST(RIGHT(ticket_id, 3) AS UNSIGNED)) FROM charging_history WHERE ticket_id LIKE :p3), 0)
                    ) AS max_num";
            $stmt = $db->prepare($sql);
            $stmt->bindValue(':p1', $prefixPatternBase, PDO::PARAM_STR);
            $stmt->bindValue(':p2', $prefixPatternBase, PDO::PARAM_STR);
            $stmt->bindValue(':p3', $prefixPatternBase, PDO::PARAM_STR);
            $stmt->execute();
            $row = $stmt->fetch(PDO::FETCH_ASSOC);
            $nextNum = (isset($row['max_num']) ? (int)$row['max_num'] : 0) + 1;
            $ticket_id = $ticketPrefix . str_pad($nextNum, 3, '0', STR_PAD_LEFT); // e.g., FCH001, NCHP012

            // Initial status alignment (priority tickets go to Waiting)
            $initialStatus = ($priority === 1) ? 'Waiting' : 'Pending';

            // Insert into queue_tickets
            $stmt = $db->prepare(
                "INSERT INTO queue_tickets (ticket_id, username, service_type, status, payment_status, initial_battery_level, priority)
                 VALUES (:ticket_id, :username, :service_type, :status, '', :battery_level, :priority)"
            );
            $ok = $stmt->execute([
                ':ticket_id' => $ticket_id,
                ':username' => $username,
                ':service_type' => $queueServiceType,
                ':status' => $initialStatus,
                ':battery_level' => $battery_level,
                ':priority' => $priority
            ]);

            if ($ok) {
                echo json_encode([
                    "success" => true,
                    "message" => "Ticket created successfully",
                    "ticket_id" => $ticket_id,
                    "serviceType" => $queueServiceType,
                    "batteryLevel" => $battery_level
                ]);
            } else {
                echo json_encode([
                    "success" => false,
                    "error" => "Failed to create ticket"
                ]);
            }
            break;

        case "login":
            if ($method !== "POST") {
                echo json_encode([
                    "success" => false,
                    "error" => "Method not allowed"
                ]);
                break;
            }
            
            $username = $_POST["username"] ?? "";
            $password = $_POST["password"] ?? "";
            
            if (!$username || !$password) {
                echo json_encode([
                    "success" => false,
                    "error" => "Username and password are required"
                ]);
                break;
            }
            
            // Check user credentials
            $stmt = $db->prepare("
                SELECT username, firstname, lastname, email 
                FROM users 
                WHERE username = ? AND password = ?
            ");
            $stmt->execute([$username, $password]);
            $user = $stmt->fetch(PDO::FETCH_ASSOC);
            
            if ($user) {
                // Start session and store user data
                session_start();
                $_SESSION['username'] = $user['username'];
                $_SESSION['firstname'] = $user['firstname'];
                $_SESSION['lastname'] = $user['lastname'];
                $_SESSION['email'] = $user['email'];
                
                echo json_encode([
                    "success" => true,
                    "message" => "Login successful",
                    "username" => $user['username'],
                    "firstname" => $user['firstname'],
                    "lastname" => $user['lastname']
                ]);
            } else {
                echo json_encode([
                    "success" => false,
                    "error" => "Invalid username or password"
                ]);
            }
            break;

        case "register-user":
            if ($method !== "POST") {
                echo json_encode([
                    "success" => false,
                    "error" => "Method not allowed"
                ]);
                break;
            }
            
            $username = $_POST["username"] ?? "";
            $firstname = $_POST["firstname"] ?? "";
            $lastname = $_POST["lastname"] ?? "";
            $email = $_POST["email"] ?? "";
            $password = $_POST["password"] ?? "";
            
            if (!$username || !$firstname || !$lastname || !$email || !$password) {
                echo json_encode([
                    "success" => false,
                    "error" => "All fields are required"
                ]);
                break;
            }
            
            // Check if username already exists
            $stmt = $db->prepare("SELECT username FROM users WHERE username = ?");
            $stmt->execute([$username]);
            if ($stmt->fetch()) {
                echo json_encode([
                    "success" => false,
                    "error" => "Username already exists"
                ]);
                break;
            }
            
            // Insert new user
            $stmt = $db->prepare("
                INSERT INTO users (username, firstname, lastname, email, password) 
                VALUES (?, ?, ?, ?, ?)
            ");
            $result = $stmt->execute([$username, $firstname, $lastname, $email, $password]);
            
            if ($result) {
                echo json_encode([
                    "success" => true,
                    "message" => "User registered successfully"
                ]);
            } else {
                echo json_encode([
                    "success" => false,
                    "error" => "Failed to register user"
                ]);
            }
            break;

        default:
            echo json_encode([
                "success" => false,
                "error" => "Invalid action",
                "available_actions" => [
                    "test", "login", "user-profile", "user-history", "available-bays", 
                    "create-ticket", "register-user"
                ]
            ]);
            break;
    }
} catch (Exception $e) {
    echo json_encode([
        "success" => false,
        "error" => "Server error: " . $e->getMessage()
    ]);
}
?>
