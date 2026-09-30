<script lang="ts" setup>
import type {
  LawCitation,
  LawConversationHistoryItem,
  LawSearchScope,
} from '#/api/cms/law';

import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue';

import { answerLaw, downloadLawOriginal } from '#/api/cms/law';
import type { AiModelOption } from '#/api/cms/portal';
import { chatWithModel, getAiModels } from '#/api/cms/portal';
import { useAiAssistant } from './use-ai-assistant';

interface ChatMessage {
  citations: LawCitation[];
  content: string;
  generated: boolean;
  id: number;
  loading: boolean;
  mode?: 'kb' | 'llm';
  model?: string;
  question?: string;
  role: 'bot' | 'me';
  scopeLabel?: string;
  warning?: string;
}

const { visible } = useAiAssistant();

const PET_SIZE = 96;
const EDGE = 16;
const POS_KEY = 'txgy-ai-pet-position';
const THINKING_TEXTS = [
  '正在翻法律法规库…',
  '正在核对金融政策原文…',
  '正在整理引用出处…',
  '马上就好…',
];

const input = ref('');
const sending = ref(false);
const scope = ref<LawSearchScope>('ALL');
const messages = ref<ChatMessage[]>([]);

/** 用户是否已提问：提问后隐藏推荐问题 */
const hasAsked = computed(() => messages.value.some((item) => item.role === 'me'));
const messageBox = ref<HTMLElement>();
const petElement = ref<HTMLElement>();
const petPosition = ref({ x: 0, y: 0 });
const panelStyle = ref<Record<string, string>>({});
const dragging = ref(false);
const listening = ref(false);
const voiceMessage = ref('');
const newlyMounted = ref(true);
const thinkingIndex = ref(0);
let sequence = 0;
let recognition: any = null;
let dragMoved = false;
let thinkingTimer: number | undefined;
let introTimer: number | undefined;
const dragState = { offsetX: 0, offsetY: 0, pointerId: -1 };

const scopeOptions: { label: string; value: LawSearchScope }[] = [
  { label: '全部知识库', value: 'ALL' },
  { label: '法律法规', value: 'LAW' },
  { label: '金融政策', value: 'FINANCE_POLICY' },
];

const suggestions = [
  '专精特新中小企业有哪些金融支持政策？',
  '设备更新贷款贴息的补贴标准是多少？',
  '防腐保温工程施工需要遵守哪些国家标准？',
  '知识产权质押贷款有哪些政策支持？',
];

const scopeName = computed(
  () => scopeOptions.find((item) => item.value === scope.value)?.label ?? '全部知识库',
);

const canSend = computed(() => input.value.trim().length > 0 && !sending.value);
const speechSupported = ref(false);
const mode = ref<'kb' | 'llm'>('kb');
const aiModels = ref<AiModelOption[]>([]);
const selectedModelId = ref<number>();
const thinking = computed(() => sending.value);
const thinkingText = computed(() => THINKING_TEXTS[thinkingIndex.value % THINKING_TEXTS.length]);
const bubbleText = computed(() => {
  if (thinking.value) {
    return thinkingText.value;
  }
  if (newlyMounted.value) {
    return '我是小天，欢迎咨询';
  }
  return visible.value ? '收起对话' : '我是小天，欢迎咨询';
});

function scopeLabel(resolved?: string) {
  switch (resolved) {
    case 'ALL': {
      return '综合检索';
    }
    case 'FINANCE_POLICY': {
      return '金融政策库';
    }
    case 'LAW': {
      return '法律法规库';
    }
    case 'QA': {
      return '问答库';
    }
    default: {
      return '知识库';
    }
  }
}

function pushMessage(partial: Partial<ChatMessage> & { role: 'bot' | 'me' }) {
  sequence += 1;
  const message: ChatMessage = {
    citations: [],
    content: '',
    generated: false,
    id: sequence,
    loading: false,
    ...partial,
  };
  messages.value.push(message);
  return message.id;
}

function scrollToBottom() {
  void nextTick(() => {
    if (messageBox.value) {
      messageBox.value.scrollTop = messageBox.value.scrollHeight;
    }
  });
}

function buildHistory(): LawConversationHistoryItem[] {
  const history: LawConversationHistoryItem[] = [];
  let lastQuestion = '';
  for (const message of messages.value) {
    if (message.role === 'me') {
      lastQuestion = message.content;
    } else if (lastQuestion && !message.loading) {
      history.push({ answer: message.content.slice(0, 4000), question: lastQuestion });
      lastQuestion = '';
    }
  }
  return history.slice(-3);
}

function startThinkingAnimation() {
  thinkingIndex.value = 0;
  stopThinkingAnimation();
  thinkingTimer = window.setInterval(() => {
    thinkingIndex.value = (thinkingIndex.value + 1) % THINKING_TEXTS.length;
  }, 1500);
}

function stopThinkingAnimation() {
  if (thinkingTimer) {
    window.clearInterval(thinkingTimer);
    thinkingTimer = undefined;
  }
}

