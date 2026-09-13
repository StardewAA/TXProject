<script lang="ts" setup>
import { computed, reactive, ref, watch } from 'vue';

import { message } from 'ant-design-vue';
import { IconifyIcon } from '@vben/icons';
import { useAccessStore, useUserStore } from '@vben/stores';

import { uploadFile } from '#/api/infra/file';
import {
  getPortalResumeList,
  type HrmPortalResumeApi,
} from '#/api/hrm/portal/resume';
import {
  createPortalRecruitApplication,
  type HrmPortalRecruitApi,
} from '#/api/hrm/portal/recruit';
import { getRecruitChatConversationList } from '#/api/hrm/portal/recruit/chat';
import { getUserProfile } from '#/api/system/user/profile';

const props = defineProps<{
  open: boolean;
  post?: HrmPortalRecruitApi.Post;
}>();

const emit = defineEmits<{
  'update:open': [value: boolean];
  submitted: [data: HrmPortalRecruitApi.ApplyReq];
}>();

const modalOpen = computed({
  get: () => props.open,
  set: (value: boolean) => emit('update:open', value),
});

const accessStore = useAccessStore();
const userStore = useUserStore();
const applySubmitting = ref(false);
const applyFormRef = ref();
const savedResumes = ref<HrmPortalResumeApi.Resume[]>([]);
const resumeLoading = ref(false);
const userProfile = ref<{ mobile?: string; phone?: string }>();
const applyForm = reactive<HrmPortalRecruitApi.ApplyReq>({
  postId: 0,
  name: '',
  mobile: '',
  sex: 1,
  education: 5,
  resumeFileName: '',
  resumeFileUrl: '',
});

const savedResumeOptions = computed(() =>
  savedResumes.value.filter(
    (resume) => resume.resumeType === 1 && resume.fileUrl,
  ),
);

const resumePickerMode = computed(() => savedResumeOptions.value.length > 1);
const modalTitle = computed(() =>
  resumePickerMode.value ? '选择投递简历' : '在线投递',
);

const educationValueMap: Record<string, number> = {
  初中及以下: 3,
  中专: 3,
  中专中技: 3,
  高中: 3,
  大专: 4,
  本科: 5,
  硕士: 6,
  博士: 7,
};

const mobilePattern = /^(\+?0?\d{2,4}-?)?\d{6,11}$/;

function readProfile(resumes: HrmPortalResumeApi.Resume[]) {
  const profileResume = resumes.find(
    (resume) => resume.title === '求职资料（系统）',
  );
  if (!profileResume?.content) return {};
  try {
    const profile = JSON.parse(profileResume.content);
    return profile && typeof profile === 'object' ? profile : {};
  } catch {
    return {};
  }
}

function calculateAge(birthMonth?: unknown) {
  if (typeof birthMonth !== 'string' || !birthMonth) return undefined;
  const birthYear = Number(birthMonth.slice(0, 4));
  const birthMonthNumber = Number(birthMonth.slice(5, 7));
  if (!birthYear || !birthMonthNumber) return undefined;
  const now = new Date();
  return Math.max(
    0,
    now.getFullYear() - birthYear -
      (now.getMonth() + 1 < birthMonthNumber ? 1 : 0),
  );
}

function estimateWorkTime(start?: unknown) {
  if (typeof start !== 'string' || !start) return undefined;
  const startYear = Number(start.slice(0, 4));
  if (!startYear) return undefined;
  return Math.max(0, new Date().getFullYear() - startYear);
}

function getUserValue(name: string) {
  const user = (userProfile.value || userStore.userInfo) as null | Record<string, unknown>;
  const value = user?.[name];
  return typeof value === 'string' ? value.trim() : value;
}

function normalizeMobile(value: unknown) {
  return typeof value === 'string' ? value.trim().replace(/\s+/g, '') : '';
}

function isValidMobile(value: unknown) {
  const mobile = normalizeMobile(value);
  return Boolean(mobile && mobilePattern.test(mobile));
}

function fillProfileFields(resumes: HrmPortalResumeApi.Resume[]) {
  const profile = readProfile(resumes);
  const profileEducation = String(profile.education || '').replaceAll(
    /[\s/]+/g,
    '',
  );
  const mobileCandidates = [
    profile.mobile,
    getUserValue('mobile'),
    getUserValue('phone'),
  ];
  const mobile = mobileCandidates.map(normalizeMobile).find(isValidMobile);
  Object.assign(applyForm, {
    name: profile.name || getUserValue('nickname') || '',
    mobile: mobile || '',
    sex: profile.gender === 2 ? 2 : 1,
    age: calculateAge(profile.birthMonth),
    email: profile.email || getUserValue('email') || '',
    workTime: profile.workTime ?? estimateWorkTime(profile.jobStart || profile.workStart),
    education: educationValueMap[profileEducation] || 5,
    graduateSchool: profile.school || '',
    latestWorkPlace: profile.company || '',
  });
}

