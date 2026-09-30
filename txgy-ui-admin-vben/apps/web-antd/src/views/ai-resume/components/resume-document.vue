<script lang="ts" setup>
import { computed, ref, watch } from 'vue';

import { IconifyIcon } from '@vben/icons';

import { useSortable } from '@vueuse/integrations/useSortable';

import ResumeInlineField from './resume-inline-field.vue';
import { findTemplate } from './resume-templates';
import { RESUME_SECTION_MAP, resolveSectionOrder } from './resume-sections';

/**
 * 简历文档预览：把草稿内容渲染成一页「像简历」的文档。
 *
 * 这是工作区的中栏，诊断前后共用 —— 区别只在诊断后每个模块下面
 * 会多出一块「诊断建议」（参考站里也是内联在模块里的，不是浮层）。
 *
 * 空白时不是一片留白，而是铺出小节的占位骨架与灰色填写指引，版式对齐参考站：
 * 空简历打开就能看出「每一段该写什么」，而不是只有个名字挂在那儿。
 */

interface ResumeExperience {
  achievements?: string;
  company?: string;
  department?: string;
  description?: string;
  end?: string;
  id?: number | string;
  position?: string;
  start?: string;
}

interface ResumeProject {
  achievements?: string;
  description?: string;
  end?: string;
  highlight?: string;
  id?: number | string;
  name?: string;
  role?: string;
  start?: string;
}

interface ResumeEducation {
  degree?: string;
  end?: string;
  experience?: string;
  id?: number | string;
  major?: string;
  school?: string;
  schoolType?: string;
  start?: string;
}

/** 学生会/社团经历：字段与实习基本同形，单独列出来只是为了让编辑器好读 */
interface ResumeClub {
  description?: string;
  end?: string;
  id?: number | string;
  name?: string;
  role?: string;
  start?: string;
}

/** 所获荣誉：一条一行，只有名称和时间 */
interface ResumeHonor {
  date?: string;
  id?: number | string;
  name?: string;
}

export interface ResumeDocumentContent {
  avatar?: string;
  /** 出生年月，YYYY-MM。与门户求职资料同名字段 */
  birthMonth?: string;
  city?: string;
  clubs?: ResumeClub[];
  educationExperiences?: ResumeEducation[];
  email?: string;
  expectedPostName?: string;
  experiences?: ResumeExperience[];
  /** 性别：1 男 / 2 女，取值与门户求职资料一致 */
  gender?: number;
  /** 被用户隐藏的小节 key；存「隐藏了哪些」而不是「显示了哪些」，新增小节才会默认可见 */
  hiddenSections?: string[];
  honors?: ResumeHonor[];
  internships?: ResumeExperience[];
  mobile?: string;
  name?: string;
  /** 一页模式：压缩行距段距，尽量收在一页 */
  onePage?: boolean;
  pageFontSize?: string;
  pageMargin?: string;
  /** 行距与段距的整体缩放，0.8~1.4 */
  pageSpacing?: number;
  projects?: ResumeProject[];
  /**
   * 小节的展示顺序（key 数组）。用户在「页面布局」或中栏拖出来的。
   *
   * <p>与 `hiddenSections` 同构：都只存「用户显式表达过的部分」，
   * 缺失项由 resolveSectionOrder 补齐 —— 所以老简历没有这个字段也能正常渲染。</p>
   */
  sectionOrder?: string[];
  showAvatar?: boolean;
  skills?: string;
  strengths?: string;
  summary?: string;
  template?: string;
}

/** 一条「诊断建议」：挂在哪一块、给什么建议 */
interface SectionAdvice {
  /** 对应的模块名（教育经历 / 项目经历 …），服务端字段是中文模块名 */
  items: string[];
  module: string;
}

const props = withDefaults(
  defineProps<{
    content: ResumeDocumentContent;
    /** 诊断建议，按模块分组；为空表示还没诊断过 */
    advice?: SectionAdvice[];
    /** 是否已展开建议正文（参考站上可以「收起」） */
    showAdvice?: boolean;
    /**
     * 中栏工作区开启交互：悬停工具条（拖动/引用/隐藏/添加）、小节可拖拽排序。
     *
     * <p>**默认关闭**，因为本组件同时被列表页的预览弹窗复用（resume-preview-dialog.vue），
     * 那份是只读的，不该出现任何可以写草稿的入口。</p>
     */
    interactive?: boolean;
    /**
     * 交付态：只渲染「交出去」的内容，藏掉编辑器专用的那几样 ——
     * 填写指引、空值占位、占位骨架、诊断建议。与「打印/导出」所见一致。
     *
     * <p>预览弹窗要开这个：它展示的是「这份简历长什么样」，
     * 而不是「你可以照着填什么」，留着「大学之前的教育经历建议不写」这类教人写简历的话
     * 既不是简历内容，也容易被当成简历正文读。</p>
     *
     * <p>不复用 `interactive` 的原因：「不可交互」与「可交付」是两件事 ——
     * 现在恰好只读的那一处需要交付态，但把两件事绑在一个开关上，下一次改动就会踩。</p>
     */
    deliver?: boolean;
  }>(),
  { advice: () => [], deliver: false, interactive: false, showAdvice: true },
);

const emit = defineEmits<{
  /** 点「收起/展开」把建议折叠起来 */
  toggleAdvice: [];
  /** 隐藏某小节（写 hiddenSections） */
  hide: [key: string];
  /** 恢复某小节 */
  show: [key: string];
  /** 给数组型小节添加一条空条目 */
  add: [key: string];
  /** 清空某小节的内容（小节本身保留） */
  clear: [key: string];
  /** 点小节标题，请父组件把左栏编辑器定位过去 */
  open: [key: string];
  /** 引用某小节到右侧对话 */
  quote: [key: string];
}>();

/*
 * 每段的填写指引。文案取自参考站（deephire.cn 简历编辑器）的空简历预览，
 * 常驻显示在对应小节下方 —— 参考站也是这么做的：它是编辑态预览，
 * 指引是给用户看的，不是「空态才出现」。
 */
const HINTS = {
  clubs:
    '明确职位与职责，侧重描述策划、组织或执行的具体活动。突出如何调配资源、解决冲突或达成目标，避免流水账。工作多年者建议根据相关性适当精简。',
  education:
    '大学之前的教育经历建议不写。重点展示相关课程、交换经验，成绩优异可标出GPA或排名。若工作多年，清晰罗列背景即可，将篇幅留给其他模块。描述需简洁，突出专业性。',
  honors: '按含金量排序，写清奖项名称、级别与获奖时间。',
  internships:
    '采取“动词+任务+结果”的形式描述核心业务与贡献。强调你解决的问题或带来的提升，突出与申请岗位匹配的技能点，体现职场适应性及专业产出。',
  projects:
    '简述项目内容、个人职责及最终成果。内容需简洁清晰，重点突出与求职岗位匹配的实操经验与技能点。建议遵循“做了什么-怎么做-结果如何”的逻辑进行描述。',
  skills:
    '分类罗列硬技能（如编程、设计、办公软件等），建议标注熟练程度（如：精通、掌握）。优先列举岗位要求的关键词，确保每一项都能为你的专业度背书。',
  strengths:
    '篇幅宜短，总结与岗位匹配的经验和特长。避免堆砌形容词，结合实例展示沟通、协作或适应能力。保持排版美观，确保内容直击岗位痛点，提升简历整体含金量。',
  work: '按时间倒序。描述职责与成果时，多用数字说明成绩，突出专业素质。不知名企业可简述公司或产品。尽量对标岗位要求，确保HR能第一时间发现你的亮点。',
};