async function send(preset?: string) {
  const question = (preset ?? input.value).trim();
  if (!question || sending.value) {
    return;
  }
  input.value = '';
  sending.value = true;
  newlyMounted.value = false;
  startThinkingAnimation();
  pushMessage({ content: question, role: 'me' });
  const pendingId = pushMessage({ content: '', loading: true, question, role: 'bot' });
  scrollToBottom();
  try {
    const target = messages.value.find((message) => message.id === pendingId);
    if (mode.value === 'llm') {
      const response = await chatWithModel(question, selectedModelId.value, buildHistory());
      if (target) {
        target.loading = false;
        target.mode = 'llm';
        target.content = response.answer || '大模型暂时没有返回内容，请稍后重试。';
        target.model = response.model;
        target.scopeLabel = '大模型直答（未引用知识库）';
      }
    } else {
      const response = await answerLaw(question, 5, true, buildHistory(), scope.value);
      if (target) {
        target.loading = false;
        target.mode = 'kb';
        target.content = response.answer || '知识库中暂无相关依据，请换个问法或缩小范围。';
        target.citations = response.citations ?? [];
        target.generated = Boolean(response.generated);
        target.model = response.model;
        target.warning = response.warning;
        target.scopeLabel = scopeLabel(response.resolvedSearchScope);
      }
    }
  } catch (error) {
    const target = messages.value.find((message) => message.id === pendingId);
    if (target) {
      target.loading = false;
      target.content = '知识库暂时不可用，请稍后重试。';
      target.warning = error instanceof Error ? error.message : String(error);
    }
  } finally {
    sending.value = false;
    stopThinkingAnimation();
    scrollToBottom();
  }
}

/** 知识库没有依据时，一键切换到大模型直答。 */
async function askByModel(question?: string) {
  if (!question) {
    return;
  }
  mode.value = 'llm';
  await send(question);
}

async function loadAiModels() {
  try {
    aiModels.value = await getAiModels();
    selectedModelId.value = aiModels.value[0]?.id;
  } catch {
    // 模型列表加载失败时保持知识库模式
  }
}

async function openCitation(citation: LawCitation) {
  if (!citation.reviewId) {
    return;
  }
  try {
    const file = await downloadLawOriginal(citation.reviewId, true);
    const url = URL.createObjectURL(file);
    const target = citation.pageNumber ? `${url}#page=${citation.pageNumber}` : url;
    window.open(target, '_blank', 'noopener');
    window.setTimeout(() => URL.revokeObjectURL(url), 60_000);
  } catch {
    window.alert('本地原件读取失败，请稍后重试。');
  }
}

/* ============ 宠物位置与拖拽 ============ */

function clampPosition(x: number, y: number) {
  const maxX = Math.max(EDGE, window.innerWidth - PET_SIZE - EDGE);
  const maxY = Math.max(EDGE, window.innerHeight - PET_SIZE - EDGE);
  return {
    x: Math.min(Math.max(EDGE, x), maxX),
    y: Math.min(Math.max(EDGE, y), maxY),
  };
}

function applyPosition(next: { x: number; y: number }) {
  petPosition.value = clampPosition(next.x, next.y);
  if (petElement.value) {
    petElement.value.style.left = `${petPosition.value.x}px`;
    petElement.value.style.top = `${petPosition.value.y}px`;
  }
  updatePanelStyle();
}

function defaultPosition() {
  return {
    x: window.innerWidth - PET_SIZE - 28,
    y: window.innerHeight - PET_SIZE - 36,
  };
}

function restorePosition() {
  let restored = defaultPosition();
  try {
    const raw = window.localStorage.getItem(POS_KEY);
    if (raw) {
      const parsed = JSON.parse(raw) as { x: number; y: number };
      if (Number.isFinite(parsed?.x) && Number.isFinite(parsed?.y)) {
        restored = { x: parsed.x, y: parsed.y };
      }
    }
  } catch {
    // 忽略损坏的本地位置
  }
  applyPosition(restored);
}

function persistPosition() {
  try {
    window.localStorage.setItem(POS_KEY, JSON.stringify(petPosition.value));
  } catch {
    // 忽略存储失败
  }
}

function onPointerDown(event: PointerEvent) {
  if (event.button !== 0) {
    return;
  }
  const target = event.currentTarget as HTMLElement;
  const rect = target.getBoundingClientRect();
  dragMoved = false;
  dragging.value = true;
  dragState.offsetX = event.clientX - rect.left;
  dragState.offsetY = event.clientY - rect.top;
  dragState.pointerId = event.pointerId;
  target.setPointerCapture?.(event.pointerId);
}

function onPointerMove(event: PointerEvent) {
  if (!dragging.value || event.pointerId !== dragState.pointerId) {
    return;
  }
  const nextX = event.clientX - dragState.offsetX;
  const nextY = event.clientY - dragState.offsetY;
  if (
    Math.abs(nextX - petPosition.value.x) > 3 ||
    Math.abs(nextY - petPosition.value.y) > 3
  ) {
    dragMoved = true;
  }
  applyPosition({ x: nextX, y: nextY });
}

