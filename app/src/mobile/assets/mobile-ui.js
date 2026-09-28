(function () {
    'use strict';
    if (location.origin !== 'https://animeav1.com' || window.top !== window) return;
    if (window.__av1MobileUi) { window.__av1MobileUi.refresh(); return; }

    function norm(value) { return String(value || '').replace(/\s+/g, ' ').trim().toLowerCase(); }
    function path(link) {
        try { var u = new URL(link.getAttribute('href'), location.href); return u.origin === location.origin ? u.pathname.replace(/\/$/, '') || '/' : ''; }
        catch (_) { return ''; }
    }
    function visible(element) { return !!(element && element.getClientRects().length); }
    function accountForm() {
        if (!/^\/cuenta\/?$/.test(location.pathname)) return null;
        return Array.from(document.querySelectorAll('form')).find(function (form) {
            return Array.from(form.querySelectorAll('button,input[type=submit]')).some(function (b) {
                return ['actualizar perfil', 'actualizar contraseña', 'cambiar contraseña'].includes(norm(b.textContent || b.value));
            });
        }) || null;
    }
    function refresh() {
        var root = document.head || document.documentElement;
        if (!root) return;
        if (!document.getElementById('av1-mobile-ui-css')) {
            var style = document.createElement('style');
            style.id = 'av1-mobile-ui-css';
            // The first selector matches the server-rendered navigation, before hydration.
            // Never hide headers, login dialogs or their ancestors.
            style.textContent = 'nav.sticky.bottom-0,nav[data-av1-mobile-nav]{display:none!important}' +
                '#av1-sign-out{display:block;width:100%;margin:24px 0;padding:14px;border:1px solid currentColor;border-radius:10px;background:transparent;color:inherit;font:inherit;cursor:pointer}';
            root.appendChild(style);
        }
        document.querySelectorAll('nav').forEach(function (nav) {
            var paths = Array.from(nav.querySelectorAll('a[href]')).map(path);
            var labels = norm(nav.textContent);
            if (paths.includes('/') && paths.includes('/catalogo') && paths.includes('/horario') && labels.includes('mis listas') && labels.includes('mi cuenta') && !nav.hasAttribute('data-av1-mobile-nav')) {
                nav.setAttribute('data-av1-mobile-nav', '');
            }
        });
        var form = accountForm(), old = document.getElementById('av1-sign-out');
        if (!form) { if (old) old.remove(); return; }
        if (old && old.previousElementSibling !== form) { old.remove(); old = null; }
        if (!old) {
            var button = document.createElement('button');
            button.id = 'av1-sign-out';
            button.type = 'button';
            button.textContent = 'Cerrar sesión';
            button.onclick = function () { location.href = 'https://animeav1.com/__android/logout'; };
            form.insertAdjacentElement('afterend', button);
        }
    }
    function openAccount() {
        refresh();
        if (accountForm()) return 'authenticated';
        // A visible password field means the official login is already open.
        if (Array.from(document.querySelectorAll('input[type=password]')).some(visible)) return 'login-open';
        var login = document.querySelector('button[aria-label="Iniciar sesión"]');
        if (login) {
            // Hidden controls keep their official event handlers; don't require visibility.
            login.click();
            return Array.from(document.querySelectorAll('input[type=password]')).some(visible) ? 'login-open' : 'retry';
        }
        var account = Array.from(document.querySelectorAll('a[href]')).find(function (a) { return path(a) === '/cuenta'; });
        if (account) return 'authenticated';
        var guest = Array.from(document.querySelectorAll('nav button')).find(function (b) { return norm(b.textContent) === 'mi cuenta'; });
        if (guest) { guest.click(); return 'retry'; }
        return 'missing';
    }
    window.__av1MobileUi = {refresh: refresh, openAccount: openAccount};
    refresh();
    new MutationObserver(refresh).observe(document, {childList: true, subtree: true, characterData: true});
})();
