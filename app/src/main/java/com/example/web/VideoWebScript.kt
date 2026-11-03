package com.example.web

object VideoWebScript {
    fun getScript(dol: String): String {
        return """
        // =========================================================================
        // --- PEMUTAR VIDEO (100% OFFLINE, TAMPILAN & FUNGSI SETARA ANDROID) ---
        // =========================================================================
        window.webVideoList = window.webVideoList || [];
        window.webVideoSort = window.webVideoSort || 'DATE_DESC';
        window.webVideoGrid = window.webVideoGrid !== false;
        window.webVideoSearch = window.webVideoSearch || '';
        window.webVideoObjectUrls = window.webVideoObjectUrls || {};
        window.webVideoActiveId = null;
        window.webVideoDb = null;
        window.webVideoSpeed = window.webVideoSpeed || 1;
        window.webVideoFitCover = false;

        function webVideoEscape(text) {
            if (typeof escapeHtml === 'function') return escapeHtml(text);
            return (text || '').toString().replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
        }

        function showWebVideoToast(message) {
            let el = document.getElementById('webVideoToast');
            if (!el) {
                el = document.createElement('div');
                el.id = 'webVideoToast';
                el.style.cssText = 'position:fixed;left:50%;bottom:28px;transform:translateX(-50%);background:rgba(15,23,42,.94);color:#fff;padding:10px 18px;border-radius:12px;font-size:13px;z-index:100000;box-shadow:0 8px 24px rgba(0,0,0,.35);opacity:0;transition:opacity .2s ease;max-width:90vw;text-align:center;pointer-events:none;';
                document.body.appendChild(el);
            }
            el.textContent = message;
            el.style.opacity = '1';
            clearTimeout(window.webVideoToastTimer);
            window.webVideoToastTimer = setTimeout(function () { el.style.opacity = '0'; }, 2200);
        }

        function formatWebVideoDuration(totalSeconds) {
            const total = Math.max(0, Math.floor(totalSeconds || 0));
            const h = Math.floor(total / 3600);
            const m = Math.floor((total % 3600) / 60);
            const s = total % 60;
            const ss = String(s).padStart(2, '0');
            if (h > 0) return h + ':' + String(m).padStart(2, '0') + ':' + ss;
            return String(m).padStart(2, '0') + ':' + ss;
        }

        function formatWebVideoSize(bytes) {
            const value = Number(bytes) || 0;
            if (value <= 0) return '-';
            const kb = value / 1024;
            const mb = kb / 1024;
            const gb = mb / 1024;
            if (gb >= 1) return gb.toFixed(2) + ' GB';
            if (mb >= 1) return mb.toFixed(1) + ' MB';
            return Math.floor(kb) + ' KB';
        }

        function openWebVideoDatabase() {
            return new Promise(function (resolve) {
                if (window.webVideoDb) return resolve(window.webVideoDb);
                try {
                    const request = indexedDB.open('SinggharaVideoOfflineDB', 1);
                    request.onupgradeneeded = function (e) {
                        const db = e.target.result;
                        if (!db.objectStoreNames.contains('videos')) {
                            db.createObjectStore('videos', { keyPath: 'id' });
                        }
                    };
                    request.onsuccess = function (e) {
                        window.webVideoDb = e.target.result;
                        resolve(window.webVideoDb);
                    };
                    request.onerror = function () { resolve(null); };
                } catch (_) {
                    resolve(null);
                }
            });
        }

        function readWebVideoDuration(file) {
            return new Promise(function (resolve) {
                let settled = false;
                const url = URL.createObjectURL(file);
                const probe = document.createElement('video');
                const done = function (value) {
                    if (settled) return;
                    settled = true;
                    URL.revokeObjectURL(url);
                    resolve(value);
                };
                probe.preload = 'metadata';
                probe.onloadedmetadata = function () {
                    done(isFinite(probe.duration) ? Math.round(probe.duration) : 0);
                };
                probe.onerror = function () { done(0); };
                setTimeout(function () { done(0); }, 6000);
                probe.src = url;
            });
        }

        async function loadVideoLibrary() {
            const db = await openWebVideoDatabase();
            let items = [];
            if (db) {
                items = await new Promise(function (resolve) {
                    try {
                        const request = db.transaction('videos', 'readonly').objectStore('videos').getAll();
                        request.onsuccess = function () { resolve(request.result || []); };
                        request.onerror = function () { resolve([]); };
                    } catch (_) {
                        resolve([]);
                    }
                });
            }
            window.webVideoList = items;
            window.renderVideoLibrary();
        }

        function loadPemutarVideo() {
            if (window.webVideoActiveId) {
                window.playWebVideo(window.webVideoActiveId);
                return;
            }
            loadVideoLibrary();
        }

        function getVisibleWebVideos() {
            const query = (window.webVideoSearch || '').trim().toLowerCase();
            let list = window.webVideoList.slice();
            if (query) {
                list = list.filter(function (item) {
                    return (item.name || '').toLowerCase().indexOf(query) >= 0;
                });
            }
            const sort = window.webVideoSort;
            list.sort(function (a, b) {
                if (sort === 'NAME_ASC') return (a.name || '').localeCompare(b.name || '');
                if (sort === 'SIZE_DESC') return (b.size || 0) - (a.size || 0);
                if (sort === 'DURATION_DESC') return (b.duration || 0) - (a.duration || 0);
                return (b.dateAdded || 0) - (a.dateAdded || 0);
            });
            return list;
        }

        function getWebVideoObjectUrl(item) {
            const existing = window.webVideoObjectUrls[item.id];
            if (existing) return existing;
            const url = URL.createObjectURL(item.blob);
            window.webVideoObjectUrls[item.id] = url;
            return url;
        }

        function renderVideoLibrary() {
            const container = document.getElementById('tab-content');
            if (!container) return;
            const visible = getVisibleWebVideos();
            const totalDuration = window.webVideoList.reduce(function (sum, item) {
                return sum + (item.duration || 0);
            }, 0);
            const totalSize = window.webVideoList.reduce(function (sum, item) {
                return sum + (item.size || 0);
            }, 0);

            let html = '';
            html += '<div class="d-flex flex-wrap justify-content-between align-items-center mb-3 gap-2">';
            html += '  <div>';
            html += '    <h4 class="fw-bold mb-0 d-flex align-items-center gap-2"><i class="bi bi-camera-reels" style="color:var(--primary-color)"></i> Pemutar Video</h4>';
            html += '    <small class="text-secondary">' + window.webVideoList.length + ' video • ' + formatWebVideoDuration(totalDuration) + ' • ' + formatWebVideoSize(totalSize) + ' (offline)</small>';
            html += '  </div>';
            html += '  <div class="d-flex flex-wrap align-items-center gap-2">';
            html += '    <input class="form-control form-control-sm" style="width:190px" placeholder="Cari video..." value="' + webVideoEscape(window.webVideoSearch) + '" oninput="window.webVideoSearch=this.value; window.renderVideoLibrary();" />';
            html += '    <select class="form-select form-select-sm" style="width:150px" onchange="window.webVideoSort=this.value; window.renderVideoLibrary();">';
            html += '      <option value="DATE_DESC"' + (window.webVideoSort === 'DATE_DESC' ? ' selected' : '') + '>Terbaru</option>';
            html += '      <option value="NAME_ASC"' + (window.webVideoSort === 'NAME_ASC' ? ' selected' : '') + '>Nama A-Z</option>';
            html += '      <option value="SIZE_DESC"' + (window.webVideoSort === 'SIZE_DESC' ? ' selected' : '') + '>Ukuran</option>';
            html += '      <option value="DURATION_DESC"' + (window.webVideoSort === 'DURATION_DESC' ? ' selected' : '') + '>Durasi</option>';
            html += '    </select>';
            html += '    <button type="button" class="btn btn-sm btn-outline-secondary" onclick="window.toggleWebVideoGrid()" title="Tampilan grid / daftar"><i class="bi ' + (window.webVideoGrid ? 'bi-list-ul' : 'bi-grid-3x3-gap') + '"></i></button>';
            html += '    <label class="btn btn-sm btn-primary mb-0"><i class="bi bi-upload me-1"></i> Tambah Video';
            html += '      <input type="file" accept="video/*" multiple hidden onchange="window.handleWebVideoFiles(this.files); this.value=\'\';" />';
            html += '    </label>';
            html += '  </div>';
            html += '</div>';

            if (visible.length === 0) {
                html += '<div class="card card-custom p-5 text-center">';
                html += '  <i class="bi bi-camera-reels" style="font-size:42px;color:var(--primary-color)"></i>';
                html += '  <h6 class="fw-bold mt-3 mb-1">' + (window.webVideoList.length === 0 ? 'Belum ada video di pustaka offline' : 'Video tidak ditemukan') + '</h6>';
                html += '  <p class="text-secondary small mb-0">' + (window.webVideoList.length === 0 ? 'Ketuk "Tambah Video" untuk memuat berkas dari PC. Semua video tersimpan di browser dan dapat diputar tanpa internet.' : 'Coba kata kunci lain atau ubah urutan pencarian.') + '</p>';
                html += '</div>';
            } else if (window.webVideoGrid) {
                html += '<div class="row g-3">';
                visible.forEach(function (item) {
                    const url = getWebVideoObjectUrl(item);
                    html += '<div class="col-12 col-sm-6 col-lg-4 col-xxl-3">';
                    html += '  <div class="card card-custom h-100 overflow-hidden" style="border-radius:16px;cursor:pointer" onclick="window.playWebVideo(\'' + item.id + '\')">';
                    html += '    <div style="position:relative;aspect-ratio:16/9;background:#0f172a;overflow:hidden">';
                    html += '      <video src="' + url + '#t=0.5" preload="metadata" muted playsinline style="width:100%;height:100%;object-fit:cover;pointer-events:none"></video>';
                    html += '      <span class="badge" style="position:absolute;right:8px;bottom:8px;background:rgba(0,0,0,.7);color:#fff;font-size:11px">' + formatWebVideoDuration(item.duration) + '</span>';
                    html += '      <button type="button" class="btn btn-sm" style="position:absolute;top:6px;right:6px;width:28px;height:28px;padding:0;border-radius:50%;background:rgba(0,0,0,.6);color:#fff" title="Hapus video" onclick="event.stopPropagation(); window.deleteWebVideo(\'' + item.id + '\')"><i class="bi bi-trash"></i></button>';
                    html += '      <i class="bi bi-play-circle-fill" style="position:absolute;left:50%;top:50%;transform:translate(-50%,-50%);font-size:44px;color:rgba(255,255,255,.85);pointer-events:none"></i>';
                    html += '    </div>';
                    html += '    <div class="p-3">';
                    html += '      <div class="fw-semibold text-truncate" style="font-size:13px" title="' + webVideoEscape(item.name) + '">' + webVideoEscape(item.name) + '</div>';
                    html += '      <div class="text-secondary" style="font-size:11px">' + formatWebVideoSize(item.size) + '</div>';
                    html += '    </div>';
                    html += '  </div>';
                    html += '</div>';
                });
                html += '</div>';
            } else {
                html += '<div class="d-flex flex-column gap-2">';
                visible.forEach(function (item) {
                    const url = getWebVideoObjectUrl(item);
                    html += '<div class="card card-custom p-2 d-flex align-items-center gap-3" style="border-radius:14px;cursor:pointer" onclick="window.playWebVideo(\'' + item.id + '\')">';
                    html += '  <div style="width:96px;min-width:96px;aspect-ratio:16/9;border-radius:10px;overflow:hidden;background:#0f172a"><video src="' + url + '#t=0.5" preload="metadata" muted playsinline style="width:100%;height:100%;object-fit:cover;pointer-events:none"></video></div>';
                    html += '  <div class="flex-grow-1 overflow-hidden">';
                    html += '    <div class="fw-semibold text-truncate" style="font-size:13px">' + webVideoEscape(item.name) + '</div>';
                    html += '    <div class="text-secondary" style="font-size:11px">' + formatWebVideoDuration(item.duration) + ' • ' + formatWebVideoSize(item.size) + '</div>';
                    html += '  </div>';
                    html += '  <button type="button" class="btn btn-sm btn-outline-danger" title="Hapus video" onclick="event.stopPropagation(); window.deleteWebVideo(\'' + item.id + '\')"><i class="bi bi-trash"></i></button>';
                    html += '</div>';
                });
                html += '</div>';
            }

            container.innerHTML = html;
        }

        function toggleWebVideoGrid() {
            window.webVideoGrid = !window.webVideoGrid;
            window.renderVideoLibrary();
        }

        async function handleWebVideoFiles(files) {
            const list = Array.prototype.slice.call(files || []).filter(function (file) {
                return file && (file.type || '').indexOf('video') === 0;
            });
            if (list.length === 0) {
                showWebVideoToast('Pilih berkas video (MP4, MKV, WEBM, dan lainnya).');
                return;
            }
            const db = await openWebVideoDatabase();
            if (!db) {
                showWebVideoToast('Penyimpanan browser tidak tersedia. Tutup mode privat lalu coba lagi.');
                return;
            }
            let saved = 0;
            for (let i = 0; i < list.length; i++) {
                const file = list[i];
                try {
                    const duration = await readWebVideoDuration(file);
                    const id = 'video_' + Date.now() + '_' + i;
                    await new Promise(function (resolve, reject) {
                        try {
                            const tx = db.transaction('videos', 'readwrite');
                            tx.objectStore('videos').put({
                                id: id,
                                name: file.name || ('Video ' + (i + 1)),
                                size: file.size || 0,
                                type: file.type || 'video/mp4',
                                duration: duration,
                                dateAdded: Date.now(),
                                blob: file
                            });
                            tx.oncomplete = function () { resolve(); };
                            tx.onerror = function () { reject(tx.error || new Error('Gagal menyimpan')); };
                            tx.onabort = function () { reject(tx.error || new Error('Dibatalkan')); };
                        } catch (err) {
                            reject(err);
                        }
                    });
                    saved++;
                } catch (err) {
                    console.warn('Video gagal disimpan', err);
                }
            }
            await loadVideoLibrary();
            showWebVideoToast(saved > 0 ? (saved + ' video ditambahkan ke pustaka offline.') : 'Gagal menyimpan video (kuota browser penuh).');
        }

        function playWebVideo(id) {
            const item = window.webVideoList.find(function (v) { return v.id === id; });
            const container = document.getElementById('tab-content');
            if (!item || !container) return;
            window.webVideoActiveId = id;
            const url = getWebVideoObjectUrl(item);
            window.webVideoSpeed = 1;
            window.webVideoFitCover = false;

            let html = '';
            html += '<div id="webVideoPlayerShell" style="position:fixed;inset:0;background:#000;z-index:1300;display:flex;flex-direction:column">';
            html += '  <div class="d-flex align-items-center gap-2 px-2 py-1" style="background:#0f172a;color:#fff">';
            html += '    <button type="button" class="btn btn-sm text-white" title="Kembali" onclick="window.closeWebVideo()"><i class="bi bi-arrow-left fs-5"></i></button>';
            html += '    <div class="flex-grow-1 overflow-hidden">';
            html += '      <div class="text-truncate" style="font-size:13px;font-weight:600">' + webVideoEscape(item.name) + '</div>';
            html += '      <div class="text-truncate" style="font-size:11px;color:#94a3b8">' + formatWebVideoDuration(item.duration) + ' • ' + formatWebVideoSize(item.size) + '</div>';
            html += '    </div>';
            html += '    <div class="dropdown">';
            html += '      <button type="button" class="btn btn-sm text-white" data-bs-toggle="dropdown" title="Opsi"><i class="bi bi-three-dots-vertical fs-5"></i></button>';
            html += '      <ul class="dropdown-menu dropdown-menu-end">';
            html += '        <li><h6 class="dropdown-header">Kecepatan</h6></li>';
            [0.5, 0.75, 1, 1.25, 1.5, 2].forEach(function (speed) {
                html += '        <li><a class="dropdown-item" href="javascript:void(0)" onclick="window.setWebVideoSpeed(' + speed + ')">' + speed + 'x</a></li>';
            });
            html += '        <li><hr class="dropdown-divider"></li>';
            html += '        <li><a class="dropdown-item" href="javascript:void(0)" onclick="window.toggleWebVideoFit()"><i class="bi bi-aspect-ratio me-1"></i> Sesuai layar / isi layar</a></li>';
            html += '        <li><a class="dropdown-item" href="javascript:void(0)" onclick="window.downloadWebVideo(\'' + item.id + '\')"><i class="bi bi-download me-1"></i> Unduh ke PC</a></li>';
            html += '        <li><a class="dropdown-item text-danger" href="javascript:void(0)" onclick="window.closeWebVideo(); window.deleteWebVideo(\'' + item.id + '\')"><i class="bi bi-trash me-1"></i> Hapus dari pustaka</a></li>';
            html += '      </ul>';
            html += '    </div>';
            html += '  </div>';
            html += '  <div class="flex-grow-1 d-flex align-items-center justify-content-center" style="overflow:hidden">';
            html += '    <video id="webVideoPlayer" src="' + url + '" controls autoplay playsinline preload="metadata" style="width:100%;height:100%;object-fit:contain;background:#000"></video>';
            html += '  </div>';
            html += '  <div class="text-center" style="background:#0f172a;color:#94a3b8;font-size:11px;padding:6px">Spasi: putar/jeda • Panah kiri/kanan: 5 detik • Esc: kembali</div>';
            html += '</div>';

            container.innerHTML = html;

            const player = document.getElementById('webVideoPlayer');
            if (player) {
                player.playbackRate = 1;
                player.focus();
                window.webVideoKeyHandler = function (event) {
                    if (!document.getElementById('webVideoPlayer')) return;
                    if (event.key === ' ' || event.code === 'Space') {
                        event.preventDefault();
                        if (player.paused) player.play(); else player.pause();
                    } else if (event.key === 'ArrowRight') {
                        player.currentTime = Math.min((player.duration || 0), player.currentTime + 5);
                    } else if (event.key === 'ArrowLeft') {
                        player.currentTime = Math.max(0, player.currentTime - 5);
                    } else if (event.key === 'Escape') {
                        window.closeWebVideo();
                    }
                };
                document.addEventListener('keydown', window.webVideoKeyHandler);
            }
        }

        function setWebVideoSpeed(speed) {
            const player = document.getElementById('webVideoPlayer');
            if (!player) return;
            window.webVideoSpeed = speed;
            player.playbackRate = speed;
            showWebVideoToast('Kecepatan ' + speed + 'x');
        }

        function toggleWebVideoFit() {
            const player = document.getElementById('webVideoPlayer');
            if (!player) return;
            window.webVideoFitCover = !window.webVideoFitCover;
            player.style.objectFit = window.webVideoFitCover ? 'cover' : 'contain';
            showWebVideoToast(window.webVideoFitCover ? 'Mode isi layar' : 'Mode sesuai video');
        }

        function closeWebVideo() {
            window.webVideoActiveId = null;
            if (window.webVideoKeyHandler) {
                document.removeEventListener('keydown', window.webVideoKeyHandler);
                window.webVideoKeyHandler = null;
            }
            const shell = document.getElementById('webVideoPlayerShell');
            if (shell) shell.remove();
            window.renderVideoLibrary();
        }

        function downloadWebVideo(id) {
            const item = window.webVideoList.find(function (v) { return v.id === id; });
            if (!item) return;
            const url = getWebVideoObjectUrl(item);
            const link = document.createElement('a');
            link.href = url;
            link.download = item.name || 'video.mp4';
            document.body.appendChild(link);
            link.click();
            document.body.removeChild(link);
            showWebVideoToast('Mengunduh ' + item.name);
        }

        async function deleteWebVideo(id) {
            const item = window.webVideoList.find(function (v) { return v.id === id; });
            if (!item) return;
            const db = await openWebVideoDatabase();
            if (db) {
                await new Promise(function (resolve) {
                    try {
                        const tx = db.transaction('videos', 'readwrite');
                        tx.objectStore('videos').delete(id);
                        tx.oncomplete = function () { resolve(); };
                        tx.onerror = function () { resolve(); };
                    } catch (_) {
                        resolve();
                    }
                });
            }
            if (window.webVideoObjectUrls[id]) {
                URL.revokeObjectURL(window.webVideoObjectUrls[id]);
                delete window.webVideoObjectUrls[id];
            }
            showWebVideoToast('Video dihapus dari pustaka.');
            await loadVideoLibrary();
        }
        """.trimIndent()
    }
}
