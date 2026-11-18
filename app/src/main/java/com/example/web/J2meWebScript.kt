package com.example.web

// Halaman "J2ME Loader" untuk Web Connect / PC. Ini halaman informasi (bukan emulator):
// engine Java ME bersifat native Android, jadi game hanya bisa dijalankan dari aplikasi
// Android / Samsung DeX. Client-side murni (tanpa API), otomatis ikut ter-bake ke .exe PC
// lewat task generateWebUiPage (mengekstrak getWebUiHtml()).
// Dinavigasi lewat switchTab('j2me') -> loadCurrentTab -> window.loadJ2me().
// PENTING: raw string di bawah tidak boleh berisi karakter '$' (konvensi template Kotlin).
object J2meWebScript {
    fun getScript(dol: String): String {
        return """
        // =========================================================================
        // --- J2ME LOADER (info page, emulator native ada di aplikasi Android/DeX) ---
        // =========================================================================
        function j2mEnsureStyle() {
            if (document.getElementById('j2mStyles')) return;
            var st = document.createElement('style');
            st.id = 'j2mStyles';
            st.textContent = '.j2m-page{max-width:760px;margin:0 auto;padding:16px;}'
                + '.j2m-hero{display:flex;align-items:center;gap:16px;padding:22px 24px;border-radius:22px;'
                + 'background:linear-gradient(135deg,#306230 0%,#4C7A34 60%,#6FA84B 100%);color:#fff;'
                + 'box-shadow:0 12px 30px rgba(48,98,48,.28);margin-bottom:18px;}'
                + '.j2m-logo{width:60px;height:60px;border-radius:18px;background:rgba(255,255,255,.2);'
                + 'display:flex;align-items:center;justify-content:center;font-size:30px;flex-shrink:0;}'
                + '.j2m-hero-title{font-size:22px;font-weight:800;margin:0 0 4px;}'
                + '.j2m-hero-sub{font-size:13px;opacity:.9;}'
                + '.j2m-card{border-radius:18px;border:1px solid var(--border-color,rgba(100,116,139,.25));'
                + 'box-shadow:var(--card-shadow,0 4px 14px rgba(0,0,0,.06));padding:18px 20px;margin-bottom:14px;'
                + 'background:var(--bg-card,#fff);}'
                + '.j2m-card h6{font-weight:800;margin:0 0 8px;color:var(--text-main,#1e293b);}'
                + '.j2m-card p{margin:0 0 6px;font-size:14px;line-height:1.55;color:var(--text-secondary,#475569);}'
                + '.j2m-steps{margin:8px 0 0;padding-left:20px;font-size:14px;line-height:1.7;color:var(--text-secondary,#475569);}'
                + '.j2m-badge{display:inline-flex;align-items:center;gap:6px;padding:4px 12px;border-radius:999px;'
                + 'font-size:12px;font-weight:700;background:rgba(48,98,48,.12);color:#306230;margin-top:6px;}'
                + 'body.dark .j2m-card{background:rgba(148,163,184,.06);}'
                + 'body.dark .j2m-card h6{color:#e2e8f0;}'
                + 'body.dark .j2m-badge{background:rgba(111,168,75,.18);color:#A3D977;}';
            document.head.appendChild(st);
        }
        window.loadJ2me = function () {
            j2mEnsureStyle();
            var c = document.getElementById('tab-content');
            if (!c) return;
            c.innerHTML = '<div class="j2m-page">'
                + '<div class="j2m-hero"><div class="j2m-logo"><i class="bi bi-controller"></i></div>'
                + '<div><div class="j2m-hero-title">J2ME Loader</div>'
                + '<div class="j2m-hero-sub">Emulator Java ME (MIDP) untuk memainkan game lawas .jar / .jad</div>'
                + '<span class="j2m-badge"><i class="bi bi-android2"></i> Tersedia di aplikasi Android &amp; Samsung DeX</span></div></div>'
                + '<div class="j2m-card"><h6><i class="bi bi-info-circle me-1"></i> Tentang halaman ini</h6>'
                + '<p>J2ME Loader memakai engine emulator <b>native Android</b>, sehingga game hanya dapat '
                + 'dijalankan langsung dari aplikasi My Singghara di ponsel atau melalui <b>Samsung DeX</b>. '
                + 'Pada tampilan Web Connect / PC, halaman ini hadir sebagai informasi dan pemersatu menu '
                + '(100% offline, tanpa memanggil server eksternal).</p></div>'
                + '<div class="j2m-card"><h6><i class="bi bi-signpost-split me-1"></i> Cara memainkan game</h6>'
                + '<ol class="j2m-steps">'
                + '<li>Buka aplikasi <b>My Singghara</b> di ponsel, atau sambungkan ke monitor via <b>Samsung DeX</b>.</li>'
                + '<li>Buka menu <b>J2ME Loader</b> (ikon gamepad) pada drawer / sidebar.</li>'
                + '<li>Tambahkan file game <b>.jar</b> atau <b>.jad</b> memakai tombol tambah pada daftar.</li>'
                + '<li>Ketuk game untuk menjalankannya. Kontrol gamepad Bluetooth didukung otomatis.</li>'
                + '</ol></div>'
                + '</div>';
        };
        """
    }
}
