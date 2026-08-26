type Locale = 'en' | 'zh-cn';

interface PageLinkRefs {
    dev: Record<string, string>[];
    prod: Record<string, string>[];
}

interface NavigationLink {
    path: string;
    title: Record<Locale, string>;
}

interface NavigationSection {
    links: NavigationLink[];
    title: Record<Locale, string>;
}

const navigationSections: NavigationSection[] = [{
    title: { en: 'Get Started', 'zh-cn': '入门' },
    links: [
        { path: '/guide/home', title: { en: 'Introduction', 'zh-cn': '介绍' } },
        { path: '/guide/icon-resources', title: { en: 'Icon Resources', 'zh-cn': '图标资源' } }
    ]
}, {
    title: { en: 'Contribution', 'zh-cn': '参与项目' },
    links: [
        { path: '/contribute/request', title: { en: 'Request Adaptation', 'zh-cn': '请求适配' } },
        { path: '/contribute/submit', title: { en: 'Ready to Submit', 'zh-cn': '准备提交' } }
    ]
}, {
    title: { en: 'About', 'zh-cn': '关于' },
    links: [
        { path: '/about/contacts', title: { en: 'Contact Us', 'zh-cn': '联系我们' } },
        { path: '/about/adopters', title: { en: 'Adopters', 'zh-cn': '谁在使用' } },
        { path: '/about/about', title: { en: 'About This Document', 'zh-cn': '关于此文档' } }
    ]
}];

const topNavigationLinks: NavigationLink[] = [
    { path: '/', title: { en: 'Home', 'zh-cn': '首页' } },
    { path: '/guide/icon-resources', title: { en: 'Icon Resources', 'zh-cn': '图标资源' } },
    { path: '/about/contacts', title: { en: 'Contact Us', 'zh-cn': '联系我们' } }
];

const localizedLink = (link: NavigationLink, locale: Locale) => ({
    text: link.title[locale],
    link: `/${locale}${link.path}`
});

/** Creates localized top navigation and documentation sidebars. */
export const createThemeNavigation = (locale: Locale) => {
    const sections = navigationSections.map((section) => ({
        text: section.title[locale],
        collapsed: false,
        items: section.links.map((link) => localizedLink(link, locale))
    }));
    return {
        nav: topNavigationLinks.map((link) => localizedLink(link, locale)),
        sidebar: {
            [`/${locale}/`]: sections
        }
    };
};

/** Shared website, repository, and local development settings. */
export const configs = {
    dev: {
        dest: '../dist',
        port: 9000
    },
    website: {
        base: '/android-notification-icon-project/',
        icon: '/android-notification-icon-project/images/logo.svg',
        logo: '/images/logo.svg',
        locales: {
            'en': {
                title: 'Android Notification Icon Project',
                lang: 'en-US',
                description: 'Provides standardized monochrome icon resources for apps and vendor systems that do not conform to the Android standard notification design'
            },
            'zh-cn': {
                title: 'Android 通知图标适配计划',
                lang: 'zh-CN',
                description: '为不符合 Android 原生通知设计的应用与厂商系统提供规范的单色图标资源'
            }
        }
    },
    github: {
        repo: 'https://github.com/BetterAndroid/android-notification-icon-project',
        page: 'https://betterandroid.github.io/android-notification-icon-project',
        branch: 'main',
        sourceDir: 'docs-source/src'
    },
    resources: {
        raw: 'https://raw.githubusercontent.com/BetterAndroid/android-notification-icon-project/main/icons/',
        cdn: 'https://cdn.jsdelivr.net/gh/BetterAndroid/android-notification-icon-project@main/icons/'
    }
} as const;

/** Markdown protocol replacements for local development and production builds. */
export const pageLinkRefs: PageLinkRefs = {
    dev: [
        { 'repo://': `${configs.github.repo}/` },
        { 'releases://': `${configs.github.repo}/releases/` },
        { 'branch://': `${configs.github.repo}/blob/${configs.github.branch}/` },
        { 'raw://': `${configs.github.repo}/raw/${configs.github.branch}/` },
        { 'icons://': `http://localhost:${configs.dev.port}${configs.website.base}icons/` }
    ],
    prod: [
        { 'repo://': `${configs.github.repo}/` },
        { 'releases://': `${configs.github.repo}/releases/` },
        { 'branch://': `${configs.github.repo}/blob/${configs.github.branch}/` },
        { 'raw://': `${configs.github.repo}/raw/${configs.github.branch}/` },
        { 'icons://': configs.resources.raw }
    ]
};