<script lang="ts" setup>
import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import type { TrainingCatalogCategory } from './course-catalog';

import { TRAINING_CATALOG } from './course-catalog';
import { TRAINING_MODULES } from './training-modules';
import { TRAINING_LEVELS, useTrainingDirection } from './use-training-direction';

defineOptions({ name: 'PortalTraining' });

const route = useRoute();
const router = useRouter();

/** F1~F10 培训学院模块导航，对应 /portal/training/* 子路由 */
const modules = TRAINING_MODULES;

const catalog = TRAINING_CATALOG;
const { state, levelName, load, save } = useTrainingDirection();

const dialog = ref(false);
const message = ref('');
const draft = ref<{ level: number; selected: string[] }>({
  level: 0,
  selected: [],
});

const directionDescriptions: Record<string, string> = {
  A: 'HSE、安全规范、基础认知',
  B: '表面处理、涂料、施工工艺',
  C: '绝热结构、保温施工',
  D: '内衬技术、修复工艺',
  E: '无损检测、理化试验',
  F: '材料认知、采购仓储',
  G: '工艺方案、质量验收',
  H: '平台工具、数字化应用',
};
const levelDescriptions = [
  '开始系统学习基础知识与岗位规范。',
  '掌握基础作业流程与操作要求。',
  '能够独立完成常规岗位作业。',
  '能够处理复杂任务并把控质量。',
  '能够指导现场实践与技能提升。',
  '具备专业决策与技术指导能力。',
];

/** 鼠标悬浮「课程中心」时弹出的课程目录面板 */
const hoverOpen = ref(false);
const hoverCategory = ref<TrainingCatalogCategory | null>(TRAINING_CATALOG[0] ?? null);

const currentTitle = computed(
  () => modules.find((item) => isActive(item.path))?.label ?? '云培训学院',
);

const selectedNames = computed(() =>
  state.value.selected
    .map((code) => catalog.find((item) => item.code === code)?.name ?? code)
    .join('、'),
);

function isActive(path: string) {
  return route.path === path || route.path.startsWith(`${path}/`);
}

function go(path: string) {
  router.push(path);
}

function openDialog() {
  draft.value = {
    level: state.value.level,
    selected: [...state.value.selected],
  };
  message.value = '';
  dialog.value = true;
}

function toggle(code: string) {
  draft.value.selected = draft.value.selected.includes(code)
    ? draft.value.selected.filter((x) => x !== code)
    : [...draft.value.selected, code];
}

function saveDirection() {
  if (!draft.value.selected.length) {
    message.value = '请至少选择一个课程大类';
    return;
  }
  save({ level: draft.value.level, selected: draft.value.selected });
  dialog.value = false;
  message.value = '学习方向已保存';
}

function goCourseCategory(code: string) {
  router.push({ path: '/portal/training/courses', query: { category: code } });
}

function openCategoryPopup() {
  if (!hoverCategory.value) {
    hoverCategory.value = TRAINING_CATALOG[0] ?? null;
  }
  hoverOpen.value = true;
}

/** 从悬浮面板选择二级中类：跳转课程中心并带上筛选条件 */
function goCourseGroup(categoryCode: string, groupName: string) {
  hoverOpen.value = false;
  router.push({
    path: '/portal/training/courses',
    query: { category: categoryCode, group: groupName },
  });
}

onMounted(() => {
  load();
  if (!state.value.selected.length) dialog.value = true;
});
</script>

