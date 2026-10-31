package com.example.web

object ArisanWebScript {
    fun getScript(dol: String): String {
        return """
        // =======================================================
        // --- MODUL MANAJEMEN KEUANGAN ARISAN KELUARGA (WEB PC) ---
        // =======================================================

        window.arisanData = {
            config: {
                namaArisan: "Arisan Keluarga besar singghara",
                periodeAktif: 1,
                iuranPokokPerSlot: 100000.0,
                iuranKasPerSlot: 20000.0,
                jadwalPertemuanBerikutnya: "",
                waktuPertemuanBerikutnya: "",
                lokasiPertemuanBerikutnya: "",
                namaKetuaArisan: "",
                namaBendahara: "",
                catatanPertemuan: ""
            },
            anggotaList: [],
            pembayaranList: [],
            kasSosialList: [],
            riwayatPemenangList: [],
            siklusKe: 1
        };

        window.arisanSection = 'RINGKASAN'; // RINGKASAN, IURAN, KAS_SOSIAL, PESERTA, RIWAYAT
        window.arisanIsRolling = false;
        window.arisanIuranSearch = '';
        window.arisanIuranStatusFilter = 'ALL';
        window.arisanPesertaSearch = '';
        window.arisanPesertaFilter = 'ALL';
        window.arisanKasSearch = '';
        window.arisanKasFilter = 'ALL';
        window.arisanRiwayatSearch = '';
        window.activeReceiptData = null;

        // Indonesian Terbilang (Spelling out amount in Indonesian words)
        function terbilangAngkaArisan(nilai) {
            const num = Math.floor(Math.abs(Number(nilai) || 0));
            const satuan = ["", "Satu", "Dua", "Tiga", "Empat", "Lima", "Enam", "Tujuh", "Delapan", "Sembilan", "Sepuluh", "Sebelas"];
            
            function convert(n) {
                if (n === 0) return "";
                if (n < 12) return satuan[n] + " ";
                if (n < 20) return convert(n - 10) + "Belas ";
                if (n < 100) return convert(Math.floor(n / 10)) + "Puluh " + convert(n % 10);
                if (n < 200) return "Seratus " + convert(n - 100);
                if (n < 1000) return convert(Math.floor(n / 100)) + "Ratus " + convert(n % 100);
                if (n < 2000) return "Seribu " + convert(n - 1000);
                if (n < 1000000) return convert(Math.floor(n / 1000)) + "Ribu " + convert(n % 1000);
                if (n < 1000000000) return convert(Math.floor(n / 1000000)) + "Juta " + convert(n % 1000000);
                if (n < 1000000000000) return convert(Math.floor(n / 1000000000)) + "Miliar " + convert(n % 1000000000);
                return convert(Math.floor(n / 1000000000000)) + "Triliun " + convert(n % 1000000000000);
            }
            
            if (num === 0) return "Nol Rupiah";
            const res = convert(num).replace(/\s+/g, " ").trim();
            return (nilai < 0 ? "Minus " : "") + res + " Rupiah";
        }

        function formatRupiahArisanWeb(amount) {
            const num = Number(amount) || 0;
            return 'Rp ' + num.toLocaleString('id-ID');
        }

        function escapeHtmlArisan(str) {
            if (str === null || str === undefined) return '';
            return String(str)
                .replace(/&/g, '&amp;')
                .replace(/</g, '&lt;')
                .replace(/>/g, '&gt;')
                .replace(/"/g, '&quot;')
                .replace(/'/g, '&#039;');
        }

        function escapeCsvArisan(str) {
            if (str === null || str === undefined) return '""';
            const s = String(str).replace(/"/g, '""');
            return '"' + s + '"';
        }

        function generateArisanUuid() {
            return 'ars_' + Date.now().toString(36) + '_' + Math.random().toString(36).substr(2, 6);
        }

        async function fetchArisanData() {
            try {
                const res = await fetch('/api/arisan');
                if (res.ok) {
                    const data = await res.json();
                    if (data && data.config) {
                        window.arisanData = {
                            config: {
                                namaArisan: data.config.namaArisan || "Arisan Keluarga besar singghara",
                                periodeAktif: Number(data.config.periodeAktif) || 1,
                                iuranPokokPerSlot: Number(data.config.iuranPokokPerSlot) || 100000.0,
                                iuranKasPerSlot: Number(data.config.iuranKasPerSlot) || 20000.0,
                                jadwalPertemuanBerikutnya: data.config.jadwalPertemuanBerikutnya || "",
                                waktuPertemuanBerikutnya: data.config.waktuPertemuanBerikutnya || "",
                                lokasiPertemuanBerikutnya: data.config.lokasiPertemuanBerikutnya || "",
                                namaKetuaArisan: data.config.namaKetuaArisan || "",
                                namaBendahara: data.config.namaBendahara || "",
                                catatanPertemuan: data.config.catatanPertemuan || ""
                            },
                            anggotaList: Array.isArray(data.anggotaList) ? data.anggotaList : [],
                            pembayaranList: Array.isArray(data.pembayaranList) ? data.pembayaranList : [],
                            kasSosialList: Array.isArray(data.kasSosialList) ? data.kasSosialList : [],
                            riwayatPemenangList: Array.isArray(data.riwayatPemenangList) ? data.riwayatPemenangList : [],
                            siklusKe: Number(data.siklusKe) || 1
                        };
                    }
                }
            } catch (e) {
                console.log('Arisan fetch fallback to default state', e);
            }
        }

        async function saveArisanDataToServer() {
            try {
                await fetch('/api/arisan', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(window.arisanData)
                });
            } catch (e) {
                console.log('Failed saving arisan to server', e);
            }
        }

        function loadArisanKeluarga() {
            fetchArisanData().then(() => {
                renderArisanMainView();
            });
        }

        function setArisanSection(section) {
            window.arisanSection = section;
            renderArisanMainView();
        }

        // --- RENDER MAIN VIEW ---
        function renderArisanMainView() {
            const container = document.getElementById('tab-content') || document.getElementById('main-content');
            if (!container) return;

            const cfg = window.arisanData.config || {};
            const anggota = window.arisanData.anggotaList || [];
            const pembayaran = window.arisanData.pembayaranList || [];
            const kas = window.arisanData.kasSosialList || [];
            const riwayat = window.arisanData.riwayatPemenangList || [];

            const totalSlots = anggota.reduce((acc, m) => acc + (Number(m.jumlahSlot) || 1), 0);
            const totalTarikanPerPutaran = totalSlots * (Number(cfg.iuranPokokPerSlot) || 0);

            const totalKasMasuk = kas.filter(k => k.jenis === 'Masuk').reduce((acc, k) => acc + (Number(k.nominal) || 0), 0);
            const totalKasKeluar = kas.filter(k => k.jenis === 'Keluar').reduce((acc, k) => acc + (Number(k.nominal) || 0), 0);
            const saldoKasSosial = totalKasMasuk - totalKasKeluar;

            const totalSudahMenang = anggota.filter(m => m.sudahMenang).length;
            const totalBelumMenang = anggota.filter(m => !m.sudahMenang).length;

            let sectionTitle = "Ringkasan & Kocokan";
            let sectionIcon = "bi-dice-5-fill";
            if (window.arisanSection === 'IURAN') {
                sectionTitle = "Iuran & Pembayaran";
                sectionIcon = "bi-credit-card-2-front-fill";
            } else if (window.arisanSection === 'KAS_SOSIAL') {
                sectionTitle = "Buku Kas Sosial";
                sectionIcon = "bi-wallet2";
            } else if (window.arisanSection === 'PESERTA') {
                sectionTitle = "Daftar Peserta & Anggota";
                sectionIcon = "bi-people-fill";
            } else if (window.arisanSection === 'RIWAYAT') {
                sectionTitle = "Riwayat Pemenang & Kuitansi";
                sectionIcon = "bi-receipt-cutoff";
            }

            let html = `
                <div class="container-fluid py-2 px-3" style="max-width: 1200px;">
                    <!-- HEADER CARD (Sama persis My Singghara dengan Card Aksen & 3-Dots Overflow Menu) -->
                    <div class="card border rounded-3 mb-3 shadow-sm" style="background-color: var(--card-bg, #ffffff); border-color: var(--border-color, #e2e8f0) !important;">
                        <div class="card-body p-3">
                            <div class="d-flex align-items-center justify-content-between">
                                <div>
                                    <h5 class="fw-bold mb-0 text-truncate" style="color: var(--text-main, #0f172a); font-size: 1.05rem;">Arisan Keluarga</h5>
                                    <div class="small text-truncate" style="color: var(--text-sub, #64748b); font-size: 0.8rem;">
                                        ${dol}{escapeHtmlArisan(cfg.namaArisan || 'Arisan Keluarga besar singghara')}
                                    </div>
                                    <div class="d-flex align-items-center gap-1 mt-1 text-primary small fw-semibold" style="font-size: 0.8rem;">
                                        <i class="bi ${dol}{sectionIcon}"></i>
                                        <span>${dol}{sectionTitle}</span>
                                    </div>
                                </div>

                                <!-- 3-Dots Menu Dropdown (Pilihan Menu & Navigasi) -->
                                <div class="dropdown">
                                    <button class="btn btn-sm btn-outline-secondary rounded-circle d-flex align-items-center justify-content-center p-2" type="button" data-bs-toggle="dropdown" aria-expanded="false" style="width: 36px; height: 36px; border-color: var(--border-color, #cbd5e1);" title="Menu Pilihan Arisan">
                                        <i class="bi bi-three-dots-vertical fs-6"></i>
                                    </button>
                                    <ul class="dropdown-menu dropdown-menu-end shadow border rounded-3 p-1" style="min-width: 260px; background-color: var(--card-bg, #ffffff); border-color: var(--border-color, #e2e8f0);">
                                        <li>
                                            <a class="dropdown-item py-2 rounded-2 d-flex align-items-center gap-2 ${dol}{window.arisanSection === 'RINGKASAN' ? 'active fw-bold' : ''}" href="javascript:void(0)" onclick="setArisanSection('RINGKASAN')">
                                                <i class="bi bi-dice-5-fill text-primary"></i>
                                                <span>📊 Ringkasan & Kocokan</span>
                                            </a>
                                        </li>
                                        <li>
                                            <a class="dropdown-item py-2 rounded-2 d-flex align-items-center gap-2 ${dol}{window.arisanSection === 'IURAN' ? 'active fw-bold' : ''}" href="javascript:void(0)" onclick="setArisanSection('IURAN')">
                                                <i class="bi bi-credit-card-2-front-fill text-primary"></i>
                                                <span>💰 Iuran & Pembayaran</span>
                                            </a>
                                        </li>
                                        <li>
                                            <a class="dropdown-item py-2 rounded-2 d-flex align-items-center gap-2 ${dol}{window.arisanSection === 'KAS_SOSIAL' ? 'active fw-bold' : ''}" href="javascript:void(0)" onclick="setArisanSection('KAS_SOSIAL')">
                                                <i class="bi bi-wallet2 text-primary"></i>
                                                <span>📖 Buku Catatan Kas Sosial</span>
                                            </a>
                                        </li>
                                        <li>
                                            <a class="dropdown-item py-2 rounded-2 d-flex align-items-center gap-2 ${dol}{window.arisanSection === 'PESERTA' ? 'active fw-bold' : ''}" href="javascript:void(0)" onclick="setArisanSection('PESERTA')">
                                                <i class="bi bi-people-fill text-primary"></i>
                                                <span>👥 Daftar Peserta & Anggota (${dol}{anggota.length})</span>
                                            </a>
                                        </li>
                                        <li>
                                            <a class="dropdown-item py-2 rounded-2 d-flex align-items-center gap-2 ${dol}{window.arisanSection === 'RIWAYAT' ? 'active fw-bold' : ''}" href="javascript:void(0)" onclick="setArisanSection('RIWAYAT')">
                                                <i class="bi bi-receipt-cutoff text-primary"></i>
                                                <span>📜 Riwayat Pemenang & Kuitansi</span>
                                            </a>
                                        </li>
                                        <li><hr class="dropdown-divider my-1"></li>
                                        <li>
                                            <a class="dropdown-item py-2 rounded-2 d-flex align-items-center gap-2" href="javascript:void(0)" onclick="openArisanConfigModal()">
                                                <i class="bi bi-gear-fill text-secondary"></i>
                                                <span>⚙️ Pengaturan Arisan</span>
                                            </a>
                                        </li>
                                        <li>
                                            <a class="dropdown-item py-2 rounded-2 d-flex align-items-center gap-2" href="javascript:void(0)" onclick="exportArisanToExcelWeb()">
                                                <i class="bi bi-file-earmark-excel-fill text-success"></i>
                                                <span>📥 Export Rekapitulasi Excel (.csv)</span>
                                            </a>
                                        </li>
                                        <li>
                                            <a class="dropdown-item py-2 rounded-2 d-flex align-items-center gap-2" href="javascript:void(0)" onclick="confirmResetCycleArisan()">
                                                <i class="bi bi-arrow-repeat text-info"></i>
                                                <span>🔄 Reset Putaran Baru / Siklus</span>
                                            </a>
                                        </li>
                                        <li>
                                            <a class="dropdown-item py-2 rounded-2 d-flex align-items-center gap-2 text-danger" href="javascript:void(0)" onclick="confirmResetDataDefaultArisan()">
                                                <i class="bi bi-trash3-fill text-danger"></i>
                                                <span>🗑️ Reset / Kosongkan Data</span>
                                            </a>
                                        </li>
                                    </ul>
                                </div>
                            </div>
                        </div>
                    </div>

                    <!-- CONTENT CONTAINER SESUAI MENU AKTIF -->
                    <div id="arisanMainSectionContent">
            `;

            if (window.arisanSection === 'RINGKASAN') {
                html += renderSectionRingkasan(cfg, anggota, pembayaran, kas, riwayat, totalTarikanPerPutaran, saldoKasSosial, totalSudahMenang, totalBelumMenang, totalSlots);
            } else if (window.arisanSection === 'IURAN') {
                html += renderSectionIuran(cfg, anggota, pembayaran);
            } else if (window.arisanSection === 'KAS_SOSIAL') {
                html += renderSectionKasSosial(kas, saldoKasSosial, totalKasMasuk, totalKasKeluar);
            } else if (window.arisanSection === 'PESERTA') {
                html += renderSectionPeserta(anggota, totalSlots);
            } else if (window.arisanSection === 'RIWAYAT') {
                html += renderSectionRiwayat(riwayat, cfg);
            }

            html += `
                    </div>
                </div>

                <!-- MODAL CONTAINER -->
                <div id="arisanModalContainer"></div>
            `;

            container.innerHTML = html;
        }

        // ==========================================
        // --- 1. SECTION: RINGKASAN & KOCOKAN ---
        // ==========================================
        function renderSectionRingkasan(cfg, anggota, pembayaran, kas, riwayat, totalTarikan, saldoKas, sudahMenang, belumMenang, totalSlots) {
            const periode = Number(cfg.periodeAktif) || 1;
            const eligible = anggota.filter(m => !m.sudahMenang);
            const lastWinner = riwayat && riwayat.length > 0 ? riwayat[0] : null;

            if (anggota.length === 0) {
                return `
                    <div class="card border rounded-4 text-center p-5 mb-4 shadow-sm" style="background-color: var(--card-bg, #ffffff); border-color: var(--border-color, #e2e8f0) !important;">
                        <div class="mb-3">
                            <i class="bi bi-people text-muted" style="font-size: 3.5rem;"></i>
                        </div>
                        <h5 class="fw-bold mb-2" style="color: var(--text-main, #0f172a);">Belum Ada Peserta Terdaftar</h5>
                        <p class="text-secondary small mb-4 mx-auto" style="max-width: 480px;">
                            Data arisan keluarga saat ini masih kosong. Silakan tambahkan anggota keluarga pertama untuk mulai mencatat iuran, kas sosial, dan melakukan kocokan arisan.
                        </p>
                        <div>
                            <button class="btn btn-primary rounded-pill px-4 py-2 fw-semibold shadow-sm" onclick="openAddAnggotaModal()">
                                <i class="bi bi-person-plus-fill me-2"></i> Tambah Anggota Pertama
                            </button>
                        </div>
                    </div>
                `;
            }

            return `
                <!-- 4 KARTU STATISTIK KEUANGAN -->
                <div class="row g-3 mb-3">
                    <div class="col-6 col-md-3">
                        <div class="card border rounded-3 p-3 h-100 shadow-sm" style="background-color: var(--card-bg, #ffffff); border-color: var(--border-color, #e2e8f0) !important;">
                            <span class="small text-secondary fw-medium">Uang Tarikan Pemenang</span>
                            <h4 class="fw-bold mb-0 text-warning" style="color: #b45309 !important;">${dol}{formatRupiahArisanWeb(totalTarikan)}</h4>
                            <span class="small text-muted mt-1">${dol}{totalSlots} Slot Peserta</span>
                        </div>
                    </div>
                    <div class="col-6 col-md-3">
                        <div class="card border rounded-3 p-3 h-100 shadow-sm" style="background-color: var(--card-bg, #ffffff); border-color: var(--border-color, #e2e8f0) !important;">
                            <span class="small text-secondary fw-medium">Saldo Kas Sosial</span>
                            <h4 class="fw-bold mb-0 text-success">${dol}{formatRupiahArisanWeb(saldoKas)}</h4>
                            <span class="small text-muted mt-1">Konsumsi & Cadangan</span>
                        </div>
                    </div>
                    <div class="col-6 col-md-3">
                        <div class="card border rounded-3 p-3 h-100 shadow-sm" style="background-color: var(--card-bg, #ffffff); border-color: var(--border-color, #e2e8f0) !important;">
                            <span class="small text-secondary fw-medium">Iuran per Slot</span>
                            <h4 class="fw-bold mb-0 text-primary">${dol}{formatRupiahArisanWeb(cfg.iuranPokokPerSlot)}</h4>
                            <span class="small text-muted mt-1">+ Kas ${dol}{formatRupiahArisanWeb(cfg.iuranKasPerSlot)}</span>
                        </div>
                    </div>
                    <div class="col-6 col-md-3">
                        <div class="card border rounded-3 p-3 h-100 shadow-sm" style="background-color: var(--card-bg, #ffffff); border-color: var(--border-color, #e2e8f0) !important;">
                            <span class="small text-secondary fw-medium">Status Undian</span>
                            <h4 class="fw-bold mb-0" style="color: var(--text-main, #0f172a);">${dol}{sudahMenang} / ${dol}{anggota.length}</h4>
                            <span class="small text-success mt-1">Sisa ${dol}{belumMenang} belum menang</span>
                        </div>
                    </div>
                </div>

                <!-- INFO JADWAL & PERTEMUAN BERIKUTNYA -->
                <div class="card border rounded-3 mb-3 shadow-sm" style="background-color: var(--card-bg, #ffffff); border-color: var(--border-color, #e2e8f0) !important;">
                    <div class="card-body p-3">
                        <div class="d-flex flex-wrap justify-content-between align-items-center gap-2 mb-2">
                            <h6 class="fw-bold mb-0" style="color: var(--text-main, #0f172a);">
                                <i class="bi bi-calendar2-check-fill text-primary me-2"></i> Jadwal & Info Pertemuan Berikutnya (Putaran #${dol}{periode})
                            </h6>
                            <div class="d-flex gap-2">
                                <button class="btn btn-sm btn-outline-success rounded-pill px-3" onclick="shareArisanWhatsAppInvite()">
                                    <i class="bi bi-whatsapp me-1"></i> Bagikan Undangan WA
                                </button>
                                <button class="btn btn-sm btn-outline-secondary rounded-pill px-3" onclick="openArisanConfigModal()">
                                    <i class="bi bi-pencil-square me-1"></i> Ubah Jadwal
                                </button>
                            </div>
                        </div>
                        <div class="row g-2 small text-secondary">
                            <div class="col-md-4 col-12">
                                <div class="p-2 rounded bg-light border text-truncate">
                                    <i class="bi bi-calendar-event text-primary me-1"></i>
                                    <strong>Tanggal:</strong> ${dol}{escapeHtmlArisan(cfg.jadwalPertemuanBerikutnya || 'Belum Ditentukan')}
                                    ${dol}{cfg.waktuPertemuanBerikutnya ? ` &bull; ${dol}{escapeHtmlArisan(cfg.waktuPertemuanBerikutnya)}` : ''}
                                </div>
                            </div>
                            <div class="col-md-5 col-12">
                                <div class="p-2 rounded bg-light border text-truncate">
                                    <i class="bi bi-geo-alt-fill text-danger me-1"></i>
                                    <strong>Lokasi / Tuan Rumah:</strong> ${dol}{escapeHtmlArisan(cfg.lokasiPertemuanBerikutnya || 'Belum Ditentukan')}
                                </div>
                            </div>
                            <div class="col-md-3 col-12">
                                <div class="p-2 rounded bg-light border text-truncate">
                                    <i class="bi bi-person-fill text-secondary me-1"></i>
                                    <strong>Bendahara:</strong> ${dol}{escapeHtmlArisan(cfg.namaBendahara || '-')}
                                </div>
                            </div>
                            ${dol}{cfg.catatanPertemuan ? `
                                <div class="col-12">
                                    <div class="p-2 rounded bg-light border text-truncate">
                                        <i class="bi bi-info-circle text-info me-1"></i>
                                        <strong>Catatan:</strong> ${dol}{escapeHtmlArisan(cfg.catatanPertemuan)}
                                    </div>
                                </div>
                            ` : ''}
                        </div>
                    </div>
                </div>

                <div class="row g-3">
                    <!-- MESIN KOCOKAN ARISAN DIGITAL INTERAKTIF -->
                    <div class="col-lg-7 col-12">
                        <div class="card border rounded-3 h-100 shadow-sm" style="background-color: var(--card-bg, #ffffff); border-color: var(--border-color, #e2e8f0) !important;">
                            <div class="card-header border-0 py-3 px-4 d-flex justify-content-between align-items-center" style="background: transparent;">
                                <h6 class="fw-bold mb-0" style="color: var(--text-main, #0f172a);">
                                    <i class="bi bi-dice-5-fill text-primary me-2"></i> Mesin Kocokan Arisan Digital - Putaran Ke-${dol}{periode}
                                </h6>
                                <span class="badge bg-primary-subtle text-primary border border-primary px-2 py-1 rounded-pill">
                                    ${dol}{eligible.length} Berhak Diundi
                                </span>
                            </div>
                            <div class="card-body p-4 text-center">
                                <div class="py-4 my-2 rounded-4 border border-2 border-dashed position-relative" style="background: rgba(14, 165, 233, 0.04); border-color: var(--border-color, #cbd5e1) !important;">
                                    <div class="display-6 fw-bold mb-2" id="arisanDrawWinnerBox" style="color: var(--text-main, #0f172a);">
                                        🎲 Siap Mengundi Pemenang...
                                    </div>
                                    <p class="text-secondary small mb-0" id="arisanDrawSubBox">
                                        Total Uang Tarikan: <strong class="text-warning" style="color: #b45309 !important;">${dol}{formatRupiahArisanWeb(totalTarikan)}</strong>
                                    </p>
                                </div>

                                <div class="mt-4 d-flex justify-content-center gap-2 flex-wrap">
                                    <button class="btn btn-primary btn-lg px-4 py-2 rounded-pill fw-bold shadow-sm" id="btnRollArisan" onclick="startArisanRollAction()" ${dol}{eligible.length === 0 ? 'disabled' : ''}>
                                        <i class="bi bi-dice-6-fill me-2"></i> Mulai Kocok Arisan!
                                    </button>
                                    <button class="btn btn-outline-secondary btn-lg px-3 py-2 rounded-pill shadow-sm" onclick="confirmResetCycleArisan()">
                                        <i class="bi bi-arrow-repeat me-1"></i> Reset Siklus
                                    </button>
                                </div>

                                ${dol}{eligible.length === 0 ? `
                                    <div class="alert alert-success mt-3 rounded-pill py-2 small mb-0">
                                        🎉 Selamat! Seluruh anggota pada siklus ini telah mendapatkan giliran menang. Silakan klik "Reset Siklus" untuk putaran baru.
                                    </div>
                                ` : ''}
                            </div>
                        </div>
                    </div>

                    <!-- KANDIDAT YANG BELUM MENANG & PEMENANG TERAKHIR -->
                    <div class="col-lg-5 col-12">
                        <div class="card border rounded-3 h-100 shadow-sm" style="background-color: var(--card-bg, #ffffff); border-color: var(--border-color, #e2e8f0) !important;">
                            <div class="card-header border-0 py-3 px-4 d-flex justify-content-between align-items-center" style="background: transparent;">
                                <h6 class="fw-bold mb-0" style="color: var(--text-main, #0f172a);">
                                    <i class="bi bi-person-check-fill text-success me-2"></i> Belum Menang (${dol}{eligible.length})
                                </h6>
                                <span class="small text-muted">Kandidat Undian</span>
                            </div>
                            <div class="card-body p-0">
                                <div class="list-group list-group-flush" style="max-height: 340px; overflow-y: auto;">
                                    ${dol}{eligible.length === 0 ? `
                                        <div class="p-4 text-center text-muted small">Semua peserta telah menang pada siklus ini.</div>
                                    ` : eligible.map(m => `
                                        <div class="list-group-item px-3 py-2 d-flex align-items-center justify-content-between" style="background: transparent; border-color: var(--border-color, #f1f5f9);">
                                            <div>
                                                <div class="fw-semibold text-truncate" style="color: var(--text-main, #0f172a); max-width: 200px;">${dol}{escapeHtmlArisan(m.nama)}</div>
                                                <small class="text-muted">${dol}{escapeHtmlArisan(m.hubunganKeluarga || 'Kerabat')} &bull; ${dol}{m.jumlahSlot} Slot</small>
                                            </div>
                                            <span class="badge bg-secondary-subtle text-secondary px-2 py-1 rounded-pill small">Belum Menang</span>
                                        </div>
                                    `).join('')}
                                </div>
                            </div>
                        </div>
                    </div>
                </div>

                ${dol}{lastWinner ? `
                    <!-- PEMENANG PUTARAN TERAKHIR -->
                    <div class="card border rounded-3 mt-3 shadow-sm" style="background-color: var(--card-bg, #ffffff); border-color: var(--border-color, #e2e8f0) !important;">
                        <div class="card-body p-3 d-flex flex-wrap align-items-center justify-content-between gap-3">
                            <div class="d-flex align-items-center gap-3">
                                <div class="rounded-circle d-flex align-items-center justify-content-center bg-warning bg-opacity-25" style="width: 48px; height: 48px;">
                                    <i class="bi bi-trophy-fill text-warning fs-4" style="color: #b45309 !important;"></i>
                                </div>
                                <div>
                                    <span class="badge bg-warning-subtle text-dark border border-warning px-2 py-0.5 rounded-pill small mb-1">
                                        Pemenang Terakhir (Putaran #${dol}{lastWinner.periode})
                                    </span>
                                    <h6 class="fw-bold mb-0" style="color: var(--text-main, #0f172a);">${dol}{escapeHtmlArisan(lastWinner.namaPemenang)}</h6>
                                    <small class="text-secondary">${dol}{escapeHtmlArisan(lastWinner.hubunganKeluarga)} &bull; ${dol}{formatRupiahArisanWeb(lastWinner.nominalTarikan)} &bull; ${dol}{escapeHtmlArisan(lastWinner.tanggalPengundian)}</small>
                                </div>
                            </div>
                            <div class="d-flex gap-2">
                                <button class="btn btn-sm btn-outline-primary rounded-pill px-3" onclick="viewReceiptArisan('${dol}{lastWinner.id}')">
                                    <i class="bi bi-receipt me-1"></i> Lihat Kuitansi
                                </button>
                                <button class="btn btn-sm btn-outline-success rounded-pill px-3" onclick="shareReceiptWhatsApp('${dol}{lastWinner.id}')">
                                    <i class="bi bi-whatsapp me-1"></i> Bukti WA
                                </button>
                            </div>
                        </div>
                    </div>
                ` : ''}
            `;
        }

        // ==========================================
        // --- 2. SECTION: IURAN & PEMBAYARAN ---
        // ==========================================
        function renderSectionIuran(cfg, anggota, pembayaran) {
            const periode = Number(cfg.periodeAktif) || 1;
            const nominalPokok = Number(cfg.iuranPokokPerSlot) || 0;
            const nominalKas = Number(cfg.iuranKasPerSlot) || 0;
            const totalPerSlot = nominalPokok + nominalKas;

            if (anggota.length === 0) {
                return `
                    <div class="card border rounded-4 text-center p-5 mb-4 shadow-sm" style="background-color: var(--card-bg, #ffffff); border-color: var(--border-color, #e2e8f0) !important;">
                        <i class="bi bi-credit-card-2-front text-muted" style="font-size: 3.5rem;"></i>
                        <h5 class="fw-bold mt-3 mb-2" style="color: var(--text-main, #0f172a);">Belum Ada Peserta Terdaftar</h5>
                        <p class="text-secondary small mb-4">Tambahkan peserta terlebih dahulu untuk membuat tagihan iuran putaran #${dol}{periode}.</p>
                        <div>
                            <button class="btn btn-primary rounded-pill px-4 py-2 fw-semibold shadow-sm" onclick="openAddAnggotaModal()">
                                <i class="bi bi-person-plus-fill me-2"></i> Tambah Anggota
                            </button>
                        </div>
                    </div>
                `;
            }

            // Hitung statistik pembayaran periode aktif
            let totalWajib = 0;
            let totalTerkumpul = 0;
            let countLunas = 0;

            anggota.forEach(m => {
                const slots = Number(m.jumlahSlot) || 1;
                const tagihan = slots * totalPerSlot;
                totalWajib += tagihan;

                const p = pembayaran.find(item => item.periode === periode && item.anggotaId === m.id);
                if (p && p.isLunas) {
                    totalTerkumpul += tagihan;
                    countLunas++;
                }
            });

            const countBelum = anggota.length - countLunas;
            const sisaTagihan = totalWajib - totalTerkumpul;
            const persen = totalWajib > 0 ? Math.round((totalTerkumpul / totalWajib) * 100) : 0;

            const query = (window.arisanIuranSearch || '').trim().toLowerCase();
            const statusFilter = window.arisanIuranStatusFilter || 'ALL';

            const filteredAnggota = anggota.filter(m => {
                const p = pembayaran.find(item => item.periode === periode && item.anggotaId === m.id);
                const isPaid = p && p.isLunas;

                if (statusFilter === 'PAID' && !isPaid) return false;
                if (statusFilter === 'UNPAID' && isPaid) return false;

                if (query) {
                    const matchNama = (m.nama || '').toLowerCase().includes(query);
                    const matchHub = (m.hubunganKeluarga || '').toLowerCase().includes(query);
                    const matchWa = (m.nomorWa || '').includes(query);
                    if (!matchNama && !matchHub && !matchWa) return false;
                }
                return true;
            });

            return `
                <!-- METRIK IURAN -->
                <div class="row g-3 mb-3">
                    <div class="col-md-4 col-12">
                        <div class="card border rounded-3 p-3 shadow-sm" style="background-color: var(--card-bg, #ffffff); border-color: var(--border-color, #e2e8f0) !important;">
                            <span class="small text-secondary fw-medium">Terkumpul Periode #${dol}{periode}</span>
                            <h4 class="fw-bold mb-0 text-success">${dol}{formatRupiahArisanWeb(totalTerkumpul)}</h4>
                            <span class="small text-muted mt-1">${dol}{countLunas} Peserta Lunas (${dol}{persen}%)</span>
                        </div>
                    </div>
                    <div class="col-md-4 col-12">
                        <div class="card border rounded-3 p-3 shadow-sm" style="background-color: var(--card-bg, #ffffff); border-color: var(--border-color, #e2e8f0) !important;">
                            <span class="small text-secondary fw-medium">Belum Lunas</span>
                            <h4 class="fw-bold mb-0 text-danger">${dol}{formatRupiahArisanWeb(sisaTagihan)}</h4>
                            <span class="small text-muted mt-1">${dol}{countBelum} Peserta Belum Bayar</span>
                        </div>
                    </div>
                    <div class="col-md-4 col-12">
                        <div class="card border rounded-3 p-3 shadow-sm" style="background-color: var(--card-bg, #ffffff); border-color: var(--border-color, #e2e8f0) !important;">
                            <span class="small text-secondary fw-medium">Total Wajib Tagihan</span>
                            <h4 class="fw-bold mb-0 text-primary">${dol}{formatRupiahArisanWeb(totalWajib)}</h4>
                            <span class="small text-muted mt-1">${dol}{totalPerSlot.toLocaleString('id-ID')} / Slot (${dol}{nominalPokok.toLocaleString('id-ID')} Iuran + ${dol}{nominalKas.toLocaleString('id-ID')} Kas)</span>
                        </div>
                    </div>
                </div>

                <!-- CARD DAFTAR IURAN & FILTER -->
                <div class="card border rounded-3 p-3 shadow-sm" style="background-color: var(--card-bg, #ffffff); border-color: var(--border-color, #e2e8f0) !important;">
                    <div class="d-flex flex-wrap justify-content-between align-items-center gap-2 mb-3">
                        <h6 class="fw-bold mb-0" style="color: var(--text-main, #0f172a);">
                            <i class="bi bi-wallet-fill text-primary me-2"></i> Pencatatan Iuran Putaran Ke-${dol}{periode}
                        </h6>
                        <div class="d-flex gap-2">
                            <button class="btn btn-sm btn-outline-primary rounded-pill px-3" onclick="confirmMarkAllPaidArisan(${dol}{periode})">
                                <i class="bi bi-check-all me-1"></i> Tandai Semua Lunas
                            </button>
                        </div>
                    </div>

                    <!-- SEARCH & FILTER BAR -->
                    <div class="row g-2 align-items-center mb-3">
                        <div class="col-md-6 col-12">
                            <div class="input-group input-group-sm">
                                <span class="input-group-text bg-transparent border-end-0" style="border-color: var(--border-color, #cbd5e1);"><i class="bi bi-search text-muted"></i></span>
                                <input type="text" class="form-control border-start-0" placeholder="Cari nama anggota, no WA, hubungan..." value="${dol}{escapeHtmlArisan(window.arisanIuranSearch || '')}" oninput="window.arisanIuranSearch = this.value; renderArisanMainView();" style="border-color: var(--border-color, #cbd5e1); background-color: var(--input-bg, #ffffff); color: var(--input-color, #0f172a);">
                            </div>
                        </div>
                        <div class="col-md-6 col-12 d-flex justify-content-md-end gap-1">
                            <div class="btn-group btn-group-sm" role="group">
                                <button type="button" class="btn ${dol}{statusFilter === 'ALL' ? 'btn-primary fw-bold' : 'btn-outline-secondary'}" onclick="window.arisanIuranStatusFilter = 'ALL'; renderArisanMainView();">Semua (${dol}{anggota.length})</button>
                                <button type="button" class="btn ${dol}{statusFilter === 'PAID' ? 'btn-success fw-bold' : 'btn-outline-secondary'}" onclick="window.arisanIuranStatusFilter = 'PAID'; renderArisanMainView();">Lunas (${dol}{countLunas})</button>
                                <button type="button" class="btn ${dol}{statusFilter === 'UNPAID' ? 'btn-danger fw-bold' : 'btn-outline-secondary'}" onclick="window.arisanIuranStatusFilter = 'UNPAID'; renderArisanMainView();">Belum Bayar (${dol}{countBelum})</button>
                            </div>
                        </div>
                    </div>

                    <div class="table-responsive">
                        <table class="table table-hover align-middle mb-0" style="color: var(--text-main, #0f172a);">
                            <thead class="small text-secondary" style="border-color: var(--border-color, #e2e8f0);">
                                <tr>
                                    <th>#</th>
                                    <th>Peserta</th>
                                    <th>Slot</th>
                                    <th>Rincian Tagihan</th>
                                    <th>Total Tagihan</th>
                                    <th>Status & Metode</th>
                                    <th class="text-end">Aksi</th>
                                </tr>
                            </thead>
                            <tbody style="border-color: var(--border-color, #e2e8f0);">
                                ${dol}{filteredAnggota.length === 0 ? `
                                    <tr><td colspan="7" class="text-center py-4 text-muted small">Tidak ditemukan data iuran yang sesuai.</td></tr>
                                ` : filteredAnggota.map((m, idx) => {
                                    const slots = Number(m.jumlahSlot) || 1;
                                    const tagihan = slots * totalPerSlot;
                                    const pokok = slots * nominalPokok;
                                    const kasVal = slots * nominalKas;
                                    const p = pembayaran.find(item => item.periode === periode && item.anggotaId === m.id);
                                    const isPaid = p && p.isLunas;
                                    const metode = (p && p.metodePembayaran) || 'Tunai';

                                    return `
                                        <tr>
                                            <td>${dol}{idx + 1}</td>
                                            <td>
                                                <div class="fw-semibold">${dol}{escapeHtmlArisan(m.nama)}</div>
                                                <small class="text-secondary">${dol}{escapeHtmlArisan(m.hubunganKeluarga || 'Kerabat')}</small>
                                            </td>
                                            <td><span class="badge bg-light text-secondary border">${dol}{slots} Slot</span></td>
                                            <td>
                                                <small class="text-secondary">Pokok: ${dol}{formatRupiahArisanWeb(pokok)}<br>Kas: ${dol}{formatRupiahArisanWeb(kasVal)}</small>
                                            </td>
                                            <td class="fw-bold">${dol}{formatRupiahArisanWeb(tagihan)}</td>
                                            <td>
                                                ${dol}{isPaid ? `
                                                    <span class="badge bg-success-subtle text-success border border-success px-2 py-1 rounded-pill small">
                                                        <i class="bi bi-check-circle-fill me-1"></i> Lunas (${dol}{metode})
                                                    </span>
                                                ` : `
                                                    <span class="badge bg-danger-subtle text-danger border border-danger px-2 py-1 rounded-pill small">
                                                        <i class="bi bi-clock-fill me-1"></i> Belum Bayar
                                                    </span>
                                                `}
                                            </td>
                                            <td class="text-end">
                                                <div class="btn-group btn-group-sm">
                                                    <button class="btn ${dol}{isPaid ? 'btn-outline-danger' : 'btn-success'} rounded-pill px-3" onclick="togglePaymentStatusArisan('${dol}{m.id}', ${dol}{periode}, ${dol}{tagihan})">
                                                        ${dol}{isPaid ? '<i class="bi bi-x-circle me-1"></i> Batal Lunas' : '<i class="bi bi-check2 me-1"></i> Tandai Lunas'}
                                                    </button>
                                                    ${dol}{!isPaid ? `
                                                        <button class="btn btn-outline-success rounded-pill px-2 ms-1" onclick="sendReminderWhatsApp('${dol}{m.id}', ${dol}{tagihan}, ${dol}{periode})" title="Kirim Pengingat WA">
                                                            <i class="bi bi-whatsapp"></i>
                                                        </button>
                                                    ` : ''}
                                                </div>
                                            </td>
                                        </tr>
                                    `;
                                }).join('')}
                            </tbody>
                        </table>
                    </div>
                </div>
            `;
        }

        // ==========================================
        // --- 3. SECTION: BUKU KAS SOSIAL ---
        // ==========================================
        function renderSectionKasSosial(kas, saldoKas, totalMasuk, totalKeluar) {
            const query = (window.arisanKasSearch || '').trim().toLowerCase();
            const filterType = window.arisanKasFilter || 'ALL';

            const filteredKas = kas.filter(k => {
                if (filterType === 'MASUK' && k.jenis !== 'Masuk') return false;
                if (filterType === 'KELUAR' && k.jenis !== 'Keluar') return false;

                if (query) {
                    const matchKet = (k.deskripsi || '').toLowerCase().includes(query);
                    const matchKat = (k.kategori || '').toLowerCase().includes(query);
                    const matchTgl = (k.tanggal || '').includes(query);
                    if (!matchKet && !matchKat && !matchTgl) return false;
                }
                return true;
            });

            return `
                <!-- KARTU SALDO KAS SOSIAL -->
                <div class="card border rounded-3 p-3 mb-3 shadow-sm" style="background-color: var(--card-bg, #ffffff); border-color: var(--border-color, #e2e8f0) !important;">
                    <div class="row align-items-center g-3">
                        <div class="col-md-5 col-12">
                            <span class="small text-secondary fw-medium">Saldo Kas Sosial Saat Ini</span>
                            <h3 class="fw-bold mb-0 text-success">${dol}{formatRupiahArisanWeb(saldoKas)}</h3>
                            <small class="text-muted">Untuk konsumsi arisan, santunan, & dana darurat keluarga</small>
                        </div>
                        <div class="col-md-7 col-12 d-flex flex-wrap justify-content-md-end gap-2 align-items-center">
                            <div class="text-md-end me-3">
                                <span class="small text-success d-block fw-semibold"><i class="bi bi-arrow-down-left"></i> Masuk: ${dol}{formatRupiahArisanWeb(totalMasuk)}</span>
                                <span class="small text-danger d-block fw-semibold"><i class="bi bi-arrow-up-right"></i> Keluar: ${dol}{formatRupiahArisanWeb(totalKeluar)}</span>
                            </div>
                            <button class="btn btn-primary btn-sm rounded-pill px-3 fw-semibold shadow-sm" onclick="openAddKasDialog()">
                                <i class="bi bi-plus-circle me-1"></i> Catat Transaksi Kas
                            </button>
                        </div>
                    </div>
                </div>

                <!-- CARD DAFTAR KAS & FILTER -->
                <div class="card border rounded-3 p-3 shadow-sm" style="background-color: var(--card-bg, #ffffff); border-color: var(--border-color, #e2e8f0) !important;">
                    <div class="row g-2 align-items-center mb-3">
                        <div class="col-md-6 col-12">
                            <div class="input-group input-group-sm">
                                <span class="input-group-text bg-transparent border-end-0" style="border-color: var(--border-color, #cbd5e1);"><i class="bi bi-search text-muted"></i></span>
                                <input type="text" class="form-control border-start-0" placeholder="Cari keterangan, kategori, tanggal..." value="${dol}{escapeHtmlArisan(window.arisanKasSearch || '')}" oninput="window.arisanKasSearch = this.value; renderArisanMainView();" style="border-color: var(--border-color, #cbd5e1); background-color: var(--input-bg, #ffffff); color: var(--input-color, #0f172a);">
                            </div>
                        </div>
                        <div class="col-md-6 col-12 d-flex justify-content-md-end gap-1">
                            <div class="btn-group btn-group-sm" role="group">
                                <button type="button" class="btn ${dol}{filterType === 'ALL' ? 'btn-primary fw-bold' : 'btn-outline-secondary'}" onclick="window.arisanKasFilter = 'ALL'; renderArisanMainView();">Semua (${dol}{kas.length})</button>
                                <button type="button" class="btn ${dol}{filterType === 'MASUK' ? 'btn-success fw-bold' : 'btn-outline-secondary'}" onclick="window.arisanKasFilter = 'MASUK'; renderArisanMainView();">Masuk</button>
                                <button type="button" class="btn ${dol}{filterType === 'KELUAR' ? 'btn-danger fw-bold' : 'btn-outline-secondary'}" onclick="window.arisanKasFilter = 'KELUAR'; renderArisanMainView();">Keluar</button>
                            </div>
                        </div>
                    </div>

                    <div class="table-responsive">
                        <table class="table table-hover align-middle mb-0" style="color: var(--text-main, #0f172a);">
                            <thead class="small text-secondary" style="border-color: var(--border-color, #e2e8f0);">
                                <tr>
                                    <th>Tanggal</th>
                                    <th>Jenis</th>
                                    <th>Kategori</th>
                                    <th>Keterangan</th>
                                    <th class="text-end">Nominal</th>
                                    <th class="text-end">Aksi</th>
                                </tr>
                            </thead>
                            <tbody style="border-color: var(--border-color, #e2e8f0);">
                                ${dol}{filteredKas.length === 0 ? `
                                    <tr><td colspan="6" class="text-center py-4 text-muted small">Belum ada catatan transaksi kas sosial.</td></tr>
                                ` : filteredKas.map(k => `
                                    <tr>
                                        <td>${dol}{escapeHtmlArisan(k.tanggal)}</td>
                                        <td>
                                            ${dol}{k.jenis === 'Masuk' ? `
                                                <span class="badge bg-success-subtle text-success border border-success px-2 py-1 rounded-pill small">Masuk</span>
                                            ` : `
                                                <span class="badge bg-danger-subtle text-danger border border-danger px-2 py-1 rounded-pill small">Keluar</span>
                                            `}
                                        </td>
                                        <td><strong>${dol}{escapeHtmlArisan(k.kategori || 'Umum')}</strong></td>
                                        <td class="text-secondary small">${dol}{escapeHtmlArisan(k.deskripsi || '-')}</td>
                                        <td class="text-end fw-bold ${dol}{k.jenis === 'Masuk' ? 'text-success' : 'text-danger'}">
                                            ${dol}{k.jenis === 'Masuk' ? '+' : '-'} ${dol}{formatRupiahArisanWeb(k.nominal)}
                                        </td>
                                        <td class="text-end">
                                            <button class="btn btn-sm btn-outline-danger rounded-circle p-1" onclick="deleteKasEntryArisan('${dol}{k.id}')" title="Hapus Catatan">
                                                <i class="bi bi-trash"></i>
                                            </button>
                                        </td>
                                    </tr>
                                `).join('')}
                            </tbody>
                        </table>
                    </div>
                </div>
            `;
        }

        // ==========================================
        // --- 4. SECTION: DAFTAR PESERTA & ANGGOTA ---
        // ==========================================
        function renderSectionPeserta(anggota, totalSlots) {
            const query = (window.arisanPesertaSearch || '').trim().toLowerCase();
            const filterType = window.arisanPesertaFilter || 'ALL';

            const filteredAnggota = anggota.filter(m => {
                if (filterType === 'WON' && !m.sudahMenang) return false;
                if (filterType === 'UNWON' && m.sudahMenang) return false;

                if (query) {
                    const matchNama = (m.nama || '').toLowerCase().includes(query);
                    const matchHub = (m.hubunganKeluarga || '').toLowerCase().includes(query);
                    const matchWa = (m.nomorWa || '').includes(query);
                    if (!matchNama && !matchHub && !matchWa) return false;
                }
                return true;
            });

            const countWon = anggota.filter(m => m.sudahMenang).length;
            const countUnwon = anggota.length - countWon;

            return `
                <div class="card border rounded-3 p-3 shadow-sm" style="background-color: var(--card-bg, #ffffff); border-color: var(--border-color, #e2e8f0) !important;">
                    <div class="d-flex flex-wrap justify-content-between align-items-center gap-2 mb-3">
                        <div>
                            <h6 class="fw-bold mb-0" style="color: var(--text-main, #0f172a);">
                                <i class="bi bi-people-fill text-primary me-2"></i> Daftar Anggota Peserta Arisan (${dol}{anggota.length} Anggota &bull; ${dol}{totalSlots} Slot)
                            </h6>
                        </div>
                        <div>
                            <button class="btn btn-primary btn-sm rounded-pill px-3 fw-semibold shadow-sm" onclick="openAddAnggotaModal()">
                                <i class="bi bi-person-plus-fill me-1"></i> Tambah Peserta
                            </button>
                        </div>
                    </div>

                    <!-- SEARCH & FILTER BAR -->
                    <div class="row g-2 align-items-center mb-3">
                        <div class="col-md-6 col-12">
                            <div class="input-group input-group-sm">
                                <span class="input-group-text bg-transparent border-end-0" style="border-color: var(--border-color, #cbd5e1);"><i class="bi bi-search text-muted"></i></span>
                                <input type="text" class="form-control border-start-0" placeholder="Cari nama peserta, no WA, hubungan..." value="${dol}{escapeHtmlArisan(window.arisanPesertaSearch || '')}" oninput="window.arisanPesertaSearch = this.value; renderArisanMainView();" style="border-color: var(--border-color, #cbd5e1); background-color: var(--input-bg, #ffffff); color: var(--input-color, #0f172a);">
                            </div>
                        </div>
                        <div class="col-md-6 col-12 d-flex justify-content-md-end gap-1">
                            <div class="btn-group btn-group-sm" role="group">
                                <button type="button" class="btn ${dol}{filterType === 'ALL' ? 'btn-primary fw-bold' : 'btn-outline-secondary'}" onclick="window.arisanPesertaFilter = 'ALL'; renderArisanMainView();">Semua (${dol}{anggota.length})</button>
                                <button type="button" class="btn ${dol}{filterType === 'UNWON' ? 'btn-info text-dark fw-bold' : 'btn-outline-secondary'}" onclick="window.arisanPesertaFilter = 'UNWON'; renderArisanMainView();">Belum Menang (${dol}{countUnwon})</button>
                                <button type="button" class="btn ${dol}{filterType === 'WON' ? 'btn-success fw-bold' : 'btn-outline-secondary'}" onclick="window.arisanPesertaFilter = 'WON'; renderArisanMainView();">Sudah Menang (${dol}{countWon})</button>
                            </div>
                        </div>
                    </div>

                    <div class="table-responsive">
                        <table class="table table-hover align-middle mb-0" style="color: var(--text-main, #0f172a);">
                            <thead class="small text-secondary" style="border-color: var(--border-color, #e2e8f0);">
                                <tr>
                                    <th>#</th>
                                    <th>Nama Anggota</th>
                                    <th>Hubungan Keluarga</th>
                                    <th>No. WhatsApp / HP</th>
                                    <th>Slot</th>
                                    <th>Status Giliran</th>
                                    <th class="text-end">Aksi</th>
                                </tr>
                            </thead>
                            <tbody style="border-color: var(--border-color, #e2e8f0);">
                                ${dol}{filteredAnggota.length === 0 ? `
                                    <tr><td colspan="7" class="text-center py-4 text-muted small">Belum ada data anggota yang sesuai.</td></tr>
                                ` : filteredAnggota.map((m, idx) => `
                                    <tr>
                                        <td>${dol}{idx + 1}</td>
                                        <td>
                                            <div class="d-flex align-items-center gap-2">
                                                <div class="rounded-circle d-flex align-items-center justify-content-center bg-primary bg-opacity-10 text-primary fw-bold" style="width: 32px; height: 32px; font-size: 0.85rem;">
                                                    ${dol}{m.nama ? m.nama.charAt(0).toUpperCase() : 'A'}
                                                </div>
                                                <div class="fw-semibold">${dol}{escapeHtmlArisan(m.nama)}</div>
                                            </div>
                                        </td>
                                        <td><span class="badge bg-light text-secondary border">${dol}{escapeHtmlArisan(m.hubunganKeluarga || 'Kerabat')}</span></td>
                                        <td>
                                            ${dol}{m.nomorWa ? `
                                                <a href="https://wa.me/${dol}{m.nomorWa.replace(/[^0-9]/g, '')}" target="_blank" class="text-decoration-none text-success small">
                                                    <i class="bi bi-whatsapp me-1"></i>${dol}{escapeHtmlArisan(m.nomorWa)}
                                                </a>
                                            ` : '<span class="text-muted small">-</span>'}
                                        </td>
                                        <td><span class="badge bg-light text-secondary border">${dol}{m.jumlahSlot} Slot</span></td>
                                        <td>
                                            ${dol}{m.sudahMenang ? `
                                                <span class="badge bg-warning-subtle text-dark border border-warning px-2 py-1 rounded-pill small">
                                                    🏆 Sudah Menang (Putaran #${dol}{m.periodeMenang || '1'})
                                                </span>
                                            ` : `
                                                <span class="badge bg-secondary-subtle text-secondary px-2 py-1 rounded-pill small">
                                                    Belum Menang
                                                </span>
                                            `}
                                        </td>
                                        <td class="text-end">
                                            <div class="btn-group btn-group-sm">
                                                <button class="btn btn-outline-secondary rounded-pill px-2" onclick="openEditAnggotaModal('${dol}{m.id}')" title="Edit Anggota">
                                                    <i class="bi bi-pencil"></i>
                                                </button>
                                                <button class="btn btn-outline-danger rounded-pill px-2 ms-1" onclick="deleteAnggotaArisan('${dol}{m.id}')" title="Hapus Anggota">
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
            `;
        }

        // ==========================================
        // --- 5. SECTION: RIWAYAT PEMENANG & KUITANSI ---
        // ==========================================
        function renderSectionRiwayat(riwayat, cfg) {
            const query = (window.arisanRiwayatSearch || '').trim().toLowerCase();

            const filteredRiwayat = riwayat.filter(h => {
                if (query) {
                    const matchNama = (h.namaPemenang || '').toLowerCase().includes(query);
                    const matchKwt = (h.nomorKuitansi || '').toLowerCase().includes(query);
                    const matchTgl = (h.tanggalPengundian || '').includes(query);
                    const matchPer = ('#' + h.periode).includes(query) || String(h.periode).includes(query);
                    if (!matchNama && !matchKwt && !matchTgl && !matchPer) return false;
                }
                return true;
            });

            const totalTarikanDiserahkan = riwayat.reduce((acc, h) => acc + (Number(h.nominalTarikan) || 0), 0);

            return `
                <div class="card border rounded-3 p-3 shadow-sm" style="background-color: var(--card-bg, #ffffff); border-color: var(--border-color, #e2e8f0) !important;">
                    <div class="d-flex flex-wrap justify-content-between align-items-center gap-2 mb-3">
                        <div>
                            <h6 class="fw-bold mb-0" style="color: var(--text-main, #0f172a);">
                                <i class="bi bi-receipt-cutoff text-primary me-2"></i> Riwayat Pemenang & Kuitansi Penyerahan Uang
                            </h6>
                            <small class="text-secondary">Total Tarikan Diserahkan: <strong>${dol}{formatRupiahArisanWeb(totalTarikanDiserahkan)}</strong> (${dol}{riwayat.length} Putaran Selesai)</small>
                        </div>
                    </div>

                    <!-- SEARCH BAR -->
                    <div class="row g-2 align-items-center mb-3">
                        <div class="col-md-6 col-12">
                            <div class="input-group input-group-sm">
                                <span class="input-group-text bg-transparent border-end-0" style="border-color: var(--border-color, #cbd5e1);"><i class="bi bi-search text-muted"></i></span>
                                <input type="text" class="form-control border-start-0" placeholder="Cari nama pemenang, nomor kuitansi, tanggal..." value="${dol}{escapeHtmlArisan(window.arisanRiwayatSearch || '')}" oninput="window.arisanRiwayatSearch = this.value; renderArisanMainView();" style="border-color: var(--border-color, #cbd5e1); background-color: var(--input-bg, #ffffff); color: var(--input-color, #0f172a);">
                            </div>
                        </div>
                    </div>

                    <div class="table-responsive">
                        <table class="table table-hover align-middle mb-0" style="color: var(--text-main, #0f172a);">
                            <thead class="small text-secondary" style="border-color: var(--border-color, #e2e8f0);">
                                <tr>
                                    <th>Putaran</th>
                                    <th>Tanggal Pengundian</th>
                                    <th>Nama Pemenang</th>
                                    <th>Nomor Kuitansi</th>
                                    <th>Nominal Tarikan</th>
                                    <th class="text-end">Aksi & Kuitansi</th>
                                </tr>
                            </thead>
                            <tbody style="border-color: var(--border-color, #e2e8f0);">
                                ${dol}{filteredRiwayat.length === 0 ? `
                                    <tr><td colspan="6" class="text-center py-4 text-muted small">Belum ada riwayat pemenang kocokan arisan.</td></tr>
                                ` : filteredRiwayat.map(h => `
                                    <tr>
                                        <td><span class="badge bg-primary-subtle text-primary border border-primary px-2 py-1 rounded-pill small">Putaran #${dol}{h.periode}</span></td>
                                        <td>${dol}{escapeHtmlArisan(h.tanggalPengundian)}</td>
                                        <td>
                                            <div class="fw-semibold">${dol}{escapeHtmlArisan(h.namaPemenang)}</div>
                                            <small class="text-secondary">${dol}{escapeHtmlArisan(h.hubunganKeluarga || 'Kerabat')}</small>
                                        </td>
                                        <td><code class="small text-muted">${dol}{escapeHtmlArisan(h.nomorKuitansi)}</code></td>
                                        <td class="fw-bold text-success">${dol}{formatRupiahArisanWeb(h.nominalTarikan)}</td>
                                        <td class="text-end">
                                            <div class="btn-group btn-group-sm">
                                                <button class="btn btn-outline-primary rounded-pill px-3" onclick="viewReceiptArisan('${dol}{h.id}')" title="Lihat & Cetak Kuitansi Resmi">
                                                    <i class="bi bi-receipt me-1"></i> Kuitansi
                                                </button>
                                                <button class="btn btn-outline-success rounded-pill px-2 ms-1" onclick="shareReceiptWhatsApp('${dol}{h.id}')" title="Kirim Bukti WA">
                                                    <i class="bi bi-whatsapp"></i>
                                                </button>
                                                <button class="btn btn-outline-danger rounded-pill px-2 ms-1" onclick="deleteRiwayatArisan('${dol}{h.id}')" title="Hapus Riwayat">
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
            `;
        }

        // ==========================================
        // --- ACTION LOGIC & REPOSITORY SYNC ---
        // ==========================================

        // Kocok Arisan Action
        function startArisanRollAction() {
            const anggota = window.arisanData.anggotaList || [];
            const eligible = anggota.filter(m => !m.sudahMenang);
            if (eligible.length === 0) {
                alert('Semua anggota telah menang!');
                return;
            }

            const winBox = document.getElementById('arisanDrawWinnerBox');
            const btnRoll = document.getElementById('btnRollArisan');
            if (btnRoll) btnRoll.disabled = true;

            let count = 0;
            const maxRolls = 20;
            const interval = setInterval(() => {
                const randomMem = eligible[Math.floor(Math.random() * eligible.length)];
                if (winBox) winBox.innerText = '🎲 ' + randomMem.nama;
                count++;

                if (count >= maxRolls) {
                    clearInterval(interval);
                    // Select final winner
                    const winner = eligible[Math.floor(Math.random() * eligible.length)];
                    if (winBox) {
                        winBox.innerHTML = '<span class="text-success">🎉 ' + escapeHtmlArisan(winner.nama) + ' 🎉</span>';
                    }

                    const cfg = window.arisanData.config || {};
                    const totalSlots = anggota.reduce((acc, m) => acc + (Number(m.jumlahSlot) || 1), 0);
                    const totalTarikan = totalSlots * (Number(cfg.iuranPokokPerSlot) || 0);
                    const now = new Date();
                    const ymd = now.toISOString().split('T')[0];
                    const receiptNum = 'KWT-ARS-' + ymd.replace(/-/g, '') + '-' + Math.floor(1000 + Math.random() * 9000);

                    // Update winner status
                    winner.sudahMenang = true;
                    winner.periodeMenang = Number(cfg.periodeAktif) || 1;
                    winner.tanggalMenang = ymd;

                    // Add to riwayat
                    const newHist = {
                        id: generateArisanUuid(),
                        periode: Number(cfg.periodeAktif) || 1,
                        tanggalPengundian: ymd,
                        anggotaId: winner.id,
                        namaPemenang: winner.nama,
                        hubunganKeluarga: winner.hubunganKeluarga || 'Kerabat',
                        nomorWa: winner.nomorWa || '',
                        slotKe: 1,
                        nominalTarikan: totalTarikan,
                        nomorKuitansi: receiptNum,
                        catatan: 'Tarikan Arisan Periode ' + cfg.periodeAktif
                    };

                    if (!Array.isArray(window.arisanData.riwayatPemenangList)) {
                        window.arisanData.riwayatPemenangList = [];
                    }
                    window.arisanData.riwayatPemenangList.unshift(newHist);

                    // Advance active period
                    cfg.periodeAktif = (Number(cfg.periodeAktif) || 1) + 1;

                    saveArisanDataToServer();

                    setTimeout(() => {
                        viewReceiptArisan(newHist.id);
                        renderArisanMainView();
                    }, 600);
                }
            }, 100);
        }

        // Toggle Payment Lunas Status
        function togglePaymentStatusArisan(anggotaId, periode, nominalTotal) {
            if (!Array.isArray(window.arisanData.pembayaranList)) {
                window.arisanData.pembayaranList = [];
            }
            const existing = window.arisanData.pembayaranList.find(p => p.periode === periode && p.anggotaId === anggotaId);
            const m = (window.arisanData.anggotaList || []).find(item => item.id === anggotaId);
            const cfg = window.arisanData.config || {};

            if (existing) {
                existing.isLunas = !existing.isLunas;
                if (existing.isLunas) {
                    existing.tanggalBayar = new Date().toISOString().split('T')[0];
                }
            } else {
                const slots = m ? (Number(m.jumlahSlot) || 1) : 1;
                const pokok = slots * (Number(cfg.iuranPokokPerSlot) || 0);
                const kas = slots * (Number(cfg.iuranKasPerSlot) || 0);

                window.arisanData.pembayaranList.push({
                    id: generateArisanUuid(),
                    periode: periode,
                    anggotaId: anggotaId,
                    namaAnggota: m ? m.nama : '',
                    jumlahSlot: slots,
                    iuranPokok: pokok,
                    iuranKas: kas,
                    totalTagihan: nominalTotal,
                    isLunas: true,
                    metodePembayaran: 'Tunai',
                    tanggalBayar: new Date().toISOString().split('T')[0],
                    catatan: ''
                });
            }

            saveArisanDataToServer();
            renderArisanMainView();
        }

        // Tandai Semua Lunas
        function confirmMarkAllPaidArisan(periode) {
            if (!confirm('Tandai semua anggota telah lunas membayar iuran putaran ke-' + periode + '?')) return;

            const cfg = window.arisanData.config || {};
            const totalPerSlot = (Number(cfg.iuranPokokPerSlot) || 0) + (Number(cfg.iuranKasPerSlot) || 0);
            const today = new Date().toISOString().split('T')[0];

            if (!Array.isArray(window.arisanData.pembayaranList)) {
                window.arisanData.pembayaranList = [];
            }

            (window.arisanData.anggotaList || []).forEach(m => {
                const existing = window.arisanData.pembayaranList.find(p => p.periode === periode && p.anggotaId === m.id);
                if (existing) {
                    existing.isLunas = true;
                    existing.tanggalBayar = today;
                } else {
                    const slots = Number(m.jumlahSlot) || 1;
                    window.arisanData.pembayaranList.push({
                        id: generateArisanUuid(),
                        periode: periode,
                        anggotaId: m.id,
                        namaAnggota: m.nama,
                        jumlahSlot: slots,
                        iuranPokok: slots * (Number(cfg.iuranPokokPerSlot) || 0),
                        iuranKas: slots * (Number(cfg.iuranKasPerSlot) || 0),
                        totalTagihan: slots * totalPerSlot,
                        isLunas: true,
                        metodePembayaran: 'Tunai',
                        tanggalBayar: today,
                        catatan: ''
                    });
                }
            });

            saveArisanDataToServer();
            renderArisanMainView();
        }

        // WhatsApp Reminder
        function sendReminderWhatsApp(anggotaId, nominal, periode) {
            const m = (window.arisanData.anggotaList || []).find(item => item.id === anggotaId);
            if (!m) return;
            const cfg = window.arisanData.config || {};

            let text = `Halo ${dol}{m.nama}, mengingatkan untuk pembayaran Iuran Arisan ${dol}{cfg.namaArisan} Putaran ke-${dol}{periode} sebesar ${dol}{formatRupiahArisanWeb(nominal)}. Terima kasih! 🙏`;
            let cleanPhone = (m.nomorWa || '').replace(/[^0-9]/g, '');
            if (cleanPhone.startsWith('0')) cleanPhone = '62' + cleanPhone.substring(1);

            const url = cleanPhone ? `https://wa.me/${dol}{cleanPhone}?text=` + encodeURIComponent(text) : `https://wa.me/?text=` + encodeURIComponent(text);
            window.open(url, '_blank');
        }

        // WhatsApp Invitation Message (Format persis My Singghara)
        function shareArisanWhatsAppInvite() {
            const cfg = window.arisanData.config || {};
            const anggota = window.arisanData.anggotaList || [];
            const totalSlots = anggota.reduce((acc, m) => acc + (Number(m.jumlahSlot) || 1), 0);
            const totalTarikan = totalSlots * (Number(cfg.iuranPokokPerSlot) || 0);
            const lastWinner = (window.arisanData.riwayatPemenangList || [])[0];

            let msg = `*📢 INFORMASI PERTEMUAN ARISAN KELUARGA*\n`;
            msg += `*${dol}{cfg.namaArisan}*\n`;
            msg += `Putaran ke-${dol}{cfg.periodeAktif}\n\n`;
            msg += `📅 *Hari / Tanggal:* ${dol}{cfg.jadwalPertemuanBerikutnya || '-'}\n`;
            msg += `⏰ *Waktu:* ${dol}{cfg.waktuPertemuanBerikutnya || '-'}\n`;
            msg += `📍 *Lokasi / Tuan Rumah:* ${dol}{cfg.lokasiPertemuanBerikutnya || '-'}\n\n`;
            msg += `💰 *Uang Tarikan Pemenang:* ${dol}{formatRupiahArisanWeb(totalTarikan)}\n`;
            msg += `💵 *Iuran Pokok:* ${dol}{formatRupiahArisanWeb(cfg.iuranPokokPerSlot)} / slot\n`;
            msg += `☕ *Iuran Kas Sosial:* ${dol}{formatRupiahArisanWeb(cfg.iuranKasPerSlot)} / slot\n`;
            if (lastWinner) {
                msg += `🏆 *Pemenang Putaran Lalu:* ${dol}{lastWinner.namaPemenang} (${dol}{formatRupiahArisanWeb(lastWinner.nominalTarikan)})\n`;
            }
            if (cfg.catatanPertemuan) {
                msg += `\n📝 *Catatan:* ${dol}{cfg.catatanPertemuan}\n`;
            }
            msg += `\nMohon kehadirannya bapak/ibu & seluruh keluarga sekalian. Terima kasih! 🙏`;

            const url = `https://wa.me/?text=` + encodeURIComponent(msg);
            window.open(url, '_blank');
        }

        // Reset Cycle
        function confirmResetCycleArisan() {
            if (confirm('Mulai siklus putaran baru arisan? Status menang seluruh anggota akan direset kembali menjadi belum menang.')) {
                (window.arisanData.anggotaList || []).forEach(m => {
                    m.sudahMenang = false;
                    m.periodeMenang = null;
                    m.tanggalMenang = null;
                });
                window.arisanData.config.periodeAktif = 1;
                window.arisanData.siklusKe = (Number(window.arisanData.siklusKe) || 1) + 1;
                saveArisanDataToServer();
                renderArisanMainView();
            }
        }

        // Reset Data to Default Empty
        function confirmResetDataDefaultArisan() {
            if (confirm('PERINGATAN: Kosongkan seluruh data arisan (peserta, iuran, kas sosial, dan riwayat pemenang)?')) {
                window.arisanData = {
                    config: {
                        namaArisan: "Arisan Keluarga besar singghara",
                        periodeAktif: 1,
                        iuranPokokPerSlot: 100000.0,
                        iuranKasPerSlot: 20000.0,
                        jadwalPertemuanBerikutnya: "",
                        waktuPertemuanBerikutnya: "",
                        lokasiPertemuanBerikutnya: "",
                        namaKetuaArisan: "",
                        namaBendahara: "",
                        catatanPertemuan: ""
                    },
                    anggotaList: [],
                    pembayaranList: [],
                    kasSosialList: [],
                    riwayatPemenangList: [],
                    siklusKe: 1
                };
                saveArisanDataToServer();
                renderArisanMainView();
            }
        }

        // Delete Kas Entry
        function deleteKasEntryArisan(id) {
            if (confirm('Hapus catatan kas ini?')) {
                window.arisanData.kasSosialList = (window.arisanData.kasSosialList || []).filter(k => k.id !== id);
                saveArisanDataToServer();
                renderArisanMainView();
            }
        }

        // Delete Member
        function deleteAnggotaArisan(id) {
            if (confirm('Hapus anggota ini dari daftar arisan keluarga?')) {
                window.arisanData.anggotaList = (window.arisanData.anggotaList || []).filter(m => m.id !== id);
                window.arisanData.pembayaranList = (window.arisanData.pembayaranList || []).filter(p => p.anggotaId !== id);
                saveArisanDataToServer();
                renderArisanMainView();
            }
        }

        // Delete Riwayat
        function deleteRiwayatArisan(id) {
            if (confirm('Hapus catatan riwayat pemenang ini?')) {
                const item = (window.arisanData.riwayatPemenangList || []).find(h => h.id === id);
                if (item) {
                    const m = (window.arisanData.anggotaList || []).find(a => a.id === item.anggotaId);
                    if (m && m.periodeMenang === item.periode) {
                        m.sudahMenang = false;
                        m.periodeMenang = null;
                        m.tanggalMenang = null;
                    }
                }
                window.arisanData.riwayatPemenangList = (window.arisanData.riwayatPemenangList || []).filter(h => h.id !== id);
                saveArisanDataToServer();
                renderArisanMainView();
            }
        }

        // ==========================================
        // --- MODAL DIALOGS (Bootstrap Responsive) ---
        // ==========================================

        // Modal Tambah / Edit Anggota
        function openAddAnggotaModal(editId) {
            const m = editId ? (window.arisanData.anggotaList || []).find(a => a.id === editId) : null;
            const container = document.getElementById('arisanModalContainer');
            if (!container) return;

            container.innerHTML = `
                <div class="modal fade show d-block" id="modalAnggotaArisan" tabindex="-1" style="background: rgba(0,0,0,0.5);">
                    <div class="modal-dialog modal-dialog-centered">
                        <div class="modal-content rounded-4 border-0 shadow" style="background-color: var(--card-bg, #ffffff); color: var(--text-main, #0f172a);">
                            <div class="modal-header border-bottom">
                                <h6 class="modal-title fw-bold">
                                    <i class="bi bi-person-fill text-primary me-2"></i> ${dol}{m ? 'Edit Anggota Peserta' : 'Tambah Peserta Baru'}
                                </h6>
                                <button type="button" class="btn-close" onclick="closeArisanModal()"></button>
                            </div>
                            <div class="modal-body p-4">
                                <div class="mb-3">
                                    <label class="form-label small fw-semibold">Nama Lengkap Anggota *</label>
                                    <input type="text" class="form-control rounded-3" id="inpNamaAnggota" placeholder="Contoh: Bpk. Singgih Pamungkas" value="${dol}{m ? escapeHtmlArisan(m.nama) : ''}" required>
                                </div>
                                <div class="mb-3">
                                    <label class="form-label small fw-semibold">Hubungan Keluarga</label>
                                    <input type="text" class="form-control rounded-3" id="inpHubunganAnggota" placeholder="Contoh: Kakak / Adik / Paman / Sepupu / Kerabat" value="${dol}{m ? escapeHtmlArisan(m.hubunganKeluarga || '') : 'Kerabat'}">
                                </div>
                                <div class="mb-3">
                                    <label class="form-label small fw-semibold">Nomor WhatsApp / HP</label>
                                    <input type="text" class="form-control rounded-3" id="inpWaAnggota" placeholder="Contoh: 081234567890" value="${dol}{m ? escapeHtmlArisan(m.nomorWa || '') : ''}">
                                </div>
                                <div class="mb-3">
                                    <label class="form-label small fw-semibold">Jumlah Slot Arisan (Kelipatan)</label>
                                    <input type="number" min="1" class="form-control rounded-3" id="inpSlotAnggota" value="${dol}{m ? (m.jumlahSlot || 1) : 1}">
                                    <small class="text-muted">1 slot = 1x kewajiban iuran & 1x hak menang</small>
                                </div>
                            </div>
                            <div class="modal-footer border-top">
                                <button type="button" class="btn btn-outline-secondary rounded-pill px-3" onclick="closeArisanModal()">Batal</button>
                                <button type="button" class="btn btn-primary rounded-pill px-4" onclick="saveAnggotaModalAction('${dol}{editId || ''}')">Simpan Peserta</button>
                            </div>
                        </div>
                    </div>
                </div>
            `;
        }

        function openEditAnggotaModal(id) {
            openAddAnggotaModal(id);
        }

        function saveAnggotaModalAction(editId) {
            const nama = (document.getElementById('inpNamaAnggota')?.value || '').trim();
            if (!nama) {
                alert('Nama anggota wajib diisi!');
                return;
            }
            const hub = (document.getElementById('inpHubunganAnggota')?.value || '').trim() || 'Kerabat';
            const wa = (document.getElementById('inpWaAnggota')?.value || '').trim();
            const slot = Math.max(1, parseInt(document.getElementById('inpSlotAnggota')?.value) || 1);

            if (!Array.isArray(window.arisanData.anggotaList)) {
                window.arisanData.anggotaList = [];
            }

            if (editId) {
                const item = window.arisanData.anggotaList.find(a => a.id === editId);
                if (item) {
                    item.nama = nama;
                    item.hubunganKeluarga = hub;
                    item.nomorWa = wa;
                    item.jumlahSlot = slot;
                }
            } else {
                const newMember = {
                    id: generateArisanUuid(),
                    nama: nama,
                    hubunganKeluarga: hub,
                    nomorWa: wa,
                    jumlahSlot: slot,
                    sudahMenang: false,
                    periodeMenang: null,
                    tanggalMenang: null,
                    catatan: ''
                };
                window.arisanData.anggotaList.push(newMember);
            }

            saveArisanDataToServer();
            closeArisanModal();
            renderArisanMainView();
        }

        // Modal Catat Kas Sosial
        function openAddKasDialog() {
            const container = document.getElementById('arisanModalContainer');
            if (!container) return;

            const today = new Date().toISOString().split('T')[0];

            container.innerHTML = `
                <div class="modal fade show d-block" id="modalKasArisan" tabindex="-1" style="background: rgba(0,0,0,0.5);">
                    <div class="modal-dialog modal-dialog-centered">
                        <div class="modal-content rounded-4 border-0 shadow" style="background-color: var(--card-bg, #ffffff); color: var(--text-main, #0f172a);">
                            <div class="modal-header border-bottom">
                                <h6 class="modal-title fw-bold">
                                    <i class="bi bi-wallet2 text-primary me-2"></i> Catat Transaksi Kas Sosial
                                </h6>
                                <button type="button" class="btn-close" onclick="closeArisanModal()"></button>
                            </div>
                            <div class="modal-body p-4">
                                <div class="mb-3">
                                    <label class="form-label small fw-semibold">Tanggal Transaksi</label>
                                    <input type="date" class="form-control rounded-3" id="inpTglKas" value="${dol}{today}">
                                </div>
                                <div class="mb-3">
                                    <label class="form-label small fw-semibold">Jenis Transaksi</label>
                                    <div class="d-flex gap-3">
                                        <div class="form-check">
                                            <input class="form-check-input" type="radio" name="radioJenisKas" id="kasMasukRadio" value="Masuk" checked>
                                            <label class="form-check-label text-success fw-bold" for="kasMasukRadio">
                                                <i class="bi bi-arrow-down-left"></i> Kas Masuk (Pemasukan)
                                            </label>
                                        </div>
                                        <div class="form-check">
                                            <input class="form-check-input" type="radio" name="radioJenisKas" id="kasKeluarRadio" value="Keluar">
                                            <label class="form-check-label text-danger fw-bold" for="kasKeluarRadio">
                                                <i class="bi bi-arrow-up-right"></i> Kas Keluar (Pengeluaran)
                                            </label>
                                        </div>
                                    </div>
                                </div>
                                <div class="mb-3">
                                    <label class="form-label small fw-semibold">Kategori</label>
                                    <select class="form-select rounded-3" id="inpKatKas">
                                        <option value="Iuran Kas Anggota">Iuran Kas Anggota</option>
                                        <option value="Konsumsi Pertemuan">Konsumsi Pertemuan</option>
                                        <option value="Snack & Minuman">Snack & Minuman</option>
                                        <option value="Santunan / Dana Darurat">Santunan / Dana Darurat</option>
                                        <option value="Sewa Perlengkapan">Sewa Perlengkapan</option>
                                        <option value="Lain-lain">Lain-lain</option>
                                    </select>
                                </div>
                                <div class="mb-3">
                                    <label class="form-label small fw-semibold">Nominal (Rp) *</label>
                                    <input type="number" min="0" step="1000" class="form-control rounded-3" id="inpNominalKas" placeholder="Contoh: 50000" required>
                                </div>
                                <div class="mb-3">
                                    <label class="form-label small fw-semibold">Keterangan / Catatan</label>
                                    <input type="text" class="form-control rounded-3" id="inpKetKas" placeholder="Rincian catatan transaksi...">
                                </div>
                            </div>
                            <div class="modal-footer border-top">
                                <button type="button" class="btn btn-outline-secondary rounded-pill px-3" onclick="closeArisanModal()">Batal</button>
                                <button type="button" class="btn btn-primary rounded-pill px-4" onclick="saveKasModalAction()">Simpan Catatan Kas</button>
                            </div>
                        </div>
                    </div>
                </div>
            `;
        }

        function saveKasModalAction() {
            const nominal = Math.max(0, parseFloat(document.getElementById('inpNominalKas')?.value) || 0);
            if (nominal <= 0) {
                alert('Nominal transaksi kas harus lebih besar dari 0!');
                return;
            }
            const tgl = (document.getElementById('inpTglKas')?.value || '').trim() || new Date().toISOString().split('T')[0];
            const jenis = document.querySelector('input[name="radioJenisKas"]:checked')?.value || 'Masuk';
            const kat = (document.getElementById('inpKatKas')?.value || '').trim() || 'Lain-lain';
            const ket = (document.getElementById('inpKetKas')?.value || '').trim();

            if (!Array.isArray(window.arisanData.kasSosialList)) {
                window.arisanData.kasSosialList = [];
            }

            window.arisanData.kasSosialList.unshift({
                id: generateArisanUuid(),
                tanggal: tgl,
                jenis: jenis,
                kategori: kat,
                deskripsi: ket,
                nominal: nominal
            });

            saveArisanDataToServer();
            closeArisanModal();
            renderArisanMainView();
        }

        // Modal Pengaturan Arisan
        function openArisanConfigModal() {
            const cfg = window.arisanData.config || {};
            const container = document.getElementById('arisanModalContainer');
            if (!container) return;

            container.innerHTML = `
                <div class="modal fade show d-block" id="modalConfigArisan" tabindex="-1" style="background: rgba(0,0,0,0.5);">
                    <div class="modal-dialog modal-dialog-centered modal-lg">
                        <div class="modal-content rounded-4 border-0 shadow" style="background-color: var(--card-bg, #ffffff); color: var(--text-main, #0f172a);">
                            <div class="modal-header border-bottom">
                                <h6 class="modal-title fw-bold">
                                    <i class="bi bi-gear-fill text-primary me-2"></i> Pengaturan Arisan Keluarga
                                </h6>
                                <button type="button" class="btn-close" onclick="closeArisanModal()"></button>
                            </div>
                            <div class="modal-body p-4">
                                <div class="row g-3">
                                    <div class="col-md-6 col-12">
                                        <label class="form-label small fw-semibold">Nama Kelompok Arisan</label>
                                        <input type="text" class="form-control rounded-3" id="inpCfgNama" value="${dol}{escapeHtmlArisan(cfg.namaArisan || 'Arisan Keluarga besar singghara')}">
                                    </div>
                                    <div class="col-md-6 col-12">
                                        <label class="form-label small fw-semibold">Putaran / Periode Aktif</label>
                                        <input type="number" min="1" class="form-control rounded-3" id="inpCfgPeriode" value="${dol}{cfg.periodeAktif || 1}">
                                    </div>
                                    <div class="col-md-6 col-12">
                                        <label class="form-label small fw-semibold">Nominal Iuran Pokok per Slot (Rp)</label>
                                        <input type="number" min="0" step="5000" class="form-control rounded-3" id="inpCfgPokok" value="${dol}{cfg.iuranPokokPerSlot || 100000}">
                                    </div>
                                    <div class="col-md-6 col-12">
                                        <label class="form-label small fw-semibold">Nominal Kas Sosial per Slot (Rp)</label>
                                        <input type="number" min="0" step="1000" class="form-control rounded-3" id="inpCfgKas" value="${dol}{cfg.iuranKasPerSlot || 20000}">
                                    </div>
                                    <div class="col-md-6 col-12">
                                        <label class="form-label small fw-semibold">Tanggal Pertemuan Berikutnya</label>
                                        <input type="text" class="form-control rounded-3" id="inpCfgTgl" placeholder="Contoh: Minggu, 25 Oktober 2026" value="${dol}{escapeHtmlArisan(cfg.jadwalPertemuanBerikutnya || '')}">
                                    </div>
                                    <div class="col-md-6 col-12">
                                        <label class="form-label small fw-semibold">Waktu Pertemuan</label>
                                        <input type="text" class="form-control rounded-3" id="inpCfgWaktu" placeholder="Contoh: 10:00 WIB" value="${dol}{escapeHtmlArisan(cfg.waktuPertemuanBerikutnya || '')}">
                                    </div>
                                    <div class="col-12">
                                        <label class="form-label small fw-semibold">Lokasi / Tuan Rumah Pertemuan</label>
                                        <input type="text" class="form-control rounded-3" id="inpCfgLokasi" placeholder="Kediaman keluarga..." value="${dol}{escapeHtmlArisan(cfg.lokasiPertemuanBerikutnya || '')}">
                                    </div>
                                    <div class="col-md-6 col-12">
                                        <label class="form-label small fw-semibold">Nama Ketua Arisan</label>
                                        <input type="text" class="form-control rounded-3" id="inpCfgKetua" value="${dol}{escapeHtmlArisan(cfg.namaKetuaArisan || '')}">
                                    </div>
                                    <div class="col-md-6 col-12">
                                        <label class="form-label small fw-semibold">Nama Bendahara</label>
                                        <input type="text" class="form-control rounded-3" id="inpCfgBendahara" value="${dol}{escapeHtmlArisan(cfg.namaBendahara || '')}">
                                    </div>
                                    <div class="col-12">
                                        <label class="form-label small fw-semibold">Catatan / Pengumuman Pertemuan</label>
                                        <textarea class="form-control rounded-3" id="inpCfgCatatan" rows="2">${dol}{escapeHtmlArisan(cfg.catatanPertemuan || '')}</textarea>
                                    </div>
                                </div>
                            </div>
                            <div class="modal-footer border-top">
                                <button type="button" class="btn btn-outline-secondary rounded-pill px-3" onclick="closeArisanModal()">Batal</button>
                                <button type="button" class="btn btn-primary rounded-pill px-4" onclick="saveArisanConfigAction()">Simpan Pengaturan</button>
                            </div>
                        </div>
                    </div>
                </div>
            `;
        }

        function saveArisanConfigAction() {
            const nama = (document.getElementById('inpCfgNama')?.value || '').trim() || 'Arisan Keluarga besar singghara';
            const periode = Math.max(1, parseInt(document.getElementById('inpCfgPeriode')?.value) || 1);
            const pokok = Math.max(0, parseFloat(document.getElementById('inpCfgPokok')?.value) || 0);
            const kas = Math.max(0, parseFloat(document.getElementById('inpCfgKas')?.value) || 0);
            const tgl = (document.getElementById('inpCfgTgl')?.value || '').trim();
            const waktu = (document.getElementById('inpCfgWaktu')?.value || '').trim();
            const lokasi = (document.getElementById('inpCfgLokasi')?.value || '').trim();
            const ketua = (document.getElementById('inpCfgKetua')?.value || '').trim();
            const bendahara = (document.getElementById('inpCfgBendahara')?.value || '').trim();
            const catatan = (document.getElementById('inpCfgCatatan')?.value || '').trim();

            window.arisanData.config = {
                namaArisan: nama,
                periodeAktif: periode,
                iuranPokokPerSlot: pokok,
                iuranKasPerSlot: kas,
                jadwalPertemuanBerikutnya: tgl,
                waktuPertemuanBerikutnya: waktu,
                lokasiPertemuanBerikutnya: lokasi,
                namaKetuaArisan: ketua,
                namaBendahara: bendahara,
                catatanPertemuan: catatan
            };

            saveArisanDataToServer();
            closeArisanModal();
            renderArisanMainView();
        }

        // Modal Kuitansi Resmi Penyerahan Uang Arisan (Tampilan & Format Persis My Singghara)
        function viewReceiptArisan(histId) {
            const riwayat = (window.arisanData.riwayatPemenangList || []).find(h => h.id === histId);
            if (!riwayat) return;

            const cfg = window.arisanData.config || {};
            const container = document.getElementById('arisanModalContainer');
            if (!container) return;

            const terbilangStr = terbilangAngkaArisan(riwayat.nominalTarikan);

            container.innerHTML = `
                <div class="modal fade show d-block" id="modalKuitansiArisan" tabindex="-1" style="background: rgba(0,0,0,0.65);">
                    <div class="modal-dialog modal-dialog-centered modal-lg">
                        <div class="modal-content rounded-4 border-0 shadow">
                            <div class="modal-header border-bottom py-2 px-3">
                                <h6 class="modal-title fw-bold text-dark">
                                    <i class="bi bi-receipt text-primary me-2"></i> Bukti Kuitansi Penyerahan Uang
                                </h6>
                                <button type="button" class="btn-close" onclick="closeArisanModal()"></button>
                            </div>
                            <div class="modal-body p-4 bg-light">
                                <!-- KUITANSI RESMI FRAME -->
                                <div id="kuitansiPrintArea" class="bg-white p-4 rounded-3 border border-2 shadow-sm position-relative mx-auto" style="max-width: 680px; border-color: #1e293b !important; font-family: 'Times New Roman', Times, serif;">
                                    <div class="border border-1 p-4" style="border-color: #94a3b8 !important;">
                                        <div class="text-center mb-3">
                                            <h4 class="fw-bold mb-1" style="color: #0f172a; letter-spacing: 1px;">KUITANSI PENYERAHAN UANG ARISAN</h4>
                                            <h6 class="text-secondary mb-2 fst-italic">${dol}{escapeHtmlArisan(cfg.namaArisan || 'Arisan Keluarga besar singghara')}</h6>
                                            <hr style="border-top: 2px solid #0f172a; opacity: 1;" class="my-2">
                                        </div>

                                        <table class="table table-borderless table-sm mb-3" style="font-size: 1.05rem;">
                                            <tbody>
                                                <tr>
                                                    <td style="width: 230px;" class="fw-bold text-secondary">Nomor Bukti</td>
                                                    <td style="width: 15px;">:</td>
                                                    <td class="fw-bold text-dark">${dol}{escapeHtmlArisan(riwayat.nomorKuitansi)}</td>
                                                </tr>
                                                <tr>
                                                    <td class="fw-bold text-secondary">Periode Putaran</td>
                                                    <td>:</td>
                                                    <td class="fw-bold">Putaran Ke-${dol}{riwayat.periode}</td>
                                                </tr>
                                                <tr>
                                                    <td class="fw-bold text-secondary">Tanggal Pengundian</td>
                                                    <td>:</td>
                                                    <td>${dol}{escapeHtmlArisan(riwayat.tanggalPengundian)}</td>
                                                </tr>
                                                <tr>
                                                    <td class="fw-bold text-secondary">Telah Diserahkan Kepada</td>
                                                    <td>:</td>
                                                    <td class="fw-bold fs-5 text-dark">${dol}{escapeHtmlArisan(riwayat.namaPemenang)}</td>
                                                </tr>
                                                <tr>
                                                    <td class="fw-bold text-secondary">Hubungan Keluarga</td>
                                                    <td>:</td>
                                                    <td>${dol}{escapeHtmlArisan(riwayat.hubunganKeluarga)}</td>
                                                </tr>
                                                <tr>
                                                    <td class="fw-bold text-secondary">Jumlah Uang Tarikan</td>
                                                    <td>:</td>
                                                    <td class="fw-bold text-success fs-5">${dol}{formatRupiahArisanWeb(riwayat.nominalTarikan)}</td>
                                                </tr>
                                                <tr>
                                                    <td class="fw-bold text-secondary align-top">Terbilang</td>
                                                    <td class="align-top">:</td>
                                                    <td class="fst-italic fw-semibold" style="color: #0f766e;">"${dol}{terbilangStr}"</td>
                                                </tr>
                                                <tr>
                                                    <td class="fw-bold text-secondary">Untuk Keperluan</td>
                                                    <td>:</td>
                                                    <td>Tarikan Uang Arisan Keluarga Putaran ${dol}{riwayat.periode}</td>
                                                </tr>
                                                <tr>
                                                    <td class="fw-bold text-secondary">Catatan</td>
                                                    <td>:</td>
                                                    <td>${dol}{escapeHtmlArisan(riwayat.catatan || 'Lunas diserahkan kepada pemenang')}</td>
                                                </tr>
                                            </tbody>
                                        </table>

                                        <!-- AREA TANDA TANGAN -->
                                        <div class="row pt-4 text-center mt-3" style="font-size: 0.95rem;">
                                            <div class="col-6">
                                                <p class="text-muted mb-5">Yang Menerima (Pemenang),</p>
                                                <p class="fw-bold text-dark mb-0">(${dol}{escapeHtmlArisan(riwayat.namaPemenang)})</p>
                                            </div>
                                            <div class="col-6">
                                                <p class="text-muted mb-5">Yang Menyerahkan (Bendahara),</p>
                                                <p class="fw-bold text-dark mb-0">(${dol}{escapeHtmlArisan(cfg.namaBendahara || 'Bendahara Arisan')})</p>
                                            </div>
                                        </div>
                                    </div>
                                </div>
                            </div>
                            <div class="modal-footer border-top d-flex justify-content-between">
                                <button type="button" class="btn btn-outline-success rounded-pill px-3" onclick="copyReceiptText('${dol}{riwayat.id}')">
                                    <i class="bi bi-clipboard me-1"></i> Salin Teks Kuitansi
                                </button>
                                <div class="d-flex gap-2">
                                    <button type="button" class="btn btn-outline-secondary rounded-pill px-3" onclick="closeArisanModal()">Tutup</button>
                                    <button type="button" class="btn btn-primary rounded-pill px-4 fw-semibold" onclick="printReceiptDirectly('${dol}{riwayat.id}')">
                                        <i class="bi bi-printer me-1"></i> Cetak Kuitansi
                                    </button>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            `;
        }

        function closeArisanModal() {
            const container = document.getElementById('arisanModalContainer');
            if (container) container.innerHTML = '';
        }

        // Print Kuitansi
        function printReceiptDirectly(histId) {
            const riwayat = (window.arisanData.riwayatPemenangList || []).find(h => h.id === histId);
            if (!riwayat) return;
            const cfg = window.arisanData.config || {};
            const terbilangStr = terbilangAngkaArisan(riwayat.nominalTarikan);

            const printWin = window.open('', '_blank');
            printWin.document.write(`
                <!DOCTYPE html>
                <html>
                <head>
                    <title>Kuitansi Arisan - ${dol}{escapeHtmlArisan(riwayat.nomorKuitansi)}</title>
                    <style>
                        body { font-family: 'Times New Roman', serif; padding: 30px; margin: 0; }
                        .frame { border: 2px solid #0f172a; padding: 25px; border-radius: 8px; max-width: 650px; margin: auto; }
                        .inner { border: 1px solid #94a3b8; padding: 25px; }
                        .title { text-align: center; font-size: 20px; font-weight: bold; margin-bottom: 4px; }
                        .sub { text-align: center; font-size: 14px; font-style: italic; color: #475569; margin-bottom: 15px; }
                        hr { border: none; border-top: 2px solid #0f172a; margin-bottom: 20px; }
                        table { width: 100%; border-collapse: collapse; font-size: 15px; }
                        td { padding: 6px 4px; vertical-align: top; }
                        .label { width: 220px; font-weight: bold; color: #475569; }
                        .val { font-weight: bold; }
                        .terbilang { font-style: italic; color: #0f766e; }
                        .sig { margin-top: 50px; display: flex; justify-content: space-between; text-align: center; font-size: 14px; }
                        .sig-col { width: 45%; }
                    </style>
                </head>
                <body>
                    <div class="frame">
                        <div class="inner">
                            <div class="title">KUITANSI PENYERAHAN UANG ARISAN</div>
                            <div class="sub">${dol}{escapeHtmlArisan(cfg.namaArisan || 'Arisan Keluarga besar singghara')}</div>
                            <hr>
                            <table>
                                <tr><td class="label">Nomor Bukti</td><td>:</td><td class="val">${dol}{escapeHtmlArisan(riwayat.nomorKuitansi)}</td></tr>
                                <tr><td class="label">Periode Putaran</td><td>:</td><td class="val">Putaran Ke-${dol}{riwayat.periode}</td></tr>
                                <tr><td class="label">Tanggal Pengundian</td><td>:</td><td>${dol}{escapeHtmlArisan(riwayat.tanggalPengundian)}</td></tr>
                                <tr><td class="label">Telah Diserahkan Kepada</td><td>:</td><td class="val" style="font-size: 16px;">${dol}{escapeHtmlArisan(riwayat.namaPemenang)}</td></tr>
                                <tr><td class="label">Hubungan Keluarga</td><td>:</td><td>${dol}{escapeHtmlArisan(riwayat.hubunganKeluarga)}</td></tr>
                                <tr><td class="label">Jumlah Uang Tarikan</td><td>:</td><td class="val" style="font-size: 16px;">${dol}{formatRupiahArisanWeb(riwayat.nominalTarikan)}</td></tr>
                                <tr><td class="label">Terbilang</td><td>:</td><td class="terbilang">"${dol}{terbilangStr}"</td></tr>
                                <tr><td class="label">Untuk Keperluan</td><td>:</td><td>Tarikan Uang Arisan Keluarga Putaran ${dol}{riwayat.periode}</td></tr>
                                <tr><td class="label">Catatan</td><td>:</td><td>${dol}{escapeHtmlArisan(riwayat.catatan || 'Lunas diserahkan kepada pemenang')}</td></tr>
                            </table>

                            <div class="sig">
                                <div class="sig-col" style="float: left;">
                                    <p style="margin-bottom: 60px;">Yang Menerima (Pemenang),</p>
                                    <p><strong>(${dol}{escapeHtmlArisan(riwayat.namaPemenang)})</strong></p>
                                </div>
                                <div class="sig-col" style="float: right;">
                                    <p style="margin-bottom: 60px;">Yang Menyerahkan (Bendahara),</p>
                                    <p><strong>(${dol}{escapeHtmlArisan(cfg.namaBendahara || 'Bendahara Arisan')})</strong></p>
                                </div>
                                <div style="clear: both;"></div>
                            </div>
                        </div>
                    </div>
                </body>
                </html>
            `);
            printWin.document.close();
            printWin.focus();
            setTimeout(() => {
                try {
                    printWin.print();
                } catch (e) {
                    console.error('Print receipt error:', e);
                }
            }, 300);
        }

        // Salin Teks Kuitansi
        function copyReceiptText(histId) {
            const riwayat = (window.arisanData.riwayatPemenangList || []).find(h => h.id === histId);
            if (!riwayat) return;
            const cfg = window.arisanData.config || {};
            const terbilangStr = terbilangAngkaArisan(riwayat.nominalTarikan);

            let txt = `*KUITANSI PENYERAHAN UANG ARISAN*\n`;
            txt += `*${dol}{cfg.namaArisan}*\n`;
            txt += `------------------------------------\n`;
            txt += `Nomor Bukti : ${dol}{riwayat.nomorKuitansi}\n`;
            txt += `Periode : Putaran Ke-${dol}{riwayat.periode}\n`;
            txt += `Tanggal : ${dol}{riwayat.tanggalPengundian}\n`;
            txt += `Penerima : ${dol}{riwayat.namaPemenang} (${dol}{riwayat.hubunganKeluarga})\n`;
            txt += `Jumlah : ${dol}{formatRupiahArisanWeb(riwayat.nominalTarikan)}\n`;
            txt += `Terbilang : "${dol}{terbilangStr}"\n`;
            txt += `Catatan : ${dol}{riwayat.catatan || 'Lunas diserahkan'}\n`;
            txt += `Bendahara : ${dol}{cfg.namaBendahara || 'Bendahara Arisan'}\n`;

            if (navigator.clipboard) {
                navigator.clipboard.writeText(txt).then(() => {
                    alert('Teks kuitansi berhasil disalin!');
                });
            } else {
                alert('Teks kuitansi:\n\n' + txt);
            }
        }

        // Share Receipt via WhatsApp
        function shareReceiptWhatsApp(histId) {
            const riwayat = (window.arisanData.riwayatPemenangList || []).find(h => h.id === histId);
            if (!riwayat) return;
            const cfg = window.arisanData.config || {};
            const terbilangStr = terbilangAngkaArisan(riwayat.nominalTarikan);

            let msg = `*📜 BUKTI KUITANSI ARISAN KELUARGA*\n`;
            msg += `*${dol}{cfg.namaArisan}*\n\n`;
            msg += `📄 *Nomor Bukti:* ${dol}{riwayat.nomorKuitansi}\n`;
            msg += `🎯 *Putaran:* Ke-${dol}{riwayat.periode}\n`;
            msg += `📅 *Tanggal:* ${dol}{riwayat.tanggalPengundian}\n`;
            msg += `👤 *Pemenang:* ${dol}{riwayat.namaPemenang} (${dol}{riwayat.hubunganKeluarga})\n`;
            msg += `💰 *Jumlah Tarikan:* ${dol}{formatRupiahArisanWeb(riwayat.nominalTarikan)}\n`;
            msg += `✍️ *Terbilang:* _${dol}{terbilangStr}_\n`;
            msg += `📝 *Catatan:* ${dol}{riwayat.catatan || 'Lunas diserahkan'}\n\n`;
            msg += `Selamat kepada pemenang arisan putaran ini! 🎊`;

            let cleanPhone = (riwayat.nomorWa || '').replace(/[^0-9]/g, '');
            if (cleanPhone.startsWith('0')) cleanPhone = '62' + cleanPhone.substring(1);

            const url = cleanPhone ? `https://wa.me/${dol}{cleanPhone}?text=` + encodeURIComponent(msg) : `https://wa.me/?text=` + encodeURIComponent(msg);
            window.open(url, '_blank');
        }

        // Export Rekapitulasi Excel (.csv)
        function exportArisanToExcelWeb() {
            const cfg = window.arisanData.config || {};
            const anggota = window.arisanData.anggotaList || [];
            const pembayaran = window.arisanData.pembayaranList || [];
            const kas = window.arisanData.kasSosialList || [];
            const riwayat = window.arisanData.riwayatPemenangList || [];

            const totalSlots = anggota.reduce((acc, m) => acc + (Number(m.jumlahSlot) || 1), 0);
            const nominalPokok = Number(cfg.iuranPokokPerSlot) || 0;
            const nominalKas = Number(cfg.iuranKasPerSlot) || 0;
            const totalTarikan = totalSlots * nominalPokok;

            const totalKasMasuk = kas.filter(k => k.jenis === 'Masuk').reduce((acc, k) => acc + (Number(k.nominal) || 0), 0);
            const totalKasKeluar = kas.filter(k => k.jenis === 'Keluar').reduce((acc, k) => acc + (Number(k.nominal) || 0), 0);
            const saldoKas = totalKasMasuk - totalKasKeluar;
            const periode = Number(cfg.periodeAktif) || 1;

            const now = new Date();
            const dateStr = now.toLocaleDateString('id-ID', { day: '2-digit', month: 'long', year: 'numeric' });
            const timeStr = now.toLocaleTimeString('id-ID', { hour: '2-digit', minute: '2-digit' });

            let csv = '\uFEFF'; // UTF-8 BOM untuk Microsoft Excel & Google Sheets
            csv += 'LAPORAN REKAPITULASI KEUANGAN ARISAN KELUARGA\n';
            csv += 'Nama Arisan,' + escapeCsvArisan(cfg.namaArisan || 'Arisan Keluarga besar singghara') + '\n';
            csv += 'Putaran / Periode Aktif,#' + periode + '\n';
            csv += 'Tanggal Pertemuan,' + escapeCsvArisan(cfg.jadwalPertemuanBerikutnya || '-') + '\n';
            csv += 'Lokasi Pertemuan,' + escapeCsvArisan(cfg.lokasiPertemuanBerikutnya || '-') + '\n';
            csv += 'Iuran Pokok per Slot,' + nominalPokok + '\n';
            csv += 'Iuran Kas per Slot,' + nominalKas + '\n';
            csv += 'Total Wajib Bayar per Slot,' + (nominalPokok + nominalKas) + '\n';
            csv += 'Total Peserta Terdaftar,' + anggota.length + '\n';
            csv += 'Total Slot Peserta,' + totalSlots + '\n';
            csv += 'Total Uang Tarikan Putaran,' + totalTarikan + '\n';
            csv += 'Total Kas Sosial Masuk,' + totalKasMasuk + '\n';
            csv += 'Total Kas Sosial Keluar,' + totalKasKeluar + '\n';
            csv += 'Saldo Kas Sosial Saat Ini,' + saldoKas + '\n';
            csv += 'Tanggal Export,' + escapeCsvArisan(dateStr + ' ' + timeStr) + '\n\n';

            // 1. TABEL ANGGOTA & IURAN
            csv += '=== TABEL 1: DAFTAR PESERTA & STATUS IURAN PERIODE #' + periode + ' ===\n';
            csv += 'No,Nama Anggota,Hubungan Keluarga,No. WhatsApp,Jumlah Slot,Iuran Pokok,Iuran Kas,Total Tagihan,Status Pembayaran,Metode Bayar,Tanggal Bayar,Status Undian\n';
            anggota.forEach((m, idx) => {
                const slots = Number(m.jumlahSlot) || 1;
                const tagihanPokok = slots * nominalPokok;
                const tagihanKas = slots * nominalKas;
                const totalTagihan = tagihanPokok + tagihanKas;
                const p = pembayaran.find(item => item.periode === periode && item.anggotaId === m.id);
                const isPaid = p && p.isLunas;
                const statusBayar = isPaid ? 'Lunas' : 'Belum Bayar';
                const metode = isPaid ? (p.metodePembayaran || 'Tunai') : '-';
                const tglBayar = (isPaid && p.tanggalBayar) ? p.tanggalBayar : '-';
                const statusMenang = m.sudahMenang ? ('Sudah Menang (Putaran #' + (m.periodeMenang || '1') + ')') : 'Belum Menang';

                csv += (idx + 1) + ',' +
                    escapeCsvArisan(m.nama) + ',' +
                    escapeCsvArisan(m.hubunganKeluarga || 'Kerabat') + ',' +
                    escapeCsvArisan(m.nomorWa || '-') + ',' +
                    slots + ',' +
                    tagihanPokok + ',' +
                    tagihanKas + ',' +
                    totalTagihan + ',' +
                    escapeCsvArisan(statusBayar) + ',' +
                    escapeCsvArisan(metode) + ',' +
                    escapeCsvArisan(tglBayar) + ',' +
                    escapeCsvArisan(statusMenang) + '\n';
            });
            csv += '\n';

            // 2. TABEL BUKU KAS SOSIAL
            csv += '=== TABEL 2: BUKU KAS SOSIAL ARISAN ===\n';
            csv += 'No,Tanggal,Jenis Transaksi,Kategori,Keterangan,Nominal Masuk,Nominal Keluar\n';
            if (kas.length === 0) {
                csv += 'Belum ada catatan kas sosial\n';
            } else {
                kas.forEach((k, idx) => {
                    const nom = Number(k.nominal) || 0;
                    const masuk = k.jenis === 'Masuk' ? nom : 0;
                    const keluar = k.jenis === 'Keluar' ? nom : 0;
                    csv += (idx + 1) + ',' +
                        escapeCsvArisan(k.tanggal) + ',' +
                        escapeCsvArisan(k.jenis) + ',' +
                        escapeCsvArisan(k.kategori || 'Umum') + ',' +
                        escapeCsvArisan(k.deskripsi || '-') + ',' +
                        masuk + ',' +
                        keluar + '\n';
                });
            }
            csv += '\n';

            // 3. TABEL RIWAYAT PEMENANG
            csv += '=== TABEL 3: RIWAYAT PEMENANG UNDIAN ARISAN ===\n';
            csv += 'No,Putaran,Tanggal Undian,Nama Pemenang,Nomor Kuitansi,Nominal Tarikan,Catatan\n';
            if (riwayat.length === 0) {
                csv += 'Belum ada riwayat pemenang\n';
            } else {
                riwayat.forEach((h, idx) => {
                    csv += (idx + 1) + ',' +
                        'Putaran #' + h.periode + ',' +
                        escapeCsvArisan(h.tanggalPengundian) + ',' +
                        escapeCsvArisan(h.namaPemenang) + ',' +
                        escapeCsvArisan(h.nomorKuitansi) + ',' +
                        (Number(h.nominalTarikan) || 0) + ',' +
                        escapeCsvArisan(h.catatan || '-') + '\n';
                });
            }

            const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
            const link = document.createElement('a');
            const ymd = now.toISOString().slice(0, 10).replace(/-/g, '');
            const hms = String(now.getHours()).padStart(2, '0') + String(now.getMinutes()).padStart(2, '0') + String(now.getSeconds()).padStart(2, '0');
            link.href = URL.createObjectURL(blob);
            link.download = 'Rekap_Arisan_Keluarga_' + ymd + '_' + hms + '.csv';
            document.body.appendChild(link);
            link.click();
            document.body.removeChild(link);
        }
        """.trimIndent()
    }
}
