package cephra.Admin;

import java.awt.*;
import javax.swing.*;
import javax.swing.table.*;

import cephra.Admin.Utilities.ButtonHoverEffect;
import cephra.Admin.Utilities.jtableDesign;
import cephra.Frame.Monitor;

public class Queue extends javax.swing.JPanel {
    private static Monitor monitorInstance;
    private JButton[] gridButtons;
    private int buttonCount = 0;
    
    // Static notification instance to allow updates
    private static cephra.Phone.Popups.Notification staticNotification = null;

    public Queue() {
        initComponents();
        setPreferredSize(new java.awt.Dimension(1000, 750));
        setSize(1000, 750);
        
        // Initialize custom components after NetBeans generated code
        initializeCustomComponents();
    }

    private void initializeCustomComponents() {
        setupDateTimeTimer();    
        
        jtableDesign.apply(queTab);
        jtableDesign.makeScrollPaneTransparent(jScrollPane1);

        JTableHeader header = queTab.getTableHeader();
        header.setFont(new Font("Sogie UI", Font.BOLD, 16));
        
        // Create a custom table model that makes only Action and Payment columns editable
        DefaultTableModel originalModel = (DefaultTableModel) queTab.getModel();
        DefaultTableModel customModel = new DefaultTableModel() {
            @Override
            public boolean isCellEditable(int row, int column) {
                // Only allow editing of Action and Payment columns
                String columnName = getColumnName(column);
                return "Action".equals(columnName) || "Payment".equals(columnName);
            }
            
            @Override
            public void setValueAt(Object value, int row, int column) {
                // Only allow setting values for Action and Payment columns
                String columnName = getColumnName(column);
                if ("Action".equals(columnName) || "Payment".equals(columnName)) {
                    super.setValueAt(value, row, column);
                }
                // Ignore all other column edits
            }
        };
        
        // Copy column names from original model
        for (int i = 0; i < originalModel.getColumnCount(); i++) {
            customModel.addColumn(originalModel.getColumnName(i));
        }
        
        // Set the custom model
        queTab.setModel(customModel);
        
        // Make the table non-editable by default
        queTab.setDefaultEditor(Object.class, null);
        
        // Register the custom model so other modules can add rows
        cephra.Admin.Utilities.QueueBridge.registerModel(customModel);
        
        // Setup Action column with an invisible button that shows text "Proceed"
        // Delay setup to ensure table model is ready
        SwingUtilities.invokeLater(() -> {
            setupActionColumn();
            setupPaymentColumn();
            setupTicketColumn();
        });
        jPanel1.setOpaque(false);
        
        // Monitor instance initialization (commented out)
        // if (monitorInstance == null) {
        //     monitorInstance = new cephra.Frame.Monitor();
        //     monitorInstance.setVisible(true);
        // }
        
        // Initialize grid buttons
        gridButtons = new JButton[] {
            jButton1, jButton2, jButton3, jButton4, jButton5,
            jButton6, jButton7, jButton8
        };
        
        // Initially hide all grid buttons
        for (JButton button : gridButtons) {
            button.setVisible(false);
        }
        
        // Setup next buttons
        setupNextButtons();

        queTab.getModel().addTableModelListener(e -> {
            updateStatusCounters();
            if (e.getType() == javax.swing.event.TableModelEvent.UPDATE) {
                checkAndRemovePaidRows();
            }
        });
        updateStatusCounters();
        
        initializeGridDisplays();
        initializeWaitingGridFromDatabase();
        cephra.Admin.BayManagement.registerQueueInstance(this);

        ButtonHoverEffect.addHoverEffect(Baybutton);
        ButtonHoverEffect.addHoverEffect(businessbutton);
        ButtonHoverEffect.addHoverEffect(exitlogin);
        ButtonHoverEffect.addHoverEffect(staffbutton);
        ButtonHoverEffect.addHoverEffect(historybutton);
        
        setupPeriodicRefresh();
    }

    private void updateStatusCounters() {
        int waiting = 0;
        int charging = 0;
        for (int i = 0; i < queTab.getRowCount(); i++) {
            Object s = queTab.getValueAt(i, getColumnIndex("Status"));
            String status = s == null ? "" : String.valueOf(s).trim();
            if ("Waiting".equalsIgnoreCase(status)) waiting++;
            else if ("Charging".equalsIgnoreCase(status)) charging++;
        }
        
        int paidCumulative = cephra.Admin.Utilities.QueueBridge.getTotalPaidCount();
        Waitings.setText(String.valueOf(waiting));
        Charging.setText(String.valueOf(charging));
        Paid.setText(String.valueOf(paidCumulative));
    }

    private void setupActionColumn() {
        final int actionCol = getColumnIndex("Action");
        final int statusCol = getColumnIndex("Status");
        if (actionCol < 0 || statusCol < 0) return;

        queTab.getColumnModel().getColumn(actionCol).setCellRenderer(new TableCellRenderer() {
            private final JButton button = createFlatButton();
            private final JLabel empty = new JLabel("");

            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                Object ticketVal = table.getValueAt(row, getColumnIndex("Ticket"));
                boolean hasTicket = ticketVal != null && String.valueOf(ticketVal).trim().length() > 0;
                
                int paymentCol = getColumnIndex("Payment");
                boolean isPaid = false;
                if (paymentCol >= 0 && hasTicket) {
                    Object paymentVal = table.getValueAt(row, paymentCol);
                    String paymentStatus = paymentVal == null ? "" : String.valueOf(paymentVal).trim();
                    isPaid = "Paid".equalsIgnoreCase(paymentStatus);
                }
                
                if (hasTicket && !isPaid) {
                    button.setText("Proceed");
                    button.setForeground(new java.awt.Color(255, 255, 255));
                    button.setBackground(new java.awt.Color(0, 120, 215));
                    button.setOpaque(true);
                    return button;
                }
                return empty;
            }
        });

        queTab.getColumnModel().getColumn(actionCol).setCellEditor(new CombinedProceedEditor(statusCol));
    }

private class CombinedProceedEditor extends AbstractCellEditor implements TableCellEditor, java.awt.event.ActionListener {
        private final JButton button = createFlatButton();
        private int editingRow = -1;
        private final int statusColumnIndex;

    CombinedProceedEditor(int statusColumnIndex) {
            this.statusColumnIndex = statusColumnIndex;
            button.addActionListener(this);
        }

        @Override
        public Component getTableCellEditorComponent(javax.swing.JTable table, Object value, boolean isSelected, int row, int column) {
            editingRow = row;
            Object ticketVal = queTab.getValueAt(row, 0);
            boolean hasTicket = ticketVal != null && String.valueOf(ticketVal).trim().length() > 0;
            if (hasTicket) {
                button.setText("Proceed");
                return button;
            }
            return new JLabel("");
        }

        @Override
        public Object getCellEditorValue() {
            return "Proceed";
        }

