package cephra.Database;

import java.sql.*;
import java.util.*;
import java.time.*;
import java.time.format.*;

/**
 * CephraDB - Main database management class for the Cephra charging station system.
 * 
 * This class provides comprehensive database operations including:
 * - User authentication and management
 * - Battery level tracking
 * - Queue ticket management
 * - Charging history and payment processing
 * - Staff management
 * - System settings
 * 
 * All database operations are performed using prepared statements for security
 * and the class includes comprehensive error handling and logging.
 */
public class CephraDB {

    private static class User {
        final String username;
        final String email;

        public User(String username, String email) {
            this.username = username;
            this.email = email;
        }
    }
    private static User currentPhoneUser;
    private static User currentAdminUser;
    
    public static void initializeDatabase() {
        try (Connection conn = cephra.Database.DatabaseConnection.getConnection()) {
            // Check if all required tables exist
            String[] requiredTables = {
                "users", "battery_levels", "active_tickets", "otp_codes",
                "queue_tickets", "charging_history", "staff_records", 
                "charging_bays", "payment_transactions", "system_settings",
                "wallet_balance", "wallet_transactions", "user_points", 
                "reward_transactions", "waiting_grid", "charging_grid"
            };
            
            boolean allTablesExist = true;
            for (String tableName : requiredTables) {
                if (!tableExists(conn, tableName)) {
                    System.err.println("Warning: " + tableName + " table does not exist.");
                    allTablesExist = false;
                }
            }
            
            if (!allTablesExist) {
                System.err.println("Some required database tables are missing.");
            }
            
            // Clean up any existing duplicate battery level entries
            cleanupDuplicateBatteryLevels();
            
            // Also clean up duplicates for all users to prevent future issues
            cleanupAllDuplicateBatteryLevels();
            
            // Clean up any orphaned queue tickets (tickets in queue but already in history)
            cleanupOrphanedQueueTickets();
            
            // Clean up admin users from users table (they should be in staff_records)
            cleanupAdminFromUsersTable();
            
            // Verify database connection and basic functionality
            verifyDatabaseConnection(conn);
        } catch (SQLException e) {
            System.err.println("Failed to initialize database: " + e.getMessage());
            System.err.println("Please ensure:");
            System.err.println("1. MySQL service / Workbench is running");
            System.err.println("2. Database 'cephradb' exists");
            System.err.println("3. All tables are created using init.sql");
            e.printStackTrace();
        }
    }
    
    // Helper method to check if a table exists
    private static boolean tableExists(Connection conn, String tableName) {
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = ?")) {
            stmt.setString(1, tableName);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {
            System.err.println("Error checking if table exists: " + e.getMessage());
        }
        return false;
    }

