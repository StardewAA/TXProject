import { ref } from 'vue';

/** AI 政策助手浮层的跨组件共享状态（页面 CTA 与全局悬浮框共用）。 */
const visible = ref(false);

export function useAiAssistant() {
  function close() {
    visible.value = false;
  }

  function open() {
    visible.value = true;
  }

  function toggle() {
    visible.value = !visible.value;
  }

  return { close, open, toggle, visible };
}