<script lang="ts" setup>
import { computed, nextTick, reactive, ref } from 'vue';

import { IconifyIcon } from '@vben/icons';

import { message } from 'ant-design-vue';

import { uploadHrmRecruitFile } from '#/api/hrm/portal/recruit/file';

import ResumeField from './resume-field.vue';
import type { ResumeDocumentContent } from './resume-document.vue';
import { RESUME_SECTION_MAP } from './resume-sections';
import {
  itemPath,
  scalarPath,
  type FieldSuggestion,
  type SuggestionMap,
} from './resume-suggestions';

/**
 * 工作区左栏：分区可折叠的结构化编辑器。
 *
 * 字段名跟着 HRM 简历 content 的实际结构走（见 hrm_personal_resume.content），
 * 不是照着参考站的字面抄 —— 参考站有性别/微信，我们的 content 里没有这两项，
 * 硬加上去只会编辑到空气。
 *
 * 这里直接改传入的对象（`content` 是父级的 reactive 对象），不另存一份副本：
 * 中栏预览要跟着打字实时变，走 emit 再回灌会慢半拍。
 */

interface ExperienceItem {
  achievements?: string;
  company?: string;
  department?: string;
  description?: string;
  end?: string;
  id?: number | string;
  industry?: string;
  position?: string;
  start?: string;
}

interface ProjectItem {
  achievements?: string;
  description?: string;
  end?: string;
  highlight?: string;
  id?: number | string;
  name?: string;
  role?: string;
  start?: string;
}

interface EducationItem {
  degree?: string;
  end?: string;
  experience?: string;
  id?: number | string;
  major?: string;
  school?: string;
  schoolType?: string;
  start?: string;
}

/** 实习经历：字段与工作经历同形，靠所在分区区分语义 */
type InternshipItem = ExperienceItem;

/** 学生会/社团经历 */
interface ClubItem {
  description?: string;
  end?: string;
  id?: number | string;
  name?: string;
  role?: string;
  start?: string;
}

/** 所获荣誉：名称 + 时间，列表很轻，不再做成卡片 */
interface HonorItem {
  date?: string;
  id?: number | string;
  name?: string;
}

const props = defineProps<{
  content: ResumeDocumentContent & Record<string, any>;
  /** 正在跑的 AI 任务名，用于提示哪些内容会被覆盖 */
  busyLabel?: string;
  /** 字段路径 → 挂在该字段下的 AI 建议（见 resume-suggestions.ts 的路径约定） */
  suggestions?: SuggestionMap;
}>();

const emit = defineEmits<{
  /** 用户在建议卡上点了「使用」或「忽略」 */
  decide: [item: FieldSuggestion, decision: 'ACCEPT' | 'REJECT'];
}>();

/** 取某个字段上的建议；用空数组兜底，模板里不用到处写 `|| []` */
function sug(path: string): FieldSuggestion[] {
  return props.suggestions?.[path] || [];
}

/**
 * 转发建议卡的点击。
 *
 * 用具名函数而不是 `@decide="emit('decide', $event[0], $event[1])"`：
 * 多参数 emit 配内联写法时 `$event` 是数组还是首个参数，各处说法不一，
 * 与其赌，不如让参数原样透传。
 */
function onDecide(item: FieldSuggestion, decision: 'ACCEPT' | 'REJECT') {
  emit('decide', item, decision);
}

/**
 * 性别存成数字（1 男 / 2 女），与门户「求职资料」的 profile-setup 保持同一套取值。
 *
 * 注意 gender 在服务端的 FORBIDDEN_ATTRIBUTE_FIELDS 里，**刻意**不在模型白名单中：
 * 它能进简历、能展示，但永远不会进提示词，也不会参与评分或排序。
 * 往白名单里加它会同时破坏这条合规约定，别加。
 */
const GENDERS = [
  { label: '男', value: 1 },
  { label: '女', value: 2 },
];

const gender = computed<number | undefined>({
  get: () => (props.content.gender == null ? undefined : Number(props.content.gender)),
  set: (value) => {
    props.content.gender = value;
  },
});

/** 折叠状态：默认全部展开，和参考站进入时一致 */
const collapsed = reactive<Record<string, boolean>>({});

function toggle(section: string) {
  collapsed[section] = !collapsed[section];
}

const root = ref<HTMLElement>();

