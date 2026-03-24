package cephra.Phone.Utilities;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.*;
import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Modern drag and drop image selector with visual feedback
 */
public class DragDropImageSelector extends JDialog {
    private File selectedFile;
    private JLabel dropArea;
    private JLabel instructionLabel;
    private JButton browseButton;
    private JButton cancelButton;
    private boolean fileSelected = false;
    
    public DragDropImageSelector(Window parent) {
        super(parent, "📸 Select Profile Picture", ModalityType.APPLICATION_MODAL);
        
        setSize(500, 400);
        setLocationRelativeTo(parent);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setResizable(false);
        
        initComponents();
        setupDragAndDrop();
    }
    
    private void initComponents() {
        setLayout(new BorderLayout());
        
        // Main panel
        JPanel mainPanel = new JPanel(new BorderLayout(20, 20));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(30, 30, 30, 30));
        mainPanel.setBackground(new Color(248, 249, 250));
        
        // Title
        JLabel titleLabel = new JLabel("Add Your Profile Picture", JLabel.CENTER);
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 24));
        titleLabel.setForeground(new Color(31, 41, 55));
        mainPanel.add(titleLabel, BorderLayout.NORTH);
        
        // Drop area
        dropArea = new JLabel();
        dropArea.setPreferredSize(new Dimension(400, 200));
        dropArea.setBorder(BorderFactory.createDashedBorder(
            new Color(156, 163, 175), 3, 10, 3, true));
        dropArea.setBackground(new Color(249, 250, 251));
        dropArea.setOpaque(true);
        dropArea.setHorizontalAlignment(JLabel.CENTER);
        dropArea.setVerticalAlignment(JLabel.CENTER);
        
        // Instruction label
        instructionLabel = new JLabel("<html><div style='text-align: center;'>" +
            "<h3>📁 Drag & Drop your image here</h3>" +
            "<p style='color: #6B7280; font-size: 14px;'>" +
            "or click the browse button below</p>" +
            "<p style='color: #9CA3AF; font-size: 12px;'>" +
            "Supports: JPG, PNG, GIF, BMP, WebP<br>" +
            "Max size: 10MB</p></div></html>");
        instructionLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        
        dropArea.setLayout(new BorderLayout());
        dropArea.add(instructionLabel, BorderLayout.CENTER);
        
        mainPanel.add(dropArea, BorderLayout.CENTER);
        
        // Button panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 0));
        buttonPanel.setBackground(new Color(248, 249, 250));
        
        browseButton = new JButton("📂 Browse Files");
        browseButton.setFont(new Font("Segoe UI", Font.BOLD, 14));
        browseButton.setBackground(new Color(59, 130, 246));
        browseButton.setForeground(Color.WHITE);
        browseButton.setPreferredSize(new Dimension(150, 40));
        browseButton.setFocusPainted(false);
        browseButton.setBorderPainted(false);
        
        cancelButton = new JButton("✗ Cancel");
        cancelButton.setFont(new Font("Segoe UI", Font.BOLD, 14));
        cancelButton.setBackground(new Color(239, 68, 68));
        cancelButton.setForeground(Color.WHITE);
        cancelButton.setPreferredSize(new Dimension(150, 40));
        cancelButton.setFocusPainted(false);
        cancelButton.setBorderPainted(false);
        
        buttonPanel.add(browseButton);
        buttonPanel.add(cancelButton);
        
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);
        
        add(mainPanel);
        
        // Add button listeners
        browseButton.addActionListener(e -> openFileChooser());
        cancelButton.addActionListener(e -> {
            fileSelected = false;
            dispose();
        });
    }
    
    private void setupDragAndDrop() {
        new DropTarget(dropArea, new DropTargetListener() {
            @Override
            public void dragEnter(DropTargetDragEvent dtde) {
                updateDropArea(true);
            }
            
            @Override
            public void dragOver(DropTargetDragEvent dtde) {
                // Keep visual feedback
            }
            
            @Override
            public void dropActionChanged(DropTargetDragEvent dtde) {
                // Handle action changes if needed
            }
            
            @Override
            public void dragExit(DropTargetEvent dtde) {
                updateDropArea(false);
            }
            
            @Override
            public void drop(DropTargetDropEvent dtde) {
                updateDropArea(false);
                
                try {
                    Transferable transferable = dtde.getTransferable();
                    if (transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                        dtde.acceptDrop(DnDConstants.ACTION_COPY);
                        
                        @SuppressWarnings("unchecked")
                        List<File> files = (List<File>) transferable.getTransferData(DataFlavor.javaFileListFlavor);
                        
                        if (!files.isEmpty()) {
                            File file = files.get(0);
                            if (validateImageFile(file)) {
                                selectedFile = file;
                                fileSelected = true;
                                showFilePreview(file);
                                dtde.dropComplete(true);
                            } else {
                                dtde.dropComplete(false);
                            }
                        }
                    } else {
                        dtde.rejectDrop();
                    }
                } catch (IOException | UnsupportedFlavorException e) {
                    dtde.rejectDrop();
                    JOptionPane.showMessageDialog(DragDropImageSelector.this, 
                        "Error processing dropped file: " + e.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        });
    }
    
    private void updateDropArea(boolean isDragOver) {
        if (isDragOver) {
            dropArea.setBackground(new Color(219, 234, 254));
            dropArea.setBorder(BorderFactory.createDashedBorder(
                new Color(59, 130, 246), 3, 10, 3, true));
            instructionLabel.setText("<html><div style='text-align: center;'>" +
                "<h3 style='color: #3B82F6;'>🎯 Drop your image here!</h3>" +
                "<p style='color: #6B7280; font-size: 14px;'>" +
                "Release to select this file</p></div></html>");
        } else {
            dropArea.setBackground(new Color(249, 250, 251));
            dropArea.setBorder(BorderFactory.createDashedBorder(
                new Color(156, 163, 175), 3, 10, 3, true));
            instructionLabel.setText("<html><div style='text-align: center;'>" +
                "<h3>📁 Drag & Drop your image here</h3>" +
                "<p style='color: #6B7280; font-size: 14px;'>" +
                "or click the browse button below</p>" +
                "<p style='color: #9CA3AF; font-size: 12px;'>" +
                "Supports: JPG, PNG, GIF, BMP, WebP<br>" +
                "Max size: 10MB</p></div></html>");
        }
    }
    
    private void showFilePreview(File file) {
        instructionLabel.setText("<html><div style='text-align: center;'>" +
            "<h3 style='color: #10B981;'>✅ File Selected!</h3>" +
            "<p style='color: #6B7280; font-size: 14px;'>" +
            "<strong>" + file.getName() + "</strong></p>" +
            "<p style='color: #9CA3AF; font-size: 12px;'>" +
            "Size: " + formatFileSize(file.length()) + "</p>" +
            "<p style='color: #10B981; font-size: 12px;'>" +
            "Click 'Browse Files' to select a different image</p></div></html>");
        
        dropArea.setBackground(new Color(236, 253, 245));
        dropArea.setBorder(BorderFactory.createDashedBorder(
            new Color(16, 185, 129), 3, 10, 3, true));
    }
    
    private void openFileChooser() {
        File file = CustomImageFileChooser.selectImageFile(this);
        if (file != null) {
            selectedFile = file;
            fileSelected = true;
            showFilePreview(file);
        }
    }
    
    private boolean validateImageFile(File file) {
        if (!file.exists()) {
            JOptionPane.showMessageDialog(this, 
                "File not found.", "Error", JOptionPane.ERROR_MESSAGE);
            return false;
        }
        
        long maxSize = 10 * 1024 * 1024; // 10MB
        if (file.length() > maxSize) {
            JOptionPane.showMessageDialog(this, 
                "Image file is too large. Please select an image smaller than 10MB.",
                "File Too Large", JOptionPane.ERROR_MESSAGE);
            return false;
        }
        
        String fileName = file.getName().toLowerCase();
        String[] validExtensions = {".jpg", ".jpeg", ".png", ".gif", ".bmp", ".webp"};
        boolean validExtension = false;
        for (String ext : validExtensions) {
            if (fileName.endsWith(ext)) {
                validExtension = true;
                break;
            }
        }
        
        if (!validExtension) {
            JOptionPane.showMessageDialog(this, 
                "Please select a valid image file (JPG, PNG, GIF, BMP, or WebP).",
                "Invalid File Type", JOptionPane.ERROR_MESSAGE);
            return false;
        }
        
        return true;
    }
    
    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }
    
    public File getSelectedFile() {
        return selectedFile;
    }
    
    public boolean isFileSelected() {
        return fileSelected;
    }
    
    public static File selectImageFile(Component parent) {
        DragDropImageSelector selector = new DragDropImageSelector(
            SwingUtilities.getWindowAncestor(parent));
        selector.setVisible(true);
        
        return selector.isFileSelected() ? selector.getSelectedFile() : null;
    }
}
