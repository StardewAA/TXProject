/**
 * 由 system_menu.icon 生成 src/offline-icons.menu.ts（离线图标清单）
 *
 * 用法：
 *   mysql -uroot -p ruoyi-vue-pro -N -B \
 *     -e "SELECT DISTINCT icon FROM system_menu WHERE icon IS NOT NULL AND icon<>'' ORDER BY icon" \
 *     > /tmp/menu-icons.txt
 *   node scripts/gen-menu-icon-list.mjs /tmp/menu-icons.txt
 *
 * 说明：Docker 构建阶段无法访问数据库，因此生成结果需要提交到仓库，
 *      构建时由 scripts/generate-offline-icons.mjs 扫描该文件并入首屏图标集合。
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const APP_ROOT = fileURLToPath(new URL('../apps/web-antd', import.meta.url));
const OUT = path.join(APP_ROOT, 'src', 'offline-icons.menu.ts');
const COLLECTIONS = [
  'ant-design', 'ep', 'fa', 'fa-solid', 'fa6-solid', 'carbon', 'fluent-mdl2',
  'foundation', 'lucide', 'material-symbols', 'mdi', 'ri', 'simple-icons', 'tabler',
];
const ICON_RE = new RegExp(`^(${COLLECTIONS.join('|')}):[a-z0-9-]+$`);

const input = process.argv[2];
if (!input) {
  console.error('用法: node scripts/gen-menu-icon-list.mjs <menu-icons.txt>');
  process.exit(1);
}
const raw = fs.readFileSync(input, 'utf8').split(/\r?\n/).map((s) => s.trim()).filter(Boolean);
const icons = [...new Set(raw.filter((i) => ICON_RE.test(i)))].sort();
const skipped = [...new Set(raw.filter((i) => !ICON_RE.test(i)))];

const content = `/**
 * 后端菜单图标清单（供 scripts/generate-offline-icons.mjs 扫描）
 *
 * 背景：离线图标生成脚本只扫描源码中静态书写的图标名，而侧边栏图标来自后端
 *      system_menu.icon（动态数据）。不进这份清单的图标在离线环境下会渲染成空占位符。
 *
 * 维护：菜单表 system_menu 的 icon 字段有新增/变更后，重新生成本文件并重构建前端。
 *      生成命令见 scripts/gen-menu-icon-list.mjs 头部说明。
 */
export const offlineMenuIconNames = [
${icons.map((i) => `  '${i}',`).join('\n')}
] as const;
`;
fs.mkdirSync(path.dirname(OUT), { recursive: true });
fs.writeFileSync(OUT, content, 'utf8');
console.log(`[menu-icons] 写入 ${path.relative(APP_ROOT, OUT)}：${icons.length} 个图标` + (skipped.length ? `，跳过 ${skipped.length} 个：${skipped.join(', ')}` : ''));
