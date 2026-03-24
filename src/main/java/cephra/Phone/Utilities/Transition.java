package cephra.Phone.Utilities;

import javax.swing.SwingUtilities;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import javax.swing.Timer;

public class Transition extends javax.swing.JPanel {
   
    public Transition() {
        initComponents();
        setPreferredSize(new java.awt.Dimension(370, 750));
        setSize(370, 750);
        
        // Make the panel draggable
        makeDraggable();
        // Align label like Porsche screens
        setupLabelPosition();
         new Timer(3200, e -> {
                java.awt.Window w = javax.swing.SwingUtilities.getWindowAncestor(Transition.this);
        if (w instanceof cephra.Frame.Phone) {
            ((cephra.Frame.Phone) w).switchPanel(new cephra.Phone.UserProfile.User_Login());
        }
               
             
            }).start();
        
        
    }

    // Match label position to Porsche-style screens
    private void setupLabelPosition() {
        if (jLabel1 != null) {
            jLabel1.setBounds(0, 0, 370, 750);
        }
        if (jLabel2 != null) {
            jLabel2.setBounds(0, 0, 370, 750);
        }
    }

    @Override
    public void addNotify() {
        super.addNotify();
        javax.swing.SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                setupLabelPosition();
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
                    java.awt.Window window = SwingUtilities.getWindowAncestor(Transition.this);
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

        jLabel1 = new javax.swing.JLabel();
        jLabel2 = new javax.swing.JLabel();

        setMaximumSize(new java.awt.Dimension(370, 750));
        setPreferredSize(new java.awt.Dimension(370, 750));
        setLayout(null);

        jLabel1.setIcon(new javax.swing.ImageIcon(getClass().getResource("/cephra/Cephra Images/sad.gif"))); // NOI18N
        add(jLabel1);
        jLabel1.setBounds(0, 0, 370, 750);

        jLabel2.setIcon(new javax.swing.ImageIcon(getClass().getResource("/cephra/Cephra Images/Coolintro.gif"))); // NOI18N
        add(jLabel2);
        jLabel2.setBounds(0, 0, 370, 750);
    }// </editor-fold>//GEN-END:initComponents


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    // End of variables declaration//GEN-END:variables
}
