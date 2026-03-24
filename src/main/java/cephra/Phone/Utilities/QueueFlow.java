package cephra.Phone.Utilities;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

public final class QueueFlow {

    public static final class Entry implements Comparable<Entry> {
        public final String ticketId;
        public final String customerName;
        public final String serviceName;
        public final String status;
        public final String payment;
        public final String action;
        public final int initialBatteryPercent;
        public final double batteryCapacityKWh;
        // Insertion order — used as tiebreaker so FIFO is preserved within same priority
        final long insertionOrder;

        public Entry(String ticketId, String customerName, String serviceName, String status, String payment, String action, int initialBatteryPercent, double batteryCapacityKWh) {
            this.ticketId = ticketId;
            this.customerName = customerName;
            this.serviceName = serviceName;
            this.status = status;
            this.payment = payment;
            this.action = action;
            this.initialBatteryPercent = initialBatteryPercent;
            this.batteryCapacityKWh = batteryCapacityKWh;
            this.insertionOrder = System.nanoTime();
        }

        /** Priority: battery < 20% (FCHP/NCHP) sorts before normal tickets. */
        private int priorityRank() {
            return (initialBatteryPercent < 20) ? 0 : 1;
        }

        @Override
        public int compareTo(Entry other) {
            int cmp = Integer.compare(this.priorityRank(), other.priorityRank());
            if (cmp != 0) return cmp;
            return Long.compare(this.insertionOrder, other.insertionOrder); // FIFO tiebreak
        }
    }

    // PriorityQueue: low-battery tickets (< 20%) sort ahead of normal tickets.
    // Within the same priority, insertion order (FIFO) is preserved.
    private static final PriorityQueue<Entry> queue = new PriorityQueue<>();

    private static String currentTicketId = "";
    private static String currentServiceName = "";
    private static int nextFastNumber = 1;   // FCH001, FCH002, ...
    private static int nextNormalNumber = 1; // NCH001, NCH002, ...

    private QueueFlow() {}
    
    // Initialize counters for ticket generation
    static {
        // Reset the current service and ticket for normal operation
        currentTicketId = "";
        currentServiceName = "";
        
        // Initialize counters from existing database tickets
        initializeCountersFromDatabase();
    }
    
    private static void initializeCountersFromDatabase() {
        try {
            int maxFastNumber = 0;
            int maxNormalNumber = 0;
            
            // Get all existing queue tickets from database
            List<Object[]> existingTickets = cephra.Database.CephraDB.getAllQueueTickets();
            for (Object[] ticket : existingTickets) {
                String ticketId = String.valueOf(ticket[0]); // ticket_id is at index 0
                if (ticketId.startsWith("FCH")) {
                    int number = extractNumber(ticketId);
                    maxFastNumber = Math.max(maxFastNumber, number);
                } else if (ticketId.startsWith("NCH")) {
                    int number = extractNumber(ticketId);
                    maxNormalNumber = Math.max(maxNormalNumber, number);
                }
            }
            
            // Also check charging history for completed tickets
            List<Object[]> completedTickets = cephra.Database.CephraDB.getAllChargingHistory();
            for (Object[] ticket : completedTickets) {
                String ticketId = String.valueOf(ticket[0]); // ticket_id is at index 0
                if (ticketId.startsWith("FCH")) {
                    int number = extractNumber(ticketId);
                    maxFastNumber = Math.max(maxFastNumber, number);
                } else if (ticketId.startsWith("NCH")) {
                    int number = extractNumber(ticketId);
                    maxNormalNumber = Math.max(maxNormalNumber, number);
                }
            }
            
            // Set counters to next available number
            nextFastNumber = maxFastNumber + 1;
            nextNormalNumber = maxNormalNumber + 1;
            
        } catch (Exception e) {
            System.err.println("Error initializing counters from database: " + e.getMessage());
            // Keep default values if database access fails
        }
    }

