import { ref } from 'vue';
import type { CategoryId, ResolvedIconEntry } from '../../../data/icon-resources';

/** One localized label row in the submission draft. */
export interface LabelRow {
    code: string;
    id: number;
    value: string;
}

/** Encoded icon data kept in the submission draft. */
export interface IconPayload {
    data: string;
    format: 'png' | 'svg';
    mimeType: 'image/png' | 'image/svg+xml';
    schemaVersion: 1;
    sha256: string;
    size: number;
}

/** Result of the client-side icon checks. */
export interface IconValidation {
    grayscale: boolean;
    message: string;
    valid: boolean;
}

/** Keeps editable submission values while VitePress swaps a localized page. */
export const submitAdaptionState = {
    category: ref<CategoryId>('app'),
    color: ref(''),
    contributors: ref(['']),
    format: ref<'png' | 'svg'>('png'),
    iconValidation: ref<IconValidation>(),
    label: ref(''),
    labelRows: ref<LabelRow[]>([]),
    localizedLabel: ref(false),
    nextLabelRowId: ref(0),
    overlay: ref(false),
    packageName: ref(''),
    pickerColor: ref('transparent'),
    remark: ref(''),
    selectedData: ref(''),
    selectedFile: ref<File>(),
    selectedFormat: ref<'png' | 'svg'>(),
    selectedTarget: ref<ResolvedIconEntry>(),
    targetQuery: ref('')
};

/** Clears the draft when the visitor leaves the submission page. */
export const resetSubmitAdaptionState = () => {
    submitAdaptionState.category.value = 'app';
    submitAdaptionState.color.value = '';
    submitAdaptionState.contributors.value = [''];
    submitAdaptionState.format.value = 'png';
    submitAdaptionState.iconValidation.value = undefined;
    submitAdaptionState.label.value = '';
    submitAdaptionState.labelRows.value = [];
    submitAdaptionState.localizedLabel.value = false;
    submitAdaptionState.nextLabelRowId.value = 0;
    submitAdaptionState.overlay.value = false;
    submitAdaptionState.packageName.value = '';
    submitAdaptionState.pickerColor.value = 'transparent';
    submitAdaptionState.remark.value = '';
    submitAdaptionState.selectedData.value = '';
    submitAdaptionState.selectedFile.value = undefined;
    submitAdaptionState.selectedFormat.value = undefined;
    submitAdaptionState.selectedTarget.value = undefined;
    submitAdaptionState.targetQuery.value = '';
};