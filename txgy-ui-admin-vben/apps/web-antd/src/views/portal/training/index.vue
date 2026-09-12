<script lang="ts" setup>
import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import type { TrainingCatalogCategory } from './course-catalog';

import { TRAINING_CATALOG } from './course-catalog';
import { TRAINING_LEVELS, useTrainingDirection } from './use-training-direction';

defineOptions({ name: 'PortalTraining' });

const route = useRoute();
const router = useRouter();

/** F1~F10 培训学院模块导航，对应 /portal/training/* 子路由 */
const modules = [
  { label: '学习工作台', path: '/portal/training/workbench' },
  { label: '课程中心', path: '/portal/training/courses' },
  { label: '我的学习', path: '/portal/training/learning' },
  { label: 'VR 实操训练', path: '/portal/training/vr' },
  { label: '考试与证书', path: '/portal/training/exam' },
  { label: '直播大厅', path: '/portal/training/live' },
  { label: '培训任务与上岗', path: '/portal/training/tasks' },
  { label: '个人档案与成长', path: '/portal/training/profile' },
  { label: '校企合作专区', path: '/portal/training/cooperation' },
  { label: '创新大赛专区', path: '/portal/training/contest' },
];

const catalog = TRAINING_CATALOG;
const { state, levelName, load, save } = useTrainingDirection();

const dialog = ref(false);
const message = ref('');
const draft = ref<{ level: number; selected: string[] }>({
  level: 0,
  selected: [],
});

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
      <section class="dialog">
        <small>WELCOME TO LEARNING</small>
        <h2>想先学习什么？</h2>
        <p>请按"一级大类 → 二级中类 → 具体小类"的课程目录选择学习方向。</p>
        <div class="options">
          <button
            v-for="item in catalog"
            :key="item.code"
            type="button"
            :class="{ chosen: draft.selected.includes(item.code) }"
            @click="toggle(item.code)"
          >
            {{ item.code }} {{ item.name }}
          </button>
        </div>
        <label>
          当前成长等级
          <select v-model="draft.level">
            <option
              v-for="(name, index) in TRAINING_LEVELS"
              :key="name"
              :value="index"
            >
              {{ index + 1 }} · {{ name }}
            </option>
          </select>
        </label>
        <p v-if="message" class="message">{{ message }}</p>
        <footer>
          <button type="button" @click="dialog = false">稍后选择</button>
          <button class="primary" type="button" @click="saveDirection">
            进入学习中心
          </button>
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
  padding: 24px;
  background: rgb(15 23 42 / 42%);
}

.dialog {
  width: min(620px, 100%);
  padding: 30px;
  background: #fff;
  box-shadow: 0 18px 48px rgb(15 23 42 / 24%);
  border-radius: 12px;
}

.dialog > p {
  color: #67748b;
}

.dialog label {
  display: grid;
  gap: 8px;
  color: #43516a;
}

.dialog select {
  padding: 9px;
  background: #fff;
  border: 1px solid #d9e0ec;
  border-radius: 6px;
}

.options {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  margin: 16px 0;
}

.options button {
  padding: 8px 12px;
  color: #43516a;
  cursor: pointer;
  background: #fff;
  border: 1px solid #d9e0ec;
  border-radius: 6px;
}

.options button.chosen {
  color: #245fec;
  background: #eef4ff;
  border-color: #2f6bff;
}

footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 24px;
}

footer button {
  padding: 8px 14px;
  color: #245fec;
  cursor: pointer;
  background: #fff;
  border: 1px solid #cdd6e6;
  border-radius: 6px;
}

footer .primary {
  color: #fff;
  background: #2f6bff;
  border-color: #2f6bff;
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
