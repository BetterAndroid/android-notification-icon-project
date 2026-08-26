<script lang="ts">
interface GitHubUserResponse {
    name?: string | null;
}

const nameRequests = new Map<string, Promise<string>>();
const resolveGitHubName = (username: string) => {
    const cached = nameRequests.get(username);
    if (cached) return cached;
    const request = fetch(`https://api.github.com/users/${encodeURIComponent(username)}`, {
        headers: { Accept: 'application/vnd.github+json' }
    }).then(async (response) => {
        if (!response.ok) return username;
        const profile = await response.json() as GitHubUserResponse;
        return profile.name?.trim() || username;
    }).catch(() => username);
    nameRequests.set(username, request);
    return request;
};
</script>

<script setup lang="ts">
import { onMounted, ref } from 'vue';

const props = defineProps<{ username: string }>();
const name = ref(props.username);

onMounted(async () => name.value = await resolveGitHubName(props.username));
</script>

<template>
    <a class="github-user" :href="`https://github.com/${username}`" target="_blank" rel="noopener noreferrer">
        <img :src="`https://github.com/${username}.png?size=64`" :alt="name" width="32" height="32">
        <span>{{ name }}</span>
    </a>
</template>

<style scoped lang="scss">
.github-user {
    display: inline-flex;
    align-items: center;
    gap: 9px;
    vertical-align: middle;
    white-space: nowrap;

    img {
        flex: 0 0 32px;
        width: 32px;
        height: 32px;
        margin: 0;
        border-radius: 50%;
        object-fit: cover;
    }
}
</style>