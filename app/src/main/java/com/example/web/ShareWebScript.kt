package com.example.web

object ShareWebScript {
    fun getScript(dol: String): String {
        return """
        // =========================================================================
        // --- SHARE / GOOGLE DRIVE STORAGE (100% OFFLINE PERSIS SHARESCREEN.KT) ---
        // =========================================================================

        window.shareFiles = [];
        window.shareSearchQuery = '';
        window.shareSelectedType = 'Semua';
        window.shareSelectedSort = 'Terbaru';
        window.shareSelectedIds = new Set();
        window.shareIsLoading = false;
        window.shareIsUploading = false;
        window.shareUploadProgress = 0;
        window.shareUploadStatusText = '';
        window.shareIsOnline = (typeof navigator.onLine !== 'undefined') ? navigator.onLine : true;
        window.shareDefaultUrl = "https://script.google.com/macros/s/AKfycbyZVVnTMObneY2YMjNSP2G_ywm48vxSctRJYGqNpeTktcZqpkDZRMwSdecUGwlq7tP1/exec";
        window.shareAppsScriptUrl = localStorage.getItem('mysingghara_apps_script_url') || window.shareDefaultUrl;
        window.shareFileToDelete = null;

        // Listeners status koneksi online/offline
        window.addEventListener('online', () => {
            window.shareIsOnline = true;
            if (currentTab === 'share') renderShareUI();
        });
        window.addEventListener('offline', () => {
            window.shareIsOnline = false;
            if (currentTab === 'share') renderShareUI();
        });

        // Helper format ukuran file persis ShareScreen.kt
        function formatShareFileSize(bytes) {
            bytes = Number(bytes) || 0;
            if (bytes <= 0) return "0 B";
            const units = ["B", "KB", "MB", "GB"];
            const digitGroups = Math.floor(Math.log10(bytes) / Math.log10(1024));
            const idx = Math.min(Math.max(digitGroups, 0), units.length - 1);
            return (bytes / Math.pow(1024, idx)).toFixed(1) + " " + units[idx];
        }

        // Helper ekstensi file persis ShareScreen.kt
        function getShareFileExtension(name) {
            if (!name) return "DATA";
            const lastDot = name.lastIndexOf('.');
            if (lastDot !== -1 && lastDot < name.length - 1) {
                return name.substring(lastDot + 1).toUpperCase();
            }
            return "DATA";
        }

        // Helper warna ekstensi persis ShareScreen.kt
        function getShareExtensionColor(ext) {
            switch(ext) {
                case "PDF": return "#D32F2F";
                case "ZIP":
                case "RAR":
                case "7Z":
                case "TAR":
                case "GZ": return "#7B1FA2";
                case "XLS":
                case "XLSX":
                case "CSV": return "#388E3C";
                case "DOC":
                case "DOCX": return "#1976D2";
                case "JPG":
                case "JPEG":
                case "PNG":
                case "WEBP":
                case "GIF": return "#F57C00";
                case "TXT": return "#455A64";
                default: return "#303F9F";
            }
        }

        // Helper icon ekstensi Bootstrap Icon
        function getShareExtensionIcon(ext) {
            switch(ext) {
                case "PDF": return "bi-filetype-pdf";
                case "ZIP":
                case "RAR":
                case "7Z":
                case "TAR":
                case "GZ": return "bi-file-earmark-zip";
                case "XLS":
                case "XLSX":
                case "CSV": return "bi-file-earmark-spreadsheet";
                case "DOC":
                case "DOCX": return "bi-file-earmark-word";
                case "JPG":
                case "JPEG":
                case "PNG":
                case "WEBP":
                case "GIF": return "bi-file-earmark-image";
                case "TXT": return "bi-file-earmark-text";
                default: return "bi-file-earmark";
            }
        }

        // Helper kategori tipe persis getFileTypeGroup di ShareScreen.kt
        function getShareFileTypeGroup(item) {
            const name = (item.name || '').toLowerCase();
            const mime = (item.mimeType || '').toLowerCase();
            if (name.endsWith('.pdf') || mime.includes('pdf')) return "PDF";
            if (name.endsWith('.jpg') || name.endsWith('.jpeg') || name.endsWith('.png') || name.endsWith('.gif') || name.endsWith('.webp') || mime.includes('image')) return "Gambar";
            if (name.endsWith('.doc') || name.endsWith('.docx') || name.endsWith('.xls') || name.endsWith('.xlsx') || name.endsWith('.txt') || name.endsWith('.ppt') || name.endsWith('.pptx') || mime.includes('document') || mime.includes('sheet') || mime.includes('text') || mime.includes('presentation')) return "Dokumen";
            if (name.endsWith('.zip') || name.endsWith('.rar') || name.endsWith('.7z') || name.endsWith('.tar') || name.endsWith('.gz') || mime.includes('zip') || mime.includes('compressed') || mime.includes('archive')) return "Arsip";
            return "Lainnya";
        }

        // Toast Notifikasi Khusus Halaman Share
        function showShareToast(message, icon = 'bi-info-circle-fill', iconColor = '#0F766E') {
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

        // --- FILTER & SORT LOGIC PERSIS SHARESCREEN.KT ---
        function getFilteredAndSortedShareFiles() {
            let result = window.shareFiles || [];

            // 1. Filter Search Query
            const query = (window.shareSearchQuery || '').trim().toLowerCase();
            if (query.length > 0) {
                result = result.filter(item => {
                    const name = (item.name || '').toLowerCase();
                    const mime = (item.mimeType || '').toLowerCase();
                    return name.includes(query) || mime.includes(query);
                });
            }

            // 2. Filter Kategori Tipe
            if (window.shareSelectedType && window.shareSelectedType !== 'Semua') {
                result = result.filter(item => getShareFileTypeGroup(item) === window.shareSelectedType);
            }

            // 3. Sort List
            const sorted = [...result];
            switch(window.shareSelectedSort) {
                case 'Terbaru':
                    // default order dari API / ID
                    return sorted;
                case 'Terlama':
                    return sorted.reverse();
                case 'Nama A-Z':
                    return sorted.sort((a, b) => (a.name || '').localeCompare(b.name || ''));
                case 'Nama Z-A':
                    return sorted.sort((a, b) => (b.name || '').localeCompare(a.name || ''));
                case 'Ukuran Terbesar':
                    return sorted.sort((a, b) => (Number(b.size) || 0) - (Number(a.size) || 0));
                case 'Ukuran Terkecil':
                    return sorted.sort((a, b) => (Number(a.size) || 0) - (Number(b.size) || 0));
                default:
                    return sorted;
            }
        }

        // --- ENTRY POINT: LOAD SHARE ---
        async function loadShare() {
            // Coba ambil URL dari cache / backend jika ada
            try {
                const res = await fetch('/api/share');
                if (res.ok) {
                    const info = await res.json();
                    if (info.appsScriptUrl && info.appsScriptUrl.trim()) {
                        window.shareAppsScriptUrl = info.appsScriptUrl.trim();
                        localStorage.setItem('mysingghara_apps_script_url', window.shareAppsScriptUrl);
                    }
                }
            } catch(e) {}

            // Muat data dari cache terlebih dahulu agar 100% instan & offline
            let cached = null;
            if (typeof StorageCache !== 'undefined' && StorageCache && StorageCache.get) {
                cached = StorageCache.get('share_files');
            }
            if (!cached) {
                try {
                    const localData = localStorage.getItem('mysingghara_share_files');
                    if (localData) cached = JSON.parse(localData);
                } catch(e) {}
            }
            if (cached && Array.isArray(cached) && cached.length > 0) {
                window.shareFiles = cached;
            }

            renderShareUI();

            // Jika ada koneksi, sinkronkan daftar terbaru dari server
            if (navigator.onLine) {
                fetchShareFiles(window.shareFiles.length > 0);
            }
        }

        // --- AMBIL DAFTAR FILE DARI GOOGLE DRIVE ---
        async function fetchShareFiles(silent = false) {
            if (!navigator.onLine) {
                window.shareIsOnline = false;
                renderShareUI();
                return;
            }
            window.shareIsOnline = true;

            if (!silent) {
                window.shareIsLoading = true;
                renderShareUI();
            }

            try {
                // Panggil endpoint backend Android yang akan mem-proxy ke Google Apps Script (bebas CORS)
                const res = await fetch('/api/share?action=list');
                if (res.ok) {
                    const data = await res.json();
                    if (data && data.status === 'success' && Array.isArray(data.files)) {
                        window.shareFiles = data.files.map(f => ({
                            id: f.id,
                            name: f.name,
                            mimeType: f.mimeType,
                            size: Number(f.size) || 0,
                            url: f.url || '',
                            createdTime: f.createdTime || f.date || ''
                        }));

                        // Simpan ke Cache Offline
                        if (typeof StorageCache !== 'undefined' && StorageCache && StorageCache.set) {
                            StorageCache.set('share_files', window.shareFiles);
                        }
                        try {
                            localStorage.setItem('mysingghara_share_files', JSON.stringify(window.shareFiles));
                        } catch(e) {}

                        window.shareSelectedIds.clear();
                        window.shareIsLoading = false;
                        renderShareUI();
                        return;
                    }
                }
                // Fallback jika /api/share gagal, coba panggil langsung Apps Script jika diizinkan browser
                if (window.shareAppsScriptUrl) {
                    const directUrl = window.shareAppsScriptUrl + (window.shareAppsScriptUrl.includes('?') ? '&' : '?') + 'action=list';
                    const directRes = await fetch(directUrl);
                    if (directRes.ok) {
                        const directData = await directRes.json();
                        if (directData && directData.status === 'success' && Array.isArray(directData.files)) {
                            window.shareFiles = directData.files;
                            if (typeof StorageCache !== 'undefined' && StorageCache && StorageCache.set) {
                                StorageCache.set('share_files', window.shareFiles);
                            }
                            try {
                                localStorage.setItem('mysingghara_share_files', JSON.stringify(window.shareFiles));
                            } catch(e) {}
                            window.shareSelectedIds.clear();
                            window.shareIsLoading = false;
                            renderShareUI();
                            return;
                        }
                    }
                }
            } catch (err) {
                console.warn("fetchShareFiles failed:", err);
            }

            window.shareIsLoading = false;
            // Jika tidak ada data sama sekali dan offline/error, tandai offline
            if (window.shareFiles.length === 0) {
                window.shareIsOnline = false;
            }
            renderShareUI();
        }

        // --- RENDER SELURUH TAMPILAN SHARE (PERSIS SHARESCREEN.KT) ---
        function renderShareUI() {
            const container = document.getElementById('tab-content') || document.getElementById('main-content');
            if (!container) return;

            // 1. STATE OFFLINE PERSIS ANIMATEDOFFLINESTATE DI SHARESCREEN.KT
            if (!window.shareIsOnline && (!window.shareFiles || window.shareFiles.length === 0)) {
                container.innerHTML = `
                    <div class="d-flex flex-column align-items-center justify-content-center text-center p-4 py-5" style="min-height: 70vh;">
                        <!-- ANIMATED FLOATING OFFLINE BADGE (PERSIS SHARESCREEN.KT) -->
                        <div class="share-offline-container position-relative mb-4">
                            <div class="share-offline-glow"></div>
                            <div class="share-offline-spin-ring">
                                <i class="bi bi-arrow-repeat text-primary" style="font-size: 5rem; opacity: 0.35;"></i>
                            </div>
                            <div class="share-offline-center-circle shadow">
                                <i class="bi bi-cloud-slash text-primary" style="font-size: 2.2rem;"></i>
                            </div>
                        </div>

                        <h4 class="fw-bold text-main mb-2">Koneksi Terputus</h4>
                        <p class="text-muted mx-auto mb-4" style="max-width: 440px; font-size: 13.5px; line-height: 1.6;">
                            Halaman Share memerlukan koneksi internet aktif. Silakan aktifkan Wi-Fi atau Data Seluler Anda, lalu segarkan halaman.
                        </p>
                        <div class="d-flex flex-wrap justify-content-center gap-2">
                            <button class="btn btn-primary px-4 py-2 d-flex align-items-center gap-2 fw-semibold shadow-sm" style="border-radius: 10px;" onclick="retryShareConnection()">
                                <i class="bi bi-arrow-clockwise fs-5"></i>
                                <span>Segarkan Jaringan</span>
                            </button>
                            <button class="btn btn-outline-secondary px-3 py-2 fw-semibold" style="border-radius: 10px;" onclick="forceOpenShareOfflineCache()">
                                <i class="bi bi-hdd-network me-1"></i> Buka Cache
                            </button>
                            <button class="btn btn-outline-primary px-3 py-2 fw-semibold" style="border-radius: 10px;" onclick="switchTabByName('transfer_file')">
                                <i class="bi bi-arrow-left-right me-1"></i> Transfer File (100% Offline)
                            </button>
                        </div>
                    </div>
                `;
                return;
            }

            // 2. STATE URL APPS SCRIPT BELUM ADA
            if (!window.shareAppsScriptUrl || window.shareAppsScriptUrl.trim().length === 0) {
                container.innerHTML = `
                    <div class="d-flex flex-column align-items-center justify-content-center text-center p-4 py-5" style="min-height: 70vh;">
                        <div class="mb-3 text-primary">
                            <i class="bi bi-cloud-slash" style="font-size: 4rem;"></i>
                        </div>
                        <h4 class="fw-bold text-main mb-2">Penyimpanan Cloud Belum Aktif</h4>
                        <p class="text-muted mx-auto mb-4" style="max-width: 480px; font-size: 13.5px;">
                            URL Google Apps Script belum dikonfigurasi di aplikasi ini. Anda dapat mengaktifkannya atau menggunakan URL default bawaan.
                        </p>
                        <div class="d-flex flex-wrap gap-2">
                            <button class="btn btn-primary px-4 py-2 fw-semibold shadow-sm" style="border-radius: 10px;" onclick="resetShareDefaultUrl()">
                                <i class="bi bi-cloud-check-fill me-2"></i> Aktifkan URL Default
                            </button>
                            <button class="btn btn-outline-primary px-4 py-2 fw-semibold" style="border-radius: 10px;" onclick="switchTabByName('transfer_file')">
                                <i class="bi bi-arrow-left-right me-1"></i> Transfer File (100% Offline)
                            </button>
                        </div>
                    </div>
                `;
                return;
            }

            // 3. ONLINE STATE DENGAN SEMUA ELEMEN PERSIS SHARESCREEN.KT
            const files = getFilteredAndSortedShareFiles();
            const totalCount = (window.shareFiles || []).length;
            const selectedCount = window.shareSelectedIds.size;
            const allSelected = files.length > 0 && selectedCount === files.length;
            const typeOptions = ["Semua", "PDF", "Gambar", "Dokumen", "Arsip", "Lainnya"];

            container.innerHTML = `
                <div class="d-flex flex-column gap-3 p-1 p-md-3" style="max-width: 1100px; margin: 0 auto;">
                    
                    <!-- SUB-MENU SWITCHER (Share Cloud vs Transfer File) -->
                    <div class="d-flex p-1 rounded-3 transfer-sub-menu-bar mb-1" style="max-width: 420px;">
                        <button class="btn btn-sm flex-fill fw-bold shadow-sm transfer-sub-btn-active" style="border-radius: 8px;">
                            <i class="bi bi-share me-1"></i> Share (Cloud)
                        </button>
                        <button class="btn btn-sm flex-fill fw-medium transfer-sub-btn-inactive" style="border-radius: 8px;" onclick="switchTabByName('transfer_file')">
                            <i class="bi bi-arrow-left-right me-1"></i> Transfer File (Offline)
                        </button>
                    </div>

                    <!-- HEADER STATUS TERHUBUNG (PERSIS SHARESCREEN.KT) -->
                    <div class="share-header-banner">
                        <div class="d-flex align-items-center gap-2 gap-md-3 overflow-hidden">
                            <div class="share-icon-circle shadow-sm">
                                <i class="bi bi-cloud-check-fill text-primary"></i>
                            </div>
                            <div class="d-flex flex-column text-truncate">
                                <div class="fw-bold text-main" style="font-size: 13px;">Penyimpanan Cloud Aktif</div>
                                <div class="text-muted text-truncate" style="font-size: 11px;">
                                    ${dol}{window.shareIsLoading ? 'Mensinkronkan berkas...' : 'Terhubung ke Google Drive'} • ${dol}{totalCount} berkas
                                </div>
                            </div>
                        </div>

                        <div class="d-flex align-items-center gap-1 gap-md-2">
                            <button class="btn btn-sm btn-outline-secondary d-flex align-items-center justify-content-center p-2 rounded-circle" 
                                    style="width: 34px; height: 34px;" title="Pengaturan URL Cloud" onclick="openShareConfigModal()">
                                <i class="bi bi-gear fs-6"></i>
                            </button>
                            <button class="btn btn-sm btn-outline-primary d-flex align-items-center justify-content-center p-2 rounded-circle ${dol}{window.shareIsLoading ? 'spinning' : ''}" 
                                    style="width: 34px; height: 34px;" title="Segarkan Berkas" onclick="fetchShareFiles(false)">
                                <i class="bi bi-arrow-clockwise fs-6"></i>
                            </button>
                        </div>
                    </div>

                    <!-- BARIS TOMBOL UTAMA: UPLOAD BARU + DOWNLOAD ZIP (PERSIS SHARESCREEN.KT) -->
                    <div class="row g-2">
                        <div class="col-6">
                            <input type="file" id="shareFileInput" multiple style="display: none;" onchange="onShareFilesSelected(event)">
                            <button type="button" class="btn btn-primary share-btn-action w-100" 
                                    onclick="triggerShareUpload()" ${dol}{window.shareIsUploading ? 'disabled' : ''}>
                                ${dol}{window.shareIsUploading ? `
                                    <div class="spinner-border spinner-border-sm me-1" role="status"></div>
                                    <span>Unggah ${dol}{window.shareUploadProgress}%</span>
                                ` : `
                                    <i class="bi bi-cloud-arrow-up fs-5"></i>
                                    <span>Upload Baru</span>
                                `}
                            </button>
                        </div>
                        <div class="col-6">
                            <button type="button" class="btn btn-secondary share-btn-action w-100" 
                                    onclick="downloadShareZip()" style="background-color: #475569; border-color: #475569;">
                                <i class="bi bi-cloud-arrow-down fs-5"></i>
                                <span>Download ZIP</span>
                            </button>
                        </div>
                    </div>

                    <!-- DETAILED UPLOAD STATUS ALERT (PERSIS SHARESCREEN.KT) -->
                    ${dol}{window.shareIsUploading ? `
                        <div class="alert alert-info py-2 px-3 mb-0 d-flex flex-column gap-1 border-0 shadow-sm" style="border-radius: 10px; background-color: rgba(56, 189, 248, 0.15); color: #0284C7;">
                            <div class="d-flex justify-content-between align-items-center" style="font-size: 11.5px; font-weight: 600;">
                                <span>${dol}{escapeHtml(window.shareUploadStatusText || 'Sedang mengunggah berkas...')}</span>
                                <span>${dol}{window.shareUploadProgress}%</span>
                            </div>
                            <div class="progress" style="height: 6px; border-radius: 3px; background-color: rgba(2, 132, 199, 0.2);">
                                <div class="progress-bar progress-bar-striped progress-bar-animated bg-info" style="width: ${dol}{window.shareUploadProgress}%;"></div>
                            </div>
                        </div>
                    ` : ''}

                    <!-- SEARCH BAR FILTER (PERSIS SHARESCREEN.KT) -->
                    <div class="position-relative">
                        <div class="input-group" style="border-radius: 10px; overflow: hidden; border: 1px solid var(--border-color);">
                            <span class="input-group-text bg-surface border-0 pe-1 text-muted">
                                <i class="bi bi-search fs-6"></i>
                            </span>
                            <input type="text" class="form-control bg-surface border-0 ps-2" 
                                   style="height: 48px; font-size: 13px;" 
                                   placeholder="Cari file..." 
                                   value="${dol}{escapeHtml(window.shareSearchQuery)}" 
                                   oninput="onShareSearchInput(this.value)">
                            ${dol}{window.shareSearchQuery ? `
                                <button class="btn bg-surface border-0 text-muted" type="button" onclick="clearShareSearch()">
                                    <i class="bi bi-x-lg"></i>
                                </button>
                            ` : ''}
                        </div>
                    </div>

                    <!-- ROW COMBINING SORT BUTTON & SCROLLABLE CHIPS (PERSIS SHARESCREEN.KT) -->
                    <div class="d-flex align-items-center gap-2">
                        <!-- SORT DROPDOWN BUTTON -->
                        <div class="dropdown">
                            <button class="btn btn-outline-secondary d-flex align-items-center justify-content-center p-2" 
                                    style="width: 38px; height: 38px; border-radius: 8px;" 
                                    type="button" data-bs-toggle="dropdown" title="Urutkan: ${dol}{window.shareSelectedSort}">
                                <i class="bi bi-sort-down fs-5 text-primary"></i>
                            </button>
                            <ul class="dropdown-menu shadow border-0" style="font-size: 12.5px;">
                                ${dol}{['Terbaru', 'Terlama', 'Nama A-Z', 'Nama Z-A', 'Ukuran Terbesar', 'Ukuran Terkecil'].map(opt => `
                                    <li>
                                        <a class="dropdown-item d-flex align-items-center justify-content-between py-2 ${dol}{window.shareSelectedSort === opt ? 'active fw-bold' : ''}" 
                                           href="javascript:void(0)" onclick="setShareSort('${dol}{opt}')">
                                            <span>${dol}{opt}</span>
                                            ${dol}{window.shareSelectedSort === opt ? '<i class="bi bi-check2 ms-2"></i>' : ''}
                                        </a>
                                    </li>
                                `).join('')}
                            </ul>
                        </div>

                        <!-- HORIZONTAL TYPE FILTER CHIPS (SCROLLABLE ROW) -->
                        <div class="d-flex align-items-center gap-2 overflow-auto py-1 flex-grow-1" style="scrollbar-width: none;">
                            ${dol}{typeOptions.map(t => {
                                const isSelected = window.shareSelectedType === t;
                                return `
                                    <div class="share-filter-chip ${dol}{isSelected ? 'active' : ''}" onclick="setShareTypeFilter('${dol}{t}')">
                                        ${dol}{isSelected ? '<i class="bi bi-check fs-6 text-primary"></i>' : ''}
                                        <span>${dol}{t}</span>
                                    </div>
                                `;
                            }).join('')}
                        </div>
                    </div>

                    <!-- ANIMATED SELECTION ACTION BAR (PERSIS SHARESCREEN.KT) -->
                    ${dol}{selectedCount > 0 ? `
                        <div class="share-selection-bar">
                            <div class="d-flex align-items-center gap-2">
                                <button class="btn btn-sm btn-link text-main p-0 me-1" onclick="clearShareSelection()" title="Batal Pilih">
                                    <i class="bi bi-x-lg fs-5"></i>
                                </button>
                                <span class="fw-bold" style="font-size: 13px;">${dol}{selectedCount} file terpilih</span>
                            </div>

                            <div class="d-flex align-items-center gap-2">
                                <button class="btn btn-sm btn-link text-primary text-decoration-none fw-bold p-0 me-2" 
                                        style="font-size: 12px;" onclick="toggleShareSelectAll()">
                                    ${dol}{allSelected ? 'Batal Semua' : 'Pilih Semua'}
                                </button>

                                <button class="btn btn-sm btn-outline-primary d-flex align-items-center justify-content-center p-1 rounded-circle" 
                                        style="width: 32px; height: 32px;" title="Buka Terpilih" onclick="openSelectedShareFiles()">
                                    <i class="bi bi-box-arrow-up-right"></i>
                                </button>

                                <button class="btn btn-sm btn-outline-danger d-flex align-items-center justify-content-center p-1 rounded-circle" 
                                        style="width: 32px; height: 32px;" title="Hapus Terpilih" onclick="confirmBulkDeleteShareFiles()">
                                    <i class="bi bi-trash"></i>
                                </button>
                            </div>
                        </div>
                    ` : ''}

                    <!-- TABLE BODY / FILE LIST CARD (PERSIS SHARESCREEN.KT) -->
                    <div class="share-file-list-card">
                        ${dol}{window.shareIsLoading ? `
                            <div class="d-flex flex-column align-items-center justify-content-center p-5 text-center">
                                <div class="spinner-border text-primary mb-3" style="width: 2.5rem; height: 2.5rem;" role="status"></div>
                                <div class="text-muted" style="font-size: 13px;">Mengambil daftar file...</div>
                            </div>
                        ` : files.length === 0 ? `
                            <div class="d-flex flex-column align-items-center justify-content-center p-5 text-center">
                                <i class="bi bi-folder2-open text-muted mb-2" style="font-size: 3rem; opacity: 0.5;"></i>
                                <div class="fw-bold text-main mb-1" style="font-size: 14px;">Tidak Ada File</div>
                                <div class="text-muted" style="font-size: 12px; max-width: 340px;">
                                    Tidak ada berkas yang sesuai dengan pencarian atau filter tipe Anda.
                                </div>
                            </div>
                        ` : `
                            <div class="d-flex flex-column">
                                ${dol}{files.map(item => {
                                    const isSelected = window.shareSelectedIds.has(item.id);
                                    const ext = getShareFileExtension(item.name);
                                    const extColor = getShareExtensionColor(ext);
                                    const extIcon = getShareExtensionIcon(ext);
                                    const formattedSize = formatShareFileSize(item.size);

                                    return `
                                        <div class="share-file-row ${dol}{isSelected ? 'selected' : ''}" id="share-file-${dol}{item.id}">
                                            <!-- CHECKBOX UNTUK SELEKSI -->
                                            <div class="form-check m-0 p-0 d-flex align-items-center">
                                                <input class="form-check-input ms-0 me-1" type="checkbox" 
                                                       ${dol}{isSelected ? 'checked' : ''} 
                                                       onclick="event.stopPropagation(); toggleShareSelect('${dol}{item.id}')" 
                                                       style="cursor: pointer; width: 18px; height: 18px;">
                                            </div>

                                            <!-- LINGKARAN IKON FILE PERSIS SHARESCREEN.KT -->
                                            <div class="share-file-icon-badge" style="background-color: ${dol}{extColor}1A; border-color: ${dol}{extColor}33;">
                                                <i class="bi ${dol}{extIcon}" style="color: ${dol}{extColor};"></i>
                                            </div>

                                            <!-- DETAIL FILE (NAMA, EKSTENSI & UKURAN) -->
                                            <div class="d-flex flex-column flex-grow-1 overflow-hidden" 
                                                 style="cursor: pointer;" onclick="onShareRowClicked('${dol}{item.id}', '${dol}{escapeJs(item.url)}')">
                                                <div class="fw-semibold text-main text-truncate" style="font-size: 13px;" title="${dol}{escapeHtml(item.name)}">
                                                    ${dol}{escapeHtml(item.name)}
                                                </div>
                                                <div class="text-muted" style="font-size: 11px;">
                                                    <span class="fw-semibold" style="color: ${dol}{extColor};">${dol}{ext}</span> • ${dol}{formattedSize}
                                                    ${dol}{item.createdTime ? `<span class="opacity-75 ms-1">• ${dol}{escapeHtml(item.createdTime.substring(0, 10))}</span>` : ''}
                                                </div>
                                            </div>

                                            <!-- TOMBOL AKSI MANDIRI: BUKA & HAPUS -->
                                            <div class="d-flex align-items-center gap-1">
                                                <button class="share-row-btn btn-open" title="Buka Berkas" 
                                                        onclick="event.stopPropagation(); openShareFile('${dol}{escapeJs(item.url)}')">
                                                    <i class="bi bi-box-arrow-up-right"></i>
                                                </button>
                                                <button class="share-row-btn btn-delete" title="Hapus Berkas" 
                                                        onclick="event.stopPropagation(); confirmDeleteShareFile('${dol}{item.id}', '${dol}{escapeJs(item.name)}')">
                                                    <i class="bi bi-trash"></i>
                                                </button>
                                            </div>
                                        </div>
                                    `;
                                }).join('')}
                            </div>
                        `}
                    </div>
                </div>
            `;
        }

        // --- INTERAKSI SEARCH, FILTER & SORT ---
        function onShareSearchInput(val) {
            window.shareSearchQuery = val;
            renderShareUI();
        }

        function clearShareSearch() {
            window.shareSearchQuery = '';
            renderShareUI();
        }

        function setShareTypeFilter(type) {
            window.shareSelectedType = type;
            renderShareUI();
        }

        function setShareSort(sortOpt) {
            window.shareSelectedSort = sortOpt;
            renderShareUI();
        }

        // --- SELEKSI MULTI FILE ---
        function toggleShareSelect(id) {
            if (window.shareSelectedIds.has(id)) {
                window.shareSelectedIds.delete(id);
            } else {
                window.shareSelectedIds.add(id);
            }
            renderShareUI();
        }

        function toggleShareSelectAll() {
            const files = getFilteredAndSortedShareFiles();
            if (window.shareSelectedIds.size === files.length && files.length > 0) {
                window.shareSelectedIds.clear();
            } else {
                files.forEach(f => window.shareSelectedIds.add(f.id));
            }
            renderShareUI();
        }

        function clearShareSelection() {
            window.shareSelectedIds.clear();
            renderShareUI();
        }

        function onShareRowClicked(id, url) {
            if (window.shareSelectedIds.size > 0) {
                toggleShareSelect(id);
            } else {
                openShareFile(url);
            }
        }

        function openShareFile(url) {
            if (url && url.trim().length > 0) {
                window.open(url, '_blank');
            } else {
                showShareToast("URL file tidak tersedia.", "bi-exclamation-triangle", "#F59E0B");
            }
        }

        function openSelectedShareFiles() {
            const list = (window.shareFiles || []).filter(f => window.shareSelectedIds.has(f.id));
            let opened = 0;
            list.forEach(item => {
                if (item.url && item.url.trim().length > 0) {
                    window.open(item.url, '_blank');
                    opened++;
                }
            });
            showShareToast(`Membuka ${dol}{opened} file di penjelajah web...`, 'bi-box-arrow-up-right', '#0284C7');
        }

        // --- UPLOAD FILE PERSIS SHARESCREEN.KT ---
        function triggerShareUpload() {
            if (window.shareIsUploading) return;
            const input = document.getElementById('shareFileInput');
            if (input) {
                input.value = '';
                input.click();
            }
        }

        async function onShareFilesSelected(e) {
            const files = Array.from(e.target.files || []);
            if (files.length === 0) return;
            await uploadShareFiles(files);
        }

        async function uploadShareFiles(fileList) {
            if (!navigator.onLine) {
                showShareToast("Koneksi terputus. Pastikan internet aktif untuk mengunggah ke Google Drive.", "bi-wifi-off", "#EF4444");
                return;
            }

            const total = fileList.length;
            window.shareIsUploading = true;
            window.shareUploadProgress = 0;
            window.shareUploadStatusText = "Mempersiapkan pengunggahan...";
            renderShareUI();

            let successCount = 0;
            let failCount = 0;
            const maxLimit = 20 * 1024 * 1024; // 20 MB persis ShareScreen.kt

            for (let i = 0; i < total; i++) {
                const file = fileList[i];
                const currentNumber = i + 1;

                if (file.size > maxLimit) {
                    const mb = (file.size / (1024 * 1024)).toFixed(1);
                    showShareToast(`Berkas ${dol}{file.name} terlalu besar (${dol}{mb} MB). Batas maksimum adalah 20 MB.`, "bi-exclamation-octagon", "#EF4444");
                    failCount++;
                    continue;
                }

                window.shareUploadStatusText = `Mengunggah ${dol}{currentNumber}/${dol}{total}: ${dol}{file.name}`;
                window.shareUploadProgress = Math.round(((i) / total) * 100);
                renderShareUI();

                try {
                    // Baca file ke Base64
                    const base64Data = await new Promise((resolve, reject) => {
                        const reader = new FileReader();
                        reader.onload = () => {
                            const result = reader.result || '';
                            const b64 = result.includes(',') ? result.split(',')[1] : result;
                            resolve(b64);
                        };
                        reader.onerror = reject;
                        reader.readAsDataURL(file);
                    });

                    const payload = {
                        action: 'upload',
                        filename: file.name,
                        mimeType: file.type || 'application/octet-stream',
                        base64Data: base64Data
                    };

                    // Kirim ke proxy server Android
                    const res = await fetch('/api/share', {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify(payload)
                    });

                    if (res.ok) {
                        const json = await res.json();
                        if (json.status === 'success') {
                            successCount++;
                        } else {
                            failCount++;
                            showShareToast(`Gagal mengunggah ${dol}{file.name}: ${dol}{json.message || 'Server error'}`, "bi-x-circle", "#EF4444");
                        }
                    } else {
                        failCount++;
                    }
                } catch(err) {
                    console.error("Upload error:", err);
                    failCount++;
                }

                window.shareUploadProgress = Math.round(((i + 1) / total) * 100);
                renderShareUI();
            }

            window.shareIsUploading = false;
            window.shareUploadProgress = 0;
            window.shareUploadStatusText = '';

            if (failCount === 0) {
                showShareToast(`Berhasil mengunggah semua ${dol}{successCount} berkas!`, 'bi-cloud-check-fill', '#10B981');
            } else {
                showShareToast(`Selesai mengunggah. Sukses: ${dol}{successCount}, Gagal: ${dol}{failCount}`, 'bi-info-circle-fill', '#F59E0B');
            }

            // Segarkan daftar berkas
            await fetchShareFiles(true);
        }

        // --- DOWNLOAD SEMUA SEBAGAI ZIP PERSIS SHARESCREEN.KT ---
        async function downloadShareZip() {
            if (!navigator.onLine) {
                showShareToast("Koneksi terputus. Pastikan internet aktif untuk mengunduh ZIP.", "bi-wifi-off", "#EF4444");
                return;
            }

            showShareToast("Mengompres seluruh isi folder menjadi ZIP...", "bi-cloud-arrow-down", "#0284C7");

            try {
                const res = await fetch('/api/share?action=download_all');
                if (res.ok) {
                    const data = await res.json();
                    if (data && data.status === 'success' && data.base64) {
                        const byteCharacters = atob(data.base64);
                        const byteNumbers = new Array(byteCharacters.length);
                        for (let i = 0; i < byteCharacters.length; i++) {
                            byteNumbers[i] = byteCharacters.charCodeAt(i);
                        }
                        const byteArray = new Uint8Array(byteNumbers);
                        const blob = new Blob([byteArray], { type: 'application/zip' });
                        const link = document.createElement('a');
                        link.href = URL.createObjectURL(blob);
                        link.download = `My_Drive_Backup_${dol}{Date.now()}.zip`;
                        document.body.appendChild(link);
                        link.click();
                        document.body.removeChild(link);
                        showShareToast("ZIP berhasil disimpan ke perangkat!", "bi-check-circle-fill", "#10B981");
                        return;
                    }
                }
            } catch (e) {
                console.error("ZIP download error:", e);
            }
            showShareToast("Gagal mengunduh berkas ZIP. Periksa koneksi atau kapasitas server.", "bi-x-circle-fill", "#EF4444");
        }

        // --- HAPUS FILE TUNGGAL PERSIS SHARESCREEN.KT ---
        function confirmDeleteShareFile(fileId, fileName) {
            window.shareFileToDelete = { id: fileId, name: fileName };
            const modalEl = document.getElementById('shareDeleteConfirmModal');
            const targetText = document.getElementById('shareDeleteTargetName');
            if (targetText) targetText.textContent = `'${dol}{fileName}'`;
            if (modalEl) {
                const modal = new bootstrap.Modal(modalEl);
                modal.show();
            } else if (confirm(`Apakah Anda yakin ingin menghapus '${dol}{fileName}' secara permanen dari folder Google Drive Anda?`)) {
                executeDeleteShareFile();
            }
        }

        async function executeDeleteShareFile() {
            if (!window.shareFileToDelete) return;
            const item = window.shareFileToDelete;
            window.shareFileToDelete = null;

            // Tutup modal
            const modalEl = document.getElementById('shareDeleteConfirmModal');
            if (modalEl) {
                try { bootstrap.Modal.getInstance(modalEl)?.hide(); } catch(e) {}
            }

            try {
                const res = await fetch('/api/share', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ action: 'delete', fileId: item.id })
                });
                if (res.ok) {
                    showShareToast(`Berhasil menghapus: ${dol}{item.name}`, 'bi-trash-fill', '#10B981');
                    window.shareFiles = (window.shareFiles || []).filter(f => f.id !== item.id);
                    window.shareSelectedIds.delete(item.id);
                    if (typeof StorageCache !== 'undefined' && StorageCache && StorageCache.set) {
                        StorageCache.set('share_files', window.shareFiles);
                    }
                    try { localStorage.setItem('mysingghara_share_files', JSON.stringify(window.shareFiles)); } catch(e) {}
                    renderShareUI();
                } else {
                    showShareToast(`Gagal menghapus file ${dol}{item.name}`, 'bi-x-circle', '#EF4444');
                }
            } catch(e) {
                showShareToast(`Kesalahan jaringan saat menghapus file`, 'bi-x-circle', '#EF4444');
            }
        }

        // --- HAPUS MASSAL (BULK DELETE) PERSIS SHARESCREEN.KT ---
        function confirmBulkDeleteShareFiles() {
            const count = window.shareSelectedIds.size;
            if (count === 0) return;
            const modalEl = document.getElementById('shareBulkDeleteConfirmModal');
            const countText = document.getElementById('shareBulkDeleteCount');
            if (countText) countText.textContent = count;
            if (modalEl) {
                const modal = new bootstrap.Modal(modalEl);
                modal.show();
            } else if (confirm(`Apakah Anda yakin ingin menghapus ${dol}{count} file terpilih secara permanen dari Google Drive?`)) {
                executeBulkDeleteShareFiles();
            }
        }

        async function executeBulkDeleteShareFiles() {
            const modalEl = document.getElementById('shareBulkDeleteConfirmModal');
            if (modalEl) {
                try { bootstrap.Modal.getInstance(modalEl)?.hide(); } catch(e) {}
            }

            const ids = Array.from(window.shareSelectedIds);
            let successCount = 0;
            let failCount = 0;

            showShareToast(`Menghapus ${dol}{ids.length} file...`, 'bi-trash', '#0284C7');

            for (const id of ids) {
                try {
                    const res = await fetch('/api/share', {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({ action: 'delete', fileId: id })
                    });
                    if (res.ok) successCount++;
                    else failCount++;
                } catch(e) {
                    failCount++;
                }
            }

            window.shareFiles = (window.shareFiles || []).filter(f => !window.shareSelectedIds.has(f.id));
            window.shareSelectedIds.clear();
            if (typeof StorageCache !== 'undefined' && StorageCache && StorageCache.set) {
                StorageCache.set('share_files', window.shareFiles);
            }
            try { localStorage.setItem('mysingghara_share_files', JSON.stringify(window.shareFiles)); } catch(e) {}

            showShareToast(`Berhasil menghapus ${dol}{successCount} file. Gagal: ${dol}{failCount}.`, 'bi-check-circle-fill', '#10B981');
            renderShareUI();
        }

        // --- PENGATURAN URL CLOUD ---
        function openShareConfigModal() {
            const input = document.getElementById('shareConfigUrlInput');
            if (input) input.value = window.shareAppsScriptUrl || window.shareDefaultUrl;
            const modalEl = document.getElementById('shareConfigModal');
            if (modalEl) {
                const modal = new bootstrap.Modal(modalEl);
                modal.show();
            }
        }

        async function saveShareConfigUrl() {
            const input = document.getElementById('shareConfigUrlInput');
            if (!input) return;
            const newUrl = input.value.trim() || window.shareDefaultUrl;
            window.shareAppsScriptUrl = newUrl;
            localStorage.setItem('mysingghara_apps_script_url', newUrl);

            try {
                await fetch('/api/share', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ action: 'save_url', url: newUrl })
                });
            } catch(e) {}

            const modalEl = document.getElementById('shareConfigModal');
            if (modalEl) {
                try { bootstrap.Modal.getInstance(modalEl)?.hide(); } catch(e) {}
            }

            showShareToast("URL Google Apps Script berhasil disimpan!", "bi-cloud-check", "#10B981");
            fetchShareFiles(false);
        }

        function resetShareDefaultUrl() {
            window.shareAppsScriptUrl = window.shareDefaultUrl;
            localStorage.setItem('mysingghara_apps_script_url', window.shareDefaultUrl);
            try {
                fetch('/api/share', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ action: 'save_url', url: window.shareDefaultUrl })
                });
            } catch(e) {}
            renderShareUI();
            fetchShareFiles(false);
        }

        function retryShareConnection() {
            window.shareIsOnline = (typeof navigator.onLine !== 'undefined') ? navigator.onLine : true;
            fetchShareFiles(false);
        }

        function forceOpenShareOfflineCache() {
            window.shareIsOnline = true;
            renderShareUI();
        }
        """
    }
}
