import type { PortalHighlights, PortalHomeSections } from '#/api/cms/portal';

/** 门户内容区块的栏目元数据与文案生成 */

export interface PreviewSource {
  category?: string;
  contentType?: string;
  date?: string;
  department?: string;
  group?: string;
  id?: number;
  imageCount?: number;
  name?: string;
  tags?: string;
  title?: string;
  url?: string;
}

export type SectionKey = 'about' | 'banners' | 'news' | 'products' | 'techDocs';

export const SECTION_META: Record<SectionKey, { en: string; intro: string; title: string }> = {
  about: {
    en: 'About Tianxin',
    intro: '厂区风貌、集团简介、企业文化与荣誉资质，直观了解天信管业的生产规模、技术能力与工程资信。',
    title: '关于天信',
  },
  banners: {
    en: 'Featured',
    intro: '精选集团简介、管道防腐业务与技术资料等重点栏目内容，点击卡片可查看大图、文字说明与相关推荐。',
    title: '推荐阅读',
  },
  news: {
    en: 'News Center',
    intro: '汇集公司新闻、通知公告与政策动态，及时发布企业经营、合作交流与行业政策信息。',
    title: '新闻动态',
  },
  products: {
    en: 'Product Center',
    intro: '覆盖管道内防腐、外防腐、保温保冷与内衬系列产品，可根据输送介质、温度与敷设环境匹配工艺方案。',
    title: '产品中心',
  },
  techDocs: {
    en: 'Technical Documents',
    intro: '防腐保温相关技术文献、标准资料与工程案例，供工艺选型、方案编制与技术学习参考。',
    title: '技术资料',
  },
};

export const SECTION_KEYS: SectionKey[] = ['banners', 'about', 'products', 'techDocs', 'news'];

export const RELATED_TITLE: Record<SectionKey, string> = {
  about: '相关图片',
  banners: '相关推荐',
  news: '相关文章',
  products: '相关产品',
  techDocs: '相关资料',
};

/** 内容主题说明：按标题/标签关键词匹配，用于列表与详情文案 */
const TOPIC_TEXT: Array<[RegExp, string]> = [
  [
    /3PE|2PE|三层结构聚乙烯/i,
    '采用三层结构聚乙烯（3PE / 2PE）防腐工艺，由底层环氧粉末、中间层胶粘剂与外层聚乙烯复合而成，兼具优良的耐蚀性与抗机械损伤能力，适用于埋地长输油气管道。',
  ],
  [
    /超高分子量聚乙烯|内衬/,
    '管道内衬防腐工艺：在管柱内壁复合超高分子量聚乙烯等耐磨耐蚀内衬层，用于注水井井下管柱、含油污水管道等强磨损工况，可有效延长管柱使用寿命。',
  ],
  [/玻璃釉/, '玻璃釉内防腐：在钢管内壁烧结致密玻璃釉层，耐酸碱、耐介质腐蚀，适用于输送腐蚀性介质的管道。'],
  [
    /环氧陶瓷/,
    '环氧陶瓷内防腐：由环氧树脂与陶瓷骨料复合形成高硬度内衬层，耐磨性与耐蚀性优于普通涂层，适用于含砂、高流速介质管道。',
  ],
  [
    /石墨烯/,
    '石墨烯改性防腐涂层：利用石墨烯片层的物理屏蔽效应提升涂层致密性，在油管防腐中表现出更好的耐蚀与抗渗透性能。',
  ],
  [
    /钛纳米|碳锆|纳米/,
    '纳米复合涂层防腐：通过纳米颗粒改性涂层结构，提升涂层致密度与结合力，用于油管、套管的耐蚀防护。',
  ],
  [
    /钨合金/,
    '钨合金防腐油管、套管：采用合金化表面处理提升基体硬度与耐磨蚀性能，适用于偏磨与腐蚀并存的井下工况。',
  ],
  [/储罐/, '钢制储罐防腐：针对罐底、罐壁、罐顶不同的腐蚀环境分区设计防腐方案，包含表面处理与重防腐涂层体系。'],
  [
    /聚氨酯|保温|保冷|发泡/,
    '聚氨酯保温结构：以硬质聚氨酯泡沫为保温层、外护管为保护层，用于供热与输油管道的保温、保冷工程，可有效降低热损失。',
  ],
  [/检测/, '管道检测：包含管道内腐蚀检测、外检测与防腐层完整性评价，为管道安全运行与检修计划提供依据。'],
  [
    /厂区|车间|风貌/,
    '天信管业生产基地实景：包含生产车间、防腐保温生产线与成品堆场，直观展示企业的生产规模与现场管理能力。',
  ],
  [
    /集团简介|公司简介|企业简介/,
    '天信管业科技集团企业简介：长期专注于管道防腐、保温与内衬技术服务，业务覆盖油田、市政供热等领域。',
  ],
  [/企业文化/, '企业文化展示：反映天信管业的经营理念、质量方针与团队建设情况。'],
  [/荣誉|资质|认证/i, '资质与荣誉展示：包含防腐保温相关专业承包资质与行业认证，是承接工程项目的资信证明。'],
  [/招聘|人才/, '人才招聘公告：面向防腐保温技术与生产管理岗位的人才招募信息，可供求职者了解岗位方向。'],
  [/管道防腐/, '管道防腐业务：涵盖外防腐、内防腐与保温一体化工艺，可根据输送介质与敷设环境定制方案。'],
  [/PE管|聚乙烯管/i, '聚乙烯（PE）管材生产：采用挤出成型工艺生产 PE 管材，具备良好的柔韧性与耐化学腐蚀性能。'],
];

