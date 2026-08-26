<script setup lang="ts">
import { inject } from 'vue';
import {
    CheckOutlined,
    CloseOutlined,
    ExclamationOutlined,
    RollbackOutlined
} from '@ant-design/icons-vue';
import { submitAdaptionContextKey } from './utils/context';

const context = inject(submitAdaptionContextKey);
if (!context) throw new Error('SubmitAdaption context is unavailable');

const {
    clearSelectedIcon,
    fileInput,
    handleFileDrop,
    handleFileSelection,
    handleIconDragEnter,
    handleIconDragLeave,
    handleIconDragOver,
    iconDragActive,
    iconValidating,
    openFilePicker,
    previewColorizableOnHover,
    previewColorized,
    previewSource,
    selectedTarget,
    text,
    validationStatus
} = context;
const bindFileInput = (element: unknown) =>
    fileInput.value = element instanceof HTMLInputElement ? element : undefined;
</script>
<template>
    <section id="submit-icon" class="icon-checker" :class="{ 'drag-active': iconDragActive }"
        :aria-busy="iconValidating" tabindex="-1" @dragenter.prevent="handleIconDragEnter"
        @dragover.prevent="handleIconDragOver" @dragleave.prevent="handleIconDragLeave"
        @drop.prevent.stop="handleFileDrop">
        <input :ref="bindFileInput" class="visually-hidden" type="file" accept=".png,.svg,image/png,image/svg+xml"
            :aria-label="text.selectIcon" @change="handleFileSelection">
        <template v-if="previewSource">
            <div v-for="tone in ['dark', 'mid', 'light']" :key="tone" class="icon-preview" :class="[tone, {
                colorized: previewColorized,
                'hover-colorized': previewColorizableOnHover
            }]">
                <img :src="previewSource" alt="">
                <span v-if="previewColorizableOnHover" class="hover-colorized-overlay" aria-hidden="true">
                    <img :src="previewSource" alt="">
                </span>
            </div>
            <button v-if="!selectedTarget" type="button" class="compact-icon-button" :title="text.clearIcon"
                :aria-label="text.clearIcon" @click="clearSelectedIcon">
                <RollbackOutlined aria-hidden="true" />
            </button>
        </template>
        <button v-else type="button" class="icon-button select-empty" :title="text.selectIcon"
            :aria-label="text.selectIcon" @click="openFilePicker">
            <span class="vpi-plus select-plus" aria-hidden="true" />
            <small>{{ text.selectHint }}</small>
        </button>
        <div class="checker-message">
            <div class="validation-result" :class="validationStatus.state" role="status">
                <span v-if="['valid', 'warning', 'invalid'].includes(validationStatus.state)" class="validation-mark"
                    aria-hidden="true">
                    <CheckOutlined v-if="validationStatus.state === 'valid'" />
                    <ExclamationOutlined v-else-if="validationStatus.state === 'warning'" />
                    <CloseOutlined v-else />
                </span>
                <span class="validation-label">{{ validationStatus.label }}</span>
            </div>
            <small v-if="!previewSource" class="sponsor-hint">
                {{ text.sponsorPrefix }}
                <a href="https://github.com/pzcn/Perfect-Icons-Completion-Project/tree/main/icons" target="_blank"
                    rel="noopener noreferrer">{{ text.sponsorProject }}</a>
                {{ text.sponsorSuffix }}
            </small>
        </div>
    </section>
</template>
<style scoped lang="scss">
.visually-hidden {
    position: absolute;
    width: 1px;
    height: 1px;
    overflow: hidden;
    clip: rect(0 0 0 0);
    white-space: nowrap;
    clip-path: inset(50%);
}

.icon-checker {
    position: relative;
    display: flex;
    align-items: center;
    gap: 14px;
    min-height: 96px;
    padding: 18px 0 24px;
    border-bottom: 1px solid var(--vp-c-divider);
    border-radius: 0;
    transition: border-radius 0.18s ease;

    &::after {
        position: absolute;
        z-index: 2;
        border-radius: inherit;
        background: #000;
        content: '';
        inset: 0;
        opacity: 0;
        pointer-events: none;
        transition: opacity 0.18s ease;
    }

    &.drag-active {
        border-radius: 10px;

        &::after {
            opacity: 0.06;
        }
    }
}

.checker-message {
    display: flex;
    min-width: 0;
    flex: 1 1 280px;
    align-self: stretch;
    align-items: flex-start;
    flex-direction: column;
    gap: 7px;
    justify-content: center;
}