        @Override
        public void actionPerformed(java.awt.event.ActionEvent e) {
            final int rowSnapshot = editingRow;
            javax.swing.SwingUtilities.invokeLater(() -> {
                stopCellEditing();
                if (rowSnapshot < 0) return;
                String ticket = String.valueOf(queTab.getValueAt(rowSnapshot, 0));
                int paymentCol = getColumnIndex("Payment");
                String status = String.valueOf(queTab.getValueAt(rowSnapshot, statusColumnIndex));

                try {
                    String payVal = paymentCol >= 0 ? String.valueOf(queTab.getValueAt(rowSnapshot, paymentCol)) : "";
                    if ("Complete".equalsIgnoreCase(status)
                        && ticket != null && !ticket.trim().isEmpty()
                        && (payVal == null || payVal.trim().isEmpty()
                            || "Pending".equalsIgnoreCase(payVal)
                            || "TopupRequired".equalsIgnoreCase(payVal))) {
                        ensurePaymentPending(ticket);
                        if (paymentCol >= 0) {
                            queTab.setValueAt("Pending", rowSnapshot, paymentCol);
                        }
                    }
                } catch (Exception ignore) {}

                int customerCol = Math.min(1, queTab.getColumnCount() - 1);
                String customer = String.valueOf(queTab.getValueAt(rowSnapshot, customerCol));

                try {
                    if ("Pending".equalsIgnoreCase(status)) {
                        int statusCol = getColumnIndex("Status");
                        if (statusCol >= 0) {
                            queTab.setValueAt("Waiting", rowSnapshot, statusCol);
                            try {
                                cephra.Database.CephraDB.updateQueueTicketStatus(ticket, "Waiting");
                                
                                boolean alreadyInWaitingGrid = cephra.Admin.BayManagement.isTicketInWaitingGrid(ticket);
                                
                                if (!alreadyInWaitingGrid) {
                                    int svcCol = getColumnIndex("Service");
                                    String serviceName = svcCol >= 0 ? String.valueOf(queTab.getValueAt(rowSnapshot, svcCol)) : "";
                                    String customerName = String.valueOf(queTab.getValueAt(rowSnapshot, Math.min(1, queTab.getColumnCount()-1)));
                                    int battery = cephra.Database.CephraDB.getUserBatteryLevel(customerName);
                                    cephra.Admin.BayManagement.addTicketToWaitingGrid(ticket, customerName, serviceName, battery);
                                }
                                
                                initializeWaitingGridFromDatabase();
                                String customerName = String.valueOf(queTab.getValueAt(rowSnapshot, Math.min(1, queTab.getColumnCount()-1)));
                                triggerNotificationForCustomer(customerName, "TICKET_WAITING", ticket, null);
                            } catch (Throwable ignore) {}
                        }
                        updateStatusCounters();
                        return;
                    }
                    if ("Waiting".equalsIgnoreCase(status)) {
                        // Get customer name for popup
                        String customerName = String.valueOf(queTab.getValueAt(rowSnapshot, Math.min(1, queTab.getColumnCount()-1)));
                        
                        // Show waiting bay popup to user (bay number will be determined after assignment)
                        cephra.Phone.Utilities.CustomPopupManager.showWaitingBayPopup(ticket, customerName, () -> {
                            // Bay assignment is now handled in executeCallback, this callback just updates the table
                            System.out.println("Queue callback: Bay assignment already handled, updating table status");
                            setTableStatusToChargingByTicket(ticket);
                        });
                        updateStatusCounters();
                        return;
                    }
                    if ("Charging".equalsIgnoreCase(status)) {
                        // Admin can only view charging status, cannot manually complete it
                        // Charging completion happens automatically when battery reaches 100%
                        javax.swing.JOptionPane.showMessageDialog(null, 
                            "Charging is in progress. Please wait for automatic completion when battery reaches 100%.", 
                            "Charging in Progress", 
                            javax.swing.JOptionPane.INFORMATION_MESSAGE);
                        return;
                    }
                    if ("Complete".equalsIgnoreCase(status)) {
                        String payment = paymentCol >= 0 ? String.valueOf(queTab.getValueAt(rowSnapshot, paymentCol)) : "";
                        
                        if ("Pending".equalsIgnoreCase(payment)) {
                            try {
                                boolean success = cephra.Phone.Popups.Pending_Payment.showPayPop(ticket, customer);
                                if (!success) { 
                                    System.err.println("Queue: PayPop failed to open for ticket " + ticket + " - user " + customer + " is not logged in to process payment"); 
                                }
                            } catch (Throwable t) {
                                System.err.println("Error showing PayPop: " + t.getMessage());
                            }
                            return;
                        } else if ("Paid".equalsIgnoreCase(payment)) {
                            boolean alreadyProcessed = cephra.Database.CephraDB.isPaymentAlreadyProcessed(ticket);
                            
                            if (alreadyProcessed) {
                                stopCellEditing();
                                try {
                                    cephra.Admin.Utilities.QueueBridge.removeTicket(ticket);
                                    hardRefreshTable();
                                } catch (Throwable t) {
                                    System.err.println("Error removing ticket via QueueBridge: " + t.getMessage());
                                }
                                return;
                            }
                            
                            try {
                                boolean success = cephra.Phone.Popups.Pending_Payment.showPayPop(ticket, customer);
                                if (!success) { 
                                    System.err.println("Queue: PayPop failed to open for ticket " + ticket + " - user " + customer + " is not logged in to process payment"); 
                                }
                            } catch (Throwable t) {
                                System.err.println("Error showing PayPop: " + t.getMessage());
                            }
                            return;
                        }
                    }
                } catch (Exception ex) {
                    System.err.println("Queue: Error in actionPerformed: " + ex.getMessage());
                    ex.printStackTrace();
                }
            });
        }
    }

    private void ensurePaymentPending(String ticket) {
        try {
            int paymentCol = getColumnIndex("Payment");
            int ticketCol = getColumnIndex("Ticket");
            if (paymentCol < 0 || ticketCol < 0) return;
            for (int row = 0; row < queTab.getRowCount(); row++) {
                Object v = queTab.getValueAt(row, ticketCol);
                if (v != null && ticket.equals(String.valueOf(v).trim())) {
                    queTab.setValueAt("Pending", row, paymentCol);
                    break;
                }
            }
        } catch (Throwable t) {
            System.err.println("Queue: ensurePaymentPending failed for ticket " + ticket + ": " + t.getMessage());
        }
    }

    private static JButton createFlatButton() {
        JButton b = new JButton();
        b.setBorderPainted(false);
        b.setContentAreaFilled(true);
        b.setFocusPainted(false);
        b.setOpaque(true);
        b.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        b.setForeground(new java.awt.Color(255, 255, 255));
        b.setBackground(new java.awt.Color(0, 120, 215));
        b.setText("Proceed");
        b.setPreferredSize(new java.awt.Dimension(80, 25));
        return b;
    }

    private int getColumnIndex(String name) {
        for (int i = 0; i < queTab.getColumnModel().getColumnCount(); i++) {
            if (name.equals(queTab.getColumnModel().getColumn(i).getHeaderValue())) {
                return i;
            }
        }
        return -1;
    }
    
    private void checkAndRemovePaidRows() {
        SwingUtilities.invokeLater(() -> {
            javax.swing.Timer timer = new javax.swing.Timer(500, _ -> {
                int paymentCol = getColumnIndex("Payment");
                int ticketCol = getColumnIndex("Ticket");
                
                if (paymentCol < 0 || ticketCol < 0) return;
                
                for (int i = queTab.getRowCount() - 1; i >= 0; i--) {
                    Object paymentVal = queTab.getValueAt(i, paymentCol);
                    String paymentStatus = paymentVal == null ? "" : String.valueOf(paymentVal).trim();
                    
                    if ("Paid".equalsIgnoreCase(paymentStatus)) {
                        Object ticketVal = queTab.getValueAt(i, ticketCol);
                        String ticket = ticketVal == null ? "" : String.valueOf(ticketVal).trim();
                        
                        if (!ticket.isEmpty()) {
                            try {
                                cephra.Admin.Utilities.QueueBridge.removeTicket(ticket);
                                hardRefreshTable();
                            } catch (Throwable t) {
                                System.err.println("Error auto-removing ticket via QueueBridge: " + t.getMessage());
                            }
                        }
                    }
                }
            });
            timer.setRepeats(false);
            timer.start();
        });
    }
    
    
    
    public void refreshEntirePanel() {
        SwingUtilities.invokeLater(() -> {
            this.repaint();
            this.revalidate();
            queTab.repaint();
            queTab.revalidate();
            
            if (jScrollPane1 != null) {
                jScrollPane1.repaint();
                jScrollPane1.revalidate();
            }
            
            if (this.getParent() != null) {
                this.getParent().repaint();
                this.getParent().revalidate();
            }
            
            updateStatusCounters();
        });
    }
    
    public void hardRefreshTable() {
        SwingUtilities.invokeLater(() -> {
            try {
                if (queTab.isEditing() && queTab.getRowCount() > 0) {
                    try {
                        queTab.getCellEditor().stopCellEditing();
                    } catch (Exception e) {
                        // Ignore cell editing errors during refresh
                    }
                }
                
                int oldRowCount = queTab.getRowCount();
                ((DefaultTableModel) queTab.getModel()).setRowCount(0);
                cephra.Admin.Utilities.QueueBridge.reloadFromDatabase();
                
                int newRowCount = queTab.getRowCount();
                
                queTab.clearSelection();
                queTab.repaint();
                queTab.revalidate();
                queTab.updateUI();
                
                // Log when tickets are removed externally (e.g., by web payments)
                if (newRowCount < oldRowCount) {
                    System.out.println("Queue: Hard refresh detected ticket removal (rows: " + oldRowCount + " -> " + newRowCount + ")");
                }
                
                // Re-setup columns after data refresh
                setupActionColumn();
                setupTicketColumn();
                
                jScrollPane1.repaint();
                jScrollPane1.revalidate();
                this.repaint();
                this.revalidate();
                updateStatusCounters();
                
            } catch (Exception e) {
                System.err.println("Queue: Error during hard refresh: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }


    
    private void setupNextButtons() {
        nxtnormalbtn.addActionListener(_ -> nextNormalTicket());
        nxtfastbtn.addActionListener(_ -> nextFastTicket());
    }
    
    private void setupPeriodicRefresh() {
        javax.swing.Timer refreshTimer = new javax.swing.Timer(2000, _ -> { // Reduced from 3000ms to 2000ms for faster response
            SwingUtilities.invokeLater(() -> {
                try {
                    int currentRowCount = queTab.getRowCount();
                    cephra.Admin.Utilities.QueueBridge.reloadFromDatabase();
                    
                    int newRowCount = queTab.getRowCount();
                    
                    // Always repaint and revalidate when row count changes (additions OR removals)
                    if (newRowCount != currentRowCount) {
                        queTab.repaint();
                        queTab.revalidate();
                        updateStatusCounters();
                        
                        if (newRowCount < currentRowCount) {
                            System.out.println("Queue: Detected ticket removal - refreshed admin queue (rows: " + currentRowCount + " -> " + newRowCount + ")");
                        }
                    }
                    
                    initializeWaitingGridFromDatabase();
                } catch (Exception ex) {
                    System.err.println("Queue: Error during periodic refresh: " + ex.getMessage());
                }
            });
        });
        
        refreshTimer.setRepeats(true);
        refreshTimer.start();
    }
    
    private void nextNormalTicket() {
        String ticket = findNextTicketByType("NCH");
        if (ticket != null) {
            // Get customer name for popup
            String customerName = cephra.Database.CephraDB.getCustomerByTicket(ticket);
            
            // Show waiting bay popup to user (same as Proceed button)
            cephra.Phone.Utilities.CustomPopupManager.showWaitingBayPopup(ticket, customerName, () -> {
                // Bay assignment and status update are handled in executeBayAssignmentCallback
                // This callback is just for cleanup if needed
                System.out.println("Next Normal callback: Bay assignment handled by executeBayAssignmentCallback");
            });
        }
        updateStatusCounters();
    }
    
    private void nextFastTicket() {
        String ticket = findNextTicketByType("FCH");
        if (ticket != null) {
            // Get customer name for popup
            String customerName = cephra.Database.CephraDB.getCustomerByTicket(ticket);
            
            // Show waiting bay popup to user (same as Proceed button)
            cephra.Phone.Utilities.CustomPopupManager.showWaitingBayPopup(ticket, customerName, () -> {
                // Bay assignment and status update are handled in executeBayAssignmentCallback
                // This callback is just for cleanup if needed
                System.out.println("Next Fast callback: Bay assignment handled by executeBayAssignmentCallback");
            });
        }
        updateStatusCounters();
    }
    
    
    private String findNextTicketByType(String type) {
        String lowestTicket = null;
        int lowestNumber = Integer.MAX_VALUE;
        
        for (int i = 0; i < buttonCount; i++) {
            String ticketText = gridButtons[i].getText();
            if (ticketText.contains(type)) {
                try {
                    String numberPart = ticketText.replaceAll("[^0-9]", "");
                    if (!numberPart.isEmpty()) {
                        int ticketNumber = Integer.parseInt(numberPart);
                        if (ticketNumber < lowestNumber) {
                            lowestNumber = ticketNumber;
                            lowestTicket = ticketText;
                        }
                    }
                } catch (NumberFormatException e) {
                    if (lowestTicket == null) {
                        lowestTicket = ticketText;
                    }
                }
            }
        }
        return lowestTicket;
    }
    
    

    // Expose waiting grid refresh for external callers (e.g., BayManagement)
    public void refreshWaitingGrid() {
        initializeWaitingGridFromDatabase();
    }
    @SuppressWarnings("unused")
    private void removeTicketFromGrid(String ticket) {
        for (int i = 0; i < buttonCount; i++) {
            if (gridButtons[i].getText().equals(ticket)) {
                try (java.sql.Connection conn = cephra.Database.DatabaseConnection.getConnection();
                     java.sql.PreparedStatement pstmt = conn.prepareStatement(
                         "UPDATE waiting_grid SET ticket_id = NULL, username = NULL, service_type = NULL, initial_battery_level = NULL, position_in_queue = NULL WHERE ticket_id = ?")) {
                    pstmt.setString(1, ticket);
                    pstmt.executeUpdate();
                } catch (Exception e) {
                    System.err.println("Error removing ticket from waiting grid database: " + e.getMessage());
                }
                
                for (int j = i; j < buttonCount - 1; j++) {
                    gridButtons[j].setText(gridButtons[j + 1].getText());
                    gridButtons[j].setVisible(gridButtons[j + 1].isVisible());
                }
                gridButtons[buttonCount - 1].setText("");
                gridButtons[buttonCount - 1].setVisible(false);
                buttonCount--;
                updateMonitorDisplay();
                break;
            }
        }
    }
    
    private void updateMonitorDisplay() {
        if (monitorInstance != null) {
            String[] buttonTexts = new String[10];
            for (int i = 0; i < 10; i++) {
                if (i < buttonCount) {
                    buttonTexts[i] = gridButtons[i].getText();
                } else {
                    buttonTexts[i] = "";
                }
            }
            monitorInstance.updateDisplay(buttonTexts);
        }
    }
    
 @SuppressWarnings("unused")
    private boolean assignToFastSlot(String ticket) {
        if (!cephra.Admin.BayManagement.hasChargingCapacity(true)) {
            return false;
        }
        
        int bayNumber = cephra.Admin.BayManagement.findNextAvailableBay(true);
        
        if (bayNumber > 0) {
            if (cephra.Admin.BayManagement.moveTicketFromWaitingToCharging(ticket, bayNumber)) {
                updateLocalFastButtons(cephra.Admin.BayManagement.getFastChargingGridTexts(), 
                                     cephra.Admin.BayManagement.getFastChargingGridColors());
                updateMonitorFastGrid();
                return true;
            }
        }
        return false;
    }
@SuppressWarnings("unused")
    private boolean assignToNormalSlot(String ticket) {
        if (!cephra.Admin.BayManagement.hasChargingCapacity(false)) {
            return false;
        }
        
        int bayNumber = cephra.Admin.BayManagement.findNextAvailableBay(false);
        
        if (bayNumber > 0) {
            if (cephra.Admin.BayManagement.moveTicketFromWaitingToCharging(ticket, bayNumber)) {
                updateLocalNormalButtons(cephra.Admin.BayManagement.getNormalChargingGridTexts(), 
                                       cephra.Admin.BayManagement.getNormalChargingGridColors());
                updateMonitorNormalGrid();
                return true;
            }
        }
        return false;
    }

    private void updateMonitorFastGrid() {
        if (monitorInstance != null) {
            String[] fastTickets = cephra.Admin.BayManagement.getFastChargingGridTexts();
            java.awt.Color[] fastColors = cephra.Admin.BayManagement.getFastChargingGridColors();
            
            monitorInstance.updateFastGrid(fastTickets);
            updateLocalFastButtons(fastTickets, fastColors);
        }
    }

    private void updateMonitorNormalGrid() {
        if (monitorInstance != null) {
            String[] normalTickets = cephra.Admin.BayManagement.getNormalChargingGridTexts();
            java.awt.Color[] normalColors = cephra.Admin.BayManagement.getNormalChargingGridColors();
            
            monitorInstance.updateNormalGrid(normalTickets);
            updateLocalNormalButtons(normalTickets, normalColors);
        }
    }
    
    private void updateLocalFastButtons(String[] texts, java.awt.Color[] colors) {
        JButton[] fastButtons = {fastslot1, fastslot2, fastslot3};
        
        for (int i = 0; i < fastButtons.length && i < texts.length; i++) {
            if (texts[i] != null && !texts[i].isEmpty()) {
                fastButtons[i].setText(texts[i]);
                fastButtons[i].setVisible(true);
                if (colors != null && i < colors.length) {
                    fastButtons[i].setForeground(colors[i]);
                }
            } else {
                fastButtons[i].setText("");
                fastButtons[i].setVisible(true);
                fastButtons[i].setForeground(new java.awt.Color(0, 147, 73)); // Green for Fast charging (bays 1-3)
            }
        }
    }
     
    
    private void updateLocalNormalButtons(String[] texts, java.awt.Color[] colors) {
        JButton[] normalButtons = {normalcharge1, normalcharge2, normalcharge3, normalcharge4, normalcharge5};
        
        for (int i = 0; i < normalButtons.length && i < texts.length; i++) {
            if (texts[i] != null && !texts[i].isEmpty()) {
                normalButtons[i].setText(texts[i]);
                normalButtons[i].setVisible(true);
                if (colors != null && i < colors.length) {
                    normalButtons[i].setForeground(colors[i]);
                }
            } else {
                normalButtons[i].setText("");
                normalButtons[i].setVisible(true);
                normalButtons[i].setForeground(new java.awt.Color(22,130,146));
            }
        }
    }




    public void setTableStatusToChargingByTicket(String ticket) {
        int ticketCol = getColumnIndex("Ticket");
        int statusCol = getColumnIndex("Status");
        if (ticketCol < 0 || statusCol < 0) return;
        for (int row = 0; row < queTab.getRowCount(); row++) {
            Object val = queTab.getValueAt(row, ticketCol);
            if (val != null && ticket.equals(String.valueOf(val).trim())) {
                queTab.setValueAt("Charging", row, statusCol);
                cephra.Database.CephraDB.updateQueueTicketStatus(ticket, "Charging");
                
                String customerName = cephra.Database.CephraDB.getCustomerByTicket(ticket);
                String bayNumber = cephra.Admin.BayManagement.getBayNumberByTicket(ticket);
                if (customerName != null) {
                    triggerNotificationForCustomer(customerName, "MY_TURN", ticket, bayNumber);
                }
                break;
            }
        }
    }

  


    private void setupPaymentColumn() {
        final int paymentCol = getColumnIndex("Payment");
        if (paymentCol < 0) return;

        queTab.getColumnModel().getColumn(paymentCol).setCellRenderer(new TableCellRenderer() {
            private final JButton btn = createFlatButton();
            private final JLabel label = new JLabel("");

            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                String v = value == null ? "" : String.valueOf(value).trim();
                
                if ("Pending".equalsIgnoreCase(v)) {
                    btn.setText("Pending");
                    btn.setForeground(new java.awt.Color(255, 255, 255));
                    btn.setBackground(new java.awt.Color(255, 140, 0));
                    btn.setOpaque(true);
                    return btn;
                } else if ("Paid".equalsIgnoreCase(v)) {
                    btn.setText("Paid");
                    btn.setBackground(new java.awt.Color(34, 139, 34));
                    btn.setForeground(new java.awt.Color(255, 255, 255));
                    btn.setOpaque(true);
                    return btn;
                }
                label.setText(v);
                return label;
            }
        });

        queTab.getColumnModel().getColumn(paymentCol).setCellEditor(new PaymentEditor());
    }

    private void setupTicketColumn() {
        final int ticketCol = getColumnIndex("Ticket");
        if (ticketCol < 0) return;

        queTab.getColumnModel().getColumn(ticketCol).setCellRenderer(new TableCellRenderer() {
            private final JLabel label = new JLabel();

            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                String ticketId = value == null ? "" : String.valueOf(value).trim();
                label.setText(ticketId);
                label.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
                
                // Handle row selection highlighting
                if (isSelected) {
                    label.setBackground(table.getSelectionBackground());
                    label.setForeground(table.getSelectionForeground());
                } else {
                    label.setBackground(table.getBackground());
                    // Set text color based on ticket type
                    if (ticketId.contains("P") && (ticketId.startsWith("FCHP") || ticketId.startsWith("NCHP"))) {
                        label.setForeground(new java.awt.Color(220, 20, 60));
                        label.setFont(label.getFont().deriveFont(Font.BOLD));
                    } else {
                        label.setForeground(new java.awt.Color(0, 0, 0));
                        label.setFont(label.getFont().deriveFont(Font.PLAIN));
                    }
                }
                
                label.setOpaque(true);
                return label;
            }
        });
    }

    private class PaymentEditor extends AbstractCellEditor implements TableCellEditor, java.awt.event.ActionListener {
        private final JButton btn = createFlatButton();
        private final JLabel label = new JLabel("");
        private int editingRow = -1;
        private String editorValue = "";

        PaymentEditor() {
            btn.addActionListener(this);
        }

        @Override
        public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
            editingRow = row;
            String v = value == null ? "" : String.valueOf(value).trim();
            editorValue = v;
            
            if ("Pending".equalsIgnoreCase(v)) {
                btn.setText("Pending");
                btn.setBackground(new java.awt.Color(255, 140, 0));
                btn.setForeground(new java.awt.Color(255, 255, 255));
                btn.setOpaque(true);
                return btn;
            } else if ("Paid".equalsIgnoreCase(v)) {
                btn.setText("Paid");
                btn.setBackground(new java.awt.Color(34, 139, 34));
                btn.setForeground(new java.awt.Color(255, 255, 255));
                btn.setOpaque(true);
                return btn;
            }
            label.setText(v);
            return label;
        }

        @Override
        public Object getCellEditorValue() {
            return editorValue;
        }

        @Override
        public void actionPerformed(java.awt.event.ActionEvent e) {
            if (editingRow >= 0 && editingRow < queTab.getRowCount()) {
                int statusCol = getColumnIndex("Status");
                String status = statusCol >= 0 ? String.valueOf(queTab.getValueAt(editingRow, statusCol)) : "";
                
                if (!"Complete".equalsIgnoreCase(status)) {
                    JOptionPane.showMessageDialog(
                        Queue.this,
                        "Cannot mark as paid until charging is complete.\nCurrent status: " + status,
                        "Payment Not Ready",
                        JOptionPane.WARNING_MESSAGE
                    );
                    stopCellEditing();
                    return;
                }
                
                int ticketCol = getColumnIndex("Ticket");
                if (ticketCol >= 0) {
                    Object v = queTab.getValueAt(editingRow, ticketCol);
                    String ticket = v == null ? "" : String.valueOf(v).trim();
                    if (!ticket.isEmpty()) {
                        String paymentMethod = cephra.Database.CephraDB.getQueueTicketPaymentMethod(ticket);
                        if (!"Cash".equalsIgnoreCase(paymentMethod)) {
                            JOptionPane.showMessageDialog(
                                Queue.this,
                                "This ticket is not marked for cash payment.\nPayment method: " + paymentMethod + "\nOnly cash payments can be marked as paid by admin.",
                                "Payment Method Mismatch",
                                JOptionPane.WARNING_MESSAGE
                            );
                            stopCellEditing();
                            return;
                        }
                    }
                }
                
                Object[] options = new Object[] { "Mark as Paid", "Cancel" };
                int choice = JOptionPane.showOptionDialog(
                    Queue.this,
                    "Mark this payment as paid?",
                    "Payment",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.INFORMATION_MESSAGE,
                    null,
                    options,
                    options[0]
                );
                if (choice == 0) {
                    editorValue = "Paid";
                    if (ticketCol >= 0) {
                        Object v = queTab.getValueAt(editingRow, ticketCol);
                        String ticket = v == null ? "" : String.valueOf(v).trim();
                        if (!ticket.isEmpty()) {
                            boolean alreadyProcessed = cephra.Database.CephraDB.isPaymentAlreadyProcessed(ticket);
                            if (alreadyProcessed) {
                                stopCellEditing();
                                try {
                                    cephra.Admin.Utilities.QueueBridge.removeTicket(ticket);
                                    refreshEntirePanel();
                                } catch (Throwable t) {
                                    System.err.println("Error removing ticket via QueueBridge: " + t.getMessage());
                                }
                            } else {
                                int customerCol = getColumnIndex("Customer");
                                int serviceCol = getColumnIndex("Service");
                                String customer = customerCol >= 0 ? String.valueOf(queTab.getValueAt(editingRow, customerCol)) : "";
                                String serviceName = serviceCol >= 0 ? String.valueOf(queTab.getValueAt(editingRow, serviceCol)) : "";
                                
                                if (customer == null || customer.trim().isEmpty()) {
                                    System.err.println("Warning: Customer name is empty for ticket " + ticket);
                                }
                                if (serviceName == null || serviceName.trim().isEmpty()) {
                                    System.err.println("Warning: Service name is empty for ticket " + ticket);
                                }
                                
                                cephra.Admin.Utilities.QueueBridge.markPaymentPaid(ticket);
                                
                                try {
                                    if (cephra.Phone.Popups.Pending_Payment.isShowingForTicket(ticket)) {
                                        cephra.Phone.Popups.Pending_Payment.hidePayPop();
                                    }
                                } catch (Exception ex) {
                                    System.err.println("Error hiding PayPop: " + ex.getMessage());
                                }
                                
                                stopCellEditing();
                                try {
                                    cephra.Admin.Utilities.QueueBridge.removeTicket(ticket);
                                    refreshEntirePanel();
                                } catch (Throwable t) {
                                    System.err.println("Error removing ticket via QueueBridge: " + t.getMessage());
                                }
                            }
                        }
                    }
                }
            }
            stopCellEditing();
            updateStatusCounters();
        }
    }
    
    private void setupDateTimeTimer() {
        updateDateTime();
        javax.swing.Timer timer = new javax.swing.Timer(1000, _ -> updateDateTime());
        timer.start();
    }
    
    private void updateDateTime() {
        java.text.SimpleDateFormat timeFormat = new java.text.SimpleDateFormat("hh:mm a");
        java.text.SimpleDateFormat dateFormat = new java.text.SimpleDateFormat("dd MMMM, EEEE");
        
        java.util.Date now = new java.util.Date();
        String time = timeFormat.format(now);
        String date = dateFormat.format(now);
        
        datetimeStaff.setText(time + " " + date);
    }

    
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        Baybutton = new javax.swing.JButton();
        businessbutton = new javax.swing.JButton();
        exitlogin = new javax.swing.JButton();
        staffbutton = new javax.swing.JButton();
        historybutton = new javax.swing.JButton();
        jTabbedPane1 = new javax.swing.JTabbedPane();
        panelLists = new javax.swing.JPanel();
        Paid = new javax.swing.JLabel();
        Waitings = new javax.swing.JLabel();
        Charging = new javax.swing.JLabel();
        jScrollPane1 = new javax.swing.JScrollPane();
        queTab = new javax.swing.JTable();
        jLabel2 = new javax.swing.JLabel();
        ControlPanel = new javax.swing.JPanel();
        fastpanel = new javax.swing.JPanel();
        fastslot1 = new javax.swing.JButton();
        fastslot2 = new javax.swing.JButton();
        fastslot3 = new javax.swing.JButton();
        jPanel1 = new javax.swing.JPanel();
        jButton1 = new javax.swing.JButton();
        jButton2 = new javax.swing.JButton();
        jButton3 = new javax.swing.JButton();
        jButton4 = new javax.swing.JButton();
        jButton5 = new javax.swing.JButton();
        jButton6 = new javax.swing.JButton();
        jButton7 = new javax.swing.JButton();
        jButton8 = new javax.swing.JButton();
        normalcharge1 = new javax.swing.JButton();
        nxtfastbtn = new javax.swing.JButton();
        nxtnormalbtn = new javax.swing.JButton();
        jPanel2 = new javax.swing.JPanel();
        normalcharge2 = new javax.swing.JButton();
        normalcharge3 = new javax.swing.JButton();
        normalcharge4 = new javax.swing.JButton();
        normalcharge5 = new javax.swing.JButton();
        queIcon = new javax.swing.JLabel();
        datetimeStaff = new javax.swing.JLabel();
        labelStaff = new javax.swing.JLabel();
        jLabel3 = new javax.swing.JLabel();
        jLabel1 = new javax.swing.JLabel();
        MainIcon = new javax.swing.JLabel();

        setMaximumSize(new java.awt.Dimension(1000, 750));
        setLayout(null);

        Baybutton.setForeground(new java.awt.Color(255, 255, 255));
        Baybutton.setText("BAYS");
        Baybutton.setBorder(null);
        Baybutton.setBorderPainted(false);
        Baybutton.setContentAreaFilled(false);
        Baybutton.setFocusPainted(false);
        Baybutton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                BaybuttonActionPerformed(evt);
            }
        });
        add(Baybutton);
        Baybutton.setBounds(385, 26, 33, 15);

        businessbutton.setForeground(new java.awt.Color(255, 255, 255));
        businessbutton.setText("BUSINESS OVERVIEW");
        businessbutton.setBorder(null);
        businessbutton.setBorderPainted(false);
        businessbutton.setContentAreaFilled(false);
        businessbutton.setFocusPainted(false);
        businessbutton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                businessbuttonActionPerformed(evt);
            }
        });
        add(businessbutton);
        businessbutton.setBounds(617, 26, 136, 15);

        exitlogin.setBorder(null);
        exitlogin.setBorderPainted(false);
        exitlogin.setContentAreaFilled(false);
        exitlogin.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                exitloginActionPerformed(evt);
            }
        });
        add(exitlogin);
        exitlogin.setBounds(930, 0, 70, 60);

        staffbutton.setForeground(new java.awt.Color(255, 255, 255));
        staffbutton.setText("STAFF RECORDS");
        staffbutton.setBorder(null);
        staffbutton.setBorderPainted(false);
        staffbutton.setContentAreaFilled(false);
        staffbutton.setFocusPainted(false);
        staffbutton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                staffbuttonActionPerformed(evt);
            }
        });
        add(staffbutton);
        staffbutton.setBounds(505, 26, 96, 15);

        historybutton.setForeground(new java.awt.Color(255, 255, 255));
        historybutton.setText("HISTORY");
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
        historybutton.setBounds(433, 26, 57, 15);

        jTabbedPane1.setBackground(new java.awt.Color(4, 38, 55));
        jTabbedPane1.setForeground(new java.awt.Color(255, 255, 255));
        jTabbedPane1.setCursor(new java.awt.Cursor(java.awt.Cursor.DEFAULT_CURSOR));

        panelLists.setLayout(null);

        Paid.setFont(new java.awt.Font("Segoe UI", 1, 48)); // NOI18N
        Paid.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        Paid.setText("5");
        panelLists.add(Paid);
        Paid.setBounds(40, 520, 120, 70);

        Waitings.setFont(new java.awt.Font("Segoe UI", 1, 48)); // NOI18N
        Waitings.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        Waitings.setText("4");
        panelLists.add(Waitings);
        Waitings.setBounds(40, 140, 120, 70);

        Charging.setFont(new java.awt.Font("Segoe UI", 1, 48)); // NOI18N
        Charging.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        Charging.setText("4");
        panelLists.add(Charging);
        Charging.setBounds(40, 330, 120, 70);

        queTab.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {
                {null, null, null, null, null, null},
                {null, null, null, null, null, null},
                {null, null, null, null, null, null},
                {null, null, null, null, null, null}
            },
            new String [] {
                "Ticket", "Customer", "Service", "Status", "Payment", "Action"
            }
        ));
        queTab.setShowHorizontalLines(true);
        queTab.getTableHeader().setResizingAllowed(false);
        queTab.getTableHeader().setReorderingAllowed(false);
        jScrollPane1.setViewportView(queTab);

        panelLists.add(jScrollPane1);
        jScrollPane1.setBounds(210, 55, 770, 597);

        jLabel2.setBackground(new java.awt.Color(4, 38, 55));
        jLabel2.setIcon(new javax.swing.ImageIcon(getClass().getResource("/cephra/Cephra Images/QUEUEtable.png"))); // NOI18N
        panelLists.add(jLabel2);
        jLabel2.setBounds(-10, -90, 1000, 750);

        jTabbedPane1.addTab("Queue Lists", panelLists);

        ControlPanel.setLayout(null);

        fastpanel.setOpaque(false);
        fastpanel.setLayout(new java.awt.GridLayout(1, 3, 1, 45));

        fastslot1.setBackground(new java.awt.Color(255, 0, 0));
        fastslot1.setFont(new java.awt.Font("Segoe UI", 1, 24)); // NOI18N
        fastslot1.setForeground(new java.awt.Color(51, 51, 255));
        fastslot1.setText("XXXXXX");
        fastslot1.setBorder(null);
        fastslot1.setBorderPainted(false);
        fastslot1.setContentAreaFilled(false);
        fastslot1.setFocusPainted(false);
        fastslot1.setFocusable(false);
        fastpanel.add(fastslot1);

        fastslot2.setBackground(new java.awt.Color(255, 0, 0));
        fastslot2.setFont(new java.awt.Font("Segoe UI", 1, 24)); // NOI18N
        fastslot2.setForeground(new java.awt.Color(0, 147, 73));
        fastslot2.setText("XXXXXX");
        fastslot2.setToolTipText("");
        fastslot2.setBorder(null);
        fastslot2.setBorderPainted(false);
        fastslot2.setContentAreaFilled(false);
        fastslot2.setFocusPainted(false);
        fastslot2.setFocusable(false);
        fastpanel.add(fastslot2);

        fastslot3.setBackground(new java.awt.Color(255, 0, 0));
        fastslot3.setFont(new java.awt.Font("Segoe UI", 1, 24)); // NOI18N
        fastslot3.setForeground(new java.awt.Color(0, 147, 73));
        fastslot3.setText("XXXXXX");
        fastslot3.setBorder(null);
        fastslot3.setBorderPainted(false);
        fastslot3.setContentAreaFilled(false);
        fastslot3.setFocusPainted(false);
        fastslot3.setFocusable(false);
        fastpanel.add(fastslot3);

        ControlPanel.add(fastpanel);
        fastpanel.setBounds(90, 135, 420, 50);

        jPanel1.setOpaque(false);
        jPanel1.setLayout(new java.awt.GridLayout(2, 4, 1, 20));

        jButton1.setBackground(new java.awt.Color(255, 0, 0));
        jButton1.setFont(new java.awt.Font("Segoe UI", 1, 24)); // NOI18N
        jButton1.setText("XXXXXX");
        jButton1.setBorder(null);
        jButton1.setBorderPainted(false);
        jButton1.setContentAreaFilled(false);
        jButton1.setFocusPainted(false);
        jButton1.setFocusable(false);
        jButton1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton1ActionPerformed(evt);
            }
        });
        jPanel1.add(jButton1);

        jButton2.setBackground(new java.awt.Color(255, 0, 0));
        jButton2.setFont(new java.awt.Font("Segoe UI", 1, 24)); // NOI18N
        jButton2.setText("XXXXXX");
        jButton2.setBorder(null);
        jButton2.setBorderPainted(false);
        jButton2.setContentAreaFilled(false);
        jButton2.setFocusPainted(false);
        jButton2.setFocusable(false);
        jButton2.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton2ActionPerformed(evt);
            }
        });
        jPanel1.add(jButton2);

        jButton3.setBackground(new java.awt.Color(255, 0, 0));
        jButton3.setFont(new java.awt.Font("Segoe UI", 1, 24)); // NOI18N
        jButton3.setText("XXXXXX");
        jButton3.setBorder(null);
        jButton3.setBorderPainted(false);
        jButton3.setContentAreaFilled(false);
        jButton3.setFocusPainted(false);
        jButton3.setFocusable(false);
        jButton3.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton3ActionPerformed(evt);
            }
        });
        jPanel1.add(jButton3);

        jButton4.setBackground(new java.awt.Color(255, 0, 0));
        jButton4.setFont(new java.awt.Font("Segoe UI", 1, 24)); // NOI18N
        jButton4.setText("XXXXXX");
        jButton4.setBorder(null);
        jButton4.setBorderPainted(false);
        jButton4.setContentAreaFilled(false);
        jButton4.setFocusPainted(false);
        jButton4.setFocusable(false);
        jButton4.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton4ActionPerformed(evt);
            }
        });
        jPanel1.add(jButton4);

        jButton5.setBackground(new java.awt.Color(255, 0, 0));
        jButton5.setFont(new java.awt.Font("Segoe UI", 1, 24)); // NOI18N
        jButton5.setText("XXXXXX");
        jButton5.setBorder(null);
        jButton5.setBorderPainted(false);
        jButton5.setContentAreaFilled(false);
        jButton5.setFocusPainted(false);
        jButton5.setFocusable(false);
        jButton5.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton5ActionPerformed(evt);
            }
        });
        jPanel1.add(jButton5);

        jButton6.setBackground(new java.awt.Color(255, 0, 0));
        jButton6.setFont(new java.awt.Font("Segoe UI", 1, 24)); // NOI18N
        jButton6.setText("XXXXXX");
        jButton6.setBorder(null);
        jButton6.setBorderPainted(false);
        jButton6.setContentAreaFilled(false);
        jButton6.setFocusPainted(false);
        jButton6.setFocusable(false);
        jButton6.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton6ActionPerformed(evt);
            }
        });
        jPanel1.add(jButton6);

        jButton7.setBackground(new java.awt.Color(255, 0, 0));
        jButton7.setFont(new java.awt.Font("Segoe UI", 1, 24)); // NOI18N
        jButton7.setText("XXXXXX");
        jButton7.setBorder(null);
        jButton7.setBorderPainted(false);
        jButton7.setContentAreaFilled(false);
        jButton7.setFocusPainted(false);
        jButton7.setFocusable(false);
        jButton7.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton7ActionPerformed(evt);
            }
        });
        jPanel1.add(jButton7);

        jButton8.setBackground(new java.awt.Color(255, 0, 0));
        jButton8.setFont(new java.awt.Font("Segoe UI", 1, 24)); // NOI18N
        jButton8.setText("XXXXXX");
        jButton8.setBorder(null);
        jButton8.setBorderPainted(false);
        jButton8.setContentAreaFilled(false);
        jButton8.setFocusPainted(false);
        jButton8.setFocusable(false);
        jButton8.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton8ActionPerformed(evt);
            }
        });
        jPanel1.add(jButton8);

        ControlPanel.add(jPanel1);
        jPanel1.setBounds(90, 430, 560, 180);

        normalcharge1.setFont(new java.awt.Font("Segoe UI", 1, 24)); // NOI18N
        normalcharge1.setForeground(new java.awt.Color(22, 130, 146));
        normalcharge1.setText("XXXXXX");
        normalcharge1.setBorder(null);
        normalcharge1.setBorderPainted(false);
        normalcharge1.setContentAreaFilled(false);
        normalcharge1.setFocusPainted(false);
        normalcharge1.setFocusable(false);
        ControlPanel.add(normalcharge1);
        normalcharge1.setBounds(520, 140, 120, 40);

        nxtfastbtn.setBorder(null);
        nxtfastbtn.setBorderPainted(false);
        nxtfastbtn.setContentAreaFilled(false);
        nxtfastbtn.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                nxtfastbtnActionPerformed(evt);
            }
        });
        ControlPanel.add(nxtfastbtn);
        nxtfastbtn.setBounds(770, 350, 140, 60);

        nxtnormalbtn.setBorder(null);
        nxtnormalbtn.setBorderPainted(false);
        nxtnormalbtn.setContentAreaFilled(false);
        nxtnormalbtn.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                nxtnormalbtnActionPerformed(evt);
            }
        });
        ControlPanel.add(nxtnormalbtn);
        nxtnormalbtn.setBounds(750, 250, 140, 70);

        jPanel2.setOpaque(false);
        jPanel2.setLayout(new java.awt.GridLayout(1, 4, 1, 25));

        normalcharge2.setBackground(new java.awt.Color(255, 0, 0));
        normalcharge2.setFont(new java.awt.Font("Segoe UI", 1, 24)); // NOI18N
        normalcharge2.setForeground(new java.awt.Color(22, 130, 146));
        normalcharge2.setText("XXXXXX");
        normalcharge2.setBorder(null);
        normalcharge2.setBorderPainted(false);
        normalcharge2.setContentAreaFilled(false);
        normalcharge2.setFocusPainted(false);
        normalcharge2.setFocusable(false);
        jPanel2.add(normalcharge2);

        normalcharge3.setBackground(new java.awt.Color(255, 0, 0));
        normalcharge3.setFont(new java.awt.Font("Segoe UI", 1, 24)); // NOI18N
        normalcharge3.setForeground(new java.awt.Color(22, 130, 146));
        normalcharge3.setText("XXXXXX");
        normalcharge3.setBorder(null);
        normalcharge3.setBorderPainted(false);
        normalcharge3.setContentAreaFilled(false);
        normalcharge3.setFocusPainted(false);
        normalcharge3.setFocusable(false);
        jPanel2.add(normalcharge3);

        normalcharge4.setBackground(new java.awt.Color(255, 0, 0));
        normalcharge4.setFont(new java.awt.Font("Segoe UI", 1, 24)); // NOI18N
        normalcharge4.setForeground(new java.awt.Color(22, 130, 146));
        normalcharge4.setText("XXXXXX");
        normalcharge4.setBorder(null);
        normalcharge4.setBorderPainted(false);
        normalcharge4.setContentAreaFilled(false);
        normalcharge4.setFocusPainted(false);
        normalcharge4.setFocusable(false);
        jPanel2.add(normalcharge4);

        normalcharge5.setBackground(new java.awt.Color(255, 0, 0));
        normalcharge5.setFont(new java.awt.Font("Segoe UI", 1, 24)); // NOI18N
        normalcharge5.setForeground(new java.awt.Color(22, 130, 146));
        normalcharge5.setText("XXXXXX");
        normalcharge5.setBorder(null);
        normalcharge5.setBorderPainted(false);
        normalcharge5.setContentAreaFilled(false);
        normalcharge5.setFocusPainted(false);
        normalcharge5.setFocusable(false);
        jPanel2.add(normalcharge5);

        ControlPanel.add(jPanel2);
        jPanel2.setBounds(90, 230, 560, 60);

        queIcon.setIcon(new javax.swing.ImageIcon(getClass().getResource("/cephra/Cephra Images/ControlQe.png"))); // NOI18N
        queIcon.setOpaque(true);
        ControlPanel.add(queIcon);
        queIcon.setBounds(0, -70, 1010, 800);

        jTabbedPane1.addTab("Queue Control", ControlPanel);

        add(jTabbedPane1);
        jTabbedPane1.setBounds(0, 50, 1020, 700);

        datetimeStaff.setFont(new java.awt.Font("Segoe UI", 0, 10)); // NOI18N
        datetimeStaff.setForeground(new java.awt.Color(255, 255, 255));
        datetimeStaff.setText("10:44 AM 17 August, Sunday");
        add(datetimeStaff);
        datetimeStaff.setBounds(820, 40, 170, 20);

        labelStaff.setFont(new java.awt.Font("Segoe UI", 1, 16)); // NOI18N
        labelStaff.setForeground(new java.awt.Color(255, 255, 255));
        labelStaff.setText("Admin!");
        add(labelStaff);
        labelStaff.setBounds(870, 10, 70, 30);

        jLabel3.setFont(new java.awt.Font("Segoe UI", 1, 16)); // NOI18N
        jLabel3.setForeground(new java.awt.Color(255, 255, 255));
        jLabel3.setText("Hello,");
        add(jLabel3);
        jLabel3.setBounds(820, 10, 50, 30);

        jLabel1.setForeground(new java.awt.Color(4, 167, 182));
        jLabel1.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        jLabel1.setText("QUEUE LIST");
        add(jLabel1);
        jLabel1.setBounds(289, 26, 80, 15);

        MainIcon.setIcon(new javax.swing.ImageIcon(getClass().getResource("/cephra/Cephra Images/Tab Pane.png"))); // NOI18N
        add(MainIcon);
        MainIcon.setBounds(0, -10, 1000, 770);
    }// </editor-fold>//GEN-END:initComponents

    private void businessbuttonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_businessbuttonActionPerformed
        Window w = SwingUtilities.getWindowAncestor(Queue.this);
        if (w instanceof cephra.Frame.Admin) {
            ((cephra.Frame.Admin) w).switchPanel(new cephra.Admin.Business_Overview());
        }
    }//GEN-LAST:event_businessbuttonActionPerformed

    private void staffbuttonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_staffbuttonActionPerformed
        Window w = SwingUtilities.getWindowAncestor(Queue.this);
        if (w instanceof cephra.Frame.Admin) {
            ((cephra.Frame.Admin) w).switchPanel(new StaffRecord());
        }
    }//GEN-LAST:event_staffbuttonActionPerformed

    private void exitloginActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exitloginActionPerformed
        Window w = SwingUtilities.getWindowAncestor(Queue.this);
        if (w instanceof cephra.Frame.Admin) {
            ((cephra.Frame.Admin) w).switchPanel(new cephra.Admin.Login());
        }
    }//GEN-LAST:event_exitloginActionPerformed

    private void historybuttonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_historybuttonActionPerformed
        Window w = SwingUtilities.getWindowAncestor(Queue.this);
        if (w instanceof cephra.Frame.Admin) {
            ((cephra.Frame.Admin) w).switchPanel(new History());
        }
    }//GEN-LAST:event_historybuttonActionPerformed

    private void BaybuttonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_BaybuttonActionPerformed
        Window w = SwingUtilities.getWindowAncestor(Queue.this);
        if (w instanceof cephra.Frame.Admin) {
            ((cephra.Frame.Admin) w).switchPanel(new BayManagement());
        }
    }//GEN-LAST:event_BaybuttonActionPerformed

    private void nxtnormalbtnActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_nxtnormalbtnActionPerformed
    }//GEN-LAST:event_nxtnormalbtnActionPerformed
    
    private void nxtfastbtnActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_nxtfastbtnActionPerformed
    }//GEN-LAST:event_nxtfastbtnActionPerformed

    private void jButton1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton1ActionPerformed
    }//GEN-LAST:event_jButton1ActionPerformed
    
    private void jButton2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton2ActionPerformed
    }//GEN-LAST:event_jButton2ActionPerformed
    
    private void jButton3ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton3ActionPerformed
    }//GEN-LAST:event_jButton3ActionPerformed
    
    private void jButton4ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton4ActionPerformed
    }//GEN-LAST:event_jButton4ActionPerformed
    
    private void jButton5ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton5ActionPerformed
    }//GEN-LAST:event_jButton5ActionPerformed
    
    private void jButton6ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton6ActionPerformed
    }//GEN-LAST:event_jButton6ActionPerformed
    
    private void jButton7ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton7ActionPerformed
    }//GEN-LAST:event_jButton7ActionPerformed
    
    private void jButton8ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton8ActionPerformed
    }//GEN-LAST:event_jButton8ActionPerformed

    private void initializeGridDisplays() {
        try {
            cephra.Admin.BayManagement.ensureMaintenanceDisplay();
            
            String[] fastTexts = cephra.Admin.BayManagement.getFastChargingGridTexts();
            java.awt.Color[] fastColors = cephra.Admin.BayManagement.getFastChargingGridColors();
            updateLocalFastButtons(fastTexts, fastColors);
            
            String[] normalTexts = cephra.Admin.BayManagement.getNormalChargingGridTexts();
            java.awt.Color[] normalColors = cephra.Admin.BayManagement.getNormalChargingGridColors();
            updateLocalNormalButtons(normalTexts, normalColors);
            
            if (monitorInstance != null) {
                monitorInstance.updateFastGrid(fastTexts);
                monitorInstance.updateNormalGrid(normalTexts);
            }
            
        } catch (Exception e) {
            System.err.println("Error initializing Queue grid displays: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    public void refreshGridDisplays() {
        try {
            initializeWaitingGridFromDatabase();
            
            String[] fastTexts = cephra.Admin.BayManagement.getFastChargingGridTexts();
            java.awt.Color[] fastColors = cephra.Admin.BayManagement.getFastChargingGridColors();
            updateLocalFastButtons(fastTexts, fastColors);
            
            String[] normalTexts = cephra.Admin.BayManagement.getNormalChargingGridTexts();
            java.awt.Color[] normalColors = cephra.Admin.BayManagement.getNormalChargingGridColors();
            updateLocalNormalButtons(normalTexts, normalColors);
            
            if (monitorInstance != null) {
                monitorInstance.updateFastGrid(fastTexts);
                monitorInstance.updateNormalGrid(normalTexts);
            }
            
        } catch (Exception e) {
            System.err.println("Error refreshing Queue grid displays: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void initializeWaitingGridFromDatabase() {
        try {
            String[] waitingTickets = cephra.Admin.BayManagement.getWaitingGridTickets();
            
            for (int i = 0; i < waitingTickets.length && i < gridButtons.length; i++) {
                if (waitingTickets[i] != null && !waitingTickets[i].isEmpty()) {
                    gridButtons[i].setText(waitingTickets[i]);
                    gridButtons[i].setVisible(true);
                    
                    if (waitingTickets[i].startsWith("FCHP") || waitingTickets[i].startsWith("NCHP")) {
                        gridButtons[i].setForeground(java.awt.Color.RED);
                    } else {
                        gridButtons[i].setForeground(java.awt.Color.BLACK);
                    }
                    
                    buttonCount = Math.max(buttonCount, i + 1);
                } else {
                    gridButtons[i].setText("");
                    gridButtons[i].setVisible(false);
                }
            }
            
            if (monitorInstance != null) {
                monitorInstance.updateDisplay(waitingTickets);
            }
            
        } catch (Exception e) {
            System.err.println("Error initializing waiting grid from database: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
  
    private static cephra.Phone.Popups.Notification getOrCreateUnifiedNotification(cephra.Frame.Phone phoneFrame) {
        if (staticNotification == null) {
            staticNotification = new cephra.Phone.Popups.Notification();
            staticNotification.addToFrame(phoneFrame);
        }
        return staticNotification;
    }
    
    private void triggerNotificationForCustomer(String customer, String notificationType, String ticketId, String bayNumber) {
        if (customer == null || customer.trim().isEmpty()) {
            return;
        }
        
        try {
            switch (notificationType) {
                case "TICKET_WAITING":
                    cephra.Phone.Utilities.NotificationManager.addTicketWaitingNotification(customer, ticketId);
                    break;
                case "TICKET_PENDING":
                    cephra.Phone.Utilities.NotificationManager.addTicketPendingNotification(customer, ticketId);
                    break;
                case "MY_TURN":
                    cephra.Phone.Utilities.NotificationManager.addMyTurnNotification(customer, ticketId, bayNumber);
                    break;
                case "FULL_CHARGE":
                    cephra.Phone.Utilities.NotificationManager.addFullChargeNotification(customer, ticketId);
                    break;
                default:
                    return;
            }
            
            showVisualNotification(customer, notificationType, ticketId, bayNumber);
            
        } catch (Exception e) {
            System.err.println("Error triggering notification for customer " + customer + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void showVisualNotification(String customer, String notificationType, String ticketId, String bayNumber) {
        SwingUtilities.invokeLater(() -> {
            try {
                java.awt.Window[] windows = java.awt.Window.getWindows();
                cephra.Frame.Phone phoneFrame = null;
                
                for (java.awt.Window window : windows) {
                    if (window instanceof cephra.Frame.Phone) {
                        phoneFrame = (cephra.Frame.Phone) window;
                        break;
                    }
                }
                
                if (phoneFrame == null) {
                    return;
                }
                
                String currentUser = cephra.Database.CephraDB.getCurrentUsername();
                if (currentUser == null || !currentUser.equals(customer)) {
                    return;
                }
                
                cephra.Phone.Popups.Notification unifiedNotif = getOrCreateUnifiedNotification(phoneFrame);
                
                switch (notificationType) {
                    case "TICKET_WAITING":
                        unifiedNotif.updateAndShowNotification(cephra.Phone.Popups.Notification.TYPE_WAITING, ticketId, bayNumber);
                        break;
                        
                    case "TICKET_PENDING":
                        unifiedNotif.updateAndShowNotification(cephra.Phone.Popups.Notification.TYPE_PENDING, ticketId, bayNumber);
                        break;
                        
                    case "MY_TURN":
                        unifiedNotif.updateAndShowNotification(cephra.Phone.Popups.Notification.TYPE_MY_TURN, ticketId, bayNumber);
                        break;
                        
                    case "FULL_CHARGE":
                        unifiedNotif.updateAndShowNotification(cephra.Phone.Popups.Notification.TYPE_DONE, ticketId, bayNumber);
                        break;
                        
                    default:
                        break;
                }
                
            } catch (Exception e) {
                System.err.println("Error showing visual notification: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }
    
    @SuppressWarnings("unused")
    private String determineBayNumber(String ticket, boolean isFast) {
        if (ticket == null || ticket.trim().isEmpty()) {
            return "Unknown";
        }
        
        try {
            if (isFast) {
                if (ticket.equals(fastslot1.getText())) return "Bay-1";
                if (ticket.equals(fastslot2.getText())) return "Bay-2";
                if (ticket.equals(fastslot3.getText())) return "Bay-3";
                return "Bay-1";
            } else {
                if (ticket.equals(normalcharge1.getText())) return "Bay-4";
                if (ticket.equals(normalcharge2.getText())) return "Bay-5";
                if (ticket.equals(normalcharge3.getText())) return "Bay-6";
                if (ticket.equals(normalcharge4.getText())) return "Bay-7";
                if (ticket.equals(normalcharge5.getText())) return "Bay-8";
                return "Bay-4";
            }
        } catch (Exception e) {
            System.err.println("Error determining bay number for ticket " + ticket + ": " + e.getMessage());
        }
        
        return isFast ? "Bay-1" : "Bay-4";
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton Baybutton;
    private javax.swing.JLabel Charging;
    private javax.swing.JPanel ControlPanel;
    private javax.swing.JLabel MainIcon;
    private javax.swing.JLabel Paid;
    private javax.swing.JLabel Waitings;
    private javax.swing.JButton businessbutton;
    private javax.swing.JLabel datetimeStaff;
    private javax.swing.JButton exitlogin;
    private javax.swing.JPanel fastpanel;
    private javax.swing.JButton fastslot1;
    private javax.swing.JButton fastslot2;
    private javax.swing.JButton fastslot3;
    private javax.swing.JButton historybutton;
    private javax.swing.JButton jButton1;
    private javax.swing.JButton jButton2;
    private javax.swing.JButton jButton3;
    private javax.swing.JButton jButton4;
    private javax.swing.JButton jButton5;
    private javax.swing.JButton jButton6;
    private javax.swing.JButton jButton7;
    private javax.swing.JButton jButton8;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JTabbedPane jTabbedPane1;
    private javax.swing.JLabel labelStaff;
    private javax.swing.JButton normalcharge1;
    private javax.swing.JButton normalcharge2;
    private javax.swing.JButton normalcharge3;
    private javax.swing.JButton normalcharge4;
    private javax.swing.JButton normalcharge5;
    private javax.swing.JButton nxtfastbtn;
    private javax.swing.JButton nxtnormalbtn;
    private javax.swing.JPanel panelLists;
    private javax.swing.JLabel queIcon;
    private javax.swing.JTable queTab;
    private javax.swing.JButton staffbutton;
    // End of variables declaration//GEN-END:variables
}