const contacts = computed(() =>
  [props.content.mobile, props.content.email].filter(Boolean),
);

/** 性别取值与门户求职资料一致：1 男 / 2 女 */
const GENDER_LABELS: Record<number, string> = { 1: '男', 2: '女' };

/**
 * 头部「男 / 女」「生日：YYYY/MM」两项。
 *
 * 空白骨架里本来就画了这两个占位（见下面的 else 分支），之前没人把它接上，
 * 于是填了性别和出生年月反而看不到 —— 填了没反应比不填更让人困惑。
 */
const metaItems = computed(() => {
  const out: Array<{ icon: string; text: string }> = [];
  const label = GENDER_LABELS[Number(props.content.gender)];
  if (label) out.push({ icon: 'lucide:user-round', text: label });
  const birth = String(props.content.birthMonth || '').trim();
  if (birth) out.push({ icon: 'lucide:cake', text: `生日：${birth}` });
  return out;
});

/** 头像。没上传时不占位，文档版式与之前完全一致 */
const avatarUrl = computed(() => String(props.content.avatar || '').trim());

/*
 * 当前模板。
 *
 * 按约定只换「版头样式 + 主色」，不动排版骨架 —— 28 套模板各写一套排版
 * 是不现实的，而缩略图能分辨的本来也只有这两样。主色经 CSS 变量下发，
 * 具体样式在下面按 archetype 分支。
 */
const tmpl = computed(() => findTemplate(String(props.content.template || '')));
const accent = computed(() => tmpl.value.accent);
const kind = computed(() => tmpl.value.archetype);

/* ── 页面布局：页边距 / 字号 / 是否显示头像 ── */

const pagePadding = computed(() => {
  switch (String(props.content.pageMargin || 'standard')) {
    case 'compact': {
      return '30px 34px 38px';
    }
    case 'loose': {
      return '58px 64px 72px';
    }
    default: {
      return '44px 48px 56px';
    }
  }
});

/*
 * 字号用 zoom 实现，而不是把每条 font-size 都改成 em 再乘系数 ——
 * 这个文档里有二十多处写死的 px，逐个换算既啰嗦又容易漏。
 * zoom 会连带缩放行高与间距，正好是「整页放大/缩小」想要的效果；
 * 目标环境是 Chromium 内核的管理端，支持没问题。
 */
const pageZoom = computed(() => {
  switch (String(props.content.pageFontSize || 'standard')) {
    case 'large': {
      return 1.1;
    }
    case 'small': {
      return 0.92;
    }
    default: {
      return 1;
    }
  }
});

/** 布局页可以把头像关掉，用来导出不带照片的版本 */
const showPhoto = computed(
  () => Boolean(avatarUrl.value) && props.content.showAvatar !== false,
);

/* ── 间距 / 一页模式 / 模块显隐 ── */

const spacingScale = computed(() => {
  const value = Number(props.content.pageSpacing ?? 1);
  if (!Number.isFinite(value)) return 1;
  return Math.min(1.4, Math.max(0.8, value));
});

const onePage = computed(() => props.content.onePage === true);

/**
 * 纵向节奏。间距滑块缩放大节与小节之间的留白；一页模式在它之上再压一档，
 * 并收紧行高 —— 「一页」靠的是密度，不只是把间距调小。
 */
const layoutVars = computed(() => ({
  '--line-height': onePage.value ? '1.6' : '1.8',
  '--sec-gap': onePage.value
    ? '14px'
    : `${Math.round(26 * spacingScale.value)}px`,
}));

/**
 * 模块显隐。存的是「隐藏了哪些」：以后新增小节时，老简历默认显示，
 * 不会因为清单里没有它而凭空消失。
 */
const hiddenSections = computed<string[]>(() =>
  Array.isArray(props.content.hiddenSections)
    ? props.content.hiddenSections
    : [],
);

function showSection(key: string) {
  return !hiddenSections.value.includes(key);
}

/**
 * 小节渲染顺序。
 *
 * 取自草稿的 `sectionOrder`（用户在「页面布局」或中栏拖出来的），
 * 老简历没有这个字段时由 resolveSectionOrder 给出默认顺序 —— 与改造前完全一致。
 * 它保证结果一定完整合法，所以模板里可以直接 v-for，不需要兜底。
 */
const resolvedOrder = computed(() => resolveSectionOrder(props.content));

/**
 * 当前渲染用的顺序，也是拖拽直接改的那一份。
 *
 * <p>不能把 `resolvedOrder` 本身交给拖拽库：它每次求值都新建数组，
 * 排序结果会落在一个马上被丢掉的临时数组上。</p>
 */
const order = ref<string[]>([...resolvedOrder.value]);

const dragging = ref(false);
const listEl = ref<HTMLElement>();

/*
 * 顺序的双向同步：草稿 ↔ 本地。
 *
 * 两边都能改：中栏拖拽、左栏「页面布局」拖拽改的是本地/草稿，而外部（换简历、
 * 会话恢复）会整份换掉草稿。必须防回环 —— 一版没防住的写法是「拖完把本地写回草稿」，
 * 结果被自己的反向 watcher 又倒灌回来，把刚拖好的顺序覆盖成旧的：
 *
 *     排序库在 onUpdate 里是 nextTick 才改数组的，而 onEnd 是同步的。
 *     于是 onEnd 里读到的还是**改动前**的顺序，写进草稿 → 反向 watcher 认为
 *     草稿变了 → 把旧顺序灌回本地 → 用户看到的还是原样。
 *
 * 用一个「最后应用值」标记把它解开：谁写的都记下来，另一边见到同一个值就不再动作。
 */
const appliedOrder = ref('');

watch(
  order,
  (next) => {
    const joined = next.join(',');
    if (joined === appliedOrder.value) return;
    appliedOrder.value = joined;
    // 只读预览不写草稿：列表页只是看，不该因为渲染一次就改动数据
    if (props.interactive) props.content.sectionOrder = [...next];
  },
  { deep: true, flush: 'post' },
);

watch(
  resolvedOrder,
  (next) => {
    const joined = next.join(',');
    if (joined === appliedOrder.value) return;
    appliedOrder.value = joined;
    order.value = [...next];
  },
  { immediate: true },
);

/*
 * 拖拽实例。
 *
 * 用 vueuse 的 useSortable（项目里 bpm 模块也是这么用的）：它直接作用于**已有容器**，
 * 按 v-for 渲染好的子节点接管排序。这一点很关键 —— vuedraggable 要求把列表项写进
 * 它的 #item 插槽，而这里的小节标记有二百多行，搬进插槽就得再复制一份。
 *
 * 只在交互态、且容器已挂载时初始化一次。
 */
