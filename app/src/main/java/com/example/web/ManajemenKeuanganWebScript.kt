package com.example.web

object ManajemenKeuanganWebScript {
    fun getScript(dol: String): String {
        return """
        // ==========================================
        // --- MODUL MANAJEMEN KEUANGAN LENGKAP ---
        // (Kasir POS, Daftar Belanja, Catatan Tabungan)
        // ==========================================

        window.mkKasirActiveSubTab = 'dasbor'; // 'dasbor', 'stok', 'pos', 'riwayat'
        window.mkPosCart = window.mkPosCart || [];
        window.mkKasirData = { namaUsaha: 'Usaha Saya', stock: [], cashier: [] };
        window.mkKasirSearchStock = '';
        window.mkKasirCategoryStock = 'Semua';
        window.mkKasirSearchPos = '';
        window.mkKasirCategoryPos = 'Semua';
        window.mkKasirSearchHistory = '';
        window.mkKasirDateHistory = '';
        window.mkKasirTaxEnabled = false;
        window.mkKasirTaxRate = 0.11;
        window.mkKasirDiscount = 0;
        window.mkKasirCashInput = '';
        window.html5QrCodeScanner = null;

        // --- SHOPPING / DAFTAR BELANJA STATE ---
        window.mkShoppingList = [];
        window.mkShoppingSearch = '';
        window.mkShoppingCategory = 'Semua';
        window.mkShoppingDateStart = '';
        window.mkShoppingDateEnd = '';
        window.mkEditingShoppingId = null;

        // --- SAVINGS / CATATAN TABUNGAN STATE ---
        window.mkSavingsTarget = { id: 1, nama: 'Tabungan Utama', jumlah: 0 };
        window.mkSavingsTransactions = [];
        window.mkSavingsSearch = '';
        window.mkSavingsTypeFilter = 'Semua'; // 'Semua', 'masuk', 'keluar'
        window.mkSavingsCategoryFilter = 'Semua';
        window.mkSavingsDateFilter = '';
        window.mkEditingSavingId = null;

        // Formatter Helper Functions
        function mkFormatRupiah(val) {
            const n = Math.round(Number(val) || 0);
            return 'Rp ' + n.toLocaleString('id-ID');
        }

        function mkFormatDateIndo(dateStr) {
            if (!dateStr) return '-';
            try {
                const parts = dateStr.split(' ')[0].split('-');
                if (parts.length === 3) {
                    const months = ['Januari', 'Februari', 'Maret', 'April', 'Mei', 'Juni', 'Juli', 'Agustus', 'September', 'Oktober', 'November', 'Desember'];
                    const d = parseInt(parts[2], 10);
                    const m = months[parseInt(parts[1], 10) - 1] || parts[1];
                    const y = parts[0];
                    const timePart = dateStr.includes(' ') ? ' ' + dateStr.split(' ')[1] : '';
                    return d + ' ' + m + ' ' + y + timePart;
                }
                return dateStr;
            } catch (e) {
                return dateStr;
            }
        }

        function renderManajemenKeuanganSubNav(activeKey) {
            // Button titles removed as requested; sub-menus are accessed via the sidebar navigation
            return '';
        }

        // ==========================================
        // 1. MODUL HALAMAN KASIR (POS & STOK & RIWAYAT)
        // ==========================================

        async function fetchKasirData() {
            try {
                const res = await fetch('/api/kasir');
                if (res.ok) {
                    const data = await res.json();
                    window.mkKasirData = {
                        namaUsaha: data.namaUsaha || 'Usaha Saya',
                        stock: data.stock || [],
                        cashier: data.cashier || []
                    };
                }
            } catch (e) {
                console.error("Gagal memuat data kasir:", e);
            }
        }

        async function loadKasir(subTab) {
            if (subTab) window.mkKasirActiveSubTab = subTab;
            await fetchKasirData();
            renderKasirUi();
        }

        function renderKasirUi() {
            const container = document.getElementById('tab-content') || document.getElementById('main-content');
            if (!container) return;

            const stockList = window.mkKasirData.stock || [];
            const cashierList = window.mkKasirData.cashier || [];
            const namaUsaha = window.mkKasirData.namaUsaha || 'Usaha Saya';

            const totalSales = cashierList.reduce((acc, tx) => acc + (Number(tx.totalHarga) || 0), 0);
            const lowStockCount = stockList.filter(s => s.stok > 0 && s.stok <= 2).length;
            const outOfStockCount = stockList.filter(s => s.stok <= 0).length;

            let html = `
                <div class="container-fluid px-0">
                    ${dol}{renderManajemenKeuanganSubNav('kasir')}

                    <!-- Header Profil Usaha & Quick Summary (Mobile Responsive) -->
                    <div class="card card-custom kasir-header-card p-3 p-sm-4 mb-3 mb-sm-4">
                        <div class="d-flex flex-column flex-md-row justify-content-between align-items-start align-items-md-center gap-3">
                            <div class="d-flex align-items-center gap-2 gap-sm-3">
                                <div class="bg-primary text-white rounded-circle p-2 p-sm-3 d-flex align-items-center justify-content-center shadow-sm flex-shrink-0" style="width: 48px; height: 48px; font-size: 20px;">
                                    <i class="bi bi-shop-window"></i>
                                </div>
                                <div>
                                    <div class="d-flex align-items-center gap-2 flex-wrap">
                                        <h4 class="fw-bold mb-0 text-break">${dol}{escapeHtml(namaUsaha)}</h4>
                                        <button class="btn btn-sm btn-outline-primary border-0 rounded-circle p-1" title="Ubah Nama Usaha" onclick="openEditNamaUsahaModal()">
                                            <i class="bi bi-pencil-square fs-6"></i>
                                        </button>
                                    </div>
                                    <div class="text-muted small mt-1"><i class="bi bi-shield-check text-success me-1"></i>Point of Sale (100% Offline Lokal)</div>
                                </div>
                            </div>
                            <div class="d-grid gap-2 d-sm-flex flex-wrap w-100 w-md-auto">
                                <button class="btn btn-success fw-semibold shadow-sm px-3" onclick="loadKasir('pos')">
                                    <i class="bi bi-calculator me-1"></i> Buka Kasir POS
                                </button>
                                <button class="btn btn-primary fw-semibold shadow-sm px-3" onclick="openAddStockModal()">
                                    <i class="bi bi-plus-circle me-1"></i> Tambah Stok
                                </button>
                                <button class="btn btn-outline-secondary fw-semibold px-3" onclick="openRestockModal()">
                                    <i class="bi bi-upc-scan me-1"></i> Restock
                                </button>
                                <button class="btn btn-outline-primary fw-semibold px-3" onclick="printKasirReport()">
                                    <i class="bi bi-printer me-1"></i> Laporan
                                </button>
                            </div>
                        </div>

                        <div class="row g-2 g-sm-3 mt-2 mt-sm-3">
                            <div class="col-12 col-sm-4">
                                <div class="card kasir-stat-sales p-3 h-100 shadow-none">
                                    <div class="text-muted small fw-semibold text-uppercase">Total Penjualan Kasir</div>
                                    <div class="fs-5 fs-sm-4 fw-bold text-success mt-1 text-break">${dol}{mkFormatRupiah(totalSales)}</div>
                                    <div class="text-muted small mt-1"><i class="bi bi-receipt me-1"></i> ${dol}{cashierList.length} Transaksi Selesai</div>
                                </div>
                            </div>
                            <div class="col-6 col-sm-4">
                                <div class="card kasir-stat-warning p-3 h-100 shadow-none">
                                    <div class="text-muted small fw-semibold text-uppercase">Stok Menipis (<= 2)</div>
                                    <div class="fs-5 fs-sm-4 fw-bold text-warning mt-1">${dol}{lowStockCount} Produk</div>
                                    <div class="text-muted small mt-1"><i class="bi bi-exclamation-triangle me-1"></i> Perlu restock</div>
                                </div>
                            </div>
                            <div class="col-6 col-sm-4">
                                <div class="card kasir-stat-danger p-3 h-100 shadow-none">
                                    <div class="text-muted small fw-semibold text-uppercase">Stok Habis (0 unit)</div>
                                    <div class="fs-5 fs-sm-4 fw-bold text-danger mt-1">${dol}{outOfStockCount} Produk</div>
                                    <div class="text-muted small mt-1"><i class="bi bi-x-circle me-1"></i> Kosong</div>
                                </div>
                            </div>
                        </div>
                    </div>

                    <!-- Kasir Sub Navigation Tabs (Mobile Smooth Horizontal Scrollable) -->
                    <div class="d-flex overflow-auto pb-2 mb-3 text-nowrap" style="-webkit-overflow-scrolling: touch;">
                        <ul class="nav nav-pills flex-nowrap gap-2">
                            <li class="nav-item">
                                <a class="nav-link kasir-nav-tab py-2 px-3 ${dol}{window.mkKasirActiveSubTab === 'dasbor' ? 'active' : ''}" href="javascript:void(0)" onclick="loadKasir('dasbor')">
                                    <i class="bi bi-grid-1x2 me-1"></i> Dasbor & Ringkasan
                                </a>
                            </li>
                            <li class="nav-item">
                                <a class="nav-link kasir-nav-tab py-2 px-3 ${dol}{window.mkKasirActiveSubTab === 'stok' ? 'active' : ''}" href="javascript:void(0)" onclick="loadKasir('stok')">
                                    <i class="bi bi-boxes me-1"></i> Stok Barang <span class="badge bg-secondary ms-1">${dol}{stockList.length}</span>
                                </a>
                            </li>
                            <li class="nav-item">
                                <a class="nav-link kasir-nav-tab py-2 px-3 ${dol}{window.mkKasirActiveSubTab === 'pos' ? 'active' : ''}" href="javascript:void(0)" onclick="loadKasir('pos')">
                                    <i class="bi bi-calculator me-1"></i> Kasir POS ${dol}{window.mkPosCart && window.mkPosCart.length > 0 ? ('<span class=\"badge bg-danger ms-1\">' + window.mkPosCart.length + '</span>') : ''}
                                </a>
                            </li>
                            <li class="nav-item">
                                <a class="nav-link kasir-nav-tab py-2 px-3 ${dol}{window.mkKasirActiveSubTab === 'riwayat' ? 'active' : ''}" href="javascript:void(0)" onclick="loadKasir('riwayat')">
                                    <i class="bi bi-clock-history me-1"></i> Riwayat Transaksi <span class="badge bg-secondary ms-1">${dol}{cashierList.length}</span>
                                </a>
                            </li>
                        </ul>
                    </div>

                    <!-- Content Sub Tab Switcher -->
                    <div id="kasir-subtab-container">
            `;

            if (window.mkKasirActiveSubTab === 'dasbor') {
                html += renderKasirDasborTab(stockList, cashierList);
            } else if (window.mkKasirActiveSubTab === 'stok') {
                html += renderKasirStokTab(stockList);
            } else if (window.mkKasirActiveSubTab === 'pos') {
                html += renderKasirPosTab(stockList);
            } else if (window.mkKasirActiveSubTab === 'riwayat') {
                html += renderKasirRiwayatTab(cashierList);
            }

            html += `
                    </div>
                </div>
            `;

            container.innerHTML = html;
        }

        // --- SUBTAB: DASBOR KASIR ---
        function renderKasirDasborTab(stockList, cashierList) {
            // Calculate Top Selling Products
            const itemSalesCount = {};
            cashierList.forEach(tx => {
                (tx.items || []).forEach(it => {
                    const name = it.namaBarang || 'Produk';
                    itemSalesCount[name] = (itemSalesCount[name] || 0) + (Number(it.qty) || 1);
                });
            });

            const topProducts = Object.entries(itemSalesCount)
                .map(([nama, qty]) => ({ nama, qty }))
                .sort((a, b) => b.qty - a.qty)
                .slice(0, 5);

            let html = `
                <div class="row g-3 g-lg-4">
                    <div class="col-12 col-lg-7">
                        <div class="card card-custom kasir-bestseller-card p-3 p-sm-4 h-100 shadow-sm">
                            <div class="d-flex justify-content-between align-items-center mb-3">
                                <h6 class="fw-bold mb-0"><i class="bi bi-trophy text-warning me-2"></i> 5 Produk Paling Laris</h6>
                                <span class="badge bg-primary bg-opacity-10 text-primary">Penjualan Tertinggi</span>
                            </div>
            `;

            if (topProducts.length === 0) {
                html += `<div class="text-center py-5 text-muted"><i class="bi bi-basket2 fs-1 d-block mb-2 opacity-50"></i>Belum ada transaksi penjualan yang tercatat.</div>`;
            } else {
                html += `<div class="list-group list-group-flush">`;
                topProducts.forEach((p, idx) => {
                    html += `
                        <div class="list-group-item d-flex flex-wrap justify-content-between align-items-center px-0 py-2 py-sm-3 border-bottom gap-2">
                            <div class="d-flex align-items-center gap-2 gap-sm-3">
                                <span class="badge bg-secondary rounded-circle d-flex align-items-center justify-content-center" style="width: 28px; height: 28px;">${dol}{idx + 1}</span>
                                <div>
                                    <div class="fw-bold text-break">${dol}{escapeHtml(p.nama)}</div>
                                    <small class="text-muted">Terjual ke pelanggan</small>
                                </div>
                            </div>
                            <span class="badge bg-success fs-6 px-3 py-1 py-sm-2 rounded-pill">${dol}{p.qty} Terjual</span>
                        </div>
                    `;
                });
                html += `</div>`;
            }

            html += `
                        </div>
                    </div>

                    <div class="col-12 col-lg-5">
                        <div class="card card-custom kasir-critical-stock-card p-3 p-sm-4 h-100 shadow-sm">
                            <div class="d-flex justify-content-between align-items-center mb-3">
                                <h6 class="fw-bold mb-0"><i class="bi bi-exclamation-triangle text-danger me-2"></i> Peringatan Stok Kritis</h6>
                                <button class="btn btn-sm btn-outline-secondary" onclick="loadKasir('stok')">Lihat Semua</button>
                            </div>
            `;

            const criticalStocks = stockList.filter(s => s.stok <= 2);
            if (criticalStocks.length === 0) {
                html += `<div class="text-center py-5 text-success"><i class="bi bi-check-circle fs-1 d-block mb-2"></i>Semua stok produk dalam kondisi aman!</div>`;
            } else {
                html += `<div class="list-group list-group-flush">`;
                criticalStocks.slice(0, 6).forEach(s => {
                    const badgeClass = s.stok <= 0 ? 'bg-danger' : 'bg-warning text-dark';
                    const statusText = s.stok <= 0 ? 'Habis (0)' : 'Sisa ' + s.stok;
                    html += `
                        <div class="list-group-item d-flex flex-wrap justify-content-between align-items-center px-0 py-2 border-bottom gap-2">
                            <div class="d-flex align-items-center gap-2">
                                <div class="rounded-2 overflow-hidden border d-flex align-items-center justify-content-center flex-shrink-0" style="width: 38px; height: 38px; background: rgba(0,0,0,0.04); ${dol}{s.foto ? 'cursor: pointer;' : ''}" ${dol}{s.foto ? `onclick="showPhotoZoomModal('${dol}{escapeHtml(s.nama)}', '${dol}{s.foto}')" title="Klik untuk perbesar foto"` : ''}>
                                    ${dol}{s.foto ? `<img src="${dol}{s.foto}" alt="${dol}{escapeHtml(s.nama)}" style="width: 100%; height: 100%; object-fit: cover;">` : `<i class="bi bi-box-seam text-secondary opacity-50"></i>`}
                                </div>
                                <div>
                                    <div class="fw-semibold text-break">${dol}{escapeHtml(s.nama)}</div>
                                    <small class="text-muted">${dol}{escapeHtml(s.kategori || 'Umum')} | ${dol}{mkFormatRupiah(s.harga)}</small>
                                </div>
                            </div>
                            <div class="d-flex align-items-center gap-2">
                                <span class="badge ${dol}{badgeClass}">${dol}{statusText}</span>
                                <button class="btn btn-sm btn-outline-success py-0 px-2" onclick="quickAddStock('${dol}{s.id}', 5)" title="Tambah 5 Stok">+5</button>
                            </div>
                        </div>
                    `;
                });
                html += `</div>`;
            }

            html += `
                        </div>
                    </div>
                </div>
            `;
            return html;
        }

        // --- SUBTAB: STOK BARANG (RESPONSIF MOBILE & DESKTOP) ---
        function renderKasirStokTab(stockList) {
            const categories = ['Semua', ...new Set(stockList.map(s => s.kategori || 'Umum'))];
            const search = (window.mkKasirSearchStock || '').toLowerCase();
            const cat = window.mkKasirCategoryStock || 'Semua';

            const filtered = stockList.filter(s => {
                const matchesSearch = (s.nama || '').toLowerCase().includes(search) || (s.barcode || '').toLowerCase().includes(search);
                const matchesCat = (cat === 'Semua') || ((s.kategori || 'Umum') === cat);
                return matchesSearch && matchesCat;
            });

            let html = `
                <div class="card card-custom kasir-filter-card p-3 p-sm-4 shadow-sm mb-4">
                    <!-- Responsive Filter Bar -->
                    <div class="row g-2 align-items-center mb-3">
                        <div class="col-12 col-md-6">
                            <div class="input-group">
                                <span class="input-group-text bg-transparent"><i class="bi bi-search"></i></span>
                                <input type="text" class="form-control" placeholder="Cari nama produk atau kode barcode..." value="${dol}{escapeHtml(window.mkKasirSearchStock)}" oninput="window.mkKasirSearchStock = this.value; renderKasirUi();">
                                ${dol}{window.mkKasirSearchStock ? '<button class="btn btn-outline-secondary" onclick="window.mkKasirSearchStock=\'\'; renderKasirUi();"><i class="bi bi-x"></i></button>' : ''}
                                <button class="btn btn-outline-primary" type="button" title="Scan Barcode Kamera (Offline)" onclick="openCameraScannerModalForInput(null, (code) => { window.mkKasirSearchStock = code; renderKasirUi(); }, 'Scan Barcode Cari Produk')"><i class="bi bi-camera"></i></button>
                            </div>
                        </div>
                        <div class="col-6 col-md-3">
                            <select class="form-select" onchange="window.mkKasirCategoryStock = this.value; renderKasirUi();">
            `;
            categories.forEach(c => {
                const sel = (c === cat) ? 'selected' : '';
                html += `<option value="${dol}{escapeHtml(c)}" ${dol}{sel}>${dol}{escapeHtml(c)}</option>`;
            });
            html += `
                            </select>
                        </div>
                        <div class="col-6 col-md-3">
                            <button class="btn btn-primary fw-semibold w-100 text-nowrap" onclick="openAddStockModal()">
                                <i class="bi bi-plus-lg me-1"></i> Tambah Produk
                            </button>
                        </div>
                    </div>

                    <!-- Mobile Card View (Tampilan Khusus Layar Smartphone) -->
                    <div class="d-block d-md-none">
            `;

            if (filtered.length === 0) {
                html += `<div class="text-center py-5 text-muted"><i class="bi bi-inbox fs-1 d-block mb-2 opacity-50"></i>Tidak ada data produk yang cocok.</div>`;
            } else {
                html += `<div class="d-flex flex-column gap-2">`;
                filtered.forEach(s => {
                    let statusBadge = '<span class="badge bg-success">Aman</span>';
                    if (s.stok <= 0) {
                        statusBadge = '<span class="badge bg-danger">Habis</span>';
                    } else if (s.stok <= 2) {
                        statusBadge = '<span class="badge bg-warning text-dark">Menipis</span>';
                    }

                    html += `
                        <div class="card kasir-mobile-card p-3 rounded-3">
                            <div class="d-flex justify-content-between align-items-start gap-2 mb-2">
                                <div class="d-flex align-items-center gap-2">
                                    <div class="rounded-3 overflow-hidden border d-flex align-items-center justify-content-center flex-shrink-0" style="width: 46px; height: 46px; background: rgba(0,0,0,0.04); ${dol}{s.foto ? 'cursor: pointer;' : ''}" ${dol}{s.foto ? `onclick="showPhotoZoomModal('${dol}{escapeHtml(s.nama)}', '${dol}{s.foto}')" title="Klik untuk perbesar foto"` : ''}>
                                        ${dol}{s.foto ? `<img src="${dol}{s.foto}" alt="${dol}{escapeHtml(s.nama)}" style="width: 100%; height: 100%; object-fit: cover;">` : `<i class="bi bi-box-seam text-secondary opacity-50 fs-5"></i>`}
                                    </div>
                                    <div>
                                        <div class="fw-bold fs-6 text-break">${dol}{escapeHtml(s.nama)}</div>
                                        <div class="d-flex flex-wrap align-items-center gap-1 mt-1">
                                            <code class="text-primary fw-bold small">${dol}{escapeHtml(s.barcode || '-')}</code>
                                            <span class="badge bg-secondary bg-opacity-10 text-body small">${dol}{escapeHtml(s.kategori || 'Umum')}</span>
                                        </div>
                                    </div>
                                </div>
                                <div class="text-end">
                                    <div class="fw-bold text-success">${dol}{mkFormatRupiah(s.harga)}</div>
                                    <div class="mt-1">${dol}{statusBadge} <span class="fw-bold ms-1">(${dol}{s.stok} unit)</span></div>
                                </div>
                            </div>
                            ${dol}{s.keterangan ? `<div class="small text-muted mb-2">${dol}{escapeHtml(s.keterangan)}</div>` : ''}
                            <div class="d-flex justify-content-between align-items-center border-top pt-2 mt-1">
                                <div class="btn-group btn-group-sm">
                                    <button class="btn btn-outline-success px-2" onclick="quickAddStock('${dol}{s.id}', 1)" title="Tambah 1 Stok">+1</button>
                                    <button class="btn btn-outline-success px-2" onclick="quickAddStock('${dol}{s.id}', 5)" title="Tambah 5 Stok">+5</button>
                                </div>
                                <div class="d-flex gap-1">
                                    <button class="btn btn-sm btn-outline-primary px-2" onclick="openEditStockModal('${dol}{s.id}')" title="Edit Produk"><i class="bi bi-pencil"></i> Edit</button>
                                    <button class="btn btn-sm btn-outline-danger px-2" onclick="deleteStockItem('${dol}{s.id}')" title="Hapus"><i class="bi bi-trash"></i></button>
                                </div>
                            </div>
                        </div>
                    `;
                });
                html += `</div>`;
            }

            html += `
                    </div>

                    <!-- Desktop Table View (Tampilan Layar Komputer / Tablet Lebar) -->
                    <div class="d-none d-md-block table-responsive">
                        <table class="table table-hover align-middle mb-0">
                            <thead class="table-light">
                                <tr>
                                    <th style="width: 58px;" class="text-center">Foto</th>
                                    <th>Kode / Barcode</th>
                                    <th>Nama Produk</th>
                                    <th>Kategori</th>
                                    <th>Harga Jual</th>
                                    <th class="text-center">Sisa Stok</th>
                                    <th class="text-center">Status</th>
                                    <th class="text-end">Aksi</th>
                                </tr>
                            </thead>
                            <tbody>
            `;

            if (filtered.length === 0) {
                html += `<tr><td colspan="8" class="text-center py-5 text-muted"><i class="bi bi-inbox fs-1 d-block mb-2 opacity-50"></i>Tidak ada data produk yang cocok.</td></tr>`;
            } else {
                filtered.forEach(s => {
                    let statusBadge = '<span class="badge bg-success">Aman</span>';
                    if (s.stok <= 0) {
                        statusBadge = '<span class="badge bg-danger">Habis</span>';
                    } else if (s.stok <= 2) {
                        statusBadge = '<span class="badge bg-warning text-dark">Menipis</span>';
                    }

                    html += `
                        <tr>
                            <td class="text-center">
                                <div class="rounded-2 overflow-hidden border d-inline-flex align-items-center justify-content-center shadow-xs" style="width: 44px; height: 44px; background: rgba(0,0,0,0.04); ${dol}{s.foto ? 'cursor: pointer;' : ''}" ${dol}{s.foto ? `onclick="showPhotoZoomModal('${dol}{escapeHtml(s.nama)}', '${dol}{s.foto}')" title="Klik untuk perbesar foto"` : ''}>
                                    ${dol}{s.foto ? `<img src="${dol}{s.foto}" alt="${dol}{escapeHtml(s.nama)}" style="width: 100%; height: 100%; object-fit: cover;">` : `<i class="bi bi-box-seam text-secondary opacity-50 fs-5"></i>`}
                                </div>
                            </td>
                            <td>
                                <code class="text-primary fw-bold">${dol}{escapeHtml(s.barcode || '-')}</code>
                            </td>
                            <td>
                                <div class="fw-bold">${dol}{escapeHtml(s.nama)}</div>
                                ${dol}{s.keterangan ? '<small class="text-muted">' + escapeHtml(s.keterangan) + '</small>' : ''}
                            </td>
                            <td><span class="badge bg-secondary bg-opacity-25 text-body">${dol}{escapeHtml(s.kategori || 'Umum')}</span></td>
                            <td class="fw-semibold text-success">${dol}{mkFormatRupiah(s.harga)}</td>
                            <td class="text-center fw-bold fs-6">${dol}{s.stok}</td>
                            <td class="text-center">${dol}{statusBadge}</td>
                            <td class="text-end text-nowrap">
                                <button class="btn btn-sm btn-outline-success py-1 px-2 me-1" onclick="quickAddStock('${dol}{s.id}', 1)" title="Tambah 1 Stok">+1</button>
                                <button class="btn btn-sm btn-outline-success py-1 px-2 me-1" onclick="quickAddStock('${dol}{s.id}', 5)" title="Tambah 5 Stok">+5</button>
                                <button class="btn btn-sm btn-outline-primary py-1 px-2 me-1" onclick="openEditStockModal('${dol}{s.id}')" title="Edit"><i class="bi bi-pencil"></i></button>
                                <button class="btn btn-sm btn-outline-danger py-1 px-2" onclick="deleteStockItem('${dol}{s.id}')" title="Hapus"><i class="bi bi-trash"></i></button>
                            </td>
                        </tr>
                    `;
                });
            }

            html += `
                            </tbody>
                        </table>
                    </div>
                </div>
            `;
            return html;
        }

        // --- SUBTAB: KASIR POS TRANSAKSI (OPTIMASI MOBILE & DESKTOP) ---
        function renderKasirPosTab(stockList) {
            const search = (window.mkKasirSearchPos || '').toLowerCase();
            const cat = window.mkKasirCategoryPos || 'Semua';
            const categories = ['Semua', ...new Set(stockList.map(s => s.kategori || 'Umum'))];

            const filtered = stockList.filter(s => {
                const matchesSearch = (s.nama || '').toLowerCase().includes(search) || (s.barcode || '').toLowerCase().includes(search);
                const matchesCat = (cat === 'Semua') || ((s.kategori || 'Umum') === cat);
                return matchesSearch && matchesCat;
            });

            // Calculate POS Subtotal, Tax, and Grand Total
            const cart = window.mkPosCart || [];
            const subtotal = cart.reduce((acc, it) => acc + (Number(it.harga) * Number(it.qty)), 0);
            const discount = Number(window.mkKasirDiscount) || 0;
            const taxedSubtotal = Math.max(0, subtotal - discount);
            const taxAmount = window.mkKasirTaxEnabled ? (taxedSubtotal * window.mkKasirTaxRate) : 0;
            const grandTotal = Math.round(taxedSubtotal + taxAmount);

            const cash = Number(window.mkKasirCashInput) || 0;
            const change = Math.max(0, cash - grandTotal);
            const isCashEnough = cash >= grandTotal && grandTotal > 0;

            let html = `
                <div class="row g-3 g-lg-4">
                    <!-- Left: Produk & Barcode Scanner Panel -->
                    <div class="col-12 col-lg-7">
                        <div class="card card-custom kasir-filter-card p-3 p-sm-4 h-100 shadow-sm">
                            <div class="d-flex flex-wrap justify-content-between align-items-center gap-2 mb-3">
                                <h6 class="fw-bold mb-0"><i class="bi bi-grid-3x3-gap text-primary me-2"></i> Pilih Produk Kasir</h6>
                                <div class="d-flex gap-2">
                                    <button class="btn btn-sm btn-outline-primary" onclick="openCameraScannerModal(null, handlePosBarcodeEnter, 'Scan Barcode Produk Kasir')">
                                        <i class="bi bi-camera me-1"></i> Scan Kamera
                                    </button>
                                </div>
                            </div>

                            <!-- Barcode Fast Input & Category Search Filter -->
                            <div class="row g-2 mb-3">
                                <div class="col-12 col-sm-7">
                                    <div class="input-group">
                                        <span class="input-group-text bg-transparent"><i class="bi bi-upc-scan"></i></span>
                                        <input type="text" id="kasir-pos-barcode-input" class="form-control" placeholder="Scan barcode / ketik lalu Enter..." onkeydown="if(event.key==='Enter') handlePosBarcodeEnter(this.value);">
                                        <button class="btn btn-primary" type="button" title="Scan Barcode Kamera (100% Offline)" onclick="openCameraScannerModal(null, handlePosBarcodeEnter, 'Scan Barcode Produk Kasir')"><i class="bi bi-camera"></i></button>
                                    </div>
                                </div>
                                <div class="col-12 col-sm-5">
                                    <select class="form-select" onchange="window.mkKasirCategoryPos = this.value; renderKasirUi();">
            `;
            categories.forEach(c => {
                const sel = (c === cat) ? 'selected' : '';
                html += `<option value="${dol}{escapeHtml(c)}" ${dol}{sel}>${dol}{escapeHtml(c)}</option>`;
            });
            html += `
                                    </select>
                                </div>
                            </div>

                            <!-- Product Cards Grid (Responsif Mobile 2 Kolom, Tablet/Desktop 3-4 Kolom) -->
                            <div class="row g-2 g-sm-3 overflow-auto" style="max-height: 520px; -webkit-overflow-scrolling: touch;">
            `;

            if (filtered.length === 0) {
                html += `<div class="col-12 text-center py-5 text-muted"><i class="bi bi-search fs-1 d-block mb-2 opacity-50"></i>Produk tidak ditemukan.</div>`;
            } else {
                filtered.forEach(s => {
                    const isOutOfStock = s.stok <= 0;
                    const cardStyle = isOutOfStock ? 'opacity: 0.6; pointer-events: none;' : 'cursor: pointer;';
                    html += `
                        <div class="col-6 col-sm-4 col-md-4 col-xl-3">
                            <div class="card kasir-product-card h-100 p-2 p-sm-3 text-center transition" style="${dol}{cardStyle}" onclick="addToPosCart('${dol}{s.id}')">
                                <div class="mb-2 mx-auto rounded-3 overflow-hidden d-flex align-items-center justify-content-center" style="width: 100%; height: 80px; background: rgba(0,0,0,0.04);">
                                    ${dol}{s.foto ? `<img src="${dol}{s.foto}" alt="${dol}{escapeHtml(s.nama)}" style="width: 100%; height: 100%; object-fit: cover;">` : `<i class="bi bi-box-seam text-secondary opacity-50" style="font-size: 32px;"></i>`}
                                </div>
                                <div class="fw-bold text-truncate small fs-sm-6" title="${dol}{escapeHtml(s.nama)}">${dol}{escapeHtml(s.nama)}</div>
                                <small class="text-muted d-block text-truncate" style="font-size: 0.75rem;">${dol}{escapeHtml(s.kategori || 'Umum')}</small>
                                <div class="text-success fw-bold mt-1 mt-sm-2 small fs-sm-6">${dol}{mkFormatRupiah(s.harga)}</div>
                                <div class="mt-1 mt-sm-2">
                                    ${dol}{isOutOfStock ? '<span class="badge bg-danger">Habis</span>' : '<span class="badge bg-light text-dark border">Stok: ' + s.stok + '</span>'}
                                </div>
                            </div>
                        </div>
                    `;
                });
            }

            html += `
                            </div>
                        </div>
                    </div>

                    <!-- Right: Keranjang Belanja & Kalkulator Pembayaran -->
                    <div id="pos-cart-panel" class="col-12 col-lg-5">
                        <div class="card card-custom kasir-cart-panel p-3 p-sm-4 shadow-sm">
                            <div class="d-flex justify-content-between align-items-center mb-3">
                                <h6 class="fw-bold mb-0"><i class="bi bi-cart-check text-success me-2"></i> Keranjang Kasir <span class="badge bg-success bg-opacity-10 text-success">${dol}{cart.length}</span></h6>
                                ${dol}{cart.length > 0 ? '<button class="btn btn-sm btn-outline-danger" onclick="clearPosCart()"><i class="bi bi-trash me-1"></i> Kosongkan</button>' : ''}
                            </div>

                            <!-- Cart Items List (Mobile Scrollable) -->
                            <div class="list-group list-group-flush mb-3 overflow-auto border-top border-bottom py-2" style="max-height: 240px; -webkit-overflow-scrolling: touch;">
            `;

            if (cart.length === 0) {
                html += `<div class="text-center py-4 text-muted small"><i class="bi bi-cart-x fs-2 d-block mb-1 opacity-50"></i>Keranjang belanja masih kosong</div>`;
            } else {
                cart.forEach((item, idx) => {
                    const itemTotal = Number(item.harga) * Number(item.qty);
                    html += `
                        <div class="list-group-item d-flex justify-content-between align-items-center px-0 py-2 border-0 gap-2">
                            <div class="d-flex align-items-center gap-2 me-auto" style="max-width: 170px;">
                                <div class="rounded-2 overflow-hidden border d-flex align-items-center justify-content-center flex-shrink-0" style="width: 34px; height: 34px; background: rgba(0,0,0,0.04); ${dol}{item.foto ? 'cursor: pointer;' : ''}" ${dol}{item.foto ? `onclick="showPhotoZoomModal('${dol}{escapeHtml(item.namaBarang)}', '${dol}{item.foto}')" title="Klik untuk perbesar foto"` : ''}>
                                    ${dol}{item.foto ? `<img src="${dol}{item.foto}" alt="${dol}{escapeHtml(item.namaBarang)}" style="width: 100%; height: 100%; object-fit: cover;">` : `<i class="bi bi-box-seam text-secondary opacity-50" style="font-size: 14px;"></i>`}
                                </div>
                                <div class="text-truncate">
                                    <div class="fw-semibold text-truncate small">${dol}{escapeHtml(item.namaBarang)}</div>
                                    <small class="text-muted" style="font-size: 0.75rem;">${dol}{mkFormatRupiah(item.harga)}</small>
                                </div>
                            </div>
                            <div class="d-flex align-items-center gap-1 gap-sm-2 flex-shrink-0">
                                <div class="btn-group btn-group-sm">
                                    <button class="btn btn-outline-secondary px-2" onclick="updatePosCartQty(${dol}{idx}, -1)">-</button>
                                    <span class="btn btn-outline-secondary disabled fw-bold px-2 text-body" style="min-width: 28px;">${dol}{item.qty}</span>
                                    <button class="btn btn-outline-secondary px-2" onclick="updatePosCartQty(${dol}{idx}, 1)">+</button>
                                </div>
                                <span class="fw-bold text-success text-nowrap small ms-1" style="min-width: 65px; text-align: right;">${dol}{mkFormatRupiah(itemTotal)}</span>
                                <button class="btn btn-sm text-danger border-0 p-1" onclick="removePosCartItem(${dol}{idx})"><i class="bi bi-x-circle"></i></button>
                            </div>
                        </div>
                    `;
                });
            }

            html += `
                            </div>

                            <!-- Calculations & Taxes -->
                            <div class="mb-3">
                                <div class="d-flex justify-content-between small text-muted mb-1">
                                    <span>Subtotal:</span>
                                    <span class="fw-semibold text-body">${dol}{mkFormatRupiah(subtotal)}</span>
                                </div>
                                <div class="d-flex justify-content-between align-items-center small text-muted mb-1">
                                    <div class="form-check form-switch mb-0">
                                        <input class="form-check-input" type="checkbox" id="taxSwitch" ${dol}{window.mkKasirTaxEnabled ? 'checked' : ''} onchange="window.mkKasirTaxEnabled = this.checked; renderKasirUi();">
                                        <label class="form-check-label" for="taxSwitch">PPN 11%</label>
                                    </div>
                                    <span class="fw-semibold text-body">${dol}{mkFormatRupiah(taxAmount)}</span>
                                </div>
                                <div class="d-flex justify-content-between fw-bold fs-5 fs-sm-4 border-top pt-2 mt-2 text-primary" style="border-top: 2px dashed rgba(54, 116, 181, 0.3) !important;">
                                    <span>Total Tagihan:</span>
                                    <span>${dol}{mkFormatRupiah(grandTotal)}</span>
                                </div>
                            </div>

                            <!-- Cash Payment Input & Quick Money Chips -->
                            <div class="mb-3">
                                <label class="form-label small fw-bold">Nominal Uang Tunai (Rp):</label>
                                <div class="input-group mb-2">
                                    <span class="input-group-text bg-transparent">Rp</span>
                                    <input type="number" class="form-control fw-bold fs-5 text-success" placeholder="0" value="${dol}{window.mkKasirCashInput}" oninput="window.mkKasirCashInput = this.value; renderKasirUi();">
                                </div>

                                <!-- Quick Chips (Grid Rapi Mobile Mode Terang Berwarna) -->
                                <div class="row g-1 mb-2">
                                    <div class="col-4 col-sm"><button class="btn btn-sm kasir-quick-cash-chip w-100 py-1" onclick="window.mkKasirCashInput = ${dol}{grandTotal}; renderKasirUi();">Pas</button></div>
                                    <div class="col-4 col-sm"><button class="btn btn-sm kasir-quick-cash-chip w-100 py-1" onclick="window.mkKasirCashInput = 10000; renderKasirUi();">10rb</button></div>
                                    <div class="col-4 col-sm"><button class="btn btn-sm kasir-quick-cash-chip w-100 py-1" onclick="window.mkKasirCashInput = 20000; renderKasirUi();">20rb</button></div>
                                    <div class="col-6 col-sm"><button class="btn btn-sm kasir-quick-cash-chip w-100 py-1" onclick="window.mkKasirCashInput = 50000; renderKasirUi();">50rb</button></div>
                                    <div class="col-6 col-sm"><button class="btn btn-sm kasir-quick-cash-chip w-100 py-1" onclick="window.mkKasirCashInput = 100000; renderKasirUi();">100rb</button></div>
                                </div>

                                <div class="card kasir-change-box p-2">
                                    <div class="d-flex justify-content-between align-items-center">
                                        <span class="small fw-semibold text-muted">Kembalian:</span>
                                        <span class="fw-bold fs-5 ${dol}{change > 0 ? 'text-success' : 'text-body'}">${dol}{mkFormatRupiah(change)}</span>
                                    </div>
                                </div>
                            </div>

                            <button class="btn btn-success btn-lg w-100 fw-bold shadow-sm py-3" ${dol}{!isCashEnough ? 'disabled' : ''} onclick="processPosCheckout(${dol}{grandTotal}, ${dol}{cash}, ${dol}{window.mkKasirTaxEnabled})">
                                <i class="bi bi-printer me-2"></i> Bayar & Cetak Struk
                            </button>
                        </div>
                    </div>
                </div>

                <!-- Sticky Bottom Bar untuk Layar Mobile ketika Keranjang Berisi Produk -->
                ${dol}{cart.length > 0 ? ('<div class=\"d-block d-lg-none position-sticky bottom-0 start-0 end-0 p-2 z-3 mt-3\" style=\"background: rgba(255,255,255,0.96); backdrop-filter: blur(8px); border-top: 2px solid rgba(16, 185, 129, 0.35); box-shadow: 0 -4px 16px rgba(0,0,0,0.08); border-radius: 16px 16px 0 0;\"><div class=\"d-flex justify-content-between align-items-center gap-2\"><div><span class=\"badge bg-primary rounded-pill me-1\">' + cart.length + ' Item</span><span class=\"fw-bold text-success fs-6\">' + mkFormatRupiah(grandTotal) + '</span></div><button class=\"btn btn-success btn-sm fw-bold px-3 py-2\" onclick=\"document.getElementById(\'pos-cart-panel\').scrollIntoView({behavior: \'smooth\'})\"><i class=\"bi bi-arrow-down-circle me-1\"></i> Ke Kasir / Bayar</button></div></div>') : ''}
            `;
            return html;
        }

        // --- SUBTAB: RIWAYAT TRANSAKSI KASIR (RESPONSIF MOBILE & DESKTOP) ---
        function renderKasirRiwayatTab(cashierList) {
            const search = (window.mkKasirSearchHistory || '').toLowerCase();
            const dateFilter = window.mkKasirDateHistory || '';

            const filtered = cashierList.filter(tx => {
                const matchesDate = !dateFilter || (tx.waktu || '').startsWith(dateFilter);
                const matchesSearch = !search || (tx.items || []).some(it => (it.namaBarang || '').toLowerCase().includes(search)) || (tx.id || '').toLowerCase().includes(search);
                return matchesDate && matchesSearch;
            });

            let html = `
                <div class="card card-custom kasir-filter-card p-3 p-sm-4 shadow-sm mb-4">
                    <!-- Responsive Filter Bar -->
                    <div class="row g-2 align-items-center mb-3">
                        <div class="col-12 col-sm-7 col-md-8">
                            <div class="input-group">
                                <span class="input-group-text bg-transparent"><i class="bi bi-search"></i></span>
                                <input type="text" class="form-control" placeholder="Cari nama barang atau ID transaksi..." value="${dol}{escapeHtml(window.mkKasirSearchHistory)}" oninput="window.mkKasirSearchHistory = this.value; renderKasirUi();">
                                ${dol}{window.mkKasirSearchHistory ? '<button class="btn btn-outline-secondary" onclick="window.mkKasirSearchHistory=\'\'; renderKasirUi();"><i class="bi bi-x"></i></button>' : ''}
                            </div>
                        </div>
                        <div class="col-12 col-sm-5 col-md-4">
                            <div class="input-group">
                                <input type="date" class="form-control" value="${dol}{escapeHtml(window.mkKasirDateHistory)}" onchange="window.mkKasirDateHistory = this.value; renderKasirUi();">
                                ${dol}{window.mkKasirDateHistory ? '<button class="btn btn-outline-secondary" onclick="window.mkKasirDateHistory=\'\'; renderKasirUi();">Reset</button>' : ''}
                            </div>
                        </div>
                    </div>

                    <!-- Mobile Card View (Tampilan Khusus Layar Smartphone) -->
                    <div class="d-block d-md-none">
            `;

            if (filtered.length === 0) {
                html += `<div class="text-center py-5 text-muted"><i class="bi bi-receipt-cutoff fs-1 d-block mb-2 opacity-50"></i>Belum ada riwayat transaksi.</div>`;
            } else {
                html += `<div class="d-flex flex-column gap-2">`;
                filtered.forEach(tx => {
                    const total = Number(tx.totalHarga) || 0;
                    const tunai = Number(tx.tunai) || 0;
                    const change = Math.max(0, tunai - total);
                    const itemsSummary = (tx.items || []).map(i => escapeHtml(i.namaBarang) + ' (' + i.qty + 'x)').join(', ');

                    html += `
                        <div class="card kasir-mobile-card p-3 rounded-3">
                            <div class="d-flex justify-content-between align-items-start gap-2 mb-2">
                                <div>
                                    <div class="fw-bold small">${dol}{mkFormatDateIndo(tx.waktu)}</div>
                                    <code class="text-muted small">ID: #${dol}{escapeHtml((tx.id || '').substring(0, 8))}</code>
                                </div>
                                <div class="text-end">
                                    <div class="fw-bold fs-6 text-success">${dol}{mkFormatRupiah(total)}</div>
                                    <small class="text-muted">Tunai: ${dol}{mkFormatRupiah(tunai)}</small>
                                </div>
                            </div>
                            <div class="small text-muted mb-2 bg-light p-2 rounded">
                                <i class="bi bi-bag-check me-1"></i>${dol}{itemsSummary}
                            </div>
                            <div class="d-flex justify-content-between align-items-center border-top pt-2">
                                <span class="small text-muted">Kembali: <strong>${dol}{mkFormatRupiah(change)}</strong></span>
                                <div class="d-flex gap-1">
                                    <button class="btn btn-sm btn-outline-primary py-1 px-2" onclick="showReceiptModal('${dol}{tx.id}')"><i class="bi bi-printer me-1"></i> Struk</button>
                                    <button class="btn btn-sm btn-outline-danger py-1 px-2" onclick="deleteKasirTransaction('${dol}{tx.id}')"><i class="bi bi-trash"></i></button>
                                </div>
                            </div>
                        </div>
                    `;
                });
                html += `</div>`;
            }

            html += `
                    </div>

                    <!-- Desktop Table View (Tampilan Layar Komputer / Tablet Lebar) -->
                    <div class="d-none d-md-block table-responsive">
                        <table class="table table-hover align-middle mb-0">
                            <thead class="table-light">
                                <tr>
                                    <th>Waktu Transaksi</th>
                                    <th>Rincian Barang</th>
                                    <th>Total Tagihan</th>
                                    <th>Tunai / Bayar</th>
                                    <th>Kembalian</th>
                                    <th class="text-end">Aksi</th>
                                </tr>
                            </thead>
                            <tbody>
            `;

            if (filtered.length === 0) {
                html += `<tr><td colspan="6" class="text-center py-5 text-muted"><i class="bi bi-receipt-cutoff fs-1 d-block mb-2 opacity-50"></i>Belum ada riwayat transaksi.</td></tr>`;
            } else {
                filtered.forEach(tx => {
                    const total = Number(tx.totalHarga) || 0;
                    const tunai = Number(tx.tunai) || 0;
                    const change = Math.max(0, tunai - total);
                    const itemsSummary = (tx.items || []).map(i => escapeHtml(i.namaBarang) + ' (' + i.qty + 'x)').join(', ');

                    html += `
                        <tr>
                            <td>
                                <div class="fw-bold">${dol}{mkFormatDateIndo(tx.waktu)}</div>
                                <code class="text-muted small">${dol}{escapeHtml((tx.id || '').substring(0, 8))}</code>
                            </td>
                            <td>
                                <div class="text-truncate" style="max-width: 250px;" title="${dol}{itemsSummary}">${dol}{itemsSummary}</div>
                                <small class="text-muted">${dol}{(tx.items || []).length} jenis barang</small>
                            </td>
                            <td class="fw-bold text-success">${dol}{mkFormatRupiah(total)}</td>
                            <td>${dol}{mkFormatRupiah(tunai)}</td>
                            <td>${dol}{mkFormatRupiah(change)}</td>
                            <td class="text-end text-nowrap">
                                <button class="btn btn-sm btn-outline-primary py-1 px-2 me-1" onclick="showReceiptModal('${dol}{tx.id}')" title="Lihat & Cetak Struk"><i class="bi bi-printer me-1"></i> Struk</button>
                                <button class="btn btn-sm btn-outline-danger py-1 px-2" onclick="deleteKasirTransaction('${dol}{tx.id}')" title="Hapus Transaksi"><i class="bi bi-trash"></i></button>
                            </td>
                        </tr>
                    `;
                });
            }

            html += `
                            </tbody>
                        </table>
                    </div>
                </div>
            `;
            return html;
        }

        // --- POS CART OPERATIONS ---
        function addToPosCart(stockId) {
            const product = (window.mkKasirData.stock || []).find(s => s.id === stockId);
            if (!product) return;
            if (product.stok <= 0) {
                alert('Stok barang sudah habis!');
                return;
            }

            const existing = window.mkPosCart.find(i => i.id === stockId);
            if (existing) {
                if (existing.qty + 1 > product.stok) {
                    alert('Jumlah melebihi sisa stok yang tersedia (' + product.stok + ')!');
                    return;
                }
                existing.qty += 1;
            } else {
                window.mkPosCart.push({
                    id: product.id,
                    namaBarang: product.nama,
                    kategori: product.kategori || 'Umum',
                    harga: product.harga,
                    foto: product.foto || '',
                    qty: 1
                });
            }
            renderKasirUi();
        }

        function handlePosBarcodeEnter(barcode) {
            if (!barcode || !barcode.trim()) return;
            const code = barcode.trim();
            const product = (window.mkKasirData.stock || []).find(s => (s.barcode || '').toLowerCase() === code.toLowerCase());
            if (product) {
                addToPosCart(product.id);
                const input = document.getElementById('kasir-pos-barcode-input');
                if (input) {
                    input.value = '';
                    input.focus();
                }
            } else {
                alert('Produk dengan barcode "' + code + '" tidak ditemukan!');
            }
        }

        function updatePosCartQty(index, delta) {
            const item = window.mkPosCart[index];
            if (!item) return;
            const product = (window.mkKasirData.stock || []).find(s => s.id === item.id);
            const maxStock = product ? product.stok : 9999;

            const newQty = item.qty + delta;
            if (newQty <= 0) {
                window.mkPosCart.splice(index, 1);
            } else if (newQty > maxStock) {
                alert('Stok maksimal tercapai (' + maxStock + ')!');
            } else {
                item.qty = newQty;
            }
            renderKasirUi();
        }

        function removePosCartItem(index) {
            window.mkPosCart.splice(index, 1);
            renderKasirUi();
        }

        function clearPosCart() {
            window.mkPosCart = [];
            window.mkKasirCashInput = '';
            renderKasirUi();
        }

        async function processPosCheckout(total, tunai, taxEnabled) {
            if (window.mkPosCart.length === 0) return;
            try {
                const res = await fetch('/api/kasir', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({
                        action: 'checkout',
                        items: window.mkPosCart,
                        tunai: tunai,
                        taxEnabled: taxEnabled,
                        taxRate: 0.11
                    })
                });
                const resJson = await res.json();
                if (resJson.success && resJson.transaction) {
                    const tx = resJson.transaction;
                    window.mkPosCart = [];
                    window.mkKasirCashInput = '';
                    await fetchKasirData();
                    renderKasirUi();
                    showReceiptModal(tx.id || (window.mkKasirData.cashier[0] || {}).id);
                } else {
                    alert('Gagal memproses transaksi kasir!');
                }
            } catch (e) {
                alert('Terjadi kesalahan: ' + e.message);
            }
        }

        // --- STOCK CRUD OPERATIONS ---
        async function quickAddStock(stockId, addQty) {
            try {
                const product = (window.mkKasirData.stock || []).find(s => s.id === stockId);
                if (!product) return;
                const newStock = product.stok + addQty;
                await fetch('/api/kasir', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({
                        id: product.id,
                        nama: product.nama,
                        kategori: product.kategori,
                        keterangan: product.keterangan,
                        harga: product.harga,
                        stok: newStock,
                        barcode: product.barcode
                    })
                });
                await fetchKasirData();
                renderKasirUi();
            } catch (e) {
                alert('Gagal menambah stok: ' + e.message);
            }
        }

        async function deleteStockItem(stockId) {
            if (!confirm('Apakah Anda yakin ingin menghapus produk ini dari stok?')) return;
            try {
                await fetch('/api/kasir?id=' + encodeURIComponent(stockId), { method: 'DELETE' });
                await fetchKasirData();
                renderKasirUi();
            } catch (e) {
                alert('Gagal menghapus produk: ' + e.message);
            }
        }

        async function deleteKasirTransaction(txId) {
            if (!confirm('Apakah Anda yakin ingin menghapus riwayat transaksi ini?')) return;
            try {
                await fetch('/api/kasir?id=' + encodeURIComponent(txId) + '&type=transaction', { method: 'DELETE' });
                await fetchKasirData();
                renderKasirUi();
            } catch (e) {
                alert('Gagal menghapus transaksi: ' + e.message);
            }
        }

        // --- KAMERA & FOTO PRODUK KASIR (100% OFFLINE) ---
        function compressImageFileToDataUrl(file, maxWidth, maxHeight, quality, callback) {
            const reader = new FileReader();
            reader.onload = function(e) {
                const img = new Image();
                img.onload = function() {
                    let w = img.width;
                    let h = img.height;
                    if (w > maxWidth || h > maxHeight) {
                        const ratio = Math.min(maxWidth / w, maxHeight / h);
                        w = Math.round(w * ratio);
                        h = Math.round(h * ratio);
                    }
                    const canvas = document.createElement('canvas');
                    canvas.width = w;
                    canvas.height = h;
                    const ctx = canvas.getContext('2d');
                    ctx.drawImage(img, 0, 0, w, h);
                    const dataUrl = canvas.toDataURL('image/jpeg', quality || 0.82);
                    callback(dataUrl);
                };
                img.src = e.target.result;
            };
            reader.readAsDataURL(file);
        }

        function showPhotoZoomModal(title, photoUrl) {
            if (!photoUrl) return;
            const modalId = 'modalPhotoZoomViewer';
            let modalEl = document.getElementById(modalId);
            if (modalEl) modalEl.remove();

            const html = `
                <div class="modal fade" id="${dol}{modalId}" tabindex="-1">
                    <div class="modal-dialog modal-dialog-centered">
                        <div class="modal-content card-custom">
                            <div class="modal-header border-bottom py-2">
                                <h6 class="modal-title fw-bold text-truncate"><i class="bi bi-image text-primary me-2"></i> ${dol}{escapeHtml(title || 'Foto Produk')}</h6>
                                <button type="button" class="btn-close" data-bs-dismiss="modal"></button>
                            </div>
                            <div class="modal-body text-center p-3">
                                <div class="rounded-3 overflow-hidden border shadow-sm mx-auto" style="max-height: 70vh; background: #000; display: inline-block;">
                                    <img src="${dol}{photoUrl}" alt="${dol}{escapeHtml(title || 'Foto')}" style="max-width: 100%; max-height: 65vh; object-fit: contain;">
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            `;
            document.body.insertAdjacentHTML('beforeend', html);
            const modal = new bootstrap.Modal(document.getElementById(modalId));
            modal.show();
        }

        let kasirActiveCameraStream = null;

        function openKasirCameraCapture(onPhotoCaptured, title) {
            window.activeKasirCameraCallback = onPhotoCaptured;
            const modalId = 'modalKasirLiveCamera';
            let modalEl = document.getElementById(modalId);
            if (modalEl) modalEl.remove();

            const html = `
                <div class="modal fade" id="${dol}{modalId}" tabindex="-1" data-bs-backdrop="static">
                    <div class="modal-dialog modal-dialog-centered" style="max-width: 440px;">
                        <div class="modal-content card-custom">
                            <div class="modal-header border-bottom">
                                <h6 class="modal-title fw-bold">
                                    <i class="bi bi-camera-fill text-primary me-2"></i> ${dol}{title || 'Ambil Foto Produk'}
                                </h6>
                                <button type="button" class="btn-close" onclick="closeKasirCameraCaptureModal()"></button>
                            </div>
                            <div class="modal-body text-center p-3">
                                <div id="kasirCameraVideoContainer" class="position-relative rounded-3 overflow-hidden mb-3 bg-dark d-flex align-items-center justify-content-center" style="min-height: 260px; max-height: 320px;">
                                    <video id="kasirLiveCamVideo" autoplay playsinline muted style="width: 100%; height: 100%; max-height: 320px; object-fit: cover;"></video>
                                    <div id="kasirCameraLoadingIndicator" class="position-absolute text-white">
                                        <div class="spinner-border spinner-border-sm text-primary mb-2" role="status"></div>
                                        <div class="small">Mengakses kamera...</div>
                                    </div>
                                    <div id="kasirCameraErrorFallback" class="p-3 text-white d-none">
                                        <i class="bi bi-camera-video-off fs-1 text-danger d-block mb-2"></i>
                                        <div class="small mb-3">Kamera browser dibatasi atau belum diizinkan. Gunakan tombol kamera perangkat di bawah:</div>
                                        <button type="button" class="btn btn-primary rounded-pill px-3 py-2 fw-bold" onclick="document.getElementById('nativeCameraCaptureInput').click()">
                                            <i class="bi bi-camera me-1"></i> Buka Kamera Perangkat
                                        </button>
                                    </div>
                                </div>

                                <input type="file" id="nativeCameraCaptureInput" accept="image/*" capture="environment" class="d-none" onchange="handleNativeCameraFile(this)">
                                <input type="file" id="nativeGalleryPickInput" accept="image/*" class="d-none" onchange="handleNativeCameraFile(this)">

                                <div class="d-flex justify-content-center gap-2 flex-wrap">
                                    <button type="button" id="btnSnapLiveCam" class="btn btn-primary rounded-pill px-4 py-2 fw-bold shadow-sm" onclick="snapKasirLiveCam()">
                                        <i class="bi bi-camera-fill me-1"></i> Ambil Foto
                                    </button>
                                    <button type="button" class="btn btn-outline-secondary rounded-pill px-3 py-2" onclick="document.getElementById('nativeCameraCaptureInput').click()" title="Kamera Perangkat">
                                        <i class="bi bi-phone me-1"></i> Kamera HP
                                    </button>
                                    <button type="button" class="btn btn-outline-secondary rounded-pill px-3 py-2" onclick="document.getElementById('nativeGalleryPickInput').click()" title="Galeri">
                                        <i class="bi bi-image me-1"></i> Galeri
                                    </button>
                                </div>
                            </div>
                            <div class="modal-footer border-top pt-2">
                                <button type="button" class="btn btn-secondary w-100 rounded-pill" onclick="closeKasirCameraCaptureModal()">Batal</button>
                            </div>
                        </div>
                    </div>
                </div>
            `;
            document.body.insertAdjacentHTML('beforeend', html);
            const modal = new bootstrap.Modal(document.getElementById(modalId));
            modal.show();

            const video = document.getElementById('kasirLiveCamVideo');
            const loading = document.getElementById('kasirCameraLoadingIndicator');
            const fallback = document.getElementById('kasirCameraErrorFallback');
            const snapBtn = document.getElementById('btnSnapLiveCam');

            if (navigator.mediaDevices && navigator.mediaDevices.getUserMedia) {
                navigator.mediaDevices.getUserMedia({
                    video: { facingMode: 'environment', width: { ideal: 640 }, height: { ideal: 640 } },
                    audio: false
                }).then(stream => {
                    kasirActiveCameraStream = stream;
                    if (video) {
                        video.srcObject = stream;
                        video.play();
                    }
                    if (loading) loading.classList.add('d-none');
                }).catch(err => {
                    console.warn("getUserMedia fallback to input file:", err);
                    if (loading) loading.classList.add('d-none');
                    if (fallback) fallback.classList.remove('d-none');
                    if (snapBtn) snapBtn.classList.add('d-none');
                });
            } else {
                if (loading) loading.classList.add('d-none');
                if (fallback) fallback.classList.remove('d-none');
                if (snapBtn) snapBtn.classList.add('d-none');
            }
        }

        function closeKasirCameraCaptureModal() {
            if (kasirActiveCameraStream) {
                kasirActiveCameraStream.getTracks().forEach(t => t.stop());
                kasirActiveCameraStream = null;
            }
            const modalEl = document.getElementById('modalKasirLiveCamera');
            if (modalEl) {
                const modal = bootstrap.Modal.getInstance(modalEl);
                if (modal) modal.hide();
                modalEl.remove();
            }
        }

        function snapKasirLiveCam() {
            const video = document.getElementById('kasirLiveCamVideo');
            if (!video || !video.videoWidth) {
                return alert('Kamera belum siap, silakan tunggu sebentar atau gunakan Kamera HP!');
            }
            const canvas = document.createElement('canvas');
            const maxDim = 400;
            let w = video.videoWidth;
            let h = video.videoHeight;
            if (w > maxDim || h > maxDim) {
                const ratio = Math.min(maxDim / w, maxDim / h);
                w = Math.round(w * ratio);
                h = Math.round(h * ratio);
            }
            canvas.width = w;
            canvas.height = h;
            const ctx = canvas.getContext('2d');
            ctx.drawImage(video, 0, 0, w, h);
            const dataUrl = canvas.toDataURL('image/jpeg', 0.82);

            closeKasirCameraCaptureModal();
            if (typeof window.activeKasirCameraCallback === 'function') {
                window.activeKasirCameraCallback(dataUrl);
            }
        }

        function handleNativeCameraFile(input) {
            if (input.files && input.files[0]) {
                compressImageFileToDataUrl(input.files[0], 400, 400, 0.82, (dataUrl) => {
                    closeKasirCameraCaptureModal();
                    if (typeof window.activeKasirCameraCallback === 'function') {
                        window.activeKasirCameraCallback(dataUrl);
                    }
                });
            }
        }

        // --- MODALS (STOK, NAMA USAHA, RECEIPT, SCANNER) ---
        function openAddStockModal() {
            openStockFormModal(null);
        }

        function openEditStockModal(stockId) {
            const product = (window.mkKasirData.stock || []).find(s => s.id === stockId);
            if (product) openStockFormModal(product);
        }

        function setStockFormPhoto(dataUrl) {
            window.currentStockFormFoto = dataUrl || '';
            const box = document.getElementById('stockFormFotoPreviewBox');
            const delBtn = document.getElementById('btnHapusStockFormFoto');
            if (box) {
                if (dataUrl) {
                    box.innerHTML = `<img id="imgStockFormPreview" src="${dol}{dataUrl}" style="width: 100%; height: 100%; object-fit: cover;">`;
                    if (delBtn) delBtn.classList.remove('d-none');
                } else {
                    box.innerHTML = `<i id="iconStockFormPlaceholder" class="bi bi-camera fs-3 text-muted"></i>`;
                    if (delBtn) delBtn.classList.add('d-none');
                }
            }
        }

        function handleStockFormFileDirect(input) {
            if (input.files && input.files[0]) {
                compressImageFileToDataUrl(input.files[0], 400, 400, 0.82, (dataUrl) => {
                    setStockFormPhoto(dataUrl);
                });
            }
        }

        function openStockFormModal(item) {
            const isEdit = !!item;
            const title = isEdit ? 'Edit Produk Stok' : 'Tambah Produk Baru';
            const modalId = 'modalStockForm';

            let modalEl = document.getElementById(modalId);
            if (modalEl) modalEl.remove();

            window.currentStockFormFoto = item ? (item.foto || '') : '';
            const existingBarcode = item ? (item.barcode || '') : '';
            const genBarcode = existingBarcode || ('BRG' + Math.floor(100000 + Math.random() * 900000));

            const html = `
                <div class="modal fade" id="${dol}{modalId}" tabindex="-1">
                    <div class="modal-dialog modal-dialog-centered">
                        <div class="modal-content card-custom">
                            <div class="modal-header border-bottom">
                                <h5 class="modal-title fw-bold"><i class="bi bi-box-seam text-primary me-2"></i> ${dol}{title}</h5>
                                <button type="button" class="btn-close" data-bs-dismiss="modal"></button>
                            </div>
                            <div class="modal-body">
                                <form id="formStockInput" onsubmit="event.preventDefault(); saveStockForm('${dol}{item ? item.id : ''}');">
                                    <!-- Foto Produk Kamera / Gambar -->
                                    <div class="mb-3">
                                        <label class="form-label small fw-bold">Foto Produk (Kamera / Gambar)</label>
                                        <div class="d-flex align-items-center gap-3 p-2.5 rounded-3 border" style="background: var(--bg-hover);">
                                            <div id="stockFormFotoPreviewBox" class="rounded-3 overflow-hidden border d-flex align-items-center justify-content-center flex-shrink-0" style="width: 76px; height: 76px; background: rgba(0,0,0,0.05);">
                                                ${dol}{window.currentStockFormFoto ? `<img id="imgStockFormPreview" src="${dol}{window.currentStockFormFoto}" style="width: 100%; height: 100%; object-fit: cover;">` : `<i id="iconStockFormPlaceholder" class="bi bi-camera fs-3 text-muted"></i>`}
                                            </div>
                                            <div class="flex-grow-1">
                                                <div class="d-flex flex-wrap gap-2 mb-1">
                                                    <button type="button" class="btn btn-sm btn-primary rounded-pill px-3 fw-semibold shadow-sm" onclick="openKasirCameraCapture((d) => setStockFormPhoto(d), 'Ambil Foto: ${dol}{item ? escapeHtml(item.nama) : 'Produk Baru'}')">
                                                        <i class="bi bi-camera-fill me-1"></i> Ambil Foto Kamera
                                                    </button>
                                                    <button type="button" class="btn btn-sm btn-outline-secondary rounded-pill px-2.5" onclick="document.getElementById('inpStockFormFileDirect').click()">
                                                        <i class="bi bi-image me-1"></i> Galeri / File
                                                    </button>
                                                    <button type="button" id="btnHapusStockFormFoto" class="btn btn-sm btn-outline-danger rounded-pill px-2.5 ${dol}{window.currentStockFormFoto ? '' : 'd-none'}" onclick="setStockFormPhoto('')">
                                                        <i class="bi bi-trash"></i> Hapus
                                                    </button>
                                                </div>
                                                <input type="file" id="inpStockFormFileDirect" accept="image/*" class="d-none" onchange="handleStockFormFileDirect(this)">
                                                <small class="text-muted d-block" style="font-size: 11px;">Akses kamera web/HP 100% offline dengan kompresi otomatis.</small>
                                            </div>
                                        </div>
                                    </div>

                                    <div class="mb-3">
                                        <label class="form-label small fw-bold">Nama Produk / Barang <span class="text-danger">*</span></label>
                                        <input type="text" id="inpStockNama" class="form-control" required value="${dol}{item ? escapeHtml(item.nama) : ''}" placeholder="Contoh: Kopi Susu Aren">
                                    </div>
                                    <div class="row g-2 mb-3">
                                        <div class="col-6">
                                            <label class="form-label small fw-bold">Kategori</label>
                                            <input type="text" id="inpStockKategori" class="form-control" list="stockCatList" value="${dol}{item ? escapeHtml(item.kategori || 'Umum') : 'Makanan & Minuman'}" placeholder="Kategori">
                                            <datalist id="stockCatList">
                                                <option value="Makanan & Minuman">
                                                <option value="Sembako">
                                                <option value="Pakaian">
                                                <option value="Elektronik">
                                                <option value="Perlengkapan">
                                                <option value="Umum">
                                            </datalist>
                                        </div>
                                        <div class="col-6">
                                            <label class="form-label small fw-bold">Kode Barcode</label>
                                            <div class="input-group">
                                                <input type="text" id="inpStockBarcode" class="form-control" value="${dol}{escapeHtml(genBarcode)}" placeholder="Kode barcode">
                                                <button class="btn btn-outline-secondary" type="button" title="Acak Kode Barcode Baru" onclick="document.getElementById('inpStockBarcode').value='BRG'+Math.floor(100000+Math.random()*900000)"><i class="bi bi-arrow-repeat"></i></button>
                                                <button class="btn btn-primary" type="button" title="Scan Barcode Kamera (100% Offline)" onclick="openCameraScannerModalForInput('inpStockBarcode', null, 'Scan Barcode Produk Baru')"><i class="bi bi-camera"></i></button>
                                            </div>
                                        </div>
                                    </div>
                                    <div class="row g-2 mb-3">
                                        <div class="col-6">
                                            <label class="form-label small fw-bold">Harga Jual (Rp) <span class="text-danger">*</span></label>
                                            <input type="number" id="inpStockHarga" class="form-control" required value="${dol}{item ? item.harga : ''}" placeholder="0">
                                        </div>
                                        <div class="col-6">
                                            <label class="form-label small fw-bold">Jumlah Stok Awal <span class="text-danger">*</span></label>
                                            <input type="number" id="inpStockQty" class="form-control" required value="${dol}{item ? item.stok : '10'}" placeholder="0">
                                        </div>
                                    </div>
                                    <div class="mb-3">
                                        <label class="form-label small fw-bold">Keterangan Tambahan (Opsional)</label>
                                        <textarea id="inpStockKeterangan" class="form-control" rows="2" placeholder="Catatan supplier, varian rasa, dsb...">${dol}{item ? escapeHtml(item.keterangan || '') : ''}</textarea>
                                    </div>
                                    <div class="d-flex justify-content-end gap-2 pt-2 border-top">
                                        <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">Batal</button>
                                        <button type="submit" class="btn btn-primary fw-bold"><i class="bi bi-save me-1"></i> Simpan Produk</button>
                                    </div>
                                </form>
                            </div>
                        </div>
                    </div>
                </div>
            `;
            document.body.insertAdjacentHTML('beforeend', html);
            const modal = new bootstrap.Modal(document.getElementById(modalId));
            modal.show();
        }

        async function saveStockForm(existingId) {
            const nama = document.getElementById('inpStockNama').value.trim();
            const kategori = document.getElementById('inpStockKategori').value.trim() || 'Umum';
            const barcode = document.getElementById('inpStockBarcode').value.trim();
            const harga = parseFloat(document.getElementById('inpStockHarga').value) || 0;
            const stok = parseInt(document.getElementById('inpStockQty').value, 10) || 0;
            const keterangan = document.getElementById('inpStockKeterangan').value.trim();
            const foto = window.currentStockFormFoto || '';

            if (!nama) return alert('Nama produk wajib diisi!');

            try {
                await fetch('/api/kasir', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({
                        id: existingId || '',
                        nama: nama,
                        kategori: kategori,
                        barcode: barcode,
                        harga: harga,
                        stok: stok,
                        keterangan: keterangan,
                        foto: foto
                    })
                });
                const modalEl = document.getElementById('modalStockForm');
                if (modalEl) {
                    const modal = bootstrap.Modal.getInstance(modalEl);
                    if (modal) modal.hide();
                }
                await fetchKasirData();
                renderKasirUi();
            } catch (e) {
                alert('Gagal menyimpan produk: ' + e.message);
            }
        }

        function openEditNamaUsahaModal() {
            const modalId = 'modalEditNamaUsaha';
            let modalEl = document.getElementById(modalId);
            if (modalEl) modalEl.remove();

            const curNama = window.mkKasirData.namaUsaha || 'Usaha Saya';
            const html = `
                <div class="modal fade" id="${dol}{modalId}" tabindex="-1">
                    <div class="modal-dialog modal-dialog-centered modal-sm">
                        <div class="modal-content card-custom">
                            <div class="modal-header border-bottom">
                                <h6 class="modal-title fw-bold"><i class="bi bi-shop me-2 text-primary"></i> Atur Nama Usaha</h6>
                                <button type="button" class="btn-close" data-bs-dismiss="modal"></button>
                            </div>
                            <div class="modal-body">
                                <div class="mb-3">
                                    <label class="form-label small fw-bold">Nama Toko / Usaha:</label>
                                    <input type="text" id="inpNamaUsaha" class="form-control" value="${dol}{escapeHtml(curNama)}">
                                </div>
                                <button class="btn btn-primary w-100 fw-bold" onclick="saveNamaUsaha()"><i class="bi bi-check2 me-1"></i> Simpan</button>
                            </div>
                        </div>
                    </div>
                </div>
            `;
            document.body.insertAdjacentHTML('beforeend', html);
            const modal = new bootstrap.Modal(document.getElementById(modalId));
            modal.show();
        }

        async function saveNamaUsaha() {
            const nama = document.getElementById('inpNamaUsaha').value.trim();
            if (!nama) return;
            try {
                await fetch('/api/kasir', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ action: 'set_nama_usaha', namaUsaha: nama })
                });
                const modalEl = document.getElementById('modalEditNamaUsaha');
                if (modalEl) {
                    const modal = bootstrap.Modal.getInstance(modalEl);
                    if (modal) modal.hide();
                }
                await fetchKasirData();
                renderKasirUi();
            } catch (e) {
                alert('Gagal menyimpan nama usaha: ' + e.message);
            }
        }

        // --- STOK ULANG BARANG DENGAN AMBIL FOTO KAMERA & SCANNER (100% OFFLINE) ---
        function setRestockPhoto(dataUrl) {
            window.currentRestockFoto = dataUrl || '';
            const box = document.getElementById('restockProductFotoBox');
            if (box) {
                if (dataUrl) {
                    box.innerHTML = `<img src="${dol}{dataUrl}" style="width: 100%; height: 100%; object-fit: cover;">`;
                } else {
                    box.innerHTML = `<i class="bi bi-box-seam fs-2 text-muted"></i>`;
                }
            }
        }

        function handleRestockFileDirect(input) {
            if (input.files && input.files[0]) {
                compressImageFileToDataUrl(input.files[0], 400, 400, 0.82, (dataUrl) => {
                    setRestockPhoto(dataUrl);
                });
            }
        }

        function updateRestockSelectedProductPreview(product) {
            const previewContainer = document.getElementById('restockProductPreviewContainer');
            if (!previewContainer) return;

            if (product) {
                window.activeRestockProduct = product;
                window.currentRestockFoto = product.foto || '';

                previewContainer.classList.remove('d-none');
                document.getElementById('restockPreviewNama').innerText = product.nama || '-';
                document.getElementById('restockPreviewBarcode').innerText = product.barcode || '-';
                document.getElementById('restockPreviewKategori').innerText = product.kategori || 'Umum';
                document.getElementById('restockPreviewStok').innerText = (product.stok || 0) + ' unit';
                document.getElementById('restockPreviewHarga').innerText = mkFormatRupiah(product.harga || 0);

                const box = document.getElementById('restockProductFotoBox');
                if (box) {
                    if (product.foto) {
                        box.innerHTML = `<img src="${dol}{product.foto}" style="width: 100%; height: 100%; object-fit: cover;">`;
                    } else {
                        box.innerHTML = `<i class="bi bi-box-seam fs-2 text-muted"></i>`;
                    }
                }
            } else {
                window.activeRestockProduct = null;
                window.currentRestockFoto = '';
                previewContainer.classList.add('d-none');
            }
        }

        function onRestockBarcodeInput(val) {
            const query = (val || '').trim().toLowerCase();
            if (!query) {
                updateRestockSelectedProductPreview(null);
                return;
            }
            const product = (window.mkKasirData.stock || []).find(s => 
                (s.barcode || '').toLowerCase() === query || 
                (s.nama || '').toLowerCase() === query ||
                (s.id || '').toLowerCase() === query
            );
            if (product) {
                const sel = document.getElementById('selRestockProduct');
                if (sel) sel.value = product.id;
                updateRestockSelectedProductPreview(product);
            } else {
                updateRestockSelectedProductPreview(null);
            }
        }

        function onRestockSelectChange(prodId) {
            if (!prodId) {
                updateRestockSelectedProductPreview(null);
                return;
            }
            const product = (window.mkKasirData.stock || []).find(s => s.id === prodId);
            if (product) {
                const inp = document.getElementById('inpRestockBarcode');
                if (inp) inp.value = product.barcode || product.nama;
                updateRestockSelectedProductPreview(product);
            } else {
                updateRestockSelectedProductPreview(null);
            }
        }

        function adjustRestockQty(delta) {
            const inp = document.getElementById('inpRestockQty');
            if (inp) {
                const cur = parseInt(inp.value, 10) || 1;
                inp.value = Math.max(1, cur + delta);
            }
        }

        function openRestockModal() {
            const modalId = 'modalRestockBarcode';
            let modalEl = document.getElementById(modalId);
            if (modalEl) modalEl.remove();

            window.activeRestockProduct = null;
            window.currentRestockFoto = '';

            const stockList = window.mkKasirData.stock || [];
            const html = `
                <div class="modal fade" id="${dol}{modalId}" tabindex="-1">
                    <div class="modal-dialog modal-dialog-centered">
                        <div class="modal-content card-custom">
                            <div class="modal-header border-bottom">
                                <h5 class="modal-title fw-bold"><i class="bi bi-upc-scan text-primary me-2"></i> Stok Ulang Barang (Restock)</h5>
                                <button type="button" class="btn-close" data-bs-dismiss="modal"></button>
                            </div>
                            <div class="modal-body">
                                <div class="mb-3">
                                    <label class="form-label small fw-bold">Pilih atau Scan Barcode Produk:</label>
                                    <div class="input-group mb-2">
                                        <input type="text" id="inpRestockBarcode" class="form-control" placeholder="Scan barcode atau ketik barcode/nama..." autofocus oninput="onRestockBarcodeInput(this.value)">
                                        <button class="btn btn-primary" type="button" title="Scan Barcode Kamera (100% Offline)" onclick="openCameraScannerModalForInput('inpRestockBarcode', (code) => { document.getElementById('inpRestockBarcode').value = code; onRestockBarcodeInput(code); }, 'Scan Barcode Restock')"><i class="bi bi-camera fs-5"></i></button>
                                    </div>
                                    <select id="selRestockProduct" class="form-select" onchange="onRestockSelectChange(this.value)">
                                        <option value="">-- Atau Pilih Produk dari Daftar Stok --</option>
                                        ${dol}{stockList.map(s => `<option value="${dol}{s.id}">${dol}{escapeHtml(s.nama)} (Stok: ${dol}{s.stok} | ${dol}{escapeHtml(s.barcode || '-')})</option>`).join('')}
                                    </select>
                                </div>

                                <!-- Preview Produk yang Terpilih untuk Restock -->
                                <div id="restockProductPreviewContainer" class="p-3 rounded-3 mb-3 border d-none" style="background: var(--bg-hover);">
                                    <div class="d-flex align-items-center gap-3">
                                        <div id="restockProductFotoBox" class="rounded-3 overflow-hidden border d-flex align-items-center justify-content-center flex-shrink-0" style="width: 72px; height: 72px; background: rgba(0,0,0,0.06);">
                                            <i class="bi bi-box-seam fs-2 text-muted"></i>
                                        </div>
                                        <div class="flex-grow-1 min-w-0">
                                            <h6 id="restockPreviewNama" class="fw-bold mb-1 text-truncate">-</h6>
                                            <div class="small text-muted mb-1">
                                                Barcode: <code id="restockPreviewBarcode" class="text-primary fw-bold">-</code> |
                                                Kategori: <span id="restockPreviewKategori" class="badge bg-secondary bg-opacity-25 text-body">-</span>
                                            </div>
                                            <div class="small">
                                                Stok Saat Ini: <strong id="restockPreviewStok" class="text-danger">0 unit</strong> |
                                                Harga: <strong id="restockPreviewHarga" class="text-success">Rp 0</strong>
                                            </div>
                                        </div>
                                    </div>

                                    <!-- Ambil Foto Produk saat Stok Ulang -->
                                    <div class="mt-3 pt-2 border-top d-flex align-items-center justify-content-between flex-wrap gap-2">
                                        <div class="small fw-semibold text-secondary">
                                            <i class="bi bi-camera me-1"></i> Foto Produk Saat Stok Ulang:
                                        </div>
                                        <div class="d-flex gap-2">
                                            <button type="button" class="btn btn-sm btn-primary rounded-pill px-3 fw-semibold shadow-sm" onclick="openKasirCameraCapture((d) => setRestockPhoto(d), 'Foto Saat Stok Ulang')">
                                                <i class="bi bi-camera-fill me-1"></i> Ambil Foto Kamera
                                            </button>
                                            <button type="button" class="btn btn-sm btn-outline-secondary rounded-pill px-2.5" onclick="document.getElementById('inpRestockFileDirect').click()">
                                                <i class="bi bi-image me-1"></i> Galeri
                                            </button>
                                        </div>
                                        <input type="file" id="inpRestockFileDirect" accept="image/*" class="d-none" onchange="handleRestockFileDirect(this)">
                                    </div>
                                </div>

                                <div class="mb-3">
                                    <label class="form-label small fw-bold">Tambah Jumlah Stok Unit (+Qty):</label>
                                    <div class="input-group">
                                        <button class="btn btn-outline-secondary px-3" type="button" onclick="adjustRestockQty(-1)"><i class="bi bi-dash-lg"></i></button>
                                        <input type="number" id="inpRestockQty" class="form-control text-center fw-bold fs-5" value="10" min="1">
                                        <button class="btn btn-outline-secondary px-3" type="button" onclick="adjustRestockQty(1)"><i class="bi bi-plus-lg"></i></button>
                                    </div>
                                </div>
                                <div class="d-flex justify-content-end gap-2 pt-2 border-top">
                                    <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">Tutup</button>
                                    <button type="button" class="btn btn-success fw-bold" onclick="processRestockBarcode()"><i class="bi bi-plus-lg me-1"></i> Tambah Stok</button>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            `;
            document.body.insertAdjacentHTML('beforeend', html);
            const modal = new bootstrap.Modal(document.getElementById(modalId));
            modal.show();
        }

        async function processRestockBarcode() {
            const barcode = document.getElementById('inpRestockBarcode').value.trim();
            const qty = parseInt(document.getElementById('inpRestockQty').value, 10) || 1;
            if (!barcode && !window.activeRestockProduct) return alert('Silakan scan barcode atau pilih produk yang ingin di-stok ulang!');

            const product = window.activeRestockProduct || (window.mkKasirData.stock || []).find(s => 
                (s.barcode || '').toLowerCase() === barcode.toLowerCase() ||
                (s.nama || '').toLowerCase() === barcode.toLowerCase() ||
                s.id === barcode
            );
            if (!product) return alert('Produk tidak ditemukan di data stok!');

            try {
                const res = await fetch('/api/kasir', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({
                        action: 'restock',
                        id: product.id,
                        barcode: product.barcode || '',
                        qty: qty,
                        foto: window.currentRestockFoto || ''
                    })
                });

                if (res.ok) {
                    const modalEl = document.getElementById('modalRestockBarcode');
                    if (modalEl) {
                        const modal = bootstrap.Modal.getInstance(modalEl);
                        if (modal) modal.hide();
                    }
                    await fetchKasirData();
                    renderKasirUi();
                    alert('Berhasil menambahkan ' + qty + ' unit stok untuk ' + product.nama + '!');
                } else {
                    alert('Gagal menambah stok produk!');
                }
            } catch (e) {
                alert('Kesalahan saat restock: ' + e.message);
            }
        }

        // --- STRUK BELANJA THERMAL / RECEIPT MODAL ---
        function showReceiptModal(txId) {
            const tx = (window.mkKasirData.cashier || []).find(t => t.id === txId);
            if (!tx) return;

            const modalId = 'modalKasirReceipt';
            let modalEl = document.getElementById(modalId);
            if (modalEl) modalEl.remove();

            const namaUsaha = window.mkKasirData.namaUsaha || 'Usaha Saya';
            const total = Number(tx.totalHarga) || 0;
            const tunai = Number(tx.tunai) || 0;
            const change = Math.max(0, tunai - total);

            let receiptHtml = `
                <div class="modal fade" id="${dol}{modalId}" tabindex="-1">
                    <div class="modal-dialog modal-dialog-centered modal-sm">
                        <div class="modal-content card-custom">
                            <div class="modal-header border-bottom py-2">
                                <h6 class="modal-title fw-bold"><i class="bi bi-receipt me-1"></i> Struk Transaksi Kasir</h6>
                                <button type="button" class="btn-close" data-bs-dismiss="modal"></button>
                            </div>
                            <div class="modal-body p-3">
                                <div id="thermalReceiptContent" class="p-3 bg-white text-dark font-monospace border shadow-sm rounded" style="font-size: 12px; line-height: 1.4;">
                                    <div class="text-center mb-2 pb-2 border-bottom border-dark border-dashed">
                                        <div class="fw-bold fs-6 text-uppercase">${dol}{escapeHtml(namaUsaha)}</div>
                                        <div>POINT OF SALE SYSTEM</div>
                                        <div class="text-muted small">${dol}{mkFormatDateIndo(tx.waktu)}</div>
                                        <div class="text-muted small">No: #${dol}{escapeHtml((tx.id || '').substring(0, 8))}</div>
                                    </div>
                                    <div class="mb-2 pb-2 border-bottom border-dark border-dashed">
            `;

            (tx.items || []).forEach(it => {
                const itemTotal = Number(it.harga) * Number(it.qty);
                receiptHtml += `
                    <div class="d-flex justify-content-between">
                        <span>${dol}{escapeHtml(it.namaBarang)}</span>
                        <span>${dol}{mkFormatRupiah(itemTotal)}</span>
                    </div>
                    <div class="text-muted small mb-1">${dol}{it.qty}x @ ${dol}{mkFormatRupiah(it.harga)}</div>
                `;
            });

            receiptHtml += `
                                    </div>
                                    <div class="mb-2 pb-2 border-bottom border-dark border-dashed">
                                        <div class="d-flex justify-content-between fw-bold fs-6">
                                            <span>TOTAL:</span>
                                            <span>${dol}{mkFormatRupiah(total)}</span>
                                        </div>
                                        <div class="d-flex justify-content-between">
                                            <span>TUNAI:</span>
                                            <span>${dol}{mkFormatRupiah(tunai)}</span>
                                        </div>
                                        <div class="d-flex justify-content-between">
                                            <span>KEMBALIAN:</span>
                                            <span>${dol}{mkFormatRupiah(change)}</span>
                                        </div>
                                    </div>
                                    <div class="text-center pt-2 text-muted small">
                                        <div>TERIMA KASIH ATAS KUNJUNGAN ANDA</div>
                                        <div>BARANG YANG DIBELI TIDAK DAPAT DITUKAR</div>
                                    </div>
                                </div>
                            </div>
                            <div class="modal-footer border-top py-2 d-flex justify-content-between">
                                <button type="button" class="btn btn-secondary btn-sm" data-bs-dismiss="modal">Tutup</button>
                                <button type="button" class="btn btn-primary btn-sm fw-bold" onclick="printThermalReceipt()"><i class="bi bi-printer me-1"></i> Cetak Struk</button>
                            </div>
                        </div>
                    </div>
                </div>
            `;

            document.body.insertAdjacentHTML('beforeend', receiptHtml);
            const modal = new bootstrap.Modal(document.getElementById(modalId));
            modal.show();
        }

        function printThermalReceipt() {
            const printContent = document.getElementById('thermalReceiptContent');
            if (!printContent) return;
            const win = window.open('', '', 'width=350,height=500');
            win.document.write('<html><head><title>Struk Kasir</title><style>body{font-family:monospace;font-size:12px;margin:10px;color:#000;}.text-center{text-align:center}.d-flex{display:flex}.justify-content-between{justify-content-space-between}.fw-bold{font-weight:bold}.border-bottom{border-bottom:1px dashed #000;padding-bottom:5px;margin-bottom:5px;}</style></head><body>');
            win.document.write(printContent.innerHTML);
            win.document.write('</body></html>');
            win.document.close();
            win.focus();
            setTimeout(() => {
                win.print();
                win.close();
            }, 300);
        }

        function printKasirReport() {
            window.print();
        }

        function mkPlayBeepSound() {
            try {
                const ctx = new (window.AudioContext || window.webkitAudioContext)();
                const osc = ctx.createOscillator();
                const gain = ctx.createGain();
                osc.type = 'sine';
                osc.frequency.setValueAtTime(1400, ctx.currentTime);
                gain.gain.setValueAtTime(0.25, ctx.currentTime);
                gain.gain.exponentialRampToValueAtTime(0.01, ctx.currentTime + 0.12);
                osc.connect(gain);
                gain.connect(ctx.destination);
                osc.start();
                osc.stop(ctx.currentTime + 0.12);
            } catch (e) {}
            if (navigator.vibrate) {
                try { navigator.vibrate(80); } catch (e) {}
            }
        }

        function openCameraScannerModal(targetInputId, onScanCallback, customTitle) {
            const modalId = 'modalCameraScanner';
            let modalEl = document.getElementById(modalId);
            if (modalEl) {
                stopCameraScanner();
                modalEl.remove();
            }

            const title = customTitle || 'Kamera Pemindai Barcode (100% Offline)';

            const html = `
                <div class="modal fade" id="${dol}{modalId}" tabindex="-1" data-bs-backdrop="static">
                    <div class="modal-dialog modal-dialog-centered">
                        <div class="modal-content card-custom shadow-lg border-0" style="border-radius: 20px;">
                            <div class="modal-header border-bottom py-3">
                                <div class="d-flex align-items-center gap-2">
                                    <div class="bg-primary bg-opacity-10 text-primary p-2 rounded-circle d-flex align-items-center justify-content-center" style="width: 38px; height: 38px;">
                                        <i class="bi bi-camera fs-5"></i>
                                    </div>
                                    <div>
                                        <h6 class="modal-title fw-bold mb-0">${dol}{title}</h6>
                                        <small class="text-success fw-semibold"><i class="bi bi-shield-check me-1"></i>Akses Kamera 100% Offline Lokal</small>
                                    </div>
                                </div>
                                <button type="button" class="btn-close" data-bs-dismiss="modal" onclick="stopCameraScanner()"></button>
                            </div>
                            <div class="modal-body text-center p-3 p-sm-4">
                                <div id="qr-reader" style="width: 100%; max-width: 380px; min-height: 250px; margin: auto; border-radius: 14px; overflow: hidden; background: #000;" class="border shadow-sm"></div>
                                <div class="d-flex align-items-center justify-content-center gap-2 mt-3 text-muted small">
                                    <i class="bi bi-upc-scan text-primary"></i>
                                    <span>Posisikan barcode atau QR di dalam kotak pemindai</span>
                                </div>
                                <div id="camera-scan-status" class="mt-2 text-primary small fw-semibold">Memulai kamera perangkat...</div>
                            </div>
                            <div class="modal-footer border-top py-2 d-flex justify-content-between">
                                <small class="text-muted"><i class="bi bi-lightning-charge text-warning me-1"></i>Deteksi Instan</small>
                                <button type="button" class="btn btn-secondary btn-sm px-3 fw-semibold" data-bs-dismiss="modal" onclick="stopCameraScanner()">Tutup</button>
                            </div>
                        </div>
                    </div>
                </div>
            `;
            document.body.insertAdjacentHTML('beforeend', html);
            const modal = new bootstrap.Modal(document.getElementById(modalId));
            modal.show();

            const modalNode = document.getElementById(modalId);
            modalNode.addEventListener('hidden.bs.modal', function () {
                stopCameraScanner();
                if (modalNode) modalNode.remove();
            }, { once: true });

            setTimeout(() => {
                if (window.Html5Qrcode) {
                    try {
                        window.html5QrCodeScanner = new Html5Qrcode("qr-reader");
                        const statusEl = document.getElementById('camera-scan-status');
                        
                        const config = {
                            fps: 15,
                            qrbox: { width: 260, height: 180 },
                            aspectRatio: 1.333334
                        };

                        window.html5QrCodeScanner.start(
                            { facingMode: "environment" },
                            config,
                            (decodedText) => {
                                mkPlayBeepSound();
                                stopCameraScanner();
                                modal.hide();

                                const cleanCode = (decodedText || '').trim();

                                if (typeof onScanCallback === 'function') {
                                    onScanCallback(cleanCode);
                                } else if (targetInputId) {
                                    const input = document.getElementById(targetInputId);
                                    if (input) {
                                        input.value = cleanCode;
                                        input.dispatchEvent(new Event('input', { bubbles: true }));
                                        input.dispatchEvent(new Event('change', { bubbles: true }));
                                        input.focus();
                                    }
                                } else {
                                    handlePosBarcodeEnter(cleanCode);
                                }
                            },
                            (errorMessage) => {
                                // scanning in progress...
                            }
                        ).then(() => {
                            if (statusEl) statusEl.innerHTML = '<span class="text-success"><i class="bi bi-check-circle me-1"></i>Kamera aktif. Siap memindai...</span>';
                        }).catch(err => {
                            console.error("Camera scan error:", err);
                            const reader = document.getElementById('qr-reader');
                            if (reader) {
                                reader.innerHTML = `
                                    <div class="alert alert-warning m-3 text-start small">
                                        <div class="fw-bold mb-1"><i class="bi bi-exclamation-triangle me-1"></i>Izin Kamera Diperlukan</div>
                                        <div>Pastikan Anda memberikan izin akses kamera pada browser untuk menggunakan pemindai. Anda tetap dapat mengetik barcode secara manual.</div>
                                    </div>
                                `;
                            }
                            if (statusEl) statusEl.innerText = '';
                        });
                    } catch (e) {
                        console.error("Scanner init error:", e);
                    }
                } else {
                    const reader = document.getElementById('qr-reader');
                    if (reader) {
                        reader.innerHTML = '<div class="alert alert-danger m-3 small">Modul scanner tidak tersedia offline. Silakan gunakan input manual.</div>';
                    }
                }
            }, 450);
        }

        function openCameraScannerModalForInput(inputId, onScanCallback, title) {
            openCameraScannerModal(inputId, onScanCallback, title);
        }

        function openCameraScannerModalForShoppingSearch() {
            openCameraScannerModal(null, (code) => {
                window.mkShoppingSearch = code;
                renderShoppingUi();
            }, 'Scan Barcode / Cari Belanja');
        }

        function openCameraScannerModalForShoppingForm() {
            openCameraScannerModal(null, (code) => {
                const cleanCode = (code || '').trim();
                const matched = (window.mkKasirData.stock || []).find(s => 
                    (s.barcode || '').toLowerCase() === cleanCode.toLowerCase() || 
                    (s.nama || '').toLowerCase() === cleanCode.toLowerCase()
                );

                const namaInp = document.getElementById('inpShopNama');
                const hargaInp = document.getElementById('inpShopHarga');
                const jenisInp = document.getElementById('inpShopJenis');

                if (matched) {
                    if (namaInp) namaInp.value = matched.nama;
                    if (hargaInp) {
                        hargaInp.value = matched.harga;
                        calcShoppingModalTotal();
                    }
                    if (jenisInp && matched.kategori) {
                        for (let opt of jenisInp.options) {
                            if (opt.value.toLowerCase().includes(matched.kategori.toLowerCase()) || matched.kategori.toLowerCase().includes(opt.value.toLowerCase())) {
                                jenisInp.value = opt.value;
                                break;
                            }
                        }
                    }
                } else {
                    if (namaInp) namaInp.value = cleanCode;
                }
            }, 'Scan Barcode Produk Belanja');
        }

        function stopCameraScanner() {
            if (window.html5QrCodeScanner) {
                try {
                    window.html5QrCodeScanner.stop().then(() => {
                        try {
                            window.html5QrCodeScanner.clear();
                        } catch (e) {}
                        window.html5QrCodeScanner = null;
                    }).catch(e => {
                        window.html5QrCodeScanner = null;
                    });
                } catch (e) {
                    window.html5QrCodeScanner = null;
                }
            }
        }


        // ==========================================
        // 2. MODUL DAFTAR BELANJA (SHOPPING)
        // ==========================================

        async function fetchShoppingData() {
            try {
                const res = await fetch('/api/shopping');
                if (res.ok) {
                    window.mkShoppingList = await res.json();
                }
            } catch (e) {
                console.error("Gagal memuat daftar belanja:", e);
            }
        }

        async function loadShopping() {
            await fetchShoppingData();
            renderShoppingUi();
        }

        function getShoppingCategoryStyle(cat) {
            switch (cat) {
                case 'Pakaian & Fashion': return { color: '#9C27B0', icon: 'bi-handbag', bg: 'rgba(156, 39, 176, 0.12)' };
                case 'Perlengkapan Sekolah': return { color: '#2196F3', icon: 'bi-mortarboard', bg: 'rgba(33, 150, 243, 0.12)' };
                case 'Supermarket': return { color: '#4CAF50', icon: 'bi-cart-check', bg: 'rgba(76, 175, 80, 0.12)' };
                case 'Perawatan & Kecantikan': return { color: '#E91E63', icon: 'bi-flower1', bg: 'rgba(233, 30, 99, 0.12)' };
                case 'Elektronik': return { color: '#FF9800', icon: 'bi-laptop', bg: 'rgba(255, 152, 0, 0.12)' };
                case 'Mainan & Anak': return { color: '#00BCD4', icon: 'bi-controller', bg: 'rgba(0, 188, 212, 0.12)' };
                case 'Perabotan Rumah': return { color: '#795548', icon: 'bi-house-door', bg: 'rgba(121, 85, 72, 0.12)' };
                default: return { color: '#607D8B', icon: 'bi-bag-check', bg: 'rgba(96, 125, 139, 0.12)' };
            }
        }

        function resetShoppingFilters() {
            window.mkShoppingSearch = '';
            window.mkShoppingCategory = 'Semua';
            window.mkShoppingDatePreset = 'all';
            window.mkShoppingDateStart = '';
            window.mkShoppingDateEnd = '';
            window.mkShoppingSort = 'newest';
            renderShoppingUi();
        }

        function renderShoppingUi() {
            const container = document.getElementById('tab-content') || document.getElementById('main-content');
            if (!container) return;

            const items = window.mkShoppingList || [];
            const search = (window.mkShoppingSearch || '').toLowerCase().trim();
            const cat = window.mkShoppingCategory || 'Semua';
            const datePreset = window.mkShoppingDatePreset || 'all';
            const dateStart = window.mkShoppingDateStart || '';
            const dateEnd = window.mkShoppingDateEnd || '';
            const sortOrder = window.mkShoppingSort || 'newest';

            const categoryList = [
                'Semua',
                'Pakaian & Fashion',
                'Perlengkapan Sekolah',
                'Supermarket',
                'Perawatan & Kecantikan',
                'Elektronik',
                'Mainan & Anak',
                'Perabotan Rumah',
                'Lainnya'
            ];

            // Date helpers
            const now = new Date();
            const todayStr = now.toISOString().split('T')[0];
            const sevenDaysAgo = new Date(now.getTime() - (7 * 24 * 60 * 60 * 1000)).toISOString().split('T')[0];
            const currentMonthStr = todayStr.substring(0, 7);

            // Filter logic
            let filtered = items.filter(it => {
                const namaStr = (it.nama || '').toLowerCase();
                const jenisStr = (it.jenis || 'Lainnya').toLowerCase();
                const hargaStr = String(it.hargaSatuan || '');
                const totalStr = String(it.total || '');
                const matchesSearch = !search || namaStr.includes(search) || jenisStr.includes(search) || hargaStr.includes(search) || totalStr.includes(search);
                
                const matchesCat = (cat === 'Semua') || ((it.jenis || 'Lainnya') === cat);

                let matchesDate = true;
                const trxDate = it.tanggal || '';
                if (datePreset === 'today') {
                    matchesDate = (trxDate === todayStr);
                } else if (datePreset === 'week') {
                    matchesDate = (trxDate >= sevenDaysAgo && trxDate <= todayStr);
                } else if (datePreset === 'month') {
                    matchesDate = trxDate.startsWith(currentMonthStr);
                } else if (datePreset === 'custom') {
                    const matchesStart = !dateStart || (trxDate >= dateStart);
                    const matchesEnd = !dateEnd || (trxDate <= dateEnd);
                    matchesDate = matchesStart && matchesEnd;
                } else {
                    const matchesStart = !dateStart || (trxDate >= dateStart);
                    const matchesEnd = !dateEnd || (trxDate <= dateEnd);
                    matchesDate = matchesStart && matchesEnd;
                }

                return matchesSearch && matchesCat && matchesDate;
            });

            // Sorting logic
            filtered.sort((a, b) => {
                if (sortOrder === 'newest') {
                    const dateComp = (b.tanggal || '').localeCompare(a.tanggal || '');
                    return dateComp !== 0 ? dateComp : (b.id || 0) - (a.id || 0);
                } else if (sortOrder === 'oldest') {
                    const dateComp = (a.tanggal || '').localeCompare(b.tanggal || '');
                    return dateComp !== 0 ? dateComp : (a.id || 0) - (b.id || 0);
                } else if (sortOrder === 'total_desc') {
                    return (Number(b.total) || 0) - (Number(a.total) || 0);
                } else if (sortOrder === 'total_asc') {
                    return (Number(a.total) || 0) - (Number(b.total) || 0);
                } else if (sortOrder === 'name_asc') {
                    return (a.nama || '').localeCompare(b.nama || '');
                }
                return 0;
            });

            // Summary Calculations
            const totalBelanja = filtered.reduce((acc, it) => acc + (Number(it.total) || 0), 0);
            const totalQty = filtered.reduce((acc, it) => acc + (Number(it.qty) || 0), 0);
            const totalPajak = filtered.reduce((acc, it) => acc + ((Number(it.hargaSatuan) || 0) * (Number(it.qty) || 0) * ((Number(it.pajak) || 0) / 100.0)), 0);

            // Category Counts
            const categoryCounts = {};
            categoryList.forEach(c => {
                if (c === 'Semua') {
                    categoryCounts[c] = items.length;
                } else {
                    categoryCounts[c] = items.filter(it => (it.jenis || 'Lainnya') === c).length;
                }
            });

            // Dominant Category
            const filteredCatCounts = {};
            filtered.forEach(it => {
                const c = it.jenis || 'Lainnya';
                filteredCatCounts[c] = (filteredCatCounts[c] || 0) + 1;
            });
            const topCategory = Object.entries(filteredCatCounts).sort((a, b) => b[1] - a[1])[0];
            const dominantCategory = topCategory ? topCategory[0] : '—';

            const isAnyFilterActive = search || cat !== 'Semua' || datePreset !== 'all' || dateStart || dateEnd || sortOrder !== 'newest';

            let html = `
                <div class="container-fluid px-0">
                    ${dol}{renderManajemenKeuanganSubNav('shopping')}

                    <!-- 4 Asymmetric Responsive Summary Cards -->
                    <div class="row g-3 mb-4">
                        <div class="col-12 col-sm-6 col-xl-3">
                            <div class="card card-custom p-3 p-sm-4 h-100 border-0 text-white shadow-sm" style="background: linear-gradient(135deg, #2563eb 0%, #1d4ed8 100%); border-radius: 16px;">
                                <div class="d-flex justify-content-between align-items-center mb-1">
                                    <span class="small fw-semibold text-uppercase text-white-50">Total Pengeluaran Belanja</span>
                                    <i class="bi bi-cart-check fs-5 fs-sm-4"></i>
                                </div>
                                <h4 class="fw-bold mb-1 fs-5 fs-sm-4">${dol}{mkFormatRupiah(totalBelanja)}</h4>
                                <small class="text-white-50"><i class="bi bi-check-circle me-1"></i> ${dol}{filtered.length} item transaksi</small>
                            </div>
                        </div>

                        <div class="col-12 col-sm-6 col-xl-3">
                            <div class="card card-custom p-3 p-sm-4 h-100 border-0 shadow-sm" style="background: linear-gradient(135deg, rgba(16, 185, 129, 0.1) 0%, rgba(5, 150, 105, 0.1) 100%); border-radius: 16px; border-left: 4px solid #10b981 !important;">
                                <div class="d-flex justify-content-between align-items-center mb-1">
                                    <span class="small fw-semibold text-uppercase text-muted">Total Unit Barang</span>
                                    <i class="bi bi-boxes text-success fs-5 fs-sm-4"></i>
                                </div>
                                <h4 class="fw-bold text-success mb-1 fs-5 fs-sm-4">${dol}{totalQty} Unit</h4>
                                <small class="text-muted"><i class="bi bi-bag me-1"></i> Kuantitas akumulasi</small>
                            </div>
                        </div>

                        <div class="col-12 col-sm-6 col-xl-3">
                            <div class="card card-custom p-3 p-sm-4 h-100 border-0 shadow-sm" style="background: linear-gradient(135deg, rgba(245, 158, 11, 0.1) 0%, rgba(217, 119, 6, 0.1) 100%); border-radius: 16px; border-left: 4px solid #f59e0b !important;">
                                <div class="d-flex justify-content-between align-items-center mb-1">
                                    <span class="small fw-semibold text-uppercase text-muted">Estimasi Pajak</span>
                                    <i class="bi bi-receipt text-warning fs-5 fs-sm-4"></i>
                                </div>
                                <h4 class="fw-bold text-warning mb-1 fs-5 fs-sm-4">${dol}{mkFormatRupiah(totalPajak)}</h4>
                                <small class="text-muted"><i class="bi bi-percent me-1"></i> Pajak PPN belanja</small>
                            </div>
                        </div>

                        <div class="col-12 col-sm-6 col-xl-3">
                            <div class="card card-custom p-3 p-sm-4 h-100 border-0 shadow-sm" style="background: linear-gradient(135deg, rgba(139, 92, 246, 0.1) 0%, rgba(109, 40, 217, 0.1) 100%); border-radius: 16px; border-left: 4px solid #8b5cf6 !important;">
                                <div class="d-flex justify-content-between align-items-center mb-1">
                                    <span class="small fw-semibold text-uppercase text-muted">Kategori Terbanyak</span>
                                    <i class="bi bi-tags text-primary fs-5 fs-sm-4"></i>
                                </div>
                                <h4 class="fw-bold text-primary mb-1 text-truncate fs-5 fs-sm-4">${dol}{escapeHtml(dominantCategory)}</h4>
                                <small class="text-muted"><i class="bi bi-award me-1"></i> Porsi pengeluaran utama</small>
                            </div>
                        </div>
                    </div>

                    <!-- Filter Data Menu List Panel with Dropdown & Responsive Nav -->
                    <div class="card shopping-filter-panel p-3 p-sm-4 shadow-sm mb-4">
                        <div class="d-flex flex-wrap justify-content-between align-items-center gap-2 mb-3 pb-2 border-bottom">
                            <div class="d-flex align-items-center gap-2">
                                <i class="bi bi-funnel text-primary fs-5"></i>
                                <h6 class="fw-bold mb-0">Filter & Menu List Daftar Belanja</h6>
                            </div>
                            <div class="d-flex align-items-center gap-2">
                                <span class="badge bg-primary bg-opacity-10 text-primary border border-primary border-opacity-25 px-2 py-1 rounded-pill small">
                                    ${dol}{filtered.length} dari ${dol}{items.length} item
                                </span>
                                ${dol}{isAnyFilterActive ? `<button class="btn btn-sm btn-outline-danger py-0 px-2 rounded-pill small" onclick="resetShoppingFilters()"><i class="bi bi-x-circle me-1"></i>Reset Filter</button>` : ''}
                            </div>
                        </div>

                        <!-- Menu List Nav Pills (Scrollable for Touch Devices) -->
                        <div class="shopping-menu-nav mb-3">
            `;

            categoryList.forEach(c => {
                const isSel = (c === cat);
                const style = getShoppingCategoryStyle(c);
                const count = categoryCounts[c] || 0;
                const activeClass = isSel ? 'active-cat' : '';
                html += `
                    <button type="button" class="shopping-menu-item ${dol}{activeClass}" onclick="window.mkShoppingCategory = '${dol}{escapeJs(c)}'; renderShoppingUi();">
                        <i class="bi ${dol}{style.icon} me-1 ${dol}{isSel ? 'text-white' : ''}"></i> ${dol}{escapeHtml(c)}
                        <span class="badge ${dol}{isSel ? 'bg-white text-dark' : 'bg-secondary bg-opacity-25 text-body'} rounded-pill ms-1">${dol}{count}</span>
                    </button>
                `;
            });

            html += `
                        </div>

                        <!-- Filter Controls Grid -->
                        <div class="row g-2 align-items-center">
                            <!-- Live Search Input -->
                            <div class="col-12 col-md-4">
                                <div class="input-group input-group-sm">
                                    <span class="input-group-text bg-transparent"><i class="bi bi-search"></i></span>
                                    <input type="text" class="form-control" placeholder="Cari nama barang belanja / nominal..." value="${dol}{escapeHtml(window.mkShoppingSearch || '')}" oninput="window.mkShoppingSearch = this.value; renderShoppingUi();">
                                    ${dol}{search ? `<button class="btn btn-outline-secondary" onclick="window.mkShoppingSearch = ''; renderShoppingUi();"><i class="bi bi-x"></i></button>` : ''}
                                    <button class="btn btn-outline-primary" type="button" title="Scan Barcode / Cari Belanja (100% Offline)" onclick="openCameraScannerModalForShoppingSearch()"><i class="bi bi-camera"></i></button>
                                </div>
                            </div>

                            <!-- Menu List Dropdown (Semua, Pakaian & Fashion, Perlengkapan Sekolah, Supermarket, dll) -->
                            <div class="col-12 col-md-4">
                                <div class="input-group input-group-sm">
                                    <span class="input-group-text bg-transparent"><i class="bi bi-tag"></i></span>
                                    <select class="form-select" onchange="window.mkShoppingCategory = this.value; renderShoppingUi();">
            `;

            categoryList.forEach(c => {
                const sel = (c === cat) ? 'selected' : '';
                const count = categoryCounts[c] || 0;
                html += `<option value="${dol}{escapeHtml(c)}" ${dol}{sel}>Kategori: ${dol}{escapeHtml(c)} (${dol}{count})</option>`;
            });

            html += `
                                    </select>
                                </div>
                            </div>

                            <!-- Sorting Dropdown -->
                            <div class="col-12 col-md-4">
                                <div class="input-group input-group-sm">
                                    <span class="input-group-text bg-transparent"><i class="bi bi-sort-down"></i></span>
                                    <select class="form-select" onchange="window.mkShoppingSort = this.value; renderShoppingUi();">
                                        <option value="newest" ${dol}{sortOrder === 'newest' ? 'selected' : ''}>Urut: Tanggal Terbaru</option>
                                        <option value="oldest" ${dol}{sortOrder === 'oldest' ? 'selected' : ''}>Urut: Tanggal Terlama</option>
                                        <option value="total_desc" ${dol}{sortOrder === 'total_desc' ? 'selected' : ''}>Total Tertinggi</option>
                                        <option value="total_asc" ${dol}{sortOrder === 'total_asc' ? 'selected' : ''}>Total Terendah</option>
                                        <option value="name_asc" ${dol}{sortOrder === 'name_asc' ? 'selected' : ''}>Nama Barang (A-Z)</option>
                                    </select>
                                </div>
                            </div>

                            <!-- Date Range Preset -->
                            <div class="col-12 col-md-4 mt-2">
                                <div class="input-group input-group-sm">
                                    <span class="input-group-text bg-transparent"><i class="bi bi-calendar-range"></i></span>
                                    <select class="form-select" onchange="window.mkShoppingDatePreset = this.value; if(this.value !== 'custom') { window.mkShoppingDateStart = ''; window.mkShoppingDateEnd = ''; } renderShoppingUi();">
                                        <option value="all" ${dol}{datePreset === 'all' ? 'selected' : ''}>Semua Waktu</option>
                                        <option value="today" ${dol}{datePreset === 'today' ? 'selected' : ''}>Hari Ini</option>
                                        <option value="week" ${dol}{datePreset === 'week' ? 'selected' : ''}>7 Hari Terakhir</option>
                                        <option value="month" ${dol}{datePreset === 'month' ? 'selected' : ''}>Bulan Ini</option>
                                        <option value="custom" ${dol}{datePreset === 'custom' ? 'selected' : ''}>Pilih Rentang Tanggal...</option>
                                    </select>
                                </div>
                            </div>

                            <!-- Custom Date Range Inputs -->
                            <div class="col-12 col-md-4 mt-2">
                                <div class="d-flex align-items-center gap-1">
                                    <input type="date" class="form-control form-control-sm" placeholder="Dari" value="${dol}{escapeHtml(dateStart)}" onchange="window.mkShoppingDatePreset = 'custom'; window.mkShoppingDateStart = this.value; renderShoppingUi();" title="Dari Tanggal">
                                    <span class="text-muted small">-</span>
                                    <input type="date" class="form-control form-control-sm" placeholder="Sampai" value="${dol}{escapeHtml(dateEnd)}" onchange="window.mkShoppingDatePreset = 'custom'; window.mkShoppingDateEnd = this.value; renderShoppingUi();" title="Sampai Tanggal">
                                </div>
                            </div>

                            <!-- Action Buttons: Tambah Belanja & Cetak -->
                            <div class="col-12 col-md-4 mt-2">
                                <div class="d-flex align-items-center gap-2 justify-content-between justify-content-md-end">
                                    <button class="btn btn-sm btn-primary fw-semibold flex-grow-1 flex-md-grow-0 text-nowrap" onclick="openAddShoppingModal()">
                                        <i class="bi bi-plus-lg me-1"></i> Tambah Belanja
                                    </button>
                                    <button class="btn btn-sm btn-outline-primary fw-semibold px-3 text-nowrap" onclick="window.print()" title="Cetak Rekap Belanja">
                                        <i class="bi bi-printer me-1"></i> Cetak
                                    </button>
                                </div>
                            </div>
                        </div>

                        <!-- Active Filter Summary -->
                        ${dol}{isAnyFilterActive ? `
                            <div class="d-flex flex-wrap align-items-center gap-2 mt-3 pt-2 border-top small text-muted">
                                <span><i class="bi bi-info-circle me-1"></i> Ringkasan Filter:</span>
                                <span class="badge bg-primary bg-opacity-10 text-primary border border-primary border-opacity-25">Total Belanja: ${dol}{mkFormatRupiah(totalBelanja)}</span>
                                <span class="badge bg-success bg-opacity-10 text-success border border-success border-opacity-25">Total Unit: ${dol}{totalQty} Unit</span>
                                ${dol}{cat !== 'Semua' ? `<span class="badge bg-info bg-opacity-10 text-info border border-info border-opacity-25">Kategori: ${dol}{escapeHtml(cat)}</span>` : ''}
                            </div>
                        ` : ''}
                    </div>

                    <!-- Shopping Items Table (100% Format Tabel Responsif di Semua Perangkat) -->
                    <div class="card card-custom p-3 p-sm-4 shadow-sm mb-4">
                        <div class="d-flex flex-wrap justify-content-between align-items-center gap-2 mb-3">
                            <div class="d-flex align-items-center gap-2">
                                <i class="bi bi-table text-primary fs-5"></i>
                                <h6 class="fw-bold mb-0">Daftar Riwayat Catatan Belanja</h6>
                            </div>
                            <span class="badge bg-secondary bg-opacity-25 text-body fw-bold">${dol}{filtered.length} Item Belanja</span>
                        </div>

                        <div class="table-responsive">
                            <table class="table table-shopping table-hover align-middle mb-0">
                                <thead class="table-light">
                                    <tr>
                                        <th scope="col" style="min-width: 140px;">Kategori</th>
                                        <th scope="col" style="min-width: 180px;">Nama Barang</th>
                                        <th scope="col" style="min-width: 120px;">Tanggal Belanja</th>
                                        <th scope="col" style="min-width: 120px;">Harga Satuan</th>
                                        <th scope="col" class="text-center" style="min-width: 70px;">Qty</th>
                                        <th scope="col" class="text-center" style="min-width: 80px;">Pajak</th>
                                        <th scope="col" class="text-end" style="min-width: 130px;">Total Akhir</th>
                                        <th scope="col" class="text-end" style="min-width: 90px;">Aksi</th>
                                    </tr>
                                </thead>
                                <tbody>
            `;

            if (filtered.length === 0) {
                html += `<tr><td colspan="8" class="text-center py-5 text-muted"><i class="bi bi-cart-x fs-1 d-block mb-2 opacity-50"></i>Tidak ada item belanja yang cocok dengan filter.</td></tr>`;
            } else {
                filtered.forEach(it => {
                    const cStyle = getShoppingCategoryStyle(it.jenis || 'Lainnya');
                    html += `
                        <tr>
                            <td>
                                <span class="badge rounded-pill px-2 px-sm-3 py-1 py-sm-2 d-inline-flex align-items-center gap-1" style="background: ${dol}{cStyle.bg}; color: ${dol}{cStyle.color}; font-weight: 600;">
                                    <i class="bi ${dol}{cStyle.icon}"></i> ${dol}{escapeHtml(it.jenis || 'Lainnya')}
                                </span>
                            </td>
                            <td>
                                <div class="fw-bold text-break">${dol}{escapeHtml(it.nama)}</div>
                            </td>
                            <td>
                                <span class="text-muted small"><i class="bi bi-calendar3 me-1"></i>${dol}{mkFormatDateIndo(it.tanggal)}</span>
                            </td>
                            <td class="fw-semibold">${dol}{mkFormatRupiah(it.hargaSatuan)}</td>
                            <td class="text-center fw-bold fs-6">${dol}{it.qty}</td>
                            <td class="text-center"><span class="badge bg-light text-dark border">${dol}{it.pajak || 0}%</span></td>
                            <td class="text-end fw-bold fs-6 text-success">${dol}{mkFormatRupiah(it.total)}</td>
                            <td class="text-end text-nowrap">
                                <button class="btn btn-sm btn-outline-primary py-1 px-2 me-1" onclick="openEditShoppingModal(${dol}{it.id})" title="Edit Item">
                                    <i class="bi bi-pencil"></i>
                                </button>
                                <button class="btn btn-sm btn-outline-danger py-1 px-2" onclick="deleteShoppingItem(${dol}{it.id})" title="Hapus Item">
                                    <i class="bi bi-trash"></i>
                                </button>
                            </td>
                        </tr>
                    `;
                });
            }

            html += `
                                </tbody>
                            </table>
                        </div>
                    </div>
                </div>
            `;

            container.innerHTML = html;
        }

        function openAddShoppingModal() {
            openShoppingFormModal(null);
        }

        function openEditShoppingModal(itemId) {
            const item = (window.mkShoppingList || []).find(i => i.id === itemId);
            if (item) openShoppingFormModal(item);
        }

        function openShoppingFormModal(item) {
            const isEdit = !!item;
            const title = isEdit ? 'Edit Catatan Belanja' : 'Tambah Item Belanja Baru';
            const modalId = 'modalShoppingForm';

            let modalEl = document.getElementById(modalId);
            if (modalEl) modalEl.remove();

            const curDate = item ? item.tanggal : new Date().toISOString().split('T')[0];

            const html = `
                <div class="modal fade" id="${dol}{modalId}" tabindex="-1">
                    <div class="modal-dialog modal-dialog-centered">
                        <div class="modal-content card-custom">
                            <div class="modal-header border-bottom">
                                <h5 class="modal-title fw-bold"><i class="bi bi-cart-plus text-primary me-2"></i> ${dol}{title}</h5>
                                <button type="button" class="btn-close" data-bs-dismiss="modal"></button>
                            </div>
                            <div class="modal-body">
                                <form id="formShoppingInput" onsubmit="event.preventDefault(); saveShoppingForm(${dol}{item ? item.id : 'null'});">
                                    <div class="mb-3">
                                        <label class="form-label small fw-bold">Nama / Barcode Barang Belanja <span class="text-danger">*</span></label>
                                        <div class="input-group">
                                            <input type="text" id="inpShopNama" class="form-control" required value="${dol}{item ? escapeHtml(item.nama) : ''}" placeholder="Contoh: Beras Premium 5kg / Barcode">
                                            <button class="btn btn-primary" type="button" title="Scan Barcode Kamera (100% Offline)" onclick="openCameraScannerModalForShoppingForm()"><i class="bi bi-camera"></i></button>
                                        </div>
                                        <small class="text-muted" style="font-size: 0.78rem;"><i class="bi bi-camera me-1"></i> Scan barcode untuk auto-fill nama & harga dari katalog stok secara offline</small>
                                    </div>
                                    <div class="row g-2 mb-3">
                                        <div class="col-12 col-sm-6">
                                            <label class="form-label small fw-bold">Kategori Belanja</label>
                                            <select id="inpShopJenis" class="form-select">
                                                <option value="Supermarket" ${dol}{item && item.jenis === 'Supermarket' ? 'selected' : ''}>Supermarket</option>
                                                <option value="Pakaian & Fashion" ${dol}{item && item.jenis === 'Pakaian & Fashion' ? 'selected' : ''}>Pakaian & Fashion</option>
                                                <option value="Perlengkapan Sekolah" ${dol}{item && item.jenis === 'Perlengkapan Sekolah' ? 'selected' : ''}>Perlengkapan Sekolah</option>
                                                <option value="Perawatan & Kecantikan" ${dol}{item && item.jenis === 'Perawatan & Kecantikan' ? 'selected' : ''}>Perawatan & Kecantikan</option>
                                                <option value="Elektronik" ${dol}{item && item.jenis === 'Elektronik' ? 'selected' : ''}>Elektronik</option>
                                                <option value="Mainan & Anak" ${dol}{item && item.jenis === 'Mainan & Anak' ? 'selected' : ''}>Mainan & Anak</option>
                                                <option value="Perabotan Rumah" ${dol}{item && item.jenis === 'Perabotan Rumah' ? 'selected' : ''}>Perabotan Rumah</option>
                                                <option value="Lainnya" ${dol}{item && item.jenis === 'Lainnya' ? 'selected' : ''}>Lainnya</option>
                                            </select>
                                        </div>
                                        <div class="col-12 col-sm-6">
                                            <label class="form-label small fw-bold">Tanggal Belanja</label>
                                            <input type="date" id="inpShopTanggal" class="form-control" required value="${dol}{escapeHtml(curDate)}">
                                        </div>
                                    </div>
                                    <div class="row g-2 mb-3">
                                        <div class="col-12 col-sm-6">
                                            <label class="form-label small fw-bold">Harga Satuan (Rp) <span class="text-danger">*</span></label>
                                            <input type="number" id="inpShopHarga" class="form-control" required value="${dol}{item ? item.hargaSatuan : ''}" placeholder="0" oninput="calcShoppingModalTotal()">
                                        </div>
                                        <div class="col-6 col-sm-3">
                                            <label class="form-label small fw-bold">Qty <span class="text-danger">*</span></label>
                                            <input type="number" id="inpShopQty" class="form-control" required value="${dol}{item ? item.qty : '1'}" min="1" oninput="calcShoppingModalTotal()">
                                        </div>
                                        <div class="col-6 col-sm-3">
                                            <label class="form-label small fw-bold">Pajak %</label>
                                            <input type="number" id="inpShopPajak" class="form-control" value="${dol}{item ? item.pajak : '0'}" min="0" oninput="calcShoppingModalTotal()">
                                        </div>
                                    </div>
                                    <div class="card p-3 bg-light border-0 mb-3">
                                        <div class="d-flex justify-content-between align-items-center">
                                            <span class="small fw-semibold text-muted">Perkiraan Total Belanja:</span>
                                            <span id="shopModalTotalPreview" class="fw-bold fs-5 text-success">Rp 0</span>
                                        </div>
                                    </div>
                                    <div class="d-flex justify-content-end gap-2 pt-2 border-top">
                                        <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">Batal</button>
                                        <button type="submit" class="btn btn-primary fw-bold"><i class="bi bi-save me-1"></i> Simpan Belanja</button>
                                    </div>
                                </form>
                            </div>
                        </div>
                    </div>
                </div>
            `;
            document.body.insertAdjacentHTML('beforeend', html);
            const modal = new bootstrap.Modal(document.getElementById(modalId));
            modal.show();
            calcShoppingModalTotal();
        }

        function calcShoppingModalTotal() {
            const h = parseFloat(document.getElementById('inpShopHarga')?.value) || 0;
            const q = parseInt(document.getElementById('inpShopQty')?.value, 10) || 1;
            const p = parseFloat(document.getElementById('inpShopPajak')?.value) || 0;
            const total = (h * q) * (1.0 + (p / 100.0));
            const prev = document.getElementById('shopModalTotalPreview');
            if (prev) prev.innerText = mkFormatRupiah(total);
        }

        async function saveShoppingForm(existingId) {
            const nama = document.getElementById('inpShopNama').value.trim();
            const jenis = document.getElementById('inpShopJenis').value;
            const tanggal = document.getElementById('inpShopTanggal').value;
            const hargaSatuan = parseFloat(document.getElementById('inpShopHarga').value) || 0;
            const qty = parseInt(document.getElementById('inpShopQty').value, 10) || 1;
            const pajak = parseFloat(document.getElementById('inpShopPajak').value) || 0;

            if (!nama) return alert('Nama barang wajib diisi!');

            const payload = {
                nama: nama,
                jenis: jenis,
                tanggal: tanggal,
                hargaSatuan: hargaSatuan,
                qty: qty,
                pajak: pajak
            };

            const method = existingId ? 'PUT' : 'POST';
            if (existingId) payload.id = existingId;

            try {
                await fetch('/api/shopping', {
                    method: method,
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(payload)
                });
                const modalEl = document.getElementById('modalShoppingForm');
                if (modalEl) {
                    const modal = bootstrap.Modal.getInstance(modalEl);
                    if (modal) modal.hide();
                }
                await fetchShoppingData();
                renderShoppingUi();
            } catch (e) {
                alert('Gagal menyimpan belanja: ' + e.message);
            }
        }

        async function deleteShoppingItem(id) {
            if (!confirm('Apakah Anda yakin ingin menghapus item belanja ini?')) return;
            try {
                await fetch('/api/shopping?id=' + encodeURIComponent(id), { method: 'DELETE' });
                await fetchShoppingData();
                renderShoppingUi();
            } catch (e) {
                alert('Gagal menghapus item: ' + e.message);
            }
        }


        // ==========================================
        // 3. MODUL CATATAN TABUNGAN (SAVINGS)
        // ==========================================

        async function fetchSavingsData() {
            try {
                const res = await fetch('/api/savings');
                if (res.ok) {
                    const data = await res.json();
                    window.mkSavingsTarget = data.target || { id: 1, nama: 'Tabungan Utama', jumlah: 0 };
                    window.mkSavingsTransactions = data.transactions || [];
                }
            } catch (e) {
                console.error("Gagal memuat catatan tabungan:", e);
            }
        }

        async function loadSavings() {
            await fetchSavingsData();
            renderSavingsUi();
        }

        function resetSavingsFilters() {
            window.mkSavingsSearch = '';
            window.mkSavingsTypeFilter = 'Semua';
            window.mkSavingsCategoryFilter = 'Semua';
            window.mkSavingsDatePreset = 'all';
            window.mkSavingsDateFilter = '';
            window.mkSavingsSort = 'newest';
            renderSavingsUi();
        }

        function renderSavingsUi() {
            const container = document.getElementById('tab-content') || document.getElementById('main-content');
            if (!container) return;

            const target = window.mkSavingsTarget || { nama: 'Tabungan Utama', jumlah: 0 };
            const transactions = window.mkSavingsTransactions || [];

            const totalMasuk = transactions.filter(t => t.jenis === 'masuk').reduce((acc, t) => acc + (Number(t.nominal) || 0), 0);
            const totalKeluar = transactions.filter(t => t.jenis === 'keluar').reduce((acc, t) => acc + (Number(t.nominal) || 0), 0);
            const currentBalance = totalMasuk - totalKeluar;

            const countMasuk = transactions.filter(t => t.jenis === 'masuk').length;
            const countKeluar = transactions.filter(t => t.jenis === 'keluar').length;

            const targetAmount = Number(target.jumlah) || 0;
            const progressPercent = targetAmount > 0 ? Math.min(100, Math.round((currentBalance / targetAmount) * 100)) : 0;
            const remainingTarget = Math.max(0, targetAmount - currentBalance);

            // Filter states
            const search = (window.mkSavingsSearch || '').toLowerCase().trim();
            const typeFilter = window.mkSavingsTypeFilter || 'Semua';
            const catFilter = window.mkSavingsCategoryFilter || 'Semua';
            const datePreset = window.mkSavingsDatePreset || 'all';
            const dateFilter = window.mkSavingsDateFilter || '';
            const sortOrder = window.mkSavingsSort || 'newest';

            // Today, 7 days ago, Current Month helpers
            const now = new Date();
            const todayStr = now.toISOString().split('T')[0];
            const sevenDaysAgo = new Date(now.getTime() - (7 * 24 * 60 * 60 * 1000)).toISOString().split('T')[0];
            const currentMonthStr = todayStr.substring(0, 7);

            // Filter logic
            let filtered = transactions.filter(t => {
                const descStr = (t.deskripsi || '').toLowerCase();
                const catStr = (t.kategori || '').toLowerCase();
                const nomStr = String(t.nominal || '');
                const matchesSearch = !search || descStr.includes(search) || catStr.includes(search) || nomStr.includes(search);
                const matchesType = (typeFilter === 'Semua') || (t.jenis === typeFilter);
                const matchesCat = (catFilter === 'Semua') || (t.kategori === catFilter);

                let matchesDate = true;
                const trxDate = t.tanggal || '';
                if (datePreset === 'today') {
                    matchesDate = (trxDate === todayStr);
                } else if (datePreset === 'week') {
                    matchesDate = (trxDate >= sevenDaysAgo && trxDate <= todayStr);
                } else if (datePreset === 'month') {
                    matchesDate = trxDate.startsWith(currentMonthStr);
                } else if (datePreset === 'custom' && dateFilter) {
                    matchesDate = (trxDate === dateFilter);
                } else if (dateFilter) {
                    matchesDate = trxDate.startsWith(dateFilter);
                }

                return matchesSearch && matchesType && matchesCat && matchesDate;
            });

            // Sorting logic
            filtered.sort((a, b) => {
                if (sortOrder === 'newest') {
                    const dateComp = (b.tanggal || '').localeCompare(a.tanggal || '');
                    return dateComp !== 0 ? dateComp : (b.id || 0) - (a.id || 0);
                } else if (sortOrder === 'oldest') {
                    const dateComp = (a.tanggal || '').localeCompare(b.tanggal || '');
                    return dateComp !== 0 ? dateComp : (a.id || 0) - (b.id || 0);
                } else if (sortOrder === 'amount_desc') {
                    return (Number(b.nominal) || 0) - (Number(a.nominal) || 0);
                } else if (sortOrder === 'amount_asc') {
                    return (Number(a.nominal) || 0) - (Number(b.nominal) || 0);
                }
                return 0;
            });

            const filteredMasuk = filtered.filter(t => t.jenis === 'masuk').reduce((acc, t) => acc + (Number(t.nominal) || 0), 0);
            const filteredKeluar = filtered.filter(t => t.jenis === 'keluar').reduce((acc, t) => acc + (Number(t.nominal) || 0), 0);

            // Dynamic Categories for Filter
            const allCats = ['Semua', ...new Set(transactions.map(t => t.kategori || 'Umum'))];
            const isAnyFilterActive = search || typeFilter !== 'Semua' || catFilter !== 'Semua' || datePreset !== 'all' || dateFilter || sortOrder !== 'newest';

            let html = `
                <div class="container-fluid px-0">
                    ${dol}{renderManajemenKeuanganSubNav('savings')}

                    <!-- Target Tabungan Goal Card (Responsive for Mobile & Desktop) -->
                    <div class="card card-savings-goal p-3 p-sm-4 mb-4 border-0 shadow-sm">
                        <div class="d-flex flex-column flex-sm-row justify-content-between align-items-start align-items-sm-center gap-3 mb-3">
                            <div class="d-flex align-items-center gap-3 w-100 w-sm-auto">
                                <div class="bg-success text-white rounded-circle p-2 p-sm-3 d-flex align-items-center justify-content-center shadow-sm flex-shrink-0" style="width: 52px; height: 52px; font-size: 22px;">
                                    <i class="bi bi-piggy-bank"></i>
                                </div>
                                <div class="flex-grow-1">
                                    <div class="d-flex align-items-center gap-2 flex-wrap">
                                        <h4 class="fw-bold mb-0 fs-5 fs-sm-4">${dol}{escapeHtml(target.nama || 'Tabungan Utama')}</h4>
                                        <button class="btn btn-sm btn-outline-primary border-0 rounded-circle p-1" title="Atur Target Tabungan" onclick="openSetSavingTargetModal()">
                                            <i class="bi bi-pencil-square fs-6"></i>
                                        </button>
                                    </div>
                                    <div class="text-muted small mt-1"><i class="bi bi-flag me-1"></i> Target Goal: <b class="text-body">${dol}{mkFormatRupiah(targetAmount)}</b></div>
                                </div>
                            </div>
                            <div class="d-flex flex-wrap align-items-center gap-2 w-100 w-sm-auto justify-content-between justify-content-sm-end">
                                <span class="badge bg-success fs-6 px-3 py-2 rounded-pill">${dol}{progressPercent}% Terkumpul</span>
                                <button class="btn btn-success fw-bold px-3 py-2 shadow-sm flex-grow-1 flex-sm-grow-0" onclick="openAddSavingModal()">
                                    <i class="bi bi-plus-lg me-1"></i> Tambah Transaksi
                                </button>
                            </div>
                        </div>

                        <!-- Target Progress Bar -->
                        <div class="progress mb-2" style="height: 14px; border-radius: 10px;">
                            <div class="progress-bar bg-success progress-bar-striped progress-bar-animated" role="progressbar" style="width: ${dol}{progressPercent}%;"></div>
                        </div>
                        <div class="d-flex flex-column flex-sm-row justify-content-between small text-muted gap-1">
                            <span>Terkumpul: <b class="text-success">${dol}{mkFormatRupiah(currentBalance)}</b></span>
                            <span>Sisa Menuju Target: <b class="text-danger">${dol}{mkFormatRupiah(remainingTarget)}</b></span>
                        </div>
                    </div>

                    <!-- 3 Stat Summary Cards (Responsive Columns with Harmonized Light/Dark Borders) -->
                    <div class="row g-3 mb-4">
                        <div class="col-12 col-sm-4">
                            <div class="card stat-card-saldo p-3 h-100 shadow-sm">
                                <div class="d-flex justify-content-between align-items-center mb-1">
                                    <span class="small fw-semibold text-muted text-uppercase">Sisa Saldo</span>
                                    <i class="bi bi-wallet2 text-primary fs-5 fs-sm-4"></i>
                                </div>
                                <h5 class="fw-bold text-primary mb-1 fs-5 fs-sm-4">${dol}{mkFormatRupiah(currentBalance)}</h5>
                                <small class="text-muted"><i class="bi bi-shield-check me-1"></i> Total tabungan aktif</small>
                            </div>
                        </div>
                        <div class="col-12 col-sm-4">
                            <div class="card stat-card-masuk p-3 h-100 shadow-sm">
                                <div class="d-flex justify-content-between align-items-center mb-1">
                                    <span class="small fw-semibold text-muted text-uppercase">Total Masuk</span>
                                    <i class="bi bi-arrow-down-left-circle text-warning fs-5 fs-sm-4"></i>
                                </div>
                                <h5 class="fw-bold text-warning mb-1 fs-5 fs-sm-4">${dol}{mkFormatRupiah(totalMasuk)}</h5>
                                <small class="text-muted"><i class="bi bi-plus-circle me-1"></i> Total uang masuk</small>
                            </div>
                        </div>
                        <div class="col-12 col-sm-4">
                            <div class="card stat-card-keluar p-3 h-100 shadow-sm">
                                <div class="d-flex justify-content-between align-items-center mb-1">
                                    <span class="small fw-semibold text-muted text-uppercase">Total Keluar</span>
                                    <i class="bi bi-arrow-up-right-circle text-danger fs-5 fs-sm-4"></i>
                                </div>
                                <h5 class="fw-bold text-danger mb-1 fs-5 fs-sm-4">${dol}{mkFormatRupiah(totalKeluar)}</h5>
                                <small class="text-muted"><i class="bi bi-dash-circle me-1"></i> Pengeluaran tabungan</small>
                            </div>
                        </div>
                    </div>

                    <!-- 2 Interactive Offline Charts (Proporsi Saldo & Aliran Kas) -->
                    <div class="row g-3 g-md-4 mb-4">
                        <!-- Left Column: Kartu Aliran Kas -->
                        <div class="col-12 col-xl-7">
                            <div class="card card-custom p-3 p-sm-4 shadow-sm h-100 d-flex flex-column justify-content-between">
                                <div>
                                    <div class="d-flex flex-wrap justify-content-between align-items-center mb-3 gap-2">
                                        <div>
                                            <h6 class="fw-bold mb-1"><i class="bi bi-bar-chart-line text-primary me-2"></i> Kartu Aliran Kas (Uang Masuk vs Keluar)</h6>
                                            <small class="text-muted">Visualisasi grafik arus kas tabungan</small>
                                        </div>
                                        <span class="badge ${dol}{currentBalance >= 0 ? 'bg-success' : 'bg-danger'} px-3 py-2 rounded-pill fs-6">
                                            ${dol}{currentBalance >= 0 ? 'Surplus' : 'Defisit'}: ${dol}{currentBalance >= 0 ? '+' : ''}${dol}{mkFormatRupiah(currentBalance)}
                                        </span>
                                    </div>
                                    
                                    <div id="savingsCashflowChartBox" class="w-100 my-2" style="min-height: 180px;">
                                        ${dol}{generateSavingsCashflowSvg(transactions)}
                                    </div>
                                </div>

                                <div class="d-flex justify-content-center gap-4 small mt-2 pt-2 border-top">
                                    <span class="fw-semibold text-success"><i class="bi bi-square-fill me-1"></i> Uang Masuk</span>
                                    <span class="fw-semibold text-danger"><i class="bi bi-square-fill me-1"></i> Uang Keluar</span>
                                </div>
                            </div>
                        </div>

                        <!-- Right Column: Kartu Proporsi Saldo (Interactive Donut Chart matching TabunganScreen) -->
                        <div class="col-12 col-xl-5">
                            <div class="card card-custom p-3 p-sm-4 h-100 shadow-sm text-center d-flex flex-column justify-content-between">
                                <div>
                                    <div class="d-flex justify-content-between align-items-center mb-2">
                                        <h6 class="fw-bold mb-0"><i class="bi bi-pie-chart text-success me-2"></i> Kartu Proporsi Saldo</h6>
                                        <span class="badge bg-success bg-opacity-10 text-success border border-success border-opacity-25 small">100% Offline</span>
                                    </div>
                                    <p class="text-muted small mb-2">Sentuh bagian donat atau tombol di bawah untuk melihat detail</p>
                                    
                                    <div id="savingsProporsiDonutBox" class="d-flex justify-content-center align-items-center my-2" style="min-height: 200px;">
                                        ${dol}{generateSavingsProporsiDonutSvg(targetAmount, currentBalance, remainingTarget, totalMasuk, totalKeluar)}
                                    </div>
                                </div>

                                <!-- Interactive Legend Chips (Sisa Target, Total Masuk, Total Keluar) -->
                                <div class="d-flex flex-wrap justify-content-center gap-2 mt-3 pt-2 border-top">
                                    <button class="savings-legend-chip chip-sisa" onclick="selectSavingsProporsiSegment('sisa', '${dol}{mkFormatRupiah(remainingTarget)}')">
                                        <span style="display:inline-block;width:10px;height:10px;border-radius:50%;background:#2E7D32;"></span> Sisa: ${dol}{mkFormatRupiah(remainingTarget)}
                                    </button>
                                    <button class="savings-legend-chip chip-masuk" onclick="selectSavingsProporsiSegment('masuk', '${dol}{mkFormatRupiah(totalMasuk)}')">
                                        <span style="display:inline-block;width:10px;height:10px;border-radius:50%;background:#FBC02D;"></span> Masuk: ${dol}{mkFormatRupiah(totalMasuk)}
                                    </button>
                                    <button class="savings-legend-chip chip-keluar" onclick="selectSavingsProporsiSegment('keluar', '${dol}{mkFormatRupiah(totalKeluar)}')">
                                        <span style="display:inline-block;width:10px;height:10px;border-radius:50%;background:#C62828;"></span> Keluar: ${dol}{mkFormatRupiah(totalKeluar)}
                                    </button>
                                </div>
                            </div>
                        </div>
                    </div>

                    <!-- Filter Data Menu List Panel -->
                    <div class="card savings-filter-panel p-3 p-sm-4 shadow-sm mb-4">
                        <div class="d-flex flex-wrap justify-content-between align-items-center gap-2 mb-3 pb-2 border-bottom">
                            <div class="d-flex align-items-center gap-2">
                                <i class="bi bi-funnel text-primary fs-5"></i>
                                <h6 class="fw-bold mb-0">Filter Data Catatan Tabungan</h6>
                            </div>
                            <div class="d-flex align-items-center gap-2">
                                <span class="badge bg-primary bg-opacity-10 text-primary border border-primary border-opacity-25 px-2 py-1 rounded-pill small">
                                    ${dol}{filtered.length} dari ${dol}{transactions.length} transaksi
                                </span>
                                ${dol}{isAnyFilterActive ? `<button class="btn btn-sm btn-outline-danger py-0 px-2 rounded-pill small" onclick="resetSavingsFilters()"><i class="bi bi-x-circle me-1"></i>Reset Filter</button>` : ''}
                            </div>
                        </div>

                        <!-- Menu List Tabs (Semua, Masuk, Keluar) -->
                        <div class="savings-menu-nav mb-3">
                            <button type="button" class="savings-menu-item ${dol}{typeFilter === 'Semua' ? 'active-all' : ''}" onclick="window.mkSavingsTypeFilter = 'Semua'; renderSavingsUi();">
                                <i class="bi bi-collection me-1"></i> Semua Transaksi
                                <span class="badge ${dol}{typeFilter === 'Semua' ? 'bg-white text-dark' : 'bg-secondary bg-opacity-25 text-body'} rounded-pill ms-1">${dol}{transactions.length}</span>
                            </button>
                            <button type="button" class="savings-menu-item ${dol}{typeFilter === 'masuk' ? 'active-masuk' : ''}" onclick="window.mkSavingsTypeFilter = 'masuk'; renderSavingsUi();">
                                <i class="bi bi-arrow-down-left-circle-fill me-1 ${dol}{typeFilter === 'masuk' ? 'text-white' : 'text-success'}"></i> Uang Masuk
                                <span class="badge ${dol}{typeFilter === 'masuk' ? 'bg-white text-success' : 'bg-success bg-opacity-15 text-success'} rounded-pill ms-1">${dol}{countMasuk}</span>
                            </button>
                            <button type="button" class="savings-menu-item ${dol}{typeFilter === 'keluar' ? 'active-keluar' : ''}" onclick="window.mkSavingsTypeFilter = 'keluar'; renderSavingsUi();">
                                <i class="bi bi-arrow-up-right-circle-fill me-1 ${dol}{typeFilter === 'keluar' ? 'text-white' : 'text-danger'}"></i> Uang Keluar
                                <span class="badge ${dol}{typeFilter === 'keluar' ? 'bg-white text-danger' : 'bg-danger bg-opacity-15 text-danger'} rounded-pill ms-1">${dol}{countKeluar}</span>
                            </button>
                        </div>

                        <!-- Filter Controls Grid -->
                        <div class="row g-2 align-items-center">
                            <!-- Search Bar -->
                            <div class="col-12 col-md-4">
                                <div class="input-group input-group-sm">
                                    <span class="input-group-text bg-transparent"><i class="bi bi-search"></i></span>
                                    <input type="text" class="form-control" placeholder="Cari deskripsi / kategori / nominal..." value="${dol}{escapeHtml(window.mkSavingsSearch || '')}" oninput="window.mkSavingsSearch = this.value; renderSavingsUi();">
                                    ${dol}{search ? `<button class="btn btn-outline-secondary" onclick="window.mkSavingsSearch = ''; renderSavingsUi();"><i class="bi bi-x"></i></button>` : ''}
                                </div>
                            </div>

                            <!-- Sorting Dropdown -->
                            <div class="col-12 col-md-4">
                                <div class="input-group input-group-sm">
                                    <span class="input-group-text bg-transparent"><i class="bi bi-sort-down"></i></span>
                                    <select class="form-select" onchange="window.mkSavingsSort = this.value; renderSavingsUi();">
                                        <option value="newest" ${dol}{sortOrder === 'newest' ? 'selected' : ''}>Urut: Tanggal Terbaru</option>
                                        <option value="oldest" ${dol}{sortOrder === 'oldest' ? 'selected' : ''}>Urut: Tanggal Terlama</option>
                                        <option value="amount_desc" ${dol}{sortOrder === 'amount_desc' ? 'selected' : ''}>Nominal Terbesar</option>
                                        <option value="amount_asc" ${dol}{sortOrder === 'amount_asc' ? 'selected' : ''}>Nominal Terkecil</option>
                                    </select>
                                </div>
                            </div>

                            <!-- Category Dropdown -->
                            <div class="col-12 col-md-4">
                                <div class="input-group input-group-sm">
                                    <span class="input-group-text bg-transparent"><i class="bi bi-tag"></i></span>
                                    <select class="form-select" onchange="window.mkSavingsCategoryFilter = this.value; renderSavingsUi();">
            `;
            allCats.forEach(c => {
                const sel = (c === catFilter) ? 'selected' : '';
                html += `<option value="${dol}{escapeHtml(c)}" ${dol}{sel}>Kategori: ${dol}{escapeHtml(c)}</option>`;
            });
            html += `
                                    </select>
                                </div>
                            </div>

                            <!-- Date Filter Row -->
                            <div class="col-12 col-md-6 mt-2">
                                <div class="input-group input-group-sm">
                                    <span class="input-group-text bg-transparent"><i class="bi bi-calendar-range"></i></span>
                                    <select class="form-select" onchange="window.mkSavingsDatePreset = this.value; if(this.value !== 'custom') window.mkSavingsDateFilter = ''; renderSavingsUi();">
                                        <option value="all" ${dol}{datePreset === 'all' ? 'selected' : ''}>Semua Waktu</option>
                                        <option value="today" ${dol}{datePreset === 'today' ? 'selected' : ''}>Hari Ini</option>
                                        <option value="week" ${dol}{datePreset === 'week' ? 'selected' : ''}>7 Hari Terakhir</option>
                                        <option value="month" ${dol}{datePreset === 'month' ? 'selected' : ''}>Bulan Ini</option>
                                        <option value="custom" ${dol}{datePreset === 'custom' ? 'selected' : ''}>Pilih Tanggal Kustom...</option>
                                    </select>
                                </div>
                            </div>

                            <div class="col-12 col-md-6 mt-2">
                                <div class="d-flex align-items-center gap-2">
                                    <input type="date" class="form-control form-control-sm" value="${dol}{escapeHtml(dateFilter)}" onchange="window.mkSavingsDatePreset = 'custom'; window.mkSavingsDateFilter = this.value; renderSavingsUi();">
                                    <button class="btn btn-sm btn-outline-primary fw-semibold px-3 flex-shrink-0" onclick="window.print()" title="Cetak Rekap"><i class="bi bi-printer me-1"></i> Cetak</button>
                                </div>
                            </div>
                        </div>

                        <!-- Active Filter Summary Bar -->
                        ${dol}{isAnyFilterActive ? `
                            <div class="d-flex flex-wrap align-items-center gap-2 mt-3 pt-2 border-top small text-muted">
                                <span><i class="bi bi-info-circle me-1"></i> Ringkasan Filter:</span>
                                <span class="badge bg-success bg-opacity-10 text-success border border-success border-opacity-25">+ Masuk: ${dol}{mkFormatRupiah(filteredMasuk)}</span>
                                <span class="badge bg-danger bg-opacity-10 text-danger border border-danger border-opacity-25">- Keluar: ${dol}{mkFormatRupiah(filteredKeluar)}</span>
                            </div>
                        ` : ''}
                    </div>

                    <!-- Transactions List Table (Exclusively Table for All Devices, Fully Responsive) -->
                    <div class="card card-custom p-3 p-sm-4 shadow-sm mb-4">
                        <div class="d-flex flex-wrap justify-content-between align-items-center gap-2 mb-3">
                            <div class="d-flex align-items-center gap-2">
                                <i class="bi bi-table text-primary fs-5"></i>
                                <h6 class="fw-bold mb-0">Daftar Riwayat Transaksi Tabungan</h6>
                            </div>
                            <span class="badge bg-secondary bg-opacity-25 text-body fw-bold">${dol}{filtered.length} Transaksi Ditampilkan</span>
                        </div>

                        <!-- Fully Responsive Table with Touch-Friendly Scroll -->
                        <div class="table-responsive">
                            <table class="table table-savings table-hover align-middle mb-0">
                                <thead class="table-light">
                                    <tr>
                                        <th scope="col" style="min-width: 130px;">Tipe</th>
                                        <th scope="col" style="min-width: 110px;">Kategori</th>
                                        <th scope="col" style="min-width: 180px;">Deskripsi / Keterangan</th>
                                        <th scope="col" style="min-width: 120px;">Tanggal</th>
                                        <th scope="col" class="text-end" style="min-width: 130px;">Nominal</th>
                                        <th scope="col" class="text-end" style="min-width: 100px;">Aksi</th>
                                    </tr>
                                </thead>
                                <tbody>
            `;

            if (filtered.length === 0) {
                html += `<tr><td colspan="6" class="text-center py-5 text-muted"><i class="bi bi-wallet-fill fs-1 d-block mb-2 opacity-50"></i>Tidak ada transaksi tabungan yang cocok dengan filter.</td></tr>`;
            } else {
                filtered.forEach(trx => {
                    const isMasuk = trx.jenis === 'masuk';
                    const iconClass = isMasuk ? 'bi-arrow-down-left-circle-fill text-success' : 'bi-arrow-up-right-circle-fill text-danger';
                    const badgeClass = isMasuk ? 'bg-success bg-opacity-10 text-success border border-success border-opacity-25' : 'bg-danger bg-opacity-10 text-danger border border-danger border-opacity-25';
                    const nominalColor = isMasuk ? 'text-success' : 'text-danger';
                    const prefix = isMasuk ? '+ ' : '- ';

                    html += `
                        <tr>
                            <td>
                                <span class="badge ${dol}{badgeClass} px-2 px-sm-3 py-1 py-sm-2 rounded-pill fw-semibold">
                                    <i class="bi ${dol}{iconClass} me-1"></i> ${dol}{isMasuk ? 'Uang Masuk' : 'Uang Keluar'}
                                </span>
                            </td>
                            <td>
                                <span class="badge bg-secondary bg-opacity-25 text-body fw-bold">${dol}{escapeHtml(trx.kategori || 'Umum')}</span>
                            </td>
                            <td>
                                <div class="fw-semibold text-break">${dol}{escapeHtml(trx.deskripsi || '-')}</div>
                            </td>
                            <td>
                                <span class="text-muted small"><i class="bi bi-calendar3 me-1"></i>${dol}{mkFormatDateIndo(trx.tanggal)}</span>
                            </td>
                            <td class="text-end fw-bold fs-6 ${dol}{nominalColor}">
                                ${dol}{prefix}${dol}{mkFormatRupiah(trx.nominal)}
                            </td>
                            <td class="text-end text-nowrap">
                                <button class="btn btn-sm btn-outline-primary py-1 px-2 me-1" onclick="openEditSavingModal(${dol}{trx.id})" title="Edit Transaksi">
                                    <i class="bi bi-pencil"></i>
                                </button>
                                <button class="btn btn-sm btn-outline-danger py-1 px-2" onclick="deleteSavingTransaction(${dol}{trx.id})" title="Hapus Transaksi">
                                    <i class="bi bi-trash"></i>
                                </button>
                            </td>
                        </tr>
                    `;
                });
            }

            html += `
                                </tbody>
                            </table>
                        </div>
                    </div>
                </div>
            `;

            container.innerHTML = html;
        }

        function generateSavingsProporsiDonutSvg(targetAmount, currentBalance, remainingTarget, totalMasuk, totalKeluar) {
            const valSisa = Math.max(0, remainingTarget || 0);
            const valMasuk = Math.max(0, totalMasuk || 0);
            const valKeluar = Math.max(0, totalKeluar || 0);
            const total = valSisa + valMasuk + valKeluar;

            const cx = 110, cy = 110, R = 90, r = 58;

            if (total === 0) {
                return `
                    <svg viewBox="0 0 220 220" width="220" height="220" style="max-width: 100%;">
                        <circle cx="${dol}{cx}" cy="${dol}{cy}" r="${dol}{(R + r) / 2}" fill="none" class="donut-empty-circle" stroke="#e2e8f0" stroke-width="${dol}{R - r}" />
                        <text x="${dol}{cx}" y="${dol}{cy - 6}" text-anchor="middle" font-size="12" font-weight="600" class="donut-center-label" fill="#94a3b8">Belum Ada Data</text>
                        <text x="${dol}{cx}" y="${dol}{cy + 16}" text-anchor="middle" font-size="14" font-weight="bold" class="donut-center-percent" fill="#64748b">Rp 0</text>
                    </svg>
                `;
            }

            const items = [
                { id: 'sisa', label: 'Sisa Target', val: valSisa, color: '#2E7D32' },
                { id: 'masuk', label: 'Total Masuk', val: valMasuk, color: '#FBC02D' },
                { id: 'keluar', label: 'Total Keluar', val: valKeluar, color: '#C62828' }
            ].filter(item => item.val > 0);

            let currentAngle = -Math.PI / 2;
            let pathsHtml = '';

            items.forEach((item, idx) => {
                const sliceAngle = (item.val / total) * (Math.PI * 2);
                const endAngle = currentAngle + sliceAngle;

                // Handle full 360 circle
                if (sliceAngle >= Math.PI * 2 - 0.001) {
                    pathsHtml += `
                        <circle cx="${dol}{cx}" cy="${dol}{cy}" r="${dol}{(R + r) / 2}" fill="none" stroke="${dol}{item.color}" stroke-width="${dol}{R - r}"
                            style="cursor: pointer; transition: all 0.3s;"
                            onclick="selectSavingsProporsiSegment('${dol}{item.id}', '${dol}{mkFormatRupiah(item.val)}', '${dol}{item.color}', '${dol}{Math.round((item.val / total) * 100)}%')" />
                    `;
                } else {
                    const x1 = cx + R * Math.cos(currentAngle);
                    const y1 = cy + R * Math.sin(currentAngle);
                    const x2 = cx + R * Math.cos(endAngle);
                    const y2 = cy + R * Math.sin(endAngle);

                    const x3 = cx + r * Math.cos(endAngle);
                    const y3 = cy + r * Math.sin(endAngle);
                    const x4 = cx + r * Math.cos(currentAngle);
                    const y4 = cy + r * Math.sin(currentAngle);

                    const largeArc = sliceAngle > Math.PI ? 1 : 0;
                    const d = `M ${dol}{x1} ${dol}{y1} A ${dol}{R} ${dol}{R} 0 ${dol}{largeArc} 1 ${dol}{x2} ${dol}{y2} L ${dol}{x3} ${dol}{y3} A ${dol}{r} ${dol}{r} 0 ${dol}{largeArc} 0 ${dol}{x4} ${dol}{y4} Z`;

                    const percent = Math.round((item.val / total) * 100);
                    pathsHtml += `
                        <path d="${dol}{d}" fill="${dol}{item.color}" id="proporsiArc_${dol}{item.id}"
                            style="cursor: pointer; transition: opacity 0.2s; filter: drop-shadow(0 2px 4px rgba(0,0,0,0.08));"
                            onmouseover="this.style.opacity='0.85'" onmouseout="this.style.opacity='1'"
                            onclick="selectSavingsProporsiSegment('${dol}{item.id}', '${dol}{mkFormatRupiah(item.val)}', '${dol}{item.color}', '${dol}{percent}%')">
                            <title>${dol}{item.label}: ${dol}{mkFormatRupiah(item.val)} (${dol}{percent}%)</title>
                        </path>
                    `;
                }
                currentAngle = endAngle;
            });

            const defaultPercent = Math.round((valSisa / total) * 100);

            return `
                <svg viewBox="0 0 220 220" width="220" height="220" style="max-width: 100%;">
                    <circle cx="${dol}{cx}" cy="${dol}{cy}" r="${dol}{r - 2}" class="donut-center-circle" fill="var(--card-bg, #ffffff)" />
                    ${dol}{pathsHtml}
                    <!-- Center Display -->
                    <text id="proporsiCenterLabel" class="donut-center-label" x="${dol}{cx}" y="${dol}{cy - 12}" text-anchor="middle" font-size="11" font-weight="600" fill="#64748b">Sisa Target</text>
                    <text id="proporsiCenterValue" class="donut-center-value" x="${dol}{cx}" y="${dol}{cy + 10}" text-anchor="middle" font-size="13" font-weight="bold" fill="#2E7D32">${dol}{mkFormatRupiah(valSisa)}</text>
                    <text id="proporsiCenterPercent" class="donut-center-percent" x="${dol}{cx}" y="${dol}{cy + 26}" text-anchor="middle" font-size="10" font-weight="600" fill="#94a3b8">${dol}{defaultPercent}%</text>
                </svg>
            `;
        }

        function generateSavingsCashflowSvg(transactions) {
            if (!transactions || transactions.length === 0) {
                return `
                    <div class="d-flex flex-column align-items-center justify-content-center py-4 text-muted">
                        <i class="bi bi-graph-up-arrow fs-2 mb-2 opacity-50"></i>
                        <small>Belum ada riwayat aliran kas tabungan.</small>
                    </div>
                `;
            }

            // Group by date (take up to 7 distinct recent dates)
            const dateMap = {};
            transactions.forEach(t => {
                const d = t.tanggal || 'Hari Ini';
                if (!dateMap[d]) dateMap[d] = { masuk: 0, keluar: 0 };
                if (t.jenis === 'masuk') dateMap[d].masuk += (Number(t.nominal) || 0);
                else if (t.jenis === 'keluar') dateMap[d].keluar += (Number(t.nominal) || 0);
            });

            const dates = Object.keys(dateMap).sort().slice(-7);
            if (dates.length === 0) {
                return `<div class="text-center py-3 text-muted small">Belum ada data grafik</div>`;
            }

            let maxVal = 1;
            dates.forEach(d => {
                maxVal = Math.max(maxVal, dateMap[d].masuk, dateMap[d].keluar);
            });

            const W = 460, H = 160;
            const padL = 50, padR = 20, padT = 20, padB = 35;
            const chartW = W - padL - padR;
            const chartH = H - padT - padB;
            const slotW = chartW / dates.length;
            const barW = Math.max(6, Math.min(16, (slotW - 12) / 2));

            let svgContent = '';

            // Gridlines & Y-axis labels
            const gridCount = 3;
            for (let i = 0; i <= gridCount; i++) {
                const y = padT + (chartH / gridCount) * i;
                const val = maxVal * (1 - i / gridCount);
                svgContent += `
                    <line class="cashflow-gridline" x1="${dol}{padL}" y1="${dol}{y}" x2="${dol}{W - padR}" y2="${dol}{y}" stroke="#f1f5f9" stroke-width="1" />
                    <text class="cashflow-axis-text" x="${dol}{padL - 6}" y="${dol}{y + 4}" text-anchor="end" font-size="9" fill="#94a3b8">${dol}{mkFormatRupiah(val, true)}</text>
                `;
            }

            // Draw grouped bars for each date
            dates.forEach((d, idx) => {
                const centerX = padL + idx * slotW + slotW / 2;
                const item = dateMap[d];

                const masukH = (item.masuk / maxVal) * chartH;
                const keluarH = (item.keluar / maxVal) * chartH;

                const masukY = padT + chartH - masukH;
                const keluarY = padT + chartH - keluarH;

                const masukX = centerX - barW - 2;
                const keluarX = centerX + 2;

                // Masuk bar (Green)
                if (item.masuk > 0) {
                    svgContent += `
                        <rect x="${dol}{masukX}" y="${dol}{masukY}" width="${dol}{barW}" height="${dol}{masukH}" rx="3" fill="#2E7D32"
                            style="transition: all 0.2s; cursor: pointer;">
                            <title>${dol}{d} - Uang Masuk: ${dol}{mkFormatRupiah(item.masuk)}</title>
                        </rect>
                    `;
                }

                // Keluar bar (Red)
                if (item.keluar > 0) {
                    svgContent += `
                        <rect x="${dol}{keluarX}" y="${dol}{keluarY}" width="${dol}{barW}" height="${dol}{keluarH}" rx="3" fill="#C62828"
                            style="transition: all 0.2s; cursor: pointer;">
                            <title>${dol}{d} - Uang Keluar: ${dol}{mkFormatRupiah(item.keluar)}</title>
                        </rect>
                    `;
                }

                // X-axis label (formatted date)
                const dateParts = d.split('-');
                const labelText = dateParts.length === 3 ? (dateParts[2] + '/' + dateParts[1]) : d;
                svgContent += `
                    <text class="cashflow-axis-text" x="${dol}{centerX}" y="${dol}{H - 12}" text-anchor="middle" font-size="9" fill="#64748b">${dol}{labelText}</text>
                `;
            });

            return `
                <svg viewBox="0 0 ${dol}{W} ${dol}{H}" width="100%" height="160" style="overflow: visible;">
                    ${dol}{svgContent}
                </svg>
            `;
        }

        function selectSavingsProporsiSegment(type, valText, color, percent) {
            const titleEl = document.getElementById('proporsiCenterLabel');
            const valEl = document.getElementById('proporsiCenterValue');
            const pctEl = document.getElementById('proporsiCenterPercent');

            const labelMap = {
                sisa: 'Sisa Target',
                masuk: 'Total Masuk',
                keluar: 'Total Keluar'
            };
            const isDark = document.documentElement.getAttribute('data-theme') === 'dark' || document.body.classList.contains('dark');
            const colorMap = {
                sisa: isDark ? '#4ade80' : '#2E7D32',
                masuk: isDark ? '#fbbf24' : '#FBC02D',
                keluar: isDark ? '#f87171' : '#C62828'
            };

            if (titleEl) titleEl.textContent = labelMap[type] || 'Proporsi';
            if (valEl) {
                if (valText) valEl.textContent = valText;
                valEl.setAttribute('fill', color || colorMap[type] || (isDark ? '#4ade80' : '#2E7D32'));
            }
            if (pctEl && percent) {
                pctEl.textContent = percent;
            }
        }

        function openSetSavingTargetModal() {
            const modalId = 'modalSetSavingTarget';
            let modalEl = document.getElementById(modalId);
            if (modalEl) modalEl.remove();

            const target = window.mkSavingsTarget || { nama: 'Tabungan Utama', jumlah: 0 };
            const html = `
                <div class="modal fade" id="${dol}{modalId}" tabindex="-1">
                    <div class="modal-dialog modal-dialog-centered">
                        <div class="modal-content card-custom">
                            <div class="modal-header border-bottom">
                                <h5 class="modal-title fw-bold"><i class="bi bi-piggy-bank text-primary me-2"></i> Atur Target Celengan Tabungan</h5>
                                <button type="button" class="btn-close" data-bs-dismiss="modal"></button>
                            </div>
                            <div class="modal-body">
                                <div class="mb-3">
                                    <label class="form-label small fw-bold">Nama Target Impian <span class="text-danger">*</span></label>
                                    <input type="text" id="inpTargetNama" class="form-control" value="${dol}{escapeHtml(target.nama || '')}" placeholder="Contoh: Beli Laptop Baru / Tabungan Umroh">
                                </div>
                                <div class="mb-3">
                                    <label class="form-label small fw-bold">Nominal Target Goal (Rp) <span class="text-danger">*</span></label>
                                    <input type="number" id="inpTargetJumlah" class="form-control" value="${dol}{target.jumlah || ''}" placeholder="Contoh: 15000000">
                                </div>
                                <div class="d-flex justify-content-end gap-2 pt-2 border-top">
                                    <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">Batal</button>
                                    <button type="button" class="btn btn-primary fw-bold" onclick="saveSavingTarget()"><i class="bi bi-check2 me-1"></i> Simpan Target</button>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            `;
            document.body.insertAdjacentHTML('beforeend', html);
            const modal = new bootstrap.Modal(document.getElementById(modalId));
            modal.show();
        }

        async function saveSavingTarget() {
            const nama = document.getElementById('inpTargetNama').value.trim();
            const jumlah = parseFloat(document.getElementById('inpTargetJumlah').value) || 0;
            if (!nama) return alert('Nama target wajib diisi!');

            try {
                await fetch('/api/savings', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ action: 'set_target', targetNama: nama, targetJumlah: jumlah })
                });
                const modalEl = document.getElementById('modalSetSavingTarget');
                if (modalEl) {
                    const modal = bootstrap.Modal.getInstance(modalEl);
                    if (modal) modal.hide();
                }
                await fetchSavingsData();
                renderSavingsUi();
            } catch (e) {
                alert('Gagal menyimpan target: ' + e.message);
            }
        }

        function openAddSavingModal() {
            openSavingFormModal(null);
        }

        function openEditSavingModal(trxId) {
            const trx = (window.mkSavingsTransactions || []).find(t => t.id === trxId);
            if (trx) openSavingFormModal(trx);
        }

        function openSavingFormModal(item) {
            const isEdit = !!item;
            const title = isEdit ? 'Edit Transaksi Tabungan' : 'Tambah Transaksi Tabungan';
            const modalId = 'modalSavingForm';

            let modalEl = document.getElementById(modalId);
            if (modalEl) modalEl.remove();

            const curJenis = item ? item.jenis : 'masuk';
            const curDate = item ? item.tanggal : new Date().toISOString().split('T')[0];

            const html = `
                <div class="modal fade" id="${dol}{modalId}" tabindex="-1">
                    <div class="modal-dialog modal-dialog-centered">
                        <div class="modal-content card-custom">
                            <div class="modal-header border-bottom">
                                <h5 class="modal-title fw-bold"><i class="bi bi-wallet2 text-primary me-2"></i> ${dol}{title}</h5>
                                <button type="button" class="btn-close" data-bs-dismiss="modal"></button>
                            </div>
                            <div class="modal-body">
                                <form id="formSavingInput" onsubmit="event.preventDefault(); saveSavingForm(${dol}{item ? item.id : 'null'});">
                                    <div class="mb-3">
                                        <label class="form-label small fw-bold">Jenis Transaksi</label>
                                        <div class="d-flex gap-2">
                                            <div class="form-check">
                                                <input class="form-check-input" type="radio" name="inpTrxJenis" id="radioMasuk" value="masuk" ${dol}{curJenis === 'masuk' ? 'checked' : ''} onchange="updateSavingCategoryOptions('masuk')">
                                                <label class="form-check-label text-success fw-bold" for="radioMasuk">
                                                    <i class="bi bi-arrow-down-left-circle me-1"></i> Uang Masuk (Pemasukan)
                                                </label>
                                            </div>
                                            <div class="form-check ms-3">
                                                <input class="form-check-input" type="radio" name="inpTrxJenis" id="radioKeluar" value="keluar" ${dol}{curJenis === 'keluar' ? 'checked' : ''} onchange="updateSavingCategoryOptions('keluar')">
                                                <label class="form-check-label text-danger fw-bold" for="radioKeluar">
                                                    <i class="bi bi-arrow-up-right-circle me-1"></i> Uang Keluar (Pengeluaran)
                                                </label>
                                            </div>
                                        </div>
                                    </div>
                                    <div class="row g-2 mb-3">
                                        <div class="col-6">
                                            <label class="form-label small fw-bold">Kategori Transaksi</label>
                                            <select id="inpTrxKategori" class="form-select"></select>
                                        </div>
                                        <div class="col-6">
                                            <label class="form-label small fw-bold">Tanggal</label>
                                            <input type="date" id="inpTrxTanggal" class="form-control" required value="${dol}{escapeHtml(curDate)}">
                                        </div>
                                    </div>
                                    <div class="mb-3">
                                        <label class="form-label small fw-bold">Nominal Transaksi (Rp) <span class="text-danger">*</span></label>
                                        <input type="number" id="inpTrxNominal" class="form-control fs-5 fw-bold" required value="${dol}{item ? item.nominal : ''}" placeholder="0">
                                    </div>
                                    <div class="mb-3">
                                        <label class="form-label small fw-bold">Deskripsi / Catatan</label>
                                        <textarea id="inpTrxDeskripsi" class="form-control" rows="2" placeholder="Contoh: Gaji bulanan / Beli kuota internet">${dol}{item ? escapeHtml(item.deskripsi || '') : ''}</textarea>
                                    </div>
                                    <div class="d-flex justify-content-end gap-2 pt-2 border-top">
                                        <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">Batal</button>
                                        <button type="submit" class="btn btn-primary fw-bold"><i class="bi bi-save me-1"></i> Simpan Transaksi</button>
                                    </div>
                                </form>
                            </div>
                        </div>
                    </div>
                </div>
            `;
            document.body.insertAdjacentHTML('beforeend', html);
            const modal = new bootstrap.Modal(document.getElementById(modalId));
            modal.show();

            updateSavingCategoryOptions(curJenis, item ? item.kategori : null);
        }

        function updateSavingCategoryOptions(jenis, selectedVal) {
            const selectEl = document.getElementById('inpTrxKategori');
            if (!selectEl) return;
            const categories = jenis === 'masuk'
                ? ['Gaji', 'Bonus', 'Hadiah', 'Investasi', 'Lainnya']
                : ['Kebutuhan Pokok', 'Tagihan', 'Transportasi', 'Pendidikan', 'Gaya Hidup', 'Lainnya'];

            selectEl.innerHTML = '';
            categories.forEach(c => {
                const opt = document.createElement('option');
                opt.value = c;
                opt.innerText = c;
                if (c === selectedVal) opt.selected = true;
                selectEl.appendChild(opt);
            });
        }

        async function saveSavingForm(existingId) {
            const jenis = document.querySelector('input[name="inpTrxJenis"]:checked')?.value || 'masuk';
            const kategori = document.getElementById('inpTrxKategori').value;
            const tanggal = document.getElementById('inpTrxTanggal').value;
            const nominal = parseFloat(document.getElementById('inpTrxNominal').value) || 0;
            const deskripsi = document.getElementById('inpTrxDeskripsi').value.trim();

            if (nominal <= 0) return alert('Nominal harus lebih dari 0!');

            const payload = {
                jenis: jenis,
                kategori: kategori,
                tanggal: tanggal,
                nominal: nominal,
                deskripsi: deskripsi
            };

            const method = existingId ? 'PUT' : 'POST';
            if (existingId) payload.id = existingId;

            try {
                await fetch('/api/savings', {
                    method: method,
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(payload)
                });
                const modalEl = document.getElementById('modalSavingForm');
                if (modalEl) {
                    const modal = bootstrap.Modal.getInstance(modalEl);
                    if (modal) modal.hide();
                }
                await fetchSavingsData();
                renderSavingsUi();
            } catch (e) {
                alert('Gagal menyimpan transaksi: ' + e.message);
            }
        }

        async function deleteSavingTransaction(id) {
            if (!confirm('Apakah Anda yakin ingin menghapus transaksi ini?')) return;
            try {
                await fetch('/api/savings?id=' + encodeURIComponent(id), { method: 'DELETE' });
                await fetchSavingsData();
                renderSavingsUi();
            } catch (e) {
                alert('Gagal menghapus transaksi: ' + e.message);
            }
        }
        """
    }
}
