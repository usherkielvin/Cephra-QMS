package cephra.Phone.Popups;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class Has_Ticket extends javax.swing.JPanel {
    
    // Static state management to prevent multiple instances
    private static Has_Ticket currentInstance = null;
    private static String currentTicketId = null;
    private static boolean isShowing = false;
    
    // Intro animation fields
    private Timer introTimer;
    private ImageIcon mainImageIcon;
    
    // Popup dimensions (centered in phone frame)
    private static final int POPUP_WIDTH = 280;
    private static final int POPUP_HEIGHT = 269;
    private static final int PHONE_WIDTH = 350; // fallback if frame size not yet realized
    private static final int PHONE_HEIGHT = 750; // fallback if frame size not yet realized
    
    /**
     * Checks if PayPop is currently showing for a specific ticket
     * @param ticketId the ticket ID to check
     * @return true if PayPop is showing for this ticket
     */
    public static boolean isShowingForTicket(String ticketId) {
        return isShowing && ticketId != null && ticketId.equals(currentTicketId);
    }
    
    /**
     * Validates if PayPop can be shown for the given ticket and user
     * @param ticketId the ticket ID
     * @param customerUsername the customer username
     * @return true if PayPop can be shown
     */
    public static boolean canShowPayPop(String ticketId, String customerUsername) {
        // If latest ticket status is not open (Pending/Waiting/Charging), do not show
        try {
            String currentUser = cephra.Database.CephraDB.getCurrentUsername();
            if (currentUser != null && !currentUser.trim().isEmpty()) {
                String status = cephra.Database.CephraDB.getUserCurrentTicketStatus(currentUser);
                if (status != null) {
                    String s = status.trim().toLowerCase();
                    boolean isOpen = ("pending".equals(s) || "waiting".equals(s) || "charging".equals(s));
                    if (!isOpen) {
                        return false;
                    }
                }
            }
        } catch (Throwable ignore) {}

        // Allow reappearing - if already showing, hide first then show again
        if (isShowing) {
            hidepop();
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
     * Shows PayPop with validation
     * @param ticketId the ticket ID
     * @param customerUsername the customer username
     * @return true if PayPop was shown successfully
     */
    public static boolean showPayPop(String ticketId, String customerUsername) {
        
        if (!canShowPayPop(ticketId, customerUsername)) {
            return false;
        }
        
        // Find Phone frame and show centered PayPop
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
     * Shows PayPop centered on the Phone frame
     * @param phoneFrame the Phone frame to center on
     * @param ticketId the ticket ID
     */
    private static void showCenteredPayPop(cephra.Frame.Phone phoneFrame, String ticketId) {
        SwingUtilities.invokeLater(() -> {
            currentInstance = new Has_Ticket();
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
            
            // Center the PayPop on the phone frame
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
            
            // Start intro animation after popup is visible
            SwingUtilities.invokeLater(() -> {
                currentInstance.startIntroAnimation();
            });
            
            phoneFrame.repaint();
        });
    }
    
    /**
     * Hides the PayPop and cleans up resources
     */
    public static void hidepop() {
    if (currentInstance != null && isShowing) {
        // Capture a local reference to avoid race conditions
        Has_Ticket instance = currentInstance;

        SwingUtilities.invokeLater(() -> {
            // Stop intro timer if running
            if (instance.introTimer != null) {
                instance.introTimer.stop();
            }
            
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
            currentTicketId = null;
            isShowing = false;
        });
    }
}


    // Sets the ticket number on the UI label immediately (safe call)
    private void setTicketOnUi(String ticketId) {
      
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

    /**
     * Constructor for PayPop
     */
    public Has_Ticket() {
        // Load intro gif and main image
        loadImages();
        initComponents();
        initializePayPop();
    }
    
    /**
     * Loads the intro gif and main image icons
     */
    private void loadImages() {
        try {
            mainImageIcon = new ImageIcon(getClass().getResource("/cephra/Cephra Images/AlreadyhaveTicket.png"));
        } catch (Exception e) {
            System.err.println("Error loading images: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Starts the intro animation sequence
     */
    private void startIntroAnimation() {
        if (jLabel1 == null) {
            // Fallback to main image if label not available
            showMainImage();
            return;
        }
        
        // Create a fresh GIF instance to reset animation
        ImageIcon freshGifIcon = null;
        try {
            freshGifIcon = new ImageIcon(getClass().getResource("/cephra/Cephra Images/IntroAltick.gif"));
        } catch (Exception e) {
            System.err.println("Error loading fresh intro gif: " + e.getMessage());
            showMainImage();
            return;
        }
        
        jLabel1.setIcon(freshGifIcon);
        
        // Set up timer to forcibly cut GIF and switch to main image after 200ms (0.2 seconds)
        introTimer = new Timer(200, e -> {
            // Force stop GIF by clearing icon first, then setting main image
            jLabel1.setIcon(null);
            SwingUtilities.invokeLater(() -> {
                showMainImage();
            });
            introTimer.stop();
        });
        introTimer.setRepeats(false);
        introTimer.start();
    }
    
    /**
     * Shows the main image and ends intro animation
     */
    private void showMainImage() {
        if (jLabel1 != null && mainImageIcon != null) {
            jLabel1.setIcon(mainImageIcon);
        }
        if (introTimer != null) {
            introTimer.stop();
        }
    }
    
    /**
     * Initializes PayPop components and data
     */
    private void initializePayPop() {
        // Match popup panel to background image and remove excess white by making it transparent
        setPreferredSize(new Dimension(POPUP_WIDTH, POPUP_HEIGHT));
        setSize(POPUP_WIDTH, POPUP_HEIGHT);
        setOpaque(false);
        setupCloseButton();
        
        // Update labels with actual ticket data after components are initialized
        SwingUtilities.invokeLater(this::updateTextWithAmount);
        
        // Set username if available (optional display label removed)
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
                    hidepop();
                }
            }
        });
        
        // Request focus so key events work
        SwingUtilities.invokeLater(this::requestFocusInWindow);
    }

    /**
     * Updates PayPop labels with current ticket data and amounts
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
            
            
        } catch (Exception e) {
            System.err.println("Error updating PayPop labels: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        Golink = new javax.swing.JButton();
        jLabel1 = new javax.swing.JLabel();

        setLayout(null);

        Golink.setBorder(null);
        Golink.setBorderPainted(false);
        Golink.setContentAreaFilled(false);
        Golink.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                GolinkActionPerformed(evt);
            }
        });
        add(Golink);
        Golink.setBounds(20, 180, 220, 40);

        jLabel1.setIcon(new javax.swing.ImageIcon(getClass().getResource("/cephra/Cephra Images/AlreadyhaveTicket.png"))); // NOI18N
        add(jLabel1);
        jLabel1.setBounds(10, 0, 280, 230);
    }// </editor-fold>//GEN-END:initComponents

    private void GolinkActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_GolinkActionPerformed
       
        
        // Simply hide this popup panel
        hidepop();
    }//GEN-LAST:event_GolinkActionPerformed

   


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton Golink;
    private javax.swing.JLabel jLabel1;
    // End of variables declaration//GEN-END:variables
}
