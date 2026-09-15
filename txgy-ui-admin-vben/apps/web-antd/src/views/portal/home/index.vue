<script lang="ts" setup>
import { nextTick, ref } from 'vue';
import { useRouter } from 'vue-router';

import txgyLogo from '#/assets/imgs/txgy-logo.png';

type DispatchTarget = {
  description: string;
  label: string;
  path: string;
};

type DispatchMessage = {
  action?: DispatchTarget;
  role: 'assistant' | 'user';
  text: string;
};

const router = useRouter();
const input = ref('');
const messages = ref<DispatchMessage[]>([]);
const threadRef = ref<HTMLElement>();

const dispatchTargets: DispatchTarget[] = [
  {
    description: '浏览职位、查看企业并投递简历',
    label: '我要找工作',
    path: '/portal/recruit',
  },
  {
    description: '进入课程、考试和学习任务',
    label: '云培训学院',
    path: '/portal/training',
  },
  {
    description: '查看公告、项目和投标机会',
    label: '招标投标',
    path: '/portal/bidding',
  },
];

function findDispatchTarget(value: string) {
  if (/(职位|招聘|求职|简历|应聘|工作|企业)/u.test(value)) {
    return dispatchTargets[0];
  }
  if (/(培训|课程|考试|学习|证书)/u.test(value)) {
    return dispatchTargets[1];
  }
  if (/(招标|投标|项目|公告)/u.test(value)) {
    return dispatchTargets[2];
  }
  return undefined;
}

async function scrollThreadToBottom() {
  await nextTick();
  threadRef.value?.scrollTo({
    behavior: 'smooth',
    top: threadRef.value.scrollHeight,
  });
}

function submit() {
  const value = input.value.trim();
  if (!value) return;

  const action = findDispatchTarget(value);
  messages.value.push({ role: 'user', text: value });
  messages.value.push({
    action,
    role: 'assistant',
    text: action
      ? `我帮你找到「${action.label}」，这里可以${action.description}。`
      : '我可以帮你进入招聘、培训或招投标页面。换一种说法告诉我你的目标吧。',
  });
  input.value = '';
  void scrollThreadToBottom();
}

function handleInputKeydown(event: KeyboardEvent) {
  if (event.key === 'Enter' && !event.shiftKey) {
    event.preventDefault();
    submit();
  }
}

function openTarget(path: string) {
  void router.push(path);
}
</script>

<template>
  <div class="dispatch-page">
    <section
      class="dispatch-stage"
      :class="{ 'dispatch-stage--thread': messages.length }"
    >
      <div v-if="!messages.length" class="dispatch-welcome">
        <img :src="txgyLogo" alt="天信管业" class="dispatch-logo" />
        <h1>有什么可以帮忙的？</h1>
      </div>

      <div v-else ref="threadRef" class="dispatch-thread" aria-live="polite">
        <div class="thread-title">
          <span class="thread-avatar">分</span>
          <div>
            <strong>平台分流助手</strong>
            <span>帮你找到下一步</span>
          </div>
        </div>
        <div
          v-for="(message, index) in messages"
          :key="`${message.role}-${index}`"
          :class="['dispatch-message', `dispatch-message--${message.role}`]"
        >
          <div v-if="message.role === 'assistant'" class="message-avatar">
            分
          </div>
          <div class="message-body">
            <p>{{ message.text }}</p>
            <button
              v-if="message.action"
              class="message-action"
              type="button"
              @click="openTarget(message.action.path)"
            >
              {{ message.action.label }}
              <span>→</span>
            </button>
          </div>
        </div>
      </div>

      <div class="dispatch-composer">
        <span class="composer-leading attachment-control">
          <button
            aria-describedby="attachment-help"
            aria-label="添加图片或文档"
            class="composer-tool"
            type="button"
          >
            <svg aria-hidden="true" viewBox="0 0 24 24">
              <path
                d="m8.5 12.5 5.8-5.8a3 3 0 0 1 4.2 4.2l-7.7 7.7a5 5 0 0 1-7.1-7.1l8-8a3.5 3.5 0 0 1 5 5l-7.4 7.4a2 2 0 0 1-2.8-2.8l6.4-6.4"
              />
            </svg>
          </button>
          <span id="attachment-help" class="attachment-popover" role="tooltip">
            <strong>添加图片或文档</strong>
            <small>支持 PDF、DOCX、TXT、MD、XLSX、PPTX、JPG/JPEG、PNG</small>
          </span>
        </span>
        <textarea
          v-model="input"
          aria-label="告诉平台你想做什么"
          @keydown="handleInputKeydown"
          placeholder="有问题，随便问"
          rows="1"
        />
        <div class="composer-trailing">
          <button
            disabled
            aria-label="发送功能暂未开放"
            class="dispatch-composer-submit"
            title="发送功能暂未开放"
            type="submit"
          >
            ↑
          </button>
        </div>
      </div>

      <button
        v-if="messages.length"
        class="dispatch-reset"
        type="button"
        @click="messages = []"
      >
        开始新的分流
      </button>
    </section>
  </div>
