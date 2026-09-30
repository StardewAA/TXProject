<script lang="ts" setup>
import { IconifyIcon } from '@vben/icons';

/**
 * 「AI 生成简历方式」对话框。
 *
 * 在生成入口的模板面板点「确认选择」后弹出，让用户选一条路走：
 * 快速新建（AI 直接出一份初稿）或全面优化（上传/选一份简历，AI 改造它）。
 *
 * <p>**可以直接关掉不选** —— 关掉等同于没选方式，不调任何 AI、不改草稿，
 * 只是按原来的行为进「简历编辑」页。所以这个弹窗不拦路。</p>
 *
 * <p>用自绘遮罩而不是 antd Modal，与同目录的 resume-picker-dialog /
 * resume-preview-dialog 保持一套视觉与关闭语义（点遮罩关闭、role=dialog）。
 * 那个文件里记过不用 Modal 的具体原因：标题要「主标题 + 一行灰色说明」的两段式，
 * antd 的 title 塞不进第二段。这里虽然没有副标题，但三处弹窗混用两套遮罩色
 * 和两种 Esc 行为更难受。</p>
 */

defineProps<{
  open: boolean;
}>();

const emit = defineEmits<{
  cancel: [];
  /** 快速新建：AI 生成一份结构化初稿 */
  quick: [];
  /** 全面优化：上传或选一份简历，跑诊断+优化 */
  optimize: [];
}>();
</script>

<template>
  <div v-if="open" class="gen-mask" @click.self="emit('cancel')">
    <div class="gen" role="dialog" aria-modal="true" aria-labelledby="gen-title">
      <header class="gen-head">
        <h2 id="gen-title">AI 生成简历方式</h2>
        <button
          class="gen-close"
          type="button"
          aria-label="关闭"
          @click="emit('cancel')"
        >
          <IconifyIcon icon="lucide:x" />
        </button>
      </header>

      <div class="gen-body">
        <button class="mode-card" type="button" @click="emit('quick')">
          <span class="mode-icon is-quick">
            <IconifyIcon icon="lucide:sparkles" />
          </span>
          <span class="mode-text">
            <strong>快速新建</strong>
            <em>轻松获得一份为你定制的专业简历初稿。</em>
          </span>
        </button>

        <button class="mode-card" type="button" @click="emit('optimize')">
          <span class="mode-icon is-optimize">
            <IconifyIcon icon="lucide:wand-sparkles" />
          </span>
          <span class="mode-text">
            <strong>全面优化</strong>
            <em>上传你的简历，AI 助其快速升级，更受青睐。</em>
          </span>
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.gen-mask {
  position: fixed;
  z-index: 1200;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: rgb(0 0 0 / 45%);
}

.gen {
  width: min(560px, 100%);
  padding: 24px;
  border-radius: 12px;
  background: #fff;
}

.gen-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
}

.gen-head h2 {
  margin: 0;
  color: #292929;
  font-size: 18px;
  font-weight: 500;
  line-height: 26px;
}

.gen-close {
  display: flex;
  padding: 4px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: #999;
  font-size: 18px;
  cursor: pointer;
  transition: 0.2s ease-in-out;
}

.gen-close:hover {
  background: #f5f5f5;
  color: #333;
}

/* 两张卡片并排，窄屏自动堆叠 */
.gen-body {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.mode-card {
  display: flex;
  gap: 10px;
  padding: 14px;
  border: 1px solid #ececec;
  border-radius: 10px;
  background: #fff;
  text-align: left;
  cursor: pointer;
  transition: 0.2s ease-in-out;
}

.mode-card:hover {
  border-color: #cbd7bd;
  background: #fafcf8;
}

.mode-icon {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 8px;
  font-size: 16px;
}

.mode-icon.is-quick {
  background: #fdf3e7;
  color: #d9822b;
}

.mode-icon.is-optimize {
  background: #eef5e8;
  color: #4e6840;
}

.mode-text {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.mode-text strong {
  color: #292929;
  font-size: 15px;
  font-weight: 500;
  line-height: 22px;
}

.mode-text em {
  color: #8a8a8a;
  font-size: 12px;
  font-style: normal;
  line-height: 19px;
}

@media (max-width: 520px) {
  .gen-body {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