<template>
  <div class="page">
    <aside>
      <b>云培训学院</b>
      <button
        v-for="(item, index) in modules"
        :key="item.path"
        type="button"
        :class="{ active: isActive(item.path) }"
        @mouseenter="index === 1 && openCategoryPopup()"
        @click="go(item.path)"
      >
        <small>F{{ index + 1 }}</small>{{ item.label }}
      </button>
    </aside>

    <!-- 悬浮「课程中心」时弹出的课程目录面板：一级大类 → 二级中类 -->
    <div
      v-if="hoverOpen"
      class="category-popup"
      @mouseenter="hoverOpen = true"
      @mouseleave="hoverOpen = false"
    >
      <div class="popup-primary">
        <button
          v-for="item in catalog"
          :key="item.code"
          type="button"
          :class="{ chosen: hoverCategory?.code === item.code }"
          @mouseenter="hoverCategory = item"
        >
          {{ item.code }} {{ item.name }} <span>›</span>
        </button>
      </div>
      <div class="popup-secondary">
        <h3>{{ hoverCategory?.name }}</h3>
        <button
          v-for="item in hoverCategory?.groups ?? []"
          :key="item.code"
          type="button"
          @click="hoverCategory && goCourseGroup(hoverCategory.code, item.name)"
        >
          {{ item.code }} {{ item.name }}
        </button>
      </div>
    </div>

    <main>
      <header>
        <div class="title">
          <small>LEARNING CENTER</small>
          <h1>{{ currentTitle }}</h1>
        </div>
        <button type="button" class="ghost" @click="openDialog">
          调整学习方向
        </button>
      </header>

      <nav class="topic-strip" aria-label="课程导览">
        <span>热门方向</span>
        <button
          v-for="item in catalog.slice(0, 5)"
          :key="item.code"
          type="button"
          @click="goCourseCategory(item.code)"
        >
          {{ item.code }} {{ item.name }}
        </button>
      </nav>

      <p class="direction-bar">
        当前学习方向：<strong>{{ selectedNames || '未选择' }}</strong>
        · 成长等级：<strong>{{ levelName }}</strong>
        <span class="link" @click="openDialog">修改</span>
      </p>

      <p v-if="message && !dialog" class="message">{{ message }}</p>

      <router-view />
    </main>

    <div v-if="dialog" class="mask">
      <section class="dialog" role="dialog" aria-modal="true" aria-labelledby="direction-title">
        <header class="dialog-heading">
          <h2 id="direction-title">选择学习方向</h2>
          <p>用于匹配课程、实训和考试内容，后续可在个人设置中修改。</p>
        </header>

        <div class="dialog-section-heading"><h3>学习方向 <span>（可多选）</span></h3></div>
        <div class="direction-options">
          <button
            v-for="item in catalog"
            :key="item.code"
            type="button"
            class="direction-card"
            :class="{ chosen: draft.selected.includes(item.code) }"
            :aria-pressed="draft.selected.includes(item.code)"
            @click="toggle(item.code)"
          >
            <span class="direction-check" aria-hidden="true">{{ draft.selected.includes(item.code) ? '✓' : '' }}</span>
            <span class="direction-copy"><b>{{ item.name }}</b><small>{{ directionDescriptions[item.code] }}</small></span>
          </button>
        </div>

        <div class="dialog-section-heading level-heading"><h3>当前成长阶段</h3></div>
        <div class="level-options" role="group" aria-label="当前成长等级">
          <button v-for="(name, index) in TRAINING_LEVELS" :key="name" type="button" :class="{ chosen: draft.level === index }" :aria-pressed="draft.level === index" @click="draft.level = index">
            {{ name }}
          </button>
        </div>
        <p class="level-description">当前阶段：<strong>{{ TRAINING_LEVELS[draft.level] }}</strong><span>{{ levelDescriptions[draft.level] }}</span></p>

        <p class="path-preview">推荐学习顺序：<span>岗位基础 → 实操训练 → 在线考试 → 技能认证</span></p>
        <p v-if="message" class="dialog-message" role="alert">{{ message }}</p>
        <footer class="dialog-footer">
          <button type="button" class="later" @click="dialog = false">稍后设置</button>
          <button class="primary" type="button" @click="saveDirection">确认并进入学习</button>
        </footer>
      </section>
    </div>
  </div>
</template>

<style scoped>
.page {
  position: relative;
  display: grid;
  grid-template-columns: 210px 1fr;
  min-height: calc(100vh - 128px);
  overflow: hidden;
  color: #172033;
  background:
    radial-gradient(circle at 78% 8%, rgb(53 112 255 / 12%), transparent 29rem),
    radial-gradient(circle at 42% 92%, rgb(102 212 185 / 10%), transparent 28rem),
    #f7f8fa;
  border: 1px solid #e6ebf3;
  border-radius: 12px;
}

