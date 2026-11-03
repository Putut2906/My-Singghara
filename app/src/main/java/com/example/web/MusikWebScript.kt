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

        // Sumber berkas: 'pc' = folder yang dipilih di browser (IndexedDB),
        // 'phone' = pustaka musik yang tersimpan di HP (lewat server lokal).
        window.musicSource = window.musicSource || 'pc';
        window.musicPcTracks = window.musicPcTracks || [];
        window.musicPhoneTracks = window.musicPhoneTracks || [];
        window.musicPhoneLibraryLoaded = false;

        function applyMusicSource() {
            window.musicPlaylist = (window.musicSource === 'phone') ? window.musicPhoneTracks : window.musicPcTracks;
            if (window.currentMusicIndex >= window.musicPlaylist.length) window.currentMusicIndex = 0;
        }

        async function loadPhoneMusicLibrary(force) {
            if (window.musicPhoneLibraryLoaded && !force) return window.musicPhoneTracks;
            try {
                const response = await fetch('/api/media/library?type=audio' + (force ? '&refresh=1' : ''));
                const payload = await response.json();
                const files = (payload && payload.files) || [];
                window.musicPhoneTracks = files.map(function (file) {
                    return {
                        id: file.id,
                        title: file.title || file.name,
                        artist: file.artist || file.folder || 'Musik di HP',
                        folder: file.folder || 'Musik di HP',
                        size: file.formattedSize,
                        sizeBytes: file.size || 0,
                        durationMs: file.duration || 0,
                        url: file.streamUrl,
                        thumb: file.thumbUrl,
                        origin: 'phone',
                        addedAt: (file.dateAdded || 0) * 1000
                    };
                });
                window.musicPhoneLibraryLoaded = true;
            } catch (err) {
                console.warn('Pustaka musik HP gagal dimuat:', err);
                showCustomToast('Pustaka musik HP gagal dimuat. Pastikan izin media di HP sudah diberikan.');
            }
            return window.musicPhoneTracks;
        }

        async function switchMusicSource(source) {
            if (window.musicSource === source) return;
            window.musicSource = source;
            try { localStorage.setItem('singghara_music_source', source); } catch (e) {}
            window.musicSelectedFolder = null;
            window.musicSearchQuery = '';
            if (source === 'phone') {
                await loadPhoneMusicLibrary(true);
            }
            applyMusicSource();
            loadMusik();
            showCustomToast(source === 'phone'
                ? (window.musicPhoneTracks.length > 0 ? window.musicPhoneTracks.length + ' lagu dari HP dimuat.' : 'Tidak ada lagu terdeteksi di HP.')
                : 'Menampilkan pustaka musik dari komputer.');
        }

        async function refreshPhoneMusicLibrary() {
            await loadPhoneMusicLibrary(true);
            if (window.musicSource !== 'phone') {
                window.musicSource = 'phone';
                try { localStorage.setItem('singghara_music_source', 'phone'); } catch (e) {}
                window.musicSelectedFolder = null;
            }
            applyMusicSource();
            loadMusik();
            showCustomToast(window.musicPhoneTracks.length + ' lagu dari HP diperbarui.');
        }

        function getMusicTrackCoverUrl(track) {
            if (!track) return null;
            const custom = window.musicCustomAlbumArts && (window.musicCustomAlbumArts[track.id] || window.musicCustomAlbumArts[track.title]);
            return custom || track.thumb || null;
        }

        function getMusicSourceSwitchHtml() {
            const onPhone = window.musicSource === 'phone';
            const pillBase = 'cursor:pointer;border-radius:999px;padding:6px 14px;font-size:12px;font-weight:700;display:inline-flex;align-items:center;gap:6px;border:1px solid rgba(127,127,127,0.25);';
            const activeStyle = 'background:var(--bs-primary);color:#fff;border-color:var(--bs-primary);';
            const idleStyle = 'background:rgba(127,127,127,0.08);color:var(--bs-secondary);';
            return `
                <div class="d-flex flex-wrap align-items-center gap-2 mb-2">
                    <span style="${dol}{pillBase}${dol}{onPhone ? activeStyle : idleStyle}" onclick="switchMusicSource('phone')">
                        <i class="bi bi-phone-fill"></i> Berkas di HP (${dol}{window.musicPhoneTracks.length})
                    </span>
                    <span style="${dol}{pillBase}${dol}{!onPhone ? activeStyle : idleStyle}" onclick="switchMusicSource('pc')">
                        <i class="bi bi-pc-display"></i> Berkas di Komputer (${dol}{window.musicPcTracks.length})
                    </span>
                    ${dol}{onPhone ? `<button class="btn btn-sm btn-link text-secondary p-0 ms-1" title="Muat ulang pustaka HP" onclick="refreshPhoneMusicLibrary()"><i class="bi bi-arrow-clockwise"></i></button>` : ''}
                </div>
            `;
        }

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

                const source = localStorage.getItem('singghara_music_source');
                if (source === 'phone' || source === 'pc') window.musicSource = source;

                // Restore cached tracks from IndexedDB
                const db = await openMusicDatabase();
                if (db && window.musicPcTracks.length === 0) {
                    const savedTracks = await new Promise((resolve) => {
                        try {
                            const getAllReq = db.transaction('tracks', 'readonly').objectStore('tracks').getAll();
                            getAllReq.onsuccess = () => resolve(getAllReq.result || []);
                            getAllReq.onerror = () => resolve([]);
                        } catch (e) {
                            resolve([]);
                        }
                    });
                    savedTracks.forEach(st => {
                        // Berkas dari HP tidak punya blob; hanya trek PC yang dibuat URL blob.
                        if (st.blob) {
                            st.url = URL.createObjectURL(st.blob);
                        }
                    });
                    window.musicPcTracks = savedTracks;
                }

                if (window.musicSource === 'phone') {
                    await loadPhoneMusicLibrary();
                }
                applyMusicSource();

                if (window.currentTab === 'musik') {
                    loadMusik();
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
                localStorage.setItem('singghara_music_source', window.musicSource);
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

            window.musicPlayerAudio.addEventListener('play', () => {
                updatePlayPauseStateInUI(true);
            });

            window.musicPlayerAudio.addEventListener('pause', () => {
                updatePlayPauseStateInUI(false);
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
            // Update mini player button
            const miniBtn = document.getElementById('musicMiniPlayPauseBtn');
            if (miniBtn) {
                miniBtn.innerHTML = `<i class="bi ${dol}{isPlaying ? 'bi-pause-fill' : 'bi-play-fill'}" style="font-size: 24px;"></i>`;
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

            const lrcText = window.musicManualLyrics[currentTrack.id] || window.musicManualLyrics[currentTrack.title] || '';
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

            renderActiveMusicTabContent();
            updateExpandedDeckTrackInfo();
            updateMiniPlayerInfo();
            updateDeckActionIcons();
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
            const btn = document.getElementById('deckRepeatBtn');
            if (!btn) return;
            const mode = window.musicRepeatMode;
            let badge = '';
            let isActive = mode !== 'NONE';

            if (mode === 'ONE') badge = '<span style="position: absolute; font-size: 10px; font-weight: 800; top: 12px; left: 19px;">1</span>';
            else if (mode === 'ALL') badge = '<span style="position: absolute; font-size: 9px; font-weight: 800; top: 13px; left: 18px;">A</span>';
            else if (mode === 'AZ') badge = '<span style="position: absolute; font-size: 7px; font-weight: 900; top: 13px; left: 15px;">A-Z</span>';

            btn.innerHTML = `
                <div style="position: relative; width: 44px; height: 44px; display: flex; align-items: center; justify-content: center;">
                    <i class="bi bi-repeat ${dol}{isActive ? 'text-primary' : 'text-secondary'}" style="font-size: 24px;"></i>
                    ${dol}{badge}
                </div>
            `;
        }

        function toggleShuffleMode() {
            window.isMusicShuffle = !window.isMusicShuffle;
            saveMusicSettings();
            const btn = document.getElementById('deckShuffleBtn');
            if (btn) {
                btn.className = `btn btn-link p-0 ${dol}{window.isMusicShuffle ? 'text-primary' : 'text-secondary'}`;
            }
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
            const startIndex = window.musicPcTracks.length;

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
                    origin: 'pc',
                    addedAt: Date.now()
                };

                newTracks.push(trackObj);
                window.musicPcTracks.push(trackObj);

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

            // Berkas yang baru dipilih ada di sisi komputer, jadi tampilkan pustaka PC.
            window.musicSource = 'pc';
            try { localStorage.setItem('singghara_music_source', 'pc'); } catch (e) {}
            applyMusicSource();

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

                window.musicPcTracks.unshift(demoTrack);
                window.musicSource = 'pc';
                try { localStorage.setItem('singghara_music_source', 'pc'); } catch (e) {}
                applyMusicSource();
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
            const hasTracks = window.musicPlaylist.length > 0;
            const currentTrack = hasTracks ? (window.musicPlaylist[window.currentMusicIndex] || window.musicPlaylist[0]) : null;

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

                <!-- SUMBER BERKAS: PUSTAKA HP ATAU KOMPUTER -->
                ${dol}{getMusicSourceSwitchHtml()}

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
                    ${dol}{renderActiveMusicTabContentHtml()}
                </div>

                <!-- PERSISTENT MINI PLAYER DOCK AT BOTTOM -->
                ${dol}{hasTracks && currentTrack ? getMiniPlayerHtml(currentTrack) : ''}

                <!-- HIDDEN FILE INPUTS -->
                <input type="file" id="musicFolderPickerInput" webkitdirectory directory multiple class="d-none" onchange="handleMusicFilesImport(event)">
                <input type="file" id="musicFilesPickerInput" accept="audio/*" multiple class="d-none" onchange="handleMusicFilesImport(event)">
                <input type="file" id="musicCoverPickerInput" accept="image/*" class="d-none" onchange="handleCustomCoverUpload(event)">
                <input type="file" id="musicLrcRestoreInput" accept=".json,application/json" class="d-none" onchange="handleRestoreLyricsFile(event)">

                <!-- FULL IMMERSIVE PLAYER DECK OVERLAY -->
                <div id="musicExpandedPlayerDeck" style="display: ${dol}{window.musicIsPlayerExpanded ? 'block' : 'none'}; position: fixed; inset: 0; z-index: 1060; background: var(--bs-body-bg); overflow-y: auto;">
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
                            <div class="modal-body p-3">
                                ${dol}{getMusicSettingsHtml()}
                            </div>
                        </div>
                    </div>
                </div>
            `;

            document.getElementById('tab-content').innerHTML = html;

            // Bind swipe gesture listeners for vinyl/album art
            setupMusicArtworkGestures();
            updateRepeatButtonUI();
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
                container.innerHTML = renderActiveMusicTabContentHtml();
            }
        }

        function renderActiveMusicTabContentHtml() {
            const query = (window.musicSearchQuery || '').toLowerCase().trim();

            // TAB 0: FOLDER
            if (window.musicSelectedTab === 0) {
                if (window.musicPlaylist.length === 0) {
                    if (window.musicSource === 'phone') {
                        return `
                            <div class="card card-custom p-5 text-center my-3" style="border-radius: 20px;">
                                <div class="rounded-circle d-flex align-items-center justify-content-center mx-auto mb-3" style="width: 76px; height: 76px; background-color: rgba(13, 110, 253, 0.12); color: var(--bs-primary);">
                                    <i class="bi bi-phone fs-1"></i>
                                </div>
                                <h6 class="fw-bold mb-2">Tidak Ada Musik di HP</h6>
                                <p class="text-secondary small mb-4" style="max-width: 460px; margin-inline: auto;">
                                    Pustaka musik di HP masih kosong. Pastikan izin media di HP sudah diterima saat server web dinyalakan, lalu simpan lagu pada folder Music di HP atau melalui menu Kirim Berkas ke HP.
                                </p>
                                <div class="d-flex justify-content-center gap-2 flex-wrap">
                                    <button class="btn btn-brand px-4 py-2" onclick="refreshPhoneMusicLibrary()">
                                        <i class="bi bi-arrow-clockwise me-2"></i> Muat Ulang Pustaka HP
                                    </button>
                                    <button class="btn btn-outline-primary px-3 py-2" onclick="switchMusicSource('pc')">
                                        <i class="bi bi-pc-display me-1"></i> Pakai Berkas Komputer
                                    </button>
                                </div>
                            </div>
                        `;
                    }
                    return `
                        <div class="card card-custom p-5 text-center my-3" style="border-radius: 20px;">
                            <div class="rounded-circle d-flex align-items-center justify-content-center mx-auto mb-3" style="width: 76px; height: 76px; background-color: rgba(13, 110, 253, 0.12); color: var(--bs-primary);">
                                <i class="bi bi-folder-fill fs-1"></i>
                            </div>
                            <h6 class="fw-bold mb-2">Folder Musik Belum Dipilih</h6>
                            <p class="text-secondary small mb-4" style="max-width: 440px; margin-inline: auto;">
                                Pilih folder musik dari komputer Anda atau tambahkan file lagu untuk mulai memutar musik.
                            </p>
                            <div class="d-flex justify-content-center gap-2 flex-wrap">
                                <button class="btn btn-brand px-4 py-2" onclick="document.getElementById('musicFolderPickerInput').click()">
                                    <i class="bi bi-folder-plus me-2"></i> Pilih Folder Musik
                                </button>
                                <button class="btn btn-outline-secondary px-3 py-2" onclick="document.getElementById('musicFilesPickerInput').click()">
                                    <i class="bi bi-file-earmark-music me-1"></i> Tambah Berkas
                                </button>
                                <button class="btn btn-outline-primary px-3 py-2" onclick="generateOfflineDemoTrack()">
                                    <i class="bi bi-play-circle me-1"></i> Putar Demo Offline
                                </button>
                            </div>
                        </div>
                    `;
                }

                // Group tracks by folder / artist
                const grouped = {};
                window.musicPlaylist.forEach(trk => {
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
                const favTracks = window.musicPlaylist.filter(t => isTrackFavorite(t))
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
                    list = [...window.musicPlaylist].reverse();
                    emptyMsg = 'Tidak ada lagu yang baru ditambahkan.';
                } else if (window.musicSelectedSubTab === 1) {
                    list = window.musicPlaylist.filter(t => (window.musicPlayCounts[t.id] || 0) > 0)
                        .sort((a, b) => (window.musicPlayCounts[b.id] || 0) - (window.musicPlayCounts[a.id] || 0));
                    emptyMsg = 'Belum ada lagu yang sering diputar.';
                } else if (window.musicSelectedSubTab === 2) {
                    list = window.musicRecentlyPlayed.filter(t => window.musicPlaylist.some(p => p.id === t.id));
                    emptyMsg = 'Belum ada lagu yang baru saja diputar.';
                } else if (window.musicSelectedSubTab === 3) {
                    list = window.musicPlaylist.filter(t => isTrackInMyPlaylist(t));
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
            const coverUrl = getMusicTrackCoverUrl(track);

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

        function getMiniPlayerHtml(currentTrack) {
            if (!currentTrack) return '';
            const isPlaying = window.musicPlayerAudio && !window.musicPlayerAudio.paused && !window.musicPlayerAudio.ended;
            const coverUrl = getMusicTrackCoverUrl(currentTrack);

            return `
                <div class="music-mini-dock">
                    <div class="card card-custom p-2 shadow-lg" 
                         style="border-radius: 16px; border: 1px solid rgba(13, 110, 253, 0.25); background: var(--bs-body-bg); cursor: pointer;"
                         onclick="expandMusicPlayerDeck()">
                        <div class="d-flex align-items-center justify-content-between px-2 py-1 gap-2">
                            <!-- ALBUM ART -->
                            <div class="rounded-3 flex-shrink-0 d-flex align-items-center justify-content-center overflow-hidden" 
                                 style="width: 44px; height: 44px; background: linear-gradient(135deg, #1f2937, #111827);">
                                ${dol}{coverUrl ? `<img src="${dol}{coverUrl}" style="width: 100%; height: 100%; object-fit: cover;">` : '<i class="bi bi-music-note-beamed text-primary fs-5"></i>'}
                            </div>

                            <!-- TITLE & ARTIST -->
                            <div class="overflow-hidden flex-grow-1 px-1">
                                <div class="fw-bold text-truncate" style="font-size: 13px;" id="miniPlayerTrackTitle">
                                    ${dol}{escapeHtml(currentTrack.title)}
                                </div>
                                <small class="text-secondary text-truncate d-block" style="font-size: 11px;" id="miniPlayerTrackArtist">
                                    ${dol}{escapeHtml(currentTrack.artist || currentTrack.folder || 'Musik')}
                                </small>
                            </div>

                            <!-- CONTROLS: PREV, PLAY/PAUSE, NEXT, QUEUE -->
                            <div class="d-flex align-items-center gap-1 flex-shrink-0" onclick="event.stopPropagation()">
                                <button class="btn btn-sm btn-link text-secondary p-1" onclick="prevMusicTrack()" title="Sebelumnya">
                                    <i class="bi bi-skip-start-fill fs-5"></i>
                                </button>
                                <button class="btn btn-sm btn-link text-primary p-1" onclick="togglePlayMusic()" id="musicMiniPlayPauseBtn" title="Putar/Jeda">
                                    <i class="bi ${dol}{isPlaying ? 'bi-pause-fill' : 'bi-play-fill'}" style="font-size: 24px;"></i>
                                </button>
                                <button class="btn btn-sm btn-link text-secondary p-1" onclick="nextMusicTrack()" title="Selanjutnya">
                                    <i class="bi bi-skip-end-fill fs-5"></i>
                                </button>
                                <button class="btn btn-sm btn-link text-secondary p-1" onclick="openMusicQueueModal()" title="Lihat Antrean">
                                    <i class="bi bi-music-note-list fs-5"></i>
                                </button>
                            </div>
                        </div>
                    </div>
                </div>
            `;
        }

        function updateMiniPlayerInfo() {
            const currentTrack = window.musicPlaylist[window.currentMusicIndex];
            if (!currentTrack) return;
            const tEl = document.getElementById('miniPlayerTrackTitle');
            if (tEl) tEl.innerText = currentTrack.title;
            const aEl = document.getElementById('miniPlayerTrackArtist');
            if (aEl) aEl.innerText = currentTrack.artist || currentTrack.folder || 'Musik';
        }

        function getExpandedPlayerDeckHtml(currentTrack) {
            if (!currentTrack) return '';
            const isPlaying = window.musicPlayerAudio && !window.musicPlayerAudio.paused && !window.musicPlayerAudio.ended;
            const isFav = isTrackFavorite(currentTrack);
            const inPl = isTrackInMyPlaylist(currentTrack);
            const coverUrl = getMusicTrackCoverUrl(currentTrack);
            const lrcText = (window.musicManualLyrics && (window.musicManualLyrics[currentTrack.id] || window.musicManualLyrics[currentTrack.title])) || '';

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
                            <button class="btn btn-link p-0 ${dol}{window.isMusicShuffle ? 'text-primary' : 'text-secondary'}" 
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
                            <button class="btn btn-link p-0" id="deckRepeatBtn" onclick="cycleRepeatMode()" title="Mode Pengulangan">
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
            const coverUrl = getMusicTrackCoverUrl(currentTrack);
            const lrcText = (window.musicManualLyrics && (window.musicManualLyrics[currentTrack.id] || window.musicManualLyrics[currentTrack.title])) || '';

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
            window.musicIsPlayerExpanded = true;
            const deck = document.getElementById('musicExpandedPlayerDeck');
            if (deck) {
                const currentTrack = window.musicPlaylist[window.currentMusicIndex];
                if (currentTrack) deck.innerHTML = getExpandedPlayerDeckHtml(currentTrack);
                deck.style.display = 'block';
                updateRepeatButtonUI();
                setupMusicArtworkGestures();
            }
        }

        function collapseMusicPlayerDeck() {
            window.musicIsPlayerExpanded = false;
            const deck = document.getElementById('musicExpandedPlayerDeck');
            if (deck) deck.style.display = 'none';
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
                const coverUrl = getMusicTrackCoverUrl(trk);
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
            const modalEl = document.getElementById('musicSettingsModal');
            if (modalEl && window.bootstrap) {
                const modal = new bootstrap.Modal(modalEl);
                modal.show();
            }
        }

        function getMusicSettingsHtml() {
            const isConnected = window.musicPlaylist.length > 0;
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

                    <!-- 2. PINDAI ULANG FOLDER -->
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
            if (!confirm('Kosongkan daftar lagu hasil unduhan dari komputer? Berkas musik di HP tidak ikut terhapus.')) return;
            window.musicPlayerAudio.pause();
            window.musicPlayerAudio.src = '';
            window.musicPcTracks = [];
            window.musicSource = 'pc';
            try { localStorage.setItem('singghara_music_source', 'pc'); } catch (e) {}
            applyMusicSource();
            window.currentMusicIndex = 0;
            window.musicSelectedFolder = null;

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
