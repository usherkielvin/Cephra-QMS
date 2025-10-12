<?php
// Disable error display to prevent breaking JSON
ini_set('display_errors', 0);
ini_set('log_errors', 1);

session_start();
if (!isset($_SESSION['username'])) {
    http_response_code(401);
    echo json_encode(['success' => false, 'error' => 'Unauthorized']);
    exit();
}

require_once '../config/database.php';

header('Content-Type: application/json');

$db = new Database();
$conn = $db->getConnection();

if (!$conn) {
    echo json_encode(['success' => false, 'error' => 'Database connection failed']);
    exit();
}

$input = json_decode(file_get_contents('php://input'), true);
$username = $_SESSION['username'];
$ticket_id = $input['ticket_id'] ?? '';
$payment_method = $input['payment_method'] ?? '';
$amount = floatval($input['amount'] ?? 0);

if (!$ticket_id || !$payment_method || $amount <= 0) {
    echo json_encode(['success' => false, 'error' => 'Invalid parameters']);
    exit();
}

try {
    $conn->beginTransaction();
    
    // Get ticket details
    $stmt = $conn->prepare("SELECT * FROM queue_tickets WHERE ticket_id = ? AND username = ?");
    $stmt->execute([$ticket_id, $username]);
    $ticket = $stmt->fetch(PDO::FETCH_ASSOC);
    
    if (!$ticket) {
        $conn->rollback();
        echo json_encode(['success' => false, 'error' => 'Ticket not found']);
        exit();
    }
    
    $remaining_balance = null;
    
    // Handle different payment methods
    if ($payment_method === 'ewallet') {
        // Check wallet balance
        $stmt = $conn->prepare("SELECT balance FROM wallet_balance WHERE username = ? FOR UPDATE");
        $stmt->execute([$username]);
        $wallet = $stmt->fetch(PDO::FETCH_ASSOC);
        
        if (!$wallet || $wallet['balance'] < $amount) {
            $conn->rollback();
            echo json_encode(['success' => false, 'error' => 'Insufficient wallet balance']);
            exit();
        }
        
        // Deduct from wallet
        $new_balance = $wallet['balance'] - $amount;
        $stmt = $conn->prepare("UPDATE wallet_balance SET balance = ?, updated_at = NOW() WHERE username = ?");
        $stmt->execute([$new_balance, $username]);
        
        // Record wallet transaction
        $reference = 'WT' . date('YmdHis') . rand(100, 999);
        $stmt = $conn->prepare("INSERT INTO wallet_transactions (username, transaction_type, amount, previous_balance, new_balance, description, reference_id, transaction_date) VALUES (?, 'PAYMENT', ?, ?, ?, 'Charging payment - E-Wallet', ?, NOW())");
        $stmt->execute([$username, $amount, $wallet['balance'], $new_balance, $reference]);
        
        $remaining_balance = $new_balance;
        
    } elseif ($payment_method === 'cash') {
        // For cash payment, no wallet deduction needed
        // Just record the payment transaction
        $reference = 'CASH' . date('YmdHis') . rand(100, 999);
    }
    
    // Update queue_tickets: detect available columns and update only those
    $stmt = $conn->prepare("SHOW COLUMNS FROM queue_tickets LIKE 'status'");
    $stmt->execute();
    $hasQueueStatus = (bool) $stmt->fetch(PDO::FETCH_ASSOC);

    $stmt = $conn->prepare("SHOW COLUMNS FROM queue_tickets LIKE 'payment_status'");
    $stmt->execute();
    $hasQueuePaymentStatus = (bool) $stmt->fetch(PDO::FETCH_ASSOC);

    $updateParts = [];
    if ($hasQueuePaymentStatus) {
        $updateParts[] = "payment_status = 'paid'";
    }
    if ($hasQueueStatus) {
        $updateParts[] = "status = 'completed'";
    }

    if (count($updateParts) > 0) {
        $updateSql = "UPDATE queue_tickets SET " . implode(', ', $updateParts) . " WHERE ticket_id = ? AND username = ?";
        $stmt = $conn->prepare($updateSql);
        $stmt->execute([$ticket_id, $username]);
    } else {
        // No relevant columns; log and continue
        error_log("Payment: queue_tickets has no payment_status or status columns to update for ticket " . $ticket_id);
    }
    
    // Record payment transaction
    $transaction_id = 'TXN' . date('YmdHis') . rand(100, 999);
    // Insert using columns that exist in the payment_transactions table (include reference_number)
    $stmt = $conn->prepare("INSERT INTO payment_transactions (ticket_id, username, amount, payment_method, reference_number, transaction_status, processed_at) VALUES (?, ?, ?, ?, ?, 'completed', NOW())");
    $stmt->execute([$ticket_id, $username, $amount, $payment_method, $transaction_id]);
    
    // Update battery level to 100% when charging is complete and paid
    $stmt = $conn->prepare("UPDATE battery_levels SET battery_level = 100, last_updated = NOW() WHERE username = ?");
    $result = $stmt->execute([$username]);
    
    if ($result) {
        error_log("Payment: Successfully set battery to 100% for user $username");
    } else {
        error_log("Payment: Failed to set battery to 100% for user $username");
    }
    
    // Save to charging_history
    // charging_history has a UNIQUE constraint on ticket_id; avoid duplicate-key errors by
    // checking for an existing row and performing an UPDATE if present.
    $stmt = $conn->prepare("SELECT id FROM charging_history WHERE ticket_id = ?");
    $stmt->execute([$ticket_id]);
    $existing = $stmt->fetch(PDO::FETCH_ASSOC);

    if ($existing) {
        // Update existing charging_history record with finalized values
        $stmt = $conn->prepare("UPDATE charging_history SET username = ?, service_type = ?, initial_battery_level = ?, final_battery_level = ?, charging_time_minutes = ?, energy_used = ?, total_amount = ?, reference_number = ?, served_by = ?, completed_at = NOW() WHERE ticket_id = ?");
        $stmt->execute([
            $username,
            $ticket['service_type'],
            $ticket['initial_battery_level'] ?? 0,
            100,
            0,
            0.0,
            $amount,
            $transaction_id,
            'System',
            $ticket_id
        ]);
    } else {
        // Insert new charging_history record
        $stmt = $conn->prepare("INSERT INTO charging_history (ticket_id, username, service_type, initial_battery_level, final_battery_level, charging_time_minutes, energy_used, total_amount, reference_number, served_by, completed_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())");
        $stmt->execute([
            $ticket_id,
            $username,
            $ticket['service_type'],
            $ticket['initial_battery_level'] ?? 0,
            100, // final battery level after charging
            0,   // charging_time_minutes (unknown here)
            0.0, // energy_used (unknown here)
            $amount,
            $transaction_id,
            'System'
        ]);
    }
    
    // Also update active_tickets if exists (for admin system). Be defensive: check table and columns.
    $stmt = $conn->prepare("SHOW TABLES LIKE 'active_tickets'");
    $stmt->execute();
    $hasActiveTable = (bool) $stmt->fetch(PDO::FETCH_ASSOC);
    if ($hasActiveTable) {
        // Check if 'status' and 'payment_status' columns exist in active_tickets
        $stmt = $conn->prepare("SHOW COLUMNS FROM active_tickets LIKE 'status'");
        $stmt->execute();
        $hasActiveStatus = (bool) $stmt->fetch(PDO::FETCH_ASSOC);

        $stmt = $conn->prepare("SHOW COLUMNS FROM active_tickets LIKE 'payment_status'");
        $stmt->execute();
        $hasActivePaymentStatus = (bool) $stmt->fetch(PDO::FETCH_ASSOC);

        // Build update parts based on available columns
        $updateParts = [];
        $params = [];
        if ($hasActiveStatus) {
            $updateParts[] = "status = 'completed'";
        }
        if ($hasActivePaymentStatus) {
            $updateParts[] = "payment_status = 'paid'";
        }

        if (count($updateParts) > 0) {
            $updateSql = "UPDATE active_tickets SET " . implode(', ', $updateParts) . " WHERE ticket_id = ? AND username = ?";
            $stmt = $conn->prepare($updateSql);
            $stmt->execute([$ticket_id, $username]);
        } else {
            // No relevant columns exist in active_tickets, skip update
            error_log("Payment: active_tickets has no status/payment_status columns to update");
        }
    }
    
    // CRITICAL FIX: Remove ticket from queue_tickets after payment (like other payment flows)
    $stmt = $conn->prepare("DELETE FROM queue_tickets WHERE ticket_id = ? AND username = ?");
    $delete_result = $stmt->execute([$ticket_id, $username]);
    
    if (!$delete_result) {
        error_log("Payment: Failed to remove ticket from queue_tickets for ticket " . $ticket_id);
        // Don't fail the transaction, but log the issue
    } else {
        error_log("Payment: Successfully removed ticket " . $ticket_id . " from queue_tickets after payment");
    }
    
    // Also clear active_tickets if it exists (like other payment flows)
    if ($hasActiveTable) {
        $stmt = $conn->prepare("DELETE FROM active_tickets WHERE ticket_id = ? AND username = ?");
        $stmt->execute([$ticket_id, $username]);
        error_log("Payment: Cleared active_tickets for ticket " . $ticket_id);
    }
    
    $conn->commit();
    
    echo json_encode([
        'success' => true,
        'transaction_id' => $transaction_id,
        'remaining_balance' => $remaining_balance,
        'payment_method' => $payment_method,
        'message' => 'Payment processed successfully'
    ]);
    
} catch (Exception $e) {
    $conn->rollback();
    error_log("Payment processing error: " . $e->getMessage());
    echo json_encode(['success' => false, 'error' => 'Payment processing failed: ' . $e->getMessage()]);
}
?>
