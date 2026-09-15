<script lang="ts" setup>
import { cmsContentConfigs } from '../config';
import ContentList from '../content-list.vue';

defineOptions({ name: 'CmsProcessResource' });

const config = cmsContentConfigs.process;
</script>

<template>
  <ContentList v-bind="config" />
</template>
