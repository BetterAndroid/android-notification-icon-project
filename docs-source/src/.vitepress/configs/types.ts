import type { MarkdownOptions } from 'vitepress';

/** VitePress's exact MarkdownIt callback type, avoiding duplicate declaration conflicts. */
export type VitePressMarkdownIt = Parameters<NonNullable<MarkdownOptions['config']>>[0];