</template>

<style scoped>
.dispatch-page {
  display: flex;
  width: 100%;
  height: calc(100vh - 64px);
  min-height: calc(100vh - 64px);
  overflow: hidden;
  padding: 0;
  background: #fff;
  flex-direction: column;
  align-items: center;
}

.dispatch-stage {
  position: relative;
  display: flex;
  width: 100%;
  height: 100%;
  min-height: calc(100vh - 64px);
  padding: 26px 0 24px;
  flex-direction: column;
  align-items: center;
}

.dispatch-stage--thread {
  display: grid;
  min-height: 610px;
  align-content: stretch;
  justify-items: stretch;
  padding: 34px 38px 24px;
  border: 1px solid #e8eceb;
  border-radius: 18px;
  background: #fff;
  box-shadow: 0 18px 50px rgb(28 67 62 / 7%);
}

.dispatch-welcome {
  position: absolute;
  top: 50%;
  left: 50%;
  width: 100%;
  height: 100%;
  display: grid;
  justify-items: center;
  pointer-events: none;
  text-align: center;
  transform: translate(-50%, -50%);
}

.dispatch-logo {
  position: absolute;
  top: calc(50% - 153px);
  left: 50%;
  width: 82px;
  height: 82px;
  object-fit: contain;
  transform: translateX(-50%);
}

.dispatch-welcome h1 {
  position: absolute;
  top: calc(50% - 32px);
  left: 50%;
  margin: 0;
  color: #111827;
  font-size: clamp(24px, 3vw, 32px);
  font-weight: 700;
  letter-spacing: -0.03em;
  line-height: 1.2;
  text-align: center;
  transform: translate(-50%, -50%);
  white-space: nowrap;
}

.dispatch-composer {
  display: flex;
  width: min(640px, calc(100% - 32px));
  min-height: 56px;
  align-items: center;
  gap: 6px;
  margin-top: auto;
  padding: 7px 8px 7px 10px;
  border: 1px solid #e2e6e9;
  border-radius: 30px;
  background: #fff;
  box-shadow: 0 5px 18px rgb(25 35 45 / 7%);
}

.dispatch-composer textarea {
  min-height: 42px;
  flex: 1;
  padding: 10px 4px;
  border: 0;
  outline: 0;
  background: transparent;
  color: #17343d;
  font: inherit;
  line-height: 1.45;
  resize: none;
}

.dispatch-composer textarea::placeholder {
  color: #b1bfbc;
}

.composer-trailing {
  display: flex;
  align-items: center;
  gap: 4px;
}

.composer-tool {
  display: grid;
  height: 38px;
  place-items: center;
  border: 0;
  background: transparent;
  color: #71817f;
  cursor: pointer;
}

.composer-tool {
  width: 38px;
  border-radius: 50%;
  font-size: 24px;
  font-weight: 300;
  line-height: 1;
}

.composer-tool:hover,
.composer-tool:focus-visible {
  background: #f1f5f4;
  color: #17343d;
}

.composer-leading {
  display: flex;
  flex: 0 0 auto;
}

.composer-tool svg {
  width: 20px;
  height: 20px;
  fill: none;
  stroke: currentcolor;
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 1.8;
}

.attachment-control {
  position: relative;
}

