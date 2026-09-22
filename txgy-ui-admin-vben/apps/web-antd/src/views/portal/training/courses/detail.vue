<script lang="ts" setup>
import type { TrainingLearnerApi } from '../learning/api';

import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import defaultCourseCover from '#/assets/imgs/academy/safety-course.png';

import { getCourseDetail, getPortalCourse } from '../learning/api';

defineOptions({ name: 'PortalTrainingCourseDetail' });

const route = useRoute();
const router = useRouter();

const courseId = Number(route.query.id);

const loading = ref(false);
const loadError = ref('');
const course = ref<TrainingLearnerApi.PortalCourse | null>(null);
const medias = ref<TrainingLearnerApi.MediaItem[]>([]);

/** 页内菜单：课程简介 / 课程视频 / 课程资料（不跳转页面） */
type MenuKey = 'intro' | 'video' | 'doc';
const activeMenu = ref<MenuKey>('intro');

const menus: { key: MenuKey; label: string }[] = [
  { key: 'intro', label: '课程简介' },
  { key: 'video', label: '课程视频' },
  { key: 'doc', label: '课程资料' },
];

/** 媒资类型：1 文档 2 视频（兼容后端返回 number 或 string） */
function isVideo(item: TrainingLearnerApi.MediaItem) {
  return Number(item.mediaType) === 2 || item.mediaType === 'video';
}
function isDocument(item: TrainingLearnerApi.MediaItem) {
  return Number(item.mediaType) === 1 || item.mediaType === 'document';
}

const videos = computed(() => medias.value.filter(isVideo));
const docs = computed(() => medias.value.filter(isDocument));

/** 适合学习人员：applicable_posts 为 JSON 数组字符串（如 ["管道工","防腐工"]） */
const audienceText = computed(() => {
  const raw = course.value?.applicablePosts;
  if (!raw) return '';
  try {
    const parsed = JSON.parse(raw);
    return Array.isArray(parsed) ? parsed.join('、') : String(raw);
  } catch {
    return raw;
  }
});

async function load() {
  if (!courseId) {
    loadError.value = '课程 ID 缺失';
    return;
  }
  loading.value = true;
  loadError.value = '';
  try {
    // 课程基础信息 + 简介/授课目标/适合学习人员（/training/portal/course/get）
    course.value = (await getPortalCourse(courseId)) ?? null;
    // 真实媒资列表（/training/portal/learner/course/detail，未选学也可查看）
    const detail = await getCourseDetail(courseId);
    medias.value = (detail?.medias ?? []).slice().sort((a, b) => (a.sort ?? 0) - (b.sort ?? 0));
  } catch {
    course.value = null;
    medias.value = [];
    loadError.value = '课程详情加载失败，请稍后重试';
  } finally {
    loading.value = false;
  }
}

/** 跳到项目已有在线学习页学习该媒资 */
function startStudy(item: TrainingLearnerApi.MediaItem) {
  if (!item.mediaId) return;
  router.push({
    path: '/portal/training/study',
    query: {
      courseId: String(courseId),
      mediaId: String(item.mediaId),
      mediaType: isDocument(item) ? 'document' : 'video',
      title: item.name ?? '',
    },
  });
}

function goBack() {
  router.push('/portal/training/courses');
}

onMounted(load);
</script>

