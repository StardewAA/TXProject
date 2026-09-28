<script lang="ts" setup>
import { computed, ref } from 'vue';

import { IconifyIcon } from '@vben/icons';

/**
 * AI 诊断报告面板。
 *
 * 数据来自两个任务：「简历诊断」给分数与各维度，由此出雷达和维度条；
 * 「简历优化」给逐条的原文/建议文，聚合成「优化详情」里的前后对照 ——
 * 诊断本身不含改写后的文字，前端要把两者拼在一起才够得着参考站那张报告。
 */

export interface ReportDimension {
  key: string;
  score: number;
  comment?: string;
}

export interface ReportDetailItem {
  module: string;
  insight: string[];
  before: string;
  after: string;
}

const props = withDefaults(
  defineProps<{
    /** 报告标题，一般用简历名 + 本次诊断的主题 */
    heading?: string;
    /** 一句话总评 */
    summary?: string;
    score: number;
    dimensions: ReportDimension[];
    details?: ReportDetailItem[];
    /** 主要优化成果 */
    achievements?: string[];
    /** 进一步优化建议 */
    further?: string[];
    /** 诊断或优化还在跑 */
    loading?: boolean;
  }>(),
  {
    achievements: () => [],
    details: () => [],
    further: () => [],
    heading: '',
    loading: false,
    summary: '',
  },
);

const emit = defineEmits<{ close: [] }>();

/** 维度 key → 中文名。带职位的诊断会多出 JOB_MATCH，一并留着，免得旧任务显示成英文 */
const DIMENSION_LABELS: Record<string, string> = {
  CLARITY: '逻辑清晰',
  STAR: 'STAR应用',
  READABILITY: '内容可读',
  COMPLETENESS: '内容完整',
  PROFESSIONALISM: '专业表达',
  QUANTIFICATION: '成果量化',
  JOB_MATCH: '职位匹配',
};

/** 参考站上「优化后 / 优化前」的对照开关，默认看优化后 */
const compareMode = ref<'after' | 'before'>('after');

/** 关掉对照：回到只显示当前版本 */
const compareOn = ref(true);

const labeledDimensions = computed(() =>
  props.dimensions.map((item) => ({
    ...item,
    label: DIMENSION_LABELS[item.key] || item.key,
    score: Math.max(0, Math.min(100, Math.round(item.score || 0))),
  })),
);

/*
 * 雷达图：正多边形顶点，第 1 个顶点朝正上方。
 * 用 SVG 手算而没引图表库 —— 六根轴的固定图形，引一个库进来不划算。
 *
 * viewBox 要比「圆心 + 半径」宽出一圈：轴标签挂在顶点外侧，只按图形取景会把
 * 「成果量化」这类四字标签切掉首尾字。
 */
const RADIUS = 80;
const CENTER = 150;
/** 标签离圆心的距离，比半径再外推一截，避免压在多边形上 */
const LABEL_RADIUS = RADIUS + 30;

const radarPoints = computed(() => {
  const list = labeledDimensions.value;
  if (list.length < 3) return '';
  return list
    .map((item, index) => {
      const angle = (Math.PI * 2 * index) / list.length - Math.PI / 2;
      const ratio = item.score / 100;
      const x = CENTER + RADIUS * ratio * Math.cos(angle);
      const y = CENTER + RADIUS * ratio * Math.sin(angle);
      return `${x.toFixed(1)},${y.toFixed(1)}`;
    })
    .join(' ');
});

/** 雷达外圈的网格：取 100 / 66 / 33 三档，画成同心正多边形 */
const radarGrid = computed(() => {
  const list = labeledDimensions.value;
  if (list.length < 3) return [];
  return [1, 0.66, 0.33].map((ratio) =>
    list
      .map((_, index) => {
        const angle = (Math.PI * 2 * index) / list.length - Math.PI / 2;
        const x = CENTER + RADIUS * ratio * Math.cos(angle);
        const y = CENTER + RADIUS * ratio * Math.sin(angle);
        return `${x.toFixed(1)},${y.toFixed(1)}`;
      })
      .join(' '),
  );
});