.attachment-popover {
  position: absolute;
  bottom: calc(100% + 12px);
  left: -8px;
  z-index: 2;
  display: grid;
  width: 238px;
  gap: 5px;
  padding: 10px 12px;
  border: 1px solid #e1e8e6;
  border-radius: 10px;
  background: #fff;
  box-shadow: 0 8px 24px rgb(23 52 61 / 14%);
  opacity: 0;
  pointer-events: none;
  transform: translateY(4px);
  transition:
    opacity 140ms ease,
    transform 140ms ease;
}

.attachment-control:hover .attachment-popover,
.attachment-control:focus-within .attachment-popover {
  opacity: 1;
  transform: translateY(0);
}

.attachment-popover strong {
  color: #17343d;
  font-size: 13px;
  font-weight: 700;
}

.attachment-popover small {
  color: #71817f;
  font-size: 11px;
  line-height: 1.5;
}

.dispatch-composer-submit {
  display: grid;
  width: 38px;
  min-width: 38px;
  height: 38px;
  place-items: center;
  border: 0;
  border-radius: 50%;
  background: #f27b32;
  color: #fff;
  cursor: pointer;
  font-size: 22px;
  font-weight: 500;
  line-height: 1;
  transition:
    background 160ms ease,
    transform 160ms ease;
}

.dispatch-composer-submit:hover:not(:disabled) {
  background: #df6421;
  transform: translateY(-1px);
}

.dispatch-composer-submit:disabled {
  background: #e3e7e9;
  color: #a5b0b4;
  cursor: not-allowed;
}

.dispatch-stage--thread .dispatch-composer {
  margin-top: 28px;
}

.dispatch-thread {
  min-height: 0;
  overflow: auto;
  padding: 4px 8px 28px;
}

.thread-title {
  display: flex;
  align-items: center;
  gap: 11px;
  padding-bottom: 24px;
  border-bottom: 1px solid #edf1ef;
}

.thread-avatar,
.message-avatar {
  display: grid;
  place-items: center;
  border-radius: 10px;
  background: #173f46;
  color: #a8e2d6;
  font-weight: 800;
}

.thread-avatar {
  width: 38px;
  height: 38px;
}

.thread-title strong,
.thread-title span {
  display: block;
}

.thread-title strong {
  color: #17343d;
  font-size: 15px;
}

.thread-title span {
  margin-top: 3px;
  color: #91a19d;
  font-size: 12px;
}

.dispatch-message {
  display: flex;
  max-width: 82%;
  gap: 10px;
  margin-top: 28px;
}

.dispatch-message--user {
  margin-left: auto;
  justify-content: flex-end;
}

.message-avatar {
  width: 30px;
  height: 30px;
  flex: 0 0 30px;
  border-radius: 9px;
  font-size: 13px;
}

.message-body {
  padding: 13px 16px;
  border-radius: 14px;
  background: #f1f7f5;
  color: #36504e;
  line-height: 1.65;
}

.dispatch-message--user .message-body {
  background: #173f46;
  color: #fff;
}

.message-body p {
  margin: 0;
}

.message-action {
  display: inline-flex;
  align-items: center;
  gap: 12px;
  margin-top: 13px;
  padding: 9px 12px;
  border: 1px solid #b5ddd4;
  border-radius: 8px;
  background: #fff;
  color: #16756f;
  cursor: pointer;
  font-weight: 700;
}

.message-action:hover {
  border-color: #16756f;
}

.dispatch-reset {
  justify-self: center;
  margin-top: 10px;
  border: 0;
  background: transparent;
  color: #16756f;
  cursor: pointer;
  font-size: 12px;
}

@media (max-width: 700px) {
  .dispatch-page {
    height: calc(100vh - 156px);
    min-height: calc(100vh - 156px);
    padding: 14px 12px 38px;
  }

  .dispatch-stage,
  .dispatch-stage--thread {
    min-height: 520px;
    padding: 42px 18px 22px;
    border-radius: 18px;
  }

  .dispatch-logo {
    top: calc(50% - 138px);
    width: 72px;
    height: 72px;
  }

  .dispatch-composer {
    padding-left: 14px;
  }

  .dispatch-message {
    max-width: 94%;
  }
}

@media (prefers-reduced-motion: reduce) {
  .dispatch-composer-submit,
  .attachment-popover {
    transition: none;
  }
}
</style>
