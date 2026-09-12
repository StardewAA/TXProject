<script setup lang="ts">
import PurchaseLayout from './PurchaseLayout.vue'
import { isPurchasePath } from '@/utils/buyer-navigation'
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

import { useAuthStore } from '@/stores/auth'
import { useCartStore } from '@/stores/cart'

const router = useRouter()
const authStore = useAuthStore()
const cartStore = useCartStore()
const isMemberArea = computed(() => router.currentRoute.value.path.startsWith('/member'))
const searchMode = ref('goods')
function search(event: Event) {
  const q = String(new FormData(event.target as HTMLFormElement).get('keyword') || '').trim()
  void router.push({ path: searchMode.value === 'shops' ? '/shops' : '/search', query: q ? { q } : {} })
}

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
          <RouterLink to="/purchase">采购中心</RouterLink>
          <RouterLink to="/sales">销售中心</RouterLink>
          <RouterLink to="/member/after-sales">售后服务</RouterLink>
          <RouterLink to="/member/customer-service">在线客服</RouterLink>
        </nav>
      </div>
    </div>

    <header v-if="!isMemberArea" class="main-header">
      <div class="container main-header__inner">
        <RouterLink class="brand" to="/">
          <img class="brand__mark" src="/mall-logo.png" alt="天信管业" />
          <span><strong>防腐保温</strong><small>工业材料一站式采购</small></span>
        </RouterLink>
        <div class="header-discovery"><form class="header-search" @submit.prevent="search">
          <select v-model="searchMode" aria-label="搜索范围"><option value="goods">商品</option><option value="shops">店铺</option></select>
          <input name="keyword" aria-label="搜索关键词" :value="router.currentRoute.value.query.q" :placeholder="searchMode === 'shops' ? '搜索店铺或企业名称' : '搜索防腐材料、保温材料、管件及施工配套'" />
          <button type="submit">搜索</button>
        </form><div class="header-hotwords"><span>热门选材</span><RouterLink v-for="word in ['防腐', '岩棉', '玻璃棉', '硅酸铝', '管件']" :key="word" :to="{ path: '/goods', query: { keyword: word } }">{{ word }}</RouterLink></div></div>
        <RouterLink class="cart-entry" to="/cart">
          <span>购物车</span>
          <b>{{ cartStore.count }}</b>
        </RouterLink>
      </div>
      <div class="primary-nav">
        <nav class="container">
          <RouterLink to="/categories">全部商品分类</RouterLink>
          <RouterLink to="/">首页</RouterLink>
          <RouterLink to="/shops">企业店铺</RouterLink>
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
        <RouterLink class="brand" to="/"><img class="brand__mark" src="/mall-logo.png" alt="天信管业" /><span><strong>防腐保温</strong><small>会员中心</small></span></RouterLink>
        <nav><RouterLink to="/">商城首页</RouterLink><RouterLink to="/member/orders">我的订单</RouterLink><RouterLink to="/member/wallet">我的钱包</RouterLink><RouterLink to="/member/customer-service">在线客服</RouterLink><RouterLink to="/cart">购物车（{{ cartStore.count }}）</RouterLink></nav>
      </div>
    </header>

    <main class="site-main"><PurchaseLayout v-if="isPurchasePath(router.currentRoute.value.path)"><RouterView /></PurchaseLayout><RouterView v-else /></main>

    <footer class="site-footer">
      <div class="container footer-features">
        <div><b>品类齐全</b><span>防腐保温材料集中采购</span></div>
        <div><b>真实库存</b><span>以下单结算结果为准</span></div>
        <div><b>规范交易</b><span>订单全流程线上留痕</span></div>
        <div><b>售后保障</b><span>专业售后流程可追踪</span></div>
      </div>
      <div class="container footer-bottom">防腐保温购物平台 · 为工程选好每一份材料 · 实际商品与服务以平台发布内容为准</div>
    </footer>
  </div>
</template>