/**
 * 从中栏点小节标题跳过来时调用：展开该分区并滚动到对应小节。
 *
 * <p>先展开再滚动 —— 折叠状态下元素高度是 0，直接 scrollIntoView 会落到收起后的位置，
 * 所以必须 await nextTick 等 DOM 展开。</p>
 *
 * <p>落地后高亮一下再淡出：平滑滚动结束的位置不一定在视口正中，不做提示的话
 * 用户未必知道自己被带到了哪一节（尤其从中栏一处跳到左栏另一处时）。
 * 用直接加类而不是 Vue 绑定的原因：锚点是八处分散的 data-anchor，
 * 为这一处临时态给每处都加一遍 :class 不划算，而这些元素本身没有 :class，
 * Vue 也不会去动它们的 classList。</p>
 */
async function focusSection(key: string) {
  const section = RESUME_SECTION_MAP.get(key);
  if (!section) return;
  collapsed[section.editorSection] = false;
  await nextTick();
  const anchor = root.value?.querySelector<HTMLElement>(
    `[data-anchor="${key}"]`,
  );
  if (!anchor) return;
  anchor.scrollIntoView({ behavior: 'smooth', block: 'start' });
  anchor.classList.add('is-flash');
  window.setTimeout(() => anchor.classList.remove('is-flash'), 1600);
}

defineExpose({ focusSection });

const experiences = computed<ExperienceItem[]>({
  get: () => props.content.experiences || [],
  set: (value) => {
    props.content.experiences = value;
  },
});

const projects = computed<ProjectItem[]>({
  get: () => props.content.projects || [],
  set: (value) => {
    props.content.projects = value;
  },
});

const educations = computed<EducationItem[]>({
  get: () => props.content.educationExperiences || [],
  set: (value) => {
    props.content.educationExperiences = value;
  },
});

const internships = computed<InternshipItem[]>({
  get: () => props.content.internships || [],
  set: (value) => {
    props.content.internships = value;
  },
});

const clubs = computed<ClubItem[]>({
  get: () => props.content.clubs || [],
  set: (value) => {
    props.content.clubs = value;
  },
});

const honors = computed<HonorItem[]>({
  get: () => props.content.honors || [],
  set: (value) => {
    props.content.honors = value;
  },
});

/*
 * 技能只有「单行且短」时才拆成标签给个删除按钮；分条长文（每行一条、行内满是顿号）
 * 不拆——拆出来是几十个碎片，而且删除时会用顿号 join 回去，把换行结构彻底毁掉。
 * 那种形态交给下面的文本框直接编辑原文。
 */
const skillText = computed(() => String(props.content.skills || '').trim());

const skills = computed(() => {
  const lines = skillText.value.split(/\n+/).filter((item) => item.trim());
  if (lines.length !== 1 || skillText.value.length > 40) return [];
  return skillText.value
    .split(/[、,，]/)
    .map((item) => item.trim())
    .filter(Boolean);
});

/** 新增条目一律用「新条目」占位，让预览里立刻能看到一块空白区，而不是无声无息 */
function addExperience() {
  experiences.value = [
    ...experiences.value,
    { achievements: '', company: '', description: '', position: '' },
  ];
}

function addProject() {
  projects.value = [...projects.value, { description: '', name: '', role: '' }];
}

function addEducation() {
  educations.value = [
    ...educations.value,
    { degree: '', experience: '', major: '', school: '' },
  ];
}

/*
 * 删除条目按分区各写一个，没有抽成通用的 removeAt(list, index)。
 *
 * 抽过一版，模板里写 `@click="removeAt(educations, index)"`，结果整条删不动、
 * 控制台报 `Cannot read properties of undefined (reading 'filter')`：
 * **模板里的 ref 会自动解包**，传进去的是数组本身而不是 ref，函数里的 `list.value`
 * 于是是 undefined。新增那边没踩到，是因为 addEducation 在 script 里用
 * `educations.value`，不经过模板解包。
 *
 * 要在模板里传 ref 得写成 `removeAt(educations, ...)` 之外的形式（比如再包一层函数），
 * 与其绕，不如每个分区一个具名函数——调用点也更好读。
 */
function removeEducation(index: number) {
  educations.value = educations.value.filter((_, i) => i !== index);
}

