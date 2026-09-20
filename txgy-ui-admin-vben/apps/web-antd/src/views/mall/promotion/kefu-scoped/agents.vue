<script setup lang="ts">
import type { ScopedAgent } from '#/api/mall/promotion/kefu-scoped';
import { computed, reactive, ref, watch } from 'vue';
import { Page } from '@vben/common-ui';
import { Alert, Button, Input, InputNumber, Popconfirm, Select, Switch, Tag } from 'ant-design-vue';
import { getScopedAgents, grantScopedAgent } from '#/api/mall/promotion/kefu-scoped';
import { actorName, kefuError, MANAGE_PERMISSION, sameScope, validateGrant } from './model';
import { useScopedView } from './use-scoped-view';

defineOptions({ name: 'MallScopedKefuAgents' });
const rows = ref<ScopedAgent[]>([]), page = ref(1), loading = ref(false), busy = ref(false), loaded = ref(false);
const error = ref(''), notice = ref(''), editing = ref(false), existing = ref(false);
const form = reactive({ actorType: 1 as 1 | 2, actorId: undefined as number | undefined, enabled: true, version: 0, reason: '' });
function clear() {
  rows.value = []; page.value = 1; loading.value = false; busy.value = false; loaded.value = false;
  error.value = ''; notice.value = ''; editing.value = false; existing.value = false;
  Object.assign(form, { actorType: 1, actorId: undefined, enabled: true, version: 0, reason: '' });
}
const context = useScopedView(MANAGE_PERMISSION, clear);
const listFence = context.fence(), writeFence = context.fence();
const pageSize = 20;
const scopeOptions = [{ label: '平台客服（仅本租户）', value: 'PLATFORM' }, { label: '店铺客服', value: 'SHOP' }];
const actorOptions = [{ label: '商城会员 MEMBER（1）', value: 1 }, { label: '后台管理员 ADMIN（2）', value: 2 }];
const payload = computed(() => context.query.value ? { ...context.query.value, ...form, actorId: form.actorId ?? 0, reason: form.reason.trim() } : undefined);
const formError = computed(() => payload.value ? validateGrant(payload.value) : '请选择合法客服范围');
async function load() {
  if (!context.ready.value || !context.query.value) return;
  const query = { ...context.query.value };
  const request = listFence.begin();
  loading.value = true; loaded.value = false; rows.value = []; error.value = '';
  try {
    const data = await getScopedAgents({ ...query, page: page.value, size: pageSize }, request.signal);
    if (!request.current()) return;
    if (!Array.isArray(data) || data.some((row) => !sameScope(row, query))) throw new Error('Invalid scope');
    rows.value = data; loaded.value = true;
  } catch (cause) { if (request.current()) error.value = kefuError(cause); }
  finally { if (request.current()) loading.value = false; }
}
function newAgent() {
  if (!context.ready.value || !loaded.value || busy.value) return;
  Object.assign(form, { actorType: 1, actorId: undefined, enabled: true, version: 0, reason: '' });
  existing.value = false; editing.value = true; error.value = ''; notice.value = '';
}
function edit(row: ScopedAgent) {
  if (!context.ready.value || busy.value || !context.query.value || !sameScope(row, context.query.value)) return;
  Object.assign(form, { actorType: row.actorType, actorId: row.actorId, enabled: !row.enabled, version: row.version, reason: '' });
  existing.value = true; editing.value = true; error.value = ''; notice.value = '';
}
async function save() {
  if (!context.ready.value || !payload.value || formError.value || busy.value) return;
  const data = { ...payload.value };
  const request = writeFence.begin();
  busy.value = true; error.value = ''; notice.value = '';
  try {
    await grantScopedAgent(data, request.signal);
    if (!request.current()) return;
    editing.value = false;
    notice.value = data.enabled ? '授权已保存。该账号仍须满足登录、账号及店铺状态检查，管理员接待还需要 receive 权限。'
      : '授权已撤销；后端会将该坐席未结束的会话退回待接待，并记录审计。';
    await load();
  } catch (cause) { if (request.current()) error.value = kefuError(cause); }
  finally { if (request.current()) busy.value = false; }
}
function turn(value: number) { if (loading.value || busy.value) return; page.value = value; editing.value = false; void load(); }
watch(context.ready, (ready) => { if (ready) void load(); });
</script>

