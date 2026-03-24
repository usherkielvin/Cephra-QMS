package cephra.Frame;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

public class Phone extends javax.swing.JFrame {

    private Point dragStartPoint;
    private JLabel Iphoneframe;

    public Phone() {
        setUndecorated(true);
        initComponents();
        setSize(370, 750);
        setResizable(false);
        setAppIcon();
        addEscapeKeyListener();
        makeDraggable();
        getContentPane().setLayout(null);
        setShape(new java.awt.geom.RoundRectangle2D.Double(0, 0, 370, 750, 120, 120));

        cephra.Phone.Utilities.AppState.initializeCarLinkingState();
        switchPanel(new cephra.Phone.UserProfile.Loading_Screen());
        PhoneFrame();
        updateTime();
        startTimeTimer();
    }

    private void setAppIcon() {
        java.net.URL iconUrl = getClass().getResource("/cephra/Cephra Images/Applogo.png");
        setIconImage(new javax.swing.ImageIcon(iconUrl).getImage());
    }

    private void addEscapeKeyListener() {
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent evt) {
                if (evt.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    System.exit(0);
                }
            }
        });
        setFocusable(true);
    }

    private void makeDraggable() {
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                dragStartPoint = e.getPoint();
            }
        });

        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                if (dragStartPoint != null) {
                    Point currentLocation = getLocation();
                    setLocation(
                        currentLocation.x + e.getX() - dragStartPoint.x,
                        currentLocation.y + e.getY() - dragStartPoint.y
                    );
                }
            }
        });
    }

    private void PhoneFrame() {
        Iphoneframe = new JLabel();
        Iphoneframe.setIcon(new javax.swing.ImageIcon(getClass().getResource("/cephra/Cephra Images/PHONEFRAME.png")));
        Iphoneframe.setBounds(0, 0, 370, 750);
        Iphoneframe.setHorizontalAlignment(SwingConstants.CENTER);
        Iphoneframe.setOpaque(false);
        getRootPane().getLayeredPane().add(Iphoneframe, JLayeredPane.DRAG_LAYER);
        
        SwingUtilities.invokeLater(() -> {
            if (jLabel1.getParent() == getContentPane()) {
                getContentPane().remove(jLabel1);
            }
            jLabel1.setOpaque(false);
            jLabel1.setForeground(Color.BLACK);
            jLabel1.setBounds(39, 21, 55, 20);
            jLabel1.setFont(new java.awt.Font("Segoe UI Semibold", 0, 12));
            getRootPane().getLayeredPane().add(jLabel1, JLayeredPane.MODAL_LAYER);
            getRootPane().getLayeredPane().moveToFront(jLabel1);
            getRootPane().revalidate();
            getRootPane().repaint();
        });
    }

    public void switchPanel(javax.swing.JPanel newPanel) {
        getContentPane().removeAll();
        newPanel.setBounds(0, -6, 370, 756);
        getContentPane().add(newPanel);
        revalidate();
        repaint();
        ensureIphoneFrameOnTop();
    }

    public void ensureIphoneFrameOnTop() {
        if (Iphoneframe != null) {
            getRootPane().getLayeredPane().moveToFront(Iphoneframe);
        }
        if (jLabel1 != null) {
            getRootPane().getLayeredPane().moveToFront(jLabel1);
        }
    }
    
    private void updateTime() {
        if (jLabel1 != null) {
            java.time.LocalTime now = java.time.LocalTime.now();
            java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("h:mm a");
            jLabel1.setText(now.format(formatter));
        }
    }
    
    private void startTimeTimer() {
        javax.swing.Timer timer = new javax.swing.Timer(1000, e -> updateTime());
        timer.setRepeats(true);
        timer.start();
    }

    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jLabel1 = new javax.swing.JLabel();

        getContentPane().setLayout(null);

        jLabel1.setFont(new java.awt.Font("Segoe UI Semibold", 0, 12)); // NOI18N
        jLabel1.setText("12:24");
        getContentPane().add(jLabel1);
        jLabel1.setBounds(0, 0, 29, 16);
    }// </editor-fold>//GEN-END:initComponents

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel jLabel1;
    // End of variables declaration//GEN-END:variables
}
