<!--
  官方 CA 互认签章 · 调起工作台（CaShareLaunch）
  契约：《后端变更通知-CA签章对接官方口径-2026-09-21》§二 调起参数 / §三 回调 / §四 避坑 / §五 待办。

  职责边界（与 CaSealAuth 严格区分，勿混）：
    · CaSealAuth    = 走本模块 /bidding/ca/seal/**（本地测试签章、盖章落台账）
    · CaShareLaunch = 走官方 /bidding/ca/share/**（对接官方 CA 互认签章工具/组件，官方固定格式）

  本组件做契约 §五 的前端 4 件事：
    1) 按 §二 构造调起参数 —— 密钥由后端签发（前端不硬编码 clientSecret/accessKeySecret）；
    2) 给出回调地址（H5 / 客户端两个不同地址，切换展示、可复制）；
    3) 调起前硬性校验（官方明示：文件必须关闭；退出 360/腾讯管家等安全软件）；
    4) 「签名前先登记」提示 + 回调链路连通性自测（发空 document 官方报文，不落库、不写脏数据）。

  端点状态：POST /bidding/ca/share/launch-param **已落地**（契约 §8.1，后端提交 c1388c02f）——
    请求按官方字段名（awaitingSignatureFilePath / awaitingSignatureFileName），密钥由服务端拼装、不进前端源码。
-->
<script lang="ts" setup>
import { computed, ref, watch } from 'vue';

import { message } from 'ant-design-vue';

import {
  caShareCallbackUrl,
  fetchCaShareLaunchParam,
  probeCaShareCallback,
  type CaShareCallbackType,
  type CaShareLaunchParam,
} from '#/api/bidding/ca';

const props = withDefaults(
  defineProps<{
    /** 业务编号（仅展示，便于对账） */
    business?: string;
    /** 待签章文件（fileId 必填；filePath 为本地/网络路径，缺省用 url） */
    file?: { fileId?: number | string; fileName?: string; filePath?: string; url?: string };
    /** 隐藏自带触发按钮（页面用自己的按钮样式时） */
    hideTrigger?: boolean;
    /** 挂载按钮文案 */
    label?: string;
    /** 受控打开 */
    open?: boolean;
  }>(),
  { hideTrigger: false, label: '官方 CA 签章', open: false },
);

const emit = defineEmits<{ 'update:open': [v: boolean] }>();

const open = ref(props.open);
/** 回调类型：H5 组件 vs 客户端工具 —— 结构不同、回调地址也不同，不可混用（契约 §五-5） */
const callbackType = ref<CaShareCallbackType>('h5');
const param = ref<CaShareLaunchParam | null>(null);
const paramError = ref('');
const loading = ref(false);
const probing = ref(false);
/** 调起前硬性校验（官方明示，两项都要用户确认） */
const fileClosed = ref(false);
const avExited = ref(false);
const precheckPassed = computed(() => fileClosed.value && avExited.value);

const callbackUrl = computed(() => caShareCallbackUrl(callbackType.value));
const fileName = computed(
  () => props.file?.fileName || props.file?.filePath || props.file?.url || '待签章文件',
);
const canLaunch = computed(() => !!props.file?.fileId);

/** 签发调起参数（密钥由后端填充；后端未实现端点时降级提示，不阻断其他功能） */
async function loadParam(): Promise<void> {
  paramError.value = '';
  param.value = null;
  if (!canLaunch.value) {
    paramError.value =
      '缺少 fileId：请先用 /bidding/file/upload 上传待签章文件（契约 §五-4：签名前必须先登记）';
    return;
  }
  loading.value = true;
  try {
    // 字段名照抄后端 CaShareLaunchReqVO（官方那套）：发 fileId/filePath 会 code=400，
    // fieldErrors 会点名 awaitingSignatureFilePath/Name 两个必填（2026-09-21 实测）
    param.value = await fetchCaShareLaunchParam({
      awaitingSignatureFilePath:
        props.file?.filePath || props.file?.url || fileName.value,
      awaitingSignatureFileName: fileName.value,
      // 签章后路径/文件名与 fileType 一律不猜：后端按原名派生 _sealed、fileType 默认 pdf
    });
  } catch (e) {
    const msg = e instanceof Error ? e.message : String(e);
    paramError.value = /404|不存在|Not Found/i.test(msg)
      ? '调起参数签发端点不可达（POST /bidding/ca/share/launch-param）—— 请确认 bidding 服务已启动、本账号已登录。为避免在前端硬编码 clientSecret / accessKeySecret，本组件不自行拼装密钥；回调地址与连通性自测仍可用。'
      : '签发调起参数失败：' + msg;
  } finally {
    loading.value = false;
  }
}