function removeExperience(index: number) {
  experiences.value = experiences.value.filter((_, i) => i !== index);
}

function removeProject(index: number) {
  projects.value = projects.value.filter((_, i) => i !== index);
}

function addInternship() {
  internships.value = [
    ...internships.value,
    { achievements: '', company: '', description: '', position: '' },
  ];
}

function removeInternship(index: number) {
  internships.value = internships.value.filter((_, i) => i !== index);
}

function addClub() {
  clubs.value = [...clubs.value, { description: '', name: '', role: '' }];
}

function removeClub(index: number) {
  clubs.value = clubs.value.filter((_, i) => i !== index);
}

function addHonor() {
  honors.value = [...honors.value, { date: '', name: '' }];
}

function removeHonor(index: number) {
  honors.value = honors.value.filter((_, i) => i !== index);
}

function removeSkill(skill: string) {
  const next = skills.value.filter((item) => item !== skill);
  props.content.skills = next.join('、');
}

/*
 * ── 头像 ──
 *
 * 存进 content.avatar。库里已有的在线求职资料就是这个字段名
 * （形如 /admin-api/hrm/portal/recruit/file/avatar-0-xxxx），保持一致才能互认。
 * 走 resume-avatar 分类：avatar 那一类是账号头像，两者不是一回事。
 */
const avatarInput = ref<HTMLInputElement>();
const avatarUploading = ref(false);

const avatarUrl = computed(() => String(props.content.avatar || '').trim());

function chooseAvatar() {
  if (avatarUploading.value) return;
  avatarInput.value?.click();
}

async function onAvatarChange(event: Event) {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  input.value = '';
  if (!file) return;

  if (!file.type.startsWith('image/')) {
    message.warning('头像只支持图片');
    return;
  }
  if (file.size > 5 * 1024 * 1024) {
    message.warning('头像不能超过 5 MB');
    return;
  }

  avatarUploading.value = true;
  try {
    // 上传成功才写回草稿：先写再传的话，传失败会留下一个指向不存在文件的地址
    const url = await uploadHrmRecruitFile(file, 'resume-avatar');
    props.content.avatar = url;
  } catch (error: any) {
    message.error(error?.message || '头像上传失败，请稍后重试');
  } finally {
    avatarUploading.value = false;
  }
}
</script>

