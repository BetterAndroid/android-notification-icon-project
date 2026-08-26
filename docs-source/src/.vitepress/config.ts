import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import VueI18nPlugin from '@intlify/unplugin-vue-i18n/vite';
import {
    GitChangelog,
    GitChangelogMarkdownSection
} from '@nolebase/vitepress-plugin-git-changelog/vite';
import { defineConfig, type DefaultTheme, type UserConfigFn } from 'vitepress';
import { alignI18nAnchors } from './configs/anchors';
import { serveLocalIcons } from './configs/dev-assets';
import {
    createHomepageAlternates,
    createRootLocaleRedirect,
    localizedHomepagePaths
} from './configs/i18n';
import { configs, pageLinkRefs } from './configs/template';
import { markdown } from './configs/utils';
import locales from './locales';

/** Creates the VitePress configuration for local development or production builds. */
const createConfig: UserConfigFn<DefaultTheme.Config> = ({ command }) => defineConfig({
    base: configs.website.base,
    title: configs.website.locales.en.title,
    description: configs.website.locales.en.description,
    outDir: configs.dev.dest,
    cacheDir: '.vitepress/cache',
    cleanUrls: true,
    vite: {
        css: {
            preprocessorOptions: {
                scss: {
                    api: 'modern-compiler'
                }
            }
        },
        optimizeDeps: {
            exclude: [
                '@nolebase/vitepress-plugin-enhanced-readabilities/client',
                '@nolebase/ui',
                'vitepress'
            ]
        },
        server: {
            port: configs.dev.port
        },
        plugins: [
            VueI18nPlugin({
                include: resolve(dirname(fileURLToPath(import.meta.url)), './locales/**'),
                ssr: true
            }),
            serveLocalIcons(),
            GitChangelog({
                repoURL: () => configs.github.repo
            }),
            GitChangelogMarkdownSection({
                excludes: ['index.md', ...localizedHomepagePaths],
                sections: {
                    disableContributors: true
                }
            })
        ],
        ssr: {
            noExternal: [
                '@nolebase/vitepress-plugin-enhanced-readabilities',
                '@nolebase/ui'
            ]
        }
    },
    head: [
        ['meta', { name: 'color-scheme', content: 'light dark' }],
        ['meta', { name: 'theme-color', content: '#596A8D' }],
        ['link', { rel: 'icon', href: configs.website.icon }]
    ],
    transformHead: ({ page }) => [
        ...createHomepageAlternates(page),
        ...createRootLocaleRedirect(page)
    ],
    locales: locales.locales,
    markdown: {
        image: {
            lazyLoading: true
        },
        config: (md) => {
            md.use(alignI18nAnchors);
            markdown.localizeContainerTitles(md);
            markdown.injectLinks(
                md,
                command === 'serve' ? pageLinkRefs.dev : pageLinkRefs.prod,
                configs.website.base
            );
        }
    },
    themeConfig: {
        logo: configs.website.logo,
        siteTitle: 'ANIP',
        socialLinks: [{
            icon: 'github',
            link: configs.github.repo
        }],
        search: {
            provider: 'local',
            options: {
                // VitePress has no isSearchable callback; empty rendered HTML excludes the root redirect page.
                _render: (src, renderEnv, md) => renderEnv.relativePath === 'index.md'
                    ? ''
                    : md.render(src, renderEnv),
                locales: {
                    'zh-cn': {
                        translations: {
                            button: {
                                buttonText: '搜索',
                                buttonAriaLabel: '搜索'
                            },
                            modal: {
                                noResultsText: '无法找到相关结果',
                                resetButtonTitle: '清除查询条件',
                                footer: {
                                    selectText: '选择',
                                    navigateText: '切换',
                                    closeText: '关闭'
                                }
                            }
                        }
                    }
                }
            }
        }
    }
});

export default createConfig;