async function copyText(text: string, tip: string): Promise<void> {
  try {
    await navigator.clipboard?.writeText(text);
    message.success(tip);
  } catch {
    message.warning('复制失败，请手动选择文本复制');
  }
}

/** 回调链路连通性自测：发空 document 官方报文（不落库），验证"无 token/无租户头也进得来" */
async function probeCallback(): Promise<void> {
  probing.value = true;
  try {
    const res = await probeCaShareCallback(callbackType.value);
    if (res?.success === true || res?.code === 200) {
      message.success('回调链路正常：' + (res.msg || '官方结构响应 200'));
    } else {
      message.warning('回调返回异常：' + JSON.stringify(res));
    }
  } catch (e) {
    message.error('回调自测失败：' + (e instanceof Error ? e.message : String(e)));
  } finally {
    probing.value = false;
  }
}

/* ==================== 真正调起官方工具（依据 doc07 §5.3.2 官方 C# 示例） ====================
 * 官方示例（C#）三步：
 *   1) explorer "CAShareDetectInstallTool://websocket?flag=0"  ← 自定义 URL 协议唤起检测安装工具
 *   2) Task.Delay(2s)                                          ← 等工具起来
 *   3) WebSocket 连 ws://localhost:12345 → Send(JSON)          ← 把 §5.3.1 的入参 JSON 发过去
 * 浏览器侧同构实现：location.href 触协议 + setTimeout 2s + new WebSocket 发送。
 * 注意：
 *   · https 页面连 ws://localhost 属混合内容，但 localhost 被浏览器视为安全上下文，一般放行；
 *   · 若走官方 H5 签章组件（SealWorkbench 装载的那套），唤起由组件内部完成，不需要本函数。 */
const CA_SHARE_PROTOCOL = 'CAShareDetectInstallTool://websocket?flag=0';
const CA_SHARE_WS_URL = 'ws://localhost:12345';
const launching = ref(false);
const launchLog = ref<string[]>([]);

function log(line: string): void {
  launchLog.value = [...launchLog.value, line];
}

/** 打开 WebSocket 把参数发给本地工具（2 秒等待与官方示例一致） */
function sendParamViaWebSocket(json: string): Promise<void> {
  return new Promise((resolve, reject) => {
    let ws: WebSocket;
    try {
      ws = new WebSocket(CA_SHARE_WS_URL);
    } catch (e) {
      reject(new Error('无法建立 WebSocket：' + (e instanceof Error ? e.message : String(e))));
      return;
    }
    const timer = setTimeout(() => {
      try { ws.close(); } catch { /* ignore */ }
      reject(new Error('连接超时（' + CA_SHARE_WS_URL + '）——本地多半未安装 CA 互认检测安装工具'));
    }, 8000);
    ws.onopen = () => {
      log('已连接 ' + CA_SHARE_WS_URL + '，发送调起参数…');
      ws.send(json);
      clearTimeout(timer);
      setTimeout(() => { try { ws.close(); } catch { /* ignore */ } resolve(); }, 500);
    };
    ws.onerror = () => {
      clearTimeout(timer);
      reject(new Error('连接失败（' + CA_SHARE_WS_URL + '）——本地多半未安装 CA 互认检测安装工具；可改用官方 H5 组件路线'));
    };
  });
}

/** 调起官方检测安装工具（需先通过调起前校验） */
async function launchOfficialTool(): Promise<void> {
  if (!precheckPassed.value) {
    message.warning('请先完成调起前两项确认（文件已关闭 / 已退出安全软件）');
    return;
  }
  if (!param.value) {
    message.warning('调起参数尚未签发，无法调起');
    return;
  }
  launching.value = true;
  launchLog.value = [];
  try {
    log('① 触自定义协议唤起检测安装工具：' + CA_SHARE_PROTOCOL);
    globalThis.location.href = CA_SHARE_PROTOCOL;
    log('② 等待 2 秒（与官方示例一致）…');
    await new Promise((r) => setTimeout(r, 2000));
    log('③ 建立 WebSocket 并下发参数…');
    await sendParamViaWebSocket(JSON.stringify(param.value));
    log('✅ 参数已下发：请在官方工具内完成后续签章操作');
    message.success('已调起官方工具并下发参数');
  } catch (e) {
    const msg = e instanceof Error ? e.message : String(e);
    log('❌ ' + msg);
    message.warning(msg);
  } finally {
    launching.value = false;
  }
}

function openWorkbench(): void {
  open.value = true;
  emit('update:open', true);
  void loadParam();
}

watch(
  () => props.open,
  (v) => {
    open.value = v;
    if (v) void loadParam();
  },
);
watch(callbackType, () => {
  // 换回调类型后参数里的 callBackUrl 也要跟着变，重新签发
  void loadParam();
});
</script>

