import { withBase } from 'vitepress';
import { configs } from '../configs/template';

/** Stable identifier for one published icon resources category. */
export type CategoryId = 'app' | 'game' | 'common' | 'mios' | 'coloros';

/** A default label or locale-keyed labels supplied by one manifest rule. */
export type ManifestLabel = string | Record<string, string>;

/** JSON fields accepted for one manifest rule. */
export interface ManifestRule {
    target?: string;
    label?: ManifestLabel;
    format?: string;
    color?: string;
    overlay?: boolean;
    contributors?: string;
}

/** Repository path and identifier for a manifest category. */
export interface CategorySpec {
    id: CategoryId;
    path: string;
}

/** Browser-ready rule with inherited fields and resolved resource URLs. */
export interface ResolvedIconEntry {
    category: CategorySpec;
    effectiveRule: ManifestRule;
    errors: string[];
    iconUrls: string[];
    key: string;
    rule: ManifestRule;
}

/** One loaded manifest together with its resolved entries and diagnostics. */
export interface LoadedCategory {
    entries: ResolvedIconEntry[];
    errors: string[];
    resourceDirectoryUrl: string;
    sourceUrl?: string;
    spec: CategorySpec;
}

interface ManifestLocation {
    category: CategorySpec;
    key: string;
    rule: ManifestRule;
}

interface LoadedManifest {
    category: CategorySpec;
    manifest: Record<string, ManifestRule>;
    sourceUrl: string;
}

/** Manifest categories loaded by the resource portal in display order. */
export const categorySpecs: CategorySpec[] = [
    { id: 'app', path: 'app' },
    { id: 'game', path: 'game' },
    { id: 'common', path: 'system/common' },
    { id: 'mios', path: 'system/mios' },
    { id: 'coloros', path: 'system/coloros' }
];

const appendPath = (root: string, relativePath: string) => `${root.replace(/\/$/, '')}/${relativePath}`;
const resourceDirectoryUrl = (category: CategorySpec) =>
    `${configs.github.repo}/tree/${configs.github.branch}/icons/${category.path}/res`;

/** Resolves a manifest label for the active documentation locale. */
export const resolveLabel = (label: ManifestLabel | undefined, locale: string) => {
    if (!label || typeof label === 'string') return label;
    const normalizedLocale = locale.toLocaleLowerCase();
    const localizedLabel = Object.entries(label)
        .find(([key]) => key.toLocaleLowerCase() === normalizedLocale)?.[1];
    return localizedLabel || label.en || Object.values(label).find((value) => value.trim());
};

const resourceRoots = () => {
    if (!import.meta.env.DEV) return [configs.resources.raw, configs.resources.cdn];
    return [new URL(withBase('/icons/'), window.location.origin).href];
};

const fetchManifest = async (category: CategorySpec, roots: string[]): Promise<LoadedManifest> => {
    const failures: string[] = [];
    for (const root of roots) {
        const sourceUrl = appendPath(root, `${category.path}/manifest.json`);
        const controller = new AbortController();
        const timeout = window.setTimeout(() => controller.abort(), 12_000);
        try {
            const response = await fetch(sourceUrl, {
                cache: import.meta.env.DEV ? 'no-store' : 'default',
                credentials: 'omit',
                mode: 'cors',
                signal: controller.signal
            });
            if (!response.ok) throw new Error(`HTTP ${response.status}`);
            const manifest = await response.json() as unknown;
            if (!manifest || Array.isArray(manifest) || typeof manifest !== 'object')
                throw new Error('invalid manifest root');
            return {
                category,
                manifest: manifest as Record<string, ManifestRule>,
                sourceUrl
            };
        } catch (error) {
            failures.push(`${sourceUrl}: ${error instanceof Error ? error.message : String(error)}`);
        } finally {
            window.clearTimeout(timeout);
        }
    }
    throw new Error(failures.join('\n'));
};

const mergeRule = (base: ManifestRule, current: ManifestRule): ManifestRule => ({
    label: current.label ?? base.label,
    format: current.format ?? base.format,
    color: current.color ?? base.color,
    overlay: current.overlay ?? base.overlay,
    contributors: current.contributors ?? base.contributors,
    ...(current.target ? { target: current.target } : {})
});

/** Loads every manifest and resolves target inheritance within that manifest only. */
export const loadIconResources = async (): Promise<LoadedCategory[]> => {
    const roots = resourceRoots();
    const settled = await Promise.allSettled(categorySpecs.map((category) => fetchManifest(category, roots)));
    const manifests = settled
        .filter((result): result is PromiseFulfilledResult<LoadedManifest> => result.status === 'fulfilled')
        .map((result) => result.value);
    const categoryErrors = new Map<CategoryId, string[]>();
    settled.forEach((result, index) => {
        if (result.status === 'fulfilled') return;
        categoryErrors.set(categorySpecs[index].id, [result.reason instanceof Error ? result.reason.message : String(result.reason)]);
    });

    const locations = new Map<CategoryId, Map<string, ManifestLocation>>();
    for (const loaded of manifests) {
        const categoryLocations = new Map<string, ManifestLocation>();
        for (const [key, rule] of Object.entries(loaded.manifest)) {
            categoryLocations.set(key, { category: loaded.category, key, rule });
        }
        locations.set(loaded.category.id, categoryLocations);
    }

    const resolveLocation = (location: ManifestLocation, stack = new Set<string>()): {
        effectiveRule: ManifestRule;
        errors: string[];
        resourceKey: string;
    } => {
        if (!location.rule.target) {
            const errors = location.rule.format ? [] : [`missing format: ${location.key}`];
            return {
                effectiveRule: location.rule,
                errors,
                resourceKey: location.key
            };
        }
        const locationId = `${location.category.id}:${location.key}`;
        if (stack.has(locationId)) {
            return {
                effectiveRule: location.rule,
                errors: [`target cycle: ${[...stack, locationId].join(' -> ')}`],
                resourceKey: location.key
            };
        }
        const targetLocation = locations.get(location.category.id)?.get(location.rule.target);
        if (!targetLocation) {
            return {
                effectiveRule: location.rule,
                errors: [`missing target in ${location.category.path}: ${location.rule.target}`],
                resourceKey: location.key
            };
        }
        const nextStack = new Set(stack);
        nextStack.add(locationId);
        const target = resolveLocation(targetLocation, nextStack);
        return {
            effectiveRule: mergeRule(target.effectiveRule, location.rule),
            errors: target.errors,
            resourceKey: target.resourceKey
        };
    };

    return categorySpecs.map((category) => {
        const loaded = manifests.find((candidate) => candidate.category.id === category.id);
        if (!loaded) return {
            spec: category,
            entries: [],
            errors: categoryErrors.get(category.id) ?? ['manifest unavailable'],
            resourceDirectoryUrl: resourceDirectoryUrl(category)
        };
        const entries = Object.entries(loaded.manifest).map(([key, rule]) => {
            const resolved = resolveLocation({ category, key, rule });
            const format = resolved.effectiveRule.format;
            const resourcePath = `${category.path}/res/${resolved.resourceKey}${format ? `.${format}` : ''}`;
            return {
                category,
                effectiveRule: resolved.effectiveRule,
                errors: resolved.errors,
                iconUrls: format && !resolved.errors.length
                    ? roots.map((root) => appendPath(root, resourcePath))
                    : [],
                key,
                rule
            } satisfies ResolvedIconEntry;
        });
        return {
            spec: category,
            entries,
            errors: categoryErrors.get(category.id) ?? [],
            resourceDirectoryUrl: resourceDirectoryUrl(category),
            sourceUrl: loaded.sourceUrl
        };
    });
};