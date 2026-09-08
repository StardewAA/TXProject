<!--
  招投标门户 · 招标人发布公告详情（notice public/detail）— 自研渲染器 NoticeHtmlView（L1-L3 分层）
  数据源：公开详情（@PermitAll，仅 PUBLISHED+社会公开；payload 已 SOCIAL 裁剪）
-->
<script lang="ts" setup>
import { onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import NoticeHtmlView from '#/views/bidding/notice/NoticeHtmlView.vue';

import { fetchInternalNoticeDetail } from './api';
import type { InternalNoticeDetail } from './types';

const route = useRoute();
const router = useRouter();
const id = String(route.params.id || '');
const loading = ref(true);
const errorMsg = ref('');
const detail = ref<InternalNoticeDetail>();

async function load(): Promise<void> {
  loading.value = true;
  errorMsg.value = '';
  try {
    detail.value = await fetchInternalNoticeDetail(id);
  } catch (e) {
    errorMsg.value = (e as Error).message || String(e);
  } finally {
    loading.value = false;
  }
}
onMounted(() => void load());
</script>

<template>
  <div class="bidding-page bid-ind">
    <button class="back" @click="router.push('/portal/bidding/internal-hall').catch(() => {})">‹ 返回本平台发布</button>

    <div v-if="loading" class="hint">加载中…</div>
    <div v-else-if="errorMsg" class="err">{{ errorMsg }}</div>
    <div v-else-if="detail" class="paper">
      <NoticeHtmlView :detail="detail" />
    </div>
  </div>
</template>

<style scoped>
.bidding-page { max-width: 880px; margin: 0 auto; padding: 16px 16px 48px; }
.back { border: none; background: none; color: #1d4ed8; font-size: 13px; cursor: pointer; margin-bottom: 10px; }
.hint { color: #9ca3af; text-align: center; padding: 60px 0; font-size: 13px; }
.err { color: #dc2626; padding: 30px 0; font-size: 13px; text-align: center; }
.paper { background: #fff; border: 1px solid #e4e7ec; border-radius: 10px; padding: 26px 32px; }
</style>
