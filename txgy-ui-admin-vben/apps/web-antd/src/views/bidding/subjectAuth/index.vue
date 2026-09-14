<!-- ============================================================================
  平台授权管理（投标人 / 招标人主体资格台账）· 管理端
  页面定位（业务）：对基座既有企业租户逐一判定“是否具备投标人/招标人主体资格”——
    资格具备 → 平台授权（ENABLED），该企业可投标/招标；
    资格不具备 → 平台不授权（撤销授权 / 未授权），该企业不可参与（参与动作被资格门拒绝）。
    不建租户（租户=基座 system 职责）、不授菜单权限（按钮显隐=系统管理·角色授权职责）、
    不经手具体项目资格评审（某项目够不够格/废标与否是招标人资格预审与专家评审团的事）。
  理由：主体=企业（法人/其他组织）粒度；人员只是企业下经办自然人（经办账号由企业自管，平台不代管）。
  依据（法规，含文件类型）：
    · 招标投标法 第25/26条（法律）——投标人是法人或其他组织；投标人应当具备招标文件/国家规定的资格条件；
    · 电子招标投标办法 第24/14/13条（部门规章，八部委令第20号 2013）——投标人注册登记如实递交并经运营机构验证；
    · 实施条例 第19条（行政法规）——未通过资格预审的申请人不具有投标资格（资格认定在项目层，不在本台账）；
    · 招标公告和公示信息发布管理办法 第5条（部门规章，发改委令第10号）——公告载明事项含资格要求。
  后端契约（嵌套仓库 docs/）：
    · 前端规范-全域接口说明/07-主体域前端交付-业务口径说明.md §3/§5.2
    · 外部公告-前端对接/03-页面结构与建议.md §5.2（授权 authorize / 状态 status）
    · 前端规范-全域接口说明/04-全链路接口说明.md 链路A-A2、链路D-D3
    · 主体信息与授权-设计说明.md 二（企业=租户；资格门=授权∧档案完善）
  权限点：bidding:subject-auth:query / update（见 07 文档 §7 与 sql/2026-09-09-subject-menu.sql）。
============================================================================ -->

<script lang="ts" setup>
import { computed, onMounted, reactive, ref } from 'vue';

import { Button, Drawer, Input, message, Modal, Radio, Select, Space, Switch, Table, Tag } from 'ant-design-vue';

import { SubjectApi } from '#/api/bidding/subject';
import {
  authorizeSubject,
  fetchSubjectAuthEligibility,
  fetchSubjectAuthPage,
  updateSubjectAuthStatus,
} from '#/api/bidding/subject';
import { requestClient } from '#/api/request';

import { useCertMeta } from '../certificate/useCertMeta';
import { eligibleReasonText } from '../subject/labels';
import InfoCell from './modules/InfoCell.vue';
import SpinWrap from './modules/SpinWrap.vue';

const createMessage = message;

/* ===== 主体类型文案与选项：**字典优先、SUBJECT_TYPE_LABEL 兜底** =====
 * 用户硬约束「状态码、枚举值必须支持动态配置」——字典是主路径；兜底保证拿不到字典时仍是中文。
 * 为什么选项要**过滤 AGENCY**：SubjectTypeEnum 注释写明 AGENCY 是"扩展位：本期未落地，预留枚举防止脏数据入库"，
 * 放进授权下拉等于允许用户产出后端未支持的数据（前端不臆测未落地取值）；待后端确认落地后再放开。
 * 说明：本页此前有三处硬编码（映射表、筛选下拉内联 option、授权单选内联 options），一并收口到这里。 */
const { dictLabel, dictOptions } = useCertMeta();

function subjectTypeLabel(v?: string) {
  if (!v) return '';
  const byDict = dictLabel('subject_type', v);
  return byDict === v ? SubjectApi.SUBJECT_TYPE_LABEL[v] || v : byDict;
}

const subjectTypeOptions = computed(() => {
  const byDict = dictOptions('subject_type').filter((o) => o.value !== 'AGENCY');
  if (byDict.length > 0) return byDict;
  return Object.entries(SubjectApi.SUBJECT_TYPE_LABEL).map(([value, label]) => ({ label, value }));
});

