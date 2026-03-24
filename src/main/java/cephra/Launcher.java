package cephra;
import cephra.Database.CephraDB;
public final class Launcher {

	public static void main(String[] args) {

		// Shut down HikariCP pool cleanly on JVM exit
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			cephra.Phone.Utilities.ChargingManager.getInstance().stopAllCharging();
			cephra.Database.DatabaseConnection.close();
			System.out.println("Launcher: DB pool closed.");
		}, "shutdown-hook"));

		try {
			CephraDB.initializeDatabase();
			CephraDB.validateDatabaseIntegrity();
			cephra.Phone.Utilities.QueueFlow.refreshCountersFromDatabase();
			
			// Initialize background charging manager for global monitoring
			cephra.Phone.Utilities.ChargingManager.getInstance();
			System.out.println("Launcher: Initialized background charging manager");
		} catch (Exception e) {
			System.err.println("Database connection failed: " + e.getMessage());
		}
		javax.swing.SwingUtilities.invokeLater(() -> {
			
			cephra.Frame.Admin admin = new cephra.Frame.Admin();
			java.awt.Toolkit toolkit = java.awt.Toolkit.getDefaultToolkit();
			java.awt.Dimension screenSize = toolkit.getScreenSize();
			admin.setLocation(screenSize.width - admin.getWidth(), 0);
			admin.setVisible(true);
			
			cephra.Frame.Phone phone = new cephra.Frame.Phone();
			phone.setVisible(true);
			phone.toFront();
			phone.requestFocus();
		});
	}
	private Launcher() {}
}