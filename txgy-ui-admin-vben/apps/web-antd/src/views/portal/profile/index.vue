<script lang="ts" setup>
import { computed, onMounted, reactive, ref } from 'vue';

import { message } from 'ant-design-vue';
import { IconifyIcon } from '@vben/icons';
import { useUserStore } from '@vben/stores';

import ExamWorkspace from '#/views/training/components/exam-workspace.vue';
import { uploadFile } from '#/api/infra/file';
import {
  getPortalRecruitApplication,
  getPortalRecruitApplicationPage,
  withdrawPortalRecruitApplication,
  type HrmPortalRecruitApi,
} from '#/api/hrm/portal/recruit';
import {
  getPortalResumeList,
  uploadPortalResume,
  type HrmPortalResumeApi,
} from '#/api/hrm/portal/resume';

const MAX_RESUME_SIZE = 10 * 1024 * 1024;
const ACCEPTED_RESUME_EXTENSIONS = ['pdf', 'doc', 'docx'];

const userStore = useUserStore();
const userInfo = computed(() => userStore.userInfo);
const resumeLoading = ref(false);
const uploadSubmitting = ref(false);
const resumes = ref<HrmPortalResumeApi.Resume[]>([]);
const uploadInput = ref<HTMLInputElement>();
const selectedUploadFile = ref<File>();
const resumeSort = ref<'latest' | 'oldest'>('latest');
const uploadForm = reactive({ title: '', remark: '' });
const applicationLoading = ref(false);
const applications = ref<HrmPortalRecruitApi.Application[]>([]);
const applicationTotal = ref(0);
const applicationPage = reactive({ pageNo: 1, pageSize: 6 });
const selectedApplication = ref<HrmPortalRecruitApi.Application>();
const applicationModalOpen = ref(false);

const attachmentCount = computed(
  () => resumes.value.filter((resume) => resume.resumeType === 1).length,
);
const visibleResumes = computed(() => {
  const attachments = resumes.value.filter((resume) => resume.resumeType === 1);
  return [...attachments].sort((left, right) => {
    const leftTime = left.createTime ? new Date(left.createTime).getTime() : 0;
    const rightTime = right.createTime
      ? new Date(right.createTime).getTime()
      : 0;
    return resumeSort.value === 'latest'
      ? rightTime - leftTime
      : leftTime - rightTime;
  });
});

function formatDate(value?: string) {
  return value
    ? new Date(value).toLocaleString('zh-CN', {
        hour12: false,
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
      })
    : '-';
}

function validateResumeFile(file: File) {
  const extension = file.name.split('.').pop()?.toLowerCase();
  if (!extension || !ACCEPTED_RESUME_EXTENSIONS.includes(extension)) {
    message.warning('仅支持 PDF、DOC、DOCX 格式的简历');
    return false;
  }
  if (file.size > MAX_RESUME_SIZE) {
    message.warning('简历文件不能超过 10MB');
    return false;
  }
  return true;
}

function handleFileChange(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0];
  if (!file || !validateResumeFile(file)) {
    selectedUploadFile.value = undefined;
    return;
  }
  selectedUploadFile.value = file;
}

function clearSelectedFile() {
  selectedUploadFile.value = undefined;
  if (uploadInput.value) uploadInput.value.value = '';
}

async function loadResumes() {
  resumeLoading.value = true;
  try {
    resumes.value = (await getPortalResumeList()) || [];
  } catch (error) {
    resumes.value = [];
    console.warn('[PortalProfile] 加载简历失败', error);
  } finally {
    resumeLoading.value = false;
  }
}

async function loadApplications() {
  applicationLoading.value = true;
  try {
    const result = await getPortalRecruitApplicationPage(applicationPage);
    applications.value = result.list || [];
    applicationTotal.value = result.total || 0;
  } catch (error) {
    applications.value = [];
    applicationTotal.value = 0;
    console.warn('[PortalProfile] 加载应聘记录失败', error);
  } finally {
    applicationLoading.value = false;
  }
}

async function showApplication(application: HrmPortalRecruitApi.Application) {
  selectedApplication.value = await getPortalRecruitApplication(application.id);
  applicationModalOpen.value = true;
}

async function withdrawApplication(application: HrmPortalRecruitApi.Application) {
  await withdrawPortalRecruitApplication(application.id, '用户主动撤回');
  message.success('应聘已撤回');
  await loadApplications();
}

function handleApplicationPageChange(page: number, pageSize: number) {
  applicationPage.pageNo = page;
  applicationPage.pageSize = pageSize;
  void loadApplications();
}

