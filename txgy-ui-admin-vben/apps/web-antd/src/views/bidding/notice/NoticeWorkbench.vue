<!-- ============================================================================
  公告 / 公示管理 · 招标人“发布工作台”（企业内部公告，管理端）
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
import { computed, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';

import { Button, Drawer, Input, message, Modal, Radio, Select, Space, Switch, Table, Tag, Upload } from 'ant-design-vue';

import { NoticeApi } from '#/api/bidding/notice';
import { createNotice, deleteNotice, fetchNoticeDetail, fetchNoticePage, fetchNoticeTypeMeta, publishNotice, reindexAllNotices, reindexNotices, syncNoticePublic, unpublishNotice, updateNotice } from '#/api/bidding/notice';
import { getBidSectionByCode, getBidSectionPage, getTenderProjectPage, type ProjectApi } from '#/api/bidding/project';
import { fetchSubjectProfile } from '#/api/bidding/subject';
import { fmtDateTime } from '#/utils/datetime';
import { fmtBytes } from '#/utils/format';

import AttachmentsPanel from '../vo/AttachmentsPanel.vue';
import { type AttachmentRecord, stageItems } from '../vo/attachmentStages';
import { uploadAttachment } from '../vo/attachmentUpload';
import PdfPreview from '../vo/PdfPreview.vue';
import { PLATFORM_MAINTAINED_CODES } from '../vo/voFieldControl';
import NoticeHtmlView from './NoticeHtmlView.vue';
import { NOTICE_PAYLOAD_FIELDS } from './payloadFieldMeta';
import CandidatePublicityTable from './tables/CandidatePublicityTable.vue';
import ChangeNoticeTable from './tables/ChangeNoticeTable.vue';
import ResultPublicityTable from './tables/ResultPublicityTable.vue';
import TenderNoticeTable from './tables/TenderNoticeTable.vue';
const props = withDefaults(defineProps<{ mode?: 'admin' | 'portal' }>(), { mode: 'admin' });
/* 身份由**显式 prop** 决定，不再嗅探 route.path —— 此前 /portal/bidding/publish 直接挂本组件，
 * 组件靠 route.path.startsWith('/portal') 猜自己是谁，导致"前台没有自己的页面、前后台共用一个工作台"，
 * 职责也就分不开（用户 2026-09-14 指出）。现在：
 *   · 管理端页面  views/bidding/notice/index.vue       → <NoticeWorkbench mode="admin" />
 *   · 门户发布页  views/portal/bidding/publish.vue     → <NoticeWorkbench mode="portal" />
 * 口径（用户已裁定）：发布动作归前台（门户 · 公告发布 = 企业经办人发布），管理端收敛为"管理前台"——
 *   全量台账/下架/详情留痕/运维（索引重建、公开同步）；起草/编辑/发布/重新发布/删除 仅门户出现。
 * 内部公告是否需"提交 → 审核"取决于后端是否补 PENDING_AUDIT/REJECTED 与 submit/approve/reject（待后端）。 */
// 时间展示统一走 #/utils/datetime（本页原有一份本地 fmtT，与 noticeExternal/data.ts 的同名实现重复；
// 两处空值口径还不一致：那边空串、这里 '—' —— 台账页要显式占位，故此处保留 '—'）
const fmtT = (v?: null | number | string): string => fmtDateTime(v, '—');
const createMessage = message;
const router = useRouter();
const inPortal = computed(() => props.mode === 'portal');
const goBackHall = () => {
  router.push('/portal/bidding/internal-hall').catch(() => {});
};

/* PDF 版式上传（infra 文件服务：保存只带 file_id；对外 URL 由业务系统按可见性签发） */
const pdfUploading = ref(false);
const pdfFileName = ref('');
/** 版式文件 URL（上传链路只回 URL 时保留展示；file_id 取到则另行写入 pdfFileId） */
const pdfUrl = ref('');
const pdfSize = ref<number>();
/** 版式文件内嵌预览（浏览器原生 PDF 查看器；上传后立即有 URL，历史数据走 file_id 取 URL） */
const pdfPreviewOpen = ref(false);
/** 权威数据集字段值（key=规范英文名 → 值）；第四节的四张类型专属表（tables/*）双向绑定此对象 */
const payloadValues = ref<Record<string, string>>({});
/** 公告附件（AttachmentSetVO 列表）：按公告类型对应环节的附件采集项分组，可加多个 */
const noticeAttachments = ref<AttachmentRecord[]>([]);
/** 公告类型 → 附件环节（AttachmentItemVO 采集项随环节不同） */
const NOTICE_ATTACH_STAGE: Record<string, string> = {
  TENDER_NOTICE: 'NOTICE_TENDER',
  CHANGE_NOTICE: 'NOTICE_CHANGE',
  WIN_CANDIDATE_PUBLICITY: 'NOTICE_CANDIDATE',
  WIN_RESULT_PUBLICITY: 'NOTICE_RESULT',
};
/** 业务链选项（项目→招标项目→标段）：公告的招标项目编号/标段编号/标段数量由此带出。
 *  数据源已切换为后端真实台账：GET /bidding/tender-project/page 与 /bidding/bid-section/page。 */
const tenderProjects = ref<ProjectApi.TenderProject[]>([]);
const sections = ref<ProjectApi.BidSection[]>([]);
const chainLoading = ref(false);
async function loadChainOptions(): Promise<void> {
  chainLoading.value = true;
  try {
    const [tp, bs] = await Promise.all([
      getTenderProjectPage({ pageNo: 1, pageSize: 200 }),
      getBidSectionPage({ pageNo: 1, pageSize: 500 }),
    ]);
    tenderProjects.value = tp?.list ?? [];
    sections.value = bs?.list ?? [];
  } catch {
    tenderProjects.value = [];
    sections.value = [];
  } finally {
    chainLoading.value = false;
  }
}
/** 本页空值留白（发布工作台附件行） */
const sizeText = (n?: number): string => fmtBytes(n, '');
async function onPdfBeforeUpload(file: File): Promise<boolean> {
  if (!/\.pdf$/i.test(file.name)) { createMessage.warning('仅支持 PDF 文件'); return false; }
  pdfUploading.value = true;
  try {
    /* 上传口径（规范《11-文件上传与file_id契约》§3.2/§3.3）：单一链路
       主：POST /bidding/file/upload（响应必带 fileId + url + sha256/size）；
       降：预签名直传 /infra/file/presigned-url → PUT → /infra/file/create（同样必须拿到 file_id）。
       拿不到 file_id 时 uploadAttachment 直接抛「文件注册失败，请重试」——绝不回退到让用户手填 ID。 */
    const up = await uploadAttachment(file, 'bidding/notice');
    pdfUrl.value = up.url;
    pdfFileName.value = file.name;
    pdfSize.value = up.size;
    /* 红线 8 三件套：pdfFileId + pdfSha256 + pdfSize 同源留存（fileId 由类型保证必存在，无分支） */
    form.pdfFileId = up.fileId;
    if (up.sha256) form.pdfSha256 = up.sha256;
    createMessage.success('版式文件已上传：file_id=' + up.fileId + (up.sha256 ? '（已附 SHA-256）' : ''));
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
const filters = reactive<{ internalFlag?: boolean; noticeType?: string; status?: string; }>({});

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
 * 企业内部公告量小，开销可忽略；KPI 数字为后端真实计数，不做前端凑数）。
 * 依据：GET /bidding/notice/page 契约（NoticeApi.PageParams.status 过滤，见 api/bidding/notice）。 */
const kpis = ref<Array<{ color: string; note: string; num: number; status?: string; text: string; }>>([]);
const KPI_BASE: Array<{ color: string; note: string; status?: string; text: string; }> = [
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

/* ===== 业务链带值（先项目→招标项目→标段，再对标准段发公告）=====
 * 公告数据集的 TENDER_PROJECT_CODE/BID_SECTION_CODES/NOTICE_BID_NUM 由上游带出，用户不手抄编号。
 * 数据源：后端真实台账（/bidding/tender-project/page、/bidding/bid-section/page）；
 * 前台「项目公告管理」项目→招标项目→标段→公告 的同一套数据，避免本地草稿与台账两套口径。 */
const noticeStage = computed(() => NOTICE_ATTACH_STAGE[form.noticeType] ?? 'NOTICE_TENDER');
const chainTenderProjects = computed<ProjectApi.TenderProject[]>(() => tenderProjects.value);
const chainSections = computed<ProjectApi.BidSection[]>(() => {
  const code = payloadValues.value.TENDER_PROJECT_CODE ?? '';
  return sections.value.filter((s) => !code || (s.tenderProjectCode ?? '') === code);
});
/** 已选标段编号（BID_SECTION_CODES 半角分号分隔，规范口径） */
const selectedSectionCodes = computed<string[]>(() =>
  (payloadValues.value.BID_SECTION_CODES ?? '').split(';').map((s) => s.trim()).filter(Boolean),
);
/** 公告附件的必传采集项缺失（AttachmentItemVO 必填=是，陕西必传口径） */
const noticeAttachMissing = computed(() =>
  stageItems(noticeStage.value)
    .filter((it) => it.required && !noticeAttachments.value.some((a) => a.setCode === it.code))
    .map((it) => it.label),
);
/** 招标项目选项文案（编号 + 名称） */
function tenderProjectLabel(r: ProjectApi.TenderProject): string {
  const code = r.tenderProjectCode ?? '';
  const name = r.tenderProjectName ?? '';
  return code ? code + (name ? ' · ' + name : '') : name || '（未命名招标项目）';
}
/** 标段选项文案（编号 + 名称） */
function bidSectionLabel(r: ProjectApi.BidSection): string {
  const code = r.bidSectionCode ?? '';
  const name = r.bidSectionName ?? '';
  return code ? code + (name ? ' · ' + name : '') : name || '（未命名标段）';
}
/** 选招标项目：带 TENDER_PROJECT_CODE，并清掉不属于该项目的标段选择
 *
 * 2026-09-14 修正一处**会丢用户数据**的缺陷：原实现只在 `sections.value`（首屏 500 条）里判断归属，
 * 于是"已选标段不在已加载页"时会被**静默剔除**（保存即丢）。现改为：不在列表内的编号先用
 * `getBidSectionByCode` 逐个反查真实归属，再决定去留；查不到（已删除）才剔除。 */
async function applyTenderProject(code: string): Promise<void> {
  const tp = tenderProjects.value.find((t) => (t.tenderProjectCode ?? '') === code);
  const unknown = selectedSectionCodes.value.filter(
    (sc) => !sections.value.some((s) => (s.bidSectionCode ?? '') === sc),
  );
  const resolved = await Promise.all(
    unknown.map(async (sc) => {
      try {
        return await getBidSectionByCode(sc);
      } catch {
        return null;
      }
    }),
  );
  const pool = [...sections.value, ...resolved.filter((s): s is ProjectApi.BidSection => !!s)];
  const kept = selectedSectionCodes.value.filter((sc) =>
    pool.some((s) => (s.bidSectionCode ?? '') === sc && (s.tenderProjectCode ?? '') === code),
  );
  const next: Record<string, string> = { ...payloadValues.value, TENDER_PROJECT_CODE: code, BID_SECTION_CODES: kept.join(';') };
  if (kept.length) next.NOTICE_BID_NUM = String(kept.length);
  else delete next.NOTICE_BID_NUM;
  payloadValues.value = next;
  form.projectCode = code;
  form.projectName = tp?.tenderProjectName ?? form.projectName ?? '';
}
/** 选标段（可多选）：带 BID_SECTION_CODES（半角分号）与 NOTICE_BID_NUM（数量） */
function applySections(codes: string[]): void {
  const next: Record<string, string> = { ...payloadValues.value, BID_SECTION_CODES: codes.join(';') };
  if (codes.length) next.NOTICE_BID_NUM = String(codes.length);
  else delete next.NOTICE_BID_NUM;
  payloadValues.value = next;
}
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
  pdfUrl.value = '';
  noticeAttachments.value = [];
  void loadChainOptions();
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
  pdfUrl.value = '';
  noticeAttachments.value = [];
  void loadChainOptions();
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

  /* 必填预校验（企业填报字段 = 规范必填 − 平台注入字段；时间 ISO yyyy-MM-ddTHH:mm:ss 由"现在"带入，校验非空）。
     口径纠正：VO 的「公开范围」是披露范围（政务公开=只报政务平台、不对外公开），不是"谁填报"，
     故不再按 pub 跳过，只跳过平台注入字段（平台码/时间戳/统一交易标识码/附件关联标识号——
     附件关联标识号由附件面板生成）。依据：VO数据规范"必填=是" + meta/types required。 */
  const checklist: { key: string; label: string; pub: string; required: boolean; }[] =
    (payloadMeta.value.fields).map((f) => ({
      key: f.key, label: f.label, required: f.required, pub: f.pub,
    }));
  const bizMissing: string[] = [];
  for (const f of checklist) {
    if (PLATFORM_MAINTAINED_CODES.has(f.key)) continue; // 平台注入：企业表单不收集（附件关联标识号由附件面板写入）
    if (!f.required || (merged[f.key] || '').trim()) continue;
    bizMissing.push(f.label + '（' + f.key + '）');
  }
  if (bizMissing.length) {
    createMessage.warning('权威必填字段未填全（' + bizMissing.length + ' 项）：\n' + bizMissing.slice(0, 12).join('、') + (bizMissing.length > 12 ? '…' : ''));
    return;
  }

  /* 公告附件：ATTACHMENT_CODE（附件关联标识号）保留历史并追加新增——规范：多个标识号半角分号隔开 */
  if (noticeAttachments.value.length) {
    const exist = (merged.ATTACHMENT_CODE ?? '').split(';').map((s) => s.trim()).filter(Boolean);
    const fresh = noticeAttachments.value.map((a) => a.guid).filter((g) => !!g && !exist.includes(g));
    merged.ATTACHMENT_CODE = [...exist, ...fresh].join(';');
  }
  if (noticeAttachMissing.value.length) {
    createMessage.warning('公告必传附件（AttachmentItemVO 必填=是）尚未添加：' + noticeAttachMissing.value.join('、') + '；仍可保存草稿，发布前请补齐');
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

/* ================= 运维动作：索引重建 / 公开同步（**管理端专属**） =================
 * 权限 bidding:notice:update 由后端 @PreAuthorize 把关，但**权限码相同不等于职责相同**：
 * 门户"撤下公告"同样用 bidding:notice:update，故门户用户能通过校验触发平台级索引重建/公开同步。
 * 2026-09-14 按用户口径（企业在后台、个人在前台）在**界面层**收敛为仅管理端可见（v-if="!inPortal"）；
 * 若后端愿意把运维端点拆到独立权限码（如 bidding:notice:ops），界面层这道约束即可降级为冗余双保险。 */
const reindexing = ref<'' | 'all' | 'one'>('');

async function doReindex(all = false) {
  reindexing.value = all ? 'all' : 'one';
  try {
    const result = all ? await reindexAllNotices() : await reindexNotices();
    message.success(result || (all ? '全量重建已完成' : '索引重建已完成'));
  } catch {
    // 静默：403/失败由请求层提示
  } finally {
    reindexing.value = '';
  }
}

const syncOpen = ref(false);
const syncing = ref(false);
const syncBizNo = ref('');

async function doSync() {
  const bizNo = syncBizNo.value.trim();
  if (!bizNo) {
    message.warning('请填写业务单号 bizNo');
    return;
  }
  syncing.value = true;
  try {
    const resp = await syncNoticePublic({ bizNo });
    message.success(`公开同步已受理：taskNo=${resp?.taskNo ?? '—'}｜status=${resp?.status ?? '—'}`);
    syncOpen.value = false;
  } catch {
    // 静默
  } finally {
    syncing.value = false;
  }
}
</script>

<template>
  <div class="bid-ind ind-page">
    <div class="ind-stripes"></div>
    <div v-if="inPortal" class="ind-backbar">
      <a class="text-sm font-bold text-primary" href="#" @click.prevent="goBackHall()">‹ 返回本平台发布</a>
      <span class="text-xs text-muted-foreground">门户 · 企业前台发布（当前登录租户内部公告）</span>
    </div>

    <!-- 页头：石墨墨色面板（沉稳基调）+ 主操作 -->
    <div class="ind-head">
      <div class="min-w-0">
        <div class="ind-title">{{ inPortal ? '公告发布 · 发布工作台（企业前台）' : '公告 / 公示管理 · 发布工作台' }} <span class="ind-mark">NOTICE</span></div>
        <div class="ind-sub">{{ inPortal ? '本企业（当前租户）内部公告：起草 → 发布 → 撤下 全生命周期。发布前需平台授权并完善主体档案；发布后订阅者将收到动态；修订需撤下后重新发布（历史版本保留）。' : '本企业内部公告（T 轨）。招标人发布门：创建/发布需平台授权并完善主体档案；发布后关注订阅者将收到动态。对外法律文书修订需撤下后重新发布（历史版本保留）。' }}</div>
      </div>
      <div class="ind-actions">
        <Button v-if="inPortal" type="primary" size="large" @click="openCreate">＋ 新建公告（草稿）</Button>
        <!-- 运维动作**仅管理端**：门户发布工作台不出现（口径 2026-09-14：企业在后台、个人在前台；
             且这三个端点权限为 bidding:notice:update，而门户"撤下"用的也是该码 ⇒ 门户用户点得动，
             等于把平台级索引重建/公开同步暴露给了前台） -->
        <template v-if="!inPortal">
          <Button size="large" :loading="reindexing === 'one'" @click="doReindex(false)">重建索引</Button>
          <Button size="large" :loading="reindexing === 'all'" @click="doReindex(true)">全量重建</Button>
          <Button size="large" @click="syncOpen = true">公开同步</Button>
        </template>
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
                  <Button v-if="inPortal" size="small" type="link" class="nw-act" @click="openEdit(record as NoticeApi.Row)">编辑</Button>
                  <Button v-if="inPortal" size="small" type="primary" ghost class="nw-act-primary" @click="doPublish(record as NoticeApi.Row)">{{ record.status === 'DRAFT' ? '发布' : '重新发布' }}</Button>
                  <Button v-if="inPortal" size="small" type="link" danger @click="doDelete(record as NoticeApi.Row)">删除</Button>
                  <span v-else class="nw-readonly">待前台发布</span>
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
              <div class="nw-empty-sub">{{ inPortal ? '点右上「新建公告」起草（发布前需主体档案完善 + 平台授权）' : '发布动作在企业前台（门户 · 公告发布），本页只读留痕与下架/运维' }}</div>
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
    <Modal v-model:open="syncOpen" title="公告公开同步（公告 → 归集 → 检索文档）" :footer="null" width="540" destroy-on-close>
      <div class="mb-2 text-xs text-muted-foreground">
        按业务单号触发一次公开同步；后端 <code>@Idempotent</code>，重复提交不会重复归集，返回值即受理回执（taskNo / status）。
      </div>
      <Input v-model:value="syncBizNo" placeholder="业务单号 bizNo（如公告编号 / 项目编号）" allow-clear />
      <div class="mt-1 text-xs text-muted-foreground">dataTimestamp 留空由服务端取当前时间（请求侧时间格式未在契约中约定，故不在表单暴露）。</div>
      <div class="mt-3 flex justify-end gap-2">
        <Button @click="syncOpen = false">取消</Button>
        <Button type="primary" :loading="syncing" @click="doSync">提交同步</Button>
      </div>
    </Modal>

    <Modal v-model:open="editorOpen" :title="editorTitle" :footer="null" width="880" destroy-on-close>
      <div class="nw-modal-body">
        <!-- 第一节：标题与辨识 -->
        <div class="nw-form-sect">
          <div class="nw-sect-h"><span class="nw-sect-no">1</span>标题与辨识<span class="nw-sect-ast">* 为必填</span></div>
          <div class="grid grid-cols-2 gap-3">
            <div class="col-span-2">
              <div class="mb-1 text-xs font-semibold text-foreground">公告名称（标题）*</div>
              <Input v-model:value="form.title" placeholder="如 榆林炼油厂 2026 大修消缺项目施工招标公告" />
            </div>
            <div>
              <div class="mb-1 text-xs font-semibold text-foreground">公告类型 *</div>
              <Select v-model:value="form.noticeType" style="width: 100%">
                <Select.Option v-for="t in NoticeApi.NOTICE_TYPES" :key="t.code" :value="t.code">{{ t.label }}</Select.Option>
              </Select>
            </div>
            <div>
              <div class="mb-1 text-xs font-semibold text-foreground">发布单位</div>
              <Input v-model:value="form.publisherName" placeholder="本企业名称快照（可留空由后端取）" />
            </div>
            <div>
              <div class="mb-1 text-xs font-semibold text-foreground">公开范围</div>
              <Select v-model:value="form.publicLevel" style="width: 100%">
                <Select.Option value="SOCIAL">社会公开（大厅可见）</Select.Option>
                <Select.Option value="GOVERNMENT">政府公开</Select.Option>
              </Select>
            </div>
            <div class="flex items-end pb-1">
              <div class="flex items-center gap-2 text-xs text-muted-foreground"><Switch v-model:checked="form.internalFlag" size="small" /> 企业内部公告（仅本租户可见，不进入大厅）</div>
            </div>
          </div>
        </div>

        <!-- 第二节：项目关联 -->
        <div class="nw-form-sect">
          <div class="nw-sect-h">
<span class="nw-sect-no">2</span>业务链关联
            <span class="nw-sect-sub">先项目 → 再招标项目 → 再标段，公告对“标段”发布：招标项目编号/标段编号/标段数量由上游带出，不手抄</span>
          </div>
          <div class="grid grid-cols-2 gap-3">
            <div>
              <div class="mb-1 text-xs font-semibold text-foreground">招标项目（TENDER_PROJECT_CODE）</div>
              <Select
                :value="payloadValues.TENDER_PROJECT_CODE || undefined"
                class="w-full"
                show-search
                option-filter-prop="label"
                placeholder="选择已登记的招标项目（前台项目公告管理中创建）"
                @update:value="(v) => applyTenderProject(String(v ?? ''))"
              >
                <Select.Option v-for="t in chainTenderProjects" :key="t.id" :value="t.tenderProjectCode || ''" :label="tenderProjectLabel(t)">
                  {{ tenderProjectLabel(t) }}
                </Select.Option>
              </Select>
            </div>
            <div>
              <div class="mb-1 text-xs font-semibold text-foreground">项目编号（表单字段 projectCode）</div>
              <Input v-model:value="form.projectCode" placeholder="随招标项目自动带出" />
            </div>
            <div class="col-span-2">
              <div class="mb-1 text-xs font-semibold text-foreground">
                相关标段（包）（BID_SECTION_CODES，多选，半角分号分隔）
                <span class="ml-2 font-normal text-muted-foreground">公告标段（包）数量 NOTICE_BID_NUM 按选择个数自动写入</span>
              </div>
              <Select
                mode="multiple"
                :value="selectedSectionCodes"
                class="w-full"
                placeholder="选择标段（可多选）；未选到请先在项目公告管理中建立标段"
                @update:value="(v) => applySections((v as string[]) ?? [])"
              >
                <Select.Option v-for="s in chainSections" :key="s.id" :value="s.bidSectionCode || ''">
                  {{ bidSectionLabel(s) }}
                </Select.Option>
              </Select>
              <div class="mt-1 text-xs text-muted-foreground">
                已选 {{ selectedSectionCodes.length }} 个标段<template v-if="payloadValues.NOTICE_BID_NUM"> · NOTICE_BID_NUM={{ payloadValues.NOTICE_BID_NUM }}</template>
                <span v-if="!chainTenderProjects.length" class="ml-2 text-amber-600 dark:text-amber-400">尚无招标项目草稿：请到「招投标业务链（VO 设计稿）」按 项目 → 招标项目 → 标段 顺序建立</span>
              </div>
            </div>
          </div>
        </div>

        <!-- 第三节：正文与版式 -->
        <div class="nw-form-sect">
          <div class="nw-sect-h"><span class="nw-sect-no">3</span>正文与版式<span class="nw-sect-sub">发布前必填；实质变更自动追加版本</span></div>
          <div class="grid grid-cols-1 gap-3">
            <div>
              <div class="mb-1 text-xs font-semibold text-foreground">公告正文（全文）</div>
              <Input.TextArea v-model:value="form.content" :rows="6" placeholder="公告正文全文…" />
            </div>
            <div class="nw-pdf-row">
              <div class="mb-1 flex flex-wrap items-center gap-3 text-xs font-semibold text-foreground">
                <span>公告版式文件（PDF，发布前必填）— 文件服务上传，保存只带 file_id；URL 由业务系统按可见性签发</span>
                <Upload :show-upload-list="false" accept=".pdf" :before-upload="onPdfBeforeUpload">
                  <Button size="small" :loading="pdfUploading">{{ form.pdfFileId !== undefined ? '重新上传 PDF' : '上传 PDF' }}</Button>
                </Upload>
              </div>
              <div v-if="form.pdfFileId !== undefined || pdfFileName" class="text-xs text-muted-foreground">
                file_id #{{ form.pdfFileId ?? '—' }}<template v-if="pdfFileName"> · {{ pdfFileName }}</template><template v-if="pdfSize"> · {{ sizeText(pdfSize) }}</template>
                <span v-if="form.pdfFileId !== undefined" class="ml-2 text-amber-600 dark:text-amber-400">（{{ form.internalFlag || form.publicLevel === 'GOVERNMENT' ? '限定可见' : '社会公开公告可免费查阅' }}）</span>
                <a v-if="pdfUrl" :href="pdfUrl" target="_blank" rel="noopener noreferrer" class="ml-2">查看已上传文件</a>
                <Button
                  v-if="pdfUrl || form.pdfFileId !== undefined"
                  class="ml-2"
                  size="small"
                  type="link"
                  @click="pdfPreviewOpen = true"
                >
内嵌预览
</Button>
              </div>
              <PdfPreview
                v-model:open="pdfPreviewOpen"
                :url="pdfUrl"
                :file-id="form.pdfFileId"
                :file-name="pdfFileName || '公告版式文件.pdf'"
                title="公告版式文件预览"
              />
            </div>
          </div>
        </div>

        <!-- 第四节：权威数据集字段 —— 四张类型专属表（招标公告/变更公告/中标候选人公示/中标结果公示），按所选类型渲染对应表。
             title/content（第1/3节）自动并入权威名称/正文键；政务报送参数（平台码/时间戳）不在此收集。 -->
        <div class="nw-form-sect">
          <div class="nw-sect-h">
<span class="nw-sect-no">4</span>权威数据集字段
            <span class="nw-sect-sub">当前类型：{{ typeLabel }} · 按类型专属表分节填报（必填=VO数据规范“必填=是”/meta/types）</span>
          </div>
          <div>
            <TenderNoticeTable v-if="form.noticeType === 'TENDER_NOTICE'" :type-meta="currentTypeMeta" v-model:values="payloadValues" />
            <ChangeNoticeTable v-else-if="form.noticeType === 'CHANGE_NOTICE'" :type-meta="currentTypeMeta" v-model:values="payloadValues" />
            <CandidatePublicityTable v-else-if="form.noticeType === 'WIN_CANDIDATE_PUBLICITY'" :type-meta="currentTypeMeta" v-model:values="payloadValues" />
            <ResultPublicityTable v-else-if="form.noticeType === 'WIN_RESULT_PUBLICITY'" :type-meta="currentTypeMeta" v-model:values="payloadValues" />
            <div class="mt-2 text-xs text-muted-foreground">公告名称与正文已在上方第 1、3 节填写，保存时自动并入权威字段（NOTICE_NAME/NOTICE_CONTENT 等）；报送平台参数由系统/平台维护，不在表单中。</div>
          </div>
        </div>

        <!-- 第五节：公告附件（AttachmentSetVO 附件集结构，按本类型公告的采集项 AttachmentItemVO 分组，可加多个） -->
        <div class="nw-form-sect">
          <div class="nw-sect-h">
<span class="nw-sect-no">5</span>公告附件
            <span class="nw-sect-sub">附件集结构 AttachmentSetVO · 采集项 AttachmentItemVO；同一采集项可加多个，保存时并入 ATTACHMENT_CODE（半角分号分隔）</span>
          </div>
          <AttachmentsPanel
            v-model="noticeAttachments"
            :stage="noticeStage"
            :title="`${typeLabel }附件`"
            directory="bidding/notice"
          />
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
          <span class="text-muted-foreground">{{ detail.noticeCode }}</span>
          <span class="text-muted-foreground">版本 v{{ detail.contentVersion ?? '—' }}</span>
        </div>
        <div class="mb-2 grid grid-cols-2 gap-x-4 gap-y-1 text-xs text-muted-foreground">
          <div>项目：{{ detail.projectCode || '—' }} {{ detail.projectName || '' }}</div>
          <div>发布单位：{{ detail.publisherName || '—' }}</div>
          <div>发布时间：{{ fmtT(detail.publishTime) }}</div>
          <div>公开级别：{{ detail.publicLevel || '—' }}<span v-if="detail.internalFlag">（内部）</span></div>
        </div>
        <div class="rounded-md border border-border p-3">
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
.nw-kpi-tap:hover { transform: translateY(-1px); box-shadow: 0 4px 12px rgb(0 0 0 / 8%); }
.nw-kpi-tap.nw-kpi-on {
  border-color: hsl(var(--primary));
  box-shadow: 0 0 0 1.5px hsl(var(--primary)) inset;
}
.nw-kpi-tap.nw-kpi-on .k-label { color: hsl(var(--primary)); }
.nw-kpi-empty { padding: 8px 2px; font-size: 13px; color: hsl(var(--muted-foreground)); }

/* 栏目二 台账表壳：标题条 + 内联筛选 + 表格 + 表尾 */
.nw-table-shell { margin-top: 14px; }
.nw-shell-head {
  display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 10px;
  padding: 10px 14px; border-bottom: 1px solid hsl(var(--border)); background: hsl(var(--muted) / 50%);
}
.nw-shell-title { font-size: 15px; font-weight: 600; color: hsl(var(--foreground)); }
.nw-shell-meta { margin-left: 10px; font-size: 12.5px; font-weight: 400; color: hsl(var(--muted-foreground)); }
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
.nw-title-sub .nw-code { font-family: ui-monospace, SFMono-Regular, Menlo, monospace; color: hsl(var(--muted-foreground)); }
.nw-type-text { color: hsl(var(--muted-foreground)); font-weight: 400; }
.nw-time { font-variant-numeric: tabular-nums; color: hsl(var(--muted-foreground)); }
.nw-readonly { color: hsl(var(--muted-foreground) / 70%); font-size: 12.5px; }

/* 行操作按钮保持紧凑 */
.nw-act { padding: 0 6px !important; }
.nw-act-primary { padding: 0 10px !important; }
.nw-act-warn { padding: 0 6px !important; color: var(--ind-warn) !important; }
.nw-act-warn:hover { color: var(--ind-warn) !important; opacity: .8; }

/* 空态 */
.nw-empty { padding: 34px 0 30px; text-align: center; }
.nw-empty-title { font-size: 15px; font-weight: 600; color: hsl(var(--muted-foreground)); }
.nw-empty-sub { margin-top: 6px; font-size: 12.5px; color: hsl(var(--muted-foreground) / 70%); }

/* 表尾分页条 */
.nw-pager {
  display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 8px;
  padding: 10px 14px; border-top: 1px solid hsl(var(--border)); background: hsl(var(--muted) / 40%);
}
.nw-pager-total { font-size: 12.5px; color: hsl(var(--muted-foreground)); }
.nw-pager-total b { color: hsl(var(--foreground)); font-variant-numeric: tabular-nums; }
.nw-pager-btns { display: flex; gap: 8px; }

/* 弹窗分节表单 */
.nw-modal-body { max-height: 72vh; overflow-y: auto; padding-right: 6px; }
.nw-form-sect { margin-bottom: 16px; padding: 14px 14px 4px; border: 1px solid hsl(var(--border)); border-left: 3px solid hsl(var(--primary)); border-radius: var(--radius); background: hsl(var(--card)); }
.nw-sect-h { display: flex; align-items: center; gap: 8px; margin-bottom: 10px; font-size: 14.5px; font-weight: 600; color: hsl(var(--foreground)); }
.nw-sect-no {
  display: inline-flex; align-items: center; justify-content: center; width: 20px; height: 20px;
  background: hsl(var(--primary) / 10%); color: hsl(var(--primary)); font-size: 11.5px; font-weight: 600; border-radius: 4px; letter-spacing: .5px;
}
.nw-sect-ast { font-size: 11.5px; font-weight: 500; color: var(--ind-warn); }
.nw-sect-sub { font-size: 12px; font-weight: 400; color: hsl(var(--muted-foreground)); margin-left: 2px; }
.nw-pdf-row { background: hsl(var(--warning) / 8%); border: 1px dashed hsl(var(--warning) / 45%); border-radius: var(--radius); padding: 10px 12px; }
.nw-modal-foot { display: flex; justify-content: flex-end; border-top: 1px solid hsl(var(--border)); padding-top: 12px; }

/* 详情抽屉正文轻边框 */
.nw-detail-doc { border: 1px solid hsl(var(--border)); border-radius: var(--radius); }
</style>
