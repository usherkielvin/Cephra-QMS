<?php
header('Content-Type: application/json');
header('Access-Control-Allow-Origin: *');
header('Access-Control-Allow-Methods: GET, POST');
header('Access-Control-Allow-Headers: Content-Type');

session_start();

// Ensure PHP does not emit HTML error pages. Instead convert errors/exceptions/fatals to JSON
ini_set('display_errors', '0');
error_reporting(E_ALL);

set_error_handler(function ($severity, $message, $file, $line) {
    // Convert PHP warnings/notices/errors to JSON response without using HTTP status codes
    send_response([
        'success' => false,
        'code' => 500,
        'message' => $message,
        'type' => 'php_error',
        'file' => $file,
        'line' => $line,
        'severity' => $severity
    ]);
});

set_exception_handler(function ($e) {
    send_response([
        'success' => false,
        'code' => 500,
        'message' => $e->getMessage(),
        'type' => 'exception'
    ]);
});

register_shutdown_function(function () {
    $err = error_get_last();
    if ($err && in_array($err['type'], [E_ERROR, E_PARSE, E_CORE_ERROR, E_COMPILE_ERROR, E_USER_ERROR])) {
        send_response([
            'success' => false,
            'code' => 500,
            'message' => $err['message'],
            'type' => 'shutdown',
            'file' => $err['file'],
            'line' => $err['line']
        ]);
    }
});

/**
 * Standardized JSON response helper
 * Always returns a JSON object with the shape:
 * {
 *   success: bool,
 *   code: int,        // application-level code (not HTTP status)
 *   message: string,  // human-readable message or ''
 *   data: object|null  // payload (any keys besides success/code/message are moved here)
 * }
 */
function send_response(array $payload)
{
    // Ensure Content-Type header
    header('Content-Type: application/json');

    $success = isset($payload['success']) ? (bool)$payload['success'] : false;
    $code = isset($payload['code']) ? intval($payload['code']) : 0;
    $message = '';
    if (isset($payload['message'])) {
        $message = $payload['message'];
    } elseif (isset($payload['error'])) {
        $message = $payload['error'];
    }

    // Move remaining keys to data
    $data = null;
    $reserved = ['success', 'code', 'message', 'error'];
    $other = array_diff_key($payload, array_flip($reserved));
    if (!empty($other)) {
        $data = $other;
    }

    $response = [
        'success' => $success,
        'code' => $code,
        'message' => $message,
        'data' => $data
    ];

    echo json_encode($response);
    exit();
}

// Check if admin is logged in
if (!isset($_SESSION['admin_logged_in']) || $_SESSION['admin_logged_in'] !== true) {
    send_response([
        'success' => false,
        'code' => 401,
        'message' => 'Unauthorized access'
    ]);
}

// Enforce deactivated staff cannot use API
try {
    require_once '../config/database.php';
    $tmpdb = (new Database())->getConnection();
    if ($tmpdb && isset($_SESSION['admin_username'])) {
        $chk = $tmpdb->prepare("SELECT status FROM staff_records WHERE username = ?");
        $chk->execute([$_SESSION['admin_username']]);
        $row = $chk->fetch(PDO::FETCH_ASSOC);
        if (!$row || strcasecmp($row['status'] ?? '', 'Active') !== 0) {
            session_unset();
            session_destroy();
            send_response([
                'success' => false,
                'code' => 401,
                'message' => 'Account deactivated'
            ]);
        }
    }
} catch (Exception $e) {
    // If validation fails unexpectedly, block access
    send_response([
        'success' => false,
        'code' => 401,
        'message' => 'Unauthorized'
    ]);
}

$db = (new Database())->getConnection();
if (!$db) {
    echo json_encode(['error' => 'Database connection failed']);
    exit();
}

$method = $_SERVER['REQUEST_METHOD'];

// Get action from POST data or query string
$action = '';
if ($method === 'POST') {
    $action = $_POST['action'] ?? '';
} else {
    $action = $_GET['action'] ?? '';
}

