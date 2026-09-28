<script setup lang="ts">
/* 通用实体详情页（静态路由 /bidding/entity/:type/:code，不挂菜单，与 /portal 同机制）
 * 定稿口径（2026-09-14）：抽屉层叠 + 通用实体详细页两种下钻并存；详情页「承载之前工作台的能力，不限项目、标段等」。
 *   实体注册表（ENTITY_REGISTRY）驱动，一套页面覆盖 5 类：
 *     · PROJECT / TENDER_PROJECT / BID_SECTION —— 详情（trace 关联区）+ 登记/编辑（复用 EntityFormModal + ENTITY_DEFS + 附件）
 *       并支持"新建下级实体"（项目下建招标项目、招标项目下建标段），延续前台业务链生产能力；
 *     · NOTICE —— 内嵌 NoticeWorkbench mode="admin"（即原"公告发布工作台"完整能力）+ 本公告详情（NoticeHtmlView）；
 *     · NOTICE_EXTERNAL —— 复用 noticeExternal DetailModal（审核预览：净化正文/PDF/附件病毒状态/前台观感）。
 * 数据完全走现有 trace 链路及 各域 CRUD/审核 接口 → **后端零改动**。
 * 入口：项目台账抽屉/列表行「详情 ↗」；权限沿用各域既有权限点（bidding:project:* / bid-section:* / notice:* 等）。 */
