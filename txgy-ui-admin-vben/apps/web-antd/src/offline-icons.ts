/**
 * 离线图标注册
 *
 * 背景：@iconify/vue 运行时会按需请求外部图标 API（api.iconify.design /
 * api.simplesvg.com / api.unisvg.com），当前网络不可达导致控制台大量
 * ERR_CONNECTION_TIMED_OUT。本模块把图标数据本地化，图标渲染不再请求外网。
 *
 * 体积优化（首屏入口包 22MB → 约 100KB）：
 * 1) 首屏只注册源码中实际使用到的图标，数据来自 src/offline-icons.generated.ts
 *    （由 scripts/generate-offline-icons.mjs 从完整集合中裁剪生成）；
 * 2) 后端菜单等动态图标通过 setCustomIconsLoader 按需拉取本地完整集合
 *    /iconify/{prefix}.json（构建产物，nginx 已配置 gzip + 强缓存），
 *    既不进首屏包，也不会因外网不可达而报错。
 */
import { addCollection, setCustomIconsLoader } from '@iconify/vue';
import type { IconifyJSON } from '@iconify/types';
import { subsetIconCollection } from './utils/iconify-subset.mjs';

import { CRM_AI_AVATAR_BODY } from '#/assets/icons/crm-ai-avatar';
import {
  offlineIconCollections,
  offlineIconPrefixes,
} from '#/offline-icons.generated';

// 1) 首屏：注册项目实际使用到的图标（精简数据）
offlineIconCollections.forEach((collection) => addCollection(collection));

// 2) 项目自有图标：CRM AI 助手（蓝绿渐变圆形机器人头像）
//    侧边栏菜单「AI对话助手」使用 crmai:avatar，避免运行时请求外部图标 API
//    注意：该图标不在 @iconify/json 中，生成脚本无法裁剪，需在此手动注册
addCollection({
  prefix: 'crmai',
  icons: {
    avatar: { body: CRM_AI_AVATAR_BODY },
  },
  width: 96,
  height: 96,
} as unknown as IconifyJSON);

// 3) 按需兜底：命中未预置的图标（如后端菜单下发的图标）时，加载本地完整集合
/** 已加载的完整集合 */
const loaded = new Map<string, IconifyJSON>();
/** 正在加载的请求，避免并发重复拉取 */
const inflight = new Map<string, Promise<IconifyJSON | null>>();

async function fetchCollection(prefix: string): Promise<IconifyJSON | null> {
  const cached = loaded.get(prefix);
  if (cached) {
    return cached;
  }
  const pending = inflight.get(prefix);
  if (pending) {
    return pending;
  }
  const task = fetch(`${import.meta.env.BASE_URL}iconify/${prefix}.json`)
    .then((res) => (res.ok ? (res.json() as Promise<IconifyJSON>) : null))
    .then((data) => {
      if (data) {
        loaded.set(prefix, data);
      }
      return data;
    })
    .catch(() => null)
    .finally(() => {
      // 无论成功失败都允许下次重新尝试（失败场景：网络抖动）
      inflight.delete(prefix);
    });
  inflight.set(prefix, task);
  return task;
}

/** 从完整集合中裁剪出本次需要的图标（含别名依赖的父图标） */
function pickIcons(data: IconifyJSON, names: string[]): IconifyJSON | null {
  const subset = subsetIconCollection(data, names);
  return Object.keys(subset.icons).length ? subset : null;
}

offlineIconPrefixes.forEach((prefix) => {
  setCustomIconsLoader(async (names: string[]) => {
    const data = await fetchCollection(prefix);
    if (!data) {
      return null;
    }
    return pickIcons(data, names);
  }, prefix);
});
