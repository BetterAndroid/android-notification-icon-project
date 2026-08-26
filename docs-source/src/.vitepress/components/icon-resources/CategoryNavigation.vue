<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import type { CategoryId, CategorySpec } from '../../data/icon-resources';

interface NavigationText {
    categories: Record<CategoryId, string>;
    system: string;
}

const props = defineProps<{
    activeCategoryId: CategoryId;
    categoryCount: (id: CategoryId) => number;
    systemCategories: CategorySpec[];
    systemMenuOpen: boolean;
    systemButtonLabel: string;
    text: NavigationText;
}>();

const emit = defineEmits<{
    select: [id: CategoryId];
    'update:systemMenuOpen': [open: boolean];
}>();

const navigationRoot = ref<HTMLElement>();
const activeSystemCategory = computed(() =>
    props.systemCategories.find((category) => category.id === props.activeCategoryId)
);
const closeSystemMenuOnDocumentClick = (event: MouseEvent) => {
    if (!navigationRoot.value?.contains(event.target as Node)) emit('update:systemMenuOpen', false);
};
const closeSystemMenuOnEscape = (event: KeyboardEvent) => {
    if (event.key === 'Escape') emit('update:systemMenuOpen', false);
};

onMounted(() => {
    document.addEventListener('click', closeSystemMenuOnDocumentClick);
    document.addEventListener('keydown', closeSystemMenuOnEscape);
});
onBeforeUnmount(() => {
    document.removeEventListener('click', closeSystemMenuOnDocumentClick);
    document.removeEventListener('keydown', closeSystemMenuOnEscape);
});
</script>

<template>
    <div ref="navigationRoot" class="category-tabs">
        <button type="button" class="category-tab" :aria-pressed="activeCategoryId === 'app'"
            :class="{ active: activeCategoryId === 'app' }" @click="emit('select', 'app')">
            {{ text.categories.app }}
            <span class="count">{{ categoryCount('app') }}</span>
        </button>
        <button type="button" class="category-tab" :aria-pressed="activeCategoryId === 'game'"
            :class="{ active: activeCategoryId === 'game' }" @click="emit('select', 'game')">
            {{ text.categories.game }}
            <span class="count">{{ categoryCount('game') }}</span>
        </button>
        <div class="system-menu-wrapper">
            <button type="button" class="system-trigger" :class="{ active: activeSystemCategory }" aria-haspopup="menu"
                :aria-expanded="systemMenuOpen" @click="emit('update:systemMenuOpen', !systemMenuOpen)">
                {{ systemButtonLabel }}
                <span v-if="activeSystemCategory" class="count">{{ categoryCount(activeSystemCategory.id) }}</span>
                <span class="vpi-chevron-down system-chevron" aria-hidden="true" />
            </button>
            <Transition name="system-menu">
                <div v-if="systemMenuOpen" class="system-menu" role="menu">
                    <button v-for="category in systemCategories" :key="category.id" type="button" role="menuitemradio"
                        :aria-checked="activeCategoryId === category.id"
                        :class="{ active: activeCategoryId === category.id }" @click="emit('select', category.id)">
                        {{ text.categories[category.id] }}
                        <span>{{ categoryCount(category.id) }}</span>
                    </button>
                </div>
            </Transition>
        </div>
    </div>
</template>

<style scoped lang="scss">
.category-tabs {
    display: flex;
    align-items: flex-start;
    gap: 8px;
    padding-bottom: 6px;

    .category-tab,
    .system-trigger {
        display: inline-flex;
        align-items: center;
        flex: 0 0 auto;
        gap: 7px;
        min-height: 38px;
        padding: 0 13px;
        border: 1px solid var(--vp-c-divider);
        border-radius: 9px;
        background: var(--vp-c-bg-soft);
        color: var(--vp-c-text-2);
        font: inherit;
        cursor: pointer;

        .count {
            color: var(--vp-c-text-3);
            font-size: 12px;
        }

        &.active {
            border-color: var(--vp-c-brand-1);
            background: var(--vp-c-brand-soft);
            color: var(--vp-c-brand-1);
        }
    }
}

.system-menu-wrapper {
    position: relative;
    flex: 0 0 auto;
}

.system-chevron {
    color: var(--vp-c-text-3);
    font-size: 14px;
    transition: transform 0.2s ease;
}

.system-trigger[aria-expanded='true'] .system-chevron {
    transform: rotate(180deg);
}

.system-menu {
    position: absolute;
    z-index: 20;
    top: calc(100% + 8px);
    left: 0;
    display: grid;
    min-width: 160px;
    padding: 8px;
    border: 1px solid var(--vp-c-divider);
    border-radius: 10px;
    background: var(--vp-c-bg-elv);
    box-shadow: var(--vp-shadow-3);

    button {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: 20px;
        width: 100%;
        padding: 7px 10px;
        border: 0;
        border-radius: 6px;
        background: transparent;
        color: var(--vp-c-text-2);
        font: inherit;
        text-align: left;
        cursor: pointer;

        span {
            color: var(--vp-c-text-3);
            font-size: 12px;
        }

        &:hover {
            background: var(--vp-c-bg-soft);
            color: var(--vp-c-text-1);
        }

        &.active {
            background: var(--vp-c-brand-soft);
            color: var(--vp-c-brand-1);
        }
    }
}

.system-menu-enter-active,
.system-menu-leave-active {
    transition: opacity 0.15s ease, transform 0.15s ease;
}

.system-menu-enter-from,
.system-menu-leave-to {
    opacity: 0;
    transform: translateY(-6px);
}
</style>