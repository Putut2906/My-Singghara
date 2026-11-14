package com.example.web

object CatWebScript {
    fun getScript(dol: String): String {
        return """
        // =========================================================================
        // --- SIMULASI CAT (Web Connect). Memakai /api/cat/* (CatStore di HP = server).
        // =========================================================================
        window.catActiveTab = window.catActiveTab || 0;
        window.catTimerId = null;

        function catEsc(s){ return (s===null||s===undefined)?'':String(s).replace(/[&<>"']/g,function(c){return {'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c];}); }
        function catPad(n){ return (n<10?'0':'')+n; }
        function catFmt(ms){ ms=Math.max(0,ms); var t=Math.floor(ms/1000),h=Math.floor(t/3600),m=Math.floor((t%3600)/60),s=t%60; return h>0?(h+':'+catPad(m)+':'+catPad(s)):(catPad(m)+':'+catPad(s)); }
        function catStopTimer(){ if(window.catTimerId){ clearInterval(window.catTimerId); window.catTimerId=null; } }
        function catCard(inner){ return '<div class="card p-3 mb-3" style="border-radius:16px;border:1px solid var(--border-color);background:var(--card-bg);box-shadow:0 2px 10px rgba(15,23,42,0.08);">'+inner+'</div>'; }

        window.loadCat = function(initialTab){
            if(typeof initialTab === 'number') window.catActiveTab = initialTab;
            catStopTimer();
            var el = document.getElementById('tab-content'); if(!el) return;
            var tabs = [[0,'Input Soal','bi-ui-checks'],[1,'Simulasi Ujian','bi-clock-history'],[2,'Hasil & Live Score','bi-bar-chart-line-fill']];
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

        async function catRenderPaketBar(){
            var bar=document.getElementById('catPaketBar'); if(!bar) return;
            var d; try{ d=await (await fetch('/api/cat/paket')).json(); }catch(e){ return; }
            var list=d.list||[]; var aktif=d.aktif||'';
            var opts=list.map(function(p){ return '<option value="'+p.id+'"'+(p.id===aktif?' selected':'')+'>'+catEsc(p.nama)+'</option>'; }).join('');
            bar.innerHTML='<div class="d-flex align-items-center gap-2 flex-wrap">'
                + '<label class="small fw-bold mb-0"><i class="bi bi-folder2-open me-1"></i>Paket Tryout:</label>'
                + '<select id="catPaketSel" class="form-select form-select-sm" style="max-width:240px;" onchange="catPaketChanged()">'+opts+'</select>'
                + '<button class="btn btn-sm btn-outline-primary" onclick="catBuatPaket()"><i class="bi bi-plus-lg"></i></button>'
                + '<button class="btn btn-sm btn-outline-secondary" onclick="catRenamePaket()"><i class="bi bi-pencil"></i></button>'
                + '<button class="btn btn-sm btn-outline-danger" onclick="catHapusPaket()"><i class="bi bi-trash"></i></button>'
                + '</div>';
        }
        window.catPaketChanged = async function(){
            var sel=document.getElementById('catPaketSel'); if(!sel) return;
            await fetch('/api/cat/paket',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({action:'select',id:sel.value})});
            catRenderTab();
        };
        window.catBuatPaket = async function(){
            var nama=prompt('Nama paket baru (mis. Tryout 2):',''); if(nama===null) return;
            await fetch('/api/cat/paket',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({action:'create',nama:nama})});
            await catRenderPaketBar(); catRenderTab();
        };
        window.catRenamePaket = async function(){
            var sel=document.getElementById('catPaketSel'); if(!sel) return;
            var nama=prompt('Ganti nama paket:', sel.options[sel.selectedIndex].text); if(nama===null) return;
            await fetch('/api/cat/paket',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({action:'rename',id:sel.value,nama:nama})});
            await catRenderPaketBar(); catRenderTab();
        };
        window.catHapusPaket = async function(){
            var sel=document.getElementById('catPaketSel'); if(!sel) return;
            if(!confirm('Hapus paket ini beserta soal di dalamnya?')) return;
            await fetch('/api/cat/paket',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({action:'delete',id:sel.value})});
            await catRenderPaketBar(); catRenderTab();
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
            else catRenderHasil();
        }

        // ---------------- TAB 1: INPUT SOAL ----------------
        async function catRenderInput(){
            var b=document.getElementById('catBody'); if(!b) return;
            var bank=[]; try{ bank=await (await fetch('/api/cat/bank')).json(); }catch(e){}
            var cnt={TWK:0,TIU:0,TKP:0}; bank.forEach(function(s){ if(cnt[s.kategori]!==undefined) cnt[s.kategori]++; });
            var opts='';
            for(var i=0;i<5;i++){
                var bo=''; [1,2,3,4,5].forEach(function(n){ bo += '<option value="'+n+'"'+(n===(5-i)?' selected':'')+'>'+n+'</option>'; });
                opts += '<div class="d-flex align-items-center gap-2 mb-1">'
                    + '<input class="form-check-input flex-shrink-0" type="radio" name="catKunci" value="'+i+'"'+(i===0?' checked':'')+'>'
                    + '<input class="form-control form-control-sm catOpsi" placeholder="Opsi '+'ABCDEFG'.charAt(i)+'" style="flex:1;">'
                    + '<select class="form-select form-select-sm catBobot" style="width:96px;" disabled>'+bo+'</select>'
                    + '</div>';
            }
            var form = catCard(
                '<div class="d-flex flex-wrap gap-2 mb-2 small">'
                + '<span class="badge text-bg-primary">TWK '+cnt.TWK+'/30</span>'
                + '<span class="badge text-bg-success">TIU '+cnt.TIU+'/35</span>'
                + '<span class="badge text-bg-warning">TKP '+cnt.TKP+'/45</span></div>'
                + '<div class="row g-2">'
                + '<div class="col-6"><label class="form-label small mb-1">Kategori</label><select id="catKategori" class="form-select form-select-sm" onchange="catToggleBobot()"><option value="TWK">TWK</option><option value="TIU">TIU</option><option value="TKP">TKP</option></select></div>'
                + '<div class="col-6"><label class="form-label small mb-1">Pembahasan (opsional)</label><input id="catPembahasan" class="form-control form-control-sm"></div>'
                + '<div class="col-12"><label class="form-label small mb-1">Soal</label><textarea id="catTeks" class="form-control form-control-sm" rows="2"></textarea></div></div>'
                + '<div class="small text-secondary mt-2 mb-1">Opsi (radio = kunci benar; untuk TKP atur bobot 1-5 per opsi)</div>'
                + opts
                + '<button class="btn btn-sm btn-primary mt-2" onclick="catAddSoal()"><i class="bi bi-plus-lg me-1"></i>Simpan Soal</button>'
            );
            var rows = bank.length===0 ? '<div class="p-3 text-secondary small">Belum ada soal.</div>' :
                bank.slice().reverse().map(function(s){
                    return '<div class="d-flex align-items-center gap-2 p-2 border-bottom" style="border-color:var(--border-color);">'
                        + '<span class="badge text-bg-secondary">'+catEsc(s.kategori)+'</span>'
                        + '<div class="flex-grow-1" style="min-width:0;"><div class="small fw-semibold text-truncate">'+catEsc(s.teks)+'</div>'
                        + '<div class="text-secondary" style="font-size:11px;">'+s.opsi.length+' opsi • kunci '+'ABCDEFG'.charAt(s.kunci)+'</div></div>'
                        + '<button class="btn btn-sm btn-outline-danger py-0" onclick="catDelSoal(\''+s.id+'\')"><i class="bi bi-trash"></i></button></div>';
                }).join('');
            var listCard = '<div class="card p-0 overflow-hidden" style="border-radius:16px;border:1px solid var(--border-color);background:var(--card-bg);box-shadow:0 2px 10px rgba(15,23,42,0.08);">'
                + '<div class="p-3 fw-bold border-bottom" style="border-color:var(--border-color);">Daftar Soal ('+bank.length+')</div>'
                + '<div>'+rows+'</div></div>';
            var importCard = catCard(
                '<div class="fw-bold mb-1">Impor Soal dari File</div>'
                + '<div class="small text-secondary mb-2">Format: .md, .txt, .docx, .xlsx. Tiap soal: baris "1. Soal", opsi "A." s/d "E.", "KUNCI: B", "PEMBAHASAN: ..." (TKP: "BOBOT: 5,4,3,2,1").</div>'
                + '<input type="file" id="catFile" accept=".md,.txt,.docx,.xlsx" class="form-control form-control-sm mb-2">'
                + '<button class="btn btn-sm btn-outline-primary" onclick="catImportFile()"><i class="bi bi-upload me-1"></i>Impor File</button>'
                + '<span id="catImportMsg" class="small ms-2"></span>'
            );
            b.innerHTML = importCard + form + listCard;
        }

        window.catImportFile = function(){
            var inp=document.getElementById('catFile'); var f=inp&&inp.files[0]; if(!f){ alert('Pilih file dulu.'); return; }
            var msg=document.getElementById('catImportMsg');
            var reader=new FileReader();
            reader.onload=function(){
                var bytes=new Uint8Array(reader.result); var bin=''; var chunk=0x8000;
                for(var i=0;i<bytes.length;i+=chunk){ bin+=String.fromCharCode.apply(null,bytes.subarray(i,i+chunk)); }
                var b64=btoa(bin);
                if(msg) msg.textContent='Mengimpor...';
                fetch('/api/cat/import',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({filename:f.name,data:b64})})
                    .then(function(r){return r.json();})
                    .then(function(j){ if(j&&j.success){ if(msg) msg.textContent='Berhasil impor '+(j.count||0)+' soal.'; setTimeout(function(){catRenderInput();},700); } else { if(msg) msg.textContent='Gagal: '+((j&&j.message)||''); } })
                    .catch(function(e){ if(msg) msg.textContent='Error: '+e; });
            };
            reader.readAsArrayBuffer(f);
        };

        window.catToggleBobot = function(){
            var isTkp = document.getElementById('catKategori').value==='TKP';
            document.querySelectorAll('.catBobot').forEach(function(sel){ sel.disabled=!isTkp; });
        };

        window.catAddSoal = async function(){
            var kategori=document.getElementById('catKategori').value;
            var teks=document.getElementById('catTeks').value.trim();
            var inputs=[].slice.call(document.querySelectorAll('.catOpsi'));
            var opsi=inputs.map(function(x){return x.value.trim();}).filter(function(v){return v.length>0;});
            if(!teks||opsi.length<2){ alert('Isi soal dan minimal 2 opsi.'); return; }
            var kEl=document.querySelector('input[name=catKunci]:checked'); var kunci=kEl?parseInt(kEl.value):0;
            var pembahasan=document.getElementById('catPembahasan').value.trim();
            var payload={kategori:kategori,teks:teks,opsi:opsi,kunci:kunci,pembahasan:pembahasan};
            if(kategori==='TKP'){
                var sels=[].slice.call(document.querySelectorAll('.catBobot')); var bobot=[];
                inputs.forEach(function(inp,idx){ if(inp.value.trim().length>0) bobot.push(parseInt(sels[idx].value)||1); });
                payload.bobot=bobot;
            }
            await fetch('/api/cat/bank',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(payload)});
            catRenderInput();
        };

        window.catDelSoal = async function(id){ await fetch('/api/cat/bank?id='+encodeURIComponent(id),{method:'DELETE'}); catRenderInput(); };

        // ---------------- TAB 2: SIMULASI UJIAN ----------------
        async function catRenderSimulasi(){
            var b=document.getElementById('catBody'); if(!b) return;
            var sesi; try{ sesi=await (await fetch('/api/cat/sesi')).json(); }catch(e){ sesi={active:false}; }
            if(!sesi || !sesi.active || sesi.selesai){
                var bank=[]; try{ bank=await (await fetch('/api/cat/bank')).json(); }catch(e){}
                var cnt={TWK:0,TIU:0,TKP:0}; bank.forEach(function(s){ if(cnt[s.kategori]!==undefined) cnt[s.kategori]++; });
                var total=cnt.TWK+cnt.TIU+cnt.TKP;
                var info=catCard(
                    '<div class="fw-bold mb-2">Petunjuk Ujian (SKD CPNS)</div>'
                    + '<ul class="small mb-2" style="line-height:1.7;">'
                    + '<li>Durasi 100 menit; timer dihitung dari waktu server (HP), menutup/memuat ulang tidak mereset.</li>'
                    + '<li>Jumlah soal 110 (TWK 30, TIU 35, TKP 45).</li>'
                    + '<li>TWK/TIU: benar = 5, salah/kosong = 0. TKP: skala 1-5.</li>'
                    + '<li>Passing grade: TWK 65, TIU 80, TKP 156.</li></ul>'
                    + '<div class="small text-secondary mb-2">Bank soal Anda: TWK '+cnt.TWK+', TIU '+cnt.TIU+', TKP '+cnt.TKP+' (total '+total+').</div>'
                    + (total<110 ? '<div class="small text-danger mb-2">Bank belum lengkap (ideal 110). Ujian memakai soal seadanya.</div>' : '')
                    + '<button class="btn btn-primary btn-sm" '+(total===0?'disabled':'')+' onclick="catStart()"><i class="bi bi-play-fill me-1"></i>Mulai Ujian</button>'
                );
                if(sesi && sesi.selesai){
                    info = catCard('<div class="fw-bold mb-1">Sesi sebelumnya selesai.</div><div class="small text-secondary mb-2">Lihat hasil di tab "Hasil & Live Score", atau mulai sesi baru.</div><button class="btn btn-primary btn-sm" onclick="catStart()">Mulai Ujian Baru</button>') + info;
                }
                b.innerHTML = info;
                return;
            }
            var order=sesi.order||[]; var jawaban={}; Object.keys(sesi.jawaban||{}).forEach(function(k){ jawaban[parseInt(k)]=sesi.jawaban[k]; });
            var current=sesi.currentIndex||0; var endTime=sesi.endTime; var bankMap={};
            try{ (await (await fetch('/api/cat/bank')).json()).forEach(function(s){ bankMap[s.id]=s; }); }catch(e){}
            window.catExam={order:order,jawaban:jawaban,current:current,endTime:endTime,bankMap:bankMap,pg:sesi.pg||{twk:65,tiu:80,tkp:156}};

            b.innerHTML = catCard(
                '<div class="d-flex justify-content-between align-items-center">'
                + '<div><div class="small text-secondary">Sisa Waktu</div><div id="catClock" class="fw-bold" style="font-size:1.6rem;color:#7C3AED;">--:--</div></div>'
                + '<div class="text-end"><div class="small text-secondary">Passing Grade</div><div class="small fw-bold">TWK '+window.catExam.pg.twk+' • TIU '+window.catExam.pg.tiu+' • TKP '+window.catExam.pg.tkp+'</div></div></div>'
                + '<div class="d-flex gap-4 mt-2"><div><span id="catAnsCount" class="fw-bold">0</span> <span class="small text-secondary">dijawab</span></div><div><span id="catUnCount" class="fw-bold">0</span> <span class="small text-secondary">belum</span></div></div>'
            ) + '<div class="row g-3"><div class="col-lg-8"><div id="catQuestion"></div></div><div class="col-lg-4"><div class="card p-2" style="border-radius:16px;border:1px solid var(--border-color);background:var(--card-bg);box-shadow:0 2px 10px rgba(15,23,42,0.08);"><div class="small fw-bold mb-2 px-1">Navigasi Nomor</div><div id="catNav" class="d-flex flex-wrap gap-1"></div></div></div></div>'
            + '<div class="d-flex gap-2 mt-3">'
            + '<button class="btn btn-outline-secondary btn-sm flex-fill" onclick="catSkip()"><i class="bi bi-skip-end me-1"></i>Lewati</button>'
            + '<button class="btn btn-primary btn-sm flex-fill" onclick="catSaveNext()"><i class="bi bi-check-lg me-1"></i>Simpan &amp; Lanjut</button>'
            + '<button class="btn btn-danger btn-sm flex-fill" onclick="catFinish()"><i class="bi bi-stop-circle me-1"></i>Selesai</button></div>';
            catRenderQuestion(); catRenderNav(); catUpdateHeader();
            catStopTimer();
            window.catTimerId=setInterval(function(){
                var rem=window.catExam?window.catExam.endTime-Date.now():0;
                var c=document.getElementById('catClock'); if(c) c.textContent=catFmt(rem);
                if(rem<=0){ catStopTimer(); catFinish(true); }
            },1000);
        }

        function catRenderQuestion(){
            var ex=window.catExam; var q=ex.bankMap[ex.order[ex.current]]; var el=document.getElementById('catQuestion'); if(!el) return;
            if(!q){ el.innerHTML=catCard('<div class="text-danger small">Soal tidak ditemukan.</div>'); return; }
            var letters='ABCDEFG';
            var opsiHtml=q.opsi.map(function(o,i){
                var sel=(ex.jawaban[ex.current]===i);
                return '<label class="d-flex align-items-center gap-2 p-2 mb-1" style="border-radius:12px;cursor:pointer;background:'+(sel?'rgba(124,58,237,0.12)':'transparent')+';border:1px solid '+(sel?'#7C3AED':'var(--border-color)')+';">'
                    + '<input class="form-check-input" type="radio" name="catOpt" value="'+i+'"'+(sel?' checked':'')+' onchange="catSelect('+i+')">'
                    + '<span class="small">'+letters.charAt(i)+'. '+catEsc(o)+'</span></label>';
            }).join('');
            el.innerHTML = catCard(
                '<div class="d-flex align-items-center gap-2 mb-2"><span class="badge text-bg-secondary">'+catEsc(q.kategori)+'</span>'
                + '<span class="small text-secondary">Soal '+(ex.current+1)+' / '+ex.order.length+'</span></div>'
                + '<div class="fw-semibold mb-2">'+catEsc(q.teks)+'</div>'+opsiHtml
            );
        }

        function catRenderNav(){
            var ex=window.catExam; var el=document.getElementById('catNav'); if(!el) return;
            var letters='ABCDEFG'; var h='';
            for(var i=0;i<ex.order.length;i++){
                var answered=(ex.jawaban[i]!==undefined); var active=(i===ex.current);
                var bg=active?'#7C3AED':(answered?'#16A34A':'#DC2626');
                h += '<button onclick="catGo('+i+')" style="width:32px;height:32px;border-radius:8px;color:#fff;font-size:12px;font-weight:700;border:'+(active?'2px solid #000':'none')+';background:'+bg+';">'+(i+1)+'</button>';
            }
            el.innerHTML=h;
        }

        function catUpdateHeader(){
            var ex=window.catExam; if(!ex) return;
            var ans=Object.keys(ex.jawaban).length; var ac=document.getElementById('catAnsCount'); var uc=document.getElementById('catUnCount');
            if(ac) ac.textContent=ans; if(uc) uc.textContent=(ex.order.length-ans);
            var c=document.getElementById('catClock'); if(c) c.textContent=catFmt(ex.endTime-Date.now());
        }

        window.catSelect = async function(i){
            var ex=window.catExam; if(!ex) return;
            ex.jawaban[ex.current]=i; catRenderQuestion(); catRenderNav(); catUpdateHeader();
            try{ var r=await (await fetch('/api/cat/sesi',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({action:'answer',index:ex.current,opsi:i})})).json(); if(r && r.expired) catFinish(true); }catch(e){}
        };
        function catGo(i){ var ex=window.catExam; if(!ex) return; ex.current=i; catRenderQuestion(); catRenderNav(); catUpdateHeader(); fetch('/api/cat/sesi',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({action:'navigate',current:i})}); }
        window.catSkip = function(){ var ex=window.catExam; if(ex) catGo(Math.min(ex.current+1, ex.order.length-1)); };
        window.catSaveNext = function(){ var ex=window.catExam; if(ex) catGo(Math.min(ex.current+1, ex.order.length-1)); };
        window.catStart = async function(){ await fetch('/api/cat/sesi',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({action:'start'})}); catRenderSimulasi(); };
        window.catFinish = async function(auto){
            var ex=window.catExam; if(!ex) return;
            if(!auto){ var un=ex.order.length-Object.keys(ex.jawaban).length; if(!confirm('Masih ada '+un+' soal belum dijawab. Yakin selesaikan ujian?')) return; }
            catStopTimer();
            await fetch('/api/cat/sesi',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({action:'finish'})});
            window.catExam=null; window.catActiveTab=2; window.loadCat(2);
        };

        // ---------------- TAB 3: HASIL & LIVE SCORE ----------------
        async function catRenderHasil(){
            var b=document.getElementById('catBody'); if(!b) return;
            var data; try{ data=await (await fetch('/api/cat/hasil')).json(); }catch(e){ data={available:false}; }
            if(!data || !data.available){ b.innerHTML=catCard('<div class="fw-bold mb-1">Belum ada hasil</div><div class="small text-secondary mb-2">Selesaikan simulasi ujian untuk melihat skor & pembahasan.</div><button class="btn btn-primary btn-sm" onclick="catSetTab(1)">Mulai Simulasi</button>'); return; }
            var h=data.hasil;
            var banner='<div class="card p-4 mb-3 text-center text-white" style="border-radius:20px;background:'+(h.lulus?'#16A34A':'#DC2626')+';box-shadow:0 6px 18px rgba(15,23,42,0.15);">'
                + '<div class="small text-uppercase" style="opacity:.85;">Total Skor</div><div style="font-size:2.4rem;font-weight:800;">'+h.total+' / 550</div>'
                + '<div class="fw-bold">'+(h.lulus?'LULUS PASSING GRADE':'BELUM MEMENUHI PASSING GRADE')+'</div>'
                + '<div class="small" style="opacity:.85;">Dijawab '+h.jumlahDijawab+'/'+h.jumlahSoal+'</div></div>';
            function bar(lab,val,maks,pg,lulus){ var pct=Math.max(0,Math.min(100,Math.round(val/maks*100)));
                return '<div class="d-flex align-items-center gap-2 mb-2"><div style="width:40px;font-weight:700;">'+lab+'</div>'
                    + '<div class="flex-grow-1" style="height:10px;border-radius:6px;background:var(--border-color);overflow:hidden;"><div style="height:100%;width:'+pct+'%;background:'+(lulus?'#16A34A':'#DC2626')+';"></div></div>'
                    + '<div style="width:70px;text-align:right;font-size:12px;font-weight:700;">'+val+'/'+maks+'</div>'
                    + '<span class="badge" style="background:'+(lulus?'rgba(22,163,74,.15)':'rgba(220,38,38,.15)')+';color:'+(lulus?'#16A34A':'#DC2626')+';">'+(lulus?'>=':'<')+pg+'</span></div>'; }
            var rekap=catCard('<div class="fw-bold mb-2">Rincian per Kategori</div>'
                + bar('TWK',h.twk,150,h.pg.twk,h.twkLulus) + bar('TIU',h.tiu,175,h.pg.tiu,h.tiuLulus) + bar('TKP',h.tkp,225,h.pg.tkp,h.tkpLulus));
            var letters='ABCDEFG';
            var ulasan=h.ulasan.map(function(u){
                var opsiHtml=u.opsi.map(function(o,i){ var isK=(i===u.kunci); var isJ=(i===u.jawaban);
                    return '<div class="small mb-1">'+letters.charAt(i)+'. '+catEsc(o)+(isK?' <span class="text-success fw-bold">kunci</span>':'')+(isJ?' <span class="text-primary fw-bold">jawabanmu</span>':'')+'</div>'; }).join('');
                var head='<div class="d-flex align-items-center gap-2"><span class="badge text-bg-secondary">'+catEsc(u.kategori)+'</span>'
                    + '<div class="flex-grow-1 small fw-semibold">'+u.nomor+'. '+catEsc(u.teks)+'</div>'
                    + '<span class="fw-bold" style="color:'+(u.benar?'#16A34A':'#DC2626')+';">+'+u.poin+'</span></div>';
                var body='<div class="mt-2">'+opsiHtml+(u.jawaban<0?'<div class="small text-danger">Tidak dijawab.</div>':'')+(u.pembahasan?'<div class="small text-secondary mt-1"><b>Pembahasan:</b> '+catEsc(u.pembahasan)+'</div>':'')+'</div>';
                return '<div class="card p-3 mb-2" style="border-radius:16px;border:1px solid var(--border-color);background:var(--card-bg);box-shadow:0 2px 10px rgba(15,23,42,0.08);">'
                    + '<div data-bs-toggle="collapse" data-bs-target="#catU'+u.nomor+'" style="cursor:pointer;">'+head+'</div>'
                    + '<div class="collapse" id="catU'+u.nomor+'">'+body+'</div></div>';
            }).join('');
            b.innerHTML = banner + rekap + '<div id="catChart"></div>' + '<div class="fw-bold mb-2">Ulasan &amp; Pembahasan</div>' + ulasan;
            catRenderChart();
        }

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
        """
    }
}