function onPointerUp(event: PointerEvent) {
  if (!dragging.value) {
    return;
  }
  dragging.value = false;
  (event.currentTarget as HTMLElement).releasePointerCapture?.(event.pointerId);
  if (dragMoved) {
    persistPosition();
  } else {
    togglePanel();
  }
}

function onWindowResize() {
  applyPosition(petPosition.value);
}

function updatePanelStyle() {
  const panelWidth = Math.min(400, window.innerWidth - 2 * EDGE);
  const panelHeight = Math.min(560, Math.max(320, window.innerHeight - 2 * EDGE - 24));
  let left = petPosition.value.x + PET_SIZE + 12;
  if (left + panelWidth > window.innerWidth - EDGE) {
    left = petPosition.value.x - panelWidth - 12;
  }
  left = Math.min(Math.max(EDGE, left), Math.max(EDGE, window.innerWidth - panelWidth - EDGE));
  let top = petPosition.value.y + PET_SIZE - panelHeight;
  top = Math.min(Math.max(EDGE, top), Math.max(EDGE, window.innerHeight - panelHeight - EDGE));
  panelStyle.value = {
    height: `${panelHeight}px`,
    left: `${left}px`,
    top: `${top}px`,
    width: `${panelWidth}px`,
  };
}

function togglePanel() {
  visible.value = !visible.value;
  newlyMounted.value = false;
  if (visible.value && messages.value.length === 0) {
    pushMessage({
      content:
        '我是小天，很高兴为您服务～ 我会依据知识库中的法律法规与金融政策原文回答问题，并标注引用出处。您可以直接输入、点选推荐问题，或点麦克风用语音提问。',
      role: 'bot',
    });
  }
  if (visible.value) {
    updatePanelStyle();
    scrollToBottom();
  }
}

function closePanel() {
  visible.value = false;
}

/* ============ 语音输入 ============ */

function setVoiceMessage(text: string) {
  voiceMessage.value = text;
  if (text) {
    window.setTimeout(() => {
      if (voiceMessage.value === text) {
        voiceMessage.value = '';
      }
    }, 4000);
  }
}

function startListening() {
  if (!speechSupported.value || !recognition) {
    setVoiceMessage('当前浏览器不支持语音输入，请改用文字提问。');
    return;
  }
  if (listening.value) {
    recognition.stop();
    return;
  }
  voiceMessage.value = '';
  try {
    recognition.start();
    listening.value = true;
  } catch {
    listening.value = false;
    setVoiceMessage('麦克风启动失败，请检查浏览器权限。');
  }
}

function setupSpeechRecognition() {
  const globalWindow = window as any;
  const SpeechRecognitionCtor =
    globalWindow.SpeechRecognition ?? globalWindow.webkitSpeechRecognition;
  if (!SpeechRecognitionCtor) {
    speechSupported.value = false;
    return;
  }
  speechSupported.value = true;
  recognition = new SpeechRecognitionCtor();
  recognition.lang = 'zh-CN';
  recognition.interimResults = true;
  recognition.continuous = false;
  recognition.onresult = (event: any) => {
    let transcript = '';
    let isFinal = false;
    for (let index = event.resultIndex; index < event.results.length; index += 1) {
      transcript += event.results[index][0].transcript;
      if (event.results[index].isFinal) {
        isFinal = true;
      }
    }
    input.value = transcript.trim();
    if (isFinal && input.value) {
      void send();
    }
  };
  recognition.onerror = (event: any) => {
    listening.value = false;
    const map: Record<string, string> = {
      'audio-capture': '未检测到麦克风，请检查设备。',
      'not-allowed': '麦克风权限被拒绝，请在浏览器中允许后重试。',
      'no-speech': '没有听到声音，请再说一次。',
      'service-not-allowed': '浏览器语音服务不可用。',
    };
    setVoiceMessage(map[event?.error] ?? '语音识别失败，请改用文字提问。');
  };
  recognition.onend = () => {
    listening.value = false;
  };
}

onMounted(() => {
  restorePosition();
  updatePanelStyle();
  setupSpeechRecognition();
  void loadAiModels();
  window.addEventListener('resize', onWindowResize);
  introTimer = window.setTimeout(() => {
    newlyMounted.value = false;
  }, 6000);
});

onUnmounted(() => {
  window.removeEventListener('resize', onWindowResize);
  stopThinkingAnimation();
  if (introTimer) {
    window.clearTimeout(introTimer);
  }
  try {
    recognition?.stop?.();
  } catch {
    // 忽略停止异常
  }
});
</script>

