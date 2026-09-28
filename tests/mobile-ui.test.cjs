const { test } = require('node:test');
const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const { JSDOM } = require('jsdom');
const script = readFileSync('app/src/mobile/assets/mobile-ui.js', 'utf8');
const nav = '<nav class="bg-body sticky bottom-0 z-30 lg:hidden"><a href="/">Inicio</a><a href="/catalogo">Catálogo</a><a href="/horario">Horario</a><button>Mis Listas</button><button>Mi cuenta</button></nav>';
function page(t, html = '', path = '/') {
    const dom = new JSDOM(html, { url: 'https://animeav1.com' + path, runScripts: 'outside-only' });
    const observers = [], NativeObserver = dom.window.MutationObserver;
    dom.window.MutationObserver = class extends NativeObserver {
        constructor(callback) { super(callback); observers.push(this); }
    };
    t.after(() => { observers.forEach(observer => observer.disconnect()); dom.window.close(); });
    dom.window.eval(script);
    return dom.window;
}
const mutations = () => new Promise(resolve => setImmediate(resolve));

test('navigation is hidden before controls exist, while header stays visible', t => {
    const w = page(t, '<header><nav id="header"><a href="/">Inicio</a><a href="/catalogo">Catálogo</a><a href="/horario">Horario</a></nav></header><nav class="sticky bottom-0"></nav>');
    assert.equal(w.getComputedStyle(w.document.querySelector('.sticky')).display, 'none');
    assert.notEqual(w.getComputedStyle(w.document.querySelector('#header')).display, 'none');
});

test('navigation remains hidden after hydration replaces it', async t => {
    const w = page(t, nav);
    w.document.querySelector('nav').outerHTML = nav.replace('class="bg-body sticky bottom-0 z-30 lg:hidden"', '');
    await mutations();
    assert.equal(w.getComputedStyle(w.document.querySelector('nav')).display, 'none');
});

test('login uses the hidden official mobile control after hydration', t => {
    const w = page(t, '<button style="display:none" aria-label="Iniciar sesión"></button>' + nav);
    assert.equal(w.__av1MobileUi.openAccount(), 'retry');
    let clicks = 0;
    w.document.querySelector('[aria-label]').onclick = () => {
        clicks++;
        const password = w.document.createElement('input');
        password.type = 'password';
        password.getClientRects = () => [{}];
        w.document.body.appendChild(password);
    };
    assert.equal(w.__av1MobileUi.openAccount(), 'login-open');
    assert.equal(w.__av1MobileUi.openAccount(), 'login-open');
    assert.equal(clicks, 1, 'does not toggle the open dialog closed');
});

test('authenticated navigation is recognized', t => {
    const w = page(t, '<a href="/cuenta">Mi cuenta</a>');
    assert.equal(w.__av1MobileUi.openAccount(), 'authenticated');
});

test('own logout button appears once outside the profile form', async t => {
    const w = page(t, '<main><form><button>Actualizar Perfil</button></form></main>', '/cuenta');
    w.eval(script);
    await mutations();
    const button = w.document.querySelector('#av1-sign-out');
    assert.equal(button.textContent, 'Cerrar sesión');
    assert.equal(button.type, 'button');
    assert.equal(button.closest('form'), null);
    assert.equal(w.document.querySelectorAll('#av1-sign-out').length, 1);
    assert.equal(w.__av1MobileUi.openAccount(), 'authenticated');
});

test('logout follows account tab changes and disappears after leaving account', async t => {
    const w = page(t, '<main><form><button>Actualizar Perfil</button></form></main>', '/cuenta');
    w.document.querySelector('main').innerHTML = '<form><input type="password"><button>Actualizar contraseña</button></form>';
    await mutations();
    assert.ok(w.document.querySelector('#av1-sign-out'));
    w.history.pushState({}, '', '/');
    w.document.body.innerHTML = nav;
    await mutations();
    assert.equal(w.document.querySelector('#av1-sign-out'), null);
});

test('guest and 401 pages do not get a logout button', t => {
    const w = page(t, '<h1>401</h1><p>No autorizado</p>', '/cuenta');
    assert.equal(w.document.querySelector('#av1-sign-out'), null);
    assert.equal(w.__av1MobileUi.openAccount(), 'missing');
});

test('integration does not run on another origin', t => {
    const dom = new JSDOM(nav, { url: 'https://example.com', runScripts: 'outside-only' });
    t.after(() => dom.window.close());
    dom.window.eval(script);
    assert.equal(dom.window.__av1MobileUi, undefined);
    assert.equal(dom.window.document.querySelector('style'), null);
});
