<script setup lang="ts">
import EmptyState from '@/components/EmptyState.vue'
</script>

<template><div class="container page-section panel"><EmptyState title="页面不存在" description="访问地址可能已失效"><el-button type="primary" @click="$router.push('/')">返回首页</el-button></EmptyState></div></template>
