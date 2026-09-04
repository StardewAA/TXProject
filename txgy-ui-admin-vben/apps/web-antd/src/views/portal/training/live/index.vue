<script lang="ts" setup>
import type { TrainingLearnerApi } from '../learning/api';

import { onMounted, onUnmounted, ref } from 'vue';

import { Page } from '@vben/common-ui';

import { Button, Card, Empty, Input, Modal, Tag, message } from 'ant-design-vue';

import {
  endSession,
  getDanmaku,
  getLiveRooms,
  heartbeat,
  joinLive,
  sendDanmaku,
  startSession,
} from '../learning/api';

defineOptions({ name: 'PortalTrainingLive' });

const loading = ref(false);
const groups = ref<TrainingLearnerApi.LiveRoomGroup>({});

async function load() {
  loading.value = true;
  try {
    groups.value = (await getLiveRooms()) ?? {};
  } finally {
    loading.value = false;
  }
}

// ==================== 观看直播/回放 ====================
const watchVisible = ref(false);
const watching = ref<TrainingLearnerApi.LiveRoom | null>(null);
const playUrl = ref('');
const sessionId = ref<number | undefined>();
const validSeconds = ref(0);

const danmakuList = ref<TrainingLearnerApi.Danmaku[]>([]);
const danmakuText = ref('');

let hbTimer: ReturnType<typeof setInterval> | undefined;
let dmTimer: ReturnType<typeof setInterval> | undefined;

async function openWatch(room: TrainingLearnerApi.LiveRoom, mode: 'live' | 'replay') {
  const url = mode === 'live' ? room.streamUrl : room.replayVideoUrl;
  if (!url) {
    message.warning('该房间暂无可播放地址');
    return;
  }
  try {
    const session =
      mode === 'live'
        ? await joinLive(room.id!)
        : await startSession({
            mediaId: room.id!,
            mediaType: 'replay',
          });
    sessionId.value = session?.sessionId;
    watching.value = room;
    playUrl.value = url;
    validSeconds.value = 0;
    danmakuList.value = [];
    watchVisible.value = true;
    hbTimer = setInterval(sendHeartbeat, 5000);
    if (mode === 'live') {
      loadDanmaku();
      dmTimer = setInterval(loadDanmaku, 3000);
    }
  } catch (error: any) {
    message.error(error?.message ?? '进入直播间失败');
  }
}

async function sendHeartbeat() {
  if (!sessionId.value) return;
  try {
    const res = await heartbeat({ sessionId: sessionId.value });
    if (res) {
      validSeconds.value = res.validSeconds ?? validSeconds.value;
    }
  } catch {
    // 心跳异常忽略，避免打扰观看
  }
}

async function loadDanmaku() {
  if (!watching.value?.id) return;
  const lastId = danmakuList.value.at(-1)?.id;
  const list = await getDanmaku(watching.value.id, lastId);
  if (list?.length) {
    danmakuList.value = [...danmakuList.value, ...list].slice(-100);
  }
}

async function handleSendDanmaku() {
  if (!watching.value?.id || !danmakuText.value.trim()) return;
  await sendDanmaku({ roomId: watching.value.id, content: danmakuText.value.trim() });
  danmakuText.value = '';
  loadDanmaku();
}

function closeWatch() {
  watchVisible.value = false;
  if (hbTimer) {
    clearInterval(hbTimer);
    hbTimer = undefined;
  }
  if (dmTimer) {
    clearInterval(dmTimer);
    dmTimer = undefined;
  }
  if (sessionId.value) {
    endSession(sessionId.value).catch(() => undefined);
    sessionId.value = undefined;
  }
}

onMounted(load);
onUnmounted(closeWatch);
</script>

<template>
  <Page auto-content-height>
    <div v-if="loading" class="py-10 text-center text-gray-400">加载中...</div>
    <template v-else>
      <div class="mb-6">
        <div class="mb-2 flex items-center gap-2">
          <span class="text-base font-medium">直播中</span>
          <Tag color="red">LIVE</Tag>
        </div>
        <div v-if="groups.live?.length" class="grid gap-4 md:grid-cols-3">
          <Card v-for="room in groups.live" :key="room.id" hoverable size="small">
            <div class="truncate font-medium">{{ room.title }}</div>
            <div class="mt-1 text-xs text-gray-500">
              {{ room.lecturerName || '—' }} · 在线 {{ room.viewerCount ?? 0 }} 人
            </div>
            <div class="mt-3 text-right">
              <Button type="primary" size="small" @click="openWatch(room, 'live')">
                进入直播间
              </Button>
            </div>
          </Card>
        </div>
        <Empty v-else description="暂无进行中的直播" />
      </div>

      <div class="mb-6">
        <div class="mb-2 text-base font-medium">即将开始</div>
        <div v-if="groups.upcoming?.length" class="grid gap-4 md:grid-cols-3">
          <Card v-for="room in groups.upcoming" :key="room.id" size="small">
            <div class="truncate font-medium">{{ room.title }}</div>
            <div class="mt-1 text-xs text-gray-500">
              {{ room.planStartAt || '待定' }} 开始 ·
              {{ room.lecturerName || '—' }}
            </div>
          </Card>
        </div>
        <Empty v-else description="暂无预告直播" />
      </div>

      <div>
        <div class="mb-2 text-base font-medium">精彩回放</div>
        <div v-if="groups.ended?.length" class="grid gap-4 md:grid-cols-3">
          <Card v-for="room in groups.ended" :key="room.id" size="small">
            <div class="truncate font-medium">{{ room.title }}</div>
            <div class="mt-1 text-xs text-gray-500">
              {{ room.lecturerName || '—' }} · {{ room.creditHours ?? 0 }} 学时
            </div>
            <div class="mt-3 text-right">
              <Button
                v-if="room.canReplay"
                size="small"
                @click="openWatch(room, 'replay')"
              >
                观看回放
              </Button>
              <Tag v-else>回放生成中</Tag>
            </div>
          </Card>
        </div>
        <Empty v-else description="暂无回放" />
      </div>
    </template>

    <Modal
      v-model:open="watchVisible"
      :title="watching?.title || '直播间'"
      :width="900"
      :footer="null"
      @cancel="closeWatch"
    >
      <div class="grid gap-3 md:grid-cols-[1fr_260px]">
        <div>
          <video
            :src="playUrl"
            controls
            autoplay
            class="w-full rounded bg-black"
            style="max-height: 50vh"
          />
          <div class="mt-2 text-xs text-gray-500">
            已记录有效学习 {{ validSeconds }} 秒，离开页面将自动结束会话并结算学时。
          </div>
        </div>
        <div class="flex h-full flex-col rounded border">
          <div class="border-b px-3 py-2 text-sm font-medium">弹幕互动</div>
          <div class="flex-1 overflow-y-auto px-3 py-2" style="height: 320px">
            <div
              v-for="item in danmakuList"
              :key="item.id"
              class="mb-1 text-xs text-gray-600"
            >
              <span class="text-blue-600">{{ item.userName }}：</span>
              {{ item.content }}
            </div>
            <Empty
              v-if="!danmakuList.length"
              :image="undefined"
              description="暂无弹幕"
            />
          </div>
          <div class="flex gap-2 border-t p-2">
            <Input
              v-model:value="danmakuText"
              size="small"
              placeholder="说点什么..."
              :maxlength="200"
              @press-enter="handleSendDanmaku"
            />
            <Button size="small" type="primary" @click="handleSendDanmaku">
              发送
            </Button>
          </div>
        </div>
      </div>
    </Modal>
  </Page>
</template>
