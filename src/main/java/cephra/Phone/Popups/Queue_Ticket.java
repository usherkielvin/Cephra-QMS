
package cephra.Phone.Popups;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class Queue_Ticket extends javax.swing.JPanel {
    
    private static Queue_Ticket currentInstance = null;
    private static boolean isShowing = false;
    
    private static final int POPUP_WIDTH = 270;
    private static final int POPUP_HEIGHT = 390;
    private static final int PHONE_WIDTH = 350; 
    private static final int PHONE_HEIGHT = 750;
    
    public static boolean isPopupShowing() {
        return isShowing;
    }
    
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
     * Shows Queue_Ticket popup with validation
     * @return true if popup was shown successfully
     */
    public static boolean showPopup() {
        // If any other modal popup is showing, do not override it
        try {
            if (cephra.Phone.Utilities.CustomPopupManager.isPopupShowing()) return false;
        } catch (Throwable ignore) {}
        try {
            if (cephra.Phone.Popups.Battery_Full.isPopupShowing()) return false;
        } catch (Throwable ignore) {}
        try {
            if (cephra.Phone.Popups.Link_First.isPopupShowing()) return false;
        } catch (Throwable ignore) {}

        // Allow reappearing - if Queue_Ticket already showing, hide first then show again
        if (isShowing) { hidePopup(); }
        
        // Validate user is logged in
        if (!cephra.Database.CephraDB.isUserLoggedIn()) {
            return false;
        }
        
        // Find Phone frame and show centered popup
        Window[] windows = Window.getWindows();
        for (Window window : windows) {
            if (window instanceof cephra.Frame.Phone) {
                cephra.Frame.Phone phoneFrame = (cephra.Frame.Phone) window;
                showCenteredPopup(phoneFrame);
                return true;
            }
        }
        return false;
    }
    
    /**
     * Shows Queue_Ticket popup centered on the Phone frame
     * @param phoneFrame the Phone frame to center on
     */
    private static void showCenteredPopup(cephra.Frame.Phone phoneFrame) {
        SwingUtilities.invokeLater(() -> {
            currentInstance = new Queue_Ticket();
            isShowing = true;
            
            // Populate labels based on QueueFlow / DB
            currentInstance.populateFromQueueFlow();
            
            // Determine phone content size (fallback to constants if not realized yet)
            int containerW = phoneFrame.getContentPane() != null ? phoneFrame.getContentPane().getWidth() : 0;
            int containerH = phoneFrame.getContentPane() != null ? phoneFrame.getContentPane().getHeight() : 0;
            if (containerW <= 0) containerW = PHONE_WIDTH;
            if (containerH <= 0) containerH = PHONE_HEIGHT;
            
            // Center the popup on the phone frame
            int x = (containerW - POPUP_WIDTH) / 2;
            int y = (containerH - POPUP_HEIGHT) / 2;
            
            currentInstance.setBounds(x, y, POPUP_WIDTH, POPUP_HEIGHT);
            
            // Disable the background panel
            Component contentPane = phoneFrame.getContentPane();
            if (contentPane != null) {
                contentPane.setEnabled(false);
                // Disable all child components recursively
                disableAllComponents(contentPane);
            }
            
            // Add to layered pane so it appears on top of current panel
            JLayeredPane layeredPane = phoneFrame.getRootPane().getLayeredPane();
            layeredPane.add(currentInstance, JLayeredPane.MODAL_LAYER);
            layeredPane.moveToFront(currentInstance);
            
            currentInstance.setVisible(true);
            
            // Start the intro animation after the popup is visible
            currentInstance.startIntroAnimation();
            
            phoneFrame.repaint();
        });
    }
    
    /**
     * Hides the Queue_Ticket popup and cleans up resources
     */
    public static void hidePopup() {
        if (currentInstance != null && isShowing) {
            // Capture a local reference to avoid race conditions
            Queue_Ticket instance = currentInstance;

            SwingUtilities.invokeLater(() -> {
                if (instance.getParent() != null) {
                    instance.getParent().remove(instance);
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
                        window.repaint();
                        break;
                    }
                }
                
                currentInstance = null;
                isShowing = false;
            });
        }
    }
    
    // Timer for intro animation
    private Timer introTimer;
    private ImageIcon mainImageIcon;
    
    public Queue_Ticket() {
        initComponents();
        setPreferredSize(new java.awt.Dimension(POPUP_WIDTH, POPUP_HEIGHT));
        setSize(POPUP_WIDTH, POPUP_HEIGHT);
        
        // Initially hide interactive content until intro GIF finishes
        hideContent();

        // Load the main image
        loadImages();

        // Setup close button functionality (ESC key)
        setupCloseButton();

        // Setup action listeners for buttons
        setupActionListeners();
    }
    
    /**
     * Loads the main static image
     */
    private void loadImages() {
        mainImageIcon = new ImageIcon(getClass().getResource("/cephra/Cephra Images/ChargeQueProceed.png"));
    }
    
    /**
     * Starts the intro animation with GIF
     */
    private void startIntroAnimation() {
        // Load the GIF animation
        ImageIcon gifIcon = new ImageIcon(getClass().getResource("/cephra/Cephra Images/inqueuetick.gif"));
        Icon.setIcon(gifIcon);
        
        // Set up timer to switch to static image after 200ms (0.2 seconds)
        if (introTimer != null && introTimer.isRunning()) {
            introTimer.stop();
        }
        
        introTimer = new Timer(200, new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                // Switch to the static image and reveal content
                showMainImage();
                showContent();
                introTimer.stop();
            }
        });
        introTimer.setRepeats(false);
        introTimer.start();
    }
    
    /**
     * Shows the main static image
     */
    private void showMainImage() {
        Icon.setIcon(mainImageIcon);
    }

    /**
     * Hide the interactive labels and buttons while the intro GIF plays.
     */
    private void hideContent() {
        try {
            tickectID.setVisible(false);
            chargingService.setVisible(false);
            batteryLevel.setVisible(false);
            okBTN.setVisible(false);
            cancelTixBTN.setVisible(false);
        } catch (Exception ignore) {}
    }

    /**
     * Show the interactive labels and buttons after the intro GIF finishes.
     */
    private void showContent() {
        try {
            tickectID.setVisible(true);
            chargingService.setVisible(true);
            batteryLevel.setVisible(true);
            okBTN.setVisible(true);
            cancelTixBTN.setVisible(true);
        } catch (Exception ignore) {}
    }


    /**
     * Sets up close button functionality (ESC key)
     */
    private void setupCloseButton() {
        setFocusable(true);
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent evt) {
                if (evt.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    hidePopup();
                }
            }
        });
        
        // Request focus so key events work
        SwingUtilities.invokeLater(this::requestFocusInWindow);
    }

    private void populateFromQueueFlow() {
        String service = cephra.Phone.Utilities.QueueFlow.getCurrentServiceName();
        String ticket = cephra.Phone.Utilities.QueueFlow.getCurrentTicketId();
        String username = cephra.Database.CephraDB.getCurrentUsername();
        int level = cephra.Database.CephraDB.getUserBatteryLevel(username);

        // Set ticket ID (priority if battery < 20%)
        if (level < 20 && (ticket == null || ticket.isEmpty())) {
            ticket = cephra.Phone.Utilities.QueueFlow.previewNextPriorityTicketIdForService(service, level);
        } else if (ticket == null || ticket.isEmpty()) {
            ticket = cephra.Phone.Utilities.QueueFlow.previewNextTicketIdForService(service);
        }

        tickectID.setText(ticket != null ? ticket : "N/A");
        chargingService.setText(service != null ? service : "N/A");
        batteryLevel.setText(level + "%");
    }

   
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        tickectID = new javax.swing.JLabel();
        chargingService = new javax.swing.JLabel();
        batteryLevel = new javax.swing.JLabel();
        okBTN = new javax.swing.JButton();
        cancelTixBTN = new javax.swing.JButton();
        Icon = new javax.swing.JLabel();

        setOpaque(false);
        setLayout(null);

        tickectID.setFont(new java.awt.Font("Segoe UI", 1, 48)); // NOI18N
        tickectID.setForeground(new java.awt.Color(0, 189, 201));
        tickectID.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        tickectID.setText("NCH005");
        add(tickectID);
        tickectID.setBounds(17, 20, 230, 50);

        chargingService.setFont(new java.awt.Font("Segoe UI", 1, 18)); // NOI18N
        chargingService.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        chargingService.setText("Normal Charging");
        add(chargingService);
        chargingService.setBounds(7, 70, 245, 30);

        batteryLevel.setFont(new java.awt.Font("Segoe UI", 1, 40)); // NOI18N
        batteryLevel.setForeground(new java.awt.Color(0, 189, 201));
        batteryLevel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        batteryLevel.setText("55%");
        add(batteryLevel);
        batteryLevel.setBounds(66, 114, 130, 50);

        okBTN.setBorder(null);
        okBTN.setBorderPainted(false);
        okBTN.setContentAreaFilled(false);
        add(okBTN);
        okBTN.setBounds(20, 282, 220, 39);

        cancelTixBTN.setBorder(null);
        cancelTixBTN.setBorderPainted(false);
        cancelTixBTN.setContentAreaFilled(false);
        cancelTixBTN.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cancelTixBTNActionPerformed(evt);
            }
        });
        add(cancelTixBTN);
        cancelTixBTN.setBounds(20, 335, 220, 35);

        Icon.setIcon(new javax.swing.ImageIcon(getClass().getResource("/cephra/Cephra Images/ChargeQueProceed.png"))); // NOI18N
        add(Icon);
        Icon.setBounds(0, 0, 270, 390);
    }// </editor-fold>//GEN-END:initComponents

    private void cancelTixBTNActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cancelTixBTNActionPerformed
        try {
            String username = cephra.Database.CephraDB.getCurrentUsername();
            cephra.Database.CephraDB.clearActiveTicket(username);
        } catch (Throwable t) {
            System.err.println("Ticketing Cancel error: " + t.getMessage());
        }
        hidePopup();
    }//GEN-LAST:event_cancelTixBTNActionPerformed

    // Action listeners for buttons
    private void setupActionListeners() {
        // OK button - create ticket
        okBTN.addActionListener(e -> {
            try {
                String username = cephra.Database.CephraDB.getCurrentUsername();
                String existingTicket = cephra.Phone.Utilities.QueueFlow.getCurrentTicketId();

                // If user already has an active or pending ticket, show the Has_Ticket popup
                if (cephra.Phone.Utilities.QueueFlow.hasActiveTicket() ||
                    (existingTicket != null && existingTicket.trim().length() > 0)) {
                    hidePopup();
                    cephra.Phone.Popups.Has_Ticket.showPayPop(existingTicket, username);
                    return;
                }

                // Otherwise proceed to create a new ticket
                cephra.Phone.Utilities.QueueFlow.addCurrentToAdminAndStore(username);
                hidePopup();
            } catch (Throwable t) {
                System.err.println("Ticketing OK error: " + t.getMessage());
            }
        });
        
        // Cancel button - clear ticket and hide panel
        cancelTixBTN.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cancelTixBTNActionPerformed(evt);
            }
        });
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel Icon;
    private javax.swing.JLabel batteryLevel;
    private javax.swing.JButton cancelTixBTN;
    private javax.swing.JLabel chargingService;
    private javax.swing.JButton okBTN;
    private javax.swing.JLabel tickectID;
    // End of variables declaration//GEN-END:variables
}