    public static void setCurrent(String ticketId, String serviceName) {
        currentTicketId = ticketId;
        currentServiceName = serviceName;
    }

    public static void setCurrentServiceOnly(String serviceName) {
        currentTicketId = "";
        currentServiceName = serviceName;
    }

    public static String getCurrentTicketId() {
        // Validate any in-memory ticket against database status; clear if completed/cancelled
        try {
            String currentUserForValidation = cephra.Database.CephraDB.getCurrentUsername();
            if (currentUserForValidation != null && !currentUserForValidation.trim().isEmpty()) {
                String latestStatus = cephra.Database.CephraDB.getUserCurrentTicketStatus(currentUserForValidation);
                if (latestStatus != null) {
                    String s = latestStatus.trim().toLowerCase();
                    boolean isOpen = ("pending".equals(s) || "waiting".equals(s) || "charging".equals(s));
                    if (!isOpen) {
                        currentTicketId = "";
                    }
                }
            }
        } catch (Exception ignore) {}

        if (currentTicketId != null && !currentTicketId.trim().isEmpty()) {
            return currentTicketId;
        }

        // Otherwise, try to get from database fresh
        try {
            String currentUser = cephra.Database.CephraDB.getCurrentUsername();
            if (currentUser != null && !currentUser.trim().isEmpty()) {
                // Check active_tickets table first
                String activeTicket = cephra.Database.CephraDB.getActiveTicket(currentUser);
                if (activeTicket != null && !activeTicket.trim().isEmpty()) {
                    // Update the in-memory current ticket ID to match database
                    currentTicketId = activeTicket;
                    return activeTicket;
                }
                
                // If no active ticket, check queue_tickets table for pending tickets
                String queueTicket = cephra.Database.CephraDB.getQueueTicketForUser(currentUser);
                if (queueTicket != null && !queueTicket.trim().isEmpty()) {
                    // Update the in-memory current ticket ID to match queue ticket
                    currentTicketId = queueTicket;
                    return queueTicket;
                }
                // No active/pending, ensure memory is cleared
                currentTicketId = "";
            }
        } catch (Exception e) {
            System.err.println("Error getting current ticket ID from database: " + e.getMessage());
        }
        
        return currentTicketId; // Return empty string if nothing found
    }

    public static String getCurrentServiceName() {
        return currentServiceName;
    }

    public static List<Entry> getEntries() {
        // Return a sorted snapshot — callers get priority order without touching the queue directly
        List<Entry> sorted = new ArrayList<>(queue);
        sorted.sort(Comparator.naturalOrder());
        return sorted;
    }

    // Returns the next ticket id that would be used for the given service, without mutating counters
    public static String previewNextTicketIdForService(String serviceName) {
        String s = serviceName == null ? "" : serviceName.toLowerCase();
        if (s.contains("fast")) {
            return formatTicket("FCH", nextFastNumber);
        }
        if (s.contains("normal")) {
            return formatTicket("NCH", nextNormalNumber);
        }
        return "";
    }
    
    // Returns the next priority ticket id that would be used for the given service and battery level
    public static String previewNextPriorityTicketIdForService(String serviceName, int batteryLevel) {
        String s = serviceName == null ? "" : serviceName.toLowerCase();
        boolean isPriority = (batteryLevel < 20);
        
        if (s.contains("fast")) {
            String prefix = isPriority ? "FCHP" : "FCH";
            return formatTicket(prefix, nextFastNumber);
        }
        if (s.contains("normal")) {
            String prefix = isPriority ? "NCHP" : "NCH";
            return formatTicket(prefix, nextNormalNumber);
        }
        return "";
    }

    // Returns the next ticket id for the currently selected service, without mutating counters
    public static String previewNextTicketId() {
        return previewNextTicketIdForService(currentServiceName);
    }
    
    // Method to refresh counters from database (useful after application restart or manual refresh)
    public static void refreshCountersFromDatabase() {
        initializeCountersFromDatabase();
    }
    