import { computed, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { useVbenModal } from '@vben/common-ui';

import { message } from 'ant-design-vue';

import { fetchNoticeDetail, type NoticeApi } from '#/api/bidding/notice';
import {
  getBidSectionByCode,
  getProjectByCode,
  getTenderProjectByCode,
  saveBidSection,
  saveProject,
  saveTenderProject,
  traceBidSection,
  traceProject,
  traceTenderProject,
  type ProjectApi,
} from '#/api/bidding/project';

import { fmtDateTime } from '../certificate/format';
import NoticeHtmlView from '../notice/NoticeHtmlView.vue';
import NoticeWorkbench from '../notice/NoticeWorkbench.vue';
import DetailModal from '../noticeExternal/modules/detail.vue';
import AttachmentList from '../vo/AttachmentList.vue';
import EntityFormModal from '../vo/EntityFormModal.vue';
import { ENTITY_DEFS, type EntityDef } from '../vo/entityForms';
import { loadAttachments } from '../vo/attachmentStore';

type EntityType = 'BID_SECTION' | 'NOTICE' | 'NOTICE_EXTERNAL' | 'PROJECT' | 'TENDER_PROJECT';
type DomainKey = 'bidSection' | 'project' | 'tenderProject';

const route = useRoute();
const router = useRouter();
const type = computed<EntityType>(() => (String(route.params.type ?? '') as EntityType).toUpperCase() as EntityType);
const code = computed(() => String(route.params.code ?? ''));

const loading = ref(false);
const error = ref('');
const record = ref<null | Record<string, unknown>>(null);
const trace = ref<null | ProjectApi.Trace>(null);
const notice = ref<null | NoticeApi.Detail>(null);

const TITLE: Record<EntityType, string> = {
  PROJECT: '项目',
  TENDER_PROJECT: '招标项目',
  BID_SECTION: '标段（包）',
  NOTICE: '公告',
  NOTICE_EXTERNAL: '外部公告',
};
const pageTitle = computed(() => `${TITLE[type.value] ?? '实体'}详情 · ${code.value}`);

async function doLoad(): Promise<void> {
  if (!code.value) return;
  loading.value = true;
  error.value = '';
  record.value = null;
  trace.value = null;
  notice.value = null;
  try {
    if (type.value === 'PROJECT') {
      record.value = (await getProjectByCode(code.value)) as unknown as Record<string, unknown> | null;
      trace.value = await traceProject(code.value);
    } else if (type.value === 'TENDER_PROJECT') {
      record.value = (await getTenderProjectByCode(code.value)) as unknown as Record<string, unknown> | null;
      trace.value = await traceTenderProject(code.value);
    } else if (type.value === 'BID_SECTION') {
      record.value = (await getBidSectionByCode(code.value)) as unknown as Record<string, unknown> | null;
      trace.value = await traceBidSection(code.value);
    } else if (type.value === 'NOTICE') {
      notice.value = await fetchNoticeDetail(Number(code.value) || code.value);
    }
    // NOTICE_EXTERNAL：详情在审核预览弹窗内按 id 取（manage/get），此处不预取
  } catch (e) {
    error.value = (e instanceof Error ? e.message : String(e));
  } finally {
    loading.value = false;
  }
}
onMounted(doLoad);
watch(() => route.params.code, doLoad);

/* ===== 实体主档 → 详情展示（ProjectVO / TenderProjectVO / BidSectionVO） ===== */
const selfDef = computed<EntityDef | null>(() => {
  if (type.value === 'PROJECT') return ENTITY_DEFS.project;
  if (type.value === 'TENDER_PROJECT') return ENTITY_DEFS.tenderProject;
  if (type.value === 'BID_SECTION') return ENTITY_DEFS.bidSection;
  return null;
});
/** 主档可展示字段（主列 camelCase → 值；剔除 payload/fields 等非展示键、空值） */
const fieldEntries = computed(() => {
  const r = (record.value ?? {}) as Record<string, unknown>;
  return Object.entries(r).filter(
    ([k, v]) =>
      !['fields', 'payload', 'id'].includes(k) &&
      v !== null &&
      v !== undefined &&
      String(v) !== '',
  );
});
const attachments = computed(() => {
  const def = selfDef.value;
  const r = record.value;
  if (!def || !r) return [];
  const col = def.mainMap[def.codeField] ?? '';
  const c = String(r[col] ?? r[def.codeField] ?? '');
  return c ? loadAttachments(def.key, c) : [];
});

/* ===== 登记/编辑（EntityFormModal：def/save/record/preset 由操作动态切） ===== */
const formOpen = ref(false);
const formDef = ref<EntityDef | null>(null);
const formRecord = ref<null | Record<string, unknown>>(null);
const formPreset = ref<Record<string, string>>({});
function selfDefKey(): DomainKey | null {
  if (type.value === 'PROJECT') return 'project';
  if (type.value === 'TENDER_PROJECT') return 'tenderProject';
  if (type.value === 'BID_SECTION') return 'bidSection';
  return null;
}
function saveFor(k: DomainKey | null): ((data: Record<string, unknown>) => Promise<unknown>) {
  if (k === 'project') return (d) => saveProject(d as unknown as ProjectApi.Project);
  if (k === 'tenderProject') return (d) => saveTenderProject(d as unknown as ProjectApi.TenderProject);
  if (k === 'bidSection') return (d) => saveBidSection(d as unknown as ProjectApi.BidSection);
  return () => Promise.reject(new Error('不支持的实体类型'));
}
function openEdit(): void {
  const k = selfDefKey();
  if (!k) return;
  formDef.value = ENTITY_DEFS[k];
  formRecord.value = record.value;
  formPreset.value = {};
  formOpen.value = true;
}
/** 新建下级实体：项目下建招标项目 / 招标项目下建标段（延续前台业务链生产能力；标段菜单 6772 已退场） */
function openChildCreate(k: DomainKey, presetKey: string): void {
  formDef.value = ENTITY_DEFS[k];
  formRecord.value = null;
  formPreset.value = { [presetKey]: code.value };
  formOpen.value = true;
}
async function onSaved(): Promise<void> {
  message.success('已保存');
  await doLoad();
}

/* ===== 外部公告审核预览（NOTICE_EXTERNAL，manage/get） ===== */
const [ExternalDetail, externalApi] = useVbenModal({ connectedComponent: DetailModal, destroyOnClose: true });
function openExternal(): void {
  externalApi.setData({ id: Number(code.value) || code.value }).open();
}

function goBack(): void {
  router.back();
}
</script>

<template>
  <div class="ct-page">
    <div class="ct-head">
      <div>
        <div class="ct-title">{{ pageTitle }}</div>
        <div class="ct-sub">通用实体详情页（/bidding/entity/:type/:code，不挂菜单）：详情 + 登记/编辑 + 关联追溯；数据全走现有接口，后端零改动</div>
      </div>
      <div class="ct-actions">
        <a @click="goBack" class="mr-3 text-sm text-blue-600">‹ 返回</a>
        <a-button @click="doLoad">刷新</a-button>
      </div>
    </div>

    <a-spin :spinning="loading">
      <div v-if="error" class="ct-panel ct-tip" style="color: #c6362e">加载失败：{{ error }}</div>

      <!-- ===== 项目域三级实体（PROJECT / TENDER_PROJECT / BID_SECTION） ===== -->
      <template v-else-if="selfDef">
        <div class="ct-panel mb-3">
          <div class="flex flex-wrap items-center justify-between gap-2 px-4 py-2" style="border-bottom: 1px solid #eef1f4">
            <span class="text-sm font-semibold">{{ selfDef.title }}主档</span>
            <a-space size="small">
              <a v-if="type === 'PROJECT'" @click="openChildCreate('tenderProject', 'PROJECT_CODE')">＋ 新建招标项目</a>
              <a v-if="type === 'TENDER_PROJECT'" @click="openChildCreate('bidSection', 'TENDER_PROJECT_CODE')">＋ 新建标段（包）</a>
              <a-button size="small" @click="openEdit">编辑{{ type === 'BID_SECTION' ? '标段' : '本' + selfDef.title }}</a-button>
            </a-space>
          </div>
          <a-descriptions v-if="fieldEntries.length" :column="2" size="small" bordered>
            <a-descriptions-item v-for="[k, v] in fieldEntries" :key="k" :label="k">
              {{ typeof v === 'object' ? JSON.stringify(v) : v }}
            </a-descriptions-item>
            <a-descriptions-item v-if="record?.createTime" label="登记时间">{{ fmtDateTime(record.createTime as number) }}</a-descriptions-item>
          </a-descriptions>
          <div v-else-if="record" class="ct-empty">未取到主档字段</div>
          <div v-else class="ct-empty">未找到该{{ selfDef.title }}</div>
        </div>

        <div v-if="attachments.length" class="ct-panel mb-3">
          <div class="ct-panel-t">附件 <span class="ct-panel-meta">（GUID 按规范写入 ATTACHMENT_CODE；可在编辑弹窗内增删）</span></div>
          <AttachmentList :list="attachments" :title="`${selfDef.title }附件`" />
        </div>

        <!-- 关联追溯（trace：项目 → 招标项目 → 标段 + 公告/外部公告/备案/关注/主体/统计） -->
        <div v-if="trace" class="ct-panel mb-3">
          <div class="ct-panel-t">关联追溯（{{ trace.queryType }} · {{ trace.queryCode }}）</div>
          <a-descriptions v-if="trace.stats" :column="4" size="small" bordered class="mb-3">
            <a-descriptions-item label="关联招标项目">{{ trace.stats.tenderProjectCount ?? 0 }}</a-descriptions-item>
            <a-descriptions-item label="关联标段">{{ trace.stats.sectionCount ?? 0 }}</a-descriptions-item>
            <a-descriptions-item label="内部公告">{{ trace.stats.noticeCount ?? 0 }}</a-descriptions-item>
            <a-descriptions-item label="外部公告">{{ trace.stats.externalNoticeCount ?? 0 }}</a-descriptions-item>
          </a-descriptions>
          <div class="ct-panel-t mb-2">关联招标项目（{{ trace.tenderProjects?.length ?? 0 }}）</div>
          <a-table
            class="ct-table mb-3" :data-source="trace.tenderProjects ?? []" :pagination="false" row-key="id" size="small"
            :columns="[{ title: '编号', dataIndex: 'tenderProjectCode', key: 'code', width: 200 }, { title: '名称', dataIndex: 'tenderProjectName', key: 'name' }, { title: '编号->详情', key: 'op', width: 90 }]"
          >
            <template #bodyCell="{ column, record: r }">
              <template v-if="column.key === 'op'">
                <a class="text-xs text-blue-600" @click="router.push(`/bidding/entity/TENDER_PROJECT/${encodeURIComponent(r.tenderProjectCode)}`)">详情 ↗</a>
              </template>
            </template>
          </a-table>
          <div class="ct-panel-t mb-2">关联标段（{{ trace.sections?.length ?? 0 }}）</div>
          <a-table
            class="ct-table mb-3" :data-source="trace.sections ?? []" :pagination="false" row-key="id" size="small"
            :columns="[{ title: '标段编号', dataIndex: 'bidSectionCode', key: 'code', width: 200 }, { title: '名称', dataIndex: 'bidSectionName', key: 'name' }, { title: '操作', key: 'op', width: 90 }]"
          >
            <template #bodyCell="{ column, record: r }">
              <template v-if="column.key === 'op'">
                <a class="text-xs text-blue-600" @click="router.push(`/bidding/entity/BID_SECTION/${encodeURIComponent(r.bidSectionCode)}`)">详情 ↗</a>
              </template>
            </template>
          </a-table>
          <div class="ct-panel-t mb-2">关联公告（{{ trace.notices?.length ?? 0 }}）</div>
          <a-table
            class="ct-table" :data-source="trace.notices ?? []" :pagination="false" row-key="id" size="small"
            :columns="[{ title: '公告编号', dataIndex: 'noticeCode', key: 'code', width: 200 }, { title: '标题', dataIndex: 'title', key: 'title' }, { title: '类型', dataIndex: 'noticeTypeName', key: 'type', width: 140 }, { title: '发布时间', dataIndex: 'publishTime', key: 'publishTime', width: 170 }]"
          >
            <template #bodyCell="{ column, record: r }">
              <template v-if="column.key === 'publishTime'">{{ fmtDateTime(r.publishTime) }}</template>
            </template>
          </a-table>
        </div>
      </template>

      <!-- ===== 内部公告：原工作台能力（NoticeWorkbench 全生命周期）+ 本公告详情 ===== -->
      <template v-else-if="type === 'NOTICE'">
        <div v-if="notice" class="ct-panel mb-3">
          <div class="ct-panel-t">本公告详情（NoticeHtmlView：版式 PDF / 附件 / 招标文件获取指引）</div>
          <a-descriptions :column="2" size="small" bordered class="mb-3">
            <a-descriptions-item label="公告编号">{{ notice.noticeCode || '—' }}</a-descriptions-item>
            <a-descriptions-item label="类型">{{ notice.noticeTypeName || notice.noticeType || '—' }}</a-descriptions-item>
            <a-descriptions-item label="状态">{{ notice.status || '—' }}</a-descriptions-item>
            <a-descriptions-item label="发布时间">{{ fmtDateTime(notice.publishTime) }}</a-descriptions-item>
          </a-descriptions>
          <div class="rounded-md border border-border p-3">
            <NoticeHtmlView :detail="notice" :show-head="false" />
          </div>
        </div>
        <div class="ct-panel">
          <div class="ct-panel-t">公告/公示 发布工作台（软件：起草 → 发布 → 撤下 全生命周期）</div>
          <NoticeWorkbench mode="admin" />
        </div>
      </template>

      <!-- ===== 外部公告：审核预览 ===== -->
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

    <ExternalDetail />
  </div>
</template>