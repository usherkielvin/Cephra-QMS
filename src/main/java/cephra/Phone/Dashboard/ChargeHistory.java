package cephra.Phone.Dashboard;

import cephra.Phone.Utilities.DetailLabelResizer;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

public class ChargeHistory extends javax.swing.JPanel implements cephra.Phone.Utilities.HistoryManager.HistoryUpdateListener {
    
    private String currentUsername;
    private cephra.Phone.Utilities.HistoryManager.HistoryEntry currentHistoryEntry;
    private javax.swing.JPanel modalOverlay;

    public ChargeHistory() {
        initComponents();
        setPreferredSize(new java.awt.Dimension(370, 750));
        setSize(370, 750);
        setupLabelPosition();
        makeDraggable();
        ICON.setName("ICON");
        
        DetailLabelResizer.resizeLabelsInPanel(detailpanel);
        
        cephra.Phone.Utilities.HistoryManager.addHistoryUpdateListener(this);
        setupCustomCode();
        setupScrollableHistoryContent();
        
        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentShown(java.awt.event.ComponentEvent e) {
                refreshHistoryDisplay();
            }
        });
    }
    
    
    
    
    
    
    /**
     * Setup scrollable history content using your designed pink history panel with labels
     */
    private void setupScrollableHistoryContent() {
        // Use SwingUtilities.invokeLater to ensure everything is ready after initComponents
        javax.swing.SwingUtilities.invokeLater(() -> {
            try {
                // Your pink history panel is already set up in initComponents with your labels:
                // time, price, date, type, chargetime
                if (history != null) {
                    
                    // Your panel is already in the scroll pane thanks to initComponents
                    // Keep your exact design and layout - don't change anything!
                    
                } else {
                    return;
                }
                
                // Configure scroll pane behavior (allow vertical scrolling; hide bars visually)
                historyScrollPane.setVerticalScrollBarPolicy(javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
                historyScrollPane.setHorizontalScrollBarPolicy(javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
                historyScrollPane.getVerticalScrollBar().setUnitIncrement(16);
                
                // Hide vertical scrollbar (visuals) but keep scrolling behavior
                if (historyScrollPane.getVerticalScrollBar() != null) {
                    historyScrollPane.getVerticalScrollBar().setPreferredSize(new Dimension(0, 0));
                }
                if (historyScrollPane.getHorizontalScrollBar() != null) {
                    historyScrollPane.getHorizontalScrollBar().setPreferredSize(new Dimension(0, 0));
                }
                
                historyScrollPane.setWheelScrollingEnabled(true);
                historyScrollPane.putClientProperty("JScrollPane.fastWheelScrolling", Boolean.TRUE);
                if (historyScrollPane.getViewport() != null) {
                    historyScrollPane.getViewport().setOpaque(false);
                }

                // Add padding so content isn't clipped (reserve space on right & bottom)
                if (history != null) {
                    java.awt.Insets insets = history.getBorder() instanceof javax.swing.border.EmptyBorder
                        ? ((javax.swing.border.EmptyBorder) history.getBorder()).getBorderInsets()
                        : new java.awt.Insets(0, 0, 0, 0);
                    int top = insets.top;
                    int left = insets.left;
                    int bottom = Math.max(12, insets.bottom);
                    int right = Math.max(10, insets.right);
                    history.setBorder(javax.swing.BorderFactory.createEmptyBorder(top, left, bottom, right));
                }
                
                // Force a repaint to ensure everything displays correctly
                historyScrollPane.revalidate();
                historyScrollPane.repaint();
                this.revalidate();
                this.repaint();
                
                
                // Load history entries now that the panel is ready
                loadHistoryEntries();
                
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
    
    
    private void loadHistoryEntries() {
        // Get current user's history
        currentUsername = cephra.Database.CephraDB.getCurrentUsername();
        refreshHistoryDisplay();
    }
    
    public void refreshHistoryDisplay() {
        // Check if your designed panels are initialized before using them
        if (history == null || history1 == null) {
            return;
        }
        
        
        // Get current user's history (now includes admin history entries)
        java.util.List<cephra.Phone.Utilities.HistoryManager.HistoryEntry> entries = cephra.Phone.Utilities.HistoryManager.getUserHistory(currentUsername);
        
        // Debug information
        
        if (entries.isEmpty()) {
            // Show "No history" message in your designed labels
            time.setText("No Data");
            price.setText("₱0.00");
            date.setText("No Date");
            type.setText("No Service");
            chargetime.setText("0 mins");
            currentHistoryEntry = null;  // No entry to show details for
            
            // Debug: Check if there are any charging history records in database
        } else {
            // Clear the container first to add all history entries
            history.removeAll();
            
            // Create a copy of your history1 panel for each entry
            for (int i = 0; i < entries.size(); i++) {
                cephra.Phone.Utilities.HistoryManager.HistoryEntry entry = entries.get(i);
                
                // Create a panel that looks like your history1 panel
                JPanel entryPanel = createHistoryPanelLikeHistory1(entry);
                history.add(entryPanel);
                
                // Add spacing between entries (except for the last one)
                if (i < entries.size() - 1) {
                    history.add(Box.createRigidArea(new Dimension(0, 8)));
                }
            }
            
            // Store the first entry for details button
            currentHistoryEntry = entries.get(0);
            
        }
        
        // Ensure proper repaint
        SwingUtilities.invokeLater(() -> {
            history1.revalidate();
            history1.repaint();
            history.revalidate();
            history.repaint();
        });
    }
    
    @Override
    public void onHistoryUpdated(String username) {
        if (username != null && username.equals(currentUsername)) {
            SwingUtilities.invokeLater(this::refreshHistoryDisplay);
        } else {
        }
    }
    
    /**
     * Creates a panel that looks exactly like your designed history1 panel
     */
    private JPanel createHistoryPanelLikeHistory1(final cephra.Phone.Utilities.HistoryManager.HistoryEntry entry) {
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
        createHistoryComponents(panel, entry);
        
        return panel;
    }
    
    private void createHistoryComponents(JPanel panel, final cephra.Phone.Utilities.HistoryManager.HistoryEntry entry) {
        // Time label
        JLabel timeClone = new JLabel(entry.getFormattedTime());
        if (time != null) {
            try {
                timeClone.setBounds(time.getBounds());
                timeClone.setFont(time.getFont());
                timeClone.setForeground(time.getForeground());
                timeClone.setBackground(time.getBackground());
                timeClone.setOpaque(time.isOpaque());
            } catch (Exception e) {
                timeClone.setBounds(10, 20, 80, 16);
                timeClone.setFont(new Font("Arial", Font.PLAIN, 12));
            }
        } else {
            timeClone.setBounds(10, 20, 80, 16);
            timeClone.setFont(new Font("Arial", Font.PLAIN, 12));
        }
        panel.add(timeClone);
        
        // Price label
        JLabel priceClone = new JLabel(entry.getTotal());
        if (price != null) {
            try {
                priceClone.setBounds(price.getBounds());
                priceClone.setFont(price.getFont());
                priceClone.setForeground(price.getForeground());
                priceClone.setBackground(price.getBackground());
                priceClone.setOpaque(price.isOpaque());
            } catch (Exception e) {
                priceClone.setBounds(220, 30, 70, 16);
                priceClone.setFont(new Font("Arial", Font.PLAIN, 12));
            }
        } else {
            priceClone.setBounds(220, 30, 70, 16);
            priceClone.setFont(new Font("Arial", Font.PLAIN, 12));
        }
        panel.add(priceClone);
        
        // Date label
        JLabel dateClone = new JLabel(entry.getFormattedDate());
        if (date != null) {
            try {
                dateClone.setBounds(date.getBounds());
                dateClone.setFont(date.getFont());
                dateClone.setForeground(date.getForeground());
                dateClone.setBackground(date.getBackground());
                dateClone.setOpaque(date.isOpaque());
            } catch (Exception e) {
                dateClone.setBounds(10, 0, 120, 20);
                dateClone.setFont(new Font("Arial", Font.BOLD, 14));
            }
        } else {
            dateClone.setBounds(10, 0, 120, 20);
            dateClone.setFont(new Font("Arial", Font.BOLD, 14));
        }
        panel.add(dateClone);
        
        // Type label - show full service type names
        String serviceType = entry.getServiceType();
        String displayType;
        
        // Clean service type and map to proper display names (handle payment method suffix)
        if (serviceType != null) {
            String cleanServiceType = serviceType.toLowerCase();
            
            // Remove payment method suffix like " (cash)", " (gcash)", etc.
            if (cleanServiceType.contains(" (")) {
                cleanServiceType = cleanServiceType.substring(0, cleanServiceType.indexOf(" ("));
            }
            
            // Check if this is a priority ticket by ticket ID
            String ticketId = entry.getTicketId();
            boolean isPriorityTicket = false;
            if (ticketId != null) {
                String ticketIdUpper = ticketId.toUpperCase();
                isPriorityTicket = ticketIdUpper.startsWith("FCHP") || ticketIdUpper.startsWith("NCHP");
            }
            
            switch (cleanServiceType) {
                case "fast charging":
                    displayType = isPriorityTicket ? "Prio Fast Charging" : "Fast Charging";
                    break;
                case "normal charging":
                    displayType = isPriorityTicket ? "Prio Normal Charging" : "Normal Charging";
                    break;
                case "fast charging priority":
                    displayType = "Prio Fast Charging";
                    break;
                case "normal charging priority":
                    displayType = "Prio Normal Charging";
                    break;
                case "fchp":
                    displayType = "Prio Fast Charging";
                    break;
                case "nchp":
                    displayType = "Prio Normal Charging";
                    break;
                default:
                    displayType = serviceType; // Fallback to original if not recognized
                    break;
            }
        } else {
            displayType = "Normal Charging"; // Default fallback
        }
        
        JLabel typeClone = new JLabel(displayType);
        if (type != null) {
            try {
                typeClone.setBounds(type.getBounds());
                typeClone.setFont(type.getFont());
                typeClone.setForeground(type.getForeground());
                typeClone.setBackground(type.getBackground());
                typeClone.setOpaque(type.isOpaque());
            } catch (Exception e) {
                typeClone.setBounds(10, 40, 210, 16);
                typeClone.setFont(new Font("Arial", Font.PLAIN, 12));
            }
        } else {
            typeClone.setBounds(10, 40, 210, 16);
            typeClone.setFont(new Font("Arial", Font.PLAIN, 12));
        }
        panel.add(typeClone);
        
        // Chargetime label
        JLabel chargetimeClone = new JLabel(entry.getChargingTime());
        if (chargetime != null) {
            try {
                chargetimeClone.setBounds(chargetime.getBounds());
                chargetimeClone.setFont(chargetime.getFont());
                chargetimeClone.setForeground(chargetime.getForeground());
                chargetimeClone.setBackground(chargetime.getBackground());
                chargetimeClone.setOpaque(chargetime.isOpaque());
            } catch (Exception e) {
                chargetimeClone.setBounds(10, 60, 220, 16);
                chargetimeClone.setFont(new Font("Arial", Font.PLAIN, 12));
            }
        } else {
            chargetimeClone.setBounds(10, 60, 220, 16);
            chargetimeClone.setFont(new Font("Arial", Font.PLAIN, 12));
        }
        panel.add(chargetimeClone);
        
        // Details button
        JButton detailsClone = new JButton();
        if (details != null) {
            try {
                detailsClone.setBounds(details.getBounds());
                detailsClone.setBorderPainted(details.isBorderPainted());
                detailsClone.setContentAreaFilled(details.isContentAreaFilled());
                detailsClone.setFocusPainted(details.isFocusPainted());
            } catch (Exception e) {
                detailsClone.setBounds(200, 0, 110, 80);
                detailsClone.setBorderPainted(false);
                detailsClone.setContentAreaFilled(false);
                detailsClone.setFocusPainted(false);
            }
        } else {
            detailsClone.setBounds(200, 0, 110, 80);
            detailsClone.setBorderPainted(false);
            detailsClone.setContentAreaFilled(false);
            detailsClone.setFocusPainted(false);
        }
        
        detailsClone.addActionListener(e -> {
            currentHistoryEntry = entry;
            showHistoryDetails(entry);
        });
        panel.add(detailsClone);
    }
    
    
    
    
/////
   
    private void showHistoryDetails(cephra.Phone.Utilities.HistoryManager.HistoryEntry entry) {
        
        // Update the designed detailpanel with the entry information
        if (detailpanel != null) {
            // Update ticket information - just the value
            if (ticket != null) {
                ticket.setText(entry.getTicketId());
            }
            
            // Update customer information - just the value
            if (Customer != null) {
                Customer.setText(cephra.Database.CephraDB.getCurrentUsername());
            }
            
            // Update service type - show full service type names
            if (paymentmethod != null) {
                String serviceType = entry.getServiceType();
                String displayType;
                
                // Clean service type and map to proper display names (handle payment method suffix)
                if (serviceType != null) {
                    String cleanServiceType = serviceType.toLowerCase();
                    
                    // Remove payment method suffix like " (cash)", " (gcash)", etc.
                    if (cleanServiceType.contains(" (")) {
                        cleanServiceType = cleanServiceType.substring(0, cleanServiceType.indexOf(" ("));
                    }
                    
                    // Check if this is a priority ticket by ticket ID
                    String ticketId = entry.getTicketId();
                    boolean isPriorityTicket = false;
                    if (ticketId != null) {
                        String ticketIdUpper = ticketId.toUpperCase();
                        isPriorityTicket = ticketIdUpper.startsWith("FCHP") || ticketIdUpper.startsWith("NCHP");
                    }
                    
                    switch (cleanServiceType) {
                        case "fast charging":
                            displayType = isPriorityTicket ? "Prio Fast Charging" : "Fast Charging";
                            break;
                        case "normal charging":
                            displayType = isPriorityTicket ? "Prio Normal Charging" : "Normal Charging";
                            break;
                        case "fast charging priority":
                            displayType = "Prio Fast Charging";
                            break;
                        case "normal charging priority":
                            displayType = "Prio Normal Charging";
                            break;
                        case "fchp":
                            displayType = "Prio Fast Charging";
                            break;
                        case "nchp":
                            displayType = "Prio Normal Charging";
                            break;
                        default:
                            displayType = serviceType; // Fallback to original if not recognized
                            break;
                    }
                } else {
                    displayType = "Normal Charging"; // Default fallback
                }
                
                paymentmethod.setText(displayType);
            }
            
            // Update kWh information (get from admin history record) - just the value
            String kWhValue = "32.80"; // Default value
            try {
                java.util.List<Object[]> adminRecords = cephra.Admin.Utilities.HistoryBridge.getRecordsForUser(cephra.Database.CephraDB.getCurrentUsername());
                if (adminRecords != null) {
                    for (Object[] record : adminRecords) {
                        if (record.length >= 7 && entry.getTicketId().equals(String.valueOf(record[0]))) {
                            kWhValue = String.valueOf(record[2]); // KWh is at index 2
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("Error getting kWh from admin history: " + e.getMessage());
            }
            if (kwh != null) {
                kwh.setText(kWhValue);
            }
            
            // Update charging time - just the value
            if (Chargingtime != null) {
                Chargingtime.setText(entry.getChargingTime());
            }
            
            // Update total price - just the value
            if (totalprice != null) {
                totalprice.setText(entry.getTotal());
            }
            
            try {
                java.util.List<Object[]> adminRecords = cephra.Admin.Utilities.HistoryBridge.getRecordsForUser(cephra.Database.CephraDB.getCurrentUsername());
                if (adminRecords != null) {
                    for (Object[] record : adminRecords) {
                        if (record.length >= 5 && entry.getTicketId().equals(String.valueOf(record[0]))) {
                            String servedByValue = String.valueOf(record[4]); // Served By is at index 4
                            if (servedByValue != null && !servedByValue.equals("null")) {
                            }
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("Error getting served by from admin history: " + e.getMessage());
            }
            if (server != null) {
            //    server.setText(servedBy);
             String username = cephra.Database.CephraDB.getCurrentUsername();
             String userPlateNumber = cephra.Database.CephraDB.getUserPlateNumber(username);
               server.setText(userPlateNumber);
               
            }
            
            // Update date - just the value
            if (dated != null) {
                dated.setText(entry.getFormattedDate());
            }
            
            // Update time - just the value
            if (timed != null) {
                timed.setText(entry.getFormattedTime());
            }
            
            // Update reference number - just the value
            String refNumber = entry.getReferenceNumber();
            if (refNumber == null || refNumber.trim().isEmpty() || refNumber.equals("null")) {
                refNumber = cephra.Admin.Utilities.QueueBridge.getTicketRefNumber(entry.getTicketId());
            }
            if (ref != null) {
                ref.setText(refNumber);
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
                    java.awt.Window window = SwingUtilities.getWindowAncestor(ChargeHistory.this);
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

        mainHistoryContainer = new javax.swing.JPanel();
        historyScrollPane = new javax.swing.JScrollPane();
        history = new javax.swing.JPanel();
        profilebutton = new javax.swing.JButton();
        charge = new javax.swing.JButton();
        homebutton = new javax.swing.JButton();
        linkbutton = new javax.swing.JButton();
        jLabel1 = new javax.swing.JLabel();
        history1 = new javax.swing.JPanel();
        details = new javax.swing.JButton();
        time = new javax.swing.JLabel();
        price = new javax.swing.JLabel();
        date = new javax.swing.JLabel();
        type = new javax.swing.JLabel();
        chargetime = new javax.swing.JLabel();
        detailpanel = new javax.swing.JPanel();
        ticket = new javax.swing.JLabel();
        Customer = new javax.swing.JLabel();
        paymentmethod = new javax.swing.JLabel();
        kwh = new javax.swing.JLabel();
        Chargingtime = new javax.swing.JLabel();
        totalprice = new javax.swing.JLabel();
        server = new javax.swing.JLabel();
        dated = new javax.swing.JLabel();
        timed = new javax.swing.JLabel();
        ref = new javax.swing.JLabel();
        ok = new javax.swing.JToggleButton();
        ICON = new javax.swing.JLabel();

        setMaximumSize(new java.awt.Dimension(370, 750));
        setPreferredSize(new java.awt.Dimension(370, 750));
        setLayout(null);

        mainHistoryContainer.setBackground(new java.awt.Color(255, 255, 255));
        mainHistoryContainer.setOpaque(false);

        historyScrollPane.setBackground(new java.awt.Color(255, 255, 255));
        historyScrollPane.setBorder(null);
        historyScrollPane.setHorizontalScrollBarPolicy(javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        historyScrollPane.setVerticalScrollBarPolicy(javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
        historyScrollPane.setOpaque(false);

        history.setBackground(new java.awt.Color(255, 255, 255));
        history.setLayout(new javax.swing.BoxLayout(history, javax.swing.BoxLayout.Y_AXIS));
        historyScrollPane.setViewportView(history);

        javax.swing.GroupLayout mainHistoryContainerLayout = new javax.swing.GroupLayout(mainHistoryContainer);
        mainHistoryContainer.setLayout(mainHistoryContainerLayout);
        mainHistoryContainerLayout.setHorizontalGroup(
            mainHistoryContainerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(mainHistoryContainerLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(historyScrollPane)
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
        mainHistoryContainer.setBounds(20, 130, 330, 530);

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
        profilebutton.setBounds(260, 680, 40, 40);

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
        charge.setBounds(30, 680, 50, 50);

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
        linkbutton.setBounds(90, 680, 60, 40);

        jLabel1.setIcon(new javax.swing.ImageIcon(getClass().getResource("/cephra/Cephra Images/HISTORY - if none.png"))); // NOI18N
        add(jLabel1);
        jLabel1.setBounds(-15, 0, 398, 750);

        history1.setBackground(new java.awt.Color(255, 255, 255));
        history1.setBorder(javax.swing.BorderFactory.createMatteBorder(0, 0, 1, 0, new java.awt.Color(0, 0, 0)));
        history1.setLayout(null);

        details.setBorderPainted(false);
        details.setContentAreaFilled(false);
        details.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                detailsActionPerformed(evt);
            }
        });
        history1.add(details);
        details.setBounds(0, -3, 310, 80);

        time.setText("time");
        history1.add(time);
        time.setBounds(20, 20, 80, 16);

        price.setFont(new java.awt.Font("Segoe UI Semibold", 0, 12)); // NOI18N
        price.setText("Price");
        history1.add(price);
        price.setBounds(220, 40, 70, 16);

        date.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        date.setText("Date");
        history1.add(date);
        date.setBounds(20, 0, 120, 20);

        type.setText("jLabel2");
        history1.add(type);
        type.setBounds(20, 40, 210, 16);

        chargetime.setBackground(new java.awt.Color(0, 0, 0));
        chargetime.setText("jLabel2");
        history1.add(chargetime);
        chargetime.setBounds(20, 60, 220, 16);

        add(history1);
        history1.setBounds(400, 160, 310, 80);

        detailpanel.setBackground(new java.awt.Color(255, 255, 255));
        detailpanel.setBorder(new javax.swing.border.MatteBorder(null));
        detailpanel.setLayout(null);

        ticket.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
        ticket.setText("jLabel3");
        ticket.setToolTipText("");
        ticket.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        detailpanel.add(ticket);
        ticket.setBounds(120, 65, 100, 16);

        Customer.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
        Customer.setText("jLabel3");
        Customer.setToolTipText("");
        Customer.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        detailpanel.add(Customer);
        Customer.setBounds(120, 95, 100, 16);

        paymentmethod.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
        paymentmethod.setText("NC (GCash)");
        paymentmethod.setToolTipText("");
        paymentmethod.setHorizontalTextPosition(javax.swing.SwingConstants.LEFT);
        detailpanel.add(paymentmethod);
        paymentmethod.setBounds(120, 120, 100, 16);

        kwh.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
        kwh.setText("jLabel3");
        kwh.setToolTipText("");
        detailpanel.add(kwh);
        kwh.setBounds(120, 150, 100, 16);

        Chargingtime.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
        Chargingtime.setText("jLabel3");
        Chargingtime.setToolTipText("");
        Chargingtime.setHorizontalTextPosition(javax.swing.SwingConstants.LEFT);
        detailpanel.add(Chargingtime);
        Chargingtime.setBounds(120, 176, 100, 16);

        totalprice.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
        totalprice.setText("jLabel3");
        totalprice.setToolTipText("");
        totalprice.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        detailpanel.add(totalprice);
        totalprice.setBounds(120, 204, 100, 16);

        server.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
        server.setText("jLabel3");
        server.setToolTipText("");
        server.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        detailpanel.add(server);
        server.setBounds(120, 228, 100, 16);

        dated.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
        dated.setText("jLabel3");
        dated.setToolTipText("");
        dated.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        detailpanel.add(dated);
        dated.setBounds(120, 256, 100, 16);

        timed.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
        timed.setText("jLabel3");
        timed.setToolTipText("");
        timed.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        detailpanel.add(timed);
        timed.setBounds(120, 280, 100, 16);

        ref.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
        ref.setText("jLabel3");
        ref.setToolTipText("");
        ref.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        detailpanel.add(ref);
        ref.setBounds(120, 309, 100, 16);

        ok.setBorder(null);
        ok.setBorderPainted(false);
        ok.setContentAreaFilled(false);
        ok.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                okActionPerformed(evt);
            }
        });
        detailpanel.add(ok);
        ok.setBounds(20, 340, 220, 30);

        ICON.setIcon(new javax.swing.ImageIcon(getClass().getResource("/cephra/Cephra Images/ChargingDetailsPOP.png"))); // NOI18N
        ICON.setText("jLabel3");
        detailpanel.add(ICON);
        ICON.setBounds(0, 0, 266, 400);

        add(detailpanel);
        detailpanel.setBounds(430, 303, 266, 410);
    }// </editor-fold>//GEN-END:initComponents

    // CUSTOM CODE SECTION
    
    // Setup label position
    private void setupLabelPosition() {
        if (jLabel1 != null) {
            jLabel1.setBounds(0, 0, 370, 750);
        }
    }
    
    // SIMPLIFIED CUSTOM CODE - NETBEANS-SAFE
    private void setupCustomCode() {
        try {
            // Set initial text for labels (safe operations only)
            if (time != null) time.setText("Loading...");
            if (price != null) price.setText("₱0.00");
            if (date != null) date.setText("No Date");
            if (type != null) type.setText("No Service");
            if (chargetime != null) chargetime.setText("0 mins");
            
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

    private void detailsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_detailsActionPerformed
        if (currentHistoryEntry != null) {
            showHistoryDetails(currentHistoryEntry);
        }
    }//GEN-LAST:event_detailsActionPerformed

    private void okActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_okActionPerformed
       
    }//GEN-LAST:event_okActionPerformed


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel Chargingtime;
    private javax.swing.JLabel Customer;
    private javax.swing.JLabel ICON;
    private javax.swing.JButton charge;
    private javax.swing.JLabel chargetime;
    private javax.swing.JLabel date;
    private javax.swing.JLabel dated;
    private javax.swing.JPanel detailpanel;
    private javax.swing.JButton details;
    private javax.swing.JPanel history;
    private javax.swing.JPanel history1;
    private javax.swing.JScrollPane historyScrollPane;
    private javax.swing.JButton homebutton;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel kwh;
    private javax.swing.JButton linkbutton;
    private javax.swing.JPanel mainHistoryContainer;
    private javax.swing.JToggleButton ok;
    private javax.swing.JLabel paymentmethod;
    private javax.swing.JLabel price;
    private javax.swing.JButton profilebutton;
    private javax.swing.JLabel ref;
    private javax.swing.JLabel server;
    private javax.swing.JLabel ticket;
    private javax.swing.JLabel time;
    private javax.swing.JLabel timed;
    private javax.swing.JLabel totalprice;
    private javax.swing.JLabel type;
    // End of variables declaration//GEN-END:variables

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
        // Unregister as listener when panel is removed
        cephra.Phone.Utilities.HistoryManager.removeHistoryUpdateListener(this);
        super.removeNotify();
    }
    
    // Test method to add sample history entry (for testing purposes)
    public void addTestHistoryEntry() {
        String currentUser = cephra.Database.CephraDB.getCurrentUsername();
        if (currentUser != null) {
            // History entries are created from database data, not added manually
            // This method is for testing - just refresh the display
            refreshHistoryDisplay();
        }
    }
}
