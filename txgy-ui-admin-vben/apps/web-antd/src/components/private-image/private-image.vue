<script lang="ts" setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue';

import { Image, Spin } from 'ant-design-vue';

import {
  isPrivateImageUrl,
  loadPrivateImageObjectUrl,
  revokeObjectUrl,
} from '#/utils/private-image';

/**
 * 私有图片展示组件。
 *
 * 私有资源（售后凭证等）的读接口要求后台登录态，`<img src>` 无法携带 Authorization，
 * 因此这里带 token 取回内容后以 `blob:` 地址渲染，并在卸载/换图时释放。
 * 非本平台文件服务的地址（OSS 直链等）按原地址直接渲染。
 */
const props = withDefaults(
  defineProps<{
    height?: number | string;
    src?: null | string;
    width?: number | string;
  }>(),
  {
    src: '',
    width: 60,
    height: 60,
  },
);

const objectUrl = ref('');
const failed = ref(false);
let currentObjectUrl = '';
let loadToken = 0;

const displayUrl = computed(() => objectUrl.value || String(props.src ?? ''));

function release() {
  revokeObjectUrl(currentObjectUrl);
  currentObjectUrl = '';
  objectUrl.value = '';
}

async function refresh(url?: null | string) {
  const token = ++loadToken;
  failed.value = false;
  release();
  const value = String(url ?? '').trim();
  if (!value || !isPrivateImageUrl(value)) {
    return;
  }
  try {
    const loaded = await loadPrivateImageObjectUrl(value);
    if (token !== loadToken) {
      revokeObjectUrl(loaded);
      return;
    }
    currentObjectUrl = loaded;
    objectUrl.value = loaded;
  } catch {
    if (token === loadToken) {
      failed.value = true;
    }
  }
}

watch(() => props.src, refresh, { immediate: true });

onBeforeUnmount(() => {
  loadToken++;
  release();
});
</script>

<template>
  <span v-if="failed" class="private-image-failed">凭证图片加载失败</span>
  <Spin v-else-if="isPrivateImageUrl(src) && !objectUrl" size="small" />
  <Image
    v-else
    :src="displayUrl"
    :width="width"
    :height="height"
    :preview="{ src: displayUrl }"
  />
</template>

<style scoped>
.private-image-failed {
  color: #dc2626;
  font-size: 12px;
}
</style>
