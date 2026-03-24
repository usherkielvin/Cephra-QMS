package cephra.Admin.Utilities;

import javax.swing.*;
import javax.swing.table.*;


import java.util.*;

public final class QueueBridge {
    private static DefaultTableModel model;
    private static final List<Object[]> records = new ArrayList<>();
    private static final Map<String, BatteryInfo> ticketBattery = new HashMap<>();
    private static int totalPaidCount = 0;

    // Configurable billing settings (central source of truth)
    private static volatile double RATE_PER_KWH = 15.0; // pesos per kWh
    private static volatile double MINIMUM_FEE = 50.0;   // pesos
    private static volatile double FAST_MULTIPLIER = 1.25; // Fast charging gets 25% premium
    
    // Static initialization block to load settings from database
    static {
        loadSettingsFromDatabase();
    }

    // Configurable charging speed (minutes per 1% charge)
    private static volatile double MINS_PER_PERCENT_FAST = 0.8;   // Fast charge
    private static volatile double MINS_PER_PERCENT_NORMAL = 1.6; // Normal charge

    // Battery info storage
    public static final class BatteryInfo {
        public final int initialPercent;
        public final double capacityKWh;
        public BatteryInfo(int initialPercent, double capacityKWh) {
            this.initialPercent = initialPercent;
            this.capacityKWh = capacityKWh;
        }
    }

    private QueueBridge() {}

    /** Register a JTable model so QueueBridge can sync with it */
    public static void registerModel(DefaultTableModel m) {
        model = m;
        if (model != null) {
            loadQueueFromDatabase();
        }
    }
    
