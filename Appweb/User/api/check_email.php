<?php
// Check Email API - Returns whether email exists in database
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
        "error" => "Database connection failed"
    ]);
    exit();
}

$method = $_SERVER["REQUEST_METHOD"];

try {
    if ($method !== "POST") {
        echo json_encode([
            "success" => false,
            "error" => "Method not allowed"
        ]);
        exit();
    }

    $email = $_POST["email"] ?? "";

    if (!$email) {
        echo json_encode([
            "success" => false,
            "error" => "Email is required"
        ]);
        exit();
    }

    // Validate email format
    if (!filter_var($email, FILTER_VALIDATE_EMAIL)) {
        echo json_encode([
            "success" => false,
            "error" => "Invalid email format"
        ]);
        exit();
    }

    // Check if user exists in database
    $stmt = $db->prepare("SELECT email FROM users WHERE email = ?");
    $stmt->execute([$email]);
    $user = $stmt->fetch(PDO::FETCH_ASSOC);

    if ($user) {
        // Email exists in database
        echo json_encode([
            "success" => true,
            "exists" => true,
            "message" => "Email found in database"
        ]);
    } else {
        // Email does not exist in database
        echo json_encode([
            "success" => true,
            "exists" => false,
            "message" => "Email not found in database"
        ]);
    }

} catch (Exception $e) {
    echo json_encode([
        "success" => false,
        "error" => "Server error: " . $e->getMessage()
    ]);
}
?>
