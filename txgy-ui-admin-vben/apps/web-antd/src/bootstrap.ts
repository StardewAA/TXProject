import Antd from 'ant-design-vue';
import { createApp, watchEffect } from 'vue';
import VueDOMPurifyHTML from 'vue-dompurify-html';

import { registerAccessDirective } from '@vben/access';
import { registerLoadingDirective } from '@vben/common-ui/es/loading';
import { preferences } from '@vben/preferences';
import { initStores } from '@vben/stores';
import '@vben/styles';
import '@vben/styles/antd';

import { useTitle } from '@vueuse/core';

import { $t, setupI18n } from '#/locales';
import { setupFormCreate } from '#/plugins/form-create';

import { initComponentAdapter } from './adapter/component';
import { initSetupVbenForm } from './adapter/form';
import App from './app.vue';
import { router } from './router';

async function bootstrap(namespace: string) {
  // 初始化组件适配器
  await initComponentAdapter();

  // 初始化表单组件
  await initSetupVbenForm();

  // // 设置弹窗的默认配置
  // setDefaultModalProps({
  //   fullscreenButton: false,
  // });
  // // 设置抽屉的默认配置
  // setDefaultDrawerProps({
  //   zIndex: 1020,
  // });

  const app = createApp(App);
  // DOMPurify 全局净化配置：放开**文字排版类** style 属性（font/颜色/对齐/装饰/行高/段落留白），
  // 让粘贴的富文本（Word/网页，含 宋体/字号 等内联样式）在公告正文/预览中保留预期样式；
  // 其余 style 继续一律剥除（DOMPurify 对 url(/javascript:) 等危险值本身会拦截，不构成 XSS 面）。
  app.use(VueDOMPurifyHTML, {
    ALLOWED_ATTR: ['style'],
    ALLOWED_STYLE_PROPERTIES: [
      'font-family', 'font-size', 'font-weight', 'font-style', 'text-decoration',
      'color', 'background-color', 'text-align', 'line-height', 'text-indent',
      'margin', 'padding', 'border', 'border-top', 'border-bottom', 'border-left', 'border-right',
    ],
  });
  // 全局注册 ant-design-vue 全量组件（AButton/APagination/AEmpty … 用 kebab 写法 <a-xxx> 即可解析）。
  // 背景：本项目未装 unplugin-vue-components，此前只有 plugins/form-create 注册了一批子集，
  // 清单外组件（a-pagination / a-empty / a-progress / a-drawer / a-descriptions / a-space / a-spin …）
  // 用 kebab 写法会 “Failed to resolve component”，2026-09-20 起改为全量注册，一次修掉该类问题。
  // 注意：不要引入 ant-design-vue/dist/reset.css —— 样式复位已由 @vben/styles/antd 提供，重复引入会与 Vben 主题冲突。
  app.use(Antd);

  // 注册v-loading指令
  registerLoadingDirective(app, {
    loading: 'loading', // 在这里可以自定义指令名称，也可以明确提供false表示不注册这个指令
    spinning: 'spinning',
  });

  // 国际化 i18n 配置
  await setupI18n(app);

  // 配置 pinia-store
  await initStores(app, { namespace });

  // 安装权限指令
  registerAccessDirective(app);

  // 初始化 tippy
  const { initTippy } = await import('@vben/common-ui/es/tippy');
  initTippy(app);

  // 配置路由及路由守卫
  app.use(router);

  // formCreate
  setupFormCreate(app);

  // 配置Motion插件
  const { MotionPlugin } = await import('@vben/plugins/motion');
  app.use(MotionPlugin);

  // 动态更新标题
  watchEffect(() => {
    if (preferences.app.dynamicTitle) {
      const routeTitle = router.currentRoute.value.meta?.title;
      const pageTitle =
        (routeTitle ? `${$t(routeTitle)} - ` : '') + preferences.app.name;
      useTitle(pageTitle);
    }
  });

  app.mount('#app');
}

export { bootstrap };
