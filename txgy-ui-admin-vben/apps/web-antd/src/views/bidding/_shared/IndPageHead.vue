<!-- 工业风页头：斜纹刻度 + 石墨头（大标题/徽标/副说明）+ 右侧动作插槽 -->
<script lang="ts" setup>
withDefaults(defineProps<{ title: string; mark?: string; sub?: string }>(), {});
</script>

<template>
  <div>
    <div class="ind-stripes"></div>
    <div class="ind-head">
      <div class="min-w-0">
        <div class="ind-title">
          {{ title }}
          <span v-if="mark" class="ind-mark">{{ mark }}</span>
        </div>
        <div v-if="sub" class="ind-sub">{{ sub }}</div>
      </div>
      <div class="ind-actions">
        <slot name="actions"></slot>
      </div>
    </div>
  </div>
</template>
