<script setup lang="ts">
import { Page } from '@vben/common-ui';

import LawSearchPanel from '../_shared/law-search-panel.vue';
import '../_shared/cms-glass-theme.css';

defineOptions({ name: 'CmsHome' });
</script>

<template>
  <Page>
    <div class="cms-glass-page cms-search-page">
      <LawSearchPanel />
    </div>
  </Page>
</template>