.sponsor-hint {
    display: block;
    margin: 0 0 0 8px;
    color: var(--vp-c-text-3);
    font-size: 13px;
    line-height: 1.4;
    text-align: left;

    a {
        color: inherit;
        text-decoration: underline;
        text-underline-offset: 2px;

        &:hover {
            color: var(--vp-c-text-2);
        }
    }
}

.icon-preview,
.icon-button {
    display: grid;
    flex: 0 0 72px;
    width: 72px;
    height: 72px;
    place-items: center;
    border: 0;
    border-radius: 8px;
}

.icon-preview {
    position: relative;

    &.dark {
        background: #616161;
    }

    &.mid {
        background: #969696;
    }

    &.light {
        background: #f0f0f0;
    }

    img {
        width: 48px;
        height: 48px;
        filter: none;
        object-fit: contain;
    }

    &.colorized.dark>img,
    &.colorized.mid>img,
    &.hover-colorized.dark .hover-colorized-overlay img,
    &.hover-colorized.mid .hover-colorized-overlay img {
        filter: brightness(0) invert(1);
    }

    &.colorized.light>img,
    &.hover-colorized.light .hover-colorized-overlay img {
        filter: brightness(0) invert(26%);
    }

    .hover-colorized-overlay {
        position: absolute;
        display: grid;
        border-radius: inherit;
        background: inherit;
        inset: 0;
        opacity: 0;
        place-items: center;
        pointer-events: none;
        transition: opacity 0.22s ease;
    }

    &.hover-colorized:hover .hover-colorized-overlay {
        opacity: 1;
    }
}

.icon-button {
    border: 1px dashed var(--vp-c-divider);
    background: var(--vp-c-bg-soft);
    color: var(--vp-c-text-2);
    cursor: pointer;

    &:hover {
        border-color: var(--vp-c-brand-1);
        color: var(--vp-c-brand-1);
    }
}

.select-empty {
    display: flex;
    width: 118px;
    flex-basis: 118px;
    align-items: center;
    flex-direction: column;
    gap: 2px;
    justify-content: center;

    .select-plus {
        width: 28px;
        height: 28px;
    }

    small {
        display: block;
        max-width: 104px;
        color: var(--vp-c-text-3);
        font-size: 11px;
        line-height: 1.25;
        text-align: center;
    }
}

.compact-icon-button {
    display: grid;
    width: 28px;
    height: 28px;
    flex: 0 0 28px;
    place-items: center;
    padding: 0;
    border: 1px solid var(--vp-c-divider);
    border-radius: 6px;
    background: var(--vp-c-bg-soft);
    color: var(--vp-c-text-2);
    font: inherit;
    cursor: pointer;

    &:hover {
        border-color: var(--vp-c-brand-1);
        color: var(--vp-c-brand-1);
    }
}

.validation-result {
    display: flex;
    align-items: center;
    gap: 8px;
    min-width: 0;
    margin-left: 8px;
    color: var(--vp-c-text-3);
    font-size: 18px;

    .validation-mark {
        display: grid;
        width: 1.15em;
        height: 1.15em;
        flex: 0 0 1.15em;
        line-height: 1;
        place-items: center;

        :deep(.anticon) {
            display: grid;
            width: 1.15em;
            height: 1.15em;
            place-items: center;
            vertical-align: 0;
        }

        :deep(svg) {
            display: block;
            width: 100%;
            height: 100%;
        }
    }

    .validation-label {
        display: flex;
        align-items: center;
        line-height: 1.35;
        white-space: pre-line;
    }

    &.valid {
        color: #3b9586;
    }

    &.invalid {
        color: var(--vp-c-danger-1);
    }

    &.warning {
        color: var(--vp-c-warning-1);
    }
}

@media (max-width: 760px) {
    .icon-checker {
        flex-wrap: wrap;
        gap: 10px;
    }

    .icon-preview,
    .icon-button {
        width: 58px;
        height: 58px;
        flex-basis: 58px;
    }

    .icon-preview img {
        width: 40px;
        height: 40px;
    }

    .select-empty {
        width: 112px;
        flex-basis: 112px;
    }

    .checker-message {
        width: 100%;
        flex-basis: 100%;
    }

    .validation-result {
        width: 100%;
        margin: 6px 0 0;
        font-size: 16px;

        .validation-mark {
            width: 1.15em;
            height: 1.15em;
            flex-basis: 1.15em;

            :deep(.anticon) {
                width: 1.15em;
                height: 1.15em;
            }
        }
    }

    .sponsor-hint {
        margin-right: 0;
        margin-left: 0;
    }
}
</style>