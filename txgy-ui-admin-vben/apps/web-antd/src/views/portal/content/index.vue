<script setup lang="ts">
import { ref } from 'vue';

import { Button, Tabs } from 'ant-design-vue';

import LawSearchPanel from '../../cms/_shared/law-search-panel.vue';
import '../../cms/_shared/cms-glass-theme.css';
import PublicationSubmit from './publication-submit.vue';

defineOptions({ name: 'PortalContent' });

const activeModule = ref<'publish' | 'search'>('search');

function openPublish() {
  activeModule.value = 'publish';
}
</script>

<template>
  <div class="cms-glass-page portal-cms-page">
    <section class="cms-modules">
      <div class="module-heading">
        <div>
          <strong>政策法规智查</strong>
          <span>POLICY · STANDARD · LAW</span>
        </div>
        <Button type="primary" @click="openPublish">发布内容</Button>
      </div>
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
.cms-modules {
  padding: 4px 24px 24px;
  background: rgb(255 255 255 / 62%);
  border: 1px solid rgb(255 255 255 / 76%);
  border-radius: 22px;
  box-shadow:
    0 22px 55px rgb(45 72 108 / 9%),
    inset 0 1px 0 rgb(255 255 255 / 88%);
  backdrop-filter: blur(22px) saturate(135%);
}

.module-heading {
  display: flex;
  gap: 18px;
  align-items: center;
  justify-content: space-between;
  min-height: 64px;
  border-bottom: 1px solid rgb(113 105 85 / 14%);
}

.module-heading > div {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.module-heading strong {
  font-family: 'Noto Serif SC', 'Songti SC', SimSun, serif;
  font-size: 16px;
  color: #1a2b4a;
}

.module-heading span {
  font-size: 8px;
  color: #9b927e;
  letter-spacing: 0.16em;
}

@media (max-width: 760px) {
  .cms-modules {
    padding: 2px 14px 14px;
  }
}
</style>
