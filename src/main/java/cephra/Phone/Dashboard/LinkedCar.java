
package cephra.Phone.Dashboard;

import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import javax.swing.ImageIcon;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Random;

public class LinkedCar extends javax.swing.JPanel {

    // Array of car image paths (c1.png to c10.png)
    private final String[] carImages = {
        "/cephra/Cephra Images/c1.png",
        "/cephra/Cephra Images/c2.png",
        "/cephra/Cephra Images/c3.png",
        "/cephra/Cephra Images/c4.png",
        "/cephra/Cephra Images/c5.png",
        "/cephra/Cephra Images/c6.png",
        "/cephra/Cephra Images/c7.png",
        "/cephra/Cephra Images/c8.png",
        "/cephra/Cephra Images/c9.png",
        "/cephra/Cephra Images/c10.png"
    };
    @SuppressWarnings("unused")
    // Charging progress tracking (now handled by ChargingManager)
    // Keep these for compatibility but charging logic moved to background
    private boolean isCharging = false; // For UI display only
    @SuppressWarnings("unused")
    private String chargingType = ""; // For UI display only
    
    // Auto-refresh timer to detect when admin changes status
    private Timer statusCheckTimer;
    private String lastKnownStatus = ""; // Track last status to detect changes

    public LinkedCar() {
        initComponents();
         setPreferredSize(new java.awt.Dimension(370, 750));
        setSize(370, 750);
        setupLabelPosition(); // Set label position
        makeDraggable();
        
        // Update battery percentage from user's stored level
        refreshBatteryDisplay();
        
        // Set random car image for user
        setRandomCarImage();
        
        // Set plate number for user
        setUserPlateNumber();
        
        // Ensure car positioning follows NetBeans form settings
        ensureCarPositioning();
        
        // Initialize auto-refresh to detect admin changes
        initializeStatusChecker();
        
        // Add a focus listener to refresh battery display when panel becomes visible
        addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusGained(java.awt.event.FocusEvent e) {
                refreshBatteryDisplay();
                startStatusChecker(); // Start checking when panel gains focus
            }
        });
        
        // Also refresh when component becomes visible
        addHierarchyListener(new java.awt.event.HierarchyListener() {
            @Override
            public void hierarchyChanged(java.awt.event.HierarchyEvent e) {
                if ((e.getChangeFlags() & java.awt.event.HierarchyEvent.SHOWING_CHANGED) != 0) {
                    if (isShowing()) {
                        refreshBatteryDisplay();
                        // Check if user was already charging when panel becomes visible
                        resumeChargingIfActive();
                        startStatusChecker(); // Start checking when panel becomes visible
                    } else {
                        stopStatusChecker(); // Stop checking when panel is hidden
                    }
                }
            }
        });
    }
    
    // Method to refresh battery display with current values from database
    public void refreshBatteryDisplay() {
        try {
            String username = cephra.Database.CephraDB.getCurrentUsername();
            if (username != null && !username.isEmpty()) {
                int batteryLevel = cephra.Database.CephraDB.getUserBatteryLevel(username);
                
                if (batteryLevel == -1) {
                    // No battery initialized yet - show "Link Car" message
                    batterypercent.setText("Link Car");
                    km.setText("0 km");
                    charge.setEnabled(false);
                    charge.setToolTipText("Please link your car first to initialize battery level.");
                    charge.setBackground(new java.awt.Color(200, 200, 200));
                    charge.setForeground(new java.awt.Color(100, 100, 100));
                    // System.out.println("PorscheTaycan: No battery initialized for user " + username + " - showing 'Link Car' message");
                } else {
                    batterypercent.setText(batteryLevel + " %");
                    
                    // Update range based on battery level (roughly 8km per 1% battery)
                    int rangeKm = (int)(batteryLevel * 8);
                    km.setText(rangeKm + " km");
                    
                    // Rotate car based on battery percentage (0% = 0°, 50% = 90°, 100% = 180°)
                    updateCarRotation(batteryLevel);
                    
                    // Update charging time and type based on queue ticket
                    updateChargingTimeAndType(username, batteryLevel);
                    
                    // Update driving mode times based on battery level
                    updateDrivingModeTimes(batteryLevel);
                    
                    // Keep charge button always enabled - full battery handling is done in action listener with JDialog
                    charge.setEnabled(true);
                    charge.setToolTipText(null);
                    // Reset button appearance
                    charge.setBackground(null);
                    charge.setForeground(java.awt.Color.WHITE);
                    
                    // Control StopCharge button visibility based on charging status
                    if (cephra.Phone.Utilities.ChargingManager.getInstance().isCharging(username)) {
                        StopCharge.setVisible(true);
                    } else {
                        StopCharge.setVisible(false);
                    }
                    
                    // System.out.println("PorscheTaycan: Updated battery display to " + batteryLevel + "% for user " + username);
                }
            }
        } catch (Exception e) {
            System.err.println("Error refreshing battery display: " + e.getMessage());
        }
    }
    
    // CUSTOM CODE - DO NOT REMOVE - NetBeans will regenerate form code but this method should be preserved
    // Setup label position to prevent NetBeans from changing it
    private void setupLabelPosition() {
        if (jLabel1 != null) {
            jLabel1.setBounds(0, 0, 370, 750);
        }
    }
     private void makeDraggable() {
        final Point[] dragPoint = {null};

        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                dragPoint[0] = e.getPoint();
            }
        });

        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                if (dragPoint[0] != null) {
                    java.awt.Window window = SwingUtilities.getWindowAncestor(LinkedCar.this);
                    if (window != null) {
                        Point currentLocation = window.getLocation();
                        window.setLocation(
                            currentLocation.x + e.getX() - dragPoint[0].x,
                            currentLocation.y + e.getY() - dragPoint[0].y
                        );
                    }
                }
            }
        });
    }
    
    // Method to rotate the car icon based on degrees
    private ImageIcon rotateIcon(ImageIcon icon, double degrees) {
        int w = icon.getIconWidth();
        int h = icon.getIconHeight();
        
        java.awt.image.BufferedImage rotated = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = rotated.createGraphics();
        g2d.rotate(Math.toRadians(degrees), w/2, h/2);
        g2d.drawImage(icon.getImage(), 0, 0, null);
        g2d.dispose();
        
        return new ImageIcon(rotated);
    }
    
    // Method to update car rotation based on battery percentage
    private void updateCarRotation(int batteryLevel) {
        try {
            // Calculate rotation: 0% -> 0°, 50% -> 90°, 100% -> 180°
            double degrees = (batteryLevel / 100.0) * 180.0;
            
            // Get the current car icon and rotate it
            if (car.getIcon() != null && car.getIcon() instanceof ImageIcon) {
                // Get the original car image path based on current user
                String username = cephra.Database.CephraDB.getCurrentUsername();
                if (username != null && !username.isEmpty()) {
                    int carIndex = cephra.Database.CephraDB.getUserCarIndex(username);
                    if (carIndex >= 0 && carIndex < carImages.length) {
                        // Create fresh icon from resource to avoid cumulative rotation
                        ImageIcon originalIcon = new ImageIcon(getClass().getResource("/cephra/Cephra Images/ver.png"));
                        ImageIcon rotatedIcon = rotateIcon(originalIcon, degrees);
                        bar.setIcon(rotatedIcon);
                        
                        // System.out.println("LinkedCar: Rotated car to " + degrees + "° for battery level " + batteryLevel + "%");
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error rotating car icon: " + e.getMessage());
        }
    }
    
    // Method to update charging time and type based on queue ticket
    private void updateChargingTimeAndType(String username, int batteryLevel) {
        try {
            // Check if user has an active ticket (from active_tickets table first, then queue_tickets)
            String activeTicketId = cephra.Database.CephraDB.getActiveTicket(username);
            String queueTicketId = cephra.Database.CephraDB.getQueueTicketForUser(username);
            String currentTicketId = (activeTicketId != null && !activeTicketId.isEmpty()) ? activeTicketId : queueTicketId;
            String ticketStatus = cephra.Database.CephraDB.getUserCurrentTicketStatus(username);
            
            // Check if user has completed charging (battery at 100% or ticket completed)
            boolean isFullyCharged = (batteryLevel >= 100);
            boolean isChargingCompleted = (ticketStatus != null && 
                (ticketStatus.equals("Completed") || ticketStatus.equals("Complete")));
            
            if (isFullyCharged || isChargingCompleted) {
                // Background charging manager handles this now
                // No need to stop local timers
                // Don't handle full battery display here - that's handled elsewhere
                chargingTypeLabel.setText("");
                chargingTimeLabel.setText(isFullyCharged ? "Fully Charged" : "Ready");
                
            } else if (currentTicketId != null && ticketStatus != null && 
                (ticketStatus.equals("Charging"))) {
                
                // Get ticket details from database
                String serviceType = getTicketServiceType(currentTicketId);
                
                if (serviceType != null) {
                    // Only start real-time charging when admin sets status to "Charging"
                    if (!cephra.Phone.Utilities.ChargingManager.getInstance().isCharging(username)) {
                        // Start background charging (independent of UI)
                        cephra.Phone.Utilities.ChargingManager.getInstance().startCharging(username, serviceType);
                        System.out.println("LinkedCar: Started background charging for " + username);
                    }
                    
                    // Update display for charging state
                    if (serviceType.toLowerCase().contains("fast")) {
                        chargingTypeLabel.setText("Fast Charging");
                    } else {
                        chargingTypeLabel.setText("Normal Charging");
                    }
                    updateChargingTimeDisplay(batteryLevel, serviceType);
                } else {
                    // No ticket found, show not charging status
                    setNotChargingDisplay();
                }
            } else {
                // Show appropriate status based on ticket status
                if (ticketStatus != null) {
                    if (ticketStatus.equals("Pending") || ticketStatus.equals("Waiting")) {
                        chargingTypeLabel.setText("");
                        chargingTimeLabel.setText("Waiting");
                        // Just update UI, no notification
                    } else if (ticketStatus.equals("In Progress")) {
                        chargingTypeLabel.setText("");
                        chargingTimeLabel.setText("Ready to Charge");
                        // Just update UI, no notification
                    } else {
                        setNotChargingDisplay();
                    }
                } else {
                    setNotChargingDisplay();
                }
                
                // Background charging manager handles charging now
                // No need to stop local timers
            }
        } catch (Exception e) {
            System.err.println("Error updating charging time and type: " + e.getMessage());
            setNotChargingDisplay();
        }
    }
    
    /**
     * Set display for when user is not charging
     */
    private void setNotChargingDisplay() {
        chargingTypeLabel.setText("");
        chargingTimeLabel.setText("Not Charging");
        chargingTypeLabel.setCursor(new java.awt.Cursor(java.awt.Cursor.DEFAULT_CURSOR));
        
        // Remove click listeners
        for (java.awt.event.MouseListener ml : chargingTypeLabel.getMouseListeners()) {
            chargingTypeLabel.removeMouseListener(ml);
        }
        
        // Hide StopCharge button when not charging
        StopCharge.setVisible(false);
    }
    
    /**
     * Set display for when charging is completed (manual or automatic)
     */
    private void setChargingCompleteDisplay() {
        chargingTypeLabel.setText("");
        chargingTimeLabel.setText("Charging Complete");
        chargingTypeLabel.setCursor(new java.awt.Cursor(java.awt.Cursor.DEFAULT_CURSOR));
        
        // Remove click listeners
        for (java.awt.event.MouseListener ml : chargingTypeLabel.getMouseListeners()) {
            chargingTypeLabel.removeMouseListener(ml);
        }
        
        // Hide StopCharge button when charging is complete
        StopCharge.setVisible(false);
    }
    
    // Method to get service type from ticket ID
    private String getTicketServiceType(String ticketId) {
        try (java.sql.Connection conn = cephra.Database.DatabaseConnection.getConnection()) {
            // First try to get from active_tickets table (for charging users)
            try (java.sql.PreparedStatement stmt = conn.prepareStatement(
                    "SELECT service_type FROM active_tickets WHERE ticket_id = ?")) {
                stmt.setString(1, ticketId);
                
                try (java.sql.ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getString("service_type");
                    }
                }
            }
            
            // If not found in active_tickets, try queue_tickets table
            try (java.sql.PreparedStatement stmt = conn.prepareStatement(
                    "SELECT service_type FROM queue_tickets WHERE ticket_id = ?")) {
                stmt.setString(1, ticketId);
                
                try (java.sql.ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getString("service_type");
                    }
                }
            }
        } catch (java.sql.SQLException e) {
            System.err.println("Error getting ticket service type: " + e.getMessage());
        }
        return null;
    }
    @SuppressWarnings("unused")
    // Method to calculate charging time based on battery level and service type
    private int calculateChargingTime(int currentBatteryLevel, String serviceType) {
        // Charging rates from QueueBridge
        double minsPerPercentFast = 0.8;   // Fast charging: 0.8 minutes per 1%
        double minsPerPercentNormal = 1.6; // Normal charging: 1.6 minutes per 1%
        
        // Calculate percentage needed to reach 100%
        int batteryNeeded = Math.max(0, 100 - currentBatteryLevel);
        
        // Choose charging rate based on service type
        double minsPerPercent;
        if (serviceType != null && serviceType.toLowerCase().contains("fast")) {
            minsPerPercent = minsPerPercentFast;
        } else {
            minsPerPercent = minsPerPercentNormal;
        }
        
        // Calculate total minutes needed
        return (int) Math.round(batteryNeeded * minsPerPercent);
    }
    
    @SuppressWarnings("unused")
    // Method to format charging time for display
    private String formatChargingTime(int totalMinutes) {
        if (totalMinutes < 60) {
            return totalMinutes + "m";
        } else {
            int hours = totalMinutes / 60;
            int minutes = totalMinutes % 60;
            if (minutes == 0) {
                return hours + "h";
            } else {
                return hours + "h " + minutes + "m";
            }
        }
    }
    
    @SuppressWarnings("unused")
    // Method to show notification based on current status
    private void showNotificationForStatus(String status, String ticketId) {
        try {
            String username = cephra.Database.CephraDB.getCurrentUsername();
            
            // Add notification to history first using NotificationManager
            if (username != null && ticketId != null) {
                switch (status) {
                    case "WAITING":
                        cephra.Phone.Utilities.NotificationManager.addTicketWaitingNotification(username, ticketId);
                        break;
                    case "CHARGING":
                        // For charging status, we can use ticket pending since there's no specific charging method
                        cephra.Phone.Utilities.NotificationManager.addTicketPendingNotification(username, ticketId);
                        break;
                    case "MY_TURN":
                        // For My Turn notification, bay number is optional
                        cephra.Phone.Utilities.NotificationManager.addMyTurnNotification(username, ticketId, "");
                        break;
                    case "DONE":
                        // Full charge notification is already handled in completeCharging() method
                        break;
                }
            }
            
            // Then show the popup notification at the top
            java.awt.Window[] windows = java.awt.Window.getWindows();
            for (java.awt.Window window : windows) {
                if (window instanceof cephra.Frame.Phone) {
                    cephra.Frame.Phone phoneFrame = (cephra.Frame.Phone) window;
                    
                    SwingUtilities.invokeLater(() -> {
                        try {
                            cephra.Phone.Popups.Notification notification = new cephra.Phone.Popups.Notification();
                            notification.addToFrame(phoneFrame);
                            
                            // Show notification based on status
                            switch (status) {
                                case "WAITING":
                                    notification.updateAndShowNotification(
                                        cephra.Phone.Popups.Notification.TYPE_WAITING, 
                                        ticketId, 
                                        ""
                                    );
                                    break;
                                case "CHARGING":
                                    notification.updateAndShowNotification(
                                        cephra.Phone.Popups.Notification.TYPE_CHARGING, 
                                        ticketId, 
                                        ""
                                    );
                                    break;
                                case "MY_TURN":
                                    notification.updateAndShowNotification(
                                        cephra.Phone.Popups.Notification.TYPE_MY_TURN, 
                                        ticketId, 
                                        ""
                                    );
                                    break;
                                case "DONE":
                                    notification.updateAndShowNotification(
                                        cephra.Phone.Popups.Notification.TYPE_DONE, 
                                        ticketId, 
                                        ""
                                    );
                                    break;
                                default:
                                    System.out.println("LinkedCar: Unknown notification status: " + status);
                                    break;
                            }
                            
                            System.out.println("LinkedCar: Showed " + status + " notification for ticket " + ticketId);
                        } catch (Exception notifEx) {
                            System.err.println("Error showing " + status + " notification: " + notifEx.getMessage());
                        }
                    });
                    break;
                }
            }
        } catch (Exception ex) {
            System.err.println("Error finding phone frame for notification: " + ex.getMessage());
        }
    }
    
    /**
     * Stop charging manually when user clicks the stop button
     */
    private void stopChargingManually() {
        try {
            String username = cephra.Database.CephraDB.getCurrentUsername();
            if (username != null && cephra.Phone.Utilities.ChargingManager.getInstance().isCharging(username)) {
                
                // Stop charging immediately - no popups, no confirmation
                cephra.Phone.Utilities.ChargingManager.getInstance().stopCharging(username, true);
                
                // Update UI to show completion
                setChargingCompleteDisplay();
                
                System.out.println("LinkedCar: User manually stopped charging");
            }
        } catch (Exception e) {
            System.err.println("LinkedCar: Error stopping charging manually: " + e.getMessage());
        }
    }
    
    /**
     * Initialize the smart status checker that only acts on actual changes
     */
    private void initializeStatusChecker() {
        // Get initial status to avoid showing notification on first check
        try {
            String username = cephra.Database.CephraDB.getCurrentUsername();
            if (username != null) {
                lastKnownStatus = cephra.Database.CephraDB.getUserCurrentTicketStatus(username);
                if (lastKnownStatus == null) {
                    lastKnownStatus = "";
                }
            }
        } catch (Exception e) {
            lastKnownStatus = "";
        }
        
        statusCheckTimer = new Timer(1000, new ActionListener() { // Check every 1 second for faster battery sync
            @Override
            public void actionPerformed(ActionEvent e) {
                checkForStatusChanges();
                // Also refresh battery display to show charging progress
                refreshBatteryDisplayOnly();
            }
        });
        statusCheckTimer.setRepeats(true);
        
        // Start if panel is visible
        if (isShowing()) {
            startStatusChecker();
        }
    }
    
    /**
     * Check for status changes and only refresh/notify if something actually changed
     */
    private void checkForStatusChanges() {
        try {
            String username = cephra.Database.CephraDB.getCurrentUsername();
            if (username != null) {
                String currentStatus = cephra.Database.CephraDB.getUserCurrentTicketStatus(username);
                if (currentStatus == null) {
                    currentStatus = "";
                }
                
                // Only refresh if status actually changed
                if (!currentStatus.equals(lastKnownStatus)) {
                    System.out.println("LinkedCar: Status changed from '" + lastKnownStatus + "' to '" + currentStatus + "' - refreshing!");
                    lastKnownStatus = currentStatus;
                    
                    // Refresh the display which will trigger appropriate notifications
                    SwingUtilities.invokeLater(() -> {
                        refreshBatteryDisplay();
                    });
                }
                // If no change, do nothing (no spam!)
            }
        } catch (Exception ex) {
            System.err.println("Error checking status changes: " + ex.getMessage());
        }
    }
    
    /**
     * Start the status checker
     */
    private void startStatusChecker() {
        if (statusCheckTimer != null && !statusCheckTimer.isRunning()) {
            statusCheckTimer.start();
            System.out.println("LinkedCar: Started smart status checker");
        }
    }
    
    /**
     * Stop the status checker
     */
    private void stopStatusChecker() {
        if (statusCheckTimer != null && statusCheckTimer.isRunning()) {
            statusCheckTimer.stop();
            System.out.println("LinkedCar: Stopped smart status checker");
        }
    }
    
    /**
     * Refresh only the battery display without triggering status change notifications
     * Used for showing real-time charging progress
     */
    private void refreshBatteryDisplayOnly() {
        try {
            String username = cephra.Database.CephraDB.getCurrentUsername();
            if (username != null && !username.isEmpty()) {
                int batteryLevel = cephra.Database.CephraDB.getUserBatteryLevel(username);
                
                if (batteryLevel != -1) {
                    // Update battery percentage
                    batterypercent.setText(batteryLevel + " %");
                    
                    // Update range based on battery level
                    int rangeKm = (int)(batteryLevel * 8);
                    km.setText(rangeKm + " km");
                    
                    // Update car rotation
                    updateCarRotation(batteryLevel);
                    
                    // Update driving mode times
                    updateDrivingModeTimes(batteryLevel);
                    
                    // Update charging display if user is charging
                    if (cephra.Phone.Utilities.ChargingManager.getInstance().isCharging(username)) {
                        String chargingType = cephra.Phone.Utilities.ChargingManager.getInstance().getChargingType(username);
                        if (chargingType != null) {
                            if (chargingType.toLowerCase().contains("fast")) {
                                chargingTypeLabel.setText("Fast Charging - Click to Complete");
                            } else {
                                chargingTypeLabel.setText("Normal Charging - Click to Complete");
                            }
                            updateChargingTimeDisplay(batteryLevel, chargingType);
                            
                            // Make the charging type label clickable to complete charging
                            chargingTypeLabel.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
                            
                            // Add click listener for complete charging (remove existing listeners first)
                            for (java.awt.event.MouseListener ml : chargingTypeLabel.getMouseListeners()) {
                                chargingTypeLabel.removeMouseListener(ml);
                            }
                            
                            chargingTypeLabel.addMouseListener(new java.awt.event.MouseAdapter() {
                                @Override
                                public void mouseClicked(java.awt.event.MouseEvent e) {
                                    stopChargingManually();
                                }
                            });
                        }
                        // Show StopCharge button when charging
                        StopCharge.setVisible(true);
                    } else {
                        // Reset charging type label if not charging
                        chargingTypeLabel.setCursor(new java.awt.Cursor(java.awt.Cursor.DEFAULT_CURSOR));
                        // Remove click listeners
                        for (java.awt.event.MouseListener ml : chargingTypeLabel.getMouseListeners()) {
                            chargingTypeLabel.removeMouseListener(ml);
                        }
                        // Hide StopCharge button when not charging
                        StopCharge.setVisible(false);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error refreshing battery display: " + e.getMessage());
        }
    }
    
    // Method to check if user was charging and resume if necessary
    private void resumeChargingIfActive() {
        try {
            String username = cephra.Database.CephraDB.getCurrentUsername();
            if (username != null && !username.isEmpty()) {
                // Check if user has an active charging ticket (only "Charging" status starts the progress bar)
                String activeTicketId = cephra.Database.CephraDB.getActiveTicket(username);
                String queueTicketId = cephra.Database.CephraDB.getQueueTicketForUser(username);
                String currentTicketId = (activeTicketId != null && !activeTicketId.isEmpty()) ? activeTicketId : queueTicketId;
                String ticketStatus = cephra.Database.CephraDB.getUserCurrentTicketStatus(username);
                
                // Only resume if status is specifically "Charging" (admin has started it)
                if (currentTicketId != null && ticketStatus != null && ticketStatus.equals("Charging")) {
                    
                    int currentBatteryLevel = cephra.Database.CephraDB.getUserBatteryLevel(username);
                    if (currentBatteryLevel < 100) {
                        // Get service type to determine charging speed
                        String serviceType = getTicketServiceType(currentTicketId);
                        if (serviceType != null) {
                            // Use background charging manager instead of local timer
                            cephra.Phone.Utilities.ChargingManager.getInstance().startCharging(username, serviceType);
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error resuming charging: " + e.getMessage());
        }
    }
    
    // Method to update charging time display
    private void updateChargingTimeDisplay(int currentBatteryLevel, String serviceType) {
        try {
            int batteryRemaining = 100 - currentBatteryLevel;
            if (batteryRemaining <= 0) {
                chargingTimeLabel.setText("Fully Charged");
                return;
            }
            
            // Calculate time remaining based on service type
            int totalSecondsRemaining;
            if (serviceType.toLowerCase().contains("fast")) {
                // Fast charge: 30 seconds total, proportional to remaining battery
                totalSecondsRemaining = (30 * batteryRemaining) / 100;
            } else {
                // Normal charge: 60 seconds total, proportional to remaining battery
                totalSecondsRemaining = (60 * batteryRemaining) / 100;
            }
            
            // Format time display
            if (totalSecondsRemaining < 60) {
                chargingTimeLabel.setText(totalSecondsRemaining + "s");
            } else {
                int minutes = totalSecondsRemaining / 60;
                int seconds = totalSecondsRemaining % 60;
                if (seconds == 0) {
                    chargingTimeLabel.setText(minutes + "m");
                } else {
                    chargingTimeLabel.setText(minutes + "m " + seconds + "s");
                }
            }
        } catch (Exception e) {
            System.err.println("Error updating charging time display: " + e.getMessage());
        }
    }
    
    // Method to update driving mode times based on battery level
    private void updateDrivingModeTimes(int batteryLevel) {
        try {
            // Calculate driving times for different modes based on battery percentage
            // Base range: 8km per 1% battery (320km at 100%)
            int baseRangeKm = batteryLevel * 8;
            
            // Different efficiency modes:
            // Eco: 100% efficiency (base range)
            // Normal: 85% efficiency 
            // Sports: 70% efficiency
            
            int ecoRange = baseRangeKm;
            int normalRange = (int) (baseRangeKm * 0.85);
            int sportsRange = (int) (baseRangeKm * 0.70);
            
            // Convert to time estimates (assuming average speeds)
            // Eco: 60 km/h average
            // Normal: 70 km/h average  
            // Sports: 80 km/h average
            
            int ecoTimeMinutes = (int) Math.round((double) ecoRange / 60.0 * 60); // km / (km/h) * 60 min/h
            int normalTimeMinutes = (int) Math.round((double) normalRange / 70.0 * 60);
            int sportsTimeMinutes = (int) Math.round((double) sportsRange / 80.0 * 60);
            
            // Format time display
            eco.setText(formatDrivingTime(ecoTimeMinutes));
            normal.setText(formatDrivingTime(normalTimeMinutes));
            sports.setText(formatDrivingTime(sportsTimeMinutes));
            
            // System.out.println("LinkedCar: Updated driving mode times - Eco: " + formatDrivingTime(ecoTimeMinutes) + 
            //                  ", Normal: " + formatDrivingTime(normalTimeMinutes) + 
            //                  ", Sports: " + formatDrivingTime(sportsTimeMinutes) + 
            //                  " (battery: " + batteryLevel + "%)");
            
        } catch (Exception e) {
            System.err.println("Error updating driving mode times: " + e.getMessage());
            // Set default values on error
            eco.setText("3hrs 12mins");
            normal.setText("2hrs 50mins");
            sports.setText("1hr 30mins");
        }
    }
    
    // Method to format driving time for display
    private String formatDrivingTime(int totalMinutes) {
        if (totalMinutes < 60) {
            return totalMinutes + "mins";
        } else {
            int hours = totalMinutes / 60;
            int minutes = totalMinutes % 60;
            if (minutes == 0) {
                return hours + "hrs";
            } else {
                return hours + "hrs " + minutes + "mins";
            }
        }
    }

    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        StopCharge = new javax.swing.JButton();
        jLabel2 = new javax.swing.JLabel();
        plateNumber = new javax.swing.JLabel();
        car = new javax.swing.JLabel();
        jPanel1 = new javax.swing.JPanel();
        profilebutton = new javax.swing.JButton();
        historybutton = new javax.swing.JButton();
        homebutton2 = new javax.swing.JButton();
        linkbutton = new javax.swing.JButton();
        charge = new javax.swing.JButton();
        batterypercent = new javax.swing.JLabel();
        km = new javax.swing.JLabel();
        cover = new javax.swing.JLabel();
        bar = new javax.swing.JLabel();
        chargingTypeLabel = new javax.swing.JLabel();
        chargingTimeLabel = new javax.swing.JLabel();
        eco = new javax.swing.JLabel();
        normal = new javax.swing.JLabel();
        sports = new javax.swing.JLabel();
        jLabel1 = new javax.swing.JLabel();

        setLayout(null);

        StopCharge.setFont(new java.awt.Font("Segoe UI Black", 0, 36)); // NOI18N
        StopCharge.setForeground(new java.awt.Color(204, 0, 51));
        StopCharge.setIcon(new javax.swing.ImageIcon(getClass().getResource("/cephra/Cephra Images/STOPCHARGING.png"))); // NOI18N
        StopCharge.setToolTipText("Stop charging");
        StopCharge.setBorder(null);
        StopCharge.setBorderPainted(false);
        StopCharge.setContentAreaFilled(false);
        StopCharge.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                StopChargeActionPerformed(evt);
            }
        });
        add(StopCharge);
        StopCharge.setBounds(30, 520, 310, 130);

        jLabel2.setBackground(new java.awt.Color(204, 204, 204));
        jLabel2.setFont(new java.awt.Font("Segoe UI", 1, 14)); // NOI18N
        jLabel2.setForeground(new java.awt.Color(102, 102, 102));
        jLabel2.setText("PLATE NUMBER:");
        add(jLabel2);
        jLabel2.setBounds(61, 280, 110, 20);

        plateNumber.setBackground(new java.awt.Color(204, 204, 204));
        plateNumber.setFont(new java.awt.Font("Segoe UI", 1, 14)); // NOI18N
        plateNumber.setForeground(new java.awt.Color(102, 102, 102));
        plateNumber.setText("NBH3261");
        add(plateNumber);
        plateNumber.setBounds(180, 280, 70, 20);

        car.setIcon(new javax.swing.ImageIcon(getClass().getResource("/cephra/Cephra Images/c7.png"))); // NOI18N
        car.setPreferredSize(new java.awt.Dimension(301, 226));
        add(car);
        car.setBounds(36, 90, 301, 226);

        jPanel1.setOpaque(false);
        jPanel1.setLayout(null);
        add(jPanel1);
        jPanel1.setBounds(20, 50, 140, 100);

        profilebutton.setBorder(null);
        profilebutton.setBorderPainted(false);
        profilebutton.setContentAreaFilled(false);
        profilebutton.setFocusPainted(false);
        profilebutton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                profilebuttonActionPerformed(evt);
            }
        });
        add(profilebutton);
        profilebutton.setBounds(280, 680, 50, 40);

        historybutton.setBorder(null);
        historybutton.setBorderPainted(false);
        historybutton.setContentAreaFilled(false);
        historybutton.setFocusPainted(false);
        historybutton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                historybuttonActionPerformed(evt);
            }
        });
        add(historybutton);
        historybutton.setBounds(220, 680, 50, 40);

        homebutton2.setBorder(null);
        homebutton2.setBorderPainted(false);
        homebutton2.setContentAreaFilled(false);
        homebutton2.setFocusPainted(false);
        homebutton2.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                homebutton2ActionPerformed(evt);
            }
        });
        add(homebutton2);
        homebutton2.setBounds(160, 680, 50, 40);

        linkbutton.setBorder(null);
        linkbutton.setBorderPainted(false);
        linkbutton.setContentAreaFilled(false);
        linkbutton.setFocusPainted(false);
        linkbutton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                linkbuttonActionPerformed(evt);
            }
        });
        add(linkbutton);
        linkbutton.setBounds(90, 680, 50, 40);

        charge.setBorder(null);
        charge.setBorderPainted(false);
        charge.setContentAreaFilled(false);
        charge.setFocusPainted(false);
        charge.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                chargeActionPerformed(evt);
            }
        });
        add(charge);
        charge.setBounds(30, 680, 50, 40);

        batterypercent.setFont(new java.awt.Font("Segoe UI", 1, 24)); // NOI18N
        batterypercent.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        batterypercent.setText("25 %");
        add(batterypercent);
        batterypercent.setBounds(230, 430, 80, 30);

        km.setFont(new java.awt.Font("Segoe UI", 1, 24)); // NOI18N
        km.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        km.setText("400 km");
        add(km);
        km.setBounds(200, 480, 140, 30);

        cover.setIcon(new javax.swing.ImageIcon(getClass().getResource("/cephra/Cephra Images/co.png"))); // NOI18N
        add(cover);
        cover.setBounds(210, 390, 120, 120);

        bar.setIcon(new javax.swing.ImageIcon(getClass().getResource("/cephra/Cephra Images/ver.png"))); // NOI18N
        add(bar);
        bar.setBounds(210, 390, 120, 120);

        chargingTypeLabel.setFont(new java.awt.Font("Segoe UI Semibold", 1, 18)); // NOI18N
        chargingTypeLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        chargingTypeLabel.setText("Fast Charging");
        add(chargingTypeLabel);
        chargingTypeLabel.setBounds(30, 410, 150, 25);

        chargingTimeLabel.setFont(new java.awt.Font("Segoe UI", 1, 18)); // NOI18N
        chargingTimeLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        chargingTimeLabel.setText("45m");
        add(chargingTimeLabel);
        chargingTimeLabel.setBounds(20, 440, 160, 50);

        eco.setText("3hrs 12mins");
        add(eco);
        eco.setBounds(180, 568, 90, 16);

        normal.setText("2hrs 50mins");
        add(normal);
        normal.setBounds(180, 585, 90, 16);

        sports.setText("1hr 30mins");
        add(sports);
        sports.setBounds(180, 603, 90, 16);

        jLabel1.setIcon(new javax.swing.ImageIcon(getClass().getResource("/cephra/Cephra Images/linked.png"))); // NOI18N
        add(jLabel1);
        jLabel1.setBounds(3, 0, 380, 750);
    }// </editor-fold>//GEN-END:initComponents

    private void profilebuttonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_profilebuttonActionPerformed
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                java.awt.Window[] windows = java.awt.Window.getWindows();
                for (java.awt.Window window : windows) {
                    if (window instanceof cephra.Frame.Phone) {
                        cephra.Frame.Phone phoneFrame = (cephra.Frame.Phone) window;
                        phoneFrame.switchPanel(new cephra.Phone.Dashboard.Profile());
                        break;
                    }
                }
            }
        });
    }//GEN-LAST:event_profilebuttonActionPerformed

    private void historybuttonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_historybuttonActionPerformed
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                java.awt.Window[] windows = java.awt.Window.getWindows();
                for (java.awt.Window window : windows) {
                    if (window instanceof cephra.Frame.Phone) {
                        cephra.Frame.Phone phoneFrame = (cephra.Frame.Phone) window;
                        phoneFrame.switchPanel(new cephra.Phone.Dashboard.ChargeHistory());
                        break;
                    }
                }
            }
        });
    }//GEN-LAST:event_historybuttonActionPerformed

    private void homebutton2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_homebutton2ActionPerformed
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                java.awt.Window[] windows = java.awt.Window.getWindows();
                for (java.awt.Window window : windows) {
                    if (window instanceof cephra.Frame.Phone) {
                        cephra.Frame.Phone phoneFrame = (cephra.Frame.Phone) window;
                        phoneFrame.switchPanel(cephra.Phone.Dashboard.Home.getAppropriateHomePanel());
                        break;
                    }
                }
            }
        });
    }//GEN-LAST:event_homebutton2ActionPerformed

    private void linkbuttonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_linkbuttonActionPerformed
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                java.awt.Window[] windows = java.awt.Window.getWindows();
                for (java.awt.Window window : windows) {
                    if (window instanceof cephra.Frame.Phone) {
                        cephra.Frame.Phone phoneFrame = (cephra.Frame.Phone) window;
                        phoneFrame.switchPanel(new cephra.Phone.Dashboard.LinkConnect());
                        break;
                    }
                }
            }
        });
    }//GEN-LAST:event_linkbuttonActionPerformed

    private void chargeActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_chargeActionPerformed
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                java.awt.Window[] windows = java.awt.Window.getWindows();
                for (java.awt.Window window : windows) {
                    if (window instanceof cephra.Frame.Phone) {
                        cephra.Frame.Phone phoneFrame = (cephra.Frame.Phone) window;
                        phoneFrame.switchPanel(new cephra.Phone.Dashboard.ChargingOption());
                        break;
                    }
                }
            }
        });
    }//GEN-LAST:event_chargeActionPerformed

    private void StopChargeActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_StopChargeActionPerformed
         stopChargingManually();
    }//GEN-LAST:event_StopChargeActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton StopCharge;
    private javax.swing.JLabel bar;
    private javax.swing.JLabel batterypercent;
    private javax.swing.JLabel car;
    private javax.swing.JButton charge;
    private javax.swing.JLabel chargingTimeLabel;
    private javax.swing.JLabel chargingTypeLabel;
    private javax.swing.JLabel cover;
    private javax.swing.JLabel eco;
    private javax.swing.JButton historybutton;
    private javax.swing.JButton homebutton2;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JLabel km;
    private javax.swing.JButton linkbutton;
    private javax.swing.JLabel normal;
    private javax.swing.JLabel plateNumber;
    private javax.swing.JButton profilebutton;
    private javax.swing.JLabel sports;
    // End of variables declaration//GEN-END:variables
    
    // Method to set random car image for the current user
    private void setRandomCarImage() {
        try {
            String username = cephra.Database.CephraDB.getCurrentUsername();
            if (username != null && !username.isEmpty()) {
                // Get user's car index from database
                int carIndex = cephra.Database.CephraDB.getUserCarIndex(username);
                
                // If no car assigned yet, assign a random one
                if (carIndex == -1) {
                    carIndex = new Random().nextInt(carImages.length);
                    cephra.Database.CephraDB.setUserCarIndex(username, carIndex);
                    // System.out.println("LinkedCar: Assigned car " + (carIndex + 1) + " to user " + username);
                }
                
                // Set the car image while preserving the form positioning (x=34, y=130, size=301x226)
                // Special sizing for c2.png: increase height by 2 pixels to show full image (228 instead of 226)
                if (carIndex >= 0 && carIndex < carImages.length) {
                    car.setIcon(new javax.swing.ImageIcon(getClass().getResource(carImages[carIndex])));
                    
                    // Special case for c2.png - increase height by 2 pixels to show cut-off part
                    if (carIndex == 1) { // c2.png is at index 1 (c1=0, c2=1, c3=2, etc.)
                        car.setBounds(36, 90, 301, 228); // Height increased from 226 to 228
                        car.setPreferredSize(new java.awt.Dimension(301, 228));
                        // System.out.println("LinkedCar: Set c2.png car image with increased height (301x228) to show full image");
                    } else {
                        car.setBounds(36, 90, 301, 226);
                        car.setPreferredSize(new java.awt.Dimension(301, 226));
                        // System.out.println("LinkedCar: Set car image to " + carImages[carIndex] + " for user " + username + " at position (34, 130)");
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error setting car image: " + e.getMessage());
            // Fallback to default car image with preserved positioning
            car.setIcon(new javax.swing.ImageIcon(getClass().getResource("/cephra/Cephra Images/c1.png")));
            car.setBounds(36, 90, 301, 226);
            car.setPreferredSize(new java.awt.Dimension(301, 226));
        }
    }
    
    // Method to ensure car positioning follows NetBeans form settings
    private void ensureCarPositioning() {
        // Set car position and size according to NetBeans form settings
        // From form: x="34" y="130" width="-1" height="-1" preferredSize="301, 226"
        // Special case for c2.png: increase height by 2 pixels to show full image (228 instead of 226)
        try {
            String username = cephra.Database.CephraDB.getCurrentUsername();
            if (username != null && !username.isEmpty()) {
                int carIndex = cephra.Database.CephraDB.getUserCarIndex(username);
                if (carIndex == 1) { // c2.png is at index 1
                    car.setBounds(36, 90, 301, 228); // Height increased from 226 to 228
                    car.setPreferredSize(new java.awt.Dimension(301, 228));
                } else {
                    car.setBounds(36, 90, 301, 226);
                    car.setPreferredSize(new java.awt.Dimension(301, 226));
                }
            } else {
                car.setBounds(36, 90, 301, 226);
                car.setPreferredSize(new java.awt.Dimension(301, 226));
            }
        } catch (Exception e) {
            car.setBounds(36, 90, 301, 226);
            car.setPreferredSize(new java.awt.Dimension(301, 226));
        }
    }
    
    // Method to set plate number for the current user
    private void setUserPlateNumber() {
        try {
            String username = cephra.Database.CephraDB.getCurrentUsername();
            if (username != null && !username.isEmpty()) {
                // Get user's plate number from database
                String userPlateNumber = cephra.Database.CephraDB.getUserPlateNumber(username);
                
                // If no plate number assigned yet, generate a new unique one
                if (userPlateNumber == null) {
                    userPlateNumber = cephra.Database.CephraDB.generateUniquePlateNumber();
                    cephra.Database.CephraDB.setUserPlateNumber(username, userPlateNumber);
                   // System.out.println("LinkedCar: Generated and assigned plate number " + userPlateNumber + " to user " + username);
                }
                
                // Set the plate number on the label
                if (userPlateNumber != null) {
                    plateNumber.setText(userPlateNumber);
                  //  System.out.println("LinkedCar: Set plate number label to " + userPlateNumber + " for user " + username);
                } else {
                    plateNumber.setText("ABC0000"); // Fallback display
                  //  System.err.println("LinkedCar: Failed to get or generate plate number for user " + username + " - using fallback");
                }
            } else {
                plateNumber.setText("ABC0000"); // Default when no user logged in
                System.out.println("LinkedCar: No user logged in - using default plate number");
            }
        } catch (Exception e) {
            System.err.println("Error setting plate number: " + e.getMessage());
            e.printStackTrace();
            // Fallback to default plate number on error
            plateNumber.setText("ABC0000");
        }
    }
    
    @Override
    public void addNotify() {
        super.addNotify();
        // Refresh battery display when panel becomes visible
        SwingUtilities.invokeLater(() -> {
            refreshBatteryDisplay();
            setRandomCarImage(); // Also refresh car image
            setUserPlateNumber(); // Also refresh plate number
            ensureCarPositioning(); // Ensure positioning is maintained
        });
    }
    
    @Override
    public void removeNotify() {
        // Stop the status checker when panel is removed
        stopStatusChecker();
        super.removeNotify();
    }
}
