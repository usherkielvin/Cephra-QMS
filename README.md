# Cephra QMS — EV Charging Queue Management System

![Java](https://img.shields.io/badge/Java-21-orange)
![PHP](https://img.shields.io/badge/PHP-8%2B-blue)
![MySQL](https://img.shields.io/badge/MySQL-8.0%2B-blue)
![License](https://img.shields.io/badge/License-MIT-yellow)

Cephra is a full-stack EV charging queue management system designed to optimize station operations, reduce wait times, and improve customer flow.

Built as a Data Structures and Algorithms project, it integrates a **Java desktop system**, a **PHP web application**, and a **shared MySQL database** with real-time updates.

---

## 🚀 TL;DR

- Priority-based EV queue system (low battery first)
- Real-time updates using WebSocket + SSE
- Multi-interface system (Admin, Customer, Monitor)
- Java + PHP connected directly to one MySQL database
- Designed for scalability and real-world station workflows

---

## 📌 Why This Project Matters

EV infrastructure is growing rapidly, but queue handling at charging stations remains inefficient.

Cephra solves this by:
- Automating queue prioritization
- Reducing idle bay time
- Providing real-time visibility for both customers and operators
- Simulating a real production environment using multiple systems

---

## 🧩 Features

- Real-time queue management across 8 charging bays  
- Priority scheduling (battery < 20%)  
- Automatic bay assignment (Fast / Normal)  
- Live station monitor display (TV-ready)  
- Customer queue tracking and charging progress  
- Admin dashboard for operations and control  
- Real-time updates via WebSocket and Server-Sent Events  

---

## 🖥️ System Components

### 1. Admin Panel (Java Swing)
- Queue management  
- Bay allocation  
- Staff and transaction control  
- Real-time monitoring  

### 2. Phone Simulator (Java Swing)
- Simulates customer mobile interface  
- Queue status and charging progress  

### 3. Web Application (PHP)
- Customer interface (browser-based)  
- Admin dashboard (SSE-powered)  
- Monitor display (WebSocket-powered)  

---

## 🛠️ Tech Stack

| Layer | Technology |
|------|-----------|
| Desktop App | Java 21, Swing, Maven |
| Web Backend | PHP 8+, PDO |
| Database | MySQL 8 |
| Connection Pool | HikariCP |
| Real-time | WebSocket (Ratchet), Server-Sent Events |
| Email | PHPMailer |
| Frontend | HTML, CSS, JavaScript, Bootstrap |

---

## 🏗️ Architecture

Both Java and PHP systems connect directly to a shared MySQL database.
Java App ─── JDBC ───┐
├── MySQL (cephradb)
PHP Web ─── PDO ────┘
│
├── SSE (Admin updates)
└── WebSocket (Monitor display)


### Real-Time Flow
- MySQL triggers log updates into a `notifications` table  
- PHP services read and broadcast updates  
- Clients receive instant updates without refresh  

---

## 🧠 Data Structures & Algorithms

### QueueFlow
- Uses `PriorityQueue<Entry>`
- Vehicles with battery < 20% are prioritized
- FIFO preserved within same priority

### BayManagement
- Assigns available charging bays dynamically
- Supports Fast and Normal chargers

### ChargingManager
- Uses background `Timer`
- Battery increases 1% per tick
- Auto-completes at 100%

### Ticket Format
- `FCH001` — Fast charging  
- `NCH001` — Normal charging  
- `FCHP001` / `NCHP001` — Priority tickets  

---

## 📂 Project Structure


Cephra-QMS/
├── src/main/java/cephra/
│ ├── Admin/
│ ├── Database/
│ ├── Frame/
│ ├── Phone/
│ └── Launcher.java
├── src/main/resources/
├── Appweb/
│ ├── Admin/
│ ├── User/
│ ├── Monitor/
│ └── shared/
└── pom.xml


---

## ⚙️ Setup

### Requirements
- Java 21+
- Maven
- MySQL 8+
- PHP (local server like Apache/Nginx)

---

### 1. Database Setup

Run:


Appweb/shared/notifications_setup.sql


---

### 2. Configure Web App

Create `.env` file:


DB_HOST=127.0.0.1
DB_NAME=cephradb
DB_USER=root
DB_PASSWORD=yourpassword


---

### 3. Configure Java App

Edit:


src/main/resources/db.properties


Set your database password:


dataSource.password=yourpassword


---

### 4. Run Web App

Serve the `Appweb/` folder using your PHP server.

---

### 5. Run Java App


mvn exec:java


---

## 🌐 Access

- Customer: `http://localhost/Cephra/Appweb/User/`  
- Admin: `http://localhost/Cephra/Appweb/Admin/`  
- Monitor: `http://localhost/Cephra/Appweb/Monitor/`  

---

## 📸 Demo / Screenshots

> (Add your screenshots here for portfolio)

Example:







---

## 📊 Future Improvements

- Mobile app (React Native / Flutter)
- API layer (Spring Boot REST)
- Cloud deployment (AWS / GCP)
- Payment gateway integration
- Predictive queue optimization (AI-based)

---

## 👥 Team

- **Usher Kielvin Ponce** — Project Lead, Backend  
- **Mark Dwayne P. Dela Cruz** — Web UI/UX  
- **Dizon S. Dizon** — Backend, Database  
- **Kenji A. Hizon** — Java Frontend  

---

## 📄 License

MIT License
