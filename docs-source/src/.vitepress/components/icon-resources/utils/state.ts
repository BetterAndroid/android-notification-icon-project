import { ref } from 'vue';
import type { CategoryId, LoadedCategory } from '../../../data/icon-resources';

/** Keeps the icon resources view state while VitePress swaps a localized page. */
export const iconResourcesState = {
    activeCategoryId: ref<CategoryId>('app'),
    categories: ref<LoadedCategory[]>([]),
    fatalError: ref(''),
    initialized: ref(false),
    loading: ref(true),
    query: ref('')
};

/** Clears view state when the visitor leaves the icon resources page. */
export const resetIconResourcesState = () => {
    iconResourcesState.activeCategoryId.value = 'app';
    iconResourcesState.categories.value = [];
    iconResourcesState.fatalError.value = '';
    iconResourcesState.initialized.value = false;
    iconResourcesState.loading.value = true;
    iconResourcesState.query.value = '';
};