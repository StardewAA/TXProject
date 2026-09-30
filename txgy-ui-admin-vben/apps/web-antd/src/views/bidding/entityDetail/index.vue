<script setup lang="ts">
import type { AttachmentRecord } from '../vo/attachmentStages';

/* 通用实体详情页（静态路由 /bidding/entity/:type/:code，不挂菜单，与 /portal 同机制）
 * 2026-09-16 重构：四级下钻「项目 → 招标项目 → 标段（包）→ 公告」逐级独立成页，每页只渲染
 *   「当前层结构化主档 + 直属下一层列表」，靠页面内自制裁剪面包屑（可点击/悬停显名/过长折叠）导航，不用左侧树。
 *   · PROJECT            —— ① 项目主档（结构化分组） ② 关联招标项目列表（不出现标段/公告）
 *   · TENDER_PROJECT     —— ① 招标项目主档 ② 关联标段列表（不出现公告）
 *   · BID_SECTION        —— ① 标段主档 ② 关联公告列表（口径标注：内部公告按项目聚合，待后端补齐）
 *   · NOTICE             —— ① 公告详情（NoticeHtmlView） ② 发布工作台（NoticeWorkbench mode="admin"）
 *   · NOTICE_EXTERNAL    —— 保留现有审核预览形态（不参与四级下钻）
 * 主档中文标签/分组：复用 ENTITY_DEFS[type].groups + recordToFields + VO_SPEC（数据规范 V2.0）——不再 Object.entries 倾倒英文键。
 * 数据完全走现有 trace 链路及 各域 CRUD/审核 接口 → **后端零改动**。
 * 入口：项目台账列表行「详情 ↗」（/bidding/project）；公告页面包屑由下钻时 query 携带上级编号（方案B）。 */
