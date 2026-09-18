/**
 * 离线图标注册
 *
 * 背景：@iconify/vue 运行时会按需请求外部图标 API（api.iconify.design /
 * api.simplesvg.com / api.unisvg.com），当前网络不可达导致控制台大量
 * ERR_CONNECTION_TIMED_OUT。本模块在应用启动前把项目用到的图标集合
 * 注册为本地数据，图标渲染不再发起任何网络请求。
 */
import { addCollection } from '@iconify/vue';
import { getIconData } from '@iconify/utils';
import type { IconifyJSON } from '@iconify/types';

import antDesign from '@iconify/json/json/ant-design.json';
import carbon from '@iconify/json/json/carbon.json';
import ep from '@iconify/json/json/ep.json';
import fa from '@iconify/json/json/fa.json';
import faSolid from '@iconify/json/json/fa-solid.json';
import fa6Solid from '@iconify/json/json/fa6-solid.json';
import fluentMdl2 from '@iconify/json/json/fluent-mdl2.json';
import foundation from '@iconify/json/json/foundation.json';
import lucide from '@iconify/json/json/lucide.json';
import materialSymbols from '@iconify/json/json/material-symbols.json';
import mdi from '@iconify/json/json/mdi.json';
import ri from '@iconify/json/json/ri.json';
import simpleIcons from '@iconify/json/json/simple-icons.json';
import tabler from '@iconify/json/json/tabler.json';

// 1) 菜单/业务图标大量使用的集合，整体注册（ep/fa/fa-solid 等）
const fullCollections = [
  ep,
  fa,
  faSolid,
  fa6Solid,
  foundation,
  fluentMdl2,
  simpleIcons,
  antDesign,
  carbon,
  lucide,
] as unknown as IconifyJSON[];
fullCollections.forEach((collection) => addCollection(collection));

// 2) 大集合只按需提取项目实际用到的图标，避免打包整个集合
function registerPartial(collection: IconifyJSON, names: string[]) {
  const icons: Record<string, unknown> = {};
  names.forEach((name) => {
    const icon = getIconData(collection, name);
    if (icon) {
      icons[name] = icon;
    }
  });
  if (Object.keys(icons).length > 0) {
    addCollection({
      prefix: collection.prefix,
      icons,
      width: collection.width,
      height: collection.height,
    } as IconifyJSON);
  }
}

registerPartial(mdi as unknown as IconifyJSON, [
  'github',
  'google',
  'keyboard-esc',
  'qqchat',
  'wechat',
]);
registerPartial(materialSymbols as unknown as IconifyJSON, ['refresh-rounded']);
registerPartial(ri as unknown as IconifyJSON, ['dingding-fill']);
registerPartial(tabler as unknown as IconifyJSON, ['arrows-minimize', 'ai']);
