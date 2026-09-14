<script setup lang="ts">
/* 某对象的生命周期轨迹（契约 §6.6 list-by-biz，按 appliedAt 倒序）
 * §6.6④：同一次变更（changeGroupId 相同）必须**合并成一个节点**展示多条字段变更，而非每行一个节点。 */
import { computed, onMounted, ref, watch } from 'vue';

import { type CertificateApi, getCertLifecycleByBiz } from '#/api/bidding/certificate';

import { fmtDate, fmtDateTime } from '../format';
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

/** §6.6④ 按 changeGroupId 归组：同组的多条字段变更合成一个时间轴节点 */
interface TimelineNode {
  groupId: string;
  head: CertificateApi.Lifecycle;
  members: CertificateApi.Lifecycle[];
}
const nodes = computed<TimelineNode[]>(() => {
  const out: TimelineNode[] = [];
  const index = new Map<string, TimelineNode>();
  for (const r of rows.value) {
    const key = r.changeGroupId || `#${r.id}`;
    const hit = index.get(key);
    if (hit) {
      hit.members.push(r);
      continue;
    }
    const node: TimelineNode = { groupId: key, head: r, members: [r] };
    index.set(key, node);
    out.push(node);
  }
  return out;
});
</script>

<template>
  <div class="ct-panel">
    <div class="ct-panel-h">
      <span class="ct-panel-t">变更历史</span>
      <span class="ct-panel-meta">变更 / 续期 / 注销吊销 / 年检 全轨迹，按申请时间倒序</span>
    </div>
    <div class="ct-panel-b">
      <a-spin :spinning="loading">
        <ul v-if="nodes.length" class="ct-tl">
          <li v-for="node in nodes" :key="node.groupId">
            <div class="tl-t">
              <a-tag>{{ dictLabel('lifecycle_type', node.head.lifecycleType) }}</a-tag>
              <a-tag :color="dictTag('lifecycle_status', node.head.status).color">
                {{ dictTag('lifecycle_status', node.head.status).label }}
              </a-tag>
              <span class="ct-id">{{ node.head.applyNo }}</span>
              <a-tag v-if="node.members.length > 1" color="blue">{{ node.members.length }} 项变更</a-tag>
            </div>
            <div class="tl-m">
              <!-- §6.6④ 同一次变更（同 changeGroupId）合并展示所有字段对照 -->
              <template v-if="node.head.lifecycleType === 'CHANGE'">
                <div v-for="m in node.members" :key="m.id">
                  <span class="tl-k">变更：</span>
                  {{ m.fieldName || m.changeSummary || '—' }}
                  <template v-if="m.fieldName">
                    ：<span class="tl-k">{{ m.oldValue || '空' }}</span> → <b>{{ m.newValue || '空' }}</b>
                  </template>
                </div>
              </template>
              <template v-else-if="node.head.lifecycleType === 'RENEW'">
                <div>
                  <span class="tl-k">续期：</span>{{ dictLabel('renewal_type', node.head.renewalType) }}；新有效期
                  {{ fmtDate(node.head.newValidFrom) }} ~ {{ fmtDate(node.head.newValidTo) }}
                </div>
              </template>
              <template v-else-if="node.head.lifecycleType === 'REVOKE'">
                <div>
                  <span class="tl-k">注销吊销：</span>{{ dictLabel('revoke_type', node.head.revokeType) }}；事由
                  {{ node.head.revokeReason || '—' }}
                </div>
              </template>
              <template v-else-if="node.head.lifecycleType === 'ANNUAL'">
                <div>
                  <span class="tl-k">年检：</span>{{ node.head.reviewYear ?? '—' }} 年度，{{
                    fmtDate(node.head.reviewDate)
                  }}
                </div>
              </template>
              <div>
                <span class="tl-k">申请：</span>{{ fmtDateTime(node.head.appliedAt) }}
                <template v-if="node.head.handledAt">；处理：{{ fmtDateTime(node.head.handledAt) }}</template>
                <template v-if="node.head.effectiveAt">；生效：{{ fmtDateTime(node.head.effectiveAt) }}</template>
              </div>
              <div v-if="node.head.remark"><span class="tl-k">备注：</span>{{ node.head.remark }}</div>
            </div>
          </li>
        </ul>
        <div v-else class="ct-empty">暂无生命周期记录</div>
      </a-spin>
    </div>
  </div>
</template>
