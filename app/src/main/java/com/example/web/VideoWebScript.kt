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

        // Sumber berkas: 'all' | 'phone' (MediaStore HP) | 'pc' (pustaka offline browser).
        window.webVideoSourceFilter = 'all';
        window.webVideoPhoneLoaded = window.webVideoPhoneLoaded || false;
        window.webVideoPhoneAutoTried = false;
        window.webVideoPhoneLoading = false;
        window.webVideoPhonePermission = true;
        window.webVideoPhoneError = '';

        function webVideoEscape(text) {
            if (typeof escapeHtml === 'function') return escapeHtml(text);
            return (text || '').toString().replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
        }

        function showWebVideoToast(message) {
            let el = document.getElementById('webVideoToast');
            if (!el) {
                el = document.createElement('div');
                el.id = 'webVideoToast';
                el.style.cssText = 'position:fixed;left:50%;bottom:28px;transform:translateX(-50%);background:var(--card-bg,#0f172a);color:var(--text-main,#fff);padding:10px 18px;border-radius:12px;font-size:13px;z-index:100000;box-shadow:0 8px 24px rgba(0,0,0,.35);opacity:0;transition:opacity .2s ease;max-width:90vw;text-align:center;pointer-events:none;border:1px solid var(--border-color,#334155);';
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
            items.forEach(function (item) { item.origin = 'pc'; });
            items = items.filter(function (item) { return !!item.blob; });
            const phoneItems = window.webVideoList.filter(function (v) { return v.origin === 'phone'; });
            window.webVideoList = items.concat(phoneItems);
            window.renderVideoLibrary();
        }

        async function loadPhoneVideoLibrary(showToast) {
            if (window.webVideoPhoneLoading) return;
            window.webVideoPhoneLoading = true;
            if (showToast) showWebVideoToast('Memindai video yang ada di HP...');
            try {
                const res = await fetch('/api/phone_media/video', { cache: 'no-store' });
                const data = await res.json();
                window.webVideoPhonePermission = data.hasPermission !== false;
                window.webVideoPhoneError = window.webVideoPhonePermission ? '' : 'Izin baca video di HP belum diberikan.';
                const phoneItems = (data.items || []).map(function (item) {
                    return {
                        id: 'phone_video_' + item.id,
                        mediaId: item.id,
                        origin: 'phone',
                        name: item.title || 'Video',
                        folder: item.folder || 'Video HP',
                        size: item.sizeBytes || 0,
                        duration: Math.round((item.durationMs || 0) / 1000),
                        type: item.mime || 'video/mp4',
                        dateAdded: (item.dateAddedSec || 0) * 1000,
                        url: item.url,
                        artUrl: item.artUrl
                    };
                });
                window.webVideoList = window.webVideoList
                    .filter(function (v) { return v.origin !== 'phone'; })
                    .concat(phoneItems);
                window.webVideoPhoneLoaded = true;
                if (showToast) showWebVideoToast(phoneItems.length + ' video dari HP berhasil dimuat.');
            } catch (err) {
                window.webVideoPhoneError = 'Layanan media HP tidak terjangkau: ' + err.message;
                if (showToast) showWebVideoToast('Gagal memuat video dari HP.');
            } finally {
                window.webVideoPhoneLoading = false;
                window.renderVideoLibrary();
            }
        }

        function loadPemutarVideo() {
            if (window.webVideoActiveId) {
                window.playWebVideo(window.webVideoActiveId);
                return;
            }
            loadVideoLibrary().then(function () {
                if (!window.webVideoPhoneLoaded && !window.webVideoPhoneAutoTried) {
                    window.webVideoPhoneAutoTried = true;
                    loadPhoneVideoLibrary(false);
                }
            });
        }

        function getWebVideoSourceFilterHtml() {
            const phoneCount = window.webVideoList.filter(function (v) { return v.origin === 'phone'; }).length;
            const pcCount = window.webVideoList.length - phoneCount;
            let html = '<div class="d-flex align-items-center gap-2 mb-3 flex-wrap">';
            [['all', 'Semua (' + window.webVideoList.length + ')', ''],
             ['phone', 'HP (' + phoneCount + ')', 'bi-phone'],
             ['pc', 'PC (' + pcCount + ')', 'bi-pc-display']].forEach(function (entry) {
                const active = window.webVideoSourceFilter === entry[0];
                html += '<button type="button" class="btn btn-sm ' + (active ? 'btn-primary' : 'btn-outline-secondary') + '" onclick="window.setWebVideoSourceFilter(\'' + entry[0] + '\')">';
                if (entry[2]) html += '<i class="bi ' + entry[2] + ' me-1"></i>';
                html += entry[1] + '</button>';
            });
            html += '<button type="button" class="btn btn-sm btn-outline-primary ms-auto" onclick="window.loadPhoneVideoLibrary(true)"><i class="bi bi-arrow-repeat me-1"></i>' + (phoneCount > 0 ? 'Pindai Ulang HP' : 'Muat Video HP') + '</button>';
            html += '</div>';
            if (window.webVideoPhoneError) {
                html += '<div class="alert alert-warning py-2 px-3 small">' + webVideoEscape(window.webVideoPhoneError) + '</div>';
            }
            return html;
        }

        function setWebVideoSourceFilter(filter) {
            window.webVideoSourceFilter = filter;
            window.renderVideoLibrary();
        }

        function getVisibleWebVideos() {
            const query = (window.webVideoSearch || '').trim().toLowerCase();
            const filter = window.webVideoSourceFilter || 'all';
            let list = window.webVideoList.filter(function (item) {
                if (filter === 'phone') return item.origin === 'phone';
                if (filter === 'pc') return item.origin !== 'phone';
                return true;
            });
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
            if (!item.blob) return '';
            const url = URL.createObjectURL(item.blob);
            window.webVideoObjectUrls[item.id] = url;
            return url;
        }

        // Berkas HP dialirkan lewat /api/phone_media/stream (mendukung Range), berkas PC lewat blob.
        function getWebVideoPlayUrl(item) {
            if (item.origin === 'phone') return item.url;
            return getWebVideoObjectUrl(item);
        }

        function getWebVideoThumbHtml(item, style) {
            if (item.origin === 'phone') {
                if (item.artUrl) {
                    return '<img src="' + item.artUrl + '" loading="lazy" alt="" style="' + style + '">';
                }
                return '<i class="bi bi-film" style="font-size:34px;color:var(--text-sub)"></i>';
            }
            return '<video src="' + getWebVideoPlayUrl(item) + '#t=0.5" preload="metadata" muted playsinline style="' + style + '"></video>';
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
            const phoneCount = window.webVideoList.filter(function (v) { return v.origin === 'phone'; }).length;

            let html = '';
            html += '<div class="d-flex flex-wrap justify-content-between align-items-center mb-3 gap-2">';
            html += '  <div>';
            html += '    <h4 class="fw-bold mb-0 d-flex align-items-center gap-2"><i class="bi bi-camera-reels" style="color:var(--brand-green)"></i> Pemutar Video</h4>';
            html += '    <small class="text-secondary">' + window.webVideoList.length + ' video • ' + formatWebVideoDuration(totalDuration) + ' • ' + formatWebVideoSize(totalSize) + ' (' + phoneCount + ' dari HP, ' + (window.webVideoList.length - phoneCount) + ' dari PC)</small>';
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

            html += getWebVideoSourceFilterHtml();

            if (visible.length === 0) {
                html += '<div class="card card-custom p-5 text-center">';
                html += '  <i class="bi bi-camera-reels" style="font-size:42px;color:var(--brand-green)"></i>';
                html += '  <h6 class="fw-bold mt-3 mb-1">' + (window.webVideoList.length === 0 ? 'Belum ada video di pustaka' : 'Video tidak ditemukan') + '</h6>';
                html += '  <p class="text-secondary small mb-3">' + (window.webVideoList.length === 0 ? 'Ketuk "Tambah Video" untuk memuat berkas dari PC, atau "Muat Video HP" untuk memutar rekaman yang tersimpan di ponsel.' : 'Coba kata kunci lain, ubah urutan, atau pilih sumber berkas yang berbeda.') + '</p>';
                if (window.webVideoList.length === 0) {
                    html += '  <button type="button" class="btn btn-outline-primary btn-sm" onclick="window.loadPhoneVideoLibrary(true)"><i class="bi bi-phone me-1"></i> Muat Video HP</button>';
                }
                html += '</div>';
            } else if (window.webVideoGrid) {
                html += '<div class="row g-3">';
                visible.forEach(function (item) {
                    const isPhone = item.origin === 'phone';
                    const thumbStyle = 'width:100%;height:100%;object-fit:cover;pointer-events:none';
                    html += '<div class="col-12 col-sm-6 col-lg-4 col-xxl-3">';
                    html += '  <div class="card card-custom h-100 overflow-hidden" style="border-radius:16px;cursor:pointer" onclick="window.playWebVideo(\'' + item.id + '\')">';
                    html += '    <div style="position:relative;aspect-ratio:16/9;background:var(--surface-subtle);overflow:hidden;display:flex;align-items:center;justify-content:center">';
                    html += '      ' + getWebVideoThumbHtml(item, thumbStyle);
                    html += '      <span class="badge" style="position:absolute;right:8px;bottom:8px;background:rgba(0,0,0,.7);color:#fff;font-size:11px">' + formatWebVideoDuration(item.duration) + '</span>';
                    html += '      <span class="badge" style="position:absolute;left:8px;top:8px;background:rgba(0,0,0,.6);color:#fff;font-size:10px">' + (isPhone ? 'HP' : 'PC') + '</span>';
                    if (!isPhone) {
                        html += '      <button type="button" class="btn btn-sm" style="position:absolute;top:6px;right:6px;width:28px;height:28px;padding:0;border-radius:50%;background:rgba(0,0,0,.6);color:#fff" title="Hapus video" onclick="event.stopPropagation(); window.deleteWebVideo(\'' + item.id + '\')"><i class="bi bi-trash"></i></button>';
                    }
                    html += '      <i class="bi bi-play-circle-fill" style="position:absolute;left:50%;top:50%;transform:translate(-50%,-50%);font-size:44px;color:rgba(255,255,255,.85);pointer-events:none;filter:drop-shadow(0 1px 4px rgba(0,0,0,.6))"></i>';
                    html += '    </div>';
                    html += '    <div class="p-3">';
                    html += '      <div class="fw-semibold text-truncate" style="font-size:13px" title="' + webVideoEscape(item.name) + '">' + webVideoEscape(item.name) + '</div>';
                    html += '      <div class="text-secondary" style="font-size:11px">' + webVideoEscape(item.folder || 'Video') + ' • ' + formatWebVideoSize(item.size) + '</div>';
                    html += '    </div>';
                    html += '  </div>';
                    html += '</div>';
                });
                html += '</div>';
            } else {
                html += '<div class="d-flex flex-column gap-2">';
                visible.forEach(function (item) {
                    const isPhone = item.origin === 'phone';
                    const thumbStyle = 'width:100%;height:100%;object-fit:cover;pointer-events:none';
                    html += '<div class="card card-custom p-2 d-flex align-items-center gap-3" style="border-radius:14px;cursor:pointer" onclick="window.playWebVideo(\'' + item.id + '\')">';
                    html += '  <div style="width:96px;min-width:96px;aspect-ratio:16/9;border-radius:10px;overflow:hidden;background:var(--surface-subtle);display:flex;align-items:center;justify-content:center">' + getWebVideoThumbHtml(item, thumbStyle) + '</div>';
                    html += '  <div class="flex-grow-1 overflow-hidden">';
                    html += '    <div class="fw-semibold text-truncate" style="font-size:13px">' + webVideoEscape(item.name) + '</div>';
                    html += '    <div class="text-secondary" style="font-size:11px">' + formatWebVideoDuration(item.duration) + ' • ' + formatWebVideoSize(item.size) + ' • ' + webVideoEscape(item.folder || (isPhone ? 'Video HP' : 'Pustaka PC')) + '</div>';
                    html += '  </div>';
                    html += '  <span class="badge" style="background:var(--surface-hover);color:var(--text-sub);font-size:10px">' + (isPhone ? 'HP' : 'PC') + '</span>';
                    if (!isPhone) {
                        html += '  <button type="button" class="btn btn-sm btn-outline-danger" title="Hapus video" onclick="event.stopPropagation(); window.deleteWebVideo(\'' + item.id + '\')"><i class="bi bi-trash"></i></button>';
                    }
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
            const url = getWebVideoPlayUrl(item);
            const isPhone = item.origin === 'phone';
            window.webVideoSpeed = 1;
            window.webVideoFitCover = false;
            window.webVideoScrubbing = false;
            clearTimeout(window.webVideoHideTimer);

            let html = '';
            html += '<style>' + getWebVideoPlayerCss() + '</style>';
            html += '<div id="webVideoPlayerShell" class="wvp-shell">';
            html += '  <div class="wvp-video-wrap" id="wvpVideoWrap">';
            html += '    <video id="webVideoPlayer" class="wvp-video" src="' + url + '" autoplay playsinline preload="metadata"></video>';
            html += '    <div class="wvp-gesture-flash wvp-flash-left" id="wvpFlashBackward"><i class="bi bi-skip-backward-fill"></i><span>10s</span></div>';
            html += '    <div class="wvp-gesture-flash wvp-flash-right" id="wvpFlashForward"><i class="bi bi-skip-forward-fill"></i><span>10s</span></div>';
            html += '    <button class="wvp-center-play" id="wvpCenterPlay" onclick="window.wvpTogglePlay()" title="Putar"><i class="bi bi-play-fill"></i></button>';

            html += '    <div class="wvp-top-bar">';
            html += '      <button class="wvp-icon-btn" onclick="window.closeWebVideo()" title="Kembali"><i class="bi bi-arrow-left"></i></button>';
            html += '      <div class="wvp-title-block">';
            html += '        <div class="wvp-title">' + webVideoEscape(item.name) + ' <span class="wvp-source-tag">[' + (isPhone ? 'HP' : 'PC') + ']</span></div>';
            html += '        <div class="wvp-sub">' + formatWebVideoDuration(item.duration) + ' • ' + formatWebVideoSize(item.size) + ' • ' + webVideoEscape(item.folder || '') + '</div>';
            html += '      </div>';
            html += '    </div>';

            html += '    <div class="wvp-bottom-bar">';
            html += '      <div class="wvp-seek-row">';
            html += '        <span class="wvp-time" id="wvpCurTime">00:00</span>';
            html += '        <div class="wvp-seek-wrap">';
            html += '          <div class="wvp-seek-buffer" id="wvpBuffer"></div>';
            html += '          <div class="wvp-seek-played" id="wvpPlayed"></div>';
            html += '          <input type="range" class="wvp-seek" id="wvpSeek" min="0" max="1000" step="1" value="0" oninput="window.wvpScrub(this.value)" onchange="window.wvpCommitScrub(this.value)">';
            html += '        </div>';
            html += '        <span class="wvp-time" id="wvpDurTime">' + formatWebVideoDuration(item.duration) + '</span>';
            html += '      </div>';
            html += '      <div class="wvp-btn-row">';
            html += '        <button class="wvp-icon-btn wvp-skip-btn" onclick="window.wvpSkip(-10)" title="-10 detik"><i class="bi bi-skip-backward-fill"></i><span class="wvp-badge">10</span></button>';
            html += '        <button class="wvp-icon-btn wvp-play-btn" id="wvpPlayBtn" onclick="window.wvpTogglePlay()" title="Putar / Jeda"><i class="bi bi-play-fill"></i></button>';
            html += '        <button class="wvp-icon-btn wvp-skip-btn" onclick="window.wvpSkip(10)" title="+10 detik"><i class="bi bi-skip-forward-fill"></i><span class="wvp-badge">10</span></button>';
            html += '        <div class="wvp-spacer"></div>';
            html += '        <button class="wvp-icon-btn" id="wvpFitBtn" onclick="window.toggleWebVideoFit()" title="Rasio layar"><i class="bi bi-aspect-ratio"></i></button>';
            html += '        <button class="wvp-icon-btn" id="wvpMuteBtn" onclick="window.wvpToggleMute()" title="Bisukan"><i class="bi bi-volume-up-fill"></i></button>';
            html += '        <input type="range" class="wvp-vol" id="wvpVol" min="0" max="1" step="0.01" value="1" oninput="window.wvpSetVolume(this.value)">';
            html += '        <button class="wvp-speed-btn" id="wvpSpeedBtn" onclick="window.wvpCycleSpeed()" title="Kecepatan">1x</button>';
            html += '        <button class="wvp-icon-btn" onclick="window.wvpToggleFullscreen()" title="Layar penuh (F)"><i class="bi bi-fullscreen"></i></button>';
            html += '      </div>';
            html += '    </div>';
            html += '  </div>';
            html += '</div>';

            container.innerHTML = html;

            const player = document.getElementById('webVideoPlayer');
            if (player) {
                player.playbackRate = 1;
                player.onerror = function () {
                    showWebVideoToast(isPhone
                        ? 'Video HP gagal diputar (format mungkin tidak didukung browser).'
                        : 'Video gagal diputar dari pustaka offline.');
                };
                window.wvpWirePlayer();

                window.webVideoKeyHandler = function (event) {
                    if (!document.getElementById('webVideoPlayer')) return;
                    const target = event.target;
                    if (target && (target.tagName === 'INPUT' || target.tagName === 'SELECT' || target.tagName === 'TEXTAREA')) return;
                    if (event.key === ' ' || event.code === 'Space') {
                        event.preventDefault();
                        window.wvpTogglePlay();
                    } else if (event.key === 'ArrowRight') {
                        event.preventDefault();
                        window.wvpSkip(event.shiftKey ? 30 : 10);
                    } else if (event.key === 'ArrowLeft') {
                        event.preventDefault();
                        window.wvpSkip(event.shiftKey ? -30 : -10);
                    } else if (event.key === 'ArrowUp') {
                        event.preventDefault();
                        player.volume = Math.min(1, (player.volume || 0) + 0.1);
                        player.muted = false;
                        window.wvpSyncVolumeUI();
                    } else if (event.key === 'ArrowDown') {
                        event.preventDefault();
                        player.volume = Math.max(0, (player.volume || 0) - 0.1);
                        window.wvpSyncVolumeUI();
                    } else if (event.key === 'm' || event.key === 'M') {
                        window.wvpToggleMute();
                    } else if (event.key === 'f' || event.key === 'F') {
                        window.wvpToggleFullscreen();
                    } else if (event.key === 'Escape') {
                        if (!document.fullscreenElement) window.closeWebVideo();
                    }
                };
                document.addEventListener('keydown', window.webVideoKeyHandler);
            }
        }

        function getWebVideoPlayerCss() {
            return [
                '.wvp-shell{position:fixed;inset:0;background:#000;z-index:1300;display:flex;flex-direction:column;color:#fff;font-family:system-ui,-apple-system,"Segoe UI",Roboto,sans-serif;}',
                '.wvp-video-wrap{position:relative;flex:1;overflow:hidden;background:#000;}',
                '.wvp-video{width:100%;height:100%;object-fit:contain;background:#000;display:block;}',
                '.wvp-top-bar{position:absolute;left:0;right:0;top:0;padding:10px 14px 24px;display:flex;align-items:flex-start;gap:10px;background:linear-gradient(to bottom,rgba(0,0,0,.65),transparent);transition:opacity .3s ease;z-index:3;}',
                '.wvp-title-block{flex:1;min-width:0;}',
                '.wvp-title{font-size:14px;font-weight:600;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;}',
                '.wvp-source-tag{font-size:10px;opacity:.7;margin-left:6px;}',
                '.wvp-sub{font-size:11px;opacity:.72;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;}',
                '.wvp-bottom-bar{position:absolute;left:0;right:0;bottom:0;padding:24px 14px 12px;background:linear-gradient(to top,rgba(0,0,0,.75),transparent);transition:opacity .3s ease;z-index:3;}',
                '.wvp-seek-row{display:flex;align-items:center;gap:10px;margin-bottom:6px;}',
                '.wvp-time{font-size:11px;font-variant-numeric:tabular-nums;min-width:42px;text-align:center;opacity:.9;}',
                '.wvp-seek-wrap{flex:1;position:relative;height:16px;display:flex;align-items:center;}',
                '.wvp-seek-buffer,.wvp-seek-played{position:absolute;left:0;top:50%;transform:translateY(-50%);height:4px;border-radius:99px;pointer-events:none;}',
                '.wvp-seek-buffer{background:rgba(255,255,255,.22);width:0;}',
                '.wvp-seek-played{background:var(--brand-green);width:0;box-shadow:0 0 6px rgba(0,0,0,.5);}',
                '.wvp-seek{-webkit-appearance:none;appearance:none;position:relative;z-index:2;width:100%;background:transparent;height:16px;margin:0;cursor:pointer;}',
                '.wvp-seek::-webkit-slider-runnable-track{height:4px;border-radius:99px;background:rgba(255,255,255,.25);}',
                '.wvp-seek::-webkit-slider-thumb{-webkit-appearance:none;appearance:none;width:14px;height:14px;margin-top:-5px;border:0;border-radius:50%;background:#fff;box-shadow:0 1px 4px rgba(0,0,0,.55);}',
                '.wvp-seek::-moz-range-track{height:4px;border-radius:99px;background:rgba(255,255,255,.25);border:0;}',
                '.wvp-seek::-moz-range-thumb{width:14px;height:14px;border:0;border-radius:50%;background:#fff;box-shadow:0 1px 4px rgba(0,0,0,.55);}',
                '.wvp-btn-row{display:flex;align-items:center;gap:6px;}',
                '.wvp-icon-btn{background:transparent;border:0;color:#fff;cursor:pointer;padding:6px;line-height:1;display:inline-flex;align-items:center;justify-content:center;position:relative;font-size:22px;transition:transform .15s ease,color .2s ease;}',
                '.wvp-icon-btn i{pointer-events:none;}',
                '.wvp-icon-btn:hover{color:var(--brand-green);}',
                '.wvp-icon-btn:active{transform:scale(.9);}',
                '.wvp-play-btn{font-size:34px;padding:4px 10px;}',
                '.wvp-skip-btn{font-size:22px;}',
                '.wvp-badge{position:absolute;font-size:9px;font-weight:800;bottom:2px;left:50%;transform:translateX(-50%);}',
                '.wvp-spacer{flex:1;}',
                '.wvp-vol{-webkit-appearance:none;appearance:none;background:transparent;width:80px;height:16px;margin:0;cursor:pointer;}',
                '.wvp-vol::-webkit-slider-runnable-track{height:4px;border-radius:99px;background:rgba(255,255,255,.3);}',
                '.wvp-vol::-webkit-slider-thumb{-webkit-appearance:none;appearance:none;width:12px;height:12px;margin-top:-4px;border:0;border-radius:50%;background:#fff;}',
                '.wvp-vol::-moz-range-track{height:4px;border-radius:99px;background:rgba(255,255,255,.3);border:0;}',
                '.wvp-vol::-moz-range-thumb{width:12px;height:12px;border:0;border-radius:50%;background:#fff;}',
                '.wvp-speed-btn{background:rgba(255,255,255,.15);border:1px solid rgba(255,255,255,.3);color:#fff;padding:3px 10px;border-radius:99px;font-size:12px;font-weight:700;cursor:pointer;margin:0 4px;}',
                '.wvp-speed-btn:hover{background:rgba(255,255,255,.28);}',
                '.wvp-center-play{position:absolute;left:50%;top:50%;transform:translate(-50%,-50%);width:78px;height:78px;border-radius:50%;background:rgba(0,0,0,.5);border:2px solid rgba(255,255,255,.4);color:#fff;display:flex;align-items:center;justify-content:center;font-size:38px;cursor:pointer;z-index:4;transition:opacity .3s ease,transform .2s ease;backdrop-filter:blur(4px);}',
                '.wvp-center-play:hover{transform:translate(-50%,-50%) scale(1.08);}',
                '.wvp-gesture-flash{position:absolute;top:50%;transform:translateY(-50%);padding:14px 18px;border-radius:14px;background:rgba(0,0,0,.6);color:#fff;font-weight:800;font-size:14px;pointer-events:none;opacity:0;z-index:5;display:flex;flex-direction:column;align-items:center;gap:2px;transition:opacity .25s ease;}',
                '.wvp-gesture-flash i{font-size:26px;}',
                '.wvp-gesture-flash.show{opacity:1;}',
                '.wvp-flash-left{left:15%;}',
                '.wvp-flash-right{right:15%;}',
                '.wvp-autohide .wvp-top-bar,.wvp-autohide .wvp-bottom-bar,.wvp-autohide .wvp-center-play{opacity:0;pointer-events:none;}',
                '@media (max-width:600px){.wvp-vol{width:60px;}.wvp-sub{display:none;}.wvp-seek-row .wvp-time{min-width:34px;font-size:10px;}}'
            ].join('');
        }

        function wvpWirePlayer() {
            const player = document.getElementById('webVideoPlayer');
            if (!player) return;
            player.addEventListener('timeupdate', wvpUpdateProgress);
            player.addEventListener('loadedmetadata', function () {
                const dur = isFinite(player.duration) ? player.duration : 0;
                const durEl = document.getElementById('wvpDurTime');
                if (durEl && dur > 0) durEl.innerText = formatWebVideoDuration(dur);
                wvpUpdateProgress();
            });
            player.addEventListener('progress', wvpUpdateProgress);
            player.addEventListener('play', function () { wvpUpdatePlayIcon(true); wvpScheduleHide(); });
            player.addEventListener('pause', function () { wvpUpdatePlayIcon(false); wvpShowControls(); });
            player.addEventListener('ended', function () { wvpUpdatePlayIcon(false); wvpShowControls(); });
            player.addEventListener('volumechange', wvpSyncVolumeUI);
            wvpSyncVolumeUI();

            const wrap = document.getElementById('wvpVideoWrap');
            if (wrap) {
                let lastTapTime = 0;
                let singleTapTimer = null;
                wrap.addEventListener('click', function (ev) {
                    if (ev.target.closest('.wvp-top-bar') || ev.target.closest('.wvp-bottom-bar') ||
                        ev.target.closest('.wvp-center-play') || ev.target.closest('.dropdown')) return;
                    const now = Date.now();
                    if (now - lastTapTime < 300) {
                        lastTapTime = 0;
                        clearTimeout(singleTapTimer);
                        const rect = wrap.getBoundingClientRect();
                        const rel = (ev.clientX - rect.left) / Math.max(1, rect.width);
                        if (rel < 0.35) wvpSkipFlash(-10, 'backward');
                        else if (rel > 0.65) wvpSkipFlash(10, 'forward');
                        else wvpTogglePlay();
                    } else {
                        lastTapTime = now;
                        singleTapTimer = setTimeout(function () { wvpShowControlsToggle(); }, 280);
                    }
                });
                wrap.addEventListener('mousemove', wvpShowControls);
                wrap.addEventListener('touchstart', wvpShowControls, { passive: true });
            }

            wvpShowControls();
        }

        function wvpUpdateProgress() {
            const player = document.getElementById('webVideoPlayer');
            if (!player) return;
            const dur = isFinite(player.duration) && player.duration > 0 ? player.duration : 0;
            const pos = player.currentTime || 0;
            const pct = dur > 0 ? Math.min(100, pos * 100 / dur) : 0;
            const played = document.getElementById('wvpPlayed');
            const seek = document.getElementById('wvpSeek');
            const cur = document.getElementById('wvpCurTime');
            const durEl = document.getElementById('wvpDurTime');
            const buf = document.getElementById('wvpBuffer');
            if (played) played.style.width = pct + '%';
            if (cur) cur.innerText = formatWebVideoDuration(pos);
            if (durEl && dur > 0) durEl.innerText = formatWebVideoDuration(dur);
            if (seek && !window.webVideoScrubbing) seek.value = Math.round(pct * 10);
            if (buf && player.buffered && player.buffered.length > 0 && dur > 0) {
                let end = 0;
                for (let i = 0; i < player.buffered.length; i++) {
                    const segEnd = player.buffered.end(i);
                    if (segEnd > end) end = segEnd;
                }
                buf.style.width = Math.min(100, end * 100 / dur) + '%';
            }
        }

        function wvpScrub(v) {
            window.webVideoScrubbing = true;
            const player = document.getElementById('webVideoPlayer');
            const dur = player && isFinite(player.duration) ? player.duration : 0;
            const pct = Number(v) / 1000;
            const played = document.getElementById('wvpPlayed');
            const cur = document.getElementById('wvpCurTime');
            if (played) played.style.width = (pct * 100) + '%';
            if (cur && dur > 0) cur.innerText = formatWebVideoDuration(pct * dur);
        }

        function wvpCommitScrub(v) {
            const player = document.getElementById('webVideoPlayer');
            if (player && isFinite(player.duration)) {
                const pct = Number(v) / 1000;
                player.currentTime = pct * player.duration;
            }
            window.webVideoScrubbing = false;
            wvpShowControls();
        }

        function wvpTogglePlay() {
            const player = document.getElementById('webVideoPlayer');
            if (!player) return;
            if (player.paused) player.play().catch(function () {}); else player.pause();
        }

        function wvpSkip(sec) {
            const player = document.getElementById('webVideoPlayer');
            if (!player) return;
            const dur = isFinite(player.duration) ? player.duration : 0;
            player.currentTime = Math.max(0, Math.min(dur || player.currentTime + sec, (player.currentTime || 0) + sec));
            wvpShowControls();
        }

        function wvpSkipFlash(sec, side) {
            wvpSkip(sec);
            const el = document.getElementById(side === 'backward' ? 'wvpFlashBackward' : 'wvpFlashForward');
            if (el) {
                el.classList.add('show');
                setTimeout(function () { el.classList.remove('show'); }, 700);
            }
        }

        function wvpUpdatePlayIcon(isPlaying) {
            const center = document.getElementById('wvpCenterPlay');
            const bottom = document.getElementById('wvpPlayBtn');
            if (center) {
                center.style.opacity = isPlaying ? '0' : '1';
                center.style.pointerEvents = isPlaying ? 'none' : 'auto';
                center.innerHTML = isPlaying ? '<i class="bi bi-pause-fill"></i>' : '<i class="bi bi-play-fill"></i>';
            }
            if (bottom) bottom.innerHTML = isPlaying ? '<i class="bi bi-pause-fill"></i>' : '<i class="bi bi-play-fill"></i>';
        }

        function wvpShowControls() {
            const shell = document.getElementById('webVideoPlayerShell');
            if (shell) shell.classList.remove('wvp-autohide');
            wvpScheduleHide();
        }

        function wvpShowControlsToggle() {
            const shell = document.getElementById('webVideoPlayerShell');
            if (!shell) return;
            if (shell.classList.contains('wvp-autohide')) {
                shell.classList.remove('wvp-autohide');
                wvpScheduleHide();
            } else {
                shell.classList.add('wvp-autohide');
                clearTimeout(window.webVideoHideTimer);
            }
        }

        function wvpScheduleHide() {
            const shell = document.getElementById('webVideoPlayerShell');
            const player = document.getElementById('webVideoPlayer');
            if (!shell || !player) return;
            clearTimeout(window.webVideoHideTimer);
            if (player.paused) return;
            window.webVideoHideTimer = setTimeout(function () {
                const sh = document.getElementById('webVideoPlayerShell');
                if (!sh) return;
                const topBar = sh.querySelector('.wvp-top-bar');
                const bottomBar = sh.querySelector('.wvp-bottom-bar');
                if ((topBar && topBar.matches(':hover')) || (bottomBar && bottomBar.matches(':hover'))) {
                    wvpScheduleHide();
                    return;
                }
                sh.classList.add('wvp-autohide');
            }, 3000);
        }

        function wvpSetVolume(v) {
            const player = document.getElementById('webVideoPlayer');
            if (!player) return;
            player.volume = Math.max(0, Math.min(1, Number(v)));
            player.muted = player.volume === 0;
            wvpSyncVolumeUI();
        }

        function wvpToggleMute() {
            const player = document.getElementById('webVideoPlayer');
            if (!player) return;
            player.muted = !player.muted;
            wvpSyncVolumeUI();
        }

        function wvpSyncVolumeUI() {
            const player = document.getElementById('webVideoPlayer');
            if (!player) return;
            const vol = document.getElementById('wvpVol');
            const icon = document.getElementById('wvpMuteBtn');
            const effective = player.muted ? 0 : (player.volume != null ? player.volume : 1);
            if (vol) vol.value = effective;
            if (icon) {
                let cls = 'bi-volume-up-fill';
                if (player.muted || effective === 0) cls = 'bi-volume-mute-fill';
                else if (effective < 0.4) cls = 'bi-volume-down-fill';
                icon.innerHTML = '<i class="bi ' + cls + '"></i>';
            }
        }

        function wvpCycleSpeed() {
            const speeds = [0.5, 0.75, 1, 1.25, 1.5, 2];
            const cur = window.webVideoSpeed || 1;
            const idx = speeds.indexOf(cur);
            const next = speeds[(idx + 1 + speeds.length) % speeds.length];
            setWebVideoSpeed(next);
        }

        function wvpToggleFullscreen() {
            const shell = document.getElementById('webVideoPlayerShell');
            if (!shell) return;
            const doc = document;
            const fsEl = doc.fullscreenElement || doc.webkitFullscreenElement;
            if (fsEl) {
                const exit = doc.exitFullscreen || doc.webkitExitFullscreen;
                if (exit) exit.call(doc);
            } else {
                const req = shell.requestFullscreen || shell.webkitRequestFullscreen;
                if (req) {
                    const res = req.call(shell);
                    if (res && res.catch) res.catch(function () { showWebVideoToast('Fullscreen tidak diizinkan.'); });
                } else {
                    showWebVideoToast('Fullscreen tidak didukung di browser ini.');
                }
            }
        }

        function setWebVideoSpeed(speed) {
            const player = document.getElementById('webVideoPlayer');
            if (!player) return;
            window.webVideoSpeed = speed;
            player.playbackRate = speed;
            const btn = document.getElementById('wvpSpeedBtn');
            if (btn) btn.innerText = speed + 'x';
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

        async function deleteWebVideo(id) {
            const item = window.webVideoList.find(function (v) { return v.id === id; });
            if (!item) return;
            if (item.origin === 'phone') {
                showWebVideoToast('Hapus video langsung dari galeri HP; pustaka web hanya menyimpan tautannya.');
                return;
            }
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
