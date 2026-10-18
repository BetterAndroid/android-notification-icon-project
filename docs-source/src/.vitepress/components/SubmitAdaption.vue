<script setup lang="ts">
import { message as antMessage } from 'ant-design-vue';
import { useRoute } from 'vitepress';
import { computed, nextTick, onBeforeUnmount, onMounted, provide, ref, watch } from 'vue';
import { useComponentMessages } from '../theme/i18n';
import {
    canonicalizeLocaleCode,
    commonLocaleCodes,
    isLocalizedRouteSwitch
} from '../configs/i18n';
import { configs } from '../configs/template';
import SubmitFields from './submit-adaption/SubmitFields.vue';
import SubmitConfirmationModal from './submit-adaption/SubmitConfirmationModal.vue';
import { submitAdaptionContextKey } from './submit-adaption/utils/context';
import {
    categorySpecs,
    loadIconResources,
    resolveLabel,
    type LoadedCategory,
    type ManifestLabel,
    type ResolvedIconEntry
} from '../data/icon-resources';
import {
    resetSubmitAdaptionState,
    submitAdaptionState,
    type IconPayload,
    type IconValidation
} from './submit-adaption/utils/state';

const { locale, text } = useComponentMessages('submitAdaption');
const route = useRoute();
const mountedRoutePath = route.path;
const pngSignature = [137, 80, 78, 71, 13, 10, 26, 10];
const packageNamePattern = /^[A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z][A-Za-z0-9_]*)+$/;
// The existing Android framework resource is named android, which has no dot like an applicationId.
const legacyPackageNames = new Set(['android']);
const localeCodePattern = /^[A-Za-z]{2,8}(?:-[A-Za-z0-9]{1,8})*$/;
const colorPattern = /^#[0-9A-F]{6}$/;
const maxIconDimension = 150;
const maxResourceSize = 24 * 1024;
const maxRemarkLength = 300;
const contributorCookieName = 'anip-contributors';
const contributorCookieMaxAge = 60 * 60 * 24 * 365;

const categories = ref<LoadedCategory[]>([]);
const resourcesLoading = ref(true);
const resourcesError = ref('');
const {
    category,
    color,
    contributors,
    editingKey,
    format,
    iconValidation,
    label,
    labelRows,
    localizedLabel,
    nextLabelRowId,
    originalIcon,
    overlay,
    packageName,
    pickerColor,
    remark,
    selectedData,
    selectedFile,
    selectedFormat,
    selectedTarget,
    targetQuery
} = submitAdaptionState;
const colorMenuOpen = ref(false);
const colorControl = ref<HTMLElement>();
const savedContributors = ref<string[]>([]);
const fileInput = ref<HTMLInputElement>();
const iconDragActive = ref(false);
const iconValidating = ref(false);
const modalOpen = ref(false);
const overlayWarningOpen = ref(false);
const iconChanged = ref(true);
const pendingPayload = ref('');
const pendingIssueUrl = ref('');
let iconDragDepth = 0;

const selectedCategory = computed(() =>
    categories.value.find((candidate) => candidate.spec.id === category.value)
);
const packageNameExists = (value: string) =>
    selectedCategory.value?.entries.some((entry) => entry.key === value) ?? false;
const packageEntries = computed(() => (selectedCategory.value?.entries ?? [])
    .filter((entry) => !entry.errors.length && entry.iconUrls.length));
