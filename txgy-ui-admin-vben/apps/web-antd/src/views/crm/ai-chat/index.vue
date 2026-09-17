<script lang="ts" setup>
import { computed, nextTick, ref } from 'vue';
import { useRouter } from 'vue-router';

import { Page } from '@vben/common-ui';

import AiRobotIcon from './components/ai-robot-icon.vue';
import { mockAiReply, type BizType, type TableResult } from './mock';

interface ChatMessage {
  id: number;
  role: 'user' | 'assistant';
  text: string;
  table?: TableResult;
  loading?: boolean;
}

interface Session {
  id: number;
  title: string;
  messages: ChatMessage[];
}

let sessionSeq = 1;
let msgSeq = 1;

const sessions = ref<Session[]>([]);
const activeId = ref<number | undefined>(undefined);
const inputText = ref('');
const sending = ref(false);
const scrollRef = ref<HTMLElement | null>(null);

const activeSession = computed(() =>
  sessions.value.find((s) => s.id === activeId.value),
);

const examples = [
  '帮我查找名字叫张三的客户信息',
  '今日待办要联系的客户有哪些',
  '列出本月所有商机',
  '查看待办事项',
  '查询客户公海',
];

function createSession() {
  const s: Session = { id: sessionSeq++, title: '新对话', messages: [] };
  sessions.value.unshift(s);
  activeId.value = s.id;
  return s;
}

function selectSession(id: number) {
  activeId.value = id;
}

async function scrollToBottom() {
  await nextTick();
  if (scrollRef.value) {
    scrollRef.value.scrollTop = scrollRef.value.scrollHeight;
  }
}

/** 给整次回复加超时，避免 AI/接口挂起导致发送按钮一直被锁死 */
function withTimeout<T>(p: Promise<T>, ms: number): Promise<T> {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error('回复超时，请稍后重试')), ms);
    p.then(
      (v) => {
        clearTimeout(timer);
        resolve(v);
      },
      (e) => {
        clearTimeout(timer);
        reject(e);
      },
    );
  });
}

/** 最多带最近 6 条消息给模型：够理解「那他们呢」这类追问，又不至于挤占上下文 */
const HISTORY_LIMIT = 6;
/** 单条历史的截断长度，助手回复里可能带很长的摘要 */
const HISTORY_TEXT_LIMIT = 200;

/** 把当前会话最近几轮问答拼成上下文，供模型理解追问、补充条件 */
function buildHistory(session: Session): string {
  return session.messages
    .filter((m) => !m.loading && m.text.trim())
    .slice(-HISTORY_LIMIT)
    .map(
      (m) =>
        `${m.role === 'user' ? '用户' : '助手'}：${m.text.slice(0, HISTORY_TEXT_LIMIT)}`,
    )
    .join('\n');
}

async function send(text?: string) {
  const content = (text ?? inputText.value).trim();
  if (!content || sending.value) return;
  if (!activeSession.value) {
    createSession();
  }
  const session = activeSession.value!;
  // 追问要基于上一轮的查询范围继续筛选，因此先取历史，再放入本轮问题
  const history = buildHistory(session);
  session.messages.push({ id: msgSeq++, role: 'user', text: content });
  inputText.value = '';

  // 首次提问时用问题作为会话标题
  const userCount = session.messages.filter((m) => m.role === 'user').length;
  if (userCount === 1) {
    session.title = content.slice(0, 14);
  }

  const assistant: ChatMessage = {
    id: msgSeq++,
    role: 'assistant',
    text: '',
    loading: true,
  };
  session.messages.push(assistant);
  sending.value = true;
  await scrollToBottom();

  try {
    const reply = await withTimeout(mockAiReply(content, history), 30000);
    assistant.loading = false;
    assistant.text = reply?.text ?? '';
    if (reply?.degraded) {
      assistant.text +=
        '\n\n（提示：AI 意图识别未生效，本次使用关键词匹配，请检查千问接口配置）';
    }
    if (reply?.table) {
      assistant.table = reply.table;
    }
  } catch {
    assistant.loading = false;
    assistant.text = '抱歉，处理您的请求时出错了，请稍后再试。';
  } finally {
    sending.value = false;
    await scrollToBottom();
  }
}

