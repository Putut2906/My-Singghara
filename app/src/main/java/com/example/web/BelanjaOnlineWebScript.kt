package com.example.web

object BelanjaOnlineWebScript {
    fun getScript(dol: String): String {
        return """
        // =========================================================================
        // --- BELANJA ONLINE (Web Connect). Memakai /api/belanja_online (Room di HP).
        // =========================================================================
        window.boList = [];
        window.boEditId = 0;
        window.boFotoBase64 = '';

        function boEsc(s){ return (s===null||s===undefined)?'':String(s).replace(/[&<>"']/g,function(c){return {'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c];}); }
        function boRupiah(n){ n=Number(n)||0; return 'Rp ' + n.toLocaleString('id-ID'); }
        function boCard(inner){ return '<div class="card p-3 mb-3" style="border-radius:16px;border:1px solid var(--border-color);background:var(--card-bg);box-shadow:0 2px 10px rgba(15,23,42,0.08);">'+inner+'</div>'; }

        window.loadBelanjaOnline = async function(){
            var el=document.getElementById('tab-content'); if(!el) return;
            try{ window.boList = await (await fetch('/api/belanja_online')).json(); }catch(e){ window.boList=[]; }
            renderBelanjaOnline();
        };

        function boTotal(){ var h=parseFloat(document.getElementById('boHarga').value)||0; var o=parseFloat(document.getElementById('boOngkir').value)||0; var b=parseFloat(document.getElementById('boBiaya').value)||0; var p=parseFloat(document.getElementById('boPotongan').value)||0; return Math.max(0,(h+o+b-p)); }
        function boRecalcTotal(){ var t=document.getElementById('boTotalPreview'); if(t) t.textContent=boRupiah(boTotal()); }

        window.boShowForm = function(id){
            window.boEditId = id||0;
            var f=document.getElementById('boForm'); if(!f) return;
            f.style.display='block';
            var item = id ? window.boList.find(function(x){return x.id===id;}) : null;
            document.getElementById('boFormTitle').textContent = id ? 'Edit Transaksi' : 'Tambah Transaksi';
            document.getElementById('boTanggal').value = item ? item.tanggal : new Date().toISOString().slice(0,10);
            document.getElementById('boMarketplace').value = item ? item.marketplace : 'Tokopedia';
            document.getElementById('boToko').value = item ? (item.namaToko||'') : '';
            document.getElementById('boBarang').value = item ? (item.namaBarang||'') : '';
            document.getElementById('boKategori').value = item ? item.kategori : 'Kebutuhan Pokok';
            document.getElementById('boHarga').value = item ? item.hargaBarang : '';
            document.getElementById('boOngkir').value = item ? item.ongkir : '';
            document.getElementById('boBiaya').value = item ? item.biayaLayanan : '';
            document.getElementById('boPotongan').value = item ? item.potongan : '';
            document.getElementById('boMetode').value = item ? item.metodePembayaran : 'Transfer Bank';
            document.getElementById('boCatatan').value = item ? (item.catatan||'') : '';
            document.getElementById('boFoto').value='';
            window.boFotoBase64 = item ? (item.foto||'') : '';
            boRecalcTotal();
            f.scrollIntoView({behavior:'smooth'});
        };
        window.boHideForm = function(){ var f=document.getElementById('boForm'); if(f) f.style.display='none'; window.boEditId=0; };

        window.boOnFoto = function(inp){ var file=inp.files&&inp.files[0]; if(!file){return;} var r=new FileReader(); r.onload=function(){ window.boFotoBase64 = r.result; }; r.readAsDataURL(file); };

        window.boSave = async function(){
            var namaBarang=document.getElementById('boBarang').value.trim();
            if(!namaBarang){ alert('Nama barang wajib diisi.'); return; }
            var payload={
                tanggal: document.getElementById('boTanggal').value,
                marketplace: document.getElementById('boMarketplace').value,
                namaToko: document.getElementById('boToko').value.trim(),
                namaBarang: namaBarang,
                kategori: document.getElementById('boKategori').value,
                hargaBarang: parseFloat(document.getElementById('boHarga').value)||0,
                ongkir: parseFloat(document.getElementById('boOngkir').value)||0,
                biayaLayanan: parseFloat(document.getElementById('boBiaya').value)||0,
                potongan: parseFloat(document.getElementById('boPotongan').value)||0,
                metodePembayaran: document.getElementById('boMetode').value,
                catatan: document.getElementById('boCatatan').value.trim(),
                foto: window.boFotoBase64||''
            };
            var method = window.boEditId ? 'PUT' : 'POST';
            if(window.boEditId) payload.id = window.boEditId;
            await fetch('/api/belanja_online',{method:method,headers:{'Content-Type':'application/json'},body:JSON.stringify(payload)});
            boHideForm();
            await loadBelanjaOnline();
        };
        window.boDelete = async function(id){ if(!confirm('Hapus transaksi belanja online ini?')) return; await fetch('/api/belanja_online?id='+encodeURIComponent(id),{method:'DELETE'}); await loadBelanjaOnline(); };

        function renderBelanjaOnline(){
            var el=document.getElementById('tab-content'); if(!el) return;
            var list=window.boList||[];
            var total=list.reduce(function(a,x){return a+(Number(x.total)||0);},0);
            var optsMarket=['Tokopedia','Shopee','Blibli','Lazada','Amazon','Zalora','Bukalapak','Others'].map(function(m){return '<option value="'+boEsc(m)+'">'+boEsc(m)+'</option>';}).join('');
            var optsKat=['Kebutuhan Pokok','Skincare','Elektronik','Hobi','Fashion','Perlengkapan Rumah','Lain-lain'].map(function(k){return '<option value="'+boEsc(k)+'">'+boEsc(k)+'</option>';}).join('');
            var optsBayar=['Transfer Bank','QRIS','E-Wallet','Kartu Debit','Kartu Kredit','Paylater/Cicilan','COD','Tunai','Lainnya'].map(function(m){return '<option value="'+boEsc(m)+'">'+boEsc(m)+'</option>';}).join('');
            var form = boCard(
                '<div id="boFormTitle" class="fw-bold mb-2">Tambah Transaksi</div>'
                + '<div class="row g-2">'
                + '<div class="col-6 col-md-3"><label class="form-label small mb-1">Tanggal</label><input id="boTanggal" type="date" class="form-control form-control-sm"></div>'
                + '<div class="col-6 col-md-3"><label class="form-label small mb-1">Marketplace</label><select id="boMarketplace" class="form-select form-select-sm">'+optsMarket+'</select></div>'
                + '<div class="col-6 col-md-3"><label class="form-label small mb-1">Nama Toko</label><input id="boToko" class="form-control form-control-sm"></div>'
                + '<div class="col-6 col-md-3"><label class="form-label small mb-1">Nama Barang</label><input id="boBarang" class="form-control form-control-sm"></div>'
                + '<div class="col-6 col-md-3"><label class="form-label small mb-1">Kategori</label><select id="boKategori" class="form-select form-select-sm">'+optsKat+'</select></div>'
                + '<div class="col-6 col-md-3"><label class="form-label small mb-1">Harga Barang</label><input id="boHarga" type="number" min="0" class="form-control form-control-sm" oninput="boRecalcTotal()"></div>'
                + '<div class="col-6 col-md-3"><label class="form-label small mb-1">Ongkir</label><input id="boOngkir" type="number" min="0" class="form-control form-control-sm" oninput="boRecalcTotal()"></div>'
                + '<div class="col-6 col-md-3"><label class="form-label small mb-1">Biaya Layanan</label><input id="boBiaya" type="number" min="0" class="form-control form-control-sm" oninput="boRecalcTotal()"></div>'
                + '<div class="col-6 col-md-3"><label class="form-label small mb-1">Potongan</label><input id="boPotongan" type="number" min="0" class="form-control form-control-sm" oninput="boRecalcTotal()"></div>'
                + '<div class="col-6 col-md-3"><label class="form-label small mb-1">Metode Bayar</label><select id="boMetode" class="form-select form-select-sm">'+optsBayar+'</select></div>'
                + '<div class="col-6 col-md-3"><label class="form-label small mb-1">Total</label><div id="boTotalPreview" class="form-control form-control-sm" style="background:var(--sidebar-bg);font-weight:700;">Rp 0</div></div>'
                + '<div class="col-12 col-md-6"><label class="form-label small mb-1">Catatan</label><input id="boCatatan" class="form-control form-control-sm"></div>'
                + '<div class="col-12 col-md-3"><label class="form-label small mb-1">Foto (opsional)</label><input id="boFoto" type="file" accept="image/*" class="form-control form-control-sm" onchange="boOnFoto(this)"></div>'
                + '</div>'
                + '<div class="d-flex gap-2 mt-3"><button class="btn btn-sm btn-primary" onclick="boSave()"><i class="bi bi-check-lg me-1"></i>Simpan</button><button class="btn btn-sm btn-outline-secondary" onclick="boHideForm()">Batal</button></div>'
            );
            var rows = list.length===0 ? '<div class="p-3 text-secondary small">Belum ada transaksi.</div>' : list.slice().reverse().map(function(x){
                return '<tr><td class="small">'+boEsc(x.tanggal)+'</td><td class="small">'+boEsc(x.marketplace)+'</td><td class="small">'+boEsc(x.namaToko||'-')+'</td><td class="small fw-semibold">'+boEsc(x.namaBarang)+'</td><td class="small">'+boEsc(x.kategori)+'</td><td class="small text-end fw-bold">'+boRupiah(x.total)+'</td><td class="small">'+boEsc(x.metodePembayaran)+'</td>'
                    + '<td class="text-end"><button class="btn btn-sm btn-outline-primary py-0 px-2 me-1" onclick="boShowForm('+x.id+')"><i class="bi bi-pencil"></i></button><button class="btn btn-sm btn-outline-danger py-0 px-2" onclick="boDelete('+x.id+')"><i class="bi bi-trash"></i></button></td></tr>';
            }).join('');
            var summary = boCard('<div class="d-flex justify-content-between align-items-center"><div><div class="small text-secondary">Total Pengeluaran Belanja Online</div><div class="fw-bold" style="font-size:1.4rem;">'+boRupiah(total)+'</div></div><div class="text-end"><div class="small text-secondary">Jumlah Transaksi</div><div class="fw-bold" style="font-size:1.4rem;">'+list.length+'</div></div></div>');
            el.innerHTML = '<div class="container-fluid py-3" style="max-width:1100px;">'
                + '<div class="d-flex justify-content-between align-items-center mb-3"><h4 class="fw-bold mb-0"><i class="bi bi-bag-check me-2" style="color:#7C3AED;"></i>Belanja Online</h4><button class="btn btn-sm btn-primary" onclick="boShowForm(0)"><i class="bi bi-plus-lg me-1"></i>Tambah</button></div>'
                + '<div id="boForm" style="display:none;">'+form+'</div>'
                + summary
                + boCard('<div class="table-responsive"><table class="table table-sm align-middle mb-0"><thead><tr><th>Tanggal</th><th>Marketplace</th><th>Toko</th><th>Barang</th><th>Kategori</th><th class="text-end">Total</th><th>Metode</th><th class="text-end">Aksi</th></tr></thead><tbody>'+rows+'</tbody></table></div>')
                + '</div>';
        }
        """
    }
}