    // Method to get current counter values for debugging
    public static String getCurrentCounterStatus() {
        return "Fast: " + nextFastNumber + ", Normal: " + nextNormalNumber;
    }
    
    public static void updatePaymentStatus(String ticketId, String paymentStatus) {
        // Rebuild the queue replacing the matching entry with updated payment status
        List<Entry> snapshot = new ArrayList<>(queue);
        queue.clear();
        for (Entry entry : snapshot) {
            if (entry.ticketId.equals(ticketId)) {
                queue.add(new Entry(
                    entry.ticketId, entry.customerName, entry.serviceName,
                    entry.status, paymentStatus, entry.action,
                    entry.initialBatteryPercent, entry.batteryCapacityKWh
                ));
                System.out.println("QueueFlow: Updated payment status for ticket " + ticketId + " to " + paymentStatus);
            } else {
                queue.add(entry);
            }
        }
    }
    
    public static boolean hasActiveTicket() {
        // Validate in-memory ticket with DB status; treat completed/cancelled as not active UNLESS payment is pending
        try {
            String currentUser = cephra.Database.CephraDB.getCurrentUsername();
            if (currentUser != null && !currentUser.trim().isEmpty()) {
                String latestStatus = cephra.Database.CephraDB.getUserCurrentTicketStatus(currentUser);
                if (latestStatus != null) {
                    String s = latestStatus.trim().toLowerCase();
                    // Include "complete" status if payment is still pending
                    boolean isOpen = ("pending".equals(s) || "waiting".equals(s) || "charging".equals(s));
                    boolean isCompleteWithPendingPayment = "complete".equals(s);
                    
                    if (isCompleteWithPendingPayment) {
                        // Check if payment is still pending for this completed ticket
                        String paymentStatus = cephra.Database.CephraDB.getUserCurrentTicketPaymentStatus(currentUser);
                        if ("pending".equalsIgnoreCase(paymentStatus) || paymentStatus == null || paymentStatus.trim().isEmpty()) {
                            isOpen = true; // Treat as active because payment is needed
                        }
                    }
                    
                    if (!isOpen) {
                        currentTicketId = "";
                    }
                }
            }
        } catch (Exception ignore) {}

        if (currentTicketId != null && !currentTicketId.trim().isEmpty()) {
            return true;
        }

        // If no in-memory ticket, check database for current user's active/pending ticket
        try {
            String currentUser = cephra.Database.CephraDB.getCurrentUsername();
            if (currentUser != null && !currentUser.trim().isEmpty()) {
                // Check active_tickets table first
                String activeTicket = cephra.Database.CephraDB.getActiveTicket(currentUser);
                if (activeTicket != null && !activeTicket.trim().isEmpty()) {
                    // Update the in-memory current ticket ID to match database
                    currentTicketId = activeTicket;
                    return true;
                }
                
                // If no active ticket, check queue_tickets table for pending tickets
                String queueTicket = cephra.Database.CephraDB.getQueueTicketForUser(currentUser);
                if (queueTicket != null && !queueTicket.trim().isEmpty()) {
                    // Update the in-memory current ticket ID to match queue ticket
                    currentTicketId = queueTicket;
                    return true;
                }
                // No active/pending, ensure memory is cleared
                currentTicketId = "";
            }
        } catch (Exception e) {
            System.err.println("Error checking active ticket in database: " + e.getMessage());
        }
        
        return false;
    }
    
    public static Entry getCurrentTicketEntry() {
        if (!hasActiveTicket()) {
            return null;
        }
        
        for (Entry entry : queue) {
            if (entry.ticketId.equals(currentTicketId)) {
                return entry;
            }
        }
        return null;
    }