<template>
  <div>
    <!-- 小天：可拖动的小宠物，思考时会坐下抱着电脑 -->
    <button
      ref="petElement"
      :class="[
        'ai-pet',
        { 'ai-pet-dragging': dragging, 'ai-pet-listening': listening, 'ai-pet-thinking': thinking },
      ]"
      aria-label="AI 政策助手 小天"
      type="button"
      @pointercancel="onPointerUp"
      @pointerdown="onPointerDown"
      @pointermove="onPointerMove"
      @pointerup="onPointerUp"
    >
      <!-- 悬停 / 思考气泡 -->
      <span :class="['ai-pet-bubble', { 'ai-pet-bubble-show': newlyMounted || thinking }]">
        <template v-if="thinking">
          <span class="dot"></span><span class="dot"></span><span class="dot"></span>
          {{ thinkingText }}
        </template>
        <template v-else>我是小天<br />欢迎咨询</template>
      </span>

      <span class="ai-pet-online"></span>

      <!-- 平时：站立的可爱小天 -->
      <svg v-if="!thinking" class="ai-pet-svg" viewBox="0 0 140 140">
        <defs>
          <radialGradient id="fishBodyGrad" cx="36%" cy="30%" r="82%">
            <stop offset="0" stop-color="#FFB067" />
            <stop offset="45%" stop-color="#F4653A" />
            <stop offset="100%" stop-color="#CF2A20" />
          </radialGradient>
          <linearGradient id="fishFinGrad" x1="0" x2="1" y1="0" y2="1">
            <stop offset="0" stop-color="#FFC98F" stop-opacity="0.95" />
            <stop offset="100%" stop-color="#F1743C" stop-opacity="0.9" />
          </linearGradient>
          <linearGradient id="fishBadgeGrad" x1="0" x2="1" y1="0" y2="1">
            <stop offset="0" stop-color="#E8412F" />
            <stop offset="1" stop-color="#B71C13" />
          </linearGradient>
        </defs>

        <ellipse cx="70" cy="128" fill="#0A2540" opacity="0.16" rx="26" ry="5" />

        <!-- 尾鳍 -->
        <path
          class="fish-tail"
          d="M40 74C24 58 12 50 4 50c-1 18 6 36 22 48 6 4 12 2 14-6Z"
          fill="url(#fishFinGrad)"
        />
        <!-- 背鳍 -->
        <path
          d="M60 44c8-16 20-24 32-20 6 2 6 10 0 14-8 6-16 12-22 20Z"
          fill="url(#fishFinGrad)"
        />
        <!-- 身体 -->
        <ellipse cx="76" cy="76" fill="url(#fishBodyGrad)" rx="42" ry="36" />
        <!-- 肚皮 -->
        <ellipse cx="74" cy="94" fill="#FFE3C8" opacity="0.92" rx="26" ry="16" />
        <!-- 胸鳍（小手） -->
        <ellipse
          class="fish-fin-left"
          cx="40"
          cy="88"
          fill="url(#fishFinGrad)"
          rx="12"
          ry="7"
          transform="rotate(-18 40 88)"
        />
        <ellipse
          class="fish-fin-right"
          cx="112"
          cy="88"
          fill="url(#fishFinGrad)"
          rx="12"
          ry="7"
          transform="rotate(18 112 88)"
        />
        <!-- 胸牌：天信管业商标 -->
        <g transform="translate(74 98)">
          <path d="M0-15 13 7.5H-13Z" fill="url(#fishBadgeGrad)" />
          <circle cx="0" cy="0" fill="none" r="5.4" stroke="#fff" stroke-width="2.1" />
          <path d="M0-7 3-4H-3Z" fill="#fff" />
          <path d="M6.6-1.6 5.4 1.8 2.2-.3Z" fill="#fff" />
          <path d="M-6.6-1.6 -2.2-.3 -5.4 1.8Z" fill="#fff" />
        </g>
        <!-- 眼睛 -->
        <g class="ai-pet-eyes">
          <ellipse cx="60" cy="66" fill="#2B1A16" rx="8" ry="9" />
          <ellipse cx="92" cy="66" fill="#2B1A16" rx="8" ry="9" />
          <circle cx="63" cy="62" fill="#fff" r="3" />
          <circle cx="95" cy="62" fill="#fff" r="3" />
          <circle cx="57.4" cy="70" fill="#fff" opacity="0.6" r="1.4" />
          <circle cx="89.4" cy="70" fill="#fff" opacity="0.6" r="1.4" />
        </g>
        <!-- 腮红 + 嘴 -->
        <ellipse cx="46" cy="82" fill="#FF9E8A" opacity="0.7" rx="7" ry="4.6" />
        <ellipse cx="104" cy="82" fill="#FF9E8A" opacity="0.7" rx="7" ry="4.6" />
        <path
          d="M68 82c3 4.4 9 4.4 12 0"
          fill="none"
          stroke="#8C2318"
          stroke-linecap="round"
          stroke-width="2.6"
        />
        <!-- 气泡 -->
        <g class="fish-bubbles" fill="#fff" opacity="0.75">
          <circle cx="126" cy="52" r="5" />
          <circle cx="134" cy="36" r="3.4" />
          <circle cx="120" cy="32" r="2.4" />
        </g>
      </svg>

      <!-- 思考中：坐着抱电脑 -->
      <svg v-else class="ai-pet-svg ai-pet-svg-thinking" viewBox="0 0 140 140">
        <defs>
          <radialGradient id="fishBodyGrad2" cx="36%" cy="30%" r="82%">
            <stop offset="0" stop-color="#FFB067" />
            <stop offset="45%" stop-color="#F4653A" />
            <stop offset="100%" stop-color="#CF2A20" />
          </radialGradient>
          <linearGradient id="fishFinGrad2" x1="0" x2="1" y1="0" y2="1">
            <stop offset="0" stop-color="#FFC98F" stop-opacity="0.95" />
            <stop offset="100%" stop-color="#F1743C" stop-opacity="0.9" />
          </linearGradient>
          <linearGradient id="fishScreenGrad" x1="0" x2="1" y1="0" y2="1">
            <stop offset="0" stop-color="#9FE3FF" />
            <stop offset="100%" stop-color="#3C8FE0" />
          </linearGradient>
        </defs>

        <ellipse cx="70" cy="128" fill="#0A2540" opacity="0.16" rx="26" ry="5" />

        <!-- 尾鳍 -->
        <path
          class="fish-tail"
          d="M40 66C24 50 12 42 4 42c-1 18 6 36 22 48 6 4 12 2 14-6Z"
          fill="url(#fishFinGrad2)"
        />
        <!-- 背鳍 -->
        <path
          d="M60 38c8-16 20-24 32-20 6 2 6 10 0 14-8 6-16 12-22 20Z"
          fill="url(#fishFinGrad2)"
        />
        <!-- 身体 -->
        <ellipse cx="76" cy="70" fill="url(#fishBodyGrad2)" rx="42" ry="36" />
        <!-- 眼睛（看向电脑） -->
        <g class="ai-pet-eyes">
          <ellipse cx="60" cy="58" fill="#2B1A16" rx="8" ry="8.4" />
          <ellipse cx="92" cy="58" fill="#2B1A16" rx="8" ry="8.4" />
          <circle cx="60" cy="61" fill="#fff" r="2.8" />
          <circle cx="92" cy="61" fill="#fff" r="2.8" />
        </g>
        <ellipse cx="46" cy="74" fill="#FF9E8A" opacity="0.7" rx="7" ry="4.6" />
        <ellipse cx="104" cy="74" fill="#FF9E8A" opacity="0.7" rx="7" ry="4.6" />
        <!-- 笔记本电脑 -->
        <g class="ai-pet-laptop">
          <rect x="42" y="96" fill="#2C3444" height="8" rx="3" width="66" />
          <rect x="47" y="78" fill="#3A4356" height="22" rx="4" width="56" />
          <rect x="50" y="81" fill="url(#fishScreenGrad)" height="16" rx="2.5" width="50" />
          <rect x="54" y="85" fill="#fff" height="2" opacity="0.85" rx="1" width="24" />
          <rect x="54" y="89" fill="#fff" height="2" opacity="0.6" rx="1" width="34" />
          <rect x="54" y="93" fill="#fff" height="2" opacity="0.45" rx="1" width="16" />
        </g>
        <!-- 鳍扶着电脑 -->
        <ellipse
          class="fish-fin-left"
          cx="40"
          cy="96"
          fill="url(#fishFinGrad2)"
          rx="12"
          ry="7.5"
          transform="rotate(-14 40 96)"
        />
        <ellipse
          class="fish-fin-right"
          cx="112"
          cy="96"
          fill="url(#fishFinGrad2)"
          rx="12"
          ry="7.5"
          transform="rotate(14 112 96)"
        />
      </svg>
    </button>

    <div v-if="visible" class="ai-mask" @click="closePanel"></div>

    <section v-if="visible" :style="panelStyle" class="ai-panel">
      <header class="ai-panel-head">
        <div class="ai-panel-user">
          <span class="ai-panel-avatar">
            <svg viewBox="0 0 140 140">
              <defs>
                <linearGradient id="fishMiniFin" x1="0" x2="1" y1="0" y2="1">
                  <stop offset="0" stop-color="#FFC98F" />
                  <stop offset="1" stop-color="#F1743C" />
                </linearGradient>
              </defs>
              <path d="M30 70C18 56 10 50 4 50c-1 14 5 28 17 38 5 3 9 1 11-5Z" fill="url(#fishMiniFin)" />
              <ellipse cx="74" cy="72" fill="#E8452F" rx="42" ry="36" />
              <ellipse cx="72" cy="90" fill="#FFE3C8" rx="25" ry="15" />
              <ellipse cx="58" cy="62" fill="#2B1A16" rx="8" ry="9" />
              <ellipse cx="90" cy="62" fill="#2B1A16" rx="8" ry="9" />
              <circle cx="61" cy="58" fill="#fff" r="3" />
              <circle cx="93" cy="58" fill="#fff" r="3" />
              <path d="M66 78c3 4.2 9 4.2 12 0" fill="none" stroke="#8C2318" stroke-linecap="round" stroke-width="2.4" />
              <path d="M72 92 84 112H60Z" fill="#C4231A" />
              <circle cx="72" cy="104" fill="none" r="5" stroke="#fff" stroke-width="2" />
            </svg>
          </span>
          <div>
            <div class="ai-panel-title">小天 · AI 政策助手</div>
            <div class="ai-panel-status">
              <span v-if="thinking">● {{ thinkingText }}</span>
              <span v-else>● 在线 · {{ scopeName }}</span>
            </div>
          </div>
        </div>
        <div class="ai-panel-tools">
          <select v-model="scope" class="ai-scope">
            <option v-for="option in scopeOptions" :key="option.value" :value="option.value">
              {{ option.label }}
            </option>
          </select>
          <button aria-label="关闭" class="ai-panel-close" type="button" @click="closePanel">×</button>
        </div>
      </header>


      <div ref="messageBox" class="ai-msgs">
        <div
          v-for="item in messages"
          :key="item.id"
          :class="['ai-msg', item.role === 'me' ? 'me' : 'bot']"
        >
          <template v-if="item.loading">
            <span class="ai-typing">
              <span class="ai-think-dot"></span><span class="ai-think-dot"></span
              ><span class="ai-think-dot"></span>
              {{ thinkingText }}
            </span>
          </template>
          <template v-else>
            <div class="ai-msg-text">{{ item.content }}</div>
            <div v-if="item.citations.length" class="ai-cites">
              <button
                v-for="citation in item.citations"
                :key="citation.sourceId + '-' + citation.index"
                class="ai-cite"
                type="button"
                @click="openCitation(citation)"
              >
                <span class="ai-cite-no">[{{ citation.index }}]</span>
                <span class="ai-cite-title">{{ citation.title }}</span>
                <span v-if="citation.articleNo" class="ai-cite-meta">{{ citation.articleNo }}</span>
                <span v-if="citation.pageNumber" class="ai-cite-meta">P{{ citation.pageNumber }}</span>
              </button>
            </div>
            <button
              v-if="item.role === 'bot' && item.mode !== 'llm' && item.citations.length === 0"
              class="ai-fallback"
              type="button"
              @click="askByModel(item.question)"
            >
              知识库没找到依据？让大模型直接回答 →
            </button>
            <div class="ai-msg-foot">
              <span v-if="item.scopeLabel">{{ item.scopeLabel }}</span>
              <span v-if="item.model">· {{ item.model }}</span>
              <span v-if="item.warning" class="ai-warn">· {{ item.warning }}</span>
            </div>
          </template>
        </div>
      </div>

      <div v-if="!hasAsked" class="ai-suggest">
        <button
          v-for="question in suggestions"
          :key="question"
          type="button"
          @click="send(question)"
        >
          {{ question }}
        </button>
      </div>

      <div v-if="voiceMessage || listening" class="ai-voice-tip">
        {{ listening ? '正在聆听，请说出您的问题…' : voiceMessage }}
      </div>

      <div class="ai-input">
        <button
          :class="['ai-mic', { 'ai-mic-on': listening, 'ai-mic-off': !speechSupported }]"
          :title="speechSupported ? '语音输入' : '当前浏览器不支持语音输入'"
          aria-label="语音输入"
          type="button"
          @click="startListening"
        >
          <svg
            fill="none"
            stroke="currentColor"
            stroke-linecap="round"
            stroke-linejoin="round"
            stroke-width="2"
            viewBox="0 0 24 24"
          >
            <path d="M12 3a3 3 0 0 1 3 3v6a3 3 0 0 1-6 0V6a3 3 0 0 1 3-3Z" />
            <path d="M5 11a7 7 0 0 0 14 0" />
            <path d="M12 18v3" />
          </svg>
        </button>
        <input
          v-model="input"
          placeholder="请输入您的问题，或点击麦克风说话"
          type="text"
          @keydown.enter.prevent="send()"
        />
        <button :disabled="!canSend" aria-label="发送" type="button" @click="send()">
          <svg
            fill="none"
            stroke="currentColor"
            stroke-linecap="round"
            stroke-linejoin="round"
            stroke-width="2"
            viewBox="0 0 24 24"
          >
            <path d="m22 2-7 20-4-9-9-4Z" />
            <path d="M22 2 11 13" />
          </svg>
        </button>
      </div>
    </section>
  </div>
