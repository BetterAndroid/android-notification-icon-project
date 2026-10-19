<script setup lang="ts">
import { computed, defineAsyncComponent, inject } from 'vue';
import { useData } from 'vitepress';
import {
    CloseOutlined,
    DeleteOutlined,
    GlobalOutlined,
    PlusOutlined,
    RollbackOutlined
} from '@ant-design/icons-vue';
import {
    AutoComplete,
    Checkbox,
    ConfigProvider,
    Form,
    FormItem,
    Input,
    Select,
    Textarea,
    theme
} from 'ant-design-vue';
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
    changeTarget,
    color,
    colorControl,
    colorMenuOpen,
    contributorSuggestions,
    contributors,
    duplicateContributorIndexes,
    editingEntry,
    fieldErrors,
    format,
    filteredPackages,
    filteredTargets,
    handlePickerColorChange,
    isLocaleSelected,
    label,
    labelRows,
    localizedLabel,
    localizedResourceLabel,
    lockedContributorCount,
    maxRemarkLength,
    normalizeColor,
    normalizePickerFormatLabel,
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
    selectedFormat,
    selectedTarget,
    skipLocalizedLabelVisible,
    submitDisabled,
    targetQuery,
    text
} = context;

const ColorPicker = defineAsyncComponent(() =>
    import('vue3-colorpicker').then(({ ColorPicker: component }) => component)
);

