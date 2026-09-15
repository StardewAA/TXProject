<script lang="ts" setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';

import { IconifyIcon } from '@vben/icons';
import { useUserStore } from '@vben/stores';

import { message, Pagination } from 'ant-design-vue';

import {
  getPortalRecruitApplication,
  getPortalRecruitApplicationPage,
  type HrmPortalRecruitApi,
  withdrawPortalRecruitApplication,
} from '#/api/hrm/portal/recruit';
import {
  getPortalResumeList,
  type HrmPortalResumeApi,
  uploadPortalResume,
} from '#/api/hrm/portal/resume';
import { getHrmRecruitFilePreviewUrl } from '#/api/hrm/portal/recruit/file-url';
import { uploadFile } from '#/api/infra/file';
import { requestClient } from '#/api/request';

const props = defineProps<{
  embedded?: boolean;
  section?: 'resume' | 'user';
}>();
const MAX_RESUME_SIZE = 10 * 1024 * 1024;
const ACCEPTED_RESUME_EXTENSIONS = ['pdf', 'doc', 'docx'];

const router = useRouter();
const userStore = useUserStore();
const resumeLoading = ref(false);
const applicationLoading = ref(false);
const uploadSubmitting = ref(false);
const resumes = ref<HrmPortalResumeApi.Resume[]>([]);
const applications = ref<HrmPortalRecruitApi.Application[]>([]);
const applicationTotal = ref(0);
const uploadInput = ref<HTMLInputElement>();
const selectedUploadFile = ref<File>();
const selectedApplication = ref<HrmPortalRecruitApi.Application>();
const applicationModalOpen = ref(false);
const resumeSort = ref<'latest' | 'oldest'>('latest');
const uploadForm = reactive({ title: '', remark: '' });
const applicationPage = reactive({ pageNo: 1, pageSize: 6 });
const showResumePanel = computed(() => props.section !== 'user');
const showApplicationPanel = computed(() => props.section !== 'resume');
const applicationStages = [
  { key: 'screening', label: '初选通过' },
  { key: 'interview', label: '安排面试' },
  { key: 'reinterview', label: '安排复试' },
  { key: 'offer', label: '发放录用通知' },
  { key: 'onboard', label: '转为员工' },
] as const;
const applicationStatusLabels: Record<string, string> = {
  ELIMINATED: '已淘汰',
  INTERVIEWING: '面试中',
  OFFER_APPROVING: '录用审批中',
  OFFER_SENT: '已发放录用通知',
  ONBOARDED: '已入职',
  PASSED: '面试通过',
  PENDING_ONBOARD: '待入职',
  SCREENING: '筛选中',
  SUBMITTED: '已投递',
  WITHDRAWN: '已撤回',
};
const offerStatusLabels: Record<string, string> = {
  APPROVED: '审批通过',
  CANCELED: '已取消',
  REJECTED: '审批未通过',
  SENT: '已发放',
  WAITING: '等待处理',
};

const careerProfile = computed<Record<string, any>>(() => {
  const resume = resumes.value.find((item) => item.title === '求职资料（系统）');
  try {
    return resume?.content ? JSON.parse(resume.content) : {};
  } catch {
    return {};
  }
});
const profileName = computed(() => careerProfile.value.name || userStore.userInfo?.nickname || '求职者');
const navUserName = computed(() => {
  const name =
    careerProfile.value.name ||
    userStore.userInfo?.nickname ||
    userStore.userInfo?.username ||
    '用户';
  return /^1\d{10}$/.test(name) ? `${name.slice(0, 3)}****${name.slice(-4)}` : name;
});
const profileMeta = computed(() => [careerProfile.value.education, careerProfile.value.school, careerProfile.value.major].filter(Boolean).join(' · ') || '求职资料待完善');
const profileAvatarUrl = computed(() => getHrmRecruitFilePreviewUrl(careerProfile.value.avatar));

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

function getResumeFileUrl(fileUrl?: string) {
  if (!fileUrl) return '';
  return fileUrl.includes('/infra/file/') && fileUrl.includes('/preview/')
    ? fileUrl.replace('/preview/', '/get/')
    : fileUrl;
}