async function handleUploadResume() {
  const file = selectedUploadFile.value;
  if (!uploadForm.title.trim()) {
    message.warning('请输入简历标题');
    return;
  }
  if (!file) {
    message.warning('请选择简历附件');
    return;
  }
  uploadSubmitting.value = true;
  try {
    const fileUrl = (await uploadFile({
      file,
      directory: 'hrm/personal/resume',
    })) as string;
    await uploadPortalResume({
      title: uploadForm.title.trim(),
      fileName: file.name,
      fileUrl,
      remark: uploadForm.remark.trim() || undefined,
    });
    message.success('简历附件已保存');
    uploadForm.title = '';
    uploadForm.remark = '';
    clearSelectedFile();
    await loadResumes();
  } finally {
    uploadSubmitting.value = false;
  }
}

onMounted(async () => {
  await Promise.all([loadResumes(), loadApplications()]);
});
</script>

<template>
  <div class="portal-page profile-page">
    <section class="profile-header">
      <div>
        <p class="eyebrow">账号与求职资料</p>
        <h1>个人中心</h1>
        <p>完善个人信息和简历，让每次投递都更高效。</p>
      </div>
      <div class="profile-stat">
        <strong>{{ resumes.length }}</strong>
        <span>份简历</span>
      </div>
    </section>
    <section class="profile-overview">
      <div class="profile-identity">
        <span class="profile-avatar">
          {{ (userInfo?.nickname || userInfo?.username || '用').slice(0, 1) }}
        </span>
        <div>
          <h2>{{ userInfo?.nickname || userInfo?.username || '用户' }}</h2>
          <p>{{ userInfo?.username || '个人用户' }}</p>
        </div>
      </div>
      <div class="profile-fields">
        <div>
          <span>用户编号</span>
          <strong>{{ userInfo?.userId || userInfo?.id || '-' }}</strong>
        </div>
        <div>
          <span>账号角色</span>
          <strong>{{ userInfo?.roles?.join('、') || '普通用户' }}</strong>
        </div>
      </div>
    </section>

    <section class="resume-section">
      <div class="section-heading">
        <div>
          <p class="eyebrow">求职资料</p>
          <h2>我的简历</h2>
          <p>上传简历附件，投递岗位时可直接使用。</p>
        </div>
        <div class="resume-summary">
          <span><strong>{{ attachmentCount }}</strong> 份附件</span>
        </div>
      </div>

      <div class="resume-actions">
        <div class="resume-tool">
          <div class="tool-heading">
            <span class="tool-icon upload-icon"
              ><IconifyIcon icon="lucide:cloud-upload"
            /></span>
            <div>
              <h3>上传简历附件</h3>
              <p>适合已有 PDF 或 Word 简历的求职者</p>
            </div>
          </div>
          <a-input
            v-model:value="uploadForm.title"
            placeholder="给简历起个标题，例如：产品经理简历"
          />
          <label
            class="file-dropzone"
            :class="{ 'has-file': selectedUploadFile }"
          >
            <input
              ref="uploadInput"
              type="file"
              accept=".pdf,.doc,.docx"
              @change="handleFileChange"
            />
            <IconifyIcon
              :icon="
                selectedUploadFile
                  ? 'lucide:file-check-2'
                  : 'lucide:cloud-upload'
              "
            />
            <strong>{{ selectedUploadFile?.name || '选择简历文件' }}</strong>
            <span>{{
              selectedUploadFile
                ? '已选择，可点击重新选择'
                : '支持 PDF、DOC、DOCX，文件不超过 10MB'
            }}</span>
          </label>
          <a-input
            v-model:value="uploadForm.remark"
            placeholder="备注（可选）"
          />
          <div class="tool-footer">
            <a-button
              v-if="selectedUploadFile"
              type="link"
              @click="clearSelectedFile"
              >重新选择</a-button
            >
            <a-button
              type="primary"
              :loading="uploadSubmitting"
              @click="handleUploadResume"
            >
              <IconifyIcon icon="lucide:save" />
              上传并保存
            </a-button>
          </div>
        </div>
      </div>

      <div class="resume-list-heading">
        <h3>已保存的简历</h3>
        <div class="resume-filters">
          <a-select
            v-model:value="resumeSort"
            size="small"
            class="resume-sort-select"
          >
            <a-select-option value="latest">最新创建</a-select-option>
            <a-select-option value="oldest">最早创建</a-select-option>
          </a-select>
        </div>
      </div>

      <div v-if="resumeLoading" class="resume-loading">
        <a-skeleton
          v-for="item in 3"
          :key="item"
          active
          :paragraph="{ rows: 2 }"
        />
      </div>
      <div v-else-if="!visibleResumes.length" class="portal-empty">
        <span class="portal-empty-icon">
          <IconifyIcon icon="lucide:file-text" />
        </span>
        <strong>暂无附件简历</strong>
        <p>上传一份简历，投递岗位时即可直接使用</p>
      </div>
      <div v-else class="resume-list">
        <article
          v-for="resume in visibleResumes"
          :key="resume.id"
          class="resume-item"
        >
          <span class="resume-item-icon attachment">
            <IconifyIcon icon="lucide:file-text" />
          </span>
          <div class="resume-item-main">
            <div class="resume-item-title">
              <strong>{{ resume.title }}</strong>
              <a-tag color="blue">附件简历</a-tag>
            </div>
            <p v-if="resume.fileName" class="resume-meta">
              附件：{{ resume.fileName }}
            </p>
            <p v-if="resume.remark" class="resume-meta">
              备注：{{ resume.remark }}
            </p>
          </div>
          <div class="resume-item-side">
            <span>{{ formatDate(resume.createTime) }}</span>
            <a
              v-if="resume.fileUrl"
              :href="resume.fileUrl"
              target="_blank"
              rel="noopener noreferrer"
            >
              <IconifyIcon icon="lucide:external-link" />
              查看附件
            </a>
          </div>
        </article>
      </div>
    </section>

    <section class="application-section">
      <div class="section-heading">
        <div>
          <p class="eyebrow">招聘进度</p>
          <h2>我的应聘</h2>
          <p>查看已投递岗位、面试安排和 Offer 状态。</p>
        </div>
        <span class="application-summary">共 {{ applicationTotal }} 条记录</span>
      </div>
      <div v-if="applicationLoading" class="resume-loading">
        <a-skeleton v-for="item in 3" :key="item" active :paragraph="{ rows: 2 }" />
      </div>
      <div v-else-if="!applications.length" class="portal-empty">
        <span class="portal-empty-icon">
          <IconifyIcon icon="lucide:clipboard-list" />
        </span>
        <strong>暂无应聘记录</strong>
        <p>投递岗位后，可在这里查看招聘进度</p>
      </div>
      <div v-else class="application-list">
        <article v-for="application in applications" :key="application.id" class="application-item">
          <div class="application-item-main">
            <div class="application-item-title">
              <strong>{{ application.postName || '招聘职位' }}</strong>
              <a-tag :color="application.status === 'ELIMINATED' ? 'error' : application.status === 'WITHDRAWN' ? 'default' : 'processing'">
                {{ application.statusName || application.status }}
              </a-tag>
            </div>
            <p>投递于 {{ formatDate(application.createTime) }}</p>
            <p v-if="application.interviews?.length">
              最近面试：第 {{ application.interviews[application.interviews.length - 1]?.stageNumber || 1 }} 轮，
              {{ formatDate(application.interviews[application.interviews.length - 1]?.interviewTime) }}
            </p>
          </div>
          <div class="application-item-actions">
            <a-button type="link" @click="showApplication(application)">查看进度</a-button>
            <a-button
              v-if="['SUBMITTED', 'SCREENING', 'INTERVIEWING'].includes(application.status)"
              type="link"
              danger
              @click="withdrawApplication(application)"
            >撤回应聘</a-button>
          </div>
        </article>
      </div>
      <a-pagination
        v-if="applicationTotal"
        :current="applicationPage.pageNo"
        :page-size="applicationPage.pageSize"
        :total="applicationTotal"
        class="application-pagination"
        @change="handleApplicationPageChange"
      />
    </section>

    <a-modal v-model:open="applicationModalOpen" title="应聘进度" :footer="null" width="680px">
      <template v-if="selectedApplication">
        <a-descriptions :column="2" bordered>
          <a-descriptions-item label="职位">{{ selectedApplication.postName }}</a-descriptions-item>
          <a-descriptions-item label="当前状态">{{ selectedApplication.statusName || selectedApplication.status }}</a-descriptions-item>
          <a-descriptions-item label="投递时间">{{ formatDate(selectedApplication.createTime) }}</a-descriptions-item>
          <a-descriptions-item label="简历">{{ selectedApplication.resumeFileName || '-' }}</a-descriptions-item>
        </a-descriptions>
        <a-timeline class="timeline">
          <a-timeline-item color="green">已投递：{{ formatDate(selectedApplication.createTime) }}</a-timeline-item>
          <a-timeline-item v-for="interview in selectedApplication.interviews || []" :key="interview.id" color="blue">
            第 {{ interview.stageNumber || 1 }} 轮面试：{{ formatDate(interview.interviewTime) }}，{{ interview.address || '线上' }}
          </a-timeline-item>
          <a-timeline-item v-if="selectedApplication.offer" color="green">
            Offer：{{ selectedApplication.offer.approvalStatus || '处理中' }}
          </a-timeline-item>
        </a-timeline>
      </template>
    </a-modal>
    <section class="profile-panel">
      <h2>个人考试与认证档案</h2>
      <ExamWorkspace mode="learner" initial-page="mycerts" />
    </section>
  </div>
