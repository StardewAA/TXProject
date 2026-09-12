<script setup lang="ts">
/* 项目台账（菜单 6771 / component bidding/project/index / componentName BiddingProject / 权限 bidding:project:query）
 * 契约：项目域接口（api/bidding/project）
 * 口径：
 *   · 时间字段（createTime 等）为 **epoch 毫秒**（平台统一序列化）→ 一律过 fmtDateTime；
 *   · state 是模块内部管理态（ACTIVE/CLOSED），不是数据集字段，故本地用固定两档展示；
 *   · fields 为 ProjectVO 数据集全量字段（key=规范英文名），详情按原样列出，不硬编码字段名清单；
 *   · 追溯（trace）一次取回：项目 → 招标项目 → 标段 + 公告/外部公告/备案/关注/主体 + 统计。 */
import { computed, onMounted, reactive, ref } from 'vue';

import { useAccess } from '@vben/access';
import { useAccessStore } from '@vben/stores';

import { message, Modal } from 'ant-design-vue';

import { FILING_STATUS_OPTIONS, type FilingApi, receiveFilingResult, submitFiling } from '#/api/bidding/filing';
import {
  deleteProject,
  getProject,
  getProjectPage,
  type ProjectApi,
  saveProject,
  traceProject,
} from '#/api/bidding/project';

import { fmtDateTime } from '../certificate/format';

import '../certificate/cert.css';

const { hasAccessByCodes } = useAccess();
const accessStore = useAccessStore();

/** 权限判定：权限码未下发时放行（最终由后端 @PreAuthorize 裁决），避免界面被锁死 */
function can(code: string): boolean {
  const codes = accessStore.accessCodes ?? [];
  if (codes.length === 0) return true;
  return codes.includes('*') || codes.includes('*:*:*') || hasAccessByCodes([code]);
}

const query = reactive<ProjectApi.PageParams>({
  pageNo: 1,
  pageSize: 10,
  projectCode: undefined,
  projectName: undefined,
  regionCode: undefined,
  industriesType: undefined,
  state: undefined,
});
const rows = ref<ProjectApi.Project[]>([]);
const total = ref(0);
const loading = ref(false);

async function load() {
  loading.value = true;
  try {
    const page = await getProjectPage({ ...query });
    rows.value = page.list ?? [];
    total.value = page.total ?? 0;
  } catch {
    rows.value = [];
    total.value = 0;
  } finally {
    loading.value = false;
  }
}

function resetQuery() {
  query.projectCode = undefined;
  query.projectName = undefined;
  query.regionCode = undefined;
  query.industriesType = undefined;
  query.state = undefined;
  query.pageNo = 1;
  void load();
}

/* ================= 项目备案：提交 / 回执登记（FilingController） ================= */
const filingOpen = ref(false);
const filingSaving = ref(false);
const filingForm = reactive<FilingApi.SubmitReq>({ filingType: '', projectCode: '' });
const filingAttachmentText = ref('');

/** 逗号/空格分隔的文件 id → number[]（后端为 Long 列表） */
function toFileIds(text: string) {
  const ids = text
    .split(/[,\s，]+/)
    .map((t) => t.trim())
    .filter(Boolean)
    .map((t) => Number(t))
    .filter((n) => Number.isFinite(n));
  return ids.length > 0 ? ids : undefined;
}

function openFiling(row: ProjectApi.Project) {
  Object.assign(filingForm, {
    attachmentFileIds: undefined,
    dataTimestamp: undefined,
    filingType: '',
    orgForm: undefined,
    projectCode: row.projectCode ?? '',
    submitTime: undefined,
    superviseDeptCode: undefined,
    superviseDeptName: undefined,
    tenderMode: undefined,
    tenderProjectCode: undefined,
    unifiedDealCode: undefined,
  });
  filingAttachmentText.value = '';
  filingOpen.value = true;
}

const receiptOpen = ref(false);
const receiptSaving = ref(false);
const receiptForm = reactive<FilingApi.ResultReq>({ receiptNo: '', resultStatus: '' });

