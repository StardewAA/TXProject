<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'

import { useAuthStore } from '@/stores/auth'
import { useCartStore } from '@/stores/cart'

const router = useRouter()
const authStore = useAuthStore()
const cartStore = useCartStore()
const isMemberArea = computed(() => router.currentRoute.value.path.startsWith('/member'))

async function logout() {
  await authStore.logout()
  cartStore.reset()
  await router.push('/')
}

onMounted(() => {
  if (authStore.isLoggedIn) void cartStore.fetchCart()
})
</script>

<template>
  <div class="site-shell">
    <div class="service-bar">
      <div class="container service-bar__inner">
        <span>您好，欢迎来到防腐保温购物平台</span>
        <nav>
          <template v-if="authStore.isLoggedIn">
            <RouterLink to="/member">{{ authStore.profile?.nickname || authStore.profile?.mobile || '个人中心' }}</RouterLink>
            <button class="link-button" @click="logout">退出登录</button>
          </template>
          <template v-else>
            <RouterLink to="/login">登录</RouterLink>
            <RouterLink to="/register">免费注册</RouterLink>
          </template>
          <RouterLink to="/member/orders">我的订单</RouterLink>
          <RouterLink to="/member/after-sales">售后服务</RouterLink>
          <RouterLink to="/member/customer-service">在线客服</RouterLink>
        </nav>
      </div>
    </div>

    <header v-if="!isMemberArea" class="main-header">
      <div class="container main-header__inner">
        <RouterLink class="brand" to="/">
          <span class="brand__mark">TX</span>
          <span><strong>防腐保温</strong><small>工业材料一站式采购</small></span>
        </RouterLink>
        <form class="header-search" @submit.prevent="router.push({ path: '/search', query: { q: ($event.target as HTMLFormElement).keyword.value } })">
          <input name="keyword" :value="router.currentRoute.value.query.q" placeholder="搜索防腐材料、保温材料、管件及施工配套" />
          <button type="submit">搜索</button>
        </form>
        <RouterLink class="cart-entry" to="/cart">
          <span>购物车</span>
          <b>{{ cartStore.count }}</b>
        </RouterLink>
      </div>
      <div class="primary-nav">
        <nav class="container">
          <RouterLink to="/categories">全部商品分类</RouterLink>
          <RouterLink to="/">首页</RouterLink>
          <RouterLink to="/marketing">采购优惠</RouterLink>
          <RouterLink :to="{ path: '/goods', query: { keyword: '防腐' } }">防腐材料</RouterLink>
          <RouterLink :to="{ path: '/goods', query: { keyword: '保温' } }">保温材料</RouterLink>
          <RouterLink :to="{ path: '/goods', query: { keyword: '管件' } }">管材管件</RouterLink>
          <RouterLink :to="{ path: '/goods', query: { keyword: '施工' } }">施工配套</RouterLink>
        </nav>
      </div>
    </header>

    <header v-else class="member-header">
      <div class="container member-header__inner">
        <RouterLink class="brand" to="/"><span class="brand__mark">TX</span><span><strong>防腐保温</strong><small>会员中心</small></span></RouterLink>
        <nav><RouterLink to="/">商城首页</RouterLink><RouterLink to="/member/orders">我的订单</RouterLink><RouterLink to="/member/wallet">我的钱包</RouterLink><RouterLink to="/member/customer-service">在线客服</RouterLink><RouterLink to="/cart">购物车（{{ cartStore.count }}）</RouterLink></nav>
      </div>
    </header>

    <main class="site-main"><RouterView /></main>

    <footer class="site-footer">
      <div class="container footer-features">
        <div><b>品类齐全</b><span>防腐保温材料集中采购</span></div>
        <div><b>真实库存</b><span>以下单结算结果为准</span></div>
        <div><b>规范交易</b><span>订单全流程线上留痕</span></div>
        <div><b>售后保障</b><span>专业售后流程可追踪</span></div>
      </div>
      <div class="container footer-bottom">防腐保温购物平台 · 商城公共租户 1 · 实际商品与服务以平台发布内容为准</div>
    </footer>
  </div>
</template>
