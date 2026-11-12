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

        // Ikon Material Symbols (Google) ditanam inline agar Web Connect tetap
        // 100% offline: repeat, repeat_one, repeat_on, dan sort_by_alpha.
        window.musicRepeatIconPaths = {
            'NONE': 'M280-80 120-240l160-160 56 58-62 62h406v-160h80v240H274l62 62-56 58Zm-80-440v-240h486l-62-62 56-58 160 160-160 160-56-58 62-62H280v160h-80Z',
            'ONE': 'M460-360v-180h-60v-60h120v240h-60ZM280-80 120-240l160-160 56 58-62 62h406v-160h80v240H274l62 62-56 58Zm-80-440v-240h486l-62-62 56-58 160 160-160 160-56-58 62-62H280v160h-80Z',
            'ALL': 'M120-40q-33 0-56.5-23.5T40-120v-720q0-33 23.5-56.5T120-920h720q33 0 56.5 23.5T920-840v720q0 33-23.5 56.5T840-40H120Zm160-40 56-58-62-62h486v-240h-80v160H274l62-62-56-58-160 160L280-80Zm-80-440h80v-160h406l-62 62 56 58 160-160-160-160-56 58 62 62H200v240Z',
            'AZ': 'm80-280 150-400h86l150 400h-82l-34-96H196l-32 96H80Zm140-164h104l-48-150h-6l-50 150Zm328 164v-76l202-252H556v-72h282v76L638-352h202v72H548ZM360-760l120-120 120 120H360ZM480-80 360-200h240L480-80Z'
        };

        function musicRepeatIconHtml(mode, size, colorClass) {
            var path = window.musicRepeatIconPaths[mode] || window.musicRepeatIconPaths['NONE'];
            return '<svg xmlns="http://www.w3.org/2000/svg" width="' + size + '" height="' + size +
                '" viewBox="0 -960 960 960" fill="currentColor" style="display:block" class="' + (colorClass || '') +
                '" aria-hidden="true" focusable="false"><path d="' + path + '"/></svg>';
        }

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
        window.musicLyricsOffsets = window.musicLyricsOffsets || {}; // { [trackId]: ms } geser timing lirik
        window._musicLrcCache = null; // { trackId, source, lines, info } hasil parse terakhir
        window._lyricsRafId = null; // satu loop requestAnimationFrame utk sorotan lirik halus
        window._lyricsScrollPauseUntil = 0; // auto-scroll berhenti sebentar saat user menggulir
        window.musicCustomAlbumArts = window.musicCustomAlbumArts || {}; // { [trackIdOrTitle]: dataUrl }
        // Sampul otomatis (tag tertanam / cover.jpg folder impor). Disimpan di IndexedDB,
        // bukan localStorage, karena data URL banyak lagu cepat melewati kuota ~5 MB.
        window.musicAutoAlbumArts = window.musicAutoAlbumArts || {}; // { [trackIdOrTitle]: dataUrl }
        window.musicFavorites = window.musicFavorites || []; // [trackIdOrTitle]
        window.musicMyPlaylist = window.musicMyPlaylist || []; // [trackIdOrTitle]
        window.musicPlayCounts = window.musicPlayCounts || {}; // { [trackIdOrTitle]: number }
        window.musicRecentlyPlayed = window.musicRecentlyPlayed || []; // [track objects]

        // --- INDEXEDDB & LOCALSTORAGE PERSISTENCE (100% OFFLINE) ---
        const MUSIC_DB_NAME = 'SinggharaMusicOfflineDB';
        // Versi 2 menambah object store `covers`; pengguna lama perlu upgrade agar
        // sampul hasil ekstraksi benar-benar tersimpan.
        const MUSIC_DB_VERSION = 2;
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
                    if (s.lyricsOffsets && typeof s.lyricsOffsets === 'object') window.musicLyricsOffsets = s.lyricsOffsets;
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
                        // Lirik .lrc yang diimpor pengguna ikut bertahan setelah browser ditutup.
                        try {
                            const lyrReq = db.transaction('lyrics', 'readonly').objectStore('lyrics').getAll();
                            lyrReq.onsuccess = () => {
                                (lyrReq.result || []).forEach(rec => {
                                    if (rec && rec.id && typeof rec.text === 'string' && window.musicFetchedLyrics[rec.id] === undefined) {
                                        window.musicFetchedLyrics[rec.id] = rec.text;
                                    }
                                });
                                refreshLyricsViews();
                            };
                        } catch (e) {}
                        // Sampul hasil ekstraksi tag/cover.jpg folder impor.
                        try {
                            const coverReq = db.transaction('covers', 'readonly').objectStore('covers').getAll();
                            coverReq.onsuccess = () => {
                                (coverReq.result || []).forEach(rec => {
                                    if (rec && rec.id && typeof rec.dataUrl === 'string' && !window.musicAutoAlbumArts[rec.id]) {
                                        window.musicAutoAlbumArts[rec.id] = rec.dataUrl;
                                    }
                                });
                                refreshMusicDeckImages();
                            };
                        } catch (e) {}
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
                    volume: window.musicPlayerAudio.volume,
                    lyricsOffsets: window.musicLyricsOffsets
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
                    const pct = dur > 0 ? Math.min(100, cur * 100 / dur).toFixed(1) : 0;
                    sliders.forEach(el => {
                        if (dur > 0) {
                            el.max = dur;
                            el.value = cur;
                        }
                        el.style.setProperty('--seek-pct', pct + '%');
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

            window.musicPlayerAudio.addEventListener('error', async () => {
                const failed = window.musicPlaylist[window.currentMusicIndex];
                const err = window.musicPlayerAudio.error;
                // 'error' juga meledak saat src dikosongkan (stop/reset) atau saat user
                // berpindah trek cepat (ABORTED) — dua-duanya bukan kegagalan playback.
                if (!window.musicPlayerAudio.currentSrc || !err || err.code === 1) {
                    syncMusicDockState();
                    return;
                }
                const codeNames = { 1: 'ABORTED', 2: 'NETWORK', 3: 'DECODE', 4: 'SRC_NOT_SUPPORTED' };
                const code = codeNames[err.code] || String(err.code);
                if (failed && failed.origin === 'phone') {
                    // Minta sebab konkret ke server: uri/ukuran/mime/offset/fd bisa dibaca atau tidak.
                    let detail = '';
                    try {
                        const res = await fetch('/api/phone_media/probe?type=audio&id=' + (failed.mediaId || -1), { cache: 'no-store' });
                        if (res.ok) {
                            const p = await res.json();
                            if (p.error || p.fdOpened === false || p.firstByteReadable === false || (typeof p.statSize === 'number' && p.statSize <= 0)) {
                                detail = ' Server: ' + JSON.stringify(p).slice(0, 180);
                            }
                        } else {
                            detail = ' Diagnosis balas HTTP ' + res.status + '.';
                        }
                    } catch (e) {
                        detail = ' Server diagnosis tidak terjangkau.';
                    }
                    showCustomToast('Lagu HP gagal diputar (' + code + ').' + detail);
                } else {
                    showCustomToast('Audio gagal diputar (' + code + ').');
                }
                syncMusicDockState();
            });

            window.musicPlayerAudio.addEventListener('volumechange', () => {
                saveMusicSettings();
            });

            window.musicPlayerAudio.addEventListener('play', () => {
                updatePlayPauseStateInUI(true);
                syncMusicDockState();
                startLyricsClock();
            });

            window.musicPlayerAudio.addEventListener('pause', () => {
                updatePlayPauseStateInUI(false);
                syncMusicDockState();
                stopLyricsClock();
                updateLyricsHighlight(window.musicPlayerAudio.currentTime || 0);
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
            // Tombol putar di layar lirik penuh
            const fsBtn = document.getElementById('musicFsPlayBtn');
            if (fsBtn) {
                fsBtn.innerHTML = `<i class="bi ${dol}{isPlaying ? 'bi-pause-fill' : 'bi-play-fill'}"></i>`;
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
            const autoArts = window.musicAutoAlbumArts || {};
            if (autoArts[track.id]) return autoArts[track.id];
            if (autoArts[track.title]) return autoArts[track.title];
            if (track.relFolder && autoArts['folder:' + track.relFolder]) return autoArts['folder:' + track.relFolder];
            if (track.folder && autoArts['folder:' + track.folder]) return autoArts['folder:' + track.folder];
            if (track.artUrl) return track.artUrl;
            return null;
        }

        // Trek HP tanpa sampul membuat endpoint art menjawab 404; tanpa handler ini
        // browser menampilkan ikon gambar rusak. Ganti dengan ikon/bentuk cadangan.
        function musicCoverFailed(img) {
            const parent = img.parentNode;
            if (!parent) return;
            img.remove();
            if (parent.getAttribute('data-cover-fallback') === 'vinyl') {
                const disc = document.createElement('div');
                disc.className = 'rounded-circle shadow-lg d-flex align-items-center justify-content-center';
                disc.style.cssText = 'width:min(240px,70%);aspect-ratio:1/1;background:radial-gradient(circle,#222 0%,#111 60%,#000 100%);border:8px solid #333;';
                disc.innerHTML = '<i class="bi bi-music-note-beamed" style="font-size:44px;color:#e2e8f0;opacity:.8;"></i>';
                parent.appendChild(disc);
                return;
            }
            const size = Math.max(16, Math.round((parent.clientWidth || 40) * 0.42));
            parent.innerHTML = '<i class="bi bi-music-note-beamed" style="font-size:' + size + 'px;color:var(--brand-green);opacity:.75;"></i>';
        }

        function musicCoverImgHtml(src) {
            if (!src) return '<i class="bi bi-music-note-beamed text-secondary fs-5"></i>';
            return '<img src="' + src + '" style="width:100%;height:100%;object-fit:cover;" onerror="musicCoverFailed(this)">';
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

        // Lirik dimuat sekali per trek: untuk lagu HP lewat server (.lrc samping + tag
        // tertanam ID3/MP4/Vorbis, parser sama dengan aplikasi), untuk lagu PC dengan
        // membaca tag ID3 langsung dari blob di browser.
        async function ensureTrackLyricsLoaded(track) {
            if (!track) return;
            if (track.origin === 'phone') {
                if (!track.lyricsUrl || window.musicFetchedLyrics[track.id] !== undefined) return;
                try {
                    const res = await fetch(track.lyricsUrl, { cache: 'no-store' });
                    window.musicFetchedLyrics[track.id] = res.ok ? await res.text() : '';
                } catch (err) {
                    window.musicFetchedLyrics[track.id] = '';
                }
            } else if (track.origin === 'pc') {
                if (!track.blob || window.musicFetchedLyrics[track.id] !== undefined) return;
                window.musicFetchedLyrics[track.id] = await extractEmbeddedLyricsFromBlob(track.blob);
            } else {
                return;
            }
            const nowTrack = window.musicPlaylist[window.currentMusicIndex];
            if (nowTrack && nowTrack.id === track.id) {
                autoOpenLyricsIfPresent();
                refreshLyricsViews();
            }
        }

        // Seperti di aplikasi: trek yang punya lirik langsung menampilkan lirik,
        // kecuali pengguna menutup mode lirik secara manual pada sesi ini.
        function autoOpenLyricsIfPresent() {
            if (window.musicLyricsUserClosed || window.musicIsLyricsMode) return;
            const track = window.musicPlaylist[window.currentMusicIndex];
            if (track && getTrackLyrics(track)) window.musicIsLyricsMode = true;
        }

        function beInt32U(data, off) {
            return ((data[off] << 24) | (data[off + 1] << 16) | (data[off + 2] << 8) | data[off + 3]) >>> 0;
        }
        function beInt24U(data, off) {
            return (data[off] << 16) | (data[off + 1] << 8) | data[off + 2];
        }
        function syncInt32U(data, off) {
            return ((data[off] & 0x7F) << 21) | ((data[off + 1] & 0x7F) << 14) | ((data[off + 2] & 0x7F) << 7) | (data[off + 3] & 0x7F);
        }
        function id3Decode(data, start, end, enc) {
            if (start >= end) return '';
            const slice = data.subarray(start, end);
            try {
                if (enc === 1) return new TextDecoder('utf-16').decode(slice);
                if (enc === 2) return new TextDecoder('utf-16be').decode(slice);
                if (enc === 3) return new TextDecoder('utf-8').decode(slice);
                return new TextDecoder('windows-1252').decode(slice);
            } catch (err) {
                return new TextDecoder('utf-8').decode(slice);
            }
        }
        function id3ReadTerminated(data, start, limit, enc) {
            const isUtf16 = enc === 1 || enc === 2;
            let cursor = start;
            while (cursor < limit) {
                if (isUtf16) {
                    if (cursor + 1 >= limit) { cursor = limit; break; }
                    if (data[cursor] === 0 && data[cursor + 1] === 0) {
                        return { text: id3Decode(data, start, cursor, enc), next: Math.min(cursor + 2, limit) };
                    }
                    cursor += 2;
                } else {
                    if (data[cursor] === 0) {
                        return { text: id3Decode(data, start, cursor, enc), next: Math.min(cursor + 1, limit) };
                    }
                    cursor++;
                }
            }
            return { text: id3Decode(data, start, limit, enc), next: limit };
        }
        function id3Sanitize(raw) {
            if (!raw) return '';
            const cut = raw.indexOf('\u0000');
            const base = cut >= 0 ? raw.slice(0, cut) : raw;
            return base.replace(/\uFEFF/g, '').trim();
        }
        function id3FormatTag(timeMs) {
            const safe = Math.max(0, Math.floor(timeMs));
            const totalSec = Math.floor(safe / 1000);
            const mm = String(Math.floor(totalSec / 60)).padStart(2, '0');
            const ss = String(totalSec % 60).padStart(2, '0');
            const ms = String(safe % 1000).padStart(3, '0');
            return mm + ':' + ss + '.' + ms;
        }
        function id3SyltEntriesToLrc(entries) {
            if (!entries.length) return '';
            let out = '';
            let lineHasWords = false;
            let lastTimeMs = -1;
            let breakAfterPrevious = false;
            entries.forEach(entry => {
                const timeMs = entry[0];
                const rawText = entry[1];
                if (lineHasWords && (breakAfterPrevious || timeMs - lastTimeMs > 900)) {
                    out += '\n';
                    lineHasWords = false;
                }
                const stamp = id3FormatTag(timeMs);
                if (!lineHasWords) out += '[' + stamp + ']';
                const hasLineBreak = rawText.indexOf('\n') >= 0 || rawText.indexOf('\r') >= 0;
                const cleaned = rawText.replace(/[\r\n]+/g, ' ').trim();
                if (cleaned) {
                    if (lineHasWords) out += ' ';
                    out += '<' + stamp + '>' + cleaned;
                    lineHasWords = true;
                }
                breakAfterPrevious = hasLineBreak;
                lastTimeMs = timeMs;
            });
            return out;
        }
        function parseUsltBytes(data, offset, size) {
            const end = offset + size;
            if (size <= 4) return '';
            const enc = data[offset];
            const r = id3ReadTerminated(data, offset + 4, end, enc);
            if (r.next >= end) return '';
            return id3Sanitize(id3Decode(data, r.next, end, enc));
        }
        function parseTxxxBytes(data, offset, size) {
            const end = offset + size;
            if (size <= 2) return '';
            const enc = data[offset];
            const r = id3ReadTerminated(data, offset + 1, end, enc);
            const key = r.text.toUpperCase();
            if (key.indexOf('LYRIC') < 0 && key.indexOf('LIRIK') < 0 && key.indexOf('UNSYNC') < 0) return '';
            if (r.next >= end) return '';
            return id3Sanitize(id3Decode(data, r.next, end, enc));
        }
        function parseSyltBytes(data, offset, size, mpegFrameMs) {
            const end = offset + size;
            if (size < 8) return '';
            const enc = data[offset];
            const tsFormat = data[offset + 4];
            // Format 2 = milidetik; format 1 = hitungan frame MPEG (dikonversi).
            if (tsFormat !== 1 && tsFormat !== 2) return '';
            const r = id3ReadTerminated(data, offset + 6, end, enc);
            let cursor = r.next;
            const entries = [];
            while (cursor < end - 1) {
                const t = id3ReadTerminated(data, cursor, end, enc);
                if (t.next + 4 > end) break;
                const rawTime = beInt32U(data, t.next);
                cursor = t.next + 4;
                const timeMs = tsFormat === 2 ? rawTime : Math.round(rawTime * (mpegFrameMs || 26.122448979591837));
                if (t.text) entries.push([timeMs, t.text]);
            }
            return id3SyltEntriesToLrc(entries);
        }
        // Durasi satu frame MPEG audio — cermin EmbeddedLyricsReader.detectMpegFrameDurationMs.
        function detectMpegFrameDurationMs(probe) {
            for (let index = 0; index + 4 <= probe.length; index++) {
                const b1 = probe[index];
                const b2 = probe[index + 1];
                if (b1 === 0xFF && (b2 & 0xE0) === 0xE0) {
                    const versionBits = (b2 >> 3) & 0x03;
                    const layerBits = (b2 >> 1) & 0x03;
                    const b3 = probe[index + 2];
                    const sampleRateIndex = (b3 >> 2) & 0x03;
                    const bitrateIndex = (b3 >> 4) & 0x0F;
                    if (versionBits !== 1 && layerBits !== 0 && sampleRateIndex !== 3 && bitrateIndex !== 0 && bitrateIndex !== 15) {
                        const rates = versionBits === 3 ? [44100, 48000, 32000] : versionBits === 2 ? [22050, 24000, 16000] : [11025, 12000, 8000];
                        const sampleRate = rates[sampleRateIndex];
                        const samplesPerFrame = layerBits === 3 ? 384 : layerBits === 2 ? 1152 : (versionBits === 3 ? 1152 : 576);
                        return samplesPerFrame * 1000 / sampleRate;
                    }
                }
            }
            return 0;
        }
        function deUnsynchroniseBytes(source) {
            const out = new Uint8Array(source.length);
            let w = 0;
            let i = 0;
            while (i < source.length) {
                const b = source[i];
                out[w++] = b;
                if (b === 0xFF && i + 1 < source.length && source[i + 1] === 0) i += 2;
                else i++;
            }
            return out.subarray(0, w);
        }

        // Penanda frame SYLT/USLT/TXXX — cermin EmbeddedLyricsReader.lirikFor sisi aplikasi
        // untuk berkas yang diimpor dari PC (blob tidak pernah dikirim ke server).
        async function extractId3LyricsFromBlob(blob) {
            try {
                const head = new Uint8Array(await blob.slice(0, 10).arrayBuffer());
                if (head.length < 10 || head[0] !== 0x49 || head[1] !== 0x44 || head[2] !== 0x33) return '';
                const version = head[3];
                if (version < 2 || version > 4) return '';
                const flags = head[5];
                const tagSize = syncInt32U(head, 6);
                if (tagSize <= 10 || tagSize > 16 * 1024 * 1024) return '';
                let tag = new Uint8Array(await blob.slice(10, 10 + tagSize).arrayBuffer());
                if ((flags & 0x80) !== 0) tag = deUnsynchroniseBytes(tag);
                // SYLT berbasis frame butuh durasi frame MPEG dari header audio sesudah tag.
                const audioProbe = new Uint8Array(await blob.slice(10 + tagSize, 10 + tagSize + 16384).arrayBuffer());
                const mpegFrameMs = detectMpegFrameDurationMs(audioProbe);

                let bodyStart = 0;
                if ((flags & 0x40) !== 0 && tag.length >= 4) {
                    bodyStart = version === 4 ? syncInt32U(tag, 0) : beInt32U(tag, 0) + 4;
                    if (bodyStart < 0 || bodyStart >= tag.length) bodyStart = 0;
                }

                const headerSize = version === 2 ? 6 : 10;
                const idLength = version === 2 ? 3 : 4;
                let synced = '';
                let plain = '';
                let offset = bodyStart;
                while (offset + headerSize <= tag.length) {
                    let frameId = '';
                    for (let i = 0; i < idLength; i++) frameId += String.fromCharCode(tag[offset + i]);
                    if (!/^[A-Z0-9]{3,4}$/.test(frameId)) break;
                    const dataStart = offset + headerSize;
                    const sizes = version === 2
                        ? [beInt24U(tag, offset + 3)]
                        : [beInt32U(tag, offset + 4), syncInt32U(tag, offset + 4)];
                    let used = 0;
                    sizes.forEach(size => {
                        if (size <= 0 || dataStart + size > tag.length) return;
                        used = Math.max(used, size);
                        try {
                            if (frameId === 'SYLT' || frameId === 'SLT') {
                                const t = parseSyltBytes(tag, dataStart, size, mpegFrameMs);
                                if (t.length > synced.length) synced = t;
                            } else if (frameId === 'USLT' || frameId === 'ULT') {
                                const t = parseUsltBytes(tag, dataStart, size);
                                if (t.length > plain.length) plain = t;
                            } else if (frameId === 'TXXX' || frameId === 'TXX') {
                                const t = parseTxxxBytes(tag, dataStart, size);
                                if (t) {
                                    if (/\[\d{1,3}:\d{1,2}/.test(t)) {
                                        if (t.length > synced.length) synced = t;
                                    } else if (t.length > plain.length) {
                                        plain = t;
                                    }
                                }
                            }
                        } catch (err) {}
                    });
                    if (used === 0) break;
                    offset = dataStart + used;
                }
                return synced || plain || '';
            } catch (err) {
                return '';
            }
        }

        function bytesIndexOf(data, needle, from, limit) {
            const end = Math.min(limit === undefined ? data.length : limit, data.length) - needle.length;
            outer: for (let i = from; i <= end; i++) {
                for (let n = 0; n < needle.length; n++) {
                    if (data[i + n] !== needle[n]) continue outer;
                }
                return i;
            }
            return -1;
        }
        function bytesFromAscii(text) {
            const out = new Uint8Array(text.length);
            for (let i = 0; i < text.length; i++) out[i] = text.charCodeAt(i) & 0xFF;
            return out;
        }
        function looksLikeSyncedLrc(text) {
            return /\[\d{1,3}:\d{1,2}/.test(text);
        }

        // FLAC: blok metadata VORBIS_COMMENT (tipe 4) berisi kunci LYRICS / UNSYNCEDLYRICS.
        async function extractFlacLyricsFromBlob(blob) {
            const buf = new Uint8Array(await blob.slice(0, 8 * 1024 * 1024).arrayBuffer());
            if (buf.length < 8) return '';
            const view = new DataView(buf.buffer, buf.byteOffset, buf.byteLength);
            let off = 4;
            let synced = '';
            let plain = '';
            while (off + 4 <= buf.length) {
                const header = buf[off];
                const isLast = (header & 0x80) !== 0;
                const type = header & 0x7F;
                const len = (buf[off + 1] << 16) | (buf[off + 2] << 8) | buf[off + 3];
                off += 4;
                if (len <= 0 || off + len > buf.length) break;
                if (type === 4) {
                    let cursor = off;
                    const end = off + len;
                    if (cursor + 4 <= end) {
                        const vendorLen = view.getUint32(cursor, true);
                        cursor += 4 + vendorLen;
                        if (cursor + 4 <= end) {
                            const count = view.getUint32(cursor, true);
                            cursor += 4;
                            for (let i = 0; i < count && cursor + 4 <= end; i++) {
                                const clen = view.getUint32(cursor, true);
                                cursor += 4;
                                if (clen <= 0 || cursor + clen > end) break;
                                const comment = new TextDecoder('utf-8').decode(buf.subarray(cursor, cursor + clen));
                                cursor += clen;
                                const eq = comment.indexOf('=');
                                if (eq <= 0) continue;
                                const key = comment.slice(0, eq).toUpperCase();
                                const val = comment.slice(eq + 1).trim();
                                if (!val) continue;
                                if (key === 'LYRICS' || key === 'SYNCEDLYRICS' || key === 'UNSYNCEDLYRICS' || key === 'UNSYNCED LYRICS') {
                                    if (looksLikeSyncedLrc(val)) { if (val.length > synced.length) synced = val; }
                                    else if (val.length > plain.length) plain = val;
                                }
                            }
                        }
                    }
                }
                off += len;
                if (isLast) break;
            }
            return synced || plain || '';
        }

        // MP4/M4A: atom (c)lyr dengan sub-atom "data" — cermin findMp4LyricAtom sisi aplikasi.
        function findMp4LyricAtomInBytes(data) {
            const needle = new Uint8Array([0xA9, 0x6C, 0x79, 0x72]); // ©lyr
            let searchFrom = 0;
            for (let hits = 0; hits < 8; hits++) {
                const index = bytesIndexOf(data, needle, searchFrom);
                if (index < 0) break;
                searchFrom = index + 4;
                const dataAtom = index + 4;
                if (dataAtom + 16 > data.length) continue;
                if (String.fromCharCode(data[dataAtom + 4], data[dataAtom + 5], data[dataAtom + 6], data[dataAtom + 7]) !== 'data') continue;
                const view = new DataView(data.buffer, data.byteOffset, data.byteLength);
                const dataSize = view.getUint32(dataAtom, false);
                const dataType = view.getUint32(dataAtom + 8, false);
                const payloadStart = dataAtom + 16;
                const payloadEnd = Math.min(data.length, dataSize >= 17 && dataAtom + dataSize <= data.length ? dataAtom + dataSize : payloadStart + 8192);
                if (payloadEnd <= payloadStart) continue;
                const slice = data.subarray(payloadStart, payloadEnd);
                let text = '';
                try {
                    text = dataType === 2 ? new TextDecoder('utf-16be').decode(slice) : new TextDecoder('utf-8').decode(slice);
                } catch (err) { continue; }
                text = id3Sanitize(text);
                if (text.length >= 8) return text;
            }
            return '';
        }

        // Ogg/Opus/FLAC mentah: komentar Vorbis "LYRICS=" / "UNSYNCEDLYRICS=".
        function findVorbisLyricCommentInBytes(data) {
            const keys = ['LYRICS=', 'UNSYNCEDLYRICS=', 'UNSYNCED LYRICS='];
            let synced = '';
            let plain = '';
            for (let k = 0; k < keys.length; k++) {
                const needle = bytesFromAscii(keys[k]);
                let searchFrom = 0;
                for (let hits = 0; hits < 4; hits++) {
                    const index = bytesIndexOf(data, needle, searchFrom);
                    if (index < 0) break;
                    const valueStart = index + needle.length;
                    searchFrom = valueStart;
                    let valueEnd = valueStart;
                    while (valueEnd < data.length && valueEnd - valueStart < 128 * 1024) {
                        const b = data[valueEnd];
                        if (b === 0 || b < 0x09) break;
                        valueEnd++;
                    }
                    let text = '';
                    try { text = new TextDecoder('utf-8').decode(data.subarray(valueStart, valueEnd)); } catch (err) { continue; }
                    text = id3Sanitize(text);
                    if (text.length < 8) continue;
                    if (looksLikeSyncedLrc(text)) { if (text.length > synced.length) synced = text; }
                    else if (text.length > plain.length) plain = text;
                }
            }
            return synced || plain || '';
        }

        // Dispatcher lirik tertanam utk berkas PC: ID3 (MP3) -> FLAC -> pindai MP4/Vorbis.
        async function extractEmbeddedLyricsFromBlob(blob) {
            try {
                if (!blob || blob.size < 16) return '';
                const head = new Uint8Array(await blob.slice(0, 8).arrayBuffer());
                if (head[0] === 0x49 && head[1] === 0x44 && head[2] === 0x33) {
                    return await extractId3LyricsFromBlob(blob);
                }
                if (head[0] === 0x66 && head[1] === 0x4C && head[2] === 0x61 && head[3] === 0x43) { // "fLaC"
                    const flac = await extractFlacLyricsFromBlob(blob);
                    if (flac) return flac;
                }
                const scan = new Uint8Array(await blob.slice(0, 4 * 1024 * 1024).arrayBuffer());
                return findMp4LyricAtomInBytes(scan) || findVorbisLyricCommentInBytes(scan) || '';
            } catch (err) {
                return '';
            }
        }

        // --- SAMPUL ALBUM UNTUK BERKAS PC (blob tidak pernah dikirim ke server) ---
        function coverDataUrlFromBytes(bytes) {
            if (!bytes || bytes.length < 512) return null;
            const isPng = bytes[0] === 0x89 && bytes[1] === 0x50 && bytes[2] === 0x4E && bytes[3] === 0x47;
            const isJpeg = bytes[0] === 0xFF && bytes[1] === 0xD8;
            if (!isPng && !isJpeg) return null;
            const maxBytes = 4 * 1024 * 1024;
            const slice = bytes.length > maxBytes ? bytes.subarray(0, maxBytes) : bytes;
            let binary = '';
            const chunk = 0x8000;
            for (let i = 0; i < slice.length; i += chunk) {
                binary += String.fromCharCode.apply(null, slice.subarray(i, i + chunk));
            }
            try {
                return 'data:' + (isPng ? 'image/png' : 'image/jpeg') + ';base64,' + btoa(binary);
            } catch (err) {
                return null;
            }
        }

        // APIC (ID3v2.3/2.4) atau PIC (ID3v2.2): encoding, MIME/format, tipe, deskripsi, gambar.
        function parseApicFrameBytes(data, start, size, isV22) {
            const end = start + size;
            if (start >= end) return null;
            const encoding = data[start];
            let p = start + 1;
            if (isV22) {
                p += 3;
            } else {
                while (p < end && data[p] !== 0) p++;
                p++;
            }
            p++;
            if (encoding === 1 || encoding === 2) {
                while (p + 1 < end && !(data[p] === 0 && data[p + 1] === 0)) p += 2;
                p += 2;
            } else {
                while (p < end && data[p] !== 0) p++;
                p++;
            }
            if (p >= end) return null;
            return data.subarray(p, end);
        }

        async function extractEmbeddedCoverFromBlob(blob) {
            try {
                if (!blob || blob.size < 1024) return null;
                const head = new Uint8Array(await blob.slice(0, 10).arrayBuffer());
                if (head.length < 10) return null;

                if (head[0] === 0x49 && head[1] === 0x44 && head[2] === 0x33) {
                    const version = head[3];
                    const flags = head[5];
                    const tagSize = syncInt32U(head, 6);
                    if (version >= 2 && version <= 4 && tagSize > 10 && tagSize < 32 * 1024 * 1024) {
                        let tag = new Uint8Array(await blob.slice(10, 10 + tagSize).arrayBuffer());
                        if ((flags & 0x80) !== 0) tag = deUnsynchroniseBytes(tag);
                        let bodyStart = 0;
                        if ((flags & 0x40) !== 0 && tag.length >= 4) {
                            bodyStart = version === 4 ? syncInt32U(tag, 0) : beInt32U(tag, 0) + 4;
                            if (bodyStart < 0 || bodyStart >= tag.length) bodyStart = 0;
                        }
                        const headerSize = version === 2 ? 6 : 10;
                        const idLength = version === 2 ? 3 : 4;
                        let offset = bodyStart;
                        while (offset + headerSize <= tag.length) {
                            let frameId = '';
                            for (let i = 0; i < idLength; i++) frameId += String.fromCharCode(tag[offset + i]);
                            if (!/^[A-Z0-9]{3,4}$/.test(frameId)) break;
                            const dataStart = offset + headerSize;
                            const sizes = version === 2
                                ? [beInt24U(tag, offset + 3)]
                                : [beInt32U(tag, offset + 4), syncInt32U(tag, offset + 4)];
                            let used = 0;
                            for (let s = 0; s < sizes.length; s++) {
                                const size = sizes[s];
                                if (size <= 0 || dataStart + size > tag.length) continue;
                                used = Math.max(used, size);
                                if (frameId === 'APIC' || frameId === 'PIC') {
                                    const img = parseApicFrameBytes(tag, dataStart, size, frameId === 'PIC');
                                    const url = img ? coverDataUrlFromBytes(img) : null;
                                    if (url) return url;
                                }
                            }
                            if (used === 0) break;
                            offset = dataStart + used;
                        }
                    }
                }

                if (head[0] === 0x66 && head[1] === 0x4C && head[2] === 0x61 && head[3] === 0x43) {
                    const buf = new Uint8Array(await blob.slice(0, 8 * 1024 * 1024).arrayBuffer());
                    let off = 4;
                    while (off + 4 <= buf.length) {
                        const blockHeader = buf[off];
                        const isLast = (blockHeader & 0x80) !== 0;
                        const type = blockHeader & 0x7F;
                        const len = (buf[off + 1] << 16) | (buf[off + 2] << 8) | buf[off + 3];
                        off += 4;
                        if (len <= 0 || off + len > buf.length) break;
                        if (type === 6) {
                            let p = off + 4;
                            if (p + 4 <= off + len) {
                                const mimeLen = (buf[p] << 24) | (buf[p + 1] << 16) | (buf[p + 2] << 8) | buf[p + 3];
                                p += 4 + mimeLen;
                                if (p + 4 <= off + len) {
                                    const descLen = (buf[p] << 24) | (buf[p + 1] << 16) | (buf[p + 2] << 8) | buf[p + 3];
                                    // lewati deskripsi, 4 field 32-bit (w/h/depth/colors), dan panjang data
                                    p += 4 + descLen + 16 + 4;
                                    if (p < off + len) {
                                        const url = coverDataUrlFromBytes(buf.subarray(p, off + len));
                                        if (url) return url;
                                    }
                                }
                            }
                        }
                        off += len;
                        if (isLast) break;
                    }
                }

                // MP4/M4A: atom covr -> anak atom data -> payload gambar.
                const whole = new Uint8Array(await blob.slice(0, 8 * 1024 * 1024).arrayBuffer());
                const covr = bytesIndexOf(whole, bytesFromAscii('covr'), 0);
                if (covr >= 4) {
                    const dataAtom = bytesIndexOf(whole, bytesFromAscii('data'), covr, Math.min(whole.length, covr + 64));
                    if (dataAtom >= 4 && dataAtom + 12 <= whole.length) {
                        const view = new DataView(whole.buffer, whole.byteOffset, whole.byteLength);
                        const atomStart = dataAtom - 4;
                        const atomSize = view.getUint32(atomStart, false);
                        const payloadStart = dataAtom + 12;
                        const payloadEnd = Math.min(whole.length, atomStart + atomSize);
                        if (atomSize > 16 && payloadStart < payloadEnd) {
                            const url = coverDataUrlFromBytes(whole.subarray(payloadStart, payloadEnd));
                            if (url) return url;
                        }
                    }
                }
                return null;
            } catch (err) {
                return null;
            }
        }

        function isCoverFileName(name) {
            const lower = String(name || '').toLowerCase();
            const dot = lower.lastIndexOf('.');
            if (dot <= 0) return false;
            const base = lower.substring(0, dot);
            const extension = lower.substring(dot + 1);
            if (['jpg', 'jpeg', 'png', 'webp'].indexOf(extension) < 0) return false;
            return ['cover', 'folder', 'album', 'front', 'artwork', 'albumart'].indexOf(base) >= 0;
        }

        function relativeFolderOf(file) {
            const rel = file && file.webkitRelativePath ? file.webkitRelativePath : '';
            const slash = rel.lastIndexOf('/');
            return slash > 0 ? rel.substring(0, slash) : '';
        }

        function fileToDataUrl(file) {
            return new Promise(resolve => {
                const reader = new FileReader();
                reader.onload = () => resolve(typeof reader.result === 'string' ? reader.result : null);
                reader.onerror = () => resolve(null);
                reader.readAsDataURL(file);
            });
        }

        async function applyImportedAlbumCovers(tracks, coverFileByTrackId) {
            let changed = false;
            for (let i = 0; i < tracks.length; i++) {
                const track = tracks[i];
                if (window.musicAutoAlbumArts[track.id]) continue;

                // Tag sampul tertanam khusus satu trek.
                if (track.blob) {
                    const embedded = await extractEmbeddedCoverFromBlob(track.blob);
                    if (embedded) {
                        window.musicAutoAlbumArts[track.id] = embedded;
                        persistCoverToDb(track.id, embedded);
                        changed = true;
                        continue;
                    }
                }

                const coverFile = coverFileByTrackId ? coverFileByTrackId[track.id] : null;
                if (!coverFile) continue;

                if (!track.relFolder) {
                    // Berkas tunggal tanpa folder: sampul hanya berlaku untuk trek ini.
                    const singleUrl = await fileToDataUrl(coverFile);
                    if (singleUrl) {
                        window.musicAutoAlbumArts[track.id] = singleUrl;
                        persistCoverToDb(track.id, singleUrl);
                        changed = true;
                    }
                    continue;
                }

                // cover.jpg satu folder dipakai semua lagu di folder itu: simpan sekali
                // dengan kunci folder agar IndexedDB tidak menyimpan salinan per trek.
                const folderKey = 'folder:' + track.relFolder;
                if (!window.musicAutoAlbumArts[folderKey]) {
                    const dataUrl = await fileToDataUrl(coverFile);
                    if (dataUrl) {
                        window.musicAutoAlbumArts[folderKey] = dataUrl;
                        persistCoverToDb(folderKey, dataUrl);
                        changed = true;
                    }
                }
            }
            if (changed) refreshMusicDeckImages();
        }


        function persistTrackLyricsToDb(trackId, text) {
            openMusicDatabase().then(db => {
                if (!db) return;
                try {
                    db.transaction('lyrics', 'readwrite').objectStore('lyrics').put({ id: trackId, text: text });
                } catch (e) {}
            });
        }

        // Sampul otomatis disimpan terpisah dari sampul pilihan pengguna (localStorage).
        function persistCoverToDb(trackId, dataUrl) {
            openMusicDatabase().then(db => {
                if (!db) return;
                try {
                    db.transaction('covers', 'readwrite').objectStore('covers').put({ id: trackId, dataUrl: dataUrl });
                } catch (e) {}
            });
        }

        function refreshMusicDeckImages() {
            if (window.musicIsPlayerExpanded) refreshMusicDeck();
            syncMusicDockState();
            if (typeof currentTab !== 'undefined' && currentTab === 'musik') {
                renderActiveMusicTabContent();
            }
        }

        function refreshLyricsViews() {
            const card = document.getElementById('musicCentralArtCard');
            const currentTrack = window.musicPlaylist[window.currentMusicIndex];
            if (card && currentTrack && window.musicIsLyricsMode && !window.musicLyricsEditMode) {
                card.innerHTML = getCentralArtCardInnerHtml(currentTrack);
            }
            if (window.musicIsLyricsFullScreen && currentTrack) {
                renderLyricsFullScreen();
            }
            syncLyricsContainers();
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
                window.musicPhoneError = window.musicPhonePermission ? '' : 'Izin baca audio di HP belum diberikan. Buka aplikasi My Singghara di HP, izinkan "Musik dan audio", lalu tekan Muat Musik HP.';
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
                if (!window.musicPhonePermission) schedulePhonePermissionRecheck();
                if (typeof currentTab !== 'undefined' && currentTab === 'musik') loadMusik();
            }
        }

        // Izin bisa diberikan di HP setelah halaman ini terbuka. Periksa ulang berkala
        // supaya pesan "belum diberikan" tidak tertinggal padahal izin sudah aktif.
        let _musicPhoneRecheckTimer = null;
        function schedulePhonePermissionRecheck() {
            if (_musicPhoneRecheckTimer) return;
            let attempts = 0;
            _musicPhoneRecheckTimer = setInterval(() => {
                attempts++;
                const onMusicTab = typeof currentTab !== 'undefined' && currentTab === 'musik';
                if (window.musicPhonePermission || attempts > 12 || !onMusicTab) {
                    clearInterval(_musicPhoneRecheckTimer);
                    _musicPhoneRecheckTimer = null;
                    return;
                }
                loadPhoneMusicLibrary(false);
            }, 5000);
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
            ensureMusicShellMounted();
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
                coverBox.innerHTML = musicCoverImgHtml(src);
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
            html += '    <input type="range" class="music-dock-slider music-seek-slider" id="dockSeekSlider" min="0" max="' + dur + '" value="' + pos + '" style="--seek-pct:' + (dur > 0 ? Math.min(100, pos * 100 / dur).toFixed(1) : 0) + '%" oninput="handleMusicSeekInput(this.value); this.style.setProperty(\'--seek-pct\', (this.value / Math.max(1, this.max) * 100) + \'%\')" onchange="handleMusicSeekChange(this.value)">';
            html += '    <span class="music-dur-time-text music-dock-time">' + formatAudioTime(dur) + '</span>';
            html += '  </div>';
            html += '  <div class="music-dock-main-row">';
            html += '    <div class="music-dock-cover" id="dockCoverArt" onclick="expandMusicPlayerDeck()" title="Buka pemutar penuh">' +
                musicCoverImgHtml(src) + '</div>';
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
            html += '      <button class="music-dock-btn music-repeat-btn" onclick="cycleRepeatMode()" title="Pengulangan">' + musicRepeatIconHtml(window.musicRepeatMode, 16, window.musicRepeatMode === 'NONE' ? 'text-secondary' : 'text-primary') + '</button>';
            html += '      <button class="music-dock-btn" id="dockFavoriteBtn" onclick="toggleTrackFavorite(\'' + track.id + '\', event)" title="Favorit"><i class="bi bi-heart text-secondary"></i></button>';
            html += '      <button class="music-dock-btn" onclick="openMusicDeckWithLyrics()" title="Lirik"><i class="bi bi-text-center"></i></button>';
            html += '      <button class="music-dock-btn" onclick="openMusicQueueModal()" title="Antrean Lagu"><i class="bi bi-music-note-list"></i></button>';
            html += '      <button class="music-dock-btn music-dock-optional" onclick="downloadMusicTrack(\'' + track.id + '\')" title="Unduh ke PC"><i class="bi bi-download"></i></button>';
            html += '      <input type="range" id="dockVolumeSlider" class="music-dock-volume" min="0" max="1" step="0.01" value="' + window.musicPlayerAudio.volume + '" style="--seek-pct:' + Math.round(window.musicPlayerAudio.volume * 100) + '%" oninput="setMusicVolume(this.value); this.style.setProperty(\'--seek-pct\', (this.value * 100) + \'%\')" title="Volume">';
            html += '      <button class="music-dock-btn music-dock-optional" onclick="expandMusicPlayerDeck()" title="Perbesar"><i class="bi bi-arrows-angle-expand"></i></button>';
            html += '      <button class="music-dock-btn text-danger" onclick="stopMusicPlayback()" title="Hentikan Putar"><i class="bi bi-x-lg"></i></button>';
            html += '    </div>';
            html += '  </div>';
            html += '</div>';
            return html;
        }

        function getMusicDockCss() {
            return '' +
                '.music-global-dock{position:fixed;left:var(--sidebar-width,0px);right:0;bottom:0;z-index:1050;padding:0 16px 12px;pointer-events:none;transition:left .3s cubic-bezier(0.4, 0, 0.2, 1);}' +
                'body.sidebar-collapsed .music-global-dock{left:var(--sidebar-collapsed-width,72px);}' +
                '.music-global-dock .music-dock-card{pointer-events:auto;background:var(--card-bg,#fff);border:1px solid var(--border-color,#dee2e6);border-radius:16px;box-shadow:0 -6px 24px rgba(0,0,0,.18);padding:8px 12px;}' +
                'body.has-music-dock .content-wrapper{padding-bottom:132px;}' +
                '.music-dock-progress-row{display:flex;align-items:center;gap:10px;margin-bottom:2px;}' +
                '.music-dock-time{font-size:10.5px;color:var(--text-sub);min-width:38px;text-align:center;}' +
                '.music-dock-slider{flex:1;}' +
                '.music-dock-main-row{display:flex;align-items:center;gap:12px;}' +
                '.music-dock-cover{width:46px;height:46px;min-width:46px;border-radius:10px;overflow:hidden;background:linear-gradient(135deg,#1f2937,#111827);display:flex;align-items:center;justify-content:center;cursor:pointer;}' +
                '.music-dock-meta{flex:1;min-width:0;cursor:pointer;}' +
                '.music-dock-title{font-size:13px;font-weight:700;color:var(--text-main);}' +
                '.music-dock-artist{font-size:11px;color:var(--text-sub);}' +
                '.music-dock-lyric{font-size:11px;color:var(--brand-green);font-weight:600;min-height:14px;}' +
                '.music-dock-controls{display:flex;align-items:center;gap:2px;flex-shrink:0;}' +
                '.music-dock-btn{position:relative;background:transparent;border:0;color:var(--text-sub);width:30px;height:30px;border-radius:8px;display:flex;align-items:center;justify-content:center;font-size:16px;cursor:pointer;transition:background .15s ease,color .15s ease;}' +
                '.music-dock-btn:hover{background:var(--surface-hover);color:var(--text-main);}' +
                '.music-dock-btn.is-active{color:var(--brand-green);}' +
                '.music-dock-btn.is-active:hover{color:var(--brand-green-hover);}' +
                '.music-dock-play-btn{background:var(--brand-green);border:0;color:#fff;width:42px;height:42px;border-radius:50%;display:flex;align-items:center;justify-content:center;cursor:pointer;transition:filter .15s ease,transform .1s ease;}' +
                '.music-dock-play-btn:hover{filter:brightness(1.08);}' +
                '.music-dock-play-btn:active{transform:scale(.94);}' +
                '.music-dock-volume{width:70px;--seek-pct:100%;}' +
                '.music-surface{background:var(--surface-subtle);border:1px solid var(--border-color);}' +
                // --- LIVE LYRICS: baris, sorotan kata karaoke, chip, badge, stepper ---
                '.lyrics-viewport{position:relative;min-height:0;}' +
                '.lyrics-scroll{position:relative;-webkit-mask-image:linear-gradient(to bottom,transparent 0,#000 14%,#000 86%,transparent 100%);mask-image:linear-gradient(to bottom,transparent 0,#000 14%,#000 86%,transparent 100%);scrollbar-width:thin;scrollbar-color:var(--brand-green) transparent;}' +
                '.lyrics-scroll::-webkit-scrollbar{width:8px;height:8px;}' +
                '.lyrics-scroll::-webkit-scrollbar-track{background:transparent;}' +
                '.lyrics-scroll::-webkit-scrollbar-thumb{background:var(--brand-green);border-radius:99px;}' +
                '.lyrics-pad{width:100%;pointer-events:none;}' +
                '.lrc-line-item{padding:10px 16px;text-align:center;font-size:14px;color:var(--text-sub);cursor:pointer;border-radius:10px;opacity:.45;transform:scale(.85);transform-origin:center;line-height:1.55;transition:transform .45s cubic-bezier(.34,1.56,.64,1),font-size .35s ease,opacity .35s ease,color .35s ease,background .35s ease;}' +
                '.lrc-line-item:hover{opacity:.8;}' +
                '.lrc-line-item.past-lrc-line{opacity:.72;}' +
                '.lrc-line-item.active-lrc-line{font-size:17px;font-weight:800;color:var(--brand-green);opacity:1;transform:scale(1.08);background:var(--badge-bg);letter-spacing:.2px;}' +
                '.lrc-line-item.lrc-line-static{opacity:.9;transform:none;cursor:default;}' +
                '.lrc-word{display:inline-block;transition:transform .15s ease;}' +
                '.active-lrc-line .lrc-word{background:linear-gradient(90deg,var(--lyric-sung,var(--brand-green)) var(--fill,0%),var(--lyric-unsung,var(--text-sub)) var(--fill,0%));-webkit-background-clip:text;background-clip:text;-webkit-text-fill-color:transparent;color:transparent;}' +
                '.active-lrc-line .lrc-word.singing-now{transform:translateY(-1.5px) scale(1.05);filter:drop-shadow(0 0 8px var(--lyric-glow,rgba(56,189,248,.45)));}' +
                '@supports not (background-clip:text){.active-lrc-line .lrc-word{color:var(--lyric-unsung,var(--text-sub));}.active-lrc-line .lrc-word.singing-now{color:var(--lyric-sung,var(--brand-green));}}' +
                '.lyrics-resume-chip{position:absolute;left:50%;bottom:10px;transform:translateX(-50%);z-index:5;border:1px solid var(--border-color);background:var(--card-bg,#fff);color:var(--brand-green);font-size:11px;font-weight:700;border-radius:99px;padding:5px 14px;cursor:pointer;box-shadow:0 4px 14px rgba(0,0,0,.18);display:flex;align-items:center;gap:6px;white-space:nowrap;}' +
                '.lyrics-resume-chip:hover{filter:brightness(1.06);}' +
                '.lyrics-sync-badge{display:inline-flex;align-items:center;gap:4px;font-size:9px;font-weight:800;letter-spacing:.4px;text-transform:uppercase;border-radius:99px;padding:3px 8px;background:var(--badge-bg);color:var(--brand-green);border:1px solid var(--border-color);white-space:nowrap;}' +
                '.lyrics-offset-stepper{display:inline-flex;align-items:center;gap:2px;background:var(--surface-subtle);border:1px solid var(--border-color);border-radius:99px;padding:2px 4px;}' +
                '.lyrics-offset-btn{border:0;background:transparent;color:var(--text-sub);width:22px;height:22px;border-radius:50%;font-size:13px;font-weight:800;cursor:pointer;display:inline-flex;align-items:center;justify-content:center;line-height:1;}' +
                '.lyrics-offset-btn:hover{background:var(--surface-hover);color:var(--brand-green);}' +
                '.lyrics-offset-label{font-size:10px;font-weight:700;color:var(--text-sub);min-width:38px;text-align:center;font-variant-numeric:tabular-nums;}' +
                '.lyrics-offset-label.is-shifted{color:var(--brand-green);}' +
                // Semua warna layar lirik memakai token tema (body.dark ikut berubah
                // otomatis saat pengguna mengganti mode terang/gelap).
                'body.music-lyrics-fs-open{overflow:hidden;}' +
                '.music-lyrics-fullscreen{position:fixed;inset:0;z-index:1080;display:flex;' +
                    'background:var(--mlfs-bg,#f1f5f9);' +
                    '--mlfs-bg:#f1f5f9;' +
                    '--mlfs-scrim:radial-gradient(120% 90% at 50% 0%,rgba(255,255,255,.72) 0%,rgba(241,245,249,.9) 60%,rgba(241,245,249,.97) 100%);' +
                    '--mlfs-bg-filter:blur(52px) saturate(1.15) brightness(1.04);' +
                    '--mlfs-btn-bg:rgba(255,255,255,.72);' +
                    '--mlfs-btn-fg:var(--text-main);' +
                    '--mlfs-play-fg:#ffffff;' +
                    '--lyric-sung:var(--brand-green);' +
                    '--lyric-unsung:rgba(71,85,105,.42);' +
                    '--lyric-glow:rgba(54,116,181,.35);}' +
                'body.dark .music-lyrics-fullscreen,[data-theme="dark"] .music-lyrics-fullscreen{' +
                    'background:#050b18;' +
                    '--mlfs-bg:#050b18;' +
                    '--mlfs-scrim:radial-gradient(120% 90% at 50% 0%,rgba(8,47,73,.55) 0%,rgba(2,6,23,.88) 60%,rgba(2,6,23,.96) 100%);' +
                    '--mlfs-bg-filter:blur(52px) saturate(1.3) brightness(.5);' +
                    '--mlfs-btn-bg:rgba(15,23,42,.5);' +
                    '--mlfs-btn-fg:#e2e8f0;' +
                    '--mlfs-play-fg:#04121f;' +
                    '--lyric-sung:var(--brand-green);' +
                    '--lyric-unsung:rgba(226,232,240,.38);' +
                    '--lyric-glow:rgba(56,189,248,.5);}' +
                '.mlfs-bg{position:absolute;inset:-60px;background-size:cover;background-position:center;filter:var(--mlfs-bg-filter);transform:scale(1.15);}' +
                '.mlfs-scrim{position:absolute;inset:0;background:var(--mlfs-scrim);}' +
                '.mlfs-content{position:relative;z-index:2;display:flex;flex-direction:column;width:100%;max-width:860px;margin:0 auto;padding:18px 20px 16px;height:100%;min-height:0;}' +
                '.mlfs-header{display:flex;align-items:flex-start;justify-content:space-between;gap:12px;margin-bottom:6px;}' +
                '.mlfs-eyebrow{font-size:10px;font-weight:800;letter-spacing:2px;color:var(--brand-green);margin-bottom:4px;}' +
                '.mlfs-title{font-size:clamp(18px,3vh,26px);font-weight:800;color:var(--text-main);}' +
                '.mlfs-artist{font-size:13px;color:var(--text-sub);}' +
                '.mlfs-icon-btn{border:1px solid var(--border-color);background:var(--mlfs-btn-bg);color:var(--mlfs-btn-fg);width:38px;height:38px;min-width:38px;border-radius:50%;display:inline-flex;align-items:center;justify-content:center;font-size:16px;cursor:pointer;transition:border-color .15s ease,color .15s ease;}' +
                '.mlfs-icon-btn:hover{border-color:var(--brand-green);color:var(--brand-green);}' +
                '.music-lyrics-fullscreen .lyrics-sync-badge{background:var(--badge-bg);color:var(--brand-green);border-color:var(--border-color);}' +
                '.music-lyrics-fullscreen .lrc-line-item{color:var(--text-sub);font-size:clamp(16px,2.6vh,22px);padding:12px 18px;}' +
                '.music-lyrics-fullscreen .lrc-line-item.active-lrc-line{font-size:clamp(20px,3.6vh,32px);color:var(--text-main);background:var(--badge-bg);transform:scale(1.04);}' +
                '.music-lyrics-fullscreen .lrc-line-item.past-lrc-line{opacity:.75;}' +
                '.music-lyrics-fullscreen .lyrics-scroll{scrollbar-color:var(--brand-green) transparent;}' +
                '.music-lyrics-fullscreen .lyrics-scroll::-webkit-scrollbar-thumb{background:var(--brand-green);}' +
                '.music-lyrics-fullscreen .lyrics-resume-chip{background:var(--mlfs-btn-bg);color:var(--brand-green);border-color:var(--border-color);}' +
                '.mlfs-footer{flex:0 0 auto;padding-top:8px;}' +
                '.mlfs-time{font-size:11px;color:var(--text-sub);min-width:40px;text-align:center;font-variant-numeric:tabular-nums;}' +
                '.mlfs-play-btn{width:56px;height:56px;border-radius:50%;border:0;background:var(--brand-green);color:var(--mlfs-play-fg);font-size:26px;display:inline-flex;align-items:center;justify-content:center;cursor:pointer;box-shadow:0 6px 24px var(--lyric-glow);transition:transform .1s ease;}' +
                '.mlfs-play-btn:active{transform:scale(.93);}' +
                '.music-lyrics-fullscreen .lyrics-offset-stepper{background:var(--mlfs-btn-bg);border-color:var(--border-color);}' +
                '.music-lyrics-fullscreen .lyrics-offset-label{color:var(--text-sub);}' +
                '.music-lyrics-fullscreen .lyrics-offset-label.is-shifted{color:var(--brand-green);}' +
                '.music-lyrics-fullscreen .lyrics-offset-btn{color:var(--text-sub);}' +
                '.music-lyrics-fullscreen .lyrics-offset-btn:hover{color:var(--brand-green);background:var(--badge-bg);}' +
                '.music-lyrics-fullscreen .music-seek-slider::-webkit-slider-runnable-track{background:linear-gradient(to right,var(--brand-green) var(--seek-pct,0%),var(--surface-hover) var(--seek-pct,0%));}' +
                '.music-lyrics-fullscreen .music-seek-slider::-webkit-slider-thumb{background:var(--brand-green);}' +
                '.music-lyrics-fullscreen .music-seek-slider::-moz-range-progress{background:var(--brand-green);}' +
                '.music-lyrics-fullscreen .music-seek-slider::-moz-range-thumb{background:var(--brand-green);}' +
                '.music-lyrics-fullscreen .music-cur-time-text{color:var(--brand-green);}' +
                '.music-seek-slider,.music-dock-volume{-webkit-appearance:none;appearance:none;background:transparent;height:20px;cursor:pointer;}' +
                '.music-seek-slider::-webkit-slider-runnable-track,.music-dock-volume::-webkit-slider-runnable-track{height:6px;border-radius:99px;background:linear-gradient(to right,var(--brand-green) var(--seek-pct,0%),var(--surface-hover) var(--seek-pct,0%));}' +
                '.music-seek-slider::-webkit-slider-thumb,.music-dock-volume::-webkit-slider-thumb{-webkit-appearance:none;appearance:none;width:14px;height:14px;margin-top:-4px;border:0;border-radius:50%;background:var(--brand-green);box-shadow:0 1px 4px rgba(0,0,0,.35);}' +
                '.music-seek-slider::-moz-range-track,.music-dock-volume::-moz-range-track{height:6px;border-radius:99px;background:var(--surface-hover);}' +
                '.music-seek-slider::-moz-range-progress,.music-dock-volume::-moz-range-progress{height:6px;border-radius:99px;background:var(--brand-green);}' +
                '.music-seek-slider::-moz-range-thumb,.music-dock-volume::-moz-range-thumb{width:14px;height:14px;border:0;border-radius:50%;background:var(--brand-green);}' +
                '.music-now-playing-page{position:fixed;top:64px;left:var(--sidebar-width,0px);right:0;bottom:0;z-index:1015;background:var(--bg-sidebar,#f8fafc);color:var(--text-main);overflow:hidden;transition:left .3s cubic-bezier(0.4, 0, 0.2, 1);display:none;}' +
                'body.sidebar-collapsed .music-now-playing-page{left:var(--sidebar-collapsed-width,72px);}' +
                '.music-now-playing-inner{max-width:480px;height:100%;margin:0 auto;display:flex;flex-direction:column;justify-content:space-between;padding:10px 14px 14px;overflow:hidden;}' +
                '.np-center{flex:1 1 auto;min-height:0;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:clamp(8px,2.2vh,20px);}' +
                '.np-bottom{flex:0 0 auto;}' +
                '.np-art-card{width:min(380px,90vw,48vh);aspect-ratio:1/1;flex:0 1 auto;min-height:0;}' +
                '.np-card-minimize{position:absolute;left:0;right:0;bottom:0;padding:10px 14px 12px;text-align:center;font-size:12px;font-weight:800;letter-spacing:1.2px;text-transform:uppercase;color:#fff;background:linear-gradient(to top,rgba(0,0,0,.65),rgba(0,0,0,0));cursor:pointer;user-select:none;z-index:6;}' +
                '.np-card-minimize:active{filter:brightness(.85);}' +
                '.np-minimize-lyrics{left:auto;right:8px;bottom:6px;width:30px;height:30px;padding:0;display:flex;align-items:center;justify-content:center;border-radius:50%;background:var(--surface-subtle);border:1px solid var(--border-color);color:var(--text-sub);font-size:16px;letter-spacing:0;text-transform:none;backdrop-filter:blur(4px);}' +
                '.np-minimize-lyrics:hover{color:var(--brand-green);border-color:var(--brand-green);}' +
                '@media (hover:hover) and (pointer:fine){' +
                '.music-now-playing-page,.music-now-playing-page *{scrollbar-width:thin;scrollbar-color:transparent transparent;scrollbar-gutter:stable;transition:scrollbar-color .25s ease;}' +
                '.music-now-playing-page:hover,.music-now-playing-page *:hover{scrollbar-color:var(--brand-green) transparent;}' +
                '.music-now-playing-page::-webkit-scrollbar,.music-now-playing-page *::-webkit-scrollbar{width:10px;height:10px;background:transparent;}' +
                '.music-now-playing-page::-webkit-scrollbar-thumb,.music-now-playing-page *::-webkit-scrollbar-thumb{background:transparent;border-radius:99px;border:2px solid transparent;background-clip:content-box;transition:background-color .25s ease;}' +
                '.music-now-playing-page:hover::-webkit-scrollbar-thumb,.music-now-playing-page *:hover::-webkit-scrollbar-thumb,.music-now-playing-page:active::-webkit-scrollbar-thumb,.music-now-playing-page *:active::-webkit-scrollbar-thumb{background-color:var(--brand-green);}' +
                '.music-now-playing-page::-webkit-scrollbar-corner,.music-now-playing-page *::-webkit-scrollbar-corner{background:transparent;}' +
                '}' +
                '@media (max-width:900px){.music-dock-volume{display:none;}.music-dock-lyric{display:none;}.music-dock-optional{display:none;}}' +
                '@media (max-width:768px){' +
                '.music-global-dock{left:0 !important;padding:0 8px 8px;}' +
                '.music-now-playing-page{top:56px;left:0 !important;}' +
                'body.sidebar-mobile-open .music-global-dock{display:none;}' +
                '.mlfs-content{padding:12px 12px 10px;}' +
                '.mlfs-title{font-size:18px;}' +
                '.mlfs-icon-btn{width:34px;height:34px;min-width:34px;font-size:14px;}' +
                '.mlfs-controls{gap:8px !important;}' +
                '}';
        }

        function updateDockLyricLine(currentSec) {
            const el = document.getElementById('dockLyricLine');
            if (!el) return;
            const track = window.musicPlaylist[window.currentMusicIndex];
            if (!track) { el.innerText = ''; return; }
            const entry = getParsedLyricsForTrack(track);
            const lines = entry.lines;
            if (!lines.length || entry.info.kind === 'static') {
                el.innerText = track.folder || (track.origin === 'phone' ? 'Berkas HP' : 'Berkas PC');
                return;
            }
            const t = (currentSec || 0) + getLyricsOffsetSec(track);
            let active = '';
            for (let i = 0; i < lines.length; i++) {
                if (lines[i].timeSec <= t) active = lines[i].text; else break;
            }
            el.innerText = active || (lines[0] ? lines[0].text : '');
        }

        function stopMusicPlayback() {
            stopLyricsClock();
            closeLyricsFullScreen();
            window._musicLrcCache = null;
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

        // --- PARSING & LIVE LYRICS ENGINE (LRC / ENHANCED LRC COMPLIANT) ---
        // Mendukung: [mm:ss.xx] per baris, <mm:ss.xx> per kata (Enhanced LRC / keluaran
        // SYLT), tag [offset:±ms], lirik statis tanpa timestamp, dan geser timing manual.
        function parseLrcLyrics(lrcText) {
            if (!lrcText || !String(lrcText).trim()) return [];
            const source = String(lrcText);
            const rawLines = source.split(/\r?\n/);
            let fileOffsetMs = 0;
            const offsetMatch = /\[offset\s*:\s*([+-]?\d+)\s*\]/i.exec(source);
            if (offsetMatch) fileOffsetMs = parseInt(offsetMatch[1], 10) || 0;
            const metaReg = /^\[(ti|ar|al|au|by|offset|length|re|ve|tool|#)\s*:/i;
            const timeReg = /\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?\]/g;
            const wordReg = /<(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?>/g;
            const stampToSec = (mm, ss, frac) => {
                const ms = parseInt(String(frac || '0').padEnd(3, '0').slice(0, 3), 10);
                return parseInt(mm, 10) * 60 + parseInt(ss, 10) + ms / 1000;
            };
            const result = [];
            const plainLines = [];

            rawLines.forEach(raw => {
                const trimmed = raw.trim();
                if (!trimmed || metaReg.test(trimmed)) return;
                timeReg.lastIndex = 0;
                const stamps = [];
                let m;
                while ((m = timeReg.exec(trimmed)) !== null) stamps.push(stampToSec(m[1], m[2], m[3]));
                wordReg.lastIndex = 0;
                const wordStamps = [];
                while ((m = wordReg.exec(trimmed)) !== null) {
                    wordStamps.push({ timeSec: stampToSec(m[1], m[2], m[3]), from: m.index + m[0].length, to: m.index });
                }
                const text = trimmed.replace(timeReg, '').replace(wordReg, '').replace(/\s+/g, ' ').trim();
                let words = null;
                if (wordStamps.length) {
                    words = wordStamps.map((t, i) => {
                        const nextTo = i + 1 < wordStamps.length ? wordStamps[i + 1].to : trimmed.length;
                        const wText = trimmed.slice(t.from, nextTo).replace(timeReg, '').replace(wordReg, '').replace(/\s+/g, ' ').trim();
                        return { timeSec: t.timeSec, endSec: t.timeSec + 1, text: wText };
                    }).filter(w => w.text.length > 0);
                    if (!words.length) words = null;
                }
                if (!stamps.length) {
                    if (text) plainLines.push(text);
                    return;
                }
                stamps.forEach(sec => {
                    result.push({ timeMs: Math.round(sec * 1000), timeSec: sec, text: text || '♪', words: words });
                });
            });

            // Tanpa timestamp sama sekali = lirik statis (mis. USLT): tampil apa adanya,
            // tanpa sorotan palsu yang bergeser tidak sesuai lagu.
            if (result.length === 0) {
                return plainLines.map(t => ({ timeMs: null, timeSec: null, text: t, words: null, unsynced: true }));
            }

            result.sort((a, b) => a.timeMs - b.timeMs);
            if (fileOffsetMs) {
                const shiftSec = fileOffsetMs / 1000;
                result.forEach(line => {
                    line.timeMs += fileOffsetMs;
                    line.timeSec = line.timeMs / 1000;
                    if (line.words) line.words.forEach(w => { w.timeSec += shiftSec; });
                });
            }
            // Akhir tiap kata = awal kata berikutnya (kata terakhir dibatasi baris berikut).
            for (let i = 0; i < result.length; i++) {
                const line = result[i];
                if (!line.words) continue;
                const lineEnd = i + 1 < result.length ? result[i + 1].timeSec : line.timeSec + 6;
                line.words.forEach((w, wi) => {
                    const nextStart = wi + 1 < line.words.length ? line.words[wi + 1].timeSec : Math.min(lineEnd, w.timeSec + 4);
                    w.endSec = Math.max(w.timeSec + 0.12, nextStart);
                });
            }
            return result;
        }

        function getLyricsSyncInfo(lines) {
            if (!lines || !lines.length) return { kind: 'none', label: '' };
            if (lines[0].unsynced) return { kind: 'static', label: 'Lirik Statis' };
            const hasWords = lines.some(l => l.words && l.words.length);
            return hasWords ? { kind: 'word', label: 'Karaoke Kata' } : { kind: 'line', label: 'Tersinkron' };
        }

        // Cache parse bersama (kartu lirik, layar penuh, dock) — diganti hanya bila teks berubah.
        function getParsedLyricsForTrack(track) {
            const source = getTrackLyrics(track);
            const cached = window._musicLrcCache;
            if (cached && track && cached.trackId === track.id && cached.source === source) return cached;
            const lines = source ? parseLrcLyrics(source) : [];
            const entry = { trackId: track ? track.id : null, source: source, lines: lines, info: getLyricsSyncInfo(lines) };
            window._musicLrcCache = entry;
            return entry;
        }

        function getLyricsOffsetSec(track) {
            if (!track) return 0;
            const ms = window.musicLyricsOffsets[track.id];
            return (typeof ms === 'number' ? ms : 0) / 1000;
        }

        function getLyricsBadgeHtml(info) {
            if (!info || info.kind === 'none') return '';
            const icons = { word: 'bi-mic-fill', line: 'bi-clock-fill', static: 'bi-file-text-fill' };
            return '<span class="lyrics-sync-badge' + (info.kind === 'word' ? ' is-word' : '') + '"><i class="bi ' + icons[info.kind] + '"></i> ' + info.label + '</span>';
        }

        function getLyricsLineHtml(line, idx) {
            if (line.unsynced) {
                return '<div class="lrc-line-item lrc-line-static">' + escapeHtml(line.text) + '</div>';
            }
            const inner = line.words && line.words.length
                ? line.words.map(w => '<span class="lrc-word">' + escapeHtml(w.text) + '</span>').join(' ')
                : escapeHtml(line.text);
            return '<div class="lrc-line-item" onclick="seekToLyricLine(' + idx + ')" title="Ketuk untuk memutar dari baris ini">' + inner + '</div>';
        }

        function getLyricsScrollInnerHtml(entry, emptyAction) {
            if (!entry.lines.length) {
                return '<div class="text-center text-secondary py-4" onclick="' + emptyAction + '" style="cursor: pointer;">' +
                    '<i class="bi bi-file-earmark-music fs-2 d-block mb-2 opacity-50"></i>' +
                    '<div class="fw-bold small">Belum ada lirik lagu</div>' +
                    '<small class="opacity-75">Impor file .LRC atau ketuk di sini untuk menulis lirik manual.</small>' +
                    '</div>';
            }
            return '<div class="lyrics-pad" aria-hidden="true"></div>' +
                entry.lines.map((line, idx) => getLyricsLineHtml(line, idx)).join('') +
                '<div class="lyrics-pad" aria-hidden="true"></div>';
        }

        function seekToLyricLine(idx) {
            const track = window.musicPlaylist[window.currentMusicIndex];
            if (!track) return;
            const line = getParsedLyricsForTrack(track).lines[idx];
            if (!line || line.timeSec === null) return;
            seekAudioToSeconds(line.timeSec + getLyricsOffsetSec(track));
        }

        // --- OFFSET TIMING MANUAL (saat lirik kurang pas dengan lagu) ---
        function getLyricsOffsetFooterHtml() {
            const track = window.musicPlaylist[window.currentMusicIndex];
            const ms = track ? (window.musicLyricsOffsets[track.id] || 0) : 0;
            const label = (ms > 0 ? '+' : '') + (ms / 1000).toFixed(1) + 's';
            return '<div class="lyrics-offset-stepper" title="Geser timing lirik bila kurang pas">' +
                '<button class="lyrics-offset-btn" onclick="adjustLyricsOffset(-100)" title="Lirik lebih awal 0,1 detik">−</button>' +
                '<span class="lyrics-offset-label' + (ms !== 0 ? ' is-shifted' : '') + '">' + label + '</span>' +
                '<button class="lyrics-offset-btn" onclick="adjustLyricsOffset(100)" title="Lirik lebih lambat 0,1 detik">+</button>' +
                '<button class="lyrics-offset-btn" onclick="resetLyricsOffset()" title="Reset timing lirik" style="' + (ms === 0 ? 'display:none;' : '') + '"><i class="bi bi-arrow-counterclockwise" style="font-size:11px;"></i></button>' +
                '</div>';
        }

        function refreshLyricsOffsetUi() {
            ['lyricsOffsetFooter', 'fsLyricsOffsetFooter'].forEach(id => {
                const el = document.getElementById(id);
                if (el) el.innerHTML = getLyricsOffsetFooterHtml();
            });
        }

        function adjustLyricsOffset(deltaMs) {
            const track = window.musicPlaylist[window.currentMusicIndex];
            if (!track) return;
            const next = Math.max(-10000, Math.min(10000, (window.musicLyricsOffsets[track.id] || 0) + deltaMs));
            window.musicLyricsOffsets[track.id] = next;
            saveMusicSettings();
            refreshLyricsOffsetUi();
            forceLyricsResync();
        }

        function resetLyricsOffset() {
            const track = window.musicPlaylist[window.currentMusicIndex];
            if (!track) return;
            delete window.musicLyricsOffsets[track.id];
            saveMusicSettings();
            refreshLyricsOffsetUi();
            forceLyricsResync();
            showCustomToast('Timing lirik dikembalikan ke nol');
        }

        function forceLyricsResync() {
            ['musicLyricsScrollContainer', 'musicFsLyricsContainer'].forEach(id => {
                const c = document.getElementById(id);
                if (c) c._lyricsActiveIdx = -1;
            });
            window._lyricsScrollPauseUntil = 0;
            updateLyricsHighlight(window.musicPlayerAudio.currentTime || 0);
            updateDockLyricLine(window.musicPlayerAudio.currentTime || 0);
        }

        // --- SOROTAN REAL-TIME (baris + kata) ---
        function updateLyricsHighlight(currentSec) {
            const track = window.musicPlaylist[window.currentMusicIndex];
            if (!track) return;
            const containers = [];
            const cardContainer = document.getElementById('musicLyricsScrollContainer');
            const fsContainer = document.getElementById('musicFsLyricsContainer');
            if (cardContainer) containers.push(cardContainer);
            if (fsContainer && window.musicIsLyricsFullScreen) containers.push(fsContainer);
            if (!containers.length) return;

            const entry = getParsedLyricsForTrack(track);
            const lines = entry.lines;
            if (!lines.length || entry.info.kind === 'static') return;

            const t = (currentSec || 0) + getLyricsOffsetSec(track);
            let activeIdx = -1;
            for (let i = 0; i < lines.length; i++) {
                if (lines[i].timeSec <= t) activeIdx = i; else break;
            }
            const autoScrollPaused = Date.now() < (window._lyricsScrollPauseUntil || 0);

            containers.forEach(container => {
                const els = container.querySelectorAll('.lrc-line-item');
                if (els.length !== lines.length) return;
                els.forEach((el, idx) => {
                    const isActive = idx === activeIdx;
                    el.classList.toggle('active-lrc-line', isActive);
                    el.classList.toggle('past-lrc-line', activeIdx >= 0 && idx < activeIdx);
                    const spans = el.querySelectorAll('.lrc-word');
                    if (!spans.length) return;
                    const words = (lines[idx] && lines[idx].words) || [];
                    spans.forEach((sp, wi) => {
                        const w = words[wi];
                        if (!w) return;
                        const end = w.endSec || w.timeSec + 1;
                        let fill = 0;
                        if (activeIdx >= 0 && idx < activeIdx) fill = 100;
                        else if (isActive) fill = Math.max(0, Math.min(100, ((t - w.timeSec) / Math.max(0.12, end - w.timeSec)) * 100));
                        sp.style.setProperty('--fill', fill.toFixed(1) + '%');
                        sp.classList.toggle('singing-now', isActive && t >= w.timeSec && t < end);
                    });
                });
                if (!autoScrollPaused) {
                    if (activeIdx >= 0 && container._lyricsActiveIdx !== activeIdx) {
                        scrollLyricsContainerToLine(container, els[activeIdx]);
                    }
                    container._lyricsActiveIdx = activeIdx;
                }
            });
            updateResumeChips(autoScrollPaused && activeIdx >= 0);
        }

        // Gulir HANYA kontainer lirik (scrollIntoView ikut menyeret halaman induk).
        function scrollLyricsContainerToLine(container, el) {
            if (!container || !el) return;
            const cRect = container.getBoundingClientRect();
            const eRect = el.getBoundingClientRect();
            const target = container.scrollTop + (eRect.top - cRect.top) - container.clientHeight / 2 + el.offsetHeight / 2;
            const max = Math.max(0, container.scrollHeight - container.clientHeight);
            container._lyricsAutoScrollUntil = Date.now() + 800;
            container.scrollTo({ top: Math.max(0, Math.min(max, target)), behavior: container._lyricsHasSynced ? 'smooth' : 'auto' });
            container._lyricsHasSynced = true;
        }

        function sizeLyricsPads() {
            ['musicLyricsScrollContainer', 'musicFsLyricsContainer'].forEach(id => {
                const c = document.getElementById(id);
                if (!c) return;
                const pad = Math.max(0, c.clientHeight / 2 - 24);
                c.querySelectorAll('.lyrics-pad').forEach(p => { p.style.height = pad + 'px'; });
            });
        }

        function bindLyricsContainerInteraction(container) {
            if (container._lyricsBound) return;
            container._lyricsBound = true;
            container.addEventListener('wheel', pauseLyricsAutoScroll, { passive: true });
            container.addEventListener('touchmove', pauseLyricsAutoScroll, { passive: true });
            container.addEventListener('scroll', () => {
                if (Date.now() > (container._lyricsAutoScrollUntil || 0)) pauseLyricsAutoScroll();
            }, { passive: true });
        }

        function pauseLyricsAutoScroll() {
            window._lyricsScrollPauseUntil = Date.now() + 4000;
            updateResumeChips(true);
        }

        function resumeLyricsAutoScroll() {
            window._lyricsScrollPauseUntil = 0;
            updateResumeChips(false);
            forceLyricsResync();
        }

        function updateResumeChips(show) {
            if (window._lyricsChipsShown === show) return;
            window._lyricsChipsShown = show;
            ['lyricsResumeChip', 'fsLyricsResumeChip'].forEach(id => {
                const chip = document.getElementById(id);
                if (chip) chip.style.display = show ? 'flex' : 'none';
            });
        }

        // Dipanggil setiap kali kontainer lirik selesai dirender ulang.
        function syncLyricsContainers() {
            ['musicLyricsScrollContainer', 'musicFsLyricsContainer'].forEach(id => {
                const c = document.getElementById(id);
                if (!c) return;
                bindLyricsContainerInteraction(c);
                c._lyricsActiveIdx = -1;
                c._lyricsHasSynced = false;
            });
            sizeLyricsPads();
            updateLyricsHighlight(window.musicPlayerAudio.currentTime || window.musicPlaybackPosition || 0);
            if (!window._lyricsResizeBound) {
                window._lyricsResizeBound = true;
                window.addEventListener('resize', () => {
                    sizeLyricsPads();
                    forceLyricsResync();
                });
            }
        }

        // Satu loop rAF menggerakkan sorotan kata sehalus 60 fps selama lagu diputar.
        function startLyricsClock() {
            if (window._lyricsRafId) return;
            const step = () => {
                const audio = window.musicPlayerAudio;
                if (!audio || audio.paused || audio.ended) { window._lyricsRafId = null; return; }
                window._lyricsRafId = requestAnimationFrame(step);
                updateLyricsHighlight(audio.currentTime || 0);
                updateDockLyricLine(audio.currentTime || 0);
            };
            window._lyricsRafId = requestAnimationFrame(step);
        }

        function stopLyricsClock() {
            if (window._lyricsRafId) {
                cancelAnimationFrame(window._lyricsRafId);
                window._lyricsRafId = null;
            }
        }

        if (!window._lyricsKeysBound) {
            window._lyricsKeysBound = true;
            document.addEventListener('keydown', (e) => {
                if (e.key === 'Escape' && window.musicIsLyricsFullScreen) closeLyricsFullScreen();
            });
        }

        // --- LAYAR LIRIK PENUH (IMMERSIVE LIVE LYRICS) ---
        function toggleLyricsFullScreen() {
            if (window.musicIsLyricsFullScreen) { closeLyricsFullScreen(); return; }
            const track = window.musicPlaylist[window.currentMusicIndex];
            if (!track) { showCustomToast('Putar lagu terlebih dahulu'); return; }
            window.musicIsLyricsFullScreen = true;
            renderLyricsFullScreen();
            const overlay = document.getElementById('musicLyricsFullScreen');
            if (overlay) overlay.style.display = 'flex';
            document.body.classList.add('music-lyrics-fs-open');
            syncLyricsContainers();
            updatePlayPauseStateInUI(!window.musicPlayerAudio.paused && !window.musicPlayerAudio.ended);
            if (!window.musicPlayerAudio.paused) startLyricsClock();
        }

        function closeLyricsFullScreen() {
            window.musicIsLyricsFullScreen = false;
            const overlay = document.getElementById('musicLyricsFullScreen');
            if (overlay) overlay.style.display = 'none';
            document.body.classList.remove('music-lyrics-fs-open');
        }

        function renderLyricsFullScreen() {
            const track = window.musicPlaylist[window.currentMusicIndex];
            if (!track) return;
            const entry = getParsedLyricsForTrack(track);
            const titleEl = document.getElementById('mlfsTitle');
            if (titleEl) titleEl.innerText = track.title;
            const artistEl = document.getElementById('mlfsArtist');
            if (artistEl) artistEl.innerText = track.artist || track.folder || 'Folder Audio';
            const badgeEl = document.getElementById('mlfsBadge');
            if (badgeEl) badgeEl.innerHTML = getLyricsBadgeHtml(entry.info);
            const bg = document.getElementById('mlfsBg');
            if (bg) {
                const cover = getTrackCover(track);
                bg.style.backgroundImage = cover ? 'url("' + cover.replace(/"/g, '%22') + '")' : 'none';
            }
            const container = document.getElementById('musicFsLyricsContainer');
            if (container) container.innerHTML = getLyricsScrollInnerHtml(entry, 'closeLyricsFullScreen(); openLyricsEditMode()');
            refreshLyricsOffsetUi();
            const dur = window.musicTotalDuration || 0;
            const pos = window.musicPlaybackPosition || 0;
            const overlay = document.getElementById('musicLyricsFullScreen');
            if (overlay) {
                const slider = overlay.querySelector('.music-seek-slider');
                if (slider) {
                    slider.max = dur || 100;
                    slider.value = pos;
                    slider.style.setProperty('--seek-pct', (dur > 0 ? Math.min(100, pos * 100 / dur) : 0) + '%');
                }
                overlay.querySelectorAll('.music-dur-time-text').forEach(el => { el.innerText = formatAudioTime(dur); });
                overlay.querySelectorAll('.music-cur-time-text').forEach(el => { el.innerText = formatAudioTime(pos); });
            }
        }

        function openLyricsEditModeFromFs() {
            closeLyricsFullScreen();
            window.musicIsLyricsMode = true;
            expandMusicPlayerDeck();
            openLyricsEditMode();
        }

        // --- IMPOR FILE .LRC & PENCOCOKAN LIRIK SAMPINGAN ---
        function handleLrcFileImport(event) {
            const file = event.target.files && event.target.files[0];
            event.target.value = '';
            const track = window.musicPlaylist[window.currentMusicIndex];
            if (!file || !track) return;
            const reader = new FileReader();
            reader.onload = () => {
                const text = String(reader.result || '').replace(/^\uFEFF/, '');
                if (!text.trim()) { showCustomToast('File lirik kosong.'); return; }
                setTrackLyricsText(track, text);
                showCustomToast('Lirik "' + file.name + '" berhasil dimuat!');
            };
            reader.onerror = () => showCustomToast('Gagal membaca file lirik.');
            reader.readAsText(file);
        }

        function setTrackLyricsText(track, text) {
            // Impor eksplisit pengguna menang atas lirik manual yang mungkin ada sebelumnya.
            delete window.musicManualLyrics[track.id];
            delete window.musicManualLyrics[track.title];
            window.musicFetchedLyrics[track.id] = text;
            window._musicLrcCache = null;
            persistTrackLyricsToDb(track.id, text);
            saveMusicSettings();
            autoOpenLyricsIfPresent();
            refreshLyricsViews();
        }

        function normalizeLyricsBaseName(name) {
            return String(name || '').toLowerCase().replace(/\.[^.]+${dol}/, '').replace(/[^\p{L}\p{N}]/gu, '');
        }

        // Saat impor folder: pasangkan .lrc dengan audio bernama sama (lalu nama mirip),
        // cermin aturan pencocokan EmbeddedLyricsReader sisi aplikasi.
        async function attachSidecarLyrics(lrcFiles, newTracks) {
            if (!lrcFiles.length || !newTracks.length) return 0;
            const entries = [];
            for (let i = 0; i < lrcFiles.length; i++) {
                try {
                    const text = await lrcFiles[i].text();
                    entries.push({ base: normalizeLyricsBaseName(lrcFiles[i].name), text: String(text || '').replace(/^\uFEFF/, '') });
                } catch (e) {}
            }
            let attached = 0;
            newTracks.forEach(trk => {
                const base = normalizeLyricsBaseName(trk.title);
                if (!base) return;
                let hit = entries.find(en => en.base === base);
                if (!hit) hit = entries.find(en => en.base && (en.base.indexOf(base) >= 0 || base.indexOf(en.base) >= 0));
                if (hit && hit.text.trim()) {
                    window.musicFetchedLyrics[trk.id] = hit.text;
                    persistTrackLyricsToDb(trk.id, hit.text);
                    attached++;
                }
            });
            return attached;
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
                    if (e && e.name === 'NotAllowedError') {
                        showCustomToast('Browser menolak autoplay — klik tombol play sekali lagi.');
                    }
                });
            } else if (track.origin === 'phone') {
                showCustomToast('Trek HP ini belum punya URL sumber — tekan "Muat Musik HP" ulang.');
            }

            window.musicDockTrackId = track.id;
            setupMusicMediaSession();
            ensureTrackLyricsLoaded(track);
            refreshLyricsViews();
            window._lyricsScrollPauseUntil = 0;
            if (window.musicIsLyricsFullScreen) renderLyricsFullScreen();

            renderActiveMusicTabContent();
            updateExpandedDeckTrackInfo();
            updateDeckActionIcons();
            syncMusicDockState();
            updateMediaSession(track);
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
            const mode = window.musicRepeatMode || 'NONE';
            const isActive = mode !== 'NONE';
            const labels = { 'NONE': 'Pengulangan mati', 'ONE': 'Ulangi 1 lagu', 'ALL': 'Ulangi semua', 'AZ': 'Urutan A-Z' };

            // Tombol pengulangan ada dua: di dock mini player dan di deck penuh.
            document.querySelectorAll('.music-repeat-btn').forEach(function (btn) {
                const isDock = btn.classList.contains('music-dock-btn');
                const iconSize = isDock ? 16 : 24;
                btn.innerHTML = musicRepeatIconHtml(mode, iconSize, isActive ? 'text-primary' : 'text-secondary');
                btn.title = labels[mode] || 'Pengulangan';
                btn.setAttribute('aria-label', btn.title);
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
        // Hanya berkas audio yang menjadi trek; .lrc/.txt di folder yang sama otomatis
        // dipasangkan sebagai lirik sampingan (nama sama, lalu nama mirip).
        async function handleMusicFilesImport(event) {
            const fileList = event.target.files;
            if (!fileList || fileList.length === 0) return;
            const files = Array.prototype.slice.call(fileList);
            event.target.value = '';

            const audioExt = /\.(mp3|m4a|m4b|aac|mp4|flac|ogg|oga|opus|wav|webm|wma|amr|mid|midi|aiff|aif|caf)${dol}/i;
            const lrcExt = /\.(lrc|txt)${dol}/i;
            const audioFiles = files.filter(f => audioExt.test(f.name) || (f.type && f.type.indexOf('audio/') === 0));
            const lrcFiles = files.filter(f => lrcExt.test(f.name));
            if (audioFiles.length === 0) {
                showCustomToast(lrcFiles.length
                    ? 'Tidak ada audio terpilih — impor folder agar .lrc terhubung otomatis, atau gunakan tombol Impor .LRC di tampilan lirik.'
                    : 'Tidak ada berkas audio yang didukung.');
                return;
            }

            const db = await openMusicDatabase();
            const newTracks = [];
            const startIndex = window.musicPlaylist.length;

            // cover.jpg/folder.jpg di folder yang sama dipakai bila lagu tidak punya tag sampul.
            const coverFiles = files.filter(f => isCoverFileName(f.name));
            const coverByFolder = {};
            coverFiles.forEach(img => {
                const dir = relativeFolderOf(img);
                if (coverByFolder[dir] === undefined) coverByFolder[dir] = img;
            });
            const coverFileByTrackId = {};

            for (let i = 0; i < audioFiles.length; i++) {
                const file = audioFiles[i];
                const url = URL.createObjectURL(file);
                const sizeMb = (file.size / (1024 * 1024)).toFixed(1) + ' MB';
                const folderName = file.webkitRelativePath ? file.webkitRelativePath.split('/')[0] : 'Folder Musik';
                const cleanTitle = file.name.replace(/\.[^/.]+${dol}/, "");
                const trackId = 'trk_' + Date.now() + '_' + i + '_' + Math.random().toString(36).substring(2, 6);
                coverFileByTrackId[trackId] = coverByFolder[relativeFolderOf(file)] || null;

                const trackObj = {
                    id: trackId,
                    title: cleanTitle,
                    artist: folderName,
                    folder: folderName,
                    relFolder: relativeFolderOf(file),
                    origin: 'pc',
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

            const attached = await attachSidecarLyrics(lrcFiles, newTracks);
            // Ekstraksi sampul berjalan di latar belakang agar impor folder besar tidak tertahan.
            applyImportedAlbumCovers(newTracks, coverFileByTrackId);
            showCustomToast(newTracks.length + ' file musik berhasil ditambahkan' + (attached ? ' · ' + attached + ' lirik .LRC terhubung' : '') + '!');

            if (startIndex === 0 && window.musicPlaylist.length > 0) {
                playTrackByIndex(0);
            }
            loadMusik();
        }

        // Generate procedural offline lo-fi demo track using Web Audio API
        function generateOfflineDemoTrack() {
            const existingIdx = window.musicPlaylist.findIndex(t => t.id === 'demo_lofi_singghara');
            if (existingIdx >= 0) {
                playTrackByIndex(existingIdx);
                showCustomToast('Demo musik offline diputar ulang!');
                return;
            }
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
                    origin: 'pc',
                    size: '1.8 MB',
                    url: url,
                    blob: wavBlob,
                    addedAt: Date.now()
                };

                // Contoh Enhanced LRC: timestamp baris [mm:ss.xx] + timestamp kata <mm:ss.xx>
                // agar sorotan karaoke kata demi kata langsung terdemo tanpa file apa pun.
                window.musicManualLyrics[demoTrack.id] =
                    "[ti:My Singghara Lo-Fi Chill]\n" +
                    "[ar:My Singghara Audio]\n" +
                    "[offset:0]\n" +
                    "[00:00.00] ~ Intro Lo-Fi Instrumental ~\n" +
                    "[00:03.00]<00:03.00>Selamat <00:03.60>datang <00:04.10>di <00:04.40>Pemutar <00:05.00>Musik <00:05.50>My <00:05.90>Singghara\n" +
                    "[00:06.50]<00:06.50>Musik <00:07.10>santai <00:07.70>untuk <00:08.20>menemani <00:08.90>aktivitas <00:09.50>Anda\n" +
                    "[00:10.00]<00:10.00>Semua <00:10.60>fitur <00:11.10>dapat <00:11.60>diakses <00:12.30>100% <00:12.90>offline\n" +
                    "[00:14.00]<00:14.00>Putar <00:14.60>lagu <00:15.10>favorit, <00:15.80>folder <00:16.40>musik, <00:17.00>dan <00:17.40>lirik <00:17.80>lagu\n" +
                    "[00:18.00]<00:18.00>Ketuk <00:18.60>album <00:19.20>art <00:19.60>untuk <00:20.10>melihat <00:20.70>mode <00:21.20>lirik\n" +
                    "[00:22.00] ~ Melodi Cmaj7 - Am7 - Dm7 - G7 ~";

                window.musicPlaylist.unshift(demoTrack);
                saveMusicSettings();
                // Demo ikut tersimpan agar tetap ada setelah halaman di-reload.
                openMusicDatabase().then(db => {
                    if (db) {
                        try {
                            db.transaction('tracks', 'readwrite').objectStore('tracks').put(demoTrack);
                        } catch (e) {
                            console.warn('IDB write failed for track:', e);
                        }
                    }
                });
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
                        color: var(--brand-green) !important;
                        text-decoration: underline !important;
                        text-underline-offset: 4px;
                    }
                    .music-tab-link:not(.active) {
                        color: var(--text-sub);
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
                        background: var(--badge-bg);
                        color: var(--brand-green);
                        border-color: var(--border-color);
                    }
                    .music-subtab-chip:not(.active) {
                        background: var(--surface-subtle);
                        color: var(--text-sub);
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
                        border: 1px solid var(--border-color);
                        background: var(--surface-subtle);
                        color: var(--text-sub);
                        transition: all 0.15s ease;
                    }
                    .music-source-chip.active {
                        background: var(--brand-green);
                        border-color: var(--brand-green);
                        color: #fff;
                    }
                    .music-source-chip:hover {
                        filter: brightness(1.08);
                    }
                    .music-card-item {
                        border-radius: 12px;
                        padding: 12px 14px;
                        background: var(--surface-subtle);
                        border: 1px solid var(--border-color);
                        box-shadow: 0 2px 10px rgba(15, 23, 42, 0.08);
                        display: flex;
                        align-items: center;
                        justify-content: space-between;
                        cursor: pointer;
                        transition: background 0.15s ease, border-color 0.15s ease, box-shadow 0.15s ease;
                    }
                    body.dark .music-card-item,
                    [data-theme="dark"] .music-card-item {
                        box-shadow: none;
                    }
                    .music-card-item:hover {
                        background: var(--surface-hover);
                        box-shadow: 0 4px 14px rgba(15, 23, 42, 0.12);
                    }
                    body.dark .music-card-item:hover,
                    [data-theme="dark"] .music-card-item:hover {
                        box-shadow: none;
                    }
                    .music-card-item.active-playing {
                        background: var(--badge-bg);
                        border-color: var(--brand-green);
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
            } else if (!window.musicPhonePermission && !window.musicPhoneLoading) {
                // Izin mungkin baru diberikan lewat Pengaturan HP; cek ulang tanpa
                // memaksa pengguna menekan tombol saat kembali ke tab Musik.
                schedulePhonePermissionRecheck();
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
                <input type="file" id="musicLrcFileInput" accept=".lrc,.txt,text/plain" class="d-none" onchange="handleLrcFileImport(event)">

                <!-- FULL PLAYER "KINI MEMUTAR" — area halaman: navbar + sidebar tetap terlihat -->
                <style>${dol}{getMusicDockCss()}</style>
                <div id="musicExpandedPlayerDeck" class="music-now-playing-page">
                    ${dol}{currentTrack ? getExpandedPlayerDeckHtml(currentTrack) : ''}
                </div>

                <!-- LAYAR LIRIK PENUH (LIVE LYRICS IMMERSIVE) -->
                <div id="musicLyricsFullScreen" class="music-lyrics-fullscreen" style="display:none;" role="dialog" aria-label="Lirik layar penuh">
                    <div class="mlfs-bg" id="mlfsBg"></div>
                    <div class="mlfs-scrim"></div>
                    <div class="mlfs-content">
                        <div class="mlfs-header">
                            <div class="overflow-hidden">
                                <div class="mlfs-eyebrow"><i class="bi bi-music-note-beamed me-1"></i>KINI MEMUTAR · LIVE LYRICS</div>
                                <div class="mlfs-title text-truncate" id="mlfsTitle"></div>
                                <div class="mlfs-artist text-truncate" id="mlfsArtist"></div>
                            </div>
                            <div class="d-flex align-items-center gap-2 flex-shrink-0">
                                <span id="mlfsBadge"></span>
                                <button class="mlfs-icon-btn" onclick="closeLyricsFullScreen()" title="Tutup layar penuh (Esc)"><i class="bi bi-x-lg"></i></button>
                            </div>
                        </div>
                        <div class="lyrics-viewport flex-grow-1">
                            <div id="musicFsLyricsContainer" class="lyrics-scroll h-100 overflow-auto px-2"></div>
                            <button id="fsLyricsResumeChip" class="lyrics-resume-chip" onclick="resumeLyricsAutoScroll()" style="display:none;"><i class="bi bi-skip-end-fill"></i> Ikuti lagu</button>
                        </div>
                        <div class="mlfs-footer">
                            <div class="d-flex align-items-center gap-2 px-1 mb-2">
                                <span class="music-cur-time-text mlfs-time">00:00</span>
                                <input type="range" class="form-range music-seek-slider flex-grow-1" min="0" max="100" value="0" style="--seek-pct:0%"
                                       onchange="handleMusicSeekChange(this.value)"
                                       oninput="handleMusicSeekInput(this.value); this.style.setProperty('--seek-pct', (this.value / Math.max(1, this.max) * 100) + '%')">
                                <span class="music-dur-time-text mlfs-time">00:00</span>
                            </div>
                            <div class="mlfs-controls d-flex align-items-center justify-content-center gap-3 flex-wrap">
                                <span id="fsLyricsOffsetFooter"></span>
                                <button class="mlfs-icon-btn" onclick="prevMusicTrack()" title="Sebelumnya"><i class="bi bi-skip-start-fill"></i></button>
                                <button class="mlfs-play-btn" id="musicFsPlayBtn" onclick="togglePlayMusic()" title="Putar / Jeda"><i class="bi bi-play-fill"></i></button>
                                <button class="mlfs-icon-btn" onclick="nextMusicTrack()" title="Selanjutnya"><i class="bi bi-skip-end-fill"></i></button>
                                <button class="mlfs-icon-btn" onclick="document.getElementById('musicLrcFileInput').click()" title="Impor file .LRC"><i class="bi bi-file-earmark-arrow-down"></i></button>
                                <button class="mlfs-icon-btn" onclick="openLyricsEditModeFromFs()" title="Edit lirik"><i class="bi bi-pencil-square"></i></button>
                            </div>
                        </div>
                    </div>
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
            syncLyricsContainers();
        }

        function getMusicHeaderTabsHtml() {
            if (window.musicIsSearchExpanded) {
                return `
                    <div class="d-flex align-items-center music-surface rounded-pill px-3 py-1 flex-grow-1">
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
                            <div class="rounded-circle d-flex align-items-center justify-content-center mx-auto mb-3" style="width: 76px; height: 76px; background-color: var(--badge-bg); color: var(--brand-green);">
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
                            ${dol}{window.musicPhoneError ? '<p class="text-danger small mb-0 mt-3">' + escapeHtml(window.musicPhoneError) + '</p><button class="btn btn-sm btn-outline-danger mt-2" onclick="loadPhoneMusicLibrary(true)"><i class="bi bi-arrow-clockwise me-1"></i>Periksa Izin Lagi</button>' : ''}
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
                                         style="width: 42px; height: 42px; background: var(--badge-bg); color: var(--brand-green);">
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
                            <div class="rounded-circle d-flex align-items-center justify-content-center mx-auto mb-3" style="width: 72px; height: 72px; background: var(--danger-subtle); color: var(--danger-color);">
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
                            <div class="rounded-circle d-flex align-items-center justify-content-center mx-auto mb-3" style="width: 70px; height: 70px; background: var(--surface-subtle); color: var(--text-sub);">
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
                            ${dol}{musicCoverImgHtml(coverUrl)}
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
            const npPos = window.musicPlaybackPosition || 0;
            const npDur = window.musicTotalDuration || 0;
            const seekPct = npDur > 0 ? Math.min(100, npPos * 100 / npDur).toFixed(1) : 0;

            return `
                <div class="music-now-playing-inner">
                    <div class="np-center">
                        <!-- SAMPUL/LIRIK + overlay "Kini Memutar" (minimize) di dalam card -->
                        <div class="card card-custom p-0 mx-auto shadow-lg np-art-card"
                             id="musicCentralArtCard"
                             style="border-radius: 24px; position: relative; overflow: hidden; background: var(--surface-subtle); border: 1px solid var(--border-color);">
                            ${dol}{getCentralArtCardInnerHtml(currentTrack)}
                        </div>

                        <!-- TRACK METADATA (TITLE & ARTIST) -->
                        <div class="text-center px-3 w-100" style="min-width:0;">
                            <h4 class="fw-bold mb-1 text-truncate cursor-pointer" id="deckTrackTitle"
                                onclick="toggleLyricsMode()" title="Ketuk untuk melihat lirik">
                                ${dol}{escapeHtml(currentTrack.title)}
                            </h4>
                            <div class="text-secondary small text-truncate" id="deckTrackArtist">
                                ${dol}{escapeHtml(currentTrack.artist || currentTrack.folder || 'Folder Audio')}
                            </div>
                        </div>

                        <!-- MIDDLE ACTION ROW (QUEUE, FAVORITE, PLAYLIST) -->
                        <div class="d-flex align-items-center justify-content-center gap-5">
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
                    <div class="np-bottom">
                        <!-- SEEK SLIDER & DURATION -->
                        <div class="px-2 mb-2">
                            <input type="range" class="form-range music-seek-slider" min="0"
                                   max="${dol}{npDur || 100}"
                                   value="${dol}{npPos}"
                                   style="--seek-pct:${dol}{seekPct}%"
                                   onchange="handleMusicSeekChange(this.value)"
                                   oninput="handleMusicSeekInput(this.value); this.style.setProperty('--seek-pct', (this.value / Math.max(1, this.max) * 100) + '%')">
                            <div class="d-flex justify-content-between small text-secondary">
                                <span class="music-cur-time-text fw-bold text-primary">${dol}{formatAudioTime(npPos)}</span>
                                <span class="music-dur-time-text">${dol}{formatAudioTime(npDur)}</span>
                            </div>
                        </div>

                        <!-- MAIN MEDIA CONTROL DECK (OPSI, SHUFFLE, PREV, BIG PLAY, NEXT, REPEAT) -->
                        <div class="d-flex align-items-center justify-content-between px-1">
                            <!-- 0. TITIK TIGA (Opsi) — di kiri shuffle -->
                            <button class="btn btn-link text-secondary p-0" onclick="openMusicSettingsModal()" title="Opsi">
                                <i class="bi bi-three-dots-vertical fs-4"></i>
                            </button>

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
                            <div class="rounded-circle d-flex align-items-center justify-content-center shadow cursor-pointer"
                                 id="musicBigPlayPauseBtn"
                                 style="width: 72px; height: 72px; background: var(--brand-green); transition: transform 0.1s ease;"
                                 onclick="togglePlayMusic()">
                                <i class="bi ${dol}{isPlaying ? 'bi-pause-fill' : 'bi-play-fill'}" style="font-size: 38px; color: white;"></i>
                            </div>

                            <!-- 4. NEXT -->
                            <button class="btn btn-link text-body p-0" onclick="nextMusicTrack()" title="Selanjutnya">
                                <i class="bi bi-skip-end-fill" style="font-size: 36px;"></i>
                            </button>

                            <!-- 5. REPEAT MODE -->
                            <button class="btn btn-link p-0 music-repeat-btn" id="deckRepeatBtn" onclick="cycleRepeatMode()" title="Mode Pengulangan" style="position:relative;">
                                ${dol}{musicRepeatIconHtml(window.musicRepeatMode, 24, window.musicRepeatMode === 'NONE' ? 'text-secondary' : 'text-primary')}
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
                        <div class="p-3 d-flex flex-column h-100 music-surface">
                            <div class="d-flex justify-content-between align-items-center mb-2">
                                <span class="fw-bold text-primary small">Tulis / Tempel Lirik (LRC)</span>
                                <span class="badge text-secondary" style="font-size: 10px; background: var(--surface-subtle);">Posisi: ${dol}{curPos}</span>
                            </div>

                            <div class="d-flex gap-2 mb-2 flex-wrap">
                                <button class="btn btn-xs btn-primary py-1 px-2 fw-bold" style="font-size: 11px;" onclick="insertTimestampAtLyricsCursor()">
                                    <i class="bi bi-plus-lg me-1"></i> Sisipkan Waktu
                                </button>
                                <button class="btn btn-xs btn-outline-primary py-1 px-2 fw-bold" style="font-size: 11px;" onclick="document.getElementById('musicLrcFileInput').click()">
                                    <i class="bi bi-file-earmark-arrow-down me-1"></i> Impor .LRC
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
                    // VIEW LYRICS MODE — Live Lyrics: sorotan baris/kata + auto-scroll
                    const entry = getParsedLyricsForTrack(currentTrack);
                    const isTimed = entry.info.kind === 'line' || entry.info.kind === 'word';
                    return `
                        <div class="p-3 d-flex flex-column h-100 music-surface">
                            <div class="d-flex justify-content-between align-items-center mb-2 border-bottom pb-2" style="--bs-border-color: var(--border-color);">
                                <div class="d-flex align-items-center gap-2 text-primary fw-bold small overflow-hidden">
                                    <i class="bi bi-lyrics"></i>
                                    <span class="text-nowrap">Lirik Lagu</span>
                                    ${dol}{getLyricsBadgeHtml(entry.info)}
                                </div>
                                <div class="d-flex align-items-center gap-1 flex-shrink-0">
                                    <button class="btn btn-sm btn-link text-primary p-0 px-1" onclick="document.getElementById('musicLrcFileInput').click()" title="Impor file .LRC">
                                        <i class="bi bi-file-earmark-arrow-down fs-6"></i>
                                    </button>
                                    <button class="btn btn-sm btn-link text-primary p-0 px-1" onclick="toggleLyricsFullScreen()" title="Lirik layar penuh">
                                        <i class="bi bi-arrows-fullscreen fs-6"></i>
                                    </button>
                                    <button class="btn btn-sm btn-link text-primary p-0 px-1" onclick="openLyricsEditMode()" title="Edit Lirik">
                                        <i class="bi bi-pencil-square fs-6"></i>
                                    </button>
                                    <button class="btn btn-sm btn-link text-secondary p-0 px-1" onclick="toggleLyricsMode()" title="Tutup Lirik">
                                        <i class="bi bi-x-lg fs-6"></i>
                                    </button>
                                </div>
                            </div>

                            <div class="lyrics-viewport flex-grow-1">
                                <div class="lyrics-scroll h-100 overflow-auto px-2" id="musicLyricsScrollContainer">
                                    ${dol}{getLyricsScrollInnerHtml(entry, 'openLyricsEditMode()')}
                                </div>
                                <button id="lyricsResumeChip" class="lyrics-resume-chip" onclick="resumeLyricsAutoScroll()" style="display:none;"><i class="bi bi-skip-end-fill"></i> Ikuti lagu</button>
                            </div>
                            <div class="d-flex justify-content-center pt-2" id="lyricsOffsetFooter" style="min-height:28px;">
                                ${dol}{isTimed ? getLyricsOffsetFooterHtml() : ''}
                            </div>
                        </div>
                        <button class="np-card-minimize np-minimize-lyrics" onclick="collapseMusicPlayerDeck()" title="Perkecil"><i class="bi bi-chevron-compact-down"></i></button>
                    `;
                }
            } else {
                // NORMAL ALBUM ART VIEW
                return `
                    <div class="w-100 h-100 d-flex align-items-center justify-content-center cursor-pointer" data-cover-fallback="vinyl"
                         onclick="toggleLyricsMode()" title="Ketuk untuk melihat lirik">
                        ${dol}{coverUrl ? `
                            <img src="${dol}{coverUrl}" style="width: 100%; height: 100%; object-fit: cover;" onerror="musicCoverFailed(this)">
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
                    <div class="np-card-minimize" onclick="collapseMusicPlayerDeck()" title="Perkecil">Kini Memutar</div>
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
                deck.scrollTop = 0;
                updateRepeatButtonUI();
                updateShuffleButtonUI();
                setupMusicArtworkGestures();
                syncLyricsContainers();
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
            syncLyricsContainers();
        }

        function toggleLyricsMode() {
            window.musicIsLyricsMode = !window.musicIsLyricsMode;
            // Tutup manual = jangan auto-buka lagi di trek berikutnya sesi ini.
            window.musicLyricsUserClosed = !window.musicIsLyricsMode;
            window.musicLyricsEditMode = false;
            const card = document.getElementById('musicCentralArtCard');
            const currentTrack = window.musicPlaylist[window.currentMusicIndex];
            if (card && currentTrack) {
                card.innerHTML = getCentralArtCardInnerHtml(currentTrack);
            }
            syncLyricsContainers();
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
            syncLyricsContainers();
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
            window._musicLrcCache = null;
            saveMusicSettings();
            showCustomToast('Lirik manual berhasil disimpan!');
            window.musicLyricsEditMode = false;
            const card = document.getElementById('musicCentralArtCard');
            if (card) card.innerHTML = getCentralArtCardInnerHtml(currentTrack);
            if (window.musicIsLyricsFullScreen) renderLyricsFullScreen();
            syncLyricsContainers();
        }

        function seekAudioToSeconds(sec) {
            const audio = window.musicPlayerAudio;
            const dur = audio.duration;
            let target = Math.max(0, sec || 0);
            if (isFinite(dur) && dur > 0) target = Math.min(target, dur - 0.05);
            audio.currentTime = target;
            window.musicPlaybackPosition = target;
            forceLyricsResync();
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
            if (!card || card._musicGesturesBound) return;
            card._musicGesturesBound = true;
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
                                 style="width: 36px; height: 36px; background: var(--surface-hover);">
                            ${dol}{musicCoverImgHtml(coverUrl)}
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
                    <!-- 1. FOLDER MUSIK LOKAL/PC -->
                    <div class="p-3 rounded-3 cursor-pointer d-flex align-items-center gap-3 dashboard-subcard" 
                         onclick="document.getElementById('musicFolderPickerInput').click()">
                        <div class="rounded-3 d-flex align-items-center justify-content-center flex-shrink-0" 
                             style="width: 44px; height: 44px; background: var(--badge-bg); color: var(--brand-green);">
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
                             style="width: 44px; height: 44px; background: var(--success-subtle); color: var(--success-color);">
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
                             style="width: 44px; height: 44px; background: var(--surface-hover); color: var(--text-sub);">
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
                             style="width: 44px; height: 44px; background: var(--warning-subtle); color: var(--warning-color);">
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
                             style="width: 44px; height: 44px; background: var(--badge-bg); color: var(--brand-green);">
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
                             style="width: 44px; height: 44px; background: var(--success-subtle); color: var(--success-color);">
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
                             style="width: 44px; height: 44px; background: var(--danger-subtle); color: var(--danger-color);">
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
            stopLyricsClock();
            closeLyricsFullScreen();
            window._musicLrcCache = null;
            window.musicPlayerAudio.pause();
            window.musicPlayerAudio.src = '';
            window.musicPlaylist = [];
            window.currentMusicIndex = 0;
            window.musicSelectedFolder = null;
            window.musicPhoneTracksLoaded = false;
            window.musicPhoneAutoTried = true;
            window.musicFetchedLyrics = {};
            window.musicAutoAlbumArts = {};
            window.musicDockTrackId = null;
            const dockEl = document.getElementById('musicGlobalDock');
            if (dockEl) dockEl.remove();
            document.body.classList.remove('has-music-dock');

            // Clear IndexedDB tracks
            const db = await openMusicDatabase();
            if (db) {
                try {
                    const tx = db.transaction(['tracks', 'lyrics', 'covers'], 'readwrite');
                    tx.objectStore('tracks').clear();
                    tx.objectStore('lyrics').clear();
                    tx.objectStore('covers').clear();
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