function onEnter(e: KeyboardEvent) {
  // 仅在按下 Enter 时发送；Shift+Enter 换行；排除中文输入法选词（组合输入）时的 Enter
  if (e.key !== 'Enter' || e.shiftKey || e.isComposing) return;
  e.preventDefault();
  send();
}

const router = useRouter();

/** 业务类型 → 详情页路由名 */
const ROUTE_BY_BIZ: Record<string, string> = {
  business: 'CrmBusinessDetail',
  clue: 'CrmClueDetail',
  contact: 'CrmContactDetail',
  contract: 'CrmContractDetail',
  customer: 'CrmCustomerDetail',
  product: 'CrmProductDetail',
  receivable: 'CrmReceivableDetail',
  receivablePlan: 'CrmReceivablePlanDetail',
};

/** 点击表格行：跳转到对应业务对象的详情页（统计表没有 bizType，不响应） */
function openDetail(bizType: BizType | undefined, row: Record<string, any>) {
  const id = row?.id;
  const routeName = bizType ? ROUTE_BY_BIZ[bizType] : undefined;
  if (!id || !routeName) {
    return;
  }
  router.push({ name: routeName, params: { id } });
}
</script>

<template>
  <Page>
    <div class="flex h-full min-h-0 gap-4">
      <!-- 左侧：会话列表 -->
      <aside
        class="flex w-64 shrink-0 flex-col overflow-hidden rounded-xl border border-border bg-card"
      >
        <div class="flex items-center justify-between border-b border-border p-3">
          <span class="text-sm font-semibold text-foreground">对话</span>
          <button
            class="rounded-md bg-primary px-2 py-1 text-xs text-primary-foreground transition hover:opacity-90"
            @click="createSession"
          >
            + 新建
          </button>
        </div>
        <div class="min-h-0 flex-1 overflow-y-auto p-2">
          <div
            v-for="s in sessions"
            :key="s.id"
            class="mb-1 cursor-pointer rounded-lg px-3 py-2 text-sm transition"
            :class="
              s.id === activeId
                ? 'bg-accent text-accent-foreground'
                : 'text-muted-foreground hover:bg-accent/60'
            "
            @click="selectSession(s.id)"
          >
            <div class="truncate font-medium">{{ s.title }}</div>
            <div class="truncate text-xs opacity-60">
              {{ s.messages.at(-1)?.text || '暂无消息' }}
            </div>
          </div>
          <div
            v-if="sessions.length === 0"
            class="px-3 py-6 text-center text-xs text-muted-foreground"
          >
            还没有对话，点击「新建」开始
          </div>
        </div>
      </aside>

      <!-- 右侧：对话窗口 -->
      <section
        class="flex min-w-0 flex-1 flex-col overflow-hidden rounded-xl border border-border bg-card"
      >
        <!-- 消息区 -->
        <div
          v-if="activeSession"
          ref="scrollRef"
          class="min-h-0 flex-1 space-y-4 overflow-y-auto p-4"
        >
          <div
            v-for="m in activeSession.messages"
            :key="m.id"
            class="flex"
            :class="m.role === 'user' ? 'justify-end' : 'justify-start'"
          >
            <div
              class="max-w-[80%] rounded-2xl px-4 py-3 text-sm leading-relaxed"
              :class="
                m.role === 'user'
                  ? 'bg-primary text-primary-foreground'
                  : 'bg-muted text-foreground'
              "
            >
              <!-- 思考中 -->
              <span v-if="m.loading" class="inline-flex gap-1">
                <i class="dot" /> <i class="dot" /> <i class="dot" />
                <span class="ml-1 opacity-70">正在思考…</span>
              </span>

              <!-- 文本 -->
              <template v-else>
                <p
                  v-for="(line, i) in m.text.split('\n')"
                  :key="i"
                  class="whitespace-pre-wrap"
                >
                  {{ line }}
                </p>

                <!-- 表格结果 -->
                <div
                  v-if="m.table"
                  class="mt-3 overflow-hidden rounded-lg border border-border bg-background"
                >
                  <div
                    class="flex items-center justify-between border-b border-border px-3 py-2"
                  >
                    <span class="text-xs font-medium text-foreground">{{
                      m.table.title
                    }}</span>
                    <span class="text-[11px] text-muted-foreground">{{
                      m.table.source
                    }}</span>
                  </div>
                  <div class="overflow-x-auto">
                    <table class="w-full border-collapse text-xs">
                      <thead>
                        <tr class="bg-accent/50 text-left text-muted-foreground">
                          <th
                            v-for="col in m.table.columns"
                            :key="col.key"
                            class="whitespace-nowrap px-3 py-2 font-medium"
                          >
                            {{ col.title }}
                          </th>
                        </tr>
                      </thead>
                      <tbody>
                        <tr
                          v-for="(row, ri) in m.table.rows"
                          :key="ri"
                          :class="[
                            'border-t border-border transition hover:bg-accent/40',
                            m.table.bizType ? 'cursor-pointer' : '',
                          ]"
                          :title="m.table.bizType ? '点击查看详情' : ''"
                          @click="openDetail(m.table.bizType, row)"
                        >
                          <td
                            v-for="col in m.table.columns"
                            :key="col.key"
                            class="whitespace-nowrap px-3 py-2 text-foreground"
                          >
                            {{ row[col.dataIndex] }}
                          </td>
                        </tr>
                        <tr
                          v-if="m.table.rows.length === 0"
                          class="border-t border-border"
                        >
                          <td
                            :colspan="m.table.columns.length"
                            class="px-3 py-4 text-center text-muted-foreground"
                          >
                            暂无数据
                          </td>
                        </tr>
                      </tbody>
                    </table>
                  </div>
                </div>
              </template>
            </div>
          </div>
        </div>

        <!-- 空状态 -->
        <div
          v-else
          class="flex min-h-0 flex-1 flex-col items-center justify-center gap-4 p-6 text-center"
        >
          <AiRobotIcon :size="88" class="text-primary" />
          <div class="text-base font-medium text-foreground">CRM 智能助手</div>
          <div class="max-w-md text-sm text-muted-foreground">
            用自然语言向我提问，例如查询客户、线索、商机、待办等。我会调用通义千问
            理解你的意图，并从各 CRM 模块拉取数据以表格展示。
          </div>
          <div class="flex flex-wrap justify-center gap-2">
            <button
              v-for="ex in examples"
              :key="ex"
              class="rounded-full border border-border bg-background px-3 py-1 text-xs text-muted-foreground transition hover:border-primary hover:text-primary"
              @click="send(ex)"
            >
              {{ ex }}
            </button>
          </div>
        </div>

        <!-- 输入区 -->
        <div class="border-t border-border p-3">
          <div class="flex items-end gap-2">
            <textarea
              v-model="inputText"
              rows="2"
              placeholder="输入您的问题，例如：帮我查找名字叫张三的客户信息"
              class="min-h-[44px] flex-1 resize-none rounded-lg border border-border bg-background px-3 py-2 text-sm text-foreground outline-none placeholder:text-muted-foreground focus:border-primary"
              @keydown="onEnter"
            ></textarea>
            <button
              class="h-[44px] shrink-0 rounded-lg bg-primary px-4 text-sm font-medium text-primary-foreground transition hover:opacity-90 disabled:opacity-50"
              :disabled="sending || !inputText.trim()"
              :title="
                sending
                  ? '正在等待 AI 回复，请稍候'
                  : !inputText.trim()
                    ? '请先输入问题'
                    : ''
              "
              @click="send"
            >
              {{ sending ? '思考中…' : '发送' }}
            </button>
          </div>
          <div class="mt-1 text-[11px] text-muted-foreground">
            由通义千问理解意图，数据实时来自 CRM 各业务模块的真实接口。
          </div>
        </div>
      </section>
    </div>
  </Page>
</template>

<style scoped>
.dot {
  display: inline-block;
  width: 5px;
  height: 5px;
  border-radius: 9999px;
  background: currentColor;
  opacity: 0.5;
  animation: blink 1.2s infinite both;
}
.dot:nth-child(2) {
  animation-delay: 0.2s;
}
.dot:nth-child(3) {
  animation-delay: 0.4s;
}
@keyframes blink {
  0%,
  80%,
  100% {
    opacity: 0.2;
  }
  40% {
    opacity: 1;
  }
}
</style>
