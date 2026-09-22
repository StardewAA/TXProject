<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { publicMarketClient } from '@/api/public-market'
const visible = ref(false)
let alive = true
onMounted(async () => { try { const directory = await publicMarketClient.directory(); if (alive) visible.value = directory.enabled } catch { /* Existing entry remains usable. */ } })
onBeforeUnmount(() => { alive = false })
</script>
<template><MallLink v-if="visible" to="/markets">浏览其他商城</MallLink></template>
