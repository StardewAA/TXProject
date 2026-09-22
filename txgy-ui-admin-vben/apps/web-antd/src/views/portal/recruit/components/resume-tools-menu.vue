<script setup lang="ts">
defineProps<{ variant?: string; triggerLabel?: string }>();
defineEmits<{ ai: []; edit: []; upload: []; version: [] }>();
</script>

<template>
  <button type="button"><slot>{{ triggerLabel || '简历' }}</slot></button>
</template>
