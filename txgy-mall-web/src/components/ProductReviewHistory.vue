<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
import { supplierApi, type ProductReviewRecord } from '@/api/supplier'
import { createProductReviewRequestGate, reviewSnapshotText } from '@/utils/product-review'
import { formatDate } from '@/utils/format'
const props = defineProps<{ shopId: number; productId: number; disabled?: boolean; refreshKey?: string | null }>()
const opened = ref(false), busy = ref(false), failed = ref(false), rows = ref<ProductReviewRecord[]>([])
const gate = createProductReviewRequestGate()
const actions: Record<string, string> = { CREATE: '创建草稿', SAVE: '保存草稿', EDIT: '修改商品', UPDATE: '修改商品', RESTORE: '恢复为待审草稿', SUBMIT: '提交审核', WITHDRAW: '撤回审核', APPROVE: '审核通过', REJECT: '审核驳回', PUBLISH: '上架', UNPUBLISH: '下架' }
function actorLabel(type: string | number) { return type === 2 || type === 'ADMIN' ? '后台人员' : type === 1 || type === 'MEMBER' ? '会员' : type === 0 ? '系统事件' : '操作身份待核对' }
async function load() {
  const ticket = gate.begin()
  busy.value = true; failed.value = false; rows.value = []
  try { const result = await supplierApi.reviewHistory(props.shopId, props.productId); if (gate.isCurrent(ticket)) rows.value = result }
  catch { if (gate.isCurrent(ticket)) failed.value = true }
  finally { if (gate.isCurrent(ticket)) busy.value = false }
}
function open() { if (props.disabled) return; opened.value = true; void load() }
watch(() => [props.shopId, props.productId, props.refreshKey], () => {
  gate.invalidate(); rows.value = []; busy.value = false; failed.value = false
  if (opened.value) void load()
})
watch(opened, value => { if (!value) { gate.invalidate(); rows.value = []; busy.value = false } })
onBeforeUnmount(gate.invalidate)
</script>
<template>
  <el-button link :disabled="disabled" @click="open">审核记录</el-button>
  <el-dialog v-model="opened" title="商品版本与审核记录" width="min(960px, 94vw)" destroy-on-close>
    <p>记录属于当前店铺与商品；快照为当时提交内容，不会覆盖当前库存、订单或商品。</p>
    <el-alert v-if="failed" type="error" title="审核记录读取失败，不代表没有审核记录" :closable="false"><el-button @click="load">重试</el-button></el-alert>
    <div v-loading="busy" class="review-history">
      <el-empty v-if="!busy && !failed && !rows.length" description="暂无审核记录；历史商品不代表已通过审核" />
      <article v-for="row in rows" :key="row.id">
        <h3>{{ actions[row.action] || row.action }} · 内容版本 {{ row.contentVersion }}</h3>
        <p>{{ formatDate(row.createTime) }} · {{ actorLabel(row.actorType) }} #{{ row.actorId }} · 记录 #{{ row.id }}</p>
        <p v-if="row.submissionId">提交批次 #{{ row.submissionId }}</p><p class="remark">{{ row.remark || '未填写补充意见' }}</p>
        <details v-if="row.snapshot"><summary>查看不可变内容快照</summary><pre>{{ reviewSnapshotText(row.snapshot) }}</pre></details>
      </article>
    </div>
  </el-dialog>
</template>
<style scoped>
.review-history{min-height:140px}article{border-bottom:1px solid #dbe6e2;padding:14px 0}h3{margin:0 0 8px}p{color:#64748b;font-size:13px;margin:8px 0}.remark{color:#334155;white-space:pre-wrap}summary{cursor:pointer;color:#167664}pre{max-height:340px;overflow:auto;white-space:pre-wrap;overflow-wrap:anywhere;background:#f4f8f7;padding:16px;font-size:12px}
</style>
