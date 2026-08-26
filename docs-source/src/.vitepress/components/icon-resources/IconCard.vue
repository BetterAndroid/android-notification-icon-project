<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue';
import { useComponentMessages } from '../../theme/i18n';
import { resolveLabel, type ResolvedIconEntry } from '../../data/icon-resources';

const props = defineProps<{
    entry: ResolvedIconEntry;
}>();

const { locale, text } = useComponentMessages('iconCard');
const imageIndex = ref(0);
const imageFailed = ref(false);
const targetOpen = ref(false);
const targetPopover = ref<HTMLElement>();
const label = computed(() => resolveLabel(props.entry.effectiveRule.label, locale.value) ?? props.entry.key);
const imageUrl = computed(() => props.entry.iconUrls[imageIndex.value]);
const iconStyle = computed(() => ({
    '--icon-color': props.entry.effectiveRule.color ?? '#586174'
}));

watch(() => props.entry.key, () => {
    imageIndex.value = 0;
    imageFailed.value = false;
    targetOpen.value = false;
});

const closeTargetOnDocumentClick = (event: MouseEvent) => {
    if (!targetPopover.value?.contains(event.target as Node)) targetOpen.value = false;
};
const closeTargetOnEscape = (event: KeyboardEvent) => {
    if (event.key === 'Escape') targetOpen.value = false;
};

watch(targetOpen, (open) => {
    if (open) {
        document.addEventListener('click', closeTargetOnDocumentClick);
        document.addEventListener('keydown', closeTargetOnEscape);
        return;
    }
    document.removeEventListener('click', closeTargetOnDocumentClick);
    document.removeEventListener('keydown', closeTargetOnEscape);
});

onBeforeUnmount(() => {
    document.removeEventListener('click', closeTargetOnDocumentClick);
    document.removeEventListener('keydown', closeTargetOnEscape);
});

const useNextImage = () => {
    if (imageIndex.value + 1 < props.entry.iconUrls.length) {
        imageIndex.value += 1;
        return;
    }
    imageFailed.value = true;
};
</script>

<template>
    <article class="icon-card">
        <button class="icon-preview" type="button" :style="iconStyle" :title="text.preview"
            :disabled="!imageUrl || imageFailed">
            <img v-if="imageUrl && !imageFailed" class="icon-resources-viewer-image" :src="imageUrl" :alt="label"
                loading="lazy" decoding="async" @error="useNextImage">
            <span v-else aria-hidden="true">!</span>
        </button>
        <div class="icon-summary">
            <div class="icon-title-row">
                <h3>{{ label }}</h3>
                <div v-if="entry.rule.target" ref="targetPopover" class="target-popover" @mouseenter="targetOpen = true"
                    @mouseleave="targetOpen = false" @focusin="targetOpen = true" @focusout="targetOpen = false">
                    <button type="button" class="target-trigger" :aria-label="text.target" :aria-expanded="targetOpen"
                        @click="targetOpen = true">
                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
                            stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                            <path d="M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71" />
                            <path d="M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71" />
                        </svg>
                    </button>
                    <Transition name="target-bubble">
                        <div v-if="targetOpen" class="target-bubble" role="status">
                            <span>{{ text.target }}</span>
                            <code>{{ entry.rule.target }}</code>
                        </div>
                    </Transition>
                </div>
                <div v-if="entry.effectiveRule.format || entry.effectiveRule.overlay" class="icon-badges">
                    <span v-if="entry.effectiveRule.format" class="format-badge">{{ entry.effectiveRule.format }}</span>
                    <span v-if="entry.effectiveRule.overlay" class="overlay-badge">{{ text.overlay }}</span>
                </div>
            </div>
            <div class="package-name language-package">
                <button type="button" class="copy" :title="text.copyPackage" :aria-label="text.copyPackage"></button>
                <span class="lang" aria-hidden="true"></span>
                <code>{{ entry.key }}</code>
            </div>
            <p v-if="entry.effectiveRule.contributors" class="secondary-line">
                {{ text.contributors }}: {{ entry.effectiveRule.contributors }}
            </p>
            <p v-for="error in entry.errors" :key="error" class="error-line">{{ error }}</p>
        </div>
    </article>
</template>

<style scoped lang="scss">
.icon-card {
    display: grid;
    grid-template-columns: 60px minmax(0, 1fr);
    gap: 14px;
    padding: 16px;
    border: 1px solid var(--vp-c-divider);
    border-radius: 12px;
    background: var(--vp-c-bg-soft);
}

.icon-preview {
    display: grid;
    width: 56px;
    height: 56px;
    margin: 0;
    padding: 0;
    border: 0;
    place-items: center;
    border-radius: 16px;
    background: var(--icon-color);
    box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.18);
    cursor: zoom-in;

    img {
        width: 38px;
        height: 38px;
        object-fit: contain;
    }

    span {
        color: white;
        font-size: 24px;
        font-weight: 700;
    }

    &:disabled {
        cursor: default;
    }
}

