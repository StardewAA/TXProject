<script setup lang="ts">
import MemberSidebar from './MemberSidebar.vue'

defineProps<{ title: string; description?: string }>()
</script>

<template>
  <div class="container member-layout">
    <MemberSidebar />
    <section class="member-content panel">
      <div class="section-heading">
        <div><h1>{{ title }}</h1><p v-if="description">{{ description }}</p></div>
        <slot name="actions" />
      </div>
      <slot />
    </section>
  </div>
</template>
