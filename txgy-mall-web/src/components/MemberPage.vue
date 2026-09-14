<script setup lang="ts">
import { useRoute } from 'vue-router'
import { isPurchasePath } from '@/utils/buyer-navigation'
const route = useRoute()
import MemberSidebar from './MemberSidebar.vue'

defineProps<{ title: string; description?: string }>()
</script>

<template>
  <div class="container member-layout">
    <MemberSidebar v-if="!isPurchasePath(route.path)" />
    <section class="member-content panel">
      <div class="section-heading">
        <div><h1>{{ isPurchasePath(route.path) ? title.replace('我的订单', '采购订单').replace('订单详情', '采购订单详情').replace('退款售后', '采购售后') : title }}</h1><p v-if="description">{{ description }}</p></div>
        <slot name="actions" />
      </div>
      <slot />
    </section>
  </div>
</template>
