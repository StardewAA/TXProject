<!--
  CA 扫码二维码（CaQrCode）—— 统一「后端下发真图 / 测试态本地渲染」两条路，供扫码登录·签名·解密三通道复用。

  口径（用户裁定 2026-09-20）：
    · 真实 SDK 模式（bidding.ca.sdk.enabled=true）：后端 qrCodeImageBase64 来自官方 CA 互认共享组件，直接展示；
    · 测试模式（=false，未取得官方 key、无法真实扫码）：后端占位实现只下发 tid，没有二维码图片。
      此时用 @vben/common-ui 的 Barcode 组件，按同一个 CA 互认跳转地址本地渲染真实可扫二维码，
      让测试流程与真实流程在观感上完全一致；「扫码」这一动作在测试态由「模拟已扫码」按钮代替
      （见 CaSealAuth / CaSignEntry）。

  为什么不用占位方块：用户口径是「把测试的东西抄得越像真的越好」——二维码要真的能扫得出来，
  只是扫了也没有官方通道应答，故用真实二维码渲染 + 手动推进状态。
-->
<script lang="ts" setup>
import { computed } from 'vue';

import { Barcode } from '@vben/common-ui';

const props = withDefaults(
  defineProps<{
    /** 二维码下方说明文字 */
    caption?: string;
    /** 摘要（hex），拼进跳转链接便于扫码方核对 */
    digest?: null | string;
    /** 后端下发的二维码图片（data URL / base64）；有则优先展示 */
    imageBase64?: null | string;
    /** 后端下发的跳转链接；无图片时用它本地渲染二维码 */
    jumpUrl?: null | string;
    /** 二维码边长（px） */
    size?: number;
    /** 会话号 tid（无 jumpUrl 时按官方同构地址拼一个可扫链接） */
    tid?: null | string;
  }>(),
  { size: 168 },
);

/** 官方 CA 互认共享组件域名；真实模式由后端签发完整链接，测试态按同构地址本地渲染 */
const CA_SHARE_ORIGIN = 'https://cashare.cebpubservice.com';
const qrContent = computed(() => {
  if (props.jumpUrl) return props.jumpUrl;
  const tid = encodeURIComponent(String(props.tid ?? ''));
  const digest = props.digest ? '&digest=' + encodeURIComponent(String(props.digest)) : '';
  return CA_SHARE_ORIGIN + '/h5/ca-share?tid=' + tid + digest;
});
</script>

<template>
  <div class="caqr">
    <img
      v-if="imageBase64"
      :src="imageBase64"
      alt="CA 二维码"
      class="caqr-img"
      :style="{ height: size + 'px', width: size + 'px' }"
    />
    <Barcode v-else :content="qrContent" :width="size" />
    <div class="caqr-tid">会话号 <code class="caqr-code">{{ tid || '—' }}</code></div>
    <div v-if="caption" class="caqr-cap">{{ caption }}</div>
  </div>
</template>

<style scoped>
.caqr { display: flex; flex-direction: column; align-items: center; gap: 6px; }
.caqr-img { border: 1px solid #e6eaef; border-radius: 6px; background: #fff; }
.caqr-tid { font-size: 12px; color: #8a949e; }
.caqr-code { word-break: break-all; background: #f5f7fa; padding: 2px 6px; border-radius: 4px; }
.caqr-cap { font-size: 12px; color: #8a949e; text-align: center; }
</style>
