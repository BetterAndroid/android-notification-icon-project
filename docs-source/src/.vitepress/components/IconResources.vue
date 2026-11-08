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
import ResourceSorting from './icon-resources/ResourceSorting.vue';
import {
    categorySpecs,
    loadIconResources,
    resolveLabel,
    type CategoryId,
    type ManifestLabel
} from '../data/icon-resources';
import {
    iconResourcesState,
    resetIconResourcesState,
    type IconResourceSortMode
} from './icon-resources/utils/state';

const { locale, text } = useComponentMessages('iconResources');
const route = useRoute();
const mountedRoutePath = route.path;
const sortModeCookieName = 'anip-icon-resources-sort';
const sortModeCookieMaxAge = 60 * 60 * 24 * 365;
const sortModes = new Set<IconResourceSortMode>(['nameAsc', 'nameDesc', 'addedAsc', 'addedDesc']);
const { activeCategoryId, categories, fatalError, loading, query, sortMode } = iconResourcesState;
const readSavedSortMode = () => {
    const cookie = document.cookie.split('; ').find((value) => value.startsWith(`${sortModeCookieName}=`));
    if (!cookie) return;
    const value = cookie.slice(sortModeCookieName.length + 1);
    return sortModes.has(value as IconResourceSortMode) ? value as IconResourceSortMode : undefined;
};
const writeSavedSortMode = (value: IconResourceSortMode) => {
    document.cookie = `${sortModeCookieName}=${value}; Max-Age=${sortModeCookieMaxAge}; Path=/; SameSite=Lax`;
};
const persistedSortMode = computed({
    get: () => sortMode.value,
    set: (value: IconResourceSortMode) => {
        sortMode.value = value;
        writeSavedSortMode(value);
    }
});
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
const sortedEntries = computed(() => {
    const entries = filteredEntries.value;
    if (sortMode.value === 'addedAsc') return entries;
    if (sortMode.value === 'addedDesc') return [...entries].reverse();
    const collator = new Intl.Collator(locale.value, {
        numeric: true,
        sensitivity: 'base',
        usage: 'sort'
    });
    const direction = sortMode.value === 'nameAsc' ? 1 : -1;
    return [...entries].sort((left, right) => direction * (
        collator.compare(
            resolveLabel(left.effectiveRule.label, locale.value) ?? left.key,
            resolveLabel(right.effectiveRule.label, locale.value) ?? right.key
        ) || collator.compare(left.key, right.key)
    ));
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
    imageZoom.on('open', () => {
        const width = document.documentElement.clientWidth;
        const height = document.documentElement.clientHeight;
        // medium-zoom expands SVGs to the viewport, so keep resource previews within the 150 px icon limit.
        const horizontalInset = Math.max(0, (width - 150 - 64) / 2);
        const verticalInset = Math.max(0, (height - 150 - 64) / 2);
        imageZoom?.update({
            container: {
                width,
                height,
                left: horizontalInset,
                right: horizontalInset,
                top: verticalInset,
                bottom: verticalInset
            }
        });
    });
});

watch([sortedEntries, loading], refreshImageViewer, { flush: 'post' });
onBeforeUnmount(() => {
    imageZoom?.detach();
    if (!isLocalizedRouteSwitch(mountedRoutePath, window.location.pathname)) resetIconResourcesState();
});

onMounted(async () => {
    const savedSortMode = readSavedSortMode();
    if (savedSortMode) sortMode.value = savedSortMode;
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
        <div class="resource-toolbar">
            <CategoryNavigation v-model:system-menu-open="systemMenuOpen" :active-category-id="activeCategoryId"
                :category-count="categoryCount" :system-categories="systemCategories"
                :system-button-label="systemButtonLabel" :text="text" @select="selectCategory" />
            <ResourceSorting v-model="persistedSortMode" :text="text" />
        </div>
        <label class="search-box">
            <span aria-hidden="true">⌕</span>
            <input v-model="query" type="search" :placeholder="text.search">
        </label>
        <ResourceResults :fatal-error="fatalError" :filtered-entries="sortedEntries" :loading="loading"
            :selected-category="selectedCategory" :text="text" />
    </section>
</template>

<style scoped lang="scss">
.icon-resources-shell {
    margin-top: 24px;
}

.resource-toolbar {
    display: flex;
    align-items: flex-start;
    gap: 8px;

    :deep(.category-tabs) {
        flex: 1 1 auto;
        min-width: 0;
    }
}

@media (max-width: 640px) {
    .resource-toolbar {
        display: grid;
        grid-template-columns: minmax(0, 1fr) auto;

        :deep(.category-tabs) {
            width: 100%;
            padding-right: 8px;
            overflow-x: auto;
            overflow-y: hidden;
            overscroll-behavior-x: contain;
            scrollbar-width: none;
            -webkit-overflow-scrolling: touch;

            &::-webkit-scrollbar {
                display: none;
            }
        }

        :deep(.sort-control) {
            z-index: 1;
        }
    }
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