const targetEntries = computed(() => packageEntries.value.filter((entry) => !entry.rule.target));
const filterEntries = (entries: ResolvedIconEntry[], value: string) => {
    const query = value.trim().toLocaleLowerCase();
    if (!query) return entries.slice(0, 12);
    return entries.filter((entry) => [
        entry.key,
        ...labelSearchValues(entry.effectiveRule.label)
    ].some((candidate) => candidate.toLocaleLowerCase().includes(query))).slice(0, 12);
};
const filteredPackages = computed(() => filterEntries(packageEntries.value, packageName.value));
const filteredTargets = computed(() => filterEntries(targetEntries.value, targetQuery.value));
/** The adapted entry being modified, matched by the exact package name. */
const editingEntry = computed(() =>
    packageEntries.value.find((entry) => entry.key === packageName.value.trim())
);
const lockedContributorCount = computed(() =>
    editingEntry.value?.effectiveRule.contributors?.split(', ').length ?? 0
);
const dependentEntries = computed(() => {
    const entry = editingEntry.value;
    if (!entry || entry.rule.target) return [];
    return packageEntries.value.filter((candidate) => candidate.rule.target === entry.key);
});
const iconResetDisabled = computed(() =>
    Boolean(originalIcon.value) && selectedFile.value === originalIcon.value
);
const previewSources = computed(() => {
    if (selectedTarget.value) return selectedTarget.value.iconUrls;
    if (!selectedData.value || !selectedFormat.value) return [];
    const mimeType = selectedFormat.value === 'png' ? 'image/png' : 'image/svg+xml';
    return [`data:${mimeType};base64,${selectedData.value}`];
});
const previewSource = computed(() => previewSources.value[0]);
const previewColorized = computed(() => Boolean(selectedTarget.value || iconValidation.value?.grayscale));
const previewColorizableOnHover = computed(() => Boolean(
    !selectedTarget.value && iconValidation.value && !iconValidation.value.grayscale
));
const validationStatus = computed(() => {
    if (iconValidating.value) return { state: 'checking', label: text.value.iconValidating };
    if (selectedTarget.value) return { state: 'valid', label: text.value.iconReady };
    if (!iconValidation.value)
        return { state: 'empty', label: text.value.iconMissing };
    if (!iconValidation.value.valid)
        return { state: 'invalid', label: iconValidation.value.message };
    return iconValidation.value.message
        ? { state: 'warning', label: iconValidation.value.message }
        : { state: 'valid', label: text.value.iconReady };
});
const serializedLabel = computed(() => {
    if (!localizedLabel.value) return label.value.trim();
    return Object.fromEntries(labelRows.value.map((row) => [row.code.trim(), row.value.trim()]));
});
const serializedContributors = computed(() => contributors.value.map((value) => value.trim()).join(', '));
const contributorSuggestions = (index: number) => {
    const query = contributors.value[index]?.trim().toLocaleLowerCase() ?? '';
    return savedContributors.value.filter((value) =>
        !query || value.toLocaleLowerCase().includes(query)
    ).slice(0, 12);
};

const labelSearchValues = (value: ManifestLabel | undefined) =>
    typeof value === 'string' ? [value] : Object.values(value ?? {});
const localizedResourceLabel = (value: ManifestLabel | undefined) =>
    resolveLabel(value, locale.value);
const pickerLanguage = computed(() =>
    locale.value.toLocaleLowerCase().startsWith('zh') ? 'ZH-cn' : 'En'
);
const showToast = (message: string) => antMessage.error(message);
const openFilePicker = () => fileInput.value?.click();
const resetFileInput = () => {
    if (fileInput.value) fileInput.value.value = '';
};
const clearSelectedIcon = () => {
    selectedFile.value = undefined;
    selectedFormat.value = undefined;
    selectedData.value = '';
    iconValidation.value = undefined;
    resetFileInput();
};
const resetSelectedIcon = () => originalIcon.value
    ? processSelectedFile(originalIcon.value)
    : clearSelectedIcon();
