package com.example.web

object NoteWebScript {
    fun getScript(dol: String): String {
        return """
        // =========================================================================
        // --- NOTE MODULE: CATATAN & JADWAL HARIAN (100% OFFLINE PERSIS ANDROID) ---
        // =========================================================================

        // --- GLOBAL STATE UNTUK CATATAN & JADWAL ---
        window.notesData = [];
        window.notesSearchQuery = '';
        window.notesSelectedCategory = 'Semua';
        window.notesViewMode = localStorage.getItem('singghara_notes_view_mode') || 'grid';
        window.activeNoteItem = null;
        window.noteUndoStack = [];
        window.noteRedoStack = [];
        window.noteHasUnsavedChanges = false;
        window.noteLastSnapshotTime = 0;
        window.notePreviewMode = false;

        window.jadwalData = [];
        window.jadwalItemToEdit = null;

        // --- SAFE FALLBACK INVALIDATE CACHE ---
        if (typeof window.invalidateApiCache !== 'function') {
            window.invalidateApiCache = function(cacheKey) {
                try {
                    if (typeof StorageCache !== 'undefined' && StorageCache && StorageCache.remove) {
                        StorageCache.remove(cacheKey);
                    } else if (cacheKey) {
                        localStorage.removeItem('singghara_offline_cache_' + cacheKey);
                    }
                } catch(e) {
                    console.warn("invalidateApiCache fallback error:", e);
                }
            };
        }
        function invalidateApiCache(cacheKey) {
            try {
                if (typeof window.invalidateApiCache === 'function') {
                    window.invalidateApiCache(cacheKey);
                }
            } catch(e) {}
        }

        // --- PALET WARNA CATATAN (PERSIS NOTE_COLORS DI CatatanScreen.kt) ---
        const NOTE_COLOR_PALETTE = [
            { hex: "#6366F1", name: "Indigo" },
            { hex: "#10B981", name: "Emerald" },
            { hex: "#F59E0B", name: "Amber" },
            { hex: "#EF4444", name: "Coral" },
            { hex: "#8B5CF6", name: "Violet" },
            { hex: "#06B6D4", name: "Cyan" },
            { hex: "#EC4899", name: "Pink" }
        ];

        // --- KATEGORI JADWAL (PERSIS CategoryConfig DI JadwalHarianScreen.kt) ---
        const JADWAL_CATEGORIES = [
            { name: "Rutinitas Pagi", color: "#FBC02D", icon: "bi-brightness-high-fill" },
            { name: "Kesehatan", color: "#E53935", icon: "bi-heart-fill" },
            { name: "Persiapan Diri", color: "#0288D1", icon: "bi-person-fill" },
            { name: "Energi", color: "#FF9800", icon: "bi-lightning-charge-fill" },
            { name: "Kebersihan", color: "#00ACC1", icon: "bi-droplet-fill" },
            { name: "Produktivitas", color: "#43A047", icon: "bi-pencil-fill" },
            { name: "Istirahat", color: "#5E35B1", icon: "bi-moon-stars-fill" },
            { name: "Me Time", color: "#D81B60", icon: "bi-emoji-smile-fill" },
            { name: "Evaluasi", color: "#757575", icon: "bi-info-circle-fill" }
        ];

        function getJadwalCatConfig(catName) {
            const found = JADWAL_CATEGORIES.find(c => c.name.toLowerCase() === (catName || '').toLowerCase());
            return found || { name: catName || 'Umum', color: "#9E9E9E", icon: "bi-clock-fill" };
        }

        function formatRelativeTimeNotes(timestamp) {
            if (!timestamp || timestamp <= 0) return '';
            const now = Date.now();
            const diffMs = now - Number(timestamp);
            const diffMinutes = Math.floor(diffMs / (1000 * 60));
            const diffHours = Math.floor(diffMinutes / 60);
            const diffDays = Math.floor(diffHours / 24);

            if (diffMinutes < 1) return 'Baru saja';
            if (diffMinutes < 60) return diffMinutes + ' mnt lalu';
            if (diffHours < 24) return diffHours + ' jam lalu';
            if (diffDays === 1) return 'Kemarin';
            if (diffDays < 7) return diffDays + ' hari lalu';

            const d = new Date(Number(timestamp));
            return d.toLocaleDateString('id-ID', { day: 'numeric', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' });
        }

        function showNoteFeedbackToast(msg, iconClass, colorHex) {
            const toastContainer = document.getElementById('noteFloatingToast');
            if (!toastContainer) return;
            toastContainer.innerHTML = `
                <div class="card shadow-lg border-0 px-3 py-2 rounded-pill d-flex flex-row align-items-center gap-2"
                     style="background: var(--card-bg, #ffffff); border: 1.5px solid ${dol}{colorHex || '#6366F1'} !important; color: ${dol}{colorHex || '#6366F1'};">
                    <i class="bi ${dol}{iconClass || 'bi-check-circle-fill'} fs-5"></i>
                    <span class="fw-bold small text-body">${dol}{escapeHtml(msg)}</span>
                </div>
            `;
            toastContainer.style.display = 'block';
            toastContainer.style.opacity = '1';
            clearTimeout(window.noteToastTimer);
            window.noteToastTimer = setTimeout(() => {
                toastContainer.style.opacity = '0';
                setTimeout(() => { toastContainer.style.display = 'none'; }, 300);
            }, 2600);
        }

        // =========================================================================
        // --- 1. SUB-MENU CATATAN (PERSIS CatatanScreen.kt) ---
        // =========================================================================

        async function loadNotes() {
            try {
                const fetchRes = await fetchApiWithCache('/api/notes', 'notes');
                window.notesData = Array.isArray(fetchRes.data) ? fetchRes.data : [];
                renderNotesUI();
            } catch (err) {
                console.error("Gagal memuat catatan:", err);
                const container = document.getElementById('tab-content') || document.getElementById('main-content');
                if (container) {
                    container.innerHTML = `<div class="alert alert-danger m-3">Gagal memuat catatan: ${dol}{escapeHtml(err.message)}</div>`;
                }
            }
        }

        function renderNotesUI() {
            const container = document.getElementById('tab-content') || document.getElementById('main-content');
            if (!container) return;

            // Jika sedang dalam mode editor catatan aktif
            if (window.activeNoteItem !== null) {
                renderNoteEditor(container);
                return;
            }

            const notes = window.notesData || [];
            const pinnedCount = notes.filter(n => n.isPinned).length;

            // Filter catatan sesuai pencarian & chip kategori
            const query = (window.notesSearchQuery || '').toLowerCase().trim();
            const filterCat = window.notesSelectedCategory || 'Semua';

            const filtered = notes.filter(n => {
                const matchQuery = !query ||
                    (n.title && n.title.toLowerCase().includes(query)) ||
                    (n.content && n.content.toLowerCase().includes(query)) ||
                    (n.category && n.category.toLowerCase().includes(query));

                let matchCat = true;
                if (filterCat === '📌 Disematkan') {
                    matchCat = !!n.isPinned;
                } else if (filterCat === '📝 Umum') {
                    matchCat = !n.category || n.category === 'Umum';
                } else if (filterCat === '💡 Ide') {
                    matchCat = n.category === 'Ide';
                } else if (filterCat === '💼 Pekerjaan') {
                    matchCat = n.category === 'Pekerjaan';
                } else if (filterCat === '☑️ Checklist') {
                    matchCat = n.category === 'Checklist';
                } else if (filterCat === '💻 Kode') {
                    matchCat = n.category === 'Kode' ||
                        (n.title && (n.title.endsWith('.py') || n.title.endsWith('.js') || n.title.endsWith('.html') || n.title.endsWith('.json') || n.title.endsWith('.css') || n.title.endsWith('.sql')));
                }

                return matchQuery && matchCat;
            });

            const categories = ["Semua", "📌 Disematkan", "📝 Umum", "💡 Ide", "💼 Pekerjaan", "☑️ Checklist", "💻 Kode"];

            let html = `
                <div class="container-fluid px-3 py-2 position-relative" id="notesMainWrapper">
                    <!-- FLOATING FEEDBACK TOAST -->
                    <div id="noteFloatingToast" style="display: none; position: fixed; top: 75px; left: 50%; transform: translateX(-50%); z-index: 1060; transition: opacity 0.3s ease;"></div>

                    <!-- HERO STATS & METRICS HEADER -->
                    <div class="row g-3 mb-3">
                        <!-- Total Notes Card with Quick Create -->
                        <div class="col-12 col-md-6">
                            <div class="card card-custom p-3 cursor-pointer hover-scale h-100" onclick="openCreateNewNote()">
                                <div class="d-flex align-items-center">
                                    <div class="rounded-circle d-flex align-items-center justify-content-center me-3 shadow-sm text-white"
                                         style="width: 46px; height: 46px; background: linear-gradient(135deg, #6366F1, #8B5CF6); flex-shrink: 0;">
                                        <i class="bi bi-plus-lg fs-4"></i>
                                    </div>
                                    <div>
                                        <small class="text-secondary d-block fw-semibold">Total Catatan</small>
                                        <h5 class="fw-bold mb-0 text-body">${dol}{notes.length} Catatan</h5>
                                    </div>
                                </div>
                            </div>
                        </div>

                        <!-- Pinned Notes Counter & Quick Filter -->
                        <div class="col-12 col-md-6">
                            <div class="card card-custom p-3 cursor-pointer hover-scale h-100 ${dol}{filterCat === '📌 Disematkan' ? 'border-warning shadow-sm' : ''}"
                                 onclick="togglePinnedFilter()"
                                 style="${dol}{filterCat === '📌 Disematkan' ? 'background: rgba(245, 158, 11, 0.08); border: 1.5px solid #F59E0B !important;' : ''}">
                                <div class="d-flex align-items-center">
                                    <div class="rounded-circle d-flex align-items-center justify-content-center me-3 shadow-sm"
                                         style="width: 46px; height: 46px; background: rgba(245, 158, 11, 0.18); color: #D97706; flex-shrink: 0;">
                                        <i class="bi bi-bookmark-star-fill fs-4"></i>
                                    </div>
                                    <div>
                                        <small class="text-secondary d-block fw-semibold">Sangat Penting</small>
                                        <h5 class="fw-bold mb-0 text-body">${dol}{pinnedCount} Disematkan</h5>
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div>

                    <!-- SECONDARY UTILITY BAR: BUKA DOKUMEN / PDF & TATA LETAK TOGGLE -->
                    <div class="d-flex flex-wrap justify-content-between align-items-center gap-2 mb-3">
                        <div class="d-flex align-items-center gap-2">
                            <!-- Tombol Buka Dokumen / PDF Offline -->
                            <button type="button" class="btn btn-outline-primary btn-sm rounded-pill px-3 py-1 fw-bold d-flex align-items-center gap-2"
                                    onclick="triggerOpenDocFile()">
                                <i class="bi bi-book"></i>
                                <span>Buka Dokumen / PDF</span>
                            </button>
                            <input type="file" id="noteDocFileInput" style="display: none;"
                                   accept=".txt,.md,.doc,.docx,.odt,.rtf,.pdf,.json,.csv,.html,.js,.py"
                                   onchange="handleDocFileSelected(event)">
                        </div>

                        <div class="d-flex align-items-center gap-1">
                            <!-- Buat File Kode / Script -->
                            <button type="button" class="btn btn-sm btn-light border rounded-circle p-2 text-primary"
                                    title="Buat File Kode / Script" onclick="showCodeTemplateModal()">
                                <i class="bi bi-code-slash fs-6"></i>
                            </button>

                            <!-- View Mode Toggle (Grid vs List) -->
                            <button type="button" class="btn btn-sm btn-light border rounded-circle p-2 text-secondary"
                                    title="Ubah Tampilan Grid / List" onclick="toggleNotesViewMode()">
                                <i class="bi ${dol}{window.notesViewMode === 'grid' ? 'bi-view-list' : 'bi-grid'} fs-6"></i>
                            </button>
                        </div>
                    </div>

                    <!-- SEARCH BAR -->
                    <div class="input-group mb-3 shadow-sm rounded-pill overflow-hidden border">
                        <span class="input-group-text bg-body border-0 ps-3 text-primary">
                            <i class="bi bi-search"></i>
                        </span>
                        <input type="text" class="form-control bg-body border-0 shadow-none"
                               placeholder="Cari judul, kategori, atau isi..."
                               value="${dol}{escapeHtml(window.notesSearchQuery)}"
                               oninput="handleNotesSearch(this.value)">
                        ${dol}{window.notesSearchQuery ? `
                            <button class="btn btn-link text-secondary pe-3" type="button" onclick="clearNotesSearch()">
                                <i class="bi bi-x-circle-fill"></i>
                            </button>
                        ` : ''}
                    </div>

                    <!-- CATEGORY FILTER CHIPS SCROLLABLE ROW -->
                    <div class="d-flex gap-2 overflow-auto pb-2 mb-3 no-scrollbar" style="white-space: nowrap;">
                        ${dol}{categories.map(cat => {
                            const isSel = filterCat === cat;
                            return `
                                <button type="button"
                                        class="btn btn-sm rounded-pill px-3 py-1 fw-bold ${dol}{isSel ? 'btn-primary shadow-sm text-white' : 'btn-outline-secondary border'}"
                                        onclick="selectNotesCategory('${dol}{cat}')">
                                    ${dol}{cat}
                                </button>
                            `;
                        }).join('')}
                    </div>

                    <!-- NOTES LIST / GRID CONTENT -->
            `;

            if (filtered.length === 0) {
                // Empty state matching Android
                html += `
                    <div class="card card-custom p-5 text-center my-4 border border-dashed">
                        <div class="mb-3">
                            <i class="bi bi-sticky text-primary opacity-50" style="font-size: 4rem;"></i>
                        </div>
                        <h5 class="fw-bold mb-1">${dol}{window.notesSearchQuery ? 'Catatan Tidak Ditemukan' : 'Mulai Menulis Ide Pertama Anda!'}</h5>
                        <p class="text-secondary small mb-3 max-w-500 mx-auto">
                            ${dol}{window.notesSearchQuery ? 'Coba gunakan kata kunci pencarian atau kategori yang berbeda.' : 'Belum ada catatan di kategori ini. Tekan tombol \'+ Tambah Catatan\' di atas atau gunakan template siap pakai.'}
                        </p>
                        <div>
                            <button class="btn btn-primary rounded-pill px-4 fw-bold shadow-sm" onclick="openCreateNewNote()">
                                <i class="bi bi-plus-lg me-1"></i> Buat Catatan Baru
                            </button>
                        </div>
                    </div>
                `;
            } else {
                if (window.notesViewMode === 'grid') {
                    html += `<div class="row g-3">`;
                    filtered.forEach(note => {
                        html += renderNoteCard(note, false);
                    });
                    html += `</div>`;
                } else {
                    html += `<div class="d-flex flex-column gap-3">`;
                    filtered.forEach(note => {
                        html += renderNoteCard(note, true);
                    });
                    html += `</div>`;
                }
            }

            html += `</div>`;
            container.innerHTML = html;
        }

        function renderNoteCard(note, isList) {
            const colorHex = note.colorHex || '#6366F1';
            const cat = note.category || 'Umum';
            const timeStr = formatRelativeTimeNotes(note.lastSaved);
            const contentClean = (note.content || '').trim();
            const charCount = contentClean.length;
            const lineCount = contentClean ? contentClean.split('\n').length : 0;

            const colClass = isList ? 'col-12' : 'col-12 col-md-6 col-lg-4';

            return `
                <div class="${dol}{colClass}">
                    <div class="card card-custom p-3 h-100 position-relative transition-all"
                         style="border-left: 4.5px solid ${dol}{colorHex} !important; cursor: pointer; border-radius: 14px;"
                         onclick="openEditNoteById(${dol}{note.id})">
                        <!-- Top header of note card -->
                        <div class="d-flex justify-content-between align-items-center mb-2">
                            <div class="d-flex align-items-center gap-2">
                                <span class="badge rounded-pill px-2 py-1 small"
                                      style="background: ${dol}{colorHex}1A; color: ${dol}{colorHex}; border: 1px solid ${dol}{colorHex}40; font-size: 11px;">
                                    ${dol}{escapeHtml(cat)}
                                </span>
                                ${dol}{timeStr ? `<span class="text-secondary" style="font-size: 11px;">• ${dol}{timeStr}</span>` : ''}
                            </div>
                            <div class="d-flex align-items-center" onclick="event.stopPropagation()">
                                <button type="button" class="btn btn-sm p-1 text-secondary border-0"
                                        title="${dol}{note.isPinned ? 'Batal Sematkan' : 'Sematkan Catatan'}"
                                        onclick="toggleNotePinState(${dol}{note.id})">
                                    <i class="bi ${dol}{note.isPinned ? 'bi-bookmark-fill text-warning' : 'bi-bookmark'} fs-6"></i>
                                </button>
                            </div>
                        </div>

                        <!-- Title -->
                        <h6 class="fw-bold mb-1 text-break text-body" style="font-size: 15px;">
                            ${dol}{escapeHtml(note.title || 'Catatan Tanpa Judul')}
                        </h6>

                        <!-- Content Preview -->
                        <p class="text-secondary small mb-3 text-break flex-grow-1"
                           style="display: -webkit-box; -webkit-line-clamp: ${dol}{isList ? '3' : '4'}; -webkit-box-orient: vertical; overflow: hidden; white-space: pre-line; line-height: 1.45;">
                            ${dol}{escapeHtml(contentClean || '(Catatan kosong)')}
                        </p>

                        <!-- Card Footer: Stats & Actions -->
                        <div class="d-flex justify-content-between align-items-center pt-2 border-top border-secondary border-opacity-10" onclick="event.stopPropagation()">
                            <small class="text-muted" style="font-size: 11px;">
                                ${dol}{charCount} Karakter • ${dol}{lineCount} Baris
                            </small>
                            <div class="d-flex align-items-center gap-1">
                                <button type="button" class="btn btn-sm btn-light border-0 p-1 text-primary"
                                        title="Bagikan / Salin Catatan" onclick="shareOrCopyNote(${dol}{note.id})">
                                    <i class="bi bi-share"></i>
                                </button>
                                <button type="button" class="btn btn-sm btn-light border-0 p-1 text-info"
                                        title="Buka / Edit" onclick="openEditNoteById(${dol}{note.id})">
                                    <i class="bi bi-pencil-square"></i>
                                </button>
                                <button type="button" class="btn btn-sm btn-light border-0 p-1 text-danger"
                                        title="Hapus Catatan" onclick="confirmDeleteNote(${dol}{note.id})">
                                    <i class="bi bi-trash"></i>
                                </button>
                            </div>
                        </div>
                    </div>
                </div>
            `;
        }

        // --- IMMERSIVE NOTE EDITOR (PERSIS CatatanScreen.kt EDITOR) ---
        function renderNoteEditor(container) {
            const note = window.activeNoteItem;
            if (!note) return;

            const colorHex = note.colorHex || '#6366F1';
            const cat = note.category || 'Umum';
            const contentVal = note.content || '';
            const charCount = contentVal.length;
            const lineCount = contentVal ? contentVal.split('\n').length : 0;

            const isPinned = !!note.isPinned;

            let html = `
                <div class="container-fluid px-3 py-2 position-relative" id="noteEditorWrapper">
                    <!-- FLOATING FEEDBACK TOAST -->
                    <div id="noteFloatingToast" style="display: none; position: fixed; top: 75px; left: 50%; transform: translateX(-50%); z-index: 1060; transition: opacity 0.3s ease;"></div>

                    <!-- EDITOR CARD WITH CUSTOM COLOR BORDER -->
                    <div class="card card-custom p-3 p-md-4 shadow-sm"
                         style="border: 2px solid ${dol}{colorHex}55 !important; border-radius: 18px; min-height: 80vh;">

                        <!-- TOP BAR: BACK, TITLE INPUT, TOOLS MENU -->
                        <div class="d-flex flex-wrap align-items-center justify-content-between gap-2 pb-3 border-bottom border-secondary border-opacity-10 mb-3">
                            <div class="d-flex align-items-center gap-2 flex-grow-1">
                                <button type="button" class="btn btn-light rounded-circle p-2 border" title="Kembali ke Daftar" onclick="closeNoteEditor()">
                                    <i class="bi bi-arrow-left fs-5"></i>
                                </button>
                                <input type="text" class="form-control form-control-lg border-0 shadow-none fw-bold text-body"
                                       id="editorNoteTitle"
                                       placeholder="Beri judul catatan..."
                                       value="${dol}{escapeHtml(note.title || '')}"
                                       oninput="handleEditorTitleChange(this.value)">
                            </div>

                            <!-- EDITOR ACTION ICONS (KATEGORI, WARNA, SEMATKAN, FORMAT, DLL) -->
                            <div class="d-flex align-items-center gap-1">
                                <!-- 1. Kategori Dropdown -->
                                <div class="dropdown">
                                    <button class="btn btn-sm btn-outline-primary dropdown-toggle rounded-pill px-3 py-1 fw-bold"
                                            type="button" data-bs-toggle="dropdown" id="editorCategoryBtn">
                                        <i class="bi bi-funnel me-1"></i> ${dol}{escapeHtml(cat)}
                                    </button>
                                    <ul class="dropdown-menu dropdown-menu-end shadow-sm border-0">
                                        <li><h6 class="dropdown-header text-primary fw-bold">Kategori Catatan</h6></li>
                                        <li><a class="dropdown-item ${dol}{cat === 'Umum' ? 'active fw-bold' : ''}" href="javascript:void(0)" onclick="setEditorCategory('Umum')">📝 Umum</a></li>
                                        <li><a class="dropdown-item ${dol}{cat === 'Ide' ? 'active fw-bold' : ''}" href="javascript:void(0)" onclick="setEditorCategory('Ide')">💡 Ide</a></li>
                                        <li><a class="dropdown-item ${dol}{cat === 'Pekerjaan' ? 'active fw-bold' : ''}" href="javascript:void(0)" onclick="setEditorCategory('Pekerjaan')">💼 Pekerjaan</a></li>
                                        <li><a class="dropdown-item ${dol}{cat === 'Checklist' ? 'active fw-bold' : ''}" href="javascript:void(0)" onclick="setEditorCategory('Checklist')">☑️ Checklist</a></li>
                                        <li><a class="dropdown-item ${dol}{cat === 'Kode' ? 'active fw-bold' : ''}" href="javascript:void(0)" onclick="setEditorCategory('Kode')">💻 Kode</a></li>
                                    </ul>
                                </div>

                                <!-- 2. Ubah Warna Popover / Dropdown -->
                                <div class="dropdown">
                                    <button class="btn btn-sm btn-light border rounded-circle p-2" type="button" data-bs-toggle="dropdown" title="Ubah Warna Catatan">
                                        <div class="rounded-circle" style="width: 18px; height: 18px; background: ${dol}{colorHex}; border: 1.5px solid rgba(0,0,0,0.15);"></div>
                                    </button>
                                    <div class="dropdown-menu dropdown-menu-end p-2 shadow-sm border-0" style="min-width: 180px;">
                                        <h6 class="dropdown-header text-secondary py-1 small fw-bold">Pilih Aksen Warna</h6>
                                        <div class="d-flex flex-wrap gap-2 p-1">
                                            ${dol}{NOTE_COLOR_PALETTE.map(c => `
                                                <div class="rounded-circle cursor-pointer border"
                                                     title="${dol}{c.name}"
                                                     onclick="setEditorColor('${dol}{c.hex}', '${dol}{c.name}')"
                                                     style="width: 26px; height: 26px; background: ${dol}{c.hex}; ${dol}{c.hex === colorHex ? 'outline: 2.5px solid #2563EB; outline-offset: 1px;' : ''}">
                                                </div>
                                            `).join('')}
                                        </div>
                                    </div>
                                </div>

                                <!-- 3. Sematkan / Pin Button -->
                                <button type="button" class="btn btn-sm btn-light border rounded-circle p-2 ${dol}{isPinned ? 'text-warning' : 'text-secondary'}"
                                        title="${dol}{isPinned ? 'Batal Sematkan' : 'Sematkan Catatan'}"
                                        onclick="toggleEditorPin()">
                                    <i class="bi ${dol}{isPinned ? 'bi-bookmark-fill' : 'bi-bookmark'} fs-6"></i>
                                </button>

                                <!-- 4. Menu Format Teks / Sisipkan -->
                                <div class="dropdown">
                                    <button class="btn btn-sm btn-light border rounded-circle p-2 text-primary" type="button" data-bs-toggle="dropdown" title="Format Teks & Sisipkan">
                                        <i class="bi bi-type-bold fs-6"></i>
                                    </button>
                                    <ul class="dropdown-menu dropdown-menu-end shadow-sm border-0">
                                        <li><h6 class="dropdown-header text-primary fw-bold">Gaya Teks</h6></li>
                                        <li><a class="dropdown-item" href="javascript:void(0)" onclick="insertFormatting('bold')"><i class="bi bi-type-bold me-2"></i> Tebal (Bold)</a></li>
                                        <li><a class="dropdown-item" href="javascript:void(0)" onclick="insertFormatting('italic')"><i class="bi bi-type-italic me-2"></i> Miring (Italic)</a></li>
                                        <li><a class="dropdown-item" href="javascript:void(0)" onclick="insertFormatting('strikethrough')"><i class="bi bi-type-strikethrough me-2"></i> Coret (Strikethrough)</a></li>
                                        <li><hr class="dropdown-divider"></li>
                                        <li><h6 class="dropdown-header text-primary fw-bold">Struktur & Daftar</h6></li>
                                        <li><a class="dropdown-item" href="javascript:void(0)" onclick="insertFormatting('h1')"><i class="bi bi-type-h1 me-2"></i> Judul Besar (H1)</a></li>
                                        <li><a class="dropdown-item" href="javascript:void(0)" onclick="insertFormatting('h2')"><i class="bi bi-type-h2 me-2"></i> Sub-judul (H2)</a></li>
                                        <li><a class="dropdown-item" href="javascript:void(0)" onclick="insertFormatting('bullet')"><i class="bi bi-list-ul me-2"></i> Daftar Titik (Bullet)</a></li>
                                        <li><a class="dropdown-item" href="javascript:void(0)" onclick="insertFormatting('number')"><i class="bi bi-list-ol me-2"></i> Daftar Nomor</a></li>
                                        <li><a class="dropdown-item" href="javascript:void(0)" onclick="insertFormatting('checklist')"><i class="bi bi-check2-square me-2"></i> Checklist [ ]</a></li>
                                        <li><a class="dropdown-item" href="javascript:void(0)" onclick="insertFormatting('code')"><i class="bi bi-code-square me-2"></i> Blok Kode</a></li>
                                        <li><a class="dropdown-item" href="javascript:void(0)" onclick="insertFormatting('divider')"><i class="bi bi-hr me-2"></i> Garis Pembatas (---)</a></li>
                                        <li><a class="dropdown-item" href="javascript:void(0)" onclick="insertFormatting('time')"><i class="bi bi-clock-history me-2"></i> Waktu Sekarang</a></li>
                                    </ul>
                                </div>

                                <!-- 5. Undo / Redo / Tempel Teks -->
                                <button type="button" class="btn btn-sm btn-light border rounded-circle p-2 text-secondary"
                                        title="Undo (Kembalikan)" onclick="performEditorUndo()">
                                    <i class="bi bi-arrow-counterclockwise fs-6"></i>
                                </button>
                                <button type="button" class="btn btn-sm btn-light border rounded-circle p-2 text-secondary"
                                        title="Redo (Ulangi)" onclick="performEditorRedo()">
                                    <i class="bi bi-arrow-clockwise fs-6"></i>
                                </button>
                                <button type="button" class="btn btn-sm btn-light border rounded-circle p-2 text-primary"
                                        title="Tempel di Posisi Kursor" onclick="pasteAtCursor()">
                                    <i class="bi bi-clipboard fs-6"></i>
                                </button>

                                <!-- 6. Pratinjau HTML / Markdown Toggle -->
                                <button type="button" class="btn btn-sm btn-light border rounded-circle p-2 ${dol}{window.notePreviewMode ? 'btn-primary text-white' : 'text-secondary'}"
                                        title="${dol}{window.notePreviewMode ? 'Kembali ke Editor' : 'Pratinjau Tampilan'}"
                                        onclick="toggleEditorPreviewMode()">
                                    <i class="bi ${dol}{window.notePreviewMode ? 'bi-eye-fill' : 'bi-eye'} fs-6"></i>
                                </button>

                                <!-- 7. Bagikan / Salin -->
                                <button type="button" class="btn btn-sm btn-light border rounded-circle p-2 text-primary"
                                        title="Bagikan / Salin Catatan" onclick="shareOrCopyActiveNote()">
                                    <i class="bi bi-share fs-6"></i>
                                </button>

                                <!-- 8. Simpan Manual -->
                                <button type="button" class="btn btn-sm btn-primary rounded-pill px-3 py-1 fw-bold shadow-sm d-flex align-items-center gap-1"
                                        onclick="saveActiveNoteManual()">
                                    <i class="bi bi-floppy"></i>
                                    <span class="d-none d-sm-inline">Simpan</span>
                                </button>
                            </div>
                        </div>

                        <!-- CONTENT AREA: TEXTAREA OR PREVIEW -->
                        <div class="flex-grow-1 position-relative mb-3" style="min-height: 480px;">
                            ${dol}{window.notePreviewMode ? `
                                <div class="p-3 bg-body border rounded-3 overflow-auto" style="min-height: 480px; max-height: 65vh; white-space: pre-wrap; font-family: inherit;">
                                    ${dol}{renderMarkdownPreview(contentVal)}
                                </div>
                            ` : `
                                <textarea class="form-control border-0 shadow-none bg-transparent"
                                          id="editorNoteContent"
                                          rows="18"
                                          placeholder="Tulis isi catatan di sini..."
                                          style="font-size: 15px; line-height: 1.6; resize: vertical; min-height: 480px;"
                                          oninput="handleEditorContentChange(this.value)">${dol}{escapeHtml(contentVal)}</textarea>
                            `}
                        </div>

                        <!-- EDITOR FOOTER: AUTOSAVE STATUS & CHAR/LINE COUNTER -->
                        <div class="d-flex justify-content-between align-items-center pt-2 border-top border-secondary border-opacity-10 text-secondary" style="font-size: 12px;">
                            <div class="d-flex align-items-center gap-2">
                                <span id="editorAutoSaveStatus" class="fw-semibold">
                                    ${dol}{window.noteHasUnsavedChanges ? '<i class="bi bi-circle-fill text-warning me-1"></i> Belum disimpan' : '<i class="bi bi-check-circle text-success me-1"></i> Tersimpan'}
                                </span>
                            </div>
                            <div>
                                <span id="editorStatsCounter">${dol}{charCount} Karakter • ${dol}{lineCount} Baris</span>
                            </div>
                        </div>
                    </div>
                </div>
            `;

            container.innerHTML = html;
        }

        function renderMarkdownPreview(txt) {
            if (!txt) return '<span class="text-secondary fst-italic">(Catatan masih kosong)</span>';
            // Simple robust offline Markdown/HTML renderer
            let res = escapeHtml(txt);
            // Headers
            res = res.replace(/^### (.*$)/gim, '<h5 class="fw-bold mt-3 mb-2">$1</h5>');
            res = res.replace(/^## (.*$)/gim, '<h4 class="fw-bold mt-3 mb-2">$1</h4>');
            res = res.replace(/^# (.*$)/gim, '<h3 class="fw-bold mt-3 mb-2">$1</h3>');
            // Bold & Italic & Strike
            res = res.replace(/\*\*(.*?)\*\*/gim, '<strong>$1</strong>');
            res = res.replace(/\*(.*?)\*/gim, '<em>$1</em>');
            res = res.replace(/~~(.*?)~~/gim, '<del>$1</del>');
            // Code block
            res = res.replace(/```([\s\S]*?)```/gim, '<pre class="bg-dark text-light p-3 rounded-3 my-2"><code>$1</code></pre>');
            // Inline code
            res = res.replace(/`([^`]+)`/gim, '<code class="bg-secondary bg-opacity-25 px-1 rounded text-primary">$1</code>');
            // Checklist [ ] and [x]
            res = res.replace(/\[ \]/gim, '<input type="checkbox" disabled class="form-check-input me-1">');
            res = res.replace(/\[x\]/gim, '<input type="checkbox" checked disabled class="form-check-input me-1">');
            // Horizontal line
            res = res.replace(/^---$/gim, '<hr class="my-3 border-secondary opacity-25">');

            return res;
        }

        // --- NOTE HANDLERS ---
        function openCreateNewNote() {
            window.activeNoteItem = {
                id: 0,
                title: '',
                content: '',
                category: 'Umum',
                colorHex: '#6366F1',
                isPinned: false,
                lastSaved: Date.now()
            };
            window.noteUndoStack = [{ content: '', cursor: 0 }];
            window.noteRedoStack = [];
            window.noteHasUnsavedChanges = false;
            window.notePreviewMode = false;
            renderNotesUI();
        }

        function openEditNoteById(noteId) {
            const found = (window.notesData || []).find(n => n.id === noteId);
            if (!found) return;
            window.activeNoteItem = { ...found };
            const c = found.content || '';
            window.noteUndoStack = [{ content: c, cursor: c.length }];
            window.noteRedoStack = [];
            window.noteHasUnsavedChanges = false;
            window.notePreviewMode = false;
            renderNotesUI();
        }

        function closeNoteEditor() {
            if (window.noteHasUnsavedChanges) {
                showNoteUnsavedWarningModal();
            } else {
                window.activeNoteItem = null;
                renderNotesUI();
            }
        }

        function showNoteUnsavedWarningModal() {
            let modalEl = document.getElementById('noteUnsavedWarningModal');
            if (!modalEl) {
                const div = document.createElement('div');
                div.id = 'noteUnsavedWarningModal';
                div.className = 'modal fade';
                div.tabIndex = -1;
                div.innerHTML = `
                    <div class="modal-dialog modal-dialog-centered">
                        <div class="modal-content border-0 shadow-lg rounded-4">
                            <div class="modal-header border-0 pb-0">
                                <h5 class="modal-title fw-bold text-warning d-flex align-items-center gap-2">
                                    <i class="bi bi-exclamation-triangle-fill"></i> Simpan Catatan?
                                </h5>
                                <button type="button" class="btn-close" data-bs-dismiss="modal"></button>
                            </div>
                            <div class="modal-body py-3">
                                <p class="mb-0 text-secondary">
                                    Anda memiliki perubahan catatan yang belum disimpan. Apakah Anda ingin menyimpan catatan sebelum kembali?
                                </p>
                            </div>
                            <div class="modal-footer border-0 pt-0 d-flex justify-content-end gap-2">
                                <button type="button" class="btn btn-outline-secondary rounded-pill px-3" data-bs-dismiss="modal">
                                    Batal
                                </button>
                                <button type="button" class="btn btn-outline-danger rounded-pill px-3" onclick="discardNoteAndClose()">
                                    Buang
                                </button>
                                <button type="button" class="btn btn-primary rounded-pill px-4 fw-bold" onclick="saveNoteAndClose()">
                                    <i class="bi bi-floppy me-1"></i> Simpan
                                </button>
                            </div>
                        </div>
                    </div>
                `;
                document.body.appendChild(div);
                modalEl = div;
            }
            const modal = new bootstrap.Modal(modalEl);
            modal.show();
        }

        async function saveNoteAndClose() {
            const modalEl = document.getElementById('noteUnsavedWarningModal');
            if (modalEl) {
                const modal = bootstrap.Modal.getInstance(modalEl);
                if (modal) modal.hide();
            }
            await saveActiveNoteManual(true);
            window.noteHasUnsavedChanges = false;
            window.activeNoteItem = null;
            renderNotesUI();
        }

        function discardNoteAndClose() {
            const modalEl = document.getElementById('noteUnsavedWarningModal');
            if (modalEl) {
                const modal = bootstrap.Modal.getInstance(modalEl);
                if (modal) modal.hide();
            }
            window.noteHasUnsavedChanges = false;
            window.activeNoteItem = null;
            renderNotesUI();
        }

        function handleEditorTitleChange(newTitle) {
            if (!window.activeNoteItem) return;
            window.activeNoteItem.title = newTitle;
            window.noteHasUnsavedChanges = true;
            updateSaveStatusUI(false);
        }

        function handleEditorContentChange(newContent) {
            if (!window.activeNoteItem) return;
            window.activeNoteItem.content = newContent;
            window.noteHasUnsavedChanges = true;
            updateSaveStatusUI(false);

            // Record undo snapshot
            recordUndoSnapshot(newContent);

            // Update footer stats
            updateStatsCounter(newContent);
        }

        function updateStatsCounter(text) {
            const statsEl = document.getElementById('editorStatsCounter');
            if (statsEl) {
                const charCount = text ? text.length : 0;
                const lineCount = text ? text.split('\n').length : 0;
                statsEl.innerText = charCount + ' Karakter • ' + lineCount + ' Baris';
            }
        }

        function updateSaveStatusUI(isSaved) {
            const statusEl = document.getElementById('editorAutoSaveStatus');
            if (statusEl) {
                if (isSaved) {
                    statusEl.innerHTML = '<i class="bi bi-check-circle text-success me-1"></i> Tersimpan';
                } else {
                    statusEl.innerHTML = '<i class="bi bi-circle-fill text-warning me-1"></i> Belum disimpan';
                }
            }
        }

        function recordUndoSnapshot(text, cursor, force) {
            const now = Date.now();
            if (typeof cursor !== 'number') {
                const textarea = document.getElementById('editorNoteContent');
                cursor = textarea ? textarea.selectionStart : text.length;
            }
            if (force || now - window.noteLastSnapshotTime > 600) {
                const last = window.noteUndoStack.length > 0 ? window.noteUndoStack[window.noteUndoStack.length - 1] : null;
                if (!last || last.content !== text) {
                    window.noteUndoStack.push({ content: text, cursor: cursor });
                    if (window.noteUndoStack.length > 100) window.noteUndoStack.shift();
                    window.noteRedoStack = [];
                }
                window.noteLastSnapshotTime = now;
            }
        }

        async function saveActiveNoteToServer(isManualClick) {
            if (!window.activeNoteItem) return;
            const note = window.activeNoteItem;

            const title = (note.title || '').trim() || 'Catatan Tanpa Judul';
            const content = note.content || '';
            const category = note.category || 'Umum';
            const colorHex = note.colorHex || '#6366F1';
            const isPinned = !!note.isPinned;

            const payload = {
                title,
                content,
                category,
                colorHex,
                isPinned
            };

            const isUpdate = !!note.id;
            if (isUpdate) payload.id = note.id;

            try {
                const res = await fetch('/api/notes', {
                    method: isUpdate ? 'PUT' : 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(payload)
                });
                if (res.ok) {
                    const resJson = await res.json().catch(() => ({}));
                    if (!isUpdate && resJson.id) {
                        note.id = resJson.id;
                    }
                    note.lastSaved = Date.now();

                    // Update cached list item in memory immediately
                    const idx = (window.notesData || []).findIndex(n => n.id === note.id);
                    if (idx !== -1) {
                        window.notesData[idx] = { ...note };
                    } else if (note.id) {
                        window.notesData.unshift({ ...note });
                    }

                    invalidateApiCache('notes');
                    if (typeof StorageCache !== 'undefined' && StorageCache && StorageCache.set) {
                        StorageCache.set('notes', window.notesData);
                    }

                    window.noteHasUnsavedChanges = false;
                    updateSaveStatusUI(true);

                    if (isManualClick) {
                        showNoteFeedbackToast('Catatan "' + title + '" berhasil disimpan!', 'bi-check-circle-fill', '#10B981');
                    }
                }
            } catch (err) {
                console.error("Gagal simpan catatan:", err);
                const statusEl = document.getElementById('editorAutoSaveStatus');
                if (statusEl) {
                    statusEl.innerHTML = '<i class="bi bi-exclamation-circle text-danger me-1"></i> Gagal simpan';
                }
            }
        }

        async function saveActiveNoteManual(silent) {
            return await saveActiveNoteToServer(!silent);
        }

        function setEditorCategory(catName) {
            if (!window.activeNoteItem) return;
            window.activeNoteItem.category = catName;
            const btn = document.getElementById('editorCategoryBtn');
            if (btn) btn.innerHTML = '<i class="bi bi-funnel me-1"></i> ' + escapeHtml(catName);
            showNoteFeedbackToast('Kategori diubah ke "' + catName + '"', 'bi-funnel-fill', '#6366F1');
            window.noteHasUnsavedChanges = true;
            updateSaveStatusUI(false);
        }

        function setEditorColor(hex, name) {
            if (!window.activeNoteItem) return;
            window.activeNoteItem.colorHex = hex;
            showNoteFeedbackToast('Warna diubah ke ' + name, 'bi-palette-fill', hex);
            window.noteHasUnsavedChanges = true;
            renderNoteEditor(document.getElementById('tab-content') || document.getElementById('main-content'));
            updateSaveStatusUI(false);
        }

        function toggleEditorPin() {
            if (!window.activeNoteItem) return;
            window.activeNoteItem.isPinned = !window.activeNoteItem.isPinned;
            const isP = window.activeNoteItem.isPinned;
            showNoteFeedbackToast(isP ? 'Catatan disematkan!' : 'Batal disematkan', 'bi-bookmark-star-fill', '#F59E0B');
            window.noteHasUnsavedChanges = true;
            renderNoteEditor(document.getElementById('tab-content') || document.getElementById('main-content'));
            updateSaveStatusUI(false);
        }

        function insertFormatting(type) {
            const textarea = document.getElementById('editorNoteContent');
            if (!textarea) return;

            const start = textarea.selectionStart;
            const end = textarea.selectionEnd;
            const text = textarea.value;
            const selectedText = text.substring(start, end);

            let replacement = '';
            let newCursorPos = end;

            switch (type) {
                case 'bold':
                    replacement = '**' + (selectedText || 'Teks Tebal') + '**';
                    newCursorPos = start + replacement.length;
                    break;
                case 'italic':
                    replacement = '*' + (selectedText || 'Teks Miring') + '*';
                    newCursorPos = start + replacement.length;
                    break;
                case 'strikethrough':
                    replacement = '~~' + (selectedText || 'Teks Dicoret') + '~~';
                    newCursorPos = start + replacement.length;
                    break;
                case 'h1':
                    replacement = '\n# ' + (selectedText || 'Judul Utama') + '\n';
                    newCursorPos = start + replacement.length;
                    break;
                case 'h2':
                    replacement = '\n## ' + (selectedText || 'Sub Judul') + '\n';
                    newCursorPos = start + replacement.length;
                    break;
                case 'bullet':
                    replacement = '\n• ' + (selectedText || 'Item daftar');
                    newCursorPos = start + replacement.length;
                    break;
                case 'number':
                    replacement = '\n1. ' + (selectedText || 'Item pertama');
                    newCursorPos = start + replacement.length;
                    break;
                case 'checklist':
                    replacement = '\n[ ] ' + (selectedText || 'Tugas yang harus dikerjakan');
                    newCursorPos = start + replacement.length;
                    break;
                case 'code':
                    replacement = '\n```\n' + (selectedText || '// Tulis kode di sini') + '\n```\n';
                    newCursorPos = start + replacement.length;
                    break;
                case 'divider':
                    replacement = '\n---\n';
                    newCursorPos = start + replacement.length;
                    break;
                case 'time':
                    const nowStr = new Date().toLocaleString('id-ID', { dateStyle: 'medium', timeStyle: 'short' });
                    replacement = ' [' + nowStr + '] ';
                    newCursorPos = start + replacement.length;
                    break;
            }

            textarea.value = text.substring(0, start) + replacement + text.substring(end);
            textarea.focus();
            textarea.setSelectionRange(newCursorPos, newCursorPos);
            handleEditorContentChange(textarea.value);
            recordUndoSnapshot(textarea.value, newCursorPos, true);
        }

        function performEditorUndo() {
            const textarea = document.getElementById('editorNoteContent');
            if (!textarea) return;
            if (window.noteUndoStack.length > 1) {
                const current = window.noteUndoStack.pop();
                window.noteRedoStack.push({ content: textarea.value, cursor: textarea.selectionStart });
                const prev = window.noteUndoStack[window.noteUndoStack.length - 1];
                textarea.value = prev.content;
                textarea.focus();
                textarea.setSelectionRange(prev.cursor, prev.cursor);
                if (window.activeNoteItem) window.activeNoteItem.content = prev.content;
                window.noteHasUnsavedChanges = true;
                updateSaveStatusUI(false);
                updateStatsCounter(prev.content);
            } else if (window.noteUndoStack.length === 1) {
                const prev = window.noteUndoStack[0];
                if (textarea.value !== prev.content) {
                    window.noteRedoStack.push({ content: textarea.value, cursor: textarea.selectionStart });
                    textarea.value = prev.content;
                    textarea.focus();
                    textarea.setSelectionRange(prev.cursor, prev.cursor);
                    if (window.activeNoteItem) window.activeNoteItem.content = prev.content;
                    window.noteHasUnsavedChanges = true;
                    updateSaveStatusUI(false);
                    updateStatsCounter(prev.content);
                }
            }
        }

        function performEditorRedo() {
            const textarea = document.getElementById('editorNoteContent');
            if (!textarea) return;
            if (window.noteRedoStack.length > 0) {
                const next = window.noteRedoStack.pop();
                window.noteUndoStack.push({ content: textarea.value, cursor: textarea.selectionStart });
                textarea.value = next.content;
                textarea.focus();
                textarea.setSelectionRange(next.cursor, next.cursor);
                if (window.activeNoteItem) window.activeNoteItem.content = next.content;
                window.noteHasUnsavedChanges = true;
                updateSaveStatusUI(false);
                updateStatsCounter(next.content);
            }
        }

        async function pasteAtCursor() {
            const textarea = document.getElementById('editorNoteContent');
            if (!textarea) return;
            try {
                let textToPaste = '';
                if (navigator.clipboard && navigator.clipboard.readText) {
                    textToPaste = await navigator.clipboard.readText();
                }
                if (!textToPaste) {
                    textToPaste = prompt("Tempel teks catatan di posisi kursor:") || '';
                }
                if (textToPaste) {
                    insertTextAtCursorWeb(textToPaste);
                    showNoteFeedbackToast('Teks berhasil ditempel di posisi kursor!', 'bi-clipboard-check', '#10B981');
                }
            } catch (e) {
                const textToPaste = prompt("Tempel teks catatan di posisi kursor:") || '';
                if (textToPaste) {
                    insertTextAtCursorWeb(textToPaste);
                    showNoteFeedbackToast('Teks berhasil ditempel di posisi kursor!', 'bi-clipboard-check', '#10B981');
                }
            }
        }

        function insertTextAtCursorWeb(textToInsert) {
            const textarea = document.getElementById('editorNoteContent');
            if (!textarea) return;
            const start = textarea.selectionStart;
            const end = textarea.selectionEnd;
            const val = textarea.value;
            const newVal = val.substring(0, start) + textToInsert + val.substring(end);
            textarea.value = newVal;
            const newCursor = start + textToInsert.length;
            textarea.focus();
            textarea.setSelectionRange(newCursor, newCursor);
            handleEditorContentChange(newVal);
            recordUndoSnapshot(newVal, newCursor, true);
        }

        function toggleEditorPreviewMode() {
            window.notePreviewMode = !window.notePreviewMode;
            renderNoteEditor(document.getElementById('tab-content') || document.getElementById('main-content'));
        }

        function shareOrCopyActiveNote() {
            if (!window.activeNoteItem) return;
            const t = window.activeNoteItem.title || 'Catatan';
            const c = window.activeNoteItem.content || '';
            const shareText = t + '\n\n' + c;
            navigator.clipboard.writeText(shareText).then(() => {
                showNoteFeedbackToast('Teks catatan berhasil disalin ke clipboard!', 'bi-clipboard-check', '#10B981');
            }).catch(() => {
                prompt('Salin teks catatan:', shareText);
            });
        }

        function shareOrCopyNote(noteId) {
            const found = (window.notesData || []).find(n => n.id === noteId);
            if (!found) return;
            const shareText = (found.title || 'Catatan') + '\n\n' + (found.content || '');
            navigator.clipboard.writeText(shareText).then(() => {
                showNoteFeedbackToast('Catatan "' + found.title + '" disalin ke clipboard!', 'bi-clipboard-check', '#10B981');
            }).catch(() => {
                prompt('Salin teks catatan:', shareText);
            });
        }

        async function toggleNotePinState(noteId) {
            const found = (window.notesData || []).find(n => n.id === noteId);
            if (!found) return;
            found.isPinned = !found.isPinned;
            try {
                await fetch('/api/notes', {
                    method: 'PUT',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(found)
                });
                invalidateApiCache('notes');
                if (typeof StorageCache !== 'undefined' && StorageCache && StorageCache.set) {
                    StorageCache.set('notes', window.notesData);
                }
                showNoteFeedbackToast('"' + found.title + '" ' + (found.isPinned ? 'disematkan ke atas' : 'batal disematkan'), 'bi-bookmark-star-fill', '#F59E0B');
                renderNotesUI();
            } catch (err) {
                console.error("Gagal pin:", err);
            }
        }

        function confirmDeleteNote(noteId) {
            const found = (window.notesData || []).find(n => n.id === noteId);
            const title = found ? found.title : 'Catatan ini';

            showCustomConfirm({
                title: 'Hapus Catatan',
                message: 'Apakah Anda yakin ingin menghapus catatan "' + title + '"?',
                confirmText: 'Hapus',
                onConfirm: async () => {
                    try {
                        const res = await fetch('/api/notes?id=' + noteId, { method: 'DELETE' });
                        if (res.ok) {
                            window.notesData = (window.notesData || []).filter(n => n.id !== noteId);
                            invalidateApiCache('notes');
                            if (typeof StorageCache !== 'undefined' && StorageCache && StorageCache.set) {
                                StorageCache.set('notes', window.notesData);
                            }
                            showNoteFeedbackToast('Catatan berhasil dihapus!', 'bi-trash', '#EF4444');
                            renderNotesUI();
                        }
                    } catch (e) {
                        alert('Gagal menghapus: ' + e.message);
                    }
                }
            });
        }

        function handleNotesSearch(val) {
            window.notesSearchQuery = val;
            renderNotesUI();
        }

        function clearNotesSearch() {
            window.notesSearchQuery = '';
            renderNotesUI();
        }

        function selectNotesCategory(cat) {
            window.notesSelectedCategory = cat;
            renderNotesUI();
        }

        function togglePinnedFilter() {
            window.notesSelectedCategory = window.notesSelectedCategory === '📌 Disematkan' ? 'Semua' : '📌 Disematkan';
            renderNotesUI();
        }

        function toggleNotesViewMode() {
            window.notesViewMode = window.notesViewMode === 'grid' ? 'list' : 'grid';
            localStorage.setItem('singghara_notes_view_mode', window.notesViewMode);
            renderNotesUI();
        }

        // --- BUKA DOKUMEN / PDF 100% OFFLINE ---
        function triggerOpenDocFile() {
            const input = document.getElementById('noteDocFileInput');
            if (input) input.click();
        }

        function handleDocFileSelected(event) {
            const file = event.target.files && event.target.files[0];
            if (!file) return;

            const fileName = file.name;
            const ext = fileName.split('.').pop().toLowerCase();

            if (ext === 'pdf') {
                // Tampilkan offline PDF modal viewer
                const fileUrl = URL.createObjectURL(file);
                showOfflinePdfModal(fileName, fileUrl);
                return;
            }

            // Baca file teks / kode / docx / html / json offline via FileReader
            const reader = new FileReader();
            reader.onload = function(e) {
                const textContent = e.target.result || '';
                // Buat catatan baru otomatis dengan isi file ini
                window.activeNoteItem = {
                    id: 0,
                    title: fileName,
                    content: textContent,
                    category: (ext === 'py' || ext === 'js' || ext === 'html' || ext === 'json' || ext === 'css' || ext === 'sql') ? 'Kode' : 'Umum',
                    colorHex: '#06B6D4',
                    isPinned: false,
                    lastSaved: Date.now()
                };
                window.noteUndoStack = [textContent];
                window.noteRedoStack = [];
                window.notePreviewMode = false;
                renderNotesUI();
                showNoteFeedbackToast('Dokumen "' + fileName + '" berhasil dibuka!', 'bi-file-earmark-check-fill', '#06B6D4');
            };
            reader.readAsText(file);
            event.target.value = '';
        }

        function showOfflinePdfModal(fileName, fileUrl) {
            let modalEl = document.getElementById('notePdfViewerModal');
            if (!modalEl) {
                const mDiv = document.createElement('div');
                mDiv.id = 'notePdfViewerModal';
                mDiv.className = 'modal fade';
                mDiv.tabIndex = -1;
                mDiv.innerHTML = `
                    <div class="modal-dialog modal-xl modal-dialog-centered" style="height: 90vh;">
                        <div class="modal-content card-custom h-100">
                            <div class="modal-header border-bottom-0 pb-1">
                                <h5 class="modal-title fw-bold text-primary" id="notePdfModalTitle">Pratinjau PDF</h5>
                                <button type="button" class="btn-close btn-close-custom" data-bs-dismiss="modal"></button>
                            </div>
                            <div class="modal-body p-0 h-100 overflow-hidden">
                                <iframe id="notePdfIframe" src="" style="width: 100%; height: 100%; border: none;"></iframe>
                            </div>
                        </div>
                    </div>
                `;
                document.body.appendChild(mDiv);
                modalEl = mDiv;
            }
            document.getElementById('notePdfModalTitle').innerText = fileName;
            document.getElementById('notePdfIframe').src = fileUrl;
            new bootstrap.Modal(modalEl).show();
        }

        // --- BUAT FILE KODE / SCRIPT MODAL ---
        function showCodeTemplateModal() {
            let modalEl = document.getElementById('noteCodeTemplateModal');
            if (!modalEl) {
                const mDiv = document.createElement('div');
                mDiv.id = 'noteCodeTemplateModal';
                mDiv.className = 'modal fade';
                mDiv.tabIndex = -1;
                mDiv.innerHTML = `
                    <div class="modal-dialog modal-dialog-centered">
                        <div class="modal-content card-custom">
                            <div class="modal-header border-bottom-0 pb-1">
                                <h5 class="modal-title fw-bold text-primary"><i class="bi bi-code-slash me-2"></i> Buat Script / File Kode</h5>
                                <button type="button" class="btn-close btn-close-custom" data-bs-dismiss="modal"></button>
                            </div>
                            <div class="modal-body pt-2">
                                <p class="text-secondary small mb-3">Pilih bahasa pemrograman atau format file untuk membuat catatan baru:</p>
                                <div class="row g-2">
                                    <div class="col-6">
                                        <button class="btn btn-outline-primary w-100 text-start p-2 d-flex align-items-center gap-2" onclick="createCodeSnippetNote('Python', 'script.py', '# Python Script\n\ndef main():\n    print(\"Hello Singghara!\")\n\nif __name__ == \"__main__\":\n    main()\n')">
                                            <i class="bi bi-filetype-py fs-4 text-warning"></i>
                                            <div><div class="fw-bold small">Python</div><small class="text-muted">.py</small></div>
                                        </button>
                                    </div>
                                    <div class="col-6">
                                        <button class="btn btn-outline-primary w-100 text-start p-2 d-flex align-items-center gap-2" onclick="createCodeSnippetNote('JavaScript', 'app.js', '// JavaScript Module\n\nfunction init() {\n    console.log(\"Singghara Suite Ready\");\n}\n\ninit();\n')">
                                            <i class="bi bi-filetype-js fs-4 text-warning"></i>
                                            <div><div class="fw-bold small">JavaScript</div><small class="text-muted">.js</small></div>
                                        </button>
                                    </div>
                                    <div class="col-6">
                                        <button class="btn btn-outline-primary w-100 text-start p-2 d-flex align-items-center gap-2" onclick="createCodeSnippetNote('HTML5', 'index.html', '<!DOCTYPE html>\n<html lang=\"id\">\n<head>\n    <meta charset=\"UTF-8\">\n    <title>Halaman Web</title>\n</head>\n<body>\n    <h1>Selamat Datang</h1>\n</body>\n</html>\n')">
                                            <i class="bi bi-filetype-html fs-4 text-danger"></i>
                                            <div><div class="fw-bold small">HTML5</div><small class="text-muted">.html</small></div>
                                        </button>
                                    </div>
                                    <div class="col-6">
                                        <button class="btn btn-outline-primary w-100 text-start p-2 d-flex align-items-center gap-2" onclick="createCodeSnippetNote('JSON Data', 'data.json', '{\n    \"app\": \"Singghara Suite\",\n    \"version\": \"1.0.0\",\n    \"offline\": true\n}\n')">
                                            <i class="bi bi-filetype-json fs-4 text-success"></i>
                                            <div><div class="fw-bold small">JSON</div><small class="text-muted">.json</small></div>
                                        </button>
                                    </div>
                                    <div class="col-6">
                                        <button class="btn btn-outline-primary w-100 text-start p-2 d-flex align-items-center gap-2" onclick="createCodeSnippetNote('Markdown', 'README.md', '# Catatan Proyek\n\n## Rangkuman\n- Poin 1\n- Poin 2\n\n## Checklist\n- [ ] Persiapan\n- [x] Selesai\n')">
                                            <i class="bi bi-filetype-md fs-4 text-info"></i>
                                            <div><div class="fw-bold small">Markdown</div><small class="text-muted">.md</small></div>
                                        </button>
                                    </div>
                                    <div class="col-6">
                                        <button class="btn btn-outline-primary w-100 text-start p-2 d-flex align-items-center gap-2" onclick="createCodeSnippetNote('SQL Query', 'query.sql', '-- Query Database Lokal\n\nSELECT * FROM data_karyawan\nWHERE aktif = 1\nORDER BY nama ASC;\n')">
                                            <i class="bi bi-filetype-sql fs-4 text-primary"></i>
                                            <div><div class="fw-bold small">SQL Query</div><small class="text-muted">.sql</small></div>
                                        </button>
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div>
                `;
                document.body.appendChild(mDiv);
                modalEl = mDiv;
            }
            new bootstrap.Modal(modalEl).show();
        }

        function createCodeSnippetNote(langName, defaultFilename, boilerplate) {
            const modalEl = document.getElementById('noteCodeTemplateModal');
            if (modalEl) bootstrap.Modal.getInstance(modalEl)?.hide();

            window.activeNoteItem = {
                id: 0,
                title: defaultFilename,
                content: boilerplate,
                category: 'Kode',
                colorHex: '#8B5CF6',
                isPinned: false,
                lastSaved: Date.now()
            };
            window.noteUndoStack = [boilerplate];
            window.noteRedoStack = [];
            window.notePreviewMode = false;
            renderNotesUI();
            showNoteFeedbackToast('File template ' + langName + ' dibuat!', 'bi-code-slash', '#8B5CF6');
        }

        // =========================================================================
        // --- 2. SUB-MENU JADWAL HARIAN (PERSIS JadwalHarianScreen.kt) ---
        // =========================================================================

        async function loadJadwal() {
            try {
                const fetchRes = await fetchApiWithCache('/api/jadwal', 'jadwal');
                window.jadwalData = Array.isArray(fetchRes.data) ? fetchRes.data : [];
                renderJadwalUI();
            } catch (err) {
                console.error("Gagal memuat jadwal:", err);
                const container = document.getElementById('tab-content') || document.getElementById('main-content');
                if (container) {
                    container.innerHTML = `<div class="alert alert-danger m-3">Gagal memuat jadwal: ${dol}{escapeHtml(err.message)}</div>`;
                }
            }
        }

        function renderJadwalUI() {
            const container = document.getElementById('tab-content') || document.getElementById('main-content');
            if (!container) return;

            const list = window.jadwalData || [];
            const completedCount = list.filter(j => j.isCompleted).length;
            const totalCount = list.length;
            const percentage = totalCount > 0 ? Math.round((completedCount / totalCount) * 100) : 0;

            let html = `
                <div class="container-fluid px-3 py-2 position-relative" id="jadwalMainWrapper">
                    <!-- FLOATING FEEDBACK TOAST -->
                    <div id="jadwalFloatingToast" style="display: none; position: fixed; top: 75px; left: 50%; transform: translateX(-50%); z-index: 1060; transition: opacity 0.3s ease;"></div>

                    <!-- TABLE TITLE HEADER WITH '+' BUTTON BESIDE IT AS IN ANDROID -->
                    <div class="d-flex justify-content-between align-items-center mb-3">
                        <div class="d-flex align-items-center gap-2">
                            <i class="bi bi-list-task fs-4 text-primary"></i>
                            <h5 class="fw-bold mb-0 text-primary">Tabel Rencana Jadwal</h5>
                        </div>
                        <button type="button" class="btn btn-primary rounded-circle d-flex align-items-center justify-content-center shadow-sm"
                                style="width: 38px; height: 38px;" title="Tambah Jadwal Baru" onclick="openAddJadwalModal()">
                            <i class="bi bi-plus-lg fs-5"></i>
                        </button>
                    </div>
            `;

            if (list.length === 0) {
                // Empty state matching Android
                html += `
                    <div class="card card-custom p-5 text-center my-4 border border-dashed">
                        <div class="mb-3">
                            <div class="rounded-circle d-inline-flex align-items-center justify-content-center bg-secondary bg-opacity-10" style="width: 72px; height: 72px;">
                                <i class="bi bi-calendar3 text-secondary opacity-75" style="font-size: 2.2rem;"></i>
                            </div>
                        </div>
                        <h5 class="fw-bold mb-1">Belum Ada Jadwal</h5>
                        <p class="text-secondary small mb-3 max-w-500 mx-auto">
                            Ketuk tombol + di atas untuk merancang aktivitas harian baru Anda.
                        </p>
                        <div>
                            <button class="btn btn-primary rounded-pill px-4 fw-bold shadow-sm" onclick="openAddJadwalModal()">
                                <i class="bi bi-plus-lg me-1"></i> Tambah Jadwal Baru
                            </button>
                        </div>
                    </div>
                `;
            } else {
                // PROGRESS BAR SECTION (PERSIS Card Progres Rencana Hari Ini DI ANDROID)
                html += `
                    <div class="card card-custom p-3 mb-3" style="background: rgba(67, 160, 71, 0.08); border: 1.2px solid rgba(67, 160, 71, 0.25) !important;">
                        <div class="d-flex justify-content-between align-items-center mb-2">
                            <span class="fw-bold small text-success">Progres Rencana Hari Ini</span>
                            <span class="fw-bold small text-success">${dol}{completedCount}/${dol}{totalCount} Selesai (${dol}{percentage}%)</span>
                        </div>
                        <div class="progress rounded-pill" style="height: 7px; background: rgba(67, 160, 71, 0.18);">
                            <div class="progress-bar rounded-pill bg-success" role="progressbar"
                                 style="width: ${dol}{percentage}%; transition: width 0.4s ease;"></div>
                        </div>
                    </div>

                    <!-- STRUCTURED TABLE WITH ALL BORDERS (PERSIS TABEL JadwalHarianScreen.kt) -->
                    <div class="card card-custom shadow-sm p-0 overflow-hidden" style="border: 1px solid rgba(0,0,0,0.12); border-radius: 12px;">
                        <div class="table-responsive mb-0">
                            <table class="table table-bordered table-hover align-middle mb-0 text-nowrap" style="border-color: rgba(0,0,0,0.08);">
                                <thead style="background: rgba(67, 160, 71, 0.15);">
                                    <tr>
                                        <th class="text-center text-success fw-bold py-3" style="width: 55px; font-size: 11px;">CEK</th>
                                        <th class="text-center text-success fw-bold py-3" style="width: 140px; font-size: 11px;">WAKTU</th>
                                        <th class="text-success fw-bold py-3" style="font-size: 11px;">AKTIVITAS</th>
                                        <th class="text-success fw-bold py-3" style="width: 160px; font-size: 11px;">KATEGORI</th>
                                        <th class="text-center text-success fw-bold py-3" style="width: 105px; font-size: 11px;">AKSI</th>
                                    </tr>
                                </thead>
                                <tbody>
                `;

                list.forEach(item => {
                    const catCfg = getJadwalCatConfig(item.kategori);
                    const isDone = !!item.isCompleted;

                    html += `
                        <tr class="${dol}{isDone ? 'table-light' : ''}" style="${dol}{isDone ? 'background: rgba(0,0,0,0.02);' : ''}">
                            <!-- CEK (CHECKBOX) -->
                            <td class="text-center align-middle" style="width: 55px;">
                                <input type="checkbox" class="form-check-input cursor-pointer"
                                       ${dol}{isDone ? 'checked' : ''}
                                       onchange="toggleJadwalCompletion(${dol}{item.id}, this.checked)"
                                       style="width: 18px; height: 18px;">
                            </td>

                            <!-- WAKTU -->
                            <td class="text-center align-middle" style="width: 140px;">
                                <div class="d-inline-flex align-items-center gap-1 fw-bold small ${dol}{isDone ? 'opacity-50 text-secondary' : 'text-body'}">
                                    <i class="bi bi-clock-fill text-success" style="font-size: 12px;"></i>
                                    <span>${dol}{escapeHtml(item.waktu || '')}</span>
                                </div>
                            </td>

                            <!-- AKTIVITAS -->
                            <td class="align-middle">
                                <span class="fw-medium text-break ${dol}{isDone ? 'text-decoration-line-through text-secondary opacity-50' : 'text-body'}"
                                      style="font-size: 13.5px; transition: all 0.3s ease;">
                                    ${dol}{escapeHtml(item.kegiatan || '')}
                                </span>
                            </td>

                            <!-- KATEGORI BADGE -->
                            <td class="align-middle" style="width: 160px;">
                                <span class="badge rounded-pill px-2 py-1 fw-bold d-inline-flex align-items-center gap-1"
                                      style="background: ${dol}{catCfg.color}1E; color: ${dol}{catCfg.color}; font-size: 11px; border: 1px solid ${dol}{catCfg.color}35;">
                                    <i class="bi ${dol}{catCfg.icon}"></i>
                                    <span>${dol}{escapeHtml(catCfg.name)}</span>
                                </span>
                            </td>

                            <!-- AKSI (EDIT & DELETE) -->
                            <td class="text-center align-middle" style="width: 105px;">
                                <div class="d-flex align-items-center justify-content-center gap-1">
                                    <button type="button" class="btn btn-sm btn-light border-0 p-1 text-primary"
                                            title="Edit Jadwal" onclick="openEditJadwalModal(${dol}{item.id})">
                                        <i class="bi bi-pencil-square"></i>
                                    </button>
                                    <button type="button" class="btn btn-sm btn-light border-0 p-1 text-danger"
                                            title="Hapus Jadwal" onclick="confirmDeleteJadwal(${dol}{item.id})">
                                        <i class="bi bi-trash"></i>
                                    </button>
                                </div>
                            </td>
                        </tr>
                    `;
                });

                html += `
                                </tbody>
                            </table>
                        </div>
                    </div>
                `;
            }

            html += `</div>`;
            container.innerHTML = html;
        }

        // --- JADWAL ACTIONS & MODAL ---
        function showJadwalFeedbackToast(msg, iconClass, colorHex) {
            const toastContainer = document.getElementById('jadwalFloatingToast');
            if (!toastContainer) return;
            toastContainer.innerHTML = `
                <div class="card shadow-lg border-0 px-3 py-2 rounded-pill d-flex flex-row align-items-center gap-2"
                     style="background: var(--card-bg, #ffffff); border: 1.5px solid ${dol}{colorHex || '#43A047'} !important; color: ${dol}{colorHex || '#43A047'};">
                    <i class="bi ${dol}{iconClass || 'bi-check-circle-fill'} fs-5"></i>
                    <span class="fw-bold small text-body">${dol}{escapeHtml(msg)}</span>
                </div>
            `;
            toastContainer.style.display = 'block';
            toastContainer.style.opacity = '1';
            clearTimeout(window.jadwalToastTimer);
            window.jadwalToastTimer = setTimeout(() => {
                toastContainer.style.opacity = '0';
                setTimeout(() => { toastContainer.style.display = 'none'; }, 300);
            }, 2600);
        }

        async function toggleJadwalCompletion(jadwalId, isChecked) {
            const found = (window.jadwalData || []).find(j => j.id === jadwalId);
            if (!found) return;

            found.isCompleted = isChecked;
            try {
                await fetch('/api/jadwal', {
                    method: 'PUT',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(found)
                });
                invalidateApiCache('jadwal');
                if (typeof StorageCache !== 'undefined' && StorageCache && StorageCache.set) {
                    StorageCache.set('jadwal', window.jadwalData);
                }
                renderJadwalUI();
            } catch (err) {
                console.error("Gagal update status jadwal:", err);
            }
        }

        function openAddJadwalModal() {
            window.jadwalItemToEdit = null;
            showJadwalFormModal("Tambah Jadwal Baru", "07.00 - 08.00", "", "Rutinitas Pagi");
        }

        function openEditJadwalModal(jadwalId) {
            const found = (window.jadwalData || []).find(j => j.id === jadwalId);
            if (!found) return;
            window.jadwalItemToEdit = found;
            showJadwalFormModal("Edit Jadwal Kegiatan", found.waktu || "07.00 - 08.00", found.kegiatan || "", found.kategori || "Rutinitas Pagi");
        }

        function showJadwalFormModal(modalTitle, defaultWaktu, defaultKegiatan, defaultKategori) {
            let modalEl = document.getElementById('jadwalFormModal');
            if (!modalEl) {
                const mDiv = document.createElement('div');
                mDiv.id = 'jadwalFormModal';
                mDiv.className = 'modal fade';
                mDiv.tabIndex = -1;
                mDiv.innerHTML = `
                    <div class="modal-dialog modal-dialog-centered">
                        <div class="modal-content card-custom">
                            <div class="modal-header border-bottom-0 pb-1">
                                <h5 class="modal-title fw-bold text-success" id="jadwalModalTitle">Jadwal</h5>
                                <button type="button" class="btn-close btn-close-custom" data-bs-dismiss="modal"></button>
                            </div>
                            <div class="modal-body pt-2">
                                <!-- Quick Presets Chip Buttons -->
                                <div class="mb-3">
                                    <label class="form-label small fw-bold text-secondary">Pilihan Jam Cepat</label>
                                    <div class="d-flex flex-wrap gap-1">
                                        <button type="button" class="btn btn-sm btn-outline-secondary rounded-pill py-0 px-2" onclick="setJadwalTimePreset('06.00 - 07.00')">06.00 - 07.00</button>
                                        <button type="button" class="btn btn-sm btn-outline-secondary rounded-pill py-0 px-2" onclick="setJadwalTimePreset('07.00 - 08.00')">07.00 - 08.00</button>
                                        <button type="button" class="btn btn-sm btn-outline-secondary rounded-pill py-0 px-2" onclick="setJadwalTimePreset('08.00 - 09.00')">08.00 - 09.00</button>
                                        <button type="button" class="btn btn-sm btn-outline-secondary rounded-pill py-0 px-2" onclick="setJadwalTimePreset('12.00 - 13.00')">12.00 - 13.00</button>
                                        <button type="button" class="btn btn-sm btn-outline-secondary rounded-pill py-0 px-2" onclick="setJadwalTimePreset('16.00 - 17.00')">16.00 - 17.00</button>
                                        <button type="button" class="btn btn-sm btn-outline-secondary rounded-pill py-0 px-2" onclick="setJadwalTimePreset('19.00 - 20.00')">19.00 - 20.00</button>
                                        <button type="button" class="btn btn-sm btn-outline-secondary rounded-pill py-0 px-2" onclick="setJadwalTimePreset('21.00 - 22.00')">21.00 - 22.00</button>
                                    </div>
                                </div>

                                <!-- Waktu Input -->
                                <div class="mb-3">
                                    <label class="form-label small fw-bold">Waktu Pelaksanaan</label>
                                    <div class="input-group">
                                        <span class="input-group-text bg-body text-success"><i class="bi bi-clock"></i></span>
                                        <input type="text" class="form-control" id="jadwalInputWaktu" placeholder="Contoh: 07.00 - 08.00">
                                    </div>
                                </div>

                                <!-- Nama Kegiatan Input -->
                                <div class="mb-3">
                                    <label class="form-label small fw-bold">Nama Kegiatan / Aktivitas</label>
                                    <input type="text" class="form-control" id="jadwalInputKegiatan" placeholder="Contoh: Senam pagi & sarapan sehat...">
                                </div>

                                <!-- Kategori Dropdown Selection -->
                                <div class="mb-3">
                                    <label class="form-label small fw-bold">Kategori Aktivitas</label>
                                    <select class="form-select" id="jadwalInputKategori">
                                        ${dol}{JADWAL_CATEGORIES.map(c => `
                                            <option value="${dol}{c.name}">${dol}{c.name}</option>
                                        `).join('')}
                                    </select>
                                </div>
                            </div>
                            <div class="modal-footer border-top-0 pt-0">
                                <button type="button" class="btn btn-outline-secondary" data-bs-dismiss="modal">Batal</button>
                                <button type="button" class="btn btn-success fw-bold px-4" onclick="saveJadwalForm()">
                                    <i class="bi bi-save me-1"></i> Simpan
                                </button>
                            </div>
                        </div>
                    </div>
                `;
                document.body.appendChild(mDiv);
                modalEl = mDiv;
            }

            document.getElementById('jadwalModalTitle').innerText = modalTitle;
            document.getElementById('jadwalInputWaktu').value = defaultWaktu;
            document.getElementById('jadwalInputKegiatan').value = defaultKegiatan;
            document.getElementById('jadwalInputKategori').value = defaultKategori;

            new bootstrap.Modal(modalEl).show();
        }

        function setJadwalTimePreset(preset) {
            const input = document.getElementById('jadwalInputWaktu');
            if (input) input.value = preset;
        }

        async function saveJadwalForm() {
            const waktu = (document.getElementById('jadwalInputWaktu')?.value || '').trim();
            const kegiatan = (document.getElementById('jadwalInputKegiatan')?.value || '').trim();
            const kategori = (document.getElementById('jadwalInputKategori')?.value || '').trim() || 'Rutinitas Pagi';

            if (!waktu) return alert('Waktu jadwal wajib diisi!');
            if (!kegiatan) return alert('Nama kegiatan wajib diisi!');

            // Check duplicate check matching Android
            const currentItem = window.jadwalItemToEdit;
            const isDuplicate = (window.jadwalData || []).some(item => {
                if (currentItem && item.id === currentItem.id) return false;
                return item.waktu.trim() === waktu && item.kegiatan.trim().toLowerCase() === kegiatan.toLowerCase();
            });

            if (isDuplicate) {
                alert('Aktivitas dengan waktu yang sama sudah terdaftar!');
                return;
            }

            const payload = {
                waktu,
                kegiatan,
                kategori,
                isCompleted: currentItem ? !!currentItem.isCompleted : false
            };

            const isEdit = !!currentItem;
            if (isEdit) payload.id = currentItem.id;

            try {
                const res = await fetch('/api/jadwal', {
                    method: isEdit ? 'PUT' : 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(payload)
                });
                if (res.ok) {
                    const modalEl = document.getElementById('jadwalFormModal');
                    if (modalEl) {
                        try {
                            const inst = bootstrap.Modal.getInstance(modalEl);
                            if (inst) inst.hide();
                        } catch(e) {}
                        modalEl.classList.remove('show');
                        modalEl.style.display = 'none';
                        document.body.classList.remove('modal-open');
                        const backdrop = document.getElementById('custom-modal-backdrop') || document.querySelector('.modal-backdrop');
                        if (backdrop) backdrop.remove();
                    }

                    invalidateApiCache('jadwal');
                    await loadJadwal();
                    showJadwalFeedbackToast(isEdit ? 'Jadwal berhasil diperbarui!' : 'Jadwal berhasil disimpan!', 'bi-check-circle-fill', '#43A047');
                } else {
                    alert('Gagal menyimpan jadwal');
                }
            } catch (err) {
                alert('Terjadi kesalahan: ' + err.message);
            }
        }

        function confirmDeleteJadwal(jadwalId) {
            const found = (window.jadwalData || []).find(j => j.id === jadwalId);
            const title = found ? found.kegiatan : 'Jadwal ini';

            showCustomConfirm({
                title: 'Hapus Jadwal Kegiatan',
                message: 'Apakah Anda yakin ingin menghapus kegiatan \'' + title + '\' dari rencana harian Anda?',
                confirmText: 'Hapus',
                onConfirm: async () => {
                    try {
                        const res = await fetch('/api/jadwal?id=' + jadwalId, { method: 'DELETE' });
                        if (res.ok) {
                            window.jadwalData = (window.jadwalData || []).filter(j => j.id !== jadwalId);
                            invalidateApiCache('jadwal');
                            if (typeof StorageCache !== 'undefined' && StorageCache && StorageCache.set) {
                                StorageCache.set('jadwal', window.jadwalData);
                            }
                            showJadwalFeedbackToast('Jadwal berhasil dihapus!', 'bi-trash', '#EF4444');
                            renderJadwalUI();
                        }
                    } catch (e) {
                        alert('Gagal menghapus: ' + e.message);
                    }
                }
            });
        }
        window.openAddNoteModal = openCreateNewNote;
        window.openEditNoteModal = function(id) { openEditNoteById(id); };
        """
    }
}
