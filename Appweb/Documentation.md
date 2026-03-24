# Cephra Appweb Documentation

## Overview
The `Appweb` folder contains the complete web application for the Cephra EV charging system. It is divided into three main components: **User** (customer-facing interface), **Admin** (management panel), and **Monitor** (real-time display system). This documentation provides a clear, specific description of each file's purpose and features to improve code readability and maintainability.

## File Structure Overview
- **Root Files**: Project documentation and manifests
- **Admin/**: Administrative management interface
- **Monitor/**: Live monitoring and announcement system
- **User/**: Customer-facing web application

---

## Root Files (Appweb/)

### README.md
- **Type**: Markdown documentation
- **Purpose**: Project overview, setup instructions, and general information about the Cephra application
- **Features**: Contains project description, installation guides, and contributor information

---

## Admin/ - Administrative Management Panel

### index.php
- **Type**: PHP (Main Dashboard)
- **Purpose**: Central admin dashboard with navigation to all management sections
- **Features**:
  - Multi-panel interface (Dashboard, Queue, Bays, Users, Staff, Analytics, Transactions, Settings)
  - Real-time statistics display (users, queue count, active bays, revenue)
  - Sidebar navigation with role-based access control
  - Multi-language support (English, Filipino, Bisaya, Spanish, Chinese)
  - Monitor web integration button
  - Session validation and staff status checking

**Internal Flow**:
1. Session check: Validates admin login and staff status from `staff_records` table
2. Database connection: Loads config/database.php for PDO connection
3. UI rendering: Loads panels dynamically via JS (admin-fixed.js) with AJAX calls to api/admin.php
4. Data refresh: Periodic AJAX fetches for stats (queue count from queue_tickets, bays from charging_bays)
5. Language application: Applies i18n from localStorage, updates DOM elements
6. Logout handling: Clears session and redirects to login.php

### login.php
- **Type**: PHP (Authentication)
- **Purpose**: Admin login interface with staff authentication
- **Features**:
  - Username/password authentication
  - Staff status validation (active/inactive)
  - Session management for admin access
  - Error handling for invalid credentials
  - Redirect to dashboard on successful login

**Internal Flow**:
1. Form submission: POST username/password to login.php
2. Query staff_records: SELECT status WHERE username = ? AND password = ?
3. If active: Set $_SESSION['admin_logged_in'] = true, $_SESSION['admin_username'] = username
4. Redirect: header("Location: index.php") on success; error message on failure
5. Security: Plaintext password check (note: should be hashed in production)

### logout.php
- **Type**: PHP (Session Management)
- **Purpose**: Handles admin logout and session cleanup
- **Features**:
  - Session destruction
  - Redirect to login page
  - Security cleanup

### README.md
- **Type**: Markdown documentation
- **Purpose**: Admin panel specific documentation
- **Features**: Admin-specific setup and usage instructions

### api/admin.php
- **Type**: PHP (API Endpoints)
- **Purpose**: Backend API for admin panel operations
- **Features**:
  - CRUD operations for users, staff, and system settings
  - Queue management (ticket assignment, status updates)
  - Bay management (status changes, maintenance)
  - Analytics data retrieval
  - Transaction history queries
  - Staff management (add, edit, deactivate)

**Internal Flow** (Example: Queue Management):
1. AJAX POST from admin-fixed.js with action='update_ticket_status'
2. Validate session: Check $_SESSION['admin_logged_in']
3. Query: UPDATE queue_tickets SET status = ? WHERE ticket_id = ?
4. If success: Broadcast to Monitor WebSocket (if connected)
5. Response: JSON {success: true, message: 'Status updated'}
6. Error handling: JSON {success: false, error: 'Invalid ticket'}

### config/database.php
- **Type**: PHP (Configuration)
- **Purpose**: Database connection configuration for admin panel
- **Features**:
  - PDO database connection setup
  - Connection error handling
  - Database credentials management

### css/admin.css
- **Type**: CSS (Styling)
- **Purpose**: Complete styling for admin panel interface
- **Features**:
  - Responsive design for desktop/tablet
  - Dark theme with accent colors
  - Modal dialogs styling
  - Table and form layouts
  - Navigation sidebar styling
  - Chart and analytics visualization styles

### js/admin-fixed.js
- **Type**: JavaScript (Frontend Logic)
- **Purpose**: Interactive functionality for admin panel
- **Features**:
  - Panel switching and navigation
  - Data loading and AJAX calls to admin API
  - User/staff management modals
  - Queue and bay status updates
  - Analytics chart rendering (Chart.js integration)
  - Transaction filtering and export
  - Real-time data refresh
  - Multi-language text switching

### images/
- **Type**: Image Assets
- **Purpose**: Visual assets for admin panel
- **Features**:
  - Logo files (logo.png, MONU.png)
  - Advertisement images (ads.png)
  - Thumbnail images (thumbnail.png)

---

## Monitor/ - Real-Time Monitoring System

### index.php
- **Type**: PHP (Main Monitor Interface)
- **Purpose**: Live monitoring display for charging station status
- **Features**:
  - Real-time bay status visualization (8 charging bays)
  - Queue display with pagination
  - Text-to-speech announcements for bay changes
  - Multi-language support (English, Chinese, Filipino, Bisaya, Spanish)
  - Theme switching (dark/light mode)
  - Fullscreen mode for large displays
  - WebSocket/polling fallback for real-time updates
  - Volume and speed controls for TTS
  - Responsive design for mobile/desktop

**Internal Flow** (Data Update Cycle):
1. Page load: Render placeholder bays, fetch initial data via fetchSnapshot() to api/monitor.php
2. WebSocket connect: ws://host:8080; onmessage parses JSON (bays, queue)
3. Render: updateBays() grids bay cards; renderQueuePage() paginates waiting tickets
4. Alerts: handleAlerts() compares lastBays/lastQueueTickets, triggers speak() for changes
5. TTS: speechSynthesisUtterance with voice selection by language, volume/rate from sliders
6. Fallback: If WS fails, startPolling() every 3s via fetchSnapshot()
7. User interaction: Theme/language changes persist to localStorage, re-render UI

### monitor.webmanifest
- **Type**: Web App Manifest
- **Purpose**: Progressive Web App configuration for monitor
- **Features**:
  - PWA installation support
  - App icons and theme colors
  - Display mode settings

### README.md
- **Type**: Markdown documentation
- **Purpose**: Monitor system documentation
- **Features**: Setup and usage instructions for the monitor

### test_websocket.html
- **Type**: HTML (Testing Tool)
- **Purpose**: WebSocket connection testing interface
- **Features**:
  - Manual WebSocket connection testing
  - Message sending/receiving
  - Connection status monitoring

### composer.json
- **Type**: JSON (Dependencies)
- **Purpose**: PHP dependency management for monitor
- **Features**: Lists required PHP packages and versions

### api/monitor.php
- **Type**: PHP (API Endpoints)
- **Purpose**: Data API for monitor system
- **Features**:
  - Real-time bay status retrieval
  - Queue data with waiting tickets
  - Waiting grid and charging grid data
  - Recent activity feed
  - Statistics calculation (available/occupied bays, queue count)
  - Cross-origin support for web access

**Internal Flow** (Data Retrieval):
1. GET request to monitor.php (CORS enabled)
2. DB connect: Load ../../Admin/config/database.php
3. Query bays: SELECT from charging_bays with JOIN to charging_grid for occupied status
4. Query queue: SELECT from queue_tickets WHERE status='Waiting' ORDER BY created_at
5. Query grids: waiting_grid ORDER BY position_in_queue; charging_grid ORDER BY bay_number
6. Query activity: Last 10 from queue_tickets ORDER BY created_at DESC
7. Compute stats: Count available/occupied/maintenance bays, queue length
8. Response: JSON {success: true, bays: [], queue: [], stats: {...}, timestamp: now}
9. Error: JSON {success: false, error: message} on DB failure

### src/MonitorWebSocketServer.php
- **Type**: PHP (WebSocket Server)
- **Purpose**: Real-time WebSocket server for live updates
- **Features**:
  - WebSocket connection handling
  - Real-time data broadcasting
  - Client connection management
  - Message routing and event handling

---

## User/ - Customer-Facing Web Application

### index.php
- **Type**: PHP (Landing Page)
- **Purpose**: Main entry point for user interface
- **Features**:
  - Welcome page with navigation
  - Login/register links
  - Basic site information

**Internal Flow**:
1. No session check: Public access
2. Render HTML: Basic template with links to login.php, Register_Panel.php
3. Static content: Site description, logo, navigation bar
4. Redirect logic: If logged in, header("Location: dashboard.php")

### login.php
- **Type**: PHP (Authentication)
- **Purpose**: User login interface
- **Features**:
  - Username/password authentication
  - Session management
  - Remember me functionality
  - Error handling and validation
  - Redirect to dashboard on success

**Internal Flow**:
1. Form POST: username, password, remember_me
2. Query users: SELECT * FROM users WHERE username = ? AND password = ?
3. If match: $_SESSION['username'] = username; $_SESSION['firstname'] = firstname
4. Remember me: Set cookie for 30 days if checked
5. Redirect: header("Location: dashboard.php") on success; error display on failure
6. Security: Plaintext password (recommend hashing)

### Register_Panel.php
- **Type**: PHP (Registration)
- **Purpose**: User registration form
- **Features**:
  - Multi-step registration process
  - Form validation (username, email, password)
  - Email verification
  - Database user creation
  - Success/error messaging

**Internal Flow**:
1. Form submission: POST firstname, lastname, username, email, password
2. Validation: Check username/email uniqueness via api/check_email.php
3. Email verification: Generate code, send via email_config.php, store in temp table
4. On verify: INSERT INTO users (username, firstname, lastname, email, password)
5. Redirect: To login.php with success message
6. Error: Display validation errors (e.g., weak password, duplicate email)

### dashboard.php
- **Type**: PHP (User Dashboard)
- **Purpose**: Main user dashboard after login
- **Features**:
  - Welcome message with user name
  - Navigation to all user features
  - Quick access to charging, history, profile
  - Live status display (queue count, active bays)
  - Battery level display (if car linked)

**Internal Flow**:
1. Session check: If !$_SESSION['username'], redirect to index.php
2. Query user: SELECT firstname FROM users WHERE username = ?
3. Live data: AJAX to api/mobile.php?action=live-status for queue/active_bays
4. Battery query: SELECT battery_level FROM battery_levels WHERE username = ?
5. Render: HTML with nav links (link.php, ChargingPage.php, history.php, profile.php)
6. Logout: Link to profile_logout.php

### link.php
- **Type**: PHP (Car Linking)
- **Purpose**: Interface for linking electric vehicles
- **Features**:
  - Car linking simulation (generates random car index)
  - Terms and conditions modal
  - Car details display (model, performance, battery level)
  - JavaScript form validation
  - Responsive design with car images

**Internal Flow**:
1. Session check: Require login, fetch user details
2. If already linked: Query battery_levels for car_index; display current car
3. Form submission: POST to link_action.php with terms acceptance
4. Simulation: Generate random car_index (1-100), insert/update battery_levels (random battery 10-90%)
5. Success: Display car details (model from predefined list, performance stats)
6. JS: Validate form, show/hide modal, AJAX for real-time check

### link_action.php
- **Type**: PHP (Backend Processing)
- **Purpose**: Handles car linking form submission
- **Features**:
  - Database update for car_index
  - Session management
  - Error handling and validation

**Internal Flow**:
1. POST from link.php: Check session username
2. Generate car: Random car_index, battery_level (10-90%), insert/UPDATE battery_levels
3. Validation: Ensure no existing link or overwrite if allowed
4. Response: JSON {success: true, car_index: X, battery: Y%} or error
5. Redirect: Back to link.php with message

### ChargingPage.php
- **Type**: PHP (Charging Selection)
- **Purpose**: Interface for selecting charging type
- **Features**:
  - Normal vs Fast charging options
  - AJAX ticket creation
  - Queue ticket popup display
  - Battery level validation
  - Responsive card layout

**Internal Flow**:
1. Session check: Require login, fetch firstname
2. Battery check: Query battery_levels; if <100%, show options; else error popup
3. Button click: JS processChargeRequest('Normal Charging'/'Fast Charging')
4. AJAX POST to charge_action.php: {serviceType: 'Normal Charging'}
5. Response: If success, showQueueTicketProceedPopup(ticketId, serviceType, batteryLevel)
6. Popup: Display ticket details, estimated wait (5 min), OK button to close
7. Error: showDialog('Charging', error message)

### charge_action.php
- **Type**: PHP (Ticket Creation)
- **Purpose**: Processes charging requests and creates tickets
- **Features**:
  - Battery level validation
  - Active ticket checking
  - Priority ticket logic (<20% battery)
  - Ticket ID generation with prefixes
  - Queue insertion and waiting grid management
  - Session storage for current ticket

**Internal Flow**:
1. POST from ChargingPage.php: Validate session, serviceType ('Normal Charging'/'Fast Charging')
2. Checks: No active ticket (COUNT active_tickets), car linked (battery_levels exists), battery <100%
3. Priority: If battery <20%, priority=1, prefix='NCH/FCHP' else 'NCH/FCH'
4. Ticket ID: MAX suffix from queue_tickets/active_tickets +1, e.g., 'NCH001'
5. Insert queue_tickets: status='Pending'/'Waiting' (priority), initial_battery_level
6. If priority: Find available waiting_grid slot, UPDATE with ticket details
7. Session: $_SESSION['currentService'], $_SESSION['currentTicketId']
8. Response: JSON {success: true, ticketId, serviceType, batteryLevel}
9. Errors: JSON {error: message} for validations

### history.php
- **Type**: PHP (Transaction History)
- **Purpose**: Displays user's charging history
- **Features**:
  - Paginated transaction list
  - Search and filter functionality
  - Date range filtering
  - Service type filtering (Normal/Fast)
  - Responsive table design

**Internal Flow**:
1. Session check: Require login
2. Query: SELECT from queue_tickets WHERE username = ? ORDER BY created_at DESC
3. JS filtering: Input for search, date from/to, checkboxes for Normal/Fast
4. Pagination: JS-based (client-side) with page size 10
5. Render: Table with ticket_id, service_type, status, payment_status, created_at
6. Export: Button to download filtered data as CSV (via JS)

### profile.php
- **Type**: PHP (User Profile)
- **Purpose**: User profile management
- **Features**:
  - Profile information display
  - Profile picture upload
  - Password change functionality
  - Account settings
  - Logout option

**Internal Flow**:
1. Session check: Require login, fetch user details from users table
2. Display: Show firstname, lastname, email, profile pic from uploads/
3. Upload: POST image to profile.php, save to uploads/profile_pictures/ with timestamp
4. Password change: POST new_password, UPDATE users SET password = ?
5. Settings: Update email/name if editable
6. Logout: Link to profile_logout.php (session_destroy, redirect to index.php)

### profile_logout.php
- **Type**: PHP (Session Management)
- **Purpose**: Handles user logout
- **Features**:
  - Session cleanup
  - Redirect to login page

### queue_ticket_popup.php
- **Status**: Removed — functionality is handled inline in ChargingPage.php

### FORGOT_PASSWORD_README.md
- **Status**: Removed — superseded by inline code documentation

### forgot_password.php, forgot_password_*.php (variants)
- **Type**: PHP (Password Reset)
- **Purpose**: Password recovery system (multiple iterations)
- **Features**:
  - Email-based password reset
  - Token generation and validation
  - Security measures against abuse
  - User notification system

### reset_password.php
- **Type**: PHP (Password Update)
- **Purpose**: Password reset completion
- **Features**:
  - Token validation
  - New password setting
  - Security validation

### verify_code.php, verify_code_updated.php
- **Type**: PHP (Email Verification)
- **Purpose**: Email verification for registration/reset
- **Features**:
  - Code generation and sending
  - Verification logic
  - Time-based expiration

### composer.json, composer.lock
- **Type**: JSON (Dependencies)
- **Purpose**: PHP dependency management
- **Features**: Package management and version locking

### api/mobile.php
- **Type**: PHP (Mobile API)
- **Purpose**: REST API for mobile applications
- **Features**:
  - User authentication endpoints
  - Profile and history retrieval
  - Ticket creation
  - Live status information
  - Available bays query
  - Cross-origin support

**Internal Flow** (Example: Login):
1. POST action='login', username, password
2. Query: SELECT from users WHERE username=? AND password=?
3. If match: session_start(), set $_SESSION, JSON {success: true, user: {firstname, etc.}}
4. Other actions: user-profile (GET username), user-history (GET username from queue_tickets), create-ticket (POST service_type), live-status (queue_count from queue_tickets 'Waiting/Processing', active_bays from charging_grid)
5. Error: JSON {success: false, error: message}
6. CORS: Headers for * origin, GET/POST methods

### api/check_email.php
- **Type**: PHP (Validation API)
- **Purpose**: Email availability checking
- **Features**:
  - Real-time email validation
  - Database lookup for existing emails

### api/forgot_password_*.php (variants)
- **Type**: PHP (Password Reset API)
- **Purpose**: API endpoints for password recovery
- **Features**:
  - Email sending for reset codes
  - Token validation
  - Multiple implementation attempts

### api/test_*.php
- **Type**: PHP (Testing Scripts)
- **Purpose**: API testing utilities
- **Features**:
  - Endpoint testing
  - Debug output
  - Development tools

### config/database.php
- **Type**: PHP (Configuration)
- **Purpose**: Database connection for user interface
- **Features**:
  - PDO connection setup
  - Error handling
  - Shared configuration

### config/email_config.php
- **Type**: PHP (Configuration)
- **Purpose**: Email service configuration
- **Features**:
  - SMTP settings
  - Email templates
  - Provider credentials

### config/SETUP_GUIDE.md
- **Type**: Markdown documentation
- **Purpose**: Setup and configuration guide
- **Features**: Step-by-step installation instructions

### config/*.sql, *.php (database setup files)
- **Type**: SQL/PHP (Database Setup)
- **Purpose**: Database schema and migration scripts
- **Features**:
  - Table creation
  - Column additions
  - Data seeding

### css/*.css
- **Type**: CSS (Styling)
- **Purpose**: Styling for various user interface pages
- **Features**:
  - vantage-style.css: Main theme styling
  - main.css: General layout and components
  - index.css: Landing page styling
  - forgot_password.css: Password reset form styling
  - reset_password.css: Password change styling
  - verify_code.css: Verification code styling
  - styles.css: Additional utility styles

### assets/css/pages/*.css
- **Type**: CSS (Page-Specific Styling)
- **Purpose**: Individual page styling
- **Features**:
  - history.css: Transaction history page styling
  - link.css: Car linking page styling
  - profile.css: User profile page styling
  - offline.css: Offline page styling
  - queue_ticket_popup.css: Ticket popup styling

### assets/js/*.js
- **Type**: JavaScript (Frontend Scripts)
- **Purpose**: Interactive functionality for user interface
- **Features**:
  - jquery.min.js: jQuery library
  - main.js: Main site functionality
  - i18n.js: Internationalization support
  - Various utility scripts (breakpoints, browser, util)

### assets/css/fontawesome-all.min.css, assets/webfonts/
- **Type**: CSS/Fonts (Icons)
- **Purpose**: Font Awesome icon library
- **Features**:
  - Complete icon set
  - Multiple font formats for compatibility

### images/
- **Type**: Image Assets
- **Purpose**: Visual assets for user interface
- **Features**:
  - Logo files (logo.png)
  - UI images (ads.png, qr.png, pop-up.png)
  - Monitor images (MONU.png)
  - Thumbnails (thumbnail.png)

### uploads/profile_pictures/
- **Type**: User Uploads
- **Purpose**: User profile picture storage
- **Features**:
  - Dynamic user-uploaded images
  - Timestamped filenames for uniqueness

---

## Key Integration Points

### Database Configuration
- `config/database.php` files are shared across Admin, Monitor, and User components
- All components connect to the same Cephra database

### Session Management
- User sessions managed in User/ PHP files
- Admin sessions in Admin/ with staff validation
- Cross-component authentication checks

### API Endpoints
- User API (`api/mobile.php`) provides mobile app integration
- Admin API (`api/admin.php`) handles management operations
- Monitor API (`api/monitor.php`) serves real-time data

### Real-Time Updates
- Monitor uses WebSocket server for live updates
- Admin panel can trigger monitor refreshes
- User actions (charging) update queue and bay status

### Multi-Language Support
- Admin panel has full i18n with language switching
- Monitor supports multiple languages for announcements
- User interface has basic localization support

This documentation provides a comprehensive overview of the Cephra Appweb structure. Each file's purpose is described with specific features to aid in understanding the codebase architecture and functionality.
