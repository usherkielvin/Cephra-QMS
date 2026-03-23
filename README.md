# 🔋 Cephra - EV Charging Queue Management System

![Java](https://img.shields.io/badge/Java-24-orange)
![Maven](https://img.shields.io/badge/Maven-3.11.0-blue)
![Swing](https://img.shields.io/badge/Swing-GUI-green)
![MySQL](https://img.shields.io/badge/MySQL-8.0+-blue)
![Web](https://img.shields.io/badge/Web-PHP%20%2B%20HTML5-blue)
![License](https://img.shields.io/badge/License-MIT-yellow)

## 🎯 Introduction

**Cephra** is a comprehensive Electric Vehicle (EV) charging station queue management system that bridges the gap between traditional desktop applications and modern web interfaces. Built with Java Swing for robust desktop functionality and enhanced with a mobile-optimized web interface, Cephra provides a complete solution for managing EV charging stations, customer queues, and payment processing.

> **📚 Academic Project**: This is a final project for the Data Structures & Algorithms course, demonstrating practical implementation of queue management algorithms, data structures, and modern software development practices.

### Why Cephra?
- **Dual Interface**: Seamlessly combines Java desktop applications with modern web technology
- **Real-time Management**: Live queue updates and station monitoring
- **Mobile-First Web Design**: Accessible from any device with internet connection
- **Complete EV Ecosystem**: From customer registration to payment processing
- **Scalable Architecture**: Built to handle multiple charging stations and high customer volumes

## ✨ Features

### 🔧 Admin Panel (Java Desktop)
- **📊 Dashboard Management** - Real-time overview of all charging stations
- **📋 Queue Management** - Live status tracking and customer management
- **👥 Staff Records** - Complete employee management system
- **📈 History Tracking** - Detailed analytics and reporting
- **🔌 Bay Management** - Monitor and control charging bays
- **💳 Payment Processing** - Integrated payment system with transaction history

### 📱 Customer Mobile Interface
- **🖥️ Java Desktop App** - Native 350x750 mobile interface simulation
- **🌐 Web Interface** - Modern, responsive mobile-optimized web UI
- **👤 User Registration** - Complete profile management with firstname/lastname support
- **⏳ Queue Joining** - Real-time queue updates and notifications
- **⚡ Service Selection** - Choose between Fast Charging and Normal Charging
- **🔋 Battery Monitoring** - Track battery levels and charging history
- **📱 Cross-Platform** - Works on desktop, tablet, and mobile devices

### 📺 Display Monitor (Java)
- **📺 Real-time Display** - Public queue status and station information
- **🔔 Notifications** - Announcements and system alerts
- **📊 Live Statistics** - Current queue length and wait times
- **🎨 Customizable Interface** - Adjustable display settings

### 🌐 Web Integration
- **🔗 API Endpoints** - RESTful API for mobile web integration
- **📱 Mobile Responsive** - Optimized for all screen sizes
- **🔄 Real-time Sync** - Live synchronization between Java and web interfaces
- **🔐 Secure Authentication** - User login and session management

## 📁 Project Structure

```
Cephra/
├── 📁 src/                          # Java Source Code (Primary System)
│   └── main/java/cephra/
│       ├── Admin/                   # Admin panel components (20 files)
│       ├── Database/                # Database connection classes
│       ├── Frame/                   # Main application frames (6 files)
│       ├── Phone/                   # Customer mobile interface (96 files)
│       └── Launcher.java           # Application entry point
│   └── resources/
│       ├── db/                     # Database schema files (4 SQL files)
│       └── cephra/Cephra Images/   # Application images and icons (131 files)
├── 📁 Appweb/                       # Web Interface Components
│   ├── Admin/                      # Admin web interface
│   │   ├── api/                    # Admin API endpoints
│   │   ├── config/                 # Admin configuration
│   │   ├── css/                    # Admin stylesheets
│   │   ├── images/                 # Admin interface images
│   │   ├── js/                     # Admin JavaScript files
│   │   └── *.php                   # Admin PHP pages
│   ├── Monitor/                    # Queue monitor web interface
│   │   ├── api/                    # Monitor API endpoints
│   │   ├── src/                    # Monitor source files
│   │   └── *.php                   # Monitor PHP pages
│   ├── User/                       # Customer web interface
│   │   ├── api/                    # User API endpoints
│   │   ├── assets/                 # CSS, JS, and web assets
│   │   ├── config/                 # User configuration files
│   │   ├── css/                    # User stylesheets
│   │   ├── images/                 # User interface images
│   │   ├── partials/               # Reusable PHP components
│   │   ├── uploads/                # User uploads directory
│   │   ├── vendor/                 # Composer dependencies
│   │   └── *.php                   # User PHP pages
│   └── Documentation.md            # Web interface documentation
├── 📁 config/                      # Configuration Files
│   └── database.php               # Database configuration
├── 📁 docs/                        # Documentation
│   ├── SETUP.md                  # Setup instructions
│   ├── MYSQL_SETUP.md            # MySQL configuration
│   ├── XAMPP_SETUP.md            # Web server setup
│   └── TESTING.md                # Testing guidelines
├── 📁 target/                      # Maven Build Output
│   └── classes/                   # Compiled Java classes
├── pom.xml                        # Maven project configuration
├── nb-configuration.xml           # NetBeans project settings
├── setup_database.php            # Database setup script
├── setup_database_tables.php     # Database table creation
├── setup_plate_numbers.sql       # Plate number setup
├── add_missing_plate_columns.sql # Database migration script
├── DATA_STRUCTURES_ALGORITHMS_Final_Project.txt # Project documentation
├── README.md                      # This file
├── TODO.md                        # Project tasks and roadmap
├── LICENSE                        # MIT License
├── CODE_OF_CONDUCT.md            # Code of conduct
├── CONTRIBUTING.md               # Contributing guidelines
└── SECURITY.md                   # Security policy
```

## 🚀 Installation & Setup

### Prerequisites
- **Java 24** or higher
- **Maven 3.11.0** or higher
- **MySQL 8.0+** or **XAMPP** (for web interface)
- **Windows 10/11** (batch scripts are Windows-specific)

### Quick Setup (Recommended)

1. **Clone the Repository**
   ```bash
   git clone https://github.com/yourusername/cephra.git
   cd cephra
   ```

2. **Initialize Database**
   ```bash
   scripts/init-database.bat
   ```

3. **Run Application**
   ```bash
   scripts/run.bat
   ```

4. **Access Web Interface**
   - Mobile Web: `http://localhost/cephra/mobileweb/`
   - Queue Monitor: `http://localhost/cephra/api/view-queue.php`

### Manual Setup

#### Database Setup
1. **MySQL Configuration**
   ```bash
   # Edit database settings
   config/database.php
   ```

2. **Initialize Schema**
   ```bash
   # Run SQL initialization
   php setup_database.php
   ```

#### Java Application
1. **Compile with Maven**
   ```bash
   mvn clean compile
   ```

2. **Run Application**
   ```bash
   mvn exec:java -Dexec.mainClass="cephra.Launcher"
   ```

#### Web Interface Setup
1. **XAMPP Setup**
   - Install XAMPP
   - Copy project to `htdocs/cephra/`
   - Start Apache and MySQL services

2. **Database Connection**
   - Update `config/database.php` with your MySQL credentials
   - Run `scripts/init-database.bat`

### Default Login Credentials
- **Username**: `dizon`
- **Password**: `123`

## 🛠️ Technologies Used

### Backend
- **Java 24** - Core application logic
- **Java Swing** - Desktop GUI framework
- **Maven** - Build and dependency management
- **MySQL 8.0+** - Primary database
- **HikariCP** - High-performance connection pooling

### Frontend
- **PHP 8+** - Web interface backend
- **HTML5/CSS3** - Modern web markup and styling
- **JavaScript** - Interactive web components
- **Bootstrap** - Responsive web framework
- **Font Awesome** - Icon library

### Development Tools
- **NetBeans** - Java IDE integration
- **VS Code** - Web development environment
- **Git** - Version control
- **XAMPP** - Local web server stack
- **JUnit 5** - Unit testing framework

## 🎮 Usage

### Java Application Launch
The application launches three synchronized interfaces:
1. **Admin Panel** (Right side) - Station management and monitoring
2. **Display Monitor** (Top-left) - Public queue display
3. **Customer Mobile** (Center) - Customer interface simulation

### Web Interface Access
- **Mobile Web**: Access from any device with internet connection
- **Real-time Updates**: Live synchronization with Java application
- **Responsive Design**: Optimized for mobile, tablet, and desktop

### Key Workflows
1. **Customer Registration** → **Queue Joining** → **Service Selection** → **Payment Processing**
2. **Admin Monitoring** → **Queue Management** → **Payment Verification** → **History Tracking**

### Recent Enhancements
- **🔋 Enhanced Battery Management**: Real-time battery level tracking with dynamic range calculations
- **🚗 Improved Vehicle Linking**: Dynamic vehicle model display with battery specifications
- **📱 Dashboard Improvements**: Enhanced user dashboard with live status updates and responsive design
- **🎨 UI/UX Updates**: Modern interface improvements across all platforms
- **🔧 Database Optimizations**: Improved connection pooling with HikariCP integration

## 🔧 Configuration

### Database Settings
- **Java**: `src/main/java/cephra/db/DatabaseConnection.java`
- **Web**: `config/database.php`

### Application Settings
- **Maven**: `pom.xml`
- **NetBeans**: `config/nb-configuration.xml`
- **VS Code**: `vscode/` folder

### Scripts Customization
- See `scripts/README.md` for detailed script documentation
- Modify batch files for custom deployment scenarios

## 📚 Documentation

- **[Complete Setup Guide](docs/SETUP.md)** - Comprehensive installation instructions
- **[MySQL Configuration](docs/MYSQL_SETUP.md)** - Database setup and optimization
- **[XAMPP Setup](docs/XAMPP_SETUP.md)** - Web server configuration
- **[Testing Guidelines](docs/TESTING.md)** - Testing procedures and best practices
- **[Scripts Documentation](scripts/README.md)** - Batch scripts reference

## 🤝 Contributing

We welcome contributions! Please see our contributing guidelines:

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## 📞 Support

- **Documentation**: Check the `docs/` folder for detailed guides
- **Issues**: Report bugs and request features via GitHub Issues
- **Scripts**: Use the provided batch scripts for common tasks

## 👥 Development Team – Cephra QMS

### 👨‍💻 Project Lead
**Usher Kielvin Ponce** – Project Lead, Backend Developer & EV Technology Enthusiast

- Backend logic & project coordination
- Manages EV charging system flow

### 🎨 UI/UX & Web Developer
**Mark Dwayne P. Dela Cruz** – Web Interface & User Experience

- Designs and develops mobile web interface for customers
- Focuses on intuitive, responsive layouts

### 🔧 Backend Developer
**Dizon S. Dizon** – Backend Development & Database Architecture

- Expert in Java backend development
- Handles server-side logic and database design

### 💻 Frontend Developer (Java Swing)
**Kenji A. Hizon** – Desktop Application Interface Developer

- Specializes in Java Swing GUI
- Focuses on building an intuitive and functional desktop interface

## 📚 Academic Project Details

### Data Structures & Algorithms Implementation

This project demonstrates practical implementation of core computer science concepts:

#### Key Data Structures Used
- **ArrayList** - Dynamic queue management and ticket storage
- **HashMap** - Battery information mapping and user data storage  
- **Boolean Arrays** - Bay availability and occupation tracking
- **PreparedStatement** - Secure database operations
- **Swing Components** - GUI elements and user interface

#### Key Algorithms Implemented
- **Queue Management Algorithm** - FIFO (First In, First Out) with priority handling for low battery levels
- **Bay Allocation Algorithm** - Checks availability and assigns appropriate charging bays
- **Ticket Generation Algorithm** - Sequential numbering system (FCH001, NCH001, etc.)
- **Battery Level Validation** - Prevents charging when battery is full
- **Service Selection Algorithm** - Validates user eligibility and bay availability

#### Project Scope
- **Course**: Data Structures and Algorithms Final Project
- **Institution**: NU MOA (National University - Mall of Asia)
- **Objective**: Apply and implement learnings from the course in a real-world application
- **Focus**: Queue Management System (QMS) for EV Charging Stations

## 🔄 API Documentation

### Core Endpoints

#### Mobile API (`api/mobile.php`)
- **GET** `/api/mobile.php?action=get_queue` - Retrieve current queue status
- **POST** `/api/mobile.php?action=join_queue` - Join charging queue
- **POST** `/api/mobile.php?action=leave_queue` - Leave current queue
- **GET** `/api/mobile.php?action=get_history` - Get user's charging history

#### Web Interface APIs
- **POST** `/api/check_email.php` - Email validation for registration
- **POST** `/api/forgot_password.php` - Password reset functionality
- **GET** `/api/view-queue.php` - Public queue monitoring

### Authentication
- Session-based authentication for web interface
- Username/password validation
- Secure password hashing with bcrypt

## 🧪 Testing

### Automated Testing
- Unit tests for Java components
- Integration tests for database operations
- API endpoint testing with Postman collections

### Manual Testing Guidelines
- See `docs/TESTING.md` for comprehensive testing procedures
- User acceptance testing for new features
- Cross-browser compatibility testing

## 🚀 Deployment

### Production Setup
1. **Server Requirements**
   - Apache/Nginx web server
   - PHP 8.0+ with PDO extension
   - MySQL 8.0+ database
   - Java 24 runtime for admin panel

2. **Security Configuration**
   - SSL certificate installation
   - Database connection encryption
   - Secure session management
   - Input validation and sanitization

3. **Performance Optimization**
   - Database query optimization
   - Caching implementation
   - CDN for static assets
   - Load balancing for high traffic

## 📊 Monitoring & Analytics

### System Monitoring
- Real-time queue length tracking
- Charging station utilization reports
- Payment transaction analytics
- User activity logs

### Performance Metrics
- Average queue wait times
- Charging session completion rates
- System uptime and availability
- User satisfaction scores

## 🔐 Security Features

### Data Protection
- Encrypted database connections
- Secure password storage (bcrypt hashing)
- XSS protection in web forms
- CSRF protection for API endpoints

### Access Control
- Role-based access (Admin, Staff, Customer)
- Session timeout management
- IP-based restrictions for admin panel
- Audit logging for sensitive operations

## 🐛 Troubleshooting

### Common Issues
- **Database Connection Errors**: Check MySQL credentials in `config/database.php`
- **Java Application Won't Start**: Verify Java 21 installation and Maven dependencies
- **Web Interface Not Loading**: Ensure Apache is running and files are in correct directory
- **Queue Sync Issues**: Check WebSocket connections and API endpoints

### Debug Mode
- Enable debug logging in Java application
- PHP error reporting in web interface
- Database query logging for performance issues

## 📈 Roadmap

### Upcoming Features
- [ ] Mobile app development (React Native)
- [ ] Advanced analytics dashboard
- [ ] Multi-language support (i18n)
- [ ] Integration with third-party EV APIs
- [ ] Automated testing suite expansion
- [ ] Cloud deployment support

### Version History
- **v1.0.0** - Initial release with core functionality
- **v1.1.0** - Web interface enhancements
- **v1.2.0** - API improvements and bug fixes
- **v2.0.0** - Planned major update with mobile app

## 🤝 Contributing Guidelines

### Code Standards
- Java: Follow Google Java Style Guide
- PHP: PSR-12 coding standards
- HTML/CSS: Consistent indentation and semantic markup
- Commit messages: Clear, descriptive, and prefixed with type

### Development Workflow
1. Create feature branch from `main`
2. Implement changes with proper testing
3. Submit pull request with detailed description
4. Code review and approval process
5. Merge to main branch

## 📞 Contact & Support

### Development Team
- **Project Lead**: Usher Kielvin Ponce
- **Web Developer**: Mark Dwayne P. Dela Cruz
- **Backend Developer**: Dizon S. Dizon
- **Java Developer**: Kenji A. Hizon

### Support Channels
- **GitHub Issues**: Bug reports and feature requests
- **Documentation**: Comprehensive guides in `docs/` folder
- **Email**: support@cephra-project.com (placeholder)

## 🙏 Acknowledgments

### Open Source Libraries
- **Java Swing** - Desktop GUI framework
- **MySQL Connector/J** - Database connectivity
- **PHP PDO** - Database abstraction layer
- **Font Awesome** - Icon library
- **Bootstrap** - CSS framework

### Community Support
- **EV Enthusiasts** - For valuable feedback and testing
- **Open Source Community** - For tools and inspiration
- **Educational Institutions** - For providing development environment

---

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

**Made with ❤️ for the EV charging community**

**Cephra - Where Java Meets Modern Web Technology** 🚀✨

*Empowering the future of electric vehicle charging infrastructure through innovative software solutions.*
