<script setup lang="ts">
/* 前台 · 项目公告管理 — 第四级：某标段下的公告/公示列表（创建公告入口在这里）
 * 入口：第三级点击标段行。本页提供「创建公告」——先选公告类型（4 选 1），再按类型动态加载对应表前端文件。
 * 数据：GET /bidding/bid-section/trace?bidSectionCode=… 一次取回 标段→招标项目→项目 + 公告列表（内部公告）；
 *      公告详情/版式文件：GET /bidding/notice/get（含 pdfFileId）→ 内嵌 PDF 预览。
 * 发布前置状态限制按业务要求暂不处理（此处只做创建与查看）。 */
import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { Button, message, Table, Tag } from 'ant-design-vue';

/* NoticeApi 同时含类型与常量（NOTICE_TYPES），按值导入 */
import { fetchNoticeDetail, NoticeApi } from '#/api/bidding/notice';
import { type ProjectApi, traceBidSection } from '#/api/bidding/project';

import PdfPreview from '../../../bidding/vo/PdfPreview.vue';
import { usePortalCan } from '../access';
import NoticeCreateModal from './NoticeCreateModal.vue';

import './panel.css';

const route = useRoute();
const router = useRouter();
/* 判权口径见 ../access.ts（未下发权限码时放行，与后台一致） */
const { can } = usePortalCan();
const bidSectionCode = computed(() => String(route.params.bidSectionCode ?? ''));

const loading = ref(false);
const trace = ref<null | ProjectApi.Trace>(null);
const rows = computed<ProjectApi.TraceNotice[]>(() => trace.value?.notices ?? []);
const sectionName = computed(() => {
  const s = trace.value?.sections?.find((x) => x.bidSectionCode === bidSectionCode.value);
  return s?.bidSectionName ?? '';
});
const tenderProjectCode = computed(() => {
  const s = trace.value?.sections?.find((x) => x.bidSectionCode === bidSectionCode.value);
  return s?.tenderProjectCode ?? trace.value?.tenderProjects?.[0]?.tenderProjectCode ?? '';
});
const tenderProjectName = computed(() => {
  const tp = trace.value?.tenderProjects?.find((t) => t.tenderProjectCode === tenderProjectCode.value) ?? trace.value?.tenderProjects?.[0];
  return tp?.tenderProjectName ?? '';
});
const projectCode = computed(() => trace.value?.project?.projectCode ?? trace.value?.queryCode ?? '');
const projectName = computed(() => trace.value?.project?.projectName ?? '');

const createOpen = ref(false);

const pdfOpen = ref(false);
const pdfFileId = ref<number | string | undefined>(undefined);
const pdfName = ref('');

async function load(): Promise<void> {
  loading.value = true;
  try {
    trace.value = await traceBidSection(bidSectionCode.value);
  } catch (e) {
    message.error('加载标段公告失败：' + ((e as Error).message || String(e)));
  } finally {
    loading.value = false;
  }
}
onMounted(load);

function typeLabel(r: ProjectApi.TraceNotice): string {
  return r.noticeTypeName || NoticeApi.NOTICE_TYPES.find((t) => t.code === r.noticeType)?.label || (r.noticeType ?? '—');
}
function statusLabel(s?: string): { color: string; text: string } {
  const m: Record<string, { color: string; text: string }> = {
    DRAFT: { color: 'default', text: '草稿' },
    PUBLISHED: { color: 'green', text: '已发布' },
    UNPUBLISHED: { color: 'orange', text: '已撤下' },
    ARCHIVED: { color: 'default', text: '已归档' },
  };
  return m[s ?? ''] ?? { color: 'default', text: s ?? '—' };
}
function fmtTime(t?: number): string {
  if (!t) return '—';
  return new Date(t).toLocaleString('zh-CN', { hour12: false });
}
/** 查看公告版式文件（PDF）：后端详情仅回 pdfFileId，预览组件负责取 URL（需 infra:file:query） */
async function previewPdf(r: ProjectApi.TraceNotice): Promise<void> {
  if (r.id === undefined || r.id === null) return;
  try {
    const d = await fetchNoticeDetail(r.id);
    if (!d?.pdfFileId) {
      message.warning('该公告尚未上传 PDF 版式文件（发布前必填）');
      return;
    }
    pdfFileId.value = d.pdfFileId;
    pdfName.value = (d.title ?? r.title ?? '公告') + '.pdf';
    pdfOpen.value = true;
  } catch (e) {
    message.error('获取公告详情失败：' + ((e as Error).message || String(e)));
  }
}
function backToSections(): void {
  void router.push(
    '/portal/bidding/projects/' + encodeURIComponent(projectCode.value) +
      '/tender-projects/' + encodeURIComponent(tenderProjectCode.value) + '/sections',
  );
}
</script>

