<script lang="ts" setup>
import { useRouter } from 'vue-router';

import { Button } from 'ant-design-vue';

defineOptions({ name: 'TrainingPageHeaderBar' });

defineProps<{ crumb?: string; title: string; description?: string }>();

const router = useRouter();
</script>

<template>
  <section
    class="flex items-center justify-between gap-4 border-b border-gray-100 pb-3 dark:border-gray-700"
  >
    <div class="min-w-0">
      <div v-if="crumb" class="text-xs text-gray-400">{{ crumb }}</div>
      <div class="text-lg font-semibold leading-7 text-gray-900 dark:text-gray-100">
        {{ title }}
      </div>
      <div
        v-if="description"
        class="mt-0.5 text-xs leading-5 text-gray-500 dark:text-gray-400"
      >
        {{ description }}
      </div>
    </div>
    <div class="flex shrink-0 items-center gap-2">
      <slot name="actions"></slot>
      <Button size="small" @click="router.push('/training/workbench')">
        返回工作台
      </Button>
    </div>
  </section>
</template>
