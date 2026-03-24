package cephra.Admin;

import java.awt.Color;
import java.awt.Font;
import java.awt.Window;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;

import cephra.Admin.Utilities.ButtonHoverEffect;
import cephra.Admin.Utilities.StaffData;
import cephra.Admin.Utilities.jtableDesign;


public class StaffRecord extends javax.swing.JPanel {

    public StaffRecord() {
        initComponents();
        setPreferredSize(new java.awt.Dimension(1000, 750));
        setSize(1000, 750);
        setupDateTimeTimer();

        search.setOpaque(false);
        search.setBackground(new Color(0, 0, 0, 0));
        search.setBorder(null);
        
        jtableDesign.apply(staffTable);
  
        jtableDesign.makeScrollPaneTransparent(jScrollPane1);
        
        JTableHeader header = staffTable.getTableHeader();
        header.setFont(new Font("Sogie UI", Font.BOLD, 16));
        
               BTNsearch.addActionListener(e -> searchStaff());
        
        // Add Enter key listener to search field
        search.addKeyListener(new java.awt.event.KeyAdapter() {
            @Override
            public void keyPressed(java.awt.event.KeyEvent evt) {
                if (evt.getKeyCode() == java.awt.event.KeyEvent.VK_ENTER) {
                    searchStaff();
                }
            }
        });
        
        // Initially disable profRemove and passReset buttons
        profRemove.setEnabled(false);
        passReset.setEnabled(false);
        
        // Add table selection listener to enable/disable buttons
        staffTable.getSelectionModel().addListSelectionListener(e -> {
            int selectedRow = staffTable.getSelectedRow();
            boolean hasSelection = selectedRow != -1;
            profRemove.setEnabled(hasSelection);
            passReset.setEnabled(hasSelection);
        });
        
        // Load staff data into table
        refreshStaffTable();
        
        // Add action listener to profRemove button
        profRemove.addActionListener(e -> removeSelectedStaff());
        
        // Add action listener to passReset button
        passReset.addActionListener(e -> resetSelectedStaffPassword());

        // Add hover effects to navigation buttons
        ButtonHoverEffect.addHoverEffect(quebutton);
        ButtonHoverEffect.addHoverEffect(Baybutton);
        ButtonHoverEffect.addHoverEffect(historybutton);
        ButtonHoverEffect.addHoverEffect(businessbutton);
        ButtonHoverEffect.addHoverEffect(exitlogin);

        refreshStaffTable();
    }


    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        exitlogin = new javax.swing.JButton();
        STAFFtEXT = new javax.swing.JLabel();
        businessbutton = new javax.swing.JButton();
        historybutton = new javax.swing.JButton();
        Baybutton = new javax.swing.JButton();
        quebutton = new javax.swing.JButton();
        profRemove = new javax.swing.JButton();
        passReset = new javax.swing.JButton();
        reg = new javax.swing.JButton();
        BTNsearch = new javax.swing.JButton();
        jScrollPane1 = new javax.swing.JScrollPane();
        staffTable = new javax.swing.JTable();
        search = new javax.swing.JTextField();
        datetimeStaff = new javax.swing.JLabel();
        labelStaff = new javax.swing.JLabel();
        jLabel3 = new javax.swing.JLabel();
        jLabel1 = new javax.swing.JLabel();

        setLayout(null);

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

        STAFFtEXT.setForeground(new java.awt.Color(4, 167, 182));
        STAFFtEXT.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        STAFFtEXT.setText("STAFF RECORDS");
        add(STAFFtEXT);
        STAFFtEXT.setBounds(505, 26, 96, 15);

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