.icon-resources-viewer-image {
    filter: brightness(0) invert(1);
}

.icon-title-row {
    display: flex;
    align-items: center;
    gap: 8px;

    h3 {
        margin: 0;
        border: 0;
        padding: 0;
        font-size: 17px;
        line-height: 1.4;
    }
}

.target-popover {
    position: relative;
    flex: 0 0 auto;
    margin: 0 -4px;
    line-height: 0;
}

.target-trigger {
    display: grid;
    width: 24px;
    height: 24px;
    margin: 0;
    padding: 0;
    border: 0;
    place-items: center;
    border-radius: 6px;
    background: transparent;
    color: var(--vp-c-text-3);
    cursor: pointer;

    svg {
        width: 15px;
        height: 15px;
    }

    &:hover,
    &[aria-expanded='true'] {
        background: var(--vp-c-default-soft);
        color: var(--vp-c-brand-1);
    }
}

.target-bubble {
    position: absolute;
    z-index: 30;
    top: calc(100% + 7px);
    right: -6px;
    width: max-content;
    max-width: min(280px, calc(100vw - 32px));
    padding: 9px 11px;
    border: 1px solid var(--vp-c-divider);
    border-radius: 10px;
    background: var(--vp-c-bg-elv);
    box-shadow: var(--vp-shadow-3);
    line-height: 1.4;

    &::before {
        position: absolute;
        right: 0;
        bottom: 100%;
        left: 0;
        height: 8px;
        content: '';
    }

    span {
        display: block;
        color: var(--vp-c-text-3);
        font-size: 11px;
    }

    code {
        display: block;
        margin-top: 2px;
        padding: 0;
        background: transparent;
        color: var(--vp-c-text-1);
        font-size: 12px;
        overflow-wrap: anywhere;
        white-space: normal;
    }
}

.target-bubble-enter-active,
.target-bubble-leave-active {
    transition: opacity 0.15s ease, transform 0.15s ease;
}

.target-bubble-enter-from,
.target-bubble-leave-to {
    opacity: 0;
    transform: translateY(-5px);
}

.icon-summary {
    min-width: 0;
}

div.package-name {
    --copy-icon-color: #9ca3af;
    --package-name-background: rgba(156, 163, 175, 0.1);

    position: relative;
    margin: 4px 0 0;
    overflow: hidden;
    border-radius: 4px;
    background: var(--package-name-background);

    >code {
        display: block;
        width: 100%;
        min-width: 0;
        padding: 3px 38px 3px 6px;
        overflow: hidden;
        background: transparent;
        color: var(--vp-c-text-2);
        line-height: inherit;
        text-overflow: ellipsis;
        white-space: nowrap;
    }

    >span.lang {
        display: none;
    }

    >button.copy {
        top: 0;
        right: 0;
        width: 32px;
        height: 100%;
        border: 0;
        border-radius: 0 4px 4px 0;
        background-color: transparent;
        background-image: none;
        color: var(--copy-icon-color);
        opacity: 1;

        &::after {
            position: absolute;
            top: 50%;
            left: 50%;
            width: 17px;
            height: 17px;
            background: currentColor;
            content: '';
            mask: var(--vp-icon-copy) center / contain no-repeat;
            transform: translate(-50%, -50%);
        }

        &:hover,
        &:focus {
            background-color: var(--vp-c-default-soft);
        }

        &.copied,
        &:hover.copied {
            border-radius: 0 4px 4px 0;
            background-color: var(--vp-c-default-soft);
            background-image: none;
        }

        &.copied::after,
        &:hover.copied::after {
            mask-image: var(--vp-icon-copied);
        }

        &.copied::before,
        &:hover.copied::before {
            display: none;
            content: none;
        }
    }
}

:global(.dark) div.package-name {
    --copy-icon-color: #b6bec9;
    --package-name-background: rgba(156, 163, 175, 0.18);
}

.icon-badges {
    display: flex;
    align-items: center;
    gap: 4px;
}

.format-badge,
.overlay-badge {
    padding: 2.5px 6px;
    border-radius: 10px;
    font-size: 12px;
    font-weight: 600;
    line-height: 1.2;
}

.format-badge {
    background: var(--vp-c-brand-soft);
    color: var(--vp-c-brand-1);
    text-transform: uppercase;
}

.overlay-badge {
    background: var(--vp-c-warning-soft);
    color: var(--vp-c-warning-1);
}

.secondary-line,
.error-line {
    margin: 5px 0 0;
    font-size: 13px;
    line-height: 1.5;
}

.secondary-line {
    color: var(--vp-c-text-2);
}

.error-line {
    color: var(--vp-c-danger-1);
}
</style>