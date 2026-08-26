<script setup lang="ts">
import { defineAsyncComponent, inject } from 'vue';
import {
    CloseOutlined,
    DeleteOutlined,
    GlobalOutlined,
    PlusOutlined,
    RollbackOutlined
} from '@ant-design/icons-vue';
import 'vue3-colorpicker/style.css';
import { commonLocaleOptions } from '../../configs/i18n';
import { categorySpecs } from '../../data/icon-resources';
import SubmitIconChecker from './SubmitIconChecker.vue';
import { submitAdaptionContextKey } from './utils/context';

const context = inject(submitAdaptionContextKey);
if (!context) throw new Error('SubmitAdaption context is unavailable');

const {
    activateLocalizedLabel,
    addContributor,
    addLabelRow,
    category,
    closeContributorMenuSoon,
    closeTargetMenuSoon,
    color,
    colorControl,
    colorMenuOpen,
    contributorMenuIndex,
    contributorSuggestions,
    contributors,
    format,
    filteredTargets,
    handlePickerColorChange,
    handleTargetInput,
    isLocaleSelected,
    label,
    labelRows,
    localizedLabel,
    localizedResourceLabel,
    maxRemarkLength,
    normalizeColor,
    normalizePickerFormatLabel,
    openContributorMenu,
    overlay,
    packageName,
    pickerColor,
    pickerLanguage,
    prepareSubmission,
    remark,
    removeContributor,
    removeLabelRow,
    removeSavedContributor,
    resetLocalizedLabel,
    resourcesError,
    resourcesLoading,
    selectContributor,
    selectTarget,
    selectedFormat,
    selectedTarget,
    targetMenuOpen,
    targetQuery,
    text
} = context;

