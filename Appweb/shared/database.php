<?php
/**
 * Shared Database Configuration
 * Single source of truth for all DB connections across Admin, Monitor, and User.
 *
 * Usage from any component:
 *   require_once __DIR__ . '/../../shared/database.php';  // adjust depth as needed
 *   $conn = (new Database())->getConnection();
 *
 * Credentials are read from environment variables (set in your web server config or a .env file).
 * Copy Appweb/.env.example to Appweb/.env and fill in your values for local development.
 */

// Load .env file if present (simple key=value parser, no external dependency needed)
(static function () {
    $envFile = __DIR__ . '/../.env';
    if (!file_exists($envFile)) return;
    foreach (file($envFile, FILE_IGNORE_NEW_LINES | FILE_SKIP_EMPTY_LINES) as $line) {
        $line = trim($line);
        if ($line === '' || str_starts_with($line, '#')) continue;
        [$key, $value] = array_map('trim', explode('=', $line, 2));
        if ($key !== '' && getenv($key) === false) {
            putenv("{$key}={$value}");
        }
    }
})();
class Database {
    private string $host;
    private string $db_name;
    private string $username;
    private string $password;
    private string $charset = 'utf8mb4';
    private ?PDO $conn = null;

    public function __construct() {
        $this->host     = getenv('DB_HOST')     ?: '127.0.0.1';
        $this->db_name  = getenv('DB_NAME')     ?: 'cephradb';
        $this->username = getenv('DB_USER')     ?: 'root';
        $this->password = getenv('DB_PASSWORD') ?: '';
    }

    private function isLocalhost(): bool {
        $host = $_SERVER['HTTP_HOST'] ?? '';
        return (
            $host === 'localhost' ||
            $host === '127.0.0.1' ||
            str_starts_with($host, 'localhost:') ||
            str_starts_with($host, '127.0.0.1:')
        );
    }

    public function getConnection(): ?PDO {
        if ($this->conn !== null) {
            return $this->conn;
        }
        try {
            $dsn = "mysql:host={$this->host};port=3306;dbname={$this->db_name};charset={$this->charset}";
            $options = [
                PDO::ATTR_ERRMODE            => PDO::ERRMODE_EXCEPTION,
                PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
                PDO::ATTR_EMULATE_PREPARES   => false,
            ];
            $this->conn = new PDO($dsn, $this->username, $this->password, $options);
            $this->conn->exec("SET time_zone = '+08:00'"); // Asia/Manila
            return $this->conn;
        } catch (PDOException $e) {
            $env = $this->isLocalhost() ? 'LOCALHOST' : 'PRODUCTION';
            error_log("Database connection failed ({$env}): " . $e->getMessage());
            return null;
        }
    }
}
