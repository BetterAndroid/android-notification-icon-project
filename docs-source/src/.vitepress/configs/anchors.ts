import { existsSync, readFileSync, statSync } from 'node:fs';
import path from 'node:path';
import { slugify } from '@mdit-vue/shared';
import MarkdownIt from 'markdown-it';
import { defaultLocale, resolveRouteLocale } from './i18n';
import type { VitePressMarkdownIt } from './types';

interface HeadingAnchor {
    alignedId: string;
    localizedSlug: string;
    pathKey: string;
}

interface HeadingLookup {
    byPathKey: Map<string, HeadingAnchor>;
    bySlug: Map<string, string>;
}

interface CachedHeadingLookup {
    lookup: HeadingLookup;
    mtimeMs: number;
}

interface MarkdownToken {
    children: MarkdownToken[] | null;
    content: string;
    tag: string;
    type: string;
    attrGet: (name: string) => string | null;
    attrSet: (name: string, value: string) => void;
}

interface HeadingToken {
    inlineToken: MarkdownToken;
    pathKey: string;
    token: MarkdownToken;
}

interface LinkResolveContext {
    base?: string;
    filePathRelative?: string | null;
}

const sourceRoot = path.resolve(process.cwd(), 'src');
const defaultLocaleRoot = path.join(sourceRoot, defaultLocale);
const headingParser = new MarkdownIt({ html: true });
const headingLookupCache = new Map<string, CachedHeadingLookup>();

const createHeadingPathTracker = (): ((level: number) => string) => {
    const indexes: number[] = [];
    return (level: number) => {
        const slot = Math.max(level - 1, 0);
        indexes.length = slot + 1;
        for (let index = 0; index < slot; index += 1)
            if (typeof indexes[index] !== 'number') indexes[index] = 0;
        indexes[slot] = (indexes[slot] ?? -1) + 1;
        return indexes.join('.');
    };
};

const getHeadingText = (token: MarkdownToken) => (token.children ?? [])
    .filter((child) => child.type === 'text' || child.type === 'code_inline')
    .map((child) => child.content)
    .join('')
    .trim();

const createUniqueSlug = (candidate: string, usedSlugs: Set<string>) => {
    let resolvedSlug = candidate;
    let suffix = 1;
    while (usedSlugs.has(resolvedSlug)) {
        resolvedSlug = `${candidate}-${suffix}`;
        suffix += 1;
    }
    usedSlugs.add(resolvedSlug);
    return resolvedSlug;
};

const collectHeadings = (tokens: MarkdownToken[]) => {
    const nextPathKey = createHeadingPathTracker();
    const usedSlugs = new Set<string>();
    const headings: HeadingAnchor[] = [];
    for (let index = 0; index < tokens.length; index += 1) {
        const token = tokens[index];
        const inlineToken = tokens[index + 1];
        if (token?.type !== 'heading_open' || !inlineToken) continue;
        const localizedSlug = createUniqueSlug(slugify(getHeadingText(inlineToken)), usedSlugs);
        headings.push({
            alignedId: localizedSlug,
            localizedSlug,
            pathKey: nextPathKey(Number.parseInt(token.tag.slice(1), 10))
        });
    }
    return headings;
};

const resolveFileHeadings = (filePath: string) => collectHeadings(
    headingParser.parse(readFileSync(filePath, 'utf-8'), {}) as MarkdownToken[]
);

const buildHeadingLookup = (localizedFilePath: string, defaultLocaleFilePath: string): HeadingLookup => {
    const localizedHeadings = resolveFileHeadings(localizedFilePath);
    const defaultLocaleHeadings = resolveFileHeadings(defaultLocaleFilePath);
    const defaultLocaleByPathKey = new Map(
        defaultLocaleHeadings.map((heading) => [heading.pathKey, heading.alignedId])
    );
    const byPathKey = new Map<string, HeadingAnchor>();
    const bySlug = new Map<string, string>();
    for (const localizedHeading of localizedHeadings) {
        const heading = {
            ...localizedHeading,
            alignedId: defaultLocaleByPathKey.get(localizedHeading.pathKey) ?? localizedHeading.localizedSlug
        };
        byPathKey.set(heading.pathKey, heading);
        bySlug.set(heading.localizedSlug, heading.alignedId);
    }
    return { byPathKey, bySlug };
};

const ensureMarkdownFilePath = (filePath: string) => {
    if (existsSync(filePath) && statSync(filePath).isFile()) return filePath;
    const indexFilePath = path.join(filePath, 'index.md');
    return existsSync(indexFilePath) && statSync(indexFilePath).isFile() ? indexFilePath : null;
};