/* ============ 台账数据 ============
 * 业务：管理端展示已授权主体台账（契约 Task2「管理端应显示已授权的投标人」）。
 * 理由：主体=企业租户（招标投标法 第25条，投标人是法人/其他组织）；行内带档案完善度，区分“仅授权未完善”。
 * 依据：招标投标法 第25/26条（法律）；电子办法 第24条（部门规章，注册登记+验证）。
 * 契约：07-主体域 §3/§5.2；GET /bidding/subject-auth/page（bidding:subject-auth:query）。
 * ============ */
const loading = ref(false);
const rows = ref<SubjectApi.AuthRow[]>([]);
const total = ref(0);
const pageNo = ref(1);
const pageSize = ref(10);
const filters = reactive<{ authStatus?: string; subjectType?: string; tenantName?: string }>({});

/* ============ 时间格式 ============
 * 契约：04-全链路 §0 通用约定——时间字段为 epoch 毫秒数字，前端统一格式化。
 * ============ */
const p2 = (n: number) => String(n).padStart(2, '0');
const fmtT = (v?: number): string => {
  if (!v) return '—';
  const d = new Date(Number(v));
  if (Number.isNaN(+d)) return '—';
  return d.getFullYear() + '-' + p2(d.getMonth() + 1) + '-' + p2(d.getDate()) + ' ' + p2(d.getHours()) + ':' + p2(d.getMinutes());
};

async function load() {
  loading.value = true;
  try {
    const res = await fetchSubjectAuthPage({ pageNo: pageNo.value, pageSize: pageSize.value, ...filters });
    rows.value = res.list || [];
    total.value = Number(res.total || 0);
  } catch (e) {
    createMessage.error('加载失败：' + ((e as Error).message || String(e)));
  } finally {
    loading.value = false;
  }
}
const pages = computed(() => Math.max(1, Math.ceil(total.value / pageSize.value)));
function goPrev() { if (pageNo.value > 1) { pageNo.value -= 1; load(); } }
function goNext() { if (pageNo.value < pages.value) { pageNo.value += 1; load(); } }
function onSearch() { pageNo.value = 1; load(); }

/* ============ 授权新主体（核发资格） ============
 * 业务：对基座既有企业租户核发“投标人/招标人”主体资格（企业=租户，租户本体由基座 system 负责，此处仅选择）。
 * 理由：企业具备资格（满足招标投标法 第26条“具备规定的资格条件”）→ 平台在系统内放行其参与投标/招标。
 * 依据：招标投标法 第25/26条（法律）；电子办法 第16/24条（部门规章，招标人/投标人注册登记并经验证）。
 * 契约：04-全链路 A2 POST /bidding/subject-auth/authorize（重复启用返 106_032）；03-页面结构 §5.2。
 * ============ */
const authOpen = ref(false);
const authSaving = ref(false);
const tenantOptions = ref<Array<{ label: string; value: number | string }>>([]);
const authForm = reactive<{ remark?: string; subjectType: string; tenantId?: number | string; }>({ subjectType: 'BIDDER' });
async function loadTenants() {
  if (tenantOptions.value.length) return;
  try {
    // 基座租户清单：租户管理属 system 模块既有能力，本模块只读取以“企业”身份选择授权对象
    const res = await requestClient.get<{ list?: Array<{ id: number | string; name?: string }> }>('/system/tenant/page', { params: { pageNo: 1, pageSize: 100 } });
    tenantOptions.value = (res.list || []).map((t) => ({ label: String(t.name || t.id), value: t.id }));
  } catch {
    tenantOptions.value = [];
  }
}
function openAuthorize() {
  authForm.tenantId = undefined;
  authForm.subjectType = 'BIDDER';
  authForm.remark = '';
  void loadTenants();
  authOpen.value = true;
}
async function doAuthorize() {
  if (authForm.tenantId === undefined || authForm.tenantId === null) { createMessage.warning('请选择企业租户'); return; }
  authSaving.value = true;
  try {
    const name = tenantOptions.value.find((t) => String(t.value) === String(authForm.tenantId))?.label;
    await authorizeSubject({ tenantId: authForm.tenantId, tenantName: name || undefined, subjectType: authForm.subjectType, remark: authForm.remark || undefined });
    createMessage.success('已授权（重复授权后端拒绝 106_032；撤销后可再次授权）');
    authOpen.value = false;
    load();
  } catch (e) {
    createMessage.error('授权失败：' + ((e as Error).message || String(e)));
  } finally {
    authSaving.value = false;
  }
}

