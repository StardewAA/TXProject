<script setup lang="ts">
import { ref } from 'vue';

import { Button, Tabs } from 'ant-design-vue';

import LawSearchPanel from '../../cms/_shared/law-search-panel.vue';
import PublicationSubmit from './publication-submit.vue';

defineOptions({ name: 'PortalContent' });

const activeModule = ref<'publish' | 'search'>('search');

function openPublish() {
  activeModule.value = 'publish';
}
</script>

<template>
  <div class="portal-cms-page">
    <section class="cms-hero">
      <div>
        <p>CMS 系统</p>
        <h1>企业法规检索与内容发布</h1>
        <span>企业账户可查询已发布的法规原文，或提交内容进入管理员审查流程。</span>
      </div>
      <Button class="hero-publish" size="large" type="primary" @click="openPublish">
        发布内容
      </Button>
    </section>

    <section class="cms-modules">
      <Tabs v-model:active-key="activeModule" size="large">
        <Tabs.TabPane key="search" tab="智能检索">
          <div class="search-publish-entry">
            <div>
              <strong>需要发布新的法规、国标或政策内容？</strong>
              <span>提交后将自动查重并进入 AI 初审和管理员人工复核。</span>
            </div>
            <Button type="primary" @click="openPublish">发布内容</Button>
          </div>
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
.cms-hero { display: flex; align-items: center; justify-content: space-between; padding: 30px 34px; border: 1px solid #bfdbfe; border-radius: 18px; background: linear-gradient(125deg, #eff6ff, #fff 70%); gap: 24px; }
.cms-hero p { margin: 0 0 8px; color: #2563eb; font-size: 14px; font-weight: 700; }
.cms-hero h1 { margin: 0; color: #0f172a; font-size: 30px; }
.cms-hero span { display: block; margin-top: 10px; color: #475569; line-height: 1.7; }
.hero-publish { min-width: 120px; flex-shrink: 0; }
.cms-modules { padding: 0 22px 22px; border: 1px solid #e2e8f0; border-radius: 16px; background: #fff; }
.search-publish-entry { display: flex; align-items: center; justify-content: space-between; margin-bottom: 14px; padding: 14px 18px; border: 1px solid #bfdbfe; border-radius: 10px; background: #eff6ff; gap: 18px; }
.search-publish-entry div { display: grid; gap: 4px; }
.search-publish-entry strong { color: #1e3a8a; font-size: 14px; }
.search-publish-entry span { color: #64748b; font-size: 13px; }
@media (max-width: 760px) { .cms-hero { align-items: flex-start; padding: 24px; flex-direction: column; } .cms-hero h1 { font-size: 24px; } .hero-publish { width: 100%; } .cms-modules { padding: 0 12px 12px; } .search-publish-entry { align-items: stretch; flex-direction: column; } }
</style>
