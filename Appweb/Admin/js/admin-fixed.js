// Fixed Admin Panel JavaScript with Proper API Connections

// Global unauthorized handler: intercept 401 responses and stop auto-refresh
// This prevents the UI from repeatedly showing "Unauthorized access" notifications
// when the session expires. It shows a single message and redirects to login.
window._originalFetch = window.fetch ? window.fetch.bind(window) : null;
window.__unauthorizedHandled = false;

function handleUnauthorizedGlobal() {
    if (window.__unauthorizedHandled) return;
    window.__unauthorizedHandled = true;
    try {
        if (window.adminPanel && window.adminPanel.refreshInterval) {
            clearInterval(window.adminPanel.refreshInterval);
            window.adminPanel.refreshInterval = null;
        }
        if (window.adminPanel && typeof window.adminPanel.showError === 'function') {
            window.adminPanel.showError('Session expired. Redirecting to login...');
        } else {
            // Fallback if adminPanel not initialized yet
            try { alert('Session expired. Redirecting to login...'); } catch (_) {}
        }
    } catch (e) {
        console.error('Error handling unauthorized state:', e);
    }
    // Redirect to login after a short delay so the user can see the message
    setTimeout(() => { window.location.href = 'index.php'; }, 2200);
}

// Override global fetch to detect 401 responses. Keep original behavior otherwise.
if (window._originalFetch) {
    window.fetch = async function(...args) {
        try {
            const res = await window._originalFetch(...args);
            if (res && res.status === 401) {
                // Try to parse message for debugging but don't rely on it
                try {
                    const txt = await res.clone().text();
                    console.warn('API returned 401:', txt);
                } catch (_) {}
                handleUnauthorizedGlobal();
            }
            return res;
        } catch (err) {
            // Network or other fetch error - just rethrow
            throw err;
        }
    };
}

// Helper: safely parse JSON response and log raw response on failure.
async function safeParseResponse(response, context = '') {
    const raw = await response.text().catch(() => '');
    try {
        return JSON.parse(raw);
    } catch (err) {
        console.error(`Failed to parse JSON response${context ? ' (' + context + ')' : ''}:`, err);
        const preview = raw && raw.length > 800 ? raw.slice(0,800) + '... [truncated]' : raw;
        console.error('Server response preview:', preview);
        // Return an error-shaped object to keep callers safe
        return { success: false, error: 'Invalid server response (not JSON)', _raw: preview };
    }
}
class AdminPanel {
    constructor() {
        this.currentPanel = 'dashboard';
        this.refreshInterval = null;
        this.analyticsRange = 'week'; // default range
        this.init();
    }

    init() {
        this.setupEventListeners();
        this.updateCurrentTime();
        this.loadDashboardData();
        this.startAutoRefresh();
        this.startTimeUpdate();
        this.setupAnalyticsRangeSelector();
    }

    setupEventListeners() {
        // Sidebar navigation
        document.querySelectorAll('.sidebar-menu li').forEach(item => {
            item.addEventListener('click', (e) => {
                const panel = e.currentTarget.dataset.panel;
                this.switchPanel(panel);
            });
        });

        // Sidebar toggle for mobile
        const sidebarToggle = document.querySelector('.sidebar-toggle');
        const sidebar = document.querySelector('.sidebar');
        const mainContent = document.querySelector('.main-content');

        if (sidebarToggle && sidebar) {
            sidebarToggle.addEventListener('click', (e) => {
                e.stopPropagation();
                sidebar.classList.toggle('active');
            });
        }

        // Close sidebar when clicking on main content background
        if (mainContent && sidebar) {
            mainContent.addEventListener('click', () => {
                if (sidebar.classList.contains('active')) {
                    sidebar.classList.remove('active');
                }
            });
        }

        // Prevent sidebar clicks from closing the sidebar
        if (sidebar) {
            sidebar.addEventListener('click', (e) => {
                e.stopPropagation();
            });
        }

        // Modal close buttons
        document.querySelectorAll('.modal-close').forEach(btn => {
            btn.addEventListener('click', (e) => {
                const modal = e.target.closest('.modal');
                this.closeModal(modal.id);
            });
        });

        // Close modal on outside click
        document.querySelectorAll('.modal').forEach(modal => {
            modal.addEventListener('click', (e) => {
                if (e.target === modal) {
                    this.closeModal(modal.id);
                }
            });
        });

        // Queue filters
        const statusFilter = document.getElementById('status-filter');
        const serviceFilter = document.getElementById('service-filter');

        if (statusFilter) {
            statusFilter.addEventListener('change', () => this.filterQueue());
        }
        if (serviceFilter) {
            serviceFilter.addEventListener('change', () => this.filterQueue());
        }
    }

    switchPanel(panelName) {

        // Update sidebar active state
        document.querySelectorAll('.sidebar-menu li').forEach(item => {
            item.classList.remove('active');
        });
        document.querySelector(`[data-panel="${panelName}"]`).classList.add('active');

        // Update panel visibility
        document.querySelectorAll('.panel').forEach(panel => {
            panel.classList.remove('active');
        });
        document.getElementById(`${panelName}-panel`).classList.add('active');

        // Update page title
        const titles = {
            'dashboard': 'Dashboard',
            'queue': 'Queue Management',
            'bays': 'Charging Bays',
            'users': 'User Management',
            'analytics': 'Analytics & Reports',
            'transactions': 'Transaction History',
            'settings': 'System Settings'
        };
        document.getElementById('page-title').textContent = titles[panelName];

        this.currentPanel = panelName;

        // Load panel-specific data
        switch (panelName) {
            case 'dashboard':
                this.loadDashboardData();
                break;
            case 'queue':
                this.loadQueueData();
                break;
            case 'bays':
                this.loadBaysData();
                break;
            case 'users':
                this.loadUsersData();
                break;
            case 'staff':
                this.loadStaffData();
                break;
            case 'analytics':
                this.loadAnalyticsData();
                break;
            case 'transactions':
                this.loadTransactions();
                break;
            case 'settings':
                this.loadBusinessData();
                break;
        }
    }

    // (Staff panel removed)
    // Staff panel methods
    async loadStaffData() {
        try {
            const res = await fetch('api/admin.php?action=staff');
            const data = await res.json();
            if (data.success) {
                this.updateStaffTable(data.staff);
            } else {
                this.showError(data.error || 'Failed to load staff');
            }
        } catch (e) {
            console.error('Error loading staff:', e);
            this.showError('Failed to load staff');
        }
    }

    updateStaffTable(staff) {
        const tbody = document.getElementById('staff-tbody');
        if (!tbody) return;
        if (!staff || staff.length === 0) {
            tbody.innerHTML = '<tr><td colspan="6" class="loading"><i class="fas fa-info-circle"></i> No staff found</td></tr>';
            return;
        }
        tbody.innerHTML = staff.map(s => `
            <tr id="staff-row-${s.username}">
                <td>${s.name}</td>
                <td>${s.username}</td>
                <td>${s.email}</td>
                <td><span class="status-badge ${s.status.toLowerCase()}">${s.status}</span></td>
                <td>${this.formatDateTime(s.created_at)}</td>
                <td>
                    <button class="btn btn-warning btn-sm" onclick="if (adminPanel) adminPanel.resetStaffPassword('${s.username}')">
                        <i class="fas fa-key"></i> Reset Password
                    </button>
                    <button class="btn btn-danger btn-sm" onclick="if (adminPanel) adminPanel.deleteStaff('${s.username}')">
                        <i class="fas fa-trash"></i> Delete
                    </button>
                </td>
            </tr>
        `).join('');
    }

    async resetStaffPassword(username) {
        if (!username) return;

        // Show confirmation dialog
        const confirmed = confirm(`Reset password for staff member: ${username}\n\nA new 6-digit password will be generated.\n\nProceed with password reset?`);

        if (!confirmed) return;

        try {
            const response = await fetch('api/admin.php', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/x-www-form-urlencoded',
                },
                body: `action=reset-staff-password&username=${encodeURIComponent(username)}`
            });

