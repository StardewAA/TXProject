<script lang="ts" setup>
import { cmsContentConfigs } from '../config';
import ContentList from '../content-list.vue';

defineOptions({ name: 'CmsQuota' });

const config = cmsContentConfigs.quota;
</script>

<template>
  <ContentList v-bind="config" />
</template>
