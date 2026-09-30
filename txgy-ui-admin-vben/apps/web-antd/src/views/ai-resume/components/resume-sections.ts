/**
 * 简历小节的元数据与顺序 —— 全项目唯一来源。
 *
 * <p>改造前这套东西抄了三份，全靠注释手工对齐，改一处要记得改另两处：</p>
 * <ul>
 *   <li>`resume-document.vue` 模板里 8 段硬编码的 `&lt;section&gt;`</li>
 *   <li>`resume-docx.ts` 的 `SECTIONS` 常量</li>
 *   <li>`resume-layout-panel.vue` 的 `SECTIONS` 常量</li>
 * </ul>
 *
 * <p>现在顺序还多了一个来源：用户在「页面布局」或中栏拖出来的 `content.sectionOrder`。
 * 两者由 `resolveSectionOrder()` 合成，消费方只管拿结果。</p>
 */

/** `list` 小节是数组，可以「添加一条」；`text` 小节是一段文字，没有添加动作 */
export type ResumeSectionKind = 'list' | 'text';

export interface ResumeSection {
  /** content 里的字段名，同时也是小节的稳定标识 */
  key: string;
  /** 文档小节标题、Word 小节标题、布局面板行名共用这一个 */
  label: string;
  icon: string;
  kind: ResumeSectionKind;
  /**
   * 诊断建议挂在哪一块。服务端字段是中文模块名，且只有这三节有建议块
   * （见 resume-document.vue 的 adviceFor）。
   */
  adviceModule?: string;
  /**
   * 左栏编辑器里它属于哪个折叠分区。点中栏标题要先把对应分区展开。
   * 注意 `strengths` 落在 `basic` 里 —— 这是唯一一处「分区名 ≠ 小节名」，
   * 所以这层映射必须写死在这里，不能靠 key 拼字符串。
   */
  editorSection: string;
}

/**
 * 8 个小节，**顺序即默认版式顺序**。
 *
 * <p>滚动锚点统一用 `key` 本身（左栏编辑器上挂 `data-anchor="<key>"`），
 * 所以不再单列一个 anchor 字段 —— 少一个能写错的地方。</p>
 */
export const RESUME_SECTIONS: ResumeSection[] = [
  {
    editorSection: 'edu',
    icon: 'lucide:graduation-cap',
    key: 'educationExperiences',
    kind: 'list',
    label: '教育经历',
    adviceModule: '教育经历',
  },
  {
    editorSection: 'intern',
    icon: 'lucide:briefcase',
    key: 'internships',
    kind: 'list',
    label: '实习经历',
  },
  {
    editorSection: 'club',
    icon: 'lucide:users',
    key: 'clubs',
    kind: 'list',
    label: '学生会/社团经历',
  },
  {
    editorSection: 'honor',
    icon: 'lucide:award',
    key: 'honors',
    kind: 'list',
    label: '所获荣誉',
  },
  {
    editorSection: 'basic',
    icon: 'lucide:user-round',
    key: 'strengths',
    kind: 'text',
    label: '个人优势',
  },
  {
    editorSection: 'skill',
    icon: 'lucide:wrench',
    key: 'skills',
    kind: 'text',
    label: '掌握技能',
  },
  {
    editorSection: 'proj',
    icon: 'lucide:folder-kanban',
    key: 'projects',
    kind: 'list',
    label: '项目经历',
    adviceModule: '项目经历',
  },
  {
    editorSection: 'work',
    icon: 'lucide:building-2',
    key: 'experiences',
    kind: 'list',
    label: '工作经历',
    adviceModule: '工作经历',
  },
];

export const RESUME_SECTION_MAP: Map<string, ResumeSection> = new Map(
  RESUME_SECTIONS.map((section) => [section.key, section]),
);

/** 默认顺序的 key 清单 */
const CANONICAL_ORDER: string[] = RESUME_SECTIONS.map((section) => section.key);

/**
 * 把草稿里存的顺序归一化成一份**一定完整、一定合法**的 key 数组。只读，不改入参。
 *
 * <p>四种情况一次解决：</p>
 * <ol>
 *   <li><b>老简历没有这个字段</b>（改造前存的所有简历）→ 返回默认顺序，
 *       与改造前的渲染结果完全一致；</li>
 *   <li><b>将来新增小节</b> → 它不在任何老数据的清单里 → 自动补到末尾；
 *       又因为 `hiddenSections` 是「存隐藏了哪些」，它也默认可见；</li>
 *   <li><b>清单里有脏值或已废弃的 key</b> → 过滤掉，永远渲染不出不存在的小节；</li>
 *   <li><b>清单不完整</b>（脚本写坏、并发覆盖）→ 补齐，永不丢小节。</li>
 * </ol>
 *
 * <p>所以消费方可以放心地拿它去 `v-for`，不需要再判空或兜底。</p>
 */
