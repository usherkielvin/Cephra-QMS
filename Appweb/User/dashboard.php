<?php
session_start();
if (!isset($_SESSION['username'])) {
    header("Location: index.php");
    exit();
}
require_once 'config/database.php';
$db = new Database();
$conn = $db->getConnection();

// Function to calculate charging amount based on service type
function calculateChargingAmount($serviceType) {
    // Define pricing based on service type
    $pricing = [
        'Fast Charging' => 75.00,
        'Normal Charging' => 45.00,
        'Fast' => 75.00,
        'Normal' => 45.00
    ];
    
    foreach ($pricing as $type => $amount) {
        if (stripos($serviceType, $type) !== false) {
            return $amount;
        }
    }
    
    return 75.00; // Default amount
}

if ($conn) {
    $username = $_SESSION['username'];
    $stmt = $conn->prepare("SELECT firstname, car_index, plate_number, profile_picture FROM users WHERE username = :username");
    $stmt->bindParam(':username', $username);
    $stmt->execute();
    $user = $stmt->fetch(PDO::FETCH_ASSOC);
$firstname = $user ? $user['firstname'] : 'User';
$car_index = $user ? $user['car_index'] : null;
$plate_number = $user ? $user['plate_number'] : null;
$user_profile_picture = $user ? $user['profile_picture'] : null;

$stmt_charging = $conn->prepare("SELECT * FROM queue_tickets WHERE username = :username AND status NOT IN ('complete') ORDER BY created_at DESC LIMIT 1");
$stmt_charging->bindParam(':username', $username);
$stmt_charging->execute();
$latest_charging = $stmt_charging->fetch(PDO::FETCH_ASSOC);

// Fetch active ticket
$stmt_active = $conn->prepare("SELECT ticket_id, status, bay_number FROM active_tickets WHERE username = :username ORDER BY created_at DESC LIMIT 1");
$stmt_active->bindParam(':username', $username);
$stmt_active->execute();
$active_ticket = $stmt_active->fetch(PDO::FETCH_ASSOC);

// Set queue_ticket to latest_charging
$queue_ticket = $latest_charging;

// Determine current status based on ticket states
$current_ticket = null;
$status_text = 'Connected';
$background_class = 'connected-bg';
$button_text = 'Charge Now';
$button_href = 'ChargingPage.php';

// Check active_tickets for charging states
if ($active_ticket) {
    $current_ticket = $active_ticket;
    if (strtolower($active_ticket['status']) === 'charging') {
        $background_class = 'charging-bg';
        $bay_number = $active_ticket['bay_number'] ?? 'TBD';
        $status_text = 'Charging at Bay ' . $bay_number;
        $button_text = 'Check Monitor';
        $button_href = '../Monitor/index.php';
    }
}
// Check queue_tickets for pending/waiting states
elseif ($queue_ticket) {
    $current_ticket = $queue_ticket;
    // Check if payment is completed - if so, show as connected
    if (strtolower($queue_ticket['payment_status']) === 'paid' ||
        strtolower($queue_ticket['payment_status']) === 'completed' ||
        strtolower($queue_ticket['payment_status']) === 'success') {
        $status_text = 'Connected';
        $background_class = 'connected-bg';
        $button_text = 'Charge Now';
        $button_href = 'ChargingPage.php';
    } elseif (strtolower($queue_ticket['status']) === 'complete') {
        // Check if payment is already completed (ticket should be removed if paid)
        if (strtolower($queue_ticket['payment_status']) === 'paid' ||
            strtolower($queue_ticket['payment_status']) === 'completed' ||
            strtolower($queue_ticket['payment_status']) === 'success') {
            // Payment already completed - ticket should have been removed
            $status_text = 'Connected';
            $background_class = 'connected-bg';
            $button_text = 'Charge Now';
            $button_href = 'ChargingPage.php';
        } else {
            // Still pending payment
            $background_class = 'queue-pending-bg';
            $status_text = 'Pending Payment';
            $button_text = 'Pay Now';
            $button_href = 'javascript:void(0)'; // Change to trigger payment popup
        }
    } elseif (strtolower($queue_ticket['status']) === 'waiting') {
        $background_class = 'waiting-bg';
        $status_text = 'Waiting';
        $button_text = 'Check Monitor';
        $button_href = '../Monitor/index.php';
    } elseif (strtolower($queue_ticket['status']) === 'pending') {
        $background_class = 'connected-bg';
        $status_text = 'Connected';
        $button_text = 'Charge Now';
        $button_href = 'ChargingPage.php';
    }
}

// Determine if vehicle action button (quick-action-btn) should be disabled
$vehicle_button_disabled = false;
$vehicle_disabled_status = '';
if ($current_ticket && in_array(strtolower($current_ticket['status']), ['charging', 'completed', 'pending'])) {
    $vehicle_button_disabled = true;
    $vehicle_disabled_status = ucfirst(strtolower($current_ticket['status']));
}

// Determine if Start Charging button should be disabled
$start_charging_disabled = false;
$start_charging_disabled_status = '';
// Disable when status is one of: waiting, charging, completed, pending (case-insensitive)
if ($current_ticket) {
	$st = strtolower($current_ticket['status']);
	// normalize some common variants
	if ($st === 'complete') $st = 'completed';
	if (in_array($st, ['waiting', 'charging', 'completed', 'pending'])) {
		$start_charging_disabled = true;
		$start_charging_disabled_status = ucfirst($st);
	}
}

// Fetch battery level from database
$db_battery_level = null;
$battery_history = [];
if ($conn && $username) {
    $stmt_battery = $conn->prepare("SELECT battery_level FROM battery_levels WHERE username = :username ORDER BY last_updated DESC LIMIT 1");
    $stmt_battery->bindParam(':username', $username);
    $stmt_battery->execute();
    $battery_row = $stmt_battery->fetch(PDO::FETCH_ASSOC);
    $db_battery_level = $battery_row ? $battery_row['battery_level'] . '%' : null;

    // Fetch all battery history
    $stmt_history = $conn->prepare("SELECT battery_level, initial_battery_level, battery_capacity_kwh, last_updated FROM battery_levels WHERE username = :username ORDER BY last_updated DESC");
    $stmt_history->bindParam(':username', $username);
    $stmt_history->execute();
    $battery_history = $stmt_history->fetchAll(PDO::FETCH_ASSOC);
}

// Vehicle data based on car_index
$vehicle_data = null;
if ($car_index !== null && $car_index >= 0 && $car_index <= 8) {
    // Real EV models
    $models = [
        0 => 'Audi q8 etron',
        1 => 'Nissan leaf',
        2 => 'Tesla Model X',
        3 => 'Lotus Spectre',
        4 => 'BYD Seagull',
        5 => 'Hyundai Ionic 5',
        6 => 'Porsche Taycan',
        7 => 'BYD Tang',
        8 => 'omada e5'
    ];

    // Car images
    $car_images = [
        0 => 'images/cars/audiq8etron.png',
        1 => 'images/cars/nissanleaf_.png',
        2 => 'images/cars/teslamodelx.png',
        3 => 'images/cars/lotuseltre.png',
        4 => 'images/cars/bydseagull.png',
        5 => 'images/cars/hyundai.png',
        6 => 'images/cars/porschetaycan.png',
        7 => 'images/cars/bydtang.png',
        8 => 'images/cars/omodae5.png'
    ];

    // Realistic vehicle specs based on model
    $vehicle_specs = [
        0 => ['range' => '450 km', 'time_to_full' => '6h 0m', 'battery_level' => '80%'], // Audi q8 etron
        1 => ['range' => '220 km', 'time_to_full' => '8h 0m', 'battery_level' => '72%'], // Nissan leaf
        2 => ['range' => '400 km', 'time_to_full' => '7h 0m', 'battery_level' => '85%'], // Tesla x
        3 => ['range' => '500 km', 'time_to_full' => '5h 0m', 'battery_level' => '90%'], // Lotus Spectre
        4 => ['range' => '300 km', 'time_to_full' => '5h 0m', 'battery_level' => '75%'], // BYD Seagull
        5 => ['range' => '484 km', 'time_to_full' => '7h 20m', 'battery_level' => '95%'], // Hyundai
        6 => ['range' => '400 km', 'time_to_full' => '6h 0m', 'battery_level' => '85%'], // Porsche Taycan
        7 => ['range' => '400 km', 'time_to_full' => '7h 0m', 'battery_level' => '80%'], // BYD Tang
        8 => ['range' => '350 km', 'time_to_full' => '6h 0m', 'battery_level' => '78%'] // omada e5
    ];

    $vehicle_data = [
        'model' => $models[$car_index],
        'status' => $status_text,
        'range' => $vehicle_specs[$car_index]['range'],
        'time_to_full' => $vehicle_specs[$car_index]['time_to_full'],
        'battery_level' => $db_battery_level ?? $vehicle_specs[$car_index]['battery_level'],
        'image' => $car_images[$car_index],
        'plate_number' => $plate_number
    ];

    // Calculate range and time_to_full based on battery level
    $battery_level_str = $vehicle_data['battery_level'];
    $battery_level_num = floatval(str_replace('%', '', $battery_level_str));

    // Get max range from specs (assuming it's the max at 100%)
    $max_range_km = intval(str_replace(' km', '', $vehicle_specs[$car_index]['range']));

    // Parse max charge time from specs
    $time_str = $vehicle_specs[$car_index]['time_to_full'];
    preg_match('/(\d+)h\s*(\d+)m/', $time_str, $matches);
    $max_charge_time_hours = 0;
    if ($matches) {
        $hours = intval($matches[1]);
        $mins = intval($matches[2]);
        $max_charge_time_hours = $hours + $mins / 60;
    }

    // Calculate current range
    $current_range_km = round($max_range_km * ($battery_level_num / 100));
    
    // Check if we should show ticket instead of range (for ALL queue states including charging)
    if ($current_ticket && in_array(strtolower($current_ticket['status']), [ 'waiting', 'in progress', 'in_progress', 'charging'])) {
        $vehicle_data['range_label'] = 'Ticket';
        $vehicle_data['range'] = $current_ticket['ticket_id'];
    } else {
        $vehicle_data['range_label'] = 'Range';
        $vehicle_data['range'] = $current_range_km . ' km';
    }

    // Calculate time to full
    $time_to_full_hours = $max_charge_time_hours * ((100 - $battery_level_num) / 100);
    $hours_full = floor($time_to_full_hours);
    $mins_full = round(($time_to_full_hours - $hours_full) * 60);
    $vehicle_data['time_to_full'] = $hours_full . 'h ' . $mins_full . 'm';

    // Calculate additional metrics based on battery_level
    $health_score = round($battery_level_num);
    $degradation = round((100 - $battery_level_num) * 0.08);
    $temperature = 25 + rand(-5, 5);
    $cycles_remaining = round(($battery_level_num / 100) * 1000);
    $highway_range = round($current_range_km * 0.8);
    $city_range = round($current_range_km * 1.2);
    $weather_impact = -round((100 - $battery_level_num) * 0.05);
    $normal_charge = 45.00;
    $fast_charge = 75.00;
    $monthly_savings = round(($battery_level_num / 100) * 1250);
    $green_points = round(($battery_level_num / 100) * 340);
    $system_status = 'All systems normal';
    $last_check = '2 hours ago';
    $alerts = 'None';
    $maintenance_due = round(($battery_level_num / 100) * 1200) . ' km';
}

$latest_transaction = null;
if ($conn) {
    $stmt = $conn->prepare("SELECT pt.ticket_id, pt.amount, pt.payment_method, pt.transaction_status, pt.processed_at, ch.service_type FROM payment_transactions pt LEFT JOIN charging_history ch ON pt.ticket_id = ch.ticket_id WHERE pt.username = :username ORDER BY pt.processed_at DESC LIMIT 1");
    $stmt->bindParam(':username', $username);
    $stmt->execute();
    $latest_transaction = $stmt->fetch(PDO::FETCH_ASSOC);
}

// Fetch all queue tickets for the user
$queue_tickets = [];
if ($conn) {
    $stmt_tickets = $conn->prepare("SELECT ticket_id, service_type, status, payment_status, created_at FROM queue_tickets WHERE username = :username ORDER BY created_at DESC");
    $stmt_tickets->bindParam(':username', $username);
    $stmt_tickets->execute();
    $queue_tickets = $stmt_tickets->fetchAll(PDO::FETCH_ASSOC);
}



} else {
    $firstname = 'User';
    $car_index = null;
}
?>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1, user-scalable=no" />
    <title>Cephra - Dashboard</title>
    <link rel="icon" type="image/png" href="images/logo.png?v=2" />
    <link rel="apple-touch-icon" href="images/logo.png?v=2" />
    <link rel="apple-touch-icon" sizes="192x192" href="images/logo.png?v=2" />
    <link rel="apple-touch-icon" sizes="512x512" href="images/logo.png?v=2" />
    <link rel="manifest" href="manifest.webmanifest" />
    <meta name="theme-color" content="#1a1a2e" />
    
    <!-- iOS Web App Meta Tags -->
    <meta name="apple-mobile-web-app-capable" content="yes" />
    <meta name="apple-mobile-web-app-status-bar-style" content="black-translucent" />
    <meta name="apple-mobile-web-app-title" content="Cephra" />
    <meta name="mobile-web-app-capable" content="yes" />

    <link rel="stylesheet" href="css/vantage-style.css" />
    <link rel="stylesheet" href="css/modal.css" />
    <link rel="stylesheet" href="assets/css/fontawesome-all.min.css" />
		<style>
			/* ============================================
			   VANTAGE MARKETS INSPIRED WHITE THEME
			   ============================================ */

			:root {
				/* Light Theme - Professional White Theme */
				--primary-color: #00c2ce;
				--primary-dark: #0e3a49;
				--secondary-color: #f8fafc;
				--accent-color: #e2e8f0;
				--text-primary: #1a202c;
				--text-secondary: rgba(26, 32, 44, 0.8);
				--text-muted: rgba(26, 32, 44, 0.6);
				--bg-primary: #ffffff;
				--bg-secondary: #f8fafc;
				--bg-card: #ffffff;

			/* Payment Modal Styles */
			.charging-modal-overlay {
				position: fixed;
				top: 0;
				left: 0;
				width: 100%;
				height: 100%;
				background: rgba(0, 0, 0, 0.7);
				display: flex;
				justify-content: center;
				align-items: center;
				z-index: 10000;
			}

			.charging-modal-content {
				background: white;
				border-radius: 15px;
				padding: 2rem;
				max-width: 500px;
				width: 90%;
				max-height: 90vh;
				overflow-y: auto;
				box-shadow: 0 10px 30px rgba(0, 0, 0, 0.3);
			}

			.payment-option {
				cursor: pointer;
				transition: all 0.3s ease;
				border: 2px solid #e0e0e0;
				border-radius: 10px;
				padding: 1rem;
				margin: 0.5rem 0;
				display: flex;
				align-items: center;
			}

			.payment-option:hover {
				border-color: #00c2ce;
				background-color: #f0f9ff;
				transform: translateY(-2px);
				box-shadow: 0 4px 12px rgba(0, 194, 206, 0.2);
			}

			.payment-option.selected {
				border-color: #00c2ce;
				background-color: #e6f7ff;
			}

			.payment-icon {
				margin-right: 1rem;
				font-size: 1.5rem;
				color: #00c2ce;
			}

			.payment-label {
				font-weight: 600;
				font-size: 1.1rem;
			}



			.wallet-balance {
				background-color: #f0f9ff;
				border: 1px solid #00c2ce;
				border-radius: 8px;
				padding: 1rem;
				margin: 1rem 0;
				text-align: center;
			}

			.error-message {
				background-color: #fee;
				border: 1px solid #fcc;
				border-radius: 8px;
				padding: 1rem;
				margin: 1rem 0;
				color: #c33;
				text-align: center;
			}
				--border-color: rgba(26, 32, 44, 0.1);
				--shadow-light: rgba(0, 194, 206, 0.1);
				--shadow-medium: rgba(0, 194, 206, 0.2);
				--shadow-strong: rgba(0, 194, 206, 0.3);
				--gradient-primary: linear-gradient(135deg, #00c2ce 0%, #0e3a49 100%);
				--gradient-secondary: linear-gradient(135deg, #f8fafc 0%, #e2e8f0 100%);
				--gradient-accent: linear-gradient(135deg, #00c2ce 0%, #0e3a49 50%, #1a202c 100%);
			}

			/* ============================================
			   EASY-TO-ADJUST POPUP STYLES
			   ============================================ */

			.popup-overlay {
				position: fixed;
				top: 0;
				left: 0;
				width: 100%;
				height: 100%;
				background: rgba(0, 0, 0, 0.7);
				display: flex;
				align-items: center;
				justify-content: center;
				z-index: 10000;
				padding: 20px;
			}

			.popup-content {
				position: relative;
				width: 90%;
				max-width: 400px;
				max-height: 59vh;
				border-radius: 20px;
				overflow: hidden;
				display: flex;
				align-items: center;
				justify-content: center;
				animation: popupSlideIn 0.3s ease-out;
			}

			.popup-image {
				width: 100%;
				height: auto;
				max-height: 100%;
				max-width: 100%;
				object-fit: contain;
				display: block;
				border-radius: 20px;
			}

.close-btn {
				position: absolute;
				top: 48px;
				right: 32px;
				background: none;
				border: none;
				color: white;
				font-size: 40px;
				cursor: pointer;
				z-index: 10001;
				padding: 0;
				min-width: auto;
				font-weight: normal;
				transition: color 0.3s ease;
			} 

			.close-btn:hover {
				color: #00c2ce;
			}

			@keyframes popupSlideIn {
				from {
					opacity: 0;
					transform: scale(0.8) translateY(-50px);
				}
				to {
					opacity: 1;
					transform: scale(1) translateY(0);
				}
			}

			/* ============================================
			   MODERN DASHBOARD STYLES - WHITE THEME
			   ============================================ */

			/* Header Styles */
			.header {
				position: fixed;
				top: 0;
				left: 0;
				right: 0;
				width: 100vw;
				background: rgba(255, 255, 255, 0.95);
				backdrop-filter: blur(20px);
				border-bottom: 1px solid var(--border-color);
				z-index: 1000;
				transition: all 0.3s ease;
				box-shadow: 0 2px 10px rgba(0, 0, 0, 0.1);
			}

			.header.scrolled {
				background: rgba(255, 255, 255, 0.98);
				box-shadow: 0 2px 20px rgba(0, 0, 0, 0.15);
			}

			.header-content {
				display: flex;
				align-items: center;
				justify-content: space-between;
				padding: 1rem 0;
				width: 100%;
			}

			.logo {
				display: flex;
				align-items: center;
				gap: 12px;
				text-decoration: none;
			}

			.logo-img {
				width: 40px;
				height: 40px;
				border-radius: 10px;
				object-fit: cover;
			}

			.logo-text {
				font-size: 24px;
				font-weight: 800;
				color: var(--text-primary);
				letter-spacing: 1px;
			}

			.nav-list {
				display: flex;
				list-style: none;
				gap: 2rem;
				align-items: center;
			}

			.nav-link {
				color: var(--text-secondary);
				text-decoration: none;
				font-weight: 500;
				transition: all 0.3s ease;
				position: relative;
			}

			.nav-link:hover {
				color: var(--primary-color);
			}

			.header-actions {
				display: flex;
				align-items: center;
				gap: 1.5rem;
			}

			.auth-link {
				color: var(--text-secondary);
				text-decoration: none;
				font-weight: 500;
				transition: all 0.3s ease;
				position: relative;
				padding: 0.5rem 0;
			}

			.auth-link:hover {
				color: var(--primary-color);
			}

			.mobile-menu-toggle {
				display: flex;
				flex-direction: column;
				background: none;
				border: none;
				cursor: pointer;
				padding: 8px;
				gap: 4px;
			}

			.mobile-menu-toggle span {
				width: 25px;
				height: 3px;
				background: var(--text-primary);
				transition: all 0.3s ease;
			}

			.mobile-menu-toggle.active span:nth-child(1) {
				transform: rotate(45deg) translate(6px, 6px);
			}

			.mobile-menu-toggle.active span:nth-child(2) {
				opacity: 0;
			}

			.mobile-menu-toggle.active span:nth-child(3) {
				transform: rotate(-45deg) translate(6px, -6px);
			}

			/* Mobile Menu Styles */
			.mobile-menu {
				position: fixed;
				top: 0;
				right: -100%;
				width: 280px;
				height: 100vh;
				background: rgba(255, 255, 255, 0.98);
				backdrop-filter: blur(20px);
				border-left: 1px solid var(--border-color);
				z-index: 1001;
				transition: right 0.3s ease;
				box-shadow: -5px 0 20px rgba(0, 0, 0, 0.1);
			}

.mobile-menu.mobile-menu-open {
    right: 0;
}

			.mobile-menu-content {
				padding: 80px 2rem 2rem;
				height: 100%;
				display: flex;
				flex-direction: column;
				gap: 2rem;
			}

			.mobile-nav-list {
				list-style: none;
				padding: 0;
				margin: 0;
				display: flex;
				flex-direction: column;
				gap: 1rem;
			}

			.mobile-nav-link {
				color: var(--text-primary);
				text-decoration: none;
				font-weight: 500;
				padding: 1rem;
				border-radius: 8px;
				transition: all 0.3s ease;
				display: block;
			}

			.mobile-nav-link:hover {
				background: var(--bg-secondary);
				color: var(--primary-color);
			}

			.mobile-header-actions {
				margin-top: auto;
				padding-top: 2rem;
				border-top: 1px solid var(--border-color);
			}

			.mobile-auth-link {
				color: var(--text-secondary);
				text-decoration: none;
				font-weight: 500;
				padding: 1rem;
				border-radius: 8px;
				transition: all 0.3s ease;
				display: block;
				text-align: center;
				background: var(--gradient-primary);
				color: white;
			}

			.mobile-auth-link:hover {
				transform: translateY(-2px);
				box-shadow: 0 5px 15px var(--shadow-medium);
			}

			/* Mobile Menu Overlay */
			.mobile-menu-overlay {
				position: fixed;
				top: 0;
				left: 0;
				width: 100%;
				height: 100%;
				background: rgba(0, 0, 0, 0.5);
				z-index: 998;
				opacity: 0;
				visibility: hidden;
				transition: all 0.3s ease;
			}

			.mobile-menu-overlay.active {
				opacity: 1;
				visibility: visible;
			}

			/* Hide Start Charging button when disabled for clearer UX */
			#startChargingBtn.disabled,
			#startChargingBtn[aria-disabled="true"] {
				display: none !important;
				visibility: hidden !important;
				pointer-events: none !important;
			}

			/* Dashboard Hero Section */
			.dashboard-hero {
				background: var(--gradient-secondary);
				padding: 100px 0;
				text-align: center;
				position: relative;
				overflow: hidden;
			}

			.dashboard-hero::before {
				content: '';
				position: absolute;
				top: 0;
				left: 0;
				right: 0;
				bottom: 0;
				background: radial-gradient(circle at 20% 80%, rgba(0, 194, 206, 0.15) 0%, transparent 50%), radial-gradient(circle at 80% 20%, rgba(14, 58, 73, 0.1) 0%, transparent 50%), radial-gradient(circle at 40% 40%, rgba(0, 194, 206, 0.08) 0%, transparent 50%), linear-gradient(135deg, rgba(248, 250, 252, 0.9) 0%, rgba(226, 232, 240, 0.7) 100%);
				z-index: 1;
			}

			.dashboard-hero::after {
				content: '';
				position: absolute;
				top: 0;
				left: 0;
				right: 0;
				bottom: 0;
				background-image: linear-gradient(45deg, transparent 40%, rgba(0, 194, 206, 0.03) 40%, rgba(0, 194, 206, 0.03) 60%, transparent 60%), linear-gradient(-45deg, transparent 40%, rgba(14, 58, 73, 0.02) 40%, rgba(14, 58, 73, 0.02) 60%, transparent 60%);
				background-size: 60px 60px, 40px 40px;
				opacity: 0.6;
				z-index: 1;
			}

			.dashboard-greeting {
				font-size: clamp(3rem, 8vw, 6rem);
				font-weight: 900;
				line-height: 1.1;
				margin-bottom: 1.5rem;
				position: relative;
				z-index: 2;
			}

			.dashboard-greeting-main {
				display: block;
				background: var(--gradient-primary);
				-webkit-background-clip: text;
				-webkit-text-fill-color: transparent;
				background-clip: text;
			}

			.dashboard-greeting-accent {
				display: block;
				color: var(--primary-color);
				font-style: italic;
				margin: 0.5rem 0;
			}

			.dashboard-greeting-sub {
				display: block;
				color: var(--text-secondary);
				font-size: 0.7em;
				font-weight: 400;
			}

			.dashboard-actions {
				display: flex;
				gap: 1.5rem;
				justify-content: center;
				flex-wrap: wrap;
				position: relative;
				z-index: 2;
			}

			.dashboard-btn {
				padding: 12px 24px;
				border: none;
				border-radius: 8px;
				text-decoration: none;
				font-weight: 600;
				transition: all 0.3s ease;
				cursor: pointer;
				display: inline-block;
				text-align: center;
			}

			.btn-rewards {
				background: var(--gradient-primary);
				color: white;
			}

			.btn-rewards:hover {
				transform: translateY(-2px);
				box-shadow: 0 8px 25px var(--shadow-medium);
			}

			.btn-wallet {
				background: transparent;
				color: var(--text-primary);
				border: 2px solid var(--primary-color);
			}

			.btn-wallet:hover {
				background: var(--primary-color);
				color: white;
				transform: translateY(-2px);
			}

			/* Features Section */
			.features {
				padding: 100px 0;
				background: var(--bg-secondary);
			}

			.section-header {
				text-align: center;
				margin-bottom: 60px;
			}

			.section-title {
				font-size: 2.5rem;
				font-weight: 700;
				margin-bottom: 1rem;
				background: var(--gradient-primary);
				-webkit-background-clip: text;
				-webkit-text-fill-color: transparent;
				background-clip: text;
			}

			.section-description {
				font-size: 1.2rem;
				color: var(--text-secondary);
				max-width: 600px;
				margin: 0 auto;
			}

			.features-grid {
				display: grid;
				grid-template-columns: repeat(4, 1fr);
				gap: 2rem;
			}

			.main-vehicle-card {
				grid-column: span 4;
			}

			.feature-card {
				background: var(--bg-card);
				border-radius: 20px;
				padding: 2rem;
				border: 1px solid var(--border-color);
				transition: all 0.3s ease;
				opacity: 0;
				transform: translateY(30px);
			}

			.feature-card.animate-in {
				opacity: 1;
				transform: translateY(0);
			}

			.feature-card:hover {
				transform: translateY(-10px);
				box-shadow: 0 20px 40px var(--shadow-medium);
				border-color: var(--primary-color);
			}

			.feature-icon {
				width: 60px;
				height: 60px;
				background: var(--gradient-primary);
				border-radius: 15px;
				display: flex;
				align-items: center;
				justify-content: center;
				margin-bottom: 1.5rem;
				font-size: 24px;
				color: white;
			}

			.feature-title {
				font-size: 1.5rem;
				font-weight: 600;
				margin-bottom: 1rem;
				color: var(--text-primary);
			}

			.feature-description {
				color: var(--text-secondary);
				margin-bottom: 1.5rem;
				line-height: 1.6;
			}

			.feature-link {
				color: var(--primary-color);
				text-decoration: none;
				font-weight: 600;
				transition: all 0.3s ease;
			}

			.feature-link:hover {
				color: var(--text-primary);
				text-shadow: 0 0 10px var(--primary-color);
			}

			/* Responsive Design */
			@media (max-width: 768px) {
				.header-content {
					flex-wrap: wrap;
				}

				.nav {
					display: none;
				}

				.header-actions {
					display: none;
				}

				.mobile-menu-toggle {
					display: flex;
				}

				.dashboard-greeting {
					font-size: 2rem;
				}

				.dashboard-actions {
					flex-direction: column;
					align-items: center;
				}

				.section-title {
					font-size: 2rem;
				}

				.features-grid {
					grid-template-columns: 1fr;
				}
			}

			@media (max-width: 690px) {
				.dashboard-hero {
					padding: 60px 0;
				}

				.dashboard-greeting {
					font-size: 1.8rem;
				}

				.feature-card {
					padding: 1.5rem;
				}

				.main-vehicle-card {
					grid-column: 1;
				}
			}

			/* ============================================
			   MAIN VEHICLE CARD STYLES
			   ============================================ */

			.main-vehicle-card {
				color: white;
				position: relative;
				overflow: hidden;
				border-radius: 20px;
				padding: 2.5rem;
				box-shadow: 0 20px 40px rgba(0, 194, 206, 0.3);
			}

			/* Dynamic background classes */
			.charging-bg {
				background: linear-gradient(90deg, #2f855a 0%, #38a169 50%, #2f855a 100%);
				animation: shift 2s linear infinite;
			}

			.pending-bg {
				background: linear-gradient(135deg, #ff6b35 0%, #f7931e 100%);
			}

			.waiting-bg {
				background: linear-gradient(135deg, #ff6b35 0%, #f7931e 100%);
			}

			.queue-pending-bg {
				background: linear-gradient(135deg, #ffa726 0%, #fb8c00 100%);
			}

			.connected-bg {
				background: linear-gradient(135deg, #00c2ce 0%, #0e3a49 100%);
			}

@keyframes shift {
	0% { background-position: 0% 0; }
	100% { background-position: 200% 0; }
}

			.main-vehicle-content {
				display: flex;
				justify-content: space-between;
				align-items: center;
				position: relative;
				z-index: 2;
			}

			.vehicle-info {
				display: flex;
				align-items: center;
				gap: 2rem;
			}

			.feature-icon-large {
				width: 80px;
				height: 80px;
				background: rgba(255, 255, 255, 0.2);
				border-radius: 20px;
				display: flex;
				align-items: center;
				justify-content: center;
				font-size: 32px;
				color: white;
				backdrop-filter: blur(10px);
			}

			.vehicle-details {
				flex: 1;
			}

			.vehicle-stats {
				display: grid;
				grid-template-columns: repeat(2, 1fr);
				gap: 1rem;
				margin-top: 1rem;
			}

			.stat-item {
				display: flex;
				flex-direction: column;
				gap: 0.25rem;
			}

			.stat-label {
				font-size: 0.9rem;
				opacity: 0.8;
				font-weight: 500;
			}

			.stat-value {
				font-size: 1.1rem;
				font-weight: 600;
			}

			.vehicle-actions {
				display: flex;
				flex-direction: column;
				gap: 1rem;
				align-items: flex-end;
			}

			.quick-action-btn {
				background: rgba(255, 255, 255, 0.2);
				color: white;
				border: 1px solid rgba(255, 255, 255, 0.3);
				padding: 0.75rem 1.5rem;
				border-radius: 25px;
				cursor: pointer;
				font-weight: 600;
				transition: all 0.3s ease;
				backdrop-filter: blur(10px);
				text-decoration: none;
				font-size: 1rem;
				font-family: inherit;
			}

			.quick-action-btn:hover:not(:disabled) {
				background: rgba(255, 255, 255, 0.3);
				transform: translateY(-2px);
			}

			.quick-action-btn:disabled {
				opacity: 0.5;
				cursor: not-allowed;
				background: rgba(255, 255, 255, 0.1);
			}

			.vehicle-bg-pattern {
				position: absolute;
				top: 0;
				left: 0;
				right: 0;
				bottom: 0;
				background-image: radial-gradient(circle at 20% 20%, rgba(255, 255, 255, 0.1) 0%, transparent 50%), radial-gradient(circle at 80% 80%, rgba(255, 255, 255, 0.05) 0%, transparent 50%);
				z-index: 1;
			}

			/* ============================================
			   ADDITIONAL FEATURE STYLES
			   ============================================ */

			/* Stats Section */
			.stats-section {
				background: linear-gradient(135deg, #f8f9fa 0%, #e9ecef 100%);
			}

			.stats-grid {
				display: grid;
				grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
				gap: 2rem;
			}

			.stat-card {
				background: white;
				padding: 2rem;
				border-radius: 12px;
				box-shadow: 0 5px 15px rgba(0, 0, 0, 0.08);
				display: flex;
				align-items: center;
				gap: 1.5rem;
				transition: all 0.3s ease;
			}

			.stat-card:hover {
				transform: translateY(-3px);
				box-shadow: 0 8px 25px rgba(0, 0, 0, 0.15);
			}

			.stat-icon {
				width: 50px;
				height: 50px;
				background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
				border-radius: 10px;
				display: flex;
				align-items: center;
				justify-content: center;
				font-size: 1.2rem;
				color: white;
			}

			.stat-content {
				flex: 1;
			}

			.stat-number {
				font-size: 2rem;
				font-weight: 700;
				color: #1a1a2e;
				margin: 0;
				line-height: 1;
			}

			.stat-label {
				color: #666;
				margin: 0.5rem 0 0 0;
				font-size: 0.9rem;
				font-weight: 500;
			}

			/* Live Status Section */
			.status-grid {
				display: grid;
				grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
				gap: 2rem;
			}

			.status-card {
				background: white;
				padding: 2rem;
				border-radius: 12px;
				box-shadow: 0 5px 15px rgba(0, 0, 0, 0.08);
				border-left: 4px solid #28a745;
			}

			.status-title {
				font-size: 1.2rem;
				font-weight: 600;
				color: #1a1a2e;
				margin-bottom: 1rem;
			}

			.status-indicator {
				display: flex;
				align-items: center;
				gap: 0.5rem;
				margin-bottom: 0.5rem;
			}

			.status-dot {
				width: 12px;
				height: 12px;
				border-radius: 50%;
				background: #28a745;
			}

			.status-dot.active {
				animation: pulse 2s infinite;
			}

			@keyframes pulse {
				0% { opacity: 1; }
				50% { opacity: 0.5; }
				100% { opacity: 1; }
			}

			.status-text {
				font-weight: 600;
				color: #28a745;
			}

			.queue-info, .active-sessions {
				display: flex;
				align-items: baseline;
				gap: 0.5rem;
				margin-bottom: 0.5rem;
			}

			.queue-number, .session-number {
				font-size: 2rem;
				font-weight: 700;
				color: #667eea;
			}

			.queue-label, .session-label {
				color: #666;
				font-size: 0.9rem;
			}

			.status-description {
				color: #666;
				line-height: 1.6;
				margin: 0;
			}

			/* Recent Activity Section */
			.activity-list {
				margin-bottom: 2rem;
			}

			.activity-item {
				display: flex;
				align-items: flex-start;
				gap: 1rem;
				padding: 1.5rem;
				background: white;
				border-radius: 8px;
				box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
				margin-bottom: 1rem;
			}

			.activity-icon {
				width: 40px;
				height: 40px;
				background: #e9ecef;
				border-radius: 8px;
				display: flex;
				align-items: center;
				justify-content: center;
				color: #667eea;
				font-size: 1rem;
			}

			.activity-content {
				flex: 1;
			}

			.activity-title {
				font-size: 1rem;
				font-weight: 600;
				color: #1a1a2e;
				margin: 0 0 0.25rem 0;
			}

			.activity-description {
				color: #666;
				font-size: 0.9rem;
				margin: 0 0 0.5rem 0;
			}

			.activity-time {
				color: #999;
				font-size: 0.8rem;
			}

			.activity-actions {
				text-align: center;
			}

			.activity-link {
				color: #667eea;
				text-decoration: none;
				font-weight: 600;
				transition: color 0.3s ease;
			}

			.activity-link:hover {
				color: #764ba2;
			}

			/* Modal Styles */
			.modal-overlay {
				position: fixed;
				top: 0;
				left: 0;
				width: 100%;
				height: 100%;
				background: rgba(0, 0, 0, 0.7);
				display: flex;
				align-items: center;
				justify-content: center;
				z-index: 10000;
				padding: 20px;
			}

			.modal-content {
				background: white;
				border-radius: 12px;
				box-shadow: 0 20px 40px rgba(0, 0, 0, 0.3);
				max-width: 500px;
				width: 100%;
				max-height: 90vh;
				overflow-y: auto;
				animation: modalSlideIn 0.3s ease-out;
			}

			.modal-content.large {
				max-width: 700px;
			}

			@keyframes modalSlideIn {
				from {
					opacity: 0;
					transform: scale(0.9) translateY(-20px);
				}
				to {
					opacity: 1;
					transform: scale(1) translateY(0);
				}
			}

			.modal-header {
				padding: 1.5rem;
				border-bottom: 1px solid #e9ecef;
				display: flex;
				justify-content: space-between;
				align-items: center;
			}

			.modal-title {
				font-size: 1.25rem;
				font-weight: 600;
				color: #1a1a2e;
				margin: 0;
			}

			.modal-close {
				background: none;
				border: none;
				font-size: 1.5rem;
				color: #666;
				cursor: pointer;
				padding: 0;
				width: 30px;
				height: 30px;
				display: flex;
				align-items: center;
				justify-content: center;
				border-radius: 50%;
				transition: background-color 0.3s ease;
			}

			.modal-close:hover {
				background: #f8f9fa;
				color: #333;
			}

			.modal-body {
				padding: 1.5rem;
			}

			.modal-footer {
				padding: 1rem 1.5rem;
				border-top: 1px solid #e9ecef;
				display: flex;
				justify-content: flex-end;
				gap: 1rem;
			}

			.modal-btn {
				padding: 0.75rem 1.5rem;
				border: none;
				border-radius: 6px;
				font-weight: 600;
				cursor: pointer;
				transition: all 0.3s ease;
			}

			.modal-btn.primary {
				background: #667eea;
				color: white;
			}

			.modal-btn.primary:hover {
				background: #764ba2;
			}

			.modal-btn.secondary {
				background: #f8f9fa;
				color: #666;
				border: 1px solid #dee2e6;
			}

			.modal-btn.secondary:hover {
				background: #e9ecef;
			}

			/* Form Styles */
			.form-group {
				margin-bottom: 1.5rem;
			}

			.form-group label {
				display: block;
				margin-bottom: 0.5rem;
				font-weight: 600;
				color: #1a1a2e;
			}

			.form-group input,
			.form-group select {
				width: 100%;
				padding: 0.75rem;
				border: 1px solid #dee2e6;
				border-radius: 6px;
				font-size: 1rem;
				transition: border-color 0.3s ease;
			}

			.form-group input:focus,
			.form-group select:focus {
				outline: none;
				border-color: #667eea;
				box-shadow: 0 0 0 3px rgba(102, 126, 234, 0.1);
			}

			/* Support Options */
			.support-options {
				display: grid;
				grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
				gap: 1rem;
			}

			.support-option {
				padding: 1.5rem;
				background: #f8f9fa;
				border-radius: 8px;
				text-align: center;
				cursor: pointer;
				transition: all 0.3s ease;
			}

			.support-option:hover {
				background: #e9ecef;
				transform: translateY(-2px);
			}

			.support-icon {
				width: 50px;
				height: 50px;
				background: #667eea;
				border-radius: 50%;
				display: flex;
				align-items: center;
				justify-content: center;
				margin: 0 auto 1rem;
				color: white;
				font-size: 1.2rem;
			}

			.support-option h4 {
				font-size: 1.1rem;
				font-weight: 600;
				color: #1a1a2e;
				margin: 0 0 0.5rem 0;
			}

			.support-option p {
				color: #666;
				font-size: 0.9rem;
				margin: 0;
			}

			/* Stations List */
			.stations-list {
				max-height: 400px;
				overflow-y: auto;
			}

			.station-item {
				display: flex;
				justify-content: space-between;
				align-items: center;
				padding: 1rem;
				border: 1px solid #e9ecef;
				border-radius: 8px;
				margin-bottom: 1rem;
			}

			.station-info h4 {
				font-size: 1.1rem;
				font-weight: 600;
				color: #1a1a2e;
				margin: 0 0 0.5rem 0;
			}

			.station-info p {
				color: #666;
				margin: 0 0 0.5rem 0;
				font-size: 0.9rem;
			}

			.station-features {
				display: flex;
				gap: 0.5rem;
				flex-wrap: wrap;
			}

			.feature-tag {
				background: #e9ecef;
				color: #495057;
				padding: 0.25rem 0.5rem;
				border-radius: 12px;
				font-size: 0.75rem;
				font-weight: 500;
			}

			.station-status {
				display: flex;
				flex-direction: column;
				align-items: flex-end;
				gap: 0.5rem;
			}

			.status-available {
				background: #28a745;
				color: white;
				padding: 0.25rem 0.75rem;
				border-radius: 12px;
				font-size: 0.8rem;
				font-weight: 600;
			}

			.station-btn {
				background: #667eea;
				color: white;
				border: none;
				padding: 0.5rem 1rem;
				border-radius: 6px;
				font-weight: 600;
				cursor: pointer;
				transition: background-color 0.3s ease;
			}

			.station-btn:hover {
				background: #764ba2;
			}

			/* Enhanced Responsive Design */
			@media (max-width: 1200px) {
				.features-grid {
					grid-template-columns: repeat(3, 1fr);
					gap: 1.5rem;
				}
			}

			@media (max-width: 992px) {
				.stats-grid {
					grid-template-columns: repeat(2, 1fr);
				}

				.features-grid {
					grid-template-columns: repeat(2, 1fr);
					gap: 1.5rem;
				}

				.status-grid {
					grid-template-columns: 1fr;
				}

				/* Improve feature card spacing on tablet */
				.feature-card {
					padding: 1.5rem;
				}

				.feature-icon {
					width: 50px;
					height: 50px;
					font-size: 20px;
				}

				.feature-title {
					font-size: 1.3rem;
				}
			}

			@media (max-width: 780px) {
				.stats-grid {
					grid-template-columns: 1fr;
					gap: 1rem;
				}

				.features-grid {
					grid-template-columns: 1fr;
					gap: 1.5rem;
					padding: 0 1rem;
				}

				.status-grid {
					grid-template-columns: 1fr;
					gap: 1rem;
				}

				.stat-card {
					flex-direction: column;
					text-align: center;
					gap: 1rem;
					padding: 1.5rem;
				}

				.support-options {
					grid-template-columns: 1fr;
					gap: 1rem;
				}

				.station-item {
					flex-direction: column;
					align-items: flex-start;
					gap: 1rem;
				}

				.station-status {
					flex-direction: row;
					width: 100%;
					justify-content: space-between;
				}

				/* Optimize feature cards for mobile */
				.feature-card {
					padding: 1.5rem;
					margin-bottom: 1rem;
					border-radius: 15px;
					box-shadow: 0 5px 15px rgba(0, 194, 206, 0.1);
				}

				.feature-icon {
					width: 50px;
					height: 50px;
					font-size: 20px;
					margin-bottom: 1rem;
				}

				.feature-title {
					font-size: 1.25rem;
					margin-bottom: 0.75rem;
					line-height: 1.3;
				}

				.feature-description {
					font-size: 0.95rem;
					line-height: 1.5;
				}

				/* Improve main vehicle card on mobile */
				.main-vehicle-card {
					padding: 1.5rem;
					margin: 0 1rem;
				}

				.main-vehicle-content {
					flex-direction: column;
					gap: 1.5rem;
					text-align: center;
				}

				.vehicle-info {
					flex-direction: column;
					gap: 1rem;
				}

				.feature-icon-large {
					width: 60px;
					height: 60px;
					font-size: 24px;
				}

				.vehicle-stats {
					grid-template-columns: 1fr;
					gap: 0.75rem;
				}

				.vehicle-actions {
					align-items: center;
				}

				/* Additional mobile grid improvements */
				.section-header {
					margin-bottom: 2rem;
				}

				.section-title {
					font-size: 2rem;
				}

				.section-description {
					font-size: 1.1rem;
				}
			}

			@media (max-width: 480px) {
				.modal-content {
					margin: 1rem;
					max-width: calc(100% - 2rem);
				}

				.modal-header,
				.modal-body,
				.modal-footer {
					padding: 1rem;
				}

				.stat-number {
					font-size: 1.5rem;
				}

				.queue-number,
				.session-number {
					font-size: 1.5rem;
				}

				/* Enhanced features grid for 480px and below */
				.features-grid {
					grid-template-columns: 1fr;
					gap: 1rem;
					padding: 0 0.5rem;
				}

				.feature-card {
					padding: 1rem;
					margin-bottom: 0.75rem;
					border-radius: 12px;
					box-shadow: 0 3px 10px rgba(0, 194, 206, 0.08);
				}

				.feature-icon {
					width: 40px;
					height: 40px;
					font-size: 16px;
					margin-bottom: 0.75rem;
				}

				.feature-title {
					font-size: 1.1rem;
					margin-bottom: 0.5rem;
					line-height: 1.2;
				}

				.feature-description {
					font-size: 0.9rem;
					line-height: 1.4;
				}

				/* Improve main vehicle card for small screens */
				.main-vehicle-card {
					padding: 1rem;
					margin: 0 0.5rem;
					grid-column: 1; /* Reset span to fit single column */
				}

				.main-vehicle-content {
					flex-direction: column;
					gap: 1rem;
					text-align: center;
				}

				.vehicle-info {
					flex-direction: column;
					gap: 0.75rem;
				}

				.feature-icon-large {
					width: 50px;
					height: 50px;
					font-size: 20px;
				}

				.vehicle-stats {
					grid-template-columns: 1fr;
					gap: 0.5rem;
				}

				.vehicle-actions {
					align-items: center;
				}

				/* Additional improvements for small screens */
				.section-header {
					margin-bottom: 1.5rem;
				}

				.section-title {
					font-size: 1.75rem;
				}

				.section-description {
					font-size: 1rem;
				}

    /* Optimize dashboard hero for small screens */
    .dashboard-hero {
        padding: 40px 0;
    }
    .dashboard-greeting {
        font-size: 1.5rem;
    }
    .dashboard-actions {
        flex-direction: column;
        gap: 1rem;
    }
}

@media (max-width: 400px) {
    .wallet-link {
        display: none !important;
    }
}
		</style>
	</head>
	<body>
			<?php include __DIR__ . '/partials/header.php'; ?>



		<!-- Dashboard Hero Section -->
		<section class="dashboard-hero">
   		<div class="container">
            <div class="hero-content">
                <h1 class="hero-title">
                    <span class="hero-title-main">Cephra</span>
                    <span class="hero-title-accent"></span>
                    <span class="hero-title-sub">Ultimate Charging Platform</span>
                </h1>
                <p class="hero-description">
                    An award-winning EV charging platform trusted by 50,000+ drivers.
                    Experience the future of electric vehicle charging with intelligent,
                    fast, and reliable charging solutions.
                </p>
                <div class="hero-actions">
                    <?php if ($start_charging_disabled): ?>
                    <span class="btn btn-outline disabled" style="pointer-events: none; opacity: 0.5; cursor: not-allowed;" id="startChargingBtn">Start Charging (<?php echo htmlspecialchars($start_charging_disabled_status); ?>)</span>
                    <?php else: ?>
                    <a href="ChargingPage.php" class="btn btn-outline" id="startChargingBtn">Start Charging</a>
                    <?php endif; ?>
                </div>
            </div>
		</section>

		<!-- Live Status Section -->
		<section class="live-status" style="padding: 60px 0; background: white;">
			<div class="container">
				<div class="section-header">
					<h2 class="section-title">Live Status</h2>
					<p class="section-description">Real-time charging station information</p>
				</div>

				<div class="status-grid">
					<div class="status-card">
						<h4 class="status-title">System Status</h4>
						<div class="status-indicator">
							<span class="status-dot active"></span>
							<span class="status-text">All system operational</span>
						</div>
						<p class="status-description">All charging stations are currently online and available.</p>
					</div>

					<div class="status-card">
						<h4 class="status-title">Current Queue</h4>
						<div class="queue-info">
							<span class="queue-number" id="currentQueue">0</span>
							<span class="queue-label">vehicles waiting</span>
						</div>
						<p class="status-description">Estimated wait time: <strong id="waitTime">0 minutes</strong></p>
					</div>

					<div class="status-card">
						<h4 class="status-title">Active Sessions</h4>
						<div class="active-sessions">
							<span class="session-number" id="activeSessions">0</span>
							<span class="session-label">charging now</span>
						</div>
						<p class="status-description">Average session duration: <strong id="avgDuration">0 min</strong></p>
					</div>
				</div>
			</div>
		</section>

		<!-- Features Section -->
		<section class="features" style="padding: 80px 0;">
			<div class="container">
				<div class="section-header">
					<h2 class="section-title">Vehicle Status</h2>
					<p class="section-description">Monitor your electric vehicle's charging status and performance</p>
				</div>

				<div class="features-grid">
					<!-- Car Status Feature -->
					<?php if ($vehicle_data): ?>
					<div class="feature-card main-vehicle-card <?php echo $background_class; ?>" style="color: white; position: relative; overflow: hidden;">
						<div class="main-vehicle-content">
							<div class="vehicle-info">
								<div class="feature-icon-large">
									<i class="fas fa-car"></i>
								</div>
								<div class="vehicle-details">
									<h3 class="feature-title"><?php echo htmlspecialchars($vehicle_data['model']); ?></h3>
									<div class="vehicle-stats">
										<div class="stat-item">
											<span class="stat-label">Status</span>
											<span class="stat-value" data-status-value="true"><?php echo htmlspecialchars($vehicle_data['status']); ?></span>
										</div>
										<div class="stat-item">
											<span class="stat-label"><?php echo htmlspecialchars($vehicle_data['range_label']); ?></span>
											<span class="stat-value"><?php echo htmlspecialchars($vehicle_data['range']); ?></span>
										</div>
										<div class="stat-item">
											<span class="stat-label">Time to Full</span>
											<span class="stat-value"><?php echo htmlspecialchars($vehicle_data['time_to_full']); ?></span>
										</div>
										<div class="stat-item">
											<span class="stat-label">Battery</span>
											<span class="stat-value"><?php echo htmlspecialchars($vehicle_data['battery_level']); ?></span>
										</div>
										<div class="stat-item">
											<span class="stat-label">Plate Number</span>
											<span class="stat-value"><?php echo htmlspecialchars($plate_number ?: 'Not Set'); ?></span>
										</div>
									</div>
								</div>
							</div>
			<div class="vehicle-actions">
				<button class="quick-action-btn" id="vehicleActionBtn" type="button" <?php echo $vehicle_button_disabled ? 'disabled' : ''; ?>><?php echo htmlspecialchars($button_text); ?></button>
			</div>
						</div>
						<div class="vehicle-bg-pattern"></div>
					</div>
					<?php else: ?>
					<div class="feature-card main-vehicle-card" style="background: linear-gradient(135deg, #ff6b6b 0%, #ee5a52 100%); color: white; position: relative; overflow: hidden; text-align: center; padding: 4rem 2rem;">
						<div class="feature-icon-large" style="margin: 0 auto 1rem; background: rgba(255, 255, 255, 0.2);">
							<i class="fas fa-car" style="font-size: 32px;"></i>
						</div>
						<h3 class="feature-title" style="text-align: center; font-size: 1.8rem; margin-bottom: 1rem;">No Vehicle Linked</h3>
<p style="font-size: 1rem; opacity: 0.9; margin-bottom: 2rem;">Link your vehicle in the Profile section to get started with charging.</p>
<a href="link.php" style="background: rgba(255, 255, 255, 0.2); color: white; border: 1px solid rgba(255, 255, 255, 0.3); padding: 0.75rem 1.5rem; border-radius: 25px; cursor: pointer; font-weight: 600; text-decoration: none; transition: all 0.3s ease; backdrop-filter: blur(10px);">Link Vehicle</a>
					</div>
					<?php endif; ?>

					<?php if ($vehicle_data): ?>
					<!-- Battery Health Monitor -->
					<div class="feature-card">
						<div class="feature-icon">
							<i class="fas fa-battery-three-quarters"></i>
						</div>
						<h3 class="feature-title">Battery Health Monitor</h3>
						<p class="feature-description">
							<strong>Current Level:</strong> <?php echo htmlspecialchars($vehicle_data['battery_level']); ?><br>
							<strong>Health Score:</strong> 92% (Excellent)<br>
							<strong>Temperature:</strong> Optimal (25°C)<br>
						</p>
					</div>

					<!-- Range Calculator -->
					<div class="feature-card">
						<div class="feature-icon">
							<i class="fas fa-route"></i>
					</div>
						<h3 class="feature-title">Range Calculator</h3>
						<p class="feature-description">
							<strong>Current Range:</strong> 45 km<br>
							<strong>Highway Range:</strong> 38 km<br>
							<strong>City Range:</strong> 52 km<br>
							<strong>Weather Impact:</strong> -5 km (rain)
						</p>
					</div>

					<!-- Estimated Cost -->
					<div class="feature-card">
						<div class="feature-icon">
							<i class="fas fa-dollar-sign"></i>
						</div>
						<h3 class="feature-title">Estimated Cost</h3>
						<p class="feature-description">
							<strong>Normal Charge:</strong> ₱45.00<br>
							<strong>Fast Charge:</strong> ₱75.00<br>
							<strong>Monthly Savings:</strong> ₱1,250<br>
							<strong>Green Points:</strong> 340 earned
						</p>
					</div>

					<!-- Vehicle Diagnostics -->
					<div class="feature-card">
						<div class="feature-icon">
							<i class="fas fa-stethoscope"></i>
						</div>
						<h3 class="feature-title">Vehicle Diagnostics</h3>
						<p class="feature-description">	
							<strong>System Status:</strong> All systems normal<br>
							<strong>Last Check:</strong> 2 hours ago<br>
							<strong>Alerts:</strong> None<br>
						</p>
					</div>
					<?php endif; ?>
				</div>
			</div>
		</section>

		<!-- Rewards and Wallet Section -->
		<section class="rewards-wallet" style="padding: 80px 0; background: #f8f9fa;">
			<div class="container">
				<div class="section-header">
					<h2 class="section-title">Rewards & Wallet</h2>
					<p class="section-description">Manage your rewards and wallet balance</p>
				</div>

				<div class="features-grid">
					<!-- Rewards Feature -->
					<div class="feature-card" style="grid-column: span 2;">
						<div class="feature-icon">
							<i class="fas fa-gift"></i>
						</div>
						<h3 class="feature-title">Cephra Rewards</h3>
						<p class="feature-description">
							Earn points on every charge and unlock exclusive benefits,
							discounts, and premium features as you charge more.
						</p>
						<a href="rewards.php" class="feature-link">View Rewards →</a>
					</div>

					<!-- Wallet Feature -->
					<div class="feature-card" style="grid-column: span 2;">
						<div class="feature-icon">
							<i class="fas fa-wallet"></i>
						</div>
						<h3 class="feature-title">Digital Wallet</h3>
						<p class="feature-description">
							Manage your payment methods, view transaction history,
							and track your spending across all charging sessions.
						</p>
						<a href="wallet.php" class="feature-link">Manage Wallet →</a>
					</div>
				</div>
			</div>
		</section>

		<!-- Recent Activity Section -->
		<section class="recent-activity" style="padding: 60px 0; background: #f8f9fa;">
			<div class="container">
				<div class="section-header">
					<h2 class="section-title">Recent Activity</h2>
					<p class="section-description">Your latest charging sessions and transactions</p>
				</div>

				<div class="activity-list" id="recentActivity">
					<?php if ($latest_transaction): ?>
					<div class="activity-item">
						<div class="activity-icon">
							<i class="fas fa-credit-card"></i>
						</div>
						<div class="activity-content">
							<h4 class="activity-title">₱<?php echo number_format($latest_transaction['amount'], 2); ?> Payment for <?php echo htmlspecialchars($latest_transaction['service_type'] ?? 'Charging'); ?></h4>
							<p class="activity-description">Paid via <?php echo htmlspecialchars($latest_transaction['payment_method']); ?> - Status: <?php echo htmlspecialchars($latest_transaction['transaction_status']); ?></p>
							<span class="activity-time"><?php echo date('M j, Y g:i A', strtotime($latest_transaction['processed_at'])); ?></span>
						</div>
					</div>
					<?php else: ?>
					<div class="activity-item">
						<div class="activity-icon">
							<i class="fas fa-info-circle"></i>
						</div>
						<div class="activity-content">
							<h4 class="activity-title">Welcome to your dashboard!</h4>
							<p class="activity-description">Your personalized dashboard is ready to use.</p>
							<span class="activity-time">Just now</span>
						</div>
					</div>
					<?php endif; ?>
				</div>

				<div class="activity-actions">
					<a href="history.php" class="activity-link">View All →</a>
				</div>
			</div>
		</section>

		<!-- Image-based Popup Ad -->
		<div id="greenPointsPopup" class="popup-overlay" style="display: none;">
			<div class="popup-content">
				<img src="images/pop-up.png" alt="Cephra Rewards Popup" class="popup-image" />
				<button class="close-btn" onclick="closeGreenPointsPopup()">×</button>
			</div>
		</div>

		<!-- Stations Modal -->
		<div id="stationsModal" class="modal-overlay" style="display: none;">
			<div class="modal-content large">
				<div class="modal-header">
					<h2 class="modal-title">Nearby Charging Stations</h2>
					<button class="modal-close" onclick="closeStationsModal()">&times;</button>
				</div>
				<div class="modal-body">
					<div class="stations-list">
						<!-- Sample stations -->
						<div class="station-item">
							<div class="station-info">
								<h4>Station A - Downtown</h4>
								<p>123 Main St, City Center</p>
								<div class="station-features">
									<span class="feature-tag">Fast Charge</span>
									<span class="feature-tag">24/7</span>
								</div>
							</div>
							<div class="station-status">
								<span class="status-available">Available</span>
								<button class="station-btn" onclick="navigateToStation('Station A - Downtown')">Navigate</button>
							</div>
						</div>
						<div class="station-item">
							<div class="station-info">
								<h4>Station B - Mall Area</h4>
								<p>456 Shopping Blvd, Mall District</p>
								<div class="station-features">
									<span class="feature-tag">Normal Charge</span>
									<span class="feature-tag">Covered</span>
								</div>
							</div>
							<div class="station-status">
								<span class="status-available">Available</span>
								<button class="station-btn" onclick="navigateToStation('Station B - Mall Area')">Navigate</button>
							</div>
						</div>
						<div class="station-item">
							<div class="station-info">
								<h4>Station C - Highway</h4>
								<p>789 Highway Exit, Route 10</p>
								<div class="station-features">
									<span class="feature-tag">Fast Charge</span>
									<span class="feature-tag">Rest Area</span>
								</div>
							</div>
							<div class="station-status">
								<span class="status-available">Available</span>
								<button class="station-btn" onclick="navigateToStation('Station C - Highway')">Navigate</button>
							</div>
						</div>
					</div>
				</div>
			</div>
		</div>

		<!-- Schedule Modal -->
		<div id="scheduleModal" class="modal-overlay" style="display: none;">
			<div class="modal-content">
				<div class="modal-header">
					<h2 class="modal-title">Schedule Charging</h2>
					<button class="modal-close" onclick="closeScheduleModal()">&times;</button>
				</div>
				<div class="modal-body">
					<form id="scheduleForm">
						<div class="form-group">
							<label for="scheduleDate">Date</label>
							<input type="date" id="scheduleDate" name="date" required>
						</div>
						<div class="form-group">
							<label for="scheduleTime">Time</label>
							<input type="time" id="scheduleTime" name="time" required>
						</div>
						<div class="form-group">
							<label for="chargingType">Charging Type</label>
							<select id="chargingType" name="chargingType" required>
								<option value="">Select Type</option>
								<option value="Normal">Normal Charging</option>
								<option value="Fast">Fast Charging</option>
							</select>
						</div>
						<div class="form-group">
							<label for="estimatedDuration">Estimated Duration (hours)</label>
							<input type="number" id="estimatedDuration" name="duration" min="1" max="8" required>
						</div>
					</form>
				</div>
				<div class="modal-footer">
					<button class="modal-btn secondary" onclick="closeScheduleModal()">Cancel</button>
					<button class="modal-btn primary" onclick="submitSchedule()">Schedule</button>
				</div>
			</div>
		</div>

		<!-- Support Modal -->
		<div id="supportModal" class="modal-overlay" style="display: none;">
			<div class="modal-content">
				<div class="modal-header">
					<h2 class="modal-title">Support Center</h2>
					<button class="modal-close" onclick="closeSupportModal()">&times;</button>
				</div>
				<div class="modal-body">
					<div class="support-options">
						<div class="support-option" onclick="showFAQ()">
							<div class="support-icon"><i class="fas fa-question-circle"></i></div>
							<h4>FAQ</h4>
							<p>Frequently asked questions</p>
						</div>
						<div class="support-option" onclick="contactSupport()">
							<div class="support-icon"><i class="fas fa-phone"></i></div>
							<h4>Contact Support</h4>
							<p>Get in touch with our team</p>
						</div>
						<div class="support-option" onclick="reportIssue()">
							<div class="support-icon"><i class="fas fa-exclamation-triangle"></i></div>
							<h4>Report Issue</h4>
							<p>Report technical problems</p>
						</div>
					</div>
				</div>
			</div>
		</div>

		<!-- Charging Status Modal -->
		<div id="chargingModal" class="charging-modal-overlay" style="display: none;">
			<div class="charging-modal-content">
				<div class="charging-modal-icon charging-icon">
					<i class="fas fa-bolt"></i>
				</div>
				<h2 class="charging-modal-title">Your Car is Charging</h2>
				<p class="charging-modal-description">Your vehicle is currently charging. Please wait while we power up your battery.</p>
				<div class="charging-modal-info">
					<div class="charging-info-item">
						<span>Current Battery:</span>
						<span id="chargingCurrentBattery">45%</span>
					</div>
					<div class="charging-info-item">
						<span>Target Battery:</span>
						<span id="chargingTargetBattery">80%</span>
					</div>
					<div class="charging-info-item">
						<span>Charging Speed:</span>
						<span id="chargingSpeed">7.2 kW</span>
					</div>
					<div class="charging-info-item">
						<span>Time Remaining:</span>
						<span id="chargingTimeRemaining">20 minutes</span>
					</div>
				</div>
				<div class="charging-modal-buttons">
					<button class="charging-modal-btn btn-primary" onclick="closeChargingModal()">OK</button>
				</div>
			</div>
		</div>

		<!-- Waiting Queue Modal -->
		<div id="waitingModal" class="charging-modal-overlay" style="display: none;">
			<div class="charging-modal-content">
				<div class="charging-modal-icon waiting-icon">
					<i class="fas fa-clock"></i>
				</div>
				<h2 class="charging-modal-title">You're in the Queue</h2>
				<p class="charging-modal-description">Your car is already in the charging queue. Please wait for your turn.</p>
				<div class="charging-modal-info">
					<div class="charging-info-item">
						<span>Queue Position:</span>
						<span id="queuePosition">2nd</span>
					</div>
					<div class="charging-info-item">
						<span>Estimated Wait:</span>
						<span id="estimatedWait">10 minutes</span>
					</div>
					<div class="charging-info-item">
						<span>Ticket ID:</span>
						<span id="ticketId">#CH001</span>
					</div>
					<div class="charging-info-item">
						<span>Service Type:</span>
						<span id="serviceType">Fast Charging</span>
					</div>
				</div>
				<div class="charging-modal-buttons">
					<button class="charging-modal-btn btn-secondary" onclick="cancelQueue()">Cancel</button>
					<button class="charging-modal-btn btn-primary" onclick="closeWaitingModal()">OK</button>
				</div>
			</div>
		</div>

		<!-- Payment Completion Modal -->
		<div id="paymentModal" class="charging-modal-overlay" style="display: none; align-items: center; justify-content: center;">
			<div class="charging-modal-content modern-modal-card" role="dialog" aria-labelledby="paymentModalTitle" aria-modal="true">
				<div class="charging-modal-header">
					<div class="charging-modal-icon completed-icon modern-icon">
						<i class="fas fa-check-circle"></i>
					</div>
					<div class="charging-modal-headline">
						<h2 id="paymentModalTitle" class="charging-modal-title">Charging Complete</h2>
						<p class="charging-modal-description">Your charging session is complete. Please select your payment method.</p>
					</div>
				</div>

				<div class="charging-modal-info modern-info">
					<div class="charging-info-item">
						<div class="label">Session Duration</div>
						<div class="value" id="sessionDuration">45 minutes</div>
					</div>
					<div class="charging-info-item">
						<div class="label">Energy Delivered</div>
						<div class="value" id="energyDelivered">12.5 kWh</div>
					</div>
					<div class="charging-info-item">
						<div class="label">Final Battery</div>
						<div class="value" id="finalBattery">85%</div>
					</div>
					<div class="charging-info-item">
						<div class="label">Total Amount</div>
						<div class="value total-amount" id="totalAmount">₱75.00</div>
					</div>
				</div>

				<div class="wallet-balance" id="walletBalanceDisplay" style="display: none;">
					Current Wallet Balance: ₱<span id="currentWalletBalance">150.00</span>
				</div>

				<div class="error-message" id="errorMessage" style="display: none; color: #b00020; font-weight: 600; text-align: center; margin-top: 8px;">
					Insufficient wallet balance. Please choose cash payment or add funds to your wallet.
				</div>

				<!-- payment option cards removed; action buttons below now carry the same design -->

				<!-- Action buttons - side by side -->
				<div class="charging-modal-buttons modern-button-row" style="margin-top:18px; display:flex; gap:12px;">
					<button type="button" id="payWithCashBtn" class="charging-modal-btn btn-secondary modern-btn payment-option" data-method="cash" style="flex:1;">Pay with Cash</button>
					<button type="button" id="payWithEwalletBtn" class="charging-modal-btn btn-primary modern-btn payment-option" data-method="ewallet" style="flex:1;">Pay with E-Wallet</button>
				</div>

				<!-- E-Wallet confirmation step (hidden by default) -->
				<div id="ewalletConfirm" style="display:none; margin-top:14px; text-align:center;">
					<p style="margin:0 0 10px 0; font-weight:600;">Your wallet has sufficient balance. Confirm payment?</p>
					<div class="modern-button-row" style="display:flex; gap:12px; justify-content:center; margin-top:8px;">
						<button id="ewalletProceedBtn" class="charging-modal-btn btn-primary modern-btn" type="button" style="flex:1;">Proceed</button>
						<button id="ewalletCancelBtn" class="charging-modal-btn btn-secondary modern-btn" type="button" style="flex:1;">Cancel</button>
					</div>
				</div>

				<div style="margin-top:10px; text-align:center;">
					<button class="charging-modal-btn" onclick="closePaymentModal()" style="background:transparent;border:0;color:#666;">Close</button>
				</div>
			</div>
		</div>

		<!-- Payment Success Modal -->
		<div id="paymentSuccessModal" class="charging-modal-overlay" style="display: none;">
			<div class="charging-modal-content">
				<div class="charging-modal-icon charging-icon">
					<i class="fas fa-check"></i>
				</div>
				<h2 class="charging-modal-title">Payment Successful</h2>
				<p class="charging-modal-description">Thank you for your payment. Here's your receipt.</p>
				
				<div class="receipt-info">
					<div class="receipt-header">PAYMENT RECEIPT</div>
					<div class="receipt-item">
						<span>Transaction ID:</span>
						<span id="transactionId">#TXN12345</span>
					</div>
					<div class="receipt-item">
						<span>Date & Time:</span>
						<span id="receiptDateTime"></span>
					</div>
					<div class="receipt-item">
						<span>Service:</span>
						<span id="receiptService">Fast Charging</span>
					</div>
					<div class="receipt-item">
						<span>Duration:</span>
						<span id="receiptDuration">45 minutes</span>
					</div>
					<div class="receipt-item">
						<span>Energy:</span>
						<span id="receiptEnergy">12.5 kWh</span>
					</div>
					<div class="receipt-item">
						<span>Payment Method:</span>
						<span id="receiptPaymentMethod">E-Wallet</span>
					</div>
					<div class="receipt-item total">
						<span>Total Paid:</span>
						<span id="receiptTotal">₱75.00</span>
					</div>
					<div class="receipt-item" id="remainingBalanceItem" style="display: none;">
						<span>Remaining Balance:</span>
						<span id="receiptRemainingBalance">₱75.00</span>
					</div>
				</div>

				<div class="charging-modal-buttons">
					<button class="charging-modal-btn btn-primary" onclick="closePaymentSuccessModal()">OK</button>
				</div>
			</div>
		</div>

		<!-- Scripts -->
			<script src="assets/js/jquery.min.js"></script>
			<script src="assets/js/jquery.dropotron.min.js"></script>
			<script src="assets/js/browser.min.js"></script>
			<script src="assets/js/breakpoints.min.js"></script>
			<script src="assets/js/util.js"></script>
			<script src="assets/js/main.js"></script>
			<script>
				
				 // Mobile Menu Toggle
        function initMobileMenu() {
            const mobileMenuToggle = document.getElementById('mobileMenuToggle');
            const mobileMenu = document.getElementById('mobileMenu');
            const mobileMenuOverlay = document.createElement('div');
            mobileMenuOverlay.className = 'mobile-menu-overlay';
            mobileMenuOverlay.id = 'mobileMenuOverlay';
            document.body.appendChild(mobileMenuOverlay);

            function toggleMobileMenu() {
                const isActive = mobileMenu.classList.contains('active');
                if (isActive) {
                    closeMobileMenu();
                } else {
                    openMobileMenu();
                }
            }

            function openMobileMenu() {
                mobileMenu.classList.add('active');
                mobileMenuToggle.classList.add('active');
                mobileMenuOverlay.classList.add('active');
                document.body.style.overflow = 'hidden';
                mobileMenuOverlay.addEventListener('click', closeMobileMenu);
                document.addEventListener('keydown', handleEscapeKey);
            }

            function closeMobileMenu() {
                mobileMenu.classList.remove('active');
                mobileMenuToggle.classList.remove('active');
                mobileMenuOverlay.classList.remove('active');
                document.body.style.overflow = '';
                mobileMenuOverlay.removeEventListener('click', closeMobileMenu);
                document.removeEventListener('keydown', handleEscapeKey);
            }

            function handleEscapeKey(e) {
                if (e.key === 'Escape') {
                    closeMobileMenu();
                }
            }

            mobileMenuToggle.addEventListener('click', toggleMobileMenu);
            const mobileNavLinks = document.querySelectorAll('.mobile-nav-link');
            mobileNavLinks.forEach(link => link.addEventListener('click', closeMobileMenu));

            document.addEventListener('click', function(e) {
                if (window.innerWidth <= 768) {
                    if (!mobileMenu.contains(e.target) && !mobileMenuToggle.contains(e.target)) {
                        if (mobileMenu.classList.contains('active')) {
                            closeMobileMenu();
                        }
                    }
                }
            });
        }
                // Pass PHP variables to JavaScript
                window.chargingStatus = '<?php echo $charging_status; ?>';
                window.statusText = '<?php echo $status_text; ?>';
                window.currentStatus = '<?php echo $current_ticket ? strtolower($current_ticket['status']) : ''; ?>';
                window.paymentStatus = '<?php echo $latest_charging ? $latest_charging['payment_status'] : ''; ?>';
                window.ticketId = '<?php echo $latest_charging ? $latest_charging['ticket_id'] : ''; ?>';
                window.serviceType = '<?php echo $latest_charging ? $latest_charging['service_type'] : ''; ?>';
                window.batteryLevel = '<?php echo $db_battery_level ?: '0'; ?>';

                // Function to show error popup
                function showErrorPopup(message) {
                    // Remove existing popup if any
                    const existingPopup = document.querySelector('.popup-overlay.error-popup');
                    if (existingPopup) {
                        existingPopup.remove();
                    }

                    const overlay = document.createElement('div');
                    overlay.className = 'popup-overlay error-popup';
                    overlay.style.background = 'rgba(0, 0, 0, 0.6)';
                    overlay.style.zIndex = '10001';

                    const popup = document.createElement('div');
                    popup.className = 'popup-content';
                    popup.style.background = 'var(--bg-primary)';
                    popup.style.color = 'var(--text-primary)';
                    popup.style.border = '2px solid var(--primary-color)';
                    popup.style.padding = '20px';
                    popup.style.textAlign = 'center';
                    popup.style.fontWeight = '600';
                    popup.style.fontSize = '1.1rem';
                    popup.style.borderRadius = '12px';
                    popup.style.maxWidth = '320px';
                    popup.style.boxShadow = '0 10px 30px rgba(0, 194, 206, 0.3)';
                    popup.textContent = message;

                    const btn = document.createElement('button');
                    btn.textContent = 'OK';
                    btn.style.marginTop = '16px';
                    btn.style.padding = '8px 20px';
                    btn.style.background = 'var(--primary-color)';
                    btn.style.color = 'white';
                    btn.style.border = 'none';
                    btn.style.borderRadius = '8px';
                    btn.style.cursor = 'pointer';
                    btn.style.fontWeight = '700';
                    btn.onclick = () => {
                        if (overlay.parentNode) {
                            overlay.parentNode.removeChild(overlay);
                        }
                    };

                    popup.appendChild(document.createElement('br'));
                    popup.appendChild(btn);
                    overlay.appendChild(popup);
                    document.body.appendChild(overlay);
                }

                // Add event listener to Start Charging button
                document.addEventListener('DOMContentLoaded', function() {
                    const startBtn = document.getElementById('startChargingBtn');
                    if (startBtn) {
							startBtn.addEventListener('click', function(event) {
								// Check if current ticket status is one that should block starting a new charge
								const blockedStatuses = ['charging', 'completed', 'complete', 'pending', 'waiting'];
								const currentStatus = (window.currentStatus || '').toLowerCase();

								if (blockedStatuses.includes(currentStatus)) {
									event.preventDefault();
									showErrorPopup('You already have a pending ticket or session in progress');
									return;
								}
								// else allow navigation normally
							});
                    }
                });

				// Notification dropdown functionality
(function() {
    const notifBtn = document.getElementById('notifBtn');
    const notificationDropdown = document.getElementById('notificationDropdown');

    if (notifBtn && notificationDropdown) {
        notifBtn.addEventListener('click', function(e) {
            e.stopPropagation();
            const isVisible = notificationDropdown.style.display === 'block';
            notificationDropdown.style.display = isVisible ? 'none' : 'block';
        });

        // Close dropdown when clicking outside
        document.addEventListener('click', function(e) {
            if (!notifBtn.contains(e.target) && !notificationDropdown.contains(e.target)) {
                notificationDropdown.style.display = 'none';
            }
        });
    }
})();

            </script>

            <script>
                function showDialog(title, message) {
                    // Remove any existing dialogs first
                    const existingDialogs = document.querySelectorAll('[data-dialog-overlay]');
                    existingDialogs.forEach(dialog => {
                        try {
                            if (dialog.parentNode) {
                                dialog.parentNode.removeChild(dialog);
                            }
                        } catch (e) {
                            // Element already removed
                        }
                    });
                    
                    const overlay = document.createElement('div');
                    overlay.setAttribute('data-dialog-overlay', 'true');
                    overlay.style.cssText = 'position:fixed;inset:0;background:rgba(0,0,0,0.6);display:flex;align-items:center;justify-content:center;z-index:10000;padding:16px;';
                    const dialog = document.createElement('div');
                    dialog.style.cssText = 'width:100%;max-width:360px;background:#fff;border-radius:12px;box-shadow:0 10px 30px rgba(0,0,0,0.25);overflow:hidden;';
                    const header = document.createElement('div');
                    header.style.cssText = 'background:#00c2ce;color:#fff;padding:12px 16px;font-weight:700';
                    header.textContent = title || 'Notice';
                    const body = document.createElement('div');
                    body.style.cssText = 'padding:16px;color:#333;line-height:1.5;';
                    body.textContent = message || '';
                    const footer = document.createElement('div');
                    footer.style.cssText = 'padding:12px 16px;display:flex;justify-content:flex-end;gap:8px;background:#f7f7f7;';
                    const ok = document.createElement('button');
                    ok.textContent = 'OK';
                    ok.style.cssText = 'background:#00c2ce;color:#fff;border:0;padding:8px 14px;border-radius:8px;cursor:pointer;';
                    ok.onclick = () => {
                        try {
                            if (overlay && overlay.parentNode) {
                                overlay.style.display = 'none';
                                document.body.removeChild(overlay);
                            }
                        } catch (e) {
                            // Element already removed
                        }
                    };
                    footer.appendChild(ok);
                    dialog.appendChild(header);
                    dialog.appendChild(body);
                    dialog.appendChild(footer);
                    overlay.appendChild(dialog);
                    document.body.appendChild(overlay);
                }

                // Function to show Green Points popup
                function showGreenPointsPopup() {
                    document.getElementById('greenPointsPopup').style.display = 'flex';
                }

                // Function to close Green Points popup
                function closeGreenPointsPopup() {
                    document.getElementById('greenPointsPopup').style.display = 'none';
                }

                // Show popup after page loads (with delay)
                $(document).ready(function() {
                    // Show Green Points popup after 2 seconds
                    setTimeout(function() {
                        showGreenPointsPopup();
                    }, 2000);

                    // Normal Charge Button Click Handler
                    $('#normalChargeBtn').click(function(e) {
                        e.preventDefault();
                        processChargeRequest('Normal Charging');
                    });

                    // Fast Charge Button Click Handler
                    $('#fastChargeBtn').click(function(e) {
                        e.preventDefault();
                        processChargeRequest('Fast Charging');
                    });

                    function processChargeRequest(serviceType) {
                        // Force exact service type strings expected by backend
                        let serviceTypeMapped = '';
                        if (serviceType === 'Normal Charging' || serviceType === 'normal charging') {
                            serviceTypeMapped = 'Normal Charging';
                        } else if (serviceType === 'Fast Charging' || serviceType === 'fast charging') {
                            serviceTypeMapped = 'Fast Charging';
                        } else {
                            serviceTypeMapped = serviceType; // fallback
                        }

                        // Disable buttons during processing
                        $('#normalChargeBtn, #fastChargeBtn').prop('disabled', true);

                        $.ajax({
                            url: 'charge_action.php',
                            type: 'POST',
                            data: { serviceType: serviceTypeMapped },
                            dataType: 'json',
                            success: function(response) {
                                if (response.success) {
                                    // Show QueueTicketProceed popup
                                    // Small popup removed - now handled by ChargingPage.php modal
                                } else if (response.error) {
                                    showDialog('Charging', response.error);
                                }
                            },
                            error: function(xhr, status, error) {
                                showDialog('Charging', 'An error occurred while processing your request. Please try again.');
                                console.error('AJAX Error:', error);
                            },
                            complete: function() {
                                // Re-enable buttons
                                $('#normalChargeBtn, #fastChargeBtn').prop('disabled', false);
                            }
                        });
                    }

                    // Removed showQueueTicketProceedPopup - now handled by ChargingPage.php modal

                    // Function to close popup (defined globally) with state management
                    window.closePopup = function() {
                        // Prevent multiple rapid clicks
                        if (window.popupClosing) {
                            return;
                        }
                        window.popupClosing = true;
                        
                        $('#queuePopup').remove();
                        
                        // Reset state after a short delay
                        setTimeout(function() {
                            window.popupClosing = false;
                        }, 500);
                    };
                });

                // Function to open Monitor Web in new tab
                window.openMonitorWeb = function() {
                    const monitorUrl = '../Monitor/';
                    window.open(monitorUrl, '_blank', 'noopener,noreferrer');
                };

                // Modal Functions for New Features
                function showNearbyStations() {
                    document.getElementById('stationsModal').style.display = 'flex';
                }

                function closeStationsModal() {
                    document.getElementById('stationsModal').style.display = 'none';
                }

                function showScheduleModal() {
                    document.getElementById('scheduleModal').style.display = 'flex';
                }

                function closeScheduleModal() {
                    document.getElementById('scheduleModal').style.display = 'none';
                }

                function showSupportModal() {
                    document.getElementById('supportModal').style.display = 'flex';
                }

                function closeSupportModal() {
                    document.getElementById('supportModal').style.display = 'none';
                }

                function showEstimatedCost() {
                    showDialog('Estimated Cost', 'View detailed cost breakdown for your charging sessions:\n\n• Normal Charge: ₱45.00 per session\n• Fast Charge: ₱75.00 per session\n• Monthly Savings: ₱1,250 (based on usage)\n• Green Points: 340 earned this month\n• Total Sessions: 12 completed\n• Energy Consumed: 45.2 kWh\n\nTrack your spending and maximize your savings with our cost analysis tools.');
                }

                function showBatteryHealth() {
                    showDialog('Battery Health Monitor', 'Detailed battery health information:\n\n• Health Score: 92% (Excellent)\n• Degradation: 8% over 2 years\n• Temperature: Optimal (25°C)\n• Cycles: 340/1000 remaining\n• Voltage: 3.7V per cell\n• Capacity: 95% of original\n\nRecommendations:\n- Continue current usage patterns\n- Schedule maintenance in 6 months\n- Monitor temperature during fast charging');
                }

                function showRangeCalculator() {
                    showDialog('Range Calculator', 'Calculate your remaining range:\n\nCurrent Conditions:\n• Battery Level: 45%\n• Highway Range: 38 km\n• City Range: 52 km\n• Weather Impact: -5 km (rain)\n• Temperature Impact: -3 km (cold)\n\nEstimated Total Range: 42 km\n\nFactors affecting range:\n- Driving style: 15% impact\n- Speed: 20% impact\n- Weather: 10% impact\n- Temperature: 8% impact\n\nTips to maximize range:\n- Maintain steady speed\n- Use regenerative braking\n- Keep tires properly inflated');
                }

                function showDiagnostics() {
                    showDialog('Vehicle Diagnostics', 'System diagnostic results:\n\n✓ Battery Management System: Normal\n✓ Charging System: Normal\n✓ Motor Controller: Normal\n✓ Thermal Management: Normal\n✓ Communication Module: Normal\n✓ Safety Systems: Normal\n\nLast Diagnostic Run: 2 hours ago\nNext Scheduled: Due in 1,200 km\n\nNo issues detected. All systems operating within normal parameters.\n\nFor detailed reports, visit your service center or use the mobile app.');
                }

                function showChargingOptions() {
                    showDialog('Charging Options', 'Select your charging option:\n\n• Normal Charging: ₱45.00 (Standard rate)\n• Fast Charging: ₱75.00 (Express rate)\n\nClick "Start Charging" to proceed to the charging page.');
                    // Optionally redirect after dialog
                    setTimeout(() => {
                        window.location.href = 'ChargingPage.php';
                    }, 3000);
                }

                function submitSchedule() {
                    const form = document.getElementById('scheduleForm');
                    const formData = new FormData(form);

                    // Basic validation
                    const date = document.getElementById('scheduleDate').value;
                    const time = document.getElementById('scheduleTime').value;
                    const chargingType = document.getElementById('chargingType').value;
                    const duration = document.getElementById('estimatedDuration').value;

                    if (!date || !time || !chargingType || !duration) {
                        showDialog('Schedule Charging', 'Please fill in all required fields.');
                        return;
                    }

                    // Show loading dialog
                    showDialog('Schedule Charging', 'Scheduling your charging session...');

                    // Simulate API call (replace with actual endpoint)
                    setTimeout(() => {
                        closeScheduleModal();
                        showDialog('Schedule Charging', 'Your charging session has been scheduled successfully! You will receive a notification when your slot becomes available.');
                    }, 2000);
                }

                function showFAQ() {
                    showDialog('FAQ', 'Frequently Asked Questions:\n\n1. How do I start charging?\n   - Click on "Start Charging" and select your preferred charging type.\n\n2. How do I view my charging history?\n   - Navigate to the "History" section in the navigation menu.\n\n3. How do I update my profile?\n   - Go to "Profile" in the navigation menu to manage your settings.\n\n4. What are Green Points?\n   - Green Points are rewards earned for using our charging stations.\n\nFor more questions, please contact our support team.');
                }

                function contactSupport() {
                    showDialog('Contact Support', 'You can reach our support team through:\n\n📞 Phone: +63 (2) 123-4567\n📧 Email: support@cephra.com\n💬 Live Chat: Available 24/7\n\nOur support team is available Monday to Sunday, 6:00 AM to 10:00 PM.');
                }

                function reportIssue() {
                    showDialog('Report Issue', 'To report a technical issue:\n\n1. Describe the problem in detail\n2. Include any error messages\n3. Mention your device and browser\n4. Note the time when the issue occurred\n\nPlease contact our technical support team at:\n📞 Phone: +63 (2) 123-4567\n📧 Email: techsupport@cephra.com\n\nWe appreciate your feedback and will resolve the issue as quickly as possible.');
                }

                function navigateToStation(stationName) {
                    // Check if geolocation is available
                    if (navigator.geolocation) {
                        navigator.geolocation.getCurrentPosition(
                            function(position) {
                                const lat = position.coords.latitude;
                                const lng = position.coords.longitude;

                                // Use Google Maps or Waze for navigation
                                const mapsUrl = `https://www.google.com/maps/dir/${lat},${lng}/${encodeURIComponent(stationName)}`;
                                window.open(mapsUrl, '_blank', 'noopener,noreferrer');

                                showDialog('Navigation', `Opening navigation to ${stationName}...`);
                            },
                            function(error) {
                                // Fallback if geolocation fails
                                const mapsUrl = `https://www.google.com/maps/search/${encodeURIComponent(stationName + ' charging station')}`;
                                window.open(mapsUrl, '_blank', 'noopener,noreferrer');
                                showDialog('Navigation', 'Opening map directions. Please enable location services for precise navigation.');
                            }
                        );
                    } else {
                        // Fallback for browsers without geolocation
                        const mapsUrl = `https://www.google.com/maps/search/${encodeURIComponent(stationName + ' charging station')}`;
                        window.open(mapsUrl, '_blank', 'noopener,noreferrer');
                        showDialog('Navigation', 'Opening map directions. Please enable location services for precise navigation.');
                    }
                }

                // Load dashboard statistics
                function loadDashboardStats() {
                    // Simulate loading stats from API
                    setTimeout(() => {
                        const currentQueueEl = document.getElementById('currentQueue');
                        if (currentQueueEl) currentQueueEl.textContent = '3';

                        const waitTimeEl = document.getElementById('waitTime');
                        if (waitTimeEl) waitTimeEl.textContent = '8 minutes';

                        const activeSessionsEl = document.getElementById('activeSessions');
                        if (activeSessionsEl) activeSessionsEl.textContent = '7';

                        const avgDurationEl = document.getElementById('avgDuration');
                        if (avgDurationEl) avgDurationEl.textContent = '45 min';
                    }, 1000);
                }

                // Fetch live status from Admin API (same source as admin panel)
                function fetchAndRenderLiveStatus() {
                    // Try user public API first; fallback to admin API if accessible
                    fetch('api/mobile.php?action=live-status')
                        .then(res => res.json())
                        .then(data => {
                            if (!data || !data.success) throw new Error('fallback');
                            const queueCount = Number(data.queue_count || 0);
                            const activeBays = Number(data.active_bays || 0);

                            const queueEl = document.getElementById('currentQueue');
                            const activeEl = document.getElementById('activeSessions');
                            const waitEl = document.getElementById('waitTime');

                            if (queueEl) queueEl.textContent = queueCount;
                            if (activeEl) activeEl.textContent = activeBays;
                            if (waitEl) waitEl.textContent = `${Math.max(0, queueCount)} minutes`;
                        })
                        .catch(() => {
                            // Fallback to admin endpoint if session exists
                            fetch('../Admin/api/admin.php?action=dashboard')
                                .then(r => r.json())
                                .then(d => {
                                    if (!d || !d.success || !d.stats) return;
                                    const queueCount = Number(d.stats.queue_count || 0);
                                    const activeBays = Number(d.stats.active_bays || 0);
                                    document.getElementById('currentQueue').textContent = queueCount;
                                    document.getElementById('activeSessions').textContent = activeBays;
                                    document.getElementById('waitTime').textContent = `${Math.max(0, queueCount)} minutes`;
                                })
                                .catch(() => {});
                        });
                }

                // Start live updates every 3 seconds
                function updateLiveStatus() {
                    fetchAndRenderLiveStatus();
                    setInterval(fetchAndRenderLiveStatus, 3000);
                }

                // Fix for passive event listeners in jQuery
                if (typeof jQuery !== 'undefined') {
                    jQuery.event.special.touchstart = {
                        setup: function(_, ns, handle) {
                            this.addEventListener('touchstart', handle, { passive: true });
                        }
                    };
                    jQuery.event.special.touchmove = {
                        setup: function(_, ns, handle) {
                            this.addEventListener('touchmove', handle, { passive: true });
                        }
                    };
                }


                



                // Initialize dashboard features
                $(document).ready(function() {
                    loadDashboardStats();
                    updateLiveStatus();
					// Apply saved language translations
					setTimeout(() => { try { window.translateDashboard(); } catch(e){} }, 0);

                    // Intersection Observer for animations
                    const observerOptions = {
                        threshold: 0.1,
                        rootMargin: '0px 0px -50px 0px'
                    };

                    const observer = new IntersectionObserver((entries) => {
                        entries.forEach(entry => {
                            if (entry.isIntersecting) {
                                entry.target.classList.add('animate-in');
                            }
                        });
                    }, observerOptions);

                    // Observe all feature cards and sections
                    document.querySelectorAll('.feature-card, .status-card, .stat-card, .promo-card, .charging-card').forEach(el => {
                        observer.observe(el);
                    });

                    // Real-time status updates
                    function updateVehicleStatus() {
                        fetch('api/status_update.php', {
                            method: 'POST',
                            headers: {
                                'Content-Type': 'application/json',
                            },
                            body: JSON.stringify({action: 'get_status'})
                        })
                        .then(response => {
                            if (!response.ok) {
                                throw new Error('Network response was not ok');
                            }
                            return response.text();
                        })
                        .then(text => {
                            try {
                                const data = JSON.parse(text);
                                return data;
                            } catch (e) {
                                console.error('JSON parse error:', e);
                                console.error('Response text:', text);
                                throw new Error('Invalid JSON response');
                            }
                        })
                        .then(data => {
                            if (data.success) {
                                // Update status text
                                const statusElement = document.querySelector('.stat-value');
                                if (statusElement && data.status_text) {
                                    statusElement.textContent = data.status_text;
                                }

                                // Update button text and functionality
                                const buttonElement = document.getElementById('vehicleActionBtn');
                                if (buttonElement && data.button_text) {
                                    buttonElement.textContent = data.button_text;
                                    buttonElement.disabled = false; // Enable button by default
                                    
                                    // Handle Pay Now button click
                                    if (data.button_text === 'Pay Now') {
                                        console.log('Setting up Pay Now button click handler');
                                        buttonElement.onclick = function(e) {
                                            e.preventDefault();
                                            console.log('Pay Now button clicked');
                                            // Set payment data and show modal
                                            currentTicketId = data.payment_modal ? data.payment_modal.ticket_id : '';
                                            totalAmount = data.payment_modal ? data.payment_modal.amount : 0;
                                            
                                            console.log('Payment data set:', { currentTicketId, totalAmount });
                                            
                                            // Update modal with actual data
                                            const totalAmountEl = document.getElementById('totalAmount');
                                            const receiptServiceEl = document.getElementById('receiptService');
                                            
                                            if (totalAmountEl) {
                                                totalAmountEl.textContent = '₱' + totalAmount.toFixed(2);
                                            }
                                            if (receiptServiceEl) {
                                                receiptServiceEl.textContent = data.payment_modal ? data.payment_modal.service_type : 'Charging';
                                            }
                                            
											// Show the payment completion modal (user-initiated, force it)
											showPaymentCompletionModal(true);
                                        };
                                    } else {
                                        // Handle other button actions (redirect to href)
                                        buttonElement.onclick = function(e) {
                                            e.preventDefault();
                                            if (data.button_href) {
                                                if (data.button_href.includes('Monitor')) {
                                                    window.open(data.button_href, '_blank');
                                                } else {
                                                    window.location.href = data.button_href;
                                                }
                                            }
                                        };
                                    }
                                }

                                // Update background class if status changed
                                const vehicleCard = document.querySelector('.main-vehicle-card');
                                if (vehicleCard && data.background_class) {
                                    // Remove old background classes
                                    vehicleCard.classList.remove('connected-bg', 'waiting-bg', 'charging-bg', 'pending-bg', 'queue-pending-bg');
                                    // Add new background class
                                    vehicleCard.classList.add(data.background_class);
                                }

								// Disable or enable the Start Charging button when status is 'complete'
								try {
									const startBtn = document.getElementById('startChargingBtn');
									const normalizedStatus = (data.status_text || '').toLowerCase();
									if (startBtn) {
										// disable for several known blocking status keywords
										const shouldDisable = ['complete','completed','pending','waiting','charging'].some(k => normalizedStatus.includes(k) || normalizedStatus === k);
										if (shouldDisable) {
											// Mark as disabled for both <a> and <button> variants
											startBtn.classList.add('disabled');
											startBtn.setAttribute('aria-disabled', 'true');
											// Anchor handling: remove href and prevent interaction
											if (startBtn.tagName && startBtn.tagName.toLowerCase() === 'a') {
												// stash href so we can restore it later
												if (!startBtn.dataset._hrefBackup) startBtn.dataset._hrefBackup = startBtn.getAttribute('href') || '';
												startBtn.removeAttribute('href');
												startBtn.style.pointerEvents = 'none';
												startBtn.tabIndex = -1;
											} else {
												// For buttons/spans, set disabled if supported
												try { startBtn.disabled = true; } catch(e){}
											}
										} else {
											// Re-enable
											startBtn.classList.remove('disabled');
											startBtn.removeAttribute('aria-disabled');
											if (startBtn.tagName && startBtn.tagName.toLowerCase() === 'a') {
												if (startBtn.dataset._hrefBackup) {
													startBtn.setAttribute('href', startBtn.dataset._hrefBackup);
													delete startBtn.dataset._hrefBackup;
												}
												startBtn.style.pointerEvents = '';
												startBtn.tabIndex = 0;
											} else {
												try { startBtn.disabled = false; } catch(e){}
											}
										}
									}
								} catch (e) {
									console.warn('startChargingBtn toggle failed', e);
								}

                                // Update battery level if changed
                                const batteryElement = $('.feature-description strong').filter(function() {
                                    return $(this).text().includes('Current Level:');
                                }).get(0);
                                if (batteryElement && data.battery_level) {
                                    const batteryText = batteryElement.parentElement;
                                    if (batteryText) {
                                        batteryText.innerHTML = batteryText.innerHTML.replace(
                                            /<strong>Current Level:<\/strong>.*?<br>/,
                                            `<strong>Current Level:</strong> ${data.battery_level}<br>`
                                        );
                                    }
                                }

                                // Handle notification popup (like Java MY_TURN notification)
                                if (data.notification && data.notification.type === 'charge_now_popup') {
                                    showChargeNowPopup(data.notification);
                                }

                                // New modal logic for charging status
                                if (data.status_text.toLowerCase().includes('charging')) {
                                    // Show modal for charging status with bay number
                                    showChargingBayModal(data.ticket_info ? data.ticket_info.ticket_id : '', data.status_text);
                                }

                                // New modal logic for pending payment
                                if (data.payment_modal && data.payment_modal.show) {
                                    // Set payment data
                                    currentTicketId = data.payment_modal.ticket_id;
                                    totalAmount = data.payment_modal.amount;
                                    
                                    // Update modal with actual data
                                    document.getElementById('totalAmount').textContent = '₱' + totalAmount.toFixed(2);
                                    document.getElementById('receiptService').textContent = data.payment_modal.service_type || 'Charging';
                                    
									// Show the payment completion modal (automated status update - do not force)
												showPaymentCompletionModal();
                                }
                            }
                        })
                        .catch(error => {
                            console.log('Status update error:', error);
                        });
                    }

                    // Show modal for charging status with bay number and OK button
                    function showChargingBayModal(ticketId, statusText) {
                        // Check if modal already shown for this ticket
                        const modalKey = 'bayModalShown_' + ticketId;
                        if (localStorage.getItem(modalKey) === 'true') {
                            return;
                        }

                        // Check if modal already exists
                        if (document.getElementById('chargingBayModal')) return;

                        // Extract bay number from statusText
                        const bayMatch = statusText.match(/Bay\s*#?(\w+)/i);
                        const bayNumber = bayMatch ? bayMatch[1] : 'TBD';

                        // Create modal overlay
                        const overlay = document.createElement('div');
                        overlay.id = 'chargingBayModal';
                        overlay.className = 'modal-overlay';
                        overlay.style.display = 'flex';

                        // Create modal content
                        const modalContent = document.createElement('div');
                        modalContent.className = 'modal-content';
                        modalContent.style.textAlign = 'center';
                        modalContent.style.padding = '2rem';

                        // Modal message
                        const message = document.createElement('p');
                        message.textContent = `Please Proceed to Bay #${bayNumber}`;
                        message.style.fontSize = '1.25rem';
                        message.style.marginBottom = '1.5rem';

                        // OK button
                        const okButton = document.createElement('button');
                        okButton.textContent = 'OK';
                        okButton.className = 'modal-btn primary';
                        okButton.style.padding = '0.75rem 2rem';
                        okButton.onclick = () => {
                            document.body.removeChild(overlay);
                        };

                        modalContent.appendChild(message);
                        modalContent.appendChild(okButton);
                        overlay.appendChild(modalContent);
                        document.body.appendChild(overlay);

                        // Mark modal as shown for this ticket
                        localStorage.setItem(modalKey, 'true');
                    }

                    // Show modal for payment with Pay Online and Pay Cash buttons
                    function showPaymentModal() {
                        // Check if modal already exists
                        if (document.getElementById('paymentChoiceModal')) return;

                        // Create modal overlay
                        const overlay = document.createElement('div');
                        overlay.id = 'paymentChoiceModal';
                        overlay.className = 'modal-overlay';
                        overlay.style.display = 'flex';

                        // Create modal content
                        const modalContent = document.createElement('div');
                        modalContent.className = 'modal-content';
                        modalContent.style.textAlign = 'center';
                        modalContent.style.padding = '2rem';

                        // Modal message
                        const message = document.createElement('p');
                        message.textContent = 'Your payment is pending. Please choose a payment method:';
                        message.style.fontSize = '1.25rem';
                        message.style.marginBottom = '1.5rem';

                        // Pay Online button
                        const payOnlineBtn = document.createElement('button');
                        payOnlineBtn.textContent = 'Pay Online';
                        payOnlineBtn.className = 'modal-btn primary';
                        payOnlineBtn.style.marginRight = '1rem';
                        payOnlineBtn.onclick = () => {
                            // Redirect to online payment page or handle payment logic
                            window.location.href = 'wallet.php';
                        };

                        // Pay Cash button
                        const payCashBtn = document.createElement('button');
                        payCashBtn.textContent = 'Pay Cash';
                        payCashBtn.className = 'modal-btn secondary';
                        payCashBtn.onclick = () => {
                            // Close modal and possibly show instructions for cash payment
                            document.body.removeChild(overlay);
                            alert('Please proceed to pay cash at the charging station.');
                        };

                        modalContent.appendChild(message);
                        modalContent.appendChild(payOnlineBtn);
                        modalContent.appendChild(payCashBtn);
                        overlay.appendChild(modalContent);
                        document.body.appendChild(overlay);
                    }

                    // Show Charge Now popup (like Java MY_TURN notification)
                    function showChargeNowPopup(notification) {
                        // Create popup HTML similar to Java notification
                        const popupHtml = `
                            <div id="chargeNowPopup" style="position: fixed; top: 50%; left: 50%; transform: translate(-50%, -50%); background: white; border: 2px solid #00c2ce; border-radius: 15px; padding: 30px; width: 400px; max-width: 90vw; z-index: 10000; box-shadow: 0 10px 30px rgba(0,0,0,0.3); text-align: center;">
                                <div style="margin-bottom: 20px;">
                                    <div style="width: 60px; height: 60px; background: linear-gradient(135deg, #00c2ce 0%, #0e3a49 100%); border-radius: 50%; margin: 0 auto 15px; display: flex; align-items: center; justify-content: center;">
                                        <i class="fas fa-bolt" style="color: white; font-size: 24px;"></i>
                                    </div>
                                    <h3 style="margin: 0; color: #0e3a49; font-size: 1.5rem;">Charge Now</h3>
                                    <p style="margin: 10px 0; color: #666; font-size: 1rem;">Please go to your bay "${notification.bay_number}" now</p>
                                    <p style="margin: 0; color: #888; font-size: 0.9rem;">Ticket ID: ${notification.ticket_id}</p>
                                </div>
                                <div style="display: flex; gap: 15px; justify-content: center;">
                                    <button id="chargeNowCancel" style="padding: 12px 24px; background: #f0f0f0; color: #666; border: none; border-radius: 8px; cursor: pointer; font-weight: 600;">Cancel</button>
                                    <button id="chargeNowOK" style="padding: 12px 24px; background: linear-gradient(135deg, #00c2ce 0%, #0e3a49 100%); color: white; border: none; border-radius: 8px; cursor: pointer; font-weight: 600;">OK</button>
                                </div>
                            </div>
                            <div id="chargeNowOverlay" style="position: fixed; top: 0; left: 0; width: 100%; height: 100%; background: rgba(0,0,0,0.5); z-index: 9999;"></div>
                        `;
                        
                        document.body.insertAdjacentHTML('beforeend', popupHtml);
                        
                        // Handle OK button click (like Java system)
                        document.getElementById('chargeNowOK').addEventListener('click', function() {
                            // Show bay number popup (like Java system)
                            showBayNumberPopup(notification);
                            closeChargeNowPopup();
                        });
                        
                        // Handle Cancel button click
                        document.getElementById('chargeNowCancel').addEventListener('click', function() {
                            closeChargeNowPopup();
                        });
                        
                        // Handle overlay click to close
                        document.getElementById('chargeNowOverlay').addEventListener('click', function() {
                            closeChargeNowPopup();
                        });
                    }
                    
                    // Show Bay Number popup (like Java system)
                    function showBayNumberPopup(notification) {
                        const bayPopupHtml = `
                            <div id="bayNumberPopup" style="position: fixed; top: 50%; left: 50%; transform: translate(-50%, -50%); background: white; border: 2px solid #00c2ce; border-radius: 15px; padding: 30px; width: 350px; max-width: 90vw; z-index: 10000; box-shadow: 0 10px 30px rgba(0,0,0,0.3); text-align: center;">
                                <div style="margin-bottom: 20px;">
                                    <div style="width: 60px; height: 60px; background: linear-gradient(135deg, #ffd700 0%, #ffed4e 100%); border-radius: 50%; margin: 0 auto 15px; display: flex; align-items: center; justify-content: center;">
                                        <i class="fas fa-map-marker-alt" style="color: #333; font-size: 24px;"></i>
                                    </div>
                                    <h3 style="margin: 0; color: #0e3a49; font-size: 1.5rem;">Bay Assignment</h3>
                                    <p style="margin: 15px 0; color: #666; font-size: 1.1rem;">Your assigned bay is:</p>
                                    <div style="font-size: 2rem; font-weight: bold; color: #00c2ce; margin: 15px 0;">Bay ${notification.bay_number}</div>
                                    <p style="margin: 10px 0; color: #888; font-size: 0.9rem;">Ticket ID: ${notification.ticket_id}</p>
                                </div>
                                <button id="bayNumberOK" style="padding: 12px 30px; background: linear-gradient(135deg, #00c2ce 0%, #0e3a49 100%); color: white; border: none; border-radius: 8px; cursor: pointer; font-weight: 600; font-size: 1rem;">OK</button>
                            </div>
                            <div id="bayNumberOverlay" style="position: fixed; top: 0; left: 0; width: 100%; height: 100%; background: rgba(0,0,0,0.5); z-index: 9999;"></div>
                        `;
                        
                        document.body.insertAdjacentHTML('beforeend', bayPopupHtml);
                        
                        // Handle OK button click
                        document.getElementById('bayNumberOK').addEventListener('click', function() {
                            closeBayNumberPopup();
                            // Update ticket status from waiting to charging (like Java system)
                            updateTicketToCharging(notification.ticket_id);
                        });
                        
                        // Handle overlay click to close
                        document.getElementById('bayNumberOverlay').addEventListener('click', function() {
                            closeBayNumberPopup();
                        });
                    }
                    
                    // Close Charge Now popup
                    function closeChargeNowPopup() {
                        const popup = document.getElementById('chargeNowPopup');
                        const overlay = document.getElementById('chargeNowOverlay');
                        if (popup) popup.remove();
                        if (overlay) overlay.remove();
                    }
                    
                    // Close Bay Number popup
                    function closeBayNumberPopup() {
                        const popup = document.getElementById('bayNumberPopup');
                        const overlay = document.getElementById('bayNumberOverlay');
                        if (popup) popup.remove();
                        if (overlay) overlay.remove();
                    }
                    
                    // Update ticket status from waiting to charging (like Java system)
                    function updateTicketToCharging(ticketId) {
                        fetch('api/status_update.php', {
                            method: 'POST',
                            headers: {
                                'Content-Type': 'application/json',
                            },
                            body: JSON.stringify({
                                action: 'confirm_charging',
                                ticket_id: ticketId
                            })
                        })
                        .then(response => response.json())
                        .then(data => {
                            if (data.success) {
                                // Refresh status to show charging state
                                updateVehicleStatus();
                            }
                        })
                        .catch(error => {
                            console.log('Error updating ticket status:', error);
                        });
                    }

					// Update status every 5 seconds
					// Call once immediately so the UI reflects the latest state on page load
					try { updateVehicleStatus(); } catch (e) { console.error('initial updateVehicleStatus call failed', e); }
					setInterval(updateVehicleStatus, 5000);

                    // Also update when page becomes visible (user switches back to tab)
                    document.addEventListener('visibilitychange', function() {
                        if (!document.hidden) {
                            updateVehicleStatus();
                        }
                    });

					// Handle initial Pay Now button click on page load
					console.log('setting up payment modal handlers (immediate)');

					// Attach handlers to any element with .payment-option (buttons now)
					document.querySelectorAll('.payment-option').forEach(el => {
						el.addEventListener('click', function(e) {
							// If this is one of the dedicated pay buttons, skip the generic handler
							if (el.id === 'payWithCashBtn' || el.id === 'payWithEwalletBtn') {
								// Let the button-specific listeners handle the click
								return;
							}
							const method = el.getAttribute('data-method');
							console.log('payment-option clicked, method=', method);
							if (!method) return;
							selectPaymentMethod(method);
							try { payNow(method); } catch(e){ console.error('payNow failed', e); }
						});
					});

					console.log('Payment option handlers set up (buttons)');

					// Set up direct-pay button handlers (replaces confirm/cancel flow)
					const payCashBtn = document.getElementById('payWithCashBtn');
					const payEwalletBtn = document.getElementById('payWithEwalletBtn');

					if (payCashBtn) {
						payCashBtn.addEventListener('click', function() {
							console.log('Pay with Cash clicked');
							payNow('cash');
						});
					}

					if (payEwalletBtn) {
						// Open a confirmation step for e-wallet payments when clicked
						payEwalletBtn.addEventListener('click', function() {
							console.log('Pay with E-Wallet clicked');
							// Start the e-wallet payment flow which checks balance and shows a Proceed/Cancel confirmation
							try { initiateEwalletPayment(); } catch (e) { console.error('initiateEwalletPayment failed', e); }
						});
					}

					console.log('Direct pay button handlers set up');

					const initialButton = document.getElementById('vehicleActionBtn');
					console.log('Initial button found:', initialButton);
					console.log('Button text:', initialButton ? initialButton.textContent : 'none');

					if (initialButton && initialButton.textContent === 'Pay Now') {
						console.log('Setting up initial Pay Now button click handler');
						initialButton.onclick = function(e) {
							e.preventDefault();
							console.log('Initial Pay Now button clicked');

							// Get payment data from PHP variables
							const ticketId = '<?php echo $queue_ticket ? $queue_ticket['ticket_id'] : ''; ?>';
							const serviceType = '<?php echo $queue_ticket ? $queue_ticket['service_type'] : ''; ?>';
							const amount = <?php echo $queue_ticket ? calculateChargingAmount($queue_ticket['service_type']) : 0; ?>;

							console.log('PHP data:', { ticketId, serviceType, amount });

							if (ticketId) {
								currentTicketId = ticketId;
								totalAmount = amount;

								// Update modal with actual data
								const totalAmountEl = document.getElementById('totalAmount');
								const receiptServiceEl = document.getElementById('receiptService');

								if (totalAmountEl) {
									totalAmountEl.textContent = '₱' + totalAmount.toFixed(2);
								}
								if (receiptServiceEl) {
									receiptServiceEl.textContent = serviceType || 'Charging';
								}

								// Show the payment completion modal
								showPaymentCompletionModal();
							} else {
								console.log('No ticket ID found');
							}
						};
					} else if (initialButton && initialButton.textContent !== 'Pay Now') {
						// Handle other button actions on page load
						const buttonHref = '<?php echo $button_href; ?>';
						if (buttonHref) {
							initialButton.onclick = function(e) {
								e.preventDefault();
								if (buttonHref.includes('Monitor')) {
									window.open(buttonHref, '_blank');
								} else {
									window.location.href = buttonHref;
								}
							};
						}
					}

                    // Add click handlers for modal triggers
                    $(document).on('click', function(e) {
                        // Close modals when clicking outside
                        if (e.target.classList.contains('modal-overlay')) {
                            closeStationsModal();
                            closeScheduleModal();
                            closeSupportModal();
                        }
                    });

                    // Add keyboard support for modals
                    $(document).on('keydown', function(e) {
                        if (e.key === 'Escape') {
                            closeStationsModal();
                            closeScheduleModal();
                            closeSupportModal();
                        }
                    });

                    // Payment method selection - Global variables
                    let selectedPaymentMethod = null;
                    let currentTicketId = null;
                    let totalAmount = 0;

                    // Make functions global
                    window.selectPaymentMethod = function(method) {
                        console.log('selectPaymentMethod called with:', method);
                        
                        if (!method || (method !== 'cash' && method !== 'ewallet')) {
                            console.error('Invalid payment method:', method);
                            return;
                        }
                        
                        selectedPaymentMethod = method;
                        
                        // Update UI to show selected method
                        document.querySelectorAll('.payment-option').forEach(option => {
                            option.classList.remove('selected');
                        });
                        
                        const selectedOption = document.querySelector(`[data-method="${method}"]`);
                        if (selectedOption) {
                            selectedOption.classList.add('selected');
                        } else {
                            console.error('Payment option not found for method:', method);
                            return;
                        }
                        
						// Enable pay buttons (direct pay flow)
						const payCashBtnEl = document.getElementById('payWithCashBtn');
						const payEwalletBtnEl = document.getElementById('payWithEwalletBtn');
						if (payCashBtnEl) payCashBtnEl.disabled = false;
						if (payEwalletBtnEl) payEwalletBtnEl.disabled = false;
                        
                        // Show wallet balance if e-wallet is selected
                        if (method === 'ewallet') {
                            console.log('E-wallet selected, fetching balance');
                            fetchWalletBalance();
                        } else {
                            console.log('Cash selected, hiding wallet info');
                            document.getElementById('walletBalanceDisplay').style.display = 'none';
                            document.getElementById('errorMessage').style.display = 'none';
                        }
                    }

					// Fetch current wallet balance - returns numeric balance or throws
					window.fetchWalletBalance = async function() {
						console.log('Fetching wallet balance for amount:', totalAmount);
						const resp = await fetch('api/mobile.php?action=wallet-balance&username=<?php echo htmlspecialchars($_SESSION['username']); ?>');
						if (!resp.ok) throw new Error('Network response was not ok');
						const text = await resp.text();
						let data;
						try {
							data = JSON.parse(text);
						} catch (e) {
							console.error('Wallet balance JSON parse error:', e);
							console.error('Response text:', text);
							throw new Error('Invalid JSON response from wallet API');
						}
						if (!data.success) throw new Error(data.error || 'Wallet API error');
						const balance = Number(data.balance || 0);
						document.getElementById('currentWalletBalance').textContent = balance.toFixed(2);
						document.getElementById('walletBalanceDisplay').style.display = 'block';
						return balance;
					}

					// Confirm payment - now callable directly by payNow
					window.confirmPayment = async function(method) {
						console.log('confirmPayment called with method:', method);
						selectedPaymentMethod = method;

						// Validate payment method
						if (!selectedPaymentMethod || (selectedPaymentMethod !== 'cash' && selectedPaymentMethod !== 'ewallet')) {
							alert('Invalid payment method.');
							return;
						}



						// Validate ticket ID and amount
						if (!currentTicketId || currentTicketId.trim() === '') {
							alert('No ticket ID found. Please refresh the page and try again.');
							return;
						}
						if (!totalAmount || totalAmount <= 0 || isNaN(totalAmount)) {
							alert('Invalid payment amount. Please refresh the page and try again.');
							return;
						}

						// For e-wallet, check balance first
						if (selectedPaymentMethod === 'ewallet') {
							try {
								const balance = await fetchWalletBalance();
								if (balance < totalAmount) {
									document.getElementById('errorMessage').style.display = 'block';
									alert('Insufficient wallet balance. Please add funds or choose cash.');
									return;
								}
							} catch (err) {
								console.error('Wallet balance check failed:', err);
								alert('Unable to verify wallet balance. Please try again later.');
								return;
							}
						}

						// Show loading state on the pay buttons
						const payCashBtnEl = document.getElementById('payWithCashBtn');
						const payEwalletBtnEl = document.getElementById('payWithEwalletBtn');
						if (payCashBtnEl) payCashBtnEl.disabled = true;
						if (payEwalletBtnEl) payEwalletBtnEl.disabled = true;

						try {
							const response = await fetch('api/process_payment.php', {
								method: 'POST',
								headers: { 'Content-Type': 'application/json' },
								body: JSON.stringify({ ticket_id: currentTicketId, payment_method: selectedPaymentMethod, amount: totalAmount })
							});
							if (!response.ok) throw new Error('Network error');
							const text = await response.text();
							let data;
							try { data = JSON.parse(text); } catch (e) { throw new Error('Invalid JSON from payment API'); }

							if (data.success) {
								if (selectedPaymentMethod === 'cash') {
									alert('Cash payment recorded successfully!');
								} else {
									const remaining = data.remaining_balance != null ? Number(data.remaining_balance) : null;
									if (remaining != null) alert(`E-wallet payment processed. Remaining balance: ₱${remaining.toFixed(2)}`);
									else alert('E-wallet payment processed successfully!');
								}

								closePaymentModal();
								setTimeout(() => window.location.reload(), 900);
							} else {
								throw new Error(data.error || 'Payment failed');
							}
						} catch (err) {
							console.error('Payment error', err);
							alert('Payment failed: ' + (err.message || 'Unknown error'));
						} finally {
							if (payCashBtnEl) payCashBtnEl.disabled = false;
							if (payEwalletBtnEl) payEwalletBtnEl.disabled = false;
						}
					}

					// Pay Now helper - immediately attempts payment with the chosen method
					window.payNow = function(method) {
						// Ensure modal ticket data present
						console.log('payNow triggered for:', method, 'ticket:', currentTicketId, 'amount:', totalAmount);
						if (!method) return;
						// Short-circuit client-side UX messages to avoid server errors during development
						if (method === 'cash') {
							// Show a friendly instruction to the user
							showDialog('Cash Payment', 'Please go to the counter to pay.');
							// Close the payment modal after a short delay
							setTimeout(() => {
								closePaymentModal();
							}, 900);
							return;
						}

						if (method === 'ewallet') {
							// Attempt to fetch balance and provide user-friendly message, but do NOT call server payment API
							fetchWalletBalance().then(balance => {
								if (balance >= totalAmount) {
									showDialog('E-Wallet Payment', 'Your e-wallet has sufficient balance. Please confirm payment from the app.');
								} else {
									document.getElementById('errorMessage').style.display = 'block';
									showDialog('E-Wallet Payment', 'Insufficient wallet balance. Please add funds or choose cash.');
								}
							}).catch(err => {
								console.error('Wallet balance check failed:', err);
								showDialog('E-Wallet Payment', 'Unable to verify wallet balance. Please try again later.');
							});
							return;
						}

						// Fallback: if other methods are passed, call the original confirm flow
						confirmPayment(method);
					}

						// Close payment modal
						window.closePaymentModal = function() {
							console.log('closePaymentModal called');
                        
							const paymentModal = document.getElementById('paymentModal');
							if (paymentModal) {
								paymentModal.style.display = 'none';
							}

							// Reset payment selection and enable pay buttons
							selectedPaymentMethod = null;
							const payCashBtnEl = document.getElementById('payWithCashBtn');
							const payEwalletBtnEl = document.getElementById('payWithEwalletBtn');
							if (payCashBtnEl) payCashBtnEl.disabled = false;
							if (payEwalletBtnEl) payEwalletBtnEl.disabled = false;

							// Clear any selections
							document.querySelectorAll('.payment-option').forEach(option => {
								option.classList.remove('selected');
							});

							// Hide wallet balance and error messages
							const walletDisplay = document.getElementById('walletBalanceDisplay');
							const errorDisplay = document.getElementById('errorMessage');
							if (walletDisplay) walletDisplay.style.display = 'none';
							if (errorDisplay) errorDisplay.style.display = 'none';
                        
							console.log('Payment modal closed and reset');
						}

						// Global e-wallet confirmation helpers (separate from confirmPayment)
						window.initiateEwalletPayment = async function() {
							console.log('initiateEwalletPayment (global) called');
							if (!currentTicketId || !totalAmount) {
								alert('Payment data missing. Please refresh and try again.');
								return;
							}
							try {
								const balance = await fetchWalletBalance();
								console.log('Wallet balance:', balance, 'Total amount:', totalAmount);
								if (balance >= totalAmount) {
									showEwalletConfirmUI(balance);
								} else {
									document.getElementById('errorMessage').style.display = 'block';
									alert('Insufficient wallet balance. Please add funds or choose cash.');
								}
							} catch (err) {
								console.error('Error checking wallet balance for ewallet payment', err);
								alert('Unable to verify wallet balance. Please try again later.');
							}
						}

						window.showEwalletConfirmUI = function(balance) {
							const confirmEl = document.getElementById('ewalletConfirm');
							const walletBalanceDisplay = document.getElementById('walletBalanceDisplay');
							if (walletBalanceDisplay) walletBalanceDisplay.style.display = 'block';
							if (confirmEl) confirmEl.style.display = 'block';
							const payCashBtnEl = document.getElementById('payWithCashBtn');
							const payEwalletBtnEl = document.getElementById('payWithEwalletBtn');
							if (payCashBtnEl) payCashBtnEl.disabled = true;
							if (payEwalletBtnEl) payEwalletBtnEl.disabled = true;
						}

						window.hideEwalletConfirmUI = function() {
							const confirmEl = document.getElementById('ewalletConfirm');
							if (confirmEl) confirmEl.style.display = 'none';
							const payCashBtnEl = document.getElementById('payWithCashBtn');
							const payEwalletBtnEl = document.getElementById('payWithEwalletBtn');
							if (payCashBtnEl) payCashBtnEl.disabled = false;
							if (payEwalletBtnEl) payEwalletBtnEl.disabled = false;
						}

						// Proceed / Cancel wiring for the confirm UI
						document.addEventListener('click', function(e) {
							if (e.target && e.target.id === 'ewalletProceedBtn') {
								// Proceed: call the main confirmPayment flow which performs the deduction
								confirmPayment('ewallet');
							}
							if (e.target && e.target.id === 'ewalletCancelBtn') {
								// Cancel: hide confirm UI and return to modal
								hideEwalletConfirmUI();
							}
						});

					// Show payment completion modal
					// optional parameter force=true bypasses the per-ticket spam guard (useful for explicit user actions)
					window.showPaymentCompletionModal = function(force) {
                        console.log('showPaymentCompletionModal called');
                        console.log('currentTicketId:', currentTicketId);
                        console.log('totalAmount:', totalAmount);
                        
					// Prevent spamming: show once per ticket using localStorage, and guard against duplicate calls in this session
						if (!force) {
							try {
								const ticketKey = currentTicketId ? ('paymentModalShown_' + currentTicketId) : 'paymentModalShown_global';
								if (localStorage.getItem(ticketKey) === 'true') {
									console.log('Payment modal already shown for', ticketKey, '- skipping');
									return;
								}
								// mark as shown
								localStorage.setItem(ticketKey, 'true');
							} catch (e) {
								// localStorage could be unavailable in some privacy modes; fall back to in-memory guard
								console.warn('localStorage unavailable, using in-memory guard');
							}

							// in-memory guard to prevent duplicate shows in same page session
							if (window._paymentModalShowing) {
								console.log('Payment modal already showing in this session, skipping');
								return;
							}
							window._paymentModalShowing = true;
						} else {
							console.log('force=true: bypassing spam guard and showing modal');
						}

					// Reset payment selection and enable pay buttons
						selectedPaymentMethod = null;
						const payCashBtnEl = document.getElementById('payWithCashBtn');
						const payEwalletBtnEl = document.getElementById('payWithEwalletBtn');
						if (payCashBtnEl) payCashBtnEl.disabled = false;
						if (payEwalletBtnEl) payEwalletBtnEl.disabled = false;
                        
                        // Remove any existing selection
                        document.querySelectorAll('.payment-option').forEach(option => {
                            option.classList.remove('selected');
                        });
                        
                        // Hide wallet balance and error messages initially
                        const walletDisplay = document.getElementById('walletBalanceDisplay');
                        const errorDisplay = document.getElementById('errorMessage');
                        if (walletDisplay) walletDisplay.style.display = 'none';
                        if (errorDisplay) errorDisplay.style.display = 'none';
                        
                        // Show the payment modal
                        const paymentModal = document.getElementById('paymentModal');
                        if (paymentModal) {
                            paymentModal.style.display = 'flex';
                            console.log('Payment modal shown');
							// When modal is closed, reset the in-memory guard so it can be shown again in the same session if needed
							const observer = new MutationObserver(() => {
								if (paymentModal.style.display === 'none') {
									window._paymentModalShowing = false;
									observer.disconnect();
								}
							});
							observer.observe(paymentModal, { attributes: true, attributeFilter: ['style'] });
                        } else {
                            console.error('Payment modal element not found');
                        }
                    }

                    // Test function to manually show payment modal (for debugging)
                    window.testPaymentModal = function() {
                        console.log('Testing payment modal');
                        currentTicketId = 'TEST123';
                        totalAmount = 75.00;
                        showPaymentCompletionModal();
                    };

                    // Test function to check button functionality
                    window.testPayNowButton = function() {
                        const button = document.getElementById('vehicleActionBtn');
                        console.log('Button element:', button);
                        console.log('Button text:', button ? button.textContent : 'not found');
                        console.log('Button disabled:', button ? button.disabled : 'not found');
                        console.log('Button onclick:', button ? button.onclick : 'not found');
                        
                        if (button && button.textContent === 'Pay Now') {
                            console.log('Triggering Pay Now button click');
                            button.click();
                        } else {
                            console.log('Button is not Pay Now or not found');
                        }
                    };

                    // Function to clear all payment modal flags
                    window.clearPaymentModalFlags = function() {
                        console.log('Clearing all payment modal flags');
                        for (let i = localStorage.length - 1; i >= 0; i--) {
                            const key = localStorage.key(i);
                            if (key && key.startsWith('paymentModalShown_')) {
                                localStorage.removeItem(key);
                                console.log('Removed:', key);
                            }
                        }
                    };

                    // Test function to verify payment modal functionality
                    window.testPaymentFlow = function() {
                        console.log('=== Testing Payment Flow ===');
                        
                        // Test 1: Check if modal exists
                        const modal = document.getElementById('paymentModal');
                        console.log('1. Payment modal exists:', !!modal);
                        
						// Test 2: Check if payment option buttons exist
						const paymentOptions = document.querySelectorAll('.payment-option');
						console.log('2. Payment option buttons count:', paymentOptions.length);
                        
                        // Test 3: Check if buttons exist
						const payCashBtn = document.getElementById('payWithCashBtn');
						const payEwalletBtn = document.getElementById('payWithEwalletBtn');
						console.log('4. Pay with Cash button exists:', !!payCashBtn);
						console.log('5. Pay with E-Wallet button exists:', !!payEwalletBtn);
                        
                        // Test 4: Check if functions are global
                        console.log('6. selectPaymentMethod function exists:', typeof window.selectPaymentMethod);
                        console.log('7. confirmPayment function exists:', typeof window.confirmPayment);
                        console.log('8. closePaymentModal function exists:', typeof window.closePaymentModal);
                        
                        // Test 5: Show modal
                        console.log('9. Showing payment modal...');
                        currentTicketId = 'TEST123';
                        totalAmount = 75.00;
                        showPaymentCompletionModal();
                        
                        console.log('=== Test Complete ===');
                    };

                    // Test function to test payment method selection
                    window.testPaymentMethods = function() {
                        console.log('=== Testing Payment Methods ===');
                        
                        // Show modal first
                        currentTicketId = 'TEST123';
                        totalAmount = 75.00;
                        showPaymentCompletionModal();
                        
                        // Test cash selection
                        setTimeout(() => {
                            console.log('Testing cash selection...');
                            selectPaymentMethod('cash');
                        }, 1000);
                        
                        // Test e-wallet selection
                        setTimeout(() => {
                            console.log('Testing e-wallet selection...');
                            selectPaymentMethod('ewallet');
                        }, 2000);
                        
                        console.log('=== Payment Method Test Started ===');
                    };
                });
            </script>

		<!-- Footer -->

		<style>
		/* Modern payment modal styles */
		.modern-modal-card{
			width:100%;
			max-width:480px;
			background:var(--bg-card, #fff);
			border-radius:14px;
			box-shadow:0 20px 40px rgba(14,58,73,0.12);
			padding:18px 18px 16px 18px;
			display:flex;
			flex-direction:column;
			gap:8px;
			border:1px solid rgba(14,58,73,0.06);
		}

		.charging-modal-header{display:flex;gap:12px;align-items:center}
		.modern-icon{width:56px;height:56px;border-radius:12px;display:flex;align-items:center;justify-content:center;background:linear-gradient(135deg,#00c2ce 0%, #0e3a49 100%);color:#fff;font-size:20px}
		.charging-modal-title{margin:0;font-size:1.25rem;color:#0e3a49}
		.charging-modal-description{margin:0;color:#666;font-size:0.95rem}

		.modern-info{display:grid;grid-template-columns:1fr 1fr;gap:10px;margin-top:6px}
		.charging-info-item{background:rgba(14,58,73,0.02);padding:10px;border-radius:10px;display:flex;flex-direction:column}
		.charging-info-item .label{font-size:0.85rem;color:#667;}
		.charging-info-item .value{font-weight:700;color:#0e3a49;margin-top:4px}
		.total-amount{color:#00aab0}

		/* Apply payment-option card styles to buttons inside the modern button row */
		.modern-button-row .payment-option{display:flex;flex-direction:column;align-items:center;gap:8px;padding:10px;border-radius:10px;border:1px solid rgba(14,58,73,0.06);cursor:pointer;background:#fff;justify-content:center}
		.payment-option{ /* keep generic class for other uses */ display:inline-flex }
		.payment-option.selected{border-color:rgba(0,194,206,0.9);box-shadow:0 6px 18px rgba(0,194,206,0.08)}
		.payment-icon{font-size:20px;color:#0e3a49}
		.payment-label{font-weight:600;color:#0e3a49}

		.modern-button-row .modern-btn{padding:12px 14px;border-radius:10px;font-weight:700;border:0;cursor:pointer}
		.modern-button-row .btn-primary{background:linear-gradient(135deg,#00c2ce 0%, #0e3a49 100%);color:#fff}
		.modern-button-row .btn-secondary{background:#f3f6f7;color:#0e3a49}

		/* Ensure modal overlay centers content when displayed as flex */
		.charging-modal-overlay{position:fixed;inset:0;background:rgba(0,0,0,0.45);display:none;align-items:center;justify-content:center;z-index:10000;padding:16px}
		</style>
		<footer class="footer">
			<div class="container">
				<div class="footer-content">
					<div class="footer-section">
						<div class="footer-logo">
							<img src="images/logo.png" alt="Cephra" class="footer-logo-img" />
							<span class="footer-logo-text">CEPHRA</span>
						</div>
						<p class="footer-description">
							Your ultimate electric vehicle charging platform,
							powering the future of sustainable transportation.
						</p>
					</div>

					<div class="footer-section">
				<h4 class="footer-title">Platform</h4>
				<ul class="footer-links">
					<li><a href="dashboard.php">Home</a></li>
					<li><a href="../Monitor/monitor.php">Monitor</a></li>
					<li><a href="link.php">Link</a></li>
					<li><a href="history.php">History</a></li>
					<li><a href="rewards.php">Rewards</a></li>
				</ul>
			</div>
					<div class="footer-section">
						<h4 class="footer-title">Support</h4>
						<ul class="footer-links">
							<li><a href="help_center.php">Help Center</a></li>
							<li><a href="contact_us.php">Contact Us</a></li>
						</ul>
					</div>

					<div class="footer-section">
						<h4 class="footer-title">Company</h4>
						<ul class="footer-links">
							<li><a href="about_us.php">About Us</a></li>
							<li><a href="our_team.php">Our Team</a></li>
						</ul>
					</div>
				</div>

        <div class="footer-bottom">
            <p>&copy; 2025 Cephra. All rights reserved. | <a href="privacy_policy.php">Privacy Policy</a> | <a href="terms_of_service.php">Terms of Service</a></p>
    </div>
		</footer>

	</body>
</html>
