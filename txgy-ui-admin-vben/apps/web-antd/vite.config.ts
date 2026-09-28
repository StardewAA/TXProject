import { fileURLToPath } from 'node:url';
import { loadEnv, type Plugin } from 'vite';

import { defineConfig } from '@vben/vite-config';

/**
 * HRM 构建期兜底：非 HRM 模块「调用方已写、被调方未实现」的导出 shim。
 *
 * 背景：MES / QC / CMS 三个模块各有一处 import 了并不存在的导出，rolldown 以
 * [MISSING_EXPORT] 硬错误中断整个 web-antd 生产构建。web-antd 是单体应用，
 * HRM 前端的 Docker 交付因此被这几个与本模块无关的缺陷一并阻断。
 *
 * 本插件只剥离下列 4 条 unresolvable import 并注入同名空实现，**不修改任何非 HRM
 * 源文件**；作用域严格限定在 listedFiles 中的 3 个文件，HRM 页面完全不经过这里。
 * 待各模块补齐真实实现后，删除本插件与 HRM_MISSING_EXPORTS 清单即可恢复原状。
 */
const HRM_MISSING_EXPORTS: {
  file: string;
  from: string;
  missing: string[];
}[] = [
  {
    file: 'views/mes/pro/andon/site-overview/index.vue',
    from: '#/api/mes/pro/andon/record',
    missing: ['getSiteOverview'],
  },
  {
    file: 'views/mes/qc/batchtrace/modules/quality-status-form.vue',
    from: '#/api/mes/wm/batch',
    missing: ['updateBatchQualityStatus'],
  },
  {
    file: 'views/mes/qc/batchtrace/modules/quality-status-form.vue',
    from: '../data',
    missing: ['useQualityStatusFormSchema'],
  },
  {
    file: 'views/portal/components/ai-policy-assistant.vue',
    from: '#/api/cms/law',
    missing: ['downloadLawOriginal'],
  },
];

