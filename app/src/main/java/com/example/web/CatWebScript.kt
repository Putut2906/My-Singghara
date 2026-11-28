package com.example.web

/**
 * Halaman SIMULASI CAT pada Web Connect (akses PC, 100% offline).
 *
 * Disamakan dengan layar mobile (SimulasiCatScreen.kt + CatTipsScreen.kt):
 * 5 sub-menu = Input Soal, Simulasi Ujian, Hasil & Live Score, Grafik Progres, Tips.
 * Semua data dibaca dari CatStore yang sama lewat /api/cat/*, jadi soal yang dibuat
 * di PC langsung muncul di HP dan sebaliknya.
 *
 * Catatan gaya penulisan: whole body ini berada di dalam raw string Kotlin, jadi
 * TIDAK boleh ada tanda dollar sama sekali dan template literal JS dihindari -
 * semua HTML dirakit dengan concatenation string biasa.
 */
object CatWebScript {
    fun getScript(dol: String): String {
        return """
        // =========================================================================
        // SIMULASI CAT BKN - Web Connect
        // =========================================================================

        window.catActiveTab = 0;
        window.catTimerId = null;
        window.catExam = null;
        window.catDeadlineTs = 0;
        window.catPaketPanel = false;
        window.catTipsCopied = false;
        window.catForm = { kategori:'TWK', teks:'', opsi:['','','',''], kunci:0, bobot:[5,4,3,2], pembahasan:'', gambar:'' };
        window.catGForm = { twk:'', tiu:'', tkp:'' };
        window.catGMsg = null;
        window.catUlasanIdx = 0;
        window.catImportMsg = '';

        var CAT_TWK_C='#2563EB', CAT_TIU_C='#0F766E', CAT_TKP_C='#B45309';
        var CAT_BAR_TWK='#2563EB', CAT_BAR_TIU='#FACC15', CAT_BAR_TKP='#EC4899';
        var CAT_OK='#16A34A', CAT_NO='#DC2626';
        var CAT_LETTERS='ABCDEFG';

        function catEsc(s){ return (s===null||s===undefined)?'':String(s).replace(/[&<>"']/g,function(c){return {'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c];}); }
        function catPad(n){ return (n<10?'0':'')+n; }
        function catFmt(ms){ ms=Math.max(0,ms); var t=Math.floor(ms/1000),h=Math.floor(t/3600),m=Math.floor((t%3600)/60),s=t%60; return h>0?(h+':'+catPad(m)+':'+catPad(s)):(catPad(m)+':'+catPad(s)); }
        function catStopTimer(){ if(window.catTimerId){ clearInterval(window.catTimerId); window.catTimerId=null; } }
        function catCard(inner){ return '<div class="card p-3 mb-3" style="border-radius:16px;border:1px solid var(--border-color);background:var(--card-bg);box-shadow:0 2px 10px rgba(15,23,42,0.08);">'+inner+'</div>'; }

        function catToast(msg, ok){
            var t=document.createElement('div');
            t.className='toast align-items-center text-white border-0 show position-fixed bottom-0 end-0 m-3';
            t.style.backgroundColor=(ok===false)?'#DC2626':'#0F766E';
            t.style.borderRadius='12px'; t.style.zIndex='99999';
            t.innerHTML='<div class="d-flex p-3 align-items-center"><i class="bi '+(ok===false?'bi-exclamation-triangle-fill':'bi-check-circle-fill')+' fs-5 me-2"></i><div class="toast-body p-0 small fw-semibold">'+catEsc(msg)+'</div></div>';
            document.body.appendChild(t);
            setTimeout(function(){ try{ t.remove(); }catch(e){} }, 2600);
        }

        /** Dialog konfirmasi bergaya Material (pengganti window.confirm/alert). */
        function catConfirm(title, bodyHtml, okLabel, onOk, okClass){
            var back=document.createElement('div');
            back.style.cssText='position:fixed;top:0;left:0;right:0;bottom:0;background:rgba(0,0,0,.55);z-index:99998;display:flex;align-items:center;justify-content:center;padding:16px;';
            back.innerHTML='<div class="card shadow-lg" style="max-width:440px;width:100%;border-radius:18px;background:var(--card-bg);">'
                +'<div class="card-body p-4"><div class="fw-bold mb-2">'+catEsc(title)+'</div>'
                +'<div class="small text-secondary mb-3" style="line-height:1.6;">'+bodyHtml+'</div>'
                +'<div class="d-flex justify-content-end gap-2">'
                +'<button class="btn btn-sm btn-outline-secondary" data-catdlg="no">Batal</button>'
                +'<button class="btn btn-sm '+(okClass||'btn-danger')+'" data-catdlg="ok">'+catEsc(okLabel||'Ya')+'</button>'
                +'</div></div></div>';
            back.addEventListener('click', function(e){
                var el=e.target, act=el.getAttribute?el.getAttribute('data-catdlg'):null;
                if(act==='ok'){ back.remove(); onOk(); }
                else if(act==='no'||el===back){ back.remove(); }
            });
            document.body.appendChild(back);
        }

        /** Kartu input satu baris (label di atas), dipakai form Tambah Skor. */
        function catNumField(id, label, value, maxlen){
            return '<div class="col-4"><label class="form-label small mb-1">'+label+'</label>'
                +'<input id="'+id+'" class="form-control form-control-sm" inputmode="numeric" maxlength="'+(maxlen||3)+'" value="'+catEsc(value)+'" oninput="catDigitsOnly(\''+id+'\')"></div>';
        }
        window.catDigitsOnly = function(id){
            var el=document.getElementById(id); if(!el) return;
            el.value=el.value.replace(/[^0-9]/g,'').slice(0,3);
        };

        function catTanggal(ms){
            var d=new Date(ms), b=['Jan','Feb','Mar','Apr','Mei','Jun','Jul','Agu','Sep','Okt','Nov','Des'];
            return d.getDate()+' '+b[d.getMonth()]+' '+d.getFullYear()+' '+catPad(d.getHours())+':'+catPad(d.getMinutes());
        }
        function catTanggalPendek(ms){ var d=new Date(ms); return catPad(d.getDate())+'/'+catPad(d.getMonth()+1); }

        function catBadgeKategori(k){
            var c=(k==='TWK')?CAT_TWK_C:((k==='TIU')?CAT_TIU_C:CAT_TKP_C);
            return '<span class="badge fw-bold" style="background:'+c+'22;color:'+c+';border:1px solid '+c+'66;font-size:11px;border-radius:8px;padding:3px 8px;">'+catEsc(k)+'</span>';
        }
        function catCountChip(label){
            return '<span class="badge rounded-pill fw-bold px-3 py-2" style="background:rgba(124,58,237,.12);color:#7C3AED;font-size:12px;">'+catEsc(label)+'</span>';
        }
        function catImgTag(soalId, height){
            if(!soalId) return '';
            return '<img src="/api/cat/image?soal='+encodeURIComponent(soalId)+'" alt="Gambar soal" '
                +'style="width:100%;max-height:'+height+'px;object-fit:contain;border-radius:12px;background:var(--border-color);margin:8px 0;" '
                +'onerror="this.style.display=\'none\'">';
        }

        // ---------------- ENTRY POINT ----------------

        window.loadCat = function(initialTab){
            if(typeof initialTab === 'number') window.catActiveTab = initialTab;
            catStopTimer();
            var el = document.getElementById('tab-content'); if(!el) return;
            var tabs = [[0,'Input Soal','bi-ui-checks'],[1,'Simulasi Ujian','bi-clock-history'],[2,'Hasil & Live Score','bi-bar-chart-line-fill'],[3,'Grafik Progres','bi-graph-up-arrow'],[4,'Tips','bi-lightbulb']];
            var nav = '';
            tabs.forEach(function(t){ var on=(t[0]===window.catActiveTab)?' active':''; nav += '<button class="nav-link'+on+'" data-cattab="'+t[0]+'" onclick="catSetTab('+t[0]+')"><i class="bi '+t[2]+' me-1"></i>'+t[1]+'</button>'; });
            el.innerHTML = '<div class="container-fluid py-3" style="max-width:960px;">'
                + '<h4 class="fw-bold mb-2"><i class="bi bi-pencil-square me-2" style="color:#7C3AED;"></i>Simulasi CAT BKN</h4>'
                + '<div id="catPaketBar" class="mb-2"></div>'
                + '<ul class="nav nav-pills mb-3 gap-2">'+nav+'</ul>'
                + '<div id="catBody"></div></div>';
            catRenderPaketBar();
            catRenderTab();
        };

        window.catSetTab = function(n){
            window.catActiveTab = n;
            document.querySelectorAll('[data-cattab]').forEach(function(b){ b.classList.toggle('active', parseInt(b.getAttribute('data-cattab'))===n); });
            catStopTimer();
            catRenderTab();
        };

        function catRenderTab(){
            if(window.catActiveTab===0) catRenderInput();
            else if(window.catActiveTab===1) catRenderSimulasi();
            else if(window.catActiveTab===2) catRenderHasil();
            else if(window.catActiveTab===3) catRenderGrafik();
            else catRenderTips();
        }

        // ---------------- PAKET TRYOUT ----------------

        async function catRenderPaketBar(){
            var bar=document.getElementById('catPaketBar'); if(!bar) return;
            var d; try{ d=await (await fetch('/api/cat/paket')).json(); }catch(e){ return; }
            var list=d.list||[]; var aktif=d.aktif||'';
            var nama=(list.filter(function(p){return p.id===aktif;})[0]||{}).nama||'Paket';
            var head='<button class="btn btn-sm btn-outline-secondary d-inline-flex align-items-center gap-2" onclick="catTogglePaketPanel()">'
                +'<i class="bi bi-folder"></i><span class="fw-semibold">Paket: '+catEsc(nama)+'</span><span class="fw-bold">&#9662;</span></button>';
            var panel='';
            if(window.catPaketPanel){
                var rows=list.map(function(p){
                    var isAktif=(p.id===aktif);
                    return '<div class="d-flex align-items-center gap-2 py-1">'
                        +'<input class="form-check-input flex-shrink-0" type="radio" name="catPaketPilih" value="'+p.id+'"'+(isAktif?' checked':'')+' onchange="catPaketChanged(this.value)">'
                        +'<span class="flex-grow-1 small '+(isAktif?'fw-bold':'')+'">'+catEsc(p.nama)+'</span>'
                        +'<button class="btn btn-sm btn-outline-secondary py-0" title="Ganti nama" onclick="catRenamePaket(\''+p.id+'\')"><i class="bi bi-pencil"></i></button>'
                        +'<button class="btn btn-sm btn-outline-danger py-0" title="Hapus" onclick="catHapusPaket(\''+p.id+'\')"><i class="bi bi-trash"></i></button>'
                        +'</div>';
                }).join('');
                panel='<div class="mt-2 p-3" style="border:1px solid var(--border-color);border-radius:14px;background:var(--card-bg);">'
                    +'<div class="small fw-bold mb-2">Paket Tryout</div>'+rows
                    +'<div class="d-flex gap-2 mt-3">'
                    +'<input id="catPaketNama" class="form-control form-control-sm" placeholder="Nama paket baru (mis. Tryout 2)">'
                    +'<button class="btn btn-sm btn-primary flex-shrink-0" onclick="catBuatPaket()"><i class="bi bi-plus-lg"></i> Buat</button>'
                    +'</div></div>';
            }
            bar.innerHTML=head+panel;
        }

        window.catTogglePaketPanel = function(){ window.catPaketPanel=!window.catPaketPanel; catRenderPaketBar(); };

        window.catPaketChanged = async function(id){
            await fetch('/api/cat/paket',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({action:'select',id:id})});
            await catRenderPaketBar(); catRenderTab();
        };

        window.catBuatPaket = async function(){
            var el=document.getElementById('catPaketNama');
            var nama=(el&&el.value||'').trim();
            if(!nama){ catToast('Nama paket tidak boleh kosong.', false); if(el) el.focus(); return; }
            await fetch('/api/cat/paket',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({action:'create',nama:nama})});
            await catRenderPaketBar(); catRenderTab();
            catToast('Paket "'+nama+'" dibuat dan dijadikan paket aktif.');
        };

        window.catRenamePaket = function(id){
            var sel=document.querySelector('input[name=catPaketPilih][value="'+id+'"]');
            var lama=sel?sel.parentNode.querySelector('span').textContent.trim():'';
            catConfirm('Ganti Nama Paket','<input id="catRenameVal" class="form-control form-control-sm" value="'+catEsc(lama)+'">','Simpan',async function(){
                var v=(document.getElementById('catRenameVal').value||'').trim();
                if(!v){ catToast('Nama paket tidak boleh kosong.', false); return; }
                await fetch('/api/cat/paket',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({action:'rename',id:id,nama:v})});
                await catRenderPaketBar(); catRenderTab();
            },'btn-primary');
        };

        window.catHapusPaket = function(id){
            var semua=document.querySelectorAll('input[name=catPaketPilih]');
            if(semua.length<=1){ catToast('Minimal satu paket harus ada.', false); return; }
            var sel=document.querySelector('input[name=catPaketPilih][value="'+id+'"]');
            var nama=sel?sel.parentNode.querySelector('span').textContent.trim():'paket ini';
            catConfirm('Hapus Paket?','Paket <b>'+catEsc(nama)+'</b> beserta seluruh soal di dalamnya akan dihapus. Tidak bisa dibatalkan.','Hapus',async function(){
                await fetch('/api/cat/paket',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({action:'delete',id:id})});
                await catRenderPaketBar(); catRenderTab();
                catToast('Paket dihapus.');
            });
        };

        // ---------------- TAB 1: INPUT SOAL ----------------

        async function catRenderInput(){
            var b=document.getElementById('catBody'); if(!b) return;
            var bank=[]; try{ bank=await (await fetch('/api/cat/bank')).json(); }catch(e){}
            var cnt={TWK:0,TIU:0,TKP:0}; bank.forEach(function(s){ if(cnt[s.kategori]!==undefined) cnt[s.kategori]++; });
            var f=window.catForm;

            var ketersediaan=catCard('<div class="fw-bold mb-2">Ketersediaan Bank Soal</div>'
                +'<div class="d-flex flex-wrap gap-2">'+catCountChip('TWK '+cnt.TWK+'/30')+catCountChip('TIU '+cnt.TIU+'/35')+catCountChip('TKP '+cnt.TKP+'/45')+'</div>');

            var importCard = catCard(
                '<div class="fw-bold mb-1">Impor Soal dari File</div>'
                + '<div class="small text-secondary mb-2">Format didukung: .md, .txt, .docx, .xlsx. Contoh tiap soal: baris "1. Soal", lalu "A. opsi".."E. opsi", "KUNCI: B", "PEMBAHASAN: ..." (TKP: "BOBOT: 5,4,3,2,1").</div>'
                + '<input type="file" id="catFile" accept=".md,.txt,.docx,.xlsx" class="form-control form-control-sm mb-2">'
                + '<button class="btn btn-sm btn-outline-primary" onclick="catImportFile()"><i class="bi bi-upload me-1"></i>Pilih File &amp; Impor</button>'
                + (window.catImportMsg?'<div class="small fw-semibold mt-2" style="color:#7C3AED;">'+catEsc(window.catImportMsg)+'</div>':'')
            );

            var katPills=['TWK','TIU','TKP'].map(function(k){
                var on=(f.kategori===k);
                return '<button class="btn btn-sm '+(on?'btn-primary':'btn-outline-secondary')+'" onclick="catSetKategori(\''+k+'\')">'+k+'</button>';
            }).join('');

            var gambarBlock='';
            if(f.kategori==='TIU'){
                gambarBlock='<div class="small text-secondary mt-3 mb-1">Gambar Soal (opsional &mdash; untuk TIU)</div>';
                if(f.gambar){
                    gambarBlock+='<div style="max-height:180px;overflow:hidden;border-radius:12px;background:var(--border-color);padding:6px;text-align:center;">'
                        +'<img src="'+catEsc(f.gambarPreview)+'" style="max-height:168px;max-width:100%;object-fit:contain;"></div>'
                        +'<div class="d-flex gap-2 mt-2">'
                        +'<label class="btn btn-sm btn-outline-primary flex-fill mb-0"><i class="bi bi-image me-1"></i>Ganti Gambar<input type="file" accept="image/*" class="d-none" onchange="catPickImage(this)"></label>'
                        +'<button class="btn btn-sm btn-outline-danger flex-fill mb-0" onclick="catClearImage()"><i class="bi bi-trash me-1"></i>Hapus</button></div>';
                } else {
                    gambarBlock+='<label class="btn btn-sm btn-outline-primary w-100 mb-0 mt-1"><i class="bi bi-image me-1"></i>Impor Gambar Soal<input type="file" accept="image/*" class="d-none" onchange="catPickImage(this)"></label>';
                }
            }

            var opsiRows=f.opsi.map(function(v,i){
                var showDel=(f.opsi.length>2);
                var bobot=(f.kategori==='TKP')
                    ? '<select class="form-select form-select-sm flex-shrink-0" style="width:96px;" onchange="catSetBobot('+i+',this.value)">'
                        +[1,2,3,4,5].map(function(n){ return '<option value="'+n+'"'+(n===(f.bobot[i]||1)?' selected':'')+'>'+n+'</option>'; }).join('')+'</select>'
                    : '';
                return '<div class="d-flex align-items-center gap-2 mb-1">'
                    +'<input class="form-check-input flex-shrink-0" type="radio" name="catKunci" value="'+i+'"'+(f.kunci===i?' checked':'')+' onchange="catSetKunci('+i+')">'
                    +'<input class="form-control form-control-sm" placeholder="Opsi '+CAT_LETTERS.charAt(i)+'" value="'+catEsc(v)+'" oninput="catSetOpsi('+i+',this.value)" style="flex:1;min-width:0;">'
                    +bobot
                    +(showDel?'<button class="btn btn-sm btn-outline-danger py-0 flex-shrink-0" onclick="catDelOpsi('+i+')"><i class="bi bi-x-lg"></i></button>':'')
                    +'</div>';
            }).join('');

            var tambahOpsi=(f.opsi.length<5)
                ? '<button class="btn btn-sm btn-link text-decoration-none px-0" onclick="catAddOpsi()"><i class="bi bi-plus-lg me-1"></i>Tambah Opsi</button>' : '';

            var lengkap=f.opsi.every(function(v){ return v.trim().length>0; }) && (f.teks.trim().length>0 || f.gambar.length>0);

            var form = catCard(
                '<div class="fw-bold mb-2">Tambah Soal</div>'
                + '<div class="small text-secondary mb-1">Kategori</div>'
                + '<div class="d-flex gap-2 mb-3">'+katPills+'</div>'
                + '<label class="form-label small mb-1">Soal / pertanyaan</label>'
                + '<textarea id="catTeks" class="form-control form-control-sm mb-2" rows="2" oninput="catSetTeks(this.value)">'+catEsc(f.teks)+'</textarea>'
                + gambarBlock
                + '<div class="small text-secondary mt-3 mb-1">'+(f.kategori==='TKP'?'Opsi (atur bobot 1-5 tiap opsi; tandai yang terbaik = 5)':'Opsi (tandai jawaban benar)')+'</div>'
                + opsiRows + tambahOpsi
                + '<label class="form-label small mb-1 mt-3">Pembahasan (opsional)</label>'
                + '<textarea id="catPembahasan" class="form-control form-control-sm" rows="2" oninput="catSetPembahasan(this.value)">'+catEsc(f.pembahasan)+'</textarea>'
                + '<button class="btn btn-sm btn-primary mt-3 w-100 py-2" onclick="catAddSoal()"'+(lengkap?'':' disabled')+'>Simpan Soal</button>'
            );

            var rows = bank.length===0 ? '<div class="p-3 text-secondary small">Belum ada soal. Tambahkan di atas.</div>' :
                bank.slice().reverse().map(function(s){
                    var extra=(s.kategori==='TKP' && s.bobot && s.bobot.length)?' &bull; bobot ['+s.bobot.join(', ')+']':'';
                    var thumb=(s.gambar&&s.gambar.length)?catImgTag(s.id,120):'';
                    return '<div class="d-flex align-items-center gap-2 p-2 border-bottom" style="border-color:var(--border-color);">'
                        + catBadgeKategori(s.kategori)
                        + '<div class="flex-grow-1" style="min-width:0;">'
                        + '<div class="small fw-semibold" style="display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden;">'+catEsc(s.teks)+'</div>'
                        + thumb
                        + '<div class="text-secondary" style="font-size:11px;">'+s.opsi.length+' opsi &bull; kunci: '+CAT_LETTERS.charAt(s.kunci)+extra+'</div></div>'
                        + '<button class="btn btn-sm btn-outline-danger py-0 flex-shrink-0" onclick="catDelSoal(\''+s.id+'\')"><i class="bi bi-trash"></i></button></div>';
                }).join('');
            var listCard = '<div class="card p-0 overflow-hidden mb-3" style="border-radius:16px;border:1px solid var(--border-color);background:var(--card-bg);box-shadow:0 2px 10px rgba(15,23,42,0.08);">'
                + '<div class="p-3 fw-bold border-bottom" style="border-color:var(--border-color);font-size:15px;">Daftar Soal ('+bank.length+')</div>'
                + '<div>'+rows+'</div></div>';
            b.innerHTML = ketersediaan + importCard + form + listCard;
        }

        window.catSetKategori = function(k){ window.catForm.kategori=k; if(window.catForm.kunci>=window.catForm.opsi.length) window.catForm.kunci=0; if(k!=='TIU') window.catForm.gambar=''; catRenderInput(); };
        window.catSetTeks = function(v){ window.catForm.teks=v; };
        window.catSetPembahasan = function(v){ window.catForm.pembahasan=v; };
        window.catSetOpsi = function(i,v){ window.catForm.opsi[i]=v; };
        window.catSetKunci = function(i){ window.catForm.kunci=i; };
        window.catSetBobot = function(i,v){ window.catForm.bobot[i]=parseInt(v)||1; };
        window.catAddOpsi = function(){ var f=window.catForm; if(f.opsi.length<5){ f.opsi.push(''); f.bobot.push(1); catRenderInput(); } };
        window.catDelOpsi = function(i){ var f=window.catForm; if(f.opsi.length>2){ f.opsi.splice(i,1); f.bobot.splice(i,1); if(f.kunci>=f.opsi.length) f.kunci=0; catRenderInput(); } };
        window.catClearImage = function(){ window.catForm.gambar=''; window.catForm.gambarPreview=''; catRenderInput(); };

        window.catPickImage = function(inp){
            var f=inp&&inp.files&&inp.files[0]; if(!f) return;
            var reader=new FileReader();
            reader.onload=function(){
                var bytes=new Uint8Array(reader.result), bin='', chunk=0x8000;
                for(var i=0;i<bytes.length;i+=chunk){ bin+=String.fromCharCode.apply(null,bytes.subarray(i,i+chunk)); }
                fetch('/api/cat/image',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({data:btoa(bin),filename:f.name})})
                    .then(function(r){return r.json();})
                    .then(function(j){
                        if(j&&j.success){ window.catForm.gambar=j.path; window.catForm.gambarPreview=reader.result; catRenderInput(); catToast('Gambar soal tersimpan.'); }
                        else { catToast('Gagal menyimpan gambar: '+((j&&j.message)||''), false); }
                    })
                    .catch(function(e){ catToast('Gagal menyimpan gambar.', false); });
            };
            reader.readAsArrayBuffer(f);
        };

        window.catImportFile = function(){
            var inp=document.getElementById('catFile'); var f=inp&&inp.files[0]; if(!f){ catToast('Pilih file dulu.', false); return; }
            var reader=new FileReader();
            reader.onload=function(){
                var bytes=new Uint8Array(reader.result); var bin=''; var chunk=0x8000;
                for(var i=0;i<bytes.length;i+=chunk){ bin+=String.fromCharCode.apply(null,bytes.subarray(i,i+chunk)); }
                var b64=btoa(bin);
                window.catImportMsg='Mengimpor...'; catRenderInput();
                fetch('/api/cat/import',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({filename:f.name,data:b64})})
                    .then(function(r){return r.json();})
                    .then(function(j){
                        if(j&&j.success){
                            window.catImportMsg=(j.count>0)?('Berhasil impor '+j.count+' soal.'):('Tidak ada soal terbaca (cek format/ekstensi: .md/.txt/.docx/.xlsx).');
                        } else { window.catImportMsg='Gagal impor: '+((j&&j.message)||''); }
                        catRenderInput();
                    })
                    .catch(function(e){ window.catImportMsg='Gagal impor: '+e; catRenderInput(); });
            };
            reader.readAsArrayBuffer(f);
        };

        window.catAddSoal = async function(){
            var f=window.catForm;
            var opsi=f.opsi.map(function(v){ return v.trim(); });
            var teks=f.teks.trim();
            if((!teks && !f.gambar) || opsi.some(function(v){ return v.length===0; })){ catToast('Isi soal dan semua opsi.', false); return; }
            var payload={kategori:f.kategori,teks:teks,opsi:opsi,kunci:f.kunci,pembahasan:f.pembahasan.trim(),gambar:(f.kategori==='TIU')?f.gambar:''};
            if(f.kategori==='TKP'){ payload.bobot=f.opsi.map(function(v,i){ return f.bobot[i]||1; }); }
            await fetch('/api/cat/bank',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(payload)});
            window.catForm={ kategori:f.kategori, teks:'', opsi:['','','',''], kunci:0, bobot:[5,4,3,2], pembahasan:'', gambar:'' };
            catRenderInput();
            catToast('Soal disimpan.');
        };

        window.catDelSoal = async function(id){ await fetch('/api/cat/bank?id='+encodeURIComponent(id),{method:'DELETE'}); catRenderInput(); };

        // ---------------- TAB 2: SIMULASI UJIAN ----------------

        async function catRenderSimulasi(){
            var b=document.getElementById('catBody'); if(!b) return;
            var sesi; try{ sesi=await (await fetch('/api/cat/sesi')).json(); }catch(e){ sesi={active:false}; }
            var pk={aktif:'',maks:5}; try{ var pj=await (await fetch('/api/cat/paket')).json(); pk.aktif=pj.aktif||''; pk.maks=pj.maks||5; }catch(e){}
            var riw=[]; try{ riw=await (await fetch('/api/cat/riwayat')).json(); }catch(e){}
            var terpakai=riw.filter(function(r){ return r.paketId===pk.aktif; }).length;

            if(!sesi || !sesi.active || sesi.selesai){
                var bank=[]; try{ bank=await (await fetch('/api/cat/bank')).json(); }catch(e){}
                var cnt={TWK:0,TIU:0,TKP:0}; bank.forEach(function(s){ if(cnt[s.kategori]!==undefined) cnt[s.kategori]++; });
                var total=cnt.TWK+cnt.TIU+cnt.TKP;
                var habis=(terpakai>=pk.maks);
                var html='';
                if(sesi && sesi.active && sesi.selesai){
                    html+=catCard('<div class="fw-bold mb-1">Sesi sebelumnya sudah selesai.</div>'
                        +'<div class="small text-secondary">Lihat hasil di tab \'Hasil &amp; Live Score\', atau mulai sesi baru di bawah.</div>');
                }
                html+=catCard('<div class="fw-bold mb-2">Petunjuk Ujian (SKD CPNS)</div>'
                    +'<div class="small" style="line-height:1.9;">'
                    +'<div>&bull; Durasi: 100 menit (timer berjalan dari waktu sistem; menutup aplikasi tidak mereset waktu).</div>'
                    +'<div>&bull; Jumlah soal: 110 (TWK 30, TIU 35, TKP 45).</div>'
                    +'<div>&bull; Penilaian TWK/TIU: benar = 5, salah/kosong = 0.</div>'
                    +'<div>&bull; Penilaian TKP: skala 1-5 per opsi (tidak bernilai 0 bila dijawab).</div>'
                    +'<div>&bull; Passing grade: TWK 65, TIU 80, TKP 156.</div></div>'
                    +'<div class="small '+(total===0?'text-danger':'text-secondary')+' mt-2">Ketersediaan bank soal Anda: TWK '+cnt.TWK+', TIU '+cnt.TIU+', TKP '+cnt.TKP+' (total '+total+').</div>'
                    +(total<110?'<div class="small text-danger mt-1">Bank soal belum lengkap (ideal 110). Ujian akan memakai soal seadanya.</div>'
                        +'<button class="btn btn-sm btn-link text-decoration-none px-0" onclick="catSetTab(0)">Ke Input Soal</button>':''));
                html+=catCard('<div class="d-flex justify-content-between align-items-center"><div class="fw-bold">Percobaan Ujian</div>'
                    +(terpakai>0?'<button class="btn btn-sm btn-link text-danger text-decoration-none p-0" onclick="catResetPercobaan('+terpakai+')">Reset</button>':'')+'</div>'
                    +'<div class="small '+(habis?'text-danger':'text-secondary')+' mt-1">Sudah dipakai: '+terpakai+' dari '+pk.maks+'. Sisa: '+Math.max(0,pk.maks-terpakai)+'.</div>'
                    +(habis?'<div class="small text-danger mt-1">Batas '+pk.maks+' percobaan tercapai &mdash; simulasi ujian dikunci. Tekan Reset untuk mengulang.</div>':''));
                html+='<button class="btn btn-primary w-100 py-2 d-flex align-items-center justify-content-center gap-2" '+((total===0||habis)?'disabled':'')+' onclick="catStart()">'
                    +'<i class="bi bi-play-fill"></i><span>'+(habis?'Batas Percobaan Tercapai':'Mulai Ujian')+'</span></button>';
                b.innerHTML=html;
                return;
            }

            var order=sesi.order||[]; var jawaban={}; Object.keys(sesi.jawaban||{}).forEach(function(k){ jawaban[parseInt(k)]=sesi.jawaban[k]; });
            var current=sesi.currentIndex||0; var bankMap={};
            try{ (await (await fetch('/api/cat/bank')).json()).forEach(function(s){ bankMap[s.id]=s; }); }catch(e){}
            // Sisa waktu diambil dari HP (sisaMs), bukan jam PC, supaya tidak melenceng
            // bila jam komputer berbeda beberapa menit.
            window.catDeadlineTs = Date.now() + (sesi.sisaMs||0);
            window.catExam={order:order,jawaban:jawaban,current:current,bankMap:bankMap,pg:sesi.pg||{twk:65,tiu:80,tkp:156}};

            b.innerHTML = catCard(
                '<div class="d-flex justify-content-between align-items-center">'
                + '<div><div class="small text-secondary">Sisa Waktu</div><div id="catClock" class="fw-bold" style="font-size:1.6rem;color:#7C3AED;">--:--</div></div>'
                + '<div class="text-end"><div class="small text-secondary">Passing Grade</div><div class="small fw-bold">TWK '+window.catExam.pg.twk+' &bull; TIU '+window.catExam.pg.tiu+' &bull; TKP '+window.catExam.pg.tkp+'</div></div></div>'
                + '<div class="d-flex gap-4 mt-2">'
                + '<div class="text-center"><div id="catAnsCount" class="fw-bold" style="font-size:1.15rem;color:#7C3AED;">0</div><div class="small text-secondary">Dijawab</div></div>'
                + '<div class="text-center"><div id="catUnCount" class="fw-bold" style="font-size:1.15rem;color:#7C3AED;">0</div><div class="small text-secondary">Belum</div></div>'
                + '<div class="text-center"><div id="catLiveScore" class="fw-bold" style="font-size:1.15rem;color:#7C3AED;">0</div><div class="small text-secondary">Live Score</div></div>'
                + '</div>')
                + '<div class="row g-3"><div class="col-lg-8"><div id="catQuestion"></div></div>'
                + '<div class="col-lg-4"><div class="card p-2" style="border-radius:16px;border:1px solid var(--border-color);background:var(--card-bg);box-shadow:0 2px 10px rgba(15,23,42,0.08);">'
                + '<div class="small fw-bold mb-2 px-1">&#9776; Nomor Soal</div><div id="catNav" class="d-flex flex-wrap gap-1"></div></div></div></div>'
                + '<div class="d-flex gap-2 mt-3">'
                + '<button class="btn btn-outline-secondary btn-sm flex-fill" onclick="catSkip()"><i class="bi bi-skip-end me-1"></i>Lewati</button>'
                + '<button class="btn btn-primary btn-sm flex-grow-1" style="flex:1.4;" onclick="catSaveNext()"><i class="bi bi-check-lg me-1"></i>Simpan &amp; Lanjut</button>'
                + '<button class="btn btn-danger btn-sm flex-fill" onclick="catFinish(false)"><i class="bi bi-stop-circle me-1"></i>Selesai</button></div>';

            catRenderQuestion(); catRenderNav(); catUpdateHeader();
            catStopTimer();
            window.catTimerId=setInterval(function(){
                var rem=window.catDeadlineTs-Date.now();
                var c=document.getElementById('catClock');
                if(c){ c.textContent=catFmt(rem); c.style.color=(rem<60000)?CAT_NO:'#7C3AED'; }
                if(rem<=0){ catStopTimer(); catFinish(true); }
            },1000);
        }

        function catLiveScore(){
            var ex=window.catExam; if(!ex) return 0; var s=0;
            ex.order.forEach(function(id,idx){
                var q=ex.bankMap[id]; var j=ex.jawaban[idx];
                if(q && j!==undefined && j>=0){
                    s += (q.kategori==='TKP') ? (q.bobot&&q.bobot[j]!==undefined ? q.bobot[j] : 0) : (j===q.kunci?5:0);
                }
            });
            return s;
        }

        function catRenderQuestion(){
            var ex=window.catExam; var q=ex.bankMap[ex.order[ex.current]]; var el=document.getElementById('catQuestion'); if(!el) return;
            if(!q){ el.innerHTML=catCard('<div class="text-danger small">Soal tidak ditemukan.</div>'); return; }
            var img=(q.gambar&&q.gambar.length)?catImgTag(q.id,220):'';
            var opsiHtml=q.opsi.map(function(o,i){
                var sel=(ex.jawaban[ex.current]===i);
                return '<label class="d-flex align-items-center gap-2 p-2 mb-1" style="border-radius:12px;cursor:pointer;background:'+(sel?'rgba(124,58,237,0.12)':'transparent')+';">'
                    + '<input class="form-check-input" type="radio" name="catOpt" value="'+i+'"'+(sel?' checked':'')+' onchange="catSelect('+i+')">'
                    + '<span class="small fw-semibold">'+CAT_LETTERS.charAt(i)+'. </span><span class="small">'+catEsc(o)+'</span></label>';
            }).join('');
            el.innerHTML = catCard(
                '<div class="d-flex align-items-center gap-2 mb-2">'+catBadgeKategori(q.kategori)
                + '<span class="small text-secondary flex-grow-1">Soal '+(ex.current+1)+' / '+ex.order.length+'</span></div>'
                + '<div class="fw-semibold mb-1" style="font-size:15px;">'+catEsc(q.teks)+'</div>'+img+opsiHtml
            );
        }

        function catRenderNav(){
            var ex=window.catExam; var el=document.getElementById('catNav'); if(!el) return;
            var h='';
            for(var i=0;i<ex.order.length;i++){
                var answered=(ex.jawaban[i]!==undefined); var active=(i===ex.current);
                var bg=active?'#7C3AED':(answered?CAT_OK:CAT_NO);
                h += '<button onclick="catGo('+i+')" style="width:32px;height:32px;border-radius:8px;color:#fff;font-size:12px;font-weight:700;border:'+(active?'2px solid #000':'none')+';background:'+bg+';">'+(i+1)+'</button>';
            }
            el.innerHTML=h;
        }

        function catUpdateHeader(){
            var ex=window.catExam; if(!ex) return;
            var ans=Object.keys(ex.jawaban).length;
            var ac=document.getElementById('catAnsCount'); var uc=document.getElementById('catUnCount'); var ls=document.getElementById('catLiveScore');
            if(ac) ac.textContent=ans;
            if(uc) uc.textContent=(ex.order.length-ans);
            if(ls) ls.textContent=catLiveScore();
            var c=document.getElementById('catClock');
            if(c){ var rem=window.catDeadlineTs-Date.now(); c.textContent=catFmt(rem); c.style.color=(rem<60000)?CAT_NO:'#7C3AED'; }
        }

        window.catSelect = async function(i){
            var ex=window.catExam; if(!ex) return;
            ex.jawaban[ex.current]=i; catRenderQuestion(); catRenderNav(); catUpdateHeader();
            try{ var r=await (await fetch('/api/cat/sesi',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({action:'answer',index:ex.current,opsi:i})})).json(); if(r && r.expired) catFinish(true); }catch(e){}
        };
        function catGo(i){ var ex=window.catExam; if(!ex) return; ex.current=i; catRenderQuestion(); catRenderNav(); catUpdateHeader(); fetch('/api/cat/sesi',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({action:'navigate',current:i})}); }
        window.catSkip = function(){ var ex=window.catExam; if(ex) catGo(Math.min(ex.current+1, ex.order.length-1)); };
        window.catSaveNext = function(){ var ex=window.catExam; if(ex) catGo(Math.min(ex.current+1, ex.order.length-1)); };
        window.catStart = async function(){
            var r=await (await fetch('/api/cat/sesi',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({action:'start'})})).json();
            if(r && r.success===false){ catToast(r.message||'Tidak bisa memulai ujian.', false); }
            catRenderSimulasi();
        };
        window.catFinish = async function(auto){
            var ex=window.catExam; if(!ex) return;
            if(!auto){
                var un=ex.order.length-Object.keys(ex.jawaban).length;
                catConfirm('Selesai Ujian?','Masih ada '+un+' soal belum dijawab. Yakin mengakhiri tes? Jawaban tidak dapat diubah setelah selesai.','Ya, Selesai',async function(){
                    await catDoFinish();
                });
                return;
            }
            await catDoFinish();
        };
        async function catDoFinish(){
            catStopTimer();
            await fetch('/api/cat/sesi',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({action:'finish'})});
            window.catExam=null; window.catUlasanIdx=0; window.catActiveTab=2; window.loadCat(2);
        }

        window.catResetPercobaan = function(terpakai){
            catConfirm('Reset percobaan?','Riwayat percobaan paket ini ('+terpakai+') akan dihapus sehingga ujian bisa dimulai lagi dari percobaan ke-1. Tidak bisa dibatalkan.','Reset',async function(){
                await fetch('/api/cat/riwayat',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({action:'reset'})});
                catRenderSimulasi();
                catToast('Percobaan direset.');
            });
        };

        // ---------------- TAB 3: HASIL & LIVE SCORE ----------------

        async function catRenderHasil(){
            var b=document.getElementById('catBody'); if(!b) return;
            var data; try{ data=await (await fetch('/api/cat/hasil')).json(); }catch(e){ data={available:false}; }
            if(!data || !data.available){
                b.innerHTML=catCard('<div class="fw-bold mb-1">Belum ada hasil</div><div class="small text-secondary mb-2">Selesaikan simulasi ujian terlebih dahulu untuk melihat skor dan pembahasan.</div><button class="btn btn-primary btn-sm w-100 py-2" onclick="catSetTab(1)">Mulai Simulasi</button>');
                return;
            }
            var h=data.hasil;
            var benar=h.ulasan.filter(function(u){ return u.benar; }).length;
            var riw=[]; try{ riw=await (await fetch('/api/cat/riwayat')).json(); }catch(e){}
            var riwPaket=riw.filter(function(r){ return r.paketId===data.paketId; });

            var header=catCard('<div class="d-flex justify-content-between align-items-center mb-2">'
                +'<div class="fw-bold" style="font-size:16px;">Hasil Ujian</div>'
                +'<span class="badge fw-bold" style="background:'+(h.lulus?CAT_OK+'26':CAT_NO+'26')+';color:'+(h.lulus?CAT_OK:CAT_NO)+';">'+(h.lulus?'LULUS':'GAGAL')+'</span></div>'
                +'<div class="d-flex gap-4">'
                +'<div class="text-center"><div class="fw-bold" style="font-size:1.1rem;color:#7C3AED;">'+(data.percobaanKe||1)+'/'+(data.maks||5)+'</div><div class="small text-secondary">Percobaan</div></div>'
                +'<div class="text-center"><div class="fw-bold" style="font-size:1.1rem;color:#7C3AED;">'+h.jumlahDijawab+'/'+h.jumlahSoal+'</div><div class="small text-secondary">Dijawab</div></div>'
                +'<div class="text-center"><div class="fw-bold" style="font-size:1.1rem;color:#7C3AED;">'+benar+'</div><div class="small text-secondary">Benar</div></div>'
                +'</div>');

            function bar(lab,val,maks,pg,lulus){
                var pct=Math.max(0,Math.min(100,Math.round(val/maks*100)));
                return '<div class="d-flex align-items-center gap-2 mb-2"><div style="width:40px;font-weight:700;">'+lab+'</div>'
                    + '<div class="flex-grow-1" style="height:10px;border-radius:6px;background:var(--border-color);overflow:hidden;"><div style="height:100%;width:'+pct+'%;background:'+(lulus?CAT_OK:CAT_NO)+';"></div></div>'
                    + '<div style="width:70px;text-align:right;font-size:12px;font-weight:700;">'+val+'/'+maks+'</div>'
                    + '<span class="badge" style="background:'+(lulus?CAT_OK+'26':CAT_NO+'26')+';color:'+(lulus?CAT_OK:CAT_NO)+';">'+(lulus?'&ge;':'&lt;')+pg+'</span></div>';
            }
            var rincian=catCard('<div class="fw-bold mb-2">Total &amp; Rincian Skor</div>'
                +'<div class="d-flex align-items-center gap-3 flex-wrap">'
                + catDoughnut(h)
                + '<div class="flex-grow-1" style="min-width:260px;">'
                + bar('TWK',h.twk,150,h.pg.twk,h.twkLulus) + bar('TIU',h.tiu,175,h.pg.tiu,h.tiuLulus) + bar('TKP',h.tkp,225,h.pg.tkp,h.tkpLulus)
                +'</div></div>');

            var riwayatHtml='';
            if(riwPaket.length>0){
                var rowsR=riwPaket.slice().reverse().map(function(r,idx){
                    var no=riwPaket.length-idx;
                    return '<div class="d-flex align-items-center gap-2 py-2 border-bottom" style="border-color:var(--border-color);">'
                        +'<span class="rounded-circle d-flex align-items-center justify-content-center flex-shrink-0" style="width:26px;height:26px;background:rgba(124,58,237,.15);color:#7C3AED;font-size:12px;font-weight:700;">'+no+'</span>'
                        +'<div class="flex-grow-1" style="min-width:0;"><div class="small fw-semibold">'+catTanggal(r.tanggal)+'</div>'
                        +'<div class="small text-secondary">TWK '+r.twk+' &bull; TIU '+r.tiu+' &bull; TKP '+r.tkp+'</div></div>'
                        +'<span class="fw-bold" style="color:#7C3AED;">'+r.total+'</span>'
                        +'<span class="badge" style="background:'+(r.lulus?CAT_OK+'26':CAT_NO+'26')+';color:'+(r.lulus?CAT_OK:CAT_NO)+';">'+(r.lulus?'LULUS':'GAGAL')+'</span></div>';
                }).join('');
                riwayatHtml=catCard('<div class="d-flex justify-content-between align-items-center mb-1">'
                    +'<div class="fw-bold">Riwayat Percobaan</div>'
                    +'<button class="btn btn-sm btn-link text-danger text-decoration-none p-0" onclick="catResetPercobaanDariHasil('+riwPaket.length+')">Reset</button></div>'
                    +rowsR);
            }

            window.catUlasanList = h.ulasan;
            if(window.catUlasanIdx>=h.ulasan.length) window.catUlasanIdx=0;

            b.innerHTML = header + rincian + riwayatHtml
                + '<div id="catChart"></div>'
                + '<div class="fw-bold mb-2" style="font-size:15px;">Ulasan &amp; Pembahasan</div>'
                + '<div id="catUlasan"></div>';
            catRenderChart();
            catRenderUlasan();
        }

        /** Doughnut TWK/TIU/TKP dengan total di tengah (meniru CatDoughnut di mobile). */
        function catDoughnut(h){
            var vals=[h.twk,h.tiu,h.tkp], cols=[CAT_BAR_TWK,CAT_BAR_TIU,CAT_BAR_TKP], labs=['TWK','TIU','TKP'];
            var tot=vals.reduce(function(a,b){ return a+(b||0); },0);
            var R=52, C=2*Math.PI*R, cx=64, cy=64, off=0, arcs='';
            if(tot<=0){
                arcs='<circle cx="'+cx+'" cy="'+cy+'" r="'+R+'" fill="none" stroke="var(--border-color)" stroke-width="16"/>';
            } else {
                vals.forEach(function(v,i){
                    var frac=v/tot, len=C*frac;
                    if(len>0){
                        arcs+='<circle cx="'+cx+'" cy="'+cy+'" r="'+R+'" fill="none" stroke="'+cols[i]+'" stroke-width="16" stroke-linecap="round" '
                            +'stroke-dasharray="'+Math.max(0,len-2)+' '+(C-Math.max(0,len-2))+'" stroke-dashoffset="'+(-off)+'" transform="rotate(-90 '+cx+' '+cy+')"></circle>';
                    }
                    off+=len;
                });
            }
            var legend=vals.map(function(v,i){
                return '<div class="d-flex align-items-center gap-2 small mb-1"><span style="width:10px;height:10px;border-radius:50%;background:'+cols[i]+';"></span>'+labs[i]+' <b class="ms-1">'+v+'</b></div>';
            }).join('');
            return '<div class="text-center"><svg viewBox="0 0 128 128" style="width:128px;height:128px;">'+arcs
                +'<text x="64" y="52" text-anchor="middle" font-size="9" fill="#64748b">TOTAL</text>'
                +'<text x="64" y="76" text-anchor="middle" font-size="24" font-weight="800" fill="'+(h.lulus?CAT_OK:CAT_NO)+'">'+h.total+'</text>'
                +'<text x="64" y="90" text-anchor="middle" font-size="9" fill="#64748b">'+(h.lulus?'LULUS / 550':'GAGAL / 550')+'</text>'
                +'</svg>'+legend+'</div>';
        }

        function catRenderUlasan(){
            var host=document.getElementById('catUlasan'); if(!host) return;
            var list=window.catUlasanList||[];
            if(list.length===0){ host.innerHTML=catCard('<div class="small text-secondary">Tidak ada soal.</div>'); return; }
            var i=window.catUlasanIdx, u=list[i];
            var img=(u.gambar&&u.gambar.length)?catImgTag(u.soalId||'',220):'';
            var opsiHtml=u.opsi.map(function(o,k){
                var isK=(k===u.kunci), isJ=(k===u.jawaban);
                var bg=isK?'rgba(22,163,74,.12)':(isJ?'rgba(220,38,38,.12)':'transparent');
                return '<div class="d-flex align-items-center gap-2 mb-1 px-2 py-2" style="border-radius:12px;background:'+bg+';">'
                    +'<span class="small fw-bold text-secondary">'+CAT_LETTERS.charAt(k)+'. </span>'
                    +'<span class="small flex-grow-1">'+catEsc(o)+'</span>'
                    +(isK?'<span class="small fw-bold" style="color:'+CAT_OK+';">&#10003; kunci</span>':'')
                    +(isJ?'<span class="small fw-bold ms-1" style="color:#7C3AED;">jawabanmu</span>':'')+'</div>';
            }).join('');
            var navCells='';
            for(var n=0;n<list.length;n++){
                var on=(n===i), answered=list[n].benar;
                var bg=on?'#7C3AED':(answered?CAT_OK:CAT_NO);
                navCells+='<button onclick="catUlasanGo('+n+')" style="width:30px;height:30px;border-radius:8px;color:#fff;font-size:11px;font-weight:700;border:'+(on?'2px solid #000':'none')+';background:'+bg+';">'+(n+1)+'</button>';
            }
            host.innerHTML = catCard(
                '<div class="d-flex align-items-center gap-2 mb-2 flex-wrap">'+catBadgeKategori(u.kategori)
                +'<span class="small text-secondary">Soal '+(i+1)+' / '+list.length+'</span>'
                +'<span class="badge ms-auto fw-bold" style="background:'+((u.benar)?CAT_OK+'26':CAT_NO+'26')+';color:'+((u.benar)?CAT_OK:CAT_NO)+';">'
                +(u.benar?'BENAR +'+u.poin:'SALAH +'+u.poin)+'</span></div>'
                +'<div class="fw-semibold mb-1" style="font-size:15px;">'+catEsc(u.teks)+'</div>'+img+opsiHtml
                +(u.jawaban<0?'<div class="small mt-1" style="color:'+CAT_NO+';">Tidak dijawab.</div>':'')
                +(u.pembahasan?'<div class="mt-3 p-3" style="border-radius:12px;background:var(--border-color);"><div class="small fw-bold mb-1" style="color:#7C3AED;">Pembahasan</div><div class="small">'+catEsc(u.pembahasan)+'</div></div>':'')
                +'<div class="d-flex flex-wrap gap-1 mt-3">'+navCells+'</div>')
                +'<div class="d-flex gap-2 mb-3">'
                +'<button class="btn btn-outline-secondary btn-sm flex-fill" onclick="catUlasanGo('+(i-1)+')"'+(i<=0?' disabled':'')+'>&#8592; Sebelumnya</button>'
                +'<button class="btn btn-primary btn-sm flex-fill" onclick="catUlasanGo('+(i+1)+')"'+(i>=list.length-1?' disabled':'')+'>Berikutnya &#8594;</button></div>';
        }
        window.catUlasanGo = function(n){
            var list=window.catUlasanList||[]; if(list.length===0) return;
            window.catUlasanIdx=Math.max(0,Math.min(list.length-1,n));
            catRenderUlasan();
        };

        window.catResetPercobaanDariHasil = function(n){
            catConfirm('Reset percobaan?','Riwayat percobaan paket ini ('+n+') akan dihapus agar ujian bisa diulang dari percobaan ke-1. Tindakan ini tidak bisa dibatalkan.','Reset',async function(){
                await fetch('/api/cat/riwayat',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({action:'reset'})});
                catToast('Percobaan direset.');
                catRenderHasil();
            });
        };

        async function catRenderChart(){
            var host=document.getElementById('catChart'); if(!host) return;
            var riw; try{ riw=await (await fetch('/api/cat/riwayat')).json(); }catch(e){ return; }
            if(!riw || riw.length===0) return;
            var W=560, H=200, padL=40, padR=14, padT=14, padB=28;
            var cw=W-padL-padR, ch=H-padT-padB, maxY=550, n=riw.length;
            function xAt(i){ return n===1?(padL+cw/2):(padL+cw*(i/(n-1))); }
            function yAt(v){ return padT+ch*(1-Math.max(0,Math.min(1,v/maxY))); }
            var grid=''; for(var g=0; g<=4; g++){ var gy=padT+ch*(g/4); grid+='<line x1="'+padL+'" y1="'+gy+'" x2="'+(padL+cw)+'" y2="'+gy+'" stroke="var(--border-color)" stroke-width="1"/><text x="4" y="'+(gy+4)+'" font-size="10" fill="#64748b">'+Math.round(maxY*(1-g/4))+'</text>'; }
            var pts=riw.map(function(r,i){ return xAt(i)+','+yAt(r.total); }).join(' ');
            var dots=riw.map(function(r,i){ return '<circle cx="'+xAt(i)+'" cy="'+yAt(r.total)+'" r="3.5" fill="#7C3AED"/>'; }).join('');
            var xl=riw.map(function(r,i){ return (i<14)?('<text x="'+xAt(i)+'" y="'+(H-8)+'" font-size="9" text-anchor="middle" fill="#64748b">'+(i+1)+'</text>'):''; }).join('');
            host.innerHTML='<div class="card p-3 mb-3" style="border-radius:16px;border:1px solid var(--border-color);background:var(--card-bg);box-shadow:0 2px 10px rgba(15,23,42,0.08);">'
                +'<div class="fw-bold mb-2">Grafik Perkembangan (Skor Total)</div>'
                +'<svg viewBox="0 0 '+W+' '+H+'" style="width:100%;height:auto;">'
                +grid
                +'<polyline fill="none" stroke="#7C3AED" stroke-width="2.5" points="'+pts+'"/>'
                +dots+xl
                +'</svg>'
                +'<div class="small text-secondary mt-1">Jumlah percobaan: '+n+' (X = urutan percobaan, Y = skor total /550)</div>'
                +'</div>';
        }

        // ---------------- TAB 4: GRAFIK PROGRES ----------------

        async function catRenderGrafik(){
            var b=document.getElementById('catBody'); if(!b) return;
            var riw; try{ riw=await (await fetch('/api/cat/riwayat')).json(); }catch(e){ riw=[]; }
            var g=window.catGForm;
            var head='<div class="fw-bold mb-1" style="font-size:18px;">Grafik Progres Skor</div>'
                +'<div class="small text-secondary mb-3">Masukkan skor TWK, TIU, dan TKP tiap latihan untuk memantau perkembangan.</div>';
            var form=catCard('<div class="fw-bold mb-2">Tambah Skor</div>'
                + '<div class="row g-2">'
                + catNumField('catGtwk','TWK',g.twk,3)
                + catNumField('catGtiu','TIU',g.tiu,3)
                + catNumField('catGtkp','TKP',g.tkp,3)
                + '</div>'
                + '<button class="btn btn-sm btn-primary mt-3 d-inline-flex align-items-center gap-1" onclick="catAddManual()"><i class="bi bi-plus-lg"></i>Simpan Skor</button>'
                + (window.catGMsg?'<div class="small ms-2" style="color:'+(window.catGMsg[1]?CAT_NO:CAT_OK)+';">'+catEsc(window.catGMsg[0])+'</div>':'')
            );
            var chart = riw.length===0 ? catCard('<div class="small text-secondary">Belum ada data. Simpan skor pertama Anda untuk melihat grafik.</div>') : catBarChartCard(riw);
            var rows = riw.length===0 ? '' : riw.slice().reverse().map(function(r){
                return '<tr><td class="small">'+catTanggal(r.tanggal)+'</td>'
                    +'<td class="small fw-bold" style="color:'+CAT_BAR_TWK+';">'+r.twk+'</td>'
                    +'<td class="small fw-bold" style="color:'+CAT_TKP_C+';">'+r.tiu+'</td>'
                    +'<td class="small fw-bold" style="color:#DB2777;">'+r.tkp+'</td>'
                    +'<td class="small fw-bold">'+r.total+'</td>'
                    +'<td class="small"><span class="badge fw-bold" style="background:'+(r.lulus?'#E8F5E9':'#FFEBEE')+';color:'+(r.lulus?'#2E7D32':'#C62828')+';">'+(r.lulus?'LULUS':'GAGAL')+'</span></td>'
                    +'<td class="small"><button class="btn btn-sm btn-outline-danger py-0" title="Hapus" onclick="catDelRiwayat(\''+r.id+'\')"><i class="bi bi-trash"></i></button></td></tr>';
            }).join('');
            var table = riw.length===0 ? '' : catCard('<div class="fw-bold mb-2">Riwayat Skor</div><div class="table-responsive"><table class="table table-sm align-middle mb-0"><thead><tr><th>Tanggal</th><th>TWK</th><th>TIU</th><th>TKP</th><th>Total</th><th>Status</th><th>Aksi</th></tr></thead><tbody>'+rows+'</tbody></table></div>');
            b.innerHTML = head + form + chart + table;
        }

        window.catAddManual = async function(){
            var twk=parseInt(document.getElementById('catGtwk').value,10);
            var tiu=parseInt(document.getElementById('catGtiu').value,10);
            var tkp=parseInt(document.getElementById('catGtkp').value,10);
            if(isNaN(twk)||isNaN(tiu)||isNaN(tkp)){ window.catGMsg=['Isi TWK, TIU, dan TKP dengan angka.',true]; catRenderGrafik(); return; }
            if(twk<0||twk>150||tiu<0||tiu>175||tkp<0||tkp>225){ window.catGMsg=['Rentang: TWK 0-150, TIU 0-175, TKP 0-225.',true]; catRenderGrafik(); return; }
            await fetch('/api/cat/riwayat',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({action:'add-manual',twk:twk,tiu:tiu,tkp:tkp})});
            window.catGForm={twk:'',tiu:'',tkp:''};
            window.catGMsg=['Skor tersimpan.',false];
            catRenderGrafik();
        };
        window.catDelRiwayat = async function(id){ await fetch('/api/cat/riwayat',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({action:'delete-item',id:id})}); catRenderGrafik(); };

        function catBarChartCard(riw){
            var W=560, H=220, padL=36, padR=10, padT=14, padB=30;
            var cw=W-padL-padR, ch=H-padT-padB, n=riw.length;
            var maxes={twk:150,tiu:175,tkp:225};
            var grid=''; for(var g2=0; g2<=4; g2++){ var gy=padT+ch*(g2/4); grid+='<line x1="'+padL+'" y1="'+gy+'" x2="'+(padL+cw)+'" y2="'+gy+'" stroke="var(--border-color)" stroke-width="1"/>'; }
            var gw=cw/n, gap=gw*0.14, bw=(gw-gap*2)/3;
            var bars='';
            riw.forEach(function(r,i){
                var gx=padL+gw*i;
                var series=[['twk',r.twk,CAT_BAR_TWK],['tiu',r.tiu,CAT_BAR_TIU],['tkp',r.tkp,CAT_BAR_TKP]];
                series.forEach(function(s,si){
                    var frac=Math.max(0,Math.min(1, s[1]/maxes[s[0]])); var hh=ch*frac; var bx=gx+gap+bw*si;
                    bars+='<rect x="'+bx+'" y="'+(padT+ch-hh)+'" width="'+(bw-1)+'" height="'+hh+'" rx="2" fill="'+s[2]+'"/>';
                });
                if(n<=14) bars+='<text x="'+(gx+gw/2)+'" y="'+(H-8)+'" font-size="9" text-anchor="middle" fill="#64748b">'+catTanggalPendek(r.tanggal)+'</text>';
            });
            function dot(c,l){ return '<span class="d-inline-flex align-items-center gap-1 small text-secondary"><span style="width:10px;height:10px;border-radius:50%;background:'+c+';"></span>'+l+'</span>'; }
            return '<div class="card p-3 mb-3" style="border-radius:16px;border:1px solid var(--border-color);background:var(--card-bg);box-shadow:0 2px 10px rgba(15,23,42,0.08);">'
                +'<div class="fw-bold mb-2">Perbandingan Skor (TWK / TIU / TKP)</div>'
                +'<div class="d-flex gap-3 mb-2">'+dot(CAT_BAR_TWK,'TWK')+dot(CAT_BAR_TIU,'TIU')+dot(CAT_BAR_TKP,'TKP')+'</div>'
                +'<svg viewBox="0 0 '+W+' '+H+'" style="width:100%;height:auto;">'+grid+bars+'</svg></div>';
        }

        // ---------------- TAB 5: TIPS ----------------

        function catRenderTips(){
            var b=document.getElementById('catBody'); if(!b) return;
            var prompt=[
                'Anda adalah pembuat soal SKD/SIMULASI CAT BKN. Buatkan soal dalam format teks Markdown (.md) yang WAJIB mengikuti aturan parser berikut, tanpa penjelasan tambahan di luar format.',
                '',
                'ATURAN FORMAT (persis, baris per baris):',
                '1. Tandai kelompok kategori dengan baris: KATEGORI: TWK  atau  KATEGORI: TIU  atau  KATEGORI: TKP',
                '2. Tiap soal diawali nomor: "1. <teks soal>" (boleh 1..999, titik atau kurung tutup).',
                '3. Opsi jawaban satu baris per huruf, A-E: "A. <opsi>", "B. <opsi>", dst. (minimal 2, maksimal 5).',
                '4. Kunci jawaban: "KUNCI: B" (huruf) ATAU "JAWABAN: 2" (nomor urut).',
                '5. Pembahasan (opsional, 1 baris): "PEMBAHASAN: <penjelasan singkat>".',
                '6. KHUSUS TKP: tambahkan bobot tiap opsi urut sesuai opsi: "BOBOT: 5,4,3,2" (5 = paling baik). Opsi terbaik harus = 5.',
                '',
                'CONTOH OUTPUT BENAR:',
                'KATEGORI: TWK',
                '1. Pancasila sebagai dasar negara terkandung dalam Pembukaan UUD 1945 alinea ke-...',
                'A. Pertama',
                'B. Kedua',
                'C. Ketiga',
                'D. Keempat',
                'E. Penutup',
                'KUNCI: A',
                'PEMBAHASAN: Rumusan dasar negara terdapat pada alinea keempat, namun kedudukan sebagai dasar negara ditegaskan pada alinea pertama...',
                '',
                'KATEGORI: TIU',
                '1. Jika semua miring adalah licin, dan sebagian licin adalah basah, maka kesimpulan yang tepat adalah...',
                'A. Semua miring basah',
                'B. Sebagian miring basah',
                'C. Tidak ada yang basah',
                'D. Semua basah miring',
                'E. Sebagian basah tidak miring',
                'KUNCI: B',
                'PEMBAHASAN: Silogisme: sebagian anggota himpunan licin juga basah, sehingga sebagian yang miring (licin) basah.',
                '',
                'KATEGORI: TKP',
                '1. Atasan meminta laporan selesai hari ini, namun Anda punya dua pekerjaan lain yang juga mendesak. Sikap Anda...',
                'A. Mengerjakan ketiganya sekaligus seadanya',
                'B. Mendahulukan laporan atasan dan menyesuaikan pekerjaan lain',
                'C. Menolak menambah pekerjaan',
                'D. Meminta rekan mengerjakan semuanya',
                'E. Mengerjakan pekerjaan lain dulu',
                'KUNCI: B',
                'BOBOT: 4,5,2,1,3',
                'PEMBAHASAN: Prioritas pada instruksi atasan dengan tetap menata pekerjaan lain.',
                '',
                'TUGAS ANDA:',
                '- Buatkan JUMLAH berikut per kategori: TWK = {N_TWK}, TIU = {N_TIU}, TKP = {N_TKP}.',
                '- Topik/level: {TOPIK}.',
                '- Gunakan bahasa Indonesia, tingkat kesulitan bervariasi (mudah-sedang-sulit), jawaban benar bervariasi posisinya (A-E).',
                '- Keluarkan HANYA isi .md siap simpan, tanpa ``` dan tanpa komentar pembuka/penutup.'
            ].join('\n');
            window.catTipsPrompt = prompt;

            function fmtRow(code, desc){
                return '<div class="d-flex align-items-start gap-2 py-1">'
                    +'<code class="small fw-bold px-2 py-1" style="border-radius:6px;background:rgba(124,58,237,.12);color:#7C3AED;">'+catEsc(code)+'</code>'
                    +'<span class="small text-secondary flex-grow-1">'+catEsc(desc)+'</span></div>';
            }
            function step(n, t){
                return '<div class="d-flex align-items-start gap-2 py-1">'
                    +'<span class="rounded-circle d-flex align-items-center justify-content-center flex-shrink-0" style="width:22px;height:22px;background:#7C3AED;color:#fff;font-size:12px;font-weight:700;">'+n+'</span>'
                    +'<span class="small">'+catEsc(t)+'</span></div>';
            }

            var fmt=catCard('<div class="d-flex align-items-center gap-2 mb-2">'
                +'<span class="rounded-circle d-flex align-items-center justify-content-center" style="width:34px;height:34px;background:rgba(124,58,237,.15);"><i class="bi bi-info-circle" style="color:#7C3AED;"></i></span>'
                +'<span class="fw-bold" style="font-size:15px;">Format File .md</span></div>'
                +'<div class="small text-secondary mb-2">Simpan soal sebagai file .md / .txt, lalu impor lewat menu "Impor Soal dari File". Satu blok = satu soal. Baris diawali kata kunci berikut:</div>'
                + fmtRow('KATEGORI: TWK','Kelompok kategori (TWK/TIU/TKP)')
                + fmtRow('1. <teks soal>','Nomor lalu teks soal')
                + fmtRow('A. <opsi> .. E. <opsi>','2-5 opsi jawaban')
                + fmtRow('KUNCI: B','Jawaban benar (huruf) / JAWABAN: 2 (nomor)')
                + fmtRow('PEMBAHASAN: ...','Penjelasan (opsional, 1 baris)')
                + fmtRow('BOBOT: 5,4,3,2','Khusus TKP, bobot tiap opsi (5=terbaik)'));

            var langkah=catCard('<div class="fw-bold mb-2" style="font-size:15px;">Langkah Membuat Soal</div>'
                + step(1,'Buka AI mana pun (ChatGPT, Gemini, Claude, Copilot, dsb.).')
                + step(2,'Salin PROMPT di bawah, isi bagian {N_TWK}, {N_TIU}, {N_TKP}, dan {TOPIK}.')
                + step(3,'Tempel jawaban AI ke aplikasi Notes/editor, simpan sebagai soal.md (UTF-8).')
                + step(4,'Di halaman Input Soal: pilih paket tryout, lalu "Pilih File & Impor" -> soal.md.')
                + step(5,'Cek daftar soal. Untuk TIU bergambar, buka soal lalu gunakan tombol "Impor Gambar Soal".'));

            var promptCard=catCard('<div class="d-flex justify-content-between align-items-center mb-2">'
                +'<div class="fw-bold" style="font-size:15px;">Prompt untuk Semua AI</div>'
                +'<button class="btn btn-sm btn-outline-primary d-inline-flex align-items-center gap-1" onclick="catCopyPrompt(this)">'
                +'<i class="bi '+(window.catTipsCopied?'bi-check-lg':'bi-clipboard')+'"></i>'+(window.catTipsCopied?'Tersalin':'Salin')+'</button></div>'
                +'<div class="small text-secondary mb-2">Ganti {N_TWK}, {N_TIU}, {N_TKP} dengan jumlah soal, dan {TOPIK} dengan topik yang diinginkan.</div>'
                +'<pre class="small p-3 mb-0" style="background:var(--border-color);border-radius:12px;white-space:pre-wrap;max-height:360px;overflow:auto;font-family:ui-monospace,Menlo,Consolas,monospace;">'+catEsc(prompt)+'</pre>');

            b.innerHTML = '<div class="fw-bold mb-3" style="font-size:18px;">Tips &amp; Tata Cara Buat Soal</div>' + fmt + langkah + promptCard;
        }

        window.catCopyPrompt = function(btn){
            var t=window.catTipsPrompt||'';
            function done(){
                window.catTipsCopied=true;
                if(btn){ btn.innerHTML='<i class="bi bi-check-lg"></i>Tersalin'; }
                catToast('Prompt disalin ke clipboard');
                setTimeout(function(){ window.catTipsCopied=false; if(btn){ btn.innerHTML='<i class="bi bi-clipboard"></i>Salin'; } },2600);
            }
            if(navigator.clipboard && navigator.clipboard.writeText){
                navigator.clipboard.writeText(t).then(done).catch(function(){ catFallbackCopy(t); done(); });
            } else { catFallbackCopy(t); done(); }
        };
        function catFallbackCopy(t){
            var ta=document.createElement('textarea'); ta.value=t; document.body.appendChild(ta); ta.select();
            try{ document.execCommand('copy'); }catch(e){}
            document.body.removeChild(ta);
        }
        """
    }
}
