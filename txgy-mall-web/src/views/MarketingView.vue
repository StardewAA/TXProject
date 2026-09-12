<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'

import { promotionApi } from '@/api/mall'
import EmptyState from '@/components/EmptyState.vue'
import { useAuthStore } from '@/stores/auth'
import type { CouponTemplate } from '@/types/mall'
import { formatMoney } from '@/utils/format'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const coupons = ref<CouponTemplate[]>([])
const loading = ref(true)
const takingId = ref<string | number | null>(null)

function discountText(item: CouponTemplate) {
  if (item.discountType === 1) return `减 ¥${formatMoney(item.discountPrice)}`
  const text = `${Number(item.discountPercent || 0) / 10} 折`
  return item.discountLimitPrice ? `${text}，最高减 ¥${formatMoney(item.discountLimitPrice)}` : text
}

function validityText(item: CouponTemplate) {
  if (item.validityType === 1) return `${item.validStartTime || ''} 至 ${item.validEndTime || ''}`
  return `领取后第 ${item.fixedStartTerm || 0} 天生效，有效至第 ${item.fixedEndTerm || 1} 天`
}

async function load() {
  loading.value = true
  try {
    coupons.value = await promotionApi.getCouponTemplates() || []
  } finally {
    loading.value = false
  }
}

async function take(item: CouponTemplate) {
  if (!authStore.isLoggedIn) return router.push({ name: 'login', query: { redirect: route.fullPath } })
  takingId.value = item.id
  try {
    item.canTake = await promotionApi.takeCoupon(item.id)
    ElMessage.success('优惠券领取成功，可在确认订单时使用')
  } finally {
    takingId.value = null
  }
}

onMounted(load)
</script>

<template>
  <div class="container page-section marketing-page">
    <div class="marketing-hero">
      <span>PROCUREMENT BENEFITS</span>
      <h1>采购优惠中心</h1>
      <p>保留企业采购需要的折扣、满额优惠和优惠券，具体优惠以订单结算结果为准</p>
    </div>
    <section id="discount" class="benefit-card">
      <i>折</i>
      <div>
        <h2>采购优惠</h2>
        <p>后台配置的限时折扣会按商品和规格自动匹配。进入确认订单后，系统直接计算活动价，无需单独领取。</p>
        <RouterLink to="/goods">选购商品 →</RouterLink>
      </div>
    </section>
    <section id="reward" class="benefit-card">
      <i>满</i>
      <div>
        <h2>采购满额优惠</h2>
        <p>达到活动要求的采购金额或件数后，自动享受减免、包邮或积分奖励；最终规则会在结算明细中体现。</p>
        <RouterLink to="/cart">前往购物车 →</RouterLink>
      </div>
    </section>
    <section id="coupon" class="coupon-section">
      <div class="section-heading">
        <div><h2>采购优惠券</h2><p>领取后在确认订单页选择适用优惠券</p></div>
      </div>
      <div v-if="loading" class="panel loading-box"><el-skeleton :rows="6" animated /></div>
      <EmptyState v-else-if="!coupons.length" title="暂无可领取优惠券" description="优惠券发布后会在这里展示" />
      <div v-else class="coupon-grid">
        <article v-for="item in coupons" :key="item.id" class="coupon-card">
          <div class="coupon-value">
            <strong>{{ discountText(item) }}</strong>
            <span>{{ item.usePrice ? `满 ¥${formatMoney(item.usePrice)} 可用` : '无门槛' }}</span>
          </div>
          <div class="coupon-info">
            <h3>{{ item.name }}</h3>
            <p>{{ item.description || '适用范围以结算时校验为准' }}</p>
            <small>{{ validityText(item) }}</small>
          </div>
          <button :disabled="!item.canTake || takingId === item.id" @click="take(item)">
            {{ item.canTake ? (takingId === item.id ? '领取中' : '立即领取') : '已达领取上限' }}
          </button>
        </article>
      </div>
    </section>
  </div>
</template>

<style scoped lang="scss">
.marketing-page { padding-bottom: 30px; }
.marketing-hero { padding: 46px 54px; border-radius: 8px; background: linear-gradient(120deg,#28363f,#b64225); color:white; }
.marketing-hero span { letter-spacing:3px; opacity:.72; }
.marketing-hero h1 { margin:10px 0; font-size:36px; }
.marketing-hero p { margin:0; opacity:.84; }
.benefit-card { display:flex; gap:24px; align-items:center; margin-top:20px; padding:28px 34px; border:1px solid var(--line); border-radius:8px; background:white; scroll-margin-top:20px; }
.benefit-card i { flex:0 0 62px; height:62px; display:grid; place-items:center; border-radius:50%; background:#f7e7df; color:var(--brand); font-size:26px; }
.benefit-card h2 { margin:0 0 8px; }
.benefit-card p { margin:0 0 10px; color:var(--muted); }
.benefit-card a { color:var(--brand); }
.coupon-section { margin-top:30px; scroll-margin-top:20px; }
.coupon-grid { display:grid; grid-template-columns:repeat(2,1fr); gap:16px; }
.coupon-card { display:grid; grid-template-columns:170px 1fr 110px; align-items:center; min-height:142px; overflow:hidden; border:1px solid #ead7ce; border-radius:8px; background:white; }
.coupon-value { align-self:stretch; display:flex; flex-direction:column; justify-content:center; padding:22px; background:#fff0e8; color:var(--brand); }
.coupon-value strong { font-size:22px; }
.coupon-value span { margin-top:8px; font-size:13px; }
.coupon-info { padding:20px; }
.coupon-info h3 { margin:0 0 8px; }
.coupon-info p { margin:0 0 12px; color:var(--muted); }
.coupon-info small { color:#929ba2; }
.coupon-card button { margin-right:18px; padding:10px; border:0; border-radius:4px; background:var(--brand); color:white; }
.coupon-card button:disabled { background:#aeb5b9; }
</style>