<template>
  <div class="course-detail">
    <!-- 加载失败 -->
    <div v-if="loadError" class="state-block">
      <span>{{ loadError }}</span>
      <button type="button" @click="load">重新加载</button>
      <button type="button" class="ghost" @click="goBack">返回课程中心</button>
    </div>

    <template v-else>
      <!-- 顶部课程信息区域 -->
      <header class="course-head">
        <button type="button" class="back-btn" @click="goBack">‹ 返回</button>
        <div class="head-main">
          <div class="cover">
            <img
              :src="course?.coverUrl || defaultCourseCover"
              :alt="course?.title || '课程封面'"
            />
          </div>
          <div class="head-info">
            <h1>{{ course?.title || (loading ? '加载中…' : '未找到课程') }}</h1>
            <p v-if="course?.subtitle" class="subtitle">{{ course.subtitle }}</p>
            <div class="meta">
              <span v-if="course?.code">编号：{{ course.code }}</span>
              <span>{{ course?.lecturer || '讲师待配置' }}</span>
              <span v-if="course?.credit != null">{{ course.credit }} 学时</span>
              <span v-if="course?.durationMinutes != null">
                {{ course.durationMinutes }} 分钟
              </span>
              <span class="req" :class="{ must: course?.isRequired }">
                {{ course?.isRequired ? '必修' : '选修' }}
              </span>
            </div>
          </div>
        </div>
      </header>

      <!-- 课程内容菜单栏（页内切换，不跳转） -->
      <nav class="menu-bar" role="tablist">
        <button
          v-for="menu in menus"
          :key="menu.key"
          type="button"
          role="tab"
          :aria-selected="activeMenu === menu.key"
          :class="{ active: activeMenu === menu.key }"
          @click="activeMenu = menu.key"
        >
          {{ menu.label }}
        </button>
      </nav>

      <!-- 课程简介 -->
      <section v-show="activeMenu === 'intro'" class="panel intro">
        <div class="block">
          <h2>课程概述</h2>
          <p v-if="course?.summary">{{ course.summary }}</p>
          <p v-else class="empty-text">暂无课程概述</p>
        </div>
        <div class="block">
          <h2>适合学习人员</h2>
          <p v-if="audienceText">{{ audienceText }}</p>
          <p v-else class="empty-text">暂无适合学习人员说明</p>
        </div>
        <div class="block">
          <h2>授课目标</h2>
          <p v-if="course?.objectives">{{ course.objectives }}</p>
          <p v-else class="empty-text">暂无授课目标</p>
        </div>
      </section>

      <!-- 课程视频（真实媒资：training_course_media mediaType=2） -->
      <section v-show="activeMenu === 'video'" class="panel">
        <div v-if="loading" class="state-text">加载中…</div>
        <ul v-else-if="videos.length" class="media-list">
          <li v-for="item in videos" :key="item.mediaId">
            <button type="button" class="media-item" @click="startStudy(item)">
              <span class="media-name">{{ item.name || '未命名视频' }}</span>
              <span class="media-meta">
                {{ item.durationSeconds ? `${Math.round(item.durationSeconds / 60)} 分钟` : '时长待定' }}
                <i>›</i>
              </span>
            </button>
          </li>
        </ul>
        <div v-else class="state-text">暂无课程视频</div>
      </section>

      <!-- 课程资料（真实媒资：training_course_media mediaType=1） -->
      <section v-show="activeMenu === 'doc'" class="panel">
        <div v-if="loading" class="state-text">加载中…</div>
        <ul v-else-if="docs.length" class="media-list">
          <li v-for="item in docs" :key="item.mediaId">
            <button type="button" class="media-item" @click="startStudy(item)">
              <span class="media-name">{{ item.name || '未命名资料' }}</span>
              <span class="media-meta">
                {{ item.docTotalPages ? `${item.docTotalPages} 页` : '' }}
                <i>›</i>
              </span>
            </button>
          </li>
        </ul>
        <div v-else class="state-text">暂无课程资料</div>
      </section>
    </template>
  </div>
</template>

<style scoped>
.course-detail {
  max-width: 860px;
  margin: 0 auto;
}

/* ---------- 顶部课程信息 ---------- */
.course-head {
  padding: 0 0 16px;
  border-bottom: 1px solid #e5e9f0;
}

.back-btn {
  padding: 4px 10px 4px 0;
  color: #5c6b80;
  font-size: 13px;
  background: transparent;
  border: 0;
  cursor: pointer;
}

.back-btn:hover {
  color: #245fec;
}

.head-main {
  display: flex;
  gap: 16px;
  align-items: flex-start;
  margin-top: 6px;
}

.cover {
  flex-shrink: 0;
  width: 200px;
  height: 118px;
  overflow: hidden;
  background: #f0f3f8;
  border: 1px solid #e0e6ef;
  border-radius: 8px;
}

.cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.head-info {
  min-width: 0;
  flex: 1;
}