const resolveHeadingLookup = (filePathRelative: string | null | undefined) => {
    if (!filePathRelative) return null;
    const normalizedPath = filePathRelative.replace(/\\/g, '/');
    const locale = resolveRouteLocale(normalizedPath);
    if (!locale || locale === defaultLocale) return null;
    const localizedFilePath = ensureMarkdownFilePath(path.join(sourceRoot, ...normalizedPath.split('/')));
    const defaultLocaleFilePath = ensureMarkdownFilePath(
        path.join(defaultLocaleRoot, ...normalizedPath.split('/').slice(1))
    );
    if (!localizedFilePath || !defaultLocaleFilePath) return null;
    const cacheKey = `${localizedFilePath}::${defaultLocaleFilePath}`;
    const mtimeMs = Math.max(statSync(localizedFilePath).mtimeMs, statSync(defaultLocaleFilePath).mtimeMs);
    const cached = headingLookupCache.get(cacheKey);
    if (cached?.mtimeMs === mtimeMs) return cached.lookup;
    const lookup = buildHeadingLookup(localizedFilePath, defaultLocaleFilePath);
    headingLookupCache.set(cacheKey, { lookup, mtimeMs });
    return lookup;
};

const collectHeadingTokens = (tokens: MarkdownToken[]) => {
    const nextPathKey = createHeadingPathTracker();
    const headings: HeadingToken[] = [];
    for (let index = 0; index < tokens.length; index += 1) {
        const token = tokens[index];
        const inlineToken = tokens[index + 1];
        if (token?.type !== 'heading_open' || !inlineToken) continue;
        headings.push({
            token,
            inlineToken,
            pathKey: nextPathKey(Number.parseInt(token.tag.slice(1), 10))
        });
    }
    return headings;
};

const syncPermalinkHref = (inlineToken: MarkdownToken, id: string) => {
    for (const child of inlineToken.children ?? []) {
        const className = child.attrGet('class') ?? '';
        if (child.type !== 'link_open' || !className.split(/\s+/).includes('header-anchor')) continue;
        child.attrSet('href', `#${id}`);
        break;
    }
};

const normalizeRelativeTarget = (currentFile: string, rawPath: string, base: string) => {
    const current = currentFile.replace(/\\/g, '/');
    if (!rawPath) return current;
    if (/^[a-z][a-z\d+.-]*:/i.test(rawPath) || rawPath.startsWith('//')) return null;
    if (rawPath.startsWith('/')) {
        const normalizedBase = base === '/' ? '/' : `${base.replace(/\/+$/, '')}/`;
        const trimmed = rawPath.startsWith(normalizedBase)
            ? rawPath.slice(normalizedBase.length)
            : rawPath.replace(/^\/+/, '');
        return trimmed.endsWith('.md') ? trimmed : trimmed.replace(/\.html$/, '') + '.md';
    }
    const resolved = path.posix.join(path.posix.dirname(current), rawPath);
    return resolved.endsWith('.md') ? resolved : resolved.replace(/\.html$/, '') + '.md';
};

/** Aligns translated heading IDs with matching default-locale heading positions. */
export const alignI18nAnchors = (md: VitePressMarkdownIt) => {
    md.core.ruler.after('anchor', 'align-i18n-anchors', (state) => {
        const lookup = resolveHeadingLookup(state.env.relativePath);
        if (!lookup) return;
        for (const heading of collectHeadingTokens(state.tokens as MarkdownToken[])) {
            const aligned = lookup.byPathKey.get(heading.pathKey);
            if (!aligned) continue;
            heading.token.attrSet('id', aligned.alignedId);
            syncPermalinkHref(heading.inlineToken, aligned.alignedId);
        }
    });
};

/** Rewrites translated hash fragments to their aligned default-locale heading IDs. */
export const resolveI18nLink = (context: LinkResolveContext, rawHref: string) => {
    if (!context.filePathRelative || !rawHref.includes('#')) return rawHref;
    const hashIndex = rawHref.indexOf('#');
    const rawPath = rawHref.slice(0, hashIndex);
    const targetFile = normalizeRelativeTarget(context.filePathRelative, rawPath, context.base ?? '/');
    if (!targetFile) return rawHref;
    const lookup = resolveHeadingLookup(targetFile);
    if (!lookup) return rawHref;
    const rawHash = decodeURIComponent(rawHref.slice(hashIndex + 1));
    const resolvedHash = lookup.bySlug.get(rawHash);
    if (!resolvedHash || resolvedHash === rawHash) return rawHref;
    if (!rawPath || targetFile === context.filePathRelative.replace(/\\/g, '/')) return `#${resolvedHash}`;
    return `${rawPath}#${resolvedHash}`;
};