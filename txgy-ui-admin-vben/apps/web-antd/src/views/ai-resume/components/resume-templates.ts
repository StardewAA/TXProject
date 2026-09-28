/**
 * 简历模板清单。
 *
 * 名称与顺序取自参考站的模板面板（deephire.cn 简历编辑器 → 简历模板），
 * 共 28 套。**缩略图不是外站图片**：那边用的是 z.zhipin.com / img.bosszhipin.com 的
 * 直链素材，而本项目的需求文档明确要求不把外站作为运行时依赖、不使用未获授权的素材，
 * 所以这里只保留「名字 + 配色 + 版式原型」，缩略图由 template-thumb.vue 现画。
 *
 * archetype 决定缩略图怎么画，也决定中栏简历文档走哪种版头：
 * - classic  单栏，姓名在左上，标题带主色小节线
 * - sidebar  左侧主色竖栏
 * - banner   顶部整条主色横带
 * - minimal  无装饰，只靠字重和留白
 * - tabular  带表格线
 * - modern   大字号姓名块 + 双栏
 */

export type TemplateArchetype =
  | 'banner'
  | 'classic'
  | 'minimal'
  | 'modern'
  | 'sidebar'
  | 'tabular';

export interface ResumeTemplate {
  /** 主色：缩略图的色块与简历文档的强调色都用它 */
  accent: string;
  archetype: TemplateArchetype;
  /** 稳定标识，存进简历内容，换模板后可回显 */
  key: string;
  name: string;
}

export const RESUME_TEMPLATES: ResumeTemplate[] = [
  { accent: '#6E8D57', archetype: 'classic', key: 'standard', name: '标准' },
  { accent: '#2F6FB0', archetype: 'classic', key: 'blue-light', name: '青蓝' },
  { accent: '#2B3A55', archetype: 'sidebar', key: 'steady', name: '沉稳' },
  { accent: '#4E6840', archetype: 'minimal', key: 'simple', name: '简约' },
  { accent: '#2E8B8B', archetype: 'banner', key: 'azure', name: '湛青' },
  { accent: '#C0392B', archetype: 'sidebar', key: 'lucky-red', name: '幸运红' },
  { accent: '#1F5FA8', archetype: 'sidebar', key: 'champion-blue', name: '冠军蓝' },
  { accent: '#8E2B2B', archetype: 'banner', key: 'classic-red', name: '典藏红' },
  { accent: '#6E8D57', archetype: 'minimal', key: 'minimal', name: '极简' },
  { accent: '#2C6FB5', archetype: 'banner', key: 'blue-top', name: '蓝顶' },
  { accent: '#7A8B6F', archetype: 'classic', key: 'elegant-clear', name: '清雅' },
  { accent: '#4A6FA5', archetype: 'tabular', key: 'concise', name: '简明' },
  { accent: '#6B7B8C', archetype: 'tabular', key: 'table', name: '表格' },
  { accent: '#1F2B2B', archetype: 'modern', key: 'ink', name: '墨韵' },
  { accent: '#8C6B4A', archetype: 'classic', key: 'retro', name: '复古' },
  { accent: '#2F4F6F', archetype: 'modern', key: 'business', name: '商务' },
  { accent: '#1A1A1A', archetype: 'minimal', key: 'fashion-black', name: '时尚黑' },
  { accent: '#E8804A', archetype: 'banner', key: 'youth', name: '活力青春' },
  { accent: '#8B5EA8', archetype: 'banner', key: 'artistic', name: '艺术气息' },
  { accent: '#C79BB0', archetype: 'minimal', key: 'soft', name: '柔境' },
  { accent: '#4F7A3F', archetype: 'classic', key: 'forest', name: '林原' },
  { accent: '#5B6B7C', archetype: 'classic', key: 'graceful', name: '典雅' },
  { accent: '#C0392B', archetype: 'modern', key: 'magazine', name: '杂志编辑' },
  { accent: '#6FA05A', archetype: 'classic', key: 'forest-fresh', name: '森系清新' },
  { accent: '#0891B2', archetype: 'modern', key: 'cyber', name: '赛博未来' },
  { accent: '#A9812F', archetype: 'classic', key: 'renaissance', name: '文艺复兴' },
  { accent: '#6BA8C9', archetype: 'minimal', key: 'watercolor', name: '清新水彩' },
  { accent: '#4A90D9', archetype: 'banner', key: 'campus', name: '青春校园' },
];

export const DEFAULT_TEMPLATE_KEY = 'standard';

export function findTemplate(key?: string): ResumeTemplate {
  return (
    RESUME_TEMPLATES.find((item) => item.key === key) ||
    RESUME_TEMPLATES.find((item) => item.key === DEFAULT_TEMPLATE_KEY)!
  );
}