</template>

<style scoped>
.portal-page {
  display: grid;
  gap: 20px;
}

.profile-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: 28px 32px;
  border: 1px solid #dbeafe;
  background: #eff6ff;
}

.eyebrow {
  margin: 0 0 8px;
  color: #2563eb;
  font-size: 13px;
  font-weight: 600;
}

.profile-header h1 {
  margin: 0 0 8px;
  color: #111827;
  font-size: 28px;
}

.profile-header p:last-child {
  margin: 0;
  color: #4b5563;
  font-size: 14px;
}

.profile-stat {
  display: grid;
  min-width: 82px;
  gap: 2px;
  color: #64748b;
  text-align: center;
}

.profile-stat strong {
  color: #1d4ed8;
  font-size: 30px;
  line-height: 1;
}

.profile-stat span {
  font-size: 12px;
}

.profile-overview {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  padding: 22px 24px;
  border: 1px solid #e5e7eb;
  background: #fff;
}

.profile-identity {
  display: flex;
  align-items: center;
  gap: 13px;
}

.profile-avatar {
  display: grid;
  width: 48px;
  height: 48px;
  place-items: center;
  border-radius: 50%;
  color: #1d4ed8;
  background: #dbeafe;
  font-size: 20px;
  font-weight: 650;
}

.profile-identity h2 {
  margin: 0 0 5px;
  color: #1f2937;
  font-size: 17px;
}

