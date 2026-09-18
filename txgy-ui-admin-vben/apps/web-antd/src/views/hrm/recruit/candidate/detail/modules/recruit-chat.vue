<script lang="ts" setup>
import { computed, onMounted, onUnmounted, ref } from 'vue';

import { message } from 'ant-design-vue';

import { getHrmRecruitChatAttachment } from '#/api/hrm/portal/recruit/chat-attachment';
import { requestClient } from '#/api/request';
import type { HrmPortalRecruitChatApi } from '#/api/hrm/portal/recruit/chat';

const props = defineProps<{ candidateId: number }>();

type Conversation = HrmPortalRecruitChatApi.Conversation & {
  messages?: HrmPortalRecruitChatApi.ChatMessage[];
};

const loading = ref(false);
const sending = ref(false);
const conversations = ref<Conversation[]>([]);
const messages = ref<HrmPortalRecruitChatApi.ChatMessage[]>([]);
const selectedId = ref<number>();
const draft = ref('');
let pollTimer: ReturnType<typeof setInterval> | undefined;

const selectedConversation = computed(() =>
  conversations.value.find((item) => item.id === selectedId.value),
);

function statusText(status?: number) {
  if (status === 1) return '等待招聘方回复';
  if (status === 2) return '求职方已回复';
  if (status === 3) return '沟通失败';
  return '等待回复';
}

function statusColor(status?: number) {
  if (status === 2) return 'green';
  if (status === 3) return 'default';
  return 'blue';
}

function applicationStatusText(status?: string) {
  if (status === 'READY_TO_SUBMIT') return '待投递';
  if (status === 'SUBMITTED') return '已投递';
  return '';
}

async function load(preferredId?: number) {
  loading.value = true;
  try {
    const list =
      (await requestClient.get<Conversation[]>('/hrm/recruit/chat/conversation/list', {
        params: { candidateId: props.candidateId },
      })) || [];
    conversations.value = list.filter((item) => item.lastTime);
    const nextId = preferredId || selectedId.value || conversations.value[0]?.id;
    if (nextId && conversations.value.some((item) => item.id === nextId)) {
      await selectConversation(nextId);
    } else {
      selectedId.value = undefined;
      messages.value = [];
    }
  } catch (error: any) {
    conversations.value = [];
    message.error(error?.msg || error?.message || '招聘沟通列表加载失败');
  } finally {
    loading.value = false;
  }
}

async function selectConversation(id: number) {
  selectedId.value = id;
  try {
    messages.value =
      (await requestClient.get<HrmPortalRecruitChatApi.ChatMessage[]>(
        '/hrm/recruit/chat/message/list',
        { params: { conversationId: id, limit: 200 } },
      )) || [];
    await requestClient.put('/hrm/recruit/chat/conversation/read', undefined, {
      params: { conversationId: id },
    });
    const current = conversations.value.find((item) => item.id === id);
    if (current) current.unreadCount = 0;
  } catch (error: any) {
    message.error(error?.msg || error?.message || '消息加载失败');
  }
}

async function send() {
  const conversation = selectedConversation.value;
  const content = draft.value.trim();
  if (!conversation || !content || sending.value) return;
  if (conversation.status === 3) {
    message.info('沟通失败，不能继续回复');
    return;
  }
  sending.value = true;
  try {
    const created = await requestClient.post<HrmPortalRecruitChatApi.ChatMessage>(
      '/hrm/recruit/chat/message/send',
      { conversationId: conversation.id, contentType: 1, content },
    );
    messages.value.push(created);
    draft.value = '';
    await load(conversation.id);
    message.success('回复成功');
  } catch (error: any) {
    message.error(error?.msg || error?.message || '发送失败');
  } finally {
    sending.value = false;
  }
}

async function endConversation() {
  const conversation = selectedConversation.value;
  if (!conversation || conversation.status === 3) return;
  const reason = window.prompt('请填写结束沟通原因', '招聘方结束沟通');
  if (!reason?.trim()) return;
  try {
    await requestClient.put('/hrm/recruit/chat/conversation/end', {
      conversationId: conversation.id,
      reason: reason.trim(),
    });
    await load(conversation.id);
    message.success('沟通已结束');
  } catch (error: any) {
    message.error(error?.msg || error?.message || '结束沟通失败');
  }
}

