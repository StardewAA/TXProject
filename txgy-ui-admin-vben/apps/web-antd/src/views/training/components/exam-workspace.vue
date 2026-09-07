<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { onBeforeRouteLeave } from 'vue-router';
import { useAccessStore } from '@vben/stores';
import { requestClient } from '#/api/request';

const props = withDefaults(defineProps<{ mode?: 'admin' | 'learner'; initialPage?: string }>(), { mode: 'admin', initialPage: '' });
const emit = defineEmits<{ active: [value: boolean] }>();
const accessStore = useAccessStore();
const frame = ref<HTMLIFrameElement>();
const active = ref(false);
let observer: MutationObserver | undefined;
let refreshing = false;
function sendSession() {
  frame.value?.contentWindow?.postMessage({
    type: 'training-exam:session', token: accessStore.accessToken,
    tenantId: accessStore.tenantId,
    dark: document.documentElement.classList.contains('dark'),
  }, window.location.origin);
}
async function receive(event: MessageEvent) {
  if (event.origin !== window.location.origin || event.source !== frame.value?.contentWindow) return;
  if (event.data?.type === 'training-exam:ready') sendSession();
  if (event.data?.type === 'training-exam:attempt') {
    active.value = !!event.data.active;
    emit('active', active.value);
  }
  if (event.data?.type === 'training-exam:expired' && !refreshing) {
    refreshing = true;
    try { await requestClient.get('/system/auth/get-permission-info'); sendSession(); }
    finally { refreshing = false; }
  }
}
onMounted(() => {
  window.addEventListener('message', receive);
  observer = new MutationObserver(sendSession);
  observer.observe(document.documentElement, { attributes: true, attributeFilter: ['class'] });
});
watch(() => [accessStore.accessToken, accessStore.tenantId], sendSession);
onBeforeRouteLeave(() => !active.value || window.confirm('考试正在进行，离开后计时不会暂停。确定离开吗？'));
onBeforeUnmount(() => { window.removeEventListener('message', receive); observer?.disconnect(); });
</script>
<template>
  <iframe ref="frame" :src="`/training-exam/?mode=${props.mode}&page=${props.initialPage}`" title="考试与认证" class="exam-frame" @load="sendSession" />
</template>
<style scoped>
.exam-frame { display: block; width: 100%; height: calc(100vh - 170px); min-height: 700px; border: 0; border-radius: 10px; }
</style>