.profile-identity p {
  margin: 0;
  color: #64748b;
  font-size: 13px;
}

.profile-fields {
  display: flex;
  gap: 32px;
}

.profile-fields > div {
  display: grid;
  gap: 5px;
  min-width: 100px;
}

.profile-fields span {
  color: #94a3b8;
  font-size: 12px;
}

.profile-fields strong {
  color: #334155;
  font-size: 13px;
  font-weight: 500;
}

.resume-section {
  padding: 28px 24px 24px;
  border: 1px solid #e5e7eb;
  background: #fff;
}

.application-section {
  padding: 28px 24px 24px;
  border: 1px solid #e5e7eb;
  background: #fff;
}

.application-summary {
  color: #64748b;
  font-size: 13px;
}

.application-list {
  display: grid;
  gap: 10px;
}

.application-item {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: flex-start;
  gap: 16px;
  padding: 16px;
  border: 1px solid #eef0f2;
}

.application-item-main {
  min-width: 0;
}

.application-item-title {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.application-item-title strong {
  color: #1f2937;
  font-size: 15px;
}

.application-item-main p {
  margin: 7px 0 0;
  color: #64748b;
  font-size: 13px;
}

.application-item-actions {
  display: flex;
  flex-shrink: 0;
  gap: 4px;
}

.application-pagination {
  margin-top: 16px;
  text-align: right;
}

.section-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 22px;
}

.section-heading h2 {
  margin: 0 0 8px;
  color: #111827;
  font-size: 22px;
}

.section-heading p:last-child {
  margin: 0;
  color: #6b7280;
  font-size: 14px;
}

.resume-summary {
  display: flex;
  gap: 14px;
  color: #94a3b8;
  font-size: 13px;
  white-space: nowrap;
}

.resume-summary strong {
  color: #334155;
  font-size: 16px;
}

.resume-actions {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 16px;
  margin-bottom: 28px;
}

.resume-tool {
  display: grid;
  align-content: start;
  gap: 12px;
  padding: 18px;
  border: 1px solid #e2e8f0;
  background: #fafbfc;
}

.tool-heading {
  display: flex;
  align-items: center;
  gap: 10px;
}

.tool-heading h3 {
  margin: 0 0 4px;
  color: #1f2937;
  font-size: 16px;
}

.tool-heading p {
  margin: 0;
  color: #94a3b8;
  font-size: 12px;
}

.tool-icon {
  display: grid;
  width: 36px;
  height: 36px;
  flex-shrink: 0;
  place-items: center;
  font-size: 18px;
}

