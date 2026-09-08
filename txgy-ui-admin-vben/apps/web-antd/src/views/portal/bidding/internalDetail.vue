<!--
  招投标门户 · 招标人发布公告详情（notice public/detail）
  形态：结构化 payload（权威数据集字段全集） + content 正文 vN + PDF 原文件元数据
-->
<script lang="ts" setup>
import { onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { fetchInternalNoticeDetail } from './api';
import { fmtDT } from './format';
import type { InternalNoticeDetail } from './types';

const route = useRoute();
const router = useRouter();
const id = String(route.params.id || '');
const loading = ref(true);
const errorMsg = ref('');
const detail = ref<InternalNoticeDetail>();

const TYPE_LABEL: Record<string, string> = {
  TENDER_NOTICE: '招标公告',
  CHANGE_NOTICE: '变更公告',
  WIN_CANDIDATE_PUBLICITY: '中标候选人公示',
  WIN_RESULT_PUBLICITY: '中标结果公示',
};
const payloadEntries = ref<Array<{ k: string; v: string }>>([]);

async function load(): Promise<void> {
  loading.value = true;
  errorMsg.value = '';
  try {
    const d = await fetchInternalNoticeDetail(id);
    detail.value = d;
    const p = d.payload || {};
    payloadEntries.value = Object.entries(p).map(([k, v]) => ({
      k,
      v: v === null || v === undefined ? '' : String(v),
    }));
  } catch (e) {
    errorMsg.value = (e as Error).message || String(e);
  } finally {
    loading.value = false;
  }
}
onMounted(() => void load());
</script>

<template>
  <div class="bidding-page">
    <button class="back" @click="router.push('/portal/bidding/internal-hall').catch(() => {})">‹ 返回本平台发布</button>

    <div v-if="loading" class="hint">加载中…</div>
    <div v-else-if="errorMsg" class="err">{{ errorMsg }}</div>
    <template v-else-if="detail">
      <div class="paper">
        <div class="head">
          <h1>{{ detail.title }}</h1>
          <div class="head-meta">
            <span class="tag-type">{{ detail.noticeTypeName || TYPE_LABEL[detail.noticeType || ''] || detail.noticeType }}</span>
            <span class="pub">{{ detail.publisherName }}</span>
            <span v-if="detail.publishTime" class="time">发布时间：{{ fmtDT(detail.publishTime) }}</span>
            <span v-if="detail.noticeCode" class="code">编号：{{ detail.noticeCode }}</span>
            <span class="ver">正文版本 v{{ detail.contentVersion ?? 1 }}</span>
          </div>
          <div v-if="detail.projectCode || detail.projectName" class="proj">
            项目：{{ detail.projectCode ? detail.projectCode + ' · ' : '' }}{{ detail.projectName || '' }}
          </div>
        </div>

        <div v-if="payloadEntries.length" class="sec">
          <div class="sec-t">结构化信息（权威数据集）</div>
          <table class="kv">
            <tbody>
              <tr v-for="e in payloadEntries" :key="e.k">
                <td class="k">{{ e.k }}</td>
                <td>{{ e.v }}</td>
              </tr>
            </tbody>
          </table>
        </div>

        <div class="sec">
          <div class="sec-t">公告正文（v{{ detail.contentVersion ?? 1 }}）</div>
          <div v-if="detail.content" class="content">{{ detail.content }}</div>
          <div v-else class="none">（本公告暂无正文 — 发布校验要求正文与 PDF 原文件齐全）</div>
        </div>

        <div v-if="detail.pdfFileId" class="pdf-note">
          版式文件：PDF 原文件（file_id #{{ detail.pdfFileId }}{{ detail.pdfSize ? '，' + Math.round(Number(detail.pdfSize) / 1024) + ' KB' : '' }}）— 文件服务签名下载地址回填后此处可直接打开/下载
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.bidding-page { max-width: 860px; margin: 0 auto; padding: 16px 16px 48px; }
.back { border: none; background: none; color: #1d4ed8; font-size: 13px; cursor: pointer; margin-bottom: 10px; }
.hint { color: #9ca3af; text-align: center; padding: 60px 0; font-size: 13px; }
.err { color: #dc2626; padding: 30px 0; font-size: 13px; text-align: center; }
.paper { background: #fff; border: 1px solid #e4e7ec; border-radius: 10px; padding: 28px 34px; }
.head h1 { font-size: 22px; line-height: 1.5; margin: 0 0 10px; color: #111827; }
.head-meta { display: flex; flex-wrap: wrap; gap: 12px; align-items: center; font-size: 13px; color: #6b7280; padding-bottom: 12px; border-bottom: 1px solid #eef0f3; }
.tag-type { color: #1d4ed8; background: #eff4ff; border: 1px solid #c7d7f7; border-radius: 4px; padding: 1px 8px; font-size: 12px; }
.proj { margin-top: 10px; font-size: 13px; color: #374151; background: #f7f8fa; padding: 8px 12px; border-radius: 6px; }
.sec { margin-top: 22px; }
.sec-t { font-size: 15px; font-weight: 600; margin-bottom: 10px; color: #111827; border-left: 4px solid #1d4ed8; padding-left: 8px; }
table.kv { width: 100%; border-collapse: collapse; font-size: 13px; }
table.kv td { border: 1px solid #eef0f3; padding: 7px 10px; vertical-align: top; }
table.kv td.k { width: 220px; font-family: ui-monospace, monospace; color: #6b7280; background: #fafbfc; }
.content { white-space: pre-wrap; font-size: 14px; line-height: 1.9; color: #1f2937; }
.none { color: #9ca3af; font-size: 13px; }
.pdf-note { margin-top: 24px; font-size: 12px; color: #92400e; background: #fef3c7; border: 1px solid #fde68a; border-radius: 6px; padding: 8px 12px; }
</style>