/** 轴标签落在顶点外侧 */
const radarLabels = computed(() => {
  const list = labeledDimensions.value;
  return list.map((item, index) => {
    const angle = (Math.PI * 2 * index) / list.length - Math.PI / 2;
    const x = CENTER + LABEL_RADIUS * Math.cos(angle);
    const y = CENTER + LABEL_RADIUS * Math.sin(angle);
    // 最左/最右两枚标签贴着画布边缘，居中锚点会往外溢，靠边时改成向内对齐
    const anchor = x <= CENTER - RADIUS ? 'end' : x >= CENTER + RADIUS ? 'start' : 'middle';
    return { label: item.label, x, y, key: item.key, anchor };
  });
});

/** 每根轴的轴线 */
const radarAxes = computed(() => {
  const list = labeledDimensions.value;
  return list.map((_, index) => {
    const angle = (Math.PI * 2 * index) / list.length - Math.PI / 2;
    return {
      x: CENTER + RADIUS * Math.cos(angle),
      y: CENTER + RADIUS * Math.sin(angle),
    };
  });
});

function scoreTone(score: number) {
  if (score >= 80) return 'is-good';
  if (score >= 60) return 'is-mid';
  return 'is-low';
}

/**
 * 有没有诊断那部分的数据。
 *
 * 维度为空说明这次只跑了优化（没用诊断），此时不该渲染总分和雷达：
 * 总分拿不到会显示成「0 分」，看上去像在说这份简历一无是处。
 */
const hasDiagnosis = computed(() => labeledDimensions.value.length > 0);
</script>

<template>
  <div class="report">
    <header class="report-head">
      <div class="head-title">
        <span>AI诊断报告</span>
        <button
          class="tip"
          type="button"
          title="报告由简历诊断与简历优化两个 AI 任务合成，结论仅供参考"
          aria-label="报告说明"
        >
          <IconifyIcon icon="lucide:info" />
        </button>
        <button class="close" type="button" aria-label="关闭诊断报告" @click="emit('close')">
          <IconifyIcon icon="lucide:x" />
        </button>
      </div>
      <p v-if="heading" class="head-sub">{{ heading }}</p>
    </header>

    <div class="report-body">
      <div v-if="loading" class="report-loading">
        <IconifyIcon icon="lucide:loader-circle" class="spin" />
        <span>正在生成诊断报告…</span>
      </div>

      <template v-else>
        <div class="toolbar">
          <span class="switch-label">
            <IconifyIcon icon="lucide:git-compare" /> 对照诊改
          </span>
          <div class="seg">
            <button
              type="button"
              :class="{ active: compareMode === 'after' }"
              @click="compareMode = 'after'"
            >
              优化后
            </button>
            <button
              type="button"
              :class="{ active: compareMode === 'before' }"
              @click="compareMode = 'before'"
            >
              优化前
            </button>
          </div>
        </div>

        <div v-if="hasDiagnosis" class="score-block">
          <b class="score">{{ Math.round(score) }}<small>分</small></b>
          <p v-if="summary" class="score-summary">{{ summary }}</p>
        </div>

        <div v-if="labeledDimensions.length >= 3" class="radar">
          <svg :viewBox="`0 0 ${CENTER * 2} ${CENTER * 2}`" role="img" aria-label="能力维度雷达图">
            <polygon
              v-for="(points, index) in radarGrid"
              :key="`grid-${index}`"
              :points="points"
              class="grid"
            />
            <line
              v-for="(axis, index) in radarAxes"
              :key="`axis-${index}`"
              :x1="CENTER"
              :y1="CENTER"
              :x2="axis.x"
              :y2="axis.y"
              class="axis"
            />
            <polygon :points="radarPoints" class="area" />
            <text
              v-for="item in radarLabels"
              :key="item.key"
              :x="item.x"
              :y="item.y"
              class="axis-label"
              :text-anchor="item.anchor"
              dominant-baseline="middle"
            >
              {{ item.label }}
            </text>
          </svg>
        </div>

        <ul v-if="hasDiagnosis" class="dim-list">
          <li v-for="item in labeledDimensions" :key="item.key">
            <span class="dim-name">{{ item.label }}</span>
            <span class="dim-bar"><i :style="{ width: `${item.score}%` }" /></span>
            <span class="dim-score" :class="scoreTone(item.score)">{{ item.score }}分</span>
          </li>
        </ul>

        <section v-if="achievements.length || further.length" class="block">
          <h3>优化概述</h3>

          <template v-if="achievements.length">
            <h4>主要优化成果</h4>
            <ol class="text-list">
              <li v-for="(item, index) in achievements" :key="`ach-${index}`">{{ item }}</li>
            </ol>
          </template>

          <template v-if="further.length">
            <h4>进一步优化建议</h4>
            <ol class="text-list">
              <li v-for="(item, index) in further" :key="`fur-${index}`">{{ item }}</li>
            </ol>
          </template>
        </section>

        <section v-if="details.length" class="block">
          <h3>优化详情</h3>
          <article v-for="(item, index) in details" :key="`dt-${index}`" class="detail">
            <h4>{{ item.module }}</h4>

            <div v-if="item.insight.length" class="insight">
              <strong>专家洞察</strong>
              <p v-for="(line, i) in item.insight" :key="i">{{ line }}</p>
            </div>

            <div v-if="compareOn" class="compare">
              <div class="compare-col is-before">
                <span class="col-tag">优化前</span>
                <p>{{ item.before || '（原文无此段）' }}</p>
              </div>
              <div class="compare-col is-after">
                <span class="col-tag">优化后</span>
                <p>{{ item.after || '（未改动）' }}</p>
              </div>
            </div>
            <div v-else class="compare-single">
              <p>{{ compareMode === 'after' ? item.after : item.before }}</p>
            </div>
          </article>
        </section>

        <p v-if="!details.length && !achievements.length && !further.length" class="report-note">
          本次诊断只给出了维度评分，没有附带改写建议。
        </p>
      </template>
    </div>
  </div>