/**
 * 交给排序库的元素。只读预览（列表页弹窗）走这个组件时不挂排序 ——
 * 那边的小节是 `display: contents`，既不该能拖，也不需要拖着鼠标的监听。
 */
const sortableHost = computed(() => (props.interactive ? listEl.value : null));

/*
 * 注意别把这个调用包在自己的 onMounted 里：useSortable 内部是 tryOnMounted 注册挂载钩子，
 * 在已挂载的钩子里再注册一次，那个钩子永远不会触发，拖拽会静默失效。
 * 传 ref + watchElement 让它自己去等元素出现。
 */
useSortable(sortableHost, order, {
  animation: 180,
  /*
   * 走指针事件而不是 HTML5 拖放。
   * 项目里另一处拖拽（mall/promotion 的 draggable）同样开着它：原生拖放在
   * 变形容器里定位会飘，还会带出浏览器自带的半透明拖影，盖掉我们自己的落点提示。
   */
  forceFallback: true,
  // 只有手柄能起拖：点标题要定位、点正文要编辑，都不能误触发排序
  handle: '.sec-drag',
  draggable: '.sec-slot',
  ghostClass: 'sec-ghost',
  // 顺序的写回由上面的 order watcher 负责（它与排序库的 onUpdate 同拍），
  // 这里只管拖拽态
  onEnd: () => {
    dragging.value = false;
  },
  onStart: () => {
    dragging.value = true;
  },
  watchElement: true,
});

function labelOf(key: string) {
  return RESUME_SECTION_MAP.get(key)?.label || key;
}

/** 「添加一条」只对数组型小节有意义 */
function isListSection(key: string) {
  return RESUME_SECTION_MAP.get(key)?.kind === 'list';
}

/**
 * 点小节标题 → 请父组件把左栏编辑器定位过去。
 *
 * 用委托而不是给 8 个 &lt;h2&gt; 各挂一次：本文件的小节标记已经很长，
 * 再逐个改一遍标题只为加同一个监听不值得。只有点在 H2 上才动作，
 * 点正文（后续要做行内编辑）不受影响。
 */
function onSectionClick(event: MouseEvent, key: string) {
  if (!props.interactive) return;
  const target = event.target as HTMLElement | null;
  if (target?.tagName !== 'H2') return;
  emit('open', key);
}

/**
 * 当前正在就地编辑的字段路径，全文档唯一。
 *
 * <p>**唯一**是这里的关键：只留一个输入框，中栏才不会退化成第二个编辑面板。
 * 点开下一个字段时，上一个会先 blur 提交（浏览器的焦点转移天然保证这一点），
 * 所以不会出现「两个输入框同时开着、改哪个算数」。</p>
 */
const activeField = ref('');

function activateField(path: string) {
  activeField.value = path;
}

function leaveField() {
  activeField.value = '';
}

/**
 * 条目字段的路径。带小节与下标，保证同一字段在不同条目、不同小节里互不串味 ——
 * 否则点开第二段实习的「公司」会把第一段的也一起变成输入框。
 */
function fieldPath(key: string, index: number, field: string) {
  return `${key}-${index}-${field}`;
}

/**
 * 「个人优势」的双写模型。
 *
 * <p>文档读的是 `summary || strengths`，而左栏编辑器绑的是 `strengths` ——
 * 中栏只写其中一个的话，用户改完切到左栏会看到旧值。这类「两边不一致」最难查，
 * 因为各自看上去都对。所以中栏编辑时两个一起写。</p>
 */
const strengthsModel = computed({
  get: () => String(props.content.summary || props.content.strengths || ''),
  set: (value: string) => {
    props.content.summary = value;
    props.content.strengths = value;
  },
});

/** 技能是整块编辑（点标签或任一行都进编辑态），见下方模板里的说明 */
function activateSkills() {
  activateField('skills');
}

/**
 * 这一节有没有真内容。
 *
 * 编辑器里空小节要铺骨架和填写指引——那是给人看着填的。导出（打印成 PDF）时
 * 这些东西一个都不能出现，但只剩一个光秃秃的小节标题更糟，所以整节一起收起来。
 * 判断只看「数组有没有元素 / 文本去掉空白后是不是空的」，不掺任何展示逻辑。
 */
function filled(value: unknown): boolean {
  if (Array.isArray(value)) return value.length > 0;
  if (typeof value === 'string') return value.trim().length > 0;
  return Boolean(value);
}

const educations = computed(() => props.content.educationExperiences || []);
const internships = computed(() => props.content.internships || []);
const clubs = computed(() => props.content.clubs || []);
const honors = computed(() => props.content.honors || []);
const projects = computed(() => props.content.projects || []);
const experiences = computed(() => props.content.experiences || []);

const summaryText = computed(() =>
  String(props.content.summary || props.content.strengths || '').trim(),
);

/*
 * 技能区在数据里有两种形态，渲染方式得跟着分：
 * - 短标签串：「办公软件、工程制图、项目管理、施工组织」（一行，顿号分隔）→ 标签
 * - 分条长文：「1. 除锈工艺：…、…；2. 防腐涂装技能：…」（多行，每行一条）→ 逐条成段
 *
 * 早先不区分、一律按顿号拆，结果长文里每一个顿号都断成一行
 * （「精准把控Sa2.0」/「Sa2.5」/「Sa3.0」各占一行），一份简历被撑成好几屏。
 * 换行才是真正的人工分隔符，顿号在长句里只是句读。
 */
const skillLines = computed(() =>
  String(props.content.skills || '')
    .split(/\n+/)
    .map((item) => item.trim())
    .filter(Boolean),
);

/** 只有「单行且足够短」才当标签看；长了就照原文成段，别再拆 */
const skillTags = computed(() => {
  const only = skillLines.value.length === 1 ? skillLines.value[0] : '';
  if (!only || only.length > 40) return [];
  return only
    .split(/[、,，]/)
    .map((item) => item.trim())
    .filter(Boolean);
});

/** 把模块名归一化后取建议：服务端可能给「教育经历」也可能给「教育经历-XX学校」 */
function adviceFor(module: string) {
  const match = props.advice.find((item) =>
    item.module.includes(module.slice(0, 2)),
  );
  return match?.items || [];
}

function range(start?: string, end?: string) {
  const from = (start || '').slice(0, 7);
  const to = (end || '').slice(0, 7);
  if (!from && !to) return '开始时间 - 结束时间';
  return `${from || '—'} - ${to || '至今'}`;
}

/**
 * 取值并保证是可渲染的文本。
 *
 * content 里的字段类型并不整齐：`projects[].highlight` 在库里是布尔，
 * 直接 `v-if="item.highlight"` 会往页面上打印一个字面的「true」。
 * 非字符串一律当没有，宁可少一行，也不要让简历里出现这种噪声。
 */
function text(value: unknown): string {
  if (typeof value === 'string') return value.trim();
  if (typeof value === 'number') return String(value);
  return '';
}
</script>

