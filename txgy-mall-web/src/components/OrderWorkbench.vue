<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { orderWorkbenchApi, type TodoAction, type TodoEntry, type WorkbenchRole } from '@/api/order-workbench'
import { useOrderWorkbench } from '@/composables/useOrderWorkbench'
import { todoActionNames, workbenchDestination } from '@/utils/order-workbench'
import { formatDate } from '@/utils/format'

const props = withDefaults(defineProps<{ role?: WorkbenchRole; shopId?: number; navigationBlocked?: boolean }>(), { role: 'BUYER', navigationBlocked: false })
const router = useRouter(), route = useRoute()
const workbench = useOrderWorkbench(() => props.role, () => props.shopId)
const state = workbench.state
const title = computed(() => props.role === 'SUPPLIER' ? `当前店铺 #${props.shopId} 全部待办` : '本人全部订单待办')
const categories = computed(() => Object.entries(state.value.snapshot?.todoCounts || {}).map(([action, count]) => ({ action: action as TodoAction, count: Number(count) })))
const opened = ref(false), page = ref(1), action = ref<TodoAction>(), unreadOnly = ref(false)
const rows = ref<TodoEntry[]>([]), total = ref(0), loading = ref(false), failed = ref(''), reading = ref(false)
let version = 0
async function load() {
  const current = ++version, key = workbench.key.value
  if (!key) return
  loading.value = true; failed.value = ''
  try {
    const result = await orderWorkbenchApi.page({ ...workbench.scope.value, pageNo: page.value, pageSize: 15, action: action.value, unreadOnly: unreadOnly.value })
    if (current !== version || key !== workbench.key.value) return
    rows.value = result.list; total.value = result.total
  } catch (error) { if (current === version) failed.value = error instanceof Error ? error.message : '待办加载失败' }
  finally { if (current === version) loading.value = false }
}
function show(selected?: TodoAction) { action.value = selected; page.value = 1; unreadOnly.value = false; opened.value = true; workbench.dismissBanner(); void load() }
async function read(entries: TodoEntry[]) {
  const key = workbench.key.value
  reading.value = true; failed.value = ''
  try {
    if (!await workbench.read(entries) || key !== workbench.key.value) return false
    await load()
    return !failed.value && key === workbench.key.value
  }
  catch (error) { if(key===workbench.key.value)failed.value = error instanceof Error ? error.message : '标记已读失败，请重试'; return false }
  finally { reading.value = false }
}
async function openId(eventId: string) {
  const entry = rows.value.find(row=>row.eventId===eventId)
  if (!entry) return
  if (props.navigationBlocked || state.value.error) return
  const key = workbench.key.value
  const target = workbenchDestination(entry, props.role, route.path.startsWith('/purchase') ? '/purchase' : '/member', props.shopId)
  if (!await read([entry]) || key!==workbench.key.value || props.navigationBlocked) return
  opened.value = false
  await router.push(target)
}
watch(workbench.key, () => { ++version; opened.value = false; rows.value = []; total.value = 0; failed.value = '' }, { flush: 'sync' })
watch(() => state.value.updatedAt, () => { if (opened.value && !reading.value) void load() })
</script>
<template>
  <section v-if="workbench.key.value" class="order-workbench" :aria-busy="state.loading">
    <header><h2>{{ title }}</h2><button type="button" :disabled="state.loading" @click="workbench.refresh">刷新待办</button></header>
    <div class="totals"><strong>待处理事项 <b>{{ state.snapshot?.pendingCount ?? '—' }}</b></strong><span>未读提醒 {{ state.snapshot?.unreadCount ?? '—' }}</span><button type="button" :disabled="!state.snapshot || !!state.error || navigationBlocked" @click="show()">查看待办</button></div>
    <p class="scope">全部授权范围，不受下方状态、搜索或分页限制；阅读提醒不会减少待处理事项。</p>
    <div class="category-links"><button v-for="item in categories" :key="item.action" type="button" :disabled="!!state.error || navigationBlocked" @click="show(item.action)">{{ todoActionNames[item.action] }} <b>{{ item.count }}</b></button></div>
    <div v-if="state.newCount > 0" class="new-banner" role="status"><strong>新增 {{ state.newCount }} 条待处理事项</strong><button type="button" :disabled="!!state.error || navigationBlocked" @click="show()">点击查看</button><button type="button" @click="workbench.dismissBanner">关闭提示</button><small>当前待办以最新服务端结果为准</small></div>
    <p v-if="navigationBlocked" class="scope">正在编辑/查看业务详情，待办仍会更新；请先关闭详情再跳转，不会覆盖已填写内容。</p>
    <p v-if="state.error" class="failed" role="status">暂无法更新待办：{{ state.error }}。保留上次结果，请刷新后再处理。</p>
    <small>{{ state.updatedAt ? `最近更新 ${formatDate(state.updatedAt)}` : '尚未取得待办数据' }} · 在线约 {{ Math.round(state.interval / 1000) }} 秒更新 · 无声音提醒</small>
  </section>
  <el-dialog v-model="opened" :title="`${title} · 事项列表`" width="850px">
    <div class="todo-filters"><el-select v-model="action" clearable placeholder="全部事项类型" @change="page=1;load()"><el-option v-for="(label,value) in todoActionNames" :key="value" :label="label" :value="value"/></el-select><el-checkbox v-model="unreadOnly" @change="page=1;load()">仅未读</el-checkbox><el-button :disabled="reading || !!failed || !rows.some(row=>row.unread)" @click="read(rows)">本页已展示提醒标为已读</el-button></div>
    <el-alert v-if="failed" :title="failed" type="error" :closable="false"/>
    <p class="scope">这里按待处理事项计数；同一订单可能关联不同售后或处理阶段。标记已读不执行发货、退款或收货。</p>
    <el-table :data="rows" v-loading="loading" empty-text="暂无匹配的待处理事项">
      <el-table-column label="提醒" width="70"><template #default="{row}"><strong v-if="row.unread" class="new-label">新</strong><span v-else>已读</span></template></el-table-column>
      <el-table-column label="事项" min-width="140"><template #default="{row}">{{todoActionNames[row.action as TodoAction]}}</template></el-table-column>
      <el-table-column prop="no" label="关联单号" min-width="180"/>
      <el-table-column prop="reason" label="处理说明" min-width="180"/>
      <el-table-column label="操作" width="160"><template #default="{row}"><el-button link :disabled="reading || navigationBlocked || !!state.error" @click="openId(String(row.eventId))">查看详情并标已读</el-button></template></el-table-column>
    </el-table>
    <el-pagination v-model:current-page="page" :page-size="15" :total="total" layout="prev,pager,next,total" @current-change="load"/>
  </el-dialog>