export function resolveSectionOrder(
  content?: Record<string, any>,
): string[] {
  const raw = content?.sectionOrder;
  const seen = new Set<string>();
  const order: string[] = [];

  if (Array.isArray(raw)) {
    for (const key of raw) {
      if (typeof key !== 'string') continue; // 脏数据直接丢
      if (!RESUME_SECTION_MAP.has(key)) continue; // 已废弃的小节
      if (seen.has(key)) continue; // 去重：重复项会让 v-for 的 key 撞车
      seen.add(key);
      order.push(key);
    }
  }

  for (const key of CANONICAL_ORDER) {
    if (!seen.has(key)) order.push(key);
  }
  return order;
}

/**
 * 把某小节的内容渲染成一段可读文本。
 *
 * <p>用于「引用模块」：用户指着某一节提问时，把**这一节**的事实交给模型，
 * 而不是把整份简历塞过去 —— 后者的注意力会散，也更容易让模型答非所问。</p>
 *
 * <p>字段口径与 resume-document.vue 的渲染保持一致（「个人优势」同样取
 * `summary || strengths`、描述按换行分条），这样引用过去的内容与用户在预览里
 * 看到的是同一份，不会出现「我引用的和你说的是两回事」。</p>
 */
export function sectionText(
  content: Record<string, any> | undefined,
  key: string,
): string {
  if (!content) return '';

  /** 与文档的 text() 同口径：非字符串一律当没有，免得印出一个字面的 true */
  const t = (value: unknown) =>
    typeof value === 'string'
      ? value.trim()
      : typeof value === 'number'
        ? String(value)
        : '';
  const lines = (value: unknown) =>
    t(value)
      .split(/\n+/)
      .map((line) => line.trim())
      .filter(Boolean);
  const rows = (value: unknown) => (Array.isArray(value) ? value : []);
  const when = (start: unknown, end: unknown) =>
    t(start) ? `${t(start)}-${t(end) || '至今'}` : '';
  const join = (...parts: string[]) => parts.filter(Boolean).join(' ');

  switch (key) {
    case 'clubs': {
      return rows(content.clubs)
        .map((c) => join(t(c.name), t(c.role), when(c.start, c.end), ...lines(c.description)))
        .filter(Boolean)
        .join('\n');
    }
    case 'educationExperiences': {
      return rows(content.educationExperiences)
        .map((e) =>
          join(t(e.school), t(e.major), t(e.degree), when(e.start, e.end), ...lines(e.experience)),
        )
        .filter(Boolean)
        .join('\n');
    }
    case 'experiences':
    case 'internships': {
      return rows(content[key])
        .map((e) =>
          join(
            t(e.company),
            t(e.position),
            when(e.start, e.end),
            ...lines(e.description),
            ...lines(e.achievements),
          ),
        )
        .filter(Boolean)
        .join('\n');
    }
    case 'honors': {
      return rows(content.honors)
        .map((h) => join(t(h.name), t(h.date)))
        .filter(Boolean)
        .join('\n');
    }
    case 'projects': {
      return rows(content.projects)
        .map((p) =>
          join(
            t(p.name),
            t(p.role),
            when(p.start, p.end),
            ...lines(p.description),
            ...lines(p.achievements),
            ...lines(p.highlight),
          ),
        )
        .filter(Boolean)
        .join('\n');
    }
    case 'skills': {
      return t(content.skills);
    }
    case 'strengths': {
      // 与文档一致：优先 summary，没有再退回 strengths
      return t(content.summary) || t(content.strengths);
    }
    default: {
      return '';
    }
  }
}

/**
 * 新增一条空白条目。
 *
 * <p>字段集抄自 `resume-editor-panel.vue` 的各 add* 函数 —— 左右两侧都能加条目，
 * 形状不一致的话「中栏加的在左栏编辑不了」这种问题很难查。</p>
 *
 * @returns `text` 小节没有条目，返回 undefined
 */
export function createBlankItem(key: string): Record<string, any> | undefined {
  switch (key) {
    case 'educationExperiences': {
      return { degree: '', experience: '', major: '', school: '' };
    }
    case 'internships':
    case 'experiences': {
      return { achievements: '', company: '', description: '', position: '' };
    }
    case 'clubs':
    case 'projects': {
      return { description: '', name: '', role: '' };
    }
    case 'honors': {
      return { date: '', name: '' };
    }
    default: {
      return undefined;
    }
  }
}