async function prepareForm() {
  Object.assign(applyForm, {
    postId: props.post?.id || 0,
    name: '',
    mobile: '',
    sex: 1,
    education: 5,
    email: '',
    graduateSchool: '',
    latestWorkPlace: '',
    resumeFileName: '',
    resumeFileUrl: '',
    resumeId: undefined,
    resumeRemark: '',
    sourceRemark: '',
  });

  if (!accessStore.accessToken) {
    savedResumes.value = [];
    return;
  }

  resumeLoading.value = true;
  try {
    const [resumeResult, profileResult] = await Promise.allSettled([
      getPortalResumeList(),
      getUserProfile(),
    ]);
    userProfile.value =
      profileResult.status === 'fulfilled' ? profileResult.value : undefined;
    if (resumeResult.status === 'rejected') throw resumeResult.reason;
    savedResumes.value = resumeResult.value || [];
    fillProfileFields(savedResumes.value);
    const latestResume = savedResumeOptions.value[0];
    if (latestResume) {
      applyForm.resumeId = latestResume.id;
      applyForm.resumeFileName = latestResume.fileName || latestResume.title;
      applyForm.resumeFileUrl = latestResume.fileUrl || '';
    }
  } catch (error) {
    savedResumes.value = [];
    console.warn('[PortalRecruit] 加载已保存简历失败', error);
  } finally {
    resumeLoading.value = false;
  }
}

function handleSavedResumeChange(value?: string) {
  if (!value) {
    applyForm.resumeId = undefined;
    applyForm.resumeFileName = '';
    applyForm.resumeFileUrl = '';
    return;
  }
  const resume = savedResumeOptions.value.find((item) => item.fileUrl === value);
  if (resume) {
    applyForm.resumeId = resume.id;
    applyForm.resumeFileName = resume.fileName || resume.title;
    applyForm.resumeFileUrl = resume.fileUrl || '';
  }
}

async function handleResumeUpload(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0];
  if (!file) return;
  const extension = file.name.split('.').pop()?.toLowerCase();
  if (!extension || !['pdf', 'doc', 'docx'].includes(extension)) {
    message.warning('仅支持 PDF、DOC、DOCX 格式的简历');
    return;
  }
  if (file.size > 10 * 1024 * 1024) {
    message.warning('简历文件不能超过 10MB');
    return;
  }
  const result = await uploadFile({ file, directory: 'hrm/recruit/resume' });
  applyForm.resumeFileName = file.name;
  applyForm.resumeFileUrl = result as string;
  applyForm.resumeId = undefined;
  message.success('简历上传成功');
}

async function ensureApplyQualification() {
  if (!accessStore.accessToken) {
    message.warning('请先登录后再投递');
    return false;
  }
  const postId = props.post?.id;
  if (!postId) {
    message.warning('岗位信息不完整');
    return false;
  }
  try {
    const conversations = (await getRecruitChatConversationList()) || [];
    const matched = conversations.find(
      (item) => item.postId === postId && !item.hrSide,
    );
    if (!matched) {
      message.warning('请先与招聘方沟通，待回复后才能投递');
      return false;
    }
    if (!matched.applyQualified) {
      message.warning('需等待招聘方回复后才能投递简历');
      return false;
    }
    return true;
  } catch (error: any) {
    message.error(error?.message || '校验投递资格失败');
    return false;
  }
}

async function submitApplication() {
  if (!savedResumeOptions.value.length) {
    await applyFormRef.value?.validate();
  }
  if (resumePickerMode.value && !applyForm.resumeId) {
    message.warning('请选择要投递的简历');
    return;
  }
  if (!applyForm.resumeFileUrl) {
    message.warning('请先上传简历文件');
    return;
  }
  applyForm.mobile = normalizeMobile(applyForm.mobile);
  if (!isValidMobile(applyForm.mobile)) {
    message.warning('请输入有效的联系手机号');
    return;
  }
  if (!(await ensureApplyQualification())) {
    return;
  }
  applySubmitting.value = true;
  try {
    await createPortalRecruitApplication(applyForm);
    emit('submitted', { ...applyForm });
    message.success('应聘投递成功，可在个人中心查看进度');
    modalOpen.value = false;
  } catch (error: any) {
    message.error(error?.message || '投递失败');
  } finally {
    applySubmitting.value = false;
  }
}

