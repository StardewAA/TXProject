<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, useRouter, type RouteLocationRaw } from 'vue-router'
import { entryTenantId } from '@/utils/tenant-context'
import { scopeLocalPath } from '@/utils/tenant-navigation.mjs'

defineOptions({ inheritAttrs: false })
const props = defineProps<{ to: RouteLocationRaw; replace?: boolean; activeClass?: string; exactActiveClass?: string }>()
const router = useRouter()
const destination = computed(() => {
  try {
    // Validate raw paths before Router.resolve can normalize away a conflicting hint.
    const raw = typeof props.to === 'string' ? props.to : 'path' in props.to ? props.to.path : undefined
    if (raw !== undefined) scopeLocalPath(raw, entryTenantId)
    return scopeLocalPath(router.resolve(props.to).fullPath, entryTenantId)
  } catch { return null }
})
</script>

<template>
  <RouterLink v-if="destination" v-bind="$attrs" :to="destination" :replace="replace" :active-class="activeClass" :exact-active-class="exactActiveClass" v-slot="link">
    <slot v-bind="link" />
  </RouterLink>
  <span v-else :class="$attrs.class" role="link" aria-disabled="true" title="链接无效或属于其他商城，请从商城入口切换"><slot /></span>
</template>