            const result = await safeParseResponse(response, 'resetStaffPassword');

            if (result.success) {
                alert(`Password reset successful!\n\nNew password: ${result.new_password}\n\nPlease inform the staff member of their new password.`);
                // Refresh the staff table
                this.loadStaffData();
            } else {
                this.showError(result.error || 'Failed to reset password');
            }
        } catch (error) {
            console.error('Error resetting password:', error);
            this.showError('Failed to reset password');
        }
    }

    // staff activity removed

    showAddStaffModal() {
        const form = document.getElementById('add-staff-form');
        if (form) form.reset();
        this.showModal('add-staff-modal');
    }

    async addStaff() {
        const payload = {
            name: (document.getElementById('staff-name')?.value) || '',
            username: (document.getElementById('staff-username')?.value) || '',
            email: (document.getElementById('staff-email')?.value) || '',
            password: (document.getElementById('staff-password')?.value) || ''
        };
        if (!payload.name || !payload.username || !payload.email || !payload.password) {
            this.showError('Please fill in all fields');
            return;
        }
        try {
            const res = await fetch('api/admin.php', {
                method: 'POST',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                body: `action=add-staff&${new URLSearchParams(payload).toString()}`
            });
            const data = await safeParseResponse(res, 'addStaff');
            if (data.success) {
                this.showSuccess('Staff added');
                this.closeModal('add-staff-modal');
                this.loadStaffData();
            } else {
                this.showError(data.error || 'Failed to add staff');
            }
        } catch (e) {
            console.error('Error adding staff:', e);
            this.showError('Failed to add staff');
        }
    }


    async deleteStaff(username) {
        if (!confirm(`Delete staff "${username}"?`)) return;
        try {
            const res = await fetch('api/admin.php', {
                method: 'POST',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                body: `action=delete-staff&username=${encodeURIComponent(username)}`
            });
            const data = await safeParseResponse(res, 'deleteStaff');
            if (data.success) {
                this.showSuccess('Staff deleted');
                const row = document.getElementById(`staff-row-${username}`);
                if (row) row.remove();
            } else {
                this.showError(data.error || 'Failed to delete');
            }
        } catch (e) {
            console.error('Error deleting staff:', e);
            this.showError('Failed to delete');
        }
    }

    async loadDashboardData() {
        try {
            const response = await fetch('api/admin.php?action=dashboard');
            const data = await safeParseResponse(response, 'loadDashboardData');

            if (data.success) {
                this.updateDashboardStats(data.stats);
                this.updateRecentActivity(data.recent_activity);
            } else {
                this.showError(data.error || 'Failed to load dashboard data');
            }
        } catch (error) {
            console.error('Error loading dashboard data:', error);
            this.showError('Failed to load dashboard data - check console for details');
        }
    }

    updateDashboardStats(stats) {
        document.getElementById('total-users').textContent = stats.total_users || 0;
        document.getElementById('queue-count').textContent = stats.queue_count || 0;
        document.getElementById('active-bays').textContent = stats.active_bays || 0;
        document.getElementById('revenue-today').textContent = `₱${this.formatCurrency(stats.revenue_today || 0)}`;
    }

    updateRecentActivity(activities) {
        const container = document.getElementById('recent-activity');
        if (!activities || activities.length === 0) {
            container.innerHTML = '<div class="activity-item"><i class="fas fa-info-circle"></i><span>No recent activity</span></div>';
            return;
        }

        container.innerHTML = activities.map(activity => `
            <div class="activity-item">
                <i class="fas fa-${activity.icon || 'info-circle'}"></i>
                <span>${activity.description}</span>
                <small>${this.formatDateTime(activity.time || activity.created_at)}</small>
            </div>
        `).join('');
    }

    async loadQueueData() {
        try {
            const response = await fetch('api/admin.php?action=queue');
            const data = await safeParseResponse(response, 'loadQueueData');

            if (data.success) {
                this.updateQueueTable(data.queue);
            } else {
                this.showError(data.error || 'Failed to load queue data');
            }
        } catch (error) {
            console.error('Error loading queue data:', error);
            this.showError('Failed to load queue data - check console for details');
        }
    }

    updateQueueTable(queue) {
            const tbody = document.getElementById('queue-tbody');
            if (!queue || queue.length === 0) {
                tbody.innerHTML = '<tr><td colspan="8" class="loading"><i class="fas fa-info-circle"></i> No tickets in queue</td></tr>';
                return;
            }

            tbody.innerHTML = queue.map(ticket => `
            <tr>
                <td class="ticket-priority ${ticket.priority == 1 ? 'urgent' : 'normal'}">${ticket.ticket_id}</td>
                <td>${ticket.username}</td>
                <td>${ticket.service_type}</td>
                <td>
                    ${ticket.priority == 1 ? 
                        '<span class="priority-badge urgent"><i class="fas fa-exclamation-triangle"></i> URGENT</span>' : 
                        '<span class="priority-badge normal"><i class="fas fa-clock"></i> Normal</span>'
                    }
                </td>
                <td><span class="status-badge ${ticket.status.toLowerCase()}">${ticket.status}</span></td>
                <td><span class="status-badge ${ticket.payment_status.toLowerCase()}">${ticket.payment_status}</span></td>
                <td>${this.formatDateTime(ticket.created_at)}</td>
                <td>
                    <button class="btn btn-primary btn-sm" onclick="if (adminPanel) adminPanel.viewTicket('${ticket.ticket_id}')">
                        <i class="fas fa-eye"></i> View
                    </button>
                ${ticket.status !== 'Complete' ? `
                    <button class="btn btn-success" onclick="if (adminPanel) adminPanel.proceedTicket('${ticket.ticket_id}', '${ticket.status}')">
                        <i class="fas fa-play"></i> Process
                    </button>
                ` : `
                    <button class="btn btn-warning" onclick="if (adminPanel) adminPanel.markAsPaid('${ticket.ticket_id}')">
                        <i class="fas fa-credit-card"></i> Mark as Paid
                    </button>
                `}
                </td>
            </tr>
        `).join('');

        // Auto-remove paid tickets after a delay
        this.autoRemovePaidTickets();

        // Re-apply status filter (keep current selection in effect)
        try {
            const sel = document.getElementById('status-filter');
            if (sel) sel.dispatchEvent(new Event('change'));
        } catch(_) {}
    }

    async loadBaysData() {
        try {
            const response = await fetch('api/admin.php?action=bays');
            const data = await safeParseResponse(response, 'loadBaysData');

            if (data.success) {
                this.updateBaysGrid(data.bays);
            } else {
                this.showError(data.error || 'Failed to load bays data');
            }
        } catch (error) {
            console.error('Error loading bays data:', error);
            this.showError('Failed to load bays data - check console for details');
        }
    }

    updateBaysGrid(bays) {
        const container = document.getElementById('bays-grid');
        if (!bays || bays.length === 0) {
            container.innerHTML = '<div class="loading"><i class="fas fa-info-circle"></i> No bays available</div>';
            return;
        }

        container.innerHTML = bays.map(bay => `
            <div class="bay-card ${bay.status.toLowerCase()}">
                <div class="bay-header">
                    <span class="bay-number">${bay.bay_number}</span>
                    <span class="bay-status ${bay.status.toLowerCase()}">${bay.status}</span>
                </div>
                <div class="bay-info">
                    <div class="bay-info-item">
                        <span class="bay-info-label">Type:</span>
                        <span class="bay-info-value">${bay.bay_type}</span>
                    </div>
                    <div class="bay-info-item">
                        <span class="bay-info-label">Current User:</span>
                        <span class="bay-info-value">${bay.current_username || 'None'}</span>
                    </div>
                    <div class="bay-info-item">
                        <span class="bay-info-label">Ticket ID:</span>
                        <span class="bay-info-value">${bay.current_ticket_id || 'None'}</span>
                    </div>
                    <div class="bay-info-item">
                        <span class="bay-info-label">Start Time:</span>
                        <span class="bay-info-value">${bay.start_time ? this.formatDateTime(bay.start_time) : 'N/A'}</span>
                    </div>
                </div>
                <div class="bay-actions" style="margin-top: 15px;">
                    ${bay.status === 'Available' ? `
                        <button class="btn btn-warning btn-sm" onclick="if (adminPanel) adminPanel.setBayMaintenance('${bay.bay_number}')">
                            <i class="fas fa-tools"></i> Maintenance
                        </button>
                    ` : bay.status === 'Maintenance' ? `
                        <button class="btn btn-success btn-sm" onclick="if (adminPanel) adminPanel.setBayAvailable('${bay.bay_number}')">
                            <i class="fas fa-check"></i> Available
                        </button>
                    ` : ''}
                </div>
            </div>
        `).join('');
    }

    async loadUsersData() {
        try {
            const response = await fetch('api/admin.php?action=users');
            const data = await safeParseResponse(response, 'loadUsersData');

            if (data.success) {
                this.updateUsersTable(data.users);
            } else {
                this.showError(data.error || 'Failed to load users data');
            }
        } catch (error) {
            console.error('Error loading users data:', error);
            this.showError('Failed to load users data - check console for details');
        }
    }

    updateUsersTable(users) {
        const tbody = document.getElementById('users-tbody');
        if (!users || users.length === 0) {
            tbody.innerHTML = '<tr><td colspan="6" class="loading"><i class="fas fa-info-circle"></i> No users found</td></tr>';
            return;
        }

        tbody.innerHTML = users.map(user => `
            <tr id="user-row-${user.username}">
                <td>${user.username}</td>
                <td>${user.firstname}</td>
                <td>${user.lastname}</td>
                <td>${user.email}</td>
                <td>${this.formatDateTime(user.created_at)}</td>
                <td>
                    <button class="btn btn-danger btn-sm" onclick="if (adminPanel) adminPanel.deleteUser('${user.username}')">
                        <i class="fas fa-trash"></i> Delete
                    </button>
                </td>
            </tr>
        `).join('');
    }

    async deleteUser(username) {
        if (!confirm(`Are you sure you want to delete user "${username}"? This action cannot be undone.`)) {
            return;
        }

        try {
            const response = await fetch('api/admin.php', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/x-www-form-urlencoded',
                },
                body: `action=delete-user&username=${username}`
            });
            const data = await safeParseResponse(res, 'deleteUser');

            if (data.success) {
                this.showSuccess('User deleted successfully');
                // Remove the user row from the table
                const userRow = document.getElementById(`user-row-${username}`);
                if (userRow) {
                    userRow.remove();
                }
            } else {
                this.showError(data.message || 'Failed to delete user');
            }
        } catch (error) {
            console.error('Error deleting user:', error);
            this.showError('Failed to delete user');
        }
    }

    setupAnalyticsRangeSelector() {
        const container = document.getElementById('analytics-range-selector');
        if (!container) return;

        container.innerHTML = `
            <button id="range-day" class="range-btn">Day</button>
            <button id="range-week" class="range-btn active">Week</button>
            <button id="range-month" class="range-btn">Month</button>
        `;

        container.querySelectorAll('.range-btn').forEach(btn => {
            btn.addEventListener('click', (e) => {
                container.querySelectorAll('.range-btn').forEach(b => b.classList.remove('active'));
                e.target.classList.add('active');
                this.analyticsRange = e.target.id.replace('range-', '');
                this.loadAnalyticsData();
            });
        });
    }

    async loadAnalyticsData() {
        try {
            console.log('Loading analytics data for range:', this.analyticsRange);
            const response = await fetch(`api/admin.php?action=analytics&range=${this.analyticsRange}`);
            const data = await safeParseResponse(response, 'processPayment');

            console.log('Analytics API response:', data);

            // Update chart titles based on selected range
            this.updateChartTitles();

            if (data.success) {
                if (data.revenue_data && data.revenue_data.length > 0) {
                    console.log('Rendering revenue chart with', data.revenue_data.length, 'data points');
                    const filledRevenueData = this.fillMissingDates(data.revenue_data, 'revenue');
                    this.renderRevenueChart(filledRevenueData);
                } else {
                    console.log('No revenue data available');
                }
                if (data.service_data && data.service_data.length > 0) {
                    console.log('Rendering service chart with', data.service_data.length, 'data points');
                    const filledServiceData = this.fillMissingServiceDates(data.service_data);
                    this.renderServiceChart(filledServiceData);
                } else {
                    console.log('No service data available');
                }
            } else {
                console.error('Analytics API returned error:', data);
                this.showError('Failed to load analytics data');
            }
        } catch (error) {
            console.error('Error loading analytics data:', error);
            this.showError('Failed to load analytics data - check console for details');
        }
    }

    updateChartTitles() {
        // Update chart card titles based on selected range
        const revenueTitle = document.querySelector('#analytics-panel .analytics-card:first-child h3');
        const serviceTitle = document.querySelector('#analytics-panel .analytics-card:last-child h3');
        
        let rangeText = '';
        switch (this.analyticsRange) {
            case 'day':
                rangeText = ' - Last 7 Days';
                break;
            case 'week':
                rangeText = ' - Last 2 Weeks';
                break;
            case 'month':
                rangeText = ' - Last 30 Days';
                break;
        }
        
        if (revenueTitle) {
            revenueTitle.textContent = 'Daily Revenue' + rangeText;
        }
        if (serviceTitle) {
            serviceTitle.textContent = 'Service Usage' + rangeText;
        }
    }

    // Format date for chart display (remove year, show as M/D)
    formatDateForChart(dateString) {
        const date = new Date(dateString);
        const month = date.getMonth() + 1; // getMonth() returns 0-11
        const day = date.getDate();
        return `${month}/${day}`;
    }

    // Fill missing dates with zero values to ensure all days are shown
    fillMissingDates(data, valueKey) {
        if (!data || data.length === 0) return data;

        // Determine date range based on analytics range
        const today = new Date();
        let startDate = new Date();
        
        switch (this.analyticsRange) {
            case 'day':
                startDate.setDate(today.getDate() - 7); // Show last 7 days
                break;
            case 'week':
                startDate.setDate(today.getDate() - 14); // Show last 2 weeks
                break;
            case 'month':
                startDate.setDate(today.getDate() - 30); // Show last 30 days
                break;
            default:
                startDate.setDate(today.getDate() - 7);
        }

        // Create a map of existing data
        const dataMap = new Map();
        data.forEach(item => {
            dataMap.set(item.date, parseFloat(item[valueKey]) || 0);
        });

        // Fill missing dates with zero values
        const filledData = [];
        const currentDate = new Date(startDate);
        
        while (currentDate <= today) {
            const dateString = currentDate.toISOString().split('T')[0]; // YYYY-MM-DD format
            const value = dataMap.get(dateString) || 0;
            
            filledData.push({
                date: dateString,
                [valueKey]: value
            });
            
            currentDate.setDate(currentDate.getDate() + 1);
        }

        return filledData;
    }

    // Fill missing dates for service data with separate normal and fast charging counts
    fillMissingServiceDates(data) {
        if (!data || data.length === 0) return data;

        // Determine date range based on analytics range
        const today = new Date();
        let startDate = new Date();
        
        switch (this.analyticsRange) {
            case 'day':
                startDate.setDate(today.getDate() - 7); // Show last 7 days
                break;
            case 'week':
                startDate.setDate(today.getDate() - 14); // Show last 2 weeks
                break;
            case 'month':
                startDate.setDate(today.getDate() - 30); // Show last 30 days
                break;
            default:
                startDate.setDate(today.getDate() - 7);
        }

        // Create maps for existing data
        const normalDataMap = new Map();
        const fastDataMap = new Map();
        
        data.forEach(item => {
            normalDataMap.set(item.date, parseInt(item.normal_count) || 0);
            fastDataMap.set(item.date, parseInt(item.fast_count) || 0);
        });

        // Fill missing dates with zero values
        const filledData = [];
        const currentDate = new Date(startDate);
        
        while (currentDate <= today) {
            const dateString = currentDate.toISOString().split('T')[0]; // YYYY-MM-DD format
            const normalCount = normalDataMap.get(dateString) || 0;
            const fastCount = fastDataMap.get(dateString) || 0;
            
            filledData.push({
                date: dateString,
                normal_count: normalCount,
                fast_count: fastCount,
                service_count: normalCount + fastCount // Total for backward compatibility
            });
            
            currentDate.setDate(currentDate.getDate() + 1);
        }

        return filledData;
    }

    renderRevenueChart(revenueData) {
        const ctx = document.getElementById('revenue-chart').getContext('2d');

        // Prepare data for chart with proper date formatting
        const labels = revenueData.map(item => this.formatDateForChart(item.date));
        const dataPoints = revenueData.map(item => parseFloat(item.revenue));

        if (this.revenueChart) {
            this.revenueChart.destroy();
        }

        this.revenueChart = new Chart(ctx, {
            type: 'line',
            data: {
                labels: labels,
                datasets: [{
                    label: 'Revenue (₱)',
                    data: dataPoints,
                    borderColor: 'rgba(75, 192, 192, 1)',
                    backgroundColor: 'rgba(75, 192, 192, 0.2)',
                    fill: true,
                    tension: 0.3,
                    pointRadius: 4,
                    pointHoverRadius: 6,
                }]
            },
            options: {
                responsive: true,
                scales: {
                    x: {
                        title: {
                            display: true,
                            text: this.analyticsRange === 'day' ? 'Last 7 Days' : 
                                  this.analyticsRange === 'week' ? 'Last 2 Weeks' : 'Date'
                        }
                    },
                    y: {
                        beginAtZero: true,
                        title: {
                            display: true,
                            text: 'Revenue (₱)'
                        }
                    }
                },
                plugins: {
                    legend: {
                        display: true,
                        position: 'top',
                    },
                    tooltip: {
                        enabled: true,
                        mode: 'nearest',
                        intersect: false,
                    }
                }
            }
        });
    }

    renderServiceChart(serviceData) {
        const ctx = document.getElementById('service-chart').getContext('2d');

        // Prepare data for chart with proper date formatting
        const labels = serviceData.map(item => this.formatDateForChart(item.date));
        const normalData = serviceData.map(item => parseInt(item.normal_count || 0));
        const fastData = serviceData.map(item => parseInt(item.fast_count || 0));

        if (this.serviceChart) {
            this.serviceChart.destroy();
        }

        this.serviceChart = new Chart(ctx, {
            type: 'line',
            data: {
                labels: labels,
                datasets: [
                    {
                        label: 'Normal Charging',
                        data: normalData,
                        borderColor: 'rgba(54, 162, 235, 1)',
                        backgroundColor: 'rgba(54, 162, 235, 0.2)',
                        fill: false,
                        tension: 0.3,
                        pointRadius: 4,
                        pointHoverRadius: 6,
                        borderWidth: 2
                    },
                    {
                        label: 'Fast Charging',
                        data: fastData,
                        borderColor: 'rgba(255, 99, 132, 1)',
                        backgroundColor: 'rgba(255, 99, 132, 0.2)',
                        fill: false,
                        tension: 0.3,
                        pointRadius: 4,
                        pointHoverRadius: 6,
                        borderWidth: 2
                    }
                ]
            },
            options: {
                responsive: true,
                interaction: {
                    mode: 'index',
                    intersect: false,
                },
                scales: {
                    x: {
                        title: {
                            display: true,
                            text: this.analyticsRange === 'day' ? 'Last 7 Days' : 
                                  this.analyticsRange === 'week' ? 'Last 2 Weeks' : 'Date'
                        }
                    },
                    y: {
                        beginAtZero: true,
                        title: {
                            display: true,
                            text: 'Number of Services'
                        }
                    }
                },
                plugins: {
                    legend: {
                        display: true,
                        position: 'top',
                    },
                    tooltip: {
                        enabled: true,
                        mode: 'index',
                        intersect: false,
                        callbacks: {
                            title: function(context) {
                                return context[0].label;
                            },
                            label: function(context) {
                                const datasetLabel = context.dataset.label;
                                const value = context.parsed.y;
                                return `${datasetLabel}: ${value} services`;
                            }
                        }
                    }
                }
            }
        });
    }

    async loadBusinessData() {
        try {
            const response = await fetch('api/admin.php?action=business-settings');
            const data = await safeParseResponse(response, 'markAsPaid');

            if (data.success) {
                document.getElementById('min-fee').value = data.settings.min_fee || 0;
                document.getElementById('kwh-per-peso').value = data.settings.kwh_per_peso || 0;
            }
        } catch (error) {
            console.error('Error loading business data:', error);
        }
    }

    async viewTicket(ticketId) {
        try {
            const response = await fetch(`api/admin.php?action=ticket-details&ticket_id=${ticketId}`);
            const data = await safeParseResponse(response, 'setBayMaintenance');

            if (data.success) {
                this.showTicketModal(data.ticket);
            } else {
                this.showError('Failed to load ticket details');
            }
        } catch (error) {
            console.error('Error loading ticket details:', error);
            this.showError('Failed to load ticket details');
        }
    }

    showTicketModal(ticket) {
        const modalBody = document.getElementById('ticket-modal-body');
        modalBody.innerHTML = `
            <div class="ticket-details">
                <div class="detail-row">
                    <strong>Ticket ID:</strong> ${ticket.ticket_id}
                </div>
                <div class="detail-row">
                    <strong>Username:</strong> ${ticket.username}
                </div>
                <div class="detail-row">
                    <strong>Service Type:</strong> ${ticket.service_type}
                </div>
                <div class="detail-row">
                    <strong>Status:</strong> <span class="status-badge ${ticket.status.toLowerCase()}">${ticket.status}</span>
                </div>
                <div class="detail-row">
                    <strong>Payment Status:</strong> <span class="status-badge ${ticket.payment_status.toLowerCase()}">${ticket.payment_status}</span>
                </div>
                <div class="detail-row">
                    <strong>Battery Level:</strong> ${ticket.initial_battery_level}%
                </div>
                <div class="detail-row">
                    <strong>Created:</strong> ${this.formatDateTime(ticket.created_at)}
                </div>
            </div>
        `;
        this.showModal('ticket-modal');
    }

    async processTicket(ticketId) {
        if (!confirm('Are you sure you want to process this ticket?')) {
            return;
        }

        try {
            const response = await fetch('api/admin.php', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/x-www-form-urlencoded',
                },
                body: `action=process-ticket&ticket_id=${ticketId}`
            });
            const data = await safeParseResponse(response, 'setBayAvailable');

            if (data.success) {
                this.showSuccess('Ticket processed successfully');
                this.loadQueueData();
            } else {
                this.showError(data.message || 'Failed to process ticket');
            }
        } catch (error) {
            console.error('Error processing ticket:', error);
            this.showError('Failed to process ticket');
        }
    }

    async processPayment(ticketId) {
        try {
            const response = await fetch('api/admin.php', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/x-www-form-urlencoded',
                },
                body: `action=mark-payment-paid&ticket_id=${ticketId}`
            });
            const data = await safeParseResponse(response, 'loadBusinessData');

            if (data.success) {
                this.showSuccess('Payment processed successfully');
                this.loadQueueData();
            } else {
                this.showError(data.message || 'Failed to process payment');
            }
        } catch (error) {
            console.error('Error processing payment:', error);
            this.showError('Failed to process payment');
        }
    }

    async markAsPaid(ticketId) {
        try {
            const response = await fetch('api/admin.php', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/x-www-form-urlencoded',
                },
                body: `action=mark-payment-paid&ticket_id=${ticketId}`
            });
            const data = await safeParseResponse(response, 'viewTicket');

            if (data.success) {
                this.showSuccess('Ticket marked as paid successfully');
                this.loadQueueData();
            } else {
                this.showError(data.message || 'Failed to mark ticket as paid');
            }
        } catch (error) {
            console.error('Error marking ticket as paid:', error);
            this.showError('Failed to mark ticket as paid');
        }
    }

    async proceedTicket(ticketId, currentStatus) {
        try {
            let action = '';

            switch (currentStatus.toLowerCase()) {
                case 'pending':
                    action = 'progress-to-waiting';
                    break;
                case 'waiting':
                    action = 'progress-to-charging';
                    break;
                case 'charging':
                    action = 'progress-to-complete';
                    break;
                case 'complete':
                    // For complete tickets, show message that PayPop will be displayed to customer
                    this.showInfo('Charging completed! PayPop will be displayed to the customer for payment.');
                    this.loadQueueData();
                    return;
                default:
                    this.showError('Invalid ticket status for progression');
                    return;
            }

            const response = await fetch('api/admin.php', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/x-www-form-urlencoded',
                },
                body: `action=${action}&ticket_id=${ticketId}`
            });
            
            if (!response.ok) {
                const text = await response.text().catch(() => '');
                console.error(`HTTP ${response.status}: ${response.statusText}`, text);
                throw new Error(`HTTP ${response.status}: ${response.statusText}`);
            }

            // Some server errors return HTML (PHP warnings/notices) which cause
            // response.json() to throw a SyntaxError. Read text and try to parse
            // JSON manually so we can log the raw response when parsing fails.
            const raw = await response.text();
            let data;
            try {
                data = JSON.parse(raw);
            } catch (parseErr) {
                console.error('Invalid JSON response while progressing ticket:', parseErr);
                // Log a truncated preview of the server response for debugging
                const preview = raw.length > 800 ? raw.slice(0, 800) + '... [truncated]' : raw;
                console.error('Server response preview:', preview);
                this.showError('Server returned an unexpected response while progressing the ticket. Check console for details.');
                return;
            }

            if (data.success) {
                this.showSuccess(`Ticket ${ticketId} progressed successfully`);
                this.loadQueueData();
                this.loadBaysData(); // Refresh bays in case assignment happened
            } else {
                console.error('API Error:', data);
                this.showError(data.error || data.message || 'Failed to progress ticket');
            }
        } catch (error) {
            console.error('Error progressing ticket:', error);
            this.showError('Failed to progress ticket');
        }
    }

    autoRemovePaidTickets() {
        // Auto-remove paid tickets after a delay to allow for visual feedback
        setTimeout(() => {
            const tbody = document.getElementById('queue-tbody');
            if (!tbody) return;

            const rows = tbody.querySelectorAll('tr');
            rows.forEach(row => {
                const cells = row.querySelectorAll('td');
                // Payment column is the 6th column (index 5) in the table layout
                if (cells.length >= 6) {
                    const paymentStatus = cells[5].textContent.trim().toLowerCase();
                    if (paymentStatus === 'paid') {
                        // Attempt server-side delete to permanently remove the ticket
                        const ticketId = (cells[0].textContent || '').trim();
                        if (!ticketId) { row.remove(); return; }

                        fetch('api/admin.php', {
                            method: 'POST',
                            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                            body: `action=delete-ticket&ticket_id=${encodeURIComponent(ticketId)}`
                        })
                        .then(res => res.json())
                        .then(data => {
                            if (data && data.success) {
                                console.log(`Deleted ticket ${ticketId} from server`);
                                row.remove();
                                this.updateQueueCounters();
                            } else {
                                console.warn('Failed to delete ticket on server, removing from UI anyway', data);
                                row.remove();
                                this.updateQueueCounters();
                            }
                        })
                        .catch(err => {
                            console.error('Network error deleting ticket:', err);
                            // Remove from UI to keep consistent, but log error
                            row.remove();
                            this.updateQueueCounters();
                        });
                    }
                }
            });

            // Update counters after removal
            this.updateQueueCounters();
        }, 2000); // 2 second delay
    }

    updateQueueCounters() {
        // Update the queue counters in the UI
        const tbody = document.getElementById('queue-tbody');
        if (!tbody) return;

        let waiting = 0;
        let charging = 0;
        let paid = 0;

        const rows = tbody.querySelectorAll('tr');
        rows.forEach(row => {
            const cells = row.querySelectorAll('td');
            if (cells.length >= 5) {
                const status = cells[3].textContent.trim().toLowerCase();
                const payment = cells[4].textContent.trim().toLowerCase();

                if (status === 'waiting') waiting++;
                else if (status === 'charging') charging++;
                if (payment === 'paid') paid++;
            }
        });

        // Update counter elements if they exist
        const waitingEl = document.getElementById('waiting-count');
        const chargingEl = document.getElementById('charging-count');
        const paidEl = document.getElementById('paid-count');

        if (waitingEl) waitingEl.textContent = waiting;
        if (chargingEl) chargingEl.textContent = charging;
        if (paidEl) paidEl.textContent = paid;
    }

    async setBayMaintenance(bayNumber) {
        if (!confirm(`Are you sure you want to set ${bayNumber} to maintenance mode?`)) {
            return;
        }

        try {
            const response = await fetch('api/admin.php', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/x-www-form-urlencoded',
                },
                body: `action=set-bay-maintenance&bay_number=${bayNumber}`
            });
            const data = await safeParseResponse(response, 'addUser');

            if (data.success) {
                this.showSuccess('Bay set to maintenance mode');
                this.loadBaysData();
            } else {
                this.showError(data.message || 'Failed to set bay to maintenance');
            }
        } catch (error) {
            console.error('Error setting bay to maintenance:', error);
            this.showError('Failed to set bay to maintenance');
        }
    }

    async setBayAvailable(bayNumber) {
        if (!confirm(`Are you sure you want to set Bay ${bayNumber} to available?`)) {
            return;
        }

        try {
            const response = await fetch('api/admin.php', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/x-www-form-urlencoded',
                },
                body: `action=set-bay-available&bay_number=${bayNumber}`
            });
            const data = await safeParseResponse(response, 'saveBusinessSettings');

            if (data.success) {
                this.showSuccess('Bay set to available');
                this.loadBaysData();
            } else {
                this.showError(data.message || 'Failed to set bay to available');
            }
        } catch (error) {
            console.error('Error setting bay to available:', error);
            this.showError('Failed to set bay to available');
        }
    }

    showAddUserModal() {
        document.getElementById('add-user-form').reset();
        this.showModal('add-user-modal');
    }

    async addUser() {
        const userData = {
            username: document.getElementById('new-username').value,
            firstname: document.getElementById('new-firstname').value,
            lastname: document.getElementById('new-lastname').value,
            email: document.getElementById('new-email').value,
            password: document.getElementById('new-password').value
        };

        if (!userData.username || !userData.firstname || !userData.lastname || !userData.email || !userData.password) {
            this.showError('Please fill in all fields');
            return;
        }

        try {
            const response = await fetch('api/admin.php', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/x-www-form-urlencoded',
                },
                body: `action=add-user&${new URLSearchParams(userData).toString()}`
            });
            const data = await safeParseResponse(response, 'loadTransactions');

            if (data.success) {
                this.showSuccess('User added successfully');
                this.closeModal('add-user-modal');
                this.loadUsersData();
            } else {
                this.showError(data.message || 'Failed to add user');
            }
        } catch (error) {
            console.error('Error adding user:', error);
            this.showError('Failed to add user');
        }
    }

    async saveBusinessSettings() {
        const minFee = parseFloat(document.getElementById('min-fee').value);
        const kwhPerPeso = parseFloat(document.getElementById('kwh-per-peso').value);

        if (isNaN(minFee) || isNaN(kwhPerPeso) || minFee < 0 || kwhPerPeso <= 0) {
            this.showError('Please enter valid business values (min fee >= 0, kWh per peso > 0)');
            return;
        }

        try {
            const response = await fetch('api/admin.php', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/x-www-form-urlencoded',
                },
                body: `action=save-business-settings&min_fee=${minFee}&kwh_per_peso=${kwhPerPeso}`
            });
            const data = await safeParseResponse(response, 'filterTransactions');

            if (data.success) {
                this.showSuccess('Business settings updated successfully');
            } else {
                this.showError(data.message || 'Failed to update settings');
            }
        } catch (error) {
            console.error('Error updating business settings:', error);
            this.showError('Failed to update business settings');
        }
    }

    filterQueue() {
        const statusFilter = document.getElementById('status-filter').value;
        const serviceFilter = document.getElementById('service-filter').value;
        
        // Simple client-side filtering
        const rows = document.querySelectorAll('#queue-tbody tr');
        rows.forEach(row => {
            const status = row.cells[3].textContent.trim();
            const service = row.cells[2].textContent.trim();
            
            let showRow = true;
            
            if (statusFilter && status !== statusFilter) {
                showRow = false;
            }
            
            if (serviceFilter && service !== serviceFilter) {
                showRow = false;
            }
            
            row.style.display = showRow ? '' : 'none';
        });
    }

    showModal(modalId) {
        document.getElementById(modalId).classList.add('active');
    }

    closeModal(modalId) {
        document.getElementById(modalId).classList.remove('active');
    }

    // Transaction History Methods
    async loadTransactions() {
        try {
            const response = await fetch('api/admin.php?action=transactions');
            const data = await safeParseResponse(response, 'loadTransactions');
            
            if (data.success) {
                // Store transaction data for export
                this.transactionData = data.transactions;
                this.displayTransactions(data.transactions);
                if (data.warnings && data.warnings.length > 0) {
                    console.warn('Transaction loading warnings:', data.warnings);
                }
                if (data.count !== undefined) {
                    console.log(`Loaded ${data.count} transactions`);
                }
            } else {
                console.error('Failed to load transactions:', data.error);
                this.showError('Failed to load transaction data: ' + (data.error || 'Unknown error'));
            }
        } catch (error) {
            console.error('Error loading transactions:', error);
            this.showError('Error loading transaction data: ' + error.message);
        }
    }

    displayTransactions(transactions) {
        const tbody = document.getElementById('transactions-tbody');
        if (!tbody) return;

        if (transactions.length === 0) {
            tbody.innerHTML = `
                <tr>
                    <td colspan="8" class="no-data">
                        <i class="fas fa-inbox"></i>
                        No transactions found
                    </td>
                </tr>
            `;
            return;
        }

        tbody.innerHTML = transactions.map(transaction => {
            // Safely handle date parsing
            let formattedDate = 'N/A';
            try {
                if (transaction.transaction_date) {
                    const date = new Date(transaction.transaction_date);
                    if (!isNaN(date.getTime())) {
                        formattedDate = date.toLocaleDateString() + ' ' + 
                            date.toLocaleTimeString([], {hour: '2-digit', minute:'2-digit'});
                    }
                }
            } catch (e) {
                console.warn('Date parsing error:', e);
            }
            
            // Safely handle numeric values
            let amount = '0.00';
            let energy = '0.00';
            
            try {
                if (transaction.total_amount !== null && 
                    transaction.total_amount !== undefined && 
                    transaction.total_amount !== '' &&
                    !isNaN(parseFloat(transaction.total_amount))) {
                    amount = parseFloat(transaction.total_amount).toFixed(2);
                }
            } catch (e) {
                console.warn('Amount parsing error:', e);
            }
            
            try {
                if (transaction.energy_kwh !== null && 
                    transaction.energy_kwh !== undefined && 
                    transaction.energy_kwh !== '' &&
                    !isNaN(parseFloat(transaction.energy_kwh))) {
                    energy = parseFloat(transaction.energy_kwh).toFixed(2);
                }
            } catch (e) {
                console.warn('Energy parsing error:', e);
            }
            
            return `
                <tr>
                    <td>${transaction.ticket_id || 'N/A'}</td>
                    <td>${transaction.username || 'N/A'}</td>
                    <td>${energy} kWh</td>
                    <td>₱${amount}</td>
                    <td>${formattedDate}</td>
                    <td>${transaction.reference_number || 'N/A'}</td>
                </tr>
            `;
        }).join('');
    }

    async filterTransactions() {
        const typeFilter = document.getElementById('transaction-type-filter')?.value || '';
        const statusFilter = document.getElementById('transaction-status-filter')?.value || '';
        const dateFrom = document.getElementById('transaction-date-from')?.value || '';
        const dateTo = document.getElementById('transaction-date-to')?.value || '';

        try {
            let url = 'api/admin.php?action=transactions';
            const params = new URLSearchParams();
            
            if (typeFilter) params.append('type', typeFilter);
            if (statusFilter) params.append('status', statusFilter);
            if (dateFrom) params.append('date_from', dateFrom);
            if (dateTo) params.append('date_to', dateTo);
            
            if (params.toString()) {
                url += '&' + params.toString();
            }

            const response = await fetch(url);
            const data = await safeParseResponse(response, 'filterTransactions');
            
            if (data.success) {
                // Store filtered transaction data for export
                this.transactionData = data.transactions;
                this.displayTransactions(data.transactions);
            } else {
                console.error('Failed to filter transactions:', data.error);
                this.showError('Failed to filter transaction data');
            }
        } catch (error) {
            console.error('Error filtering transactions:', error);
            this.showError('Error filtering transaction data');
        }
    }

    exportTransactions() {
        if (!this.transactionData || this.transactionData.length === 0) {
            this.showError('No transactions to export');
            return;
        }

        // Create proper CSV format for Excel with shorter headers
        let csv = 'Ticket,User,kWh,Amount,Date,Reference\n';
        
        this.transactionData.forEach(transaction => {
            // Safely handle date parsing - use compact 12-hour format to prevent ####
            let formattedDate = 'N/A';
            try {
                if (transaction.transaction_date) {
                    const date = new Date(transaction.transaction_date);
                    if (!isNaN(date.getTime())) {
                        // Use compact 12-hour format: MM/DD/YY H:MM AM/PM (fits in narrow columns)
                        const month = (date.getMonth() + 1).toString().padStart(2, '0');
                        const day = date.getDate().toString().padStart(2, '0');
                        const year = date.getFullYear().toString().slice(-2);
                        
                        let hours = date.getHours();
                        const minutes = date.getMinutes().toString().padStart(2, '0');
                        const ampm = hours >= 12 ? 'PM' : 'AM';
                        
                        // Convert to 12-hour format
                        hours = hours % 12;
                        hours = hours ? hours : 12; // 0 should be 12
                        
                        // Add tab character to force Excel to treat as text
                        formattedDate = `\t${month}/${day}/${year} ${hours}:${minutes} ${ampm}`;
                    }
                }
            } catch (e) {
                console.warn('Date parsing error:', e);
            }
            
            // Safely handle numeric values
            let amount = '0.00';
            let energy = '0.00';
            
            try {
                if (transaction.total_amount !== null && 
                    transaction.total_amount !== undefined && 
                    transaction.total_amount !== '' &&
                    !isNaN(parseFloat(transaction.total_amount))) {
                    amount = parseFloat(transaction.total_amount).toFixed(2);
                }
            } catch (e) {
                console.warn('Amount parsing error:', e);
            }
            
            try {
                if (transaction.energy_kwh !== null && 
                    transaction.energy_kwh !== undefined && 
                    transaction.energy_kwh !== '' &&
                    !isNaN(parseFloat(transaction.energy_kwh))) {
                    energy = parseFloat(transaction.energy_kwh).toFixed(2);
                }
            } catch (e) {
                console.warn('Energy parsing error:', e);
            }
            
            // Clean text fields and escape quotes for CSV
            const ticketId = (transaction.ticket_id || 'N/A').toString().replace(/"/g, '""');
            const username = (transaction.username || 'N/A').toString().replace(/"/g, '""');
            const reference = (transaction.reference_number || 'N/A').toString().replace(/"/g, '""');
            
            // Create CSV row - format date as text to prevent Excel conversion
            const rowData = [
                `"${ticketId}"`,
                `"${username}"`,
                energy, // Don't quote numbers for Excel
                amount, // Don't quote numbers for Excel
                `"${formattedDate}"`, // Keep quoted to force text format
                `"${reference}"`
            ];
            
            csv += rowData.join(',') + '\n';
        });

        // Create HTML table directly from transaction data
        let html = `
        <html xmlns:o="urn:schemas-microsoft-com:office:office" 
              xmlns:x="urn:schemas-microsoft-com:office:excel" 
              xmlns="http://www.w3.org/TR/REC-html40">
        <head>
            <meta charset="utf-8">
            <meta name="ExcelCreated" content="true">
            <style>
                table { border-collapse: collapse; width: 100%; }
                th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }
                th { background-color: #f2f2f2; font-weight: bold; }
                .date-column { width: 20em; min-width: 20em; }
            </style>
        </head>
        <body>
            <table>
                <thead>
                    <tr>
                        <th>Ticket</th>
                        <th>User</th>
                        <th>kWh</th>
                        <th>Amount</th>
                        <th class="date-column">Date</th>
                        <th>Reference</th>
                    </tr>
                </thead>
                <tbody>
        `;

        // Add data rows directly from transaction data
        this.transactionData.forEach(transaction => {
            // Safely handle date parsing - use compact 12-hour format to prevent ####
            let formattedDate = 'N/A';
            try {
                if (transaction.transaction_date) {
                    const date = new Date(transaction.transaction_date);
                    if (!isNaN(date.getTime())) {
                        // Use compact 12-hour format: MM/DD/YY H:MM AM/PM (fits in narrow columns)
                        const month = (date.getMonth() + 1).toString().padStart(2, '0');
                        const day = date.getDate().toString().padStart(2, '0');
                        const year = date.getFullYear().toString().slice(-2);
                        
                        let hours = date.getHours();
                        const minutes = date.getMinutes().toString().padStart(2, '0');
                        const ampm = hours >= 12 ? 'PM' : 'AM';
                        
                        // Convert to 12-hour format
                        hours = hours % 12;
                        hours = hours ? hours : 12; // 0 should be 12
                        
                        // Add tab character to force Excel to treat as text
                        formattedDate = `${month}/${day}/${year} ${hours}:${minutes} ${ampm}`;
                    }
                }
            } catch (e) {
                console.warn('Date parsing error:', e);
            }
            
            // Safely handle numeric values
            let amount = '0.00';
            let energy = '0.00';
            
            try {
                if (transaction.total_amount !== null && 
                    transaction.total_amount !== undefined && 
                    transaction.total_amount !== '' &&
                    !isNaN(parseFloat(transaction.total_amount))) {
                    amount = parseFloat(transaction.total_amount).toFixed(2);
                }
            } catch (e) {
                console.warn('Amount parsing error:', e);
            }
            
            try {
                if (transaction.energy_kwh !== null && 
                    transaction.energy_kwh !== undefined && 
                    transaction.energy_kwh !== '' &&
                    !isNaN(parseFloat(transaction.energy_kwh))) {
                    energy = parseFloat(transaction.energy_kwh).toFixed(2);
                }
            } catch (e) {
                console.warn('Energy parsing error:', e);
            }

            html += `
                <tr>
                    <td>${(transaction.ticket_id || 'N/A').toString().replace(/</g, '&lt;').replace(/>/g, '&gt;')}</td>
                    <td>${(transaction.username || 'N/A').toString().replace(/</g, '&lt;').replace(/>/g, '&gt;')}</td>
                    <td>${energy}</td>
                    <td>${amount}</td>
                    <td class="date-column">${formattedDate}</td>
                    <td>${(transaction.reference_number || 'N/A').toString().replace(/</g, '&lt;').replace(/>/g, '&gt;')}</td>
                </tr>
            `;
        });

        html += `
                </tbody>
            </table>
        </body>
        </html>
        `;

        // Add BOM for proper UTF-8 encoding in Excel
        const BOM = '\uFEFF';
        const csvWithBOM = BOM + html;
        
        // Download as Excel-compatible HTML file with column formatting
        const blob = new Blob([csvWithBOM], { 
            type: 'application/vnd.ms-excel' 
        });
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `transactions_${new Date().toISOString().split('T')[0]}.xls`;
        
        this.showSuccess('Transactions exported successfully! Date column is set to width 20 for proper display.');
        
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        window.URL.revokeObjectURL(url);
    }

    showSuccess(message) {
        this.showNotification(message, 'success');
    }

    showError(message) {
        this.showNotification(message, 'error');
    }

    showNotification(message, type) {
        // Create notification element
        const notification = document.createElement('div');
        notification.className = `notification notification-${type}`;
        notification.innerHTML = `
            <i class="fas fa-${type === 'success' ? 'check-circle' : 'exclamation-triangle'}"></i>
            <span>${message}</span>
        `;
        
        // Add styles
        notification.style.cssText = `
            position: fixed;
            top: 20px;
            right: 20px;
            background: ${type === 'success' ? '#d4edda' : '#f8d7da'};
            color: ${type === 'success' ? '#155724' : '#721c24'};
            padding: 15px 20px;
            border-radius: 8px;
            box-shadow: 0 2px 10px rgba(0, 0, 0, 0.1);
            z-index: 3000;
            display: flex;
            align-items: center;
            gap: 10px;
            animation: slideIn 0.3s ease;
        `;
        
        document.body.appendChild(notification);
        
        // Remove after 3 seconds
        setTimeout(() => {
            notification.style.animation = 'slideOut 0.3s ease';
            setTimeout(() => {
                if (notification.parentNode) {
                    notification.parentNode.removeChild(notification);
                }
            }, 300);
        }, 3000);
    }

    updateCurrentTime() {
        const timeElement = document.getElementById('current-time');
        if (timeElement) {
            const now = new Date();
            let hours = now.getHours();
            const minutes = now.getMinutes();
            const ampm = hours >= 12 ? 'PM' : 'AM';
            
            hours = hours % 12;
            hours = hours ? hours : 12;
            
            const formattedTime = `${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')} ${ampm}`;
            timeElement.textContent = formattedTime;
        }
    }

    startTimeUpdate() {
        setInterval(() => {
            this.updateCurrentTime();
        }, 60000);
    }

    startAutoRefresh() {
        // SSE: connect to the event stream for instant push updates.
        // Falls back to polling if SSE is unavailable.
        if (typeof EventSource !== 'undefined') {
            this._connectSSE();
        } else {
            this._startPolling();
        }
    }

    _connectSSE() {
        const lastId = sessionStorage.getItem('sse_last_id') || 0;
        this._sse = new EventSource(`api/events.php?lastEventId=${lastId}`);

        this._sse.addEventListener('queue_update', () => {
            if (this.currentPanel === 'dashboard') this.loadDashboardData();
            if (this.currentPanel === 'queue')     this.loadQueueData();
        });
        this._sse.addEventListener('bay_update', () => {
            if (this.currentPanel === 'dashboard') this.loadDashboardData();
            if (this.currentPanel === 'bays')      this.loadBaysData();
        });
        this._sse.addEventListener('charging_complete', () => {
            this.loadDashboardData();
            if (this.currentPanel === 'queue') this.loadQueueData();
        });
        this._sse.addEventListener('reconnect', () => {
            this._sse.close();
            setTimeout(() => this._connectSSE(), 500);
        });
        this._sse.onmessage = (e) => {
            // store last event id for reconnect continuity
            if (e.lastEventId) sessionStorage.setItem('sse_last_id', e.lastEventId);
        };
        this._sse.onerror = () => {
            // SSE dropped — fall back to polling until reconnect
            if (!this.refreshInterval) this._startPolling();
        };
        this._sse.onopen = () => {
            // SSE reconnected — stop polling fallback
            if (this.refreshInterval) {
                clearInterval(this.refreshInterval);
                this.refreshInterval = null;
            }
        };
    }

    _startPolling() {
        if (this.refreshInterval) return;
        this.refreshInterval = setInterval(() => {
            switch (this.currentPanel) {
                case 'dashboard': this.loadDashboardData(); break;
                case 'queue':     this.loadQueueData();     break;
                case 'bays':      this.loadBaysData();      break;
                case 'users':     this.loadUsersData();     break;
            }
        }, 10000); // 10s fallback — SSE handles the fast path
    }

    formatCurrency(amount) {
        return Math.round(amount).toLocaleString();
    }

    formatDateTime(dateString) {
        const date = new Date(dateString);
        const month = (date.getMonth() + 1).toString().padStart(2, '0');
        const day = date.getDate().toString().padStart(2, '0');
        const year = date.getFullYear();

        let hours = date.getHours();
        const minutes = date.getMinutes();
        const ampm = hours >= 12 ? 'PM' : 'AM';

        hours = hours % 12;
        hours = hours ? hours : 12;

        return `${month}/${day}/${year}, ${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')} ${ampm}`;
    }
}

// Open Monitor Web in new tab
function openMonitorWeb() {
    const monitorUrl = '../Monitor/';
    window.open(monitorUrl, '_blank', 'noopener,noreferrer');
}

// Global functions for button onclick handlers
function showLoadingSpinner() {
    const overlay = document.getElementById('loading-overlay');
    if (overlay) {
        overlay.style.display = 'flex';
        overlay.style.position = 'fixed';
        overlay.style.top = '0';
        overlay.style.left = '0';
        overlay.style.width = '100%';
        overlay.style.height = '100%';
        overlay.style.background = 'rgba(0, 0, 0, 0.5)';
        overlay.style.zIndex = '9999';
        overlay.style.alignItems = 'center';
        overlay.style.justifyContent = 'center';
        console.log('Loading overlay shown with inline styles and refreshing text');
        return overlay;
    } else {
        console.error('Loading overlay element not found');
        return null;
    }
}

function hideLoadingSpinner() {
    const overlay = document.getElementById('loading-overlay');
    if (overlay) {
        overlay.style.display = 'none';
        console.log('Loading overlay hidden');
    }
}

function refreshQueue() {
    console.log('Refresh Queue clicked');
    const overlay = showLoadingSpinner();
    if (!overlay) return;

    if (adminPanel) {
        console.log('Calling adminPanel.loadQueueData()');
        adminPanel.loadQueueData()
            .then(() => {
                console.log('loadQueueData completed successfully');
            })
            .catch((error) => {
                console.error('loadQueueData failed:', error);
            })
            .finally(() => {
                console.log('Hiding overlay after loadQueueData');
                setTimeout(() => hideLoadingSpinner(), 1300); // Extended delay to 1.3 seconds
            });
    } else {
        console.log('adminPanel not found, using fallback timeout');
        setTimeout(() => hideLoadingSpinner(), 1500);
    }
}

function refreshBays() {
    console.log('Refresh Bays clicked');
    const overlay = showLoadingSpinner();
    if (!overlay) return;

    if (adminPanel) {
        console.log('Calling adminPanel.loadBaysData()');
        adminPanel.loadBaysData()
            .then(() => {
                console.log('loadBaysData completed successfully');
            })
            .catch((error) => {
                console.error('loadBaysData failed:', error);
            })
            .finally(() => {
                console.log('Hiding overlay after loadBaysData');
                setTimeout(() => hideLoadingSpinner(), 1300); // Extended delay to 1.3 seconds
            });
    } else {
        console.log('adminPanel not found, using fallback timeout');
        setTimeout(() => hideLoadingSpinner(), 1500);
    }
}

function refreshUsers() {
    console.log('Refresh Users clicked');
    const overlay = showLoadingSpinner();
    if (!overlay) return;

    if (adminPanel) {
        console.log('Calling adminPanel.loadUsersData()');
        adminPanel.loadUsersData()
            .then(() => {
                console.log('loadUsersData completed successfully');
            })
            .catch((error) => {
                console.error('loadUsersData failed:', error);
            })
            .finally(() => {
                console.log('Hiding overlay after loadUsersData');
                setTimeout(() => hideLoadingSpinner(), 1300); // Extended delay to 1.3 seconds
            });
    } else {
        console.log('adminPanel not found, using fallback timeout');
        setTimeout(() => hideLoadingSpinner(), 1500);
    }
}

function autoAssignWaitingTickets() {
    console.log('Auto-Assign Waiting Tickets clicked');
    
    // Show confirmation dialog
    if (!confirm('This will automatically assign all waiting tickets to available bays. Continue?')) {
        return;
    }
    
    const overlay = showLoadingSpinner();
    if (!overlay) return;

    fetch('api/admin.php?action=auto-assign-waiting-tickets', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/x-www-form-urlencoded',
        }
    })
        .then(async (response) => {
            const data = await safeParseResponse(response, 'autoAssignWaitingTickets');
            return data;
        })
        .then(data => {
            hideLoadingSpinner(overlay);
            if (data.success) {
                const count = data.assigned_count || 0;
                adminPanel.showSuccess(`Successfully assigned ${count} waiting tickets to available bays`);
                adminPanel.loadQueueData();
                adminPanel.loadBaysData(); // Refresh bays to show updated status
            } else {
                console.error('API Error:', data);
                adminPanel.showError(data.error || data.message || 'Failed to auto-assign waiting tickets');
            }
        })
        .catch(error => {
            hideLoadingSpinner(overlay);
            console.error('Network Error:', error);
            adminPanel.showError('Network error occurred while auto-assigning tickets');
        });
}

