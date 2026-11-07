package com.example.web

object KalkulatorWebScript {
    fun getScript(dol: String): String {
        return """
        // =========================================================================
        // --- KALKULATOR (100% OFFLINE, PORT 1:1 KALKULATORSCREEN.KT MY SINGGHARA)
        // =========================================================================

        const KALC_ACCENT = '#0EA5E9';

        const KALC_SCI_KEYS = [
            [ { l: 'SHIFT', t: 'SHIFT' }, { l: 'x²', t: '²', al: '2ˣ', at: '2^(' }, { l: 'x³', t: '³', al: '3ˣ', at: '3^(' }, { l: 'xʸ', t: '^(', al: 'logb(', at: 'logb(' }, { l: '√', t: '√(', al: '|x|', at: 'abs(' } ],
            [ { l: 'sin', t: 'sin(', al: 'sin⁻¹', at: 'asin(' }, { l: 'cos', t: 'cos(', al: 'cos⁻¹', at: 'acos(' }, { l: 'tan', t: 'tan(', al: 'tan⁻¹', at: 'atan(' }, { l: 'log', t: 'log(', al: '10ˣ', at: '10^(' }, { l: 'ln', t: 'ln(', al: 'eˣ', at: 'e^(' } ],
            [ { l: 'sinh', t: 'sinh(', al: 'asinh', at: 'asinh(' }, { l: 'cosh', t: 'cosh(', al: 'acosh', at: 'acosh(' }, { l: 'tanh', t: 'tanh(', al: 'atanh', at: 'atanh(' }, { l: 'ⁿ√', t: 'root(' }, { l: '∛', t: '∛(' } ],
            [ { l: '(', t: '(' }, { l: ')', t: ')' }, { l: 'π', t: 'π' }, { l: 'e', t: 'e' }, { l: 'EXP', t: '×10^(' } ],
            [ { l: '1/x', t: '⁻¹' }, { l: 'x!', t: '!' }, { l: ',', t: ',' }, { l: 'mod', t: 'mod(' }, { l: 'ANS', t: 'Ans' } ]
        ];

        const KALC_MEM_KEYS = [
            { l: 'MC', t: 'MC' }, { l: 'MR', t: 'M' }, { l: 'M+', t: 'M+' },
            { l: 'M−', t: 'M-' }, { l: 'STO', t: 'STO' }, { l: 'RCL', t: 'A' }
        ];

        const KALC_STATS_KEYS = [
            { l: 'D+', t: 'D+' }, { l: 'n', t: 'n' }, { l: 'Σx', t: 'sum' },
            { l: 'Σx²', t: 'sumsq' }, { l: 'x̄', t: 'mean' }, { l: 'σx', t: 'sigp' },
            { l: 'sx', t: 'sigs' }, { l: 'CL', t: 'CL' }
        ];

        const KALC_MAIN_ROWS = [
            [ { l: 'C', t: 'C', c: 'clear' }, { l: '⌫', t: 'BS', c: 'num' }, { l: '%', t: '%', c: 'op' }, { l: '÷', t: '÷', c: 'op' } ],
            [ { l: '7', t: '7', c: 'num' }, { l: '8', t: '8', c: 'num' }, { l: '9', t: '9', c: 'num' }, { l: '×', t: '×', c: 'op' } ],
            [ { l: '4', t: '4', c: 'num' }, { l: '5', t: '5', c: 'num' }, { l: '6', t: '6', c: 'num' }, { l: '−', t: '−', c: 'op' } ],
            [ { l: '1', t: '1', c: 'num' }, { l: '2', t: '2', c: 'num' }, { l: '3', t: '3', c: 'num' }, { l: '+', t: '+', c: 'op' } ],
            [ { l: '0', t: '0', c: 'num', z: true }, { l: '.', t: '.', c: 'num' }, { l: '=', t: 'EQ', c: 'eq' } ]
        ];

        const KALC_STAT_TOKENS = ['n', 'sum', 'sumsq', 'mean', 'sigp', 'sigs'];
        const KALC_BINARY_OPS = ['+', '−', '×', '÷', '^'];
        const KALC_CONSTANT_WORDS = ['e', 'M', 'A', 'Ans'];
        const KALC_MULTI_CHAR_TOKENS = [
            '×10^(', 'asinh(', 'acosh(', 'atanh(', 'asin(', 'acos(', 'atan(',
            'sinh(', 'cosh(', 'tanh(', 'root(', 'logb(', 'sin(', 'cos(', 'tan(',
            'log(', 'abs(', 'mod(', 'ln(', '√(', '∛(', '10^(', 'e^(', 'Ans', '⁻¹'
        ];

        const KALC_CSS = `
        .kalc-page { display: flex; flex-direction: column; height: calc(100vh - 112px); min-height: 460px; max-width: 680px; margin: 0 auto; padding: 8px 12px 10px; box-sizing: border-box; }
        .kalc-header { display: flex; align-items: center; gap: 10px; padding-bottom: 6px; flex: none; }
        .kalc-logo { width: 38px; height: 38px; border-radius: 50%; background: rgba(14, 165, 233, 0.15); color: #0EA5E9; display: flex; align-items: center; justify-content: center; font-size: 22px; flex: none; }
        .kalc-title { font-size: 18px; font-weight: 700; color: var(--text-main, #1e293b); }
        .kalc-flex-spacer { flex: 1; }
        .kalc-n { font-size: 11px; color: var(--text-sub, #64748b); margin-right: 6px; }
        .kalc-mini { height: 24px; border: none; border-radius: 8px; background: transparent; color: var(--text-sub, #64748b); padding: 0 7px; display: inline-flex; align-items: center; justify-content: center; cursor: pointer; font-size: 14px; line-height: 1; flex: none; }
        .kalc-mini.kalc-active { background: rgba(14, 165, 233, 0.16); color: #0EA5E9; }
        .kalc-fx { font-family: Georgia, 'Times New Roman', serif; font-style: italic; font-size: 14px; font-weight: 600; }
        .kalc-unit { font-size: 10px; font-weight: 700; letter-spacing: 0.3px; }
        .kalc-body { flex: 1; min-height: 0; display: flex; flex-direction: column; gap: 6px; }
        .kalc-left, .kalc-right { display: contents; }
        .kalc-display { flex: none; order: 0; background: rgba(14, 165, 233, 0.08); border-radius: 18px; padding: 8px 14px; }
        .kalc-chips-row { display: flex; align-items: center; gap: 5px; min-height: 24px; }
        .kalc-chip { display: none; font-size: 9px; font-weight: 700; color: var(--text-sub, #64748b); background: var(--surface-hover, rgba(127, 127, 127, 0.16)); border-radius: 6px; padding: 1px 5px; }
        .kalc-chip.kalc-show { display: inline-block; }
        .kalc-prev { display: none; font-size: 12px; color: var(--text-sub, #64748b); white-space: nowrap; overflow-x: auto; scrollbar-width: none; }
        .kalc-expr { font-size: 29px; font-weight: 700; color: var(--text-main, #1e293b); white-space: nowrap; overflow-x: auto; scrollbar-width: none; min-height: 38px; }
        .kalc-expr.kalc-error { color: var(--danger-color, #e74c3c); }
        .kalc-hint { display: none; font-size: 11px; color: #0EA5E9; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
        .kalc-frac { display: none; font-size: 12px; color: var(--text-sub, #64748b); text-align: right; white-space: nowrap; overflow-x: auto; scrollbar-width: none; }
        .kalc-live { font-size: 16px; font-weight: 600; color: var(--text-sub, #64748b); text-align: right; white-space: nowrap; overflow-x: auto; scrollbar-width: none; min-height: 22px; }
        .kalc-live.kalc-error { color: var(--danger-color, #e74c3c); }
        .kalc-prev::-webkit-scrollbar, .kalc-expr::-webkit-scrollbar, .kalc-frac::-webkit-scrollbar, .kalc-live::-webkit-scrollbar { display: none; }
        .kalc-mem { order: 1; display: flex; gap: 5px; flex: 0.58 1 0%; min-height: 34px; }
        .kalc-sci { order: 2; display: flex; flex-direction: column; gap: 5px; flex: 3.9 1 0%; min-height: 0; }
        .kalc-sci-row { display: flex; gap: 5px; flex: 1 1 0%; min-height: 0; }
        .kalc-stats { order: 3; display: flex; gap: 4px; flex: 0.58 1 0%; min-height: 34px; }
        .kalc-keypad { order: 4; display: flex; flex-direction: column; gap: 8px; flex: 7.2 1 0%; min-height: 0; }
        .kalc-keypad-row { display: flex; gap: 8px; flex: 1 1 0%; min-height: 0; }
        .kalc-key { border: none; border-radius: 16px; display: flex; align-items: center; justify-content: center; font-weight: 600; cursor: pointer; user-select: none; -webkit-user-select: none; padding: 0; min-width: 0; min-height: 0; overflow: hidden; white-space: nowrap; transition: transform 0.09s ease; font-family: inherit; }
        .kalc-key:active { transform: scale(0.93); }
        .kalc-keypad .kalc-key { font-size: 26px; flex: 1 1 0%; }
        .kalc-key-num { background: var(--surface-hover, rgba(127, 127, 127, 0.16)); color: var(--text-main, #1e293b); }
        .kalc-key-op { background: rgba(14, 165, 233, 0.14); color: #0EA5E9; }
        .kalc-key-clear { background: var(--danger-subtle, rgba(231, 76, 60, 0.12)); color: var(--danger-color, #e74c3c); }
        .kalc-key-eq { background: #0EA5E9; color: #fff; font-size: 30px !important; }
        .kalc-key-zero { flex: 2 1 0% !important; }
        .kalc-sci .kalc-key { background: var(--surface-subtle, rgba(127, 127, 127, 0.08)); color: #0EA5E9; font-size: 13px; flex: 1 1 0%; }
        .kalc-sci .kalc-key.kalc-key-long { font-size: 11px; }
        .kalc-sci .kalc-key-shift { background: rgba(14, 165, 233, 0.18); }
        .kalc-sci .kalc-key-shift.kalc-active { background: #0EA5E9; color: #fff; }
        .kalc-mem .kalc-key { background: var(--surface-subtle, rgba(127, 127, 127, 0.08)); color: #0EA5E9; font-size: 12px; flex: 1 1 0%; }
        .kalc-mem .kalc-key.kalc-mr-active { background: rgba(14, 165, 233, 0.2); }
        .kalc-stats .kalc-key { background: var(--surface-subtle, rgba(127, 127, 127, 0.08)); color: var(--text-sub, #64748b); font-size: 11px; flex: 1 1 0%; }
        .kalc-page.kalc-has-data .kalc-stats .kalc-key { color: #0EA5E9; }
        .kalc-page.kalc-scientific .kalc-keypad { flex-grow: 5; }
        .kalc-page:not(.kalc-scientific) .kalc-mem, .kalc-page:not(.kalc-scientific) .kalc-sci, .kalc-page:not(.kalc-scientific) .kalc-stats, .kalc-page:not(.kalc-scientific) .kalc-unit { display: none; }
        .kalc-page.kalc-landscape.kalc-scientific { max-width: 1200px; }
        .kalc-page.kalc-landscape.kalc-scientific .kalc-body { flex-direction: row; gap: 0; }
        .kalc-page.kalc-landscape.kalc-scientific .kalc-left { display: flex; flex-direction: column; flex: 1 1 0%; gap: 4px; min-width: 0; }
        .kalc-page.kalc-landscape.kalc-scientific .kalc-right { display: flex; flex-direction: column; flex: 1.4 1 0%; gap: 6px; padding-left: 8px; min-width: 0; }
        .kalc-page.kalc-landscape.kalc-scientific .kalc-mem { order: 0; flex: 0.6 1 0%; }
        .kalc-page.kalc-landscape.kalc-scientific .kalc-sci { order: 0; flex: 3.4 1 0%; }
        .kalc-page.kalc-landscape.kalc-scientific .kalc-stats { order: 0; flex: 0.6 1 0%; }
        .kalc-page.kalc-landscape.kalc-scientific .kalc-display { order: 0; }
        .kalc-page.kalc-landscape.kalc-scientific .kalc-keypad { order: 0; flex: 1 1 0%; }
        @keyframes kalcShake { 0% { transform: translateX(0); } 20% { transform: translateX(-9px); } 50% { transform: translateX(9px); } 75% { transform: translateX(-5px); } 100% { transform: translateX(0); } }
        .kalc-shake { animation: kalcShake 0.25s ease; }
        @keyframes kalcPop { 0% { transform: scale(0.9); } 60% { transform: scale(1.04); } 100% { transform: scale(1); } }
        .kalc-pop { animation: kalcPop 0.3s ease; }
        @media (max-width: 768px) {
            .kalc-page { height: calc(100vh - 80px); min-height: 420px; padding: 8px 4px; }
        }
        `;

        // --- mesin hitung (sama persis dengan KalkulatorScreen.kt) ---------------

        function kalcIsAsciiLetter(c) { return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z'); }
        function kalcIsDigit(c) { return c >= '0' && c <= '9'; }
        function kalcIsLetter(c) { return c.toLowerCase() !== c.toUpperCase(); }
        function kalcIsBinaryOp(c) { return c !== null && KALC_BINARY_OPS.indexOf(c) >= 0; }

        function kalcStartsOperand(token) {
            const first = token.length > 0 ? token.charAt(0) : null;
            if (first === null) return false;
            return kalcIsDigit(first) || first === '(' || first === '.' ||
                first === 'π' || first === '√' || first === '∛' || kalcIsAsciiLetter(first);
        }

        function kalcDropOneToken(expr) {
            for (let i = 0; i < KALC_MULTI_CHAR_TOKENS.length; i++) {
                const t = KALC_MULTI_CHAR_TOKENS[i];
                if (expr.endsWith(t)) return expr.slice(0, expr.length - t.length);
            }
            return expr.slice(0, -1);
        }

        // Pembulatan 11 angka penting: menghilangkan noise seperti 0.49999999999999994
        function kalcFormat(v) {
            let rounded = v;
            if (v !== 0 && isFinite(v) && Math.abs(v) < 1e15) {
                const magnitude = Math.log10(Math.abs(v));
                const decimals = Math.max(0, Math.min(15, Math.floor(11 - magnitude)));
                const factor = Math.pow(10, decimals);
                rounded = Math.round(v * factor) / factor;
            }
            if (rounded === Math.floor(rounded) && Math.abs(rounded) < 1e15) {
                return String(Math.round(rounded));
            }
            return String(rounded);
        }

        // Bentuk pecahan untuk desimal rasional sederhana, mis. 1÷3 -> 1/3 (penyebut <= 999).
        function kalcFractionHint(v) {
            if (!isFinite(v)) return null;
            const x = Math.abs(v);
            if (x > 1e6) return null;
            const fracPart = x - Math.floor(x);
            if (fracPart < 1e-9 || 1.0 - fracPart < 1e-9) return null;
            let bestDen = 0;
            let bestErr = Number.MAX_VALUE;
            for (let d = 2; d <= 999; d++) {
                const n = Math.round(x * d);
                const err = Math.abs(x * d - n) / d;
                if (err < bestErr) { bestErr = err; bestDen = d; }
            }
            if (bestErr > 1e-9 || bestDen < 2) return null;
            const num = Math.round(x * bestDen);
            if (num % bestDen === 0) return null;
            const whole = Math.floor(num / bestDen);
            const rest = num % bestDen;
            const sign = v < 0 ? '-' : '';
            return whole > 0 && rest > 0 ? sign + whole + ' ' + rest + '/' + bestDen : sign + num + '/' + bestDen;
        }

        function kalcStatValue(kind, data) {
            const n = data.length;
            if (kind === 'n') return n;
            if (kind === 'sum') return data.reduce(function (a, b) { return a + b; }, 0);
            if (kind === 'sumsq') return data.reduce(function (a, b) { return a + b * b; }, 0);
            if (n === 0) return null;
            const m = data.reduce(function (a, b) { return a + b; }, 0) / n;
            if (kind === 'mean') return m;
            const ss = data.reduce(function (a, b) { return a + (b - m) * (b - m); }, 0);
            if (kind === 'sigp') return Math.sqrt(ss / n);
            if (kind === 'sigs') return n < 2 ? null : Math.sqrt(ss / (n - 1));
            return null;
        }

        // Menerima variasi tanda: x/X/* sebagai kali, : atau / sebagai bagi,
        // minus unicode, dan kurung lebar penuh.
        function kalcNormalize(raw) {
            let mapped = '';
            for (let k = 0; k < raw.length; k++) {
                const ch = raw.charAt(k);
                if ('xX*∗·⋅＊'.indexOf(ch) >= 0) mapped += '×';
                else if ('/:∕／'.indexOf(ch) >= 0) mapped += '÷';
                else if ('–—－'.indexOf(ch) >= 0) mapped += '−';
                else if (ch === '＋') mapped += '+';
                else if (ch === '（') mapped += '(';
                else if (ch === '）') mapped += ')';
                else mapped += ch;
            }
            const s = mapped;
            const out = [];
            let prev = null;
            let i = 0;
            while (i < s.length) {
                const c = s.charAt(i);
                if (kalcIsAsciiLetter(c)) {
                    const start = i;
                    while (i < s.length && kalcIsAsciiLetter(s.charAt(i))) i++;
                    const word = s.slice(start, i);
                    for (let k = 0; k < word.length; k++) out.push(word.charAt(k));
                    prev = word.charAt(word.length - 1);
                    if (KALC_CONSTANT_WORDS.indexOf(word) < 0) {
                        let j = i;
                        while (j < s.length && s.charAt(j) === ' ') j++;
                        if (j >= s.length || s.charAt(j) !== '(') {
                            out.push('(');
                            prev = '(';
                        }
                    }
                    continue;
                }
                // titik ganda dalam satu angka ("6..6") cukup satu saja
                if (c === '.' && prev === '.') { i++; continue; }
                // dua operator berurutan: pakai yang terakhir ("6××6" -> "6×6")
                if (kalcIsBinaryOp(c) && kalcIsBinaryOp(prev) &&
                    !((prev === '+' || prev === '−') && (c === '+' || c === '−'))) {
                    out[out.length - 1] = c;
                    prev = c;
                    i++;
                    continue;
                }
                out.push(c);
                prev = c;
                i++;
            }
            let result = out.join('').split('()').join('');
            while (result.length > 0 && (result.charAt(0) === '×' || result.charAt(0) === '÷' || result.charAt(0) === '^')) {
                result = result.slice(1);
            }
            return result;
        }

        // Lengkapi ekspresi yang belum selesai: buang tanda menggantung, tutup kurung.
        function kalcPrepare(raw) {
            if (raw.trim() === '') return '';
            let s = kalcNormalize(raw);
            while (s.length > 0) {
                const trimmed = s.replace(/\s+$/, '');
                const last = trimmed.length > 0 ? trimmed.charAt(trimmed.length - 1) : null;
                if (last === '(' || last === '.' || last === ',' || last === '√' ||
                    last === '∛' || kalcIsBinaryOp(last)) {
                    s = trimmed.slice(0, -1);
                    continue;
                }
                s = trimmed;
                break;
            }
            let open = (s.match(/\(/g) || []).length;
            let close = (s.match(/\)/g) || []).length;
            while (close > open && s.endsWith(')')) { s = s.slice(0, -1); close--; }
            open = (s.match(/\(/g) || []).length;
            close = (s.match(/\)/g) || []).length;
            if (open > close) s += new Array(open - close + 1).join(')');
            return s.trim();
        }

        function kalcFactorial(v) {
            if (v < 0 || v !== Math.floor(v) || v > 170) throw new Error('faktorial tak valid');
            let r = 1.0;
            for (let k = 2.0; k <= v; k++) r *= k;
            return r;
        }

        function kalcSqrtOf(v) {
            if (v < 0) throw new Error('akar bilangan negatif');
            return Math.sqrt(v);
        }

        function kalcCubeRoot(v) {
            return v < 0 ? -Math.pow(-v, 1.0 / 3.0) : Math.pow(v, 1.0 / 3.0);
        }

        function kalcNthRoot(v, n) {
            if (n === 0) throw new Error('indeks akar tidak boleh 0');
            if (v < 0) {
                const whole = Math.floor(n);
                const oddInt = whole === n && whole % 2 !== 0;
                if (!oddInt) throw new Error('akar bilangan negatif');
                return -Math.pow(-v, 1.0 / n);
            }
            return Math.pow(v, 1.0 / n);
        }

        function kalcEvaluate(raw, unit, ans, mem, varA) {
            const prepared = kalcPrepare(raw);
            if (prepared.trim() === '') throw new Error('kosong');
            const v = new KalcParser(prepared, unit, ans, mem, varA).parseAll();
            if (!isFinite(v)) throw new Error('hasil tak hingga');
            return v;
        }

        class KalcParser {
            constructor(s, unit, ans, mem, varA) {
                this.s = s; this.unit = unit; this.ans = ans; this.mem = mem; this.varA = varA; this.pos = 0;
            }
            parseAll() {
                const v = this.parseExpr();
                this.skipSpace();
                if (this.pos < this.s.length) throw new Error('sisa karakter');
                return v;
            }
            skipSpace() { while (this.pos < this.s.length && this.s.charAt(this.pos) === ' ') this.pos++; }
            peek() { this.skipSpace(); return this.pos < this.s.length ? this.s.charAt(this.pos) : ' '; }
            take(c) {
                this.skipSpace();
                if (this.pos < this.s.length && this.s.charAt(this.pos) === c) { this.pos++; return true; }
                return false;
            }
            parseExpr() {
                let v = this.parseTerm();
                for (;;) {
                    const c = this.peek();
                    if (c === '+') { this.pos++; v += this.parseTerm(); }
                    else if (c === '-' || c === '−') { this.pos++; v -= this.parseTerm(); }
                    else return v;
                }
            }
            parseTerm() {
                let v = this.parseUnary();
                for (;;) {
                    const c = this.peek();
                    if (c === '*' || c === '×') { this.pos++; v *= this.parseUnary(); }
                    else if (c === '/' || c === '÷') {
                        this.pos++;
                        const d = this.parseUnary();
                        if (d === 0) throw new Error('pembagian nol');
                        v /= d;
                    } else {
                        if (this.startsPrimary()) v *= this.parseUnary(); else return v;
                    }
                }
            }
            startsPrimary() {
                this.skipSpace();
                if (this.pos >= this.s.length) return false;
                const c = this.s.charAt(this.pos);
                return kalcIsDigit(c) || c === '.' || c === '(' || c === 'π' || c === '√' ||
                    c === '∛' || kalcIsLetter(c);
            }
            parseUnary() {
                const c = this.peek();
                if (c === '-' || c === '−') { this.pos++; return -this.parseUnary(); }
                if (c === '+') { this.pos++; return this.parseUnary(); }
                return this.parsePower();
            }
            parsePower() {
                const base = this.parsePostfix();
                return this.take('^') ? Math.pow(base, this.parseUnary()) : base;
            }
            parsePostfix() {
                let v = this.parsePrimary();
                for (;;) {
                    const c = this.peek();
                    if (c === '%') { this.pos++; v /= 100.0; }
                    else if (c === '!') { this.pos++; v = kalcFactorial(v); }
                    else if (c === '²') { this.pos++; v = v * v; }
                    else if (c === '³') { this.pos++; v = v * v * v; }
                    else if (c === '⁻' && this.pos + 1 < this.s.length && this.s.charAt(this.pos + 1) === '¹') {
                        this.pos += 2;
                        if (v === 0) throw new Error('1/0 tidak terdefinisi');
                        v = 1.0 / v;
                    } else return v;
                }
            }
            parsePrimary() {
                this.skipSpace();
                if (this.pos >= this.s.length) throw new Error('ekspresi belum lengkap');
                const c = this.s.charAt(this.pos);
                if (c === '(') {
                    this.pos++;
                    const v = this.parseExpr();
                    if (!this.take(')')) throw new Error('kurung belum ditutup');
                    return v;
                }
                if (c === '√') { this.pos++; return kalcSqrtOf(this.parsePostfix()); }
                if (c === '∛') { this.pos++; return kalcCubeRoot(this.parsePostfix()); }
                if (c === 'π') { this.pos++; return Math.PI; }
                if (kalcIsLetter(c)) {
                    const start = this.pos;
                    while (this.pos < this.s.length && kalcIsLetter(this.s.charAt(this.pos))) this.pos++;
                    const word = this.s.slice(start, this.pos);
                    return this.applyFunction(word, this.readFunctionArgs(word));
                }
                if (kalcIsDigit(c) || c === '.') return this.readNumber();
                throw new Error('karakter tak dikenal: ' + c);
            }
            readFunctionArgs(word) {
                if (KALC_CONSTANT_WORDS.indexOf(word) >= 0) return [];
                if (!this.take('(')) throw new Error(word + ' butuh ( )');
                const args = [this.parseExpr()];
                while (this.take(',')) args.push(this.parseExpr());
                if (!this.take(')')) throw new Error('kurung ' + word + ' belum ditutup');
                return args;
            }
            one(args, word) {
                if (args.length !== 1) throw new Error(word + ' butuh 1 angka');
                return args[0];
            }
            two(args, word) {
                if (args.length !== 2) throw new Error(word + ' butuh 2 angka (pisahkan koma)');
                return [args[0], args[1]];
            }
            toRad(a) {
                if (this.unit === 'DEG') return a * (Math.PI / 180.0);
                if (this.unit === 'GRAD') return a * (Math.PI / 200.0);
                return a;
            }
            fromRad(r) {
                if (this.unit === 'DEG') return r * (180.0 / Math.PI);
                if (this.unit === 'GRAD') return r * (200.0 / Math.PI);
                return r;
            }
            applyFunction(word, args) {
                switch (word) {
                    case 'e': return Math.E;
                    case 'Ans': return this.ans;
                    case 'M': return this.mem;
                    case 'A': return this.varA;
                    case 'sin': return Math.sin(this.toRad(this.one(args, word)));
                    case 'cos': return Math.cos(this.toRad(this.one(args, word)));
                    case 'tan': return Math.tan(this.toRad(this.one(args, word)));
                    case 'asin': {
                        const a = this.one(args, word);
                        if (a < -1.0 || a > 1.0) throw new Error('sin⁻¹ rentang -1..1');
                        return this.fromRad(Math.asin(a));
                    }
                    case 'acos': {
                        const a = this.one(args, word);
                        if (a < -1.0 || a > 1.0) throw new Error('cos⁻¹ rentang -1..1');
                        return this.fromRad(Math.acos(a));
                    }
                    case 'atan': return this.fromRad(Math.atan(this.one(args, word)));
                    case 'sinh': return Math.sinh(this.one(args, word));
                    case 'cosh': return Math.cosh(this.one(args, word));
                    case 'tanh': return Math.tanh(this.one(args, word));
                    case 'asinh': {
                        const a = this.one(args, word);
                        return Math.log(a + Math.sqrt(a * a + 1.0));
                    }
                    case 'acosh': {
                        const a = this.one(args, word);
                        if (a < 1.0) throw new Error('cosh⁻¹ butuh angka >= 1');
                        return Math.log(a + Math.sqrt(a * a - 1.0));
                    }
                    case 'atanh': {
                        const a = this.one(args, word);
                        if (a <= -1.0 || a >= 1.0) throw new Error('tanh⁻¹ rentang -1..1');
                        return 0.5 * Math.log((1.0 + a) / (1.0 - a));
                    }
                    case 'ln': {
                        const a = this.one(args, word);
                        if (a <= 0) throw new Error('ln butuh bilangan > 0');
                        return Math.log(a);
                    }
                    case 'log': {
                        const a = this.one(args, word);
                        if (a <= 0) throw new Error('log butuh bilangan > 0');
                        return Math.log10(a);
                    }
                    case 'logb': {
                        const pair = this.two(args, word);
                        if (pair[0] <= 0 || pair[0] === 1.0) throw new Error('basis log salah');
                        if (pair[1] <= 0) throw new Error('log butuh bilangan > 0');
                        return Math.log(pair[1]) / Math.log(pair[0]);
                    }
                    case 'abs': return Math.abs(this.one(args, word));
                    case 'mod': {
                        const pair = this.two(args, word);
                        if (pair[1] === 0) throw new Error('mod 0 tidak terdefinisi');
                        return pair[0] % pair[1];
                    }
                    case 'root': {
                        const pair = this.two(args, word);
                        return kalcNthRoot(pair[0], pair[1]);
                    }
                    default: throw new Error('fungsi tak dikenal: ' + word);
                }
            }
            readNumber() {
                const start = this.pos;
                while (this.pos < this.s.length && (kalcIsDigit(this.s.charAt(this.pos)) || this.s.charAt(this.pos) === '.')) this.pos++;
                const token = this.s.slice(start, this.pos);
                if (token.split('.').length > 2) throw new Error('dua titik desimal');
                const v = Number(token);
                if (isNaN(v)) throw new Error('angka tak valid: ' + token);
                return v;
            }
        }

        // --- keadaan & interaksi (sama persis dengan KalkulatorScreen.kt) --------

        window.kalcState = null;
        let kalcAudioCtx = null;

        // Nilai persis hasil terakhir, dipakai saat melanjutkan perhitungan
        // supaya tidak muncul galat pembulatan (1/3 lalu x3 tetap 1).
        function kalcCarrySource(st, expr) {
            if (st.lastRaw !== null && st.lastResult !== '' && expr.startsWith(st.lastResult)) {
                return st.lastRaw + expr.slice(st.lastResult.length);
            }
            return expr;
        }

        function kalcEvalNow(st) {
            const src = kalcCarrySource(st, st.expression);
            if (src.trim() === '') return null;
            try {
                const v = kalcEvaluate(src, st.unit, st.ansVal, st.memVal === null ? 0.0 : st.memVal, st.varA);
                return isFinite(v) ? v : null;
            } catch (e) { return null; }
        }

        // 0 = tombol biasa, 1 = gagal, 2 = hasil
        function kalcHandleKey(st, token) {
            switch (token) {
                case 'C':
                    st.expression = '';
                    st.prevFormula = '';
                    st.lastResult = '';
                    st.lastRaw = null;
                    st.error = false;
                    st.hint = '';
                    st.freshEntry = false;
                    break;
                case 'BS':
                    st.lastRaw = null;
                    st.hint = '';
                    st.freshEntry = false;
                    if (st.error) {
                        st.expression = '';
                        st.error = false;
                    } else {
                        st.expression = kalcDropOneToken(st.expression);
                    }
                    break;
                case 'EQ': {
                    const expr = kalcPrepare(kalcCarrySource(st, st.expression));
                    let result = null;
                    if (expr.trim() !== '') {
                        try {
                            result = kalcEvaluate(expr, st.unit, st.ansVal, st.memVal === null ? 0.0 : st.memVal, st.varA);
                        } catch (e) { result = null; }
                    }
                    if (result !== null && isFinite(result)) {
                        const formatted = kalcFormat(result);
                        if (st.expression.trim() !== '') st.prevFormula = st.expression + ' =';
                        st.ansVal = result;
                        st.expression = formatted;
                        st.lastResult = formatted;
                        st.lastRaw = String(result);
                        st.error = false;
                        st.hint = '';
                        st.shiftOn = false;
                        st.freshEntry = false;
                        return 2;
                    }
                    if (expr.trim() !== '') {
                        st.error = true;
                        st.errorTick++;
                    }
                    return 1;
                }
                case 'MC':
                    st.memVal = null;
                    st.hint = 'Memori kosong';
                    break;
                case 'M+': {
                    const v = kalcEvalNow(st);
                    if (v === null) return 1;
                    const total = (st.memVal === null ? 0.0 : st.memVal) + v;
                    st.memVal = total;
                    st.hint = 'M = ' + kalcFormat(total);
                    st.freshEntry = true;
                    break;
                }
                case 'M-': {
                    const v = kalcEvalNow(st);
                    if (v === null) return 1;
                    const total = (st.memVal === null ? 0.0 : st.memVal) - v;
                    st.memVal = total;
                    st.hint = 'M = ' + kalcFormat(total);
                    st.freshEntry = true;
                    break;
                }
                case 'STO': {
                    const v = kalcEvalNow(st);
                    if (v === null) return 1;
                    st.varA = v;
                    st.hint = 'Simpan A = ' + kalcFormat(v);
                    st.freshEntry = true;
                    break;
                }
                case 'D+': {
                    const v = kalcEvalNow(st);
                    if (v === null) return 1;
                    st.stats.push(v);
                    st.hint = 'Data n = ' + st.stats.length;
                    st.freshEntry = true;
                    break;
                }
                case 'CL':
                    st.stats = [];
                    st.hint = 'Data dihapus';
                    break;
                case 'SHIFT':
                    st.shiftOn = !st.shiftOn;
                    break;
                default: {
                    if (st.error) {
                        st.expression = '';
                        st.error = false;
                    }
                    st.hint = '';
                    // ketikan baru setelah hasil: mulai dari nol, bukan menempel
                    if (st.lastRaw !== null && st.expression === st.lastResult && kalcStartsOperand(token)) {
                        st.expression = '';
                        st.lastResult = '';
                        st.lastRaw = null;
                    }
                    if (st.freshEntry && kalcStartsOperand(token)) {
                        st.expression = '';
                        st.lastResult = '';
                        st.lastRaw = null;
                    }
                    st.freshEntry = false;
                    if (KALC_STAT_TOKENS.indexOf(token) >= 0) {
                        const v = kalcStatValue(token, st.stats);
                        if (v === null) return 1;
                        st.expression += kalcFormat(v);
                        st.freshEntry = true;
                    } else {
                        const tail = st.expression.length > 0 ? st.expression.charAt(st.expression.length - 1) : null;
                        // "Ans" lalu fungsi tidak boleh menyatu jadi kata baru
                        if (tail !== null && kalcIsAsciiLetter(tail) && kalcIsAsciiLetter(token.charAt(0) || ' ')) {
                            st.expression += '×';
                        }
                        st.expression += token;
                    }
                    break;
                }
            }
            if (token !== 'SHIFT') st.shiftOn = false;
            return 0;
        }

        function kalcPlayClick(code) {
            try {
                if (!kalcAudioCtx) kalcAudioCtx = new (window.AudioContext || window.webkitAudioContext)();
                if (kalcAudioCtx.state === 'suspended') kalcAudioCtx.resume();
                const t = kalcAudioCtx.currentTime;
                const osc = kalcAudioCtx.createOscillator();
                const gain = kalcAudioCtx.createGain();
                osc.connect(gain);
                gain.connect(kalcAudioCtx.destination);
                if (code === 1) {
                    osc.type = 'sawtooth';
                    osc.frequency.setValueAtTime(150, t);
                    gain.gain.setValueAtTime(0.12, t);
                    gain.gain.exponentialRampToValueAtTime(0.001, t + 0.18);
                    osc.start(t); osc.stop(t + 0.2);
                } else if (code === 2) {
                    osc.type = 'sine';
                    osc.frequency.setValueAtTime(880, t);
                    gain.gain.setValueAtTime(0.1, t);
                    gain.gain.exponentialRampToValueAtTime(0.001, t + 0.12);
                    osc.start(t); osc.stop(t + 0.13);
                } else {
                    osc.type = 'triangle';
                    osc.frequency.setValueAtTime(1400, t);
                    gain.gain.setValueAtTime(0.08, t);
                    gain.gain.exponentialRampToValueAtTime(0.001, t + 0.04);
                    osc.start(t); osc.stop(t + 0.05);
                }
            } catch (e) { /*.audio tidak tersedia*/ }
        }

        function kalcFeedback(st, code) {
            if (st.soundOn) kalcPlayClick(code);
            try { if (navigator.vibrate) navigator.vibrate(code === 1 ? 60 : 10); } catch (e) { /*tanpa getar*/ }
        }

        function kalcPress(token) {
            const st = window.kalcState;
            if (!st) return;
            const code = kalcHandleKey(st, token);
            kalcFeedback(st, code);
            kalcRender(st);
        }

        function kalcBuildKey(label, extraClass, onClick) {
            const b = document.createElement('button');
            b.type = 'button';
            b.className = 'kalc-key' + (extraClass ? ' ' + extraClass : '');
            b.textContent = label;
            b.addEventListener('click', onClick);
            return b;
        }

        function kalcEnsureStyles() {
            if (document.getElementById('kalcStyles')) return;
            const style = document.createElement('style');
            style.id = 'kalcStyles';
            style.textContent = KALC_CSS;
            document.head.appendChild(style);
        }

        function kalcRender(st) {
            const page = document.getElementById('kalcPage');
            if (!page) return;
            const land = window.innerWidth > window.innerHeight;
            const sci = st.mode === 'ILMIAH';
            page.classList.toggle('kalc-scientific', sci);
            page.classList.toggle('kalc-landscape', land);
            page.classList.toggle('kalc-has-data', st.stats.length > 0);

            const nEl = document.getElementById('kalcN');
            nEl.style.display = st.stats.length > 0 ? '' : 'none';
            nEl.textContent = 'n=' + st.stats.length;
            document.getElementById('kalcSoundBtn').classList.toggle('kalc-active', st.soundOn);
            document.getElementById('kalcSoundIcon').className = 'bi ' + (st.soundOn ? 'bi-volume-up-fill' : 'bi-volume-mute-fill');

            document.getElementById('kalcChipShift').classList.toggle('kalc-show', st.shiftOn);
            document.getElementById('kalcChipM').classList.toggle('kalc-show', st.memVal !== null);
            document.getElementById('kalcChipA').classList.toggle('kalc-show', st.varA !== 0);
            document.getElementById('kalcModeBtn').classList.toggle('kalc-active', sci);
            const unitBtn = document.getElementById('kalcUnitBtn');
            unitBtn.textContent = st.unit;
            unitBtn.classList.toggle('kalc-active', st.unit !== 'RAD');

            const live = kalcEvalNow(st);
            const liveText = live !== null ? kalcFormat(live) : '';
            const frac = live !== null ? (kalcFractionHint(live) || '') : '';

            const prevEl = document.getElementById('kalcPrev');
            prevEl.style.display = st.prevFormula !== '' ? '' : 'none';
            prevEl.textContent = st.prevFormula;
            prevEl.scrollLeft = prevEl.scrollWidth;

            const exprEl = document.getElementById('kalcExpr');
            exprEl.textContent = st.expression !== '' ? st.expression : (st.lastResult !== '' ? st.lastResult : '0');
            exprEl.style.fontSize = st.expression.length > 26 ? '18px' : (st.expression.length > 18 ? '23px' : '29px');
            exprEl.classList.toggle('kalc-error', st.error);
            exprEl.scrollLeft = exprEl.scrollWidth;

            const hintEl = document.getElementById('kalcHint');
            hintEl.style.display = st.hint !== '' ? '' : 'none';
            hintEl.textContent = st.hint;

            const fracEl = document.getElementById('kalcFrac');
            fracEl.style.display = frac !== '' ? '' : 'none';
            fracEl.textContent = '= ' + frac;
            fracEl.scrollLeft = fracEl.scrollWidth;

            const liveEl = document.getElementById('kalcLive');
            let liveRow;
            if (st.error) liveRow = 'Pernyataan salah';
            else if (st.expression.trim() === '') liveRow = '\u00A0';
            else if (liveText !== '' && liveText !== st.expression) liveRow = '= ' + liveText;
            else liveRow = '\u00A0';
            liveEl.textContent = liveRow;
            liveEl.classList.toggle('kalc-error', st.error);
            liveEl.scrollLeft = liveEl.scrollWidth;

            // geser kiri-kanan singkat saat pernyataan salah
            if (st.errorTick !== st._shakeTick) {
                st._shakeTick = st.errorTick;
                if (st.errorTick > 0) {
                    exprEl.classList.remove('kalc-shake');
                    void exprEl.offsetWidth;
                    exprEl.classList.add('kalc-shake');
                }
            }
            // hasil muncul dengan efek pop kecil
            if (st.lastResult !== st._popResult) {
                st._popResult = st.lastResult;
                if (st.lastResult !== '') {
                    liveEl.classList.remove('kalc-pop');
                    void liveEl.offsetWidth;
                    liveEl.classList.add('kalc-pop');
                }
            }

            st._sciBtns.forEach(function (item) {
                const key = item.key;
                const label = st.shiftOn && key.al !== undefined ? key.al : key.l;
                item.btn.textContent = label;
                item.btn.classList.toggle('kalc-key-long', label.length > 4);
                if (item.isShift) item.btn.classList.toggle('kalc-active', st.shiftOn);
            });
            if (st._mrBtn) st._mrBtn.classList.toggle('kalc-mr-active', st.memVal !== null);
        }

        function loadKalkulator() {
            kalcEnsureStyles();
            const st = {
                expression: '', prevFormula: '', lastResult: '', lastRaw: null,
                error: false, errorTick: 0, mode: 'BIASA', unit: 'DEG', shiftOn: false,
                ansVal: 0.0, memVal: null, varA: 0.0, stats: [], hint: '', freshEntry: false,
                soundOn: true, lastLandscape: null,
                _sciBtns: [], _mrBtn: null, _shakeTick: 0, _popResult: ''
            };
            try { st.soundOn = localStorage.getItem('calc_key_sound') !== '0'; } catch (e) { /*penyimpanan privat*/ }
            st.lastLandscape = window.innerWidth > window.innerHeight;
            window.kalcState = st;

            const container = document.getElementById('tab-content');
            if (!container) return;
            container.innerHTML = `
                <div class="kalc-page" id="kalcPage">
                    <div class="kalc-header">
                        <div class="kalc-logo"><i class="bi bi-calculator"></i></div>
                        <div class="kalc-title">Kalkulator</div>
                        <div class="kalc-flex-spacer"></div>
                        <span class="kalc-n" id="kalcN" style="display: none;"></span>
                        <button type="button" class="kalc-mini" id="kalcSoundBtn" title="Efek suara dan getar" aria-label="Efek suara dan getar">
                            <i class="bi bi-volume-up-fill" id="kalcSoundIcon"></i>
                        </button>
                    </div>
                    <div class="kalc-body">
                        <div class="kalc-left">
                            <div class="kalc-mem" id="kalcMem"></div>
                            <div class="kalc-sci" id="kalcSci"></div>
                            <div class="kalc-stats" id="kalcStats"></div>
                        </div>
                        <div class="kalc-right">
                            <div class="kalc-display">
                                <div class="kalc-chips-row">
                                    <span class="kalc-chip" id="kalcChipShift">SHIFT</span>
                                    <span class="kalc-chip" id="kalcChipM">M</span>
                                    <span class="kalc-chip" id="kalcChipA">A</span>
                                    <div class="kalc-flex-spacer"></div>
                                    <button type="button" class="kalc-mini" id="kalcModeBtn" title="Mode ilmiah" aria-label="Mode ilmiah"><span class="kalc-fx">ƒx</span></button>
                                    <button type="button" class="kalc-mini kalc-unit" id="kalcUnitBtn" title="Derajat, radian, atau gradian" aria-label="Derajat, radian, atau gradian">DEG</button>
                                </div>
                                <div class="kalc-prev" id="kalcPrev"></div>
                                <div class="kalc-expr" id="kalcExpr">0</div>
                                <div class="kalc-hint" id="kalcHint"></div>
                                <div class="kalc-frac" id="kalcFrac"></div>
                                <div class="kalc-live" id="kalcLive">&nbsp;</div>
                            </div>
                            <div class="kalc-keypad" id="kalcKeypad"></div>
                        </div>
                    </div>
                </div>
            `;

            const memEl = document.getElementById('kalcMem');
            KALC_MEM_KEYS.forEach(function (cap) {
                const b = kalcBuildKey(cap.l, '', function () { kalcPress(cap.t); });
                if (cap.t === 'M') st._mrBtn = b;
                memEl.appendChild(b);
            });

            const sciEl = document.getElementById('kalcSci');
            KALC_SCI_KEYS.forEach(function (row) {
                const rowEl = document.createElement('div');
                rowEl.className = 'kalc-sci-row';
                row.forEach(function (key) {
                    const isShift = key.t === 'SHIFT';
                    const b = kalcBuildKey(key.l, isShift ? 'kalc-key-shift' : '', function () {
                        const s = window.kalcState;
                        kalcPress(s && s.shiftOn && key.at !== undefined ? key.at : key.t);
                    });
                    st._sciBtns.push({ btn: b, key: key, isShift: isShift });
                    rowEl.appendChild(b);
                });
                sciEl.appendChild(rowEl);
            });

            const statsEl = document.getElementById('kalcStats');
            KALC_STATS_KEYS.forEach(function (cap) {
                statsEl.appendChild(kalcBuildKey(cap.l, '', function () { kalcPress(cap.t); }));
            });

            const keypadEl = document.getElementById('kalcKeypad');
            KALC_MAIN_ROWS.forEach(function (row) {
                const rowEl = document.createElement('div');
                rowEl.className = 'kalc-keypad-row';
                row.forEach(function (cap) {
                    const cls = cap.c === 'clear' ? 'kalc-key-clear' : cap.c === 'op' ? 'kalc-key-op' : cap.c === 'eq' ? 'kalc-key-eq' : 'kalc-key-num';
                    const b = kalcBuildKey(cap.l, cls + (cap.z ? ' kalc-key-zero' : ''), function () { kalcPress(cap.t); });
                    rowEl.appendChild(b);
                });
                keypadEl.appendChild(rowEl);
            });

            document.getElementById('kalcSoundBtn').addEventListener('click', function () {
                st.soundOn = !st.soundOn;
                try { localStorage.setItem('calc_key_sound', st.soundOn ? '1' : '0'); } catch (e) { /*penyimpanan privat*/ }
                kalcRender(st);
            });
            document.getElementById('kalcModeBtn').addEventListener('click', function () {
                const land = window.innerWidth > window.innerHeight;
                if (land && st.mode === 'ILMIAH') st.mode = 'BIASA';
                else st.mode = st.mode === 'BIASA' ? 'ILMIAH' : 'BIASA';
                kalcRender(st);
            });
            document.getElementById('kalcUnitBtn').addEventListener('click', function () {
                st.unit = st.unit === 'DEG' ? 'RAD' : st.unit === 'RAD' ? 'GRAD' : 'DEG';
                kalcRender(st);
            });

            kalcRender(st);
        }

        // Putaran layar: lanskap -> ilmiah, kembali ke potret -> biasa (hanya saat berubah)
        window.addEventListener('resize', function () {
            const st = window.kalcState;
            if (!st || currentTab !== 'kalkulator' || !document.getElementById('kalcPage')) return;
            const land = window.innerWidth > window.innerHeight;
            const prev = st.lastLandscape;
            st.lastLandscape = land;
            if (prev !== null && prev !== land) {
                st.mode = land ? 'ILMIAH' : 'BIASA';
            }
            kalcRender(st);
        });

        // Dukungan keyboard fisik PC (angka, operator, Enter, Backspace, Escape)
        document.addEventListener('keydown', function (e) {
            const st = window.kalcState;
            if (!st || currentTab !== 'kalkulator' || !document.getElementById('kalcPage')) return;
            if (e.ctrlKey || e.metaKey || e.altKey) return;
            const target = e.target;
            const tag = target && target.tagName ? target.tagName.toLowerCase() : '';
            if (tag === 'input' || tag === 'textarea' || tag === 'select' || (target && target.isContentEditable)) return;
            const k = e.key;
            let token = null;
            if (/^[0-9]$/.test(k)) token = k;
            else if (k === '.') token = '.';
            else if (k === '+') token = '+';
            else if (k === '-') token = '−';
            else if (k === '*' || k === 'x' || k === 'X') token = '×';
            else if (k === '/') token = '÷';
            else if (k === '(' || k === ')') token = k;
            else if (k === '%') token = '%';
            else if (k === '!') token = '!';
            else if (k === '^') token = '^(';
            else if (k === 'Enter' || k === '=') token = 'EQ';
            else if (k === 'Backspace') token = 'BS';
            else if (k === 'Escape') token = 'C';
            if (token !== null) {
                e.preventDefault();
                kalcPress(token);
            }
        });
        """
    }
}