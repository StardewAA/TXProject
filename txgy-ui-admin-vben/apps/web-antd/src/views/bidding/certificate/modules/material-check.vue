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
import { fetchSubjectProfile } from '#/api/bidding/subject';

import AttrFilter from '../../components/AttrFilter.vue';
import MaterialItemsTable from '../../components/MaterialItemsTable.vue';
import { materialResultFallback } from '../../components/materialMeta';
import { useCertMeta } from '../useCertMeta';

// 明细表的状态/大类字典已随表格收进 MaterialItemsTable，本页只剩「结论标签」要用字典
const { dictOptions, dictTag } = useCertMeta();

const entId = ref<number | undefined>(undefined);
const asOf = ref<string | undefined>(undefined);

/* 本企业：普通租户只能看自己，无需也不应"选企业"——由主体档案自动带出并只读展示名称
 * 口径依据：07-主体域前端交付-业务口径说明（企业主体=法人/其他组织，uscc 唯一键）；
 * 主体档案 GET /bidding/subject/profile/get 返回本租户档案（含 id/subjectName/uscc），
 * 故前端直接取本企业，不提供企业选择器（平台跨租户查看走审查任务中心/名称搜索）。 */
const subjectLoading = ref(false);
const currentSubject = ref<{ id: number; label: string } | null>(null);
async function loadCurrentSubject(): Promise<void> {
  subjectLoading.value = true;
  try {
    // 契约《主体档案合并一租户一档》：投标/招标共用同一份企业档案，单次无类型调用
    const profile = await fetchSubjectProfile().catch(() => null);
    if (profile?.id !== undefined && profile?.id !== null && profile.subjectName) {
      const label = profile.uscc ? profile.subjectName + '（' + profile.uscc + '）' : profile.subjectName;
      currentSubject.value = { id: Number(profile.id), label };
      entId.value = Number(profile.id);
    }
  } finally {
    subjectLoading.value = false;
  }
}
void loadCurrentSubject();
const loading = ref(false);
const data = ref<CertificateApi.MaterialCompleteness | null>(null);
const emptyMsg = ref('');

/** 结论标签：优先取字典 material_result，缺字典时用 ../components/materialMeta 的统一兜底文案
 * （原先本文件自写「有临近到期」，与资质页的「部分缺失或临近到期」不一致：RISK 是"有缺失**或**有临近到期"，
 *   只说后半句会让人漏掉缺失项；文案现已收敛到 materialMeta，两页共用）。 */
const resultTag = computed(() => {
  const r = data.value?.result;
  if (!r) return null;
  // 字典优先（tagType→颜色由 dictTag 统一映射）
  if (dictOptions('material_result').length > 0) return dictTag('material_result', r);
  return materialResultFallback(r);
});

/* 逐项明细的列定义 / 状态兜底 / 两条汇总提示已统一到 ../../components/MaterialItemsTable.vue
 * （字段口径：materialLabel 材料名称 / category 大类 / mandatory 必备 / status 状态 /
 *   validTo 有效期至（后端 VO 有"有效期止/至"两种写法，见契约 17） / refName 取数来源 / message 说明） */

async function run() {
  if (!entId.value) {
    message.warning('未取到本企业主体档案：请先在「主体档案」完善企业档案（统一社会信用代码）');
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
      <AttrFilter label="本企业">
        <span class="ct-id">{{ subjectLoading ? '加载中…' : currentSubject?.label || '未取到主体档案（请先在「主体档案」完善企业信息）' }}</span>
      </AttrFilter>
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
        <!-- 两条汇总提示 + 逐项明细：与资质页页签⑤ 共用 MaterialItemsTable（列/状态兜底/空态文案同源） -->
        <MaterialItemsTable :data="data" :scroll-x="1000" />
      </template>
      <div v-else class="ct-empty">
        {{ emptyMsg || '本企业自动带出（主体档案），点「开始自查」按身份类/资质类/实力类逐项核对齐备情况' }}
      </div>
    </div>
  </div>
</template>