/* ============ 授权 / 撤销授权（行内开关） ============
 * 业务：资格具备 → 授权；资格不具备 / 需暂停参与 → 撤销授权（本页判定企业主体资格的唯一动作面）。
 * 理由：用户口径“资格齐全就能投标招标，不够就是不能；资格不够 = 未授权，资格够 = 有权限”——
 *      撤销授权即该企业不再具备投标人/招标人参与资格（其发布/投标入口被资格门拒绝）。
 * 依据：招标投标法 第26条（法律，资格条件）；实施条例 第19条（行政法规，不具有资格则不可投标）。
 * 契约：PUT /bidding/subject-auth/status（bidding:subject-auth:update）；04-全链路 A2“停用后可复用启用”。
 * ============ */
function toggleStatus(row: SubjectApi.AuthRow, checked: boolean) {
  const target = checked ? 'ENABLED' : 'DISABLED';
  const cur = row.authStatus;
  if (cur === target) return;
  Modal.confirm({
    title: checked ? '授权（核发主体资格）' : '撤销授权（不再具备资格）',
    content: (checked ? '授权' : '撤销授权') + '「' + row.tenantName + '」（' + subjectTypeLabel(row.subjectType) + '）？' + (checked ? '授权后该企业可参与投标/招标（仍需档案完善）。' : '撤销后该企业不具备主体资格，发布/投标将被资格门拒绝（106_031）；留痕可查，可再次授权。'),
    okText: checked ? '授权' : '撤销授权',
    okButtonProps: checked ? {} : { danger: true },
    cancelText: '取消',
    onOk: async () => {
      try { await updateSubjectAuthStatus(row.id!, target); createMessage.success('已' + (checked ? '授权' : '撤销授权')); load(); } catch (e) { createMessage.error((e as Error).message || String(e)); }
    },
  });
}

/* ============ 资格抽屉（核验单家企业的参与资格） ============
 * 业务：查看“该企业当前是否具备投标/招标资格”及不满足原因（授权状态 ∧ 档案完善度）。
 * 理由：仅授权不充分（Task2 口径）：电子办法 第24/14条 注册登记、如实递交并经验证；信息不完整无法参与投标/发布，
 *      但关注/订阅/浏览不受影响（仅需登录+权限）。
 * 依据：电子办法 第24/14/13条（部门规章）；招标投标法 第26条（法律）。
 * 契约：GET /bidding/subject-auth/eligibility?tenantId=&subjectType=（bidding:subject-auth:query）；04-全链路 D3。
 * ============ */
const elOpen = ref(false);
const elLoading = ref(false);
const elData = ref<SubjectApi.Eligibility>();
const elRow = ref<SubjectApi.AuthRow>();
async function openEligibility(row: SubjectApi.AuthRow) {
  elRow.value = row;
  elData.value = undefined;
  elOpen.value = true;
  elLoading.value = true;
  try {
    elData.value = await fetchSubjectAuthEligibility(row.tenantId!, row.subjectType || 'BIDDER');
  } catch (e) {
    createMessage.error('资格查询失败：' + ((e as Error).message || String(e)));
  } finally {
    elLoading.value = false;
  }
}

onMounted(load);
</script>

<template>
  <div class="bid-ind ind-page">
        <div class="ind-stripes"></div>
    <div class="ind-head">
      <div class="min-w-0">
        <div class="ind-title">平台授权管理 · 企业主体资格 <span class="ind-mark">SUBJECT</span></div>
        <!-- 页头说明（业务口径）：资格具备→授权可参与；不具备→不授权不可参与；租户/权限/具体项目评审均另有其主 -->

        <div class="ind-sub">
