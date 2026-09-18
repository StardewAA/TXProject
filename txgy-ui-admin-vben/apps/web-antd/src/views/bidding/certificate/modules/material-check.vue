<script setup lang="ts">
/* 企业材料完备性自查（契约 §7.1）
 * · asOf = 核验基准日：可"以投标截止日为基准"预演（如填未来日期看那时是否仍有效）
 * · 1060107013「材料不完备」是**业务结论**而非错误：优先用 data 渲染，仅当无 data 时用 msg
 * · 1060107014「必备材料清单为空」→ 提示并提供配置入口 */
import { computed, ref } from 'vue';

import { message } from 'ant-design-vue';

import {
  CERT_ERR,
  certErrorCode,
  certErrorMsg,
  type CertificateApi,
  getMaterialCompleteness,
} from '#/api/bidding/certificate';

import AttrFilter from '../../components/AttrFilter.vue';
import { useCertMeta } from '../useCertMeta';

const { dictLabel, dictOptions, dictTag } = useCertMeta();

const entId = ref<number | undefined>(undefined);
const asOf = ref<string | undefined>(undefined);
const loading = ref(false);
const data = ref<CertificateApi.MaterialCompleteness | null>(null);
const emptyMsg = ref('');

/** 结论标签：优先取字典 material_result，缺字典时用契约 §7.1 的三值兜底文案 */
const RESULT_FALLBACK: Record<string, { color: string; label: string }> = {
  PASS: { color: 'green', label: '齐备' },
  RISK: { color: 'orange', label: '有临近到期' },
  FAIL: { color: 'red', label: '有缺失或已过期' },
};
const resultTag = computed(() => {
  const r = data.value?.result;
  if (!r) return null;
  // 字典优先（tagType→颜色由 dictTag 统一映射）；缺字典时用契约 §7.1 兜底文案
  if (dictOptions('material_result').length > 0) return dictTag('material_result', r);
  return RESULT_FALLBACK[r] ?? { color: 'default', label: r };
});

/* 字段逐字对齐 EnterpriseMaterialCompletenessRespVO.MaterialItem：
 *   materialLabel 材料名称 / category 大类 / mandatory 必备 / status 状态 /
 *   validTo 有效期至（字符串；后端 VO 对该字段有"有效期止/至"两种写法，见契约 17） / refName 匹配到的名称或编号 / message 说明 */
const columns = [
  { title: '材料名称', dataIndex: 'materialLabel', key: 'materialLabel', width: 240 },
  { title: '大类', dataIndex: 'category', key: 'category', width: 110 },
  { title: '必备', dataIndex: 'mandatory', key: 'mandatory', width: 80 },
  { title: '状态', dataIndex: 'status', key: 'status', width: 110 },
  { title: '有效期至', dataIndex: 'validTo', key: 'validTo', width: 120 },
  { title: '取数来源', dataIndex: 'refName', key: 'refName', width: 180 },
  { title: '说明', dataIndex: 'message', key: 'message', ellipsis: true },
];

const STATUS_FALLBACK: Record<string, { color: string; label: string }> = {
  READY: { color: 'green', label: '齐备' },
  EXPIRING: { color: 'orange', label: '临近到期' },
  MISSING: { color: 'red', label: '缺失' },
  EXPIRED: { color: 'red', label: '已过期' },
};
function statusTag(s: string) {
  if (dictOptions('material_status').length > 0) return dictTag('material_status', s);
  return STATUS_FALLBACK[s] ?? { color: 'default', label: s };
}

async function run() {
  if (!entId.value) {
    message.warning('请填写企业主体 ID（entId）');
    return;
  }
  loading.value = true;
  emptyMsg.value = '';
  data.value = null;
  try {
    data.value = await getMaterialCompleteness(entId.value, asOf.value);
  } catch (error: unknown) {
    const code = certErrorCode(error);
    if (code === CERT_ERR.MATERIAL_INCOMPLETE) {
      // 部分场景后端以"错误"形式返回阻断结论：此处按契约提示，并引导用户核对台账
      emptyMsg.value = certErrorMsg(error);
      message.warning(certErrorMsg(error));
    } else if (code === CERT_ERR.MATERIAL_LIST_EMPTY) {
      emptyMsg.value = '尚未配置必备材料清单，请先在材料清单配置中维护后再自查。';
      message.warning('尚未配置必备材料清单');
    } else {
      emptyMsg.value = certErrorMsg(error);
    }
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <div>
    <div class="mb-3 rounded-md border border-border bg-card px-3.5 pt-1.5 pb-2.5">
      <AttrFilter label="企业主体 ID（必填）"><a-input-number v-model:value="entId" :min="1" placeholder="企业主体 ID（必填）" style="width: 200px" /></AttrFilter>
      <AttrFilter label="核验基准日（默认今天）"><a-date-picker v-model:value="asOf" value-format="YYYY-MM-DD" placeholder="核验基准日（默认今天）" /></AttrFilter>
      <a-button type="primary" :loading="loading" @click="run">开始自查</a-button>
      <span class="ct-ml">可填未来日期预演"投标截止日是否仍有效"</span>
    </div>
    <div class="ct-panel-b">
      <template v-if="data">
        <div class="flex flex-wrap items-center gap-3 mb-3">
          <a-tag :color="resultTag?.color">{{ resultTag?.label }}</a-tag>
          <span class="ct-panel-meta">
            基准日 {{ asOf || '今天' }}；必备 {{ data.requiredCount }} 项，已齐备 {{ data.readyCount }} 项
          </span>
          <a-progress
            style="width: 220px"
            :percent="data.requiredCount ? Math.round((data.readyCount / data.requiredCount) * 100) : 0"
            size="small"
          />
        </div>
        <a-alert
          v-if="data.missingItems?.length"
          class="mb-2"
          type="error"
          show-icon
          :message="`缺失/过期 ${data.missingItems.length} 项`"
          :description="data.missingItems.join('、')"
        />
        <a-alert
          v-if="data.expiringItems?.length"
          class="mb-3"
          type="warning"
          show-icon
          :message="`临近到期 ${data.expiringItems.length} 项`"
          :description="data.expiringItems.join('、')"
        />
        <a-table
          class="ct-table"
          :columns="columns"
          :data-source="data.items"
          :pagination="false"
          row-key="materialType"
          size="small"
          :scroll="{ x: 1000 }"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'category'">
              {{ dictLabel('material_category', record.category) }}
            </template>
            <template v-else-if="column.key === 'mandatory'">
              <a-tag :color="record.mandatory ? 'red' : 'default'">{{ record.mandatory ? '必备' : '非必备' }}</a-tag>
            </template>
            <template v-else-if="column.key === 'status'">
              <a-tag :color="statusTag(record.status).color">{{ statusTag(record.status).label }}</a-tag>
            </template>
            <template v-else-if="column.key === 'validTo'">
              {{ record.validTo || '长期' }}
            </template>
          </template>
        </a-table>
      </template>
      <div v-else class="ct-empty">
        {{ emptyMsg || '填写企业主体 ID 后开始自查：按身份类/资质类/实力类逐项核对齐备情况' }}
      </div>
    </div>
  </div>
</template>