const ColorPicker = defineAsyncComponent(() =>
    import('vue3-colorpicker').then(({ ColorPicker: component }) => component)
);
</script>
<template>
    <form class="submit-form" novalidate @submit.prevent="prepareSubmission">
        <SubmitIconChecker />

        <div class="form-grid">
            <label class="field">
                <span class="field-label"><b>*</b>{{ text.category }}</span>
                <div class="select-control">
                    <select id="submit-category" v-model="category">
                        <option v-for="categorySpec in categorySpecs" :key="categorySpec.id" :value="categorySpec.id">
                            {{ text.categoryNames[categorySpec.id] }}
                        </option>
                    </select>
                    <span class="vpi-chevron-down select-chevron" aria-hidden="true" />
                </div>
            </label>

            <label class="field">
                <span class="field-label"><b>*</b>{{ text.packageName }}</span>
                <input id="submit-package" v-model="packageName" type="text" autocomplete="off"
                    :placeholder="text.packagePlaceholder">
            </label>

            <div class="field target-field">
                <div class="target-input-control">
                    <label for="submit-target" class="field-label">{{ text.target }}</label>
                    <input id="submit-target" v-model="targetQuery" type="search" autocomplete="off"
                        :placeholder="resourcesError || (resourcesLoading ? text.iconValidating : text.targetPlaceholder)"
                        :disabled="resourcesLoading || Boolean(resourcesError)" @input="handleTargetInput"
                        @focus="targetMenuOpen = true" @blur="closeTargetMenuSoon">
                    <div v-if="targetMenuOpen && !resourcesLoading && !resourcesError" class="target-menu"
                        role="listbox">
                        <button v-for="entry in filteredTargets" :key="entry.key" type="button" role="option"
                            :aria-selected="selectedTarget?.key === entry.key" @mousedown.prevent
                            @click="selectTarget(entry)">
                            <strong>{{ entry.key }}</strong>
                            <span>{{ localizedResourceLabel(entry.effectiveRule.label) }}</span>
                        </button>
                        <p v-if="!filteredTargets.length">{{ text.targetEmpty }}</p>
                    </div>
                </div>
                <small class="field-description">{{ text.targetDescription }}</small>
            </div>

            <div class="field full-width label-field">
                <div class="field-label-row">
                    <label :for="localizedLabel ? 'submit-label-i18n' : 'submit-label'" class="field-label">
                        <b>*</b>{{ text.label }}
                    </label>
                    <button v-if="!localizedLabel" type="button" class="compact-icon-button" :title="text.addLocale"
                        :aria-label="text.addLocale" @click="activateLocalizedLabel">
                        <GlobalOutlined aria-hidden="true" />
                    </button>
                    <button v-else type="button" class="compact-icon-button" :title="text.resetLabel"
                        :aria-label="text.resetLabel" @click="resetLocalizedLabel">
                        <RollbackOutlined aria-hidden="true" />
                    </button>
                </div>
                <input v-if="!localizedLabel" id="submit-label" v-model="label" type="text"
                    :placeholder="text.labelPlaceholder">
                <div v-else id="submit-label-i18n" class="repeatable-list" tabindex="-1">
                    <div v-for="row in labelRows" :key="row.id" class="i18n-row">
                        <div class="select-control">
                            <select v-model="row.code" :aria-label="text.i18nCode">
                                <option value="" disabled>{{ text.selectLocale }}</option>
                                <option v-for="option in commonLocaleOptions" :key="option.code" :value="option.code"
                                    :disabled="isLocaleSelected(option.code, row.id)">
                                    {{ option.name }}
                                </option>
                            </select>
                            <span class="vpi-chevron-down select-chevron" aria-hidden="true" />
                        </div>
                        <input v-model="row.value" type="text" :aria-label="text.i18nValue"
                            :placeholder="text.i18nValue">
                        <button type="button" :title="text.removeLocale" :aria-label="text.removeLocale"
                            :disabled="labelRows.length === 1 && !row.value.trim()" @click="removeLabelRow(row.id)">
                            <DeleteOutlined aria-hidden="true" />
                        </button>
                    </div>
                    <button type="button" class="add-row-button" :title="text.addLocale" :aria-label="text.addLocale"
                        @click="addLabelRow">
                        <PlusOutlined aria-hidden="true" />
                    </button>
                </div>
            </div>

            <div ref="colorControl" class="field color-field">
                <label for="submit-color" class="field-label">{{ text.color }}</label>
                <div class="color-control">
                    <input id="submit-color" v-model="color" type="text" autocomplete="off"
                        :placeholder="text.colorPlaceholder" @blur="normalizeColor">
                    <button type="button" class="color-preview" :style="{ backgroundColor: color || 'transparent' }"
                        :aria-label="text.color" :title="text.color" @click.stop="colorMenuOpen = !colorMenuOpen" />
                </div>
                <div v-if="colorMenuOpen" class="color-menu" @click="normalizePickerFormatLabel">
                    <ClientOnly>
                        <ColorPicker v-model:pureColor="pickerColor" :lang="pickerLanguage" picker-type="chrome"
                            format="hex6" :disable-alpha="true" :disable-history="true" :is-widget="true"
                            @pure-color-change="handlePickerColorChange" />
                    </ClientOnly>
                </div>
            </div>

            <label class="field">
                <span class="field-label"><b>*</b>{{ text.format }}</span>
                <div class="select-control">
                    <select id="submit-format" v-model="format" :disabled="Boolean(selectedTarget || selectedFormat)">
                        <option value="svg">SVG</option>
                        <option value="png">PNG</option>
                    </select>
                    <span class="vpi-chevron-down select-chevron" aria-hidden="true" />
                </div>
            </label>

            <div class="field full-width overlay-field">
                <label class="checkbox-row">
                    <input v-model="overlay" type="checkbox">
                    <strong>{{ text.overlay }}</strong>
                </label>
                <small class="field-description">{{ text.overlayDescription }}</small>
            </div>

            <div class="field full-width">
                <div class="field-label-row">
                    <label for="submit-contributor-0" class="field-label"><b>*</b>{{ text.contributors }}</label>
                </div>
                <div class="repeatable-list">
                    <div v-for="(_, index) in contributors" :key="index" class="contributor-row">
                        <input :id="`submit-contributor-${index}`" v-model="contributors[index]" type="text"
                            autocomplete="off" @focus="openContributorMenu(index)" @input="openContributorMenu(index)"
                            @blur="closeContributorMenuSoon" @keydown.escape="contributorMenuIndex = undefined">
                        <button type="button" :title="text.removeContributor" :aria-label="text.removeContributor"
                            :disabled="contributors.length === 1 && !contributors[index].trim()"
                            @click="removeContributor(index)">
                            <DeleteOutlined aria-hidden="true" />
                        </button>
                        <div v-if="contributorMenuIndex === index && contributorSuggestions.length"
                            class="contributor-menu" role="listbox">
                            <div v-for="candidate in contributorSuggestions" :key="candidate"
                                class="contributor-option">
                                <button type="button" class="contributor-value" role="option" @mousedown.prevent
                                    @click="selectContributor(index, candidate)">
                                    {{ candidate }}
                                </button>
                                <button type="button" class="contributor-remove" :title="text.removeSavedContributor"
                                    :aria-label="text.removeSavedContributor" @mousedown.prevent
                                    @click="removeSavedContributor(candidate)">
                                    <CloseOutlined aria-hidden="true" />
                                </button>
                            </div>
                        </div>
                    </div>
                </div>
                <button type="button" class="add-row-button" :title="text.addContributor"
                    :aria-label="text.addContributor" @click="addContributor">
                    <PlusOutlined aria-hidden="true" />
                </button>
            </div>

            <label class="field full-width">
                <span class="field-label">{{ text.remark }}</span>
                <textarea id="submit-remark" v-model="remark" rows="5" :maxlength="maxRemarkLength"
                    :placeholder="text.remarkPlaceholder" />
            </label>
        </div>

        <button type="submit" class="submit-button">{{ text.createIssue }}</button>
    </form>

