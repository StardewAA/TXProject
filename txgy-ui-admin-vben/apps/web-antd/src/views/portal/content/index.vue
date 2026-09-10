<script setup lang="ts">
import { ref } from 'vue';

import { Tabs } from 'ant-design-vue';

import LawSearchPanel from '../../cms/_shared/law-search-panel.vue';
import PublicationSubmit from './publication-submit.vue';

defineOptions({ name: 'PortalContent' });

const activeModule = ref<'publish' | 'search'>('search');
</script>

<template>
  <div class="portal-cms-page">
    <section class="cms-hero">
      <p>CMS 系统</p>
      <h1>企业法规检索与内容发布</h1>
      <span>企业账户可查询已发布的法规原文，或提交内容进入管理员审查流程。</span>
    </section>

    <section class="cms-modules">
      <Tabs v-model:active-key="activeModule" size="large">
        <Tabs.TabPane key="search" tab="智能检索">
          <LawSearchPanel portal />
        </Tabs.TabPane>
        <Tabs.TabPane key="publish" tab="内容发布">
          <PublicationSubmit />
        </Tabs.TabPane>
      </Tabs>
    </section>
  </div>
</template>

<style scoped>
.portal-cms-page { display: grid; gap: 20px; }
.cms-hero { padding: 30px 34px; border: 1px solid #bfdbfe; border-radius: 18px; background: linear-gradient(125deg, #eff6ff, #fff 70%); }
.cms-hero p { margin: 0 0 8px; color: #2563eb; font-size: 14px; font-weight: 700; }
.cms-hero h1 { margin: 0; color: #0f172a; font-size: 30px; }
.cms-hero span { display: block; margin-top: 10px; color: #475569; line-height: 1.7; }
.cms-modules { padding: 0 22px 22px; border: 1px solid #e2e8f0; border-radius: 16px; background: #fff; }
@media (max-width: 760px) { .cms-hero { padding: 24px; } .cms-hero h1 { font-size: 24px; } .cms-modules { padding: 0 12px 12px; } }
</style>
