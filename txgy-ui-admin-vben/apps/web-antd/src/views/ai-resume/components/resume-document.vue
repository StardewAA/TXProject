<script lang="ts" setup>
import { computed } from 'vue';

import { IconifyIcon } from '@vben/icons';

import { findTemplate } from './resume-templates';

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
  }>(),
  { advice: () => [], showAdvice: true },
);

const emit = defineEmits<{
  /** 点「收起/展开」把建议折叠起来 */
  toggleAdvice: [];
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
      :style="{ ...layoutVars, padding: pagePadding, zoom: pageZoom }"
    >
      <header class="doc-head">
        <div class="head-main">
          <h1>{{ content.name || '你的姓名' }}</h1>
          <div v-if="contacts.length || metaItems.length" class="doc-contacts">
            <span v-for="item in metaItems" :key="item.text" class="contact">
              <IconifyIcon :icon="item.icon" />
              {{ item.text }}
            </span>
            <span v-for="(item, index) in contacts" :key="index" class="contact">
              <IconifyIcon :icon="index === 0 ? 'lucide:phone' : 'lucide:mail'" />
              {{ item }}
            </span>
          </div>
          <p v-else class="doc-contacts">
            <span class="contact is-empty">男 / 女</span>
            <span class="contact is-empty">生日：YYYY/MM</span>
            <span class="contact is-empty">电话</span>
          </p>
          <p v-if="content.expectedPostName" class="doc-target">
            求职意向 · {{ content.expectedPostName }}
          </p>
        </div>
        <img
          v-if="showPhoto"
          class="doc-avatar"
          :src="avatarUrl"
          :alt="content.name || '头像'"
        />
      </header>

      <!-- 教育经历 -->
      <section
        v-if="showSection('educationExperiences')"
        class="doc-section"
        :class="{ 'is-empty': !filled(educations) }"
      >
        <h2>教育经历</h2>
        <template v-if="educations.length">
          <div
            v-for="(item, index) in educations"
            :key="`edu-${index}`"
            class="doc-entry"
          >
            <div class="entry-head">
              <strong>{{ item.school || '学校' }}</strong>
              <span class="entry-time">{{ range(item.start, item.end) }}</span>
            </div>
            <p class="entry-sub">
              {{ [item.major, item.degree].filter(Boolean).join(' · ') || '专业 · 学历' }}
            </p>
            <p v-if="text(item.experience)" class="doc-text">{{ text(item.experience) }}</p>
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
        v-if="showSection('internships')"
        class="doc-section"
        :class="{ 'is-empty': !filled(internships) }"
      >
        <h2>实习经历</h2>
        <template v-if="internships.length">
          <div
            v-for="(item, index) in internships"
            :key="`int-${index}`"
            class="doc-entry"
          >
            <div class="entry-head">
              <strong>{{ item.company || '公司名称' }}</strong>
              <span class="entry-time">{{ range(item.start, item.end) }}</span>
            </div>
            <p class="entry-sub">{{ item.position || '实习岗位' }}</p>
            <p v-if="text(item.description)" class="doc-text">{{ text(item.description) }}</p>
            <p v-if="text(item.achievements)" class="doc-text">{{ text(item.achievements) }}</p>
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
        v-if="showSection('clubs')"
        class="doc-section"
        :class="{ 'is-empty': !filled(clubs) }"
      >
        <h2>学生会/社团经历</h2>
        <template v-if="clubs.length">
          <div v-for="(item, index) in clubs" :key="`club-${index}`" class="doc-entry">
            <div class="entry-head">
              <strong>{{ item.name || '社团名称' }}</strong>
              <span class="entry-time">{{ range(item.start, item.end) }}</span>
            </div>
            <p class="entry-sub">{{ item.role || '担任角色' }}</p>
            <p v-if="text(item.description)" class="doc-text">{{ text(item.description) }}</p>
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
        v-if="showSection('honors')"
        class="doc-section"
        :class="{ 'is-empty': !filled(honors) }"
      >
        <h2>所获荣誉</h2>
        <ul v-if="honors.length" class="doc-honors">
          <li v-for="(item, index) in honors" :key="`honor-${index}`">
            <span>{{ text(item.name) || '奖项名称' }}</span>
            <span v-if="text(item.date)" class="entry-time">{{ text(item.date) }}</span>
          </li>
        </ul>
        <p v-else class="entry-sub is-dashed">奖项名称 · 获奖时间</p>
        <p class="doc-hint">{{ HINTS.honors }}</p>
      </section>

      <!-- 个人优势 -->
      <section
        v-if="showSection('strengths')"
        class="doc-section"
        :class="{ 'is-empty': !filled(content.strengths) }"
      >
        <h2>个人优势</h2>
        <p v-if="summaryText" class="doc-text">{{ summaryText }}</p>
        <p v-else class="doc-text is-empty">一句话说清你的核心竞争力。</p>
        <p class="doc-hint">{{ HINTS.strengths }}</p>
      </section>

      <!-- 掌握技能 -->
      <section
        v-if="showSection('skills')"
        class="doc-section"
        :class="{ 'is-empty': !filled(content.skills) }"
      >
        <h2>掌握技能</h2>
        <div v-if="skillTags.length" class="doc-tags">
          <span v-for="skill in skillTags" :key="skill">{{ skill }}</span>
        </div>
        <div v-else-if="skillLines.length" class="doc-skill-lines">
          <p v-for="(line, index) in skillLines" :key="index">{{ line }}</p>
        </div>
        <p v-else class="doc-text is-empty">办公软件、工程制图、项目管理…</p>
        <p class="doc-hint">{{ HINTS.skills }}</p>
      </section>

      <!-- 项目经历 -->
      <section
        v-if="showSection('projects')"
        class="doc-section"
        :class="{ 'is-empty': !filled(projects) }"
      >
        <h2>项目经历</h2>
        <template v-if="projects.length">
          <div
            v-for="(item, index) in projects"
            :key="`proj-${index}`"
            class="doc-entry"
          >
            <div class="entry-head">
              <strong>{{ item.name || '项目名称' }}</strong>
              <span class="entry-time">{{ range(item.start, item.end) }}</span>
            </div>
            <p v-if="text(item.role)" class="entry-sub">{{ text(item.role) }}</p>
            <p v-if="text(item.description)" class="doc-text">{{ text(item.description) }}</p>
            <p v-if="text(item.achievements)" class="doc-text">{{ text(item.achievements) }}</p>
            <p v-if="text(item.highlight)" class="doc-text">{{ text(item.highlight) }}</p>
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
        v-if="showSection('experiences')"
        class="doc-section"
        :class="{ 'is-empty': !filled(experiences) }"
      >
        <h2>工作经历</h2>
        <template v-if="experiences.length">
          <div
            v-for="(item, index) in experiences"
            :key="`exp-${index}`"
            class="doc-entry"
          >
            <div class="entry-head">
              <strong>{{ item.company || '公司名称' }}</strong>
              <span class="entry-time">{{ range(item.start, item.end) }}</span>
            </div>
            <p v-if="text(item.position)" class="entry-sub">{{ text(item.position) }}</p>
            <p v-if="text(item.description)" class="doc-text">{{ text(item.description) }}</p>
            <p v-if="text(item.achievements)" class="doc-text">{{ text(item.achievements) }}</p>
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
}
</style>
