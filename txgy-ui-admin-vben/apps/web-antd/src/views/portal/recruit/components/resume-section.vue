<script lang="ts" setup>
/**
 * 在线简历的一个分区外壳：图标 + 标题 + 说明 + 完成状态，下面是分区内容。
 *
 * 七个分区的排布一样，所以抽出来；各分区的字段/条目从插槽进。
 * 内容始终铺开显示 —— 之前试过「折叠成一行摘要、点击才展开」，页面上什么都看不到，
 * 太像空页，已改回常显。
 */
import { IconifyIcon } from '@vben/icons';

defineOptions({ name: 'ResumeSection' });

// 按仓库的 perfectionist 规则排成字母序；语义看注释
defineProps<{
  /** 标题下的说明文字 */
  description: string;
  /**
   * 是否已完善，七个分区都要传。
   *
   * 注意别写成「可选、不传就不渲染」：声明成 Boolean 的 prop 缺失时，
   * Vue 会按布尔转换补成 false 而不是 undefined，`done !== undefined` 永远成立，
   * 结果就是没传的分区照样渲染出一个「待完善」—— 与完成度分段条（七段都算）自相矛盾。
   */
  done: boolean;
  icon: string;
  /** 分区标识，用于拼 #resume-section-<id> 锚点，与目录、完成度分段条共用 */
  sectionId: string;
  title: string;
}>();
</script>

<template>
  <section :id="`resume-section-${sectionId}`" class="resume-section">
    <div class="section-title-row">
      <div class="section-title">
        <span class="section-icon"><IconifyIcon :icon="icon" /></span>
        <div class="section-title-copy">
          <h2>{{ title }}</h2>
          <small class="section-hint">{{ description }}</small>
        </div>
      </div>

      <div class="section-actions">
        <slot name="actions"></slot>
        <span class="section-status" :class="{ done }">
          {{ done ? '已完善' : '待完善' }}
        </span>
        <!-- 放在状态标签之后：给「个人信息」挂头像用，位置在标题行最右 -->
        <slot name="aside"></slot>
      </div>
    </div>

    <div class="section-body">
      <slot></slot>
    </div>
  </section>
</template>

<style scoped>
/* 配色沿用父页 .online-resume-page 上那份 token（自定义属性跨组件继承，不受 scoped 影响） */
.resume-section {
  scroll-margin-top: 76px;
  padding: 20px 22px 22px;
  border: 1px solid var(--job-line);
  border-radius: 14px;
  background: #fff;
  box-shadow: 0 4px 14px rgb(27 42 61 / 4%);
}

.section-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.section-title {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 12px;
}

.section-icon {
  display: grid;
  width: 38px;
  height: 38px;
  flex: 0 0 auto;
  place-items: center;
  border-radius: 11px;
  background: var(--job-blue-soft);
  color: var(--job-blue);
  font-size: 18px;
}

.section-title-copy {
  display: grid;
  min-width: 0;
  gap: 3px;
}

.section-title h2 {
  margin: 0;
  color: var(--job-ink);
  font-size: 20px;
  font-weight: 650;
  letter-spacing: -0.01em;
}

.section-hint {
  display: block;
  color: var(--job-muted);
  font-size: 13px;
}

.section-actions {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 8px;
}

.section-status {
  padding: 4px 9px;
  border-radius: 6px;
  background: var(--job-blue-soft);
  color: var(--job-blue);
  font-size: 12px;
  font-weight: 600;
  white-space: nowrap;
}

.section-status.done {
  background: var(--job-green-soft);
  color: var(--job-green);
}

/* 标题与内容之间拉一条分隔线，长页面里分区边界更清楚 */
.section-body {
  margin-top: 16px;
  padding-top: 18px;
  border-top: 1px solid var(--job-line);
}

@media (max-width: 760px) {
  .resume-section {
    padding: 16px 14px 18px;
  }

  .section-title-row {
    align-items: flex-start;
  }

  .section-actions {
    gap: 6px;
  }

  .section-title h2 {
    font-size: 18px;
  }
}
</style>
