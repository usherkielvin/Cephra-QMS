package cephra.Phone.Utilities;
import javax.swing.*;
import java.awt.event.*;
import java.util.concurrent.*;

public class ChargingManager {
    private static ChargingManager instance;
    private static final ConcurrentHashMap<String, ChargingSession> activeSessions = new ConcurrentHashMap<>();
    
    private Timer globalMonitorTimer;
    private final ConcurrentHashMap<String, String> lastKnownUserStatus = new ConcurrentHashMap<>();
    private final java.util.Set<String> manualStopBlacklist = java.util.Collections.synchronizedSet(new java.util.HashSet<>());
    
    @SuppressWarnings("unused")
    private static class ChargingSession {
        Timer chargingTimer;
        String chargingType;
        long startTime;
        int startingBatteryLevel;
        boolean completionNotificationSent;
        
        ChargingSession(String type, int startLevel) {
            this.chargingType = type;
            this.startingBatteryLevel = startLevel;
            this.startTime = System.currentTimeMillis();
            this.completionNotificationSent = false;
        }
    }
    
    private ChargingManager() {
        startGlobalMonitoring();
    }
    
    public static ChargingManager getInstance() {
        if (instance == null) {
            instance = new ChargingManager();
        }
        return instance;
    }
    
    private void startGlobalMonitoring() {
        globalMonitorTimer = new Timer(2000, new ActionListener() { // Check every 2 seconds
            @Override
            public void actionPerformed(ActionEvent e) {
                monitorAllUsersForChargingStarts();
            }
        });
        globalMonitorTimer.setRepeats(true);
        globalMonitorTimer.start();
        System.out.println("ChargingManager: Started global monitoring for admin changes");
    }
    
    private void monitorAllUsersForChargingStarts() {
        try {
            java.util.List<String> activeUsers = getActiveUsers();
            
            for (String username : activeUsers) {
                if (username == null || username.trim().isEmpty()) continue;
                
                // FIRST CHECK: If user is in manual stop blacklist, NEVER restart!
                if (manualStopBlacklist.contains(username)) {
                    continue; // Skip - user manually stopped, don't auto-restart
                }
                
                String currentStatus = cephra.Database.CephraDB.getUserCurrentTicketStatus(username);
                String lastStatus = lastKnownUserStatus.get(username);
                
                // IMPORTANT: If lastStatus is "Complete", don't restart even if admin changes to "Charging"
                // This prevents restart after user manually stopped charging
                if ("Complete".equals(lastStatus)) {
                    // Update last known status but don't restart
                    lastKnownUserStatus.put(username, currentStatus != null ? currentStatus : "");
                    continue; // Skip this user - they already completed
                }
                
                // EXTRA SAFETY: Also check if currentStatus is "Complete" - don't restart even if lastStatus wasn't set properly
                if ("Complete".equals(currentStatus)) {
                    lastKnownUserStatus.put(username, currentStatus);
                    continue; // Skip this user - they are completed
                }
                
                // Check if admin just changed status to 'Charging'
                if ("Charging".equals(currentStatus) && !"Charging".equals(lastStatus)) {
                    // Admin just started charging for this user!
                    if (!isCharging(username)) {
                        // Check if user is already fully charged or completed
                        try {
                            int currentBattery = cephra.Database.CephraDB.getUserBatteryLevel(username);
                            if (currentBattery >= 100) {
                                System.out.println("ChargingManager: Skipping " + username + " - already at 100% battery");
                                continue; // Don't restart charging for full battery
                            }
                            
                            // Check if ticket is already complete
                            String ticketId = getActiveTicketForUser(username);
                            if (ticketId != null) {
                                String ticketStatus = getTicketStatusDirect(ticketId);
                                if ("Complete".equals(ticketStatus)) {
                                    System.out.println("ChargingManager: Skipping " + username + " - ticket already Complete");
                                    continue; // Don't restart charging for completed tickets
                                }
                            }
                        } catch (Exception batteryCheck) {
                            System.err.println("ChargingManager: Error checking battery/ticket for " + username + ": " + batteryCheck.getMessage());
                            continue; // Skip on error to be safe
                        }
                        
                        String ticketId = getActiveTicketForUser(username);
                        String serviceType = getServiceTypeForTicket(ticketId);
                        
                        if (serviceType != null) {
                            System.out.println("ChargingManager: Auto-detected admin started charging for " + username + " (" + serviceType + ")");
                            startCharging(username, serviceType);
                        }
                    }
                }
                
                // Update last known status
                lastKnownUserStatus.put(username, currentStatus != null ? currentStatus : "");
            }
        } catch (Exception ex) {
            System.err.println("ChargingManager: Error in global monitoring: " + ex.getMessage());
        }
    }
    
