import { fileURLToPath } from 'node:url';

import type { Plugin, UserConfig } from 'vite';

import { mergeConfig } from 'vite';

import baseConfig from './vite.config';

const attrFilterPath = fileURLToPath(
  new URL('./src/views/bidding/components/AttrFilter.vue', import.meta.url),
);

const compatibilityPlugin: Plugin = {
  name: 'hmr-compatibility-alias',
  resolveId(source, importer) {
    const normalizedImporter = importer?.replaceAll('\\', '/') ?? '';
    // 招标模块有 6 个视图把 AttrFilter 的相对层级写错（真实文件在
    // src/views/bidding/components/，它们却写成 ../components/，于是解析到各自子目录下
    // 并不存在的 components/）。该组件全局只有这一个，因此按「说明符 + 归属招标视图」
    // 统一重定向即可，既不改招标源码，也不会影响别的模块。
    if (
      source === '../components/AttrFilter.vue' &&
      normalizedImporter.includes('/views/bidding/')
    ) {
      return attrFilterPath;
    }
  },
};

export default async (configEnv: any) => {
  const resolved = await baseConfig(configEnv);
  return mergeConfig(resolved as UserConfig, {
    plugins: [compatibilityPlugin],
  });
};
