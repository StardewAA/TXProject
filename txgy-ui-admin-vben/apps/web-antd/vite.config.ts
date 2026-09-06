import { defineConfig } from '@vben/vite-config';

export default defineConfig(async () => {
  return {
    application: {},
    vite: {
      build: {
        rolldownOptions: {
          output: {
            // 规避 Rolldown（vite 8.0.10 / rolldown 1.0.0-rc.17）chunk 优化器的循环依赖缺陷：
            // 入口 chunk 静态依赖 @vben/utils，而 @vben/utils 又会从入口 chunk 导入
            // init_* 初始化函数并在模块顶层调用，导致 “xxx is not a function”。
            // 将 @vben/utils 与 @vben-core/shared 强制合并到独立 chunk，切断与入口的循环。
            manualChunks(id) {
              const normalizedId = id.split('\\').join('/');
              if (
                normalizedId.includes('/packages/utils/') ||
                normalizedId.includes('/packages/@core/base/shared/')
              ) {
                return 'vben-utils-shared';
              }
            },
          },
        },
      },
      server: {
        allowedHosts: true,
        // 监听所有网络接口，避免内嵌 WebView 的 ERR_CONNECTION_RESET
        host: true,
        proxy: {
          '/admin-api': {
            changeOrigin: true,
            rewrite: (path) => path.replace(/^\/admin-api/, ''),
            // mock代理目标地址
            target: 'http://localhost:48080/admin-api',
            ws: true,
          },
          // 招投标门户（外部公告域 notice-external）本地联调：bidding 模块尚在 48110 直跑、未注册网关，
          // 走本地代理绕开 CORS / 网关 404；bidding 模块接入网关后可改回 /admin-api 直连并删掉此项。
          '/bidding-api': {
            changeOrigin: true,
            rewrite: (path) => path.replace(/^\/bidding-api/, ''),
            target: 'http://127.0.0.1:48110/admin-api',
          },
        },
      },
    },
  };
});
