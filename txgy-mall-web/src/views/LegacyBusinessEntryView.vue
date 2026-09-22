<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()
const isSalesEntry = computed(() => route.path === '/sales' || route.path.startsWith('/sales/'))

function continueInMall() {
  void router.push(isSalesEntry.value ? '/' : '/purchase/inquiries')
}
</script>

<template>
  <section class="container legacy-business-entry panel">
    <div class="entry-mark" aria-hidden="true">{{ isSalesEntry ? '营' : '询' }}</div>
    <small>BUSINESS ENTRY UPDATED</small>
    <h1>{{ isSalesEntry ? '供应经营入口已迁移至后台管理端' : '企业询价已调整为授权业务入口' }}</h1>
    <p v-if="isSalesEntry">PC 商城用户端不再提供销售中心。店铺资料、商品经营、询价机会与报价、销售订单、售后、客服和结算，请使用平台为企业配置的后台账号办理。</p>
    <p v-else>采购企业请登录后从“采购中心”发布和管理询价、查看报价及沟通；供应企业请在后台“销售管理—询价机会”中查看并响应，公开页面不再展示正式询价内容。</p>
    <el-alert v-if="isSalesEntry" title="商城会员登录态不能转换为后台管理权限；如尚未取得后台账号或店铺授权，请联系当前租户管理员。" type="warning" :closable="false" show-icon />
    <el-alert v-else title="只有审核有效企业的采购成员可以发布询价；其他会员和匿名用户不能读取正式询价内容。" type="info" :closable="false" show-icon />
    <div class="entry-actions">
      <el-button type="primary" @click="continueInMall">{{ isSalesEntry ? '返回商城首页' : '进入采购中心—我的询价' }}</el-button>
      <MallLink to="/enterprise/profile">企业入驻 / 资质认证</MallLink>
      <MallLink to="/member/customer-service/scoped">联系平台客服</MallLink>
    </div>
  </section>
</template>

<style scoped>
.legacy-business-entry{max-width:920px;margin:56px auto;padding:52px;text-align:center}.entry-mark{display:grid;place-items:center;width:68px;height:68px;margin:0 auto 18px;border-radius:20px;background:linear-gradient(145deg,#173f49,#0f806d);color:#fff;font-size:28px;font-weight:800;box-shadow:0 12px 28px #123e4628}.legacy-business-entry>small{color:#16806d;font-weight:800;letter-spacing:2px}.legacy-business-entry h1{margin:12px 0 18px;color:#17343c;font-size:30px}.legacy-business-entry>p{max-width:720px;margin:0 auto 24px;color:#64748b;line-height:1.9}.legacy-business-entry :deep(.el-alert){margin:0 auto;max-width:760px;text-align:left}.entry-actions{display:flex;justify-content:center;align-items:center;gap:22px;flex-wrap:wrap;margin-top:28px}.entry-actions a{color:#167664;font-weight:700}
</style>
