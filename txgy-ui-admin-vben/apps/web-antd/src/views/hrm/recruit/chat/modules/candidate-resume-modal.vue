<script lang="ts" setup>
import type { HrmPortalRecruitChatApi } from '#/api/hrm/portal/recruit/chat';

import { computed, ref, watch } from 'vue';

import { IconifyIcon } from '@vben/icons';

import { getAdminRecruitChatCandidateResume } from '#/api/hrm/portal/recruit/chat';

import {
  formatPeriod,
  hasResumeContent,
  jobTypeLabel,
  parseResumeProfile,
  salaryLabel,
} from './resume-profile';

const props = defineProps<{
  conversation?: HrmPortalRecruitChatApi.Conversation;
  open: boolean;
}>();

const emit = defineEmits<{
  'update:open': [value: boolean];
}>();

const modalOpen = computed({
  get: () => props.open,
  set: (value: boolean) => emit('update:open', value),
});

const loading = ref(false);
const errorText = ref('');
const resume = ref<HrmPortalRecruitChatApi.CandidateResume>();

const profile = computed(() => parseResumeProfile(resume.value?.content));
const hasContent = computed(() => hasResumeContent(profile.value));
const displayName = computed(
  () =>
    resume.value?.name ||
    props.conversation?.candidateName ||
    props.conversation?.peerUserName ||
    '求职者',
);
const modalTitle = computed(() => `${displayName.value} 的在线简历`);
/** 快照是投递当时冻结的版本，与求职者当前正在编辑的简历可能不同，需要让 HR 看得出来 */
const isSnapshot = computed(() => resume.value?.source === 'SNAPSHOT');

async function loadResume() {
  const conversationId = props.conversation?.id;
  resume.value = undefined;
  errorText.value = '';
  if (!conversationId) return;
  loading.value = true;
  try {
    resume.value = await getAdminRecruitChatCandidateResume(conversationId);
  } catch (error: any) {
    errorText.value =
      error?.msg || error?.message || '该求职者暂无可查看的在线简历';
  } finally {
    loading.value = false;
  }
}

watch(
  () => [props.open, props.conversation?.id],
  ([open]) => {
    if (open) void loadResume();
  },
);
</script>

<template>
  <a-modal
    v-model:open="modalOpen"
    :footer="null"
    :title="modalTitle"
    width="900px"
    wrap-class-name="candidate-resume-modal"
  >
    <div v-if="loading" class="resume-state">
      <a-spin />
      <span>正在读取在线简历…</span>
    </div>

    <div v-else-if="errorText || !hasContent" class="resume-state">
      <span class="resume-state-icon"><IconifyIcon icon="lucide:file-text" /></span>
      <strong>{{ errorText || '该求职者尚未填写在线简历' }}</strong>
      <p>求职者完善在线简历后，可在这里查看完整内容。</p>
    </div>

    <article v-else class="resume-preview">
      <header class="resume-head">
        <span class="resume-head-avatar">
          <img v-if="profile.avatar" :src="profile.avatar" alt="" />
          <IconifyIcon v-else icon="lucide:user-round" />
        </span>
        <div class="resume-head-copy">
          <div class="resume-head-name">
            <h3>{{ profile.name || displayName }}</h3>
            <span
              class="resume-source"
              :class="isSnapshot ? 'snapshot' : 'online'"
            >
              <IconifyIcon
                :icon="isSnapshot ? 'lucide:file-check-2' : 'lucide:file-text'"
              />
              {{ isSnapshot ? '投递时的简历' : '当前在线简历' }}
            </span>
          </div>
          <p>
            {{
              [profile.mobile, profile.email, profile.employmentStatus]
                .filter(Boolean)
                .join(' · ') || '未填写联系方式'
            }}
          </p>
          <p v-if="profile.domicile">户籍地：{{ profile.domicile }}</p>
        </div>
      </header>

      <section class="resume-block">
        <h4>个人优势</h4>
        <p class="resume-text">{{ profile.strengths || '未填写' }}</p>
      </section>

      <section class="resume-block">
        <h4>期望职位</h4>
        <dl class="resume-grid">
          <div>
            <dt>求职类型</dt>
            <dd>{{ jobTypeLabel(profile.jobType) }}</dd>
          </div>
          <div>
            <dt>期望职位</dt>
            <dd>{{ profile.expectedPostName || '未填写' }}</dd>
          </div>
          <div>
            <dt>期望行业</dt>
            <dd>{{ profile.industry || '未填写' }}</dd>
          </div>
          <div>
            <dt>薪资要求</dt>
            <dd>{{ salaryLabel(profile.salary) }}</dd>
          </div>
        </dl>
        <p v-if="profile.expectedCities.length" class="resume-tags">
          <span v-for="city in profile.expectedCities" :key="city">{{ city }}</span>
        </p>
      </section>

      <section class="resume-block">
        <h4>工作 / 实习经历</h4>
        <p v-if="!profile.experiences.length" class="resume-text">未填写</p>
        <article
          v-for="(item, index) in profile.experiences"
          :key="`work-${index}`"
          class="resume-entry"
        >
          <div class="resume-entry-head">
            <strong>
              {{ item.position || '未填写职位' }} · {{ item.company || '未填写公司' }}
            </strong>
            <small>{{ formatPeriod(item.start, item.end) }}</small>
          </div>
          <p v-if="item.industry || item.department" class="resume-meta">
            {{ [item.industry, item.department].filter(Boolean).join(' · ') }}
          </p>
          <p v-if="item.description" class="resume-text">{{ item.description }}</p>
          <p v-if="item.achievements" class="resume-text">
            <b>工作业绩</b>{{ item.achievements }}
          </p>
        </article>
      </section>

      <section class="resume-block">
        <h4>项目经历</h4>
        <p v-if="!profile.projects.length" class="resume-text">未填写</p>
        <article
          v-for="(item, index) in profile.projects"
          :key="`project-${index}`"
          class="resume-entry"
        >
          <div class="resume-entry-head">
            <strong>
              {{ item.name || '未填写项目名称' }} · {{ item.role || '未填写角色' }}
            </strong>
            <small>{{ formatPeriod(item.start, item.end) }}</small>
          </div>
          <p v-if="item.description" class="resume-text">{{ item.description }}</p>
          <p v-if="item.achievements" class="resume-text">
            <b>项目业绩</b>{{ item.achievements }}
          </p>
          <p v-if="item.link" class="resume-meta">
            <a :href="item.link" target="_blank" rel="noopener noreferrer">
              {{ item.link }}
            </a>
          </p>
        </article>
      </section>

      <section class="resume-block">
        <h4>教育经历</h4>
        <p v-if="!profile.educationExperiences.length" class="resume-text">未填写</p>
        <article
          v-for="(item, index) in profile.educationExperiences"
          :key="`edu-${index}`"
          class="resume-entry"
        >
          <div class="resume-entry-head">
            <strong>
              {{ item.degree || '未填写学历' }} · {{ item.school || '未填写学校' }}
            </strong>
            <small>{{ formatPeriod(item.start, item.end) }}</small>
          </div>
          <p class="resume-text">
            {{ item.major || '未填写专业' }}
            <template v-if="item.schoolType"> · {{ item.schoolType }}</template>
          </p>
          <p v-if="item.experience" class="resume-text">{{ item.experience }}</p>
        </article>
      </section>

      <section class="resume-block">
        <h4>专业技能</h4>
        <p class="resume-text">{{ profile.skills || '未填写' }}</p>
      </section>
    </article>
  </a-modal>
