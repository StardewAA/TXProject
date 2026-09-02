<script lang="ts" setup>
import { ref } from 'vue';
import { useRouter } from 'vue-router';

import { Page } from '@vben/common-ui';
import { useUserStore } from '@vben/stores';

import { Button, Card, Input, message } from 'ant-design-vue';

import { createChatConversationMy } from '#/api/ai/chat/conversation';

defineOptions({ name: 'DashboardAiHome' });

const router = useRouter();
const userStore = useUserStore();
const prompt = ref('');

/** 发送消息：创建 AI 对话并跳转到「AI 大模型 -> AI 对话」自动发送 */
async function handleSend() {
  const content = prompt.value.trim();
  if (!content) {
    message.warning('请输入消息内容');
    return;
  }
  try {
    // 1. 创建对话
    const conversationId = await createChatConversationMy({} as any);
    // 2. 跳转到 AI 对话页，并携带消息自动发送
    await router.push({
      path: '/ai/chat',
      query: { conversationId, message: content },
    });
  } catch (error) {
    console.error('创建对话失败:', error);
    message.error('创建对话失败，请稍后重试');
  }
}
</script>

<template>
  <Page auto-content-height>
    <div class="flex h-full items-center justify-center p-6">
      <Card class="w-full max-w-2xl">
        <div class="mb-6 text-center">
          <div class="text-xl font-bold">
            你好，{{ userStore.userInfo?.nickname || '朋友' }}，欢迎使用 OPENLAB BS！
          </div>
          <div class="mt-2 text-sm opacity-60">
            输入消息后点击发送，将自动跳转到「AI 大模型 -&gt; AI 对话」进行对话
          </div>
        </div>
        <Input.TextArea
          v-model:value="prompt"
          :rows="5"
          placeholder="请输入你想咨询的内容..."
          class="w-full"
          @keydown.ctrl.enter.prevent="handleSend"
        />
        <div class="mt-4 flex justify-end">
          <Button
            type="primary"
            :disabled="!prompt.trim()"
            @click="handleSend"
          >
            发送
          </Button>
        </div>
      </Card>
    </div>
  </Page>
</template>
