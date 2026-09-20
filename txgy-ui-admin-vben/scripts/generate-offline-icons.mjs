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

  const collections = [];
  const stats = [];
  for (const prefix of COLLECTIONS) {
    const names = [...(used.get(prefix) ?? [])].sort();
    const raw = JSON.parse(fs.readFileSync(resolveCollectionJson(prefix), 'utf8'));
    const icons = {};
    let missing = 0;
    for (const name of names) {
      if (raw.icons?.[name]) {
        icons[name] = raw.icons[name];
      } else if (raw.aliases?.[name]) {
        // 别名图标依赖父图标，需一并保留，否则渲染时找不到图形
        const alias = raw.aliases[name];
        icons[name] = alias;
        if (alias?.parent && raw.icons[alias.parent]) {
          icons[alias.parent] = raw.icons[alias.parent];
        }
      } else {
        missing += 1;
      }
    }
    collections.push({
      prefix,
      icons,
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
