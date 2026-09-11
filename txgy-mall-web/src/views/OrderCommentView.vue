<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'

import { tradeApi } from '@/api/mall'
import MemberPage from '@/components/MemberPage.vue'
import type { OrderItem, TradeOrder } from '@/types/mall'
import { normalizeImage, propertyText } from '@/utils/format'

interface CommentForm { item: OrderItem; descriptionScores: number; benefitScores: number; content: string; anonymous: boolean; picUrlsText: string }
const route = useRoute()
const router = useRouter()
const order = ref<TradeOrder | null>(null)
const forms = reactive<CommentForm[]>([])
const loading = ref(true)
const submitting = ref(false)

async function submit() {
  if (forms.some((form) => !form.content.trim())) return ElMessage.warning('请填写每件商品的评价内容')
  submitting.value = true
  try {
    for (const form of forms) {
      await tradeApi.createComment({ anonymous: form.anonymous, orderItemId: form.item.id, descriptionScores: form.descriptionScores, benefitScores: form.benefitScores, content: form.content.trim(), picUrls: form.picUrlsText.split(/[\n,，]/).map((value) => value.trim()).filter(Boolean).slice(0, 9) })
    }
    ElMessage.success('评价发布成功')
    await router.replace(`/member/orders/${route.params.id}`)
  } finally { submitting.value = false }
}

onMounted(async () => {
  try {
    order.value = await tradeApi.getOrder(String(route.params.id))
    for (const item of order.value.items || []) forms.push({ item, descriptionScores: 5, benefitScores: 5, content: '', anonymous: false, picUrlsText: '' })
  } finally { loading.value = false }
})
</script>

<template><MemberPage title="评价商品" description="评价提交后将显示在商品详情页"><div v-if="loading" class="loading-box"><el-skeleton :rows="8" animated /></div><el-empty v-else-if="!forms.length" description="没有可评价的商品" /><div v-else class="comment-forms"><article v-for="form in forms" :key="form.item.id"><div class="comment-product"><div><img v-if="form.item.picUrl" :src="normalizeImage(form.item.picUrl)" alt="商品" /></div><section><strong>{{ form.item.spuName || '商品' }}</strong><small>{{ propertyText(form.item.properties) }}</small></section></div><el-form label-position="top"><el-form-item label="商品符合度"><el-rate v-model="form.descriptionScores" /></el-form-item><el-form-item label="服务满意度"><el-rate v-model="form.benefitScores" /></el-form-item><el-form-item label="评价内容"><el-input v-model="form.content" type="textarea" :rows="4" maxlength="500" show-word-limit /></el-form-item><el-form-item label="评价图片地址"><el-input v-model="form.picUrlsText" placeholder="可选，最多 9 个地址，以逗号分隔" /></el-form-item><el-checkbox v-model="form.anonymous">匿名评价</el-checkbox></el-form></article><el-button type="primary" size="large" :loading="submitting" @click="submit">发布全部评价</el-button></div></MemberPage></template>

<style scoped lang="scss">.comment-forms article { display: grid; grid-template-columns: 280px 1fr; gap: 28px; margin-bottom: 18px; padding: 22px; border: 1px solid var(--line); }.comment-product { display: flex; gap: 14px; }.comment-product > div { width: 76px; height: 76px; background: #f5f5f5; }.comment-product img { width: 100%; height: 100%; object-fit: cover; }.comment-product small { display: block; margin-top: 10px; color: var(--muted); }</style>