async function submitFilingForm() {
  if (!filingForm.filingType.trim() || !filingForm.projectCode.trim()) {
    message.warning('备案类型与项目编号必填');
    return;
  }
  filingSaving.value = true;
  try {
    const resp = await submitFiling({ ...filingForm, attachmentFileIds: toFileIds(filingAttachmentText.value) });
    message.success(`备案已提交：受理号 ${resp?.receiptNo ?? '—'}｜状态 ${resp?.status ?? '—'}`);
    filingOpen.value = false;
    // 提交后把受理号带到回执登记，省一次手工誊抄
    receiptForm.receiptNo = resp?.receiptNo ?? '';
  } catch {
    // 静默：重复提交由后端返回 FILING_ALREADY_SUBMITTED（§0.4）
  } finally {
    filingSaving.value = false;
  }
}

function openReceipt() {
  Object.assign(receiptForm, { opinion: undefined, receiptNo: receiptForm.receiptNo ?? '', resultStatus: '' });
  receiptOpen.value = true;
}

async function submitReceipt() {
  if (!receiptForm.receiptNo.trim() || !receiptForm.resultStatus) {
    message.warning('受理号与回推结果必填');
    return;
  }
  receiptSaving.value = true;
  try {
    await receiveFilingResult({ ...receiptForm });
    message.success('备案回执已登记');
    receiptOpen.value = false;
  } catch {
    // 静默
  } finally {
    receiptSaving.value = false;
  }
}

onMounted(load);

const kpi = computed(() => {
  const list = rows.value;
  const active = list.filter((r) => (r.state ?? 'ACTIVE') === 'ACTIVE').length;
  return { active, closed: list.length - active };
});

const columns = [
  { title: '项目编号', dataIndex: 'projectCode', key: 'projectCode', width: 190 },
  { title: '项目名称', dataIndex: 'projectName', key: 'projectName', width: 320 },
  { title: '地区', dataIndex: 'regionCode', key: 'regionCode', width: 110 },
  { title: '法人', dataIndex: 'legalPerson', key: 'legalPerson', width: 110 },
  { title: '状态', dataIndex: 'state', key: 'state', width: 100 },
  { title: '登记时间', dataIndex: 'createTime', key: 'createTime', width: 170 },
  { title: '操作', key: 'action', width: 210, fixed: 'right' as const },
];

/* ===== 详情 / 追溯（一次 trace 拿全关联，避免按域多次请求） ===== */
const traceOpen = ref(false);
const traceLoading = ref(false);
const trace = ref<null | ProjectApi.Trace>(null);
const currentRow = ref<null | ProjectApi.Project>(null);

async function openTrace(r: ProjectApi.Project) {
  currentRow.value = r;
  traceOpen.value = true;
  traceLoading.value = true;
  trace.value = null;
  try {
    trace.value = await traceProject(r.projectCode);
  } catch {
    trace.value = null;
  } finally {
    traceLoading.value = false;
  }
}

/** 数据集字段：只列出有值的项（null 表示该数据集未覆盖此字段） */
const fieldEntries = computed(() => {
  const f = (trace.value?.project?.fields ?? currentRow.value?.fields ?? {}) as Record<string, unknown>;
  return Object.entries(f).filter(([, v]) => v !== null && v !== undefined && v !== '');
});

/* ===== 新建 / 编辑 ===== */
const formOpen = ref(false);
const saving = ref(false);
const formRef = ref();
const form = reactive<ProjectApi.Project>({ projectCode: '', projectName: '' });
const isEdit = computed(() => !!form.id);

const rules = {
  projectCode: [{ required: true, message: '项目编号必填', trigger: 'blur' }],
  projectName: [{ required: true, message: '项目名称必填', trigger: 'blur' }],
};

async function onCreate() {
  Object.assign(form, {
    id: undefined,
    projectCode: '',
    projectName: '',
    regionCode: undefined,
    legalPerson: undefined,
    industriesType: undefined,
    fundSource: undefined,
    projectScale: undefined,
    approvalDeptName: undefined,
    approvalDocNo: undefined,
    state: 'ACTIVE',
    remark: undefined,
  });
  formOpen.value = true;
}

async function onEdit(r: ProjectApi.Project) {
  Object.assign(form, { ...r });
  // §2.4 同源口径：编辑前以详情为准，避免用列表行旧值覆盖
  if (r.id) {
    try {
      Object.assign(form, await getProject(r.id));
    } catch {
      /* 详情取不到时退回列表行数据 */
    }
  }
  formOpen.value = true;
}

