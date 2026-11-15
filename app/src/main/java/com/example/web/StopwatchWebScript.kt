package com.example.web

// Stopwatch untuk Web Connect / PC. 100% client-side (tanpa API), jadi otomatis ikut
// ter-bake ke aplikasi PC .exe lewat task generateWebUiPage (yang mengekstrak getWebUiHtml()).
// Dinavigasi lewat switchTab('stopwatch') -> loadCurrentTab -> window.loadStopwatch().
object StopwatchWebScript {
    fun getScript(dol: String): String {
        return """
        // =========================================================================
        // --- STOPWATCH (client-side, tanpa API) ---
        // =========================================================================
        window.__sw = window.__sw || { running: false, base: 0, elapsed: 0, laps: [], tick: null };

        function swPad(n) { return (n < 10 ? '0' : '') + n; }
        function swFmt(ms) {
            var cs = Math.floor((ms % 1000) / 10);
            var s = Math.floor(ms / 1000) % 60;
            var m = Math.floor(ms / 60000) % 60;
            var h = Math.floor(ms / 3600000);
            return swPad(h) + ':' + swPad(m) + ':' + swPad(s) + '.' + swPad(cs);
        }
        function swNow() {
            var st = window.__sw;
            return st.elapsed + (st.running ? (performance.now() - st.base) : 0);
        }
        function swEnsureStyle() {
            if (document.getElementById('swStyles')) return;
            var st = document.createElement('style');
            st.id = 'swStyles';
            st.textContent = '.sw-page{max-width:560px;margin:0 auto;padding:16px;}'
                + '.sw-head{display:flex;align-items:center;gap:10px;margin-bottom:14px;}'
                + '.sw-logo{width:40px;height:40px;border-radius:50%;background:rgba(124,58,237,.15);color:#7C3AED;display:flex;align-items:center;justify-content:center;font-size:22px;}'
                + '.sw-title{font-size:18px;font-weight:700;color:var(--text-main,#1e293b);}'
                + '.sw-time{font-variant-numeric:tabular-nums;font-family:ui-monospace,Menlo,Consolas,monospace;font-size:52px;font-weight:800;text-align:center;letter-spacing:1px;color:var(--text-main,#0f172a);margin:18px 0;}'
                + '.sw-btns{display:flex;gap:10px;justify-content:center;margin-bottom:16px;}'
                + '.sw-btn{border:none;border-radius:12px;padding:12px 22px;font-size:15px;font-weight:700;cursor:pointer;color:#fff;}'
                + '.sw-start{background:#16A34A;} .sw-stop{background:#DC2626;} .sw-lap{background:#0EA5E9;} .sw-reset{background:#64748B;}'
                + '.sw-btn:disabled{opacity:.45;cursor:not-allowed;}'
                + '.sw-laps{list-style:none;padding:0;margin:0;border-top:1px solid rgba(100,116,139,.25);}'
                + '.sw-laps li{display:flex;justify-content:space-between;padding:9px 6px;border-bottom:1px solid rgba(100,116,139,.18);font-size:14px;color:var(--text-main,#334155);}'
                + '.sw-laps .lap-n{font-weight:700;} .sw-laps .lap-split{color:#64748B;font-size:12px;}'
                + '.sw-empty{text-align:center;color:#94a3b8;font-size:13px;padding:14px;display:block;border:none;}'
                + 'body.dark .sw-title,body.dark .sw-time,body.dark .sw-laps li{color:#e2e8f0;}';
            document.head.appendChild(st);
        }
        function swUpdate() {
            var el = document.getElementById('swTime');
            if (el) el.textContent = swFmt(swNow());
        }
        function swRender() {
            var st = window.__sw;
            swUpdate();
            var toggle = document.getElementById('swToggle');
            if (toggle) {
                toggle.textContent = st.running ? 'Stop' : 'Start';
                toggle.className = 'sw-btn ' + (st.running ? 'sw-stop' : 'sw-start');
            }
            var lapBtn = document.getElementById('swLap');
            if (lapBtn) lapBtn.disabled = (!st.running && st.elapsed === 0);
            var resetBtn = document.getElementById('swReset');
            if (resetBtn) resetBtn.disabled = (st.elapsed === 0 && st.laps.length === 0 && !st.running);
            var list = document.getElementById('swLaps');
            if (list) {
                if (!st.laps.length) {
                    list.innerHTML = '<li class="sw-empty">Belum ada lap.</li>';
                } else {
                    var html = '';
                    for (var i = st.laps.length - 1; i >= 0; i--) {
                        var L = st.laps[i];
                        html += '<li><span class="lap-n">Lap ' + (i + 1) + '</span><span>' + swFmt(L.split)
                            + ' <span class="lap-split">(' + swFmt(L.total) + ')</span></span></li>';
                    }
                    list.innerHTML = html;
                }
            }
        }
        function swStartStop() {
            var st = window.__sw;
            if (st.running) {
                st.elapsed = swNow();
                st.running = false;
                if (st.tick) { clearInterval(st.tick); st.tick = null; }
            } else {
                st.base = performance.now();
                st.running = true;
                st.tick = setInterval(swUpdate, 50);
            }
            swRender();
        }
        function swLap() {
            var st = window.__sw;
            var cur = swNow();
            var prev = st.laps.length ? st.laps[st.laps.length - 1].total : 0;
            st.laps.push({ total: cur, split: cur - prev });
            swRender();
        }
        function swReset() {
            var st = window.__sw;
            if (st.tick) { clearInterval(st.tick); st.tick = null; }
            st.running = false;
            st.elapsed = 0;
            st.laps = [];
            swRender();
        }
        window.loadStopwatch = function () {
            swEnsureStyle();
            var c = document.getElementById('tab-content');
            if (!c) return;
            c.innerHTML = '<div class="sw-page">'
                + '<div class="sw-head"><div class="sw-logo"><i class="bi bi-stopwatch"></i></div><div class="sw-title">Stopwatch</div></div>'
                + '<div class="sw-time" id="swTime">00:00:00.00</div>'
                + '<div class="sw-btns">'
                + '<button class="sw-btn sw-start" id="swToggle" onclick="swStartStop()">Start</button>'
                + '<button class="sw-btn sw-lap" id="swLap" onclick="swLap()">Lap</button>'
                + '<button class="sw-btn sw-reset" id="swReset" onclick="swReset()">Reset</button>'
                + '</div>'
                + '<ul class="sw-laps" id="swLaps"></ul>'
                + '</div>';
            swRender();
        };
        """
    }
}
