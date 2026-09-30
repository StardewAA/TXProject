<script lang="ts" setup>
import type { HrmRecruitResumeApi } from '#/api/hrm/recruit/resume';

import { onMounted, ref } from 'vue';

import { useAccess } from '@vben/access';

import { Button, Empty, message } from 'ant-design-vue';
import { downloadFileFromUrl, getFileNameFromUrl } from '@vben/utils';

import {
  createRecruitResume,
  getRecruitLegacyResumeDownload,
  getRecruitLegacyResumePreview,
  getRecruitResumeDownload,
  getRecruitResumeList,
  getRecruitResumePreview,
} from '#/api/hrm/recruit/resume';
import { FileUpload } from '#/components/upload';

import ResumePreviewModal from './resume-preview-modal.vue';

const props = defineProps<{ candidateId: number }>();
const { hasAccessByCodes } = useAccess();
const canUpdate = hasAccessByCodes(['hrm:recruit:candidate:update']);
const loading = ref(false);
const uploadUrls = ref<string[]>([]);
const resumes = ref<HrmRecruitResumeApi.RecruitResume[]>([]);
/** 问题 6：简历在页面内预览（PDF/DOCX/图片），而不是直接下载 */
const previewModalRef = ref<InstanceType<typeof ResumePreviewModal>>();

async function refresh() {
  loading.value = true;
  try {
    resumes.value = await getRecruitResumeList(props.candidateId);
  } finally {
    loading.value = false;
  }
}

async function handleUpload() {
  try {
    for (const fileUrl of uploadUrls.value) {
      const fileName = getFileNameFromUrl(fileUrl);
      await createRecruitResume({
        candidateId: props.candidateId,
        fileName,
        fileUrl,
      });
    }
    uploadUrls.value = [];
    await refresh();
    message.success('简历版本已保存');
  } catch {
    message.error('简历保存失败，请稍后重试');
  }
}

async function handlePreview(item: HrmRecruitResumeApi.RecruitResume) {
  try {
    const resume =
      item.id != null
        ? await getRecruitResumePreview(item.id)
        : await getRecruitLegacyResumePreview(
            props.candidateId,
            item.legacyIndex!,
          );
    if (!resume.fileUrl) {
      message.error('简历授权地址无效');
      return;
    }
    previewModalRef.value?.open({
      fileName: item.fileName,
      fileUrl: resume.fileUrl,
      resumeId: item.id,
    });
  } catch {
    message.error('简历预览失败，请稍后重试');
  }
}

async function handleDownload(item: HrmRecruitResumeApi.RecruitResume) {
  try {
    const resume =
      item.id != null
        ? await getRecruitResumeDownload(item.id)
        : await getRecruitLegacyResumeDownload(
            props.candidateId,
            item.legacyIndex!,
          );
    if (!resume.fileUrl) {
      message.error('简历授权地址无效');
      return;
    }
    downloadFileFromUrl({ source: resume.fileUrl, fileName: item.fileName });
  } catch {
    message.error('简历下载失败，请稍后重试');
  }
}

onMounted(refresh);
</script>

<template>
  <div v-loading="loading" class="space-y-3">
    <ResumePreviewModal ref="previewModalRef" @saved="refresh" />
    <div v-if="canUpdate" class="flex items-start gap-3">
      <FileUpload
        v-model="uploadUrls"
        :max-number="5"
        :multiple="true"
        directory="hrm/recruit/resume"
        :accept="['.pdf', '.doc', '.docx']"
      />
      <Button
        type="primary"
        :disabled="!uploadUrls.length"
        @click="handleUpload"
        >保存新版本</Button
      >
    </div>
    <div v-if="resumes.length" class="divide-y">
      <div
        v-for="resume in resumes"
        :key="resume.id ?? `legacy-${resume.legacyIndex}`"
        class="flex items-center justify-between py-3"
      >
        <div class="min-w-0">
          <div class="truncate">
            <span v-if="resume.legacyIndex != null">历史附件</span>
            <span v-else>V{{ resume.versionNo ?? '-' }}</span>
            · {{ resume.fileName }}
          </div>
          <div class="text-muted-foreground text-xs">
            {{ resume.createTime }}
          </div>
        </div>
        <div class="shrink-0">
          <Button type="link" @click="handlePreview(resume)">预览</Button>
          <Button type="link" @click="handleDownload(resume)">下载</Button>
        </div>
      </div>
    </div>
    <Empty v-else description="暂无简历版本" />
  </div>
</template>