const clearTarget = () => {
    selectedTarget.value = undefined;
    targetQuery.value = '';
};
const selectTarget = (entry: ResolvedIconEntry) => {
    selectedTarget.value = entry;
    targetQuery.value = '';
    clearSelectedIcon();
    const inheritedFormat = entry.effectiveRule.format?.toLocaleLowerCase();
    if (inheritedFormat === 'png' || inheritedFormat === 'svg') format.value = inheritedFormat;
};
const changeTarget = (key: unknown) => {
    const entry = targetEntries.value.find((candidate) => candidate.key === key);
    if (entry) selectTarget(entry);
    else clearTarget();
};
const activateLocalizedLabel = () => {
    localizedLabel.value = true;
    labelRows.value = [{
        code: 'en',
        id: nextLabelRowId.value++,
        value: label.value
    }];
};
const resetLocalizedLabel = () => {
    const preferredCode = canonicalizeLocaleCode(locale.value).toLocaleLowerCase();
    label.value = labelRows.value.find((row) => row.code.toLocaleLowerCase() === preferredCode)?.value ??
        labelRows.value.find((row) => row.value.trim())?.value ?? '';
    localizedLabel.value = false;
    labelRows.value = [];
};
const addLabelRow = () => labelRows.value.push({ code: '', id: nextLabelRowId.value++, value: '' });
const removeLabelRow = (id: number) => {
    if (labelRows.value.length > 1) {
        labelRows.value = labelRows.value.filter((row) => row.id !== id);
        return;
    }
    const row = labelRows.value.find((candidate) => candidate.id === id);
    if (row) row.value = '';
};
const isLocaleSelected = (code: string, rowId: number) => labelRows.value.some((row) =>
    row.id !== rowId && row.code.toLocaleLowerCase() === code.toLocaleLowerCase()
);
const addContributor = () => contributors.value.push('');
const removeContributor = (index: number) => {
    if (contributors.value.length > 1) contributors.value.splice(index, 1);
    else contributors.value[index] = '';
};
const readSavedContributors = () => {
    const cookie = document.cookie.split('; ').find((value) => value.startsWith(`${contributorCookieName}=`));
    if (!cookie) return [];
    try {
        const value = JSON.parse(decodeURIComponent(cookie.slice(contributorCookieName.length + 1)));
        if (!Array.isArray(value)) return [];
        return [...new Set(value.filter((item): item is string => typeof item === 'string')
            .map((item) => item.trim()).filter(Boolean))];
    } catch {
        return [];
    }
};
const writeSavedContributors = () => {
    document.cookie = `${contributorCookieName}=${encodeURIComponent(JSON.stringify(savedContributors.value))}; ` +
        `Max-Age=${contributorCookieMaxAge}; Path=/; SameSite=Lax`;
};
const rememberContributors = () => {
    const current = contributors.value.map((value) => value.trim()).filter(Boolean);
    savedContributors.value = [...new Set([...current, ...savedContributors.value])];
    writeSavedContributors();
};
const removeSavedContributor = (value: string) => {
    savedContributors.value = savedContributors.value.filter((candidate) => candidate !== value);
    writeSavedContributors();
};
const syncPickerColor = () => {
    const normalized = color.value.trim().toLocaleUpperCase();
    pickerColor.value = colorPattern.test(normalized) ? normalized : 'transparent';
};
const normalizeColor = () => {
    color.value = color.value.trim().toLocaleUpperCase();
    syncPickerColor();
};
const handlePickerColorChange = (value: string) => {
    const normalized = value.trim().toLocaleUpperCase();
    if (colorPattern.test(normalized)) color.value = normalized;
};
// vue3-colorpicker keeps the RGBA label after disableAlpha removes alpha controls.
const normalizePickerFormatLabel = () => nextTick(() => {
    const toggle = colorControl.value?.querySelector<HTMLElement>('.vc-input-toggle');
    if (toggle?.textContent?.trim().toLocaleLowerCase() === 'rgba') toggle.textContent = 'rgb';
});
const closeFloatingControls = (event: MouseEvent) => {
    if (!colorControl.value?.contains(event.target as Node)) colorMenuOpen.value = false;
};
const closeOnEscape = (event: KeyboardEvent) => {
    if (event.key !== 'Escape') return;
    colorMenuOpen.value = false;
    modalOpen.value = false;
    overlayWarningOpen.value = false;
};

watch(color, (value) => {
    const normalized = value.trim().toLocaleUpperCase();
    if (!normalized) {
        if (pickerColor.value !== 'transparent') pickerColor.value = 'transparent';
    } else if (colorPattern.test(normalized) && pickerColor.value !== normalized) {
        pickerColor.value = normalized;
    }
});
watch(pickerColor, (value) => handlePickerColorChange(String(value)));

const bytesToBase64 = (bytes: Uint8Array) => {
    let binary = '';
    const chunkSize = 0x8000;
    for (let index = 0; index < bytes.length; index += chunkSize)
        binary += String.fromCharCode(...bytes.subarray(index, index + chunkSize));
    return btoa(binary);
};
const textToBase64Url = (value: string) => bytesToBase64(new TextEncoder().encode(value))
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/, '');
const sha256 = async (bytes: Uint8Array<ArrayBuffer>) => Array.from(
    new Uint8Array(await crypto.subtle.digest('SHA-256', bytes))
).map((value) => value.toString(16).padStart(2, '0')).join('');

interface IconDimensions {
    height: number;
    width: number;
}

