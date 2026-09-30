<script setup lang="ts">
import PurchaseLayout from './PurchaseLayout.vue'
import AccountPage from '@/components/AccountPage.vue'
import OrderWorkbenchBadge from '@/components/OrderWorkbenchBadge.vue'
import { isPurchasePath } from '@/utils/buyer-navigation'
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

import { useAuthStore } from '@/stores/auth'
import { useCartStore } from '@/stores/cart'
import { useComparisonStore } from '@/stores/comparison'
import { getRegistrationEntryPath } from '@/utils/tenant-context'

const router = useRouter()
const authStore = useAuthStore()
const cartStore = useCartStore()
const comparison = useComparisonStore()
const registrationEntryPath = getRegistrationEntryPath()
const isMemberArea = computed(() => router.currentRoute.value.path.startsWith('/member'))
const isMobileDownloadPage = computed(() => router.currentRoute.value.meta.mobileDownload === true)
const searchMode = ref('goods')
function search(event: Event) {
  const q = String(new FormData(event.target as HTMLFormElement).get('keyword') || '').trim()
  void router.push({ name: 'goods-list', query: q ? { keyword: q } : {} })
}

async function logout() {
  await authStore.logout()
  cartStore.reset()
  await router.push('/')
}

onMounted(() => {
  if (authStore.isLoggedIn) void cartStore.fetchCart()
  void comparison.refresh()
})
</script>

<template>
  <div class="site-shell">
    <div v-if="!isMobileDownloadPage" class="service-bar">
      <div class="container service-bar__inner">
        <span>您好，欢迎来到防腐保温购物平台</span>
        <nav>
          <RouterLink to="/">平台首页</RouterLink>
          <template v-if="authStore.isLoggedIn">
            <MallLink to="/member">{{ authStore.profile?.nickname || authStore.profile?.mobile || '个人中心' }}</MallLink>
            <button class="link-button" @click="logout">退出登录</button>
          </template>
          <template v-else>
            <RouterLink to="/platform-login">登录</RouterLink>
            <a :href="registrationEntryPath">免费注册</a>
          </template>
          <MallLink to="/member/orders">我的订单<OrderWorkbenchBadge /></MallLink>
          <MallLink to="/purchase">采购中心</MallLink>
          <MallLink to="/enterprise/profile">企业入驻</MallLink>
          <MallLink to="/compare">商品对比（{{ comparison.count }}）</MallLink>
          <MallLink to="/member/after-sales">售后服务</MallLink>
          <MallLink to="/member/customer-service/scoped">在线客服</MallLink>
        </nav>
      </div>
    </div>

    <header v-if="!isMemberArea && !isMobileDownloadPage" class="main-header">
      <div class="container main-header__inner">
        <MallLink class="brand" to="/">
          <img class="brand__mark" src="/mall-logo.png" alt="天信管业" />
          <span><strong>防腐保温</strong><small>工业材料一站式采购</small></span>
        </MallLink>
        <div class="header-discovery"><form class="header-search" @submit.prevent="search">
          <select v-model="searchMode" aria-label="搜索范围"><option value="goods">全平台</option></select>
          <input name="keyword" aria-label="搜索关键词" :value="router.currentRoute.value.query.keyword || router.currentRoute.value.query.q" placeholder="搜索防腐材料、保温材料、管件及施工配套" />
          <button type="submit">搜索</button>
        </form><div class="header-hotwords"><span>热门选材</span><MallLink v-for="word in ['防腐', '岩棉', '玻璃棉', '硅酸铝', '管件']" :key="word" :to="{ path: '/goods', query: { keyword: word } }">{{ word }}</MallLink></div></div>
        <MallLink class="cart-entry" to="/cart">
          <span>购物车</span>
          <b>{{ cartStore.count }}</b>
        </MallLink>
      </div>
      <div class="primary-nav">
        <nav class="container">
          <RouterLink to="/">全平台商品</RouterLink>
          <RouterLink to="/">首页</RouterLink>
          <MallLink to="/shops">企业店铺</MallLink>
          <MallLink to="/inquiries">询价专栏</MallLink>
          <MallLink to="/marketing">采购优惠</MallLink>
          <MallLink to="/categories">商品分类</MallLink>
          <MallLink to="/download/mobile" class="mobile-download-entry">下载移动端</MallLink>
        </nav>
      </div>
    </header>

    <header v-else-if="!isMobileDownloadPage" class="member-header">
      <div class="container member-header__inner">
        <MallLink class="brand" to="/"><img class="brand__mark" src="/mall-logo.png" alt="天信管业" /><span><strong>防腐保温</strong><small>会员中心</small></span></MallLink>
        <nav><MallLink to="/">商城首页</MallLink><MallLink to="/enterprise/profile">企业入驻</MallLink><MallLink to="/member/orders">我的订单</MallLink><MallLink to="/member/wallet">我的钱包</MallLink><MallLink to="/member/customer-service/scoped">在线客服</MallLink><MallLink to="/cart">购物车（{{ cartStore.count }}）</MallLink></nav>
      </div>
    </header>

    <main :class="['site-main', { 'site-main--download': isMobileDownloadPage }]"><AccountPage v-if="router.currentRoute.value.meta.auth"><PurchaseLayout v-if="isPurchasePath(router.currentRoute.value.path)"><RouterView /></PurchaseLayout><RouterView v-else :key="router.currentRoute.value.name === 'supplier-kefu' ? router.currentRoute.value.fullPath : undefined" /></AccountPage><RouterView v-else /></main>

    <aside v-if="!isMobileDownloadPage && comparison.count && router.currentRoute.value.path !== '/compare'" class="comparison-dock" aria-label="商品对比篮"><i>⇄</i><span><b>商品对比</b>已选 {{ comparison.count }} / 4 个规格</span><MallLink to="/compare">去对比 →</MallLink></aside>

    <footer v-if="!isMobileDownloadPage" class="site-footer">
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

<style scoped>
.comparison-dock{position:fixed;right:28px;bottom:26px;z-index:30;display:flex;gap:14px;align-items:center;padding:13px 15px;border:1px solid #c7dcd7;border-radius:14px;background:#fffffff2;box-shadow:0 14px 38px #123b3330;color:var(--brand);backdrop-filter:blur(12px)}.comparison-dock i{display:grid;place-items:center;width:42px;height:42px;border-radius:11px;background:linear-gradient(145deg,#173943,#0e8672);color:#fff;font-size:21px;font-style:normal}.comparison-dock span{display:flex;flex-direction:column;color:var(--muted);font-size:12px}.comparison-dock span b{margin-bottom:2px;color:#1b403a;font-size:14px}.comparison-dock a{padding:9px 13px;border-radius:8px;background:#0d826f;color:#fff;font-size:13px;font-weight:700}
</style>
