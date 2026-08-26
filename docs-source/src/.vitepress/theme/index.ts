import {
    InjectionKey as InjectionKeyEnhancedReadabilities,
    NolebaseEnhancedReadabilitiesMenu,
    NolebaseEnhancedReadabilitiesScreenMenu,
    type Options as EnhancedReadabilitiesOptions
} from '@nolebase/vitepress-plugin-enhanced-readabilities/client';
import '@nolebase/vitepress-plugin-enhanced-readabilities/client/style.css';
import {
    InjectionKey as InjectionKeyGitChangelogPlugin,
    NolebaseGitChangelogPlugin,
    type Options as GitChangelogOptions
} from '@nolebase/vitepress-plugin-git-changelog/client';
import '@nolebase/vitepress-plugin-git-changelog/client/style.css';
import { inBrowser, useData, useRoute, withBase, type Theme } from 'vitepress';
import DefaultTheme from 'vitepress/theme';
import giscusTalk from 'vitepress-plugin-comment-with-giscus';
import { computed, h, watch } from 'vue';
import { giscusExcludedPages, giscusOptions } from '../configs/giscus';
import {
    defaultLocale,
    localeStorageKey,
    resolveRouteLocale,
    resolveStoredLocale
} from '../configs/i18n';
import GitHubUser from '../components/common/GitHubUser.vue';
import IconResources from '../components/IconResources.vue';
import SubmitAdaption from '../components/SubmitAdaption.vue';
import { componentI18n } from './i18n';
import './styles/index.scss';

const enhancedReadabilitiesOptions: EnhancedReadabilitiesOptions = {};

const gitChangelogOptions: GitChangelogOptions = {
    hideChangelogHeader: true,
    hideChangelogNoChangesText: true,
    displayAuthorsInsideCommitLine: true,
    locales: {
        'zh-CN': {
            changelog: {
                title: '变更日志',
                noData: '暂无最近变更日志',
                viewFullHistory: '查看完整变更日志',
                committedOn: ' 提交于 {{date}}',
                lastEdited: '最后编辑于 {{daysAgo}}',
                lastEditedDateFnsLocaleName: 'zhCN'
            }
        }
    }
};

export default {
    extends: DefaultTheme,
    Layout: () => h(DefaultTheme.Layout, null, {
        'nav-bar-content-after': () => h(NolebaseEnhancedReadabilitiesMenu),
        'nav-screen-content-after': () => h(NolebaseEnhancedReadabilitiesScreenMenu)
    }),
    enhanceApp({ app }) {
        app.use(componentI18n);
        app.component('GitHubUser', GitHubUser);
        app.component('IconResources', IconResources);
        app.component('SubmitAdaption', SubmitAdaption);
        app.use(NolebaseGitChangelogPlugin);
        app.provide(InjectionKeyGitChangelogPlugin, gitChangelogOptions);
        app.provide(InjectionKeyEnhancedReadabilities, enhancedReadabilitiesOptions);
    },
    setup() {
        const { frontmatter, page } = useData();
        const route = useRoute();
        watch(() => route.path, (currentPath) => {
            const locale = resolveRouteLocale(currentPath);
            if (locale) {
                componentI18n.global.locale.value = locale;
                if (inBrowser)
                    try {
                        localStorage.setItem(localeStorageKey, locale);
                    } catch {
                        // Privacy settings may disable storage without affecting locale navigation.
                    }
                return;
            }
            if (!inBrowser) return;
            const rootPaths = ['/', '/index.html', withBase('/'), withBase('/index.html')];
            if (!rootPaths.includes(currentPath)) return;
            let selectedLocale = defaultLocale;
            try {
                selectedLocale = resolveStoredLocale(localStorage.getItem(localeStorageKey));
            } catch {
                // English remains the deterministic fallback when storage is unavailable.
            }
            window.location.replace(withBase(`/${selectedLocale}/`));
        }, { immediate: true });
        // The plugin only supports exclusions through frontmatter, so project-owned exclusions are projected here.
        const commentFrontmatter = computed(() => {
            const commentsDisabled = frontmatter.value.layout === 'home' ||
                giscusExcludedPages.includes(page.value.relativePath);
            return commentsDisabled ? { ...frontmatter.value, comment: false } : frontmatter.value;
        });
        giscusTalk(giscusOptions, {
            frontmatter: commentFrontmatter,
            route
        });
    }
} satisfies Theme;