<template>
  <article
    class="resume-doc"
    :class="`is-${kind}`"
    :style="{ '--accent': accent }"
  >
    <div
      class="doc-page"
      :class="{ 'is-deliver': deliver, 'is-dragging': dragging }"
      :style="{ ...layoutVars, padding: pagePadding, zoom: pageZoom }"
    >
      <header class="doc-head">
        <div class="head-main">
          <ResumeInlineField
            v-model="content.name"
            :active="activeField"
            :editable="interactive"
            path="name"
            placeholder="你的姓名"
            tag="h1"
            @activate="activateField"
            @leave="leaveField"
          />
          <div v-if="contacts.length || metaItems.length" class="doc-contacts">
            <span v-for="item in metaItems" :key="item.text" class="contact">
              <IconifyIcon :icon="item.icon" />
              {{ item.text }}
            </span>
            <span class="contact">
              <IconifyIcon icon="lucide:phone" />
              <ResumeInlineField
                v-model="content.mobile"
                :active="activeField"
                :editable="interactive"
                path="mobile"
                placeholder="电话"
                @activate="activateField"
                @leave="leaveField"
              />
            </span>
            <span class="contact">
              <IconifyIcon icon="lucide:mail" />
              <ResumeInlineField
                v-model="content.email"
                :active="activeField"
                :editable="interactive"
                path="email"
                placeholder="邮箱"
                @activate="activateField"
                @leave="leaveField"
              />
            </span>
          </div>
          <p v-else class="doc-contacts">
            <span class="contact is-empty">男 / 女</span>
            <span class="contact is-empty">生日：YYYY/MM</span>
            <span class="contact is-empty">电话</span>
          </p>
          <p
            v-if="content.expectedPostName || interactive"
            class="doc-target"
          >
            求职意向 ·
            <ResumeInlineField
              v-model="content.expectedPostName"
              :active="activeField"
              :editable="interactive"
              path="expectedPostName"
              placeholder="填写期望职位"
              @activate="activateField"
              @leave="leaveField"
            />
          </p>
        </div>
        <img
          v-if="showPhoto"
          class="doc-avatar"
          :src="avatarUrl"
          :alt="content.name || '头像'"
        />
      </header>

      <!--
        8 个小节按 order 渲染，顺序来自草稿的 sectionOrder（用户在页面布局或中栏拖出来的）；
        老简历没有这个字段时 resolveSectionOrder 给出默认顺序，与改造前逐字一致。

        就地 v-for 化而不是抽成子组件：本文件的样式是 scoped 的，把标记挪进子组件会让
        这些元素丢掉父组件的 scope 属性，`.doc-section h2` / `.entry-head` / `.doc-text`
        十几条规则会静默失效（不报错、样式就是不生效），排查起来很费劲。
      -->
      <!--
        小节容器。交互态下它是排序库接管的元素 —— 只让 .sec-slot 可拖，
        顶部的 .doc-head 不在其中，所以版头不会被拖走。

        下面外层刻意与小节同缩进：正文有二百多行，为多包一层而整体重排会让 diff 没法看。
      -->
      <div ref="listEl" class="doc-body">
      <template v-for="key in order" :key="key">
      <div
        class="sec-slot"
        :class="{ 'is-interactive': interactive }"
        :data-section="key"
      >
      <!-- 悬停工具条：只在中栏（interactive）且该小节可见时出现 -->
      <div v-if="interactive && showSection(key)" class="sec-tools">
        <button
          class="sec-tool sec-drag"
          type="button"
          :title="`拖动${labelOf(key)}排序`"
          :aria-label="`拖动${labelOf(key)}排序`"
          @click.stop
        >
          <IconifyIcon icon="lucide:move" />
        </button>
        <button
          class="sec-tool"
          type="button"
          :title="`引用${labelOf(key)}，向 AI 提问`"
          :aria-label="`引用${labelOf(key)}`"
          @click.stop="emit('quote', key)"
        >
          <IconifyIcon icon="lucide:quote" />
        </button>
        <button
          class="sec-tool"
          type="button"
          :title="`隐藏${labelOf(key)}`"
          :aria-label="`隐藏${labelOf(key)}`"
          @click.stop="emit('hide', key)"
        >
          <IconifyIcon icon="lucide:eye-off" />
        </button>
        <button
          v-if="isListSection(key)"
          class="sec-tool"
          type="button"
          :title="`向${labelOf(key)}添加一条`"
          :aria-label="`添加${labelOf(key)}`"
          @click.stop="emit('add', key)"
        >
          <IconifyIcon icon="lucide:plus" />
        </button>
        <button
          class="sec-tool is-danger"
          type="button"
          :title="`清空${labelOf(key)}的内容`"
          :aria-label="`清空${labelOf(key)}`"
          @click.stop="emit('clear', key)"
        >
          <IconifyIcon icon="lucide:trash-2" />
        </button>
      </div>
      <section
        v-if="key === 'educationExperiences' && showSection(key)"
        class="doc-section"
        :class="{ 'is-empty': !filled(educations) }"
        @click="onSectionClick($event, key)"
      >
        <h2>教育经历</h2>
        <template v-if="educations.length">
          <div
            v-for="(item, index) in educations"
            :key="`edu-${index}`"
            class="doc-entry"
          >
            <div class="entry-head">
              <strong>
                <ResumeInlineField
                  v-model="item.school"
                  :active="activeField"
                  :editable="interactive"
                  :path="fieldPath(key, index, 'school')"
                  placeholder="学校"
                  @activate="activateField"
                  @leave="leaveField"
                />
              </strong>
              <!-- 时间的空态文案由 range() 给（含「至今」），只读时保持原样不走行内编辑 -->
              <span v-if="!interactive" class="entry-time">
                {{ range(item.start, item.end) }}
              </span>
              <span v-else class="entry-time">
                <ResumeInlineField
                  v-model="item.start"
                  :active="activeField"
                  :editable="interactive"
                  :path="fieldPath(key, index, 'start')"
                  placeholder="开始时间"
                  @activate="activateField"
                  @leave="leaveField"
                />
                -
                <ResumeInlineField
                  v-model="item.end"
                  :active="activeField"
                  :editable="interactive"
                  :path="fieldPath(key, index, 'end')"
                  placeholder="结束时间"
                  @activate="activateField"
                  @leave="leaveField"
                />
              </span>
            </div>
            <p class="entry-sub">
              <ResumeInlineField
                v-model="item.major"
                :active="activeField"
                :editable="interactive"
                :path="fieldPath(key, index, 'major')"
                placeholder="专业"
                @activate="activateField"
                @leave="leaveField"
              />
              <span v-if="item.major && item.degree"> · </span>
              <ResumeInlineField
                v-model="item.degree"
                :active="activeField"
                :editable="interactive"
                :path="fieldPath(key, index, 'degree')"
                placeholder="学历"
                @activate="activateField"
                @leave="leaveField"
              />
            </p>
            <ResumeInlineField
              v-if="interactive || text(item.experience)"
              v-model="item.experience"
              class="doc-text"
              :active="activeField"
              :editable="interactive"
              :path="fieldPath(key, index, 'experience')"
              multiline
              placeholder="在校经历"
              tag="p"
              @activate="activateField"
              @leave="leaveField"
            />
          </div>
        </template>
        <div v-else class="doc-entry is-placeholder">
          <div class="entry-head">
            <strong>学校</strong>
            <span class="entry-time">开始时间 - 结束时间</span>
          </div>
          <p class="entry-sub">专业 · 学历</p>
        </div>
        <p class="doc-hint">{{ HINTS.education }}</p>
        <div v-if="adviceFor('教育经历').length" class="advice">
          <div class="advice-head">
            <span><IconifyIcon icon="lucide:sparkles" /> 诊断建议</span>
            <button type="button" @click="emit('toggleAdvice')">
              {{ showAdvice ? '收起' : '展开' }}
            </button>
          </div>
          <ol v-if="showAdvice" class="advice-list">
            <li v-for="(item, index) in adviceFor('教育经历')" :key="index">{{ item }}</li>
          </ol>
        </div>
      </section>

      <!-- 实习经历 -->
      <section
        v-else-if="key === 'internships' && showSection(key)"
        class="doc-section"
        :class="{ 'is-empty': !filled(internships) }"
        @click="onSectionClick($event, key)"
      >
        <h2>实习经历</h2>
        <template v-if="internships.length">
          <div
            v-for="(item, index) in internships"
            :key="`int-${index}`"
            class="doc-entry"
          >
            <div class="entry-head">
              <strong>
                <ResumeInlineField
                  v-model="item.company"
                  :active="activeField"
                  :editable="interactive"
                  :path="fieldPath(key, index, 'company')"
                  placeholder="公司名称"
                  @activate="activateField"
                  @leave="leaveField"
                />
              </strong>
              <span v-if="!interactive" class="entry-time">{{ range(item.start, item.end) }}</span>
              <span v-else class="entry-time">
                <ResumeInlineField
                  v-model="item.start"
                  :active="activeField"
                  :editable="interactive"
                  :path="fieldPath(key, index, 'start')"
                  placeholder="开始时间"
                  @activate="activateField"
                  @leave="leaveField"
                />
                -
                <ResumeInlineField
                  v-model="item.end"
                  :active="activeField"
                  :editable="interactive"
                  :path="fieldPath(key, index, 'end')"
                  placeholder="结束时间"
                  @activate="activateField"
                  @leave="leaveField"
                />
              </span>
            </div>
            <p class="entry-sub">
              <ResumeInlineField
                v-model="item.position"
                :active="activeField"
                :editable="interactive"
                :path="fieldPath(key, index, 'position')"
                placeholder="实习岗位"
                @activate="activateField"
                @leave="leaveField"
              />
            </p>
            <ResumeInlineField
              v-if="interactive || text(item.description)"
              v-model="item.description"
              class="doc-text"
              :active="activeField"
              :editable="interactive"
              :path="fieldPath(key, index, 'description')"
              multiline
              placeholder="实习内容"
              tag="p"
              @activate="activateField"
              @leave="leaveField"
            />
            <ResumeInlineField
              v-if="interactive || text(item.achievements)"
              v-model="item.achievements"
              class="doc-text"
              :active="activeField"
              :editable="interactive"
              :path="fieldPath(key, index, 'achievements')"
              multiline
              placeholder="成果与数据"
              tag="p"
              @activate="activateField"
              @leave="leaveField"
            />
          </div>
        </template>
        <div v-else class="doc-entry is-placeholder">
          <div class="entry-head">
            <strong>公司名称</strong>
            <span class="entry-time">开始时间 - 结束时间</span>
          </div>
          <p class="entry-sub">实习岗位</p>
        </div>
        <p class="doc-hint">{{ HINTS.internships }}</p>
      </section>

      <!-- 学生会 / 社团经历 -->
      <section
        v-else-if="key === 'clubs' && showSection(key)"
        class="doc-section"
        :class="{ 'is-empty': !filled(clubs) }"
        @click="onSectionClick($event, key)"
      >
        <h2>学生会/社团经历</h2>
        <template v-if="clubs.length">
          <div v-for="(item, index) in clubs" :key="`club-${index}`" class="doc-entry">
            <div class="entry-head">
              <strong>
                <ResumeInlineField
                  v-model="item.name"
                  :active="activeField"
                  :editable="interactive"
                  :path="fieldPath(key, index, 'name')"
                  placeholder="社团名称"
                  @activate="activateField"
                  @leave="leaveField"
                />
              </strong>
              <span v-if="!interactive" class="entry-time">{{ range(item.start, item.end) }}</span>
              <span v-else class="entry-time">
                <ResumeInlineField
                  v-model="item.start"
                  :active="activeField"
                  :editable="interactive"
                  :path="fieldPath(key, index, 'start')"
                  placeholder="开始时间"
                  @activate="activateField"
                  @leave="leaveField"
                />
                -
                <ResumeInlineField
                  v-model="item.end"
                  :active="activeField"
                  :editable="interactive"
                  :path="fieldPath(key, index, 'end')"
                  placeholder="结束时间"
                  @activate="activateField"
                  @leave="leaveField"
                />
              </span>
            </div>
            <p class="entry-sub">
              <ResumeInlineField
                v-model="item.role"
                :active="activeField"
                :editable="interactive"
                :path="fieldPath(key, index, 'role')"
                placeholder="担任角色"
                @activate="activateField"
                @leave="leaveField"
              />
            </p>
            <ResumeInlineField
              v-if="interactive || text(item.description)"
              v-model="item.description"
              class="doc-text"
              :active="activeField"
              :editable="interactive"
              :path="fieldPath(key, index, 'description')"
              multiline
              placeholder="活动与职责"
              tag="p"
              @activate="activateField"
              @leave="leaveField"
            />
          </div>
        </template>
        <div v-else class="doc-entry is-placeholder">
          <div class="entry-head">
            <strong>社团名称</strong>
            <span class="entry-time">开始时间 - 结束时间</span>
          </div>
          <p class="entry-sub">担任角色</p>
        </div>
        <p class="doc-hint">{{ HINTS.clubs }}</p>
      </section>

      <!-- 所获荣誉 -->
      <section
        v-else-if="key === 'honors' && showSection(key)"
        class="doc-section"
        :class="{ 'is-empty': !filled(honors) }"
        @click="onSectionClick($event, key)"
      >
        <h2>所获荣誉</h2>
        <ul v-if="honors.length" class="doc-honors">
          <li v-for="(item, index) in honors" :key="`honor-${index}`">
            <ResumeInlineField
              v-model="item.name"
              :active="activeField"
              :editable="interactive"
              :path="fieldPath(key, index, 'name')"
              placeholder="奖项名称"
              @activate="activateField"
              @leave="leaveField"
            />
            <span v-if="interactive || text(item.date)" class="entry-time">
              <ResumeInlineField
                v-model="item.date"
                :active="activeField"
                :editable="interactive"
                :path="fieldPath(key, index, 'date')"
                placeholder="获奖时间"
                @activate="activateField"
                @leave="leaveField"
              />
            </span>
          </li>
        </ul>
        <p v-else class="entry-sub is-dashed">奖项名称 · 获奖时间</p>
        <p class="doc-hint">{{ HINTS.honors }}</p>
      </section>

      <!-- 个人优势 -->
      <!--
        is-empty 判的是 summaryText（= summary || strengths），必须与下面渲染的内容同源。
        原先判的是 content.strengths：附件上传解析出的正文是整段纯文本，只填进 summary、
        不填 strengths，于是这一节明明有内容却被判成空 —— 打印与交付态下整节被藏掉，
        导出的 PDF 直接丢掉正文（预览里也会看到「只有标题、正文全空」）。
      -->
      <section
        v-else-if="key === 'strengths' && showSection(key)"
        class="doc-section"
        :class="{ 'is-empty': !filled(summaryText) }"
        @click="onSectionClick($event, key)"
      >
        <h2>个人优势</h2>
        <ResumeInlineField
          v-if="interactive || summaryText"
          v-model="strengthsModel"
          class="doc-text"
          :active="activeField"
          :editable="interactive"
          path="strengths"
          multiline
          placeholder="一句话说清你的核心竞争力。"
          tag="p"
          @activate="activateField"
          @leave="leaveField"
        />
        <p v-else class="doc-text is-empty">一句话说清你的核心竞争力。</p>
        <p class="doc-hint">{{ HINTS.strengths }}</p>
      </section>

      <!-- 掌握技能 -->
      <section
        v-else-if="key === 'skills' && showSection(key)"
        class="doc-section"
        :class="{ 'is-empty': !filled(content.skills) }"
        @click="onSectionClick($event, key)"
      >
        <h2>掌握技能</h2>
        <!--
          技能整块编辑：点任一标签或任一行，整块换成绑 skills 的多行框。
          不做「改单个标签再拼回去」—— 按顿号 join 会把用户手工分条的换行结构毁掉，
          resume-editor-panel 里为这个坑写过注释。
        -->
        <ResumeInlineField
          v-if="interactive && activeField === 'skills'"
          v-model="content.skills"
          class="doc-skill-edit"
          :active="activeField"
          :editable="interactive"
          path="skills"
          multiline
          placeholder="办公软件、工程制图、项目管理…"
          tag="p"
          @activate="activateField"
          @leave="leaveField"
        />
        <template v-else>
          <div
            v-if="skillTags.length"
            class="doc-tags"
            :class="{ 'is-editable': interactive }"
            @click="activateSkills"
          >
            <span v-for="skill in skillTags" :key="skill">{{ skill }}</span>
          </div>
          <div
            v-else-if="skillLines.length"
            class="doc-skill-lines"
            :class="{ 'is-editable': interactive }"
            @click="activateSkills"
          >
            <p v-for="(line, index) in skillLines" :key="index">{{ line }}</p>
          </div>
          <p
            v-else
            class="doc-text"
            :class="{ 'is-empty': !interactive, 'is-editable': interactive }"
            @click="activateSkills"
          >
            办公软件、工程制图、项目管理…
          </p>
        </template>
        <p class="doc-hint">{{ HINTS.skills }}</p>
      </section>

      <!-- 项目经历 -->
      <section
        v-else-if="key === 'projects' && showSection(key)"
        class="doc-section"
        :class="{ 'is-empty': !filled(projects) }"
        @click="onSectionClick($event, key)"
      >
        <h2>项目经历</h2>
        <template v-if="projects.length">
          <div
            v-for="(item, index) in projects"
            :key="`proj-${index}`"
            class="doc-entry"
          >
            <div class="entry-head">
              <strong>
                <ResumeInlineField
                  v-model="item.name"
                  :active="activeField"
                  :editable="interactive"
                  :path="fieldPath(key, index, 'name')"
                  placeholder="项目名称"
                  @activate="activateField"
                  @leave="leaveField"
                />
              </strong>
              <span v-if="!interactive" class="entry-time">{{ range(item.start, item.end) }}</span>
              <span v-else class="entry-time">
                <ResumeInlineField
                  v-model="item.start"
                  :active="activeField"
                  :editable="interactive"
                  :path="fieldPath(key, index, 'start')"
                  placeholder="开始时间"
                  @activate="activateField"
                  @leave="leaveField"
                />
                -
                <ResumeInlineField
                  v-model="item.end"
                  :active="activeField"
                  :editable="interactive"
                  :path="fieldPath(key, index, 'end')"
                  placeholder="结束时间"
                  @activate="activateField"
                  @leave="leaveField"
                />
              </span>
            </div>
            <p class="entry-sub">
              <ResumeInlineField
                v-model="item.role"
                :active="activeField"
                :editable="interactive"
                :path="fieldPath(key, index, 'role')"
                placeholder="担任角色"
                @activate="activateField"
                @leave="leaveField"
              />
            </p>
            <ResumeInlineField
              v-if="interactive || text(item.description)"
              v-model="item.description"
              class="doc-text"
              :active="activeField"
              :editable="interactive"
              :path="fieldPath(key, index, 'description')"
              multiline
              placeholder="项目内容与职责"
              tag="p"
              @activate="activateField"
              @leave="leaveField"
            />
            <ResumeInlineField
              v-if="interactive || text(item.achievements)"
              v-model="item.achievements"
              class="doc-text"
              :active="activeField"
              :editable="interactive"
              :path="fieldPath(key, index, 'achievements')"
              multiline
              placeholder="项目成果"
              tag="p"
              @activate="activateField"
              @leave="leaveField"
            />
            <ResumeInlineField
              v-if="interactive || text(item.highlight)"
              v-model="item.highlight"
              class="doc-text"
              :active="activeField"
              :editable="interactive"
              :path="fieldPath(key, index, 'highlight')"
              multiline
              placeholder="项目亮点"
              tag="p"
              @activate="activateField"
              @leave="leaveField"
            />
          </div>
        </template>
        <div v-else class="doc-entry is-placeholder">
          <div class="entry-head">
            <strong>项目名称</strong>
            <span class="entry-time">开始时间 - 结束时间</span>
          </div>
          <p class="entry-sub">担任角色</p>
        </div>
        <p class="doc-hint">{{ HINTS.projects }}</p>
        <div v-if="adviceFor('项目经历').length" class="advice">
          <div class="advice-head">
            <span><IconifyIcon icon="lucide:sparkles" /> 诊断建议</span>
            <button type="button" @click="emit('toggleAdvice')">
              {{ showAdvice ? '收起' : '展开' }}
            </button>
          </div>
          <ol v-if="showAdvice" class="advice-list">
            <li v-for="(item, index) in adviceFor('项目经历')" :key="index">{{ item }}</li>
          </ol>
        </div>
      </section>

      <!-- 工作经历 -->
      <section
        v-else-if="key === 'experiences' && showSection(key)"
        class="doc-section"
        :class="{ 'is-empty': !filled(experiences) }"
        @click="onSectionClick($event, key)"
      >
        <h2>工作经历</h2>
        <template v-if="experiences.length">
          <div
            v-for="(item, index) in experiences"
            :key="`exp-${index}`"
            class="doc-entry"
          >
            <div class="entry-head">
              <strong>
                <ResumeInlineField
                  v-model="item.company"
                  :active="activeField"
                  :editable="interactive"
                  :path="fieldPath(key, index, 'company')"
                  placeholder="公司名称"
                  @activate="activateField"
                  @leave="leaveField"
                />
              </strong>
              <span v-if="!interactive" class="entry-time">{{ range(item.start, item.end) }}</span>
              <span v-else class="entry-time">
                <ResumeInlineField
                  v-model="item.start"
                  :active="activeField"
                  :editable="interactive"
                  :path="fieldPath(key, index, 'start')"
                  placeholder="开始时间"
                  @activate="activateField"
                  @leave="leaveField"
                />
                -
                <ResumeInlineField
                  v-model="item.end"
                  :active="activeField"
                  :editable="interactive"
                  :path="fieldPath(key, index, 'end')"
                  placeholder="结束时间"
                  @activate="activateField"
                  @leave="leaveField"
                />
              </span>
            </div>
            <p class="entry-sub">
              <ResumeInlineField
                v-model="item.position"
                :active="activeField"
                :editable="interactive"
                :path="fieldPath(key, index, 'position')"
                placeholder="岗位名称"
                @activate="activateField"
                @leave="leaveField"
              />
            </p>
            <ResumeInlineField
              v-if="interactive || text(item.description)"
              v-model="item.description"
              class="doc-text"
              :active="activeField"
              :editable="interactive"
              :path="fieldPath(key, index, 'description')"
              multiline
              placeholder="工作内容"
              tag="p"
              @activate="activateField"
              @leave="leaveField"
            />
            <ResumeInlineField
              v-if="interactive || text(item.achievements)"
              v-model="item.achievements"
              class="doc-text"
              :active="activeField"
              :editable="interactive"
              :path="fieldPath(key, index, 'achievements')"
              multiline
              placeholder="成果与数据"
              tag="p"
              @activate="activateField"
              @leave="leaveField"
            />
          </div>
        </template>
        <div v-else class="doc-entry is-placeholder">
          <div class="entry-head">
            <strong>公司名称</strong>
            <span class="entry-time">开始时间 - 结束时间</span>
          </div>
          <p class="entry-sub">岗位名称</p>
        </div>
        <p class="doc-hint">{{ HINTS.work }}</p>
        <div v-if="adviceFor('工作经历').length" class="advice">
          <div class="advice-head">
            <span><IconifyIcon icon="lucide:sparkles" /> 诊断建议</span>
            <button type="button" @click="emit('toggleAdvice')">
              {{ showAdvice ? '收起' : '展开' }}
            </button>
          </div>
          <ol v-if="showAdvice" class="advice-list">
            <li v-for="(item, index) in adviceFor('工作经历')" :key="index">{{ item }}</li>
          </ol>
        </div>
      </section>
      <!--
        隐藏的小节仍留在列表里，渲染成一行占位。
        拿掉它的话，写回草稿的 sectionOrder 就会缺这一项，resolveSectionOrder 只能把它补到末尾 ——
        用户一恢复显示就发现小节跑到最后去了。
      -->
      <button
        v-if="interactive && !showSection(key)"
        class="sec-hidden-row"
        type="button"
        @click.stop="emit('show', key)"
      >
        <IconifyIcon icon="lucide:eye-off" />
        <span>{{ labelOf(key) }}</span>
        <em>已隐藏 · 点击恢复</em>
      </button>
      </div>
      </template>
      </div>
    </div>
  </article>
