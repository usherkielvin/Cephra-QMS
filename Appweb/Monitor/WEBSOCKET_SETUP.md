# WebSocket Server — Setup & Process Management

The Monitor uses a persistent PHP WebSocket server (Ratchet) for real-time bay/queue updates.
This process must be running separately from Apache/XAMPP.

## Starting the server (development)

```bash
cd Cephra-QMS/Appweb/Monitor
php bin/server.php
```

The server listens on **port 8080** by default.
The browser client auto-falls back to 3-second HTTP polling if the WebSocket is unavailable.

---

## Production — keeping it alive with Supervisor

Install Supervisor (Linux):

```bash
sudo apt install supervisor
```

Create `/etc/supervisor/conf.d/cephra-monitor.conf`:

```ini
[program:cephra-monitor-ws]
command=php /var/www/html/Cephra/Appweb/Monitor/bin/server.php
directory=/var/www/html/Cephra/Appweb/Monitor
autostart=true
autorestart=true
stderr_logfile=/var/log/cephra-monitor.err.log
stdout_logfile=/var/log/cephra-monitor.out.log
user=www-data
```

Then reload Supervisor:

```bash
sudo supervisorctl reread
sudo supervisorctl update
sudo supervisorctl start cephra-monitor-ws
```

Check status:

```bash
sudo supervisorctl status cephra-monitor-ws
```

---

## Windows (XAMPP / development)

Run the server in a separate terminal window:

```bat
cd C:\xampp\htdocs\Cephra\Appweb\Monitor
php bin\server.php
```

To auto-start on Windows, create a scheduled task or use NSSM (Non-Sucking Service Manager):

```bat
nssm install CephraMonitorWS "php" "C:\xampp\htdocs\Cephra\Appweb\Monitor\bin\server.php"
nssm start CephraMonitorWS
```

---

## Firewall

Ensure port **8080** is open on the server if the Monitor is accessed from other machines.
For production behind a reverse proxy (Nginx/Apache), you can proxy WebSocket traffic:

```nginx
location /ws/ {
    proxy_pass http://127.0.0.1:8080;
    proxy_http_version 1.1;
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection "Upgrade";
}
```

---

## Fallback behaviour

If the WebSocket server is not running, the Monitor UI automatically falls back to polling
`api/monitor.php` every **3 seconds**. The connection indicator turns red to signal polling mode.
