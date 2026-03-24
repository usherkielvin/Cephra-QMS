# Cephra — EV Charging Queue Management System

![Java](https://img.shields.io/badge/Java-21-orange)
![PHP](https://img.shields.io/badge/PHP-8%2B-blue)
![MySQL](https://img.shields.io/badge/MySQL-8.0%2B-blue)
![License](https://img.shields.io/badge/License-MIT-yellow)

Final project for Data Structures & Algorithms — NU MOA. A full-stack EV charging station queue management system with a Java desktop app and a PHP web app sharing one MySQL database.

---

## What it does

Manages EV charging queues across 8 bays. Customers join a queue, get assigned a bay, charge, and pay. Admins manage the queue, bays, and staff in real time.

Three interfaces run simultaneously:
- **Java Admin Panel** — desktop app for station operators (queue, bays, staff, payments, history)
- **Java Phone Simulator** — 350×750px Swing window simulating a customer's mobile experience
- **PHP Web App** — real browser-based interface for customers (`User/`) and admins (`Admin/`), plus a live display board (`Monitor/`) meant for a TV screen at the station

---

## Tech stack

| Layer | Tech |
|---|---|
| Desktop app | Java 21, Swing, Maven |
| Desktop DB access | HikariCP 5.1, MySQL Connector/J 8.4 |
| Web backend | PHP 8+, PDO |
| Web frontend | HTML/CSS/JS, Bootstrap, Font Awesome |
| Email | PHPMailer 6.10 |
| Real-time web | Ratchet WebSocket (Monitor), Server-Sent Events (Admin) |
| Database | MySQL 8.0+ via XAMPP |

---

## Architecture

Both the Java app and PHP web app connect directly to the same `cephradb` MySQL database — no API layer between them. MySQL triggers write to a `notifications` table on any status change; the PHP WebSocket server and SSE endpoint read from it to push updates to browsers instantly.

```
Java Swing  ──── JDBC/HikariCP ────┐
                                    ├──► MySQL (cephradb)
PHP Web     ──── PDO ──────────────┘
                 │
                 ├── SSE (Admin dashboard — event-driven)
                 └── WebSocket/Ratchet (Monitor display — 1s push)
```

---

## DSA implementation

- `QueueFlow` — `PriorityQueue<Entry>` where tickets with battery < 20% sort ahead of normal tickets, FIFO preserved as tiebreaker within same priority
- `BayManagement` — bay allocation algorithm assigns available bays by type (Fast/Normal)
- `ChargingManager` — background `javax.swing.Timer` per user, increments battery 1%/tick, completes session at 100%
- Ticket IDs: `FCH001` (Fast), `NCH001` (Normal), `FCHP001`/`NCHP001` (priority variants)

---

## Project structure

```
Cephra-QMS/
├── src/main/java/cephra/
│   ├── Admin/          # Admin Swing panels (Queue, BayManagement, History, etc.)
│   ├── Database/       # DatabaseConnection (HikariCP), CephraDB (all queries), HttpNotifier
│   ├── Frame/          # Top-level windows (Admin, Phone, Monitor)
│   ├── Phone/          # Customer simulator (Dashboard, Wallet, Profile, Utilities)
│   └── Launcher.java   # Entry point — opens Admin + Phone windows
├── src/main/resources/
│   ├── db.properties       # DB credentials (not committed)
│   └── config.properties   # Web notify URL
├── Appweb/
│   ├── shared/         # Shared database.php + notifications_setup.sql
│   ├── Admin/          # Web admin dashboard + SSE endpoint
│   ├── Monitor/        # Live display board (WebSocket/Ratchet)
│   ├── User/           # Customer web app (PWA-ready)
│   └── notify.php      # Internal endpoint — Java posts events here
└── pom.xml
```

---

## Setup

**Prerequisites:** Java 21+, Maven, XAMPP (Apache + MySQL)

**1. Database**

Open `Appweb/shared/notifications_setup.sql` in MySQL Workbench and execute (⚡). This creates the `notifications` table and triggers on top of the existing `cephradb` schema.

**2. Web credentials**

Copy `Appweb/.env.example` to `Appweb/.env` and set your MySQL password:
```
DB_HOST=127.0.0.1
DB_NAME=cephradb
DB_USER=root
DB_PASSWORD=yourpassword
```

**3. Java credentials**

Edit `src/main/resources/db.properties` — set `dataSource.password`.

**4. Place web files**

Copy `Appweb/` into `htdocs/Cephra/Appweb/` and start Apache + MySQL in XAMPP.

**5. Run Java app**
```bash
mvn exec:java
```

**Web URLs**
- Customer: `http://localhost/Cephra/Appweb/User/`
- Admin: `http://localhost/Cephra/Appweb/Admin/`
- Monitor: `http://localhost/Cephra/Appweb/Monitor/`

---

## Team

| Name | Role |
|---|---|
| Usher Kielvin Ponce | Project lead, backend |
| Mark Dwayne P. Dela Cruz | Web UI/UX |
| Dizon S. Dizon | Backend, database |
| Kenji A. Hizon | Java Swing frontend |

---

## License

MIT — see [LICENSE](LICENSE)
