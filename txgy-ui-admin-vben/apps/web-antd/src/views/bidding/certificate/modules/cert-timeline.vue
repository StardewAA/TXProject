<script setup lang="ts">
/* 某对象的生命周期轨迹（契约 §6.6 list-by-biz，按 appliedAt 倒序） */
import { onMounted, ref, watch } from 'vue';

import { type CertificateApi, getCertLifecycleByBiz } from '#/api/bidding/certificate';

import { useCertMeta } from '../useCertMeta';

const props = defineProps<{
  bizDomain?: string;
  bizId?: null | number;
}>();

const { dictLabel, dictTag } = useCertMeta();
const loading = ref(false);
const rows = ref<CertificateApi.Lifecycle[]>([]);

async function load() {
  if (!props.bizId) {
    rows.value = [];
    return;
  }
  loading.value = true;
  try {
    rows.value = await getCertLifecycleByBiz(props.bizDomain ?? 'CERT', props.bizId);
  } catch {
    rows.value = [];
  } finally {
    loading.value = false;
  }
}
onMounted(load);
watch(() => [props.bizDomain, props.bizId], load);
</script>

<template>
  <div class="ct-panel">
    <div class="ct-panel-h">
      <span class="ct-panel-t">变更历史</span>
      <span class="ct-panel-meta">变更 / 续期 / 注销吊销 / 年检 全轨迹，按申请时间倒序</span>
    </div>
    <div class="ct-panel-b">
      <a-spin :spinning="loading">
        <ul v-if="rows.length" class="ct-tl">
          <li v-for="r in rows" :key="r.id">
            <div class="tl-t">
              <a-tag>{{ dictLabel('lifecycle_type', r.lifecycleType) }}</a-tag>
              <a-tag :color="dictTag('lifecycle_status', r.status).color">
                {{ dictTag('lifecycle_status', r.status).label }}
              </a-tag>
              <span class="ct-id">{{ r.applyNo }}</span>
            </div>
            <div class="tl-m">
              <template v-if="r.lifecycleType === 'CHANGE'">
                <div>
                  <span class="tl-k">变更：</span>
                  {{ r.fieldName || r.changeSummary || '—' }}
                  <template v-if="r.fieldName">
                    ：<span class="tl-k">{{ r.oldValue || '空' }}</span> → <b>{{ r.newValue || '空' }}</b>
                  </template>
                </div>
              </template>
              <template v-else-if="r.lifecycleType === 'RENEW'">
                <div>
                  <span class="tl-k">续期：</span>{{ dictLabel('renewal_type', r.renewalType) }}；新有效期
                  {{ r.newValidFrom || '—' }} ~ {{ r.newValidTo || '—' }}
                </div>
              </template>
              <template v-else-if="r.lifecycleType === 'REVOKE'">
                <div>
                  <span class="tl-k">注销吊销：</span>{{ dictLabel('revoke_type', r.revokeType) }}；事由
                  {{ r.revokeReason || '—' }}
                </div>
              </template>
              <template v-else-if="r.lifecycleType === 'ANNUAL'">
                <div><span class="tl-k">年检：</span>{{ r.reviewYear ?? '—' }} 年度，{{ r.reviewDate || '—' }}</div>
              </template>
              <div>
                <span class="tl-k">申请：</span>{{ r.appliedAt || '—' }}
                <template v-if="r.handledAt">；处理：{{ r.handledAt }}</template>
                <template v-if="r.effectiveAt">；生效：{{ r.effectiveAt }}</template>
              </div>
              <div v-if="r.remark"><span class="tl-k">备注：</span>{{ r.remark }}</div>
            </div>
          </li>
        </ul>
        <div v-else class="ct-empty">暂无生命周期记录</div>
      </a-spin>
    </div>
  </div>
</template>
