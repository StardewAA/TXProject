<script lang="ts" setup>
import { computed } from 'vue';

import type { ResumeTemplate } from './resume-templates';

/**
 * 模板缩略图。
 *
 * 参考站那边是 136×193 的位图直链，这里改成按「主色 + 版式原型」现画：
 * 不引外站素材，配色换了图也跟着换，加模板不用再找图。
 *
 * 画的是示意图不是真渲染 —— 一页简历缩到 136px 宽，正文本来就只是一片灰条，
 * 能分辨的是版头形状和主色，所以只把这两样画准。
 */

const props = defineProps<{ template: ResumeTemplate }>();

const accent = computed(() => props.template.accent);
const kind = computed(() => props.template.archetype);

/** 正文里的灰色行，宽度错落一点才像文字 */
const LINES = ['100%', '88%', '72%'];
</script>

<template>
  <div class="thumb" :class="`is-${kind}`">
    <!-- 左侧竖栏 -->
    <div v-if="kind === 'sidebar'" class="side" :style="{ background: accent }">
      <span v-for="n in 5" :key="n" class="side-line" />
    </div>

    <div class="body">
      <!-- 顶部横带 -->
      <div v-if="kind === 'banner'" class="banner" :style="{ background: accent }">
        <span class="banner-name" />
      </div>

      <!-- 大字号姓名块（modern 与 banner 之外的常规版头） -->
      <div v-else class="head">
        <span class="name" :style="kind === 'modern' ? { background: accent } : {}" />
        <span class="contact" />
      </div>

      <template v-for="section in 3" :key="section">
        <span
          class="section-bar"
          :style="{ background: kind === 'tabular' ? '#dfe3e6' : accent }"
        />
        <div class="lines">
          <span
            v-for="(w, i) in LINES"
            :key="i"
            class="line"
            :style="{ width: w }"
          />
        </div>
      </template>
    </div>
  </div>
</template>

<style scoped>
/*
 * 宽高按 136:193 流式缩放，而不是写死 136×193。
 * 左栏宽度会变（3 列排布时每格只有 ~136px），写死就会撑破格子。
 */
.thumb {
  display: flex;
  width: 100%;
  aspect-ratio: 136 / 193;
  overflow: hidden;
  border-radius: 9px;
  background: #fff;
}

.body {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 7px;
  min-width: 0;
  padding: 11px 10px;
}

/* ── 版头 ── */
.head {
  display: flex;
  flex-direction: column;
  gap: 5px;
  padding-bottom: 7px;
  border-bottom: 1px solid #eef0ee;
}

.name {
  width: 44px;
  height: 8px;
  border-radius: 2px;
  background: #2f3a33;
}

/* modern：姓名是一整块主色，不是一根细条 */
.is-modern .name {
  width: 66px;
  height: 16px;
  border-radius: 3px;
}

.contact {
  width: 74px;
  height: 3px;
  border-radius: 2px;
  background: #dfe3e0;
}

/* ── 横带 ── */
.banner {
  display: flex;
  align-items: center;
  height: 34px;
  margin: -11px -10px 2px;
  padding: 0 10px;
}

.banner-name {
  width: 46px;
  height: 6px;
  border-radius: 2px;
  background: rgb(255 255 255 / 85%);
}

/* ── 左侧竖栏 ── */
.side {
  display: flex;
  flex-direction: column;
  gap: 6px;
  width: 30px;
  flex-shrink: 0;
  padding: 16px 7px;
}

.side-line {
  height: 3px;
  border-radius: 2px;
  background: rgb(255 255 255 / 55%);
}

/* ── 小节 ── */
.section-bar {
  width: 28px;
  height: 4px;
  border-radius: 2px;
}

.lines {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.line {
  height: 3px;
  border-radius: 2px;
  background: #e8eae8;
}

/* ── 各原型的微调 ── */

/* 极简：去掉分割线，留白更多 */
.is-minimal .head {
  border-bottom-color: transparent;
  padding-bottom: 3px;
}

.is-minimal .body {
  gap: 9px;
}

/* 表格：靠横线区分，而不是色块 */
.is-tabular .line {
  background: #eef0f1;
}

.is-tabular .head {
  border-bottom: 1.5px solid #d8dde1;
}

/* 商务/墨韵一类：姓名块底部压一条主色 */
.is-modern .head {
  border-bottom-width: 2px;
}
</style>