async function poll() {
  if (!selectedId.value) return;
  try {
    const lastId = messages.value[messages.value.length - 1]?.id;
    const incoming = await requestClient.get<HrmPortalRecruitChatApi.ChatMessage[]>(
      '/hrm/recruit/chat/message/list',
      { params: { conversationId: selectedId.value, minId: lastId, limit: 50 } },
    );
    if (incoming?.length) {
      const knownIds = new Set(messages.value.map((item) => item.id));
      messages.value.push(...incoming.filter((item) => !knownIds.has(item.id)));
    }
    await load(selectedId.value);
  } catch {
    // 轮询失败不影响企业继续操作，下一轮自动重试。
  }
}

onMounted(() => {
  void load();
  pollTimer = setInterval(() => void poll(), 2500);
});

onUnmounted(() => {
  if (pollTimer) clearInterval(pollTimer);
});
</script>

<template>
  <div class="recruit-chat-panel">
    <div v-if="loading && !conversations.length" class="chat-empty">正在加载沟通记录...</div>
    <div v-else-if="!conversations.length" class="chat-empty">该候选人暂无招聘沟通记录</div>
    <div v-else class="chat-layout">
      <aside class="conversation-list">
        <button
          v-for="item in conversations"
          :key="item.id"
          type="button"
          class="conversation-item"
          :class="{ active: selectedId === item.id }"
          @click="selectConversation(item.id)"
        >
          <span class="conversation-copy">
            <strong>{{ item.postName || '招聘职位' }}</strong>
            <small>{{ item.lastContent || '暂无消息' }}</small>
          </span>
          <span class="conversation-tags">
            <a-tag :color="statusColor(item.status)">{{ statusText(item.status) }}</a-tag>
            <a-tag v-if="applicationStatusText(item.applicationStatus)" color="blue">{{ applicationStatusText(item.applicationStatus) }}</a-tag>
          </span>
        </button>
      </aside>

      <section v-if="selectedConversation" class="conversation-detail">
        <header class="conversation-header">
          <div>
            <strong>{{ selectedConversation.peerUserName || '候选人' }}</strong>
            <span>{{ selectedConversation.postName || '招聘职位' }}</span>
            <a-tag :color="statusColor(selectedConversation.status)">{{ statusText(selectedConversation.status) }}</a-tag>
            <a-tag v-if="applicationStatusText(selectedConversation.applicationStatus)" color="blue">{{ applicationStatusText(selectedConversation.applicationStatus) }}</a-tag>
          </div>
          <a-button v-if="selectedConversation.status !== 3" type="link" danger @click="endConversation">结束沟通</a-button>
        </header>

        <div class="message-list">
          <div v-if="!messages.length" class="chat-empty">求职者还没有发送消息</div>
          <div v-for="item in messages" :key="item.id" class="message-row" :class="{ mine: item.mine }">
            <div class="message-bubble">
              <a
                v-if="getHrmRecruitChatAttachment(item.content, item.contentType)?.type === 'resume'"
                class="message-file"
                :href="getHrmRecruitChatAttachment(item.content, item.contentType)?.previewUrl"
                target="_blank"
                rel="noreferrer"
              >
                <span class="message-file-icon">📄</span>
                <span class="message-file-copy">
                  <b>{{ getHrmRecruitChatAttachment(item.content, item.contentType)?.title }}</b>
                  <em>{{ getHrmRecruitChatAttachment(item.content, item.contentType)?.fileName }}</em>
                </span>
              </a>
              <a
                v-else-if="getHrmRecruitChatAttachment(item.content, item.contentType)?.type === 'image'"
                class="message-image"
                :href="getHrmRecruitChatAttachment(item.content, item.contentType)?.previewUrl"
                target="_blank"
                rel="noreferrer"
              >
                <img :src="getHrmRecruitChatAttachment(item.content, item.contentType)?.previewUrl" :alt="getHrmRecruitChatAttachment(item.content, item.contentType)?.fileName" />
                <span>{{ getHrmRecruitChatAttachment(item.content, item.contentType)?.fileName }}</span>
              </a>
              <template v-else>{{ item.content }}</template>
              <small>{{ item.sendTime }}</small>
            </div>
          </div>
        </div>

        <div v-if="selectedConversation.status !== 3" class="reply-box">
          <a-textarea v-model:value="draft" :rows="3" placeholder="回复求职者" @press-enter="send" />
          <a-button type="primary" :loading="sending" :disabled="!draft.trim()" @click="send">发送回复</a-button>
        </div>
        <div v-else class="chat-notice">沟通失败{{ selectedConversation.endReason ? `：${selectedConversation.endReason}` : '' }}</div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.recruit-chat-panel { min-height: 420px; }