import { computed, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { useVbenModal } from '@vben/common-ui';

import { Button, message, Modal, Select, Upload } from 'ant-design-vue';

import {
  ATTACHMENT_TYPE_OPTIONS,
  type AttachmentBizType,
  type AttachmentItem,
  attachmentTypeText,
  deleteAttachment,
  listAttachments,
  saveAttachment,
} from '#/api/bidding/attachment';
import { CaApi } from '#/api/bidding/ca';
import { uploadBiddingFile } from '#/api/bidding/file';
import { announcementStatusColorOf, announcementStatusTextOf, loadFlowDicts } from '#/api/bidding/flow/labels';
import { fetchNoticeDetail, NoticeApi } from '#/api/bidding/notice';
import {
  getBidSection,
  getBidSectionByCode,
  getProject,
  getProjectByCode,
  getTenderProject,
  getTenderProjectByCode,
  type ProjectApi,
  saveBidSection,
  saveProject,
  saveTenderProject,
  traceBidSection,
  traceProject,
  traceTenderProject,
} from '#/api/bidding/project';
import { useBiddingDraftStore } from '#/store/bidding-draft';

import { getBidDepositPage, saveBidDeposit } from '#/api/bidding/deposit';
import { archiveBiddingContract, getBiddingContractPage, saveBiddingContract } from '#/api/bidding/contract';
import {
  getBidOpeningList,
  OPENING_STATUS_TEXT,
  ROOM_STATUS_TEXT,
} from '#/api/bidding/opening';
import { fetchProcessTrackPage } from '#/api/bidding/processTrack';
import { getPrequalPage } from '#/api/bidding/prequal';

import CaSealAuth from '../ca/CaSealAuth.vue';
import PrequalPanel from '../project/modules/PrequalPanel.vue';
import { fmtDateTime } from '../certificate/format';
// 错误 → 可读文案：请求器在业务 code 非 0 时抛的是普通对象，直接 String(e) 会得到 "[object Object]"
import { crawlErrMsg } from '../crawlExternal/shared';
import FlowPanorama from '../flow/FlowPanorama.vue';
import NoticeHtmlView from '../notice/NoticeHtmlView.vue';
import InternalNoticePreview from '../notice/InternalNoticePreview.vue';
import { generateNoticePdfBlob } from '../notice/pdfGen';
import { buildNoticeSections } from '../notice/noticeSections';
import NoticeWorkbench from '../notice/NoticeWorkbench.vue';
import DetailModal from '../noticeExternal/modules/detail.vue';
import AttachmentList from '../vo/AttachmentList.vue';
import { loadAttachments } from '../vo/attachmentStore';
import EntityFormModal from '../vo/EntityFormModal.vue';
import { ENTITY_DEFS, type EntityDef, MASTER_CONFIG } from '../vo/entityForms';
import MainCardTable from '../vo/MainCardTable.vue';

type EntityType = 'BID_SECTION' | 'NOTICE' | 'NOTICE_EXTERNAL' | 'PROJECT' | 'TENDER_PROJECT';
type DomainKey = 'bidSection' | 'notice' | 'project' | 'tenderProject';

/* ===== 面包屑折叠规则（约定，勿随手改样式）：≤4 段全部显示；≥5 段折叠为 首段 + … + 末 2 段 ===== */
const FOLD_AT = 5;

const route = useRoute();
const router = useRouter();
const type = computed<EntityType>(() => (String(route.params.type ?? '') as EntityType).toUpperCase() as EntityType);
const code = computed(() => String(route.params.code ?? ''));

const loading = ref(false);
const error = ref('');
const record = ref<null | Record<string, unknown>>(null);
const trace = ref<null | ProjectApi.Trace>(null);
const notice = ref<NoticeApi.Detail | null>(null);

/** 公告状态：优先 announcementStatus（契约 26 §3.4 五态，announcement_status 字典），回退旧 status 四态静态映射 */
function noticeStatusText(n: NoticeApi.Row): string {
  if (n.announcementStatus) return announcementStatusTextOf(n.announcementStatus);
  return NoticeApi.NOTICE_STATUS_LABEL[n.status ?? ''] ?? n.status ?? '—';
}
function noticeStatusColor(n: NoticeApi.Row): string {
  return announcementStatusColorOf(n.announcementStatus ?? n.status ?? '');
}

/* ===== 通用附件域（契约 29 §2/§3：bidding_attachment 一对 N 附件；新机制优先，DDL 未执行时静默降级） ===== */
const serverAttach = ref<AttachmentItem[]>([]);
const attachReady = ref(false);
const attachLoading = ref(false);
const attachUploading = ref(false);
const attachType = ref('');
const attachAsName = ref('');
/** 实体类型 → 附件归属类型（NOTICE→ANNOUNCEMENT；NOTICE_EXTERNAL 走外部公告自己的附件表，不适用） */
const attachBizType = computed<AttachmentBizType | null>(() => {
  if (type.value === 'PROJECT') return 'PROJECT';
  if (type.value === 'TENDER_PROJECT') return 'TENDER_PROJECT';
  if (type.value === 'BID_SECTION') return 'BID_SECTION';
  if (type.value === 'NOTICE') return 'ANNOUNCEMENT';
  return null;
});

/** 新机制附件行 → 展示记录 */
function toAttachRecord(a: AttachmentItem): AttachmentRecord {
  return {
    guid: 'att-' + a.id,
    rowId: a.id,
    setCode: a.attachmentType ?? 'OTHER_FILE',
    name: a.attachmentName || attachmentTypeText(a.attachmentType),
    type: a.mimeType ?? '',
    fileName: a.fileName ?? '',
    url: a.url ?? '',
    fileId: a.fileId,
    size: a.size,
  };
}
/** 展示列表：新机制优先；未就绪（DDL 未执行）→ 旧 localStorage 兜底 */
const displayAttachments = computed<AttachmentRecord[]>(() => {
  if (attachReady.value) return serverAttach.value.map(toAttachRecord);
  return attachments.value;
});

/** 加载新机制附件列表；失败（如表不存在）→ attachReady=false 静默降级 */
async function loadServerAttachments(): Promise<void> {
  const bt = attachBizType.value;
  const c = code.value;
  if (!bt || !c) return;
  attachLoading.value = true;
  try {
    serverAttach.value = await listAttachments(bt, c);
    attachReady.value = true;
  } catch {
    serverAttach.value = [];
    attachReady.value = false;
  } finally {
    attachLoading.value = false;
  }
}

/** 上传 → 绑定（契约 29 §3.1：POST /bidding/file/upload 拿 fileId → attachment/save 绑定） */
async function handleAttachUpload(file: File): Promise<void> {
  const bt = attachBizType.value;
  const c = code.value;
  if (!bt || !c) return;
  attachUploading.value = true;
  try {
    const up = await uploadBiddingFile(file);
    if (!up.fileId) throw new Error('上传未返回 fileId，请重试');
    // 绑定返回值带附件行 id —— 后续「用签章件替换原文件」要按这个 id 解绑原行（严格意义上的"换掉那一行"）
    const bound = await saveAttachment({
      bizType: bt,
      bizCode: c,
      fileId: up.fileId,
      attachmentType: attachType.value || undefined,
      attachmentName: attachAsName.value.trim() || undefined,
      url: up.url,
      fileName: up.name ?? file.name,
      sha256: up.sha256,
      size: up.size,
    });
    message.success('附件已绑定');
    attachAsName.value = '';
    await loadServerAttachments();
    // 上传回调后强制 CA 认证：未认证 → 打开认证工作台（用户裁定：上传后文件必须 CA 认证）
    void checkSealAfterAttach({ ...up, name: up.name ?? file.name }, file, bound?.id);
  } catch (e) {
    const m = crawlErrMsg(e);
    message.error(/bidding_attachment|doesn't exist|1146/i.test(m) ? '附件服务未就绪：请后端执行 DDL（sql/history/2026-09-27-bidding-attachment.sql）' : '绑定失败：' + m);
  } finally {
    attachUploading.value = false;
  }
}

/** 附件强制 CA 认证门：上传回调后自动 check；未认证 → 警告 + 强制打开 CA 认证工作台（盖章后落台账） */
const authTarget = ref<{
  /** 本次盖章对应的附件行 id：盖章后可「用签章件替换原文件」按它解绑原行 */
  attachmentId?: number | string;
  fileId?: number | string;
  fileName?: string;
  name?: string;
  sha256?: string;
  url?: string;
} | null>(null);
const authUploaded = ref<File | null>(null);
const authOpen = ref(false);
async function checkSealAfterAttach(
  up: { fileId?: number | string; name?: string; sha256?: string; },
  rawFile?: File,
  attachmentId?: number | string,
): Promise<void> {
  try {
    if (up.fileId === undefined || up.fileId === null || up.fileId === '') return;
    const chk = await CaApi.checkSealAuth(up.fileId, up.sha256);
    if (chk?.caAuthed === false) {
      message.warning('「' + (up.name || '该文件') + '」未 CA 认证——已为您打开认证工作台，盖章后方可对外使用');
      authTarget.value = {
        attachmentId,
        fileId: up.fileId,
        fileName: up.name,
        name: up.name,
        sha256: up.sha256,
        url: undefined,
      };
      authUploaded.value = rawFile ?? null;
      authOpen.value = true;
    }
  } catch (e) {
    void e; // check 接口不可达不阻断上传（可后续手动认证）
  }
}

/** 上传前文件检查（限制常见附件类型与大小） */
function beforeAttachUpload(f: File): boolean {
  if (f.size > 50 * 1024 * 1024) {
    message.warning('单个附件不能超过 50MB');
    return false;
  }
  return true;
}

/** 解绑（确认后删；契约 29 §2.3 幂等） */
function handleAttachDelete(item: AttachmentItem): void {
  Modal.confirm({
    title: '解绑附件',
    content: '确认解绑「' + (item.fileName || item.attachmentName || item.id) + '」？文件本体不变（仅解除与当前实体的绑定）。',
    okText: '解绑',
    okButtonProps: { danger: true },
    onOk: async () => {
      try {
        await deleteAttachment(item.id);
        serverAttach.value = serverAttach.value.filter((x) => x.id !== item.id);
        message.success('已解绑');
      } catch (e) {
        message.error(crawlErrMsg(e) || '解绑失败');
      }
    },
  });
}

const draftStore = useBiddingDraftStore();

/** 新建态（内存草稿）：/bidding/entity/:type/DRAFT_xxx?mode=create */
const isCreateMode = computed(() => route.query.mode === 'create');

/** 新建态草稿已填字段（供 MainCardTable seedFields 恢复） */
const seedFields = computed<Record<string, string>>(() =>
  isCreateMode.value ? (draftStore.findDraft(code.value)?.fields ?? {}) : {},
);

async function doLoad(): Promise<void> {
  if (!code.value) return;
  loading.value = true;
  error.value = '';
  record.value = null;
  trace.value = null;
  notice.value = null;
  try {
    if (type.value === 'PROJECT' || type.value === 'TENDER_PROJECT' || type.value === 'BID_SECTION') {
      if (isCreateMode.value) {
        // 新建态：校验草稿存在，不存在则提示并跳回父级列表
        const draft = draftStore.findDraft(code.value);
        if (!draft) {
          message.warning('草稿已失效，请重新新建');
          if (type.value === 'PROJECT') {
            void router.replace('/bidding/project');
            return;
          }
          const parent = String(route.query.projectCode || route.query.tenderProjectCode || '');
          const parentType = type.value === 'TENDER_PROJECT' ? 'PROJECT' : 'TENDER_PROJECT';
          if (parent) void router.replace(`/bidding/entity/${parentType}/${encodeURIComponent(parent)}`);
          return;
        }
        // 新建态不拉后端数据；record 仅含上级归属编号（其余字段为空）
        record.value = {
          ...(route.query.projectCode ? { projectCode: String(route.query.projectCode) } : {}),
          ...(route.query.tenderProjectCode ? { tenderProjectCode: String(route.query.tenderProjectCode) } : {}),
        } as Record<string, unknown>;
        trace.value = null;
      } else if (type.value === 'PROJECT') {
        record.value = (await getProjectByCode(code.value)) as unknown as null | Record<string, unknown>;
        trace.value = await traceProject(code.value);
      } else if (type.value === 'TENDER_PROJECT') {
        record.value = (await getTenderProjectByCode(code.value)) as unknown as null | Record<string, unknown>;
        trace.value = await traceTenderProject(code.value);
      } else {
        record.value = (await getBidSectionByCode(code.value)) as unknown as null | Record<string, unknown>;
        trace.value = await traceBidSection(code.value);
      }
    } else if (type.value === 'NOTICE') {
      if (isCreateMode.value) {
        // 新建态（内存草稿）：校验草稿存在，不存在则提示并跳回父级标段；草稿无后端记录，不拉详情
        const draft = draftStore.findDraft(code.value);
        if (!draft) {
          message.warning('草稿已失效，请重新新建');
          const parent = String(route.query.bidSectionCode || route.query.tenderProjectCode || '');
          const parentType = route.query.bidSectionCode ? 'BID_SECTION' : 'TENDER_PROJECT';
          if (parent) void router.replace(`/bidding/entity/${parentType}/${encodeURIComponent(parent)}`);
          return;
        }
        notice.value = null;
      } else {
        // 公告 id 为雪花 ID（19 位，超过 JS Number 安全整数范围）：必须原样传字符串，
        // 不能 Number() 转换（会精度丢失导致按失真 id 查询不到 → 点击标段下公告报错）
        notice.value = await fetchNoticeDetail(code.value);
      }
    }
    // NOTICE_EXTERNAL：详情在审核预览弹窗内按 id 取（manage/get），此处不预取
  } catch (e) {
    error.value = crawlErrMsg(e);
  } finally {
    loading.value = false;
  }
}
onMounted(() => {
  // 公告状态字典（契约 26 §8：announcement_status 五态；字典不可用时静态中文兜底）
  void loadFlowDicts();
  void doLoad();
  void loadSectionBiz();
});
watch(() => route.params.code, doLoad);
watch(() => [route.params.code, isCreateMode.value], () => void loadSectionBiz());
// 附件服务就绪检查（无感：DDL 未执行时静默降级，不打断页面）
void loadServerAttachments();

/* ============================================================
 * 页面内自制裁剪面包屑（a-breadcrumb 渲染，不依赖 Vben 页级面包屑）
 * ============================================================ */
interface Crumb {
  text: string;
  code?: string;
  type?: EntityType;
  href?: string;
  name?: string;
  bold?: boolean;
  /** 折叠占位：可展开的隐藏中间层 */
  fold?: Crumb[];
}

/** 由 trace 快照反查父级编号，逐级回跳 /bidding/entity/:type/:code。 */
const crumbs = computed<Crumb[]>(() => {
  const base: Crumb = { text: '项目台账', href: '/bidding/project' };
  const t = type.value;
  const tr = trace.value;
  const projectCode = tr?.project?.projectCode;
  const projectName = tr?.project?.projectName;
  const tp = tr?.tenderProjects?.[0];
  const tpCode = tp?.tenderProjectCode;
  const tpName = tp?.tenderProjectName;
  const q = route.query;

  if (t === 'PROJECT') {
    return [base, { text: code.value, bold: true, code: code.value, type: 'PROJECT', name: projectName }];
  }
  if (t === 'TENDER_PROJECT') {
    return [
      base,
      projectCode ? { text: projectCode, href: `/bidding/entity/PROJECT/${encodeURIComponent(projectCode)}`, code: projectCode, type: 'PROJECT', name: projectName } : null,
      { text: code.value, bold: true, code: code.value, type: 'TENDER_PROJECT', name: tpName },
    ].filter(Boolean) as Crumb[];
  }
  if (t === 'BID_SECTION') {
    const selfSection = (tr?.sections ?? []).find((s) => s.bidSectionCode === code.value);
    return [
      base,
      projectCode ? { text: projectCode, href: `/bidding/entity/PROJECT/${encodeURIComponent(projectCode)}`, code: projectCode, type: 'PROJECT', name: projectName } : null,
      tpCode ? { text: tpCode, href: `/bidding/entity/TENDER_PROJECT/${encodeURIComponent(tpCode)}`, code: tpCode, type: 'TENDER_PROJECT', name: tpName } : null,
      { text: code.value, bold: true, code: code.value, type: 'BID_SECTION', name: selfSection?.bidSectionName },
    ].filter(Boolean) as Crumb[];
  }
  if (t === 'NOTICE') {
    const list: Crumb[] = [base];
    const pCode = String(q.projectCode ?? '');
    const pName = String(q.projectName ?? '');
    const tCode = String(q.tenderProjectCode ?? '');
    const tName = String(q.tenderProjectName ?? '');
    const bCode = String(q.bidSectionCode ?? '');
    const bName = String(q.bidSectionName ?? '');
    if (pCode) list.push({ text: pCode, href: `/bidding/entity/PROJECT/${encodeURIComponent(pCode)}`, code: pCode, type: 'PROJECT', name: pName });
    if (tCode) list.push({ text: tCode, href: `/bidding/entity/TENDER_PROJECT/${encodeURIComponent(tCode)}`, code: tCode, type: 'TENDER_PROJECT', name: tName });
    if (bCode) list.push({ text: bCode, href: `/bidding/entity/BID_SECTION/${encodeURIComponent(bCode)}`, code: bCode, type: 'BID_SECTION', name: bName });
    list.push({ text: notice.value?.noticeCode || code.value, bold: true, code: notice.value?.noticeCode || code.value, type: 'NOTICE', name: notice.value?.title });
    return list;
  }
  if (t === 'NOTICE_EXTERNAL') {
    return [base, { text: code.value, bold: true, code: code.value, type: 'NOTICE_EXTERNAL' }];
  }
  return [base];
});

/** 应用折叠规则（≤4 全显；≥5 折叠为 首段 + … + 末 2 段），折叠项可下拉展开。 */
const breadcrumbView = computed<Crumb[]>(() => {
  const all = crumbs.value;
  if (all.length <= FOLD_AT) return all;
  return [...all.slice(0, 1), { text: '…', fold: all.slice(1, all.length - 2) }, ...all.slice(all.length - 2)];
});

/* ============================================================
 * 结构化主档（ENTITY_DEFS[type].groups 分组 + recordToFields + VO_SPEC 中文标签）
 * ============================================================ */
const selfDef = computed<EntityDef | null>(() => {
  if (type.value === 'PROJECT') return ENTITY_DEFS.project;
  if (type.value === 'TENDER_PROJECT') return ENTITY_DEFS.tenderProject;
  if (type.value === 'BID_SECTION') return ENTITY_DEFS.bidSection;
  return null;
});

/** 当前实体主档卡片的配置（分组 / 可编辑 / 不可编辑字段，驱动 MainCardTable） */
const master = computed(() => (selfDef.value ? MASTER_CONFIG[selfDef.value.key] : null));

/** 标段详情页「关联公告」：标段维度反向查看（公告可关联多标段，标段侧需知道哪些公告关联了它）。
 * 口径：外部公告按 BID_SECTION_CODES 分号串精确匹配；内部公告按追溯首段归属匹配（契约 A 部分），
 * 多段精确回写待契约 B（bidSectionCodes 复数，见 api/bidding/project TraceNotice）。 */
const sectionNotices = computed(() => {
  const t = trace.value;
  if (!t || type.value !== 'BID_SECTION') return { internal: [], external: [] };
  const self = code.value;
  return {
    internal: (t.notices ?? []).filter((n) => n.bidSectionCode === self),
    external: (t.externalNotices ?? []).filter((n) => (n.bidSectionCodes ?? '').split(/[;,]/).includes(self)),
  };
});

/** 标段详情「投标与材料」：按标段聚合 保证金 / 合同签署（接口现成，按 bidSectionCode 过滤）。
 * 投标记录 / 开标依赖契约 R5（/bidding/process-track/page 补 bidSectionCode 过滤 + 新增 opening page），后端落地后再并入。 */
const depositRows = ref<Array<any>>([]);
const contractRows = ref<Array<any>>([]);
/** 投标记录（契约 R5：/bidding/process-track/page?bidSectionCode= 已落地） */
const trackRows = ref<Array<any>>([]);
/** 开标记录（契约 R5：/bidding/opening/page?bidSectionCode= 已落地） */
const openingRows = ref<Array<any>>([]);
/** 资格预审（契约 R6：apply-page 请求标段过滤；前端以响应 bidSectionCode 精确兜底） */
const prequalRows = ref<Array<any>>([]);
const sectionBizLoading = ref(false);
async function loadSectionBiz(): Promise<void> {
  if (type.value !== 'BID_SECTION' || isCreateMode.value || !code.value) return;
  sectionBizLoading.value = true;
  try {
    const [d, c, t, o, p] = await Promise.all([
      getBidDepositPage({ pageNo: 1, pageSize: 50, bidSectionCode: code.value }),
      getBiddingContractPage({ pageNo: 1, pageSize: 50, bidSectionCode: code.value }),
      fetchProcessTrackPage({ pageNo: 1, pageSize: 50, bidSectionCode: code.value }),
      getBidOpeningList(code.value),
      getPrequalPage({ pageNo: 1, pageSize: 50, bidSectionCode: code.value }),
    ]);
    depositRows.value = (d?.list ?? []) as any[];
    contractRows.value = (c?.list ?? []) as any[];
    trackRows.value = (t?.list ?? []) as any[];
    openingRows.value = (o ?? []) as any[];
    // 契约 R6：请求标段过滤（后端已写未提交）；前端以响应 bidSectionCode 精确兜底，无论 R6 是否生效展示都正确
    prequalRows.value = (p?.list ?? []).filter(
      (r: Record<string, unknown>) => String(r.bidSectionCode ?? '') === code.value,
    ) as any[];
  } catch {
    depositRows.value = [];
    contractRows.value = [];
    trackRows.value = [];
    openingRows.value = [];
    prequalRows.value = [];
  } finally {
    sectionBizLoading.value = false;
  }
}

/** 唱标明细解密状态文案（open_list[].decryptStatus，原码兜底） */
function openDecryptText(s?: string): string {
  return s === 'PENDING' ? '未解密' : s === 'DECRYPTED' || s === 'SUCCESS' ? '已解密' : s === 'FAILED' ? '解密失败' : s || '—';
}

/** 保证金登记（按标段：平台/招标方登记某投标主体的缴纳记录；字段走自由输入，字典由后端权威校验） */
const depositModalOpen = ref(false);
const depositSaving = ref(false);
const depositForm = ref<{
  subjectId: string;
  amount?: number | string;
  currencyCode?: string;
  payType?: string;
  status?: string;
  depositTime?: string;
  remark?: string;
}>({ subjectId: '', currencyCode: 'CNY' });
function openDepositCreate(): void {
  depositForm.value = { subjectId: '', currencyCode: 'CNY', amount: undefined, payType: undefined, status: undefined, depositTime: undefined, remark: undefined };
  depositModalOpen.value = true;
}
async function submitDeposit(): Promise<void> {
  if (!depositForm.value.subjectId.trim()) {
    message.warning('请填写投标主体编号');
    return;
  }
  depositSaving.value = true;
  try {
    await saveBidDeposit({
      bidSectionCode: code.value,
      subjectId: depositForm.value.subjectId.trim(),
      amount: depositForm.value.amount,
      currencyCode: depositForm.value.currencyCode,
      payType: depositForm.value.payType,
      status: depositForm.value.status,
      remark: depositForm.value.remark,
    });
    depositModalOpen.value = false;
    message.success('保证金记录已登记');
    await loadSectionBiz();
  } catch {
    /* 失败提示由请求层统一弹出 */
  } finally {
    depositSaving.value = false;
  }
}

/** 合同登记（链末环节：登记即 SIGNED 生效，履约保证金 10% 与签约 30 日由后端校验；归档=ARCHIVED 只读，保存 15 年） */
const contractModalOpen = ref(false);
const contractSaving = ref(false);
const contractForm = ref<{
  contractCode: string;
  contractName: string;
  contractPrice?: number | string;
  performanceBond?: number | string;
  winNoticeTime?: string;
  signTime?: string;
  status: string;
}>({ contractCode: '', contractName: '', status: 'SIGNED' });
function openContractCreate(): void {
  contractForm.value = {
    contractCode: '',
    contractName: '',
    contractPrice: undefined,
    performanceBond: undefined,
    winNoticeTime: undefined,
    signTime: undefined,
    status: 'SIGNED',
  };
  contractModalOpen.value = true;
}
async function submitContract(): Promise<void> {
  if (!contractForm.value.contractCode.trim() || !contractForm.value.contractName.trim()) {
    message.warning('合同编号与合同名称为必填');
    return;
  }
  contractSaving.value = true;
  try {
    await saveBiddingContract({
      tenderProjectCode:
        String(record.value?.tenderProjectCode ?? record.value?.tender_project_code ?? ''),
      bidSectionCode: code.value,
      contractCode: contractForm.value.contractCode.trim(),
      contractName: contractForm.value.contractName.trim(),
      contractPrice: contractForm.value.contractPrice,
      performanceBond: contractForm.value.performanceBond,
      winNoticeTime: contractForm.value.winNoticeTime,
      signTime: contractForm.value.signTime,
      status: contractForm.value.status,
    });
    contractModalOpen.value = false;
    message.success('合同已登记（SIGNED 生效）；归档后 15 年保存期只读锁定');
    await loadSectionBiz();
  } catch {
    /* 失败提示由请求层统一弹出 */
  } finally {
    contractSaving.value = false;
  }
}
function archiveContract(r: Record<string, any>): void {
  Modal.confirm({
    title: '归档合同「' + (r.contractName || r.contractCode || '') + '」？',
    content: '归档后进入只读锁定（SIGNED→ARCHIVED），保存到期=归档+15 年，幂等可重复。',
    okText: '确认归档',
    onOk: async () => {
      try {
        await archiveBiddingContract(r.id);
        message.success('合同已归档');
        await loadSectionBiz();
      } catch {
        /* 失败提示由请求层统一弹出 */
      }
    },
  });
}

/** 招标项目详情「资格预审」抽屉（与树页第一刀对称：仅审查方式=资格预审(前审)时出现） */
const prequalOpen = ref(false);
const prequalTpCode = ref('');
function openPrequalTp(): void {
  // 标段详情打开的是「该标段所在招标项目」的资格预审管理（PrequalPanel 按项目列出，含本标段预审与申请审查）
  prequalTpCode.value =
    type.value === 'BID_SECTION' && prequalRows.value[0]?.tenderProjectCode
      ? String(prequalRows.value[0].tenderProjectCode)
      : code.value;
  prequalOpen.value = true;
}

/** 流程全景（FlowPanorama）流转型：仅项目域三级实体可流转（契约 flow 域 bizType） */
const flowBizType = computed<'BID_SECTION' | 'PROJECT' | 'TENDER_PROJECT' | null>(() => {
  if (type.value === 'PROJECT') return 'PROJECT';
  if (type.value === 'TENDER_PROJECT') return 'TENDER_PROJECT';
  if (type.value === 'BID_SECTION') return 'BID_SECTION';
  return null;
});

const attachments = computed(() => {
  const def = selfDef.value;
  const r = record.value;
  if (!def || !r) return [];
  const col = def.mainMap[def.codeField] ?? '';
  const c = String(r[col] ?? r[def.codeField] ?? '');
  return c ? loadAttachments(def.key, c) : [];
});

/* ============================================================
 * 逐层区块：每页只渲染「直属下一层」，不再平铺其它层级
 * ============================================================ */
/* 逐层区块已移除（2026-10-02）：层级导航/新建下级/草稿收敛到项目台账树（views/bidding/project/index.vue）；
 * 详情页只承担 主档看+编辑 + 公告正文/工作台；树外发起的新建以 create 态进入本页。 */

/* ============================================================
 * 登记/编辑（EntityFormModal：def/save/record/preset 由操作动态切）
 * ============================================================ */
const formOpen = ref(false);
const formDef = ref<EntityDef | null>(null);
const formRecord = ref<null | Record<string, unknown>>(null);
const formPreset = ref<Record<string, string>>({});
/** 标段编号 = 招标项目编号(20位) + 标段序号(3位)：表单只填序列号，保存时拼完整编号（规范 BidSectionVO） */
function composeBidSectionCode(d: Record<string, unknown>): string {
  const tp = String(d.tenderProjectCode ?? route.query.tenderProjectCode ?? '').trim();
  const seq = String(d.bidEctionNum ?? '').trim();
  if (!tp || !seq) return '';
  return `${tp}${seq.padStart(3, '0')}`;
}

function saveFor(k: DomainKey | null): ((data: Record<string, unknown>) => Promise<unknown>) {
  if (k === 'project') return (d) => saveProject(d as unknown as ProjectApi.Project);
  if (k === 'tenderProject') return (d) => saveTenderProject(d as unknown as ProjectApi.TenderProject);
  if (k === 'bidSection') {
    return (d) => {
      const composed = composeBidSectionCode(d);
      return saveBidSection((composed ? { ...d, bidSectionCode: composed } : d) as unknown as ProjectApi.BidSection);
    };
  }
  return () => Promise.reject(new Error('不支持的实体类型'));
}
async function onSaved(): Promise<void> {
  message.success('已保存');
  await doLoad();
}

/* 新建下级/草稿已收敛到项目台账树（2026-10-02）：树「更多 → 新建X」→ create 态进入本页编辑；
 * 本页不再提供「＋ 新建」入口与草稿行（删除草稿在树行操作）。 */

/** 编辑值变化 → 同步草稿 fields（返回再进，字段还在） */
function onDraftEditValues(v: Record<string, string>): void {
  if (!isCreateMode.value) return;
  if (type.value === 'PROJECT') draftStore.updateProjectDraftFields(code.value, v);
  else if (type.value === 'TENDER_PROJECT') draftStore.updateTenderProjectDraftFields(code.value, v);
  else if (type.value === 'BID_SECTION') draftStore.updateBidSectionDraftFields(code.value, v);
}

/** MainCardTable 保存完成：新建态=按 id 回显正式编号→移除草稿→跳正式详情页；编辑态=刷新主档 */
async function onMainCardSaved(id?: unknown): Promise<void> {
  if (!isCreateMode.value) {
    await doLoad();
    return;
  }
  const parentCode = String(route.query.projectCode || route.query.tenderProjectCode || '');
  let formalCode = '';
  try {
    // 主键为雪花 ID（19 位，超 JS Number 安全范围）：原样传字符串避免精度丢失导致回显失败
    const savedId = typeof id === 'number' ? String(id) : (id as string);
    if (type.value === 'PROJECT') formalCode = (await getProject(savedId))?.projectCode ?? '';
    else if (type.value === 'TENDER_PROJECT') formalCode = (await getTenderProject(savedId))?.tenderProjectCode ?? '';
    else if (type.value === 'BID_SECTION') formalCode = (await getBidSection(savedId))?.bidSectionCode ?? '';
  } catch {
    /* 回显失败时退回父级列表 */
  }
  if (type.value === 'PROJECT') draftStore.removeProjectDraft(code.value);
  else if (type.value === 'TENDER_PROJECT') draftStore.removeTenderProjectDraft(parentCode, code.value);
  else if (type.value === 'BID_SECTION') draftStore.removeBidSectionDraft(parentCode, code.value);
  const parentType = type.value === 'TENDER_PROJECT' ? 'PROJECT' : 'TENDER_PROJECT';
  if (formalCode) void router.replace(`/bidding/entity/${type.value}/${encodeURIComponent(formalCode)}`);
  else if (type.value === 'PROJECT') void router.replace('/bidding/project');
  else if (parentCode) void router.replace(`/bidding/entity/${parentType}/${encodeURIComponent(parentCode)}`);
}

/** 公告新建保存完成（NoticeWorkbench @created，标段页「＋ 新建公告」跳转编辑后落库）：
 * 移除内存草稿 → 跳转正式公告详情（NOTICE/{id}，非 create 态展示详情 + 工作台）。 */
function onNoticeCreated(id: number | string): void {
  if (!isCreateMode.value) return;
  const parentCode = String(route.query.bidSectionCode || route.query.tenderProjectCode || '');
  if (parentCode) draftStore.removeNoticeDraft(parentCode, code.value);
  void router.replace(`/bidding/entity/NOTICE/${encodeURIComponent(String(id))}`);
}

/* ===== 外部公告审核预览（NOTICE_EXTERNAL，manage/get） ===== */
const [ExternalDetail, externalApi] = useVbenModal({ connectedComponent: DetailModal, destroyOnClose: true });
function openExternal(): void {
  // 外部公告 id 同为雪花 ID（超 JS Number 安全范围），原样传字符串避免精度丢失
  externalApi.setData({ id: code.value }).open();
}

function goBack(): void {
  router.back();
}

/** 发布后大厅预览（复用外部公告展示器 InternalNoticePreview 弹窗） */
const previewOpen = ref(false);

/** PDF 落款时间：兼容 epoch 毫秒归一为 'YYYY-MM-DD' */
function noticePdfTime(v?: number | string): string {
  if (v === undefined || v === null || v === '') return '';
  const date = /^\d+$/.test(String(v)) ? new Date(Number(v)) : new Date(String(v));
  if (Number.isNaN(date.getTime())) return String(v);
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

const noticePdfGenerating = ref(false);
/** 一键生成版式 PDF：复用 notice/pdfGen.ts 图片型 PDF 生成器 */
async function generateNoticePdf(): Promise<void> {
  const d = notice.value;
  if (!d) return;
  noticePdfGenerating.value = true;
  try {
    const blob = await generateNoticePdfBlob({
      title: d.title || '公告',
      code: d.noticeCode,
      content: d.content || '',
      publisher: d.publisherName || '',
      publishTime: noticePdfTime(d.publishTime),
      sections: buildNoticeSections(d.noticeType, d.payload, {
        projectCode: d.projectCode,
        projectName: d.projectName,
      }),
    });
    const safe = (d.noticeCode || d.title || '公告').replace(/[\\/:*?"<>|]/g, '_').slice(0, 60);
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `公告版式-${safe}.pdf`;
    a.rel = 'noopener';
    document.body.appendChild(a);
    a.click();
    a.remove();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  } finally {
    noticePdfGenerating.value = false;
  }
}
</script>

<template>
  <div class="ct-page">
    <!-- 页面头：面包屑 + ‹ 返回 + 刷新（删除原「关联追溯(类型·编号)」大字块） -->
    <div class="ct-head">
      <nav class="entity-index" aria-label="项目下钻索引">
        <template v-for="(c, i) in breadcrumbView" :key="String(c.code || c.text) + '-' + i">
          <span v-if="i > 0" class="entity-index-arrow" aria-hidden="true">›</span>
          <a-tooltip v-if="c.fold" :title="c.fold.map((h) => h.name || h.text).join(' / ')" placement="top">
            <a-dropdown>
              <a class="entity-index-item text-muted-foreground">…</a>
              <template #overlay>
                <a-menu>
                  <a-menu-item v-for="h in c.fold" :key="String(h.code || h.text)" @click="h.href && router.push(h.href)">
                    {{ h.name || h.text }}
                  </a-menu-item>
                </a-menu>
              </template>
            </a-dropdown>
          </a-tooltip>
          <a-tooltip v-else-if="c.name && c.name !== c.text" :title="c.name" placement="top">
            <a
              v-if="c.href && !c.bold"
              class="entity-index-item text-blue-600"
              :aria-current="c.bold ? 'page' : undefined"
              @click.prevent="router.push(c.href)"
            >{{ c.text }}</a>
            <span v-else class="entity-index-item" :class="{ 'entity-index-current': c.bold }" :aria-current="c.bold ? 'page' : undefined">{{ c.text }}</span>
          </a-tooltip>
          <template v-else>
            <a v-if="c.href && !c.bold" class="entity-index-item text-blue-600" @click.prevent="router.push(c.href)">{{ c.text }}</a>
            <span v-else class="entity-index-item" :class="{ 'entity-index-current': c.bold }" :aria-current="c.bold ? 'page' : undefined">{{ c.text }}</span>
          </template>
        </template>
      </nav>
      <div class="ct-actions">
        <a @click="goBack" class="mr-3 text-sm text-blue-600">‹ 返回</a>
        <a-button @click="doLoad">刷新</a-button>
      </div>
    </div>

    <a-spin :spinning="loading">
      <div v-if="error" class="ct-panel ct-tip" style="color: #c6362e">加载失败：{{ error }}</div>

      <!-- ===== 项目域三级实体（PROJECT / TENDER_PROJECT / BID_SECTION）：主档 + 直属下一层 ===== -->
      <template v-else-if="selfDef">
        <!-- 招标全流程全景条（五阶段可视化 + 当前阶段高亮 + 下一步动作；仅项目域三级实体 + 非新建态） -->
        <FlowPanorama
          v-if="flowBizType && !isCreateMode && record"
          :biz-type="flowBizType"
          :biz-code="code"
          :current-status="String(record.status ?? '')"
        />
        <!-- ===== 三主档统一：结构化分组表格 + 行内编辑（MainCardTable） ===== -->
        <MainCardTable
          v-if="master"
          :title="`${selfDef.title}主档`"
          :type="selfDef.key"
          :def="selfDef"
          :record="record"
          :groups="master.groups"
          :editable-fields="master.editable"
          :readonly-fields="master.readonly"
          :edit-label="type === 'BID_SECTION' ? '编辑标段（包）' : '编辑本项目'"
          :save="saveFor(selfDef.key)"
          :create-mode="isCreateMode"
          :seed-fields="seedFields"
          @update:edit-values="onDraftEditValues"
          @saved="onMainCardSaved"
        />

        <!-- ===== 标段详情「投标与材料」：按标段聚合 保证金 / 合同 / 投标记录 / 开标记录 / 资格预审（真接口，契约 R1-R6） ===== -->
        <div v-if="type === 'BID_SECTION' && !isCreateMode" class="mb-3">
          <div class="ct-panel mb-3">
            <div class="ct-panel-t">投标保证金<span class="ct-panel-meta">（该标段缴纳记录，按标段；平台/招标方登记）</span>
              <a-button size="small" type="primary" class="ml-2" @click="openDepositCreate">登记保证金</a-button>
            </div>
            <a-table
              v-if="depositRows.length"
              class="ct-table"
              :data-source="depositRows"
              :pagination="false"
              size="small"
              :columns="[
                { title: '投标主体', dataIndex: 'subjectId', key: 'subjectId', ellipsis: true },
                { title: '金额（元）', dataIndex: 'amount', key: 'amount', width: 130 },
                { title: '币种', dataIndex: 'currencyCode', key: 'currencyCode', width: 70 },
                { title: '缴纳时间', dataIndex: 'depositTime', key: 'depositTime', width: 170 },
                { title: '状态', dataIndex: 'status', key: 'status', width: 100 },
              ]"
              :row-key="'id'"
            >
              <template #bodyCell="{ column, record }">
                <template v-if="column.key === 'amount'">{{ record.amount ?? '—' }}</template>
                <template v-else-if="column.key === 'depositTime'">{{ record.depositTime ? fmtDateTime(record.depositTime) : '—' }}</template>
                <template v-else-if="column.key === 'status'">{{ record.status || '—' }}</template>
              </template>
            </a-table>
            <div v-else class="ct-empty">该标段暂无保证金记录</div>
          </div>

          <div class="ct-panel mb-3">
            <div class="ct-panel-t">合同签署<span class="ct-panel-meta">（该标段合同，按标段；登记=SIGNED 生效，归档 15 年只读）</span>
              <a-button size="small" type="primary" class="ml-2" @click="openContractCreate">登记合同</a-button>
            </div>
            <a-table
              v-if="contractRows.length"
              class="ct-table"
              :data-source="contractRows"
              :pagination="false"
              size="small"
              :columns="[
                { title: '合同编号', dataIndex: 'contractCode', key: 'contractCode', width: 180 },
                { title: '合同名称', dataIndex: 'contractName', key: 'contractName', ellipsis: true },
                { title: '合同价（元）', dataIndex: 'contractPrice', key: 'contractPrice', width: 130 },
                { title: '签约时间', dataIndex: 'signTime', key: 'signTime', width: 170 },
                { title: '状态', dataIndex: 'status', key: 'status', width: 110 },
                { title: '操作', key: 'ops', width: 90 },
              ]"
              :row-key="'id'"
            >
              <template #bodyCell="{ column, record }">
                <template v-if="column.key === 'contractPrice'">{{ record.contractPrice ?? '—' }}</template>
                <template v-else-if="column.key === 'signTime'">{{ record.signTime ? fmtDateTime(record.signTime) : '—' }}</template>
                <template v-else-if="column.key === 'status'">
                  {{ record.status === 'SIGNED' ? '已签署' : record.status === 'ARCHIVED' ? '已归档' : record.status || '—' }}
                </template>
                <template v-else-if="column.key === 'ops'">
                  <a v-if="record.status === 'SIGNED'" class="text-blue-600" @click="archiveContract(record)">归档</a>
                  <span v-else>—</span>
                </template>
              </template>
            </a-table>
            <div v-else class="ct-empty">该标段暂无合同签署记录，可点右上「登记合同」</div>
          </div>

          <div class="ct-panel mb-3">
            <div class="ct-panel-t">投标记录（按本标段）<span class="ct-panel-meta">（契约 R5：process-track/page 按标段过滤）</span></div>
            <a-table
              v-if="trackRows.length"
              class="ct-table"
              :data-source="trackRows"
              :pagination="false"
              size="small"
              :columns="[
                { title: '投标主体', dataIndex: 'ownerName', key: 'ownerName', ellipsis: true },
                { title: '状态', dataIndex: 'stateName', key: 'stateName', width: 110 },
                { title: '递交时间', dataIndex: 'biddedTime', key: 'biddedTime', width: 170 },
                { title: '标书文件', dataIndex: 'documentName', key: 'documentName', ellipsis: true },
                { title: '材料', dataIndex: 'attachments', key: 'attachments', width: 90 },
              ]"
              :row-key="'id'"
            >
              <template #bodyCell="{ column, record }">
                <template v-if="column.key === 'stateName'">{{ record.stateName || record.state || '—' }}</template>
                <template v-else-if="column.key === 'biddedTime'">{{ record.biddedTime ? fmtDateTime(record.biddedTime) : '—' }}</template>
                <template v-else-if="column.key === 'attachments'">{{ (record.attachments?.length ?? 0) > 0 ? (record.attachments.length + ' 份') : '—' }}</template>
              </template>
            </a-table>
            <div v-else class="ct-empty">该标段暂无投标记录</div>
          </div>

          <div class="ct-panel mb-3">
            <div class="ct-panel-t">开标记录（按本标段）<span class="ct-panel-meta">（契约 R5：/bidding/opening/page 按标段）</span></div>
            <a-table
              v-if="openingRows.length"
              class="ct-table"
              :data-source="openingRows"
              :pagination="false"
              size="small"
              :columns="[
                { title: '开标时间', dataIndex: 'openingTime', key: 'openingTime', width: 170 },
                { title: '开标状态', dataIndex: 'openingStatus', key: 'openingStatus', width: 110 },
                { title: '房间状态', dataIndex: 'roomStatus', key: 'roomStatus', width: 110 },
                { title: '投标人数', dataIndex: 'bidderCount', key: 'bidderCount', width: 90 },
                { title: '主持人', dataIndex: 'operatorName', key: 'operatorName', ellipsis: true },
                { title: '唱标明细', key: 'openList', width: 100 },
              ]"
              :row-key="'id'"
              :expandable="{ rowExpandable: (r: any) => !!r?.openList?.length }"
            >
              <template #bodyCell="{ column, record }">
                <template v-if="column.key === 'openingTime'">{{ record.openingTime ? fmtDateTime(record.openingTime) : '—' }}</template>
                <template v-else-if="column.key === 'openingStatus'">{{ OPENING_STATUS_TEXT[record.openingStatus] || record.openingStatus || '—' }}</template>
                <template v-else-if="column.key === 'roomStatus'">{{ ROOM_STATUS_TEXT[record.roomStatus] || record.roomStatus || '—' }}</template>
                <template v-else-if="column.key === 'bidderCount'">{{ record.bidderCount ?? '—' }}</template>
                <template v-else-if="column.key === 'openList'">
                  <a v-if="record?.openList?.length" class="text-blue-600">展开 ›</a>
                  <span v-else>—</span>
                </template>
              </template>
              <template #expandedRowRender="{ record }">
                <div v-if="record?.openList?.length" class="px-2 py-1">
                  <table class="ct-inner-table">
                    <thead>
                      <tr><th>投标人</th><th>标段</th><th>报价</th><th>解密状态</th></tr>
                    </thead>
                    <tbody>
                      <tr v-for="(o, i) in record.openList" :key="i">
                        <td>{{ o.bidderName || '—' }}</td>
                        <td>{{ o.bidSectionCode || '—' }}</td>
                        <td>{{ o.price ?? '—' }}</td>
                        <td>{{ openDecryptText(o.decryptStatus) }}</td>
                      </tr>
                    </tbody>
                  </table>
                </div>
                <div v-else class="ct-empty">该场开标暂无唱标明细</div>
              </template>
            </a-table>
            <div v-else class="ct-empty">该标段暂无开标记录</div>
          </div>

          <div class="ct-panel mb-3">
            <div class="ct-panel-t">资格预审（按本标段）<span class="ct-panel-meta">（契约 R6：请求标段过滤 + 前端精确兜底；申请审查在管理抽屉）</span>
              <a class="ml-2 text-blue-600" @click="openPrequalTp">打开资格预审管理抽屉 ›</a>
            </div>
            <a-table
              v-if="prequalRows.length"
              class="ct-table"
              :data-source="prequalRows"
              :pagination="false"
              size="small"
              :columns="[
                { title: '资格预审编号', dataIndex: 'prequalCode', key: 'prequalCode', width: 170 },
                { title: '标题', dataIndex: 'title', key: 'title', ellipsis: true },
                { title: '状态', dataIndex: 'status', key: 'status', width: 100 },
                { title: '申请截止', dataIndex: 'applyDeadline', key: 'applyDeadline', width: 170 },
              ]"
              :row-key="'id'"
            >
              <template #bodyCell="{ column, record }">
                <template v-if="column.key === 'status'">{{ record.status === 'PUBLISHED' ? '已发布' : record.status === 'APPLYING' ? '申请中' : record.status === 'DRAFT' ? '草稿' : record.status === 'REVIEWING' ? '审查中' : record.status === 'RESULTED' ? '已出结果' : record.status === 'CLOSED' ? '已关闭' : record.status || '—' }}</template>
                <template v-else-if="column.key === 'applyDeadline'">{{ record.applyDeadline ? fmtDateTime(record.applyDeadline) : '—' }}</template>
              </template>
            </a-table>
            <div v-else class="ct-empty">该标段暂无资格预审</div>
          </div>
        </div>

        <!-- ===== 招标项目详情「资格预审」入口（仅审查方式=资格预审(前审)时出现；与树页第一刀对称） ===== -->
        <div
          v-if="type === 'TENDER_PROJECT' && !isCreateMode && record && String(record.qulificationCheckMode ?? '') === '1'"
          class="mb-3 flex flex-wrap items-center gap-2 px-1"
        >
          <a-button type="primary" @click="openPrequalTp">资格预审</a-button>
          <span class="text-xs text-muted-foreground">该招标项目采用资格预审（前审），在此管理资格预审、看申请并审查</span>
        </div>

        <!-- ===== 标段详情页「关联公告」反向查看：公告可关联多标段，标段侧展示关联它的公告（只读） ===== -->
        <div v-if="type === 'BID_SECTION' && !isCreateMode" class="ct-panel mb-3">
          <div class="ct-panel-t">关联公告<span class="ct-panel-meta">（外部公告按 BID_SECTION_CODES 精确匹配；内部公告按追溯首段归属，多段精确回写待契约 B）</span></div>
          <a-table
            v-if="sectionNotices.internal.length || sectionNotices.external.length"
            class="ct-table"
            :data-source="[
              ...sectionNotices.internal.map((n) => ({ key: `i_${n.id}`, code: n.noticeCode ?? '', title: n.title ?? '（无标题）', typeName: n.noticeTypeName ?? '', time: n.publishTime, kind: 'NOTICE', id: String(n.id ?? '') })),
              ...sectionNotices.external.map((n) => ({ key: `e_${n.id}`, code: n.sourceCode ?? '', title: n.title ?? '（无标题）', typeName: (n.sourceName || n.contentType || '') , time: n.publishTime, kind: 'NOTICE_EXTERNAL', id: String(n.id ?? '') })),
            ]"
            :pagination="false"
            size="small"
            :columns="[
              { title: '公告编号', dataIndex: 'code', key: 'code', width: 200 },
              { title: '标题', dataIndex: 'title', key: 'title' },
              { title: '类型', dataIndex: 'typeName', key: 'type', width: 160 },
              { title: '发布时间', dataIndex: 'time', key: 'time', width: 170 },
              { title: '操作', key: 'op', width: 90 },
            ]"
            :row-key="(r: any) => r.key"
          >
            <template #bodyCell="{ column, record: r }">
              <template v-if="column.key === 'time'">{{ fmtDateTime(r.time) }}</template>
              <template v-else-if="column.key === 'op'">
                <a class="text-xs text-blue-600" @click="goDetail(r.kind, r.id)">查看 ›</a>
              </template>
            </template>
            <template #emptyText><div class="ct-empty">暂无关联公告</div></template>
          </a-table>
          <div v-else class="ct-empty">暂无关联公告</div>
        </div>

        <!-- ===== 标段详情「投标与材料」：按标段聚合 保证金 / 合同签署（用户 2026-10-02：按实体管，业务挂标段下） ===== -->
        <div v-if="type === 'BID_SECTION' && !isCreateMode" class="ct-panel mb-3">
          <div class="ct-panel-t">投标与材料<span class="ct-panel-meta">（按本标段聚合：保证金 / 合同签署；投标记录、开标列表待契约 R5 后端补齐接口后并入）</span></div>

          <div class="mb-2 mt-1 text-sm font-medium">投标保证金</div>
          <a-table
            v-if="depositRows.length"
            class="ct-table"
            :data-source="depositRows"
            :pagination="false"
            size="small"
            :row-key="(r: any) => r.id"
            :columns="[
              { title: '投标主体', dataIndex: 'subjectId', key: 'subjectId', width: 230 },
              { title: '金额', dataIndex: 'amount', key: 'amount', width: 150 },
              { title: '币种', dataIndex: 'currencyCode', key: 'currencyCode', width: 90 },
              { title: '支付方式', dataIndex: 'payType', key: 'payType', width: 120 },
              { title: '缴纳时间', dataIndex: 'depositTime', key: 'depositTime', width: 170 },
              { title: '退还状态', dataIndex: 'refundStatus', key: 'refundStatus', width: 130 },
            ]"
          >
            <template #bodyCell="{ column, record: r }">
              <template v-if="column.key === 'depositTime'">{{ fmtDateTime(r.depositTime) }}</template>
            </template>
            <template #emptyText><div class="ct-empty">暂无保证金记录</div></template>
          </a-table>
          <div v-else class="ct-empty">暂无保证金记录</div>

          <div class="mb-2 mt-3 text-sm font-medium">合同签署</div>
          <a-table
            v-if="contractRows.length"
            class="ct-table"
            :data-source="contractRows"
            :pagination="false"
            size="small"
            :row-key="(r: any) => r.id"
            :columns="[
              { title: '合同编号', dataIndex: 'contractCode', key: 'contractCode', width: 230 },
              { title: '合同名称', dataIndex: 'contractName', key: 'contractName' },
              { title: '合同金额', dataIndex: 'contractPrice', key: 'contractPrice', width: 150 },
              { title: '履约保证金', dataIndex: 'performanceBond', key: 'performanceBond', width: 150 },
              { title: '中标通知时间', dataIndex: 'winNoticeTime', key: 'winNoticeTime', width: 170 },
              { title: '签订时间', dataIndex: 'signTime', key: 'signTime', width: 170 },
            ]"
          >
            <template #bodyCell="{ column, record: r }">
              <template v-if="column.key === 'winNoticeTime'">{{ fmtDateTime(r.winNoticeTime) }}</template>
              <template v-else-if="column.key === 'signTime'">{{ fmtDateTime(r.signTime) }}</template>
            </template>
            <template #emptyText><div class="ct-empty">暂无合同</div></template>
          </a-table>
          <div v-else class="ct-empty">暂无合同</div>

          <div class="mb-2 mt-3 text-sm font-medium">投标记录（按本标段）</div>
          <a-table
            v-if="trackRows.length"
            class="ct-table"
            :data-source="trackRows"
            :pagination="false"
            size="small"
            :row-key="(r: any) => r.id"
            :columns="[
              { title: '投标主体', dataIndex: 'ownerName', key: 'ownerName', width: 180 },
              { title: '状态', dataIndex: 'stateName', key: 'stateName', width: 120 },
              { title: '递交时间', dataIndex: 'biddedTime', key: 'biddedTime', width: 170 },
              { title: '标书文件', dataIndex: 'documentName', key: 'documentName', ellipsis: true },
              { title: '材料', dataIndex: 'attachments', key: 'attachments', width: 90 },
            ]"
          >
            <template #bodyCell="{ column, record: r }">
              <template v-if="column.key === 'biddedTime'">{{ fmtDateTime(r.biddedTime) }}</template>
              <template v-else-if="column.key === 'stateName'">{{ r.stateName || r.state || '—' }}</template>
              <template v-else-if="column.key === 'attachments'">{{ (r.attachments?.length ?? 0) > 0 ? (r.attachments.length + ' 份') : '—' }}</template>
            </template>
            <template #emptyText><div class="ct-empty">暂无投标记录</div></template>
          </a-table>
          <div v-else class="ct-empty">暂无投标记录</div>

          <div class="mb-2 mt-3 text-sm font-medium">开标记录（按本标段）</div>
          <a-table
            v-if="openingRows.length"
            class="ct-table"
            :data-source="openingRows"
            :pagination="false"
            size="small"
            :row-key="(r: any) => r.id"
            :columns="[
              { title: '开标时间', dataIndex: 'openingTime', key: 'openingTime', width: 170 },
              { title: '开标状态', dataIndex: 'openingStatus', key: 'openingStatus', width: 110 },
              { title: '房间状态', dataIndex: 'roomStatus', key: 'roomStatus', width: 110 },
              { title: '投标人数', dataIndex: 'bidderCount', key: 'bidderCount', width: 100 },
              { title: '主持人', dataIndex: 'operatorName', key: 'operatorName' },
            ]"
          >
            <template #bodyCell="{ column, record: r }">
              <template v-if="column.key === 'openingTime'">{{ fmtDateTime(r.openingTime) }}</template>
              <template v-else-if="column.key === 'openingStatus'">{{ OPENING_STATUS_TEXT[r.openingStatus] || r.openingStatus || '—' }}</template>
              <template v-else-if="column.key === 'roomStatus'">{{ ROOM_STATUS_TEXT[r.roomStatus] || r.roomStatus || '—' }}</template>
            </template>
            <template #emptyText><div class="ct-empty">暂无开标记录</div></template>
          </a-table>
          <div v-else class="ct-empty">暂无开标记录</div>

          <div v-if="sectionBizLoading" class="mt-1 text-xs text-muted-foreground">加载中…</div>
        </div>

        <div v-if="displayAttachments.length || attachReady" class="ct-panel mb-3">
          <div class="ct-panel-t">
