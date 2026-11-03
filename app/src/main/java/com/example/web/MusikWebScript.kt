package com.example.web

object MusikWebScript {
    fun getScript(dol: String): String {
        return """
        // =========================================================================
        // --- PEMUTAR MUSIK AUDIO (100% OFFLINE, TAMPILAN & FUNGSI PERSIS ANDROID) ---
        // =========================================================================

        // --- GLOBAL MUSIC STATE ---
        window.musicPlaylist = window.musicPlaylist || [];
        window.currentMusicIndex = window.currentMusicIndex || 0;
        window.musicPlayerAudio = window.musicPlayerAudio || new Audio();
        window.isMusicShuffle = window.isMusicShuffle || false;
        window.musicRepeatMode = window.musicRepeatMode || 'NONE'; // 'NONE', 'ONE', 'ALL', 'AZ'
        window.isSeekingMusic = false;
        window.musicPlaybackPosition = 0;
        window.musicTotalDuration = 0;

        // Sumber berkas: 'phone' = penyimpanan HP (MediaStore), 'pc' = berkas yang diimpor browser.
        window.musicSourceFilter = window.musicSourceFilter || 'all'; // 'all' | 'phone' | 'pc'
        window.musicPhoneTracksLoaded = window.musicPhoneTracksLoaded || false;
        window.musicPhoneAutoTried = false;
        window.musicPhoneLoading = false;
        window.musicPhonePermission = true;
        window.musicPhoneError = '';
        window.musicFetchedLyrics = window.musicFetchedLyrics || {}; // { [trackId]: lrcText }
        window.musicDockTrackId = null;

        // Tabs & Navigation State (Identical to PemutarMusikScreen.kt)
        window.musicSelectedTab = window.musicSelectedTab || 0; // 0: Folder, 1: Favorit, 2: Daftar Putar
        window.musicSelectedSubTab = window.musicSelectedSubTab || 0; // 0: Baru Ditambahkan, 1: Paling Sering, 2: Baru Saja, 3: Daftar Putar Saya
        window.musicSelectedFolder = window.musicSelectedFolder || null;
        window.musicSearchQuery = window.musicSearchQuery || '';
        window.musicIsSearchExpanded = window.musicIsSearchExpanded || false;
        window.musicIsPlayerExpanded = window.musicIsPlayerExpanded || false;

        // Lyrics & Visual State
        window.musicIsLyricsMode = window.musicIsLyricsMode || false;
        window.musicLyricsEditMode = window.musicLyricsEditMode || false;
        window.musicIsLyricsFullScreen = window.musicIsLyricsFullScreen || false;
        window.musicManualLyrics = window.musicManualLyrics || {}; // { [trackIdOrTitle]: string }
        window.musicCustomAlbumArts = window.musicCustomAlbumArts || {}; // { [trackIdOrTitle]: dataUrl }
        window.musicFavorites = window.musicFavorites || []; // [trackIdOrTitle]
        window.musicMyPlaylist = window.musicMyPlaylist || []; // [trackIdOrTitle]
        window.musicPlayCounts = window.musicPlayCounts || {}; // { [trackIdOrTitle]: number }
        window.musicRecentlyPlayed = window.musicRecentlyPlayed || []; // [track objects]

        // --- INDEXEDDB & LOCALSTORAGE PERSISTENCE (100% OFFLINE) ---
        const MUSIC_DB_NAME = 'SinggharaMusicOfflineDB';
        const MUSIC_DB_VERSION = 1;
        let musicDbInstance = null;

        function openMusicDatabase() {
            return new Promise((resolve, reject) => {
                if (musicDbInstance) return resolve(musicDbInstance);
                const req = indexedDB.open(MUSIC_DB_NAME, MUSIC_DB_VERSION);
                req.onupgradeneeded = (e) => {
                    const db = e.target.result;
                    if (!db.objectStoreNames.contains('tracks')) {
                        db.createObjectStore('tracks', { keyPath: 'id' });
                    }
                    if (!db.objectStoreNames.contains('lyrics')) {
                        db.createObjectStore('lyrics', { keyPath: 'id' });
                    }
                    if (!db.objectStoreNames.contains('covers')) {
                        db.createObjectStore('covers', { keyPath: 'id' });
                    }
                };
                req.onsuccess = (e) => {
                    musicDbInstance = e.target.result;
                    resolve(musicDbInstance);
                };
                req.onerror = () => {
                    resolve(null); // Fallback gracefully if IndexedDB restricted
                };
            });
        }

        // Load persisted state on startup
        async function initMusicPersistence() {
            try {
                const favs = localStorage.getItem('singghara_music_favs');
                if (favs) window.musicFavorites = JSON.parse(favs);

                const myPl = localStorage.getItem('singghara_music_my_playlist');
                if (myPl) window.musicMyPlaylist = JSON.parse(myPl);

                const counts = localStorage.getItem('singghara_music_play_counts');
                if (counts) window.musicPlayCounts = JSON.parse(counts);

                const recents = localStorage.getItem('singghara_music_recently_played');
                if (recents) window.musicRecentlyPlayed = JSON.parse(recents);

                const lyrics = localStorage.getItem('singghara_music_manual_lyrics');
                if (lyrics) window.musicManualLyrics = JSON.parse(lyrics);

                const covers = localStorage.getItem('singghara_music_custom_covers');
                if (covers) window.musicCustomAlbumArts = JSON.parse(covers);

                const settings = localStorage.getItem('singghara_music_settings');
                if (settings) {
                    const s = JSON.parse(settings);
                    if (s.repeatMode) window.musicRepeatMode = s.repeatMode;
                    if (typeof s.shuffle === 'boolean') window.isMusicShuffle = s.shuffle;
                    if (typeof s.volume === 'number') window.musicPlayerAudio.volume = s.volume;
                }

                // Restore cached tracks from IndexedDB
                const db = await openMusicDatabase();
                if (db && window.musicPlaylist.length === 0) {
                    const tx = db.transaction('tracks', 'readonly');
                    const store = tx.objectStore('tracks');
                    const getAllReq = store.getAll();
                    getAllReq.onsuccess = () => {
                        const savedTracks = getAllReq.result || [];
                        if (savedTracks.length > 0) {
                            savedTracks.forEach(st => {
                                if (st.blob) {
                                    st.url = URL.createObjectURL(st.blob);
                                }
                                st.origin = 'pc';
                            });
                            window.musicPlaylist = savedTracks;
                            // `currentTab` adalah binding top-level (let) di dashboard, bukan properti
                            // window — memakainya lewat window selalu bernilai undefined.
                            if (typeof currentTab !== 'undefined' && currentTab === 'musik') {
                                loadMusik();
                            }
                        }
                    };
                }
            } catch (err) {
                console.warn('Failed to load music persistence:', err);
            }
        }
        initMusicPersistence();

        function saveMusicSettings() {
            try {
                localStorage.setItem('singghara_music_settings', JSON.stringify({
                    repeatMode: window.musicRepeatMode,
                    shuffle: window.isMusicShuffle,
                    volume: window.musicPlayerAudio.volume
                }));
                localStorage.setItem('singghara_music_favs', JSON.stringify(window.musicFavorites));
                localStorage.setItem('singghara_music_my_playlist', JSON.stringify(window.musicMyPlaylist));
                localStorage.setItem('singghara_music_play_counts', JSON.stringify(window.musicPlayCounts));
                localStorage.setItem('singghara_music_recently_played', JSON.stringify(window.musicRecentlyPlayed.slice(0, 50)));
                localStorage.setItem('singghara_music_manual_lyrics', JSON.stringify(window.musicManualLyrics));
                localStorage.setItem('singghara_music_custom_covers', JSON.stringify(window.musicCustomAlbumArts));
            } catch (e) {
                console.warn('Error saving music settings:', e);
            }
        }

        // --- AUDIO ENGINE LISTENERS ---
        if (!window.musicAudioEventsBound) {
            window.musicAudioEventsBound = true;

            window.musicPlayerAudio.addEventListener('timeupdate', () => {
                const cur = window.musicPlayerAudio.currentTime || 0;
                const dur = window.musicPlayerAudio.duration || 0;
                window.musicPlaybackPosition = cur;
                window.musicTotalDuration = dur;

                if (!window.isSeekingMusic) {
                    const sliders = document.querySelectorAll('.music-seek-slider');
                    sliders.forEach(el => {
                        if (dur > 0) {
                            el.max = dur;
                            el.value = cur;
                        }
                    });
                    const curTexts = document.querySelectorAll('.music-cur-time-text');
                    curTexts.forEach(el => el.innerText = formatAudioTime(cur));
                }

                // Update real-time lyrics scrolling and word highlight
                updateLyricsHighlight(cur);
                updateDockLyricLine(cur);
            });

            window.musicPlayerAudio.addEventListener('loadedmetadata', () => {
                const dur = window.musicPlayerAudio.duration || 0;
                window.musicTotalDuration = dur;
                const durTexts = document.querySelectorAll('.music-dur-time-text');
                durTexts.forEach(el => el.innerText = formatAudioTime(dur));
                const sliders = document.querySelectorAll('.music-seek-slider');
                sliders.forEach(el => {
                    el.max = dur;
                });
            });

            window.musicPlayerAudio.addEventListener('ended', () => {
                handleTrackEnded();
            });

            window.musicPlayerAudio.addEventListener('error', () => {
                const failed = window.musicPlaylist[window.currentMusicIndex];
                if (failed && failed.origin === 'phone') {
                    showCustomToast('Berkas HP tidak bisa diputar (mungkin sudah dipindah/dihapus).');
                }
                syncMusicDockState();
            });

            window.musicPlayerAudio.addEventListener('volumechange', () => {
                saveMusicSettings();
            });

            window.musicPlayerAudio.addEventListener('play', () => {
                updatePlayPauseStateInUI(true);
                syncMusicDockState();
            });

            window.musicPlayerAudio.addEventListener('pause', () => {
                updatePlayPauseStateInUI(false);
                syncMusicDockState();
            });
        }

        function formatAudioTime(secs) {
            if (isNaN(secs) || secs < 0) return '00:00';
            const m = Math.floor(secs / 60);
            const s = Math.floor(secs % 60);
            return String(m).padStart(2, '0') + ':' + String(s).padStart(2, '0');
        }

        function handleTrackEnded() {
            if (window.musicRepeatMode === 'ONE') {
                window.musicPlayerAudio.currentTime = 0;
                window.musicPlayerAudio.play().catch(e => {});
            } else if (window.musicRepeatMode === 'ALL') {
                nextMusicTrack(false);
            } else if (window.musicRepeatMode === 'AZ') {
                // A-Z Mode: Sort alphabetically and play next in that order
                playNextAlphabetical();
            } else {
                // NONE: if not at end, play next, otherwise stop
                if (window.currentMusicIndex < window.musicPlaylist.length - 1 || window.isMusicShuffle) {
                    nextMusicTrack(false);
                } else {
                    updatePlayPauseStateInUI(false);
                }
            }
        }

        function playNextAlphabetical() {
            if (window.musicPlaylist.length === 0) return;
            const sortedIndices = window.musicPlaylist
                .map((t, idx) => ({ idx, title: t.title.toLowerCase() }))
                .sort((a, b) => a.title.localeCompare(b.title));
            const currentSortedPos = sortedIndices.findIndex(item => item.idx === window.currentMusicIndex);
            const nextSortedPos = (currentSortedPos + 1) % sortedIndices.length;
            playTrackByIndex(sortedIndices[nextSortedPos].idx);
        }

        function updatePlayPauseStateInUI(isPlaying) {
            // Update big circular button
            const bigBtn = document.getElementById('musicBigPlayPauseBtn');
            if (bigBtn) {
                bigBtn.innerHTML = `<i class="bi ${dol}{isPlaying ? 'bi-pause-fill' : 'bi-play-fill'}" style="font-size: 38px; color: white;"></i>`;
            }
            // Update the persistent now-playing dock button
            const dockBtn = document.getElementById('musicDockPlayPauseBtn');
            if (dockBtn) {
                dockBtn.innerHTML = `<i class="bi ${dol}{isPlaying ? 'bi-pause-fill' : 'bi-play-fill'}" style="font-size: 26px;"></i>`;
            }
            // Update rotating disc animation
            const disc = document.getElementById('musicVinylArtDisc');
            if (disc) {
                if (isPlaying) disc.classList.add('spinning-disc');
                else disc.classList.remove('spinning-disc');
            }
            // Update status badges and list play icons
            const activeIcons = document.querySelectorAll('.music-track-active-icon');
            activeIcons.forEach(icon => {
                if (isPlaying) {
                    icon.innerHTML = '<i class="bi bi-volume-up-fill text-primary"></i>';
                } else {
                    icon.innerHTML = '<i class="bi bi-pause-circle text-primary"></i>';
                }
            });
        }

        // =========================================================================
        // --- SUMBER BERKAS HP (MediaStore) + MINI PLAYER PERSISTEN ---
        // =========================================================================

        function getVisibleMusicPlaylist() {
            const filter = window.musicSourceFilter || 'all';
            if (filter === 'all') return window.musicPlaylist;
            if (filter === 'phone') return window.musicPlaylist.filter(function (t) { return t.origin === 'phone'; });
            return window.musicPlaylist.filter(function (t) { return t.origin !== 'phone'; });
        }

        function setMusicSourceFilter(filter) {
            window.musicSourceFilter = filter;
            loadMusik();
        }

        function getTrackCover(track) {
            if (!track) return null;
            const arts = window.musicCustomAlbumArts || {};
            if (arts[track.id]) return arts[track.id];
            if (arts[track.title]) return arts[track.title];
            if (track.artUrl) return track.artUrl;
            return null;
        }

        function getTrackLyrics(track) {
            if (!track) return '';
            const manual = window.musicManualLyrics || {};
            if (manual[track.id]) return manual[track.id];
            if (manual[track.title]) return manual[track.title];
            if (window.musicFetchedLyrics[track.id]) return window.musicFetchedLyrics[track.id];
            if (window.musicFetchedLyrics[track.title]) return window.musicFetchedLyrics[track.title];
            return '';
        }

        // Berkas .lrc di samping lagu HP dimuat sekali per trek, tidak disimpan ke localStorage
        // karena id MediaStore sudah stabil lintas sesi.
        async function ensurePhoneLyricsLoaded(track) {
            if (!track || track.origin !== 'phone' || !track.lyricsUrl) return;
            if (window.musicFetchedLyrics[track.id] !== undefined) return;
            try {
                const res = await fetch(track.lyricsUrl, { cache: 'no-store' });
                window.musicFetchedLyrics[track.id] = res.ok ? await res.text() : '';
            } catch (err) {
                window.musicFetchedLyrics[track.id] = '';
            }
            const nowTrack = window.musicPlaylist[window.currentMusicIndex];
            if (nowTrack && nowTrack.id === track.id) refreshLyricsViews();
        }

        function refreshLyricsViews() {
            const card = document.getElementById('musicCentralArtCard');
            const currentTrack = window.musicPlaylist[window.currentMusicIndex];
            if (card && currentTrack && window.musicIsLyricsMode && !window.musicLyricsEditMode) {
                card.innerHTML = getCentralArtCardInnerHtml(currentTrack);
            }
            updateDockLyricLine(window.musicPlaybackPosition || 0);
        }

        async function loadPhoneMusicLibrary(showToast) {
            if (window.musicPhoneLoading) return;
            window.musicPhoneLoading = true;
            if (showToast) showCustomToast('Memindai musik yang ada di HP...');
            try {
                const res = await fetch('/api/phone_media/audio', { cache: 'no-store' });
                const data = await res.json();
                window.musicPhonePermission = data.hasPermission !== false;
                window.musicPhoneError = window.musicPhonePermission ? '' : 'Izin baca audio di HP belum diberikan.';
                const playingId = window.musicPlaylist[window.currentMusicIndex] ? window.musicPlaylist[window.currentMusicIndex].id : null;
                const phoneTracks = (data.items || []).map(function (item) {
                    return {
                        id: 'phone_audio_' + item.id,
                        mediaId: item.id,
                        origin: 'phone',
                        title: item.title || 'Audio',
                        artist: item.artist || 'Penyanyi Tidak Diketahui',
                        album: item.album || '',
                        folder: item.folder || 'Musik HP',
                        size: item.sizeLabel || '-',
                        sizeBytes: item.sizeBytes || 0,
                        durationMs: item.durationMs || 0,
                        url: item.url,
                        artUrl: item.artUrl,
                        lyricsUrl: item.lyricsUrl || '',
                        addedAt: (item.dateAddedSec || 0) * 1000
                    };
                });
                window.musicPlaylist = window.musicPlaylist
                    .filter(function (t) { return t.origin !== 'phone'; })
                    .concat(phoneTracks);
                window.musicPhoneTracksLoaded = true;
                if (playingId) {
                    const restoredIndex = window.musicPlaylist.findIndex(function (t) { return t.id === playingId; });
                    if (restoredIndex >= 0) window.currentMusicIndex = restoredIndex;
                }
                if (showToast) showCustomToast(phoneTracks.length + ' lagu dari HP berhasil dimuat.');
            } catch (err) {
                window.musicPhoneError = 'Layanan media HP tidak terjangkau: ' + err.message;
                if (showToast) showCustomToast('Gagal memuat musik dari HP.');
            } finally {
                window.musicPhoneLoading = false;
                if (typeof currentTab !== 'undefined' && currentTab === 'musik') loadMusik();
            }
        }

        function getMusicSourceChipsHtml() {
            const phoneCount = window.musicPlaylist.filter(function (t) { return t.origin === 'phone'; }).length;
            const pcCount = window.musicPlaylist.length - phoneCount;
            return '' +
                '<div class="d-flex align-items-center gap-2 mb-3 flex-wrap">' +
                '  <div class="music-source-chip ' + (window.musicSourceFilter === 'all' ? 'active' : '') + '" onclick="setMusicSourceFilter(\'all\')">Semua (' + window.musicPlaylist.length + ')</div>' +
                '  <div class="music-source-chip ' + (window.musicSourceFilter === 'phone' ? 'active' : '') + '" onclick="setMusicSourceFilter(\'phone\')"><i class="bi bi-phone me-1"></i>HP (' + phoneCount + ')</div>' +
                '  <div class="music-source-chip ' + (window.musicSourceFilter === 'pc' ? 'active' : '') + '" onclick="setMusicSourceFilter(\'pc\')"><i class="bi bi-pc-display me-1"></i>PC (' + pcCount + ')</div>' +
                '  <button class="btn btn-sm btn-outline-primary ms-auto" onclick="loadPhoneMusicLibrary(true)"><i class="bi bi-arrow-repeat me-1"></i>' + (phoneCount > 0 ? 'Pindai Ulang HP' : 'Muat Musik HP') + '</button>' +
                '</div>';
        }

        // --- DOCK "NOW PLAYING" DI LEVEL BODY (tetap tampil di semua halaman) ---

        function mountMusicDock() {
            const track = window.musicPlaylist[window.currentMusicIndex];
            let dock = document.getElementById('musicGlobalDock');
            if (!track || !window.musicDockTrackId) {
                if (dock) dock.remove();
                document.body.classList.remove('has-music-dock');
                return;
            }
            if (!dock) {
                dock = document.createElement('div');
                dock.id = 'musicGlobalDock';
                dock.className = 'music-global-dock';
                document.body.appendChild(dock);
            }
            if (dock.getAttribute('data-track-id') !== track.id) {
                dock.innerHTML = getGlobalDockHtml(track);
                dock.setAttribute('data-track-id', track.id);
            }
            dock.style.display = window.musicIsPlayerExpanded ? 'none' : '';
            document.body.classList.add('has-music-dock');
        }

        function syncMusicDockState() {
            const track = window.musicPlaylist[window.currentMusicIndex];
            if (!track || !window.musicDockTrackId) return;
            mountMusicDock();
            const isPlaying = !window.musicPlayerAudio.paused && !window.musicPlayerAudio.ended;

            const btn = document.getElementById('musicDockPlayPauseBtn');
            if (btn) btn.innerHTML = '<i class="bi ' + (isPlaying ? 'bi-pause-fill' : 'bi-play-fill') + '" style="font-size: 26px;"></i>';

            const titleEl = document.getElementById('dockTrackTitle');
            if (titleEl) titleEl.innerText = track.title;
            const artistEl = document.getElementById('dockTrackArtist');
            if (artistEl) artistEl.innerText = track.artist || track.folder || 'Musik';

            const coverBox = document.getElementById('dockCoverArt');
            if (coverBox) {
                const src = getTrackCover(track);
                coverBox.innerHTML = src
                    ? '<img src="' + src + '" style="width:100%;height:100%;object-fit:cover;">'
                    : '<i class="bi bi-music-note-beamed text-primary fs-4"></i>';
            }

            const favBtn = document.getElementById('dockFavoriteBtn');
            if (favBtn) {
                const isFav = isTrackFavorite(track);
                favBtn.innerHTML = '<i class="bi ' + (isFav ? 'bi-heart-fill text-danger' : 'bi-heart text-secondary') + '" style="font-size: 19px;"></i>';
            }

            const volume = document.getElementById('dockVolumeSlider');
            if (volume) volume.value = window.musicPlayerAudio.volume;

            const slider = document.getElementById('dockSeekSlider');
            if (slider && !window.isSeekingMusic) {
                slider.max = window.musicTotalDuration || 0;
                slider.value = window.musicPlaybackPosition || 0;
            }

            updateRepeatButtonUI();
            updateShuffleButtonUI();
            updateDockLyricLine(window.musicPlaybackPosition || 0);
            updateMediaSession(track);
        }

        function getGlobalDockHtml(track) {
            const isPlaying = window.musicPlayerAudio && !window.musicPlayerAudio.paused && !window.musicPlayerAudio.ended;
            const src = getTrackCover(track);
            const dur = window.musicTotalDuration || 0;
            const pos = window.musicPlaybackPosition || 0;
            const originBadge = track.origin === 'phone'
                ? '<span class="badge text-bg-primary ms-1" style="font-size:9px">HP</span>'
                : '<span class="badge text-bg-secondary ms-1" style="font-size:9px">PC</span>';

            let html = '';
            html += '<style>' + getMusicDockCss() + '</style>';
            html += '<div class="music-dock-card">';
            html += '  <div class="music-dock-progress-row">';
            html += '    <span class="music-cur-time-text music-dock-time">' + formatAudioTime(pos) + '</span>';
            html += '    <input type="range" class="music-dock-slider music-seek-slider" id="dockSeekSlider" min="0" max="' + dur + '" value="' + pos + '" oninput="handleMusicSeekInput(this.value)" onchange="handleMusicSeekChange(this.value)">';
            html += '    <span class="music-dur-time-text music-dock-time">' + formatAudioTime(dur) + '</span>';
            html += '  </div>';
            html += '  <div class="music-dock-main-row">';
            html += '    <div class="music-dock-cover" id="dockCoverArt" onclick="expandMusicPlayerDeck()" title="Buka pemutar penuh">' +
                (src ? '<img src="' + src + '" style="width:100%;height:100%;object-fit:cover;">' : '<i class="bi bi-music-note-beamed text-primary fs-4"></i>') + '</div>';
            html += '    <div class="music-dock-meta" onclick="expandMusicPlayerDeck()" title="Buka pemutar penuh">';
            html += '      <div class="music-dock-title text-truncate" id="dockTrackTitle">' + escapeHtml(track.title) + originBadge + '</div>';
            html += '      <div class="music-dock-artist text-truncate" id="dockTrackArtist">' + escapeHtml(track.artist || track.folder || 'Musik') + '</div>';
            html += '      <div class="music-dock-lyric text-truncate" id="dockLyricLine"></div>';
            html += '    </div>';
            html += '    <div class="music-dock-controls">';
            html += '      <button class="music-dock-btn music-shuffle-btn' + (window.isMusicShuffle ? ' is-active' : '') + '" onclick="toggleShuffleMode()" title="Acak"><i class="bi bi-shuffle"></i></button>';
            html += '      <button class="music-dock-btn" onclick="prevMusicTrack()" title="Sebelumnya"><i class="bi bi-skip-start-fill"></i></button>';
            html += '      <button class="music-dock-play-btn" id="musicDockPlayPauseBtn" onclick="togglePlayMusic()" title="Putar / Jeda"><i class="bi ' + (isPlaying ? 'bi-pause-fill' : 'bi-play-fill') + '" style="font-size:26px"></i></button>';
            html += '      <button class="music-dock-btn" onclick="nextMusicTrack()" title="Selanjutnya"><i class="bi bi-skip-end-fill"></i></button>';
            html += '      <button class="music-dock-btn music-repeat-btn" onclick="cycleRepeatMode()" title="Pengulangan"><i class="bi bi-repeat"></i></button>';
            html += '      <button class="music-dock-btn" id="dockFavoriteBtn" onclick="toggleTrackFavorite(\'' + track.id + '\', event)" title="Favorit"><i class="bi bi-heart text-secondary"></i></button>';
            html += '      <button class="music-dock-btn" onclick="openMusicDeckWithLyrics()" title="Lirik"><i class="bi bi-text-center"></i></button>';
            html += '      <button class="music-dock-btn" onclick="openMusicQueueModal()" title="Antrean Lagu"><i class="bi bi-music-note-list"></i></button>';
            html += '      <button class="music-dock-btn music-dock-optional" onclick="downloadMusicTrack(\'' + track.id + '\')" title="Unduh ke PC"><i class="bi bi-download"></i></button>';
            html += '      <input type="range" id="dockVolumeSlider" class="music-dock-volume" min="0" max="1" step="0.01" value="' + window.musicPlayerAudio.volume + '" oninput="setMusicVolume(this.value)" title="Volume">';
            html += '      <button class="music-dock-btn music-dock-optional" onclick="expandMusicPlayerDeck()" title="Perbesar"><i class="bi bi-arrows-angle-expand"></i></button>';
            html += '      <button class="music-dock-btn text-danger" onclick="stopMusicPlayback()" title="Hentikan Putar"><i class="bi bi-x-lg"></i></button>';
            html += '    </div>';
            html += '  </div>';
            html += '</div>';
            return html;
        }

        function getMusicDockCss() {
            return '' +
                '.music-global-dock{position:fixed;left:var(--sidebar-width,0px);right:0;bottom:0;z-index:1050;padding:0 16px 12px;pointer-events:none;transition:left .3s ease;}' +
                '.music-global-dock .music-dock-card{pointer-events:auto;background:var(--bs-body-bg,#fff);border:1px solid rgba(13,110,253,.28);border-radius:16px;box-shadow:0 -6px 24px rgba(0,0,0,.18);padding:8px 12px;}' +
                'body.has-music-dock .content-wrapper{padding-bottom:132px;}' +
                '.music-dock-progress-row{display:flex;align-items:center;gap:10px;margin-bottom:2px;}' +
                '.music-dock-time{font-size:10.5px;color:var(--bs-secondary);min-width:38px;text-align:center;}' +
                '.music-dock-slider{flex:1;height:4px;accent-color:var(--bs-primary,#0d6efd);}' +
                '.music-dock-main-row{display:flex;align-items:center;gap:12px;}' +
                '.music-dock-cover{width:46px;height:46px;min-width:46px;border-radius:10px;overflow:hidden;background:linear-gradient(135deg,#1f2937,#111827);display:flex;align-items:center;justify-content:center;cursor:pointer;}' +
                '.music-dock-meta{flex:1;min-width:0;cursor:pointer;}' +
                '.music-dock-title{font-size:13px;font-weight:700;}' +
                '.music-dock-artist{font-size:11px;color:var(--bs-secondary);}' +
                '.music-dock-lyric{font-size:11px;color:var(--bs-primary);font-weight:600;min-height:14px;}' +
                '.music-dock-controls{display:flex;align-items:center;gap:2px;flex-shrink:0;}' +
                '.music-dock-btn{position:relative;background:transparent;border:0;color:var(--bs-secondary);width:30px;height:30px;border-radius:8px;display:flex;align-items:center;justify-content:center;font-size:16px;cursor:pointer;}' +
                '.music-dock-btn:hover{background:rgba(127,127,127,.14);}' +
                '.music-dock-btn.is-active{color:var(--bs-primary);}' +
                '.music-repeat-badge{position:absolute;top:-1px;right:0;font-size:8px;font-weight:800;line-height:1;}' +
                '.music-dock-play-btn{background:var(--bs-primary,#0d6efd);border:0;color:#fff;width:42px;height:42px;border-radius:50%;display:flex;align-items:center;justify-content:center;cursor:pointer;}' +
                '.music-dock-play-btn:hover{filter:brightness(1.08);}' +
                '.music-dock-volume{width:70px;height:4px;accent-color:var(--bs-primary,#0d6efd);}' +
                '@media (max-width:1100px){.music-global-dock{left:0;}}' +
                '@media (max-width:900px){.music-dock-volume{display:none;}.music-dock-lyric{display:none;}.music-dock-optional{display:none;}}';
        }

        function updateDockLyricLine(currentSec) {
            const el = document.getElementById('dockLyricLine');
            if (!el) return;
            const track = window.musicPlaylist[window.currentMusicIndex];
            if (!track) { el.innerText = ''; return; }
            const lrcText = getTrackLyrics(track);
            let lines;
            if (window._dockLrcTrack === track.id && window._dockLrcSource === lrcText) {
                lines = window._dockLrcLines;
            } else {
                lines = lrcText ? parseLrcLyrics(lrcText) : [];
                window._dockLrcTrack = track.id;
                window._dockLrcSource = lrcText;
                window._dockLrcLines = lines;
            }
            if (!lines || lines.length === 0) {
                el.innerText = track.origin === 'phone' ? (track.folder || 'Berkas HP') : (track.folder || 'Berkas PC');
                return;
            }
            let active = lines[0].text;
            for (let i = 0; i < lines.length; i++) {
                if (lines[i].timeSec <= currentSec) active = lines[i].text; else break;
            }
            el.innerText = active;
        }

        function stopMusicPlayback() {
            window.musicPlayerAudio.pause();
            window.musicPlayerAudio.removeAttribute('src');
            window.musicPlayerAudio.load();
            window.musicDockTrackId = null;
            window.musicIsPlayerExpanded = false;
            const dock = document.getElementById('musicGlobalDock');
            if (dock) dock.remove();
            document.body.classList.remove('has-music-dock');
            const deck = document.getElementById('musicExpandedPlayerDeck');
            if (deck) deck.style.display = 'none';
            if ('mediaSession' in navigator) {
                try { navigator.mediaSession.metadata = null; } catch (err) {}
            }
            if (typeof currentTab !== 'undefined' && currentTab === 'musik') loadMusik();
        }

        function downloadMusicTrack(trackId) {
            const track = window.musicPlaylist.find(function (t) { return t.id === trackId; });
            if (!track || !track.url) return;
            const link = document.createElement('a');
            link.href = track.url;
            link.download = track.title || 'lagu';
            document.body.appendChild(link);
            link.click();
            document.body.removeChild(link);
            showCustomToast('Mengunduh ' + (track.title || 'lagu'));
        }

        function setMusicVolume(val) {
            const volume = Math.max(0, Math.min(1, parseFloat(val) || 0));
            window.musicPlayerAudio.volume = volume;
            saveMusicSettings();
        }

        function openMusicDeckWithLyrics() {
            window.musicIsLyricsMode = true;
            window.musicLyricsEditMode = false;
            expandMusicPlayerDeck();
        }

        // Tombol media sistem (keyboard/OS) memakai Media Session API bila tersedia.
        function setupMusicMediaSession() {
            if (!('mediaSession' in navigator)) return;
            try {
                navigator.mediaSession.setActionHandler('play', function () { window.musicPlayerAudio.play().catch(function () {}); });
                navigator.mediaSession.setActionHandler('pause', function () { window.musicPlayerAudio.pause(); });
                navigator.mediaSession.setActionHandler('previoustrack', function () { prevMusicTrack(); });
                navigator.mediaSession.setActionHandler('nexttrack', function () { nextMusicTrack(false); });
                navigator.mediaSession.setActionHandler('seekto', function (details) {
                    if (details && typeof details.seekTime === 'number') window.musicPlayerAudio.currentTime = details.seekTime;
                });
            } catch (err) {}
        }

        function updateMediaSession(track) {
            if (!('mediaSession' in navigator)) return;
            try {
                const cover = getTrackCover(track);
                navigator.mediaSession.metadata = new MediaMetadata({
                    title: track.title || 'Tanpa Judul',
                    artist: track.artist || track.folder || 'My Singghara',
                    album: track.album || track.folder || 'My Singghara',
                    artwork: cover ? [{ src: cover, sizes: '512x512', type: 'image/jpeg' }] : []
                });
                const duration = window.musicPlayerAudio.duration;
                if (isFinite(duration) && duration > 0) {
                    navigator.mediaSession.setPositionState({
                        duration: duration,
                        position: Math.min(window.musicPlayerAudio.currentTime || 0, duration),
                        playbackRate: window.musicPlayerAudio.playbackRate || 1
                    });
                }
            } catch (err) {}
        }

        // --- PARSING & LYRICS ENGINE (LRC COMPLIANT) ---
        function parseLrcLyrics(lrcText) {
            if (!lrcText || !lrcText.trim()) return [];
            const lines = lrcText.split(/\r?\n/);
            const result = [];
            const timeReg = /\[(\d{1,2}):(\d{1,2})(?:\.(\d{1,3}))?\]/g;

            lines.forEach(line => {
                const trimmed = line.trim();
                if (!trimmed) return;
                let match;
                let text = trimmed.replace(timeReg, '').trim();
                // Find all timestamps on this line
                timeReg.lastIndex = 0;
                while ((match = timeReg.exec(trimmed)) !== null) {
                    const min = parseInt(match[1], 10);
                    const sec = parseInt(match[2], 10);
                    const msStr = match[3] || '0';
                    const ms = parseInt(msStr.padEnd(3, '0').slice(0, 3), 10);
                    const timeMs = min * 60000 + sec * 1000 + ms;
                    result.push({ timeMs, timeSec: timeMs / 1000, text: text || '...' });
                }
            });

            // If no timestamps found, treat as plain text lines evenly spaced
            if (result.length === 0 && lines.length > 0) {
                const estDur = window.musicTotalDuration || 180;
                const slice = estDur / Math.max(lines.length, 1);
                lines.forEach((l, idx) => {
                    const t = l.trim();
                    if (t) {
                        result.push({ timeMs: idx * slice * 1000, timeSec: idx * slice, text: t });
                    }
                });
            }

            result.sort((a, b) => a.timeMs - b.timeMs);
            return result;
        }

        function updateLyricsHighlight(currentSec) {
            const container = document.getElementById('musicLyricsScrollContainer');
            if (!container) return;
            const currentTrack = window.musicPlaylist[window.currentMusicIndex];
            if (!currentTrack) return;

            const lrcText = getTrackLyrics(currentTrack);
            if (!lrcText) return;

            const lines = parseLrcLyrics(lrcText);
            if (lines.length === 0) return;

            // Find current active line
            let activeIdx = -1;
            for (let i = 0; i < lines.length; i++) {
                if (lines[i].timeSec <= currentSec) {
                    activeIdx = i;
                } else {
                    break;
                }
            }
            if (activeIdx === -1 && lines.length > 0) activeIdx = 0;

            const lineEls = container.querySelectorAll('.lrc-line-item');
            lineEls.forEach((el, idx) => {
                if (idx === activeIdx) {
                    if (!el.classList.contains('active-lrc-line')) {
                        el.classList.add('active-lrc-line');
                        el.scrollIntoView({ behavior: 'smooth', block: 'center' });
                    }
                } else {
                    el.classList.remove('active-lrc-line');
                }
            });
        }

        // --- TRACK FAVORITES & PLAYLISTS MANAGEMENT ---
        function isTrackFavorite(track) {
            if (!track) return false;
            return window.musicFavorites.includes(track.id) || window.musicFavorites.includes(track.title);
        }

        function toggleTrackFavorite(trackId, event) {
            if (event) event.stopPropagation();
            const track = window.musicPlaylist.find(t => t.id === trackId);
            if (!track) return;

            const key = track.id;
            const idx = window.musicFavorites.indexOf(key);
            if (idx !== -1) {
                window.musicFavorites.splice(idx, 1);
                showCustomToast('Dihapus dari Favorit');
            } else {
                window.musicFavorites.push(key);
                showCustomToast('Ditambahkan ke Favorit');
            }
            saveMusicSettings();
            renderActiveMusicTabContent();
            updateDeckActionIcons();
        }

        function isTrackInMyPlaylist(track) {
            if (!track) return false;
            return window.musicMyPlaylist.includes(track.id) || window.musicMyPlaylist.includes(track.title);
        }

        function toggleTrackMyPlaylist(trackId, event) {
            if (event) event.stopPropagation();
            const track = window.musicPlaylist.find(t => t.id === trackId);
            if (!track) return;

            const key = track.id;
            const idx = window.musicMyPlaylist.indexOf(key);
            if (idx !== -1) {
                window.musicMyPlaylist.splice(idx, 1);
                showCustomToast('Dihapus dari Daftar Putar Saya');
            } else {
                window.musicMyPlaylist.push(key);
                showCustomToast('Ditambahkan ke Daftar Putar Saya');
            }
            saveMusicSettings();
            renderActiveMusicTabContent();
            updateDeckActionIcons();
        }

        function updateDeckActionIcons() {
            const currentTrack = window.musicPlaylist[window.currentMusicIndex];
            if (!currentTrack) return;

            const isFav = isTrackFavorite(currentTrack);
            const favBtn = document.getElementById('deckFavoriteBtn');
            if (favBtn) {
                favBtn.innerHTML = `<i class="bi ${dol}{isFav ? 'bi-heart-fill text-danger' : 'bi-heart'}" style="font-size: 26px;"></i>`;
            }

            const inPl = isTrackInMyPlaylist(currentTrack);
            const plBtn = document.getElementById('deckPlaylistBtn');
            if (plBtn) {
                plBtn.innerHTML = `<i class="bi ${dol}{inPl ? 'bi-check-lg text-success' : 'bi-plus-lg'}" style="font-size: 26px;"></i>`;
            }
        }

        // --- PLAYBACK CONTROLS ---
        function playTrackByIndex(idx) {
            if (idx < 0 || idx >= window.musicPlaylist.length) return;
            window.currentMusicIndex = idx;
            const track = window.musicPlaylist[idx];
            if (!track) return;

            // Track play counts & recently played
            window.musicPlayCounts[track.id] = (window.musicPlayCounts[track.id] || 0) + 1;
            window.musicRecentlyPlayed = [track, ...window.musicRecentlyPlayed.filter(t => t.id !== track.id)].slice(0, 50);
            saveMusicSettings();

            if (track.url) {
                window.musicPlayerAudio.src = track.url;
                window.musicPlayerAudio.play().then(() => {
                    updatePlayPauseStateInUI(true);
                }).catch(e => {
                    console.warn('Playback autoplay note:', e);
                });
            }

            window.musicDockTrackId = track.id;
            setupMusicMediaSession();
            ensurePhoneLyricsLoaded(track);
            refreshLyricsViews();

            renderActiveMusicTabContent();
            updateExpandedDeckTrackInfo();
            updateDeckActionIcons();
            syncMusicDockState();
            updateMediaSession();
        }

        function togglePlayMusic() {
            if (window.musicPlaylist.length === 0) {
                showCustomToast('Pilih atau tambahkan lagu terlebih dahulu');
                return;
            }
            if (window.musicPlayerAudio.paused) {
                if (!window.musicPlayerAudio.src && window.musicPlaylist[window.currentMusicIndex]) {
                    playTrackByIndex(window.currentMusicIndex);
                } else {
                    window.musicPlayerAudio.play().catch(e => {});
                }
            } else {
                window.musicPlayerAudio.pause();
            }
        }

        function prevMusicTrack() {
            if (window.musicPlaylist.length === 0) return;
            // If already played more than 3 seconds, restart current track like modern players
            if (window.musicPlayerAudio.currentTime > 3) {
                window.musicPlayerAudio.currentTime = 0;
                return;
            }
            let prevIdx = window.currentMusicIndex - 1;
            if (prevIdx < 0) prevIdx = window.musicPlaylist.length - 1;
            playTrackByIndex(prevIdx);
        }

        function nextMusicTrack(manualClick = true) {
            if (window.musicPlaylist.length === 0) return;
            let nextIdx;
            if (window.isMusicShuffle && window.musicPlaylist.length > 1) {
                do {
                    nextIdx = Math.floor(Math.random() * window.musicPlaylist.length);
                } while (nextIdx === window.currentMusicIndex);
            } else {
                nextIdx = (window.currentMusicIndex + 1) % window.musicPlaylist.length;
            }
            playTrackByIndex(nextIdx);
        }

        function cycleRepeatMode() {
            // Cycle: NONE -> ONE -> ALL -> AZ -> NONE
            if (window.musicRepeatMode === 'NONE') window.musicRepeatMode = 'ONE';
            else if (window.musicRepeatMode === 'ONE') window.musicRepeatMode = 'ALL';
            else if (window.musicRepeatMode === 'ALL') window.musicRepeatMode = 'AZ';
            else window.musicRepeatMode = 'NONE';

            saveMusicSettings();
            updateRepeatButtonUI();
            const labels = { 'NONE': 'Pengulangan: Mati', 'ONE': 'Ulangi 1 Lagu', 'ALL': 'Ulangi Semua Lagu', 'AZ': 'Urutan Abjad A-Z' };
            showCustomToast(labels[window.musicRepeatMode]);
        }

        function updateRepeatButtonUI() {
            const mode = window.musicRepeatMode;
            const isActive = mode !== 'NONE';
            let badgeText = '';
            if (mode === 'ONE') badgeText = '1';
            else if (mode === 'ALL') badgeText = 'A';
            else if (mode === 'AZ') badgeText = 'A-Z';

            // Tombol pengulangan ada dua: di dock mini player dan di deck penuh.
            document.querySelectorAll('.music-repeat-btn').forEach(function (btn) {
                const isDock = btn.classList.contains('music-dock-btn');
                const iconSize = isDock ? 16 : 24;
                const badge = badgeText
                    ? '<span style="position:absolute;top:' + (isDock ? '-1' : '2') + 'px;right:' + (isDock ? '0' : '4') + 'px;font-size:' + (mode === 'AZ' ? 7 : 9) + 'px;font-weight:800;line-height:1;">' + badgeText + '</span>'
                    : '';
                btn.innerHTML = '<i class="bi bi-repeat ' + (isActive ? 'text-primary' : 'text-secondary') + '" style="font-size:' + iconSize + 'px;"></i>' + badge;
            });
        }

        function updateShuffleButtonUI() {
            document.querySelectorAll('.music-shuffle-btn').forEach(function (btn) {
                if (btn.classList.contains('music-dock-btn')) {
                    btn.classList.toggle('is-active', window.isMusicShuffle);
                } else {
                    btn.classList.toggle('text-primary', window.isMusicShuffle);
                    btn.classList.toggle('text-secondary', !window.isMusicShuffle);
                }
            });
        }

        function toggleShuffleMode() {
            window.isMusicShuffle = !window.isMusicShuffle;
            saveMusicSettings();
            updateShuffleButtonUI();
            showCustomToast(window.isMusicShuffle ? 'Mode Acak (Shuffle) Aktif' : 'Mode Acak Dinonaktifkan');
        }

        function handleMusicSeekChange(val) {
            window.isSeekingMusic = false;
            window.musicPlayerAudio.currentTime = parseFloat(val);
        }

        function handleMusicSeekInput(val) {
            window.isSeekingMusic = true;
            const curTexts = document.querySelectorAll('.music-cur-time-text');
            curTexts.forEach(el => el.innerText = formatAudioTime(parseFloat(val)));
        }

        // --- FILE & FOLDER IMPORT (100% OFFLINE) ---
        async function handleMusicFilesImport(event) {
            const files = event.target.files;
            if (!files || files.length === 0) return;

            const db = await openMusicDatabase();
            const newTracks = [];
            const startIndex = window.musicPlaylist.length;

            for (let i = 0; i < files.length; i++) {
                const file = files[i];
                const url = URL.createObjectURL(file);
                const sizeMb = (file.size / (1024 * 1024)).toFixed(1) + ' MB';
                const folderName = file.webkitRelativePath ? file.webkitRelativePath.split('/')[0] : 'Folder Musik';
                const cleanTitle = file.name.replace(/\.[^/.]+${dol}/, "");
                const trackId = 'trk_' + Date.now() + '_' + i + '_' + Math.random().toString(36).substring(2, 6);

                const trackObj = {
                    id: trackId,
                    title: cleanTitle,
                    artist: folderName,
                    folder: folderName,
                    size: sizeMb,
                    url: url,
                    blob: file,
                    addedAt: Date.now()
                };

                newTracks.push(trackObj);
                window.musicPlaylist.push(trackObj);

                // Persist to IndexedDB
                if (db) {
                    try {
                        const tx = db.transaction('tracks', 'readwrite');
                        tx.objectStore('tracks').put(trackObj);
                    } catch (e) {
                        console.warn('IDB write failed for track:', e);
                    }
                }
            }

            event.target.value = '';
            showCustomToast(newTracks.length + ' file musik berhasil ditambahkan!');

            if (startIndex === 0 && window.musicPlaylist.length > 0) {
                playTrackByIndex(0);
            }
            loadMusik();
        }

        // Generate procedural offline lo-fi demo track using Web Audio API
        function generateOfflineDemoTrack() {
            try {
                const AudioContextClass = window.AudioContext || window.webkitAudioContext;
                const ctx = new AudioContextClass();
                const sampleRate = ctx.sampleRate;
                const duration = 24; // 24 seconds loop
                const numFrames = sampleRate * duration;
                const buffer = ctx.createBuffer(2, numFrames, sampleRate);
                const left = buffer.getChannelData(0);
                const right = buffer.getChannelData(1);

                // Chord progression: Cmaj7 - Am7 - Dm7 - G7
                const chords = [
                    [261.63, 329.63, 392.00, 493.88], // Cmaj7
                    [220.00, 261.63, 329.63, 392.00], // Am7
                    [146.83, 174.61, 220.00, 261.63], // Dm7
                    [196.00, 246.94, 293.66, 349.23]  // G7
                ];

                for (let i = 0; i < numFrames; i++) {
                    const t = i / sampleRate;
                    const chordIdx = Math.floor((t / 6) % 4);
                    const chord = chords[chordIdx];
                    let sample = 0;

                    chord.forEach((freq, fi) => {
                        const env = Math.exp(-((t % 1.5) * 1.8));
                        sample += Math.sin(2 * Math.PI * freq * t) * 0.12 * env;
                        // Sub bass
                        if (fi === 0) {
                            sample += Math.sin(2 * Math.PI * (freq / 2) * t) * 0.2 * Math.exp(-((t % 3) * 1.2));
                        }
                    });

                    // Soft ambient vinyl noise
                    sample += (Math.random() * 2 - 1) * 0.004;

                    left[i] = sample * 0.8;
                    right[i] = sample * 0.8;
                }

                // Convert AudioBuffer to WAV Blob
                const wavBlob = audioBufferToWavBlob(buffer);
                const url = URL.createObjectURL(wavBlob);
                const demoTrack = {
                    id: 'demo_lofi_singghara',
                    title: 'My Singghara Lo-Fi Chill (Offline Demo)',
                    artist: 'My Singghara Audio',
                    folder: 'Demo Musik',
                    size: '1.8 MB',
                    url: url,
                    blob: wavBlob,
                    addedAt: Date.now()
                };

                // Add sample LRC lyrics
                window.musicManualLyrics[demoTrack.id] = 
                    "[ti:My Singghara Lo-Fi Chill]\n" +
                    "[ar:My Singghara Audio]\n" +
                    "[00:00.00] ~ Intro Lo-Fi Instrumental ~\n" +
                    "[00:03.00] Selamat datang di Pemutar Musik My Singghara\n" +
                    "[00:06.00] Musik santai untuk menemani aktivitas Anda\n" +
                    "[00:10.00] Semua fitur dapat diakses 100% offline\n" +
                    "[00:14.00] Putar lagu favorit, folder musik, dan lirik lagu\n" +
                    "[00:18.00] Ketuk album art untuk melihat mode lirik\n" +
                    "[00:22.00] ~ Melodi Cmaj7 - Am7 - Dm7 - G7 ~";

                window.musicPlaylist.unshift(demoTrack);
                saveMusicSettings();
                showCustomToast('Demo musik offline berhasil dimuat!');
                playTrackByIndex(0);
                loadMusik();
            } catch (e) {
                console.error('Failed to generate demo track:', e);
                showCustomToast('Gagal membuat demo track: ' + e.message);
            }
        }

        function audioBufferToWavBlob(buffer) {
            const numOfChan = buffer.numberOfChannels;
            const length = buffer.length * numOfChan * 2 + 44;
            const outBuffer = new ArrayBuffer(length);
            const view = new DataView(outBuffer);
            const channels = [];
            let sample = 0;
            let offset = 0;
            let pos = 0;

            function setUint16(data) { view.setUint16(pos, data, true); pos += 2; }
            function setUint32(data) { view.setUint32(pos, data, true); pos += 4; }

            // RIFF identifier
            setUint32(0x46464952);
            setUint32(length - 8);
            // WAVE identifier
            setUint32(0x45564157);
            // fmt sub-chunk
            setUint32(0x20746d66);
            setUint32(16);
            setUint16(1); // PCM
            setUint16(numOfChan);
            setUint32(buffer.sampleRate);
            setUint32(buffer.sampleRate * 2 * numOfChan);
            setUint16(numOfChan * 2);
            setUint16(16);
            // data sub-chunk
            setUint32(0x61746164);
            setUint32(length - pos - 4);

            for (let i = 0; i < buffer.numberOfChannels; i++) {
                channels.push(buffer.getChannelData(i));
            }

            while (offset < buffer.length) {
                for (let i = 0; i < numOfChan; i++) {
                    sample = Math.max(-1, Math.min(1, channels[i][offset]));
                    sample = (0.5 + sample < 0 ? sample * 32768 : sample * 32767) | 0;
                    view.setInt16(pos, sample, true);
                    pos += 2;
                }
                offset++;
            }
            return new Blob([outBuffer], { type: 'audio/wav' });
        }

        // --- MAIN RENDER FUNCTION: loadMusik() ---
        function loadMusik() {
            let html = `
                <!-- STYLES TO ENSURE 100% IDENTICAL LOOK TO JETPACK COMPOSE -->
                <style>
                    .music-tab-link {
                        cursor: pointer;
                        padding: 8px 0;
                        font-size: 14px;
                        transition: all 0.2s ease;
                        user-select: none;
                        display: inline-block;
                        text-decoration: none;
                    }
                    .music-tab-link.active {
                        font-weight: 700;
                        color: var(--bs-primary) !important;
                        text-decoration: underline !important;
                        text-underline-offset: 4px;
                    }
                    .music-tab-link:not(.active) {
                        color: var(--bs-secondary);
                        font-weight: 500;
                    }
                    .music-subtab-chip {
                        cursor: pointer;
                        border-radius: 16px;
                        padding: 7px 12px;
                        font-size: 11px;
                        font-weight: 700;
                        text-align: center;
                        transition: all 0.15s ease;
                        flex: 1;
                        user-select: none;
                        border: 1px solid transparent;
                    }
                    .music-subtab-chip.active {
                        background: var(--bs-primary-bg-subtle, rgba(13, 110, 253, 0.18));
                        color: var(--bs-primary);
                        border-color: rgba(13, 110, 253, 0.3);
                    }
                    .music-subtab-chip:not(.active) {
                        background: rgba(127, 127, 127, 0.08);
                        color: var(--bs-secondary);
                    }
                    .music-subtab-chip:hover {
                        filter: brightness(1.1);
                    }
                    .music-source-chip {
                        cursor: pointer;
                        border-radius: 20px;
                        padding: 6px 14px;
                        font-size: 12px;
                        font-weight: 700;
                        user-select: none;
                        border: 1px solid rgba(127, 127, 127, 0.25);
                        background: rgba(127, 127, 127, 0.08);
                        color: var(--bs-secondary);
                        transition: all 0.15s ease;
                    }
                    .music-source-chip.active {
                        background: var(--bs-primary);
                        border-color: var(--bs-primary);
                        color: #fff;
                    }
                    .music-source-chip:hover {
                        filter: brightness(1.08);
                    }
                    .music-card-item {
                        border-radius: 12px;
                        padding: 12px 14px;
                        background: rgba(127, 127, 127, 0.06);
                        border: 1px solid rgba(127, 127, 127, 0.15);
                        display: flex;
                        align-items: center;
                        justify-content: space-between;
                        cursor: pointer;
                        transition: background 0.15s ease, border-color 0.15s ease;
                    }
                    .music-card-item:hover {
                        background: rgba(127, 127, 127, 0.12);
                    }
                    .music-card-item.active-playing {
                        background: rgba(13, 110, 253, 0.12);
                        border-color: rgba(13, 110, 253, 0.35);
                    }
                    .spinning-disc {
                        animation: spinDisc 12s linear infinite;
                    }
                    @keyframes spinDisc {
                        from { transform: rotate(0deg); }
                        to { transform: rotate(360deg); }
                    }
                    .music-mini-dock {
                        position: sticky;
                        bottom: 16px;
                        left: 0;
                        right: 0;
                        margin-top: 24px;
                        z-index: 100;
                    }
                    .lrc-line-item {
                        padding: 8px 16px;
                        text-align: center;
                        font-size: 13px;
                        color: rgba(255, 255, 255, 0.6);
                        transition: all 0.25s ease;
                        cursor: pointer;
                        border-radius: 8px;
                    }
                    .lrc-line-item.active-lrc-line {
                        font-size: 16px;
                        font-weight: 800;
                        color: var(--bs-primary) !important;
                        transform: scale(1.1);
                        background: rgba(13, 110, 253, 0.12);
                    }
                    .music-marquee {
                        white-space: nowrap;
                        overflow: hidden;
                        text-overflow: ellipsis;
                    }
                    .music-marquee:hover {
                        overflow: visible;
                    }
                </style>

                <!-- TOP BAR WITH TABS, SEARCH & SETTINGS -->
                <div class="d-flex align-items-center justify-content-between mb-3 py-1 gap-2">
                    <div class="d-flex align-items-center flex-grow-1" id="musicHeaderBarContainer">
                        ${dol}{getMusicHeaderTabsHtml()}
                    </div>
                    <div class="d-flex align-items-center gap-1">
                        <button class="btn btn-sm btn-link text-secondary p-1" onclick="toggleMusicSearchBar()" title="Cari Lagu">
                            <i class="bi ${dol}{window.musicIsSearchExpanded ? 'bi-x-lg text-primary' : 'bi-search'}" style="font-size: 18px;"></i>
                        </button>
                        <button class="btn btn-sm btn-link text-secondary p-1" onclick="openMusicSettingsModal()" title="Pengaturan Musik">
                            <i class="bi bi-three-dots-vertical" style="font-size: 20px;"></i>
                        </button>
                    </div>
                </div>

                <!-- MAIN SCROLLABLE CONTENT AREA -->
                <div id="musicTabContentArea">
                    ${dol}{renderActiveMusicTabContentWithChips()}
                </div>
            `;

            document.getElementById('tab-content').innerHTML = html;

            // Deck, modal dan input file dipasang di level body agar tetap hidup saat
            // pengguna berpindah halaman (dock "Kini Memutar" juga tampil di luar tab musik).
            ensureMusicShellMounted();
            refreshMusicDeck();
            mountMusicDock();

            // Bind swipe gesture listeners for vinyl/album art
            setupMusicArtworkGestures();
            updateRepeatButtonUI();
            updateShuffleButtonUI();
            syncMusicDockState();

            // Sekali per sesi browser: isi otomatis daftar lagu dengan musik yang ada di HP.
            if (!window.musicPhoneTracksLoaded && !window.musicPhoneAutoTried) {
                window.musicPhoneAutoTried = true;
                loadPhoneMusicLibrary(false);
            }
        }

        function ensureMusicShellMounted() {
            const existing = document.getElementById('musicWebShell');
            if (existing) return existing;
            const shell = document.createElement('div');
            shell.id = 'musicWebShell';
            shell.innerHTML = getMusicShellHtml();
            document.body.appendChild(shell);
            return shell;
        }

        function getMusicShellHtml() {
            const currentTrack = window.musicPlaylist[window.currentMusicIndex];
            return `
                <!-- HIDDEN FILE INPUTS -->
                <input type="file" id="musicFolderPickerInput" webkitdirectory directory multiple class="d-none" onchange="handleMusicFilesImport(event)">
                <input type="file" id="musicFilesPickerInput" accept="audio/*" multiple class="d-none" onchange="handleMusicFilesImport(event)">
                <input type="file" id="musicCoverPickerInput" accept="image/*" class="d-none" onchange="handleCustomCoverUpload(event)">
                <input type="file" id="musicLrcRestoreInput" accept=".json,application/json" class="d-none" onchange="handleRestoreLyricsFile(event)">

                <!-- FULL IMMERSIVE PLAYER DECK OVERLAY -->
                <div id="musicExpandedPlayerDeck" style="display: none; position: fixed; inset: 0; z-index: 1060; background: var(--bs-body-bg); overflow-y: auto;">
                    ${dol}{currentTrack ? getExpandedPlayerDeckHtml(currentTrack) : ''}
                </div>

                <!-- ANTREAN LAGU (QUEUE) MODAL SHEET -->
                <div class="modal fade" id="musicQueueModal" tabindex="-1" aria-hidden="true">
                    <div class="modal-dialog modal-dialog-centered modal-dialog-scrollable">
                        <div class="modal-content card-custom" style="border-radius: 20px;">
                            <div class="modal-header border-0 pb-0">
                                <div>
                                    <h5 class="modal-title fw-bold d-flex align-items-center gap-2">
                                        <i class="bi bi-music-note-list text-primary"></i> Antrean Lagu
                                    </h5>
                                    <small class="text-secondary" id="musicQueueFolderSubtitle">Folder: ${dol}{escapeHtml(currentTrack ? currentTrack.folder : 'Daftar Lagu')}</small>
                                </div>
                                <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close"></button>
                            </div>
                            <div class="modal-body px-3 py-2" style="max-height: 420px;" id="musicQueueListBody">
                                ${dol}{getQueueListHtml()}
                            </div>
                        </div>
                    </div>
                </div>

                <!-- PENGATURAN (SETTINGS) MODAL -->
                <div class="modal fade" id="musicSettingsModal" tabindex="-1" aria-hidden="true">
                    <div class="modal-dialog modal-dialog-centered">
                        <div class="modal-content card-custom" style="border-radius: 20px;">
                            <div class="modal-header border-0 pb-0">
                                <div>
                                    <h4 class="modal-title fw-bold">Pengaturan</h4>
                                    <small class="text-secondary">Kelola folder penyimpanan musik dan preferensi pemutar audio Anda.</small>
                                </div>
                                <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close"></button>
                            </div>
                            <div class="modal-body p-3" id="musicSettingsModalBody">
                                ${dol}{getMusicSettingsHtml()}
                            </div>
                        </div>
                    </div>
                </div>
            `;
        }

        function refreshMusicDeck() {
            const deck = document.getElementById('musicExpandedPlayerDeck');
            if (!deck) return;
            const currentTrack = window.musicPlaylist[window.currentMusicIndex];
            if (currentTrack) deck.innerHTML = getExpandedPlayerDeckHtml(currentTrack);
            deck.style.display = window.musicIsPlayerExpanded ? 'block' : 'none';
        }

        function getMusicHeaderTabsHtml() {
            if (window.musicIsSearchExpanded) {
                return `
                    <div class="d-flex align-items-center bg-body-tertiary rounded-pill px-3 py-1 flex-grow-1 border border-secondary-subtle">
                        <i class="bi bi-search text-secondary me-2"></i>
                        <input type="text" class="form-control form-control-sm border-0 bg-transparent p-0 shadow-none" 
                               id="musicSearchInputField" placeholder="Cari..." value="${dol}{escapeHtml(window.musicSearchQuery)}"
                               oninput="handleMusicSearchChange(this.value)" autofocus>
                        ${dol}{window.musicSearchQuery ? '<button class="btn btn-sm btn-link text-secondary p-0 ms-1" onclick="clearMusicSearch()"><i class="bi bi-x-circle-fill"></i></button>' : ''}
                    </div>
                `;
            } else {
                return `
                    <div class="d-flex align-items-center gap-4">
                        <span class="music-tab-link ${dol}{window.musicSelectedTab === 0 ? 'active' : ''}" onclick="switchMusicTab(0)">Folder</span>
                        <span class="music-tab-link ${dol}{window.musicSelectedTab === 1 ? 'active' : ''}" onclick="switchMusicTab(1)">Favorit</span>
                        <span class="music-tab-link ${dol}{window.musicSelectedTab === 2 ? 'active' : ''}" onclick="switchMusicTab(2)">Daftar Putar</span>
                    </div>
                `;
            }
        }

        function switchMusicTab(tabIdx) {
            window.musicSelectedTab = tabIdx;
            window.musicSearchQuery = '';
            loadMusik();
        }

        function switchMusicSubTab(subIdx) {
            window.musicSelectedSubTab = subIdx;
            renderActiveMusicTabContent();
        }

        function toggleMusicSearchBar() {
            window.musicIsSearchExpanded = !window.musicIsSearchExpanded;
            if (!window.musicIsSearchExpanded) {
                window.musicSearchQuery = '';
            }
            loadMusik();
            if (window.musicIsSearchExpanded) {
                setTimeout(() => {
                    const input = document.getElementById('musicSearchInputField');
                    if (input) input.focus();
                }, 50);
            }
        }

        function handleMusicSearchChange(val) {
            window.musicSearchQuery = val;
            renderActiveMusicTabContent();
        }

        function clearMusicSearch() {
            window.musicSearchQuery = '';
            const input = document.getElementById('musicSearchInputField');
            if (input) input.value = '';
            renderActiveMusicTabContent();
        }

        function renderActiveMusicTabContent() {
            const container = document.getElementById('musicTabContentArea');
            if (container) {
                container.innerHTML = renderActiveMusicTabContentWithChips();
            }
        }

        function renderActiveMusicTabContentWithChips() {
            return getMusicSourceChipsHtml() + renderActiveMusicTabContentHtml();
        }

        function renderActiveMusicTabContentHtml() {
            const query = (window.musicSearchQuery || '').toLowerCase().trim();
            const sourceTracks = getVisibleMusicPlaylist();

            // TAB 0: FOLDER
            if (window.musicSelectedTab === 0) {
                if (sourceTracks.length === 0) {
                    return `
                        <div class="card card-custom p-5 text-center my-3" style="border-radius: 20px;">
                            <div class="rounded-circle d-flex align-items-center justify-content-center mx-auto mb-3" style="width: 76px; height: 76px; background-color: rgba(13, 110, 253, 0.12); color: var(--bs-primary);">
                                <i class="bi bi-folder-fill fs-1"></i>
                            </div>
                            <h6 class="fw-bold mb-2">Folder Musik Belum Dipilih</h6>
                            <p class="text-secondary small mb-4" style="max-width: 440px; margin-inline: auto;">
                                Pilih folder musik dari komputer Anda, muat lagu yang tersimpan di HP, atau tambahkan file lagu untuk mulai memutar musik.
                            </p>
                            <div class="d-flex justify-content-center gap-2 flex-wrap">
                                <button class="btn btn-brand px-4 py-2" onclick="document.getElementById('musicFolderPickerInput').click()">
                                    <i class="bi bi-folder-plus me-2"></i> Pilih Folder Musik
                                </button>
                                <button class="btn btn-outline-primary px-3 py-2" onclick="loadPhoneMusicLibrary(true)">
                                    <i class="bi bi-phone me-1"></i> Muat Musik HP
                                </button>
                                <button class="btn btn-outline-secondary px-3 py-2" onclick="document.getElementById('musicFilesPickerInput').click()">
                                    <i class="bi bi-file-earmark-music me-1"></i> Tambah Berkas
                                </button>
                                <button class="btn btn-outline-primary px-3 py-2" onclick="generateOfflineDemoTrack()">
                                    <i class="bi bi-play-circle me-1"></i> Putar Demo Offline
                                </button>
                            </div>
                            ${dol}{window.musicPhoneError ? '<p class="text-danger small mb-0 mt-3">' + escapeHtml(window.musicPhoneError) + '</p>' : ''}
                        </div>
                    `;
                }

                // Group tracks by folder / artist
                const grouped = {};
                sourceTracks.forEach(trk => {
                    const fName = trk.folder || trk.artist || 'Folder Musik';
                    if (!grouped[fName]) grouped[fName] = [];
                    grouped[fName].push(trk);
                });

                if (!window.musicSelectedFolder) {
                    // Render folder list cards
                    let foldersHtml = '<div class="d-flex flex-column gap-2">';
                    Object.keys(grouped).forEach(folderName => {
                        const count = grouped[folderName].length;
                        foldersHtml += `
                            <div class="music-card-item" onclick="selectMusicFolder('${dol}{escapeHtml(folderName)}')">
                                <div class="d-flex align-items-center gap-3">
                                    <div class="rounded-circle d-flex align-items-center justify-content-center flex-shrink-0" 
                                         style="width: 42px; height: 42px; background: rgba(13, 110, 253, 0.12); color: var(--bs-primary);">
                                        <i class="bi bi-folder-fill fs-5"></i>
                                    </div>
                                    <div>
                                        <div class="fw-bold text-truncate" style="font-size: 14px;">${dol}{escapeHtml(folderName)}</div>
                                        <small class="text-secondary">${dol}{count} Lagu</small>
                                    </div>
                                </div>
                                <i class="bi bi-chevron-right text-secondary"></i>
                            </div>
                        `;
                    });
                    foldersHtml += '</div>';
                    return foldersHtml;
                } else {
                    // Inside selected folder
                    const folderTracks = grouped[window.musicSelectedFolder] || [];
                    const filtered = query ? folderTracks.filter(t => t.title.toLowerCase().includes(query) || (t.artist && t.artist.toLowerCase().includes(query))) : folderTracks;

                    let html = `
                        <div class="d-flex align-items-center justify-content-between mb-3">
                            <button class="btn btn-sm btn-link text-secondary p-0 d-flex align-items-center gap-1" onclick="selectMusicFolder(null)">
                                <i class="bi bi-arrow-left fs-5"></i>
                                <span class="fw-bold">${dol}{escapeHtml(window.musicSelectedFolder)}</span>
                            </button>
                            <small class="text-secondary">${dol}{filtered.length} Lagu</small>
                        </div>
                    `;

                    if (filtered.length === 0) {
                        html += '<div class="text-center text-secondary py-5">Tidak ada lagu yang cocok dengan pencarian.</div>';
                    } else {
                        html += '<div class="d-flex flex-column gap-2">';
                        filtered.forEach((trk) => {
                            const globalIdx = window.musicPlaylist.findIndex(t => t.id === trk.id);
                            html += renderTrackListItemHtml(trk, globalIdx);
                        });
                        html += '</div>';
                    }
                    return html;
                }
            }

            // TAB 1: FAVORIT
            if (window.musicSelectedTab === 1) {
                const favTracks = sourceTracks.filter(t => isTrackFavorite(t))
                    .sort((a, b) => a.title.localeCompare(b.title));
                const filtered = query ? favTracks.filter(t => t.title.toLowerCase().includes(query) || (t.artist && t.artist.toLowerCase().includes(query))) : favTracks;

                if (favTracks.length === 0) {
                    return `
                        <div class="card card-custom p-5 text-center my-3" style="border-radius: 20px;">
                            <div class="rounded-circle d-flex align-items-center justify-content-center mx-auto mb-3" style="width: 72px; height: 72px; background: rgba(220, 53, 69, 0.1); color: #dc3545;">
                                <i class="bi bi-heart fs-1"></i>
                            </div>
                            <h6 class="fw-bold mb-2">Belum Ada Lagu Favorit</h6>
                            <p class="text-secondary small mb-0" style="max-width: 440px; margin-inline: auto;">
                                Belum ada lagu yang ditambahkan ke favorit. Ketuk ikon hati pada daftar lagu untuk menambahkannya.
                            </p>
                        </div>
                    `;
                }

                if (filtered.length === 0) {
                    return '<div class="text-center text-secondary py-5">Tidak ada lagu favorit yang cocok dengan pencarian.</div>';
                }

                let html = '<div class="d-flex flex-column gap-2">';
                filtered.forEach(trk => {
                    const globalIdx = window.musicPlaylist.findIndex(t => t.id === trk.id);
                    html += renderTrackListItemHtml(trk, globalIdx);
                });
                html += '</div>';
                return html;
            }

            // TAB 2: DAFTAR PUTAR
            if (window.musicSelectedTab === 2) {
                let html = `
                    <div class="d-flex align-items-center gap-2 mb-3">
                        <div class="music-subtab-chip ${dol}{window.musicSelectedSubTab === 0 ? 'active' : ''}" onclick="switchMusicSubTab(0)">Baru Ditambahkan</div>
                        <div class="music-subtab-chip ${dol}{window.musicSelectedSubTab === 1 ? 'active' : ''}" onclick="switchMusicSubTab(1)">Paling Sering</div>
                        <div class="music-subtab-chip ${dol}{window.musicSelectedSubTab === 2 ? 'active' : ''}" onclick="switchMusicSubTab(2)">Baru Saja</div>
                        <div class="music-subtab-chip ${dol}{window.musicSelectedSubTab === 3 ? 'active' : ''}" onclick="switchMusicSubTab(3)">Daftar Putar Saya</div>
                    </div>
                `;

                let list = [];
                let emptyMsg = '';

                if (window.musicSelectedSubTab === 0) {
                    list = [...sourceTracks].reverse();
                    emptyMsg = 'Tidak ada lagu yang baru ditambahkan.';
                } else if (window.musicSelectedSubTab === 1) {
                    list = sourceTracks.filter(t => (window.musicPlayCounts[t.id] || 0) > 0)
                        .sort((a, b) => (window.musicPlayCounts[b.id] || 0) - (window.musicPlayCounts[a.id] || 0));
                    emptyMsg = 'Belum ada lagu yang sering diputar.';
                } else if (window.musicSelectedSubTab === 2) {
                    list = window.musicRecentlyPlayed.filter(t => sourceTracks.some(p => p.id === t.id));
                    emptyMsg = 'Belum ada lagu yang baru saja diputar.';
                } else if (window.musicSelectedSubTab === 3) {
                    list = sourceTracks.filter(t => isTrackInMyPlaylist(t));
                    emptyMsg = 'Belum ada lagu di Daftar Putar Anda. Klik tombol Plus (+) di pemutar musik untuk menambahkan.';
                }

                if (query) {
                    list = list.filter(t => t.title.toLowerCase().includes(query) || (t.artist && t.artist.toLowerCase().includes(query)));
                }

                if (list.length === 0) {
                    html += `
                        <div class="card card-custom p-5 text-center my-3" style="border-radius: 20px;">
                            <div class="rounded-circle d-flex align-items-center justify-content-center mx-auto mb-3" style="width: 70px; height: 70px; background: rgba(127, 127, 127, 0.1); color: var(--bs-secondary);">
                                <i class="bi bi-music-note-beamed fs-2"></i>
                            </div>
                            <p class="text-secondary small mb-0" style="max-width: 420px; margin-inline: auto;">
                                ${dol}{emptyMsg}
                            </p>
                        </div>
                    `;
                } else {
                    html += '<div class="d-flex flex-column gap-2">';
                    list.forEach(trk => {
                        const globalIdx = window.musicPlaylist.findIndex(t => t.id === trk.id);
                        const playCount = window.musicPlayCounts[trk.id] || 0;
                        const sub = window.musicSelectedSubTab === 1 ? `Diputar ${dol}{playCount} kali` : null;
                        html += renderTrackListItemHtml(trk, globalIdx, sub);
                    });
                    html += '</div>';
                }
                return html;
            }

            return '';
        }

        function renderTrackListItemHtml(track, globalIndex, customSubtitle) {
            const isActive = globalIndex === window.currentMusicIndex;
            const isPlaying = isActive && !window.musicPlayerAudio.paused && !window.musicPlayerAudio.ended;
            const isFav = isTrackFavorite(track);
            const coverUrl = getTrackCover(track);

            return `
                <div class="music-card-item ${dol}{isActive ? 'active-playing' : ''}" onclick="playTrackByIndex(${dol}{globalIndex})">
                    <div class="d-flex align-items-center gap-3 overflow-hidden flex-grow-1">
                        <!-- ALBUM ART / VINYL THUMBNAIL -->
                        <div class="rounded-3 flex-shrink-0 d-flex align-items-center justify-content-center overflow-hidden" 
                             style="width: 44px; height: 44px; background: linear-gradient(135deg, #2c3e50, #1a252f); border: 1px solid rgba(255,255,255,0.1);">
                            ${dol}{coverUrl ? `<img src="${dol}{coverUrl}" style="width: 100%; height: 100%; object-fit: cover;">` : '<i class="bi bi-music-note-beamed text-light fs-5"></i>'}
                        </div>

                        <!-- TITLE & ARTIST -->
                        <div class="overflow-hidden flex-grow-1">
                            <div class="fw-bold text-truncate ${dol}{isActive ? 'text-primary' : ''}" style="font-size: 13.5px;" title="${dol}{escapeHtml(track.title)}">
                                ${dol}{escapeHtml(track.title)}
                            </div>
                            <small class="text-secondary text-truncate d-block" style="font-size: 11px;">
                                ${dol}{escapeHtml(customSubtitle || track.artist || track.folder || 'Berkas Audio')}
                            </small>
                        </div>
                    </div>

                    <!-- ACTION BUTTONS: FAVORITE & STATUS -->
                    <div class="d-flex align-items-center gap-2 flex-shrink-0 ms-2">
                        <button class="btn btn-sm btn-link p-1" onclick="toggleTrackFavorite('${dol}{track.id}', event)" title="Favorit">
                            <i class="bi ${dol}{isFav ? 'bi-heart-fill text-danger' : 'bi-heart text-secondary'}" style="font-size: 18px;"></i>
                        </button>
                        <div class="music-track-active-icon ms-1" style="width: 24px; text-align: center;">
                            ${dol}{isActive && isPlaying ? '<i class="bi bi-volume-up-fill text-primary fs-5"></i>' : ''}
                        </div>
                    </div>
                </div>
            `;
        }

        function selectMusicFolder(fName) {
            window.musicSelectedFolder = fName;
            renderActiveMusicTabContent();
        }

        function getExpandedPlayerDeckHtml(currentTrack) {
            if (!currentTrack) return '';
            const isPlaying = window.musicPlayerAudio && !window.musicPlayerAudio.paused && !window.musicPlayerAudio.ended;
            const isFav = isTrackFavorite(currentTrack);
            const inPl = isTrackInMyPlaylist(currentTrack);
            const coverUrl = getTrackCover(currentTrack);
            const lrcText = getTrackLyrics(currentTrack);

            return `
                <div class="container py-3" style="max-width: 480px; min-height: 100vh; display: flex; flex-column; justify-content: space-between;">
                    <div>
                        <!-- TOP BAR INSIDE EXPANDED DECK -->
                        <div class="d-flex align-items-center justify-content-between mb-4">
                            <button class="btn btn-link text-body p-0" onclick="collapseMusicPlayerDeck()" title="Perkecil">
                                <i class="bi bi-chevron-down fs-2"></i>
                            </button>
                            <span class="fw-bold" style="font-size: 14px; letter-spacing: 0.5px;">Kini Memutar</span>
                            <button class="btn btn-link text-secondary p-0" onclick="openMusicSettingsModal()" title="Opsi">
                                <i class="bi bi-three-dots-vertical fs-5"></i>
                            </button>
                        </div>

                        <!-- BAGIAN TENGAH: SAMPUL ALBUM (CARD 1:1) / MODE LIRIK -->
                        <div class="card card-custom p-0 mx-auto shadow-lg mb-4" 
                             id="musicCentralArtCard"
                             style="border-radius: 24px; width: 100%; max-width: 320px; aspect-ratio: 1 / 1; position: relative; overflow: hidden; background: rgba(127,127,127,0.08); border: 1px solid rgba(255,255,255,0.1);">
                            ${dol}{getCentralArtCardInnerHtml(currentTrack)}
                        </div>

                        <!-- TRACK METADATA (TITLE & ARTIST) -->
                        <div class="text-center px-3 mb-3">
                            <h4 class="fw-bold mb-1 text-truncate cursor-pointer" id="deckTrackTitle" 
                                onclick="toggleLyricsMode()" title="Ketuk untuk melihat lirik">
                                ${dol}{escapeHtml(currentTrack.title)}
                            </h4>
                            <div class="text-secondary small text-truncate" id="deckTrackArtist">
                                ${dol}{escapeHtml(currentTrack.artist || currentTrack.folder || 'Folder Audio')}
                            </div>
                        </div>

                        <!-- MIDDLE ACTION ROW (QUEUE, FAVORITE, PLAYLIST) -->
                        <div class="d-flex align-items-center justify-content-center gap-5 my-3">
                            <!-- 1. Ikon Antrean -->
                            <button class="btn btn-link text-secondary p-0" onclick="openMusicQueueModal()" title="Lihat Antrean">
                                <i class="bi bi-music-note-list fs-4"></i>
                            </button>

                            <!-- 2. Ikon Hati (Favorit) -->
                            <button class="btn btn-link p-0" id="deckFavoriteBtn" onclick="toggleTrackFavorite('${dol}{currentTrack.id}')" title="Favorit">
                                <i class="bi ${dol}{isFav ? 'bi-heart-fill text-danger' : 'bi-heart text-secondary'}" style="font-size: 26px;"></i>
                            </button>

                            <!-- 3. Ikon Plus (Daftar Putar Saya) -->
                            <button class="btn btn-link p-0" id="deckPlaylistBtn" onclick="toggleTrackMyPlaylist('${dol}{currentTrack.id}')" title="Daftar Putar">
                                <i class="bi ${dol}{inPl ? 'bi-check-lg text-success' : 'bi-plus-lg text-secondary'}" style="font-size: 26px;"></i>
                            </button>
                        </div>
                    </div>

                    <!-- BOTTOM SECTION: SEEK BAR & CONTROLS -->
                    <div>
                        <!-- SEEK SLIDER & DURATION -->
                        <div class="px-2 mb-3">
                            <input type="range" class="form-range music-seek-slider" min="0" 
                                   max="${dol}{window.musicTotalDuration || 100}" 
                                   value="${dol}{window.musicPlaybackPosition || 0}"
                                   onchange="handleMusicSeekChange(this.value)"
                                   oninput="handleMusicSeekInput(this.value)">
                            <div class="d-flex justify-content-between small text-secondary">
                                <span class="music-cur-time-text fw-bold text-primary">${dol}{formatAudioTime(window.musicPlaybackPosition || 0)}</span>
                                <span class="music-dur-time-text">${dol}{formatAudioTime(window.musicTotalDuration || 0)}</span>
                            </div>
                        </div>

                        <!-- MAIN MEDIA CONTROL DECK (SHUFFLE, PREV, BIG PLAY, NEXT, REPEAT) -->
                        <div class="d-flex align-items-center justify-content-between px-2 mb-4">
                            <!-- 1. SHUFFLE -->
                            <button class="btn btn-link p-0 music-shuffle-btn"
                                    id="deckShuffleBtn" onclick="toggleShuffleMode()" title="Acak / Shuffle">
                                <i class="bi bi-shuffle" style="font-size: 24px;"></i>
                            </button>

                            <!-- 2. PREV -->
                            <button class="btn btn-link text-body p-0" onclick="prevMusicTrack()" title="Sebelumnya">
                                <i class="bi bi-skip-start-fill" style="font-size: 36px;"></i>
                            </button>

                            <!-- 3. BIG PLAY/PAUSE BUTTON (72px CIRCLE) -->
                            <div class="rounded-circle d-flex align-items-center justify-content-center shadow cursor-pointer bg-primary"
                                 id="musicBigPlayPauseBtn"
                                 style="width: 72px; height: 72px; transition: transform 0.1s ease;"
                                 onclick="togglePlayMusic()">
                                <i class="bi ${dol}{isPlaying ? 'bi-pause-fill' : 'bi-play-fill'}" style="font-size: 38px; color: white;"></i>
                            </div>

                            <!-- 4. NEXT -->
                            <button class="btn btn-link text-body p-0" onclick="nextMusicTrack()" title="Selanjutnya">
                                <i class="bi bi-skip-end-fill" style="font-size: 36px;"></i>
                            </button>

                            <!-- 5. REPEAT MODE -->
                            <button class="btn btn-link p-0 music-repeat-btn" id="deckRepeatBtn" onclick="cycleRepeatMode()" title="Mode Pengulangan" style="position:relative;">
                                <i class="bi bi-repeat text-secondary" style="font-size: 24px;"></i>
                            </button>
                        </div>
                    </div>
                </div>
            `;
        }

        function getCentralArtCardInnerHtml(currentTrack) {
            if (!currentTrack) return '';
            const isPlaying = window.musicPlayerAudio && !window.musicPlayerAudio.paused && !window.musicPlayerAudio.ended;
            const coverUrl = getTrackCover(currentTrack);
            const lrcText = getTrackLyrics(currentTrack);

            if (window.musicIsLyricsMode) {
                // MODE LIRIK
                if (window.musicLyricsEditMode) {
                    // EDIT LRC MODE
                    const curPos = formatAudioTime(window.musicPlaybackPosition || 0);
                    return `
                        <div class="p-3 d-flex flex-column h-100 bg-body-tertiary">
                            <div class="d-flex justify-content-between align-items-center mb-2">
                                <span class="fw-bold text-primary small">Tulis / Tempel Lirik (LRC)</span>
                                <span class="badge bg-secondary-subtle text-secondary" style="font-size: 10px;">Posisi: ${dol}{curPos}</span>
                            </div>

                            <div class="d-flex gap-2 mb-2">
                                <button class="btn btn-xs btn-primary py-1 px-2 fw-bold" style="font-size: 11px;" onclick="insertTimestampAtLyricsCursor()">
                                    <i class="bi bi-plus-lg me-1"></i> Sisipkan Waktu
                                </button>
                                <button class="btn btn-xs btn-outline-secondary py-1 px-2 fw-bold" style="font-size: 11px;" onclick="loadSampleLrcTemplate()">
                                    <i class="bi bi-file-text me-1"></i> Templat LRC
                                </button>
                            </div>

                            <textarea id="manualLyricsTextarea" class="form-control form-control-sm flex-grow-1 mb-2 font-monospace" 
                                      style="font-size: 11px; resize: none;" 
                                      placeholder="Tulis/tempel lirik di sini. Putar lagu lalu ketuk 'Sisipkan Waktu' untuk merekam ketukan lirik!">${dol}{escapeHtml(lrcText)}</textarea>

                            <div class="d-flex justify-content-end gap-2">
                                <button class="btn btn-sm btn-outline-secondary px-3" onclick="closeLyricsEditMode()">Batal</button>
                                <button class="btn btn-sm btn-primary px-3 fw-bold" onclick="saveManualLyricsFromEditor()">Simpan</button>
                            </div>
                        </div>
                    `;
                } else {
                    // VIEW LYRICS MODE
                    const parsedLines = parseLrcLyrics(lrcText);
                    return `
                        <div class="p-3 d-flex flex-column h-100 bg-body-tertiary">
                            <div class="d-flex justify-content-between align-items-center mb-2 border-bottom border-secondary-subtle pb-2">
                                <div class="d-flex align-items-center gap-1 text-primary fw-bold small">
                                    <i class="bi bi-lyrics"></i> Lirik Lagu
                                </div>
                                <div class="d-flex align-items-center gap-1">
                                    <button class="btn btn-sm btn-link text-primary p-0 px-1" onclick="openLyricsEditMode()" title="Edit Lirik">
                                        <i class="bi bi-pencil-square fs-6"></i>
                                    </button>
                                    <button class="btn btn-sm btn-link text-secondary p-0 px-1" onclick="toggleLyricsMode()" title="Tutup Lirik">
                                        <i class="bi bi-x-lg fs-6"></i>
                                    </button>
                                </div>
                            </div>

                            <div class="flex-grow-1 overflow-auto px-2 py-3" id="musicLyricsScrollContainer" style="max-height: 230px;">
                                ${dol}{parsedLines.length === 0 ? `
                                    <div class="text-center text-secondary py-4" onclick="openLyricsEditMode()" style="cursor: pointer;">
                                        <i class="bi bi-file-earmark-music fs-2 d-block mb-2 opacity-50"></i>
                                        <div class="fw-bold small">Belum ada lirik lagu</div>
                                        <small class="opacity-75">Ketuk ikon pensil di atas untuk menambahkan lirik manual.</small>
                                    </div>
                                ` : parsedLines.map((line, idx) => `
                                    <div class="lrc-line-item" onclick="seekAudioToSeconds(${dol}{line.timeSec})">
                                        ${dol}{escapeHtml(line.text)}
                                    </div>
                                `).join('')}
                            </div>
                        </div>
                    `;
                }
            } else {
                // NORMAL ALBUM ART VIEW
                return `
                    <div class="w-100 h-100 d-flex align-items-center justify-content-center cursor-pointer" 
                         onclick="toggleLyricsMode()" title="Ketuk untuk melihat lirik">
                        ${dol}{coverUrl ? `
                            <img src="${dol}{coverUrl}" style="width: 100%; height: 100%; object-fit: cover;">
                        ` : `
                            <!-- STYLIZED VINYL RECORD / ARTWORK -->
                            <div class="rounded-circle shadow-lg d-flex align-items-center justify-content-center ${dol}{isPlaying ? 'spinning-disc' : ''}" 
                                 id="musicVinylArtDisc"
                                 style="width: 240px; height: 240px; background: radial-gradient(circle, #222 0%, #111 60%, #000 100%); border: 8px solid #333; position: relative;">
                                <!-- Grooves -->
                                <div class="rounded-circle border border-secondary border-opacity-25" style="width: 180px; height: 180px; position: absolute;"></div>
                                <div class="rounded-circle border border-secondary border-opacity-25" style="width: 120px; height: 120px; position: absolute;"></div>
                                <!-- Center Label -->
                                <div class="rounded-circle d-flex align-items-center justify-content-center shadow-sm" 
                                     style="width: 70px; height: 70px; background: linear-gradient(135deg, #E91E63, #8338EC); color: white;">
                                    <i class="bi bi-music-note-beamed fs-3"></i>
                                </div>
                            </div>
                        `}

                        <!-- GANTI SAMPUL CAMERA BUTTON (TOP RIGHT) -->
                        <button class="btn btn-sm btn-dark rounded-circle p-0 d-flex align-items-center justify-content-center" 
                                style="position: absolute; top: 12px; right: 12px; width: 34px; height: 34px; background: rgba(0,0,0,0.6); backdrop-filter: blur(4px); z-index: 5;"
                                onclick="event.stopPropagation(); document.getElementById('musicCoverPickerInput').click()" 
                                title="Ganti Sampul Album">
                            <i class="bi bi-camera-fill text-white fs-6"></i>
                        </button>
                    </div>
                `;
            }
        }

        function expandMusicPlayerDeck() {
            const currentTrack = window.musicPlaylist[window.currentMusicIndex];
            if (!currentTrack) return;
            window.musicIsPlayerExpanded = true;
            ensureMusicShellMounted();
            const deck = document.getElementById('musicExpandedPlayerDeck');
            if (deck) {
                deck.innerHTML = getExpandedPlayerDeckHtml(currentTrack);
                deck.style.display = 'block';
                updateRepeatButtonUI();
                updateShuffleButtonUI();
                setupMusicArtworkGestures();
            }
            const dock = document.getElementById('musicGlobalDock');
            if (dock) dock.style.display = 'none';
        }

        function collapseMusicPlayerDeck() {
            window.musicIsPlayerExpanded = false;
            const deck = document.getElementById('musicExpandedPlayerDeck');
            if (deck) deck.style.display = 'none';
            const dock = document.getElementById('musicGlobalDock');
            if (dock) dock.style.display = '';
        }

        function updateExpandedDeckTrackInfo() {
            const currentTrack = window.musicPlaylist[window.currentMusicIndex];
            if (!currentTrack) return;

            const tEl = document.getElementById('deckTrackTitle');
            if (tEl) tEl.innerText = currentTrack.title;
            const aEl = document.getElementById('deckTrackArtist');
            if (aEl) aEl.innerText = currentTrack.artist || currentTrack.folder || 'Folder Audio';

            const card = document.getElementById('musicCentralArtCard');
            if (card) card.innerHTML = getCentralArtCardInnerHtml(currentTrack);
        }

        function toggleLyricsMode() {
            window.musicIsLyricsMode = !window.musicIsLyricsMode;
            window.musicLyricsEditMode = false;
            const card = document.getElementById('musicCentralArtCard');
            const currentTrack = window.musicPlaylist[window.currentMusicIndex];
            if (card && currentTrack) {
                card.innerHTML = getCentralArtCardInnerHtml(currentTrack);
            }
        }

        function openLyricsEditMode() {
            window.musicLyricsEditMode = true;
            const card = document.getElementById('musicCentralArtCard');
            const currentTrack = window.musicPlaylist[window.currentMusicIndex];
            if (card && currentTrack) {
                card.innerHTML = getCentralArtCardInnerHtml(currentTrack);
            }
        }

        function closeLyricsEditMode() {
            window.musicLyricsEditMode = false;
            const card = document.getElementById('musicCentralArtCard');
            const currentTrack = window.musicPlaylist[window.currentMusicIndex];
            if (card && currentTrack) {
                card.innerHTML = getCentralArtCardInnerHtml(currentTrack);
            }
        }

        function insertTimestampAtLyricsCursor() {
            const textarea = document.getElementById('manualLyricsTextarea');
            if (!textarea) return;
            const pos = window.musicPlaybackPosition || 0;
            const min = Math.floor(pos / 60);
            const sec = Math.floor(pos % 60);
            const hundredths = Math.floor((pos % 1) * 100);
            const tag = `[${dol}{String(min).padStart(2, '0')}:${dol}{String(sec).padStart(2, '0')}.${dol}{String(hundredths).padStart(2, '0')}] `;

            const start = textarea.selectionStart;
            const end = textarea.selectionEnd;
            const val = textarea.value;
            textarea.value = val.substring(0, start) + tag + val.substring(end);
            textarea.selectionStart = textarea.selectionEnd = start + tag.length;
            textarea.focus();
        }

        function loadSampleLrcTemplate() {
            const textarea = document.getElementById('manualLyricsTextarea');
            if (!textarea) return;
            const template = 
                "[ti:Judul Lagu]\n" +
                "[ar:Nama Penyanyi]\n" +
                "[00:00.00] ~ Intro Instrumental ~\n" +
                "[00:08.50] \n" +
                "[00:10.00] Bait pertama lirik lagu di sini\n" +
                "[00:15.20] Nyanyikan sesuai alunan musik\n" +
                "[00:20.00] \n" +
                "[00:25.00] ~ Reff / Bagian Chorus ~\n" +
                "[00:30.00] Lagu ini terdengar begitu merdu dan syahdu\n" +
                "[00:36.00] \n" +
                "[00:40.00] ~ Outro Instrumental ~";
            textarea.value = template;
            textarea.focus();
        }

        function saveManualLyricsFromEditor() {
            const textarea = document.getElementById('manualLyricsTextarea');
            const currentTrack = window.musicPlaylist[window.currentMusicIndex];
            if (!textarea || !currentTrack) return;

            const text = textarea.value;
            window.musicManualLyrics[currentTrack.id] = text;
            window.musicManualLyrics[currentTrack.title] = text;
            saveMusicSettings();
            showCustomToast('Lirik manual berhasil disimpan!');
            window.musicLyricsEditMode = false;
            const card = document.getElementById('musicCentralArtCard');
            if (card) card.innerHTML = getCentralArtCardInnerHtml(currentTrack);
        }

        function seekAudioToSeconds(sec) {
            window.musicPlayerAudio.currentTime = sec;
        }

        function handleCustomCoverUpload(event) {
            const file = event.target.files && event.target.files[0];
            const currentTrack = window.musicPlaylist[window.currentMusicIndex];
            if (!file || !currentTrack) return;

            const reader = new FileReader();
            reader.onload = (e) => {
                const dataUrl = e.target.result;
                window.musicCustomAlbumArts[currentTrack.id] = dataUrl;
                window.musicCustomAlbumArts[currentTrack.title] = dataUrl;
                saveMusicSettings();
                showCustomToast('Sampul album berhasil diperbarui!');
                updateExpandedDeckTrackInfo();
                loadMusik();
            };
            reader.readAsDataURL(file);
            event.target.value = '';
        }

        function setupMusicArtworkGestures() {
            const card = document.getElementById('musicCentralArtCard');
            if (!card) return;
            let startX = 0;
            let diffX = 0;

            card.addEventListener('touchstart', (e) => {
                if (e.touches && e.touches[0]) startX = e.touches[0].clientX;
            }, { passive: true });

            card.addEventListener('touchmove', (e) => {
                if (e.touches && e.touches[0]) diffX = e.touches[0].clientX - startX;
            }, { passive: true });

            card.addEventListener('touchend', () => {
                if (diffX > 60) {
                    prevMusicTrack(); // Swipe right -> prev
                } else if (diffX < -60) {
                    nextMusicTrack(); // Swipe left -> next
                }
                diffX = 0;
            });
        }

        // --- ANTREAN LAGU (QUEUE) SHEET HTML ---
        function openMusicQueueModal() {
            ensureMusicShellMounted();
            const currentTrack = window.musicPlaylist[window.currentMusicIndex];
            const sub = document.getElementById('musicQueueFolderSubtitle');
            if (sub && currentTrack) sub.innerText = 'Folder: ' + (currentTrack.folder || 'Daftar Lagu');
            const body = document.getElementById('musicQueueListBody');
            if (body) body.innerHTML = getQueueListHtml();
            const modalEl = document.getElementById('musicQueueModal');
            if (modalEl && window.bootstrap) {
                const modal = new bootstrap.Modal(modalEl);
                modal.show();
            }
        }

        function getQueueListHtml() {
            if (window.musicPlaylist.length === 0) {
                return '<div class="text-center text-secondary py-4">Antrean lagu kosong.</div>';
            }
            let html = '<div class="d-flex flex-column gap-2">';
            window.musicPlaylist.forEach((trk, idx) => {
                const isPlaying = idx === window.currentMusicIndex;
                const coverUrl = getTrackCover(trk);
                html += `
                    <div class="music-card-item ${dol}{isPlaying ? 'active-playing' : ''}" onclick="playTrackByIndex(${dol}{idx}); bootstrap.Modal.getInstance(document.getElementById('musicQueueModal'))?.hide();">
                        <div class="d-flex align-items-center gap-3 overflow-hidden flex-grow-1">
                            <div class="rounded-3 flex-shrink-0 d-flex align-items-center justify-content-center overflow-hidden" 
                                 style="width: 36px; height: 36px; background: rgba(127,127,127,0.15);">
                                ${dol}{coverUrl ? `<img src="${dol}{coverUrl}" style="width: 100%; height: 100%; object-fit: cover;">` : '<i class="bi bi-music-note-beamed text-secondary"></i>'}
                            </div>
                            <div class="overflow-hidden">
                                <div class="fw-bold text-truncate ${dol}{isPlaying ? 'text-primary' : ''}" style="font-size: 13px;">${dol}{escapeHtml(trk.title)}</div>
                                <small class="text-secondary text-truncate d-block" style="font-size: 10.5px;">${dol}{escapeHtml(trk.artist || 'Musik')}</small>
                            </div>
                        </div>
                        ${dol}{isPlaying ? '<i class="bi bi-volume-up-fill text-primary ms-2"></i>' : ''}
                    </div>
                `;
            });
            html += '</div>';
            return html;
        }

        // --- PENGATURAN (SETTINGS) MODAL HTML ---
        function openMusicSettingsModal() {
            ensureMusicShellMounted();
            const body = document.getElementById('musicSettingsModalBody');
            if (body) body.innerHTML = getMusicSettingsHtml();
            const modalEl = document.getElementById('musicSettingsModal');
            if (modalEl && window.bootstrap) {
                const modal = new bootstrap.Modal(modalEl);
                modal.show();
            }
        }

        function getMusicSettingsHtml() {
            const isConnected = window.musicPlaylist.length > 0;
            const phoneMusicCount = window.musicPlaylist.filter(function (t) { return t.origin === 'phone'; }).length;
            return `
                <div class="d-flex flex-column gap-3">
                    <!-- 1. FOLDER MUSIK SAF / LOCAL -->
                    <div class="p-3 rounded-3 cursor-pointer d-flex align-items-center gap-3 dashboard-subcard" 
                         onclick="document.getElementById('musicFolderPickerInput').click()">
                        <div class="rounded-3 d-flex align-items-center justify-content-center flex-shrink-0" 
                             style="width: 44px; height: 44px; background: rgba(13, 110, 253, 0.15); color: var(--bs-primary);">
                            <i class="bi bi-folder-fill fs-5"></i>
                        </div>
                        <div class="flex-grow-1">
                            <div class="fw-bold" style="font-size: 14.5px;">Folder Musik</div>
                            <small class="${dol}{isConnected ? 'text-primary' : 'text-danger'} d-block">
                                ${dol}{isConnected ? 'Terkoneksi ke penyimpanan musik lokal' : 'Folder musik belum dipilih (Klik untuk memilih)'}
                            </small>
                        </div>
                    </div>

                    <!-- 2. MUSIK DARI PENYIMPANAN HP -->
                    <div class="p-3 rounded-3 cursor-pointer d-flex align-items-center gap-3 dashboard-subcard"
                         onclick="loadPhoneMusicLibrary(true)">
                        <div class="rounded-3 d-flex align-items-center justify-content-center flex-shrink-0"
                             style="width: 44px; height: 44px; background: rgba(25, 135, 84, 0.15); color: #198754;">
                            <i class="bi bi-phone fs-5"></i>
                        </div>
                        <div class="flex-grow-1">
                            <div class="fw-bold" style="font-size: 14.5px;">Musik dari HP</div>
                            <small class="${dol}{phoneMusicCount > 0 ? 'text-primary' : 'text-secondary'} d-block">
                                ${dol}{phoneMusicCount > 0 ? phoneMusicCount + ' lagu terbaca dari pemutar media HP (ketuk untuk pindai ulang)' : 'Ketuk untuk memindai lagu yang tersimpan di HP'}
                            </small>
                        </div>
                    </div>

                    <!-- 3. PINDAI ULANG FOLDER -->
                    <div class="p-3 rounded-3 cursor-pointer d-flex align-items-center gap-3 dashboard-subcard" 
                         onclick="rescanMusicFolder()">
                        <div class="rounded-3 d-flex align-items-center justify-content-center flex-shrink-0" 
                             style="width: 44px; height: 44px; background: rgba(108, 117, 125, 0.15); color: var(--bs-secondary);">
                            <i class="bi bi-arrow-repeat fs-5"></i>
                        </div>
                        <div class="flex-grow-1">
                            <div class="fw-bold" style="font-size: 14.5px;">Pindai Ulang Folder</div>
                            <small class="text-secondary d-block">Segarkan daftar lagu dari folder aktif</small>
                        </div>
                    </div>

                    <!-- 3. BERSIHKAN RIWAYAT PUTAR -->
                    <div class="p-3 rounded-3 cursor-pointer d-flex align-items-center gap-3 dashboard-subcard" 
                         onclick="clearMusicPlayHistory()">
                        <div class="rounded-3 d-flex align-items-center justify-content-center flex-shrink-0" 
                             style="width: 44px; height: 44px; background: rgba(255, 193, 7, 0.15); color: #ffc107;">
                            <i class="bi bi-trash fs-5"></i>
                        </div>
                        <div class="flex-grow-1">
                            <div class="fw-bold" style="font-size: 14.5px;">Bersihkan Riwayat Putar</div>
                            <small class="text-secondary d-block">Menghapus riwayat putar lagu baru-baru ini</small>
                        </div>
                    </div>

                    <!-- 4. BACKUP LIRIK MANUAL -->
                    <div class="p-3 rounded-3 cursor-pointer d-flex align-items-center gap-3 dashboard-subcard" 
                         onclick="exportManualLyricsJson()">
                        <div class="rounded-3 d-flex align-items-center justify-content-center flex-shrink-0" 
                             style="width: 44px; height: 44px; background: rgba(13, 110, 253, 0.15); color: var(--bs-primary);">
                            <i class="bi bi-cloud-arrow-down-fill fs-5"></i>
                        </div>
                        <div class="flex-grow-1">
                            <div class="fw-bold" style="font-size: 14.5px;">Backup Lirik Manual</div>
                            <small class="text-secondary d-block">Ekspor semua lirik manual ke file JSON</small>
                        </div>
                    </div>

                    <!-- 5. RESTORE LIRIK MANUAL -->
                    <div class="p-3 rounded-3 cursor-pointer d-flex align-items-center gap-3 dashboard-subcard" 
                         onclick="document.getElementById('musicLrcRestoreInput').click()">
                        <div class="rounded-3 d-flex align-items-center justify-content-center flex-shrink-0" 
                             style="width: 44px; height: 44px; background: rgba(25, 135, 84, 0.15); color: #198754;">
                            <i class="bi bi-cloud-arrow-up-fill fs-5"></i>
                        </div>
                        <div class="flex-grow-1">
                            <div class="fw-bold" style="font-size: 14.5px;">Restore Lirik Manual</div>
                            <small class="text-secondary d-block">Impor file lirik manual dari penyimpanan</small>
                        </div>
                    </div>

                    <!-- 6. RESET / KOSONGKAN PLAYLIST -->
                    <div class="p-3 rounded-3 cursor-pointer d-flex align-items-center gap-3 dashboard-subcard text-danger" 
                         onclick="resetEntireMusicPlaylist()">
                        <div class="rounded-3 d-flex align-items-center justify-content-center flex-shrink-0" 
                             style="width: 44px; height: 44px; background: rgba(220, 53, 69, 0.15); color: #dc3545;">
                            <i class="bi bi-x-circle-fill fs-5"></i>
                        </div>
                        <div class="flex-grow-1">
                            <div class="fw-bold" style="font-size: 14.5px;">Kosongkan Daftar Lagu</div>
                            <small class="text-secondary d-block">Hapus semua trek dan bersihkan cache musik offline</small>
                        </div>
                    </div>
                </div>
            `;
        }

        function rescanMusicFolder() {
            showCustomToast('Memindai ulang folder musik...');
            loadMusik();
            bootstrap.Modal.getInstance(document.getElementById('musicSettingsModal'))?.hide();
        }

        function clearMusicPlayHistory() {
            window.musicRecentlyPlayed = [];
            window.musicPlayCounts = {};
            saveMusicSettings();
            showCustomToast('Riwayat putar dibersihkan');
            renderActiveMusicTabContent();
            bootstrap.Modal.getInstance(document.getElementById('musicSettingsModal'))?.hide();
        }

        function exportManualLyricsJson() {
            const dataStr = "data:text/json;charset=utf-8," + encodeURIComponent(JSON.stringify(window.musicManualLyrics, null, 2));
            const dlAnchor = document.createElement('a');
            dlAnchor.setAttribute("href", dataStr);
            dlAnchor.setAttribute("download", "singghara_backup_lirik.json");
            document.body.appendChild(dlAnchor);
            dlAnchor.click();
            dlAnchor.remove();
            showCustomToast('Backup lirik manual berhasil diunduh!');
        }

        function handleRestoreLyricsFile(event) {
            const file = event.target.files && event.target.files[0];
            if (!file) return;
            const reader = new FileReader();
            reader.onload = (e) => {
                try {
                    const parsed = JSON.parse(e.target.result);
                    if (typeof parsed === 'object' && parsed !== null) {
                        window.musicManualLyrics = Object.assign({}, window.musicManualLyrics, parsed);
                        saveMusicSettings();
                        showCustomToast('Pemulihan lirik manual berhasil!');
                        updateExpandedDeckTrackInfo();
                    } else {
                        showCustomToast('Format file tidak valid untuk lirik');
                    }
                } catch (err) {
                    showCustomToast('Gagal memulihkan lirik: ' + err.message);
                }
            };
            reader.readAsText(file);
            event.target.value = '';
            bootstrap.Modal.getInstance(document.getElementById('musicSettingsModal'))?.hide();
        }

        async function resetEntireMusicPlaylist() {
            if (!confirm('Apakah Anda yakin ingin mengosongkan seluruh daftar lagu?')) return;
            window.musicPlayerAudio.pause();
            window.musicPlayerAudio.src = '';
            window.musicPlaylist = [];
            window.currentMusicIndex = 0;
            window.musicSelectedFolder = null;
            window.musicPhoneTracksLoaded = false;
            window.musicPhoneAutoTried = true;
            window.musicFetchedLyrics = {};
            window.musicDockTrackId = null;
            const dockEl = document.getElementById('musicGlobalDock');
            if (dockEl) dockEl.remove();
            document.body.classList.remove('has-music-dock');

            // Clear IndexedDB tracks
            const db = await openMusicDatabase();
            if (db) {
                try {
                    const tx = db.transaction('tracks', 'readwrite');
                    tx.objectStore('tracks').clear();
                } catch (e) {}
            }

            collapseMusicPlayerDeck();
            loadMusik();
            bootstrap.Modal.getInstance(document.getElementById('musicSettingsModal'))?.hide();
            showCustomToast('Daftar lagu berhasil dikosongkan.');
        }
        """
    }
}
