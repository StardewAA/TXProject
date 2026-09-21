/**
 * 离线图标生成脚本
 *
 * 背景：此前 src/offline-icons.ts 直接 import 12 个完整的 @iconify/json 集合，
 * 这些 JSON 会被全部打进首屏入口 chunk（约 22MB），导致站点首次访问极慢。
 *
 * 方案：
 * 1) 扫描源码中静态书写的图标名（icon="lucide:search"、icon: 'mdi:github' 等），
 *    从完整集合中只提取实际用到的图标，生成 src/offline-icons.generated.ts；
 *    入口只保留这份精简数据（通常几十 KB～几百 KB）。
 * 2) 将完整集合复制到 public/iconify/{prefix}.json，作为运行时兜底数据源：
 *    后端菜单等动态图标通过 setCustomIconsLoader 按需拉取，避免请求外网图标 API。
 *
 * 用法：pnpm gen:icons（apps/web-antd 下）
 */
import { createRequire } from 'node:module';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { subsetIconCollection } from '../apps/web-antd/src/utils/iconify-subset.mjs';

const APP_ROOT = fileURLToPath(new URL('../apps/web-antd', import.meta.url));
const MONOREPO_ROOT = fileURLToPath(new URL('..', import.meta.url));
const SRC_DIR = path.join(APP_ROOT, 'src');
const OUT_TS = path.join(SRC_DIR, 'offline-icons.generated.ts');
const OUT_PUBLIC_DIR = path.join(APP_ROOT, 'public', 'iconify');
/** 扫描范围：业务源码 + vben 内部组件库（布局/控件内置图标也要预置） */
const SCAN_DIRS = [SRC_DIR, path.join(MONOREPO_ROOT, 'packages')];

/** 需要离线化的图标集合（与 offline-icons.ts 保持一致） */
const COLLECTIONS = [
  'ant-design',
  'ep',
  'fa',
  /** 后端菜单图标使用的前缀（fa-solid:xxx），缺失会导致左侧导航图标离线时无法渲染 */
  'fa-solid',
  'fa6-solid',
  /** 后端菜单图标使用的前缀（carbon:workspace） */
  'carbon',
  'fluent-mdl2',
  'foundation',
  'lucide',
  'material-symbols',
  'mdi',
  'ri',
  'simple-icons',
  'tabler',
];

// 后端 system_menu 动态下发的常用图标不会出现在前端源码中，必须显式纳入首屏离线集合。
// 否则首次渲染依赖运行时兜底请求，离线或请求竞态时会出现菜单无图标。
const BACKEND_MENU_ICONS = {
  "ep": [
    "add-location",
    "aim",
    "alarm-clock",
    "apple",
    "avatar",
    "baseball",
    "basketball",
    "bell",
    "bicycle",
    "bottom",
    "bowl",
    "box",
    "burger",
    "calendar",
    "caret-right",
    "cellphone",
    "chat-dot-round",
    "chat-dot-square",
    "chat-line-round",
    "chat-round",
    "chat-square",
    "check",
    "checked",
    "chicken",
    "circle-check-filled",
    "clock",
    "coffee",
    "coffee-cup",
    "cold-drink",
    "collection",
    "collection-tag",
    "comment",
    "compass",
    "connection",
    "coordinate",
    "copy-document",
    "cpu",
    "credit-card",
    "data-analysis",
    "data-board",
    "data-line",
    "discount",
    "dish-dot",
    "document",
    "document-checked",
    "document-copy",
    "download",
    "edit",
    "edit-pen",
    "eleme",
    "expand",
    "files",
    "finished",
    "folder",
    "folder-remove",
    "goblet-square-full",
    "goods",
    "grape",
    "guide",
    "home-filled",
    "house",
    "key",
    "list",
    "magic-stick",
    "management",
    "memo",
    "menu",
    "message",
    "message-box",
    "minus",
    "money",
    "monitor",
    "mute-notification",
    "notebook",
    "office-building",
    "operation",
    "opportunity",
    "phone",
    "picture",
    "picture-rounded",
    "pie-chart",
    "place",
    "position",
    "postcard",
    "present",
    "price-tag",
    "promotion",
    "refresh-left",
    "refrigerator",
    "scale-to-original",
    "scissor",
    "sell",
    "set-up",
    "setting",
    "shop",
    "shopping-cart",
    "sold-out",
    "sort",
    "sunny",
    "takeaway-box",
    "timer",
    "tools",
    "trend-charts",
    "upload",
    "upload-filled",
    "user",
    "user-filled",
    "wallet",
    "warning",
    "warning-filled",
    "zoom-in",
    "zoom-out"
  ],
  "fa-solid": [
    "abacus",
    "apple-alt",
    "blog",
    "bone",
    "book",
    "book-reader",
    "border-all",
    "box",
    "charging-station",
    "credit-card",
    "disease",
    "dragon",
    "file-signature",
    "gopuram",
    "grin-stars",
    "house-user",
    "mail-bulk",
    "notes-medical",
    "project-diagram",
    "republican",
    "swimming-pool",
    "tasks",
    "tools",
    "truck",
    "user-alt",
    "user-tie",
    "window-restore"
  ],
  "fa": [
    "address-book-o",
    "address-card",
    "apple",
    "archive",
    "area-chart",
    "assistive-listening-systems",
    "asterisk",
    "bandcamp",
    "barcode",
    "bars",
    "battery-3",
    "bookmark-o",
    "bus",
    "buysellads",
    "cc-paypal",
    "certificate",
    "connectdevelop",
    "credit-card",
    "dashcube",
    "dedent",
    "delicious",
    "edit",
    "eye",
    "fighter-jet",
    "file-image-o",
    "first-order",
    "group",
    "hand-grab-o",
    "hdd-o",
    "key",
    "leaf",
    "leanpub",
    "level-up",
    "map",
    "map-marker",
    "medium",
    "money",
    "music",
    "object-ungroup",
    "pagelines",
    "paypal",
    "power-off",
    "product-hunt",
    "qrcode",
    "reddit-square",
    "registered",
    "road",
    "rocket",
    "sellsy",
    "sitemap",
    "slack",
    "square",
    "stack-exchange",
    "superpowers",
    "tag",
    "tasks",
    "transgender-alt",
    "tree",
    "universal-access",
    "user",
    "user-secret",
    "window-restore",
    "wpexplorer",
    "wpforms",
    "y-combinator"
  ],
  "fa6-solid": [
    "brush"
  ],
  "foundation": [
    "page-edit"
  ],
  "lucide": [
    "badge-check",
    "clipboard-check",
    "copy-check",
    "file-user",
    "history",
    "list-todo"
  ],
  "simple-icons": [
    "civicrm",
    "erpnext"
  ],
  "tabler": [
    "ai"
  ]
};

