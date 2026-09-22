<script setup lang="ts">
import { Page } from '@vben/common-ui';

import QuoteList from '../modules/quote-list.vue';
</script>

<template>
  <Page auto-content-height>
    <QuoteList communication-only />
  </Page>
</template>
