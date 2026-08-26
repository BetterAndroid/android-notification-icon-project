import { resolveI18nLink } from './anchors';
import { resolvePageLocale, type DocsLocale } from './i18n';
import type { VitePressMarkdownIt } from './types';

const containerTypes = ['tip', 'warning', 'danger'] as const;
type ContainerType = typeof containerTypes[number];
const containerTitles: Record<DocsLocale, Record<ContainerType, string>> = {
    'en': {
        tip: 'Tip',
        warning: 'Notice',
        danger: 'Warning'
    },
    'zh-cn': {
        tip: '提示',
        warning: '注意',
        danger: '警告'
    }
};

/** Markdown renderer customizations shared by development and production. */
export const markdown = {
    /** Localizes untitled VitePress custom containers. */
    localizeContainerTitles: (md: VitePressMarkdownIt) => {
        for (const type of containerTypes) {
            const ruleName = `container_${type}_open`;
            const defaultRender = md.renderer.rules[ruleName];
            if (!defaultRender) continue;
            md.renderer.rules[ruleName] = function (tokens, index, options, renderEnv, self) {
                const token = tokens[index];
                const originalInfo = token.info;
                if (originalInfo.trim() !== type)
                    return defaultRender(tokens, index, options, renderEnv, self);
                const locale = resolvePageLocale(renderEnv.relativePath);
                token.info = `${type} ${containerTitles[locale][type]}`;
                try {
                    return defaultRender(tokens, index, options, renderEnv, self);
                } finally {
                    token.info = originalInfo;
                }
            };
        }
    },
    /** Rewrites project-owned link protocols and aligns localized anchors. */
    injectLinks: (md: VitePressMarkdownIt, maps: Record<string, string>[], base: string) => {
        const defaultRender = md.renderer.rules.link_open || function (tokens, index, options, _env, self) {
            return self.renderToken(tokens, index, options);
        };
        md.renderer.rules.link_open = function (tokens, index, options, renderEnv, self) {
            const hrefIndex = tokens[index].attrIndex('href');
            if (hrefIndex < 0 || !tokens[index].attrs)
                return defaultRender(tokens, index, options, renderEnv, self);
            let current = tokens[index].attrs[hrefIndex][1];
            current = resolveI18nLink({
                base,
                filePathRelative: renderEnv.relativePath
            }, current);
            for (const map of maps) {
                for (const [search, replace] of Object.entries(map)) {
                    if (!current.startsWith(search)) continue;
                    current = current.replace(search, replace);
                    break;
                }
            }
            tokens[index].attrs[hrefIndex][1] = current;
            return defaultRender(tokens, index, options, renderEnv, self);
        };
    }
};