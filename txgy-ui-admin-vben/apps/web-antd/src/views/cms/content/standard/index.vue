<script lang="ts" setup>
import { cmsContentConfigs } from '../config';
import ContentList from '../content-list.vue';

defineOptions({ name: 'CmsStandard' });

const config = cmsContentConfigs.standard;
</script>

<template>
  <ContentList v-bind="config" />
</template>
