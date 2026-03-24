
package cephra.Phone.RewardsWallet;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import cephra.Phone.Utilities.WalletHistoryManager.WalletHistoryUpdateListener;

public class WalletHistory extends javax.swing.JPanel implements WalletHistoryUpdateListener {
    
    private String currentUsername;
    private Object[] currentTransactionEntry;
    private javax.swing.JPanel modalOverlay;
    private JPanel previousPanel; // To store the previous panel for back navigation

    public WalletHistory() {
        initComponents();
        setPreferredSize(new java.awt.Dimension(370, 750));
        setSize(370, 750);
        setupLabelPosition();
        makeDraggable();
        
        // Initialize components
        setupCustomCode();
        setupScrollableHistoryContent();
        
        // Add close button action
      
        // Register with WalletHistoryManager to receive updates
        cephra.Phone.Utilities.WalletHistoryManager.addWalletHistoryUpdateListener(this);
        
        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentShown(java.awt.event.ComponentEvent e) {
                refreshHistoryDisplay();
            }
        });
    }
    
    /**
     * Setup scrollable history content using designed panel structure like ChargeHistory
     */
    private void setupScrollableHistoryContent() {
        // Use SwingUtilities.invokeLater to ensure everything is ready after initComponents
        javax.swing.SwingUtilities.invokeLater(() -> {
            try {
                // Your history panel is already set up in initComponents with your labels:
                // time, amount, date, type, description
                if (historyPanel != null) {
                    
                    // Your panel is already in the scroll pane thanks to initComponents
                    // Keep your exact design and layout - don't change anything!
                    
                } else {
                    return;
                }
                
                // Configure scroll pane behavior to enable vertical scrolling
                historyScrollPane.setVerticalScrollBarPolicy(javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
                historyScrollPane.setHorizontalScrollBarPolicy(javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
                historyScrollPane.getVerticalScrollBar().setUnitIncrement(16);
                
                // Style the scrollbar to match the application's design
           
                if (historyScrollPane.getHorizontalScrollBar() != null) {
                    historyScrollPane.getHorizontalScrollBar().setPreferredSize(new Dimension(0, 0));
                }
                
                historyScrollPane.setWheelScrollingEnabled(true);
                historyScrollPane.putClientProperty("JScrollPane.fastWheelScrolling", Boolean.TRUE);
                
                // Add mouse wheel listener to the main panel to ensure scrolling works properly
                this.addMouseWheelListener(new MouseWheelListener() {
                    @Override
                    public void mouseWheelMoved(MouseWheelEvent e) {
                        JScrollBar verticalScrollBar = historyScrollPane.getVerticalScrollBar();
                        int notches = e.getWheelRotation();
                        int scrollAmount = verticalScrollBar.getUnitIncrement() * notches * 3;
                        int newValue = verticalScrollBar.getValue() + scrollAmount;
                        
                        // Ensure the new value is within bounds
                        newValue = Math.max(newValue, verticalScrollBar.getMinimum());
                        newValue = Math.min(newValue, verticalScrollBar.getMaximum() - verticalScrollBar.getVisibleAmount());
                        
                        verticalScrollBar.setValue(newValue);
                        e.consume(); // Prevent the event from propagating
                    }
                });
                
                // Also add the listener to the history panel and scroll pane for better coverage
                historyPanel.addMouseWheelListener(new MouseWheelListener() {
                    @Override
                    public void mouseWheelMoved(MouseWheelEvent e) {
                        JScrollBar verticalScrollBar = historyScrollPane.getVerticalScrollBar();
                        int notches = e.getWheelRotation();
                        int scrollAmount = verticalScrollBar.getUnitIncrement() * notches * 3;
                        int newValue = verticalScrollBar.getValue() + scrollAmount;
                        
                        newValue = Math.max(newValue, verticalScrollBar.getMinimum());
                        newValue = Math.min(newValue, verticalScrollBar.getMaximum() - verticalScrollBar.getVisibleAmount());
                        
                        verticalScrollBar.setValue(newValue);
                        e.consume();
                    }
                });
                
                mainHistoryContainer.addMouseWheelListener(new MouseWheelListener() {
                    @Override
                    public void mouseWheelMoved(MouseWheelEvent e) {
                        JScrollBar verticalScrollBar = historyScrollPane.getVerticalScrollBar();
                        int notches = e.getWheelRotation();
                        int scrollAmount = verticalScrollBar.getUnitIncrement() * notches * 3;
                        int newValue = verticalScrollBar.getValue() + scrollAmount;
                        
                        newValue = Math.max(newValue, verticalScrollBar.getMinimum());
                        newValue = Math.min(newValue, verticalScrollBar.getMaximum() - verticalScrollBar.getVisibleAmount());
                        
                        verticalScrollBar.setValue(newValue);
                        e.consume();
                    }
                });
                
                // Force a repaint to ensure everything displays correctly
                historyScrollPane.revalidate();
                historyScrollPane.repaint();
                this.revalidate();
                this.repaint();
                
                // Load wallet transaction entries now that the panel is ready
                loadWalletTransactions();
                
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
    
    private void loadWalletTransactions() {
        // Get current user's wallet transactions
        currentUsername =   cephra.Database.CephraDB.getCurrentUsername(); // Placeholder - replace with actual current user logic
        refreshHistoryDisplay();
    }
    
    public void setPreviousPanel(JPanel panel) {
        this.previousPanel = panel;
    }
    
    private void goBackToPreviousPanel() {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                System.out.println("Going back from Wallet History");
                     
          
                java.awt.Window[] windows = java.awt.Window.getWindows();
                for (java.awt.Window window : windows) {
                    if (window instanceof cephra.Frame.Phone) {
                        cephra.Frame.Phone phoneFrame = (cephra.Frame.Phone) window;
                        phoneFrame.switchPanel(new cephra.Phone.RewardsWallet.Wallet());
                        break;
                    }
                
            
        }
                // Close or navigate back logic would go here
                if (previousPanel != null) {
                    System.out.println("Would switch to previous panel: " + previousPanel.getClass().getSimpleName());
                    
                }
            }
        });
    }
    
    public void refreshHistoryDisplay() {
        // Check if your designed panels are initialized before using them
        if (historyPanel == null || history1 == null) {
            return;
        }
        
        // Get current user's wallet transactions from the database
        java.util.List<Object[]> transactions = cephra.Database.CephraDB.getWalletTransactionHistory(currentUsername);
        
        if (transactions.isEmpty()) {
            // Show "No transactions" message in your designed labels
            time.setText("No Data");
            amount.setText("₱0.00");
            date.setText("No Date");
            type.setText("No Transactions");
            description.setText("0 transactions");
            currentTransactionEntry = null;  // No entry to show details for
            
        } else {
            // Clear the container first to add all transaction entries
            historyPanel.removeAll();
            
            // Create a copy of your history1 panel for each entry
            for (int i = 0; i < transactions.size(); i++) {
                Object[] transaction = transactions.get(i);
                
                // Create a panel that looks like your history1 panel
                JPanel entryPanel = createHistoryPanelLikeHistory1(transaction);
                historyPanel.add(entryPanel);
                
                // Add spacing between entries (except for the last one)
                if (i < transactions.size() - 1) {
                    historyPanel.add(Box.createRigidArea(new Dimension(0, 8)));
                }
            }
            
            // Store the first entry for details button
            currentTransactionEntry = transactions.get(0);
        }
        
        // Calculate and set the preferred height based on content
        SwingUtilities.invokeLater(() -> {
            // Calculate height based on number of transactions
            int totalHeight = transactions.size() * 80;  // Each entry is 80px high
            if (transactions.size() > 1) {
                totalHeight += (transactions.size() - 1) * 8;  // Add spacing between entries
            }
            
            // Set preferred size to enable scrolling when content exceeds visible area
            historyPanel.setPreferredSize(new Dimension(308, totalHeight));
            
            // Ensure proper repaint
            history1.revalidate();
            history1.repaint();
            historyPanel.revalidate();
            historyPanel.repaint();
            historyScrollPane.revalidate();
            historyScrollPane.repaint();
        });
    }
    
    /**
     * Public method to refresh wallet history when called externally
     */
    public void refreshWalletHistory() {
        SwingUtilities.invokeLater(this::refreshHistoryDisplay);
    }
    
    
    /**
     * Method to handle wallet history updates
     * Implements WalletHistoryUpdateListener interface
     * @param username The username whose wallet history was updated
     */
    @Override
    public void onWalletHistoryUpdated(String username) {
        if (username != null && username.equals(currentUsername)) {
            // Refresh the display with the latest transaction data from the database
            SwingUtilities.invokeLater(this::refreshHistoryDisplay);
        }
    }
    
    /**
     * Creates a panel that looks exactly like your designed history1 panel
     */
    private JPanel createHistoryPanelLikeHistory1(final Object[] transaction) {
        // Create panel with safe template copying
        JPanel panel = new JPanel();
        
        // Use safe defaults if history1 is not available
        if (history1 != null) {
            try {
                panel.setLayout(null); // Always use null layout for absolute positioning
                panel.setBackground(history1.getBackground());
                panel.setBorder(history1.getBorder());
                panel.setPreferredSize(new Dimension(310, 80));
                panel.setMaximumSize(new Dimension(310, 80));
                panel.setMinimumSize(new Dimension(310, 80));
            } catch (Exception e) {
                // Fallback to safe defaults
                panel.setLayout(null);
                panel.setBackground(Color.WHITE);
                panel.setPreferredSize(new Dimension(310, 80));
                panel.setMaximumSize(new Dimension(310, 80));
            }
        } else {
            // Safe defaults when template is not available
            panel.setLayout(null);
            panel.setBackground(Color.WHITE);
            panel.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Color.BLACK));
            panel.setPreferredSize(new Dimension(310, 80));
            panel.setMaximumSize(new Dimension(310, 80));
        }
        
        // Create components with safe copying or fallbacks
        createTransactionComponents(panel, transaction);
        
        return panel;
    }
    
    private void createTransactionComponents(JPanel panel, final Object[] transaction) {
        // Transaction data structure: [transaction_type, amount, new_balance, description, reference_id, transaction_date]
        String transactionType = (String) transaction[0];
        double transactionAmount = (Double) transaction[1];
        String desc = (String) transaction[3];
        java.sql.Timestamp timestamp = (java.sql.Timestamp) transaction[5];
        
        // Format date and time
        java.text.SimpleDateFormat dateFormat = new java.text.SimpleDateFormat("MMM dd, yyyy");
        java.text.SimpleDateFormat timeFormat = new java.text.SimpleDateFormat("hh:mm a");
        String dateStr = dateFormat.format(timestamp);
        String timeStr = timeFormat.format(timestamp);
        
        // Time label
        JLabel timeClone = new JLabel(timeStr);
        if (time != null) {
            try {
                timeClone.setBounds(time.getBounds());
                timeClone.setFont(time.getFont());
                timeClone.setForeground(time.getForeground());
                timeClone.setBackground(time.getBackground());
                timeClone.setOpaque(time.isOpaque());
            } catch (Exception e) {
                timeClone.setBounds(20, 20, 80, 16);
                timeClone.setFont(new Font("Arial", Font.PLAIN, 12));
            }
        } else {
            timeClone.setBounds(20, 20, 80, 16);
            timeClone.setFont(new Font("Arial", Font.PLAIN, 12));
        }
        panel.add(timeClone);
        
        // Amount label
        String amountText = formatTransactionAmount(transactionType, transactionAmount);
        JLabel amountClone = new JLabel(amountText);
        if (amount != null) {
            try {
                amountClone.setBounds(amount.getBounds());
                amountClone.setFont(amount.getFont());
                amountClone.setForeground(getAmountColor(transactionType));
                amountClone.setBackground(amount.getBackground());
                amountClone.setOpaque(amount.isOpaque());
            } catch (Exception e) {
                amountClone.setBounds(220, 40, 70, 16);
                amountClone.setFont(new Font("Arial", Font.BOLD, 12));
                amountClone.setForeground(getAmountColor(transactionType));
            }
        } else {
            amountClone.setBounds(220, 40, 70, 16);
            amountClone.setFont(new Font("Arial", Font.BOLD, 12));
            amountClone.setForeground(getAmountColor(transactionType));
        }
        panel.add(amountClone);
        
        // Date label
        JLabel dateClone = new JLabel(dateStr);
        if (date != null) {
            try {
                dateClone.setBounds(date.getBounds());
                dateClone.setFont(date.getFont());
                dateClone.setForeground(date.getForeground());
                dateClone.setBackground(date.getBackground());
                dateClone.setOpaque(date.isOpaque());
            } catch (Exception e) {
                dateClone.setBounds(20, 0, 120, 20);
                dateClone.setFont(new Font("Arial", Font.BOLD, 14));
            }
        } else {
            dateClone.setBounds(20, 0, 120, 20);
            dateClone.setFont(new Font("Arial", Font.BOLD, 14));
        }
        panel.add(dateClone);
        
        // Type label
        String displayType = getTransactionTypeDisplay(transactionType);
        JLabel typeClone = new JLabel(displayType);
        if (type != null) {
            try {
                typeClone.setBounds(type.getBounds());
                typeClone.setFont(type.getFont());
                typeClone.setForeground(getTransactionTypeColor(transactionType));
                typeClone.setBackground(type.getBackground());
                typeClone.setOpaque(type.isOpaque());
            } catch (Exception e) {
                typeClone.setBounds(20, 40, 210, 16);
                typeClone.setFont(new Font("Arial", Font.PLAIN, 12));
                typeClone.setForeground(getTransactionTypeColor(transactionType));
            }
        } else {
            typeClone.setBounds(20, 40, 210, 16);
            typeClone.setFont(new Font("Arial", Font.PLAIN, 12));
            typeClone.setForeground(getTransactionTypeColor(transactionType));
        }
        panel.add(typeClone);
        
        // Description label
        String descriptionPreview = desc != null ? desc : "";
        if (descriptionPreview.length() > 30) {
            descriptionPreview = descriptionPreview.substring(0, 30) + "...";
        }
        JLabel descClone = new JLabel(descriptionPreview);
        if (description != null) {
            try {
                descClone.setBounds(description.getBounds());
                descClone.setFont(description.getFont());
                descClone.setForeground(description.getForeground());
                descClone.setBackground(description.getBackground());
                descClone.setOpaque(description.isOpaque());
            } catch (Exception e) {
                descClone.setBounds(20, 60, 220, 16);
                descClone.setFont(new Font("Arial", Font.PLAIN, 11));
            }
        } else {
            descClone.setBounds(20, 60, 220, 16);
            descClone.setFont(new Font("Arial", Font.PLAIN, 11));
        }
        panel.add(descClone);
        
        // Details button
        JButton detailsClone = new JButton();
        if (details != null) {
            try {
                detailsClone.setBounds(details.getBounds());
                detailsClone.setBorderPainted(details.isBorderPainted());
                detailsClone.setContentAreaFilled(details.isContentAreaFilled());
                detailsClone.setFocusPainted(details.isFocusPainted());
            } catch (Exception e) {
                detailsClone.setBounds(0, -3, 310, 80);
                detailsClone.setBorderPainted(false);
                detailsClone.setContentAreaFilled(false);
                detailsClone.setFocusPainted(false);
            }
        } else {
            detailsClone.setBounds(0, -3, 310, 80);
            detailsClone.setBorderPainted(false);
            detailsClone.setContentAreaFilled(false);
            detailsClone.setFocusPainted(false);
        }
        
        detailsClone.addActionListener(e -> {
            currentTransactionEntry = transaction;
            showWalletTransactionDetails(transaction);
        });
        panel.add(detailsClone);
    }

    
    /**
     * Gets display name for transaction type
     */
    private String getTransactionTypeDisplay(String transactionType) {
        switch (transactionType) {
            case "TOP_UP": return "Top Up";
            case "PAYMENT": return "Payment";
            default: return transactionType;
        }
    }
    
    /**
     * Gets color for transaction type
     */
    private Color getTransactionTypeColor(String transactionType) {
        switch (transactionType) {
            case "TOP_UP": return new Color(0, 150, 0); // Green for income
            case "PAYMENT": return new Color(200, 0, 0); // Red for payment
            default: return new Color(50, 100, 150); // Blue for others
        }
    }
    
    /**
     * Formats transaction amount with proper sign
     */
    private String formatTransactionAmount(String transactionType, double amount) {
        String sign = transactionType.equals("TOP_UP") ? "+" : "-";
        return sign + String.format("₱%.2f", Math.abs(amount));
    }
    
    /**
     * Gets color for amount display
     */
    private Color getAmountColor(String transactionType) {
        return transactionType.equals("TOP_UP") ? 
               new Color(0, 150, 0) : new Color(200, 0, 0);
    }
    
    private void showWalletTransactionDetails(Object[] transaction) {
        // Transaction data: [transaction_type, amount, new_balance, description, reference_id, transaction_date]
        String transactionType = (String) transaction[0];
        double transactionAmount = (Double) transaction[1];
        double newBalance = (Double) transaction[2];
        String desc = (String) transaction[3];
        String referenceId = (String) transaction[4];
        java.sql.Timestamp timestamp = (java.sql.Timestamp) transaction[5];
        
        // Update the designed detailpanel with the transaction information
        if (detailpanel != null) {
            // Update transaction type - just the value
            if (transactionType != null) {
                this.transactionType.setText(getTransactionTypeDisplay(transactionType));
            }
            
            // Update amount information - just the value
            if (transactionAmount != 0.0) {
                this.transactionAmount.setText(formatTransactionAmount(transactionType, transactionAmount));
            }
            
            // Update description - just the value
            if (transactionDesc != null) {
                transactionDesc.setText(desc != null ? desc : "No description");
            }
            
            // Update reference ID - just the value
            if (refId != null) {
                refId.setText(referenceId != null ? referenceId : "N/A");
            }
            
            // Update new balance after transaction - just the value
            if (newBalance != 0.0) {
                this.newBalance.setText(String.format("₱%.2f", newBalance));
            }
            
            // Update username - just the value
            if (username != null) {
                username.setText(currentUsername != null ? currentUsername : "Unknown");
            }
            
            // Format date and time
            java.text.SimpleDateFormat dateFormat = new java.text.SimpleDateFormat("MMM dd, yyyy");
            java.text.SimpleDateFormat timeFormat = new java.text.SimpleDateFormat("hh:mm a");
            String dateStr = dateFormat.format(timestamp);
            String timeStr = timeFormat.format(timestamp);
            
            // Update date - just the value
            if (transactionDate != null) {
                transactionDate.setText(dateStr);
            }
            
            // Update time - just the value
            if (transactionTime != null) {
                transactionTime.setText(timeStr);
            }
            
            // Setup OK button action if not already done
            if (ok != null) {
                // Remove any existing listeners first
                for (java.awt.event.ActionListener listener : ok.getActionListeners()) {
                    ok.removeActionListener(listener);
                }
                // Add action listener to close the details
                ok.addActionListener(e -> closeDetailsPanel());
                ok.setText("");
            }
            
            // Create modal overlay to block all clicks except on detailpanel
            if (modalOverlay == null) {
                modalOverlay = new javax.swing.JPanel();
                modalOverlay.setBackground(new java.awt.Color(0, 0, 0, 100)); // Semi-transparent black
                modalOverlay.setOpaque(false);
                
                // Add mouse listener to block all clicks on the overlay
                modalOverlay.addMouseListener(new java.awt.event.MouseAdapter() {
                    @Override
                    public void mouseClicked(java.awt.event.MouseEvent e) {
                        // Block all clicks - do nothing
                        e.consume();
                    }
                });
                
                // Add mouse motion listener to block drag events too
                modalOverlay.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
                    @Override
                    public void mouseDragged(java.awt.event.MouseEvent e) {
                        e.consume();
                    }
                });
                
                add(modalOverlay);
            }
            
            // Position and show the modal overlay to cover everything
            modalOverlay.setBounds(0, 0, getWidth(), getHeight());
            modalOverlay.setVisible(true);
            
            // Position the detailpanel in the center of the history area as a popup
            detailpanel.setBounds(50, 200, 266, 400); // Centered in the history area
            detailpanel.setVisible(true);
            
            // Make sure detailpanel is on top of the modal overlay
            setComponentZOrder(modalOverlay, 1); // Behind detailpanel
            setComponentZOrder(detailpanel, 0);   // On top
        }
        
        // Make sure the background stays behind
        if (jLabel1 != null) {
            setComponentZOrder(jLabel1, getComponentCount() - 1);
        }
        
        revalidate();
        repaint();
    }
    
    private void closeDetailsPanel() {
        // Hide the designed detailpanel
        if (detailpanel != null) {
            detailpanel.setVisible(false);
        }
        
        // Hide the modal overlay to restore click functionality
        if (modalOverlay != null) {
            modalOverlay.setVisible(false);
        }
        
        revalidate();
        repaint();
    }
    
    // Setup custom code - like ChargeHistory
    private void setupCustomCode() {
        try {
            // Set initial text for labels (safe operations only)
            if (time != null) time.setText("Loading...");
            if (amount != null) amount.setText("₱0.00");
            if (date != null) date.setText("No Date");
            if (type != null) type.setText("No Transactions");
            if (description != null) description.setText("0 transactions");
            
            // Initially hide the detailpanel
            if (detailpanel != null) {
                detailpanel.setVisible(false);
            }
            
            // Position history1 outside visible area (it's used as template)
            if (history1 != null) {
                history1.setBounds(400, 160, 310, 80); // Keep it outside view
                history1.setVisible(true);
            }
            
        } catch (Exception e) {
            System.err.println("Error in setupCustomCode: " + e.getMessage());
        }
    }
    
    /**
     * Fix after design change
     */
    public void fixAfterDesignChange() {
        SwingUtilities.invokeLater(() -> {
            setupCustomCode();
            if (currentUsername != null) {
                refreshHistoryDisplay();
            }
        });
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
                    java.awt.Window window = SwingUtilities.getWindowAncestor(WalletHistory.this);
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

    
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        closeButton = new javax.swing.JButton();
        mainHistoryContainer = new javax.swing.JPanel();
        historyScrollPane = new javax.swing.JScrollPane();
        historyPanel = new javax.swing.JPanel();
        history1 = new javax.swing.JPanel();
        details = new javax.swing.JButton();
        time = new javax.swing.JLabel();
        amount = new javax.swing.JLabel();
        date = new javax.swing.JLabel();
        type = new javax.swing.JLabel();
        description = new javax.swing.JLabel();
        detailpanel = new javax.swing.JPanel();
        transactionType = new javax.swing.JLabel();
        transactionAmount = new javax.swing.JLabel();
        transactionDesc = new javax.swing.JLabel();
        refId = new javax.swing.JLabel();
        newBalance = new javax.swing.JLabel();
        username = new javax.swing.JLabel();
        transactionDate = new javax.swing.JLabel();
        transactionTime = new javax.swing.JLabel();
        ok = new javax.swing.JToggleButton();
        ICONdetailPanel = new javax.swing.JLabel();
        headerLabel = new javax.swing.JLabel();
        profilebutton = new javax.swing.JButton();
        historybutton = new javax.swing.JButton();
        linkbutton = new javax.swing.JButton();
        charge = new javax.swing.JButton();
        homebutton = new javax.swing.JButton();
        jLabel1 = new javax.swing.JLabel();

        setMaximumSize(new java.awt.Dimension(370, 750));
        setPreferredSize(new java.awt.Dimension(370, 750));
        setLayout(null);

        closeButton.setBorder(null);
        closeButton.setBorderPainted(false);
        closeButton.setContentAreaFilled(false);
        closeButton.setFocusPainted(false);
        closeButton.setRequestFocusEnabled(false);
        closeButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                closeButtonActionPerformed(evt);
            }
        });
        add(closeButton);
        closeButton.setBounds(10, 50, 40, 30);

        mainHistoryContainer.setBackground(new java.awt.Color(255, 255, 255));
        mainHistoryContainer.setOpaque(false);

        historyScrollPane.setBackground(new java.awt.Color(255, 255, 255));
        historyScrollPane.setBorder(null);
        historyScrollPane.setHorizontalScrollBarPolicy(javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        historyScrollPane.setVerticalScrollBarPolicy(javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
        historyScrollPane.setOpaque(false);

        historyPanel.setBackground(new java.awt.Color(255, 255, 255));
        historyPanel.setLayout(new javax.swing.BoxLayout(historyPanel, javax.swing.BoxLayout.Y_AXIS));
        historyScrollPane.setViewportView(historyPanel);

        javax.swing.GroupLayout mainHistoryContainerLayout = new javax.swing.GroupLayout(mainHistoryContainer);
        mainHistoryContainer.setLayout(mainHistoryContainerLayout);
        mainHistoryContainerLayout.setHorizontalGroup(
            mainHistoryContainerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(mainHistoryContainerLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(historyScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 308, Short.MAX_VALUE)
                .addContainerGap())
        );
        mainHistoryContainerLayout.setVerticalGroup(
            mainHistoryContainerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(mainHistoryContainerLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(historyScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 518, Short.MAX_VALUE)
                .addContainerGap())
        );

        add(mainHistoryContainer);
        mainHistoryContainer.setBounds(20, 130, 320, 530);

        history1.setBackground(new java.awt.Color(255, 255, 255));
        history1.setBorder(javax.swing.BorderFactory.createMatteBorder(0, 0, 1, 0, new java.awt.Color(0, 0, 0)));
        history1.setLayout(null);

        details.setBorder(null);
        details.setBorderPainted(false);
        details.setContentAreaFilled(false);
        details.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                detailsActionPerformed(evt);
            }
        });
        history1.add(details);
        details.setBounds(0, 0, 310, 80);

        time.setText("time");
        history1.add(time);
        time.setBounds(20, 20, 80, 16);

        amount.setFont(new java.awt.Font("Segoe UI Semibold", 0, 12)); // NOI18N
        amount.setText("Amount");
        history1.add(amount);
        amount.setBounds(220, 40, 70, 16);

        date.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        date.setText("Date");
        history1.add(date);
        date.setBounds(20, 0, 120, 20);

        type.setText("Transaction Type");
        history1.add(type);
        type.setBounds(20, 40, 210, 16);

        description.setBackground(new java.awt.Color(0, 0, 0));
        description.setText("Description");
        history1.add(description);
        description.setBounds(20, 60, 280, 16);

        add(history1);
        history1.setBounds(400, 160, 310, 80);

        detailpanel.setBackground(new java.awt.Color(255, 255, 255));
        detailpanel.setOpaque(false);
        detailpanel.setLayout(null);

        transactionType.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        transactionType.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
        transactionType.setText("Type");
        transactionType.setToolTipText("");
        transactionType.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        detailpanel.add(transactionType);
        transactionType.setBounds(90, 85, 150, 16);

        transactionAmount.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        transactionAmount.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
        transactionAmount.setText("Amount");
        transactionAmount.setToolTipText("");
        transactionAmount.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        detailpanel.add(transactionAmount);
        transactionAmount.setBounds(95, 110, 150, 16);

        transactionDesc.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        transactionDesc.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
        transactionDesc.setText("Description");
        transactionDesc.setToolTipText("");
        transactionDesc.setHorizontalTextPosition(javax.swing.SwingConstants.LEFT);
        detailpanel.add(transactionDesc);
        transactionDesc.setBounds(70, 138, 170, 16);

        refId.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        refId.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
        refId.setText("Reference");
        refId.setToolTipText("");
        detailpanel.add(refId);
        refId.setBounds(90, 245, 150, 16);

        newBalance.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        newBalance.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
        newBalance.setText("Balance");
        newBalance.setToolTipText("");
        newBalance.setHorizontalTextPosition(javax.swing.SwingConstants.LEFT);
        detailpanel.add(newBalance);
        newBalance.setBounds(90, 273, 150, 16);

        username.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        username.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
        username.setText("User");
        username.setToolTipText("");
        username.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        detailpanel.add(username);
        username.setBounds(90, 170, 150, 16);

        transactionDate.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        transactionDate.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
        transactionDate.setText("Date");
        transactionDate.setToolTipText("");
        transactionDate.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        detailpanel.add(transactionDate);
        transactionDate.setBounds(90, 195, 150, 16);

        transactionTime.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        transactionTime.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
        transactionTime.setText("Time");
        transactionTime.setToolTipText("");
        transactionTime.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        detailpanel.add(transactionTime);
        transactionTime.setBounds(90, 220, 150, 16);

        ok.setBorder(null);
        ok.setBorderPainted(false);
        ok.setContentAreaFilled(false);
        ok.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                okActionPerformed(evt);
            }
        });
        detailpanel.add(ok);
        ok.setBounds(20, 330, 220, 40);

        ICONdetailPanel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/cephra/Cephra Images/TransacWalletDetailsPanelPOP.png"))); // NOI18N
        detailpanel.add(ICONdetailPanel);
        ICONdetailPanel.setBounds(0, 0, 266, 410);

        headerLabel.setFont(new java.awt.Font("Segoe UI", 1, 16)); // NOI18N
        headerLabel.setForeground(new java.awt.Color(51, 51, 51));
        headerLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        headerLabel.setText("Transaction Details");
        detailpanel.add(headerLabel);
        headerLabel.setBounds(10, 70, 226, 25);

        add(detailpanel);
        detailpanel.setBounds(430, 303, 266, 410);

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
        profilebutton.setBounds(280, 680, 40, 40);

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

        homebutton.setBorder(null);
        homebutton.setBorderPainted(false);
        homebutton.setContentAreaFilled(false);
        homebutton.setFocusPainted(false);
        homebutton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                homebuttonActionPerformed(evt);
            }
        });
        add(homebutton);
        homebutton.setBounds(150, 680, 40, 40);

        jLabel1.setIcon(new javax.swing.ImageIcon(getClass().getResource("/cephra/Cephra Images/wallethistory.png"))); // NOI18N
        add(jLabel1);
        jLabel1.setBounds(-15, 0, 398, 750);
    }// </editor-fold>//GEN-END:initComponents

    // CUSTOM CODE - DO NOT REMOVE - NetBeans will regenerate form code but this method should be preserved
    // Setup label position to prevent NetBeans from changing it
    private void setupLabelPosition() {
        if (jLabel1 != null) {
            jLabel1.setBounds(0, 0, 370, 750);
        }
    }

    private void detailsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_detailsActionPerformed
        if (currentTransactionEntry != null) {
            showWalletTransactionDetails(currentTransactionEntry);
        }
    }//GEN-LAST:event_detailsActionPerformed

    private void okActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_okActionPerformed
       
    }//GEN-LAST:event_okActionPerformed

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

    private void homebuttonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_homebuttonActionPerformed
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
    }//GEN-LAST:event_homebuttonActionPerformed

    private void closeButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_closeButtonActionPerformed
  
                goBackToPreviousPanel();
                
    }//GEN-LAST:event_closeButtonActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel ICONdetailPanel;
    private javax.swing.JLabel amount;
    private javax.swing.JButton charge;
    private javax.swing.JButton closeButton;
    private javax.swing.JLabel date;
    private javax.swing.JLabel description;
    private javax.swing.JPanel detailpanel;
    private javax.swing.JButton details;
    private javax.swing.JLabel headerLabel;
    private javax.swing.JPanel history1;
    private javax.swing.JPanel historyPanel;
    private javax.swing.JScrollPane historyScrollPane;
    private javax.swing.JButton historybutton;
    private javax.swing.JButton homebutton;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JButton linkbutton;
    private javax.swing.JPanel mainHistoryContainer;
    private javax.swing.JLabel newBalance;
    private javax.swing.JToggleButton ok;
    private javax.swing.JButton profilebutton;
    private javax.swing.JLabel refId;
    private javax.swing.JLabel time;
    private javax.swing.JLabel transactionAmount;
    private javax.swing.JLabel transactionDate;
    private javax.swing.JLabel transactionDesc;
    private javax.swing.JLabel transactionTime;
    private javax.swing.JLabel transactionType;
    private javax.swing.JLabel type;
    private javax.swing.JLabel username;
    // End of variables declaration//GEN-END:variables
    
    // Custom modal overlay to block clicks
  

    @Override
    public void addNotify() {
        super.addNotify();
        javax.swing.SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                setupLabelPosition();
                // Ensure background stays behind controls
                if (jLabel1 != null) {
                    setComponentZOrder(jLabel1, getComponentCount() - 1);
                }
            }
        });
    }
    
    @Override
    public void removeNotify() {
        // Clean up resources when panel is removed
        super.removeNotify();
        // Unregister from WalletHistoryManager when removed from UI
        cephra.Phone.Utilities.WalletHistoryManager.removeWalletHistoryUpdateListener(this);
    }
}
