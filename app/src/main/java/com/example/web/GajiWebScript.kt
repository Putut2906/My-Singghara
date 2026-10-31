package com.example.web

object GajiWebScript {
    fun getScript(dol: String): String {
        return """
        // ==========================================
        // --- MODUL GAJI (SALARY MANAGEMENT) ---
        // ==========================================
        window.gajiList = [];
        window.gajiSearchQuery = '';
        window.gajiFilterStatus = 'Semua';
        window.gajiFilterPeriode = 'Semua';
        window.gajiFilterStatusBayar = 'Semua';

        const GAJI_STATUS_OPTIONS = [
            "Karyawan Tetap",
            "Karyawan Kontrak",
            "Staf",
            "Guru / Dosen",
            "Magang / Trainee",
            "Freelance / Part-time",
            "Outsourcing"
        ];

        const GAJI_PERAN_OPTIONS = [
            "Manager / Pimpinan",
            "Supervisor / Koordinator",
            "Staff IT / Developer",
            "Staff Keuangan / Akuntan",
            "Staff Operasional",
            "Guru / Pengajar",
            "Admin & Personalia",
            "Marketing & Sales",
            "Desainer / Kreatif",
            "Security & Umum"
        ];

        function formatRupiahWeb(amount) {
            const num = Number(amount) || 0;
            return 'Rp ' + num.toLocaleString('id-ID');
        }

        function getPresetStandarGaji(status, peran) {
            let multiplier = 0.8;
            if (status === "Karyawan Tetap") multiplier = 1.0;
            else if (status === "Karyawan Kontrak") multiplier = 0.85;
            else if (status === "Staf") multiplier = 0.8;
            else if (status === "Guru / Dosen") multiplier = 0.9;
            else if (status === "Magang / Trainee") multiplier = 0.35;
            else if (status === "Freelance / Part-time") multiplier = 0.6;
            else if (status === "Outsourcing") multiplier = 0.7;

            let basePokok = 4000000;
            if (peran.includes("Manager")) basePokok = 9000000;
            else if (peran.includes("Supervisor")) basePokok = 6500000;
            else if (peran.includes("IT")) basePokok = 6000000;
            else if (peran.includes("Keuangan")) basePokok = 5200000;
            else if (peran.includes("Guru")) basePokok = 4800000;
            else if (peran.includes("Marketing")) basePokok = 4500000;
            else if (peran.includes("Desainer")) basePokok = 4800000;
            else if (peran.includes("Admin")) basePokok = 4200000;
            else if (peran.includes("Security")) basePokok = 3500000;

            let baseJabatan = 300000;
            if (peran.includes("Manager")) baseJabatan = 2500000;
            else if (peran.includes("Supervisor")) baseJabatan = 1200000;
            else if (peran.includes("IT")) baseJabatan = 800000;
            else if (peran.includes("Keuangan")) baseJabatan = 600000;

            const calcPokok = Math.max(1500000, Math.round(basePokok * multiplier));
            const calcJabatan = Math.max(0, Math.round(baseJabatan * multiplier));
            const calcTransport = (status === "Magang / Trainee") ? 500000 : 800000;
            const calcKehadiran = (status === "Magang / Trainee") ? 300000 : 500000;
            const calcBpjs = (status === "Karyawan Tetap") ? Math.round(calcPokok * 0.03) : 0;
            const calcPph = (calcPokok > 5000000) ? Math.round((calcPokok - 4500000) * 0.05) : 0;

            return {
                gajiPokok: calcPokok,
                tunjanganJabatan: calcJabatan,
                tunjanganMakanTransport: calcTransport,
                tunjanganKehadiran: calcKehadiran,
                potonganBpjs: calcBpjs,
                potonganPph: calcPph
            };
        }

        async function fetchGajiList() {
            try {
                const res = await fetch('/api/gaji');
                if (res.ok) {
                    window.gajiList = await res.json();
                }
            } catch(e) {
                console.error("Gagal memuat data gaji:", e);
            }
        }

        async function loadGajiPage() {
            const container = document.getElementById('tab-content') || document.getElementById('main-content');
            if (!container) return;

            container.innerHTML = `
                <div class="d-flex justify-content-center align-items-center py-5">
                    <div class="spinner-border text-teal" style="color: #0F766E;" role="status"></div>
                    <span class="ms-3 fw-semibold">Memuat Data Penggajian...</span>
                </div>
            `;

            await fetchGajiList();
            if (!window.anggotaList || window.anggotaList.length === 0) {
                try {
                    const resAnggota = await fetch('/api/anggota');
                    if (resAnggota.ok) window.anggotaList = await resAnggota.json();
                } catch(e){}
            }

            renderGajiDashboard();
        }

        function renderGajiDashboard() {
            const container = document.getElementById('tab-content') || document.getElementById('main-content');
            if (!container) return;

            const totalPengeluaran = window.gajiList.reduce((acc, curr) => acc + (Number(curr.gajiBersih) || 0), 0);
            const totalSlip = window.gajiList.length;
            const totalLunas = window.gajiList.filter(g => (g.statusPembayaran || '').toLowerCase() === 'lunas').length;
            const totalMenunggu = totalSlip - totalLunas;
            const avgGaji = totalSlip > 0 ? (totalPengeluaran / totalSlip) : 0;

            const uniquePeriodes = ['Semua', ...new Set(window.gajiList.map(g => g.periode).filter(Boolean))];

            // Filter
            const filtered = window.gajiList.filter(item => {
                const q = (window.gajiSearchQuery || '').toLowerCase();
                const mQuery = !q || (item.nama || '').toLowerCase().includes(q) ||
                    (item.nomorInduk || '').toLowerCase().includes(q) ||
                    (item.peran || '').toLowerCase().includes(q);
                const mStatus = window.gajiFilterStatus === 'Semua' || (item.status || '') === window.gajiFilterStatus;
                const mPeriode = window.gajiFilterPeriode === 'Semua' || (item.periode || '') === window.gajiFilterPeriode;
                const mBayar = window.gajiFilterStatusBayar === 'Semua' || (item.statusPembayaran || '').toLowerCase() === window.gajiFilterStatusBayar.toLowerCase();
                return mQuery && mStatus && mPeriode && mBayar;
            });

            container.innerHTML = `
                <div class="container-fluid p-4" style="max-width: 1300px;">
                    <!-- Header -->
                    <div class="d-flex flex-wrap justify-content-between align-items-center mb-4 pb-3 border-bottom" style="border-color: var(--border-color) !important;">
                        <div>
                            <div class="d-flex align-items-center gap-2">
                                <h3 class="fw-bold mb-0" style="color: #0F766E;"><i class="bi bi-cash-stack me-2"></i>Penggajian (Gaji Company)</h3>
                                <span class="badge" style="background-color: #0F766E;">COMPANY</span>
                            </div>
                            <p class="text-secondary mb-0 small">Sistem manajemen & input slip gaji terintegrasi berdasarkan Status Kepegawaian dan Peran / Jabatan.</p>
                        </div>
                        <div class="d-flex gap-2 mt-2 mt-md-0">
                            <button class="btn btn-outline-secondary btn-sm" onclick="exportGajiToCsv()">
                                <i class="bi bi-file-earmark-spreadsheet me-1"></i>Ekspor CSV
                            </button>
                            <button class="btn btn-outline-teal btn-sm" style="color: #0F766E; border-color: #0F766E;" onclick="printGajiRekap()">
                                <i class="bi bi-printer me-1"></i>Cetak Rekap
                            </button>
                            <button class="btn text-white btn-sm" style="background-color: #0F766E;" onclick="openGajiModal()">
                                <i class="bi bi-plus-circle me-1"></i>Input Gaji Baru
                            </button>
                        </div>
                    </div>

                    <!-- Finansial Metrics Cards -->
                    <div class="row g-3 mb-4">
                        <div class="col-12 col-sm-6 col-lg-3">
                            <div class="card shadow-sm border-0 border-start border-4 h-100" style="border-left-color: #0F766E !important; background: var(--card-bg); border: 1px solid var(--border-color); color: var(--text-main);">
                                <div class="card-body p-3">
                                    <div class="text-secondary small fw-semibold">Total Pengeluaran Gaji</div>
                                    <div class="h4 fw-bold mb-1" style="color: #0F766E;">${dol}{formatRupiahWeb(totalPengeluaran)}</div>
                                    <div class="small text-secondary">${dol}{totalSlip} total slip gaji diterbitkan</div>
                                </div>
                            </div>
                        </div>
                        <div class="col-12 col-sm-6 col-lg-3">
                            <div class="card shadow-sm border-0 border-start border-4 border-success h-100" style="border-left-color: #10B981 !important; background: var(--card-bg); border: 1px solid var(--border-color); color: var(--text-main);">
                                <div class="card-body p-3">
                                    <div class="text-secondary small fw-semibold">Gaji Lunas / Terbayar</div>
                                    <div class="h4 fw-bold text-success mb-1">${dol}{totalLunas} Slip</div>
                                    <div class="small text-success"><i class="bi bi-check-circle-fill me-1"></i>${dol}{totalSlip > 0 ? Math.round((totalLunas/totalSlip)*100) : 0}% telah dibayarkan</div>
                                </div>
                            </div>
                        </div>
                        <div class="col-12 col-sm-6 col-lg-3">
                            <div class="card shadow-sm border-0 border-start border-4 border-warning h-100" style="border-left-color: #F59E0B !important; background: var(--card-bg); border: 1px solid var(--border-color); color: var(--text-main);">
                                <div class="card-body p-3">
                                    <div class="text-secondary small fw-semibold">Gaji Menunggu / Diproses</div>
                                    <div class="h4 fw-bold text-warning mb-1">${dol}{totalMenunggu} Slip</div>
                                    <div class="small text-warning"><i class="bi bi-hourglass-split me-1"></i>Belum lunas / draf</div>
                                </div>
                            </div>
                        </div>
                        <div class="col-12 col-sm-6 col-lg-3">
                            <div class="card shadow-sm border-0 border-start border-4 border-info h-100" style="border-left-color: #0EA5E9 !important; background: var(--card-bg); border: 1px solid var(--border-color); color: var(--text-main);">
                                <div class="card-body p-3">
                                    <div class="text-secondary small fw-semibold">Rata-rata Take Home Pay</div>
                                    <div class="h4 fw-bold text-info mb-1">${dol}{formatRupiahWeb(avgGaji)}</div>
                                    <div class="small text-secondary">Per karyawan / slip</div>
                                </div>
                            </div>
                        </div>
                    </div>

                    <!-- Search and Filters Bar -->
                    <div class="card shadow-sm border-0 mb-4" style="background: var(--card-bg); border: 1px solid var(--border-color) !important; color: var(--text-main);">
                        <div class="card-body p-3">
                            <div class="row g-2 align-items-center">
                                <div class="col-12 col-md-4">
                                    <div class="input-group input-group-sm">
                                        <span class="input-group-text border-end-0"><i class="bi bi-search text-secondary"></i></span>
                                        <input type="text" class="form-control border-start-0" id="gaji-search-input" placeholder="Cari nama, NIP, atau jabatan..." value="${dol}{escapeHtml(window.gajiSearchQuery)}" oninput="window.gajiSearchQuery = this.value; renderGajiDashboard();">
                                        ${dol}{window.gajiSearchQuery ? `<button class="btn btn-outline-secondary" onclick="window.gajiSearchQuery=''; renderGajiDashboard();"><i class="bi bi-x"></i></button>` : ''}
                                    </div>
                                </div>
                                <div class="col-6 col-md-3">
                                    <select class="form-select form-select-sm" onchange="window.gajiFilterStatus = this.value; renderGajiDashboard();">
                                        <option value="Semua" ${dol}{window.gajiFilterStatus === 'Semua' ? 'selected' : ''}>Status: Semua Status</option>
                                        ${dol}{GAJI_STATUS_OPTIONS.map(st => `<option value="${dol}{st}" ${dol}{window.gajiFilterStatus === st ? 'selected' : ''}>${dol}{st}</option>`).join('')}
                                    </select>
                                </div>
                                <div class="col-6 col-md-3">
                                    <select class="form-select form-select-sm" onchange="window.gajiFilterPeriode = this.value; renderGajiDashboard();">
                                        ${dol}{uniquePeriodes.map(per => `<option value="${dol}{per}" ${dol}{window.gajiFilterPeriode === per ? 'selected' : ''}>Periode: ${dol}{per}</option>`).join('')}
                                    </select>
                                </div>
                                <div class="col-12 col-md-2">
                                    <select class="form-select form-select-sm" onchange="window.gajiFilterStatusBayar = this.value; renderGajiDashboard();">
                                        <option value="Semua" ${dol}{window.gajiFilterStatusBayar === 'Semua' ? 'selected' : ''}>Bayar: Semua</option>
                                        <option value="Lunas" ${dol}{window.gajiFilterStatusBayar === 'Lunas' ? 'selected' : ''}>Lunas</option>
                                        <option value="Menunggu" ${dol}{window.gajiFilterStatusBayar === 'Menunggu' ? 'selected' : ''}>Menunggu</option>
                                    </select>
                                </div>
                            </div>
                        </div>
                    </div>

                    <!-- Gaji Table Card -->
                    <div class="card shadow-sm border-0" style="background: var(--card-bg); border: 1px solid var(--border-color) !important; color: var(--text-main);">
                        <div class="card-header py-3 d-flex justify-content-between align-items-center" style="background: transparent; border-bottom: 1px solid var(--border-color);">
                            <h6 class="fw-bold mb-0" style="color: #0F766E;"><i class="bi bi-table me-2"></i>Daftar Riwayat Slip Gaji (${dol}{filtered.length})</h6>
                            <span class="badge border" style="background: var(--sidebar-bg); color: var(--text-main); border-color: var(--border-color) !important;">Total Disaring: ${dol}{formatRupiahWeb(filtered.reduce((a,c) => a + (Number(c.gajiBersih)||0), 0))}</span>
                        </div>
                        <div class="table-responsive">
                            <table class="table table-hover align-middle mb-0">
                                <thead class="small text-secondary" style="background-color: var(--table-th-bg); border-bottom: 1px solid var(--border-color);">
                                    <tr>
                                        <th style="width: 50px;" class="text-center">#</th>
                                        <th>Karyawan / Anggota</th>
                                        <th>Status Kepegawaian</th>
                                        <th>Peran / Posisi</th>
                                        <th>Periode & Tanggal</th>
                                        <th class="text-end">Penerimaan (+)</th>
                                        <th class="text-end">Potongan (-)</th>
                                        <th class="text-end fw-bold" style="color: #0F766E;">Gaji Bersih (THP)</th>
                                        <th class="text-center">Status</th>
                                        <th class="text-center" style="width: 140px;">Aksi</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    ${dol}{filtered.length === 0 ? `
                                        <tr>
                                            <td colspan="10" class="text-center py-5 text-secondary">
                                                <i class="bi bi-inbox fs-1 d-block mb-2 text-secondary"></i>
                                                Tidak ada slip gaji yang cocok dengan filter.<br>
                                                <button class="btn btn-sm btn-outline-teal mt-2" style="color:#0F766E; border-color:#0F766E;" onclick="openGajiModal()">
                                                    <i class="bi bi-plus-circle me-1"></i>Input Slip Gaji Baru
                                                </button>
                                            </td>
                                        </tr>
                                    ` : filtered.map((item, index) => `
                                        <tr>
                                            <td class="text-center text-secondary small">${dol}{index + 1}</td>
                                            <td>
                                                <div class="d-flex align-items-center">
                                                    <div class="rounded-circle d-flex align-items-center justify-content-center me-2 fw-bold text-white small" style="width: 34px; height: 34px; background-color: #0F766E;">
                                                        ${dol}{escapeHtml((item.nama || 'A').substring(0, 2).toUpperCase())}
                                                    </div>
                                                    <div>
                                                        <div class="fw-bold" style="color: var(--text-main);">${dol}{escapeHtml(item.nama)}</div>
                                                        <div class="text-secondary small">${dol}{escapeHtml(item.nomorInduk || '-')}</div>
                                                    </div>
                                                </div>
                                            </td>
                                            <td>
                                                <span class="badge" style="background-color: rgba(15, 118, 110, 0.15); color: #2DD4BF; border: 1px solid rgba(45, 212, 191, 0.3);">
                                                    ${dol}{escapeHtml(item.status || 'Karyawan')}
                                                </span>
                                            </td>
                                            <td>
                                                <div class="fw-semibold small" style="color: var(--text-main);">${dol}{escapeHtml(item.peran || '-')}</div>
                                            </td>
                                            <td>
                                                <div class="fw-semibold small" style="color: var(--text-main);">${dol}{escapeHtml(item.periode || '-')}</div>
                                                <div class="text-secondary small">${dol}{escapeHtml(item.tanggalBayar || '-')}</div>
                                            </td>
                                            <td class="text-end text-success small fw-semibold">
                                                ${dol}{formatRupiahWeb(item.totalPendapatan)}
                                            </td>
                                            <td class="text-end text-danger small fw-semibold">
                                                ${dol}{formatRupiahWeb(item.totalPotongan)}
                                            </td>
                                            <td class="text-end fw-bold" style="color: #0F766E;">
                                                ${dol}{formatRupiahWeb(item.gajiBersih)}
                                            </td>
                                            <td class="text-center">
                                                <span class="badge ${dol}{(item.statusPembayaran || '').toLowerCase() === 'lunas' ? 'bg-success' : 'bg-warning text-dark'}">
                                                    ${dol}{escapeHtml((item.statusPembayaran || 'Draf').toUpperCase())}
                                                </span>
                                            </td>
                                            <td class="text-center">
                                                <div class="btn-group btn-group-sm">
                                                    <button class="btn btn-outline-info" title="Lihat Slip Digital" onclick="viewDigitalSlip(${dol}{item.id})">
                                                        <i class="bi bi-receipt"></i>
                                                    </button>
                                                    <button class="btn btn-outline-primary" title="Edit Data" onclick="editGajiItem(${dol}{item.id})">
                                                        <i class="bi bi-pencil"></i>
                                                    </button>
                                                    <button class="btn btn-outline-danger" title="Hapus" onclick="deleteGajiItem(${dol}{item.id})">
                                                        <i class="bi bi-trash"></i>
                                                    </button>
                                                </div>
                                            </td>
                                        </tr>
                                    `).join('')}
                                </tbody>
                            </table>
                        </div>
                    </div>
                </div>
            `;
        }

        function openGajiModal(editItem = null) {
            const todayStr = new Date().toISOString().split('T')[0];
            const nowMonthStr = new Date().toLocaleString('id-ID', { month: 'long', year: 'numeric' });

            const current = editItem || {
                id: 0,
                anggotaId: 0,
                nomorInduk: '',
                nama: '',
                status: 'Karyawan Tetap',
                peran: 'Staff Operasional',
                periode: nowMonthStr,
                tanggalBayar: todayStr,
                gajiPokok: 4000000,
                tunjanganJabatan: 300000,
                tunjanganMakanTransport: 800000,
                tunjanganKehadiran: 500000,
                bonusLembur: 0,
                tunjanganLain: 0,
                potonganBpjs: 120000,
                potonganPph: 0,
                potonganKasbon: 0,
                potonganKeterlambatan: 0,
                potonganLain: 0,
                metodePembayaran: 'Transfer Bank',
                statusPembayaran: 'Lunas',
                catatan: ''
            };

            const isEdit = current.id > 0;

            const modalHtml = `
                <div class="modal fade" id="modalGajiForm" tabindex="-1">
                    <div class="modal-dialog modal-lg modal-dialog-scrollable">
                        <div class="modal-content">
                            <div class="modal-header text-white" style="background-color: #0F766E;">
                                <h5 class="modal-title fw-bold">
                                    <i class="bi bi-${dol}{isEdit ? 'pencil-square' : 'plus-circle'} me-2"></i>
                                    ${dol}{isEdit ? 'Edit Slip Gaji #' + current.id : 'Input Gaji Karyawan Berdasarkan Status & Peran'}
                                </h5>
                                <button type="button" class="btn-close btn-close-white" data-bs-dismiss="modal"></button>
                            </div>
                            <div class="modal-body p-4">
                                <form id="form-input-gaji">
                                    <!-- 1. Identitas & Standar -->
                                    <div class="card mb-3 border" style="background: var(--sidebar-bg); border-color: var(--border-color) !important; color: var(--text-main);">
                                        <div class="card-body p-3">
                                            <h6 class="fw-bold mb-3" style="color: #0F766E;"><i class="bi bi-person-badge me-2"></i>1. Identitas & Status Karyawan</h6>
                                            
                                            ${dol}{window.anggotaList && window.anggotaList.length > 0 ? `
                                                <div class="mb-3">
                                                    <label class="form-label small fw-semibold text-secondary">Pilih Cepat dari Data Anggota Terdaftar:</label>
                                                    <select class="form-select form-select-sm" onchange="onSelectAnggotaGaji(this.value)">
                                                        <option value="">-- Pilih Anggota (Opsional) --</option>
                                                        ${dol}{window.anggotaList.map(a => `<option value="${dol}{a.id}">${dol}{escapeHtml(a.nama)} (${dol}{escapeHtml(a.status)} - ${dol}{escapeHtml(a.divisiOrJurusan || 'Umum')})</option>`).join('')}
                                                    </select>
                                                </div>
                                            ` : ''}

                                            <div class="row g-2 mb-2">
                                                <div class="col-md-7">
                                                    <label class="form-label small fw-semibold">Nama Lengkap *</label>
                                                    <input type="text" class="form-control form-control-sm" id="gaji-in-nama" value="${dol}{escapeHtml(current.nama)}" required placeholder="Contoh: Budi Santoso">
                                                </div>
                                                <div class="col-md-5">
                                                    <label class="form-label small fw-semibold">NIP / NIM / ID Karyawan</label>
                                                    <input type="text" class="form-control form-control-sm" id="gaji-in-nip" value="${dol}{escapeHtml(current.nomorInduk)}" placeholder="Contoh: NIP-10293">
                                                </div>
                                            </div>

                                            <div class="row g-2 mb-2">
                                                <div class="col-md-6">
                                                    <label class="form-label small fw-semibold">Status Kepegawaian *</label>
                                                    <select class="form-select form-select-sm" id="gaji-in-status" onchange="onStatusPeranChanged()">
                                                        ${dol}{GAJI_STATUS_OPTIONS.map(st => `<option value="${dol}{st}" ${dol}{current.status === st ? 'selected' : ''}>${dol}{st}</option>`).join('')}
                                                    </select>
                                                </div>
                                                <div class="col-md-6">
                                                    <label class="form-label small fw-semibold">Peran / Jabatan / Posisi *</label>
                                                    <select class="form-select form-select-sm" id="gaji-in-peran" onchange="onStatusPeranChanged()">
                                                        ${dol}{GAJI_PERAN_OPTIONS.map(pr => `<option value="${dol}{pr}" ${dol}{current.peran === pr ? 'selected' : ''}>${dol}{pr}</option>`).join('')}
                                                    </select>
                                                </div>
                                            </div>

                                            <div class="d-flex justify-content-between align-items-center mt-2 pt-2 border-top" style="border-color: var(--border-color) !important;">
                                                <span class="small text-secondary">Kombinasi Status & Peran menentukan rekomendasi komponen gaji otomatis.</span>
                                                <button type="button" class="btn btn-sm btn-outline-teal" style="color: #0F766E; border-color: #0F766E;" onclick="applyPresetToInputs()">
                                                    <i class="bi bi-magic me-1"></i>Terapkan Standar Otomatis
                                                </button>
                                            </div>
                                        </div>
                                    </div>

                                    <!-- 2. Periode & Tanggal -->
                                    <div class="row g-2 mb-3">
                                        <div class="col-md-6">
                                            <label class="form-label small fw-semibold">Periode Gaji (Bulan & Tahun) *</label>
                                            <input type="text" class="form-control form-control-sm" id="gaji-in-periode" value="${dol}{escapeHtml(current.periode)}" required placeholder="Contoh: Agustus 2026">
                                        </div>
                                        <div class="col-md-6">
                                            <label class="form-label small fw-semibold">Tanggal Pembayaran *</label>
                                            <input type="date" class="form-control form-control-sm" id="gaji-in-tanggal" value="${dol}{escapeHtml(current.tanggalBayar)}" required>
                                        </div>
                                    </div>

                                    <!-- 3. Komponen Penerimaan (+) -->
                                    <div class="card mb-3 border border-success border-opacity-25" style="background-color: rgba(16, 185, 129, 0.08); color: var(--text-main);">
                                        <div class="card-body p-3">
                                            <h6 class="fw-bold text-success mb-3"><i class="bi bi-plus-circle me-2"></i>2. Komponen Penerimaan / Gaji (+)</h6>
                                            <div class="row g-2 mb-2">
                                                <div class="col-md-6">
                                                    <label class="form-label small fw-semibold">Gaji Pokok (Rp) *</label>
                                                    <input type="number" class="form-control form-control-sm" id="gaji-in-pokok" value="${dol}{current.gajiPokok}" oninput="calcGajiLive()" required>
                                                </div>
                                                <div class="col-md-6">
                                                    <label class="form-label small fw-semibold">Tunjangan Jabatan / Peran (Rp)</label>
                                                    <input type="number" class="form-control form-control-sm" id="gaji-in-jabatan" value="${dol}{current.tunjanganJabatan}" oninput="calcGajiLive()">
                                                </div>
                                            </div>
                                            <div class="row g-2 mb-2">
                                                <div class="col-md-6">
                                                    <label class="form-label small fw-semibold">Tunjangan Makan & Transport</label>
                                                    <input type="number" class="form-control form-control-sm" id="gaji-in-transport" value="${dol}{current.tunjanganMakanTransport}" oninput="calcGajiLive()">
                                                </div>
                                                <div class="col-md-6">
                                                    <label class="form-label small fw-semibold">Tunjangan Kehadiran / Disiplin</label>
                                                    <input type="number" class="form-control form-control-sm" id="gaji-in-kehadiran" value="${dol}{current.tunjanganKehadiran}" oninput="calcGajiLive()">
                                                </div>
                                            </div>
                                            <div class="row g-2">
                                                <div class="col-md-6">
                                                    <label class="form-label small fw-semibold">Bonus Lembur / Insentif</label>
                                                    <input type="number" class="form-control form-control-sm" id="gaji-in-lembur" value="${dol}{current.bonusLembur}" oninput="calcGajiLive()">
                                                </div>
                                                <div class="col-md-6">
                                                    <label class="form-label small fw-semibold">Tunjangan Lainnya</label>
                                                    <input type="number" class="form-control form-control-sm" id="gaji-in-tunjlain" value="${dol}{current.tunjanganLain}" oninput="calcGajiLive()">
                                                </div>
                                            </div>
                                        </div>
                                    </div>

                                    <!-- 4. Komponen Potongan (-) -->
                                    <div class="card mb-3 border border-danger border-opacity-25" style="background-color: rgba(239, 68, 68, 0.08); color: var(--text-main);">
                                        <div class="card-body p-3">
                                            <h6 class="fw-bold text-danger mb-3"><i class="bi bi-dash-circle me-2"></i>3. Komponen Potongan (-)</h6>
                                            <div class="row g-2 mb-2">
                                                <div class="col-md-6">
                                                    <label class="form-label small fw-semibold">BPJS Kes & Ketenagakerjaan</label>
                                                    <input type="number" class="form-control form-control-sm" id="gaji-in-bpjs" value="${dol}{current.potonganBpjs}" oninput="calcGajiLive()">
                                                </div>
                                                <div class="col-md-6">
                                                    <label class="form-label small fw-semibold">Pajak PPh 21 (Rp)</label>
                                                    <input type="number" class="form-control form-control-sm" id="gaji-in-pph" value="${dol}{current.potonganPph}" oninput="calcGajiLive()">
                                                </div>
                                            </div>
                                            <div class="row g-2 mb-2">
                                                <div class="col-md-6">
                                                    <label class="form-label small fw-semibold">Kasbon / Pinjaman Karyawan</label>
                                                    <input type="number" class="form-control form-control-sm" id="gaji-in-kasbon" value="${dol}{current.potonganKasbon}" oninput="calcGajiLive()">
                                                </div>
                                                <div class="col-md-6">
                                                    <label class="form-label small fw-semibold">Potongan Terlambat / Absen</label>
                                                    <input type="number" class="form-control form-control-sm" id="gaji-in-telat" value="${dol}{current.potonganKeterlambatan}" oninput="calcGajiLive()">
                                                </div>
                                            </div>
                                            <div class="row g-2">
                                                <div class="col-12">
                                                    <label class="form-label small fw-semibold">Potongan Lainnya</label>
                                                    <input type="number" class="form-control form-control-sm" id="gaji-in-potlain" value="${dol}{current.potonganLain}" oninput="calcGajiLive()">
                                                </div>
                                            </div>
                                        </div>
                                    </div>

                                    <!-- 5. Metode & Status -->
                                    <div class="row g-2 mb-3">
                                        <div class="col-md-6">
                                            <label class="form-label small fw-semibold">Metode Pembayaran</label>
                                            <select class="form-select form-select-sm" id="gaji-in-metode">
                                                <option value="Transfer Bank" ${dol}{current.metodePembayaran === 'Transfer Bank' ? 'selected' : ''}>Transfer Bank</option>
                                                <option value="Tunai" ${dol}{current.metodePembayaran === 'Tunai' ? 'selected' : ''}>Tunai</option>
                                                <option value="E-Wallet (GoPay/OVO/Dana)" ${dol}{current.metodePembayaran === 'E-Wallet (GoPay/OVO/Dana)' ? 'selected' : ''}>E-Wallet (GoPay/OVO/Dana)</option>
                                                <option value="Cek / Bilyet Giro" ${dol}{current.metodePembayaran === 'Cek / Bilyet Giro' ? 'selected' : ''}>Cek / Bilyet Giro</option>
                                            </select>
                                        </div>
                                        <div class="col-md-6">
                                            <label class="form-label small fw-semibold">Status Pembayaran</label>
                                            <select class="form-select form-select-sm" id="gaji-in-statusbayar">
                                                <option value="Lunas" ${dol}{current.statusPembayaran === 'Lunas' ? 'selected' : ''}>Lunas</option>
                                                <option value="Menunggu" ${dol}{current.statusPembayaran === 'Menunggu' ? 'selected' : ''}>Menunggu</option>
                                                <option value="Diproses" ${dol}{current.statusPembayaran === 'Diproses' ? 'selected' : ''}>Diproses</option>
                                                <option value="Draf" ${dol}{current.statusPembayaran === 'Draf' ? 'selected' : ''}>Draf</option>
                                            </select>
                                        </div>
                                    </div>

                                    <div class="mb-3">
                                        <label class="form-label small fw-semibold">Catatan / Keterangan Tambahan</label>
                                        <textarea class="form-control form-control-sm" id="gaji-in-catatan" rows="2" placeholder="Catatan slip gaji...">${dol}{escapeHtml(current.catatan || '')}</textarea>
                                    </div>

                                    <!-- Live Summary Box -->
                                    <div class="card text-white p-3 mb-0" style="background: linear-gradient(135deg, #0F766E 0%, #115E59 100%); border-radius: 12px;">
                                        <div class="d-flex justify-content-between small mb-1">
                                            <span>Total Penerimaan (+):</span>
                                            <span class="fw-bold" id="live-total-penerimaan">Rp 0</span>
                                        </div>
                                        <div class="d-flex justify-content-between small mb-2 text-danger-emphasis" style="color: #FFCDD2 !important;">
                                            <span>Total Potongan (-):</span>
                                            <span class="fw-bold" id="live-total-potongan">Rp 0</span>
                                        </div>
                                        <hr class="my-2 border-white opacity-25">
                                        <div class="d-flex justify-content-between align-items-center">
                                            <div>
                                                <div class="small text-white-50">GAJI BERSIH (TAKE HOME PAY)</div>
                                                <div class="h4 fw-bold mb-0 text-white" id="live-gaji-bersih">Rp 0</div>
                                            </div>
                                            <span class="badge bg-white fs-6" style="color: #0F766E;" id="live-status-badge">LUNAS</span>
                                        </div>
                                    </div>
                                </form>
                            </div>
                            <div class="modal-footer" style="border-top: 1px solid var(--border-color);">
                                <button type="button" class="btn btn-secondary btn-sm" data-bs-dismiss="modal">Batal</button>
                                <button type="button" class="btn text-white btn-sm px-4" style="background-color: #0F766E;" onclick="submitGajiForm(${dol}{current.id})">
                                    <i class="bi bi-save me-1"></i>${dol}{isEdit ? 'Simpan Perubahan' : 'Terbitkan Slip Gaji'}
                                </button>
                            </div>
                        </div>
                    </div>
                </div>
            `;

            const existingModal = document.getElementById('modalGajiForm');
            if (existingModal) existingModal.remove();

            document.body.insertAdjacentHTML('beforeend', modalHtml);
            const modalEl = document.getElementById('modalGajiForm');
            const bsModal = new bootstrap.Modal(modalEl);
            bsModal.show();

            calcGajiLive();
        }

        window.onSelectAnggotaGaji = function(anggotaId) {
            if (!anggotaId) return;
            const a = (window.anggotaList || []).find(x => x.id == anggotaId);
            if (!a) return;

            document.getElementById('gaji-in-nama').value = a.nama || '';
            document.getElementById('gaji-in-nip').value = a.nomorInduk || '';

            // Map status
            const stEl = document.getElementById('gaji-in-status');
            for (let opt of stEl.options) {
                if (opt.value.toLowerCase().includes((a.status || '').toLowerCase())) {
                    stEl.value = opt.value;
                    break;
                }
            }

            // Map peran
            const prEl = document.getElementById('gaji-in-peran');
            if (a.divisiOrJurusan) {
                for (let opt of prEl.options) {
                    if (opt.value.toLowerCase().includes(a.divisiOrJurusan.toLowerCase())) {
                        prEl.value = opt.value;
                        break;
                    }
                }
            }

            applyPresetToInputs();
        };

        window.onStatusPeranChanged = function() {
            // Can optionally trigger or notify
        };

        window.applyPresetToInputs = function() {
            const st = document.getElementById('gaji-in-status').value;
            const pr = document.getElementById('gaji-in-peran').value;
            const preset = getPresetStandarGaji(st, pr);

            document.getElementById('gaji-in-pokok').value = preset.gajiPokok;
            document.getElementById('gaji-in-jabatan').value = preset.tunjanganJabatan;
            document.getElementById('gaji-in-transport').value = preset.tunjanganMakanTransport;
            document.getElementById('gaji-in-kehadiran').value = preset.tunjanganKehadiran;
            document.getElementById('gaji-in-bpjs').value = preset.potonganBpjs;
            document.getElementById('gaji-in-pph').value = preset.potonganPph;

            calcGajiLive();
        };

        window.calcGajiLive = function() {
            const pokok = Number(document.getElementById('gaji-in-pokok')?.value) || 0;
            const jabatan = Number(document.getElementById('gaji-in-jabatan')?.value) || 0;
            const transport = Number(document.getElementById('gaji-in-transport')?.value) || 0;
            const kehadiran = Number(document.getElementById('gaji-in-kehadiran')?.value) || 0;
            const lembur = Number(document.getElementById('gaji-in-lembur')?.value) || 0;
            const tunjLain = Number(document.getElementById('gaji-in-tunjlain')?.value) || 0;

            const totPenerimaan = pokok + jabatan + transport + kehadiran + lembur + tunjLain;

            const bpjs = Number(document.getElementById('gaji-in-bpjs')?.value) || 0;
            const pph = Number(document.getElementById('gaji-in-pph')?.value) || 0;
            const kasbon = Number(document.getElementById('gaji-in-kasbon')?.value) || 0;
            const telat = Number(document.getElementById('gaji-in-telat')?.value) || 0;
            const potLain = Number(document.getElementById('gaji-in-potlain')?.value) || 0;

            const totPotongan = bpjs + pph + kasbon + telat + potLain;
            const gajiBersih = Math.max(0, totPenerimaan - totPotongan);

            const elPen = document.getElementById('live-total-penerimaan');
            const elPot = document.getElementById('live-total-potongan');
            const elTHP = document.getElementById('live-gaji-bersih');

            if (elPen) elPen.innerText = formatRupiahWeb(totPenerimaan);
            if (elPot) elPot.innerText = formatRupiahWeb(totPotongan);
            if (elTHP) elTHP.innerText = formatRupiahWeb(gajiBersih);
        };

        window.submitGajiForm = async function(existingId = 0) {
            const nama = document.getElementById('gaji-in-nama').value.trim();
            if (!nama) {
                alert("Nama Karyawan tidak boleh kosong!");
                return;
            }

            const payload = {
                id: existingId,
                nama: nama,
                nomorInduk: document.getElementById('gaji-in-nip').value.trim(),
                status: document.getElementById('gaji-in-status').value,
                peran: document.getElementById('gaji-in-peran').value,
                periode: document.getElementById('gaji-in-periode').value.trim(),
                tanggalBayar: document.getElementById('gaji-in-tanggal').value,
                gajiPokok: Number(document.getElementById('gaji-in-pokok').value) || 0,
                tunjanganJabatan: Number(document.getElementById('gaji-in-jabatan').value) || 0,
                tunjanganMakanTransport: Number(document.getElementById('gaji-in-transport').value) || 0,
                tunjanganKehadiran: Number(document.getElementById('gaji-in-kehadiran').value) || 0,
                bonusLembur: Number(document.getElementById('gaji-in-lembur').value) || 0,
                tunjanganLain: Number(document.getElementById('gaji-in-tunjlain').value) || 0,
                potonganBpjs: Number(document.getElementById('gaji-in-bpjs').value) || 0,
                potonganPph: Number(document.getElementById('gaji-in-pph').value) || 0,
                potonganKasbon: Number(document.getElementById('gaji-in-kasbon').value) || 0,
                potonganKeterlambatan: Number(document.getElementById('gaji-in-telat').value) || 0,
                potonganLain: Number(document.getElementById('gaji-in-potlain').value) || 0,
                metodePembayaran: document.getElementById('gaji-in-metode').value,
                statusPembayaran: document.getElementById('gaji-in-statusbayar').value,
                catatan: document.getElementById('gaji-in-catatan').value.trim()
            };

            try {
                const method = existingId > 0 ? 'PUT' : 'POST';
                const res = await fetch('/api/gaji', {
                    method: method,
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(payload)
                });

                if (res.ok) {
                    const modalEl = document.getElementById('modalGajiForm');
                    const bsModal = bootstrap.Modal.getInstance(modalEl);
                    if (bsModal) bsModal.hide();
                    await fetchGajiList();
                    renderGajiDashboard();
                } else {
                    alert("Gagal menyimpan slip gaji!");
                }
            } catch(e) {
                alert("Error menyimpan: " + e);
            }
        };

        window.editGajiItem = function(id) {
            const item = (window.gajiList || []).find(x => x.id === id);
            if (item) openGajiModal(item);
        };

        window.deleteGajiItem = async function(id) {
            const item = (window.gajiList || []).find(x => x.id === id);
            if (!item) return;

            if (!confirm(`Hapus slip gaji untuk ${dol}{item.nama} periode ${dol}{item.periode}?`)) return;

            try {
                const res = await fetch(`/api/gaji?id=${dol}{id}`, { method: 'DELETE' });
                if (res.ok) {
                    await fetchGajiList();
                    renderGajiDashboard();
                } else {
                    alert("Gagal menghapus data!");
                }
            } catch(e) {
                alert("Error menghapus: " + e);
            }
        };

        window.viewDigitalSlip = function(id) {
            const slip = (window.gajiList || []).find(x => x.id === id);
            if (!slip) return;

            const slipModalHtml = `
                <div class="modal fade" id="modalDigitalSlip" tabindex="-1">
                    <div class="modal-dialog modal-lg">
                        <div class="modal-content" style="background: var(--card-bg); color: var(--text-main); border: 1px solid var(--border-color);">
                            <div class="modal-header text-white" style="background-color: #0F766E; border-bottom: 1px solid var(--border-color);">
                                <h5 class="modal-title fw-bold"><i class="bi bi-receipt me-2"></i>SLIP GAJI RESMI KARYAWAN</h5>
                                <button type="button" class="btn-close btn-close-white" data-bs-dismiss="modal"></button>
                            </div>
                            <div class="modal-body p-4" id="printable-slip-area">
                                <div class="p-4 rounded shadow-sm" style="background: var(--card-bg); color: var(--text-main); border: 1px solid var(--border-color); font-family: 'Segoe UI', Arial, sans-serif;">
                                    <!-- Slip Header -->
                                    <div class="d-flex justify-content-between align-items-center pb-3 mb-3 border-bottom border-2" style="border-color: #0F766E !important;">
                                        <div>
                                            <h4 class="fw-bold mb-0" style="color: #0F766E;">MY SINGGHARA COMPANY</h4>
                                            <div class="text-secondary small">Sistem Penggajian Terpadu & Payroll Otomatis</div>
                                        </div>
                                        <div class="text-end">
                                            <div class="badge px-3 py-2 fs-6" style="background-color: #0F766E;">SLIP GAJI KARYAWAN</div>
                                            <div class="small text-secondary mt-1">Periode: <strong>${dol}{escapeHtml(slip.periode)}</strong></div>
                                        </div>
                                    </div>

                                    <!-- Employee Info -->
                                    <div class="row g-2 mb-4 p-3 rounded" style="background-color: var(--sidebar-bg); border: 1px solid var(--border-color); color: var(--text-main);">
                                        <div class="col-6">
                                            <div class="small text-secondary">Nama Karyawan</div>
                                            <div class="fw-bold fs-6" style="color: var(--text-main);">${dol}{escapeHtml(slip.nama)}</div>
                                        </div>
                                        <div class="col-6">
                                            <div class="small text-secondary">NIP / ID Karyawan</div>
                                            <div class="fw-semibold" style="color: var(--text-main);">${dol}{escapeHtml(slip.nomorInduk || '-')}</div>
                                        </div>
                                        <div class="col-6">
                                            <div class="small text-secondary">Status Kepegawaian</div>
                                            <div class="fw-semibold" style="color: #2DD4BF;">${dol}{escapeHtml(slip.status)}</div>
                                        </div>
                                        <div class="col-6">
                                            <div class="small text-secondary">Peran / Jabatan</div>
                                            <div class="fw-semibold" style="color: var(--text-main);">${dol}{escapeHtml(slip.peran)}</div>
                                        </div>
                                        <div class="col-6">
                                            <div class="small text-secondary">Tanggal Pembayaran</div>
                                            <div class="fw-semibold" style="color: var(--text-main);">${dol}{escapeHtml(slip.tanggalBayar)}</div>
                                        </div>
                                        <div class="col-6">
                                            <div class="small text-secondary">Metode / Status</div>
                                            <div class="fw-semibold" style="color: var(--text-main);">${dol}{escapeHtml(slip.metodePembayaran)} - <span class="badge ${dol}{(slip.statusPembayaran || '').toLowerCase() === 'lunas' ? 'bg-success' : 'bg-warning text-dark'}">${dol}{escapeHtml((slip.statusPembayaran || '').toUpperCase())}</span></div>
                                        </div>
                                    </div>

                                    <!-- Breakdown Tables -->
                                    <div class="row g-4 mb-4">
                                        <!-- Penerimaan Column -->
                                        <div class="col-md-6">
                                            <div class="p-3 rounded h-100" style="border: 1px solid rgba(16, 185, 129, 0.35) !important; background-color: rgba(16, 185, 129, 0.08); color: var(--text-main);">
                                                <h6 class="fw-bold text-success border-bottom pb-2 mb-2" style="border-color: rgba(16, 185, 129, 0.25) !important;"><i class="bi bi-plus-circle me-1"></i>PENERIMAAN (EARNINGS)</h6>
                                                <div class="d-flex justify-content-between small py-1 border-bottom" style="border-color: var(--border-color) !important;">
                                                    <span>Gaji Pokok:</span>
                                                    <span class="fw-semibold">${dol}{formatRupiahWeb(slip.gajiPokok)}</span>
                                                </div>
                                                ${dol}{slip.tunjanganJabatan > 0 ? `
                                                    <div class="d-flex justify-content-between small py-1 border-bottom" style="border-color: var(--border-color) !important;">
                                                        <span>Tunjangan Jabatan:</span>
                                                        <span class="fw-semibold">${dol}{formatRupiahWeb(slip.tunjanganJabatan)}</span>
                                                    </div>
                                                ` : ''}
                                                ${dol}{slip.tunjanganMakanTransport > 0 ? `
                                                    <div class="d-flex justify-content-between small py-1 border-bottom" style="border-color: var(--border-color) !important;">
                                                        <span>Tunj. Makan & Transport:</span>
                                                        <span class="fw-semibold">${dol}{formatRupiahWeb(slip.tunjanganMakanTransport)}</span>
                                                    </div>
                                                ` : ''}
                                                ${dol}{slip.tunjanganKehadiran > 0 ? `
                                                    <div class="d-flex justify-content-between small py-1 border-bottom" style="border-color: var(--border-color) !important;">
                                                        <span>Tunj. Kehadiran:</span>
                                                        <span class="fw-semibold">${dol}{formatRupiahWeb(slip.tunjanganKehadiran)}</span>
                                                    </div>
                                                ` : ''}
                                                ${dol}{slip.bonusLembur > 0 ? `
                                                    <div class="d-flex justify-content-between small py-1 border-bottom" style="border-color: var(--border-color) !important;">
                                                        <span>Bonus Lembur / Insentif:</span>
                                                        <span class="fw-semibold">${dol}{formatRupiahWeb(slip.bonusLembur)}</span>
                                                    </div>
                                                ` : ''}
                                                ${dol}{slip.tunjanganLain > 0 ? `
                                                    <div class="d-flex justify-content-between small py-1 border-bottom" style="border-color: var(--border-color) !important;">
                                                        <span>Tunjangan Lainnya:</span>
                                                        <span class="fw-semibold">${dol}{formatRupiahWeb(slip.tunjanganLain)}</span>
                                                    </div>
                                                ` : ''}
                                                <div class="d-flex justify-content-between fw-bold text-success pt-2">
                                                    <span>Total Penerimaan:</span>
                                                    <span>${dol}{formatRupiahWeb(slip.totalPendapatan)}</span>
                                                </div>
                                            </div>
                                        </div>

                                        <!-- Potongan Column -->
                                        <div class="col-md-6">
                                            <div class="p-3 rounded h-100" style="border: 1px solid rgba(239, 68, 68, 0.35) !important; background-color: rgba(239, 68, 68, 0.08); color: var(--text-main);">
                                                <h6 class="fw-bold text-danger border-bottom pb-2 mb-2" style="border-color: rgba(239, 68, 68, 0.25) !important;"><i class="bi bi-dash-circle me-1"></i>POTONGAN (DEDUCTIONS)</h6>
                                                ${dol}{slip.potonganBpjs > 0 ? `
                                                    <div class="d-flex justify-content-between small py-1 border-bottom" style="border-color: var(--border-color) !important;">
                                                        <span>BPJS Kes & Naker:</span>
                                                        <span class="fw-semibold">${dol}{formatRupiahWeb(slip.potonganBpjs)}</span>
                                                    </div>
                                                ` : ''}
                                                ${dol}{slip.potonganPph > 0 ? `
                                                    <div class="d-flex justify-content-between small py-1 border-bottom" style="border-color: var(--border-color) !important;">
                                                        <span>Pajak PPh 21:</span>
                                                        <span class="fw-semibold">${dol}{formatRupiahWeb(slip.potonganPph)}</span>
                                                    </div>
                                                ` : ''}
                                                ${dol}{slip.potonganKasbon > 0 ? `
                                                    <div class="d-flex justify-content-between small py-1 border-bottom" style="border-color: var(--border-color) !important;">
                                                        <span>Kasbon / Pinjaman:</span>
                                                        <span class="fw-semibold">${dol}{formatRupiahWeb(slip.potonganKasbon)}</span>
                                                    </div>
                                                ` : ''}
                                                ${dol}{slip.potonganKeterlambatan > 0 ? `
                                                    <div class="d-flex justify-content-between small py-1 border-bottom" style="border-color: var(--border-color) !important;">
                                                        <span>Potongan Telat / Absen:</span>
                                                        <span class="fw-semibold">${dol}{formatRupiahWeb(slip.potonganKeterlambatan)}</span>
                                                    </div>
                                                ` : ''}
                                                ${dol}{slip.potonganLain > 0 ? `
                                                    <div class="d-flex justify-content-between small py-1 border-bottom" style="border-color: var(--border-color) !important;">
                                                        <span>Potongan Lainnya:</span>
                                                        <span class="fw-semibold">${dol}{formatRupiahWeb(slip.potonganLain)}</span>
                                                    </div>
                                                ` : ''}
                                                ${dol}{slip.totalPotongan === 0 ? `<div class="text-secondary small py-2">Tidak ada potongan.</div>` : ''}
                                                <div class="d-flex justify-content-between fw-bold text-danger pt-2">
                                                    <span>Total Potongan:</span>
                                                    <span>${dol}{formatRupiahWeb(slip.totalPotongan)}</span>
                                                </div>
                                            </div>
                                        </div>
                                    </div>

                                    <!-- Final THP Banner -->
                                    <div class="p-3 rounded text-white mb-3" style="background: linear-gradient(135deg, #0F766E 0%, #115E59 100%);">
                                        <div class="d-flex justify-content-between align-items-center">
                                            <div>
                                                <div class="small opacity-75">GAJI BERSIH DITERIMA (TAKE HOME PAY)</div>
                                                <div class="h3 fw-bold mb-0">${dol}{formatRupiahWeb(slip.gajiBersih)}</div>
                                            </div>
                                            <div class="text-end">
                                                <div class="small opacity-75">STATUS PEMBAYARAN</div>
                                                <div class="h5 fw-bold mb-0">${dol}{escapeHtml((slip.statusPembayaran || '').toUpperCase())}</div>
                                            </div>
                                        </div>
                                    </div>

                                    ${dol}{slip.catatan ? `<div class="small text-secondary fst-italic mb-3">Catatan: ${dol}{escapeHtml(slip.catatan)}</div>` : ''}

                                    <!-- Signatures Section -->
                                    <div class="row text-center mt-4 pt-3 border-top" style="border-color: var(--border-color) !important;">
                                        <div class="col-6">
                                            <div class="small text-secondary mb-5">Penerima,</div>
                                            <div class="fw-bold" style="color: var(--text-main);">${dol}{escapeHtml(slip.nama)}</div>
                                        </div>
                                        <div class="col-6">
                                            <div class="small text-secondary mb-5">Finance / Manajemen,</div>
                                            <div class="fw-bold" style="color: var(--text-main);">My Singghara HRD</div>
                                        </div>
                                    </div>
                                </div>
                            </div>
                            <div class="modal-footer" style="border-top: 1px solid var(--border-color);">
                                <button type="button" class="btn btn-secondary btn-sm" data-bs-dismiss="modal">Tutup</button>
                                <button type="button" class="btn btn-primary btn-sm" onclick="printElement('printable-slip-area')">
                                    <i class="bi bi-printer me-1"></i>Cetak Slip (Print / PDF)
                                </button>
                            </div>
                        </div>
                    </div>
                </div>
            `;

            const existing = document.getElementById('modalDigitalSlip');
            if (existing) existing.remove();

            document.body.insertAdjacentHTML('beforeend', slipModalHtml);
            const modalEl = document.getElementById('modalDigitalSlip');
            const bsModal = new bootstrap.Modal(modalEl);
            bsModal.show();
        };

        window.printElement = function(elementId) {
            const printContent = document.getElementById(elementId).innerHTML;
            const printWindow = window.open('', '', 'height=700,width=900');
            printWindow.document.write('<html><head><title>Cetak Slip Gaji</title>');
            printWindow.document.write('<link href="/assets/css/bootstrap.min.css" rel="stylesheet">');
            printWindow.document.write('<style>@media print { body { padding: 20px; background: #fff !important; color: #000 !important; } .btn { display: none; } * { color: inherit; } } </style>');
            printWindow.document.write('</head><body style="background: #fff; color: #111;">');
            printWindow.document.write(printContent);
            printWindow.document.write('</body></html>');
            printWindow.document.close();
            printWindow.focus();
            setTimeout(() => {
                printWindow.print();
                printWindow.close();
            }, 500);
        };

        window.exportGajiToCsv = function() {
            if (!window.gajiList || window.gajiList.length === 0) {
                alert("Belum ada data gaji untuk diekspor!");
                return;
            }

            let csv = "ID,Nomor Induk,Nama,Status,Peran,Periode,Tanggal Bayar,Gaji Pokok,Tunjangan Jabatan,Tunjangan Transport,Tunjangan Kehadiran,Bonus Lembur,Tunjangan Lain,Potongan BPJS,Potongan PPh,Potongan Kasbon,Potongan Telat,Potongan Lain,Total Pendapatan,Total Potongan,Gaji Bersih,Metode,Status Bayar,Catatan\n";

            window.gajiList.forEach(g => {
                csv += `${dol}{g.id},"${dol}{g.nomorInduk||''}","${dol}{g.nama||''}","${dol}{g.status||''}","${dol}{g.peran||''}","${dol}{g.periode||''}","${dol}{g.tanggalBayar||''}",${dol}{g.gajiPokok||0},${dol}{g.tunjanganJabatan||0},${dol}{g.tunjanganMakanTransport||0},${dol}{g.tunjanganKehadiran||0},${dol}{g.bonusLembur||0},${dol}{g.tunjanganLain||0},${dol}{g.potonganBpjs||0},${dol}{g.potonganPph||0},${dol}{g.potonganKasbon||0},${dol}{g.potonganKeterlambatan||0},${dol}{g.potonganLain||0},${dol}{g.totalPendapatan||0},${dol}{g.totalPotongan||0},${dol}{g.gajiBersih||0},"${dol}{g.metodePembayaran||''}","${dol}{g.statusPembayaran||''}","${dol}{g.catatan||''}"\n`;
            });

            const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
            const url = URL.createObjectURL(blob);
            const link = document.createElement('a');
            link.setAttribute('href', url);
            link.setAttribute('download', `rekap_gaji_${dol}{Date.now()}.csv`);
            document.body.appendChild(link);
            link.click();
            document.body.removeChild(link);
        };

        window.printGajiRekap = function() {
            window.print();
        };
        """
    }
}