        quebutton.setForeground(new java.awt.Color(255, 255, 255));
        quebutton.setText("QUEUE LIST");
        quebutton.setBorder(null);
        quebutton.setBorderPainted(false);
        quebutton.setContentAreaFilled(false);
        quebutton.setFocusPainted(false);
        quebutton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                quebuttonActionPerformed(evt);
            }
        });
        add(quebutton);
        quebutton.setBounds(289, 26, 80, 15);

        profRemove.setBorderPainted(false);
        profRemove.setContentAreaFilled(false);
        add(profRemove);
        profRemove.setBounds(520, 140, 120, 30);

        passReset.setBorderPainted(false);
        passReset.setContentAreaFilled(false);
        add(passReset);
        passReset.setBounds(660, 140, 120, 30);

        reg.setBorderPainted(false);
        reg.setContentAreaFilled(false);
        reg.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                regActionPerformed(evt);
            }
        });
        add(reg);
        reg.setBounds(840, 135, 150, 40);

        BTNsearch.setBorderPainted(false);
        BTNsearch.setContentAreaFilled(false);
        BTNsearch.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                BTNsearchActionPerformed(evt);
            }
        });
        add(BTNsearch);
        BTNsearch.setBounds(35, 180, 30, 40);

        staffTable.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {
                {null, null, null, null, null},
                {null, null, null, null, null},
                {null, null, null, null, null},
                {null, null, null, null, null}
            },
            new String [] {
                "Name", "Username", "Email", "Status", "Date Created"
            }
        ) {
            @Override
            public Class<?> getColumnClass(int columnIndex) {
                return String.class;
            }
        });
        staffTable.setFocusable(false);
        staffTable.setGridColor(new java.awt.Color(255, 255, 255));
        staffTable.setOpaque(false);
        staffTable.setRequestFocusEnabled(false);
        staffTable.setShowHorizontalLines(true);
        
        // Set center alignment for all columns
        javax.swing.table.DefaultTableCellRenderer centerRenderer = new javax.swing.table.DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        for (int i = 0; i < staffTable.getColumnCount(); i++) {
            staffTable.getColumnModel().getColumn(i).setCellRenderer(centerRenderer);
        }
        staffTable.getTableHeader().setResizingAllowed(false);
        staffTable.getTableHeader().setReorderingAllowed(false);
        jScrollPane1.setViewportView(staffTable);

        add(jScrollPane1);
        jScrollPane1.setBounds(10, 190, 980, 558);

        search.setFont(new java.awt.Font("Segoe UI", 0, 16)); // NOI18N
        add(search);
        search.setBounds(70, 140, 350, 31);

        datetimeStaff.setFont(new java.awt.Font("Segoe UI", 0, 10)); // NOI18N
        datetimeStaff.setForeground(new java.awt.Color(255, 255, 255));
        datetimeStaff.setText("10:44 AM 17 August, Sunday");
        add(datetimeStaff);
        datetimeStaff.setBounds(820, 40, 170, 20);

        labelStaff.setFont(new java.awt.Font("Segoe UI", 1, 16)); // NOI18N
        labelStaff.setForeground(new java.awt.Color(255, 255, 255));
        // Set the staff first name instead of "Admin!"
        String firstName = getStaffFirstNameFromDB();
        labelStaff.setText(firstName + "!");
        add(labelStaff);
        labelStaff.setBounds(870, 10, 70, 30);

        jLabel3.setFont(new java.awt.Font("Segoe UI", 1, 16)); // NOI18N
        jLabel3.setForeground(new java.awt.Color(255, 255, 255));
        jLabel3.setText("Hello,");
        add(jLabel3);
        jLabel3.setBounds(820, 10, 50, 30);

        jLabel1.setIcon(new javax.swing.ImageIcon(getClass().getResource("/cephra/Cephra Images/Staff .png"))); // NOI18N
        add(jLabel1);
        jLabel1.setBounds(0, 0, 1010, 750);
    }// </editor-fold>//GEN-END:initComponents

    private void exitloginActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exitloginActionPerformed
        Window w = SwingUtilities.getWindowAncestor(StaffRecord.this);
        if (w instanceof cephra.Frame.Admin) {
            ((cephra.Frame.Admin) w).switchPanel(new cephra.Admin.Login());
        }
    }//GEN-LAST:event_exitloginActionPerformed

    private void businessbuttonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_businessbuttonActionPerformed
        Window w = SwingUtilities.getWindowAncestor(StaffRecord.this);
        if (w instanceof cephra.Frame.Admin) {
            ((cephra.Frame.Admin) w).switchPanel(new cephra.Admin.Business_Overview());
        }
    }//GEN-LAST:event_businessbuttonActionPerformed

    private void historybuttonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_historybuttonActionPerformed
        Window w = SwingUtilities.getWindowAncestor(StaffRecord.this);
        if (w instanceof cephra.Frame.Admin) {
            ((cephra.Frame.Admin) w).switchPanel(new History());
        }
    }//GEN-LAST:event_historybuttonActionPerformed

    private void BaybuttonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_BaybuttonActionPerformed
        Window w = SwingUtilities.getWindowAncestor(StaffRecord.this);
        if (w instanceof cephra.Frame.Admin) {
            ((cephra.Frame.Admin) w).switchPanel(new BayManagement());
        }
    }//GEN-LAST:event_BaybuttonActionPerformed

    private void quebuttonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_quebuttonActionPerformed
        Window w = SwingUtilities.getWindowAncestor(StaffRecord.this);
        if (w instanceof cephra.Frame.Admin) {
            ((cephra.Frame.Admin) w).switchPanel(new Queue());
        }
    }//GEN-LAST:event_quebuttonActionPerformed

    private void BTNsearchActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_BTNsearchActionPerformed
        

    }//GEN-LAST:event_BTNsearchActionPerformed

    private void regActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_regActionPerformed
        Window w = SwingUtilities.getWindowAncestor(StaffRecord.this);
        if (w instanceof cephra.Frame.Admin) {
            ((cephra.Frame.Admin) w).switchPanel(new AdminRegister());
        }
       
    }//GEN-LAST:event_regActionPerformed

    private void setupDateTimeTimer() {
        updateDateTime();
        javax.swing.Timer timer = new javax.swing.Timer(1000, new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                updateDateTime();
            }
        });
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
    
    
    private void searchStaff() {
        String keyword = search.getText().trim().toLowerCase();
        DefaultTableModel model = (DefaultTableModel) staffTable.getModel();
        model.setRowCount(0);
        
        if (keyword.isEmpty()) {
            refreshStaffTable();
            return;
        } 
        
        for (String[] staff : StaffData.getStaffList()) {
            String username = staff[3]; // Username is at index 3
            
            // Skip admin account - it's a special system account
            if ("admin".equalsIgnoreCase(username)) {
                continue;
            }
            
            String name = staff[0];
            if (name.toLowerCase().contains(keyword)) {
                // staff array: [name, firstname, lastname, username, email, status, password, date_created]
                model.addRow(new Object[]{staff[0], staff[3], staff[4], staff[5], staff[7]});
            }
        }
    }
    
    private void resetSelectedStaffPassword() {
        int selectedRow = staffTable.getSelectedRow();
        if (selectedRow == -1) {
            return; // No row selected
        }
        
        // Get the staff name and username from the selected row
        String staffName = (String) staffTable.getValueAt(selectedRow, 0);
        String staffUsername = (String) staffTable.getValueAt(selectedRow, 1);
        
        // Generate random 6-digit password
        java.util.Random random = new java.util.Random();
        String newPassword = String.format("%06d", random.nextInt(1000000));
        
        // Show confirmation dialog
        int result = javax.swing.JOptionPane.showConfirmDialog(
            this,
            "Reset password for:\nName: " + staffName + "\nUsername: " + staffUsername + "\n\nNew password will be: " + newPassword + "\n\nProceed with reset?",
            "Reset Staff Password",
            javax.swing.JOptionPane.YES_NO_OPTION,
            javax.swing.JOptionPane.QUESTION_MESSAGE
        );
        
        if (result == javax.swing.JOptionPane.YES_OPTION) {
            // Reset password in database
            boolean reset = cephra.Database.CephraDB.resetStaffPassword(staffUsername, newPassword);
            if (reset) {
                javax.swing.JOptionPane.showMessageDialog(
                    this,
                    "Password reset successfully!\n\nNew password: " + newPassword + "\n\nPlease inform the staff member of the new password.",
                    "Success",
                    javax.swing.JOptionPane.INFORMATION_MESSAGE
                );
                // Refresh the table to show updated data
                refreshStaffTable();
            } else {
                javax.swing.JOptionPane.showMessageDialog(
                    this,
                    "Error resetting password!",
                    "Error",
                    javax.swing.JOptionPane.ERROR_MESSAGE
                );
            }
        }
    }
    
    private void removeSelectedStaff() {
        int selectedRow = staffTable.getSelectedRow();
        if (selectedRow == -1) {
            return; // No row selected
        }
        
        // Get the staff name from the selected row
        String staffName = (String) staffTable.getValueAt(selectedRow, 0);
        String staffUsername = (String) staffTable.getValueAt(selectedRow, 1);
        
        // Show confirmation dialog
        int result = javax.swing.JOptionPane.showConfirmDialog(
            this,
            "Are you sure you want to remove this account?\n\nName: " + staffName + "\nUsername: " + staffUsername,
            "Confirm Staff Removal",
            javax.swing.JOptionPane.YES_NO_OPTION,
            javax.swing.JOptionPane.QUESTION_MESSAGE
        );
        
        if (result == javax.swing.JOptionPane.YES_OPTION) {
            // Remove from database
            boolean removed = cephra.Database.CephraDB.removeStaff(staffUsername);
            if (removed) {
                javax.swing.JOptionPane.showMessageDialog(
                    this,
                    "Staff account removed successfully!",
                    "Success",
                    javax.swing.JOptionPane.INFORMATION_MESSAGE
                );
                // Refresh the table
                refreshStaffTable();
                // Disable the button since no row is selected
                profRemove.setEnabled(false);
            } else {
                javax.swing.JOptionPane.showMessageDialog(
                    this,
                    "Error removing staff account!",
                    "Error",
                    javax.swing.JOptionPane.ERROR_MESSAGE
                );
            }
        }
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton BTNsearch;
    private javax.swing.JButton Baybutton;
    private javax.swing.JLabel STAFFtEXT;
    private javax.swing.JButton businessbutton;
    private javax.swing.JLabel datetimeStaff;
    private javax.swing.JButton exitlogin;
    private javax.swing.JButton historybutton;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JLabel labelStaff;
    private javax.swing.JButton passReset;
    private javax.swing.JButton profRemove;
    private javax.swing.JButton quebutton;
    private javax.swing.JButton reg;
    private javax.swing.JTextField search;
    private javax.swing.JTable staffTable;
    // End of variables declaration//GEN-END:variables
    
   
    private String getStaffFirstNameFromDB() {
        try {
            // Get the logged-in username from the admin frame
            java.awt.Window window = javax.swing.SwingUtilities.getWindowAncestor(this);
            if (window instanceof cephra.Frame.Admin) {
                java.lang.reflect.Field usernameField = window.getClass().getDeclaredField("loggedInUsername");
                usernameField.setAccessible(true);
                String username = (String) usernameField.get(window);
                
                if (username != null && !username.isEmpty()) {
                    // Use the updated CephraDB method that queries staff_records.firstname
                    return cephra.Database.CephraDB.getStaffFirstName(username);
                }
            }
        } catch (Exception e) {
            System.err.println("Error getting staff first name: " + e.getMessage());
        }
        return "Admin"; // Fallback
    }
    
    private void refreshStaffTable() {
        DefaultTableModel model = (DefaultTableModel) staffTable.getModel();
        model.setRowCount(0); // Clear existing data
        
        try {
            // Get staff data from database
            java.util.List<Object[]> staffData = cephra.Database.CephraDB.getAllStaff();
            
            for (Object[] staff : staffData) {
                // staff array: [name, firstname, lastname, username, email, status, password]
                String username = (String) staff[3];        // Username
                
                // Skip admin account - it's a special system account
                if ("admin".equalsIgnoreCase(username)) {
                    continue;
                }
                
                String fullName = (String) staff[0];        // Combined name (firstname + lastname)
                String email = (String) staff[4];           // Email
                String status = (String) staff[5];          // Status
                String dateCreated = (String) staff[7];     // Date created
                
                // Add row to table: Name, Username, Email, Status, Date Created
                model.addRow(new Object[]{fullName, username, email, status, dateCreated});
            }
        } catch (Exception e) {
            System.err.println("Error loading staff data: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