const { isDark } = useData();
// Ant Design computes its colors in JavaScript, so the VitePress palette is passed as tokens.
const antTheme = computed(() => ({
    algorithm: isDark.value ? theme.darkAlgorithm : theme.defaultAlgorithm,
    token: {
        borderRadius: 6,
        // Matches the 16px text and 42px fields of the VitePress document body.
        controlHeight: 42,
        // The checkbox size is derived from controlHeight and would grow with it, so the original 17px is kept.
        controlInteractiveSize: 17,
        fontSize: 16,
        colorPrimary: isDark.value ? '#91A6D2' : '#596A8D',
        fontFamily: 'var(--vp-font-family-base)',
        ...(isDark.value ? { colorBgContainer: '#1B1B1F', colorBgElevated: '#202127' } : {})
    },
    components: {
        // Selected options use the hover fill (colorFillTertiary of each algorithm) instead of the primary tint.
        Select: { controlItemBgActive: isDark.value ? 'rgba(255, 255, 255, 0.08)' : 'rgba(0, 0, 0, 0.04)' }
    }
}));
const errorStatus = (message: string | undefined) => message ? 'error' : '';
const categoryOptions = computed(() => categorySpecs.map((categorySpec) => ({
    label: text.value.categoryNames[categorySpec.id],
    value: categorySpec.id
})));
const resourceOption = (entry: { key: string; effectiveRule: { label?: unknown } }) => ({
    description: localizedResourceLabel(entry.effectiveRule.label),
    value: entry.key
});
const packageOptions = computed(() =>
    resourcesLoading.value || resourcesError.value ? [] : filteredPackages.value.map(resourceOption)
);
const targetOptions = computed(() => filteredTargets.value.map(resourceOption));
const formatOptions = [{ label: 'SVG', value: 'svg' }, { label: 'PNG', value: 'png' }];
const localeOptions = (rowId: number) => commonLocaleOptions.map((option) => ({
    disabled: isLocaleSelected(option.code, rowId),
    label: option.name,
    value: option.code
}));
const contributorOptions = (index: number) => contributorSuggestions(index).map((value: string) => ({ value }));
</script>
<template>
    <ConfigProvider :theme="antTheme">
        <Form class="submit-form" layout="vertical" @submit="prepareSubmission">
            <SubmitIconChecker />

            <div class="form-grid">
                <FormItem :label="text.category" html-for="submit-category" required>
                    <Select id="submit-category" v-model:value="category" :options="categoryOptions"
                        :disabled="Boolean(editingEntry)" />
                </FormItem>

                <FormItem :label="text.packageName" html-for="submit-package" required
                    :validate-status="errorStatus(fieldErrors.packageName)" :help="fieldErrors.packageName">
                    <!-- Clearing reports an undefined value, while the package name is always a string. -->
                    <AutoComplete id="submit-package" :value="packageName" :options="packageOptions" allow-clear
                        :filter-option="false" :placeholder="text.packagePlaceholder" popup-class-name="submit-popup"
                        @update:value="packageName = $event ?? ''">
                        <template #option="{ value, description }">
                            <div class="resource-option">
                                <strong>{{ value }}</strong>
                                <span>{{ description }}</span>
                            </div>
                        </template>
                    </AutoComplete>
                </FormItem>

                <FormItem class="target-field" :label="text.target" html-for="submit-target"
                    :extra="text.targetDescription" :validate-status="errorStatus(fieldErrors.target)"
                    :help="fieldErrors.target">
                    <Select id="submit-target" :value="selectedTarget?.key" show-search allow-clear
                        :filter-option="false" :options="targetOptions" :search-value="targetQuery"
                        :placeholder="resourcesError || (resourcesLoading ? text.iconValidating : text.targetPlaceholder)"
                        :not-found-content="text.targetEmpty" :loading="resourcesLoading"
                        :disabled="resourcesLoading || Boolean(resourcesError) || Boolean(editingEntry)"
                        popup-class-name="submit-popup" @search="targetQuery = $event" @change="changeTarget"
                        @dropdown-visible-change="targetQuery = ''">
                        <template #option="{ value, description }">
                            <div class="resource-option">
                                <strong>{{ value }}</strong>
                                <span>{{ description }}</span>
                            </div>
                        </template>
                    </Select>
                </FormItem>

                <!-- The label holds the mode buttons, and a label linked to an input passes hover and clicks on them
                     to that input, while a label without a target clicks the first button. Pointing it at its own text
                     links it to nothing. -->
                <FormItem class="full-width label-field" html-for="submit-label-title" required
                    :validate-status="errorStatus(fieldErrors.label)" :help="fieldErrors.label">
                    <template #label>
                        <span class="label-row">
                            <span id="submit-label-title">{{ text.label }}</span>
                            <button v-if="!localizedLabel" type="button" class="compact-icon-button"
                                :title="text.addLocale" :aria-label="text.addLocale" @click="activateLocalizedLabel">
                                <GlobalOutlined aria-hidden="true" />
                            </button>
                            <span v-else class="label-actions">
                                <!-- A modification follows the current resource, so the link is not offered there. -->
                                <button v-if="skipLocalizedLabelVisible && !editingEntry" type="button"
                                    class="link-button" @click="resetLocalizedLabel">
                                    {{ text.skipLocalizedLabel }}
                                </button>
                                <button type="button" class="compact-icon-button" :title="text.resetLabel"
                                    :aria-label="text.resetLabel" @click="resetLocalizedLabel">
                                    <RollbackOutlined aria-hidden="true" />
                                </button>
                            </span>
                        </span>
                    </template>
                    <Input v-if="!localizedLabel" id="submit-label" v-model:value="label" :aria-label="text.label"
                        :placeholder="text.labelPlaceholder" />
                    <div v-else id="submit-label-i18n" class="repeatable-list">
                        <div v-for="row in labelRows" :key="row.id" class="i18n-row">
                            <Select v-model:value="row.code" :options="localeOptions(row.id)"
                                :placeholder="text.selectLocale" :aria-label="text.i18nCode"
                                popup-class-name="submit-popup" />
                            <Input v-model:value="row.value" :aria-label="text.i18nValue"
                                :placeholder="text.i18nValue" />
                            <button type="button" :title="text.removeLocale" :aria-label="text.removeLocale"
                                :disabled="labelRows.length === 1 && !row.value.trim()" @click="removeLabelRow(row.id)">
                                <DeleteOutlined aria-hidden="true" />
                            </button>
                        </div>
                        <button type="button" class="add-row-button" :title="text.addLocale"
                            :aria-label="text.addLocale" @click="addLabelRow">
                            <PlusOutlined aria-hidden="true" />
                        </button>
                    </div>
                </FormItem>

                <FormItem :label="text.color" html-for="submit-color" :validate-status="errorStatus(fieldErrors.color)"
                    :help="fieldErrors.color">
                    <div ref="colorControl" class="color-control">
                        <Input id="submit-color" v-model:value="color" autocomplete="off"
                            :placeholder="text.colorPlaceholder" @blur="normalizeColor">
                            <template #suffix>
                                <button type="button" class="color-preview"
                                    :style="{ backgroundColor: color || 'transparent' }" :aria-label="text.color"
                                    :title="text.color" @click.stop="colorMenuOpen = !colorMenuOpen" />
                            </template>
                        </Input>
                        <div v-if="colorMenuOpen" class="color-menu" @click="normalizePickerFormatLabel">
                            <ClientOnly>
                                <ColorPicker v-model:pureColor="pickerColor" :lang="pickerLanguage" picker-type="chrome"
                                    format="hex6" :disable-alpha="true" :disable-history="true" :is-widget="true"
                                    @pure-color-change="handlePickerColorChange" />
                            </ClientOnly>
                        </div>
                    </div>
                </FormItem>

                <FormItem :label="text.format" html-for="submit-format" required
                    :validate-status="errorStatus(fieldErrors.format)" :help="fieldErrors.format">
                    <Select id="submit-format" v-model:value="format" :options="formatOptions"
                        :disabled="Boolean(selectedTarget || selectedFormat)" />
                </FormItem>

                <FormItem class="full-width overlay-field" :extra="text.overlayDescription">
                    <Checkbox v-model:checked="overlay"><strong>{{ text.overlay }}</strong></Checkbox>
                </FormItem>

                <FormItem class="full-width" :label="text.contributors" html-for="submit-contributor-0" required>
                    <div class="repeatable-list">
                        <!-- The shared context is untyped, so the list type is given here to keep the index a number. -->
                        <div v-for="(_, index) in (contributors as string[])" :key="index" class="contributor-row">
                            <FormItem class="contributor-item"
                                :validate-status="duplicateContributorIndexes.includes(index) ? 'error' : ''"
                                :help="duplicateContributorIndexes.includes(index) ? fieldErrors.contributors : undefined">
                                <AutoComplete :id="`submit-contributor-${index}`" v-model:value="contributors[index]"
                                    :options="contributorOptions(index)" :filter-option="false"
                                    :disabled="index < lockedContributorCount" popup-class-name="submit-popup">
                                    <template #option="{ value }">
                                        <div class="contributor-option">
                                            <span>{{ value }}</span>
                                            <button type="button" class="contributor-remove"
                                                :title="text.removeSavedContributor"
                                                :aria-label="text.removeSavedContributor" @mousedown.prevent.stop
                                                @click.stop="removeSavedContributor(value)">
                                                <CloseOutlined aria-hidden="true" />
                                            </button>
                                        </div>
                                    </template>
                                </AutoComplete>
                            </FormItem>
                            <button type="button" class="contributor-remove-button" :title="text.removeContributor"
                                :aria-label="text.removeContributor"
                                :disabled="index < lockedContributorCount || (contributors.length === 1 && !contributors[index].trim())"
                                @click="removeContributor(index)">
                                <DeleteOutlined aria-hidden="true" />
                            </button>
                        </div>
                        <button type="button" class="add-row-button" :title="text.addContributor"
                            :aria-label="text.addContributor" @click="addContributor">
                            <PlusOutlined aria-hidden="true" />
                        </button>
                    </div>
                </FormItem>

                <FormItem class="full-width" :label="text.remark" html-for="submit-remark"
                    :required="Boolean(editingEntry)">
                    <Textarea id="submit-remark" v-model:value="remark" :rows="5" :maxlength="maxRemarkLength"
                        :placeholder="editingEntry ? text.remarkRequiredPlaceholder : text.remarkPlaceholder" />
                </FormItem>
            </div>

            <button type="submit" class="submit-button" :disabled="submitDisabled">{{ text.createIssue }}</button>
        </Form>
    </ConfigProvider>
