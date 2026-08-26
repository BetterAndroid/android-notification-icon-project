<script setup lang="ts">
import { inject } from 'vue';
import { submitAdaptionContextKey } from './utils/context';

const context = inject(submitAdaptionContextKey);
if (!context) throw new Error('SubmitAdaption context is unavailable');

const {
    confirmSubmission,
    continueOverlaySubmission,
    locale,
    modalOpen,
    overlayWarningOpen,
    pendingPayload,
    text
} = context;
</script>
<template>
    <Teleport to="body">
        <Transition name="submit-modal" appear>
            <div v-if="overlayWarningOpen" class="submit-modal-backdrop" role="presentation"
                @mousedown.self="overlayWarningOpen = false">
                <section class="submit-modal" role="dialog" aria-modal="true"
                    :aria-labelledby="`submit-overlay-warning-title-${locale}`">
                    <h2 :id="`submit-overlay-warning-title-${locale}`">{{ text.overlayWarningTitle }}</h2>
                    <p>{{ text.overlayWarningDescription }}</p>
                    <div class="modal-actions">
                        <button type="button" class="secondary" @click="overlayWarningOpen = false">
                            {{ text.overlayWarningReturn }}
                        </button>
                        <button type="button" class="primary" @click="continueOverlaySubmission">
                            {{ text.overlayWarningContinue }}
                        </button>
                    </div>
                </section>
            </div>
        </Transition>
        <Transition name="submit-modal" appear>
            <div v-if="modalOpen" class="submit-modal-backdrop" role="presentation" @mousedown.self="modalOpen = false">
                <section class="submit-modal" role="dialog" aria-modal="true"
                    :aria-labelledby="`submit-modal-title-${locale}`">
                    <h2 :id="`submit-modal-title-${locale}`">{{ text.confirmTitle }}</h2>
                    <p>{{ pendingPayload ? text.confirmDescription : text.targetConfirmDescription }}</p>
                    <textarea v-if="pendingPayload" :value="pendingPayload" readonly aria-label="ANIP_RESOURCE"
                        @focus="($event.target as HTMLTextAreaElement).select()" />
                    <div class="modal-actions">
                        <button type="button" class="secondary" @click="modalOpen = false">{{ text.cancel }}</button>
                        <button type="button" class="primary" @click="confirmSubmission">
                            {{ pendingPayload ? text.confirm : text.continue }}
                        </button>
                    </div>
                </section>
            </div>
        </Transition>
    </Teleport>
</template>
<style lang="scss">
.submit-modal-backdrop {
    position: fixed;
    z-index: 2000;
    inset: 0;
    display: grid;
    padding: 20px;
    place-items: center;
    background: rgba(0, 0, 0, 0.62);
}

.submit-modal-enter-active,
.submit-modal-leave-active {
    transition: opacity 0.2s ease;

    .submit-modal {
        transition: opacity 0.2s ease, transform 0.2s ease;
    }
}

.submit-modal-enter-from,
.submit-modal-leave-to {
    opacity: 0;

    .submit-modal {
        opacity: 0;
        transform: translateY(8px) scale(0.98);
    }
}

.submit-modal {
    width: min(600px, 100%);
    padding: 24px;
    border: 1px solid var(--vp-c-divider);
    border-radius: 8px;
    background: var(--vp-c-bg-elv);
    box-shadow: var(--vp-shadow-5);

    h2 {
        margin: 0;
        border: 0;
        font-size: 22px;
    }

    p {
        margin: 14px 0;
        color: var(--vp-c-text-2);
        line-height: 1.65;
    }

    textarea {
        width: 100%;
        height: 112px;
        resize: vertical;
        padding: 10px;
        border: 1px solid var(--vp-c-divider);
        border-radius: 6px;
        background: var(--vp-c-bg-soft);
        color: var(--vp-c-text-2);
        font: 12px/1.5 var(--vp-font-family-mono);
        word-break: break-all;
    }
}

.modal-actions {
    display: flex;
    justify-content: flex-end;
    gap: 10px;
    margin-top: 18px;

    button {
        min-height: 38px;
        padding: 0 15px;
        border: 1px solid var(--vp-c-divider);
        border-radius: 6px;
        font: inherit;
        cursor: pointer;
        transition: border-color 0.15s ease, background-color 0.15s ease, color 0.15s ease;

        &:hover {
            border-color: var(--vp-c-brand-1);
            background: var(--vp-c-brand-soft);
            color: var(--vp-c-brand-1);
        }

        &:focus-visible {
            outline: 2px solid var(--vp-c-brand-1);
            outline-offset: 2px;
        }
    }

    .secondary {
        background: var(--vp-c-bg-soft);
        color: var(--vp-c-text-1);
    }

    .primary {
        border-color: var(--vp-c-brand-1);
        background: var(--vp-c-brand-1);
        color: var(--vp-c-white);

        &:hover {
            border-color: var(--vp-c-brand-2);
            background: var(--vp-c-brand-2);
            color: var(--vp-c-white);
        }
    }
}
</style>