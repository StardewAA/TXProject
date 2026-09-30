import App from './App';
import { createSSRApp } from 'vue';
import { setupPinia } from './sheep/store';
// #ifdef H5
import { installTenantNavigation } from './sheep/helper/tenant-navigation-runtime.mjs';
import { getControlledTenantId, stampTenantUrl } from './sheep/helper/tenant-runtime';
// #endif


export function createApp() {
  // #ifdef H5
  // Explicit API references allow uni-app's H5 compiler to include the needed functions.
  installTenantNavigation({ addInterceptor: uni.addInterceptor, showToast: uni.showToast }, getControlledTenantId, stampTenantUrl);
  // #endif

  const app = createSSRApp(App);
  
  setupPinia(app);

  return {
    app,
  };
}