aside {
  padding: 28px 14px;
  color: #ced5e1;
  background: linear-gradient(180deg, #202633 0%, #18202f 100%);
}

aside b {
  display: block;
  margin: 0 12px 20px;
  color: #fff;
  font-size: 18px;
}

aside button {
  display: flex;
  gap: 10px;
  align-items: center;
  width: 100%;
  padding: 11px 12px;
  margin-bottom: 2px;
  color: inherit;
  text-align: left;
  cursor: pointer;
  background: transparent;
  border: 0;
  border-left: 2px solid transparent;
  border-radius: 6px;
}

aside button.active,
aside button:hover {
  color: #fff;
  background: #2b3240;
  border-left-color: #2f6bff;
}

aside small {
  width: 24px;
  color: #91a2c4;
}

.category-popup {
  position: absolute;
  z-index: 90;
  top: 176px;
  left: 214px;
  display: grid;
  width: 660px;
  max-height: 380px;
  grid-template-columns: 195px minmax(0, 1fr);
  overflow: auto;
  background: #fff;
  border: 1px solid #cfdced;
  border-radius: 8px;
  box-shadow: 0 18px 42px rgb(20 42 82 / 28%);
}

.popup-primary {
  padding: 10px;
  background: #f3f7fd;
  border-right: 1px solid #dce5f1;
  border-radius: 8px 0 0 8px;
}

.popup-primary button,
.popup-secondary button {
  display: flex;
  width: 100%;
  min-height: 40px;
  justify-content: space-between;
  align-items: center;
  padding: 8px 12px;
  color: #34435b;
  text-align: left;
  cursor: pointer;
  background: transparent;
  border: 0;
  border-radius: 6px;
}

.popup-primary button.chosen,
.popup-primary button:hover {
  font-weight: 600;
  color: #2464ed;
  background: #dfeeff;
}

.popup-secondary {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  align-content: start;
  gap: 4px;
  padding: 16px 18px;
}

.popup-secondary h3 {
  grid-column: 1 / -1;
  margin: 0 0 8px;
  padding-bottom: 10px;
  font-size: 16px;
  border-bottom: 1px solid #e7edf5;
}

.popup-secondary button:hover {
  color: #2464ed;
  background: #f2f7ff;
}

main {
  min-width: 0;
  padding: 28px 36px 56px;
}

header {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  padding-bottom: 18px;
  border-bottom: 1px solid #dfe3eb;
}

header small {
  color: #2f6bff;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
}

h1,
h2 {
  margin: 0;
}

h1 {
  font-size: 26px;
}

h2 {
  font-size: 18px;
}

.title {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

button.ghost {
  padding: 8px 14px;
  color: #245fec;
  cursor: pointer;
  background: #fff;
  border: 1px solid #cdd6e6;
  border-radius: 6px;
}

button.ghost:hover {
  background: #eef4ff;
}

.topic-strip {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 18px;
  align-items: center;
  padding: 16px 0 12px;
  color: #66738a;
  border-bottom: 1px dashed rgb(173 193 227 / 70%);
}

.topic-strip span {
  color: #172033;
  font-weight: 700;
}

.topic-strip button {
  padding: 0;
  color: #56657d;
  cursor: pointer;
  background: transparent;
  border: 0;
}

.topic-strip button:hover {
  color: #2f6bff;
}

.direction-bar {
  padding: 10px 0 0;
  margin: 0;
  color: #66738a;
  font-size: 13px;
}

.direction-bar strong {
  color: #172033;
}

.direction-bar .link {
  margin-left: 8px;
  color: #2f6bff;
  cursor: pointer;
}

.message,
.blue {
  color: #245fec;
}

.message {
  display: inline-block;
  padding: 6px 10px;
  margin: 14px 0 0;
  background: rgb(234 242 255 / 74%);
  border-radius: 4px;
}

.mask {
  position: fixed;
  z-index: 1000;
  inset: 0;
  display: grid;
  place-items: center;
  padding: 20px;
  background: rgb(15 23 42 / 45%);
}

.dialog {
  width: min(700px, 100%);
  max-height: calc(100vh - 40px);
  padding: 26px 30px 24px;
  overflow-y: auto;
  color: #27313b;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 15px 45px rgb(14 28 40 / 17%);
  font-family: 'PingFang SC', 'Microsoft YaHei', Arial, sans-serif;
}

.dialog-heading { display: block; padding-bottom: 17px; border-bottom: 1px solid #e9ebed; }
.dialog-heading h2 { margin: 0 0 6px; color: #27313a; font-size: 21px; font-weight: 600; }
.dialog-heading p { margin: 0; color: #7c858d; font-size: 12px; line-height: 1.6; }
.dialog-section-heading { margin: 18px 0 10px; }
.dialog-section-heading h3 { margin: 0; color: #39444e; font-size: 14px; font-weight: 600; }
.dialog-section-heading h3 span { color: #929aa1; font-size: 12px; font-weight: 400; }
.direction-options { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 0 18px; }
.direction-card {
  display: flex;
  min-width: 0;
  min-height: 63px;
  align-items: flex-start;
  gap: 11px;
  padding: 10px 8px;
  text-align: left;
  cursor: pointer;
  background: #fff;
  border: 0;
  border-bottom: 1px solid #edf0f1;
  border-radius: 0;
}
.direction-card:hover { background: #faf8f5; }
.direction-card.chosen { background: #fff8f2; }
.direction-check { display: grid; flex: 0 0 16px; width: 16px; height: 16px; margin-top: 3px; place-items: center; color: #fff; border: 1px solid #bdc4c9; border-radius: 3px; font-size: 12px; line-height: 1; }
.direction-card.chosen .direction-check { background: #dc792d; border-color: #dc792d; }
.direction-copy { display: flex; min-width: 0; flex-direction: column; gap: 4px; }
.direction-copy b { color: #35414b; font-size: 13px; font-weight: 570; }
.direction-copy small { color: #929ba2; font-size: 11px; line-height: 1.35; }
.level-heading { margin-top: 20px; }
.level-options { display: grid; grid-template-columns: repeat(6, minmax(0, 1fr)); border: 1px solid #e5e8ea; border-radius: 4px; overflow: hidden; }
.level-options button { height: 39px; padding: 0 3px; color: #717b83; cursor: pointer; background: #fff; border: 0; border-right: 1px solid #e5e8ea; font-size: 12px; white-space: nowrap; }
.level-options button:last-child { border-right: 0; }
.level-options button:hover { color: #dc792d; background: #fffaf6; }
.level-options button.chosen { color: #d87329; background: #fff4ea; box-shadow: inset 0 -2px #dc792d; }
.level-description { display: flex; gap: 10px; margin: 10px 0 0; color: #858e96; font-size: 11px; }
.level-description strong { color: #5d6871; font-weight: 600; }
.path-preview { margin: 18px 0 0; padding-top: 13px; color: #67727b; border-top: 1px solid #e9ebed; font-size: 12px; }
.path-preview span { color: #8d969d; }
.dialog-message { margin: 10px 0 0; color: #b45d2c; font-size: 12px; }
.dialog-footer { display: flex; justify-content: flex-end; gap: 9px; margin-top: 18px; }
.dialog-footer button { height: 40px; padding: 0 23px; cursor: pointer; border-radius: 4px; font-size: 12px; }
.dialog-footer .later { color: #6d7880; background: #fff; border: 1px solid #dfe3e5; }
.dialog-footer .later:hover { background: #f7f8f8; }
.dialog-footer .primary { color: #fff; background: #df7a2e; border: 1px solid #df7a2e; }
.dialog-footer .primary:hover { background: #ce6920; }
@media (max-width: 600px) {
  .dialog { padding: 20px; }
  .direction-options { grid-template-columns: 1fr; }
  .level-options { grid-template-columns: repeat(3, minmax(0, 1fr)); }
  .level-options button:nth-child(3) { border-right: 0; }
  .level-options button:nth-child(-n+3) { border-bottom: 1px solid #e5e8ea; }
  .level-description { flex-wrap: wrap; gap: 2px 8px; }
}
@media (max-height: 600px) { .dialog { max-height: calc(100vh - 28px); } }

/* 学院首页使用横向内容结构；仅作用于云培训学院父页面。 */
.page {
  display: block;
  min-height: 0;
  overflow: visible;
  background: #fff;
  border: 0;
  border-radius: 0;
}

.page > aside,
.page > .category-popup,
.page > main > header,
.page > main > .topic-strip,
.page > main > .direction-bar,
.page > main > .message {
  display: none;
}

.page > main {
  width: 100%;
  padding: 0;
}

@media (max-width: 900px) {
  .page {
    grid-template-columns: 1fr;
  }

  aside {
    display: flex;
    gap: 6px;
    padding: 12px;
    overflow-x: auto;
  }

  aside b {
    display: none;
  }

  aside button {
    width: auto;
    margin-bottom: 0;
    white-space: nowrap;
  }

  main {
    padding: 20px;
  }
}
</style>