<template>
  <div ref="root" class="editor-panel">
    <p v-if="busyLabel" class="editor-busy">
      <IconifyIcon icon="lucide:loader-circle" class="spin" />
      {{ busyLabel }}
    </p>

    <!-- 基础信息 -->
    <section class="sec">
      <button class="sec-head" type="button" @click="toggle('basic')">
        <IconifyIcon :icon="collapsed.basic ? 'lucide:chevron-right' : 'lucide:chevron-down'" />
        <span>基础信息</span>
      </button>
      <div v-if="!collapsed.basic" class="sec-body">
        <div class="avatar-field">
          <button
            class="avatar-box"
            type="button"
            :aria-label="avatarUrl ? '更换头像' : '上传头像'"
            @click="chooseAvatar"
          >
            <img v-if="avatarUrl" :src="avatarUrl" :alt="content.name || '头像'" />
            <IconifyIcon v-else icon="lucide:user" />
            <span v-if="avatarUploading" class="avatar-loading">
              <IconifyIcon icon="lucide:loader-circle" class="spin" />
            </span>
          </button>
          <button class="avatar-action" type="button" @click="chooseAvatar">
            {{ avatarUploading ? '上传中…' : '上传头像' }}
            <IconifyIcon icon="lucide:pencil-line" />
          </button>
          <input
            ref="avatarInput"
            type="file"
            accept="image/png,image/jpeg,image/webp"
            style="display: none"
            @change="onAvatarChange"
          />
        </div>

        <label class="field">
          <span>姓名<em>*</em></span>
          <input v-model="content.name" placeholder="请填写姓名" />
        </label>
        <div class="field">
          <span>性别</span>
          <div class="gender-row">
            <button
              v-for="item in GENDERS"
              :key="item.value"
              type="button"
              :class="{ 'is-on': gender === item.value }"
              @click="gender = item.value"
            >
              {{ item.label }}
            </button>
          </div>
        </div>
        <label class="field">
          <span>出生年月</span>
          <input v-model="content.birthMonth" placeholder="如 2002-08" />
        </label>
        <label class="field">
          <span>电话</span>
          <input v-model="content.mobile" placeholder="请填写电话" />
        </label>
        <label class="field">
          <span>邮箱</span>
          <input v-model="content.email" placeholder="请填写邮箱" />
        </label>
        <label class="field">
          <span>籍贯</span>
          <input v-model="content.city" placeholder="如 陕西省 / 延安市" />
        </label>
        <label class="field">
          <span>期望职位</span>
          <input v-model="content.expectedPostName" placeholder="如 预算员/造价员" />
        </label>
        <!-- 个人优势归属「基础信息」分区，但中栏是按小节定位过来的，所以单独挂个锚点 -->
        <div data-anchor="strengths">
          <ResumeField
            v-model="content.strengths"
            label="个人优势"
            :rows="3"
            placeholder="一句话说清你的核心竞争力"
            :suggestions="sug(scalarPath('strengths'))"
            @decide="onDecide"
          />
        </div>
      </div>
    </section>

    <!-- 教育经历 -->
    <section class="sec" data-anchor="educationExperiences">
      <button class="sec-head" type="button" @click="toggle('edu')">
        <IconifyIcon :icon="collapsed.edu ? 'lucide:chevron-right' : 'lucide:chevron-down'" />
        <span>教育经历</span>
      </button>
      <div v-if="!collapsed.edu" class="sec-body">
        <article v-for="(item, index) in educations" :key="`edu-${index}`" class="card">
          <header class="card-head">
            <strong>{{ item.school || '新增教育经历' }}</strong>
            <button type="button" class="icon-btn" aria-label="删除" @click="removeEducation(index)">
              <IconifyIcon icon="lucide:trash-2" />
            </button>
          </header>
          <label class="field"><span>学校</span><input v-model="item.school" /></label>
          <label class="field"><span>学历</span><input v-model="item.degree" placeholder="本科 / 硕士" /></label>
          <label class="field"><span>学制</span><input v-model="item.schoolType" placeholder="全日制 / 非全日制" /></label>
          <label class="field"><span>专业</span><input v-model="item.major" /></label>
          <div class="field-row">
            <label class="field"><span>开始</span><input v-model="item.start" placeholder="2020-09" /></label>
            <label class="field"><span>结束</span><input v-model="item.end" placeholder="2024-07" /></label>
          </div>
          <ResumeField
            v-model="item.experience"
            label="在校经历"
            :rows="3"
            placeholder="可写专业、课程、竞赛、奖学金等"
            :suggestions="sug(itemPath('educationExperiences', index, 'experience'))"
            @decide="onDecide"
          />
        </article>
        <button class="add-btn" type="button" @click="addEducation">
          <IconifyIcon icon="lucide:plus" /> 新增教育经历
        </button>
      </div>
    </section>

    <!-- 实习经历 -->
    <section class="sec" data-anchor="internships">
      <button class="sec-head" type="button" @click="toggle('intern')">
        <IconifyIcon :icon="collapsed.intern ? 'lucide:chevron-right' : 'lucide:chevron-down'" />
        <span>实习经历</span>
      </button>
      <div v-if="!collapsed.intern" class="sec-body">
        <article
          v-for="(item, index) in internships"
          :key="`intern-${index}`"
          class="card"
        >
          <header class="card-head">
            <strong>{{ item.company || '新增实习经历' }}</strong>
            <button
              type="button"
              class="icon-btn"
              aria-label="删除"
              @click="removeInternship(index)"
            >
              <IconifyIcon icon="lucide:trash-2" />
            </button>
          </header>
          <label class="field"><span>公司</span><input v-model="item.company" /></label>
          <label class="field"><span>实习岗位</span><input v-model="item.position" /></label>
          <div class="field-row">
            <label class="field"><span>开始</span><input v-model="item.start" placeholder="2023-07" /></label>
            <label class="field"><span>结束</span><input v-model="item.end" placeholder="2023-09" /></label>
          </div>
          <ResumeField
            v-model="item.description"
            label="工作内容"
            :rows="3"
            placeholder="动词 + 任务 + 结果"
            :suggestions="sug(itemPath('internships', index, 'description'))"
            @decide="onDecide"
          />
          <ResumeField
            v-model="item.achievements"
            label="工作业绩"
            :rows="3"
            :suggestions="sug(itemPath('internships', index, 'achievements'))"
            @decide="onDecide"
          />
        </article>
        <button class="add-btn" type="button" @click="addInternship">
          <IconifyIcon icon="lucide:plus" /> 新增实习经历
        </button>
      </div>
    </section>

    <!-- 学生会 / 社团经历 -->
    <section class="sec" data-anchor="clubs">
      <button class="sec-head" type="button" @click="toggle('club')">
        <IconifyIcon :icon="collapsed.club ? 'lucide:chevron-right' : 'lucide:chevron-down'" />
        <span>学生会/社团经历</span>
      </button>
      <div v-if="!collapsed.club" class="sec-body">
        <article v-for="(item, index) in clubs" :key="`club-${index}`" class="card">
          <header class="card-head">
            <strong>{{ item.name || '新增社团经历' }}</strong>
            <button type="button" class="icon-btn" aria-label="删除" @click="removeClub(index)">
              <IconifyIcon icon="lucide:trash-2" />
            </button>
          </header>
          <label class="field"><span>社团名称</span><input v-model="item.name" /></label>
          <label class="field"><span>担任角色</span><input v-model="item.role" /></label>
          <div class="field-row">
            <label class="field"><span>开始</span><input v-model="item.start" /></label>
            <label class="field"><span>结束</span><input v-model="item.end" /></label>
          </div>
          <ResumeField
            v-model="item.description"
            label="主要活动"
            :rows="3"
            placeholder="策划 / 组织 / 执行了什么，达成了什么"
            :suggestions="sug(itemPath('clubs', index, 'description'))"
            @decide="onDecide"
          />
        </article>
        <button class="add-btn" type="button" @click="addClub">
          <IconifyIcon icon="lucide:plus" /> 新增社团经历
        </button>
      </div>
    </section>

    <!-- 所获荣誉 -->
    <section class="sec" data-anchor="honors">
      <button class="sec-head" type="button" @click="toggle('honor')">
        <IconifyIcon :icon="collapsed.honor ? 'lucide:chevron-right' : 'lucide:chevron-down'" />
        <span>所获荣誉</span>
      </button>
      <div v-if="!collapsed.honor" class="sec-body">
        <div v-for="(item, index) in honors" :key="`honor-${index}`" class="honor-row">
          <input v-model="item.name" placeholder="奖项名称" />
          <input v-model="item.date" class="honor-date" placeholder="2024-06" />
          <button
            type="button"
            class="icon-btn"
            aria-label="删除"
            @click="removeHonor(index)"
          >
            <IconifyIcon icon="lucide:trash-2" />
          </button>
        </div>
        <button class="add-btn" type="button" @click="addHonor">
          <IconifyIcon icon="lucide:plus" /> 新增荣誉
        </button>
      </div>
    </section>

    <!-- 工作经历 -->
    <section class="sec" data-anchor="experiences">
      <button class="sec-head" type="button" @click="toggle('work')">
        <IconifyIcon :icon="collapsed.work ? 'lucide:chevron-right' : 'lucide:chevron-down'" />
        <span>工作经历</span>
      </button>
      <div v-if="!collapsed.work" class="sec-body">
        <article v-for="(item, index) in experiences" :key="`exp-${index}`" class="card">
          <header class="card-head">
            <strong>{{ item.company || '新增工作经历' }}</strong>
            <button type="button" class="icon-btn" aria-label="删除" @click="removeExperience(index)">
              <IconifyIcon icon="lucide:trash-2" />
            </button>
          </header>
          <label class="field"><span>公司</span><input v-model="item.company" /></label>
          <label class="field"><span>部门</span><input v-model="item.department" /></label>
          <label class="field"><span>职位</span><input v-model="item.position" /></label>
          <div class="field-row">
            <label class="field"><span>开始</span><input v-model="item.start" placeholder="2024-10" /></label>
            <label class="field"><span>结束</span><input v-model="item.end" placeholder="至今" /></label>
          </div>
          <ResumeField
            v-model="item.description"
            label="工作内容"
            :rows="3"
            :suggestions="sug(itemPath('experiences', index, 'description'))"
            @decide="onDecide"
          />
          <ResumeField
            v-model="item.achievements"
            label="工作业绩"
            :rows="3"
            placeholder="尽量带上可核实的数字"
            :suggestions="sug(itemPath('experiences', index, 'achievements'))"
            @decide="onDecide"
          />
        </article>
        <button class="add-btn" type="button" @click="addExperience">
          <IconifyIcon icon="lucide:plus" /> 新增工作经历
        </button>
      </div>
    </section>

    <!-- 项目经历 -->
    <section class="sec" data-anchor="projects">
      <button class="sec-head" type="button" @click="toggle('proj')">
        <IconifyIcon :icon="collapsed.proj ? 'lucide:chevron-right' : 'lucide:chevron-down'" />
        <span>项目经历</span>
      </button>
      <div v-if="!collapsed.proj" class="sec-body">
        <article v-for="(item, index) in projects" :key="`prj-${index}`" class="card">
          <header class="card-head">
            <strong>{{ item.name || '新增项目经历' }}</strong>
            <button type="button" class="icon-btn" aria-label="删除" @click="removeProject(index)">
              <IconifyIcon icon="lucide:trash-2" />
            </button>
          </header>
          <label class="field"><span>项目名称</span><input v-model="item.name" /></label>
          <label class="field"><span>担任角色</span><input v-model="item.role" /></label>
          <div class="field-row">
            <label class="field"><span>开始</span><input v-model="item.start" placeholder="2026-03" /></label>
            <label class="field"><span>结束</span><input v-model="item.end" placeholder="2026-05" /></label>
          </div>
          <ResumeField
            v-model="item.description"
            label="项目描述"
            :rows="3"
            :suggestions="sug(itemPath('projects', index, 'description'))"
            @decide="onDecide"
          />
          <ResumeField
            v-model="item.achievements"
            label="项目成果"
            :rows="3"
            :suggestions="sug(itemPath('projects', index, 'achievements'))"
            @decide="onDecide"
          />
        </article>
        <button class="add-btn" type="button" @click="addProject">
          <IconifyIcon icon="lucide:plus" /> 新增项目经历
        </button>
      </div>
    </section>

    <!-- 技能 -->
    <section class="sec" data-anchor="skills">
      <button class="sec-head" type="button" @click="toggle('skill')">
        <IconifyIcon :icon="collapsed.skill ? 'lucide:chevron-right' : 'lucide:chevron-down'" />
        <span>掌握技能</span>
      </button>
      <div v-if="!collapsed.skill" class="sec-body">
        <div v-if="skills.length" class="skill-tags">
          <span v-for="skill in skills" :key="skill" class="skill-tag">
            {{ skill }}
            <button type="button" :aria-label="`移除 ${skill}`" @click="removeSkill(skill)">
              <IconifyIcon icon="lucide:x" />
            </button>
          </span>
        </div>
        <ResumeField
          v-model="content.skills"
          label="技能（顿号或换行分隔）"
          :rows="skillText.length > 60 ? 8 : 2"
          placeholder="办公软件、工程制图、项目管理"
          :suggestions="sug(scalarPath('skills'))"
          @decide="onDecide"
        />
      </div>
    </section>
  </div>
