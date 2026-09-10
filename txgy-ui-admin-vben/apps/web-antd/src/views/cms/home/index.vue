<script setup lang="ts">
import { Page } from '@vben/common-ui';

import LawSearchPanel from '../_shared/law-search-panel.vue';

defineOptions({ name: 'CmsHome' });
</script>

<template>
  <Page>
    <div class="cms-search-page">
      <header>
        <p>CMS 系统 · 智能检索</p>
        <h1>法规政策与国家标准检索</h1>
        <span>支持原文检索、混合向量召回和带原文引用的智能回答。</span>
      </header>
      <LawSearchPanel />
    </div>
  </Page>
</template>

<style scoped>
.cms-search-page { display: grid; gap: 20px; }
header { padding: 4px 2px; }
header p { margin: 0 0 6px; color: #2563eb; font-size: 13px; font-weight: 700; }
header h1 { margin: 0; color: #0f172a; font-size: 28px; }
header span { display: block; margin-top: 8px; color: #64748b; }
</style>
