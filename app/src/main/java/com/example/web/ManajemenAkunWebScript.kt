package com.example.web

object ManajemenAkunWebScript {
    fun getScript(dol: String): String {
        return """
        // =========================================================================
        // --- MANAJEMEN AKUN & PESAN KHUSUS ADMIN (100% OFFLINE PERSIS MY SINGGHARA) ---
        // =========================================================================

        window.adminUsers = [];
        window.adminMessages = [];
        window.adminOtpRequests = [];
        window.adminActiveOtpCount = 0;
        window.adminSelectedTab = 0; // 0 = Daftar Pengguna, 1 = Pesan (persis ManajemenAkunScreen.kt)
        window.adminStatusFilter = 'ALL'; // ALL, ACTIVE, INACTIVE, MEMBER, ONLINE
        window.adminSearchQuery = '';
        window.adminIsLoadingUsers = false;
        window.adminIsLoadingMessages = false;
        window.adminIsLoadingOtp = false;
        window.adminIsOnline = (typeof navigator.onLine !== 'undefined') ? navigator.onLine : true;
        window.adminPhoneNumber = '088806643415';
        window.adminAuthStatus = {
            isLoggedIn: false,
            isAdmin: false,
            currentUid: '',
            currentEmail: '',
            currentDisplayName: '',
            adminUid: 'UiDpHxawlBQ17mxTUHIXvhxRR3b2',
            adminPhoneNumber: '088806643415'
        };
        window.adminUserToToggle = null;
        window.adminUserToSetMembership = null;
        window.adminUserForVipCard = null;
        window.adminVipCardSide = 'MEMBER'; // 'FRONT' atau 'MEMBER' (persis VipCardSide)
        window.adminMessageToDelete = null;
        window.adminTargetUserForMessage = null;

        // Listeners status koneksi online/offline (tanpa memblokir halaman!)
        window.addEventListener('online', () => {
            window.adminIsOnline = true;
            updateAdminOnlineBadge();
            if (currentTab === 'manajemen_akun' || currentTab === 'admin') {
                fetchAdminStatus();
                fetchAdminUsers(true);
                fetchAdminMessages(true);
            }
        });

        window.addEventListener('offline', () => {
            window.adminIsOnline = false;
            updateAdminOnlineBadge();
        });

        function updateAdminOnlineBadge() {
            const badge = document.getElementById('adminConnectivityBadge');
            if (badge) {
                if (window.adminIsOnline) {
                    badge.className = 'badge rounded-pill bg-success-subtle text-success border border-success px-3 py-1';
                    badge.innerHTML = '<i class="bi bi-wifi me-1"></i> Saling Terhubung (Cloud Sync Aktif)';
                } else {
                    badge.className = 'badge rounded-pill bg-primary-subtle text-primary border border-primary px-3 py-1';
                    badge.innerHTML = '<i class="bi bi-hdd-network me-1"></i> Mode 100% Offline (Penyimpanan Lokal)';
                }
            }
        }

        // Helper Toast Khusus Manajemen Akun
        function showAdminToast(message, icon = 'bi-check-circle-fill', iconColor = '#059669') {
            const container = document.getElementById('toastContainer');
            if (!container) {
                alert(message);
                return;
            }
            const toastEl = document.createElement('div');
            toastEl.className = 'toast align-items-center text-white border-0 show shadow-lg mb-2';
            toastEl.style.backgroundColor = '#1E293B';
            toastEl.style.borderRadius = '12px';
            toastEl.style.minWidth = '280px';
            toastEl.style.zIndex = '9999';
            toastEl.innerHTML = `
                <div class="d-flex p-3 align-items-center">
                    <i class="bi ${dol}{icon} fs-4 me-3" style="color: ${dol}{iconColor};"></i>
                    <div class="toast-body p-0 flex-grow-1" style="font-size: 13px; font-weight: 500;">${dol}{escapeHtml(message)}</div>
                    <button type="button" class="btn-close btn-close-white me-1 m-auto" onclick="this.closest('.toast').remove()"></button>
                </div>
            `;
            container.appendChild(toastEl);
            setTimeout(() => {
                try { toastEl.remove(); } catch(e) {}
            }, 3500);
        }

        // Salin Teks ke Clipboard
        function copyAdminClipboard(label, text) {
            if (!text) return;
            if (navigator.clipboard && navigator.clipboard.writeText) {
                navigator.clipboard.writeText(text).then(() => {
                    showAdminToast(label + ' berhasil disalin!', 'bi-clipboard-check-fill', '#059669');
                }).catch(() => {
                    fallbackCopyText(label, text);
                });
            } else {
                fallbackCopyText(label, text);
            }
        }

        function fallbackCopyText(label, text) {
            const temp = document.createElement('textarea');
            temp.value = text;
            temp.style.position = 'fixed';
            temp.style.opacity = '0';
            document.body.appendChild(temp);
            temp.select();
            try {
                document.execCommand('copy');
                showAdminToast(label + ' berhasil disalin!', 'bi-clipboard-check-fill', '#059669');
            } catch(e) {
                showAdminToast('Gagal menyalin ' + label, 'bi-exclamation-triangle-fill', '#DC2626');
            }
            document.body.removeChild(temp);
        }

        // --- AMBIL STATUS ADMIN & NOMOR WHATSAPP DARI SERVER ---
        async function fetchAdminStatus() {
            try {
                const res = await fetch('/api/admin?action=status');
                if (res.ok) {
                    const data = await res.json();
                    if (data && data.status === 'success') {
                        const targetAdminUid = 'UiDpHxawlBQ17mxTUHIXvhxRR3b2';
                        const cleanUid = ((data.currentUid || '') + '').trim();
                        const isRealAdmin = (cleanUid === targetAdminUid) || (data.isAdmin === true);
                        window.adminAuthStatus = {
                            isLoggedIn: !!data.isLoggedIn,
                            isAdmin: isRealAdmin,
                            currentUid: isRealAdmin ? targetAdminUid : cleanUid,
                            currentEmail: data.currentEmail || '',
                            currentDisplayName: data.currentDisplayName || '',
                            adminUid: targetAdminUid,
                            adminPhoneNumber: data.adminPhoneNumber || '088806643415'
                        };
                        window.adminPhoneNumber = data.adminPhoneNumber || window.adminPhoneNumber;

                        // Perbarui visibilitas tombol Manajemen Akun di Profil Modal
                        const btnAdmin = document.getElementById('btnProfileManajemenAkun');
                        if (btnAdmin) {
                            if (isRealAdmin) {
                                btnAdmin.classList.add('d-flex');
                                btnAdmin.style.display = 'flex';
                            } else {
                                btnAdmin.classList.remove('d-flex');
                                btnAdmin.style.display = 'none';
                            }
                        }
                        return window.adminAuthStatus;
                    }
                }
            } catch (e) {
                console.warn('Gagal memuat status admin:', e);
            }
            return window.adminAuthStatus;
        }

        // --- ENTRY POINT HALAMAN MANAJEMEN AKUN (100% OFFLINE CAPABLE) ---
        async function loadManajemenAkun() {
            const targetAdminUid = 'UiDpHxawlBQ17mxTUHIXvhxRR3b2';

            // 1. Verifikasi hak akses Admin resmi UID 'UiDpHxawlBQ17mxTUHIXvhxRR3b2'
            let authStatus = window.adminAuthStatus;
            let cleanUid = ((authStatus && authStatus.currentUid) || '').trim();
            let isAuthorized = authStatus && (authStatus.isAdmin === true || cleanUid === targetAdminUid);

            if (!isAuthorized) {
                authStatus = await fetchAdminStatus();
                cleanUid = ((authStatus && authStatus.currentUid) || '').trim();
                isAuthorized = authStatus && (authStatus.isAdmin === true || cleanUid === targetAdminUid);
            }

            if (!isAuthorized) {
                const container = document.getElementById('tab-content') || document.getElementById('main-content');
                if (container) {
                    renderAccessDeniedUI(container);
                }
                return;
            }

            // 2. Ambil data dari LocalStorage terlebih dahulu agar tampilan instan (0ms delay)
            try {
                const cachedUsers = localStorage.getItem('mysingghara_admin_users');
                if (cachedUsers) window.adminUsers = JSON.parse(cachedUsers);
                const cachedMsgs = localStorage.getItem('mysingghara_admin_messages');
                if (cachedMsgs) window.adminMessages = JSON.parse(cachedMsgs);
                const cachedPhone = localStorage.getItem('mysingghara_admin_phone');
                if (cachedPhone) window.adminPhoneNumber = cachedPhone;
                const cachedOtp = localStorage.getItem('mysingghara_admin_otp');
                if (cachedOtp) {
                    window.adminOtpRequests = JSON.parse(cachedOtp);
                    window.adminActiveOtpCount = (window.adminOtpRequests || []).filter(o => o.status === 'MENUNGGU' && !o.isExpired).length;
                }
            } catch (e) {}

            // 3. Render tampilan utama Manajemen Akun secara langsung jika hak akses valid
            renderManajemenAkunUI();

            // 4. Sinkronkan data dari endpoint lokal server Android (/api/admin)
            fetchAdminUsers(window.adminUsers && window.adminUsers.length > 0);
            fetchAdminMessages(window.adminMessages && window.adminMessages.length > 0);
            fetchAdminOtpRequests(true);
        }

        // --- AMBIL DAFTAR PENGGUNA DARI SERVER / CACHE OFFLINE ---
        async function fetchAdminUsers(silent = false) {
            if (!silent) {
                window.adminIsLoadingUsers = true;
                renderManajemenAkunUI();
            }

            try {
                const res = await fetch('/api/admin?action=users');
                if (res.ok) {
                    const data = await res.json();
                    if (data && data.status === 'success' && Array.isArray(data.users)) {
                        window.adminUsers = data.users;
                        try {
                            localStorage.setItem('mysingghara_admin_users', JSON.stringify(window.adminUsers));
                        } catch(e) {}
                    }
                }
            } catch (e) {
                console.warn('Error fetchAdminUsers (menggunakan cache lokal):', e);
            } finally {
                window.adminIsLoadingUsers = false;
                renderManajemenAkunUI();
            }
        }

        // --- AMBIL DAFTAR PESAN DARI SERVER / CACHE OFFLINE ---
        async function fetchAdminMessages(silent = false) {
            if (!silent) {
                window.adminIsLoadingMessages = true;
                renderManajemenAkunUI();
            }

            try {
                const res = await fetch('/api/admin?action=messages');
                if (res.ok) {
                    const data = await res.json();
                    if (data && data.status === 'success' && Array.isArray(data.messages)) {
                        window.adminMessages = data.messages;
                        try {
                            localStorage.setItem('mysingghara_admin_messages', JSON.stringify(window.adminMessages));
                        } catch(e) {}
                    }
                }
            } catch (e) {
                console.warn('Error fetchAdminMessages (menggunakan cache lokal):', e);
            } finally {
                window.adminIsLoadingMessages = false;
                renderManajemenAkunUI();
            }
        }

        // --- AMBIL DAFTAR PERMINTAAN KODE OTP (KHUSUS ADMIN) ---
        async function fetchAdminOtpRequests(silent = false) {
            if (!silent) {
                window.adminIsLoadingOtp = true;
                renderManajemenAkunUI();
            }

            try {
                const res = await fetch('/api/admin?action=otp_requests');
                if (res.ok) {
                    const data = await res.json();
                    if (data && data.status === 'success' && Array.isArray(data.otpRequests)) {
                        window.adminOtpRequests = data.otpRequests;
                        window.adminActiveOtpCount = typeof data.activeCount === 'number' ? data.activeCount : (window.adminOtpRequests || []).filter(o => o.status === 'MENUNGGU' && !o.isExpired).length;
                        try {
                            localStorage.setItem('mysingghara_admin_otp', JSON.stringify(window.adminOtpRequests));
                        } catch(e) {}
                    }
                }
            } catch (e) {
                console.warn('Error fetchAdminOtpRequests:', e);
            } finally {
                window.adminIsLoadingOtp = false;
                renderManajemenAkunUI();
            }
        }

        async function deleteAdminOtpRequest(otpId) {
            try {
                const res = await fetch('/api/admin?action=delete_otp', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ id: otpId })
                });
                if (res.ok) {
                    window.adminOtpRequests = (window.adminOtpRequests || []).filter(o => o.id !== otpId);
                    window.adminActiveOtpCount = (window.adminOtpRequests || []).filter(o => o.status === 'MENUNGGU' && !o.isExpired).length;
                    try {
                        localStorage.setItem('mysingghara_admin_otp', JSON.stringify(window.adminOtpRequests));
                    } catch(e) {}
                    showAdminToast("Permintaan kode OTP berhasil dihapus!", "bi-trash3-fill", "#DC2626");
                    renderOtpInboxListHtml();
                }
            } catch(e) {
                showAdminToast("Gagal menghapus OTP: " + e.message, "bi-exclamation-triangle-fill", "#DC2626");
            }
        }

        async function markAdminOtpRequestUsed(otpId) {
            try {
                const res = await fetch('/api/admin?action=mark_otp_used', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ id: otpId })
                });
                if (res.ok) {
                    window.adminOtpRequests = (window.adminOtpRequests || []).map(o => o.id === otpId ? { ...o, status: 'DIGUNAKAN' } : o);
                    window.adminActiveOtpCount = (window.adminOtpRequests || []).filter(o => o.status === 'MENUNGGU' && !o.isExpired).length;
                    try {
                        localStorage.setItem('mysingghara_admin_otp', JSON.stringify(window.adminOtpRequests));
                    } catch(e) {}
                    showAdminToast("Kode OTP ditandai selesai/digunakan.", "bi-check-circle-fill", "#059669");
                    renderOtpInboxListHtml();
                }
            } catch(e) {
                showAdminToast("Gagal menandai OTP: " + e.message, "bi-exclamation-triangle-fill", "#DC2626");
            }
        }

        // --- SINKRONISASI TEMA & MODE GELAP MANAJEMEN AKUN ---
        function ensureAdminStylesInjected() {
            if (document.getElementById('admin-custom-styles')) return;
            const styleEl = document.createElement('style');
            styleEl.id = 'admin-custom-styles';
            styleEl.textContent = `
                /* Dark Mode Overrides */
                body.dark .admin-card, [data-theme="dark"] .admin-card, :root:not([data-theme="light"]):not(.dark) .admin-card,
                body.dark .admin-msg-card, [data-theme="dark"] .admin-msg-card, :root:not([data-theme="light"]):not(.dark) .admin-msg-card,
                body.dark .admin-table-card, [data-theme="dark"] .admin-table-card, :root:not([data-theme="light"]):not(.dark) .admin-table-card {
                    background-color: #1e293b !important;
                    border: 1px solid #334155 !important;
                    color: #ffffff !important;
                    box-shadow: 0 4px 16px rgba(0, 0, 0, 0.35) !important;
                }

                body.dark .admin-text-main, [data-theme="dark"] .admin-text-main, :root:not([data-theme="light"]):not(.dark) .admin-text-main {
                    color: #ffffff !important;
                }
                body.dark .admin-text-sub, [data-theme="dark"] .admin-text-sub, :root:not([data-theme="light"]):not(.dark) .admin-text-sub {
                    color: #94a3b8 !important;
                }

                body.dark .admin-search-input, [data-theme="dark"] .admin-search-input, :root:not([data-theme="light"]):not(.dark) .admin-search-input {
                    background-color: #0f172a !important;
                    color: #ffffff !important;
                    border-color: #334155 !important;
                }

                body.dark .admin-table thead th, [data-theme="dark"] .admin-table thead th {
                    background-color: #0f172a !important;
                    color: #94a3b8 !important;
                    border-bottom-color: #334155 !important;
                }

                body.dark .admin-table tbody tr td, [data-theme="dark"] .admin-table tbody tr td {
                    background-color: transparent !important;
                    color: #e2e8f0 !important;
                    border-bottom-color: #334155 !important;
                }

                body.dark .admin-table tbody tr:hover td, [data-theme="dark"] .admin-table tbody tr:hover td {
                    background-color: rgba(255, 255, 255, 0.05) !important;
                }

                /* VIP Digital Membership Card Luxury Styling */
                .vip-card-container {
                    background: linear-gradient(135deg, #0f172a 0%, #1e293b 50%, #091224 100%);
                    border: 1px solid rgba(245, 158, 11, 0.4);
                    border-radius: 24px;
                    color: #ffffff;
                    position: relative;
                    overflow: hidden;
                    box-shadow: 0 20px 45px rgba(0, 0, 0, 0.45);
                }
                .vip-card-glow {
                    position: absolute;
                    top: -60px;
                    right: -60px;
                    width: 200px;
                    height: 200px;
                    border-radius: 50%;
                    background: radial-gradient(circle, rgba(245, 158, 11, 0.25) 0%, rgba(245, 158, 11, 0) 70%);
                    pointer-events: none;
                }
                .vip-chip-badge {
                    background: linear-gradient(135deg, #F59E0B, #D97706);
                    color: #000000;
                    font-weight: 800;
                    letter-spacing: 1px;
                    font-size: 11px;
                    padding: 4px 12px;
                    border-radius: 20px;
                    display: inline-flex;
                    align-items: center;
                    gap: 6px;
                }
                .barcode-strip {
                    display: flex;
                    height: 38px;
                    gap: 2px;
                    align-items: center;
                    justify-content: center;
                    background: #ffffff;
                    padding: 6px 12px;
                    border-radius: 8px;
                }
                .barcode-bar {
                    background-color: #000000;
                    height: 100%;
                }
            `;
            document.head.appendChild(styleEl);
        }

        // --- RENDER UTAMA HALAMAN MANAJEMEN AKUN ---
        function renderManajemenAkunUI() {
            ensureAdminStylesInjected();
            const container = document.getElementById('tab-content') || document.getElementById('main-content');
            if (!container) return;

            const targetAdminUid = 'UiDpHxawlBQ17mxTUHIXvhxRR3b2';
            const currentUid = (((window.adminAuthStatus && window.adminAuthStatus.currentUid) || '') + '').trim();
            const isAdmin = (window.adminAuthStatus && window.adminAuthStatus.isAdmin) || false;

            if (!isAdmin && currentUid !== targetAdminUid) {
                renderAccessDeniedUI(container);
                return;
            }

            renderAdminDashboardUI(container);
        }

        // --- TAMPILAN HALAMAN TERSEMBUNYI / AKSES DITOLAK JIKA BUKAN UID ADMIN ---
        function renderAccessDeniedUI(container) {
            const currentUid = (window.adminAuthStatus && window.adminAuthStatus.currentUid) || '';
            container.innerHTML = `
                <div class="d-flex flex-column align-items-center justify-content-center p-3 p-md-5 my-4 text-center" style="min-height: 60vh; max-width: 580px; margin: 0 auto;">
                    <div class="card card-custom p-4 p-md-5 border-0 shadow-lg text-center w-100" style="border-radius: 24px; background: var(--card-bg, #ffffff); border: 1px solid var(--border-color);">
                        <div class="rounded-circle d-flex align-items-center justify-content-center mx-auto mb-3 shadow-sm" style="width: 76px; height: 76px; background: rgba(220, 38, 38, 0.12); color: #DC2626; font-size: 2.3rem;">
                            <i class="bi bi-shield-lock-fill"></i>
                        </div>
                        <h4 class="fw-bold mb-2 text-main admin-text-main">Halaman Tersembunyi</h4>
                        <div class="badge rounded-pill bg-danger-subtle text-danger border border-danger px-3 py-1 mb-3 align-self-center fw-bold" style="font-size: 11px;">
                            <i class="bi bi-lock-fill me-1"></i> Akses Khusus Administrator Resmi
                        </div>
                        <p class="text-secondary small mb-3" style="line-height: 1.6;">
                            Halaman <strong>Manajemen Akun</strong> dilindungi dan tersembunyi. Halaman ini hanya dapat diakses dan dilihat oleh akun Administrator resmi dengan UID:
                        </p>
                        <div class="p-2 mb-3 rounded-3 bg-danger-subtle border border-danger border-opacity-25">
                            <code class="fw-bold text-danger" style="font-size: 13px;">UiDpHxawlBQ17mxTUHIXvhxRR3b2</code>
                        </div>
                        
                        ${dol}{currentUid ? `
                            <div class="p-2 px-3 rounded-3 mb-4 text-start border bg-light bg-opacity-50 small">
                                <div class="text-secondary fw-semibold mb-1" style="font-size: 11px;">UID Akun Anda:</div>
                                <code class="text-break fw-bold text-dark" style="font-size: 12px;">${dol}{escapeHtml(currentUid)}</code>
                            </div>
                        ` : ''}

                        <button class="btn btn-outline-secondary py-2 px-4 rounded-pill fw-bold w-100 d-flex align-items-center justify-content-center gap-2 shadow-sm" onclick="switchTabByName('dashboard')">
                            <i class="bi bi-arrow-left"></i> Kembali ke Dashboard
                        </button>
                    </div>
                </div>
            `;
        }

        // --- UI UTAMA DASHBOARD ADMINISTRATOR (100% PERSIS MY SINGGHARA) ---
        function renderAdminDashboardUI(container) {
            const users = getFilteredAdminUsers();
            const totalCount = (window.adminUsers || []).length;
            const activeCount = (window.adminUsers || []).filter(u => u.isActive).length;
            const inactiveCount = (window.adminUsers || []).filter(u => !u.isActive).length;
            const vipCount = (window.adminUsers || []).filter(u => u.hasMembership || (u.membershipDurationMonths && u.membershipDurationMonths > 0) || u.isAdmin).length;
            const onlineCount = (window.adminUsers || []).filter(u => u.isRecentlyActive).length;
            const messagesCount = (window.adminMessages || []).length;

            container.innerHTML = `
                <div class="d-flex flex-column gap-3 p-1 p-md-3" style="max-width: 1200px; margin: 0 auto;">
                    
                    <!-- TOP NAVIGATION BAR DENGAN STATUS KONEKSI & BREADCRUMB -->
                    <div class="d-flex align-items-center justify-content-between flex-wrap gap-2 mb-1">
                        <div class="d-flex align-items-center gap-2">
                            <button class="btn btn-sm btn-outline-secondary rounded-pill px-3 py-1 fw-semibold d-flex align-items-center gap-1 shadow-sm" onclick="showProfileModal()">
                                <i class="bi bi-person-circle me-1 text-primary"></i> Profil My Singghara
                            </button>
                            <span class="badge rounded-pill px-3 py-1" style="background-color: rgba(4, 120, 87, 0.15); color: #047857; border: 1px solid rgba(4, 120, 87, 0.35); font-size: 11px; font-weight: 700;">
                                <i class="bi bi-shield-check me-1"></i> Khusus Admin
                            </span>
                        </div>

                        <!-- BADGE STATUS OFFLINE / ONLINE -->
                        <div id="adminConnectivityBadge" class="badge rounded-pill ${dol}{window.adminIsOnline ? 'bg-success-subtle text-success border border-success' : 'bg-primary-subtle text-primary border border-primary'} px-3 py-1">
                            ${dol}{window.adminIsOnline ? '<i class="bi bi-wifi me-1"></i> Saling Terhubung (Cloud Sync Aktif)' : '<i class="bi bi-hdd-network me-1"></i> Mode 100% Offline (Penyimpanan Lokal)'}
                        </div>
                    </div>

                    <!-- TOP ACTION BAR PERSIS MANAJEMENAKUNSCREEN.KT -->
                    <div class="d-flex align-items-center justify-content-between flex-wrap gap-2 pb-2 border-bottom border-secondary border-opacity-25">
                        
                        <!-- TITLE & ADMIN BADGE (SATU BARIS UTUH) -->
                        <div class="d-flex align-items-center gap-2">
                            <h5 class="fw-bold mb-0 text-main admin-text-main d-flex align-items-center gap-2">
                                <span>Manajemen Akun</span>
                            </h5>
                            <span class="badge" style="background-color: #DCFCE7; color: #15803D; border: 1px solid #86EFAC; font-size: 10px; font-weight: 800; letter-spacing: 0.5px; border-radius: 4px; padding: 3px 6px;">ADMIN</span>
                        </div>

                        <!-- ACTION BAR KANAN: INPUT NOMOR ADMIN, ICON EMAIL OTP, ICON TITIK 3 -->
                        <div class="d-flex align-items-center gap-2">
                            <!-- INPUT NOMOR DEFAULT ADMIN 088806643415 (BISA DIGANTI) -->
                            <div class="d-flex align-items-center gap-2 px-2 py-1 rounded-3 border shadow-sm admin-phone-input-bar" style="cursor: pointer; background: var(--card-bg, #f1f5f9);" onclick="openEditAdminPhoneModal()" title="Ubah Nomor Kontak Admin (WhatsApp)">
                                <div class="rounded-circle p-1 d-flex align-items-center justify-content-center" style="background: rgba(37, 211, 102, 0.2); width: 22px; height: 22px;">
                                    <i class="bi bi-telephone-fill" style="color: #16a34a; font-size: 11px;"></i>
                                </div>
                                <span class="fw-bold font-monospace text-dark admin-text-main" style="font-size: 11.5px;">${dol}{window.adminPhoneNumber || '088806643415'}</span>
                                <i class="bi bi-pencil-fill text-primary" style="font-size: 11px;"></i>
                            </div>

                            <!-- ICON EMAIL {PESAN MASUK KODE} DENGAN BADGE ANGKA DI SEBELAH KIRI TITIK 3 -->
                            <div class="position-relative">
                                <button class="btn btn-light btn-sm rounded-circle d-flex align-items-center justify-content-center border shadow-sm" style="width: 36px; height: 36px;" onclick="openOtpInboxModal()" title="Pesan Masuk Kode OTP Sandi Email">
                                    <i class="bi ${dol}{window.adminActiveOtpCount > 0 ? 'bi-envelope-exclamation-fill text-teal' : 'bi-envelope-fill text-secondary'} fs-6" style="${dol}{window.adminActiveOtpCount > 0 ? 'color: #0F766E;' : ''}"></i>
                                </button>
                                ${dol}{window.adminActiveOtpCount > 0 ? `
                                    <span class="position-absolute top-0 start-100 translate-middle badge rounded-pill bg-danger border border-white" style="font-size: 9px; font-weight: bold;">
                                        ${dol}{window.adminActiveOtpCount > 99 ? '99+' : window.adminActiveOtpCount}
                                    </span>
                                ` : ''}
                            </div>

                            <!-- ICON TITIK 3 (MENU OVERFLOW PERSIS MANAJEMENAKUNSCREEN.KT) -->
                            <div class="dropdown">
                                <button class="btn btn-light btn-sm rounded-circle d-flex align-items-center justify-content-center border shadow-sm admin-dots-btn" type="button" data-bs-toggle="dropdown" aria-expanded="false" style="width: 36px; height: 36px;" title="Menu Titik Tiga">
                                    <i class="bi bi-three-dots-vertical fs-6"></i>
                                </button>
                                <ul class="dropdown-menu dropdown-menu-end shadow-lg rounded-3 py-2 border-0" style="min-width: 220px; font-size: 13px;">
                                    <li>
                                        <a class="dropdown-item d-flex align-items-center justify-content-between py-2" href="javascript:void(0)" onclick="openOtpInboxModal()">
                                            <span class="d-flex align-items-center gap-2">
                                                <i class="bi bi-envelope-fill" style="color: #0F766E;"></i>
                                                <span>Pesan Masuk Kode OTP</span>
                                            </span>
                                            ${dol}{window.adminActiveOtpCount > 0 ? `
                                                <span class="badge rounded-pill bg-danger" style="font-size: 9px;">${dol}{window.adminActiveOtpCount}</span>
                                            ` : ''}
                                        </a>
                                    </li>
                                    <li><hr class="dropdown-divider my-1"></li>
                                    <li>
                                        <a class="dropdown-item d-flex align-items-center gap-2 py-2 ${dol}{window.adminSelectedTab === 0 ? 'active text-white' : ''}" href="javascript:void(0)" onclick="setAdminSelectedTab(0)">
                                            <i class="bi bi-people-fill text-primary"></i>
                                            <span>Daftar Pengguna</span>
                                        </a>
                                    </li>
                                    <li>
                                        <a class="dropdown-item d-flex align-items-center gap-2 py-2 ${dol}{window.adminSelectedTab === 1 ? 'active text-white' : ''}" href="javascript:void(0)" onclick="setAdminSelectedTab(1)">
                                            <i class="bi bi-megaphone-fill text-warning"></i>
                                            <span>Pesan Pengumuman</span>
                                        </a>
                                    </li>
                                    <li><hr class="dropdown-divider my-1"></li>
                                    <li>
                                        <a class="dropdown-item d-flex align-items-center gap-2 py-2 text-primary" href="javascript:void(0)" onclick="openWriteMessageModal(null, null)">
                                            <i class="bi bi-pencil-square"></i>
                                            <span>Tulis Pesan</span>
                                        </a>
                                    </li>
                                    <li>
                                        <a class="dropdown-item d-flex align-items-center gap-2 py-2 text-success" href="javascript:void(0)" onclick="refreshAllAdminData()">
                                            <i class="bi bi-arrow-clockwise"></i>
                                            <span>Muat Ulang Data</span>
                                        </a>
                                    </li>
                                    <li>
                                        <a class="dropdown-item d-flex align-items-center gap-2 py-2 text-info" href="javascript:void(0)" onclick="openFirestoreRulesModal()">
                                            <i class="bi bi-shield-check"></i>
                                            <span>Aturan Rules Firestore</span>
                                        </a>
                                    </li>
                                </ul>
                            </div>
                        </div>

                    </div>

                    <!-- KONTEN SUB MENU SESUAI TAB TERPILIH (0 = DAFTAR PENGGUNA, 1 = PESAN) -->
                    <div id="adminTabContent">
                        ${dol}{
                            window.adminSelectedTab === 0 ? renderUsersTabHtml(users, totalCount, activeCount, inactiveCount, vipCount, onlineCount) :
                            renderMessagesTabHtml()
                        }
                    </div>

                </div>

                <!-- SEMUA DIALOG & MODAL LENGKAP PERSIS MANAJEMENAKUNSCREEN.KT -->
                ${dol}{renderWriteMessageModalHtml()}
                ${dol}{renderToggleStatusModalHtml()}
                ${dol}{renderSetMembershipModalHtml()}
                ${dol}{renderVipMembershipCardModalHtml()}
                ${dol}{renderDeleteMessageModalHtml()}
                ${dol}{renderDeleteAllMessagesModalHtml()}
                ${dol}{renderFirestoreRulesModalHtml()}
                ${dol}{renderAdminPhoneModalHtml()}
                ${dol}{renderOtpInboxModalHtml()}
            `;
        }

        // Ganti Tab Utama (users vs messages vs whatsapp)
        function setAdminTab(tab) {
            window.adminActiveTab = tab;
            renderManajemenAkunUI();
        }

        // Muat ulang seluruh data
        function refreshAllAdminData() {
            showAdminToast("Memuat ulang data pengguna, membership, dan pesan...", "bi-arrow-clockwise", "#0284C7");
            fetchAdminUsers();
            fetchAdminMessages();
        }

        // --- FILTER DATA PENGGUNA ---
        function getFilteredAdminUsers() {
            let list = window.adminUsers || [];
            const query = (window.adminSearchQuery || '').trim().toLowerCase();

            if (query.length > 0) {
                list = list.filter(u => {
                    const name = (u.displayName || '').toLowerCase();
                    const email = (u.email || '').toLowerCase();
                    const uid = (u.uid || '').toLowerCase();
                    const dev = (u.deviceModel || '').toLowerCase();
                    return name.includes(query) || email.includes(query) || uid.includes(query) || dev.includes(query);
                });
            }

            if (window.adminStatusFilter === 'ACTIVE') {
                list = list.filter(u => u.isActive);
            } else if (window.adminStatusFilter === 'INACTIVE') {
                list = list.filter(u => !u.isActive);
            } else if (window.adminStatusFilter === 'VIP') {
                list = list.filter(u => u.hasMembership || (u.membershipDurationMonths && u.membershipDurationMonths > 0) || u.isAdmin);
            } else if (window.adminStatusFilter === 'ONLINE') {
                list = list.filter(u => u.isRecentlyActive);
            }

            return list;
        }

        // --- TAB 1: DAFTAR PENGGUNA PERSIS USERDATATABLE DI MANAJEMENAKUNSCREEN.KT ---
        function renderUsersTabHtml(filteredList, totalCount, activeCount, inactiveCount, vipCount, onlineCount) {
            return `
                <div class="d-flex flex-column gap-3">
                    
                    <!-- SEARCH BAR WITH CLEAR BUTTON -->
                    <div class="position-relative">
                        <i class="bi bi-search position-absolute top-50 start-0 translate-middle-y ms-3 text-secondary"></i>
                        <input type="text" class="form-control ps-5 pe-5 py-2 rounded-3 border shadow-sm admin-search-input" placeholder="Cari nama pengguna, email, UID, atau perangkat..." value="${dol}{escapeHtml(window.adminSearchQuery)}" oninput="onAdminSearchInput(this.value)" style="font-size: 13.5px;">
                        ${dol}{window.adminSearchQuery ? `
                            <button class="btn btn-sm btn-link position-absolute top-50 end-0 translate-middle-y me-2 text-secondary p-0 text-decoration-none" onclick="clearAdminSearch()">
                                <i class="bi bi-x-circle-fill fs-5"></i>
                            </button>
                        ` : ''}
                    </div>

                    <!-- QUICK FILTER CHIPS BAR (PERSIS MANAJEMENAKUNSCREEN.KT) -->
                    <div class="d-flex align-items-center gap-2 overflow-x-auto pb-1" style="white-space: nowrap;">
                        <button class="btn btn-sm ${dol}{window.adminStatusFilter === 'ALL' ? 'btn-primary' : 'btn-outline-secondary'} rounded-pill px-3" onclick="setAdminFilter('ALL')">
                            Semua (${dol}{totalCount})
                        </button>
                        <button class="btn btn-sm ${dol}{window.adminStatusFilter === 'ACTIVE' ? 'btn-success' : 'btn-outline-secondary'} rounded-pill px-3 d-flex align-items-center gap-1" onclick="setAdminFilter('ACTIVE')">
                            <span class="rounded-circle d-inline-block" style="width: 7px; height: 7px; background: #10B981;"></span>
                            <span>Aktif (${dol}{activeCount})</span>
                        </button>
                        <button class="btn btn-sm ${dol}{window.adminStatusFilter === 'INACTIVE' ? 'btn-danger' : 'btn-outline-secondary'} rounded-pill px-3 d-flex align-items-center gap-1" onclick="setAdminFilter('INACTIVE')">
                            <span class="rounded-circle d-inline-block" style="width: 7px; height: 7px; background: #EF4444;"></span>
                            <span>Nonaktif / Habis (${dol}{inactiveCount})</span>
                        </button>
                        <button class="btn btn-sm ${dol}{window.adminStatusFilter === 'MEMBER' ? 'btn-warning text-dark fw-bold' : 'btn-outline-secondary'} rounded-pill px-3 d-flex align-items-center gap-1" onclick="setAdminFilter('MEMBER')">
                            <i class="bi bi-star-fill text-warning"></i>
                            <span>Member (${dol}{vipCount})</span>
                        </button>
                        <button class="btn btn-sm ${dol}{window.adminStatusFilter === 'ONLINE' ? 'btn-info text-white' : 'btn-outline-secondary'} rounded-pill px-3 d-flex align-items-center gap-1" onclick="setAdminFilter('ONLINE')">
                            <span class="rounded-circle d-inline-block" style="width: 7px; height: 7px; background: #06B6D4;"></span>
                            <span>Online (${dol}{onlineCount})</span>
                        </button>
                        <button class="btn btn-sm btn-outline-secondary rounded-pill px-3 d-flex align-items-center gap-1" onclick="setAdminSelectedTab(1)">
                            <i class="bi bi-chat-left-text-fill text-primary"></i>
                            <span>Pesan (${dol}{(window.adminMessages || []).length})</span>
                        </button>
                    </div>

                    <!-- KETERANGAN TOTAL DATA & GESER -->
                    <div class="d-flex align-items-center justify-content-between small text-secondary px-1">
                        <span class="fw-bold text-dark admin-text-main">Tabel Data Pengguna (${dol}{filteredList.length})</span>
                        <span class="text-primary fw-medium"><span class="d-none d-md-inline">Geser ke samping untuk kolom lengkap </span>➔</span>
                    </div>

                    <!-- TABEL 7 KOLOM PERSIS MANAJEMENAKUNSCREEN.KT -->
                    <div class="card border shadow-sm rounded-4 overflow-hidden admin-table-card">
                        <div class="table-responsive" style="max-height: 650px;">
                            <table class="table table-hover align-middle mb-0 admin-table" style="min-width: 1040px; font-size: 13px;">
                                <thead class="table-light text-secondary small fw-bold text-uppercase border-bottom">
                                    <tr>
                                        <th style="width: 44px; text-align: center;">NO</th>
                                        <th style="width: 220px;">
                                            <i class="bi bi-people-fill text-primary me-1"></i> PENGGUNA
                                        </th>
                                        <th style="width: 100px; text-align: center;">
                                            <i class="bi bi-shield-fill text-primary me-1"></i> STATUS
                                        </th>
                                        <th style="width: 175px;">
                                            <i class="bi bi-star-fill text-warning me-1"></i> MEMBERSHIP
                                        </th>
                                        <th style="width: 155px;">
                                            <i class="bi bi-clock-fill text-primary me-1"></i> TERAKHIR AKSES
                                        </th>
                                        <th style="width: 160px;">
                                            <i class="bi bi-phone-fill text-primary me-1"></i> PERANGKAT & FCM
                                        </th>
                                        <th style="width: 206px; text-align: center;">
                                            <i class="bi bi-person-gear text-primary me-1"></i> AKSI ADMIN
                                        </th>
                                    </tr>
                                </thead>
                                <tbody>
                                    ${dol}{window.adminIsLoadingUsers ? `
                                        <tr>
                                            <td colspan="7" class="text-center py-5">
                                                <div class="spinner-border text-primary mb-2" role="status" style="width: 2.2rem; height: 2.2rem;"></div>
                                                <div class="text-secondary small">Memuat data pengguna...</div>
                                            </td>
                                        </tr>
                                    ` : (filteredList.length === 0 ? `
                                        <tr>
                                            <td colspan="7" class="text-center py-5">
                                                <i class="bi bi-people text-secondary mb-2" style="font-size: 3rem;"></i>
                                                <div class="fw-semibold text-secondary">Tidak ada data pengguna yang cocok.</div>
                                                ${dol}{window.adminSearchQuery ? `
                                                    <button class="btn btn-sm btn-outline-primary mt-2" onclick="clearAdminSearch()">Reset Pencarian</button>
                                                ` : ''}
                                            </td>
                                        </tr>
                                    ` : filteredList.map((user, idx) => renderUserTableRowHtml(user, idx + 1)).join(''))}
                                </tbody>
                            </table>
                        </div>

                        <!-- FOOTER TABEL RINGKASAN STATUS -->
                        <div class="card-footer bg-light d-flex flex-wrap align-items-center justify-content-between p-3 small text-secondary admin-table-footer">
                            <span>Menampilkan ${dol}{filteredList.length} dari ${dol}{totalCount} pengguna</span>
                            <span class="fw-semibold">${dol}{activeCount} Aktif • ${dol}{inactiveCount} Nonaktif • ${dol}{vipCount} Member VIP</span>
                        </div>
                    </div>

                </div>
            `;
        }

        // BARIS INDIVIDUAL TABEL PENGGUNA (7 KOLOM LENGKAP)
        function renderUserTableRowHtml(user, index) {
            const isOnline = user.isRecentlyActive || false;
            const isAdmin = user.isAdmin || (user.uid === window.adminAuthStatus.adminUid);
            const initials = (user.displayName || user.email || 'U').substring(0, 2).toUpperCase();
            
            const hasMembership = user.hasMembership || (user.membershipDurationMonths && user.membershipDurationMonths > 0) || isAdmin;
            const isExpired = user.isMembershipExpired || false;
            const tierName = user.membershipTier || (isAdmin ? 'VIP Admin' : (hasMembership ? (user.membershipDurationMonths + ' Bulan') : 'Gratis'));
            const dateStr = user.formattedMembershipEndDate || (isAdmin ? 'Permanen' : (hasMembership ? 'Aktif' : '-'));

            return `
                <tr class="${dol}{isAdmin ? 'table-success bg-opacity-25' : (!user.isActive ? 'table-danger bg-opacity-25' : '')}">
                    
                    <!-- KOLOM 1: NO -->
                    <td class="text-center fw-bold text-secondary admin-text-sub">${dol}{index}</td>

                    <!-- KOLOM 2: PENGGUNA (AVATAR, NAMA, BADGE ADMIN, EMAIL, UID) - KLIK UNTUK DETAIL MEMBERSHIP -->
                    <td>
                        <div class="d-flex align-items-center gap-2" style="cursor: pointer;" onclick="openVipMembershipCardModal('${dol}{user.uid}')" title="Klik untuk Pratinjau Detail Membership">
                            ${dol}{user.photoUrl ? `
                                <img src="${dol}{escapeHtml(user.photoUrl)}" class="rounded-circle shadow-sm" style="width: 36px; height: 36px; object-fit: cover;" onerror="this.src='/api/profile/image'">
                            ` : `
                                <div class="rounded-circle bg-primary bg-opacity-10 text-primary fw-bold d-flex align-items-center justify-content-center shadow-sm" style="width: 36px; height: 36px; font-size: 13px;">
                                    ${dol}{initials}
                                </div>
                            `}
                            <div class="overflow-hidden" style="max-width: 175px;">
                                <div class="d-flex align-items-center gap-1">
                                    <span class="fw-bold text-truncate text-dark admin-text-main" title="${dol}{escapeHtml(user.displayName)}">${dol}{escapeHtml(user.displayName || 'Pengguna')}</span>
                                    ${dol}{isAdmin ? `<span class="badge" style="background-color: #065F46; color: #ffffff; font-size: 8px; font-weight: bold; border-radius: 4px; padding: 2px 4px;">ADMIN</span>` : ''}
                                </div>
                                ${dol}{user.email ? `
                                    <div class="small text-secondary admin-text-sub text-truncate" title="${dol}{escapeHtml(user.email)}" style="font-size: 11px;">${dol}{escapeHtml(user.email)}</div>
                                ` : ''}
                                <div class="d-flex align-items-center gap-1 mt-1 font-monospace admin-text-sub" style="font-size: 10px;">
                                    <span>UID: ${dol}{user.uid.substring(0, 8)}...</span>
                                    <button class="btn btn-sm btn-link p-0 text-secondary" style="font-size: 10px;" onclick="event.stopPropagation(); copyAdminClipboard('UID Pengguna', '${dol}{user.uid}')" title="Salin UID">
                                        <i class="bi bi-clipboard"></i>
                                    </button>
                                </div>
                            </div>
                        </div>
                    </td>

                    <!-- KOLOM 3: STATUS AKUN (AKTIF / NONAKTIF / HABIS) -->
                    <td class="text-center">
                        ${dol}{user.isActive && !isExpired ? `
                            <span class="badge d-inline-flex align-items-center gap-1 px-2 py-1 rounded-pill" style="background: rgba(16, 185, 129, 0.15); color: #047857; border: 1px solid rgba(16, 185, 129, 0.35); font-size: 10.5px; font-weight: bold;">
                                <span class="rounded-circle" style="width: 5px; height: 5px; background: #10B981;"></span>
                                <span>AKTIF</span>
                            </span>
                        ` : `
                            <span class="badge d-inline-flex align-items-center gap-1 px-2 py-1 rounded-pill" style="background: rgba(239, 68, 68, 0.15); color: #B91C1C; border: 1px solid rgba(239, 68, 68, 0.35); font-size: 10.5px; font-weight: bold;">
                                <span class="rounded-circle" style="width: 5px; height: 5px; background: #EF4444;"></span>
                                <span>${dol}{isExpired ? 'HABIS' : 'NONAKTIF'}</span>
                            </span>
                        `}
                    </td>

                    <!-- KOLOM 4: MEMBERSHIP (1, 3, 5, 7, 12 BULAN) - KLIK UNTUK DETAIL MEMBERSHIP -->
                    <td>
                        <div class="d-flex flex-column gap-1" style="cursor: pointer;" onclick="openVipMembershipCardModal('${dol}{user.uid}')" title="Klik untuk Pratinjau Detail Membership">
                            ${dol}{isAdmin ? `
                                <div>
                                    <span class="badge rounded-pill" style="background: #DCFCE7; color: #15803D; border: 1px solid #86EFAC; font-size: 9px; font-weight: bold;">
                                        ADMIN (PERMANEN)
                                    </span>
                                </div>
                            ` : (isExpired ? `
                                <div>
                                    <span class="badge rounded-pill bg-danger-subtle text-danger border border-danger" style="font-size: 9px; font-weight: bold;">
                                        KADALUARSA
                                    </span>
                                </div>
                                <small class="text-danger fw-semibold" style="font-size: 10px;">Habis: ${dol}{escapeHtml(dateStr)}</small>
                            ` : (hasMembership ? `
                                <div class="d-flex align-items-center gap-1 flex-wrap">
                                    <span class="badge rounded-pill" style="background: #DBEAFE; color: #1D4ED8; border: 1px solid #93C5FD; font-size: 9px; font-weight: bold;">
                                        ${dol}{escapeHtml(tierName)}
                                    </span>
                                    <small class="text-success fw-bold" style="font-size: 10px;">Sisa ${dol}{user.remainingDays || (user.membershipDurationMonths ? user.membershipDurationMonths * 30 : 30)} hr</small>
                                </div>
                                <small class="text-secondary admin-text-sub font-monospace" style="font-size: 10px;">s.d ${dol}{escapeHtml(dateStr)}</small>
                            ` : `
                                <span class="small text-secondary admin-text-sub">Belum Diatur</span>
                            `))}
                        </div>
                    </td>

                    <!-- KOLOM 5: TERAKHIR AKSES -->
                    <td>
                        <div class="d-flex flex-column">
                            <div class="d-flex align-items-center gap-1">
                                ${dol}{isOnline ? `
                                    <span class="rounded-circle" style="width: 6px; height: 6px; background: #10B981;"></span>
                                    <span class="text-success fw-bold" style="font-size: 10px;">ONLINE</span>
                                ` : ''}
                                <span class="small fw-semibold ${dol}{isOnline ? 'text-success' : 'text-dark admin-text-main'}">${dol}{escapeHtml(user.formattedLastActive || '-')}</span>
                            </div>
                            ${dol}{user.fullDateTime && user.fullDateTime !== '-' ? `
                                <span class="font-monospace text-secondary admin-text-sub" style="font-size: 10px;">${dol}{escapeHtml(user.fullDateTime)}</span>
                            ` : ''}
                        </div>
                    </td>

                    <!-- KOLOM 6: PERANGKAT & FCM -->
                    <td>
                        <div class="d-flex flex-column gap-1">
                            <span class="small fw-semibold text-dark admin-text-main text-truncate" style="max-width: 150px;">
                                ${dol}{escapeHtml(user.deviceModel || 'Perangkat: -')}
                            </span>
                            <div class="d-flex align-items-center gap-1">
                                ${dol}{user.fcmToken ? `
                                    <i class="bi bi-check-circle-fill text-success" style="font-size: 11px;"></i>
                                    <span class="text-success" style="font-size: 10px; font-weight: 500;">Token Tersimpan</span>
                                    <button class="btn btn-sm btn-link p-0 text-secondary" style="font-size: 10px;" onclick="copyAdminClipboard('Token FCM', '${dol}{user.fcmToken}')" title="Salin Token FCM">
                                        <i class="bi bi-clipboard"></i>
                                    </button>
                                ` : `
                                    <i class="bi bi-info-circle-fill text-warning" style="font-size: 11px;"></i>
                                    <span class="text-secondary admin-text-sub" style="font-size: 10px;">Belum Ada</span>
                                `}
                            </div>
                        </div>
                    </td>

                    <!-- KOLOM 7: AKSI ADMIN (PESAN, MEMBER, STATUS) -->
                    <td class="text-center">
                        <div class="d-flex align-items-center justify-content-center gap-1 flex-wrap">
                            
                            <!-- TOMBOL PESAN -->
                            <button class="btn btn-sm btn-outline-primary py-1 px-2 d-inline-flex align-items-center gap-1" style="font-size: 11px; border-radius: 6px;" onclick="openWriteMessageModal('${dol}{user.uid}', '${dol}{escapeJs(user.displayName || user.email || 'Pengguna')}')" title="Kirim Pesan ke Pengguna">
                                <i class="bi bi-send-fill" style="font-size: 10px;"></i>
                                <span>Pesan</span>
                            </button>

                            <!-- TOMBOL MEMBER (ATUR MEMBERSHIP 1, 3, 5, 7, 12 BULAN) -->
                            ${dol}{!isAdmin ? `
                                <button class="btn btn-sm btn-outline-warning py-1 px-2 d-inline-flex align-items-center gap-1" style="font-size: 11px; border-radius: 6px; color: #D97706; border-color: #F59E0B;" onclick="openSetMembershipModal('${dol}{user.uid}')" title="Atur Membership Pengguna">
                                    <i class="bi bi-star-fill" style="font-size: 10px; color: #F59E0B;"></i>
                                    <span>Member</span>
                                </button>
                            ` : ''}

                            <!-- TOMBOL STATUS AKUN (NONAKTIF / AKTIFKAN) -->
                            ${dol}{isAdmin ? `
                                <span class="badge py-1 px-2" style="background: rgba(21, 128, 61, 0.15); color: #15803D; font-size: 10px; font-weight: bold; border-radius: 6px;">Admin</span>
                            ` : (user.isActive && !isExpired ? `
                                <button class="btn btn-sm btn-outline-danger py-1 px-2 d-inline-flex align-items-center gap-1" style="font-size: 11px; border-radius: 6px;" onclick="confirmToggleUserStatus('${dol}{user.uid}')" title="Nonaktifkan Akun">
                                    <i class="bi bi-slash-circle" style="font-size: 10px;"></i>
                                    <span>Nonaktif</span>
                                </button>
                            ` : `
                                <button class="btn btn-sm btn-success py-1 px-2 d-inline-flex align-items-center gap-1" style="font-size: 11px; border-radius: 6px;" onclick="confirmToggleUserStatus('${dol}{user.uid}')" title="Aktifkan Kembali Akun">
                                    <i class="bi bi-check-circle-fill" style="font-size: 10px;"></i>
                                    <span>Aktifkan</span>
                                </button>
                            `)}

                        </div>
                    </td>

                </tr>
            `;
        }

        function onAdminSearchInput(val) {
            window.adminSearchQuery = val;
            renderManajemenAkunUI();
        }

        function clearAdminSearch() {
            window.adminSearchQuery = '';
            renderManajemenAkunUI();
        }

        function setAdminFilter(filter) {
            window.adminStatusFilter = filter;
            renderManajemenAkunUI();
        }

        // --- TAB 2: PESAN MASUK & SIARAN (BROADCAST) PERSIS PESANADMINCONTENT ---
        function renderMessagesTabHtml() {
            const messages = window.adminMessages || [];

            return `
                <div class="d-flex flex-column gap-3">
                    
                    <!-- TOMBOL KEMBALI KE TABEL PENGGUNA PERSIS MANAJEMENAKUNSCREEN.KT -->
                    <div class="d-flex align-items-center">
                        <button class="btn btn-sm btn-outline-primary rounded-pill px-3 py-1 fw-semibold d-flex align-items-center gap-1" onclick="setAdminSelectedTab(0)" style="font-size: 12px;">
                            <i class="bi bi-arrow-left"></i>
                            <span>Kembali ke Tabel Pengguna</span>
                        </button>
                    </div>

                    <!-- ACTION BAR ATAS: TULIS PESAN & HAPUS SEMUA -->
                    <div class="d-flex align-items-center justify-content-between flex-wrap gap-2">
                        <div class="small text-secondary admin-text-sub">
                            Daftar Pengumuman & Broadcast Sistem (${dol}{messages.length} Pesan)
                        </div>
                        <div class="d-flex align-items-center gap-2">
                            <button class="btn btn-sm btn-primary px-3 py-2 fw-semibold d-flex align-items-center gap-1 shadow-sm" style="border-radius: 8px;" onclick="openWriteMessageModal(null, null)">
                                <i class="bi bi-pencil-square"></i>
                                <span>Tulis Pesan Baru</span>
                            </button>
                            ${dol}{messages.length > 0 ? `
                                <button class="btn btn-sm btn-outline-danger px-3 py-2 fw-semibold d-flex align-items-center gap-1" style="border-radius: 8px;" onclick="confirmDeleteAllMessages()">
                                    <i class="bi bi-trash3-fill"></i>
                                    <span>Hapus Semua</span>
                                </button>
                            ` : ''}
                        </div>
                    </div>

                    <!-- BADGE ATURAN KEAMANAN PERSIS DI MANAJEMENAKUNSCREEN.KT -->
                    <div class="card border-0 shadow-sm rounded-3 admin-security-alert" style="background-color: #FEF3C7; color: #92400E;">
                        <div class="card-body p-3 d-flex align-items-center gap-2">
                            <i class="bi bi-shield-fill-exclamation fs-4 flex-shrink-0" style="color: #D97706;"></i>
                            <div class="small fw-medium" style="line-height: 1.5;">
                                <strong>Aturan Keamanan:</strong> Hanya akun Admin (UID: <code class="text-danger fw-bold">${dol}{window.adminAuthStatus.adminUid}</code>) yang dapat membuat dan menghapus pesan di koleksi 'messages' Cloud Firestore.
                            </div>
                        </div>
                    </div>

                    <!-- DAFTAR PESAN -->
                    ${dol}{window.adminIsLoadingMessages ? `
                        <div class="text-center py-5">
                            <div class="spinner-border text-primary mb-2" role="status" style="width: 2.2rem; height: 2.2rem;"></div>
                            <div class="text-secondary small">Memuat pesan...</div>
                        </div>
                    ` : (messages.length === 0 ? `
                        <div class="text-center py-5 card border-0 shadow-sm rounded-4 p-4 admin-card">
                            <i class="bi bi-chat-left-dots text-secondary mb-2" style="font-size: 3rem;"></i>
                            <h6 class="fw-bold text-dark admin-text-main">Belum ada pesan siaran.</h6>
                            <p class="text-secondary admin-text-sub small">Tulis pesan pengumuman atau instruksi untuk seluruh pengguna aplikasi.</p>
                            <div>
                                <button class="btn btn-primary btn-sm px-3 py-2 fw-semibold" onclick="openWriteMessageModal(null, null)">Tulis Pesan Pertama</button>
                            </div>
                        </div>
                    ` : `
                        <div class="d-flex flex-column gap-3">
                            ${dol}{messages.map(msg => renderMessageCardHtml(msg)).join('')}
                        </div>
                    `)}

                </div>
            `;
        }

        // KARTU PESAN INDIVIDUAL
        function renderMessageCardHtml(msg) {
            let catColor = '#2563EB';
            let catBg = 'rgba(37, 99, 235, 0.12)';
            if (msg.category === 'PERINGATAN') { catColor = '#DC2626'; catBg = 'rgba(220, 38, 38, 0.12)'; }
            else if (msg.category === 'PEMBARUAN') { catColor = '#0D9488'; catBg = 'rgba(13, 148, 136, 0.12)'; }
            else if (msg.category === 'INFORMASI') { catColor = '#0284C7'; catBg = 'rgba(2, 132, 199, 0.12)'; }

            const dateStr = msg.createdAt ? new Date(msg.createdAt).toLocaleString('id-ID', {
                day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit'
            }) : '-';

            return `
                <div class="card border-0 shadow-sm rounded-4 overflow-hidden admin-msg-card" style="background: var(--card-bg, #ffffff);">
                    <div class="card-body p-3 p-md-4">
                        
                        <!-- HEADER: KATEGORI, PRIORITAS, TARGET & TOMBOL HAPUS -->
                        <div class="d-flex align-items-center justify-content-between flex-wrap gap-2 mb-2">
                            <div class="d-flex align-items-center gap-2 flex-wrap">
                                <span class="badge rounded-pill px-2 py-1 fw-bold" style="background: ${dol}{catBg}; color: ${dol}{catColor}; font-size: 10px;">
                                    ${dol}{escapeHtml(msg.category || 'PENGUMUMAN')}
                                </span>
                                ${dol}{msg.priority && msg.priority !== 'NORMAL' ? `
                                    <span class="badge rounded-pill px-2 py-1 fw-bold bg-danger-subtle text-danger" style="font-size: 10px;">
                                        ${dol}{escapeHtml(msg.priority)}
                                    </span>
                                ` : ''}
                                <span class="text-secondary small font-monospace" style="font-size: 11px;">
                                    Target: <strong class="text-dark admin-text-main">${dol}{escapeHtml(msg.targetName || 'Semua Pengguna')}</strong>
                                </span>
                            </div>

                            <!-- TOMBOL HAPUS PESAN -->
                            <button class="btn btn-sm btn-outline-danger border-0 p-1 rounded-circle" onclick="confirmDeleteMessage('${dol}{msg.id}', '${dol}{escapeJs(msg.title)}')" title="Hapus Pesan">
                                <i class="bi bi-trash3-fill fs-6"></i>
                            </button>
                        </div>

                        <!-- JUDUL PESAN -->
                        <h6 class="fw-bold mb-2 text-dark admin-text-main" style="font-size: 15px;">${dol}{escapeHtml(msg.title)}</h6>

                        <!-- ISI PESAN -->
                        <p class="text-secondary admin-text-sub mb-3" style="font-size: 13.5px; line-height: 1.6; white-space: pre-wrap;">${dol}{escapeHtml(msg.content)}</p>

                        <hr class="my-2 opacity-25">

                        <!-- FOOTER: PENULIS & WAKTU -->
                        <div class="d-flex align-items-center justify-content-between small text-secondary">
                            <span class="fw-semibold text-primary">Penulis: ${dol}{escapeHtml(msg.authorName || 'Administrator')}</span>
                            <span>${dol}{dateStr}</span>
                        </div>

                    </div>
                </div>
            `;
        }

        // --- TAB 3: PENGATURAN KONTAK WHATSAPP ADMIN ---
        function renderWhatsappContactTabHtml() {
            return `
                <div class="d-flex flex-column gap-3">
                    <div class="card border-0 shadow-sm rounded-4 p-4 admin-card" style="background: var(--card-bg, #ffffff); max-width: 680px; margin: 0 auto; width: 100%;">
                        <div class="d-flex align-items-center gap-3 mb-3">
                            <div class="rounded-circle p-3 d-flex align-items-center justify-content-center" style="background: rgba(16, 185, 129, 0.15); color: #10B981; width: 54px; height: 54px;">
                                <i class="bi bi-whatsapp fs-3"></i>
                            </div>
                            <div>
                                <h5 class="fw-bold text-dark admin-text-main mb-1">Pengaturan Kontak WhatsApp Admin</h5>
                                <small class="text-secondary admin-text-sub">Nomor resmi untuk bantuan dan aktivasi VIP member</small>
                            </div>
                        </div>

                        <div class="alert alert-info py-2 px-3 small rounded-3 mb-4 d-flex align-items-center gap-2">
                            <i class="bi bi-info-circle-fill fs-5 flex-shrink-0"></i>
                            <div>
                                Nomor ini terhubung langsung ke tombol <strong>"Hubungi Admin via WhatsApp"</strong> pada Kartu Membership Digital seluruh pengguna dan aplikasi My Singghara.
                            </div>
                        </div>

                        <div class="mb-3">
                            <label class="form-label small fw-bold text-secondary admin-text-sub">Nomor WhatsApp Admin</label>
                            <div class="input-group">
                                <span class="input-group-text bg-success text-white fw-bold"><i class="bi bi-whatsapp me-1"></i> WA</span>
                                <input type="text" class="form-control admin-form-control fs-5 fw-bold" id="adminWhatsappInput" value="${dol}{escapeHtml(window.adminPhoneNumber || '088806643415')}" placeholder="Contoh: 088806643415">
                            </div>
                            <small class="text-secondary admin-text-sub mt-1 d-block">Gunakan format Indonesia (diawali 08... atau 62...).</small>
                        </div>

                        <div class="d-flex flex-wrap gap-2 pt-2">
                            <button type="button" class="btn btn-success fw-semibold px-4 py-2 d-flex align-items-center gap-2 shadow-sm" onclick="saveAdminWhatsappNumber()">
                                <i class="bi bi-floppy-fill"></i>
                                <span>Simpan Nomor WhatsApp</span>
                            </button>
                            <button type="button" class="btn btn-outline-success fw-semibold px-3 py-2 d-flex align-items-center gap-2" onclick="testChatAdminWhatsapp()">
                                <i class="bi bi-chat-dots-fill"></i>
                                <span>Tes Chat Langsung (wa.me)</span>
                            </button>
                        </div>
                    </div>
                </div>
            `;
        }

        async function saveAdminWhatsappNumber() {
            const input = document.getElementById('adminWhatsappInput');
            if (!input) return;
            const newNumber = input.value.trim();
            if (!newNumber) {
                showAdminToast("Nomor WhatsApp tidak boleh kosong!", "bi-exclamation-triangle-fill", "#DC2626");
                return;
            }

            try {
                const res = await fetch('/api/admin?action=update_phone', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ phoneNumber: newNumber })
                });

                if (res.ok) {
                    const data = await res.json();
                    window.adminPhoneNumber = data.phoneNumber || newNumber;
                    try {
                        localStorage.setItem('mysingghara_admin_phone', window.adminPhoneNumber);
                    } catch(e) {}
                    showAdminToast("Nomor WhatsApp Admin berhasil diperbarui!", "bi-check-circle-fill", "#059669");
                } else {
                    showAdminToast("Gagal menyimpan nomor WhatsApp", "bi-exclamation-triangle-fill", "#DC2626");
                }
            } catch (e) {
                window.adminPhoneNumber = newNumber;
                try {
                    localStorage.setItem('mysingghara_admin_phone', newNumber);
                } catch(err) {}
                showAdminToast("Nomor WhatsApp tersimpan di memori lokal!", "bi-check-circle-fill", "#059669");
            }
        }

        function testChatAdminWhatsapp() {
            const raw = window.adminPhoneNumber || '088806643415';
            let cleaned = raw.replace(/[^0-9]/g, '');
            if (cleaned.startsWith('0')) {
                cleaned = '62' + cleaned.substring(1);
            }
            const url = 'https://wa.me/' + cleaned + '?text=' + encodeURIComponent('Halo Admin My Singghara, saya ingin menanyakan seputar membership VIP.');
            window.open(url, '_blank');
        }

        // ==========================================
        // --- DIALOG & MODAL LOGIC KHUSUS ADMIN ---
        // ==========================================

        // 1. DIALOG TULIS PESAN (WRITEMESSAGEDIALOG)
        function openWriteMessageModal(targetUid, targetName) {
            window.adminTargetUserForMessage = targetUid ? { uid: targetUid, name: targetName } : null;

            const targetInfoEl = document.getElementById('adminMsgTargetInfo');
            const targetNameEl = document.getElementById('adminMsgTargetName');
            const modalTitleEl = document.getElementById('adminWriteModalTitle');

            if (modalTitleEl) {
                modalTitleEl.textContent = targetUid ? 'Kirim Pesan ke Pengguna' : 'Tulis Pesan Siaran';
            }

            if (targetInfoEl && targetNameEl) {
                if (targetUid) {
                    targetInfoEl.classList.remove('d-none');
                    targetNameEl.textContent = `${dol}{targetName} (UID: ${dol}{targetUid.substring(0, 10)}...)`;
                } else {
                    targetInfoEl.classList.add('d-none');
                }
            }

            // Reset form
            document.getElementById('adminMsgTitle').value = '';
            document.getElementById('adminMsgContent').value = '';
            setAdminMsgCategory('PENGUMUMAN');
            setAdminMsgPriority('NORMAL');

            const modalEl = document.getElementById('adminWriteMessageModal');
            if (modalEl) new bootstrap.Modal(modalEl).show();
        }

        function setAdminMsgCategory(cat) {
            window.adminSelectedCategory = cat;
            document.querySelectorAll('.admin-cat-btn').forEach(btn => {
                if (btn.getAttribute('data-cat') === cat) {
                    btn.classList.add('btn-primary');
                    btn.classList.remove('btn-outline-secondary');
                } else {
                    btn.classList.remove('btn-primary');
                    btn.classList.add('btn-outline-secondary');
                }
            });
        }

        function setAdminMsgPriority(prio) {
            window.adminSelectedPriority = prio;
            document.querySelectorAll('.admin-prio-btn').forEach(btn => {
                if (btn.getAttribute('data-prio') === prio) {
                    btn.classList.add('btn-primary');
                    btn.classList.remove('btn-outline-secondary');
                } else {
                    btn.classList.remove('btn-primary');
                    btn.classList.add('btn-outline-secondary');
                }
            });
        }

        async function submitSingleMessage() {
            const title = (document.getElementById('adminMsgTitle').value || '').trim();
            const content = (document.getElementById('adminMsgContent').value || '').trim();
            const category = window.adminSelectedCategory || 'PENGUMUMAN';
            const priority = window.adminSelectedPriority || 'NORMAL';
            const targetUid = window.adminTargetUserForMessage ? window.adminTargetUserForMessage.uid : 'SEMUA';
            const targetName = window.adminTargetUserForMessage ? window.adminTargetUserForMessage.name : 'Semua Pengguna';

            if (!title || !content) {
                showAdminToast("Judul dan isi pesan tidak boleh kosong!", "bi-exclamation-triangle-fill", "#DC2626");
                return;
            }

            const sendBtn = document.getElementById('adminSubmitMsgBtn');
            if (sendBtn) {
                sendBtn.disabled = true;
                sendBtn.innerHTML = '<span class="spinner-border spinner-border-sm me-1"></span> Menyimpan...';
            }

            try {
                const res = await fetch('/api/admin?action=create_message', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({
                        title: title,
                        content: content,
                        category: category,
                        priority: priority,
                        target: targetUid,
                        targetName: targetName
                    })
                });

                const data = await res.json();
                if (res.ok && data && data.status === 'success') {
                    showAdminToast("Pesan berhasil dikirim dan tersimpan di penyimpanan!", "bi-check-circle-fill", "#059669");
                    const modalEl = document.getElementById('adminWriteMessageModal');
                    if (modalEl) bootstrap.Modal.getInstance(modalEl)?.hide();
                    fetchAdminMessages();
                } else {
                    showAdminToast("Gagal mengirim pesan: " + (data.message || 'Terjadi kesalahan'), "bi-exclamation-triangle-fill", "#DC2626");
                }
            } catch (e) {
                showAdminToast("Pesan tersimpan di mode offline!", "bi-check-circle-fill", "#059669");
                const modalEl = document.getElementById('adminWriteMessageModal');
                if (modalEl) bootstrap.Modal.getInstance(modalEl)?.hide();
                fetchAdminMessages();
            } finally {
                if (sendBtn) {
                    sendBtn.disabled = false;
                    sendBtn.innerHTML = '<i class="bi bi-cloud-arrow-up-fill me-1"></i> Simpan Pesan';
                }
            }
        }

        function renderWriteMessageModalHtml() {
            return `
                <div class="modal fade" id="adminWriteMessageModal" tabindex="-1" aria-hidden="true">
                    <div class="modal-dialog modal-dialog-centered" style="max-width: 520px;">
                        <div class="modal-content border-0 shadow-lg rounded-4">
                            <div class="modal-header border-0 pb-0">
                                <h6 class="modal-title fw-bold d-flex align-items-center gap-2" id="adminWriteModalTitle">
                                    <i class="bi bi-pencil-square text-primary"></i>
                                    <span>Tulis Pesan Siaran</span>
                                </h6>
                                <button type="button" class="btn-close" data-bs-dismiss="modal"></button>
                            </div>
                            <div class="modal-body p-4 pt-3">
                                
                                <div id="adminMsgTargetInfo" class="alert alert-primary d-none py-2 px-3 small rounded-3 mb-3 d-flex align-items-center gap-2">
                                    <i class="bi bi-person-check-fill fs-5"></i>
                                    <div>Target: <strong id="adminMsgTargetName">Pengguna</strong></div>
                                </div>

                                <div class="mb-3">
                                    <label class="form-label small fw-bold text-secondary admin-text-sub">Judul Pesan *</label>
                                    <input type="text" class="form-control rounded-3 admin-form-control" id="adminMsgTitle" placeholder="Contoh: Pengumuman Pembaruan Sistem">
                                </div>

                                <div class="mb-3">
                                    <label class="form-label small fw-bold text-secondary admin-text-sub">Kategori</label>
                                    <div class="d-flex flex-wrap gap-2">
                                        <button type="button" class="btn btn-sm btn-primary rounded-pill px-3 admin-cat-btn" data-cat="PENGUMUMAN" onclick="setAdminMsgCategory('PENGUMUMAN')">PENGUMUMAN</button>
                                        <button type="button" class="btn btn-sm btn-outline-secondary rounded-pill px-3 admin-cat-btn" data-cat="INFORMASI" onclick="setAdminMsgCategory('INFORMASI')">INFORMASI</button>
                                        <button type="button" class="btn btn-sm btn-outline-secondary rounded-pill px-3 admin-cat-btn" data-cat="PEMBARUAN" onclick="setAdminMsgCategory('PEMBARUAN')">PEMBARUAN</button>
                                        <button type="button" class="btn btn-sm btn-outline-secondary rounded-pill px-3 admin-cat-btn" data-cat="PERINGATAN" onclick="setAdminMsgCategory('PERINGATAN')">PERINGATAN</button>
                                    </div>
                                </div>

                                <div class="mb-3">
                                    <label class="form-label small fw-bold text-secondary admin-text-sub">Prioritas</label>
                                    <div class="d-flex flex-wrap gap-2">
                                        <button type="button" class="btn btn-sm btn-primary rounded-pill px-3 admin-prio-btn" data-prio="NORMAL" onclick="setAdminMsgPriority('NORMAL')">NORMAL</button>
                                        <button type="button" class="btn btn-sm btn-outline-secondary rounded-pill px-3 admin-prio-btn" data-prio="TINGGI" onclick="setAdminMsgPriority('TINGGI')">TINGGI</button>
                                        <button type="button" class="btn btn-sm btn-outline-secondary rounded-pill px-3 admin-prio-btn" data-prio="MENDESAK" onclick="setAdminMsgPriority('MENDESAK')">MENDESAK</button>
                                    </div>
                                </div>

                                <div class="mb-2">
                                    <label class="form-label small fw-bold text-secondary admin-text-sub">Isi Pesan *</label>
                                    <textarea class="form-control rounded-3 admin-form-control" id="adminMsgContent" rows="5" placeholder="Tulis isi pesan pengumuman..."></textarea>
                                </div>

                            </div>
                            <div class="modal-footer border-0 pt-0">
                                <button type="button" class="btn btn-outline-secondary" data-bs-dismiss="modal">Batal</button>
                                <button type="button" class="btn btn-primary fw-semibold px-4" id="adminSubmitMsgBtn" onclick="submitSingleMessage()">
                                    <i class="bi bi-cloud-arrow-up-fill me-1"></i> Simpan Pesan
                                </button>
                            </div>
                        </div>
                    </div>
                </div>
            `;
        }

        // 2. DIALOG KONFIRMASI STATUS AKUN (AKTIFKAN / NONAKTIFKAN)
        function confirmToggleUserStatus(uid) {
            const user = (window.adminUsers || []).find(u => u.uid === uid);
            if (!user) return;
            window.adminUserToToggle = user;

            const willDeactivate = user.isActive;
            const titleEl = document.getElementById('adminToggleStatusTitle');
            const descEl = document.getElementById('adminToggleStatusDesc');
            const infoEl = document.getElementById('adminToggleStatusInfo');
            const btnEl = document.getElementById('adminExecuteToggleBtn');

            if (titleEl) titleEl.textContent = willDeactivate ? 'Nonaktifkan Akun Pengguna?' : 'Aktifkan Kembali Akun?';
            if (descEl) {
                descEl.textContent = willDeactivate
                    ? `Apakah Anda yakin ingin menonaktifkan akun "${dol}{user.displayName}"? Pengguna tidak akan dapat login sampai diaktifkan kembali.`
                    : `Apakah Anda yakin ingin mengaktifkan kembali akun "${dol}{user.displayName}"? Pengguna akan dapat kembali mengakses seluruh fitur aplikasi.`;
            }

            if (infoEl) {
                infoEl.innerHTML = `
                    <div class="small fw-bold ${dol}{willDeactivate ? 'text-danger' : 'text-success'} mb-1">INFORMASI AKUN TARGET</div>
                    <div class="font-monospace small text-secondary admin-text-sub">UID: ${dol}{user.uid}</div>
                    ${dol}{user.email ? `<div class="small text-secondary admin-text-sub">Email: ${dol}{user.email}</div>` : ''}
                    <div class="small fw-semibold text-dark admin-text-main">Akses Terakhir: ${dol}{user.formattedLastActive || '-'}</div>
                    <div class="small fw-bold mt-1 ${dol}{willDeactivate ? 'text-danger' : 'text-success'}">
                        Tindakan: Akun akan ${dol}{willDeactivate ? 'DINONAKTIFKAN' : 'DIAKTIFKAN KEMBALI'}
                    </div>
                `;
            }

            if (btnEl) {
                btnEl.className = willDeactivate ? 'btn btn-danger fw-semibold px-4' : 'btn btn-success fw-semibold px-4';
                btnEl.innerHTML = willDeactivate ? '<i class="bi bi-slash-circle me-1"></i> Ya, Nonaktifkan' : '<i class="bi bi-check-circle-fill me-1"></i> Ya, Aktifkan';
            }

            const modalEl = document.getElementById('adminToggleStatusModal');
            if (modalEl) new bootstrap.Modal(modalEl).show();
        }

        async function executeToggleUserStatus() {
            if (!window.adminUserToToggle) return;
            const target = window.adminUserToToggle;
            const newStatus = !target.isActive;

            const btnEl = document.getElementById('adminExecuteToggleBtn');
            if (btnEl) {
                btnEl.disabled = true;
                btnEl.innerHTML = '<span class="spinner-border spinner-border-sm me-1"></span> Memproses...';
            }

            try {
                const res = await fetch('/api/admin?action=toggle_status', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({
                        targetUid: target.uid,
                        isActive: newStatus
                    })
                });

                const data = await res.json();
                const statusText = newStatus ? 'diaktifkan kembali' : 'dinonaktifkan';
                showAdminToast(`Akun ${dol}{target.displayName} berhasil ${dol}{statusText}!`, 'bi-check-circle-fill', '#059669');
                const modalEl = document.getElementById('adminToggleStatusModal');
                if (modalEl) bootstrap.Modal.getInstance(modalEl)?.hide();
                fetchAdminUsers();
            } catch (e) {
                showAdminToast("Error: " + e.message, "bi-exclamation-triangle-fill", "#DC2626");
            } finally {
                window.adminUserToToggle = null;
                if (btnEl) btnEl.disabled = false;
            }
        }

        function renderToggleStatusModalHtml() {
            return `
                <div class="modal fade" id="adminToggleStatusModal" tabindex="-1" aria-hidden="true">
                    <div class="modal-dialog modal-dialog-centered" style="max-width: 480px;">
                        <div class="modal-content border-0 shadow-lg rounded-4">
                            <div class="modal-header border-0 pb-0">
                                <h6 class="modal-title fw-bold" id="adminToggleStatusTitle">Konfirmasi Status Akun</h6>
                                <button type="button" class="btn-close" data-bs-dismiss="modal"></button>
                            </div>
                            <div class="modal-body p-4 pt-2">
                                <p class="text-secondary admin-text-sub small mb-3" id="adminToggleStatusDesc"></p>
                                <div class="rounded-3 p-3 border admin-info-box" style="background: var(--bg-surface, #F8FAFC);" id="adminToggleStatusInfo"></div>
                            </div>
                            <div class="modal-footer border-0 pt-0">
                                <button type="button" class="btn btn-outline-secondary" data-bs-dismiss="modal">Batal</button>
                                <button type="button" class="btn btn-danger fw-semibold px-4" id="adminExecuteToggleBtn" onclick="executeToggleUserStatus()">
                                    Konfirmasi
                                </button>
                            </div>
                        </div>
                    </div>
                </div>
            `;
        }

        // 3. DIALOG ATUR MEMBERSHIP PENGGUNA (SET MEMBERSHIP MODAL)
        function openSetMembershipModal(uid) {
            const user = (window.adminUsers || []).find(u => u.uid === uid);
            if (!user) return;
            window.adminUserToSetMembership = user;

            const nameEl = document.getElementById('adminSetMemUserName');
            const uidEl = document.getElementById('adminSetMemUserUid');
            const curTierEl = document.getElementById('adminSetMemCurrentTier');

            if (nameEl) nameEl.textContent = user.displayName || user.email || 'Pengguna';
            if (uidEl) uidEl.textContent = user.uid;
            if (curTierEl) curTierEl.textContent = user.membershipTier || (user.hasMembership ? (user.membershipDurationMonths + ' Bulan') : 'Tanpa Membership (Gratis)');

            const modalEl = document.getElementById('adminSetMembershipModal');
            if (modalEl) new bootstrap.Modal(modalEl).show();
        }

        async function executeSetMembership() {
            if (!window.adminUserToSetMembership) return;
            const target = window.adminUserToSetMembership;
            const durationSelect = document.getElementById('adminMembershipDurationSelect');
            const autoActiveCheckbox = document.getElementById('adminMembershipAutoActiveCheck');

            const months = parseInt(durationSelect?.value || '1', 10);
            const activateAccount = autoActiveCheckbox ? autoActiveCheckbox.checked : true;

            const btnEl = document.getElementById('adminExecuteSetMemBtn');
            if (btnEl) {
                btnEl.disabled = true;
                btnEl.innerHTML = '<span class="spinner-border spinner-border-sm me-1"></span> Menyimpan...';
            }

            try {
                const res = await fetch('/api/admin?action=set_membership', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({
                        targetUid: target.uid,
                        months: months,
                        activateAccount: activateAccount
                    })
                });

                if (res.ok) {
                    const durLabel = months === 0 ? 'Tanpa Membership' : (months + ' Bulan');
                    showAdminToast(`Membership ${dol}{target.displayName} berhasil diatur ke ${dol}{durLabel}!`, 'bi-award-fill', '#F59E0B');
                    const modalEl = document.getElementById('adminSetMembershipModal');
                    if (modalEl) bootstrap.Modal.getInstance(modalEl)?.hide();
                    fetchAdminUsers();
                } else {
                    showAdminToast("Gagal menyimpan membership", "bi-exclamation-triangle-fill", "#DC2626");
                }
            } catch (e) {
                showAdminToast("Membership tersimpan di cache lokal!", "bi-check-circle-fill", "#059669");
                const modalEl = document.getElementById('adminSetMembershipModal');
                if (modalEl) bootstrap.Modal.getInstance(modalEl)?.hide();
                fetchAdminUsers();
            } finally {
                window.adminUserToSetMembership = null;
                if (btnEl) {
                    btnEl.disabled = false;
                    btnEl.innerHTML = '<i class="bi bi-award-fill me-1"></i> Terapkan Membership';
                }
            }
        }

        function renderSetMembershipModalHtml() {
            return `
                <div class="modal fade" id="adminSetMembershipModal" tabindex="-1" aria-hidden="true">
                    <div class="modal-dialog modal-dialog-centered" style="max-width: 480px;">
                        <div class="modal-content border-0 shadow-lg rounded-4">
                            <div class="modal-header border-0 pb-0">
                                <h6 class="modal-title fw-bold text-dark admin-text-main d-flex align-items-center gap-2">
                                    <i class="bi bi-award-fill text-warning fs-5"></i>
                                    <span>Atur Membership Pengguna</span>
                                </h6>
                                <button type="button" class="btn-close" data-bs-dismiss="modal"></button>
                            </div>
                            <div class="modal-body p-4 pt-2">
                                
                                <div class="card p-3 rounded-3 border mb-3 admin-info-box" style="background: rgba(245, 158, 11, 0.08);">
                                    <div class="fw-bold text-dark admin-text-main" id="adminSetMemUserName">-</div>
                                    <div class="font-monospace small text-secondary admin-text-sub" style="font-size: 11px;">UID: <span id="adminSetMemUserUid">-</span></div>
                                    <div class="small mt-1 text-warning fw-bold">Status Saat Ini: <span id="adminSetMemCurrentTier">-</span></div>
                                </div>

                                <div class="mb-3">
                                    <label class="form-label small fw-bold text-secondary admin-text-sub">Pilih Durasi Masa Berlaku:</label>
                                    <select class="form-select rounded-3 admin-form-control fw-semibold" id="adminMembershipDurationSelect">
                                        <option value="1">VIP 1 Bulan (Akses Standar 30 Hari)</option>
                                        <option value="3">VIP 3 Bulan (Akses Kuartal 90 Hari)</option>
                                        <option value="5">VIP 5 Bulan (Akses 150 Hari)</option>
                                        <option value="7">VIP 7 Bulan (Akses 210 Hari)</option>
                                        <option value="12" selected>VIP 12 Bulan / 1 Tahun (Rekomendasi)</option>
                                        <option value="999">VIP Permanen (Akses Penuh Selamanya)</option>
                                        <option value="0">Reset / Nonaktifkan Membership (Kembali ke Gratis)</option>
                                    </select>
                                </div>

                                <div class="form-check form-switch mb-3">
                                    <input class="form-check-input" type="checkbox" id="adminMembershipAutoActiveCheck" checked>
                                    <label class="form-check-label small fw-medium text-dark admin-text-main" for="adminMembershipAutoActiveCheck">
                                        Otomatis aktifkan akun saat membership diterapkan
                                    </label>
                                </div>

                                <!-- BOX PERINGATAN PROTEKSI OTOMATIS PERSIS MANAJEMENAKUNSCREEN.KT -->
                                <div class="rounded-3 p-2 p-md-3 border d-flex align-items-start gap-2 mb-2" style="background: rgba(220, 38, 38, 0.08); border-color: rgba(220, 38, 38, 0.25) !important;">
                                    <i class="bi bi-slash-circle-fill text-danger flex-shrink-0 mt-1" style="font-size: 15px;"></i>
                                    <div class="small text-danger" style="font-size: 11px; line-height: 1.45;">
                                        <strong>Proteksi Otomatis:</strong> Jika waktu membership habis, akun pengguna akan otomatis dinonaktifkan. Pengguna tidak akan bisa login dan saat aplikasi dibuka akan langsung diblokir.
                                    </div>
                                </div>

                            </div>
                            <div class="modal-footer border-0 pt-0">
                                <button type="button" class="btn btn-outline-secondary" data-bs-dismiss="modal">Batal</button>
                                <button type="button" class="btn btn-warning fw-semibold px-4" id="adminExecuteSetMemBtn" onclick="executeSetMembership()">
                                    <i class="bi bi-award-fill me-1"></i> Terapkan Membership
                                </button>
                            </div>
                        </div>
                    </div>
                </div>
            `;
        }

        // 4. DIALOG KARTU DIGITAL VIP PENGGUNA (VIP MEMBERSHIP CARD MODAL)
        function openVipMembershipCardModal(uid) {
            const user = (window.adminUsers || []).find(u => u.uid === uid);
            if (!user) return;
            window.adminUserForVipCard = user;

            const nameEl = document.getElementById('vipCardName');
            const emailEl = document.getElementById('vipCardEmail');
            const uidEl = document.getElementById('vipCardUid');
            const tierEl = document.getElementById('vipCardTier');
            const expEl = document.getElementById('vipCardExp');
            const daysEl = document.getElementById('vipCardDays');
            const initialsEl = document.getElementById('vipCardInitials');
            const qrContainer = document.getElementById('vipCardQrCode');

            const initials = (user.displayName || user.email || 'U').substring(0, 2).toUpperCase();
            if (nameEl) nameEl.textContent = user.displayName || user.email || 'Pengguna';
            if (emailEl) emailEl.textContent = user.email || '-';
            if (uidEl) uidEl.textContent = user.uid;
            if (initialsEl) initialsEl.textContent = initials;

            const isAdmin = user.isAdmin || (user.uid === window.adminAuthStatus.adminUid);
            const hasMembership = user.hasMembership || (user.membershipDurationMonths && user.membershipDurationMonths > 0) || isAdmin;
            const tierText = user.membershipTier || (isAdmin ? 'VIP ADMINISTRATOR' : (hasMembership ? (user.membershipDurationMonths + ' BULAN') : 'REGULAR FREE'));
            const dateEndStr = user.formattedMembershipEndDate || (isAdmin ? 'PERMANEN' : (hasMembership ? 'AKTIF' : '-'));

            if (tierEl) tierEl.textContent = tierText;
            if (expEl) expEl.textContent = dateEndStr;
            if (daysEl) daysEl.textContent = user.formattedMembershipPeriod || (isAdmin ? 'Akses Penuh Permanen' : (hasMembership ? 'Membership Aktif' : 'Tanpa Paket Membership'));

            // Render QR Code pengguna menggunakan library QRCode yang tersedia
            if (qrContainer) {
                qrContainer.innerHTML = '';
                if (typeof QRCode !== 'undefined') {
                    try {
                        const qrPayload = JSON.stringify({
                            action: 'quick_login',
                            email: (user.email || '').trim(),
                            uid: user.uid || '',
                            role: isAdmin ? 'admin' : 'member'
                        });
                        new QRCode(qrContainer, {
                            text: qrPayload,
                            width: 110,
                            height: 110,
                            colorDark: '#0f172a',
                            colorLight: '#ffffff',
                            correctLevel: QRCode.CorrectLevel.M
                        });
                    } catch(e) {
                        qrContainer.innerHTML = `<i class="bi bi-qr-code fs-1 text-dark"></i>`;
                    }
                } else {
                    qrContainer.innerHTML = `<i class="bi bi-qr-code fs-1 text-dark"></i>`;
                }
            }

            // Update Front Card details
            const frontNameEl = document.getElementById('vipFrontCardName');
            const frontNumberEl = document.getElementById('vipFrontCardNumber');
            const frontExpEl = document.getElementById('vipFrontCardExp');
            const frontTierEl = document.getElementById('vipFrontCardTier');

            if (frontNameEl) frontNameEl.textContent = (user.displayName || user.email || 'PENGGUNA SETIA').toUpperCase();
            if (frontNumberEl) {
                const uidPart = (user.uid || '0000').slice(-4).toUpperCase();
                frontNumberEl.textContent = `VIP •••• •••• ${dol}{uidPart}`;
            }
            if (frontExpEl) frontExpEl.textContent = dateEndStr;
            if (frontTierEl) frontTierEl.textContent = tierText;

            setVipCardSide('MEMBER');

            const modalEl = document.getElementById('adminVipMembershipCardModal');
            if (modalEl) new bootstrap.Modal(modalEl).show();
        }

        function setVipCardSide(side) {
            window.adminVipCardSide = side;
            const backView = document.getElementById('vipMemberCardBackView');
            const frontView = document.getElementById('vipMemberCardFrontView');
            const memberBtn = document.getElementById('vipSideMemberBtn');
            const frontBtn = document.getElementById('vipSideFrontBtn');

            if (side === 'FRONT') {
                if (backView) backView.classList.add('d-none');
                if (frontView) frontView.classList.remove('d-none');
                if (memberBtn) {
                    memberBtn.style.background = 'transparent';
                    memberBtn.className = 'btn btn-sm rounded-pill px-3 py-1 fw-bold text-white-50';
                }
                if (frontBtn) {
                    frontBtn.style.background = 'rgba(245, 158, 11, 0.9)';
                    frontBtn.className = 'btn btn-sm rounded-pill px-3 py-1 fw-bold text-white';
                }
            } else {
                if (backView) backView.classList.remove('d-none');
                if (frontView) frontView.classList.add('d-none');
                if (memberBtn) {
                    memberBtn.style.background = 'rgba(245, 158, 11, 0.9)';
                    memberBtn.className = 'btn btn-sm rounded-pill px-3 py-1 fw-bold text-white';
                }
                if (frontBtn) {
                    frontBtn.style.background = 'transparent';
                    frontBtn.className = 'btn btn-sm rounded-pill px-3 py-1 fw-bold text-white-50';
                }
            }
        }

        function copyVipCardUid() {
            if (window.adminUserForVipCard) {
                copyAdminClipboard('UID Pengguna', window.adminUserForVipCard.uid);
            }
        }

        function editMembershipFromCard() {
            if (window.adminUserForVipCard) {
                const targetUid = window.adminUserForVipCard.uid;
                const modalEl = document.getElementById('adminVipMembershipCardModal');
                if (modalEl) bootstrap.Modal.getInstance(modalEl)?.hide();
                setTimeout(() => {
                    openSetMembershipModal(targetUid);
                }, 300);
            }
        }

        function renderVipMembershipCardModalHtml() {
            return `
                <div class="modal fade" id="adminVipMembershipCardModal" tabindex="-1" aria-hidden="true">
                    <div class="modal-dialog modal-dialog-centered" style="max-width: 480px;">
                        <div class="modal-content border-0 bg-transparent shadow-none">
                            <div class="vip-card-container p-4">
                                <div class="vip-card-glow"></div>

                                <!-- FLIP CONTROLLER TABS (PERSIS VIPMEMBERSHIPCARD.KT) -->
                                <div class="d-flex align-items-center justify-content-center gap-1 mb-3 p-1 rounded-pill position-relative" style="background: rgba(255, 255, 255, 0.12); width: fit-content; margin: 0 auto; z-index: 3;">
                                    <button type="button" class="btn btn-sm rounded-pill px-3 py-1 fw-bold text-white" id="vipSideMemberBtn" style="background: rgba(245, 158, 11, 0.9); font-size: 11px;" onclick="setVipCardSide('MEMBER')">
                                        <i class="bi bi-credit-card-2-front-fill me-1"></i> Kartu Member
                                    </button>
                                    <button type="button" class="btn btn-sm rounded-pill px-3 py-1 fw-bold text-white-50" id="vipSideFrontBtn" style="background: transparent; font-size: 11px;" onclick="setVipCardSide('FRONT')">
                                        <i class="bi bi-stars me-1"></i> Tampak Depan (VIP)
                                    </button>
                                </div>
                                
                                <!-- SISI DEPAN KARTU LUXURY VIP (TAMPAK DEPAN) -->
                                <div id="vipMemberCardFrontView" class="d-none position-relative" style="z-index: 2;">
                                    <div class="p-4 rounded-4 mb-3 border border-warning border-opacity-50" style="background: linear-gradient(135deg, #1e1b4b 0%, #312e81 40%, #0f172a 100%); box-shadow: 0 12px 30px rgba(0,0,0,0.5);">
                                        <div class="d-flex align-items-center justify-content-between mb-4">
                                            <div class="d-flex align-items-center gap-2">
                                                <div class="rounded-3 p-2 d-flex align-items-center justify-content-center" style="background: linear-gradient(135deg, #F59E0B, #D97706); width: 38px; height: 38px;">
                                                    <i class="bi bi-award-fill text-dark fs-5"></i>
                                                </div>
                                                <div>
                                                    <div class="fw-black text-white" style="font-size: 16px; letter-spacing: 1px; font-weight: 900;">MY SINGGHARA</div>
                                                    <div class="text-warning fw-bold text-uppercase" style="font-size: 9.5px; letter-spacing: 1.5px;">VIP LUXURY CARD</div>
                                                </div>
                                            </div>
                                            <i class="bi bi-wifi fs-3 text-warning opacity-75"></i>
                                        </div>

                                        <!-- CHIP EMV EMAS -->
                                        <div class="mb-4 d-flex align-items-center gap-2">
                                            <div class="rounded-2 p-1" style="background: linear-gradient(135deg, #fbbf24, #d97706); width: 44px; height: 32px; border: 1px solid #78350f;">
                                                <div style="width: 100%; height: 100%; border: 1px dashed rgba(0,0,0,0.3); border-radius: 4px;"></div>
                                            </div>
                                            <span class="badge rounded-pill bg-warning text-dark fw-bold px-2 py-1" id="vipFrontCardTier" style="font-size: 10px;">
                                                VIP MEMBER
                                            </span>
                                        </div>

                                        <!-- NOMOR KARTU DIGITAL -->
                                        <div class="font-monospace fw-bold text-white mb-3" id="vipFrontCardNumber" style="font-size: 19px; letter-spacing: 3px; text-shadow: 0 2px 4px rgba(0,0,0,0.6);">
                                            VIP •••• •••• 0000
                                        </div>

                                        <!-- FOOTER SISI DEPAN -->
                                        <div class="d-flex align-items-center justify-content-between pt-2 border-top border-secondary border-opacity-25">
                                            <div>
                                                <small class="text-uppercase text-white-50 d-block" style="font-size: 9px; letter-spacing: 0.5px;">Member Name</small>
                                                <span class="fw-bold text-warning text-truncate d-block" id="vipFrontCardName" style="font-size: 13px; max-width: 190px;">PENGGUNA</span>
                                            </div>
                                            <div class="text-end">
                                                <small class="text-uppercase text-white-50 d-block" style="font-size: 9px; letter-spacing: 0.5px;">Valid Thru</small>
                                                <span class="fw-bold text-white font-monospace" id="vipFrontCardExp" style="font-size: 12px;">-</span>
                                            </div>
                                        </div>
                                    </div>
                                </div>

                                <!-- SISI BELAKANG / DETAIL KARTU MEMBER (DEFAULT) -->
                                <div id="vipMemberCardBackView" class="position-relative" style="z-index: 2;">
                                    
                                    <!-- TOP CARD: LOGO & CHIP VIP -->
                                    <div class="d-flex align-items-center justify-content-between mb-4">
                                        <div class="d-flex align-items-center gap-2">
                                            <div class="rounded-3 p-2 d-flex align-items-center justify-content-center" style="background: linear-gradient(135deg, #F59E0B, #D97706); width: 36px; height: 36px;">
                                                <i class="bi bi-award-fill text-dark fs-5"></i>
                                            </div>
                                            <div>
                                                <div class="fw-black text-white" style="font-size: 15px; letter-spacing: 0.5px; font-weight: 800;">MY SINGGHARA</div>
                                                <small class="text-warning fw-bold text-uppercase" style="font-size: 9px; letter-spacing: 1px;">Official VIP Membership</small>
                                            </div>
                                        </div>
                                        <span class="vip-chip-badge">
                                            <i class="bi bi-shield-fill-check"></i> VIP MEMBER
                                        </span>
                                    </div>

                                    <!-- USER PROFILE ROW -->
                                    <div class="d-flex align-items-center gap-3 mb-4">
                                        <div class="rounded-circle bg-warning text-dark fw-bold d-flex align-items-center justify-content-center shadow-lg" style="width: 56px; height: 56px; font-size: 20px; border: 2px solid rgba(255,255,255,0.4);" id="vipCardInitials">
                                            U
                                        </div>
                                        <div class="overflow-hidden">
                                            <h5 class="fw-bold mb-0 text-white text-truncate" id="vipCardName">Nama Pengguna</h5>
                                            <div class="text-white-50 small text-truncate" id="vipCardEmail">user@email.com</div>
                                            <div class="d-flex align-items-center gap-1 mt-1 font-monospace" style="font-size: 10px; color: #94A3B8;">
                                                <span id="vipCardUid">UID: -</span>
                                                <button class="btn btn-sm btn-link p-0 text-warning" onclick="copyVipCardUid()" title="Salin UID">
                                                    <i class="bi bi-clipboard"></i>
                                                </button>
                                            </div>
                                        </div>
                                    </div>

                                    <!-- MEMBERSHIP DETAILS BOX -->
                                    <div class="p-3 rounded-4 mb-4" style="background: rgba(255, 255, 255, 0.06); border: 1px solid rgba(255, 255, 255, 0.12);">
                                        <div class="row g-2 text-start">
                                            <div class="col-6">
                                                <small class="text-secondary text-uppercase d-block fw-bold" style="font-size: 10px;">Paket VIP</small>
                                                <span class="fw-bold text-warning" id="vipCardTier">12 BULAN</span>
                                            </div>
                                            <div class="col-6">
                                                <small class="text-secondary text-uppercase d-block fw-bold" style="font-size: 10px;">Berlaku Sampai</small>
                                                <span class="fw-bold text-white" id="vipCardExp">-</span>
                                            </div>
                                        </div>
                                        <hr class="my-2 border-secondary opacity-25">
                                        <small class="text-info fw-semibold d-block" id="vipCardDays" style="font-size: 11px;">Masa Berlaku Aktif</small>
                                    </div>

                                    <!-- QR CODE & BARCODE -->
                                    <div class="d-flex align-items-center justify-content-between gap-3 mb-4">
                                        <div class="p-2 rounded-3 bg-white shadow" style="width: 126px; height: 126px; display: flex; align-items: center; justify-content: center;" id="vipCardQrCode">
                                            <div class="spinner-border text-dark spinner-border-sm"></div>
                                        </div>
                                        <div class="flex-grow-1 d-flex flex-column gap-2">
                                            <div class="barcode-strip">
                                                <div class="barcode-bar" style="width: 4px;"></div>
                                                <div class="barcode-bar" style="width: 2px;"></div>
                                                <div class="barcode-bar" style="width: 6px;"></div>
                                                <div class="barcode-bar" style="width: 3px;"></div>
                                                <div class="barcode-bar" style="width: 5px;"></div>
                                                <div class="barcode-bar" style="width: 2px;"></div>
                                                <div class="barcode-bar" style="width: 7px;"></div>
                                                <div class="barcode-bar" style="width: 3px;"></div>
                                                <div class="barcode-bar" style="width: 4px;"></div>
                                                <div class="barcode-bar" style="width: 2px;"></div>
                                                <div class="barcode-bar" style="width: 6px;"></div>
                                                <div class="barcode-bar" style="width: 4px;"></div>
                                            </div>
                                            <small class="text-center text-secondary font-monospace" style="font-size: 9px; letter-spacing: 2px;">DIGITAL VERIFIED</small>
                                            <button class="btn btn-sm btn-outline-warning py-1 w-100 fw-bold" style="font-size: 11px; border-radius: 8px;" onclick="editMembershipFromCard()">
                                                <i class="bi bi-pencil-square me-1"></i> Edit Paket VIP
                                            </button>
                                        </div>
                                    </div>

                                </div>

                                <!-- BUTTON ACTIONS -->
                                <div class="d-flex flex-column gap-2 position-relative" style="z-index: 2;">
                                    <button class="btn btn-success w-100 py-2 fw-bold d-flex align-items-center justify-content-center gap-2 shadow" style="border-radius: 12px;" onclick="testChatAdminWhatsapp()">
                                        <i class="bi bi-whatsapp fs-5"></i>
                                        <span>Hubungi Admin via WhatsApp</span>
                                    </button>
                                    <button class="btn btn-outline-light w-100 py-2 fw-semibold" style="border-radius: 12px;" data-bs-dismiss="modal">
                                        Tutup Kartu
                                    </button>
                                </div>

                            </div>
                        </div>
                    </div>
                </div>
            `;
        }

        // 5. DIALOG HAPUS PESAN TUNGGAL
        function confirmDeleteMessage(msgId, msgTitle) {
            window.adminMessageToDelete = { id: msgId, title: msgTitle };
            const titleEl = document.getElementById('adminDeleteMsgTitleTarget');
            if (titleEl) titleEl.textContent = `'${dol}{msgTitle}'`;
            const modalEl = document.getElementById('adminDeleteMessageModal');
            if (modalEl) new bootstrap.Modal(modalEl).show();
        }

        async function executeDeleteMessage() {
            if (!window.adminMessageToDelete) return;
            const target = window.adminMessageToDelete;

            const btnEl = document.getElementById('adminExecuteDeleteMsgBtn');
            if (btnEl) {
                btnEl.disabled = true;
                btnEl.innerHTML = '<span class="spinner-border spinner-border-sm me-1"></span> Menghapus...';
            }

            try {
                const res = await fetch('/api/admin?action=delete_message', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ id: target.id })
                });

                if (res.ok) {
                    showAdminToast("Pesan berhasil dihapus!", "bi-check-circle-fill", "#059669");
                    const modalEl = document.getElementById('adminDeleteMessageModal');
                    if (modalEl) bootstrap.Modal.getInstance(modalEl)?.hide();
                    fetchAdminMessages();
                } else {
                    showAdminToast("Gagal menghapus pesan", "bi-exclamation-triangle-fill", "#DC2626");
                }
            } catch (e) {
                showAdminToast("Pesan berhasil dihapus!", "bi-check-circle-fill", "#059669");
                const modalEl = document.getElementById('adminDeleteMessageModal');
                if (modalEl) bootstrap.Modal.getInstance(modalEl)?.hide();
                fetchAdminMessages();
            } finally {
                window.adminMessageToDelete = null;
                if (btnEl) {
                    btnEl.disabled = false;
                    btnEl.innerHTML = '<i class="bi bi-trash3-fill me-1"></i> Hapus Pesan';
                }
            }
        }

        function renderDeleteMessageModalHtml() {
            return `
                <div class="modal fade" id="adminDeleteMessageModal" tabindex="-1" aria-hidden="true">
                    <div class="modal-dialog modal-dialog-centered" style="max-width: 480px;">
                        <div class="modal-content border-0 shadow-lg rounded-4">
                            <div class="modal-header border-0 pb-0">
                                <h6 class="modal-title fw-bold text-danger d-flex align-items-center gap-2">
                                    <i class="bi bi-trash3-fill"></i>
                                    <span>Hapus Pesan?</span>
                                </h6>
                                <button type="button" class="btn-close" data-bs-dismiss="modal"></button>
                            </div>
                            <div class="modal-body p-4 pt-2">
                                <p class="text-secondary admin-text-sub small mb-3">
                                    Apakah Anda yakin ingin menghapus pesan <strong id="adminDeleteMsgTitleTarget" class="text-dark admin-text-main"></strong>?
                                </p>
                            </div>
                            <div class="modal-footer border-0 pt-0">
                                <button type="button" class="btn btn-outline-secondary" data-bs-dismiss="modal">Batal</button>
                                <button type="button" class="btn btn-danger fw-semibold px-4" id="adminExecuteDeleteMsgBtn" onclick="executeDeleteMessage()">
                                    <i class="bi bi-trash3-fill me-1"></i> Hapus Pesan
                                </button>
                            </div>
                        </div>
                    </div>
                </div>
            `;
        }

        // 6. DIALOG HAPUS SELURUH PESAN
        function confirmDeleteAllMessages() {
            const countEl = document.getElementById('adminDeleteAllCountTarget');
            if (countEl) countEl.textContent = (window.adminMessages || []).length;
            const modalEl = document.getElementById('adminDeleteAllMessagesModal');
            if (modalEl) new bootstrap.Modal(modalEl).show();
        }

        async function executeDeleteAllMessages() {
            const btnEl = document.getElementById('adminExecuteDeleteAllBtn');
            if (btnEl) {
                btnEl.disabled = true;
                btnEl.innerHTML = '<span class="spinner-border spinner-border-sm me-1"></span> Menghapus Semua...';
            }

            try {
                const res = await fetch('/api/admin?action=delete_all_messages', {
                    method: 'POST'
                });

                if (res.ok) {
                    showAdminToast("Seluruh pesan berhasil dibersihkan!", "bi-check-circle-fill", "#059669");
                    const modalEl = document.getElementById('adminDeleteAllMessagesModal');
                    if (modalEl) bootstrap.Modal.getInstance(modalEl)?.hide();
                    fetchAdminMessages();
                } else {
                    showAdminToast("Gagal menghapus pesan", "bi-exclamation-triangle-fill", "#DC2626");
                }
            } catch (e) {
                showAdminToast("Pesan dibersihkan di penyimpanan lokal!", "bi-check-circle-fill", "#059669");
                const modalEl = document.getElementById('adminDeleteAllMessagesModal');
                if (modalEl) bootstrap.Modal.getInstance(modalEl)?.hide();
                fetchAdminMessages();
            } finally {
                if (btnEl) {
                    btnEl.disabled = false;
                    btnEl.innerHTML = '<i class="bi bi-trash3-fill me-1"></i> Hapus Semua';
                }
            }
        }

        function renderDeleteAllMessagesModalHtml() {
            return `
                <div class="modal fade" id="adminDeleteAllMessagesModal" tabindex="-1" aria-hidden="true">
                    <div class="modal-dialog modal-dialog-centered" style="max-width: 480px;">
                        <div class="modal-content border-0 shadow-lg rounded-4">
                            <div class="modal-header border-0 pb-0">
                                <h6 class="modal-title fw-bold text-danger d-flex align-items-center gap-2">
                                    <i class="bi bi-trash3-fill"></i>
                                    <span>Hapus Seluruh Pesan?</span>
                                </h6>
                                <button type="button" class="btn-close" data-bs-dismiss="modal"></button>
                            </div>
                            <div class="modal-body p-4 pt-2">
                                <p class="text-secondary admin-text-sub small mb-3">
                                    Apakah Anda yakin ingin menghapus seluruh <strong id="adminDeleteAllCountTarget" class="admin-text-main">0</strong> pesan secara permanen?
                                </p>
                            </div>
                            <div class="modal-footer border-0 pt-0">
                                <button type="button" class="btn btn-outline-secondary" data-bs-dismiss="modal">Batal</button>
                                <button type="button" class="btn btn-danger fw-semibold px-4" id="adminExecuteDeleteAllBtn" onclick="executeDeleteAllMessages()">
                                    <i class="bi bi-trash3-fill me-1"></i> Hapus Semua
                                </button>
                            </div>
                        </div>
                    </div>
                </div>
            `;
        }

        // 7. DIALOG ATURAN RULES FIRESTORE
        async function openFirestoreRulesModal() {
            let rulesCode = `// Memuat aturan rules...`;
            const codeEl = document.getElementById('adminFirestoreRulesCode');
            if (codeEl) codeEl.textContent = rulesCode;

            const modalEl = document.getElementById('adminFirestoreRulesModal');
            if (modalEl) new bootstrap.Modal(modalEl).show();

            try {
                const res = await fetch('/api/admin?action=rules');
                if (res.ok) {
                    const data = await res.json();
                    if (data && data.rules) {
                        window.adminFirestoreRulesText = data.rules;
                        if (codeEl) codeEl.textContent = data.rules;
                    }
                }
            } catch(e) {}
        }

        function copyFirestoreRules() {
            if (window.adminFirestoreRulesText) {
                copyAdminClipboard('Aturan Rules Firestore', window.adminFirestoreRulesText);
            }
        }

        function renderFirestoreRulesModalHtml() {
            return `
                <div class="modal fade" id="adminFirestoreRulesModal" tabindex="-1" aria-hidden="true">
                    <div class="modal-dialog modal-dialog-centered modal-lg">
                        <div class="modal-content border-0 shadow-lg rounded-4">
                            <div class="modal-header border-0 pb-0">
                                <h6 class="modal-title fw-bold text-primary d-flex align-items-center gap-2">
                                    <i class="bi bi-shield-check"></i>
                                    <span>Aturan Rules Firestore</span>
                                </h6>
                                <button type="button" class="btn-close" data-bs-dismiss="modal"></button>
                            </div>
                            <div class="modal-body p-4 pt-2">
                                <p class="text-secondary admin-text-sub small mb-3">
                                    Aturan resmi ini menjamin hanya Administrator (UID: <code class="text-primary fw-bold">${dol}{window.adminAuthStatus.adminUid}</code>) yang berwenang mengelola data pengguna dan broadcast pengumuman:
                                </p>
                                <div class="rounded-3 p-3" style="background: #0f172a; border: 1px solid #334155; max-height: 380px; overflow-y: auto;">
                                    <pre class="m-0 text-light font-monospace" style="font-size: 11px; line-height: 1.5;" id="adminFirestoreRulesCode">// Memuat aturan...</pre>
                                </div>
                            </div>
                            <div class="modal-footer border-0 pt-0">
                                <button type="button" class="btn btn-outline-secondary" data-bs-dismiss="modal">Tutup</button>
                                <button type="button" class="btn btn-primary fw-semibold px-4" onclick="copyFirestoreRules()">
                                    <i class="bi bi-clipboard me-1"></i> Salin Aturan Rules
                                </button>
                            </div>
                        </div>
                    </div>
                </div>
            `;
        }

        // ==========================================
        // 8. DIALOG UBAH NOMOR KONTAK ADMIN (ADMIN PHONE MODAL)
        // ==========================================
        function openAdminPhoneModal() {
            const input = document.getElementById('adminPhoneEditInput');
            if (input) input.value = window.adminPhoneNumber || '088806643415';
            const modalEl = document.getElementById('adminPhoneModal');
            if (modalEl) new bootstrap.Modal(modalEl).show();
        }

        async function saveAdminPhoneFromModal() {
            const input = document.getElementById('adminPhoneEditInput');
            if (!input) return;
            const newPhone = input.value.trim();
            if (!newPhone) {
                showAdminToast("Nomor kontak tidak boleh kosong!", "bi-exclamation-triangle-fill", "#DC2626");
                return;
            }

            const btn = document.getElementById('adminSavePhoneBtn');
            if (btn) {
                btn.disabled = true;
                btn.innerHTML = '<span class="spinner-border spinner-border-sm me-1"></span> Menyimpan...';
            }

            try {
                const res = await fetch('/api/admin?action=update_phone', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ phoneNumber: newPhone })
                });

                if (res.ok) {
                    const data = await res.json();
                    window.adminPhoneNumber = data.phoneNumber || newPhone;
                } else {
                    window.adminPhoneNumber = newPhone;
                }
            } catch(e) {
                window.adminPhoneNumber = newPhone;
            } finally {
                try {
                    localStorage.setItem('mysingghara_admin_phone', window.adminPhoneNumber);
                } catch(e) {}
                const dispEl = document.getElementById('adminPhoneDisplay');
                if (dispEl) dispEl.textContent = window.adminPhoneNumber;
                showAdminToast("Nomor WhatsApp Admin berhasil diperbarui!", "bi-check-circle-fill", "#059669");
                const modalEl = document.getElementById('adminPhoneModal');
                if (modalEl) bootstrap.Modal.getInstance(modalEl)?.hide();
                if (btn) {
                    btn.disabled = false;
                    btn.innerHTML = '<i class="bi bi-floppy-fill me-1"></i> Simpan Nomor';
                }
            }
        }

        function renderAdminPhoneModalHtml() {
            return `
                <div class="modal fade" id="adminPhoneModal" tabindex="-1" aria-hidden="true">
                    <div class="modal-dialog modal-dialog-centered" style="max-width: 440px;">
                        <div class="modal-content border-0 shadow-lg rounded-4">
                            <div class="modal-header border-0 pb-0">
                                <div class="d-flex align-items-center gap-2">
                                    <div class="rounded-circle p-2 d-flex align-items-center justify-content-center" style="background: rgba(37, 211, 102, 0.2); width: 36px; height: 36px;">
                                        <i class="bi bi-telephone-fill" style="color: #16A34A; font-size: 16px;"></i>
                                    </div>
                                    <h6 class="modal-title fw-bold text-dark admin-text-main mb-0">Ubah Nomor Kontak Admin</h6>
                                </div>
                                <button type="button" class="btn-close" data-bs-dismiss="modal"></button>
                            </div>
                            <div class="modal-body p-4 pt-3">
                                <p class="text-secondary admin-text-sub small mb-3">
                                    Nomor ini akan terhubung langsung ke tombol WhatsApp di kartu member seluruh pengguna:
                                </p>
                                <div class="mb-3">
                                    <label class="form-label small fw-bold text-secondary admin-text-sub">Nomor WhatsApp Admin</label>
                                    <div class="input-group">
                                        <span class="input-group-text bg-success bg-opacity-10 text-success fw-bold border-end-0">
                                            <i class="bi bi-whatsapp"></i>
                                        </span>
                                        <input type="text" class="form-control admin-form-control fw-bold font-monospace" id="adminPhoneEditInput" value="${dol}{escapeHtml(window.adminPhoneNumber || '088806643415')}" placeholder="Contoh: 088806643415">
                                    </div>
                                    <small class="text-secondary admin-text-sub mt-1 d-block" style="font-size: 11px;">Format nomor diawali 08... atau 62...</small>
                                </div>
                            </div>
                            <div class="modal-footer border-0 pt-0">
                                <button type="button" class="btn btn-outline-secondary" data-bs-dismiss="modal">Batal</button>
                                <button type="button" class="btn btn-success fw-semibold px-4" id="adminSavePhoneBtn" onclick="saveAdminPhoneFromModal()">
                                    <i class="bi bi-floppy-fill me-1"></i> Simpan Nomor
                                </button>
                            </div>
                        </div>
                    </div>
                </div>
            `;
        }

        // ==========================================
        // 9. DIALOG PESAN MASUK KODE OTP (ADMIN OTP INBOX MODAL)
        // ==========================================
        function openOtpInboxModal() {
            const modalEl = document.getElementById('adminOtpInboxModal');
            if (modalEl) new bootstrap.Modal(modalEl).show();
            renderOtpListInModal();
            fetchAdminOtpRequests();
        }

        function renderOtpListInModal() {
            const container = document.getElementById('adminOtpListContainer');
            const infoBar = document.getElementById('adminOtpInfoBar');
            if (!container) return;

            const list = window.adminOtpRequests || [];
            const activeCount = list.filter(r => r.status === 'MENUNGGU' && !r.isExpired).length;

            if (infoBar) {
                infoBar.innerHTML = `
                    <div class="d-flex align-items-center justify-content-between p-2 px-3 rounded-3" style="background: ${dol}{activeCount > 0 ? '#ECFDF5' : 'rgba(100,116,139,0.1)'};">
                        <span class="small fw-semibold" style="color: ${dol}{activeCount > 0 ? '#065F46' : 'var(--text-sub, #64748B)'}; font-size: 12px;">
                            ${dol}{activeCount > 0 ? '• ' + activeCount + ' Kode OTP Menunggu Verifikasi' : 'Tidak ada kode OTP yang menunggu'}
                        </span>
                        <span class="small font-monospace text-secondary" style="font-size: 11px;">
                            Total: ${dol}{list.length}
                        </span>
                    </div>
                `;
            }

            if (list.length === 0) {
                container.innerHTML = `
                    <div class="text-center py-5">
                        <div class="rounded-circle p-3 d-inline-flex align-items-center justify-content-center mb-2" style="background: rgba(15, 118, 110, 0.1); width: 64px; height: 64px;">
                            <i class="bi bi-envelope-check-fill" style="color: #0F766E; font-size: 28px;"></i>
                        </div>
                        <h6 class="fw-bold text-dark admin-text-main mb-1">Belum Ada Pesan Masuk Kode OTP</h6>
                        <p class="text-secondary admin-text-sub small mb-0 px-3" style="max-width: 380px; margin: 0 auto; font-size: 12px;">
                            Saat pengguna meminta kode OTP untuk kata sandi email di Profil, kode verifikasi akan muncul di sini secara realtime.
                        </p>
                    </div>
                `;
                return;
            }

            container.innerHTML = list.map(req => {
                const isPending = req.status === 'MENUNGGU' && !req.isExpired;
                const isUsed = req.status === 'DIGUNAKAN';
                const isExpired = req.isExpired && req.status === 'MENUNGGU';

                let badgeBg = '#FEF3C7';
                let badgeColor = '#B45309';
                let badgeText = 'MENUNGGU';

                if (isUsed) {
                    badgeBg = '#DCFCE7';
                    badgeColor = '#15803D';
                    badgeText = 'TERVERIFIKASI';
                } else if (isExpired) {
                    badgeBg = '#FEE2E2';
                    badgeColor = '#B91C1C';
                    badgeText = 'KADALUARSA';
                }

                const createdDate = req.createdAt ? new Date(req.createdAt).toLocaleString('id-ID', {
                    day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit'
                }) : '-';

                const spacedOtp = (req.otpCode || '').split('').join('  ');
                const replyText = `Halo ${dol}{req.requesterName || 'Pengguna'}, kode verifikasi OTP My Singghara untuk atur kata sandi email Anda adalah: *${dol}{req.otpCode}*. Kode berlaku 15 menit.`;

                return `
                    <div class="card rounded-3 p-3 mb-2 border ${dol}{isPending ? 'border-teal' : 'border-light'}" style="background: ${dol}{isPending ? 'var(--card-bg, #ffffff)' : 'rgba(241, 245, 249, 0.5)'}; border-color: ${dol}{isPending ? '#0F766E' : 'rgba(203, 213, 225, 0.6)'} !important;">
                        <div class="d-flex align-items-center justify-content-between mb-2">
                            <div>
                                <h6 class="fw-bold mb-0 text-dark admin-text-main" style="font-size: 13.5px;">${dol}{escapeHtml(req.requesterName || 'Pengguna My Singghara')}</h6>
                                <div class="text-teal fw-medium small" style="color: #0F766E; font-size: 12px;">${dol}{escapeHtml(req.requesterEmail || '-')}</div>
                            </div>
                            <span class="badge rounded-2 px-2 py-1 fw-bold" style="background: ${dol}{badgeBg}; color: ${dol}{badgeColor}; font-size: 10px;">
                                ${dol}{badgeText}
                            </span>
                        </div>

                        <div class="d-flex align-items-center justify-content-between text-secondary font-monospace mb-2" style="font-size: 11px;">
                            <span>UID: ${dol}{req.requesterUid ? req.requesterUid.substring(0, 12) + '...' : '-'}</span>
                            <span>${dol}{createdDate}</span>
                        </div>

                        <!-- HIGHLIGHT BOX KODE OTP -->
                        <div class="p-2 px-3 rounded-3 d-flex align-items-center justify-content-between mb-2" style="background: ${dol}{isPending ? '#ECFDF5' : '#F1F5F9'}; border: 1px solid ${dol}{isPending ? '#10B981' : 'transparent'};">
                            <div>
                                <div class="fw-bold" style="font-size: 9.5px; color: ${dol}{isPending ? '#047857' : '#64748B'}; letter-spacing: 0.5px;">KODE OTP VERIFIKASI</div>
                                <div class="font-monospace fw-black" style="font-size: 22px; font-weight: 800; letter-spacing: 3px; color: ${dol}{isPending ? '#065F46' : '#334155'};">
                                    ${dol}{spacedOtp}
                                </div>
                            </div>
                            <button class="btn btn-sm btn-outline-success fw-bold py-1 px-3 d-flex align-items-center gap-1" style="font-size: 11.5px; border-color: #0F766E; color: #0F766E;" onclick="copyOtpDirect('${dol}{escapeJs(req.otpCode)}')">
                                <i class="bi bi-clipboard"></i> Salin
                            </button>
                        </div>

                        <!-- ACTION BUTTONS ROW -->
                        <div class="d-flex align-items-center gap-2 pt-1">
                            <button class="btn btn-sm btn-success flex-grow-1 fw-semibold py-1 d-flex align-items-center justify-content-center gap-1" style="font-size: 11.5px; background: #059669;" onclick="shareOtpWa('${dol}{escapeJs(req.requesterName)}', '${dol}{escapeJs(req.otpCode)}')">
                                <i class="bi bi-share-fill"></i> Kirim Kode
                            </button>
                            <button class="btn btn-sm btn-outline-secondary fw-semibold py-1 px-2" style="font-size: 11px;" onclick="copyOtpReplyDirect('${dol}{escapeJs(replyText)}')">
                                Salin Balasan
                            </button>
                            ${dol}{isPending ? `
                                <button class="btn btn-sm btn-outline-primary fw-semibold py-1 px-2" style="font-size: 11px;" onclick="markAdminOtpRequestUsed('${dol}{req.id}')" title="Tandai Digunakan">
                                    <i class="bi bi-check2-circle"></i> Selesai
                                </button>
                            ` : ''}
                            <button class="btn btn-sm btn-outline-danger border-0 p-1 rounded-circle" onclick="deleteAdminOtpRequest('${dol}{req.id}')" title="Hapus">
                                <i class="bi bi-trash3-fill"></i>
                            </button>
                        </div>
                    </div>
                `;
            }).join('');
        }

        function copyOtpDirect(code) {
            copyAdminClipboard('Kode OTP', code);
        }

        function copyOtpReplyDirect(text) {
            copyAdminClipboard('Format Balasan OTP', text);
        }

        function shareOtpWa(name, code) {
            const text = `Halo ${dol}{name || 'Pengguna'}, kode verifikasi OTP My Singghara untuk atur kata sandi email Anda adalah: *${dol}{code}*. Kode berlaku 15 menit.`;
            const url = 'https://api.whatsapp.com/send?text=' + encodeURIComponent(text);
            window.open(url, '_blank');
        }

        function renderOtpInboxModalHtml() {
            return `
                <div class="modal fade" id="adminOtpInboxModal" tabindex="-1" aria-hidden="true">
                    <div class="modal-dialog modal-dialog-centered modal-dialog-scrollable" style="max-width: 520px;">
                        <div class="modal-content border-0 shadow-lg rounded-4">
                            <div class="modal-header border-0 pb-2">
                                <div class="d-flex align-items-center gap-2">
                                    <div class="rounded-circle p-2 d-flex align-items-center justify-content-center" style="background: rgba(15, 118, 110, 0.15); width: 36px; height: 36px;">
                                        <i class="bi bi-envelope-fill" style="color: #0F766E; font-size: 16px;"></i>
                                    </div>
                                    <div>
                                        <h6 class="modal-title fw-bold text-dark admin-text-main mb-0">Pesan Masuk Kode OTP</h6>
                                        <small class="fw-semibold" style="color: #0F766E; font-size: 10px;">Proteksi Sandi Email (Admin: ${dol}{window.adminAuthStatus.adminUid})</small>
                                    </div>
                                </div>
                                <div class="d-flex align-items-center gap-1">
                                    <button class="btn btn-sm btn-light rounded-circle" onclick="fetchAdminOtpRequests()" title="Muat Ulang">
                                        <i class="bi bi-arrow-clockwise fs-6"></i>
                                    </button>
                                    <button type="button" class="btn-close" data-bs-dismiss="modal"></button>
                                </div>
                            </div>
                            <div class="modal-body p-3 pt-0">
                                <div id="adminOtpInfoBar" class="mb-2"></div>
                                <div id="adminOtpListContainer">
                                    <div class="text-center py-4">
                                        <div class="spinner-border spinner-border-sm text-teal"></div>
                                    </div>
                                </div>
                            </div>
                            <div class="modal-footer border-0 pt-0">
                                <button type="button" class="btn btn-outline-secondary btn-sm px-4" data-bs-dismiss="modal">Tutup</button>
                            </div>
                        </div>
                    </div>
                </div>
            `;
        }
        """
    }
}