export function isPdfUrl(url?: string) {
  return !!url && /\.pdf(\?|$)/i.test(url);
}

/** 根据标签判断所属栏目 */
export function columnOf(source: PreviewSource) {
  const tags = source.tags ?? '';
  if (source.group) {
    return source.group;
  }
  if (tags.includes('公司新闻')) return '公司新闻';
  if (tags.includes('通知公告')) return '通知公告';
  if (tags.includes('推荐位')) return '推荐阅读';
  if (tags.startsWith('产品图')) return '产品中心';
  if (tags.startsWith('关于天信')) return '关于天信';
  if (source.contentType === 'PDF' || tags.startsWith('技术资料')) return '技术资料';
  if (source.department || source.date) return '政策动态';
  return source.category ?? '企业内容';
}

/** 组装文字描述 */
export function buildDescription(source: PreviewSource, title: string) {
  const text = `${title} ${source.tags ?? ''} ${source.category ?? ''}`;
  const topic = TOPIC_TEXT.find(([pattern]) => pattern.test(text));
  const column = columnOf(source);
  const parts: string[] = [];
  if (topic) {
    parts.push(topic[1]);
  }
  if (column === '公司新闻') {
    parts.push(
      `本条属于「公司新闻」栏目：${title}。记录天信管业与业主单位、科研院所之间的考察、洽谈与合作动态，反映企业在管道防腐保温领域的行业影响力。`,
    );
  } else if (column === '通知公告') {
    parts.push(`本条属于「通知公告」栏目：${title}。用于发布企业招聘、采购、招租等公开信息，具体安排以公告原文为准。`);
  } else if (column === '政策动态') {
    parts.push(
      `本条属于「政策动态」栏目，${source.department ? `发布单位：${source.department}；` : ''}${
        source.date ? `发布日期：${source.date}。` : ''
      }可在「智能检索」中查看该政策的完整原文与 AI 解读。`,
    );
  } else if (!topic && column === '技术资料') {
    parts.push('防腐保温相关技术资料，可供工艺选型、方案编制与技术学习参考。');
  }
  if (parts.length === 0) {
    parts.push(`天信管业「${source.category || column}」相关素材，可用于了解该栏目下的产品、工艺与企业信息。`);
  }
  return parts.join('');
}

/** 新闻动态：公司新闻 + 通知公告 + 政策动态 */
export function newsItems(sections: PortalHomeSections, highlights: PortalHighlights): PreviewSource[] {
  return [
    ...sections.news.map((item) => ({ ...item, group: '公司新闻' })),
    ...highlights.notices.map((item) => ({ ...item, group: '通知公告' })),
    ...sections.notices.map((item) => ({ ...item, group: '通知公告' })),
    ...highlights.policies.map((item) => ({ ...item, group: '政策动态' })),
  ];
}

/** 取某个栏目的全部条目 */
export function sectionItems(
  sections: PortalHomeSections,
  highlights: PortalHighlights,
  key: SectionKey,
): PreviewSource[] {
  switch (key) {
    case 'about': {
      return sections.about;
    }
    case 'banners': {
      return sections.banners;
    }
    case 'news': {
      return newsItems(sections, highlights);
    }
    case 'products': {
      return sections.products;
    }
    case 'techDocs': {
      return sections.techDocs;
    }
    default: {
      return [];
    }
  }
}

/** 详情页查询参数 */
export function detailQuery(item: PreviewSource, key: SectionKey) {
  return {
    id: item.id === undefined ? '' : String(item.id),
    section: key,
    title: item.title || item.name || '',
    url: item.url ?? '',
  };
}

export function matchItem(item: PreviewSource, url?: string, id?: string) {
  if (url && item.url === url) {
    return true;
  }
  return !!(id && item.id !== undefined && String(item.id) === id);
}