</template>

<style scoped>
/* 左栏的页签条在上面，滚动落在这里；生成模板页时它不渲染，所以互不干扰 */
/*
 * 从中栏跳过来时，落地的小节闪一下再淡出，见 focusSection。
 * 用 animation 而不是 transition：类加上去的瞬间就要看到变化，
 * transition 需要先有初始值，会闪不出来。
 */
@keyframes sec-flash {
  0%,
  60% {
    background: #fdf4e8;
    box-shadow: 0 0 0 6px #fdf4e8;
  }

  100% {
    background: transparent;
    box-shadow: 0 0 0 6px transparent;
  }
}

[data-anchor].is-flash {
  border-radius: 8px;
  animation: sec-flash 1.6s ease-out;
}

.editor-panel {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 10px;
  min-height: 0;
  padding: 16px;
  overflow-y: auto;
}

.editor-busy {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0 0 4px;
  padding: 8px 12px;
  border-radius: 8px;
  background: #f2f6ee;
  color: #4e6840;
  font-size: 13px;
}

.spin {
  animation: editor-spin 1s linear infinite;
}

@keyframes editor-spin {
  to {
    transform: rotate(360deg);
  }
}

.sec {
  border: 1px solid #ececec;
  border-radius: 10px;
  background: #fff;
}

