<?php
session_start();
require_once 'config/database.php';

if (!isset($_SESSION['username'])) {
	header('Location: index.php');
	exit();
}

$username = $_SESSION['username'];

$db = new Database();
$conn = $db->getConnection();

if ($conn) {
	// If battery level is 100%, randomize to 10–50%; otherwise retain existing level
	try {
		$stmt = $conn->prepare("SELECT battery_level FROM battery_levels WHERE username = :username LIMIT 1");
		$stmt->bindParam(':username', $username);
		$stmt->execute();
		$row = $stmt->fetch(PDO::FETCH_ASSOC);
		if ($row) {
			$level = (int)($row['battery_level'] ?? 0);
			error_log("Logout: User $username has battery level $level");
			
			if ($level >= 100) {
				$newLevel = 10 + rand(0, 40); // 10–50
				error_log("Logout: Resetting battery from $level to $newLevel for user $username");
				
				$reset = $conn->prepare("UPDATE battery_levels SET battery_level = :lvl, initial_battery_level = :lvl, last_updated = NOW() WHERE username = :username");
				$reset->bindParam(':lvl', $newLevel);
				$reset->bindParam(':username', $username);
				$result = $reset->execute();
				
				if ($result) {
					error_log("Logout: Successfully reset battery to $newLevel for user $username");
				} else {
					error_log("Logout: Failed to reset battery for user $username");
				}
			} else {
				error_log("Logout: Battery level $level is less than 100%, no reset needed for user $username");
			}
		} else {
			error_log("Logout: No battery data found for user $username");
		}
	} catch (Exception $e) {
		error_log("Logout: Database error for user $username: " . $e->getMessage());
	}
}

// Clear session and redirect to login
$_SESSION = [];
session_destroy();

// Add a small delay to ensure database operations complete
sleep(1);

header('Location: index.php');
exit();
?>
