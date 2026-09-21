<script lang="ts" setup>
import { computed, nextTick, ref } from 'vue';
import { useRouter } from 'vue-router';

import { Page } from '@vben/common-ui';

import AiAssistantIcon from './components/ai-assistant-icon.vue';
import {
  buildOcrReply,
  executeAction,
  extractDocumentOcr,
  extractImageOcr,
  mockAiReply,
  type BizType,
  type PendingAction,
  type TableResult,
} from './mock';

interface ChatMessage {
  id: number;
  role: 'user' | 'assistant';
  text: string;
  table?: TableResult;
  /** 一句话查多个对象时的多表结果（含 table 本身） */
  tables?: TableResult[];
  /** 写操作：待二次确认的操作卡片 */
  action?: PendingAction;
  /** 确认卡状态：pending 待确认 / done 已执行 / cancelled 已取消 */
  actionState?: 'pending' | 'done' | 'cancelled';
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
  '列出待处理的售后工单',
  '查询待回访任务',
  '帮我给XX客户创建3天后电话回访质保的待办',
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
  // 防御：模板里若把事件对象当参数传进来（@click="send"），不能直接 trim，否则点击会静默失败
  const raw = typeof text === 'string' ? text : inputText.value;
  const content = raw.trim();
  if (!content || sending.value) return;
  if (!activeSession.value) {
    createSession();
  }
  const session = activeSession.value!;