</template>
<style scoped lang="scss">
.submit-form {
    margin-top: 28px;
}

.form-grid {
    display: grid;
    grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
    gap: 22px 28px;
    padding: 28px 0;
}

.field {
    position: relative;
    display: flex;
    min-width: 0;
    flex-direction: column;
    gap: 8px;

    input[type='text'],
    input[type='search'],
    select,
    textarea {
        width: 100%;
        border: 1px solid var(--vp-c-divider);
        border-radius: 6px;
        outline: 0;
        background: var(--vp-c-bg);
        color: var(--vp-c-text-1);
        font: inherit;

        &:focus {
            border-color: var(--vp-c-brand-1);
            box-shadow: 0 0 0 2px var(--vp-c-brand-soft);
        }

        &:disabled {
            background: var(--vp-c-bg-soft);
            color: var(--vp-c-text-3);
            cursor: not-allowed;
        }
    }

    input[type='text'],
    input[type='search'],
    select {
        height: 42px;
        padding: 0 12px;
    }

    textarea {
        height: 115px;
        min-height: 115px;
        padding: 10px 12px;
        line-height: 1.55;
        resize: vertical;
    }
}

.full-width {
    grid-column: 1 / -1;
}

.select-control {
    position: relative;

    select {
        padding-right: 40px;
    }

    .select-chevron {
        position: absolute;
        top: 50%;
        right: 13px;
        width: 16px;
        height: 16px;
        color: var(--vp-c-text-2);
        pointer-events: none;
        transform: translateY(-50%) rotate(90deg);
    }

    select:disabled+.select-chevron {
        color: var(--vp-c-text-3);
    }
}

.field-label,
.field-label-row {
    min-height: 24px;
}

.field-label {
    color: var(--vp-c-text-1);
    font-weight: 600;

    b {
        margin-right: 3px;
        color: var(--vp-c-danger-1);
    }
}

.field-label-row {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
}

.compact-icon-button,
.add-row-button,
.i18n-row button,
.contributor-row>button {
    display: grid;
    width: 28px;
    height: 28px;
    flex: 0 0 28px;
    place-items: center;
    border: 1px solid var(--vp-c-divider);
    border-radius: 6px;
    background: var(--vp-c-bg-soft);
    color: var(--vp-c-text-2);
    font: inherit;
    cursor: pointer;

    &:hover:not(:disabled) {
        border-color: var(--vp-c-brand-1);
        color: var(--vp-c-brand-1);
    }

    &:disabled {
        opacity: 0.4;
        cursor: not-allowed;
    }
}

.color-control {
    position: relative;
    display: flex;
    align-items: center;

    input {
        padding-right: 46px !important;
    }

    >button {
        position: absolute;
        right: 7px;
    }
}

#submit-target::-webkit-search-cancel-button {
    width: 12px;
    height: 12px;
    margin: 0;
    -webkit-appearance: none;
    appearance: none;
    background:
        linear-gradient(45deg, transparent 44%, var(--vp-c-text-3) 44% 56%, transparent 56%),
        linear-gradient(-45deg, transparent 44%, var(--vp-c-text-3) 44% 56%, transparent 56%);
    cursor: pointer;
}

.target-menu,
.color-menu {
    position: absolute;
    z-index: 30;
    top: calc(100% + 8px);
    right: 0;
    left: 0;
    padding: 7px;
    border: 1px solid var(--vp-c-divider);
    border-radius: 8px;
    background: var(--vp-c-bg-elv);
    box-shadow: var(--vp-shadow-3);
}