<template>
  <div class="pn">
    <div class="pn-crumb">
      <a @click="router.push('/portal/bidding/projects')">项目</a>
      <span>/</span>
      <a @click="router.push(`/portal/bidding/projects/${ encodeURIComponent(projectCode) }/tender-projects`)">{{ projectName || projectCode || '…' }}</a>
      <span>/</span>
      <a @click="backToSections">{{ tenderProjectName || tenderProjectCode || '…' }}</a>
      <span>/</span>
      <span class="pn-cur">{{ sectionName || bidSectionCode }}</span>
      <span>/</span>
      <span>公告</span>
    </div>

    <div class="pn-head">
      <div>
        <h1 class="pn-title">公告 / 公示</h1>
        <p class="pn-sub">
          当前标段：<b>{{ sectionName || bidSectionCode }}</b>。
          公告对"标段"发布，四类数据集字段不同：招标公告 / 变更公告 / 中标候选人公示 / 中标结果公示。
          点「创建公告」先选类型，再按类型加载对应的专属字段表；公告可挂多个附件（按类型对应采集项）。
        </p>
      </div>
      <div class="pn-acts">
        <Button v-if="can('bidding:notice:create')" type="primary" @click="createOpen = true">创建公告</Button>
        <span v-else class="pn-dim">无创建权限（bidding:notice:create）：可查看该标段已发布公告，创建请联系管理员开通</span>
        <Button @click="load">刷新</Button>
      </div>
    </div>

    <div class="pn-card">
      <div class="pn-card-h">
        <span class="pn-card-t">本标段公告</span>
        <span class="pn-dim">共 {{ rows.length }} 条 · 标段 {{ sectionName || bidSectionCode }}</span>
        <span class="pn-dim">发布前置状态限制本期暂不校验（仅创建草稿与查看）</span>
      </div>
      <div class="pn-card-b">
        <Table class="pn-table" row-key="id" size="small" :loading="loading" :data-source="rows" :pagination="false">
          <!-- 用户口径：公告 ID/编号界面不显示（契约《编号体系归位》） -->
          <Table.Column title="类型" :width="140">
            <template #default="{ record }">
              <Tag color="blue">{{ typeLabel(record) }}</Tag>
            </template>
          </Table.Column>
          <Table.Column title="标题" data-index="title" :ellipsis="true" />
          <Table.Column title="状态" :width="100">
            <template #default="{ record }">
              <Tag :color="statusLabel(record.status).color">{{ statusLabel(record.status).text }}</Tag>
            </template>
          </Table.Column>
          <Table.Column title="发布时间" :width="170">
            <template #default="{ record }">{{ fmtTime(record.publishTime) }}</template>
          </Table.Column>
          <Table.Column title="操作" :width="150" fixed="right">
            <template #default="{ record }">
              <Button size="small" type="link" @click="previewPdf(record)">版式文件</Button>
            </template>
          </Table.Column>
        </Table>
        <div v-if="!loading && !rows.length" class="pn-empty">
          该标段暂无公告。点右上「创建公告」→ 选择公告类型（招标公告 / 变更公告 / 中标候选人公示 / 中标结果公示）→ 填报并保存。
        </div>
      </div>
    </div>

    <NoticeCreateModal
      v-model:open="createOpen"
      :project-code="projectCode"
      :tender-project-code="tenderProjectCode"
      :bid-section-code="bidSectionCode"
      :bid-section-name="sectionName"
      @created="load"
    />

    <PdfPreview v-model:open="pdfOpen" :file-id="pdfFileId" :file-name="pdfName" title="公告版式文件预览" />
  </div>
</template>