  // 二次确认拦截：存在待确认的写操作时，「确认 / 取消」直接执行，不再经过 AI
  const pendingMsg = getActivePending(session);
  if (
    pendingMsg?.action &&
    /^(确认|确定|执行|是的?|好的?|ok|yes)$/i.test(content)
  ) {
    session.messages.push({ id: msgSeq++, role: 'user', text: content });
    inputText.value = '';
    await runAction(pendingMsg, session);
    return;
  }
  if (
    pendingMsg?.action &&
    /^(取消|不|不用|算了|不要|cancel|no)$/i.test(content)
  ) {
    session.messages.push({ id: msgSeq++, role: 'user', text: content });
    pendingMsg.actionState = 'cancelled';
    inputText.value = '';
    session.messages.push({
      id: msgSeq++,
      role: 'assistant',
      text: '已取消，未执行任何操作。',
    });
    await scrollToBottom();
    return;
  }

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
    if (reply?.tables) {
      assistant.tables = reply.tables;
    }
    if (reply?.action) {
      assistant.action = reply.action;
      assistant.actionState = 'pending';
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

/** 一句话查多个对象时会返回多张表；兼容旧的单表结构 */
function msgTables(m: ChatMessage): TableResult[] {
  if (m.tables && m.tables.length > 0) {
    return m.tables;
  }
  return m.table ? [m.table] : [];
}

/** 取最近几条消息里仍待确认的写操作（一旦后面有新的确认结果即失效） */
function getActivePending(session: Session): ChatMessage | undefined {
  const msgs = session.messages;
  for (let i = msgs.length - 1; i >= 0; i--) {
    const m = msgs[i]!;
    if (m.role !== 'assistant' || !m.action) continue;
    return m.actionState === 'pending' ? m : undefined;
  }
  return undefined;
}

/** 执行待确认的写操作并追加结果消息 */
async function runAction(pendingMsg: ChatMessage, session: Session) {
  sending.value = true;
  const assistant: ChatMessage = {
    id: msgSeq++,
    role: 'assistant',
    text: '',
    loading: true,
  };
  session.messages.push(assistant);
  await scrollToBottom();
  try {
    const result = await executeAction(pendingMsg.action!);
    pendingMsg.actionState = 'done';
    assistant.loading = false;
    assistant.text = result;
  } catch {
    assistant.loading = false;
    assistant.text = '执行失败，请稍后再试。';
  } finally {
    sending.value = false;
    await scrollToBottom();
  }
}

async function confirmAction(m: ChatMessage) {
  if (!m.action || m.actionState !== 'pending' || !activeSession.value) return;
  await runAction(m, activeSession.value);
}

/** 选择型确认卡：把所选类型合并进 payload 后再执行（如采购合同 / 购销合同） */
async function chooseAction(
  m: ChatMessage,
  choice: { label: string; value: Record<string, any> },
) {
  if (!m.action || m.actionState !== 'pending' || !activeSession.value) return;
  const title =
    choice.value?.contractType === 1
      ? '新建采购合同（我方甲方 → 供应商）'
      : choice.value?.contractType === 2
        ? '新建购销合同（我方乙方 → 客户）'
        : choice.value?.asQuotation
          ? '新建报价单'
          : m.action.title;
  m.action = {
    ...m.action,
    title,
    payload: { ...m.action.payload, ...choice.value },
    choices: undefined,
  };
  await runAction(m, activeSession.value);
}

function cancelAction(m: ChatMessage) {
  m.actionState = 'cancelled';
  activeSession.value?.messages.push({
    id: msgSeq++,
    role: 'assistant',
    text: '已取消，未执行任何操作。',
  });
}

/** 上传报价/合同图片 → OCR 识别 → 回显字段 + 生成「新建报价单」确认卡 */
const fileRef = ref<HTMLInputElement | null>(null);

async function onPickFile(e: Event) {
  const input = e.target as HTMLInputElement;
  const file = input.files?.[0];
  input.value = '';
  if (!file) return;
  if (!activeSession.value) {
    createSession();
  }
  const session = activeSession.value!;
  const name = file.name || '';
  const lower = name.toLowerCase();
  // 图片走多模态识别；Word / PDF 走「抽取文本 + 大模型识别」，两条链路输出结构一致
  const isImage =
    file.type.startsWith('image/') ||
    /\.(png|jpe?g|webp|bmp|gif)$/.test(lower);
  const isDocument = /\.(pdf|docx|txt|md|csv)$/.test(lower);
  const sizeLimit = isImage ? 8 : 10; // MB
  if (!isImage && !isDocument) {
    session.messages.push({
      id: msgSeq++,
      role: 'assistant',
      text: '暂不支持该文件格式，请上传图片（png/jpg）或 Word(.docx)、PDF。旧版 .doc 请另存为 .docx 后再上传。',
    });
    return;
  }
  if (file.size > sizeLimit * 1024 * 1024) {
    session.messages.push({
      id: msgSeq++,
      role: 'assistant',
      text: `文件过大（超过 ${sizeLimit}MB），请压缩后再上传。`,
    });
    return;
  }
  if (session.messages.every((m) => m.role !== 'user')) {
    session.title = isImage ? '图片识别' : '文档识别';
  }
  session.messages.push({
    id: msgSeq++,
    role: 'user',
    text: `${isImage ? '📷 上传图片' : '📎 上传文档'}「${name}」进行识别`,
  });
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
    const dataUrl = await new Promise<string>((resolve, reject) => {
      const reader = new FileReader();
      reader.onload = () => resolve(String(reader.result));
      reader.onerror = () => reject(new Error('读取文件失败'));
      reader.readAsDataURL(file);
    });
    const raw = isImage
      ? await withTimeout(extractImageOcr(dataUrl), 120_000)
      : await withTimeout(extractDocumentOcr(name, dataUrl), 120_000);
    const reply = await buildOcrReply(raw);
    assistant.loading = false;
    assistant.text = reply.text;
    if (reply.table) {
      assistant.table = reply.table;
    }
    if (reply.action) {
      assistant.action = reply.action;
      assistant.actionState = 'pending';
    }
  } catch (e: any) {
    assistant.loading = false;
    const msg = String(e?.message ?? '');
    if (/没有该操作权限|无权限|403/.test(msg)) {
      assistant.text = '您当前没有权限使用识别功能。';
    } else if (/超时/.test(msg)) {
      assistant.text = '识别超时，请稍后重试，或换用体积更小的文件。';
    } else if (msg && !/Network|Failed to fetch|timeout/i.test(msg)) {
      // 后端返回的业务提示（格式不支持、文档无文字层等）直接展示，便于用户处理
      assistant.text = msg;
    } else {
      assistant.text = '识别失败，请稍后再试。';
    }
  } finally {
    sending.value = false;
    await scrollToBottom();
  }
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
  quotation: 'CrmQuotationDetail',
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
                  v-for="(line, i) in m.text ? m.text.split('\n') : []"
                  :key="i"
                  class="whitespace-pre-wrap"
                >
                  {{ line }}
                </p>

                <!-- 表格结果（一句话查多个对象时会返回多张表） -->
                <div
                  v-for="tbl in msgTables(m)"
                  :key="tbl.title"
                  class="mt-3 overflow-hidden rounded-lg border border-border bg-background"
                >
                  <div
                    class="flex items-center justify-between border-b border-border px-3 py-2"
                  >
                    <span class="text-xs font-medium text-foreground">{{
                      tbl.title
                    }}</span>
                    <span class="text-[11px] text-muted-foreground">{{
                      tbl.source
                    }}</span>
                  </div>
                  <div class="overflow-x-auto">
                    <table class="w-full border-collapse text-xs">
                      <thead>
                        <tr class="bg-accent/50 text-left text-muted-foreground">
                          <th
                            v-for="col in tbl.columns"
                            :key="col.key"
                            class="whitespace-nowrap px-3 py-2 font-medium"
                          >
                            {{ col.title }}
                          </th>
                        </tr>
                      </thead>
                      <tbody>
                        <tr
                          v-for="(row, ri) in tbl.rows"
                          :key="ri"
                          :class="[
                            'border-t border-border transition hover:bg-accent/40',
                            tbl.bizType ? 'cursor-pointer' : '',
                          ]"
                          :title="tbl.bizType ? '点击查看详情' : ''"
                          @click="openDetail(tbl.bizType, row)"
                        >
                          <td
                            v-for="col in tbl.columns"
                            :key="col.key"
                            class="whitespace-nowrap px-3 py-2 text-foreground"
                          >
                            {{ row[col.dataIndex] }}
                          </td>
                        </tr>
                        <tr
                          v-if="tbl.rows.length === 0"
                          class="border-t border-border"
                        >
                          <td
                            :colspan="tbl.columns.length"
                            class="px-3 py-4 text-center text-muted-foreground"
                          >
                            暂无数据
                          </td>
                        </tr>
                      </tbody>
                    </table>
                  </div>
                </div>