</template>

<style scoped>
.resume-doc {
  display: flex;
  justify-content: center;
  padding: 28px 0 48px;
}

.doc-page {
  /* 800px 是舒适的一页简历宽度；栏比它窄时按栏宽收，不外溢 */
  width: min(800px, 100%);
  padding: 44px 48px 56px;
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 1px 3px rgb(10 27 51 / 6%);
  box-sizing: border-box;
}

.doc-head {
  display: flex;
  align-items: flex-start;
  gap: 20px;
  padding-bottom: 18px;
  border-bottom: 1px solid #ececec;
}

.head-main {
  flex: 1;
  min-width: 0;
}

/* 有头像时才出现在右上角；没上传的话这一块整个不渲染，版式维持原样 */
.doc-avatar {
  flex-shrink: 0;
  width: 88px;
  height: 88px;
  border-radius: 6px;
  object-fit: cover;
}

.doc-head h1 {
  margin: 0;
  color: #1a1a1a;
  font-size: 28px;
  font-weight: 700;
  line-height: 1.3;
}

.doc-contacts {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 18px;
  margin-top: 10px;
}

.contact {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: #5e5e5e;
  font-size: 13px;
  line-height: 1.5;
}

/* 空白简历的联系方式占位：淡到只是「这里该有东西」，不抢注意力 */
.contact.is-empty {
  color: #c8c8c8;
}