</template>

<style scoped>
.resume-state {
  display: grid;
  min-height: 200px;
  place-items: center;
  align-content: center;
  gap: 10px;
  color: #6b7a8c;
  font-size: 13px;
  text-align: center;
}

.resume-state-icon {
  display: grid;
  width: 48px;
  height: 48px;
  place-items: center;
  border-radius: 50%;
  background: #eef3f9;
  color: #7d8fa3;
  font-size: 22px;
}

.resume-state strong {
  color: #1f2d3d;
  font-size: 15px;
}

.resume-state p {
  margin: 0;
  color: #8a99ab;
}

.resume-preview {
  display: grid;
  max-height: 66vh;
  gap: 14px;
  overflow-y: auto;
  padding-right: 6px;
}

.resume-head {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 18px 20px;
  border: 1px solid #dfeaf6;
  border-radius: 10px;
  background: linear-gradient(120deg, #f7fbff, #eef5fd);
}

.resume-head-avatar {
  display: grid;
  width: 64px;
  height: 64px;
  flex: 0 0 auto;
  place-items: center;
  overflow: hidden;
  border: 1px solid #d3e4f6;
  border-radius: 50%;
  background: #e2eefb;
  color: #3b7dd8;
  font-size: 26px;
}

.resume-head-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.resume-head-copy {
  min-width: 0;
}

.resume-head-name {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.resume-head-name h3 {
  margin: 0;
  color: #16283c;
  font-size: 20px;
  font-weight: 650;
}

.resume-head p {
  margin: 6px 0 0;
  color: #5b6b7d;
  font-size: 13px;
}

.resume-source {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 3px 9px;
  border-radius: 999px;
  font-size: 12px;
}

.resume-source.snapshot {
  background: #e9f7ef;
  color: #1f9254;
}

.resume-source.online {
  background: #e8f1fc;
  color: #2c6cb5;
}

.resume-block {
  padding: 16px 18px;
  border: 1px solid #eaeff5;
  border-radius: 10px;
  background: #fff;
}

.resume-block h4 {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0 0 12px;
  color: #1f2d3d;
  font-size: 15px;
  font-weight: 600;
}

.resume-block h4::before {
  width: 3px;
  height: 14px;
  border-radius: 2px;
  background: #1677ff;
  content: '';
}

.resume-text {
  margin: 8px 0 0;
  color: #4e5d6e;
  font-size: 13px;
  line-height: 1.75;
  white-space: pre-wrap;
  word-break: break-word;
}

.resume-text b {
  margin-right: 6px;
  color: #1f2d3d;
  font-weight: 600;
}

.resume-meta {
  margin: 6px 0 0;
  color: #8a99ab;
  font-size: 12px;
}

.resume-meta a {
  color: #2c6cb5;
  word-break: break-all;
}

.resume-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px 24px;
  margin: 0;
}

.resume-grid > div {
  display: flex;
  gap: 8px;
  font-size: 13px;
}

.resume-grid dt {
  flex: 0 0 auto;
  color: #8a99ab;
}

.resume-grid dd {
  margin: 0;
  color: #1f2d3d;
}

.resume-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin: 12px 0 0;
}

.resume-tags span {
  padding: 3px 10px;
  border-radius: 999px;
  background: #eef5fd;
  color: #2c6cb5;
  font-size: 12px;
}

.resume-entry {
  padding: 12px 0;
  border-top: 1px dashed #edf1f6;
}

.resume-entry:first-of-type {
  padding-top: 0;
  border-top: none;
}

.resume-entry-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
}

.resume-entry-head strong {
  color: #1f2d3d;
  font-size: 14px;
  font-weight: 600;
}

.resume-entry-head small {
  flex: 0 0 auto;
  color: #8a99ab;
  font-size: 12px;
}

@media (max-width: 640px) {
  .resume-grid {
    grid-template-columns: 1fr;
  }
}
</style>