平台按“资格具备与否”给已入驻企业（基座租户）授权投标人/招标人主体身份：资格具备 → 授权，可投标/招标；
资格不具备 → 不授权，发布/投标被资格门拒绝。企业是否具备资格由招标文件/资格预审与专家评审认定（本页只落台账与授权开关）；
租户=基座管、菜单权限=系统角色授权管、经办账号=企业自管。
</div>
      </div>
      <div class="ind-actions">
        <Button type="primary" size="large" @click="openAuthorize">＋ 授权主体</Button>
        <Button size="large" @click="load">刷新</Button>
      </div>
    </div>
    <div class="ind-body">
<!-- 筛选台：主体类型 / 授权状态 / 企业名；授权状态按“已授权 / 未授权（已撤销）”展示 -->
    <div class="ind-filterbar">
      <Select v-model:value="filters.subjectType" placeholder="主体类型" allow-clear style="width: 130px">
        <Select.Option v-for="opt in subjectTypeOptions" :key="opt.value" :value="opt.value">
          {{ opt.label }}
        </Select.Option>
      </Select>
      <Select v-model:value="filters.authStatus" placeholder="授权状态" allow-clear style="width: 150px">
        <Select.Option value="ENABLED">已授权</Select.Option>
        <Select.Option value="DISABLED">未授权（已撤销）</Select.Option>
      </Select>
      <Input v-model:value="filters.tenantName" placeholder="企业名称模糊检索" allow-clear style="width: 200px" @press-enter="onSearch" />
      <Button type="primary" ghost @click="onSearch">查询</Button>
    </div>

    <div class="rounded-md border border-border bg-card">
      <Table :data-source="rows" :loading="loading" :pagination="false" row-key="id" size="middle">
        <template #bodyCell="{ column, record }">
          <!-- 企业 = 基座租户（主体名称快照 + 租户号） -->
          <template v-if="column.key === 'tenant'">
            <div class="font-medium text-foreground">{{ record.tenantName || '（无名称快照）' }}</div>
            <div class="font-mono text-xs text-muted-foreground">租户 #{{ record.tenantId }}</div>
          </template>
          <!-- 主体类型：投标人 BIDDER / 招标人 TENDERER（企业扮演的角色，一家企业可同时两种） -->
          <template v-else-if="column.key === 'type'">
            <Tag color="blue">{{ subjectTypeLabel(record.subjectType) }}</Tag>
          </template>
          <!-- 授权状态 + 授权/撤销授权开关 -->
          <template v-else-if="column.key === 'authStatus'">
            <Space size="small">
              <Tag :color="record.authStatus === 'ENABLED' ? 'green' : 'red'">{{ record.authStatus === 'ENABLED' ? '已授权' : '未授权' }}</Tag>
              <Switch
                :checked="record.authStatus === 'ENABLED'"
                size="small"
                checked-children="授权"
                un-checked-children="撤销"
                @change="(c: unknown) => toggleStatus(record as SubjectApi.AuthRow, c === true)"
              />
            </Space>
          </template>
          <!-- 档案完善度：授权≠完善；资格门=授权 ∧ 档案必填齐（电子办法 24/14 条：如实递交并经验证） -->
          <template v-else-if="column.key === 'profile'">
            <template v-if="record.profileExists">
              <Tag :color="record.profileComplete ? 'green' : 'orange'">{{ record.profileComplete ? '档案已完善' : '已授权未完善' }}</Tag>
              <div v-if="!record.profileComplete && record.missingRequiredNames?.length" class="text-xs text-muted-foreground">缺：{{ record.missingRequiredNames.join('、') }}</div>
            </template>
            <Tag v-else color="red">未建档</Tag>
          </template>
          <template v-else-if="column.key === 'time'">{{ fmtT(record.authorizeTime) }}</template>
          <!-- 操作：资格核验（看授权+完善度与不满足原因） -->
          <template v-else-if="column.key === 'action'">
            <a class="text-xs text-primary" @click="openEligibility(record as SubjectApi.AuthRow)">资格核验</a>
          </template>
        </template>
        <template #emptyText><div class="py-8 text-xs text-muted-foreground/60">尚无授权记录 — 点右上「授权主体」为已入驻企业（基座租户）核发主体资格</div></template>
      </Table>
      <div class="flex items-center justify-end gap-2 px-3 py-2 text-xs text-muted-foreground">
        <span>共 {{ total }} 条</span>
        <Button size="small" :disabled="pageNo <= 1" @click="goPrev">上一页</Button>
        <span>{{ pageNo }} / {{ pages }}</span>
        <Button size="small" :disabled="pageNo >= pages" @click="goNext">下一页</Button>
      </div>
    </div>

    <!-- 授权弹窗：选基座租户 + 主体类型 → 核发资格（契约 04-全链路 A2） -->
    <Modal v-model:open="authOpen" title="授权主体（核发企业主体资格）" :footer="null" width="480">
      <div class="space-y-3">
        <div>
          <div class="mb-1 text-xs font-medium text-muted-foreground">被授权企业（基座租户）*</div>
          <Select v-model:value="authForm.tenantId" placeholder="从基座现有租户中选择" style="width: 100%" :options="tenantOptions" show-search option-filter-prop="label" />
        </div>
        <div>
          <div class="mb-1 text-xs font-medium text-muted-foreground">主体类型 *</div>
          <Radio.Group v-model:value="authForm.subjectType" option-type="button" :options="subjectTypeOptions" />
        </div>
        <div>
          <div class="mb-1 text-xs font-medium text-muted-foreground">备注</div>
          <Input.TextArea v-model:value="authForm.remark" :rows="2" />
        </div>
        <div class="text-right"><Space><Button @click="authOpen = false">取消</Button><Button type="primary" :loading="authSaving" @click="doAuthorize">授权</Button></Space></div>
      </div>
    </Modal>