    /**
     * Get list of users with active tickets
     */
    private java.util.List<String> getActiveUsers() {
        java.util.List<String> users = new java.util.ArrayList<>();
        try (java.sql.Connection conn = cephra.Database.DatabaseConnection.getConnection()) {
            // Get users from both active_tickets and queue_tickets
            String query = "SELECT DISTINCT username FROM (" +
                          "SELECT username FROM active_tickets " +
                          "UNION " +
                          "SELECT username FROM queue_tickets WHERE status IN ('Waiting', 'Pending', 'In Progress', 'Charging')" +
                          ") AS all_users";
            
            try (java.sql.PreparedStatement stmt = conn.prepareStatement(query);
                 java.sql.ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String username = rs.getString("username");
                    if (username != null && !username.trim().isEmpty()) {
                        users.add(username);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("ChargingManager: Error getting active users: " + e.getMessage());
        }
        return users;
    }
    
    /**
     * Get active ticket ID for user
     */
    private String getActiveTicketForUser(String username) {
        try {
            String activeTicketId = cephra.Database.CephraDB.getActiveTicket(username);
            if (activeTicketId != null && !activeTicketId.isEmpty()) {
                return activeTicketId;
            }
            return cephra.Database.CephraDB.getQueueTicketForUser(username);
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Get service type for a ticket
     */
    private String getServiceTypeForTicket(String ticketId) {
        if (ticketId == null) return null;
        
        try (java.sql.Connection conn = cephra.Database.DatabaseConnection.getConnection()) {
            // Try active_tickets first
            try (java.sql.PreparedStatement stmt = conn.prepareStatement(
                    "SELECT service_type FROM active_tickets WHERE ticket_id = ?")) {
                stmt.setString(1, ticketId);
                try (java.sql.ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getString("service_type");
                    }
                }
            }
            
            // Try queue_tickets
            try (java.sql.PreparedStatement stmt = conn.prepareStatement(
                    "SELECT service_type FROM queue_tickets WHERE ticket_id = ?")) {
                stmt.setString(1, ticketId);
                try (java.sql.ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getString("service_type");
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("ChargingManager: Error getting service type: " + e.getMessage());
        }
        return null;
    }
    
    /**
     * Get ticket status directly from database
     */
    private String getTicketStatusDirect(String ticketId) {
        if (ticketId == null) return null;
        
        try (java.sql.Connection conn = cephra.Database.DatabaseConnection.getConnection()) {
            // Try queue_tickets first (most common)
            try (java.sql.PreparedStatement stmt = conn.prepareStatement(
                    "SELECT status FROM queue_tickets WHERE ticket_id = ?")) {
                stmt.setString(1, ticketId);
                try (java.sql.ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getString("status");
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("ChargingManager: Error getting ticket status: " + e.getMessage());
        }
        return null;
    }
    
    /**
     * Start background charging for a user
     */
    public void startCharging(String username, String serviceType) {
        if (username == null || username.isEmpty()) {
            return;
        }
        
        // FIRST CHECK: Don't start if user manually completed recently
        String lastStatus = lastKnownUserStatus.get(username);
        if ("Complete".equals(lastStatus)) {
            // Silently refuse to start - user manually completed
            return;
        }
        
        // Check if user is already charging - prevent double charging
        if (isCharging(username)) {
            System.out.println("ChargingManager: User " + username + " is already charging - skipping duplicate start");
            return;
        }
        
        // Stop any existing charging for this user (cleanup)
        stopCharging(username);
        
        try {
            int currentBatteryLevel = cephra.Database.CephraDB.getUserBatteryLevel(username);
            if (currentBatteryLevel >= 100) {
                System.out.println("ChargingManager: User " + username + " already fully charged - marking as complete");
                
                // Mark ticket as complete if battery is full
                String ticketId = getActiveTicketForUser(username);
                if (ticketId != null) {
                    cephra.Database.CephraDB.updateQueueTicketStatus(ticketId, "Complete");
                    lastKnownUserStatus.put(username, "Complete");
                }
                return;
            }
            
            // Create charging session
            ChargingSession session = new ChargingSession(serviceType, currentBatteryLevel);
            
            // Calculate charging speed
            int batteryRemaining = 100 - currentBatteryLevel;
            int totalChargingTimeMs;
            
            if (serviceType.toLowerCase().contains("fast")) {
                totalChargingTimeMs = 30 * 1000; // 30 seconds
            } else {
                totalChargingTimeMs = 60 * 1000; // 1 minute
            }
            
            // Calculate timer delay per 1% increment
            int timerDelayMs = totalChargingTimeMs / batteryRemaining;
            
            // Create charging timer
            session.chargingTimer = new Timer(timerDelayMs, new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    updateCharging(username);
                }
            });
            
            // Start charging
            activeSessions.put(username, session);
            session.chargingTimer.start();
            
            System.out.println("ChargingManager: Started " + serviceType + " charging for " + username + 
                             " from " + currentBatteryLevel + "% (background mode)");
            
        } catch (Exception e) {
            System.err.println("ChargingManager: Error starting charging for " + username + ": " + e.getMessage());
        }
    }
    
    /**
     * Update charging progress (called by timer)
     */
    private void updateCharging(String username) {
        ChargingSession session = activeSessions.get(username);
        if (session == null) {
            return;
        }
        
        try {
            int currentBattery = cephra.Database.CephraDB.getUserBatteryLevel(username);
            
            // Stop immediately if already at 100% (prevent over-charging)
            if (currentBattery >= 100) {
                System.out.println("ChargingManager: " + username + " already at 100% - completing charging");
                completeCharging(username);
                return;
            }
            
            // Increment battery by 1%
            int newBatteryLevel = Math.min(100, currentBattery + 1); // Cap at 100%
            cephra.Database.CephraDB.setUserBatteryLevel(username, newBatteryLevel);
            
            System.out.println("ChargingManager: " + username + " - Battery: " + newBatteryLevel + "% (" + session.chargingType + ")");
            
            // Check if charging is complete (exactly 100%)
            if (newBatteryLevel >= 100) {
                completeCharging(username);
            }
            
        } catch (Exception e) {
            System.err.println("ChargingManager: Error updating charging for " + username + ": " + e.getMessage());
            completeCharging(username);
        }
    }
    
    /**
     * Complete charging process
     */
    private void completeCharging(String username) {
        ChargingSession session = activeSessions.get(username);
        if (session == null) {
            return; // Already completed or removed
        }
        
        try {
            // IMMEDIATELY stop timer and remove session to prevent double execution
            if (session.chargingTimer != null && session.chargingTimer.isRunning()) {
                session.chargingTimer.stop();
                System.out.println("ChargingManager: Stopped charging timer for " + username);
            }
            
            // Remove session immediately to prevent race conditions
            activeSessions.remove(username);
            
            // Ensure battery is exactly 100% (never over)
            int currentBattery = cephra.Database.CephraDB.getUserBatteryLevel(username);
            if (currentBattery != 100) {
                cephra.Database.CephraDB.setUserBatteryLevel(username, 100);
                System.out.println("ChargingManager: Corrected battery to 100% for " + username);
            }
            
            // Update database ticket status to Complete
            String activeTicketId = cephra.Database.CephraDB.getActiveTicket(username);
            String queueTicketId = cephra.Database.CephraDB.getQueueTicketForUser(username);
            String currentTicketId = (activeTicketId != null && !activeTicketId.isEmpty()) ? activeTicketId : queueTicketId;
            
            if (currentTicketId != null) {
                cephra.Database.CephraDB.updateQueueTicketStatus(currentTicketId, "Complete");
                System.out.println("ChargingManager: Updated ticket " + currentTicketId + " status to Complete");
            }
            
            // Send completion notification (only once)
            if (currentTicketId != null && !session.completionNotificationSent) {
                cephra.Phone.Utilities.NotificationManager.addFullChargeNotification(username, currentTicketId);
                session.completionNotificationSent = true;
                // Notify web layer so Monitor/User pages update immediately
                cephra.Database.HttpNotifier.chargingCompleted(currentTicketId, username);
                System.out.println("ChargingManager: Sent completion notification for " + username + " ticket " + currentTicketId);
            }
            
            System.out.println("ChargingManager: Charging completed for " + username + " - Battery: 100%");
            
        } catch (Exception e) {
            System.err.println("ChargingManager: Error completing charging for " + username + ": " + e.getMessage());
            // Ensure session is removed even on error
            activeSessions.remove(username);
        }
    }
    
    /**
     * Stop charging for a user (manual stop or automatic completion)
     */
    public void stopCharging(String username) {
        stopCharging(username, false); // Default to not manual stop
    }
    
    /**
     * Stop charging for a user with option to specify if it's a manual stop
     * @param username The user to stop charging for
     * @param isManualStop True if user manually stopped, false if automatic completion
     */
    public void stopCharging(String username, boolean isManualStop) {
        ChargingSession session = activeSessions.get(username);
        if (session != null) {
            try {
                // IMMEDIATELY stop the charging timer and remove session to prevent restart
                if (session.chargingTimer != null && session.chargingTimer.isRunning()) {
                    session.chargingTimer.stop();
                    System.out.println("ChargingManager: Stopped charging timer for " + username);
                }
                
                // Remove session IMMEDIATELY to prevent global monitor from restarting
                activeSessions.remove(username);
                
                // If manual stop, add to blacklist to PERMANENTLY prevent auto-restart
                if (isManualStop) {
                    manualStopBlacklist.add(username);
                    System.out.println("ChargingManager: Added " + username + " to manual stop blacklist - will not auto-restart");
                }
                
                // Get current battery level for payment calculation
                int currentBatteryLevel = cephra.Database.CephraDB.getUserBatteryLevel(username);
                int chargedAmount = Math.max(0, currentBatteryLevel - session.startingBatteryLevel);
                
                // Update ticket status based on how charging was stopped
                String activeTicketId = cephra.Database.CephraDB.getActiveTicket(username);
                String queueTicketId = cephra.Database.CephraDB.getQueueTicketForUser(username);
                String currentTicketId = (activeTicketId != null && !activeTicketId.isEmpty()) ? activeTicketId : queueTicketId;
                
                if (currentTicketId != null) {
                    if (isManualStop) {
                        // Treat manual stop as completion (just like 100% charge)
                        cephra.Database.CephraDB.updateQueueTicketStatus(currentTicketId, "Complete");
                        System.out.println("ChargingManager: User " + username + " manually stopped charging at " + currentBatteryLevel + "% - marked as Complete (charged " + chargedAmount + "%)");
                        
                        // Send same "Done" notification as full charge
                        if (!session.completionNotificationSent) {
                            cephra.Phone.Utilities.NotificationManager.addFullChargeNotification(username, currentTicketId);
                            session.completionNotificationSent = true;
                            System.out.println("ChargingManager: Sent 'Done' notification for manual stop - " + username + " at " + currentBatteryLevel + "%");
                        }
                    } else {
                        // Automatic completion (100%)
                        cephra.Database.CephraDB.updateQueueTicketStatus(currentTicketId, "Complete");
                        System.out.println("ChargingManager: Charging completed automatically for " + username + " - Battery: 100%");
                        
                        // Send completion notification (only once)
                        if (!session.completionNotificationSent) {
                            cephra.Phone.Utilities.NotificationManager.addFullChargeNotification(username, currentTicketId);
                            session.completionNotificationSent = true;
                        }
                    }
                    
                    // Also update the last known status to prevent global monitor restart
                    lastKnownUserStatus.put(username, "Complete");
                }
                
                System.out.println("ChargingManager: Stopped charging for " + username + 
                                 " - Final battery: " + currentBatteryLevel + "% (charged " + chargedAmount + "%)");
                
            } catch (Exception e) {
                System.err.println("ChargingManager: Error stopping charging for " + username + ": " + e.getMessage());
                // Ensure session is removed even on error
                activeSessions.remove(username);
                if (isManualStop) {
                    manualStopBlacklist.add(username); // Still add to blacklist on error
                }
            }
        } else {
            System.out.println("ChargingManager: No active charging session found for " + username + " - already stopped");
        }
    }

    public boolean isCharging(String username) {
        return activeSessions.containsKey(username);
    }
    
    public String getChargingType(String username) {
        ChargingSession session = activeSessions.get(username);
        return session != null ? session.chargingType : null;
    }
 
    public void stopAllCharging() {
        for (String username : activeSessions.keySet()) {
            stopCharging(username);
        }
    }

    public void stopGlobalMonitoring() {
        if (globalMonitorTimer != null && globalMonitorTimer.isRunning()) {
            globalMonitorTimer.stop();
            System.out.println("ChargingManager: Stopped global monitoring");
        }
    }
}