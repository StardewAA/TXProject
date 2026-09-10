<!-- ============================================================================
  公告 / 公示管理 · 招标人“发布工作台”（企业自营公告，管理端）
  业务（08-公告公示前端交付-业务口径说明）：公告/公示是招标人（企业）对外发布的法律文书；
    本页做 起草→发布→撤下 的全生命周期（发布工作台），不是普通文章 CRUD；大厅只读展示已发布内容。
  类型（四类，勿自由扩展）：TENDER_NOTICE 招标公告 / CHANGE_NOTICE 变更公告 /
    WIN_CANDIDATE_PUBLICITY 中标候选人公示（公示期≥3日）/ WIN_RESULT_PUBLICITY 中标结果公示。
  状态机：DRAFT → PUBLISHED → UNPUBLISHED（可再发布）；ARCHIVED=归档终态。
    编辑入口仅 草稿/已撤下；PUBLISHED 只“撤下+详情”；删除仅 草稿/已撤下（后端已补状态守卫 1_060_101_009）。
    已发布如需修订：对外法律文书走“撤下→改→重新发布”（前端管控入口；后端对线上修订走版本追加+订阅通知）。
  理由：对外已生效文书直接改会造成版本漂移与审计问题（08 §7 FAQ）；撤下重发保历史版本可追溯。
  依据（法规，含文件类型）：
    · 招标投标法 第16条（法律）——公开招标应当发布招标公告，载明事项法定；
    · 实施条例 第15条（行政法规）——公开招标项目应依法发布招标公告、编制招标文件；
    · 招标公告和公示信息发布管理办法（发改委令第10号）第5/6/12/13条（部门规章/政府文件）——
      公告/中标候选人公示载明事项、公示期、免费发布与查阅、归类检索；
    · 电子招标投标办法（八部委令第20号）第9条（部门规章/政府文件）——公众/市场主体免费获取依法公开信息。
  后端契约（嵌套仓库 docs/ 前端规范-全域接口说明/）：
    · 08-公告公示前端交付-业务口径说明.md §1-§3/§6/§8（状态机与行操作矩阵、pdf 对外最终件、internalFlag=false 对外）；
    · 06-bidding前端契约接口完全对照.md §2（notice）+ api/bidding/notice/index.ts；
    · 主体资格门：notice create/update/publish 均断言 招标人已授权且档案完善（assertTendererIssuanceAllowed，106_031/106_034）。
  权限点：bidding:notice:create|update|publish|delete|query（菜单 6754）。
  界面（bid-indust 工业视觉基座 bid-indust.css）：石墨墨色页头 + 信号橙刻度条；
    台账总览（状态分布，数据取自 notice/page 各状态 total，不伪造）+ 台账表 + 分节表单，避免平铺堆叠。
============================================================================ -->
<script lang="ts" setup>
import { NoticeApi } from '#/api/bidding/notice';

