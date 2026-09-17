<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useAccountMessageBox } from '@/composables/useAccountOperation'
const ElMessageBox = useAccountMessageBox()
import type { ImportRow, InquiryItem } from '@/api/inquiry'
import { planInquiryImport, type DuplicatePolicy } from '@/utils/inquiry-import'
import { checkInquiryCategory, type InquiryCategoryOption } from '@/utils/inquiry-category'
const props=defineProps<{rows:ImportRow[];existing:InquiryItem[];disabled?:boolean;categories?:InquiryCategoryOption[];categoriesReady?:boolean}>()
const emit=defineEmits<{confirm:[items:InquiryItem[]];cancel:[]}>()
const policy=ref<DuplicatePolicy>('skip'),confirming=ref(false)
const plan=computed(()=>planInquiryImport(props.existing,props.rows,policy.value))
async function confirm(){
 if(confirming.value||props.disabled||plan.value.errorCount||plan.value.total>200)return
 if(!plan.value.additions.length)return void ElMessage.info('没有可新增的明细；重复项已跳过，原数量未改变')
 confirming.value=true
 try{
  await ElMessageBox.confirm(`本次新增${plan.value.additions.length}行，跳过${plan.value.skippedCount}行，加入后共${plan.value.total}行。不会合并或累加重复物品数量，且不会提交询价单。请确认已核对分类、规格、单位及全部提示。`,'确认导入明细',{confirmButtonText:'已核对，加入明细',cancelButtonText:'返回核对',type:'warning'})
  if(props.disabled||plan.value.errorCount||plan.value.total>200)return
  emit('confirm',plan.value.additions)
 }catch{/* 取消确认仅返回预览，不修改明细。 */}finally{confirming.value=false}
}
</script>
<template>
 <section class="inquiry-import-preview">
  <p>现有 {{plan.existingCount}} 行 · Excel {{rows.length}} 行 · 重复 {{plan.duplicateCount}} 行 · 本次新增 {{plan.additions.length}} 行 · 跳过 {{plan.skippedCount}} 行 · 加入后 {{plan.total}} 行</p>
  <fieldset :disabled="confirming||disabled"><legend>重复行处理</legend><label><input v-model="policy" type="radio" value="skip"/> 跳过重复（保留现有明细 / 文件首行）</label><label><input v-model="policy" type="radio" value="keep"/> 保留全部（不合并数量）</label></fieldset>
  <el-alert title="仅识别SKU或名称/编码及分类、规格、单位一致的重复行。数量或备注不同也会标注，请核对后决定是否跳过；不自动换算单位或匹配ERP分类。" :closable="false"/>
  <el-alert v-if="plan.errorCount" type="error" :title="`${plan.errorCount}行存在错误，请修正Excel并重新上传；不静默丢弃错误行`" :closable="false"/>
  <el-alert v-if="plan.total>200" type="error" title="加入后超过200行，请减少文件行数或选择跳过重复" :closable="false"/>
  <el-table :data="plan.rows" max-height="470" empty-text="没有可导入行">
   <el-table-column prop="rowNumber" label="源行" width="65"/><el-table-column prop="item.name" label="物品" min-width="120"/><el-table-column prop="item.code" label="SKU条码" min-width="100"/>
   <el-table-column prop="item.category" label="分类" min-width="100"/><el-table-column prop="item.specification" label="规格" min-width="110"/><el-table-column prop="item.quantity" label="数量" width="90"/><el-table-column prop="item.unit" label="单位" width="75"/><el-table-column prop="item.remark" label="备注" min-width="100"/>
   <el-table-column label="处理结果" min-width="160"><template #default="{row}"><strong>{{row.skipped?'跳过':'加入'}}</strong><p>{{row.duplicate}}</p><p v-if="row.different" class="warning">数量或备注不同，不会合并</p></template></el-table-column>
   <el-table-column label="匹配 / 校验提示" min-width="220"><template #default="{row}"><p>{{row.matchStatus}}</p><p class="category-check">{{checkInquiryCategory(row.item.category,categories||[],!!categoriesReady)}}</p><p class="error">{{row.errors.join('；')}}</p><p>{{row.warnings.join('；')}}</p></template></el-table-column>
  </el-table>
  <footer><el-button :disabled="confirming" @click="emit('cancel')">取消导入</el-button><el-button type="primary" :disabled="disabled||!!plan.errorCount||plan.total>200||!plan.additions.length" :loading="confirming" @click="confirm">确认加入（不提交）</el-button></footer>
 </section>
</template>
<style scoped>fieldset{border:1px solid #dbe6e2;padding:12px;margin:12px 0}label{display:inline-flex;align-items:center;gap:8px;margin-right:24px}p{margin:6px 0;overflow-wrap:anywhere}.warning{color:#a16207}.error{color:#dc2626}footer{display:flex;justify-content:flex-end;gap:12px;margin-top:18px}</style>