</template>
<style scoped lang="scss">
.submit-form {
    margin-top: 28px;
    // The form is a control panel, so only the fields themselves take a text selection.
    user-select: none;

    // Fields inherit the setting above, so their text selection is turned back on.
    :deep(input),
    :deep(textarea) {
        user-select: text;
    }
}

.form-grid {
    display: grid;
    grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
    gap: 22px 28px;
    padding: 28px 0;

    :deep(.ant-form-item) {
        min-width: 0;
        margin-bottom: 0;
    }

    :deep(.ant-form-item-label > label) {
        font-weight: 600;
    }

    // Descriptions and error messages keep the size of the former <small> hints.
    :deep(.ant-form-item-extra),
    :deep(.ant-form-item-explain) {
        font-size: smaller;
        line-height: 1.55;
    }

    // The former descriptions sat 5px below their fields.
    :deep(.ant-form-item-extra) {
        margin-top: 5px;
        color: var(--vp-c-text-3);
    }

    // Ant Design leaves the search input of Select and AutoComplete at the browser default input size.
    :deep(.ant-select-selection-search-input) {
        font-size: inherit;
    }
}

.full-width {
    grid-column: 1 / -1;
}

.label-field :deep(.ant-form-item-label > label) {
    width: 100%;
}

.label-row {
    display: flex;
    flex: 1;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
}