.doc-target {
  margin: 8px 0 0;
  color: #7b8a6b;
  font-size: 13px;
}

/*
 * 小节外壳。渲染顺序改成 v-for 之后每个小节外面多了一层 div，
 * 只读时用 display: contents 让它彻底退出布局 —— 小节在版面上仍与改造前完全同构，
 * 不会因为多一层盒子改变外边距折叠。
 *
 * 交互态才需要一个真实的盒子：悬停工具条要相对它绝对定位。
 */
.sec-slot {
  display: contents;
}

.sec-slot.is-interactive {
  position: relative;
  display: block;
}

/* 悬停工具条：浮在小节标题那一行的右侧 */
.sec-tools {
  position: absolute;
  z-index: 2;
  top: -4px;
  right: 0;
  display: flex;
  gap: 2px;
  padding: 2px;
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 1px 6px rgb(10 27 51 / 14%);
  opacity: 0;
  pointer-events: none;
  transition: opacity 0.15s ease-in-out;
}

/* :focus-within 一并点亮，让键盘用户也能拿到工具条，不只照顾鼠标 */
.sec-slot.is-interactive:hover > .sec-tools,
.sec-slot.is-interactive:focus-within > .sec-tools {
  opacity: 1;
  pointer-events: auto;
}

.sec-tool {
  display: flex;
  padding: 4px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: #e0913f;
  font-size: 14px;
  cursor: pointer;
  transition: background 0.15s ease-in-out;
}