附件
            <span class="ct-panel-meta" v-if="attachReady">（契约 29：bidding_attachment 一对 N 绑定；上传 → 绑定 → 删除）</span>
            <span class="ct-panel-meta" v-else>（附件服务未就绪，展示本地暂存；请后端执行 DDL：sql/history/2026-09-27-bidding-attachment.sql）</span>
          </div>
          <div v-if="attachReady" class="mb-2 flex flex-wrap items-center gap-2 px-4 pt-2">
            <Select
              v-model:value="attachType"
              :options="ATTACHMENT_TYPE_OPTIONS"
              placeholder="附件类型（35 类字典常用项）"
              allow-clear
              style="width: 220px"
            />
            <input
              v-model="attachAsName"
              placeholder="附件集名称（可选）"
              class="h-8 rounded border border-border px-2 text-xs"
              style="width: 180px"
            />
            <Upload
              :before-upload="(f: File) => { if (!beforeAttachUpload(f)) return Upload.LIST_IGNORE; void handleAttachUpload(f); return Upload.LIST_IGNORE; }"
              :show-upload-list="false"
              :multiple="false"
            >
              <Button :loading="attachUploading" size="small" type="primary">上传并绑定</Button>
            </Upload>
            <span v-if="attachLoading" class="text-xs text-muted-foreground">附件加载中…</span>
          </div>
          <AttachmentList
            :list="displayAttachments"
            :deletable="attachReady"
            :title="`${selfDef.title }附件`"
            @delete="(rec: AttachmentRecord) => { const hit = rec.rowId ? serverAttach.find((x) => x.id === rec.rowId) : undefined; if (hit) handleAttachDelete(hit) }"
          />
        </div>
          <!-- 附件强制 CA 认证工作台（上传后未认证自动弹出；盖章落 CA 台账） -->
          <CaSealAuth
            v-if="authTarget"
            :open="authOpen"
            :file="authTarget"
            :uploaded="authUploaded || undefined"
            :business="code"
            :replace-target="
              attachBizType && code && authTarget.attachmentId
                ? { attachmentId: authTarget.attachmentId, bizCode: code, bizType: attachBizType }
                : undefined
            "
            label="CA 认证"
            @replaced="() => { void loadServerAttachments(); }"
            @update:open="(v: boolean) => { authOpen = v; if (!v) authTarget = null; }"
          />

          <!-- 招标项目「资格预审」抽屉（自动带入当前招标项目，不再手输编号；PrequalPanel 第一刀迁移） -->
          <PrequalPanel v-model:open="prequalOpen" :tender-project-code="prequalTpCode" />

        <!-- ② 直属下一层列表：已移除（2026-10-02）——层级导航/新建下级/草稿收敛到项目台账树。
             详情页只承担 主档看+编辑 与 公告正文/工作台；从树点入本页，返回走「‹ 返回」。 -->
      </template>

      <!-- ===== 内部公告：本公告详情（NoticeHtmlView）+ 发布工作台（全生命周期） ===== -->
      <template v-else-if="type === 'NOTICE'">
        <div v-if="notice" class="ct-panel mb-3">
          <div class="flex flex-wrap items-center justify-between gap-2 px-3 py-2" style="border-bottom: 1px solid #eef1f4">
            <span class="text-sm font-semibold">本公告详情</span>
            <span class="flex items-center gap-2">
              <a class="text-sm text-blue-600" @click="previewOpen = true">发布后预览 ›</a>
              <a class="text-sm text-blue-600" :class="{ 'opacity-50': noticePdfGenerating }" @click="generateNoticePdf">{{ noticePdfGenerating ? '生成中…' : '生成 PDF ›' }}</a>
            </span>
          </div>
          <!-- 公告标题（大字）+ 关键元信息：NoticeHtmlView 头部已被 show-head=false 隐藏，标题等在此补齐，避免公告"只见字段不见标题" -->
          <div class="mb-3 rounded-md border border-border bg-card p-4">
            <div class="text-lg font-semibold leading-snug">{{ notice.title || '（无标题）' }}</div>
            <div class="mt-2 flex flex-wrap gap-x-4 gap-y-1 text-sm text-muted-foreground">
              <span v-if="notice.publisherName">发布人：{{ notice.publisherName }}</span>
              <span v-if="notice.projectName">所属项目：{{ notice.projectName }}</span>
              <span v-if="notice.publicLevel">{{ notice.publicLevel === 'GOVERNMENT' ? '政务公开' : '社会公开' }}<template v-if="notice.internalFlag">（企业内部）</template></span>
              <span v-if="notice.contentVersion">正文 v{{ notice.contentVersion }}</span>
            </div>
          </div>
          <a-descriptions :column="2" size="small" bordered class="mb-3">
            <a-descriptions-item label="公告编号">{{ notice.noticeCode || '—' }}</a-descriptions-item>
            <a-descriptions-item label="类型">{{ notice.noticeTypeName || notice.noticeType || '—' }}</a-descriptions-item>
            <a-descriptions-item label="状态">
              <a-tag :color="noticeStatusColor(notice)">{{ noticeStatusText(notice) }}</a-tag>
            </a-descriptions-item>
            <a-descriptions-item label="发布时间">{{ fmtDateTime(notice.publishTime) }}</a-descriptions-item>
          </a-descriptions>
          <div class="rounded-md border border-border p-3">
            <NoticeHtmlView :detail="notice" :show-head="false" />
          </div>
        </div>
        <!-- 新建态（内存草稿，标段页「＋ 新建公告」跳转编辑）：不显示"未取到详情"，工作台自动打开新建编辑器 -->
        <div v-else-if="!loading && !isCreateMode" class="ct-panel mb-3"><div class="ct-empty">未取到公告详情</div></div>
        <div v-if="isCreateMode" class="mb-3 rounded-md border border-border bg-card px-4 py-3 text-xs text-muted-foreground">
          新建公告（内存草稿）：字段按公告 VO 规范填写，保存后落库并跳转正式公告详情。
        </div>
        <!-- 发布工作台（起草→发布→撤下）只在「新建态」出现：从树「更多 → 新建公告」进来编辑用。
             已发布的公告，看正文/元信息在上方「本公告详情」；全量台账/发布操作在 树公告行「更多 → 公告台账」或菜单「公告公示」。 -->
        <div v-if="isCreateMode" class="ct-panel">
          <div class="ct-panel-t">公告/公示 发布工作台（软件：起草 → 发布 → 撤下 全生命周期）</div>
          <NoticeWorkbench
            mode="admin"
            :auto-create="isCreateMode"
            :preset-chain="{
              projectCode: String(route.query.projectCode ?? ''),
              projectName: String(route.query.projectName ?? ''),
              tenderProjectCode: String(route.query.tenderProjectCode ?? ''),
              bidSectionCode: String(route.query.bidSectionCode ?? ''),
              bidSectionName: String(route.query.bidSectionName ?? ''),
            }"
            @created="onNoticeCreated"
          />
        </div>
      </template>

      <!-- ===== 外部公告：审核预览（保留现状） ===== -->
      <template v-else-if="type === 'NOTICE_EXTERNAL'">
        <div class="ct-panel">
          <div class="ct-panel-t">外部公告 · 管理预览</div>
          <div class="ct-tip">外部公告详情走 noticeExternal 管理预览（净化正文 / PDF / 附件病毒状态 / 前台观感），依赖审核权限 bidding:notice-external:update。</div>
          <a-button type="primary" @click="openExternal">打开审核预览</a-button>
        </div>
      </template>

      <div v-else-if="!loading && !error" class="ct-empty">未知实体类型：{{ type }}</div>
    </a-spin>

    <!-- 登记 / 编辑弹窗（def/save/record/preset 动态切换：主档编辑 + 下级新建共用） -->
    <EntityFormModal
      v-model:open="formOpen"
      :def="formDef ?? ENTITY_DEFS.project"
      :record="formRecord"
      :preset="formPreset"
      :save="saveFor(formDef ? (formDef.key) : null)"
      @saved="onSaved"
    />

    <!-- 登记保证金（按标段：投标主体编号必填；币种默认 CNY，其余字段自由输入、字典由后端校验） -->
    <Modal v-model:open="depositModalOpen" title="登记保证金" :footer="null" :width="520" wrap-class-name="white-modal">
      <div class="modal-body">
        <div class="modal-hint">登记「{{ record?.tenderProjectName || '该标段' }}」下某投标主体的保证金缴纳记录（金额/币种/方式/状态等由后端权威校验）。</div>
        <div class="flex flex-col gap-3">
          <div>
            <div class="mb-1 text-sm text-muted-foreground">投标主体编号 *</div>
            <input v-model="depositForm.subjectId" class="ct-input" placeholder="企业主体编号（subjectId）" />
          </div>
          <div>
            <div class="mb-1 text-sm text-muted-foreground">金额（元）</div>
            <input v-model="depositForm.amount" class="ct-input" placeholder="数字" />
          </div>
          <div>
            <div class="mb-1 text-sm text-muted-foreground">币种</div>
            <input v-model="depositForm.currencyCode" class="ct-input" placeholder="默认 CNY" />
          </div>
          <div>
            <div class="mb-1 text-sm text-muted-foreground">缴纳方式</div>
            <input v-model="depositForm.payType" class="ct-input" placeholder="如 BANK / CASH（字典由后端校验）" />
          </div>
          <div>
            <div class="mb-1 text-sm text-muted-foreground">状态</div>
            <input v-model="depositForm.status" class="ct-input" placeholder="如 PAID（字典由后端校验）" />
          </div>
          <div>
            <div class="mb-1 text-sm text-muted-foreground">缴纳时间</div>
            <input v-model="depositForm.depositTime" class="ct-input" placeholder="YYYY-MM-DD HH:mm:ss" />
          </div>
          <div>
            <div class="mb-1 text-sm text-muted-foreground">备注</div>
            <input v-model="depositForm.remark" class="ct-input" placeholder="可选" />
          </div>
        </div>
        <div class="modal-footer">
          <Button @click="depositModalOpen = false">取消</Button>
          <Button type="primary" :loading="depositSaving" @click="submitDeposit">登记</Button>
        </div>
      </div>
    </Modal>

    <!-- 登记合同（链末环节：合同编号/名称必填，价/保证金/签约时间可选；履约保证金 10% 与签约 30 日由后端校验） -->
    <Modal v-model:open="contractModalOpen" title="登记合同" :footer="null" :width="560" wrap-class-name="white-modal">
      <div class="modal-body">
        <div class="modal-hint">
          登记「{{ record?.tenderProjectName || '该标段' }}」的合同——登记即 SIGNED 生效；履约保证金须 ≥ 合同价 10%，签约须在中标 30 日内（后端校验）。
        </div>
        <div class="flex flex-col gap-3">
          <div>
            <div class="mb-1 text-sm text-muted-foreground">合同编号 *</div>
            <input v-model="contractForm.contractCode" class="ct-input" placeholder="如 HT2026-001" />
          </div>
          <div>
            <div class="mb-1 text-sm text-muted-foreground">合同名称 *</div>
            <input v-model="contractForm.contractName" class="ct-input" placeholder="如 XX 项目施工合同" />
          </div>
          <div>
            <div class="mb-1 text-sm text-muted-foreground">合同价（元）</div>
            <input v-model="contractForm.contractPrice" class="ct-input" placeholder="数字" />
          </div>
          <div>
            <div class="mb-1 text-sm text-muted-foreground">履约保证金（元）</div>
            <input v-model="contractForm.performanceBond" class="ct-input" placeholder="≥ 合同价 10%" />
          </div>
          <div>
            <div class="mb-1 text-sm text-muted-foreground">中标通知时间</div>
            <input v-model="contractForm.winNoticeTime" class="ct-input" placeholder="YYYY-MM-DD HH:mm:ss" />
          </div>
          <div>
            <div class="mb-1 text-sm text-muted-foreground">签约时间</div>
            <input v-model="contractForm.signTime" class="ct-input" placeholder="YYYY-MM-DD HH:mm:ss" />
          </div>
        </div>
        <div class="modal-footer">
          <Button @click="contractModalOpen = false">取消</Button>
          <Button type="primary" :loading="contractSaving" @click="submitContract">登记</Button>
        </div>
      </div>
    </Modal>

    <ExternalDetail />

    <!-- 内部公告：发布后大厅预览（复用外部公告展示器） -->
    <InternalNoticePreview v-model:open="previewOpen" :detail="notice" />
  </div>
