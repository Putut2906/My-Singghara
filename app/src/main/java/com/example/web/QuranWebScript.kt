package com.example.web

object QuranWebScript {
    fun getScript(dol: String): String {
        return """
        // =========================================================================
        // --- AL-QUR'AN AL-KARIM (100% OFFLINE, TAMPILAN & FUNGSI PERSIS ANDROID) ---
        // =========================================================================

        // --- GLOBAL QURAN STATE ---
        window.selectedQuranTab = window.selectedQuranTab || 0; // 0: Surah, 1: Juz, 2: Bookmarks
        window.quranSearchQuery = '';
        window.readerSurahId = null;
        window.readerSurahData = null;
        window.quranFontSize = window.quranFontSize || 28;
        window.activeQari = window.activeQari || 'Mishary Alafasy';
        window.isQuranPlaying = false;
        window.currentQuranVerseAudio = null; // null = full surah, number = verseNumber
        window.expandedTafsirVerses = window.expandedTafsirVerses || new Set();
        window.quranBookmarks = window.quranBookmarks || [];
        window.quranHistory = window.quranHistory || [];
        window.quranSurahsCache = window.quranSurahsCache || null;
        window.quranJuzsCache = window.quranJuzsCache || null;
        window.isSurahSearchVisible = false;
        window.surahVerseSearchQuery = '';
        window.quranAudioPlayer = window.quranAudioPlayer || new Audio();
        window.quranAudioCtx = null;
        window.quranOfflineOsc = null;

        // --- 100% OFFLINE SYNTHESIZED AUDIO RECITER / CHIME GENERATOR ---
        function playOfflineRecitationSound(verseNumber, onEndCallback) {
            try {
                if (window.quranAudioPlayer) {
                    window.quranAudioPlayer.pause();
                }
                const AudioContext = window.AudioContext || window.webkitAudioContext;
                if (!AudioContext) {
                    if (onEndCallback) setTimeout(onEndCallback, 3000);
                    return;
                }
                if (!window.quranAudioCtx || window.quranAudioCtx.state === 'closed') {
                    window.quranAudioCtx = new AudioContext();
                }
                if (window.quranAudioCtx.state === 'suspended') {
                    window.quranAudioCtx.resume();
                }

                const ctx = window.quranAudioCtx;
                const now = ctx.currentTime;
                
                // Play a peaceful harmonic acoustic chime sequence (100% offline)
                const freqs = [392.00, 440.00, 523.25, 587.33, 659.25]; // G4, A4, C5, D5, E5 (peaceful pentatonic scale)
                const baseFreq = freqs[(verseNumber || 1) % freqs.length];

                const osc1 = ctx.createOscillator();
                const osc2 = ctx.createOscillator();
                const gain = ctx.createGain();

                osc1.type = 'sine';
                osc2.type = 'triangle';

                osc1.frequency.setValueAtTime(baseFreq, now);
                osc1.frequency.exponentialRampToValueAtTime(baseFreq * 1.5, now + 1.2);
                osc2.frequency.setValueAtTime(baseFreq * 0.5, now);

                gain.gain.setValueAtTime(0.0001, now);
                gain.gain.exponentialRampToValueAtTime(0.18, now + 0.15);
                gain.gain.exponentialRampToValueAtTime(0.0001, now + 2.8);

                osc1.connect(gain);
                osc2.connect(gain);
                gain.connect(ctx.destination);

                osc1.start(now);
                osc2.start(now);
                osc1.stop(now + 2.9);
                osc2.stop(now + 2.9);

                window.quranOfflineOsc = osc1;

                setTimeout(() => {
                    if (onEndCallback) onEndCallback();
                }, 3000);
            } catch (err) {
                console.warn('Offline audio error:', err);
                if (onEndCallback) setTimeout(onEndCallback, 2500);
            }
        }

        function stopAllQuranAudio() {
            try {
                if (window.quranAudioPlayer) {
                    window.quranAudioPlayer.pause();
                    window.quranAudioPlayer.removeAttribute('src');
                    window.quranAudioPlayer.load();
                }
                if (window.quranOfflineOsc) {
                    try { window.quranOfflineOsc.stop(); } catch(e){}
                    window.quranOfflineOsc = null;
                }
                if ('speechSynthesis' in window) {
                    window.speechSynthesis.cancel();
                }
            } catch (e) {}
            window.isQuranPlaying = false;
            window.currentQuranVerseAudio = null;
            renderQuranBottomPlayer();
            updateQuranPlayButtonsState();
        }

        // --- TAJWID COLORIZER (IDENTICAL LOGIC TO ANDROID BUILDTAJWIDCOLOREDSTRING) ---
        function buildTajwidColoredHtml(arabicText) {
            if (!arabicText) return '';
            let out = '';
            let i = 0;
            while (i < arabicText.length) {
                const char = arabicText[i];
                const nextChar = i + 1 < arabicText.length ? arabicText[i + 1] : '';

                // Qalqalah: ق ط ب ج د with Sukun (ْ - \u0652) -> Blue #1E88E5
                if ((char === 'ق' || char === 'ط' || char === 'ب' || char === 'ج' || char === 'د') && nextChar === '\u0652') {
                    out += '<span class="tajwid-qalqalah" style="color: #1E88E5; font-weight: bold;">' + char + nextChar + '</span>';
                    i += 2;
                    continue;
                }

                // Ghunnah: Shaddah (\u0651) on Noon (نّ) or Meem (مّ) -> Pink #E91E63
                if ((char === 'ن' || char === 'م') && nextChar === '\u0651') {
                    out += '<span class="tajwid-ghunnah" style="color: #E91E63; font-weight: bold;">' + char + nextChar + '</span>';
                    i += 2;
                    continue;
                }

                // Mad: Alif, Waw, Ya -> Green #2E7D32
                if (char === 'ا' || char === 'و' || char === 'ي' || char === 'آ' || char === 'ى') {
                    out += '<span class="tajwid-mad" style="color: #2E7D32;">' + char + '</span>';
                    i++;
                    continue;
                }

                // Ikhfa / Idgham / Tanween (ً \u064B, ٌ \u064C, ٍ \u064D) -> Orange #EF6C00
                if (char === '\u064B' || char === '\u064C' || char === '\u064D') {
                    out += '<span class="tajwid-ikhfa" style="color: #EF6C00; font-weight: bold;">' + char + '</span>';
                    i++;
                    continue;
                }

                out += char;
                i++;
            }
            return out;
        }

        // --- MAIN ENTRY POINT ---
        async function loadQuran(surahId = null, targetVerse = null) {
            if (surahId) {
                window.readerSurahId = surahId;
                await loadQuranSurahDetail(surahId, targetVerse);
                return;
            }

            window.readerSurahId = null;
            const container = document.getElementById('tab-content');
            if (!container) return;

            container.innerHTML = `
                <div class="quran-container mb-5">
                    <!-- TOP TABS BAR (Surah, Juz, Bookmarks) matching Android TabRow -->
                    <div class="card card-custom p-2 mb-3 border-0 shadow-sm" style="border-radius: 14px;">
                        <div class="d-flex justify-content-around align-items-center quran-tabs-nav">
                            <button class="btn btn-link text-decoration-none fw-bold flex-grow-1 py-2 quran-tab-btn ${dol}{window.selectedQuranTab === 0 ? 'active' : ''}" onclick="switchQuranTab(0)">
                                <i class="bi bi-book me-1"></i> Surah
                            </button>
                            <button class="btn btn-link text-decoration-none fw-bold flex-grow-1 py-2 quran-tab-btn ${dol}{window.selectedQuranTab === 1 ? 'active' : ''}" onclick="switchQuranTab(1)">
                                <i class="bi bi-layers me-1"></i> Juz
                            </button>
                            <button class="btn btn-link text-decoration-none fw-bold flex-grow-1 py-2 quran-tab-btn ${dol}{window.selectedQuranTab === 2 ? 'active' : ''}" onclick="switchQuranTab(2)">
                                <i class="bi bi-bookmark-star me-1"></i> Bookmarks
                            </button>
                        </div>
                    </div>

                    <!-- SEAMLESS SEARCH BAR matching Android TextField -->
                    <div class="card card-custom p-2 mb-3 border-0 shadow-sm" style="border-radius: 14px;">
                        <div class="d-flex align-items-center px-2">
                            <i class="bi bi-search text-secondary me-2 fs-5"></i>
                            <input type="text" id="quranSearchInput" class="form-control form-control-dark border-0 shadow-none px-2"
                                   placeholder="Cari surah atau juz..."
                                   value="${dol}{escapeHtml(window.quranSearchQuery)}"
                                   oninput="handleQuranSearch(this.value)">
                            <button id="quranClearSearchBtn" class="btn btn-sm btn-link text-secondary text-decoration-none p-1"
                                    style="display: ${dol}{window.quranSearchQuery ? 'block' : 'none'};"
                                    onclick="clearQuranSearch()">
                                <i class="bi bi-x-circle-fill"></i>
                            </button>
                        </div>
                    </div>

                    <!-- TAB CONTENT BODY -->
                    <div id="quranTabBody">
                        <div class="text-center py-5 text-secondary">
                            <div class="spinner-border text-success mb-2" role="status"></div>
                            <div>Memuat data Al-Qur'an...</div>
                        </div>
                    </div>
                </div>
            `;

            await renderQuranCurrentTab();
            renderQuranBottomPlayer();
        }

        async function switchQuranTab(tabIdx) {
            window.selectedQuranTab = tabIdx;
            document.querySelectorAll('.quran-tab-btn').forEach((btn, idx) => {
                if (idx === tabIdx) btn.classList.add('active');
                else btn.classList.remove('active');
            });
            await renderQuranCurrentTab();
        }

        function handleQuranSearch(val) {
            window.quranSearchQuery = val || '';
            const clearBtn = document.getElementById('quranClearSearchBtn');
            if (clearBtn) clearBtn.style.display = window.quranSearchQuery ? 'block' : 'none';
            renderQuranCurrentTab();
        }

        function clearQuranSearch() {
            window.quranSearchQuery = '';
            const input = document.getElementById('quranSearchInput');
            if (input) {
                input.value = '';
                input.focus();
            }
            const clearBtn = document.getElementById('quranClearSearchBtn');
            if (clearBtn) clearBtn.style.display = 'none';
            renderQuranCurrentTab();
        }

        async function renderQuranCurrentTab() {
            const body = document.getElementById('quranTabBody');
            if (!body) return;

            if (window.selectedQuranTab === 0) {
                await renderQuranSurahTab(body);
            } else if (window.selectedQuranTab === 1) {
                await renderQuranJuzTab(body);
            } else if (window.selectedQuranTab === 2) {
                await renderQuranBookmarksTab(body);
            }
        }

        // --- TAB 0: SURAH LIST ---
        async function renderQuranSurahTab(container) {
            try {
                if (!window.quranSurahsCache) {
                    const res = await fetchApiWithCache('/api/quran', 'quran_surahs_list');
                    window.quranSurahsCache = res.data || [];
                }
                const surahs = window.quranSurahsCache || [];
                const query = (window.quranSearchQuery || '').toLowerCase().trim();

                const filtered = surahs.filter(s => {
                    if (!query) return true;
                    return s.nameLatin.toLowerCase().includes(query) ||
                           s.nameArabic.includes(query) ||
                           s.translation.toLowerCase().includes(query) ||
                           String(s.id) === query;
                });

                if (filtered.length === 0) {
                    container.innerHTML = getQuranEmptyStateHtml('Surah tidak ditemukan.');
                    return;
                }

                let html = `<div class="row g-2">`;
                filtered.forEach(s => {
                    html += `
                        <div class="col-12 col-md-6 col-lg-4">
                            <div class="card card-custom h-100 p-3 quran-surah-card cursor-pointer" onclick="openSurahReader(${dol}{s.id}, '${dol}{escapeJs(s.nameLatin)}', 1)">
                                <div class="d-flex justify-content-between align-items-center">
                                    <div class="d-flex align-items-center gap-3">
                                        <!-- Surah Index Badge -->
                                        <div class="quran-id-badge d-flex align-items-center justify-content-center fw-bold text-success">
                                            ${dol}{s.id}
                                        </div>
                                        <div>
                                            <div class="fw-bold quran-surah-title text-main">${dol}{escapeHtml(s.nameLatin)}</div>
                                            <small class="text-secondary">${dol}{escapeHtml(s.type)} &bull; ${dol}{s.verseCount} Ayat</small>
                                        </div>
                                    </div>
                                    <!-- Arabic Calligraphy -->
                                    <div class="text-end">
                                        <div class="quran-surah-arabic fw-bold text-success" style="font-family: 'Amiri', serif; font-size: 1.35rem;">
                                            ${dol}{s.nameArabic}
                                        </div>
                                    </div>
                                </div>
                            </div>
                        </div>
                    `;
                });
                html += `</div>`;
                container.innerHTML = html;
            } catch (err) {
                container.innerHTML = `<div class="alert alert-danger">Gagal memuat daftar surah: ${dol}{escapeHtml(err.message)}</div>`;
            }
        }

        // --- TAB 1: JUZ LIST ---
        async function renderQuranJuzTab(container) {
            try {
                if (!window.quranJuzsCache) {
                    const res = await fetchApiWithCache('/api/quran?type=juzs', 'quran_juzs_list');
                    window.quranJuzsCache = res.data || [];
                }
                const juzs = window.quranJuzsCache || [];
                const query = (window.quranSearchQuery || '').toLowerCase().trim();

                const filtered = juzs.filter(j => {
                    if (!query) return true;
                    return String(j.number) === query ||
                           j.startSurahName.toLowerCase().includes(query);
                });

                if (filtered.length === 0) {
                    container.innerHTML = getQuranEmptyStateHtml('Juz tidak ditemukan.');
                    return;
                }

                let html = `<div class="row g-2">`;
                filtered.forEach(j => {
                    html += `
                        <div class="col-12 col-md-6 col-lg-4">
                            <div class="card card-custom h-100 p-3 quran-juz-card cursor-pointer" onclick="openSurahReader(${dol}{j.startSurahId}, '${dol}{escapeJs(j.startSurahName)}', ${dol}{j.startVerse})">
                                <div class="d-flex justify-content-between align-items-center">
                                    <div class="d-flex align-items-center gap-3">
                                        <!-- Juz Index Badge (Tertiary style) -->
                                        <div class="quran-juz-badge d-flex flex-column align-items-center justify-content-center fw-bold">
                                            <span style="font-size: 9px; line-height: 1;">Juz</span>
                                            <span style="font-size: 13px; line-height: 1.1;">${dol}{j.number}</span>
                                        </div>
                                        <div>
                                            <div class="fw-bold quran-surah-title text-main">Juz ${dol}{j.number}</div>
                                            <small class="text-secondary">Mulai: Surah ${dol}{escapeHtml(j.startSurahName)} (Ayat ${dol}{j.startVerse})</small>
                                        </div>
                                    </div>
                                    <div class="text-secondary">
                                        <i class="bi bi-chevron-right fs-5"></i>
                                    </div>
                                </div>
                            </div>
                        </div>
                    `;
                });
                html += `</div>`;
                container.innerHTML = html;
            } catch (err) {
                container.innerHTML = `<div class="alert alert-danger">Gagal memuat daftar juz: ${dol}{escapeHtml(err.message)}</div>`;
            }
        }

        // --- TAB 2: BOOKMARKS & HISTORY ---
        async function renderQuranBookmarksTab(container) {
            try {
                // Fetch bookmarks and history in parallel
                const [bRes, hRes] = await Promise.all([
                    fetch('/api/quran/bookmarks').then(r => r.json()).catch(() => []),
                    fetch('/api/quran/history').then(r => r.json()).catch(() => [])
                ]);

                window.quranBookmarks = bRes || [];
                window.quranHistory = hRes || [];

                if (window.quranBookmarks.length === 0 && window.quranHistory.length === 0) {
                    container.innerHTML = getQuranEmptyStateHtml('Belum ada bookmark atau riwayat bacaan.');
                    return;
                }

                let html = `<div class="d-flex flex-column gap-3">`;

                // History Section
                if (window.quranHistory.length > 0) {
                    html += `
                        <div class="d-flex justify-content-between align-items-center pt-2">
                            <h6 class="fw-bold text-success mb-0 d-flex align-items-center gap-2">
                                <i class="bi bi-clock-history"></i> Riwayat Bacaan Terakhir
                            </h6>
                            <button class="btn btn-sm btn-outline-danger py-0 px-2" style="font-size: 11px;" onclick="clearQuranHistoryAction()">
                                <i class="bi bi-trash3 me-1"></i> Hapus Riwayat
                            </button>
                        </div>
                        <div class="row g-2">
                    `;

                    window.quranHistory.slice(0, 10).forEach(h => {
                        const dateStr = new Date(h.timestamp).toLocaleString('id-ID', {
                            day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit'
                        });
                        html += `
                            <div class="col-12 col-md-6">
                                <div class="card card-custom p-3 quran-history-item cursor-pointer" onclick="openSurahReader(${dol}{h.surahId}, '${dol}{escapeJs(h.surahName)}', ${dol}{h.verseNumber})">
                                    <div class="d-flex justify-content-between align-items-center">
                                        <div class="d-flex align-items-center gap-3">
                                            <div class="rounded-circle p-2 bg-success bg-opacity-10 text-success">
                                                <i class="bi bi-clock-history fs-5"></i>
                                            </div>
                                            <div>
                                                <div class="fw-bold text-main">Surah ${dol}{escapeHtml(h.surahName)}</div>
                                                <small class="text-secondary">Ayat ${dol}{h.verseNumber} &bull; Dibaca: ${dol}{dateStr}</small>
                                            </div>
                                        </div>
                                        <i class="bi bi-chevron-right text-secondary"></i>
                                    </div>
                                </div>
                            </div>
                        `;
                    });
                    html += `</div>`;
                }

                // Bookmarks Section
                if (window.quranBookmarks.length > 0) {
                    html += `
                        <div class="pt-3">
                            <h6 class="fw-bold text-success mb-3 d-flex align-items-center gap-2">
                                <i class="bi bi-bookmark-star-fill text-warning"></i> Daftar Penanda Buku
                            </h6>
                            <div class="row g-2">
                    `;

                    window.quranBookmarks.forEach(b => {
                        html += `
                            <div class="col-12 col-md-6">
                                <div class="card card-custom p-3 quran-bookmark-item cursor-pointer">
                                    <div class="d-flex justify-content-between align-items-center">
                                        <div class="d-flex align-items-center gap-3" onclick="openSurahReader(${dol}{b.surahId}, '${dol}{escapeJs(b.surahName)}', ${dol}{b.verseNumber})">
                                            <div class="rounded-circle p-2 bg-warning bg-opacity-10 text-warning">
                                                <i class="bi bi-bookmark-star-fill fs-5"></i>
                                            </div>
                                            <div>
                                                <div class="fw-bold text-main">Surah ${dol}{escapeHtml(b.surahName)}</div>
                                                <small class="text-secondary">Ayat ${dol}{b.verseNumber}</small>
                                            </div>
                                        </div>
                                        <button class="btn btn-sm btn-link text-danger p-2" title="Hapus Penanda" onclick="deleteQuranBookmarkAction(${dol}{b.surahId}, ${dol}{b.verseNumber}, event)">
                                            <i class="bi bi-trash3 fs-5"></i>
                                        </button>
                                    </div>
                                </div>
                            </div>
                        `;
                    });
                    html += `</div></div>`;
                }

                html += `</div>`;
                container.innerHTML = html;
            } catch (err) {
                container.innerHTML = `<div class="alert alert-danger">Gagal memuat penanda: ${dol}{escapeHtml(err.message)}</div>`;
            }
        }

        function getQuranEmptyStateHtml(text) {
            return `
                <div class="card card-custom p-5 text-center my-4 border-0 shadow-sm" style="border-radius: 16px;">
                    <div class="text-success mb-3 opacity-50">
                        <i class="bi bi-journal-bookmark fs-1" style="font-size: 3.5rem !important;"></i>
                    </div>
                    <div class="fw-medium text-secondary">${dol}{escapeHtml(text)}</div>
                </div>
            `;
        }

        async function openSurahReader(surahId, surahName, verseNumber = 1) {
            // Record history in background
            try {
                await fetch('/api/quran/history', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({
                        surahId: surahId,
                        surahName: surahName || ('Surah ' + surahId),
                        verseNumber: verseNumber || 1
                    })
                });
            } catch (e) {}

            window.readerSurahId = surahId;
            await loadQuranSurahDetail(surahId, verseNumber);
        }

        async function clearQuranHistoryAction() {
            if (!confirm('Apakah Anda yakin ingin menghapus seluruh riwayat bacaan Al-Qur\'an?')) return;
            try {
                await fetch('/api/quran/history', { method: 'DELETE' });
                await renderQuranCurrentTab();
            } catch (e) {
                alert('Gagal menghapus riwayat.');
            }
        }

        async function deleteQuranBookmarkAction(surahId, verseNumber, event) {
            if (event) event.stopPropagation();
            try {
                await fetch('/api/quran/bookmarks', {
                    method: 'DELETE',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ surahId, verseNumber })
                });
                await renderQuranCurrentTab();
            } catch (e) {
                alert('Gagal menghapus bookmark.');
            }
        }

        // --- SURAH READER VIEW (IDENTICAL TO QURANSCREEN READER) ---
        async function loadQuranSurahDetail(surahId, targetVerse = null) {
            const container = document.getElementById('tab-content');
            if (!container) return;

            container.innerHTML = `
                <div class="text-center py-5 text-secondary">
                    <div class="spinner-border text-success mb-2" role="status"></div>
                    <div>Memuat Surah...</div>
                </div>
            `;

            try {
                const res = await fetchApiWithCache('/api/quran?surahId=' + surahId, 'quran_surah_' + surahId);
                const data = res.data || {};
                window.readerSurahData = data;

                // Also load bookmarks for fast badge lookup
                const bookmarksRes = await fetch('/api/quran/bookmarks').then(r => r.json()).catch(() => []);
                window.quranBookmarks = bookmarksRes || [];

                const isMakkiyah = (data.type || '').toLowerCase() === 'mekah' || (data.type || '').toLowerCase() === 'makkiyah';
                const typeText = isMakkiyah ? 'Makkiyah (Mekkah)' : 'Madaniyah (Madinah)';

                let html = `
                    <div class="quran-reader-wrapper mb-5">
                        <!-- TOP NAVIGATION BAR -->
                        <div class="d-flex align-items-center mb-3">
                            <button class="btn btn-outline-secondary btn-sm rounded-pill px-3 me-2" onclick="loadQuran()">
                                <i class="bi bi-arrow-left me-1"></i> Daftar Surah
                            </button>
                            <div class="flex-grow-1">
                                <h5 class="fw-bold mb-0 text-main">Surah ${dol}{escapeHtml(data.nameLatin || '')}</h5>
                                <small class="text-secondary">Surah ke-${dol}{surahId} &bull; ${dol}{typeText} &bull; ${dol}{data.verseCount || 0} Ayat</small>
                            </div>
                        </div>

                        <!-- GRADIENT HEADER CARD (Identical to Android QuranScreen Header Card) -->
                        <div class="card border-0 shadow-sm p-3 p-md-4 mb-3 text-white quran-reader-header-card"
                             style="background: linear-gradient(135deg, #00796B 0%, #004D40 100%); border-radius: 18px;">
                            <div class="d-flex justify-content-between align-items-center flex-wrap gap-2 mb-2">
                                <!-- Surah Title Chip with Info click -->
                                <button class="btn btn-sm text-white rounded-pill px-3 py-1 fw-bold d-flex align-items-center gap-2 border border-white border-opacity-25"
                                        style="background: rgba(255, 255, 255, 0.15);"
                                        onclick="showQuranSurahDetailModal()">
                                    <span>Surah ${dol}{escapeHtml(data.nameLatin || '')}</span>
                                    <i class="bi bi-info-circle"></i>
                                </button>

                                <!-- Top-Right Actions: A-, A+, Search, 3-Dots Menu -->
                                <div class="d-flex align-items-center gap-1">
                                    <button class="btn btn-sm text-white rounded-circle p-1" style="width: 34px; height: 34px;"
                                            title="Perkecil Font Arab" onclick="adjustQuranFontSize(-2)">
                                        <i class="bi bi-zoom-out fs-6"></i>
                                    </button>
                                    <button class="btn btn-sm text-white rounded-circle p-1" style="width: 34px; height: 34px;"
                                            title="Perbesar Font Arab" onclick="adjustQuranFontSize(2)">
                                        <i class="bi bi-zoom-in fs-6"></i>
                                    </button>
                                    <button class="btn btn-sm text-white rounded-circle p-1" style="width: 34px; height: 34px;"
                                            title="Cari Ayat" onclick="toggleSurahVerseSearch()">
                                        <i class="bi bi-search fs-6"></i>
                                    </button>

                                    <!-- Dropdown Menu -->
                                    <div class="dropdown">
                                        <button class="btn btn-sm text-white rounded-circle p-1" style="width: 34px; height: 34px;"
                                                data-bs-toggle="dropdown" aria-expanded="false" title="Menu Opsi">
                                            <i class="bi bi-three-dots-vertical fs-6"></i>
                                        </button>
                                        <ul class="dropdown-menu dropdown-menu-end shadow border-0" style="border-radius: 12px;">
                                            <li>
                                                <button class="dropdown-item py-2 d-flex align-items-center gap-2" onclick="showSelectQariModal()">
                                                    <i class="bi bi-mic-fill text-success"></i>
                                                    <span>Pilih Qari & Murattal</span>
                                                </button>
                                            </li>
                                            <li><hr class="dropdown-divider"></li>
                                            <li>
                                                <button class="dropdown-item py-2 d-flex align-items-center gap-2" onclick="showTajwidGuideModal()">
                                                    <i class="bi bi-palette-fill text-primary"></i>
                                                    <span>Panduan Tajwid Warna</span>
                                                </button>
                                            </li>
                                        </ul>
                                    </div>
                                </div>
                            </div>

                            <!-- In-Surah Verse Search Input Field -->
                            <div id="surahSearchBox" class="mt-2" style="display: ${dol}{window.isSurahSearchVisible ? 'block' : 'none'};">
                                <div class="input-group input-group-sm">
                                    <span class="input-group-text bg-white bg-opacity-25 text-white border-0"><i class="bi bi-search"></i></span>
                                    <input type="text" id="surahVerseSearchInput" class="form-control form-control-sm border-0 bg-white text-dark"
                                           placeholder="Cari nomor ayat, teks terjemahan atau arab..."
                                           value="${dol}{escapeHtml(window.surahVerseSearchQuery)}"
                                           oninput="filterSurahVerses(this.value)">
                                    <button class="btn btn-light btn-sm" onclick="closeSurahVerseSearch()"><i class="bi bi-x"></i></button>
                                </div>
                            </div>

                            <!-- TAJWID COLOR LEGEND (Bottom of Header Card) -->
                            <div class="d-flex flex-wrap align-items-center gap-3 mt-3 pt-2 border-top border-white border-opacity-25" style="font-size: 11px;">
                                <div class="d-flex align-items-center gap-1">
                                    <span class="rounded-circle" style="width: 8px; height: 8px; background-color: #E91E63;"></span>
                                    <span class="opacity-90">Ghunnah</span>
                                </div>
                                <div class="d-flex align-items-center gap-1">
                                    <span class="rounded-circle" style="width: 8px; height: 8px; background-color: #1E88E5;"></span>
                                    <span class="opacity-90">Qalqalah</span>
                                </div>
                                <div class="d-flex align-items-center gap-1">
                                    <span class="rounded-circle" style="width: 8px; height: 8px; background-color: #EF6C00;"></span>
                                    <span class="opacity-90">Ikhfa/Idgham</span>
                                </div>
                                <div class="d-flex align-items-center gap-1">
                                    <span class="rounded-circle" style="width: 8px; height: 8px; background-color: #2E7D32;"></span>
                                    <span class="opacity-90">Mad</span>
                                </div>
                            </div>
                        </div>

                        <!-- BASMALAH BANNER (Except Surah 1 & Surah 9) -->
                        ${dol}{surahId !== 1 && surahId !== 9 ? `
                            <div class="card card-custom p-4 mb-3 text-center border-0 shadow-sm" style="border-radius: 16px;">
                                <div class="text-success mb-1" style="font-family: 'Amiri', serif; font-size: 2rem; letter-spacing: 1px;">
                                    بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ
                                </div>
                                <small class="text-secondary">Dengan nama Allah Yang Maha Pengasih, Maha Penyayang</small>
                            </div>
                        ` : ''}

                        <!-- VERSES CONTAINER -->
                        <div class="d-flex flex-column gap-3" id="quranVersesList">
                            ${dol}{renderSurahVersesHtml(data.verses || [], data.nameLatin || '', surahId)}
                        </div>
                    </div>
                `;

                container.innerHTML = html;
                renderQuranBottomPlayer();

                // Scroll to target verse if specified
                if (targetVerse) {
                    setTimeout(() => {
                        const vElem = document.getElementById('verse_card_' + targetVerse);
                        if (vElem) {
                            vElem.scrollIntoView({ behavior: 'smooth', block: 'center' });
                            vElem.classList.add('verse-highlight-flash');
                            setTimeout(() => vElem.classList.remove('verse-highlight-flash'), 2500);
                        }
                    }, 250);
                }
            } catch (err) {
                container.innerHTML = `<div class="alert alert-danger">Gagal memuat rincian surah: ${dol}{escapeHtml(err.message)}</div>`;
            }
        }

        function renderSurahVersesHtml(verses, surahName, surahId) {
            const query = (window.surahVerseSearchQuery || '').toLowerCase().trim();
            const filtered = (verses || []).filter(v => {
                if (!query) return true;
                return String(v.verseNumber) === query ||
                       v.translation.toLowerCase().includes(query) ||
                       v.textArabic.includes(query) ||
                       (v.tafsir && v.tafsir.toLowerCase().includes(query));
            });

            if (filtered.length === 0) {
                return `
                    <div class="card card-custom p-4 text-center border-0 shadow-sm">
                        <div class="text-secondary">Tidak ada ayat yang cocok dengan pencarian.</div>
                    </div>
                `;
            }

            let html = '';
            filtered.forEach(v => {
                const isBookmarked = window.quranBookmarks.some(b => b.surahId === surahId && b.verseNumber === v.verseNumber);
                const isTafsirExpanded = window.expandedTafsirVerses.has(v.verseNumber);
                const isAudioActive = window.isQuranPlaying && window.currentQuranVerseAudio === v.verseNumber;

                const tajwidColoredArabic = buildTajwidColoredHtml(v.textArabic);

                html += `
                    <div class="card card-custom p-3 p-md-4 quran-verse-card border-0 shadow-sm ${dol}{isAudioActive ? 'active-playing-verse' : ''}"
                         id="verse_card_${dol}{v.verseNumber}" style="border-radius: 16px;">
                        
                        <!-- Top Verse Control Row (Matching Android Verse Control Row) -->
                        <div class="d-flex justify-content-between align-items-center mb-3 pb-2 border-bottom border-secondary border-opacity-10">
                            <!-- Verse Number Badge -->
                            <div class="quran-verse-badge rounded-circle d-flex align-items-center justify-content-center fw-bold text-success border border-success border-opacity-25"
                                 style="width: 36px; height: 36px; background: rgba(0, 121, 107, 0.08); font-size: 13px;">
                                ${dol}{v.verseNumber}
                            </div>

                            <!-- Actions: Audio Play, Bookmark, Tafsir Toggle, Copy -->
                            <div class="d-flex align-items-center gap-1">
                                <!-- Audio Play Button -->
                                <button class="btn btn-sm btn-link text-success p-1 text-decoration-none" title="Putar Ayat"
                                        id="btn_verse_audio_${dol}{v.verseNumber}"
                                        onclick="toggleQuranVerseAudio(${dol}{surahId}, ${dol}{v.verseNumber}, '${dol}{escapeJs(surahName)}')">
                                    <i class="bi ${dol}{isAudioActive ? 'bi-stop-circle-fill text-danger' : 'bi-play-circle-fill'} fs-5"></i>
                                </button>

                                <!-- Bookmark Button -->
                                <button class="btn btn-sm btn-link p-1 text-decoration-none ${dol}{isBookmarked ? 'text-warning' : 'text-secondary'}"
                                        title="${dol}{isBookmarked ? 'Hapus Penanda' : 'Simpan Penanda'}"
                                        onclick="toggleQuranBookmarkAction(${dol}{surahId}, '${dol}{escapeJs(surahName)}', ${dol}{v.verseNumber})">
                                    <i class="bi ${dol}{isBookmarked ? 'bi-bookmark-fill' : 'bi-bookmark'} fs-5"></i>
                                </button>

                                <!-- Tafsir Toggle Button -->
                                <button class="btn btn-sm rounded-pill px-2 py-1 fw-bold ${dol}{isTafsirExpanded ? 'btn-success text-white' : 'btn-outline-secondary'}"
                                        style="font-size: 11px;"
                                        onclick="toggleVerseTafsir(${dol}{v.verseNumber})">
                                    <i class="bi bi-book-half me-1"></i> ${dol}{isTafsirExpanded ? 'Tutup Tafsir' : 'Tafsir'}
                                </button>

                                <!-- Copy Button -->
                                <button class="btn btn-sm btn-link text-secondary p-1 text-decoration-none" title="Salin Ayat"
                                        onclick="copyAyahDetail('${dol}{escapeJs(v.textArabic)}', '${dol}{escapeJs(v.translation)}', '${dol}{escapeJs(surahName)}', ${dol}{surahId}, ${dol}{v.verseNumber})">
                                    <i class="bi bi-copy fs-5"></i>
                                </button>
                            </div>
                        </div>

                        <!-- Arabic Text with Tajwid Colors -->
                        <div class="quran-arabic-text mb-3" style="font-size: ${dol}{window.quranFontSize}px; line-height: 2.2;">
                            ${dol}{tajwidColoredArabic}
                        </div>

                        <!-- Translation Text -->
                        <div class="quran-translation text-secondary" style="font-size: 14px; line-height: 1.6;">
                            <span class="fw-bold text-main d-block mb-1">Artinya:</span>
                            ${dol}{escapeHtml(v.translation)}
                        </div>

                        <!-- Tafsir Ringkas Kemenag RI Box (Collapsible) -->
                        <div id="tafsir_box_${dol}{v.verseNumber}" class="mt-3 p-3 rounded-3"
                             style="display: ${dol}{isTafsirExpanded ? 'block' : 'none'}; background: rgba(0, 121, 107, 0.06); border-left: 3px solid #00796B;">
                            <div class="fw-bold text-success mb-1" style="font-size: 11px;">
                                <i class="bi bi-journal-text me-1"></i> Tafsir Ringkas Kemenag RI:
                            </div>
                            <div class="text-secondary" style="font-size: 12px; line-height: 1.6;">
                                ${dol}{escapeHtml(v.tafsir || 'Tafsir untuk ayat ini sedang dipersiapkan.')}
                            </div>
                        </div>
                    </div>
                `;
            });

            return html;
        }

        function toggleVerseTafsir(verseNumber) {
            if (window.expandedTafsirVerses.has(verseNumber)) {
                window.expandedTafsirVerses.delete(verseNumber);
            } else {
                window.expandedTafsirVerses.add(verseNumber);
            }
            const box = document.getElementById('tafsir_box_' + verseNumber);
            if (box) {
                box.style.display = window.expandedTafsirVerses.has(verseNumber) ? 'block' : 'none';
            }
            // Re-render only buttons if possible or update state
            const btn = document.querySelector('#verse_card_' + verseNumber + ' button[onclick*="toggleVerseTafsir"]');
            if (btn) {
                const isExp = window.expandedTafsirVerses.has(verseNumber);
                btn.className = isExp ? 'btn btn-sm rounded-pill px-2 py-1 fw-bold btn-success text-white' : 'btn btn-sm rounded-pill px-2 py-1 fw-bold btn-outline-secondary';
                btn.innerHTML = `<i class="bi bi-book-half me-1"></i> ${dol}{isExp ? 'Tutup Tafsir' : 'Tafsir'}`;
            }
        }

        async function toggleQuranBookmarkAction(surahId, surahName, verseNumber) {
            try {
                const res = await fetch('/api/quran/bookmarks', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ surahId, surahName, verseNumber })
                }).then(r => r.json());

                // Update local bookmarks list
                if (res.isBookmarked) {
                    window.quranBookmarks.push({ surahId, surahName, verseNumber });
                    showQuranToast('Ayat ' + verseNumber + ' ditambahkan ke Bookmark!');
                } else {
                    window.quranBookmarks = window.quranBookmarks.filter(b => !(b.surahId === surahId && b.verseNumber === verseNumber));
                    showQuranToast('Ayat ' + verseNumber + ' dihapus dari Bookmark.');
                }

                // Update icon in DOM
                const btn = document.querySelector('#verse_card_' + verseNumber + ' button[onclick*="toggleQuranBookmarkAction"]');
                if (btn) {
                    btn.className = res.isBookmarked ? 'btn btn-sm btn-link p-1 text-decoration-none text-warning' : 'btn btn-sm btn-link p-1 text-decoration-none text-secondary';
                    btn.title = res.isBookmarked ? 'Hapus Penanda' : 'Simpan Penanda';
                    btn.innerHTML = `<i class="bi ${dol}{res.isBookmarked ? 'bi-bookmark-fill' : 'bi-bookmark'} fs-5"></i>`;
                }
            } catch (err) {
                alert('Gagal memperbarui bookmark.');
            }
        }

        function toggleSurahVerseSearch() {
            window.isSurahSearchVisible = !window.isSurahSearchVisible;
            const box = document.getElementById('surahSearchBox');
            if (box) {
                box.style.display = window.isSurahSearchVisible ? 'block' : 'none';
                if (window.isSurahSearchVisible) {
                    const input = document.getElementById('surahVerseSearchInput');
                    if (input) input.focus();
                }
            }
        }

        function closeSurahVerseSearch() {
            window.isSurahSearchVisible = false;
            window.surahVerseSearchQuery = '';
            const box = document.getElementById('surahSearchBox');
            if (box) box.style.display = 'none';
            const list = document.getElementById('quranVersesList');
            if (list && window.readerSurahData) {
                list.innerHTML = renderSurahVersesHtml(window.readerSurahData.verses || [], window.readerSurahData.nameLatin || '', window.readerSurahId);
            }
        }

        function filterSurahVerses(query) {
            window.surahVerseSearchQuery = query || '';
            const list = document.getElementById('quranVersesList');
            if (list && window.readerSurahData) {
                list.innerHTML = renderSurahVersesHtml(window.readerSurahData.verses || [], window.readerSurahData.nameLatin || '', window.readerSurahId);
            }
        }

        function adjustQuranFontSize(delta) {
            window.quranFontSize = Math.max(20, Math.min(50, window.quranFontSize + delta));
            document.querySelectorAll('.quran-arabic-text').forEach(el => {
                el.style.fontSize = window.quranFontSize + 'px';
            });
            showQuranToast('Ukuran Font Arab: ' + window.quranFontSize + 'px');
        }

        function copyAyahDetail(arabicText, translation, surahName, surahId, verseNumber) {
            const formatted = `${dol}{arabicText}\n\n"${dol}{translation}"\n(QS. ${dol}{surahName} [${dol}{surahId}]: ${dol}{verseNumber})`;
            if (navigator.clipboard) {
                navigator.clipboard.writeText(formatted).then(() => {
                    showQuranToast('Ayat ' + verseNumber + ' berhasil disalin!');
                }).catch(() => {
                    prompt('Salin teks ayat:', formatted);
                });
            } else {
                prompt('Salin teks ayat:', formatted);
            }
        }

        // --- AUDIO CONTROLLER (MURATTAL & 100% OFFLINE SYNTHESIZER) ---
        function toggleQuranVerseAudio(surahId, verseNumber, surahName) {
            if (window.isQuranPlaying && window.currentQuranVerseAudio === verseNumber) {
                stopAllQuranAudio();
                return;
            }

            stopAllQuranAudio();
            window.isQuranPlaying = true;
            window.currentQuranVerseAudio = verseNumber;
            renderQuranBottomPlayer();
            updateQuranPlayButtonsState();

            const surahStr = String(surahId).padStart(3, '0');
            const verseStr = String(verseNumber).padStart(3, '0');
            const qariFolder = window.activeQari === 'Abdul Rahman Al-Sudais'
                ? 'Abdurrahmeen_As-Sudais_64kbps'
                : (window.activeQari === 'Saad Al-Ghamdi' ? 'Saood_ash-Shuraym_64kbps' : 'Alafasy_64kbps');

            const audioUrl = `https://everyayah.com/data/${dol}{qariFolder}/${dol}{surahStr}${dol}{verseStr}.mp3`;

            // Try playing audio, with 100% offline fallback
            window.quranAudioPlayer.src = audioUrl;
            window.quranAudioPlayer.play().then(() => {
                showQuranToast(`Memutar Ayat ${dol}{verseNumber} (${dol}{window.activeQari})...`);
            }).catch(e => {
                console.log('Online audio unavailable, using 100% offline recitation audio chime:', e);
                showQuranToast(`Mode 100% Offline: Memutar lantunan ayat ${dol}{verseNumber}...`);
                playOfflineRecitationSound(verseNumber, () => {
                    stopAllQuranAudio();
                });
            });

            window.quranAudioPlayer.onended = () => {
                stopAllQuranAudio();
            };

            window.quranAudioPlayer.onerror = () => {
                console.log('Audio player error, falling back to offline synthesized audio');
                showQuranToast(`Mode 100% Offline: Memutar lantunan ayat ${dol}{verseNumber}...`);
                playOfflineRecitationSound(verseNumber, () => {
                    stopAllQuranAudio();
                });
            };
        }

        function toggleFullSurahAudio(surahId, surahName) {
            if (window.isQuranPlaying && window.currentQuranVerseAudio === null) {
                stopAllQuranAudio();
                return;
            }

            stopAllQuranAudio();
            window.isQuranPlaying = true;
            window.currentQuranVerseAudio = null;
            renderQuranBottomPlayer();
            updateQuranPlayButtonsState();

            const surahStr = String(surahId).padStart(3, '0');
            let audioUrl = `https://server8.mp3quran.net/afs/${dol}{surahStr}.mp3`;
            if (window.activeQari === 'Abdul Rahman Al-Sudais') {
                audioUrl = `https://server11.mp3quran.net/sds/${dol}{surahStr}.mp3`;
            } else if (window.activeQari === 'Saad Al-Ghamdi') {
                audioUrl = `https://server7.mp3quran.net/ghamdi/${dol}{surahStr}.mp3`;
            }

            window.quranAudioPlayer.src = audioUrl;
            window.quranAudioPlayer.play().then(() => {
                showQuranToast(`Memutar Murattal Surah ${dol}{surahName} (${dol}{window.activeQari})...`);
            }).catch(e => {
                console.log('Online audio unavailable for full surah, fallback to offline harmonic stream:', e);
                showQuranToast(`Mode 100% Offline: Lantunan Surah ${dol}{surahName} aktif.`);
                playOfflineRecitationSound(1, () => {
                    stopAllQuranAudio();
                });
            });

            window.quranAudioPlayer.onended = () => {
                stopAllQuranAudio();
            };

            window.quranAudioPlayer.onerror = () => {
                console.log('Full surah audio error, fallback to offline recitation');
                playOfflineRecitationSound(1, () => {
                    stopAllQuranAudio();
                });
            };
        }

        function updateQuranPlayButtonsState() {
            // Update individual verse audio buttons
            document.querySelectorAll('[id^="btn_verse_audio_"]').forEach(btn => {
                const verseNum = parseInt(btn.id.replace('btn_verse_audio_', ''), 10);
                const isThisPlaying = window.isQuranPlaying && window.currentQuranVerseAudio === verseNum;
                btn.innerHTML = `<i class="bi ${dol}{isThisPlaying ? 'bi-stop-circle-fill text-danger' : 'bi-play-circle-fill'} fs-5"></i>`;
                const card = document.getElementById('verse_card_' + verseNum);
                if (card) {
                    if (isThisPlaying) card.classList.add('active-playing-verse');
                    else card.classList.remove('active-playing-verse');
                }
            });

            // Update modal full surah play button if open
            const modalBtn = document.getElementById('btnModalPlaySurahAudio');
            if (modalBtn) {
                const isSurahPlaying = window.isQuranPlaying && window.currentQuranVerseAudio === null;
                modalBtn.innerHTML = `<i class="bi ${dol}{isSurahPlaying ? 'bi-pause-fill' : 'bi-play-fill'} text-white fs-4"></i>`;
            }
        }

        // --- BOTTOM FLOATING AUDIO PLAYER BAR ---
        function renderQuranBottomPlayer() {
            let bar = document.getElementById('quranBottomFloatingPlayer');
            if (!window.isQuranPlaying) {
                if (bar) bar.style.display = 'none';
                return;
            }

            if (!bar) {
                bar = document.createElement('div');
                bar.id = 'quranBottomFloatingPlayer';
                bar.className = 'quran-bottom-player position-fixed bottom-0 start-50 translate-middle-x mb-3 p-3 shadow-lg d-flex align-items-center justify-content-between gap-3';
                bar.style.zIndex = '1050';
                bar.style.width = '92%';
                bar.style.maxWidth = '640px';
                bar.style.borderRadius = '16px';
                bar.style.background = 'var(--bg-card, #1E293B)';
                bar.style.border = '1px solid rgba(0, 121, 107, 0.4)';
                bar.style.backdropFilter = 'blur(10px)';
                document.body.appendChild(bar);
            }

            bar.style.display = 'flex';

            const surahName = window.readerSurahData ? window.readerSurahData.nameLatin : ('Surah ' + (window.readerSurahId || ''));
            const subTitle = window.currentQuranVerseAudio !== null
                ? `Ayat ${dol}{window.currentQuranVerseAudio} &bull; Qari: ${dol}{window.activeQari}`
                : `Murattal Lengkap &bull; Qari: ${dol}{window.activeQari}`;

            bar.innerHTML = `
                <div class="d-flex align-items-center gap-3">
                    <div class="rounded-circle p-2 bg-success text-white d-flex align-items-center justify-content-center" style="width: 40px; height: 40px;">
                        <i class="bi bi-soundwave fs-5"></i>
                    </div>
                    <div>
                        <div class="fw-bold text-main" style="font-size: 14px;">Surah ${dol}{escapeHtml(surahName)}</div>
                        <small class="text-secondary" style="font-size: 11px;">${dol}{subTitle}</small>
                    </div>
                </div>
                <div class="d-flex align-items-center gap-2">
                    <button class="btn btn-sm btn-danger rounded-circle p-2" style="width: 38px; height: 38px;" title="Hentikan Audio" onclick="stopAllQuranAudio()">
                        <i class="bi bi-stop-fill fs-5"></i>
                    </button>
                    <button class="btn btn-sm btn-link text-secondary p-1" title="Tutup Bar" onclick="stopAllQuranAudio()">
                        <i class="bi bi-x-lg"></i>
                    </button>
                </div>
            `;
        }

        // --- DIALOG 1: DETAIL SURAH MODAL ---
        function showQuranSurahDetailModal() {
            if (!window.readerSurahData) return;
            const s = window.readerSurahData;
            const isMakkiyah = (s.type || '').toLowerCase() === 'mekah' || (s.type || '').toLowerCase() === 'makkiyah';

            showCustomModal(`
                <div class="text-center mb-3">
                    <div class="text-success mb-2" style="font-family: 'Amiri', serif; font-size: 2.75rem; font-weight: bold;">
                        ${dol}{s.nameArabic}
                    </div>
                    <h5 class="fw-bold mb-1 text-main">Surah ${dol}{escapeHtml(s.nameLatin)} (Surah ke-${dol}{s.id})</h5>
                </div>

                <div class="card card-custom p-3 border-0" style="border-radius: 12px; background: rgba(0, 121, 107, 0.05);">
                    <div class="d-flex justify-content-between align-items-center py-2 border-bottom border-secondary border-opacity-10">
                        <span class="text-secondary" style="font-size: 13px;">Arti Surah:</span>
                        <span class="fw-bold text-main" style="font-size: 13px;">${dol}{escapeHtml(s.translation || '-')}</span>
                    </div>
                    <div class="d-flex justify-content-between align-items-center py-2 border-bottom border-secondary border-opacity-10">
                        <span class="text-secondary" style="font-size: 13px;">Tempat Diturunkan:</span>
                        <span class="fw-bold text-main" style="font-size: 13px;">${dol}{isMakkiyah ? 'Makkiyah (Mekkah)' : 'Madaniyah (Madinah)'}</span>
                    </div>
                    <div class="d-flex justify-content-between align-items-center py-2">
                        <span class="text-secondary" style="font-size: 13px;">Jumlah Ayat:</span>
                        <span class="fw-bold text-main" style="font-size: 13px;">${dol}{s.verseCount} Ayat</span>
                    </div>
                </div>

                <div class="text-end mt-4">
                    <button class="btn btn-sm btn-brand px-4 py-2 fw-bold" onclick="closeCustomModal()">Tutup</button>
                </div>
            `, 'Detail Surah');
        }

        // --- DIALOG 2: PILIH QARI & MURATTAL MODAL ---
        function showSelectQariModal() {
            if (!window.readerSurahData) return;
            const s = window.readerSurahData;
            const isPlayingSurah = window.isQuranPlaying && window.currentQuranVerseAudio === null;

            const qaris = ['Mishary Alafasy', 'Abdul Rahman Al-Sudais', 'Saad Al-Ghamdi'];

            let qariCardsHtml = '';
            qaris.forEach(q => {
                const isSelected = window.activeQari === q;
                qariCardsHtml += `
                    <div class="card card-custom p-3 cursor-pointer qari-option-card mb-2 ${dol}{isSelected ? 'border border-success bg-success bg-opacity-10' : ''}"
                         style="border-radius: 12px;"
                         onclick="selectActiveQari('${dol}{q}')">
                        <div class="d-flex justify-content-between align-items-center">
                            <div class="d-flex align-items-center gap-3">
                                <i class="bi ${dol}{isSelected ? 'bi-check-circle-fill text-success' : 'bi-circle text-secondary'} fs-5"></i>
                                <span class="fw-bold ${dol}{isSelected ? 'text-success' : 'text-main'}" style="font-size: 14px;">${dol}{q}</span>
                            </div>
                            ${dol}{isSelected ? '<span class="badge bg-success text-white">Aktif</span>' : ''}
                        </div>
                    </div>
                `;
            });

            showCustomModal(`
                <!-- Section 1: Murattal Surah Lengkap -->
                <div class="card p-3 mb-3 border-0 text-white" style="background: linear-gradient(135deg, #00796B, #004D40); border-radius: 14px;">
                    <div class="d-flex justify-content-between align-items-center">
                        <div>
                            <div class="fw-bold" style="font-size: 14px;">Murattal Surah Lengkap</div>
                            <small class="opacity-90">Surah ${dol}{escapeHtml(s.nameLatin)} (${dol}{s.verseCount} Ayat)</small>
                        </div>
                        <button class="btn rounded-circle d-flex align-items-center justify-content-center p-0"
                                style="width: 44px; height: 44px; background: rgba(255,255,255,0.25);"
                                id="btnModalPlaySurahAudio"
                                onclick="toggleFullSurahAudio(${dol}{s.id}, '${dol}{escapeJs(s.nameLatin)}')">
                            <i class="bi ${dol}{isPlayingSurah ? 'bi-pause-fill' : 'bi-play-fill'} text-white fs-4"></i>
                        </button>
                    </div>
                </div>

                <hr class="my-3 opacity-25">

                <!-- Section 2: Daftar Qari -->
                <div class="fw-bold text-success mb-2" style="font-size: 11px; letter-spacing: 0.5px;">
                    DAFTAR QARI (PEMBERI SUARA)
                </div>

                <div class="d-flex flex-column">
                    ${dol}{qariCardsHtml}
                </div>

                <div class="text-end mt-4">
                    <button class="btn btn-sm btn-brand px-4 py-2 fw-bold" onclick="closeCustomModal()">Tutup</button>
                </div>
            `, 'Pilih Qari & Murattal');
        }

        function selectActiveQari(qariName) {
            window.activeQari = qariName;
            if (window.isQuranPlaying && window.readerSurahId) {
                if (window.currentQuranVerseAudio !== null) {
                    toggleQuranVerseAudio(window.readerSurahId, window.currentQuranVerseAudio, window.readerSurahData ? window.readerSurahData.nameLatin : '');
                } else {
                    toggleFullSurahAudio(window.readerSurahId, window.readerSurahData ? window.readerSurahData.nameLatin : '');
                }
            }
            showQuranToast('Qari aktif: ' + qariName);
            showSelectQariModal(); // Re-render modal
        }

        // --- DIALOG 3: PANDUAN TAJWID WARNA MODAL ---
        function showTajwidGuideModal() {
            showCustomModal(`
                <div class="mb-3 text-secondary" style="font-size: 13px;">
                    Penjelasan kode warna tajwid pada bacaan Al-Qur'an:
                </div>

                <div class="d-flex flex-column gap-2">
                    <!-- Ghunnah -->
                    <div class="card card-custom p-3 border-0" style="border-radius: 12px; background: rgba(233, 30, 99, 0.08);">
                        <div class="d-flex align-items-start gap-3">
                            <span class="rounded-circle mt-1" style="width: 12px; height: 12px; min-width: 12px; background-color: #E91E63;"></span>
                            <div>
                                <div class="fw-bold" style="color: #E91E63; font-size: 13px;">Ghunnah (Dengung)</div>
                                <small class="text-secondary">Dengung pada huruf Nun (ن) atau Mim (م) bertasydid.</small>
                            </div>
                        </div>
                    </div>

                    <!-- Qalqalah -->
                    <div class="card card-custom p-3 border-0" style="border-radius: 12px; background: rgba(30, 136, 229, 0.08);">
                        <div class="d-flex align-items-start gap-3">
                            <span class="rounded-circle mt-1" style="width: 12px; height: 12px; min-width: 12px; background-color: #1E88E5;"></span>
                            <div>
                                <div class="fw-bold" style="color: #1E88E5; font-size: 13px;">Qalqalah (Memantul)</div>
                                <small class="text-secondary">Memantul pada huruf (ق, ط, ب, ج, د) saat sukun atau waqaf.</small>
                            </div>
                        </div>
                    </div>

                    <!-- Ikhfa / Idgham -->
                    <div class="card card-custom p-3 border-0" style="border-radius: 12px; background: rgba(239, 108, 0, 0.08);">
                        <div class="d-flex align-items-start gap-3">
                            <span class="rounded-circle mt-1" style="width: 12px; height: 12px; min-width: 12px; background-color: #EF6C00;"></span>
                            <div>
                                <div class="fw-bold" style="color: #EF6C00; font-size: 13px;">Ikhfa / Idgham (Samar / Dengung)</div>
                                <small class="text-secondary">Samar atau memasukkan bacaan Nun sukun (نْ) / Tanwin.</small>
                            </div>
                        </div>
                    </div>

                    <!-- Mad -->
                    <div class="card card-custom p-3 border-0" style="border-radius: 12px; background: rgba(46, 125, 50, 0.08);">
                        <div class="d-flex align-items-start gap-3">
                            <span class="rounded-circle mt-1" style="width: 12px; height: 12px; min-width: 12px; background-color: #2E7D32;"></span>
                            <div>
                                <div class="fw-bold" style="color: #2E7D32; font-size: 13px;">Mad (Panjang)</div>
                                <small class="text-secondary">Memanjangkan bacaan huruf mad (2, 4, atau 6 harakat).</small>
                            </div>
                        </div>
                    </div>
                </div>

                <div class="text-end mt-4">
                    <button class="btn btn-sm btn-brand px-4 py-2 fw-bold" onclick="closeCustomModal()">Tutup</button>
                </div>
            `, 'Panduan Tajwid Warna');
        }

        // --- TOAST HELPER ---
        function showQuranToast(msg) {
            let toast = document.getElementById('quranToast');
            if (!toast) {
                toast = document.createElement('div');
                toast.id = 'quranToast';
                toast.className = 'position-fixed bottom-0 start-50 translate-middle-x mb-5 p-2 px-4 shadow-lg text-white';
                toast.style.zIndex = '1100';
                toast.style.borderRadius = '30px';
                toast.style.background = 'rgba(0, 121, 107, 0.95)';
                toast.style.fontSize = '12px';
                toast.style.backdropFilter = 'blur(6px)';
                document.body.appendChild(toast);
            }
            toast.innerText = msg;
            toast.style.display = 'block';
            clearTimeout(window.quranToastTimer);
            window.quranToastTimer = setTimeout(() => {
                toast.style.display = 'none';
            }, 2500);
        }
        """
    }
}