    public static void addCurrentToAdminAndStore(String customerName) {
        String service = currentServiceName;
        // Get battery level first to determine priority
        final int initialBatteryPercent = cephra.Database.CephraDB.getUserBatteryLevel(customerName);
        
        // Capacity is handled later by admin proceed; no need to check here
        
        // If no ticket was pre-assigned, generate the next ID for the service with priority
        if (currentTicketId == null || currentTicketId.length() == 0) {
            currentTicketId = generatePriorityTicketIdForService(service, initialBatteryPercent);
        } else {
            // If a ticket was pre-set (e.g., FCH001), update counters so subsequent tickets increment
            updateCountersFromTicket(currentTicketId);
        }
        final String ticket = currentTicketId;
        // Determine initial status on creation from phone:
        // - Priority (<20% battery): Waiting
        // - Otherwise: Pending
        final String status = (initialBatteryPercent < 20) ? "Waiting" : "Pending";
        final String payment = "";
        final String action = "";
        final double batteryCapacityKWh = 40.0; // 40kWh capacity

        // Store in priority queue — low-battery entries will sort ahead automatically
        queue.add(new Entry(ticket, customerName, service, status, payment, action, initialBatteryPercent, batteryCapacityKWh));

        // Reflect into Admin table if registered
        try {
            cephra.Admin.Utilities.QueueBridge.addTicket(ticket, customerName, service, status, payment, action);
            cephra.Admin.Utilities.QueueBridge.setTicketBatteryInfo(ticket, initialBatteryPercent, batteryCapacityKWh);
        } catch (Throwable t) {
            // ignore if admin queue not ready
        }

        // Prepare next number now that the current one has been used
        bumpCounterForService(service, ticket);
    }

    
    // Generate priority ticket ID based on battery level
    public static String generatePriorityTicketIdForService(String serviceName, int batteryLevel) {
        if (serviceName == null) {
            serviceName = "";
        }
        
        // Determine if this should be a priority ticket
        boolean isPriority = (batteryLevel < 20);
        
        if (serviceName.toLowerCase().contains("fast")) {
            String prefix = isPriority ? "FCHP" : "FCH";
            String ticket = formatTicket(prefix, nextFastNumber);
            return ticket;
        }
        if (serviceName.toLowerCase().contains("normal")) {
            String prefix = isPriority ? "NCHP" : "NCH";
            String ticket = formatTicket(prefix, nextNormalNumber);
            return ticket;
        }
        // default fall-back
        return formatTicket("GEN", 1);
    }

    private static void bumpCounterForService(String serviceName, String ticket) {
        if (serviceName == null) {
            return;
        }
        if (serviceName.toLowerCase().contains("fast")) {
            nextFastNumber = Math.max(nextFastNumber + 1, extractNumber(ticket) + 1);
        } else if (serviceName.toLowerCase().contains("normal")) {
            nextNormalNumber = Math.max(nextNormalNumber + 1, extractNumber(ticket) + 1);
        }
    }

    private static void updateCountersFromTicket(String ticket) {
        String upper = ticket == null ? "" : ticket.toUpperCase();
        int num = extractNumber(upper);
        if (upper.startsWith("FCHP")) {
            // Priority fast ticket - use FCH counter
            nextFastNumber = Math.max(nextFastNumber, num + 1);
        } else if (upper.startsWith("FCH")) {
            nextFastNumber = Math.max(nextFastNumber, num + 1);
        } else if (upper.startsWith("NCHP")) {
            // Priority normal ticket - use NCH counter
            nextNormalNumber = Math.max(nextNormalNumber, num + 1);
        } else if (upper.startsWith("NCH")) {
            nextNormalNumber = Math.max(nextNormalNumber, num + 1);
        }
    }

    private static int extractNumber(String ticket) {
        if (ticket == null) return 0;
        int n = 0;
        for (int i = 0; i < ticket.length(); i++) {
            char c = ticket.charAt(i);
            if (c >= '0' && c <= '9') {
                n = n * 10 + (c - '0');
            }
        }
        return n;
    }

    private static String formatTicket(String prefix, int number) {
        String numStr = String.format("%03d", number);
        return prefix + numStr;
    }
}