</template>

<style scoped>
.entity-index {
  display: inline-flex;
  align-items: center;
  min-width: 0;
  max-width: 100%;
  overflow-x: auto;
  white-space: nowrap;
  scrollbar-width: thin;
}
.entity-index-item {
  display: inline-flex;
  align-items: center;
  flex: 0 0 auto;
  line-height: 22px;
}
.entity-index-current {
  font-weight: 600;
  color: hsl(var(--foreground));
}
.entity-index-arrow {
  flex: 0 0 auto;
  margin: 0 7px;
  color: hsl(var(--muted-foreground));
  font-size: 18px;
  line-height: 20px;
}
/* 草稿行：正式记录保持原样；草稿浅灰背景/文字，不使用斜体 */
:deep(.draft-row) {
  background: #fafafa;
  color: #999;
}
:deep(.draft-row td) {
  color: #999;
}
/* 草稿标签：橙色边框 + 橙色文字，小字号 */
:deep(.draft-tag.ant-tag) {
  margin-left: 6px;
  padding: 0 4px;
  font-size: 11px;
  line-height: 17px;
  color: #d97706;
  background: #fff7ed;
  border-color: #f59e0b;
}
/* 登记表单输入 */
.ct-input {
  width: 100%;
  padding: 6px 10px;
  border: 1px solid #d9d9d9;
  border-radius: 4px;
  font-size: 13px;
  outline: none;
  background: #fff;
  color: hsl(var(--foreground));
}
/* 唱标明细展开表 */
.ct-inner-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
}
.ct-inner-table th,
.ct-inner-table td {
  border: 1px solid #eee;
  padding: 4px 8px;
  text-align: left;
}
.ct-inner-table th {
  background: #fafafa;
  font-weight: 600;
}
</style>