async function handleResumePreview(fileUrl?: string) {
  if (!fileUrl) return;

  const previewWindow = window.open('', '_blank');
  if (!previewWindow) {
    message.warning('请允许打开新窗口后重试');
    return;
  }

  try {
    const blob = await requestClient.download<Blob>(getResumeFileUrl(fileUrl));
    if (!(blob instanceof Blob)) {
      throw new TypeError('简历文件响应无效');
    }
    const objectUrl = URL.createObjectURL(blob);
    previewWindow.location.href = objectUrl;
    window.setTimeout(() => URL.revokeObjectURL(objectUrl), 60_000);
  } catch (error) {
    previewWindow.close();
    console.warn('[PortalProfileRecruit] 预览简历失败', error);
    message.error('简历预览失败，请稍后重试');
  }
}

function getApplicationStage(application: HrmPortalRecruitApi.Application) {
  const status = application.status;
  if (status === 'ONBOARDED') return 4;
  if (['OFFER_APPROVING', 'OFFER_SENT', 'PENDING_ONBOARD'].includes(status)) {
    return 3;
  }
  if (status === 'PASSED') return 2;
  if (status === 'INTERVIEWING') {
    const interviewStage = Math.max(
      ...(application.interviews || []).map((interview) => interview.stageNumber || 1),
    );
    return interviewStage >= 2 ? 2 : 1;
  }
  if (status === 'ELIMINATED' || status === 'WITHDRAWN') {
    const interviewCount = application.interviews?.length || 0;
    return Math.min(interviewCount, 2);
  }
  return status === 'SUBMITTED' ? -1 : 0;
}

function isApplicationTerminal(application: HrmPortalRecruitApi.Application) {
  return ['ELIMINATED', 'WITHDRAWN'].includes(application.status);
}

function getApplicationStageNote(application: HrmPortalRecruitApi.Application) {
  return getApplicationStatusLabel(application);
}

function getApplicationStatusLabel(application: HrmPortalRecruitApi.Application) {
  const label = application.statusName?.trim();
  if (label) return label.replace(/\bOffer\b/gi, '录用通知');
  return applicationStatusLabels[application.status] || '招聘方处理中';
}

function getOfferStatusLabel(status?: string) {
  return offerStatusLabels[status || ''] || '处理中';
}