const parseSvgLength = (value: string | null) => {
    const match = value?.trim().match(/^(\d+(?:\.\d+)?)(?:px)?$/i);
    if (!match) return;
    const length = Number(match[1]);
    return length > 0 ? length : undefined;
};
const resolveSvgDimensions = (root: Element): IconDimensions => {
    const viewBox = root.getAttribute('viewBox')?.trim().split(/[\s,]+/).map(Number);
    const viewBoxWidth = viewBox?.length === 4 && Number.isFinite(viewBox[2]) && viewBox[2] > 0
        ? viewBox[2] : undefined;
    const viewBoxHeight = viewBox?.length === 4 && Number.isFinite(viewBox[3]) && viewBox[3] > 0
        ? viewBox[3] : undefined;
    let width = parseSvgLength(root.getAttribute('width'));
    let height = parseSvgLength(root.getAttribute('height'));
    if (width && !height && viewBoxWidth && viewBoxHeight) height = width * viewBoxHeight / viewBoxWidth;
    if (height && !width && viewBoxWidth && viewBoxHeight) width = height * viewBoxWidth / viewBoxHeight;
    width ??= viewBoxWidth;
    height ??= viewBoxHeight;
    if (!width || !height) throw new Error(text.value.errors.invalidSvg);
    return { height, width };
};

