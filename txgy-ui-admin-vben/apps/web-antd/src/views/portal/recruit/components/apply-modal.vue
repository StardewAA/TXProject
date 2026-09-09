<script lang="ts" setup>
import { computed, reactive, ref, watch } from 'vue';

import { message } from 'ant-design-vue';
import { IconifyIcon } from '@vben/icons';
import { useAccessStore } from '@vben/stores';

import { uploadFile } from '#/api/infra/file';
import {
  getPortalResumeList,
  type HrmPortalResumeApi,
} from '#/api/hrm/portal/resume';
import {
  createPortalRecruitApplication,
  type HrmPortalRecruitApi,
} from '#/api/hrm/portal/recruit';

const props = defineProps<{
  open: boolean;
  post?: HrmPortalRecruitApi.Post;
}>();

const emit = defineEmits<{
  'update:open': [value: boolean];
}>();

const modalOpen = computed({
  get: () => props.open,
  set: (value: boolean) => emit('update:open', value),
});

const accessStore = useAccessStore();
const applySubmitting = ref(false);
const applyFormRef = ref();
const savedResumes = ref<HrmPortalResumeApi.Resume[]>([]);
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
    resumeRemark: '',
    sourceRemark: '',
  });

  if (!accessStore.accessToken) {
    savedResumes.value = [];
    return;
  }

  try {
    savedResumes.value = (await getPortalResumeList()) || [];
    const latestResume = savedResumeOptions.value[0];
    if (latestResume) {
      applyForm.resumeFileName = latestResume.fileName || latestResume.title;
      applyForm.resumeFileUrl = latestResume.fileUrl || '';
    }
  } catch (error) {
    savedResumes.value = [];
    console.warn('[PortalRecruit] 加载已保存简历失败', error);
  }
}

function handleSavedResumeChange(value?: string) {
  if (!value) {
    applyForm.resumeFileName = '';
    applyForm.resumeFileUrl = '';
    return;
  }
  const resume = savedResumeOptions.value.find((item) => item.fileUrl === value);
  if (resume) {
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
  message.success('简历上传成功');
}

async function submitApplication() {
  await applyFormRef.value?.validate();
  if (!applyForm.resumeFileUrl) {
    message.warning('请先上传简历文件');
    return;
  }
  applySubmitting.value = true;
  try {
    await createPortalRecruitApplication(applyForm);
    message.success('应聘投递成功，可在个人中心查看进度');
    modalOpen.value = false;
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
    title="在线投递"
    width="640px"
    :confirm-loading="applySubmitting"
    @ok="submitApplication"
  >
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
  background: #f8fafc;
  font-size: 13px;
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
  border: 1px dashed #93c5fd;
  color: #2563eb;
  background: #eff6ff;
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

@media (max-width: 640px) {
  .form-grid {
    grid-template-columns: 1fr;
    gap: 0;
  }
}
</style>
