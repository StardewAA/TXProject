import './offline-icons';
import './bid-indust.css';
import './styles/portal-dark.css';
import { initPreferences, updatePreferences } from '@vben/preferences';
import { unmountGlobalLoading } from '@vben/utils';

import {
  APP_HOME_PATH,
  overridesPreferences,
  preferencesExtension,
} from './preferences';

/**
 * 应用初始化完成之后再进行页面加载渲染
 */
async function initApplication() {
  // name用于指定项目唯一标识
  // 用于区分不同项目的偏好设置以及存储数据的key前缀以及其他一些需要隔离的数据
  const env = import.meta.env.PROD ? 'prod' : 'dev';
  const appVersion = import.meta.env.VITE_APP_VERSION;
  // VITE_APP_NAMESPACE 未配置时使用默认值，避免存储 key 前缀变成 undefined-*
  // （前缀为 undefined 会导致重开浏览器后登录态无法恢复、菜单空白）
  const appNamespace = import.meta.env.VITE_APP_NAMESPACE || 'vben-web-antd';
  const namespace = `${appNamespace}-${appVersion}-${env}`;

  // app偏好设置初始化
  await initPreferences({
    extension: preferencesExtension,
    namespace,
    overrides: overridesPreferences,
  });

  // 登录后落地页以代码为准：偏好设置会被浏览器缓存，而缓存的优先级高于代码默认值，
  // 老浏览器里可能残留 /wms/ai-center、/dashboard/analytics 这类旧落地页，
  // 统一强制覆盖为「概览 → AI首页」。
  updatePreferences({ app: { defaultHomePath: APP_HOME_PATH } });

  // 主题一次性迁移（问题 1）：旧版本默认把主题固定成 light/dark，老浏览器缓存里留着固定值，
  // 会出现「系统是暗色、页面还是浅色」的割裂感。这里只做**一次**迁移，把主题恢复为
  // 「跟随系统」（auto）；迁移后用户仍可在右上角偏好设置里自行选择并生效。
  const themeMigrationKey = 'txgy-theme-mode-migrated-v1';
  try {
    if (!globalThis.localStorage.getItem(themeMigrationKey)) {
      updatePreferences({ theme: { mode: 'auto' } });
      globalThis.localStorage.setItem(themeMigrationKey, '1');
    }
  } catch {
    // 隐私模式下 localStorage 不可用时忽略，默认值本身已是 auto
  }

  // 启动应用并挂载
  // vue应用主要逻辑及视图
  const { bootstrap } = await import('./bootstrap');
  await bootstrap(namespace);

  // 移除并销毁loading
  unmountGlobalLoading();
}

initApplication();
