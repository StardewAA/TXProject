import type { HrmPortalRecruitAgentApi } from '#/api/hrm/portal/recruit-agent';

/**
 * 优化建议 → 编辑器字段的定位。
 *
 * 服务端返回的建议带 `fieldPath`（description / achievements / experience /
 * strengths / skills），但**不能**直接拿它当定位依据：同一个 `description`
 * 在教学经历、实习经历、工作经历、项目经历里都有，光看名字分不清是哪一条。
 *
 * 所以这里用 `originalValue` 反查：服务端已经保证它「逐字出现在简历原文中」
 * （见 HrmResumeOptimizeResultValidator 的护栏一），拿它在草稿里搜索，
 * 唯一命中的那个字段就是这条建议该挂的地方。两边用的是同一个事实，
 * 前端不需要另造一套映射规则去猜。
 */

/** 建议项，字段与后端 OptimizeItem 一致 */
export type FieldSuggestion = HrmPortalRecruitAgentApi.OptimizeItem;

/** 路径 → 该字段上的建议 */
export type SuggestionMap = Record<string, FieldSuggestion[]>;

/**
 * 建议的稳定标识。
 *
 * 「已处理过哪些建议」必须按这个键记，不能按对象引用：会话要存进 localStorage
 * 以便刷新后接着看，而 JSON 一来一回全是新对象，引用比对会全部失效，
 * 用户点过的建议会在刷新后集体复活，还得再点一遍。
 */
export function suggestionKey(item: FieldSuggestion) {
  return `${item.module}|${item.fieldPath}|${item.originalValue}`;
}

/** 标量字段：直接挂在 content 顶层 */
const SCALAR_FIELDS = ['strengths', 'skills'] as const;

/** 数组字段：content[分区][序号][字段] */
const SECTION_FIELDS: Record<string, readonly string[]> = {
  educationExperiences: ['experience'],
  internships: ['description', 'achievements'],
  clubs: ['description'],
  experiences: ['description', 'achievements'],
  projects: ['description', 'achievements'],
};

/**
 * 标量字段的路径就是字段名本身（strengths / skills）。
 *
 * 与 {@link itemPath} 一起构成路径约定的**唯一来源**：定位侧和渲染侧
 * 都用这两个函数生成 key，任何一边改了写法都会立刻对不上，不会静默错位。
 */
export function scalarPath(field: string) {
  return field;
}

/** 数组元素的路径，形如 `experiences.2.description` */
export function itemPath(section: string, index: number, field: string) {
  return `${section}.${index}.${field}`;
}

/** 路径约定中所有可能的 key；渲染侧用它判断某个字段该不该查建议 */
export function allPathsOf(content: Record<string, any>): string[] {
  const paths: string[] = [];
  for (const field of SCALAR_FIELDS) paths.push(scalarPath(field));
  for (const [section, fields] of Object.entries(SECTION_FIELDS)) {
    const rows = content?.[section];
    if (!Array.isArray(rows)) continue;
    rows.forEach((_row, index) => {
      for (const field of fields) paths.push(itemPath(section, index, field));
    });
  }
  return paths;
}

export interface LocatedSuggestions {
  /** 路径 → 挂在那个字段下的建议 */
  byPath: SuggestionMap;
  /** 建议 → 它被归到的路径，反向查用（用户点「使用」时要据此改草稿） */
  pathByItem: Map<FieldSuggestion, string>;
  /** 没能在草稿里定位到的建议：原文已改、或字段不在可润色范围内 */
  unlocated: FieldSuggestion[];
}

/**
 * 把建议按字段归位。
 *
 * 一条建议只归一处：某条原文若同时出现在多个字段里（同一条项目描述被复制到
 * 两段经历），按先遇到的字段挂，避免同一条建议在两个地方各渲染一遍、
 * 用户分别点了「使用」后提交两条一模一样的 decision。
 */
export function locateSuggestions(
  content: Record<string, any>,
  items?: FieldSuggestion[],
): LocatedSuggestions {
  const byPath: SuggestionMap = {};
  const pathByItem = new Map<FieldSuggestion, string>();
  const unlocated: FieldSuggestion[] = [];
  if (!items?.length) return { byPath, pathByItem, unlocated };

  const pending = new Set(items);

  const claim = (path: string, text: unknown) => {
    if (typeof text !== 'string' || !text) return;
    for (const item of [...pending]) {
      if (text.includes(item.originalValue)) {
        (byPath[path] ||= []).push(item);
        pathByItem.set(item, path);
        pending.delete(item);
      }
    }
  };

  for (const field of SCALAR_FIELDS) {
    claim(scalarPath(field), content?.[field]);
  }
  for (const [section, fields] of Object.entries(SECTION_FIELDS)) {
    const rows = content?.[section];
    if (!Array.isArray(rows)) continue;
    rows.forEach((row, index) => {
      if (!row || typeof row !== 'object') return;
      for (const field of fields) {
        claim(itemPath(section, index, field), row[field]);
      }
    });
  }

  unlocated.push(...pending);
  return { byPath, pathByItem, unlocated };
}

/**
 * 就地把一条建议写进草稿，返回是否写成功。
 *
 * 只替换命中的那一处，与后端 applyDecisions 的 `content.replace(originalValue,
 * finalValue)` 语义保持一致 —— 两边做同一件事，前端预览和后端固化才不会是两个结果。
 */
export function applySuggestion(
  content: Record<string, any>,
  path: string,
  suggestion: FieldSuggestion,
  finalValue: string,
): boolean {
  const segments = path.split('.');
  const hit = (text: unknown): text is string =>
    typeof text === 'string' && text.includes(suggestion.originalValue);

  if (segments.length === 1) {
    const key = segments[0]!;
    if (!hit(content[key])) return false;
    content[key] = content[key].replace(suggestion.originalValue, finalValue);
    return true;
  }

  const [section, indexText, field] = segments;
  const row = content?.[section!]?.[Number(indexText)];
  if (!row || typeof row !== 'object' || !hit(row[field!])) return false;
  row[field!] = row[field!].replace(suggestion.originalValue, finalValue);
  return true;
}
