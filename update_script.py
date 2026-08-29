import sys

with open('app/src/main/java/com/example/web/KaryawanMuridWebScript.kt', 'r', encoding='utf-8') as f:
    content = f.read()

dol = "${'$'}"

new_render_presensi = """function renderPresensiSubTabHtml() {
            const config = getPresensiConfig();
            const todayStr = new Date().toDateString();
            const todayLogs = window.presensiList.filter(p => new Date(p.timestamp || 0).toDateString() === todayStr);

            const hadirCount = todayLogs.filter(p => (p.statusPresensi || '').toLowerCase() === 'hadir').length;
            const tepatWaktuCount = todayLogs.filter(p => (!p.statusPelanggaran || p.statusPelanggaran === 'Tepat Waktu') && (p.statusPresensi || '').toLowerCase() === 'hadir').length;
            const terlambatCount = todayLogs.filter(p => (p.statusPresensi || '').toLowerCase() === 'terlambat' || (p.statusPelanggaran || '').includes('Terlambat')).length;
            const pulangAwalCount = todayLogs.filter(p => (p.statusPelanggaran || '').includes('Awal') || (p.statusPelanggaran || '').includes('Cepat') || (p.catatanPelanggaran || '').toLowerCase().includes('awal')).length;
            const izinCount = todayLogs.filter(p => (p.statusPresensi || '').toLowerCase() === 'izin').length;
            const sakitCount = todayLogs.filter(p => (p.statusPresensi || '').toLowerCase() === 'sakit').length;

            const searchQuery = (window.presensiSearchQuery || '').toLowerCase();
            const statusFilter = window.presensiStatusFilter || 'Semua';

            let filteredLogs = window.presensiList.slice().reverse();
            if (searchQuery) {
                filteredLogs = filteredLogs.filter(p =>
                    (p.nama || '').toLowerCase().includes(searchQuery) ||
                    (p.nomorInduk || '').toLowerCase().includes(searchQuery) ||
                    (p.divisi || '').toLowerCase().includes(searchQuery) ||
                    (p.status || '').toLowerCase().includes(searchQuery) ||
                    (p.statusPelanggaran || '').toLowerCase().includes(searchQuery) ||
                    (p.catatanPelanggaran || '').toLowerCase().includes(searchQuery) ||
                    (p.metode || '').toLowerCase().includes(searchQuery)
                );
            }

            if (statusFilter !== 'Semua') {
                if (statusFilter === 'Pelanggaran') {
                    filteredLogs = filteredLogs.filter(p => p.statusPelanggaran && p.statusPelanggaran !== 'Tepat Waktu');
                } else if (statusFilter === 'Tepat Waktu') {
                    filteredLogs = filteredLogs.filter(p => !p.statusPelanggaran || p.statusPelanggaran === 'Tepat Waktu');
                } else {
                    filteredLogs = filteredLogs.filter(p => (p.statusPresensi || '').toLowerCase() === statusFilter.toLowerCase());
                }
            }

            return `
                <div class="container-fluid px-0">
                    <!-- 1. KARTU WAKTU ABSENSI & PULANG -->
                    <div class="card card-custom border-0 shadow-sm p-3 mb-3" style="border-radius: 16px;">
                        <div class="d-flex flex-wrap align-items-center justify-content-between gap-3">
                            <div class="d-flex align-items-center gap-3">
                                <button type="button" class="btn btn-link p-0 text-decoration-none d-flex align-items-center justify-content-center rounded-3 shadow-xs" 
                                        style="width: 44px; height: 44px; background: rgba(15, 118, 110, 0.12); color: #0F766E; border: 1px solid rgba(15, 118, 110, 0.25); cursor: pointer;"
                                        onclick="openBatasWaktuModal()" 
                                        title="Klik ikon jam untuk mengatur Batas Jam Masuk & Batas Jam Pulang">
                                    <i class="bi bi-clock-history fs-4"></i>
                                </button>
                                <div>
                                    <div class="d-flex align-items-center gap-2">
                                        <h5 class="fw-bold text-main mb-0" style="cursor: pointer;" onclick="openBatasWaktuModal()" title="Klik ikon jam untuk mengatur jam">
                                            Waktu Absensi &amp; Pulang
                                        </h5>
                                        <span class="badge rounded-pill px-2 py-1 small" style="background: rgba(15, 118, 110, 0.15); color: #0F766E; font-size: 11px; cursor: pointer;" onclick="openBatasWaktuModal()">
                                            <i class="bi bi-sliders me-1"></i>Klik Ikon Jam untuk Mengatur
                                        </span>
                                    </div>
                                    <p class="text-sub small mb-0 mt-1">Konfigurasi batas waktu operasional kehadiran masuk dan kepulangan harian</p>
                                </div>
                            </div>
                            <div class="d-flex align-items-center gap-3 flex-wrap">
                                <div class="px-3 py-2 rounded-3 d-flex align-items-center gap-2" style="background: var(--sidebar-bg); border: 1px solid var(--border-color);">
                                    <i class="bi bi-box-arrow-in-right text-success fs-5"></i>
                                    <div>
                                        <div class="text-sub" style="font-size: 10px; text-transform: uppercase; font-weight: 600;">Batas Jam Masuk</div>
                                        <div class="fw-bold text-main" style="font-size: 14px;">""" + dol + """{config.batasMasuk} WIB</div>
                                    </div>
                                </div>
                                <div class="px-3 py-2 rounded-3 d-flex align-items-center gap-2" style="background: var(--sidebar-bg); border: 1px solid var(--border-color);">
                                    <i class="bi bi-box-arrow-left text-warning fs-5"></i>
                                    <div>
                                        <div class="text-sub" style="font-size: 10px; text-transform: uppercase; font-weight: 600;">Batas Jam Pulang</div>
                                        <div class="fw-bold text-main" style="font-size: 14px;">""" + dol + """{config.batasPulang} WIB</div>
                                    </div>
                                </div>
                                <button class="btn btn-outline-secondary btn-sm rounded-pill px-3 py-2" onclick="openBatasWaktuModal()" title="Ubah Batas Waktu">
                                    <i class="bi bi-pencil-square me-1"></i> Ubah Jam
                                </button>
                            </div>
                        </div>
                    </div>

                    <!-- 2. KARTU STATISTIK (HADIR, TEPAT WAKTU, TERLAMBAT, PULANG AWAL, IZIN, SAKIT) -->
                    <div class="row g-3 mb-4">
                        <!-- Hadir -->
                        <div class="col-6 col-sm-4 col-md-2">
                            <div class="card card-custom p-3 border-0 h-100 shadow-sm text-center" style="border-top: 3px solid #10B981 !important; border-radius: 14px;">
                                <div class="p-2 rounded-circle mx-auto mb-2 d-flex align-items-center justify-content-center" style="width: 38px; height: 38px; background: rgba(16, 185, 129, 0.15); color: #10B981;">
                                    <i class="bi bi-person-check-fill fs-5"></i>
                                </div>
                                <span class="text-sub small text-uppercase fw-semibold" style="font-size: 11px;">Hadir</span>
                                <h4 class="fw-bold text-main mb-0 mt-1">""" + dol + """{hadirCount}</h4>
                            </div>
                        </div>
                        <!-- Tepat Waktu -->
                        <div class="col-6 col-sm-4 col-md-2">
                            <div class="card card-custom p-3 border-0 h-100 shadow-sm text-center" style="border-top: 3px solid #0F766E !important; border-radius: 14px;">
                                <div class="p-2 rounded-circle mx-auto mb-2 d-flex align-items-center justify-content-center" style="width: 38px; height: 38px; background: rgba(15, 118, 110, 0.15); color: #0F766E;">
                                    <i class="bi bi-check-circle-fill fs-5"></i>
                                </div>
                                <span class="text-sub small text-uppercase fw-semibold" style="font-size: 11px;">Tepat Waktu</span>
                                <h4 class="fw-bold mb-0 mt-1" style="color: #0F766E;">""" + dol + """{tepatWaktuCount}</h4>
                            </div>
                        </div>
                        <!-- Terlambat -->
                        <div class="col-6 col-sm-4 col-md-2">
                            <div class="card card-custom p-3 border-0 h-100 shadow-sm text-center" style="border-top: 3px solid #EF4444 !important; border-radius: 14px;">
                                <div class="p-2 rounded-circle mx-auto mb-2 d-flex align-items-center justify-content-center" style="width: 38px; height: 38px; background: rgba(239, 68, 68, 0.15); color: #EF4444;">
                                    <i class="bi bi-clock-history fs-5"></i>
                                </div>
                                <span class="text-sub small text-uppercase fw-semibold" style="font-size: 11px;">Terlambat</span>
                                <h4 class="fw-bold text-danger mb-0 mt-1">""" + dol + """{terlambatCount}</h4>
                            </div>
                        </div>
                        <!-- Pulang Awal -->
                        <div class="col-6 col-sm-4 col-md-2">
                            <div class="card card-custom p-3 border-0 h-100 shadow-sm text-center" style="border-top: 3px solid #F59E0B !important; border-radius: 14px;">
                                <div class="p-2 rounded-circle mx-auto mb-2 d-flex align-items-center justify-content-center" style="width: 38px; height: 38px; background: rgba(245, 158, 11, 0.15); color: #F59E0B;">
                                    <i class="bi bi-box-arrow-left fs-5"></i>
                                </div>
                                <span class="text-sub small text-uppercase fw-semibold" style="font-size: 11px;">Pulang Awal</span>
                                <h4 class="fw-bold text-warning mb-0 mt-1">""" + dol + """{pulangAwalCount}</h4>
                            </div>
                        </div>
                        <!-- Izin -->
                        <div class="col-6 col-sm-4 col-md-2">
                            <div class="card card-custom p-3 border-0 h-100 shadow-sm text-center" style="border-top: 3px solid #3B82F6 !important; border-radius: 14px;">
                                <div class="p-2 rounded-circle mx-auto mb-2 d-flex align-items-center justify-content-center" style="width: 38px; height: 38px; background: rgba(59, 130, 246, 0.15); color: #3B82F6;">
                                    <i class="bi bi-file-earmark-text-fill fs-5"></i>
                                </div>
                                <span class="text-sub small text-uppercase fw-semibold" style="font-size: 11px;">Izin</span>
                                <h4 class="fw-bold text-primary mb-0 mt-1">""" + dol + """{izinCount}</h4>
                            </div>
                        </div>
                        <!-- Sakit -->
                        <div class="col-6 col-sm-4 col-md-2">
                            <div class="card card-custom p-3 border-0 h-100 shadow-sm text-center" style="border-top: 3px solid #8B5CF6 !important; border-radius: 14px;">
                                <div class="p-2 rounded-circle mx-auto mb-2 d-flex align-items-center justify-content-center" style="width: 38px; height: 38px; background: rgba(139, 92, 246, 0.15); color: #8B5CF6;">
                                    <i class="bi bi-heart-pulse-fill fs-5"></i>
                                </div>
                                <span class="text-sub small text-uppercase fw-semibold" style="font-size: 11px;">Sakit</span>
                                <h4 class="fw-bold mb-0 mt-1" style="color: #8B5CF6;">""" + dol + """{sakitCount}</h4>
                            </div>
                        </div>
                    </div>

                    <!-- 3. SCANNER & LOGS SECTION -->
                    <div class="row g-4">
                        <!-- LEFT COLUMN: SCANNER / INPUT PANEL -->
                        <div class="col-12 col-lg-5">
                            <div class="card card-custom border-0 shadow-sm p-4 h-100" style="border-radius: 18px;">
                                <div class="d-flex align-items-center justify-content-between mb-3">
                                    <h5 class="fw-bold text-main mb-0 d-flex align-items-center gap-2">
                                        <i class="bi bi-qr-code-scan text-teal" style="color: #0F766E;"></i> Pemindai &amp; Presensi
                                    </h5>
                                    <span class="badge bg-success bg-opacity-25 text-success rounded-pill px-2 py-1 small">
                                        <i class="bi bi-broadcast me-1"></i> Live Ready
                                    </span>
                                </div>

                                <!-- SCANNER OPTION 1: WEBCAM -->
                                <div class="mb-3">
                                    <div id="webcamScannerContainer" class="rounded-3 overflow-hidden border bg-dark text-white d-flex align-items-center justify-content-center mb-2" style="min-height: 200px; position: relative;">
                                        <div id="webcamQrReader" class="w-100"></div>
                                        <div id="webcamPlaceholder" class="text-center p-3">
                                            <i class="bi bi-camera-video fs-1 text-secondary"></i>
                                            <p class="small text-muted mb-0 mt-2">Kamera scanner siap diaktifkan</p>
                                        </div>
                                    </div>
                                    <div class="d-flex gap-2">
                                        <button id="btnToggleWebcam" class="btn btn-sm text-white rounded-pill flex-grow-1" style="background: #0F766E;" onclick="toggleWebcamScanner()">
                                            <i class="bi bi-camera-video-fill me-1"></i> Buka Kamera Scanner
                                        </button>
                                    </div>
                                </div>

                                <!-- MODE SELEKTOR: ABSEN MASUK VS ABSEN PULANG -->
                                <div class="mb-3">
                                    <label class="form-label small fw-bold text-main d-block mb-1">
                                        <i class="bi bi-sliders me-1"></i> Mode Pemindaian / Absensi:
                                    </label>
                                    <div class="btn-group w-100" role="group">
                                        <input type="radio" class="btn-check" name="scanModeType" id="modeScanMasuk" value="masuk" checked onchange="updateScanModeNotice()">
                                        <label class="btn btn-outline-success btn-sm py-2 fw-bold" for="modeScanMasuk">
                                            <i class="bi bi-box-arrow-in-right me-1"></i> Absen Masuk
                                        </label>

                                        <input type="radio" class="btn-check" name="scanModeType" id="modeScanPulang" value="pulang" onchange="updateScanModeNotice()">
                                        <label class="btn btn-outline-warning btn-sm py-2 fw-bold" for="modeScanPulang">
                                            <i class="bi bi-box-arrow-left me-1"></i> Absen Pulang
                                        </label>
                                    </div>
                                    <div id="scanModeNoticeText" class="form-text small mt-1 text-sub" style="font-size: 11px;">
                                        Batas waktu masuk: <strong>""" + dol + """{config.batasMasuk} WIB</strong>. Jika lewat, akan muncul peringatan pelanggaran.
                                    </div>
                                </div>

                                <!-- SCANNER OPTION 2: INPUT CEPAT / BARCODE SCANNER -->
                                <div class="border-top border-secondary border-opacity-25 pt-3">
                                    <label class="form-label small fw-bold text-main">
                                        <i class="bi bi-upc-scan me-1"></i> Input Kode QR / NIP / NIM (USB Scanner / Manual)
                                    </label>
                                    <form onsubmit="handleManualPresensiScan(event)">
                                        <div class="input-group mb-2">
                                            <input type="text" id="presensiQuickInput" class="form-control rounded-start-3" placeholder="Scan Barcode / ketik NIP lalu Enter..." autofocus autocomplete="off">
                                            <button type="submit" class="btn text-white" style="background: #0F766E;">
                                                <i class="bi bi-check-lg"></i> Proses
                                            </button>
                                        </div>
                                    </form>
                                    <div class="d-flex flex-wrap gap-2 align-items-center mt-2">
                                        <span class="small text-sub fw-semibold" style="font-size: 11px;">Status Masuk:</span>
                                        <div class="btn-group btn-group-sm" role="group">
                                            <input type="radio" class="btn-check" name="statusPresensiChoice" id="optHadir" value="Hadir" checked>
                                            <label class="btn btn-outline-success btn-sm" for="optHadir">Hadir</label>

                                            <input type="radio" class="btn-check" name="statusPresensiChoice" id="optIzin" value="Izin">
                                            <label class="btn btn-outline-primary btn-sm" for="optIzin">Izin</label>

                                            <input type="radio" class="btn-check" name="statusPresensiChoice" id="optSakit" value="Sakit">
                                            <label class="btn btn-outline-warning btn-sm" for="optSakit">Sakit</label>

                                            <input type="radio" class="btn-check" name="statusPresensiChoice" id="optTerlambat" value="Terlambat">
                                            <label class="btn btn-outline-danger btn-sm" for="optTerlambat">Terlambat</label>
                                        </div>
                                    </div>

                                    <div class="mt-3 pt-2 border-top border-secondary border-opacity-25">
                                        <button class="btn btn-outline-primary btn-sm w-100 rounded-pill fw-semibold py-2" onclick="openManualAbsensiModal()">
                                            <i class="bi bi-pencil-square me-1"></i> Form Input Presensi Lengkap (Waktu Masuk &amp; Pulang)
                                        </button>
                                    </div>
                                </div>

                                <!-- HASIL SCAN TERAKHIR -->
                                <div id="scanResultBox" class="mt-3 p-3 rounded-3 border d-none" style="background: var(--input-bg); border-color: var(--border-color) !important;">
                                    <div class="d-flex align-items-center gap-3">
                                        <div class="p-2 rounded-circle bg-success text-white">
                                            <i class="bi bi-check-circle-fill fs-3"></i>
                                        </div>
                                        <div class="flex-grow-1 min-w-0">
                                            <h6 class="fw-bold text-success mb-0" id="scanResultName">Nama Anggota</h6>
                                            <div class="small text-main" id="scanResultNip">NIP: 123456</div>
                                            <span class="badge bg-success rounded-pill mt-1" id="scanResultBadge">Hadir Tercatat</span>
                                        </div>
                                    </div>
                                </div>
                            </div>
                        </div>

                        <!-- RIGHT COLUMN: RIWAYAT PRESENSI TABLE -->
                        <div class="col-12 col-lg-7">
                            <div class="card card-custom border-0 shadow-sm p-4 h-100" style="border-radius: 18px;">
                                <div class="d-flex flex-wrap justify-content-between align-items-center gap-2 mb-3">
                                    <div>
                                        <h5 class="fw-bold text-main mb-0">Tabel Riwayat &amp; Pelanggaran Absensi</h5>
                                        <p class="text-sub small mb-0">""" + dol + """{new Date().toLocaleDateString('id-ID', { weekday: 'long', year: 'numeric', month: 'long', day: 'numeric' })} • Total Log: <strong>""" + dol + """{window.presensiList.length}</strong></p>
                                    </div>
                                    <div class="d-flex align-items-center gap-2">
                                        <button class="btn btn-outline-danger btn-sm rounded-pill px-3" onclick="clearAllPresensiLog()" title="Bersihkan Semua Log">
                                            <i class="bi bi-trash me-1"></i> Bersihkan Log
                                        </button>
                                        <button class="btn btn-outline-success btn-sm rounded-pill px-3" onclick="exportPresensiCsv()">
                                            <i class="bi bi-file-earmark-spreadsheet me-1"></i> Ekspor CSV
                                        </button>
                                    </div>
                                </div>

                                <!-- SEARCH & FILTER BAR -->
                                <div class="row g-2 mb-3">
                                    <div class="col-12 col-md-7">
                                        <div class="input-group input-group-sm">
                                            <span class="input-group-text"><i class="bi bi-search"></i></span>
                                            <input type="text" class="form-control" placeholder="Cari nama, NIP, status, pelanggaran..." value="""" + dol + """{escapeHtml(window.presensiSearchQuery || '')}" oninput="handlePresensiSearch(this.value)">
                                        </div>
                                    </div>
                                    <div class="col-12 col-md-5">
                                        <select class="form-select form-select-sm" onchange="handlePresensiStatusFilter(this.value)">
                                            <option value="Semua" """ + dol + """{statusFilter === 'Semua' ? 'selected' : ''}>Semua Status</option>
                                            <option value="Pelanggaran" """ + dol + """{statusFilter === 'Pelanggaran' ? 'selected' : ''}>⚠️ Ada Pelanggaran</option>
                                            <option value="Tepat Waktu" """ + dol + """{statusFilter === 'Tepat Waktu' ? 'selected' : ''}>✅ Tepat Waktu</option>
                                            <option value="Hadir" """ + dol + """{statusFilter === 'Hadir' ? 'selected' : ''}>Hadir</option>
                                            <option value="Izin" """ + dol + """{statusFilter === 'Izin' ? 'selected' : ''}>Izin</option>
                                            <option value="Sakit" """ + dol + """{statusFilter === 'Sakit' ? 'selected' : ''}>Sakit</option>
                                            <option value="Terlambat" """ + dol + """{statusFilter === 'Terlambat' ? 'selected' : ''}>Terlambat</option>
                                        </select>
                                    </div>
                                </div>

                                <!-- LOG TABLE WITH WAKTU MASUK & WAKTU PULANG & PELANGGARAN -->
                                <div class="table-responsive" style="max-height: 480px; overflow-y: auto;">
                                    <table class="table table-hover align-middle mb-0" style="font-size: 12px;">
                                        <thead style="background: var(--table-th-bg); color: var(--text-main);" class="sticky-top">
                                            <tr>
                                                <th>Waktu Masuk</th>
                                                <th>Waktu Pulang</th>
                                                <th>Nama Anggota</th>
                                                <th>NIP/NIM</th>
                                                <th>Status</th>
                                                <th>Pelanggaran</th>
                                                <th>Metode</th>
                                                <th class="text-end">Aksi</th>
                                            </tr>
                                        </thead>
                                        <tbody>
                                            """ + dol + """{filteredLogs.length === 0 ? `
                                                <tr>
                                                    <td colspan="8" class="text-center py-4 text-muted">
                                                        <i class="bi bi-inbox fs-3 d-block mb-1"></i>
                                                        Belum ada data presensi yang sesuai.
                                                    </td>
                                                </tr>
                                            ` : filteredLogs.map(p => renderPresensiLogRow(p)).join('')}
                                        </tbody>
                                    </table>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
                <!-- MODAL CONTAINER PLACEHOLDER -->
                <div id="presensiModalsContainer"></div>
            `;
        }

        """

# Replace renderPresensiSubTabHtml
idx1 = content.find('function renderPresensiSubTabHtml() {')
idx2 = content.find('function handlePresensiSearch(query) {')
if idx1 != -1 and idx2 != -1:
    content = content[:idx1] + new_render_presensi + content[idx2:]
    print('Updated renderPresensiSubTabHtml successfully')
else:
    print('Failed to find renderPresensiSubTabHtml slice')

# Update openBatasWaktuModal
old_modal_start = 'function openBatasWaktuModal() {'
old_modal_end = 'function saveBatasWaktuSubmit() {'

m_idx1 = content.find(old_modal_start)
m_idx2 = content.find(old_modal_end)

new_modal_code = """function openBatasWaktuModal() {
            const config = getPresensiConfig();
            let container = document.getElementById('presensiModalsContainer');
            if (!container) {
                container = document.createElement('div');
                container.id = 'presensiModalsContainer';
                document.body.appendChild(container);
            }

            container.innerHTML = `
                <div class="modal fade show d-block" id="batasWaktuModal" tabindex="-1" style="background: rgba(0,0,0,0.6); z-index: 1055;">
                    <div class="modal-dialog modal-dialog-centered">
                        <div class="modal-content card-custom border-0 shadow-lg" style="border-radius: 20px; background: var(--modal-bg); color: var(--text-main); border: 1px solid var(--border-color);">
                            <div class="modal-header border-0 pb-0">
                                <h5 class="fw-bold text-main mb-0 d-flex align-items-center gap-2">
                                    <i class="bi bi-clock-history text-teal" style="color: #0F766E;"></i> Waktu Absensi &amp; Pulang
                                </h5>
                                <button type="button" class="btn-close btn-close-custom" onclick="closeBatasWaktuModal()"></button>
                            </div>
                            <div class="modal-body p-4">
                                <p class="small text-sub mb-3">Tentukan batas jam masuk dan batas jam pulang operasional. Setiap kehadiran atau kepulangan yang melewati batas waktu ini akan memicu pencatatan pelanggaran otomatis.</p>
                                <div class="mb-3">
                                    <label class="form-label small fw-bold text-main">Batas Jam Masuk (HH:mm)</label>
                                    <input type="time" id="inputBatasMasuk" class="form-control" value=\"""" + dol + """{config.batasMasuk}" style="background: var(--input-bg); color: var(--text-main); border-color: var(--border-color);">
                                    <div class="form-text small text-sub">Contoh: 08:00 (Lewat dari jam ini dianggap terlambat).</div>
                                </div>
                                <div class="mb-3">
                                    <label class="form-label small fw-bold text-main">Batas Jam Pulang Minimal (HH:mm)</label>
                                    <input type="time" id="inputBatasPulang" class="form-control" value=\"""" + dol + """{config.batasPulang}" style="background: var(--input-bg); color: var(--text-main); border-color: var(--border-color);">
                                    <div class="form-text small text-sub">Contoh: 17:00 (Pulang sebelum jam ini dianggap pulang awal).</div>
                                </div>
                                <div class="d-flex gap-2 justify-content-end mt-4">
                                    <button type="button" class="btn btn-outline-secondary rounded-pill px-3" onclick="closeBatasWaktuModal()">Batal</button>
                                    <button type="button" class="btn text-white rounded-pill px-4 fw-bold" style="background: #0F766E;" onclick="saveBatasWaktuSubmit()">Simpan Waktu</button>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            `;
        }

        """

if m_idx1 != -1 and m_idx2 != -1:
    content = content[:m_idx1] + new_modal_code + content[m_idx2:]
    print('Updated openBatasWaktuModal successfully')
else:
    print('Failed to find openBatasWaktuModal slice')

# Also replace any remaining card/modal classes that had hardcoded bg-light/bg-white
content = content.replace('card card-custom p-3 border-0 bg-light rounded-4 shadow-sm', 'card card-custom p-3 border-0 rounded-4 shadow-sm')
content = content.replace('<div class="p-3 rounded-3 mb-3 bg-light border">', '<div class="p-3 rounded-3 mb-3 border" style="background: var(--input-bg); border-color: var(--border-color) !important;">')

with open('app/src/main/java/com/example/web/KaryawanMuridWebScript.kt', 'w', encoding='utf-8') as f:
    f.write(content)
print('Done!')