.target-input-control {
    position: relative;
    display: flex;
    flex-direction: column;
    gap: 5px;
}

.target-menu {
    max-height: 320px;
    overflow: auto;

    >button {
        display: grid;
        width: 100%;
        gap: 2px;
        padding: 8px 10px;
        border: 0;
        border-radius: 5px;
        background: transparent;
        color: var(--vp-c-text-1);
        font: inherit;
        text-align: left;
        cursor: pointer;

        strong,
        span {
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
        }

        span {
            color: var(--vp-c-text-3);
            font-size: 12px;
        }

        &:hover,
        &[aria-selected='true'] {
            background: var(--vp-c-brand-soft);
        }
    }

    p {
        margin: 8px 10px;
        color: var(--vp-c-text-3);
    }
}

.repeatable-list {
    display: grid;
    gap: 9px;
}

.i18n-row {
    display: grid;
    grid-template-columns: minmax(96px, 0.3fr) minmax(0, 1fr) 28px;
    align-items: center;
    gap: 9px;
}

.add-row-button {
    justify-self: start;
}

.contributor-row {
    position: relative;
    display: flex;
    align-items: center;
    gap: 9px;
}

.contributor-menu {
    position: absolute;
    z-index: 30;
    top: calc(100% + 5px);
    right: 37px;
    left: 0;
    padding: 5px;
    border: 1px solid var(--vp-c-divider);
    border-radius: 8px;
    background: var(--vp-c-bg-elv);
    box-shadow: var(--vp-shadow-3);
}

.contributor-option {
    display: flex;
    align-items: center;
    gap: 4px;

    button {
        min-width: 0;
        border: 0;
        background: transparent;
        color: var(--vp-c-text-1);
        font: inherit;
        cursor: pointer;
    }

    .contributor-value {
        display: block;
        width: auto;
        height: auto;
        flex: 1 1 auto;
        overflow: hidden;
        padding: 7px 8px;
        border-radius: 5px;
        line-height: 1.45;
        text-align: left;
        text-overflow: ellipsis;
        white-space: nowrap;

        &:hover {
            background: var(--vp-c-brand-soft);
        }
    }

    .contributor-remove {
        display: grid;
        width: 28px;
        height: 28px;
        flex: 0 0 28px;
        place-items: center;
        padding: 0;
        border-radius: 5px;
        color: var(--vp-c-text-3);

        &:hover {
            background: var(--vp-c-brand-soft);
            color: var(--vp-c-brand-1);
        }
    }
}

.color-preview {
    width: 28px;
    height: 28px;
    border: 2px solid var(--vp-c-bg);
    border-radius: 5px;
    box-shadow: 0 0 0 1px var(--vp-c-divider);
    cursor: pointer;
}

.color-menu {
    top: calc(100% + 6px);
    left: auto;
    width: min(292px, calc(100vw - 32px));
    padding: 0;

    :deep(.vc-colorpicker) {
        width: 100%;
        border-radius: 7px;
        box-shadow: none;
    }

    :deep(.vc-colorpicker--container) {
        padding: 14px;
    }

    :deep(.vc-display .vc-current-color) {
        display: none;
    }

    :deep(.vc-display .vc-color-input:nth-child(4)) {
        display: none;
    }

}

.target-field,
.overlay-field {
    gap: 5px;
}

.field-description {
    display: block;
    color: var(--vp-c-text-3);
    line-height: 1.55;
}

.target-field .field-description {
    width: calc(200% + 28px);
}

.checkbox-row {
    display: flex;
    width: fit-content;
    align-items: center;
    align-self: flex-start;
    gap: 9px;

    input {
        width: 17px;
        height: 17px;
        accent-color: var(--vp-c-brand-1);
    }
}

.submit-button {
    min-height: 44px;
    padding: 0 20px;
    border: 1px solid var(--vp-c-brand-1);
    border-radius: 7px;
    background: var(--vp-c-brand-1);
    color: var(--vp-c-white);
    font: inherit;
    font-weight: 600;
    cursor: pointer;

    &:hover {
        background: var(--vp-c-brand-2);
    }
}

@media (max-width: 760px) {
    .form-grid {
        grid-template-columns: minmax(0, 1fr);
        gap: 20px;
    }

    .full-width {
        grid-column: auto;
    }

    .target-field .field-description {
        width: 100%;
    }

    .i18n-row {
        grid-template-columns: minmax(78px, 0.34fr) minmax(0, 1fr) 28px;
    }

    .submit-button {
        width: 100%;
    }
}
</style>