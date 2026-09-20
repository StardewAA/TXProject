<script setup lang="ts">
import EmptyState from '@/components/EmptyState.vue'
import { useRoute } from 'vue-router'
const route = useRoute()
</script>

<template><div class="container page-section panel"><EmptyState :title="route.path === '/tenant-unavailable' ? '当前入口尚未开放该商城' : '页面不存在'" :description="route.path === '/tenant-unavailable' ? '请返回当前商城首页继续浏览' : '访问地址可能已失效'"><el-button type="primary" @click="$router.push('/')">返回首页</el-button></EmptyState></div></template>