</div>

    <!-- 资格抽屉：单家企业的参与资格核验（授权 ∧ 档案完善） -->
    <Drawer v-model:open="elOpen" :title="`${elRow?.tenantName || '企业' } 资格状态`" :width="520">
      <SpinWrap :loading="elLoading">
        <div v-if="elData" class="space-y-2 text-xs">
          <div class="rounded-md border p-3" :class="elData.eligible ? 'border-emerald-500/30 bg-emerald-500/10' : 'border-amber-500/30 bg-amber-500/10'">
            <div class="text-sm font-semibold" :class="elData.eligible ? 'text-emerald-600 dark:text-emerald-400' : 'text-amber-600 dark:text-amber-400'">{{ elData.eligible ? '具备参与资格' : '暂不具备参与资格' }}（{{ elData.subjectType === 'TENDERER' ? '招标人' : '投标人' }}）</div>
            <div class="mt-1 text-muted-foreground">{{ elData.message }}</div>
          </div>
          <div class="grid grid-cols-2 gap-2">
            <InfoCell label="授权状态" :value="elData.authStatus === 'ENABLED' ? '已授权' : elData.authStatus === 'DISABLED' ? '未授权' : '未授权'" />
            <InfoCell label="档案完善" :value="elData.profileComplete ? '是' : '否'" />
            <InfoCell label="授权时间" :value="fmtT(elData.authorizeTime)" />
            <InfoCell label="撤销时间" :value="fmtT(elData.disableTime)" />
          </div>
          <div v-if="elData.reasonCodes?.length" class="rounded border border-destructive/30 bg-destructive/10 p-2">
            原因：{{ eligibleReasonText(elData.reasonCodes) }}
            <span v-if="elData.missingRequiredNames?.length" class="ml-2">缺失：{{ elData.missingRequiredNames.join('、') }}</span>
          </div>
          <div class="pt-1 text-muted-foreground/60">资格 = 平台授权（ENABLED） ∧ 档案必填齐（信息不完整无法参与投标/发布；关注与订阅不受影响）</div>
        </div>
        <div v-else-if="!elLoading" class="text-xs text-muted-foreground/60">查询无结果（后端 106 错误码会在此展示）</div>
      </SpinWrap>
    </Drawer>
  </div>
</template>