watch(
  () => props.open,
  (open) => {
    if (open) void prepareForm();
  },
);
</script>

<template>
  <a-modal
    v-model:open="modalOpen"
    :title="modalTitle"
    width="640px"
    :confirm-loading="applySubmitting"
    ok-text="确认投递"
    cancel-text="取消"
    wrap-class-name="recruit-apply-modal"
    @ok="submitApplication"
  >
    <div v-if="resumeLoading" class="resume-loading">
      <a-spin />
      <span>正在读取个人中心的简历…</span>
    </div>
    <div v-else-if="resumePickerMode" class="resume-picker">
      <div class="apply-intro">
        <IconifyIcon icon="lucide:files" />
        你有多份已保存简历，请选择投递到「{{ props.post?.postName || '当前岗位' }}」的简历
      </div>
      <button
        v-for="resume in savedResumeOptions"
        :key="resume.id"
        class="resume-option"
        :class="{ selected: applyForm.resumeId === resume.id }"
        type="button"
        @click="handleSavedResumeChange(resume.fileUrl)"
      >
        <span class="resume-option-icon"><IconifyIcon icon="lucide:file-text" /></span>
        <span class="resume-option-copy">
          <strong>{{ resume.title || '未命名简历' }}</strong>
          <small>{{ resume.fileName || '附件简历' }}</small>
        </span>
        <span class="resume-option-check"><IconifyIcon icon="lucide:check" /></span>
      </button>
      <div class="resume-contact-field">
        <label for="resume-contact-mobile">联系手机号</label>
        <a-input
          id="resume-contact-mobile"
          v-model:value="applyForm.mobile"
          placeholder="请输入招聘方可联系的手机号"
          inputmode="tel"
        />
        <span>用于接收招聘沟通，请确认号码准确。</span>
      </div>
      <p class="resume-picker-tip"><IconifyIcon icon="lucide:shield-check" />将使用你已保存的求职资料完成投递，无需重复填写。</p>
    </div>
    <div v-else>
      <div class="apply-intro">
        <IconifyIcon icon="lucide:send" />
        正在投递：{{ props.post?.postName || '当前岗位' }}
      </div>
      <a-form ref="applyFormRef" :model="applyForm" layout="vertical">
        <div class="form-grid">
          <a-form-item
            label="姓名"
            name="name"
            :rules="[{ required: true, message: '请输入姓名' }]"
          >
            <a-input v-model:value="applyForm.name" placeholder="请输入真实姓名" />
          </a-form-item>
          <a-form-item
            label="手机号"
            name="mobile"
            :rules="[{ required: true, message: '请输入手机号' }]"
          >
            <a-input v-model:value="applyForm.mobile" placeholder="用于接收招聘沟通" />
          </a-form-item>
          <a-form-item label="性别" name="sex">
            <a-radio-group v-model:value="applyForm.sex">
              <a-radio :value="1">男</a-radio>
              <a-radio :value="2">女</a-radio>
            </a-radio-group>
          </a-form-item>
          <a-form-item label="学历" name="education">
            <a-select v-model:value="applyForm.education">
              <a-select-option :value="4">大专</a-select-option>
              <a-select-option :value="5">本科</a-select-option>
              <a-select-option :value="6">硕士</a-select-option>
              <a-select-option :value="7">博士</a-select-option>
            </a-select>
          </a-form-item>
        </div>
        <a-form-item label="邮箱">
          <a-input v-model:value="applyForm.email" />
        </a-form-item>
        <div class="form-grid">
          <a-form-item label="毕业院校">
            <a-input v-model:value="applyForm.graduateSchool" />
          </a-form-item>
          <a-form-item label="最近工作单位">
            <a-input v-model:value="applyForm.latestWorkPlace" />
          </a-form-item>
        </div>
        <a-form-item label="简历文件" required>
          <a-select
            v-if="savedResumeOptions.length"
            class="saved-resume-select"
            :value="applyForm.resumeFileUrl"
            allow-clear
            placeholder="优先选择个人中心已保存的简历"
            @change="handleSavedResumeChange"
          >
            <a-select-option
              v-for="resume in savedResumeOptions"
              :key="resume.id"
              :value="resume.fileUrl"
            >
              {{ resume.title }}{{ resume.fileName ? `（${resume.fileName}）` : '' }}
            </a-select-option>
          </a-select>
          <label class="apply-upload">
            <IconifyIcon icon="lucide:cloud-upload" />
            <span>{{
              applyForm.resumeFileName ||
              '选择 PDF / DOC / DOCX 简历（不超过 10MB）'
            }}</span>
            <input
              type="file"
              accept=".pdf,.doc,.docx"
              @change="handleResumeUpload"
            />
          </label>
        </a-form-item>
        <a-form-item label="补充说明">
          <a-textarea v-model:value="applyForm.sourceRemark" :rows="3" />
        </a-form-item>
      </a-form>
    </div>
  </a-modal>
