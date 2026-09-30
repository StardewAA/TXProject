<script setup lang="ts">
import { useBuyerNavigation } from '@/composables/useBuyerNavigation'
const { buyerTo } = useBuyerNavigation()
import { onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'

import MemberPage from '@/components/MemberPage.vue'
import ServiceEntry from '@/components/ServiceEntry.vue'
import { useOrderComments } from '@/composables/useOrderComments'
import { normalizeImage, propertyText } from '@/utils/format'

const route = useRoute()
const router = useRouter()
const orderId = String(route.params.id)
const flow = useOrderComments(orderId)
const { forms, order, loading, submitting, error, needsRefresh, load } = flow

async function submit() {
  if (await flow.submit() && flow.current()) {
    ElMessage.success('评价发布成功')
    await router.replace(buyerTo(`/member/orders/${orderId}`))
  }
}

onMounted(load)
</script>

<template>
  <MemberPage title="评价商品" description="仅显示当前订单尚未评价的商品；已成功的评价不会重复提交">
    <template #actions><MallLink :to="buyerTo(`/member/orders/${orderId}`)">返回订单</MallLink></template>
    <el-alert v-if="error" :title="error" type="warning" :closable="false" />
    <el-button :disabled="submitting || loading" @click="load">刷新核验待评价项目</el-button>
    <ServiceEntry v-if="order && !loading && !needsRefresh" resource-type="ORDER" :resource-id="order.id" :disabled="submitting" />
    <div v-if="loading" class="loading-box"><el-skeleton :rows="8" animated /></div>
    <el-empty v-else-if="!forms.length && !needsRefresh" :description="order?.status === 30 ? '本订单已无待评价商品' : '订单当前状态不能评价，请确认收货后再试'" />
    <div v-else class="comment-forms">
      <article v-for="form in forms" :key="form.item.id">
        <div class="comment-product"><div><img v-if="form.item.picUrl" :src="normalizeImage(form.item.picUrl)" alt="商品" /></div><section><strong>{{ form.item.spuName || '商品' }}</strong><small>{{ propertyText(form.item.properties) }}</small></section></div>
        <el-form label-position="top" :disabled="submitting || needsRefresh">
          <el-form-item label="商品符合度"><el-rate v-model="form.descriptionScores" /></el-form-item>
          <el-form-item label="服务满意度"><el-rate v-model="form.benefitScores" /></el-form-item>
          <el-form-item label="评价内容"><el-input v-model="form.content" type="textarea" :rows="4" maxlength="500" show-word-limit /></el-form-item>
          <el-form-item label="评价图片地址"><el-input v-model="form.picUrlsText" placeholder="可选，最多 9 个地址，以逗号分隔" /></el-form-item>
          <el-checkbox v-model="form.anonymous">匿名评价</el-checkbox>
        </el-form>
      </article>
      <el-button v-if="forms.length" type="primary" size="large" :disabled="needsRefresh || loading" :loading="submitting" @click="submit">发布剩余评价（{{ forms.length }}）</el-button>
    </div>
  </MemberPage>
</template>

<style scoped lang="scss">.comment-forms article { display: grid; grid-template-columns: 280px 1fr; gap: 28px; margin-bottom: 18px; padding: 22px; border: 1px solid var(--line); }.comment-product { display: flex; gap: 14px; }.comment-product > div { width: 76px; height: 76px; background: #f5f5f5; }.comment-product img { width: 100%; height: 100%; object-fit: cover; }.comment-product small { display: block; margin-top: 10px; color: var(--muted); }</style>
