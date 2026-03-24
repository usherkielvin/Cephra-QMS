<?php
session_start();
if (!isset($_SESSION['username'])) {
    header("Location: index.php");
    exit();
}

require_once 'config/database.php';

$db = new Database();
$conn = $db->getConnection();

// Function to process image and remove white background
function process_image($image_path) {
    $processed_dir = 'images/processed/';
    if (!is_dir($processed_dir)) {
        mkdir($processed_dir, 0755, true);
    }

    $processed_path = $processed_dir . basename($image_path, '.' . pathinfo($image_path, PATHINFO_EXTENSION)) . '.png';

    if (file_exists($processed_path)) {
        return $processed_path;
    }

    // Load the image
    $image_info = getimagesize($image_path);
    if (!$image_info) {
        return $image_path; // Return original if can't load
    }

    $mime = $image_info['mime'];
    switch ($mime) {
        case 'image/jpeg':
            $src_image = imagecreatefromjpeg($image_path);
            break;
        case 'image/png':
            $src_image = imagecreatefrompng($image_path);
            break;
        case 'image/gif':
            $src_image = imagecreatefromgif($image_path);
            break;
        default:
            return $image_path;
    }

    if (!$src_image) {
        return $image_path;
    }

    $width = imagesx($src_image);
    $height = imagesy($src_image);

    // Create new image with alpha channel
    $new_image = imagecreatetruecolor($width, $height);
    imagealphablending($new_image, false);
    imagesavealpha($new_image, true);

    // Define white color (adjust fuzziness if needed)
    $white = imagecolorallocate($new_image, 255, 255, 255);

    // Copy pixels, making white transparent
    for ($x = 0; $x < $width; $x++) {
        for ($y = 0; $y < $height; $y++) {
            $rgb = imagecolorat($src_image, $x, $y);
            $colors = imagecolorsforindex($src_image, $rgb);
            $r = $colors['red'];
            $g = $colors['green'];
            $b = $colors['blue'];

            // If color is close to white (within 10), make transparent
            if ($r > 245 && $g > 245 && $b > 245) {
                $alpha = 127; // Fully transparent
            } else {
                $alpha = 0; // Opaque
            }

            $color = imagecolorallocatealpha($new_image, $r, $g, $b, $alpha);
            imagesetpixel($new_image, $x, $y, $color);
        }
    }

    // Save as PNG
    imagepng($new_image, $processed_path);
    imagedestroy($src_image);
    imagedestroy($new_image);

    return $processed_path;
}

// Function to generate random plate number: 3 letters + 4 numbers
function generatePlateNumber() {
    $letters = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ';
    $numbers = '0123456789';
    $plate = '';
    for ($i = 0; $i < 3; $i++) {
        $plate .= $letters[rand(0, 25)];
    }
    for ($i = 0; $i < 4; $i++) {
        $plate .= $numbers[rand(0, 9)];
    }
    return $plate;
}

$username = $_SESSION['username'];

// Check car_index from users table
$carIndex = null;
if ($conn) {
    $stmt = $conn->prepare("SELECT car_index FROM users WHERE username = :username");
    $stmt->bindParam(':username', $username);
    $stmt->execute();
    $result = $stmt->fetch(PDO::FETCH_ASSOC);
    $carIndex = $result['car_index'] ?? null;
}

// Handle form submissions
if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    if (isset($_POST['link_car'])) {
        // Link car functionality
        if ($conn) {
            // NOTE: Vehicle linking is currently SIMULATED for demo/academic purposes.
            // A random car model (car_index 0-8) and plate number are assigned.
            // In a production system this should be replaced with a real EV API integration
            // (e.g. Tesla API, OCPP identity, or a VIN/plate lookup service) so that the
            // actual vehicle owned by the user is linked to their account.
            $newCarIndex = rand(0, 8);
            $plateNumber = generatePlateNumber();
            $stmt = $conn->prepare("UPDATE users SET car_index = :car_index, plate_number = :plate_number WHERE username = :username");
            $stmt->bindParam(':car_index', $newCarIndex);
            $stmt->bindParam(':plate_number', $plateNumber);
            $stmt->bindParam(':username', $username);
            if ($stmt->execute()) {
                $_SESSION['car_linked'] = true;
                header("Location: link.php"); // Refresh to show "With Car"
                exit();
            } else {
                $error = "Failed to link car. Please try again.";
            }
        }
    }
}

