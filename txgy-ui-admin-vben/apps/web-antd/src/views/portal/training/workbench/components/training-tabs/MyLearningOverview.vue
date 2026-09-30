<script setup lang="ts">
import type { TrainingLearnerApi } from '../../../learning/api';

import { computed, onMounted, ref } from 'vue';

import { getMyCourses, getMySummary, getMyTracks } from '../../../learning/api';

const group = ref<TrainingLearnerApi.MyCoursesGroup | null>(null);
const summary = ref<TrainingLearnerApi.TrackSummary | null>(null);
const recentTracks = ref<TrainingLearnerApi.TrackRow[]>([]);
const loading = ref(true);

const stats = computed(() => [
  { label: '正在学习', value: group.value?.learningCount ?? group.value?.learningCourses?.length, unit: '门' },
  { label: '已完成', value: group.value?.completedCount ?? group.value?.completedCourses?.length, unit: '门' },
  { label: '累计学时', value: group.value?.creditEarned ?? summary.value?.totalCreditEarned, unit: '学时' },
]);
const recentCourses = computed(() => (group.value?.learningCourses ?? []).slice(0, 3));

onMounted(async () => {
  const results = await Promise.allSettled([
    getMyCourses(), getMySummary(), getMyTracks({ pageNo: 1, pageSize: 4 }),
  ]);
  if (results[0].status === 'fulfilled') group.value = results[0].value ?? null;
  if (results[1].status === 'fulfilled') summary.value = results[1].value ?? null;
  if (results[2].status === 'fulfilled') recentTracks.value = results[2].value?.list ?? [];
  loading.value = false;
});
</script>

<template>
  <div class="learning-overview">
    <div class="stat-grid">
      <article v-for="item in stats" :key="item.label">
        <strong>{{ item.value === undefined ? '—' : item.value }}</strong><small>{{ item.unit }}</small>
        <span>{{ item.label }}</span>
      </article>
    </div>
    <div class="overview-grid">
      <section>
        <h3>正在学习</h3>
        <p v-if="loading" class="empty">正在获取学习数据…</p>
        <template v-else-if="recentCourses.length">
          <div v-for="course in recentCourses" :key="course.courseId" class="course-row">
            <div><b>{{ course.title || '未命名课程' }}</b><small>{{ course.lecturer || '课程学习' }}</small></div>
            <span>{{ course.progressPercent ?? 0 }}%</span>
          </div>
        </template>
        <p v-else class="empty">暂无正在学习的课程。</p>
      </section>
      <section>
        <h3>最近学习</h3>
        <p v-if="loading" class="empty">正在获取学习记录…</p>
        <template v-else-if="recentTracks.length">
          <div v-for="track in recentTracks" :key="track.sessionId" class="course-row">
            <div><b>{{ track.courseTitle || track.mediaName || '学习记录' }}</b><small>{{ track.mediaTypeName || '学习轨迹' }}</small></div>
            <time>{{ track.endAt || track.startAt || '' }}</time>
          </div>
        </template>
        <p v-else class="empty">暂无最近学习记录。</p>
      </section>
    </div>
  </div>
</template>

<style scoped>
.learning-overview { width: 100%; }
.stat-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 16px; }
.stat-grid article, .overview-grid section { background: #fff; border: 1px solid #edf0f2; border-radius: 7px; box-shadow: 0 3px 12px rgb(31 43 52 / 3%); }
.stat-grid article { min-height: 105px; padding: 21px 25px; }
.stat-grid strong { color: #333d45; font-size: 28px; font-weight: 600; }
.stat-grid small { margin-left: 5px; color: #9aa2a8; font-size: 12px; }
.stat-grid span { display: block; margin-top: 6px; color: #777f86; font-size: 13px; }
.overview-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16px; margin-top: 17px; }
.overview-grid section { min-height: 208px; padding: 19px 23px; }
.overview-grid h3 { margin: 0 0 13px; color: #394149; font-size: 17px; font-weight: 550; }
.course-row { display: flex; align-items: center; justify-content: space-between; min-height: 47px; gap: 15px; border-top: 1px solid #f3f4f5; }
.course-row div { min-width: 0; }
.course-row b, .course-row small { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.course-row b { color: #4c545b; font-size: 13px; font-weight: 500; }.course-row small, .course-row time { color: #a1a8ae; font-size: 11px; }
.course-row > span { color: #e0782a; font-size: 13px; }.empty { color: #9ba3a9; font-size: 13px; }
@media (max-width: 820px) { .stat-grid, .overview-grid { grid-template-columns: 1fr; } }
</style>