.sec-head {
  display: flex;
  align-items: center;
  gap: 6px;
  width: 100%;
  padding: 11px 12px;
  border: 0;
  background: transparent;
  color: #292929;
  font-size: 14px;
  font-weight: 600;
  text-align: left;
  cursor: pointer;
}

.sec-body {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 0 12px 14px;
}

/* ── 头像 ── */
.avatar-field {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 16px 12px 14px;
  border: 1px solid #ececec;
  border-radius: 12px;
  background: #fff;
}

.avatar-box {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 148px;
  height: 148px;
  padding: 0;
  border: 0;
  border-radius: 12px;
  background: #f4f4f4;
  color: #d2d2d2;
  font-size: 56px;
  cursor: pointer;
  overflow: hidden;
  transition: background 0.2s ease-in-out;
}

.avatar-box:hover {
  background: #ededed;
}

.avatar-box img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.avatar-loading {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgb(255 255 255 / 65%);
  color: #6a785e;
  font-size: 22px;
}

.avatar-action {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 0;
  border: 0;
  background: transparent;
  color: #5e5e5e;
  font-size: 14px;
  cursor: pointer;
}

.avatar-action:hover {
  color: #292929;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 5px;
  min-width: 0;
}

.field > span {
  color: #7d7d7d;
  font-size: 12px;
  line-height: 1.4;
}