</template>

<style scoped>
/* ===== 小天 ===== */
.ai-pet {
  position: fixed;
  z-index: 202;
  display: grid;
  width: 96px;
  height: 96px;
  padding: 0;
  cursor: grab;
  background: transparent;
  border: none;
  filter: drop-shadow(0 10px 18px rgb(10 37 64 / 22%));
  place-items: center;
  touch-action: none;
  transition: filter 0.2s;
}

.ai-pet:hover {
  filter: drop-shadow(0 14px 24px rgb(10 37 64 / 34%));
}

.ai-pet-dragging {
  cursor: grabbing;
}

.ai-pet-svg {
  width: 100%;
  height: 100%;
  animation: pet-bob 3.4s ease-in-out infinite;
  transform-origin: 50% 90%;
}

.ai-pet:hover .ai-pet-svg {
  animation-duration: 1.8s;
}

.ai-pet-svg-thinking {
  animation: pet-typing 1.2s ease-in-out infinite;
}

.ai-pet-eyes {
  animation: pet-blink 5s ease-in-out infinite;
  transform-origin: 60px 58px;
}

.ai-pet-laptop {
  animation: pet-laptop 1.2s ease-in-out infinite;
  transform-origin: 60px 92px;
}

.ai-pet-listening .ai-pet-svg {
  animation: pet-pulse 1s ease-in-out infinite;
}