</template>

<style scoped>
.report {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  background: #fff;
}

.report-head {
  flex-shrink: 0;
  padding: 20px 20px 12px;
  border-bottom: 1px solid #f0f0f0;
}

.head-title {
  display: flex;
  align-items: center;
  gap: 6px;
}

.head-title > span {
  color: #000;
  font-size: 18px;
  font-weight: 500;
  line-height: 26px;
}

.tip,
.close {
  display: flex;
  padding: 0;
  border: 0;
  background: transparent;
  color: #999;
  font-size: 16px;
  cursor: pointer;
}

.close {
  margin-left: auto;
  font-size: 18px;
}

.close:hover {
  color: #292929;
}

.head-sub {
  margin: 4px 0 0;
  color: #949494;
  font-size: 13px;
  line-height: 20px;
}

.report-body {
  flex: 1;
  min-height: 0;
  padding: 14px 20px 28px;
  overflow-y: auto;
}

.report-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 70px 0;
  color: #999;
  font-size: 14px;
}

.spin {
  animation: report-spin 1s linear infinite;
}

@keyframes report-spin {
  to {
    transform: rotate(360deg);
  }
}

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.switch-label {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: #7d7d7d;
  font-size: 13px;
}

.seg {
  display: inline-flex;
  padding: 2px;
  border-radius: 999px;
  background: #f3f3f3;
}

.seg button {
  padding: 4px 14px;
  border: 0;
  border-radius: 999px;
  background: transparent;
  color: #7d7d7d;
  font-size: 12px;
  cursor: pointer;
  transition: 0.2s ease-in-out;
}

.seg button.active {
  background: #fff;
  color: #292929;
  font-weight: 500;
}

.score-block {
  padding-bottom: 14px;
}

.score {
  color: #292929;
  font-size: 34px;
  font-weight: 700;
  line-height: 1.1;
}

