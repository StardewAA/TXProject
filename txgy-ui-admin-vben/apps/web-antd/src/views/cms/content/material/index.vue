<script lang="ts" setup>
import { cmsContentConfigs } from '../config';
import ContentList from '../content-list.vue';

defineOptions({ name: 'CmsMaterial' });

const config = cmsContentConfigs.material;
</script>

<template>
  <ContentList v-bind="config" />
</template>