try {
    switch ($action) {
        case 'dashboard':
            // Get dashboard statistics
            $stats = [];
            
            // Total users
            $stmt = $db->query("SELECT COUNT(*) as count FROM users");
            $stats['total_users'] = $stmt->fetch(PDO::FETCH_ASSOC)['count'];
            
            // Queue count
            $stmt = $db->query("SELECT COUNT(*) as count FROM queue_tickets WHERE status IN ('Waiting', 'Processing')");
            $stats['queue_count'] = $stmt->fetch(PDO::FETCH_ASSOC)['count'];
            
            // Active bays (derive from charging_grid)
            $stmt = $db->query("SELECT COUNT(*) as count FROM charging_grid WHERE ticket_id IS NOT NULL");
            $stats['active_bays'] = $stmt->fetch(PDO::FETCH_ASSOC)['count'];
            
            // Overall revenue (all time) - using charging_history table to match Admin Java
            $stmt = $db->query("SELECT SUM(total_amount) as revenue FROM charging_history");
            $revenue = $stmt->fetch(PDO::FETCH_ASSOC)['revenue'];
            $stats['revenue_today'] = $revenue ? (float)$revenue : 0;
            
            // Today's revenue for comparison - using charging_history table to match Admin Java
            $stmt = $db->query("SELECT SUM(total_amount) as revenue FROM charging_history WHERE DATE(completed_at) = CURDATE()");
            $today_revenue = $stmt->fetch(PDO::FETCH_ASSOC)['revenue'];
            $stats['revenue_today_only'] = $today_revenue ? (float)$today_revenue : 0;
            
            // Recent activity from actual database records
            $stmt = $db->query("
                SELECT 
                    'ticket' as type,
                    CONCAT('Ticket ', ticket_id, ' - ', username, ' (', service_type, ')') as description,
                    'fa-ticket-alt' as icon,
                    created_at
                FROM queue_tickets 
                ORDER BY created_at DESC 
                LIMIT 10
            ");
            $recent_activity = $stmt->fetchAll(PDO::FETCH_ASSOC);
            
            echo json_encode([
                'success' => true,
                'stats' => $stats,
                'recent_activity' => $recent_activity
            ]);
            break;

        case 'queue':
            // Get queue tickets
            $stmt = $db->query("
                SELECT 
                    ticket_id,
                    username,
                    service_type,
                    status,
                    payment_status,
                    initial_battery_level,
                    priority,
                    created_at
                FROM queue_tickets 
                ORDER BY priority DESC, created_at ASC
            ");
            $queue = $stmt->fetchAll(PDO::FETCH_ASSOC);
            
            echo json_encode([
                'success' => true,
                'queue' => $queue
            ]);
            break;

        case 'bays':
            // Get charging bays with effective status from charging_grid
            $stmt = $db->query("
                SELECT
                    cb.bay_number,
                    cb.bay_type,
                    CASE
                        WHEN cb.status = 'Available'
                             AND EXISTS (SELECT 1 FROM charging_grid cg
                                         WHERE cg.bay_number = cb.bay_number
                                           AND cg.ticket_id IS NOT NULL)
                        THEN 'Occupied'
                        ELSE cb.status
                    END AS status,
                    COALESCE(cb.current_ticket_id, (SELECT cg.ticket_id FROM charging_grid cg WHERE cg.bay_number = cb.bay_number LIMIT 1)) AS current_ticket_id,
                    cb.current_username,
                    cb.start_time
                FROM charging_bays cb
                ORDER BY cb.bay_number
            ");
            $bays = $stmt->fetchAll(PDO::FETCH_ASSOC);
            
            echo json_encode([
                'success' => true,
                'bays' => $bays
            ]);
            break;

        case 'users':
            // Get users
            $stmt = $db->query("
                SELECT 
                    username,
                    firstname,
                    lastname,
                    email,
                    created_at
                FROM users 
                ORDER BY created_at DESC
            ");
            $users = $stmt->fetchAll(PDO::FETCH_ASSOC);
            
            echo json_encode([
                'success' => true,
                'users' => $users
            ]);
            break;

        case 'staff':
            $stmt = $db->query("SELECT name, username, email, status, created_at FROM staff_records WHERE username <> 'admin' ORDER BY created_at DESC");
            $staff = $stmt->fetchAll(PDO::FETCH_ASSOC);
            echo json_encode(['success' => true, 'staff' => $staff]);
            break;

        case 'add-staff':
            if ($method !== 'POST') { echo json_encode(['error' => 'Method not allowed']); break; }
            $name = $_POST['name'] ?? '';
            $username = $_POST['username'] ?? '';
            $email = $_POST['email'] ?? '';
            $password = $_POST['password'] ?? '';
            if (!$name || !$username || !$email || !$password) { echo json_encode(['error' => 'All fields are required']); break; }
            $chk = $db->prepare('SELECT 1 FROM staff_records WHERE username = ? OR email = ?');
            $chk->execute([$username, $email]);
            if ($chk->fetch()) { echo json_encode(['error' => 'Username or email already exists']); break; }
            $stmt = $db->prepare('INSERT INTO staff_records (name, username, email, status, password) VALUES (?, ?, ?, "Active", ?)');
            $ok = $stmt->execute([$name, $username, $email, $password]);
            echo json_encode($ok ? ['success' => true, 'message' => 'Staff added'] : ['error' => 'Failed to add staff']);
            break;

        case 'reset-staff-password':
            if ($method !== 'POST') { echo json_encode(['error' => 'Method not allowed']); break; }
            $username = $_POST['username'] ?? '';
            if (!$username) { echo json_encode(['error' => 'Username required']); break; }
            if (strtolower($username) === 'admin') { echo json_encode(['error' => 'Admin account password cannot be reset']); break; }
            
            // Generate random 6-digit password
            $new_password = str_pad(random_int(0, 999999), 6, '0', STR_PAD_LEFT);
            
            $stmt = $db->prepare('UPDATE staff_records SET password = ? WHERE username = ?');
            $ok = $stmt->execute([$new_password, $username]);
            
            if ($ok) {
                echo json_encode(['success' => true, 'new_password' => $new_password, 'message' => 'Password reset successfully']);
            } else {
                echo json_encode(['error' => 'Failed to reset password']);
            }
            break;

        case 'delete-staff':
            if ($method !== 'POST') { echo json_encode(['error' => 'Method not allowed']); break; }
            $username = $_POST['username'] ?? '';
            if (!$username) { echo json_encode(['error' => 'Username required']); break; }
            if (strtolower($username) === 'admin') { echo json_encode(['error' => 'Admin account cannot be deleted']); break; }
            $stmt = $db->prepare('DELETE FROM staff_records WHERE username = ?');
            $ok = $stmt->execute([$username]);
            echo json_encode($ok ? ['success' => true, 'message' => 'Staff deleted'] : ['error' => 'Failed to delete staff']);
            break;

        case 'staff-activity':
            $stmt = $db->query('SELECT ticket_id, username, service_type, total_amount, served_by, completed_at FROM charging_history ORDER BY completed_at DESC LIMIT 50');
            $rows = $stmt->fetchAll(PDO::FETCH_ASSOC);
            echo json_encode(['success' => true, 'activity' => $rows]);
            break;

        case 'ticket-details':
            $ticket_id = $_GET['ticket_id'] ?? '';
            if (!$ticket_id) {
                echo json_encode(['error' => 'Ticket ID required']);
                break;
            }
            
            $stmt = $db->prepare("
                SELECT 
                    ticket_id,
                    username,
                    service_type,
                    status,
                    payment_status,
                    initial_battery_level,
                    created_at
                FROM queue_tickets 
                WHERE ticket_id = ?
            ");
            $stmt->execute([$ticket_id]);
            $ticket = $stmt->fetch(PDO::FETCH_ASSOC);
            
            if ($ticket) {
                echo json_encode([
                    'success' => true,
                    'ticket' => $ticket
                ]);
            } else {
                echo json_encode(['error' => 'Ticket not found']);
            }
            break;

        case 'process-ticket':
            if ($method !== 'POST') {
                echo json_encode(['error' => 'Method not allowed']);
                break;
            }
            
            $ticket_id = $_POST['ticket_id'] ?? '';
            if (!$ticket_id) {
                echo json_encode(['error' => 'Ticket ID required']);
                break;
            }
            
            // Update ticket status to Processing
            $stmt = $db->prepare("UPDATE queue_tickets SET status = 'Processing' WHERE ticket_id = ?");
            $result = $stmt->execute([$ticket_id]);
            
            if ($result) {
                echo json_encode(['success' => true, 'message' => 'Ticket processed successfully']);
            } else {
                echo json_encode(['error' => 'Failed to process ticket']);
            }
            break;

        case 'progress-to-waiting':
            if ($method !== 'POST') {
                echo json_encode(['error' => 'Method not allowed']);
                break;
            }
            
            $ticket_id = $_POST['ticket_id'] ?? '';
            if (!$ticket_id) {
                echo json_encode(['error' => 'Ticket ID required']);
                break;
            }
            
            try {
                $db->beginTransaction();
                
                // Get ticket details first
                $ticket_stmt = $db->prepare("SELECT username, service_type, initial_battery_level FROM queue_tickets WHERE ticket_id = ?");
                $ticket_stmt->execute([$ticket_id]);
                $ticket_data = $ticket_stmt->fetch(PDO::FETCH_ASSOC);
                
                if (!$ticket_data) {
                    throw new Exception('Ticket not found');
                }
                
                // Update ticket status to Waiting
                $stmt = $db->prepare("UPDATE queue_tickets SET status = 'Waiting' WHERE ticket_id = ?");
                $result = $stmt->execute([$ticket_id]);
                
                if (!$result) {
                    throw new Exception('Failed to update ticket status');
                }
                
                // Get next position in queue
                $position_stmt = $db->query("SELECT COALESCE(MAX(position_in_queue), 0) + 1 as next_position FROM waiting_grid WHERE ticket_id IS NOT NULL");
                $position_data = $position_stmt->fetch(PDO::FETCH_ASSOC);
                $next_position = $position_data['next_position'];
                
                // Find an empty slot in waiting_grid or use the next position
                $empty_slot_stmt = $db->query("SELECT id FROM waiting_grid WHERE ticket_id IS NULL LIMIT 1");
                $empty_slot = $empty_slot_stmt->fetch(PDO::FETCH_ASSOC);
                
                if ($empty_slot) {
                    // Use existing empty slot
                    $insert_stmt = $db->prepare("
                        UPDATE waiting_grid 
                        SET ticket_id = ?, username = ?, service_type = ?, initial_battery_level = ?, position_in_queue = ?
                        WHERE id = ?
                    ");
                    $insert_stmt->execute([$ticket_id, $ticket_data['username'], $ticket_data['service_type'], $ticket_data['initial_battery_level'], $next_position, $empty_slot['id']]);
                } else {
                    // Insert new row (waiting_grid should have at least 8 slots)
                    $insert_stmt = $db->prepare("
                        INSERT INTO waiting_grid (ticket_id, username, service_type, initial_battery_level, position_in_queue) 
                        VALUES (?, ?, ?, ?, ?)
                    ");
                    $insert_stmt->execute([$ticket_id, $ticket_data['username'], $ticket_data['service_type'], $ticket_data['initial_battery_level'], $next_position]);
                }
                
                $db->commit();
                echo json_encode(['success' => true, 'message' => 'Ticket moved to waiting status and added to waiting grid']);
                
            } catch (Exception $e) {
                $db->rollback();
                echo json_encode(['error' => 'Failed to move ticket to waiting: ' . $e->getMessage()]);
            }
            break;

        case 'auto-assign-waiting-tickets':
            // Auto-assign waiting tickets to available bays (like Admin Java)
            try {
                $db->beginTransaction();
                $assigned_count = 0;
                
                // Get all waiting tickets
                $stmt = $db->query("SELECT ticket_id, username, service_type, initial_battery_level FROM queue_tickets WHERE status = 'Waiting' ORDER BY created_at ASC");
                $waiting_tickets = $stmt->fetchAll(PDO::FETCH_ASSOC);
                
                foreach ($waiting_tickets as $ticket) {
                    $ticket_id = $ticket['ticket_id'];
                    $username = $ticket['username'];
                    $service_type = $ticket['service_type'];
                    $battery_level = $ticket['initial_battery_level'];
                    
                    // Determine if it's fast charging
                    $is_fast_charging = (stripos($service_type, 'fast') !== false) || (stripos($ticket_id, 'FCH') !== false);
                    
                    // Find available bay
                    $bay_type = $is_fast_charging ? 'Fast' : 'Normal';
                    $bay_stmt = $db->prepare("
                        SELECT bay_number 
                        FROM charging_bays 
                        WHERE status = 'Available' 
                        AND bay_type = ? 
                        ORDER BY CAST(SUBSTRING(bay_number, 5) AS UNSIGNED) ASC 
                        LIMIT 1
                    ");
                    $bay_stmt->execute([$bay_type]);
                    $available_bay = $bay_stmt->fetch(PDO::FETCH_ASSOC);
                    
                    if ($available_bay) {
                        $bay_number = $available_bay['bay_number'];
                        
                        // Update charging_bays table
                        $update_bay_stmt = $db->prepare("
                            UPDATE charging_bays 
                            SET status = 'Occupied', current_ticket_id = ?, current_username = ?, start_time = NOW() 
                            WHERE bay_number = ?
                        ");
                        $update_bay_stmt->execute([$ticket_id, $username, $bay_number]);
                        
                        // Update queue_tickets status to In Progress (like Admin Java)
                        $update_ticket_stmt = $db->prepare("
                            UPDATE queue_tickets 
                            SET status = 'In Progress' 
                            WHERE ticket_id = ?
                        ");
                        $update_ticket_stmt->execute([$ticket_id]);
                        
                        // Update waiting_grid (if exists)
                        $update_waiting_stmt = $db->prepare("
                            UPDATE waiting_grid 
                            SET ticket_id = NULL, username = NULL, service_type = NULL, initial_battery_level = NULL, position_in_queue = NULL 
                            WHERE ticket_id = ?
                        ");
                        $update_waiting_stmt->execute([$ticket_id]);
                        
                        // Update charging_grid (if exists)
                        $update_charging_stmt = $db->prepare("
                            UPDATE charging_grid 
                            SET ticket_id = ?, username = ?, service_type = ?, initial_battery_level = ?, start_time = NOW() 
                            WHERE bay_number = ?
                        ");
                        $update_charging_stmt->execute([$ticket_id, $username, $service_type, $battery_level, $bay_number]);
                        
                        $assigned_count++;
                        error_log("Auto-assigned ticket {$ticket_id} to {$bay_number}");
                    }
                }
                
                $db->commit();
                
                echo json_encode([
                    'success' => true,
                    'message' => "Auto-assigned {$assigned_count} waiting tickets to available bays",
                    'assigned_count' => $assigned_count
                ]);
                
            } catch (Exception $e) {
                $db->rollback();
                error_log("Error in auto-assign-waiting-tickets: " . $e->getMessage());
                echo json_encode(['error' => 'Failed to auto-assign waiting tickets: ' . $e->getMessage()]);
            }
            break;

        case 'progress-to-charging':
            if ($method !== 'POST') {
                echo json_encode(['error' => 'Method not allowed']);
                break;
            }
            
            $ticket_id = $_POST['ticket_id'] ?? '';
            if (!$ticket_id) {
                echo json_encode(['error' => 'Ticket ID required']);
                break;
            }
            
            // Log the attempt
            error_log("Admin API: Attempting to progress ticket {$ticket_id} to charging");
            
            // Get ticket service type to determine bay type
            $service_stmt = $db->prepare("SELECT service_type FROM queue_tickets WHERE ticket_id = ?");
            $service_stmt->execute([$ticket_id]);
            $ticket_data = $service_stmt->fetch(PDO::FETCH_ASSOC);
            
            if (!$ticket_data) {
                error_log("Admin API: Ticket {$ticket_id} not found in database");
                echo json_encode(['error' => 'Ticket not found']);
                break;
            }
            
            $service_type = $ticket_data['service_type'] ?? '';
            $is_fast_charging = (stripos($service_type, 'fast') !== false) || (stripos($ticket_id, 'FCH') !== false);
            $bay_type = $is_fast_charging ? 'Fast' : 'Normal';
            
            error_log("Admin API: Ticket {$ticket_id} - Service: {$service_type}, Is Fast: " . ($is_fast_charging ? 'Yes' : 'No'));
            
            // First, let's check what bays are actually available
            $debug_stmt = $db->query("SELECT bay_number, status FROM charging_bays ORDER BY CAST(bay_number AS UNSIGNED) ASC");
            $all_bays = $debug_stmt->fetchAll(PDO::FETCH_ASSOC);
            error_log("Admin API: All bays: " . json_encode($all_bays));
            
            // Ensure Bay-3 exists and is properly configured
            $bay3_check = $db->query("SELECT COUNT(*) as count FROM charging_bays WHERE bay_number = 'Bay-3'");
            $bay3_exists = $bay3_check->fetch(PDO::FETCH_ASSOC)['count'];
            
            if ($bay3_exists == 0) {
                error_log("Admin API: Bay-3 does not exist - creating it");
                $create_bay3 = $db->prepare("INSERT INTO charging_bays (bay_number, bay_type, status, current_ticket_id, current_username, start_time) VALUES ('Bay-3', 'Fast', 'Available', NULL, NULL, NULL)");
                $create_result = $create_bay3->execute();
                if ($create_result) {
                    error_log("Admin API: Successfully created Bay-3");
                } else {
                    error_log("Admin API: Failed to create Bay-3");
                }
            }
            
            // Find appropriate bay based on service type
            if ($is_fast_charging) {
                // Fast charging: only use bays 1-3
                error_log("Admin API: Searching for FCH bays 1-3 with status 'AVAILABLE'");
                
                // Database uses 'Bay-1', 'Bay-2', 'Bay-3' format
                $bay_stmt = $db->query("
                    SELECT bay_number 
                    FROM charging_bays 
                    WHERE TRIM(UPPER(status)) = 'AVAILABLE' 
                    AND bay_type = 'Fast'
                    ORDER BY bay_number ASC 
                    LIMIT 1
                ");
                
                // Debug: Check what bays 1-3 look like specifically
                $debug_fast_stmt = $db->query("
                    SELECT bay_number, status, TRIM(UPPER(status)) as trimmed_status, CAST(bay_number AS UNSIGNED) as bay_num
                    FROM charging_bays 
                    WHERE CAST(bay_number AS UNSIGNED) BETWEEN 1 AND 3
                    ORDER BY CAST(bay_number AS UNSIGNED) ASC
                ");
                $fast_bays = $debug_fast_stmt->fetchAll(PDO::FETCH_ASSOC);
                error_log("Admin API: Fast bays 1-3 details: " . json_encode($fast_bays));
                
                // Test the exact query that should find Bay-3
                $test_stmt = $db->query("
                    SELECT bay_number, status, bay_type,
                           CASE WHEN TRIM(UPPER(status)) = 'AVAILABLE' THEN 'MATCH' ELSE 'NO_MATCH' END as status_check,
                           CASE WHEN bay_type = 'Fast' THEN 'FAST_TYPE' ELSE 'NORMAL_TYPE' END as type_check
                    FROM charging_bays 
                    WHERE bay_number = 'Bay-3'
                ");
                $bay3_test = $test_stmt->fetch(PDO::FETCH_ASSOC);
                error_log("Admin API: Bay-3 test: " . json_encode($bay3_test));
                
                // Force Bay-3 to be available for FCH tickets
                error_log("Admin API: Ensuring Bay-3 is available for FCH tickets");
                $force_bay3 = $db->prepare("UPDATE charging_bays SET status = 'Available', current_ticket_id = NULL, current_username = NULL, start_time = NULL WHERE bay_number = 'Bay-3'");
                $force_result = $force_bay3->execute();
                if ($force_result) {
                    error_log("Admin API: Successfully forced Bay-3 to Available status");
                }
                
                // Retry the bay search after ensuring Bay-3 is available
                $bay_stmt = $db->query("
                    SELECT bay_number 
                    FROM charging_bays 
                    WHERE TRIM(UPPER(status)) = 'AVAILABLE' 
                    AND bay_type = 'Fast'
                    ORDER BY bay_number ASC 
                    LIMIT 1
                ");
                $available_bay = $bay_stmt->fetch(PDO::FETCH_ASSOC);
                error_log("Admin API: After Bay-3 fix - Bay search result: " . json_encode($available_bay));
                
            } else {
                // Normal charging: only use bays 4-8
                error_log("Admin API: Searching for NCH bays 4-8 with status 'AVAILABLE'");
                $bay_stmt = $db->query("
                    SELECT bay_number 
                    FROM charging_bays 
                    WHERE TRIM(UPPER(status)) = 'AVAILABLE' 
                    AND bay_type = 'Normal'
                    ORDER BY bay_number ASC 
                    LIMIT 1
                ");
            }
            
            $available_bay = $bay_stmt->fetch(PDO::FETCH_ASSOC);
            error_log("Admin API: Query result for {$bay_type} bays: " . json_encode($available_bay));
            
            if (!$available_bay) {
                $bay_type = $is_fast_charging ? 'fast charging' : 'normal charging';
                error_log("Admin API: No available {$bay_type} bays for ticket {$ticket_id}");
                
                // Try alternative status values
                $alt_statuses = ['Available', 'available', 'AVAILABLE', ' Available ', ' available '];
                foreach ($alt_statuses as $alt_status) {
                    if ($is_fast_charging) {
                        $alt_stmt = $db->query("
                            SELECT bay_number 
                            FROM charging_bays 
                            WHERE status = '{$alt_status}' 
                            AND bay_type = 'Fast'
                            ORDER BY bay_number ASC 
                            LIMIT 1
                        ");
                    } else {
                        $alt_stmt = $db->query("
                            SELECT bay_number 
                            FROM charging_bays 
                            WHERE status = '{$alt_status}' 
                            AND bay_type = 'Normal'
                            ORDER BY bay_number ASC 
                            LIMIT 1
                        ");
                    }
                    
                    $alt_bay = $alt_stmt->fetch(PDO::FETCH_ASSOC);
                    if ($alt_bay) {
                        error_log("Admin API: Found bay {$alt_bay['bay_number']} with status '{$alt_status}'");
                        $available_bay = $alt_bay;
                        break;
                    }
                }
                
                if (!$available_bay) {
                    // Check if charging_bays table has any data at all
                    $check_stmt = $db->query("SELECT COUNT(*) as count FROM charging_bays");
                    $bay_count = $check_stmt->fetch(PDO::FETCH_ASSOC)['count'];
                    
                    if ($bay_count == 0) {
                        error_log("Admin API: charging_bays table is empty!");
                        echo json_encode(['error' => 'Charging bays table is empty. Please initialize the database.']);
                    } else {
                        echo json_encode(['error' => "No available {$bay_type} bays. Please wait for a {$bay_type} bay to become available."]);
                    }
                    break;
                }
            }
            
            $bay_number = $available_bay['bay_number'];
            error_log("Admin API: Found available bay {$bay_number} for ticket {$ticket_id}");
            
            // Validate bay assignment is correct for service type
            if ($is_fast_charging && !in_array($bay_number, ['Bay-1', 'Bay-2', 'Bay-3'])) {
                error_log("Admin API: ERROR - Fast charging ticket {$ticket_id} assigned to invalid bay {$bay_number} (should be Bay-1, Bay-2, or Bay-3)");
                echo json_encode(['error' => "Invalid bay assignment: Fast charging ticket assigned to bay {$bay_number}"]);
                break;
            } elseif (!$is_fast_charging && !in_array($bay_number, ['Bay-4', 'Bay-5', 'Bay-6', 'Bay-7', 'Bay-8'])) {
                error_log("Admin API: ERROR - Normal charging ticket {$ticket_id} assigned to invalid bay {$bay_number} (should be Bay-4 to Bay-8)");
                echo json_encode(['error' => "Invalid bay assignment: Normal charging ticket assigned to bay {$bay_number}"]);
                break;
            }
            
            // Start transaction
            $db->beginTransaction();
            
            try {
                // Update ticket status to Charging (from In Progress)
                $stmt = $db->prepare("UPDATE queue_tickets SET status = 'Charging' WHERE ticket_id = ?");
                $result = $stmt->execute([$ticket_id]);
                
                if (!$result) {
                    throw new Exception('Failed to update ticket status');
                }
                
                // Get ticket details for username
                $ticket_stmt = $db->prepare("SELECT username FROM queue_tickets WHERE ticket_id = ?");
                $ticket_stmt->execute([$ticket_id]);
                $ticket_data = $ticket_stmt->fetch(PDO::FETCH_ASSOC);
                
                if (!$ticket_data) {
                    throw new Exception('Ticket not found');
                }
                
                // CRITICAL: Remove ticket from waiting grid if it exists there
                $waiting_remove_stmt = $db->prepare("UPDATE waiting_grid SET ticket_id = NULL, username = NULL, service_type = NULL, initial_battery_level = NULL, position_in_queue = NULL WHERE ticket_id = ?");
                $waiting_remove_result = $waiting_remove_stmt->execute([$ticket_id]);
                if ($waiting_remove_result) {
                    error_log("Admin API: Removed ticket {$ticket_id} from waiting grid");
                }
                
                // Assign bay to ticket
                $bay_stmt = $db->prepare("UPDATE charging_bays SET status = 'Occupied', current_ticket_id = ?, current_username = ?, start_time = NOW() WHERE bay_number = ?");
                $bay_result = $bay_stmt->execute([$ticket_id, $ticket_data['username'], $bay_number]);
                
                if (!$bay_result) {
                    throw new Exception('Failed to assign bay');
                }
                
                // CRITICAL: Also update charging_grid table for status display
                $charging_grid_stmt = $db->prepare("INSERT INTO charging_grid (bay_number, ticket_id, username, service_type, initial_battery_level, start_time) VALUES (?, ?, ?, ?, ?, NOW()) ON DUPLICATE KEY UPDATE ticket_id = ?, username = ?, service_type = ?, initial_battery_level = ?, start_time = NOW()");
                
                // Get ticket details for charging_grid
                $ticket_details_stmt = $db->prepare("SELECT service_type, initial_battery_level FROM queue_tickets WHERE ticket_id = ?");
                $ticket_details_stmt->execute([$ticket_id]);
                $ticket_details = $ticket_details_stmt->fetch(PDO::FETCH_ASSOC);
                
                $service_type = $ticket_details['service_type'] ?? '';
                $initial_battery_level = $ticket_details['initial_battery_level'] ?? 0;
                
                $charging_grid_result = $charging_grid_stmt->execute([
                    $bay_number, $ticket_id, $ticket_data['username'], $service_type, $initial_battery_level,
                    $ticket_id, $ticket_data['username'], $service_type, $initial_battery_level
                ]);
                
                if (!$charging_grid_result) {
                    error_log("Admin API: Warning - Failed to update charging_grid for ticket {$ticket_id}");
                } else {
                    error_log("Admin API: Successfully updated charging_grid for ticket {$ticket_id} at bay {$bay_number}");
                }
                
                // Verify the bay assignment was successful
                $verify_stmt = $db->prepare("SELECT bay_number, status, current_ticket_id, current_username FROM charging_bays WHERE bay_number = ?");
                $verify_stmt->execute([$bay_number]);
                $verify_result = $verify_stmt->fetch(PDO::FETCH_ASSOC);
                error_log("Admin API: Bay {$bay_number} verification: " . json_encode($verify_result));
                
                // Verify the ticket status was updated
                $ticket_verify_stmt = $db->prepare("SELECT ticket_id, status FROM queue_tickets WHERE ticket_id = ?");
                $ticket_verify_stmt->execute([$ticket_id]);
                $ticket_verify_result = $ticket_verify_stmt->fetch(PDO::FETCH_ASSOC);
                error_log("Admin API: Ticket {$ticket_id} verification: " . json_encode($ticket_verify_result));
                
                $db->commit();
                
                error_log("Admin API: Successfully assigned ticket {$ticket_id} to bay {$bay_number}");
                
                // Trigger notification to user's phone to show Charge Now popup
                triggerUserNotification($ticket_data['username'], $ticket_id, $bay_number);
                
                echo json_encode([
                    'success' => true, 
                    'message' => 'Ticket assigned to charging bay',
                    'bay_number' => $bay_number,
                    'ticket_status' => 'Charging',
                    'bay_status' => 'Occupied'
                ]);
                
            } catch (Exception $e) {
                $db->rollback();
                error_log("Admin API: Error assigning ticket {$ticket_id} to bay: " . $e->getMessage());
                echo json_encode(['error' => 'Failed to assign ticket to bay: ' . $e->getMessage()]);
            }
            break;

        case 'progress-to-complete':
            if ($method !== 'POST') {
                echo json_encode(['error' => 'Method not allowed']);
                break;
            }
            
            $ticket_id = $_POST['ticket_id'] ?? '';
            if (!$ticket_id) {
                echo json_encode(['error' => 'Ticket ID required']);
                break;
            }
            
            // Start transaction
            $db->beginTransaction();
            
            try {
                // Get ticket details
                $ticket_stmt = $db->prepare("SELECT * FROM queue_tickets WHERE ticket_id = ?");
                $ticket_stmt->execute([$ticket_id]);
                $ticket = $ticket_stmt->fetch(PDO::FETCH_ASSOC);
                
                if (!$ticket) {
                    throw new Exception('Ticket not found');
                }
                
                // Use existing reference number from Java admin if available, otherwise generate new one
                $reference_number = $ticket['reference_number'] ?? '';
                if (empty($reference_number)) {
                    // Generate reference number (8-digit random number like Java admin)
                    $reference_number = str_pad(mt_rand(10000000, 99999999), 8, '0', STR_PAD_LEFT);
                }
                
                // Calculate payment amount using Java admin billing system
                $ratePerKwh = 15.0; // ₱15.00 per kWh (configurable from database)
                $minimumFee = 50.0; // ₱50.00 minimum fee (configurable from database)
                $fastMultiplier = 1.25; // 1.25x multiplier for fast charging (configurable from database)
                $batteryCapacityKwh = 40.0; // 40kWh battery capacity
                
                // Calculate energy used based on battery levels (like Java admin)
                $initialBatteryLevel = $ticket['initial_battery_level'] ?? 50;
                $usedFraction = (100.0 - $initialBatteryLevel) / 100.0;
                $energyUsed = $usedFraction * $batteryCapacityKwh;
                
                // Determine service multiplier
                $multiplier = 1.0; // Default for normal charging
                if (stripos($ticket['service_type'], 'fast') !== false) {
                    $multiplier = $fastMultiplier; // Apply fast charging premium
                }
                
                // Calculate gross amount (like Java admin)
                $grossAmount = $energyUsed * $ratePerKwh * $multiplier;
                $amount = max($grossAmount, $minimumFee * $multiplier); // Apply minimum fee with multiplier
                
                // Get the bay number from charging_bays table BEFORE freeing it
                $bay_stmt = $db->prepare("SELECT bay_number FROM charging_bays WHERE current_ticket_id = ?");
                $bay_stmt->execute([$ticket_id]);
                $bay_data = $bay_stmt->fetch(PDO::FETCH_ASSOC);
                $bay_number = $bay_data ? $bay_data['bay_number'] : 0;
                
                // Update ticket status to Complete, set payment to Pending, and save reference number
                $stmt = $db->prepare("UPDATE queue_tickets SET status = 'Complete', payment_status = 'Pending', reference_number = ? WHERE ticket_id = ?");
                $result = $stmt->execute([$reference_number, $ticket_id]);
                
                if (!$result) {
                    throw new Exception('Failed to update ticket status');
                }
                
                // Free up the bay
                $bay_stmt = $db->prepare("UPDATE charging_bays SET status = 'Available', current_ticket_id = NULL, current_username = NULL, start_time = NULL WHERE current_ticket_id = ?");
                $bay_result = $bay_stmt->execute([$ticket_id]);
                
                if (!$bay_result) {
                    throw new Exception('Failed to free bay');
                }
                
                // CRITICAL: Also clear charging_grid table
                $charging_grid_clear_stmt = $db->prepare("UPDATE charging_grid SET ticket_id = NULL, username = NULL, service_type = NULL, initial_battery_level = NULL, start_time = NULL WHERE ticket_id = ?");
                $charging_grid_clear_result = $charging_grid_clear_stmt->execute([$ticket_id]);
                
                if (!$charging_grid_clear_result) {
                    error_log("Admin API: Warning - Failed to clear charging_grid for ticket {$ticket_id}");
                } else {
                    error_log("Admin API: Successfully cleared charging_grid for ticket {$ticket_id}");
                }
                
                // Add to charging history
                try {
                    $history_stmt = $db->prepare("
                        INSERT INTO charging_history (
                            ticket_id, username, service_type, initial_battery_level,
                            final_battery_level, charging_time_minutes, energy_used, 
                            total_amount, reference_number, completed_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())
                    ");
                    // Reference number already determined above
                    $history_result = $history_stmt->execute([
                        $ticket_id, 
                        $ticket['username'], 
                        $ticket['service_type'], 
                        $initialBatteryLevel,
                        100, // final_battery_level (always 100% when completed)
                        0, // charging_time_minutes (not calculated yet)
                        $energyUsed, // energy_used (calculated from battery levels)
                        $amount, // total_amount (calculated using Java admin billing system)
                        $reference_number
                    ]);
                    
                    if (!$history_result) {
                        throw new Exception('Failed to add to charging history');
                    }
                } catch (Exception $history_error) {
                    // Log the error but don't fail the transaction
                    error_log("Charging history insertion failed for ticket $ticket_id: " . $history_error->getMessage());
                    // Continue with the transaction - bay is already freed and ticket marked complete
                }
                
                $db->commit();
                
                echo json_encode([
                    'success' => true, 
                    'message' => 'Ticket marked as complete',
                    'reference_number' => $reference_number
                ]);
                
            } catch (Exception $e) {
                $db->rollback();
                echo json_encode(['error' => 'Failed to complete ticket: ' . $e->getMessage()]);
            }
            break;

        case 'set-bay-maintenance':
            if ($method !== 'POST') {
                echo json_encode(['error' => 'Method not allowed']);
                break;
            }
            
            $bay_number = $_POST['bay_number'] ?? '';
            if (!$bay_number) {
                echo json_encode(['error' => 'Bay number required']);
                break;
            }
            
            // Set bay to maintenance
            $stmt = $db->prepare("UPDATE charging_bays SET status = 'Maintenance' WHERE bay_number = ?");
            $result = $stmt->execute([$bay_number]);
            
            if ($result) {
                echo json_encode(['success' => true, 'message' => 'Bay set to maintenance mode']);
            } else {
                echo json_encode(['error' => 'Failed to set bay to maintenance']);
            }
            break;

        case 'set-bay-available':
            if ($method !== 'POST') {
                echo json_encode(['error' => 'Method not allowed']);
                break;
            }
            
            $bay_number = $_POST['bay_number'] ?? '';
            if (!$bay_number) {
                echo json_encode(['error' => 'Bay number required']);
                break;
            }
            
            // Set bay to available
            $stmt = $db->prepare("UPDATE charging_bays SET status = 'Available' WHERE bay_number = ?");
            $result = $stmt->execute([$bay_number]);
            
            if ($result) {
                echo json_encode(['success' => true, 'message' => 'Bay set to available']);
            } else {
                echo json_encode(['error' => 'Failed to set bay to available']);
            }
            break;

        case 'add-user':
            if ($method !== 'POST') {
                echo json_encode(['error' => 'Method not allowed']);
                break;
            }
            
            $username = $_POST['username'] ?? '';
            $firstname = $_POST['firstname'] ?? '';
            $lastname = $_POST['lastname'] ?? '';
            $email = $_POST['email'] ?? '';
            $password = $_POST['password'] ?? '';
            
            if (!$username || !$firstname || !$lastname || !$email || !$password) {
                echo json_encode(['error' => 'All fields are required']);
                break;
            }
            
            // Check if username already exists
            $stmt = $db->prepare("SELECT username FROM users WHERE username = ?");
            $stmt->execute([$username]);
            if ($stmt->fetch()) {
                echo json_encode(['error' => 'Username already exists']);
                break;
            }
            
            // Insert new user
            $stmt = $db->prepare("
                INSERT INTO users (username, firstname, lastname, email, password) 
                VALUES (?, ?, ?, ?, ?)
            ");
            $result = $stmt->execute([$username, $firstname, $lastname, $email, $password]);
            
            if ($result) {
                echo json_encode(['success' => true, 'message' => 'User added successfully']);
            } else {
                echo json_encode(['error' => 'Failed to add user']);
            }
            break;

        case 'delete-user':
            if ($method !== 'POST') {
                echo json_encode(['error' => 'Method not allowed']);
                break;
            }
            
            $username = $_POST['username'] ?? '';
            if (!$username) {
                echo json_encode(['error' => 'Username required']);
                break;
            }
            
            // Delete user
            $stmt = $db->prepare("DELETE FROM users WHERE username = ?");
            $result = $stmt->execute([$username]);
            
            if ($result) {
                echo json_encode(['success' => true, 'message' => 'User deleted successfully']);
            } else {
                echo json_encode(['error' => 'Failed to delete user']);
            }
            break;

        case 'business-settings':
            // Get current business settings from database
            try {
                $stmt = $db->prepare("SELECT setting_value FROM system_settings WHERE setting_key = ?");
                
                // Get minimum fee
                $stmt->execute(['minimum_fee']);
                $minFeeRow = $stmt->fetch(PDO::FETCH_ASSOC);
                $minFee = $minFeeRow ? floatval($minFeeRow['setting_value']) : 50.0;
                
                // Get rate per kWh
                $stmt->execute(['rate_per_kwh']);
                $rateRow = $stmt->fetch(PDO::FETCH_ASSOC);
                $ratePerKwh = $rateRow ? floatval($rateRow['setting_value']) : 15.0;
                
                $settings = [
                    'min_fee' => $minFee,
                    'kwh_per_peso' => $ratePerKwh
                ];
                
                echo json_encode([
                    'success' => true,
                    'settings' => $settings
                ]);
            } catch (Exception $e) {
                echo json_encode([
                    'success' => false,
                    'error' => 'Failed to load business settings: ' . $e->getMessage()
                ]);
            }
            break;

        case 'save-business-settings':
            if ($method !== 'POST') {
                echo json_encode(['error' => 'Method not allowed']);
                break;
            }

            $min_fee = $_POST['min_fee'] ?? '';
            $kwh_per_peso = $_POST['kwh_per_peso'] ?? '';

            if ($min_fee === '' || $kwh_per_peso === '') {
                echo json_encode(['error' => 'Both business values are required']);
                break;
            }

            // Validate values
            $min_fee = floatval($min_fee);
            $kwh_per_peso = floatval($kwh_per_peso);

            if ($min_fee < 0 || $kwh_per_peso <= 0) {
                echo json_encode(['error' => 'Invalid business values (min fee >= 0, kWh per peso > 0)']);
                break;
            }

            try {
                // Update minimum fee in system_settings table
                $stmt = $db->prepare("UPDATE system_settings SET setting_value = ? WHERE setting_key = 'minimum_fee'");
                $stmt->execute([$min_fee]);
                
                // Update rate per kWh in system_settings table
                $stmt = $db->prepare("UPDATE system_settings SET setting_value = ? WHERE setting_key = 'rate_per_kwh'");
                $stmt->execute([$kwh_per_peso]);
                
                echo json_encode(['success' => true, 'message' => 'Business settings updated successfully']);
            } catch (Exception $e) {
                echo json_encode(['error' => 'Failed to save business settings: ' . $e->getMessage()]);
            }
            break;

        case 'analytics':
            // Get range parameter (day, week, month)
            $range = $_GET['range'] ?? 'week';
            $interval = match($range) {
                'day' => 'INTERVAL 7 DAY', // Daily view shows 7 days
                'week' => 'INTERVAL 14 DAY', // Weekly view shows 2 weeks
                'month' => 'INTERVAL 30 DAY',
                default => 'INTERVAL 7 DAY'
            };

            // Get revenue data - using charging_history table to match Admin Java
            $stmt = $db->query("
                SELECT
                    DATE(completed_at) as date,
                    SUM(total_amount) as revenue
                FROM charging_history
                WHERE completed_at >= DATE_SUB(CURDATE(), $interval)
                GROUP BY DATE(completed_at)
                ORDER BY DATE(completed_at)
            ");
            $revenue_data = $stmt->fetchAll(PDO::FETCH_ASSOC);

            // Get service usage data broken down by service type
            $stmt = $db->query("
                SELECT
                    DATE(completed_at) as date,
                    service_type,
                    COUNT(*) as service_count
                FROM charging_history
                WHERE completed_at >= DATE_SUB(CURDATE(), $interval)
                GROUP BY DATE(completed_at), service_type
                ORDER BY DATE(completed_at), service_type
            ");
            $service_data_raw = $stmt->fetchAll(PDO::FETCH_ASSOC);

            // Process service data to separate Normal and Fast Charging
            $service_data = [];
            $normal_data = [];
            $fast_data = [];
            
            // Create arrays for each service type
            foreach ($service_data_raw as $row) {
                $date = $row['date'];
                $service_type = $row['service_type'];
                $count = (int)$row['service_count'];
                
                // Determine if it's fast charging
                $is_fast = (stripos($service_type, 'fast') !== false) || (stripos($service_type, 'FCH') !== false);
                
                if ($is_fast) {
                    $fast_data[$date] = $count;
                } else {
                    $normal_data[$date] = $count;
                }
            }
            
            // Get all unique dates from the data
            $all_dates = array_unique(array_column($service_data_raw, 'date'));
            sort($all_dates);
            
            // Create structured data with all dates
            foreach ($all_dates as $date) {
                $service_data[] = [
                    'date' => $date,
                    'normal_count' => $normal_data[$date] ?? 0,
                    'fast_count' => $fast_data[$date] ?? 0,
                    'service_count' => ($normal_data[$date] ?? 0) + ($fast_data[$date] ?? 0) // Total for backward compatibility
                ];
            }

            echo json_encode([
                'success' => true,
                'revenue_data' => $revenue_data,
                'service_data' => $service_data
            ]);
            break;

        case 'debug-tables':
            // Debug endpoint to check table structure
            try {
                $tables_info = [];
                
                // Check charging_bays table
                $stmt = $db->query("SHOW TABLES LIKE 'charging_bays'");
                if ($stmt->rowCount() > 0) {
                    $columns = $db->query("DESCRIBE charging_bays")->fetchAll(PDO::FETCH_ASSOC);
                    $tables_info['charging_bays'] = $columns;
                    
                    // Get sample data
                    $sample_data = $db->query("SELECT * FROM charging_bays LIMIT 3")->fetchAll(PDO::FETCH_ASSOC);
                    $tables_info['charging_bays_sample'] = $sample_data;
                }
                
                // Check payment_transactions table
                $stmt = $db->query("SHOW TABLES LIKE 'payment_transactions'");
                if ($stmt->rowCount() > 0) {
                    $columns = $db->query("DESCRIBE payment_transactions")->fetchAll(PDO::FETCH_ASSOC);
                    $tables_info['payment_transactions'] = $columns;
                }
                
                // Check charging_history table
                $stmt = $db->query("SHOW TABLES LIKE 'charging_history'");
                if ($stmt->rowCount() > 0) {
                    $columns = $db->query("DESCRIBE charging_history")->fetchAll(PDO::FETCH_ASSOC);
                    $tables_info['charging_history'] = $columns;
                    
                    // Get sample data with specific column order
                    $history_data = $db->query("SELECT ticket_id, username, service_type, initial_battery_level, charging_time_minutes, energy_used, total_amount, reference_number, completed_at FROM charging_history ORDER BY completed_at DESC LIMIT 3")->fetchAll(PDO::FETCH_ASSOC);
                    $tables_info['charging_history_sample'] = $history_data;
                    
                    // Get numeric indexed data to debug column order
                    $history_numeric = $db->query("SELECT ticket_id, username, service_type, initial_battery_level, charging_time_minutes, energy_used, total_amount, reference_number, completed_at FROM charging_history ORDER BY completed_at DESC LIMIT 3")->fetchAll(PDO::FETCH_NUM);
                    $tables_info['charging_history_numeric'] = $history_numeric;
                }
                
                // Check queue_tickets table
                $stmt = $db->query("SHOW TABLES LIKE 'queue_tickets'");
                if ($stmt->rowCount() > 0) {
                    $columns = $db->query("DESCRIBE queue_tickets")->fetchAll(PDO::FETCH_ASSOC);
                    $tables_info['queue_tickets'] = $columns;
                    
                    // Get current tickets
                    $tickets_data = $db->query("SELECT * FROM queue_tickets ORDER BY created_at DESC LIMIT 5")->fetchAll(PDO::FETCH_ASSOC);
                    $tables_info['queue_tickets_sample'] = $tickets_data;
                }
                
                // Check users table
                $stmt = $db->query("SHOW TABLES LIKE 'users'");
                if ($stmt->rowCount() > 0) {
                    $columns = $db->query("DESCRIBE users")->fetchAll(PDO::FETCH_ASSOC);
                    $tables_info['users'] = $columns;
                    
                    // Get current users
                    $users_data = $db->query("SELECT username, firstname, lastname FROM users LIMIT 5")->fetchAll(PDO::FETCH_ASSOC);
                    $tables_info['users_sample'] = $users_data;
                }
                
                echo json_encode([
                    'success' => true,
                    'tables' => $tables_info
                ]);
            } catch (Exception $e) {
                echo json_encode([
                    'success' => false,
                    'error' => 'Debug error: ' . $e->getMessage()
                ]);
            }
            break;

        case 'transactions':
            // Get transaction history ONLY from charging_history table
            $type_filter = $_GET['type'] ?? '';
            $status_filter = $_GET['status'] ?? '';
            $date_from = $_GET['date_from'] ?? '';
            $date_to = $_GET['date_to'] ?? '';
            
            $transactions = [];
            $errors = [];
            
            try {
                // Check if charging_history table exists
                $tables_check = $db->query("SHOW TABLES LIKE 'charging_history'");
                $charging_table_exists = $tables_check->rowCount() > 0;
                
                if (!$charging_table_exists) {
                    echo json_encode([
                        'success' => false,
                        'error' => 'charging_history table not found in database'
                    ]);
                    break;
                }
                
                // Check if required columns exist, if not create them
                $columns_check = $db->query("SHOW COLUMNS FROM charging_history LIKE 'energy_used'");
                if ($columns_check->rowCount() == 0) {
                    $db->exec("ALTER TABLE charging_history ADD COLUMN energy_used DECIMAL(10,2) NOT NULL DEFAULT 0.0 COMMENT 'Energy consumed in kWh' AFTER charging_time_minutes");
                }
                
                $columns_check = $db->query("SHOW COLUMNS FROM charging_history LIKE 'reference_number'");
                if ($columns_check->rowCount() == 0) {
                    $db->exec("ALTER TABLE charging_history ADD COLUMN reference_number VARCHAR(50) DEFAULT NULL COMMENT 'Transaction reference number' AFTER total_amount");
                }
                
                // Build WHERE clause for filters
                $where_conditions = [];
                $params = [];
                
                if ($type_filter && $type_filter !== '') {
                    $where_conditions[] = "ch.service_type = ?";
                    $params[] = $type_filter;
                }
                
                if ($status_filter && $status_filter !== '') {
                    $where_conditions[] = "ch.status = ?";
                    $params[] = $status_filter;
                }
                
                if ($date_from && $date_from !== '') {
                    $where_conditions[] = "ch.completed_at >= ?";
                    $params[] = $date_from . ' 00:00:00';
                }
                
                if ($date_to && $date_to !== '') {
                    $where_conditions[] = "ch.completed_at <= ?";
                    $params[] = $date_to . ' 23:59:59';
                }
                
                $where_clause = '';
                if (!empty($where_conditions)) {
                    $where_clause = 'WHERE ' . implode(' AND ', $where_conditions);
                }
                
                // Get charging history ONLY from charging_history table - ONLY 6 COLUMNS
                $sql = "
                    SELECT 
                        ch.ticket_id,
                        ch.username,
                        ch.energy_used as energy_kwh,
                        ch.total_amount,
                        ch.reference_number,
                        ch.completed_at as transaction_date
                    FROM charging_history ch
                    $where_clause
                    ORDER BY ch.completed_at DESC
                    LIMIT 100
                ";
                
                $stmt = $db->prepare($sql);
                $stmt->execute($params);
                $transactions = $stmt->fetchAll(PDO::FETCH_ASSOC);
                
                // Update missing energy_used values
                $db->exec("UPDATE charging_history SET energy_used = ((100 - initial_battery_level) / 100.0) * 40.0 WHERE energy_used = 0.0 OR energy_used IS NULL");
                
                // Update missing reference_number values
                $db->exec("UPDATE charging_history SET reference_number = CONCAT('REF', ticket_id, '_', UNIX_TIMESTAMP(NOW())) WHERE reference_number IS NULL OR reference_number = '' OR reference_number = 'N/A'");
                
                // Clean and validate transaction data to prevent Excel ########## issue
                foreach ($transactions as &$transaction) {
                    // Ensure numeric values are properly formatted
                    if (isset($transaction['total_amount'])) {
                        $transaction['total_amount'] = is_numeric($transaction['total_amount']) ? 
                            number_format((float)$transaction['total_amount'], 2, '.', '') : '0.00';
                    } else {
                        $transaction['total_amount'] = '0.00';
                    }
                    
                    if (isset($transaction['energy_kwh'])) {
                        $transaction['energy_kwh'] = is_numeric($transaction['energy_kwh']) ? 
                            number_format((float)$transaction['energy_kwh'], 2, '.', '') : '0.00';
                    } else {
                        $transaction['energy_kwh'] = '0.00';
                    }
                    
                    // Ensure reference_number is not null
                    if (empty($transaction['reference_number'])) {
                        $transaction['reference_number'] = 'N/A';
                    }
                    
                    // Ensure ticket_id and username are not null
                    if (empty($transaction['ticket_id'])) {
                        $transaction['ticket_id'] = 'N/A';
                    }
                    
                    if (empty($transaction['username'])) {
                        $transaction['username'] = 'N/A';
                    }
                }
                unset($transaction); // Break reference
                
                // Sort by transaction date
                usort($transactions, function($a, $b) {
                    $dateA = $a['transaction_date'] ?? '1970-01-01';
                    $dateB = $b['transaction_date'] ?? '1970-01-01';
                    return strtotime($dateB) - strtotime($dateA);
                });
                
                // Limit to 100 total
                $transactions = array_slice($transactions, 0, 100);
                
                $response = [
                    'success' => true,
                    'data' => $transactions,
                    'transactions' => $transactions,
                    'count' => count($transactions)
                ];
                
                if (!empty($errors)) {
                    $response['warnings'] = $errors;
                }
                
                echo json_encode($response);
                
            } catch (Exception $e) {
                echo json_encode([
                    'success' => false,
                    'error' => 'Charging history error: ' . $e->getMessage()
                ]);
            }
            break;

        case 'progress-next-ticket':
            if ($method !== 'POST') {
                echo json_encode(['error' => 'Method not allowed']);
                break;
            }
            
            // Find the next waiting ticket
            $stmt = $db->query("
                SELECT ticket_id, username, service_type 
                FROM queue_tickets 
                WHERE status = 'Waiting' 
                ORDER BY created_at ASC 
                LIMIT 1
            ");
            $next_ticket = $stmt->fetch(PDO::FETCH_ASSOC);
            
            if ($next_ticket) {
                // Update ticket status to Processing
                $update_stmt = $db->prepare("UPDATE queue_tickets SET status = 'Processing' WHERE ticket_id = ?");
                $result = $update_stmt->execute([$next_ticket['ticket_id']]);
                
                if ($result) {
                    echo json_encode([
                        'success' => true,
                        'message' => 'Next ticket processed successfully',
                        'ticket' => $next_ticket
                    ]);
                } else {
                    echo json_encode(['error' => 'Failed to process next ticket']);
                }
            } else {
                echo json_encode(['error' => 'No waiting tickets found']);
            }
            break;

        case 'mark-payment-paid':
            if ($method !== 'POST') {
                echo json_encode(['error' => 'Method not allowed']);
                break;
            }
            
            $ticket_id = $_POST['ticket_id'] ?? '';
            if (!$ticket_id) {
                echo json_encode(['error' => 'Ticket ID required']);
                break;
            }
            
            // Debug: Log the request
            error_log("Payment Debug - Starting payment for ticket: $ticket_id");
            
            // Start transaction
            $db->beginTransaction();
            
            try {
                // Get ticket details
                $ticket_stmt = $db->prepare("SELECT * FROM queue_tickets WHERE ticket_id = ?");
                $ticket_stmt->execute([$ticket_id]);
                $ticket = $ticket_stmt->fetch(PDO::FETCH_ASSOC);
                
                if (!$ticket) {
                    throw new Exception('Ticket not found');
                }
                
                // Debug: Log ticket data
                error_log("Payment Debug - Ticket Data: " . json_encode($ticket));
                
                // Use existing reference number from Java admin if available, otherwise generate new one
                $reference_number = $ticket['reference_number'] ?? '';
                if (empty($reference_number)) {
                    // Generate reference number (8-digit random number like Java admin)
                    $reference_number = str_pad(mt_rand(10000000, 99999999), 8, '0', STR_PAD_LEFT);
                    error_log("Payment Debug - Generated new reference number: $reference_number");
                } else {
                    error_log("Payment Debug - Using existing reference number from Java admin: $reference_number");
                }
                
                // Get bay number if ticket was assigned to a bay
                $bay_stmt = $db->prepare("SELECT bay_number FROM charging_bays WHERE current_ticket_id = ?");
                $bay_stmt->execute([$ticket_id]);
                $bay_data = $bay_stmt->fetch(PDO::FETCH_ASSOC);
                $bay_number = $bay_data ? $bay_data['bay_number'] : 0;
                
                // Calculate payment amount using Java admin billing system
                $ratePerKwh = 15.0; // ₱15.00 per kWh (configurable from database)
                $minimumFee = 50.0; // ₱50.00 minimum fee (configurable from database)
                $fastMultiplier = 1.25; // 1.25x multiplier for fast charging (configurable from database)
                $batteryCapacityKwh = 40.0; // 40kWh battery capacity
                
                // Calculate energy used based on battery levels (like Java admin)
                $initialBatteryLevel = $ticket['initial_battery_level'] ?? 50;
                $usedFraction = (100.0 - $initialBatteryLevel) / 100.0;
                $energyUsed = $usedFraction * $batteryCapacityKwh;
                
                // Determine service multiplier
                $multiplier = 1.0; // Default for normal charging
                if (stripos($ticket['service_type'], 'fast') !== false) {
                    $multiplier = $fastMultiplier; // Apply fast charging premium
                }
                
                // Calculate gross amount (like Java admin)
                $grossAmount = $energyUsed * $ratePerKwh * $multiplier;
                $amount = max($grossAmount, $minimumFee * $multiplier); // Apply minimum fee with multiplier
                
                // Calculate charging time minutes (matching Admin Java logic)
                $batteryNeeded = 100 - $initialBatteryLevel;
                if ($multiplier > 1.0) {
                    // Fast charging: 0.8 minutes per 1%
                    $chargingTimeMinutes = (int)($batteryNeeded * 0.8);
                } else {
                    // Normal charging: 1.6 minutes per 1%
                    $chargingTimeMinutes = (int)($batteryNeeded * 1.6);
                }
                
                error_log("Payment Debug - Charging time calculation: Service='{$ticket['service_type']}', IsFast=" . ($multiplier > 1.0 ? 'Yes' : 'No') . ", InitialBattery={$initialBatteryLevel}%, BatteryNeeded={$batteryNeeded}%, ChargingTime={$chargingTimeMinutes} minutes");
                
                // Reference number already determined above
                
                // Check if username exists in users table
                error_log("Payment Debug - Checking user: " . $ticket['username']);
                $user_check_stmt = $db->prepare("SELECT username FROM users WHERE username = ?");
                $user_check_stmt->execute([$ticket['username']]);
                $user_exists = $user_check_stmt->fetch();
                
                if (!$user_exists) {
                    error_log("Payment Debug - User not found: " . $ticket['username']);
                    throw new Exception('User ' . $ticket['username'] . ' does not exist in users table');
                }
                
                error_log("Payment Debug - User exists: " . $ticket['username']);
                
                // Insert payment transaction
                error_log("Payment Debug - Creating payment transaction for amount: $amount");
                $payment_stmt = $db->prepare("
                    INSERT INTO payment_transactions (
                        ticket_id, username, amount, payment_method, 
                        reference_number, transaction_status, processed_at
                    ) VALUES (?, ?, ?, ?, ?, ?, NOW())
                ");
                $payment_result = $payment_stmt->execute([
                    $ticket_id,
                    $ticket['username'],
                    $amount,
                    $ticket['payment_method'] ?? 'Cash',
                    $reference_number,
                    'Completed'
                ]);
                
                if (!$payment_result) {
                    $error_info = $payment_stmt->errorInfo();
                    error_log("Payment Debug - Payment transaction failed: " . json_encode($error_info));
                    throw new Exception('Failed to create payment transaction: ' . $error_info[2]);
                }
                
                error_log("Payment Debug - Payment transaction created successfully");
                
                // Update queue_tickets table with reference number before moving to history
                $update_ref_stmt = $db->prepare("UPDATE queue_tickets SET reference_number = ? WHERE ticket_id = ?");
                $update_ref_stmt->execute([$reference_number, $ticket_id]);
                error_log("Payment Debug - Updated queue_tickets with reference number: $reference_number");
                
                // Add to charging history
                try {
                    $history_stmt = $db->prepare("
                        INSERT INTO charging_history (
                            ticket_id, username, service_type, initial_battery_level,
                            final_battery_level, charging_time_minutes, energy_used, 
                            total_amount, reference_number, completed_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())
                    ");
                    $history_result = $history_stmt->execute([
                        $ticket_id, 
                        $ticket['username'], 
                        $ticket['service_type'], 
                        $initialBatteryLevel,
                        100, // final_battery_level (always 100% when completed)
                        $chargingTimeMinutes, // charging_time_minutes (calculated using Admin Java logic)
                        $energyUsed, // energy_used (calculated from battery levels)
                        $amount, // total_amount (calculated using Java admin billing system)
                        $reference_number
                    ]);
                    
                    if (!$history_result) {
                        throw new Exception('Failed to add to charging history');
                    }
                } catch (Exception $history_error) {
                    // Log the error but don't fail the transaction
                    error_log("Charging history insertion failed for ticket $ticket_id: " . $history_error->getMessage());
                }
                
                // Clear active ticket (if exists) - like Java admin
                try {
                    $active_stmt = $db->prepare("DELETE FROM active_tickets WHERE ticket_id = ?");
                    $active_stmt->execute([$ticket_id]);
                } catch (Exception $e) {
                    // Don't fail if active_tickets table doesn't exist
                    error_log("Payment Debug - Active tickets table may not exist: " . $e->getMessage());
                }
                
                // Clear charging bays - like Java admin
                $bay_stmt = $db->prepare("UPDATE charging_bays SET current_ticket_id = NULL, current_username = NULL, status = 'Available', start_time = NULL WHERE current_ticket_id = ?");
                $bay_stmt->execute([$ticket_id]);
                
                // Clear charging grid - like Java admin
                try {
                    $grid_stmt = $db->prepare("UPDATE charging_grid SET ticket_id = NULL, username = NULL, service_type = NULL, initial_battery_level = NULL, start_time = NULL WHERE ticket_id = ?");
                    $grid_stmt->execute([$ticket_id]);
                } catch (Exception $e) {
                    // Don't fail if charging_grid table doesn't exist
                    error_log("Payment Debug - Charging grid table may not exist: " . $e->getMessage());
                }
                
                // Update user's battery level to 100% when charging is completed - like Java admin
                try {
                    $battery_stmt = $db->prepare("UPDATE battery_levels SET battery_level = 100, last_updated = NOW() WHERE username = ?");
                    $battery_stmt->execute([$ticket['username']]);
                } catch (Exception $e) {
                    // Don't fail if battery_levels table doesn't exist
                    error_log("Payment Debug - Battery levels table may not exist: " . $e->getMessage());
                }
                
                // Remove ticket from queue_tickets table (move to history) - like Java admin
                $delete_stmt = $db->prepare("DELETE FROM queue_tickets WHERE ticket_id = ?");
                $delete_result = $delete_stmt->execute([$ticket_id]);
                
                if (!$delete_result) {
                    throw new Exception('Failed to remove ticket from queue');
                }
                
                $db->commit();
                
                echo json_encode([
                    'success' => true,
                    'message' => 'Payment processed and ticket moved to history',
                    'amount' => $amount,
                    'reference_number' => $reference_number
                ]);
                
            } catch (Exception $e) {
                $db->rollback();
                echo json_encode(['error' => 'Failed to process payment: ' . $e->getMessage()]);
            }
            break;

        case 'delete-ticket':
            if ($method !== 'POST') { echo json_encode(['error' => 'Method not allowed']); break; }
            $ticket_id = $_POST['ticket_id'] ?? '';
            if (!$ticket_id) { echo json_encode(['error' => 'Ticket ID required']); break; }

            try {
                $db->beginTransaction();

                // Remove from waiting_grid if present
                $wg = $db->prepare("UPDATE waiting_grid SET ticket_id = NULL, username = NULL, service_type = NULL, initial_battery_level = NULL, position_in_queue = NULL WHERE ticket_id = ?");
                $wg->execute([$ticket_id]);

                // Free the bay if assigned to this ticket
                $freeBay = $db->prepare("UPDATE charging_bays SET current_ticket_id = NULL, current_username = NULL, status = 'Available', start_time = NULL WHERE current_ticket_id = ?");
                $freeBay->execute([$ticket_id]);

                // Clear charging_grid entries
                $clearGrid = $db->prepare("UPDATE charging_grid SET ticket_id = NULL, username = NULL, service_type = NULL, initial_battery_level = NULL, start_time = NULL WHERE ticket_id = ?");
                $clearGrid->execute([$ticket_id]);

                // Delete from queue_tickets
                $del = $db->prepare('DELETE FROM queue_tickets WHERE ticket_id = ?');
                $del->execute([$ticket_id]);

                $db->commit();
                echo json_encode(['success' => true, 'message' => 'Ticket deleted']);
            } catch (Exception $e) {
                $db->rollback();
                echo json_encode(['error' => 'Failed to delete ticket: ' . $e->getMessage()]);
            }
            break;


        default:
            echo json_encode([
                'error' => 'Invalid action',
                'available_actions' => [
                    'dashboard', 'queue', 'bays', 'users', 'staff', 'staff-activity', 'add-staff', 'reset-staff-password', 'delete-staff', 'ticket-details',
                    'process-ticket', 'progress-to-waiting', 'progress-to-charging', 'progress-to-complete', 'auto-assign-waiting-tickets',
                    'mark-payment-paid', 'set-bay-maintenance', 'set-bay-available',
                    'add-user', 'delete-user', 'settings', 'save-settings', 'analytics', 'transactions', 'progress-next-ticket'
                ]
            ]);
            break;
    }
} catch (Exception $e) {
    echo json_encode(['error' => 'Server error: ' . $e->getMessage()]);
}

/**
 * Trigger notification to user's phone to show Charge Now popup
 * This function creates a notification record that the user's phone can check
 */
function triggerUserNotification($username, $ticketId, $bayNumber) {
    try {
        global $db;
        
        // Create notification record in database
        $stmt = $db->prepare("
            INSERT INTO user_notifications (username, notification_type, ticket_id, bay_number, message, created_at, is_read) 
            VALUES (?, 'CHARGE_NOW', ?, ?, ?, NOW(), 0)
        ");
        
        $message = "It's your turn to charge! Please proceed to {$bayNumber} to begin your charging session.";
        $result = $stmt->execute([$username, $ticketId, $bayNumber, $message]);
        
        if ($result) {
            error_log("Admin API: Notification triggered for user {$username} - ticket {$ticketId} - bay {$bayNumber}");
        } else {
            error_log("Admin API: Failed to create notification for user {$username}");
        }
        
        // Also try to send via WebSocket if available (for real-time updates)
        sendWebSocketNotification($username, $ticketId, $bayNumber);
        
    } catch (Exception $e) {
        error_log("Admin API: Error triggering user notification: " . $e->getMessage());
    }
}

/**
 * Send WebSocket notification for real-time updates
 */
function sendWebSocketNotification($username, $ticketId, $bayNumber) {
    try {
        // This would integrate with the WebSocket server if available
        // For now, we'll log the attempt
        error_log("Admin API: WebSocket notification for user {$username} - ticket {$ticketId} - bay {$bayNumber}");
        
        // TODO: Implement WebSocket integration when available
        // This could send a message to the Monitor WebSocket server
        // which could then broadcast to connected user clients
        
    } catch (Exception $e) {
        error_log("Admin API: WebSocket notification error: " . $e->getMessage());
    }
}
?>
