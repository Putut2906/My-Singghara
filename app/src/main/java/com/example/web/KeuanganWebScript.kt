package com.example.web

object KeuanganWebScript {
    fun getScript(dol: String): String {
        return """
        // ==========================================
        // --- MODUL LAPORAN KEUANGAN LENGKAP ---
        // ==========================================
        window.keuanganJournalEntries = [];
        window.keuanganAdjustmentEntries = [];
        window.selectedLedgerAccount = "";
        window.adjActiveSubTab = 0; // 0: Entri Jurnal Penyesuaian, 1: Neraca Setelah Penyesuaian

        const KEUANGAN_ACCOUNTS_ORDERED = [
            "Kas",
            "Piutang Usaha", "Perlengkapan", "Sewa dibayar di muka", "Asuransi dibayar di muka",
            "Peralatan", "Akumulasi penyusutan peralatan", "Kendaraan", "Akumulasi penyusutan kendaraan",
            "Utang usaha", "Utang gaji", "Utang bank", "Pendapatan diterima di muka",
            "Modal pemilik", "Prive",
            "Pendapatan jasa", "Pendapatan penjualan", "Pendapatan lain-lain",
            "Harga Pokok Penjualan", "Beban gaji", "Beban sewa", "Beban perlengkapan", 
            "Beban asuransi", "Beban penyusutan peralatan", "Beban penyusutan kendaraan", 
            "Beban listrik air telepon", "Beban iklan", "Beban lain-lain"
        ];

        const KEUANGAN_GROUPED_ACCOUNTS = [
            { category: "Kas", accounts: ["Kas"] },
            { category: "Aset", accounts: [
                "Piutang Usaha", "Perlengkapan", "Sewa dibayar di muka", 
                "Asuransi dibayar di muka", "Peralatan", "Akumulasi penyusutan peralatan", 
                "Kendaraan", "Akumulasi penyusutan kendaraan"
            ]},
            { category: "Liabilitas", accounts: ["Utang usaha", "Utang gaji", "Utang bank", "Pendapatan diterima di muka"] },
            { category: "Ekuitas", accounts: ["Modal pemilik", "Prive"] },
            { category: "Pendapatan", accounts: ["Pendapatan jasa", "Pendapatan penjualan", "Pendapatan lain-lain"] },
            { category: "Beban", accounts: [
                "Harga Pokok Penjualan", "Beban gaji", "Beban sewa", "Beban perlengkapan", 
                "Beban asuransi", "Beban penyusutan peralatan", "Beban penyusutan kendaraan", 
                "Beban listrik air telepon", "Beban iklan", "Beban lain-lain"
            ]}
        ];

        const KEUANGAN_ACCOUNT_REFS = {
            "Kas": "101", "Piutang Usaha": "102", "Perlengkapan": "103", "Sewa dibayar di muka": "104", "Asuransi dibayar di muka": "105",
            "Peralatan": "121", "Akumulasi penyusutan peralatan": "122", "Kendaraan": "123", "Akumulasi penyusutan kendaraan": "124",
            "Utang usaha": "201", "Utang gaji": "202", "Utang bank": "203", "Pendapatan diterima di muka": "204",
            "Modal pemilik": "301", "Prive": "302", "Ikhtisar Laba Rugi": "303",
            "Pendapatan jasa": "401", "Pendapatan penjualan": "402", "Pendapatan lain-lain": "403", "Piutang pendapatan": "106",
            "Harga Pokok Penjualan": "501", "Beban gaji": "502", "Beban sewa": "503", "Beban perlengkapan": "504",
            "Beban asuransi": "505", "Beban penyusutan peralatan": "506", "Beban penyusutan kendaraan": "507",
            "Beban listrik air telepon": "508", "Beban iklan": "509", "Beban lain-lain": "510"
        };

        function getKeuanganAccountRef(acc) {
            if (KEUANGAN_ACCOUNT_REFS[acc]) return KEUANGAN_ACCOUNT_REFS[acc];
            const l = (acc || '').toLowerCase();
            if (l.startsWith("kas")) return "101";
            if (l.startsWith("piutang")) return "102";
            if (l.startsWith("perlengkapan")) return "103";
            if (l.startsWith("utang")) return "201";
            if (l.startsWith("modal")) return "301";
            if (l.startsWith("prive")) return "302";
            if (l.startsWith("pendapatan")) return "401";
            if (l.startsWith("beban") || l.startsWith("biaya")) return "501";
            return "100";
        }

        function getKeuanganNormalBalanceType(acc) {
            const l = (acc || '').toLowerCase();
            if (l.includes("akumulasi penyusutan")) return "K";
            if (l === "kas" || l.includes("piutang") || l.includes("perlengkapan") ||
                l.includes("dibayar di muka") || l.includes("peralatan") || l.includes("kendaraan") ||
                l.includes("beban") || l.includes("biaya") || l.includes("harga pokok penjualan") ||
                l === "prive") {
                return "D";
            }
            return "K";
        }

        function formatRupiah(val) {
            const n = Math.round(Number(val) || 0);
            return 'Rp ' + n.toLocaleString('id-ID');
        }

        async function fetchKeuanganData() {
            try {
                const [rJurnal, rAdj] = await Promise.all([
                    fetch('/api/keuangan/jurnal'),
                    fetch('/api/keuangan/penyesuaian')
                ]);
                window.keuanganJournalEntries = await rJurnal.json();
                window.keuanganAdjustmentEntries = await rAdj.json();
            } catch (e) {
                console.error("Gagal sinkronisasi data keuangan:", e);
            }
        }

        function renderKeuanganSubNav(activeKey) {
            const tabs = [
                { key: 'jurnal_umum', label: 'Jurnal Umum', icon: 'bi-journal-text' },
                { key: 'buku_besar', label: 'Buku Besar', icon: 'bi-book' },
                { key: 'neraca_saldo', label: 'Neraca Saldo', icon: 'bi-calculator' },
                { key: 'jurnal_penyesuaian', label: 'Jurnal Penyesuaian', icon: 'bi-pencil-square' },
                { key: 'neraca_saldo_disesuaikan', label: 'Neraca S. Disesuaikan', icon: 'bi-table' },
                { key: 'laba_rugi', label: 'Laba Rugi', icon: 'bi-graph-up' },
                { key: 'jurnal_penutup', label: 'Jurnal Penutup', icon: 'bi-lock-fill' },
                { key: 'neraca_saldo_setelah_tutup', label: 'Neraca Setelah Tutup', icon: 'bi-file-earmark-ruled' }
            ];

            let html = '<div class="d-flex flex-wrap gap-1 p-1 mb-4 rounded-3" style="background: var(--table-hover); border: 1px solid var(--border-color);">';
            tabs.forEach(t => {
                const isAct = (t.key === activeKey);
                html += `<button class="btn btn-sm ${dol}{isAct ? 'btn-brand text-white shadow-sm' : 'btn-outline-secondary border-0 text-body'} fw-bold d-flex align-items-center gap-1 py-1 px-3 rounded-2" onclick="switchTabByName('${dol}{t.key}')" style="font-size: 13px;">
                    <i class="bi ${dol}{t.icon}"></i> <span>${dol}{t.label}</span>
                </button>`;
            });
            html += '</div>';
            return html;
        }

        function openKeuanganAccountPicker(targetInputId, fieldTitle) {
            document.getElementById('modalTitle').innerText = 'Pilih Akun - ' + (fieldTitle || 'Daftar Akun');
            let bodyHtml = `
                <div class="mb-3">
                    <input type="text" id="accPickerSearch" class="form-control form-control-dark" placeholder="Cari nama akun..." onkeyup="filterAccountPickerList()">
                </div>
                <div style="max-height: 380px; overflow-y: auto;" id="accPickerList">
            `;

            KEUANGAN_GROUPED_ACCOUNTS.forEach(grp => {
                bodyHtml += `
                    <div class="acc-picker-group mb-2">
                        <small class="fw-bold text-uppercase text-success d-block mb-1 border-bottom border-secondary border-opacity-25 pb-1">${dol}{grp.category}</small>
                        <div class="d-flex flex-wrap gap-1">
                `;
                grp.accounts.forEach(acc => {
                    const ref = getKeuanganAccountRef(acc);
                    bodyHtml += `
                        <button type="button" class="btn btn-sm btn-outline-secondary acc-picker-item text-start d-flex align-items-center justify-content-between py-1 px-2" style="font-size: 12px; border-radius: 8px;" onclick="selectKeuanganAccount('${dol}{targetInputId}', '${dol}{escapeJs(acc)}')">
                            <span>${dol}{escapeHtml(acc)}</span>
                            <span class="badge bg-secondary bg-opacity-25 text-secondary ms-2">${dol}{ref}</span>
                        </button>
                    `;
                });
                bodyHtml += `</div></div>`;
            });

            bodyHtml += `
                </div>
                <div class="mt-3 pt-2 border-top border-secondary border-opacity-25">
                    <label class="form-label text-secondary small">Atau Tulis Akun Kustom:</label>
                    <div class="input-group input-group-sm">
                        <input type="text" id="customAccountInput" class="form-control form-control-dark" placeholder="Nama Akun Baru...">
                        <button type="button" class="btn btn-success" onclick="selectCustomKeuanganAccount('${dol}{targetInputId}')">Gunakan</button>
                    </div>
                </div>
            `;

            document.getElementById('modalBody').innerHTML = bodyHtml;
            window.submitAction = null;
            modalInstance.show();
        }

        function filterAccountPickerList() {
            const q = (document.getElementById('accPickerSearch').value || '').toLowerCase();
            document.querySelectorAll('.acc-picker-item').forEach(btn => {
                const text = btn.innerText.toLowerCase();
                btn.style.display = text.includes(q) ? 'inline-flex' : 'none';
            });
        }

        function selectKeuanganAccount(targetId, accName) {
            const el = document.getElementById(targetId);
            if (el) el.value = accName;
            modalInstance.hide();
        }

        function selectCustomKeuanganAccount(targetId) {
            const val = document.getElementById('customAccountInput').value.trim();
            if (!val) return alert('Ketik nama akun terlebih dahulu');
            selectKeuanganAccount(targetId, val);
        }

        function printKeuanganReport(title, subtitle, tableHtml) {
            const printWindow = window.open('', '_blank', 'width=900,height=700');
            const dateStr = new Date().toLocaleDateString('id-ID', { day: 'numeric', month: 'long', year: 'numeric' });
            printWindow.document.write(`
                <!DOCTYPE html>
                <html>
                <head>
                    <title>${dol}{title} - My Singghara</title>
                    <link href="/assets/css/bootstrap.min.css" rel="stylesheet">
                    <style>
                        body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; padding: 25px; color: #222; background: #fff; }
                        .report-header { text-align: center; border-bottom: 2px solid #2C5E6A; padding-bottom: 12px; margin-bottom: 20px; }
                        .report-title { font-size: 20px; font-weight: bold; color: #2C5E6A; margin: 0; text-transform: uppercase; }
                        .report-sub { font-size: 13px; color: #666; margin: 4px 0 0 0; }
                        .table-report th { background-color: #2C5E6A !important; color: #fff !important; font-size: 12px; }
                        .table-report td { font-size: 12px; }
                        .fw-bold { font-weight: bold; }
                        @media print {
                            .no-print { display: none !important; }
                        }
                    </style>
                </head>
                <body>
                    <div class="d-flex justify-content-end mb-3 no-print">
                        <button class="btn btn-primary btn-sm" onclick="window.print()"><i class="bi bi-printer"></i> Cetak / Simpan PDF</button>
                    </div>
                    <div class="report-header">
                        <h4 class="report-title">${dol}{title}</h4>
                        <p class="report-sub">${dol}{subtitle || 'Aplikasi Pembukuan Usaha - My Singghara'} | Periode: ${dol}{dateStr}</p>
                    </div>
                    ${dol}{tableHtml}
                    <div class="mt-4 text-end" style="font-size: 11px; color: #777;">
                        Dicetak secara otomatis dari My Singghara pada ${dol}{new Date().toLocaleString('id-ID')}
                    </div>
                </body>
                </html>
            `);
            printWindow.document.close();
            setTimeout(() => { printWindow.focus(); }, 250);
        }

        // ----------------------------------------------------
        // --- 1. JURNAL UMUM ---
        // ----------------------------------------------------
        async function loadJurnalUmum() {
            await fetchKeuanganData();
            const entries = window.keuanganJournalEntries || [];

            const totalDebet = entries.reduce((s, e) => s + (Number(e.nominal) || 0), 0);
            const totalKredit = entries.reduce((s, e) => s + (Number(e.nominal) || 0), 0);
            const isBalanced = (totalDebet === totalKredit) && entries.length > 0;

            let html = renderKeuanganSubNav('jurnal_umum');

            html += `
                <div class="d-flex flex-wrap justify-content-between align-items-center mb-4 gap-2">
                    <div>
                        <h4 class="fw-bold mb-0 text-success d-flex align-items-center gap-2">
                            <i class="bi bi-journal-text"></i> Jurnal Umum
                        </h4>
                        <small class="text-secondary">Pencatatan kronologis seluruh transaksi debit & kredit usaha</small>
                    </div>
                    <div class="d-flex gap-2">
                        <button class="btn btn-sm btn-outline-secondary" onclick="printJurnalUmum()"><i class="bi bi-printer me-1"></i> Cetak Jurnal</button>
                        <button class="btn btn-sm btn-brand" onclick="openAddJurnalUmumModal()"><i class="bi bi-plus-lg me-1"></i> Transaksi Baru</button>
                    </div>
                </div>

                <!-- 3 RINGKASAN STATUS KESEIMBANGAN -->
                <div class="row g-3 mb-4">
                    <div class="col-12 col-sm-4">
                        <div class="card card-custom p-3 h-100">
                            <small class="text-secondary d-block mb-1">Total Transaksi</small>
                            <h4 class="fw-bold text-primary mb-0">${dol}{entries.length}</h4>
                            <small class="text-secondary mt-1 d-block">Entri Terverifikasi</small>
                        </div>
                    </div>
                    <div class="col-12 col-sm-4">
                        <div class="card card-custom p-3 h-100">
                            <small class="text-secondary d-block mb-1">Total Debet & Kredit</small>
                            <h4 class="fw-bold text-info mb-0">${dol}{formatRupiah(totalDebet)}</h4>
                            <small class="text-secondary mt-1 d-block">Debet = Kredit</small>
                        </div>
                    </div>
                    <div class="col-12 col-sm-4">
                        <div class="card card-custom p-3 h-100 border-${dol}{isBalanced ? 'success' : (entries.length === 0 ? 'secondary' : 'danger')}">
                            <small class="text-secondary d-block mb-1">Status Keseimbangan</small>
                            <h5 class="fw-bold text-${dol}{isBalanced ? 'success' : (entries.length === 0 ? 'secondary' : 'danger')} mb-0">
                                <i class="bi bi-${dol}{isBalanced ? 'check-circle-fill' : 'exclamation-circle-fill'} me-1"></i>
                                ${dol}{isBalanced ? 'SEIMBANG (BALANCE)' : (entries.length === 0 ? 'KOSONG' : 'TIDAK SEIMBANG')}
                            </h5>
                            <small class="text-secondary mt-1 d-block">Selisih: Rp 0</small>
                        </div>
                    </div>
                </div>

                <!-- DAFTAR TABEL JURNAL UMUM -->
                <div class="card card-custom p-3 p-md-4 mb-4" style="border-radius: 20px;">
                    <div class="d-flex flex-wrap justify-content-between align-items-center mb-3 gap-2">
                        <h6 class="fw-bold mb-0 text-success"><i class="bi bi-table me-2"></i>Buku Transaksi Jurnal Umum</h6>
                        <input type="text" id="jurnalSearchInput" class="form-control form-control-sm form-control-dark" style="max-width: 250px;" placeholder="Cari akun / keterangan..." onkeyup="filterJurnalTable()">
                    </div>

                    <div class="table-responsive">
                        <table class="table table-dark-custom align-middle mb-0" id="jurnalTable">
                            <thead>
                                <tr>
                                    <th style="width: 130px;">Tanggal</th>
                                    <th>Keterangan & Posisi Akun</th>
                                    <th class="text-center" style="width: 90px;">Ref</th>
                                    <th class="text-end" style="width: 150px;">Debet (Rp)</th>
                                    <th class="text-end" style="width: 150px;">Kredit (Rp)</th>
                                    <th class="text-center" style="width: 70px;">Aksi</th>
                                </tr>
                            </thead>
                            <tbody>
            `;

            if (entries.length === 0) {
                html += `
                    <tr>
                        <td colspan="6" class="text-center py-5 text-secondary">
                            <i class="bi bi-journal-x fs-1 d-block mb-2 text-secondary opacity-50"></i>
                            <span class="fw-bold d-block">Belum ada transaksi di Jurnal Umum</span>
                            <small>Klik tombol '+ Transaksi Baru' untuk menambahkan entri jurnal pertama Anda.</small>
                        </td>
                    </tr>
                `;
            } else {
                entries.forEach((e, idx) => {
                    const debetRef = getKeuanganAccountRef(e.debetAkun);
                    const kreditRef = getKeuanganAccountRef(e.kreditAkun);
                    const nominalVal = Number(e.nominal) || 0;

                    html += `
                        <tr class="jurnal-row border-bottom border-secondary border-opacity-25">
                            <td class="align-top fw-semibold text-secondary small">${dol}{escapeHtml(e.tanggal || '-')}</td>
                            <td class="py-2">
                                <div class="fw-bold text-body">${dol}{escapeHtml(e.debetAkun)}</div>
                                <div class="text-info ps-3 mt-1" style="font-size: 13px;">${dol}{escapeHtml(e.kreditAkun)}</div>
                                ${dol}{e.keterangan ? `<div class="text-secondary small fst-italic mt-1 ps-1" style="font-size: 11px;">(${dol}{escapeHtml(e.keterangan)})</div>` : ''}
                            </td>
                            <td class="text-center align-top text-secondary small">
                                <div>${dol}{debetRef}</div>
                                <div class="mt-1">${dol}{kreditRef}</div>
                            </td>
                            <td class="text-end align-top fw-bold text-success">
                                <div>${dol}{formatRupiah(nominalVal)}</div>
                                <div class="text-secondary small mt-1">-</div>
                            </td>
                            <td class="text-end align-top fw-bold text-info">
                                <div class="text-secondary small">-</div>
                                <div class="mt-1">${dol}{formatRupiah(nominalVal)}</div>
                            </td>
                            <td class="text-center align-top">
                                <button class="btn btn-sm text-danger p-1" onclick="deleteJurnalEntry(${dol}{idx})" title="Hapus Transaksi">
                                    <i class="bi bi-trash"></i>
                                </button>
                            </td>
                        </tr>
                    `;
                });
            }

            html += `
                            </tbody>
                            <tfoot class="border-top border-secondary fw-bold">
                                <tr>
                                    <td colspan="3" class="text-end text-uppercase">Total Saldo:</td>
                                    <td class="text-end text-success">${dol}{formatRupiah(totalDebet)}</td>
                                    <td class="text-end text-info">${dol}{formatRupiah(totalKredit)}</td>
                                    <td></td>
                                </tr>
                            </tfoot>
                        </table>
                    </div>
                </div>
            `;

            document.getElementById('tab-content').innerHTML = html;
        }

        function filterJurnalTable() {
            const q = (document.getElementById('jurnalSearchInput').value || '').toLowerCase();
            document.querySelectorAll('#jurnalTable tbody tr.jurnal-row').forEach(row => {
                const text = row.innerText.toLowerCase();
                row.style.display = text.includes(q) ? '' : 'none';
            });
        }

        function openAddJurnalUmumModal() {
            document.getElementById('modalTitle').innerText = 'Tambah Transaksi Jurnal Umum';
            const todayStr = new Date().toISOString().split('T')[0];

            document.getElementById('modalBody').innerHTML = `
                <div class="row g-3">
                    <div class="col-12 col-md-6">
                        <label class="form-label text-secondary small fw-bold">Tanggal Transaksi</label>
                        <input type="date" id="juTanggal" class="form-control form-control-dark" value="${dol}{todayStr}">
                    </div>
                    <div class="col-12 col-md-6">
                        <label class="form-label text-secondary small fw-bold">Nominal Transaksi (Rp)</label>
                        <input type="number" id="juNominal" class="form-control form-control-dark" placeholder="Contoh: 1500000" min="1">
                    </div>
                    <div class="col-12 col-md-6">
                        <label class="form-label text-secondary small fw-bold">Akun Debet</label>
                        <div class="input-group">
                            <input type="text" id="juDebetAkun" class="form-control form-control-dark" value="Kas" readonly>
                            <button type="button" class="btn btn-outline-secondary" onclick="openKeuanganAccountPicker('juDebetAkun', 'Pilih Akun Debet')">
                                <i class="bi bi-list-check me-1"></i> Pilih
                            </button>
                        </div>
                    </div>
                    <div class="col-12 col-md-6">
                        <label class="form-label text-secondary small fw-bold">Akun Kredit</label>
                        <div class="input-group">
                            <input type="text" id="juKreditAkun" class="form-control form-control-dark" value="Pendapatan jasa" readonly>
                            <button type="button" class="btn btn-outline-secondary" onclick="openKeuanganAccountPicker('juKreditAkun', 'Pilih Akun Kredit')">
                                <i class="bi bi-list-check me-1"></i> Pilih
                            </button>
                        </div>
                    </div>
                    <div class="col-12">
                        <label class="form-label text-secondary small fw-bold">Keterangan Transaksi</label>
                        <textarea id="juKeterangan" class="form-control form-control-dark" rows="2" placeholder="Contoh: Penerimaan pendapatan jasa servis pelanggan..."></textarea>
                    </div>
                </div>
            `;

            window.submitAction = async () => {
                const tanggal = document.getElementById('juTanggal').value || todayStr;
                const nominal = parseFloat(document.getElementById('juNominal').value) || 0;
                const debetAkun = document.getElementById('juDebetAkun').value.trim();
                const kreditAkun = document.getElementById('juKreditAkun').value.trim();
                const keterangan = document.getElementById('juKeterangan').value.trim();

                if (nominal <= 0) return alert('Nominal harus lebih besar dari 0');
                if (!debetAkun || !kreditAkun) return alert('Akun debet dan kredit harus dipilih');
                if (debetAkun === kreditAkun) return alert('Akun debet dan akun kredit tidak boleh sama');

                try {
                    await fetch('/api/keuangan/jurnal', {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({ tanggal, debetAkun, kreditAkun, nominal, keterangan })
                    });
                    modalInstance.hide();
                    loadJurnalUmum();
                } catch (e) {
                    alert('Gagal menyimpan transaksi: ' + e.message);
                }
            };

            modalInstance.show();
        }

        async function deleteJurnalEntry(idx) {
            showCustomConfirm({
                title: 'Hapus Jurnal Umum',
                message: 'Apakah Anda yakin ingin menghapus transaksi jurnal umum ini?',
                confirmText: 'Hapus Jurnal',
                onConfirm: async () => {
                    try {
                        await fetch('/api/keuangan/jurnal?index=' + idx, { method: 'DELETE' });
                        loadJurnalUmum();
                    } catch (e) {
                        alert('Gagal menghapus: ' + e.message);
                    }
                }
            });
        }

        function printJurnalUmum() {
            const table = document.getElementById('jurnalTable');
            if (!table) return;
            printKeuanganReport('Buku Jurnal Umum', 'Rekapitulasi Jurnal Umum Transaksi', table.outerHTML);
        }

        // ----------------------------------------------------
        // --- 2. BUKU BESAR (GENERAL LEDGER) ---
        // ----------------------------------------------------
        async function loadBukuBesar() {
            await fetchKeuanganData();
            const entries = window.keuanganJournalEntries || [];

            // Kumpulkan semua akun unik
            const accSet = new Set();
            entries.forEach(e => {
                if (e.debetAkun) accSet.add(e.debetAkun);
                if (e.kreditAkun) accSet.add(e.kreditAkun);
            });
            const accounts = Array.from(accSet).sort();

            if (!window.selectedLedgerAccount || !accounts.includes(window.selectedLedgerAccount)) {
                window.selectedLedgerAccount = accounts[0] || "";
            }

            let html = renderKeuanganSubNav('buku_besar');

            html += `
                <div class="d-flex flex-wrap justify-content-between align-items-center mb-4 gap-2">
                    <div>
                        <h4 class="fw-bold mb-0 text-success d-flex align-items-center gap-2">
                            <i class="bi bi-book"></i> Buku Besar (General Ledger)
                        </h4>
                        <small class="text-secondary">Mutasi historis dan saldo berjalan per masing-masing akun</small>
                    </div>
                    <button class="btn btn-sm btn-outline-secondary" onclick="printBukuBesar()"><i class="bi bi-printer me-1"></i> Cetak Buku Besar</button>
                </div>
            `;

            if (accounts.length === 0) {
                html += `
                    <div class="card card-custom p-5 text-center my-4">
                        <i class="bi bi-journal-x fs-1 text-secondary opacity-50 mb-2"></i>
                        <h5 class="fw-bold text-secondary">Buku Besar Kosong</h5>
                        <p class="text-secondary small mb-3">Silakan input transaksi terlebih dahulu di menu Jurnal Umum agar data buku besar terisi secara otomatis.</p>
                        <button class="btn btn-sm btn-brand mx-auto" onclick="switchTabByName('jurnal_umum')">Buka Jurnal Umum</button>
                    </div>
                `;
                document.getElementById('tab-content').innerHTML = html;
                return;
            }

            // Hitung ledger untuk akun terpilih
            const selAcc = window.selectedLedgerAccount;
            const selRef = getKeuanganAccountRef(selAcc);
            const normalType = getKeuanganNormalBalanceType(selAcc);

            const matchingJournals = entries.filter(e => e.debetAkun === selAcc || e.kreditAkun === selAcc);
            
            let runningBal = 0;
            let totalDebetMutasi = 0;
            let totalKreditMutasi = 0;

            const ledgerRows = matchingJournals.map(e => {
                const isDebet = (e.debetAkun === selAcc);
                const debetAmt = isDebet ? (Number(e.nominal) || 0) : 0;
                const kreditAmt = !isDebet ? (Number(e.nominal) || 0) : 0;

                totalDebetMutasi += debetAmt;
                totalKreditMutasi += kreditAmt;

                if (normalType === 'D') {
                    runningBal += (debetAmt - kreditAmt);
                } else {
                    runningBal += (kreditAmt - debetAmt);
                }

                return {
                    tanggal: e.tanggal,
                    keterangan: e.keterangan || (isDebet ? `Terima dari ${dol}{e.kreditAkun}` : `Transfer ke ${dol}{e.debetAkun}`),
                    ref: isDebet ? getKeuanganAccountRef(e.kreditAkun) : getKeuanganAccountRef(e.debetAkun),
                    debet: debetAmt,
                    kredit: kreditAmt,
                    saldo: runningBal
                };
            });

            // Account selection pills
            html += `
                <div class="card card-custom p-3 mb-4" style="border-radius: 16px;">
                    <label class="form-label text-secondary small fw-bold mb-2">Pilih Akun Buku Besar:</label>
                    <div class="d-flex flex-wrap gap-1">
            `;
            accounts.forEach(acc => {
                const isSel = (acc === selAcc);
                html += `
                    <button class="btn btn-sm ${dol}{isSel ? 'btn-success text-white shadow-sm' : 'btn-outline-secondary border-0 text-body'} py-1 px-3 rounded-pill" onclick="changeSelectedLedgerAccount('${dol}{escapeJs(acc)}')">
                        ${dol}{escapeHtml(acc)} <span class="badge bg-${dol}{isSel ? 'light text-dark' : 'secondary bg-opacity-25 text-secondary'} ms-1">${dol}{getKeuanganAccountRef(acc)}</span>
                    </button>
                `;
            });
            html += `
                    </div>
                </div>

                <!-- HERO INFO AKUN TERPILIH -->
                <div class="row g-3 mb-4">
                    <div class="col-12 col-md-4">
                        <div class="card card-custom p-3 h-100">
                            <small class="text-secondary d-block mb-1">Nama Akun & Kode Ref</small>
                            <h4 class="fw-bold text-success mb-0">${dol}{escapeHtml(selAcc)}</h4>
                            <small class="text-secondary mt-1 d-block">Kode Ref: ${dol}{selRef} | Saldo Normal: ${dol}{normalType === 'D' ? 'Debet' : 'Kredit'}</small>
                        </div>
                    </div>
                    <div class="col-12 col-md-4">
                        <div class="card card-custom p-3 h-100">
                            <small class="text-secondary d-block mb-1">Total Mutasi Debet & Kredit</small>
                            <div class="d-flex justify-content-between align-items-center mt-1">
                                <span class="small text-success fw-bold">D: ${dol}{formatRupiah(totalDebetMutasi)}</span>
                                <span class="small text-info fw-bold">K: ${dol}{formatRupiah(totalKreditMutasi)}</span>
                            </div>
                            <small class="text-secondary mt-1 d-block">${dol}{ledgerRows.length} Transaksi Tercatat</small>
                        </div>
                    </div>
                    <div class="col-12 col-md-4">
                        <div class="card card-custom p-3 h-100 border-success">
                            <small class="text-secondary d-block mb-1">Saldo Akhir Berjalan</small>
                            <h4 class="fw-bold text-success mb-0">${dol}{formatRupiah(runningBal)}</h4>
                            <small class="text-secondary mt-1 d-block">Posisi: ${dol}{runningBal >= 0 ? (normalType === 'D' ? 'Debet' : 'Kredit') : 'Defisit'}</small>
                        </div>
                    </div>
                </div>

                <!-- TABEL BUKU BESAR -->
                <div class="card card-custom p-3 p-md-4 mb-4" style="border-radius: 20px;">
                    <div class="table-responsive">
                        <table class="table table-dark-custom align-middle mb-0" id="ledgerTable">
                            <thead>
                                <tr>
                                    <th style="width: 120px;">Tanggal</th>
                                    <th>Keterangan Mutasi</th>
                                    <th class="text-center" style="width: 80px;">Ref</th>
                                    <th class="text-end" style="width: 140px;">Debet (Rp)</th>
                                    <th class="text-end" style="width: 140px;">Kredit (Rp)</th>
                                    <th class="text-end" style="width: 160px;">Saldo Akhir (Rp)</th>
                                </tr>
                            </thead>
                            <tbody>
            `;

            ledgerRows.forEach(r => {
                html += `
                    <tr>
                        <td class="text-secondary small">${dol}{escapeHtml(r.tanggal || '-')}</td>
                        <td class="fw-semibold">${dol}{escapeHtml(r.keterangan || '-')}</td>
                        <td class="text-center text-secondary small">${dol}{r.ref || '-'}</td>
                        <td class="text-end fw-bold ${dol}{r.debet > 0 ? 'text-success' : 'text-secondary opacity-50'}">${dol}{r.debet > 0 ? formatRupiah(r.debet) : '-'}</td>
                        <td class="text-end fw-bold ${dol}{r.kredit > 0 ? 'text-info' : 'text-secondary opacity-50'}">${dol}{r.kredit > 0 ? formatRupiah(r.kredit) : '-'}</td>
                        <td class="text-end fw-bold text-success">${dol}{formatRupiah(r.saldo)}</td>
                    </tr>
                `;
            });

            html += `
                            </tbody>
                            <tfoot class="border-top border-secondary fw-bold">
                                <tr>
                                    <td colspan="3" class="text-end text-uppercase">Total Mutasi & Saldo:</td>
                                    <td class="text-end text-success">${dol}{formatRupiah(totalDebetMutasi)}</td>
                                    <td class="text-end text-info">${dol}{formatRupiah(totalKreditMutasi)}</td>
                                    <td class="text-end text-success">${dol}{formatRupiah(runningBal)}</td>
                                </tr>
                            </tfoot>
                        </table>
                    </div>
                </div>
            `;

            document.getElementById('tab-content').innerHTML = html;
        }

        function changeSelectedLedgerAccount(acc) {
            window.selectedLedgerAccount = acc;
            loadBukuBesar();
        }

        function printBukuBesar() {
            const table = document.getElementById('ledgerTable');
            if (!table) return;
            printKeuanganReport('Buku Besar: ' + window.selectedLedgerAccount, 'Buku Besar Akun Ref: ' + getKeuanganAccountRef(window.selectedLedgerAccount), table.outerHTML);
        }

        // ----------------------------------------------------
        // --- 3. NERACA SALDO (TRIAL BALANCE) ---
        // ----------------------------------------------------
        async function loadNeracaSaldo() {
            await fetchKeuanganData();
            const entries = window.keuanganJournalEntries || [];

            // Semua akun unik
            const set = new Set();
            entries.forEach(e => {
                if (e.debetAkun) set.add(e.debetAkun);
                if (e.kreditAkun) set.add(e.kreditAkun);
            });
            const customAccs = Array.from(set).filter(a => !KEUANGAN_ACCOUNTS_ORDERED.includes(a)).sort();
            const orderedAccounts = KEUANGAN_ACCOUNTS_ORDERED.filter(a => set.has(a)).concat(customAccs);

            const rows = [];
            orderedAccounts.forEach(acc => {
                const debits = entries.filter(e => e.debetAkun === acc).reduce((s, e) => s + (Number(e.nominal) || 0), 0);
                const credits = entries.filter(e => e.kreditAkun === acc).reduce((s, e) => s + (Number(e.nominal) || 0), 0);
                const normalType = getKeuanganNormalBalanceType(acc);

                let rDeb = 0;
                let rKred = 0;
                if (normalType === 'D') {
                    const net = debits - credits;
                    if (net >= 0) rDeb = net; else rKred = Math.abs(net);
                } else {
                    const net = credits - debits;
                    if (net >= 0) rKred = net; else rDeb = Math.abs(net);
                }

                if (rDeb > 0 || rKred > 0) {
                    rows.push({
                        account: acc,
                        ref: getKeuanganAccountRef(acc),
                        debit: rDeb,
                        credit: rKred
                    });
                }
            });

            const grandDeb = rows.reduce((s, r) => s + r.debit, 0);
            const grandKred = rows.reduce((s, r) => s + r.credit, 0);
            const isBalanced = (grandDeb === grandKred) && rows.length > 0;

            let html = renderKeuanganSubNav('neraca_saldo');

            html += `
                <div class="d-flex flex-wrap justify-content-between align-items-center mb-4 gap-2">
                    <div>
                        <h4 class="fw-bold mb-0 text-success d-flex align-items-center gap-2">
                            <i class="bi bi-calculator"></i> Neraca Saldo (Trial Balance)
                        </h4>
                        <small class="text-secondary">Daftar saldo debit dan kredit dari seluruh akun sebelum penyesuaian</small>
                    </div>
                    <button class="btn btn-sm btn-outline-secondary" onclick="printNeracaSaldo()"><i class="bi bi-printer me-1"></i> Cetak Neraca Saldo</button>
                </div>

                <!-- 3 RINGKASAN STATUS KESEIMBANGAN -->
                <div class="row g-3 mb-4">
                    <div class="col-12 col-sm-4">
                        <div class="card card-custom p-3 h-100">
                            <small class="text-secondary d-block mb-1">Total Saldo Debet</small>
                            <h4 class="fw-bold text-success mb-0">${dol}{formatRupiah(grandDeb)}</h4>
                            <small class="text-secondary mt-1 d-block">${dol}{rows.filter(r => r.debit > 0).length} Akun Posisi Debet</small>
                        </div>
                    </div>
                    <div class="col-12 col-sm-4">
                        <div class="card card-custom p-3 h-100">
                            <small class="text-secondary d-block mb-1">Total Saldo Kredit</small>
                            <h4 class="fw-bold text-info mb-0">${dol}{formatRupiah(grandKred)}</h4>
                            <small class="text-secondary mt-1 d-block">${dol}{rows.filter(r => r.credit > 0).length} Akun Posisi Kredit</small>
                        </div>
                    </div>
                    <div class="col-12 col-sm-4">
                        <div class="card card-custom p-3 h-100 border-${dol}{isBalanced ? 'success' : (rows.length === 0 ? 'secondary' : 'danger')}">
                            <small class="text-secondary d-block mb-1">Keseimbangan Neraca</small>
                            <h5 class="fw-bold text-${dol}{isBalanced ? 'success' : (rows.length === 0 ? 'secondary' : 'danger')} mb-0">
                                <i class="bi bi-${dol}{isBalanced ? 'check-circle-fill' : 'exclamation-circle-fill'} me-1"></i>
                                ${dol}{isBalanced ? 'SEIMBANG (BALANCE)' : (rows.length === 0 ? 'BELUM ADA DATA' : 'TIDAK SEIMBANG')}
                            </h5>
                            <small class="text-secondary mt-1 d-block">Selisih: ${dol}{formatRupiah(Math.abs(grandDeb - grandKred))}</small>
                        </div>
                    </div>
                </div>

                <!-- TABEL NERACA SALDO -->
                <div class="card card-custom p-3 p-md-4 mb-4" style="border-radius: 20px;">
                    <div class="d-flex flex-wrap justify-content-between align-items-center mb-3 gap-2">
                        <h6 class="fw-bold mb-0 text-success"><i class="bi bi-table me-2"></i>Tabel Neraca Saldo</h6>
                        <input type="text" id="neracaSearchInput" class="form-control form-control-sm form-control-dark" style="max-width: 250px;" placeholder="Cari nama akun..." onkeyup="filterNeracaTable()">
                    </div>

                    <div class="table-responsive">
                        <table class="table table-dark-custom align-middle mb-0" id="neracaTable">
                            <thead>
                                <tr>
                                    <th style="width: 60px;">No</th>
                                    <th class="text-center" style="width: 90px;">Kode Ref</th>
                                    <th>Nama Akun</th>
                                    <th class="text-end" style="width: 180px;">Debet (Rp)</th>
                                    <th class="text-end" style="width: 180px;">Kredit (Rp)</th>
                                </tr>
                            </thead>
                            <tbody>
            `;

            if (rows.length === 0) {
                html += `
                    <tr>
                        <td colspan="5" class="text-center py-5 text-secondary">
                            <i class="bi bi-calculator fs-1 d-block mb-2 text-secondary opacity-50"></i>
                            <span class="fw-bold d-block">Neraca Saldo Masih Kosong</span>
                            <small>Data otomatis terhitung saat Anda memasukkan transaksi di Jurnal Umum.</small>
                        </td>
                    </tr>
                `;
            } else {
                rows.forEach((r, idx) => {
                    html += `
                        <tr class="neraca-row">
                            <td class="text-secondary small">${dol}{idx + 1}</td>
                            <td class="text-center text-secondary small">${dol}{r.ref}</td>
                            <td class="fw-bold">${dol}{escapeHtml(r.account)}</td>
                            <td class="text-end fw-bold ${dol}{r.debit > 0 ? 'text-success' : 'text-secondary opacity-50'}">${dol}{r.debit > 0 ? formatRupiah(r.debit) : '-'}</td>
                            <td class="text-end fw-bold ${dol}{r.credit > 0 ? 'text-info' : 'text-secondary opacity-50'}">${dol}{r.credit > 0 ? formatRupiah(r.credit) : '-'}</td>
                        </tr>
                    `;
                });
            }

            html += `
                            </tbody>
                            <tfoot class="border-top border-secondary fw-bold">
                                <tr>
                                    <td colspan="3" class="text-end text-uppercase">Total Saldo Neraca:</td>
                                    <td class="text-end text-success">${dol}{formatRupiah(grandDeb)}</td>
                                    <td class="text-end text-info">${dol}{formatRupiah(grandKred)}</td>
                                </tr>
                            </tfoot>
                        </table>
                    </div>
                </div>
            `;

            document.getElementById('tab-content').innerHTML = html;
        }

        function filterNeracaTable() {
            const q = (document.getElementById('neracaSearchInput').value || '').toLowerCase();
            document.querySelectorAll('#neracaTable tbody tr.neraca-row').forEach(row => {
                const text = row.innerText.toLowerCase();
                row.style.display = text.includes(q) ? '' : 'none';
            });
        }

        function printNeracaSaldo() {
            const table = document.getElementById('neracaTable');
            if (!table) return;
            printKeuanganReport('Neraca Saldo', 'Laporan Neraca Saldo Sebelum Penyesuaian', table.outerHTML);
        }

        // ----------------------------------------------------
        // --- 4. JURNAL PENYESUAIAN ---
        // ----------------------------------------------------
        async function loadJurnalPenyesuaian() {
            await fetchKeuanganData();
            const adjEntries = window.keuanganAdjustmentEntries || [];
            const journalEntries = window.keuanganJournalEntries || [];

            const totalDeb = adjEntries.reduce((s, e) => s + (Number(e.nominal) || 0), 0);
            const totalKred = totalDeb;

            let html = renderKeuanganSubNav('jurnal_penyesuaian');

            html += `
                <div class="d-flex flex-wrap justify-content-between align-items-center mb-4 gap-2">
                    <div>
                        <h4 class="fw-bold mb-0 text-success d-flex align-items-center gap-2">
                            <i class="bi bi-pencil-square"></i> Jurnal Penyesuaian (Adjusting Entries)
                        </h4>
                        <small class="text-secondary">Penyesuaian akun nominal & riil pada akhir periode akuntansi</small>
                    </div>
                    <div class="d-flex gap-2">
                        <button class="btn btn-sm btn-outline-secondary" onclick="printJurnalPenyesuaian()"><i class="bi bi-printer me-1"></i> Cetak Penyesuaian</button>
                        <button class="btn btn-sm btn-brand" onclick="openAddPenyesuaianModal()"><i class="bi bi-plus-lg me-1"></i> Tambah Penyesuaian</button>
                    </div>
                </div>

                <!-- SUB TABS -->
                <ul class="nav nav-pills mb-4 gap-2">
                    <li class="nav-item">
                        <button class="nav-link ${dol}{window.adjActiveSubTab === 0 ? 'active btn-success' : 'text-body bg-secondary bg-opacity-10'} fw-bold" onclick="switchAdjSubTab(0)">
                            <i class="bi bi-list-check me-1"></i> Entri Jurnal Penyesuaian (${dol}{adjEntries.length})
                        </button>
                    </li>
                    <li class="nav-item">
                        <button class="nav-link ${dol}{window.adjActiveSubTab === 1 ? 'active btn-success' : 'text-body bg-secondary bg-opacity-10'} fw-bold" onclick="switchAdjSubTab(1)">
                            <i class="bi bi-table me-1"></i> Kertas Kerja / Neraca Setelah Penyesuaian
                        </button>
                    </li>
                </ul>
            `;

            if (window.adjActiveSubTab === 0) {
                // TAB 0: DAFTAR ENTRI PENYESUAIAN
                html += `
                    <!-- 3 HERO CARD -->
                    <div class="row g-3 mb-4">
                        <div class="col-12 col-sm-4">
                            <div class="card card-custom p-3 h-100">
                                <small class="text-secondary d-block mb-1">Total Entri Penyesuaian</small>
                                <h4 class="fw-bold text-primary mb-0">${dol}{adjEntries.length}</h4>
                                <small class="text-secondary mt-1 d-block">Transaksi Penyesuaian</small>
                            </div>
                        </div>
                        <div class="col-12 col-sm-4">
                            <div class="card card-custom p-3 h-100">
                                <small class="text-secondary d-block mb-1">Total Nominal Penyesuaian</small>
                                <h4 class="fw-bold text-info mb-0">${dol}{formatRupiah(totalDeb)}</h4>
                                <small class="text-secondary mt-1 d-block">Debet = Kredit</small>
                            </div>
                        </div>
                        <div class="col-12 col-sm-4">
                            <div class="card card-custom p-3 h-100 border-${dol}{adjEntries.length > 0 ? 'success' : 'secondary'}">
                                <small class="text-secondary d-block mb-1">Status Penyesuaian</small>
                                <h5 class="fw-bold text-${dol}{adjEntries.length > 0 ? 'success' : 'secondary'} mb-0">
                                    <i class="bi bi-${dol}{adjEntries.length > 0 ? 'check-circle-fill' : 'info-circle-fill'} me-1"></i>
                                    ${dol}{adjEntries.length > 0 ? 'PENYESUAIAN AKTIF' : 'BELUM ADA ENTRI'}
                                </h5>
                                <small class="text-secondary mt-1 d-block">Tersinkronisasi otomatis</small>
                            </div>
                        </div>
                    </div>

                    <!-- PRESET TEMPLATE BUTTONS -->
                    <div class="card card-custom p-3 mb-4" style="border-radius: 16px;">
                        <label class="form-label text-secondary small fw-bold mb-2"><i class="bi bi-lightning-charge me-1 text-warning"></i>Preset Cepat Penyesuaian:</label>
                        <div class="d-flex flex-wrap gap-1">
                            <button class="btn btn-sm btn-outline-secondary py-1 px-2 rounded-2" onclick="openAddPenyesuaianModal('Beban dibayar di muka')">Beban Sewa/Asuransi di Muka</button>
                            <button class="btn btn-sm btn-outline-secondary py-1 px-2 rounded-2" onclick="openAddPenyesuaianModal('Pendapatan diterima di muka')">Pendapatan Diterima di Muka</button>
                            <button class="btn btn-sm btn-outline-secondary py-1 px-2 rounded-2" onclick="openAddPenyesuaianModal('Penyusutan aset tetap')">Penyusutan Peralatan/Kendaraan</button>
                            <button class="btn btn-sm btn-outline-secondary py-1 px-2 rounded-2" onclick="openAddPenyesuaianModal('Beban masih harus dibayar')">Utang Beban Gaji / Akrual</button>
                            <button class="btn btn-sm btn-outline-secondary py-1 px-2 rounded-2" onclick="openAddPenyesuaianModal('Perlengkapan terpakai')">Perlengkapan Terpakai</button>
                        </div>
                    </div>

                    <!-- TABEL PENYESUAIAN -->
                    <div class="card card-custom p-3 p-md-4 mb-4" style="border-radius: 20px;">
                        <div class="table-responsive">
                            <table class="table table-dark-custom align-middle mb-0" id="adjTable">
                                <thead>
                                    <tr>
                                        <th style="width: 120px;">Tanggal</th>
                                        <th>Jenis & Keterangan</th>
                                        <th>Akun Debet</th>
                                        <th>Akun Kredit</th>
                                        <th class="text-end" style="width: 150px;">Nominal (Rp)</th>
                                        <th class="text-center" style="width: 70px;">Aksi</th>
                                    </tr>
                                </thead>
                                <tbody>
                `;

                if (adjEntries.length === 0) {
                    html += `
                        <tr>
                            <td colspan="6" class="text-center py-5 text-secondary">
                                <i class="bi bi-pencil-square fs-1 d-block mb-2 text-secondary opacity-50"></i>
                                <span class="fw-bold d-block">Belum ada jurnal penyesuaian</span>
                                <small>Klik tombol '+ Tambah Penyesuaian' atau pilih salah satu preset di atas.</small>
                            </td>
                        </tr>
                    `;
                } else {
                    adjEntries.forEach((e, idx) => {
                        html += `
                            <tr>
                                <td class="text-secondary small">${dol}{escapeHtml(e.tanggal || '-')}</td>
                                <td>
                                    <span class="badge bg-secondary bg-opacity-25 text-secondary mb-1">${dol}{escapeHtml(e.jenisPenyesuaian || 'Umum')}</span>
                                    <div class="small text-secondary">${dol}{escapeHtml(e.keterangan || '-')}</div>
                                </td>
                                <td class="fw-bold text-success">${dol}{escapeHtml(e.debetAkun)} <span class="badge bg-secondary bg-opacity-25 text-secondary ms-1">${dol}{getKeuanganAccountRef(e.debetAkun)}</span></td>
                                <td class="fw-bold text-info">${dol}{escapeHtml(e.kreditAkun)} <span class="badge bg-secondary bg-opacity-25 text-secondary ms-1">${dol}{getKeuanganAccountRef(e.kreditAkun)}</span></td>
                                <td class="text-end fw-bold text-success">${dol}{formatRupiah(e.nominal)}</td>
                                <td class="text-center">
                                    <button class="btn btn-sm text-danger p-1" onclick="deleteAdjustmentEntry(${dol}{idx})" title="Hapus Penyesuaian">
                                        <i class="bi bi-trash"></i>
                                    </button>
                                </td>
                            </tr>
                        `;
                    });
                }

                html += `
                                </tbody>
                                <tfoot class="border-top border-secondary fw-bold">
                                    <tr>
                                        <td colspan="4" class="text-end text-uppercase">Total Penyesuaian:</td>
                                        <td class="text-end text-success">${dol}{formatRupiah(totalDeb)}</td>
                                        <td></td>
                                    </tr>
                                </tfoot>
                            </table>
                        </div>
                    </div>
                `;
            } else {
                // TAB 1: KERTAS KERJA / NERACA SETELAH PENYESUAIAN
                html += renderKertasKerjaView(journalEntries, adjEntries);
            }

            document.getElementById('tab-content').innerHTML = html;
        }

        function switchAdjSubTab(tabIdx) {
            window.adjActiveSubTab = tabIdx;
            loadJurnalPenyesuaian();
        }

        function renderKertasKerjaView(journalEntries, adjEntries) {
            const set = new Set();
            journalEntries.forEach(e => { set.add(e.debetAkun); set.add(e.kreditAkun); });
            adjEntries.forEach(e => { set.add(e.debetAkun); set.add(e.kreditAkun); });
            const customAccs = Array.from(set).filter(a => !KEUANGAN_ACCOUNTS_ORDERED.includes(a)).sort();
            const orderedAccounts = KEUANGAN_ACCOUNTS_ORDERED.filter(a => set.has(a)).concat(customAccs);

            const matrixRows = [];
            orderedAccounts.forEach(acc => {
                const jDeb = journalEntries.filter(e => e.debetAkun === acc).reduce((s, e) => s + (Number(e.nominal) || 0), 0);
                const jKred = journalEntries.filter(e => e.kreditAkun === acc).reduce((s, e) => s + (Number(e.nominal) || 0), 0);
                const normalType = getKeuanganNormalBalanceType(acc);

                let initDeb = 0, initKred = 0;
                if (normalType === 'D') {
                    const net = jDeb - jKred;
                    if (net >= 0) initDeb = net; else initKred = Math.abs(net);
                } else {
                    const net = jKred - jDeb;
                    if (net >= 0) initKred = net; else initDeb = Math.abs(net);
                }

                const adjDeb = adjEntries.filter(e => e.debetAkun === acc).reduce((s, e) => s + (Number(e.nominal) || 0), 0);
                const adjKred = adjEntries.filter(e => e.kreditAkun === acc).reduce((s, e) => s + (Number(e.nominal) || 0), 0);

                const totalD = jDeb + adjDeb;
                const totalK = jKred + adjKred;

                let adjBalDeb = 0, adjBalKred = 0;
                if (normalType === 'D') {
                    const net = totalD - totalK;
                    if (net >= 0) adjBalDeb = net; else adjBalKred = Math.abs(net);
                } else {
                    const net = totalK - totalD;
                    if (net >= 0) adjBalKred = net; else adjBalDeb = Math.abs(net);
                }

                if (initDeb > 0 || initKred > 0 || adjDeb > 0 || adjKred > 0 || adjBalDeb > 0 || adjBalKred > 0) {
                    matrixRows.push({
                        account: acc,
                        ref: getKeuanganAccountRef(acc),
                        initDeb, initKred,
                        adjDeb, adjKred,
                        adjBalDeb, adjBalKred
                    });
                }
            });

            let tInitD = matrixRows.reduce((s, r) => s + r.initDeb, 0);
            let tInitK = matrixRows.reduce((s, r) => s + r.initKred, 0);
            let tAdjD = matrixRows.reduce((s, r) => s + r.adjDeb, 0);
            let tAdjK = matrixRows.reduce((s, r) => s + r.adjKred, 0);
            let tFinD = matrixRows.reduce((s, r) => s + r.adjBalDeb, 0);
            let tFinK = matrixRows.reduce((s, r) => s + r.adjBalKred, 0);

            return `
                <div class="card card-custom p-3 p-md-4 mb-4" style="border-radius: 20px;">
                    <h6 class="fw-bold mb-3 text-success"><i class="bi bi-grid-3x3 me-2"></i>Kertas Kerja Akuntansi 6 Kolom</h6>
                    <div class="table-responsive">
                        <table class="table table-dark-custom align-middle mb-0 text-center" id="kertasKerjaTable">
                            <thead>
                                <tr>
                                    <th rowspan="2" class="text-start align-middle" style="min-width: 160px;">Nama Akun</th>
                                    <th rowspan="2" class="align-middle" style="width: 70px;">Ref</th>
                                    <th colspan="2" class="border-start border-secondary">Neraca Saldo Awal</th>
                                    <th colspan="2" class="border-start border-secondary">Penyesuaian</th>
                                    <th colspan="2" class="border-start border-secondary">Neraca Disesuaikan</th>
                                </tr>
                                <tr>
                                    <th class="border-start border-secondary" style="width: 110px;">Debet</th>
                                    <th style="width: 110px;">Kredit</th>
                                    <th class="border-start border-secondary" style="width: 110px;">Debet</th>
                                    <th style="width: 110px;">Kredit</th>
                                    <th class="border-start border-secondary" style="width: 110px;">Debet</th>
                                    <th style="width: 110px;">Kredit</th>
                                </tr>
                            </thead>
                            <tbody>
                                ${dol}{matrixRows.map(r => `
                                    <tr>
                                        <td class="text-start fw-bold">${dol}{escapeHtml(r.account)}</td>
                                        <td class="text-secondary small">${dol}{r.ref}</td>
                                        <td class="text-end border-start border-secondary ${dol}{r.initDeb > 0 ? 'text-success' : 'text-secondary opacity-50'}">${dol}{r.initDeb > 0 ? formatRupiah(r.initDeb) : '-'}</td>
                                        <td class="text-end ${dol}{r.initKred > 0 ? 'text-info' : 'text-secondary opacity-50'}">${dol}{r.initKred > 0 ? formatRupiah(r.initKred) : '-'}</td>
                                        <td class="text-end border-start border-secondary ${dol}{r.adjDeb > 0 ? 'text-success' : 'text-secondary opacity-50'}">${dol}{r.adjDeb > 0 ? formatRupiah(r.adjDeb) : '-'}</td>
                                        <td class="text-end ${dol}{r.adjKred > 0 ? 'text-info' : 'text-secondary opacity-50'}">${dol}{r.adjKred > 0 ? formatRupiah(r.adjKred) : '-'}</td>
                                        <td class="text-end border-start border-secondary fw-bold text-success">${dol}{r.adjBalDeb > 0 ? formatRupiah(r.adjBalDeb) : '-'}</td>
                                        <td class="text-end fw-bold text-info">${dol}{r.adjBalKred > 0 ? formatRupiah(r.adjBalKred) : '-'}</td>
                                    </tr>
                                `).join('')}
                            </tbody>
                            <tfoot class="border-top border-secondary fw-bold">
                                <tr>
                                    <td colspan="2" class="text-end text-uppercase">Total 6 Kolom:</td>
                                    <td class="text-end border-start border-secondary text-success">${dol}{formatRupiah(tInitD)}</td>
                                    <td class="text-end text-info">${dol}{formatRupiah(tInitK)}</td>
                                    <td class="text-end border-start border-secondary text-success">${dol}{formatRupiah(tAdjD)}</td>
                                    <td class="text-end text-info">${dol}{formatRupiah(tAdjK)}</td>
                                    <td class="text-end border-start border-secondary text-success">${dol}{formatRupiah(tFinD)}</td>
                                    <td class="text-end text-info">${dol}{formatRupiah(tFinK)}</td>
                                </tr>
                            </tfoot>
                        </table>
                    </div>
                </div>
            `;
        }

        function openAddPenyesuaianModal(defaultJenis) {
            document.getElementById('modalTitle').innerText = 'Tambah Jurnal Penyesuaian';
            const todayStr = new Date().toISOString().split('T')[0];

            let defDebet = "Beban perlengkapan";
            let defKredit = "Perlengkapan";
            let defKet = "Penyesuaian perlengkapan yang terpakai";
            let jenis = defaultJenis || "Perlengkapan terpakai";

            if (jenis === 'Beban dibayar di muka') {
                defDebet = "Beban asuransi"; defKredit = "Asuransi dibayar di muka"; defKet = "Pengakuan beban asuransi yang telah jatuh tempo";
            } else if (jenis === 'Pendapatan diterima di muka') {
                defDebet = "Pendapatan diterima di muka"; defKredit = "Pendapatan jasa"; defKet = "Pengakuan pendapatan atas jasa yang telah diselesaikan";
            } else if (jenis === 'Penyusutan aset tetap') {
                defDebet = "Beban penyusutan peralatan"; defKredit = "Akumulasi penyusutan peralatan"; defKet = "Penyusutan peralatan operasional periode ini";
            } else if (jenis === 'Beban masih harus dibayar') {
                defDebet = "Beban gaji"; defKredit = "Utang gaji"; defKet = "Gaji karyawan yang masih harus dibayar";
            }

            document.getElementById('modalBody').innerHTML = `
                <div class="row g-3">
                    <div class="col-12 col-md-6">
                        <label class="form-label text-secondary small fw-bold">Tanggal Transaksi</label>
                        <input type="date" id="adjTanggal" class="form-control form-control-dark" value="${dol}{todayStr}">
                    </div>
                    <div class="col-12 col-md-6">
                        <label class="form-label text-secondary small fw-bold">Jenis Penyesuaian</label>
                        <select id="adjJenis" class="form-select form-select-dark" onchange="onAdjJenisChange(this.value)">
                            <option value="Perlengkapan terpakai" ${dol}{jenis === 'Perlengkapan terpakai' ? 'selected' : ''}>Perlengkapan Terpakai</option>
                            <option value="Beban dibayar di muka" ${dol}{jenis === 'Beban dibayar di muka' ? 'selected' : ''}>Beban Dibayar di Muka (Sewa/Asuransi)</option>
                            <option value="Pendapatan diterima di muka" ${dol}{jenis === 'Pendapatan diterima di muka' ? 'selected' : ''}>Pendapatan Diterima di Muka</option>
                            <option value="Penyusutan aset tetap" ${dol}{jenis === 'Penyusutan aset tetap' ? 'selected' : ''}>Penyusutan Aset Tetap</option>
                            <option value="Beban masih harus dibayar" ${dol}{jenis === 'Beban masih harus dibayar' ? 'selected' : ''}>Beban Masih Harus Dibayar (Akrual)</option>
                            <option value="Kustom">Kustom / Lainnya</option>
                        </select>
                    </div>
                    <div class="col-12 col-md-6">
                        <label class="form-label text-secondary small fw-bold">Akun Debet</label>
                        <div class="input-group">
                            <input type="text" id="adjDebetAkun" class="form-control form-control-dark" value="${dol}{defDebet}" readonly>
                            <button type="button" class="btn btn-outline-secondary" onclick="openKeuanganAccountPicker('adjDebetAkun', 'Pilih Akun Debet Penyesuaian')">Pilih</button>
                        </div>
                    </div>
                    <div class="col-12 col-md-6">
                        <label class="form-label text-secondary small fw-bold">Akun Kredit</label>
                        <div class="input-group">
                            <input type="text" id="adjKreditAkun" class="form-control form-control-dark" value="${dol}{defKredit}" readonly>
                            <button type="button" class="btn btn-outline-secondary" onclick="openKeuanganAccountPicker('adjKreditAkun', 'Pilih Akun Kredit Penyesuaian')">Pilih</button>
                        </div>
                    </div>
                    <div class="col-12 col-md-6">
                        <label class="form-label text-secondary small fw-bold">Nominal Penyesuaian (Rp)</label>
                        <input type="number" id="adjNominal" class="form-control form-control-dark" placeholder="Contoh: 250000" min="1">
                    </div>
                    <div class="col-12 col-md-6">
                        <label class="form-label text-secondary small fw-bold">Keterangan Penyesuaian</label>
                        <input type="text" id="adjKeterangan" class="form-control form-control-dark" value="${dol}{defKet}">
                    </div>
                </div>
            `;

            window.submitAction = async () => {
                const tanggal = document.getElementById('adjTanggal').value || todayStr;
                const jenisPenyesuaian = document.getElementById('adjJenis').value;
                const debetAkun = document.getElementById('adjDebetAkun').value.trim();
                const kreditAkun = document.getElementById('adjKreditAkun').value.trim();
                const nominal = parseFloat(document.getElementById('adjNominal').value) || 0;
                const keterangan = document.getElementById('adjKeterangan').value.trim();

                if (nominal <= 0) return alert('Nominal harus lebih besar dari 0');
                if (!debetAkun || !kreditAkun) return alert('Akun debet dan kredit harus dipilih');
                if (debetAkun === kreditAkun) return alert('Akun debet dan kredit tidak boleh sama');

                try {
                    await fetch('/api/keuangan/penyesuaian', {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({ tanggal, jenisPenyesuaian, debetAkun, kreditAkun, nominal, keterangan })
                    });
                    modalInstance.hide();
                    loadJurnalPenyesuaian();
                } catch (e) {
                    alert('Gagal menyimpan penyesuaian: ' + e.message);
                }
            };

            modalInstance.show();
        }

        function onAdjJenisChange(jenis) {
            if (jenis === 'Beban dibayar di muka') {
                document.getElementById('adjDebetAkun').value = "Beban asuransi";
                document.getElementById('adjKreditAkun').value = "Asuransi dibayar di muka";
                document.getElementById('adjKeterangan').value = "Pengakuan beban asuransi yang telah jatuh tempo";
            } else if (jenis === 'Pendapatan diterima di muka') {
                document.getElementById('adjDebetAkun').value = "Pendapatan diterima di muka";
                document.getElementById('adjKreditAkun').value = "Pendapatan jasa";
                document.getElementById('adjKeterangan').value = "Pengakuan pendapatan atas jasa yang telah diselesaikan";
            } else if (jenis === 'Penyusutan aset tetap') {
                document.getElementById('adjDebetAkun').value = "Beban penyusutan peralatan";
                document.getElementById('adjKreditAkun').value = "Akumulasi penyusutan peralatan";
                document.getElementById('adjKeterangan').value = "Penyusutan peralatan operasional periode ini";
            } else if (jenis === 'Beban masih harus dibayar') {
                document.getElementById('adjDebetAkun').value = "Beban gaji";
                document.getElementById('adjKreditAkun').value = "Utang gaji";
                document.getElementById('adjKeterangan').value = "Gaji karyawan yang masih harus dibayar";
            } else if (jenis === 'Perlengkapan terpakai') {
                document.getElementById('adjDebetAkun').value = "Beban perlengkapan";
                document.getElementById('adjKreditAkun').value = "Perlengkapan";
                document.getElementById('adjKeterangan').value = "Penyesuaian perlengkapan yang terpakai";
            }
        }

        async function deleteAdjustmentEntry(idx) {
            showCustomConfirm({
                title: 'Hapus Jurnal Penyesuaian',
                message: 'Apakah Anda yakin ingin menghapus entri jurnal penyesuaian ini?',
                confirmText: 'Hapus Penyesuaian',
                onConfirm: async () => {
                    try {
                        await fetch('/api/keuangan/penyesuaian?index=' + idx, { method: 'DELETE' });
                        loadJurnalPenyesuaian();
                    } catch (e) {
                        alert('Gagal menghapus: ' + e.message);
                    }
                }
            });
        }

        function printJurnalPenyesuaian() {
            const table = window.adjActiveSubTab === 0 ? document.getElementById('adjTable') : document.getElementById('kertasKerjaTable');
            if (!table) return;
            printKeuanganReport('Jurnal Penyesuaian', 'Laporan Jurnal Penyesuaian Akhir Periode', table.outerHTML);
        }

        // ----------------------------------------------------
        // --- 5. NERACA SALDO DISESUAIKAN (6 KOLOM) ---
        // ----------------------------------------------------
        async function loadNeracaSaldoDisesuaikan() {
            await fetchKeuanganData();
            const journalEntries = window.keuanganJournalEntries || [];
            const adjEntries = window.keuanganAdjustmentEntries || [];

            let html = renderKeuanganSubNav('neraca_saldo_disesuaikan');

            html += `
                <div class="d-flex flex-wrap justify-content-between align-items-center mb-4 gap-2">
                    <div>
                        <h4 class="fw-bold mb-0 text-success d-flex align-items-center gap-2">
                            <i class="bi bi-table"></i> Neraca Saldo Disesuaikan (Adjusted Trial Balance)
                        </h4>
                        <small class="text-secondary">Neraca saldo gabungan setelah seluruh entri jurnal penyesuaian diperhitungkan</small>
                    </div>
                    <button class="btn btn-sm btn-outline-secondary" onclick="printNeracaDisesuaikan()"><i class="bi bi-printer me-1"></i> Cetak Laporan</button>
                </div>
            `;

            html += renderKertasKerjaView(journalEntries, adjEntries);
            document.getElementById('tab-content').innerHTML = html;
        }

        function printNeracaDisesuaikan() {
            const table = document.getElementById('kertasKerjaTable');
            if (!table) return;
            printKeuanganReport('Neraca Saldo Disesuaikan', 'Laporan Neraca Saldo Setelah Penyesuaian (6 Kolom)', table.outerHTML);
        }

        // ----------------------------------------------------
        // --- 6. LAPORAN LABA RUGI (INCOME STATEMENT) ---
        // ----------------------------------------------------
        async function loadLabaRugi() {
            await fetchKeuanganData();
            const jEntries = window.keuanganJournalEntries || [];
            const aEntries = window.keuanganAdjustmentEntries || [];

            const set = new Set();
            jEntries.forEach(e => { set.add(e.debetAkun); set.add(e.kreditAkun); });
            aEntries.forEach(e => { set.add(e.debetAkun); set.add(e.kreditAkun); });
            const allAccounts = Array.from(set);

            const revenues = [];
            const expenses = [];

            allAccounts.forEach(acc => {
                const l = acc.toLowerCase();
                if (l.includes("pendapatan") || l.includes("penjualan")) {
                    if (l.includes("diterima di muka") || l.includes("ditangguhkan")) return; // skip kewajiban
                    const debSum = jEntries.filter(e => e.debetAkun === acc).reduce((s, e) => s + (Number(e.nominal) || 0), 0) +
                                   aEntries.filter(e => e.debetAkun === acc).reduce((s, e) => s + (Number(e.nominal) || 0), 0);
                    const kredSum = jEntries.filter(e => e.kreditAkun === acc).reduce((s, e) => s + (Number(e.nominal) || 0), 0) +
                                    aEntries.filter(e => e.kreditAkun === acc).reduce((s, e) => s + (Number(e.nominal) || 0), 0);
                    const netKred = kredSum - debSum;
                    if (netKred > 0) revenues.push({ account: acc, amount: netKred, ref: getKeuanganAccountRef(acc) });
                } else if (l.includes("beban") || l.includes("biaya") || l.includes("penyusutan") || l.includes("harga pokok penjualan")) {
                    if (l.includes("akumulasi")) return; // skip kontra aset
                    const debSum = jEntries.filter(e => e.debetAkun === acc).reduce((s, e) => s + (Number(e.nominal) || 0), 0) +
                                   aEntries.filter(e => e.debetAkun === acc).reduce((s, e) => s + (Number(e.nominal) || 0), 0);
                    const kredSum = jEntries.filter(e => e.kreditAkun === acc).reduce((s, e) => s + (Number(e.nominal) || 0), 0) +
                                    aEntries.filter(e => e.kreditAkun === acc).reduce((s, e) => s + (Number(e.nominal) || 0), 0);
                    const netDeb = debSum - kredSum;
                    if (netDeb > 0) expenses.push({ account: acc, amount: netDeb, ref: getKeuanganAccountRef(acc) });
                }
            });

            const totalRev = revenues.reduce((s, r) => s + r.amount, 0);
            const totalExp = expenses.reduce((s, r) => s + r.amount, 0);
            const netProfit = totalRev - totalExp;
            const isProfit = netProfit >= 0;
            const profitMargin = totalRev > 0 ? ((netProfit / totalRev) * 100).toFixed(1) : 0;

            let html = renderKeuanganSubNav('laba_rugi');

            html += `
                <div class="d-flex flex-wrap justify-content-between align-items-center mb-4 gap-2">
                    <div>
                        <h4 class="fw-bold mb-0 text-success d-flex align-items-center gap-2">
                            <i class="bi bi-graph-up"></i> Laporan Laba Rugi (Income Statement)
                        </h4>
                        <small class="text-secondary">Ringkasan performa finansial: Pendapatan, Beban Operasional, dan Laba Bersih</small>
                    </div>
                    <button class="btn btn-sm btn-outline-secondary" onclick="printLabaRugi()"><i class="bi bi-printer me-1"></i> Cetak Laba Rugi</button>
                </div>

                <!-- HERO SHOWCASE CARD -->
                <div class="card card-custom p-4 mb-4 border-${dol}{isProfit ? 'success' : 'danger'}" style="border-radius: 20px; background: ${dol}{isProfit ? 'rgba(76, 175, 80, 0.08)' : 'rgba(244, 67, 54, 0.08)'};">
                    <div class="d-flex flex-wrap justify-content-between align-items-center gap-3">
                        <div>
                            <span class="badge bg-${dol}{isProfit ? 'success' : 'danger'} fs-6 px-3 py-2 fw-bold text-uppercase mb-2">
                                <i class="bi bi-${dol}{isProfit ? 'trophy-fill' : 'exclamation-triangle-fill'} me-1"></i>
                                ${dol}{isProfit ? 'LABA BERSIH (NET PROFIT)' : 'RUGI BERSIH (NET LOSS)'}
                            </span>
                            <h2 class="fw-bold text-${dol}{isProfit ? 'success' : 'danger'} mb-1">${dol}{formatRupiah(Math.abs(netProfit))}</h2>
                            <small class="text-secondary">Margin Keuntungan Bersih: <strong class="text-body">${dol}{profitMargin}%</strong> dari total pendapatan</small>
                        </div>
                        <div class="text-md-end">
                            <div class="small text-secondary mb-1">Total Pendapatan: <strong class="text-success">${dol}{formatRupiah(totalRev)}</strong></div>
                            <div class="small text-secondary mb-1">Total Beban Operasional: <strong class="text-danger">${dol}{formatRupiah(totalExp)}</strong></div>
                        </div>
                    </div>
                </div>

                <div class="row g-4" id="labaRugiContent">
                    <!-- 1. PENDAPATAN -->
                    <div class="col-12 col-lg-6">
                        <div class="card card-custom p-4 h-100" style="border-radius: 20px;">
                            <div class="d-flex justify-content-between align-items-center mb-3">
                                <h6 class="fw-bold mb-0 text-success"><i class="bi bi-cash-stack me-2"></i>1. Pendapatan Usaha</h6>
                                <span class="badge bg-success bg-opacity-25 text-success">${dol}{revenues.length} Sumber</span>
                            </div>
                            <div class="table-responsive">
                                <table class="table table-dark-custom align-middle mb-0">
                                    <thead>
                                        <tr>
                                            <th>Akun Pendapatan</th>
                                            <th class="text-center" style="width: 70px;">Ref</th>
                                            <th class="text-end" style="width: 140px;">Nominal (Rp)</th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        ${dol}{revenues.length === 0 ? `
                                            <tr><td colspan="3" class="text-center py-4 text-secondary">Belum ada akun pendapatan tercatat</td></tr>
                                        ` : revenues.map(r => `
                                            <tr>
                                                <td class="fw-semibold">${dol}{escapeHtml(r.account)}</td>
                                                <td class="text-center text-secondary small">${dol}{r.ref}</td>
                                                <td class="text-end fw-bold text-success">${dol}{formatRupiah(r.amount)}</td>
                                            </tr>
                                        `).join('')}
                                    </tbody>
                                    <tfoot class="border-top border-secondary fw-bold">
                                        <tr>
                                            <td colspan="2" class="text-uppercase">Total Pendapatan:</td>
                                            <td class="text-end text-success">${dol}{formatRupiah(totalRev)}</td>
                                        </tr>
                                    </tfoot>
                                </table>
                            </div>
                        </div>
                    </div>

                    <!-- 2. BEBAN OPERASIONAL -->
                    <div class="col-12 col-lg-6">
                        <div class="card card-custom p-4 h-100" style="border-radius: 20px;">
                            <div class="d-flex justify-content-between align-items-center mb-3">
                                <h6 class="fw-bold mb-0 text-danger"><i class="bi bi-receipt me-2"></i>2. Beban Operasional</h6>
                                <span class="badge bg-danger bg-opacity-25 text-danger">${dol}{expenses.length} Akun</span>
                            </div>
                            <div class="table-responsive">
                                <table class="table table-dark-custom align-middle mb-0">
                                    <thead>
                                        <tr>
                                            <th>Akun Beban & Biaya</th>
                                            <th class="text-center" style="width: 70px;">Ref</th>
                                            <th class="text-end" style="width: 140px;">Nominal (Rp)</th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        ${dol}{expenses.length === 0 ? `
                                            <tr><td colspan="3" class="text-center py-4 text-secondary">Belum ada beban operasional tercatat</td></tr>
                                        ` : expenses.map(e => `
                                            <tr>
                                                <td class="fw-semibold">${dol}{escapeHtml(e.account)}</td>
                                                <td class="text-center text-secondary small">${dol}{e.ref}</td>
                                                <td class="text-end fw-bold text-danger">${dol}{formatRupiah(e.amount)}</td>
                                            </tr>
                                        `).join('')}
                                    </tbody>
                                    <tfoot class="border-top border-secondary fw-bold">
                                        <tr>
                                            <td colspan="2" class="text-uppercase">Total Beban:</td>
                                            <td class="text-end text-danger">${dol}{formatRupiah(totalExp)}</td>
                                        </tr>
                                    </tfoot>
                                </table>
                            </div>
                        </div>
                    </div>
                </div>
            `;

            document.getElementById('tab-content').innerHTML = html;
        }

        function printLabaRugi() {
            const content = document.getElementById('labaRugiContent');
            if (!content) return;
            printKeuanganReport('Laporan Laba Rugi', 'Laporan Kinerja Keuangan & Hasil Usaha', content.outerHTML);
        }

        // ----------------------------------------------------
        // --- 7. JURNAL PENUTUP (CLOSING ENTRIES) ---
        // ----------------------------------------------------
        async function loadJurnalPenutup() {
            await fetchKeuanganData();
            const jEntries = window.keuanganJournalEntries || [];
            const aEntries = window.keuanganAdjustmentEntries || [];

            const set = new Set();
            jEntries.forEach(e => { set.add(e.debetAkun); set.add(e.kreditAkun); });
            aEntries.forEach(e => { set.add(e.debetAkun); set.add(e.kreditAkun); });
            const allAccounts = Array.from(set);

            const closingRows = [];
            const closingDate = jEntries.length > 0 ? jEntries[jEntries.length - 1].tanggal : new Date().toISOString().split('T')[0];

            // 1. Tutup Akun Pendapatan ke Ikhtisar Laba Rugi
            const revList = [];
            allAccounts.forEach(acc => {
                const l = acc.toLowerCase();
                if (l.includes("pendapatan") || l.includes("penjualan")) {
                    if (l.includes("diterima di muka") || l.includes("ditangguhkan")) return;
                    const dSum = jEntries.filter(e => e.debetAkun === acc).reduce((s, e) => s + (Number(e.nominal) || 0), 0) +
                                 aEntries.filter(e => e.debetAkun === acc).reduce((s, e) => s + (Number(e.nominal) || 0), 0);
                    const kSum = jEntries.filter(e => e.kreditAkun === acc).reduce((s, e) => s + (Number(e.nominal) || 0), 0) +
                                 aEntries.filter(e => e.kreditAkun === acc).reduce((s, e) => s + (Number(e.nominal) || 0), 0);
                    const net = kSum - dSum;
                    if (net > 0) revList.push({ account: acc, amount: net });
                }
            });

            const totRev = revList.reduce((s, r) => s + r.amount, 0);
            if (revList.length > 0) {
                revList.forEach(r => {
                    closingRows.push({ tanggal: closingDate, keterangan: r.account, ref: getKeuanganAccountRef(r.account), isDebit: true, debit: r.amount, credit: 0, group: "1. Menutup Akun Pendapatan" });
                });
                closingRows.push({ tanggal: closingDate, keterangan: "Ikhtisar Laba Rugi", ref: "303", isDebit: false, debit: 0, credit: totRev, group: "1. Menutup Akun Pendapatan" });
            }

            // 2. Tutup Akun Beban ke Ikhtisar Laba Rugi
            const expList = [];
            allAccounts.forEach(acc => {
                const l = acc.toLowerCase();
                if (l.includes("beban") || l.includes("biaya") || l.includes("penyusutan") || l.includes("harga pokok penjualan")) {
                    if (l.includes("akumulasi")) return;
                    const dSum = jEntries.filter(e => e.debetAkun === acc).reduce((s, e) => s + (Number(e.nominal) || 0), 0) +
                                 aEntries.filter(e => e.debetAkun === acc).reduce((s, e) => s + (Number(e.nominal) || 0), 0);
                    const kSum = jEntries.filter(e => e.kreditAkun === acc).reduce((s, e) => s + (Number(e.nominal) || 0), 0) +
                                 aEntries.filter(e => e.kreditAkun === acc).reduce((s, e) => s + (Number(e.nominal) || 0), 0);
                    const net = dSum - kSum;
                    if (net > 0) expList.push({ account: acc, amount: net });
                }
            });

            const totExp = expList.reduce((s, r) => s + r.amount, 0);
            if (expList.length > 0) {
                closingRows.push({ tanggal: closingDate, keterangan: "Ikhtisar Laba Rugi", ref: "303", isDebit: true, debit: totExp, credit: 0, group: "2. Menutup Akun Beban" });
                expList.forEach(e => {
                    closingRows.push({ tanggal: closingDate, keterangan: e.account, ref: getKeuanganAccountRef(e.account), isDebit: false, debit: 0, credit: e.amount, group: "2. Menutup Akun Beban" });
                });
            }

            // 3. Tutup Ikhtisar Laba Rugi ke Modal Pemilik
            const netLabaRugi = totRev - totExp;
            if (netLabaRugi !== 0) {
                if (netLabaRugi > 0) {
                    // Laba -> Debit Ikhtisar, Kredit Modal
                    closingRows.push({ tanggal: closingDate, keterangan: "Ikhtisar Laba Rugi", ref: "303", isDebit: true, debit: netLabaRugi, credit: 0, group: "3. Menutup Saldo Laba/Rugi ke Modal" });
                    closingRows.push({ tanggal: closingDate, keterangan: "Modal pemilik", ref: "301", isDebit: false, debit: 0, credit: netLabaRugi, group: "3. Menutup Saldo Laba/Rugi ke Modal" });
                } else {
                    // Rugi -> Debit Modal, Kredit Ikhtisar
                    const absLoss = Math.abs(netLabaRugi);
                    closingRows.push({ tanggal: closingDate, keterangan: "Modal pemilik", ref: "301", isDebit: true, debit: absLoss, credit: 0, group: "3. Menutup Saldo Laba/Rugi ke Modal" });
                    closingRows.push({ tanggal: closingDate, keterangan: "Ikhtisar Laba Rugi", ref: "303", isDebit: false, debit: 0, credit: absLoss, group: "3. Menutup Saldo Laba/Rugi ke Modal" });
                }
            }

            // 4. Tutup Prive ke Modal Pemilik
            let totalPrive = 0;
            allAccounts.forEach(acc => {
                if (acc.toLowerCase().includes("prive")) {
                    const dSum = jEntries.filter(e => e.debetAkun === acc).reduce((s, e) => s + (Number(e.nominal) || 0), 0) +
                                 aEntries.filter(e => e.debetAkun === acc).reduce((s, e) => s + (Number(e.nominal) || 0), 0);
                    const kSum = jEntries.filter(e => e.kreditAkun === acc).reduce((s, e) => s + (Number(e.nominal) || 0), 0) +
                                 aEntries.filter(e => e.kreditAkun === acc).reduce((s, e) => s + (Number(e.nominal) || 0), 0);
                    const net = dSum - kSum;
                    if (net > 0) totalPrive += net;
                }
            });

            if (totalPrive > 0) {
                closingRows.push({ tanggal: closingDate, keterangan: "Modal pemilik", ref: "301", isDebit: true, debit: totalPrive, credit: 0, group: "4. Menutup Akun Prive" });
                closingRows.push({ tanggal: closingDate, keterangan: "Prive", ref: "302", isDebit: false, debit: 0, credit: totalPrive, group: "4. Menutup Akun Prive" });
            }

            const grandDeb = closingRows.reduce((s, r) => s + r.debit, 0);
            const grandKred = closingRows.reduce((s, r) => s + r.credit, 0);

            let html = renderKeuanganSubNav('jurnal_penutup');

            html += `
                <div class="d-flex flex-wrap justify-content-between align-items-center mb-4 gap-2">
                    <div>
                        <h4 class="fw-bold mb-0 text-success d-flex align-items-center gap-2">
                            <i class="bi bi-lock-fill"></i> Jurnal Penutup (Closing Entries)
                        </h4>
                        <small class="text-secondary">Penutupan akun-akun nominal untuk mentransfer laba bersih & prive ke modal</small>
                    </div>
                    <button class="btn btn-sm btn-outline-secondary" onclick="printJurnalPenutup()"><i class="bi bi-printer me-1"></i> Cetak Jurnal Penutup</button>
                </div>

                <div class="card card-custom p-3 p-md-4 mb-4" style="border-radius: 20px;">
                    <div class="table-responsive">
                        <table class="table table-dark-custom align-middle mb-0" id="closingJournalTable">
                            <thead>
                                <tr>
                                    <th style="width: 120px;">Tanggal</th>
                                    <th>Keterangan / Pos Akun Ditutup</th>
                                    <th class="text-center" style="width: 80px;">Ref</th>
                                    <th class="text-end" style="width: 160px;">Debet (Rp)</th>
                                    <th class="text-end" style="width: 160px;">Kredit (Rp)</th>
                                </tr>
                            </thead>
                            <tbody>
            `;

            if (closingRows.length === 0) {
                html += `
                    <tr>
                        <td colspan="5" class="text-center py-5 text-secondary">
                            <i class="bi bi-lock fs-1 d-block mb-2 text-secondary opacity-50"></i>
                            <span class="fw-bold d-block">Belum ada akun nominal untuk ditutup</span>
                            <small>Jurnal penutup akan terisi otomatis saat Anda memiliki akun pendapatan/beban di periode ini.</small>
                        </td>
                    </tr>
                `;
            } else {
                let currentGrp = "";
                closingRows.forEach(r => {
                    if (r.group !== currentGrp) {
                        currentGrp = r.group;
                        html += `
                            <tr class="table-active">
                                <td colspan="5" class="fw-bold text-success small py-2">${dol}{escapeHtml(currentGrp)}</td>
                            </tr>
                        `;
                    }
                    html += `
                        <tr>
                            <td class="text-secondary small">${dol}{escapeHtml(r.tanggal || '-')}</td>
                            <td class="${dol}{r.isDebit ? 'fw-bold text-body' : 'text-info ps-4'}">${dol}{escapeHtml(r.keterangan)}</td>
                            <td class="text-center text-secondary small">${dol}{r.ref}</td>
                            <td class="text-end fw-bold ${dol}{r.debit > 0 ? 'text-success' : 'text-secondary opacity-50'}">${dol}{r.debit > 0 ? formatRupiah(r.debit) : '-'}</td>
                            <td class="text-end fw-bold ${dol}{r.credit > 0 ? 'text-info' : 'text-secondary opacity-50'}">${dol}{r.credit > 0 ? formatRupiah(r.credit) : '-'}</td>
                        </tr>
                    `;
                });
            }

            html += `
                            </tbody>
                            <tfoot class="border-top border-secondary fw-bold">
                                <tr>
                                    <td colspan="3" class="text-end text-uppercase">Total Jurnal Penutup:</td>
                                    <td class="text-end text-success">${dol}{formatRupiah(grandDeb)}</td>
                                    <td class="text-end text-info">${dol}{formatRupiah(grandKred)}</td>
                                </tr>
                            </tfoot>
                        </table>
                    </div>
                </div>
            `;

            document.getElementById('tab-content').innerHTML = html;
        }

        function printJurnalPenutup() {
            const table = document.getElementById('closingJournalTable');
            if (!table) return;
            printKeuanganReport('Jurnal Penutup', 'Laporan Jurnal Penutup Akhir Periode', table.outerHTML);
        }

        // ----------------------------------------------------
        // --- 8. NERACA SALDO SETELAH PENUTUPAN ---
        // ----------------------------------------------------
        async function loadNeracaSaldoSetelahTutup() {
            await fetchKeuanganData();
            const jEntries = window.keuanganJournalEntries || [];
            const aEntries = window.keuanganAdjustmentEntries || [];

            const set = new Set();
            jEntries.forEach(e => { set.add(e.debetAkun); set.add(e.kreditAkun); });
            aEntries.forEach(e => { set.add(e.debetAkun); set.add(e.kreditAkun); });
            const allAccounts = Array.from(set);

            // 1. Hitung Laba/Rugi & Prive
            let totalRev = 0, totalExp = 0, totalPrive = 0;
            allAccounts.forEach(acc => {
                const l = acc.toLowerCase();
                const dSum = jEntries.filter(e => e.debetAkun === acc).reduce((s, e) => s + (Number(e.nominal) || 0), 0) +
                             aEntries.filter(e => e.debetAkun === acc).reduce((s, e) => s + (Number(e.nominal) || 0), 0);
                const kSum = jEntries.filter(e => e.kreditAkun === acc).reduce((s, e) => s + (Number(e.nominal) || 0), 0) +
                             aEntries.filter(e => e.kreditAkun === acc).reduce((s, e) => s + (Number(e.nominal) || 0), 0);

                if (l.includes("pendapatan") || l.includes("penjualan")) {
                    if (!l.includes("diterima di muka") && !l.includes("ditangguhkan")) {
                        const net = kSum - dSum;
                        if (net > 0) totalRev += net;
                    }
                } else if (l.includes("beban") || l.includes("biaya") || l.includes("penyusutan") || l.includes("harga pokok penjualan")) {
                    if (!l.includes("akumulasi")) {
                        const net = dSum - kSum;
                        if (net > 0) totalExp += net;
                    }
                } else if (l.includes("prive")) {
                    const net = dSum - kSum;
                    if (net > 0) totalPrive += net;
                }
            });

            const netProfit = totalRev - totalExp;

            // 2. Buat Akun Riil Saja (Aset, Liabilitas, Modal Disesuaikan)
            const postRows = [];
            const customAccs = allAccounts.filter(a => !KEUANGAN_ACCOUNTS_ORDERED.includes(a)).sort();
            const orderedAccounts = KEUANGAN_ACCOUNTS_ORDERED.filter(a => allAccounts.includes(a)).concat(customAccs);

            orderedAccounts.forEach(acc => {
                const l = acc.toLowerCase();
                // Skip akun nominal
                if (l.includes("pendapatan") || l.includes("penjualan")) {
                    if (!l.includes("diterima di muka") && !l.includes("ditangguhkan")) return;
                }
                if (l.includes("beban") || l.includes("biaya") || l.includes("penyusutan") || l.includes("harga pokok penjualan")) {
                    if (!l.includes("akumulasi")) return;
                }
                if (l.includes("prive") || l.includes("ikhtisar")) return;

                const dSum = jEntries.filter(e => e.debetAkun === acc).reduce((s, e) => s + (Number(e.nominal) || 0), 0) +
                             aEntries.filter(e => e.debetAkun === acc).reduce((s, e) => s + (Number(e.nominal) || 0), 0);
                const kSum = jEntries.filter(e => e.kreditAkun === acc).reduce((s, e) => s + (Number(e.nominal) || 0), 0) +
                             aEntries.filter(e => e.kreditAkun === acc).reduce((s, e) => s + (Number(e.nominal) || 0), 0);

                const normalType = getKeuanganNormalBalanceType(acc);

                if (l.includes("modal")) {
                    const initialModal = normalType === 'K' ? (kSum - dSum) : (dSum - kSum);
                    const updatedModal = initialModal + netProfit - totalPrive;
                    if (updatedModal >= 0) {
                        postRows.push({ account: acc, ref: getKeuanganAccountRef(acc), debit: 0, credit: updatedModal });
                    } else {
                        postRows.push({ account: acc, ref: getKeuanganAccountRef(acc), debit: Math.abs(updatedModal), credit: 0 });
                    }
                } else {
                    let cDeb = 0, cKred = 0;
                    if (normalType === 'D') {
                        const net = dSum - kSum;
                        if (net >= 0) cDeb = net; else cKred = Math.abs(net);
                    } else {
                        const net = kSum - dSum;
                        if (net >= 0) cKred = net; else cDeb = Math.abs(net);
                    }
                    if (cDeb > 0 || cKred > 0) {
                        postRows.push({ account: acc, ref: getKeuanganAccountRef(acc), debit: cDeb, credit: cKred });
                    }
                }
            });

            const grandDeb = postRows.reduce((s, r) => s + r.debit, 0);
            const grandKred = postRows.reduce((s, r) => s + r.credit, 0);
            const isBalanced = (grandDeb === grandKred) && postRows.length > 0;

            let html = renderKeuanganSubNav('neraca_saldo_setelah_tutup');

            html += `
                <div class="d-flex flex-wrap justify-content-between align-items-center mb-4 gap-2">
                    <div>
                        <h4 class="fw-bold mb-0 text-success d-flex align-items-center gap-2">
                            <i class="bi bi-file-earmark-ruled"></i> Neraca Saldo Setelah Penutupan (Post-Closing Trial Balance)
                        </h4>
                        <small class="text-secondary">Keseimbangan akun-akun riil (Neraca Aset, Kewajiban, & Ekuitas Akhir) untuk periode berikutnya</small>
                    </div>
                    <button class="btn btn-sm btn-outline-secondary" onclick="printNeracaSetelahTutup()"><i class="bi bi-printer me-1"></i> Cetak Laporan</button>
                </div>

                <!-- 3 RINGKASAN STATUS KESEIMBANGAN AKUN RIIL -->
                <div class="row g-3 mb-4">
                    <div class="col-12 col-sm-4">
                        <div class="card card-custom p-3 h-100">
                            <small class="text-secondary d-block mb-1">Total Aset (Debet)</small>
                            <h4 class="fw-bold text-success mb-0">${dol}{formatRupiah(grandDeb)}</h4>
                            <small class="text-secondary mt-1 d-block">Kas, Piutang, Perlengkapan, Aset Tetap</small>
                        </div>
                    </div>
                    <div class="col-12 col-sm-4">
                        <div class="card card-custom p-3 h-100">
                            <small class="text-secondary d-block mb-1">Total Kewajiban & Ekuitas (Kredit)</small>
                            <h4 class="fw-bold text-info mb-0">${dol}{formatRupiah(grandKred)}</h4>
                            <small class="text-secondary mt-1 d-block">Utang & Modal Pemilik Akhir</small>
                        </div>
                    </div>
                    <div class="col-12 col-sm-4">
                        <div class="card card-custom p-3 h-100 border-${dol}{isBalanced ? 'success' : 'danger'}">
                            <small class="text-secondary d-block mb-1">Status Keseimbangan Riil</small>
                            <h5 class="fw-bold text-${dol}{isBalanced ? 'success' : 'danger'} mb-0">
                                <i class="bi bi-${dol}{isBalanced ? 'shield-check' : 'exclamation-triangle'} me-1"></i>
                                ${dol}{isBalanced ? 'SEIMBANG (BALANCE)' : 'TIDAK SEIMBANG'}
                            </h5>
                            <small class="text-secondary mt-1 d-block">Selisih: ${dol}{formatRupiah(Math.abs(grandDeb - grandKred))}</small>
                        </div>
                    </div>
                </div>

                <!-- TABEL NERACA SETELAH TUTUP -->
                <div class="card card-custom p-3 p-md-4 mb-4" style="border-radius: 20px;">
                    <div class="table-responsive">
                        <table class="table table-dark-custom align-middle mb-0" id="postClosingTable">
                            <thead>
                                <tr>
                                    <th style="width: 60px;">No</th>
                                    <th class="text-center" style="width: 90px;">Kode Ref</th>
                                    <th>Nama Akun Riil</th>
                                    <th class="text-end" style="width: 180px;">Debet (Rp)</th>
                                    <th class="text-end" style="width: 180px;">Kredit (Rp)</th>
                                </tr>
                            </thead>
                            <tbody>
            `;

            if (postRows.length === 0) {
                html += `
                    <tr>
                        <td colspan="5" class="text-center py-5 text-secondary">
                            <i class="bi bi-file-earmark-ruled fs-1 d-block mb-2 text-secondary opacity-50"></i>
                            <span class="fw-bold d-block">Neraca Saldo Setelah Penutupan Kosong</span>
                            <small>Data otomatis terhitung saat terdapat transaksi di Jurnal Umum.</small>
                        </td>
                    </tr>
                `;
            } else {
                postRows.forEach((r, idx) => {
                    html += `
                        <tr>
                            <td class="text-secondary small">${dol}{idx + 1}</td>
                            <td class="text-center text-secondary small">${dol}{r.ref}</td>
                            <td class="fw-bold">${dol}{escapeHtml(r.account)}</td>
                            <td class="text-end fw-bold ${dol}{r.debit > 0 ? 'text-success' : 'text-secondary opacity-50'}">${dol}{r.debit > 0 ? formatRupiah(r.debit) : '-'}</td>
                            <td class="text-end fw-bold ${dol}{r.credit > 0 ? 'text-info' : 'text-secondary opacity-50'}">${dol}{r.credit > 0 ? formatRupiah(r.credit) : '-'}</td>
                        </tr>
                    `;
                });
            }

            html += `
                            </tbody>
                            <tfoot class="border-top border-secondary fw-bold">
                                <tr>
                                    <td colspan="3" class="text-end text-uppercase">Total Saldo Riil:</td>
                                    <td class="text-end text-success">${dol}{formatRupiah(grandDeb)}</td>
                                    <td class="text-end text-info">${dol}{formatRupiah(grandKred)}</td>
                                </tr>
                            </tfoot>
                        </table>
                    </div>
                </div>
            `;

            document.getElementById('tab-content').innerHTML = html;
        }

        function printNeracaSetelahTutup() {
            const table = document.getElementById('postClosingTable');
            if (!table) return;
            printKeuanganReport('Neraca Saldo Setelah Penutupan', 'Laporan Neraca Saldo Setelah Tutup Buku', table.outerHTML);
        }
        """
    }
}