const SCAN_EXT = new Set(['.vue', '.ts', '.tsx', '.js', '.jsx']);

const require = createRequire(path.join(APP_ROOT, 'package.json'));

function resolveCollectionJson(prefix) {
  return require.resolve(`@iconify/json/json/${prefix}.json`);
}

/** 递归收集待扫描文件 */
function walk(dir, result = []) {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    if (entry.name === 'node_modules' || entry.name === 'dist') {
      continue;
    }
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      walk(full, result);
    } else if (entry.isFile() && SCAN_EXT.has(path.extname(entry.name))) {
      result.push(full);
    }
  }
  return result;
}

/**
 * 收集源码中静态书写的图标名
 * 仅匹配形如 prefix:name 的字面量，且 prefix 属于已知集合，避免误伤 http:// 等文本
 */
function collectUsedIcons(files) {
  const prefixPattern = COLLECTIONS.map((item) =>
    item.replaceAll('-', '\\-'),
  ).join('|');
  const regex = new RegExp(
    `(?:^|[^\\w:/.-])((?:${prefixPattern}):[a-z0-9-]+)`,
    'g',
  );
  const used = new Map(COLLECTIONS.map((item) => [item, new Set()]));
  for (const file of files) {
    const content = fs.readFileSync(file, 'utf8');
    for (const match of content.matchAll(regex)) {
      const [prefix, name] = match[1].split(':');
      used.get(prefix)?.add(name);
    }
  }
  return used;
}

function main() {
  const files = SCAN_DIRS.flatMap((dir) => walk(dir));
  const used = collectUsedIcons(files);
  for (const [prefix, names] of Object.entries(BACKEND_MENU_ICONS)) {
    for (const name of names) {
      used.get(prefix)?.add(name);
    }
  }

  const collections = [];
  const stats = [];
  for (const prefix of COLLECTIONS) {
    const names = [...(used.get(prefix) ?? [])].sort();
    const raw = JSON.parse(fs.readFileSync(resolveCollectionJson(prefix), 'utf8'));
    const { icons, aliases } = subsetIconCollection(raw, names);
    const missing = names.filter((name) => !icons[name] && !aliases[name]).length;
    collections.push({
      prefix,
      icons,
      aliases,
      lastModified: raw.lastModified ?? 0,
      height: raw.height ?? 24,
      width: raw.width ?? 24,
    });
    stats.push(
      `${prefix}: ${names.length} 个引用，命中 ${Object.keys(icons).length} 个${missing ? `，缺失 ${missing} 个` : ''}`,
    );
  }

  const body = collections
    .map((item) => JSON.stringify(item))
    .join(',\n  ');

  const content = `// 该文件由 scripts/generate-offline-icons.mjs 自动生成，请勿手工修改。
/* eslint-disable */
import type { IconifyJSON } from '@iconify/types';

/** 源码中实际使用到的图标（从 @iconify/json 提取，避免整包进首屏） */
export const offlineIconCollections = [
  ${body},
] as unknown as IconifyJSON[];

/** 可作为运行时兜底数据源的集合前缀（完整 JSON 位于 public/iconify/{prefix}.json） */
export const offlineIconPrefixes: string[] = ${JSON.stringify(COLLECTIONS)};
`;

  fs.mkdirSync(path.dirname(OUT_TS), { recursive: true });
  fs.writeFileSync(OUT_TS, content, 'utf8');

  // 兜底数据源：完整集合复制到 public，构建后位于 /iconify/{prefix}.json
  fs.mkdirSync(OUT_PUBLIC_DIR, { recursive: true });
  for (const prefix of COLLECTIONS) {
    fs.copyFileSync(
      resolveCollectionJson(prefix),
      path.join(OUT_PUBLIC_DIR, `${prefix}.json`),
    );
  }

  const sizeKb = (fs.statSync(OUT_TS).size / 1024).toFixed(1);
  console.log(`[icons] 扫描文件 ${files.length} 个，生成 ${path.relative(APP_ROOT, OUT_TS)} (${sizeKb} KB)`);
  stats.forEach((line) => console.log(`[icons]   ${line}`));
  console.log(`[icons] 完整集合已复制到 ${path.relative(APP_ROOT, OUT_PUBLIC_DIR)}（运行时按需加载，需加入 .gitignore）`);
}

main();
