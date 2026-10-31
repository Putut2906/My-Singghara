package com.example.web

object RekapBelanjaPasarWebScript {
    fun getScript(dol: String): String {
        return """
        // ==========================================
        // --- REKAP BELANJA PASAR (WEB CONNECT PC) ---
        // ==========================================
        window.belanjaPasarList = [];
        window.belanjaPasarFilterCategory = 'Semua';
        window.belanjaPasarFilterStatus = 'Semua';
        window.belanjaPasarSearchQuery = '';

        async function fetchBelanjaPasarList() {
            try {
                const res = await fetch('/api/belanja_pasar');
                if (res.ok) {
                    window.belanjaPasarList = await res.json();
                    localStorage.setItem('cached_belanja_pasar', JSON.stringify(window.belanjaPasarList));
                } else {
                    const cached = localStorage.getItem('cached_belanja_pasar');
                    if (cached) window.belanjaPasarList = JSON.parse(cached);
                }
            } catch(e) {
                console.warn("Offline/Fetch error:", e);
                const cached = localStorage.getItem('cached_belanja_pasar');
                if (cached) window.belanjaPasarList = JSON.parse(cached);
            }
        }

        async function loadRekapBelanjaPasar() {
            const container = document.getElementById('tab-content') || document.getElementById('main-content');
            if (!container) return;

            container.innerHTML = `
                <div class="d-flex justify-content-center align-items-center py-5">
                    <div class="spinner-border text-success" role="status"></div>
                    <span class="ms-3 fw-semibold text-success">Memuat Rekap Belanja Pasar...</span>
                </div>
            `;

            await fetchBelanjaPasarList();
            renderRekapBelanjaPasarView();
        }

        function renderRekapBelanjaPasarView() {
            const container = document.getElementById('tab-content') || document.getElementById('main-content');
            if (!container) return;

            const items = window.belanjaPasarList || [];
            const totalCount = items.length;
            const boughtCount = items.filter(it => it.sudahDibeli).length;
            const progressPct = totalCount > 0 ? Math.round((boughtCount / totalCount) * 100) : 0;

            const totalEstimasi = items.reduce((acc, it) => acc + ((Number(it.perkiraanHarga) || 0) * (Number(it.jumlah) || 1)), 0);
            const totalRealisasi = items.filter(it => it.sudahDibeli).reduce((acc, it) => {
                const price = (Number(it.realisasiHarga) > 0) ? Number(it.realisasiHarga) : (Number(it.perkiraanHarga) || 0);
                return acc + (price * (Number(it.jumlah) || 1));
            }, 0);

            // Group by unit (kg, pcs, ikat, liter, dll.)
            const unitCounts = {};
            items.forEach(it => {
                const u = (it.satuan || 'pcs').trim().toLowerCase();
                const q = Number(it.jumlah) || 0;
                unitCounts[u] = (unitCounts[u] || 0) + q;
            });
            const unitBadges = Object.keys(unitCounts).sort().map(u => {
                const val = unitCounts[u];
                const formattedVal = (val % 1 === 0) ? val : val.toFixed(2).replace(/\.00${dol}/, '');
                return `<span class="badge rounded-pill bg-success-subtle text-success border border-success-subtle px-3 py-2 me-2 mb-2 font-monospace fs-6">
                    <strong>${dol}{formattedVal}</strong> ${dol}{u}
                </span>`;
            }).join('');

            // Filter items
            const filtered = items.filter(it => {
                const q = window.belanjaPasarSearchQuery.toLowerCase();
                const matchQuery = !q || (it.nama || '').toLowerCase().includes(q) || (it.catatan || '').toLowerCase().includes(q);
                const matchCat = window.belanjaPasarFilterCategory === 'Semua' || (it.kategori || '').toLowerCase() === window.belanjaPasarFilterCategory.toLowerCase();
                const matchStatus = window.belanjaPasarFilterStatus === 'Semua' ||
                    (window.belanjaPasarFilterStatus === 'Belum' && !it.sudahDibeli) ||
                    (window.belanjaPasarFilterStatus === 'Selesai' && it.sudahDibeli);
                return matchQuery && matchCat && matchStatus;
            });

            const categories = [
                "Semua", "Sayuran", "Daging & Unggas", "Ikan & Laut", "Bumbu & Rempah",
                "Buah-buahan", "Sembako", "Tahu & Tempe", "Perlengkapan", "Lain-lain"
            ];

            container.innerHTML = `
                <div class="container-fluid p-0">
                    <!-- HEADER BAR -->
                    <div class="card card-custom border-0 shadow-sm mb-4" style="background: linear-gradient(135deg, #10B981 0%, #059669 100%); color: white; border-radius: 20px;">
                        <div class="card-body p-4">
                            <div class="d-flex flex-wrap justify-content-between align-items-center gap-3">
                                <div>
                                    <div class="d-flex align-items-center gap-3">
                                        <div class="bg-white text-success rounded-4 d-flex align-items-center justify-content-center shadow-sm" style="width: 52px; height: 52px;">
                                            <i class="bi bi-basket3-fill fs-2 text-success"></i>
                                        </div>
                                        <div>
                                            <h3 class="fw-bold mb-0">Rekap Belanja Pasar</h3>
                                            <small class="text-white-50">Daftar belanja pasar lengkap dalam satuan kg, pcs, ikat, liter, dll.</small>
                                        </div>
                                    </div>
                                </div>
                                <div class="d-flex flex-wrap gap-2">
                                    <button class="btn btn-light text-success fw-bold shadow-sm d-flex align-items-center gap-2" onclick="openBelanjaPasarModal()">
                                        <i class="bi bi-plus-circle-fill"></i> Tambah Belanjaan
                                    </button>
                                    <button class="btn btn-outline-light d-flex align-items-center gap-2" onclick="printRekapBelanjaPasar()">
                                        <i class="bi bi-printer-fill"></i> Cetak Rekap
                                    </button>
                                    <button class="btn btn-outline-light d-flex align-items-center gap-2" onclick="shareBelanjaPasarWhatsApp()">
                                        <i class="bi bi-whatsapp"></i> Bagikan WA
                                    </button>
                                    ${dol}{boughtCount > 0 ? `
                                        <button class="btn btn-danger btn-sm text-white d-flex align-items-center gap-1" onclick="clearCompletedBelanjaPasar()">
                                            <i class="bi bi-trash-fill"></i> Bersihkan Selesai
                                        </button>
                                    ` : ''}
                                </div>
                            </div>

                            <!-- STATS CARDS -->
                            <div class="row g-3 mt-3">
                                <div class="col-12 col-md-4">
                                    <div class="p-3 bg-white bg-opacity-10 rounded-3 border border-white border-opacity-25 h-100">
                                        <div class="d-flex justify-content-between align-items-center mb-1">
                                            <small class="text-white-50 text-uppercase fw-semibold">Progres Belanja</small>
                                            <span class="badge bg-white text-success fw-bold">${dol}{progressPct}%</span>
                                        </div>
                                        <h4 class="fw-bold mb-2">${dol}{boughtCount} / ${dol}{totalCount} Item</h4>
                                        <div class="progress" style="height: 6px; background-color: rgba(255,255,255,0.25);">
                                            <div class="progress-bar bg-white" style="width: ${dol}{progressPct}%;"></div>
                                        </div>
                                    </div>
                                </div>
                                <div class="col-6 col-md-4">
                                    <div class="p-3 bg-white bg-opacity-10 rounded-3 border border-white border-opacity-25 h-100">
                                        <small class="text-white-50 text-uppercase fw-semibold">Total Estimasi Biaya</small>
                                        <h4 class="fw-bold mb-0">Rp ${dol}{totalEstimasi.toLocaleString('id-ID')}</h4>
                                        <small class="text-white-50" style="font-size: 11px;">Rencana pengeluaran pasar</small>
                                    </div>
                                </div>
                                <div class="col-6 col-md-4">
                                    <div class="p-3 bg-white bg-opacity-10 rounded-3 border border-white border-opacity-25 h-100">
                                        <small class="text-white-50 text-uppercase fw-semibold">Realisasi Terbeli</small>
                                        <h4 class="fw-bold mb-0 text-warning">Rp ${dol}{totalRealisasi.toLocaleString('id-ID')}</h4>
                                        <small class="text-white-50" style="font-size: 11px;">Aktual belanja yang sudah dicentang</small>
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div>

                    <!-- REKAP PER SATUAN SECTION -->
                    ${dol}{unitBadges ? `
                        <div class="card card-custom border-0 shadow-sm mb-4">
                            <div class="card-body p-3">
                                <div class="d-flex align-items-center gap-2 mb-2">
                                    <i class="bi bi-pie-chart-fill text-success"></i>
                                    <span class="fw-bold small text-uppercase text-secondary">Rekapitulasi Kuantitas per Satuan:</span>
                                </div>
                                <div class="d-flex flex-wrap align-items-center">
                                    ${dol}{unitBadges}
                                </div>
                            </div>
                        </div>
                    ` : ''}

                    <!-- SEARCH & FILTER ROW (Menu list di sebelah kiri icon kaca pembesar) -->
                    <div class="card card-custom border-0 shadow-sm mb-4">
                        <div class="card-body p-3">
                            <div class="row g-2 align-items-center">
                                <!-- Filter Status (Menu List - sebelah kiri kaca pembesar) -->
                                <div class="col-12 col-sm-6 col-md-3">
                                    <div class="input-group input-group-sm">
                                        <span class="input-group-text bg-light text-secondary border-end-0"><i class="bi bi-funnel-fill text-success"></i></span>
                                        <select class="form-select bg-light border-start-0 fw-semibold" id="filterStatusBelanja" onchange="setBelanjaPasarStatusFilter(this.value)">
                                            <option value="Semua" ${dol}{window.belanjaPasarFilterStatus === 'Semua' ? 'selected' : ''}>Semua Status</option>
                                            <option value="Belum" ${dol}{window.belanjaPasarFilterStatus === 'Belum' ? 'selected' : ''}>Belum Dibeli</option>
                                            <option value="Selesai" ${dol}{window.belanjaPasarFilterStatus === 'Selesai' ? 'selected' : ''}>Sudah Dibeli</option>
                                        </select>
                                    </div>
                                </div>

                                <!-- Filter Kategori (Menu List - sebelah kiri kaca pembesar) -->
                                <div class="col-12 col-sm-6 col-md-4">
                                    <div class="input-group input-group-sm">
                                        <span class="input-group-text bg-light text-secondary border-end-0"><i class="bi bi-tag-fill text-success"></i></span>
                                        <select class="form-select bg-light border-start-0 fw-semibold" id="filterKategoriBelanja" onchange="setBelanjaPasarCategoryFilter(this.value)">
                                            ${dol}{categories.map(c => `
                                                <option value="${dol}{c}" ${dol}{window.belanjaPasarFilterCategory === c ? 'selected' : ''}>Kategori: ${dol}{c}</option>
                                            `).join('')}
                                        </select>
                                    </div>
                                </div>

                                <!-- Search Box with Icon Kaca Pembesar (di sebelah kanan filter status dan kategori) -->
                                <div class="col-12 col-md-5">
                                    <div class="input-group input-group-sm">
                                        <span class="input-group-text bg-light border-end-0"><i class="bi bi-search text-secondary"></i></span>
                                        <input type="text" class="form-control bg-light border-start-0" id="searchBelanjaPasarInput" placeholder="Cari komoditas atau catatan pasar..." value="${dol}{escapeHtml(window.belanjaPasarSearchQuery)}" oninput="handleBelanjaPasarSearch(this.value)">
                                        ${dol}{window.belanjaPasarSearchQuery ? `
                                            <button class="btn btn-outline-secondary btn-sm" type="button" onclick="handleBelanjaPasarSearch(''); document.getElementById('searchBelanjaPasarInput').value='';">
                                                <i class="bi bi-x-lg"></i>
                                            </button>
                                        ` : ''}
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div>

                    <!-- ITEMS TABLE / LIST -->
                    <div class="card card-custom border-0 shadow-sm mb-4">
                        <div class="card-body p-0">
                            ${dol}{filtered.length === 0 ? `
                                <div class="text-center py-5 px-3">
                                    <div class="mx-auto text-success bg-success-subtle rounded-circle d-flex align-items-center justify-content-center mb-3" style="width: 72px; height: 72px;">
                                        <i class="bi bi-basket3 fs-1"></i>
                                    </div>
                                    <h5 class="fw-bold">Tidak ada item belanja pasar</h5>
                                    <p class="text-secondary small mb-3">Tekan tombol 'Tambah Belanjaan' untuk membuat daftar belanja dalam satuan kg, pcs, ikat, dll.</p>
                                    <button class="btn btn-success btn-sm rounded-pill px-4" onclick="openBelanjaPasarModal()">
                                        <i class="bi bi-plus-lg me-1"></i> Tambah Belanjaan Sekarang
                                    </button>
                                </div>
                            ` : `
                                <!-- Mobile View (Cards) -->
                                <div class="d-block d-md-none p-2">
                                    ${dol}{filtered.map(it => {
                                        const q = Number(it.jumlah) || 1;
                                        const qStr = (q % 1 === 0) ? q : q.toFixed(2).replace(/\.00${dol}/, '');
                                        const estUnit = Number(it.perkiraanHarga) || 0;
                                        const subtotal = estUnit * q;
                                        const isDone = !!it.sudahDibeli;

                                        let catBadgeClass = 'bg-secondary-subtle text-secondary';
                                        const catLower = (it.kategori || '').toLowerCase();
                                        if (catLower.includes('sayur')) catBadgeClass = 'bg-success-subtle text-success';
                                        else if (catLower.includes('daging')) catBadgeClass = 'bg-danger-subtle text-danger';
                                        else if (catLower.includes('ikan') || catLower.includes('laut')) catBadgeClass = 'bg-info-subtle text-info';
                                        else if (catLower.includes('bumbu')) catBadgeClass = 'bg-warning-subtle text-warning-emphasis';
                                        else if (catLower.includes('buah')) catBadgeClass = 'bg-purple-subtle text-purple';
                                        else if (catLower.includes('sembako')) catBadgeClass = 'bg-primary-subtle text-primary';

                                        return `
                                            <div class="card border rounded-3 p-3 mb-2 shadow-sm ${dol}{isDone ? 'bg-light opacity-75' : 'bg-white'}">
                                                <div class="d-flex align-items-start gap-2">
                                                    <input class="form-check-input fs-4 mt-0 cursor-pointer" type="checkbox" ${dol}{isDone ? 'checked' : ''} onchange="toggleBelanjaPasarItem(${dol}{it.id}, this.checked)">
                                                    <div class="flex-grow-1">
                                                        <div class="d-flex justify-content-between align-items-start gap-1">
                                                            <div class="${dol}{isDone ? 'text-decoration-line-through text-secondary' : 'fw-bold'} fs-6">
                                                                ${dol}{escapeHtml(it.nama)}
                                                            </div>
                                                            <span class="badge ${dol}{catBadgeClass} border">${dol}{escapeHtml(it.kategori || 'Sayuran')}</span>
                                                        </div>
                                                        ${dol}{it.catatan ? `<div class="small text-secondary mt-1"><i class="bi bi-info-circle me-1"></i>${dol}{escapeHtml(it.catatan)}</div>` : ''}
                                                        <div class="d-flex justify-content-between align-items-center mt-2 pt-2 border-top">
                                                            <div>
                                                                <span class="badge bg-success-subtle text-success border border-success-subtle font-monospace px-2 py-1">
                                                                    ${dol}{qStr} ${dol}{escapeHtml(it.satuan || 'kg')}
                                                                </span>
                                                                ${dol}{subtotal > 0 ? `<span class="small text-secondary ms-2">~ Rp ${dol}{subtotal.toLocaleString('id-ID')}</span>` : ''}
                                                            </div>
                                                            <div class="btn-group btn-group-sm">
                                                                <button class="btn btn-outline-secondary btn-sm py-0 px-2" onclick="openBelanjaPasarModal(${dol}{it.id})" title="Edit">
                                                                    <i class="bi bi-pencil-fill"></i>
                                                                </button>
                                                                <button class="btn btn-outline-danger btn-sm py-0 px-2" onclick="deleteBelanjaPasarItem(${dol}{it.id})" title="Hapus">
                                                                    <i class="bi bi-trash-fill"></i>
                                                                </button>
                                                            </div>
                                                        </div>
                                                    </div>
                                                </div>
                                            </div>
                                        `;
                                    }).join('')}
                                </div>

                                <!-- Desktop View (Table) -->
                                <div class="table-responsive d-none d-md-block">
                                    <table class="table table-hover align-middle mb-0">
                                        <thead class="table-light">
                                            <tr>
                                                <th style="width: 48px;" class="text-center">✓</th>
                                                <th>Nama Barang / Komoditas</th>
                                                <th class="text-center">Kategori</th>
                                                <th class="text-center">Kuantitas & Satuan</th>
                                                <th class="text-end">Estimasi Harga</th>
                                                <th class="text-end">Subtotal Est.</th>
                                                <th class="text-center" style="width: 100px;">Aksi</th>
                                            </tr>
                                        </thead>
                                        <tbody>
                                            ${dol}{filtered.map(it => {
                                                const q = Number(it.jumlah) || 1;
                                                const qStr = (q % 1 === 0) ? q : q.toFixed(2).replace(/\.00${dol}/, '');
                                                const estUnit = Number(it.perkiraanHarga) || 0;
                                                const subtotal = estUnit * q;
                                                const isDone = !!it.sudahDibeli;

                                                let catBadgeClass = 'bg-secondary-subtle text-secondary';
                                                const catLower = (it.kategori || '').toLowerCase();
                                                if (catLower.includes('sayur')) catBadgeClass = 'bg-success-subtle text-success';
                                                else if (catLower.includes('daging')) catBadgeClass = 'bg-danger-subtle text-danger';
                                                else if (catLower.includes('ikan') || catLower.includes('laut')) catBadgeClass = 'bg-info-subtle text-info';
                                                else if (catLower.includes('bumbu')) catBadgeClass = 'bg-warning-subtle text-warning-emphasis';
                                                else if (catLower.includes('buah')) catBadgeClass = 'bg-purple-subtle text-purple';
                                                else if (catLower.includes('sembako')) catBadgeClass = 'bg-primary-subtle text-primary';

                                                return `
                                                    <tr class="${dol}{isDone ? 'table-light opacity-75' : ''}">
                                                        <td class="text-center">
                                                            <input class="form-check-input fs-5 cursor-pointer" type="checkbox" ${dol}{isDone ? 'checked' : ''} onchange="toggleBelanjaPasarItem(${dol}{it.id}, this.checked)">
                                                        </td>
                                                        <td>
                                                            <div class="${dol}{isDone ? 'text-decoration-line-through text-secondary' : 'fw-bold'} fs-6">
                                                                ${dol}{escapeHtml(it.nama)}
                                                            </div>
                                                            ${dol}{it.catatan ? `<small class="text-secondary d-flex align-items-center gap-1"><i class="bi bi-info-circle"></i> ${dol}{escapeHtml(it.catatan)}</small>` : ''}
                                                        </td>
                                                        <td class="text-center">
                                                            <span class="badge ${dol}{catBadgeClass} border px-2 py-1">${dol}{escapeHtml(it.kategori || 'Sayuran')}</span>
                                                        </td>
                                                        <td class="text-center">
                                                            <span class="badge bg-success-subtle text-success border border-success-subtle px-2 py-1 fs-6 font-monospace">
                                                                <strong>${dol}{qStr}</strong> ${dol}{escapeHtml(it.satuan || 'kg')}
                                                            </span>
                                                        </td>
                                                        <td class="text-end text-secondary small">
                                                            ${dol}{estUnit > 0 ? 'Rp ' + estUnit.toLocaleString('id-ID') : '-'}
                                                        </td>
                                                        <td class="text-end fw-semibold">
                                                            ${dol}{subtotal > 0 ? 'Rp ' + subtotal.toLocaleString('id-ID') : '-'}
                                                        </td>
                                                        <td class="text-center">
                                                            <div class="btn-group btn-group-sm">
                                                                <button class="btn btn-outline-secondary" onclick="openBelanjaPasarModal(${dol}{it.id})" title="Edit Item">
                                                                    <i class="bi bi-pencil-fill"></i>
                                                                </button>
                                                                <button class="btn btn-outline-danger" onclick="deleteBelanjaPasarItem(${dol}{it.id})" title="Hapus Item">
                                                                    <i class="bi bi-trash-fill"></i>
                                                                </button>
                                                            </div>
                                                        </td>
                                                    </tr>
                                                `;
                                            }).join('')}
                                        </tbody>
                                    </table>
                                </div>
                            `}
                        </div>
                    </div>
                </div>
            `;
        }

        function handleBelanjaPasarSearch(val) {
            window.belanjaPasarSearchQuery = val;
            renderRekapBelanjaPasarView();
        }

        function setBelanjaPasarCategoryFilter(cat) {
            window.belanjaPasarFilterCategory = cat;
            renderRekapBelanjaPasarView();
        }

        function setBelanjaPasarStatusFilter(status) {
            window.belanjaPasarFilterStatus = status;
            renderRekapBelanjaPasarView();
        }

        async function toggleBelanjaPasarItem(id, sudahDibeli) {
            // Optimistic update
            const item = window.belanjaPasarList.find(it => it.id === id);
            if (item) item.sudahDibeli = sudahDibeli;
            renderRekapBelanjaPasarView();

            try {
                await fetch('/api/belanja_pasar?id=' + id, {
                    method: 'PUT',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ id: id, sudahDibeli: sudahDibeli })
                });
                localStorage.setItem('cached_belanja_pasar', JSON.stringify(window.belanjaPasarList));
            } catch(e) {
                console.error("Gagal update status belanja:", e);
            }
        }

        async function deleteBelanjaPasarItem(id) {
            if (!confirm("Hapus barang belanja ini dari daftar?")) return;

            window.belanjaPasarList = window.belanjaPasarList.filter(it => it.id !== id);
            renderRekapBelanjaPasarView();

            try {
                await fetch('/api/belanja_pasar?id=' + id, { method: 'DELETE' });
                localStorage.setItem('cached_belanja_pasar', JSON.stringify(window.belanjaPasarList));
            } catch(e) {
                console.error("Gagal menghapus belanja:", e);
            }
        }

        async function clearCompletedBelanjaPasar() {
            if (!confirm("Bersihkan semua item belanjaan yang sudah dicentang/dibeli?")) return;

            window.belanjaPasarList = window.belanjaPasarList.filter(it => !it.sudahDibeli);
            renderRekapBelanjaPasarView();

            try {
                await fetch('/api/belanja_pasar?clear_completed=true', { method: 'DELETE' });
                localStorage.setItem('cached_belanja_pasar', JSON.stringify(window.belanjaPasarList));
            } catch(e) {
                console.error("Gagal membersihkan belanjaan selesai:", e);
            }
        }

        // --- ADD / EDIT MODAL ---
        function openBelanjaPasarModal(id = null) {
            const item = id ? window.belanjaPasarList.find(it => it.id === id) : null;
            const isEdit = !!item;

            const popularCommodities = [
                { name: "Cabai Rawit", unit: "kg", cat: "Bumbu & Rempah" },
                { name: "Bawang Merah", unit: "kg", cat: "Bumbu & Rempah" },
                { name: "Bawang Putih", unit: "kg", cat: "Bumbu & Rempah" },
                { name: "Tomat", unit: "kg", cat: "Sayuran" },
                { name: "Kangkung", unit: "ikat", cat: "Sayuran" },
                { name: "Bayam", unit: "ikat", cat: "Sayuran" },
                { name: "Wortel", unit: "kg", cat: "Sayuran" },
                { name: "Ayam Potong", unit: "kg", cat: "Daging & Unggas" },
                { name: "Daging Sapi", unit: "kg", cat: "Daging & Unggas" },
                { name: "Telur Ayam", unit: "kg", cat: "Sembako" },
                { name: "Tahu Putih", unit: "bks", cat: "Tahu & Tempe" },
                { name: "Tempe", unit: "papan", cat: "Tahu & Tempe" },
                { name: "Ikan Nila", unit: "kg", cat: "Ikan & Laut" },
                { name: "Minyak Goreng", unit: "liter", cat: "Sembako" },
                { name: "Beras", unit: "kg", cat: "Sembako" }
            ];

            const units = ["kg", "gram", "ons", "pcs", "ikat", "liter", "bks", "karung", "ekor", "butir", "sisir", "papan", "dus"];
            const categories = ["Sayuran", "Daging & Unggas", "Ikan & Laut", "Bumbu & Rempah", "Buah-buahan", "Sembako", "Tahu & Tempe", "Perlengkapan", "Lain-lain"];

            const modalHtml = `
                <div class="modal fade" id="modalBelanjaPasar" tabindex="-1">
                    <div class="modal-dialog modal-dialog-centered">
                        <div class="modal-content border-0 shadow rounded-4">
                            <div class="modal-header text-white" style="background-color: #10B981;">
                                <h5 class="modal-title fw-bold">
                                    <i class="bi ${dol}{isEdit ? 'bi-pencil-square' : 'bi-plus-circle-fill'} me-2"></i>
                                    ${dol}{isEdit ? 'Edit Belanjaan Pasar' : 'Tambah Belanjaan Pasar'}
                                </h5>
                                <button type="button" class="btn-close btn-close-white" data-bs-dismiss="modal"></button>
                            </div>
                            <div class="modal-body p-4">
                                ${dol}{!isEdit ? `
                                    <div class="mb-3">
                                        <label class="form-label text-secondary small fw-bold text-uppercase">Pilihan Komoditas Populer (Menu List):</label>
                                        <select class="form-select" id="bpKomoditasSelect" onchange="applyCommoditySelect(this)">
                                            <option value="">-- Pilih dari Menu Komoditas Populer --</option>
                                            ${dol}{popularCommodities.map(c => `
                                                <option value="${dol}{escapeHtml(c.name)}" data-unit="${dol}{c.unit}" data-cat="${dol}{c.cat}">
                                                    ${dol}{escapeHtml(c.name)} (${dol}{c.cat} • ${dol}{c.unit})
                                                </option>
                                            `).join('')}
                                        </select>
                                        <div class="form-text small">Pilih salah satu untuk otomatis mengisi nama, satuan, dan kategori.</div>
                                    </div>
                                ` : ''}

                                <div class="mb-3">
                                    <label class="form-label fw-bold">Nama Barang / Komoditas <span class="text-danger">*</span></label>
                                    <input type="text" class="form-control" id="bpNama" placeholder="Misal: Cabai Rawit, Bawang Merah, Daging Ayam..." value="${dol}{escapeHtml(item ? item.nama : '')}">
                                </div>

                                <div class="row g-3 mb-3">
                                    <div class="col-6">
                                        <label class="form-label fw-bold">Kuantitas <span class="text-danger">*</span></label>
                                        <input type="number" step="0.01" min="0.01" class="form-control" id="bpJumlah" placeholder="1" value="${dol}{item ? item.jumlah : '1'}">
                                        <div class="d-flex gap-1 mt-1">
                                            <span class="badge bg-light text-secondary border cursor-pointer" onclick="document.getElementById('bpJumlah').value='0.25'">0.25</span>
                                            <span class="badge bg-light text-secondary border cursor-pointer" onclick="document.getElementById('bpJumlah').value='0.5'">0.5</span>
                                            <span class="badge bg-light text-secondary border cursor-pointer" onclick="document.getElementById('bpJumlah').value='1'">1</span>
                                            <span class="badge bg-light text-secondary border cursor-pointer" onclick="document.getElementById('bpJumlah').value='2'">2</span>
                                            <span class="badge bg-light text-secondary border cursor-pointer" onclick="document.getElementById('bpJumlah').value='5'">5</span>
                                        </div>
                                    </div>
                                    <div class="col-6">
                                        <label class="form-label fw-bold">Satuan <span class="text-danger">*</span></label>
                                        <select class="form-select" id="bpSatuan" onchange="checkCustomSatuan(this.value)">
                                            ${dol}{units.map(u => `
                                                <option value="${dol}{u}" ${dol}{item && item.satuan === u ? 'selected' : (u === 'kg' && !item ? 'selected' : '')}>${dol}{u}</option>
                                            `).join('')}
                                            <option value="custom" ${dol}{item && !units.includes(item.satuan) ? 'selected' : ''}>Satuan Lainnya...</option>
                                        </select>
                                    </div>
                                </div>

                                <div class="mb-3" id="bpCustomSatuanWrap" style="display: ${dol}{item && !units.includes(item.satuan) ? 'block' : 'none'};">
                                    <label class="form-label fw-bold">Ketik Satuan Kustom:</label>
                                    <input type="text" class="form-control" id="bpCustomSatuan" placeholder="Misal: pak, kantong, box..." value="${dol}{item && !units.includes(item.satuan) ? escapeHtml(item.satuan) : ''}">
                                </div>

                                <div class="mb-3">
                                    <label class="form-label fw-bold">Kategori</label>
                                    <select class="form-select" id="bpKategori">
                                        ${dol}{categories.map(c => `
                                            <option value="${dol}{c}" ${dol}{item && item.kategori === c ? 'selected' : ''}>${dol}{c}</option>
                                        `).join('')}
                                    </select>
                                </div>

                                <div class="mb-3">
                                    <label class="form-label fw-bold">Estimasi Harga Satuan (Rp) <small class="text-secondary">(Opsional)</small></label>
                                    <input type="number" class="form-control" id="bpPerkiraanHarga" placeholder="Misal: 35000" value="${dol}{item && item.perkiraanHarga > 0 ? item.perkiraanHarga : ''}">
                                </div>

                                <div class="mb-3">
                                    <label class="form-label fw-bold">Catatan Belanja <small class="text-secondary">(Opsional)</small></label>
                                    <input type="text" class="form-control" id="bpCatatan" placeholder="Misal: pilih yang segar, minta dipotong 8..." value="${dol}{escapeHtml(item ? item.catatan : '')}">
                                </div>
                            </div>
                            <div class="modal-footer">
                                <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">Batal</button>
                                <button type="button" class="btn btn-success fw-bold px-4" onclick="saveBelanjaPasarFromModal(${dol}{item ? item.id : 0})">
                                    ${dol}{isEdit ? 'Simpan Perubahan' : 'Tambah Belanjaan'}
                                </button>
                            </div>
                        </div>
                    </div>
                </div>
            `;

            const existingModal = document.getElementById('modalBelanjaPasar');
            if (existingModal) existingModal.remove();

            document.body.insertAdjacentHTML('beforeend', modalHtml);
            const modalEl = document.getElementById('modalBelanjaPasar');
            const bsModal = new bootstrap.Modal(modalEl);
            bsModal.show();
        }

        function checkCustomSatuan(val) {
            const wrap = document.getElementById('bpCustomSatuanWrap');
            if (wrap) wrap.style.display = (val === 'custom') ? 'block' : 'none';
        }

        function applyCommoditySelect(selectEl) {
            if (!selectEl || !selectEl.value) return;
            const opt = selectEl.options[selectEl.selectedIndex];
            const name = opt.value;
            const unit = opt.getAttribute('data-unit') || 'kg';
            const cat = opt.getAttribute('data-cat') || 'Sayuran';
            applyCommoditySuggestion(name, unit, cat);
        }

        function applyCommoditySuggestion(name, unit, cat) {
            const namaEl = document.getElementById('bpNama');
            const satuanEl = document.getElementById('bpSatuan');
            const katEl = document.getElementById('bpKategori');
            if (namaEl) namaEl.value = name;
            if (satuanEl) satuanEl.value = unit;
            if (katEl) katEl.value = cat;
            checkCustomSatuan(unit);
        }

        async function saveBelanjaPasarFromModal(id) {
            const nama = (document.getElementById('bpNama').value || '').trim();
            if (!nama) {
                alert("Nama barang / komoditas wajib diisi!");
                return;
            }

            const jumlah = parseFloat(document.getElementById('bpJumlah').value) || 1.0;
            const satuanSelect = document.getElementById('bpSatuan').value;
            const customSatuan = (document.getElementById('bpCustomSatuan') ? document.getElementById('bpCustomSatuan').value : '').trim();
            const satuan = (satuanSelect === 'custom') ? (customSatuan || 'pcs') : satuanSelect;

            const kategori = document.getElementById('bpKategori').value || 'Sayuran';
            const perkiraanHarga = parseFloat(document.getElementById('bpPerkiraanHarga').value) || 0.0;
            const catatan = (document.getElementById('bpCatatan').value || '').trim();
            const today = new Date().toISOString().split('T')[0];

            const payload = {
                id: id,
                nama: nama,
                kategori: kategori,
                jumlah: jumlah,
                satuan: satuan,
                perkiraanHarga: perkiraanHarga,
                realisasiHarga: 0.0,
                sudahDibeli: false,
                catatan: catatan,
                tanggal: today
            };

            try {
                const res = await fetch('/api/belanja_pasar', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(payload)
                });
                if (res.ok) {
                    const result = await res.json();
                    if (id === 0) {
                        payload.id = result.id || Date.now();
                        window.belanjaPasarList.unshift(payload);
                    } else {
                        const idx = window.belanjaPasarList.findIndex(it => it.id === id);
                        if (idx !== -1) {
                            payload.sudahDibeli = window.belanjaPasarList[idx].sudahDibeli;
                            window.belanjaPasarList[idx] = payload;
                        }
                    }
                }
            } catch(e) {
                console.error("Gagal menyimpan data belanja:", e);
                // Offline fallback
                if (id === 0) {
                    payload.id = Date.now();
                    window.belanjaPasarList.unshift(payload);
                } else {
                    const idx = window.belanjaPasarList.findIndex(it => it.id === id);
                    if (idx !== -1) window.belanjaPasarList[idx] = payload;
                }
            }

            localStorage.setItem('cached_belanja_pasar', JSON.stringify(window.belanjaPasarList));

            const modalEl = document.getElementById('modalBelanjaPasar');
            if (modalEl) {
                const bsModal = bootstrap.Modal.getInstance(modalEl);
                if (bsModal) bsModal.hide();
            }

            renderRekapBelanjaPasarView();
        }

        // --- WHATSAPP SHARE ---
        function shareBelanjaPasarWhatsApp() {
            const items = window.belanjaPasarList || [];
            if (items.length === 0) {
                alert("Daftar belanja masih kosong!");
                return;
            }

            let text = "🛒 *REKAP DAFTAR BELANJA PASAR*\n";
            text += "📅 Tanggal: " + new Date().toLocaleDateString('id-ID', { day: 'numeric', month: 'long', year: 'numeric' }) + "\n";
            text += "────────────────────────\n\n";

            const grouped = {};
            items.forEach(it => {
                const cat = it.kategori || 'Sayuran';
                if (!grouped[cat]) grouped[cat] = [];
                grouped[cat].push(it);
            });

            for (const cat in grouped) {
                text += "*[" + cat + "]*\n";
                grouped[cat].forEach(it => {
                    const check = it.sudahDibeli ? "✅" : "⬜";
                    const q = Number(it.jumlah) || 1;
                    const qStr = (q % 1 === 0) ? q : q.toFixed(2).replace(/\.00${dol}/, '');
                    text += check + " " + it.nama + " : " + qStr + " " + (it.satuan || 'kg');
                    if (it.catatan) text += " _(" + it.catatan + ")_";
                    if (it.perkiraanHarga > 0) {
                        const sub = Number(it.perkiraanHarga) * q;
                        text += " ~ Rp " + sub.toLocaleString('id-ID');
                    }
                    text += "\n";
                });
                text += "\n";
            }

            const totalEst = items.reduce((acc, it) => acc + ((Number(it.perkiraanHarga) || 0) * (Number(it.jumlah) || 1)), 0);
            if (totalEst > 0) {
                text += "────────────────────────\n";
                text += "💰 *Total Estimasi:* Rp " + totalEst.toLocaleString('id-ID') + "\n";
            }
            text += "\n_Dibuat dengan My Singghara_";

            navigator.clipboard.writeText(text).then(() => {
                alert("Daftar belanja berhasil disalin ke clipboard! Membuka WhatsApp Web...");
                window.open('https://api.whatsapp.com/send?text=' + encodeURIComponent(text), '_blank');
            }).catch(() => {
                window.open('https://api.whatsapp.com/send?text=' + encodeURIComponent(text), '_blank');
            });
        }

        // --- PRINT REKAP BELANJA ---
        function printRekapBelanjaPasar() {
            const items = window.belanjaPasarList || [];
            if (items.length === 0) {
                alert("Daftar belanja masih kosong!");
                return;
            }

            const today = new Date().toLocaleDateString('id-ID', { day: 'numeric', month: 'long', year: 'numeric' });
            let rowsHtml = '';
            let totalEst = 0;

            items.forEach(it => {
                const q = Number(it.jumlah) || 1;
                const qStr = (q % 1 === 0) ? q : q.toFixed(2).replace(/\.00${dol}/, '');
                const sub = (Number(it.perkiraanHarga) || 0) * q;
                totalEst += sub;
                rowsHtml += `
                    <tr>
                        <td style="text-align: center; font-size: 14px;">${dol}{it.sudahDibeli ? '[✓]' : '[ ]'}</td>
                        <td><strong>${dol}{escapeHtml(it.nama)}</strong></td>
                        <td>${dol}{escapeHtml(it.kategori || 'Sayuran')}</td>
                        <td style="text-align: center;"><strong>${dol}{qStr} ${dol}{escapeHtml(it.satuan || 'kg')}</strong></td>
                        <td style="text-align: right;">${dol}{sub > 0 ? 'Rp ' + sub.toLocaleString('id-ID') : '-'}</td>
                        <td>${dol}{escapeHtml(it.catatan || '')}</td>
                    </tr>
                `;
            });

            const printHtml = `
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="utf-8">
                    <title>Rekap Daftar Belanja Pasar</title>
                    <style>
                        body { font-family: 'Segoe UI', Arial, sans-serif; margin: 30px; color: #1f2937; }
                        h2 { color: #10b981; margin-bottom: 5px; }
                        p { font-size: 13px; color: #6b7280; margin-top: 0; }
                        table { width: 100%; border-collapse: collapse; margin-top: 15px; font-size: 13px; }
                        th { background: #f3f4f6; border: 1px solid #d1d5db; padding: 8px; text-align: left; font-weight: 600; }
                        td { border: 1px solid #d1d5db; padding: 8px; }
                        .total { margin-top: 15px; text-align: right; font-weight: bold; font-size: 14px; color: #10b981; }
                    </style>
                </head>
                <body>
                    <h2>🛒 REKAP DAFTAR BELANJA PASAR</h2>
                    <p>Tanggal: ${dol}{today} | Total Item: ${dol}{items.length}</p>
                    <table>
                        <thead>
                            <tr>
                                <th style="width: 35px; text-align: center;">✓</th>
                                <th>Nama Barang</th>
                                <th>Kategori</th>
                                <th style="text-align: center;">Kuantitas</th>
                                <th style="text-align: right;">Estimasi Subtotal</th>
                                <th>Catatan</th>
                            </tr>
                        </thead>
                        <tbody>
                            ${dol}{rowsHtml}
                        </tbody>
                    </table>
                    <div class="total">
                        Total Estimasi Belanja: Rp ${dol}{totalEst.toLocaleString('id-ID')}
                    </div>
                </body>
                </html>
            `;

            const printFrame = document.createElement('iframe');
            printFrame.style.position = 'fixed';
            printFrame.style.right = '0';
            printFrame.style.bottom = '0';
            printFrame.style.width = '0';
            printFrame.style.height = '0';
            printFrame.style.border = '0';
            document.body.appendChild(printFrame);

            printFrame.contentDocument.write(printHtml);
            printFrame.contentDocument.close();

            setTimeout(() => {
                printFrame.contentWindow.focus();
                printFrame.contentWindow.print();
                setTimeout(() => {
                    if (document.body.contains(printFrame)) document.body.removeChild(printFrame);
                }, 2000);
            }, 500);
        }
        """
    }
}