.ai-pet-online {
  position: absolute;
  top: 8px;
  right: 10px;
  width: 14px;
  height: 14px;
  background: #22c55e;
  border: 2px solid #fff;
  border-radius: 50%;
}

.ai-pet-bubble {
  position: absolute;
  top: -18px;
  right: 88px;
  display: flex;
  gap: 4px;
  align-items: center;
  padding: 6px 12px;
  font-size: 12.5px;
  color: #0b2f57;
  white-space: nowrap;
  pointer-events: none;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 6px 16px rgb(10 37 64 / 18%);
  opacity: 0;
  transform: translateY(4px);
  transition: all 0.22s;
}

.ai-pet-bubble::after {
  position: absolute;
  right: -6px;
  bottom: 8px;
  width: 12px;
  height: 12px;
  content: '';
  background: #fff;
  border-radius: 2px;
  transform: rotate(45deg);
}

.ai-pet:hover .ai-pet-bubble,
.ai-pet-bubble-show {
  opacity: 1;
  transform: translateY(0);
}

.ai-pet-bubble .dot {
  width: 5px;
  height: 5px;
  background: #1a4480;
  border-radius: 50%;
  animation: dot-bounce 1s infinite ease-in-out;
}

.ai-pet-bubble .dot:nth-child(2) {
  animation-delay: 0.15s;
}