function processNextTicket() {
    if (adminPanel) {
        console.log('Starting processNextTicket...');
        // Call the new API to progress the next ticket
        fetch('api/admin.php?action=progress-next-ticket', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded',
            }
        })
        .then(response => {
            console.log('Response status:', response.status);
            console.log('Response ok:', response.ok);
            return response.text().then(text => {
                console.log('Raw response:', text);
                try {
                    return JSON.parse(text);
                } catch (e) {
                    console.error('JSON parse error:', e);
                    throw new Error('Invalid JSON response: ' + text);
                }
            });
        })
        .then(data => {
            console.log('Parsed data:', data);
            if (data.success) {
                adminPanel.showSuccess(`Ticket ${data.ticket_id} assigned to Bay ${data.bay_number} (${data.new_status})`);
                adminPanel.loadQueueData();
                adminPanel.loadBaysData(); // Refresh bays to show updated status
            } else {
                adminPanel.showError(data.message || 'Failed to progress ticket');
            }
        })
        .catch(error => {
            console.error('Error progressing ticket:', error);
            adminPanel.showError('Failed to progress ticket: ' + error.message);
        });
    }
}

function showAddUserModal() {
    if (adminPanel) {
        adminPanel.showAddUserModal();
    }
}

function showAddStaffModal() {
    if (adminPanel) {
        adminPanel.showAddStaffModal();
    }
}

