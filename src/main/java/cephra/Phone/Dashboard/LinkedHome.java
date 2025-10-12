package cephra.Phone.Dashboard;

import cephra.Phone.Utilities.BalanceManager;
import javax.swing.SwingUtilities;

public class LinkedHome extends javax.swing.JPanel {

    // Car images array - variant versions for HomeLinked (C6.1, C9.1, etc.)
    private static final String[] carImages = {
        "/cephra/Cephra Images/c1.1.png",
        "/cephra/Cephra Images/c2.1.png", 
        "/cephra/Cephra Images/c3.1.png",
        "/cephra/Cephra Images/c4.1.png",
        "/cephra/Cephra Images/c5.1.png",
        "/cephra/Cephra Images/c6.1.png",
        "/cephra/Cephra Images/c7.1.png",
        "/cephra/Cephra Images/c8.1.png",
        "/cephra/Cephra Images/c9.1.png",
        "/cephra/Cephra Images/c10.1.png"
    };
    
    // Timer for real-time status updates
    private javax.swing.Timer statusUpdateTimer;

    public LinkedHome() {
        initComponents();
        setPreferredSize(new java.awt.Dimension(370, 750));
        setSize(370, 750);
        
        //Auto resizable text
        BalanceManager.setLabels(rewardbalance, pesobalance);

        
        // Load wallet balance and reward points
        loadWalletBalance();
        loadRewardPoints();
        
        // Load logged name
        loadLoggedName();
        
        // Load status
        loadStatus();
        
        // Set car image to match the linked car
        setLinkedCarImage();
        
        // Initialize and start real-time status updates
        initializeStatusTimer();
        
        // Add listeners to refresh data when panel becomes visible
        addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusGained(java.awt.event.FocusEvent e) {
                setLinkedCarImage(); // Refresh car image when panel gains focus
                loadStatus(); // Refresh status when panel gains focus
            }
        });
        
        addHierarchyListener(new java.awt.event.HierarchyListener() {
            @Override
            public void hierarchyChanged(java.awt.event.HierarchyEvent e) {
                if ((e.getChangeFlags() & java.awt.event.HierarchyEvent.SHOWING_CHANGED) != 0) {
                    if (isShowing()) {
                        setLinkedCarImage(); // Refresh car image when panel becomes visible
                        loadStatus(); // Refresh status when panel becomes visible
                        startStatusTimer(); // Start timer when panel becomes visible
                    } else {
                        stopStatusTimer(); // Stop timer when panel becomes hidden
                    }
                }
            }
        });
         String username = cephra.Database.CephraDB.getCurrentUsername();
           String userPlateNumber = cephra.Database.CephraDB.getUserPlateNumber(username);
               plateNumber.setText(userPlateNumber);
        
    }
    
    /**
     * Load wallet balance from database
     */
    private void loadWalletBalance() {
        try {
            String username = cephra.Database.CephraDB.getCurrentUsername();
            if (username != null && !username.isEmpty()) {
                double balance = cephra.Database.CephraDB.getUserWalletBalance(username);
                pesobalance.setText(String.format("%.0f", balance));
            }
        } catch (Exception e) {
            System.err.println("Error loading wallet balance: " + e.getMessage());
            pesobalance.setText("0");
        }
    }
    
    /**
     * Load reward points from database
     */
    private void loadRewardPoints() {
        try {
            String username = cephra.Database.CephraDB.getCurrentUsername();
            if (username != null && !username.isEmpty()) {
                int points = cephra.Phone.Utilities.RewardSystem.getUserPoints(username);
                rewardbalance.setText(String.valueOf(points));
            }
        } catch (Exception e) {
            System.err.println("Error loading reward points: " + e.getMessage());
            rewardbalance.setText("0");
        }
    }
    
    /**
     * Load logged name from database
     */
    private void loadLoggedName() {
        try {
            if (LoggedName != null) {
                String firstname = cephra.Database.CephraDB.getCurrentFirstname();
                String safeFirstname = firstname != null ? firstname.trim() : "";
                if (safeFirstname.isEmpty()) {
                    LoggedName.setText("Welcome to Cephra!");
                } else {
                    // Get only the first word of the firstname
                    String firstWord = safeFirstname.split("\\s+")[0];
                    LoggedName.setText("Welcome to Cephra, " + firstWord + "!");
                }
            }
        } catch (Exception e) {
            System.err.println("Error loading logged name: " + e.getMessage());
            LoggedName.setText("Welcome to Cephra!");
        }
    }
    
    /**
     * Load status from user's current queue ticket
     */
    private void loadStatus() {
        try {
            if (Status != null) {
                String username = cephra.Database.CephraDB.getCurrentUsername();
                if (username != null && !username.isEmpty()) {
                    // Get user's current ticket status
                    String status = cephra.Database.CephraDB.getUserCurrentTicketStatus(username);
                    if (status != null && !status.isEmpty()) {
                        // Map database status to display status
                        String displayStatus;
                        switch (status.toLowerCase()) {
                            case "pending":
                                displayStatus = "Pending";
                                break;
                            case "waiting":
                                displayStatus = "Waiting";
                                break;
                            case "charging":
                                // Get the actual bay number for this ticket
                                String ticketId = cephra.Database.CephraDB.getUserCurrentTicketId(username);
                                if (ticketId != null && !ticketId.isEmpty()) {
                                    String bayNumber = cephra.Admin.BayManagement.getBayNumberByTicket(ticketId);
                                    if (bayNumber != null && !bayNumber.isEmpty()) {
                                        displayStatus = "Charging at Bay " + bayNumber;
                                    } else {
                                        displayStatus = "Charging";
                                    }
                                } else {
                                    displayStatus = "Charging";
                                }
                                break;
                            case "complete":
                            case "completed":
                                displayStatus = "Complete";
                                break;
                            default:
                                // Get current user's battery level
                                int batteryLevel = cephra.Database.CephraDB.getUserBatteryLevel(username);
                                if (batteryLevel != -1) {
                                    displayStatus = "Battery is " + batteryLevel + "%";
                                } else {
                                    displayStatus = "Battery is 100%";
                                }
                                break;
                        }
                        Status.setText(displayStatus);
                        
                        // Show PENDING button when status is "Complete" (regardless of payment status)
                        // Only hide it if payment status is explicitly "Paid" OR if no ticket exists in queue
                        if ("Complete".equals(displayStatus)) {
                            // Check if ticket still exists in queue_tickets (if not, it was already paid and removed)
                            String currentTicketId = cephra.Database.CephraDB.getUserCurrentTicketId(username);
                            
                            if (currentTicketId == null || currentTicketId.isEmpty()) {
                                // No ticket found in queue - it was already paid and removed
                                PENDINGPAYMENT.setVisible(false);
                            } else {
                                // Ticket still exists - check payment status
                                String paymentStatus = cephra.Database.CephraDB.getUserCurrentTicketPaymentStatus(username);
                                
                                // Hide button only if payment status is explicitly "Paid"
                                if ("Paid".equalsIgnoreCase(paymentStatus)) {
                                    PENDINGPAYMENT.setVisible(false);
                                } else {
                                    // Show button for all other cases (Pending, null, empty, etc.)
                                    PENDINGPAYMENT.setVisible(true);
                                }
                            }
                        } else {
                            PENDINGPAYMENT.setVisible(false);
                        }
                    } else {
                        // No ticket found - show current user's battery level
                        int batteryLevel = cephra.Database.CephraDB.getUserBatteryLevel(username);
                        if (batteryLevel != -1) {
                            Status.setText("Battery is " + batteryLevel + "%");
                        } else {
                            Status.setText("Battery is 100%");
                        }
                        PENDINGPAYMENT.setVisible(false);
                    }
                } else {
                    Status.setText("Unavailable");
                    PENDINGPAYMENT.setVisible(false);
                }
            }
        } catch (Exception e) {
            System.err.println("Error loading status: " + e.getMessage());
            Status.setText("Unavailable");
        }
    }
    
    /**
     * Initialize the status update timer
     */
    private void initializeStatusTimer() {
        statusUpdateTimer = new javax.swing.Timer(2000, new java.awt.event.ActionListener() { // Update every 2 seconds
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                loadStatus(); // Refresh status in real-time
            }
        });
        statusUpdateTimer.setRepeats(true);
        
        // Start timer if panel is currently visible
        if (isShowing()) {
            startStatusTimer();
        }
    }
    
    /**
     * Start the status update timer
     */
    private void startStatusTimer() {
        if (statusUpdateTimer != null && !statusUpdateTimer.isRunning()) {
            statusUpdateTimer.start();
        }
    }
    
    /**
     * Stop the status update timer
     */
    private void stopStatusTimer() {
        if (statusUpdateTimer != null && statusUpdateTimer.isRunning()) {
            statusUpdateTimer.stop();
        }
    }
    
    /**
     * Set car image to match the user's linked car from database
     */
    private void setLinkedCarImage() {
        try {
            String username = cephra.Database.CephraDB.getCurrentUsername();
            if (username != null && !username.isEmpty()) {
                // Get user's car index from database
                int carIndex = cephra.Database.CephraDB.getUserCarIndex(username);
                
                // If no car assigned yet, assign a random one (same as LinkedCar)
                if (carIndex == -1) {
                    carIndex = new java.util.Random().nextInt(carImages.length);
                    cephra.Database.CephraDB.setUserCarIndex(username, carIndex);
                    // System.out.println("HomeLinked: Assigned car " + (carIndex + 1) + " to user " + username);
                }
                
                // Set the car image while preserving the form positioning (x=31, y=173, size=307x209)
                if (carIndex >= 0 && carIndex < carImages.length) {
                    CAR.setIcon(new javax.swing.ImageIcon(getClass().getResource(carImages[carIndex])));
                    // System.out.println("HomeLinked: Set car image to " + carImages[carIndex] + " for user " + username);
                }
            }
        } catch (Exception e) {
            System.err.println("Error setting linked car image: " + e.getMessage());
        }
    }

   
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        PENDINGPAYMENT = new javax.swing.JButton();
        jLabel2 = new javax.swing.JLabel();
        Status = new javax.swing.JLabel();
        LoggedName = new javax.swing.JLabel();
        platenumholder = new javax.swing.JPanel();
        plateNumber = new javax.swing.JLabel();
        filler = new javax.swing.JLabel();
        CAR = new javax.swing.JLabel();
        Notifications = new javax.swing.JButton();
        charge = new javax.swing.JButton();
        pesobalance = new javax.swing.JLabel();
        rewardbalance = new javax.swing.JLabel();
        linkbutton = new javax.swing.JButton();
        historybutton = new javax.swing.JButton();
        profilebutton = new javax.swing.JButton();
        rewards = new javax.swing.JButton();
        wallet = new javax.swing.JButton();
        jLabel1 = new javax.swing.JLabel();

        setLayout(null);

        PENDINGPAYMENT.setIcon(new javax.swing.ImageIcon(getClass().getResource("/cephra/Cephra Images/pending.png"))); // NOI18N
        PENDINGPAYMENT.setAlignmentY(0.0F);
        PENDINGPAYMENT.setBorder(null);
        PENDINGPAYMENT.setBorderPainted(false);
        PENDINGPAYMENT.setContentAreaFilled(false);
        PENDINGPAYMENT.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
        PENDINGPAYMENT.setHorizontalTextPosition(javax.swing.SwingConstants.LEFT);
        PENDINGPAYMENT.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                PENDINGPAYMENTActionPerformed(evt);
            }
        });
        add(PENDINGPAYMENT);
        PENDINGPAYMENT.setBounds(31, 336, 307, 65);

        jLabel2.setIcon(new javax.swing.ImageIcon(getClass().getResource("/cephra/Cephra Images/curvedgif.gif"))); // NOI18N
        add(jLabel2);
        jLabel2.setBounds(20, 400, 340, 196);

        Status.setFont(new java.awt.Font("Segoe UI", 1, 18)); // NOI18N
        Status.setForeground(new java.awt.Color(255, 255, 255));
        Status.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        Status.setText("Waiting");
        add(Status);
        Status.setBounds(30, 337, 310, 40);

        LoggedName.setFont(new java.awt.Font("Segoe UI", 1, 18)); // NOI18N
        LoggedName.setForeground(new java.awt.Color(0, 204, 204));
        LoggedName.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
        LoggedName.setText("Welcome to Cephra, Dizon");
        add(LoggedName);
        LoggedName.setBounds(30, 100, 300, 50);

        platenumholder.setBackground(new java.awt.Color(204, 204, 204));
        platenumholder.setForeground(new java.awt.Color(102, 102, 102));
        platenumholder.setOpaque(false);
        platenumholder.setLayout(new java.awt.GridLayout(1, 0, 10, 0));

        plateNumber.setFont(new java.awt.Font("Segoe UI", 1, 14)); // NOI18N
        plateNumber.setForeground(new java.awt.Color(102, 102, 102));
        plateNumber.setText("NBH3261");
        platenumholder.add(plateNumber);
        platenumholder.add(filler);

        add(platenumholder);
        platenumholder.setBounds(40, 230, 180, 20);

        CAR.setIcon(new javax.swing.ImageIcon(getClass().getResource("/cephra/Cephra Images/c7.1.png"))); // NOI18N
        add(CAR);
        CAR.setBounds(31, 173, 307, 209);

        Notifications.setBorder(null);
        Notifications.setBorderPainted(false);
        Notifications.setContentAreaFilled(false);
        Notifications.setFocusPainted(false);
        Notifications.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                NotificationsActionPerformed(evt);
            }
        });
        add(Notifications);
        Notifications.setBounds(310, 50, 40, 50);

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
        charge.setBounds(50, 680, 40, 40);

        pesobalance.setFont(new java.awt.Font("Segoe UI", 1, 10)); // NOI18N
        pesobalance.setForeground(new java.awt.Color(255, 255, 255));
        pesobalance.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
        pesobalance.setText("500");
        add(pesobalance);
        pesobalance.setBounds(258, 70, 50, 20);

        rewardbalance.setFont(new java.awt.Font("Segoe UI", 1, 14)); // NOI18N
        rewardbalance.setForeground(new java.awt.Color(255, 255, 255));
        rewardbalance.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
        rewardbalance.setText("500");
        add(rewardbalance);
        rewardbalance.setBounds(170, 70, 50, 20);

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
        linkbutton.setBounds(110, 680, 40, 40);

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
        historybutton.setBounds(220, 680, 40, 40);

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
        profilebutton.setBounds(280, 670, 40, 50);

        rewards.setBorder(null);
        rewards.setBorderPainted(false);
        rewards.setContentAreaFilled(false);
        rewards.setFocusPainted(false);
        rewards.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                rewardsActionPerformed(evt);
            }
        });
        add(rewards);
        rewards.setBounds(30, 610, 150, 60);

        wallet.setBorder(null);
        wallet.setBorderPainted(false);
        wallet.setContentAreaFilled(false);
        wallet.setFocusPainted(false);
        wallet.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                walletActionPerformed(evt);
            }
        });
        add(wallet);
        wallet.setBounds(190, 600, 150, 70);

        jLabel1.setIcon(new javax.swing.ImageIcon(getClass().getResource("/cephra/Cephra Images/Home_LINKED.png"))); // NOI18N
        jLabel1.setPreferredSize(new java.awt.Dimension(350, 750));
        add(jLabel1);
        jLabel1.setBounds(0, 0, 350, 750);
    }// </editor-fold>//GEN-END:initComponents

    private void NotificationsActionPerformed(java.awt.event.ActionEvent evt) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                java.awt.Window[] windows = java.awt.Window.getWindows();
                for (java.awt.Window window : windows) {
                    if (window instanceof cephra.Frame.Phone) {
                        cephra.Frame.Phone phoneFrame = (cephra.Frame.Phone) window;
                        cephra.Phone.Dashboard.NotificationHistory notificationHistory = new cephra.Phone.Dashboard.NotificationHistory();
                        notificationHistory.setPreviousPanel(cephra.Phone.Dashboard.Home.getAppropriateHomePanel());
                        phoneFrame.switchPanel(notificationHistory);
                        break;
                    }
                }
            }
        });
    }

    private void rewardsActionPerformed(java.awt.event.ActionEvent evt) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                java.awt.Window[] windows = java.awt.Window.getWindows();
                for (java.awt.Window window : windows) {
                    if (window instanceof cephra.Frame.Phone) {
                        cephra.Frame.Phone phoneFrame = (cephra.Frame.Phone) window;
                        phoneFrame.switchPanel(new cephra.Phone.RewardsWallet.Rewards());
                        break;
                    }
                }
            }
        });
    }

    private void walletActionPerformed(java.awt.event.ActionEvent evt) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                java.awt.Window[] windows = java.awt.Window.getWindows();
                for (java.awt.Window window : windows) {
                    if (window instanceof cephra.Frame.Phone) {
                        cephra.Frame.Phone phoneFrame = (cephra.Frame.Phone) window;
                        phoneFrame.switchPanel(new cephra.Phone.RewardsWallet.Wallet());
                        break;
                    }
                }
            }
        });
    }

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


    private void PENDINGPAYMENTActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_PENDINGPAYMENTActionPerformed
        // Show Pending_Payment payment popup when PENDING button is clicked
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                try {
                    // Get current user's ticket information
                    String username = cephra.Database.CephraDB.getCurrentUsername();
                    if (username != null && !username.isEmpty()) {
                        // Get user's current ticket ID
                        String ticketId = cephra.Database.CephraDB.getUserCurrentTicketId(username);
                        if (ticketId != null && !ticketId.isEmpty()) {
                            // Show Pending_Payment for this ticket
                            boolean success = cephra.Phone.Popups.Pending_Payment.showPayPop(ticketId, username);
                            if (!success) {
                                System.err.println("HOMELINKED: Failed to show PayPop for ticket " + ticketId);
                            }
                        } else {
                            System.err.println("HOMELINKED: No current ticket found for user " + username);
                        }
                    } else {
                        System.err.println("HOMELINKED: No user logged in");
                    }
                } catch (Exception e) {
                    System.err.println("HOMELINKED: Error showing PayPop: " + e.getMessage());
                }
            }
        });
    }//GEN-LAST:event_PENDINGPAYMENTActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel CAR;
    private javax.swing.JLabel LoggedName;
    private javax.swing.JButton Notifications;
    private javax.swing.JButton PENDINGPAYMENT;
    private javax.swing.JLabel Status;
    private javax.swing.JButton charge;
    private javax.swing.JLabel filler;
    private javax.swing.JButton historybutton;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JButton linkbutton;
    private javax.swing.JLabel pesobalance;
    private javax.swing.JLabel plateNumber;
    private javax.swing.JPanel platenumholder;
    private javax.swing.JButton profilebutton;
    private javax.swing.JLabel rewardbalance;
    private javax.swing.JButton rewards;
    private javax.swing.JButton wallet;
    // End of variables declaration//GEN-END:variables
}
