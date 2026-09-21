<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{ name: string }>()

interface IconDefinition { fill?: string[]; detail?: string[]; detailFill?: string[]; outline?: string[] }
const icons: Record<string, IconDefinition> = {
  menu: { outline: ['M4 6h16', 'M4 12h16', 'M4 18h16'] },
  shield: {
    fill: ['M12 2.3 21 5.8v6.1c0 5.6-3.8 9.1-9 10.6-5.2-1.5-9-5-9-10.6V5.8l9-3.5Z'],
    detail: ['m7.9 12.1 2.6 2.6 5.7-5.8'],
  },
  thermometer: {
    fill: ['M12 2a3.2 3.2 0 0 0-3.2 3.2v7.5a5.2 5.2 0 1 0 6.4 0V5.2A3.2 3.2 0 0 0 12 2Z'],
    detail: ['M12 6v10.2'], detailFill: ['M12 19.2a2.2 2.2 0 1 0 0-4.4 2.2 2.2 0 0 0 0 4.4Z'],
  },
  snowflake: {
    outline: ['M12 2.5v19', 'm4.6 2.7-9.2 13.6', 'm.1-13.6 9.2 13.6', 'm8.2-10.9 3.8 2.2 3.8-2.2', 'm-7.6 15.3 3.8-2.2 3.8 2.2'],
    fill: ['M12 9.1a2.9 2.9 0 1 0 0 5.8 2.9 2.9 0 0 0 0-5.8Z'],
  },
  droplet: {
    fill: ['M12 1.8S5.1 9.3 5.1 15a6.9 6.9 0 0 0 13.8 0C18.9 9.3 12 1.8 12 1.8Z'],
    detail: ['M9 15.2c.5 1.5 1.5 2.3 3 2.6'],
  },
  pipe: {
    fill: ['M3 4h13.8v4.4H21V21h-5.2V9.2H3V4Z'],
    detail: ['M7 4v5.2', 'M15.8 14.8H21'],
  },
  layers: {
    fill: ['m12 2 10 5-10 5L2 7l10-5Z', 'm2 11 10 5 10-5v4l-10 5-10-5v-4Z'],
    detail: ['m4.5 8.2 7.5 3.7 7.5-3.7'],
  },
  tools: {
    fill: ['m14.4 3.1 2.2 2.2 3.2-1.2a5.2 5.2 0 0 1-6.6 6.6L6.1 17.8 8.2 20l7.2-7.2a5.2 5.2 0 0 1 6.5-6.6l-1.2 3.2-2.2-2.2-4.1-4.1Z', 'M4.9 16.6 7.4 19l-2.1 2.1a1.8 1.8 0 0 1-2.5-2.5l2.1-2Z'],
    detail: ['m15.5 7.1 1.4 1.4'],
  },
  helmet: {
    fill: ['M4 15.8a8 8 0 0 1 16 0v.2H4v-.2Z', 'M2.5 16h19v3.6h-19V16Z'],
    detail: ['M9 6.1v6.2', 'M15 6.1v6.2'],
  },
  package: {
    fill: ['m12 2 9.5 5.3v9.4L12 22l-9.5-5.3V7.3L12 2Z'],
    detail: ['m3.4 7.8 8.6 4.8 8.6-4.8', 'M12 12.6v8.2'],
  },
  compass: {
    fill: ['M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20Z'],
    detailFill: ['m16.8 7.2-3 7-7 3 3-7 7-3Z', 'M12 10.4a1.6 1.6 0 1 0 0 3.2 1.6 1.6 0 0 0 0-3.2Z'],
  },
  warehouse: {
    fill: ['M2 9.2 12 2l10 7.2V22H2V9.2Z'],
    detail: ['M6.2 12.5h11.6', 'M6.2 16.3h11.6', 'M6.2 20.1h11.6'],
  },
  clipboard: {
    fill: ['M6 4h12a2 2 0 0 1 2 2v16H4V6a2 2 0 0 1 2-2Z'],
    detailFill: ['M9 1.8h6a1.5 1.5 0 0 1 1.5 1.5v2H7.5v-2A1.5 1.5 0 0 1 9 1.8Z'],
    detail: ['m8 13 2.5 2.5 5.5-5.7', 'M8 19h8'],
  },
  headset: {
    fill: ['M3 12a9 9 0 0 1 18 0v7h-4.8v-7H19a7 7 0 0 0-14 0h2.8v7H4a1 1 0 0 1-1-1v-6Z'],
    detail: ['M17 19c0 1.6-1.4 2.4-4 2.4h-1'], detailFill: ['M10 20h3v3h-3Z'],
  },
}

const icon = computed<IconDefinition>(() => icons[props.name] ?? icons.package!)
</script>

<template>
  <svg viewBox="0 0 24 24" aria-hidden="true" focusable="false">
    <path v-for="path in icon.fill || []" :key="`fill-${path}`" class="icon-fill" :d="path" />
    <path v-for="path in icon.detailFill || []" :key="`detail-fill-${path}`" class="icon-detail-fill" :d="path" />
    <path v-for="path in icon.detail || []" :key="`detail-${path}`" class="icon-detail" :d="path" />
    <path v-for="path in icon.outline || []" :key="`outline-${path}`" class="icon-outline" :d="path" />
  </svg>
</template>

<style scoped>
.icon-fill { fill: currentColor; }
.icon-detail-fill { fill: var(--industrial-icon-detail, #fff); }
.icon-detail, .icon-outline { fill: none; stroke-linecap: round; stroke-linejoin: round; }
.icon-detail { stroke: var(--industrial-icon-detail, #fff); stroke-width: 1.65; }
.icon-outline { stroke: currentColor; stroke-width: 2; }
</style>
