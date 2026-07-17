/*
 * SecShare lightweight i18n engine.
 *
 * Usage per page:
 *   1. <script src="/i18n.js"></script>
 *   2. I18N.register({ en: {...}, tr: {...} });
 *   3. Mark static markup with data-i18n / data-i18n-html / data-i18n-ph /
 *      data-i18n-title / data-i18n-aria attributes, then call I18N.apply().
 *   4. In JS-built markup, call I18N.t('key', { var: value }).
 *   5. Optionally set window.onLangChange = fn to re-render dynamic content.
 *
 * The active language is auto-detected from the browser on first visit
 * (Turkish browsers get 'tr', everyone else 'en') and remembered in
 * localStorage after the user toggles it.
 */
(function () {
    const KEY = 'secshare_lang';
    let dict = { en: {}, tr: {} };

    function detect() {
        const saved = localStorage.getItem(KEY);
        if (saved === 'tr' || saved === 'en') return saved;
        const nav = (navigator.language || navigator.userLanguage || 'en').toLowerCase();
        return nav.startsWith('tr') ? 'tr' : 'en';
    }

    let lang = detect();

    function t(key, vars) {
        let s = (dict[lang] && dict[lang][key]);
        if (s == null) s = (dict.en && dict.en[key]);
        if (s == null) s = key;
        if (vars) {
            for (const k in vars) s = s.split('{' + k + '}').join(vars[k]);
        }
        return s;
    }

    function apply() {
        document.querySelectorAll('[data-i18n]').forEach(el => {
            el.textContent = t(el.getAttribute('data-i18n'));
        });
        document.querySelectorAll('[data-i18n-html]').forEach(el => {
            el.innerHTML = t(el.getAttribute('data-i18n-html'));
        });
        document.querySelectorAll('[data-i18n-ph]').forEach(el => {
            el.setAttribute('placeholder', t(el.getAttribute('data-i18n-ph')));
        });
        document.querySelectorAll('[data-i18n-title]').forEach(el => {
            el.setAttribute('title', t(el.getAttribute('data-i18n-title')));
        });
        document.querySelectorAll('[data-i18n-aria]').forEach(el => {
            el.setAttribute('aria-label', t(el.getAttribute('data-i18n-aria')));
        });
        document.querySelectorAll('[data-lang-toggle]').forEach(btn => {
            // The toggle shows the language you'd switch TO.
            btn.textContent = lang === 'tr' ? 'EN' : 'TR';
            btn.setAttribute('aria-label', lang === 'tr' ? 'Switch to English' : 'Türkçe\'ye geç');
        });
        if (document.title && dict[lang] && dict[lang]['_title']) {
            document.title = dict[lang]['_title'];
        }
    }

    function set(l) {
        if (l !== 'tr' && l !== 'en') return;
        lang = l;
        localStorage.setItem(KEY, l);
        document.documentElement.lang = l;
        apply();
        if (typeof window.onLangChange === 'function') window.onLangChange(l);
    }

    document.documentElement.lang = lang;

    window.I18N = {
        register(d) { dict = { en: d.en || {}, tr: d.tr || {} }; },
        t,
        apply,
        set,
        toggle() { set(lang === 'tr' ? 'en' : 'tr'); },
        get lang() { return lang; },
        // Locale tag for Date#toLocaleString / toLocaleDateString.
        get locale() { return lang === 'tr' ? 'tr-TR' : 'en-US'; }
    };
})();
