<script lang="ts" setup>
import { onMounted, onUnmounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { Page } from '@vben/common-ui';

import { Button, Card, Input, Modal, Progress, Tag, message } from 'ant-design-vue';

import {
  checkinSession,
  endSession,
  getCourseDetail,
  getSessionConfig,
  heartbeat,
  reportDocPage,
  startSession,
  verifySession,
} from './api';

defineOptions({ name: 'PortalTrainingStudy' });

const route = useRoute();
const router = useRouter();

const courseId = Number(route.query.courseId);
const mediaId = Number(route.query.mediaId);
const mediaType = String(route.query.mediaType || '');
const title = String(route.query.title || '在线学习');

const loading = ref(false);
const sessionId = ref<number | undefined>();
const validSeconds = ref(0);
const canCheckin = ref(false);
const warnInvalid = ref(false);
const finished = ref(false);
const finishTip = ref('');
const mediaUrl = ref('');
const progressPercent = ref(0);
const creditEarned = ref<number>(0);

// 文档翻页状态
const pageNo = ref(1);
const totalPages = ref(10);
const docStatus = ref('');

// 人机验证
const verifyVisible = ref(false);
const verifyQuestion = ref('');
const verifyAnswer = ref('');

let timer: ReturnType<typeof setInterval> | undefined;
let heartbeatMs = 5000;

async function initSession() {
  loading.value = true;
  try {
    // 1. 读取媒资地址
    const detail = await getCourseDetail(courseId);
    const item = (detail?.medias ?? []).find((m) => m.mediaId === mediaId);
    mediaUrl.value = item?.url ?? '';
    if (mediaType === 'document') {
      totalPages.value = item?.docTotalPages ?? 10;
    }
    // 2. 会话参数
    const cfg = await getSessionConfig();
    heartbeatMs = (cfg?.heartbeat_interval_seconds ?? 5) * 1000;
    // 3. 开启会话
    const session = await startSession({ courseId, mediaId, mediaType });
    sessionId.value = session?.sessionId;
    if (!sessionId.value) {
      message.error('学习会话开启失败');
      return;
    }
    timer = setInterval(sendHeartbeat, heartbeatMs);
  } catch (error: any) {
    message.error(error?.message ?? '学习会话开启失败');
  } finally {
    loading.value = false;
  }
}

async function sendHeartbeat() {
  if (!sessionId.value || finished.value) return;
  if (verifyVisible.value) return; // 验证期间暂停上报
  try {
    const res =
      mediaType === 'document'
        ? await reportDocPage({
            sessionId: sessionId.value,
            pageNo: pageNo.value,
            totalPages: totalPages.value,
          })
        : await heartbeat({ sessionId: sessionId.value });
    if (!res) return;
    validSeconds.value = res.validSeconds ?? validSeconds.value;
    canCheckin.value = !!res.canCheckin;
    warnInvalid.value = !!res.warnInvalid;
    if (mediaType === 'document') {
      docStatus.value = `已覆盖 ${res.docCoveredPages ?? 0}/${res.docTotalPages ?? totalPages.value} 页`;
    }
    if (res.needVerify) {
      verifyQuestion.value = res.challengeQuestion ?? '';
      verifyAnswer.value = '';
      verifyVisible.value = true;
      return;
    }
    if (res.status && res.status !== 'active') {
      stopSession(res.status, res.reason);
    }
  } catch (error: any) {
    message.warning(error?.message ?? '心跳上报失败');
  }
}

async function submitVerify() {
  if (!sessionId.value) return;
  try {
    await verifySession({ sessionId: sessionId.value, answer: verifyAnswer.value });
    verifyVisible.value = false;
    message.success('验证通过，继续计时');
  } catch (error: any) {
    message.error(error?.message ?? '验证失败');
  }
}

function applyEndResult(res: any) {
  validSeconds.value = res?.validSeconds ?? validSeconds.value;
  progressPercent.value = Number(res?.progressPercent ?? progressPercent.value);
  creditEarned.value = Number(res?.creditEarned ?? creditEarned.value);
}

function stopSession(status: string, reason?: string) {
  finished.value = true;
  if (timer) {
    clearInterval(timer);
    timer = undefined;
  }
  finishTip.value =
    status === 'invalid'
      ? `本次学习判定无效（${reason ?? '异常'}），不计入学时`
      : '本次学习已结束，进度已保存';
}

async function handleEnd() {
  if (!sessionId.value || finished.value) return;
  const res = await endSession(sessionId.value);
  applyEndResult(res);
  stopSession('ended', 'normal');
}

async function handleCheckin() {
  if (!sessionId.value || finished.value) return;
  const res = await checkinSession(sessionId.value);
  applyEndResult(res);
  stopSession('ended', 'checkin');
  message.success('打卡成功');
}

function handlePageChange(delta: number) {
  const next = pageNo.value + delta;
  if (next < 1 || next > totalPages.value) return;
  pageNo.value = next;
}

onMounted(initSession);

onUnmounted(() => {
  if (timer) {
    clearInterval(timer);
    timer = undefined;
  }
  if (sessionId.value && !finished.value) {
    endSession(sessionId.value).catch(() => undefined);
  }
});

function goBack() {
  router.push('/portal/training/learning');
}
</script>

<template>
  <Page auto-content-height>
    <Card :title="title" size="small">
      <template #extra>
        <Tag color="blue">
          {{ mediaType === 'document' ? '文档学习' : '视频学习' }}
        </Tag>
        <span class="mr-3 text-sm text-gray-500">
          有效学习 {{ validSeconds }} 秒
        </span>
        <Button v-if="canCheckin && !finished" type="primary" ghost class="mr-2" @click="handleCheckin">
          学习打卡
        </Button>
        <Button v-if="!finished" danger class="mr-2" @click="handleEnd">结束学习</Button>
        <Button @click="goBack">返回</Button>
      </template>

      <div v-if="warnInvalid" class="mb-2 text-xs text-orange-500">
        检测到心跳间隔异常，请保持页面在前台，本次可能不计入有效学习时长。
      </div>

      <!-- 视频学习 -->
      <div v-if="mediaType === 'video'" class="flex flex-col gap-3">
        <video
          v-if="mediaUrl"
          :src="mediaUrl"
          controls
          class="w-full rounded bg-black"
          style="max-height: 60vh"
        />
        <div v-else class="rounded bg-gray-100 py-16 text-center text-gray-400">
          该视频暂无可播放地址，请联系管理员配置媒资 URL
        </div>
      </div>

      <!-- 文档学习 -->
      <div v-else-if="mediaType === 'document'" class="flex flex-col gap-3">
        <div class="flex flex-wrap items-center gap-3">
          <Button size="small" :disabled="pageNo <= 1" @click="handlePageChange(-1)">
            上一页
          </Button>
          <span class="text-sm">第 {{ pageNo }} / {{ totalPages }} 页</span>
          <Button
            size="small"
            :disabled="pageNo >= totalPages"
            @click="handlePageChange(1)"
          >
            下一页
          </Button>
          <span class="text-xs text-gray-500">{{ docStatus }}</span>
          <span class="ml-auto text-xs text-gray-400">
            单页停留满 5 秒才计入已覆盖页数，覆盖 80% 即完成
          </span>
        </div>
        <iframe
          v-if="mediaUrl"
          :src="mediaUrl"
          class="min-h-[60vh] w-full rounded border"
        />
        <div v-else class="rounded bg-gray-100 py-16 text-center text-gray-400">
          该文档暂无可预览地址，请联系管理员配置媒资 URL
        </div>
      </div>

      <div v-else class="rounded bg-gray-100 py-16 text-center text-gray-400">
        不支持的媒资类型
      </div>

      <div class="mt-3 text-xs text-gray-400">
        学习过程中会定时进行人机验证，验证期间暂停计时；连续答错 3 次本次会话判无效。
      </div>
    </Card>

    <Modal
      v-model:open="verifyVisible"
      title="学习防挂机验证"
      :width="420"
      ok-text="提交"
      cancel-text="取消"
      :closable="false"
      :mask-closable="false"
      @ok="submitVerify"
    >
      <div class="py-3">
        <div class="mb-2 text-base">请计算：{{ verifyQuestion }}</div>
        <Input v-model:value="verifyAnswer" placeholder="请输入答案" />
      </div>
    </Modal>

    <Modal
      v-model:open="finished"
      title="学习结束"
      :width="420"
      :footer="null"
    >
      <div class="py-3">
        <div class="mb-2">{{ finishTip }}</div>
        <div class="text-sm text-gray-500">
          有效学习 {{ validSeconds }} 秒 · 进度 {{ progressPercent }}% · 已获
          {{ creditEarned }} 学时
        </div>
        <Progress class="mt-3" :percent="progressPercent" size="small" />
        <div class="mt-4 text-right">
          <Button type="primary" @click="goBack">返回我的学习</Button>
        </div>
      </div>
    </Modal>
  </Page>
</template>
