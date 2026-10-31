package com.example.web

object DashboardWebScript {
    fun getScript(dol: String): String {
        return """
        // =========================================================================
        // --- 0. DASHBOARD UTAMA MY SINGGHARA (100% OFFLINE & ASLI PERSIS HOME) ---
        // =========================================================================

        // --- A. JAVANESE & HIJRI CALENDAR HELPER (100% OFFLINE PURE JS) ---
        const DINA_JAWA = ["Ahad", "Senen", "Selasa", "Rebo", "Kemis", "Jemuwah", "Setu"];
        const PASARAN_JAWA = ["Legi", "Pahing", "Pon", "Wage", "Kliwon"];
        const WUKU_JAWA = [
            "Sinta", "Landep", "Wukir", "Kurantil", "Tolu", "Gumbreg", "Warigalit",
            "Warigagung", "Julungwangi", "Sungsang", "Galungan", "Kuningan", "Langkir",
            "Mandhasiya", "Julungpujut", "Pahang", "Kuruwelut", "Marakeh", "Tambir",
            "Medhangkungan", "Maktal", "Wuye", "Manail", "Prangbakat", "Bala", "Wugu",
            "Wayang", "Kulawu", "Dukut", "Watugunung"
        ];

        const HIJRI_MONTHS = [
            "Muharram", "Safar", "Rabi'ul Awwal", "Rabi'ul Akhir",
            "Jumadil Awwal", "Jumadil Akhir", "Rajab", "Sya'ban",
            "Ramadhan", "Syawwal", "Dzulqa'dah", "Dzulhijjah"
        ];

        const INDO_MONTHS = [
            "Januari", "Februari", "Maret", "April", "Mei", "Juni",
            "Juli", "Agustus", "September", "Oktober", "November", "Desember"
        ];

        const INDO_DAYS = ["Minggu", "Senin", "Selasa", "Rabu", "Kamis", "Jumat", "Sabtu"];
        function escapeHtml(text) {
            if (!text) return '';
            return text.toString()
                .replace(/&/g, "&amp;")
                .replace(/</g, "&lt;")
                .replace(/>/g, "&gt;")
                .replace(/"/g, "&quot;")
                .replace(/'/g, "&#039;");
        }
        const safeEscape = escapeHtml;
        if (typeof window !== 'undefined') {
            window.escapeHtml = escapeHtml;
        }

        function getJavaneseDate(year, month, day) {
            const y = month <= 2 ? year - 1 : year;
            const m = month <= 2 ? month + 12 : month;
            const a = Math.floor(y / 100);
            const b = Math.floor(a / 4);
            const c = 2 - a + b;
            const e = Math.floor(365.25 * (y + 4716));
            const f = Math.floor(30.6001 * (m + 1));
            const jdn = c + day + e + f - 1524;

            const pasaranIdx = Math.floor(((jdn % 5 + 5) % 5));
            const dinaIdx = Math.floor((((jdn + 1) % 7 + 7) % 7));
            const wukuIdx = Math.floor((Math.floor((jdn + 2) / 7) % 30 + 30) % 30);

            return {
                dina: DINA_JAWA[dinaIdx],
                pasaran: PASARAN_JAWA[pasaranIdx],
                wuku: WUKU_JAWA[wukuIdx]
            };
        }

        function getHijriDate(year, month, day, offsetDays = -1) {
            const y = month <= 2 ? year - 1 : year;
            const m = month <= 2 ? month + 12 : month;
            const a = Math.floor(y / 100);
            const b = Math.floor(a / 4);
            const c = 2 - a + b;
            const e = Math.floor(365.25 * (y + 4716));
            const f = Math.floor(30.6001 * (m + 1));
            const jd = c + day + e + f - 1524 + offsetDays;

            const days = jd - 1948439;
            const cycle = Math.floor(days / 10631);
            const cycleRemainder = days % 10631;

            const yearInCycle = Math.floor((30 * cycleRemainder + 11) / 10631);
            const hYear = cycle * 30 + yearInCycle + 1;

            const dayOfYearInCycle = cycleRemainder - Math.floor((yearInCycle * 10631 + 14) / 30);
            const hMonthNum = Math.floor((dayOfYearInCycle * 2) / 59) + 1;
            const hDay = Math.floor(dayOfYearInCycle - Math.floor(((hMonthNum - 1) * 59 + 1) / 2)) + 1;

            const finalMonthIdx = Math.min(Math.max(hMonthNum - 1, 0), 11);
            const finalDay = Math.max(hDay, 1);

            return `${dol}{finalDay} ${dol}{HIJRI_MONTHS[finalMonthIdx]} ${dol}{hYear} H`;
        }

        // --- B. HOLIDAYS & OBSERVANCES DATABASE (OFFLINE ACCURATE) ---
        const DYNAMIC_HOLIDAYS = {
            // 2025
            "2025-01-27": "Isra Mikraj Nabi Muhammad SAW",
            "2025-01-29": "Tahun Baru Imlek 2576 Kongzili",
            "2025-03-29": "Hari Suci Nyepi (Tahun Baru Saka 1947)",
            "2025-03-31": "Hari Raya Idul Fitri 1446 H",
            "2025-04-01": "Hari Raya Idul Fitri 1446 H (Hari Kedua)",
            "2025-04-18": "Wafat Yesus Kristus (Jumat Agung)",
            "2025-04-20": "Kebangkitan Yesus Kristus (Hari Paskah)",
            "2025-05-12": "Hari Raya Waisak 2569 BE",
            "2025-05-29": "Kenaikan Yesus Kristus",
            "2025-06-06": "Hari Raya Idul Adha 1446 H",
            "2025-06-27": "Tahun Baru Islam 1447 H",
            "2025-09-05": "Maulid Nabi Muhammad SAW",
            // 2026
            "2026-01-16": "Isra Mi'raj Nabi Muhammad SAW",
            "2026-02-17": "Tahun Baru Imlek 2577 Kongzili",
            "2026-03-19": "Hari Suci Nyepi (Tahun Baru Saka 1948)",
            "2026-03-20": "Hari Raya Idul Fitri 1447 H",
            "2026-03-21": "Hari Raya Idul Fitri 1447 H (Hari Kedua)",
            "2026-04-03": "Wafat Yesus Kristus (Jumat Agung)",
            "2026-04-05": "Kebangkitan Yesus Kristus (Paskah)",
            "2026-05-14": "Kenaikan Yesus Kristus",
            "2026-05-27": "Hari Raya Idul Adha 1447 H",
            "2026-05-31": "Hari Raya Waisak 2570 BE",
            "2026-06-16": "Tahun Baru Islam 1448 H",
            "2026-08-25": "Maulid Nabi Muhammad SAW 1448 H",
            // 2027
            "2027-01-06": "Isra Mi'raj Nabi Muhammad SAW",
            "2027-02-06": "Tahun Baru Imlek 2578 Kongzili",
            "2027-03-09": "Hari Suci Nyepi (Tahun Baru Saka 1949)",
            "2027-03-10": "Hari Raya Idul Fitri 1448 H",
            "2027-03-11": "Hari Raya Idul Fitri 1448 H (Hari Kedua)",
            "2027-03-26": "Wafat Yesus Kristus (Jumat Agung)",
            "2027-03-28": "Kebangkitan Yesus Kristus (Paskah)",
            "2027-05-06": "Kenaikan Yesus Kristus",
            "2027-05-16": "Hari Raya Idul Adha 1448 H",
            "2027-05-20": "Hari Raya Waisak 2571 BE",
            "2027-06-06": "Tahun Baru Islam 1449 H",
            "2027-08-14": "Maulid Nabi Muhammad SAW 1449 H"
        };

        const OBSERVANCES_MAP = {
            "01-03": "Hari Amal Bhakti Kementerian Agama",
            "01-05": "Hari Korps Wanita Angkatan Laut (KOWAL)",
            "01-10": "Hari Gerakan Satu Juta Pohon",
            "01-15": "Hari Dharma Samudera",
            "01-25": "Hari Gizi dan Makanan Nasional",
            "02-09": "Hari Pers Nasional (HPN)",
            "02-14": "Hari Peringatan Pemberontakan PETA",
            "02-21": "Hari Peduli Sampah Nasional",
            "02-28": "Hari Gizi Nasional",
            "03-01": "Hari Penegakan Kedaulatan Negara",
            "03-08": "Hari Perempuan Internasional",
            "03-09": "Hari Musik Nasional",
            "03-22": "Hari Air Sedunia",
            "03-24": "Hari Peringatan Bandung Lautan Api",
            "03-30": "Hari Film Nasional",
            "04-07": "Hari Kesehatan Internasional",
            "04-21": "Hari Kartini",
            "04-22": "Hari Bumi Sedunia",
            "04-28": "Hari Puisi Nasional",
            "05-02": "Hari Pendidikan Nasional (Hardiknas)",
            "05-17": "Hari Buku Nasional",
            "05-20": "Hari Kebangkitan Nasional (Harkitnas)",
            "05-29": "Hari Lanjut Usia Nasional",
            "06-05": "Hari Lingkungan Hidup Sedunia",
            "06-21": "Hari Krida Pertanian",
            "06-24": "Hari Bidan Nasional",
            "06-29": "Hari Keluarga Nasional (Harganas)",
            "07-01": "Hari Bhayangkara (Polri)",
            "07-05": "Hari Bank Indonesia",
            "07-12": "Hari Koperasi Indonesia",
            "07-22": "Hari Kejaksaan Republik Indonesia",
            "07-23": "Hari Anak Nasional",
            "08-10": "Hari Veteran Nasional & Hakteknas",
            "08-14": "Hari Pramuka Nasional",
            "08-18": "Hari Konstitusi Republik Indonesia",
            "08-19": "Hari Departemen Luar Negeri RI",
            "09-01": "Hari Polisi Wanita (Polwan)",
            "09-04": "Hari Pelanggan Nasional",
            "09-09": "Hari Olahraga Nasional (Haornas)",
            "09-11": "Hari Radio Republik Indonesia (RRI)",
            "09-17": "Hari Palang Merah Indonesia (PMI)",
            "09-24": "Hari Tani Nasional",
            "09-27": "Hari Pos dan Telekomunikasi (Postel)",
            "09-28": "Hari Kereta Api Nasional",
            "09-30": "Hari Peringatan G30S/PKI",
            "10-01": "Hari Kesaktian Pancasila",
            "10-02": "Hari Batik Nasional",
            "10-05": "Hari Tentara Nasional Indonesia (TNI)",
            "10-16": "Hari Pangan Sedunia",
            "10-22": "Hari Santri Nasional",
            "10-24": "Hari Dokter Nasional (IDI)",
            "10-27": "Hari Listrik Nasional",
            "10-28": "Hari Sumpah Pemuda",
            "10-30": "Hari Keuangan Nasional",
            "11-10": "Hari Pahlawan Nasional",
            "11-12": "Hari Kesehatan Nasional & Hari Ayah",
            "11-14": "Hari Korps Marinir & Hari Diabetes",
            "11-25": "Hari Guru Nasional & HUT PGRI",
            "11-28": "Hari Menanam Pohon Indonesia",
            "11-29": "Hari KORPRI",
            "12-01": "Hari AIDS Sedunia",
            "12-03": "Hari Penyandang Disabilitas",
            "12-04": "Hari Artileri Nasional",
            "12-09": "Hari Anti Korupsi Sedunia",
            "12-10": "Hari Hak Asasi Manusia (HAM)",
            "12-13": "Hari Nusantara",
            "12-15": "Hari Juang Kartika TNI AD",
            "12-19": "Hari Bela Negara",
            "12-22": "Hari Ibu"
        };

        function getHolidayForDate(year, month, day) {
            const dateKey = `${dol}{year}-${dol}{String(month).padStart(2, '0')}-${dol}{String(day).padStart(2, '0')}`;
            if (month === 1 && day === 1) return "Tahun Baru Masehi";
            if (month === 5 && day === 1) return "Hari Buruh Internasional";
            if (month === 6 && day === 1) return "Hari Lahir Pancasila";
            if (month === 8 && day === 17) return "Hari Kemerdekaan RI";
            if (month === 12 && day === 25) return "Hari Raya Natal";
            if (month === 12 && day === 26) return "Cuti Bersama Hari Raya Natal";
            return DYNAMIC_HOLIDAYS[dateKey] || null;
        }

        function getObservanceForDate(month, day) {
            const key = `${dol}{String(month).padStart(2, '0')}-${dol}{String(day).padStart(2, '0')}`;
            return OBSERVANCES_MAP[key] || null;
        }

        // --- C. ASTRONOMICAL PRAYER TIMES CALCULATION (KEMENAG RI FORMULA) ---
        function getPrayerTimes(tz = 'WIB', targetDate = new Date()) {
            let lat = -6.2088, lng = 106.8456, offset = 7.0; // WIB (Jakarta)
            if (tz === 'WITA') {
                lat = -5.1477; lng = 119.4327; offset = 8.0; // Makassar
            } else if (tz === 'WIT') {
                lat = -3.6954; lng = 128.1814; offset = 9.0; // Ambon
            }

            const start = new Date(targetDate.getFullYear(), 0, 1);
            const dayOfYear = Math.floor((targetDate - start) / (24 * 60 * 60 * 1000)) + 1;

            const s = 2.0 * Math.PI * (dayOfYear - 1.0) / 365.25;
            const declination = 0.006918 - 0.399912 * Math.cos(s) + 0.070257 * Math.sin(s) - 0.006758 * Math.cos(2.0 * s) + 0.000907 * Math.sin(2.0 * s) - 0.002697 * Math.cos(3.0 * s) + 0.00148 * Math.sin(3.0 * s);
            const eot = 229.18 * (0.000075 + 0.001868 * Math.cos(s) - 0.032077 * Math.sin(s) - 0.014615 * Math.cos(2.0 * s) - 0.040849 * Math.sin(2.0 * s) - 0.000602 * Math.cos(3.0 * s) - 0.00085 * Math.sin(3.0 * s));

            const transit = 12.0 + offset - (lng / 15.0) - (eot / 60.0);

            function hourAngle(angle) {
                const aRad = angle * Math.PI / 180.0;
                const latRad = lat * Math.PI / 180.0;
                const cosH = (Math.sin(aRad) - Math.sin(latRad) * Math.sin(declination)) / (Math.cos(latRad) * Math.cos(declination));
                if (cosH >= -1.1 && cosH <= 1.1) {
                    const clampedCosH = Math.max(-1.0, Math.min(1.0, cosH));
                    return (Math.acos(clampedCosH) * 180.0 / Math.PI) / 15.0;
                }
                return 6.0;
            }

            const hFajr = hourAngle(-20.0);
            const subuhDecimal = transit - hFajr;
            const hIsha = hourAngle(-18.0);
            const isyaDecimal = transit + hIsha;
            const hSunset = hourAngle(-1.0);
            const maghribDecimal = transit + hSunset;
            const dzuhurDecimal = transit + (2.0 / 60.0);

            const latDiffRad = Math.abs(lat - (declination * 180.0 / Math.PI)) * Math.PI / 180.0;
            const altitudeAsrRad = Math.atan(1.0 / (1.0 + Math.tan(latDiffRad)));
            const cosHAsr = (Math.sin(altitudeAsrRad) - Math.sin(lat * Math.PI / 180.0) * Math.sin(declination)) / (Math.cos(lat * Math.PI / 180.0) * Math.cos(declination));
            const hAsr = (cosHAsr >= -1.1 && cosHAsr <= 1.1)
                ? (Math.acos(Math.max(-1.0, Math.min(1.0, cosHAsr))) * 180.0 / Math.PI) / 15.0
                : 3.0;
            const asharDecimal = transit + hAsr;
            const imsakDecimal = subuhDecimal - (10.0 / 60.0);

            function formatTime(dec) {
                const totalMinutes = Math.round(dec * 60);
                let h = Math.floor(totalMinutes / 60) % 24;
                let m = totalMinutes % 60;
                if (h < 0) h += 24;
                if (m < 0) m += 60;
                return `${dol}{String(h).padStart(2, '0')}:${dol}{String(m).padStart(2, '0')}`;
            }

            return {
                imsak: formatTime(imsakDecimal),
                subuh: formatTime(subuhDecimal),
                dzuhur: formatTime(dzuhurDecimal),
                ashar: formatTime(asharDecimal),
                maghrib: formatTime(maghribDecimal),
                isya: formatTime(isyaDecimal)
            };
        }

        // --- D. OFFLINE AUDIO ENGINE: REAL AZAN AUDIO & CHIME (100% OFFLINE SYNCHRONIZED) ---
        let adzanAudioCtx = null;
        let isAdzanPlaying = false;
        let activePlayingPrayerName = null;
        let adzanTimeouts = [];
        let lastTriggeredPrayerMinuteKey = null;

        if (!window.azanAudioPlayer) {
            try {
                if (typeof Audio !== 'undefined') {
                    window.azanAudioPlayer = new Audio();
                }
            } catch(e) {}
        }

        // Unlock audio on first page click (standard browser media policy)
        document.addEventListener('click', function unlockAudioOnFirstClick() {
            try {
                if (window.azanAudioPlayer) {
                    window.azanAudioPlayer.load();
                }
            } catch(e){}
            document.removeEventListener('click', unlockAudioOnFirstClick);
        }, { once: true });

        function stopActiveAzan() {
            adzanTimeouts.forEach(t => clearTimeout(t));
            adzanTimeouts = [];

            if (window.azanAudioPlayer) {
                try {
                    window.azanAudioPlayer.pause();
                    window.azanAudioPlayer.currentTime = 0;
                } catch(e){}
            }

            if (adzanAudioCtx) {
                try { adzanAudioCtx.close(); } catch(e){}
                adzanAudioCtx = null;
            }

            isAdzanPlaying = false;
            activePlayingPrayerName = null;

            const btn = document.getElementById('btnAdzanTest');
            if (btn) {
                btn.innerHTML = '<i class="bi bi-volume-up-fill me-1"></i> Uji Azan';
                btn.className = 'btn btn-sm btn-outline-primary rounded-pill px-2 py-1';
            }

            const alertBanner = document.getElementById('activeAzanAlertBanner');
            if (alertBanner) {
                alertBanner.classList.add('d-none');
            }
        }

        function playPrayerAzan(prayerName, isTest = false) {
            stopActiveAzan();
            isAdzanPlaying = true;
            activePlayingPrayerName = prayerName || 'Azan';

            // Show Real-time Active Azan Alert Banner with Stop Button
            const alertBanner = document.getElementById('activeAzanAlertBanner');
            const alertTitle = document.getElementById('activeAzanPrayerTitle');
            const alertMsg = document.getElementById('activeAzanPrayerMsg');
            if (alertBanner) {
                if (alertTitle) alertTitle.textContent = isTest ? `Uji Suara Azan (${dol}{activePlayingPrayerName})` : `Waktu Sholat ${dol}{activePlayingPrayerName} Telah Tiba!`;
                if (alertMsg) alertMsg.textContent = isTest ? 'Sedang memutar audio azan Madinah My Singghara (100% Offline)' : 'Waktu sholat telah tiba. Mari laksanakan ibadah sholat tepat waktu.';
                alertBanner.classList.remove('d-none');
            }

            const btn = document.getElementById('btnAdzanTest');
            if (btn && isTest) {
                btn.innerHTML = '<i class="bi bi-stop-circle-fill me-1"></i> Berhenti';
                btn.className = 'btn btn-sm btn-danger rounded-pill px-2 py-1';
            }

            const cleanPrayer = (prayerName || 'subuh').toLowerCase();
            const audioSrc = `/api/azan/audio?prayer=${dol}{encodeURIComponent(cleanPrayer)}&t=${dol}{Date.now()}`;

            try {
                window.azanAudioPlayer.src = audioSrc;
                window.azanAudioPlayer.onended = () => {
                    stopActiveAzan();
                };
                window.azanAudioPlayer.onerror = (e) => {
                    console.warn('MP3 playback fallback to synthesized melodic chime:', e);
                    playAdzanSynthFallback();
                };

                const playPromise = window.azanAudioPlayer.play();
                if (playPromise !== undefined) {
                    playPromise.catch((err) => {
                        console.warn('Auto-play blocked or offline error, falling back to Web Audio synth:', err);
                        playAdzanSynthFallback();
                    });
                }
            } catch(e) {
                playAdzanSynthFallback();
            }
        }

        function playAdzanSynthFallback() {
            try {
                const AudioContextClass = window.AudioContext || window.webkitAudioContext;
                if (!AudioContextClass) return;
                adzanAudioCtx = new AudioContextClass();

                // Melodic spiritual chime sequence (Adzan tone pattern)
                const notes = [
                    { f: 440.0, d: 0.8 }, // A4
                    { f: 523.25, d: 0.8 }, // C5
                    { f: 587.33, d: 1.2 }, // D5
                    { f: 659.25, d: 1.5 }, // E5
                    { f: 587.33, d: 0.9 }, // D5
                    { f: 523.25, d: 0.9 }, // C5
                    { f: 440.0, d: 1.6 }  // A4
                ];

                let currentTime = adzanAudioCtx.currentTime + 0.05;
                notes.forEach((note) => {
                    const osc = adzanAudioCtx.createOscillator();
                    const gain = adzanAudioCtx.createGain();

                    osc.type = 'sine';
                    osc.frequency.setValueAtTime(note.f, currentTime);

                    gain.gain.setValueAtTime(0.001, currentTime);
                    gain.gain.linearRampToValueAtTime(0.35, currentTime + 0.1);
                    gain.gain.exponentialRampToValueAtTime(0.0001, currentTime + note.d);

                    osc.connect(gain);
                    gain.connect(adzanAudioCtx.destination);

                    osc.start(currentTime);
                    osc.stop(currentTime + note.d);

                    currentTime += note.d + 0.15;
                });

                const totalDuration = (currentTime - adzanAudioCtx.currentTime) * 1000;
                const finishTimeout = setTimeout(() => {
                    stopActiveAzan();
                }, totalDuration);
                adzanTimeouts.push(finishTimeout);
            } catch(err) {
                console.warn('Web Audio error:', err);
                stopActiveAzan();
            }
        }

        function playSystemBeepChime(prayerName) {
            try {
                const AudioContextClass = window.AudioContext || window.webkitAudioContext;
                if (!AudioContextClass) return;
                const ctx = new AudioContextClass();
                const now = ctx.currentTime;

                [587.33, 880.0, 1174.66].forEach((freq, idx) => {
                    const osc = ctx.createOscillator();
                    const gain = ctx.createGain();
                    osc.type = 'sine';
                    osc.frequency.setValueAtTime(freq, now + idx * 0.12);
                    gain.gain.setValueAtTime(0.2, now + idx * 0.12);
                    gain.gain.exponentialRampToValueAtTime(0.001, now + idx * 0.12 + 0.3);
                    osc.connect(gain);
                    gain.connect(ctx.destination);
                    osc.start(now + idx * 0.12);
                    osc.stop(now + idx * 0.12 + 0.35);
                });

                // Show alert banner briefly
                const alertBanner = document.getElementById('activeAzanAlertBanner');
                const alertTitle = document.getElementById('activeAzanPrayerTitle');
                const alertMsg = document.getElementById('activeAzanPrayerMsg');
                if (alertBanner) {
                    if (alertTitle) alertTitle.textContent = `Pengingat Waktu Sholat ${dol}{prayerName || ""}`;
                    if (alertMsg) alertMsg.textContent = `Waktu sholat ${dol}{prayerName || ""} telah tiba. Mari bersiap ibadah.`;
                    alertBanner.classList.remove('d-none');
                    setTimeout(() => {
                        alertBanner.classList.add('d-none');
                    }, 8000);
                }
            } catch(e){}
        }

        function playAdzanSoundTest() {
            if (isAdzanPlaying) {
                stopActiveAzan();
            } else {
                playPrayerAzan('Subuh', true);
            }
        }

        // --- E. GLOBAL DASHBOARD STATE ---
        window.dashboardState = {
            activeTimeZone: localStorage.getItem('singghara_timezone') || 'WIB',
            viewCalendarMonth: new Date().getMonth(),
            viewCalendarYear: new Date().getFullYear(),
            weatherData: null,
            weatherLoading: false,
            weatherLocation: 'DKI Jakarta (Standard GPS)',
            allAgendas: [],
            clockFontSizeSp: 52,
            clockFontName: 'Technology Bold (Bawaan My Singghara)',
            hasCustomClockFont: false,
            isClockResizing: false,
            currentDay: '',
            currentDate: '',
            jawaDina: '',
            jawaPasaran: '',
            hijriDate: '',
            prayerSchedule: null,
            prayerScheduleSource: 'Kemenag RI (Bimas Islam)',
            prayerLocationText: 'Lokasi Sinkronisasi',
            prayerSoundSettings: { Imsak: 1, Subuh: 2, Dzuhur: 2, Ashar: 2, Maghrib: 2, Isya: 2 }
        };

        // Live Font Loader for My Singghara Digital Clock
        function reloadActiveClockFont(timestamp) {
            const v = timestamp || Date.now();
            const styleId = 'singghara-custom-clock-font-style';
            let styleEl = document.getElementById(styleId);
            if (!styleEl) {
                styleEl = document.createElement('style');
                styleEl.id = styleId;
                document.head.appendChild(styleEl);
            }
            styleEl.textContent = `
                @font-face {
                    font-family: 'SinggharaClockFont';
                    src: url('/api/clock-font?v=${dol}{v}') format('truetype');
                    font-weight: normal;
                    font-style: normal;
                }
                .singghara-clock-font {
                    font-family: 'SinggharaClockFont', 'Technology', monospace !important;
                }
            `;
            const bigClock = document.getElementById('bigDigitalClock');
            if (bigClock) {
                bigClock.style.fontFamily = "'SinggharaClockFont', 'Technology', monospace";
            }
        }

        // Initialize active clock font immediately
        reloadActiveClockFont();

        // Timezone Switcher Handler (Instantly changes digital clock, prayer times & UI without refresh)
        async function setDashboardTimezone(tz) {
            if (!['WIB', 'WITA', 'WIT'].includes(tz)) return;
            window.dashboardState.activeTimeZone = tz;
            localStorage.setItem('singghara_timezone', tz);
            
            // 1. Immediately update digital clock & date display
            renderDashboardClock();

            // 2. Update badges and labels across dashboard
            const tzBadge = document.getElementById('tzIndicatorBadge');
            if (tzBadge) tzBadge.textContent = tz;

            const prayerTzLabel = document.getElementById('prayerTzLabel');
            if (prayerTzLabel) prayerTzLabel.textContent = tz;

            // 3. Update prayer times and weather for the selected timezone
            renderDashboardPrayerTimes();
            renderDashboardWeatherCard();

            // 4. Hide modal
            const modalEl = document.getElementById('timezoneSelectModal');
            if (modalEl) {
                const modal = bootstrap.Modal.getInstance(modalEl);
                if (modal) modal.hide();
            }

            // 5. Background sync to server
            try {
                fetch('/api/dashboard', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ activeTimeZone: tz })
                }).catch(() => {});
            } catch(e) {}
        }

        function renderTimezoneModalContent() {
            const body = document.getElementById('timezoneModalBodyContent');
            if (!body) return;
            const currentTz = window.dashboardState.activeTimeZone || 'WIB';
            const timezones = [
                {
                    id: 'WIB',
                    code: 'WIB',
                    offset: 'UTC+7',
                    fullName: 'Waktu Indonesia Barat',
                    regions: 'Sumatera, Jawa, Madura, Kalbar, Kalteng'
                },
                {
                    id: 'WITA',
                    code: 'WITA',
                    offset: 'UTC+8',
                    fullName: 'Waktu Indonesia Tengah',
                    regions: 'Sulawesi, Bali, NTB, NTT, Kalsel, Kaltim, Kaltara'
                },
                {
                    id: 'WIT',
                    code: 'WIT',
                    offset: 'UTC+9',
                    fullName: 'Waktu Indonesia Timur',
                    regions: 'Maluku, Maluku Utara, Seluruh Papua'
                }
            ];
            let html = '';
            timezones.forEach(t => {
                const isSelected = (t.id === currentTz);
                html += `
                    <div class="card p-3 rounded-4 mb-2 cursor-pointer border ${dol}{isSelected ? 'border-2 border-primary bg-primary bg-opacity-10 shadow-sm' : 'border-color'}" 
                         onclick="setDashboardTimezone('${dol}{t.id}')" style="transition: all 0.2s ease;">
                        <div class="d-flex align-items-center gap-3">
                            <input class="form-check-input mt-0" type="radio" name="tzRadioOptions" ${dol}{isSelected ? 'checked' : ''} style="cursor: pointer; width: 1.25em; height: 1.25em;">
                            <div class="flex-grow-1">
                                <div class="d-flex align-items-center justify-content-between mb-1">
                                    <span class="fw-black ${dol}{isSelected ? 'text-primary' : 'text-main'}" style="font-size: 16px;">${dol}{t.code}</span>
                                    <span class="badge ${dol}{isSelected ? 'bg-primary text-white' : 'bg-secondary bg-opacity-25 text-main'} rounded-2 px-2 py-1" style="font-size: 11px;">
                                        ${dol}{t.offset}
                                    </span>
                                </div>
                                <div class="fw-bold text-main" style="font-size: 13px;">${dol}{t.fullName}</div>
                                <div class="text-secondary small mt-1" style="font-size: 11px;">Wilayah: ${dol}{t.regions}</div>
                            </div>
                        </div>
                    </div>
                `;
            });
            body.innerHTML = html;
        }

        function openTimezoneModal() {
            renderTimezoneModalContent();
            const modalEl = document.getElementById('timezoneSelectModal');
            if (modalEl) {
                const modal = bootstrap.Modal.getOrCreateInstance(modalEl);
                modal.show();
            }
        }

        // --- CLOCK FONT & RESIZING HANDLERS ---
        let clockClickTimeout = null;
        let lastClockTouchTime = 0;

        function handleClockClick(e) {
            if (window.dashboardState.isClockResizing) return;
            if (clockClickTimeout) {
                clearTimeout(clockClickTimeout);
                clockClickTimeout = null;
            }
            clockClickTimeout = setTimeout(() => {
                openClockFontModal();
            }, 250);
        }

        function handleClockDblClick(e) {
            if (e) {
                e.preventDefault();
                e.stopPropagation();
            }
            if (clockClickTimeout) {
                clearTimeout(clockClickTimeout);
                clockClickTimeout = null;
            }
            toggleClockResizing();
        }

        function handleClockTouchEnd(e) {
            const now = Date.now();
            if (now - lastClockTouchTime < 350) {
                if (e) {
                    e.preventDefault();
                    e.stopPropagation();
                }
                if (clockClickTimeout) {
                    clearTimeout(clockClickTimeout);
                    clockClickTimeout = null;
                }
                lastClockTouchTime = 0;
                toggleClockResizing();
            } else {
                lastClockTouchTime = now;
            }
        }

        function toggleClockResizing(forceState) {
            if (typeof forceState === 'boolean') {
                window.dashboardState.isClockResizing = forceState;
            } else {
                window.dashboardState.isClockResizing = !window.dashboardState.isClockResizing;
            }
            const isResizing = window.dashboardState.isClockResizing;
            const handles = document.getElementById('clockResizeHandles');
            const controls = document.getElementById('clockResizeControls');
            const wrapper = document.getElementById('clockDisplayWrapper');

            if (handles) {
                if (isResizing) handles.classList.remove('d-none');
                else handles.classList.add('d-none');
            }
            if (controls) {
                if (isResizing) controls.classList.remove('d-none');
                else controls.classList.add('d-none');
            }
            if (wrapper) {
                if (isResizing) {
                    wrapper.style.border = '2px dashed var(--bs-primary, #0d6efd)';
                    wrapper.style.padding = '14px 28px';
                    wrapper.style.backgroundColor = 'rgba(13, 110, 253, 0.05)';
                } else {
                    wrapper.style.border = 'none';
                    wrapper.style.padding = '6px 16px';
                    wrapper.style.backgroundColor = 'transparent';
                }
            }
            updateClockSizeIndicator();
        }

        function updateClockSizeIndicator() {
            const ind = document.getElementById('clockSizeIndicator');
            if (ind) {
                const size = Math.round(window.dashboardState.clockFontSizeSp || 52);
                ind.textContent = `Ukuran: ${dol}{size} sp`;
            }
        }

        let resizeSaveTimeout = null;
        function changeClockFontSize(delta) {
            const cur = window.dashboardState.clockFontSizeSp || 52;
            const next = Math.min(96, Math.max(24, Math.round(cur + delta)));
            window.dashboardState.clockFontSizeSp = next;

            const clock = document.getElementById('bigDigitalClock');
            if (clock) {
                clock.style.fontSize = `${dol}{next}px`;
            }
            updateClockSizeIndicator();

            if (resizeSaveTimeout) clearTimeout(resizeSaveTimeout);
            resizeSaveTimeout = setTimeout(() => {
                fetch('/api/clock-fonts', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ action: 'resize', size: next })
                }).catch(() => {});
            }, 300);
        }

        function resetClockFontSize() {
            window.dashboardState.clockFontSizeSp = 52;
            const clock = document.getElementById('bigDigitalClock');
            if (clock) {
                clock.style.fontSize = '52px';
            }
            updateClockSizeIndicator();
            fetch('/api/clock-fonts', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ action: 'resize', size: 52 })
            }).catch(() => {});
        }

        async function openClockFontModal() {
            const modalEl = document.getElementById('clockFontModal');
            if (!modalEl) return;
            const modal = bootstrap.Modal.getOrCreateInstance(modalEl);
            modal.show();

            const listContainer = document.getElementById('clockFontListContainer');
            if (listContainer) {
                listContainer.innerHTML = `
                    <div class="text-center py-4">
                        <div class="spinner-border spinner-border-sm text-primary mb-2" role="status"></div>
                        <div class="text-secondary small">Memuat font dari My Singghara/Data/font/...</div>
                    </div>
                `;
            }

            try {
                const res = await fetch('/api/clock-fonts');
                const json = await res.json();
                renderClockFontModalList(json);
            } catch(err) {
                if (listContainer) {
                    listContainer.innerHTML = `
                        <div class="alert alert-danger py-2 px-3 small">
                            Gagal memuat font: ${dol}{escapeHtml(err.message)}
                        </div>
                    `;
                }
            }
        }

        function renderClockFontModalList(data) {
            const listContainer = document.getElementById('clockFontListContainer');
            if (!listContainer) return;

            const fonts = data.fonts || [];
            
            // Inject dynamic preview font-faces
            const styleTagId = 'clock-font-previews-style';
            let styleTag = document.getElementById(styleTagId);
            if (!styleTag) {
                styleTag = document.createElement('style');
                styleTag.id = styleTagId;
                document.head.appendChild(styleTag);
            }
            let cssRules = '';
            fonts.forEach((f, idx) => {
                const familyName = 'FontPreview_' + idx;
                cssRules += `
                    @font-face {
                        font-family: '${dol}{familyName}';
                        src: url('${dol}{f.previewUrl}') format('truetype');
                    }
                `;
            });
            styleTag.textContent = cssRules;

            let html = '';
            fonts.forEach((f, idx) => {
                const isSelected = !!f.isSelected;
                const familyName = 'FontPreview_' + idx;
                html += `
                    <div class="card p-3 rounded-3 mb-2 cursor-pointer border ${dol}{isSelected ? 'border-2 border-primary bg-primary bg-opacity-10' : 'border-color'}"
                         onclick="selectClockFont('${dol}{escapeHtml(f.path)}', '${dol}{escapeHtml(f.name)}')" style="transition: all 0.2s ease;">
                        <div class="d-flex justify-content-between align-items-center">
                            <div>
                                <div class="fw-bold ${dol}{isSelected ? 'text-primary' : 'text-main'} small mb-1">
                                    ${dol}{escapeHtml(f.name)}
                                </div>
                                <div class="text-primary" style="font-family: '${dol}{familyName}', 'Technology', monospace; font-size: 1.15rem; letter-spacing: 1px;">
                                    12:34:56 • Subuh 04:30
                                </div>
                            </div>
                            <div>
                                ${dol}{isSelected ? '<i class=\"bi bi-check-circle-fill text-primary fs-4\"></i>' : '<i class=\"bi bi-circle text-muted fs-4\"></i>'}
                            </div>
                        </div>
                    </div>
                `;
            });

            const resetBtnContainer = document.getElementById('clockFontResetBtnContainer');
            if (resetBtnContainer) {
                if (data.hasCustomFont) {
                    resetBtnContainer.innerHTML = `
                        <button type="button" class="btn btn-outline-secondary w-100 rounded-3 py-2 d-flex align-items-center justify-content-center gap-2" onclick="resetClockFontDefault()">
                            <i class="bi bi-arrow-counterclockwise"></i> Gunakan Font Default
                        </button>
                    `;
                    resetBtnContainer.classList.remove('d-none');
                } else {
                    resetBtnContainer.innerHTML = '';
                    resetBtnContainer.classList.add('d-none');
                }
            }

            listContainer.innerHTML = html;
        }

        async function selectClockFont(path, name) {
            try {
                const res = await fetch('/api/clock-fonts', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ action: 'select', path, name })
                });
                const json = await res.json();
                if (json.success) {
                    window.dashboardState.clockFontName = name;
                    window.dashboardState.hasCustomClockFont = (path !== '');
                    reloadActiveClockFont(Date.now());
                    renderDashboardClock();
                    const modalEl = document.getElementById('clockFontModal');
                    if (modalEl) {
                        const modal = bootstrap.Modal.getInstance(modalEl);
                        if (modal) modal.hide();
                    }
                }
            } catch(err) {
                console.error('Error selecting font:', err);
            }
        }

        async function resetClockFontDefault() {
            try {
                const res = await fetch('/api/clock-fonts', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ action: 'reset' })
                });
                const json = await res.json();
                if (json.success) {
                    window.dashboardState.clockFontName = 'Technology Bold (Bawaan My Singghara)';
                    window.dashboardState.hasCustomClockFont = false;
                    reloadActiveClockFont(Date.now());
                    renderDashboardClock();
                    const modalEl = document.getElementById('clockFontModal');
                    if (modalEl) {
                        const modal = bootstrap.Modal.getInstance(modalEl);
                        if (modal) modal.hide();
                    }
                }
            } catch(err) {
                console.error('Error resetting font:', err);
            }
        }

        async function handleClockFontUpload(e) {
            const file = e.target.files && e.target.files[0];
            if (!file) return;
            const nameLower = file.name.toLowerCase();
            if (!nameLower.endsWith('.ttf') && !nameLower.endsWith('.otf')) {
                alert('File font harus berformat .ttf atau .otf!');
                return;
            }
            const reader = new FileReader();
            reader.onload = async function(evt) {
                const base64 = evt.target.result.split(',')[1];
                try {
                    const res = await fetch('/api/clock-fonts', {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({
                            action: 'upload',
                            fileName: file.name,
                            base64: base64
                        })
                    });
                    const json = await res.json();
                    if (json.success) {
                        window.dashboardState.clockFontName = json.name;
                        window.dashboardState.hasCustomClockFont = true;
                        reloadActiveClockFont(Date.now());
                        renderDashboardClock();
                        const modalEl = document.getElementById('clockFontModal');
                        if (modalEl) {
                            const modal = bootstrap.Modal.getInstance(modalEl);
                            if (modal) modal.hide();
                        }
                    } else {
                        alert('Gagal memasang font: ' + (json.error || 'Unknown error'));
                    }
                } catch(err) {
                    alert('Gagal mengunggah file font: ' + err.message);
                }
            };
            reader.readAsDataURL(file);
        }

        // Weather Simulator / Sync (100% Offline with realistic Indonesia climate data)
        async function refreshDashboardWeather() {
            window.dashboardState.weatherLoading = true;
            renderDashboardWeatherCard();

            try {
                // Try live openweather if cached
                const tz = window.dashboardState.activeTimeZone;
                let lat = -6.2088, lon = 106.8456, cityName = "Jakarta";
                if (tz === 'WITA') { lat = -5.1477; lon = 119.4327; cityName = "Makassar"; }
                else if (tz === 'WIT') { lat = -3.6954; lon = 128.1814; cityName = "Ambon"; }

                // Check LocalStorage cache or simulate accurately
                const cacheKey = 'singghara_weather_' + tz;
                const cached = localStorage.getItem(cacheKey);
                if (cached) {
                    window.dashboardState.weatherData = JSON.parse(cached);
                } else {
                    const hour = new Date().getHours();
                    const isDay = hour >= 6 && hour < 18;
                    const baseTemp = isDay ? 30.5 : 25.8;
                    window.dashboardState.weatherData = {
                        cityName: cityName,
                        temp: baseTemp,
                        feelsLike: baseTemp + 2.1,
                        tempMin: 24.0,
                        tempMax: 32.5,
                        humidity: 78,
                        windSpeed: 3.4,
                        uvIndex: isDay ? 6.2 : 0.0,
                        precipitation: 20,
                        condition: isDay ? "Cerah Berawan" : "Berawan",
                        icon: isDay ? "bi-cloud-sun-fill" : "bi-cloud-moon-fill",
                        iconColor: isDay ? "#F59E0B" : "#818CF8"
                    };
                    localStorage.setItem(cacheKey, JSON.stringify(window.dashboardState.weatherData));
                }
            } catch(e) {
                console.warn('Weather fetch fallback:', e);
            } finally {
                window.dashboardState.weatherLoading = false;
                renderDashboardWeatherCard();
            }
        }

        // --- F. DASHBOARD MAIN RENDER FUNCTION (100% OFFLINE CAPABLE & INSTANT RENDER) ---
        function applyDashboardData(data) {
            if (!data) return;
            const profileName = (data.profileName && data.profileName.trim()) 
                ? data.profileName.trim() 
                : (localStorage.getItem('singghara_profile_name') || 'Singghara User');
            if (data.profileName && data.profileName.trim()) {
                try { localStorage.setItem('singghara_profile_name', data.profileName.trim()); } catch(e) {}
            }

            try {
                if (typeof applyUserProfileData === 'function') {
                    applyUserProfileData(data);
                }
                const pNameEl = document.getElementById('dashboardProfileName');
                if (pNameEl) pNameEl.textContent = profileName;
                const sNameEl = document.getElementById('sidebarUserName');
                if (sNameEl) sNameEl.textContent = profileName;
            } catch(e) {}

            const serverTz = data.activeTimeZone || 'WIB';
            if (!localStorage.getItem('singghara_timezone')) {
                window.dashboardState.activeTimeZone = serverTz;
            }

            if (data.clockFontSizeSp) {
                window.dashboardState.clockFontSizeSp = Number(data.clockFontSizeSp);
            }
            if (data.clockFontName) {
                window.dashboardState.clockFontName = data.clockFontName;
            }
            window.dashboardState.hasCustomClockFont = !!data.hasCustomClockFont;
            if (data.currentDay) window.dashboardState.currentDay = data.currentDay;
            if (data.currentDate) window.dashboardState.currentDate = data.currentDate;
            if (data.jawaDina) window.dashboardState.jawaDina = data.jawaDina;
            if (data.jawaPasaran) window.dashboardState.jawaPasaran = data.jawaPasaran;
            if (data.hijriDate) window.dashboardState.hijriDate = data.hijriDate;

            if (data.prayerSchedule) window.dashboardState.prayerSchedule = data.prayerSchedule;
            if (data.prayerScheduleSource) window.dashboardState.prayerScheduleSource = data.prayerScheduleSource;
            if (data.prayerLocationText) window.dashboardState.prayerLocationText = data.prayerLocationText;
            if (data.prayerSoundSettings) window.dashboardState.prayerSoundSettings = data.prayerSoundSettings;

            if (data.allAgendas) window.dashboardState.allAgendas = data.allAgendas;

            // Update stats cards live
            const savEl = document.getElementById('dashSavingsBalance');
            if (savEl && data.savingsBalance !== undefined) {
                savEl.textContent = 'Rp ' + Number(data.savingsBalance || 0).toLocaleString('id-ID');
            }
            const revEl = document.getElementById('dashTotalRevenue');
            if (revEl && data.totalRevenue !== undefined) {
                revEl.textContent = 'Rp ' + Number(data.totalRevenue || 0).toLocaleString('id-ID');
            }
            const notEl = document.getElementById('dashNotesCount');
            if (notEl && data.notesCount !== undefined) {
                notEl.textContent = Number(data.notesCount || 0) + ' Catatan';
            }
            const agEl = document.getElementById('dashAgendaCount');
            if (agEl && (data.agendaCount !== undefined || data.jadwalCount !== undefined)) {
                const totalAcara = Number(data.agendaCount || 0) + Number(data.jadwalCount || 0);
                agEl.textContent = totalAcara + ' Acara';
            }

            const tzBadge = document.getElementById('prayerTzBadge');
            if (tzBadge) tzBadge.textContent = window.dashboardState.activeTimeZone;
            const srcEl = document.getElementById('prayerSourceLabel');
            if (srcEl) srcEl.textContent = window.dashboardState.prayerScheduleSource || 'Kemenag RI (Bimas Islam)';
            const locEl = document.getElementById('prayerLocationLabel');
            if (locEl) locEl.textContent = window.dashboardState.prayerLocationText || 'Lokasi Sinkronisasi';

            // Refresh subcomponents
            try { renderDashboardClock(); } catch(e) {}
            try { renderDashboardPrayerTimes(); } catch(e) {}
            try { renderInteractiveCalendar(); } catch(e) {}
        }

        async function loadDashboard() {
            try {
                const container = document.getElementById('tab-content') || document.getElementById('main-content') || document.querySelector('.content-wrapper');
                if (!container) return;

                const profileName = localStorage.getItem('singghara_profile_name') || 'Singghara User';
                const now = new Date();
                const year = now.getFullYear();
                const month = now.getMonth() + 1;
                const day = now.getDate();

                // 1. Calculations: Javanese & Hijri
                const jawa = getJavaneseDate(year, month, day);
                const hijri = getHijriDate(year, month, day);
                const masehiStr = `${dol}{INDO_DAYS[now.getDay()]}, ${dol}{day} ${dol}{INDO_MONTHS[now.getMonth()]} ${dol}{year}`;

                // 2. Smart Greeting
                const hour = now.getHours();
                let greeting = "Selamat Malam";
                let greetingIcon = "bi-moon-stars-fill";
                let greetingColor = "#818CF8";
                if (hour >= 5 && hour < 11) {
                    greeting = "Selamat Pagi";
                    greetingIcon = "bi-sun-fill";
                    greetingColor = "#F59E0B";
                } else if (hour >= 11 && hour < 15) {
                    greeting = "Selamat Siang";
                    greetingIcon = "bi-brightness-high-fill";
                    greetingColor = "#EAB308";
                } else if (hour >= 15 && hour < 18) {
                    greeting = "Selamat Sore";
                    greetingIcon = "bi-sunset-fill";
                    greetingColor = "#F97316";
                }

                let html = `
                    <div class="mb-4">
                        <!-- SECTION 1: HERO GREETING & PROFILE BANNER (PARITAS DENGAN HOMESCREEN) -->
                        <div class="card card-custom p-4 border-0 mb-4" style="background: linear-gradient(135deg, #0F766E 0%, #14B8A6 60%, #06B6D4 100%); color: white; border-radius: 24px; box-shadow: 0 12px 30px rgba(15, 118, 110, 0.28);">
                            <div class="d-flex flex-wrap justify-content-between align-items-center gap-3">
                                <div class="d-flex align-items-center gap-3">
                                    <div class="rounded-circle d-flex align-items-center justify-content-center flex-shrink-0 shadow-sm" style="width: 60px; height: 60px; background: rgba(255,255,255,0.2); backdrop-filter: blur(8px); border: 2px solid rgba(255,255,255,0.4); font-size: 1.8rem;">
                                        <i class="bi ${dol}{greetingIcon}" style="color: #FFF;"></i>
                                    </div>
                                    <div>
                                        <div class="d-flex align-items-center gap-2 mb-1">
                                            <span class="badge bg-white bg-opacity-25 text-white rounded-pill px-3 py-1 fw-semibold small">
                                                <i class="bi bi-person-check me-1"></i> ${dol}{greeting}
                                            </span>
                                        </div>
                                        <h3 class="fw-bold mb-0 text-white" id="dashboardProfileName">${dol}{safeEscape(profileName)}</h3>
                                        <div class="d-flex flex-wrap align-items-center gap-2 mt-1 small text-white-50">
                                            <span><i class="bi bi-calendar3 me-1"></i> ${dol}{masehiStr}</span>
                                            <span>•</span>
                                            <span class="text-warning fw-semibold"><i class="bi bi-moon me-1"></i> Jawa: ${dol}{jawa.dina} ${dol}{jawa.pasaran} (Wuku ${dol}{jawa.wuku})</span>
                                            <span>•</span>
                                            <span class="text-white"><i class="bi bi-star me-1"></i> ${dol}{hijri}</span>
                                        </div>
                                    </div>
                                </div>
                            </div>
                        </div>

                        <!-- SECTION 2 & 3: DIGITAL CLOCK & WEATHER FORECAST ROW (100% PARITAS HOMESCREEN) -->
                        <div class="row g-3 mb-4">
                            <!-- DIGITAL CLOCK WIDGET -->
                            <div class="col-12 col-lg-5">
                                <div class="card card-custom p-4 h-100 text-center position-relative d-flex flex-column justify-content-between" style="border-radius: 24px; background: rgba(13, 110, 253, 0.04); border: 1px solid rgba(13, 110, 253, 0.18); box-shadow: var(--card-shadow);">
                                    
                                    <!-- Interactive Container (Click 1x to change font, double click or tap 2x to resize) -->
                                    <div class="py-2 d-flex flex-column align-items-center justify-content-center flex-grow-1">
                                        <div id="clockDisplayWrapper" class="position-relative d-inline-flex flex-column align-items-center cursor-pointer"
                                             onclick="handleClockClick(event)"
                                             ondblclick="handleClockDblClick(event)"
                                             ontouchend="handleClockTouchEnd(event)"
                                             title="Klik 1x untuk Ubah Font • Klik / Ketuk 2x untuk Ubah Ukuran Jam"
                                             style="padding: 6px 16px; border-radius: 16px; transition: all 0.2s ease;">
                                             
                                             <!-- Digital Clock Display with Custom Synced Font -->
                                             <div id="bigDigitalClock" class="fw-black text-primary user-select-none d-flex align-items-center justify-content-center"
                                                  style="font-family: 'SinggharaClockFont', 'Technology', monospace; font-size: ${dol}{window.dashboardState.clockFontSizeSp || 52}px; letter-spacing: 2px; line-height: 1;">
                                                 <span id="clockHours">--</span>
                                                 <span id="clockColon" style="margin: 0 4px; transition: opacity 0.2s;">:</span>
                                                 <span id="clockMinutes">--</span>
                                                 <span id="clockSeconds" style="font-size: 0.45em; opacity: 0.75; margin-left: 6px; align-self: flex-start;"></span>
                                             </div>

                                             <!-- 4 Resize Dots (Top +, Bottom -, Left -, Right +) -->
                                             <div id="clockResizeHandles" class="d-none position-absolute w-100 h-100 top-0 start-0 pointer-events-none">
                                                 <!-- Top Dot (+) -->
                                                 <button type="button" class="btn btn-primary rounded-circle p-0 position-absolute start-50 translate-middle-x shadow pointer-events-auto d-flex align-items-center justify-content-center"
                                                         style="top: -14px; width: 26px; height: 26px; font-size: 14px; line-height: 1;"
                                                         onclick="event.stopPropagation(); changeClockFontSize(3);" title="Perbesar Atas (+)">
                                                     <i class="bi bi-plus-lg"></i>
                                                 </button>
                                                 <!-- Bottom Dot (-) -->
                                                 <button type="button" class="btn btn-primary rounded-circle p-0 position-absolute start-50 translate-middle-x shadow pointer-events-auto d-flex align-items-center justify-content-center"
                                                         style="bottom: -14px; width: 26px; height: 26px; font-size: 14px; line-height: 1;"
                                                         onclick="event.stopPropagation(); changeClockFontSize(-3);" title="Perkecil Bawah (-)">
                                                     <i class="bi bi-dash-lg"></i>
                                                 </button>
                                                 <!-- Left Dot (-) -->
                                                 <button type="button" class="btn btn-primary rounded-circle p-0 position-absolute top-50 translate-middle-y shadow pointer-events-auto d-flex align-items-center justify-content-center"
                                                         style="left: -14px; width: 26px; height: 26px; font-size: 14px; line-height: 1;"
                                                         onclick="event.stopPropagation(); changeClockFontSize(-3);" title="Perkecil Kiri (-)">
                                                     <i class="bi bi-dash-lg"></i>
                                                 </button>
                                                 <!-- Right Dot (+) -->
                                                 <button type="button" class="btn btn-primary rounded-circle p-0 position-absolute top-50 translate-middle-y shadow pointer-events-auto d-flex align-items-center justify-content-center"
                                                         style="right: -14px; width: 26px; height: 26px; font-size: 14px; line-height: 1;"
                                                         onclick="event.stopPropagation(); changeClockFontSize(3);" title="Perbesar Kanan (+)">
                                                     <i class="bi bi-plus-lg"></i>
                                                 </button>
                                             </div>
                                        </div>

                                        <!-- Resizing Control Toolbar -->
                                        <div id="clockResizeControls" class="d-none align-items-center justify-content-center gap-2 mt-2">
                                            <span class="badge bg-primary text-white rounded-pill px-2 py-1" style="font-size: 11px;" id="clockSizeIndicator">Ukuran: 52 sp</span>
                                            <button type="button" class="btn btn-sm btn-outline-secondary py-0 px-2 rounded-pill" style="font-size: 11px;" onclick="resetClockFontSize()">Reset (52)</button>
                                            <button type="button" class="btn btn-sm btn-primary py-0 px-2 rounded-pill" style="font-size: 11px;" onclick="toggleClockResizing(false)"><i class="bi bi-check2"></i> Selesai</button>
                                        </div>

                                        <!-- Active Font Label -->
                                        <div class="text-secondary small mt-1" style="font-size: 11px;">
                                            <span class="opacity-75">Font:</span> <span id="clockFontLabel" class="fw-semibold text-main">${dol}{safeEscape(window.dashboardState.clockFontName || 'Technology Bold')}</span>
                                        </div>
                                    </div>

                                    <!-- Timezone Pill Button (Directly Below Clock, Exact Parity with HomeScreen Surface Capsule) -->
                                    <div class="mt-2 mb-2 d-flex justify-content-center">
                                        <button type="button" class="btn btn-primary rounded-pill px-3 py-1 shadow-sm d-inline-flex align-items-center gap-2 border-0"
                                                onclick="openTimezoneModal()"
                                                style="font-size: 13px; font-weight: 700; border-radius: 20px;"
                                                title="Pilih Zona Waktu Indonesia (WIB, WITA, WIT)">
                                            <span id="clockTzFullLabel">WIB (UTC+7)</span>
                                            <span class="opacity-85 fw-medium" style="font-size: 11px;">• Ganti</span>
                                        </button>
                                    </div>

                                    <!-- Date Display (Masehi) -->
                                    <div class="fw-semibold text-main mt-1" id="bigDigitalDate" style="font-size: 15px;">
                                        Memuat tanggal...
                                    </div>

                                    <!-- Calendar Badge (Jawa & Hijriyah) -->
                                    <div class="mt-2 d-inline-flex align-items-center justify-content-center px-3 py-1 rounded-3 mx-auto"
                                         style="background: rgba(13, 110, 253, 0.08); color: var(--bs-primary); font-size: 13px; font-weight: 700;">
                                        <i class="bi bi-calendar-event me-2"></i>
                                        <span id="bigDigitalJawaHijri">Memuat kalender...</span>
                                    </div>

                                    <!-- Calculation Status Badge -->
                                    <div class="mt-2">
                                        <span class="badge rounded-pill px-2 py-1 fw-bold" id="clockCalcStatusBadge" style="background: rgba(108, 117, 125, 0.15); color: var(--text-main); font-size: 10px;">
                                            Kalkulasi Astronomis Kemenag (100% Offline)
                                        </span>
                                    </div>

                                </div>
                            </div>

                            <!-- WEATHER FORECAST CARD WIDGET -->
                            <div class="col-12 col-lg-7">
                                <div id="dashboardWeatherContainer" class="h-100">
                                    <!-- Rendered dynamically -->
                                </div>
                            </div>
                        </div>

                        <!-- SECTION 2B: "MY APLIKASI" (PERSIS HOMESCREEN MY SINGGHARA) -->
                        <div class="card card-custom p-3 p-md-4 mb-4" style="border-radius: 20px; border: 1px solid var(--border-color); box-shadow: var(--card-shadow);">
                            <!-- Title Row: "My Aplikasi" with clean Chevron-Right button -->
                            <div class="d-flex justify-content-between align-items-center mb-3">
                                <div class="d-flex align-items-center gap-2 cursor-pointer" onclick="openAllAppsModal()">
                                    <h5 class="fw-bold mb-0" style="color: var(--bs-primary, #0d6efd);">My Aplikasi</h5>
                                </div>
                                <button type="button" class="btn btn-sm btn-outline-primary rounded-circle p-1 d-flex align-items-center justify-content-center" onclick="openAllAppsModal()" title="Lihat Semua Aplikasi" style="width: 32px; height: 32px;">
                                    <i class="bi bi-chevron-right fs-6"></i>
                                </button>
                            </div>

                            <!-- 4 Most Recent Applications Matching HomeScreen.kt -->
                            <div class="row g-2 g-md-3">
                                <!-- 1. Rekap Belanja Pasar -->
                                <div class="col-3">
                                    <div class="text-center cursor-pointer hover-scale" onclick="switchTabByName('rekap_belanja')">
                                        <div class="app-icon-squircle mx-auto mb-2 d-flex align-items-center justify-content-center shadow-sm" style="background-color: #10B981; width: 56px; height: 56px; border-radius: 16px; font-size: 1.5rem; color: #FFF;">
                                            <i class="bi bi-basket3-fill"></i>
                                        </div>
                                        <div class="fw-bold text-main text-truncate" style="font-size: 12px;">Belanja Pasar</div>
                                        <small class="text-secondary d-none d-sm-block text-truncate" style="font-size: 10px;">Checklist & Satuan</small>
                                    </div>
                                </div>
                                <!-- 2. Block Puzzle -->
                                <div class="col-3">
                                    <div class="text-center cursor-pointer hover-scale" onclick="switchTabByName('block')">
                                        <div class="app-icon-squircle mx-auto mb-2 d-flex align-items-center justify-content-center shadow-sm" style="background-color: #2C5E6A; width: 56px; height: 56px; border-radius: 16px; font-size: 1.5rem; color: #FFF;">
                                            <i class="bi bi-grid-fill"></i>
                                        </div>
                                        <div class="fw-bold text-main text-truncate" style="font-size: 12px;">Block Puzzle</div>
                                        <small class="text-secondary d-none d-sm-block text-truncate" style="font-size: 10px;">Game Susun Balok</small>
                                    </div>
                                </div>
                                <!-- 3. Brick Game -->
                                <div class="col-3">
                                    <div class="text-center cursor-pointer hover-scale" onclick="switchTabByName('brick_game')">
                                        <div class="app-icon-squircle mx-auto mb-2 d-flex align-items-center justify-content-center shadow-sm" style="background-color: #306230; width: 56px; height: 56px; border-radius: 16px; font-size: 1.5rem; color: #FFF;">
                                            <i class="bi bi-controller"></i>
                                        </div>
                                        <div class="fw-bold text-main text-truncate" style="font-size: 12px;">Brick Game</div>
                                        <small class="text-secondary d-none d-sm-block text-truncate" style="font-size: 10px;">Game Retro LCD</small>
                                    </div>
                                </div>
                                <!-- 4. Al-Qur'an -->
                                <div class="col-3">
                                    <div class="text-center cursor-pointer hover-scale" onclick="switchTabByName('quran')">
                                        <div class="app-icon-squircle mx-auto mb-2 d-flex align-items-center justify-content-center shadow-sm" style="background-color: #00796B; width: 56px; height: 56px; border-radius: 16px; font-size: 1.5rem; color: #FFF;">
                                            <i class="bi bi-journal-bookmark-fill"></i>
                                        </div>
                                        <div class="fw-bold text-main text-truncate" style="font-size: 12px;">Al-Qur'an</div>
                                        <small class="text-secondary d-none d-sm-block text-truncate" style="font-size: 10px;">114 Surah Audio</small>
                                    </div>
                                </div>
                            </div>
                        </div>

                        <!-- REAL-TIME AZAN ACTIVE ALERT BANNER (SYNCED WITH TIME & AUDIO) -->
                        <div id="activeAzanAlertBanner" class="alert alert-primary d-none shadow-lg border-0 mb-4 p-3 d-flex flex-wrap align-items-center justify-content-between gap-3"
                             style="border-radius: 20px; background: linear-gradient(135deg, #0d6efd 0%, #0b5ed7 100%); color: white;">
                            <div class="d-flex align-items-center gap-3">
                                <div class="rounded-circle d-flex align-items-center justify-content-center flex-shrink-0 shadow-sm" style="width: 44px; height: 44px; background: rgba(255,255,255,0.25); font-size: 1.3rem;">
                                    <i class="bi bi-volume-up-fill text-white"></i>
                                </div>
                                <div>
                                    <h6 class="fw-bold mb-0 text-white" id="activeAzanPrayerTitle">Waktu Sholat Telah Tiba!</h6>
                                    <small class="text-white-50" id="activeAzanPrayerMsg">Sedang melantunkan azan Madinah My Singghara (100% Offline)</small>
                                </div>
                            </div>
                            <div class="d-flex align-items-center gap-2">
                                <button type="button" class="btn btn-light rounded-pill px-3 py-1 fw-bold text-primary shadow-sm" onclick="stopActiveAzan()" style="font-size: 13px;">
                                    <i class="bi bi-stop-circle-fill me-1 text-danger"></i> Matikan Azan
                                </button>
                            </div>
                        </div>

                        <!-- SECTION 4: ISLAMIC PRAYER SCHEDULE (100% PARITAS DENGAN HOMESCREEN MY SINGGHARA) -->
                        <div class="card card-custom p-4 mb-4" id="dashboardPrayerCard" style="border-radius: 24px; border: 1px solid var(--border-color); box-shadow: var(--card-shadow);">
                            <div class="d-flex flex-wrap justify-content-between align-items-center mb-3 gap-2">
                                <!-- Left: Gear Button & Title matching HomeScreen -->
                                <div class="d-flex align-items-center gap-3">
                                    <button type="button" class="btn btn-outline-primary rounded-circle p-0 d-flex align-items-center justify-content-center shadow-sm prayer-gear-btn"
                                            style="width: 42px; height: 42px; flex-shrink: 0;"
                                            onclick="openPrayerSettingsModal()"
                                            title="Pengaturan Suara Pengingat Sholat">
                                        <i class="bi bi-gear-fill fs-5"></i>
                                    </button>
                                    <div>
                                        <div class="d-flex align-items-center gap-2">
                                            <h5 class="fw-bold mb-0 text-main prayer-card-title">Waktu Solat</h5>
                                            <span class="badge bg-primary bg-opacity-10 text-primary rounded-pill px-2 py-1 prayer-tz-badge" style="font-size: 10px;" id="prayerTzBadge">${dol}{window.dashboardState.activeTimeZone}</span>
                                        </div>
                                        <small class="text-secondary prayer-source-label" id="prayerSourceLabel">${dol}{window.dashboardState.prayerScheduleSource || 'Kemenag RI (Bimas Islam)'}</small>
                                    </div>
                                </div>

                                <!-- Right: Location Text, Sync GPS, and Test Azan Button -->
                                <div class="d-flex flex-wrap align-items-center gap-2">
                                    <div class="d-flex align-items-center gap-1 px-3 py-1 rounded-pill prayer-location-pill" style="background: var(--sidebar-bg, rgba(0,0,0,0.04)); border: 1px solid var(--border-color); font-size: 12px;">
                                        <i class="bi bi-geo-alt-fill text-danger me-1"></i>
                                        <span class="prayer-loc-tag" style="font-size: 11px;">Lokasi:</span>
                                        <span class="fw-semibold text-main prayer-loc-text" id="prayerLocationLabel" style="max-width: 220px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;">
                                            ${dol}{window.dashboardState.prayerLocationText || 'Lokasi Sinkronisasi'}
                                        </span>
                                        <button type="button" class="btn btn-link p-0 ms-1 prayer-refresh-btn text-decoration-none" onclick="refreshPrayerScheduleAndLocation()" title="Sinkronkan Lokasi & Jadwal Sholat" style="font-size: 13px;">
                                            <i class="bi bi-arrow-clockwise" id="prayerRefreshIcon"></i>
                                        </button>
                                    </div>
                                    <button type="button" id="btnAdzanTest" class="btn btn-sm btn-outline-primary rounded-pill px-3 py-1 prayer-test-btn" onclick="playAdzanSoundTest()" title="Uji lantunan azan My Singghara">
                                        <i class="bi bi-volume-up-fill me-1"></i> Uji Azan
                                    </button>
                                </div>
                            </div>

                            <div id="prayerScheduleGrid" class="row g-2">
                                <!-- Rendered dynamically matching HomeScreen elements & icons -->
                            </div>
                        </div>

                        <!-- SECTION 5: INTERACTIVE INDONESIAN & JAVANESE MONTHLY CALENDAR -->
                        <div class="card card-custom p-3 p-md-4 mb-4" style="border-radius: 20px; border: 1px solid var(--border-color); box-shadow: var(--card-shadow);">
                            <div class="d-flex flex-wrap justify-content-between align-items-center mb-3 gap-2">
                                <div class="d-flex align-items-center gap-2">
                                    <div class="app-icon-squircle flex-shrink-0" style="background-color: #3B82F6; width: 36px; height: 36px; font-size: 1.1rem;">
                                        <i class="bi bi-calendar-month-fill"></i>
                                    </div>
                                    <div>
                                        <h5 class="fw-bold mb-0 text-main" id="calendarMonthTitle">Kalender Bulanan</h5>
                                        <small class="text-secondary d-none d-sm-block">Penanggalan Masehi, Weton Pasaran Jawa, Hari Libur & Kegiatan</small>
                                    </div>
                                </div>
                                <div class="d-flex align-items-center gap-1 gap-md-2 ms-auto ms-sm-0">
                                    <button class="btn btn-sm btn-outline-secondary rounded-pill px-2 px-md-3" onclick="navigateCalendarMonth(-1)" title="Bulan Sebelumnya">
                                        <i class="bi bi-chevron-left"></i><span class="d-none d-md-inline ms-1">Bulan Lalu</span>
                                    </button>
                                    <button class="btn btn-sm btn-outline-primary rounded-pill px-2 px-md-3 fw-bold" onclick="resetCalendarToCurrentMonth()">
                                        Hari Ini
                                    </button>
                                    <button class="btn btn-sm btn-outline-secondary rounded-pill px-2 px-md-3" onclick="navigateCalendarMonth(1)" title="Bulan Berikutnya">
                                        <span class="d-none d-md-inline me-1">Bulan Depan</span><i class="bi bi-chevron-right"></i>
                                    </button>
                                </div>
                            </div>

                            <div class="w-100 overflow-hidden" id="interactiveCalendarGrid">
                                <!-- Rendered dynamically -->
                            </div>

                            <div class="d-flex flex-wrap align-items-center gap-2 gap-md-3 mt-3 pt-2 border-top border-secondary border-opacity-25 text-secondary" style="font-size: 11px;">
                                <div class="d-flex align-items-center gap-1">
                                    <span class="d-inline-block rounded-circle flex-shrink-0" style="width: 8px; height: 8px; background-color: #EF4444;"></span>
                                    <span>Libur / Minggu</span>
                                </div>
                                <div class="d-flex align-items-center gap-1">
                                    <span class="d-inline-block rounded-circle flex-shrink-0" style="width: 8px; height: 8px; background-color: #10B981;"></span>
                                    <span>Peringatan</span>
                                </div>
                                <div class="d-flex align-items-center gap-1">
                                    <span class="d-inline-block rounded-circle flex-shrink-0" style="width: 8px; height: 8px; background-color: #F59E0B;"></span>
                                    <span>Agenda Terdaftar</span>
                                </div>
                                <div class="d-flex align-items-center gap-1">
                                    <span class="d-inline-block rounded-circle flex-shrink-0" style="width: 8px; height: 8px; background-color: #3B82F6;"></span>
                                    <span>Hari Ini</span>
                                </div>
                            </div>
                        </div>

                        <!-- SECTION 6: STATS SUMMARY ROW (PARITAS DATA MY SINGGHARA) -->
                        <div class="row g-3 mb-4">
                            <div class="col-6 col-lg-3">
                                <div class="card card-custom p-3 h-100 cursor-pointer hover-scale" onclick="switchTabByName('savings')">
                                    <div class="d-flex align-items-center gap-3">
                                        <div class="app-icon-squircle flex-shrink-0" style="background-color: #10B981;">
                                            <i class="bi bi-wallet2 fs-5"></i>
                                        </div>
                                        <div class="overflow-hidden">
                                            <small class="text-secondary d-block text-truncate">Saldo Tabungan</small>
                                            <h6 class="fw-bold mb-0 text-success text-truncate" id="dashSavingsBalance">Rp 0</h6>
                                        </div>
                                    </div>
                                </div>
                            </div>
                            <div class="col-6 col-lg-3">
                                <div class="card card-custom p-3 h-100 cursor-pointer hover-scale" onclick="switchTabByName('kasir')">
                                    <div class="d-flex align-items-center gap-3">
                                        <div class="app-icon-squircle flex-shrink-0" style="background-color: #0F766E;">
                                            <i class="bi bi-shop fs-5"></i>
                                        </div>
                                        <div class="overflow-hidden">
                                            <small class="text-secondary d-block text-truncate">Omset Kasir</small>
                                            <h6 class="fw-bold mb-0 text-primary text-truncate" id="dashTotalRevenue">Rp 0</h6>
                                        </div>
                                    </div>
                                </div>
                            </div>
                            <div class="col-6 col-lg-3">
                                <div class="card card-custom p-3 h-100 cursor-pointer hover-scale" onclick="switchTabByName('notes')">
                                    <div class="d-flex align-items-center gap-3">
                                        <div class="app-icon-squircle flex-shrink-0" style="background-color: #6366F1;">
                                            <i class="bi bi-journal-text fs-5"></i>
                                        </div>
                                        <div class="overflow-hidden">
                                            <small class="text-secondary d-block text-truncate">Catatan Aktif</small>
                                            <h6 class="fw-bold mb-0 text-truncate" id="dashNotesCount">0 Catatan</h6>
                                        </div>
                                    </div>
                                </div>
                            </div>
                            <div class="col-6 col-lg-3">
                                <div class="card card-custom p-3 h-100 cursor-pointer hover-scale" onclick="switchTabByName('agenda')">
                                    <div class="d-flex align-items-center gap-3">
                                        <div class="app-icon-squircle flex-shrink-0" style="background-color: #F59E0B;">
                                            <i class="bi bi-calendar-event fs-5"></i>
                                        </div>
                                        <div class="overflow-hidden">
                                            <small class="text-secondary d-block text-truncate">Agenda & Jadwal</small>
                                            <h6 class="fw-bold mb-0 text-truncate" id="dashAgendaCount">0 Acara</h6>
                                        </div>
                                    </div>
                                </div>
                            </div>
                        </div>

                    </div>

                    <!-- MODAL: TIMEZONE SELECTOR (PARITY DENGAN HOMESCREEN) -->
                    <div class="modal fade" id="timezoneSelectModal" tabindex="-1" aria-hidden="true">
                        <div class="modal-dialog modal-dialog-centered">
                            <div class="modal-content card-custom p-4" style="border-radius: 28px; border: 1px solid var(--border-color);">
                                <div class="modal-header border-bottom-0 pb-2">
                                    <div class="d-flex align-items-center gap-3">
                                        <div class="rounded-circle d-flex align-items-center justify-content-center flex-shrink-0" style="width: 44px; height: 44px; background: rgba(13, 110, 253, 0.12); font-size: 22px;">
                                            🇮🇩
                                        </div>
                                        <div>
                                            <h5 class="modal-title fw-bold text-main mb-0">Pilih Zona Waktu</h5>
                                            <small class="text-secondary">Waktu Indonesia Barat, Tengah, dan Timur</small>
                                        </div>
                                    </div>
                                    <button type="button" class="btn-close btn-close-custom" data-bs-dismiss="modal"></button>
                                </div>
                                <div class="modal-body pt-2 pb-0">
                                    <div id="timezoneModalBodyContent" class="d-flex flex-column gap-2 mb-3">
                                        <!-- Rendered dynamically by renderTimezoneModalContent -->
                                    </div>
                                </div>
                                <div class="modal-footer border-top-0 pt-0">
                                    <button type="button" class="btn btn-outline-secondary rounded-pill px-4" data-bs-dismiss="modal">Batal</button>
                                </div>
                            </div>
                        </div>
                    </div>

                    <!-- MODAL: UBAH FONT JAM DIGITAL (PARITY DENGAN HOMESCREEN) -->
                    <div class="modal fade" id="clockFontModal" tabindex="-1" aria-hidden="true">
                        <div class="modal-dialog modal-dialog-centered">
                            <div class="modal-content card-custom p-4" style="border-radius: 28px; border: 1px solid var(--border-color);">
                                <div class="modal-header border-bottom-0 pb-2">
                                    <div class="d-flex align-items-center gap-3">
                                        <div class="rounded-circle d-flex align-items-center justify-content-center flex-shrink-0" style="width: 44px; height: 44px; background: rgba(13, 110, 253, 0.12); color: var(--bs-primary);">
                                            <i class="bi bi-fonts fs-4"></i>
                                        </div>
                                        <div>
                                            <h5 class="modal-title fw-bold text-main mb-0">Ubah Font Jam Digital</h5>
                                            <small class="text-secondary">Folder My Singghara/Data/font/ (Google Drive)</small>
                                        </div>
                                    </div>
                                    <button type="button" class="btn-close btn-close-custom" data-bs-dismiss="modal"></button>
                                </div>
                                <div class="modal-body pt-2">
                                    <div id="clockFontListContainer" class="d-flex flex-column gap-2 mb-3" style="max-height: 320px; overflow-y: auto;">
                                        <!-- Rendered dynamically by renderClockFontModalList -->
                                    </div>

                                    <input type="file" id="clockFontFileInput" accept=".ttf,.otf" class="d-none" onchange="handleClockFontUpload(event)">
                                    <button type="button" class="btn btn-primary w-100 rounded-3 py-2 mb-2 d-flex align-items-center justify-content-center gap-2 shadow-sm" onclick="document.getElementById('clockFontFileInput').click()">
                                        <i class="bi bi-folder-fill"></i> Pilih File Font Lain (.ttf / .otf)
                                    </button>

                                    <div id="clockFontResetBtnContainer" class="d-none mb-2">
                                        <!-- Reset button rendered dynamically if custom font is active -->
                                    </div>
                                </div>
                                <div class="modal-footer border-top-0 pt-0">
                                    <button type="button" class="btn btn-outline-secondary rounded-pill px-4" data-bs-dismiss="modal">Batal</button>
                                </div>
                            </div>
                        </div>
                    </div>

                    <!-- MODAL: ALL APPLICATIONS (12 MODULES) -->
                    <div class="modal fade" id="allAppsModal" tabindex="-1" aria-hidden="true">
                        <div class="modal-dialog modal-dialog-centered modal-lg">
                            <div class="modal-content card-custom p-3" style="border-radius: 24px; border: 1px solid var(--border-color);">
                                <div class="modal-header border-bottom-0 pb-2">
                                    <div>
                                        <h5 class="modal-title fw-bold text-main d-flex align-items-center gap-2">
                                            <i class="bi bi-grid-3x3-gap-fill text-primary"></i> Seluruh Modul My Singghara
                                        </h5>
                                        <small class="text-secondary">Pilih modul aplikasi untuk membukanya secara instan di PC</small>
                                    </div>
                                    <button type="button" class="btn-close btn-close-custom" data-bs-dismiss="modal"></button>
                                </div>
                                <div class="modal-body pt-2">
                                    <div class="row g-3" id="allAppsModalGrid">
                                        <!-- Rendered dynamically -->
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div>

                    <!-- MODAL: CALENDAR DAY DETAIL POPUP -->
                    <div class="modal fade" id="calendarDayModal" tabindex="-1" aria-hidden="true">
                        <div class="modal-dialog modal-dialog-centered">
                            <div class="modal-content card-custom p-3" style="border-radius: 24px; border: 1px solid var(--border-color);" id="calendarDayModalContent">
                                <!-- Rendered on day click -->
                            </div>
                        </div>
                    </div>

                    <!-- MODAL: PRAYER SETTINGS (PENGATURAN SUARA SHOLAT) -->
                    <div class="modal fade" id="prayerSettingsModal" tabindex="-1" aria-hidden="true">
                        <div class="modal-dialog modal-dialog-centered">
                            <div class="modal-content card-custom p-4" style="border-radius: 28px; border: 1px solid var(--border-color);">
                                <div class="modal-header border-bottom-0 pb-2">
                                    <div class="d-flex align-items-center gap-3">
                                        <div class="rounded-circle d-flex align-items-center justify-content-center flex-shrink-0" style="width: 44px; height: 44px; background: rgba(13, 110, 253, 0.12); color: var(--bs-primary);">
                                            <i class="bi bi-bell-fill fs-4"></i>
                                        </div>
                                        <div>
                                            <h5 class="modal-title fw-bold text-main mb-0">Pengaturan Suara Solat</h5>
                                            <small class="text-secondary">Pilih Azan, Notifikasi Bip, atau Senyap untuk tiap waktu</small>
                                        </div>
                                    </div>
                                    <button type="button" class="btn-close btn-close-custom" data-bs-dismiss="modal"></button>
                                </div>
                                <div class="modal-body pt-2 pb-0">
                                    <div class="alert alert-info py-2 px-3 rounded-3 small mb-3 border-0" style="background: rgba(13, 110, 253, 0.08); color: var(--bs-primary);">
                                        <i class="bi bi-info-circle me-1"></i> Mode Azan otomatis melantunkan rekaman suara Azan Madinah My Singghara 100% offline saat waktu sholat tiba.
                                    </div>
                                    <div id="prayerSettingsList" class="d-flex flex-column gap-2 mb-3">
                                        <!-- Rendered dynamically -->
                                    </div>
                                </div>
                                <div class="modal-footer border-top-0 pt-0">
                                    <button type="button" class="btn btn-primary rounded-pill px-4" data-bs-dismiss="modal">Selesai</button>
                                </div>
                            </div>
                        </div>
                    </div>

                    <!-- MODAL: SINGLE PRAYER SOUND PICKER -->
                    <div class="modal fade" id="singlePrayerSoundModal" tabindex="-1" aria-hidden="true">
                        <div class="modal-dialog modal-dialog-centered">
                            <div class="modal-content card-custom p-4" style="border-radius: 28px; border: 1px solid var(--border-color);">
                                <div class="modal-header border-bottom-0 pb-2">
                                    <div class="d-flex align-items-center gap-3">
                                        <div class="rounded-circle d-flex align-items-center justify-content-center flex-shrink-0" style="width: 44px; height: 44px; background: rgba(13, 110, 253, 0.12); color: var(--bs-primary);">
                                            <i class="bi bi-volume-up-fill fs-4"></i>
                                        </div>
                                        <div>
                                            <h5 class="modal-title fw-bold text-main mb-0" id="singlePrayerTitle">Pilih Suara Sholat</h5>
                                            <small class="text-secondary">Atur pengingat ketika waktu tiba</small>
                                        </div>
                                    </div>
                                    <button type="button" class="btn-close btn-close-custom" data-bs-dismiss="modal"></button>
                                </div>
                                <div class="modal-body pt-2 pb-0">
                                    <div id="singlePrayerOptionsList" class="d-flex flex-column gap-2 mb-3">
                                        <!-- Radio options with test button -->
                                    </div>
                                </div>
                                <div class="modal-footer border-top-0 pt-0 d-flex justify-content-between">
                                    <button type="button" class="btn btn-outline-secondary rounded-pill px-3" data-bs-dismiss="modal">Batal</button>
                                    <button type="button" class="btn btn-primary rounded-pill px-4" onclick="saveSinglePrayerSoundSetting()">Simpan</button>
                                </div>
                            </div>
                        </div>
                    </div>
                `;

                // Set HTML immediately to container so page is NEVER blank
                container.innerHTML = html;

                // Render Sub-components immediately with current/cached offline data
                try { renderDashboardClock(); } catch(e) { console.error('Error clock:', e); }
                try { renderDashboardWeatherCard(); } catch(e) { console.error('Error weather:', e); }
                try { renderDashboardPrayerTimes(); } catch(e) { console.error('Error prayer:', e); }
                try { renderInteractiveCalendar(); } catch(e) { console.error('Error calendar:', e); }
                try { buildAllAppsModalContent(); } catch(e) { console.error('Error all apps modal:', e); }

                // Setup live interval for clock & prayer highlighting
                if (window.dashboardClockInterval) clearInterval(window.dashboardClockInterval);
                window.dashboardClockInterval = setInterval(() => {
                    try { renderDashboardClock(); } catch(e) {}
                }, 1000);

                // Asynchronously sync fresh data from Android device in the background
                (async function syncDashboardData() {
                    try {
                        let data = null;
                        try {
                            const fetchRes = await fetchApiWithCache('/api/dashboard', 'dashboard');
                            data = (fetchRes && fetchRes.data) ? fetchRes.data : null;
                        } catch(netErr) {
                            try {
                                const directRes = await fetch('/api/dashboard');
                                if (directRes.ok) data = await directRes.json();
                            } catch(e) {}
                        }

                        if (data) {
                            applyDashboardData(data);
                        }
                    } catch(err) {
                        console.warn('Dashboard sync background fallback:', err);
                    }
                })();

            } catch (err) {
                console.error('Error loading dashboard:', err);
            }
        }

        // --- G. SUB-COMPONENT: REAL-TIME DIGITAL CLOCK ---
        function renderDashboardClock() {
            const now = new Date();
            const tz = window.dashboardState.activeTimeZone || 'WIB';

            let tzOffsetHours = 7;
            let tzDesc = "UTC+7";
            if (tz === 'WITA') {
                tzOffsetHours = 8;
                tzDesc = "UTC+8";
            } else if (tz === 'WIT') {
                tzOffsetHours = 9;
                tzDesc = "UTC+9";
            }

            // Calculate timezone time
            const utc = now.getTime() + (now.getTimezoneOffset() * 60000);
            const targetTime = new Date(utc + (3600000 * tzOffsetHours));

            const h = String(targetTime.getHours()).padStart(2, '0');
            const m = String(targetTime.getMinutes()).padStart(2, '0');
            const s = String(targetTime.getSeconds()).padStart(2, '0');
            const timeStr = `${dol}{h}:${dol}{m}:${dol}{s}`;

            const headerClock = document.getElementById('headerClock');
            if (headerClock) headerClock.textContent = timeStr;

            const clockHours = document.getElementById('clockHours');
            if (clockHours) clockHours.textContent = h;

            const clockMinutes = document.getElementById('clockMinutes');
            if (clockMinutes) clockMinutes.textContent = m;

            const clockSeconds = document.getElementById('clockSeconds');
            if (clockSeconds) clockSeconds.textContent = s;

            const clockColon = document.getElementById('clockColon');
            if (clockColon) {
                // Subtle blink effect matching Android Compose InfiniteTransition
                clockColon.style.opacity = (targetTime.getSeconds() % 2 === 0) ? '1' : '0.25';
            }

            const bigClock = document.getElementById('bigDigitalClock');
            if (bigClock) {
                const fs = window.dashboardState.clockFontSizeSp || 52;
                bigClock.style.fontSize = fs + 'px';
            }

            const sizeIndicator = document.getElementById('clockSizeIndicator');
            if (sizeIndicator) {
                sizeIndicator.textContent = `Ukuran: ${dol}{window.dashboardState.clockFontSizeSp || 52} sp`;
            }

            const fontLabel = document.getElementById('clockFontLabel');
            if (fontLabel) {
                fontLabel.textContent = window.dashboardState.clockFontName || 'Technology Bold';
            }

            const tzFullLabel = document.getElementById('clockTzFullLabel');
            if (tzFullLabel) {
                tzFullLabel.textContent = `${dol}{tz} (${dol}{tzDesc})`;
            }

            // Date (Masehi)
            const d = targetTime.getDate();
            const mo = INDO_MONTHS[targetTime.getMonth()];
            const y = targetTime.getFullYear();
            const dayName = INDO_DAYS[targetTime.getDay()];

            const bigDate = document.getElementById('bigDigitalDate');
            if (bigDate) {
                const curDay = window.dashboardState.currentDay || dayName;
                const curDate = window.dashboardState.currentDate || `${dol}{d} ${dol}{mo} ${dol}{y}`;
                bigDate.textContent = `${dol}{curDay}, ${dol}{curDate}`;
            }

            // Jawa & Hijriyah Badge
            const jawaHijriEl = document.getElementById('bigDigitalJawaHijri');
            if (jawaHijriEl) {
                let jDina = window.dashboardState.jawaDina;
                let jPasaran = window.dashboardState.jawaPasaran;
                let hijri = window.dashboardState.hijriDate;
                if (!jDina || !jPasaran || !hijri) {
                    const jCalc = getJavaneseDate(y, targetTime.getMonth() + 1, d);
                    const hCalc = getHijriDate(y, targetTime.getMonth() + 1, d);
                    jDina = jDina || jCalc.dina;
                    jPasaran = jPasaran || jCalc.pasaran;
                    hijri = hijri || hCalc;
                }
                jawaHijriEl.textContent = `${dol}{jDina} ${dol}{jPasaran} · ${dol}{hijri}`;
            }

            // Calculation Status Badge
            const calcBadge = document.getElementById('clockCalcStatusBadge');
            if (calcBadge) {
                calcBadge.textContent = 'Kalkulasi Astronomis Kemenag (100% Offline)';
            }

            // Real-time Azan prayer alarm trigger
            checkPrayerAlarmTrigger(targetTime);
        }

        // --- H. SUB-COMPONENT: WEATHER CARD ---
        function renderDashboardWeatherCard() {
            const container = document.getElementById('dashboardWeatherContainer');
            if (!container) return;

            if (window.dashboardState.weatherLoading) {
                container.innerHTML = `
                    <div class="card card-custom p-4 h-100 d-flex align-items-center justify-content-center text-center" style="border-radius: 20px; border: 1px solid var(--border-color);">
                        <div class="spinner-border text-primary mb-2" role="status"></div>
                        <div class="text-secondary small">Memperbarui data perkiraan cuaca...</div>
                    </div>
                `;
                return;
            }

            const w = window.dashboardState.weatherData || {
                cityName: "Jakarta (Default GPS)",
                temp: 30.2,
                feelsLike: 32.5,
                tempMin: 24.0,
                tempMax: 33.0,
                humidity: 75,
                windSpeed: 3.5,
                uvIndex: 6.0,
                precipitation: 15,
                condition: "Cerah Berawan",
                icon: "bi-cloud-sun-fill",
                iconColor: "#F59E0B"
            };

            container.innerHTML = `
                <div class="card card-custom p-4 h-100" style="border-radius: 20px; border: 1px solid var(--border-color); box-shadow: var(--card-shadow);">
                    <div class="d-flex justify-content-between align-items-center mb-3">
                        <div class="d-flex align-items-center gap-2">
                            <div class="app-icon-squircle" style="background-color: rgba(245, 158, 11, 0.15); color: #F59E0B; width: 36px; height: 36px; font-size: 1.1rem;">
                                <i class="bi bi-cloud-sun-fill"></i>
                            </div>
                            <div>
                                <h6 class="fw-bold mb-0 text-main">Perkiraan Cuaca Harian</h6>
                                <small class="text-secondary"><i class="bi bi-geo-alt-fill text-danger me-1"></i> ${dol}{escapeHtml(w.cityName)}</small>
                            </div>
                        </div>
                        <button class="btn btn-sm btn-outline-primary rounded-circle p-2" onclick="refreshDashboardWeather()" title="Perbarui Cuaca">
                            <i class="bi bi-arrow-clockwise"></i>
                        </button>
                    </div>

                    <div class="d-flex flex-wrap justify-content-between align-items-center mb-3 gap-3">
                        <div class="d-flex align-items-center gap-3">
                            <div class="rounded-4 p-3 d-flex align-items-center justify-content-center" style="background: rgba(245, 158, 11, 0.12); width: 64px; height: 64px;">
                                <i class="bi ${dol}{w.icon} display-6" style="color: ${dol}{w.iconColor};"></i>
                            </div>
                            <div>
                                <div class="display-6 fw-bold text-main mb-0">${dol}{Number(w.temp).toFixed(1)}°C</div>
                                <div class="text-secondary fw-semibold small">${dol}{escapeHtml(w.condition)} • Terasa ${dol}{Number(w.feelsLike).toFixed(1)}°C</div>
                            </div>
                        </div>
                        <div class="text-end">
                            <div class="badge bg-secondary bg-opacity-15 text-main px-3 py-2 rounded-pill small">
                                Min ${dol}{Number(w.tempMin).toFixed(0)}°C / Maks ${dol}{Number(w.tempMax).toFixed(0)}°C
                            </div>
                        </div>
                    </div>

                    <div class="row g-2 pt-2 border-top border-secondary border-opacity-25 text-center">
                        <div class="col-3">
                            <small class="text-secondary d-block" style="font-size: 11px;"><i class="bi bi-droplet-fill text-info me-1"></i> Kelembaban</small>
                            <span class="fw-bold small text-main">${dol}{w.humidity}%</span>
                        </div>
                        <div class="col-3">
                            <small class="text-secondary d-block" style="font-size: 11px;"><i class="bi bi-wind text-primary me-1"></i> Angin</small>
                            <span class="fw-bold small text-main">${dol}{w.windSpeed} m/s</span>
                        </div>
                        <div class="col-3">
                            <small class="text-secondary d-block" style="font-size: 11px;"><i class="bi bi-sun text-warning me-1"></i> Indeks UV</small>
                            <span class="fw-bold small text-main">${dol}{w.uvIndex}</span>
                        </div>
                        <div class="col-3">
                            <small class="text-secondary d-block" style="font-size: 11px;"><i class="bi bi-umbrella text-success me-1"></i> Presipitasi</small>
                            <span class="fw-bold small text-main">${dol}{w.precipitation}%</span>
                        </div>
                    </div>
                </div>
            `;
        }

        // --- I. SUB-COMPONENT: PRAYER SCHEDULE & REAL-TIME AZAN (100% PARITY DENGAN HOMESCREEN) ---
        let lastTriggeredMinuteKey = '';

        function getPrayerSoundMode(prayerId) {
            const settings = window.dashboardState.prayerSoundSettings || {};
            const val = settings[prayerId];
            if (val !== undefined && val !== null) return Number(val);
            // Default parity: Imsak = 1 (Bip/Lonceng), Subuh/Dzuhur/Ashar/Maghrib/Isya = 2 (Azan)
            return (prayerId === 'imsak') ? 1 : 2;
        }

        function getPrayerSoundIcon(mode) {
            if (mode === 0) return { icon: 'bi-volume-mute-fill', color: 'text-secondary', title: 'Senyap (Mati)' };
            if (mode === 1) return { icon: 'bi-bell-fill', color: 'text-warning', title: 'Nada Bip Singkat' };
            return { icon: 'bi-volume-up-fill', color: 'text-primary', title: 'Azan Madinah (100% Offline)' };
        }

        function checkPrayerAlarmTrigger(targetTime) {
            const h = String(targetTime.getHours()).padStart(2, '0');
            const m = String(targetTime.getMinutes()).padStart(2, '0');
            const s = targetTime.getSeconds();
            const timeKey = `${dol}{h}:${dol}{m}`;

            if (lastTriggeredMinuteKey === timeKey) return;

            const tz = window.dashboardState.activeTimeZone || 'WIB';
            let schedule = window.dashboardState.prayerSchedule;
            if (!schedule || !schedule.subuh) schedule = getPrayerTimes(tz, targetTime);

            const prayerList = [
                { id: 'imsak', name: 'Imsak', time: schedule.imsak },
                { id: 'subuh', name: 'Subuh', time: schedule.subuh },
                { id: 'dzuhur', name: 'Dzuhur', time: schedule.dzuhur },
                { id: 'ashar', name: 'Ashar', time: schedule.ashar },
                { id: 'maghrib', name: 'Maghrib', time: schedule.maghrib },
                { id: 'isya', name: 'Isya', time: schedule.isya }
            ];

            const matched = prayerList.find(p => p.time === timeKey);
            if (matched && s <= 5) {
                lastTriggeredMinuteKey = timeKey;
                triggerPrayerAlarm(matched);
            }
        }

        function triggerPrayerAlarm(prayer) {
            const mode = getPrayerSoundMode(prayer.id);
            if (mode === 0) {
                console.log(`[Prayer Alert] ${dol}{prayer.name} reached, sound is disabled (mute).`);
                return;
            }

            const alertBanner = document.getElementById('activeAzanAlertBanner');
            const alertTitle = document.getElementById('activeAzanPrayerTitle');
            const alertMsg = document.getElementById('activeAzanPrayerMsg');
            if (alertBanner) {
                if (alertTitle) alertTitle.textContent = `Waktu Sholat ${dol}{prayer.name} Telah Tiba!`;
                if (alertMsg) {
                    alertMsg.textContent = (mode === 2 || mode === 3)
                        ? `Melantunkan azan Madinah My Singghara 100% offline (${dol}{prayer.time} ${dol}{window.dashboardState.activeTimeZone || 'WIB'})`
                        : `Pemberitahuan masuknya waktu sholat ${dol}{prayer.name} (${dol}{prayer.time})`;
                }
                alertBanner.classList.remove('d-none');
            }

            if (mode === 2 || mode === 3) {
                playPrayerAzan(prayer.name, true);
            } else if (mode === 1) {
                playSystemBeepChime(prayer.name);
            }
        }

        function renderDashboardPrayerTimes() {
            const grid = document.getElementById('prayerScheduleGrid');
            if (!grid) return;

            const tz = window.dashboardState.activeTimeZone || 'WIB';
            let schedule = window.dashboardState.prayerSchedule;
            if (!schedule || !schedule.subuh) {
                schedule = getPrayerTimes(tz, new Date());
            }

            const now = new Date();
            let tzOffsetHours = 7;
            if (tz === 'WITA') tzOffsetHours = 8;
            else if (tz === 'WIT') tzOffsetHours = 9;
            const utc = now.getTime() + (now.getTimezoneOffset() * 60000);
            const targetTime = new Date(utc + (3600000 * tzOffsetHours));
            const currentMinutes = targetTime.getHours() * 60 + targetTime.getMinutes();

            function toMin(timeStr) {
                if (!timeStr) return 0;
                const [h, m] = timeStr.split(':').map(Number);
                return h * 60 + (m || 0);
            }

            const prayerList = [
                { id: 'imsak', name: 'Imsak', time: schedule.imsak, min: toMin(schedule.imsak), icon: 'bi-clock-history' },
                { id: 'subuh', name: 'Subuh', time: schedule.subuh, min: toMin(schedule.subuh), icon: 'bi-brightness-alt-high-fill' },
                { id: 'dzuhur', name: 'Dzuhur', time: schedule.dzuhur, min: toMin(schedule.dzuhur), icon: 'bi-sun-fill' },
                { id: 'ashar', name: 'Ashar', time: schedule.ashar, min: toMin(schedule.ashar), icon: 'bi-brightness-high-fill' },
                { id: 'maghrib', name: 'Maghrib', time: schedule.maghrib, min: toMin(schedule.maghrib), icon: 'bi-sunset-fill' },
                { id: 'isya', name: 'Isya', time: schedule.isya, min: toMin(schedule.isya), icon: 'bi-moon-stars-fill' }
            ];

            let nextPrayer = prayerList.find(p => p.min > currentMinutes);
            if (!nextPrayer) nextPrayer = prayerList[0];

            const isDark = (document.documentElement.getAttribute('data-theme') === 'dark') || document.body.classList.contains('dark');
            let html = '';
            prayerList.forEach(p => {
                const isNext = (p.id === nextPrayer.id);
                const soundMode = getPrayerSoundMode(p.id);
                const sound = getPrayerSoundIcon(soundMode);

                // Sinkronisasi Mode Gelap: Hapus warna putih, sesuaikan tema gelap, font putih terang agar mudah dibaca
                const itemBg = isDark
                    ? (isNext ? 'rgba(14, 165, 233, 0.28)' : '#0f172a')
                    : (isNext ? 'rgba(13, 110, 253, 0.12)' : 'var(--sidebar-bg, #f8fafc)');
                const itemBorder = isDark
                    ? (isNext ? '2px solid #38bdf8' : '1px solid #334155')
                    : (isNext ? '2px solid #0d6efd' : '1px solid rgba(0,0,0,0.08)');
                const fontColor = isDark
                    ? '#ffffff'
                    : (isNext ? 'var(--bs-primary, #0d6efd)' : 'var(--text-main, #1e293b)');
                const iconBg = isDark
                    ? (isNext ? '#0284c7' : 'rgba(255, 255, 255, 0.12)')
                    : (isNext ? '#0d6efd' : 'rgba(13, 110, 253, 0.1)');
                const iconColor = isDark
                    ? '#ffffff'
                    : (isNext ? '#ffffff' : '#0d6efd');
                const nextBadgeBg = isDark ? '#0284c7' : '#0d6efd';

                html += `
                    <div class="col-4 col-md-2">
                        <div class="card p-2 text-center h-100 cursor-pointer position-relative prayer-time-item ${dol}{isNext ? 'prayer-item-next' : 'prayer-item-normal'}"
                             onclick="openSinglePrayerSoundModal('${dol}{p.id}')"
                             title="Klik untuk ubah suara sholat ${dol}{p.name}"
                             style="border-radius: 16px; background: ${dol}{itemBg}; border: ${dol}{itemBorder}; color: ${dol}{fontColor}; transition: all 0.2s ease;">
                            <div class="d-flex justify-content-between align-items-center mb-1">
                                <div class="rounded-circle d-flex align-items-center justify-content-center prayer-item-icon-box ${dol}{isNext ? 'prayer-icon-next' : 'prayer-icon-normal'}"
                                     style="width: 28px; height: 28px; font-size: 13px; background: ${dol}{iconBg}; color: ${dol}{iconColor};">
                                    <i class="bi ${dol}{p.icon}"></i>
                                </div>
                                <button type="button" class="btn btn-link p-0 text-decoration-none prayer-sound-btn" onclick="event.stopPropagation(); openSinglePrayerSoundModal('${dol}{p.id}')" title="${dol}{sound.title}">
                                    <i class="bi ${dol}{sound.icon} ${dol}{sound.color}" style="font-size: 14px;"></i>
                                </button>
                            </div>
                            <small class="fw-bold d-block mt-1 prayer-name-label" style="font-size: 12px; color: ${dol}{fontColor};">${dol}{p.name}</small>
                            <div class="fw-black fs-6 font-monospace mt-1 prayer-time-value" style="color: ${dol}{fontColor};">${dol}{p.time || '--:--'}</div>
                            ${dol}{isNext ? `<span class="badge rounded-pill mt-1 prayer-next-badge" style="font-size: 8px; background: ${dol}{nextBadgeBg}; color: #ffffff;">Berikutnya</span>` : ''}
                        </div>
                    </div>
                `;
            });

            grid.innerHTML = html;
        }

        async function refreshPrayerScheduleAndLocation() {
            const icon = document.getElementById('prayerRefreshIcon');
            if (icon) icon.classList.add('spin-animation');
            try {
                const res = await fetch('/api/prayer/refresh', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ activeTimeZone: window.dashboardState.activeTimeZone || 'WIB' })
                });
                const json = await res.json();
                if (json.success) {
                    if (json.prayerSchedule) window.dashboardState.prayerSchedule = json.prayerSchedule;
                    if (json.prayerScheduleSource) window.dashboardState.prayerScheduleSource = json.prayerScheduleSource;
                    if (json.prayerLocationText) window.dashboardState.prayerLocationText = json.prayerLocationText;

                    const locEl = document.getElementById('prayerLocationLabel');
                    if (locEl) locEl.textContent = json.prayerLocationText || 'Lokasi Sinkronisasi';
                    const srcEl = document.getElementById('prayerSourceLabel');
                    if (srcEl) srcEl.textContent = json.prayerScheduleSource || 'Kemenag RI';

                    renderDashboardPrayerTimes();
                }
            } catch(e) {
                console.error('Error refreshing prayer schedule:', e);
            } finally {
                if (icon) icon.classList.remove('spin-animation');
            }
        }

        function openPrayerSettingsModal() {
            renderPrayerSettingsModalList();
            const modalEl = document.getElementById('prayerSettingsModal');
            if (modalEl) {
                const modal = new bootstrap.Modal(modalEl);
                modal.show();
            }
        }

        function renderPrayerSettingsModalList() {
            const container = document.getElementById('prayerSettingsList');
            if (!container) return;

            const isDark = (document.documentElement.getAttribute('data-theme') === 'dark') || document.body.classList.contains('dark');
            const prayers = [
                { id: 'imsak', name: 'Imsak', icon: 'bi-clock-history' },
                { id: 'subuh', name: 'Subuh', icon: 'bi-brightness-alt-high-fill' },
                { id: 'dzuhur', name: 'Dzuhur', icon: 'bi-sun-fill' },
                { id: 'ashar', name: 'Ashar', icon: 'bi-brightness-high-fill' },
                { id: 'maghrib', name: 'Maghrib', icon: 'bi-sunset-fill' },
                { id: 'isya', name: 'Isya', icon: 'bi-moon-stars-fill' }
            ];

            let html = '';
            prayers.forEach(p => {
                const mode = getPrayerSoundMode(p.id);
                const sound = getPrayerSoundIcon(mode);
                const label = (mode === 0) ? 'Senyap (Mati)' : (mode === 1 ? 'Nada Bip / Lonceng Singkat' : 'Suara Azan Madinah (Offline)');
                const itemBg = isDark ? '#0f172a' : 'var(--sidebar-bg, #f8fafc)';
                const itemBorder = isDark ? 'border: 1px solid #334155;' : 'border: 1px solid var(--border-color);';
                const nameColor = isDark ? '#ffffff' : 'var(--text-main)';
                const subColor = isDark ? '#cbd5e1' : 'var(--bs-secondary)';

                html += `
                    <div class="card p-3 rounded-4 mb-2 d-flex flex-row align-items-center justify-content-between cursor-pointer"
                         onclick="openSinglePrayerSoundModal('${dol}{p.id}')" style="background: ${dol}{itemBg}; ${dol}{itemBorder} transition: all 0.2s ease;">
                        <div class="d-flex align-items-center gap-3">
                            <div class="rounded-circle d-flex align-items-center justify-content-center" style="width: 38px; height: 38px; background: ${dol}{isDark ? 'rgba(56, 189, 248, 0.18)' : 'rgba(13, 110, 253, 0.1)'}; color: ${dol}{isDark ? '#38bdf8' : '#0d6efd'};">
                                <i class="bi ${dol}{p.icon} fs-5"></i>
                            </div>
                            <div>
                                <h6 class="fw-bold mb-0" style="color: ${dol}{nameColor};">${dol}{p.name}</h6>
                                <small class="d-flex align-items-center gap-1" style="color: ${dol}{subColor};">
                                    <i class="bi ${dol}{sound.icon} ${dol}{sound.color}"></i> ${dol}{label}
                                </small>
                            </div>
                        </div>
                        <button type="button" class="btn btn-sm rounded-pill px-3 py-1" style="font-size: 12px; background: ${dol}{isDark ? 'rgba(56, 189, 248, 0.15)' : 'transparent'}; border: 1px solid ${dol}{isDark ? '#38bdf8' : 'var(--bs-primary)'}; color: ${dol}{isDark ? '#ffffff' : 'var(--bs-primary)'};">
                            Ubah
                        </button>
                    </div>
                `;
            });
            container.innerHTML = html;
        }

        let currentEditingPrayerId = '';
        let temporarySelectedSoundMode = 2;

        function openSinglePrayerSoundModal(prayerId) {
            currentEditingPrayerId = prayerId;
            temporarySelectedSoundMode = getPrayerSoundMode(prayerId);

            const titleEl = document.getElementById('singlePrayerTitle');
            const prayerName = prayerId.charAt(0).toUpperCase() + prayerId.slice(1);
            if (titleEl) titleEl.textContent = `Suara Sholat ${dol}{prayerName}`;

            renderSinglePrayerOptionsList();

            const singleModalEl = document.getElementById('singlePrayerSoundModal');
            if (singleModalEl) {
                const modal = new bootstrap.Modal(singleModalEl);
                modal.show();
            }
        }

        function renderSinglePrayerOptionsList() {
            const container = document.getElementById('singlePrayerOptionsList');
            if (!container) return;

            const isDark = (document.documentElement.getAttribute('data-theme') === 'dark') || document.body.classList.contains('dark');
            const options = [
                {
                    mode: 0,
                    title: 'Senyap / Mati',
                    desc: 'Tidak ada suara atau nada saat waktu tiba',
                    icon: 'bi-volume-mute-fill',
                    color: isDark ? 'text-light' : 'text-secondary'
                },
                {
                    mode: 1,
                    title: 'Nada Bip / Lonceng Singkat',
                    desc: 'Notifikasi bunyi penanda waktu sholat',
                    icon: 'bi-bell-fill',
                    color: 'text-warning',
                    testFn: "playSystemBeepChime('Sholat')"
                },
                {
                    mode: 2,
                    title: 'Suara Azan Madinah Merdu (Offline)',
                    desc: 'Melantunkan audio azan Madinah My Singghara 100% offline',
                    icon: 'bi-volume-up-fill',
                    color: isDark ? 'text-info' : 'text-primary',
                    testFn: "playPrayerAzan('Sholat', true)"
                }
            ];

            let html = '';
            options.forEach(opt => {
                const isSelected = (temporarySelectedSoundMode === opt.mode);
                const optBg = isDark
                    ? (isSelected ? 'rgba(14, 165, 233, 0.22)' : '#0f172a')
                    : (isSelected ? 'rgba(13, 110, 253, 0.08)' : 'var(--sidebar-bg, #f8fafc)');
                const optBorder = isDark
                    ? (isSelected ? 'border: 2px solid #38bdf8;' : 'border: 1px solid #334155;')
                    : (isSelected ? 'border: 2px solid #0d6efd;' : 'border: 1px solid var(--border-color);');
                const optTitleColor = isDark ? '#ffffff' : (isSelected ? 'var(--bs-primary)' : 'var(--text-main)');
                const optDescColor = isDark ? '#cbd5e1' : 'var(--bs-secondary)';

                html += `
                    <div class="card p-3 rounded-4 cursor-pointer mb-2"
                         onclick="selectSinglePrayerMode(${dol}{opt.mode})"
                         style="background: ${dol}{optBg}; ${dol}{optBorder} transition: all 0.2s ease;">
                        <div class="d-flex align-items-center justify-content-between">
                            <div class="d-flex align-items-center gap-3">
                                <input class="form-check-input mt-0" type="radio" name="singleSoundRadio" ${dol}{isSelected ? 'checked' : ''} style="cursor: pointer; width: 1.2em; height: 1.2em;">
                                <div>
                                    <div class="fw-bold d-flex align-items-center gap-2" style="color: ${dol}{optTitleColor};">
                                        <i class="bi ${dol}{opt.icon} ${dol}{opt.color}"></i> ${dol}{opt.title}
                                    </div>
                                    <small style="color: ${dol}{optDescColor};">${dol}{opt.desc}</small>
                                </div>
                            </div>
                            ${dol}{opt.testFn ? `
                                <button type="button" class="btn btn-sm rounded-pill px-2 py-1 ms-2" onclick="event.stopPropagation(); ${dol}{opt.testFn};" title="Dengarkan Contoh" style="font-size: 11px; background: ${dol}{isDark ? 'rgba(255,255,255,0.1)' : 'transparent'}; border: 1px solid ${dol}{isDark ? '#475569' : '#ced4da'}; color: ${dol}{isDark ? '#ffffff' : 'inherit'};">
                                    <i class="bi bi-play-fill"></i> Tes
                                </button>
                            ` : ''}
                        </div>
                    </div>
                `;
            });
            container.innerHTML = html;
        }

        function selectSinglePrayerMode(mode) {
            temporarySelectedSoundMode = mode;
            renderSinglePrayerOptionsList();
        }

        async function saveSinglePrayerSoundSetting() {
            if (!currentEditingPrayerId) return;
            const updatedSettings = Object.assign({}, window.dashboardState.prayerSoundSettings || {});
            updatedSettings[currentEditingPrayerId] = temporarySelectedSoundMode;
            window.dashboardState.prayerSoundSettings = updatedSettings;

            renderDashboardPrayerTimes();
            renderPrayerSettingsModalList();

            const singleModalEl = document.getElementById('singlePrayerSoundModal');
            if (singleModalEl) {
                const modal = bootstrap.Modal.getInstance(singleModalEl);
                if (modal) modal.hide();
            }

            // Sync with backend local server
            try {
                fetch('/api/prayer/settings', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({
                        prayerSoundSettings: updatedSettings
                    })
                }).catch(() => {});
            } catch(e) {}
        }

        // --- J. SUB-COMPONENT: INTERACTIVE CALENDAR ---
        function renderInteractiveCalendar() {
            const container = document.getElementById('interactiveCalendarGrid');
            if (!container) return;

            const m = window.dashboardState.viewCalendarMonth;
            const y = window.dashboardState.viewCalendarYear;

            const title = document.getElementById('calendarMonthTitle');
            if (title) {
                title.textContent = `${dol}{INDO_MONTHS[m]} ${dol}{y}`;
            }

            const firstDayIndex = new Date(y, m, 1).getDay(); // 0 = Sunday
            const totalDaysInMonth = new Date(y, m + 1, 0).getDate();

            const today = new Date();
            const isCurrentMonthAndYear = (today.getFullYear() === y && today.getMonth() === m);
            const currentDayNumber = today.getDate();

            let html = `
                <div class="calendar-table-wrapper w-100">
                    <div class="d-grid text-center fw-bold py-1 mb-1 text-secondary" style="grid-template-columns: repeat(7, minmax(0, 1fr)); border-bottom: 1px solid var(--border-color); font-size: 11px;">
                        <div class="text-danger">Min</div>
                        <div>Sen</div>
                        <div>Sel</div>
                        <div>Rab</div>
                        <div>Kam</div>
                        <div>Jum</div>
                        <div>Sab</div>
                    </div>
                    <div class="d-grid gap-1 gap-md-2 w-100" style="grid-template-columns: repeat(7, minmax(0, 1fr));">
            `;

            // Empty slots for previous month
            for (let i = 0; i < firstDayIndex; i++) {
                html += `<div class="p-1 opacity-0"></div>`;
            }

            // Days of current view month
            for (let day = 1; day <= totalDaysInMonth; day++) {
                const isToday = isCurrentMonthAndYear && (day === currentDayNumber);
                const dayDate = new Date(y, m, day);
                const isSunday = (dayDate.getDay() === 0);

                const jData = getJavaneseDate(y, m + 1, day);
                const holiday = getHolidayForDate(y, m + 1, day);
                const observance = getObservanceForDate(m + 1, day);

                const dateKey = `${dol}{y}-${dol}{String(m + 1).padStart(2, '0')}-${dol}{String(day).padStart(2, '0')}`;
                const hasAgenda = window.dashboardState.allAgendas.some(a => a.tanggal === dateKey);

                let cellBg = 'var(--sidebar-bg)';
                let cellBorder = 'var(--border-color)';
                let numColor = 'var(--text-main)';

                if (isToday) {
                    cellBg = 'rgba(59, 130, 246, 0.15)';
                    cellBorder = '#3B82F6';
                    numColor = '#3B82F6';
                } else if (holiday || isSunday) {
                    numColor = '#EF4444';
                    if (holiday) cellBg = 'rgba(239, 68, 68, 0.08)';
                } else if (observance) {
                    numColor = '#10B981';
                }

                html += `
                    <div class="card p-1 p-md-2 text-center cursor-pointer position-relative hover-scale overflow-hidden" 
                         onclick="showCalendarDayDetail(${dol}{y}, ${dol}{m + 1}, ${dol}{day})"
                         style="border-radius: 10px; background: ${dol}{cellBg}; border: 1px solid ${dol}{cellBorder}; min-height: 48px; max-width: 100%;"
                         title="${dol}{day} ${dol}{INDO_MONTHS[m]}: ${dol}{jData.dina} ${dol}{jData.pasaran}${dol}{holiday ? ' (' + holiday + ')' : ''}">
                        
                        <div class="fw-bold" style="color: ${dol}{numColor}; line-height: 1.1; font-size: calc(12px + 0.3vw);">
                            ${dol}{day}
                        </div>
                        <small class="d-block mt-0 mt-md-1 font-monospace text-truncate px-0" style="font-size: 8px; opacity: 0.85; color: ${dol}{numColor}; line-height: 1;">
                            ${dol}{jData.pasaran}
                        </small>

                        <!-- Indicators Dots -->
                        <div class="d-flex justify-content-center gap-1 mt-1">
                            ${dol}{holiday ? '<span class="rounded-circle flex-shrink-0" style="width: 4px; height: 4px; background-color: #EF4444;"></span>' : ''}
                            ${dol}{observance ? '<span class="rounded-circle flex-shrink-0" style="width: 4px; height: 4px; background-color: #10B981;"></span>' : ''}
                            ${dol}{hasAgenda ? '<span class="rounded-circle flex-shrink-0" style="width: 4px; height: 4px; background-color: #F59E0B;"></span>' : ''}
                        </div>
                    </div>
                `;
            }

            html += `
                    </div>
                </div>
            `;

            container.innerHTML = html;
        }

        function navigateCalendarMonth(delta) {
            let m = window.dashboardState.viewCalendarMonth + delta;
            let y = window.dashboardState.viewCalendarYear;
            if (m < 0) {
                m = 11;
                y--;
            } else if (m > 11) {
                m = 0;
                y++;
            }
            window.dashboardState.viewCalendarMonth = m;
            window.dashboardState.viewCalendarYear = y;
            renderInteractiveCalendar();
        }

        function resetCalendarToCurrentMonth() {
            const now = new Date();
            window.dashboardState.viewCalendarMonth = now.getMonth();
            window.dashboardState.viewCalendarYear = now.getFullYear();
            renderInteractiveCalendar();
        }

        // --- K. CALENDAR DAY DETAIL POPUP (CLICK EVENT) ---
        function showCalendarDayDetail(y, m, d) {
            const modalContent = document.getElementById('calendarDayModalContent');
            if (!modalContent) return;

            const jData = getJavaneseDate(y, m, d);
            const hData = getHijriDate(y, m, d);
            const holiday = getHolidayForDate(y, m, d);
            const observance = getObservanceForDate(m, d);

            const dateKey = `${dol}{y}-${dol}{String(m).padStart(2, '0')}-${dol}{String(d).padStart(2, '0')}`;
            const agendas = window.dashboardState.allAgendas.filter(a => a.tanggal === dateKey);

            const targetDate = new Date(y, m - 1, d);
            const dayName = INDO_DAYS[targetDate.getDay()];
            const masehiStr = `${dol}{dayName}, ${dol}{d} ${dol}{INDO_MONTHS[m - 1]} ${dol}{y}`;

            let html = `
                <div class="modal-header border-bottom-0 pb-1">
                    <div>
                        <span class="badge bg-primary bg-opacity-20 text-primary rounded-pill px-3 py-1 mb-1">
                            <i class="bi bi-calendar-check me-1"></i> Detail Tanggal & Acara
                        </span>
                        <h4 class="fw-bold mb-0 text-main">${dol}{masehiStr}</h4>
                    </div>
                    <button type="button" class="btn-close btn-close-custom" data-bs-dismiss="modal"></button>
                </div>
                <div class="modal-body pt-2">
                    <!-- CALENDAR EQUIVALENTS CARD -->
                    <div class="p-3 rounded-4 mb-3" style="background: var(--sidebar-bg); border: 1px solid var(--border-color);">
                        <div class="mb-2 pb-2 border-bottom border-secondary border-opacity-25">
                            <div class="text-secondary small">Penanggalan Jawa:</div>
                            <div class="fw-bold text-main fs-6">${dol}{jData.dina} ${dol}{jData.pasaran}, Wuku ${dol}{jData.wuku}</div>
                        </div>
                        <div>
                            <div class="text-secondary small">Kalender Hijriyah:</div>
                            <div class="fw-bold text-success fs-6">${dol}{hData}</div>
                        </div>
                    </div>

                    <!-- HOLIDAYS & OBSERVANCES -->
                    ${dol}{holiday ? `
                        <div class="alert alert-danger d-flex align-items-center gap-2 py-2 px-3 mb-2 rounded-3">
                            <i class="bi bi-flag-fill fs-5"></i>
                            <div>
                                <strong class="d-block">Hari Libur Nasional:</strong>
                                <span>${dol}{escapeHtml(holiday)}</span>
                            </div>
                        </div>
                    ` : ''}

                    ${dol}{observance ? `
                        <div class="alert alert-success d-flex align-items-center gap-2 py-2 px-3 mb-2 rounded-3">
                            <i class="bi bi-bookmark-star-fill fs-5"></i>
                            <div>
                                <strong class="d-block">Hari Peringatan Nasional / Internasional:</strong>
                                <span>${dol}{escapeHtml(observance)}</span>
                            </div>
                        </div>
                    ` : ''}

                    <!-- REGISTERED AGENDAS -->
                    <div class="mt-3">
                        <h6 class="fw-bold mb-2 text-main d-flex justify-content-between align-items-center">
                            <span><i class="bi bi-calendar-event text-warning me-1"></i> Agenda & Jadwal (${dol}{agendas.length})</span>
                            <button class="btn btn-sm btn-outline-warning py-0 px-2 rounded-pill" onclick="bootstrap.Modal.getInstance(document.getElementById('calendarDayModal')).hide(); switchTabByName('agenda');">
                                + Tambah
                            </button>
                        </h6>
            `;

            if (agendas.length === 0) {
                html += `<div class="text-secondary text-center py-3 small fst-italic">Tidak ada kegiatan atau agenda pada tanggal ini.</div>`;
            } else {
                html += `<div class="list-group list-group-flush">`;
                agendas.forEach(a => {
                    html += `
                        <div class="list-group-item px-0 py-2 border-bottom d-flex justify-content-between align-items-center bg-transparent">
                            <div>
                                <div class="fw-bold text-main small">${dol}{escapeHtml(a.judul)}</div>
                                <small class="text-secondary">${dol}{escapeHtml(a.detail || '')}</small>
                            </div>
                            <span class="badge bg-warning text-dark">${dol}{escapeHtml(a.tipe || 'Agenda')}</span>
                        </div>
                    `;
                });
                html += `</div>`;
            }

            html += `
                    </div>
                </div>
                <div class="modal-footer border-top-0 pt-0">
                    <button type="button" class="btn btn-secondary w-100 rounded-3" data-bs-dismiss="modal">Tutup</button>
                </div>
            `;

            modalContent.innerHTML = html;
            const modal = new bootstrap.Modal(document.getElementById('calendarDayModal'));
            modal.show();
        }

        // --- L. ALL APPLICATIONS MODAL ---
        const ALL_MY_APPS = [
            { id: 'rekap_belanja', title: "Rekap Belanja Pasar", desc: "Daftar belanja pasar lengkap dalam satuan kg, pcs, ikat, liter, dll. dengan rekap kuantitas dan estimasi biaya.", icon: "bi-basket3-fill", color: "#10B981", badge: "Belanja Pasar" },
            { id: 'quran', title: "Al-Qur'an Digital", desc: "Kitab suci 114 Surah lengkap teks Arab, terjemahan Indonesia, dan audio murottal.", icon: "bi-journal-bookmark-fill", color: "#00796B", badge: "Spiritual & Ibadah" },
            { id: 'pembukuan', title: "Pembukuan Usaha", desc: "Kalkulator modal, estimasi pendapatan harian/bulanan, dan pengeluaran operasional.", icon: "bi-graph-up-arrow", color: "#E65100", badge: "Kalkulator Usaha" },
            { id: 'jurnal_umum', title: "Laporan Keuangan", desc: "Siklus akuntansi lengkap: Jurnal Umum, Buku Besar, Neraca Saldo, Laba Rugi, hingga Jurnal Penutup.", icon: "bi-file-earmark-spreadsheet", color: "#00838F", badge: "Siklus Akuntansi" },
            { id: 'kasir', title: "Kasir POS", desc: "Sistem Point of Sale modern dengan barcode scanner kamera, cetak struk belanja, dan stok barang.", icon: "bi-shop", color: "#00897B", badge: "Point of Sale" },
            { id: 'savings', title: "Catatan Tabungan", desc: "Kelola target tabungan, impian finansial, serta riwayat setoran dan penarikan dana.", icon: "bi-wallet2", color: "#10B981", badge: "Finansial" },
            { id: 'notes', title: "Catatan Harian", desc: "Catatan cepat harian, ide penting, dan rekap informasi pribadi.", icon: "bi-journal-text", color: "#6366F1", badge: "Produktivitas" },
            { id: 'agenda', title: "Jadwal & Agenda", desc: "Manajemen kalender kegiatan, pengingat jadwal kerja harian, dan acara penting.", icon: "bi-calendar-event", color: "#F59E0B", badge: "Jadwal Kerja" },
            { id: 'musik', title: "Musik Player", desc: "Pemutar audio MP3/WAV/AAC lokal dengan daftar putar dan kontrol audio lengkap.", icon: "bi-music-note-beamed", color: "#E91E63", badge: "Pemutar Musik" },
            { id: 'spinner', title: "Spinner Undian", desc: "Roda putar keberuntungan untuk doorprize, arisan, undian acak, atau pembagian tim.", icon: "bi-arrow-repeat", color: "#8338EC", badge: "Undian Acak" },
            { id: 'block', title: "Block Puzzle", desc: "Game susun balok 8x8 klasik dengan combo eliminasi balok, power-ups, dan high score.", icon: "bi-grid-fill", color: "#2C5E6A", badge: "Game Hiburan" },
            { id: 'brick_game', title: "Brick Game", desc: "Game arcade retro LCD 9999-in-1 dengan mode Tetris, Snake, Balap Mobil, dan Tembak Tank.", icon: "bi-controller", color: "#306230", badge: "Game Retro" }
        ];

        function buildAllAppsModalContent() {
            const grid = document.getElementById('allAppsModalGrid');
            if (!grid) return;

            let html = '';
            ALL_MY_APPS.forEach(app => {
                html += `
                    <div class="col-12 col-md-6">
                        <div class="card card-custom p-3 h-100 cursor-pointer hover-scale d-flex flex-row align-items-center gap-3"
                             onclick="bootstrap.Modal.getInstance(document.getElementById('allAppsModal')).hide(); switchTabByName('${dol}{app.id}');"
                             style="border-radius: 16px; border: 1px solid var(--border-color);">
                            <div class="app-icon-squircle flex-shrink-0" style="background-color: ${dol}{app.color}; width: 48px; height: 48px; font-size: 1.4rem;">
                                <i class="bi ${dol}{app.icon}"></i>
                            </div>
                            <div class="overflow-hidden flex-grow-1">
                                <div class="d-flex justify-content-between align-items-center mb-1">
                                    <h6 class="fw-bold mb-0 text-main text-truncate">${dol}{escapeHtml(app.title)}</h6>
                                    <span class="badge bg-secondary bg-opacity-25 text-secondary" style="font-size: 10px;">${dol}{app.badge}</span>
                                </div>
                                <p class="text-secondary small mb-0 text-truncate">${dol}{escapeHtml(app.desc)}</p>
                            </div>
                        </div>
                    </div>
                `;
            });

            grid.innerHTML = html;
        }

        function openAllAppsModal() {
            buildAllAppsModalContent();
            const modal = new bootstrap.Modal(document.getElementById('allAppsModal'));
            modal.show();
        }
        """
    }
}
