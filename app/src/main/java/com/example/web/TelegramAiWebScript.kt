package com.example.web

/**
 * Script Web Connect untuk Fitur Telegram AI Assistant (100% OFFLINE PC).
 * Menyajikan tampilan dan fungsi yang sama persis seperti TelegramAiScreen.kt
 * pada aplikasi My Singghara Android:
 * - Section 0: Uji Coba AI Assistant (Chat interaktif dengan 25 suggestion chips, speech bubbles copyable)
 * - Section 1: Pengaturan & Chat ID (Bot Token, Chat ID, Auto-detect, Kirim Pesan Tes, Allow Any Chat)
 * - Section 2: Log Aktivitas Bot (Badge MASUK/BALASAN/SISTEM, Timestamp, Sender Info, Bersihkan Log)
 * - Top Bar & 3-Dots Action Menu (Uji Coba AI, Pengaturan, Log, Buka di Telegram, Start/Stop Bot)
 * - FIXED / STICKY BOTTOM POSITIONING untuk Form Tulis Pesan (selalu diam di bawah agar tidak perlu scroll)
 * - Full Dark Mode & Light Mode synchronization harmonized with CSS variables (--card-bg, --bg-main, --text-main, etc.)
 */
object TelegramAiWebScript {

    fun getScript(dol: String): String {
        return """
        // =========================================================================
        // --- TELEGRAM AI ASSISTANT (100% OFFLINE WEB CONNECT PERSIS TELEGRAMAISCREEN.KT) ---
        // =========================================================================

        window.telegramAiState = {
            currentSection: 0, // 0: Uji Coba AI, 1: Pengaturan & Chat ID, 2: Log Aktivitas Bot
            isRunning: false,
            botToken: "8670152772:AAHEVL05UN-e7HiD233MME1eTvx-3Lo--0k",
            registeredChatId: "",
            allowAnyChat: true,
            botUsername: "My_Singghara_bot",
            botFirstName: "My Singghara",
            logs: [],
            // In-App Chat Test State
            inputQuery: "",
            isSendingInApp: false,
            chatMessages: [
                {
                    isUser: false,
                    text: "👋 Halo! Saya asisten AI resmi My Singghara yang terhubung dua arah dengan database ponsel Anda. Kini selain membaca data & mencatat data baru, Anda juga dapat MENGEDIT DATA langsung (Edit Catatan, Ubah Agenda, Tandai/Edit Belanja Pasar, Ubah Jadwal Harian, Edit Stok/Harga Kasir, dan Target Tabungan). Coba pilih saran aksi atau ketik perintah Anda di bawah!"
                }
            ],
            // Settings Form State
            tokenInput: "",
            isTokenVisible: false,
            chatIdInput: "",
            isDetectingChatId: false,
            isTestingTelegramMessage: false,
            showMoreMenu: false
        };

        window.telegramAiQuickQuestions = [
            "✏️ Edit catatan kegiatan besok: Tambah sound system dan konsumsi",
            "🛒 Tandai belanja telur sudah dibeli",
            "🛒 Ubah belanja minyak jadi 2 liter",
            "📅 Ubah agenda rapat ke besok jam 10:00",
            "⏰ Edit jadwal olahraga jam 06:30",
            "🏪 Edit stok beras jadi 30",
            "🏪 Ubah harga minyak goreng jadi 28000",
            "💰 Ubah target tabungan jadi 15000000",
            "🏢 Ubah divisi Budi jadi Marketing",
            "📌 Sematkan catatan penting",
            "Buatkan saya catatan penting untuk kegiatan besok",
            "Jadwalkan agenda besok rapat evaluasi jam 09:00",
            "Beli telur 1 kg dan beras 5 kg",
            "Catat tabungan masuk 100000 dari jualan",
            "Jadwal sholat hari ini",
            "Perkiraan cuaca hari ini",
            "Apakah hari ini libur?",
            "Cek penjualan kasir",
            "Stok barang kasir",
            "Data karyawan company",
            "Absensi hari ini",
            "Laporan laba rugi usaha",
            "Berapa saldo tabungan saya?",
            "Ada agenda apa hari ini?",
            "Apa yang perlu dibeli di pasar?"
        ];

        window.telegramAiSyncTimer = null;

        // Inisialisasi Tampilan Telegram AI Assistant
        window.loadTelegramAi = function() {
            const container = document.getElementById('tab-content') || document.getElementById('main-content') || document.getElementById('mainContentContainer') || document.getElementById('tabContent');
            if (!container) return;

            // Render container utama
            renderTelegramAiView();

            // Fetch state pertama kali dari server
            fetchTelegramAiState();

            // Mulai polling sinkronisasi status bot & log
            startTelegramAiSync();
        };

        function startTelegramAiSync() {
            if (window.telegramAiSyncTimer) clearInterval(window.telegramAiSyncTimer);
            window.telegramAiSyncTimer = setInterval(() => {
                if (currentTab === 'telegram_ai' || currentTab === 'telegram' || currentTab === 'tg') {
                    fetchTelegramAiState(true);
                } else {
                    stopTelegramAiSync();
                }
            }, 3000);
        }

        function stopTelegramAiSync() {
            if (window.telegramAiSyncTimer) {
                clearInterval(window.telegramAiSyncTimer);
                window.telegramAiSyncTimer = null;
            }
        }

        // Fetch state dari LocalHttpServer
        function fetchTelegramAiState(isBackgroundSync) {
            fetch('/api/telegram_ai/state')
                .then(r => r.json())
                .then(data => {
                    const st = window.telegramAiState;
                    const prevRunning = st.isRunning;
                    st.isRunning = !!data.isRunning;
                    st.botToken = data.botToken || st.botToken;
                    st.registeredChatId = data.registeredChatId || '';
                    st.allowAnyChat = (data.allowAnyChat !== undefined) ? !!data.allowAnyChat : true;
                    st.botUsername = data.botUsername || 'My_Singghara_bot';
                    st.botFirstName = data.botFirstName || 'My Singghara';
                    st.logs = Array.isArray(data.logs) ? data.logs : [];

                    // Inisialisasi nilai form pengaturan jika pertama kali / belum diubah
                    if (!st.tokenInput) {
                        st.tokenInput = st.botToken;
                    }
                    if (!isBackgroundSync && !st.chatIdInput) {
                        st.chatIdInput = st.registeredChatId;
                    }

                    // Render ulang hanya jika bukan input aktif di form settings
                    if (!isBackgroundSync || prevRunning !== st.isRunning || st.currentSection === 2) {
                        renderTelegramAiView();
                    } else {
                        updateTelegramAiHeaderStatus();
                    }
                })
                .catch(err => {
                    console.warn('Gagal memuat state Telegram AI:', err);
                });
        }

        // Render Tampilan Keseluruhan Sesuai Section yang Aktif
        function renderTelegramAiView() {
            const container = document.getElementById('tab-content') || document.getElementById('main-content') || document.getElementById('mainContentContainer') || document.getElementById('tabContent');
            if (!container) return;

            const st = window.telegramAiState;

            let html = '';
            // Container utama setinggi viewport dengan overflow terisolasi
            html += '<div class="d-flex flex-column tg-ai-root" style="height: calc(100vh - 112px); max-height: calc(100vh - 112px); min-height: 500px; background: var(--bg-main); color: var(--text-main); position: relative; overflow: hidden; border-radius: 18px; border: 1px solid var(--border-color); box-shadow: var(--card-shadow);">';

            // --- 1. TOP BAR PERSIS TELEGRAMAISCREEN.KT (STATIONARY DI ATAS) ---
            html += '<div class="d-flex align-items-center justify-content-between px-3 py-2 border-bottom tg-ai-topbar flex-shrink-0" style="border-color: var(--border-color) !important; background: var(--card-bg); color: var(--text-main); z-index: 20;">';
            
            if (st.currentSection !== 0) {
                // Tombol Back ke Uji Coba AI
                html += '<div class="d-flex align-items-center flex-grow-1">';
                html += '<button type="button" class="btn btn-sm rounded-circle me-2 d-flex align-items-center justify-content-center tg-ai-circle-btn shadow-none" style="width: 36px; height: 36px; background: var(--bg-hover); color: var(--text-main); border: 1px solid var(--border-color);" onclick="setTelegramAiSection(0)">';
                html += '<i class="bi bi-arrow-left fs-5"></i>';
                html += '</button>';
                html += '<span class="fw-bold fs-6 tg-ai-header-title" style="color: var(--text-main);">';
                html += (st.currentSection === 1) ? 'Pengaturan & Chat ID' : 'Log Aktivitas Bot';
                html += '</span>';
                html += '</div>';
            } else {
                // Section 0: Status Dot + Judul + Badge Aktif (dengan tombol kembali jika dibuka dari profil)
                html += '<div class="d-flex align-items-center flex-grow-1" id="tg-header-status-box">';
                if (window.telegramAiCameFromProfile) {
                    html += '<button type="button" class="btn btn-sm rounded-circle me-2 d-flex align-items-center justify-content-center tg-ai-circle-btn shadow-none" style="width: 36px; height: 36px; background: var(--bg-hover); color: var(--text-main); border: 1px solid var(--border-color);" onclick="returnToProfileFromTelegramAi()" title="Kembali ke Profil">';
                    html += '<i class="bi bi-arrow-left fs-5"></i>';
                    html += '</button>';
                }
                html += '<span class="rounded-circle me-2 d-inline-block" style="width: 10px; height: 10px; background-color: ' + (st.isRunning ? '#10B981' : '#94A3B8') + ';"></span>';
                html += '<span class="fw-bold fs-6 me-2 tg-ai-header-title" style="color: var(--text-main);">Uji Coba AI Assistant</span>';
                if (st.isRunning) {
                    html += '<span class="badge rounded-pill fw-semibold px-2 py-1" style="background-color: rgba(16, 185, 129, 0.2); color: #10B981; border: 1px solid rgba(16, 185, 129, 0.35); font-size: 11px;">Aktif</span>';
                }
                html += '</div>';
            }

            // 3-Dots Action Dropdown Menu
            html += '<div class="dropdown position-relative">';
            html += '<button type="button" class="btn btn-sm rounded-circle d-flex align-items-center justify-content-center tg-ai-circle-btn shadow-none" style="width: 36px; height: 36px; background: var(--bg-hover); color: var(--text-main); border: 1px solid var(--border-color);" onclick="toggleTelegramAiMenu()">';
            html += '<i class="bi bi-three-dots-vertical fs-5"></i>';
            html += '</button>';

            if (st.showMoreMenu) {
                html += '<div class="dropdown-menu show shadow-lg py-2 position-absolute end-0 mt-1 tg-ai-dropdown-menu" style="z-index: 1060; min-width: 230px; font-size: 13.5px; background: var(--card-bg); border: 1px solid var(--border-color); box-shadow: var(--card-shadow); border-radius: 14px;">';
                
                // Menu Item: Uji Coba AI
                html += '<button class="dropdown-item d-flex align-items-center py-2 px-3 tg-ai-dropdown-item ' + (st.currentSection === 0 ? 'fw-bold' : '') + '" style="color: var(--text-main); background: transparent;" onclick="setTelegramAiSection(0)">';
                html += '<i class="bi bi-chat-dots-fill me-2 fs-5" style="color: ' + (st.currentSection === 0 ? '#0288D1' : 'var(--text-sub)') + ';"></i>';
                html += '<span class="flex-grow-1">Uji Coba AI</span>';
                if (st.currentSection === 0) html += '<i class="bi bi-check-lg fw-bold text-primary"></i>';
                html += '</button>';

                // Menu Item: Pengaturan & Chat ID
                html += '<button class="dropdown-item d-flex align-items-center py-2 px-3 tg-ai-dropdown-item ' + (st.currentSection === 1 ? 'fw-bold' : '') + '" style="color: var(--text-main); background: transparent;" onclick="setTelegramAiSection(1)">';
                html += '<i class="bi bi-gear-fill me-2 fs-5" style="color: ' + (st.currentSection === 1 ? '#0288D1' : 'var(--text-sub)') + ';"></i>';
                html += '<span class="flex-grow-1">Pengaturan & Chat ID</span>';
                if (st.currentSection === 1) html += '<i class="bi bi-check-lg fw-bold text-primary"></i>';
                html += '</button>';

                // Menu Item: Log Aktivitas
                const logCountText = st.logs.length > 0 ? ' (' + st.logs.length + ')' : '';
                html += '<button class="dropdown-item d-flex align-items-center py-2 px-3 tg-ai-dropdown-item ' + (st.currentSection === 2 ? 'fw-bold' : '') + '" style="color: var(--text-main); background: transparent;" onclick="setTelegramAiSection(2)">';
                html += '<i class="bi bi-list-ul me-2 fs-5" style="color: ' + (st.currentSection === 2 ? '#0288D1' : 'var(--text-sub)') + ';"></i>';
                html += '<span class="flex-grow-1">Log Aktivitas' + logCountText + '</span>';
                if (st.currentSection === 2) html += '<i class="bi bi-check-lg fw-bold text-primary"></i>';
                html += '</button>';

                html += '<div class="dropdown-divider my-1" style="border-color: var(--border-color);"></div>';

                // Menu Item: Buka di Telegram
                html += '<button class="dropdown-item d-flex align-items-center py-2 px-3 tg-ai-dropdown-item" style="color: var(--text-main); background: transparent;" onclick="openTelegramLink()">';
                html += '<i class="bi bi-box-arrow-up-right me-2 fs-5" style="color: #0288D1;"></i>';
                html += '<span>Buka di Telegram</span>';
                html += '</button>';

                // Menu Item: Start / Stop Bot
                if (st.isRunning) {
                    html += '<button class="dropdown-item d-flex align-items-center py-2 px-3 text-danger tg-ai-dropdown-item" style="background: transparent;" onclick="toggleTelegramBotService(false)">';
                    html += '<i class="bi bi-stop-circle-fill me-2 fs-5 text-danger"></i>';
                    html += '<span>Hentikan Bot (Stop)</span>';
                    html += '</button>';
                } else {
                    html += '<button class="dropdown-item d-flex align-items-center py-2 px-3 text-success tg-ai-dropdown-item" style="background: transparent;" onclick="toggleTelegramBotService(true)">';
                    html += '<i class="bi bi-play-circle-fill me-2 fs-5 text-success"></i>';
                    html += '<span>Aktifkan Bot</span>';
                    html += '</button>';
                }

                html += '</div>';
            }
            html += '</div>'; // End dropdown
            html += '</div>'; // End top bar

            // --- 2. KONTEN UTAMA SESUAI SECTION ---
            html += '<div class="flex-grow-1 position-relative d-flex flex-column overflow-hidden" style="min-height: 0;">';
            if (st.currentSection === 0) {
                html += renderInAppChatSection();
            } else if (st.currentSection === 1) {
                html += renderSettingsSection();
            } else if (st.currentSection === 2) {
                html += renderActivityLogSection();
            }
            html += '</div>';

            html += '</div>'; // End root wrapper

            container.innerHTML = html;

            // Scroll otomatis ke bawah jika di tab chat
            if (st.currentSection === 0) {
                scrollTelegramChatToBottom();
            }
        }

        function updateTelegramAiHeaderStatus() {
            const box = document.getElementById('tg-header-status-box');
            if (!box || window.telegramAiState.currentSection !== 0) return;
            const st = window.telegramAiState;
            let inner = '';
            if (window.telegramAiCameFromProfile) {
                inner += '<button type="button" class="btn btn-sm rounded-circle me-2 d-flex align-items-center justify-content-center tg-ai-circle-btn shadow-none" style="width: 36px; height: 36px; background: var(--bg-hover); color: var(--text-main); border: 1px solid var(--border-color);" onclick="returnToProfileFromTelegramAi()" title="Kembali ke Profil">';
                inner += '<i class="bi bi-arrow-left fs-5"></i>';
                inner += '</button>';
            }
            inner += '<span class="rounded-circle me-2 d-inline-block" style="width: 10px; height: 10px; background-color: ' + (st.isRunning ? '#10B981' : '#94A3B8') + ';"></span>';
            inner += '<span class="fw-bold fs-6 me-2 tg-ai-header-title" style="color: var(--text-main);">Uji Coba AI Assistant</span>';
            if (st.isRunning) {
                inner += '<span class="badge rounded-pill fw-semibold px-2 py-1" style="background-color: rgba(16, 185, 129, 0.2); color: #10B981; border: 1px solid rgba(16, 185, 129, 0.35); font-size: 11px;">Aktif</span>';
            }
            box.innerHTML = inner;
        }

        window.returnToProfileFromTelegramAi = function() {
            window.telegramAiCameFromProfile = false;
            if (typeof switchTab === 'function') {
                switchTab('profil');
            } else if (typeof window.loadProfile === 'function') {
                window.loadProfile();
            }
        };

        function toggleTelegramAiMenu() {
            window.telegramAiState.showMoreMenu = !window.telegramAiState.showMoreMenu;
            renderTelegramAiView();
        }

        function setTelegramAiSection(sec) {
            window.telegramAiState.currentSection = sec;
            window.telegramAiState.showMoreMenu = false;
            renderTelegramAiView();
        }

        function openTelegramLink() {
            window.telegramAiState.showMoreMenu = false;
            renderTelegramAiView();
            const username = window.telegramAiState.botUsername || 'My_Singghara_bot';
            window.open('https://t.me/' + username, '_blank');
        }

        // =========================================================================
        // --- SECTION 0: UJI COBA AI ASSISTANT (CHAT DENGAN FIXED/STICKY BOTTOM FORM) ---
        // =========================================================================
        function renderInAppChatSection() {
            const st = window.telegramAiState;
            let html = '';

            // Container chat penuh: chat messages scroll di atas, input bar fixed/sticky di bawah
            html += '<div class="d-flex flex-column h-100 position-relative overflow-hidden w-100" style="max-width: 960px; margin: 0 auto;">';

            // AREA 1: PESAN CHAT (MENGGULIR SECARA INDEPENDEN DI ATAS FORM TULIS PESAN)
            html += '<div class="flex-grow-1 overflow-auto px-3 pt-3 pb-2 d-flex flex-column tg-ai-messages-scroll" id="tg-chat-messages-container" style="gap: 12px; min-height: 0; scroll-behavior: smooth;">';
            st.chatMessages.forEach((msg, idx) => {
                html += renderChatBubble(msg.isUser, msg.text, idx);
            });

            // Loading AI Indicator
            if (st.isSendingInApp) {
                html += '<div class="d-flex align-items-center ps-2 pt-1 mb-1" style="gap: 8px;">';
                html += '<div class="spinner-border spinner-border-sm text-primary" style="width: 16px; height: 16px; color: #0288D1 !important;" role="status"></div>';
                html += '<span style="font-size: 11.5px; color: var(--text-sub);">AI membaca database My Singghara...</span>';
                html += '</div>';
            }
            html += '</div>'; // End Area 1 (Pesan Chat)

            // AREA 2: FIXED / STICKY BOTTOM INPUT BAR (SELALU DIAM DI BAWAH)
            html += '<div class="tg-ai-bottom-bar px-3 pt-2 pb-3 flex-shrink-0" id="tg-ai-bottom-bar" style="position: sticky; bottom: 0; left: 0; right: 0; z-index: 50; background: var(--card-bg); border-top: 1px solid var(--border-color); box-shadow: 0 -4px 20px rgba(0,0,0,0.08);">';
            
            // 2.a. Saran Pertanyaan Cepat (Chips): Tepat di atas form, scroll horizontal
            const chipsVisible = !st.inputQuery.trim();
            html += '<div id="tg-quick-chips-wrapper" class="overflow-x-auto pb-2" style="white-space: nowrap; transition: all 0.25s ease; ' + (chipsVisible ? 'display: block;' : 'display: none;') + '">';
            html += '<div class="d-inline-flex" style="gap: 8px;">';
            window.telegramAiQuickQuestions.forEach(q => {
                html += '<button type="button" class="btn btn-sm rounded-pill text-nowrap py-1.5 px-3 d-inline-flex align-items-center tg-ai-chip" style="font-size: 11.5px; background: var(--bg-hover); border: 1px solid var(--border-color); color: var(--text-main); transition: all 0.2s ease;" onclick="sendQuickQuestion(' + escapeJsString(q) + ')">';
                html += escapeTelegramHtml(q);
                html += '</button>';
            });
            html += '</div>';
            html += '</div>';

            // 2.b. Form Input Pesan (Pill Shaped Outlined Field + Send Button)
            html += '<div class="d-flex align-items-center" style="gap: 8px;">';
            html += '<div class="position-relative flex-grow-1">';
            html += '<input type="text" id="tg-chat-input" class="form-control rounded-pill px-3 py-2 tg-ai-input" style="font-size: 13.5px; background: var(--input-bg); color: var(--text-main); border: 1.5px solid var(--border-color); box-shadow: 0 2px 6px rgba(0,0,0,0.03);" placeholder="Tulis pesan perintah atau tanya AI Singghara..." value="' + escapeTelegramHtml(st.inputQuery) + '" oninput="handleTelegramChatInputChange(this.value)" onkeydown="handleTelegramChatKeyDown(event)" ' + (st.isSendingInApp ? 'disabled' : '') + ' autofocus autocomplete="off">';
            html += '</div>';

            // Tombol Kirim (#229ED9 Telegram Blue)
            const sendDisabled = (!st.inputQuery.trim() || st.isSendingInApp);
            html += '<button type="button" class="btn rounded-circle d-flex align-items-center justify-content-center shadow-sm flex-shrink-0" style="width: 44px; height: 44px; min-width: 44px; background: linear-gradient(135deg, #0288D1, #229ED9); color: #ffffff; border: none; opacity: ' + (sendDisabled ? '0.5' : '1') + '; transition: transform 0.15s ease;" onclick="submitTelegramChatMessage()" ' + (sendDisabled ? 'disabled' : '') + ' title="Kirim Pertanyaan">';
            html += '<i class="bi bi-send-fill fs-6" style="margin-left: 2px;"></i>';
            html += '</button>';
            html += '</div>'; // End Form Input

            html += '</div>'; // End Area 2 (Fixed Bottom Bar)

            html += '</div>'; // End Container Chat
            return html;
        }

        // Render Bubble Chat persis ChatBubble di TelegramAiScreen.kt (Mode Terang & Gelap)
        function renderChatBubble(isUser, text, idx) {
            let html = '';
            const alignment = isUser ? 'align-items-end text-end' : 'align-items-start text-start';
            const bgColor = isUser ? 'linear-gradient(135deg, #0288D1, #229ED9)' : 'var(--card-bg)';
            const textColor = isUser ? '#ffffff' : 'var(--text-main)';
            const borderStyle = isUser ? 'border: none; box-shadow: 0 2px 8px rgba(34, 158, 217, 0.25);' : 'border: 1px solid var(--border-color); box-shadow: var(--card-shadow);';
            const borderRadius = isUser ? '16px 4px 16px 16px' : '4px 16px 16px 16px';
            const labelText = isUser ? 'Anda' : 'My Singghara AI';

            html += '<div class="d-flex flex-column ' + alignment + ' w-100">';
            html += '<div class="user-select-text text-start tg-ai-bubble" style="max-width: 650px; background: ' + bgColor + '; color: ' + textColor + '; ' + borderStyle + ' border-radius: ' + borderRadius + '; padding: 10px 14px; font-size: 13.5px; line-height: 1.5; cursor: pointer; word-break: break-word; white-space: pre-wrap;" onclick="copyTelegramBubbleText(' + idx + ')" title="Klik untuk menyalin teks">';
            html += escapeTelegramHtml(text);
            html += '</div>';
            html += '<span class="px-1 pt-1" style="font-size: 10.5px; color: var(--text-sub); opacity: 0.85;">' + labelText + '</span>';
            html += '</div>';

            return html;
        }

        function handleTelegramChatInputChange(val) {
            window.telegramAiState.inputQuery = val;
            const chips = document.getElementById('tg-quick-chips-wrapper');
            if (chips) {
                if (val.trim()) {
                    chips.style.display = 'none';
                } else {
                    chips.style.display = 'block';
                }
            }
        }

        function handleTelegramChatKeyDown(e) {
            if (e.key === 'Enter' && !e.shiftKey) {
                e.preventDefault();
                submitTelegramChatMessage();
            }
        }

        function sendQuickQuestion(q) {
            window.telegramAiState.inputQuery = q;
            submitTelegramChatMessage();
        }

        function submitTelegramChatMessage() {
            const st = window.telegramAiState;
            const query = st.inputQuery.trim();
            if (!query || st.isSendingInApp) return;

            // Tambahkan pesan pengguna ke chat
            st.chatMessages.push({ isUser: true, text: query });
            st.inputQuery = "";
            st.isSendingInApp = true;
            renderTelegramAiView();
            scrollTelegramChatToBottom();

            // Refocus input field
            setTimeout(() => {
                const inp = document.getElementById('tg-chat-input');
                if (inp) inp.focus();
            }, 60);

            // Panggil backend LocalHttpServer /api/telegram_ai/chat (100% OFFLINE)
            fetch('/api/telegram_ai/chat', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    query: query,
                    chatId: st.registeredChatId || '999999',
                    senderName: 'Anda (Web PC)'
                })
            })
            .then(r => r.json())
            .then(data => {
                st.isSendingInApp = false;
                const reply = data.reply || "Maaf, respon tidak dapat dihasilkan.";
                st.chatMessages.push({ isUser: false, text: reply });
                renderTelegramAiView();
                scrollTelegramChatToBottom();
                setTimeout(() => {
                    const inp = document.getElementById('tg-chat-input');
                    if (inp) inp.focus();
                }, 60);
                // Update log di background
                fetchTelegramAiState(true);
            })
            .catch(err => {
                st.isSendingInApp = false;
                st.chatMessages.push({ isUser: false, text: "Gagal memproses pesan: " + err.message });
                renderTelegramAiView();
                scrollTelegramChatToBottom();
            });
        }

        function copyTelegramBubbleText(idx) {
            const msg = window.telegramAiState.chatMessages[idx];
            if (msg && msg.text) {
                copyToClipboard(msg.text);
                showTelegramToast("Teks disalin");
            }
        }

        function scrollTelegramChatToBottom() {
            setTimeout(() => {
                const el = document.getElementById('tg-chat-messages-container');
                if (el) {
                    el.scrollTop = el.scrollHeight;
                }
            }, 60);
        }

        // =========================================================================
        // --- SECTION 1: PENGATURAN & CHAT ID PERSIS TELEGRAMAISCREEN.KT ---
        // =========================================================================
        function renderSettingsSection() {
            const st = window.telegramAiState;
            let html = '';

            html += '<div class="overflow-auto p-3 flex-grow-1" style="max-width: 780px; width: 100%; margin: 0 auto; min-height: 0;">';
            html += '<div class="d-flex flex-column" style="gap: 16px;">';

            // CARD 1: TELEGRAM BOT API TOKEN
            html += '<div class="card rounded-4 shadow-sm tg-ai-card" style="background: var(--card-bg); border: 1px solid var(--border-color) !important; color: var(--text-main);">';
            html += '<div class="card-body p-3 p-md-4">';
            html += '<div class="d-flex align-items-center mb-2">';
            html += '<i class="bi bi-key-fill fs-5 me-2" style="color: #0288D1;"></i>';
            html += '<span class="fw-bold fs-6" style="color: var(--text-main);">Telegram Bot API Token</span>';
            html += '</div>';
            html += '<p class="mb-3" style="font-size: 12px; color: var(--text-sub);">Token bot resmi dari @BotFather untuk berkomunikasi via Telegram Bot API.</p>';

            // Input Token with Visibility Toggle
            html += '<div class="input-group mb-3">';
            const inputType = st.isTokenVisible ? 'text' : 'password';
            html += '<input type="' + inputType + '" id="tg-token-input" class="form-control rounded-start-3" style="font-size: 12.5px; background: var(--input-bg); color: var(--text-main); border: 1px solid var(--border-color);" value="' + escapeTelegramHtml(st.tokenInput) + '" oninput="window.telegramAiState.tokenInput = this.value">';
            html += '<button class="btn" type="button" style="background: var(--bg-hover); color: var(--text-main); border: 1px solid var(--border-color); border-left: none;" onclick="toggleTelegramTokenVisibility()" title="Tampilkan/Sembunyikan Token">';
            html += '<i class="bi ' + (st.isTokenVisible ? 'bi-eye-slash-fill' : 'bi-eye-fill') + '"></i>';
            html += '</button>';
            html += '</div>';

            // Tombol Reset Token & Simpan Token
            html += '<div class="row g-2">';
            html += '<div class="col-6">';
            html += '<button type="button" class="btn w-100 rounded-3 py-2" style="background: var(--bg-hover); color: var(--text-main); border: 1px solid var(--border-color); font-size: 12px; font-weight: 600;" onclick="resetTelegramTokenDefault()">Reset Token Bawaan</button>';
            html += '</div>';
            html += '<div class="col-6">';
            html += '<button type="button" class="btn w-100 rounded-3 py-2 text-white" style="background-color: #0288D1; font-size: 12px; font-weight: 600; border: none;" onclick="saveTelegramToken()">Simpan Token</button>';
            html += '</div>';
            html += '</div>';

            html += '</div>';
            html += '</div>'; // End Card 1

            // CARD 2: TELEGRAM CHAT ID
            html += '<div class="card rounded-4 shadow-sm tg-ai-card" style="background: var(--card-bg); border: 1px solid var(--border-color) !important; color: var(--text-main);">';
            html += '<div class="card-body p-3 p-md-4">';
            html += '<div class="d-flex align-items-center mb-2">';
            html += '<i class="bi bi-person-badge-fill fs-5 me-2" style="color: #0F766E;"></i>';
            html += '<span class="fw-bold fs-6" style="color: var(--text-main);">Telegram ID Obrolan (Chat ID)</span>';
            html += '</div>';
            html += '<p class="mb-3" style="font-size: 12px; color: var(--text-sub);">ID Obrolan unik Anda di Telegram untuk verifikasi dan pengiriman pesan langsung dari database My Singghara.</p>';

            // Input Chat ID with Copy Button
            html += '<div class="input-group mb-3">';
            html += '<input type="text" id="tg-chatid-input" class="form-control rounded-start-3" style="font-size: 13px; background: var(--input-bg); color: var(--text-main); border: 1px solid var(--border-color);" placeholder="Contoh: 123456789 (atau klik deteksi)" value="' + escapeTelegramHtml(st.chatIdInput) + '" oninput="window.telegramAiState.chatIdInput = this.value">';
            if (st.chatIdInput.trim()) {
                html += '<button class="btn" type="button" style="background: var(--bg-hover); color: var(--text-main); border: 1px solid var(--border-color); border-left: none;" onclick="copyTelegramChatId()" title="Salin Chat ID">';
                html += '<i class="bi bi-clipboard"></i>';
                html += '</button>';
            }
            html += '</div>';

            // Tombol Deteksi Otomatis & Simpan Chat ID
            html += '<div class="row g-2 mb-3">';
            html += '<div class="col-6">';
            html += '<button type="button" class="btn w-100 rounded-3 py-2 d-flex align-items-center justify-content-center" style="background: var(--bg-hover); color: var(--text-main); border: 1px solid var(--border-color); font-size: 12px; font-weight: 600;" onclick="autoDetectTelegramChatId()" ' + (st.isDetectingChatId ? 'disabled' : '') + '>';
            if (st.isDetectingChatId) {
                html += '<div class="spinner-border spinner-border-sm me-2" role="status"></div>';
            }
            html += 'Deteksi Otomatis';
            html += '</button>';
            html += '</div>';
            html += '<div class="col-6">';
            html += '<button type="button" class="btn w-100 rounded-3 py-2 text-white" style="background-color: #0F766E; font-size: 12px; font-weight: 600; border: none;" onclick="saveTelegramChatId()">Simpan Chat ID</button>';
            html += '</div>';
            html += '</div>';

            // Tombol Kirim Pesan Tes ke Telegram
            const canTest = (!st.isTestingTelegramMessage && !!st.chatIdInput.trim());
            html += '<button type="button" class="btn w-100 rounded-3 py-2 fw-semibold d-flex align-items-center justify-content-center" style="background-color: rgba(15, 118, 110, 0.18); color: var(--brand-green, #0F766E); border: 1px solid rgba(15, 118, 110, 0.35); font-size: 12.5px; opacity: ' + (canTest ? '1' : '0.6') + ';" onclick="sendTelegramTestMessage()" ' + (canTest ? '' : 'disabled') + '>';
            if (st.isTestingTelegramMessage) {
                html += '<div class="spinner-border spinner-border-sm me-2" role="status"></div>';
            } else {
                html += '<i class="bi bi-send-fill me-2"></i>';
            }
            html += 'Kirim Pesan Tes ke Telegram';
            html += '</button>';

            html += '</div>';
            html += '</div>'; // End Card 2

            // CARD 3: AKSES & HAK IZIN (ALLOW ANY CHAT)
            html += '<div class="card rounded-4 shadow-sm tg-ai-card" style="background: var(--card-bg); border: 1px solid var(--border-color) !important; color: var(--text-main);">';
            html += '<div class="card-body p-3 p-md-4">';
            html += '<div class="d-flex align-items-center justify-content-between">';
            html += '<div class="me-3">';
            html += '<div class="fw-bold mb-1" style="font-size: 14px; color: var(--text-main);">Izinkan Semua Chat ID</div>';
            const allowDesc = st.allowAnyChat 
                ? 'Bot membalas pertanyaan siapa saja yang menghubungi @' + st.botUsername 
                : 'Bot hanya melayani Chat ID yang terdaftar di atas';
            html += '<div style="font-size: 12px; color: var(--text-sub);">' + allowDesc + '</div>';
            html += '</div>';
            html += '<div class="form-check form-switch fs-4 mb-0">';
            html += '<input class="form-check-input" type="checkbox" role="switch" ' + (st.allowAnyChat ? 'checked' : '') + ' onchange="toggleTelegramAllowAnyChat(this.checked)">';
            html += '</div>';
            html += '</div>';
            html += '</div>';
            html += '</div>'; // End Card 3

            html += '</div>';
            html += '</div>';
            return html;
        }

        function toggleTelegramTokenVisibility() {
            window.telegramAiState.isTokenVisible = !window.telegramAiState.isTokenVisible;
            renderTelegramAiView();
        }

        function resetTelegramTokenDefault() {
            const defaultToken = "8670152772:AAHEVL05UN-e7HiD233MME1eTvx-3Lo--0k";
            window.telegramAiState.tokenInput = defaultToken;
            postTelegramAction({ action: 'reset_token' }, (res) => {
                showTelegramToast("Token bot direset ke token bawaan");
                fetchTelegramAiState();
            });
        }

        function saveTelegramToken() {
            const token = window.telegramAiState.tokenInput.trim();
            if (!token) {
                showTelegramToast("Token tidak boleh kosong");
                return;
            }
            postTelegramAction({ action: 'save_token', token: token }, (res) => {
                showTelegramToast("Token bot berhasil diperbarui");
                fetchTelegramAiState();
            });
        }

        function copyTelegramChatId() {
            const id = window.telegramAiState.chatIdInput.trim();
            if (id) {
                copyToClipboard(id);
                showTelegramToast("Chat ID disalin");
            }
        }

        function saveTelegramChatId() {
            const chatId = window.telegramAiState.chatIdInput.trim();
            postTelegramAction({ action: 'save_chat_id', chatId: chatId }, (res) => {
                showTelegramToast("Chat ID disimpan");
                fetchTelegramAiState();
            });
        }

        function autoDetectTelegramChatId() {
            const st = window.telegramAiState;
            st.isDetectingChatId = true;
            renderTelegramAiView();

            postTelegramAction({ action: 'auto_detect_chat_id' }, (res) => {
                st.isDetectingChatId = false;
                if (res.success && res.chatId) {
                    st.chatIdInput = res.chatId;
                }
                showTelegramToast(res.message || "Deteksi selesai");
                fetchTelegramAiState();
            });
        }

        function sendTelegramTestMessage() {
            const st = window.telegramAiState;
            st.isTestingTelegramMessage = true;
            renderTelegramAiView();

            postTelegramAction({ action: 'send_test_message' }, (res) => {
                st.isTestingTelegramMessage = false;
                showTelegramToast(res.message || "Pesan tes terkirim");
                fetchTelegramAiState();
            });
        }

        function toggleTelegramAllowAnyChat(allow) {
            window.telegramAiState.allowAnyChat = allow;
            postTelegramAction({ action: 'toggle_allow_any_chat', allowAnyChat: allow }, (res) => {
                fetchTelegramAiState();
            });
        }

        function toggleTelegramBotService(start) {
            window.telegramAiState.showMoreMenu = false;
            const actionName = start ? 'start' : 'stop';
            postTelegramAction({ action: actionName }, (res) => {
                showTelegramToast(start ? "Layanan Bot diaktifkan" : "Layanan Bot dihentikan");
                fetchTelegramAiState();
            });
        }

        // =========================================================================
        // --- SECTION 2: LOG AKTIVITAS BOT PERSIS TELEGRAMAISCREEN.KT ---
        // =========================================================================
        function renderActivityLogSection() {
            const st = window.telegramAiState;
            let html = '';

            html += '<div class="d-flex flex-column h-100 p-3 overflow-hidden" style="max-width: 850px; width: 100%; margin: 0 auto; min-height: 0;">';

            // Header Log
            html += '<div class="d-flex align-items-center justify-content-between mb-3 flex-shrink-0">';
            html += '<span class="fw-bold fs-6" style="color: var(--text-main);">Log Aktivitas Bot (' + st.logs.length + ')</span>';
            html += '<button type="button" class="btn btn-sm btn-link text-decoration-none text-danger d-flex align-items-center shadow-none" onclick="clearTelegramLogs()">';
            html += '<i class="bi bi-trash3 me-1"></i> Bersihkan Log';
            html += '</button>';
            html += '</div>';

            // Isi Log (Scrollable)
            if (st.logs.length === 0) {
                html += '<div class="flex-grow-1 d-flex flex-column align-items-center justify-content-center text-center p-4">';
                html += '<i class="bi bi-clock-history fs-1 mb-2 opacity-50" style="color: var(--text-sub);"></i>';
                html += '<p class="mb-0" style="font-size: 13.5px; color: var(--text-sub);">Belum ada pesan Telegram masuk atau keluar.</p>';
                html += '</div>';
            } else {
                html += '<div class="flex-grow-1 overflow-auto pe-1 d-flex flex-column" style="gap: 10px; min-height: 0;">';
                st.logs.forEach(log => {
                    html += renderLogItemCard(log);
                });
                html += '</div>';
            }

            html += '</div>';
            return html;
        }

        function renderLogItemCard(log) {
            let badgeColor = '#0288D1';
            let badgeText = 'MASUK';
            let badgeIcon = 'bi-arrow-down-left-circle-fill';

            const type = (log.type || '').toUpperCase();
            if (type === 'OUTGOING' || type === 'BALASAN') {
                badgeColor = '#10B981';
                badgeText = 'BALASAN';
                badgeIcon = 'bi-arrow-up-right-circle-fill';
            } else if (type === 'SYSTEM' || type === 'SISTEM') {
                badgeColor = '#8B5CF6';
                badgeText = 'SISTEM';
                badgeIcon = 'bi-gear-fill';
            }

            const timeStr = formatTelegramLogTime(log.timestamp);

            let html = '';
            html += '<div class="card rounded-3 shadow-none p-3 tg-ai-log-card" style="background: var(--card-bg); border: 1px solid var(--border-color) !important; color: var(--text-main);">';
            html += '<div class="d-flex align-items-center justify-content-between mb-2">';
            
            // Badge Tipe Log
            html += '<span class="badge d-inline-flex align-items-center px-2 py-1" style="background-color: ' + hexToRgba(badgeColor, 0.15) + '; color: ' + badgeColor + '; font-size: 10px; font-weight: bold; border-radius: 6px;">';
            html += '<i class="bi ' + badgeIcon + ' me-1" style="font-size: 11px;"></i> ' + badgeText;
            html += '</span>';

            // Timestamp
            html += '<span style="font-size: 11px; color: var(--text-sub);">' + timeStr + '</span>';
            html += '</div>';

            // Sender Name & Chat ID
            if (log.chatId && log.chatId.trim()) {
                const sName = log.senderName || 'Pengguna';
                html += '<div class="fw-semibold mb-1" style="font-size: 12.5px; color: var(--text-main);">';
                html += escapeTelegramHtml(sName) + ' (Chat ID: ' + escapeTelegramHtml(log.chatId) + ')';
                html += '</div>';
            }

            // Pesan Log
            html += '<div style="font-size: 12px; line-height: 1.45; word-break: break-word; white-space: pre-wrap; color: var(--text-main); opacity: 0.9;">';
            html += escapeTelegramHtml(log.message || '');
            html += '</div>';

            html += '</div>';
            return html;
        }

        function clearTelegramLogs() {
            postTelegramAction({ action: 'clear_logs' }, (res) => {
                window.telegramAiState.logs = [];
                showTelegramToast("Log aktivitas dibersihkan");
                renderTelegramAiView();
            });
        }

        // =========================================================================
        // --- HELPER & API UTILITIES ---
        // =========================================================================
        function postTelegramAction(payload, callback) {
            fetch('/api/telegram_ai/action', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload)
            })
            .then(r => r.json())
            .then(res => {
                if (callback) callback(res);
            })
            .catch(err => {
                showTelegramToast("Gagal: " + err.message);
                if (callback) callback({ success: false, message: err.message });
            });
        }

        function formatTelegramLogTime(ts) {
            if (!ts) return "--:--:--";
            const d = new Date(Number(ts));
            const hh = String(d.getHours()).padStart(2, '0');
            const mm = String(d.getMinutes()).padStart(2, '0');
            const ss = String(d.getSeconds()).padStart(2, '0');
            return hh + ':' + mm + ':' + ss;
        }

        function escapeTelegramHtml(text) {
            if (!text) return '';
            return text.toString()
                .replace(/&/g, "&amp;")
                .replace(/</g, "&lt;")
                .replace(/>/g, "&gt;")
                .replace(/"/g, "&quot;")
                .replace(/'/g, "&#039;");
        }

        function escapeJsString(str) {
            if (!str) return "''";
            return JSON.stringify(str);
        }

        function hexToRgba(hex, alpha) {
            let c;
            if (/^#([A-Fa-f0-9]{3}){1,2}${dol}/.test(hex)) {
                c = hex.substring(1).split('');
                if (c.length === 3) {
                    c = [c[0], c[0], c[1], c[1], c[2], c[2]];
                }
                c = '0x' + c.join('');
                return 'rgba(' + [(c >> 16) & 255, (c >> 8) & 255, c & 255].join(',') + ',' + alpha + ')';
            }
            return hex;
        }

        function showTelegramToast(msg) {
            let toastEl = document.getElementById('telegram-ai-toast');
            if (!toastEl) {
                toastEl = document.createElement('div');
                toastEl.id = 'telegram-ai-toast';
                document.body.appendChild(toastEl);
            }
            toastEl.style.cssText = 'position: fixed; bottom: 30px; left: 50%; transform: translateX(-50%); background: var(--card-bg, #1e293b); color: var(--text-main, #ffffff); border: 1px solid var(--border-color, rgba(255,255,255,0.15)); padding: 10px 22px; border-radius: 24px; font-size: 13px; z-index: 999999; box-shadow: 0 4px 16px rgba(0,0,0,0.3); transition: opacity 0.3s ease; pointer-events: none;';
            toastEl.textContent = msg;
            toastEl.style.opacity = '1';
            setTimeout(() => {
                toastEl.style.opacity = '0';
            }, 2500);
        }
        """
    }
}