.score small {
  margin-left: 2px;
  font-size: 14px;
  font-weight: 500;
}

.score-summary {
  margin: 8px 0 0;
  color: #5e5e5e;
  font-size: 13px;
  line-height: 1.8;
}

.radar {
  display: flex;
  justify-content: center;
  padding: 4px 0 10px;
}

.radar svg {
  width: 100%;
  max-width: 270px;
  height: auto;
}

.grid {
  fill: none;
  stroke: #ececec;
  stroke-width: 1;
}

.axis {
  stroke: #f0f0f0;
  stroke-width: 1;
}

.area {
  fill: rgb(154 181 131 / 45%);
  stroke: #8aa377;
  stroke-width: 1.5;
}

.axis-label {
  fill: #7d7d7d;
  font-size: 13px;
}

.dim-list {
  margin: 0;
  padding: 0;
  list-style: none;
}

.dim-list li {
  display: grid;
  grid-template-columns: 68px 1fr 48px;
  align-items: center;
  gap: 10px;
  padding: 5px 0;
}

.dim-name {
  color: #5e5e5e;
  font-size: 13px;
}

.dim-bar {
  height: 6px;
  border-radius: 3px;
  background: #f1f1f1;
  overflow: hidden;
}

.dim-bar i {
  display: block;
  height: 100%;
  border-radius: 3px;
  background: #9ab583;
}

.dim-score {
  color: #7d7d7d;
  font-size: 13px;
  text-align: right;
}

.dim-score.is-good {
  color: #4e6840;
  font-weight: 600;
}

.dim-score.is-low {
  color: #c2703f;
  font-weight: 600;
}

.block {
  margin-top: 22px;
  padding-top: 16px;
  border-top: 1px solid #f0f0f0;
}

.block h3 {
  margin: 0 0 10px;
  color: #292929;
  font-size: 15px;
  font-weight: 600;
}

.block h4 {
  margin: 14px 0 6px;
  color: #4e6840;
  font-size: 13px;
  font-weight: 600;
}

.text-list {
  margin: 0;
  padding-left: 18px;
  color: #5e5e5e;
  font-size: 13px;
  line-height: 1.85;
}

.text-list li + li {
  margin-top: 4px;
}

.detail + .detail {
  margin-top: 18px;
  padding-top: 14px;
  border-top: 1px dashed #f0f0f0;
}

.detail h4 {
  margin-top: 0;
  color: #292929;
  font-size: 14px;
}

.insight {
  padding: 10px 12px;
  border-radius: 8px;
  background: #f7f8f6;
}

.insight strong {
  display: block;
  margin-bottom: 4px;
  color: #4e6840;
  font-size: 12px;
  font-weight: 600;
}

.insight p {
  margin: 0;
  color: #5e5e5e;
  font-size: 12px;
  line-height: 1.8;
}

.insight p + p {
  margin-top: 4px;
}

.compare {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
  margin-top: 10px;
}

.compare-col {
  padding: 10px 12px;
  border-radius: 8px;
  font-size: 12px;
  line-height: 1.8;
}

.compare-col.is-before {
  background: #faf7f5;
  color: #8a7a70;
}

.compare-col.is-after {
  background: #f5f8f1;
  color: #4e5f42;
}

.col-tag {
  display: block;
  margin-bottom: 4px;
  font-size: 11px;
  font-weight: 600;
  opacity: 0.75;
}

.compare-col p,
.compare-single p {
  margin: 0;
  white-space: pre-wrap;
}

.compare-single {
  margin-top: 10px;
  padding: 10px 12px;
  border-radius: 8px;
  background: #f5f8f1;
  color: #4e5f42;
  font-size: 12px;
  line-height: 1.8;
}

.report-note {
  margin: 20px 0 0;
  color: #9a9a9a;
  font-size: 13px;
  text-align: center;
}

@media (max-width: 720px) {
  .compare {
    grid-template-columns: 1fr;
  }
}
</style>