<template>
  <a-button v-if="!hideTrigger" type="link" size="small" @click.stop="openWorkbench">
    {{ label }}
  </a-button>

  <a-modal
    :open="open"
    :width="760"
    :footer="null"
    destroy-on-close
    title="官方 CA 互认签章 · 调起"
    @update:open="(v: boolean) => { open = v; emit('update:open', v) }"
  >
    <a-alert
      class="csl-mb"
      type="info"
      show-icon
      message="本工作台对接官方 CA 互认签章工具/组件（/bidding/ca/share/**）；与本地测试签章（CaSealAuth，/ca/seal/**）是两套口径，不要混用。"
    />

    <!-- 1) 调起前硬性校验（官方明示） -->
    <a-card size="small" class="csl-mb" title="调起前必查（官方要求）">
      <a-checkbox v-model:checked="fileClosed">
        待签章文件已关闭（官方明示：文件必须处于关闭状态才能调起检测安装工具）
      </a-checkbox>
      <div class="csl-mt">
        <a-checkbox v-model:checked="avExited">
          已退出 360 / 腾讯管家等安全软件（否则注册表写入被拦，属客户端环境问题）
        </a-checkbox>
      </div>
      <a-alert
        v-if="!precheckPassed"
        class="csl-mt"
        type="warning"
        show-icon
        message="两项都确认后再调起，否则官方工具安装/注册会失败"
      />
    </a-card>

    <!-- 2) 回调地址（H5 与客户端不同，可切换复制） -->
    <a-card size="small" class="csl-mb" title="回调地址（填进 tradingSystemConfig.callBackUrl）">
      <a-radio-group v-model:value="callbackType" button-style="solid" size="small">
        <a-radio-button value="h5">H5 组件回调</a-radio-button>
        <a-radio-button value="client">客户端工具回调</a-radio-button>
      </a-radio-group>
      <div class="csl-url">
        <code class="csl-code">{{ callbackUrl }}</code>
        <a-button size="small" @click="copyText(callbackUrl, '回调地址已复制')">复制</a-button>
      </div>
      <div class="csl-hint">
        两者结构不同、地址也不同 —— 官方组件不带 token、也不带 tenant-id，直接 POST 即通。
      </div>
      <a-button size="small" :loading="probing" class="csl-mt" @click="probeCallback">
        回调链路自测（发空 document，不落库）
      </a-button>
    </a-card>

    <!-- 3) 调起参数（密钥由后端签发，前端不硬编码） -->
    <a-card size="small" class="csl-mb" title="调起参数（官方 §5.3.1 结构）">
      <a-alert v-if="paramError" class="csl-mb" type="warning" show-icon :message="paramError" />
      <a-spin :spinning="loading">
        <template v-if="param">
          <pre class="csl-json">{{ JSON.stringify(param, null, 2) }}</pre>
          <div class="csl-act">
            <a-button
              type="primary"
              size="small"
              :disabled="!precheckPassed"
              @click="copyText(JSON.stringify(param), '调起参数已复制，交给官方检测安装工具')"
            >
              复制调起参数
            </a-button>
            <a-button size="small" @click="loadParam">重新签发</a-button>
            <a-button
              type="primary"
              size="small"
              :loading="launching"
              :disabled="!precheckPassed || !param"
              @click="launchOfficialTool"
            >
              调起官方工具
            </a-button>
            <span class="csl-hint">
              ⚠️ CAShareUseScene（大驼峰）、callBackUrl（大写 B）为官方原文字段名，勿改
            </span>
          </div>
          <pre v-if="launchLog.length" class="csl-json csl-log">{{ launchLog.join('\n') }}</pre>
        </template>
      </a-spin>
    </a-card>

    <!-- 4) 签名前先登记（否则官方回调回来匹配不到台账） -->
    <a-alert
      type="warning"
      show-icon
      message="签名前必须先登记台账：官方回调按「待签文件 Hash」匹配 bidding_ca_seal；未登记则回调匹配不到，后端记 warning 并跳过（签章结果静默丢失）。"
    />
  </a-modal>
</template>

<style scoped>
.csl-mb { margin-bottom: 12px; display: block; }
.csl-mt { margin-top: 8px; }
.csl-act { display: flex; align-items: center; gap: 10px; margin-top: 10px; flex-wrap: wrap; }
.csl-hint { font-size: 12px; color: #8a949e; line-height: 1.6; }
.csl-code { word-break: break-all; background: #f5f7fa; padding: 2px 6px; border-radius: 4px; font-size: 12px; }
.csl-url { display: flex; align-items: center; gap: 8px; margin-top: 10px; }
.csl-log { margin-top: 10px; max-height: 160px; background: #1f2937; color: #d1fae5; }
.csl-json { max-height: 320px; overflow: auto; background: #f8fafc; border: 1px solid #eef1f5; border-radius: 6px; padding: 10px; font-size: 12px; line-height: 1.6; }
</style>