</template>

<style scoped>
.apply-intro {
  display: flex;
  align-items: center;
  gap: 7px;
  margin-bottom: 18px;
  padding: 10px 12px;
  color: #475569;
  background: #fff8ee;
  font-size: 13px;
}

.resume-loading {
  display: grid;
  min-height: 160px;
  place-items: center;
  align-content: center;
  gap: 12px;
  color: #9a765d;
  font-size: 13px;
}

.resume-picker {
  padding: 4px 0;
}

.resume-picker .apply-intro {
  margin-bottom: 14px;
  border: 1px solid #f1d8bd;
  background: #fff6ea;
  color: #9a5b2d;
  line-height: 1.6;
}

.resume-option {
  display: flex;
  width: 100%;
  align-items: center;
  gap: 12px;
  margin-top: 10px;
  padding: 14px;
  border: 1px solid #f0dfcd;
  border-radius: 8px;
  background: #fffdfa;
  color: #513c2d;
  cursor: pointer;
  text-align: left;
  transition: border-color .18s ease, background .18s ease, box-shadow .18s ease;
}

.resume-option:hover,
.resume-option.selected {
  border-color: #e87522;
  background: #fff4e6;
  box-shadow: 0 5px 14px rgb(183 92 24 / 10%);
}

.resume-option-icon {
  display: grid;
  width: 38px;
  height: 38px;
  flex: 0 0 auto;
  place-items: center;
  border-radius: 7px;
  background: #fff0df;
  color: #d86616;
  font-size: 19px;
}

.resume-option-copy {
  display: grid;
  min-width: 0;
  flex: 1;
  gap: 4px;
}

.resume-option-copy strong {
  overflow: hidden;
  color: #4a2916;
  font-size: 14px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.resume-option-copy small {
  overflow: hidden;
  color: #9a806d;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.resume-option-check {
  display: grid;
  width: 22px;
  height: 22px;
  flex: 0 0 auto;
  place-items: center;
  border: 1px solid #e6d4c3;
  border-radius: 50%;
  color: transparent;
  font-size: 13px;
}

.resume-option.selected .resume-option-check {
  border-color: #e87522;
  background: #e87522;
  color: #fff;
}

.resume-picker-tip {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 16px 2px 2px;
  color: #a2836e;
  font-size: 12px;
}

.resume-contact-field {
  display: grid;
  gap: 6px;
  margin-top: 18px;
}

.resume-contact-field label {
  color: #5f4432;
  font-size: 13px;
  font-weight: 600;
}

.resume-contact-field span {
  color: #a2836e;
  font-size: 12px;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.apply-upload {
  display: flex;
  align-items: center;
  gap: 9px;
  min-height: 48px;
  padding: 10px 14px;
  border: 1px dashed #efb36a;
  color: #c85e18;
  background: #fff0df;
  cursor: pointer;
  font-size: 13px;
}

.apply-upload span {
  overflow: hidden;
  color: #475569;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.apply-upload input {
  display: none;
}

:global(.recruit-apply-modal .ant-modal-content) {
  border: 1px solid #f0dfcd;
  border-radius: 10px;
  background: #fffdfa;
}

:global(.recruit-apply-modal .ant-modal-header) {
  background: #fffdfa;
}

:global(.recruit-apply-modal .ant-modal-title) {
  color: #4a2916;
  font-size: 18px;
}

:global(.recruit-apply-modal .ant-btn-primary) {
  border-color: #e87522;
  background: linear-gradient(135deg, #ef9a4a, #d86616);
}

:global(.recruit-apply-modal .ant-btn-primary:hover) {
  border-color: #c85e18;
  background: #c85e18;
}

@media (max-width: 640px) {
  .form-grid {
    grid-template-columns: 1fr;
    gap: 0;
  }

  .resume-picker-tip {
    align-items: flex-start;
  }
}
</style>
