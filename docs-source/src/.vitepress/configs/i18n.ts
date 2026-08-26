import type { HeadConfig } from 'vitepress';
import { configs } from './template';

export type DocsLocale = keyof typeof configs.website.locales;

/** Default locale used when no previous language choice is available. */
export const defaultLocale: DocsLocale = 'en';

/** Documentation locales configured by the site. */
export const supportedLocales = Object.keys(configs.website.locales) as DocsLocale[];

/** Converts a locale identifier to its canonical BCP 47 representation. */
export const canonicalizeLocaleCode = (locale: string) => Intl.getCanonicalLocales(locale)[0];

/** Canonical locale codes accepted by localized resource labels. */
export const supportedLocaleCodes = supportedLocales.map(canonicalizeLocaleCode);

/** Common locale choices shown by the submission form. Values are serialized as their codes. */
export const commonLocaleOptions = [
    { code: 'en', name: 'English' },
    { code: 'en-US', name: 'English (US)' },
    { code: 'en-GB', name: 'English (UK)' },
    { code: 'en-AU', name: 'English (Australia)' },
    { code: 'en-CA', name: 'English (Canada)' },
    { code: 'en-IN', name: 'English (India)' },
    { code: 'zh-CN', name: '简体中文 (中国)' },
    { code: 'zh-HK', name: '繁體中文 (香港)' },
    { code: 'zh-TW', name: '繁體中文 (台灣)' },
    { code: 'ja-JP', name: '日本語 (日本)' },
    { code: 'ko-KR', name: '한국어 (대한민국)' },
    { code: 'fr-FR', name: 'français (France)' },
    { code: 'fr-CA', name: 'français (Canada)' },
    { code: 'de-DE', name: 'Deutsch (Deutschland)' },
    { code: 'es-ES', name: 'español (España)' },
    { code: 'es-MX', name: 'español (México)' },
    { code: 'pt-BR', name: 'português (Brasil)' },
    { code: 'pt-PT', name: 'português (Portugal)' },
    { code: 'it-IT', name: 'italiano (Italia)' },
    { code: 'ru-RU', name: 'русский (Россия)' },
    { code: 'uk-UA', name: 'українська (Україна)' },
    { code: 'pl-PL', name: 'polski (Polska)' },
    { code: 'nl-NL', name: 'Nederlands (Nederland)' },
    { code: 'tr-TR', name: 'Türkçe (Türkiye)' },
    { code: 'ar-SA', name: 'العربية (السعودية)' },
    { code: 'he-IL', name: 'עברית (ישראל)' },
    { code: 'fa-IR', name: 'فارسی (ایران)' },
    { code: 'hi-IN', name: 'हिन्दी (भारत)' },
    { code: 'bn-BD', name: 'বাংলা (বাংলাদেশ)' },
    { code: 'ta-IN', name: 'தமிழ் (இந்தியா)' },
    { code: 'ur-PK', name: 'اردو (پاکستان)' },
    { code: 'id-ID', name: 'Bahasa Indonesia (Indonesia)' },
    { code: 'ms-MY', name: 'Bahasa Melayu (Malaysia)' },
    { code: 'vi-VN', name: 'Tiếng Việt (Việt Nam)' },
    { code: 'th-TH', name: 'ไทย (ไทย)' },
    { code: 'fil-PH', name: 'Filipino (Pilipinas)' },
    { code: 'sw-KE', name: 'Kiswahili (Kenya)' },
    { code: 'cs-CZ', name: 'čeština (Česko)' },
    { code: 'da-DK', name: 'dansk (Danmark)' },
    { code: 'fi-FI', name: 'suomi (Suomi)' },
    { code: 'nb-NO', name: 'norsk bokmål (Norge)' },
    { code: 'sv-SE', name: 'svenska (Sverige)' },
    { code: 'el-GR', name: 'Ελληνικά (Ελλάδα)' },
    { code: 'hu-HU', name: 'magyar (Magyarország)' },
    { code: 'ro-RO', name: 'română (România)' },
    { code: 'sk-SK', name: 'slovenčina (Slovensko)' },
    { code: 'ca-ES', name: 'català (Espanya)' }
] as const;

/** Fast lookup used to keep the form's locale values inside the built-in table. */
export const commonLocaleCodes = new Set(commonLocaleOptions.map(({ code }) => code.toLocaleLowerCase()));

/** Localized homepage source paths derived from the configured locales. */
export const localizedHomepagePaths = supportedLocales.map((locale) => `${locale}/index.md`);

const homepagePaths = new Set(['index.md', ...localizedHomepagePaths]);

/** Browser storage key for the visitor's most recently selected locale. */
export const localeStorageKey = 'android-notification-icon-project-docs-locale';

/** Resolves the locale segment from a localized documentation route. */
export const resolveRouteLocale = (path: string) => path
    .split('/')
    .find((segment): segment is DocsLocale => supportedLocales.includes(segment as DocsLocale));

const routeWithoutLocale = (path: string) => path
    .split('/')
    .filter((segment) => !supportedLocales.includes(segment as DocsLocale))
    .join('/');

/** Returns whether navigation only changes the locale of the same documentation page. */
export const isLocalizedRouteSwitch = (fromPath: string, toPath: string) => {
    const fromLocale = resolveRouteLocale(fromPath);
    const toLocale = resolveRouteLocale(toPath);
    return Boolean(fromLocale && toLocale && fromLocale !== toLocale &&
        routeWithoutLocale(fromPath) === routeWithoutLocale(toPath));
};

/** Resolves a source page locale, falling back to the configured default. */
export const resolvePageLocale = (path: string | null | undefined) =>
    path ? resolveRouteLocale(path) ?? defaultLocale : defaultLocale;

/** Returns a supported stored locale or the configured default. */
export const resolveStoredLocale = (locale: string | null) => supportedLocales
    .find((supportedLocale) => supportedLocale === locale) ?? defaultLocale;

/** Creates reciprocal hreflang links for localized homepages. */
export const createHomepageAlternates = (page: string): HeadConfig[] => {
    if (!homepagePaths.has(page)) return [];
    const siteRoot = `${configs.github.page}/`;
    const localeLinks = Object.entries(configs.website.locales).map(([locale, options]) => [
        'link',
        {
            rel: 'alternate',
            hreflang: options.lang,
            href: `${siteRoot}${locale}/`
        }
    ] satisfies HeadConfig);
    return [
        ...localeLinks,
        ['link', { rel: 'alternate', hreflang: 'x-default', href: siteRoot }]
    ];
};

/** Redirects the static root page to the default or a previously selected locale. */
export const createRootLocaleRedirect = (page: string): HeadConfig[] => {
    if (page !== 'index.md') return [];
    const base = configs.website.base;
    const locales = JSON.stringify(supportedLocales);
    const script = `(() => { const supportedLocales = ${locales}; let locale = '${defaultLocale}'; try { const saved = localStorage.getItem('${localeStorageKey}'); if (supportedLocales.includes(saved)) locale = saved; } catch {} location.replace('${base}' + locale + '/'); })();`;
    return [['script', {}, script]];
};