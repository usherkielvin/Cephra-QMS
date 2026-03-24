# Forgot Password Database Integration Setup Guide

## Overview
This guide will help you integrate the forgot password functionality with your MySQL database. The system is designed to work with your existing `cephradb` database.

## Step 1: Create Database Table

Run the following SQL in your MySQL database (phpMyAdmin or command line):

```sql
-- Password Reset Tokens Table
CREATE TABLE IF NOT EXISTS `password_reset_tokens` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `email` varchar(255) NOT NULL,
  `reset_code` varchar(6) NOT NULL,
  `temp_token` varchar(64) DEFAULT NULL,
  `expires_at` datetime NOT NULL,
  `used` tinyint(1) NOT NULL DEFAULT 0,
  `password_updated` tinyint(1) NOT NULL DEFAULT 0,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `email` (`email`),
  KEY `reset_code` (`reset_code`),
  KEY `temp_token` (`temp_token`),
  KEY `expires_at` (`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Add indexes for better performance
CREATE INDEX idx_email_used ON password_reset_tokens(email, used);
CREATE INDEX idx_temp_token_used ON password_reset_tokens(temp_token, used);
CREATE INDEX idx_expires_at ON password_reset_tokens(expires_at);
```

## Step 2: Configure Email Settings

1. Open `Appweb/User/config/email_config.php`
2. Update the following variables:
   ```php
   public static $smtp_host = 'smtp.gmail.com'; // Your SMTP server
   public static $smtp_port = 587; // Your SMTP port
   public static $smtp_username = 'your-email@gmail.com'; // Your email
   public static $smtp_password = 'your-app-password'; // App password
   public static $from_email = 'your-email@gmail.com'; // From email
   public static $from_name = 'Cephra Support'; // From name
   ```

## Step 3: Integrate Email Sending (Optional)

To actually send emails instead of showing mockup popups, update the `request-reset` action in `Appweb/User/api/forgot_password.php`:

```php
// Replace the mockup response with actual email sending
require_once "../config/email_config.php";

$emailTemplate = EmailConfig::getResetEmailTemplate($email, $reset_code);

// Send email using PHP mail() function
$headers = implode("\r\n", $emailTemplate['headers']);
$mailSent = mail($email, $emailTemplate['subject'], $emailTemplate['message'], $headers);

if ($mailSent) {
    echo json_encode([
        "success" => true,
        "message" => "Reset code sent to your email"
        // Remove the reset_code from response in production
    ]);
} else {
    echo json_encode([
        "success" => false,
        "error" => "Failed to send email. Please try again."
    ]);
}
```

## Step 4: Test the Integration

1. **Start your web server:**
   ```bash
   cd Appweb/User
   php -S localhost:8000
   ```

2. **Test the flow:**
   - Go to `http://localhost:8000/index.php`
   - Click "Forgot password?"
   - Enter an email from your users table
   - Check the API response for the reset code
   - Use the code in the verification step
   - Complete the password reset

## Step 5: Security Considerations

1. **Remove mockup code from production:**
   - Remove `reset_code` from API responses
   - Implement proper email sending
   - Add rate limiting

2. **Database security:**
   - Use prepared statements (already implemented)
   - Validate all inputs
   - Use HTTPS in production

3. **Session security:**
   - Tokens expire in 30 minutes
   - One-time use tokens
   - Secure session handling

## Database Schema

### password_reset_tokens table:
- `id` - Primary key
- `email` - User's email address
- `reset_code` - 6-digit verification code
- `temp_token` - Temporary token for password reset
- `expires_at` - When the code expires
- `used` - Whether the code has been used
- `password_updated` - Whether password was successfully updated
- `created_at` - When the token was created
- `updated_at` - When the token was last updated

## API Endpoints

### POST /api/forgot_password.php

#### Actions:
1. **request-reset**
   - Parameters: `email`
   - Returns: success message and reset code

2. **verify-code**
   - Parameters: `email`, `code`
   - Returns: temporary token

3. **reset-password**
   - Parameters: `temp_token`, `new_password`
   - Returns: success/error message

4. **resend-code**
   - Parameters: `email`
   - Returns: new reset code

## File Structure

```
Appweb/User/
├── api/
│   └── forgot_password.php          # Main API endpoint
├── config/
│   ├── database.php                 # Database connection (stub → shared/database.php)
│   ├── email_config.php             # Email configuration
│   └── SETUP_GUIDE.md              # This guide
├── forgot_password.php              # Email input page
├── verify_code.php                  # Code verification page
├── reset_password.php               # Password reset page
└── [CSS and JS files]               # Styling and functionality
```

## Troubleshooting

### Common Issues:

1. **Database connection failed:**
   - Check if MySQL is running
   - Verify database credentials in `config/database.php`
   - Ensure `cephradb` database exists

2. **Email not sending:**
   - Configure SMTP settings in `email_config.php`
   - Check PHP mail configuration
   - Use a service like SendGrid for production

3. **Codes not working:**
   - Check if table was created correctly
   - Verify API responses in browser console
   - Check PHP error logs

### Debug Mode:
The API includes detailed error messages. Check the browser's Network tab and console for debugging information.

## Production Deployment

1. **Remove debug information:**
   - Remove `reset_code` from API responses
   - Disable error display in production
   - Use proper logging instead

2. **Security hardening:**
   - Implement rate limiting
   - Use HTTPS
   - Validate all inputs server-side
   - Implement proper session management

3. **Email delivery:**
   - Use a professional email service
   - Implement proper email templates
   - Add unsubscribe links if needed

## Support

If you encounter any issues:
1. Check the browser console for JavaScript errors
2. Check the Network tab for API responses
3. Verify database connectivity
4. Check PHP error logs

The system is designed to be robust and secure. All database operations use prepared statements to prevent SQL injection.