.label-actions {
    display: flex;
    align-items: center;
    gap: 12px;
}

.link-button {
    border: 0;
    padding: 0;
    background: none;
    color: var(--vp-c-brand-1);
    font-size: smaller;
    font-weight: 500;
    cursor: pointer;
    transition: color 0.25s;
}

.link-button:hover {
    color: var(--vp-c-brand-2);
    text-decoration: underline;
}

.target-field :deep(.ant-form-item-extra) {
    width: calc(200% + 28px);
}

// The checkbox row only needs its own height, not the height reserved for text fields.
.overlay-field :deep(.ant-form-item-control-input) {
    min-height: 0;
}

.resource-option {
    display: grid;
    gap: 2px;

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
}

.repeatable-list {
    display: grid;
    gap: 9px;
}

.i18n-row {
    display: grid;
    grid-template-columns: minmax(96px, 0.3fr) minmax(0, 1fr) auto;
    align-items: center;
    gap: 9px;
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

.add-row-button {
    justify-self: start;
}

.contributor-row {
    display: flex;
    align-items: flex-start;
    gap: 9px;

    .contributor-item {
        flex: 1;
        min-width: 0;
    }

    // Rows grow when an error message shows, so the button stays centered on the input instead.
    .contributor-remove-button {
        margin-top: 7px;
    }
}

.contributor-option {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 4px;

    span {
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
    }
}

.contributor-remove {
    display: grid;
    width: 24px;
    height: 24px;
    flex: 0 0 24px;
    place-items: center;
    border: 0;
    border-radius: 5px;
    background: transparent;
    color: var(--vp-c-text-3);
    cursor: pointer;

    &:hover {
        background: var(--vp-c-brand-soft);
        color: var(--vp-c-brand-1);
    }
}

.color-control {
    position: relative;

    // The 28px swatch is taller than the text line, so the padding shrinks to keep the 42px field height.
    :deep(.ant-input-affix-wrapper) {
        padding-block: 6px;
        // The former swatch sat 7px from the right edge of the field, including its 1px border.
        padding-inline-end: 6px;
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
    position: absolute;
    z-index: 30;
    top: calc(100% + 6px);
    right: 0;
    width: min(292px, calc(100vw - 32px));
    border: 1px solid var(--vp-c-divider);
    border-radius: 8px;
    background: var(--vp-c-bg-elv);
    box-shadow: var(--vp-shadow-3);

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

    &:hover:not(:disabled) {
        background: var(--vp-c-brand-2);
    }

    &:disabled {
        opacity: 0.4;
        cursor: not-allowed;
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

    .target-field :deep(.ant-form-item-extra) {
        width: 100%;
    }

    .i18n-row {
        grid-template-columns: minmax(78px, 0.34fr) minmax(0, 1fr) auto;
    }

    .submit-button {
        width: 100%;
    }
}
</style>