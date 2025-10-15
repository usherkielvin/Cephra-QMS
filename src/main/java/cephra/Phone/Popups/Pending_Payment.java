package cephra.Phone.Popups;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class Pending_Payment extends javax.swing.JPanel {
    
    // Static state management to prevent multiple instances
    private static Pending_Payment currentInstance = null;
    private static String currentTicketId = null;
    private static boolean isShowing = false;
    private static javax.swing.JPanel currentOverlay = null;
    
    // Pending_Payment persistence state for top-up flow
    private static boolean isPendingTopUpReturn = false;
    private static String pendingTicketId = null;
    private static String pendingCustomerUsername = null;
    private static double lastPaymentAmount = 0.0;
    
    // Popup dimensions (centered in phone frame)
    private static final int POPUP_WIDTH = 270;
    private static final int POPUP_HEIGHT = 280;
    private static final int PHONE_WIDTH = 350; // fallback if frame size not yet realized
    private static final int PHONE_HEIGHT = 750; // fallback if frame size not yet realized
    
    // Method to disable all components recursively
    private static void disableAllComponents(Component component) {
        component.setEnabled(false);
        if (component instanceof Container) {
            Container container = (Container) component;
            for (Component child : container.getComponents()) {
                disableAllComponents(child);
            }
        }
    }
    
    // Method to enable all components recursively
    private static void enableAllComponents(Component component) {
        component.setEnabled(true);
        if (component instanceof Container) {
            Container container = (Container) component;
            for (Component child : container.getComponents()) {
                enableAllComponents(child);
            }
        }
    }
    
    /**
     * Checks if Pending_Payment is currently showing for a specific ticket
     * @param ticketId the ticket ID to check
     * @return true if Pending_Payment is showing for this ticket
     */
    public static boolean isShowingForTicket(String ticketId) {
        return isShowing && ticketId != null && ticketId.equals(currentTicketId);
    }
    
    /**
     * Checks if there's a Pending_Payment pending to be restored after top-up
     * @return true if Pending_Payment should be restored
     */
    public static boolean hasPendingPayPop() {
        return isPendingTopUpReturn && pendingTicketId != null && pendingCustomerUsername != null;
    }
    
    /**
     * Gets the current ticket ID from Pending_Payment
     * @return the current ticket ID, or null if not available
     */
    public static String getCurrentTicketId() {
        return currentTicketId;
    }
    
    /**
     * Gets the payment amount from Pending_Payment's last payment
     * @return the payment amount, or 0.0 if not available
     */
    public static double getPaymentAmount() {
        return lastPaymentAmount;
    }
    
    /**
     * Restores Pending_Payment after returning from top-up if there was a pending payment
     * @return true if Pending_Payment was restored successfully
     */
    public static boolean restorePayPopAfterTopUp() {
        if (!hasPendingPayPop()) {
            return false;
        }
        
        
        // Store the pending values
        String ticketId = pendingTicketId;
        String username = pendingCustomerUsername;
        
        // Clear the pending state
        clearPendingState();
        
        // Show the Pending_Payment again
        return showPayPop(ticketId, username);
    }
    
    /**
     * Clears the pending Pending_Payment state
     */
    private static void clearPendingState() {
        isPendingTopUpReturn = false;
        pendingTicketId = null;
        pendingCustomerUsername = null;
    }
    
    /**
     * Validates if Pending_Payment can be shown for the given ticket and user
     * @param ticketId the ticket ID
     * @param customerUsername the customer username
     * @return true if Pending_Payment can be shown
     */
    public static boolean canShowPayPop(String ticketId, String customerUsername) {
        
        // Allow reappearing - if already showing, hide first then show again
        if (isShowing) {
            hidePayPop();
        }
        
        // Validate user is logged in
        if (!cephra.Database.CephraDB.isUserLoggedIn()) {
            return false;
        }
        
        // Get and validate current user
        String currentUser = cephra.Database.CephraDB.getCurrentUsername();
        if (currentUser == null || currentUser.trim().isEmpty()) {
            return false;
        }
        
        // Validate user matches ticket owner
        if (!currentUser.trim().equals(customerUsername.trim())) {
            return false;
        }
        
        return true;
    }
    
    /**
     * Shows Pending_Payment with validation
     * @param ticketId the ticket ID
     * @param customerUsername the customer username
     * @return true if Pending_Payment was shown successfully
     */
    public static boolean showPayPop(String ticketId, String customerUsername) {
        
        if (!canShowPayPop(ticketId, customerUsername)) {
            return false;
        }
        
        // Find Phone frame and show centered Pending_Payment
        Window[] windows = Window.getWindows();
        for (Window window : windows) {
            if (window instanceof cephra.Frame.Phone) {
                cephra.Frame.Phone phoneFrame = (cephra.Frame.Phone) window;
                showCenteredPayPop(phoneFrame, ticketId);
                return true;
            }
        }
        return false;
    }
    
    /**
     * Shows Pending_Payment centered on the Phone frame
     * @param phoneFrame the Phone frame to center on
     * @param ticketId the ticket ID
     */
    private static void showCenteredPayPop(cephra.Frame.Phone phoneFrame, String ticketId) {
        SwingUtilities.invokeLater(() -> {
            currentInstance = new Pending_Payment();
            currentTicketId = ticketId;
            isShowing = true;
            // Push ticket id to UI immediately (fallback to admin queue model if needed)
            try {
                String resolved = ticketId;
                if (resolved == null || resolved.trim().isEmpty()) {
                    String currentUser = cephra.Database.CephraDB.getCurrentUsername();
                    resolved = currentInstance.findLatestTicketForUserFromAdminModel(currentUser);
                }
                currentInstance.setTicketOnUi(resolved);
            } catch (Exception ignore) {}
            
            // Determine phone content size (fallback to constants if not realized yet)
            int containerW = phoneFrame.getContentPane() != null ? phoneFrame.getContentPane().getWidth() : 0;
            int containerH = phoneFrame.getContentPane() != null ? phoneFrame.getContentPane().getHeight() : 0;
            if (containerW <= 0) containerW = PHONE_WIDTH;
            if (containerH <= 0) containerH = PHONE_HEIGHT;
            
            // Disable the background panel so it's not touchable
            Component contentPane = phoneFrame.getContentPane();
            if (contentPane != null) {
                contentPane.setEnabled(false);
                // Disable all child components recursively
                disableAllComponents(contentPane);
            }
            
            // Create gray overlay background that blocks interaction
            currentOverlay = new javax.swing.JPanel() {
                @Override
                protected void paintComponent(java.awt.Graphics g) {
                    super.paintComponent(g);
                    g.setColor(new java.awt.Color(0, 0, 0, 150)); // Semi-transparent black overlay
                    g.fillRect(0, 0, getWidth(), getHeight());
                }
            };
            currentOverlay.setBounds(0, 0, containerW, containerH);
            currentOverlay.setOpaque(false);
            
            // Center the Pending_Payment on the phone frame
            int x = (containerW - POPUP_WIDTH) / 2;
            int y = (containerH - POPUP_HEIGHT) / 2;
            
            currentInstance.setBounds(x, y, POPUP_WIDTH, POPUP_HEIGHT);
            
            // Add overlay and popup to layered pane
            JLayeredPane layeredPane = phoneFrame.getRootPane().getLayeredPane();
            layeredPane.add(currentOverlay, JLayeredPane.MODAL_LAYER - 1); // Background overlay
            layeredPane.add(currentInstance, JLayeredPane.MODAL_LAYER); // Popup on top
            layeredPane.moveToFront(currentInstance);
            
            currentInstance.setVisible(true);
            phoneFrame.repaint();
        });
    }
    
    /**
     * Hides the Pending_Payment and cleans up resources
     */
    public static void hidePayPop() {
    if (currentInstance != null && isShowing) {
        // Capture a local reference to avoid race conditions
        Pending_Payment instance = currentInstance;

        SwingUtilities.invokeLater(() -> {
            // Remove popup
            if (instance.getParent() != null) {
                instance.getParent().remove(instance);
            }
            
            // Remove overlay if it exists
            if (currentOverlay != null && currentOverlay.getParent() != null) {
                currentOverlay.getParent().remove(currentOverlay);
                currentOverlay = null;
            }
            
            // Re-enable the background panel
            for (Window window : Window.getWindows()) {
                if (window instanceof cephra.Frame.Phone) {
                    cephra.Frame.Phone phoneFrame = (cephra.Frame.Phone) window;
                    Component contentPane = phoneFrame.getContentPane();
                    if (contentPane != null) {
                        contentPane.setEnabled(true);
                        // Re-enable all child components recursively
                        enableAllComponents(contentPane);
                    }
                    phoneFrame.repaint();
                    break;
                }
            }
            
            currentInstance = null;
            currentTicketId = null;
            isShowing = false;
        });
    }
}


    // Sets the ticket number on the UI label immediately (safe call)
    private void setTicketOnUi(String ticketId) {
        try {
            if (TICKETNUMBER != null && ticketId != null && !ticketId.trim().isEmpty()) {
                TICKETNUMBER.setText(ticketId);
                TICKETNUMBER.repaint();
            }
        } catch (Exception ignore) {}
    }

    // ensureTicketLabelVisible: removed; form manages visual properties

    // Read the latest ticket for the current user directly from Admin Queue table model
    private String findLatestTicketForUserFromAdminModel(String username) {
        try {
            if (username == null || username.trim().isEmpty()) return null;
            javax.swing.table.DefaultTableModel model = (javax.swing.table.DefaultTableModel)
                cephra.Admin.Utilities.QueueBridge.class.getDeclaredField("model").get(null);
            if (model == null) return null;

            final int colCount = model.getColumnCount();
            int ticketCol = 0; // usually 0
            int customerCol = Math.min(1, colCount - 1);
            int statusCol = Math.min(3, colCount - 1);
            int paymentCol = Math.min(4, colCount - 1);

            String fallback = null;
            for (int i = model.getRowCount() - 1; i >= 0; i--) { // scan latest first
                String customer = String.valueOf(model.getValueAt(i, customerCol));
                if (customer != null && username.equalsIgnoreCase(customer.trim())) {
                    String t = String.valueOf(model.getValueAt(i, ticketCol));
                    String status = String.valueOf(model.getValueAt(i, statusCol));
                    String payment = String.valueOf(model.getValueAt(i, paymentCol));
                    if ("Pending".equalsIgnoreCase(payment) || "Complete".equalsIgnoreCase(status)) {
                        return t;
                    }
                    if (fallback == null) fallback = t;
                }
            }
            return fallback;
        } catch (Throwable ignore) {
            return null;
        }
    }

    // Intro animation fields
    private Timer introTimer;
    private ImageIcon mainImageIcon;
    
    /**
     * Constructor for PayPop
     */
    public Pending_Payment() {
        // Load intro gif and main image
        // Initialize components but hide interactive content until GIF finishes
        initComponents();
        hideContent();
        loadImages();
        initializePayPop();
        startIntroAnimation();
    }
    
    /**
     * Loads the intro gif and main image icons
     */
    private void loadImages() {
        try {
            mainImageIcon = new ImageIcon(getClass().getResource("/cephra/Cephra Images/CASH.png"));
        } catch (Exception e) {
            System.err.println("Error loading images: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Initializes Pending_Payment components and data
     */
    private void initializePayPop() {
        // Match popup panel to background image and remove excess white by making it transparent
        setPreferredSize(new Dimension(POPUP_WIDTH, POPUP_HEIGHT));
        setSize(POPUP_WIDTH, POPUP_HEIGHT);
        setupCloseButton();
        
        // Update labels with actual ticket data after components are initialized
        SwingUtilities.invokeLater(this::updateTextWithAmount);
        
        // Set username if available (optional display label removed)
    }
    
    /**
     * Starts the intro animation sequence
     */
    private void startIntroAnimation() {
        if (bg == null) {
            // Fallback to main image if label not available
            showMainImage();
            return;
        }
        
        // Create a fresh GIF instance to reset animation
        ImageIcon freshGifIcon = null;
        try {
            freshGifIcon = new ImageIcon(getClass().getResource("/cephra/Cephra Images/inpaypop.gif"));
        } catch (Exception e) {
            System.err.println("Error loading fresh intro gif: " + e.getMessage());
            showMainImage();
            return;
        }
        
        bg.setIcon(freshGifIcon);

        // Set up timer to forcibly cut GIF and switch to main image after 200ms (0.2 seconds)
        introTimer = new Timer(200, new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                // Force stop GIF by clearing icon first, then setting main image
                bg.setIcon(null);
                SwingUtilities.invokeLater(() -> {
                    showMainImage();
                    showContent();
                });
                introTimer.stop();
            }
        });
        introTimer.setRepeats(false);
        introTimer.start();
    }
    
    /**
     * Shows the main image and ends intro animation
     */
    private void showMainImage() {
        if (bg != null && mainImageIcon != null) {
            bg.setIcon(mainImageIcon);
        }
        if (introTimer != null) {
            introTimer.stop();
        }
    }

    private void hideContent() {
        try {
            if (TICKETNUMBER != null) TICKETNUMBER.setVisible(false);
            if (TotalBill != null) TotalBill.setVisible(false);
            if (ChargingDue != null) ChargingDue.setVisible(false);
            if (kWh != null) kWh.setVisible(false);
            if (Cash != null) Cash.setVisible(false);
            if (payonline != null) payonline.setVisible(false);
        } catch (Exception ignore) {}
    }

    private void showContent() {
        try {
            if (TICKETNUMBER != null) TICKETNUMBER.setVisible(true);
            if (TotalBill != null) TotalBill.setVisible(true);
            if (ChargingDue != null) ChargingDue.setVisible(true);
            if (kWh != null) kWh.setVisible(true);
            if (Cash != null) Cash.setVisible(true);
            if (payonline != null) payonline.setVisible(true);
        } catch (Exception ignore) {}
    }

    // NetBeans form manages background order by default

    // NetBeans form manages component Z-order
    /**
     * Sets up label position to prevent NetBeans from changing it
     * CUSTOM CODE - DO NOT REMOVE - NetBeans will regenerate form code but this method should be preserved
     */
    // NetBeans form manages label positions
    /**
     * Sets up close button functionality (ESC key)
     */
    private void setupCloseButton() {
        setFocusable(true);
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent evt) {
                if (evt.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    hidePayPop();
                }
            }
        });
        
        // Request focus so key events work
        SwingUtilities.invokeLater(this::requestFocusInWindow);
    }

    /**
     * Updates Pending_Payment labels with current ticket data and amounts
     */
    private void updateTextWithAmount() {
        try {
            // Resolve ticket strictly from Admin Queue table if not provided
            String ticket = currentTicketId;
            if (ticket == null || ticket.isEmpty()) {
                String currentUser = cephra.Database.CephraDB.getCurrentUsername();
                ticket = findLatestTicketForUserFromAdminModel(currentUser);
            }
            if (ticket == null || ticket.isEmpty()) {
                ticket = cephra.Phone.Utilities.QueueFlow.getCurrentTicketId();
            }
            if (ticket == null || ticket.isEmpty()) {
                return;
            }
            
            // Calculate amounts using centralized QueueBridge methods
            double amount = cephra.Admin.Utilities.QueueBridge.computeAmountDue(ticket);
            
            // Calculate energy usage
            double usedKWh = calculateEnergyUsage(ticket);
            
            // Update UI labels
            updateLabels(ticket, amount, usedKWh);
            
        } catch (Exception e) {
            System.err.println("Error updating PayPop labels: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Calculates energy usage for the ticket
     * @param ticket the ticket ID
     * @return energy usage in kWh
     */
    private double calculateEnergyUsage(String ticket) {
        cephra.Admin.Utilities.QueueBridge.BatteryInfo batteryInfo = cephra.Admin.Utilities.QueueBridge.getTicketBatteryInfo(ticket);
        if (batteryInfo != null) {
            int start = batteryInfo.initialPercent;
            double cap = batteryInfo.capacityKWh;
            return (100.0 - start) / 100.0 * cap;
        }
        return 0.0;
    }
    
    /**
     * Updates all UI labels with ticket data
     * @param ticket the ticket ID
     * @param amount the total amount
     * @param usedKWh the energy usage
     */
    private void updateLabels(String ticket, double amount, double usedKWh) {
        if (TICKETNUMBER != null) {
            TICKETNUMBER.setText(ticket);
            TICKETNUMBER.repaint();
        }
        if (ChargingDue != null) {
            ChargingDue.setText(String.format("₱%.2f", amount));
        }
        if (kWh != null) {
            kWh.setText(String.format("%.1f kWh", usedKWh));
        }
        if (TotalBill != null) {
            TotalBill.setText(String.format("₱%.2f", amount));
        }
    }
    
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        TICKETNUMBER = new javax.swing.JLabel();
        ChargingDue = new javax.swing.JLabel();
        kWh = new javax.swing.JLabel();
        TotalBill = new javax.swing.JLabel();
        Cash = new javax.swing.JButton();
        payonline = new javax.swing.JButton();
        bg = new javax.swing.JLabel();

        setOpaque(false);
        setLayout(null);

        TICKETNUMBER.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        add(TICKETNUMBER);
        TICKETNUMBER.setBounds(150, 75, 90, 20);

        ChargingDue.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        add(ChargingDue);
        ChargingDue.setBounds(150, 100, 90, 20);

        kWh.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        add(kWh);
        kWh.setBounds(150, 120, 80, 20);

        TotalBill.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        add(TotalBill);
        TotalBill.setBounds(150, 165, 90, 20);

        Cash.setBorder(null);
        Cash.setContentAreaFilled(false);
        Cash.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                CashActionPerformed(evt);
            }
        });
        add(Cash);
        Cash.setBounds(10, 210, 120, 50);

        payonline.setBorder(null);
        payonline.setBorderPainted(false);
        payonline.setContentAreaFilled(false);
        payonline.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                payonlineActionPerformed(evt);
            }
        });
        add(payonline);
        payonline.setBounds(140, 210, 110, 50);

        bg.setIcon(new javax.swing.ImageIcon(getClass().getResource("/cephra/Cephra Images/CASH.png"))); // NOI18N
        add(bg);
        bg.setBounds(0, 0, 280, 280);
    }// </editor-fold>//GEN-END:initComponents

    /**
     * Handles cash payment action
     * @param evt the action event
     */
    private void CashActionPerformed(ActionEvent evt) {
        if (!validateUserLoggedIn()) {
            return;
        }
        
        try {
            // Get current ticket ID
            String currentTicket = currentTicketId;
            if (currentTicket == null || currentTicket.isEmpty()) {
                currentTicket = cephra.Phone.Utilities.QueueFlow.getCurrentTicketId();
            }
            
            if (currentTicket != null && !currentTicket.isEmpty()) {
                // Set payment method to Cash in the database
                boolean success = cephra.Database.CephraDB.updateQueueTicketPaymentMethod(currentTicket, "Cash");
                
                if (success) {
                    // Refresh admin table to show the updated payment status
                    try {
                        cephra.Admin.Utilities.QueueBridge.reloadFromDatabase();
                    } catch (Exception e) {
                        System.err.println("Error refreshing admin table after cash payment: " + e.getMessage());
                    }
                }
                
                // For cash payments, do NOT mark as paid automatically
                // Admin will manually mark as paid after receiving cash
            }
            
            // Clear any pending Pending_Payment state since payment is completed
            clearPendingState();
            
            // Hide Pending_Payment and navigate to Home
            hidePayPop();
            navigateToHome();
            
        } catch (Exception e) {
            System.err.println("Error processing cash payment: " + e.getMessage());
            e.printStackTrace();
            showErrorMessage("Failed to process cash payment.\nError: " + e.getMessage() + "\nPlease try again.");
        }
    }

    /**
     * Handles online payment action
     * @param evt the action event
     */
    private void payonlineActionPerformed(ActionEvent evt) {
        if (!validateUserLoggedIn()) {
            return;
        }
        
        SwingUtilities.invokeLater(() -> {
            boolean paymentSuccess = false;
            try {
                paymentSuccess = processOnlinePayment();
                // After successful wallet payment, ensure admin queue marks as paid and removes the ticket
                if (paymentSuccess) {
                    try {
                        String currentTicket = cephra.Phone.Utilities.QueueFlow.getCurrentTicketId();
                        if (currentTicket != null && !currentTicket.isEmpty()) {
                            // Set payment method to Online in the database
                            cephra.Database.CephraDB.updateQueueTicketPaymentMethod(currentTicket, "Online");
                            
                            // Process the complete payment transaction (history, ticket removal, etc.)
                            // but skip wallet payment since it's already been processed
                            cephra.Admin.Utilities.QueueBridge.markPaymentPaidOnlineSkipWallet(currentTicket);
                        }
                    } catch (Throwable ignore) {}
                }
            } catch (Exception e) {
                handlePaymentError(e);
            } finally {
                hidePayPop();
                // Only navigate to receipt if payment was successful
                if (paymentSuccess) {
                    navigateToReceipt();
                } else {
                    // If payment failed (e.g., insufficient balance), navigate back to home
                    navigateToHome();
                }
            }
        });
    }
    
    /**
     * Validates that user is logged in
     * @return true if user is logged in, false otherwise
     */
    private boolean validateUserLoggedIn() {
        if (!cephra.Database.CephraDB.isUserLoggedIn()) {
            System.err.println("Payment blocked: No user is logged in");
            hidePayPop();
            return false;
        }
        return true;
    }
    
    /**
     * Processes online payment
     * @return true if payment was successful, false otherwise
     * @throws Exception if payment processing fails
     */
    private boolean processOnlinePayment() throws Exception {
        // Check if there's an active ticket
        if (!cephra.Phone.Utilities.QueueFlow.hasActiveTicket()) {
            showErrorMessage("No active ticket found for payment.\nPlease get a ticket first.");
            return false;
        }
        
        String currentTicket = cephra.Phone.Utilities.QueueFlow.getCurrentTicketId();
        
        // Get current user
        String currentUser = cephra.Database.CephraDB.getCurrentUsername();
        if (currentUser == null || currentUser.trim().isEmpty()) {
            showErrorMessage("No user is currently logged in.");
            return false;
        }
        
        // Calculate payment amount
        double paymentAmount = cephra.Admin.Utilities.QueueBridge.computeAmountDue(currentTicket);
        
        // Store payment amount for receipt
        lastPaymentAmount = paymentAmount;
        
        // Check wallet balance first - show top-up dialog if insufficient
        if (!cephra.Database.CephraDB.hasSufficientWalletBalance(currentUser, paymentAmount)) {
            showInsufficientBalanceMessage(paymentAmount);
            return false;
        }
        
        // No need to validate ticket status - Pending_Payment only shows when ticket is completed
        
        // Process wallet payment
        boolean walletPaymentSuccess = cephra.Database.CephraDB.processWalletPayment(currentUser, currentTicket, paymentAmount);
        
        if (!walletPaymentSuccess) {
            showErrorMessage("Failed to process wallet payment.\nPlease try again or check your balance.");
            return false;
        }
        
        // Clear any pending Pending_Payment state since wallet payment is completed successfully
        clearPendingState();
        
        // Note: The actual payment processing (markPaymentPaidOnline) will be handled
        // in the payonlineActionPerformed method after this returns true
        // to avoid double processing of the same payment
        
        // No need to show success message - receipt panel will display the information
        return true;
    }
    
    
    /**
     * Shows error message to user
     * @param message the error message
     */
    private void showErrorMessage(String message) {
        SwingUtilities.invokeLater(() -> {
            JOptionPane.showMessageDialog(
                this,
                message,
                "Payment Error",
                JOptionPane.ERROR_MESSAGE
            );
        });
    }
    
    /**
     * Shows insufficient balance using Need_Topup panel
     * @param requiredAmount the amount required for payment
     */
    private void showInsufficientBalanceMessage(double requiredAmount) {
        String currentUser = cephra.Database.CephraDB.getCurrentUsername();
        double currentBalance = cephra.Database.CephraDB.getUserWalletBalance(currentUser);
        double shortage = requiredAmount - currentBalance;
        
        SwingUtilities.invokeLater(() -> {
            // Hide current Pending_Payment
            hidePayPop();
            
            // Show Need_Topup panel with balance information
            showNeedTopupPanel(currentBalance, requiredAmount, shortage);
        });
    }
    
    /**
     * Shows the Need_Topup panel with balance information
     * @param currentBalance the current wallet balance
     * @param requiredAmount the amount required for payment
     * @param shortage the shortage amount
     */
    private void showNeedTopupPanel(double currentBalance, double requiredAmount, double shortage) {
        try {
            // Find Phone frame
            Window[] windows = Window.getWindows();
            for (Window window : windows) {
                if (window instanceof cephra.Frame.Phone) {
                    cephra.Frame.Phone phoneFrame = (cephra.Frame.Phone) window;
                    
                    // Create Need_Topup panel
                    cephra.Phone.Popups.Need_Topup needTopupPanel = new cephra.Phone.Popups.Need_Topup();
                    
                    // Set balance information on labels
                    needTopupPanel.setBalanceInfo(currentBalance, requiredAmount, shortage);
                    
                    // Set as current instance
                    setCurrentNeedTopupInstance(needTopupPanel);
                    
                    // Show centered on phone frame
                    showCenteredNeedTopup(phoneFrame, needTopupPanel);
                    break;
                }
            }
        } catch (Exception e) {
            System.err.println("Error showing Need_Topup panel: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Sets the current Need_Topup instance for static access
     * @param needTopupPanel the Need_Topup panel instance
     */
    private void setCurrentNeedTopupInstance(cephra.Phone.Popups.Need_Topup needTopupPanel) {
        try {
            // Use reflection to set the static currentInstance field
            java.lang.reflect.Field field = cephra.Phone.Popups.Need_Topup.class.getDeclaredField("currentInstance");
            field.setAccessible(true);
            field.set(null, needTopupPanel);
            
            // Set the isShowing flag
            java.lang.reflect.Field showingField = cephra.Phone.Popups.Need_Topup.class.getDeclaredField("isShowing");
            showingField.setAccessible(true);
            showingField.set(null, true);
        } catch (Exception e) {
            System.err.println("Error setting Need_Topup instance: " + e.getMessage());
        }
    }
    
    /**
     * Shows Need_Topup panel centered on the Phone frame
     * @param phoneFrame the Phone frame to center on
     * @param needTopupPanel the Need_Topup panel to show
     */
    private void showCenteredNeedTopup(cephra.Frame.Phone phoneFrame, cephra.Phone.Popups.Need_Topup needTopupPanel) {
        SwingUtilities.invokeLater(() -> {
            // Set panel properties
            needTopupPanel.setPreferredSize(new Dimension(330, 370));
            needTopupPanel.setSize(330, 370);
            
            // Determine phone content size (fallback to constants if not realized yet)
            int containerW = phoneFrame.getContentPane() != null ? phoneFrame.getContentPane().getWidth() : 0;
            int containerH = phoneFrame.getContentPane() != null ? phoneFrame.getContentPane().getHeight() : 0;
            if (containerW <= 0) containerW = 350;
            if (containerH <= 0) containerH = 750;
            
            // Center the panel on the phone frame
            int x = (containerW - 330) / 2;
            int y = (containerH - 370) / 2;
            
            needTopupPanel.setBounds(x, y, 330, 370);
            
            // Add to layered pane so it appears on top of current panel
            JLayeredPane layeredPane = phoneFrame.getRootPane().getLayeredPane();
            layeredPane.add(needTopupPanel, JLayeredPane.MODAL_LAYER);
            layeredPane.moveToFront(needTopupPanel);
            
            needTopupPanel.setVisible(true);
            phoneFrame.repaint();
        });
    }
    
    
    /**
     * Handles payment errors
     * @param e the exception
     */
    private void handlePaymentError(Exception e) {
        showErrorMessage("Failed to process online payment.\nError: " + e.getMessage() + "\nPlease try again or contact support.");
    }
    
    /**
     * Navigates to Home screen
     */
    private void navigateToHome() {
        SwingUtilities.invokeLater(() -> {
            Window[] windows = Window.getWindows();
            for (Window window : windows) {
                if (window instanceof cephra.Frame.Phone) {
                    cephra.Frame.Phone phoneFrame = (cephra.Frame.Phone) window;
                    phoneFrame.switchPanel(cephra.Phone.Dashboard.Home.getAppropriateHomePanel());
                    break;
                }
            }
        });
    }
    
    /**
     * Navigates to receipt screen
     */
    private void navigateToReceipt() {
        SwingUtilities.invokeLater(() -> {
            Window[] windows = Window.getWindows();
            for (Window window : windows) {
                if (window instanceof cephra.Frame.Phone) {
                    cephra.Frame.Phone phoneFrame = (cephra.Frame.Phone) window;
                    phoneFrame.switchPanel(new cephra.Phone.RewardsWallet.Reciept());
                    break;
                }
            }
        });
    }
    
    




    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton Cash;
    private javax.swing.JLabel ChargingDue;
    private javax.swing.JLabel TICKETNUMBER;
    private javax.swing.JLabel TotalBill;
    private javax.swing.JLabel bg;
    private javax.swing.JLabel kWh;
    private javax.swing.JButton payonline;
    // End of variables declaration//GEN-END:variables
}
