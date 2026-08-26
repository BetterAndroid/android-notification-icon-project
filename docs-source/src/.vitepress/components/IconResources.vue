<script lang="ts">
import mediumZoom, { type Zoom } from 'medium-zoom';

let imageZoom: Zoom | undefined;
</script>

<script setup lang="ts">
import { useRoute } from 'vitepress';
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { isLocalizedRouteSwitch } from '../configs/i18n';
import { useComponentMessages } from '../theme/i18n';
import CategoryNavigation from './icon-resources/CategoryNavigation.vue';
import ResourceResults from './icon-resources/ResourceResults.vue';
import {
    categorySpecs,
    loadIconResources,
    type CategoryId,
    type ManifestLabel
} from '../data/icon-resources';
import { iconResourcesState, resetIconResourcesState } from './icon-resources/utils/state';

const { text } = useComponentMessages('iconResources');
const route = useRoute();
const mountedRoutePath = route.path;
const { activeCategoryId, categories, fatalError, loading, query } = iconResourcesState;
const labelSearchValues = (label: ManifestLabel | undefined) =>
    typeof label === 'string' ? [label] : Object.values(label ?? {});
const systemMenuOpen = ref(false);
const iconResourcesShell = ref<HTMLElement>();
const systemCategories = categorySpecs.filter((category) => category.id !== 'app' && category.id !== 'game');
const activeSystemCategory = computed(() =>
    systemCategories.find((category) => category.id === activeCategoryId.value)
);
const systemButtonLabel = computed(() => {
    const category = activeSystemCategory.value;
    return category ? `${text.value.system} · ${text.value.categories[category.id]}` : text.value.system;
});
const selectedCategory = computed(() =>
    categories.value.find((category) => category.spec.id === activeCategoryId.value)
);
const filteredEntries = computed(() => {
    const normalizedQuery = query.value.trim().toLocaleLowerCase();
    const entries = selectedCategory.value?.entries ?? [];
    if (!normalizedQuery) return entries;
    return entries.filter((entry) => [
        entry.key,
        entry.rule.target,
        ...labelSearchValues(entry.effectiveRule.label),
        entry.effectiveRule.contributors
    ].some((value) => value?.toLocaleLowerCase().includes(normalizedQuery)));
});

const resolveCategoryId = (value: string | null) =>
    categorySpecs.find((category) => category.path === value)?.id;
const categoryCount = (id: CategoryId) =>
    categories.value.find((category) => category.spec.id === id)?.entries.length ?? 0;
const selectCategory = (id: CategoryId) => {
    activeCategoryId.value = id;
    systemMenuOpen.value = false;
};
const refreshImageViewer = () => nextTick(() => {
    const container = iconResourcesShell.value;
    if (!container) return;
    const images = container.querySelectorAll<HTMLImageElement>('img.icon-resources-viewer-image');
    if (imageZoom) {
        imageZoom.detach();
        imageZoom.attach(images);
        return;
    }
    imageZoom = mediumZoom(images, {
        background: 'rgba(0, 0, 0, 0.9)',
        margin: 32,
        scrollOffset: 40
    });
});

watch([filteredEntries, loading], refreshImageViewer, { flush: 'post' });
onBeforeUnmount(() => {
    imageZoom?.detach();
    if (!isLocalizedRouteSwitch(mountedRoutePath, window.location.pathname)) resetIconResourcesState();
});

onMounted(async () => {
    refreshImageViewer();
    if (iconResourcesState.initialized.value) return;
    iconResourcesState.initialized.value = true;
    const requestedCategory = resolveCategoryId(new URLSearchParams(window.location.search).get('category'));
    if (requestedCategory) activeCategoryId.value = requestedCategory;
    try {
        categories.value = await loadIconResources();
        if (!categories.value.some((category) => category.entries.length))
            fatalError.value = categories.value
                .flatMap((category) => category.errors).join('\n');
    } catch (error) {
        fatalError.value = error instanceof Error ? error.message : String(error);
    } finally {
        loading.value = false;
    }
});
</script>

<template>
    <section ref="iconResourcesShell" class="icon-resources-shell">
        <CategoryNavigation v-model:system-menu-open="systemMenuOpen" :active-category-id="activeCategoryId"
            :category-count="categoryCount" :system-categories="systemCategories"
            :system-button-label="systemButtonLabel" :text="text" @select="selectCategory" />
        <label class="search-box">
            <span aria-hidden="true">⌕</span>
            <input v-model="query" type="search" :placeholder="text.search">
        </label>
        <ResourceResults :fatal-error="fatalError" :filtered-entries="filteredEntries" :loading="loading"
            :selected-category="selectedCategory" :text="text" />
    </section>
</template>

<style scoped lang="scss">
.icon-resources-shell {
    margin-top: 24px;
}

.search-box {
    display: flex;
    align-items: center;
    gap: 10px;
    margin: 14px 0;
    padding: 0 14px;
    border: 1px solid var(--vp-c-divider);
    border-radius: 10px;
    background: var(--vp-c-bg-soft);

    span {
        color: var(--vp-c-text-3);
        font-size: 30px;
        line-height: 1;
    }

    input {
        width: 100%;
        height: 44px;
        border: 0;
        outline: 0;
        background: transparent;
        color: var(--vp-c-text-1);
        font: inherit;

        &::-webkit-search-cancel-button {
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
    }
}
</style>