.head-info h1 {
  margin: 0;
  color: #17233a;
  font-size: 20px;
  font-weight: 700;
  line-height: 1.4;
}

.subtitle {
  margin: 6px 0 0;
  color: #667085;
  font-size: 13px;
}

.meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 14px;
  margin-top: 10px;
  color: #667085;
  font-size: 13px;
}

.meta .req {
  padding: 0 8px;
  border: 1px solid #cdd7e4;
  border-radius: 10px;
  color: #667085;
}

.meta .req.must {
  color: #d4541c;
  border-color: #f0c4ae;
  background: #fdf4ef;
}

/* ---------- 菜单栏 ---------- */
.menu-bar {
  display: flex;
  border-bottom: 1px solid #e5e9f0;
}

.menu-bar button {
  flex: 1;
  padding: 12px 0;
  color: #5c6b80;
  font-size: 15px;
  background: transparent;
  border: 0;
  border-bottom: 2px solid transparent;
  cursor: pointer;
  transition: color 0.15s, border-color 0.15s;
}

.menu-bar button:hover {
  color: #17233a;
}

.menu-bar button.active {
  color: #245fec;
  font-weight: 600;
  border-bottom-color: #245fec;
}

/* ---------- 内容面板 ---------- */
.panel {
  padding: 4px 0 24px;
}

/* 课程简介：独立标题 + 正文 + 分隔 */
.intro .block {
  padding: 18px 0;
  border-bottom: 1px dashed #e5e9f0;
}

.intro .block:last-child {
  border-bottom: 0;
}

.intro h2 {
  position: relative;
  margin: 0 0 10px;
  padding-left: 10px;
  color: #1c2a42;
  font-size: 16px;
  font-weight: 600;
}

.intro h2::before {
  content: '';
  position: absolute;
  top: 50%;
  left: 0;
  width: 3px;
  height: 14px;
  background: #245fec;
  transform: translateY(-50%);
}

.intro p {
  margin: 0;
  color: #3d4a5f;
  font-size: 15px;
  line-height: 1.8;
  white-space: pre-wrap;
  word-break: break-word;
}

.empty-text,
.state-text {
  padding: 36px 0;
  color: #98a2b3;
  font-size: 14px;
  text-align: center;
}

/* ---------- 视频/资料列表 ---------- */
.media-list {
  margin: 12px 0 0;
  padding: 0;
  list-style: none;
}

.media-item {
  display: flex;
  width: 100%;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
  padding: 13px 12px;
  margin-bottom: 10px;
  text-align: left;
  background: #fff;
  border: 1px solid #e3e8f0;
  border-radius: 8px;
  cursor: pointer;
  transition: border-color 0.15s, background-color 0.15s;
}

.media-item:hover {
  border-color: #b9cdf0;
  background: #f8faff;
}

.media-name {
  min-width: 0;
  flex: 1;
  color: #23324a;
  font-size: 14px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.media-meta {
  display: flex;
  flex-shrink: 0;
  gap: 8px;
  align-items: center;
  color: #8792a5;
  font-size: 12px;
}

.media-meta i {
  color: #b3bcc9;
  font-style: normal;
}

/* ---------- 加载失败 ---------- */
.state-block {
  display: flex;
  min-height: 220px;
  flex-direction: column;
  gap: 10px;
  align-items: center;
  justify-content: center;
  color: #667085;
}

.state-block button {
  padding: 7px 16px;
  color: #245fec;
  cursor: pointer;
  background: #edf4ff;
  border: 0;
  border-radius: 5px;
}

.state-block button.ghost {
  color: #667085;
  background: #f4f6fa;
}

/* ---------- 手机端 ---------- */
@media (max-width: 640px) {
  .head-main {
    flex-direction: column;
    gap: 12px;
  }

  .cover {
    width: 100%;
    height: 180px;
  }

  .head-info h1 {
    font-size: 18px;
  }

  .meta {
    gap: 6px 10px;
    font-size: 12.5px;
  }

  .menu-bar button {
    font-size: 14px;
  }

  .intro p {
    font-size: 14.5px;
    line-height: 1.9;
  }
}
</style>