</template>
<style scoped>
.order-workbench{border:1px solid #c7dcd4;border-radius:8px;background:#f6fbf8;padding:18px;margin:0 0 20px}.order-workbench header,.totals,.category-links,.new-banner,.todo-filters{display:flex;align-items:center;gap:14px;flex-wrap:wrap}.order-workbench header{justify-content:space-between}.order-workbench h2{font-size:17px;margin:0 0 10px}.totals b{font-size:23px;margin-left:6px;color:#176953}.order-workbench button{border:1px solid #b5d4c6;background:#fff;color:#155c4a;border-radius:5px;padding:7px 11px;cursor:pointer}.order-workbench button:disabled{opacity:.55;cursor:not-allowed}.order-workbench button:focus-visible{outline:2px solid #145f4b;outline-offset:2px}.scope,.order-workbench small{font-size:12px;color:#64748b;line-height:1.6}.category-links{gap:8px;margin:10px 0}.new-banner{padding:12px;background:#fff4d6;border:1px solid #dfae4d;margin:12px 0}.failed{color:#a12e1a}.new-label{display:inline-block;border:1px solid #a74410;color:#a74410;padding:1px 5px;border-radius:3px;font-size:12px}.todo-filters{margin-bottom:14px}.todo-filters .el-select{width:200px}.el-pagination{margin-top:16px}@media(max-width:700px){.order-workbench{padding:14px}.totals{gap:10px}.category-links button{flex:1 1 140px}}
</style>