// Fetch battery level from database
$db_battery_level = null;
if ($conn && $username) {
    $stmt_battery = $conn->prepare("SELECT battery_level FROM battery_levels WHERE username = :username ORDER BY last_updated DESC LIMIT 1");
    $stmt_battery->bindParam(':username', $username);
    $stmt_battery->execute();
    $battery_row = $stmt_battery->fetch(PDO::FETCH_ASSOC);
    $db_battery_level = $battery_row ? $battery_row['battery_level'] . '%' : null;
}

// Vehicle data based on car_index
$vehicle_data = null;
if ($carIndex !== null && $carIndex >= 0 && $carIndex <= 8) {
    // Real EV models
    $models = [
        0 => 'Audi q8 etron',
        1 => 'Nissan leaf',
        2 => 'Tesla x',
        3 => 'Lotus Spectre',
        4 => 'BYD Seagull',
        5 => 'Hyundai',
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
        0 => ['range' => '450 km', 'time_to_full' => '6h 0m', 'battery_level' => '80%', 'hp' => 300], // Audi q8 etron
        1 => ['range' => '220 km', 'time_to_full' => '8h 0m', 'battery_level' => '72%', 'hp' => 150], // Nissan leaf
        2 => ['range' => '400 km', 'time_to_full' => '7h 0m', 'battery_level' => '85%', 'hp' => 450], // Tesla x
        3 => ['range' => '500 km', 'time_to_full' => '5h 0m', 'battery_level' => '90%', 'hp' => 600], // Lotus Spectre
        4 => ['range' => '300 km', 'time_to_full' => '5h 0m', 'battery_level' => '75%', 'hp' => 200], // BYD Seagull
        5 => ['range' => '484 km', 'time_to_full' => '7h 20m', 'battery_level' => '95%', 'hp' => 400], // Hyundai
        6 => ['range' => '400 km', 'time_to_full' => '6h 0m', 'battery_level' => '85%', 'hp' => 500], // Porsche Taycan
        7 => ['range' => '400 km', 'time_to_full' => '7h 0m', 'battery_level' => '80%', 'hp' => 350], // BYD Tang
        8 => ['range' => '350 km', 'time_to_full' => '6h 0m', 'battery_level' => '78%', 'hp' => 250] // omada e5
    ];

    $vehicle_data = [
        'model' => $models[$carIndex],
        'status' => 'Connected',
        'range' => $vehicle_specs[$carIndex]['range'],
        'time_to_full' => $vehicle_specs[$carIndex]['time_to_full'],
        'battery_level' => $db_battery_level ?? $vehicle_specs[$carIndex]['battery_level'],
        'image' => $car_images[$carIndex]
    ];

    // Calculate range and time_to_full based on battery level
    $battery_level_str = $vehicle_data['battery_level'];
    $battery_level_num = floatval(str_replace('%', '', $battery_level_str));

    // Get max range from specs (assuming it's the max at 100%)
    $max_range_km = intval(str_replace(' km', '', $vehicle_specs[$carIndex]['range']));

    // Parse max charge time from specs
    $time_str = $vehicle_specs[$carIndex]['time_to_full'];
    preg_match('/(\d+)h\s*(\d+)m/', $time_str, $matches);
    $max_charge_time_hours = 0;
    if ($matches) {
        $hours = intval($matches[1]);
        $mins = intval($matches[2]);
        $max_charge_time_hours = $hours + $mins / 60;
    }

    // Calculate current range
    $current_range_km = round($max_range_km * ($battery_level_num / 100));
    $vehicle_data['range'] = $current_range_km . ' km';

    // Calculate time to full
    $time_to_full_hours = $max_charge_time_hours * ((100 - $battery_level_num) / 100);
    $hours_full = floor($time_to_full_hours);
    $mins_full = round(($time_to_full_hours - $hours_full) * 60);
    $vehicle_data['time_to_full'] = $hours_full . 'h ' . $mins_full . 'm';
}

