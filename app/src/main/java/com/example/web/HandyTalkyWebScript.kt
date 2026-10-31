package com.example.web

/**
 * Script Web Connect untuk Fitur Handy Talky (HT) Real-Time via Jaringan Lokal.
 * Bekerja 100% OFFLINE tanpa internet di browser PC dengan tampilan dan fungsi
 * yang sama persis seperti HandyTalkyScreen.kt pada aplikasi My Singghara Android.
 */
object HandyTalkyWebScript {

    fun getScript(dol: String): String {
        return """
        // =========================================================================
        // --- HANDY TALKY (HT) REAL-TIME VIA JARINGAN LOKAL (100% OFFLINE PC) ---
        // =========================================================================

        window.htState = {
            currentFreq: 144.200,
            channelId: 2,
            channelCode: "CH-02",
            channelName: "Koordinasi Singghara",
            band: "VHF",
            callsign: "Echo-Singghara",
            volume: 1.0,
            micGain: 2.5,
            speakerGain: 2.2,
            squelchLevel: 1,
            rogerBeep: true,
            squelchTail: true,
            handsfree: false,
            roipGateway: false,
            isTransmitting: false,
            isReceiving: false,
            rxCallsign: "",
            txAudioLevel: 0.0,
            rxAudioLevel: 0.0,
            localIp: "127.0.0.1",
            isHotspot: false,
            discoveredDevices: [],
            logs: []
        };

        window.htChannelPresets = [
            // VHF Channels (136.000 - 174.000 MHz) - Sangat cocok untuk area terbuka & outdoor
            { id: 1, code: "CH-01", name: "Panggilan Umum", freq: 144.100, band: "VHF", desc: "Kanal panggilan umum VHF lokal", isEmergency: false },
            { id: 2, code: "CH-02", name: "Koordinasi Singghara", freq: 144.200, band: "VHF", desc: "Kanal utama koordinasi harian", isEmergency: false },
            { id: 3, code: "CH-03", name: "Operasional & Logistik", freq: 144.300, band: "VHF", desc: "Koordinasi kerja & pengantaran", isEmergency: false },
            { id: 4, code: "CH-04", name: "Darurat & Emergency", freq: 145.000, band: "VHF", desc: "Prioritas pertolongan & situasi darurat", isEmergency: true },
            { id: 5, code: "CH-05", name: "VHF Simplex 1", freq: 146.520, band: "VHF", desc: "Kanal bebas hambatan jarak menengah", isEmergency: false },
            { id: 6, code: "CH-06", name: "VHF Keamanan & Patroli", freq: 155.500, band: "VHF", desc: "Kanal pos satpam & patroli lingkungan", isEmergency: false },
            // UHF Channels (400.000 - 470.000 MHz) - Sangat baik menembus tembok, beton, & gedung bertingkat
            { id: 7, code: "CH-07", name: "Gedung & Kantor Utama", freq: 433.075, band: "UHF", desc: "PMR UHF indoor gedung perkantoran", isEmergency: false },
            { id: 8, code: "CH-08", name: "Produksi & Gudang", freq: 433.200, band: "UHF", desc: "Area gudang logistik tertutup", isEmergency: false },
            { id: 9, code: "CH-09", name: "UHF Simplex Kantor", freq: 434.500, band: "UHF", desc: "Kanal kerja tim dalam ruangan", isEmergency: false },
            { id: 10, code: "CH-10", name: "PMR Standar 1", freq: 446.006, band: "UHF", desc: "Walkie talkie standar band eropa", isEmergency: false },
            { id: 11, code: "CH-11", name: "Tim Lapangan & Toko", freq: 462.562, band: "UHF", desc: "FRS channel 1 area komersil", isEmergency: false },
            { id: 12, code: "CH-12", name: "Lantai 2 & Rooftop", freq: 467.562, band: "UHF", desc: "FRS channel 2 area vertikal gedung", isEmergency: false }
        ];

        window.htFilterBand = 'SEMUA';
        window.htKeypadBuffer = '144.200';
        window.htSyncTimer = null;
        window.htAudioRxTimer = null;
        window.htAudioContext = null;
        window.htMediaStream = null;
        window.htAudioProcessor = null;
        window.htIsSpacePressed = false;
        window.htAccumulatedPcm = [];
        window.htWasReceiving = false;

        // Inisialisasi Tab Handy Talky
        window.loadHandyTalky = function() {
            const container = document.getElementById('tab-content') || document.getElementById('main-content') || document.getElementById('mainContentContainer') || document.getElementById('tabContent');
            if (!container) return;

            // Render container utama HT persis HandyTalkyScreen.kt
            container.innerHTML = getHandyTalkyHtml();

            // Pasang event listener tombol PTT dan hotkey
            attachHtEventListeners();

            // Fetch state pertama kali dan mulai sinkronisasi live
            fetchHtState();
            startHtSync();
            startHtRxAudioPolling();
        };

        function startHtSync() {
            if (window.htSyncTimer) clearInterval(window.htSyncTimer);
            window.htSyncTimer = setInterval(() => {
                if (currentTab === 'handy_talky' || currentTab === 'ht') {
                    fetchHtState();
                } else {
                    stopHtSync();
                }
            }, 500);
        }

        function stopHtSync() {
            if (window.htSyncTimer) {
                clearInterval(window.htSyncTimer);
                window.htSyncTimer = null;
            }
            if (window.htAudioRxTimer) {
                clearInterval(window.htAudioRxTimer);
                window.htAudioRxTimer = null;
            }
        }

        function startHtRxAudioPolling() {
            if (window.htAudioRxTimer) clearInterval(window.htAudioRxTimer);
            window.htAudioRxTimer = setInterval(() => {
                if (currentTab === 'handy_talky' || currentTab === 'ht') {
                    pollHtRxAudio();
                }
            }, 250);
        }

        async function fetchHtState() {
            try {
                const res = await fetch('/api/ht/state', { cache: 'no-store' });
                if (res.ok) {
                    const data = await res.json();
                    updateHtUi(data);
                }
            } catch (e) {
                // Offline fallback
            }
        }

        async function pollHtRxAudio() {
            if (window.htState.isTransmitting) return; // jangan putar suara sendiri saat transmit
            try {
                const res = await fetch('/api/ht/rx_audio', { cache: 'no-store' });
                if (res.ok) {
                    const data = await res.json();
                    if (data.hasAudio && data.pcmBase64) {
                        playReceivedPcmBase64(data.pcmBase64);
                    }
                }
            } catch (_) {}
        }

        function updateHtUi(data) {
            if (!data) return;
            const prevTransmitting = window.htState.isTransmitting;
            const prevReceiving = window.htState.isReceiving;

            Object.assign(window.htState, data);

            // Update Callsign
            const callsignElem = document.getElementById('htCallsignText');
            if (callsignElem) callsignElem.textContent = window.htState.callsign;

            // Update Frequency Display
            const freqElem = document.getElementById('htFrequencyReadout');
            if (freqElem) {
                freqElem.textContent = Number(window.htState.currentFreq).toFixed(3);
                if (window.htState.isTransmitting) {
                    freqElem.style.color = '#EF4444';
                    freqElem.style.textShadow = '0 0 14px rgba(239, 68, 68, 0.5)';
                } else if (window.htState.isReceiving) {
                    freqElem.style.color = '#10B981';
                    freqElem.style.textShadow = '0 0 14px rgba(16, 185, 129, 0.5)';
                } else {
                    freqElem.style.color = '#38BDF8';
                    freqElem.style.textShadow = '0 0 14px rgba(56, 189, 248, 0.4)';
                }
            }

            // Update Channel Name & Band Chip
            const chNameElem = document.getElementById('htChannelName');
            if (chNameElem) {
                chNameElem.textContent = (window.htState.channelCode || 'CH-02') + ' • ' + (window.htState.channelName || 'Kanal HT');
            }
            const bandElem = document.getElementById('htBandLabel');
            if (bandElem) {
                const b = window.htState.band || 'VHF';
                bandElem.textContent = b;
                bandElem.className = 'badge px-2 py-1 ' + (b === 'VHF' ? 'bg-info bg-opacity-25 text-info border border-info border-opacity-50' : (b === 'UHF' ? 'bg-warning bg-opacity-25 text-warning border border-warning border-opacity-50' : 'bg-primary bg-opacity-25 text-primary border border-primary border-opacity-50'));
            }

            // Update LEDs
            const txLed = document.getElementById('htLedTx');
            const rxLed = document.getElementById('htLedRx');
            const stbyLed = document.getElementById('htLedStby');
            if (txLed && rxLed && stbyLed) {
                if (window.htState.isTransmitting) {
                    txLed.classList.add('ht-led-active-tx');
                    rxLed.classList.remove('ht-led-active-rx');
                    stbyLed.classList.remove('ht-led-active-stby');
                } else if (window.htState.isReceiving) {
                    txLed.classList.remove('ht-led-active-tx');
                    rxLed.classList.add('ht-led-active-rx');
                    stbyLed.classList.remove('ht-led-active-stby');
                } else {
                    txLed.classList.remove('ht-led-active-tx');
                    rxLed.classList.remove('ht-led-active-rx');
                    stbyLed.classList.add('ht-led-active-stby');
                }
            }

            // Update Live Transmission Text
            const liveStatusElem = document.getElementById('htLiveStatusText');
            const consoleCard = document.getElementById('htTacticalConsole');
            const lcdScreen = document.getElementById('htLcdScreen');
            if (liveStatusElem) {
                if (window.htState.isTransmitting) {
                    liveStatusElem.innerHTML = '<span class="text-danger fw-bold ht-blink"><i class="bi bi-broadcast me-1"></i> 🔴 MENTRANSMISIKAN SUARA ANDA...</span>';
                    if (consoleCard) consoleCard.style.borderColor = '#EF4444';
                    if (lcdScreen) lcdScreen.style.borderColor = '#EF4444';
                } else if (window.htState.isReceiving) {
                    const fromCall = window.htState.rxCallsign || 'Rekan Radio';
                    liveStatusElem.innerHTML = '<span class="text-success fw-bold"><i class="bi bi-volume-up-fill me-1"></i> 🔊 MENERIMA DARI: ' + escapeHtml(fromCall) + '</span>';
                    if (consoleCard) consoleCard.style.borderColor = '#10B981';
                    if (lcdScreen) lcdScreen.style.borderColor = '#10B981';
                } else {
                    liveStatusElem.innerHTML = '<span class="text-secondary small fw-semibold">STANDBY • MONITORING FREKUENSI</span>';
                    if (consoleCard) consoleCard.style.borderColor = '';
                    if (lcdScreen) lcdScreen.style.borderColor = '';
                }
            }

            // Squelch tail / roger beep sound cue saat status RX berakhir
            if (prevReceiving && !window.htState.isReceiving && window.htState.squelchTail) {
                playLocalSquelchTailSound();
            }

            // Update Audio VU Meter
            const activeLevel = window.htState.isTransmitting ? window.htState.txAudioLevel : (window.htState.isReceiving ? window.htState.rxAudioLevel : 0.0);
            updateHtVuMeter(activeLevel, window.htState.isTransmitting, window.htState.isReceiving);

            // Update Console Bottom Status line
            const statusLine = document.getElementById('htStatusLine');
            if (statusLine) {
                const isHot = window.htState.isHotspot;
                statusLine.innerHTML = '<span>SQL:' + window.htState.squelchLevel + ' • SPK:' + Number(window.htState.speakerGain).toFixed(1) + 'x • MIC:' + Number(window.htState.micGain).toFixed(1) + 'x • BEEP:' + (window.htState.rogerBeep ? 'ON' : 'OFF') + '</span>' +
                    '<span class="' + (isHot ? 'text-warning' : 'text-teal') + '">' + (isHot ? 'HOTSPOT: ' : 'LAN: ') + escapeHtml(window.htState.localIp) + '</span>';
            }

            // Update Radar Badge Count
            const radarBadge = document.getElementById('htRadarBadge');
            if (radarBadge) {
                const count = (window.htState.discoveredDevices || []).length;
                radarBadge.textContent = count > 0 ? count : '';
                radarBadge.style.display = count > 0 ? 'inline-block' : 'none';
            }

            // Update RoIP Shortcut Icon & Banner
            const roipBanner = document.getElementById('htRoipBanner');
            const roipShortcutIcon = document.getElementById('htRoipShortcutIcon');
            if (roipBanner) {
                roipBanner.style.display = window.htState.roipGateway ? 'block' : 'none';
            }
            if (roipShortcutIcon) {
                roipShortcutIcon.className = window.htState.roipGateway ? 'bi bi-broadcast fs-5 text-success' : 'bi bi-broadcast fs-5 text-warning';
            }

            // Update PTT Button Visual
            const pttBtn = document.getElementById('htBigPttButton');
            const pttText = document.getElementById('htPttButtonText');
            if (pttBtn && pttText) {
                if (window.htState.isTransmitting) {
                    pttBtn.className = 'ht-big-ptt ht-ptt-transmitting';
                    pttText.innerHTML = '<span class="text-danger fw-bold"><i class="bi bi-mic-fill me-1"></i> TRANSMIT BERLANGSUNG</span>';
                } else if (window.htState.isReceiving) {
                    pttBtn.className = 'ht-big-ptt ht-ptt-receiving';
                    pttText.innerHTML = '<span class="text-success fw-bold"><i class="bi bi-volume-up-fill me-1"></i> MENERIMA SUARA...</span>';
                } else {
                    pttBtn.className = 'ht-big-ptt ht-ptt-idle';
                    pttText.innerHTML = '<span class="fw-bold"><i class="bi bi-mic me-1"></i> TEKAN & TAHAN UNTUK BICARA</span>';
                }
            }

            // Update Channel Cards Active State
            const currentFreqNum = Number(window.htState.currentFreq);
            document.querySelectorAll('.ht-channel-card').forEach(card => {
                const cardFreq = Number(card.getAttribute('data-freq'));
                if (Math.abs(cardFreq - currentFreqNum) < 0.005) {
                    card.classList.add('ht-card-active');
                } else {
                    card.classList.remove('ht-card-active');
                }
            });

            // Update VOX badge
            const voxChip = document.getElementById('htVoxToggleChip');
            if (voxChip) {
                if (window.htState.handsfree) {
                    voxChip.className = 'btn btn-sm btn-primary py-0 px-2 fw-bold';
                    voxChip.textContent = 'VOX/LOCK ON';
                } else {
                    voxChip.className = 'btn btn-sm btn-outline-secondary py-0 px-2 fw-semibold';
                    voxChip.textContent = 'VOX OFF';
                }
            }
        }

        function updateHtVuMeter(level, isTx, isRx) {
            level = Math.max(0, Math.min(1, Number(level) || 0));
            const bar = document.getElementById('htVuProgressBar');
            const numText = document.getElementById('htVuDbText');
            if (bar) {
                const percent = Math.round(level * 100);
                bar.style.width = percent + '%';
                if (isTx) {
                    bar.style.backgroundColor = percent > 75 ? '#EF4444' : (percent > 40 ? '#F59E0B' : '#38BDF8');
                } else if (isRx) {
                    bar.style.backgroundColor = percent > 75 ? '#10B981' : '#34D399';
                } else {
                    bar.style.backgroundColor = '#64748B';
                }
            }
            if (numText) {
                if (level > 0.02) {
                    const db = Math.round((level * 60) - 50);
                    numText.textContent = (db > 0 ? '+' : '') + db + ' dB';
                } else {
                    numText.textContent = 'S-0';
                }
            }
        }

        // ==========================================
        // HTML GENERATOR PERSIS HANDYTALKYSCREEN.KT
        // ==========================================
        function getHandyTalkyHtml() {
            return '<div class="container-fluid py-3 px-2 px-md-4 ht-container" style="max-width: 900px; margin: 0 auto;">' +
                
                '<!-- BANNER MODE GATEWAY HT FISIK JIKA AKTIF -->' +
                '<div id="htRoipBanner" class="alert alert-success border-success border-opacity-50 py-2 px-3 mb-2 rounded-3 shadow-sm" style="display: none;">' +
                    '<div class="d-flex align-items-center justify-content-between">' +
                        '<div class="d-flex align-items-center gap-2">' +
                            '<i class="bi bi-broadcast text-success fs-5"></i>' +
                            '<div>' +
                                '<span class="fw-bold small text-success">MODE GATEWAY HT FISIK AKTIF (RoIP Bridge)</span>' +
                                '<div class="text-secondary" style="font-size: 11px;">Suara diteruskan langsung ke kabel interface / radio fisik.</div>' +
                            '</div>' +
                        '</div>' +
                        '<button class="btn btn-sm btn-outline-success py-0 px-2 small" onclick="openPhysicalHtModal()">Info</button>' +
                    '</div>' +
                '</div>' +

                '<!-- KONSOL RADIO TRANSCEIVER (OLED / LCD MODE) -->' +
                '<div class="card shadow-sm border-2 rounded-4 mb-3 ht-tactical-console" id="htTacticalConsole">' +
                    '<div class="card-body p-3">' +
                        
                        '<!-- Header Baris Atas: Callsign & LED Status -->' +
                        '<div class="d-flex align-items-center justify-content-between mb-2">' +
                            '<div class="d-flex align-items-center gap-2 role-button p-1 rounded-2 ht-hover-btn" onclick="openCallsignModal()" title="Ubah Nama Panggilan Radio (Callsign)">' +
                                '<i class="bi bi-person-badge text-info fs-6"></i>' +
                                '<span class="fw-bold text-info" id="htCallsignText">Echo-Singghara</span>' +
                                '<i class="bi bi-pencil-fill text-info opacity-50" style="font-size: 10px;"></i>' +
                            '</div>' +

                            '<div class="d-flex align-items-center gap-2">' +
                                '<div class="ht-led-item"><span class="ht-led-bulb" id="htLedTx"></span><span class="ht-led-text">TX</span></div>' +
                                '<div class="ht-led-item"><span class="ht-led-bulb" id="htLedRx"></span><span class="ht-led-text">RX</span></div>' +
                                '<div class="ht-led-item"><span class="ht-led-bulb ht-led-active-stby" id="htLedStby"></span><span class="ht-led-text">STBY</span></div>' +
                            '</div>' +
                        '</div>' +

                        '<!-- OLED MATRIX / LCD DISPLAY SCREEN -->' +
                        '<div class="p-3 rounded-3 ht-lcd-screen mb-2 border" id="htLcdScreen">' +
                            '<div class="d-flex align-items-center justify-content-between mb-1">' +
                                '<span class="badge bg-info bg-opacity-25 text-info border border-info border-opacity-50" id="htBandLabel">VHF</span>' +
                                '<span class="small fw-semibold text-secondary text-truncate" id="htChannelName" style="max-width: 70%;">CH-02 • Koordinasi Singghara</span>' +
                            '</div>' +

                            '<div class="text-center my-1 role-button ht-freq-click-area" onclick="openNumericKeypadModal()" title="Klik untuk atur Frekuensi dengan Keypad Numerik Bawaan">' +
                                '<div class="d-inline-flex align-items-baseline">' +
                                    '<span class="font-monospace fw-bolder ht-freq-digits" id="htFrequencyReadout">144.200</span>' +
                                    '<span class="ms-2 fw-bold text-secondary fs-6">MHz</span>' +
                                '</div>' +
                            '</div>' +

                            '<div class="text-center my-1" id="htLiveStatusText" style="min-height: 24px;">' +
                                '<span class="text-secondary small fw-semibold">STANDBY • MONITORING FREKUENSI</span>' +
                            '</div>' +

                            '<!-- IKON PINTASAN CEPAT DI BAWAH TITLE STANDBY (Persis HandyTalkyScreen.kt) -->' +
                            '<div class="ht-quick-shortcuts d-flex align-items-center justify-content-around py-1 px-2 my-2 rounded-2">' +
                                '<button class="btn btn-sm btn-link text-secondary ht-shortcut-btn" onclick="switchTab(\'dashboard\', null)" title="Kembali ke Beranda">' +
                                    '<i class="bi bi-arrow-left fs-5"></i>' +
                                '</button>' +
                                '<button class="btn btn-sm btn-link ht-shortcut-btn" onclick="openPhysicalHtModal()" title="Panduan & Mode HT Fisik (RoIP)">' +
                                    '<i class="bi bi-broadcast fs-5 text-warning" id="htRoipShortcutIcon"></i>' +
                                '</button>' +
                                '<button class="btn btn-sm btn-link text-info position-relative ht-shortcut-btn" onclick="openRadarModal()" title="Radar Perangkat Sekitar">' +
                                    '<i class="bi bi-radar fs-5"></i>' +
                                    '<span class="position-absolute top-0 start-100 translate-middle badge rounded-pill bg-success" id="htRadarBadge" style="font-size: 9px; display: none;">0</span>' +
                                '</button>' +
                                '<button class="btn btn-sm btn-link text-secondary ht-shortcut-btn" onclick="openLogsModal()" title="Riwayat Log Transmisi">' +
                                    '<i class="bi bi-clock-history fs-5"></i>' +
                                '</button>' +
                                '<button class="btn btn-sm btn-link text-info ht-shortcut-btn" onclick="openSettingsModal()" title="Pengaturan Audio & Booster">' +
                                    '<i class="bi bi-sliders fs-5"></i>' +
                                '</button>' +
                            '</div>' +

                            '<!-- DUAL VU AUDIO LEVEL BARS -->' +
                            '<div class="ht-vu-meter-container mt-2">' +
                                '<div class="d-flex justify-content-between text-secondary" style="font-size: 9px; font-family: monospace;">' +
                                    '<span>S-1</span><span>S-3</span><span>S-5</span><span>S-7</span><span>S-9</span><span>+20dB</span><span>+40dB</span>' +
                                '</div>' +
                                '<div class="progress ht-vu-progress-track" style="height: 6px;">' +
                                    '<div class="progress-bar ht-vu-bar" id="htVuProgressBar" role="progressbar" style="width: 0%;"></div>' +
                                '</div>' +
                                '<div class="d-flex justify-content-between align-items-center mt-1" style="font-size: 9px; font-family: monospace;">' +
                                    '<span class="text-secondary">LEVEL AUDIO</span>' +
                                    '<span class="fw-bold text-info" id="htVuDbText">S-0</span>' +
                                '</div>' +
                            '</div>' +

                            '<!-- Bottom LCD Status line -->' +
                            '<div class="d-flex justify-content-between align-items-center mt-2 pt-1 border-top border-secondary border-opacity-25" id="htStatusLine" style="font-size: 10px; font-family: monospace;">' +
                                '<span class="text-secondary">SQL:1 • SPK:2.2x • MIC:2.5x • BEEP:ON</span>' +
                                '<span class="text-teal">LAN: 127.0.0.1</span>' +
                            '</div>' +
                        '</div>' +
                    '</div>' +
                '</div>' +

                '<!-- KANAL DAN TUNER FREKUENSI -->' +
                '<div class="card shadow-sm border rounded-4 mb-3">' +
                    '<div class="card-body p-3">' +
                        '<div class="d-flex align-items-center justify-content-between mb-2">' +
                            '<span class="small fw-bold text-secondary text-uppercase" style="letter-spacing: 0.5px;">DAFTAR KANAL & FREKUENSI</span>' +
                            '<div class="btn-group btn-group-sm" role="group">' +
                                '<button type="button" class="btn btn-sm btn-outline-secondary active py-0 px-2" id="htBtnFilterAll" onclick="setHtBandFilter(\'SEMUA\')">SEMUA</button>' +
                                '<button type="button" class="btn btn-sm btn-outline-secondary py-0 px-2" id="htBtnFilterVhf" onclick="setHtBandFilter(\'VHF\')">VHF</button>' +
                                '<button type="button" class="btn btn-sm btn-outline-secondary py-0 px-2" id="htBtnFilterUhf" onclick="setHtBandFilter(\'UHF\')">UHF</button>' +
                            '</div>' +
                        '</div>' +

                        '<!-- Horizontal Scrollable Channel Selector Cards -->' +
                        '<div class="d-flex gap-2 overflow-auto pb-2 pt-1 ht-channel-scroll" id="htChannelCardsContainer">' +
                            generateChannelCardsHtml() +
                        '</div>' +

                        '<!-- Stepper Tuner Buttons (-25 kHz, Kustom Freq, +25 kHz) -->' +
                        '<div class="row g-2 mt-2">' +
                            '<div class="col-4">' +
                                '<button class="btn btn-outline-secondary w-100 py-1 small fw-semibold d-flex align-items-center justify-content-center gap-1" onclick="stepHtFreq(-0.025)">' +
                                    '<i class="bi bi-dash"></i> -25 kHz' +
                                '</button>' +
                            '</div>' +
                            '<div class="col-4">' +
                                '<button class="btn btn-primary bg-opacity-25 text-primary border-primary w-100 py-1 small fw-bold d-flex align-items-center justify-content-center gap-1" onclick="openNumericKeypadModal()">' +
                                    '<i class="bi bi-grid-3x3-gap-fill"></i> Kustom Freq' +
                                '</button>' +
                            '</div>' +
                            '<div class="col-4">' +
                                '<button class="btn btn-outline-secondary w-100 py-1 small fw-semibold d-flex align-items-center justify-content-center gap-1" onclick="stepHtFreq(0.025)">' +
                                    '+25 kHz <i class="bi bi-plus"></i>' +
                                '</button>' +
                            '</div>' +
                        '</div>' +
                    '</div>' +
                '</div>' +

                '<!-- TOMBOL PTT TAKTIS UTAMA -->' +
                '<div class="d-flex flex-column align-items-center justify-content-center my-3">' +
                    '<div class="ht-ptt-wrapper">' +
                        '<button type="button" id="htBigPttButton" class="ht-big-ptt ht-ptt-idle" title="Tekan & tahan untuk bicara (atau tahan tombol Spasi di keyboard PC)">' +
                            '<div class="ht-ptt-inner">' +
                                '<i class="bi bi-mic-fill ht-ptt-icon"></i>' +
                                '<span class="ht-ptt-label">PTT</span>' +
                            '</div>' +
                        '</button>' +
                    '</div>' +
                    '<div class="mt-2 text-center">' +
                        '<div class="small" id="htPttButtonText"><span class="fw-bold"><i class="bi bi-mic me-1"></i> TEKAN & TAHAN UNTUK BICARA</span></div>' +
                        '<div class="text-secondary" style="font-size: 11px;">' +
                            'Bisa gunakan tombol <kbd class="px-2 py-0">Spasi</kbd> keyboard PC atau klik & tahan tombol di atas' +
                        '</div>' +
                    '</div>' +
                '</div>' +

                '<!-- BOTTOM STATUS BAR -->' +
                '<div class="card border shadow-sm rounded-3 mt-2">' +
                    '<div class="card-body py-2 px-3 d-flex align-items-center justify-content-between">' +
                        '<div class="d-flex align-items-center gap-2 role-button text-warning" onclick="openPhysicalHtModal()">' +
                            '<i class="bi bi-broadcast fs-6"></i>' +
                            '<span class="small fw-semibold">Hubung HT Fisik?</span>' +
                        '</div>' +
                        '<div class="d-flex align-items-center gap-2">' +
                            '<button id="htVoxToggleChip" class="btn btn-sm btn-outline-secondary py-0 px-2 fw-semibold" onclick="toggleHtHandsfree()">' +
                                'VOX OFF' +
                            '</button>' +
                        '</div>' +
                    '</div>' +
                '</div>' +
            '</div>' +

            generateKeypadModalHtml() +
            generatePhysicalHtModalHtml() +
            generateRadarModalHtml() +
            generateLogsModalHtml() +
            generateSettingsModalHtml() +
            generateCallsignModalHtml() +
            
            '<style>' +
                '.ht-tactical-console { background: var(--bs-card-bg, #181E24); border-color: rgba(148, 163, 184, 0.25); transition: border-color 0.2s ease, box-shadow 0.2s ease; }' +
                '.ht-lcd-screen { background: #070B0E; border-color: #1E293B !important; box-shadow: inset 0 2px 8px rgba(0,0,0,0.5); transition: border-color 0.2s ease; }' +
                '[data-bs-theme="light"] .ht-lcd-screen { background: #F1F5F9; border-color: #CBD5E1 !important; box-shadow: inset 0 1px 4px rgba(0,0,0,0.1); }' +
                '.ht-freq-digits { font-size: 2.6rem; letter-spacing: 2px; color: #38BDF8; text-shadow: 0 0 14px rgba(56, 189, 248, 0.4); transition: color 0.15s ease; }' +
                '[data-bs-theme="light"] .ht-freq-digits { color: #0284C7; text-shadow: none; }' +
                '.ht-quick-shortcuts { background: rgba(30, 41, 59, 0.7); border: 1px solid rgba(148, 163, 184, 0.15); }' +
                '[data-bs-theme="light"] .ht-quick-shortcuts { background: rgba(226, 232, 240, 0.8); border: 1px solid rgba(203, 213, 225, 0.8); }' +
                '.ht-shortcut-btn { padding: 6px 12px; border-radius: 8px; transition: transform 0.1s, background-color 0.15s; }' +
                '.ht-shortcut-btn:hover { transform: scale(1.12); background: rgba(255, 255, 255, 0.1); }' +
                '.ht-led-item { display: flex; align-items: center; gap: 4px; }' +
                '.ht-led-bulb { width: 9px; height: 9px; border-radius: 50%; background: #334155; display: inline-block; transition: all 0.15s ease; }' +
                '.ht-led-text { font-size: 10px; font-weight: bold; color: #94A3B8; }' +
                '.ht-led-active-tx { background: #EF4444 !important; box-shadow: 0 0 10px #EF4444; }' +
                '.ht-led-active-rx { background: #10B981 !important; box-shadow: 0 0 10px #10B981; }' +
                '.ht-led-active-stby { background: #0284C7 !important; box-shadow: 0 0 10px #0284C7; }' +
                '.ht-vu-progress-track { background: #1E293B; border-radius: 3px; }' +
                '[data-bs-theme="light"] .ht-vu-progress-track { background: #E2E8F0; }' +
                '.ht-vu-bar { transition: width 0.08s ease; }' +
                '.ht-channel-card { min-width: 140px; padding: 10px 12px; border-radius: 10px; border: 1px solid rgba(148, 163, 184, 0.25); cursor: pointer; background: var(--bs-card-bg, #1E293B); transition: all 0.15s ease; user-select: none; }' +
                '.ht-channel-card:hover { border-color: #38BDF8; transform: translateY(-2px); }' +
                '.ht-card-active { border-color: #0284C7 !important; background: rgba(2, 132, 199, 0.18) !important; box-shadow: 0 0 12px rgba(2, 132, 199, 0.3); }' +
                '.ht-big-ptt { width: 145px; height: 145px; border-radius: 50%; border: 5px solid #334155; background: radial-gradient(circle, #1E293B 0%, #0F172A 100%); box-shadow: 0 10px 25px rgba(0, 0, 0, 0.5); cursor: pointer; outline: none; user-select: none; transition: transform 0.1s ease, box-shadow 0.2s ease, border-color 0.2s ease; display: flex; align-items: center; justify-content: center; }' +
                '.ht-big-ptt:active, .ht-ptt-transmitting { transform: scale(0.96); border-color: #EF4444 !important; background: radial-gradient(circle, #DC2626 0%, #991B1B 100%) !important; box-shadow: 0 0 35px rgba(239, 68, 68, 0.7) !important; }' +
                '.ht-ptt-receiving { border-color: #10B981 !important; background: radial-gradient(circle, #059669 0%, #065F46 100%) !important; box-shadow: 0 0 30px rgba(16, 185, 129, 0.6) !important; }' +
                '.ht-ptt-inner { display: flex; flex-direction: column; align-items: center; justify-content: center; color: white; }' +
                '.ht-ptt-icon { font-size: 2.3rem; }' +
                '.ht-ptt-label { font-size: 14px; font-weight: 800; letter-spacing: 1.5px; margin-top: 2px; }' +
                '.ht-keypad-btn { height: 54px; font-size: 1.35rem; font-weight: bold; border-radius: 10px; }' +
                '.ht-blink { animation: htBlinkAnim 1s infinite alternate; }' +
                '@keyframes htBlinkAnim { 0% { opacity: 1; } 100% { opacity: 0.3; } }' +
                '.text-teal { color: #14B8A6 !important; }' +
            '</style>';
        }

        function generateChannelCardsHtml() {
            let html = '';
            window.htChannelPresets.forEach(ch => {
                const isSelected = ch.id === 2;
                const badgeClass = ch.isEmergency ? 'bg-danger' : (ch.band === 'VHF' ? 'bg-info bg-opacity-25 text-info' : 'bg-warning bg-opacity-25 text-warning');
                html += '<div class="ht-channel-card ' + (isSelected ? 'ht-card-active' : '') + '" data-freq="' + ch.freq + '" data-id="' + ch.id + '" onclick="selectHtChannel(' + ch.id + ')">' +
                    '<div class="d-flex align-items-center justify-content-between mb-1">' +
                        '<span class="badge ' + badgeClass + '" style="font-size: 9px;">' + ch.code + '</span>' +
                        '<span class="small font-monospace fw-bold text-secondary">' + Number(ch.freq).toFixed(3) + '</span>' +
                    '</div>' +
                    '<div class="fw-bold small text-truncate" title="' + escapeHtml(ch.name) + '">' + escapeHtml(ch.name) + '</div>' +
                    '<div class="text-secondary text-truncate" style="font-size: 10px;">' + escapeHtml(ch.desc) + '</div>' +
                '</div>';
            });
            return html;
        }

        // ==========================================
        // MODAL 1: TACTICAL NUMERIC KEYPAD BAWAAN
        // ==========================================
        function generateKeypadModalHtml() {
            return '<div class="modal fade" id="htNumericKeypadModal" tabindex="-1" aria-hidden="true">' +
                '<div class="modal-dialog modal-dialog-centered" style="max-width: 360px;">' +
                    '<div class="modal-content rounded-4 border shadow">' +
                        '<div class="modal-header py-2 px-3 border-0">' +
                            '<h6 class="modal-title fw-bold d-flex align-items-center gap-2">' +
                                '<i class="bi bi-grid-3x3-gap text-info"></i> Keypad Frekuensi HT' +
                            '</h6>' +
                            '<button type="button" class="btn-close" data-bs-dismiss="modal"></button>' +
                        '</div>' +
                        '<div class="modal-body p-3">' +
                            '<div class="p-3 rounded-3 text-center mb-3 ht-lcd-screen border">' +
                                '<div class="font-monospace fw-bolder fs-2 text-info" id="htKeypadDisplay">144.200</div>' +
                                '<div class="small fw-semibold text-secondary" id="htKeypadBandInfo">JALUR VHF (136.000 - 174.000 MHz)</div>' +
                            '</div>' +
                            '<div class="row g-2 text-center">' +
                                '<div class="col-4"><button class="btn btn-outline-secondary w-100 ht-keypad-btn" onclick="htKeypadPress(\'1\')">1</button></div>' +
                                '<div class="col-4"><button class="btn btn-outline-secondary w-100 ht-keypad-btn" onclick="htKeypadPress(\'2\')">2</button></div>' +
                                '<div class="col-4"><button class="btn btn-outline-secondary w-100 ht-keypad-btn" onclick="htKeypadPress(\'3\')">3</button></div>' +
                                '<div class="col-4"><button class="btn btn-outline-secondary w-100 ht-keypad-btn" onclick="htKeypadPress(\'4\')">4</button></div>' +
                                '<div class="col-4"><button class="btn btn-outline-secondary w-100 ht-keypad-btn" onclick="htKeypadPress(\'5\')">5</button></div>' +
                                '<div class="col-4"><button class="btn btn-outline-secondary w-100 ht-keypad-btn" onclick="htKeypadPress(\'6\')">6</button></div>' +
                                '<div class="col-4"><button class="btn btn-outline-secondary w-100 ht-keypad-btn" onclick="htKeypadPress(\'7\')">7</button></div>' +
                                '<div class="col-4"><button class="btn btn-outline-secondary w-100 ht-keypad-btn" onclick="htKeypadPress(\'8\')">8</button></div>' +
                                '<div class="col-4"><button class="btn btn-outline-secondary w-100 ht-keypad-btn" onclick="htKeypadPress(\'9\')">9</button></div>' +
                                '<div class="col-4"><button class="btn btn-outline-secondary w-100 ht-keypad-btn" onclick="htKeypadPress(\'.\')">.</button></div>' +
                                '<div class="col-4"><button class="btn btn-outline-secondary w-100 ht-keypad-btn" onclick="htKeypadPress(\'0\')">0</button></div>' +
                                '<div class="col-4"><button class="btn btn-outline-warning w-100 ht-keypad-btn" onclick="htKeypadPress(\'DEL\')"><i class="bi bi-backspace"></i></button></div>' +
                            '</div>' +
                            '<div class="d-flex gap-2 mt-3">' +
                                '<button class="btn btn-outline-secondary w-50" onclick="htKeypadPress(\'CLR\')">Clear</button>' +
                                '<button class="btn btn-primary w-50 fw-bold" onclick="htApplyKeypadFreq()">Terapkan</button>' +
                            '</div>' +
                        '</div>' +
                    '</div>' +
                '</div>' +
            '</div>';
        }

        // ==========================================
        // MODAL 2: PANDUAN & MODE HT FISIK (RoIP)
        // ==========================================
        function generatePhysicalHtModalHtml() {
            return '<div class="modal fade" id="htPhysicalHtModal" tabindex="-1" aria-hidden="true">' +
                '<div class="modal-dialog modal-dialog-centered modal-lg">' +
                    '<div class="modal-content rounded-4 border shadow">' +
                        '<div class="modal-header py-2 px-3 border-0">' +
                            '<h6 class="modal-title fw-bold d-flex align-items-center gap-2 text-warning">' +
                                '<i class="bi bi-broadcast fs-5"></i> Koneksi ke HT Fisik & RoIP Gateway' +
                            '</h6>' +
                            '<button type="button" class="btn-close" data-bs-dismiss="modal"></button>' +
                        '</div>' +
                        '<div class="modal-body p-3">' +
                            '<div class="alert alert-info py-2 px-3 small mb-3">' +
                                '<strong>Apakah PC / HP bisa langsung memancar ke HT Fisik lewat udara?</strong><br>' +
                                'Secara hardware, PC/HP tidak memiliki pemancar radio RF VHF (144 MHz) atau UHF (430 MHz). PC/HP bekerja melalui Wi-Fi/LAN lokal. Namun Anda bisa menghubungkan PC / ponsel ini ke HT fisik menggunakan 3 cara di bawah:' +
                            '</div>' +
                            '<div class="d-flex flex-column gap-3 mb-3">' +
                                '<div class="p-2 border rounded-3">' +
                                    '<div class="fw-bold text-success mb-1">1. Metode RoIP Gateway (Radio over IP)</div>' +
                                    '<div class="small text-secondary">' +
                                        'Gunakan 1 HT fisik (misal Baofeng UV-5R) yang dihubungkan ke HP kedua via kabel audio jack 3.5mm K-Type (mode VOX aktif). HP kedua mengaktifkan "Mode Gateway RoIP". Siapapun yang bicara di aplikasi HP / Web PC akan otomatis dipancarkan oleh HT fisik tersebut ke udara!' +
                                    '</div>' +
                                '</div>' +
                                '<div class="p-2 border rounded-3">' +
                                    '<div class="fw-bold text-info mb-1">2. Kabel Audio Patch (BTECH APRS-K1 / K-Type)</div>' +
                                    '<div class="small text-secondary">' +
                                        'Menghubungkan jack audio PC/HP langsung ke port Mic/Spk HT fisik dengan fitur VOX (Voice Operated Exchange) pada radio fisik.' +
                                    '</div>' +
                                '</div>' +
                                '<div class="p-2 border rounded-3">' +
                                    '<div class="fw-bold text-warning mb-1">3. Handheld Wireless Bluetooth PTT Mic</div>' +
                                    '<div class="small text-secondary">' +
                                        'Menggunakan speaker mic fisik Bluetooth (seperti Dellking / B01 PTT Mic) yang memiliki tombol fisik PTT dan speaker keras layaknya mic HT sungguhan.' +
                                    '</div>' +
                                '</div>' +
                            '</div>' +
                            '<div class="d-flex align-items-center justify-content-between p-3 border rounded-3 bg-body-tertiary">' +
                                '<div>' +
                                    '<div class="fw-bold small">Aktifkan Mode RoIP Gateway di Server</div>' +
                                    '<div class="text-secondary" style="font-size: 11px;">Jadikan HP server sebagai jembatan relay ke HT fisik via kabel audio.</div>' +
                                '</div>' +
                                '<div class="form-check form-switch fs-4 mb-0">' +
                                    '<input class="form-check-input" type="checkbox" id="htRoipSwitch" onchange="toggleHtRoip()">' +
                                '</div>' +
                            '</div>' +
                        '</div>' +
                        '<div class="modal-footer py-2 px-3 border-0">' +
                            '<button type="button" class="btn btn-primary btn-sm px-4 fw-semibold" data-bs-dismiss="modal">Mengerti</button>' +
                        '</div>' +
                    '</div>' +
                '</div>' +
            '</div>';
        }

        // ==========================================
        // MODAL 3: RADAR PERANGKAT SEKITAR
        // ==========================================
        function generateRadarModalHtml() {
            return '<div class="modal fade" id="htRadarModal" tabindex="-1" aria-hidden="true">' +
                '<div class="modal-dialog modal-dialog-centered">' +
                    '<div class="modal-content rounded-4 border shadow">' +
                        '<div class="modal-header py-2 px-3 border-0">' +
                            '<div>' +
                                '<h6 class="modal-title fw-bold d-flex align-items-center gap-2">' +
                                    '<i class="bi bi-radar text-info"></i> Radar HT di Jaringan Lokal' +
                                '</h6>' +
                                '<div class="text-secondary" style="font-size: 11px;" id="htRadarSubtitle">Memindai perangkat aktif...</div>' +
                            '</div>' +
                            '<button type="button" class="btn-close" data-bs-dismiss="modal"></button>' +
                        '</div>' +
                        '<div class="modal-body p-3" id="htRadarListContainer" style="max-height: 400px; overflow-y: auto;">' +
                        '</div>' +
                    '</div>' +
                '</div>' +
            '</div>';
        }

        // ==========================================
        // MODAL 4: RIWAYAT LOG TRANSMISI
        // ==========================================
        function generateLogsModalHtml() {
            return '<div class="modal fade" id="htLogsModal" tabindex="-1" aria-hidden="true">' +
                '<div class="modal-dialog modal-dialog-centered modal-lg">' +
                    '<div class="modal-content rounded-4 border shadow">' +
                        '<div class="modal-header py-2 px-3 border-0 d-flex align-items-center justify-content-between">' +
                            '<h6 class="modal-title fw-bold d-flex align-items-center gap-2">' +
                                '<i class="bi bi-clock-history text-secondary"></i> Riwayat Log Transmisi HT' +
                            '</h6>' +
                            '<div class="d-flex align-items-center gap-2">' +
                                '<button class="btn btn-sm btn-outline-danger py-0 px-2 small" onclick="clearHtLogs()">' +
                                    '<i class="bi bi-trash"></i> Bersihkan Log' +
                                '</button>' +
                                '<button type="button" class="btn-close" data-bs-dismiss="modal"></button>' +
                            '</div>' +
                        '</div>' +
                        '<div class="modal-body p-3" id="htLogsListContainer" style="max-height: 420px; overflow-y: auto;">' +
                        '</div>' +
                    '</div>' +
                '</div>' +
            '</div>';
        }

        // ==========================================
        // MODAL 5: PENGATURAN AUDIO & BOOSTER HT
        // ==========================================
        function generateSettingsModalHtml() {
            return '<div class="modal fade" id="htSettingsModal" tabindex="-1" aria-hidden="true">' +
                '<div class="modal-dialog modal-dialog-centered">' +
                    '<div class="modal-content rounded-4 border shadow">' +
                        '<div class="modal-header py-2 px-3 border-0">' +
                            '<h6 class="modal-title fw-bold d-flex align-items-center gap-2 text-info">' +
                                '<i class="bi bi-sliders"></i> Pengaturan Audio & Booster HT' +
                            '</h6>' +
                            '<button type="button" class="btn-close" data-bs-dismiss="modal"></button>' +
                        '</div>' +
                        '<div class="modal-body p-3 d-flex flex-column gap-3">' +
                            '<div>' +
                                '<div class="d-flex justify-content-between align-items-center mb-1">' +
                                    '<span class="fw-bold small">🔊 Penguat Speaker (Speaker Boost)</span>' +
                                    '<span class="badge bg-success" id="htSpeakerGainBadge">2.2x</span>' +
                                '</div>' +
                                '<div class="text-secondary small mb-1" style="font-size: 11px;">Memperkuat volume suara lawan bicara agar terdengar kencang & lantang di loudspeaker.</div>' +
                                '<input type="range" class="form-range" min="1.0" max="4.0" step="0.5" id="htSliderSpeakerGain" onchange="setHtSpeakerGain(this.value)">' +
                            '</div>' +
                            '<div>' +
                                '<div class="d-flex justify-content-between align-items-center mb-1">' +
                                    '<span class="fw-bold small">🎙️ Kepekaan Mic (Mic Pre-Amp)</span>' +
                                    '<span class="badge bg-info" id="htMicGainBadge">2.5x</span>' +
                                '</div>' +
                                '<div class="text-secondary small mb-1" style="font-size: 11px;">Memperkuat tangkapan suara mic PC Anda saat PTT ditekan.</div>' +
                                '<input type="range" class="form-range" min="1.0" max="5.0" step="0.5" id="htSliderMicGain" onchange="setHtMicGain(this.value)">' +
                            '</div>' +
                            '<div>' +
                                '<button class="btn btn-outline-info w-100 py-1 small fw-bold d-flex align-items-center justify-content-center gap-2" onclick="testHtSound()">' +
                                    '<i class="bi bi-volume-up"></i> Tes Bunyi Loudspeaker (Roger Beep)' +
                                '</button>' +
                            '</div>' +
                            '<hr class="my-1">' +
                            '<div class="d-flex align-items-center justify-content-between">' +
                                '<div>' +
                                    '<div class="fw-bold small">Roger Beep (Nada Akhir)</div>' +
                                    '<div class="text-secondary" style="font-size: 11px;">Bunyi nada "tit" khas radio saat selesai bicara</div>' +
                                '</div>' +
                                '<div class="form-check form-switch fs-5 mb-0">' +
                                    '<input class="form-check-input" type="checkbox" id="htRogerBeepSwitch" onchange="toggleHtRogerBeep()">' +
                                '</div>' +
                            '</div>' +
                            '<div class="d-flex align-items-center justify-content-between">' +
                                '<div>' +
                                    '<div class="fw-bold small">Squelch Tail Click</div>' +
                                    '<div class="text-secondary" style="font-size: 11px;">Efek desis statis realistis penutup transmisi</div>' +
                                '</div>' +
                                '<div class="form-check form-switch fs-5 mb-0">' +
                                    '<input class="form-check-input" type="checkbox" id="htSquelchTailSwitch" onchange="toggleHtSquelchTail()">' +
                                '</div>' +
                            '</div>' +
                            '<div>' +
                                '<div class="d-flex justify-content-between align-items-center mb-1">' +
                                    '<span class="fw-bold small">Squelch Sensitivity (SQL)</span>' +
                                    '<span class="badge bg-secondary" id="htSquelchBadge">Level 1</span>' +
                                '</div>' +
                                '<input type="range" class="form-range" min="0" max="9" step="1" id="htSliderSquelch" onchange="setHtSquelch(this.value)">' +
                            '</div>' +
                        '</div>' +
                        '<div class="modal-footer py-2 px-3 border-0">' +
                            '<button type="button" class="btn btn-secondary btn-sm px-4" data-bs-dismiss="modal">Tutup</button>' +
                        '</div>' +
                    '</div>' +
                '</div>' +
            '</div>';
        }

        // ==========================================
        // MODAL 6: CALLSIGN DIALOG
        // ==========================================
        function generateCallsignModalHtml() {
            return '<div class="modal fade" id="htCallsignModal" tabindex="-1" aria-hidden="true">' +
                '<div class="modal-dialog modal-dialog-centered" style="max-width: 380px;">' +
                    '<div class="modal-content rounded-4 border shadow">' +
                        '<div class="modal-header py-2 px-3 border-0">' +
                            '<h6 class="modal-title fw-bold d-flex align-items-center gap-2">' +
                                '<i class="bi bi-person-badge text-info"></i> Ubah Callsign HT' +
                            '</h6>' +
                            '<button type="button" class="btn-close" data-bs-dismiss="modal"></button>' +
                        '</div>' +
                        '<div class="modal-body p-3">' +
                            '<div class="small text-secondary mb-2">' +
                                'Callsign adalah nama panggilan radio Anda yang akan tampil di HT pengguna lain pada frekuensi ini.' +
                            '</div>' +
                            '<input type="text" class="form-control" id="htCallsignInput" placeholder="Contoh: Echo-01, Komandan, Bravo" maxlength="25">' +
                        '</div>' +
                        '<div class="modal-footer py-2 px-3 border-0">' +
                            '<button type="button" class="btn btn-secondary btn-sm" data-bs-dismiss="modal">Batal</button>' +
                            '<button type="button" class="btn btn-primary btn-sm fw-bold px-3" onclick="saveHtCallsign()">Simpan</button>' +
                        '</div>' +
                    '</div>' +
                '</div>' +
            '</div>';
        }

        // ==========================================
        // LOGIKA KONTROL AKSI & AUDIO TRANSMISI
        // ==========================================

        function attachHtEventListeners() {
            const pttBtn = document.getElementById('htBigPttButton');
            if (pttBtn) {
                pttBtn.addEventListener('mousedown', (e) => {
                    e.preventDefault();
                    startHtPtt();
                });
                pttBtn.addEventListener('mouseup', (e) => {
                    e.preventDefault();
                    stopHtPtt();
                });
                pttBtn.addEventListener('mouseleave', () => {
                    if (window.htState.isTransmitting && !window.htState.handsfree) {
                        stopHtPtt();
                    }
                });

                pttBtn.addEventListener('touchstart', (e) => {
                    e.preventDefault();
                    startHtPtt();
                });
                pttBtn.addEventListener('touchend', (e) => {
                    e.preventDefault();
                    stopHtPtt();
                });
                pttBtn.addEventListener('touchcancel', () => {
                    if (window.htState.isTransmitting && !window.htState.handsfree) {
                        stopHtPtt();
                    }
                });
            }

            window.removeEventListener('keydown', handleHtKeyDown);
            window.removeEventListener('keyup', handleHtKeyUp);
            window.addEventListener('keydown', handleHtKeyDown);
            window.addEventListener('keyup', handleHtKeyUp);

            window.addEventListener('blur', () => {
                if (window.htState.isTransmitting && !window.htState.handsfree) {
                    stopHtPtt();
                }
            });
        }

        function handleHtKeyDown(e) {
            if (currentTab !== 'handy_talky' && currentTab !== 'ht') return;
            const tag = (document.activeElement && document.activeElement.tagName) ? document.activeElement.tagName.toLowerCase() : '';
            if (tag === 'input' || tag === 'textarea' || (document.activeElement && document.activeElement.isContentEditable)) return;

            if (e.code === 'Space' && !window.htIsSpacePressed) {
                e.preventDefault();
                window.htIsSpacePressed = true;
                startHtPtt();
            }
        }

        function handleHtKeyUp(e) {
            if (currentTab !== 'handy_talky' && currentTab !== 'ht') return;
            const tag = (document.activeElement && document.activeElement.tagName) ? document.activeElement.tagName.toLowerCase() : '';
            if (tag === 'input' || tag === 'textarea' || (document.activeElement && document.activeElement.isContentEditable)) return;

            if (e.code === 'Space') {
                e.preventDefault();
                window.htIsSpacePressed = false;
                stopHtPtt();
            }
        }

        function getOrCreateAudioContext() {
            try {
                if (!window.htAudioContext) {
                    const AudioCtx = window.AudioContext || window.webkitAudioContext;
                    if (AudioCtx) window.htAudioContext = new AudioCtx();
                }
                if (window.htAudioContext && window.htAudioContext.state === 'suspended') {
                    window.htAudioContext.resume();
                }
            } catch (_) {}
            return window.htAudioContext;
        }

        function playLocalPttPressSound() {
            try {
                const ctx = getOrCreateAudioContext();
                if (!ctx) return;
                const osc = ctx.createOscillator();
                const gain = ctx.createGain();
                osc.type = 'sine';
                osc.frequency.setValueAtTime(800, ctx.currentTime);
                osc.frequency.exponentialRampToValueAtTime(1200, ctx.currentTime + 0.05);
                gain.gain.setValueAtTime(0.15, ctx.currentTime);
                gain.gain.linearRampToValueAtTime(0.01, ctx.currentTime + 0.06);
                osc.connect(gain);
                gain.connect(ctx.destination);
                osc.start();
                osc.stop(ctx.currentTime + 0.06);
            } catch (_) {}
        }

        function playLocalRogerBeepSound() {
            try {
                const ctx = getOrCreateAudioContext();
                if (!ctx) return;
                const osc = ctx.createOscillator();
                const gain = ctx.createGain();
                osc.type = 'sine';
                osc.frequency.setValueAtTime(1200, ctx.currentTime);
                gain.gain.setValueAtTime(0.2, ctx.currentTime);
                gain.gain.linearRampToValueAtTime(0.01, ctx.currentTime + 0.08);
                osc.connect(gain);
                gain.connect(ctx.destination);
                osc.start();
                osc.stop(ctx.currentTime + 0.08);
            } catch (_) {}
        }

        function playLocalSquelchTailSound() {
            try {
                const ctx = getOrCreateAudioContext();
                if (!ctx) return;
                const bufferSize = Math.floor(ctx.sampleRate * 0.05);
                const buffer = ctx.createBuffer(1, bufferSize, ctx.sampleRate);
                const data = buffer.getChannelData(0);
                for (let i = 0; i < bufferSize; i++) {
                    data[i] = Math.random() * 2 - 1;
                }
                const noise = ctx.createBufferSource();
                noise.buffer = buffer;
                const filter = ctx.createBiquadFilter();
                filter.type = 'bandpass';
                filter.frequency.value = 1500;
                const gain = ctx.createGain();
                gain.gain.setValueAtTime(0.12, ctx.currentTime);
                gain.gain.linearRampToValueAtTime(0.001, ctx.currentTime + 0.05);
                noise.connect(filter);
                filter.connect(gain);
                gain.connect(ctx.destination);
                noise.start();
            } catch (_) {}
        }

        function playReceivedPcmBase64(b64) {
            try {
                const ctx = getOrCreateAudioContext();
                if (!ctx) return;
                const binaryStr = atob(b64);
                const len = binaryStr.length;
                const bytes = new Uint8Array(len);
                for (let i = 0; i < len; i++) {
                    bytes[i] = binaryStr.charCodeAt(i);
                }
                const int16View = new Int16Array(bytes.buffer);
                const sampleCount = int16View.length;
                if (sampleCount === 0) return;

                const audioBuf = ctx.createBuffer(1, sampleCount, 16000);
                const channelData = audioBuf.getChannelData(0);

                let sumSquares = 0;
                for (let i = 0; i < sampleCount; i++) {
                    const sample = int16View[i] / 32768.0;
                    channelData[i] = sample;
                    sumSquares += sample * sample;
                }

                const rms = Math.sqrt(sumSquares / sampleCount);
                const normalized = Math.min(1.0, rms * 3.5);
                window.htState.rxAudioLevel = normalized;
                updateHtVuMeter(normalized, false, true);

                const source = ctx.createBufferSource();
                source.buffer = audioBuf;

                const gainNode = ctx.createGain();
                gainNode.gain.value = Math.max(0.5, Math.min(4.0, window.htState.speakerGain || 2.2));

                source.connect(gainNode);
                gainNode.connect(ctx.destination);
                source.start();
            } catch (_) {}
        }

        async function startHtPtt() {
            if (window.htState.isTransmitting) return;
            if (window.htState.isReceiving) return;

            window.htState.isTransmitting = true;
            playLocalPttPressSound();
            updateHtUi(window.htState);

            fetch('/api/ht/action?action=ptt_start&callsign=' + encodeURIComponent(window.htState.callsign), { method: 'POST' }).catch(() => {});
            initWebAudioCapture();
        }

        function stopHtPtt() {
            if (!window.htState.isTransmitting) return;
            window.htState.isTransmitting = false;

            if (window.htState.rogerBeep) {
                playLocalRogerBeepSound();
            }

            updateHtUi(window.htState);
            stopWebAudioCapture();
            fetch('/api/ht/action?action=ptt_end&callsign=' + encodeURIComponent(window.htState.callsign), { method: 'POST' }).catch(() => {});
        }

        async function initWebAudioCapture() {
            try {
                if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) return;
                window.htMediaStream = await navigator.mediaDevices.getUserMedia({
                    audio: {
                        echoCancellation: true,
                        noiseSuppression: true,
                        autoGainControl: true
                    }
                });
                const ctx = getOrCreateAudioContext();
                if (!ctx) return;

                const source = ctx.createMediaStreamSource(window.htMediaStream);
                const processor = ctx.createScriptProcessor(2048, 1, 1);
                window.htAudioProcessor = processor;
                window.htAccumulatedPcm = [];

                processor.onaudioprocess = function(e) {
                    if (!window.htState.isTransmitting) return;
                    const inputData = e.inputBuffer.getChannelData(0);
                    const inSampleRate = ctx.sampleRate;
                    const targetSampleRate = 16000;
                    const ratio = inSampleRate / targetSampleRate;

                    let sumSquares = 0;
                    for (let i = 0; i < inputData.length; i += ratio) {
                        const sample = inputData[Math.floor(i)];
                        sumSquares += sample * sample;
                        window.htAccumulatedPcm.push(sample);
                    }

                    const rms = Math.sqrt(sumSquares / (inputData.length / ratio));
                    const level = Math.min(1.0, rms * 3.5);
                    window.htState.txAudioLevel = level;
                    updateHtVuMeter(level, true, false);

                    // Kirim audio chunk tiap ~1600 sampel (100ms PCM16)
                    if (window.htAccumulatedPcm.length >= 1600) {
                        const samples = window.htAccumulatedPcm;
                        window.htAccumulatedPcm = [];

                        const pcm16 = new Int16Array(samples.length);
                        const micMultiplier = Math.max(1.0, Math.min(5.0, window.htState.micGain || 2.5));
                        for (let j = 0; j < samples.length; j++) {
                            let s = samples[j] * micMultiplier;
                            s = Math.max(-1.0, Math.min(1.0, s));
                            pcm16[j] = s < 0 ? s * 32768 : s * 32767;
                        }

                        const bytes = new Uint8Array(pcm16.buffer);
                        let binary = '';
                        for (let b = 0; b < bytes.byteLength; b++) {
                            binary += String.fromCharCode(bytes[b]);
                        }
                        const b64 = btoa(binary);

                        fetch('/api/ht/tx_audio', {
                            method: 'POST',
                            headers: { 'Content-Type': 'application/json' },
                            body: JSON.stringify({
                                pcmBase64: b64,
                                callsign: window.htState.callsign
                            })
                        }).catch(() => {});
                    }
                };

                source.connect(processor);
                processor.connect(ctx.destination);
            } catch (err) {
                // Mic permission fallback
            }
        }

        function stopWebAudioCapture() {
            if (window.htAudioProcessor) {
                try { window.htAudioProcessor.disconnect(); } catch (_) {}
                window.htAudioProcessor = null;
            }
            if (window.htMediaStream) {
                try {
                    window.htMediaStream.getTracks().forEach(track => track.stop());
                } catch (_) {}
                window.htMediaStream = null;
            }
            window.htAccumulatedPcm = [];
            window.htState.txAudioLevel = 0;
            updateHtVuMeter(0, false, false);
        }

        window.selectHtChannel = async function(id) {
            const ch = window.htChannelPresets.find(c => c.id === id);
            if (!ch) return;
            window.htState.channelId = ch.id;
            window.htState.channelCode = ch.code;
            window.htState.channelName = ch.name;
            window.htState.currentFreq = ch.freq;
            window.htState.band = ch.band;
            updateHtUi(window.htState);
            await fetch('/api/ht/action?action=select_channel&id=' + id, { method: 'POST' }).catch(() => {});
        };

        window.stepHtFreq = async function(delta) {
            const newFreq = Math.round((Number(window.htState.currentFreq) + delta) * 1000) / 1000;
            if (newFreq < 100.0 || newFreq > 500.0) return;
            window.htState.currentFreq = newFreq;
            updateHtUi(window.htState);
            await fetch('/api/ht/action?action=step_freq&delta=' + delta, { method: 'POST' }).catch(() => {});
        };

        window.setHtBandFilter = function(band) {
            window.htFilterBand = band;
            document.getElementById('htBtnFilterAll').className = 'btn btn-sm btn-outline-secondary py-0 px-2 ' + (band === 'SEMUA' ? 'active' : '');
            document.getElementById('htBtnFilterVhf').className = 'btn btn-sm btn-outline-secondary py-0 px-2 ' + (band === 'VHF' ? 'active' : '');
            document.getElementById('htBtnFilterUhf').className = 'btn btn-sm btn-outline-secondary py-0 px-2 ' + (band === 'UHF' ? 'active' : '');

            document.querySelectorAll('.ht-channel-card').forEach(card => {
                const id = Number(card.getAttribute('data-id'));
                const ch = window.htChannelPresets.find(c => c.id === id);
                if (band === 'SEMUA' || (ch && ch.band === band)) {
                    card.style.display = 'block';
                } else {
                    card.style.display = 'none';
                }
            });
        };

        window.openNumericKeypadModal = function() {
            window.htKeypadBuffer = Number(window.htState.currentFreq).toFixed(3);
            updateKeypadDisplay();
            const modal = new bootstrap.Modal(document.getElementById('htNumericKeypadModal'));
            modal.show();
        };

        window.htKeypadPress = function(val) {
            if (val === 'CLR') {
                window.htKeypadBuffer = '';
            } else if (val === 'DEL') {
                window.htKeypadBuffer = window.htKeypadBuffer.slice(0, -1);
            } else {
                if (window.htKeypadBuffer.length < 8) {
                    if (val === '.' && window.htKeypadBuffer.includes('.')) return;
                    window.htKeypadBuffer += val;
                }
            }
            updateKeypadDisplay();
        };

        function updateKeypadDisplay() {
            const disp = document.getElementById('htKeypadDisplay');
            const info = document.getElementById('htKeypadBandInfo');
            if (disp) disp.textContent = window.htKeypadBuffer || '0.000';

            const num = parseFloat(window.htKeypadBuffer);
            if (info) {
                if (num >= 136.0 && num <= 174.0) {
                    info.textContent = 'JALUR VHF (136.000 - 174.000 MHz)';
                    info.className = 'small fw-semibold text-info';
                } else if (num >= 400.0 && num <= 470.0) {
                    info.textContent = 'JALUR UHF (400.000 - 470.000 MHz)';
                    info.className = 'small fw-semibold text-warning';
                } else if (num >= 100.0 && num <= 500.0) {
                    info.textContent = 'FREKUENSI KUSTOM (100.000 - 500.000 MHz)';
                    info.className = 'small fw-semibold text-secondary';
                } else {
                    info.textContent = 'DILUAR JANGKAUAN (Harus 100.000 - 500.000 MHz)';
                    info.className = 'small fw-semibold text-danger';
                }
            }
        }

        window.htApplyKeypadFreq = async function() {
            const num = parseFloat(window.htKeypadBuffer);
            if (isNaN(num) || num < 100.0 || num > 500.0) {
                alert('Rentang frekuensi valid adalah 100.000 s/d 500.000 MHz');
                return;
            }
            window.htState.currentFreq = num;
            updateHtUi(window.htState);
            bootstrap.Modal.getInstance(document.getElementById('htNumericKeypadModal'))?.hide();
            await fetch('/api/ht/action?action=set_freq&freq=' + num, { method: 'POST' }).catch(() => {});
        };

        window.openCallsignModal = function() {
            const input = document.getElementById('htCallsignInput');
            if (input) input.value = window.htState.callsign;
            new bootstrap.Modal(document.getElementById('htCallsignModal')).show();
        };

        window.saveHtCallsign = async function() {
            const input = document.getElementById('htCallsignInput');
            const val = input ? input.value.trim() : '';
            if (val) {
                window.htState.callsign = val;
                updateHtUi(window.htState);
                bootstrap.Modal.getInstance(document.getElementById('htCallsignModal'))?.hide();
                await fetch('/api/ht/action?action=set_callsign&callsign=' + encodeURIComponent(val), { method: 'POST' }).catch(() => {});
            }
        };

        window.openPhysicalHtModal = function() {
            const sw = document.getElementById('htRoipSwitch');
            if (sw) sw.checked = !!window.htState.roipGateway;
            new bootstrap.Modal(document.getElementById('htPhysicalHtModal')).show();
        };

        window.toggleHtRoip = async function() {
            window.htState.roipGateway = !window.htState.roipGateway;
            updateHtUi(window.htState);
            await fetch('/api/ht/action?action=toggle_roip', { method: 'POST' }).catch(() => {});
        };

        window.openRadarModal = function() {
            const list = document.getElementById('htRadarListContainer');
            const sub = document.getElementById('htRadarSubtitle');
            const devices = window.htState.discoveredDevices || [];
            if (sub) sub.textContent = 'Terdeteksi ' + devices.length + ' perangkat walkie-talkie aktif di jaringan lokal';

            if (list) {
                if (devices.length === 0) {
                    list.innerHTML = '<div class="text-center py-4">' +
                        '<i class="bi bi-wifi-off fs-1 text-secondary opacity-50 mb-2"></i>' +
                        '<div class="fw-semibold small">Belum ada HT lain terdeteksi</div>' +
                        '<div class="text-secondary small px-3">100% Offline: Hubungkan PC & ponsel rekan ke Wi-Fi yang sama atau Hotspot tanpa internet.</div>' +
                    '</div>';
                } else {
                    let html = '<div class="d-flex flex-column gap-2">';
                    devices.forEach(d => {
                        const isSame = Math.abs(d.frequencyMHz - window.htState.currentFreq) < 0.005;
                        html += '<div class="p-2 border rounded-3 d-flex align-items-center justify-content-between ' + (isSame ? 'border-success' : '') + '">' +
                            '<div class="d-flex align-items-center gap-2">' +
                                '<span class="badge ' + (d.isTransmitting ? 'bg-danger' : (isSame ? 'bg-success' : 'bg-warning')) + ' rounded-pill" style="width: 10px; height: 10px; padding: 0;"></span>' +
                                '<div>' +
                                    '<div class="fw-bold small">' + escapeHtml(d.callsign) + '</div>' +
                                    '<div class="text-secondary" style="font-size: 11px;">IP: ' + escapeHtml(d.ip) + ' • Kanal: ' + escapeHtml(d.channelName || 'Simplex') + ' • ' + Number(d.frequencyMHz).toFixed(3) + ' MHz</div>' +
                                '</div>' +
                            '</div>';
                        if (!isSame) {
                            html += '<button class="btn btn-sm btn-primary py-1 px-2 fw-semibold small" onclick="tuneToDeviceFreq(' + d.frequencyMHz + ')">Ikut Freq</button>';
                        } else {
                            html += '<span class="badge bg-success bg-opacity-25 text-success border border-success border-opacity-50">Frekuensi Sama</span>';
                        }
                        html += '</div>';
                    });
                    html += '</div>';
                    list.innerHTML = html;
                }
            }
            new bootstrap.Modal(document.getElementById('htRadarModal')).show();
        };

        window.tuneToDeviceFreq = async function(freq) {
            window.htState.currentFreq = freq;
            updateHtUi(window.htState);
            bootstrap.Modal.getInstance(document.getElementById('htRadarModal'))?.hide();
            await fetch('/api/ht/action?action=set_freq&freq=' + freq, { method: 'POST' }).catch(() => {});
        };

        window.openLogsModal = function() {
            const list = document.getElementById('htLogsListContainer');
            const logs = window.htState.logs || [];
            if (list) {
                if (logs.length === 0) {
                    list.innerHTML = '<div class="text-center text-secondary py-4 small">Belum ada riwayat aktivitas HT.</div>';
                } else {
                    let html = '<div class="list-group list-group-flush">';
                    logs.forEach(log => {
                        const isTx = log.type === 'TX';
                        const isRx = log.type === 'RX';
                        const badgeClass = isTx ? 'bg-danger' : (isRx ? 'bg-success' : 'bg-secondary');
                        const timeStr = log.timestamp ? new Date(log.timestamp).toLocaleTimeString() : '';
                        html += '<div class="list-group-item px-2 py-2 d-flex align-items-start justify-content-between">' +
                            '<div class="d-flex align-items-start gap-2">' +
                                '<span class="badge ' + badgeClass + ' mt-1">' + log.type + '</span>' +
                                '<div>' +
                                    '<div class="small fw-semibold">' + escapeHtml(log.message) + '</div>' +
                                    '<div class="text-secondary" style="font-size: 11px;">' + escapeHtml(log.callsign || '') + ' • ' + Number(log.frequencyMHz || 144.2).toFixed(3) + ' MHz</div>' +
                                '</div>' +
                            '</div>' +
                            '<span class="text-secondary small">' + timeStr + '</span>' +
                        '</div>';
                    });
                    html += '</div>';
                    list.innerHTML = html;
                }
            }
            new bootstrap.Modal(document.getElementById('htLogsModal')).show();
        };

        window.clearHtLogs = async function() {
            window.htState.logs = [];
            window.openLogsModal();
            await fetch('/api/ht/action?action=clear_logs', { method: 'POST' }).catch(() => {});
        };

        window.openSettingsModal = function() {
            const slSpk = document.getElementById('htSliderSpeakerGain');
            if (slSpk) slSpk.value = window.htState.speakerGain;
            const bdSpk = document.getElementById('htSpeakerGainBadge');
            if (bdSpk) bdSpk.textContent = Number(window.htState.speakerGain).toFixed(1) + 'x';

            const slMic = document.getElementById('htSliderMicGain');
            if (slMic) slMic.value = window.htState.micGain;
            const bdMic = document.getElementById('htMicGainBadge');
            if (bdMic) bdMic.textContent = Number(window.htState.micGain).toFixed(1) + 'x';

            const swBeep = document.getElementById('htRogerBeepSwitch');
            if (swBeep) swBeep.checked = !!window.htState.rogerBeep;

            const swTail = document.getElementById('htSquelchTailSwitch');
            if (swTail) swTail.checked = !!window.htState.squelchTail;

            const slSql = document.getElementById('htSliderSquelch');
            if (slSql) slSql.value = window.htState.squelchLevel;
            const bdSql = document.getElementById('htSquelchBadge');
            if (bdSql) bdSql.textContent = 'Level ' + window.htState.squelchLevel;

            new bootstrap.Modal(document.getElementById('htSettingsModal')).show();
        };

        window.setHtSpeakerGain = async function(val) {
            window.htState.speakerGain = parseFloat(val);
            document.getElementById('htSpeakerGainBadge').textContent = Number(val).toFixed(1) + 'x';
            updateHtUi(window.htState);
            await fetch('/api/ht/action?action=set_speaker_gain&gain=' + val, { method: 'POST' }).catch(() => {});
        };

        window.setHtMicGain = async function(val) {
            window.htState.micGain = parseFloat(val);
            document.getElementById('htMicGainBadge').textContent = Number(val).toFixed(1) + 'x';
            updateHtUi(window.htState);
            await fetch('/api/ht/action?action=set_mic_gain&gain=' + val, { method: 'POST' }).catch(() => {});
        };

        window.toggleHtRogerBeep = async function() {
            window.htState.rogerBeep = !window.htState.rogerBeep;
            updateHtUi(window.htState);
            await fetch('/api/ht/action?action=toggle_roger_beep', { method: 'POST' }).catch(() => {});
        };

        window.toggleHtSquelchTail = async function() {
            window.htState.squelchTail = !window.htState.squelchTail;
            updateHtUi(window.htState);
            await fetch('/api/ht/action?action=toggle_squelch_tail', { method: 'POST' }).catch(() => {});
        };

        window.setHtSquelch = async function(val) {
            window.htState.squelchLevel = parseInt(val);
            document.getElementById('htSquelchBadge').textContent = 'Level ' + val;
            updateHtUi(window.htState);
            await fetch('/api/ht/action?action=set_squelch&level=' + val, { method: 'POST' }).catch(() => {});
        };

        window.testHtSound = function() {
            playLocalPttPressSound();
            setTimeout(playLocalRogerBeepSound, 120);
            fetch('/api/ht/action?action=test_sound', { method: 'POST' }).catch(() => {});
        };

        window.toggleHtHandsfree = async function() {
            window.htState.handsfree = !window.htState.handsfree;
            updateHtUi(window.htState);
            await fetch('/api/ht/action?action=toggle_handsfree', { method: 'POST' }).catch(() => {});
        };
        """
    }
}
