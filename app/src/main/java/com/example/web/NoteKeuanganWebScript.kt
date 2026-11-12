package com.example.web

object NoteKeuanganWebScript {
    fun getScript(dol: String): String {
        return """
        // ==========================================
        // --- MODUL NOTE KEUANGAN (PARITAS APLIKASI) ---
        // ==========================================

        window.nkItems = [];
        window.nkSearch = '';
        window.nkFilterJenis = 'Semua';
        window.nkFilterSumber = 'Semua';
        window.nkFilterKategori = 'Semua';
        window.nkPage = 0;
        window.nkSelected = 'saldo';
        window.nkOpenFilter = null;
        window.nkForm = null;

        const NK_KATEGORI_PEMASUKAN = 'Pemasukan';
        const NK_KATEGORI_PENGELUARAN = ['Makan & Minum', 'Transportasi', 'Tagihan', 'Belanja & Hiburan', 'Lainnya'];
        const NK_SUMBER_UMUM = ['BCA', 'Mandiri', 'BRI', 'BNI', 'Bank Jago', 'GoPay', 'DANA', 'OVO', 'ShopeePay', 'Tunai'];

        const NK_KATA_KUNCI = (function () {
            const rows = [];
            function add(kategori, kata) { kata.forEach(k => rows.push([k, kategori])); }
            add('Makan & Minum', ['MAKAN', 'MAKANAN', 'MINUM', 'MINUMAN', 'KOPI', 'CAFE', 'KAFE', 'RESTO', 'RESTORAN', 'WARUNG', 'BAKSO', 'NASI', 'MIE', 'AYAM', 'SATE', 'BURGER', 'PIZZA', 'SUSHI', 'JAJAN', 'KUE', 'ROTI', 'BAKERY', 'KFC', 'MCD', 'MCDONALD', 'GOFOOD', 'GRABFOOD', 'SHOPEEFOOD', 'KANTIN', 'CATERING', 'ES', 'TEH', 'SUSU', 'SARAPAN', 'MAKANSIANG']);
            add('Transportasi', ['BENSIN', 'PERTAMINA', 'PERTALITE', 'PERTAMAX', 'SOLAR', 'OJEK', 'GOJEK', 'GOJCAR', 'GOJBIKE', 'GRAB', 'GRABCAR', 'GRABBIKE', 'MAXIM', 'TOL', 'PARKIR', 'KRL', 'MRT', 'LRT', 'BUS', 'ANGKOT', 'TAKSI', 'TAXI', 'TIKET', 'TRAVEL', 'BANDARA', 'KERETA', 'OLI', 'SERVIS', 'BENGKEL', 'GANJIL']);
            add('Tagihan', ['LISTRIK', 'PLN', 'AIR', 'PDAM', 'PAM', 'INTERNET', 'WIFI', 'INDIHOME', 'PULSA', 'PAKET', 'TELKOMSEL', 'TELKOM', 'XL', 'BPJS', 'KOS', 'SEWA', 'KONTRAKAN', 'CICILAN', 'ANGSURAN', 'KREDIT', 'TAGIHAN', 'IURAN', 'ASURANSI', 'PAJAK', 'SUMBANGAN', 'DONASI']);
            add('Belanja & Hiburan', ['BELANJA', 'INDOMARET', 'ALFAMART', 'SUPERMARKET', 'MINIMARKET', 'TOKOPEDIA', 'TOKO', 'SHOPEE', 'LAZADA', 'BIBIT', 'BLIBLI', 'MALL', 'FASHION', 'PAKAIAN', 'SEPATU', 'BIOSKOP', 'NONTON', 'CINEMA', 'NETFLIX', 'SPOTIFY', 'YOUTUBE', 'DISNEY', 'GAME', 'GAMES', 'STEAM', 'KONSER', 'WISATA', 'HOTEL', 'LIBURAN']);
            return rows.sort((a, b) => b[0].length - a[0].length);
        })();

        function nkEscape(text) { return escapeHtml(text || ''); }

        function nkRupiah(val) {
            const n = Math.round(Number(val) || 0);
            return 'Rp ' + n.toLocaleString('id-ID');
        }

        function nkToday() {
            const d = new Date();
            const bulan = String(d.getMonth() + 1).padStart(2, '0');
            const hari = String(d.getDate()).padStart(2, '0');
            return d.getFullYear() + '-' + bulan + '-' + hari;
        }

        function nkDeteksiKategori(keterangan) {
            const bersih = (keterangan || '').toUpperCase().replace(/[^A-Z0-9]+/g, ' ').trim();
            if (!bersih) return null;
            const token = bersih.split(' ');
            for (let i = 0; i < NK_KATA_KUNCI.length; i++) {
                const kata = NK_KATA_KUNCI[i][0];
                const kategori = NK_KATA_KUNCI[i][1];
                for (let j = 0; j < token.length; j++) {
                    const t = token[j];
                    if (t === kata) return kategori;
                    if (kata.length >= 5 && t.indexOf(kata) === 0 && t.length - kata.length <= 5) return kategori;
                }
            }
            return null;
        }

        function nkSumberTersedia() {
            const semua = NK_SUMBER_UMUM.concat((window.nkItems || []).map(x => x.sumberDana || ''));
            const terlihat = {};
            const hasil = [];
            semua.forEach(s => {
                const v = (s || '').trim();
                if (!v) return;
                const kunci = v.toLowerCase();
                if (terlihat[kunci]) return;
                terlihat[kunci] = true;
                hasil.push(v);
            });
            return hasil;
        }

        function nkFilteredItems() {
            const q = (window.nkSearch || '').toLowerCase().trim();
            const fSumber = window.nkFilterSumber || 'Semua';
            const fJenis = window.nkFilterJenis || 'Semua';
            const fKategori = window.nkFilterKategori || 'Semua';
            return (window.nkItems || []).filter(item => {
                const ket = (item.keterangan || '').toLowerCase();
                const kat = (item.kategori || '').toLowerCase();
                const sum = (item.sumberDana || '').toLowerCase();
                const cocokCari = !q || ket.indexOf(q) >= 0 || kat.indexOf(q) >= 0 || sum.indexOf(q) >= 0;
                const cocokSumber = fSumber === 'Semua' || sum === fSumber.toLowerCase();
                const cocokJenis = fJenis === 'Semua' || (item.jenis || '') === fJenis.toLowerCase();
                const cocokKategori = fKategori === 'Semua' || kat === fKategori.toLowerCase();
                return cocokCari && cocokSumber && cocokJenis && cocokKategori;
            });
        }

        async function nkFetchData() {
            try {
                const res = await fetch('/api/catatan-keuangan');
                if (res.ok) {
                    const data = await res.json();
                    window.nkItems = data.items || [];
                }
            } catch (e) {
                console.error('Gagal memuat note keuangan:', e);
            }
        }

        async function loadNoteKeuangan() {
            await nkFetchData();
            window.nkPage = 0;
            renderNoteKeuanganUi();
        }

        function nkSaldoColor() {
            try {
                const v = getComputedStyle(document.documentElement).getPropertyValue('--brand-green').trim();
                if (v) return v;
            } catch (e) { }
            const gelap = document.documentElement.getAttribute('data-theme') === 'dark' || document.body.classList.contains('dark');
            return gelap ? '#38bdf8' : '#3674B5';
        }

        function nkKategoriWarna(cat) {
            if (cat === 'masuk') return '#16A34A';
            if (cat === 'keluar') return '#DC2626';
            return nkSaldoColor();
        }

        function nkArcPath(cx, cy, r, startDeg, sweepDeg) {
            const aman = Math.min(359.6, Math.max(0.4, sweepDeg));
            const polar = (deg) => {
                const rad = (deg - 90) * Math.PI / 180;
                return [cx + r * Math.cos(rad), cy + r * Math.sin(rad)];
            };
            const awal = polar(startDeg);
            const akhir = polar(startDeg + aman);
            const largeArc = aman > 180 ? 1 : 0;
            return 'M ' + awal[0].toFixed(2) + ' ' + awal[1].toFixed(2) + ' A ' + r + ' ' + r + ' 0 ' + largeArc + ' 1 ' + akhir[0].toFixed(2) + ' ' + akhir[1].toFixed(2);
        }

        function nkDonutHtml(totalMasuk, totalKeluar, saldo) {
            const valSaldo = Math.max(0, saldo);
            const jumlah = valSaldo + totalMasuk + totalKeluar;
            const aSaldo = jumlah > 0 ? (valSaldo / jumlah) * 360 : 120;
            const aMasuk = jumlah > 0 ? (totalMasuk / jumlah) * 360 : 120;
            const aKeluar = jumlah > 0 ? (totalKeluar / jumlah) * 360 : 120;
            const terpilih = window.nkSelected || 'saldo';

            const R = 90;
            const keliling = 2 * Math.PI * R;

            function busur(cat, sudut, mulai, warna) {
                if (sudut <= 0) return '';
                const lebar = terpilih === cat ? 26 : 20;
                const panjang = keliling * sudut / 360;
                const geser = -keliling * mulai / 360;
                return '<circle cx="105" cy="105" r="' + R + '" fill="none" stroke="' + warna + '" stroke-width="' + lebar + '" stroke-linecap="round" stroke-dasharray="' + panjang.toFixed(2) + ' ' + (keliling - panjang).toFixed(2) + '" stroke-dashoffset="' + geser.toFixed(2) + '" style="cursor:pointer;transition:stroke-width .2s ease" onclick="nkOpenDetail(\'' + cat + '\')"></circle>';
            }

            let isi = '';
            isi += busur('saldo', aSaldo, 0, nkSaldoColor());
            isi += busur('masuk', aMasuk, aSaldo, '#16A34A');
            isi += busur('keluar', aKeluar, aSaldo + aMasuk, '#DC2626');

            let nilai = saldo;
            let label = 'Saldo';
            let mulaiTeks = 0;
            let sweepTeks = aSaldo;
            let warnaTeks = nkSaldoColor();
            if (terpilih === 'masuk') {
                nilai = totalMasuk; label = 'Masuk'; mulaiTeks = aSaldo; sweepTeks = aMasuk; warnaTeks = '#166534';
            } else if (terpilih === 'keluar') {
                nilai = totalKeluar; label = 'Keluar'; mulaiTeks = aSaldo + aMasuk; sweepTeks = aKeluar; warnaTeks = '#991B1B';
            }

            const pathTeks = nkArcPath(105, 105, R, mulaiTeks, Math.max(40, sweepTeks));
            const teksBusur = '<path id="nkArcPath" d="' + pathTeks + '" fill="none" stroke="none"></path>' +
                '<text fill="' + warnaTeks + '" font-size="12" font-weight="700" text-anchor="middle">' +
                '<textPath href="#nkArcPath" startOffset="50%">' + nkEscape(label + ' ' + nkRupiah(nilai)) + '</textPath></text>';

            return '<svg viewBox="0 0 210 210" width="100%" height="100%" style="overflow:visible">' +
                '<g transform="rotate(-90 105 105)">' + isi + '</g>' +
                teksBusur +
                '</svg>';
        }

        function nkFilterButtonHtml(kind, judul, ikon, nilai, pilihan) {
            const aktif = nilai !== 'Semua';
            const terbuka = window.nkOpenFilter === kind;
            let html = '<div class="nk-filter-dd">' +
                '<button type="button" class="nk-filter-btn' + (aktif ? ' active' : '') + '" title="' + nkEscape(judul) + '" onclick="nkToggleFilter(\'' + kind + '\')">' +
                '<i class="bi ' + ikon + '" style="font-size:16px"></i>' +
                (aktif ? '<span class="nk-filter-value">' + nkEscape(nilai) + '</span>' : '') +
                '<i class="bi bi-caret-down-fill" style="font-size:11px"></i>' +
                '</button>';
            if (terbuka) {
                html += '<div class="nk-filter-menu">';
                pilihan.forEach(opsi => {
                    const cocok = String(opsi).toLowerCase() === String(nilai).toLowerCase();
                    html += '<button type="button" class="nk-filter-item' + (cocok ? ' active' : '') + '" data-value="' + nkEscape(opsi) + '" onclick="nkSetFilterFromEl(\'' + kind + '\', this)">' +
                        '<span>' + nkEscape(opsi) + '</span>' +
                        (cocok ? '<i class="bi bi-check-lg"></i>' : '') +
                        '</button>';
                });
                html += '</div>';
            }
            html += '</div>';
            return html;
        }

        function nkSummaryLabel(cat) {
            if (cat === 'masuk') return 'Masuk';
            if (cat === 'keluar') return 'Keluar';
            return 'Saldo';
        }

        function renderNoteKeuanganUi() {
            const container = document.getElementById('tab-content');
            if (!container) return;

            const filtered = nkFilteredItems();
            const totalMasuk = filtered.filter(x => x.jenis === 'masuk').reduce((acc, x) => acc + (Number(x.jumlah) || 0), 0);
            const totalKeluar = filtered.filter(x => x.jenis === 'keluar').reduce((acc, x) => acc + (Number(x.jumlah) || 0), 0);
            const saldo = totalMasuk - totalKeluar;
            const jumlahCatatan = filtered.length;

            const terpilih = window.nkSelected || 'saldo';
            const nilaiTengah = terpilih === 'masuk' ? totalMasuk : (terpilih === 'keluar' ? totalKeluar : saldo);
            const labelTengah = nkSummaryLabel(terpilih);
            const warnaTengah = nkKategoriWarna(terpilih);

            const sumberList = nkSumberTersedia();
            const kategoriPilihan = ['Semua'].concat(NK_KATEGORI_PENGELUARAN);

            const perHalaman = 5;
            const totalHalaman = Math.max(1, Math.ceil(filtered.length / perHalaman));
            if (window.nkPage > totalHalaman - 1) window.nkPage = totalHalaman - 1;
            const halaman = Math.max(0, Math.min(window.nkPage, totalHalaman - 1));
            const baris = filtered.slice(halaman * perHalaman, halaman * perHalaman + perHalaman);

            let html = '<div class="nk-page">';

            // Kartu ringkasan saldo + donut interaktif
            html += '<div class="card nk-card nk-summary mb-3">' +
                '<div class="d-flex flex-column flex-sm-row align-items-center gap-3">' +
                '<div class="flex-grow-1 w-100">' +
                '<div class="d-flex justify-content-between align-items-center">' +
                '<span class="nk-label">Saldo Tercatat</span>' +
                '<button type="button" class="nk-print-btn" title="Cetak Note Keuangan" onclick="nkPrint()"><i class="bi bi-printer"></i></button>' +
                '</div>' +
                '<div class="nk-saldo">' + nkRupiah(saldo) + '</div>' +
                '</div>' +
                '<div class="nk-donut">' +
                nkDonutHtml(totalMasuk, totalKeluar, saldo) +
                '<div class="nk-donut-center">' +
                '<span class="nk-donut-title">' + labelTengah + '</span>' +
                '<span class="nk-donut-value" style="color:' + warnaTengah + '">' + (terpilih === 'saldo' ? nkRupiah(saldo) : nkRupiah(nilaiTengah)) + '</span>' +
                '</div>' +
                '</div>' +
                '</div>' +
                '<div class="d-flex gap-2 mt-3">' +
                '<div class="nk-stat-mini flex-fill"><span>Masuk</span><b style="color:#16A34A">' + nkRupiah(totalMasuk) + '</b></div>' +
                '<div class="nk-stat-mini flex-fill"><span>Keluar</span><b style="color:#DC2626">' + nkRupiah(totalKeluar) + '</b></div>' +
                '<div class="nk-stat-mini flex-fill"><span>Catatan</span><b class="nk-stat-catatan">' + jumlahCatatan + '</b></div>' +
                '</div>' +
                '</div>';

            // Filter: pencarian + 3 dropdown
            html += '<div class="d-flex align-items-center gap-2 flex-wrap mb-3">' +
                '<div class="nk-search flex-grow-1">' +
                '<i class="bi bi-search"></i>' +
                '<input id="nkSearchInput" type="text" placeholder="Cari keterangan, kategori, sumber dana..." value="' + nkEscape(window.nkSearch || '') + '" oninput="nkOnSearch(this.value)">' +
                (window.nkSearch ? '<button type="button" class="nk-clear" title="Hapus pencarian" onclick="nkOnSearch(\'\')"><i class="bi bi-x-lg"></i></button>' : '') +
                '</div>' +
                nkFilterButtonHtml('jenis', 'Filter jenis transaksi', 'bi-arrow-down-up', window.nkFilterJenis || 'Semua', ['Semua', 'Masuk', 'Keluar']) +
                nkFilterButtonHtml('sumber', 'Filter sumber dana', 'bi-wallet2', window.nkFilterSumber || 'Semua', ['Semua'].concat(sumberList)) +
                nkFilterButtonHtml('kategori', 'Filter kategori', 'bi-tag', window.nkFilterKategori || 'Semua', kategoriPilihan) +
                '</div>';

            if (filtered.length === 0) {
                html += '<div class="card nk-card nk-empty">' +
                    '<i class="bi bi-tag" style="font-size:32px;color:var(--primary-color);opacity:.6"></i>' +
                    '<div class="nk-empty-title">Belum ada catatan</div>' +
                    '<div class="nk-empty-text">Tambahkan transaksi m-banking atau e-wallet dengan tombol + di kanan bawah.</div>' +
                    '</div>';
            } else {
                html += '<div class="card nk-card nk-table-card mb-2">' +
                    '<div class="nk-table-scroll"><table class="nk-table"><thead><tr>' +
                    '<th style="width:92px">Tanggal</th>' +
                    '<th style="width:168px">Keterangan</th>' +
                    '<th style="width:92px" class="text-center">Jenis</th>' +
                    '<th style="width:112px">Kategori</th>' +
                    '<th style="width:104px">Sumber Dana</th>' +
                    '<th style="width:112px" class="text-end">Jumlah</th>' +
                    '<th style="width:84px" class="text-center">Aksi</th>' +
                    '</tr></thead><tbody>';

                baris.forEach(item => {
                    const masuk = item.jenis === 'masuk';
                    html += '<tr>' +
                        '<td><div class="nk-cell nk-cell-tanggal">' + nkEscape(item.tanggal) + '</div></td>' +
                        '<td><div class="nk-cell nk-cell-ket">' + nkEscape(item.keterangan || '-') + '</div></td>' +
                        '<td class="text-center"><div class="nk-cell"><span class="nk-badge-jenis ' + (masuk ? 'nk-badge-masuk' : 'nk-badge-keluar') + '">' + (masuk ? 'Masuk' : 'Keluar') + '</span></div></td>' +
                        '<td><div class="nk-cell nk-cell-sub">' + nkEscape(item.kategori) + '</div></td>' +
                        '<td><div class="nk-cell nk-cell-sub">' + nkEscape(item.sumberDana) + '</div></td>' +
                        '<td class="text-end"><div class="nk-cell"><span class="' + (masuk ? 'nk-amount-masuk' : 'nk-amount-keluar') + '">' + (masuk ? '+ ' : '- ') + nkRupiah(item.jumlah) + '</span></div></td>' +
                        '<td class="text-center"><div class="nk-cell nk-actions">' +
                        '<button type="button" class="nk-action-btn edit" title="Ubah" onclick="nkOpenForm(' + item.id + ')"><i class="bi bi-pencil"></i></button>' +
                        '<button type="button" class="nk-action-btn delete" title="Hapus" onclick="nkConfirmDelete(' + item.id + ')"><i class="bi bi-trash"></i></button>' +
                        '</div></td>' +
                        '</tr>';
                });

                html += '</tbody></table></div></div>';

                if (filtered.length > perHalaman) {
                    html += '<div class="nk-pagination">' +
                        '<button type="button" class="nk-page-btn" ' + (halaman <= 0 ? 'disabled' : '') + ' onclick="nkSetPage(' + (halaman - 1) + ')"><i class="bi bi-arrow-left"></i> Sebelumnya</button>' +
                        '<span class="nk-page-info">Halaman ' + (halaman + 1) + ' dari ' + totalHalaman + '</span>' +
                        '<button type="button" class="nk-page-btn" ' + (halaman >= totalHalaman - 1 ? 'disabled' : '') + ' onclick="nkSetPage(' + (halaman + 1) + ')">Selanjutnya <i class="bi bi-arrow-right"></i></button>' +
                        '</div>';
                }
            }

            html += '<button type="button" class="nk-fab" title="Tambah note keuangan" onclick="nkOpenForm(null)"><i class="bi bi-plus-lg"></i></button>';
            html += '</div>';

            container.innerHTML = html;
        }

        function nkOnSearch(nilai) {
            window.nkSearch = nilai || '';
            window.nkPage = 0;
            renderNoteKeuanganUi();
            const input = document.getElementById('nkSearchInput');
            if (input) {
                input.focus();
                try {
                    const pos = input.value.length;
                    input.setSelectionRange(pos, pos);
                } catch (e) { }
            }
        }

        function nkToggleFilter(kind) {
            window.nkOpenFilter = (window.nkOpenFilter === kind) ? null : kind;
            renderNoteKeuanganUi();
        }

        function nkSetFilterFromEl(kind, el) {
            const nilai = el && el.dataset ? (el.dataset.value || 'Semua') : 'Semua';
            if (kind === 'jenis') window.nkFilterJenis = nilai;
            else if (kind === 'sumber') window.nkFilterSumber = nilai;
            else if (kind === 'kategori') window.nkFilterKategori = nilai;
            window.nkOpenFilter = null;
            window.nkPage = 0;
            renderNoteKeuanganUi();
        }

        function nkSetPage(halaman) {
            window.nkPage = Math.max(0, halaman || 0);
            renderNoteKeuanganUi();
            const panggung = document.getElementById('tab-content');
            if (panggung) panggung.scrollIntoView({ behavior: 'smooth', block: 'start' });
        }

        document.addEventListener('click', function (e) {
            if (!window.nkOpenFilter) return;
            if (e.target && e.target.closest && e.target.closest('.nk-filter-dd')) return;
            window.nkOpenFilter = null;
            if (typeof currentTab !== 'undefined' && currentTab === 'note_keuangan') {
                renderNoteKeuanganUi();
            }
        });

        function nkRingkasan() {
            const filtered = nkFilteredItems();
            const totalMasuk = filtered.filter(x => x.jenis === 'masuk').reduce((acc, x) => acc + (Number(x.jumlah) || 0), 0);
            const totalKeluar = filtered.filter(x => x.jenis === 'keluar').reduce((acc, x) => acc + (Number(x.jumlah) || 0), 0);
            return {
                items: filtered,
                totalMasuk: totalMasuk,
                totalKeluar: totalKeluar,
                saldo: totalMasuk - totalKeluar,
                jumlah: filtered.length,
                countMasuk: filtered.filter(x => x.jenis === 'masuk').length,
                countKeluar: filtered.filter(x => x.jenis === 'keluar').length
            };
        }

        function nkOpenDetail(cat) {
            const r = nkRingkasan();
            window.nkSelected = cat;
            renderNoteKeuanganUi();

            const modalId = 'nkDetailModal';
            const lama = document.getElementById(modalId);
            if (lama) lama.remove();

            const warna = nkKategoriWarna(cat);
            const ikon = cat === 'masuk' ? 'bi-graph-up-arrow' : (cat === 'keluar' ? 'bi-graph-down-arrow' : 'bi-wallet2');
            let judul = 'Saldo Tercatat';
            let jumlahTeks = nkRupiah(r.saldo);
            let deskripsi = 'Selisih total masuk dikurangi total keluar dari ' + r.jumlah + ' catatan yang tampil.';
            if (r.saldo < 0) deskripsi += ' Saldo negatif karena pengeluaran lebih besar daripada pemasukan.';
            if (cat === 'masuk') {
                judul = 'Total Masuk';
                jumlahTeks = nkRupiah(r.totalMasuk);
                deskripsi = 'Seluruh dana masuk yang berhasil dicatatkan. Terdiri dari akumulasi sebanyak ' + r.countMasuk + ' transaksi uang masuk.';
            } else if (cat === 'keluar') {
                judul = 'Total Keluar';
                jumlahTeks = nkRupiah(r.totalKeluar);
                deskripsi = 'Seluruh pengeluaran yang pernah dicatatkan. Terdiri dari akumulasi sebanyak ' + r.countKeluar + ' transaksi uang keluar.';
            }

            const html = '<div class="modal fade" id="' + modalId + '" tabindex="-1">' +
                '<div class="modal-dialog modal-dialog-centered modal-sm">' +
                '<div class="modal-content nk-modal-content text-center">' +
                '<div class="nk-detail-icon" style="background:' + warna + '1f;color:' + warna + '"><i class="bi ' + ikon + '"></i></div>' +
                '<div class="nk-detail-title">' + judul + '</div>' +
                '<div class="nk-detail-amount" style="color:' + warna + '">' + jumlahTeks + '</div>' +
                '<div class="nk-detail-desc">' + deskripsi + '</div>' +
                '<button type="button" class="btn w-100 fw-bold mt-3" style="background:' + warna + ';color:#fff" data-bs-dismiss="modal">Tutup</button>' +
                '</div></div></div>';

            document.body.insertAdjacentHTML('beforeend', html);
            const modal = new bootstrap.Modal(document.getElementById(modalId));
            modal.show();
        }

        function nkJenisChipsHtml() {
            const f = window.nkForm;
            return '<button type="button" class="nk-form-chip' + (f.jenis === 'keluar' ? ' selected' : '') + '" onclick="nkFormSetJenis(\'keluar\')">Keluar</button>' +
                '<button type="button" class="nk-form-chip' + (f.jenis === 'masuk' ? ' selected' : '') + '" onclick="nkFormSetJenis(\'masuk\')">Masuk</button>';
        }

        function nkKategoriAreaHtml() {
            const f = window.nkForm;
            if (f.jenis === 'masuk') {
                return '<label class="nk-form-label">Kategori</label>' +
                    '<input type="text" class="form-control nk-input" value="Pemasukan" readonly>';
            }
            let html = '<label class="nk-form-label">Kategori</label><div class="nk-chips">';
            NK_KATEGORI_PENGELUARAN.forEach(k => {
                html += '<button type="button" class="nk-form-chip' + (f.kategori === k ? ' selected' : '') + '" data-value="' + nkEscape(k) + '" onclick="nkFormSetKategoriFromEl(this)">' + nkEscape(k) + '</button>';
            });
            html += '</div>';
            const saran = nkDeteksiKategori(f.keterangan);
            if (saran && saran === f.kategori && !f.kategoriManual) {
                html += '<div class="nk-auto-hint" style="color:#16A34A">Kategori otomatis dari keterangan: ' + nkEscape(saran) + '</div>';
            }
            return html;
        }

        function nkFormValid() {
            const f = window.nkForm;
            if (!f) return false;
            const nominal = parseFloat(f.jumlah) || 0;
            return (f.keterangan || '').trim().length > 0 && (f.sumber || '').trim().length > 0 && nominal > 0;
        }

        function nkRefreshForm() {
            const f = window.nkForm;
            if (!f) return;
            const jenisBox = document.getElementById('nkJenisChips');
            if (jenisBox) jenisBox.innerHTML = nkJenisChipsHtml();
            const kategoriBox = document.getElementById('nkKategoriArea');
            if (kategoriBox) kategoriBox.innerHTML = nkKategoriAreaHtml();
            const nominal = parseFloat(f.jumlah) || 0;
            const preview = document.getElementById('nkNominalPreview');
            if (preview) preview.textContent = nominal > 0 ? 'Nominal: ' + nkRupiah(nominal) : '';
            const tombol = document.getElementById('nkBtnSimpan');
            if (tombol) tombol.disabled = !nkFormValid();
            const sumberTeks = document.getElementById('nkSumberText');
            if (sumberTeks) sumberTeks.textContent = f.sumber || '';
        }

        function nkOpenForm(id) {
            const item = id !== null && id !== undefined ? (window.nkItems || []).find(x => String(x.id) === String(id)) : null;
            const sumberAwal = nkSumberTersedia()[0] || '';
            const nominal = item ? (Number(item.jumlah) || 0) : 0;
            window.nkForm = {
                id: item ? item.id : null,
                jenis: item ? item.jenis : 'keluar',
                tanggal: item ? item.tanggal : nkToday(),
                keterangan: item ? (item.keterangan || '') : '',
                kategori: item ? (item.kategori || 'Lainnya') : 'Lainnya',
                kategoriManual: !!item,
                sumber: item ? (item.sumberDana || '') : sumberAwal,
                jumlah: item ? String(nominal) : ''
            };
            const f = window.nkForm;

            const modalId = 'nkFormModal';
            const lama = document.getElementById(modalId);
            if (lama) lama.remove();

            const html = '<div class="modal fade" id="' + modalId + '" tabindex="-1">' +
                '<div class="modal-dialog modal-dialog-centered">' +
                '<div class="modal-content nk-modal-content nk-form-modal">' +
                '<div class="nk-form-title">' + (item ? 'Ubah Note Keuangan' : 'Tambah Note Keuangan') + '</div>' +
                '<div class="nk-chips mb-2" id="nkJenisChips">' + nkJenisChipsHtml() + '</div>' +
                '<label class="nk-form-label">Tanggal</label>' +
                '<input type="date" class="form-control nk-input mb-2" value="' + nkEscape(f.tanggal) + '" onchange="nkFormSetTanggal(this.value)">' +
                '<label class="nk-form-label">Keterangan</label>' +
                '<input type="text" class="form-control nk-input mb-2" placeholder="Contoh: Kopi Kenangan / Bensin Pertamax" value="' + nkEscape(f.keterangan) + '" oninput="nkFormOnKeterangan(this.value)">' +
                '<div id="nkKategoriArea">' + nkKategoriAreaHtml() + '</div>' +
                '<label class="nk-form-label mt-2">Sumber Dana</label>' +
                '<div class="nk-sumber-field mb-2" onclick="nkOpenSumberPicker()">' +
                '<span id="nkSumberText">' + nkEscape(f.sumber) + '</span>' +
                '<i class="bi bi-caret-down-fill"></i>' +
                '</div>' +
                '<label class="nk-form-label">Jumlah (Rp)</label>' +
                '<input id="nkInpJumlah" type="text" inputmode="numeric" class="form-control nk-input" value="' + nkEscape(f.jumlah) + '" oninput="nkFormOnJumlah(this.value)">' +
                '<div id="nkNominalPreview" class="nk-auto-hint" style="color:var(--text-sub)">' + (nominal > 0 ? 'Nominal: ' + nkRupiah(nominal) : '') + '</div>' +
                '<div class="d-flex justify-content-end align-items-center gap-2 mt-3">' +
                '<button type="button" class="btn btn-link text-decoration-none" data-bs-dismiss="modal">Batal</button>' +
                '<button type="button" id="nkBtnSimpan" class="btn btn-outline-primary fw-bold" onclick="nkSaveForm()" ' + (nkFormValid() ? '' : 'disabled') + '>' + (item ? 'Perbarui' : 'Simpan') + '</button>' +
                '</div>' +
                '</div></div></div>';

            document.body.insertAdjacentHTML('beforeend', html);
            const modal = new bootstrap.Modal(document.getElementById(modalId));
            modal.show();
        }

        function nkFormSetTanggal(nilai) {
            if (window.nkForm) window.nkForm.tanggal = nilai || nkToday();
        }

        function nkFormOnKeterangan(nilai) {
            const f = window.nkForm;
            if (!f) return;
            f.keterangan = nilai || '';
            if (f.jenis === 'keluar' && !f.kategoriManual) {
                const saran = nkDeteksiKategori(f.keterangan);
                if (saran) f.kategori = saran;
            }
            nkRefreshForm();
        }

        function nkFormOnJumlah(nilai) {
            const f = window.nkForm;
            if (!f) return;
            const bersih = (nilai || '').replace(/[^0-9]/g, '');
            f.jumlah = bersih;
            const input = document.getElementById('nkInpJumlah');
            if (input && input.value !== bersih) input.value = bersih;
            nkRefreshForm();
        }

        function nkFormSetJenis(jenis) {
            const f = window.nkForm;
            if (!f) return;
            f.jenis = jenis;
            if (jenis === 'masuk') {
                f.kategori = NK_KATEGORI_PEMASUKAN;
            } else if (f.kategori === NK_KATEGORI_PEMASUKAN) {
                const saran = nkDeteksiKategori(f.keterangan);
                f.kategori = saran || 'Lainnya';
                f.kategoriManual = false;
            }
            nkRefreshForm();
        }

        function nkFormSetKategoriFromEl(el) {
            const f = window.nkForm;
            if (!f || !el || !el.dataset) return;
            f.kategori = el.dataset.value || 'Lainnya';
            f.kategoriManual = true;
            nkRefreshForm();
        }

        function nkOpenSumberPicker() {
            const f = window.nkForm;
            if (!f) return;
            nkCloseSumberPicker();
            const list = nkSumberTersedia();
            let html = '<div id="nkSumberPicker" class="nk-picker-backdrop" onclick="if(event.target===this) nkCloseSumberPicker()">' +
                '<div class="nk-picker-card">' +
                '<div class="nk-picker-title">Pilih Sumber Dana</div>' +
                '<div class="nk-picker-list">';
            list.forEach(s => {
                const cocok = String(s).toLowerCase() === String(f.sumber || '').toLowerCase();
                html += '<button type="button" class="nk-picker-item" data-value="' + nkEscape(s) + '" onclick="nkPickSumberFromEl(this)">' +
                    '<span>' + nkEscape(s) + '</span>' +
                    (cocok ? '<i class="bi bi-check-circle-fill" style="color:var(--primary-color)"></i>' : '') +
                    '</button>';
            });
            html += '</div>' +
                '<div class="input-group mt-3">' +
                '<input id="nkSumberLain" type="text" class="form-control nk-input" placeholder="Mis. SeaBank, Jenius">' +
                '<button type="button" class="btn btn-outline-primary" onclick="nkUseCustomSumber()">Pakai</button>' +
                '</div>' +
                '<div class="text-end mt-2"><button type="button" class="btn btn-link text-decoration-none" onclick="nkCloseSumberPicker()">Tutup</button></div>' +
                '</div></div>';
            document.body.insertAdjacentHTML('beforeend', html);
        }

        function nkCloseSumberPicker() {
            const el = document.getElementById('nkSumberPicker');
            if (el) el.remove();
        }

        function nkPickSumberFromEl(el) {
            const f = window.nkForm;
            if (!f || !el || !el.dataset) return;
            f.sumber = el.dataset.value || '';
            nkCloseSumberPicker();
            nkRefreshForm();
        }

        function nkUseCustomSumber() {
            const input = document.getElementById('nkSumberLain');
            if (!input || !window.nkForm) return;
            const nilai = (input.value || '').trim();
            if (!nilai) return;
            window.nkForm.sumber = nilai;
            nkCloseSumberPicker();
            nkRefreshForm();
        }

        async function nkSaveForm() {
            const f = window.nkForm;
            if (!f || !nkFormValid()) return;
            const nominal = parseFloat(f.jumlah) || 0;
            const payload = {
                tanggal: f.tanggal || nkToday(),
                keterangan: (f.keterangan || '').trim(),
                kategori: f.jenis === 'masuk' ? NK_KATEGORI_PEMASUKAN : f.kategori,
                sumberDana: (f.sumber || '').trim(),
                jenis: f.jenis,
                jumlah: nominal
            };
            const method = f.id ? 'PUT' : 'POST';
            if (f.id) payload.id = f.id;

            try {
                const res = await fetch('/api/catatan-keuangan', {
                    method: method,
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(payload)
                });
                const hasil = await res.json();
                if (!res.ok || !hasil.success) throw new Error(hasil.message || 'Gagal menyimpan catatan');
                const modalEl = document.getElementById('nkFormModal');
                if (modalEl) {
                    const modal = bootstrap.Modal.getInstance(modalEl);
                    if (modal) modal.hide();
                }
                await nkFetchData();
                renderNoteKeuanganUi();
            } catch (e) {
                alert('Gagal menyimpan catatan: ' + e.message);
            }
        }

        function nkConfirmDelete(id) {
            const item = (window.nkItems || []).find(x => String(x.id) === String(id));
            if (!item) return;
            const modalId = 'nkDeleteModal';
            const lama = document.getElementById(modalId);
            if (lama) lama.remove();

            const html = '<div class="modal fade" id="' + modalId + '" tabindex="-1">' +
                '<div class="modal-dialog modal-dialog-centered modal-sm">' +
                '<div class="modal-content nk-modal-content">' +
                '<div class="nk-form-title">Hapus Catatan?</div>' +
                '<div class="nk-delete-text">' + nkEscape(item.keterangan || '-') + ' sebesar ' + nkRupiah(item.jumlah) + ' akan dihapus.</div>' +
                '<div class="d-flex justify-content-end align-items-center gap-2 mt-3">' +
                '<button type="button" class="btn btn-link text-decoration-none" data-bs-dismiss="modal">Batal</button>' +
                '<button type="button" class="btn fw-bold" style="color:#DC2626" onclick="nkDeleteItem(' + item.id + ')">Hapus</button>' +
                '</div>' +
                '</div></div></div>';

            document.body.insertAdjacentHTML('beforeend', html);
            const modal = new bootstrap.Modal(document.getElementById(modalId));
            modal.show();
        }

        async function nkDeleteItem(id) {
            try {
                const res = await fetch('/api/catatan-keuangan?id=' + encodeURIComponent(id), { method: 'DELETE' });
                const hasil = await res.json();
                if (!res.ok || !hasil.success) throw new Error(hasil.message || 'Gagal menghapus catatan');
                const modalEl = document.getElementById('nkDeleteModal');
                if (modalEl) {
                    const modal = bootstrap.Modal.getInstance(modalEl);
                    if (modal) modal.hide();
                }
                await nkFetchData();
                renderNoteKeuanganUi();
            } catch (e) {
                alert('Gagal menghapus catatan: ' + e.message);
            }
        }

        function nkPrint() {
            const r = nkRingkasan();
            const items = r.items;
            const d = new Date();
            const bulan = ['Jan', 'Feb', 'Mar', 'Apr', 'Mei', 'Jun', 'Jul', 'Agu', 'Sep', 'Okt', 'Nov', 'Des'];
            const stamp = String(d.getDate()).padStart(2, '0') + ' ' + bulan[d.getMonth()] + ' ' + d.getFullYear() + ' ' +
                String(d.getHours()).padStart(2, '0') + ':' + String(d.getMinutes()).padStart(2, '0');

            let rows = '';
            items.forEach((item, index) => {
                const masuk = item.jenis === 'masuk';
                const badge = masuk ? 'badge-masuk' : 'badge-keluar';
                const warna = masuk ? 'green' : 'red';
                const tanda = masuk ? '+' : '-';
                rows += '<tr>' +
                    '<td>' + (index + 1) + '</td>' +
                    '<td>' + nkEscape(item.tanggal) + '</td>' +
                    '<td>' + nkEscape(item.keterangan || '-') + '</td>' +
                    '<td class="text-center"><span class="' + badge + '">' + (masuk ? 'Masuk' : 'Keluar') + '</span></td>' +
                    '<td>' + nkEscape(item.kategori) + '</td>' +
                    '<td>' + nkEscape(item.sumberDana || '-') + '</td>' +
                    '<td class="text-right" style="font-weight:bold;color:' + warna + '">' + tanda + ' ' + nkRupiah(item.jumlah) + '</td>' +
                    '</tr>';
            });

            const html = '<html><head><style>' +
                'body{font-family:sans-serif;padding:20px;color:#333;}' +
                'h1{text-align:center;color:#222;margin-bottom:5px;font-size:20px;}' +
                '.meta-time{text-align:center;color:#666;margin-top:5px;margin-bottom:25px;font-size:11px;}' +
                '.summary-box{border:1px solid #ddd;border-radius:6px;padding:15px;background-color:#f9f9f9;margin-bottom:20px;line-height:1.6;}' +
                '.summary-box h3{margin:0 0 8px 0;color:#007bff;font-size:14px;border-bottom:1px solid #ddd;padding-bottom:6px;}' +
                '.summary-box p{text-align:left;margin:4px 0;color:#444;font-size:12px;}' +
                '.grid{display:flex;justify-content:space-between;}.grid-col{flex:1;}' +
                'table{width:100%;border-collapse:collapse;margin-top:15px;font-size:10px;}' +
                'th,td{border:1px solid #ddd;padding:6px;text-align:left;}' +
                'th{background-color:#f7f7f7;font-weight:bold;}' +
                '.text-right{text-align:right;}.text-center{text-align:center;}' +
                '.total-row{font-weight:bold;background-color:#fafafa;}' +
                '.badge-masuk{color:green;font-weight:bold;background-color:#e6fced;padding:2px 6px;border-radius:4px;display:inline-block;}' +
                '.badge-keluar{color:red;font-weight:bold;background-color:#ffebe9;padding:2px 6px;border-radius:4px;display:inline-block;}' +
                '</style></head><body>' +
                '<h1>Note Keuangan - My Singghara</h1>' +
                '<p class="meta-time">Dicetak pada: ' + stamp + '</p>' +
                '<div class="summary-box"><h3>Ringkasan Keuangan</h3><div class="grid">' +
                '<div class="grid-col">' +
                '<p>Saldo Tercatat: <strong>' + nkRupiah(r.saldo) + '</strong></p>' +
                '<p>Jumlah Catatan: <strong>' + r.jumlah + '</strong></p>' +
                '</div><div class="grid-col">' +
                '<p>Total Uang Masuk: <span style="color:green;font-weight:bold;">' + nkRupiah(r.totalMasuk) + '</span></p>' +
                '<p>Total Uang Keluar: <span style="color:red;font-weight:bold;">' + nkRupiah(r.totalKeluar) + '</span></p>' +
                '</div></div></div>' +
                '<table><thead><tr>' +
                '<th style="width:4%;">#</th><th style="width:12%;">Tanggal</th><th style="width:23%;">Keterangan</th>' +
                '<th style="width:9%;" class="text-center">Jenis</th><th style="width:14%;">Kategori</th>' +
                '<th style="width:14%;">Sumber Dana</th><th style="width:16%;" class="text-right">Jumlah</th>' +
                '</tr></thead><tbody>' + rows +
                '<tr class="total-row"><td colspan="6" class="text-right">Total Uang Masuk:</td><td class="text-right" style="color:green;">' + nkRupiah(r.totalMasuk) + '</td></tr>' +
                '<tr class="total-row"><td colspan="6" class="text-right">Total Uang Keluar:</td><td class="text-right" style="color:red;">' + nkRupiah(r.totalKeluar) + '</td></tr>' +
                '<tr class="total-row" style="background-color:#eef5ff;"><td colspan="6" class="text-right" style="color:#0056b3;">Saldo Tercatat:</td><td class="text-right" style="color:#0056b3;">' + nkRupiah(r.saldo) + '</td></tr>' +
                '</tbody></table></body></html>';

            const frame = document.createElement('iframe');
            frame.style.cssText = 'position:fixed;right:0;bottom:0;width:0;height:0;border:0;visibility:hidden;';
            document.body.appendChild(frame);
            const doc = frame.contentWindow.document;
            doc.open();
            doc.write(html);
            doc.close();
            setTimeout(() => {
                frame.contentWindow.focus();
                frame.contentWindow.print();
                setTimeout(() => {
                    if (document.body.contains(frame)) document.body.removeChild(frame);
                }, 2000);
            }, 500);
        }
        """
    }
}