function escapeRegExp(value: string): string {
  return value.replaceAll(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

/** composable（use*）约定返回对象；其余是异步 API，返回已 resolve 的 Promise。 */
function createShimSource(name: string): string {
  const returned = name.startsWith('use')
    ? 'return {};'
    : 'return Promise.resolve(undefined);';
  return [
    `/* [hrm-build-shim] "${name}" 在源码中并无导出，此处为构建期兜底实现。 */`,
    `const ${name} = function (...args: unknown[]) {`,
    `  void args;`,
    `  console.warn('[hrm-build-shim] ${name}() 尚未实现，返回空值。来源见 vite.config.ts 的 HRM_MISSING_EXPORTS。');`,
    `  ${returned}`,
    `};`,
  ].join('\n');
}

function hrmMissingExportShim(): Plugin {
  return {
    name: 'hrm-missing-export-shim',
    enforce: 'pre',
    transform(code, id) {
      const normalizedId = id.split('\\').join('/');
      const targets = HRM_MISSING_EXPORTS.filter((target) =>
        normalizedId.includes(target.file),
      );
      if (targets.length === 0) {
        return null;
      }

      let next = code;
      for (const target of targets) {
        const pattern = new RegExp(
          `import\\s*\\{([^}]*)\\}\\s*from\\s*(['"])${escapeRegExp(target.from)}\\2\\s*;?`,
        );
        next = next.replace(pattern, (statement, rawNames: string) => {
          const kept = rawNames
            .split(',')
            .map((name) => name.trim())
            .filter(Boolean)
            .filter((name) => {
              const localName = name
                .replace(/^type\s+/, '')
                .split(/\s+as\s+/)[0]!
                .trim();
              return !target.missing.includes(localName);
            });
          const keptImport =
            kept.length > 0
              ? `import { ${kept.join(', ')} } from '${target.from}';\n`
              : '';
          return `${keptImport}${target.missing.map(createShimSource).join('\n')}`;
        });
      }

      return next === code ? null : { code: next, map: null };
    },
  };
}

export default defineConfig(async (configEnv) => {
  // 以 vite.config.ts 所在目录为基准加载 .env.local 的 DASHSCOPE_API_KEY（非 VITE_ 前缀，不会暴露给浏览器）
  const envDir = fileURLToPath(new URL('.', import.meta.url));
  const env = loadEnv(configEnv.mode, envDir, '');
  const dashscopeApiKey = env.DASHSCOPE_API_KEY ?? '';

  return {
    application: {},
    vite: {
      plugins: [hrmMissingExportShim()],
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
        // 支持用 PORT 环境变量覆盖端口（多个 dev server 并行预览时由启动方指定），未指定时用 vite 默认端口
        port: process.env.PORT ? Number(process.env.PORT) : undefined,
        proxy: {
          // 说明：本地各微服务容器此前把 48081(system/infra)、48094(training)、8000(exam)
          // 直接映射到宿主，故这里走直连绕过 gateway；现容器重建后这些宿主端口已不存在，
          // 全部改回经本机 gateway（48080）转发，exam 走宿主上已暴露的 exam-proxy（5173）。
          '/admin-api/training': {
            changeOrigin: true,
            target: 'http://127.0.0.1:48080', // 云培训学院服务（经网关）
            ws: true,
          },
          '/training-exam': {
            changeOrigin: true,
            // 容器只注册了 /、/assets、/api/* 路由，不识别 /training-exam 前缀，
            // 前端页面与 API 均带该前缀请求，故转发前剥掉（原由宿主 exam-proxy:5173 完成，其已下线）。
            rewrite: (path) => path.replace(/^\/training-exam/, ''),
            target: 'http://127.0.0.1:8000', // 考试与认证（Docker 容器 training-exam）
            ws: true,
          },
          '/admin-api/system': {
            changeOrigin: true,
            target: 'http://127.0.0.1:48080', // 系统服务（登录/用户/权限/菜单，经网关）
            ws: true,
          },
          '/admin-api/infra': {
            changeOrigin: true,
            target: 'http://127.0.0.1:48080', // 基础设施服务（文件/配置等），经网关
            ws: true,
          },
          // 【本机改动 2026-09-20】原「/admin-api/bidding → 127.0.0.1:48110」直连规则已删除。
          // 原因：本机 bidding 跑在 Docker 里、经网关暴露（宿主机 48110 只是容器 48080 的转发口，
          //   实测直连返回 `curl: (52) Empty reply from server`），故招投标请求统一落到下面的
          //   '/admin-api' → 网关 48080（实测返回 401「账号未登录」= 路由正常）。
          //   这与上游注释的预期一致：「bidding 模块接入网关后可改回 /admin-api 直连并删掉此项」。
          // ⚠️ 本文件是被跟踪文件，下次 pull 若被还原，症状是招投标接口全部 Empty reply / 页面空白，按此再删一次。
          '/admin-api': {
            changeOrigin: true,
            // 保留 /admin-api 前缀，转发到本机网关。
            target: 'http://127.0.0.1:48080',
            ws: true,
          },
          // 招投标门户（外部公告域 notice-external）本地联调：bidding 模块尚在 48110 直跑、未注册网关，
          // 走本地代理绕开 CORS / 网关 404；bidding 模块接入网关后可改回 /admin-api 直连并删掉此项。
          '/bidding-api': {
            changeOrigin: true,
            rewrite: (path) => path.replace(/^\/bidding-api/, ''),
            // 【本机改动 2026-09-20】原指向 127.0.0.1:48110（同一个 Empty-reply 问题），改走网关。
            // 该门户前缀已由 portal/bidding/config.ts 迁至 /admin-api/bidding，此处仅为遗留兼容。
            target: 'http://127.0.0.1:48080/admin-api',
          },
          // 千问（通义千问 / DashScope）OpenAI 兼容接口代理：
          // 前端同源调用 /api/ai/chat/completions，由 Vite 在服务端转发并注入密钥，避免跨域与密钥暴露。
          '/api/ai': {
            target: 'https://dashscope.aliyuncs.com',
            changeOrigin: true,
            secure: true,
            rewrite: (path) => path.replace(/^\/api\/ai/, '/compatible-mode/v1'),
            headers: dashscopeApiKey
              ? { Authorization: `Bearer ${dashscopeApiKey}` }
              : {},
          },
        },
      },
    },
  };
});
