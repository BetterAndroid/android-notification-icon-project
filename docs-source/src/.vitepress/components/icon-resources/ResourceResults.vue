<script setup lang="ts">
import IconCard from './IconCard.vue';
import type { LoadedCategory, ResolvedIconEntry } from '../../data/icon-resources';

interface ResultsText {
    empty: string;
    loading: string;
    loadFailed: string;
    resourceDirectory: string;
    result: string;
}

defineProps<{
    fatalError: string;
    filteredEntries: ResolvedIconEntry[];
    loading: boolean;
    selectedCategory?: LoadedCategory;
    text: ResultsText;
}>();
</script>

<template>
    <p v-if="loading" class="icon-resources-state">{{ text.loading }}</p>
    <div v-else-if="fatalError" class="icon-resources-error">
        <strong>{{ text.loadFailed }}</strong>
        <pre>{{ fatalError }}</pre>
    </div>
    <template v-else>
        <div class="icon-resources-meta">
            <span>{{ filteredEntries.length }} {{ text.result }}</span>
            <div v-if="selectedCategory" class="icon-resources-links">
                <a :href="selectedCategory.resourceDirectoryUrl" target="_blank" rel="noopener noreferrer">
                    {{ text.resourceDirectory }}
                </a>
                <span v-if="selectedCategory.sourceUrl" class="icon-resources-link-divider" aria-hidden="true">|</span>
                <a v-if="selectedCategory.sourceUrl" :href="selectedCategory.sourceUrl" target="_blank"
                    rel="noopener noreferrer">manifest.json</a>
            </div>
        </div>
        <div v-if="selectedCategory?.errors.length" class="icon-resources-error compact">
            <p v-for="error in selectedCategory.errors" :key="error">{{ error }}</p>
        </div>
        <div v-if="filteredEntries.length" class="icon-grid">
            <IconCard v-for="entry in filteredEntries" :key="`${entry.category.id}:${entry.key}`" :entry="entry" />
        </div>
        <p v-else class="icon-resources-state">{{ text.empty }}</p>
    </template>
</template>

<style scoped lang="scss">
.icon-resources-meta {
    display: flex;
    justify-content: space-between;
    margin-bottom: 12px;
    color: var(--vp-c-text-2);
    font-size: 13px;
}

.icon-resources-links {
    display: flex;
    gap: 8px;
}

.icon-resources-link-divider {
    color: var(--vp-c-divider);
    pointer-events: none;
    user-select: none;
}

.icon-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(min(100%, 330px), 1fr));
    gap: 12px;
}

.icon-resources-state {
    padding: 48px 12px;
    color: var(--vp-c-text-2);
    text-align: center;
}

.icon-resources-error {
    padding: 14px;
    border: 1px solid var(--vp-c-danger-2);
    border-radius: 10px;
    background: var(--vp-c-danger-soft);
    color: var(--vp-c-danger-1);

    pre {
        overflow: auto;
        white-space: pre-wrap;
    }

    &.compact {
        margin-bottom: 12px;
        font-size: 13px;

        p {
            margin: 0;
        }
    }
}
</style>