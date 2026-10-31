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
        window.transferActiveTab = 'send_to_phone'; // 'send_to_phone', 'files_from_phone', 'received_in_phone', 'logs'
        window.transferUploading = false;
        window.transferUploadProgress = 0;
        window.transferUploadStatus = '';
        window.transferPendingFiles = [];

        async function loadTransferFile() {
            renderTransferFileUI();
            await fetchTransferData();
        }

        async function fetchTransferData() {
            try {
                const infoRes = await fetch('/api/transfer_file?action=info');
                if (infoRes.ok) {
                    window.transferInfo = await infoRes.json();
                }

                const sharedRes = await fetch('/api/transfer_file?action=files');
                if (sharedRes.ok) {
                    window.transferSharedFiles = await sharedRes.json();
                }

                const recRes = await fetch('/api/transfer_file?action=received_files');
                if (recRes.ok) {
                    window.transferReceivedFiles = await recRes.json();
                }

                const logsRes = await fetch('/api/transfer_file?action=logs');
                if (logsRes.ok) {
                    window.transferLogs = await logsRes.json();
                }
            } catch (err) {
                console.warn('Gagal memuat data transfer:', err);
            }
            renderTransferFileUI();
        }

        function setTransferTab(tab) {
            window.transferActiveTab = tab;
            renderTransferFileUI();
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
                                <div class="d-flex gap-2">
                                    <button class="btn btn-sm fw-semibold shadow-sm d-flex align-items-center gap-1 transfer-btn-refresh" onclick="fetchTransferData()">
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
                            <i class="bi bi-download me-1"></i> Berkas dari HP (${dol}{sharedCount})
                        </button>
                        <button class="btn btn-sm ${dol}{window.transferActiveTab === 'received_in_phone' ? 'btn-primary shadow-sm' : 'btn-outline-secondary'}" 
                                onclick="setTransferTab('received_in_phone')">
                            <i class="bi bi-folder-check me-1"></i> Berkas Diterima di HP (${dol}{receivedCount})
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
            window.transferUploading = true;
            window.transferUploadProgress = 0;
            renderTransferFileUI();

            const total = window.transferPendingFiles.length;
            let successCount = 0;

            for (let i = 0; i < total; i++) {
                const file = window.transferPendingFiles[i];
                window.transferUploadStatus = 'Mengirim berkas (' + (i + 1) + '/' + total + '): ' + file.name;
                window.transferUploadProgress = Math.round((i / total) * 100);
                renderTransferFileUI();

                try {
                    const base64Data = await readFileAsBase64(file);
                    const res = await fetch('/api/transfer_file?action=upload', {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({
                            filename: file.name,
                            mimeType: file.type || 'application/octet-stream',
                            base64Data: base64Data
                        })
                    });
                    if (res.ok) {
                        const json = await res.json();
                        if (json.status === 'success') {
                            successCount++;
                        }
                    }
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
                showTransferNotification('Sukses mengirim ' + successCount + ' berkas ke HP!', 'success');
            }, 1200);
        }

        function readFileAsBase64(file) {
            return new Promise((resolve, reject) => {
                const reader = new FileReader();
                reader.onload = () => {
                    const res = reader.result;
                    const base64 = res.split(',')[1] || '';
                    resolve(base64);
                };
                reader.onerror = reject;
                reader.readAsDataURL(file);
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
                                                <div class="fw-bold small text-main">${dol}{escapeHtml(log.title)}</div>
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