.ai-pet-bubble .dot:nth-child(3) {
  animation-delay: 0.3s;
}

@keyframes dot-bounce {
  0%,
  100% {
    transform: translateY(0);
    opacity: 0.45;
  }

  50% {
    transform: translateY(-3px);
    opacity: 1;
  }
}

@keyframes pet-bob {
  0%,
  100% {
    transform: translateY(0) rotate(0deg);
  }

  50% {
    transform: translateY(-5px) rotate(-1.2deg);
  }
}

@keyframes pet-pulse {
  0%,
  100% {
    transform: scale(1);
  }

  50% {
    transform: scale(1.07);
  }
}

@keyframes pet-blink {
  0%,
  92%,
  100% {
    transform: scaleY(1);
  }

  95% {
    transform: scaleY(0.12);
  }
}

@keyframes pet-typing {
  0%,
  100% {
    transform: translateY(2px) scale(0.98);
  }

  50% {
    transform: translateY(0) scale(1);
  }
}

@keyframes pet-laptop {
  0%,
  100% {
    transform: translateY(0);
  }

  50% {
    transform: translateY(-1.5px);
  }
}

.fish-tail {
  transform-box: view-box;
  transform-origin: 42px 72px;
  animation: fish-tail-wag 2.6s ease-in-out infinite;
}

.fish-bubbles {
  animation: fish-bubbles-rise 3s ease-in-out infinite;
}

@keyframes fish-tail-wag {
  0%,
  100% {
    transform: rotate(-5deg);
  }

  50% {
    transform: rotate(6deg);
  }
}

@keyframes fish-bubbles-rise {
  0%,
  100% {
    opacity: 0.25;
    transform: translateY(2px);
  }

  50% {
    opacity: 0.85;
    transform: translateY(-3px);
  }
}

/* ===== 面板 ===== */
.ai-mask {
  position: fixed;
  inset: 0;
  z-index: 199;
  background: rgb(10 37 64 / 32%);
}

.ai-panel {
  position: fixed;
  z-index: 201;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: #fff;
  border-radius: 14px;
  box-shadow: 0 14px 40px rgb(10 37 64 / 16%);
}

.ai-panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 18px;
  color: #fff;
  background: linear-gradient(120deg, #0b2f57, #1a4480);
}

.ai-panel-user {
  display: flex;
  gap: 10px;
  align-items: center;
}

.ai-panel-avatar {
  display: grid;
  width: 38px;
  height: 38px;
  overflow: hidden;
  background: rgb(255 255 255 / 92%);
  border-radius: 50%;
  place-items: center;
}

.ai-panel-avatar svg {
  width: 34px;
  height: 34px;
}

.ai-panel-title {
  font-size: 14.5px;
  font-weight: 600;
}

.ai-panel-status {
  margin-top: 2px;
  font-size: 12px;
  color: #bfe0d0;
}

.ai-panel-tools {
  display: flex;
  gap: 8px;
  align-items: center;
}

.ai-scope {
  padding: 4px 6px;
  font-size: 12px;
  color: #0b2f57;
  cursor: pointer;
  border: none;
  border-radius: 6px;
}

.ai-panel-close {
  padding: 0 4px;
  font-size: 20px;
  line-height: 1;
  color: #cdd9ec;
  cursor: pointer;
  background: none;
  border: none;
}