    /** Load queue tickets from database */
    private static void loadQueueFromDatabase() {
        try {
            // Get all queue tickets from database
            List<Object[]> dbTickets = cephra.Database.CephraDB.getAllQueueTickets();
            records.clear(); // Clear existing records
            
            for (Object[] dbTicket : dbTickets) {
                // Normalize payment status: when status is Complete and not Paid, force Pending
                String status = String.valueOf(dbTicket[4] == null ? "" : dbTicket[4]).trim();
                String payment = String.valueOf(dbTicket[5] == null ? "" : dbTicket[5]).trim();
                if ("Complete".equalsIgnoreCase(status) && !"Paid".equalsIgnoreCase(payment)) {
                    payment = "Pending";
                }

                // Convert database format to queue format
                Object[] queueRecord = {
                    dbTicket[0], // ticket_id
                    dbTicket[1], // reference_number (or generate if null)
                    dbTicket[2], // username
                    dbTicket[3], // service_type
                    status,      // status (normalized)
                    payment,     // payment_status (normalized)
                    dbTicket[6], // priority
                    dbTicket[7], // initial_battery_level
                    "" // action (empty for now)
                };
                records.add(queueRecord);
            }
            
            // Update the table
            SwingUtilities.invokeLater(() -> {
                model.setRowCount(0); // clear
                for (Object[] record : records) {
                    Object[] visibleRow = toVisibleRow(record);
                    model.insertRow(0, visibleRow);
                }
            });
            
        } catch (Exception e) {
            System.err.println("Error loading queue from database: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /** Public method to reload queue from database (for hard refresh) */
    public static void reloadFromDatabase() {
        loadQueueFromDatabase();
    }
    
    /** Force an immediate refresh of the admin queue (useful when external payments are processed) */
    public static void forceImmediateRefresh() {
        SwingUtilities.invokeLater(() -> {
            try {
                triggerHardRefresh();
                System.out.println("QueueBridge: Forced immediate refresh due to external payment processing");
            } catch (Exception e) {
                System.err.println("QueueBridge: Error during forced refresh: " + e.getMessage());
            }
        });
    }

    /** Add a ticket with hidden random reference number */
    public static void addTicket(String ticket, String customer, String service, String status, String payment, String action) {
        String refNumber = generateReference(); // Use consistent 8-digit format
        final Object[] fullRecord = new Object[] { ticket, refNumber, customer, service, status, payment, action };

        records.add(0, fullRecord);

        // Store battery info for this ticket
        int userBatteryLevel = cephra.Database.CephraDB.getUserBatteryLevel(customer);
        ticketBattery.put(ticket, new BatteryInfo(userBatteryLevel, 40.0)); // 40kWh capacity
        
        // Respect desired flow: Priority (<20%) -> Waiting; else keep provided status (usually Pending)
        String finalStatus = (userBatteryLevel < 20) ? "Waiting" : status;
        
        // Set this as the user's active ticket with correct service type
        cephra.Database.CephraDB.setActiveTicket(customer, ticket, service, userBatteryLevel, "");
        
        // Add to database for persistent storage
        boolean dbSuccess = cephra.Database.CephraDB.addQueueTicket(ticket, customer, service, finalStatus, payment, userBatteryLevel);
        
        if (!dbSuccess) {
            System.err.println("Failed to add ticket " + ticket + " to database. It may already exist.");
            // Remove from memory records since database insertion failed
            records.remove(0);
            ticketBattery.remove(ticket);
            cephra.Database.CephraDB.clearActiveTicket(customer);
        } else if (userBatteryLevel < 20) {
            // Priority ticket was successfully added to database, also add to waiting grid
            try {
                // Check if ticket is already in waiting grid to prevent duplication
                boolean alreadyInWaitingGrid = cephra.Admin.BayManagement.isTicketInWaitingGrid(ticket);
                
                if (!alreadyInWaitingGrid) {
                    cephra.Admin.BayManagement.addTicketToWaitingGrid(ticket, customer, service, userBatteryLevel);
                }
                
                // Refresh the queue table to show the updated status
                SwingUtilities.invokeLater(() -> {
                    try {
                        // Update the status in the table model to reflect "Waiting" status
                        if (model != null) {
                            for (int i = 0; i < model.getRowCount(); i++) {
                                Object ticketVal = model.getValueAt(i, 0);
                                if (ticket.equals(String.valueOf(ticketVal))) {
                                    model.setValueAt("Waiting", i, 3); // Update status column
                                    break;
                                }
                            }
                        }
                        
                        // Trigger a hard refresh to ensure everything is in sync
                        triggerHardRefresh();
                    } catch (Exception refreshError) {
                        System.err.println("QueueBridge: Error refreshing table after priority ticket: " + refreshError.getMessage());
                    }
                });
            } catch (Exception e) {
                System.err.println("QueueBridge: Failed to add priority ticket " + ticket + " to waiting grid: " + e.getMessage());
            }
        }

        if (model != null) {
            final Object[] visibleRow = toVisibleRow(fullRecord);
            SwingUtilities.invokeLater(() -> model.insertRow(0, visibleRow));
        }
    }

    /** Helper: Convert full record (with ref) to visible row for table */
    private static Object[] toVisibleRow(Object[] fullRecord) {
        return new Object[] {
            fullRecord[0], // ticket
            fullRecord[2], // customer
            fullRecord[3], // service
            fullRecord[4], // status
            fullRecord[5], // payment
            fullRecord[8]  // action
        };
    }

    /** Battery Info Management */
    public static void setTicketBatteryInfo(String ticket, int initialPercent, double capacityKWh) {
        if (ticket != null) {
            ticketBattery.put(ticket, new BatteryInfo(initialPercent, capacityKWh));
        }
    }

    public static BatteryInfo getTicketBatteryInfo(String ticket) {
        // First check in-memory storage
        BatteryInfo info = ticketBattery.get(ticket);
        if (info != null) {
            return info;
        }
        
        // If not found in memory, try to get from database
        try {
            String customer = getTicketCustomer(ticket);
            if (customer != null && !customer.isEmpty()) {
                // Get battery level from queue_tickets table
                try (java.sql.Connection conn = cephra.Database.DatabaseConnection.getConnection();
                     java.sql.PreparedStatement stmt = conn.prepareStatement(
                         "SELECT initial_battery_level FROM queue_tickets WHERE ticket_id = ?")) {
                    
                    stmt.setString(1, ticket);
                    try (java.sql.ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            int initialBatteryLevel = rs.getInt("initial_battery_level");
                            // Store in memory for future use
                            info = new BatteryInfo(initialBatteryLevel, 40.0);
                            ticketBattery.put(ticket, info);
                            return info;
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error getting battery info from database for ticket " + ticket + ": " + e.getMessage());
        }
        
        return null;
    }

    /** Retrieve hidden reference number */
    public static String getTicketRefNumber(String ticket) {
        if (ticket == null) return "";
        for (Object[] record : records) {
            if (record != null && ticket.equals(String.valueOf(record[0]))) {
                // Reference number is always stored at index 1
                if (record[1] != null) {
                    return String.valueOf(record[1]);
                }
            }
        }
        return "";
    }

    /** Billing Calculations */
    public static double computeAmountDue(String ticket) {
        BatteryInfo info = ticketBattery.get(ticket);
        if (info == null) {
            // Get actual battery levels from the ticket and user
            String customer = getTicketCustomer(ticket);
            if (customer != null && !customer.isEmpty()) {
                // Get INITIAL battery level from ticket (where charging started)
                int initialBatteryLevel = getTicketInitialBatteryLevel(ticket);
                if (initialBatteryLevel == -1) {
                    initialBatteryLevel = 18; // fallback default
                }
                info = new BatteryInfo(initialBatteryLevel, 40.0);
            } else {
                info = new BatteryInfo(18, 40.0); // fallback default
            }
        }
        
        // Get current battery level (where charging stopped)
        String customer = getTicketCustomer(ticket);
        int currentBatteryLevel = 100; // Default to 100 if can't get current level
        if (customer != null && !customer.isEmpty()) {
            int userCurrentLevel = cephra.Database.CephraDB.getUserBatteryLevel(customer);
            if (userCurrentLevel != -1) {
                currentBatteryLevel = userCurrentLevel;
            }
        }
        
        // Get service type to determine pricing
        String serviceType = getTicketService(ticket);
        double multiplier = 1.0; // Default multiplier for normal charging
        
        if (serviceType != null && serviceType.toLowerCase().contains("fast")) {
            multiplier = FAST_MULTIPLIER; // Apply fast charging premium
        }
        
        // CORRECT CALCULATION: Only charge for what was actually used
        int initialLevel = Math.max(0, Math.min(100, info.initialPercent));
        int finalLevel = Math.max(0, Math.min(100, currentBatteryLevel));
        int actualChargedPercent = Math.max(0, finalLevel - initialLevel);
        
        // Calculate energy based on actual charged amount
        double usedFraction = actualChargedPercent / 100.0;
        double energyKWh = usedFraction * info.capacityKWh;
        double gross = energyKWh * RATE_PER_KWH * multiplier; // Apply service multiplier
        
        System.out.println("QueueBridge: Billing for ticket " + ticket + " - Initial: " + initialLevel + "%, Final: " + finalLevel + "%, Charged: " + actualChargedPercent + "%, Amount: ₱" + String.format("%.2f", Math.max(gross, MINIMUM_FEE * multiplier)));
        
        return Math.max(gross, MINIMUM_FEE * multiplier); // Apply multiplier to minimum fee too
    }
    
    /** Helper method to get customer name from ticket */
    private static String getTicketCustomer(String ticket) {
        if (ticket == null) return null;
        
        // First check in-memory records
        for (Object[] record : records) {
            if (record != null && ticket.equals(String.valueOf(record[0]))) {
                return String.valueOf(record[2]); // Customer is at index 2
            }
        }
        
        // If not found in memory, try database
        try {
            try (java.sql.Connection conn = cephra.Database.DatabaseConnection.getConnection();
                 java.sql.PreparedStatement stmt = conn.prepareStatement(
                     "SELECT username FROM queue_tickets WHERE ticket_id = ?")) {
                
                stmt.setString(1, ticket);
                try (java.sql.ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getString("username");
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error getting customer from database for ticket " + ticket + ": " + e.getMessage());
        }
        
        return null;
    }
    
    /** Helper method to get initial battery level from ticket */
    private static int getTicketInitialBatteryLevel(String ticket) {
        if (ticket == null) return -1;
        
        // Try database to get initial battery level from ticket
        try {
            try (java.sql.Connection conn = cephra.Database.DatabaseConnection.getConnection();
                 java.sql.PreparedStatement stmt = conn.prepareStatement(
                     "SELECT initial_battery_level FROM queue_tickets WHERE ticket_id = ?")) {
                
                stmt.setString(1, ticket);
                try (java.sql.ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt("initial_battery_level");
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error getting initial battery level from database for ticket " + ticket + ": " + e.getMessage());
        }
        
        return -1; // Not found
    }

    public static double computePlatformCommission(double grossAmount) {
        return grossAmount * 0.18; // 18%
    }

    public static double computeNetToStation(double grossAmount) {
        return grossAmount * 0.82; // 82%
    }

    public static int getTotalPaidCount() {
        return totalPaidCount;
    }

    /** Mark a payment as Paid and add history (Cash payment - Admin) */
    public static void markPaymentPaid(final String ticket) {
        markPaymentPaidWithMethod(ticket, "Cash");
    }
    
    /** Mark a payment as Paid and add history (Online payment) */
    public static void markPaymentPaidOnline(final String ticket) {
        markPaymentPaidWithMethod(ticket, "Online");
    }
    
    /** Mark a payment as Paid and add history (Online payment) - Skip wallet processing since it's already done */
    public static void markPaymentPaidOnlineSkipWallet(final String ticket) {
        markPaymentPaidWithMethodSkipWallet(ticket, "Online");
    }
    
    /** Mark a payment as Paid and add history with specified payment method */
    private static void markPaymentPaidWithMethod(final String ticket, final String paymentMethod) {
        if (ticket == null || ticket.trim().isEmpty()) {
            System.err.println("QueueBridge: Invalid ticket ID");
            return;
        }

        boolean foundInRecords = false;
        boolean incrementCounter = false;
        boolean alreadyPaid = false;
        String customerName = "";
        String serviceName = "";
        String referenceNumber = "";

        // Check if payment has already been processed for this ticket
        for (Object[] r : records) {
            if (r != null && ticket.equals(String.valueOf(r[0]))) {
                String prev = String.valueOf(r[5]); // Payment is index 5
                if ("Paid".equalsIgnoreCase(prev)) {
                    alreadyPaid = true;
                    break;
                }
                if (!"Paid".equalsIgnoreCase(prev)) {
                    incrementCounter = true;
                }
                r[5] = "Paid";
                // Generate a new unique reference number for this payment
                referenceNumber = generateReference();
                r[1] = referenceNumber; // Store in the original reference field
                foundInRecords = true;
                customerName = String.valueOf(r[2]);
                serviceName = String.valueOf(r[3]);
                
                // Note: We don't update the table model here anymore
                // The database operation will remove the ticket entirely, so no need to set "Paid" status
                // The proceed button will disappear when the ticket is removed from the table
                break;
            }
        }

        // If already paid, don't process again
        if (alreadyPaid) {
            return;
        }

        if (incrementCounter) totalPaidCount++;

        if (foundInRecords) {
            try {
                // Check if payment already exists in database to prevent duplicates
                if (cephra.Database.CephraDB.isPaymentAlreadyProcessed(ticket)) {
                    try {
                        removeTicket(ticket);
                        triggerHardRefresh();
                        triggerPanelSwitchRefresh();
                    } catch (Throwable ignore) {}
                    return;
                }
                
                // Calculate charging details
                BatteryInfo batteryInfo = getTicketBatteryInfo(ticket);
                int initialBatteryLevel = batteryInfo != null ? batteryInfo.initialPercent : 20;
                int chargingTimeMinutes = computeEstimatedMinutes(ticket);
                double totalAmount = computeAmountDue(ticket);
                
                // Use a single database transaction to ensure consistency
                boolean dbSuccess = cephra.Database.CephraDB.processPaymentTransaction(
                    ticket, customerName, serviceName, initialBatteryLevel, 
                    chargingTimeMinutes, totalAmount, paymentMethod, referenceNumber
                );
                
                if (dbSuccess) {
                    // Note: processPaymentTransaction already handles:
                    // - Battery update to 100%
                    // - Ticket removal from queue
                    // - Active ticket clearing
                    // - History addition
                    
                    System.out.println("QueueBridge: Payment processed successfully for ticket " + ticket);
                    
                    // Refresh history table to show the new completed ticket
                    try {
                        cephra.Admin.Utilities.HistoryBridge.refreshHistoryTable();
                        System.out.println("QueueBridge: History table refreshed successfully");
                    } catch (Exception e) {
                        System.err.println("QueueBridge: Error refreshing history table: " + e.getMessage());
                        // Don't fail the payment if history refresh fails
                    }
                    
                    // Don't close PayPop here - let PayPop handle its own navigation to receipt
                    
                    // COPY THE SAME APPROACH AS MANUAL "MARK AS PAID":
                    // Add a longer delay to ensure database operations complete before UI updates
                    javax.swing.Timer refreshTimer = new javax.swing.Timer(200, event -> {
                        try {
                            // Remove ticket from queue (same as manual payment)
                            removeTicket(ticket);
                            
                            // Try to refresh admin UI if available, but don't fail if admin is not open
                            try {
                                triggerHardRefresh();
                            } catch (Exception e) {
                                System.out.println("QueueBridge: Admin UI refresh failed (admin may not be open): " + e.getMessage());
                            }
                            
                            try {
                                triggerPanelSwitchRefresh();
                            } catch (Exception e) {
                                System.out.println("QueueBridge: Panel switch refresh failed (admin may not be open): " + e.getMessage());
                            }
                        } catch (Throwable t) {
                            System.err.println("QueueBridge: Error in refresh timer: " + t.getMessage());
                        }
                    });
                    refreshTimer.setRepeats(false);
                    refreshTimer.start();
                } else {
                    System.err.println("QueueBridge: Failed to process payment transaction for ticket " + ticket);
                    // Revert the payment status in the records array
                    for (Object[] r : records) {
                        if (r != null && ticket.equals(String.valueOf(r[0]))) {
                            r[5] = "Pending"; // Revert payment status
                            r[1] = ""; // Clear reference number
                            break;
                        }
                    }
                }
                
            } catch (Throwable t) {
                System.err.println("QueueBridge: Error processing payment completion: " + t.getMessage());
                t.printStackTrace();
                
                // Revert any changes made to the records array
                for (Object[] r : records) {
                    if (r != null && ticket.equals(String.valueOf(r[0]))) {
                        r[5] = "Pending"; // Revert payment status
                        r[1] = ""; // Clear reference number
                        break;
                    }
                }
            }
        }
        
        // Note: Table model is now updated BEFORE database operations in updateTableModelForPayment()
    }
    
    /** Mark a payment as Paid and add history with specified payment method - Skip wallet processing */
    private static void markPaymentPaidWithMethodSkipWallet(final String ticket, final String paymentMethod) {
        if (ticket == null || ticket.trim().isEmpty()) {
            System.err.println("QueueBridge: Invalid ticket ID");
            return;
        }

        boolean foundInRecords = false;
        boolean incrementCounter = false;
        boolean alreadyPaid = false;
        String customerName = "";
        String serviceName = "";
        String referenceNumber = "";

        // Check if payment has already been processed for this ticket
        for (Object[] r : records) {
            if (r != null && ticket.equals(String.valueOf(r[0]))) {
                String prev = String.valueOf(r[5]); // Payment is index 5
                if ("Paid".equalsIgnoreCase(prev)) {
                    alreadyPaid = true;
                    break;
                }
                if (!"Paid".equalsIgnoreCase(prev)) {
                    incrementCounter = true;
                }
                r[5] = "Paid";
                // Generate a new unique reference number for this payment
                referenceNumber = generateReference();
                r[1] = referenceNumber; // Store in the original reference field
                foundInRecords = true;
                customerName = String.valueOf(r[2]);
                serviceName = String.valueOf(r[3]);
                break;
            }
        }

        // If already paid, don't process again
        if (alreadyPaid) {
            return;
        }

        if (incrementCounter) totalPaidCount++;

        if (foundInRecords) {
            try {
                // Check if payment already exists in database to prevent duplicates
                if (cephra.Database.CephraDB.isPaymentAlreadyProcessed(ticket)) {
                    try {
                        removeTicket(ticket);
                        triggerHardRefresh();
                        triggerPanelSwitchRefresh();
                    } catch (Throwable ignore) {}
                    return;
                }
                
                // Calculate charging details
                BatteryInfo batteryInfo = getTicketBatteryInfo(ticket);
                int initialBatteryLevel = batteryInfo != null ? batteryInfo.initialPercent : 20;
                int chargingTimeMinutes = computeEstimatedMinutes(ticket);
                double totalAmount = computeAmountDue(ticket);
                
                // Use a single database transaction to ensure consistency - SKIP wallet processing
                boolean dbSuccess = cephra.Database.CephraDB.processPaymentTransactionSkipWallet(
                    ticket, customerName, serviceName, initialBatteryLevel, 
                    chargingTimeMinutes, totalAmount, paymentMethod, referenceNumber
                );
                
                if (dbSuccess) {
                    System.out.println("QueueBridge: Online payment processed successfully for ticket " + ticket);
                    
                    // Refresh history table to show the new completed ticket
                    try {
                        cephra.Admin.Utilities.HistoryBridge.refreshHistoryTable();
                        System.out.println("QueueBridge: History table refreshed successfully");
                    } catch (Exception e) {
                        System.err.println("QueueBridge: Error refreshing history table: " + e.getMessage());
                        // Don't fail the payment if history refresh fails
                    }
                    
                    // Add a longer delay to ensure database operations complete before UI updates
                    javax.swing.Timer refreshTimer = new javax.swing.Timer(200, event -> {
                        try {
                            // Remove ticket from queue
                            removeTicket(ticket);
                            
                            // Try to refresh admin UI if available, but don't fail if admin is not open
                            try {
                                triggerHardRefresh();
                            } catch (Exception e) {
                                System.out.println("QueueBridge: Admin UI refresh failed (admin may not be open): " + e.getMessage());
                            }
                            
                            try {
                                triggerPanelSwitchRefresh();
                            } catch (Exception e) {
                                System.out.println("QueueBridge: Panel switch refresh failed (admin may not be open): " + e.getMessage());
                            }
                        } catch (Throwable t) {
                            System.err.println("QueueBridge: Error in refresh timer: " + t.getMessage());
                        }
                    });
                    refreshTimer.setRepeats(false);
                    refreshTimer.start();
                } else {
                    System.err.println("QueueBridge: Failed to process payment transaction for ticket " + ticket);
                    // Revert the payment status in the records array
                    for (Object[] r : records) {
                        if (r != null && ticket.equals(String.valueOf(r[0]))) {
                            r[5] = "Pending"; // Revert payment status
                            r[1] = ""; // Clear reference number
                            break;
                        }
                    }
                }
                
            } catch (Throwable t) {
                System.err.println("QueueBridge: Error processing payment completion: " + t.getMessage());
                t.printStackTrace();
                
                // Revert any changes made to the records array
                for (Object[] r : records) {
                    if (r != null && ticket.equals(String.valueOf(r[0]))) {
                        r[5] = "Pending"; // Revert payment status
                        r[1] = ""; // Clear reference number
                        break;
                    }
                }
            }
        }
    }
    
    private static String generateReference() {
        // Generate 8-digit number (10000000 to 99999999)
        int number = 10000000 + new Random().nextInt(90000000);
        return String.valueOf(number);
    }
    
    
    /**
     * Triggers a hard refresh by finding the Queue panel and calling its hard refresh method
     */
    private static void triggerHardRefresh() {
        SwingUtilities.invokeLater(() -> {
            try {
                // Find the Queue panel in the current window
                java.awt.Window[] windows = java.awt.Window.getWindows();
                boolean adminFound = false;
                
                for (java.awt.Window window : windows) {
                    if (window instanceof cephra.Frame.Admin) {
                        adminFound = true;
                        cephra.Frame.Admin adminFrame = (cephra.Frame.Admin) window;
                        
                        // Look for Queue panel in the admin frame
                        cephra.Admin.Queue queuePanel = findQueuePanel(adminFrame);
                        if (queuePanel != null) {
                            // Find and refresh any tabbed panes that might contain the queue
                            refreshTabbedPanes(adminFrame);
                            
                            // Trigger hard refresh
                            queuePanel.hardRefreshTable();
                            break;
                        } else {
                            System.out.println("QueueBridge: Admin frame found but Queue panel not found");
                        }
                    }
                }
                
                if (!adminFound) {
                    System.out.println("QueueBridge: No Admin frame found - hard refresh skipped");
                }
            } catch (Exception e) {
                System.err.println("QueueBridge: Error triggering hard refresh: " + e.getMessage());
            }
        });
    }
    
    /**
     * Triggers a panel switch refresh that mimics switching panels and coming back
     */
    private static void triggerPanelSwitchRefresh() {
        SwingUtilities.invokeLater(() -> {
            try {
                // Find the Admin frame
                java.awt.Window[] windows = java.awt.Window.getWindows();
                boolean adminFound = false;
                
                for (java.awt.Window window : windows) {
                    if (window instanceof cephra.Frame.Admin) {
                        adminFound = true;
                        cephra.Frame.Admin adminFrame = (cephra.Frame.Admin) window;
                        
                        // Look for Queue panel in the admin frame
                        cephra.Admin.Queue queuePanel = findQueuePanel(adminFrame);
                        if (queuePanel != null) {
                            // Find and refresh any tabbed panes that might contain the queue
                            refreshTabbedPanes(adminFrame);
                            
                            // Mimic what happens when switching panels: revalidate and repaint
                            adminFrame.revalidate();
                            adminFrame.repaint();
                            
                            // Also force the queue panel to refresh
                            queuePanel.revalidate();
                            queuePanel.repaint();
                            
                            // Force table refresh
                            queuePanel.hardRefreshTable();
                            
                            break;
                        } else {
                            System.out.println("QueueBridge: Admin frame found but Queue panel not found for panel switch refresh");
                        }
                    }
                }
                
                if (!adminFound) {
                    System.out.println("QueueBridge: No Admin frame found - panel switch refresh skipped");
                }
            } catch (Exception e) {
                System.err.println("QueueBridge: Error triggering panel switch refresh: " + e.getMessage());
            }
        });
    }
    
    /**
     * Helper method to refresh any tabbed panes in the container hierarchy
     */
    private static void refreshTabbedPanes(java.awt.Container container) {
        if (container instanceof javax.swing.JTabbedPane) {
            javax.swing.JTabbedPane tabbedPane = (javax.swing.JTabbedPane) container;
            tabbedPane.revalidate();
            tabbedPane.repaint();
        }
        
        for (java.awt.Component component : container.getComponents()) {
            if (component instanceof java.awt.Container) {
                refreshTabbedPanes((java.awt.Container) component);
            }
        }
    }
    
    /**
     * Helper method to find the Queue panel in the admin frame
     */
    private static cephra.Admin.Queue findQueuePanel(java.awt.Container container) {
        if (container instanceof cephra.Admin.Queue) {
            return (cephra.Admin.Queue) container;
        }
        
        for (java.awt.Component component : container.getComponents()) {
            if (component instanceof java.awt.Container) {
                cephra.Admin.Queue found = findQueuePanel((java.awt.Container) component);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
    
    /** Get ticket service name (e.g., Fast Charging / Normal Charging) */
    public static String getTicketService(String ticket) {
        if (ticket == null) return null;
        for (Object[] record : records) {
            if (record != null && ticket.equals(String.valueOf(record[0]))) {
                return String.valueOf(record[3]); // service at index 3
            }
        }
        return null;
    }

    /** Compute estimated minutes to full using stored ticket battery and service */
    public static int computeEstimatedMinutes(String ticket) {
        BatteryInfo info = getTicketBatteryInfo(ticket);
        if (info == null) {
            // fallback: try reconstruct from customer
            String customer = getTicketCustomer(ticket);
            if (customer != null) {
                int userBatteryLevel = cephra.Database.CephraDB.getUserBatteryLevel(customer);
                info = new BatteryInfo(userBatteryLevel, 40.0);
            } else {
                info = new BatteryInfo(18, 40.0);
            }
        }
        String service = getTicketService(ticket);
        return computeEstimatedMinutes(info.initialPercent, service);
    }

    /** Compute estimated minutes from start percent and service name */
    public static int computeEstimatedMinutes(int startPercent, String serviceName) {
        int clamped = Math.max(0, Math.min(100, startPercent));
        int needed = 100 - clamped;
        double minsPerPercent = getMinsPerPercentForService(serviceName);
        return (int)Math.round(needed * minsPerPercent);
    }

    /** Helper: get minutes per percent based on service */
    public static double getMinsPerPercentForService(String serviceName) {
        if (serviceName != null && serviceName.toLowerCase().contains("fast")) {
            return MINS_PER_PERCENT_FAST;
        }
        return MINS_PER_PERCENT_NORMAL;
    }

    // Charging speed settings API
    public static void setMinsPerPercentFast(double value) { if (value > 0) MINS_PER_PERCENT_FAST = value; }
    public static double getMinsPerPercentFast() { return MINS_PER_PERCENT_FAST; }
    public static void setMinsPerPercentNormal(double value) { if (value > 0) MINS_PER_PERCENT_NORMAL = value; }
    public static double getMinsPerPercentNormal() { return MINS_PER_PERCENT_NORMAL; }
    
    // Billing settings API (for Dashboard/Admin to control)
    public static void setRatePerKWh(double rate) {
        if (rate > 0) {
            RATE_PER_KWH = rate;
        }
    }

    public static double getRatePerKWh() {
        return RATE_PER_KWH;
    }

    public static void setMinimumFee(double minFee) {
        if (minFee >= 0) {
            MINIMUM_FEE = minFee;
        }
    }

    public static double getMinimumFee() {
        return MINIMUM_FEE;
    }
    
    public static void setFastMultiplier(double multiplier) {
        if (multiplier >= 1.0) {
            FAST_MULTIPLIER = multiplier;
        }
    }
    
    public static double getFastMultiplier() {
        return FAST_MULTIPLIER;
    }
    
    // Load settings from database
    private static void loadSettingsFromDatabase() {
        try {
            // Load minimum fee from database
            String minFeeStr = cephra.Database.CephraDB.getSystemSetting("minimum_fee");
            if (minFeeStr != null && !minFeeStr.trim().isEmpty()) {
                MINIMUM_FEE = Double.parseDouble(minFeeStr);
            } else {
                // Set default if not found in database
                cephra.Database.CephraDB.updateSystemSetting("minimum_fee", String.valueOf(MINIMUM_FEE));
            }
            
            // Load rate per kWh from database
            String rateStr = cephra.Database.CephraDB.getSystemSetting("rate_per_kwh");
            if (rateStr != null && !rateStr.trim().isEmpty()) {
                RATE_PER_KWH = Double.parseDouble(rateStr);
            } else {
                // Set default if not found in database
                cephra.Database.CephraDB.updateSystemSetting("rate_per_kwh", String.valueOf(RATE_PER_KWH));
            }
            
            // Load fast multiplier from database
            String multiplierStr = cephra.Database.CephraDB.getSystemSetting("fast_multiplier");
            if (multiplierStr != null && !multiplierStr.trim().isEmpty()) {
                FAST_MULTIPLIER = Double.parseDouble(multiplierStr);
            } else {
                // Set default if not found in database
                cephra.Database.CephraDB.updateSystemSetting("fast_multiplier", String.valueOf(FAST_MULTIPLIER));
            }
            
        } catch (Exception e) {
            System.err.println("QueueBridge: Error loading settings from database: " + e.getMessage());
            // Keep default values if there's an error
        }
    }
    


    /** Remove a ticket from records and table */
    public static void removeTicket(final String ticket) {
        if (ticket == null || ticket.trim().isEmpty()) {
            System.err.println("QueueBridge: Cannot remove null or empty ticket");
            return;
        }

        // Check if ticket is already processed (moved to history)
        // If it's already processed, just remove from UI without database operation
        boolean isAlreadyProcessed = isTicketInHistory(ticket);
        
        if (!isAlreadyProcessed) {
            try {
                // Remove from database only if not already processed
                boolean dbRemoved = cephra.Database.CephraDB.removeQueueTicket(ticket);
                if (!dbRemoved) {
                    System.err.println("QueueBridge: Failed to remove ticket " + ticket + " from database");
                }
            } catch (Exception e) {
                System.err.println("QueueBridge: Error removing ticket from database: " + e.getMessage());
            }
        } else {
        }

        // Remove from memory records
        try {
            for (int i = records.size() - 1; i >= 0; i--) {
                Object[] r = records.get(i);
                if (r != null && ticket.equals(String.valueOf(r[0]))) {
                    records.remove(i);
                    // Also remove battery info
                    ticketBattery.remove(ticket);
                    break;
                }
            }
        } catch (Exception e) {
            System.err.println("QueueBridge: Error removing ticket from memory records: " + e.getMessage());
        }

        // Remove from table model
        if (model != null) {
            try {
                SwingUtilities.invokeLater(() -> {
                    try {
                        int rowCount = model.getRowCount();
                        for (int i = rowCount - 1; i >= 0; i--) {
                            if (i < model.getRowCount()) { // Double-check row still exists
                                Object v = model.getValueAt(i, 0);
                                if (v != null && ticket.equals(String.valueOf(v))) {
                                    model.removeRow(i);
                                    
                                    // Force table model to fire change events
                                    model.fireTableDataChanged();
                                    break;
                                }
                            }
                        }
                    } catch (Exception e) {
                        System.err.println("QueueBridge: Error removing ticket from table model: " + e.getMessage());
                    }
                });
            } catch (Exception e) {
                System.err.println("QueueBridge: Error scheduling table model update: " + e.getMessage());
            }
        }
    }
    
    /** Check if ticket is already in charging history (already processed) */
    private static boolean isTicketInHistory(String ticket) {
        try {
            // Check if ticket exists in charging_history table
            return cephra.Database.CephraDB.isTicketInChargingHistory(ticket);
        } catch (Exception e) {
            System.err.println("QueueBridge: Error checking if ticket is in history: " + e.getMessage());
            return false;
        }
    }
    
    /** 
     * Validates if a ticket is ready for payment
     * @param ticketId the ticket ID to validate
     * @return true if ticket is valid for payment, false otherwise
     */
    public static boolean isTicketValidForPayment(String ticketId) {
        if (ticketId == null || ticketId.trim().isEmpty()) {
            return false;
        }
        
        try {
            if (model != null) {
                for (int i = 0; i < model.getRowCount(); i++) {
                    Object ticketValue = model.getValueAt(i, 0);
                    if (ticketId.equals(String.valueOf(ticketValue))) {
                        String status = String.valueOf(model.getValueAt(i, 3));
                        String payment = String.valueOf(model.getValueAt(i, 4));
                        
                        // Ticket is ready for payment when charging is Complete/Completed/Charging and payment is still Pending
                        // PayPop can be shown for both "Charging" and "Complete" statuses, so accept both
                        // If payment is already Paid, don't allow payment again
                        boolean chargingReady = "Complete".equalsIgnoreCase(status) || "Completed".equalsIgnoreCase(status) || "Charging".equalsIgnoreCase(status);
                        boolean paymentPending = "Pending".equalsIgnoreCase(payment);
                        boolean alreadyPaid = "Paid".equalsIgnoreCase(payment);
                        
                        
                        if (chargingReady && paymentPending) {
                            return true; // Ready for payment
                        } else if (chargingReady && alreadyPaid) {
                            return false; // Already paid, don't allow payment again
                        } else {
                            return false; // Not ready for payment
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("QueueBridge: Error validating ticket for payment: " + e.getMessage());
            // If validation fails, allow payment to proceed
            return true;
        }
        
        return false;
    }
    
}
