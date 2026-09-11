<script setup lang="ts">
/* 证照验真（契约 §4.1 证照验真 / §4.2 文件验真）
 * · 渠道取字典 verify_channel，不传则由后端按证照类别选默认渠道
 * · 幂等带时间窗（cert.verify.idempotent.window.seconds）：窗口内重复请求返回同一条记录 → 按钮防抖 + 文案说明
 * · 文件验真前置：该证照已有 isCurrent=true 的文件版本 */
import { computed, ref } from 'vue';

import { message } from 'ant-design-vue';

import {
  certErrorMsg,
  type CertificateApi,
  verifyCertificate,
  verifyCertificateFile,
} from '#/api/bidding/certificate';

import { useCertMeta } from '../useCertMeta';

const props = defineProps<{
  canVerify?: boolean;
  cert?: CertificateApi.Certificate | null;
}>();
const emit = defineEmits<{ (e: 'changed'): void }>();

const { configNumber, dictOptions, dictTag } = useCertMeta();
const channel = ref<string | undefined>(undefined);
const running = ref(false);
const runningFile = ref(false);

const channels = computed(() => dictOptions('verify_channel'));
const windowSeconds = computed(() => configNumber('cert.verify.idempotent.window.seconds', 60));
const statusTag = computed(() => dictTag('verification_status', props.cert?.verificationStatus));

async function run(kind: 'cert' | 'file') {
  if (!props.cert?.id) return;
  if (kind === 'cert') running.value = true;
  else runningFile.value = true;
  try {
    const id =
      kind === 'cert'
        ? await verifyCertificate({ certId: props.cert.id, verifyChannel: channel.value })
        : await verifyCertificateFile({ certId: props.cert.id, verifyChannel: channel.value });
    message.success(`验真已受理（记录 #${id}）`);
    emit('changed');
  } catch (error: unknown) {
    // 契约 §4.1：未配置可用渠道 / 无当前文件等业务拒绝 → 直接展示后端 msg（后端已本地化）
    message.warning(certErrorMsg(error));
  } finally {
    running.value = false;
    runningFile.value = false;
  }
}
</script>

<template>
  <div class="ct-panel">
    <div class="ct-panel-h">
      <span class="ct-panel-t">验真</span>
      <span class="ct-panel-meta">向官方白名单渠道核验，结果与请求快照留档</span>
    </div>
    <div class="ct-panel-b">
      <a-descriptions :column="2" size="small" bordered>
        <a-descriptions-item label="验真状态">
          <a-tag :color="statusTag.color">{{ statusTag.label }}</a-tag>
        </a-descriptions-item>
        <a-descriptions-item label="最近验真">{{ cert?.lastVerifiedAt || '未验真' }}</a-descriptions-item>
        <a-descriptions-item label="验真渠道来源">{{ cert?.verifyApiSource || '—' }}</a-descriptions-item>
        <a-descriptions-item label="官方查验链接">
          <a v-if="cert?.officialVerifyUrl" :href="cert.officialVerifyUrl" target="_blank" rel="noreferrer">
            打开官方查验页
          </a>
          <span v-else>—</span>
        </a-descriptions-item>
      </a-descriptions>

      <div class="mt-3 flex flex-wrap items-center gap-2">
        <a-select
          v-model:value="channel"
          :options="channels"
          style="width: 200px"
          allow-clear
          placeholder="查验渠道（不选=后端默认）"
          :disabled="channels.length === 0"
        />
        <a-button type="primary" :loading="running" @click="run('cert')">发起证照验真</a-button>
        <a-button :loading="runningFile" @click="run('file')">文件验真（指纹核验）</a-button>
      </div>
      <div class="ct-tip">
        幂等窗口 {{ windowSeconds }} 秒：同证同渠道在窗口内重复提交返回同一条验真记录，按钮已做防抖；
        失败可直接重试（窗口内安全）。
        <span v-if="channels.length === 0">字典 verify_channel 为空时留空即可，由后端按证照类别选取渠道。</span>
      </div>
    </div>
  </div>
</template>