import { computed, onMounted, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { Button, Drawer, Input, message, Modal, Radio, Select, Space, Switch, Table, Tag, Upload } from 'ant-design-vue';
import { uploadFile } from '#/api/infra/file';
import { fetchSubjectProfile } from '#/api/bidding/subject';
import { NOTICE_PAYLOAD_FIELDS } from './payloadFieldMeta';
import NoticeHtmlView from './NoticeHtmlView.vue';
import TenderNoticeTable from './tables/TenderNoticeTable.vue';
import ChangeNoticeTable from './tables/ChangeNoticeTable.vue';
import CandidatePublicityTable from './tables/CandidatePublicityTable.vue';
import ResultPublicityTable from './tables/ResultPublicityTable.vue';

import { createNotice, deleteNotice, fetchNoticeDetail, fetchNoticePage, fetchNoticeTypeMeta, publishNotice, unpublishNotice, updateNotice } from '#/api/bidding/notice';
const createMessage = message;
/* portal 前台模式：经 /portal/bidding/publish 挂载时展示前台返回栏（组件同源复用：管理端菜单与门户企业发布共用一套发布工作台） */
const route = useRoute();
const router = useRouter();
const inPortal = computed(() => String(route.path).startsWith('/portal'));
const goBackHall = () => {
  router.push('/portal/bidding/internal-hall').catch(() => {});
};

/* PDF 版式上传（infra 文件服务：保存只带 file_id；对外 URL 由业务系统按可见性签发） */
const pdfUploading = ref(false);
const pdfFileName = ref('');
const pdfSize = ref<number>();
/** 权威数据集字段值（key=规范英文名 → 值）；第四节的四张类型专属表（tables/*）双向绑定此对象 */
const payloadValues = ref<Record<string, string>>({});
const fmtBytes = (n?: number): string => {
  if (n === undefined || n === null) return '';
  if (n < 1024) return n + ' B';
  if (n < 1024 * 1024) return (n / 1024).toFixed(1) + ' KB';
  return (n / 1024 / 1024).toFixed(2) + ' MB';
};
async function onPdfBeforeUpload(file: File): Promise<boolean> {
  if (!/\.pdf$/i.test(file.name)) { createMessage.warning('仅支持 PDF 文件'); return false; }
  pdfUploading.value = true;
  try {
    const res = await uploadFile({ file, directory: 'bidding/notice' });
    const id = (res as unknown as { id?: number }).id;
    if (id === undefined) throw new Error('上传未返回 file_id');
    form.pdfFileId = id;
    pdfFileName.value = file.name;
    pdfSize.value = file.size;
    createMessage.success('版式文件已上传：file_id=' + id);
  } catch (e) {
    createMessage.error('上传失败：' + ((e as Error).message || String(e)));
  } finally {
    pdfUploading.value = false;
  }
  return false;
}

const loading = ref(false);
const rows = ref<NoticeApi.Row[]>([]);
const total = ref(0);
const pageNo = ref(1);
const pageSize = ref(10);
const filters = reactive<{ status?: string; noticeType?: string; internalFlag?: boolean }>({});

const p2 = (n: number) => String(n).padStart(2, '0');
const fmtT = (v?: number | string): string => {
  if (v === undefined || v === null || v === '') return '—';
  const d = new Date(Number(v));
  if (Number.isNaN(+d)) return String(v);
  return d.getFullYear() + '-' + p2(d.getMonth() + 1) + '-' + p2(d.getDate()) + ' ' + p2(d.getHours()) + ':' + p2(d.getMinutes()) + ':' + p2(d.getSeconds());
};
const statusTag = (s?: string) =>
  s === 'PUBLISHED' ? { color: 'green', text: NoticeApi.NOTICE_STATUS_LABEL[s] || s }
    : s === 'DRAFT' ? { color: 'default', text: NoticeApi.NOTICE_STATUS_LABEL[s] || s }
      : s === 'UNPUBLISHED' ? { color: 'orange', text: NoticeApi.NOTICE_STATUS_LABEL[s] || s }
        : { color: 'gray', text: NoticeApi.NOTICE_STATUS_LABEL[s!] || s || '—' };
const typeName = (row: NoticeApi.Row) => {
  if (row.noticeTypeName) return row.noticeTypeName;
  const hit = NoticeApi.NOTICE_TYPES.find((t) => t.code === row.noticeType);
  return hit ? hit.label : row.noticeType || '—';
};

/* ============ 台账总览（状态分布 KPI，点击即按状态过滤） ============
 * 后端 page 无按状态汇总出参，这里以 pageSize=1 对每个状态取 total（4+1 次轻量查询，
 * 企业自营公告量小，开销可忽略；KPI 数字为后端真实计数，不做前端凑数）。
 * 依据：GET /bidding/notice/page 契约（NoticeApi.PageParams.status 过滤，见 api/bidding/notice）。 */
const kpis = ref<Array<{ status?: string; text: string; note: string; color: string; num: number }>>([]);
const KPI_BASE: Array<{ status?: string; text: string; note: string; color: string }> = [
  { text: '全部公告', note: '本企业全部公告/公示', color: 'k-gray' },
  { text: '草稿', note: '起草中，可编辑后发布', color: 'k-blue', status: 'DRAFT' },
  { text: '已发布', note: '对外公示中，可撤下', color: 'k-green', status: 'PUBLISHED' },
  { text: '已撤下', note: '可重新发布或删除', color: 'k-orange', status: 'UNPUBLISHED' },
  { text: '已归档', note: '归档终态，只读留痕', color: 'k-red', status: 'ARCHIVED' },
];
async function fetchKpiCounts() {
  try {
    const items = await Promise.all(
      KPI_BASE.map(async (k) => {
        const res = await fetchNoticePage({
          pageNo: 1,
          pageSize: 1,
          ...(k.status ? { status: k.status } : {}),
        });
        return { ...k, num: Number(res.total || 0) };
      }),
    );
    kpis.value = items;
  } catch {
    // KPI 统计失败不影响主列表（保留旧值）
  }
}
const kpiActive = (k: { status?: string }) => (filters.status || undefined) === (k.status || undefined);
function onKpiTap(k: { status?: string }) {
  filters.status = k.status;
  onSearch();
}

async function refreshAll() {
  load();
  void fetchKpiCounts();
}
async function load() {
  loading.value = true;
  try {
    const res = await fetchNoticePage({ pageNo: pageNo.value, pageSize: pageSize.value, ...filters });
    rows.value = res.list || [];
    total.value = Number(res.total || 0);
  } catch (e) {
    createMessage.error('加载失败：' + ((e as Error).message || String(e)));
  } finally {
    loading.value = false;
  }
}
function onSearch() { pageNo.value = 1; load(); void fetchKpiCounts(); }
const pages = computed(() => Math.max(1, Math.ceil(total.value / pageSize.value)));
function goPrev() { if (pageNo.value > 1) { pageNo.value -= 1; load(); } }
function goNext() { if (pageNo.value < pages.value) { pageNo.value += 1; load(); } }

/* 台账表格列（标题/类型/状态/发布时间/操作）：columns 必须显式声明，否则 antd Table 无列渲染 */
const tableColumns = [
  { title: '公告标题', key: 'title', dataIndex: 'title' },
  { title: '公告类型', key: 'type', dataIndex: 'noticeType', width: 150 },
  { title: '状态', key: 'status', dataIndex: 'status', width: 100 },
  { title: '发布时间', key: 'publishTime', dataIndex: 'publishTime', width: 160 },
  { title: '操作', key: 'action', width: 240 },
];

/* ============ 编辑器 ============ */
const editorOpen = ref(false);
const editorSaving = ref(false);
const editId = ref<number | string>();
const form = reactive<NoticeApi.SaveParams>({
  noticeType: 'TENDER_NOTICE',
  title: '',
  publicLevel: 'SOCIAL',
  internalFlag: false,
});
/** 后端 meta/types 类型模板（按 type code 索引）；拉取失败时为空 → 组件回退本地静态字典 */
const typeMetaMap = ref<Record<string, NoticeApi.NoticeTypeMeta>>({});
/** 当前类型 meta 模板（供组件 + 保存逻辑使用） */
const currentTypeMeta = computed<NoticeApi.NoticeTypeMeta | undefined>(
  () => typeMetaMap.value[form.noticeType],
);
/** 当前类型权威字段元数据（本地静态字典兜底：label/必填数；组件优先 remote） */
const payloadMeta = computed(() => {
  const m = NOTICE_PAYLOAD_FIELDS[form.noticeType];
  if (m) return m;
  return NOTICE_PAYLOAD_FIELDS.TENDER_NOTICE!;
});
/** 类型展示名（meta/types name 优先，静态字典兜底） */
const typeLabel = computed(() => currentTypeMeta.value?.name || payloadMeta.value.label);
/** 发布单位自动带：本企业（当前租户）招标人主体档案单位名（用户无需手填；档案在「主体档案」维护） */
async function autoFillPublisher(): Promise<void> {
  if (form.publisherName && form.publisherName.trim()) return; // 已有值不覆盖
  try {
    const p = await fetchSubjectProfile('TENDERER');
    if (p && p.subjectName) form.publisherName = p.subjectName;
  } catch {
    /* 未建档/无权限：留空由用户填 */
  }
}
/** 拉取类型字段模板（后端 meta/types；失败静默 → 组件用本地字典兜底渲染） */
async function loadTypeMeta(): Promise<void> {
  try {
    const list = await fetchNoticeTypeMeta();
    if (!Array.isArray(list)) return;
    const map: Record<string, NoticeApi.NoticeTypeMeta> = {};
    for (const t of list) map[t.type] = t;
    typeMetaMap.value = map;
  } catch {
    /* 后端不可用/未部署：保留空 map 走本地字典 */
  }
}
const openCreate = () => {
  editId.value = undefined;
  form.id = undefined;
  form.noticeCode = '';
  form.noticeType = 'TENDER_NOTICE';
  form.title = '';
  form.projectCode = '';
  form.projectName = '';
  form.publisherName = '';
  form.publicLevel = 'SOCIAL';
  form.internalFlag = false;
  form.content = '';
  form.pdfFileId = undefined;
  payloadValues.value = {};
  pdfFileName.value = '';
  pdfSize.value = undefined;
  editorOpen.value = true;
  void autoFillPublisher();
  void loadTypeMeta();
};
const openEdit = async (row: NoticeApi.Row) => {
  const d = await fetchNoticeDetail(row.id!);
  editId.value = row.id;
  form.id = d.id;
  form.noticeCode = d.noticeCode;
  form.noticeType = d.noticeType || 'TENDER_NOTICE';
  form.title = d.title || '';
  form.projectCode = d.projectCode;
  form.projectName = d.projectName;
  form.publisherName = d.publisherName;
  form.publicLevel = d.publicLevel || 'SOCIAL';
  form.internalFlag = !!d.internalFlag;
  form.content = d.content || '';
  form.pdfFileId = d.pdfFileId;
  pdfSize.value = typeof d.pdfSize === 'number' ? d.pdfSize : undefined;
  pdfFileName.value = '';
  payloadValues.value = {};
  if (d.payload && typeof d.payload === 'object') {
    const flat: Record<string, string> = {};
    for (const [k, v] of Object.entries(d.payload)) {
      if (v === null || v === undefined) continue;
      flat[k] = String(v);
    }
    payloadValues.value = flat;
  }
  editorOpen.value = true;
};
const editorTitle = computed(() => (editId.value === undefined ? '新建公告（草稿入库）' : '编辑公告'));
function saveEditor() {
  if (!form.title.trim()) { createMessage.warning('请填写公告名称'); return; }
  if (!form.noticeType) { createMessage.warning('请选择公告类型'); return; }
  if (form.internalFlag && form.publicLevel === 'GOVERNMENT') { createMessage.warning('企业内部公告请勿选择政府公开级别'); return; }

  /* 名称/正文权威键按类型映射：标题(第1节)与正文(第3节)自动并入权威字段，避免两处重复录入。
     （公示类用 PUBLICITY_NAME/CONTENT；公告类用 NOTICE_NAME/NOTICE_CONTENT——依数据集 record 组件名） */
  const nameKey = form.noticeType === 'WIN_CANDIDATE_PUBLICITY' ? 'PUBLICITY_NAME' : 'NOTICE_NAME';
  const contentKey = form.noticeType === 'WIN_CANDIDATE_PUBLICITY' ? 'PUBLICITY_CONTENT' : 'NOTICE_CONTENT';
  const merged: Record<string, string> = { ...payloadValues.value };
  if ((merged[nameKey] || '').trim() !== form.title.trim()) merged[nameKey] = form.title.trim();
  if (form.content && (merged[contentKey] || '').trim() !== form.content.trim()) merged[contentKey] = form.content.trim();

  /* 必填预校验（业务字段：pub≠GOVERNMENT；时间 ISO yyyy-MM-ddTHH:mm:ss 由"现在"带入，校验非空）。
     依据：VO数据规范"必填=是" + meta/types required；报送政务参数（平台码等）不在表单收集——
     对外报送缺失由后端返回中文提示（公告必填字段缺失…），站内直建按联调文档 §6 可不填 payload。 */
  const checklist: { key: string; label: string; required: boolean; pub: string }[] =
    (payloadMeta.value.fields).map((f) => ({
      key: f.key, label: f.label, required: f.required, pub: f.pub,
    }));
  const bizMissing: string[] = [];
  for (const f of checklist) {
    if (f.pub === 'GOVERNMENT') continue; // 报送政务：不强制（文档 §6 站内可不填；后端返回缺失则透出）
    if (!f.required || (merged[f.key] || '').trim()) continue;
    bizMissing.push(f.label + '（' + f.key + '）');
  }
  if (bizMissing.length) {
    createMessage.warning('权威必填字段未填全（' + bizMissing.length + ' 项）：\n' + bizMissing.slice(0, 12).join('、') + (bizMissing.length > 12 ? '…' : ''));
    return;
  }

  /* payload 组装：仅带非空值（后端 canonicalize 补 null/剔未知键） */
  const assembled: Record<string, unknown> = {};
  for (const [k, v] of Object.entries(merged)) {
    if (v !== undefined && v !== null && String(v).trim() !== '') assembled[k] = v;
  }
  form.payload = Object.keys(assembled).length ? assembled : undefined;

  editorSaving.value = true;
  const submit = async () => {
    try {
      if (editId.value === undefined) {
        await createNotice({ ...form, payload: form.payload });
        createMessage.success('草稿已创建');
      } else {
        await updateNotice({ ...form, payload: form.payload });
        createMessage.success('已保存（正文变更将追加新版本）');
      }
      editorOpen.value = false;
      load();
      void fetchKpiCounts();
    } catch (e) {
      const err = e as Error;
      const msg = err.message || String(err);
      createMessage.error(msg + (msg.includes('106-03') || /106-03[14]/.test(msg) ? '（招标人发布门：需平台授权并完善主体档案）' : ''));
    } finally {
      editorSaving.value = false;
    }
  };
  void submit();
}
/* ============ 状态动作 ============ */
/* 发布公告：DRAFT/UNPUBLISHED → PUBLISHED。
 * 业务/理由：发布后进大厅对外公示（对外文书）；发布前须已上传对外 PDF 版式并关联正文（对外最终件=pdf）。
 * 依据：招标投标法 第16条（法律，发布招标公告）；实施条例 第15条（行政法规）。
 * 契约：08 §6 发布确认文案、§8 状态机 transit + 招标人发布门（106_031/034）；POST /bidding/notice/publish。 */
function doPublish(row: NoticeApi.Row) {
  Modal.confirm({
    title: '发布公告',
    content: '「' + row.title + '」发布后将在招投标大厅对外公示（内部公告仅本租户可见）；对外法律文书如需修改需撤下后重新发布（历史版本保留）。发布需已关联正文与 PDF 版式文件。是否发布？',
    okText: '发布',
    cancelText: '取消',
    onOk: async () => {
      try {
        await publishNotice(row.id!);
        createMessage.success('已发布');
        load();
        void fetchKpiCounts();
      } catch (e) {
        const err = e as Error;
        createMessage.error((err.message || String(err)) + (/106-03[14]/.test(err.message || '') ? '（发布门未过：先授权+完善档案，或补正文/PDF）' : ''));
      }
    },
  });
}
/* 撤下公告：PUBLISHED → UNPUBLISHED。
 * 业务/理由：撤下后大厅不再展示，历史版本保留；如需再次对外走“重新发布”。对外文书修订先撤下再改。
 * 契约：08 §3-4/§6 撤下确认文案；PUT /bidding/notice/unpublish。 */
function doUnpublish(row: NoticeApi.Row) {
  Modal.confirm({
    title: '撤下公告',
    content: '「' + row.title + '」撤下后大厅将不再展示（历史版本保留）；如需再次对外请重新发布。确定撤下？',
    okText: '撤下',
    cancelText: '取消',
    onOk: async () => {
      try { await unpublishNotice(row.id!); createMessage.success('已撤下'); load(); void fetchKpiCounts(); } catch (e) { createMessage.error((e as Error).message || String(e)); }
    },
  });
}
/* 删除公告：仅 草稿/已撤下 可删（PUBLISHED/ARCHIVED 后端守卫拒绝 1_060_101_009）。
 * 业务/理由：撤下≠删除；对外已发布/归档内容须留痕可追溯（08 §3-4）。
 * 依据：10号令 第5/12条（部门规章/政府文件，公示信息免费查阅与留痕）。
 * 契约：DELETE /bidding/notice/delete。 */
function doDelete(row: NoticeApi.Row) {
  Modal.confirm({
    title: '删除公告',
    content: '「' + row.title + '」将被逻辑删除（仅草稿/已撤下可删）。确定删除？',
    okText: '删除',
    okButtonProps: { danger: true },
    cancelText: '取消',
    onOk: async () => {
      try { await deleteNotice(row.id!); createMessage.success('已删除'); load(); void fetchKpiCounts(); } catch (e) { createMessage.error((e as Error).message || String(e)); }
    },
  });
}

/* ============ 详情 ============ */
const detailOpen = ref(false);
const detail = ref<NoticeApi.Detail>();
async function openDetail(row: NoticeApi.Row) {
  detailOpen.value = true;
  detail.value = undefined;
  try { detail.value = await fetchNoticeDetail(row.id!); } catch (e) { createMessage.error((e as Error).message || String(e)); }
}

onMounted(() => { load(); void fetchKpiCounts(); });
</script>

<template>
  <div class="bid-ind ind-page">
    <div class="ind-stripes"></div>
    <div v-if="inPortal" class="ind-backbar">
      <a class="text-sm font-bold text-blue-700" href="#" @click.prevent="goBackHall()">‹ 返回本平台发布</a>
      <span class="text-xs text-gray-400">门户 · 企业前台发布（当前登录租户自营公告）</span>
    </div>

    <!-- 页头：石墨墨色面板（沉稳基调）+ 主操作 -->
    <div class="ind-head">
      <div class="min-w-0">
        <div class="ind-title">{{ inPortal ? '公告发布 · 发布工作台（企业前台）' : '公告 / 公示管理 · 发布工作台' }} <span class="ind-mark">NOTICE</span></div>
        <div class="ind-sub">{{ inPortal ? '本企业（当前租户）自营公告：起草 → 发布 → 撤下 全生命周期。发布前需平台授权并完善主体档案；发布后订阅者将收到动态；修订需撤下后重新发布（历史版本保留）。' : '本企业自营公告（T 轨）。招标人发布门：创建/发布需平台授权并完善主体档案；发布后关注订阅者将收到动态。对外法律文书修订需撤下后重新发布（历史版本保留）。' }}</div>
      </div>
      <div class="ind-actions">
        <Button type="primary" size="large" @click="openCreate">＋ 新建公告（草稿）</Button>
        <Button size="large" @click="refreshAll">刷新</Button>
      </div>
    </div>

    <div class="ind-body">
      <!-- 栏目一：台账总览（状态分布，点击卡片即按状态过滤） -->
      <div class="ind-panel nw-kpi-panel">
        <div class="ind-panel-h">
          <span class="ind-panel-t">公告台账 · 状态分布</span>
          <span class="ind-panel-meta">数字为后端实时统计 · 点击卡片按状态过滤（草稿 起草中可编辑发布 / 已发布 对外公示中 / 已撤下 可重发或删除 / 已归档 只读留痕）</span>
        </div>
        <div class="ind-panel-b">
          <div v-if="kpis.length" class="ind-kpis nw-kpi-row">
            <button
              v-for="k in kpis"
              :key="k.text"
              type="button"
              class="ind-kpi nw-kpi-tap"
              :class="[k.color, { 'nw-kpi-on': kpiActive(k) }]"
              @click="onKpiTap(k)"
            >
              <div class="k-label">{{ k.text }}</div>
              <div class="k-num">{{ k.num }}</div>
              <div class="k-note">{{ k.note }}</div>
            </button>
          </div>
          <div v-else class="nw-kpi-empty">统计加载中…</div>
        </div>
      </div>

      <!-- 栏目二：公告台账（过滤 + 列表） -->
      <div class="ind-table-shell nw-table-shell">
        <div class="nw-shell-head">
          <div class="nw-shell-title">
            全生命周期台账
            <span class="nw-shell-meta">
              共 {{ total }} 条
              <template v-if="filters.status"> · {{ NoticeApi.NOTICE_STATUS_LABEL[filters.status] || filters.status }}</template>
              <template v-if="filters.noticeType"> · {{ typeName({ noticeType: filters.noticeType } as NoticeApi.Row) }}</template>
              <template v-if="filters.internalFlag !== undefined"> · {{ filters.internalFlag ? '仅内部' : '仅社会公开' }}</template>
            </span>
          </div>
          <div class="ind-filterbar nw-inline-filter">
            <Select v-model:value="filters.noticeType" placeholder="公告类型" allow-clear style="width: 170px">
              <Select.Option v-for="t in NoticeApi.NOTICE_TYPES" :key="t.code" :value="t.code">{{ t.label }}</Select.Option>
            </Select>
            <Radio.Group v-model:value="filters.internalFlag">
              <Radio.Button :value="undefined">全部</Radio.Button>
              <Radio.Button :value="true">仅内部公告</Radio.Button>
              <Radio.Button :value="false">仅社会公开</Radio.Button>
            </Radio.Group>
            <Button type="primary" ghost @click="onSearch">查询</Button>
          </div>
        </div>

        <Table
          :columns="tableColumns"
          :data-source="rows"
          :loading="loading"
          :pagination="false"
          :row-key="(r) => r.id"
          size="middle"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'title'">
              <div class="nw-title-cell">
                <span class="nw-title-text">{{ record.title }}</span>
                <Tag v-if="record.internalFlag" class="ml-1">内部</Tag>
                <Tag v-else-if="record.publicLevel" class="ml-1" color="geekblue">{{ record.publicLevel === 'GOVERNMENT' ? '政府公开' : '社会公开' }}</Tag>
                <div v-if="record.noticeCode || record.projectName || record.publisherName" class="nw-title-sub">
                  <template v-if="record.noticeCode"><span class="nw-code">{{ record.noticeCode }}</span></template>
                  <template v-if="record.projectName"><span>{{ record.projectName }}</span></template>
                  <template v-if="record.publisherName"><span>发布单位：{{ record.publisherName }}</span></template>
                </div>
              </div>
            </template>
            <template v-else-if="column.key === 'type'">
              <span class="nw-type-text">{{ typeName(record as NoticeApi.Row) }}</span>
            </template>
            <template v-else-if="column.key === 'status'">
              <Tag :color="statusTag(record.status).color">{{ statusTag(record.status).text }}</Tag>
            </template>
            <template v-else-if="column.key === 'action'">
              <Space :size="4" wrap>
                <Button size="small" type="link" class="nw-act" @click="openDetail(record as NoticeApi.Row)">详情</Button>
                <template v-if="record.status === 'DRAFT' || record.status === 'UNPUBLISHED'">
                  <Button size="small" type="link" class="nw-act" @click="openEdit(record as NoticeApi.Row)">编辑</Button>
                  <Button size="small" type="primary" ghost class="nw-act-primary" @click="doPublish(record as NoticeApi.Row)">{{ record.status === 'DRAFT' ? '发布' : '重新发布' }}</Button>
                  <Button size="small" type="link" danger @click="doDelete(record as NoticeApi.Row)">删除</Button>
                </template>
                <template v-else-if="record.status === 'PUBLISHED'">
                  <Button size="small" type="link" class="nw-act-warn" @click="doUnpublish(record as NoticeApi.Row)">撤下</Button>
                </template>
                <span v-else class="nw-readonly">只读</span>
              </Space>
            </template>
            <template v-else-if="column.key === 'publishTime'">
              <span class="nw-time">{{ fmtT(record.publishTime) }}</span>
            </template>
          </template>
          <template #emptyText>
            <div class="nw-empty">
              <div class="nw-empty-title">暂无公告记录</div>
              <div class="nw-empty-sub">点右上「新建公告」起草（发布前需主体档案完善 + 平台授权）</div>
            </div>
          </template>
        </Table>

        <!-- 表尾：分页条 -->
        <div class="nw-pager">
          <span class="nw-pager-total">第 <b>{{ pageNo }}</b> / {{ pages }} 页 · 共 {{ total }} 条</span>
          <div class="nw-pager-btns">
            <Button size="small" :disabled="pageNo <= 1" @click="goPrev">‹ 上一页</Button>
            <Button size="small" :disabled="pageNo >= pages" @click="goNext">下一页 ›</Button>
          </div>
        </div>
      </div>
    </div>

    <!-- 新建/编辑弹窗（分节表单） -->
    <Modal v-model:open="editorOpen" :title="editorTitle" :footer="null" width="880" destroy-on-close>
      <div class="nw-modal-body">
        <!-- 第一节：标题与辨识 -->
        <div class="nw-form-sect">
          <div class="nw-sect-h"><span class="nw-sect-no">1</span>标题与辨识<span class="nw-sect-ast">* 为必填</span></div>
          <div class="grid grid-cols-2 gap-3">
            <div class="col-span-2">
              <div class="mb-1 text-xs font-semibold text-gray-700">公告名称（标题）*</div>
              <Input v-model:value="form.title" placeholder="如 榆林炼油厂 2026 大修消缺项目施工招标公告" />
            </div>
            <div>
              <div class="mb-1 text-xs font-semibold text-gray-700">公告类型 *</div>
              <Select v-model:value="form.noticeType" style="width: 100%">
                <Select.Option v-for="t in NoticeApi.NOTICE_TYPES" :key="t.code" :value="t.code">{{ t.label }}</Select.Option>
              </Select>
            </div>
            <div>
              <div class="mb-1 text-xs font-semibold text-gray-700">发布单位</div>
              <Input v-model:value="form.publisherName" placeholder="本企业名称快照（可留空由后端取）" />
            </div>
            <div>
              <div class="mb-1 text-xs font-semibold text-gray-700">公开范围</div>
              <Select v-model:value="form.publicLevel" style="width: 100%">
                <Select.Option value="SOCIAL">社会公开（大厅可见）</Select.Option>
                <Select.Option value="GOVERNMENT">政府公开</Select.Option>
              </Select>
            </div>
            <div class="flex items-end pb-1">
              <div class="flex items-center gap-2 text-xs text-gray-600"><Switch v-model:checked="form.internalFlag" size="small" /> 企业内部公告（仅本租户可见，不进入大厅）</div>
            </div>
          </div>
        </div>

        <!-- 第二节：项目关联 -->
        <div class="nw-form-sect">
          <div class="nw-sect-h"><span class="nw-sect-no">2</span>项目关联<span class="nw-sect-sub">可选 — 公告将归入对应招标项目</span></div>
          <div class="grid grid-cols-2 gap-3">
            <div>
              <div class="mb-1 text-xs font-semibold text-gray-700">项目编号</div>
              <Input v-model:value="form.projectCode" placeholder="关联招标项目编号（可选）" />
            </div>
            <div>
              <div class="mb-1 text-xs font-semibold text-gray-700">项目名称</div>
              <Input v-model:value="form.projectName" placeholder="招标项目名称（可选）" />
            </div>
          </div>
        </div>

        <!-- 第三节：正文与版式 -->
        <div class="nw-form-sect">
          <div class="nw-sect-h"><span class="nw-sect-no">3</span>正文与版式<span class="nw-sect-sub">发布前必填；实质变更自动追加版本</span></div>
          <div class="grid grid-cols-1 gap-3">
            <div>
              <div class="mb-1 text-xs font-semibold text-gray-700">公告正文（全文）</div>
              <Input.TextArea v-model:value="form.content" :rows="6" placeholder="公告正文全文…" />
            </div>
            <div class="nw-pdf-row">
              <div class="mb-1 flex flex-wrap items-center gap-3 text-xs font-semibold text-gray-700">
                <span>公告版式文件（PDF，发布前必填）— 文件服务上传，保存只带 file_id；URL 由业务系统按可见性签发</span>
                <Upload :show-upload-list="false" accept=".pdf" :before-upload="onPdfBeforeUpload">
                  <Button size="small" :loading="pdfUploading">{{ form.pdfFileId !== undefined ? '重新上传 PDF' : '上传 PDF' }}</Button>
                </Upload>
              </div>
              <div v-if="form.pdfFileId !== undefined || pdfFileName" class="text-xs text-gray-500">
                file_id #{{ form.pdfFileId ?? '—' }}<template v-if="pdfFileName"> · {{ pdfFileName }}</template><template v-if="pdfSize"> · {{ fmtBytes(pdfSize) }}</template>
                <span v-if="form.pdfFileId !== undefined" class="ml-2 text-orange-500">（{{ form.internalFlag || form.publicLevel === 'GOVERNMENT' ? '限定可见' : '社会公开公告可免费查阅' }}）</span>
              </div>
            </div>
          </div>
        </div>

        <!-- 第四节：权威数据集字段 —— 四张类型专属表（招标公告/变更公告/中标候选人公示/中标结果公示），按所选类型渲染对应表。
             title/content（第1/3节）自动并入权威名称/正文键；政务报送参数（平台码/时间戳）不在此收集。 -->
        <div class="nw-form-sect">
          <div class="nw-sect-h"><span class="nw-sect-no">4</span>权威数据集字段
            <span class="nw-sect-sub">当前类型：{{ typeLabel }} · 按类型专属表分节填报（必填=VO数据规范“必填=是”/meta/types）</span>
          </div>
          <div>
            <TenderNoticeTable v-if="form.noticeType === 'TENDER_NOTICE'" :type-meta="currentTypeMeta" v-model:values="payloadValues" />
            <ChangeNoticeTable v-else-if="form.noticeType === 'CHANGE_NOTICE'" :type-meta="currentTypeMeta" v-model:values="payloadValues" />
            <CandidatePublicityTable v-else-if="form.noticeType === 'WIN_CANDIDATE_PUBLICITY'" :type-meta="currentTypeMeta" v-model:values="payloadValues" />
            <ResultPublicityTable v-else-if="form.noticeType === 'WIN_RESULT_PUBLICITY'" :type-meta="currentTypeMeta" v-model:values="payloadValues" />
            <div class="mt-2 text-xs text-gray-500">公告名称与正文已在上方第 1、3 节填写，保存时自动并入权威字段（NOTICE_NAME/NOTICE_CONTENT 等）；报送平台参数由系统/平台维护，不在表单中。</div>
          </div>
        </div>

        <div class="nw-modal-foot">
          <Space>
            <Button @click="editorOpen = false">取消</Button>
            <Button type="primary" :loading="editorSaving" @click="saveEditor">{{ editId === undefined ? '保存为草稿' : '保存修改' }}</Button>
          </Space>
        </div>
      </div>
    </Modal>

    <!-- 详情抽屉 -->
    <Drawer v-model:open="detailOpen" :title="detail ? detail.title : '公告详情'" :width="720">
      <template v-if="detail">
        <div class="mb-3 flex flex-wrap items-center gap-2 text-xs">
          <Tag :color="statusTag(detail.status).color">{{ statusTag(detail.status).text }}</Tag>
          <Tag color="blue">{{ typeName(detail) }}</Tag>
          <span class="text-gray-400">{{ detail.noticeCode }}</span>
          <span class="text-gray-400">版本 v{{ detail.contentVersion ?? '—' }}</span>
        </div>
        <div class="mb-2 grid grid-cols-2 gap-x-4 gap-y-1 text-xs text-gray-500">
          <div>项目：{{ detail.projectCode || '—' }} {{ detail.projectName || '' }}</div>
          <div>发布单位：{{ detail.publisherName || '—' }}</div>
          <div>发布时间：{{ fmtT(detail.publishTime) }}</div>
          <div>公开级别：{{ detail.publicLevel || '—' }}<span v-if="detail.internalFlag">（内部）</span></div>
        </div>
        <div class="rounded-md border border-gray-100 p-3">
          <NoticeHtmlView :detail="detail" :show-head="false" />
        </div>
      </template>
    </Drawer>
  </div>
</template>

<style scoped>
/* ===== 本页局部样式（基于 bid-indust 基座，仅增强版面层级，不改全局） ===== */

/* 栏目一 KPI 面板 */
.nw-kpi-panel { margin-bottom: 14px; }
.nw-kpi-row { margin: 4px 0 6px; }
.nw-kpi-tap {
  font: inherit;
  text-align: left;
  cursor: pointer;
  transition: transform .12s ease, box-shadow .12s ease, border-color .12s ease;
  user-select: none;
}
.nw-kpi-tap:hover { transform: translateY(-1px); box-shadow: 0 4px 12px rgba(20, 28, 38, .1); }
.nw-kpi-tap.nw-kpi-on {
  border-color: var(--ind-orange);
  box-shadow: 0 0 0 1.5px var(--ind-orange) inset, 0 3px 10px rgba(232, 135, 30, .14);
}
.nw-kpi-tap.nw-kpi-on .k-label { color: var(--ind-orange); }
.nw-kpi-empty { padding: 8px 2px; font-size: 13px; color: var(--ind-weak-text); }

/* 栏目二 台账表壳：标题条 + 内联筛选 + 表格 + 表尾 */
.nw-table-shell { margin-top: 14px; }
.nw-shell-head {
  display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 10px;
  padding: 10px 14px; border-bottom: 1.5px solid var(--ind-line); background: #f4f5f7;
}
.nw-shell-title { font-size: 15px; font-weight: 800; color: var(--ind-ink-text); }
.nw-shell-meta { margin-left: 10px; font-size: 12.5px; font-weight: 500; color: var(--ind-weak-text); }
.nw-inline-filter {
  margin-bottom: 0 !important; padding: 4px 6px !important;
  box-shadow: none !important; background: transparent !important;
  border: none !important; border-radius: 0 !important;
}
.nw-inline-filter .ant-select { width: 170px; }

/* 标题单元格：主标题 + 次行元信息 */
.nw-title-cell { min-width: 260px; }
.nw-title-text { font-weight: 600; color: var(--ind-ink-text); line-height: 1.45; }
.nw-title-sub { margin-top: 3px; display: flex; flex-wrap: wrap; gap: 4px 14px; font-size: 12px; color: var(--ind-weak-text); }
.nw-title-sub .nw-code { font-family: ui-monospace, SFMono-Regular, Menlo, monospace; color: #8a93a0; }
.nw-type-text { color: var(--ind-sub-text); font-weight: 500; }
.nw-time { font-variant-numeric: tabular-nums; color: var(--ind-sub-text); }
.nw-readonly { color: #b6bec7; font-size: 12.5px; }

/* 行操作按钮保持紧凑 */
.nw-act { padding: 0 6px !important; }
.nw-act-primary { padding: 0 10px !important; }
.nw-act-warn { padding: 0 6px !important; color: #d97706 !important; }
.nw-act-warn:hover { color: #b45309 !important; }

/* 空态 */
.nw-empty { padding: 34px 0 30px; text-align: center; }
.nw-empty-title { font-size: 15px; font-weight: 700; color: #8a93a0; }
.nw-empty-sub { margin-top: 6px; font-size: 12.5px; color: #b6bec7; }

/* 表尾分页条 */
.nw-pager {
  display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 8px;
  padding: 10px 14px; border-top: 1.5px solid var(--ind-line); background: #fafbfc;
}
.nw-pager-total { font-size: 12.5px; color: var(--ind-weak-text); }
.nw-pager-total b { color: var(--ind-ink-text); font-variant-numeric: tabular-nums; }
.nw-pager-btns { display: flex; gap: 8px; }

/* 弹窗分节表单 */
.nw-modal-body { max-height: 72vh; overflow-y: auto; padding-right: 6px; }
.nw-form-sect { margin-bottom: 16px; padding: 14px 14px 4px; border: 1.5px solid var(--ind-line-soft); border-left: 4px solid var(--ind-ink); border-radius: 3px; background: #fff; }
.nw-sect-h { display: flex; align-items: center; gap: 8px; margin-bottom: 10px; font-size: 14.5px; font-weight: 800; color: var(--ind-ink-text); }
.nw-sect-no {
  display: inline-flex; align-items: center; justify-content: center; width: 20px; height: 20px;
  background: var(--ind-ink); color: #fff; font-size: 11.5px; font-weight: 800; border-radius: 2px; letter-spacing: .5px;
}
.nw-sect-ast { font-size: 11.5px; font-weight: 600; color: var(--ind-orange); }
.nw-sect-sub { font-size: 12px; font-weight: 500; color: var(--ind-weak-text); margin-left: 2px; }
.nw-pdf-row { background: #faf7f0; border: 1px dashed #d6c7a8; border-radius: 3px; padding: 10px 12px; }
.nw-modal-foot { display: flex; justify-content: flex-end; border-top: 1.5px solid var(--ind-line-soft); padding-top: 12px; }

/* 详情抽屉正文轻边框 */
.nw-detail-doc { border: 1px solid var(--ind-line-soft); }
</style>