// === Start: Status & Start Charging integration (copied/adapted from dashboard) ===
$latest_charging = null;
$active_ticket = null;
$current_ticket = null;
$status_text = 'Connected';
$start_charging_disabled = false;
$start_charging_disabled_status = '';
if ($conn) {
    // Fetch latest charging status from queue_tickets (current/pending sessions)
    $stmt_charging = $conn->prepare("SELECT * FROM queue_tickets WHERE username = :username AND status NOT IN ('complete') ORDER BY created_at DESC LIMIT 1");
    $stmt_charging->bindParam(':username', $username);
    $stmt_charging->execute();
    $latest_charging = $stmt_charging->fetch(PDO::FETCH_ASSOC);

    // Fetch active ticket
    $stmt_active = $conn->prepare("SELECT ticket_id, status, bay_number FROM active_tickets WHERE username = :username ORDER BY created_at DESC LIMIT 1");
    $stmt_active->bindParam(':username', $username);
    $stmt_active->execute();
    $active_ticket = $stmt_active->fetch(PDO::FETCH_ASSOC);

    // Prefer active ticket for current status
    if ($active_ticket) {
        $current_ticket = $active_ticket;
        if (strtolower($active_ticket['status']) === 'charging') {
            $status_text = 'Charging at Bay ' . ($active_ticket['bay_number'] ?? 'TBD');
        } else {
            $status_text = $active_ticket['status'];
        }
    } elseif ($latest_charging) {
        $current_ticket = $latest_charging;
        // If payment shows paid, treat as connected
        if (in_array(strtolower($latest_charging['payment_status']), ['paid','completed','success'])) {
            $status_text = 'Connected';
        } else {
            $status_text = $latest_charging['status'] ?: 'Connected';
        }
    }

    // Normalize and determine disabling statuses
    if ($current_ticket) {
        $st = strtolower($current_ticket['status']);
        if ($st === 'complete') $st = 'completed';
        if (in_array($st, ['waiting','charging','completed','pending'])) {
            $start_charging_disabled = true;
            $start_charging_disabled_status = ucfirst($st);
        }
    }
}
// === End: Status integration ===
?>
<!DOCTYPE HTML>
<html>
<head>
    <title>Link Your Car - Cephra</title>
    <meta charset="utf-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1, user-scalable=no" />
    <link rel="icon" type="image/png" href="images/logo.png?v=2" />
    <link rel="apple-touch-icon" href="images/logo.png?v=2" />
    <link rel="manifest" href="manifest.webmanifest" />
    <meta name="theme-color" content="#062635" />
    <link rel="stylesheet" href="css/vantage-style.css" />
    <link rel="stylesheet" href="assets/css/fontawesome-all.min.css" />
    <link rel="stylesheet" href="assets/css/pages/link.css" />
    <style>
        /* Modernized linked car layout tweaks */
        .modernized-layout {
            display: flex;
            gap: 1.5rem;
            align-items: center;
            justify-content: space-between;
            flex-wrap: wrap;
        }

        .modernized-details {
            display: flex;
            flex-direction: column;
            gap: 0.75rem;
            width: 100%;
            max-width: 720px;
        }

        .modernized-details .detail-row {
            display: flex;
            justify-content: space-between;
            align-items: center;
            gap: 0.5rem;
        }

        .modernized-details .detail-label { color: #4a5568; font-weight:600; }
        .modernized-details .detail-value { color: #1a202c; }

        @media (max-width: 768px) {
            .modernized-layout {
                flex-direction: column;
                align-items: center;
                text-align: center;
            }
            .modernized-details {
                align-items: center;
                max-width: 100%;
                padding: 0 1rem;
            }
            .modernized-details .detail-row {
                flex-direction: column;
                align-items: center;
                width: 100%;
            }
            .modernized-details .detail-row .detail-value {
                text-align: center;
            }
            .modernized-car-image { width: 100%; display:flex; justify-content:center; }
        }
    </style>
</head>
<body class="homepage is-preload">
    <div id="page-wrapper">
        <?php include __DIR__ . '/partials/header.php'; ?>

        <!-- Link Section -->
        <section class="link-section" style="padding: 100px 0; background: linear-gradient(135deg, #f8fafc 0%, #e2e8f0 100%);">
            <div class="container">
                <div class="section-header" style="text-align: center; margin-bottom: 60px;">
                    <h2 class="section-title" style="font-size: 2.5rem; font-weight: 700; margin-bottom: 1rem; background: linear-gradient(135deg, #00c2ce 0%, #0e3a49 100%); -webkit-background-clip: text; -webkit-text-fill-color: transparent; background-clip: text;"><?php echo is_null($carIndex) ? 'Link Your Car' : 'Linked Car'; ?></h2>
                    <p class="section-description" style="font-size: 1.2rem; color: rgba(26, 32, 44, 0.8); max-width: 600px; margin: 0 auto;"><?php echo is_null($carIndex) ? 'Connect your electric vehicle to start charging at Cephra stations' : 'Your electric vehicle is connected and ready for charging'; ?></p>
                </div>

                <div class="link-container" style="background: white; border-radius: 20px; padding: 2rem; border: 1px solid rgba(26, 32, 44, 0.1); box-shadow: 0 5px 15px rgba(0, 194, 206, 0.1); max-width: 1500px; margin: 0 auto; position: relative;">
                    <?php if (is_null($carIndex)): ?>
                        <!-- No Car Design -->
                        <div class="no-car-container" style="text-align: center; padding: 2rem;">
                            <h3 style="font-size: 2rem; font-weight: 700; margin-bottom: 1rem; background: linear-gradient(135deg, #00c2ce 0%, #0e3a49 100%); -webkit-background-clip: text; -webkit-text-fill-color: transparent; background-clip: text;">Link Your Electric Vehicle</h3>
                            <p style="font-size: 1.1rem; color: rgba(26, 32, 44, 0.8); margin-bottom: 2rem;">Connect your EV to start charging at Cephra stations and enjoy seamless charging experiences.</p>
                            <form method="post" id="linkForm" style="max-width: 400px; margin: 0 auto;">
                                <div class="terms-checkbox" style="margin-bottom: 1.5rem; text-align: left;">
                                    <input type="checkbox" id="terms" name="terms" required onclick="showTerms(); this.checked = false;" style="margin-right: 0.5rem;">
                                    <label for="terms" style="font-size: 0.9rem;">By linking, I agree to the <a href="#" onclick="showTerms(); return false;" style="color: #00c2ce;">Terms & Conditions</a></label>
                                </div>
                                <?php if (isset($error)): ?>
                                    <div class="error-message" style="color: #e53e3e; margin-bottom: 1rem; font-size: 0.9rem;"><?php echo htmlspecialchars($error); ?></div>
                                <?php endif; ?>
                                <button type="submit" name="link_car" class="link-button" id="linkBtn" disabled style="width: 100%; padding: 1rem; background: linear-gradient(135deg, #cccccc 0%, #b3b3b3 100%); color: white; border: none; border-radius: 12px; cursor: not-allowed; transition: all 0.3s ease; font-size: 1rem; font-weight: 600;">
                                    Link My Car
                                </button>
                            </form>
                        </div>
                    <?php else: ?>
<!-- With Car Design -->
<div class="linked-car-layout modernized-layout">
    <div class="modernized-details">
            <div class="detail-row">
                <span class="detail-label">Car Model</span>
                <span class="detail-value"><?php echo htmlspecialchars($vehicle_data['model']); ?></span>
            </div>
            <div class="detail-row">
                <span class="detail-label">Status</span>
                <span class="detail-value" id="linkVehicleStatus"><?php echo htmlspecialchars($status_text ?? $vehicle_data['status']); ?></span>
            </div>
            <div class="detail-row">
                <span class="detail-label">Kms Remaining</span>
                <span class="detail-value"><?php echo htmlspecialchars($vehicle_data['range']); ?></span>
            </div>
            <div class="detail-row">
                <span class="detail-label">Time to Charge</span>
                <span class="detail-value"><?php echo htmlspecialchars($vehicle_data['time_to_full']); ?></span>
            </div>
            <div class="detail-row battery-row">
                <span class="detail-label">Battery Level</span>
                <span class="progress-text" style="font-size: 2rem; font-weight: 700; color: #1a202c;"><?php echo htmlspecialchars($vehicle_data['battery_level']); ?></span>
            </div>

    </div>
    <div class="modernized-car-image" style="display:flex;align-items:center;justify-content:center;padding:1.25rem 0;">
        <img src="<?php echo htmlspecialchars($vehicle_data['image']); ?>" alt="<?php echo htmlspecialchars($vehicle_data['model']); ?>" style="max-width:100%;height:auto;display:block;" />
    </div>
</div>
<?php if ($start_charging_disabled): ?>
    <button id="startChargingBtn" class="btn btn-outline disabled" aria-disabled="true" style="display:none; position: absolute; bottom: 20px; left: 50%; transform: translateX(-50%); padding: 1rem 2rem; background: linear-gradient(135deg, #00c2ce 0%, #0e3a49 100%); color: white; border: none; border-radius: 12px; font-size: 1rem; font-weight: 600; cursor: not-allowed;">Start Charging (<?php echo htmlspecialchars($start_charging_disabled_status); ?>)</button>
<?php else: ?>
    <button id="startChargingBtn" class="btn btn-outline" style="position: absolute; bottom: 20px; left: 50%; transform: translateX(-50%); padding: 1rem 2rem; background: linear-gradient(135deg, #00c2ce 0%, #0e3a49 100%); color: white; border: none; border-radius: 12px; font-size: 1rem; font-weight: 600; cursor: pointer;" onclick="window.location.href='ChargingPage.php'">Start Charging</button>
<?php endif; ?>
                    <?php endif; ?>
                </div>
            </div>
        </section>
        
		<!-- Footer -->
		<?php include __DIR__ . '/partials/footer.php'; ?>
    </div>

    <!-- Scripts -->
    <script src="assets/js/jquery.min.js"></script>
    <script src="assets/js/jquery.dropotron.min.js"></script>
    <script src="assets/js/browser.min.js"></script>
    <script src="assets/js/breakpoints.min.js"></script>
    <script src="assets/js/util.js"></script>
    <script src="assets/js/main.js"></script>

    <script>
        /* Hide Start Charging when aria-disabled or class 'disabled' is present */
        const style = document.createElement('style');
        style.innerHTML = `
            #startChargingBtn.disabled,
            #startChargingBtn[aria-disabled="true"] { display: none !important; visibility: hidden !important; pointer-events: none !important; }
        `;
        document.head.appendChild(style);

        // Expose currentStatus for inline checks (initialized from PHP)
        window.currentStatus = '<?php echo isset($current_ticket) ? strtolower($current_ticket['status']) : ''; ?>';

        // Prevent navigation if button is disabled regardless of onclick attribute
        document.addEventListener('DOMContentLoaded', function() {
            const btn = document.getElementById('startChargingBtn');
            if (!btn) return;
            btn.addEventListener('click', function(e) {
                const blocked = btn.classList.contains('disabled') || btn.getAttribute('aria-disabled') === 'true' || ['charging','completed','complete','pending','waiting'].includes((window.currentStatus||'').toLowerCase());
                if (blocked) {
                    e.preventDefault();
                    // show small inline notice
                    alert('You already have an active or pending session: ' + (window.currentStatus || '')); 
                }
            });
        });

        // Poll status_update API every 3s to refresh the status and button state
        (function startPolling() {
            async function fetchStatus() {
                try {
                    const res = await fetch('api/status_update.php', {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({ action: 'get_status' })
                    });
                    if (!res.ok) return;
                    const data = await res.json();
                    if (!data || !data.success) return;

                    // Update status text
                    const statusEl = document.getElementById('linkVehicleStatus');
                    if (statusEl && data.status_text) statusEl.textContent = data.status_text;

                    // Update window.currentStatus for local checks
                    window.currentStatus = (data.status_text || '').toLowerCase();

                    // Update start button state
                    const startBtn = document.getElementById('startChargingBtn');
                    if (startBtn) {
                        const normalized = (data.status_text || '').toLowerCase();
                        const shouldDisable = ['complete','completed','pending','waiting','charging'].some(k => normalized.includes(k) || normalized === k);
                        if (shouldDisable) {
                            startBtn.classList.add('disabled');
                            startBtn.setAttribute('aria-disabled', 'true');
                            if (startBtn.tagName && startBtn.tagName.toLowerCase() === 'a') {
                                if (!startBtn.dataset._hrefBackup) startBtn.dataset._hrefBackup = startBtn.getAttribute('href') || '';
                                startBtn.removeAttribute('href');
                                startBtn.style.pointerEvents = 'none';
                                startBtn.tabIndex = -1;
                            } else {
                                try { startBtn.disabled = true; } catch(e){}
                            }
                        } else {
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
                    // ignore network errors silently
                    console.warn('Status poll failed', e);
                }
            }

            // Initial fetch and interval
            fetchStatus();
            setInterval(fetchStatus, 3000);
        })();
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

        // Enable/disable button based on terms checkbox
        document.getElementById('terms').addEventListener('change', function() {
            const linkBtn = document.getElementById('linkBtn');
            if (this.checked) {
                linkBtn.disabled = false;
                linkBtn.style.background = 'linear-gradient(135deg, #4CAF50 0%, #45a049 100%)';
                linkBtn.style.cursor = 'pointer';
            } else {
                linkBtn.disabled = true;
                linkBtn.style.background = 'linear-gradient(135deg, #cccccc 0%, #b3b3b3 100%)';
                linkBtn.style.cursor = 'not-allowed';
            }
        });

        function showTerms() {
            const termsText = `CEPHRA EV LINKING TERMS AND CONDITIONS
Effective Date: <?php echo date('Y-m-d'); ?>
Version: 1.0

1. ACCEPTANCE OF TERMS
By linking your electric vehicle (EV) to the Cephra app (the "Service"), you agree to these Terms.

2. LINKING PURPOSE
Linking enables ticketing, charging session history, and status updates within the app.

3. DATA COLLECTED
Vehicle identifiers, session timestamps, kWh consumed, payment references, and diagnostic status necessary for Service delivery.

4. USER RESPONSIBILITIES
You confirm you are authorized to link the vehicle and will keep your account secure.

5. CONSENT TO COMMUNICATIONS
You consent to in-app notifications and transactional emails about charging and tickets.

6. PRIVACY
We process data per our Privacy Policy. Data is retained only as long as needed for the Service.

7. LIMITATIONS
The Service provides information "as is" and availability may vary by station and network conditions.

8. SECURITY
We employ reasonable safeguards, but you acknowledge inherent risks in networked systems.

9. UNLINKING
You may unlink your vehicle at any time from the app; some records may be retained for compliance.

10. LIABILITY
To the maximum extent permitted by law, Cephra is not liable for indirect or consequential damages.

11. GOVERNING LAW
These Terms are governed by the laws of Pasay City, Philippines.

12. CONTACT
Cephra Support — support@cephra.com | +63 2 8XXX XXXX

Please scroll to the bottom and click "I Agree" to accept these Terms.`;

            const modal = document.createElement('div');
            modal.style.cssText = `
                position: fixed;
                top: 0;
                left: 0;
                width: 100%;
                height: 100%;
                background: rgba(0,0,0,0.8);
                display: flex;
                align-items: center;
                justify-content: center;
                z-index: 10000;
            `;

            const modalContent = document.createElement('div');
            modalContent.style.cssText = `
                background: white;
                padding: 20px;
                border-radius: 10px;
                max-width: 600px;
                max-height: 80vh;
                overflow-y: auto;
                position: relative;
                display: flex;
                flex-direction: column;
            `;

            modalContent.innerHTML = `
                <h3>Terms and Conditions</h3>
                <pre id="termsText" style="white-space: pre-wrap; font-family: inherit; font-size: 14px; line-height: 1.5; flex-grow: 1; overflow-y: auto;">${termsText}</pre>
                <button id="agreeBtn" disabled style="margin-top: 20px; padding: 10px 20px; background: #007bff; color: white; border: none; border-radius: 5px; cursor: not-allowed;">I Agree</button>
            `;

            modal.appendChild(modalContent);
            document.body.appendChild(modal);

            const termsTextEl = document.getElementById('termsText');
            const agreeBtn = document.getElementById('agreeBtn');

            termsTextEl.addEventListener('scroll', () => {
                if (termsTextEl.scrollTop + termsTextEl.clientHeight >= termsTextEl.scrollHeight - 5) {
                    agreeBtn.disabled = false;
                    agreeBtn.style.cursor = 'pointer';
                    agreeBtn.style.pointerEvents = 'auto';
                }
            });

            agreeBtn.addEventListener('click', () => {
                document.getElementById('terms').checked = true;
                modal.remove();
                const linkBtn = document.getElementById('linkBtn');
                linkBtn.disabled = false;
                linkBtn.style.background = 'linear-gradient(135deg, #4CAF50 0%, #45a049 100%)';
                linkBtn.style.cursor = 'pointer';
            });
        }
    </script>
</body>
</html>