                <!-- 写操作二次确认卡 -->
                <div
                  v-if="m.action"
                  class="mt-3 rounded-lg border border-primary/40 bg-background p-3"
                >
                  <div class="text-xs font-semibold text-foreground">
                    {{
                      m.actionState === 'done'
                        ? '✅ 已执行'
                        : m.actionState === 'cancelled'
                          ? '已取消'
                          : m.action.choices && m.action.choices.length > 0
                            ? '⚠️ 请先选择单据类型，再建单'
                            : '⚠️ 请确认以下操作内容（确认后才会执行）'
                    }}
                  </div>
                  <table class="mt-2 w-full text-xs">
                    <tbody>
                      <tr v-for="f in m.action.fields" :key="f.label">
                        <td
                          class="w-20 whitespace-nowrap py-0.5 pr-3 align-top text-muted-foreground"
                        >
                          {{ f.label }}
                        </td>
                        <td class="py-0.5 text-foreground">{{ f.value }}</td>
                      </tr>
                    </tbody>
                  </table>
                  <div
                    v-if="m.actionState === 'pending'"
                    class="mt-2 flex flex-wrap gap-2"
                  >
                    <!-- 需要用户先选择类型（如合同类型无法判定）时，展示选项按钮，不擅自猜测 -->
                    <template v-if="m.action.choices && m.action.choices.length > 0">
                      <button
                        v-for="c in m.action.choices"
                        :key="c.label"
                        class="rounded-md bg-primary px-3 py-1 text-xs text-primary-foreground transition hover:opacity-90 disabled:opacity-50"
                        :disabled="sending"
                        @click="chooseAction(m, c)"
                      >
                        {{ c.label }}
                      </button>
                    </template>
                    <button
                      v-else
                      class="rounded-md bg-primary px-3 py-1 text-xs text-primary-foreground transition hover:opacity-90 disabled:opacity-50"
                      :disabled="sending"
                      @click="confirmAction(m)"
                    >
                      确认执行
                    </button>
                    <button
                      class="rounded-md border border-border px-3 py-1 text-xs text-muted-foreground transition hover:bg-accent/60"
                      :disabled="sending"
                      @click="cancelAction(m)"
                    >
                      取消
                    </button>
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
          <AiAssistantIcon :size="88" />
          <div class="text-base font-medium text-foreground">CRM 智能助手</div>
          <div class="max-w-md text-sm text-muted-foreground">
            用自然语言向我提问：客户、线索、商机、合同、回款、待办、报价单、售后工单、回访、质保档案都可以查。我会调用通义千问
            理解你的意图，从各 CRM 模块拉取真实数据以表格展示，查不到会如实告知。<br />
            也可以点 📎 上传合同/报价单（图片、Word、PDF）自动识别字段并建单。
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
              placeholder="输入您的问题，例如：查询XX客户的报价单、售后工单和质保档案"
              class="min-h-[44px] flex-1 resize-none rounded-lg border border-border bg-background px-3 py-2 text-sm text-foreground outline-none placeholder:text-muted-foreground focus:border-primary"
              @keydown="onEnter"
            ></textarea>
            <input
              ref="fileRef"
              type="file"
              accept="image/*,.pdf,.docx,.txt"
              class="hidden"
              @change="onPickFile"
            />
            <button
              class="h-[44px] shrink-0 rounded-lg border border-border px-3 text-sm text-muted-foreground transition hover:border-primary hover:text-primary disabled:opacity-50"
              :disabled="sending"
              title="上传合同/报价单：支持图片、Word(.docx)、PDF，自动识别字段并回填"
              @click="fileRef?.click()"
            >
              📎
            </button>
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
              @click="send()"
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
