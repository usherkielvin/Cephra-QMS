package cephra.Phone.Utilities;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.*;
import java.awt.Desktop;
import java.io.File;
import java.util.List;

/**
 * Simple, clean drag and drop image selector
 * Much simpler than the complex version - just drag, drop, done!
 */
public class SimpleDragDropSelector extends JDialog {
    
    private File selectedFile;
    private JLabel dropArea;
    private JLabel instructionLabel;
    private JButton browseButton;
    private JButton cancelButton;
    
    public SimpleDragDropSelector(Component parent) {
        super((Frame) SwingUtilities.getWindowAncestor(parent), "Select Profile Picture", true);
        initComponents();
        setupDragAndDrop();
        pack();
        
        // Center perfectly in the phone frame
        Frame parentFrame = (Frame) SwingUtilities.getWindowAncestor(parent);
        if (parentFrame != null) {
            int x = parentFrame.getX() + (parentFrame.getWidth() - getWidth()) / 2;
            int y = parentFrame.getY() + (parentFrame.getHeight() - getHeight()) / 2;
            setLocation(x, y);
        } else {
            setLocationRelativeTo(parent);
        }
    }
    
    private void initComponents() {
        setLayout(new BorderLayout());
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        setResizable(false);
        setBackground(Color.WHITE);
        setUndecorated(true); // Remove title bar for cleaner look
        
        // Main panel with clean design - smaller
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        mainPanel.setBackground(Color.WHITE);
        
        // Drop area - smaller and more compact
        dropArea = new JLabel();
        dropArea.setPreferredSize(new Dimension(220, 140));
        dropArea.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(220, 220, 220), 2),
            BorderFactory.createEmptyBorder(20, 20, 20, 20)
        ));
        dropArea.setBackground(new Color(250, 250, 250));
        dropArea.setOpaque(true);
        dropArea.setHorizontalAlignment(SwingConstants.CENTER);
        dropArea.setVerticalAlignment(SwingConstants.CENTER);
        
        // Clean instruction text - smaller
        instructionLabel = new JLabel("<html><center><b>📁 Drag & Drop Image</b><br><small>or click Browse</small></center></html>");
        instructionLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        instructionLabel.setForeground(new Color(80, 80, 80));
        instructionLabel.setHorizontalAlignment(SwingConstants.CENTER);
        
        // Clean browse button - smaller
        browseButton = new JButton("Browse");
        browseButton.setPreferredSize(new Dimension(80, 28));
        browseButton.setBackground(new Color(70, 130, 180));
        browseButton.setForeground(Color.WHITE);
        browseButton.setFocusPainted(false);
        browseButton.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        browseButton.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
        
        // Layout the drop area
        JPanel dropPanel = new JPanel(new BorderLayout());
        dropPanel.setBackground(new Color(250, 250, 250));
        dropPanel.add(instructionLabel, BorderLayout.CENTER);
        dropPanel.add(browseButton, BorderLayout.SOUTH);
        
        dropArea.setLayout(new BorderLayout());
        dropArea.add(dropPanel, BorderLayout.CENTER);
        
        mainPanel.add(dropArea, BorderLayout.CENTER);
        
        // Clean button panel - smaller
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        buttonPanel.setBackground(Color.WHITE);
        buttonPanel.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
        
        cancelButton = new JButton("Cancel");
        cancelButton.setPreferredSize(new Dimension(70, 28));
        cancelButton.setBackground(new Color(120, 120, 120));
        cancelButton.setForeground(Color.WHITE);
        cancelButton.setFocusPainted(false);
        cancelButton.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        cancelButton.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
        
        buttonPanel.add(cancelButton);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);
        
        add(mainPanel);
        
        // Add listeners
        browseButton.addActionListener(e -> openFileChooser());
        cancelButton.addActionListener(e -> {
            selectedFile = null;
            dispose();
        });
    }
    
    private void setupDragAndDrop() {
        new DropTarget(dropArea, new DropTargetAdapter() {
            @Override
            public void dragEnter(DropTargetDragEvent dtde) {
                dropArea.setBackground(new Color(240, 248, 255));
                dropArea.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(70, 130, 180), 2),
                    BorderFactory.createEmptyBorder(20, 20, 20, 20)
                ));
                instructionLabel.setText("<html><center><b>📁 Drop Image Here!</b><br><small>Release to select</small></center></html>");
                instructionLabel.setForeground(new Color(70, 130, 180));
            }
            
            @Override
            public void dragExit(DropTargetEvent dtde) {
                resetDropArea();
            }
            
            @Override
            public void drop(DropTargetDropEvent dtde) {
                resetDropArea();
                
                try {
                    dtde.acceptDrop(DnDConstants.ACTION_COPY);
                    @SuppressWarnings("unchecked")
                    List<File> files = (List<File>) dtde.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                    
                    if (files != null && !files.isEmpty()) {
                        File file = files.get(0);
                        if (isValidImageFile(file)) {
                            selectedFile = file;
                            showSuccess();
                            dispose();
                        }
                    }
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(SimpleDragDropSelector.this,
                        "Error: " + e.getMessage(), "Drop Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        });
    }
    
    private void resetDropArea() {
        dropArea.setBackground(new Color(250, 250, 250));
        dropArea.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(220, 220, 220), 2),
            BorderFactory.createEmptyBorder(20, 20, 20, 20)
        ));
        instructionLabel.setText("<html><center><b>📁 Drag & Drop Image</b><br><small>or click Browse</small></center></html>");
        instructionLabel.setForeground(new Color(80, 80, 80));
    }
    
    private void showSuccess() {
        instructionLabel.setText("<html><center><b>✓ Image Selected!</b><br><br>Processing...</center></html>");
        instructionLabel.setForeground(new Color(0, 150, 0));
        dropArea.setBackground(new Color(240, 255, 240));
    }
    
    private void openFileChooser() {
        try {
            // Open system file explorer silently to Pictures folder
            File picturesDir = new File(System.getProperty("user.home"), "Pictures");
            if (!picturesDir.exists()) {
                picturesDir = new File(System.getProperty("user.home"), "Documents");
            }
            
            Desktop.getDesktop().open(picturesDir);
            // No popup - just open silently
                
        } catch (Exception e) {
            // Fallback to Java file chooser if system explorer fails
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setDialogTitle("Select Profile Picture");
            fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
            fileChooser.setAcceptAllFileFilterUsed(false);
            
            javax.swing.filechooser.FileNameExtensionFilter imageFilter = 
                new javax.swing.filechooser.FileNameExtensionFilter(
                    "Image Files (*.jpg, *.jpeg, *.png, *.gif, *.bmp, *.webp)",
                    "jpg", "jpeg", "png", "gif", "bmp", "webp"
                );
            fileChooser.setFileFilter(imageFilter);
            
            File picturesDir = new File(System.getProperty("user.home"), "Pictures");
            if (picturesDir.exists() && picturesDir.isDirectory()) {
                fileChooser.setCurrentDirectory(picturesDir);
            }
            
            int result = fileChooser.showOpenDialog(this);
            if (result == JFileChooser.APPROVE_OPTION) {
                File file = fileChooser.getSelectedFile();
                if (isValidImageFile(file)) {
                    selectedFile = file;
                    showSuccess();
                    dispose();
                }
            }
        }
    }
    
    public File getSelectedFile() {
        return selectedFile;
    }
    
    private boolean isValidImageFile(File file) {
        if (file == null || !file.exists() || !file.isFile()) {
            return false;
        }
        
        String fileName = file.getName().toLowerCase();
        String[] validExtensions = {"jpg", "jpeg", "png", "gif", "bmp", "webp"};
        
        for (String ext : validExtensions) {
            if (fileName.endsWith("." + ext)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Show the simple drag and drop dialog
     * @param parent The parent component
     * @return Selected file or null if cancelled
     */
    public static File showDialog(Component parent) {
        SimpleDragDropSelector dialog = new SimpleDragDropSelector(parent);
        dialog.setVisible(true);
        return dialog.getSelectedFile();
    }
}
