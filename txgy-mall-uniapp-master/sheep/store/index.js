import { createPinia } from 'pinia';
import piniaPersist from 'pinia-plugin-persist-uni';

// 自动注入所有pinia模块
const files = import.meta.glob('./*.js', { eager: true });
const modules = {};
Object.keys(files).forEach((key) => {
  modules[key.replace(/(.*\/)*([^.]+).*/gi, '$2')] = files[key].default;
});

export const setupPinia = (app) => {
  const pinia = createPinia();
  pinia.use(piniaPersist);

  app.use(pinia);
  // Remove obsolete display caches, not tokens or any server-side business data.
  uni.removeStorageSync('user-store');
  uni.removeStorageSync('cart-store');
  uni.removeStorageSync('app-store');
  uni.$on('mall:session-changed', () => modules.user().clearAccountData());
  uni.$on('mall:session-status', message => { modules.user().sessionError = message; });
};

export default (name) => {
  return modules[name]();
};
