package com.example.web

object MindMapWebScript {
    fun getScript(dol: String): String {
        return """
        // =========================================================================
        // --- MINDMAP AI (100% OFFLINE, TAMPILAN & FUNGSI SETARA ANDROID) ---
        // =========================================================================
        window.webMindMaps = window.webMindMaps || [];
        window.webMindMapActiveId = null;
        window.webMindMapTab = 0;
        window.webMindMapCollapsed = window.webMindMapCollapsed || {};

        function webMindMapEscape(text) {
            if (typeof escapeHtml === 'function') return escapeHtml(text);
            return (text || '').toString().replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
        }

        function showMindMapToast(message) {
            let el = document.getElementById('webMindMapToast');
            if (!el) {
                el = document.createElement('div');
                el.id = 'webMindMapToast';
                el.style.cssText = 'position:fixed;left:50%;bottom:28px;transform:translateX(-50%);background:rgba(15,23,42,.94);color:#fff;padding:10px 18px;border-radius:12px;font-size:13px;z-index:100000;box-shadow:0 8px 24px rgba(0,0,0,.35);opacity:0;transition:opacity .2s ease;max-width:90vw;text-align:center;pointer-events:none;';
                document.body.appendChild(el);
            }
            el.textContent = message;
            el.style.opacity = '1';
            clearTimeout(window.webMindMapToastTimer);
            window.webMindMapToastTimer = setTimeout(function () { el.style.opacity = '0'; }, 2200);
        }

        function loadStoredMindMaps() {
            try {
                const raw = localStorage.getItem('singghara_mindmaps_v1');
                window.webMindMaps = raw ? JSON.parse(raw) : [];
            } catch (_) {
                window.webMindMaps = [];
            }
            if (!Array.isArray(window.webMindMaps)) window.webMindMaps = [];
        }

        function persistMindMaps() {
            try {
                localStorage.setItem('singghara_mindmaps_v1', JSON.stringify(window.webMindMaps));
                return true;
            } catch (_) {
                showMindMapToast('Penyimpanan browser penuh. Hapus peta konsep lama terlebih dahulu.');
                return false;
            }
        }

        function parseMindMapText(rawText) {
            const lines = (rawText || '').split(/\r?\n/).filter(function (line) {
                return line.trim().length > 0;
            });
            if (lines.length === 0) return null;

            const rootTitle = (lines[0].replace(/^[\s\-*•\d.)]+/, '') || 'Peta Konsep').trim();
            const root = { title: rootTitle, children: [] };
            const stack = [{ depth: -1, node: root }];

            for (let i = 1; i < lines.length; i++) {
                const original = lines[i];
                const indent = original.match(/^\s*/)[0].replace(/\t/g, '  ').length;
                const bulletMatch = original.match(/^\s*([-*•]|\d+[.)])\s*/);
                const depth = bulletMatch ? Math.floor(indent / 2) + 1 : Math.floor(indent / 2);
                const title = original.replace(/^\s*([-*•]|\d+[.)])\s*/, '').trim();
                if (!title) continue;
                const node = { title: title, children: [] };
                while (stack.length > 1 && stack[stack.length - 1].depth >= depth) {
                    stack.pop();
                }
                stack[stack.length - 1].node.children.push(node);
                stack.push({ depth: depth, node: node });
            }
            return root;
        }

        function countMindMapNodes(node) {
            if (!node) return 0;
            let total = 1;
            (node.children || []).forEach(function (child) { total += countMindMapNodes(child); });
            return total;
        }

        function collectMindMapTitles(node, output) {
            if (!node) return output;
            output.push(node.title);
            (node.children || []).forEach(function (child) { collectMindMapTitles(child, output); });
            return output;
        }

        function formatMindMapDate(millis) {
            try {
                return new Date(millis).toLocaleString('id-ID', {
                    day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit'
                });
            } catch (_) {
                return '-';
            }
        }

        function loadMindMapPage() {
            loadStoredMindMaps();
            if (window.webMindMapActiveId) {
                const map = window.webMindMaps.find(function (m) { return m.id === window.webMindMapActiveId; });
                if (map) {
                    renderMindMapDetail(map);
                    return;
                }
                window.webMindMapActiveId = null;
            }
            renderMindMapHome();
        }

        function renderMindMapHome() {
            const container = document.getElementById('tab-content');
            if (!container) return;
            let html = '';
            html += '<div class="d-flex flex-wrap justify-content-between align-items-center mb-3 gap-2">';
            html += '  <div>';
            html += '    <h4 class="fw-bold mb-0 d-flex align-items-center gap-2"><i class="bi bi-diagram-3-fill" style="color:#0288D1"></i> MindMap AI</h4>';
            html += '    <small class="text-secondary">' + window.webMindMaps.length + ' peta konsep tersimpan di perangkat (offline)</small>';
            html += '  </div>';
            html += '</div>';

            html += '<div class="card card-custom p-3 mb-3" style="border-radius:16px">';
            html += '  <label class="fw-semibold small mb-2">Buat peta konsep baru</label>';
            html += '  <textarea id="webMindMapInput" class="form-control mb-2" rows="7" placeholder="Tulis ide utama di baris pertama, lalu sub-ide dengan tanda - pada baris berikutnya.&#10;Contoh:&#10;Rencana Usaha&#10;- Produk: kopi susu&#10;  - Kemasan 250ml&#10;- Pemasaran: media sosial"></textarea>';
            html += '  <div class="d-flex flex-wrap gap-2">';
            html += '    <button type="button" class="btn btn-primary btn-sm" onclick="window.createMindMapFromInput()"><i class="bi bi-diagram-3 me-1"></i> Buat MindMap</button>';
            html += '    <button type="button" class="btn btn-outline-secondary btn-sm" onclick="document.getElementById(\'webMindMapInput\').value=\'\'"><i class="bi bi-eraser me-1"></i> Bersihkan</button>';
            html += '  </div>';
            html += '</div>';

            if (window.webMindMaps.length === 0) {
                html += '<div class="card card-custom p-5 text-center">';
                html += '  <i class="bi bi-diagram-3" style="font-size:42px;color:#0288D1"></i>';
                html += '  <h6 class="fw-bold mt-3 mb-1">Belum ada peta konsep</h6>';
                html += '  <p class="text-secondary small mb-0">Tulis ide di atas lalu ketuk Buat MindMap. Semua peta konsep tersimpan di browser ini tanpa internet.</p>';
                html += '</div>';
            } else {
                html += '<div class="row g-3">';
                window.webMindMaps.slice().sort(function (a, b) {
                    return (b.createdAt || 0) - (a.createdAt || 0);
                }).forEach(function (map) {
                    const nodeCount = countMindMapNodes(map.tree);
                    html += '<div class="col-12 col-md-6 col-xl-4">';
                    html += '  <div class="card card-custom h-100 p-3" style="border-radius:16px">';
                    html += '    <div class="d-flex align-items-start gap-2">';
                    html += '      <div class="rounded-3 d-flex align-items-center justify-content-center" style="width:42px;height:42px;min-width:42px;background:rgba(2,136,209,.14);color:#0288D1"><i class="bi bi-diagram-3-fill fs-5"></i></div>';
                    html += '      <div class="flex-grow-1 overflow-hidden">';
                    html += '        <div class="fw-bold text-truncate" style="font-size:14px">' + webMindMapEscape(map.tree ? map.tree.title : map.title) + '</div>';
                    html += '        <div class="text-secondary" style="font-size:11px">' + formatMindMapDate(map.createdAt) + ' • ' + nodeCount + ' cabang</div>';
                    html += '      </div>';
                    html += '    </div>';
                    html += '    <div class="d-flex gap-2 mt-3">';
                    html += '      <button type="button" class="btn btn-sm btn-primary flex-grow-1" onclick="window.openMindMap(\'' + map.id + '\')"><i class="bi bi-box-arrow-in-right me-1"></i> Buka</button>';
                    html += '      <button type="button" class="btn btn-sm btn-outline-danger" onclick="window.deleteMindMap(\'' + map.id + '\')"><i class="bi bi-trash"></i></button>';
                    html += '    </div>';
                    html += '  </div>';
                    html += '</div>';
                });
                html += '</div>';
            }

            container.innerHTML = html;
        }

        function createMindMapFromInput() {
            const input = document.getElementById('webMindMapInput');
            const text = input ? input.value : '';
            const tree = parseMindMapText(text);
            if (!tree) {
                showMindMapToast('Tulis ide utama terlebih dahulu.');
                return;
            }
            loadStoredMindMaps();
            const map = {
                id: 'mm_' + Date.now(),
                title: tree.title,
                originalText: text,
                tree: tree,
                createdAt: Date.now()
            };
            window.webMindMaps.unshift(map);
            if (!persistMindMaps()) return;
            showMindMapToast('Peta konsep berhasil dibuat.');
            window.openMindMap(map.id);
        }

        function renderMindMapTreeHtml(node, depth) {
            if (!node) return '';
            const key = node.title + '|' + depth;
            const collapsed = !!window.webMindMapCollapsed[key];
            const hasChildren = (node.children || []).length > 0;
            let html = '<div style="margin-left:' + (depth === 0 ? 0 : 20) + 'px;position:relative">';
            if (depth > 0) {
                html += '<span style="position:absolute;left:-12px;top:0;bottom:50%;width:1px;background:var(--border-color)"></span>';
                html += '<span style="position:absolute;left:-12px;top:50%;width:10px;height:1px;background:var(--border-color)"></span>';
            }
            html += '<div class="d-inline-flex align-items-center gap-2 px-3 py-2 mb-2 rounded-3" style="background:' + (depth === 0 ? 'rgba(2,136,209,.14)' : 'var(--bg-hover)') + ';border:1px solid var(--border-color)">';
            if (hasChildren) {
                html += '<button type="button" class="btn btn-sm p-0" style="width:18px;height:18px;line-height:1;color:var(--text-sub)" onclick="window.toggleMindMapNode(\'' + webMindMapEscape(key) + '\')"><i class="bi ' + (collapsed ? 'bi-plus' : 'bi-dash') + '"></i></button>';
            }
            html += '<span style="font-size:13px;font-weight:' + (depth === 0 ? '700' : '500') + '">' + webMindMapEscape(node.title) + '</span>';
            if (hasChildren) {
                html += '<span class="badge" style="background:rgba(2,136,209,.16);color:#0288D1;font-size:10px">' + node.children.length + '</span>';
            }
            html += '</div>';
            if (hasChildren && !collapsed) {
                html += '<div style="border-left:1px dashed var(--border-color);margin-left:6px;padding-left:6px">';
                node.children.forEach(function (child) {
                    html += renderMindMapTreeHtml(child, depth + 1);
                });
                html += '</div>';
            }
            html += '</div>';
            return html;
        }

        function renderMindMapOutline(node, depth) {
            if (!node) return '';
            let html = '<div style="padding-left:' + (depth === 0 ? 0 : 18) + 'px;margin-bottom:6px">';
            html += '<i class="bi ' + (depth === 0 ? 'bi-record-circle' : 'bi-dash') + ' me-1" style="color:#0288D1;font-size:11px"></i>';
            html += '<span style="font-size:13px;font-weight:' + (depth === 0 ? '700' : '400') + '">' + webMindMapEscape(node.title) + '</span>';
            html += '</div>';
            (node.children || []).forEach(function (child) {
                html += renderMindMapOutline(child, depth + 1);
            });
            return html;
        }

        function renderMindMapDetail(map) {
            const container = document.getElementById('tab-content');
            if (!container) return;
            const isVisual = window.webMindMapTab === 0;
            let html = '';
            html += '<div class="d-flex flex-wrap align-items-center gap-2 mb-3">';
            html += '  <button type="button" class="btn btn-sm btn-outline-secondary" onclick="window.closeMindMapDetail()"><i class="bi bi-arrow-left"></i></button>';
            html += '  <div class="flex-grow-1 overflow-hidden">';
            html += '    <h5 class="fw-bold mb-0 text-truncate">' + webMindMapEscape(map.tree ? map.tree.title : map.title) + '</h5>';
            html += '    <small class="text-secondary">' + formatMindMapDate(map.createdAt) + ' • ' + countMindMapNodes(map.tree) + ' cabang</small>';
            html += '  </div>';
            html += '  <div class="d-flex gap-1">';
            html += '    <button type="button" class="btn btn-sm ' + (isVisual ? 'btn-primary' : 'btn-outline-secondary') + '" onclick="window.setMindMapTab(0)"><i class="bi bi-diagram-3 me-1"></i> Visual</button>';
            html += '    <button type="button" class="btn btn-sm ' + (!isVisual ? 'btn-primary' : 'btn-outline-secondary') + '" onclick="window.setMindMapTab(1)"><i class="bi bi-list-ul me-1"></i> Outline</button>';
            html += '    <button type="button" class="btn btn-sm btn-outline-secondary" onclick="window.exportMindMap(\'' + map.id + '\')" title="Ekspor JSON"><i class="bi bi-download"></i></button>';
            html += '  </div>';
            html += '</div>';
            html += '<div class="card card-custom p-3" style="border-radius:16px;max-height:70vh;overflow:auto">';
            html += isVisual ? renderMindMapTreeHtml(map.tree, 0) : renderMindMapOutline(map.tree, 0);
            html += '</div>';
            container.innerHTML = html;
        }

        function openMindMap(id) {
            loadStoredMindMaps();
            const map = window.webMindMaps.find(function (m) { return m.id === id; });
            if (!map) {
                showMindMapToast('Peta konsep tidak ditemukan.');
                return;
            }
            window.webMindMapActiveId = id;
            window.webMindMapTab = 0;
            renderMindMapDetail(map);
        }

        function closeMindMapDetail() {
            window.webMindMapActiveId = null;
            renderMindMapHome();
        }

        function setMindMapTab(tab) {
            window.webMindMapTab = tab;
            const map = window.webMindMaps.find(function (m) { return m.id === window.webMindMapActiveId; });
            if (map) renderMindMapDetail(map);
        }

        function toggleMindMapNode(key) {
            window.webMindMapCollapsed[key] = !window.webMindMapCollapsed[key];
            const map = window.webMindMaps.find(function (m) { return m.id === window.webMindMapActiveId; });
            if (map) renderMindMapDetail(map);
        }

        function deleteMindMap(id) {
            window.webMindMaps = window.webMindMaps.filter(function (m) { return m.id !== id; });
            if (!persistMindMaps()) return;
            if (window.webMindMapActiveId === id) window.webMindMapActiveId = null;
            showMindMapToast('Peta konsep dihapus.');
            renderMindMapHome();
        }

        function exportMindMap(id) {
            const map = window.webMindMaps.find(function (m) { return m.id === id; });
            if (!map) return;
            const blob = new Blob([JSON.stringify(map, null, 2)], { type: 'application/json' });
            const url = URL.createObjectURL(blob);
            const link = document.createElement('a');
            link.href = url;
            link.download = (map.title || 'mindmap') + '.json';
            document.body.appendChild(link);
            link.click();
            document.body.removeChild(link);
            setTimeout(function () { URL.revokeObjectURL(url); }, 4000);
            showMindMapToast('Peta konsep diekspor.');
        }
        """.trimIndent()
    }
}