.ai-mode-bar {
  display: flex;
  gap: 6px;
  align-items: center;
  padding: 8px 14px;
  background: #fff;
  border-bottom: 1px solid #eef1f6;
}

.ai-mode {
  padding: 5px 12px;
  font-size: 12.5px;
  color: #5b6675;
  cursor: pointer;
  background: #f1f4f9;
  border: none;
  border-radius: 999px;
}

.ai-mode-active {
  color: #fff;
  background: #1a4480;
}

.ai-model-select {
  flex: 1;
  min-width: 0;
  padding: 4px 6px;
  font-size: 12px;
  color: #1a4480;
  cursor: pointer;
  background: #eef3fa;
  border: none;
  border-radius: 6px;
}

.ai-fallback {
  margin-top: 8px;
  padding: 5px 12px;
  font-size: 12.5px;
  color: #1a4480;
  cursor: pointer;
  background: #eef3fa;
  border: 1px dashed #b9cdea;
  border-radius: 8px;
}

.ai-fallback:hover {
  background: #e0eaf8;
}

.ai-msgs {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 14px;
  padding: 18px;
  overflow-y: auto;
  background: #f4f6f9;
}

.ai-msg {
  max-width: 88%;
  padding: 11px 14px;
  font-size: 14px;
  line-height: 1.65;
  border-radius: 10px;
}

.ai-msg.bot {
  align-self: flex-start;
  background: #fff;
  border: 1px solid #e3e8ef;
  border-top-left-radius: 2px;
}

.ai-msg.me {
  align-self: flex-end;
  color: #fff;
  background: #1a4480;
  border-top-right-radius: 2px;
}

.ai-msg-text {
  white-space: pre-wrap;
}

.ai-typing {
  display: inline-flex;
  gap: 4px;
  align-items: center;
  color: #5b6675;
}

.ai-think-dot {
  width: 5px;
  height: 5px;
  background: #1a4480;
  border-radius: 50%;
  animation: dot-bounce 1s infinite ease-in-out;
}

.ai-think-dot:nth-child(2) {
  animation-delay: 0.15s;
}

.ai-think-dot:nth-child(3) {
  animation-delay: 0.3s;
}

.ai-msg-foot {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin-top: 8px;
  font-size: 11.5px;
  color: #8a93a3;
}

.ai-warn {
  color: #b8860b;
}

.ai-cites {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-top: 10px;
}

.ai-cite {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
  padding: 7px 9px;
  font-size: 12.5px;
  color: #1f2937;
  text-align: left;
  cursor: pointer;
  background: #eef3fa;
  border: 1px solid #dbe6f5;
  border-radius: 8px;
}

.ai-cite:hover {
  background: #e0eaf8;
}

.ai-cite-no {
  font-weight: 700;
  color: #1a4480;
}

.ai-cite-title {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ai-cite-meta {
  font-size: 11.5px;
  color: #5b6675;
}

.ai-suggest {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 12px 18px;
  border-top: 1px solid #eef1f6;
}

.ai-suggest button {
  padding: 5px 12px;
  font-size: 12.5px;
  color: #1a4480;
  cursor: pointer;
  background: #eef3fa;
  border: none;
  border-radius: 999px;
}

.ai-suggest button:hover {
  background: #e0eaf8;
}

.ai-voice-tip {
  padding: 6px 18px;
  font-size: 12px;
  color: #b8860b;
  background: #fffaf0;
  border-top: 1px solid #f3e6c8;
}

.ai-input {
  display: flex;
  gap: 8px;
  padding: 12px 18px 16px;
  border-top: 1px solid #eef1f6;
}

.ai-input input {
  flex: 1;
  height: 42px;
  padding: 0 14px;
  font-size: 14px;
  outline: none;
  border: 1px solid #e3e8ef;
  border-radius: 8px;
}

.ai-input input:focus {
  border-color: #1a4480;
}

.ai-input > button:last-child {
  display: grid;
  width: 42px;
  height: 42px;
  color: #fff;
  cursor: pointer;
  background: #1a4480;
  border: none;
  border-radius: 8px;
  place-items: center;
}

.ai-input > button:last-child:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

.ai-input svg {
  width: 18px;
  height: 18px;
}

.ai-mic {
  display: grid;
  width: 42px;
  height: 42px;
  color: #1a4480;
  cursor: pointer;
  background: #eef3fa;
  border: 1px solid #dbe6f5;
  border-radius: 8px;
  place-items: center;
}

.ai-mic:hover {
  background: #e0eaf8;
}

.ai-mic-on {
  color: #fff;
  background: #b03a2e;
  border-color: #b03a2e;
  animation: mic-pulse 1.2s ease-in-out infinite;
}

.ai-mic-off {
  color: #a8b2c1;
  background: #f4f6f9;
}

@keyframes mic-pulse {
  0%,
  100% {
    box-shadow: 0 0 0 0 rgb(176 58 46 / 45%);
  }

  50% {
    box-shadow: 0 0 0 8px rgb(176 58 46 / 0%);
  }
}
</style>