.upload-icon {
  color: #2563eb;
  background: #dbeafe;
}

.file-dropzone {
  display: grid;
  min-height: 88px;
  place-items: center;
  align-content: center;
  gap: 5px;
  padding: 12px;
  border: 1px dashed #93c5fd;
  color: #2563eb;
  background: #eff6ff;
  cursor: pointer;
  text-align: center;
  transition:
    border-color 160ms ease,
    background 160ms ease;
}

.file-dropzone:hover,
.file-dropzone.has-file {
  border-color: #2563eb;
  background: #e0f2fe;
}

.file-dropzone input {
  display: none;
}

.file-dropzone strong {
  max-width: 100%;
  overflow: hidden;
  color: #334155;
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-dropzone span {
  color: #64748b;
  font-size: 12px;
}

.tool-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.tool-footer .ant-btn {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}

.resume-list-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 13px;
  padding-top: 18px;
  border-top: 1px solid #f1f5f9;
}

.resume-list-heading h3 {
  margin: 0;
  color: #1f2937;
  font-size: 16px;
}

.resume-filters {
  display: flex;
  align-items: center;
  gap: 10px;
}

.resume-sort-select {
  width: 108px;
}

.resume-loading {
  display: grid;
  gap: 12px;
  padding: 12px;
}

.portal-empty {
  display: grid;
  width: 100%;
  box-sizing: border-box;
  grid-template-columns: minmax(0, 1fr);
  min-height: 220px;
  place-items: center;
  align-content: center;
  justify-items: center;
  gap: 8px;
  padding: 32px 16px;
  border: 1px dashed #d1d5db;
  color: #64748b;
  text-align: center;
}

.portal-empty-icon {
  display: grid;
  width: 52px;
  min-width: 52px;
  height: 52px;
  min-height: 52px;
  flex: 0 0 52px;
  place-items: center;
  border-radius: 50%;
  color: #2563eb;
  background: #eff6ff;
  font-size: 24px;
}

.portal-empty-icon :deep(svg) {
  width: 24px;
  height: 24px;
}

.portal-empty strong {
  color: #334155;
  font-size: 15px;
  font-weight: 600;
}

.portal-empty p {
  margin: 0;
  color: #94a3b8;
  font-size: 13px;
}

.resume-list {
  display: grid;
  gap: 10px;
}

.resume-item {
  display: grid;
  grid-template-columns: 36px minmax(0, 1fr) auto;
  align-items: flex-start;
  gap: 12px;
  padding: 16px;
  border: 1px solid #eef0f2;
  transition:
    border-color 160ms ease,
    box-shadow 160ms ease;
}

.resume-item:hover {
  border-color: #bfdbfe;
  box-shadow: 0 5px 15px rgb(30 64 175 / 7%);
}

.resume-item-icon {
  display: grid;
  width: 36px;
  height: 36px;
  flex-shrink: 0;
  place-items: center;
  font-size: 17px;
}

.resume-item-icon.attachment {
  color: #2563eb;
  background: #eff6ff;
}

.resume-item-main {
  min-width: 0;
}

.resume-item-title {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.resume-item-title strong {
  color: #1f2937;
  font-size: 15px;
}

.resume-item-title :deep(.ant-tag) {
  margin: 0;
}

.resume-meta {
  margin: 7px 0 0;
  color: #6b7280;
  font-size: 13px;
  line-height: 1.6;
}

.resume-item-side {
  display: grid;
  flex-shrink: 0;
  gap: 8px;
  color: #9ca3af;
  font-size: 12px;
  text-align: right;
}

.resume-item-side a {
  display: inline-flex;
  align-items: center;
  justify-content: flex-end;
  gap: 4px;
  color: #2563eb;
}

@media (max-width: 760px) {
  .profile-header,
  .profile-overview {
    align-items: flex-start;
    flex-direction: column;
  }

  .profile-fields {
    width: 100%;
    justify-content: space-between;
    gap: 16px;
  }

  .resume-actions {
    grid-template-columns: 1fr;
  }

  .section-heading,
  .resume-list-heading {
    align-items: flex-start;
    flex-direction: column;
  }

  .resume-item {
    grid-template-columns: 36px minmax(0, 1fr);
  }

  .resume-item-side {
    grid-column: 2;
    width: auto;
    display: flex;
    align-items: center;
    justify-content: space-between;
    text-align: left;
  }

  .resume-item-side a {
    justify-content: flex-start;
  }

  .application-item {
    grid-template-columns: 1fr;
  }

  .application-item-actions {
    width: 100%;
    justify-content: flex-end;
  }
}
</style>