const validatePngHeader = (bytes: Uint8Array) => {
    if (bytes.length < 33 || !pngSignature.every((value, index) => bytes[index] === value))
        throw new Error(text.value.errors.invalidPng);
    const view = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
    const width = view.getUint32(16);
    const height = view.getUint32(20);
    const bitDepth = bytes[24];
    const colorType = bytes[25];
    const interlace = bytes[28];
    if (!width || !height) throw new Error(text.value.errors.invalidPng);
    if (bitDepth !== 8) throw new Error(text.value.errors.pngBitDepth);
    if (![0, 2, 3, 4, 6].includes(colorType)) throw new Error(text.value.errors.pngColorType);
    if (interlace !== 0) throw new Error(text.value.errors.interlacedPng);
    return { height, width };
};
const validateSvgSource = (source: string) => {
    const documentNode = new DOMParser().parseFromString(source, 'image/svg+xml');
    if (documentNode.querySelector('parsererror') || documentNode.documentElement.localName !== 'svg')
        throw new Error(text.value.errors.invalidSvg);
    if (documentNode.querySelector('script, foreignObject, iframe, object, embed, audio, video'))
        throw new Error(text.value.errors.svgActiveContent);
    for (const style of Array.from(documentNode.querySelectorAll('style'))) {
        const css = style.textContent ?? '';
        if (/@import\b|url\(\s*['"]?(?:https?:|data:|\/\/)/i.test(css))
            throw new Error(text.value.errors.svgExternalUrl);
    }
    for (const element of Array.from(documentNode.querySelectorAll('*'))) {
        for (const attribute of Array.from(element.attributes)) {
            const name = attribute.name.toLocaleLowerCase();
            const value = attribute.value.trim();
            if (name.startsWith('on')) throw new Error(text.value.errors.svgEvent);
            if ((name === 'href' || name.endsWith(':href')) && value && !value.startsWith('#'))
                throw new Error(text.value.errors.svgExternalReference);
            if (/url\(\s*['"]?(?:https?:|data:|\/\/)/i.test(value))
                throw new Error(text.value.errors.svgExternalUrl);
        }
    }
    return resolveSvgDimensions(documentNode.documentElement);
};
const validateRenderedIcon = (source: string, dimensions: IconDimensions) => new Promise<IconValidation>((resolve) => {
    const image = new Image();
    image.onload = () => {
        const width = image.naturalWidth;
        const height = image.naturalHeight;
        if (!width || !height) {
            resolve({ grayscale: false, message: text.value.errors.decode, valid: false });
            return;
        }
        const dimensionsExceeded = dimensions.width > maxIconDimension || dimensions.height > maxIconDimension;
        const samplingScale = Math.min(1, maxIconDimension / width, maxIconDimension / height);
        const canvas = document.createElement('canvas');
        canvas.width = Math.max(1, Math.round(width * samplingScale));
        canvas.height = Math.max(1, Math.round(height * samplingScale));
        const context = canvas.getContext('2d', { willReadFrequently: true });
        if (!context) {
            resolve({ grayscale: false, message: text.value.errors.canvasUnavailable, valid: false });
            return;
        }
        context.clearRect(0, 0, canvas.width, canvas.height);
        context.drawImage(image, 0, 0, canvas.width, canvas.height);
        const pixels = context.getImageData(0, 0, canvas.width, canvas.height).data;
        let visible = false;
        let transparent = false;
        let grayscale = true;
        for (let index = 0; index < pixels.length; index += 4) {
            const alpha = pixels[index + 3];
            if (alpha < 255) transparent = true;
            if (!alpha) continue;
            visible = true;
            if (pixels[index] !== pixels[index + 1] || pixels[index + 1] !== pixels[index + 2]) {
                grayscale = false;
                break;
            }
        }
        const colorizable = visible && grayscale;
        if (dimensionsExceeded)
            resolve({ grayscale: colorizable, message: text.value.errors.dimensions, valid: false });
        else if (!grayscale)
            resolve({ grayscale: false, message: text.value.errors.nonGrayscale, valid: false });
        else if (!visible)
            resolve({ grayscale: false, message: text.value.errors.noVisiblePixels, valid: false });
        else if (!transparent)
            resolve({ grayscale: true, message: text.value.errors.transparency, valid: false });
        else {
            const warnings = [];
            if (dimensions.width < 50 || dimensions.height < 50)
                warnings.push(text.value.warnings.tooSmall);
            if (dimensions.width !== dimensions.height)
                warnings.push(text.value.warnings.notSquare);
            resolve({ grayscale: true, message: warnings.join('\n'), valid: true });
        }
    };
    image.onerror = () => resolve({ grayscale: false, message: text.value.errors.decode, valid: false });
    image.src = source;
});

const processSelectedFile = async (file: File | undefined) => {
    if (!file) return;
    const extension = file.name.split('.').pop()?.toLocaleLowerCase();
    if (extension !== 'png' && extension !== 'svg') {
        resetFileInput();
        showToast(text.value.errors.unsupportedFormat);
        return;
    }
    if (file.size > maxResourceSize) {
        resetFileInput();
        showToast(text.value.errors.maxSize);
        return;
    }
    iconValidating.value = true;
    clearTarget();
    try {
        const bytes = new Uint8Array(await file.arrayBuffer());
        const dimensions = extension === 'png'
            ? validatePngHeader(bytes)
            : validateSvgSource(new TextDecoder().decode(bytes));
        const data = bytesToBase64(bytes);
        const mimeType = extension === 'png' ? 'image/png' : 'image/svg+xml';
        const result = await validateRenderedIcon(`data:${mimeType};base64,${data}`, dimensions);
        selectedFile.value = file;
        selectedFormat.value = extension;
        selectedData.value = data;
        format.value = extension;
        iconValidation.value = result;
    } catch (error) {
        resetFileInput();
        const message = error instanceof Error ? error.message : String(error);
        showToast(message);
    } finally {
        iconValidating.value = false;
    }
};
const handleFileSelection = (event: Event) =>
    processSelectedFile((event.target as HTMLInputElement).files?.[0]);
const resetIconDragState = () => {
    iconDragDepth = 0;
    iconDragActive.value = false;
};
const handleIconDragEnter = (event: DragEvent) => {
    if (selectedTarget.value) {
        if (event.dataTransfer) event.dataTransfer.dropEffect = 'none';
        return;
    }
    iconDragDepth++;
    iconDragActive.value = true;
};
const handleIconDragOver = (event: DragEvent) => {
    if (selectedTarget.value) {
        if (event.dataTransfer) event.dataTransfer.dropEffect = 'none';
        return;
    }
    if (event.dataTransfer) event.dataTransfer.dropEffect = 'copy';
    iconDragActive.value = true;
};
const handleIconDragLeave = () => {
    if (selectedTarget.value) {
        resetIconDragState();
        return;
    }
    iconDragDepth = Math.max(0, iconDragDepth - 1);
    if (!iconDragDepth) iconDragActive.value = false;
};
const handleFileDrop = (event: DragEvent) => {
    resetIconDragState();
    if (selectedTarget.value) return;
    return processSelectedFile(event.dataTransfer?.files[0]);
};

const fetchOriginalIcon = async (entry: ResolvedIconEntry) => {
    const extension = entry.effectiveRule.format!.toLocaleLowerCase();
    for (const url of entry.iconUrls) {
        try {
            const response = await fetch(url, {
                cache: import.meta.env.DEV ? 'no-store' : 'default',
                credentials: 'omit',
                mode: 'cors'
            });
            if (!response.ok) continue;
            return new File([await response.blob()], `${entry.key}.${extension}`, {
                type: extension === 'png' ? 'image/png' : 'image/svg+xml'
            });
        } catch {
            continue;
        }
    }
    showToast(text.value.errors.originalIcon);
};
const fillFromEntry = async (entry: ResolvedIconEntry, key: string) => {
    editingKey.value = key;
    originalIcon.value = undefined;
    const rule = entry.effectiveRule;
    if (rule.label && typeof rule.label === 'object') {
        localizedLabel.value = true;
        labelRows.value = Object.entries(rule.label).map(([code, value]) => ({
            code,
            id: nextLabelRowId.value++,
            value
        }));
        label.value = '';
    } else {
        localizedLabel.value = false;
        labelRows.value = [];
        label.value = rule.label ?? '';
    }
    color.value = rule.color?.toLocaleUpperCase() ?? '';
    overlay.value = rule.overlay ?? false;
    contributors.value = rule.contributors?.split(', ') ?? [''];
    remark.value = '';
    const target = targetEntries.value.find((candidate) => candidate.key === entry.rule.target);
    if (target) {
        selectTarget(target);
        return;
    }
    clearTarget();
    clearSelectedIcon();
    const inheritedFormat = rule.format?.toLocaleLowerCase();
    if (inheritedFormat === 'png' || inheritedFormat === 'svg') format.value = inheritedFormat;
    const file = await fetchOriginalIcon(entry);
    // The visitor may have switched to another package while the icon was loading.
    if (!file || editingKey.value !== key) return;
    originalIcon.value = file;
    await processSelectedFile(file);
};
const resetEditedFields = () => {
    editingKey.value = '';
    originalIcon.value = undefined;
    color.value = '';
    contributors.value = [''];
    format.value = 'png';
    label.value = '';
    labelRows.value = [];
    localizedLabel.value = false;
    overlay.value = false;
    remark.value = '';
    clearTarget();
    clearSelectedIcon();
};

/** Indexes of contributors that repeat an earlier one, GitHub user names are case-insensitive. */
const duplicateContributorIndexes = computed(() => {
    const names = contributors.value.map((value) => value.trim().toLocaleLowerCase());
    return names.flatMap((value, index) => value && names.indexOf(value) !== index ? [index] : []);
});
/** Messages for filled fields with invalid values, empty fields only keep the submit button disabled. */
const fieldErrors = computed(() => {
    const validation = text.value.validation;
    const errors: Partial<Record<'color' | 'contributors' | 'format' | 'label' | 'packageName' | 'target', string>> = {};
    const normalizedPackage = packageName.value.trim();
    if (normalizedPackage) {
        if (!packageNamePattern.test(normalizedPackage) && !legacyPackageNames.has(normalizedPackage))
            errors.packageName = validation.packageName;
        else if (resourcesError.value || selectedCategory.value?.errors.length)
            errors.packageName = validation.packageRegistryUnavailable;
        else if (!editingEntry.value && packageNameExists(normalizedPackage))
            errors.packageName = validation.packageNameExists;
    }
    if (normalizedPackage && selectedTarget.value?.key === normalizedPackage) errors.target = validation.targetSame;
    const labelRowsFilled = labelRows.value.every((row) => row.code.trim() && row.value.trim());
    if (localizedLabel.value && labelRows.value.length && labelRowsFilled) {
        const normalizedCodes = labelRows.value.map((row) => row.code.trim().toLocaleLowerCase());
        const codesValid = labelRows.value.every((row) =>
            localeCodePattern.test(row.code.trim()) && commonLocaleCodes.has(row.code.trim().toLocaleLowerCase())
        );
        if (!codesValid || new Set(normalizedCodes).size !== normalizedCodes.length) errors.label = validation.labelI18n;
    }
    const normalizedColor = color.value.trim().toLocaleUpperCase();
    if (normalizedColor && !colorPattern.test(normalizedColor)) errors.color = validation.color;
    if (selectedFormat.value && format.value && selectedFormat.value !== format.value)
        errors.format = validation.formatMismatch;
    if (duplicateContributorIndexes.value.length) errors.contributors = validation.contributorsDuplicate;
    return errors;
});
const validateForm = () => {
    if (Object.keys(fieldErrors.value).length || iconValidating.value) return false;
    if (!selectedTarget.value && !selectedFile.value) return false;
    if (!selectedTarget.value && iconValidation.value && !iconValidation.value.valid) return false;
    if (!category.value || !packageName.value.trim()) return false;
    if (resourcesLoading.value || !selectedCategory.value) return false;
    if (localizedLabel.value
        ? !labelRows.value.length || labelRows.value.some((row) => !row.code.trim() || !row.value.trim())
        : !label.value.trim()) return false;
    if (!format.value) return false;
    if (!contributors.value.length || contributors.value.some((value) => !value.trim())) return false;
    if (editingEntry.value && !remark.value.trim()) return false;
    return true;
};

const isSameLabel = (value: ManifestLabel | undefined, current: ManifestLabel) => {
    if (typeof value !== 'object' || typeof current !== 'object') return value === current;
    const entries = Object.entries(current);
    return entries.length === Object.keys(value).length && entries.every(([code, text]) => value[code] === text);
};
const isSameFile = async (first: File, second: File) => {
    if (first === second) return true;
    if (first.size !== second.size) return false;
    const [firstBytes, secondBytes] = await Promise.all([first.arrayBuffer(), second.arrayBuffer()]
        .map(async (bytes) => new Uint8Array(await bytes)));
    return firstBytes.every((value, index) => value === secondBytes[index]);
};
// Appending contributors or editing remarks alone is not a modification of the resource.
const hasIconChanges = async () => {
    if (selectedTarget.value) return false;
    if (!originalIcon.value || !selectedFile.value) return true;
    return !await isSameFile(selectedFile.value, originalIcon.value);
};
const hasResourceChanges = (entry: ResolvedIconEntry, iconChanged: boolean) => {
    const rule = entry.effectiveRule;
    return iconChanged || !isSameLabel(rule.label, serializedLabel.value) ||
        color.value.trim().toLocaleUpperCase() !== (rule.color?.toLocaleUpperCase() ?? '') ||
        overlay.value !== (rule.overlay ?? false);
};
// The submit button stays disabled until the form is valid and a modification changes the resource.
const submitDisabled = computed(() => !validateForm() ||
    Boolean(editingEntry.value) && !hasResourceChanges(editingEntry.value!, iconChanged.value)
);
const createIconPayload = async () => {
    const bytes = new Uint8Array(await selectedFile.value!.arrayBuffer());
    return {
        data: selectedData.value,
        format: format.value,
        mimeType: format.value === 'png' ? 'image/png' : 'image/svg+xml',
        schemaVersion: 1,
        sha256: await sha256(bytes),
        size: bytes.byteLength
    } satisfies IconPayload;
};
const createIssueUrl = () => {
    const titleLabel = typeof serializedLabel.value === 'string'
        ? serializedLabel.value
        : Object.values(serializedLabel.value)[0] ?? '';
    const parameters = new URLSearchParams({
        template: 'submit_adaption.yml',
        title: `[Submission] ${titleLabel}`,
        'anip-type': editingEntry.value ? '1' : '0',
        'anip-category': String(categorySpecs.findIndex(({ id }) => id === category.value)),
        'anip-package-name': packageName.value.trim(),
        'anip-target': selectedTarget.value?.key ?? '',
        'anip-label': typeof serializedLabel.value === 'string'
            ? serializedLabel.value
            : JSON.stringify(serializedLabel.value),
        'anip-color': color.value,
        'anip-format': format.value.toLocaleUpperCase(),
        'anip-overlay': String(overlay.value),
        'anip-contributors': serializedContributors.value,
        'anip-remark': remark.value.trim()
    });
    return `${configs.github.repo}/issues/new?${parameters}`;
};
const prepareSubmission = async () => {
    if (submitDisabled.value) return;
    normalizeColor();
    pendingPayload.value = '';
    // A modification that keeps the current icon does not need to carry the icon resource.
    if (await hasIconChanges()) {
        const iconPayload = await createIconPayload();
        pendingPayload.value = `ANIP_RESOURCE:${textToBase64Url(JSON.stringify(iconPayload))}`;
    }
    pendingIssueUrl.value = createIssueUrl();
    if (overlay.value && !remark.value.trim()) {
        overlayWarningOpen.value = true;
        return;
    }
    rememberContributors();
    modalOpen.value = true;
};
const continueOverlaySubmission = () => {
    overlayWarningOpen.value = false;
    rememberContributors();
    modalOpen.value = true;
};
const copyText = async (value: string) => {
    try {
        await navigator.clipboard.writeText(value);
        return;
    } catch {
        const textarea = document.createElement('textarea');
        textarea.value = value;
        textarea.style.position = 'fixed';
        textarea.style.opacity = '0';
        document.body.append(textarea);
        textarea.select();
        const copied = document.execCommand('copy');
        textarea.remove();
        if (!copied) throw new Error('clipboard write failed');
    }
};
const confirmSubmission = async () => {
    if (!pendingPayload.value) {
        window.location.assign(pendingIssueUrl.value);
        return;
    }
    try {
        await copyText(pendingPayload.value);
        window.location.assign(pendingIssueUrl.value);
    } catch {
        showToast(text.value.errors.clipboard);
    }
};

provide(submitAdaptionContextKey, {
    activateLocalizedLabel,
    addContributor,
    addLabelRow,
    category,
    changeTarget,
    color,
    colorControl,
    colorMenuOpen,
    confirmSubmission,
    continueOverlaySubmission,
    contributorSuggestions,
    contributors,
    dependentEntries,
    duplicateContributorIndexes,
    editingEntry,
    fieldErrors,
    fileInput,
    filteredPackages,
    filteredTargets,
    format,
    handleFileDrop,
    handleFileSelection,
    handleIconDragEnter,
    handleIconDragLeave,
    handleIconDragOver,
    handlePickerColorChange,
    iconDragActive,
    iconResetDisabled,
    iconValidating,
    isLocaleSelected,
    label,
    labelRows,
    locale,
    localizedLabel,
    localizedResourceLabel,
    lockedContributorCount,
    maxRemarkLength,
    modalOpen,
    normalizeColor,
    normalizePickerFormatLabel,
    openFilePicker,
    originalIcon,
    overlay,
    overlayWarningOpen,
    packageName,
    pendingPayload,
    pickerColor,
    pickerLanguage,
    prepareSubmission,
    previewColorizableOnHover,
    previewColorized,
    previewSource,
    remark,
    removeContributor,
    removeLabelRow,
    removeSavedContributor,
    resetLocalizedLabel,
    resetSelectedIcon,
    resourcesError,
    resourcesLoading,
    selectedTarget,
    selectedFormat,
    submitDisabled,
    targetQuery,
    text,
    validationStatus
});

watch(category, () => clearTarget());
watch([selectedFile, originalIcon, selectedTarget], async ([file]) => {
    const changed = await hasIconChanges();
    // Another icon may have been selected while the bytes were compared.
    if (selectedFile.value === file) iconChanged.value = changed;
}, { immediate: true });
watch(editingEntry, (entry) => {
    const key = entry ? `${entry.category.id}:${entry.key}` : '';
    // Reloaded manifests after a locale switch resolve to the same key and must keep the edited draft.
    if (key === editingKey.value) return;
    if (entry) fillFromEntry(entry, key);
    else resetEditedFields();
});
onMounted(async () => {
    antMessage.config({ top: '80px', duration: 3.6, maxCount: 5 });
    savedContributors.value = readSavedContributors();
    document.addEventListener('click', closeFloatingControls);
    document.addEventListener('keydown', closeOnEscape);
    try {
        categories.value = await loadIconResources();
        if (!categories.value.some((candidate) => candidate.entries.length))
            resourcesError.value = text.value.loadFailed;
    } catch {
        resourcesError.value = text.value.loadFailed;
    } finally {
        resourcesLoading.value = false;
    }
});
onBeforeUnmount(() => {
    document.removeEventListener('click', closeFloatingControls);
    document.removeEventListener('keydown', closeOnEscape);
    if (!isLocalizedRouteSwitch(mountedRoutePath, window.location.pathname)) resetSubmitAdaptionState();
});
</script>
<template>
    <SubmitFields />
    <SubmitConfirmationModal />
</template>