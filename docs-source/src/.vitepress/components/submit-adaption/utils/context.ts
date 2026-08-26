import type { InjectionKey } from 'vue';

/** Shared state boundary between the submit facade and its focused form sections. */
export type SubmitAdaptionContext = Record<string, any>;

export const submitAdaptionContextKey: InjectionKey<SubmitAdaptionContext> = Symbol('submit-adaption-context');