<template>
  <Page title="店铺范围客服 · 坐席授权" description="授权按本租户、平台/店铺范围、账号类型分别生效；供应商身份不自动获得客服权限。">
    <section class="scoped-panel bg-card">
      <Alert type="info" show-icon message="仅修改客服独立授权，不修改账号角色、商品权限或企业资格。会员与管理员即使编号相同也不是同一个坐席。" />
      <div class="scoped-toolbar">
        <strong>{{ context.label.value }}</strong>
        <Select v-model:value="context.scope.value" :options="scopeOptions" :disabled="busy" style="width: 220px" aria-label="客服范围" />
        <InputNumber v-if="context.scope.value === 'SHOP'" v-model:value="context.shopId.value" :min="1" :precision="0" :disabled="busy" placeholder="店铺编号" aria-label="店铺编号" />
        <Button :loading="context.capabilityLoading.value" @click="context.refreshCapability">核实开通状态</Button>
      </div>
      <Alert v-if="context.blocked.value" type="warning" show-icon :message="context.blocked.value" />
      <template v-if="context.ready.value">
        <div class="scoped-toolbar">
          <Button :loading="loading" :disabled="busy" @click="load">刷新名单</Button>
          <Button type="primary" :disabled="!loaded || loading || busy" @click="newAgent">新增独立坐席授权</Button>
          <span class="muted">不提供批量授予或默认全店授权。</span>
        </div>
        <Alert v-if="error" type="error" show-icon :message="error" />
        <Alert v-if="notice" type="success" show-icon :message="notice" />
        <div class="table-wrap">
          <table class="scoped-table">
            <thead><tr><th>账号类型</th><th>账号编号</th><th>当前状态</th><th>授权版本</th><th>操作</th></tr></thead>
            <tbody>
              <tr v-for="row in rows" :key="`${row.actorType}-${row.actorId}`">
                <td>{{ actorName(row.actorType) }}</td><td>{{ row.actorId }}</td>
                <td><Tag :color="row.enabled ? 'green' : 'default'">{{ row.enabled ? '已授权' : '已撤销' }}</Tag></td><td>{{ row.version }}</td>
                <td><Button size="small" :danger="row.enabled" :disabled="busy || loading" @click="edit(row)">{{ row.enabled ? '撤销授权' : '重新授权' }}</Button></td>
              </tr>
              <tr v-if="!rows.length"><td colspan="5">{{ loading ? '正在读取授权名单…' : loaded ? '该页没有独立坐席授权。' : '未成功读取，不代表没有授权。' }}</td></tr>
            </tbody>
          </table>
        </div>
        <div class="scoped-toolbar">
          <Button :disabled="page <= 1 || loading || busy" @click="turn(page - 1)">上一页</Button><span>第 {{ page }} 页 · 每页 {{ pageSize }} 条</span>
          <Button :disabled="!loaded || rows.length < pageSize || page >= 10000 || loading || busy" @click="turn(page + 1)">下一页</Button>
        </div>
        <section v-if="editing" class="editor" aria-label="坐席授权变更表单">
          <h3>{{ existing ? '变更现有授权' : '新建坐席授权' }}</h3>
          <p class="muted">{{ context.label.value }}。新授权使用版本 0；已存在的账号请从名单选择，不覆盖并发变更。</p>
          <div class="scoped-toolbar">
            <label>账号类型 <Select v-model:value="form.actorType" :options="actorOptions" :disabled="existing || busy" style="width: 230px" /></label>
            <label>账号编号 <InputNumber v-model:value="form.actorId" :min="1" :precision="0" :disabled="existing || busy" aria-label="坐席账号编号" /></label>
            <label>授予权限 <Switch v-model:checked="form.enabled" :disabled="busy" /></label>
            <span>版本 {{ form.version }}</span>
          </div>
          <label>变更原因（必填）<Input.TextArea v-model:value="form.reason" :maxlength="500" :rows="3" :disabled="busy" placeholder="记录本次授予或撤销原因，不填写账号密码等秘密" /></label>
          <p v-if="formError" class="muted">{{ formError }}</p>
          <div class="scoped-toolbar">
            <Popconfirm :title="`确认${form.enabled ? '授予' : '撤销'} ${actorName(form.actorType)} #${form.actorId ?? '未填写'} 在此范围的客服权限？`" :disabled="!!formError || busy" @confirm="save">
              <Button type="primary" :danger="!form.enabled" :loading="busy" :disabled="!!formError">确认保存授权</Button>
            </Popconfirm>
            <Button :disabled="busy" @click="editing = false">取消</Button>
          </div>
        </section>
      </template>
    </section>
  </Page>
</template>

<style scoped>
.scoped-panel{padding:20px;display:grid;gap:16px;border-radius:8px}.scoped-toolbar{display:flex;align-items:center;gap:12px;flex-wrap:wrap}.table-wrap{overflow:auto}.scoped-table{width:100%;border-collapse:collapse;text-align:left}.scoped-table th,.scoped-table td{padding:14px 12px;border-bottom:1px solid #8883}.editor{border:1px solid #8884;border-radius:8px;padding:20px;display:grid;gap:14px}.editor h3{font-weight:600;font-size:18px}.muted{color:#7b8493;font-size:13px}
</style>
