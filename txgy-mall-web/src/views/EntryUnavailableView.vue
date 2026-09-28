<script setup lang="ts">
import { ENTRY_KEY } from '@/utils/tenant-entry.mjs'
function retry() { window.location.assign('/') }
function original() { sessionStorage.removeItem(ENTRY_KEY); window.location.assign('/') }
</script>
<template><main class="entry-error"><h1>当前商城入口暂不可用</h1><p>可能尚未开放、已停用或目录服务暂时不可用。没有读取原商城的同编号商品或私有资料。</p><button @click="retry">重试当前商城</button><button @click="original">返回原商城</button></main></template>
<style scoped>.entry-error{max-width:800px;margin:80px auto;padding:24px;line-height:2}button{margin:12px;padding:8px 20px}</style>
