import { computed } from 'vue';
import { createI18n, useI18n, type VueMessageType } from 'vue-i18n';
import { defaultLocale } from '../configs/i18n';
import componentMessages from '../locales/components.json';

type ComponentMessageSection = keyof typeof componentMessages.en;
type ComponentMessages<Section extends ComponentMessageSection> =
    typeof componentMessages.en[Section];

/** Shared Composition API i18n instance for custom Vue components. */
export const componentI18n = createI18n({
    fallbackLocale: defaultLocale,
    legacy: false,
    locale: defaultLocale,
    messages: componentMessages
});

const isCompiledMessage = (value: object) =>
    ('type' in value && 'body' in value) || ('t' in value && 'b' in value);
const resolveMessageTree = <Messages>(
    value: unknown,
    resolve: (message: VueMessageType) => string
): Messages => {
    if (typeof value === 'string' || typeof value === 'function' || Array.isArray(value) ||
        value && typeof value === 'object' && isCompiledMessage(value))
        return resolve(value as VueMessageType) as Messages;
    if (!value || typeof value !== 'object') throw new Error('Invalid component locale message');
    return Object.fromEntries(Object.entries(value).map(([key, message]) => [
        key,
        resolveMessageTree(message, resolve)
    ])) as Messages;
};

/** Returns one translated component message section and the active locale. */
export const useComponentMessages = <Section extends ComponentMessageSection>(section: Section) => {
    const { locale, rt, tm } = useI18n({ useScope: 'global' });
    const text = computed(() => resolveMessageTree<ComponentMessages<Section>>(
        tm(section),
        (message) => rt(message)
    ));
    return { locale, text };
};