.sec-tool:hover {
  background: #fdf4e8;
}

.sec-drag {
  cursor: move;
}

/* 清空是破坏性操作，悬停时才转成警示色，平时别抢注意力 */
.sec-tool.is-danger:hover {
  background: #fdeeee;
  color: #c0392b;
}

/* 拖拽时的落点提示 */
.sec-ghost {
  border-radius: 6px;
  background: #fdf4e8;
  opacity: 0.6;
  outline: 1px dashed #e0913f;
}

/*
 * 拖拽期间把整页缩放还原。
 *
 * .doc-page 上挂着 zoom（字号档位走的就是它），而排序库算出的拖拽几何是未缩放的，
 * 幽灵元素与落点会偏。这里必须写成 !important —— zoom 是行内样式，普通类规则压不住它。
 * 默认档位本来就是 1，所以绝大多数用户看不到这一下跳变。
 */
.doc-page.is-dragging {
  zoom: 1 !important;
}

/* 隐藏的小节：留在列表里的一行占位，可拖、可一键恢复 */
.sec-hidden-row {
  display: flex;
  align-items: center;
  gap: 6px;
  width: 100%;
  padding: 6px 0;
  border: 0;
  background: transparent;
  color: #b9b9b9;
  font-size: 13px;
  text-align: left;
  cursor: pointer;
  transition: color 0.15s ease-in-out;
}

.sec-hidden-row em {
  color: #cfcfcf;
  font-size: 12px;
  font-style: normal;
}