.field > span em {
  color: #d8504e;
  font-style: normal;
}

.field input,
.field textarea {
  width: 100%;
  padding: 7px 10px;
  border: 1px solid #e6e6e6;
  border-radius: 6px;
  background: #fff;
  color: #292929;
  font-family: inherit;
  font-size: 13px;
  line-height: 1.5;
  box-sizing: border-box;
}

.field input:focus,
.field textarea:focus {
  border-color: #9ab583;
  outline: none;
}

.field textarea {
  resize: vertical;
}

.field-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}

/* 性别：取值与门户求职资料一致（1 男 / 2 女），样式沿用分段控件 */
.gender-row {
  display: flex;
  gap: 8px;
}

.gender-row button {
  flex: 1;
  padding: 7px 0;
  border: 1px solid #e6e6e6;
  border-radius: 6px;
  background: #fff;
  color: #5e5e5e;
  font-size: 13px;
  cursor: pointer;
  transition: 0.2s ease-in-out;
}

.gender-row button:hover {
  border-color: #9ab583;
}

.gender-row button.is-on {
  border-color: #6e8d57;
  background: #f2f6ee;
  color: #4e6840;
  font-weight: 500;
}

.card {
  display: flex;
  flex-direction: column;
  gap: 9px;
  padding: 11px;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  background: #fbfbfb;
}

.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.card-head strong {
  overflow: hidden;
  color: #4a4a4a;
  font-size: 13px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.icon-btn {
  padding: 2px;
  border: 0;
  background: transparent;
  color: #b0b0b0;
  font-size: 14px;
  cursor: pointer;
}

.icon-btn:hover {
  color: #d8504e;
}

.add-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  width: 100%;
  padding: 8px;
  border: 1px dashed #dcdcdc;
  border-radius: 8px;
  background: transparent;
  color: #6a785e;
  font-size: 13px;
  cursor: pointer;
}

.add-btn:hover {
  border-color: #9ab583;
  background: #f7faf4;
}

/* 荣誉一行一条，不做成卡片：字段只有两个，套卡片反而显得重 */
.honor-row {
  display: flex;
  align-items: center;
  gap: 6px;
}

.honor-row input {
  flex: 1;
  min-width: 0;
  padding: 7px 10px;
  border: 1px solid #e6e6e6;
  border-radius: 6px;
  background: #fff;
  color: #292929;
  font-family: inherit;
  font-size: 13px;
}

.honor-row input:focus {
  border-color: #9ab583;
  outline: none;
}

.honor-row .honor-date {
  flex: 0 0 96px;
}

.skill-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.skill-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px 8px;
  border-radius: 6px;
  background: #f2f6ee;
  color: #4e6840;
  font-size: 12px;
}

.skill-tag button {
  display: flex;
  padding: 0;
  border: 0;
  background: transparent;
  color: #8aa377;
  cursor: pointer;
}

.skill-tag button:hover {
  color: #d8504e;
}
</style>
