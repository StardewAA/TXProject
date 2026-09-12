<script lang="ts" setup>
import { computed, onMounted, ref, watch } from 'vue';
import { useRoute } from 'vue-router';

import { TRAINING_CATALOG } from '../course-catalog';

defineOptions({ name: 'PortalTrainingCourses' });

const route = useRoute();
const catalog = TRAINING_CATALOG;

const category = ref('全部');
const group = ref('全部');
const keyword = ref('');
const message = ref('');

const courses = computed(() => {
  const source = catalog.flatMap((c) =>
    c.groups.flatMap((g) =>
      g.courses.map((course) => ({
        code: course.code,
        category: c.code,
        group: g.name,
        name: course.name,
        credit: course.stdHours,
      })),
    ),
  );
  return source.filter(
    (x) =>
      (category.value === '全部' || x.category === category.value) &&
      (group.value === '全部' || x.group === group.value) &&
      (!keyword.value || `${x.name}${x.group}`.includes(keyword.value)),
  );
});

const currentGroups = computed(
  () => catalog.find((x) => x.code === category.value)?.groups ?? [],
);

function selectCategory(code: string) {
  category.value = code;
  group.value = '全部';
}

/** 支持从侧栏「课程中心」悬浮面板直接跳到某个大类/中类 */
function applyQuery() {
  const code = String(route.query.category ?? '');
  if (code && catalog.some((item) => item.code === code)) {
    category.value = code;
    group.value = String(route.query.group ?? '') || '全部';
  }
}

watch(() => [route.query.category, route.query.group], applyQuery);

onMounted(applyQuery);
</script>

<template>
  <div class="courses">
    <section class="filters-wrap">
      <div class="filters">
        <label>课程大类</label>
        <button
          type="button"
          :class="{ chosen: category === '全部' }"
          @click="selectCategory('全部')"
        >
          全部
        </button>
        <button
          v-for="item in catalog"
          :key="item.code"
          type="button"
          :class="{ chosen: category === item.code }"
          @click="selectCategory(item.code)"
        >
          {{ item.code }} {{ item.name }}
        </button>
      </div>
      <div v-if="category !== '全部'" class="filters">
        <label>中类</label>
        <button
          type="button"
          :class="{ chosen: group === '全部' }"
          @click="group = '全部'"
        >
          全部
        </button>
        <button
          v-for="item in currentGroups"
          :key="item.code"
          type="button"
          :class="{ chosen: group === item.name }"
          @click="group = item.name"
        >
          {{ item.code }} {{ item.name }}
        </button>
      </div>
      <input v-model="keyword" placeholder="搜索课程或中类" />
    </section>

    <p v-if="message" class="message">{{ message }}</p>

    <div class="course-grid">
      <article v-for="item in courses" :key="item.code" class="course-card">
        <div class="course-cover">
          <span>{{ item.code }}</span>
        </div>
        <div class="course-body">
          <h3>{{ item.name }}</h3>
          <p>{{ item.code }} · {{ item.group || item.category }}</p>
          <small v-if="item.credit">课程讲师待配置 · {{ item.credit }} 学时</small>
          <footer>
            <span>可自主学习</span>
            <button
              type="button"
              @click="message = '已加入学习：' + item.name"
            >
              开始学习
            </button>
          </footer>
        </div>
      </article>
      <p v-if="!courses.length">没有匹配的课程。</p>
    </div>
  </div>
</template>

<style scoped>
.filters-wrap {
  padding: 4px 0 16px;
  border-bottom: 1px solid #dfe3eb;
}

.filters {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  margin: 14px 0;
}

.filters label {
  width: 64px;
  color: #67748b;
  font-size: 13px;
}

.filters button {
  padding: 6px 10px;
  color: #43516a;
  cursor: pointer;
  background: #fff;
  border: 1px solid #d9e0ec;
  border-radius: 4px;
}

.filters button.chosen {
  color: #245fec;
  background: #eef4ff;
  border-color: #2f6bff;
}

input {
  padding: 9px;
  background: #fff;
  border: 1px solid #d9e0ec;
  border-radius: 4px;
}

.message {
  display: inline-block;
  padding: 6px 10px;
  margin: 16px 0 0;
  color: #245fec;
  background: rgb(234 242 255 / 74%);
}

.course-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 18px;
  padding: 20px 0;
}

.course-card {
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 292px;
  overflow: hidden;
  background: #fff;
  border: 1px solid #dbe5f4;
  border-radius: 10px;
  transition: transform 0.18s, box-shadow 0.18s;
}

.course-card:hover {
  transform: translateY(-3px);
  box-shadow: 0 12px 24px rgb(31 72 135 / 14%);
}

.course-cover {
  display: grid;
  height: 104px;
  place-items: center;
  color: #2f6bff;
  font-family: ui-monospace, monospace;
  font-weight: 700;
  background: linear-gradient(135deg, #eaf2ff, #d9e9ff);
}

.course-body {
  display: flex;
  flex: 1;
  flex-direction: column;
  padding: 14px;
}

.course-body h3 {
  display: -webkit-box;
  min-height: 44px;
  margin: 0;
  color: #182742;
  font-size: 15px;
  line-height: 22px;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  overflow: hidden;
}

.course-body p,
.course-body small {
  margin: 8px 0 0;
  color: #78869c;
  font-size: 12px;
}

.course-body footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: auto;
  padding-top: 14px;
}

.course-body footer span {
  color: #4d83ed;
  font-size: 12px;
}

.course-body footer button {
  padding: 6px 9px;
  color: #2865ec;
  cursor: pointer;
  background: #edf4ff;
  border: 0;
  border-radius: 4px;
}

@media (max-width: 1450px) {
  .course-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 900px) {
  .course-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