async function submit() {
  try {
    await formRef.value?.validate();
  } catch {
    return;
  }
  saving.value = true;
  try {
    await saveProject({ ...form });
    message.success(isEdit.value ? '已保存' : '登记成功');
    formOpen.value = false;
    void load();
  } catch {
    // 失败原因由请求层统一 toast 后端 msg
  } finally {
    saving.value = false;
  }
}

function confirmDelete(r: ProjectApi.Project) {
  Modal.confirm({
    title: '删除项目',
    content: `确认删除项目「${r.projectName}（${r.projectCode}）」？已关联招标项目/标段时后端会拒绝。`,
    okType: 'danger',
    async onOk() {
      await deleteProject(r.id as number);
      message.success('已删除');
      void load();
    },
  });
}
</script>

<template>
  <div class="ct-page">
    <div class="ct-head">
      <div>
        <div class="ct-title">项目台账</div>
        <div class="ct-sub">项目主档与数据集字段；追溯一次取回招标项目、标段与公告关联</div>
      </div>
      <div class="ct-actions">
        <a-button v-if="can('bidding:project:create')" type="primary" @click="onCreate">登记项目</a-button>
        <a-button @click="load">刷新</a-button>
      </div>
    </div>

    <div class="ct-kpis">
      <div class="ct-kpi k-blue">
        <div class="k-label">项目总数</div>
        <div class="k-num">{{ total }}</div>
        <div class="k-note">按当前筛选条件</div>
      </div>
      <div class="ct-kpi k-green">
        <div class="k-label">本页在建</div>
        <div class="k-num">{{ kpi.active }}</div>
        <div class="k-note">state=ACTIVE（内部管理态）</div>
      </div>
      <div class="ct-kpi">
        <div class="k-label">本页已关闭</div>
        <div class="k-num">{{ kpi.closed }}</div>
        <div class="k-note">state=CLOSED</div>
      </div>
    </div>

    <div class="ct-panel">
      <div class="ct-filter">
        <a-input v-model:value="query.projectCode" placeholder="项目编号" style="width: 190px" allow-clear @press-enter="load" />
        <a-input v-model:value="query.projectName" placeholder="项目名称（模糊）" style="width: 220px" allow-clear @press-enter="load" />
        <a-input v-model:value="query.regionCode" placeholder="地区编码" style="width: 140px" allow-clear @press-enter="load" />
        <a-input v-model:value="query.industriesType" placeholder="行业分类" style="width: 140px" allow-clear @press-enter="load" />
        <a-select
          v-model:value="query.state"
          placeholder="管理态"
          style="width: 130px"
          allow-clear
          :options="[
            { label: '在建（ACTIVE）', value: 'ACTIVE' },
            { label: '已关闭（CLOSED）', value: 'CLOSED' },
          ]"
          @change="load"
        />
        <a-button type="primary" @click="load">查询</a-button>
        <a-button @click="resetQuery">重置</a-button>
      </div>
      <div class="ct-panel-b">
        <a-table
          class="ct-table"
          :columns="columns"
          :data-source="rows"
          :loading="loading"
          :pagination="{
            current: query.pageNo,
            pageSize: query.pageSize,
            total,
            showSizeChanger: true,
            showTotal: (t: number) => `共 ${t} 条`,
          }"
          :scroll="{ x: 1300 }"
          row-key="id"
          size="middle"
          @change="(p: any) => { query.pageNo = p.current; query.pageSize = p.pageSize; void load(); }"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'projectCode'">
              <div class="ct-id">{{ record.projectCode }}</div>
            </template>
            <template v-else-if="column.key === 'projectName'">{{ record.projectName }}</template>
            <template v-else-if="column.key === 'state'">
              <a-tag :color="(record.state ?? 'ACTIVE') === 'ACTIVE' ? 'green' : 'default'">
                {{ (record.state ?? 'ACTIVE') === 'ACTIVE' ? '在建' : '已关闭' }}
              </a-tag>
            </template>
            <template v-else-if="column.key === 'createTime'">{{ fmtDateTime(record.createTime) }}</template>
            <template v-else-if="column.key === 'action'">
              <a-space size="small">
                <a @click="openTrace(record)">追溯</a>
                <a v-if="can('bidding:filing:create')" @click="openFiling(record)">提交备案</a>
                <a v-if="can('bidding:filing:update')" @click="openReceipt()">登记回执</a>
                <a v-if="can('bidding:project:update')" @click="onEdit(record)">编辑</a>
                <!-- 权限码口径：后端删除端点（POST /bidding/project/delete）用的是 bidding:project:update，
                     按规范 §4.2 复用项目域既有权限点，无独立的 :delete 码；此处若写 :delete 则普通角色永远看不到删除入口 -->
                <a v-if="can('bidding:project:update')" style="color: #c6362e" @click="confirmDelete(record)">删除</a>
              </a-space>
            </template>
          </template>
          <template #emptyText><div class="ct-empty">暂无项目，点右上「登记项目」建档</div></template>
        </a-table>
      </div>
    </div>

    <!-- 追溯抽屉：项目 → 招标项目 → 标段 + 公告/外部公告/备案/关注/主体 -->
    <a-drawer v-model:open="traceOpen" class="bid-ind" :width="1000" title="项目全景追溯" destroy-on-close>
      <a-spin :spinning="traceLoading">
        <template v-if="trace">
          <div class="ct-panel-meta mb-3">
            查询：{{ trace.queryCode || currentRow?.projectCode }}（{{ trace.queryType || 'PROJECT' }}）
          </div>
          <!-- 统计原先用 v-for 直出后端键名（tenderProjectCount：3），中文标签后才可用；
               后端一次聚合 6 个域的计数与最近动态，正是本抽屉"一屏看清"要用的那一行。 -->
          <a-descriptions v-if="trace.stats" :column="4" size="small" bordered class="mb-3">
            <a-descriptions-item label="关联招标项目">{{ trace.stats.tenderProjectCount ?? 0 }}</a-descriptions-item>
            <a-descriptions-item label="关联标段">{{ trace.stats.sectionCount ?? 0 }}</a-descriptions-item>
            <a-descriptions-item label="内部公告">{{ trace.stats.noticeCount ?? 0 }}</a-descriptions-item>
            <a-descriptions-item label="外部公告">{{ trace.stats.externalNoticeCount ?? 0 }}</a-descriptions-item>
            <a-descriptions-item label="备案记录">{{ trace.stats.filingCount ?? 0 }}</a-descriptions-item>
            <a-descriptions-item label="关注项">{{ trace.stats.followCount ?? 0 }}</a-descriptions-item>
            <a-descriptions-item label="各域最近动态" :span="4">
              {{ fmtDateTime(trace.stats.latestDynamicTime) }}
            </a-descriptions-item>
          </a-descriptions>

          <div class="ct-panel-t mb-2">项目基本信息</div>
          <a-descriptions :column="2" size="small" bordered class="mb-3">
            <a-descriptions-item label="项目编号">{{ trace.project?.projectCode || '—' }}</a-descriptions-item>
            <a-descriptions-item label="项目名称">{{ trace.project?.projectName || '—' }}</a-descriptions-item>
            <a-descriptions-item label="地区编码">{{ trace.project?.regionCode || '—' }}</a-descriptions-item>
            <a-descriptions-item label="法人">{{ trace.project?.legalPerson || '—' }}</a-descriptions-item>
            <a-descriptions-item label="投资代码">{{ trace.project?.investProjectCode || '—' }}</a-descriptions-item>
            <a-descriptions-item label="审批文号">{{ trace.project?.approvalDocNo || '—' }}</a-descriptions-item>
            <a-descriptions-item label="登记时间">{{ fmtDateTime(trace.project?.createTime) }}</a-descriptions-item>
            <a-descriptions-item label="管理态">{{ trace.project?.state || '—' }}</a-descriptions-item>
          </a-descriptions>

          <div class="ct-panel-t mb-2">数据集字段（ProjectVO）</div>
          <a-descriptions v-if="fieldEntries.length" :column="2" size="small" bordered class="mb-3">
            <a-descriptions-item v-for="[k, v] in fieldEntries" :key="k" :label="k">
              {{ typeof v === 'object' ? JSON.stringify(v) : v }}
            </a-descriptions-item>
          </a-descriptions>
          <div v-else class="ct-tip mb-3">该记录未带数据集字段（fields 为空）</div>

          <div class="ct-panel-t mb-2">关联招标项目（{{ trace.tenderProjects?.length ?? 0 }}）</div>
          <a-table
            class="ct-table mb-3"
            :columns="[
              { title: '招标项目编号', dataIndex: 'tenderProjectCode', key: 'code', width: 200 },
              { title: '名称', dataIndex: 'tenderProjectName', key: 'name' },
              { title: '招标方式', dataIndex: 'tenderMode', key: 'mode', width: 120 },
              { title: '招标人', dataIndex: 'tendererName', key: 'tenderer', width: 200 },
            ]"
            :data-source="trace.tenderProjects ?? []"
            :pagination="false"
            row-key="id"
            size="small"
          >
            <template #emptyText><div class="ct-empty">无关联招标项目</div></template>
          </a-table>

          <div class="ct-panel-t mb-2">关联标段（{{ trace.sections?.length ?? 0 }}）</div>
          <a-table
            class="ct-table mb-3"
            :columns="[
              { title: '标段编号', dataIndex: 'bidSectionCode', key: 'code', width: 200 },
              { title: '名称', dataIndex: 'bidSectionName', key: 'name' },
              { title: '所属招标项目', dataIndex: 'tenderProjectCode', key: 'tp', width: 200 },
              { title: '合同估算价', dataIndex: 'contractReckonPrice', key: 'price', width: 130 },
            ]"
            :data-source="trace.sections ?? []"
            :pagination="false"
            row-key="id"
            size="small"
          >
            <template #emptyText><div class="ct-empty">无关联标段</div></template>
          </a-table>

          <div class="ct-panel-t mb-2">关联公告（{{ trace.notices?.length ?? 0 }}）</div>
          <a-table
            class="ct-table"
            :columns="[
              { title: '公告编号', dataIndex: 'noticeCode', key: 'code', width: 200 },
              { title: '标题', dataIndex: 'title', key: 'title' },
              { title: '类型', dataIndex: 'noticeTypeName', key: 'type', width: 130 },
              { title: '发布时间', dataIndex: 'publishTime', key: 'publishTime', width: 170 },
            ]"
            :data-source="trace.notices ?? []"
            :pagination="false"
            row-key="id"
            size="small"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'publishTime'">{{ fmtDateTime(record.publishTime) }}</template>
            </template>
            <template #emptyText><div class="ct-empty">无关联公告</div></template>
          </a-table>

          <!-- 以下四张清单后端一直在返回，此前只有一行条数（"追溯"却看不到记录）。
               原文按本仓外链约定只给可复制文本 + 可达性标记，不直接给 <a>（外链需走 /go 白名单）。 -->
          <div class="ct-panel-t mb-2">外部公告（{{ trace.externalNotices?.length ?? 0 }}）</div>
          <a-table
            class="ct-table mb-3"
            :columns="[
              { title: '标题', dataIndex: 'title', key: 'title' },
              { title: '采集源', dataIndex: 'sourceName', key: 'sourceName', width: 150 },
              { title: '内容类型', dataIndex: 'contentType', key: 'contentType', width: 110 },
              { title: '状态', dataIndex: 'status', key: 'status', width: 100 },
              { title: '审核', dataIndex: 'auditStatus', key: 'auditStatus', width: 100 },
              { title: '发布时间', dataIndex: 'publishTime', key: 'publishTime', width: 160 },
              { title: '原文', key: 'origUrl', width: 90 },
            ]"
            :data-source="trace.externalNotices ?? []"
            :pagination="false"
            row-key="id"
            size="small"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'publishTime'">{{ fmtDateTime(record.publishTime) }}</template>
              <template v-else-if="column.key === 'origUrl'">
                <span v-if="record.origUrl" class="mono" :title="record.origUrl" style="cursor: text">
                  {{ record.available ? '可达（可复制）' : '平台内不可达' }}
                </span>
                <span v-else>—</span>
              </template>
            </template>
            <template #emptyText><div class="ct-empty">无关联外部公告</div></template>
          </a-table>

          <div class="ct-panel-t mb-2">备案记录（{{ trace.filings?.length ?? 0 }}）</div>
          <a-table
            class="ct-table mb-3"
            :columns="[
              { title: '备案类型', dataIndex: 'filingType', key: 'filingType', width: 140 },
              { title: '状态', dataIndex: 'status', key: 'status', width: 110 },
              { title: '提交时间', dataIndex: 'submitTime', key: 'submitTime', width: 170 },
              { title: '项目编号', dataIndex: 'projectCode', key: 'projectCode', width: 200 },
              { title: '招标项目编号', dataIndex: 'tenderProjectCode', key: 'tenderProjectCode' },
            ]"
            :data-source="trace.filings ?? []"
            :pagination="false"
            row-key="id"
            size="small"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'submitTime'">{{ fmtDateTime(record.submitTime) }}</template>
            </template>
            <template #emptyText><div class="ct-empty">无关联备案</div></template>
          </a-table>

          <div class="ct-panel-t mb-2">关注项（{{ trace.follows?.length ?? 0 }}）</div>
          <a-table
            class="ct-table mb-3"
            :columns="[
              { title: '名称', dataIndex: 'displayName', key: 'displayName' },
              { title: '关注类型', dataIndex: 'followType', key: 'followType', width: 120 },
              { title: '状态', dataIndex: 'followStatus', key: 'followStatus', width: 110 },
              { title: '最近动态', dataIndex: 'latestDynamicTime', key: 'latestDynamicTime', width: 170 },
              { title: '未读', dataIndex: 'unreadCount', key: 'unreadCount', width: 90 },
            ]"
            :data-source="trace.follows ?? []"
            :pagination="false"
            row-key="id"
            size="small"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'latestDynamicTime'">
                {{ fmtDateTime(record.latestDynamicTime) }}
              </template>
              <template v-else-if="column.key === 'unreadCount'">
                <a-badge
                  v-if="Number(record.unreadCount ?? 0) > 0"
                  :count="record.unreadCount"
                  :overflow-count="99"
                />
                <span v-else>—</span>
              </template>
            </template>
            <template #emptyText><div class="ct-empty">无关注项</div></template>
          </a-table>

          <div class="ct-panel-t mb-2">参与主体（{{ trace.subjects?.length ?? 0 }}）</div>
          <a-table
            class="ct-table"
            :columns="[
              { title: '来源', dataIndex: 'source', key: 'source', width: 190 },
              { title: '主体角色', dataIndex: 'subjectRole', key: 'subjectRole', width: 120 },
              { title: '主体名称', dataIndex: 'subjectName', key: 'subjectName' },
              { title: '证件类型', dataIndex: 'codeType', key: 'codeType', width: 110 },
              { title: '证件号', dataIndex: 'code', key: 'code', width: 190 },
              { title: '授权状态', dataIndex: 'authStatus', key: 'authStatus', width: 110 },
              { title: '档案完备', dataIndex: 'profileComplete', key: 'profileComplete', width: 100 },
            ]"
            :data-source="trace.subjects ?? []"
            :pagination="false"
            row-key="code"
            size="small"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'profileComplete'">
                <a-tag :color="record.profileComplete ? 'green' : 'orange'">
                  {{ record.profileComplete ? '完备' : '缺项' }}
                </a-tag>
              </template>
            </template>
            <template #emptyText><div class="ct-empty">无参与主体</div></template>
          </a-table>

          <div class="ct-tip mt-3">
            说明：「内容类型 / 状态 / 审核 / 主体角色 / 授权状态」为后端 code —— 这些域的字典尚未注册，
            登记后此处会自动转为中文（见 sql/history 的字典注册脚本）；「原文」按本仓外链约定只给可复制文本，
            平台内可达性由后端 available（ACTIVE 才可达）判定。
          </div>
        </template>
        <div v-else-if="!traceLoading" class="ct-empty">未取到追溯数据</div>
      </a-spin>
    </a-drawer>

    <!-- 提交备案（项目编号随行带入；filingType 为业务约定文本） -->
    <a-modal
      v-model:open="filingOpen"
      title="提交项目备案"
      :width="820"
      :confirm-loading="filingSaving"
      ok-text="提交"
      cancel-text="取消"
      destroy-on-close
      @ok="submitFilingForm"
    >
      <a-form layout="vertical">
        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item label="备案类型（必填）">
              <a-input v-model:value="filingForm.filingType" placeholder="如：招标备案 / 中标备案（后端为自由文本）" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="项目编号（必填）">
              <a-input v-model:value="filingForm.projectCode" />
            </a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="招标项目编号"><a-input v-model:value="filingForm.tenderProjectCode" /></a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="统一交易码"><a-input v-model:value="filingForm.unifiedDealCode" /></a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="招标方式"><a-input v-model:value="filingForm.tenderMode" /></a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="组织形式"><a-input v-model:value="filingForm.orgForm" /></a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="监管部门编码"><a-input v-model:value="filingForm.superviseDeptCode" /></a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="监管部门名称"><a-input v-model:value="filingForm.superviseDeptName" /></a-form-item>
          </a-col>
          <a-col :span="24">
            <a-form-item label="附件文件 id（逗号分隔）">
              <a-input v-model:value="filingAttachmentText" placeholder="如：301,302" />
            </a-form-item>
          </a-col>
        </a-row>
      </a-form>
      <div class="ct-tip">
        幂等键为「备案类型 + 项目编号」，重复提交后端返回 FILING_ALREADY_SUBMITTED；提交时间由服务端取当前时间。
      </div>
    </a-modal>

    <!-- 备案回执登记 -->
    <a-modal
      v-model:open="receiptOpen"
      title="登记备案回执"
      :width="620"
      :confirm-loading="receiptSaving"
      ok-text="登记"
      cancel-text="取消"
      destroy-on-close
      @ok="submitReceipt"
    >
      <a-form layout="vertical">
        <a-form-item label="受理号（必填）">
          <a-input v-model:value="receiptForm.receiptNo" placeholder="提交备案时后端返回的 receiptNo" />
        </a-form-item>
        <a-form-item label="回推结果（必填）">
          <a-select v-model:value="receiptForm.resultStatus" :options="FILING_STATUS_OPTIONS" placeholder="请选择" />
        </a-form-item>
        <a-form-item label="受理意见">
          <a-textarea v-model:value="receiptForm.opinion" :rows="3" placeholder="如：材料齐全，予以受理" />
        </a-form-item>
      </a-form>
      <div class="ct-tip">回执状态码表逐字取自 FilingStatusEnum；登记后「追溯」里的备案一栏即为最新状态。</div>
    </a-modal>

    <!-- 登记 / 编辑 -->
    <a-modal
      v-model:open="formOpen"
      :title="isEdit ? `编辑项目 #${form.id ?? ''}` : '登记项目'"
      :width="820"
      :confirm-loading="saving"
      ok-text="保存"
      cancel-text="取消"
      destroy-on-close
      @ok="submit"
    >
      <a-form ref="formRef" :model="form" :rules="rules" layout="vertical">
        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item label="项目编号" name="projectCode">
              <a-input v-model:value="form.projectCode" placeholder="唯一编号，用于与公告/标段关联" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="项目名称" name="projectName">
              <a-input v-model:value="form.projectName" />
            </a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="地区编码" name="regionCode"><a-input v-model:value="form.regionCode" /></a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="法人" name="legalPerson"><a-input v-model:value="form.legalPerson" /></a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="行业分类" name="industriesType"><a-input v-model:value="form.industriesType" /></a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="投资代码" name="investProjectCode">
              <a-input v-model:value="form.investProjectCode" />
            </a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="审批部门" name="approvalDeptName">
              <a-input v-model:value="form.approvalDeptName" />
            </a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="审批文号" name="approvalDocNo"><a-input v-model:value="form.approvalDocNo" /></a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="资金来源" name="fundSource"><a-input v-model:value="form.fundSource" /></a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="出资规模" name="contributionScale">
              <a-input v-model:value="form.contributionScale" />
            </a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="管理态" name="state">
              <a-select
                v-model:value="form.state"
                :options="[
                  { label: '在建（ACTIVE）', value: 'ACTIVE' },
                  { label: '已关闭（CLOSED）', value: 'CLOSED' },
                ]"
              />
            </a-form-item>
          </a-col>
          <a-col :span="24">
            <a-form-item label="项目规模" name="projectScale">
              <a-textarea v-model:value="form.projectScale" :rows="2" />
            </a-form-item>
          </a-col>
          <a-col :span="24">
            <a-form-item label="备注" name="remark"><a-textarea v-model:value="form.remark" :rows="2" /></a-form-item>
          </a-col>
        </a-row>
      </a-form>
    </a-modal>
  </div>
</template>