function addStaff() {
    if (adminPanel) {
        adminPanel.addStaff();
    }
}

function addUser() {
    if (adminPanel) {
        adminPanel.addUser();
    }
}

function saveBusinessSettings() {
    if (adminPanel) {
        adminPanel.saveBusinessSettings();
    }
}

function closeModal(modalId) {
    if (adminPanel) {
        adminPanel.closeModal(modalId);
    }
}

function processTicket(ticketId) {
    if (adminPanel) {
        adminPanel.processTicket(ticketId);
    }
}

// Transaction History Functions
function refreshTransactions() {
    if (adminPanel) {
        adminPanel.loadTransactions();
    }
}

function filterTransactions() {
    if (adminPanel) {
        adminPanel.filterTransactions();
    }
}

function exportTransactions() {
    if (adminPanel) {
        adminPanel.exportTransactions();
    }
}


// Initialize admin panel when DOM is loaded
let adminPanel;
document.addEventListener('DOMContentLoaded', function() {
    adminPanel = new AdminPanel();
});

// Add CSS animations
const style = document.createElement('style');
style.textContent = `
    @keyframes slideIn {
        from {
            transform: translateX(100%);
            opacity: 0;
        }
        to {
            transform: translateX(0);
            opacity: 1;
        }
    }
    
    @keyframes slideOut {
        from {
            transform: translateX(0);
            opacity: 1;
        }
        to {
            transform: translateX(100%);
            opacity: 0;
        }
    }
`;
document.head.appendChild(style);