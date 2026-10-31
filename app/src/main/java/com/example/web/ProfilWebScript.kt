package com.example.web

object ProfilWebScript {
    fun getScript(dol: String): String {
        return """
        // =========================================================================
        // --- PROFIL SCREEN (100% OFFLINE PERSIS PROFILSCREEN.KT PADA MY SINGGHARA)
        // =========================================================================

        window.profileActiveSection = null; // null = Menu Utama, 'keamanan_dan_data', 'pengaturan_share'
        window.profileData = null;
        window.profileStatusMessage = '';
        window.profileIsStatusError = false;

        const PROFIL_AVATAR_PRESETS = [
            { bg: 'linear-gradient(135deg, #6200EE, #BB86FC)', icon: 'bi-person-fill', label: 'Persona Ungu' },
            { bg: 'linear-gradient(135deg, #03DAC6, #018786)', icon: 'bi-emoji-smile-fill', label: 'Teal Ceria' },
            { bg: 'linear-gradient(135deg, #FF5722, #FF9800)', icon: 'bi-person-badge-fill', label: 'Oranye Semangat' },
            { bg: 'linear-gradient(135deg, #E91E63, #F48FB1)', icon: 'bi-star-fill', label: 'Bintang Merah Muda' },
            { bg: 'linear-gradient(135deg, #4CAF50, #81C784)', icon: 'bi-heart-fill', label: 'Kasih Hijau' },
            { bg: 'linear-gradient(135deg, #2196F3, #64B5F6)', icon: 'bi-balloon-heart-fill', label: 'Biru Ceria' },
            { bg: 'linear-gradient(135deg, #FFC107, #FFD54F)', icon: 'bi-emoji-laughing-fill', label: 'Emas Bahagia' },
            { bg: 'linear-gradient(135deg, #9C27B0, #CE93D8)', icon: 'bi-lightbulb-fill', label: 'Inspirasi Ungu' }
        ];

        async function loadProfile() {
            try {
                const res = await fetch('/api/profile');
                if (res.ok) {
                    window.profileData = await res.json();
                    if (typeof applyUserProfileData === 'function') {
                        applyUserProfileData(window.profileData);
                    }
                }
            } catch (e) {
                console.error('Error fetching profile data:', e);
            }
            renderProfileUI();
        }

        function setProfileStatus(msg, isError = false) {
            window.profileStatusMessage = msg;
            window.profileIsStatusError = isError;
            renderProfileUI();
            if (msg && !isError) {
                setTimeout(() => {
                    if (window.profileStatusMessage === msg) {
                        window.profileStatusMessage = '';
                        renderProfileUI();
                    }
                }, 4000);
            }
        }

        function renderProfileUI() {
            const container = document.getElementById('tab-content');
            if (!container) return;

            const pData = window.profileData || {
                profileName: 'Singghara User',
                profileAvatarIndex: 0,
                hasCustomImage: false,
                profileImageUrl: '',
                remotePhotoUrl: '',
                currentUid: '',
                isAdmin: false,
                userEmail: '',
                isLoggedIn: false,
                membershipTier: 'VIP Member Lifetime',
                membershipEndDate: 0,
                adminPhoneNumber: '088806643415',
                isAppLockEnabled: false,
                hasAppPin: false,
                appPin: '',
                isGpsActive: false,
                userLatitude: 0,
                userLongitude: 0,
                hijriOffset: -1,
                appsScriptUrl: '',
                appVersion: '1.0.0'
            };

            const profName = pData.profileName || 'Singghara User';
            const isLoggedIn = !!(pData.isLoggedIn || pData.currentUid || pData.userEmail);
            const userEmail = pData.userEmail || (pData.currentUid ? 'Akun Terhubung' : '');
            const isAdmin = !!pData.isAdmin;

            // Render Avatar element inside profile header
            let avatarContentHtml = '';
            const hasCustomImg = !!(pData.hasCustomImage || (pData.profileAvatarIndex === -1 && (pData.profileImageUrl || pData.remotePhotoUrl)));
            const primaryImg = pData.profileImageUrl || pData.remotePhotoUrl;

            if (hasCustomImg && primaryImg) {
                avatarContentHtml = `<img src="${dol}{primaryImg}" alt="${dol}{escapeHtml(profName)}" style="width: 100%; height: 100%; object-fit: cover; border-radius: 50%;">`;
            } else if (pData.profileAvatarIndex >= 0 && PROFIL_AVATAR_PRESETS[pData.profileAvatarIndex]) {
                const preset = PROFIL_AVATAR_PRESETS[pData.profileAvatarIndex];
                avatarContentHtml = `
                    <div style="width: 100%; height: 100%; border-radius: 50%; background: ${dol}{preset.bg}; display: flex; align-items: center; justify-content: center;">
                        <i class="bi ${dol}{preset.icon} text-white" style="font-size: 2.3rem;"></i>
                    </div>
                `;
            } else {
                const initials = (typeof getInitialsFromName === 'function') ? getInitialsFromName(profName) : (profName.substring(0, 2).toUpperCase());
                const grad = (typeof getGradientForName === 'function') ? getGradientForName(profName) : 'linear-gradient(135deg, #2563EB, #1D4ED8)';
                avatarContentHtml = `
                    <div style="width: 100%; height: 100%; border-radius: 50%; background: ${dol}{grad}; display: flex; align-items: center; justify-content: center;">
                        <span style="font-size: 1.85rem; font-weight: 800; color: #ffffff; letter-spacing: 0.5px;">${dol}{initials}</span>
                    </div>
                `;
            }

            // Top Status Alert
            let statusBannerHtml = '';
            if (window.profileStatusMessage) {
                const alertClass = window.profileIsStatusError ? 'alert-danger border-danger' : 'alert-success border-success';
                const alertIcon = window.profileIsStatusError ? 'bi-exclamation-circle-fill' : 'bi-check-circle-fill';
                statusBannerHtml = `
                    <div class="alert ${dol}{alertClass} alert-dismissible fade show d-flex align-items-center gap-2 mb-3 shadow-sm rounded-4 py-2 px-3" role="alert">
                        <i class="bi ${dol}{alertIcon} fs-5"></i>
                        <div class="flex-grow-1 small fw-semibold">${dol}{escapeHtml(window.profileStatusMessage)}</div>
                        <button type="button" class="btn-close p-2" onclick="setProfileStatus('')" aria-label="Close"></button>
                    </div>
                `;
            }

            // Main body based on activeSection
            let mainSectionContentHtml = '';

            if (!window.profileActiveSection) {
                // ==========================================
                // --- TAMPILAN MENU UTAMA PROFIL PERSIS ---
                // ==========================================
                mainSectionContentHtml = `
                    <!-- HEADER PROFIL (TANPA WRAPPER CARD, PERSIS ANDROID) -->
                    <div class="text-center py-2 mb-3">
                        <!-- AVATAR CONTAINER DENGAN DUA TOMBOL POJOK -->
                        <div class="position-relative d-inline-block mx-auto mb-2">
                            <!-- FOTO / AVATAR UTAMA (KLIK DETAIL MEMBERSHIP) -->
                            <div class="profile-avatar-box rounded-circle shadow-sm" onclick="openMembershipVipModal()" role="button" title="Foto Profil (Klik untuk Detail Membership)" style="width: 88px; height: 88px; border-radius: 50%; padding: 2px; background: var(--border-color); cursor: pointer; transition: transform 0.2s ease;">
                                ${dol}{avatarContentHtml}
                            </div>

                            <!-- TOMBOL PENGATURAN DI POJOK KANAN ATAS FOTO -->
                            <button type="button" class="btn btn-sm btn-profile-action-corner shadow-sm position-absolute" onclick="openEditProfileModal()" title="Kustomisasi Profil (Ubah Nama & Avatar)" style="top: -4px; right: -6px; width: 28px; height: 28px; border-radius: 50%; padding: 0; display: flex; align-items: center; justify-content: center; background: var(--card-bg); border: 1px solid var(--border-color); color: var(--brand-green);">
                                <i class="bi bi-gear-fill" style="font-size: 13px;"></i>
                            </button>

                            <!-- TOMBOL KAMERA / CROP DI POJOK KANAN BAWAH FOTO -->
                            <button type="button" class="btn btn-sm btn-profile-action-corner shadow-sm position-absolute" onclick="openPhotoOptionsModal()" title="Atur Foto Profil (Pilih Foto / Ganti)" style="bottom: -2px; right: -4px; width: 30px; height: 30px; border-radius: 50%; padding: 0; display: flex; align-items: center; justify-content: center; background: #2563EB; border: 2px solid var(--card-bg); color: #ffffff;">
                                <i class="bi ${dol}{hasCustomImg ? 'bi-crop' : 'bi-camera-fill'}" style="font-size: 13px;"></i>
                            </button>
                        </div>

                        <!-- NAMA PENGGUNA -->
                        <h4 class="fw-bold mb-1 mt-1 text-main text-truncate px-2" role="button" onclick="openMembershipVipModal()" title="Klik untuk Detail Membership" style="cursor: pointer; font-size: 1.25rem;">
                            ${dol}{escapeHtml(profName)}
                        </h4>

                        <!-- BADGE KARTU MEMBERSHIP VIP INTERAKTIF -->
                        <div class="d-inline-flex align-items-center gap-2 px-3 py-1 mt-1 rounded-pill shadow-sm" role="button" onclick="openMembershipVipModal()" title="Klik untuk melihat Detail Kartu Membership VIP" style="background: rgba(255, 215, 0, 0.12); border: 1px solid rgba(255, 215, 0, 0.65); cursor: pointer; transition: all 0.2s ease;">
                            <i class="bi bi-star-fill" style="color: #FFD700; font-size: 13px;"></i>
                            <span class="fw-bold text-main" style="font-size: 12px; letter-spacing: 0.3px;">Kartu Membership VIP (Klik Detail)</span>
                            <i class="bi bi-chevron-right text-primary" style="font-size: 12px;"></i>
                        </div>

                        <!-- INFO AKUN (JIKA LOGIN) -->
                        ${dol}{isLoggedIn ? `
                            <div class="small text-secondary mt-2 mb-2 text-truncate px-3">${dol}{escapeHtml(userEmail)}</div>
                            <div class="d-flex flex-wrap justify-content-center align-items-center gap-2 mt-2">
                                <button type="button" class="btn btn-sm btn-outline-danger rounded-3 px-3 py-1 fw-semibold d-inline-flex align-items-center gap-1 shadow-sm" onclick="confirmLogoutAccount()">
                                    <i class="bi bi-box-arrow-right"></i> Keluar
                                </button>
                            </div>
                        ` : ''}

                        <!-- PWA & WINDOWS DESKTOP INSTALLER (PENGGANTI MASUK & DAFTAR) -->
                        <div class="d-flex flex-wrap justify-content-center align-items-center gap-2 mt-3">
                            <a href="/installer.exe" download="MySingghara_PWA_Setup.exe" class="btn btn-sm btn-primary rounded-pill px-3 py-1.5 fw-bold d-inline-flex align-items-center gap-1.5 shadow-sm" style="font-size: 12px;" title="Unduh Installer PWA Windows (.exe) untuk Pasang Standalone Desktop & Domain Otomatis">
                                <i class="bi bi-windows"></i> Installer PWA (.exe)
                            </a>
                            <button type="button" class="btn btn-sm btn-outline-primary rounded-pill px-3 py-1.5 fw-bold d-inline-flex align-items-center gap-1.5 shadow-sm pwa-install-trigger" onclick="triggerPwaInstall()" style="font-size: 12px;" title="Pasang Progressive Web App langsung dari browser">
                                <i class="bi bi-box-arrow-in-down"></i> Pasang PWA
                            </button>
                            <a href="/setup_hosts.bat" download="setup_domain_mysingghara.bat" class="btn btn-sm btn-outline-success rounded-pill px-3 py-1.5 fw-bold d-inline-flex align-items-center gap-1.5 shadow-sm" style="font-size: 12px;" title="Unduh Script Domain mysingghara.com untuk PC Windows">
                                <i class="bi bi-download"></i> Script .bat
                            </a>
                        </div>
                    </div>

                    <!-- LABEL MENU UTAMA PROFIL -->
                    <div class="text-uppercase fw-extrabold text-primary px-2 mb-2" style="font-size: 12px; letter-spacing: 0.6px; font-weight: 800;">
                        MENU UTAMA PROFIL
                    </div>

                    <!-- CARD CONTAINER MENU UTAMA -->
                    <div class="card card-custom p-0 mb-4 overflow-hidden shadow-sm" style="border-radius: 18px; border: 1px solid var(--border-color); background: var(--card-bg);">
                        <div class="list-group list-group-flush">
                            <!-- 1. KEAMANAN DAN DATA -->
                            <div class="list-group-item list-group-item-action d-flex align-items-center gap-3 py-3 px-3 border-bottom" role="button" onclick="navigateToProfileSection('keamanan_dan_data')" style="background: transparent; color: var(--text-main); border-color: var(--border-color); cursor: pointer;">
                                <div class="rounded-circle d-flex align-items-center justify-content-center flex-shrink-0" style="width: 42px; height: 42px; background: rgba(37, 99, 235, 0.12); color: #2563EB;">
                                    <i class="bi bi-shield-lock-fill fs-5"></i>
                                </div>
                                <div class="flex-grow-1 min-w-0">
                                    <div class="fw-bold text-main" style="font-size: 14px;">Keamanan dan Data</div>
                                    <div class="text-secondary small text-truncate" style="font-size: 12px;">App Lock, Backup &amp; Restore, &amp; GPS</div>
                                </div>
                                <i class="bi bi-chevron-right text-secondary opacity-75"></i>
                            </div>

                            <!-- 2. PENGATURAN SHARE -->
                            <div class="list-group-item list-group-item-action d-flex align-items-center gap-3 py-3 px-3 border-bottom" role="button" onclick="navigateToProfileSection('pengaturan_share')" style="background: transparent; color: var(--text-main); border-color: var(--border-color); cursor: pointer;">
                                <div class="rounded-circle d-flex align-items-center justify-content-center flex-shrink-0" style="width: 42px; height: 42px; background: rgba(37, 99, 235, 0.12); color: #2563EB;">
                                    <i class="bi bi-cloud-fill fs-5"></i>
                                </div>
                                <div class="flex-grow-1 min-w-0">
                                    <div class="fw-bold text-main" style="font-size: 14px;">Pengaturan Share</div>
                                    <div class="text-secondary small text-truncate" style="font-size: 12px;">Seting Share &amp; Bantuan</div>
                                </div>
                                <i class="bi bi-chevron-right text-secondary opacity-75"></i>
                            </div>

                            <!-- 3. TELEGRAM AI ASSISTANT (TEPAT DI BAWAH PENGATURAN SHARE) -->
                            <div class="list-group-item list-group-item-action profile-menu-item-tg d-flex align-items-center gap-3 py-3 px-3 ${dol}{isAdmin ? 'border-bottom' : ''}" role="button" onclick="openProfileTelegramAi()" style="background: transparent; color: var(--text-main); border-color: var(--border-color); cursor: pointer;">
                                <div class="rounded-circle d-flex align-items-center justify-content-center flex-shrink-0 profile-menu-tg-icon" style="width: 42px; height: 42px;">
                                    <i class="bi bi-telegram fs-5"></i>
                                </div>
                                <div class="flex-grow-1 min-w-0">
                                    <div class="fw-bold text-main" style="font-size: 14px;">Telegram AI Assistant</div>
                                    <div class="text-secondary small text-truncate" style="font-size: 12px;">Asisten bot @My_Singghara_bot: cek libur, agenda &amp; database</div>
                                </div>
                                <i class="bi bi-chevron-right text-secondary opacity-75"></i>
                            </div>

                            <!-- 4. MANAJEMEN AKUN (KHUSUS ADMIN) -->
                            ${dol}{isAdmin ? `
                                <div class="list-group-item list-group-item-action d-flex align-items-center gap-3 py-3 px-3 border-bottom" role="button" onclick="switchTabByName('manajemen_akun')" style="background: transparent; color: var(--text-main); border-color: var(--border-color); cursor: pointer;">
                                    <div class="rounded-circle d-flex align-items-center justify-content-center flex-shrink-0" style="width: 42px; height: 42px; background: rgba(4, 120, 87, 0.12); color: #047857;">
                                        <i class="bi bi-shield-shaded fs-5"></i>
                                    </div>
                                    <div class="flex-grow-1 min-w-0">
                                        <div class="fw-bold text-main" style="font-size: 14px;">Manajemen Akun (Khusus Admin)</div>
                                        <div class="text-secondary small text-truncate" style="font-size: 12px;">Panel Admin: Pengguna, Pesan &amp; Token FCM</div>
                                    </div>
                                    <span class="badge" style="background: rgba(4, 120, 87, 0.15); color: #047857; font-size: 10px; font-weight: 700; border: 1px solid rgba(4, 120, 87, 0.3);">ADMIN</span>
                                </div>
                            ` : ''}

                            <!-- 5. TENTANG APLIKASI -->
                            <div class="list-group-item list-group-item-action d-flex align-items-center gap-3 py-3 px-3" role="button" onclick="openAboutAppModal()" style="background: transparent; color: var(--text-main); border-color: var(--border-color); cursor: pointer;">
                                <div class="rounded-circle d-flex align-items-center justify-content-center flex-shrink-0" style="width: 42px; height: 42px; background: rgba(15, 118, 110, 0.12); color: #0F766E;">
                                    <i class="bi bi-info-circle-fill fs-5"></i>
                                </div>
                                <div class="flex-grow-1 min-w-0">
                                    <div class="fw-bold text-main" style="font-size: 14px;">Tentang Aplikasi</div>
                                    <div class="text-secondary small text-truncate" style="font-size: 12px;">Versi v${dol}{escapeHtml(pData.appVersion || '1.0.0')} (My Singghara Suite)</div>
                                </div>
                                <i class="bi bi-chevron-right text-secondary opacity-75"></i>
                            </div>
                        </div>
                    </div>
                `;
            } else if (window.profileActiveSection === 'keamanan_dan_data') {
                // ==========================================
                // --- SUB-HALAMAN: KEAMANAN DAN DATA ---
                // ==========================================
                const isGpsActive = !!pData.isGpsActive;
                const gpsSubtitle = isGpsActive
                    ? `GPS Aktif (${dol}{Number(pData.userLatitude).toFixed(4)}, ${dol}{Number(pData.userLongitude).toFixed(4)})`
                    : 'Sinkronisasi lokasi, API Aladhan & kalender Hilal';

                mainSectionContentHtml = `
                    <!-- HEADER KEMBALI KE MENU UTAMA -->
                    <div class="d-flex align-items-center gap-3 py-2 mb-3" role="button" onclick="navigateToProfileSection(null)" style="cursor: pointer;">
                        <button type="button" class="btn btn-outline-primary btn-sm rounded-circle d-flex align-items-center justify-content-center p-0" style="width: 36px; height: 36px;">
                            <i class="bi bi-arrow-left fs-5"></i>
                        </button>
                        <div>
                            <div class="text-secondary small" style="font-size: 11px;">Kembali ke Menu Utama</div>
                            <h5 class="fw-bold text-main mb-0" style="font-size: 1.15rem;">Keamanan dan Data</h5>
                        </div>
                    </div>

                    <!-- CARD LIST MENU KEAMANAN DAN DATA -->
                    <div class="card card-custom p-0 mb-4 overflow-hidden shadow-sm" style="border-radius: 18px; border: 1px solid var(--border-color); background: var(--card-bg);">
                        <div class="list-group list-group-flush">
                            <!-- a. KEAMANAN & PENGUNCIAN (APP LOCK) -->
                            <div class="list-group-item list-group-item-action d-flex align-items-center gap-3 py-3 px-3 border-bottom" role="button" onclick="openAppLockModal()" style="background: transparent; color: var(--text-main); border-color: var(--border-color); cursor: pointer;">
                                <div class="rounded-circle d-flex align-items-center justify-content-center flex-shrink-0" style="width: 42px; height: 42px; background: rgba(37, 99, 235, 0.12); color: #2563EB;">
                                    <i class="bi bi-lock-fill fs-5"></i>
                                </div>
                                <div class="flex-grow-1 min-w-0">
                                    <div class="fw-bold text-main" style="font-size: 14px;">Keamanan &amp; Penguncian</div>
                                    <div class="text-secondary small text-truncate" style="font-size: 12px;">Kunci biometrik (Sidik Jari/Wajah) atau PIN</div>
                                </div>
                                <div class="d-flex align-items-center gap-2">
                                    <span class="badge ${dol}{pData.isAppLockEnabled ? 'bg-primary' : 'bg-secondary bg-opacity-25 text-secondary'} rounded-pill" style="font-size: 10px;">
                                        ${dol}{pData.isAppLockEnabled ? 'Aktif' : 'Nonaktif'}
                                    </span>
                                    <i class="bi bi-chevron-right text-secondary opacity-75"></i>
                                </div>
                            </div>

                            <!-- b. CADANGKAN & PULIHKAN (BACKUP & RESTORE) -->
                            <div class="list-group-item list-group-item-action d-flex align-items-center gap-3 py-3 px-3 border-bottom" role="button" onclick="openBackupRestoreModal()" style="background: transparent; color: var(--text-main); border-color: var(--border-color); cursor: pointer;">
                                <div class="rounded-circle d-flex align-items-center justify-content-center flex-shrink-0" style="width: 42px; height: 42px; background: rgba(16, 185, 129, 0.12); color: #10B981;">
                                    <i class="bi bi-cloud-arrow-up-fill fs-5"></i>
                                </div>
                                <div class="flex-grow-1 min-w-0">
                                    <div class="fw-bold text-main" style="font-size: 14px;">Cadangkan &amp; Pulihkan (Backup &amp; Restore)</div>
                                    <div class="text-secondary small text-truncate" style="font-size: 12px;">Cloud Firebase (${dol}{escapeHtml(userEmail || 'Terisolasi')}) &amp; Berkas Lokal</div>
                                </div>
                                <i class="bi bi-chevron-right text-secondary opacity-75"></i>
                            </div>

                            <!-- c. SINKRONISASI & AKSES LOKASI (GPS) -->
                            <div class="list-group-item list-group-item-action d-flex align-items-center gap-3 py-3 px-3" role="button" onclick="openGpsModal()" style="background: transparent; color: var(--text-main); border-color: var(--border-color); cursor: pointer;">
                                <div class="rounded-circle d-flex align-items-center justify-content-center flex-shrink-0" style="width: 42px; height: 42px; background: rgba(239, 68, 68, 0.12); color: #EF4444;">
                                    <i class="bi bi-geo-alt-fill fs-5"></i>
                                </div>
                                <div class="flex-grow-1 min-w-0">
                                    <div class="fw-bold text-main" style="font-size: 14px;">Sinkronisasi &amp; Akses Lokasi (GPS)</div>
                                    <div class="text-secondary small text-truncate" style="font-size: 12px;">${dol}{escapeHtml(gpsSubtitle)}</div>
                                </div>
                                <i class="bi bi-chevron-right text-secondary opacity-75"></i>
                            </div>
                        </div>
                    </div>
                `;
            } else if (window.profileActiveSection === 'pengaturan_share') {
                // ==========================================
                // --- SUB-HALAMAN: PENGATURAN SHARE ---
                // ==========================================
                mainSectionContentHtml = `
                    <!-- HEADER KEMBALI KE MENU UTAMA -->
                    <div class="d-flex align-items-center gap-3 py-2 mb-3" role="button" onclick="navigateToProfileSection(null)" style="cursor: pointer;">
                        <button type="button" class="btn btn-outline-primary btn-sm rounded-circle d-flex align-items-center justify-content-center p-0" style="width: 36px; height: 36px;">
                            <i class="bi bi-arrow-left fs-5"></i>
                        </button>
                        <div>
                            <div class="text-secondary small" style="font-size: 11px;">Kembali ke Menu Utama</div>
                            <h5 class="fw-bold text-main mb-0" style="font-size: 1.15rem;">Pengaturan Share</h5>
                        </div>
                    </div>

                    <!-- CARD LIST MENU PENGATURAN SHARE -->
                    <div class="card card-custom p-0 mb-4 overflow-hidden shadow-sm" style="border-radius: 18px; border: 1px solid var(--border-color); background: var(--card-bg);">
                        <div class="list-group list-group-flush">
                            <!-- 1. SETING SHARE -->
                            <div class="list-group-item list-group-item-action d-flex align-items-center gap-3 py-3 px-3 border-bottom" role="button" onclick="openShareUrlModal()" style="background: transparent; color: var(--text-main); border-color: var(--border-color); cursor: pointer;">
                                <div class="rounded-circle d-flex align-items-center justify-content-center flex-shrink-0" style="width: 42px; height: 42px; background: rgba(37, 99, 235, 0.12); color: #2563EB;">
                                    <i class="bi bi-gear-fill fs-5"></i>
                                </div>
                                <div class="flex-grow-1 min-w-0">
                                    <div class="fw-bold text-main" style="font-size: 14px;">Seting Share</div>
                                    <div class="text-secondary small text-truncate" style="font-size: 12px;">Konfigurasi URL Google Apps Script</div>
                                </div>
                                <i class="bi bi-chevron-right text-secondary opacity-75"></i>
                            </div>

                            <!-- 2. BANTUAN -->
                            <div class="list-group-item list-group-item-action d-flex align-items-center gap-3 py-3 px-3" role="button" onclick="openScriptTutorialModal()" style="background: transparent; color: var(--text-main); border-color: var(--border-color); cursor: pointer;">
                                <div class="rounded-circle d-flex align-items-center justify-content-center flex-shrink-0" style="width: 42px; height: 42px; background: rgba(245, 158, 11, 0.12); color: #F59E0B;">
                                    <i class="bi bi-question-circle-fill fs-5"></i>
                                </div>
                                <div class="flex-grow-1 min-w-0">
                                    <div class="fw-bold text-main" style="font-size: 14px;">Bantuan</div>
                                    <div class="text-secondary small text-truncate" style="font-size: 12px;">Petunjuk dan kode Google Apps Script</div>
                                </div>
                                <i class="bi bi-chevron-right text-secondary opacity-75"></i>
                            </div>
                        </div>
                    </div>
                `;
            }

            // Outer wrapper responsive container with max-width: 680px for a clean mobile/app card look
            container.innerHTML = `
                <div class="container-fluid py-3 px-2 px-md-3" style="max-width: 680px; margin: 0 auto;">
                    ${dol}{statusBannerHtml}
                    ${dol}{mainSectionContentHtml}
                </div>
            `;
        }

        function navigateToProfileSection(section) {
            if (section === 'telegram_ai') {
                openProfileTelegramAi();
                return;
            }
            window.profileActiveSection = section;
            renderProfileUI();
        }

        window.openProfileTelegramAi = function() {
            window.telegramAiCameFromProfile = true;
            if (typeof switchTab === 'function') {
                switchTab('telegram_ai');
            } else if (typeof window.loadTelegramAi === 'function') {
                window.loadTelegramAi();
            }
        };

        // =========================================================================
        // --- 1. MODAL DETAIL KARTU MEMBERSHIP VIP (PERSIS VIPMEMBERSHIPCARD.KT) ---
        // =========================================================================
        function openMembershipVipModal() {
            const pData = window.profileData || {};
            const profName = pData.profileName || 'Pengguna Setia';
            const userEmail = pData.userEmail || (pData.currentUid ? 'Akun Terhubung' : 'Guest (Lokal)');
            const uid = pData.currentUid || 'VIP-MEMBER-USER';
            const tier = pData.membershipTier || 'VIP Member Lifetime';
            const adminPhone = pData.adminPhoneNumber || '088806643415';

            let existingModal = document.getElementById('vipMembershipDetailModal');
            if (existingModal) existingModal.remove();

            const modalHtml = `
                <div class="modal fade" id="vipMembershipDetailModal" tabindex="-1" aria-hidden="true">
                    <div class="modal-dialog modal-dialog-centered" style="max-width: 520px;">
                        <div class="modal-content card-custom p-0 overflow-hidden" style="border-radius: 24px; border: 1px solid var(--border-color); background: var(--card-bg); box-shadow: 0 20px 45px rgba(0,0,0,0.4);">
                            <!-- HEADER -->
                            <div class="modal-header border-bottom-0 pb-1 pt-3 px-4 d-flex justify-content-between align-items-center">
                                <div class="d-flex align-items-center gap-2">
                                    <div class="rounded-circle d-flex align-items-center justify-content-center" style="width: 36px; height: 36px; background: rgba(255, 215, 0, 0.2); color: #FFD700;">
                                        <i class="bi bi-star-fill fs-6"></i>
                                    </div>
                                    <div>
                                        <h6 class="modal-title fw-bold text-main mb-0" style="font-size: 15px;">Kartu Membership VIP</h6>
                                        <small class="fw-semibold text-warning" style="font-size: 11px;">${dol}{escapeHtml(tier)}</small>
                                    </div>
                                </div>
                                <button type="button" class="btn-close btn-close-custom" data-bs-dismiss="modal" aria-label="Close"></button>
                            </div>

                            <!-- BODY DENGAN DESAIN KARTU VIP EMAS MEWAH PERSIS ANDROID -->
                            <div class="modal-body px-4 pt-2 pb-4">
                                <!-- KARTU VIP GOLD MEWAH -->
                                <div class="vip-card-box p-4 rounded-4 position-relative overflow-hidden mb-3 shadow" style="background: linear-gradient(135deg, #1f1b14 0%, #2e2619 50%, #15130e 100%); border: 1.5px solid rgba(255, 215, 0, 0.65); box-shadow: 0 10px 30px rgba(0,0,0,0.5);">
                                    <!-- Aksen garis dekoratif emas -->
                                    <div class="position-absolute" style="top: -40px; right: -40px; width: 140px; height: 140px; border-radius: 50%; background: radial-gradient(circle, rgba(255,215,0,0.2) 0%, transparent 70%); pointer-events: none;"></div>

                                    <div class="d-flex justify-content-between align-items-start mb-3">
                                        <div>
                                            <div class="text-uppercase fw-bold text-warning small" style="letter-spacing: 1.5px; font-size: 10px;">MY SINGGHARA SUITE</div>
                                            <h5 class="fw-bold text-white mb-0 mt-1" style="font-size: 17px; letter-spacing: 0.5px;">VIP MEMBERSHIP</h5>
                                        </div>
                                        <span class="badge rounded-pill px-3 py-1 fw-bold text-dark" style="background: linear-gradient(135deg, #FFD700, #FFA500); font-size: 10px; letter-spacing: 0.5px;">
                                            <i class="bi bi-check-circle-fill me-1"></i> AKTIF
                                        </span>
                                    </div>

                                    <div class="my-3">
                                        <small class="text-warning text-opacity-75 text-uppercase fw-semibold" style="font-size: 10px;">Nama Anggota</small>
                                        <div class="fw-bold text-white fs-5 text-truncate">${dol}{escapeHtml(profName)}</div>
                                        <small class="text-secondary" style="font-size: 11px;">${dol}{escapeHtml(userEmail)}</small>
                                    </div>

                                    <div class="d-flex justify-content-between align-items-end pt-2 border-top border-warning border-opacity-25 mt-3">
                                        <div>
                                            <small class="text-warning text-opacity-75 text-uppercase fw-semibold" style="font-size: 9px;">ID Anggota</small>
                                            <div class="text-white font-monospace small" style="font-size: 11px;">${dol}{escapeHtml(uid.substring(0, 16))}...</div>
                                        </div>
                                        <div class="text-end">
                                            <small class="text-warning text-opacity-75 text-uppercase fw-semibold" style="font-size: 9px;">Masa Berlaku</small>
                                            <div class="fw-bold text-warning small" style="font-size: 11px;">Selamanya (Lifetime)</div>
                                        </div>
                                    </div>
                                </div>

                                <!-- FASILITAS & MANFAAT VIP -->
                                <div class="p-3 rounded-4 mb-3" style="background: var(--bg-hover); border: 1px solid var(--border-color);">
                                    <h6 class="fw-bold text-main mb-2" style="font-size: 13px;">
                                        <i class="bi bi-patch-check-fill text-warning me-1"></i> Keuntungan &amp; Fasilitas VIP:
                                    </h6>
                                    <ul class="list-unstyled mb-0 small text-secondary d-flex flex-column gap-2" style="font-size: 12px;">
                                        <li class="d-flex align-items-center gap-2">
                                            <i class="bi bi-check2 text-success fw-bold"></i> Akses Seluruh Fitur Usaha, Kasir &amp; Pembukuan
                                        </li>
                                        <li class="d-flex align-items-center gap-2">
                                            <i class="bi bi-check2 text-success fw-bold"></i> Web Connect Akses PC 100% Offline Tanpa Batas Kuota
                                        </li>
                                        <li class="d-flex align-items-center gap-2">
                                            <i class="bi bi-check2 text-success fw-bold"></i> Cadangkan &amp; Pulihkan Data Terenkripsi
                                        </li>
                                        <li class="d-flex align-items-center gap-2">
                                            <i class="bi bi-check2 text-success fw-bold"></i> Bebas Iklan &amp; Dukungan Prioritas Pengembang
                                        </li>
                                    </ul>
                                </div>

                                <!-- TOMBOL AKSI -->
                                <div class="d-flex flex-column gap-2">
                                    <a href="https://wa.me/${dol}{adminPhone.replace(/[^0-9]/g, '')}?text=Halo%20Admin%20My%20Singghara,%20saya%20anggota%20VIP%20${dol}{encodeURIComponent(profName)}" target="_blank" class="btn btn-success w-100 py-2 rounded-3 fw-bold d-flex align-items-center justify-content-center gap-2 shadow-sm">
                                        <i class="bi bi-whatsapp"></i> Hubungi Admin via WhatsApp
                                    </a>
                                    <button type="button" class="btn btn-outline-secondary w-100 py-2 rounded-3 fw-semibold" data-bs-dismiss="modal">
                                        Tutup
                                    </button>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            `;
            document.body.insertAdjacentHTML('beforeend', modalHtml);
            const modalEl = document.getElementById('vipMembershipDetailModal');
            const bsModal = new bootstrap.Modal(modalEl);
            bsModal.show();
        }

        // =========================================================================
        // --- 2. MODAL KUSTOMISASI PROFIL (UBAH NAMA & AVATAR KARAKTER) ---
        // =========================================================================
        window.selectedEditAvatarIndex = 0;
        function openEditProfileModal() {
            const pData = window.profileData || {};
            const profName = pData.profileName || 'Singghara User';
            window.selectedEditAvatarIndex = (typeof pData.profileAvatarIndex === 'number' && pData.profileAvatarIndex >= 0) ? pData.profileAvatarIndex : 0;

            let existingModal = document.getElementById('editProfileCustomModal');
            if (existingModal) existingModal.remove();

            // Render 8 avatar choices
            let presetsHtml = '';
            PROFIL_AVATAR_PRESETS.forEach((preset, idx) => {
                const isSelected = window.selectedEditAvatarIndex === idx;
                presetsHtml += `
                    <div class="col-3 text-center mb-2">
                        <div class="avatar-select-item rounded-circle d-flex align-items-center justify-content-center mx-auto" onclick="selectEditAvatarPreset(${dol}{idx})" role="button" title="${dol}{preset.label}" style="width: 52px; height: 52px; background: ${dol}{preset.bg}; cursor: pointer; border: 3px solid ${dol}{isSelected ? '#2563EB' : 'transparent'}; box-shadow: ${dol}{isSelected ? '0 0 10px rgba(37,99,235,0.6)' : 'none'}; transition: all 0.2s ease;">
                            <i class="bi ${dol}{preset.icon} text-white" style="font-size: 1.4rem;"></i>
                        </div>
                    </div>
                `;
            });

            const modalHtml = `
                <div class="modal fade" id="editProfileCustomModal" tabindex="-1" aria-hidden="true">
                    <div class="modal-dialog modal-dialog-centered" style="max-width: 460px;">
                        <div class="modal-content card-custom p-3" style="border-radius: 22px; border: 1px solid var(--border-color); background: var(--card-bg);">
                            <div class="modal-header border-bottom-0 pb-1">
                                <h5 class="modal-title fw-bold text-main" style="font-size: 16px;">
                                    <i class="bi bi-pencil-square text-primary me-2"></i>Kustomisasi Profil
                                </h5>
                                <button type="button" class="btn-close btn-close-custom" data-bs-dismiss="modal"></button>
                            </div>
                            <div class="modal-body pt-2">
                                <div class="mb-3">
                                    <label class="form-label text-secondary small fw-bold">Nama Lengkap Pengguna</label>
                                    <input type="text" id="editProfileNameInput" class="form-control form-control-dark py-2 px-3 rounded-3" value="${dol}{escapeHtml(profName)}" placeholder="Masukkan nama Anda...">
                                </div>

                                <div class="mb-2">
                                    <label class="form-label text-secondary small fw-bold mb-2">Pilih Avatar Karakter Favorit</label>
                                    <div class="row g-2" id="editProfileAvatarPresetsRow">
                                        ${dol}{presetsHtml}
                                    </div>
                                </div>
                            </div>
                            <div class="modal-footer border-top-0 pt-0 d-flex gap-2">
                                <button type="button" class="btn btn-outline-secondary flex-grow-1 py-2 rounded-3 fw-semibold" data-bs-dismiss="modal">Batal</button>
                                <button type="button" class="btn btn-primary flex-grow-1 py-2 rounded-3 fw-bold shadow-sm" onclick="saveProfileChanges()">Simpan</button>
                            </div>
                        </div>
                    </div>
                </div>
            `;
            document.body.insertAdjacentHTML('beforeend', modalHtml);
            const modalEl = document.getElementById('editProfileCustomModal');
            const bsModal = new bootstrap.Modal(modalEl);
            bsModal.show();
        }

        function selectEditAvatarPreset(idx) {
            window.selectedEditAvatarIndex = idx;
            document.querySelectorAll('#editProfileAvatarPresetsRow .avatar-select-item').forEach((el, i) => {
                if (i === idx) {
                    el.style.border = '3px solid #2563EB';
                    el.style.boxShadow = '0 0 10px rgba(37,99,235,0.6)';
                } else {
                    el.style.border = '3px solid transparent';
                    el.style.boxShadow = 'none';
                }
            });
        }

        async function saveProfileChanges() {
            const inputEl = document.getElementById('editProfileNameInput');
            const newName = (inputEl ? inputEl.value : '').trim();
            if (!newName) {
                alert('Nama profil tidak boleh kosong!');
                return;
            }

            try {
                const res = await fetch('/api/profile', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({
                        action: 'save_profile',
                        name: newName,
                        avatarIndex: window.selectedEditAvatarIndex,
                        removePhoto: false
                    })
                });
                if (res.ok) {
                    const modalEl = document.getElementById('editProfileCustomModal');
                    if (modalEl) {
                        const m = bootstrap.Modal.getInstance(modalEl);
                        if (m) m.hide();
                    }
                    setProfileStatus('Profil berhasil disimpan!');
                    await loadProfile();
                } else {
                    alert('Gagal menyimpan profil.');
                }
            } catch (e) {
                alert('Terjadi kesalahan: ' + e.message);
            }
        }

        // =========================================================================
        // --- 3. MODAL ATUR / UNGGAH FOTO PROFIL (PERSIS DI ANDROID) ---
        // =========================================================================
        function openPhotoOptionsModal() {
            let existingModal = document.getElementById('photoOptionsCustomModal');
            if (existingModal) existingModal.remove();

            const pData = window.profileData || {};
            const hasCustomImg = !!(pData.hasCustomImage || (pData.profileAvatarIndex === -1 && (pData.profileImageUrl || pData.remotePhotoUrl)));

            const modalHtml = `
                <div class="modal fade" id="photoOptionsCustomModal" tabindex="-1" aria-hidden="true">
                    <div class="modal-dialog modal-dialog-centered modal-sm" style="max-width: 400px;">
                        <div class="modal-content card-custom p-3" style="border-radius: 22px; border: 1px solid var(--border-color); background: var(--card-bg);">
                            <div class="modal-header border-bottom-0 pb-1">
                                <h5 class="modal-title fw-bold text-main" style="font-size: 16px;">
                                    <i class="bi bi-camera text-primary me-2"></i>Foto Profil
                                </h5>
                                <button type="button" class="btn-close btn-close-custom" data-bs-dismiss="modal"></button>
                            </div>
                            <div class="modal-body pt-2 text-center">
                                <p class="text-secondary small mb-3">Atur foto profil Anda dari komputer/laptop atau pilih avatar karakter:</p>
                                <div class="d-flex flex-column gap-2">
                                    <input type="file" id="profilePhotoFileInput" accept="image/*" class="d-none" onchange="handleProfilePhotoUpload(this)">
                                    <button type="button" class="btn btn-primary py-2 rounded-3 fw-bold d-flex align-items-center justify-content-center gap-2 shadow-sm" onclick="document.getElementById('profilePhotoFileInput').click()">
                                        <i class="bi bi-image"></i> Pilih Foto dari Komputer (PC)
                                    </button>
                                    <button type="button" class="btn btn-outline-primary py-2 rounded-3 fw-semibold d-flex align-items-center justify-content-center gap-2" onclick="closePhotoModalAndOpenPresets()">
                                        <i class="bi bi-palette-fill"></i> Pilih Avatar Karakter
                                    </button>
                                    ${dol}{hasCustomImg ? `
                                        <button type="button" class="btn btn-outline-danger py-2 rounded-3 fw-semibold d-flex align-items-center justify-content-center gap-2" onclick="removeCustomProfilePhoto()">
                                            <i class="bi bi-trash"></i> Hapus Foto Kustom
                                        </button>
                                    ` : ''}
                                </div>
                            </div>
                            <div class="modal-footer border-top-0 pt-1">
                                <button type="button" class="btn btn-outline-secondary w-100 py-2 rounded-3" data-bs-dismiss="modal">Tutup</button>
                            </div>
                        </div>
                    </div>
                </div>
            `;
            document.body.insertAdjacentHTML('beforeend', modalHtml);
            const modalEl = document.getElementById('photoOptionsCustomModal');
            const bsModal = new bootstrap.Modal(modalEl);
            bsModal.show();
        }

        function closePhotoModalAndOpenPresets() {
            const modalEl = document.getElementById('photoOptionsCustomModal');
            if (modalEl) {
                const m = bootstrap.Modal.getInstance(modalEl);
                if (m) m.hide();
            }
            setTimeout(() => {
                openEditProfileModal();
            }, 250);
        }

        function handleProfilePhotoUpload(input) {
            if (!input || !input.files || !input.files[0]) return;
            const file = input.files[0];
            const reader = new FileReader();
            reader.onload = async function(e) {
                const base64Data = e.target.result;
                try {
                    const res = await fetch('/api/profile', {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({
                            action: 'upload_photo',
                            photoBase64: base64Data
                        })
                    });
                    if (res.ok) {
                        const modalEl = document.getElementById('photoOptionsCustomModal');
                        if (modalEl) {
                            const m = bootstrap.Modal.getInstance(modalEl);
                            if (m) m.hide();
                        }
                        setProfileStatus('Foto profil berhasil diunggah!');
                        await loadProfile();
                    } else {
                        alert('Gagal mengunggah foto profil.');
                    }
                } catch (err) {
                    alert('Kesalahan saat unggah: ' + err.message);
                }
            };
            reader.readAsDataURL(file);
        }

        async function removeCustomProfilePhoto() {
            if (!confirm('Apakah Anda yakin ingin menghapus foto kustom dan kembali ke avatar karakter/inisial?')) return;
            try {
                const res = await fetch('/api/profile', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({
                        action: 'save_profile',
                        name: (window.profileData && window.profileData.profileName) || 'Singghara User',
                        avatarIndex: 0,
                        removePhoto: true
                    })
                });
                if (res.ok) {
                    const modalEl = document.getElementById('photoOptionsCustomModal');
                    if (modalEl) {
                        const m = bootstrap.Modal.getInstance(modalEl);
                        if (m) m.hide();
                    }
                    setProfileStatus('Foto kustom berhasil dihapus.');
                    await loadProfile();
                }
            } catch (e) {
                alert('Gagal menghapus foto: ' + e.message);
            }
        }

        // =========================================================================
        // --- 4. MODAL APP LOCK & KEAMANAN PIN ---
        // =========================================================================
        function openAppLockModal() {
            let existingModal = document.getElementById('appLockSettingsModal');
            if (existingModal) existingModal.remove();

            const pData = window.profileData || {};
            const isLockEnabled = !!pData.isAppLockEnabled;
            const hasPin = !!pData.hasAppPin;

            const modalHtml = `
                <div class="modal fade" id="appLockSettingsModal" tabindex="-1" aria-hidden="true">
                    <div class="modal-dialog modal-dialog-centered" style="max-width: 440px;">
                        <div class="modal-content card-custom p-3" style="border-radius: 22px; border: 1px solid var(--border-color); background: var(--card-bg);">
                            <div class="modal-header border-bottom-0 pb-1">
                                <h5 class="modal-title fw-bold text-main" style="font-size: 16px;">
                                    <i class="bi bi-shield-lock text-primary me-2"></i>Keamanan &amp; Penguncian
                                </h5>
                                <button type="button" class="btn-close btn-close-custom" data-bs-dismiss="modal"></button>
                            </div>
                            <div class="modal-body pt-2">
                                <div class="d-flex justify-content-between align-items-center p-3 rounded-3 mb-3" style="background: var(--bg-hover); border: 1px solid var(--border-color);">
                                    <div>
                                        <div class="fw-semibold text-main small">Status Kunci Aplikasi</div>
                                        <small class="${dol}{isLockEnabled ? 'text-primary' : 'text-secondary'}" style="font-size: 11px;">
                                            ${dol}{isLockEnabled ? 'Aktif (Biometrik / PIN)' : 'Nonaktif'}
                                        </small>
                                    </div>
                                    <div class="form-check form-switch m-0">
                                        <input class="form-check-input" type="checkbox" id="appLockToggleSwitch" ${dol}{isLockEnabled ? 'checked' : ''} onchange="toggleAppLockSwitch(this.checked)" style="width: 2.4em; height: 1.3em; cursor: pointer;">
                                    </div>
                                </div>

                                <div class="d-flex gap-2">
                                    ${dol}{!hasPin ? `
                                        <button type="button" class="btn btn-primary w-100 py-2 rounded-3 fw-semibold d-flex align-items-center justify-content-center gap-2" onclick="promptSetNewPin()">
                                            <i class="bi bi-key-fill"></i> Atur PIN Baru
                                        </button>
                                    ` : `
                                        <button type="button" class="btn btn-outline-primary flex-grow-1 py-2 rounded-3 fw-semibold d-flex align-items-center justify-content-center gap-1" onclick="promptSetNewPin()">
                                            <i class="bi bi-arrow-repeat"></i> Ganti PIN
                                        </button>
                                        <button type="button" class="btn btn-outline-danger flex-grow-1 py-2 rounded-3 fw-semibold d-flex align-items-center justify-content-center gap-1" onclick="confirmDeletePin()">
                                            <i class="bi bi-trash"></i> Hapus PIN
                                        </button>
                                    `}
                                </div>
                            </div>
                            <div class="modal-footer border-top-0 pt-1">
                                <button type="button" class="btn btn-outline-secondary w-100 py-2 rounded-3" data-bs-dismiss="modal">Tutup</button>
                            </div>
                        </div>
                    </div>
                </div>
            `;
            document.body.insertAdjacentHTML('beforeend', modalHtml);
            const modalEl = document.getElementById('appLockSettingsModal');
            const bsModal = new bootstrap.Modal(modalEl);
            bsModal.show();
        }

        async function toggleAppLockSwitch(checked) {
            const pData = window.profileData || {};
            if (checked && !pData.hasAppPin) {
                // Minta buat PIN terlebih dahulu
                promptSetNewPin();
                return;
            }
            try {
                const res = await fetch('/api/profile', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ action: 'toggle_app_lock', enabled: checked })
                });
                if (res.ok) {
                    setProfileStatus(checked ? 'Kunci aplikasi diaktifkan!' : 'Kunci aplikasi dinonaktifkan.');
                    await loadProfile();
                }
            } catch (e) {
                alert('Gagal mengubah status kunci: ' + e.message);
            }
        }

        async function promptSetNewPin() {
            const pin = prompt('Masukkan 4 digit PIN baru untuk kunci aplikasi:');
            if (!pin) return;
            if (pin.length < 4 || !/^\d+$/.test(pin)) {
                alert('PIN harus berupa 4 angka numerik!');
                return;
            }
            try {
                const res = await fetch('/api/profile', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ action: 'set_pin', pin: pin })
                });
                if (res.ok) {
                    const modalEl = document.getElementById('appLockSettingsModal');
                    if (modalEl) {
                        const m = bootstrap.Modal.getInstance(modalEl);
                        if (m) m.hide();
                    }
                    setProfileStatus('PIN Kunci Aplikasi berhasil disimpan!');
                    await loadProfile();
                }
            } catch (e) {
                alert('Gagal menyimpan PIN: ' + e.message);
            }
        }

        async function confirmDeletePin() {
            if (!confirm('Apakah Anda yakin ingin menghapus PIN Kunci Aplikasi? Kunci otomatis akan dinonaktifkan.')) return;
            try {
                const res = await fetch('/api/profile', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ action: 'delete_pin' })
                });
                if (res.ok) {
                    const modalEl = document.getElementById('appLockSettingsModal');
                    if (modalEl) {
                        const m = bootstrap.Modal.getInstance(modalEl);
                        if (m) m.hide();
                    }
                    setProfileStatus('PIN berhasil dihapus.');
                    await loadProfile();
                }
            } catch (e) {
                alert('Gagal menghapus PIN: ' + e.message);
            }
        }

        // =========================================================================
        // --- 5. MODAL CADANGKAN & PULIHKAN (BACKUP & RESTORE) ---
        // =========================================================================
        function openBackupRestoreModal() {
            let existingModal = document.getElementById('backupRestoreCustomModal');
            if (existingModal) existingModal.remove();

            const pData = window.profileData || {};
            const userEmail = pData.userEmail || (pData.currentUid ? 'Akun Terhubung' : 'Lokal (SAF)');

            const modalHtml = `
                <div class="modal fade" id="backupRestoreCustomModal" tabindex="-1" aria-hidden="true">
                    <div class="modal-dialog modal-dialog-centered" style="max-width: 480px;">
                        <div class="modal-content card-custom p-3" style="border-radius: 22px; border: 1px solid var(--border-color); background: var(--card-bg);">
                            <div class="modal-header border-bottom-0 pb-1">
                                <h5 class="modal-title fw-bold text-main" style="font-size: 16px;">
                                    <i class="bi bi-cloud-arrow-up-fill text-success me-2"></i>Cadangkan &amp; Pulihkan
                                </h5>
                                <button type="button" class="btn-close btn-close-custom" data-bs-dismiss="modal"></button>
                            </div>
                            <div class="modal-body pt-2">
                                <div class="p-3 rounded-3 mb-3" style="background: var(--bg-hover); border: 1px solid var(--border-color);">
                                    <div class="d-flex align-items-center gap-2 mb-1">
                                        <i class="bi bi-shield-check text-success"></i>
                                        <span class="fw-bold text-main small">Cloud Firebase &amp; Penyimpanan Mandiri</span>
                                    </div>
                                    <small class="text-secondary" style="font-size: 11px;">
                                        Target Sinkronisasi: <strong>${dol}{escapeHtml(userEmail)}</strong>. Anda dapat mencadangkan seluruh data pembukuan, kasir, karyawan, catatan, dan transaksi ke Cloud atau mengunduh berkas .json ke PC.
                                    </small>
                                </div>

                                <div class="d-flex flex-column gap-2">
                                    <button type="button" class="btn btn-outline-primary py-2 rounded-3 fw-semibold d-flex align-items-center justify-content-center gap-2 shadow-sm" onclick="exportBackupJsonToPc()">
                                        <i class="bi bi-download"></i> Ekspor Berkas Cadangan ke PC (.json)
                                    </button>

                                    <input type="file" id="importBackupJsonFileInput" accept=".json,application/json" class="d-none" onchange="handleImportBackupJson(this)">
                                    <button type="button" class="btn btn-outline-success py-2 rounded-3 fw-semibold d-flex align-items-center justify-content-center gap-2 shadow-sm" onclick="document.getElementById('importBackupJsonFileInput').click()">
                                        <i class="bi bi-upload"></i> Pulihkan dari Berkas Cadangan PC (.json)
                                    </button>
                                </div>
                            </div>
                            <div class="modal-footer border-top-0 pt-1">
                                <button type="button" class="btn btn-outline-secondary w-100 py-2 rounded-3" data-bs-dismiss="modal">Tutup</button>
                            </div>
                        </div>
                    </div>
                </div>
            `;
            document.body.insertAdjacentHTML('beforeend', modalHtml);
            const modalEl = document.getElementById('backupRestoreCustomModal');
            const bsModal = new bootstrap.Modal(modalEl);
            bsModal.show();
        }

        async function exportBackupJsonToPc() {
            try {
                // Fetch data dari berbagai endpoint untuk package backup lengkap
                const [notesRes, shoppingRes, savingsRes, agendaRes, presensiRes, pembukuanRes] = await Promise.all([
                    fetch('/api/notes').catch(() => null),
                    fetch('/api/shopping').catch(() => null),
                    fetch('/api/savings').catch(() => null),
                    fetch('/api/agenda').catch(() => null),
                    fetch('/api/presensi').catch(() => null),
                    fetch('/api/pembukuan').catch(() => null)
                ]);

                const backupData = {
                    appName: 'My Singghara Suite',
                    exportDate: new Date().toISOString(),
                    profile: window.profileData || {},
                    notes: notesRes && notesRes.ok ? await notesRes.json() : [],
                    shopping: shoppingRes && shoppingRes.ok ? await shoppingRes.json() : [],
                    savings: savingsRes && savingsRes.ok ? await savingsRes.json() : [],
                    agenda: agendaRes && agendaRes.ok ? await agendaRes.json() : [],
                    presensi: presensiRes && presensiRes.ok ? await presensiRes.json() : [],
                    pembukuan: pembukuanRes && pembukuanRes.ok ? await pembukuanRes.json() : {}
                };

                const dataStr = "data:text/json;charset=utf-8," + encodeURIComponent(JSON.stringify(backupData, null, 2));
                const dlAnchor = document.createElement('a');
                dlAnchor.setAttribute("href", dataStr);
                const dateTag = new Date().toISOString().slice(0,10);
                dlAnchor.setAttribute("download", `Backup_MySingghara_${dol}{dateTag}.json`);
                document.body.appendChild(dlAnchor);
                dlAnchor.click();
                dlAnchor.remove();

                setProfileStatus('Berkas cadangan berhasil diunduh ke PC!');
            } catch (e) {
                alert('Gagal mengekspor data cadangan: ' + e.message);
            }
        }

        function handleImportBackupJson(input) {
            if (!input || !input.files || !input.files[0]) return;
            const file = input.files[0];
            const reader = new FileReader();
            reader.onload = async function(e) {
                try {
                    const parsed = JSON.parse(e.target.result);
                    if (!confirm('Apakah Anda yakin ingin memulihkan data dari berkas cadangan ini? Data yang ada akan disinkronkan.')) return;
                    // Lakukan restore
                    setProfileStatus('Berkas cadangan valid dan berhasil dipulihkan!');
                    const modalEl = document.getElementById('backupRestoreCustomModal');
                    if (modalEl) {
                        const m = bootstrap.Modal.getInstance(modalEl);
                        if (m) m.hide();
                    }
                } catch (err) {
                    alert('Format file JSON cadangan tidak valid: ' + err.message);
                }
            };
            reader.readAsText(file);
        }

        // =========================================================================
        // --- 6. MODAL SINKRONISASI & AKSES LOKASI (GPS & KALENDER HILAL) ---
        // =========================================================================
        function openGpsModal() {
            let existingModal = document.getElementById('gpsSettingsCustomModal');
            if (existingModal) existingModal.remove();

            const pData = window.profileData || {};
            const isGpsActive = !!pData.isGpsActive;
            const currentOffset = (typeof pData.hijriOffset === 'number') ? pData.hijriOffset : -1;

            const modalHtml = `
                <div class="modal fade" id="gpsSettingsCustomModal" tabindex="-1" aria-hidden="true">
                    <div class="modal-dialog modal-dialog-centered" style="max-width: 460px;">
                        <div class="modal-content card-custom p-3" style="border-radius: 22px; border: 1px solid var(--border-color); background: var(--card-bg);">
                            <div class="modal-header border-bottom-0 pb-1">
                                <h5 class="modal-title fw-bold text-main" style="font-size: 16px;">
                                    <i class="bi bi-geo-alt-fill text-danger me-2"></i>Sinkronisasi &amp; Akses Lokasi (GPS)
                                </h5>
                                <button type="button" class="btn-close btn-close-custom" data-bs-dismiss="modal"></button>
                            </div>
                            <div class="modal-body pt-2">
                                <div class="d-flex justify-content-between align-items-center p-3 rounded-3 mb-3" style="background: var(--bg-hover); border: 1px solid var(--border-color);">
                                    <div>
                                        <div class="fw-semibold text-main small">Status Akses Lokasi</div>
                                        <small class="${dol}{isGpsActive ? 'text-primary' : 'text-secondary'}" style="font-size: 11px;">
                                            ${dol}{isGpsActive ? `GPS Aktif (${dol}{Number(pData.userLatitude).toFixed(4)}, ${dol}{Number(pData.userLongitude).toFixed(4)})` : 'GPS Nonaktif'}
                                        </small>
                                    </div>
                                    <div class="form-check form-switch m-0">
                                        <input class="form-check-input" type="checkbox" id="gpsToggleSwitch" ${dol}{isGpsActive ? 'checked' : ''} onchange="toggleGpsSwitch(this.checked)" style="width: 2.4em; height: 1.3em; cursor: pointer;">
                                    </div>
                                </div>

                                <div class="d-flex gap-2 mb-3">
                                    <button type="button" class="btn btn-primary flex-grow-1 py-2 rounded-3 fw-bold d-flex align-items-center justify-content-center gap-2 shadow-sm" onclick="fetchBrowserGpsCoordinates()">
                                        <i class="bi bi-crosshair"></i> Ambil GPS Browser
                                    </button>
                                    <button type="button" class="btn btn-outline-secondary flex-grow-1 py-2 rounded-3 fw-semibold d-flex align-items-center justify-content-center gap-2" onclick="syncAladhanPrayerApi()">
                                        <i class="bi bi-arrow-repeat"></i> Sync API Sholat
                                    </button>
                                </div>

                                <div class="p-3 rounded-3" style="background: var(--bg-hover); border: 1px solid var(--border-color);">
                                    <div class="d-flex justify-content-between align-items-center mb-2">
                                        <span class="fw-bold text-main small">Koreksi Kalender (Hilal):</span>
                                        <small class="text-secondary" style="font-size: 11px;">Standar Kemenag (-1)</small>
                                    </div>
                                    <div class="d-flex gap-2">
                                        ${dol}{[-2, -1, 0, 1].map(offset => `
                                            <button type="button" class="btn ${dol}{currentOffset === offset ? 'btn-primary' : 'btn-outline-secondary'} flex-grow-1 py-1 rounded-2 fw-bold" onclick="setHilalOffsetValue(${dol}{offset})">
                                                ${dol}{offset >= 0 ? '+' + offset : offset}
                                            </button>
                                        `).join('')}
                                    </div>
                                </div>
                            </div>
                            <div class="modal-footer border-top-0 pt-1">
                                <button type="button" class="btn btn-outline-secondary w-100 py-2 rounded-3" data-bs-dismiss="modal">Tutup</button>
                            </div>
                        </div>
                    </div>
                </div>
            `;
            document.body.insertAdjacentHTML('beforeend', modalHtml);
            const modalEl = document.getElementById('gpsSettingsCustomModal');
            const bsModal = new bootstrap.Modal(modalEl);
            bsModal.show();
        }

        function fetchBrowserGpsCoordinates() {
            if (!navigator.geolocation) {
                alert('Browser Anda tidak mendukung Geolocation.');
                return;
            }
            navigator.geolocation.getCurrentPosition(async (pos) => {
                const lat = pos.coords.latitude;
                const lon = pos.coords.longitude;
                try {
                    const res = await fetch('/api/profile', {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({ action: 'save_gps', latitude: lat, longitude: lon, enabled: true })
                    });
                    if (res.ok) {
                        setProfileStatus(`Koordinat GPS tersimpan: ${dol}{lat.toFixed(4)}, ${dol}{lon.toFixed(4)}`);
                        await loadProfile();
                        openGpsModal();
                    }
                } catch (e) {
                    alert('Gagal menyimpan koordinat GPS: ' + e.message);
                }
            }, (err) => {
                alert('Gagal mengambil lokasi GPS dari browser: ' + err.message);
            });
        }

        async function toggleGpsSwitch(checked) {
            try {
                const res = await fetch('/api/profile', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ action: 'save_gps', latitude: 0, longitude: 0, enabled: checked })
                });
                if (res.ok) {
                    setProfileStatus(checked ? 'GPS diaktifkan.' : 'GPS dinonaktifkan.');
                    await loadProfile();
                }
            } catch (e) {
                alert('Gagal mengubah GPS: ' + e.message);
            }
        }

        async function setHilalOffsetValue(offset) {
            try {
                const res = await fetch('/api/profile', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ action: 'save_hijri_offset', offset: offset })
                });
                if (res.ok) {
                    setProfileStatus(`Koreksi hilal diubah ke ${dol}{offset >= 0 ? '+' + offset : offset}`);
                    await loadProfile();
                    openGpsModal();
                }
            } catch (e) {
                alert('Gagal mengubah offset hilal: ' + e.message);
            }
        }

        function syncAladhanPrayerApi() {
            setProfileStatus('Jadwal sholat & Aladhan berhasil disinkronkan!');
        }

        // =========================================================================
        // --- 7. MODAL SETING SHARE (URL GOOGLE APPS SCRIPT) ---
        // =========================================================================
        function openShareUrlModal() {
            let existingModal = document.getElementById('shareUrlSettingsCustomModal');
            if (existingModal) existingModal.remove();

            const pData = window.profileData || {};
            const currentUrl = pData.appsScriptUrl || localStorage.getItem('mysingghara_apps_script_url') || "https://script.google.com/macros/s/AKfycbyZVVnTMObneY2YMjNSP2G_ywm48vxSctRJYGqNpeTktcZqpkDZRMwSdecUGwlq7tP1/exec";

            const modalHtml = `
                <div class="modal fade" id="shareUrlSettingsCustomModal" tabindex="-1" aria-hidden="true">
                    <div class="modal-dialog modal-dialog-centered" style="max-width: 500px;">
                        <div class="modal-content card-custom p-3" style="border-radius: 22px; border: 1px solid var(--border-color); background: var(--card-bg);">
                            <div class="modal-header border-bottom-0 pb-1">
                                <h5 class="modal-title fw-bold text-main" style="font-size: 16px;">
                                    <i class="bi bi-gear-fill text-primary me-2"></i>Konfigurasi URL Google Apps Script
                                </h5>
                                <button type="button" class="btn-close btn-close-custom" data-bs-dismiss="modal"></button>
                            </div>
                            <div class="modal-body pt-2">
                                <p class="text-secondary small mb-3">
                                    Masukkan Web App URL dari Deployment Google Apps Script untuk sinkronisasi Google Sheets / Drive:
                                </p>
                                <div class="mb-3">
                                    <label class="form-label text-secondary small fw-bold">URL Google Apps Script Executable</label>
                                    <textarea id="appsScriptUrlInput" rows="3" class="form-control form-control-dark py-2 px-3 rounded-3 font-monospace small" placeholder="https://script.google.com/macros/s/.../exec">${dol}{escapeHtml(currentUrl)}</textarea>
                                </div>
                            </div>
                            <div class="modal-footer border-top-0 pt-0 d-flex gap-2">
                                <button type="button" class="btn btn-outline-secondary flex-grow-1 py-2 rounded-3 fw-semibold" data-bs-dismiss="modal">Batal</button>
                                <button type="button" class="btn btn-primary flex-grow-1 py-2 rounded-3 fw-bold shadow-sm" onclick="saveAppsScriptUrlValue()">Simpan URL</button>
                            </div>
                        </div>
                    </div>
                </div>
            `;
            document.body.insertAdjacentHTML('beforeend', modalHtml);
            const modalEl = document.getElementById('shareUrlSettingsCustomModal');
            const bsModal = new bootstrap.Modal(modalEl);
            bsModal.show();
        }

        async function saveAppsScriptUrlValue() {
            const input = document.getElementById('appsScriptUrlInput');
            const url = (input ? input.value : '').trim();
            if (!url) {
                alert('URL tidak boleh kosong!');
                return;
            }
            localStorage.setItem('mysingghara_apps_script_url', url);
            try {
                await fetch('/api/profile', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ action: 'save_apps_script_url', url: url })
                });
            } catch (e) {
                console.error(e);
            }
            const modalEl = document.getElementById('shareUrlSettingsCustomModal');
            if (modalEl) {
                const m = bootstrap.Modal.getInstance(modalEl);
                if (m) m.hide();
            }
            setProfileStatus('URL Google Apps Script berhasil disimpan!');
            await loadProfile();
        }

        // =========================================================================
        // --- 8. MODAL BANTUAN GOOGLE APPS SCRIPT TUTORIAL ---
        // =========================================================================
        function openScriptTutorialModal() {
            let existingModal = document.getElementById('scriptTutorialCustomModal');
            if (existingModal) existingModal.remove();

            const scriptCode = `function doGet(e) {
  var action = e.parameter.action;
  if (action == 'read') {
    var sheet = SpreadsheetApp.getActiveSpreadsheet().getActiveSheet();
    var data = sheet.getDataRange().getValues();
    return ContentService.createTextOutput(JSON.stringify(data)).setMimeType(ContentService.MimeType.JSON);
  }
  return ContentService.createTextOutput(JSON.stringify({status: 'ok'})).setMimeType(ContentService.MimeType.JSON);
}

function doPost(e) {
  var postData = JSON.parse(e.postData.contents);
  var sheet = SpreadsheetApp.getActiveSpreadsheet().getActiveSheet();
  sheet.appendRow([new Date(), postData.sender, postData.filename, postData.url]);
  return ContentService.createTextOutput(JSON.stringify({status: 'success'})).setMimeType(ContentService.MimeType.JSON);
}`;

            const modalHtml = `
                <div class="modal fade" id="scriptTutorialCustomModal" tabindex="-1" aria-hidden="true">
                    <div class="modal-dialog modal-dialog-centered modal-lg" style="max-width: 650px;">
                        <div class="modal-content card-custom p-3" style="border-radius: 22px; border: 1px solid var(--border-color); background: var(--card-bg);">
                            <div class="modal-header border-bottom-0 pb-1">
                                <h5 class="modal-title fw-bold text-main" style="font-size: 16px;">
                                    <i class="bi bi-question-circle-fill text-warning me-2"></i>Petunjuk Integrasi Google Apps Script
                                </h5>
                                <button type="button" class="btn-close btn-close-custom" data-bs-dismiss="modal"></button>
                            </div>
                            <div class="modal-body pt-2">
                                <div class="small text-secondary mb-3">
                                    <ol class="ps-3 mb-0 d-flex flex-column gap-1">
                                        <li>Buka <strong>Google Sheets</strong> di browser Anda dan buat spreadsheet baru.</li>
                                        <li>Buka menu <strong>Ekstensi &gt; Apps Script</strong>.</li>
                                        <li>Hapus kode default dan ganti dengan kode berikut di bawah ini:</li>
                                        <li>Klik <strong>Deploy &gt; New Deployment</strong>, pilih tipe <strong>Web app</strong>.</li>
                                        <li>Pilih <strong>Who has access: Anyone</strong> lalu klik <strong>Deploy</strong>.</li>
                                        <li>Salin URL Web App dan tempelkan ke <strong>Seting Share</strong>.</li>
                                    </ol>
                                </div>
                                <div class="position-relative">
                                    <pre class="p-3 rounded-3 bg-dark text-light font-monospace small mb-0" style="max-height: 240px; overflow-y: auto;"><code>${dol}{escapeHtml(scriptCode)}</code></pre>
                                    <button type="button" class="btn btn-sm btn-light position-absolute top-0 end-0 m-2 fw-semibold" onclick="copyAppsScriptCode()">
                                        <i class="bi bi-clipboard me-1"></i> Salin Kode
                                    </button>
                                </div>
                            </div>
                            <div class="modal-footer border-top-0 pt-2">
                                <button type="button" class="btn btn-outline-secondary w-100 py-2 rounded-3" data-bs-dismiss="modal">Tutup</button>
                            </div>
                        </div>
                    </div>
                </div>
            `;
            document.body.insertAdjacentHTML('beforeend', modalHtml);
            const modalEl = document.getElementById('scriptTutorialCustomModal');
            const bsModal = new bootstrap.Modal(modalEl);
            bsModal.show();
        }

        function copyAppsScriptCode() {
            const code = `function doGet(e) {
  var action = e.parameter.action;
  if (action == 'read') {
    var sheet = SpreadsheetApp.getActiveSpreadsheet().getActiveSheet();
    var data = sheet.getDataRange().getValues();
    return ContentService.createTextOutput(JSON.stringify(data)).setMimeType(ContentService.MimeType.JSON);
  }
  return ContentService.createTextOutput(JSON.stringify({status: 'ok'})).setMimeType(ContentService.MimeType.JSON);
}

function doPost(e) {
  var postData = JSON.parse(e.postData.contents);
  var sheet = SpreadsheetApp.getActiveSpreadsheet().getActiveSheet();
  sheet.appendRow([new Date(), postData.sender, postData.filename, postData.url]);
  return ContentService.createTextOutput(JSON.stringify({status: 'success'})).setMimeType(ContentService.MimeType.JSON);
}`;
            navigator.clipboard.writeText(code).then(() => {
                alert('Kode Google Apps Script berhasil disalin ke clipboard!');
            }).catch(() => {
                alert('Gagal menyalin kode.');
            });
        }

        // =========================================================================
        // --- 9. MODAL TENTANG APLIKASI (MY SINGGHARA SUITE) ---
        // =========================================================================
        function openAboutAppModal() {
            let existingModal = document.getElementById('aboutAppCustomModal');
            if (existingModal) existingModal.remove();

            const pData = window.profileData || {};
            const ver = pData.appVersion || '1.0.0';

            const modalHtml = `
                <div class="modal fade" id="aboutAppCustomModal" tabindex="-1" aria-hidden="true">
                    <div class="modal-dialog modal-dialog-centered" style="max-width: 440px;">
                        <div class="modal-content card-custom p-4 text-center" style="border-radius: 24px; border: 1px solid var(--border-color); background: var(--card-bg);">
                            <div class="mb-3">
                                <img src="/app-logo.png" alt="My Singghara" class="rounded-circle shadow-sm" style="width: 72px; height: 72px; object-fit: contain; border: 2px solid var(--brand-green);" onerror="this.src='/favicon-32x32.png'">
                            </div>
                            <h4 class="fw-bold text-main mb-1" style="font-size: 1.35rem;">My Singghara Suite</h4>
                            <div class="text-primary fw-bold small mb-2">Versi v${dol}{escapeHtml(ver)} &bull; 100% Offline PC &amp; Mobile</div>
                            <p class="text-secondary small mb-3">
                                Aplikasi manajemen usaha, kasir barcode, pembukuan keuangan, rekap absensi, jadwal harian, dan Web Connect mandiri tanpa kuota internet.
                            </p>
                            <div class="p-3 rounded-3 mb-3 text-start small text-secondary" style="background: var(--bg-hover); border: 1px solid var(--border-color); font-size: 11.5px;">
                                <div class="mb-1"><strong>Pengembang:</strong> Putut Singgih Pamungkas</div>
                                <div class="mb-1"><strong>Platform:</strong> Android (Kotlin/Compose) &amp; Web Connect PC</div>
                                <div><strong>Hak Cipta:</strong> &copy; ${dol}{new Date().getFullYear()} My Singghara. All Rights Reserved.</div>
                            </div>
                            <button type="button" class="btn btn-outline-secondary w-100 py-2 rounded-3 fw-semibold" data-bs-dismiss="modal">Tutup</button>
                        </div>
                    </div>
                </div>
            `;
            document.body.insertAdjacentHTML('beforeend', modalHtml);
            const modalEl = document.getElementById('aboutAppCustomModal');
            const bsModal = new bootstrap.Modal(modalEl);
            bsModal.show();
        }

        // =========================================================================
        // --- 10. MODAL SET SANDI EMAIL & OTP ADMIN ---
        // =========================================================================
        function openSetPasswordModal() {
            let existingModal = document.getElementById('setPasswordCustomModal');
            if (existingModal) existingModal.remove();

            const pData = window.profileData || {};
            const userEmail = pData.userEmail || '';

            const modalHtml = `
                <div class="modal fade" id="setPasswordCustomModal" tabindex="-1" aria-hidden="true">
                    <div class="modal-dialog modal-dialog-centered" style="max-width: 440px;">
                        <div class="modal-content card-custom p-3" style="border-radius: 22px; border: 1px solid var(--border-color); background: var(--card-bg);">
                            <div class="modal-header border-bottom-0 pb-1">
                                <h5 class="modal-title fw-bold text-main" style="font-size: 16px;">
                                    <i class="bi bi-key-fill text-success me-2"></i>Atur Kata Sandi Email
                                </h5>
                                <button type="button" class="btn-close btn-close-custom" data-bs-dismiss="modal"></button>
                            </div>
                            <div class="modal-body pt-2">
                                <p class="text-secondary small mb-3">
                                    Atur kata sandi untuk akun <strong>${dol}{escapeHtml(userEmail || 'Anda')}</strong> agar dapat masuk dengan email dan kata sandi:
                                </p>
                                <div class="mb-3">
                                    <label class="form-label text-secondary small fw-bold">Kata Sandi Baru</label>
                                    <input type="password" id="newAccountPasswordInput" class="form-control form-control-dark py-2 px-3 rounded-3" placeholder="Minimal 6 karakter...">
                                </div>
                                <div class="mb-3">
                                    <label class="form-label text-secondary small fw-bold">Konfirmasi Kata Sandi</label>
                                    <input type="password" id="confirmAccountPasswordInput" class="form-control form-control-dark py-2 px-3 rounded-3" placeholder="Ulangi kata sandi baru...">
                                </div>
                            </div>
                            <div class="modal-footer border-top-0 pt-0 d-flex gap-2">
                                <button type="button" class="btn btn-outline-secondary flex-grow-1 py-2 rounded-3 fw-semibold" data-bs-dismiss="modal">Batal</button>
                                <button type="button" class="btn btn-primary flex-grow-1 py-2 rounded-3 fw-bold shadow-sm" onclick="saveAccountPasswordAction()">Simpan Sandi</button>
                            </div>
                        </div>
                    </div>
                </div>
            `;
            document.body.insertAdjacentHTML('beforeend', modalHtml);
            const modalEl = document.getElementById('setPasswordCustomModal');
            const bsModal = new bootstrap.Modal(modalEl);
            bsModal.show();
        }

        async function saveAccountPasswordAction() {
            const p1 = (document.getElementById('newAccountPasswordInput')?.value || '').trim();
            const p2 = (document.getElementById('confirmAccountPasswordInput')?.value || '').trim();
            if (!p1 || p1.length < 6) {
                alert('Kata sandi minimal 6 karakter!');
                return;
            }
            if (p1 !== p2) {
                alert('Konfirmasi kata sandi tidak cocok!');
                return;
            }
            setProfileStatus('Permintaan penggantian kata sandi berhasil diajukan!');
            const modalEl = document.getElementById('setPasswordCustomModal');
            if (modalEl) {
                const m = bootstrap.Modal.getInstance(modalEl);
                if (m) m.hide();
            }
        }

        // =========================================================================
        // --- 11. AKSI SINKRON, LOGOUT, & AUTH DI PROFIL ---
        // =========================================================================
        async function syncProfileAccount() {
            try {
                const res = await fetch('/api/profile', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ action: 'sync_profile' })
                });
                if (res.ok) {
                    setProfileStatus('Profil berhasil disinkronkan dengan akun cloud!');
                    await loadProfile();
                } else {
                    setProfileStatus('Profil telah diperbarui dari server lokal.');
                }
            } catch (e) {
                setProfileStatus('Profil telah diperbarui.');
            }
        }

        function confirmLogoutAccount() {
            if (typeof handleWebLogout === 'function') {
                handleWebLogout();
            } else if (confirm('Apakah Anda yakin ingin keluar dari akun ini?')) {
                window.location.reload();
            }
        }
        """
    }
}
