<!-- ============================================================================
  招标全流程 · 全景条（FlowPanorama）—— 实体详情顶部的"完整生命周期"可视化
  业务（用户 2026-09-28 需求：招标全流程更人性化、更完整的展示）：
    · 五阶段全景（前期准备 → 投标阶段 → 开标评标 → 定标阶段 → 收尾归档）+ 异常终态；
    · 当前实体状态映射到阶段并高亮（phaseOf），下一步可执行动作（next-actions）直接呈现；
    · 状态/阶段中文映射走 flow/labels（字典优先、静态兜底），不手敲英文。
  契约：GET /bidding/flow/next-actions（FlowNextActionRespVO：event/label/toState）；权限 bidding:flow:query。
  用法：<FlowPanorama biz-type="TENDER_PROJECT" biz-code="TP2026..." :current-status="record.status" />
============================================================================ -->
<script lang="ts" setup>
import { onMounted, ref } from 'vue';

import { getFlowNextActions } from '#/api/bidding/flow';
import { phaseOf, statusDescOf, statusTextOf } from '#/api/bidding/flow/labels';

const props = withDefaults(
  defineProps<{
    bizType: 'PROJECT' | 'TENDER_PROJECT' | 'BID_SECTION';
    bizCode: string;
    currentStatus?: null | string;
  }>(),
  {},
);

/** 五阶段顺序（ABNORMAL 异常终态单独呈现，不参与主流程） */
const PHASE_STEPS: Array<{ key: string; text: string; desc: string }> = [
  { key: 'PREPARE', text: '前期准备', desc: '立项/编制/审批' },
  { key: 'BID', text: '投标阶段', desc: '公告/获取文件/递交' },
  { key: 'EVALUATE', text: '开标评标', desc: '开标/专家评审' },
  { key: 'DECIDE', text: '定标阶段', desc: '候选人公示/定标' },
  { key: 'FINISH', text: '收尾归档', desc: '通知/合同/归档' },
];

const nextActions = ref<Array<{ event: string; label: string; toState: string }>>([]);
const actionsLoading = ref(false);
const actionsError = ref('');

/** 当前阶段序号（0 基；未知 = -1；异常终态 = 99） */
const activeIndex = (): number => {
  const phaseText = phaseOf(props.bizType, props.currentStatus);
  if (phaseText === '异常终态') return 99;
  const hit = PHASE_STEPS.findIndex((s) => s.text === phaseText);
  return hit;
};

async function loadNextActions(): Promise<void> {
  if (!props.bizCode) return;
  actionsLoading.value = true;
  actionsError.value = '';
  try {
    const list = await getFlowNextActions({ bizType: props.bizType, bizCode: props.bizCode });
    nextActions.value = Array.isArray(list) ? list : [];
  } catch (e) {
    actionsError.value = (e as Error).message || String(e);
    nextActions.value = [];
  } finally {
    actionsLoading.value = false;
  }
}

onMounted(() => {
  void loadNextActions();
});
</script>

<template>
  <div class="fp-wrap">
    <div class="fp-head">
      <span class="fp-title">招标全流程 · 生命周期全景</span>
      <span class="fp-meta">
        当前状态：<b class="fp-state">{{ statusTextOf(bizType, currentStatus) }}</b>
        <template v-if="statusDescOf(bizType, currentStatus)">
          · {{ statusDescOf(bizType, currentStatus) }}
        </template>
      </span>
    </div>

    <div class="fp-steps">
      <template v-for="(s, i) in PHASE_STEPS" :key="s.key">
        <div class="fp-step" :class="{ 'fp-on': i === activeIndex(), 'fp-done': i < activeIndex() }">
          <div class="fp-dot">
            <span v-if="i < activeIndex()">✓</span>
            <span v-else>{{ i + 1 }}</span>
          </div>
          <div class="fp-t">{{ s.text }}</div>
          <div class="fp-d">{{ s.desc }}</div>
        </div>
        <div v-if="i < PHASE_STEPS.length - 1" class="fp-line" :class="{ 'fp-line-on': i < activeIndex() }"></div>
      </template>
    </div>

    <div v-if="activeIndex() === 99" class="fp-abnormal">
      ⚠ 当前处于<b>异常终态</b>（终止/流标）——该流程已停止推进。
    </div>

    <div v-if="nextActions.length" class="fp-actions">
      <span class="fp-actions-t">下一步可执行：</span>
      <a-tag v-for="a in nextActions" :key="a.event" color="blue" class="fp-act">{{ a.label }}<span class="fp-to">→ {{ statusTextOf(bizType, a.toState) }}</span></a-tag>
    </div>
    <div v-else-if="actionsLoading" class="fp-actions fp-muted">下一步动作加载中…</div>
    <div v-else-if="actionsError" class="fp-actions fp-muted">下一步动作不可达（{{ actionsError }}）——发布/流转请走对应工作台</div>
    <div v-else-if="!actionsLoading" class="fp-actions fp-muted">当前无下一步动作（草稿/终态或流程未登记）</div>
  </div>
</template>

<style scoped>
.fp-wrap {
  border: 1px solid #e4e8ec;
  background: #fff;
  border-radius: 6px;
  padding: 12px 16px 14px;
  margin-bottom: 14px;
}
.fp-head {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 8px 14px;
  margin-bottom: 10px;
}
.fp-title { font-size: 14px; font-weight: 700; color: #2e3947; }
.fp-meta { font-size: 12.5px; color: #8a949e; }
.fp-state { color: #c6362e; }
.fp-steps {
  display: flex;
  align-items: flex-start;
  margin-bottom: 10px;
}
.fp-step { flex: 0 0 auto; width: 132px; text-align: center; }
.fp-dot {
  width: 26px; height: 26px; margin: 0 auto 5px;
  border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  font-size: 12.5px; font-weight: 600;
  color: #8a949e; background: #f1f3f6; border: 1.5px solid #d9dee5;
  transition: all .15s ease;
}
.fp-step.fp-done .fp-dot { color: #fff; background: #2f9e6e; border-color: #2f9e6e; }
.fp-step.fp-on .fp-dot { color: #fff; background: #c6362e; border-color: #c6362e; box-shadow: 0 0 0 4px rgb(198 54 46 / 12%); }
.fp-t { font-size: 12.5px; font-weight: 600; color: #2e3947; }
.fp-step.fp-on .fp-t { color: #c6362e; }
.fp-d { font-size: 11px; color: #9aa3ac; margin-top: 2px; }
.fp-step.fp-on .fp-d { color: #b07a72; }
.fp-line {
  flex: 1 1 auto; height: 2.5px; margin-top: 12px;
  background: #e8ecf0; border-radius: 2px; min-width: 14px;
}
.fp-line-on { background: #2f9e6e; }
.fp-abnormal {
  margin: 4px 0 8px; padding: 6px 10px;
  background: #fdf3f2; border: 1px solid #f2c9c4; border-radius: 4px;
  color: #a43b30; font-size: 12.5px;
}
.fp-actions { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; font-size: 12.5px; color: #2e3947; }
.fp-actions-t { color: #6b7681; }
.fp-act { margin-right: 0 !important; }
.fp-to { margin-left: 4px; font-weight: 400; color: #8a949e; }
.fp-muted { color: #9aa3ac; }
</style>
