<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { inquiryMarketApi, type PublicInquiry } from '@/api/inquiry-market'
import { useAuthStore } from '@/stores/auth'
import { formatDate, normalizeImage } from '@/utils/format'
import { inquiryStatusText } from '@/utils/inquiry-market'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const row = ref<PublicInquiry>()
const busy = ref(true)
const failed = ref(false)
const id = computed(() => Number(route.params.id))

async function load() {
  if (!Number.isSafeInteger(id.value) || id.value <= 0) { failed.value = true; busy.value = false; return }
  busy.value = true; failed.value = false
  try { row.value = await inquiryMarketApi.publicGet(id.value) }
  catch { failed.value = true; row.value = undefined }
  finally { busy.value = false }
}

function quote() {
  if (!auth.isLoggedIn) { void router.push({ path: '/login', query: { redirect: route.fullPath } }); return }
  void router.push({ path: `/inquiries/${id.value}/quote` })
}
onMounted(load)
</script>

<template>
  <div class="container inquiry-detail">
    <MallLink to="/inquiries">← 返回询价专栏</MallLink>
    <div v-if="busy" class="panel loading"><el-skeleton :rows="8" animated /></div>
    <el-result v-else-if="failed || !row" class="panel" icon="error" title="询价单暂时无法查看" sub-title="可能已下架，或公开详情加载失败"><template #extra><el-button @click="load">重新加载</el-button></template></el-result>
    <template v-else>
      <section class="panel overview">
        <div class="title"><div><p><el-tag :type="row.status === 1 ? 'success' : 'info'">{{ inquiryStatusText(row.status) }}</el-tag>　{{ row.no || `询价 #${row.id}` }}</p><h1>{{ row.title }}</h1></div><div class="actions"><el-button v-if="row.capabilities.canQuote" type="primary" @click="quote">{{ auth.isLoggedIn ? '店铺参与报价' : '登录后参与报价' }}</el-button></div></div>
        <dl><div><dt>采购企业</dt><dd>{{ row.enterpriseName || '已认证采购企业' }}</dd></div><div><dt>配送区域</dt><dd>{{ row.addressSummary || '采纳报价后向成交店铺展示收货信息' }}</dd></div><div><dt>报价截止</dt><dd>{{ formatDate(row.deadline) }}</dd></div><div><dt>期望到货</dt><dd>{{ formatDate(row.expectedDate) }}</dd></div></dl>
        <p class="privacy">平台不主动展示结构化联系人、手机号和详细收货地址；发布方不得在公开文字或图片中填写联系方式、详细地址或二维码。采纳与下单均需经过登录及服务端权限校验。</p>
      </section>
      <section class="panel items"><header><h2>采购物料清单</h2><span>{{ row.items.length || row.itemCount }} 项</span></header>
        <div v-if="row.items.length" class="item" v-for="(item,index) in row.items" :key="item.id || index">
          <img v-if="item.picUrl" :src="normalizeImage(item.picUrl)" alt="物料图片" /><div class="placeholder" v-else>暂无图片</div>
          <div><strong>{{ item.name }}</strong><p>{{ [item.category,item.specification].filter(Boolean).join(' · ') || '规格以采购方补充说明为准' }}</p><small v-if="item.code">物料编码：{{ item.code }}</small><small v-if="item.remark">采购备注：{{ item.remark }}</small></div>
          <b>{{ item.quantity }} {{ item.unit }}</b>
        </div>
        <p v-else class="empty">物料清单暂未公开，请稍后重试。</p>
      </section>
      <section class="panel description"><h2>采购说明</h2><p>{{ row.remark || '采购方暂未填写补充说明。' }}</p></section>
    </template>
  </div>
</template>

<style scoped>
.inquiry-detail{margin:30px auto 56px}.inquiry-detail>a{color:#167664}.loading{padding:32px;margin-top:18px}.overview,.items,.description{padding:28px;margin-top:18px}.title{display:flex;justify-content:space-between;gap:20px;align-items:center}.title h1{margin:12px 0 0}.title p{color:#64748b}.actions{display:flex;gap:10px}.overview dl{display:grid;grid-template-columns:repeat(4,1fr);gap:1px;background:#e7eeee;margin:26px 0 0}.overview dl div{background:#f8fbfa;padding:18px}.overview dt{font-size:12px;color:#64748b}.overview dd{margin:8px 0 0;color:#183b39}.privacy{background:#fff8e8;color:#805d18;padding:12px 14px;margin:18px 0 0;font-size:13px}.items header{display:flex;justify-content:space-between;align-items:center}.item{display:grid;grid-template-columns:70px minmax(220px,1fr) 130px minmax(160px,.7fr);gap:18px;align-items:center;padding:18px 0;border-top:1px solid #edf1f0}.item img,.placeholder{width:64px;height:64px;object-fit:contain;background:#f3f5f5}.placeholder{display:grid;place-items:center;color:#94a3b8;font-size:11px}.item p,.item small,.description p{color:#64748b;line-height:1.7}.description p{white-space:pre-wrap}.empty{text-align:center;color:#64748b;padding:42px}@media(max-width:800px){.overview dl{grid-template-columns:repeat(2,1fr)}.item{grid-template-columns:60px 1fr}.title{align-items:flex-start;flex-direction:column}}
</style>
