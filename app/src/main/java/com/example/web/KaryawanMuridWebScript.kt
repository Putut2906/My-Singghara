package com.example.web

object KaryawanMuridWebScript {
    fun getScript(dol: String): String {
        return """
        // ==========================================
        // --- MODUL DATA KARYAWAN & MURID ---
        // ==========================================
        window.anggotaList = [];
        window.presensiList = [];
        window.anggotaActiveTab = 'anggota'; // 'anggota', 'presensi', 'batch_print'
        window.anggotaFilterStatus = 'Semua';
        window.anggotaSearchQuery = '';
        window.anggotaViewMode = 'grid'; // 'grid', 'table'
        window.selectedAnggotaCardId = null;
        window.cardSide = 'front'; // 'front', 'back'
        window.html5QrScanner = null;
        window.isWebcamScanning = false;
        window.batchSelectedIds = new Set();

        // Audio chime & speech synthesizer for attendance types
        function playAbsensiSoundWeb(data, isPulang, fallbackNama) {
            const nama = (data && data.nama) ? data.nama : (fallbackNama || 'Anggota');
            const statusPresensi = (data && data.statusPresensi) || '';
            const statusPelanggaran = (data && data.statusPelanggaran) || '';

            let soundType = 'MASUK_TEPAT';
            if (isPulang) {
                if (statusPelanggaran.includes('Awal') || statusPelanggaran.includes('Cepat')) {
                    soundType = 'PULANG_AWAL';
                } else {
                    soundType = 'PULANG_TEPAT';
                }
            } else if (statusPelanggaran.includes('Terlambat') || statusPresensi.includes('Terlambat')) {
                soundType = 'MASUK_TERLAMBAT';
            } else if (statusPresensi.includes('Izin') || statusPresensi.includes('Sakit')) {
                soundType = 'IZIN_SAKIT';
            } else {
                soundType = 'MASUK_TEPAT';
            }

            try {
                const ctx = new (window.AudioContext || window.webkitAudioContext)();
                const now = ctx.currentTime;

                if (soundType === 'MASUK_TEPAT') {
                    // Melodic major chime (C5 -> E5 -> G5)
                    [523.25, 659.25, 783.99].forEach((freq, i) => {
                        const osc = ctx.createOscillator();
                        const gain = ctx.createGain();
                        osc.type = 'sine';
                        osc.frequency.setValueAtTime(freq, now + i * 0.09);
                        gain.gain.setValueAtTime(0.12, now + i * 0.09);
                        gain.gain.exponentialRampToValueAtTime(0.001, now + i * 0.09 + 0.25);
                        osc.connect(gain);
                        gain.connect(ctx.destination);
                        osc.start(now + i * 0.09);
                        osc.stop(now + i * 0.09 + 0.25);
                    });
                } else if (soundType === 'MASUK_TERLAMBAT') {
                    // Alert warning tone (A4 -> E4)
                    [440, 329.63].forEach((freq, i) => {
                        const osc = ctx.createOscillator();
                        const gain = ctx.createGain();
                        osc.type = 'triangle';
                        osc.frequency.setValueAtTime(freq, now + i * 0.15);
                        gain.gain.setValueAtTime(0.18, now + i * 0.15);
                        gain.gain.exponentialRampToValueAtTime(0.001, now + i * 0.15 + 0.2);
                        osc.connect(gain);
                        gain.connect(ctx.destination);
                        osc.start(now + i * 0.15);
                        osc.stop(now + i * 0.15 + 0.2);
                    });
                } else if (soundType === 'PULANG_TEPAT') {
                    // Departure melody (G5 -> E5 -> C5)
                    [783.99, 659.25, 523.25].forEach((freq, i) => {
                        const osc = ctx.createOscillator();
                        const gain = ctx.createGain();
                        osc.type = 'sine';
                        osc.frequency.setValueAtTime(freq, now + i * 0.1);
                        gain.gain.setValueAtTime(0.12, now + i * 0.1);
                        gain.gain.exponentialRampToValueAtTime(0.001, now + i * 0.1 + 0.28);
                        osc.connect(gain);
                        gain.connect(ctx.destination);
                        osc.start(now + i * 0.1);
                        osc.stop(now + i * 0.1 + 0.28);
                    });
                } else if (soundType === 'PULANG_AWAL') {
                    // Pulse warning for early departure
                    [493.88, 440.0].forEach((freq, i) => {
                        const osc = ctx.createOscillator();
                        const gain = ctx.createGain();
                        osc.type = 'sawtooth';
                        osc.frequency.setValueAtTime(freq, now + i * 0.18);
                        gain.gain.setValueAtTime(0.12, now + i * 0.18);
                        gain.gain.exponentialRampToValueAtTime(0.001, now + i * 0.18 + 0.22);
                        osc.connect(gain);
                        gain.connect(ctx.destination);
                        osc.start(now + i * 0.18);
                        osc.stop(now + i * 0.18 + 0.22);
                    });
                } else {
                    // Soft ping for Izin / Sakit
                    const osc = ctx.createOscillator();
                    const gain = ctx.createGain();
                    osc.type = 'sine';
                    osc.frequency.setValueAtTime(587.33, now);
                    gain.gain.setValueAtTime(0.15, now);
                    gain.gain.exponentialRampToValueAtTime(0.001, now + 0.35);
                    osc.connect(gain);
                    gain.connect(ctx.destination);
                    osc.start(now);
                    osc.stop(now + 0.35);
                }
            } catch(e) {
                console.warn('Audio chime warning:', e);
            }

            // Text-to-speech natural voice announcement
            if ('speechSynthesis' in window) {
                try {
                    let spokenText = '';
                    if (soundType === 'MASUK_TEPAT') {
                        spokenText = `Presensi masuk ${dol}{nama} berhasil dicatat tepat waktu. Semangat beraktivitas!`;
                    } else if (soundType === 'MASUK_TERLAMBAT') {
                        spokenText = `Perhatian! Presensi masuk ${dol}{nama} tercatat terlambat.`;
                    } else if (soundType === 'PULANG_TEPAT') {
                        spokenText = `Presensi pulang ${dol}{nama} berhasil dicatat. Terima kasih dan selamat beristirahat.`;
                    } else if (soundType === 'PULANG_AWAL') {
                        spokenText = `Perhatian! Presensi pulang ${dol}{nama} tercatat mendahului batas waktu pulang lebih awal.`;
                    } else if (soundType === 'IZIN_SAKIT') {
                        spokenText = `Data kehadiran ${dol}{nama} berhasil dicatat.`;
                    } else {
                        spokenText = `Presensi ${dol}{nama} berhasil disimpan.`;
                    }

                    window.speechSynthesis.cancel();
                    const utter = new SpeechSynthesisUtterance(spokenText);
                    utter.lang = 'id-ID';
                    utter.rate = 1.0;
                    utter.pitch = 1.0;
                    setTimeout(() => {
                        window.speechSynthesis.speak(utter);
                    }, 250);
                } catch(e) {
                    console.warn('TTS voice warning:', e);
                }
            }
        }

        function playSuccessBeep() {
            playAbsensiSoundWeb(null, false, 'Anggota');
        }

        window.lastPresensiDataHash = '';
        window.lastAnggotaDataHash = '';
        window.companyAutoSyncTimer = null;
        window.isCompanySyncInProgress = false;

        async function fetchAnggotaData() {
            try {
                const res = await fetch('/api/anggota');
                if (res.ok) {
                    window.anggotaList = await res.json();
                    window.lastAnggotaDataHash = JSON.stringify(window.anggotaList);
                }
            } catch(e) {
                console.error('Failed to fetch anggota:', e);
            }
        }

        async function fetchPresensiData() {
            try {
                const res = await fetch('/api/presensi');
                if (res.ok) {
                    window.presensiList = await res.json();
                    window.lastPresensiDataHash = JSON.stringify(window.presensiList);
                }
            } catch(e) {
                console.error('Failed to fetch presensi:', e);
            }
        }

        function startCompanyAutoSync() {
            if (window.companyAutoSyncTimer) return;
            window.companyAutoSyncTimer = setInterval(async () => {
                if (window.isCompanySyncInProgress) return;
                window.isCompanySyncInProgress = true;
                try {
                    if (window.anggotaActiveTab === 'presensi') {
                        const res = await fetch('/api/presensi');
                        if (res.ok) {
                            const data = await res.json();
                            const newHash = JSON.stringify(data);
                            if (newHash !== window.lastPresensiDataHash) {
                                window.lastPresensiDataHash = newHash;
                                window.presensiList = data;
                                updatePresensiLiveUi();
                            }
                        }
                    } else if (window.anggotaActiveTab === 'anggota') {
                        const res = await fetch('/api/anggota');
                        if (res.ok) {
                            const data = await res.json();
                            const newHash = JSON.stringify(data);
                            if (newHash !== window.lastAnggotaDataHash) {
                                window.lastAnggotaDataHash = newHash;
                                window.anggotaList = data;
                                const container = document.getElementById('anggotaCardsContainer');
                                if (container) {
                                    renderKaryawanMuridMainView();
                                }
                            }
                        }
                    }
                } catch(e) {
                    // background polling safe catch
                } finally {
                    window.isCompanySyncInProgress = false;
                }
            }, 1500);
        }

        // --- MAIN ENTRY LOADER ---
        async function loadKaryawanMurid() {
            const container = document.getElementById('tab-content');
            if (!container) return;

            container.innerHTML = `
                <div class="d-flex justify-content-center align-items-center py-5">
                    <div class="spinner-border text-teal" style="color: #0F766E;" role="status">
                        <span class="visually-hidden">Memuat...</span>
                    </div>
                    <span class="ms-3 fw-bold" style="color: #0F766E;">Memuat Data Company...</span>
                </div>
            `;

            await Promise.all([fetchAnggotaData(), fetchPresensiData()]);
            renderKaryawanMuridMainView();
            startCompanyAutoSync();
        }

        function setAnggotaActiveTab(tabName) {
            window.anggotaActiveTab = tabName;
            if (tabName !== 'presensi' && window.isWebcamScanning) {
                stopWebcamScanner();
            }
            renderKaryawanMuridMainView();
            startCompanyAutoSync();
        }

        function renderKaryawanMuridMainView() {
            const container = document.getElementById('tab-content');
            if (!container) return;

            const total = window.anggotaList.length;
            const karyawanCount = window.anggotaList.filter(a => (a.status || '').toLowerCase().includes('karyawan')).length;
            const muridCount = window.anggotaList.filter(a => (a.status || '').toLowerCase().includes('murid') || (a.status || '').toLowerCase().includes('siswa')).length;
            const guruCount = window.anggotaList.filter(a => (a.status || '').toLowerCase().includes('guru') || (a.status || '').toLowerCase().includes('dosen') || (a.status || '').toLowerCase().includes('staf')).length;

            let html = '<div class="container-fluid px-0">';

            if (window.anggotaActiveTab === 'presensi') {
                // --- VIEW SUBMENU ABSENSI (Tanpa kartu statistik atas & tanpa kartu tab navigasi) ---
                html += `
                    <!-- HEADER SECTION ABSENSI -->
                    <div class="d-flex flex-wrap justify-content-between align-items-center gap-3 mb-4">
                        <div>
                            <div class="d-flex align-items-center gap-2">
                                <span class="p-2 rounded-3 d-inline-flex align-items-center justify-content-center" style="background: rgba(15, 118, 110, 0.15); color: #0F766E;">
                                    <i class="bi bi-calendar-check-fill fs-3"></i>
                                </span>
                                <div>
                                    <div class="d-flex align-items-center gap-2">
                                        <h4 class="fw-bold mb-0 text-main">Absensi &amp; Presensi</h4>
                                        <span class="badge bg-success bg-opacity-10 text-success border border-success border-opacity-25 rounded-pill px-3 py-1 small d-inline-flex align-items-center gap-1 shadow-xs" title="Sinkronisasi Otomatis Real-Time Aktif dengan Aplikasi My Singghara">
                                            <span class="spinner-grow spinner-grow-sm text-success" style="width: 7px; height: 7px;" role="status"></span>
                                            <span style="font-size: 11px; font-weight: 600;">Auto-Sync Aktif</span>
                                        </span>
                                    </div>
                                    <p class="text-sub small mb-0">Pemindai Scanner Presensi QR Code, Input Cepat &amp; Rekapitulasi Riwayat Kehadiran</p>
                                </div>
                            </div>
                        </div>
                        <div class="d-flex align-items-center gap-2">
                            <button class="btn btn-outline-secondary btn-sm rounded-pill px-3" onclick="loadKaryawanMurid()" title="Muat Ulang Data">
                                <i class="bi bi-arrow-clockwise me-1"></i> Refresh
                            </button>
                            <button class="btn btn-sm text-white rounded-pill px-3 shadow-sm" style="background: linear-gradient(135deg, #0F766E, #14B8A6);" onclick="openBatasWaktuModal()">
                                <i class="bi bi-clock-history me-1"></i> Batas Jam Kerja
                            </button>
                        </div>
                    </div>

                    <!-- TAB CONTENT CONTAINER -->
                    <div id="anggotaTabDynamicContent">
                        ${dol}{renderPresensiSubTabHtml()}
                    </div>
                `;
            } else if (window.anggotaActiveTab === 'batch_print') {
                // --- VIEW CETAK MASSAL ID CARD ---
                html += `
                    <!-- HEADER SECTION BATCH PRINT -->
                    <div class="d-flex flex-wrap justify-content-between align-items-center gap-3 mb-4">
                        <div>
                            <div class="d-flex align-items-center gap-2">
                                <span class="p-2 rounded-3 d-inline-flex align-items-center justify-content-center" style="background: rgba(15, 118, 110, 0.15); color: #0F766E;">
                                    <i class="bi bi-printer-fill fs-3"></i>
                                </span>
                                <div>
                                    <h4 class="fw-bold mb-0 text-main">Cetak Massal ID Card (A4 Sheet)</h4>
                                    <p class="text-sub small mb-0">Tata letak cetak lembaran ID Card format A4 siap dipotong &amp; dilaminasi</p>
                                </div>
                            </div>
                        </div>
                        <div class="d-flex align-items-center gap-2">
                            <button class="btn btn-outline-secondary btn-sm rounded-pill px-3" onclick="setAnggotaActiveTab('anggota')">
                                <i class="bi bi-arrow-left me-1"></i> Kembali ke Data Anggota
                            </button>
                        </div>
                    </div>

                    <!-- TAB CONTENT CONTAINER -->
                    <div id="anggotaTabDynamicContent">
                        ${dol}{renderBatchPrintSubTabHtml()}
                    </div>
                `;
            } else {
                // --- VIEW SUBMENU DATA ANGGOTA (Tanpa kartu tab navigasi) ---
                html += `
                    <!-- HEADER SECTION DATA ANGGOTA -->
                    <div class="d-flex flex-wrap justify-content-between align-items-center gap-3 mb-4">
                        <div>
                            <div class="d-flex align-items-center gap-2">
                                <span class="p-2 rounded-3 d-inline-flex align-items-center justify-content-center" style="background: rgba(15, 118, 110, 0.15); color: #0F766E;">
                                    <i class="bi bi-person-badge-fill fs-3"></i>
                                </span>
                                <div>
                                    <h4 class="fw-bold mb-0 text-main">Data Anggota &amp; ID Card</h4>
                                    <p class="text-sub small mb-0">Manajemen Anggota, Generator ID Card Digital &amp; Cetak Massal</p>
                                </div>
                            </div>
                        </div>
                        <div class="d-flex align-items-center gap-2">
                            <button class="btn btn-outline-secondary btn-sm rounded-pill px-3" onclick="loadKaryawanMurid()" title="Muat Ulang Data">
                                <i class="bi bi-arrow-clockwise me-1"></i> Refresh
                            </button>
                            <button class="btn btn-outline-secondary btn-sm rounded-pill px-3" onclick="setAnggotaActiveTab('batch_print')" title="Cetak Massal ID Card (A4 Sheet)">
                                <i class="bi bi-printer-fill me-1"></i> Cetak Massal (A4)
                            </button>
                            <button class="btn btn-sm text-white rounded-pill px-3 shadow-sm" style="background: linear-gradient(135deg, #0F766E, #14B8A6);" onclick="showAddAnggotaModal()">
                                <i class="bi bi-person-plus-fill me-1"></i> Tambah Anggota
                            </button>
                        </div>
                    </div>

                    <!-- SUMMARY KPI CARDS DATA -->
                    <div class="row g-3 mb-4">
                        <div class="col-6 col-md-3">
                            <div class="card card-custom p-3 border-0 h-100 shadow-sm" style="border-left: 4px solid #0F766E !important;">
                                <div class="d-flex align-items-center justify-content-between">
                                    <div>
                                        <span class="text-sub small text-uppercase fw-semibold">Total Anggota</span>
                                        <h3 class="fw-bold text-main mb-0 mt-1">${dol}{total}</h3>
                                    </div>
                                    <div class="p-3 rounded-circle" style="background: rgba(15, 118, 110, 0.12); color: #0F766E;">
                                        <i class="bi bi-people-fill fs-4"></i>
                                    </div>
                                </div>
                            </div>
                        </div>
                        <div class="col-6 col-md-3">
                            <div class="card card-custom p-3 border-0 h-100 shadow-sm" style="border-left: 4px solid #2563EB !important;">
                                <div class="d-flex align-items-center justify-content-between">
                                    <div>
                                        <span class="text-sub small text-uppercase fw-semibold">Karyawan &amp; Staf</span>
                                        <h3 class="fw-bold text-main mb-0 mt-1">${dol}{karyawanCount}</h3>
                                    </div>
                                    <div class="p-3 rounded-circle" style="background: rgba(37, 99, 235, 0.12); color: #2563EB;">
                                        <i class="bi bi-briefcase-fill fs-4"></i>
                                    </div>
                                </div>
                            </div>
                        </div>
                        <div class="col-6 col-md-3">
                            <div class="card card-custom p-3 border-0 h-100 shadow-sm" style="border-left: 4px solid #10B981 !important;">
                                <div class="d-flex align-items-center justify-content-between">
                                    <div>
                                        <span class="text-sub small text-uppercase fw-semibold">Murid / Siswa</span>
                                        <h3 class="fw-bold text-main mb-0 mt-1">${dol}{muridCount}</h3>
                                    </div>
                                    <div class="p-3 rounded-circle" style="background: rgba(16, 185, 129, 0.12); color: #10B981;">
                                        <i class="bi bi-mortarboard-fill fs-4"></i>
                                    </div>
                                </div>
                            </div>
                        </div>
                        <div class="col-6 col-md-3">
                            <div class="card card-custom p-3 border-0 h-100 shadow-sm" style="border-left: 4px solid #F59E0B !important;">
                                <div class="d-flex align-items-center justify-content-between">
                                    <div>
                                        <span class="text-sub small text-uppercase fw-semibold">Guru &amp; Pendidik</span>
                                        <h3 class="fw-bold text-main mb-0 mt-1">${dol}{guruCount}</h3>
                                    </div>
                                    <div class="p-3 rounded-circle" style="background: rgba(245, 158, 11, 0.12); color: #F59E0B;">
                                        <i class="bi bi-award-fill fs-4"></i>
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div>

                    <!-- TAB CONTENT CONTAINER -->
                    <div id="anggotaTabDynamicContent">
                        ${dol}{renderAnggotaSubTabHtml()}
                    </div>
                `;
            }

            html += `
                    </div>

                <!-- MODAL PREVIEW ID CARD (ENGINE ASLI APLIKASI MY SINGGHARA) -->
                <div class="modal fade" id="modalIdCardViewer" tabindex="-1" aria-hidden="true">
                    <div class="modal-dialog modal-dialog-centered modal-lg">
                        <div class="modal-content card-custom border-0 shadow-lg" style="border-radius: 20px; overflow: hidden;">
                            <div class="modal-header border-0 pb-0 d-flex justify-content-between align-items-center">
                                <div>
                                    <h5 class="modal-title fw-bold text-main d-flex align-items-center gap-2 mb-0">
                                        <i class="bi bi-person-badge text-teal" style="color: #0F766E;"></i> ID Card Asli My Singghara
                                    </h5>
                                    <small class="text-muted">Ditautkan langsung dari Engine Generator Aplikasi My Singghara</small>
                                </div>
                                <button type="button" class="btn-close btn-close-custom" data-bs-dismiss="modal"></button>
                            </div>
                            <div class="modal-body text-center p-4">
                                <!-- SISI KARTU TABS -->
                                <div class="d-flex justify-content-center gap-2 mb-3">
                                    <div class="btn-group btn-group-sm p-1 rounded-pill shadow-xs border border-secondary border-opacity-25" style="background: var(--input-bg);" role="group">
                                        <button id="btnSideFront" class="btn btn-sm rounded-pill px-3 fw-bold text-white shadow-xs" style="background: #0F766E;" onclick="setIdCardSide('front')">
                                            <i class="bi bi-person-bounding-box me-1"></i> Tampak Depan
                                        </button>
                                        <button id="btnSideBack" class="btn btn-sm rounded-pill px-3 fw-bold text-sub" style="background: transparent;" onclick="setIdCardSide('back')">
                                            <i class="bi bi-card-text me-1"></i> Tampak Belakang
                                        </button>
                                        <button id="btnSideBoth" class="btn btn-sm rounded-pill px-3 fw-bold text-sub" style="background: transparent;" onclick="setIdCardSide('both')">
                                            <i class="bi bi-grid-fill me-1"></i> Kedua Sisi
                                        </button>
                                    </div>
                                </div>

                                <div id="idCardRenderContainer" class="d-flex justify-content-center align-items-center mb-3" style="min-height: 480px;">
                                    <!-- Dynamic ID Card Image will be injected here -->
                                </div>

                                <div class="d-flex flex-wrap justify-content-center gap-2 mt-3 pt-2 border-top border-secondary border-opacity-25">
                                    <button class="btn btn-outline-secondary btn-sm rounded-pill px-3" onclick="toggleIdCardFlip()">
                                        <i class="bi bi-arrow-repeat me-1"></i> Balik Sisi Kartu
                                    </button>
                                    <button class="btn btn-primary btn-sm rounded-pill px-4" style="background: #0F766E; border-color: #0F766E;" onclick="printSingleIdCard()">
                                        <i class="bi bi-printer me-1"></i> Cetak Kartu
                                    </button>
                                    <button class="btn btn-outline-success btn-sm rounded-pill px-3" onclick="downloadIdCardPng()">
                                        <i class="bi bi-download me-1"></i> Download Gambar PNG
                                    </button>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>

                <!-- MODAL FORM TAMBAH / EDIT ANGGOTA -->
                <div class="modal fade" id="modalAnggotaForm" tabindex="-1" aria-hidden="true">
                    <div class="modal-dialog modal-dialog-centered modal-lg">
                        <div class="modal-content card-custom border-0 shadow-lg" style="border-radius: 20px;">
                            <div class="modal-header border-0 pb-0">
                                <h5 class="modal-title fw-bold text-main" id="anggotaFormTitle">
                                    <i class="bi bi-person-plus-fill me-2" style="color: #0F766E;"></i> Form Data Anggota
                                </h5>
                                <button type="button" class="btn-close btn-close-custom" data-bs-dismiss="modal"></button>
                            </div>
                            <div class="modal-body p-4">
                                 <form id="formAnggotaInput" onsubmit="handleSaveAnggotaSubmit(event)">
                                    <input type="hidden" id="anggotaInputId" value="0">
                                    <input type="hidden" id="anggotaInputFotoBase64" value="">
                                    <input type="hidden" id="anggotaInputTandaTanganBase64" value="">

                                    <div class="row g-3">
                                        <!-- FOTO PROFIL SECTION -->
                                        <div class="col-12 col-md-4 text-center">
                                            <div class="card card-custom p-3 border border-secondary border-opacity-25 rounded-4 mb-2 shadow-sm text-center" style="background: var(--input-bg);">
                                                <div class="position-relative mx-auto mb-2" style="width: 120px; height: 120px;">
                                                    <div id="anggotaFotoPreview" class="w-100 h-100 rounded-circle d-flex align-items-center justify-content-center overflow-hidden border border-2 shadow-sm" style="border-color: #0F766E !important; background: var(--card-bg);">
                                                        <i class="bi bi-person-bounding-box text-secondary" style="font-size: 3.5rem;"></i>
                                                    </div>
                                                </div>
                                                <div class="d-flex flex-column gap-1">
                                                    <label class="btn btn-sm text-white rounded-pill w-100 shadow-xs" style="background: #0F766E;">
                                                        <i class="bi bi-upload me-1"></i> Pilih / Impor Foto
                                                        <input type="file" id="anggotaFileInput" accept="image/*" class="d-none" onchange="handleAnggotaFotoUpload(event)">
                                                    </label>
                                                    <button type="button" id="btnAdjustPhoto" class="btn btn-sm btn-outline-primary rounded-pill w-100 d-none" onclick="openPhotoCropModal()">
                                                        <i class="bi bi-crop me-1"></i> Sesuaikan Posisi
                                                    </button>
                                                    <button type="button" class="btn btn-sm btn-outline-danger rounded-pill w-100" onclick="removeAnggotaFoto()">
                                                        <i class="bi bi-trash me-1"></i> Hapus Foto
                                                    </button>
                                                </div>
                                            </div>
                                            <small class="text-sub" style="font-size: 11px;">Bisa geser, zoom &amp; putar untuk menyesuaikan posisi foto lingkaran.</small>
                                        </div>

                                        <!-- FORM FIELDS -->
                                        <div class="col-12 col-md-8">
                                            <div class="row g-2">
                                                <div class="col-md-6">
                                                    <label class="form-label small fw-bold text-main">Status / Peran <span class="text-danger">*</span></label>
                                                    <select id="anggotaInputStatus" class="form-select rounded-3" onchange="handleRoleChangeAutoNip()">
                                                        <option value="Karyawan">Karyawan</option>
                                                        <option value="Murid">Murid / Siswa</option>
                                                        <option value="Guru / Pendidik">Guru / Pendidik</option>
                                                        <option value="Dosen">Dosen</option>
                                                        <option value="Staf Administrasi">Staf Administrasi</option>
                                                        <option value="Magang / PKL">Magang / PKL</option>
                                                        <option value="Mitra">Mitra / Anggota Luar</option>
                                                    </select>
                                                </div>
                                                <div class="col-md-6">
                                                    <label class="form-label small fw-bold text-main">NIM / NIP / NISN <span class="text-danger">*</span></label>
                                                    <div class="input-group">
                                                        <input type="text" id="anggotaInputNomorInduk" class="form-control rounded-start-3" placeholder="Contoh: NIP-2026-001" required>
                                                        <button type="button" class="btn btn-outline-secondary btn-sm" onclick="generateAutoNomorInduk()" title="Buat Nomor Otomatis">
                                                            <i class="bi bi-magic"></i> Auto
                                                        </button>
                                                    </div>
                                                </div>

                                                <div class="col-12">
                                                    <label class="form-label small fw-bold text-main">Nama Lengkap <span class="text-danger">*</span></label>
                                                    <input type="text" id="anggotaInputNama" class="form-control rounded-3" placeholder="Masukkan nama lengkap" required>
                                                </div>

                                                <div class="col-md-6">
                                                    <label class="form-label small fw-bold text-main">Divisi / Jurusan / Kelas</label>
                                                    <input type="text" id="anggotaInputDivisi" class="form-control rounded-3" placeholder="Contoh: IT Support / XII IPA 1">
                                                </div>

                                                <div class="col-md-6">
                                                    <label class="form-label small fw-bold text-main">Nama Instansi / Lembaga</label>
                                                    <input type="text" id="anggotaInputInstansi" class="form-control rounded-3" value="My Singghara">
                                                </div>

                                                <div class="col-md-6">
                                                    <label class="form-label small fw-bold text-main">Jenis Kelamin</label>
                                                    <select id="anggotaInputGender" class="form-select rounded-3">
                                                        <option value="Laki-laki">Laki-laki</option>
                                                        <option value="Perempuan">Perempuan</option>
                                                    </select>
                                                </div>

                                                <div class="col-md-6">
                                                    <label class="form-label small fw-bold text-main">Masa Berlaku ID Card</label>
                                                    <div class="input-group">
                                                        <input type="text" id="anggotaInputBerlaku" class="form-control rounded-start-3" value="Berlaku Selama Aktif" readonly style="cursor: pointer; background-color: var(--input-bg);" onclick="openAnggotaDatePickerModal()">
                                                        <button class="btn btn-outline-secondary rounded-end-3 px-3 d-flex align-items-center justify-content-center" type="button" onclick="openAnggotaDatePickerModal()" title="Pilih Tanggal Masa Berlaku" id="btnAnggotaCalendarPicker" style="border-color: var(--border-color);">
                                                            <i class="bi bi-calendar-event-fill" style="color: #0F766E; font-size: 1.1rem;"></i>
                                                        </button>
                                                    </div>
                                                </div>

                                                <div class="col-md-6">
                                                    <label class="form-label small fw-bold text-main">Nomor HP / WhatsApp</label>
                                                    <input type="text" id="anggotaInputKontak" class="form-control rounded-3" placeholder="08xxxxxxxxxx">
                                                </div>

                                                <div class="col-md-6">
                                                    <label class="form-label small fw-bold text-main">Email</label>
                                                    <input type="email" id="anggotaInputEmail" class="form-control rounded-3" placeholder="nama@email.com">
                                                </div>

                                                <div class="col-12">
                                                    <label class="form-label small fw-bold text-main">Alamat Domisili</label>
                                                    <input type="text" id="anggotaInputAlamat" class="form-control rounded-3" placeholder="Alamat singkat">
                                                </div>
                                            </div>
                                        </div>

                                        <!-- TANDA TANGAN DIGITAL SECTION -->
                                        <div class="col-12">
                                            <div class="card card-custom p-3 border-0 bg-light rounded-4 shadow-sm">
                                                <div class="d-flex justify-content-between align-items-center mb-2">
                                                    <div>
                                                        <span class="fw-bold small text-main d-flex align-items-center gap-1">
                                                            <i class="bi bi-pen-fill" style="color: #0F766E;"></i> Tanda Tangan Digital Anggota
                                                        </span>
                                                        <small class="text-sub" style="font-size: 11px;">Dicetak otomatis pada Tampak Belakang ID Card (Sebelah Kanan)</small>
                                                    </div>
                                                    <div class="d-flex gap-2">
                                                        <label class="btn btn-sm btn-outline-primary rounded-pill px-3 py-1" style="font-size: 11px; cursor: pointer;">
                                                            <i class="bi bi-upload me-1"></i> Unggah TTD (PNG)
                                                            <input type="file" id="anggotaSigFileInput" accept="image/*" class="d-none" onchange="handleAnggotaSigUpload(event)">
                                                        </label>
                                                        <button type="button" class="btn btn-sm btn-outline-danger rounded-pill px-3 py-1" style="font-size: 11px;" onclick="clearAnggotaSignature()">
                                                            <i class="bi bi-eraser me-1"></i> Hapus
                                                        </button>
                                                    </div>
                                                </div>
                                                <div class="position-relative rounded-3 border overflow-hidden shadow-xs" style="height: 130px; border-color: var(--border-color) !important; background: var(--card-bg);">
                                                    <canvas id="anggotaSigCanvas" class="w-100 h-100" style="touch-action: none; cursor: crosshair; display: block;"></canvas>
                                                    <div id="anggotaSigPlaceholder" class="position-absolute top-50 start-50 translate-middle text-muted text-center pointer-events-none" style="pointer-events: none; font-size: 12px; width: 90%;">
                                                        <i class="bi bi-pencil-square text-secondary d-block mb-1" style="font-size: 1.3rem;"></i>
                                                        Goreskan tanda tangan di sini menggunakan mouse atau layar sentuh
                                                    </div>
                                                </div>
                                            </div>
                                        </div>
                                    </div>

                                    <div class="d-flex justify-content-end gap-2 mt-4 pt-3 border-top border-secondary border-opacity-25">
                                        <button type="button" class="btn btn-outline-secondary rounded-pill px-4" data-bs-dismiss="modal">Batal</button>
                                        <button type="submit" class="btn text-white rounded-pill px-4 shadow-sm" style="background: #0F766E;">
                                            <i class="bi bi-save me-1"></i> Simpan Anggota
                                        </button>
                                    </div>
                                </form>
                            </div>
                        </div>
                    </div>
                </div>

                <!-- MODAL CROP & SESUAIKAN POSISI FOTO PROFIL (CIRCULAR) -->
                <div class="modal fade" id="modalCropFotoWeb" tabindex="-1" aria-hidden="true" data-bs-backdrop="static">
                    <div class="modal-dialog modal-dialog-centered modal-md">
                        <div class="modal-content card-custom border-0 shadow-lg" style="border-radius: 24px; overflow: hidden;">
                            <div class="modal-header border-0 pb-0 d-flex justify-content-between align-items-center">
                                <h5 class="modal-title fw-bold text-main d-flex align-items-center gap-2" style="font-size: 16px;">
                                    <i class="bi bi-crop text-teal" style="color: #0F766E;"></i> Sesuaikan Posisi Foto Lingkaran
                                </h5>
                                <button type="button" class="btn-close btn-close-custom" data-bs-dismiss="modal"></button>
                            </div>
                            <div class="modal-body p-4 text-center">
                                <p class="text-muted small mb-3">Geser foto dengan mouse atau jari, atur pembesaran (zoom) dan rotasi agar pas di dalam lingkaran ID card.</p>
                                
                                <!-- CANVAS CONTAINER -->
                                <div class="position-relative mx-auto border rounded-4 overflow-hidden shadow-sm user-select-none" style="width: 300px; height: 300px; background: #0f172a; cursor: grab;" id="cropCanvasWrapper">
                                    <canvas id="cropCanvas" width="300" height="300" style="width: 300px; height: 300px; display: block;"></canvas>
                                </div>

                                <!-- CONTROLS -->
                                <div class="mt-3 px-2">
                                    <div class="d-flex align-items-center justify-content-between gap-3 mb-2">
                                        <button type="button" class="btn btn-sm btn-outline-secondary rounded-circle p-0 d-flex align-items-center justify-content-center" style="width: 32px; height: 32px;" onclick="stepCropZoom(-0.15)">
                                            <i class="bi bi-dash-lg"></i>
                                        </button>
                                        <input type="range" class="form-range flex-grow-1" id="cropZoomSlider" min="0.4" max="3" step="0.05" value="1" oninput="handleCropZoomChange(this.value)">
                                        <button type="button" class="btn btn-sm btn-outline-secondary rounded-circle p-0 d-flex align-items-center justify-content-center" style="width: 32px; height: 32px;" onclick="stepCropZoom(0.15)">
                                            <i class="bi bi-plus-lg"></i>
                                        </button>
                                    </div>
                                    <div class="d-flex justify-content-center gap-2">
                                        <button type="button" class="btn btn-outline-secondary btn-sm rounded-pill px-3" onclick="rotateCropImage()">
                                            <i class="bi bi-arrow-clockwise me-1"></i> Putar 90°
                                        </button>
                                        <button type="button" class="btn btn-outline-secondary btn-sm rounded-pill px-3" onclick="resetCropTransform()">
                                            <i class="bi bi-arrow-counterclockwise me-1"></i> Pusatkan
                                        </button>
                                    </div>
                                </div>
                            </div>
                            <div class="modal-footer border-0 pt-0 d-flex justify-content-end gap-2 px-4 py-3" style="background: var(--modal-bg);">
                                <button type="button" class="btn btn-outline-secondary rounded-pill px-4" data-bs-dismiss="modal">Batal</button>
                                <button type="button" class="btn text-white rounded-pill px-4 shadow-sm" style="background: #0F766E;" onclick="applyCroppedPhoto()">
                                    <i class="bi bi-check-lg me-1"></i> Terapkan Foto
                                </button>
                            </div>
                        </div>
                    </div>
                </div>
            `;

            container.innerHTML = html;
            initSubTabListeners();
        }

        function initSubTabListeners() {
            if (window.anggotaActiveTab === 'presensi') {
                initPresensiSearchFocus();
            }
        }

        // ====================================================
        // --- 1. SUB-TAB: DAFTAR & KARTU ANGGOTA ---
        // ====================================================
        function renderAnggotaSubTabHtml() {
            const query = (window.anggotaSearchQuery || '').toLowerCase().trim();
            const statusFilter = window.anggotaFilterStatus || 'Semua';

            let filtered = window.anggotaList.filter(a => {
                const matchQuery = !query || 
                    (a.nama || '').toLowerCase().includes(query) ||
                    (a.nomorInduk || '').toLowerCase().includes(query) ||
                    (a.divisiOrJurusan || '').toLowerCase().includes(query) ||
                    (a.instansi || '').toLowerCase().includes(query);

                const matchStatus = statusFilter === 'Semua' || (a.status || '').toLowerCase() === statusFilter.toLowerCase();
                return matchQuery && matchStatus;
            });

            const statuses = ['Semua', 'Karyawan', 'Murid', 'Guru / Pendidik', 'Dosen', 'Staf Administrasi', 'Magang / PKL'];

            let html = `
                <!-- FILTER & CONTROLS TOOLBAR -->
                <div class="card card-custom border-0 shadow-sm p-3 mb-4">
                    <div class="row g-3 align-items-center">
                        <div class="col-12 col-md-6 col-lg-5">
                            <div class="input-group">
                                <span class="input-group-text bg-transparent border-end-0 text-muted"><i class="bi bi-search"></i></span>
                                <input type="text" class="form-control border-start-0 ps-0" placeholder="Cari nama, NIP/NIM, divisi..." value="${dol}{escapeHtml(window.anggotaSearchQuery)}" oninput="handleAnggotaSearch(this.value)">
                                ${dol}{window.anggotaSearchQuery ? `<button class="btn btn-outline-secondary" onclick="handleAnggotaSearch('')"><i class="bi bi-x"></i></button>` : ''}
                            </div>
                        </div>
                        <div class="col-12 col-md-6 col-lg-7 d-flex justify-content-md-end align-items-center gap-2 flex-wrap">
                            <!-- MENU LIST PADA ICON FILTER -->
                            <div class="dropdown">
                                <button class="btn btn-outline-secondary btn-sm rounded-pill px-3 py-2 dropdown-toggle d-flex align-items-center gap-2 shadow-xs" type="button" id="filterStatusDropdownBtn" data-bs-toggle="dropdown" aria-expanded="false" style="font-size: 13px;">
                                    <i class="bi bi-funnel-fill text-teal" style="color: #0F766E;"></i>
                                    <span>Filter: <strong style="color: #0F766E;">${dol}{escapeHtml(statusFilter)}</strong></span>
                                </button>
                                <ul class="dropdown-menu dropdown-menu-end shadow border-0 rounded-4 p-2" aria-labelledby="filterStatusDropdownBtn" style="min-width: 220px; z-index: 1050;">
                                    <li class="dropdown-header text-uppercase small fw-bold text-muted px-3 py-2">
                                        <i class="bi bi-filter me-1"></i> Filter Berdasarkan Peran
                                    </li>
                                    <li><hr class="dropdown-divider my-1 opacity-25"></li>
                                    ${dol}{statuses.map(st => `
                                        <li>
                                            <a class="dropdown-item rounded-3 py-2 px-3 d-flex align-items-center justify-content-between mb-1 ${dol}{statusFilter === st ? 'active' : ''}" 
                                               href="javascript:void(0)"
                                               style="${dol}{statusFilter === st ? 'background: #0F766E; color: #fff; font-weight: bold;' : 'color: var(--text-main);'}"
                                               onclick="filterAnggotaStatus('${dol}{st}')">
                                                <span>${dol}{st}</span>
                                                ${dol}{statusFilter === st ? '<i class="bi bi-check2-circle fs-6"></i>' : ''}
                                            </a>
                                        </li>
                                    `).join('')}
                                </ul>
                            </div>

                            <!-- VIEW MODE SWITCHER (GRID / TABLE) -->
                            <div class="btn-group shadow-xs" role="group">
                                <button type="button" class="btn btn-sm py-2 px-3 ${dol}{window.anggotaViewMode === 'grid' ? 'btn-dark' : 'btn-outline-secondary'}" onclick="setAnggotaViewMode('grid')" title="Tampilan Grid Kartu">
                                    <i class="bi bi-grid-fill"></i>
                                </button>
                                <button type="button" class="btn btn-sm py-2 px-3 ${dol}{window.anggotaViewMode === 'table' ? 'btn-dark' : 'btn-outline-secondary'}" onclick="setAnggotaViewMode('table')" title="Tampilan Tabel">
                                    <i class="bi bi-table"></i>
                                </button>
                            </div>
                        </div>
                    </div>
                </div>
            `;

            if (filtered.length === 0) {
                html += `
                    <div class="card card-custom border-0 shadow-sm py-5 text-center">
                        <div class="py-4">
                            <i class="bi bi-person-x text-muted" style="font-size: 4rem;"></i>
                            <h5 class="fw-bold text-main mt-3">Tidak Ada Data Anggota Ditemukan</h5>
                            <p class="text-sub small">Silakan sesuaikan kata kunci pencarian atau tambahkan data anggota baru.</p>
                            <button class="btn text-white rounded-pill px-4 shadow-sm mt-2" style="background: #0F766E;" onclick="showAddAnggotaModal()">
                                <i class="bi bi-plus-lg me-1"></i> Tambah Anggota Pertama
                            </button>
                        </div>
                    </div>
                `;
                return html;
            }

            if (window.anggotaViewMode === 'grid') {
                html += `
                    <div class="row g-3">
                        ${dol}{filtered.map(item => renderAnggotaGridCard(item)).join('')}
                    </div>
                `;
            } else {
                html += `
                    <div class="card card-custom border-0 shadow-sm overflow-hidden">
                        <div class="table-responsive">
                            <table class="table table-hover align-middle mb-0">
                                <thead style="background: var(--table-th-bg); color: var(--text-main);">
                                    <tr>
                                        <th class="ps-3" style="width: 50px;">No</th>
                                        <th style="width: 70px;">Foto</th>
                                        <th>Nomor Induk</th>
                                        <th>Nama Lengkap</th>
                                        <th>Peran / Status</th>
                                        <th>Divisi / Jurusan</th>
                                        <th>Kontak / Email</th>
                                        <th>Masa Berlaku</th>
                                        <th class="text-end pe-3">Aksi</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    ${dol}{filtered.map((item, idx) => renderAnggotaTableRow(item, idx + 1)).join('')}
                                </tbody>
                            </table>
                        </div>
                    </div>
                `;
            }

            return html;
        }

        function renderAnggotaGridCard(item) {
            const badgeColor = getStatusBadgeColor(item.status);
            const theme = getIdCardTheme(item.status);
            const initial = (item.nama || 'A').charAt(0).toUpperCase();
            const hasPhoto = item.fotoUri && item.fotoUri.trim().length > 0;

            return `
                <div class="col-12 col-sm-6 col-lg-4 col-xl-3">
                    <div class="card card-custom border-0 shadow-sm h-100 overflow-hidden" style="border-radius: 16px; transition: transform 0.2s, box-shadow 0.2s;">
                        <!-- TOP COLOR ACCENT MATCHING ROLE -->
                        <div class="py-2 px-3 d-flex justify-content-between align-items-center" style="background: linear-gradient(135deg, ${dol}{theme.primaryDark}, ${dol}{theme.bottomBar}); color: #fff;">
                            <span class="badge rounded-pill px-2 py-1 small fw-bold" style="font-size: 10px; background: rgba(255,255,255,0.25); color: #fff;">${dol}{escapeHtml(item.instansi || 'My Singghara')}</span>
                            <span class="small fw-semibold text-white-50" style="font-size: 11px;">${dol}{escapeHtml(theme.roleLabel)}</span>
                        </div>

                        <div class="card-body p-3 text-center">
                            <!-- AVATAR / FOTO CIRCULAR WITH ACCENT RING -->
                            <div class="position-relative mx-auto mb-2" style="width: 84px; height: 84px;">
                                <div class="w-100 h-100 rounded-circle overflow-hidden shadow-sm" style="border: 3px solid ${dol}{theme.accent}; padding: 2px; background: var(--card-bg);">
                                    ${dol}{hasPhoto ? `
                                        <img src="${dol}{escapeHtml(item.fotoUri)}" class="w-100 h-100 rounded-circle object-fit-cover" alt="${dol}{escapeHtml(item.nama)}">
                                    ` : `
                                        <div class="w-100 h-100 rounded-circle d-flex align-items-center justify-content-center text-white fw-bold" style="background: ${dol}{theme.primaryDark}; font-size: 2rem;">
                                            ${dol}{initial}
                                        </div>
                                    `}
                                </div>
                            </div>

                            <h6 class="fw-bold text-main mb-1 text-truncate" title="${dol}{escapeHtml(item.nama)}">${dol}{escapeHtml(item.nama)}</h6>
                            <div class="d-flex justify-content-center gap-1 mb-2">
                                <span class="badge rounded-pill px-2 py-1 small fw-bold" style="background: ${dol}{badgeColor.bg}; color: ${dol}{badgeColor.text};">
                                    ${dol}{escapeHtml(item.status || 'Anggota')}
                                </span>
                            </div>

                            <div class="p-2 rounded-3 text-start small mb-3 border border-secondary border-opacity-25" style="background: var(--input-bg);">
                                <div class="d-flex justify-content-between text-sub" style="font-size: 11px;">
                                    <span>NIM/NIP:</span>
                                    <span class="fw-bold text-main font-monospace">${dol}{escapeHtml(item.nomorInduk || '-')}</span>
                                </div>
                                <div class="d-flex justify-content-between text-sub mt-1" style="font-size: 11px;">
                                    <span>Divisi:</span>
                                    <span class="fw-semibold text-main text-truncate ms-2" style="max-width: 120px;">${dol}{escapeHtml(item.divisiOrJurusan || '-')}</span>
                                </div>
                            </div>

                            <div class="d-grid gap-1">
                                <button class="btn btn-sm text-white rounded-pill py-1 fw-bold shadow-xs" style="background: ${dol}{theme.primaryDark}; font-size: 12px;" onclick="openIdCardModal('${dol}{item.id}')">
                                    <i class="bi bi-person-badge me-1"></i> Buka ID Card
                                </button>
                                <div class="btn-group w-100">
                                    <button class="btn btn-outline-secondary btn-sm py-1 rounded-start-pill" style="font-size: 11px;" onclick="showEditAnggotaModal(${dol}{item.id})">
                                        <i class="bi bi-pencil me-1"></i> Edit
                                    </button>
                                    <button class="btn btn-outline-danger btn-sm py-1 rounded-end-pill" style="font-size: 11px;" onclick="deleteAnggotaItem(${dol}{item.id}, '${dol}{escapeJs(item.nama)}')">
                                        <i class="bi bi-trash me-1"></i> Hapus
                                    </button>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            `;
        }

        function renderAnggotaTableRow(item, num) {
            const badgeColor = getStatusBadgeColor(item.status);
            const initial = (item.nama || 'A').charAt(0).toUpperCase();
            const hasPhoto = item.fotoUri && item.fotoUri.trim().length > 0;

            return `
                <tr>
                    <td class="ps-3 text-muted">${dol}{num}</td>
                    <td>
                        <div style="width: 44px; height: 44px; background: var(--card-bg);" class="rounded-circle overflow-hidden border shadow-sm p-05">
                            ${dol}{hasPhoto ? `
                                <img src="${dol}{escapeHtml(item.fotoUri)}" class="w-100 h-100 rounded-circle object-fit-cover" alt="Foto">
                            ` : `
                                <div class="w-100 h-100 rounded-circle d-flex align-items-center justify-content-center text-white fw-bold small" style="background: #0F766E;">
                                    ${dol}{initial}
                                </div>
                            `}
                        </div>
                    </td>
                    <td><span class="font-monospace fw-bold text-main">${dol}{escapeHtml(item.nomorInduk || '-')}</span></td>
                    <td>
                        <div class="fw-bold text-main" style="cursor: pointer; color: #0F766E;" onclick="openIdCardModal(${dol}{item.id})" title="Klik untuk Lihat ID Card">
                            ${dol}{escapeHtml(item.nama)} <i class="bi bi-person-badge ms-1 small text-teal"></i>
                        </div>
                        <small class="text-muted">${dol}{escapeHtml(item.instansi || 'My Singghara')}</small>
                    </td>
                    <td>
                        <span class="badge rounded-pill px-2 py-1" style="background: ${dol}{badgeColor.bg}; color: ${dol}{badgeColor.text};">
                            ${dol}{escapeHtml(item.status || 'Anggota')}
                        </span>
                    </td>
                    <td><span class="text-main">${dol}{escapeHtml(item.divisiOrJurusan || '-')}</span></td>
                    <td>
                        <div class="small text-main">${dol}{escapeHtml(item.kontak || item.emailOrTelp || '-')}</div>
                        ${dol}{item.email ? `<small class="text-muted">${dol}{escapeHtml(item.email)}</small>` : ''}
                    </td>
                    <td><span class="small text-muted">${dol}{escapeHtml(item.berlakuHingga || 'Aktif')}</span></td>
                    <td class="text-end pe-3">
                        <div class="btn-group btn-group-sm">
                            <button class="btn btn-outline-primary" onclick="openIdCardModal(${dol}{item.id})" title="Lihat Kartu">
                                <i class="bi bi-person-badge"></i>
                            </button>
                            <button class="btn btn-outline-secondary" onclick="showEditAnggotaModal(${dol}{item.id})" title="Edit Data">
                                <i class="bi bi-pencil"></i>
                            </button>
                            <button class="btn btn-outline-danger" onclick="deleteAnggotaItem(${dol}{item.id}, '${dol}{escapeJs(item.nama)}')" title="Hapus">
                                <i class="bi bi-trash"></i>
                            </button>
                        </div>
                    </td>
                </tr>
            `;
        }

        function getStatusBadgeColor(status) {
            const s = (status || '').toLowerCase();
            if (s.includes('murid') || s.includes('siswa') || s.includes('santri') || s.includes('pelajar')) return { bg: 'rgba(56, 189, 248, 0.15)', text: '#0284C7' };
            if (s.includes('guru') || s.includes('dosen') || s.includes('pendidik') || s.includes('pengajar')) return { bg: 'rgba(16, 185, 129, 0.15)', text: '#059669' };
            if (s.includes('staf') || s.includes('staff') || s.includes('admin')) return { bg: 'rgba(244, 63, 94, 0.15)', text: '#E11D48' };
            if (s.includes('magang') || s.includes('pkl') || s.includes('intern')) return { bg: 'rgba(192, 132, 252, 0.15)', text: '#9333EA' };
            if (s.includes('tamu') || s.includes('mitra') || s.includes('guest')) return { bg: 'rgba(148, 163, 184, 0.15)', text: '#475569' };
            return { bg: 'rgba(245, 158, 11, 0.15)', text: '#D97706' };
        }

        function getIdCardTheme(status) {
            const s = (status || '').toLowerCase();
            if (s.includes('murid') || s.includes('siswa') || s.includes('santri') || s.includes('pelajar')) {
                return {
                    primaryDark: '#1E1B4B',
                    accent: '#38BDF8',
                    accentDark: '#0284C7',
                    bottomBar: '#2563EB',
                    roleLabel: 'MURID / SISWA'
                };
            }
            if (s.includes('guru') || s.includes('dosen') || s.includes('pendidik') || s.includes('pengajar') || s.includes('ustadz')) {
                return {
                    primaryDark: '#064E3B',
                    accent: '#10B981',
                    accentDark: '#059669',
                    bottomBar: '#0D9488',
                    roleLabel: 'GURU / PENDIDIK'
                };
            }
            if (s.includes('staf') || s.includes('staff') || s.includes('admin') || s.includes('keuangan') || s.includes('tata usaha')) {
                return {
                    primaryDark: '#4C0519',
                    accent: '#FB7185',
                    accentDark: '#E11D48',
                    bottomBar: '#BE123C',
                    roleLabel: 'STAF ADMINISTRASI'
                };
            }
            if (s.includes('magang') || s.includes('pkl') || s.includes('intern') || s.includes('prakerin')) {
                return {
                    primaryDark: '#3B0764',
                    accent: '#C084FC',
                    accentDark: '#9333EA',
                    bottomBar: '#7E22CE',
                    roleLabel: 'MAGANG / PKL'
                };
            }
            if (s.includes('tamu') || s.includes('mitra') || s.includes('guest') || s.includes('alumni')) {
                return {
                    primaryDark: '#1E293B',
                    accent: '#94A3B8',
                    accentDark: '#64748B',
                    bottomBar: '#475569',
                    roleLabel: 'TAMU / MITRA'
                };
            }
            // Default: Karyawan / Pegawai / Official
            return {
                primaryDark: '#1B222E',
                accent: '#F59E0B',
                accentDark: '#D97706',
                bottomBar: '#F59E0B',
                roleLabel: (status || 'KARYAWAN').toUpperCase()
            };
        }

        function handleAnggotaSearch(val) {
            window.anggotaSearchQuery = val;
            const container = document.getElementById('anggotaTabDynamicContent');
            if (container) container.innerHTML = renderAnggotaSubTabHtml();
        }

        function filterAnggotaStatus(st) {
            window.anggotaFilterStatus = st;
            const container = document.getElementById('anggotaTabDynamicContent');
            if (container) container.innerHTML = renderAnggotaSubTabHtml();
        }

        function setAnggotaViewMode(mode) {
            window.anggotaViewMode = mode;
            const container = document.getElementById('anggotaTabDynamicContent');
            if (container) container.innerHTML = renderAnggotaSubTabHtml();
        }
        // ====================================================
        // --- 2. ID CARD DIGITAL ENGINE (ASLI MY SINGGHARA) ---
        // ====================================================
        function openIdCardModal(id) {
            window.selectedAnggotaCardId = parseInt(id) || id;
            window.cardSide = 'front';
            renderIdCardInModal();

            const modalEl = document.getElementById('modalIdCardViewer');
            if (modalEl) {
                if (typeof bootstrap !== 'undefined' && bootstrap.Modal) {
                    const modal = bootstrap.Modal.getOrCreateInstance(modalEl);
                    modal.show();
                }
            }
        }

        function setIdCardSide(side) {
            window.cardSide = side;
            renderIdCardInModal();
        }

        function toggleIdCardFlip() {
            if (window.cardSide === 'front') {
                window.cardSide = 'back';
            } else if (window.cardSide === 'back') {
                window.cardSide = 'front';
            } else {
                window.cardSide = 'front';
            }
            renderIdCardInModal();
        }

        function renderIdCardInModal() {
            const container = document.getElementById('idCardRenderContainer');
            if (!container) return;

            const item = window.anggotaList.find(a => a.id == window.selectedAnggotaCardId);
            if (!item) {
                container.innerHTML = `<div class="text-danger p-3">Data anggota tidak ditemukan.</div>`;
                return;
            }

            // Update Tab Button Styles
            const btnFront = document.getElementById('btnSideFront');
            const btnBack = document.getElementById('btnSideBack');
            const btnBoth = document.getElementById('btnSideBoth');
            if (btnFront && btnBack && btnBoth) {
                btnFront.className = 'btn btn-sm rounded-pill px-3 fw-bold ' + (window.cardSide === 'front' ? 'text-white shadow-xs' : 'text-sub');
                btnFront.style.background = window.cardSide === 'front' ? '#0F766E' : 'transparent';

                btnBack.className = 'btn btn-sm rounded-pill px-3 fw-bold ' + (window.cardSide === 'back' ? 'text-white shadow-xs' : 'text-sub');
                btnBack.style.background = window.cardSide === 'back' ? '#0F766E' : 'transparent';

                btnBoth.className = 'btn btn-sm rounded-pill px-3 fw-bold ' + (window.cardSide === 'both' ? 'text-white shadow-xs' : 'text-sub');
                btnBoth.style.background = window.cardSide === 'both' ? '#0F766E' : 'transparent';
            }

            const ts = Date.now();
            const frontUrl = '/api/idcard?id=' + item.id + '&side=front&t=' + ts;
            const backUrl = '/api/idcard?id=' + item.id + '&side=back&t=' + ts;

            if (window.cardSide === 'both') {
                container.innerHTML = `
                    <div class="d-flex flex-wrap justify-content-center gap-4 align-items-center w-100 py-2">
                        <div class="text-center">
                            <span class="badge bg-secondary bg-opacity-25 text-main rounded-pill mb-2 px-3 py-1 fw-bold">Tampak Depan</span>
                            <div class="position-relative shadow-lg rounded-4 overflow-hidden" style="width: 290px; height: 467px; background: var(--card-bg); border: 1.5px solid var(--border-color);">
                                <img src="${dol}{frontUrl}" class="w-100 h-100 object-fit-contain" alt="ID Card Depan" onerror="this.outerHTML='<div class=\\'p-4 text-muted\\'>Gagal memuat kartu depan</div>'">
                            </div>
                        </div>
                        <div class="text-center">
                            <span class="badge bg-secondary bg-opacity-25 text-main rounded-pill mb-2 px-3 py-1 fw-bold">Tampak Belakang</span>
                            <div class="position-relative shadow-lg rounded-4 overflow-hidden" style="width: 290px; height: 467px; background: var(--card-bg); border: 1.5px solid var(--border-color);">
                                <img src="${dol}{backUrl}" class="w-100 h-100 object-fit-contain" alt="ID Card Belakang" onerror="this.outerHTML='<div class=\\'p-4 text-muted\\'>Gagal memuat kartu belakang</div>'">
                            </div>
                        </div>
                    </div>
                `;
            } else {
                const isFront = window.cardSide === 'front';
                const currentUrl = isFront ? frontUrl : backUrl;
                const labelSide = isFront ? 'Tampak Depan' : 'Tampak Belakang';
                container.innerHTML = `
                    <div class="text-center py-2">
                        <div class="position-relative shadow-lg rounded-4 overflow-hidden mx-auto" style="width: 320px; height: 515px; background: var(--card-bg); border: 1.5px solid var(--border-color);">
                            <img id="mainIdCardImg" src="${dol}{currentUrl}" class="w-100 h-100 object-fit-contain" alt="${dol}{labelSide}" onerror="this.outerHTML='<div class=\\'p-4 text-muted\\'>Gagal memuat kartu dari aplikasi</div>'">
                        </div>
                        <div class="small text-sub mt-2 fw-semibold"><i class="bi bi-shield-check text-success me-1"></i> ${dol}{labelSide} • Generator Asli Aplikasi My Singghara</div>
                    </div>
                `;
            }
        }

        function printSingleIdCard() {
            const item = window.anggotaList.find(a => a.id == window.selectedAnggotaCardId);
            if (!item) return;

            const ts = Date.now();
            const frontUrl = '/api/idcard?id=' + item.id + '&side=front&t=' + ts;
            const backUrl = '/api/idcard?id=' + item.id + '&side=back&t=' + ts;

            const printWindow = window.open('', '_blank', 'width=900,height=800');
            const showBoth = window.cardSide === 'both';
            const showOnlyBack = window.cardSide === 'back';

            let cardsContent = '';
            if (showBoth) {
                cardsContent = `
                    <div class="card-print-box">
                        <img src="${dol}{frontUrl}" class="card-img" alt="Depan">
                        <div class="label">TAMPAK DEPAN</div>
                    </div>
                    <div class="card-print-box">
                        <img src="${dol}{backUrl}" class="card-img" alt="Belakang">
                        <div class="label">TAMPAK BELAKANG</div>
                    </div>
                `;
            } else if (showOnlyBack) {
                cardsContent = `
                    <div class="card-print-box">
                        <img src="${dol}{backUrl}" class="card-img" alt="Belakang">
                    </div>
                `;
            } else {
                cardsContent = `
                    <div class="card-print-box">
                        <img src="${dol}{frontUrl}" class="card-img" alt="Depan">
                    </div>
                `;
            }

            printWindow.document.write(`
                <!DOCTYPE html>
                <html>
                <head>
                    <title>Cetak ID Card - ${dol}{escapeHtml(item.nama)}</title>
                    <link href="/assets/css/bootstrap.min.css" rel="stylesheet">
                    <link rel="stylesheet" href="/assets/css/bootstrap-icons.css">
                    <style>
                        @media print {
                            body { margin: 0; padding: 15mm; background: #fff; }
                            .no-print { display: none !important; }
                            .print-container { display: flex; flex-wrap: wrap; gap: 20mm; justify-content: center; }
                        }
                        body { display: flex; flex-direction: column; align-items: center; background: #f8fafc; padding: 25px; font-family: -apple-system, BlinkMacSystemFont, sans-serif; }
                        .print-container { display: flex; flex-wrap: wrap; gap: 25px; justify-content: center; align-items: center; }
                        .card-print-box { text-align: center; break-inside: avoid; page-break-inside: avoid; }
                        .card-img { width: 54mm; height: 86mm; border-radius: 4mm; box-shadow: 0 4px 12px rgba(0,0,0,0.15); border: 1px solid #e2e8f0; object-fit: contain; }
                        .label { font-size: 11px; font-weight: bold; color: #64748b; margin-top: 6px; letter-spacing: 1px; }
                    </style>
                </head>
                <body>
                    <div class="d-flex justify-content-between align-items-center w-100 mb-4 no-print" style="max-width: 600px;">
                        <div>
                            <h6 class="fw-bold mb-0">${dol}{escapeHtml(item.nama)} (${dol}{escapeHtml(item.nomorInduk)})</h6>
                            <small class="text-muted">Siap dicetak standar kartu (CR80: 54 x 86 mm)</small>
                        </div>
                        <button class="btn btn-primary btn-sm rounded-pill px-4" onclick="window.print()">
                            <i class="bi bi-printer me-1"></i> Cetak / Simpan PDF
                        </button>
                    </div>
                    <div class="print-container">
                        ${dol}{cardsContent}
                    </div>
                </body>
                </html>
            `);
            printWindow.document.close();
            setTimeout(() => {
                try {
                    printWindow.focus();
                    printWindow.print();
                } catch(e) {}
            }, 400);
        }

        function downloadIdCardPng() {
            const item = window.anggotaList.find(a => a.id == window.selectedAnggotaCardId);
            if (!item) return;

            const sideToDownload = window.cardSide === 'both' ? 'front' : window.cardSide;
            const link = document.createElement('a');
            link.href = '/api/idcard?id=' + item.id + '&side=' + sideToDownload + '&t=' + Date.now();
            link.download = 'ID_CARD_' + (item.nomorInduk || item.id) + '_' + sideToDownload.toUpperCase() + '.png';
            link.target = '_blank';
            document.body.appendChild(link);
            link.click();
            document.body.removeChild(link);

            if (window.cardSide === 'both') {
                setTimeout(() => {
                    const linkBack = document.createElement('a');
                    linkBack.href = '/api/idcard?id=' + item.id + '&side=back&t=' + Date.now();
                    linkBack.download = 'ID_CARD_' + (item.nomorInduk || item.id) + '_BACK.png';
                    linkBack.target = '_blank';
                    document.body.appendChild(linkBack);
                    linkBack.click();
                    document.body.removeChild(linkBack);
                }, 400);
            }
        }

        // ====================================================
        // --- 3. SUB-TAB: PRESENSI QR CODE & SCANNER ---
        // ====================================================
        function getPresensiConfig() {
            return {
                batasMasuk: localStorage.getItem('singghara_batas_masuk') || '08:00',
                batasPulang: localStorage.getItem('singghara_batas_pulang') || '17:00'
            };
        }

        function savePresensiConfig(masuk, pulang) {
            localStorage.setItem('singghara_batas_masuk', masuk);
            localStorage.setItem('singghara_batas_pulang', pulang);
        }

        function parseTimeToMinutes(timeStr) {
            try {
                const parts = (timeStr || '08:00').trim().split(':');
                return (parseInt(parts[0], 10) || 0) * 60 + (parseInt(parts[1], 10) || 0);
            } catch(e) {
                return 8 * 60;
            }
        }

        function renderPresensiSubTabHtml() {
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
                                        <div class="fw-bold text-main" style="font-size: 14px;">${dol}{config.batasMasuk} WIB</div>
                                    </div>
                                </div>
                                <div class="px-3 py-2 rounded-3 d-flex align-items-center gap-2" style="background: var(--sidebar-bg); border: 1px solid var(--border-color);">
                                    <i class="bi bi-box-arrow-left text-warning fs-5"></i>
                                    <div>
                                        <div class="text-sub" style="font-size: 10px; text-transform: uppercase; font-weight: 600;">Batas Jam Pulang</div>
                                        <div class="fw-bold text-main" style="font-size: 14px;">${dol}{config.batasPulang} WIB</div>
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
                                <h4 class="fw-bold text-main mb-0 mt-1" id="presensiStatHadir">${dol}{hadirCount}</h4>
                            </div>
                        </div>
                        <!-- Tepat Waktu -->
                        <div class="col-6 col-sm-4 col-md-2">
                            <div class="card card-custom p-3 border-0 h-100 shadow-sm text-center" style="border-top: 3px solid #0F766E !important; border-radius: 14px;">
                                <div class="p-2 rounded-circle mx-auto mb-2 d-flex align-items-center justify-content-center" style="width: 38px; height: 38px; background: rgba(15, 118, 110, 0.15); color: #0F766E;">
                                    <i class="bi bi-check-circle-fill fs-5"></i>
                                </div>
                                <span class="text-sub small text-uppercase fw-semibold" style="font-size: 11px;">Tepat Waktu</span>
                                <h4 class="fw-bold mb-0 mt-1" style="color: #0F766E;" id="presensiStatTepatWaktu">${dol}{tepatWaktuCount}</h4>
                            </div>
                        </div>
                        <!-- Terlambat -->
                        <div class="col-6 col-sm-4 col-md-2">
                            <div class="card card-custom p-3 border-0 h-100 shadow-sm text-center" style="border-top: 3px solid #EF4444 !important; border-radius: 14px;">
                                <div class="p-2 rounded-circle mx-auto mb-2 d-flex align-items-center justify-content-center" style="width: 38px; height: 38px; background: rgba(239, 68, 68, 0.15); color: #EF4444;">
                                    <i class="bi bi-clock-history fs-5"></i>
                                </div>
                                <span class="text-sub small text-uppercase fw-semibold" style="font-size: 11px;">Terlambat</span>
                                <h4 class="fw-bold text-danger mb-0 mt-1" id="presensiStatTerlambat">${dol}{terlambatCount}</h4>
                            </div>
                        </div>
                        <!-- Pulang Awal -->
                        <div class="col-6 col-sm-4 col-md-2">
                            <div class="card card-custom p-3 border-0 h-100 shadow-sm text-center" style="border-top: 3px solid #F59E0B !important; border-radius: 14px;">
                                <div class="p-2 rounded-circle mx-auto mb-2 d-flex align-items-center justify-content-center" style="width: 38px; height: 38px; background: rgba(245, 158, 11, 0.15); color: #F59E0B;">
                                    <i class="bi bi-box-arrow-left fs-5"></i>
                                </div>
                                <span class="text-sub small text-uppercase fw-semibold" style="font-size: 11px;">Pulang Awal</span>
                                <h4 class="fw-bold text-warning mb-0 mt-1" id="presensiStatPulangAwal">${dol}{pulangAwalCount}</h4>
                            </div>
                        </div>
                        <!-- Izin -->
                        <div class="col-6 col-sm-4 col-md-2">
                            <div class="card card-custom p-3 border-0 h-100 shadow-sm text-center" style="border-top: 3px solid #3B82F6 !important; border-radius: 14px;">
                                <div class="p-2 rounded-circle mx-auto mb-2 d-flex align-items-center justify-content-center" style="width: 38px; height: 38px; background: rgba(59, 130, 246, 0.15); color: #3B82F6;">
                                    <i class="bi bi-file-earmark-text-fill fs-5"></i>
                                </div>
                                <span class="text-sub small text-uppercase fw-semibold" style="font-size: 11px;">Izin</span>
                                <h4 class="fw-bold text-primary mb-0 mt-1" id="presensiStatIzin">${dol}{izinCount}</h4>
                            </div>
                        </div>
                        <!-- Sakit -->
                        <div class="col-6 col-sm-4 col-md-2">
                            <div class="card card-custom p-3 border-0 h-100 shadow-sm text-center" style="border-top: 3px solid #8B5CF6 !important; border-radius: 14px;">
                                <div class="p-2 rounded-circle mx-auto mb-2 d-flex align-items-center justify-content-center" style="width: 38px; height: 38px; background: rgba(139, 92, 246, 0.15); color: #8B5CF6;">
                                    <i class="bi bi-heart-pulse-fill fs-5"></i>
                                </div>
                                <span class="text-sub small text-uppercase fw-semibold" style="font-size: 11px;">Sakit</span>
                                <h4 class="fw-bold mb-0 mt-1" style="color: #8B5CF6;" id="presensiStatSakit">${dol}{sakitCount}</h4>
                            </div>
                        </div>
                    </div>

                    <!-- 3. TABEL RIWAYAT & PELANGGARAN ABSENSI (FULL WIDTH WITH ACTION ICONS NEXT TO CSV EXPORT) -->
                    <div class="card card-custom border-0 shadow-sm p-4 mb-4" style="border-radius: 18px;">
                        <div class="d-flex flex-wrap justify-content-between align-items-center gap-3 mb-3">
                            <div>
                                <h5 class="fw-bold text-main mb-0 d-flex align-items-center gap-2">
                                    <i class="bi bi-table text-teal" style="color: #0F766E;"></i> Tabel Riwayat &amp; Pelanggaran Absensi
                                </h5>
                                <p class="text-sub small mb-0">${dol}{new Date().toLocaleDateString('id-ID', { weekday: 'long', year: 'numeric', month: 'long', day: 'numeric' })} • Total Log: <strong id="presensiTotalLogCount">${dol}{window.presensiList.length}</strong></p>
                            </div>
                            
                            <!-- ACTION BUTTONS: BERSIHKAN, EKSPOR CSV, SCAN KAMERA, SCAN BARCODE, FORM INPUT ICON -->
                            <div class="d-flex align-items-center gap-2 flex-wrap">
                                <button class="btn btn-outline-danger btn-sm rounded-pill px-3" onclick="clearAllPresensiLog()" title="Bersihkan Semua Log">
                                    <i class="bi bi-trash me-1"></i> Bersihkan Log
                                </button>
                                <button class="btn btn-outline-success btn-sm rounded-pill px-3" onclick="exportPresensiCsv()" title="Ekspor Rekapitulasi ke CSV">
                                    <i class="bi bi-file-earmark-spreadsheet me-1"></i> Ekspor CSV
                                </button>
                                
                                <!-- 1. Pemindai Kamera Pop-Up Button (HP / Laptop) -->
                                <button class="btn btn-sm text-white rounded-pill px-3 shadow-xs d-flex align-items-center gap-2" style="background: #0F766E;" onclick="openCameraScannerModal()" title="Buka Pemindai Kamera HP / Laptop">
                                    <i class="bi bi-camera-video-fill"></i>
                                    <span>Scan Kamera</span>
                                </button>

                                <!-- 2. Input Cepat / USB Barcode Pop-Up Button -->
                                <button class="btn btn-outline-primary btn-sm rounded-pill px-3 shadow-xs d-flex align-items-center gap-2" onclick="openQuickScanModal()" title="Input Barcode / NIP / USB Scanner">
                                    <i class="bi bi-upc-scan"></i>
                                    <span>Input Barcode / NIP</span>
                                </button>

                                <!-- 3. Form Input Presensi Lengkap (Icon Only without title text) -->
                                <button class="btn btn-outline-secondary btn-sm rounded-circle shadow-xs d-inline-flex align-items-center justify-content-center" style="width: 34px; height: 34px;" onclick="openManualAbsensiModal()" title="Form Input Presensi Lengkap (Waktu Masuk &amp; Pulang)">
                                    <i class="bi bi-pencil-square fs-6"></i>
                                </button>
                            </div>
                        </div>

                        <!-- SEARCH & FILTER BAR -->
                        <div class="row g-2 mb-3">
                            <div class="col-12 col-md-7 col-lg-8">
                                <div class="input-group input-group-sm">
                                    <span class="input-group-text bg-transparent text-muted"><i class="bi bi-search"></i></span>
                                    <input type="text" id="presensiSearchInput" class="form-control" placeholder="Cari nama, NIP, status, divisi, pelanggaran..." value="${dol}{escapeHtml(window.presensiSearchQuery || '')}" oninput="handlePresensiSearch(this.value)">
                                    <button class="btn btn-outline-secondary" id="presensiSearchClearBtn" style="${dol}{window.presensiSearchQuery ? '' : 'display:none;'}" onclick="handlePresensiSearch('')"><i class="bi bi-x"></i></button>
                                </div>
                            </div>
                            <div class="col-12 col-md-5 col-lg-4">
                                <select class="form-select form-select-sm" id="presensiStatusSelectFilter" onchange="handlePresensiStatusFilter(this.value)" style="background: var(--input-bg); color: var(--text-main); border-color: var(--border-color);">
                                    <option value="Semua" ${dol}{statusFilter === 'Semua' ? 'selected' : ''}>Semua Status</option>
                                    <option value="Pelanggaran" ${dol}{statusFilter === 'Pelanggaran' ? 'selected' : ''}>⚠️ Ada Pelanggaran</option>
                                    <option value="Tepat Waktu" ${dol}{statusFilter === 'Tepat Waktu' ? 'selected' : ''}>✅ Tepat Waktu</option>
                                    <option value="Hadir" ${dol}{statusFilter === 'Hadir' ? 'selected' : ''}>Hadir</option>
                                    <option value="Izin" ${dol}{statusFilter === 'Izin' ? 'selected' : ''}>Izin</option>
                                    <option value="Sakit" ${dol}{statusFilter === 'Sakit' ? 'selected' : ''}>Sakit</option>
                                    <option value="Terlambat" ${dol}{statusFilter === 'Terlambat' ? 'selected' : ''}>Terlambat</option>
                                </select>
                            </div>
                        </div>

                        <!-- LOG TABLE WITH WAKTU MASUK & WAKTU PULANG & PELANGGARAN -->
                        <div class="table-responsive" style="max-height: 520px; overflow-y: auto;">
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
                                <tbody id="presensiTableBody">
                                    ${dol}{filteredLogs.length === 0 ? `
                                        <tr>
                                            <td colspan="8" class="text-center py-5 text-muted">
                                                <i class="bi bi-inbox fs-2 d-block mb-2 text-secondary opacity-50"></i>
                                                Belum ada riwayat absensi yang tercatat sesuai filter.
                                            </td>
                                        </tr>
                                    ` : filteredLogs.map(p => renderPresensiLogRow(p)).join('')}
                                </tbody>
                            </table>
                        </div>
                    </div>
                </div>

                <!-- MODAL CONTAINER PLACEHOLDER -->
                <div id="presensiModalsContainer"></div>
            `;
        }

        function updatePresensiLiveUi(forceFullRender = false) {
            const tableBody = document.getElementById('presensiTableBody');
            const dynamicContainer = document.getElementById('anggotaTabDynamicContent');

            if (!tableBody || forceFullRender) {
                if (dynamicContainer && window.anggotaActiveTab === 'presensi') {
                    dynamicContainer.innerHTML = renderPresensiSubTabHtml();
                }
                return;
            }

            // Calculate live statistics
            const todayStr = new Date().toDateString();
            const todayLogs = window.presensiList.filter(p => new Date(p.timestamp || 0).toDateString() === todayStr);

            const hadirCount = todayLogs.filter(p => (p.statusPresensi || '').toLowerCase() === 'hadir').length;
            const tepatWaktuCount = todayLogs.filter(p => (!p.statusPelanggaran || p.statusPelanggaran === 'Tepat Waktu') && (p.statusPresensi || '').toLowerCase() === 'hadir').length;
            const terlambatCount = todayLogs.filter(p => (p.statusPresensi || '').toLowerCase() === 'terlambat' || (p.statusPelanggaran || '').includes('Terlambat')).length;
            const pulangAwalCount = todayLogs.filter(p => (p.statusPelanggaran || '').includes('Awal') || (p.statusPelanggaran || '').includes('Cepat') || (p.catatanPelanggaran || '').toLowerCase().includes('awal')).length;
            const izinCount = todayLogs.filter(p => (p.statusPresensi || '').toLowerCase() === 'izin').length;
            const sakitCount = todayLogs.filter(p => (p.statusPresensi || '').toLowerCase() === 'sakit').length;

            const elHadir = document.getElementById('presensiStatHadir');
            if (elHadir) elHadir.textContent = hadirCount;
            const elTepat = document.getElementById('presensiStatTepatWaktu');
            if (elTepat) elTepat.textContent = tepatWaktuCount;
            const elTerlambat = document.getElementById('presensiStatTerlambat');
            if (elTerlambat) elTerlambat.textContent = terlambatCount;
            const elPulangAwal = document.getElementById('presensiStatPulangAwal');
            if (elPulangAwal) elPulangAwal.textContent = pulangAwalCount;
            const elIzin = document.getElementById('presensiStatIzin');
            if (elIzin) elIzin.textContent = izinCount;
            const elSakit = document.getElementById('presensiStatSakit');
            if (elSakit) elSakit.textContent = sakitCount;
            const elTotal = document.getElementById('presensiTotalLogCount');
            if (elTotal) elTotal.textContent = window.presensiList.length;

            // Filter table rows
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

            tableBody.innerHTML = (filteredLogs.length === 0) ? `
                <tr>
                    <td colspan="8" class="text-center py-5 text-muted">
                        <i class="bi bi-inbox fs-2 d-block mb-2 text-secondary opacity-50"></i>
                        Belum ada riwayat absensi yang tercatat sesuai filter.
                    </td>
                </tr>
            ` : filteredLogs.map(p => renderPresensiLogRow(p)).join('');
        }

        function handlePresensiSearch(query) {
            window.presensiSearchQuery = query;
            const clearBtn = document.getElementById('presensiSearchClearBtn');
            if (clearBtn) {
                clearBtn.style.display = query ? 'inline-block' : 'none';
            }
            updatePresensiLiveUi();
        }

        function handlePresensiStatusFilter(status) {
            window.presensiStatusFilter = status;
            updatePresensiLiveUi();
        }

        function renderPresensiLogRow(p) {
            const dateObj = new Date(p.timestamp || Date.now());
            const timeMasukStr = dateObj.toLocaleTimeString('id-ID', { hour: '2-digit', minute: '2-digit' });
            const dateMasukStr = dateObj.toLocaleDateString('id-ID', { day: 'numeric', month: 'short' });

            let waktuPulangHtml = '';
            if (p.waktuPulang && p.waktuPulang > 0) {
                const pulangDate = new Date(p.waktuPulang);
                const timePulangStr = pulangDate.toLocaleTimeString('id-ID', { hour: '2-digit', minute: '2-digit' });
                const datePulangStr = pulangDate.toLocaleDateString('id-ID', { day: 'numeric', month: 'short' });
                waktuPulangHtml = `
                    <span class="fw-bold text-teal" style="color: #0F766E;">${dol}{timePulangStr}</span>
                    <small class="text-sub d-block" style="font-size: 10px;">${dol}{datePulangStr}</small>
                `;
            } else {
                waktuPulangHtml = `
                    <button class="btn btn-outline-warning btn-sm py-0 px-2 fw-semibold" style="font-size: 10px;" onclick="openCatatPulangModal(${dol}{p.id})" title="Catat Waktu Kepulangan">
                        <i class="bi bi-plus-circle me-1"></i>Catat Pulang
                    </button>
                `;
            }

            const statusPresensi = p.statusPresensi || 'Hadir';
            let badgeClass = 'bg-success';
            if (statusPresensi.toLowerCase() === 'izin') badgeClass = 'bg-primary';
            else if (statusPresensi.toLowerCase() === 'sakit') badgeClass = 'bg-warning text-dark';
            else if (statusPresensi.toLowerCase() === 'terlambat') badgeClass = 'bg-danger';

            const pelanggaran = p.statusPelanggaran || 'Tepat Waktu';
            let pelanggaranBadge = '';
            if (pelanggaran === 'Tepat Waktu') {
                pelanggaranBadge = `<span class="badge bg-success bg-opacity-25 text-success rounded-pill px-2 py-1" style="font-size: 10px;"><i class="bi bi-check-circle me-1"></i>Tepat Waktu</span>`;
            } else if (pelanggaran.includes('Terlambat & Pulang')) {
                pelanggaranBadge = `<span class="badge bg-danger rounded-pill px-2 py-1" style="font-size: 10px;" title="${dol}{escapeHtml(p.catatanPelanggaran || '')}"><i class="bi bi-exclamation-triangle-fill me-1"></i>Terlambat & Pulang Cepat</span>`;
            } else if (pelanggaran.includes('Terlambat')) {
                pelanggaranBadge = `<span class="badge bg-danger rounded-pill px-2 py-1" style="font-size: 10px;" title="${dol}{escapeHtml(p.catatanPelanggaran || '')}"><i class="bi bi-exclamation-triangle-fill me-1"></i>Terlambat Masuk</span>`;
            } else {
                pelanggaranBadge = `<span class="badge bg-warning text-dark rounded-pill px-2 py-1" style="font-size: 10px;" title="${dol}{escapeHtml(p.catatanPelanggaran || '')}"><i class="bi bi-exclamation-triangle me-1"></i>Pulang Lebih Awal</span>`;
            }

            return `
                <tr>
                    <td>
                        <span class="fw-bold text-main">${dol}{timeMasukStr}</span>
                        <small class="text-sub d-block" style="font-size: 10px;">${dol}{dateMasukStr}</small>
                    </td>
                    <td>${dol}{waktuPulangHtml}</td>
                    <td>
                        <div class="d-flex align-items-center gap-2">
                            <div class="rounded-circle d-flex align-items-center justify-content-center fw-bold shadow-xs" style="width: 26px; height: 26px; background: rgba(15, 118, 110, 0.15); color: #0F766E; font-size: 11px;">
                                ${dol}{escapeHtml((p.nama || 'A').slice(0,1).toUpperCase())}
                            </div>
                            <div>
                                <div class="fw-bold text-main" style="max-width: 160px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis;">${dol}{escapeHtml(p.nama)}</div>
                                <small class="text-sub" style="font-size: 10px;">${dol}{escapeHtml(p.status || 'Anggota')}${dol}{p.divisi ? ' • ' + escapeHtml(p.divisi) : ''}</small>
                            </div>
                        </div>
                    </td>
                    <td><span class="font-monospace text-main" style="font-size: 11px;">${dol}{escapeHtml(p.nomorInduk || '-')}</span></td>
                    <td>
                        <span class="badge ${dol}{badgeClass} rounded-pill px-2 py-1" style="font-size: 10px;">
                            ${dol}{escapeHtml(statusPresensi)}
                        </span>
                    </td>
                    <td>${dol}{pelanggaranBadge}</td>
                    <td><small class="text-sub" style="font-size: 10px;"><i class="bi bi-upc-scan me-1"></i>${dol}{escapeHtml(p.metode || 'QR')}</small></td>
                    <td class="text-end">
                        <div class="btn-group btn-group-sm">
                            <button class="btn btn-outline-info btn-sm py-0 px-2" onclick="openDetailPresensiModal(${dol}{p.id})" title="Lihat Detail">
                                <i class="bi bi-info-circle"></i>
                            </button>
                            <button class="btn btn-outline-warning btn-sm py-0 px-2" onclick="openCatatPulangModal(${dol}{p.id})" title="Ubah/Catat Waktu Pulang">
                                <i class="bi bi-clock-history"></i>
                            </button>
                            <button class="btn btn-outline-danger btn-sm py-0 px-2" onclick="deleteSinglePresensi(${dol}{p.id})" title="Hapus Log">
                                <i class="bi bi-trash"></i>
                            </button>
                        </div>
                    </td>
                </tr>
            `;
        }

        async function processPresensiPayload(payload, statusPresensi = 'Hadir', isModePulang = false, metode = 'QR Web Scanner') {
            let matchedAnggota = null;
            let nomorInduk = payload;
            let nama = payload;
            let statusPeran = 'Umum';
            let divisi = '';

            if (payload.startsWith('SINGGHARA_MEMBER:')) {
                const parts = payload.split(':');
                nomorInduk = parts[1] || '';
                nama = parts[2] || 'Anggota';
                statusPeran = parts[3] || 'Umum';
            }

            matchedAnggota = window.anggotaList.find(a => 
                (a.nomorInduk && a.nomorInduk.toLowerCase() === nomorInduk.toLowerCase()) ||
                (a.qrCodeData && a.qrCodeData.toLowerCase() === payload.toLowerCase()) ||
                (a.nama && a.nama.toLowerCase() === payload.toLowerCase())
            );

            if (matchedAnggota) {
                nomorInduk = matchedAnggota.nomorInduk;
                nama = matchedAnggota.nama;
                statusPeran = matchedAnggota.status;
                divisi = matchedAnggota.divisiOrJurusan || '';
            }

            const config = getPresensiConfig();
            const now = new Date();
            const nowTimeStr = now.toLocaleTimeString('id-ID', { hour: '2-digit', minute: '2-digit' });
            const nowMinutes = now.getHours() * 60 + now.getMinutes();

            if (!isModePulang) {
                // MODE ABSEN MASUK
                const batasMasukMinutes = parseTimeToMinutes(config.batasMasuk);
                const isTerlambat = nowMinutes > batasMasukMinutes && statusPresensi.toLowerCase() === 'hadir';

                const statusPelanggaran = isTerlambat ? 'Terlambat Masuk' : 'Tepat Waktu';
                const catatanPelanggaran = isTerlambat ? `Terlambat ${dol}{nowMinutes - batasMasukMinutes} mnt (Masuk: ${dol}{nowTimeStr} > Batas: ${dol}{config.batasMasuk})` : '';

                const presensiData = {
                    anggotaId: matchedAnggota ? matchedAnggota.id : 0,
                    nomorInduk: nomorInduk,
                    nama: nama,
                    status: statusPeran,
                    divisi: divisi,
                    statusPresensi: isTerlambat ? 'Terlambat' : statusPresensi,
                    timestamp: Date.now(),
                    batasWaktuMasuk: config.batasMasuk,
                    batasWaktuPulang: config.batasPulang,
                    statusPelanggaran: statusPelanggaran,
                    catatanPelanggaran: catatanPelanggaran,
                    metode: metode,
                    qrPayload: payload
                };

                if (isTerlambat) {
                    showViolationPopup({
                        title: '⚠️ PERINGATAN PELANGGARAN TERLAMBAT MASUK',
                        nama: nama,
                        jenis: 'Terlambat Masuk',
                        waktuInput: `Masuk: ${dol}{nowTimeStr} WIB`,
                        batasWaktu: `Batas Masuk: ${dol}{config.batasMasuk} WIB`,
                        selisih: `${dol}{nowMinutes - batasMasukMinutes} Menit`,
                        pesan: `Anggota ${dol}{nama} melakukan absensi masuk setelah batas waktu yang ditentukan (${dol}{nowTimeStr} > ${dol}{config.batasMasuk}).`,
                        onProceed: async () => {
                            await sendPresensiToServer(presensiData, nama, nomorInduk, 'Terlambat Masuk');
                        }
                    });
                } else {
                    await sendPresensiToServer(presensiData, nama, nomorInduk, statusPresensi);
                }
            } else {
                // MODE ABSEN PULANG
                const batasPulangMinutes = parseTimeToMinutes(config.batasPulang);
                const isPulangAwal = nowMinutes < batasPulangMinutes;

                // Cari presensi hari ini
                const todayStart = new Date().setHours(0,0,0,0);
                const existing = window.presensiList.find(p =>
                    ((p.nomorInduk && p.nomorInduk.toLowerCase() === nomorInduk.toLowerCase()) ||
                     (p.nama && p.nama.toLowerCase() === nama.toLowerCase())) &&
                    p.timestamp >= todayStart
                );

                if (existing) {
                    const isTerlambatMasuk = (existing.statusPelanggaran || '').includes('Terlambat');
                    let newPelanggaran = 'Tepat Waktu';
                    if (isTerlambatMasuk && isPulangAwal) newPelanggaran = 'Terlambat & Pulang Cepat';
                    else if (isTerlambatMasuk) newPelanggaran = 'Terlambat Masuk';
                    else if (isPulangAwal) newPelanggaran = 'Pulang Lebih Awal';

                    const extraCatatan = isPulangAwal ? `Pulang lebih awal ${dol}{batasPulangMinutes - nowMinutes} mnt (Pulang: ${dol}{nowTimeStr} < Batas: ${dol}{config.batasPulang}).` : '';
                    const finalCatatan = [existing.catatanPelanggaran, extraCatatan].filter(Boolean).join(' ');

                    const updateData = {
                        action: 'record_pulang',
                        id: existing.id,
                        waktuPulang: Date.now(),
                        statusPelanggaran: newPelanggaran,
                        catatanPelanggaran: finalCatatan
                    };

                    if (isPulangAwal) {
                        showViolationPopup({
                            title: '⚠️ PERINGATAN PELANGGARAN PULANG LEBIH AWAL',
                            nama: nama,
                            jenis: 'Pulang Lebih Awal',
                            waktuInput: `Pulang: ${dol}{nowTimeStr} WIB`,
                            batasWaktu: `Batas Pulang: ${dol}{config.batasPulang} WIB`,
                            selisih: `${dol}{batasPulangMinutes - nowMinutes} Menit`,
                            pesan: `Anggota ${dol}{nama} melakukan absensi kepulangan mendahului batas jam operasional (${dol}{nowTimeStr} < ${dol}{config.batasPulang}).`,
                            onProceed: async () => {
                                await sendUpdatePulangToServer(updateData, nama);
                            }
                        });
                    } else {
                        await sendUpdatePulangToServer(updateData, nama);
                    }
                } else {
                    // Belum ada data masuk, buat baru langsung pulang
                    const presensiData = {
                        anggotaId: matchedAnggota ? matchedAnggota.id : 0,
                        nomorInduk: nomorInduk,
                        nama: nama,
                        status: statusPeran,
                        divisi: divisi,
                        statusPresensi: 'Hadir',
                        timestamp: Date.now(),
                        waktuPulang: Date.now(),
                        batasWaktuMasuk: config.batasMasuk,
                        batasWaktuPulang: config.batasPulang,
                        statusPelanggaran: isPulangAwal ? 'Pulang Lebih Awal' : 'Tepat Waktu',
                        catatanPelanggaran: isPulangAwal ? `Pulang lebih awal ${dol}{batasPulangMinutes - nowMinutes} mnt.` : '',
                        metode: metode + ' Pulang',
                        qrPayload: payload
                    };

                    if (isPulangAwal) {
                        showViolationPopup({
                            title: '⚠️ PERINGATAN PULANG LEBIH AWAL',
                            nama: nama,
                            jenis: 'Pulang Lebih Awal',
                            waktuInput: `Pulang: ${dol}{nowTimeStr} WIB`,
                            batasWaktu: `Batas Pulang: ${dol}{config.batasPulang} WIB`,
                            selisih: `${dol}{batasPulangMinutes - nowMinutes} Menit`,
                            pesan: `Anggota ${dol}{nama} pulang sebelum batas waktu ${dol}{config.batasPulang} WIB.`,
                            onProceed: async () => {
                                await sendPresensiToServer(presensiData, nama, nomorInduk, 'Pulang Lebih Awal');
                            }
                        });
                    } else {
                        await sendPresensiToServer(presensiData, nama, nomorInduk, 'Pulang');
                    }
                }
            }
        }

        async function sendPresensiToServer(presensiData, nama, nomorInduk, statusText) {
            try {
                const res = await fetch('/api/presensi', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(presensiData)
                });

                if (res.ok) {
                    playSuccessBeep();
                    showScanResultToast(nama, nomorInduk, statusText);
                    await fetchPresensiData();
                    updatePresensiLiveUi();
                }
            } catch(e) {
                console.error('Failed to log presensi:', e);
                alert('Gagal mencatat presensi: ' + e.message);
            }
        }

        async function sendUpdatePulangToServer(updateData, nama) {
            try {
                const res = await fetch('/api/presensi', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(updateData)
                });

                if (res.ok) {
                    playSuccessBeep();
                    showScanResultToast(nama, '', 'Waktu Pulang');
                    await fetchPresensiData();
                    updatePresensiLiveUi();
                }
            } catch(e) {
                console.error('Failed to update pulang:', e);
                alert('Gagal mencatat kepulangan: ' + e.message);
            }
        }

        // ====================================================
        // --- POP-UP PERINGATAN PELANGGARAN MODAL ---
        // ====================================================
        function showViolationPopup(data) {
            let container = document.getElementById('presensiModalsContainer');
            if (!container) {
                container = document.createElement('div');
                container.id = 'presensiModalsContainer';
                document.body.appendChild(container);
            }

            container.innerHTML = `
                <div class="modal fade show d-block" id="violationAlertModal" tabindex="-1" style="background: rgba(0,0,0,0.65); z-index: 1060;">
                    <div class="modal-dialog modal-dialog-centered">
                        <div class="modal-content card-custom border-0 shadow-lg" style="border-radius: 18px; border-top: 6px solid #dc2626 !important; background: var(--modal-bg); color: var(--text-main); border: 1px solid var(--border-color);">
                            <div class="modal-body p-4 text-center">
                                <div class="mb-3">
                                    <div class="d-inline-flex align-items-center justify-content-center rounded-circle" style="width: 64px; height: 64px; background: rgba(220, 38, 38, 0.15); color: #dc2626;">
                                        <i class="bi bi-exclamation-triangle-fill fs-1"></i>
                                    </div>
                                </div>
                                <h5 class="fw-bold text-danger mb-2">${dol}{escapeHtml(data.title)}</h5>
                                <div class="p-3 rounded-3 text-start mb-3" style="background: rgba(220, 38, 38, 0.06); border: 1px dashed rgba(220, 38, 38, 0.3);">
                                    <div class="fw-bold text-danger mb-1"><i class="bi bi-person-fill me-1"></i>${dol}{escapeHtml(data.nama)}</div>
                                    <div class="small text-main"><i class="bi bi-clock me-1"></i><strong>Waktu:</strong> ${dol}{escapeHtml(data.waktuInput)}</div>
                                    <div class="small text-main"><i class="bi bi-shield-exclamation me-1"></i><strong>Ketentuan:</strong> ${dol}{escapeHtml(data.batasWaktu)}</div>
                                    ${dol}{data.selisih ? `<div class="small fw-bold text-danger mt-1"><i class="bi bi-stopwatch me-1"></i>Selisih Keterlambatan: ${dol}{escapeHtml(data.selisih)}</div>` : ''}
                                </div>
                                <p class="small text-sub mb-4">${dol}{escapeHtml(data.pesan)}</p>
                                <div class="d-flex gap-2">
                                    <button type="button" class="btn btn-outline-secondary flex-grow-1 rounded-pill" onclick="closeViolationPopup()">
                                        <i class="bi bi-x-circle me-1"></i> Batal / Koreksi
                                    </button>
                                    <button type="button" class="btn btn-danger flex-grow-1 rounded-pill fw-bold" id="btnConfirmProceedViolation">
                                        <i class="bi bi-check2-circle me-1"></i> Tetap Simpan Pelanggaran
                                    </button>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            `;

            document.getElementById('btnConfirmProceedViolation').onclick = () => {
                closeViolationPopup();
                if (data.onProceed) data.onProceed();
            };
        }

        function closeViolationPopup() {
            const el = document.getElementById('violationAlertModal');
            if (el) el.remove();
        }

        // ====================================================
        // --- MODAL: WAKTU ABSENSI & PULANG ---
        // ====================================================
        function openBatasWaktuModal() {
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
                                <p class="small text-sub mb-3">Tentukan batas jam masuk dan batas jam pulang operasional. Setiap kehadiran atau kepulangan yang melewati batas waktu ini akan memicu peringatan pelanggaran.</p>
                                <div class="mb-3">
                                    <label class="form-label small fw-bold text-main">Batas Jam Masuk (HH:mm)</label>
                                    <input type="time" id="inputBatasMasuk" class="form-control" value="${dol}{config.batasMasuk}" style="background: var(--input-bg); color: var(--text-main); border-color: var(--border-color);">
                                    <div class="form-text small text-sub">Contoh: 08:00 (Lewat dari jam ini dianggap terlambat).</div>
                                </div>
                                <div class="mb-3">
                                    <label class="form-label small fw-bold text-main">Batas Jam Pulang Minimal (HH:mm)</label>
                                    <input type="time" id="inputBatasPulang" class="form-control" value="${dol}{config.batasPulang}" style="background: var(--input-bg); color: var(--text-main); border-color: var(--border-color);">
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

        function closeBatasWaktuModal() {
            const el = document.getElementById('batasWaktuModal');
            if (el) el.remove();
        }

        function saveBatasWaktuSubmit() {
            const masuk = document.getElementById('inputBatasMasuk')?.value || '08:00';
            const pulang = document.getElementById('inputBatasPulang')?.value || '17:00';
            savePresensiConfig(masuk, pulang);
            closeBatasWaktuModal();
            const container = document.getElementById('anggotaTabDynamicContent');
            if (container && window.anggotaActiveTab === 'presensi') {
                container.innerHTML = renderPresensiSubTabHtml();
            }
        }

        // ====================================================
        // --- 1. MODAL: PEMINDAI KAMERA SCANNER (HP & LAPTOP) ---
        // ====================================================
        window.cameraScannerActive = false;
        window.currentScannerCameraId = null;
        window.availableCamerasList = [];
        window.lastScannedPayload = null;
        window.lastScannedTime = 0;

        function openCameraScannerModal() {
            const config = getPresensiConfig();
            let container = document.getElementById('presensiModalsContainer');
            if (!container) {
                container = document.createElement('div');
                container.id = 'presensiModalsContainer';
                document.body.appendChild(container);
            }

            container.innerHTML = `
                <div class="modal fade show d-block" id="cameraScannerModal" tabindex="-1" style="background: rgba(0,0,0,0.7); z-index: 1055;">
                    <div class="modal-dialog modal-dialog-centered modal-lg">
                        <div class="modal-content card-custom border-0 shadow-lg" style="border-radius: 20px; background: var(--modal-bg); color: var(--text-main); border: 1px solid var(--border-color);">
                            <div class="modal-header border-0 pb-0 d-flex align-items-center justify-content-between">
                                <h5 class="fw-bold text-main mb-0 d-flex align-items-center gap-2">
                                    <i class="bi bi-camera-video-fill text-teal" style="color: #0F766E;"></i> Pemindai Kamera Presensi (HP &amp; Laptop)
                                </h5>
                                <button type="button" class="btn-close btn-close-custom" onclick="closeCameraScannerModal()"></button>
                            </div>
                            <div class="modal-body p-4">
                                <!-- CAMERA SELECTION TOOLBAR -->
                                <div class="d-flex flex-wrap align-items-center justify-content-between gap-2 mb-3 p-2 rounded-3 border" style="background: var(--input-bg); border-color: var(--border-color) !important;">
                                    <div class="d-flex align-items-center gap-2 flex-grow-1 min-w-0">
                                        <i class="bi bi-webcam text-teal fs-5" style="color: #0F766E;"></i>
                                        <div class="flex-grow-1">
                                            <select id="modalCameraDeviceSelect" class="form-select form-select-sm" onchange="changeScannerCameraDevice(this.value)" style="background: var(--card-bg); color: var(--text-main); border-color: var(--border-color);">
                                                <option value="">Mendeteksi Kamera...</option>
                                            </select>
                                        </div>
                                    </div>
                                    <div class="d-flex align-items-center gap-2">
                                        <button type="button" class="btn btn-outline-secondary btn-sm rounded-pill px-3 shadow-xs" onclick="flipScannerCameraDevice()" title="Ganti ke Kamera Lain">
                                            <i class="bi bi-arrow-repeat me-1"></i> Ganti Kamera
                                        </button>
                                        <button type="button" class="btn btn-outline-secondary btn-sm rounded-pill px-3 shadow-xs" onclick="triggerModalQrImageUpload()" title="Scan dari File Foto / Gambar QR Code">
                                            <i class="bi bi-image me-1"></i> Scan Foto
                                        </button>
                                    </div>
                                </div>
                                <input type="file" id="modalQrCodeFileInput" accept="image/*" style="display:none;" onchange="handleModalQrImageFile(event)">

                                <!-- MODE & STATUS CONTROLS -->
                                <div class="row g-3 mb-3">
                                    <div class="col-12 col-md-6">
                                        <div class="d-flex align-items-center justify-content-between mb-1">
                                            <label class="form-label small fw-bold text-main mb-0">Mode Presensi:</label>
                                            <button type="button" class="btn btn-sm text-white rounded-pill px-2 py-0 fw-semibold d-inline-flex align-items-center gap-1 shadow-xs" id="btnReqCamPermission" onclick="requestBrowserCameraPermission()" style="background: #0F766E; font-size: 11px; height: 26px;" title="Klik untuk meminta izin akses kamera browser">
                                                <i class="bi bi-shield-check"></i> Minta Izin Kamera
                                            </button>
                                        </div>
                                        <div class="btn-group w-100" role="group">
                                            <input type="radio" class="btn-check" name="modalScanMode" id="mScanMasuk" value="masuk" checked onchange="updateModalScanNotice()">
                                            <label class="btn btn-outline-success btn-sm py-2 fw-bold" for="mScanMasuk">
                                                <i class="bi bi-box-arrow-in-right me-1"></i> Absen Masuk
                                            </label>

                                            <input type="radio" class="btn-check" name="modalScanMode" id="mScanPulang" value="pulang" onchange="updateModalScanNotice()">
                                            <label class="btn btn-outline-warning btn-sm py-2 fw-bold" for="mScanPulang">
                                                <i class="bi bi-box-arrow-left me-1"></i> Absen Pulang
                                            </label>
                                        </div>
                                    </div>
                                    <div class="col-12 col-md-6">
                                        <label class="form-label small fw-bold text-main d-block mb-1">Status Kehadiran:</label>
                                        <div class="btn-group btn-group-sm w-100" role="group">
                                            <input type="radio" class="btn-check" name="modalScanStatusChoice" id="mOptHadir" value="Hadir" checked>
                                            <label class="btn btn-outline-success btn-sm" for="mOptHadir">Hadir</label>

                                            <input type="radio" class="btn-check" name="modalScanStatusChoice" id="mOptIzin" value="Izin">
                                            <label class="btn btn-outline-primary btn-sm" for="mOptIzin">Izin</label>

                                            <input type="radio" class="btn-check" name="modalScanStatusChoice" id="mOptSakit" value="Sakit">
                                            <label class="btn btn-outline-warning btn-sm" for="mOptSakit">Sakit</label>

                                            <input type="radio" class="btn-check" name="modalScanStatusChoice" id="mOptTerlambat" value="Terlambat">
                                            <label class="btn btn-outline-danger btn-sm" for="mOptTerlambat">Terlambat</label>
                                        </div>
                                    </div>
                                </div>
                                <div id="modalScanNoticeText" class="form-text small mb-3 text-sub" style="font-size: 11px;">
                                    Batas waktu masuk: <strong>${dol}{config.batasMasuk} WIB</strong>. Jika lewat, akan memicu status Terlambat.
                                </div>

                                <!-- WEBCAM VIEWPORT -->
                                <div class="position-relative rounded-4 overflow-hidden border bg-black text-white d-flex align-items-center justify-content-center" style="min-height: 280px; max-height: 380px; border-color: var(--border-color) !important;">
                                    <div id="modalWebcamQrReader" class="w-100 h-100" style="min-height: 280px;"></div>
                                    <div id="modalWebcamLoadingSpinner" class="position-absolute top-50 start-50 translate-middle text-center">
                                        <div class="spinner-border text-teal" style="color: #0F766E;" role="status"></div>
                                        <div class="small text-white mt-2 fw-semibold">Mengakses Kamera...</div>
                                    </div>
                                </div>

                                <!-- NOTIFIKASI HASIL SCAN TERAKHIR -->
                                <div id="modalScanResultFeedback" class="mt-3 p-3 rounded-3 border d-none" style="background: var(--input-bg); border-color: var(--border-color) !important;">
                                    <div class="d-flex align-items-center gap-3">
                                        <div class="p-2 rounded-circle bg-success text-white">
                                            <i class="bi bi-check-circle-fill fs-4"></i>
                                        </div>
                                        <div class="flex-grow-1 min-w-0">
                                            <h6 class="fw-bold text-success mb-0" id="modalScanFeedbackName">Nama Anggota</h6>
                                            <div class="small text-main" id="modalScanFeedbackNip">NIP: -</div>
                                            <span class="badge bg-success rounded-pill mt-1" id="modalScanFeedbackBadge">Presensi Tercatat</span>
                                        </div>
                                    </div>
                                </div>
                            </div>
                            <div class="modal-footer border-0 pt-0 d-flex justify-content-between align-items-center">
                                <span class="small text-sub" style="font-size: 11px;"><i class="bi bi-info-circle me-1"></i>Arahkan Kode QR / Barcode kartu ke kamera</span>
                                <button type="button" class="btn btn-outline-secondary rounded-pill px-4" onclick="closeCameraScannerModal()">Tutup Pemindai</button>
                            </div>
                        </div>
                    </div>
                </div>
            `;

            initModalCameraScanner();
        }

        function updateModalScanNotice() {
            const isPulang = document.getElementById('mScanPulang')?.checked;
            const noticeEl = document.getElementById('modalScanNoticeText');
            const config = getPresensiConfig();
            if (noticeEl) {
                if (isPulang) {
                    noticeEl.innerHTML = `Batas waktu pulang minimal: <strong>${dol}{config.batasPulang} WIB</strong>. Jika pulang sebelum batas, akan tercatat Pulang Awal.`;
                } else {
                    noticeEl.innerHTML = `Batas waktu masuk: <strong>${dol}{config.batasMasuk} WIB</strong>. Jika lewat, akan tercatat Terlambat Masuk.`;
                }
            }
        }

        async function initModalCameraScanner() {
            const spinner = document.getElementById('modalWebcamLoadingSpinner');
            const selectEl = document.getElementById('modalCameraDeviceSelect');

            if (typeof Html5Qrcode === 'undefined') {
                if (spinner) spinner.innerHTML = '<span class="text-danger small">Pustaka scanner belum dimuat. Periksa koneksi internet.</span>';
                return;
            }

            try {
                const cameras = await Html5Qrcode.getCameras();
                window.availableCamerasList = cameras || [];

                if (selectEl) {
                    if (cameras && cameras.length > 0) {
                        selectEl.innerHTML = cameras.map((c, i) => {
                            let label = c.label || `Kamera ${dol}{i + 1}`;
                            if (label.toLowerCase().includes('back') || label.toLowerCase().includes('rear') || label.toLowerCase().includes('environment')) {
                                label += ' (Belakang)';
                            } else if (label.toLowerCase().includes('front') || label.toLowerCase().includes('user') || label.toLowerCase().includes('face')) {
                                label += ' (Depan / Webcam)';
                            }
                            return `<option value="${dol}{c.id}">${dol}{label}</option>`;
                        }).join('');

                        // Prioritaskan kamera belakang jika di HP / Mobile
                        let defaultCamera = cameras[0];
                        const backCam = cameras.find(c => (c.label || '').toLowerCase().includes('back') || (c.label || '').toLowerCase().includes('rear'));
                        if (backCam) {
                            defaultCamera = backCam;
                            selectEl.value = defaultCamera.id;
                        }
                        window.currentScannerCameraId = defaultCamera.id;
                    } else {
                        selectEl.innerHTML = '<option value="default">Kamera Default Perangkat</option>';
                        window.currentScannerCameraId = null;
                    }
                }

                await startModalCameraStream(window.currentScannerCameraId);
            } catch(e) {
                console.warn('GetCameras fallback:', e);
                // Coba start tanpa cameraId (fallback facingMode)
                if (selectEl) selectEl.innerHTML = '<option value="default">Kamera Utama Perangkat</option>';
                await startModalCameraStream(null);
            }
        }

        async function startModalCameraStream(cameraId) {
            const spinner = document.getElementById('modalWebcamLoadingSpinner');
            if (window.html5QrScanner) {
                try {
                    await window.html5QrScanner.stop();
                    window.html5QrScanner.clear();
                } catch(err) {}
                window.html5QrScanner = null;
            }

            try {
                window.html5QrScanner = new Html5Qrcode("modalWebcamQrReader");
                
                const cameraConfig = cameraId ? { deviceId: { exact: cameraId } } : { facingMode: "environment" };
                const qrScanConfig = {
                    fps: 15,
                    qrbox: (viewfinderWidth, viewfinderHeight) => {
                        const minEdge = Math.min(viewfinderWidth, viewfinderHeight);
                        const boxSize = Math.floor(minEdge * 0.72);
                        return { width: Math.min(boxSize, 280), height: Math.min(boxSize, 280) };
                    }
                };

                await window.html5QrScanner.start(
                    cameraConfig,
                    qrScanConfig,
                    (decodedText) => {
                        handleCameraScanDecoded(decodedText);
                    },
                    (errorMsg) => {
                        // frame decode pass
                    }
                );

                window.cameraScannerActive = true;
                if (spinner) spinner.classList.add('d-none');
            } catch(err) {
                console.warn('Camera start error with primary config, trying fallback:', err);
                try {
                    // Fallback to user-facing or general video stream for laptops
                    await window.html5QrScanner.start(
                        { facingMode: "user" },
                        { fps: 15, qrbox: { width: 240, height: 240 } },
                        (decodedText) => handleCameraScanDecoded(decodedText),
                        (errorMsg) => {}
                    );
                    window.cameraScannerActive = true;
                    if (spinner) spinner.classList.add('d-none');
                } catch(fallbackErr) {
                    console.error('Fatal camera error:', fallbackErr);
                    if (spinner) {
                        const errMsg = fallbackErr.message || fallbackErr;
                        spinner.innerHTML = `
                            <div class="p-3 text-center" style="max-width: 480px;">
                                <i class="bi bi-camera-video-off fs-2 text-danger d-block mb-2"></i>
                                <div class="small text-danger fw-bold mb-1">Tidak dapat mengakses kamera: ${dol}{escapeHtml(errMsg)}</div>
                                <div class="text-sub small mb-3" style="font-size: 11px;">Pastikan izin kamera diizinkan pada browser Anda. Jika diakses via HTTP pada PC, klik tombol di bawah untuk meminta izin atau gunakan opsi Scan Foto.</div>
                                <div class="d-flex flex-wrap justify-content-center gap-2">
                                    <button type="button" class="btn btn-sm text-white rounded-pill px-3 shadow-xs fw-semibold" style="background: #0F766E;" onclick="requestBrowserCameraPermission()">
                                        <i class="bi bi-shield-check me-1"></i> Minta Izin Kamera
                                    </button>
                                    <button type="button" class="btn btn-sm btn-outline-light rounded-pill px-3 shadow-xs" onclick="triggerModalQrImageUpload()">
                                        <i class="bi bi-image me-1"></i> Scan Foto / Gambar QR
                                    </button>
                                    <button type="button" class="btn btn-sm btn-outline-warning rounded-pill px-3 shadow-xs" onclick="showCameraPermissionGuideModal('${dol}{escapeHtml(errMsg)}')">
                                        <i class="bi bi-question-circle me-1"></i> Panduan Izin
                                    </button>
                                </div>
                            </div>
                        `;
                    }
                }
            }
        }

        async function requestBrowserCameraPermission() {
            const btn = document.getElementById('btnReqCamPermission');
            if (btn) {
                btn.innerHTML = '<span class="spinner-border spinner-border-sm me-1" role="status" style="width:10px;height:10px;"></span> Meminta...';
                btn.disabled = true;
            }

            try {
                let stream = null;
                if (navigator.mediaDevices && navigator.mediaDevices.getUserMedia) {
                    try {
                        stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: 'environment' } });
                    } catch(e) {
                        stream = await navigator.mediaDevices.getUserMedia({ video: true });
                    }
                } else if (navigator.getUserMedia) {
                    stream = await new Promise((resolve, reject) => navigator.getUserMedia({ video: true }, resolve, reject));
                } else if (navigator.webkitGetUserMedia) {
                    stream = await new Promise((resolve, reject) => navigator.webkitGetUserMedia({ video: true }, resolve, reject));
                } else {
                    throw new Error('Camera streaming not supported by the browser (Koneksi HTTP / Non-Secure Context).');
                }

                if (stream) {
                    stream.getTracks().forEach(t => t.stop());
                    if (btn) {
                        btn.innerHTML = '<i class="bi bi-check-circle-fill"></i> Izin Aktif';
                        btn.style.background = '#16a34a';
                        btn.disabled = false;
                    }
                    const spinner = document.getElementById('modalWebcamLoadingSpinner');
                    if (spinner) {
                        spinner.classList.remove('d-none');
                        spinner.innerHTML = '<div class="spinner-border text-teal" style="color: #0F766E;" role="status"></div><div class="small text-white mt-2 fw-semibold">Mengaktifkan Aliran Kamera...</div>';
                    }
                    await initModalCameraScanner();
                }
            } catch(err) {
                console.error('Request camera permission failed:', err);
                const errMsg = err.message || err.name || String(err);
                if (btn) {
                    btn.innerHTML = '<i class="bi bi-shield-exclamation"></i> Minta Izin';
                    btn.style.background = '#dc2626';
                    btn.disabled = false;
                }
                showCameraPermissionGuideModal(errMsg);
            }
        }

        function triggerModalQrImageUpload() {
            let fileInput = document.getElementById('modalQrCodeFileInput');
            if (!fileInput) {
                fileInput = document.createElement('input');
                fileInput.type = 'file';
                fileInput.id = 'modalQrCodeFileInput';
                fileInput.accept = 'image/*';
                fileInput.style.display = 'none';
                fileInput.onchange = (e) => handleModalQrImageFile(e);
                document.body.appendChild(fileInput);
            }
            fileInput.click();
        }

        async function handleModalQrImageFile(event) {
            const file = event.target.files && event.target.files[0];
            if (!file) return;

            const spinner = document.getElementById('modalWebcamLoadingSpinner');
            if (spinner) {
                spinner.classList.remove('d-none');
                spinner.innerHTML = '<div class="spinner-border text-teal" style="color: #0F766E;" role="status"></div><div class="small text-white mt-2">Menganalisis Kode QR dari Gambar...</div>';
            }

            try {
                let scanner = window.html5QrScanner;
                if (!scanner) {
                    scanner = new Html5Qrcode("modalWebcamQrReader");
                    window.html5QrScanner = scanner;
                }

                const decodedText = await scanner.scanFile(file, true);
                if (decodedText) {
                    await handleCameraScanDecoded(decodedText);
                }
            } catch(err) {
                alert('Tidak dapat membaca QR Code dari file gambar ini: ' + (err.message || err));
            } finally {
                if (spinner) spinner.classList.add('d-none');
                event.target.value = '';
            }
        }

        function showCameraPermissionGuideModal(errMsg = '') {
            let container = document.getElementById('presensiModalsContainer');
            if (!container) {
                container = document.createElement('div');
                container.id = 'presensiModalsContainer';
                document.body.appendChild(container);
            }

            const isHttpNotSecure = location.protocol === 'http:' && location.hostname !== 'localhost' && location.hostname !== '127.0.0.1';
            const originUrl = location.origin;

            const guideModal = document.createElement('div');
            guideModal.id = 'cameraPermissionGuideModal';
            guideModal.innerHTML = `
                <div class="modal fade show d-block" tabindex="-1" style="background: rgba(0,0,0,0.8); z-index: 1065;">
                    <div class="modal-dialog modal-dialog-centered modal-lg">
                        <div class="modal-content card-custom border-0 shadow-lg" style="border-radius: 20px; background: var(--modal-bg); color: var(--text-main); border: 1px solid var(--border-color);">
                            <div class="modal-header border-0 pb-0">
                                <h5 class="fw-bold text-main mb-0 d-flex align-items-center gap-2">
                                    <i class="bi bi-camera-video-off text-danger"></i> Panduan Izin Akses Kamera Browser
                                </h5>
                                <button type="button" class="btn-close btn-close-custom" onclick="closeCameraPermissionGuideModal()"></button>
                            </div>
                            <div class="modal-body p-4">
                                ${dol}{errMsg ? `
                                    <div class="alert alert-danger d-flex align-items-center gap-2 p-3 rounded-3 mb-3 border-0">
                                        <i class="bi bi-exclamation-triangle-fill fs-5"></i>
                                        <div class="small fw-semibold">Pesan Kesalahan: ${dol}{escapeHtml(errMsg)}</div>
                                    </div>
                                ` : ''}

                                <h6 class="fw-bold text-main mb-2">Mengapa Kamera Tidak Bisa Terbuka Otomatis?</h6>
                                <p class="small text-sub mb-3">
                                    Browser modern (Google Chrome, Microsoft Edge, Safari) membatasi akses streaming webcam langsung jika website diakses melalui jaringan lokal IP HTTP non-HTTPS (misal: <code>${dol}{originUrl}</code>).
                                </p>

                                <div class="row g-3 mb-4">
                                    <div class="col-12 col-md-6">
                                        <div class="p-3 rounded-3 h-100 border" style="background: var(--input-bg); border-color: var(--border-color) !important;">
                                            <div class="fw-bold text-teal mb-2 d-flex align-items-center gap-2" style="color: #0F766E;">
                                                <span class="badge rounded-circle p-2 bg-teal text-white" style="background: #0F766E;">1</span>
                                                Cek Pengaturan Izin Situs
                                            </div>
                                            <ul class="small text-sub ps-3 mb-0" style="line-height: 1.6;">
                                                <li>Klik ikon <strong>Gembok / Pengaturan Situs</strong> di sebelah kiri URL address bar browser Anda.</li>
                                                <li>Cari menu <strong>Kamera (Camera)</strong> dan ubah dari <em>Blokir</em> ke <strong>Izinkan (Allow)</strong>.</li>
                                                <li>Muat ulang (Refresh) halaman web ini.</li>
                                            </ul>
                                        </div>
                                    </div>
                                    <div class="col-12 col-md-6">
                                        <div class="p-3 rounded-3 h-100 border" style="background: var(--input-bg); border-color: var(--border-color) !important;">
                                            <div class="fw-bold text-teal mb-2 d-flex align-items-center gap-2" style="color: #0F766E;">
                                                <span class="badge rounded-circle p-2 bg-teal text-white" style="background: #0F766E;">2</span>
                                                Izinkan IP di Chrome / Edge
                                            </div>
                                            <ul class="small text-sub ps-3 mb-0" style="line-height: 1.6;">
                                                <li>Buka tab baru, ketik: <code>chrome://flags/#unsafely-treat-insecure-origin-as-secure</code></li>
                                                <li>Masukkan URL: <code>${dol}{originUrl}</code></li>
                                                <li>Ubah opsi menjadi <strong>Enabled</strong>, lalu klik tombol <strong>Relaunch</strong>.</li>
                                            </ul>
                                        </div>
                                    </div>
                                </div>

                                <div class="p-3 rounded-3 border bg-success bg-opacity-10 border-success border-opacity-25 mb-2">
                                    <h6 class="fw-bold text-success mb-1 d-flex align-items-center gap-2">
                                        <i class="bi bi-lightning-charge-fill"></i> Solusi Instan Tanpa Setting Browser:
                                    </h6>
                                    <p class="small text-sub mb-2">Anda dapat langsung memindai QR Code dengan mengklik <strong>Scan Foto / Gambar QR</strong> atau gunakan alat <strong>Input Barcode / NIP USB Scanner</strong>.</p>
                                    <div class="d-flex flex-wrap gap-2">
                                        <button type="button" class="btn btn-sm text-white rounded-pill px-3 shadow-xs fw-semibold" style="background: #0F766E;" onclick="closeCameraPermissionGuideModal(); triggerModalQrImageUpload();">
                                            <i class="bi bi-image me-1"></i> Scan dari Foto / File Gambar
                                        </button>
                                        <button type="button" class="btn btn-sm btn-outline-success rounded-pill px-3 shadow-xs fw-semibold" onclick="closeCameraPermissionGuideModal(); requestBrowserCameraPermission();">
                                            <i class="bi bi-shield-check me-1"></i> Coba Minta Izin Lagi
                                        </button>
                                    </div>
                                </div>
                            </div>
                            <div class="modal-footer border-0 pt-0">
                                <button type="button" class="btn btn-outline-secondary rounded-pill px-4" onclick="closeCameraPermissionGuideModal()">Tutup</button>
                            </div>
                        </div>
                    </div>
                </div>
            `;
            document.body.appendChild(guideModal);
        }

        function closeCameraPermissionGuideModal() {
            const el = document.getElementById('cameraPermissionGuideModal');
            if (el) el.remove();
        }

        async function changeScannerCameraDevice(cameraId) {
            window.currentScannerCameraId = cameraId;
            const spinner = document.getElementById('modalWebcamLoadingSpinner');
            if (spinner) spinner.classList.remove('d-none');
            await startModalCameraStream(cameraId);
        }

        async function flipScannerCameraDevice() {
            if (!window.availableCamerasList || window.availableCamerasList.length <= 1) {
                // Toggle between environment & user
                const spinner = document.getElementById('modalWebcamLoadingSpinner');
                if (spinner) spinner.classList.remove('d-none');
                const nextMode = window.scannerCurrentFacing === 'user' ? 'environment' : 'user';
                window.scannerCurrentFacing = nextMode;
                await startModalCameraStream(null);
                return;
            }

            const currentIdx = window.availableCamerasList.findIndex(c => c.id === window.currentScannerCameraId);
            const nextIdx = (currentIdx + 1) % window.availableCamerasList.length;
            const nextCamera = window.availableCamerasList[nextIdx];
            const selectEl = document.getElementById('modalCameraDeviceSelect');
            if (selectEl) selectEl.value = nextCamera.id;
            await changeScannerCameraDevice(nextCamera.id);
        }

        async function handleCameraScanDecoded(decodedText) {
            const now = Date.now();
            // Throttle duplicate scan of the exact same code within 2.5 seconds
            if (window.lastScannedPayload === decodedText && (now - window.lastScannedTime < 2500)) {
                return;
            }
            window.lastScannedPayload = decodedText;
            window.lastScannedTime = now;

            const isPulang = document.getElementById('mScanPulang')?.checked || false;
            const selectedRadio = document.querySelector('input[name="modalScanStatusChoice"]:checked');
            const st = selectedRadio ? selectedRadio.value : 'Hadir';

            await processPresensiPayload(decodedText, st, isPulang, 'Kamera Scanner (HP/PC)');

            // Show feedback in modal
            const feedbackBox = document.getElementById('modalScanResultFeedback');
            const nameEl = document.getElementById('modalScanFeedbackName');
            const nipEl = document.getElementById('modalScanFeedbackNip');
            const badgeEl = document.getElementById('modalScanFeedbackBadge');

            if (feedbackBox && nameEl && badgeEl) {
                nameEl.textContent = decodedText.startsWith('SINGGHARA_MEMBER:') ? decodedText.split(':')[2] : decodedText;
                if (nipEl) nipEl.textContent = `Waktu: ${dol}{new Date().toLocaleTimeString('id-ID')}`;
                badgeEl.textContent = `${dol}{isPulang ? 'Absen Pulang' : st} Sukses`;
                feedbackBox.classList.remove('d-none');

                setTimeout(() => {
                    if (feedbackBox) feedbackBox.classList.add('d-none');
                }, 3500);
            }
        }

        async function closeCameraScannerModal() {
            if (window.html5QrScanner) {
                try {
                    await window.html5QrScanner.stop();
                    window.html5QrScanner.clear();
                } catch(e) {}
                window.html5QrScanner = null;
            }
            window.cameraScannerActive = false;
            const el = document.getElementById('cameraScannerModal');
            if (el) el.remove();
        }

        // ====================================================
        // --- 2. MODAL: INPUT CEPAT / USB BARCODE SCANNER ---
        // ====================================================
        function openQuickScanModal() {
            const config = getPresensiConfig();
            let container = document.getElementById('presensiModalsContainer');
            if (!container) {
                container = document.createElement('div');
                container.id = 'presensiModalsContainer';
                document.body.appendChild(container);
            }

            container.innerHTML = `
                <div class="modal fade show d-block" id="quickScanModal" tabindex="-1" style="background: rgba(0,0,0,0.6); z-index: 1055;">
                    <div class="modal-dialog modal-dialog-centered">
                        <div class="modal-content card-custom border-0 shadow-lg" style="border-radius: 20px; background: var(--modal-bg); color: var(--text-main); border: 1px solid var(--border-color);">
                            <div class="modal-header border-0 pb-0 d-flex align-items-center justify-content-between">
                                <h5 class="fw-bold text-main mb-0 d-flex align-items-center gap-2">
                                    <i class="bi bi-upc-scan text-teal" style="color: #0F766E;"></i> Input Barcode / NIP / USB Scanner
                                </h5>
                                <button type="button" class="btn-close btn-close-custom" onclick="closeQuickScanModal()"></button>
                            </div>
                            <div class="modal-body p-4">
                                <p class="small text-sub mb-3">Gunakan pemindai barcode USB genggam, barcode laser gun, atau ketik NIP/NIM manual lalu tekan Enter.</p>
                                
                                <!-- MODE & STATUS SELECTION -->
                                <div class="mb-3">
                                    <label class="form-label small fw-bold text-main d-block mb-1">Mode Presensi:</label>
                                    <div class="btn-group w-100" role="group">
                                        <input type="radio" class="btn-check" name="quickModalScanMode" id="qScanMasuk" value="masuk" checked onchange="updateQuickModalNotice()">
                                        <label class="btn btn-outline-success btn-sm py-2 fw-bold" for="qScanMasuk">
                                            <i class="bi bi-box-arrow-in-right me-1"></i> Absen Masuk
                                        </label>

                                        <input type="radio" class="btn-check" name="quickModalScanMode" id="qScanPulang" value="pulang" onchange="updateQuickModalNotice()">
                                        <label class="btn btn-outline-warning btn-sm py-2 fw-bold" for="qScanPulang">
                                            <i class="bi bi-box-arrow-left me-1"></i> Absen Pulang
                                        </label>
                                    </div>
                                    <div id="quickModalNoticeText" class="form-text small mt-1 text-sub" style="font-size: 11px;">
                                        Batas waktu masuk: <strong>${dol}{config.batasMasuk} WIB</strong>.
                                    </div>
                                </div>

                                <div class="mb-3">
                                    <label class="form-label small fw-bold text-main d-block mb-1">Status Kehadiran:</label>
                                    <div class="btn-group btn-group-sm w-100" role="group">
                                        <input type="radio" class="btn-check" name="quickModalStatusChoice" id="qOptHadir" value="Hadir" checked>
                                        <label class="btn btn-outline-success btn-sm" for="qOptHadir">Hadir</label>

                                        <input type="radio" class="btn-check" name="quickModalStatusChoice" id="qOptIzin" value="Izin">
                                        <label class="btn btn-outline-primary btn-sm" for="qOptIzin">Izin</label>

                                        <input type="radio" class="btn-check" name="quickModalStatusChoice" id="qOptSakit" value="Sakit">
                                        <label class="btn btn-outline-warning btn-sm" for="qOptSakit">Sakit</label>

                                        <input type="radio" class="btn-check" name="quickModalStatusChoice" id="qOptTerlambat" value="Terlambat">
                                        <label class="btn btn-outline-danger btn-sm" for="qOptTerlambat">Terlambat</label>
                                    </div>
                                </div>

                                <!-- INPUT SCANNER FORM -->
                                <form onsubmit="handleQuickModalInputSubmit(event)">
                                    <div class="mb-3">
                                        <label class="form-label small fw-bold text-main">Kode Barcode / QR / NIP:</label>
                                        <div class="input-group">
                                            <span class="input-group-text bg-transparent text-teal" style="color: #0F766E;"><i class="bi bi-upc"></i></span>
                                            <input type="text" id="quickModalCodeInput" class="form-control form-control-lg font-monospace" placeholder="Arahkan Barcode Scanner atau Ketik NIP..." autofocus autocomplete="off" style="background: var(--input-bg); color: var(--text-main); border-color: var(--border-color);">
                                            <button type="submit" class="btn text-white fw-bold px-4" style="background: #0F766E;">
                                                <i class="bi bi-arrow-return-left me-1"></i> Proses
                                            </button>
                                        </div>
                                    </div>
                                </form>

                                <!-- RESULT BOX -->
                                <div id="quickModalResultBox" class="p-3 rounded-3 border d-none mt-3" style="background: var(--input-bg); border-color: var(--border-color) !important;">
                                    <div class="d-flex align-items-center gap-3">
                                        <div class="p-2 rounded-circle bg-success text-white">
                                            <i class="bi bi-check-circle-fill fs-4"></i>
                                        </div>
                                        <div class="flex-grow-1 min-w-0">
                                            <h6 class="fw-bold text-success mb-0" id="quickModalResultName">Nama</h6>
                                            <div class="small text-sub" id="quickModalResultTime">Waktu</div>
                                            <span class="badge bg-success rounded-pill mt-1" id="quickModalResultBadge">Berhasil</span>
                                        </div>
                                    </div>
                                </div>
                            </div>
                            <div class="modal-footer border-0 pt-0 d-flex justify-content-end">
                                <button type="button" class="btn btn-outline-secondary rounded-pill px-4" onclick="closeQuickScanModal()">Selesai / Tutup</button>
                            </div>
                        </div>
                    </div>
                </div>
            `;

            setTimeout(() => {
                const inp = document.getElementById('quickModalCodeInput');
                if (inp) inp.focus();
            }, 100);
        }

        function updateQuickModalNotice() {
            const isPulang = document.getElementById('qScanPulang')?.checked;
            const noticeEl = document.getElementById('quickModalNoticeText');
            const config = getPresensiConfig();
            if (noticeEl) {
                if (isPulang) {
                    noticeEl.innerHTML = `Batas waktu pulang minimal: <strong>${dol}{config.batasPulang} WIB</strong>.`;
                } else {
                    noticeEl.innerHTML = `Batas waktu masuk: <strong>${dol}{config.batasMasuk} WIB</strong>.`;
                }
            }
        }

        async function handleQuickModalInputSubmit(e) {
            e.preventDefault();
            const input = document.getElementById('quickModalCodeInput');
            if (!input) return;

            const code = input.value.trim();
            if (!code) return;

            const isModePulang = document.getElementById('qScanPulang')?.checked || false;
            const selectedStatusRadio = document.querySelector('input[name="quickModalStatusChoice"]:checked');
            const statusPresensi = selectedStatusRadio ? selectedStatusRadio.value : 'Hadir';

            await processPresensiPayload(code, statusPresensi, isModePulang, 'Input Cepat / Barcode Scanner');

            const resultBox = document.getElementById('quickModalResultBox');
            const nameEl = document.getElementById('quickModalResultName');
            const timeEl = document.getElementById('quickModalResultTime');
            const badgeEl = document.getElementById('quickModalResultBadge');

            if (resultBox && nameEl && badgeEl) {
                nameEl.textContent = code.startsWith('SINGGHARA_MEMBER:') ? code.split(':')[2] : code;
                if (timeEl) timeEl.textContent = `Waktu: ${dol}{new Date().toLocaleTimeString('id-ID')}`;
                badgeEl.textContent = `${dol}{isModePulang ? 'Absen Pulang' : statusPresensi} Berhasil`;
                resultBox.classList.remove('d-none');
            }

            input.value = '';
            input.focus();
        }

        function closeQuickScanModal() {
            const el = document.getElementById('quickScanModal');
            if (el) el.remove();
        }

        // ====================================================
        // --- 3. MODAL: INPUT MANUAL LENGKAP MASUK & PULANG ---
        // ====================================================
        function openManualAbsensiModal() {
            const config = getPresensiConfig();
            const now = new Date();
            const nowTimeStr = now.toLocaleTimeString('id-ID', { hour: '2-digit', minute: '2-digit' });

            let container = document.getElementById('presensiModalsContainer');
            if (!container) {
                container = document.createElement('div');
                container.id = 'presensiModalsContainer';
                document.body.appendChild(container);
            }

            container.innerHTML = `
                <div class="modal fade show d-block" id="manualAbsensiModal" tabindex="-1" style="background: rgba(0,0,0,0.6); z-index: 1055;">
                    <div class="modal-dialog modal-dialog-centered modal-lg">
                        <div class="modal-content card-custom border-0 shadow-lg" style="border-radius: 20px; background: var(--modal-bg); color: var(--text-main); border: 1px solid var(--border-color);">
                            <div class="modal-header border-0 pb-0">
                                <h5 class="fw-bold text-main mb-0 d-flex align-items-center gap-2">
                                    <i class="bi bi-pencil-square text-teal" style="color: #0F766E;"></i> Form Input Presensi &amp; Pulang Manual
                                </h5>
                                <button type="button" class="btn-close btn-close-custom" onclick="closeManualAbsensiModal()"></button>
                            </div>
                            <div class="modal-body p-4">
                                <form id="formManualAbsensi" onsubmit="submitManualAbsensi(event)">
                                    <div class="row g-3 mb-3">
                                        <div class="col-12 col-md-6">
                                            <label class="form-label small fw-bold text-main">Pilih Anggota Terdaftar (Opsional)</label>
                                            <select class="form-select" id="manualSelectAnggota" onchange="handleSelectAnggotaForManual(this.value)" style="background: var(--input-bg); color: var(--text-main); border-color: var(--border-color);">
                                                <option value="">-- Ketik Manual atau Pilih --</option>
                                                ${dol}{window.anggotaList.map(a => `<option value="${dol}{a.id}">${dol}{escapeHtml(a.nama)} (${dol}{escapeHtml(a.nomorInduk || '-')}) - ${dol}{escapeHtml(a.status || 'Anggota')}</option>`).join('')}
                                            </select>
                                        </div>
                                        <div class="col-12 col-md-6">
                                            <label class="form-label small fw-bold text-main">Nama Lengkap *</label>
                                            <input type="text" class="form-control" id="manualNama" required placeholder="Contoh: Budi Pratama" style="background: var(--input-bg); color: var(--text-main); border-color: var(--border-color);">
                                        </div>
                                        <div class="col-6 col-md-4">
                                            <label class="form-label small fw-bold text-main">NIM / NIP</label>
                                            <input type="text" class="form-control" id="manualNip" placeholder="Contoh: KRY-001" style="background: var(--input-bg); color: var(--text-main); border-color: var(--border-color);">
                                        </div>
                                        <div class="col-6 col-md-4">
                                            <label class="form-label small fw-bold text-main">Divisi / Kelas</label>
                                            <input type="text" class="form-control" id="manualDivisi" placeholder="Contoh: IT / Akuntansi" style="background: var(--input-bg); color: var(--text-main); border-color: var(--border-color);">
                                        </div>
                                        <div class="col-12 col-md-4">
                                            <label class="form-label small fw-bold text-main">Peran / Status</label>
                                            <select class="form-select" id="manualStatusPeran" style="background: var(--input-bg); color: var(--text-main); border-color: var(--border-color);">
                                                <option value="Karyawan">Karyawan</option>
                                                <option value="Murid">Murid</option>
                                                <option value="Guru">Guru</option>
                                                <option value="Staff">Staff</option>
                                                <option value="Umum">Umum</option>
                                            </select>
                                        </div>
                                    </div>

                                    <div class="p-3 rounded-3 mb-3" style="background: rgba(15, 118, 110, 0.06); border: 1px solid rgba(15, 118, 110, 0.2);">
                                        <div class="fw-bold text-teal mb-2" style="color: #0F766E;"><i class="bi bi-clock me-1"></i>1. Waktu Masuk &amp; Batas Waktu:</div>
                                        <div class="row g-2">
                                            <div class="col-6">
                                                <label class="form-label small text-main">Jam Masuk (HH:mm)</label>
                                                <input type="time" class="form-control" id="manualWaktuMasuk" value="${dol}{nowTimeStr}" required style="background: var(--input-bg); color: var(--text-main); border-color: var(--border-color);">
                                            </div>
                                            <div class="col-6">
                                                <label class="form-label small text-main">Batas Jam Masuk (HH:mm)</label>
                                                <input type="time" class="form-control" id="manualBatasMasuk" value="${dol}{config.batasMasuk}" required style="background: var(--input-bg); color: var(--text-main); border-color: var(--border-color);">
                                            </div>
                                        </div>
                                    </div>

                                    <div class="p-3 rounded-3 mb-3" style="background: rgba(217, 119, 6, 0.06); border: 1px solid rgba(217, 119, 6, 0.2);">
                                        <div class="d-flex align-items-center justify-content-between mb-2">
                                            <div class="fw-bold text-warning" style="color: #d97706;"><i class="bi bi-box-arrow-left me-1"></i>2. Catat Waktu Pulang Sekaligus?</div>
                                            <div class="form-check form-switch">
                                                <input class="form-check-input" type="checkbox" id="checkCatatPulangSekaligus" onchange="togglePulangFields(this.checked)">
                                            </div>
                                        </div>
                                        <div id="pulangFieldsContainer" class="row g-2 d-none">
                                            <div class="col-6">
                                                <label class="form-label small text-main">Jam Pulang (HH:mm)</label>
                                                <input type="time" class="form-control" id="manualWaktuPulang" value="${dol}{config.batasPulang}" style="background: var(--input-bg); color: var(--text-main); border-color: var(--border-color);">
                                            </div>
                                            <div class="col-6">
                                                <label class="form-label small text-main">Batas Jam Pulang (HH:mm)</label>
                                                <input type="time" class="form-control" id="manualBatasPulang" value="${dol}{config.batasPulang}" style="background: var(--input-bg); color: var(--text-main); border-color: var(--border-color);">
                                            </div>
                                        </div>
                                    </div>

                                    <div class="mb-4">
                                        <label class="form-label small fw-bold text-main">Status Kehadiran:</label>
                                        <div class="d-flex gap-2">
                                            <input type="radio" class="btn-check" name="manualStatusKehadiran" id="mHadir" value="Hadir" checked>
                                            <label class="btn btn-outline-success btn-sm flex-grow-1" for="mHadir">Hadir</label>

                                            <input type="radio" class="btn-check" name="manualStatusKehadiran" id="mIzin" value="Izin">
                                            <label class="btn btn-outline-primary btn-sm flex-grow-1" for="mIzin">Izin</label>

                                            <input type="radio" class="btn-check" name="manualStatusKehadiran" id="mSakit" value="Sakit">
                                            <label class="btn btn-outline-warning btn-sm flex-grow-1" for="mSakit">Sakit</label>

                                            <input type="radio" class="btn-check" name="manualStatusKehadiran" id="mTerlambat" value="Terlambat">
                                            <label class="btn btn-outline-danger btn-sm flex-grow-1" for="mTerlambat">Terlambat</label>
                                        </div>
                                    </div>

                                    <div class="d-flex justify-content-end gap-2">
                                        <button type="button" class="btn btn-outline-secondary rounded-pill px-3" onclick="closeManualAbsensiModal()">Batal</button>
                                        <button type="submit" class="btn text-white rounded-pill px-4 fw-bold" style="background: #0F766E;">Simpan Data Absensi</button>
                                    </div>
                                </form>
                            </div>
                        </div>
                    </div>
                </div>
            `;
        }

        function closeManualAbsensiModal() {
            const el = document.getElementById('manualAbsensiModal');
            if (el) el.remove();
        }

        function handleSelectAnggotaForManual(anggotaId) {
            if (!anggotaId) return;
            const a = window.anggotaList.find(item => item.id == anggotaId);
            if (a) {
                document.getElementById('manualNama').value = a.nama || '';
                document.getElementById('manualNip').value = a.nomorInduk || '';
                document.getElementById('manualDivisi').value = a.divisiOrJurusan || '';
                document.getElementById('manualStatusPeran').value = a.status || 'Karyawan';
            }
        }

        function togglePulangFields(checked) {
            const el = document.getElementById('pulangFieldsContainer');
            if (el) {
                if (checked) el.classList.remove('d-none');
                else el.classList.add('d-none');
            }
        }

        async function submitManualAbsensi(e) {
            e.preventDefault();
            const nama = document.getElementById('manualNama').value.trim();
            const nip = document.getElementById('manualNip').value.trim() || 'MANUAL';
            const divisi = document.getElementById('manualDivisi').value.trim();
            const statusPeran = document.getElementById('manualStatusPeran').value;
            const waktuMasukStr = document.getElementById('manualWaktuMasuk').value;
            const batasMasukStr = document.getElementById('manualBatasMasuk').value;
            const isCatatPulang = document.getElementById('checkCatatPulangSekaligus')?.checked || false;
            const statusKehadiran = document.querySelector('input[name="manualStatusKehadiran"]:checked')?.value || 'Hadir';

            const masukMinutes = parseTimeToMinutes(waktuMasukStr);
            const batasMasukMinutes = parseTimeToMinutes(batasMasukStr);
            const isTerlambatMasuk = masukMinutes > batasMasukMinutes && statusKehadiran === 'Hadir';

            let isPulangAwal = false;
            let pulangMinutes = 0;
            let batasPulangMinutes = 0;
            let waktuPulangStr = '';
            let batasPulangStr = '';

            if (isCatatPulang) {
                waktuPulangStr = document.getElementById('manualWaktuPulang').value;
                batasPulangStr = document.getElementById('manualBatasPulang').value;
                pulangMinutes = parseTimeToMinutes(waktuPulangStr);
                batasPulangMinutes = parseTimeToMinutes(batasPulangStr);
                isPulangAwal = pulangMinutes < batasPulangMinutes;
            }

            let statusPelanggaran = 'Tepat Waktu';
            if (isTerlambatMasuk && isPulangAwal) statusPelanggaran = 'Terlambat & Pulang Cepat';
            else if (isTerlambatMasuk) statusPelanggaran = 'Terlambat Masuk';
            else if (isPulangAwal) statusPelanggaran = 'Pulang Lebih Awal';

            let catatanPelanggaran = '';
            if (isTerlambatMasuk) catatanPelanggaran += `Terlambat ${dol}{masukMinutes - batasMasukMinutes} mnt (Masuk: ${dol}{waktuMasukStr} > Batas: ${dol}{batasMasukStr}). `;
            if (isPulangAwal) catatanPelanggaran += `Pulang lebih awal ${dol}{batasPulangMinutes - pulangMinutes} mnt (Pulang: ${dol}{waktuPulangStr} < Batas: ${dol}{batasPulangStr}).`;

            const dMasuk = new Date();
            dMasuk.setHours(Math.floor(masukMinutes / 60), masukMinutes % 60, 0, 0);

            let waktuPulangTs = null;
            if (isCatatPulang) {
                const dPulang = new Date();
                dPulang.setHours(Math.floor(pulangMinutes / 60), pulangMinutes % 60, 0, 0);
                waktuPulangTs = dPulang.getTime();
            }

            const presensiData = {
                anggotaId: 0,
                nomorInduk: nip,
                nama: nama,
                status: statusPeran,
                divisi: divisi,
                statusPresensi: isTerlambatMasuk ? 'Terlambat' : statusKehadiran,
                timestamp: dMasuk.getTime(),
                waktuPulang: waktuPulangTs,
                batasWaktuMasuk: batasMasukStr,
                batasWaktuPulang: batasPulangStr || '17:00',
                statusPelanggaran: statusPelanggaran,
                catatanPelanggaran: catatanPelanggaran.trim(),
                metode: 'Input Manual',
                qrPayload: ''
            };

            const hasViolation = isTerlambatMasuk || isPulangAwal;
            if (hasViolation) {
                showViolationPopup({
                    title: '⚠️ PERINGATAN PELANGGARAN WAKTU ABSENSI',
                    nama: nama,
                    jenis: statusPelanggaran,
                    waktuInput: isTerlambatMasuk ? `Masuk: ${dol}{waktuMasukStr} WIB` : `Pulang: ${dol}{waktuPulangStr} WIB`,
                    batasWaktu: isTerlambatMasuk ? `Batas Masuk: ${dol}{batasMasukStr} WIB` : `Batas Pulang: ${dol}{batasPulangStr} WIB`,
                    selisih: isTerlambatMasuk ? `${dol}{masukMinutes - batasMasukMinutes} Menit` : `${dol}{batasPulangMinutes - pulangMinutes} Menit`,
                    pesan: `Waktu yang Anda masukkan melewati batas ketentuan operasional (${dol}{statusPelanggaran}).`,
                    onProceed: async () => {
                        await sendPresensiToServer(presensiData, nama, nip, statusPelanggaran);
                        closeManualAbsensiModal();
                    }
                });
            } else {
                await sendPresensiToServer(presensiData, nama, nip, statusKehadiran);
                closeManualAbsensiModal();
            }
        }

        // ====================================================
        // --- MODAL: CATAT WAKTU PULANG SATUAN ---
        // ====================================================
        function openCatatPulangModal(id) {
            const item = window.presensiList.find(p => p.id === id);
            if (!item) return;

            const config = getPresensiConfig();
            const now = new Date();
            const nowTimeStr = now.toLocaleTimeString('id-ID', { hour: '2-digit', minute: '2-digit' });
            const masukTimeStr = new Date(item.timestamp).toLocaleTimeString('id-ID', { hour: '2-digit', minute: '2-digit' });

            let container = document.getElementById('presensiModalsContainer');
            if (!container) {
                container = document.createElement('div');
                container.id = 'presensiModalsContainer';
                document.body.appendChild(container);
            }

            container.innerHTML = `
                <div class="modal fade show d-block" id="catatPulangModal" tabindex="-1" style="background: rgba(0,0,0,0.6); z-index: 1055;">
                    <div class="modal-dialog modal-dialog-centered">
                        <div class="modal-content card-custom border-0 shadow-lg" style="border-radius: 20px; background: var(--modal-bg); color: var(--text-main); border: 1px solid var(--border-color);">
                            <div class="modal-header border-0 pb-0">
                                <h5 class="fw-bold text-main mb-0 d-flex align-items-center gap-2">
                                    <i class="bi bi-box-arrow-left text-warning"></i> Catat Waktu Kepulangan
                                </h5>
                                <button type="button" class="btn-close btn-close-custom" onclick="closeCatatPulangModal()"></button>
                            </div>
                            <div class="modal-body p-4">
                                <div class="p-3 rounded-3 mb-3 border" style="background: var(--input-bg); border-color: var(--border-color) !important;">
                                    <h6 class="fw-bold text-main mb-1">${dol}{escapeHtml(item.nama)}</h6>
                                    <div class="small text-sub">${dol}{escapeHtml(item.nomorInduk || '-')} • ${dol}{escapeHtml(item.status || 'Anggota')}</div>
                                    <div class="small fw-semibold text-teal mt-1" style="color: #0F766E;">Waktu Masuk: ${dol}{masukTimeStr} WIB</div>
                                </div>

                                <form onsubmit="submitCatatPulangSingle(event, ${dol}{item.id})">
                                    <div class="mb-3">
                                        <label class="form-label small fw-bold text-main">Jam Pulang (HH:mm)</label>
                                        <input type="time" class="form-control" id="singleWaktuPulang" value="${dol}{nowTimeStr}" required style="background: var(--input-bg); color: var(--text-main); border-color: var(--border-color);">
                                    </div>
                                    <div class="mb-3">
                                        <label class="form-label small fw-bold text-main">Batas Jam Pulang Minimal (HH:mm)</label>
                                        <input type="time" class="form-control" id="singleBatasPulang" value="${dol}{item.batasWaktuPulang || config.batasPulang}" required style="background: var(--input-bg); color: var(--text-main); border-color: var(--border-color);">
                                        <div class="form-text small text-sub">Jika jam pulang mendahului batas waktu ini, akan dicatat sebagai Pulang Lebih Awal.</div>
                                    </div>
                                    <div class="d-flex justify-content-end gap-2 mt-4">
                                        <button type="button" class="btn btn-outline-secondary rounded-pill px-3" onclick="closeCatatPulangModal()">Batal</button>
                                        <button type="submit" class="btn text-white rounded-pill px-4 fw-bold" style="background: #0F766E;">Simpan Waktu Pulang</button>
                                    </div>
                                </form>
                            </div>
                        </div>
                    </div>
                </div>
            `;
        }

        function closeCatatPulangModal() {
            const el = document.getElementById('catatPulangModal');
            if (el) el.remove();
        }

        async function submitCatatPulangSingle(e, id) {
            e.preventDefault();
            const item = window.presensiList.find(p => p.id === id);
            if (!item) return;

            const waktuPulangStr = document.getElementById('singleWaktuPulang').value;
            const batasPulangStr = document.getElementById('singleBatasPulang').value;

            const pulangMinutes = parseTimeToMinutes(waktuPulangStr);
            const batasPulangMinutes = parseTimeToMinutes(batasPulangStr);
            const isPulangAwal = pulangMinutes < batasPulangMinutes;

            const isTerlambatMasuk = (item.statusPelanggaran || '').includes('Terlambat');
            let newPelanggaran = 'Tepat Waktu';
            if (isTerlambatMasuk && isPulangAwal) newPelanggaran = 'Terlambat & Pulang Cepat';
            else if (isTerlambatMasuk) newPelanggaran = 'Terlambat Masuk';
            else if (isPulangAwal) newPelanggaran = 'Pulang Lebih Awal';

            const extraCatatan = isPulangAwal ? `Pulang lebih awal ${dol}{batasPulangMinutes - pulangMinutes} mnt (Pulang: ${dol}{waktuPulangStr} < Batas: ${dol}{batasPulangStr}).` : '';
            const finalCatatan = [item.catatanPelanggaran, extraCatatan].filter(Boolean).join(' ');

            const dPulang = new Date();
            dPulang.setHours(Math.floor(pulangMinutes / 60), pulangMinutes % 60, 0, 0);

            const updateData = {
                action: 'record_pulang',
                id: id,
                waktuPulang: dPulang.getTime(),
                statusPelanggaran: newPelanggaran,
                catatanPelanggaran: finalCatatan
            };

            if (isPulangAwal) {
                showViolationPopup({
                    title: '⚠️ PERINGATAN PULANG LEBIH AWAL',
                    nama: item.nama,
                    jenis: 'Pulang Lebih Awal',
                    waktuInput: `Pulang: ${dol}{waktuPulangStr} WIB`,
                    batasWaktu: `Batas Pulang: ${dol}{batasPulangStr} WIB`,
                    selisih: `${dol}{batasPulangMinutes - pulangMinutes} Menit`,
                    pesan: `Anggota ${dol}{item.nama} pulang sebelum batas waktu ${dol}{batasPulangStr} WIB.`,
                    onProceed: async () => {
                        await sendUpdatePulangToServer(updateData, item.nama);
                        closeCatatPulangModal();
                    }
                });
            } else {
                await sendUpdatePulangToServer(updateData, item.nama);
                closeCatatPulangModal();
            }
        }

        // ====================================================
        // --- MODAL: DETAIL PRESENSI ---
        // ====================================================
        function openDetailPresensiModal(id) {
            const item = window.presensiList.find(p => p.id === id);
            if (!item) return;

            const masukStr = new Date(item.timestamp).toLocaleString('id-ID', { dateStyle: 'full', timeStyle: 'medium' });
            const pulangStr = item.waktuPulang ? new Date(item.waktuPulang).toLocaleString('id-ID', { dateStyle: 'full', timeStyle: 'medium' }) : 'Belum Dicatat / Belum Pulang';

            let container = document.getElementById('presensiModalsContainer');
            if (!container) {
                container = document.createElement('div');
                container.id = 'presensiModalsContainer';
                document.body.appendChild(container);
            }

            container.innerHTML = `
                <div class="modal fade show d-block" id="detailPresensiModal" tabindex="-1" style="background: rgba(0,0,0,0.6); z-index: 1055;">
                    <div class="modal-dialog modal-dialog-centered">
                        <div class="modal-content card-custom border-0 shadow-lg" style="border-radius: 20px; background: var(--modal-bg); color: var(--text-main); border: 1px solid var(--border-color);">
                            <div class="modal-header border-0 pb-0">
                                <h5 class="fw-bold text-main mb-0 d-flex align-items-center gap-2">
                                    <i class="bi bi-info-circle text-teal" style="color: #0F766E;"></i> Detail Riwayat Absensi
                                </h5>
                                <button type="button" class="btn-close btn-close-custom" onclick="closeDetailPresensiModal()"></button>
                            </div>
                            <div class="modal-body p-4">
                                <table class="table table-sm table-borderless mb-3" style="font-size: 13px;">
                                    <tr><td class="text-sub" style="width: 140px;">Nama Anggota:</td><td class="fw-bold text-main">${dol}{escapeHtml(item.nama)}</td></tr>
                                    <tr><td class="text-sub">NIM / NIP:</td><td class="font-monospace text-main">${dol}{escapeHtml(item.nomorInduk || '-')}</td></tr>
                                    <tr><td class="text-sub">Peran / Divisi:</td><td class="text-main">${dol}{escapeHtml(item.status || 'Anggota')}${dol}{item.divisi ? ' (' + escapeHtml(item.divisi) + ')' : ''}</td></tr>
                                    <tr><td class="text-sub">Status Kehadiran:</td><td><span class="badge bg-secondary">${dol}{escapeHtml(item.statusPresensi || 'Hadir')}</span></td></tr>
                                    <tr><td colspan="2"><hr class="my-1 opacity-25"></td></tr>
                                    <tr><td class="text-sub">Waktu Masuk:</td><td class="fw-bold text-teal" style="color: #0F766E;">${dol}{masukStr}</td></tr>
                                    <tr><td class="text-sub">Batas Jam Masuk:</td><td class="text-main">${dol}{escapeHtml(item.batasWaktuMasuk || '08:00')} WIB</td></tr>
                                    <tr><td class="text-sub">Waktu Pulang:</td><td class="fw-bold text-warning" style="color: #d97706;">${dol}{pulangStr}</td></tr>
                                    <tr><td class="text-sub">Batas Jam Pulang:</td><td class="text-main">${dol}{escapeHtml(item.batasWaktuPulang || '17:00')} WIB</td></tr>
                                    <tr><td colspan="2"><hr class="my-1 opacity-25"></td></tr>
                                    <tr><td class="text-sub">Status Pelanggaran:</td><td class="fw-bold text-danger">${dol}{escapeHtml(item.statusPelanggaran || 'Tepat Waktu')}</td></tr>
                                    ${dol}{item.catatanPelanggaran ? `<tr><td class="text-sub">Catatan Pelanggaran:</td><td class="small text-danger">${dol}{escapeHtml(item.catatanPelanggaran)}</td></tr>` : ''}
                                    <tr><td class="text-sub">Metode Input:</td><td class="text-main">${dol}{escapeHtml(item.metode || 'QR')}</td></tr>
                                </table>
                                <div class="d-flex justify-content-end">
                                    <button type="button" class="btn text-white rounded-pill px-4" style="background: #0F766E;" onclick="closeDetailPresensiModal()">Tutup</button>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            `;
        }

        function closeDetailPresensiModal() {
            const el = document.getElementById('detailPresensiModal');
            if (el) el.remove();
        }

        function showScanResultToast(nama, nip, status) {
            console.log('Presensi logged:', nama, nip, status);
        }

        async function deleteSinglePresensi(id) {
            if (!confirm('Hapus riwayat presensi ini?')) return;
            try {
                const res = await fetch(`/api/presensi?id=${dol}{id}`, { method: 'DELETE' });
                if (res.ok) {
                    await fetchPresensiData();
                    updatePresensiLiveUi();
                }
            } catch(e) {
                alert('Gagal menghapus log: ' + e.message);
            }
        }

        async function clearAllPresensiLog() {
            if (!confirm('Apakah Anda yakin ingin MENGHAPUS SEMUA riwayat presensi yang ada?')) return;
            try {
                const res = await fetch('/api/presensi?action=clear_all', { method: 'DELETE' });
                if (res.ok) {
                    await fetchPresensiData();
                    updatePresensiLiveUi();
                }
            } catch(e) {
                alert('Gagal membersihkan riwayat: ' + e.message);
            }
        }

        function exportPresensiCsv() {
            if (window.presensiList.length === 0) {
                alert('Belum ada riwayat presensi untuk diekspor.');
                return;
            }

            let csv = "No,Waktu Masuk,Waktu Pulang,Batas Masuk,Batas Pulang,Nama Anggota,NIM/NIP,Status/Peran,Divisi,Status Presensi,Status Pelanggaran,Catatan Pelanggaran,Metode\n";
            window.presensiList.forEach((p, idx) => {
                const dtMasuk = new Date(p.timestamp || Date.now()).toLocaleString('id-ID');
                const dtPulang = p.waktuPulang ? new Date(p.waktuPulang).toLocaleString('id-ID') : '-';
                csv += `"${dol}{idx + 1}","${dol}{dtMasuk}","${dol}{dtPulang}","${dol}{p.batasWaktuMasuk || '08:00'}","${dol}{p.batasWaktuPulang || '17:00'}","${dol}{p.nama || ''}","${dol}{p.nomorInduk || ''}","${dol}{p.status || ''}","${dol}{p.divisi || ''}","${dol}{p.statusPresensi || ''}","${dol}{p.statusPelanggaran || 'Tepat Waktu'}","${dol}{p.catatanPelanggaran || ''}","${dol}{p.metode || ''}"\n`;
            });

            const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
            const link = document.createElement('a');
            link.href = URL.createObjectURL(blob);
            link.download = `REKAP_PRESENSI_PELANGGARAN_${dol}{new Date().toISOString().slice(0,10)}.csv`;
            link.click();
        }

        // --- 4. SUB-TAB: CETAK MASSAL ID CARD (A4 SHEET) ---
        // ====================================================
        function renderBatchPrintSubTabHtml() {
            const list = window.anggotaList;

            return `
                <div class="card card-custom border-0 shadow-sm p-4" style="border-radius: 18px;">
                    <div class="d-flex flex-wrap justify-content-between align-items-center gap-2 mb-3">
                        <div>
                            <h5 class="fw-bold text-main mb-0 d-flex align-items-center gap-2">
                                <i class="bi bi-printer-fill text-teal" style="color: #0F766E;"></i> Cetak Massal Lembaran ID Card (A4 Layout)
                            </h5>
                            <p class="text-sub small mb-0">Pilih kartu anggota yang ingin dicetak sekaligus dalam format lembaran siap print</p>
                        </div>
                        <div class="d-flex align-items-center gap-2">
                            <button class="btn btn-outline-secondary btn-sm rounded-pill px-3" onclick="toggleSelectAllBatchPrint()">
                                <i class="bi bi-check-all me-1"></i> Pilih Semua
                            </button>
                            <button class="btn btn-sm text-white rounded-pill px-4 shadow-sm" style="background: #0F766E;" onclick="executeBatchPrint()">
                                <i class="bi bi-printer me-1"></i> Cetak Lembaran Terpilih
                            </button>
                        </div>
                    </div>

                    ${dol}{list.length === 0 ? `
                        <div class="text-center py-5 text-muted">
                            <i class="bi bi-card-heading fs-1 d-block mb-2"></i>
                            Belum ada data anggota untuk dicetak massal.
                        </div>
                    ` : `
                        <div class="row g-3">
                            ${dol}{list.map(item => `
                                <div class="col-12 col-sm-6 col-md-4 col-lg-3">
                                    <div class="card card-custom p-3 border shadow-sm h-100 d-flex flex-row align-items-center gap-3" style="border-radius: 12px; cursor: pointer;" onclick="toggleBatchItemCheck(${dol}{item.id})">
                                        <input type="checkbox" class="form-check-input mt-0 fs-5 batch-print-cb" id="batchCb_${dol}{item.id}" ${dol}{window.batchSelectedIds.has(item.id) ? 'checked' : ''} onclick="event.stopPropagation(); toggleBatchItemCheck(${dol}{item.id})">
                                        <div class="min-w-0 flex-grow-1">
                                            <div class="fw-bold text-main text-truncate">${dol}{escapeHtml(item.nama)}</div>
                                            <small class="text-sub font-monospace d-block">${dol}{escapeHtml(item.nomorInduk || '-')}</small>
                                            <span class="badge bg-secondary bg-opacity-25 text-main rounded-pill" style="font-size: 10px;">${dol}{escapeHtml(item.status || 'Anggota')}</span>
                                        </div>
                                    </div>
                                </div>
                            `).join('')}
                        </div>
                    `}
                </div>
            `;
        }

        function toggleBatchItemCheck(id) {
            if (window.batchSelectedIds.has(id)) {
                window.batchSelectedIds.delete(id);
            } else {
                window.batchSelectedIds.add(id);
            }
            const cb = document.getElementById('batchCb_' + id);
            if (cb) cb.checked = window.batchSelectedIds.has(id);
        }

        function toggleSelectAllBatchPrint() {
            if (window.batchSelectedIds.size === window.anggotaList.length) {
                window.batchSelectedIds.clear();
            } else {
                window.batchSelectedIds = new Set(window.anggotaList.map(a => a.id));
            }
            const container = document.getElementById('anggotaTabDynamicContent');
            if (container && window.anggotaActiveTab === 'batch_print') {
                container.innerHTML = renderBatchPrintSubTabHtml();
            }
        }

        function executeBatchPrint() {
            const selectedItems = window.anggotaList.filter(a => window.batchSelectedIds.has(a.id));
            if (selectedItems.length === 0) {
                alert('Silakan pilih minimal satu kartu anggota yang ingin dicetak.');
                return;
            }

            const ts = Date.now();
            const printWindow = window.open('', '_blank', 'width=950,height=850');
            const cardsHtml = selectedItems.map(item => `
                <div class="batch-card-box">
                    <img src="/api/idcard?id=${dol}{item.id}&side=front&t=${dol}{ts}" class="batch-card-img" alt="Depan">
                    <div class="batch-card-name">${dol}{escapeHtml(item.nama)}</div>
                </div>
            `).join('');

            printWindow.document.write(`
                <!DOCTYPE html>
                <html>
                <head>
                    <title>Cetak Massal ID Card Singghara (A4)</title>
                    <link href="/assets/css/bootstrap.min.css" rel="stylesheet">
                    <link rel="stylesheet" href="/assets/css/bootstrap-icons.css">
                    <style>
                        @media print {
                            body { margin: 0; padding: 10mm; background: #fff; }
                            .batch-page-container { display: flex; flex-wrap: wrap; gap: 8mm; justify-content: flex-start; }
                            .batch-card-box { break-inside: avoid; page-break-inside: avoid; margin-bottom: 6mm; text-align: center; }
                            .batch-card-img { width: 54mm; height: 86mm; border-radius: 4mm; border: 1px solid #cbd5e1; object-fit: contain; }
                            .batch-card-name { font-size: 8.5px; color: #475569; margin-top: 2mm; font-weight: bold; }
                            .no-print { display: none !important; }
                        }
                        body { background: #f1f5f9; padding: 20px; font-family: -apple-system, BlinkMacSystemFont, sans-serif; }
                        .batch-page-container { display: flex; flex-wrap: wrap; gap: 20px; justify-content: center; }
                        .batch-card-box { text-align: center; }
                        .batch-card-img { width: 54mm; height: 86mm; border-radius: 4mm; box-shadow: 0 4px 10px rgba(0,0,0,0.12); border: 1px solid #e2e8f0; background: #fff; object-fit: contain; }
                        .batch-card-name { font-size: 11px; color: #334155; margin-top: 6px; font-weight: bold; }
                    </style>
                </head>
                <body>
                    <div class="d-flex justify-content-between align-items-center mb-3 no-print">
                        <div>
                            <h6 class="fw-bold mb-0"><i class="bi bi-printer-fill me-1"></i> Lembaran Cetak ID Card (${dol}{selectedItems.length} Kartu)</h6>
                            <small class="text-muted">Format standar A4 siap cetak dan potong</small>
                        </div>
                        <button class="btn btn-primary btn-sm rounded-pill px-4" onclick="window.print()">
                            <i class="bi bi-printer me-1"></i> Cetak / Simpan PDF
                        </button>
                    </div>
                    <div class="batch-page-container">
                        ${dol}{cardsHtml}
                    </div>
                </body>
                </html>
            `);
            printWindow.document.close();
            setTimeout(() => {
                try {
                    printWindow.focus();
                    printWindow.print();
                } catch(e) {}
            }, 500);
        }

        // ====================================================
        // --- 5. FORM TAMBAH & EDIT ANGGOTA ---
        // ====================================================
        function showAddAnggotaModal() {
            document.getElementById('formAnggotaInput').reset();
            document.getElementById('anggotaInputId').value = "0";
            document.getElementById('anggotaInputFotoBase64').value = "";
            document.getElementById('anggotaInputTandaTanganBase64').value = "";
            document.getElementById('anggotaFormTitle').innerHTML = `<i class="bi bi-person-plus-fill me-2" style="color: #0F766E;"></i> Tambah Data Anggota Baru`;
            document.getElementById('anggotaInputInstansi').value = "My Singghara";
            document.getElementById('anggotaInputBerlaku').value = "Berlaku Selama Aktif";
            
            removeAnggotaFoto();
            clearAnggotaSignature();
            generateAutoNomorInduk();

            const modalEl = document.getElementById('modalAnggotaForm');
            if (modalEl) {
                const modal = new bootstrap.Modal(modalEl);
                modal.show();
                setTimeout(initAnggotaSigCanvas, 200);
            }
        }

        function showEditAnggotaModal(id) {
            const item = window.anggotaList.find(a => a.id === id);
            if (!item) return;

            document.getElementById('anggotaInputId').value = item.id;
            document.getElementById('anggotaInputNomorInduk').value = item.nomorInduk || '';
            document.getElementById('anggotaInputNama').value = item.nama || '';
            document.getElementById('anggotaInputStatus').value = item.status || 'Karyawan';
            document.getElementById('anggotaInputDivisi').value = item.divisiOrJurusan || '';
            document.getElementById('anggotaInputInstansi').value = item.instansi || 'My Singghara';
            document.getElementById('anggotaInputGender').value = item.jenisKelamin || 'Laki-laki';
            document.getElementById('anggotaInputBerlaku').value = item.berlakuHingga || 'Berlaku Selama Aktif';
            document.getElementById('anggotaInputKontak').value = item.kontak || item.emailOrTelp || '';
            document.getElementById('anggotaInputEmail').value = item.email || '';
            document.getElementById('anggotaInputAlamat').value = item.alamat || '';
            const elCatatan = document.getElementById('anggotaInputCatatanQr');
            if (elCatatan) elCatatan.value = item.catatanQr || '';
            document.getElementById('anggotaInputFotoBase64').value = item.fotoUri || '';
            document.getElementById('anggotaInputTandaTanganBase64').value = item.tandaTanganUri || '';

            if (item.fotoUri && item.fotoUri.trim().length > 0) {
                document.getElementById('anggotaFotoPreview').innerHTML = `<img src="${dol}{escapeHtml(item.fotoUri)}" class="w-100 h-100 rounded-circle object-fit-cover">`;
                const btnAdj = document.getElementById('btnAdjustPhoto');
                if (btnAdj) btnAdj.classList.remove('d-none');
            } else {
                removeAnggotaFoto();
            }

            if (item.tandaTanganUri && item.tandaTanganUri.trim().length > 0) {
                setTimeout(() => loadSignatureDataUrl(item.tandaTanganUri), 200);
            } else {
                clearAnggotaSignature();
                setTimeout(initAnggotaSigCanvas, 200);
            }

            document.getElementById('anggotaFormTitle').innerHTML = `<i class="bi bi-pencil-square me-2" style="color: #0F766E;"></i> Edit Data Anggota`;

            const modalEl = document.getElementById('modalAnggotaForm');
            if (modalEl) {
                const modal = new bootstrap.Modal(modalEl);
                modal.show();
            }
        }

        function handleRoleChangeAutoNip() {
            const id = parseInt(document.getElementById('anggotaInputId').value || '0');
            if (id === 0) {
                generateAutoNomorInduk();
            }
        }

        function generateAutoNomorInduk() {
            const role = (document.getElementById('anggotaInputStatus')?.value || 'Karyawan').toLowerCase();
            const year = new Date().getFullYear();
            const rand = Math.floor(1000 + Math.random() * 9000);
            let prefix = 'EMP';

            if (role.includes('murid') || role.includes('siswa')) prefix = 'NISN';
            else if (role.includes('guru') || role.includes('pendidik')) prefix = 'NUPTK';
            else if (role.includes('dosen')) prefix = 'NIDN';
            else if (role.includes('magang')) prefix = 'MGG';
            else if (role.includes('staf')) prefix = 'STF';

            const nipInput = document.getElementById('anggotaInputNomorInduk');
            if (nipInput) nipInput.value = `${dol}{prefix}-${dol}{year}-${dol}{rand}`;
        }

        // ====================================================
        // DIGITAL SIGNATURE ENGINE (PC WEB)
        // ====================================================
        window.sigCanvasInited = false;
        window.sigIsDrawing = false;
        window.sigHasDrawn = false;

        function initAnggotaSigCanvas() {
            const canvas = document.getElementById('anggotaSigCanvas');
            if (!canvas) return;

            // Set fixed high resolution coordinate space
            canvas.width = 600;
            canvas.height = 200;
            const ctx = canvas.getContext('2d');
            ctx.lineCap = 'round';
            ctx.lineJoin = 'round';
            ctx.strokeStyle = '#0F172A';
            ctx.lineWidth = 4.5;

            if (window.sigCanvasInited) return;
            window.sigCanvasInited = true;

            let lastX = 0;
            let lastY = 0;

            function getCanvasPos(e) {
                const rect = canvas.getBoundingClientRect();
                const clientX = e.touches ? e.touches[0].clientX : e.clientX;
                const clientY = e.touches ? e.touches[0].clientY : e.clientY;
                return {
                    x: (clientX - rect.left) * (canvas.width / rect.width),
                    y: (clientY - rect.top) * (canvas.height / rect.height)
                };
            }

            function startDraw(e) {
                if (e.target !== canvas) return;
                e.preventDefault();
                window.sigIsDrawing = true;
                const pos = getCanvasPos(e);
                lastX = pos.x;
                lastY = pos.y;

                ctx.beginPath();
                ctx.arc(lastX, lastY, 2, 0, Math.PI * 2);
                ctx.fillStyle = '#0F172A';
                ctx.fill();

                ctx.beginPath();
                ctx.moveTo(lastX, lastY);

                const ph = document.getElementById('anggotaSigPlaceholder');
                if (ph) ph.style.display = 'none';
            }

            function draw(e) {
                if (!window.sigIsDrawing) return;
                e.preventDefault();
                const pos = getCanvasPos(e);

                ctx.beginPath();
                ctx.moveTo(lastX, lastY);
                const midX = (lastX + pos.x) / 2;
                const midY = (lastY + pos.y) / 2;
                ctx.quadraticCurveTo(lastX, lastY, midX, midY);
                ctx.stroke();

                lastX = pos.x;
                lastY = pos.y;
                window.sigHasDrawn = true;
            }

            function stopDraw() {
                if (!window.sigIsDrawing) return;
                window.sigIsDrawing = false;
                if (window.sigHasDrawn) {
                    const dataUrl = canvas.toDataURL('image/png');
                    document.getElementById('anggotaInputTandaTanganBase64').value = dataUrl;
                }
            }

            canvas.addEventListener('mousedown', startDraw);
            canvas.addEventListener('mousemove', draw);
            window.addEventListener('mouseup', stopDraw);

            canvas.addEventListener('touchstart', startDraw, { passive: false });
            canvas.addEventListener('touchmove', draw, { passive: false });
            window.addEventListener('touchend', stopDraw);
        }

        function clearAnggotaSignature() {
            const canvas = document.getElementById('anggotaSigCanvas');
            if (canvas) {
                const ctx = canvas.getContext('2d');
                ctx.clearRect(0, 0, canvas.width, canvas.height);
            }
            document.getElementById('anggotaInputTandaTanganBase64').value = '';
            window.sigHasDrawn = false;
            const ph = document.getElementById('anggotaSigPlaceholder');
            if (ph) ph.style.display = 'block';
            const fileInput = document.getElementById('anggotaSigFileInput');
            if (fileInput) fileInput.value = '';
        }

        function handleAnggotaSigUpload(event) {
            const file = event.target.files[0];
            if (!file) return;
            const reader = new FileReader();
            reader.onload = function(e) {
                loadSignatureDataUrl(e.target.result);
            };
            reader.readAsDataURL(file);
        }

        function loadSignatureDataUrl(dataUrl) {
            if (!dataUrl || dataUrl.trim().length === 0) {
                clearAnggotaSignature();
                return;
            }
            const canvas = document.getElementById('anggotaSigCanvas');
            if (!canvas) return;
            initAnggotaSigCanvas();
            const ctx = canvas.getContext('2d');
            const img = new Image();
            img.onload = function() {
                ctx.clearRect(0, 0, canvas.width, canvas.height);
                const padding = 16;
                const maxW = canvas.width - padding * 2;
                const maxH = canvas.height - padding * 2;
                let scale = Math.min(maxW / img.width, maxH / img.height);
                if (scale > 1.2) scale = 1.2;
                const dw = img.width * scale;
                const dh = img.height * scale;
                const dx = (canvas.width - dw) / 2;
                const dy = (canvas.height - dh) / 2;
                ctx.drawImage(img, dx, dy, dw, dh);

                document.getElementById('anggotaInputTandaTanganBase64').value = dataUrl;
                window.sigHasDrawn = true;
                const ph = document.getElementById('anggotaSigPlaceholder');
                if (ph) ph.style.display = 'none';
            };
            img.src = dataUrl;
        }

        // ====================================================
        // PHOTO CROPPING & POSITION ADJUSTMENT ENGINE (PC WEB)
        // ====================================================
        window.cropRawImage = null;
        window.cropScale = 1.0;
        window.cropBaseScale = 1.0;
        window.cropOffsetX = 0;
        window.cropOffsetY = 0;
        window.cropRotation = 0;
        window.cropIsDragging = false;
        window.cropStartX = 0;
        window.cropStartY = 0;
        window.cropInitialX = 0;
        window.cropInitialY = 0;
        window.cropListenersInitialized = false;

        function handleAnggotaFotoUpload(event) {
            const file = event.target.files[0];
            if (!file) return;

            const reader = new FileReader();
            reader.onload = function(e) {
                loadPhotoIntoCropEngine(e.target.result);
            };
            reader.readAsDataURL(file);
        }

        function openPhotoCropModal() {
            const currentFoto = document.getElementById('anggotaInputFotoBase64')?.value;
            if (currentFoto && currentFoto.trim().length > 0) {
                loadPhotoIntoCropEngine(currentFoto);
            } else {
                document.getElementById('anggotaFileInput')?.click();
            }
        }

        function loadPhotoIntoCropEngine(imageSrc) {
            const img = new Image();
            img.onload = function() {
                window.cropRawImage = img;
                // Calculate base scale to fit 240px circle diameter
                const circleDiameter = 240;
                const minSide = Math.min(img.width, img.height);
                window.cropBaseScale = circleDiameter / Math.max(minSide, 1);
                window.cropScale = 1.0;
                window.cropOffsetX = 0;
                window.cropOffsetY = 0;
                window.cropRotation = 0;

                const slider = document.getElementById('cropZoomSlider');
                if (slider) slider.value = "1.0";

                const cropModalEl = document.getElementById('modalCropFotoWeb');
                if (cropModalEl) {
                    const modal = new bootstrap.Modal(cropModalEl);
                    modal.show();
                    setTimeout(() => {
                        initCropCanvasListeners();
                        drawCropCanvas();
                    }, 200);
                }
            };
            img.src = imageSrc;
        }

        function initCropCanvasListeners() {
            const canvas = document.getElementById('cropCanvas');
            const wrapper = document.getElementById('cropCanvasWrapper');
            if (!canvas || !wrapper || window.cropListenersInitialized) return;
            window.cropListenersInitialized = true;

            const onPointerDown = (clientX, clientY) => {
                window.cropIsDragging = true;
                window.cropStartX = clientX;
                window.cropStartY = clientY;
                window.cropInitialX = window.cropOffsetX;
                window.cropInitialY = window.cropOffsetY;
                wrapper.style.cursor = 'grabbing';
            };

            const onPointerMove = (clientX, clientY) => {
                if (!window.cropIsDragging) return;
                const dx = clientX - window.cropStartX;
                const dy = clientY - window.cropStartY;
                window.cropOffsetX = window.cropInitialX + dx;
                window.cropOffsetY = window.cropInitialY + dy;
                drawCropCanvas();
            };

            const onPointerUp = () => {
                window.cropIsDragging = false;
                wrapper.style.cursor = 'grab';
            };

            canvas.addEventListener('mousedown', (e) => onPointerDown(e.clientX, e.clientY));
            window.addEventListener('mousemove', (e) => { if (window.cropIsDragging) onPointerMove(e.clientX, e.clientY); });
            window.addEventListener('mouseup', onPointerUp);

            canvas.addEventListener('touchstart', (e) => {
                if (e.touches.length === 1) {
                    onPointerDown(e.touches[0].clientX, e.touches[0].clientY);
                }
            }, { passive: true });
            canvas.addEventListener('touchmove', (e) => {
                if (e.touches.length === 1) {
                    onPointerMove(e.touches[0].clientX, e.touches[0].clientY);
                }
            }, { passive: true });
            canvas.addEventListener('touchend', onPointerUp);

            canvas.addEventListener('wheel', (e) => {
                e.preventDefault();
                const delta = e.deltaY < 0 ? 0.08 : -0.08;
                stepCropZoom(delta);
            }, { passive: false });
        }

        function drawCropCanvas() {
            const canvas = document.getElementById('cropCanvas');
            if (!canvas || !window.cropRawImage) return;
            const ctx = canvas.getContext('2d');
            const w = canvas.width;
            const h = canvas.height;
            const centerX = w / 2;
            const centerY = h / 2;
            const radius = 120; // 240px diameter

            ctx.clearRect(0, 0, w, h);

            // 1. Draw transformed image
            ctx.save();
            ctx.translate(centerX + window.cropOffsetX, centerY + window.cropOffsetY);
            ctx.rotate((window.cropRotation * Math.PI) / 180);
            const totalScale = window.cropBaseScale * window.cropScale;
            ctx.scale(totalScale, totalScale);
            ctx.drawImage(
                window.cropRawImage,
                -window.cropRawImage.width / 2,
                -window.cropRawImage.height / 2,
                window.cropRawImage.width,
                window.cropRawImage.height
            );
            ctx.restore();

            // 2. Translucent Vignette / Mask outside circle
            ctx.save();
            ctx.fillStyle = 'rgba(15, 23, 42, 0.75)';
            ctx.beginPath();
            ctx.rect(0, 0, w, h);
            ctx.arc(centerX, centerY, radius, 0, Math.PI * 2, true);
            ctx.fill();
            ctx.restore();

            // 3. Gold circle guide ring
            ctx.save();
            ctx.beginPath();
            ctx.arc(centerX, centerY, radius, 0, Math.PI * 2);
            ctx.lineWidth = 3;
            ctx.strokeStyle = '#F59E0B';
            ctx.stroke();

            // Subtle Grid Crosshairs inside circle
            ctx.beginPath();
            ctx.strokeStyle = 'rgba(255, 255, 255, 0.25)';
            ctx.lineWidth = 1;
            ctx.setLineDash([4, 4]);
            ctx.moveTo(centerX - radius + 20, centerY);
            ctx.lineTo(centerX + radius - 20, centerY);
            ctx.moveTo(centerX, centerY - radius + 20);
            ctx.lineTo(centerX, centerY + radius - 20);
            ctx.stroke();
            ctx.restore();
        }

        function handleCropZoomChange(val) {
            window.cropScale = parseFloat(val) || 1.0;
            drawCropCanvas();
        }

        function stepCropZoom(delta) {
            const slider = document.getElementById('cropZoomSlider');
            let current = parseFloat(slider?.value || window.cropScale);
            current = Math.max(0.4, Math.min(3.0, current + delta));
            if (slider) slider.value = current.toFixed(2);
            window.cropScale = current;
            drawCropCanvas();
        }

        function rotateCropImage() {
            window.cropRotation = (window.cropRotation + 90) % 360;
            drawCropCanvas();
        }

        function resetCropTransform() {
            window.cropScale = 1.0;
            window.cropOffsetX = 0;
            window.cropOffsetY = 0;
            window.cropRotation = 0;
            const slider = document.getElementById('cropZoomSlider');
            if (slider) slider.value = "1.0";
            drawCropCanvas();
        }

        function applyCroppedPhoto() {
            if (!window.cropRawImage) return;

            // Render final crop to 512x512 canvas for high quality
            const targetSize = 512;
            const offCanvas = document.createElement('canvas');
            offCanvas.width = targetSize;
            offCanvas.height = targetSize;
            const ctx = offCanvas.getContext('2d');

            const scaleRatio = targetSize / (120 * 2); // mapping 240px circle to 512px
            const centerX = targetSize / 2;
            const centerY = targetSize / 2;

            // Draw circular clip
            ctx.save();
            ctx.beginPath();
            ctx.arc(centerX, centerY, targetSize / 2, 0, Math.PI * 2);
            ctx.clip();

            ctx.translate(centerX + (window.cropOffsetX * scaleRatio), centerY + (window.cropOffsetY * scaleRatio));
            ctx.rotate((window.cropRotation * Math.PI) / 180);
            const totalScale = window.cropBaseScale * window.cropScale * scaleRatio;
            ctx.scale(totalScale, totalScale);
            ctx.drawImage(
                window.cropRawImage,
                -window.cropRawImage.width / 2,
                -window.cropRawImage.height / 2,
                window.cropRawImage.width,
                window.cropRawImage.height
            );
            ctx.restore();

            const finalDataUrl = offCanvas.toDataURL('image/jpeg', 0.88);
            document.getElementById('anggotaInputFotoBase64').value = finalDataUrl;
            document.getElementById('anggotaFotoPreview').innerHTML = `<img src="${dol}{finalDataUrl}" class="w-100 h-100 rounded-circle object-fit-cover">`;
            
            const btnAdj = document.getElementById('btnAdjustPhoto');
            if (btnAdj) btnAdj.classList.remove('d-none');

            const cropModalEl = document.getElementById('modalCropFotoWeb');
            if (cropModalEl) {
                const modal = bootstrap.Modal.getInstance(cropModalEl);
                if (modal) modal.hide();
            }
        }

        function removeAnggotaFoto() {
            document.getElementById('anggotaInputFotoBase64').value = "";
            const fileInp = document.getElementById('anggotaFileInput');
            if (fileInp) fileInp.value = "";
            document.getElementById('anggotaFotoPreview').innerHTML = `<i class="bi bi-person-bounding-box text-secondary" style="font-size: 3.5rem;"></i>`;
            const btnAdj = document.getElementById('btnAdjustPhoto');
            if (btnAdj) btnAdj.classList.add('d-none');
            window.cropRawImage = null;
        }

        async function handleSaveAnggotaSubmit(event) {
            event.preventDefault();
            const id = parseInt(document.getElementById('anggotaInputId').value || '0');
            const nomorInduk = document.getElementById('anggotaInputNomorInduk').value.trim();
            const nama = document.getElementById('anggotaInputNama').value.trim();
            const status = document.getElementById('anggotaInputStatus').value;
            const divisi = document.getElementById('anggotaInputDivisi').value.trim();
            const instansi = document.getElementById('anggotaInputInstansi').value.trim() || 'My Singghara';
            const gender = document.getElementById('anggotaInputGender').value;
            const berlaku = document.getElementById('anggotaInputBerlaku').value.trim() || 'Berlaku Selama Aktif';
            const kontak = document.getElementById('anggotaInputKontak').value.trim();
            const email = document.getElementById('anggotaInputEmail').value.trim();
            const alamat = document.getElementById('anggotaInputAlamat').value.trim();
            const catatanQr = document.getElementById('anggotaInputCatatanQr') ? document.getElementById('anggotaInputCatatanQr').value.trim() : (id !== 0 && window.editingAnggotaItem ? (window.editingAnggotaItem.catatanQr || '') : '');
            const fotoUri = document.getElementById('anggotaInputFotoBase64').value.trim();
            let tandaTanganUri = document.getElementById('anggotaInputTandaTanganBase64').value.trim();

            const canvas = document.getElementById('anggotaSigCanvas');
            if (window.sigHasDrawn && canvas) {
                tandaTanganUri = canvas.toDataURL('image/png');
            }

            const qrPayload = `SINGGHARA_MEMBER:${dol}{nomorInduk}:${dol}{nama}:${dol}{status}${dol}{catatanQr ? ':' + catatanQr : ''}`;

            const payload = {
                id: id,
                nomorInduk: nomorInduk,
                nama: nama,
                status: status,
                divisiOrJurusan: divisi,
                instansi: instansi,
                jenisKelamin: gender,
                berlakuHingga: berlaku,
                kontak: kontak,
                email: email,
                alamat: alamat,
                catatanQr: catatanQr,
                fotoUri: fotoUri,
                tandaTanganUri: tandaTanganUri,
                qrCodeData: qrPayload
            };

            try {
                const method = id === 0 ? 'POST' : 'PUT';
                const res = await fetch('/api/anggota', {
                    method: method,
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(payload)
                });

                if (res.ok) {
                    const modalEl = document.getElementById('modalAnggotaForm');
                    if (modalEl) {
                        const modal = bootstrap.Modal.getInstance(modalEl);
                        if (modal) modal.hide();
                    }
                    await fetchAnggotaData();
                    renderKaryawanMuridMainView();
                } else {
                    const err = await res.json();
                    alert('Gagal menyimpan data: ' + (err.message || 'Terjadi kesalahan'));
                }
            } catch(e) {
                alert('Terjadi kesalahan jaringan: ' + e.message);
            }
        }

        async function deleteAnggotaItem(id, nama) {
            if (!confirm(`Apakah Anda yakin ingin menghapus data anggota "${dol}{nama}"?`)) return;
            try {
                const res = await fetch(`/api/anggota?id=${dol}{id}`, { method: 'DELETE' });
                if (res.ok) {
                    await fetchAnggotaData();
                    renderKaryawanMuridMainView();
                } else {
                    alert('Gagal menghapus anggota.');
                }
            } catch(e) {
                alert('Kesalahan: ' + e.message);
            }
        }

        // =========================================================================
        // CUSTOM DATE TIME PICKER BAWAAN APLIKASI (POP-UP INPUT DATA MASA BERLAKU)
        // =========================================================================
        window._pickerState = {
            isLifetime: true,
            year: new Date().getFullYear(),
            month: new Date().getMonth(),
            day: new Date().getDate(),
            includeTime: false,
            hour: 12,
            minute: 0
        };

        function openAnggotaDatePickerModal() {
            let modalEl = document.getElementById('anggotaDatePickerModal');
            if (!modalEl) {
                modalEl = document.createElement('div');
                modalEl.id = 'anggotaDatePickerModal';
                modalEl.className = 'modal fade';
                modalEl.tabIndex = -1;
                modalEl.setAttribute('aria-hidden', 'true');
                modalEl.innerHTML = `
                    <div class="modal-dialog modal-dialog-centered">
                        <div class="modal-content shadow-lg border-0 rounded-4 overflow-hidden" style="background-color: var(--modal-bg, #ffffff); color: var(--text-main, #1e293b);">
                            <!-- HEADER -->
                            <div class="modal-header border-bottom border-secondary border-opacity-10 px-4 py-3" style="background: rgba(15, 118, 110, 0.08);">
                                <div class="d-flex align-items-center gap-2">
                                    <div class="rounded-circle d-flex align-items-center justify-content-center" style="width: 36px; height: 36px; background: #0F766E; color: #ffffff;">
                                        <i class="bi bi-calendar-event-fill fs-6"></i>
                                    </div>
                                    <div>
                                        <h6 class="modal-title fw-bold mb-0" style="color: #0F766E;">Pilih Masa Berlaku ID Card</h6>
                                        <small class="text-secondary" style="font-size: 11px;">Date & Time Picker Bawaan Aplikasi</small>
                                    </div>
                                </div>
                                <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close"></button>
                            </div>

                            <!-- BODY -->
                            <div class="modal-body p-4">
                                <!-- PRESET CEPAT -->
                                <div class="mb-3">
                                    <label class="form-label small fw-bold text-secondary mb-2 d-flex align-items-center gap-1">
                                        <i class="bi bi-lightning-charge-fill text-warning"></i> Preset Cepat
                                    </label>
                                    <div class="d-flex flex-wrap gap-2">
                                        <button type="button" class="btn btn-sm rounded-pill px-3 py-1 fw-bold" id="btnPresetLifetime" onclick="setPickerPreset('lifetime')" style="border: 1px solid #0F766E; background: #0F766E; color: #ffffff;">
                                            <i class="bi bi-infinity me-1"></i> Selama Aktif
                                        </button>
                                        <button type="button" class="btn btn-sm btn-outline-secondary rounded-pill px-3 py-1 fw-bold" onclick="setPickerPreset('6m')">
                                            +6 Bulan
                                        </button>
                                        <button type="button" class="btn btn-sm btn-outline-secondary rounded-pill px-3 py-1 fw-bold" onclick="setPickerPreset('1y')">
                                            +1 Tahun
                                        </button>
                                        <button type="button" class="btn btn-sm btn-outline-secondary rounded-pill px-3 py-1 fw-bold" onclick="setPickerPreset('2y')">
                                            +2 Tahun
                                        </button>
                                        <button type="button" class="btn btn-sm btn-outline-secondary rounded-pill px-3 py-1 fw-bold" onclick="setPickerPreset('3y')">
                                            +3 Tahun
                                        </button>
                                        <button type="button" class="btn btn-sm btn-outline-secondary rounded-pill px-3 py-1 fw-bold" onclick="setPickerPreset('5y')">
                                            +5 Tahun
                                        </button>
                                    </div>
                                </div>

                                <!-- KALENDER INTERAKTIF KUSTOM -->
                                <div class="card card-custom p-3 mb-3 border rounded-3" style="background-color: var(--card-bg);">
                                    <div class="d-flex justify-content-between align-items-center mb-2">
                                        <button type="button" class="btn btn-sm btn-light rounded-circle p-1 border" onclick="prevPickerMonth()" title="Bulan Sebelumnya">
                                            <i class="bi bi-chevron-left"></i>
                                        </button>
                                        <div class="d-flex align-items-center gap-2">
                                            <select id="pickerMonthSelect" class="form-select form-select-sm py-1 fw-bold border-0 bg-transparent" style="cursor: pointer;" onchange="changePickerMonthYear()">
                                                <option value="0">Januari</option>
                                                <option value="1">Februari</option>
                                                <option value="2">Maret</option>
                                                <option value="3">April</option>
                                                <option value="4">Mei</option>
                                                <option value="5">Juni</option>
                                                <option value="6">Juli</option>
                                                <option value="7">Agustus</option>
                                                <option value="8">September</option>
                                                <option value="9">Oktober</option>
                                                <option value="10">November</option>
                                                <option value="11">Desember</option>
                                            </select>
                                            <select id="pickerYearSelect" class="form-select form-select-sm py-1 fw-bold border-0 bg-transparent" style="cursor: pointer;" onchange="changePickerMonthYear()">
                                            </select>
                                        </div>
                                        <button type="button" class="btn btn-sm btn-light rounded-circle p-1 border" onclick="nextPickerMonth()" title="Bulan Berikutnya">
                                            <i class="bi bi-chevron-right"></i>
                                        </button>
                                    </div>

                                    <!-- DAYS HEADER -->
                                    <div class="d-grid text-center small fw-bold text-secondary mb-1" style="grid-template-columns: repeat(7, 1fr); font-size: 11px;">
                                        <div class="text-danger">Min</div>
                                        <div>Sen</div>
                                        <div>Sel</div>
                                        <div>Rab</div>
                                        <div>Kam</div>
                                        <div>Jum</div>
                                        <div>Sab</div>
                                    </div>

                                    <!-- DAYS GRID -->
                                    <div id="pickerDaysGrid" class="d-grid text-center gap-1" style="grid-template-columns: repeat(7, 1fr);">
                                    </div>
                                </div>

                                <!-- OPSIONAL: TIME PICKER (JAM & MENIT) -->
                                <div class="card card-custom p-3 border rounded-3 mb-3" style="background-color: var(--card-bg);">
                                    <div class="d-flex justify-content-between align-items-center">
                                        <div class="d-flex align-items-center gap-2">
                                            <i class="bi bi-clock-history text-teal" style="color: #0F766E;"></i>
                                            <label class="form-check-label small fw-bold cursor-pointer" for="pickerTimeToggle">Sertakan Waktu Jam & Menit</label>
                                        </div>
                                        <div class="form-check form-switch m-0">
                                            <input class="form-check-input cursor-pointer" type="checkbox" id="pickerTimeToggle" onchange="togglePickerTime(this.checked)">
                                        </div>
                                    </div>
                                    <div id="pickerTimeWrapper" class="mt-2 pt-2 border-top border-secondary border-opacity-10 d-none">
                                        <div class="d-flex align-items-center justify-content-center gap-2">
                                            <div>
                                                <label class="form-label small text-secondary mb-0" style="font-size: 10px;">Jam (00-23)</label>
                                                <input type="number" id="pickerHourInput" min="0" max="23" class="form-control form-control-sm text-center fw-bold" style="width: 70px;" value="12" onchange="updatePickerTimeVals()">
                                            </div>
                                            <span class="fw-bold fs-5 mt-3">:</span>
                                            <div>
                                                <label class="form-label small text-secondary mb-0" style="font-size: 10px;">Menit (00-59)</label>
                                                <input type="number" id="pickerMinuteInput" min="0" max="59" class="form-control form-control-sm text-center fw-bold" style="width: 70px;" value="00" onchange="updatePickerTimeVals()">
                                            </div>
                                        </div>
                                    </div>
                                </div>

                                <!-- PREVIEW HASIL AKHIR -->
                                <div class="p-3 rounded-3 border d-flex align-items-center justify-content-between" style="background: rgba(15, 118, 110, 0.06); border-color: rgba(15, 118, 110, 0.25) !important;">
                                    <span class="small fw-bold text-secondary">Hasil Masa Berlaku:</span>
                                    <span class="fw-bold text-teal" id="pickerPreviewText" style="color: #0F766E; font-size: 14px;">Berlaku Selama Aktif</span>
                                </div>
                            </div>

                            <!-- FOOTER -->
                            <div class="modal-footer border-top border-secondary border-opacity-10 px-4 py-3">
                                <button type="button" class="btn btn-sm btn-outline-secondary rounded-pill px-3 fw-bold" data-bs-dismiss="modal">Batal</button>
                                <button type="button" class="btn btn-sm btn-primary rounded-pill px-4 fw-bold shadow-sm d-flex align-items-center gap-1" onclick="applyAnggotaDatePicker()" style="background-color: #0F766E; border-color: #0F766E;">
                                    <i class="bi bi-check2-circle fs-6"></i>
                                    <span>Terapkan Masa Berlaku</span>
                                </button>
                            </div>
                        </div>
                    </div>
                `;
                document.body.appendChild(modalEl);

                // Populate Years Dropdown (Current year up to +15 years)
                const yrSel = document.getElementById('pickerYearSelect');
                const curY = new Date().getFullYear();
                for (let y = curY; y <= curY + 15; y++) {
                    const opt = document.createElement('option');
                    opt.value = y;
                    opt.textContent = y;
                    yrSel.appendChild(opt);
                }
            }

            // Parse existing value in anggotaInputBerlaku if present
            const curVal = (document.getElementById('anggotaInputBerlaku').value || '').trim();
            if (curVal && curVal !== 'Berlaku Selama Aktif') {
                const parts = curVal.split(' ');
                const datePart = parts[0];
                const timePart = parts[1] || '';
                const dParts = datePart.split('/');
                if (dParts.length === 3) {
                    const d = parseInt(dParts[0], 10);
                    const m = parseInt(dParts[1], 10) - 1;
                    const y = parseInt(dParts[2], 10);
                    if (!isNaN(d) && !isNaN(m) && !isNaN(y)) {
                        window._pickerState.isLifetime = false;
                        window._pickerState.year = y;
                        window._pickerState.month = m;
                        window._pickerState.day = d;
                    }
                }
                if (timePart && timePart.includes(':')) {
                    const tParts = timePart.split(':');
                    window._pickerState.includeTime = true;
                    window._pickerState.hour = parseInt(tParts[0], 10) || 12;
                    window._pickerState.minute = parseInt(tParts[1], 10) || 0;
                } else {
                    window._pickerState.includeTime = false;
                }
            } else {
                window._pickerState.isLifetime = true;
            }

            renderPickerCalendarUI();

            const modal = new bootstrap.Modal(modalEl);
            modal.show();
        }

        function renderPickerCalendarUI() {
            const st = window._pickerState;
            const mSel = document.getElementById('pickerMonthSelect');
            const ySel = document.getElementById('pickerYearSelect');
            const tToggle = document.getElementById('pickerTimeToggle');
            const tWrap = document.getElementById('pickerTimeWrapper');
            const hInput = document.getElementById('pickerHourInput');
            const minInput = document.getElementById('pickerMinuteInput');
            const prevText = document.getElementById('pickerPreviewText');
            const btnLifetime = document.getElementById('btnPresetLifetime');

            if (mSel) mSel.value = st.month;
            if (ySel) ySel.value = st.year;
            if (tToggle) tToggle.checked = st.includeTime;
            if (tWrap) {
                if (st.includeTime && !st.isLifetime) tWrap.classList.remove('d-none');
                else tWrap.classList.add('d-none');
            }
            if (hInput) hInput.value = String(st.hour).padStart(2, '0');
            if (minInput) minInput.value = String(st.minute).padStart(2, '0');

            if (btnLifetime) {
                if (st.isLifetime) {
                    btnLifetime.style.background = '#0F766E';
                    btnLifetime.style.color = '#ffffff';
                    btnLifetime.style.borderColor = '#0F766E';
                } else {
                    btnLifetime.style.background = 'transparent';
                    btnLifetime.style.color = '#64748b';
                    btnLifetime.style.borderColor = '#cbd5e1';
                }
            }

            // Build calendar grid
            const grid = document.getElementById('pickerDaysGrid');
            if (grid) {
                grid.innerHTML = '';
                const firstDay = new Date(st.year, st.month, 1).getDay();
                const totalDays = new Date(st.year, st.month + 1, 0).getDate();

                // Empty cells before first day of month
                for (let i = 0; i < firstDay; i++) {
                    const emptyCell = document.createElement('div');
                    emptyCell.className = 'p-2';
                    grid.appendChild(emptyCell);
                }

                // Days cells
                for (let d = 1; d <= totalDays; d++) {
                    const isSelected = !st.isLifetime && st.day === d;
                    const cell = document.createElement('button');
                    cell.type = 'button';
                    cell.className = 'btn btn-sm p-1 rounded-circle fw-bold ' + (isSelected ? 'btn-primary text-white shadow-sm' : 'btn-light');
                    if (isSelected) {
                        cell.style.backgroundColor = '#0F766E';
                        cell.style.borderColor = '#0F766E';
                    }
                    cell.style.width = '36px';
                    cell.style.height = '36px';
                    cell.textContent = d;
                    cell.onclick = () => {
                        st.isLifetime = false;
                        st.day = d;
                        renderPickerCalendarUI();
                    };
                    grid.appendChild(cell);
                }
            }

            // Update preview text
            if (prevText) {
                if (st.isLifetime) {
                    prevText.textContent = 'Berlaku Selama Aktif';
                } else {
                    const dd = String(st.day).padStart(2, '0');
                    const mm = String(st.month + 1).padStart(2, '0');
                    const yyyy = st.year;
                    let txt = `${dol}{dd}/${dol}{mm}/${dol}{yyyy}`;
                    if (st.includeTime) {
                        const hh = String(st.hour).padStart(2, '0');
                        const mi = String(st.minute).padStart(2, '0');
                        txt += ` ${dol}{hh}:${dol}{mi}`;
                    }
                    prevText.textContent = txt;
                }
            }
        }

        function setPickerPreset(preset) {
            const st = window._pickerState;
            const now = new Date();
            if (preset === 'lifetime') {
                st.isLifetime = true;
            } else if (preset === '6m') {
                st.isLifetime = false;
                const d = new Date(now.getFullYear(), now.getMonth() + 6, now.getDate());
                st.year = d.getFullYear();
                st.month = d.getMonth();
                st.day = d.getDate();
            } else if (preset === '1y') {
                st.isLifetime = false;
                const d = new Date(now.getFullYear() + 1, now.getMonth(), now.getDate());
                st.year = d.getFullYear();
                st.month = d.getMonth();
                st.day = d.getDate();
            } else if (preset === '2y') {
                st.isLifetime = false;
                const d = new Date(now.getFullYear() + 2, now.getMonth(), now.getDate());
                st.year = d.getFullYear();
                st.month = d.getMonth();
                st.day = d.getDate();
            } else if (preset === '3y') {
                st.isLifetime = false;
                const d = new Date(now.getFullYear() + 3, now.getMonth(), now.getDate());
                st.year = d.getFullYear();
                st.month = d.getMonth();
                st.day = d.getDate();
            } else if (preset === '5y') {
                st.isLifetime = false;
                const d = new Date(now.getFullYear() + 5, now.getMonth(), now.getDate());
                st.year = d.getFullYear();
                st.month = d.getMonth();
                st.day = d.getDate();
            }
            renderPickerCalendarUI();
        }

        function prevPickerMonth() {
            const st = window._pickerState;
            st.month--;
            if (st.month < 0) {
                st.month = 11;
                st.year--;
            }
            renderPickerCalendarUI();
        }

        function nextPickerMonth() {
            const st = window._pickerState;
            st.month++;
            if (st.month > 11) {
                st.month = 0;
                st.year++;
            }
            renderPickerCalendarUI();
        }

        function changePickerMonthYear() {
            const st = window._pickerState;
            const mSel = document.getElementById('pickerMonthSelect');
            const ySel = document.getElementById('pickerYearSelect');
            if (mSel) st.month = parseInt(mSel.value, 10);
            if (ySel) st.year = parseInt(ySel.value, 10);
            renderPickerCalendarUI();
        }

        function togglePickerTime(isChecked) {
            window._pickerState.includeTime = isChecked;
            renderPickerCalendarUI();
        }

        function updatePickerTimeVals() {
            const st = window._pickerState;
            const h = parseInt(document.getElementById('pickerHourInput').value, 10);
            const m = parseInt(document.getElementById('pickerMinuteInput').value, 10);
            st.hour = isNaN(h) ? 12 : Math.max(0, Math.min(23, h));
            st.minute = isNaN(m) ? 0 : Math.max(0, Math.min(59, m));
            renderPickerCalendarUI();
        }

        function applyAnggotaDatePicker() {
            const st = window._pickerState;
            const input = document.getElementById('anggotaInputBerlaku');
            if (input) {
                if (st.isLifetime) {
                    input.value = 'Berlaku Selama Aktif';
                } else {
                    const dd = String(st.day).padStart(2, '0');
                    const mm = String(st.month + 1).padStart(2, '0');
                    const yyyy = st.year;
                    let txt = `${dol}{dd}/${dol}{mm}/${dol}{yyyy}`;
                    if (st.includeTime) {
                        const hh = String(st.hour).padStart(2, '0');
                        const mi = String(st.minute).padStart(2, '0');
                        txt += ` ${dol}{hh}:${dol}{mi}`;
                    }
                    input.value = txt;
                }
            }
            const modalEl = document.getElementById('anggotaDatePickerModal');
            if (modalEl) {
                const modal = bootstrap.Modal.getInstance(modalEl);
                if (modal) modal.hide();
            }
        }

        // Auto start sync on script load
        try {
            startCompanyAutoSync();
        } catch(e) {}
        """.trimIndent()
    }
}
