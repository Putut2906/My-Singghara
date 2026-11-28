package com.example.web

object TransferFileWebScript {
    fun getScript(dol: String): String {
        return """
        // =========================================================================
        // --- TRANSFER FILE (100% OFFLINE ANTARA HP DAN PC BROWSER) ---
        // =========================================================================

        window.transferSharedFiles = [];
        window.transferReceivedFiles = [];
        window.transferLogs = [];
        window.transferInfo = null;
        window.transferActiveTab = 'send_to_phone'; // 'send_to_phone', 'files_from_phone', 'received_in_phone', 'phone_to_phone', 'logs'
        window.transferUploading = false;
        window.transferUploadProgress = 0;
        window.transferUploadStatus = '';
        window.transferPendingFiles = [];
        window.transferAutoSyncTimer = null;
        window.transferEventSource = null;
        window.transferLastSignature = '';
        window.transferPeers = [];
        window.transferP2pJobs = [];
        window.transferP2pSelected = '';
        window.transferP2pFiles = [];
        window.transferP2pBusy = false;
        window.transferP2pNotice = '';
        window.transferP2pRelayFile = null;

        async function loadTransferFile() {
            renderTransferFileUI();
            await fetchTransferData();
            startTransferAutoSync();
        }

        async function fetchTransferData() {
            try {
                const res = await fetch('/api/transfer_file?action=state', { cache: 'no-store' });
                if (!res.ok) return;
                applyTransferData(await res.json());
            } catch (err) {
                console.warn('Gagal memuat data transfer:', err);
            }
            renderTransferFileUI();
        }

        function applyTransferData(data) {
            window.transferInfo = data.info || window.transferInfo;
            window.transferSharedFiles = data.shared || [];
            window.transferReceivedFiles = data.received || [];
            window.transferLogs = data.logs || [];
            window.transferLastSignature = buildTransferSignature(
                window.transferSharedFiles, window.transferReceivedFiles, window.transferLogs);
        }

        function buildTransferSignature(shared, received, logs) {
            const key = (list) => (list || []).map(item =>
                String(item.id || '') + ':' + String(item.size || '') + ':' + String(item.timestamp || '')
            ).join('|');
            return key(shared) + '#' + key(received) + '#' + key(logs);
        }

        // Berkas yang dikirim dari HP harus langsung terlihat di PC tanpa tekan F5.
        // Halaman berlangganan jalur peristiwa server (SSE); polling tetap berjalan
        // sebagai cadangan bila koneksi peristiwa terputus atau dibatasi browser.
        function startTransferAutoSync() {
            stopTransferAutoSync();
            startTransferLiveFeed();
            window.transferAutoSyncTimer = setInterval(() => {
                if (currentTab === 'transfer_file') {
                    refreshTransferSilently();
                    if (window.transferActiveTab === 'phone_to_phone') fetchP2pData(false);
                } else {
                    stopTransferAutoSync();
                }
            }, 5000);
        }

        function startTransferLiveFeed() {
            if (!window.EventSource) return;
            try {
                const source = new EventSource('/api/transfer_file?action=events');
                window.transferEventSource = source;
                source.addEventListener('change', () => { refreshTransferSilently(); });
                source.addEventListener('ready', () => { refreshTransferSilently(); });
            } catch (err) {
                window.transferEventSource = null;
            }
        }

        function stopTransferAutoSync() {
            if (window.transferAutoSyncTimer) {
                clearInterval(window.transferAutoSyncTimer);
                window.transferAutoSyncTimer = null;
            }
            if (window.transferEventSource) {
                try { window.transferEventSource.close(); } catch (err) {}
                window.transferEventSource = null;
            }
        }

        // PC yang sedang di-minimize tidak mendapat timer; saat kembali dibuka,
        // daftar langsung ditarik ulang supaya tidak perlu F5.
        document.addEventListener('visibilitychange', () => {
            if (!document.hidden && currentTab === 'transfer_file') refreshTransferSilently();
        });
        window.addEventListener('focus', () => {
            if (currentTab === 'transfer_file') refreshTransferSilently();
        });

        async function refreshTransferSilently() {
            if (window.transferUploading || window.transferSyncBusy) return;
            window.transferSyncBusy = true;
            try {
                const res = await fetch('/api/transfer_file?action=state', { cache: 'no-store' });
                if (!res.ok) return;
                const data = await res.json();
                const shared = data.shared || [];
                const received = data.received || [];
                const logs = data.logs || [];
                const signature = buildTransferSignature(shared, received, logs);
                if (signature === window.transferLastSignature) return;

                const knownIds = {};
                (window.transferSharedFiles || []).forEach(item => { knownIds[item.id] = true; });
                const arrivals = shared.filter(item => !knownIds[item.id] && !item.isSelfApk);

                window.transferInfo = data.info || window.transferInfo;
                window.transferSharedFiles = shared;
                window.transferReceivedFiles = received;
                window.transferLogs = logs;
                window.transferLastSignature = signature;

                // Tab kirim punya input berkas native: render ulang di tab itu bisa
                // membatalkan jendela pemilihan berkas yang sedang terbuka.
                if (window.transferActiveTab === 'send_to_phone') updateTransferCounters();
                else renderTransferFileUI();

                if (arrivals.length > 0) {
                    showTransferNotification(arrivals.length + ' berkas baru dari HP siap diunduh', 'success');
                }
            } catch (err) {
                console.warn('Sinkron otomatis transfer gagal:', err);
            } finally {
                window.transferSyncBusy = false;
            }
        }

        function updateTransferCounters() {
            const sharedEl = document.getElementById('transferCountShared');
            if (sharedEl) sharedEl.textContent = '(' + (window.transferSharedFiles || []).length + ')';
            const receivedEl = document.getElementById('transferCountReceived');
            if (receivedEl) receivedEl.textContent = '(' + (window.transferReceivedFiles || []).length + ')';
        }

        function setTransferTab(tab) {
            window.transferActiveTab = tab;
            renderTransferFileUI();
            if (tab === 'phone_to_phone') fetchP2pData(true);
        }

        function formatTransferFileSize(bytes) {
            bytes = Number(bytes) || 0;
            if (bytes <= 0) return "0 B";
            const units = ["B", "KB", "MB", "GB"];
            const digitGroups = Math.floor(Math.log10(bytes) / Math.log10(1024));
            const idx = Math.min(Math.max(digitGroups, 0), units.length - 1);
            return (bytes / Math.pow(1024, idx)).toFixed(1) + " " + units[idx];
        }

        function getTransferFileIcon(ext, mime) {
            ext = (ext || '').toUpperCase();
            mime = (mime || '').toLowerCase();
            if (ext === 'APK') return 'bi-android2 text-success';
            if (ext === 'PDF' || mime.includes('pdf')) return 'bi-file-earmark-pdf-fill text-danger';
            if (['JPG', 'JPEG', 'PNG', 'WEBP', 'GIF'].includes(ext) || mime.includes('image')) return 'bi-file-earmark-image-fill text-warning';
            if (['MP3', 'WAV', 'M4A', 'FLAC', 'AAC'].includes(ext) || mime.includes('audio')) return 'bi-file-earmark-music-fill text-info';
            if (['MP4', 'MKV', 'AVI', 'MOV'].includes(ext) || mime.includes('video')) return 'bi-file-earmark-play-fill text-primary';
            if (['ZIP', 'RAR', '7Z', 'TAR'].includes(ext) || mime.includes('zip')) return 'bi-file-earmark-zip-fill text-purple';
            if (['DOC', 'DOCX', 'XLS', 'XLSX', 'PPT', 'PPTX', 'TXT'].includes(ext) || mime.includes('document')) return 'bi-file-earmark-text-fill text-primary';
            return 'bi-file-earmark-fill text-secondary';
        }

        function renderTransferFileUI() {
            const container = document.getElementById('tab-content') || document.getElementById('main-content');
            if (!container) return;

            const info = window.transferInfo || { deviceName: 'HP Android', ip: '127.0.0.1', port: 8080 };
            const sharedCount = (window.transferSharedFiles || []).length;
            const receivedCount = (window.transferReceivedFiles || []).length;

            container.innerHTML = `
                <div class="d-flex flex-column gap-3 p-1 p-md-3" style="max-width: 1100px; margin: 0 auto;">
                    
                    <!-- SUB-MENU SWITCHER (Share Cloud vs Transfer File) -->
                    <div class="d-flex p-1 rounded-3 transfer-sub-menu-bar mb-1" style="max-width: 420px;">
                        <button class="btn btn-sm flex-fill fw-medium transfer-sub-btn-inactive" style="border-radius: 8px;" onclick="switchTabByName('share')">
                            <i class="bi bi-share me-1"></i> Share (Cloud)
                        </button>
                        <button class="btn btn-sm flex-fill fw-bold shadow-sm transfer-sub-btn-active" style="border-radius: 8px;">
                            <i class="bi bi-arrow-left-right me-1"></i> Transfer File (Offline)
                        </button>
                    </div>

                    <!-- HEADER STATUS KONEKSI OFFLINE -->
                    <div class="card border-0 shadow-sm rounded-4 overflow-hidden transfer-header-card">
                        <div class="card-body p-3 p-md-4">
                            <div class="d-flex flex-wrap align-items-center justify-content-between gap-3">
                                <div class="d-flex align-items-center gap-3">
                                    <div class="rounded-circle d-flex align-items-center justify-content-center bg-white bg-opacity-20 shadow-sm" style="width: 52px; height: 52px;">
                                        <i class="bi bi-arrow-left-right fs-2 text-white"></i>
                                    </div>
                                    <div>
                                        <div class="d-flex align-items-center gap-2">
                                            <h5 class="fw-bold mb-0 text-white">Transfer File (HP & PC)</h5>
                                            <span class="badge transfer-badge-offline fw-bold" style="font-size: 11px;">100% Offline</span>
                                        </div>
                                        <div class="small text-white text-opacity-90 mt-1">
                                            <i class="bi bi-phone me-1"></i> ${dol}{escapeHtml(info.deviceName || 'HP Android')} 
                                            <span class="mx-1">•</span>
                                            <i class="bi bi-wifi me-1"></i> Jaringan Lokal Wi-Fi / Hotspot (${dol}{escapeHtml(info.ip)}:${dol}{info.port || 8080})
                                        </div>
                                    </div>
                                </div>
                                <div class="d-flex align-items-center gap-2">
                                    <span class="small d-none d-md-inline-flex align-items-center gap-1 text-white text-opacity-75">
                                        <i class="bi bi-broadcast-pin"></i> Sinkron langsung aktif
                                    </span>
                                    <button class="btn btn-sm fw-semibold shadow-sm d-flex align-items-center gap-1 transfer-btn-refresh" onclick="fetchTransferData(); startTransferAutoSync();">
                                        <i class="bi bi-arrow-clockwise"></i> Segarkan
                                    </button>
                                </div>
                            </div>
                        </div>
                    </div>

                    <!-- SUB MENU TABS -->
                    <div class="d-flex flex-wrap gap-2 pb-2 transfer-tabs-nav">
                        <button class="btn btn-sm ${dol}{window.transferActiveTab === 'send_to_phone' ? 'btn-primary shadow-sm' : 'btn-outline-secondary'}" 
                                onclick="setTransferTab('send_to_phone')">
                            <i class="bi bi-upload me-1"></i> Kirim File ke HP
                            ${dol}{window.transferPendingFiles.length > 0 ? `<span class="badge bg-danger ms-1">${dol}{window.transferPendingFiles.length}</span>` : ''}
                        </button>
                        <button class="btn btn-sm ${dol}{window.transferActiveTab === 'files_from_phone' ? 'btn-primary shadow-sm' : 'btn-outline-secondary'}" 
                                onclick="setTransferTab('files_from_phone')">
                            <i class="bi bi-download me-1"></i> Berkas dari HP <span id="transferCountShared">(${dol}{sharedCount})</span>
                        </button>
                        <button class="btn btn-sm ${dol}{window.transferActiveTab === 'received_in_phone' ? 'btn-primary shadow-sm' : 'btn-outline-secondary'}" 
                                onclick="setTransferTab('received_in_phone')">
                            <i class="bi bi-folder-check me-1"></i> Berkas Diterima di HP <span id="transferCountReceived">(${dol}{receivedCount})</span>
                        </button>
                        <button class="btn btn-sm ${dol}{window.transferActiveTab === 'phone_to_phone' ? 'btn-primary shadow-sm' : 'btn-outline-secondary'}" 
                                onclick="setTransferTab('phone_to_phone')">
                            <i class="bi bi-phones me-1"></i> HP ke HP <span id="transferCountP2p">(${dol}{(window.transferPeers || []).length})</span>
                        </button>
                        <button class="btn btn-sm ${dol}{window.transferActiveTab === 'logs' ? 'btn-primary shadow-sm' : 'btn-outline-secondary'}" 
                                onclick="setTransferTab('logs')">
                            <i class="bi bi-clock-history me-1"></i> Riwayat Transfer
                        </button>
                    </div>

                    <!-- TAB CONTENT AREA -->
                    <div id="transferTabContent">
                        ${dol}{renderTransferActiveTabContent()}
                    </div>

                </div>
            `;

            setupTransferDropzone();
        }

        function renderTransferActiveTabContent() {
            switch(window.transferActiveTab) {
                case 'send_to_phone':
                    return renderSendToPhoneTab();
                case 'files_from_phone':
                    return renderFilesFromPhoneTab();
                case 'received_in_phone':
                    return renderReceivedInPhoneTab();
                case 'phone_to_phone':
                    return renderPhoneToPhoneTab();
                case 'logs':
                    return renderTransferLogsTab();
                default:
                    return renderSendToPhoneTab();
            }
        }

        // --- 1. TAB: KIRIM FILE DARI PC KE HP ---
        function renderSendToPhoneTab() {
            const hasPending = window.transferPendingFiles.length > 0;
            const isUploading = window.transferUploading;

            return `
                <div class="card border-0 shadow-sm rounded-4 p-3 p-md-4 transfer-card">
                    <div class="d-flex justify-content-between align-items-center mb-3">
                        <div>
                            <h6 class="fw-bold mb-1"><i class="bi bi-cloud-arrow-up-fill text-primary me-2"></i>Pilih Berkas dari PC untuk Dikirim ke HP</h6>
                            <div class="small text-muted">Berkas akan dikirim langsung ke memori HP melalui Wi-Fi lokal tanpa internet.</div>
                        </div>
                    </div>

                    <!-- DROPZONE -->
                    <div id="transferDropzone" class="p-4 p-md-5 text-center rounded-4 border border-2 border-dashed position-relative transfer-dropzone" 
                         style="cursor: pointer; transition: all 0.2s;">
                        <input type="file" id="transferFileInput" multiple class="position-absolute top-0 start-0 w-100 h-100 opacity-0" style="cursor: pointer;" onchange="handleTransferFilesSelected(event)" />
                        <div class="mb-3">
                            <i class="bi bi-cloud-arrow-up text-primary" style="font-size: 3.5rem;"></i>
                        </div>
                        <h6 class="fw-bold text-main mb-1">Klik atau Seret Berkas ke Area Ini</h6>
                        <p class="text-muted small mb-0">Dapat memilih satu atau banyak berkas sekaligus (Foto, Dokumen, Video, Musik, ZIP, dll)</p>
                    </div>

                    <!-- PENDING FILES LIST -->
                    ${dol}{hasPending ? `
                        <div class="mt-4">
                            <div class="d-flex justify-content-between align-items-center mb-2">
                                <span class="fw-bold small text-main">Daftar Berkas Siap Dikirim (${dol}{window.transferPendingFiles.length} berkas)</span>
                                <button class="btn btn-sm btn-link text-danger text-decoration-none p-0" onclick="clearTransferPendingFiles()" ${dol}{isUploading ? 'disabled' : ''}>
                                    <i class="bi bi-trash"></i> Bersihkan
                                </button>
                            </div>

                            <div class="list-group list-group-flush border rounded-3 mb-3 transfer-list-group" style="max-height: 280px; overflow-y: auto;">
                                ${dol}{window.transferPendingFiles.map((file, idx) => `
                                    <div class="list-group-item d-flex justify-content-between align-items-center py-2 px-3 transfer-list-item">
                                        <div class="d-flex align-items-center gap-2 text-truncate me-2">
                                            <i class="bi ${dol}{getTransferFileIcon(file.name.split('.').pop(), file.type)} fs-5"></i>
                                            <div class="text-truncate">
                                                <div class="text-truncate fw-semibold small" style="max-width: 400px;">${dol}{escapeHtml(file.name)}</div>
                                                <div class="text-muted" style="font-size: 11px;">${dol}{formatTransferFileSize(file.size)}</div>
                                            </div>
                                        </div>
                                        <button class="btn btn-sm btn-outline-danger border-0 p-1" onclick="removeTransferPendingFile(${dol}{idx})" ${dol}{isUploading ? 'disabled' : ''}>
                                            <i class="bi bi-x-lg"></i>
                                        </button>
                                    </div>
                                `).join('')}
                            </div>

                            <!-- UPLOAD PROGRESS -->
                            ${dol}{isUploading ? `
                                <div class="mb-3">
                                    <div class="d-flex justify-content-between small fw-bold mb-1">
                                        <span>${dol}{escapeHtml(window.transferUploadStatus || 'Mengirim berkas ke HP...')}</span>
                                        <span>${dol}{window.transferUploadProgress}%</span>
                                    </div>
                                    <div class="progress transfer-progress" style="height: 8px;">
                                        <div class="progress-bar progress-bar-striped progress-bar-animated bg-primary" style="width: ${dol}{window.transferUploadProgress}%"></div>
                                    </div>
                                </div>
                            ` : ''}

                            <!-- SUBMIT BUTTON -->
                            <button class="btn btn-primary w-100 py-2 fw-bold d-flex align-items-center justify-content-center gap-2 shadow-sm rounded-3" 
                                    onclick="startUploadToPhone()" ${dol}{isUploading ? 'disabled' : ''}>
                                ${dol}{isUploading ? `
                                    <span class="spinner-border spinner-border-sm"></span>
                                    <span>Mengirim ke HP (${dol}{window.transferUploadProgress}%)...</span>
                                ` : `
                                    <i class="bi bi-send-fill"></i>
                                    <span>Kirim ${dol}{window.transferPendingFiles.length} Berkas Sekarang</span>
                                `}
                            </button>
                        </div>
                    ` : ''}
                </div>
            `;
        }

        function setupTransferDropzone() {
            const dz = document.getElementById('transferDropzone');
            if (!dz) return;
            const isDark = () => document.documentElement.getAttribute('data-theme') === 'dark' || document.body.classList.contains('dark');
            ['dragenter', 'dragover'].forEach(eventName => {
                dz.addEventListener(eventName, (e) => {
                    e.preventDefault();
                    dz.style.backgroundColor = isDark() ? 'rgba(56, 189, 248, 0.15)' : 'rgba(2, 132, 199, 0.12)';
                }, false);
            });
            ['dragleave', 'drop'].forEach(eventName => {
                dz.addEventListener(eventName, (e) => {
                    e.preventDefault();
                    dz.style.backgroundColor = isDark() ? '#0f172a' : '#f8fafc';
                }, false);
            });
        }

        function handleTransferFilesSelected(event) {
            const files = event.target.files;
            if (!files || files.length === 0) return;
            for (let i = 0; i < files.length; i++) {
                window.transferPendingFiles.push(files[i]);
            }
            renderTransferFileUI();
        }

        function removeTransferPendingFile(index) {
            window.transferPendingFiles.splice(index, 1);
            renderTransferFileUI();
        }

        function clearTransferPendingFiles() {
            window.transferPendingFiles = [];
            renderTransferFileUI();
        }

        async function startUploadToPhone() {
            if (window.transferPendingFiles.length === 0 || window.transferUploading) return;
            try {
                window.transferUploading = true;
                window.transferUploadProgress = 0;
                renderTransferFileUI();

                const total = window.transferPendingFiles.length;
                let successCount = 0;
                let lastPaint = -1;

                for (let i = 0; i < total; i++) {
                    const file = window.transferPendingFiles[i];
                    window.transferUploadStatus = 'Mengirim berkas (' + (i + 1) + '/' + total + '): ' + file.name;
                    window.transferUploadProgress = Math.round((i / total) * 100);
                    renderTransferFileUI();

                    try {
                        // Body request = byte berkas murni. Dulu berkas di-base64 lalu dibungkus
                        // JSON, sehingga mentok di batas body 128 MB dan membebani memori HP.
                        const ok = await uploadRawFile(file, '/api/transfer_file?action=upload_raw', '', (loaded, size) => {
                            const part = size > 0 ? loaded / size : 0;
                            const pct = Math.min(99, Math.round(((i + part) / total) * 100));
                            if (pct !== lastPaint) {
                                lastPaint = pct;
                                window.transferUploadProgress = pct;
                                renderTransferFileUI();
                            }
                        });
                        if (ok) successCount++;
                    } catch (e) {
                        console.error('Gagal upload berkas:', file.name, e);
                    }
                }

                window.transferUploadProgress = 100;
                window.transferUploadStatus = 'Selesai! ' + successCount + ' berkas berhasil dikirim ke HP.';
                renderTransferFileUI();

                setTimeout(async () => {
                    window.transferUploading = false;
                    window.transferPendingFiles = [];
                    window.transferUploadProgress = 0;
                    window.transferUploadStatus = '';
                    await fetchTransferData();
                    const pesan = successCount > 0
                        ? 'Sukses mengirim ' + successCount + ' berkas ke HP!'
                        : 'Tidak ada berkas yang berhasil dikirim ke HP.';
                    showTransferNotification(pesan, successCount > 0 ? 'success' : 'info');
                }, 1200);
            } catch (err) {
                // Flag harus selalu dilepas: selama transferUploading masih true,
                // refreshTransferSilently dibekukan, jadi daftar hanya mau muncul
                // sesudah tekan F5.
                console.error('Upload berhenti karena galat:', err);
                window.transferUploading = false;
                window.transferUploadStatus = 'Pengiriman gagal: ' + (err && err.message ? err.message : 'galat tidak dikenal');
                renderTransferFileUI();
                showTransferNotification(window.transferUploadStatus, 'error');
            }
        }

        /**
         * Unggah satu File sebagai stream lewat XMLHttpRequest supaya progres per-byte
         * ada (fetch tidak memberi kabar unggah) dan HP penerima bisa menulis ke disk
         * tanpa menelan seluruh berkas ke memori.
         */
        function uploadRawFile(file, url, peerId, onProgress) {
            return new Promise((resolve) => {
                const xhr = new XMLHttpRequest();
                xhr.open('POST', url, true);
                xhr.setRequestHeader('Content-Type', 'application/octet-stream');
                xhr.setRequestHeader('X-Filename', encodeURIComponent(file.name));
                if (peerId) xhr.setRequestHeader('X-Peer', peerId);
                xhr.upload.onprogress = (ev) => {
                    if (ev.lengthComputable && typeof onProgress === 'function') onProgress(ev.loaded, ev.total);
                };
                xhr.onload = () => {
                    let ok = xhr.status >= 200 && xhr.status < 300;
                    let message = '';
                    try {
                        const parsed = JSON.parse(xhr.responseText || '{}');
                        if (parsed && parsed.status) ok = parsed.status === 'success';
                        message = parsed.message || '';
                    } catch (e) {}
                    if (!ok && message) window.transferP2pNotice = message;
                    resolve(ok);
                };
                xhr.onerror = () => resolve(false);
                xhr.ontimeout = () => resolve(false);
                xhr.send(file);
            });
        }

        // --- 2. TAB: BERKAS DARI HP (SIAP DIUNDUH KE PC) ---
        function renderFilesFromPhoneTab() {
            const files = window.transferSharedFiles || [];

            return `
                <div class="card border-0 shadow-sm rounded-4 p-3 p-md-4 transfer-card">
                    <div class="d-flex flex-wrap justify-content-between align-items-center gap-2 mb-3">
                        <div>
                            <h6 class="fw-bold mb-1"><i class="bi bi-phone-fill text-success me-2"></i>Berkas dari HP (Siap Diunduh ke PC)</h6>
                            <div class="small text-muted">Berkas yang dibagikan dari aplikasi My Singghara di HP.</div>
                        </div>
                        ${dol}{files.length > 0 ? `
                            <a href="/api/transfer_file?action=download_all" class="btn btn-sm btn-primary fw-semibold shadow-sm d-flex align-items-center gap-1">
                                <i class="bi bi-file-earmark-zip-fill"></i> Download Semua (ZIP)
                            </a>
                        ` : ''}
                    </div>

                    ${dol}{files.length === 0 ? `
                        <div class="text-center py-5">
                            <i class="bi bi-inbox text-secondary" style="font-size: 3rem;"></i>
                            <h6 class="fw-bold text-main mt-3 mb-1">Belum Ada Berkas dari HP</h6>
                            <p class="text-muted small mb-3">Di aplikasi My Singghara (HP), buka menu <b>Transfer File</b> lalu pilih berkas yang ingin dikirim ke PC.</p>
                            <button class="btn btn-sm btn-outline-primary" onclick="fetchTransferData()">
                                <i class="bi bi-arrow-clockwise me-1"></i> Segarkan Sekarang
                            </button>
                        </div>
                    ` : `
                        <div class="table-responsive">
                            <table class="table table-hover align-middle mb-0 transfer-table">
                                <thead class="transfer-thead small">
                                    <tr>
                                        <th>Nama Berkas</th>
                                        <th>Ukuran</th>
                                        <th>Keterangan</th>
                                        <th class="text-end">Aksi</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    ${dol}{files.map(item => `
                                        <tr>
                                            <td>
                                                <div class="d-flex align-items-center gap-2">
                                                    <i class="bi ${dol}{getTransferFileIcon(item.extension, item.mimeType)} fs-4"></i>
                                                    <div>
                                                        <div class="fw-bold small text-main text-truncate" style="max-width: 380px;">${dol}{escapeHtml(item.name)}</div>
                                                        <span class="badge ${dol}{item.isSelfApk ? 'bg-success' : 'bg-secondary'} bg-opacity-25 ${dol}{item.isSelfApk ? 'text-success' : 'text-secondary'}" style="font-size: 10px;">
                                                            ${dol}{item.isSelfApk ? 'Aplikasi APK My Singghara' : item.extension || 'BERKAS'}
                                                        </span>
                                                    </div>
                                                </div>
                                            </td>
                                            <td class="small fw-semibold">${dol}{item.formattedSize || formatTransferFileSize(item.size)}</td>
                                            <td class="small text-muted">${dol}{item.isSelfApk ? 'Installer resmi aplikasi' : 'Dari HP'}</td>
                                            <td class="text-end">
                                                <a href="/api/transfer_file?action=download&id=${dol}{encodeURIComponent(item.id)}" 
                                                   class="btn btn-sm btn-outline-primary fw-semibold d-inline-flex align-items-center gap-1">
                                                    <i class="bi bi-download"></i> Unduh
                                                </a>
                                            </td>
                                        </tr>
                                    `).join('')}
                                </tbody>
                            </table>
                        </div>
                    `}
                </div>
            `;
        }

        // --- 3. TAB: BERKAS DITERIMA DI HP ---
        function renderReceivedInPhoneTab() {
            const files = window.transferReceivedFiles || [];

            return `
                <div class="card border-0 shadow-sm rounded-4 p-3 p-md-4 transfer-card">
                    <div class="d-flex justify-content-between align-items-center mb-3">
                        <div>
                            <h6 class="fw-bold mb-1"><i class="bi bi-folder-check text-primary me-2"></i>Berkas yang Berhasil Diterima di HP</h6>
                            <div class="small text-muted">Daftar berkas yang telah dikirimkan dari PC dan tersimpan di HP.</div>
                        </div>
                        <button class="btn btn-sm btn-outline-secondary" onclick="fetchTransferData()">
                            <i class="bi bi-arrow-clockwise"></i> Segarkan
                        </button>
                    </div>

                    ${dol}{files.length === 0 ? `
                        <div class="text-center py-5">
                            <i class="bi bi-cloud-arrow-up text-secondary" style="font-size: 3rem;"></i>
                            <h6 class="fw-bold text-main mt-3 mb-1">Belum Ada Berkas Diterima di HP</h6>
                            <p class="text-muted small mb-0">Kirim berkas dari tab <b>Kirim File ke HP</b> di atas.</p>
                        </div>
                    ` : `
                        <div class="table-responsive">
                            <table class="table table-hover align-middle mb-0 transfer-table">
                                <thead class="transfer-thead small">
                                    <tr>
                                        <th>Nama Berkas</th>
                                        <th>Ukuran</th>
                                        <th class="text-end">Aksi</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    ${dol}{files.map(item => `
                                        <tr>
                                            <td>
                                                <div class="d-flex align-items-center gap-2">
                                                    <i class="bi ${dol}{getTransferFileIcon(item.extension, item.mimeType)} fs-4"></i>
                                                    <div>
                                                        <div class="fw-bold small text-main text-truncate" style="max-width: 380px;">${dol}{escapeHtml(item.name)}</div>
                                                        <span class="badge bg-info bg-opacity-25 text-info" style="font-size: 10px;">
                                                            ${dol}{item.extension || 'BERKAS'}
                                                        </span>
                                                    </div>
                                                </div>
                                            </td>
                                            <td class="small fw-semibold">${dol}{item.formattedSize || formatTransferFileSize(item.size)}</td>
                                            <td class="text-end">
                                                <a href="/api/transfer_file?action=download&id=${dol}{encodeURIComponent(item.id)}" 
                                                   class="btn btn-sm btn-outline-secondary fw-semibold d-inline-flex align-items-center gap-1 me-1">
                                                    <i class="bi bi-download"></i> Unduh Ulang
                                                </a>
                                                <button class="btn btn-sm btn-outline-danger" onclick="deleteReceivedFile('${dol}{escapeJs(item.id)}')">
                                                    <i class="bi bi-trash"></i>
                                                </button>
                                            </td>
                                        </tr>
                                    `).join('')}
                                </tbody>
                            </table>
                        </div>
                    `}
                </div>
            `;
        }

        async function deleteReceivedFile(id) {
            if (!confirm('Hapus berkas ini dari HP?')) return;
            try {
                const res = await fetch('/api/transfer_file?action=delete_received', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ id: id })
                });
                if (res.ok) {
                    await fetchTransferData();
                    showTransferNotification('Berkas dihapus dari HP', 'info');
                }
            } catch (e) {
                console.error(e);
            }
        }

        // --- 4. TAB: RIWAYAT AKTIVITAS TRANSFER ---
        function renderTransferLogsTab() {
            const logs = window.transferLogs || [];

            return `
                <div class="card border-0 shadow-sm rounded-4 p-3 p-md-4 transfer-card">
                    <div class="d-flex justify-content-between align-items-center mb-3">
                        <div>
                            <h6 class="fw-bold mb-1"><i class="bi bi-clock-history text-warning me-2"></i>Riwayat Aktivitas Transfer</h6>
                            <div class="small text-muted">Log berkas yang dikirim dan diterima secara offline.</div>
                        </div>
                        ${dol}{logs.length > 0 ? `
                            <button class="btn btn-sm btn-outline-danger" onclick="clearTransferLogs()">
                                <i class="bi bi-trash me-1"></i> Hapus Riwayat
                            </button>
                        ` : ''}
                    </div>

                    ${dol}{logs.length === 0 ? `
                        <div class="text-center py-5">
                            <i class="bi bi-journal-text text-secondary" style="font-size: 3rem;"></i>
                            <h6 class="fw-bold text-main mt-3 mb-1">Belum Ada Aktivitas</h6>
                            <p class="text-muted small mb-0">Aktivitas kirim dan terima berkas akan tercatat otomatis di sini.</p>
                        </div>
                    ` : `
                        <div class="list-group list-group-flush border rounded-3 transfer-log-group">
                            ${dol}{logs.map(log => {
                                const isIncoming = log.isIncoming;
                                const dateStr = new Date(log.timestamp).toLocaleString('id-ID');
                                return `
                                    <div class="list-group-item d-flex justify-content-between align-items-center py-3 transfer-log-item">
                                        <div class="d-flex align-items-center gap-3">
                                            <div class="rounded-circle d-flex align-items-center justify-content-center ${dol}{isIncoming ? 'bg-success' : 'bg-primary'} bg-opacity-25" style="width: 40px; height: 40px;">
                                                <i class="bi ${dol}{isIncoming ? 'bi-arrow-down-left text-success' : 'bi-arrow-up-right text-primary'} fs-5"></i>
                                            </div>
                                            <div>
                                                <div class="fw-bold small text-main">
                                                    ${dol}{escapeHtml(log.title)}
                                                    ${dol}{log.channel === 'HP' ? '<span class="badge bg-primary bg-opacity-25 text-primary ms-1" style="font-size: 10px;">HP ke HP</span>' : ''}
                                                </div>
                                                <div class="text-muted small">${dol}{escapeHtml(log.description)}</div>
                                            </div>
                                        </div>
                                        <span class="small text-muted">${dol}{dateStr}</span>
                                    </div>
                                `;
                            }).join('')}
                        </div>
                    `}
                </div>
            `;
        }

        async function clearTransferLogs() {
            if (!confirm('Bersihkan semua riwayat transfer?')) return;
            try {
                await fetch('/api/transfer_file?action=clear_logs', { method: 'POST' });
                await fetchTransferData();
            } catch (e) {
                console.error(e);
            }
        }

        // --- 5. TAB: HP KE HP (dua perangkat Android bertukar berkas offline) ---
        // Browser PC tidak boleh memanggil HP kedua lintas-origin, jadi semua permintaan
        // HP↔HP dilewatkan melalui HP yang sedang dibuka halamannya ini (perantara).
        async function fetchP2pData(forceRender) {
            try {
                const res = await fetch('/api/transfer_file?action=p2p_peers', { cache: 'no-store' });
                if (!res.ok) return;
                const data = await res.json();
                const peers = data.peers || [];
                const jobs = data.jobs || [];
                const signature = peers.map(p => String(p.id) + ':' + String(p.reachable) + ':' + String(p.lastSeen)).join('|')
                    + '#' + jobs.map(j => String(j.id) + ':' + String(j.state) + ':' + String(j.doneBytes)).join('|');
                window.transferP2pInfo = data;
                window.transferPeers = peers;
                window.transferP2pJobs = jobs;
                const changed = signature !== window.transferP2pSignature;
                window.transferP2pSignature = signature;
                const countEl = document.getElementById('transferCountP2p');
                if (countEl) countEl.textContent = '(' + peers.length + ')';
                if (changed || forceRender) renderTransferFileUI();
            } catch (err) {
                console.warn('Gagal memuat data HP ke HP:', err);
            }
        }

        async function scanP2pPeers() {
            window.transferP2pBusy = true;
            window.transferP2pNotice = 'Mencari HP My Singghara lain di jaringan lokal...';
            renderTransferFileUI();
            try {
                const res = await fetch('/api/transfer_file?action=p2p_scan', { method: 'POST' });
                const data = await res.json();
                window.transferPeers = data.peers || [];
                window.transferP2pJobs = data.jobs || [];
                const online = window.transferPeers.filter(p => p.reachable);
                window.transferP2pNotice = online.length > 0
                    ? online.length + ' HP siap diajak transfer.'
                    : (window.transferPeers.length > 0
                        ? 'Ada ' + window.transferPeers.length + ' perangkat tercatat tetapi tidak menjawab. Nyalakan Web Connect di HP tujuan.'
                        : 'Belum ada HP lain. Kedua HP harus satu Wi-Fi/hotspot dan Web Connect HP tujuan harus menyala.');
            } catch (err) {
                window.transferP2pNotice = 'Pencarian gagal: ' + err;
            }
            window.transferP2pBusy = false;
            renderTransferFileUI();
        }

        function selectP2pPeer(id) {
            window.transferP2pSelected = id;
            window.transferP2pFiles = [];
            window.transferP2pRelayFile = null;
            loadP2pPeerFiles();
        }

        async function loadP2pPeerFiles() {
            if (!window.transferP2pSelected) return;
            window.transferP2pBusy = true;
            window.transferP2pNotice = 'Mengambil daftar berkas dari HP tujuan...';
            renderTransferFileUI();
            try {
                const res = await fetch('/api/transfer_file?action=p2p_state&peer=' + encodeURIComponent(window.transferP2pSelected), { cache: 'no-store' });
                const data = await res.json();
                if (data.status === 'ok') {
                    window.transferP2pFiles = data.shared || [];
                    window.transferP2pPeerName = data.peerName || '';
                    window.transferP2pNotice = window.transferP2pFiles.length === 0
                        ? 'HP tujuan belum membagikan berkas. Di HP itu buka Transfer File tab "Kirim ke PC" lalu pilih berkas.'
                        : '';
                } else {
                    window.transferP2pNotice = data.message || 'Gagal membaca HP tujuan.';
                }
            } catch (err) {
                window.transferP2pNotice = 'HP tujuan tidak menjawab.';
            }
            window.transferP2pBusy = false;
            renderTransferFileUI();
        }

        async function takeP2pFile(id, label) {
            window.transferP2pBusy = true;
            window.transferP2pNotice = 'Mengambil ' + label + ' ke HP ini...';
            renderTransferFileUI();
            try {
                const res = await fetch('/api/transfer_file?action=p2p_take', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ peer: window.transferP2pSelected, id: id })
                });
                const data = await res.json();
                window.transferP2pNotice = data.status === 'success'
                    ? 'Berkas ' + (data.fileName || label) + ' tersimpan di HP ini.'
                    : (data.message || 'Gagal mengambil berkas.');
            } catch (err) {
                window.transferP2pNotice = 'Gagal mengambil berkas.';
            }
            window.transferP2pBusy = false;
            await fetchTransferData();
            fetchP2pData(false);
        }

        function handleP2pRelayPick(event) {
            const files = event.target.files;
            window.transferP2pRelayFile = (files && files.length > 0) ? files[0] : null;
            renderTransferFileUI();
        }

        async function sendP2pRelay() {
            const file = window.transferP2pRelayFile;
            if (!file || !window.transferP2pSelected || window.transferP2pBusy) return;
            window.transferP2pBusy = true;
            window.transferP2pNotice = 'Meneruskan ' + file.name + ' ke HP tujuan...';
            renderTransferFileUI();
            const ok = await uploadRawFile(file, '/api/transfer_file?action=p2p_relay', window.transferP2pSelected, null);
            window.transferP2pNotice = ok
                ? file.name + ' terkirim ke HP tujuan.'
                : (window.transferP2pNotice || 'Gagal mengirim ke HP tujuan.');
            window.transferP2pRelayFile = null;
            window.transferP2pBusy = false;
            renderTransferFileUI();
            fetchP2pData(false);
        }

        async function forgetP2pPeer(id) {
            try {
                await fetch('/api/transfer_file?action=p2p_forget', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ peer: id })
                });
            } catch (err) {}
            if (window.transferP2pSelected === id) {
                window.transferP2pSelected = '';
                window.transferP2pFiles = [];
            }
            await fetchP2pData(true);
        }

        function copyP2pAddress() {
            const info = window.transferP2pInfo || window.transferInfo || {};
            const addr = 'http://' + (info.selfIp || info.ip || '') + ':' + (info.port || 8080);
            try { navigator.clipboard.writeText(addr); } catch (e) {}
            showTransferNotification('Alamat HP disalin: ' + addr, 'success');
        }

        function renderPhoneToPhoneTab() {
            const info = window.transferP2pInfo || window.transferInfo || {};
            const peers = window.transferPeers || [];
            const jobs = window.transferP2pJobs || [];
            const selected = peers.find(p => p.id === window.transferP2pSelected) || null;
            const files = window.transferP2pFiles || [];
            const busy = window.transferP2pBusy;
            const myAddress = 'http://' + (info.selfIp || info.ip || '?') + ':' + (info.port || 8080);

            return `
                <div class="card border-0 shadow-sm rounded-4 p-3 p-md-4 transfer-card">
                    <div class="d-flex flex-wrap justify-content-between align-items-center gap-2 mb-2">
                        <div>
                            <h6 class="fw-bold mb-1"><i class="bi bi-phones text-primary me-2"></i>Transfer Antar HP (100% Offline)</h6>
                            <div class="small text-muted">
                                HP ini: <b>${dol}{escapeHtml(info.selfName || info.deviceName || 'HP Android')}</b>
                                <span class="mx-1">•</span> ${dol}{myAddress}
                                <span class="badge ${dol}{info.listening ? 'bg-success' : 'bg-warning'} bg-opacity-25 ${dol}{info.listening ? 'text-success' : 'text-warning'} ms-1" style="font-size: 10px;">
                                    ${dol}{info.listening ? 'Menunggu HP lain' : 'Penemuan belum aktif'}
                                </span>
                            </div>
                        </div>
                        <div class="d-flex gap-2">
                            <button class="btn btn-sm btn-outline-secondary" onclick="copyP2pAddress()">
                                <i class="bi bi-clipboard"></i> Salin
                            </button>
                            <button class="btn btn-sm btn-primary fw-semibold" onclick="scanP2pPeers()" ${dol}{busy ? 'disabled' : ''}>
                                <i class="bi bi-broadcast me-1"></i> ${dol}{busy ? 'Bekerja...' : 'Cari HP'}
                            </button>
                        </div>
                    </div>

                    <div class="alert alert-info small py-2 px-3 mb-3 rounded-3">
                        <i class="bi bi-info-circle me-1"></i>
                        Buka menu <b>Transfer File &rarr; HP ke HP</b> di HP tujuan (Web Connect harus aktif), lalu pindai QR HP itu dari HP pengirim,
                        atau pilih HP-nya di daftar ini.
                    </div>

                    ${dol}{peers.length === 0 ? `
                        <div class="text-center py-4">
                            <i class="bi bi-phone text-secondary" style="font-size: 2.6rem;"></i>
                            <h6 class="fw-bold text-main mt-2 mb-1">Belum Ada HP Terdeteksi</h6>
                            <p class="text-muted small mb-0">Tekan <b>Cari HP</b>. Kedua HP wajib satu jaringan Wi-Fi atau hotspot.</p>
                        </div>
                    ` : `
                        <div class="table-responsive mb-3">
                            <table class="table table-hover align-middle mb-0 transfer-table">
                                <thead class="transfer-thead small">
                                    <tr><th>Perangkat</th><th>Alamat</th><th>Status</th><th class="text-end">Aksi</th></tr>
                                </thead>
                                <tbody>
                                    ${dol}{peers.map(p => `
                                        <tr>
                                            <td class="small fw-bold text-main"><i class="bi bi-phone me-1 text-primary"></i>${dol}{escapeHtml(p.label)}</td>
                                            <td class="small text-muted">${dol}{p.ip}:${dol}{p.port}</td>
                                            <td class="small">
                                                <span class="badge ${dol}{p.reachable ? 'bg-success' : 'bg-secondary'} bg-opacity-25 ${dol}{p.reachable ? 'text-success' : 'text-secondary'}" style="font-size: 10px;">
                                                    ${dol}{p.reachable ? 'Online' : 'Riwayat'}
                                                </span>
                                            </td>
                                            <td class="text-end">
                                                <button class="btn btn-sm ${dol}{p.id === window.transferP2pSelected ? 'btn-primary' : 'btn-outline-primary'} fw-semibold me-1"
                                                        onclick="selectP2pPeer('${dol}{escapeJs(p.id)}')" ${dol}{busy ? 'disabled' : ''}>
                                                    <i class="bi bi-link-45deg"></i> Hubungi
                                                </button>
                                                <button class="btn btn-sm btn-outline-danger border-0" onclick="forgetP2pPeer('${dol}{escapeJs(p.id)}')">
                                                    <i class="bi bi-x-lg"></i>
                                                </button>
                                            </td>
                                        </tr>
                                    `).join('')}
                                </tbody>
                            </table>
                        </div>
                    `}

                    ${dol}{window.transferP2pNotice ? `
                        <div class="small fw-semibold text-primary mb-3"><i class="bi bi-bell me-1"></i>${dol}{escapeHtml(window.transferP2pNotice)}</div>
                    ` : ''}

                    ${dol}{selected ? `
                        <div class="border-top pt-3 mt-1">
                            <div class="d-flex flex-wrap justify-content-between align-items-center gap-2 mb-2">
                                <h6 class="fw-bold mb-0 small"><i class="bi bi-folder2-open text-primary me-1"></i>Berkas di ${dol}{escapeHtml(selected.label)}</h6>
                                <button class="btn btn-sm btn-outline-secondary" onclick="loadP2pPeerFiles()" ${dol}{busy ? 'disabled' : ''}>
                                    <i class="bi bi-arrow-clockwise"></i> Segarkan
                                </button>
                            </div>
                            ${dol}{files.length === 0 ? `
                                <div class="text-muted small py-2">Daftar berkas kosong.</div>
                            ` : `
                                <div class="table-responsive">
                                    <table class="table table-hover align-middle mb-3 transfer-table">
                                        <thead class="transfer-thead small">
                                            <tr><th>Nama Berkas</th><th>Ukuran</th><th class="text-end">Aksi</th></tr>
                                        </thead>
                                        <tbody>
                                            ${dol}{files.map(f => `
                                                <tr>
                                                    <td>
                                                        <div class="d-flex align-items-center gap-2">
                                                            <i class="bi ${dol}{getTransferFileIcon(f.extension, f.mimeType)} fs-5"></i>
                                                            <span class="fw-bold small text-main text-truncate" style="max-width: 340px;">${dol}{escapeHtml(f.name)}</span>
                                                        </div>
                                                    </td>
                                                    <td class="small fw-semibold">${dol}{f.formattedSize || formatTransferFileSize(f.size)}</td>
                                                    <td class="text-end">
                                                        <button class="btn btn-sm btn-outline-primary fw-semibold me-1"
                                                                onclick="takeP2pFile('${dol}{escapeJs(f.id)}', '${dol}{escapeJs(f.name)}')" ${dol}{busy ? 'disabled' : ''}>
                                                            <i class="bi bi-phone-arrow-down"></i> Ke HP Ini
                                                        </button>
                                                        <a class="btn btn-sm btn-outline-secondary fw-semibold"
                                                           href="/api/transfer_file?action=p2p_download&peer=${dol}{encodeURIComponent(selected.id)}&id=${dol}{encodeURIComponent(f.id)}">
                                                            <i class="bi bi-download"></i> Ke PC
                                                        </a>
                                                    </td>
                                                </tr>
                                            `).join('')}
                                        </tbody>
                                    </table>
                                </div>
                            `}

                            <div class="border rounded-3 p-3 mt-2 transfer-list-group">
                                <div class="fw-bold small mb-2"><i class="bi bi-upload me-1 text-primary"></i>Kirim Berkas dari PC ke ${dol}{escapeHtml(selected.label)}</div>
                                <div class="d-flex flex-wrap align-items-center gap-2">
                                    <input type="file" id="p2pRelayInput" class="form-control form-control-sm" style="max-width: 340px;"
                                           onchange="handleP2pRelayPick(event)" />
                                    <button class="btn btn-sm btn-primary fw-semibold" onclick="sendP2pRelay()"
                                            ${dol}{busy || !window.transferP2pRelayFile ? 'disabled' : ''}>
                                        <i class="bi bi-send me-1"></i> Teruskan
                                    </button>
                                </div>
                                <div class="text-muted mt-2" style="font-size: 11px;">
                                    Berkas mengalir PC &rarr; HP ini &rarr; ${dol}{escapeHtml(selected.label)}, semuanya lewat kabel/Wi-Fi lokal.
                                </div>
                            </div>
                        </div>
                    ` : ''}

                    ${dol}{jobs.length > 0 ? `
                        <div class="mt-3">
                            <div class="fw-bold small mb-2">Proses Berjalan</div>
                            ${dol}{jobs.map(j => `
                                <div class="d-flex justify-content-between align-items-center small py-1">
                                    <span class="text-truncate me-2">
                                        <i class="bi ${dol}{j.direction === 'out' ? 'bi-arrow-up-right text-primary' : 'bi-arrow-down-left text-success'} me-1"></i>
                                        ${dol}{escapeHtml(j.fileName)} &rarr; ${dol}{escapeHtml(j.peerLabel)}
                                    </span>
                                    <span class="text-muted">${dol}{j.state === 'running' ? (j.totalBytes > 0 ? Math.round((j.doneBytes / j.totalBytes) * 100) + '%' : '...') : (j.state === 'done' ? 'Selesai' : 'Gagal')}</span>
                                </div>
                            `).join('')}
                        </div>
                    ` : ''}
                </div>
            `;
        }

        function showTransferNotification(msg, type = 'success') {
            const toastEl = document.createElement('div');
            toastEl.className = 'toast align-items-center text-white border-0 show shadow-lg position-fixed bottom-0 end-0 m-3';
            toastEl.style.backgroundColor = type === 'success' ? '#0F766E' : '#1E293B';
            toastEl.style.borderRadius = '12px';
            toastEl.style.zIndex = '99999';
            toastEl.innerHTML = `
                <div class="d-flex p-3 align-items-center">
                    <i class="bi bi-check-circle-fill fs-5 me-2"></i>
                    <div class="toast-body p-0 flex-grow-1 small fw-semibold">${dol}{escapeHtml(msg)}</div>
                    <button type="button" class="btn-close btn-close-white ms-2 m-auto" onclick="this.closest('.toast').remove()"></button>
                </div>
            `;
            document.body.appendChild(toastEl);
            setTimeout(() => { try { toastEl.remove(); } catch(e) {} }, 3000);
        }
        """
    }
}
