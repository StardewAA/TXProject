import { isPlatformPortalAddress, platformPublicPath, scopeLocalPath } from './tenant-navigation.mjs';

const installed = new WeakSet();
// Only called in H5. switchTab cannot take a query; stamp its committed URL instead.
export function installTenantNavigation(api, tenant, stamp) {
  if (installed.has(api)) return;
  installed.add(api);
  for (const method of ['navigateTo', 'redirectTo', 'reLaunch', 'switchTab']) {
    api.addInterceptor(method, {
      invoke(options) {
        try {
          // Platform catalog/shop routes are tenant-neutral. They must not be sent through the
          // legacy tenant-switch guard a second time by the global uni navigation interceptor.
          const path = isPlatformPortalAddress(options.url)
            ? platformPublicPath(options.url)
            : scopeLocalPath(options.url, tenant());
          options.url = method === 'switchTab' ? path.split('?')[0] : path;
        } catch (error) {
          api.showToast({ title: error.message || '商城链接无效', icon: 'none' });
          const result = { errMsg: method + ':fail 商城链接无效或租户不一致' };
          options.fail?.(result);
          options.complete?.(result);
          return false;
        }
      },
      success: stamp,
    });
  }
  api.addInterceptor('navigateBack', { success: stamp });
}
