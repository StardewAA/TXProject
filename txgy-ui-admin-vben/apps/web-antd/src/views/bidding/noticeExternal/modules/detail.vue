<!-- 外部公告管理详情预览（BR-7 manage/get：全状态可读，审核/运营“读完正文再操作”用） -->
<script lang="ts" setup>
import type { NoticeExternalApi } from '#/api/bidding/noticeExternal';

import { computed, ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { Tag } from 'ant-design-vue';

import { getManageDetail } from '#/api/bidding/noticeExternal';

import {
  AUDIT_MAP,
  BODY_MAP,
  CONTENT_TYPE_MAP,
  INDUSTRY_MAP,
  STATUS_MAP,
} from '../data';

const detail = ref<NoticeExternalApi.ManageDetail | null>(null);
const loading = ref(false);
const errorMsg = ref('');

const tag = (map: Record<string, { text: string; color: string }>, v?: string) => {
  const c = v ? map[v] : undefined;
  return c ? { text: c.text, color: c.color } : null;
};

const hasBody = computed(() => !!detail.value?.contentHtmlSafe);
const fmtT = (v?: string) => (v ? v.replace('T', ' ').slice(0, 19) : '—');
const empty = (v?: string) => (v ? v : '—');

const [Modal, modalApi] = useVbenModal({
  title: '外部公告 · 管理预览',
  showConfirmButton: false,
  onOpenChange: async (isOpen: boolean) => {
    if (!isOpen) {
      detail.value = null;
      errorMsg.value = '';
      return;
    }
    const row = modalApi.getData<NoticeExternalApi.ManageItem>();
    if (!row?.id) {
      return;
    }
    loading.value = true;
    errorMsg.value = '';
    try {
      detail.value = await getManageDetail(row.id);
    } catch (e) {
      errorMsg.value = e instanceof Error ? e.message : String(e);
    } finally {
      loading.value = false;
    }
  },
});
</script>

<template>
  <Modal class="w-[980px]">
    <div class="max-h-[70vh] overflow-auto px-2 py-1">
      <div v-if="loading" class="py-16 text-center text-gray-400">加载中…</div>
      <div v-else-if="errorMsg" class="py-10 text-center text-red-500">{{ errorMsg }}</div>
      <div v-else-if="detail">
        <!-- 状态区 -->
        <div class="mb-3 flex flex-wrap items-center gap-2">
          <span class="text-base font-bold text-gray-800">{{ detail.title || '（无标题）' }}</span>
        </div>
        <div class="mb-3 flex flex-wrap items-center gap-1.5 text-xs">
          <template v-if="tag(STATUS_MAP, detail.status)">
            <Tag :color="tag(STATUS_MAP, detail.status)!.color">{{ tag(STATUS_MAP, detail.status)!.text }}</Tag>
          </template>
          <template v-if="tag(AUDIT_MAP, detail.auditStatus)">
            <Tag :color="tag(AUDIT_MAP, detail.auditStatus)!.color">{{ tag(AUDIT_MAP, detail.auditStatus)!.text }}</Tag>
          </template>
          <template v-if="tag(INDUSTRY_MAP, detail.industryFlag)">
            <Tag :color="tag(INDUSTRY_MAP, detail.industryFlag)!.color">行业：{{ tag(INDUSTRY_MAP, detail.industryFlag)!.text }}</Tag>
          </template>
          <Tag v-if="detail.contentType">{{ CONTENT_TYPE_MAP[detail.contentType] || detail.contentType }}</Tag>
          <Tag v-if="detail.noticeTypeRaw">{{ detail.noticeTypeRaw }}</Tag>
          <span v-if="detail.industryConfidence != null" class="text-gray-400">置信度 {{ (detail.industryConfidence * 100).toFixed(0) }}%</span>
        </div>
        <!-- 基础信息 -->
        <div class="mb-4 grid grid-cols-2 gap-x-6 gap-y-1 rounded border border-gray-100 bg-gray-50 p-3 text-xs text-gray-600">
          <div><b class="text-gray-400">发布单位：</b>{{ empty(detail.issuerName) }}</div>
          <div><b class="text-gray-400">来源：</b>{{ empty(detail.sourceName) }}<span v-if="detail.sourceCode" class="ml-1 text-gray-400">({{ detail.sourceCode }})</span></div>
          <div><b class="text-gray-400">发布时间：</b>{{ fmtT(detail.publishTime) }}</div>
          <div><b class="text-gray-400">生效期：</b>{{ fmtT(detail.effectiveFrom) }} ~ {{ fmtT(detail.effectiveTo) }}</div>
          <div><b class="text-gray-400">关联项目：</b>{{ empty(detail.projectName) }}<span v-if="detail.projectKey" class="ml-1 text-gray-400">({{ detail.projectKey }})</span></div>
          <div><b class="text-gray-400">标段：</b>{{ empty(detail.bidSectionCodes) }}</div>
          <div><b class="text-gray-400">抓取：</b>{{ detail.fetchCount ?? 0 }} 次<template v-if="detail.lastFetchTime">，最近 {{ fmtT(detail.lastFetchTime) }}</template></div>
          <div><b class="text-gray-400">原文：</b><a v-if="detail.origUrl" :href="detail.origUrl" target="_blank" rel="noopener noreferrer" class="text-blue-600 underline">源站链接 ↗</a><span v-else>—</span></div>
        </div>
        <!-- PDF / 抽取摘要 -->
        <div v-if="detail.contentType === 'PDF'" class="mb-3 rounded border border-amber-100 bg-amber-50 p-3 text-xs text-amber-700">
          PDF 原文件：{{ detail.pdfSize ? Math.round(detail.pdfSize / 1024) + ' KB' : '大小未知' }}
          <template v-if="detail.pdfDownloadUrl">｜<a :href="detail.pdfDownloadUrl" target="_blank" rel="noopener noreferrer" class="underline">下载/预览</a></template>
          <div v-if="detail.extractionSummary" class="mt-1 text-gray-500">抽取摘要：{{ detail.extractionSummary }}</div>
        </div>
        <!-- 正文预览：manage/get 下发 jsoup 净化正文 contentHtmlSafe（结构化块状态仅作提示） -->
        <div class="mb-1 flex items-center gap-2 text-xs text-gray-400">
          <span>正文预览（净化版）</span>
          <template v-if="tag(BODY_MAP, detail.bodyBlocksStatus)">
            <Tag :color="tag(BODY_MAP, detail.bodyBlocksStatus)!.color" style="font-size: 11px">正文：{{ tag(BODY_MAP, detail.bodyBlocksStatus)!.text }}</Tag>
          </template>
          <span v-if="detail.bodyParserCode" class="text-gray-400">策略：{{ detail.bodyParserCode === 'AI_FALLBACK' ? 'AI 兜底排版' : '原文结构化' }}</span>
        </div>
        <div v-if="hasBody" class="rounded border border-gray-200 p-4" v-dompurify-html="detail.contentHtmlSafe"></div>
        <div v-else class="rounded border border-dashed border-gray-200 p-6 text-center text-xs text-gray-400">该公告无净化正文（非 HTML 或正文为空），详见上方原文/附件。</div>
        <!-- 附件 -->
        <div v-if="detail.attachments?.length" class="mt-3 text-xs text-gray-500">
          <div class="mb-1 font-bold text-gray-400">附件（{{ detail.attachments.length }}）</div>
          <div v-for="a in detail.attachments" :key="a.id" class="flex items-center gap-2 py-0.5">
            <span>{{ a.name }}</span>
            <Tag v-if="a.virusStatus === 'INFECTED'" color="red" style="font-size: 11px">含病毒已拦截</Tag>
            <Tag v-else-if="a.virusStatus === 'PENDING'" style="font-size: 11px">扫描中</Tag>
            <a v-if="a.downloadUrl && a.virusStatus !== 'INFECTED'" :href="a.downloadUrl" target="_blank" rel="noopener noreferrer" class="text-blue-600 underline">下载</a>
          </div>
        </div>
      </div>
    </div>
  </Modal>
</template>
