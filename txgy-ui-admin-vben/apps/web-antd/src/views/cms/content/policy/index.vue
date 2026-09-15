<script lang="ts" setup>
import { cmsContentConfigs } from '../config';
import ContentList from '../content-list.vue';

defineOptions({ name: 'CmsPolicy' });

const config = cmsContentConfigs.policy;
</script>

<template>
  <ContentList v-bind="config" />
</template>