function validateResumeFile(file: File) {
  const extension = file.name.split('.').pop()?.toLowerCase();
  if (!extension || !ACCEPTED_RESUME_EXTENSIONS.includes(extension)) {
    message.warning('仅支持常见文档格式的简历');
    return false;
  }
  if (file.size > MAX_RESUME_SIZE) {
    message.warning('简历文件不能超过 10 兆');
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
    console.warn('[PortalProfileRecruit] 加载简历失败', error);
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
    console.warn('[PortalProfileRecruit] 加载应聘记录失败', error);
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
  <div class="portal-page recruit-workspace-page">
    <nav v-if="!embedded" class="recruit-subnav" aria-label="我要找工作导航">
      <div class="recruit-nav-links">
        <button type="button" @click="router.push('/portal/recruit')">首页</button>
        <button type="button" @click="router.push({ path: '/portal/recruit', query: { section: 'jobs' } })">职位</button>
        <button type="button" @click="router.push({ path: '/portal/recruit', query: { section: 'companies' } })">公司</button>
      </div>
      <div class="recruit-nav-account">
        <button type="button"><IconifyIcon icon="lucide:bell" /> 消息</button>
        <button class="active" type="button" @click="router.push({ name: 'PortalRecruit', query: { section: 'resume' } })">在线简历</button>
        <button class="recruit-account-button active" type="button" aria-label="我的应聘中心">
          <span class="recruit-account-name">{{ navUserName }}</span>
        </button>
      </div>
    </nav>

    <section class="profile-summary">
      <div class="profile-avatar"><img v-if="profileAvatarUrl" :src="profileAvatarUrl" alt="个人头像" /><IconifyIcon v-else icon="lucide:user-round" /></div>
      <div class="profile-identity"><p class="section-eyebrow">我的职业资料</p><h1>{{ profileName }}</h1><p>{{ profileMeta }}</p><span>{{ careerProfile.employmentStatus || '求职状态待确认' }}</span></div>
      <div class="profile-expectation"><small>期望职位</small><strong>{{ careerProfile.postTypeName || '暂未设置' }}</strong><p>{{ careerProfile.city || '工作城市待确认' }}</p></div>
       <a-button @click="router.push({ name: 'PortalRecruit', query: { section: 'resume' } })">在线简历</a-button>
    </section>

    <section class="recruit-workspace-hero">
      <div><p class="section-eyebrow">应聘中心</p><h2>我的招聘进度</h2><p>简历、岗位投递和招聘进度，在这里集中管理。</p></div>
      <div class="recruit-hero-stats">
        <div><strong>{{ applicationTotal }}</strong><span>次投递</span></div>
      </div>
    </section>

    <section v-if="showResumePanel" class="recruit-content-grid">
      <div class="recruit-main-column">
        <section class="recruit-panel">
          <div class="section-heading">
            <div>
              <p class="section-eyebrow">简历资料库</p>
              <h2>我的简历</h2>
              <p>上传简历附件，投递岗位时可直接使用。</p>
            </div>
            <a-select v-model:value="resumeSort" size="small" class="resume-sort-select" popup-class-name="recruit-resume-sort-dropdown">
              <a-select-option value="latest">最新创建</a-select-option>
              <a-select-option value="oldest">最早创建</a-select-option>
            </a-select>
          </div>

          <div class="resume-upload-box">
            <div class="upload-title">
              <span><IconifyIcon icon="lucide:cloud-upload" /></span>
              <div><strong>上传简历附件</strong><small>支持常见文档格式，单个不超过 10 兆</small></div>
            </div>
            <div class="upload-form-row">
              <a-input v-model:value="uploadForm.title" placeholder="给简历起个标题，例如：项目经理简历" />
              <a-input v-model:value="uploadForm.remark" placeholder="备注（可选）" />
            </div>
            <label class="file-dropzone" :class="{ 'has-file': selectedUploadFile }">
              <input ref="uploadInput" type="file" accept=".pdf,.doc,.docx" @change="handleFileChange" />
              <IconifyIcon :icon="selectedUploadFile ? 'lucide:file-check-2' : 'lucide:file-plus-2'" />
              <strong>{{ selectedUploadFile?.name || '选择简历文件' }}</strong>
              <small>{{ selectedUploadFile ? '已选择，可点击重新选择' : '点击选择本地文件' }}</small>
            </label>
            <div class="upload-actions">
              <a-button v-if="selectedUploadFile" type="link" @click="clearSelectedFile">重新选择</a-button>
              <a-button type="primary" :loading="uploadSubmitting" @click="handleUploadResume">上传并保存</a-button>
            </div>
          </div>

          <div v-if="resumeLoading" class="loading-list"><a-skeleton v-for="item in 3" :key="item" active :paragraph="{ rows: 2 }" /></div>
          <div v-else-if="!visibleResumes.length" class="empty-state"><IconifyIcon icon="lucide:file-text" /><strong>暂无附件简历</strong><p>上传一份简历，投递岗位时即可直接使用</p></div>
          <div v-else class="resume-list">
            <article v-for="resume in visibleResumes" :key="resume.id" class="resume-item">
              <span class="resume-icon"><IconifyIcon icon="lucide:file-text" /></span>
              <div class="resume-copy"><div><strong>{{ resume.title }}</strong><a-tag color="blue">附件简历</a-tag></div><p v-if="resume.fileName">{{ resume.fileName }}</p><p v-if="resume.remark">{{ resume.remark }}</p></div>
              <div class="resume-side"><span>{{ formatDate(resume.createTime) }}</span><a v-if="resume.fileUrl" href="#" @click.prevent="handleResumePreview(resume.fileUrl)">在线预览</a></div>
            </article>
          </div>
        </section>
      </div>
</section>

    <section v-if="showApplicationPanel" class="recruit-panel application-panel">
      <div class="section-heading"><div><p class="section-eyebrow">应聘记录</p><h2>我的应聘记录</h2><p>跟进每一次投递、面试安排和录用通知状态。</p></div><span class="history-total">共 {{ applicationTotal }} 条记录</span></div>
      <div v-if="applicationLoading" class="loading-list"><a-skeleton v-for="item in 3" :key="item" active :paragraph="{ rows: 2 }" /></div>
      <div v-else-if="!applications.length" class="empty-state"><IconifyIcon icon="lucide:clipboard-list" /><strong>暂无应聘记录</strong><p>去岗位机会中挑选一份适合你的工作吧</p><a-button type="link" @click="router.push('/portal/recruit')">去浏览岗位</a-button></div>
      <div v-else class="application-list">
        <article v-for="application in applications" :key="application.id" class="application-card">
          <div class="application-card-header">
            <div class="application-item-main">
              <div class="application-title"><strong>{{ application.postName || '招聘职位' }}</strong><a-tag :color="application.status === 'ELIMINATED' ? 'error' : application.status === 'WITHDRAWN' ? 'default' : 'processing'">{{ getApplicationStatusLabel(application) }}</a-tag></div>
              <p>投递于 {{ formatDate(application.createTime) }}</p>
            </div>
            <div class="application-actions"><a-button type="link" @click="showApplication(application)">查看详情</a-button><a-button v-if="['SUBMITTED', 'SCREENING', 'INTERVIEWING'].includes(application.status)" type="link" danger @click="withdrawApplication(application)">撤回应聘</a-button></div>
          </div>
          <div class="application-progress" :class="{ terminal: isApplicationTerminal(application) }">
            <div v-for="(stage, stageIndex) in applicationStages" :key="stage.key" class="application-progress-step" :class="{ reached: stageIndex <= getApplicationStage(application), current: stageIndex === getApplicationStage(application), eliminated: isApplicationTerminal(application) && stageIndex === getApplicationStage(application) }">
              <span class="application-progress-marker">{{ String(stageIndex + 1).padStart(2, '0') }}</span>
              <div><strong>{{ stage.label }}</strong><small v-if="stageIndex === getApplicationStage(application)">{{ getApplicationStageNote(application) }}</small></div>
            </div>
          </div>
        </article>
      </div>
      <Pagination v-if="applicationTotal" :current="applicationPage.pageNo" :page-size="applicationPage.pageSize" :total="applicationTotal" class="application-pagination" @change="handleApplicationPageChange" />
    </section>

    <a-modal v-model:open="applicationModalOpen" title="应聘进度" :footer="null" width="680px" wrap-class-name="recruit-application-modal">
      <template v-if="selectedApplication">
        <a-descriptions :column="2" bordered><a-descriptions-item label="职位">{{ selectedApplication.postName }}</a-descriptions-item><a-descriptions-item label="当前状态">{{ getApplicationStatusLabel(selectedApplication) }}</a-descriptions-item><a-descriptions-item label="投递时间">{{ formatDate(selectedApplication.createTime) }}</a-descriptions-item><a-descriptions-item label="简历">{{ selectedApplication.resumeFileName || '-' }}</a-descriptions-item></a-descriptions>
        <a-timeline class="timeline"><a-timeline-item color="green">已投递：{{ formatDate(selectedApplication.createTime) }}</a-timeline-item><a-timeline-item v-for="interview in selectedApplication.interviews || []" :key="interview.id" color="blue">第 {{ interview.stageNumber || 1 }} 轮面试：{{ formatDate(interview.interviewTime) }}，{{ interview.address || '线上' }}</a-timeline-item><a-timeline-item v-if="selectedApplication.offer" color="green">录用通知：{{ getOfferStatusLabel(selectedApplication.offer.approvalStatus) }}</a-timeline-item></a-timeline>
      </template>
    </a-modal>
  </div>
</template>

<style scoped>
.recruit-workspace-page {
  display: grid;
  grid-template-columns: minmax(16px, 1fr) minmax(0, 1180px) minmax(16px, 1fr);
  gap: 20px;
  --recruit-accent: #e87522;
}
.recruit-workspace-page > * { grid-column: 2; min-width: 0; }
.recruit-workspace-page > .recruit-subnav { grid-column: 1 / -1; }
.recruit-subnav { display:flex; align-items:center; gap:26px; min-height:54px; padding:0 24px; border-block:1px solid rgb(238 225 211 / 90%); background:rgb(255 255 255 / 82%); box-shadow:0 8px 20px rgb(183 92 24 / 7%); backdrop-filter:blur(16px) saturate(115%); -webkit-backdrop-filter:blur(16px) saturate(115%); }
 .recruit-nav-links,.recruit-nav-account{display:flex;align-items:center}.recruit-nav-links{gap:24px}.recruit-nav-account{margin-left:auto;gap:22px}.recruit-subnav button{position:relative;display:inline-flex;height:52px;align-items:center;gap:7px;padding:0 4px;border:0;background:transparent;color:#875222;cursor:pointer;font-size:15px}.recruit-subnav button:hover,.recruit-subnav button.active{color:var(--recruit-accent)}.recruit-account-name{max-width:120px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
 .profile-summary{display:grid;grid-template-columns:auto minmax(0,1fr) minmax(180px,auto) auto;align-items:center;gap:20px;padding:24px 28px;border:1px solid #f0dac1;border-radius:12px;background:#fffdfa;box-shadow:0 8px 24px rgb(183 92 24 / 8%)}.profile-avatar{display:grid;width:68px;height:68px;overflow:hidden;place-items:center;border-radius:50%;background:#fff0df;color:#d86616;font-size:36px}.profile-avatar img{width:100%;height:100%;object-fit:cover}.profile-identity h1{margin:0;color:#4a2916;font-size:25px}.profile-identity>p:not(.section-eyebrow){margin:5px 0;color:#806956;font-size:14px}.profile-identity>span{display:inline-block;padding:5px 10px;border-radius:4px;background:#fff0df;color:#c85e18;font-size:13px}.profile-expectation{padding-left:24px;border-left:1px solid #f0dac1}.profile-expectation small{display:block;color:#a48268;font-size:13px}.profile-expectation strong{display:block;margin-top:7px;color:#5d341d;font-size:17px}.profile-expectation p{margin:5px 0 0;color:#806956;font-size:14px}
.recruit-workspace-hero { display: flex; align-items: center; justify-content: space-between; gap: 24px; padding: 30px 34px; border: 1px solid #f2d4ad; background: linear-gradient(115deg, #fff6e8, #fffdf9); }
 .section-eyebrow { margin: 0 0 7px; color: #d86616; font-size: 13px; font-weight: 700; letter-spacing: .08em; }
 .recruit-workspace-hero h2 { margin: 0; color: #0f172a; font-size: 25px; }
 .recruit-workspace-hero p:last-child { margin: 8px 0 0; color: #64748b; font-size: 15px; }
.recruit-hero-stats { display: flex; gap: 28px; }
.recruit-hero-stats div { display: grid; gap: 4px; min-width: 60px; text-align: center; }
.recruit-hero-stats strong { color: #d86616; font-size: 25px; }
 .recruit-hero-stats span { color: #64748b; font-size: 14px; }
.recruit-content-grid { display: block; }
.recruit-panel { padding: 24px; border: 1px solid #f0dac1; background: #fffdfa; }
.section-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; margin-bottom: 20px; }
 .section-heading h2 { margin: 0; color: #0f172a; font-size: 23px; }
 .section-heading p:last-child { margin: 7px 0 0; color: #94a3b8; font-size: 14px; }
.resume-sort-select { width: 104px; }
.recruit-workspace-page :deep(.ant-input),
.recruit-workspace-page :deep(.ant-input-affix-wrapper),
.recruit-workspace-page :deep(.ant-input-number),
.recruit-workspace-page :deep(.ant-select-selector) {
  border-color: #cbd5e1 !important;
  background: #fff !important;
  color: #0f172a !important;
  font-size: 14px;
}
.recruit-workspace-page :deep(.ant-input::placeholder),
.recruit-workspace-page :deep(.ant-select-selection-placeholder) {
  color: #94a3b8 !important;
}
.recruit-workspace-page :deep(.ant-input:hover),
.recruit-workspace-page :deep(.ant-input-affix-wrapper:hover),
.recruit-workspace-page :deep(.ant-select:hover .ant-select-selector),
.recruit-workspace-page :deep(.ant-input:focus),
.recruit-workspace-page :deep(.ant-input-affix-wrapper-focused),
.recruit-workspace-page :deep(.ant-select-focused .ant-select-selector) {
  border-color: #2563eb !important;
  background: #fff !important;
  box-shadow: 0 0 0 2px rgb(37 99 235 / 12%) !important;
}
.recruit-workspace-page :deep(.ant-select-selection-item),
.recruit-workspace-page :deep(.ant-select-arrow) {
  color: #1d4ed8 !important;
}
.recruit-workspace-page :deep(.ant-select-selection-search-input) {
  color: #0f172a !important;
}
.recruit-workspace-page :deep(.ant-tag) {
  border-color: #cbd5e1 !important;
  background: #fff !important;
  color: #0f172a !important;
}
.recruit-workspace-page :deep(.ant-select-dropdown),
.recruit-workspace-page :deep(.ant-select-item) {
  background: #fff !important;
  color: #0f172a !important;
}
.recruit-workspace-page :deep(.ant-select-item-option-active),
.recruit-workspace-page :deep(.ant-select-item-option-selected) {
  background: #eff6ff !important;
  color: #1d4ed8 !important;
}
.recruit-workspace-page :deep(.ant-tag-blue) {
  border-color: #bfdbfe !important;
  background: #eff6ff !important;
  color: #1d4ed8 !important;
}
.recruit-workspace-page :deep(.ant-tag-processing) {
  border-color: #bfdbfe !important;
  background: #eff6ff !important;
  color: #1d4ed8 !important;
}
.recruit-workspace-page :deep(.ant-tag-error) {
  border-color: #fecaca !important;
  background: #fef2f2 !important;
  color: #b91c1c !important;
}
:global(.recruit-application-modal .ant-modal-content),
:global(.recruit-application-modal .ant-modal-header),
:global(.recruit-application-modal .ant-modal-body),
:global(.recruit-application-modal .ant-descriptions-item-label),
:global(.recruit-application-modal .ant-descriptions-item-content) {
  background: #fff !important;
  color: #0f172a !important;
}
:global(.recruit-resume-sort-dropdown.ant-select-dropdown),
:global(.recruit-resume-sort-dropdown .ant-select-item) {
  background: #fff !important;
  color: #0f172a !important;
}
:global(.recruit-resume-sort-dropdown .ant-select-item-option-active),
:global(.recruit-resume-sort-dropdown .ant-select-item-option-selected) {
  background: #eff6ff !important;
  color: #1d4ed8 !important;
}
.recruit-workspace-page :deep(.ant-descriptions-item-label),
.recruit-workspace-page :deep(.ant-descriptions-item-content) {
  background: #fff !important;
  color: #0f172a !important;
}
.resume-upload-box { display: grid; gap: 12px; margin-bottom: 20px; padding: 16px; border: 1px solid #f2d4ad; background: #fff8ee; }
.upload-title { display: flex; align-items: center; gap: 10px; }
.upload-title > span { display: grid; width: 34px; height: 34px; place-items: center; color: #d86616; background: #fff0df; font-size: 17px; }
.upload-title div { display: grid; gap: 3px; }
 .upload-title strong { color: #334155; font-size: 16px; }
 .upload-title small { color: #94a3b8; font-size: 14px; }
.upload-form-row { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
.file-dropzone { display: grid; min-height: 82px; place-items: center; align-content: center; gap: 4px; border: 1px dashed #efb36a; color: #d86616; background: #fff4e5; cursor: pointer; text-align: center; }
.file-dropzone:hover, .file-dropzone.has-file { border-color: #d86616; background: #fff0df; }
.file-dropzone input { display: none; }
 .file-dropzone strong { max-width: 90%; overflow: hidden; color: #334155; font-size: 15px; text-overflow: ellipsis; white-space: nowrap; }
 .file-dropzone small { color: #64748b; font-size: 14px; }
.upload-actions { display: flex; justify-content: flex-end; gap: 8px; }
.loading-list { display: grid; gap: 12px; }
.empty-state { display: grid; min-height: 150px; place-items: center; align-content: center; gap: 7px; color: #94a3b8; text-align: center; }
.empty-state > svg { color: #bfdbfe; font-size: 28px; }
 .empty-state strong { color: #64748b; font-size: 16px; }
 .empty-state p { margin: 0; font-size: 14px; }
.resume-list, .application-list { display: grid; gap: 12px; }
.resume-item { display: grid; grid-template-columns: 34px minmax(0, 1fr) auto; align-items: center; gap: 11px; padding: 13px; border: 1px solid #eef2f7; }
.resume-icon { display: grid; width: 34px; height: 34px; place-items: center; color: #2563eb; background: #eff6ff; }
.resume-copy { min-width: 0; }
.resume-copy > div, .application-title { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; }
 .resume-copy strong, .application-title strong { color: #334155; font-size: 15px; }
 .resume-copy p, .application-item-main p { margin: 5px 0 0; color: #94a3b8; font-size: 14px; }
 .resume-side { display: grid; gap: 6px; color: #94a3b8; font-size: 13px; text-align: right; }
.resume-side a { color: #2563eb; }
.application-panel { margin-top: 0; }
 .history-total { color: #94a3b8; font-size: 14px; }
.application-card { display: grid; gap: 18px; padding: 18px; border: 1px solid #dbe5f0; background: #fff; box-shadow: 0 4px 14px rgb(15 23 42 / 4%); }
.application-card-header { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; }
.application-item-main { min-width: 0; }
.application-progress { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: 8px; }
.application-progress-step { position: relative; display: grid; gap: 8px; min-width: 0; color: #94a3b8; }
.application-progress-step::before { position: absolute; top: 14px; right: calc(50% + 16px); left: calc(-50% + 16px); height: 2px; background: #e2e8f0; content: ''; }
.application-progress-step:first-child::before { display: none; }
 .application-progress-marker { position: relative; z-index: 1; display: grid; width: 32px; height: 32px; place-items: center; border: 1px solid #cbd5e1; border-radius: 50%; background: #fff; color: #94a3b8; font-size: 13px; }
.application-progress-step.reached { color: #d86616; }
.application-progress-step.reached::before { background: #efb36a; }
.application-progress-step.reached .application-progress-marker { border-color: #ef8b35; background: #fff0df; color: #d86616; }
.application-progress-step.current .application-progress-marker { box-shadow: 0 0 0 4px rgb(216 102 22 / 12%); }
.application-progress-step.eliminated { color: #b91c1c; }
.application-progress-step.eliminated .application-progress-marker { border-color: #f87171; background: #fef2f2; color: #b91c1c; }
.application-progress-step div { display: grid; gap: 3px; }
 .application-progress-step strong { color: inherit; font-size: 14px; }
 .application-progress-step small { color: inherit; font-size: 13px; }
 .application-actions { display: flex; gap: 3px; }
 .application-actions :deep(.ant-btn) { font-size: 14px; }
 :global(.recruit-application-modal .ant-descriptions-item-label), :global(.recruit-application-modal .ant-descriptions-item-content), :global(.recruit-application-modal .ant-timeline-item-content) { font-size: 14px; }
.application-pagination { margin-top: 18px; text-align: center; }
.timeline { margin-top: 22px; }
@media (max-width: 900px) { .recruit-content-grid { grid-template-columns: 1fr; } }
@media (max-width: 640px) { .recruit-workspace-hero { align-items: flex-start; flex-direction: column; padding: 24px 20px; } .upload-form-row { grid-template-columns: 1fr; } .recruit-panel { padding: 20px 16px; } .resume-item { grid-template-columns: 34px minmax(0, 1fr); } .resume-side { grid-column: 2; display: flex; justify-content: space-between; text-align: left; } .application-card-header { flex-direction: column; } .application-actions { width: 100%; justify-content: flex-end; } .application-progress { grid-template-columns: repeat(5, minmax(104px, 1fr)); overflow-x: auto; padding-bottom: 4px; } }
@media(max-width:700px){.profile-summary{grid-template-columns:auto 1fr}.profile-expectation{grid-column:1/-1;padding:14px 0 0;border-top:1px solid #e7ecef;border-left:0}}
.recruit-workspace-page { color: #5d4636; }
.recruit-workspace-page .profile-summary, .recruit-workspace-page .recruit-panel, .recruit-workspace-page .application-card, .recruit-workspace-page .resume-item { border-color: #f4e5d7; background: #fff; box-shadow: 0 8px 22px rgb(183 92 24 / 4%); }
.recruit-workspace-page .recruit-workspace-hero { background: linear-gradient(120deg, #fff 0%, #fffdfb 72%, #fff4e6 100%); }
.recruit-workspace-page .resume-upload-box, .recruit-workspace-page .file-dropzone { border-color: #f1c897; background: #fff8ef; }
.recruit-workspace-page .file-dropzone:hover, .recruit-workspace-page .file-dropzone.has-file { border-color: #e87522; background: #fff0df; }
.recruit-workspace-page .application-progress-step.reached { color: #d86616; }
.recruit-workspace-page :deep(.ant-btn-primary) { border-color: #e87522; background: linear-gradient(135deg, #ef9a4a, #d86616); }
</style>