.chat-empty { padding: 56px 20px; color: #94a3b8; text-align: center; }
.chat-layout { display: grid; grid-template-columns: 280px minmax(0, 1fr); min-height: 420px; border: 1px solid #e2e8f0; }
.conversation-list { border-right: 1px solid #e2e8f0; }
.conversation-item { display: flex; width: 100%; gap: 10px; justify-content: space-between; padding: 14px; border: 0; border-bottom: 1px solid #f1f5f9; background: #fff; text-align: left; cursor: pointer; }
.conversation-item.active, .conversation-item:hover { background: #fff7ed; }
.conversation-copy { display: grid; min-width: 0; gap: 7px; }
.conversation-copy strong, .conversation-copy small { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.conversation-copy strong { color: #334155; }
.conversation-copy small { color: #94a3b8; }
.conversation-tags { display: grid; flex-shrink: 0; justify-items: end; gap: 4px; }
.conversation-detail { display: flex; min-width: 0; flex-direction: column; }
.conversation-header { display: flex; align-items: center; justify-content: space-between; gap: 16px; padding: 18px 22px; border-bottom: 1px solid #e2e8f0; }
.conversation-header > div { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; }
.conversation-header span { color: #64748b; }
.message-list { display: flex; min-height: 240px; flex: 1; flex-direction: column; gap: 10px; padding: 20px 22px; overflow: auto; background: #f8fafc; }
.message-row { display: flex; justify-content: flex-start; }
.message-row.mine { justify-content: flex-end; }
.message-bubble { max-width: 70%; padding: 10px 13px; border-radius: 8px; background: #fff; color: #334155; white-space: pre-wrap; word-break: break-word; box-shadow: 0 2px 5px rgb(15 23 42 / 5%); }
.message-row.mine .message-bubble { background: #ffedd5; }
.message-file { display: inline-flex; min-width: 230px; align-items: center; gap: 10px; color: inherit; text-decoration: none; }
.message-file-icon { font-size: 24px; line-height: 1; }
.message-file-copy { display: grid; min-width: 0; gap: 3px; }
.message-file-copy b, .message-file-copy em { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.message-file-copy b { color: #334155; font-weight: 600; }
.message-file-copy em { color: #94a3b8; font-size: 12px; font-style: normal; }
.message-image { display: inline-flex; max-width: 250px; flex-direction: column; gap: 6px; color: inherit; text-decoration: none; }
.message-image img { display: block; max-width: 250px; max-height: 190px; border-radius: 4px; object-fit: contain; }
.message-image span { overflow: hidden; color: #94a3b8; font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }
.message-bubble small { display: block; margin-top: 5px; color: #94a3b8; font-size: 11px; }
.reply-box { display: grid; gap: 10px; padding: 14px 22px; border-top: 1px solid #e2e8f0; }
.reply-box .ant-btn { justify-self: end; }
.chat-notice { padding: 16px 22px; color: #64748b; background: #f8fafc; }
@media (max-width: 720px) { .chat-layout { grid-template-columns: 1fr; } .conversation-list { max-height: 200px; overflow: auto; border-right: 0; border-bottom: 1px solid #e2e8f0; } }
</style>