    // Method to check if the given credentials are valid (for phone users)
    public static boolean validateLogin(String username, String password) {
        try (Connection conn = cephra.Database.DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT username, email, password FROM users WHERE username = ? AND password = ?")) {
            
            stmt.setString(1, username);
            stmt.setString(2, password);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    // Set the current phone user when login is successful
                    currentPhoneUser = new User(
                            rs.getString("username"),
                            rs.getString("email")
                    );
                    return true;
                }
            }
        } catch (SQLException e) {
            System.err.println("Error validating login: " + e.getMessage());
            e.printStackTrace();
        }
        return false;
    }
    
    // Method to get the current logged-in username (phone user)
    public static String getCurrentUsername() {
        return currentPhoneUser != null ? currentPhoneUser.username : "";
    }
    
    // Alias method for compatibility with Rewards system
    public static String getCurrentPhoneUsername() {
        return getCurrentUsername();
    }
    
    // Method to get the current logged-in user's email (phone user)
    public static String getCurrentEmail() {
        return currentPhoneUser != null ? currentPhoneUser.email : "";
    }
    
    // Method to get the current logged-in user's firstname (phone user)
    public static String getCurrentFirstname() {
        return getCurrentUserField("firstname");
    }
    
    // Method to get the current logged-in user's lastname (phone user)
    public static String getCurrentLastname() {
        return getCurrentUserField("lastname");
    }
    
    // Helper method to get a specific field from the current user
    private static String getCurrentUserField(String fieldName) {
        if (currentPhoneUser == null || currentPhoneUser.username == null) {
            return "";
        }
        
        try (Connection conn = cephra.Database.DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT " + fieldName + " FROM users WHERE username = ?")) {
            
            stmt.setString(1, currentPhoneUser.username);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString(fieldName);
                }
            }
        } catch (SQLException e) {
            System.err.println("Error getting current user's " + fieldName + ": " + e.getMessage());
            e.printStackTrace();
        }
        return "";
    }
    
    // Method to logout the current phone user
    public static void logoutCurrentUser() {
        currentPhoneUser = null;
    }
    
    // Method to check if any user is currently logged in
    public static boolean isUserLoggedIn() {
        return currentPhoneUser != null;
    }
    
    // Method to get the current admin username
    public static String getCurrentAdminUsername() {
        return currentAdminUser != null ? currentAdminUser.username : "";
    }
    
    // Method to get the current admin email
    public static String getCurrentAdminEmail() {
        return currentAdminUser != null ? currentAdminUser.email : "";
    }

    // Method to add a new user to the database
    public static boolean addUser(String firstname, String lastname, String username, String email, String password) {
        try (Connection conn = cephra.Database.DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "INSERT INTO users (firstname, lastname, username, email, password) VALUES (?, ?, ?, ?, ?)")) {
            
            stmt.setString(1, firstname);
            stmt.setString(2, lastname);
            stmt.setString(3, username);
            stmt.setString(4, email);
            stmt.setString(5, password);
            
            int rowsAffected = stmt.executeUpdate();
            return rowsAffected > 0;
            
        } catch (SQLException e) {
            // Check if it's a duplicate key error
            if (e.getMessage().contains("Duplicate entry")) {
                System.err.println("User already exists: " + e.getMessage());
                return false;
            }
            System.err.println("Error adding user: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    // Method to check if a user exists
    public static boolean userExists(String username) {
        try (Connection conn = cephra.Database.DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT username FROM users WHERE username = ?")) {
            
            stmt.setString(1, username);
            
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next(); // If there's a result, user exists
            }
        } catch (SQLException e) {
            System.err.println("Error checking if user exists: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    // Method to find a user by email
    public static User findUserByEmail(String email) {
        try (Connection conn = cephra.Database.DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT username, email, password FROM users WHERE email = ?")) {
            
            stmt.setString(1, email);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new User(
                            rs.getString("username"),
                            rs.getString("email")
                    );
                }
            }
        } catch (SQLException e) {
            System.err.println("Error finding user by email: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }
    
    // Method to update a user's password
    public static boolean updateUserPassword(String email, String newPassword) {
        try (Connection conn = cephra.Database.DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "UPDATE users SET password = ? WHERE email = ?")) {
            
            stmt.setString(1, newPassword);
            stmt.setString(2, email);
            
            int rowsAffected = stmt.executeUpdate();
            return rowsAffected > 0;
            
        } catch (SQLException e) {
            System.err.println("Error updating password: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    // Method to update user profile information
    public static boolean updateUserProfile(String currentUsername, String newUsername, String firstName, String lastName, String email) {
        try (Connection conn = cephra.Database.DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "UPDATE users SET username = ?, firstname = ?, lastname = ?, email = ? WHERE username = ?")) {
            
            stmt.setString(1, newUsername);
            stmt.setString(2, firstName);
            stmt.setString(3, lastName);
            stmt.setString(4, email);
            stmt.setString(5, currentUsername);
            
            int rowsAffected = stmt.executeUpdate();
            boolean success = rowsAffected > 0;
            
            if (success) {
                System.out.println("CephraDB: Updated profile for user " + currentUsername + 
                    " - New Username: " + newUsername + ", Name: " + firstName + " " + lastName + ", Email: " + email);
            } else {
                System.err.println("CephraDB: No rows affected when updating profile for user " + currentUsername);
            }
            
            return success;
            
        } catch (SQLException e) {
            System.err.println("Error updating user profile: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    // Method to check if username is available
    public static boolean isUsernameAvailable(String username) {
        try (Connection conn = cephra.Database.DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT COUNT(*) FROM users WHERE username = ?")) {
            
            stmt.setString(1, username);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) == 0; // Returns true if username is available
                }
            }
        } catch (SQLException e) {
            System.err.println("Error checking username availability: " + e.getMessage());
        }
        return false;
    }
    
    // Method to get a user's current password by email
    public static String getUserPasswordByEmail(String email) {
        try (Connection conn = cephra.Database.DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT password FROM users WHERE email = ?")) {
            
            stmt.setString(1, email);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("password");
                }
            }
        } catch (SQLException e) {
            System.err.println("Error getting user password: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }
    
    // Method to generate and store a new 6-digit OTP
    public static String generateAndStoreOTP() {
        Random random = new Random();
        String generatedOTP = String.format("%06d", random.nextInt(1000000));
        
        // Determine which email to use for OTP storage
        String emailToUse = null;
        if (currentPhoneUser != null) {
            emailToUse = currentPhoneUser.email;
        } else if (cephra.Phone.Utilities.AppSessionState.userEmailForReset != null) {
            emailToUse = cephra.Phone.Utilities.AppSessionState.userEmailForReset;
        }
        
        // Store OTP in database if we have an email
        if (emailToUse != null) {
            try (Connection conn = cephra.Database.DatabaseConnection.getConnection();
                 PreparedStatement findUserStmt = conn.prepareStatement(
                         "SELECT username FROM users WHERE email = ?")) {
                
                findUserStmt.setString(1, emailToUse);
                ResultSet userRs = findUserStmt.executeQuery();
                
                if (userRs.next()) {
                    String username = userRs.getString("username");
                    
                    // Delete any existing OTP for this username
                    try (PreparedStatement deleteStmt = conn.prepareStatement(
                            "DELETE FROM otp_codes WHERE username = ?")) {
                        deleteStmt.setString(1, username);
                        deleteStmt.executeUpdate();
                    }
                    
                    // Insert new OTP with expiration (15 minutes from now)
                    try (PreparedStatement insertStmt = conn.prepareStatement(
                            "INSERT INTO otp_codes (username, otp_code, expires_at) VALUES (?, ?, DATE_ADD(NOW(), INTERVAL 15 MINUTE))")) {
                        insertStmt.setString(1, username);
                        insertStmt.setString(2, generatedOTP);
                        insertStmt.executeUpdate();
                    }
                    
                    System.out.println("Generated and stored OTP: " + generatedOTP + " for user: " + username);
                }
                
            } catch (SQLException e) {
                System.err.println("Error storing OTP: " + e.getMessage());
                e.printStackTrace();
            }
        }
        
        System.out.println("Generated OTP: " + generatedOTP);
        return generatedOTP;
    }

    // Method to get the stored OTP
    public static String getGeneratedOTP() {
        // Determine which email to use for OTP retrieval
        String emailToUse = null;
        if (currentPhoneUser != null) {
            emailToUse = currentPhoneUser.email;
        } else if (cephra.Phone.Utilities.AppSessionState.userEmailForReset != null) {
            emailToUse = cephra.Phone.Utilities.AppSessionState.userEmailForReset;
        }
        
        if (emailToUse == null) {
            return null;
        }
        
        try (Connection conn = cephra.Database.DatabaseConnection.getConnection()) {
            // First find username from email
            PreparedStatement findUserStmt = conn.prepareStatement(
                    "SELECT username FROM users WHERE email = ?");
            
            findUserStmt.setString(1, emailToUse);
            ResultSet userRs = findUserStmt.executeQuery();
            
            if (userRs.next()) {
                String username = userRs.getString("username");
                
                // Now get OTP using username
                PreparedStatement stmt = conn.prepareStatement(
                        "SELECT otp_code FROM otp_codes WHERE username = ? AND expires_at > NOW()");
                
                stmt.setString(1, username);
                
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getString("otp_code");
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Error getting OTP: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }
    
    // Battery management methods
    public static int getUserBatteryLevel(String username) {
        try (Connection conn = cephra.Database.DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT battery_level FROM battery_levels WHERE username = ? ORDER BY id DESC LIMIT 1")) {
            
            stmt.setString(1, username);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    // Return the most recent stored battery level (no randomization)
                    int batteryLevel = rs.getInt("battery_level");
                    // System.out.println("CephraDB: Retrieved battery level for " + username + ": " + batteryLevel + "%");
                    return batteryLevel;
                } else {
                    // No battery level found - return -1 to indicate no battery initialized yet
                    System.out.println("CephraDB: No battery level found for " + username + " - returning -1");
                    return -1;
                }
            }
        } catch (SQLException e) {
            System.err.println("Error getting battery level: " + e.getMessage());
            e.printStackTrace();
            // Return -1 to indicate no battery initialized
            return -1;
        }
    }
    
    public static void setUserBatteryLevel(String username, int batteryLevel) {
        try (Connection conn = cephra.Database.DatabaseConnection.getConnection()) {
            // First, delete ALL existing battery level entries for this user
            try (PreparedStatement deleteStmt = conn.prepareStatement(
                    "DELETE FROM battery_levels WHERE username = ?")) {
                
                deleteStmt.setString(1, username);
                int deletedRows = deleteStmt.executeUpdate();
                if (deletedRows > 0) {
                }
            }
            
            // Then insert the new battery level entry
            try (PreparedStatement insertStmt = conn.prepareStatement(
                    "INSERT INTO battery_levels (username, battery_level, initial_battery_level, battery_capacity_kwh) VALUES (?, ?, ?, ?)")) {
                
                insertStmt.setString(1, username);
                insertStmt.setInt(2, batteryLevel);
                insertStmt.setInt(3, batteryLevel); // initial_battery_level same as current
                insertStmt.setDouble(4, 40.0); // default battery capacity
                
                insertStmt.executeUpdate();
            }
            
        } catch (SQLException e) {
            System.err.println("Error setting battery level: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    public static void chargeUserBatteryToFull(String username) {
        setUserBatteryLevel(username, 100);
    }
    
    // Car index management methods
    public static int getUserCarIndex(String username) {
        try (Connection conn = cephra.Database.DatabaseConnection.getConnection()) {
            // First check if car_index column exists in users table
            if (!columnExists(conn, "users", "car_index")) {
                // Add the column if it doesn't exist
                addCarIndexColumn(conn);
            }
            
            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT car_index FROM users WHERE username = ?")) {
                
                stmt.setString(1, username);
                
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        int carIndex = rs.getInt("car_index");
                        if (rs.wasNull()) {
                            // NULL value means no car assigned yet
                            System.out.println("CephraDB: No car index found for " + username + " - returning -1");
                            return -1;
                        }
                        // System.out.println("CephraDB: Retrieved car index for " + username + ": " + carIndex);
                        return carIndex;
                    } else {
                        System.out.println("CephraDB: User " + username + " not found - returning -1");
                        return -1;
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Error getting car index: " + e.getMessage());
            e.printStackTrace();
            return -1;
        }
    }
    
    public static void setUserCarIndex(String username, int carIndex) {
        try (Connection conn = cephra.Database.DatabaseConnection.getConnection()) {
            // First check if car_index column exists in users table
            if (!columnExists(conn, "users", "car_index")) {
                // Add the column if it doesn't exist
                addCarIndexColumn(conn);
            }
            
            try (PreparedStatement stmt = conn.prepareStatement(
                    "UPDATE users SET car_index = ? WHERE username = ?")) {
                
                stmt.setInt(1, carIndex);
                stmt.setString(2, username);
                
                int rowsAffected = stmt.executeUpdate();
                System.out.println("CephraDB: Set car index for " + username + " to " + carIndex + " (rows affected: " + rowsAffected + ")");
            }
        } catch (SQLException e) {
            System.err.println("Error setting car index: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    // Helper method to check if a column exists in a table
    private static boolean columnExists(Connection conn, String tableName, String columnName) {
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?")) {
            stmt.setString(1, tableName);
            stmt.setString(2, columnName);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {
            System.err.println("Error checking if column exists: " + e.getMessage());
        }
        return false;
    }
    
    // Helper method to add car_index column to users table
    private static void addCarIndexColumn(Connection conn) {
        try (PreparedStatement stmt = conn.prepareStatement(
                "ALTER TABLE users ADD COLUMN car_index INT DEFAULT NULL")) {
            stmt.executeUpdate();
            System.out.println("CephraDB: Added car_index column to users table");
        } catch (SQLException e) {
            // Column might already exist, check error message
            if (e.getMessage().contains("Duplicate column name")) {
                System.out.println("CephraDB: car_index column already exists");
            } else {
                System.err.println("Error adding car_index column: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
    
    // Plate number management methods
    public static String getUserPlateNumber(String username) {
        try (Connection conn = cephra.Database.DatabaseConnection.getConnection()) {
            // First check if plate_number column exists in users table
            if (!columnExists(conn, "users", "plate_number")) {
                // Add the column if it doesn't exist
                addPlateNumberColumn(conn);
            }
            
            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT plate_number FROM users WHERE username = ?")) {
                
                stmt.setString(1, username);
                
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        String plateNumber = rs.getString("plate_number");
                        if (rs.wasNull() || plateNumber == null || plateNumber.trim().isEmpty()) {
                            // NULL or empty value means no plate number assigned yet
                            return null;
                        }
                        return plateNumber;
                    } else {
                        return null;
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Error getting plate number: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
    
    public static void setUserPlateNumber(String username, String plateNumber) {
        try (Connection conn = cephra.Database.DatabaseConnection.getConnection()) {
            // First check if plate_number column exists in users table
            if (!columnExists(conn, "users", "plate_number")) {
                // Add the column if it doesn't exist
                addPlateNumberColumn(conn);
            }
            
            try (PreparedStatement stmt = conn.prepareStatement(
                    "UPDATE users SET plate_number = ? WHERE username = ?")) {
                
                stmt.setString(1, plateNumber);
                stmt.setString(2, username);
                
                int rowsAffected = stmt.executeUpdate();
                System.out.println("CephraDB: Set plate number for " + username + " to " + plateNumber + " (rows affected: " + rowsAffected + ")");
            }
        } catch (SQLException e) {
            System.err.println("Error setting plate number: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Ensures the user has a plate number; generates and saves one if missing
    private static String ensureUserHasPlateNumber(String username) {
        try {
            String plate = getUserPlateNumber(username);
            if (plate == null || plate.trim().isEmpty()) {
                String generated = generateUniquePlateNumber();
                setUserPlateNumber(username, generated);
                return generated;
            }
            return plate;
        } catch (Exception ex) {
            System.err.println("CephraDB: Failed to ensure plate number for user " + username + ": " + ex.getMessage());
            return null;
        }
    }
    
    public static String generateUniquePlateNumber() {
        try (Connection conn = cephra.Database.DatabaseConnection.getConnection()) {
            // First check if plate_number column exists in users table
            if (!columnExists(conn, "users", "plate_number")) {
                // Add the column if it doesn't exist
                addPlateNumberColumn(conn);
            }
            
            Random random = new Random();
            String plateNumber;
            boolean isUnique = false;
            int attempts = 0;
            int maxAttempts = 100;
            
            do {
                // Generate plate number with random letters and numbers (e.g., XYZ1234, ABC5678)
                // 3 random letters followed by 4 random digits
                StringBuilder sb = new StringBuilder();
                
                // Generate 3 random letters (A-Z)
                for (int i = 0; i < 3; i++) {
                    char randomLetter = (char) ('A' + random.nextInt(26));
                    sb.append(randomLetter);
                }
                
                // Generate 4 random digits (0-9)
                for (int i = 0; i < 4; i++) {
                    int randomDigit = random.nextInt(10);
                    sb.append(randomDigit);
                }
                
                plateNumber = sb.toString();
                
                // Check if this plate number already exists
                try (PreparedStatement stmt = conn.prepareStatement(
                        "SELECT COUNT(*) FROM users WHERE plate_number = ?")) {
                    stmt.setString(1, plateNumber);
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            isUnique = (rs.getInt(1) == 0); // Unique if count is 0
                        }
                    }
                }
                
                attempts++;
                if (attempts >= maxAttempts) {
                    System.err.println("CephraDB: Warning - Could not generate unique plate number after " + maxAttempts + " attempts");
                    break;
                }
            } while (!isUnique);
            
            return plateNumber;
            
        } catch (SQLException e) {
            System.err.println("Error generating unique plate number: " + e.getMessage());
            e.printStackTrace();
            // Return a fallback plate number with random letters and timestamp
            Random random = new Random();
            String fallbackLetters = "";
            for (int i = 0; i < 3; i++) {
                fallbackLetters += (char) ('A' + random.nextInt(26));
            }
            String fallback = fallbackLetters + String.valueOf(System.currentTimeMillis() % 10000);
            System.out.println("CephraDB: Using fallback plate number: " + fallback);
            return fallback;
        }
    }
    
    // Helper method to add plate_number column to users table
    private static void addPlateNumberColumn(Connection conn) {
        try (PreparedStatement stmt = conn.prepareStatement(
                "ALTER TABLE users ADD COLUMN plate_number VARCHAR(10) DEFAULT NULL UNIQUE")) {
            stmt.executeUpdate();
            System.out.println("CephraDB: Added plate_number column to users table");
            
            // Also add index for better performance
            try (PreparedStatement indexStmt = conn.prepareStatement(
                    "CREATE INDEX idx_users_plate_number ON users(plate_number)")) {
                indexStmt.executeUpdate();
                System.out.println("CephraDB: Added index for plate_number column");
            } catch (SQLException indexEx) {
                // Index might already exist, check error message
                if (indexEx.getMessage().contains("Duplicate key name")) {
                    System.out.println("CephraDB: plate_number index already exists");
                } else {
                    System.err.println("Error adding plate_number index: " + indexEx.getMessage());
                }
            }
        } catch (SQLException e) {
            // Column might already exist, check error message
            if (e.getMessage().contains("Duplicate column name")) {
                System.out.println("CephraDB: plate_number column already exists");
            } else {
                System.err.println("Error adding plate_number column: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
    
    // Method to check for duplicate battery level entries for a user
    public static void checkDuplicateBatteryLevels(String username) {
        try (Connection conn = cephra.Database.DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT COUNT(*) FROM battery_levels WHERE username = ?")) {
            
            stmt.setString(1, username);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    int count = rs.getInt(1);
                    if (count > 1) {
                        System.err.println("CephraDB: WARNING - Found " + count + " battery level entries for user " + username);
                        logBatteryLevelDetails(conn, username);
                    } else {
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Error checking duplicate battery levels: " + e.getMessage());
        }
    }
    
    // Helper method to log battery level details
    private static void logBatteryLevelDetails(Connection conn, String username) {
        try (PreparedStatement detailStmt = conn.prepareStatement(
                "SELECT id, battery_level, initial_battery_level FROM battery_levels WHERE username = ? ORDER BY id")) {
            detailStmt.setString(1, username);
            try (ResultSet detailRs = detailStmt.executeQuery()) {
                while (detailRs.next()) {
                    System.err.println("CephraDB: Entry ID " + detailRs.getInt("id") + 
                                     " - battery_level: " + detailRs.getInt("battery_level") + 
                                     "%, initial_battery_level: " + detailRs.getInt("initial_battery_level") + "%");
                }
            }
        } catch (SQLException e) {
            System.err.println("Error logging battery level details: " + e.getMessage());
        }
    }
    
    // Method to clean up duplicate battery level entries
    public static void cleanupDuplicateBatteryLevels() {
        cleanupBatteryLevels(false);
    }
    
    // Method to clean up all duplicate battery level entries for all users
    public static void cleanupAllDuplicateBatteryLevels() {
        cleanupBatteryLevels(true);
    }
    
    // Consolidated method to clean up battery level duplicates
    private static void cleanupBatteryLevels(boolean allUsers) {
        try (Connection conn = cephra.Database.DatabaseConnection.getConnection()) {
            String cleanupSQL = allUsers ? 
                "DELETE b1 FROM battery_levels b1 " +
                "LEFT JOIN (" +
                "    SELECT username, MAX(id) as max_id " +
                "    FROM battery_levels " +
                "    GROUP BY username" +
                ") b2 ON b1.username = b2.username AND b1.id = b2.max_id " +
                "WHERE b2.max_id IS NULL" :
                "DELETE b1 FROM battery_levels b1 " +
                "INNER JOIN battery_levels b2 " +
                "WHERE b1.id > b2.id AND b1.username = b2.username";
            
            try (PreparedStatement stmt = conn.prepareStatement(cleanupSQL)) {
                int deletedRows = stmt.executeUpdate();
                if (deletedRows > 0) {
                    String message = allUsers ? 
                        "Cleaned up " + deletedRows + " old duplicate battery level entries for all users" :
                        "Cleaned up " + deletedRows + " duplicate battery level entries";
                    System.out.println(message);
                }
            }
        } catch (SQLException e) {
            System.err.println("Error cleaning up duplicate battery levels: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    // Active ticket management methods
    public static boolean hasActiveTicket(String username) {
        return executeActiveTicketQuery(username, "SELECT ticket_id FROM active_tickets WHERE username = ?") != null;
    }
    
    // Helper method for active ticket queries
    private static String executeActiveTicketQuery(String username, String query) {
        try (Connection conn = cephra.Database.DatabaseConnection.getConnection()) {
            if (!tableExists(conn, "active_tickets")) {
                System.err.println("Warning: active_tickets table does not exist.");
                return null;
            }
            
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setString(1, username);
                
                try (ResultSet rs = stmt.executeQuery()) {
                    return rs.next() ? rs.getString("ticket_id") : null;
                }
            }
        } catch (SQLException e) {
            System.err.println("Error executing active ticket query: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
    
    public static void setActiveTicket(String username, String ticketId) {
        try (Connection conn = cephra.Database.DatabaseConnection.getConnection()) {
            // First check if the active_tickets table exists
            if (!tableExists(conn, "active_tickets")) {
                System.err.println("Warning: active_tickets table does not exist.");
                return; // Cannot set active ticket if table doesn't exist
            }
            
            // Ensure user has a plate number
            String userPlateNumber = ensureUserHasPlateNumber(username);
            
            String insertSQL;
            if (userPlateNumber != null) {
                insertSQL = "INSERT INTO active_tickets (username, ticket_id, service_type, initial_battery_level, current_battery_level, status, plate_number) VALUES (?, ?, ?, ?, ?, ?, ?) " +
                           "ON DUPLICATE KEY UPDATE ticket_id = ?, service_type = ?, initial_battery_level = ?, current_battery_level = ?, status = ?, plate_number = ?";
            } else {
                insertSQL = "INSERT INTO active_tickets (username, ticket_id, service_type, initial_battery_level, current_battery_level, status) VALUES (?, ?, ?, ?, ?, ?) " +
                           "ON DUPLICATE KEY UPDATE ticket_id = ?, service_type = ?, initial_battery_level = ?, current_battery_level = ?, status = ?";
            }
            
            try (PreparedStatement stmt = conn.prepareStatement(insertSQL)) {
                // Get user's current battery level
                int batteryLevel = getUserBatteryLevel(username);
                
                stmt.setString(1, username);
                stmt.setString(2, ticketId);
                stmt.setString(3, "Normal"); // Default service type
                stmt.setInt(4, batteryLevel);
                stmt.setInt(5, batteryLevel);
                stmt.setString(6, "Active");
                
                if (userPlateNumber != null) {
                    stmt.setString(7, userPlateNumber);
                    stmt.setString(8, ticketId);
                    stmt.setString(9, "Normal");
                    stmt.setInt(10, batteryLevel);
                    stmt.setInt(11, batteryLevel);
                    stmt.setString(12, "Active");
                    stmt.setString(13, userPlateNumber);
                } else {
                    stmt.setString(7, ticketId);
                    stmt.setString(8, "Normal");
                    stmt.setInt(9, batteryLevel);
                    stmt.setInt(10, batteryLevel);
                    stmt.setString(11, "Active");
                }
                
                stmt.executeUpdate();

                // Backfill plate number if it was missing at insert time
                if (userPlateNumber == null) {
                    try (PreparedStatement upd = conn.prepareStatement(
                        "UPDATE active_tickets atc JOIN users u ON atc.username = u.username SET atc.plate_number = u.plate_number WHERE atc.ticket_id = ? AND atc.plate_number IS NULL")) {
                        upd.setString(1, ticketId);
                        upd.executeUpdate();
                    } catch (SQLException bfEx) {
                        System.err.println("CephraDB: Failed to backfill plate_number in active_tickets for ticket " + ticketId + ": " + bfEx.getMessage());
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Error setting active ticket: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    // Overloaded method to set active ticket with full details
    public static void setActiveTicket(String username, String ticketId, String serviceType, int initialBatteryLevel, String bayNumber) {
        try (Connection conn = cephra.Database.DatabaseConnection.getConnection()) {
            // First check if the active_tickets table exists
            if (!tableExists(conn, "active_tickets")) {
                System.err.println("Warning: active_tickets table does not exist.");
                return; // Cannot set active ticket if table doesn't exist
            }
            
            // Ensure user has a plate number
            String userPlateNumber = ensureUserHasPlateNumber(username);
            
            String insertSQL;
            if (userPlateNumber != null) {
                insertSQL = "INSERT INTO active_tickets (username, ticket_id, service_type, initial_battery_level, current_battery_level, bay_number, status, plate_number) VALUES (?, ?, ?, ?, ?, ?, ?, ?) " +
                           "ON DUPLICATE KEY UPDATE service_type = ?, initial_battery_level = ?, current_battery_level = ?, bay_number = ?, status = ?, plate_number = ?";
            } else {
                insertSQL = "INSERT INTO active_tickets (username, ticket_id, service_type, initial_battery_level, current_battery_level, bay_number, status) VALUES (?, ?, ?, ?, ?, ?, ?) " +
                           "ON DUPLICATE KEY UPDATE service_type = ?, initial_battery_level = ?, current_battery_level = ?, bay_number = ?, status = ?";
            }
            
            try (PreparedStatement stmt = conn.prepareStatement(insertSQL)) {
                stmt.setString(1, username);
                stmt.setString(2, ticketId);
                stmt.setString(3, serviceType);
                stmt.setInt(4, initialBatteryLevel);
                stmt.setInt(5, initialBatteryLevel);
                stmt.setString(6, bayNumber);
                stmt.setString(7, "Active");
                
                if (userPlateNumber != null) {
                    stmt.setString(8, userPlateNumber);
                    stmt.setString(9, serviceType);
                    stmt.setInt(10, initialBatteryLevel);
                    stmt.setInt(11, initialBatteryLevel);
                    stmt.setString(12, bayNumber);
                    stmt.setString(13, "Active");
                    stmt.setString(14, userPlateNumber);
                } else {
                    stmt.setString(8, serviceType);
                    stmt.setInt(9, initialBatteryLevel);
                    stmt.setInt(10, initialBatteryLevel);
                    stmt.setString(11, bayNumber);
                    stmt.setString(12, "Active");
                }
                
                stmt.executeUpdate();
                
                // Mark the bay as occupied in the Bay class
              
            }
        } catch (SQLException e) {
            System.err.println("Error setting active ticket with details: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    public static void clearActiveTicket(String username) {
        try (Connection conn = cephra.Database.DatabaseConnection.getConnection()) {
            // First check if the active_tickets table exists
            if (!tableExists(conn, "active_tickets")) {
                System.err.println("Warning: active_tickets table does not exist.");
                return; // Cannot clear active ticket if table doesn't exist
            }
            
            try (PreparedStatement stmt = conn.prepareStatement(
                     "DELETE FROM active_tickets WHERE username = ?")) {
                
                stmt.setString(1, username);
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            System.err.println("Error clearing active ticket: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    // Method to clear active ticket by ticket ID
    public static void clearActiveTicketByTicketId(String ticketId) {
        try (Connection conn = cephra.Database.DatabaseConnection.getConnection()) {
            // First check if the active_tickets table exists
            if (!tableExists(conn, "active_tickets")) {
                System.err.println("Warning: active_tickets table does not exist.");
                return; // Cannot clear active ticket if table doesn't exist
            }
            

            
            // Now delete the active ticket
            try (PreparedStatement stmt = conn.prepareStatement(
                     "DELETE FROM active_tickets WHERE ticket_id = ?")) {
                
                stmt.setString(1, ticketId);
                stmt.executeUpdate();
            }
            
            // Mark the bay as free in the Bay class
          
        } catch (SQLException e) {
            System.err.println("Error clearing active ticket by ticket ID: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    public static String getActiveTicket(String username) {
        return executeActiveTicketQuery(username, "SELECT ticket_id FROM active_tickets WHERE username = ?");
    }
    
    // Method to get queue ticket for a user (pending tickets)
    public static String getQueueTicketForUser(String username) {
        try (Connection conn = cephra.Database.DatabaseConnection.getConnection()) {
            if (!tableExists(conn, "queue_tickets")) {
                System.err.println("Warning: queue_tickets table does not exist.");
                return null;
            }
            
            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT ticket_id FROM queue_tickets WHERE username = ? AND status IN ('Pending', 'Waiting') ORDER BY created_at DESC LIMIT 1")) {
                stmt.setString(1, username);
                
                try (ResultSet rs = stmt.executeQuery()) {
                    return rs.next() ? rs.getString("ticket_id") : null;
                }
            }
        } catch (SQLException e) {
            System.err.println("Error getting queue ticket for user: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
    
    // Method to get current ticket status for a user
    public static String getUserCurrentTicketStatus(String username) {
        try (Connection conn = cephra.Database.DatabaseConnection.getConnection()) {
            if (!tableExists(conn, "queue_tickets")) {
                System.err.println("Warning: queue_tickets table does not exist.");
                return null; // No table means no tickets
            }
            
            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT status FROM queue_tickets WHERE username = ? ORDER BY created_at DESC LIMIT 1")) {
                stmt.setString(1, username);
                
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getString("status");
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Error getting user current ticket status: " + e.getMessage());
            e.printStackTrace();
        }
        return null; // No ticket found
    }
    
    // Method to get current ticket ID for a user
    public static String getUserCurrentTicketId(String username) {
        try (Connection conn = cephra.Database.DatabaseConnection.getConnection()) {
            if (!tableExists(conn, "queue_tickets")) {
                System.err.println("Warning: queue_tickets table does not exist.");
                return null; // No table means no tickets
            }
            
            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT ticket_id FROM queue_tickets WHERE username = ? ORDER BY created_at DESC LIMIT 1")) {
                stmt.setString(1, username);
                
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getString("ticket_id");
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Error getting user current ticket ID: " + e.getMessage());
            e.printStackTrace();
        }
        return null; // No ticket found
    }
    
    // Method to get current ticket payment status for a user
    public static String getUserCurrentTicketPaymentStatus(String username) {
        try (Connection conn = cephra.Database.DatabaseConnection.getConnection()) {
            if (!tableExists(conn, "queue_tickets")) {
                System.err.println("Warning: queue_tickets table does not exist.");
                return null; // No table means no tickets
            }
            
            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT payment_status FROM queue_tickets WHERE username = ? ORDER BY created_at DESC LIMIT 1")) {
                stmt.setString(1, username);
                
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        String paymentStatus = rs.getString("payment_status");
                        return paymentStatus;
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Error getting user current ticket payment status: " + e.getMessage());
            e.printStackTrace();
        }
        return null; // No ticket found
    }
    
    // Queue ticket management methods
    public static boolean addQueueTicket(String ticketId, String username, String serviceType, 
                                       String status, String paymentStatus, int initialBatteryLevel) {
        try (Connection conn = cephra.Database.DatabaseConnection.getConnection()) {
            // First, ensure the user exists in the users table
            if (!userExists(username)) {
                // Create a temporary user if they don't exist
                addUser("Temp", "User", username, username + "@cephra.com", "temp123");
            }
            
            // Check if ticket already exists
            try (PreparedStatement checkStmt = conn.prepareStatement(
                    "SELECT ticket_id FROM queue_tickets WHERE ticket_id = ?")) {
                checkStmt.setString(1, ticketId);
                try (ResultSet rs = checkStmt.executeQuery()) {
                    if (rs.next()) {
                        System.err.println("Ticket " + ticketId + " already exists in database. Skipping insertion.");
                        return false; // Ticket already exists
                    }
                }
            }
            
            // Determine priority based on battery level (priority 1 for <20%, priority 0 for >=20%)
            int priority = (initialBatteryLevel < 20) ? 1 : 0;
            
            // Respect flow: Priority -> Waiting; otherwise keep provided status (usually Pending)
            String initialStatus = (priority == 1) ? "Waiting" : status;
            
            // Ensure user has a plate number and fetch it
            String userPlateNumber = ensureUserHasPlateNumber(username);
            
            // Now insert the queue ticket with priority information and plate number
            String insertSQL;
            if (userPlateNumber != null) {
                insertSQL = "INSERT INTO queue_tickets (ticket_id, username, service_type, status, " +
                           "payment_status, initial_battery_level, priority, plate_number) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
            } else {
                insertSQL = "INSERT INTO queue_tickets (ticket_id, username, service_type, status, " +
                           "payment_status, initial_battery_level, priority) VALUES (?, ?, ?, ?, ?, ?, ?)";
            }
            
            try (PreparedStatement stmt = conn.prepareStatement(insertSQL)) {
                stmt.setString(1, ticketId);
                stmt.setString(2, username);
                stmt.setString(3, serviceType);
                stmt.setString(4, initialStatus);
                stmt.setString(5, paymentStatus);
                stmt.setInt(6, initialBatteryLevel);
                stmt.setInt(7, priority);
                
                // Add plate number if available
                if (userPlateNumber != null) {
                    stmt.setString(8, userPlateNumber);
                }
                
                int rowsAffected = stmt.executeUpdate();

                // Backfill plate number immediately if it was missing at insert
                if (userPlateNumber == null) {
                    try (PreparedStatement upd = conn.prepareStatement(
                        "UPDATE queue_tickets qt JOIN users u ON qt.username = u.username SET qt.plate_number = u.plate_number WHERE qt.ticket_id = ? AND qt.plate_number IS NULL")) {
                        upd.setString(1, ticketId);
                        upd.executeUpdate();
                    } catch (SQLException bfEx) {
                        System.err.println("CephraDB: Failed to backfill plate_number in queue_tickets for ticket " + ticketId + ": " + bfEx.getMessage());
                    }
                }
                
                // If initial status is Waiting (priority or manually set), also add it to waiting grid
                if (rowsAffected > 0 && "Waiting".equalsIgnoreCase(initialStatus)) {
                    try {
                        cephra.Admin.BayManagement.addTicketToWaitingGrid(ticketId, username, serviceType, initialBatteryLevel);
                        System.out.println("CephraDB: Ticket " + ticketId + " added to waiting grid (status=Waiting)");
                    } catch (Exception e) {
                        System.err.println("CephraDB: Failed to add ticket " + ticketId + " to waiting grid: " + e.getMessage());
                    }
                }
                
                return rowsAffected > 0;
            }
            
        } catch (SQLException e) {
            System.err.println("Error adding queue ticket: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    public static boolean updateQueueTicketStatus(String ticketId, String status) {
        // Validate input parameters
        if (ticketId == null || ticketId.trim().isEmpty()) {
            System.err.println("CephraDB: Invalid ticket ID for status update");
            return false;
        }
        if (status == null || status.trim().isEmpty()) {
            System.err.println("CephraDB: Invalid status for ticket " + ticketId);
            return false;
        }
        
        try (Connection conn = cephra.Database.DatabaseConnection.getConnection()) {
            if (conn == null) {
                System.err.println("CephraDB: Could not establish database connection for status update");
                return false;
            }
            
            // First check if the ticket exists
            try (PreparedStatement checkStmt = conn.prepareStatement(
                    "SELECT ticket_id FROM queue_tickets WHERE ticket_id = ?")) {
                checkStmt.setString(1, ticketId);
                try (ResultSet rs = checkStmt.executeQuery()) {
                    if (!rs.next()) {
                        System.err.println("CephraDB: Ticket " + ticketId + " not found in queue_tickets table");
                        return false;
                    }
                }
            }
            
            // Update the status
            try (PreparedStatement stmt = conn.prepareStatement(
                    "UPDATE queue_tickets SET status = ? WHERE ticket_id = ?")) {
                
                stmt.setString(1, status);
                stmt.setString(2, ticketId);
                
                int rowsAffected = stmt.executeUpdate();
                if (rowsAffected > 0) {
                    return true;
                } else {
                    System.err.println("CephraDB: No rows affected when updating status for ticket " + ticketId);
                    return false;
                }
            }
            
        } catch (SQLException e) {
            System.err.println("Error updating queue ticket status: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Updates the payment method of a queue ticket
     * @param ticketId the ticket ID to update
     * @param paymentMethod the payment method ('Cash' or 'Online')
     * @return true if successful, false otherwise
     */
    public static boolean updateQueueTicketPaymentMethod(String ticketId, String paymentMethod) {
        if (ticketId == null || ticketId.trim().isEmpty()) {
            System.err.println("CephraDB: Invalid ticket ID for payment method update");
            return false;
        }
        if (paymentMethod == null || paymentMethod.trim().isEmpty()) {
            System.err.println("CephraDB: Invalid payment method for ticket " + ticketId);
            return false;
        }
        
        try (Connection conn = cephra.Database.DatabaseConnection.getConnection()) {
            if (conn == null) {
                System.err.println("CephraDB: Could not establish database connection for payment method update");
                return false;
            }
            
            // Update the payment method
            try (PreparedStatement stmt = conn.prepareStatement(
                    "UPDATE queue_tickets SET payment_method = ? WHERE ticket_id = ?")) {
                
                stmt.setString(1, paymentMethod);
                stmt.setString(2, ticketId);
                
                int rowsAffected = stmt.executeUpdate();
                if (rowsAffected > 0) {
                    return true;
                } else {
                    System.err.println("CephraDB: No rows affected when updating payment method for ticket " + ticketId);
                    return false;
                }
            }
            
        } catch (SQLException e) {
            System.err.println("Error updating queue ticket payment method: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Gets the customer name for a given ticket ID from charging bays
     * @param ticketId the ticket ID to look up
     * @return the customer username, or null if not found
     */
    public static String getCustomerByTicket(String ticketId) {
        if (ticketId == null || ticketId.trim().isEmpty()) {
            return null;
        }
        
        try (Connection conn = cephra.Database.DatabaseConnection.getConnection()) {
            if (conn == null) {
                System.err.println("CephraDB: Could not establish database connection for customer lookup");
                return null;
            }
            
            // First try to get from charging_bays table
            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT current_username FROM charging_bays WHERE current_ticket_id = ?")) {
                stmt.setString(1, ticketId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getString("current_username");
                    }
                }
            }
            
            // If not found in charging_bays, try queue_tickets table
            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT username FROM queue_tickets WHERE ticket_id = ?")) {
                stmt.setString(1, ticketId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getString("username");
                    }
                }
            }
            
        } catch (SQLException e) {
            System.err.println("Error getting customer by ticket: " + e.getMessage());
            e.printStackTrace();
        }
        
        return null;
    }
    
    /**
     * Gets the payment method of a queue ticket
     * @param ticketId the ticket ID to check
     * @return the payment method ('Cash' or 'Online'), or null if not found
     */
    public static String getQueueTicketPaymentMethod(String ticketId) {
        if (ticketId == null || ticketId.trim().isEmpty()) {
            System.err.println("CephraDB: Invalid ticket ID for payment method query");
            return null;
        }
        
        try (Connection conn = cephra.Database.DatabaseConnection.getConnection()) {
            if (conn == null) {
                System.err.println("CephraDB: Could not establish database connection for payment method query");
                return null;
            }
            
            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT payment_method FROM queue_tickets WHERE ticket_id = ?")) {
                
                stmt.setString(1, ticketId);
                
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        String paymentMethod = rs.getString("payment_method");
                        System.out.println("CephraDB: Retrieved payment method '" + paymentMethod + "' for ticket " + ticketId);
                        return paymentMethod;
                    } else {
                        System.err.println("CephraDB: Ticket " + ticketId + " not found in queue_tickets table");
                        return null;
                    }
                }
            }
            
        } catch (SQLException e) {
            System.err.println("Error retrieving queue ticket payment method: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
    
    public static boolean updateQueueTicketPayment(String ticketId, String paymentStatus, String referenceNumber) {
        try (Connection conn = cephra.Database.DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "UPDATE queue_tickets SET payment_status = ?, reference_number = ? WHERE ticket_id = ?")) {
            
            stmt.setString(1, paymentStatus);
            stmt.setString(2, referenceNumber);
            stmt.setString(3, ticketId);
            
            int rowsAffected = stmt.executeUpdate();
            return rowsAffected > 0;
            
        } catch (SQLException e) {
            System.err.println("Error updating queue ticket payment: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    public static boolean updateQueueTicketPaymentStatus(String ticketId, String paymentStatus) {
        try (Connection conn = cephra.Database.DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "UPDATE queue_tickets SET payment_status = ? WHERE ticket_id = ?")) {
            
            stmt.setString(1, paymentStatus);
            stmt.setString(2, ticketId);
            
            int rowsAffected = stmt.executeUpdate();
            return rowsAffected > 0;
            
        } catch (SQLException e) {
            System.err.println("Error updating queue ticket payment status: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    public static boolean removeQueueTicket(String ticketId) {
        try (Connection conn = cephra.Database.DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "DELETE FROM queue_tickets WHERE ticket_id = ?")) {
            
            stmt.setString(1, ticketId);
            
            int rowsAffected = stmt.executeUpdate();
            return rowsAffected > 0;
            
        } catch (SQLException e) {
            System.err.println("Error removing queue ticket: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /** Check if ticket is already in charging history (already processed) */
    public static boolean isTicketInChargingHistory(String ticketId) {
        try (Connection conn = cephra.Database.DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT COUNT(*) FROM charging_history WHERE ticket_id = ?")) {
            
            stmt.setString(1, ticketId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
            
        } catch (SQLException e) {
            System.err.println("Error checking if ticket is in charging history: " + e.getMessage());
            e.printStackTrace();
        }
        return false;
    }
    
    // Charging history methods
    public static boolean addChargingHistory(String ticketId, String username, String serviceType,
                                           int initialBatteryLevel, int chargingTimeMinutes, 
                                           double totalAmount, String referenceNumber) {
        try (Connection conn = cephra.Database.DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "INSERT INTO charging_history (ticket_id, username, service_type, " +
                     "initial_battery_level, final_battery_level, charging_time_minutes, energy_used, total_amount, reference_number, completed_at) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())")) {
            
            stmt.setString(1, ticketId);
            stmt.setString(2, username);
            stmt.setString(3, serviceType);
            stmt.setInt(4, initialBatteryLevel);
            stmt.setInt(5, 100); // Final battery level is always 100% when completed
            stmt.setInt(6, chargingTimeMinutes);
            
            // Calculate energy used in kWh based on battery levels
            double batteryCapacityKWh = 40.0; // 40kWh capacity
            double usedFraction = (100.0 - initialBatteryLevel) / 100.0;
            double energyUsed = usedFraction * batteryCapacityKWh;
            stmt.setDouble(7, energyUsed);
            
            stmt.setDouble(8, totalAmount);
            stmt.setString(9, referenceNumber);
            // Note: completed_at is set to NOW() in the SQL, no need for setString(10)
            
            int rowsAffected = stmt.executeUpdate();
            return rowsAffected > 0;
            
        } catch (SQLException e) {
            System.err.println("Error adding charging history: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    public static java.util.List<Object[]> getChargingHistoryForUser(String username) {
        java.util.List<Object[]> history = new ArrayList<>();
        try (Connection conn = cephra.Database.DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT ticket_id, username, service_type, initial_battery_level, charging_time_minutes, " +
                     "energy_used, total_amount, reference_number, completed_at FROM charging_history " +
                     "WHERE username = ? ORDER BY completed_at DESC")) {
            
            stmt.setString(1, username);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Object[] row = {
                        rs.getString("ticket_id"),
                        rs.getString("username"),
                        rs.getString("service_type"),
                        rs.getInt("initial_battery_level"),
                        rs.getInt("charging_time_minutes"),
                        rs.getDouble("energy_used"),
                        rs.getDouble("total_amount"),
                        rs.getString("reference_number"),
                        rs.getTimestamp("completed_at")
                    };
                    history.add(row);
                }
            }
        } catch (SQLException e) {
            System.err.println("Error getting charging history: " + e.getMessage());
            e.printStackTrace();
        }
        return history;
    }
    
    public static java.util.List<Object[]> getAllChargingHistory() {
        java.util.List<Object[]> history = new ArrayList<>();
        try (Connection conn = cephra.Database.DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT ch.ticket_id, ch.username, ch.service_type, ch.initial_battery_level, ch.charging_time_minutes, " +
                     "ch.energy_used, ch.total_amount, ch.reference_number, ch.completed_at, u.plate_number " +
                     "FROM charging_history ch " +
                     "LEFT JOIN users u ON ch.username = u.username " +
                     "ORDER BY ch.completed_at DESC")) {
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Object[] row = {
                        rs.getString("ticket_id"),
                        rs.getString("username"),
                        rs.getString("service_type"),
                        rs.getInt("initial_battery_level"),
                        rs.getInt("charging_time_minutes"),
                        rs.getDouble("energy_used"),
                        rs.getDouble("total_amount"),
                        rs.getString("reference_number"),
                        rs.getTimestamp("completed_at"),
                        rs.getString("plate_number") // Now includes plate number
                    };
                    history.add(row);
                }
            }
        } catch (SQLException e) {
            System.err.println("Error getting all charging history: " + e.getMessage());
            e.printStackTrace();
        }
        return history;
    }
    
    public static java.util.List<Object[]> getAllQueueTickets() {
        java.util.List<Object[]> tickets = new ArrayList<>();
        try (Connection conn = cephra.Database.DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT qt.ticket_id, qt.reference_number, qt.username, qt.service_type, qt.status, qt.payment_status, qt.priority, qt.initial_battery_level " +
                     "FROM queue_tickets qt ORDER BY qt.priority DESC, qt.created_at ASC")) {
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Object[] row = {
                        rs.getString("ticket_id"),
                        rs.getString("reference_number"),
                        rs.getString("username"),
                        rs.getString("service_type"),
                        rs.getString("status"),
                        rs.getString("payment_status"),
                        rs.getInt("priority"),
                        rs.getInt("initial_battery_level")
                    };
                    tickets.add(row);
                }
            }
        } catch (SQLException e) {
            System.err.println("Error getting all queue tickets: " + e.getMessage());
            e.printStackTrace();
        }
        return tickets;
    }
    
    // Payment transaction methods
        public static boolean addPaymentTransaction(String ticketId, String username, double amount, 
                                               String paymentMethod, String referenceNumber) {
        try (Connection conn = cephra.Database.DatabaseConnection.getConnection()) {
            // Get user's plate number
            String userPlateNumber = getUserPlateNumber(username);
            
            String insertSQL;
            if (userPlateNumber != null) {
                insertSQL = "INSERT INTO payment_transactions (ticket_id, username, amount, " +
                           "payment_method, reference_number, plate_number) VALUES (?, ?, ?, ?, ?, ?)";
            } else {
                insertSQL = "INSERT INTO payment_transactions (ticket_id, username, amount, " +
                           "payment_method, reference_number) VALUES (?, ?, ?, ?, ?)";
            }
        
            try (PreparedStatement stmt = conn.prepareStatement(insertSQL)) {
                stmt.setString(1, ticketId);
                stmt.setString(2, username);
                stmt.setDouble(3, amount);
                stmt.setString(4, paymentMethod);
                stmt.setString(5, referenceNumber);
            
                if (userPlateNumber != null) {
                    stmt.setString(6, userPlateNumber);
                }
            
            int rowsAffected = stmt.executeUpdate();

            // Backfill plate number if it was missing at insert time
            if (userPlateNumber == null) {
                try (PreparedStatement upd = conn.prepareStatement(
                    "UPDATE payment_transactions pt JOIN users u ON pt.username = u.username SET pt.plate_number = u.plate_number WHERE pt.ticket_id = ? AND pt.plate_number IS NULL")) {
                    upd.setString(1, ticketId);
                    upd.executeUpdate();
                } catch (SQLException bfEx) {
                    System.err.println("CephraDB: Failed to backfill plate_number in payment_transactions for ticket " + ticketId + ": " + bfEx.getMessage());
                }
            }

            return rowsAffected > 0;
            }
        } catch (SQLException e) {
            System.err.println("Error adding payment transaction: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    // Method to get payment method for a specific ticket
    public static String getPaymentMethodForTicket(String ticketId) {
        try (Connection conn = cephra.Database.DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT payment_method FROM payment_transactions WHERE ticket_id = ?")) {
            
            stmt.setString(1, ticketId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("payment_method");
                }
            }
        } catch (SQLException e) {
            System.err.println("Error getting payment method for ticket: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }
    
    // Method to check if payment has already been processed for a ticket
    public static boolean isPaymentAlreadyProcessed(String ticketId) {
        try (Connection conn = cephra.Database.DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT COUNT(*) FROM charging_history WHERE ticket_id = ?")) {
            
            stmt.setString(1, ticketId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    int count = rs.getInt(1);
                    return count > 0; // If count > 0, payment already exists
                }
            }
        } catch (SQLException e) {
            System.err.println("Error checking if payment already processed: " + e.getMessage());
            e.printStackTrace();
        }
        return false; // Default to false if error occurs
    }
    
    // Method to process payment transaction with all related database operations in a single transaction
    public static boolean processPaymentTransaction(String ticketId, String username, String serviceType,
                                                  int initialBatteryLevel, int chargingTimeMinutes, 
                                                  double totalAmount, String paymentMethod, String referenceNumber) {
        // Validate input parameters
        if (ticketId == null || ticketId.trim().isEmpty()) {
            System.err.println("CephraDB: Invalid ticket ID for payment transaction");
            return false;
        }
        if (username == null || username.trim().isEmpty()) {
            System.err.println("CephraDB: Invalid username for payment transaction");
            return false;
        }
        if (serviceType == null || serviceType.trim().isEmpty()) {
            System.err.println("CephraDB: Invalid service type for payment transaction");
            return false;
        }
        if (totalAmount < 0) {
            System.err.println("CephraDB: Invalid amount for payment transaction: " + totalAmount);
            return false;
        }
        
        Connection conn = null;
        try {
            conn = cephra.Database.DatabaseConnection.getConnection();
            if (conn == null) {
                System.err.println("CephraDB: Could not establish database connection for payment transaction");
                return false;
            }
            conn.setAutoCommit(false); // Start transaction
            
            // 1. Get current battery level (what user actually charged to)
            int finalBatteryLevel = 100; // Default to 100 if we can't get actual level
            try (PreparedStatement getBatteryStmt = conn.prepareStatement(
                    "SELECT battery_level FROM battery_levels WHERE username = ?")) {
                getBatteryStmt.setString(1, username);
                try (ResultSet rs = getBatteryStmt.executeQuery()) {
                    if (rs.next()) {
                        finalBatteryLevel = rs.getInt("battery_level");
                        System.out.println("CephraDB: User " + username + " stopped charging at " + finalBatteryLevel + "% - using this for payment");
                    }
                }
            }
            
            // 2. Add to charging history with ACTUAL final battery level
            try (PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO charging_history (ticket_id, username, service_type, " +
                    "initial_battery_level, final_battery_level, charging_time_minutes, energy_used, total_amount, reference_number, served_by) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                
                stmt.setString(1, ticketId);
                stmt.setString(2, username);
                stmt.setString(3, serviceType);
                stmt.setInt(4, initialBatteryLevel);
                stmt.setInt(5, finalBatteryLevel); // Use ACTUAL battery level user stopped at
                stmt.setInt(6, chargingTimeMinutes);
                
                // Calculate energy used in kWh based on battery levels
                double batteryCapacityKWh = 40.0; // 40kWh capacity
                double usedFraction = (100.0 - initialBatteryLevel) / 100.0;
                double energyUsed = usedFraction * batteryCapacityKWh;
                stmt.setDouble(7, energyUsed);
                
                stmt.setDouble(8, totalAmount);
                stmt.setString(9, referenceNumber != null ? referenceNumber : "");
                
                // Get the actual admin username who is currently logged in
                String adminUsername = getCurrentAdminUsername();
                if (adminUsername == null || adminUsername.trim().isEmpty()) {
                    adminUsername = "Admin"; // Fallback if no admin logged in
                }
                stmt.setString(10, adminUsername);
                
                int rowsAffected = stmt.executeUpdate();
                if (rowsAffected <= 0) {
                    System.err.println("CephraDB: Failed to insert charging history for ticket " + ticketId);
                    conn.rollback();
                    return false;
                }
            }
            
            // 2. Add payment transaction record
            // Ensure user has a plate number
            String userPlateNumber = ensureUserHasPlateNumber(username);
            
            String paymentSQL;
            if (userPlateNumber != null) {
                paymentSQL = "INSERT INTO payment_transactions (ticket_id, username, amount, " +
                            "payment_method, reference_number, plate_number) VALUES (?, ?, ?, ?, ?, ?)";
            } else {
                paymentSQL = "INSERT INTO payment_transactions (ticket_id, username, amount, " +
                            "payment_method, reference_number) VALUES (?, ?, ?, ?, ?)";
            }
            
            try (PreparedStatement stmt = conn.prepareStatement(paymentSQL)) {
                stmt.setString(1, ticketId);
                stmt.setString(2, username);
                stmt.setDouble(3, totalAmount);
                stmt.setString(4, paymentMethod != null ? paymentMethod : "Cash");
                stmt.setString(5, referenceNumber != null ? referenceNumber : "");
                
                if (userPlateNumber != null) {
                    stmt.setString(6, userPlateNumber);
                }
                
                int rowsAffected = stmt.executeUpdate();
                if (rowsAffected <= 0) {
                    System.err.println("CephraDB: Failed to insert payment transaction for ticket " + ticketId);
                    conn.rollback();
                    return false;
                }
            }

            // Backfill plate number if it was missing at insert time
            if (userPlateNumber == null) {
                try (PreparedStatement upd = conn.prepareStatement(
                        "UPDATE payment_transactions pt JOIN users u ON pt.username = u.username " +
                        "SET pt.plate_number = u.plate_number WHERE pt.ticket_id = ? AND pt.plate_number IS NULL")) {
                    upd.setString(1, ticketId);
                    upd.executeUpdate();
                } catch (SQLException bfEx) {
                    System.err.println("CephraDB: Failed to backfill plate_number in payment_transactions for ticket " + ticketId + ": " + bfEx.getMessage());
                }
            }
            
            // 3. Update queue ticket payment status
            try (PreparedStatement stmt = conn.prepareStatement(
                    "UPDATE queue_tickets SET payment_status = ?, reference_number = ? WHERE ticket_id = ?")) {
                
                stmt.setString(1, "Paid");
                stmt.setString(2, referenceNumber != null ? referenceNumber : "");
                stmt.setString(3, ticketId);
                
                int rowsAffected = stmt.executeUpdate();
                if (rowsAffected <= 0) {
                    System.err.println("CephraDB: Failed to update queue ticket payment status for ticket " + ticketId);
                    conn.rollback();
                    return false;
                }
            }
            
            // 3.5. Clear active ticket (if exists)
            try (PreparedStatement stmt = conn.prepareStatement(
                    "DELETE FROM active_tickets WHERE ticket_id = ?")) {
                
                stmt.setString(1, ticketId);
                stmt.executeUpdate(); // Don't fail if no active ticket exists
            }
            
            // 3.5.1. Clear charging bays and charging grid for this ticket
            try (PreparedStatement stmt = conn.prepareStatement(
                    "UPDATE charging_bays SET current_ticket_id = NULL, current_username = NULL, status = 'Available', start_time = NULL WHERE current_ticket_id = ?")) {
                
                stmt.setString(1, ticketId);
                int bayRowsUpdated = stmt.executeUpdate();
                if (bayRowsUpdated > 0) {
                }
            }
            
            try (PreparedStatement stmt = conn.prepareStatement(
                    "UPDATE charging_grid SET ticket_id = NULL, username = NULL, service_type = NULL, initial_battery_level = NULL, start_time = NULL WHERE ticket_id = ?")) {
                
                stmt.setString(1, ticketId);
                int gridRowsUpdated = stmt.executeUpdate();
                if (gridRowsUpdated > 0) {
                }
            }
            
            // 3.5.2. Clear waiting grid for this ticket (if it exists there)
            try (PreparedStatement stmt = conn.prepareStatement(
                    "UPDATE waiting_grid SET ticket_id = NULL, username = NULL, service_type = NULL, initial_battery_level = NULL, position_in_queue = NULL WHERE ticket_id = ?")) {
                
                stmt.setString(1, ticketId);
                int waitingGridRowsUpdated = stmt.executeUpdate();
                if (waitingGridRowsUpdated > 0) {
                    System.out.println("CephraDB: Removed ticket " + ticketId + " from waiting_grid table");
                }
            }
            
            // 3.6. Remove ticket from queue_tickets table (move to history)
            try (PreparedStatement stmt = conn.prepareStatement(
                    "DELETE FROM queue_tickets WHERE ticket_id = ?")) {
                
                stmt.setString(1, ticketId);
                int rowsAffected = stmt.executeUpdate();
                if (rowsAffected > 0) {
                    System.out.println("CephraDB: Removed ticket " + ticketId + " from queue_tickets table (moved to history)");
                }
            }
            
            // 3.7. DON'T update battery level - keep it at whatever level user stopped at
            // User paid for what they actually charged, battery stays at current level
            System.out.println("CephraDB: Keeping battery at actual level (" + finalBatteryLevel + "%) for user " + username + " - NOT forcing to 100%");
            
            // 4. Add reward points for all payments (1 PHP = 0.05 points)
            if (totalAmount > 0) {
                try {
                    // Calculate points (1 PHP = 0.05 points)
                    int pointsToAdd = (int) Math.round(totalAmount * 0.05);
                    
                    // Direct SQL to update user_points table
                    try (PreparedStatement pointsStmt = conn.prepareStatement(
                            "INSERT INTO user_points (username, total_points, lifetime_earned, lifetime_spent) " +
                            "VALUES (?, ?, ?, 0) " +
                            "ON DUPLICATE KEY UPDATE " +
                            "total_points = total_points + ?, " +
                            "lifetime_earned = lifetime_earned + ?")) {
                        
                        pointsStmt.setString(1, username);
                        pointsStmt.setInt(2, pointsToAdd);
                        pointsStmt.setInt(3, pointsToAdd);
                        pointsStmt.setInt(4, pointsToAdd);
                        pointsStmt.setInt(5, pointsToAdd);
                        
                        int pointsResult = pointsStmt.executeUpdate();
                        
                        if (pointsResult > 0) {
                            System.out.println("CephraDB: Successfully added " + pointsToAdd + " points for payment of ₱" + totalAmount + " to user " + username);
                            
                            // Record the transaction in reward_transactions table
                            try (PreparedStatement rewardStmt = conn.prepareStatement(
                                    "INSERT INTO reward_transactions (username, transaction_type, points_change, total_points_after, description, reference_id) " +
                                    "VALUES (?, 'payment', ?, (SELECT total_points FROM user_points WHERE username = ?), 'Payment reward for ticket', ?)")) {
                                
                                rewardStmt.setString(1, username);
                                rewardStmt.setInt(2, pointsToAdd);
                                rewardStmt.setString(3, username);
                                rewardStmt.setString(4, ticketId);
                                
                                rewardStmt.executeUpdate();
                                System.out.println("CephraDB: Recorded reward transaction for " + pointsToAdd + " points to user " + username);
                            }
                        }
                    }
                } catch (Exception pointsEx) {
                    System.err.println("CephraDB: Error adding points for payment: " + pointsEx.getMessage());
                    // Don't fail the transaction if points addition fails
                }
            }
            
            // 5. Add to admin history (if HistoryBridge is available)
            addToAdminHistory(ticketId, username, totalAmount, referenceNumber);
            
            conn.commit(); // Commit transaction
            
            // Clear charging bay and grid after successful payment
            cephra.Admin.BayManagement.clearChargingBayForCompletedTicket(ticketId);
            
            // Verify that the ticket was added to charging history
            try (PreparedStatement verifyStmt = conn.prepareStatement(
                    "SELECT COUNT(*) FROM charging_history WHERE ticket_id = ?")) {
                verifyStmt.setString(1, ticketId);
                try (ResultSet rs = verifyStmt.executeQuery()) {
                    if (rs.next()) {
                        rs.getInt(1);
                    }
                }
            } catch (Exception verifyEx) {
                System.err.println("CephraDB: Error verifying ticket in charging_history: " + verifyEx.getMessage());
            }
            
            // Notify phone history that a new entry has been added
            try {
                cephra.Phone.Utilities.HistoryManager.notifyHistoryUpdate(username);
            } catch (Exception e) {
                System.err.println("CephraDB: Error notifying phone history: " + e.getMessage());
            }
            
            // Refresh admin history table to show the new completed ticket
            try {
                cephra.Admin.Utilities.HistoryBridge.refreshHistoryTable();
            } catch (Exception e) {
                System.err.println("CephraDB: Error refreshing admin history table: " + e.getMessage());
            }
            
            // Refresh Porsche screen to show updated 100% battery level
         //   refreshPorscheScreen(username);
            
            return true;
            
        } catch (SQLException e) {
            System.err.println("Error processing payment transaction: " + e.getMessage());
            e.printStackTrace();
            if (conn != null) {
                try {
                    conn.rollback();
                    System.err.println("CephraDB: Rolled back payment transaction for ticket " + ticketId);
                } catch (SQLException rollbackEx) {
                    System.err.println("Error rolling back transaction: " + rollbackEx.getMessage());
                }
            }
            return false;
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (SQLException e) {
                    System.err.println("Error closing connection: " + e.getMessage());
                }
            }
        }
    }
    
    // Method to process payment transaction with all related database operations in a single transaction - SKIP wallet processing
    public static boolean processPaymentTransactionSkipWallet(String ticketId, String username, String serviceType,
                                                  int initialBatteryLevel, int chargingTimeMinutes, 
                                                  double totalAmount, String paymentMethod, String referenceNumber) {
        System.out.println("CephraDB: Starting online payment processing for ticket " + ticketId + " by user " + username);
        
        // Validate input parameters
        if (ticketId == null || ticketId.trim().isEmpty()) {
            System.err.println("CephraDB: Invalid ticket ID for payment transaction");
            return false;
        }
        if (username == null || username.trim().isEmpty()) {
            System.err.println("CephraDB: Invalid username for payment transaction");
            return false;
        }
        if (serviceType == null || serviceType.trim().isEmpty()) {
            System.err.println("CephraDB: Invalid service type for payment transaction");
            return false;
        }
        if (totalAmount < 0) {
            System.err.println("CephraDB: Invalid amount for payment transaction: " + totalAmount);
            return false;
        }
        
        Connection conn = null;
        try {
            conn = cephra.Database.DatabaseConnection.getConnection();
            if (conn == null) {
                System.err.println("CephraDB: Could not establish database connection for payment transaction");
                return false;
            }
            conn.setAutoCommit(false); // Start transaction
            
            // 1. Get current battery level (what user actually charged to)
            int finalBatteryLevel = 100; // Default to 100 if we can't get actual level
            try (PreparedStatement getBatteryStmt = conn.prepareStatement(
                    "SELECT battery_level FROM battery_levels WHERE username = ?")) {
                getBatteryStmt.setString(1, username);
                try (ResultSet rs = getBatteryStmt.executeQuery()) {
                    if (rs.next()) {
                        finalBatteryLevel = rs.getInt("battery_level");
                        System.out.println("CephraDB: User " + username + " stopped charging at " + finalBatteryLevel + "% (skip wallet) - using this for payment");
                    }
                }
            }
            
            // 2. Add to charging history with ACTUAL final battery level
            try (PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO charging_history (ticket_id, username, service_type, " +
                    "initial_battery_level, final_battery_level, charging_time_minutes, energy_used, total_amount, reference_number, served_by) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                
                stmt.setString(1, ticketId);
                stmt.setString(2, username);
                stmt.setString(3, serviceType);
                stmt.setInt(4, initialBatteryLevel);
                stmt.setInt(5, finalBatteryLevel); // Use ACTUAL battery level user stopped at
                stmt.setInt(6, chargingTimeMinutes);
                
                // Calculate energy used in kWh based on battery levels
                double batteryCapacityKWh = 40.0; // 40kWh capacity
                double usedFraction = (100.0 - initialBatteryLevel) / 100.0;
                double energyUsed = usedFraction * batteryCapacityKWh;
                stmt.setDouble(7, energyUsed);
                
                stmt.setDouble(8, totalAmount);
                stmt.setString(9, referenceNumber != null ? referenceNumber : "");
                
                // Get the actual admin username who is currently logged in
                String adminUsername = getCurrentAdminUsername();
                if (adminUsername == null || adminUsername.trim().isEmpty()) {
                    adminUsername = "Admin"; // Fallback if no admin logged in
                }
                stmt.setString(10, adminUsername);
                
                int rowsAffected = stmt.executeUpdate();
                if (rowsAffected <= 0) {
                    System.err.println("CephraDB: Failed to insert charging history for ticket " + ticketId);
                    conn.rollback();
                    return false;
                }
            }
            
            // 2. Add payment transaction record (with plate number)
            String userPlateNumber = ensureUserHasPlateNumber(username);
            
            String paymentSQL;
            if (userPlateNumber != null) {
                paymentSQL = "INSERT INTO payment_transactions (ticket_id, username, amount, " +
                            "payment_method, reference_number, plate_number) VALUES (?, ?, ?, ?, ?, ?)";
            } else {
                paymentSQL = "INSERT INTO payment_transactions (ticket_id, username, amount, " +
                            "payment_method, reference_number) VALUES (?, ?, ?, ?, ?)";
            }
            
            try (PreparedStatement stmt = conn.prepareStatement(paymentSQL)) {
                stmt.setString(1, ticketId);
                stmt.setString(2, username);
                stmt.setDouble(3, totalAmount);
                stmt.setString(4, paymentMethod != null ? paymentMethod : "Cash");
                stmt.setString(5, referenceNumber != null ? referenceNumber : "");
                
                if (userPlateNumber != null) {
                    stmt.setString(6, userPlateNumber);
                }
                
                int rowsAffected = stmt.executeUpdate();
                if (rowsAffected <= 0) {
                    System.err.println("CephraDB: Failed to insert payment transaction for ticket " + ticketId);
                    conn.rollback();
                    return false;
                }
            }

            // Backfill plate number if it was missing at insert time
            if (userPlateNumber == null) {
                try (PreparedStatement upd = conn.prepareStatement(
                        "UPDATE payment_transactions pt JOIN users u ON pt.username = u.username " +
                        "SET pt.plate_number = u.plate_number WHERE pt.ticket_id = ? AND pt.plate_number IS NULL")) {
                    upd.setString(1, ticketId);
                    upd.executeUpdate();
                } catch (SQLException bfEx) {
                    System.err.println("CephraDB: Failed to backfill plate_number in payment_transactions for ticket " + ticketId + ": " + bfEx.getMessage());
                }
            }
            
            // 3. Remove from queue_tickets table
            try (PreparedStatement stmt = conn.prepareStatement(
                    "DELETE FROM queue_tickets WHERE ticket_id = ?")) {
                
                stmt.setString(1, ticketId);
                int rowsAffected = stmt.executeUpdate();
                if (rowsAffected <= 0) {
                    System.err.println("CephraDB: Warning - No queue ticket found to remove for ticket " + ticketId);
                    // Don't fail the transaction if ticket wasn't in queue_tickets
                } else {
                }
            }
            
            // 3.1. Clear active ticket (if exists)
            try (PreparedStatement stmt = conn.prepareStatement(
                    "DELETE FROM active_tickets WHERE ticket_id = ?")) {
                
                stmt.setString(1, ticketId);
                stmt.executeUpdate(); // Don't fail if no active ticket exists
            }
            
            // 3.2. Clear charging bays and charging grid for this ticket
            try (PreparedStatement stmt = conn.prepareStatement(
                    "UPDATE charging_bays SET current_ticket_id = NULL, current_username = NULL, status = 'Available', start_time = NULL WHERE current_ticket_id = ?")) {
                
                stmt.setString(1, ticketId);
                int bayRowsUpdated = stmt.executeUpdate();
                if (bayRowsUpdated > 0) {
                }
            }
            
            try (PreparedStatement stmt = conn.prepareStatement(
                    "UPDATE charging_grid SET ticket_id = NULL, username = NULL, service_type = NULL, initial_battery_level = NULL, start_time = NULL WHERE ticket_id = ?")) {
                
                stmt.setString(1, ticketId);
                int gridRowsUpdated = stmt.executeUpdate();
                if (gridRowsUpdated > 0) {
                }
            }
            
            // 3.2.1. Clear waiting grid for this ticket (if it exists there)
            try (PreparedStatement stmt = conn.prepareStatement(
                    "UPDATE waiting_grid SET ticket_id = NULL, username = NULL, service_type = NULL, initial_battery_level = NULL, position_in_queue = NULL WHERE ticket_id = ?")) {
                
                stmt.setString(1, ticketId);
                int waitingGridRowsUpdated = stmt.executeUpdate();
                if (waitingGridRowsUpdated > 0) {
                    System.out.println("CephraDB: Removed ticket " + ticketId + " from waiting_grid table (skip wallet)");
                }
            }
            
            // 3.3. DON'T update battery level - keep it at whatever level user stopped at
            // User paid for what they actually charged, battery stays at current level
            System.out.println("CephraDB: Keeping battery at actual level (" + finalBatteryLevel + "%) for user " + username + " (skip wallet) - NOT forcing to 100%");
            
            // 4. Add reward points for all payments (1 PHP = 0.05 points)
            if (totalAmount > 0) {
                try {
                    // Calculate points (1 PHP = 0.05 points)
                    int pointsToAdd = (int) Math.round(totalAmount * 0.05);
                    
                    // Direct SQL to update user_points table
                    try (PreparedStatement pointsStmt = conn.prepareStatement(
                            "INSERT INTO user_points (username, total_points, lifetime_earned, lifetime_spent) " +
                            "VALUES (?, ?, ?, 0) " +
                            "ON DUPLICATE KEY UPDATE " +
                            "total_points = total_points + ?, " +
                            "lifetime_earned = lifetime_earned + ?")) {
                        
                        pointsStmt.setString(1, username);
                        pointsStmt.setInt(2, pointsToAdd);
                        pointsStmt.setInt(3, pointsToAdd);
                        pointsStmt.setInt(4, pointsToAdd);
                        pointsStmt.setInt(5, pointsToAdd);
                        
                        pointsStmt.executeUpdate();
                    }
                } catch (Exception pointsEx) {
                    System.err.println("CephraDB: Error adding points for payment: " + pointsEx.getMessage());
                    // Don't fail the transaction if points addition fails
                }
            }
            
            // 5. Add to admin history (if HistoryBridge is available)
            addToAdminHistory(ticketId, username, totalAmount, referenceNumber);
            
            conn.commit(); // Commit transaction
            
            // Clear charging bay and grid after successful payment
            cephra.Admin.BayManagement.clearChargingBayForCompletedTicket(ticketId);
            
            // Verify that the ticket was added to charging history
            try (PreparedStatement verifyStmt = conn.prepareStatement(
                    "SELECT COUNT(*) FROM charging_history WHERE ticket_id = ?")) {
                verifyStmt.setString(1, ticketId);
                try (ResultSet rs = verifyStmt.executeQuery()) {
                    if (rs.next()) {
                        rs.getInt(1);
                    }
                }
            } catch (Exception verifyEx) {
                System.err.println("CephraDB: Error verifying ticket in charging_history: " + verifyEx.getMessage());
            }
            
            // Notify phone history that a new entry has been added
            try {
                cephra.Phone.Utilities.HistoryManager.notifyHistoryUpdate(username);
            } catch (Exception e) {
                System.err.println("CephraDB: Error notifying phone history: " + e.getMessage());
            }
            
            // Refresh admin history table to show the new completed ticket
            try {
                cephra.Admin.Utilities.HistoryBridge.refreshHistoryTable();
            } catch (Exception e) {
                System.err.println("CephraDB: Error refreshing admin history table: " + e.getMessage());
            }
            
            System.out.println("CephraDB: Online payment processing completed successfully for ticket " + ticketId);
            return true;
            
        } catch (SQLException e) {
            System.err.println("Error processing payment transaction (skip wallet): " + e.getMessage());
            e.printStackTrace();
            if (conn != null) {
                try {
                    conn.rollback();
                    System.err.println("CephraDB: Rolled back payment transaction for ticket " + ticketId);
                } catch (SQLException rollbackEx) {
                    System.err.println("Error rolling back transaction: " + rollbackEx.getMessage());
                }
            }
            return false;
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (SQLException e) {
                    System.err.println("Error closing connection: " + e.getMessage());
                }
            }
        }
    }
    
    // Staff management methods
    public static boolean addStaff(String firstname, String lastname, String username, String email, String password) {
        try (Connection conn = cephra.Database.DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "INSERT INTO staff_records (name, firstname, lastname, username, email, password) VALUES (?, ?, ?, ?, ?, ?)")) {
            
            // Combine firstname and lastname for the name column
            String fullName = firstname.trim() + " " + lastname.trim();
            
            stmt.setString(1, fullName);
            stmt.setString(2, firstname);
            stmt.setString(3, lastname);
            stmt.setString(4, username);
            stmt.setString(5, email);
            stmt.setString(6, password);
            
            int rowsAffected = stmt.executeUpdate();
            return rowsAffected > 0;
            
        } catch (SQLException e) {
            System.err.println("Error adding staff: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    public static java.util.List<Object[]> getAllStaff() {
        java.util.List<Object[]> staff = new ArrayList<>();
        try (Connection conn = cephra.Database.DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT name, firstname, lastname, username, email, status, password, DATE_FORMAT(created_at, '%m/%d/%Y') as created_at FROM staff_records ORDER BY firstname")) {
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Object[] row = {
                        rs.getString("name"),        // Combined name
                        rs.getString("firstname"),    // First name only
                        rs.getString("lastname"),     // Last name only
                        rs.getString("username"),
                        rs.getString("email"),
                        rs.getString("status"),
                        rs.getString("password"),
                        rs.getString("created_at")   // Date created
                    };
                    staff.add(row);
                }
            }
        } catch (SQLException e) {
            System.err.println("Error getting staff: " + e.getMessage());
            e.printStackTrace();
        }
        return staff;
    }
    
    /**
     * Gets the staff first name by username
     * @param username the staff username
     * @return the first name or "Admin" if not found
     */
    public static String getStaffFirstName(String username) {
        
        try (Connection conn = cephra.Database.DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT firstname FROM staff_records WHERE username = ?")) {
            
            stmt.setString(1, username);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String firstname = rs.getString("firstname");
                    if (firstname != null && !firstname.trim().isEmpty()) {
                        String result = firstname.trim();
                        return result;
                    }
                } else {
                }
            }
        } catch (SQLException e) {
            System.err.println("Error getting staff first name: " + e.getMessage());
            e.printStackTrace();
        }
        System.out.println("DEBUG: Returning fallback 'Admin'");
        return "Admin"; // Fallback
    }
    
    public static boolean removeStaff(String username) {
        try (Connection conn = cephra.Database.DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "DELETE FROM staff_records WHERE username = ?")) {
            
            stmt.setString(1, username);
            
            int rowsAffected = stmt.executeUpdate();
            return rowsAffected > 0;
            
        } catch (SQLException e) {
            System.err.println("Error removing staff: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    public static boolean resetStaffPassword(String username, String newPassword) {
        try (Connection conn = cephra.Database.DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "UPDATE staff_records SET password = ? WHERE username = ?")) {
            
            stmt.setString(1, newPassword);
            stmt.setString(2, username);
            
            int rowsAffected = stmt.executeUpdate();
            return rowsAffected > 0;
            
        } catch (SQLException e) {
            System.err.println("Error resetting staff password: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Checks if a user is an admin (username = 'admin') or regular staff
     * @param username the username to check
     * @return true if user is admin, false if regular staff
     */
    public static boolean isAdminUser(String username) {
        return "admin".equalsIgnoreCase(username);
    }
    
    // Method to validate staff login credentials
    public static boolean validateStaffLogin(String username, String password) {
        try (Connection conn = cephra.Database.DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT username, email, password FROM staff_records WHERE username = ? AND password = ? AND status = 'Active'")) {
            
            stmt.setString(1, username);
            stmt.setString(2, password);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    // Set the current admin user when login is successful
                    currentAdminUser = new User(
                            rs.getString("username"),
                            rs.getString("email")
                    );
                    return true;
                }
            }
        } catch (SQLException e) {
            System.err.println("Error validating staff login: " + e.getMessage());
            e.printStackTrace();
        }
        return false;
    }
    
    // System settings methods
    public static String getSystemSetting(String settingKey) {
        try (Connection conn = cephra.Database.DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT setting_value FROM system_settings WHERE setting_key = ?")) {
            
            stmt.setString(1, settingKey);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("setting_value");
                }
            }
        } catch (SQLException e) {
            System.err.println("Error getting system setting: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }
    
    public static boolean updateSystemSetting(String settingKey, String settingValue) {
        try (Connection conn = cephra.Database.DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "UPDATE system_settings SET setting_value = ? WHERE setting_key = ?")) {
            
            stmt.setString(1, settingValue);
            stmt.setString(2, settingKey);
            
            int rowsAffected = stmt.executeUpdate();
            return rowsAffected > 0;
            
        } catch (SQLException e) {
            System.err.println("Error updating system setting: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    // Method to verify database connection and basic functionality
    private static void verifyDatabaseConnection(Connection conn) {
        try {
            // Test basic query
            try (PreparedStatement stmt = conn.prepareStatement("SELECT 1")) {
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        System.out.println("Database connection verified successfully.");
                    }
                }
            }
            
            // Migrate existing staff records to populate firstname/lastname columns
            migrateStaffRecords(conn);
            
            // Check if admin staff exists in staff_records, create if not (but don't override existing password)
            try (PreparedStatement checkStmt = conn.prepareStatement(
                    "SELECT username FROM staff_records WHERE username = 'admin'")) {
                try (ResultSet rs = checkStmt.executeQuery()) {
                    if (!rs.next()) {
                        // Only create admin if it doesn't exist at all
                        addStaff("Admin", "User", "admin", "admin@cephra.com", "admin123");
                    }
                }
            }
            
        } catch (SQLException e) {
            System.err.println("Error verifying database connection: " + e.getMessage());
        }
    }
    
    // Method to migrate existing staff records to populate firstname/lastname columns
    private static void migrateStaffRecords(Connection conn) {
        try {
            // Check if firstname/lastname columns exist, add them if not
            if (!columnExists(conn, "staff_records", "firstname")) {
                try (PreparedStatement stmt = conn.prepareStatement(
                        "ALTER TABLE staff_records ADD COLUMN firstname VARCHAR(100) AFTER name")) {
                    stmt.executeUpdate();
                    System.out.println("CephraDB: Added firstname column to staff_records table");
                }
            }
            
            if (!columnExists(conn, "staff_records", "lastname")) {
                try (PreparedStatement stmt = conn.prepareStatement(
                        "ALTER TABLE staff_records ADD COLUMN lastname VARCHAR(100) AFTER firstname")) {
                    stmt.executeUpdate();
                    System.out.println("CephraDB: Added lastname column to staff_records table");
                }
            }
            
            // Update existing records where firstname/lastname are NULL or empty
            try (PreparedStatement stmt = conn.prepareStatement(
                    "UPDATE staff_records SET firstname = ?, lastname = ? WHERE (firstname IS NULL OR firstname = '') AND name IS NOT NULL AND name != ''")) {
                
                // Get all staff records that need migration
                try (PreparedStatement selectStmt = conn.prepareStatement(
                        "SELECT username, name FROM staff_records WHERE (firstname IS NULL OR firstname = '') AND name IS NOT NULL AND name != ''")) {
                    
                    try (ResultSet rs = selectStmt.executeQuery()) {
                        while (rs.next()) {
                            String username = rs.getString("username");
                            String fullName = rs.getString("name");
                            
                            if (fullName != null && !fullName.trim().isEmpty()) {
                                // Split the name into firstname and lastname
                                String[] nameParts = fullName.trim().split("\\s+", 2);
                                String firstname = nameParts[0];
                                String lastname = nameParts.length > 1 ? nameParts[1] : "";
                                
                                // Update the record
                                stmt.setString(1, firstname);
                                stmt.setString(2, lastname);
                                stmt.executeUpdate();
                                
                                System.out.println("CephraDB: Migrated staff record for " + username + ": '" + fullName + "' -> firstname: '" + firstname + "', lastname: '" + lastname + "'");
                            }
                        }
                    }
                }
            }
            
        } catch (SQLException e) {
            System.err.println("Error migrating staff records: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    // Method to clean up orphaned queue tickets (tickets in queue but already in history)
    private static void cleanupOrphanedQueueTickets() {
        try (Connection conn = cephra.Database.DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "DELETE qt FROM queue_tickets qt " +
                     "INNER JOIN charging_history ch ON qt.ticket_id = ch.ticket_id " +
                     "WHERE qt.payment_status = 'Paid'")) {
            
            int rowsAffected = stmt.executeUpdate();
            if (rowsAffected > 0) {
                System.out.println("Cleaned up " + rowsAffected + " orphaned queue tickets that were already in history.");
            }
        } catch (SQLException e) {
            System.err.println("Error cleaning up orphaned queue tickets: " + e.getMessage());
        }
    }
    
    // Method to clean up admin users from users table (they should be in staff_records)
    private static void cleanupAdminFromUsersTable() {
        try (Connection conn = cephra.Database.DatabaseConnection.getConnection()) {
            // Remove admin and testuser from users table
            try (PreparedStatement stmt = conn.prepareStatement(
                    "DELETE FROM users WHERE username IN ('admin', 'testuser')")) {
                
                stmt.executeUpdate();
            }
            
            // Note: Removed automatic deletion of admin users with old passwords
            // This was causing admin password changes to be reverted on Java app startup
            
            // Ensure admin exists in staff_records (but don't override existing password)
            try (PreparedStatement checkStmt = conn.prepareStatement(
                    "SELECT username FROM staff_records WHERE username = 'admin'")) {
                try (ResultSet rs = checkStmt.executeQuery()) {
                    if (!rs.next()) {
                        // Admin doesn't exist, create it with default password
                        try (PreparedStatement insertStmt = conn.prepareStatement(
                                "INSERT INTO staff_records (name, username, email, status, password) VALUES (?, ?, ?, ?, ?)")) {
                            
                            insertStmt.setString(1, "Admin User");
                            insertStmt.setString(2, "admin");
                            insertStmt.setString(3, "admin@cephra.com");
                            insertStmt.setString(4, "Active");
                            insertStmt.setString(5, "admin123");
                            
                            insertStmt.executeUpdate();
                        } catch (SQLException insertException) {
                            // Check if it's a duplicate key error
                            if (!insertException.getMessage().contains("Duplicate entry")) {
                                System.err.println("Error creating admin user: " + insertException.getMessage());
                            }
                        }
                    } else {
                        // Admin exists - do NOT update password to preserve user changes
                        System.out.println("Admin user already exists in staff_records - preserving existing password");
                    }
                }
            }
            
        } catch (SQLException e) {
            System.err.println("Error cleaning up admin from users table: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Helper method to add record to admin history
    private static void addToAdminHistory(String ticketId, String username, double totalAmount, String referenceNumber) {
        try {
            // Show plate number in Admin History (replacing Served By)
            String plateNumber = getUserPlateNumber(username);
            if (plateNumber == null) {
                plateNumber = "";
            }

            Object[] historyRow = new Object[] {
                ticketId,
                username,
                String.format("%.2f", (totalAmount / 100.0) * 40.0), // kWh calculation
                String.format("%.2f", totalAmount),
                plateNumber,
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd hh:mm a")),
                referenceNumber != null ? referenceNumber : ""
            };
            cephra.Admin.Utilities.HistoryBridge.addRecord(historyRow);
        } catch (Throwable t) {
            // Ignore if HistoryBridge is not available
        }
    }
    
    // Method to validate and ensure database integrity
    public static void validateDatabaseIntegrity() {
        try (Connection conn = cephra.Database.DatabaseConnection.getConnection()) {
            
            // Check if all required tables exist
            String[] requiredTables = {
                "users", "battery_levels", "active_tickets", "otp_codes",
                "queue_tickets", "charging_history", "staff_records", 
                "charging_bays", "payment_transactions", "system_settings",
                "wallet_balance", "wallet_transactions", "user_points", 
                "reward_transactions", "waiting_grid", "charging_grid"
            };
            
            boolean allTablesExist = true;
            for (String tableName : requiredTables) {
                if (!tableExists(conn, tableName)) {
                    System.err.println("❌ Missing table: " + tableName);
                    allTablesExist = false;
                } else {
                    System.out.println("✅ Table exists: " + tableName);
                }
            }
            
            if (!allTablesExist) {
                return;
            }
            
            // Check if charging bays are properly configured
            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT COUNT(*) FROM charging_bays WHERE bay_type IN ('Fast', 'Normal')")) {
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        int bayCount = rs.getInt(1);
                        System.out.println("✅ Found " + bayCount + " charging bays configured");
                        if (bayCount < 8) {
                            System.err.println("⚠️  Warning: Expected at least 8 charging bays, found " + bayCount);
                        }
                    }
                }
            }
            
            // Check if system settings are configured
            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT COUNT(*) FROM system_settings")) {
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        int settingCount = rs.getInt(1);
                        System.out.println("✅ Found " + settingCount + " system settings configured");
                    }
                }
            }
            
            // Check if there are any records in key tables
            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT COUNT(*) FROM users")) {
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        int userCount = rs.getInt(1);
                        System.out.println("✅ Found " + userCount + " users in database");
                    }
                }
            }
            
            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT COUNT(*) FROM charging_bays")) {
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        int bayCount = rs.getInt(1);
                        System.out.println("✅ Found " + bayCount + " charging bays in database");
                    }
                }
            }
            
            System.out.println("✅ Database integrity validation completed successfully.");
            
        } catch (SQLException e) {
            System.err.println("❌ Error validating database integrity: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    // ========================= WALLET METHODS =========================
    
    /**
     * Gets the current wallet balance for a user
     * @param username the username to get balance for
     * @return the current balance, or 0.00 if user not found
     */
    public static double getUserWalletBalance(String username) {
        if (username == null || username.trim().isEmpty()) {
            return 0.00;
        }
        
        try (Connection conn = cephra.Database.DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT balance FROM wallet_balance WHERE username = ?")) {
            
            stmt.setString(1, username);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getDouble("balance");
                } else {
                    // Create wallet balance entry if it doesn't exist
                    createWalletBalanceEntry(username, 0.00);
                    return 0.00;
                }
            }
            
        } catch (SQLException e) {
            System.err.println("Error getting user wallet balance: " + e.getMessage());
            e.printStackTrace();
            return 0.00;
        }
    }
    
    /**
     * Creates a wallet balance entry for a new user
     * @param username the username
     * @param initialBalance the initial balance (default 0.00)
     * @return true if successful
     */
    private static boolean createWalletBalanceEntry(String username, double initialBalance) {
        try (Connection conn = cephra.Database.DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "INSERT IGNORE INTO wallet_balance (username, balance) VALUES (?, ?)")) {
            
            stmt.setString(1, username);
            stmt.setDouble(2, initialBalance);
            
            int rowsAffected = stmt.executeUpdate();
            return rowsAffected > 0;
            
        } catch (SQLException e) {
            System.err.println("Error creating wallet balance entry: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Updates user wallet balance and adds transaction record
     * @param username the username
     * @param amount the amount to add (positive) or deduct (negative)
     * @param transactionType the type of transaction (TOP_UP, PAYMENT, REFUND)
     * @param description the transaction description
     * @param referenceId the reference ID (ticket ID for payments, transaction ID for topups)
     * @return true if successful
     */
    public static boolean updateWalletBalance(String username, double amount, String transactionType, 
                                            String description, String referenceId) {
        if (username == null || username.trim().isEmpty()) {
            System.err.println("Invalid username for wallet balance update");
            return false;
        }
        
        Connection conn = null;
        try {
            conn = cephra.Database.DatabaseConnection.getConnection();
            conn.setAutoCommit(false); // Start transaction
            
            // Get current balance
            double currentBalance = getUserWalletBalance(username);
            double newBalance = currentBalance + amount;
            
            if (newBalance < 0 && transactionType.equals("PAYMENT")) {
                System.err.println("Insufficient wallet balance for payment. Current: " + currentBalance + ", Required: " + Math.abs(amount));
                return false;
            }
            
            // Update wallet balance
            try (PreparedStatement updateStmt = conn.prepareStatement(
                    "UPDATE wallet_balance SET balance = ?, updated_at = CURRENT_TIMESTAMP WHERE username = ?")) {
                
                updateStmt.setDouble(1, newBalance);
                updateStmt.setString(2, username);
                
                int rowsUpdated = updateStmt.executeUpdate();
                if (rowsUpdated == 0) {
                    // Create wallet balance entry if it doesn't exist
                    try (PreparedStatement insertStmt = conn.prepareStatement(
                            "INSERT INTO wallet_balance (username, balance) VALUES (?, ?)")) {
                        insertStmt.setString(1, username);
                        insertStmt.setDouble(2, newBalance);
                        insertStmt.executeUpdate();
                    }
                }
            }
            
            // Add wallet transaction record
            try (PreparedStatement transStmt = conn.prepareStatement(
                    "INSERT INTO wallet_transactions (username, transaction_type, amount, previous_balance, new_balance, description, reference_id) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                
                transStmt.setString(1, username);
                transStmt.setString(2, transactionType);
                transStmt.setDouble(3, amount);
                transStmt.setDouble(4, currentBalance);
                transStmt.setDouble(5, newBalance);
                transStmt.setString(6, description);
                transStmt.setString(7, referenceId);
                
                transStmt.executeUpdate();
            }
            
            // Notify wallet history manager about the transaction
            cephra.Phone.Utilities.WalletHistoryManager.updateWalletHistory(username);
            
            // Note: No automatic transaction limit - preserve all wallet history
            // The Wallet panel preview uses getLatestWalletTransactions (LIMIT 5), but WalletHistory uses getWalletTransactionHistory (LIMIT 20)
            
            conn.commit(); // Commit transaction
            return true;
            
        } catch (SQLException e) {
            try {
                if (conn != null) {
                    conn.rollback(); // Rollback on error
                }
            } catch (SQLException rollbackEx) {
                System.err.println("Error rolling back wallet transaction: " + rollbackEx.getMessage());
            }
            System.err.println("Error updating wallet balance: " + e.getMessage());
            e.printStackTrace();
            return false;
        } finally {
            try {
                if (conn != null) {
                    conn.setAutoCommit(true); // Reset auto-commit
                    conn.close();
                }
            } catch (SQLException closeEx) {
                System.err.println("Error closing connection: " + closeEx.getMessage());
            }
        }
    }
    
    // History retention: We no longer auto-delete wallet transactions here.
    // If retention is needed in the future, implement an archival strategy instead.
    
    /**
     * Gets ALL wallet transactions for a user (for WalletHistory panel)
     * @param username the username
     * @return list of all wallet transaction records
     */
    public static java.util.List<Object[]> getAllWalletTransactionHistory(String username) {
        java.util.List<Object[]> transactions = new ArrayList<>();
        
        if (username == null || username.trim().isEmpty()) {
            return transactions;
        }
        
        try (Connection conn = cephra.Database.DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT transaction_type, amount, new_balance, description, reference_id, transaction_date " +
                     "FROM wallet_transactions WHERE username = ? ORDER BY transaction_date DESC")) {
            
            stmt.setString(1, username);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Object[] row = {
                        rs.getString("transaction_type"),
                        rs.getDouble("amount"),
                        rs.getDouble("new_balance"),
                        rs.getString("description"),
                        rs.getString("reference_id"),
                        rs.getTimestamp("transaction_date")
                    };
                    transactions.add(row);
                }
            }
        } catch (SQLException e) {
            System.err.println("Error getting all wallet transaction history: " + e.getMessage());
            e.printStackTrace();
        }
        
        return transactions;
    }
    
    /**
     * Gets the latest wallet transactions for a user (up to 20 transactions for WalletHistory panel)
     * @param username the username
     * @return list of wallet transaction records
     */
    public static java.util.List<Object[]> getWalletTransactionHistory(String username) {
        java.util.List<Object[]> transactions = new ArrayList<>();
        
        if (username == null || username.trim().isEmpty()) {
            return transactions;
        }
        
        try (Connection conn = cephra.Database.DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT transaction_type, amount, new_balance, description, reference_id, transaction_date " +
                     "FROM wallet_transactions WHERE username = ? ORDER BY transaction_date DESC LIMIT 20")) {
            
            stmt.setString(1, username);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Object[] row = {
                        rs.getString("transaction_type"),
                        rs.getDouble("amount"),
                        rs.getDouble("new_balance"),
                        rs.getString("description"),
                        rs.getString("reference_id"),
                        rs.getTimestamp("transaction_date")
                    };
                    transactions.add(row);
                }
            }
        } catch (SQLException e) {
            System.err.println("Error getting wallet transaction history: " + e.getMessage());
            e.printStackTrace();
        }
        
        return transactions;
    }
    
    /**
     * Gets the 5 latest wallet transactions for a user (specifically for Wallet panel preview)
     * @param username the username
     * @return list of wallet transaction records (maximum 5)
     */
    public static java.util.List<Object[]> getLatestWalletTransactions(String username) {
        java.util.List<Object[]> transactions = new ArrayList<>();
        
        if (username == null || username.trim().isEmpty()) {
            return transactions;
        }
        
        try (Connection conn = cephra.Database.DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT transaction_type, amount, new_balance, description, reference_id, transaction_date " +
                     "FROM wallet_transactions WHERE username = ? ORDER BY transaction_date DESC LIMIT 5")) {
            
            stmt.setString(1, username);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Object[] row = {
                        rs.getString("transaction_type"),
                        rs.getDouble("amount"),
                        rs.getDouble("new_balance"),
                        rs.getString("description"),
                        rs.getString("reference_id"),
                        rs.getTimestamp("transaction_date")
                    };
                    transactions.add(row);
                }
            }
        } catch (SQLException e) {
            System.err.println("Error getting latest wallet transactions: " + e.getMessage());
            e.printStackTrace();
        }
        
        return transactions;
    }
    
    /**
     * Checks if user has sufficient wallet balance for a payment
     * @param username the username
     * @param amount the amount required
     * @return true if balance is sufficient
     */
    public static boolean hasSufficientWalletBalance(String username, double amount) {
        double currentBalance = getUserWalletBalance(username);
        return currentBalance >= amount;
    }
    
    /**
     * Processes wallet payment for a ticket
     * @param username the username
     * @param ticketId the ticket ID
     * @param amount the payment amount
     * @return true if payment successful
     */
    public static boolean processWalletPayment(String username, String ticketId, double amount) {
        if (!hasSufficientWalletBalance(username, amount)) {
            return false;
        }
        
        String description = "Payment for ticket " + ticketId;
        return updateWalletBalance(username, -amount, "PAYMENT", description, ticketId);
    }
    
    /**
     * Processes wallet top-up
     * @param username the username
     * @param amount the top-up amount
     * @param method the top-up method (e.g., "Quick Amount A", "Custom Amount")
     * @return true if top-up successful
     */
    public static boolean processWalletTopUp(String username, double amount, String method) {
        String description = "Wallet top-up via " + method;
        String referenceId = "TOPUP_" + System.currentTimeMillis();
        return updateWalletBalance(username, amount, "TOP_UP", description, referenceId);
    }
    
    // ========================= PROFILE PICTURE METHODS =========================
    
    /**
     * Saves a user's profile picture to the database
     * @param username the username
     * @param profilePictureBase64 the profile picture as Base64 encoded string
     * @return true if successful, false otherwise
     */
    public static boolean saveUserProfilePicture(String username, String profilePictureBase64) {
        if (username == null || username.trim().isEmpty()) {
            System.err.println("CephraDB: Invalid username for profile picture save");
            return false;
        }
        
        try (Connection conn = cephra.Database.DatabaseConnection.getConnection()) {
            // First check if profile_picture column exists, add it if not
            if (!columnExists(conn, "users", "profile_picture")) {
                addProfilePictureColumn(conn);
            }
            
            try (PreparedStatement stmt = conn.prepareStatement(
                    "UPDATE users SET profile_picture = ? WHERE username = ?")) {
                
                stmt.setString(1, profilePictureBase64);
                stmt.setString(2, username);
                
                int rowsAffected = stmt.executeUpdate();
                if (rowsAffected > 0) {
                    System.out.println("CephraDB: Successfully saved profile picture for user " + username);
                    return true;
                } else {
                    System.err.println("CephraDB: No user found with username " + username + " to update profile picture");
                    return false;
                }
            }
        } catch (SQLException e) {
            System.err.println("Error saving user profile picture: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Gets a user's profile picture from the database
     * @param username the username
     * @return Base64 encoded profile picture string, or null if not found
     */
    public static String getUserProfilePicture(String username) {
        if (username == null || username.trim().isEmpty()) {
            return null;
        }
        
        try (Connection conn = cephra.Database.DatabaseConnection.getConnection()) {
            // First check if profile_picture column exists
            if (!columnExists(conn, "users", "profile_picture")) {
                System.out.println("CephraDB: profile_picture column doesn't exist yet");
                return null;
            }
            
            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT profile_picture FROM users WHERE username = ?")) {
                
                stmt.setString(1, username);
                
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        String profilePicture = rs.getString("profile_picture");
                        if (profilePicture != null && !profilePicture.trim().isEmpty()) {
                            // If it's a file path (web upload), convert to Base64 data URI
                            if (profilePicture.endsWith(".jpg") || profilePicture.endsWith(".jpeg") || 
                                profilePicture.endsWith(".png") || profilePicture.endsWith(".gif")) {
                                return convertWebImageToDataUri(profilePicture);
                            }
                            return profilePicture;
                        }
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Error getting user profile picture: " + e.getMessage());
            e.printStackTrace();
        }
        
        return null;
    }
    
    /**
     * Converts a web-uploaded image file to Base64 data URI format
     * @param filename the filename of the uploaded image
     * @return Base64 data URI string, or null if conversion fails
     */
    private static String convertWebImageToDataUri(String filename) {
        try {
            // Construct the full path to the uploaded image
            String webPath = "C:/xampp/htdocs/Cephra/Appweb/User/uploads/profile_pictures/" + filename;
            java.io.File imageFile = new java.io.File(webPath);
            
            if (!imageFile.exists()) {
                System.out.println("CephraDB: Web image file not found: " + webPath);
                return null;
            }
            
            // Read the image file and convert to Base64
            byte[] imageBytes = java.nio.file.Files.readAllBytes(imageFile.toPath());
            String base64String = java.util.Base64.getEncoder().encodeToString(imageBytes);
            
            // Determine MIME type based on file extension
            String mimeType = "image/png"; // default
            if (filename.toLowerCase().endsWith(".jpg") || filename.toLowerCase().endsWith(".jpeg")) {
                mimeType = "image/jpeg";
            } else if (filename.toLowerCase().endsWith(".gif")) {
                mimeType = "image/gif";
            }
            
            // Return as data URI
            String dataUri = "data:" + mimeType + ";base64," + base64String;
            return dataUri;
            
        } catch (Exception e) {
            System.err.println("CephraDB: Error converting web image to data URI: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Gets the current logged-in user's profile picture
     * @return Base64 encoded profile picture string, or null if not found
     */
    public static String getCurrentUserProfilePicture() {
        String username = getCurrentUsername();
        if (username != null && !username.isEmpty()) {
            return getUserProfilePicture(username);
        }
        return null;
    }
    
    /**
     * Removes a user's profile picture from the database
     * @param username the username
     * @return true if successful, false otherwise
     */
    public static boolean removeUserProfilePicture(String username) {
        if (username == null || username.trim().isEmpty()) {
            System.err.println("CephraDB: Invalid username for profile picture removal");
            return false;
        }
        
        try (Connection conn = cephra.Database.DatabaseConnection.getConnection()) {
            // First check if profile_picture column exists
            if (!columnExists(conn, "users", "profile_picture")) {
                System.out.println("CephraDB: profile_picture column doesn't exist, nothing to remove");
                return true; // Consider it successful since there's nothing to remove
            }
            
            try (PreparedStatement stmt = conn.prepareStatement(
                    "UPDATE users SET profile_picture = NULL WHERE username = ?")) {
                
                stmt.setString(1, username);
                
                int rowsAffected = stmt.executeUpdate();
                if (rowsAffected > 0) {
                    System.out.println("CephraDB: Successfully removed profile picture for user " + username);
                    return true;
                } else {
                    System.err.println("CephraDB: No user found with username " + username + " to remove profile picture");
                    return false;
                }
            }
        } catch (SQLException e) {
            System.err.println("Error removing user profile picture: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Helper method to add profile_picture column to users table if it doesn't exist
     * @param conn the database connection
     */
    private static void addProfilePictureColumn(Connection conn) {
        try (PreparedStatement stmt = conn.prepareStatement(
                "ALTER TABLE users ADD COLUMN profile_picture LONGTEXT NULL AFTER email")) {
            stmt.executeUpdate();
            System.out.println("CephraDB: Added profile_picture column to users table");
        } catch (SQLException e) {
            // Column might already exist, check error message
            if (e.getMessage().contains("Duplicate column name")) {
                System.out.println("CephraDB: profile_picture column already exists");
            } else {
                System.err.println("Error adding profile_picture column: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

}