.sec-hidden-row:hover {
  color: #6e8d57;
}

/* 技能块可整块编辑时的悬停提示，与行内字段的虚线保持一致 */
.doc-tags.is-editable,
.doc-skill-lines.is-editable,
.doc-text.is-editable {
  cursor: text;
  border-radius: 4px;
  transition: background 0.15s ease-in-out;
}

.doc-tags.is-editable:hover,
.doc-skill-lines.is-editable:hover,
.doc-text.is-editable:hover {
  background: #fffaf4;
  box-shadow: 0 0 0 4px #fffaf4;
}

/* 章节标题用模板主色，和参考站的绿色小节标题一致 */
.doc-section {
  margin-top: var(--sec-gap, 26px);
}

.doc-section h2 {
  margin: 0 0 12px;
  padding-bottom: 6px;
  border-bottom: 1px solid #f0f0f0;
  color: var(--accent, #4e6840);
  font-size: 15px;
  font-weight: 600;
  line-height: 1.4;
}

.doc-entry + .doc-entry {
  margin-top: 18px;
}

/* 空白骨架：只有标签没有内容，用浅色示意「这里要填什么」 */
.doc-entry.is-placeholder {
  margin-top: 0;
}

.doc-entry.is-placeholder strong,
.doc-entry.is-placeholder .entry-sub {
  color: #c8c8c8;
  font-weight: 500;
}

.doc-entry.is-placeholder .entry-time {
  color: #d4d4d4;
}

.entry-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 16px;
}

.entry-head strong {
  color: #292929;
  font-size: 15px;
  font-weight: 600;
  line-height: 1.5;
}

.entry-time {
  flex-shrink: 0;
  color: #949494;
  font-size: 13px;
  line-height: 1.5;
}

.entry-sub {
  margin: 3px 0 0;
  color: #7d7d7d;
  font-size: 13px;
  line-height: 1.6;
}

.entry-sub.is-dashed {
  color: #c8c8c8;
}

.doc-text {
  margin: 6px 0 0;
  color: #4a4a4a;
  font-size: 13px;
  line-height: var(--line-height, 1.8);
  white-space: pre-wrap;
}

.doc-text.is-empty {
  color: #c8c8c8;
}

/* 填写指引：常驻的浅灰说明，参考站在编辑态一直显示 */
.doc-hint {
  margin: 8px 0 0;
  color: #c2c2c2;
  font-size: 12px;
  line-height: 1.75;
}

.doc-honors {
  margin: 0;
  padding: 0;
  list-style: none;
}

.doc-honors li {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 16px;
  color: #4a4a4a;
  font-size: 13px;
  line-height: 1.9;
}

.doc-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.doc-tags span {
  padding: 3px 10px;
  border-radius: 6px;
  background: #f5f7f6;
  color: #333;
  font-size: 13px;
  font-weight: 500;
  line-height: 26px;
}

.doc-skill-lines p {
  margin: 0;
  color: #4a4a4a;
  font-size: 13px;
  line-height: 1.9;
}

.doc-skill-lines p + p {
  margin-top: 2px;
}

/* ── 内联诊断建议 ── */
.advice {
  margin-top: 16px;
  padding: 12px 14px;
  border-radius: 8px;
  background: #f5f8f1;
}

.advice-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.advice-head span {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: #4e6840;
  font-size: 13px;
  font-weight: 600;
}

.advice-head button {
  padding: 0;
  border: 0;
  background: transparent;
  color: #8a8a8a;
  font-size: 12px;
  cursor: pointer;
}

.advice-list {
  margin: 8px 0 0;
  padding-left: 18px;
  color: #5e5e5e;
  font-size: 13px;
  line-height: 1.8;
}

/* ── 模板：版头与主色随 archetype 变，排版骨架不动 ── */

/* 顶部横带：整条主色，姓名反白 */
.is-banner .doc-head {
  margin: -44px -48px 18px;
  padding: 26px 48px 20px;
  border-bottom: 0;
  background: var(--accent, #4e6840);
}

.is-banner .doc-head h1,
.is-banner .contact,
.is-banner .doc-target {
  color: #fff;
}

/* 左侧竖栏：整页左边压一条主色 */
.is-sidebar .doc-page {
  border-left: 10px solid var(--accent, #4e6840);
}

/* 大标题：姓名放大，下面压一条主色 */
.is-modern .doc-head h1 {
  font-size: 34px;
}

.is-modern .doc-head {
  border-bottom: 2px solid var(--accent, #4e6840);
}

/* 表格化：小节线加粗，靠线条区分层级 */
.is-tabular .doc-section h2 {
  border-bottom: 1.5px solid var(--accent, #4e6840);
  color: #3f4a44;
}

/* 极简：去掉所有装饰线，只留主色小标题 */
.is-minimal .doc-head {
  border-bottom-color: transparent;
}

.is-minimal .doc-section h2 {
  border-bottom-color: transparent;
  padding-bottom: 0;
}

@media (max-width: 900px) {
  .doc-page {
    padding: 28px 22px 36px;
  }

  .is-banner .doc-head {
    margin: -28px -22px 18px;
    padding: 22px;
  }
}

/*
 * ── 交付态（预览弹窗）：与打印同一套口径，只是不需要等 @media print ──
 *
 * 下面这两条要和紧跟其后的 @media print 里的同名规则保持一致：
 * 两处说的都是「交出去的简历」该长什么样，分开写是因为触发条件不同
 * （一个是属性，一个是打印），漏了任何一处都会让预览和导出对不上。
 */
.doc-page.is-deliver .doc-hint,
.doc-page.is-deliver .is-empty,
.doc-page.is-deliver .is-placeholder,
.doc-page.is-deliver .advice {
  display: none !important;
}

.doc-page.is-deliver .doc-section.is-empty {
  display: none !important;
}

/*
 * ── 打印：只留「交出去」的内容 ──
 *
 * 下面这些在编辑器里都有用，但它们服务的是「看着填」，不是「交出去」：
 * 填写指引、空白骨架、行内占位、诊断建议 —— 导出成 PDF 时一个都不能出现。
 * （这些是在真正生成 PDF 时才发现的：早先只验了工作区外壳被隐藏，
 *   结果导出的简历里混着「大学之前的教育经历建议不写」这类教人写简历的话。）
 */
@media print {
  .doc-hint,
  .is-empty,
  .is-placeholder,
  .advice {
    display: none !important;
  }

  /* 内容为空的小节整节收掉：光剩一个「实习经历」标题比留白更像出错 */
  .doc-section.is-empty {
    display: none !important;
  }

  /*
   * 中栏的交互壳子一律不进 PDF：悬停工具条、隐藏占位行。
   * 它们只有工作区（interactive）才渲染，但导出走的是同一个 DOM —— 不加这条就会印出来。
   */
  .sec-tools,
  .sec-hidden-row {
    display: none !important;
  }

  /* 兜底：万一打印瞬间某个字段还停在编辑态（行内输入框），也别印出个输入框 */
  .resume-doc input,
  .resume-doc textarea {
    padding: 0 !important;
    border: 0 !important;
    background: transparent !important;
    appearance: none;
  }
}
</style>
