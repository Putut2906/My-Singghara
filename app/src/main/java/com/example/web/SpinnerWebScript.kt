package com.example.web

object SpinnerWebScript {
    fun getScript(dol: String): String {
        return """
        // =========================================================================
        // --- SPINNER MODULE: RODA UNDIAN KEBERUNTUNGAN (100% OFFLINE PERSIS ANDROID) ---
        // =========================================================================

        // --- GLOBAL STATE ---
        window.spinnerOptions = [];
        window.spinnerAngle = 0; // Current rotation in radians
        window.isSpinning = false;
        window.spinnerAnimId = null;
        window.spinnerWinnerName = '';
        window.spinnerParticleAnimId = null;
        window.spinnerParticles = [];

        // Vibrant colors for wheel segments (Identical to segmentColors in UndianSpinnerScreen.kt)
        const SPINNER_SEGMENT_COLORS = [
            "#FF5964", // Coral Red
            "#35A7FF", // Bright Blue
            "#38B000", // Lime Green
            "#F9C80E", // Vivid Yellow
            "#8338EC", // Purple
            "#FF70A6", // Pink
            "#06D6A0", // Teal
            "#F77F00", // Orange
            "#48CAE4", // Sky Blue
            "#7209B7"  // Deep Violet
        ];

        // Placeholder sector colors when empty
        const SPINNER_PLACEHOLDER_COLORS = [
            "#34495E",
            "#2C3E50",
            "#5D6D7E",
            "#7F8C8D"
        ];

        // --- WEB AUDIO API ENGINE (100% OFFLINE, ZERO EXTERNAL ASSETS) ---
        // Generates identical sound effects to TickSoundPlayer and FireworksSoundPlayer in Android
        const SpinnerAudioEngine = {
            audioCtx: null,
            activeNodes: [],

            getAudioContext() {
                if (!this.audioCtx) {
                    const AudioContextClass = window.AudioContext || window.webkitAudioContext;
                    if (AudioContextClass) {
                        this.audioCtx = new AudioContextClass();
                    }
                }
                if (this.audioCtx && this.audioCtx.state === 'suspended') {
                    this.audioCtx.resume();
                }
                return this.audioCtx;
            },

            // Realistic "tek tek" wood-block tick sound (swept sine from 1500Hz decaying downwards in 15ms)
            playClick() {
                try {
                    const ctx = this.getAudioContext();
                    if (!ctx) return;
                    const now = ctx.currentTime;
                    const osc = ctx.createOscillator();
                    const gain = ctx.createGain();

                    osc.type = 'sine';
                    osc.frequency.setValueAtTime(1500, now);
                    osc.frequency.exponentialRampToValueAtTime(120, now + 0.015);

                    gain.gain.setValueAtTime(0.4, now);
                    gain.gain.exponentialRampToValueAtTime(0.001, now + 0.015);

                    osc.connect(gain);
                    gain.connect(ctx.destination);

                    osc.start(now);
                    osc.stop(now + 0.018);
                } catch (e) {
                    // Audio fallback
                }
            },

            // Grand Festive Fireworks Celebration (Rocket whistle, grand boom rumble, cascading sparkle pops, fanfare chords)
            playCelebration() {
                try {
                    this.stop();
                    const ctx = this.getAudioContext();
                    if (!ctx) return;
                    const now = ctx.currentTime;

                    // 1. Rocket Launch Whistle (0s to 0.38s)
                    const whistleOsc = ctx.createOscillator();
                    const whistleGain = ctx.createGain();
                    whistleOsc.type = 'sine';
                    whistleOsc.frequency.setValueAtTime(420, now);
                    whistleOsc.frequency.exponentialRampToValueAtTime(2500, now + 0.38);

                    whistleGain.gain.setValueAtTime(0.01, now);
                    whistleGain.gain.linearRampToValueAtTime(0.25, now + 0.28);
                    whistleGain.gain.exponentialRampToValueAtTime(0.001, now + 0.38);

                    whistleOsc.connect(whistleGain);
                    whistleGain.connect(ctx.destination);
                    whistleOsc.start(now);
                    whistleOsc.stop(now + 0.39);
                    this.activeNodes.push(whistleOsc);

                    // 2. Grand Boom Explosion (at 0.38s)
                    const boomTime = now + 0.38;

                    // Sub-bass punch
                    const boomOsc = ctx.createOscillator();
                    const boomGain = ctx.createGain();
                    boomOsc.type = 'sine';
                    boomOsc.frequency.setValueAtTime(120, boomTime);
                    boomOsc.frequency.exponentialRampToValueAtTime(36, boomTime + 0.85);

                    boomGain.gain.setValueAtTime(0.75, boomTime);
                    boomGain.gain.exponentialRampToValueAtTime(0.001, boomTime + 1.25);

                    boomOsc.connect(boomGain);
                    boomGain.connect(ctx.destination);
                    boomOsc.start(boomTime);
                    boomOsc.stop(boomTime + 1.3);
                    this.activeNodes.push(boomOsc);

                    // Noise explosion rumble
                    const bufferSize = Math.floor(ctx.sampleRate * 1.5);
                    const noiseBuffer = ctx.createBuffer(1, bufferSize, ctx.sampleRate);
                    const output = noiseBuffer.getChannelData(0);
                    for (let i = 0; i < bufferSize; i++) {
                        output[i] = Math.random() * 2 - 1;
                    }
                    const noiseSource = ctx.createBufferSource();
                    noiseSource.buffer = noiseBuffer;

                    const noiseFilter = ctx.createBiquadFilter();
                    noiseFilter.type = 'lowpass';
                    noiseFilter.frequency.setValueAtTime(800, boomTime);
                    noiseFilter.frequency.exponentialRampToValueAtTime(120, boomTime + 1.2);

                    const noiseGain = ctx.createGain();
                    noiseGain.gain.setValueAtTime(0.45, boomTime);
                    noiseGain.gain.exponentialRampToValueAtTime(0.001, boomTime + 1.25);

                    noiseSource.connect(noiseFilter);
                    noiseFilter.connect(noiseGain);
                    noiseGain.connect(ctx.destination);
                    noiseSource.start(boomTime);
                    noiseSource.stop(boomTime + 1.3);
                    this.activeNodes.push(noiseSource);

                    // 3. Cascading Crackles & Sparkle Pops (Mercon & Kembang Api Beruntun)
                    const popDelays = [0.48, 0.62, 0.78, 0.95, 1.15, 1.38, 1.62, 1.88, 2.15];
                    const popPitches = [260, 190, 310, 220, 280, 170, 340, 200, 240];

                    popDelays.forEach((delay, idx) => {
                        const popTime = now + delay;
                        const popOsc = ctx.createOscillator();
                        const popGain = ctx.createGain();
                        popOsc.type = 'triangle';
                        popOsc.frequency.setValueAtTime(popPitches[idx], popTime);
                        popOsc.frequency.exponentialRampToValueAtTime(60, popTime + 0.16);

                        popGain.gain.setValueAtTime(0.3, popTime);
                        popGain.gain.exponentialRampToValueAtTime(0.001, popTime + 0.16);

                        popOsc.connect(popGain);
                        popGain.connect(ctx.destination);
                        popOsc.start(popTime);
                        popOsc.stop(popTime + 0.17);
                        this.activeNodes.push(popOsc);
                    });

                    // 4. Celebration Fanfare Harmonics (C5, E5, G5, C6)
                    const chordTimes = [0.65, 0.95, 1.30, 1.65];
                    const chordFreqs = [523.25, 659.25, 783.99, 1046.50];
                    chordTimes.forEach((delay, idx) => {
                        const cTime = now + delay;
                        const cOsc = ctx.createOscillator();
                        const cGain = ctx.createGain();
                        cOsc.type = 'sine';
                        cOsc.frequency.setValueAtTime(chordFreqs[idx], cTime);

                        cGain.gain.setValueAtTime(0.22, cTime);
                        cGain.gain.exponentialRampToValueAtTime(0.001, cTime + 0.65);

                        cOsc.connect(cGain);
                        cGain.connect(ctx.destination);
                        cOsc.start(cTime);
                        cOsc.stop(cTime + 0.7);
                        this.activeNodes.push(cOsc);
                    });
                } catch (e) {
                    console.warn("Celebration audio error", e);
                }
            },

            // Crisp secondary firework burst pop
            playBurst() {
                try {
                    const ctx = this.getAudioContext();
                    if (!ctx) return;
                    const now = ctx.currentTime;
                    const osc = ctx.createOscillator();
                    const gain = ctx.createGain();
                    osc.type = 'sine';
                    osc.frequency.setValueAtTime(210, now);
                    osc.frequency.exponentialRampToValueAtTime(45, now + 0.26);

                    gain.gain.setValueAtTime(0.35, now);
                    gain.gain.exponentialRampToValueAtTime(0.001, now + 0.26);

                    osc.connect(gain);
                    gain.connect(ctx.destination);
                    osc.start(now);
                    osc.stop(now + 0.28);
                    this.activeNodes.push(osc);
                } catch (e) {}
            },

            stop() {
                this.activeNodes.forEach(node => {
                    try { node.stop(); } catch (e) {}
                });
                this.activeNodes = [];
            }
        };

        // --- LOCAL STORAGE PERSISTENCE (100% OFFLINE) ---
        function loadSpinnerFromStorage() {
            try {
                const saved = localStorage.getItem('my_singghara_spinner_options');
                if (saved) {
                    const parsed = JSON.parse(saved);
                    if (Array.isArray(parsed)) {
                        window.spinnerOptions = parsed;
                        return;
                    }
                }
            } catch (e) {}
            // Default options start empty or default to empty as in UndianSpinnerScreen.kt
            window.spinnerOptions = [];
        }

        function saveSpinnerToStorage() {
            try {
                localStorage.setItem('my_singghara_spinner_options', JSON.stringify(window.spinnerOptions));
            } catch (e) {}
        }

        // Helper Toast Notification
        function showSpinnerToast(msg, isError = false) {
            let toastEl = document.getElementById('spinnerToastPopup');
            if (!toastEl) {
                toastEl = document.createElement('div');
                toastEl.id = 'spinnerToastPopup';
                toastEl.style.cssText = 'position: fixed; bottom: 30px; left: 50%; transform: translateX(-50%) translateY(100px); opacity: 0; z-index: 10050; padding: 12px 24px; border-radius: 30px; font-weight: 700; font-size: 0.95rem; box-shadow: 0 10px 30px rgba(0,0,0,0.35); transition: all 0.35s cubic-bezier(0.175, 0.885, 0.32, 1.275); pointer-events: none;';
                document.body.appendChild(toastEl);
            }
            toastEl.style.backgroundColor = isError ? '#ef4444' : '#0F766E';
            toastEl.style.color = '#ffffff';
            toastEl.innerHTML = '<i class="bi ' + (isError ? 'bi-exclamation-circle-fill' : 'bi-check-circle-fill') + ' me-2"></i>' + escapeHtml(msg);
            toastEl.style.transform = 'translateX(-50%) translateY(0)';
            toastEl.style.opacity = '1';

            if (window._spinnerToastTimeout) clearTimeout(window._spinnerToastTimeout);
            window._spinnerToastTimeout = setTimeout(() => {
                toastEl.style.transform = 'translateX(-50%) translateY(100px)';
                toastEl.style.opacity = '0';
            }, 2600);
        }

        // --- MAIN ENTRY: loadSpinner() ---
        function loadSpinner() {
            loadSpinnerFromStorage();

            const count = window.spinnerOptions.length;
            const subtitleText = count === 0
                ? "Klik tombol emas tengah atau tambah opsi!"
                : "Tekan tombol emas tengah untuk mengundi!";

            let html = `
                <!-- TOAST & MODALS PLACEHOLDER -->
                <div id="spinnerModalsContainer"></div>

                <!-- FULLSCREEN FIREWORKS & CONFETTI CANVAS OVERLAY -->
                <canvas id="spinnerParticleCanvas" style="position: fixed; top: 0; left: 0; width: 100vw; height: 100vh; pointer-events: none; z-index: 9999; display: none;"></canvas>

                <!-- HEADER -->
                <div class="d-flex flex-wrap justify-content-between align-items-center mb-4 gap-3">
                    <div class="d-flex align-items-center gap-3">
                        <div class="rounded-circle d-flex align-items-center justify-content-center shadow-sm" style="width: 48px; height: 48px; background: linear-gradient(135deg, #8338EC 0%, #3A86FF 100%); color: #ffffff;">
                            <i class="bi bi-arrow-repeat fs-4"></i>
                        </div>
                        <div>
                            <h4 class="fw-bold mb-0 d-flex align-items-center gap-2" style="letter-spacing: -0.5px;">
                                Spinner Roda Undian
                                <span class="badge rounded-pill bg-warning text-dark fs-7 px-2 py-1">100% Offline</span>
                            </h4>
                            <small class="text-secondary">Undian Acak, Arisan, Doorprize & Pembagian Kelompok (Sama Persis Aplikasi My Singghara)</small>
                        </div>
                    </div>

                    <!-- QUICK ACTION TOOLBAR -->
                    <div class="d-flex align-items-center gap-2">
                        <button class="btn btn-sm btn-outline-secondary d-flex align-items-center gap-1 shadow-sm" onclick="shuffleSpinnerOptions()" title="Acak urutan pilihan">
                            <i class="bi bi-shuffle"></i> <span class="d-none d-sm-inline">Acak Urutan</span>
                        </button>
                        <div class="dropdown">
                            <button class="btn btn-sm btn-outline-secondary dropdown-toggle d-flex align-items-center gap-1 shadow-sm" type="button" data-bs-toggle="dropdown" aria-expanded="false">
                                <i class="bi bi-magic"></i> <span class="d-none d-sm-inline">Contoh Pilihan</span>
                            </button>
                            <ul class="dropdown-menu dropdown-menu-end shadow border-0" style="border-radius: 12px;">
                                <li><h6 class="dropdown-header text-uppercase fs-8">Muat Preset Undian</h6></li>
                                <li><a class="dropdown-item py-2" href="javascript:void(0)" onclick="loadSpinnerPreset('nama')"><i class="bi bi-people me-2 text-primary"></i> Contoh Nama / Peserta</a></li>
                                <li><a class="dropdown-item py-2" href="javascript:void(0)" onclick="loadSpinnerPreset('doorprize')"><i class="bi bi-gift me-2 text-danger"></i> Doorprize & Hadiah</a></li>
                                <li><a class="dropdown-item py-2" href="javascript:void(0)" onclick="loadSpinnerPreset('kuliner')"><i class="bi bi-cup-hot me-2 text-warning"></i> Pilihan Makanan</a></li>
                                <li><a class="dropdown-item py-2" href="javascript:void(0)" onclick="loadSpinnerPreset('angka')"><i class="bi bi-123 me-2 text-success"></i> Angka 1 s/d 8</a></li>
                                <li><a class="dropdown-item py-2" href="javascript:void(0)" onclick="loadSpinnerPreset('yatidak')"><i class="bi bi-question-circle me-2 text-info"></i> Ya / Tidak</a></li>
                            </ul>
                        </div>
                        <button class="btn btn-sm btn-outline-danger d-flex align-items-center gap-1 shadow-sm" onclick="clearAllSpinnerOptions()" title="Bersihkan semua opsi">
                            <i class="bi bi-trash3"></i> <span class="d-none d-sm-inline">Reset</span>
                        </button>
                        <button class="btn btn-brand btn-sm d-flex align-items-center gap-1 px-3 shadow" onclick="openAddSpinnerOptionModal()">
                            <i class="bi bi-plus-lg"></i> <span>Tambah Opsi</span>
                        </button>
                    </div>
                </div>

                <!-- MAIN WORKSPACE: SPINNER WHEEL & OPTIONS LIST -->
                <div class="row g-4 align-items-start justify-content-center">
                    <!-- LEFT COLUMN: INTERACTIVE WHEEL CONTAINER -->
                    <div class="col-12 col-lg-6 d-flex flex-column align-items-center">
                        <div class="card card-custom p-4 text-center shadow-lg w-100 d-flex flex-column align-items-center" style="border-radius: 28px; max-width: 480px; position: relative;">
                            
                            <!-- WHEEL OUTER HOUSING WITH POINTER & CENTER HUB -->
                            <div style="position: relative; width: 330px; height: 330px; display: flex; align-items: center; justify-content: center;">
                                
                                <!-- DOWNWARD POINTER ARROW (TOP CENTER OUTSIDE ROTATING WHEEL) -->
                                <div style="position: absolute; top: -6px; left: 50%; transform: translateX(-50%); z-index: 25; pointer-events: none;">
                                    <svg width="34" height="30" viewBox="0 0 34 30">
                                        <polygon points="17,30 2,2 32,2" fill="#FF5964" stroke="#ffffff" stroke-width="2.5" stroke-linejoin="round" />
                                    </svg>
                                </div>

                                <!-- RADIAL RIM & CANVAS HOUSING (310dp size, border-radius circle, drop shadow) -->
                                <div style="width: 316px; height: 316px; border-radius: 50%; padding: 8px; box-shadow: 0 16px 36px rgba(0, 0, 0, 0.45); background: radial-gradient(circle, #2C3E50 0%, #1A252F 100%); border: 8px solid var(--border-color, #34495E); display: flex; align-items: center; justify-content: center; position: relative;">
                                    
                                    <!-- ROTATING CANVAS -->
                                    <canvas id="spinnerWheelCanvas" width="300" height="300" style="border-radius: 50%; cursor: pointer;" onclick="onCenterHubClick()"></canvas>
                                    
                                    <!-- CRITICAL: PLAY ARROW / SPIN BUTTON DIRECTLY ON CENTRAL HUB (72px circle) -->
                                    <button id="spinnerCenterHubBtn" onclick="onCenterHubClick()" 
                                        style="position: absolute; top: 50%; left: 50%; transform: translate(-50%, -50%); width: 72px; height: 72px; border-radius: 50%; border: 3.5px solid #ffffff; box-shadow: 0 8px 24px rgba(0,0,0,0.5); background: linear-gradient(180deg, #FFD700 0%, #FFA000 100%); display: flex; align-items: center; justify-content: center; z-index: 20; cursor: pointer; transition: all 0.2s cubic-bezier(0.175, 0.885, 0.32, 1.275); outline: none;"
                                        title="Tekan untuk memutar roda undian">
                                        <i class="bi bi-arrow-repeat" id="spinnerHubIcon" style="font-size: 36px; color: #1B1D28; display: inline-block;"></i>
                                    </button>
                                </div>
                            </div>

                            <!-- SUBTITLE HELPER TEXT (Identical to Android Text) -->
                            <div class="mt-3">
                                <p class="text-secondary small fw-semibold mb-1" id="spinnerSubtitleHelper" style="font-size: 0.88rem;">
                                    ${dol}{subtitleText}
                                </p>
                                <span class="badge bg-secondary bg-opacity-10 text-secondary border border-secondary border-opacity-25 px-2 py-1 fs-8">
                                    <i class="bi bi-shield-check me-1 text-success"></i> Pengacakan Fisika Realistis & Suara Tek-Tek
                                </span>
                            </div>
                        </div>
                    </div>

                    <!-- RIGHT COLUMN: OPTIONS LIST CONTAINER -->
                    <div class="col-12 col-lg-6">
                        <div class="card card-custom p-4 shadow-sm h-100 d-flex flex-column" style="border-radius: 24px; min-height: 420px;">
                            <div class="d-flex justify-content-between align-items-center mb-3">
                                <h6 class="fw-bold mb-0 d-flex align-items-center gap-2">
                                    <i class="bi bi-card-checklist text-warning fs-5"></i>
                                    <span>Daftar Opsi Undian (<span id="spinnerOptionsCount">${dol}{count}</span>):</span>
                                </h6>
                                <button class="btn btn-sm btn-outline-brand d-flex align-items-center gap-1" onclick="openAddSpinnerOptionModal()">
                                    <i class="bi bi-plus-circle"></i> Tambah Opsi
                                </button>
                            </div>

                            <!-- SCROLLABLE LIST OF RAFFLE OPTIONS -->
                            <div id="spinnerOptionsListContainer" class="flex-grow-1 overflow-auto pe-1" style="max-height: 330px;">
                                ${dol}{renderSpinnerOptionsListHtml()}
                            </div>

                            <!-- BOTTOM QUICK ADD HELPER BAR -->
                            <div class="mt-3 pt-3 border-top border-secondary border-opacity-25 d-flex gap-2">
                                <input type="text" id="spinnerQuickInput" class="form-control form-control-dark" placeholder="Ketik opsi & tekan Enter..." onkeydown="if(event.key==='Enter') quickAddSpinnerOption()">
                                <button class="btn btn-brand px-3 text-nowrap d-flex align-items-center gap-1" onclick="quickAddSpinnerOption()">
                                    <i class="bi bi-plus-lg"></i> <span class="d-none d-sm-inline">Tambah</span>
                                </button>
                            </div>
                        </div>
                    </div>
                </div>
            `;

            const container = document.getElementById('tab-content') || document.getElementById('main-content');
            if (container) container.innerHTML = html;

            // Draw current canvas
            drawSpinnerWheel();

            // Bind window resize for particle canvas
            window.onresize = () => {
                const pCanvas = document.getElementById('spinnerParticleCanvas');
                if (pCanvas) {
                    pCanvas.width = window.innerWidth;
                    pCanvas.height = window.innerHeight;
                }
            };
        }

        // --- RENDER OPTIONS LIST HTML ---
        function renderSpinnerOptionsListHtml() {
            if (!window.spinnerOptions || window.spinnerOptions.length === 0) {
                return `
                    <div class="text-center py-5 d-flex flex-column align-items-center justify-content-center text-secondary">
                        <i class="bi bi-card-list opacity-50 mb-2" style="font-size: 48px;"></i>
                        <p class="mb-1 small fw-semibold">Daftar kosong.</p>
                        <p class="mb-0 fs-8 text-muted">Silakan klik <b>+ Tambah Opsi</b> atau muat <b>Contoh Pilihan</b> di atas!</p>
                    </div>
                `;
            }

            let html = '<div class="d-flex flex-column gap-2">';
            window.spinnerOptions.forEach((opt, idx) => {
                const color = SPINNER_SEGMENT_COLORS[idx % SPINNER_SEGMENT_COLORS.length];
                html += `
                    <div class="d-flex align-items-center justify-content-between p-2 px-3 rounded-3 dashboard-subcard border shadow-xs" style="border-left: 5px solid ${dol}{color} !important;">
                        <div class="d-flex align-items-center gap-2 overflow-hidden me-2">
                            <span class="rounded-circle flex-shrink-0" style="width: 12px; height: 12px; background-color: ${dol}{color};"></span>
                            <span class="fw-bold text-truncate" style="font-size: 0.92rem;" title="${dol}{escapeHtml(opt)}">${dol}{escapeHtml(opt)}</span>
                        </div>
                        <div class="d-flex align-items-center gap-1 flex-shrink-0">
                            <button class="btn btn-sm text-secondary p-1 px-2" onclick="editSpinnerItem(${dol}{idx})" title="Ubah nama">
                                <i class="bi bi-pencil-square"></i>
                            </button>
                            <button class="btn btn-sm text-danger p-1 px-2" onclick="deleteSpinnerItem(${dol}{idx})" title="Hapus opsi">
                                <i class="bi bi-trash3"></i>
                            </button>
                        </div>
                    </div>
                `;
            });
            html += '</div>';
            return html;
        }

        function refreshSpinnerOptionsUI() {
            const listEl = document.getElementById('spinnerOptionsListContainer');
            if (listEl) listEl.innerHTML = renderSpinnerOptionsListHtml();
            const countEl = document.getElementById('spinnerOptionsCount');
            if (countEl) countEl.innerText = window.spinnerOptions.length;

            const helperEl = document.getElementById('spinnerSubtitleHelper');
            if (helperEl) {
                helperEl.innerText = window.spinnerOptions.length === 0
                    ? "Klik tombol emas tengah atau tambah opsi!"
                    : "Tekan tombol emas tengah untuk mengundi!";
            }

            saveSpinnerToStorage();
            drawSpinnerWheel();
        }

        // --- DRAW SPINNER WHEEL CANVAS ---
        function drawSpinnerWheel() {
            const canvas = document.getElementById('spinnerWheelCanvas');
            if (!canvas) return;
            const ctx = canvas.getContext('2d');
            const centerX = canvas.width / 2;
            const centerY = canvas.height / 2;
            const radius = centerX - 2;

            ctx.clearRect(0, 0, canvas.width, canvas.height);

            const isEmpty = !window.spinnerOptions || window.spinnerOptions.length === 0;
            const count = isEmpty ? 4 : window.spinnerOptions.length;
            const sweepAngle = (2 * Math.PI) / count;

            ctx.save();
            ctx.translate(centerX, centerY);
            ctx.rotate(window.spinnerAngle);

            for (let i = 0; i < count; i++) {
                const startAngle = i * sweepAngle;
                const endAngle = startAngle + sweepAngle;

                // Sector Fill
                ctx.beginPath();
                ctx.moveTo(0, 0);
                ctx.arc(0, 0, radius, startAngle, endAngle);
                ctx.closePath();
                ctx.fillStyle = isEmpty
                    ? SPINNER_PLACEHOLDER_COLORS[i % 4]
                    : SPINNER_SEGMENT_COLORS[i % SPINNER_SEGMENT_COLORS.length];
                ctx.fill();

                // Sector Divider Line (2px semi-transparent white)
                ctx.beginPath();
                ctx.moveTo(0, 0);
                ctx.lineTo(Math.cos(startAngle) * radius, Math.sin(startAngle) * radius);
                ctx.lineWidth = 2;
                ctx.strokeStyle = 'rgba(255, 255, 255, 0.35)';
                ctx.stroke();

                // Sector Label
                const midAngle = startAngle + (sweepAngle / 2);
                ctx.save();
                ctx.rotate(midAngle);

                if (!isEmpty) {
                    const rawText = window.spinnerOptions[i];
                    const maxLength = 10;
                    const displayText = rawText.length > maxLength
                        ? rawText.substring(0, maxLength - 2) + '..'
                        : rawText;

                    ctx.textAlign = 'right';
                    ctx.textBaseline = 'middle';
                    ctx.fillStyle = '#ffffff';
                    ctx.font = '900 12px "Courier New", monospace';
                    ctx.shadowColor = 'rgba(0,0,0,0.5)';
                    ctx.shadowBlur = 4;
                    ctx.fillText(displayText, radius * 0.88, 0);
                } else {
                    // Placeholder "?" symbol
                    ctx.textAlign = 'right';
                    ctx.textBaseline = 'middle';
                    ctx.fillStyle = 'rgba(255, 255, 255, 0.65)';
                    ctx.font = 'bold 15px "Courier New", monospace';
                    ctx.fillText('?', radius * 0.72, 0);
                }
                ctx.restore();
            }

            ctx.restore();
        }

        // Helper: Calculate current winning index relative to top indicator (270 degrees / 12 o'clock)
        // Matches UndianSpinnerScreen.kt getWinningIndex()
        function getSpinnerWinningIndex(angleRad, count) {
            if (count <= 0) return 0;
            const sweepDeg = 360 / count;
            const angleDeg = (angleRad * 180 / Math.PI);
            const normalizedAngle = (angleDeg % 360 + 360) % 360;
            const pointerTarget = (270 - normalizedAngle + 360) % 360;
            const index = Math.floor(pointerTarget / sweepDeg);
            return Math.min(Math.max(index, 0), count - 1);
        }

        // --- INTERACTION: ON CENTER HUB CLICK ---
        function onCenterHubClick() {
            if (window.isSpinning) return;

            if (!window.spinnerOptions || window.spinnerOptions.length === 0) {
                showSpinnerToast("Tambahkan opsi terlebih dahulu!", true);
                openAddSpinnerOptionModal();
                return;
            }

            spinWheel();
        }

        // --- SPIN WHEEL PHYSICS ANIMATION ---
        function spinWheel() {
            if (window.isSpinning) return;
            window.isSpinning = true;

            // Warm up audio context on user action
            SpinnerAudioEngine.getAudioContext();

            const hubBtn = document.getElementById('spinnerCenterHubBtn');
            const hubIcon = document.getElementById('spinnerHubIcon');
            if (hubBtn) {
                hubBtn.style.background = 'linear-gradient(180deg, #BFC9CA 0%, #7F8C8D 100%)';
                hubBtn.style.transform = 'translate(-50%, -50%) scale(0.94)';
            }

            // Decelerating Physics Spin Algorithm (identical to UndianSpinnerScreen.kt)
            const randomTurns = 7 + Math.floor(Math.random() * 5); // 7 to 11 full rotations
            const finalOffsetDeg = Math.random() * 360;
            const totalRadianToAdd = (randomTurns * 2 * Math.PI) + (finalOffsetDeg * Math.PI / 180);

            const startAngle = window.spinnerAngle;
            const targetAngle = startAngle + totalRadianToAdd;
            const duration = 5200; // 5.2 seconds
            const startTime = performance.now();

            let lastWinningIndex = getSpinnerWinningIndex(window.spinnerAngle, window.spinnerOptions.length);

            // Cubic Bezier (0.12, 0.88, 0.18, 1.0) approx easing
            function cubicBezierEase(t) {
                // High initial velocity, smooth prolonged decelerating spin
                return 1 - Math.pow(1 - t, 3.8);
            }

            function animate(now) {
                const elapsed = now - startTime;
                const progress = Math.min(elapsed / duration, 1.0);
                const ease = cubicBezierEase(progress);

                window.spinnerAngle = startAngle + (totalRadianToAdd * ease);
                drawSpinnerWheel();

                // Trigger tick sound on crossing sector boundaries
                const currentWinIdx = getSpinnerWinningIndex(window.spinnerAngle, window.spinnerOptions.length);
                if (currentWinIdx !== lastWinningIndex) {
                    lastWinningIndex = currentWinIdx;
                    SpinnerAudioEngine.playClick();
                }

                if (progress < 1.0) {
                    window.spinnerAnimId = requestAnimationFrame(animate);
                } else {
                    window.isSpinning = false;
                    window.spinnerAngle = targetAngle % (2 * Math.PI);
                    drawSpinnerWheel();

                    if (hubBtn) {
                        hubBtn.style.background = 'linear-gradient(180deg, #FFD700 0%, #FFA000 100%)';
                        hubBtn.style.transform = 'translate(-50%, -50%) scale(1)';
                    }

                    // Determine Winner
                    const winningIdx = getSpinnerWinningIndex(window.spinnerAngle, window.spinnerOptions.length);
                    const winner = window.spinnerOptions[winningIdx];
                    window.spinnerWinnerName = winner;

                    // Play Festive Fireworks Celebration Audio
                    SpinnerAudioEngine.playCelebration();

                    // Show Celebratory Winner Modal & Particles
                    showWinnerAnnouncementModal(winner, winningIdx);
                }
            }

            window.spinnerAnimId = requestAnimationFrame(animate);
        }

        // --- FULLSCREEN FIREWORKS & CONFETTI PARTICLE SYSTEM ---
        function startCelebrationParticles() {
            const canvas = document.getElementById('spinnerParticleCanvas');
            if (!canvas) return;
            canvas.style.display = 'block';
            canvas.width = window.innerWidth;
            canvas.height = window.innerHeight;
            const ctx = canvas.getContext('2d');

            window.spinnerParticles = [];
            const colors = [
                "#FF5964", "#35A7FF", "#38B000", "#F9C80E",
                "#8338EC", "#FF70A6", "#00F5D4", "#F77F00",
                "#9D4EDD", "#FF3366", "#33CCFF"
            ];

            // Seed initial 100 falling fluttering paper confetti
            for (let i = 0; i < 110; i++) {
                window.spinnerParticles.push({
                    x: Math.random() * canvas.width,
                    y: -50 - Math.random() * 400,
                    vx: (Math.random() - 0.5) * 5,
                    vy: Math.random() * 6 + 3,
                    color: colors[Math.floor(Math.random() * colors.length)],
                    size: Math.random() * 8 + 6,
                    alpha: 1.0,
                    rotation: Math.random() * 360,
                    rotationSpeed: (Math.random() - 0.5) * 8,
                    type: 0 // 0 = Confetti
                });
            }

            let lastTime = performance.now();

            function updateParticles(time) {
                ctx.clearRect(0, 0, canvas.width, canvas.height);

                // Spawn periodic explosive fireworks in background
                if (Math.random() < 0.05 && window.spinnerParticles.filter(p => p.type === 1).length < 80) {
                    SpinnerAudioEngine.playBurst();
                    const fx = 80 + Math.random() * (canvas.width - 160);
                    const fy = 60 + Math.random() * (canvas.height * 0.6);
                    const fColor = colors[Math.floor(Math.random() * colors.length)];
                    for (let j = 0; j < 26; j++) {
                        const angle = Math.random() * 2 * Math.PI;
                        const speed = Math.random() * 10 + 2;
                        window.spinnerParticles.push({
                            x: fx,
                            y: fy,
                            vx: Math.cos(angle) * speed,
                            vy: Math.sin(angle) * speed,
                            color: fColor,
                            size: Math.random() * 5 + 3,
                            alpha: 1.0,
                            type: 1 // 1 = Fireworks sparkle
                        });
                    }
                }

                // Update & draw particles
                for (let i = window.spinnerParticles.length - 1; i >= 0; i--) {
                    const p = window.spinnerParticles[i];
                    p.x += p.vx;
                    p.y += p.vy;

                    if (p.type === 0) {
                        // Confetti fluttering paper physics
                        p.vy += 0.04;
                        p.vx += Math.sin(time / 150 + p.size) * 0.1;
                        p.rotation += p.rotationSpeed;

                        if (p.y > canvas.height + 50) {
                            p.y = -30;
                            p.x = Math.random() * canvas.width;
                            p.vy = Math.random() * 6 + 3;
                        }

                        ctx.save();
                        ctx.translate(p.x, p.y);
                        ctx.rotate(p.rotation * Math.PI / 180);
                        ctx.fillStyle = p.color;
                        ctx.globalAlpha = p.alpha;
                        ctx.fillRect(-p.size / 2, -p.size / 4, p.size, p.size / 2);
                        ctx.restore();
                    } else {
                        // Firework spark physics
                        p.vy += 0.07;
                        p.alpha -= 0.02;
                        p.size *= 0.97;

                        if (p.alpha <= 0 || p.size < 1) {
                            window.spinnerParticles.splice(i, 1);
                            continue;
                        }

                        ctx.save();
                        ctx.beginPath();
                        ctx.arc(p.x, p.y, p.size, 0, 2 * Math.PI);
                        ctx.fillStyle = p.color;
                        ctx.globalAlpha = Math.max(p.alpha, 0);
                        ctx.fill();
                        ctx.restore();
                    }
                }

                window.spinnerParticleAnimId = requestAnimationFrame(updateParticles);
            }

            window.spinnerParticleAnimId = requestAnimationFrame(updateParticles);
        }

        function stopCelebrationParticles() {
            if (window.spinnerParticleAnimId) {
                cancelAnimationFrame(window.spinnerParticleAnimId);
                window.spinnerParticleAnimId = null;
            }
            const canvas = document.getElementById('spinnerParticleCanvas');
            if (canvas) {
                canvas.style.display = 'none';
                const ctx = canvas.getContext('2d');
                ctx.clearRect(0, 0, canvas.width, canvas.height);
            }
            SpinnerAudioEngine.stop();
        }

        // --- WINNER ANNOUNCEMENT MODAL ("HASIL UNDIAN") ---
        // Matches UndianSpinnerScreen.kt Compose AlertDialog
        function showWinnerAnnouncementModal(winnerName, winnerIdx) {
            startCelebrationParticles();

            let modalEl = document.getElementById('spinnerWinnerModal');
            if (modalEl) modalEl.remove();

            const modalHtml = `
                <div class="modal fade" id="spinnerWinnerModal" tabindex="-1" aria-hidden="true" data-bs-backdrop="static">
                    <div class="modal-dialog modal-dialog-centered" style="max-width: 440px;">
                        <div class="modal-content text-center shadow-lg p-3" style="border-radius: 28px; border: 2px solid #FFD700; background-color: var(--card-bg, #ffffff);">
                            <div class="modal-body pt-4 pb-3">
                                
                                <!-- GOLDEN STAR BADGE (56px) -->
                                <div class="mx-auto mb-3 rounded-circle d-flex align-items-center justify-content-center shadow" style="width: 64px; height: 64px; background: linear-gradient(135deg, #FFD700 0%, #FFA000 100%);">
                                    <i class="bi bi-star-fill text-white fs-2"></i>
                                </div>

                                <!-- TITLE: HASIL UNDIAN -->
                                <h4 class="fw-bold mb-1" style="letter-spacing: 1.5px; color: #0F766E;">
                                    HASIL UNDIAN
                                </h4>
                                <p class="text-secondary small mb-3">Selamat! Roda spinner berhenti pada pilihan:</p>

                                <!-- WINNER NAME CARD (Gold Border, prominent typography) -->
                                <div class="p-4 rounded-4 mb-3 shadow-sm" style="border: 2.5px solid #FFD700; background: rgba(255, 215, 0, 0.08);">
                                    <h2 class="fw-black mb-0 text-break" style="font-size: 1.85rem; font-weight: 900; color: var(--text-main, #0F766E);">
                                        ✨ ${dol}{escapeHtml(winnerName)} ✨
                                    </h2>
                                </div>

                                <p class="text-muted fs-8 mb-4">
                                    <i class="bi bi-info-circle me-1"></i> Pilihan di atas ditentukan secara acak & adil.
                                </p>

                                <!-- ACTION BUTTONS -->
                                <div class="d-flex flex-column gap-2">
                                    <button class="btn btn-brand btn-lg py-2 fw-bold shadow-sm" onclick="closeWinnerModal()" style="border-radius: 14px;">
                                        MANTAP!
                                    </button>
                                    <div class="d-flex gap-2 justify-content-center mt-1">
                                        <button class="btn btn-sm btn-outline-secondary px-3" onclick="removeWinnerAndClose(${dol}{winnerIdx})" title="Hapus pemenang ini dari daftar agar tidak terpilih lagi">
                                            <i class="bi bi-person-x me-1 text-danger"></i> Hapus Dari Pilihan
                                        </button>
                                        <button class="btn btn-sm btn-outline-brand px-3" onclick="spinAgainFromModal()">
                                            <i class="bi bi-arrow-repeat me-1"></i> Putar Lagi
                                        </button>
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            `;

            const modalsContainer = document.getElementById('spinnerModalsContainer');
            if (modalsContainer) {
                modalsContainer.innerHTML = modalHtml;
                const modal = new bootstrap.Modal(document.getElementById('spinnerWinnerModal'));
                modal.show();
            }
        }

        function closeWinnerModal() {
            const modalEl = document.getElementById('spinnerWinnerModal');
            if (modalEl) {
                const bsModal = bootstrap.Modal.getInstance(modalEl);
                if (bsModal) bsModal.hide();
            }
            stopCelebrationParticles();
        }

        function removeWinnerAndClose(winnerIdx) {
            closeWinnerModal();
            if (winnerIdx >= 0 && winnerIdx < window.spinnerOptions.length) {
                const removedName = window.spinnerOptions[winnerIdx];
                window.spinnerOptions.splice(winnerIdx, 1);
                refreshSpinnerOptionsUI();
                showSpinnerToast('"' + removedName + '" dihapus dari pilihan undian.');
            }
        }

        function spinAgainFromModal() {
            closeWinnerModal();
            setTimeout(() => {
                onCenterHubClick();
            }, 300);
        }

        // --- ADD OPTION MODAL ---
        // Matches UndianSpinnerScreen.kt showAddDialog AlertDialog
        function openAddSpinnerOptionModal() {
            let modalEl = document.getElementById('spinnerAddOptionModal');
            if (modalEl) modalEl.remove();

            const modalHtml = `
                <div class="modal fade" id="spinnerAddOptionModal" tabindex="-1" aria-hidden="true">
                    <div class="modal-dialog modal-dialog-centered" style="max-width: 420px;">
                        <div class="modal-content shadow-lg p-3" style="border-radius: 24px; background-color: var(--card-bg, #ffffff);">
                            <div class="modal-header border-0 pb-1">
                                <h5 class="modal-title fw-bold d-flex align-items-center gap-2" style="color: #0F766E;">
                                    <i class="bi bi-plus-circle-fill text-teal"></i> Tambah Opsi Undian
                                </h5>
                                <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close"></button>
                            </div>
                            <div class="modal-body py-2">
                                <p class="text-secondary small mb-3">
                                    Masukkan teks pilihan atau nama yang ingin dimasukkan ke dalam roda undian:
                                </p>
                                <div class="mb-3">
                                    <input type="text" id="spinnerNewOptionInput" class="form-control form-control-dark py-2" placeholder="Contoh: Nasi Goreng" autofocus onkeydown="if(event.key==='Enter') confirmAddSpinnerOption()">
                                </div>
                            </div>
                            <div class="modal-footer border-0 pt-0 d-flex justify-content-end gap-2">
                                <button type="button" class="btn btn-outline-secondary px-3" data-bs-dismiss="modal">Batal</button>
                                <button type="button" class="btn btn-brand px-4 fw-bold" onclick="confirmAddSpinnerOption()">Tambah</button>
                            </div>
                        </div>
                    </div>
                </div>
            `;

            const modalsContainer = document.getElementById('spinnerModalsContainer');
            if (modalsContainer) {
                const tempDiv = document.createElement('div');
                tempDiv.innerHTML = modalHtml;
                modalsContainer.appendChild(tempDiv.firstElementChild);

                const modal = new bootstrap.Modal(document.getElementById('spinnerAddOptionModal'));
                modal.show();

                setTimeout(() => {
                    const input = document.getElementById('spinnerNewOptionInput');
                    if (input) input.focus();
                }, 300);
            }
        }

        function confirmAddSpinnerOption() {
            const input = document.getElementById('spinnerNewOptionInput');
            if (!input) return;
            const text = input.value.trim();

            if (!text) {
                showSpinnerToast("Input tidak boleh kosong!", true);
                return;
            }

            if (window.spinnerOptions.includes(text)) {
                showSpinnerToast("Opsi sudah ada!", true);
                return;
            }

            window.spinnerOptions.push(text);
            refreshSpinnerOptionsUI();

            const modalEl = document.getElementById('spinnerAddOptionModal');
            if (modalEl) {
                const bsModal = bootstrap.Modal.getInstance(modalEl);
                if (bsModal) bsModal.hide();
            }

            showSpinnerToast('Opsi "' + text + '" berhasil ditambahkan.');
        }

        function quickAddSpinnerOption() {
            const input = document.getElementById('spinnerQuickInput');
            if (!input) return;
            const text = input.value.trim();
            if (!text) return;

            if (window.spinnerOptions.includes(text)) {
                showSpinnerToast("Opsi sudah ada!", true);
                return;
            }

            window.spinnerOptions.push(text);
            input.value = '';
            refreshSpinnerOptionsUI();
            showSpinnerToast('Opsi "' + text + '" berhasil ditambahkan.');
        }

        function editSpinnerItem(idx) {
            const current = window.spinnerOptions[idx] || '';
            const newName = prompt("Ubah nama pilihan:", current);
            if (newName !== null) {
                const trimmed = newName.trim();
                if (!trimmed) {
                    showSpinnerToast("Nama tidak boleh kosong!", true);
                    return;
                }
                window.spinnerOptions[idx] = trimmed;
                refreshSpinnerOptionsUI();
                showSpinnerToast("Opsi berhasil diperbarui.");
            }
        }

        function deleteSpinnerItem(idx) {
            if (window.isSpinning) return;
            if (idx >= 0 && idx < window.spinnerOptions.length) {
                const name = window.spinnerOptions[idx];
                window.spinnerOptions.splice(idx, 1);
                refreshSpinnerOptionsUI();
                showSpinnerToast('"' + name + '" dihapus.');
            }
        }

        function shuffleSpinnerOptions() {
            if (window.isSpinning) return;
            if (!window.spinnerOptions || window.spinnerOptions.length <= 1) {
                showSpinnerToast("Minimal 2 opsi untuk mengacak!", true);
                return;
            }

            // Fisher-Yates shuffle
            for (let i = window.spinnerOptions.length - 1; i > 0; i--) {
                const j = Math.floor(Math.random() * (i + 1));
                const temp = window.spinnerOptions[i];
                window.spinnerOptions[i] = window.spinnerOptions[j];
                window.spinnerOptions[j] = temp;
            }

            refreshSpinnerOptionsUI();
            showSpinnerToast("Urutan pilihan berhasil diacak!");
        }

        function clearAllSpinnerOptions() {
            if (window.isSpinning) return;
            if (!window.spinnerOptions || window.spinnerOptions.length === 0) return;

            showCustomConfirm({
                title: 'Bersihkan Daftar Spinner',
                message: 'Apakah Anda yakin ingin menghapus semua opsi dari roda undian?',
                confirmText: 'Reset Semua',
                onConfirm: () => {
                    window.spinnerOptions = [];
                    refreshSpinnerOptionsUI();
                    showSpinnerToast("Daftar roda undian berhasil dikosongkan.");
                }
            });
        }

        // --- PRESETS LOADER ---
        function loadSpinnerPreset(type) {
            if (window.isSpinning) return;
            let list = [];
            if (type === 'nama') {
                list = ["Ahmad", "Budi", "Citra", "Dian", "Eko", "Fitri", "Gunawan", "Hani"];
            } else if (type === 'doorprize') {
                list = ["Hadiah Utama", "Voucher 500rb", "Kaos Eksklusif", "Tumbler Keren", "Payung Lipat", "Mug Cantik", "Coba Lagi"];
            } else if (type === 'kuliner') {
                list = ["Nasi Goreng", "Mie Ayam", "Sate Ayam", "Bakso Malang", "Soto Betawi", "Gado-Gado", "Ayam Geprek", "Martabak"];
            } else if (type === 'angka') {
                list = ["Angka 1", "Angka 2", "Angka 3", "Angka 4", "Angka 5", "Angka 6", "Angka 7", "Angka 8"];
            } else if (type === 'yatidak') {
                list = ["YA", "TIDAK", "MUNGKIN", "COBA LAGI"];
            }

            window.spinnerOptions = list;
            refreshSpinnerOptionsUI();
            showSpinnerToast("Preset undian berhasil dimuat!");
        }
        """
    }
}
