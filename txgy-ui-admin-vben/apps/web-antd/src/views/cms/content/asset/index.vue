<script lang="ts" setup>
import type { CmsAsset } from '#/api/cms/content-ext';

import {
  Button,
  Card,
  Empty,
  message,
  Popconfirm,
  Select,
  Space,
  Spin,
  Tag,
  Upload,
} from 'ant-design-vue';
import { onMounted, ref } from 'vue';

import { deleteAsset, getAssetPage, saveAsset } from '#/api/cms/content-ext';
import { uploadFile } from '#/api/infra/file';

defineOptions({ name: 'CmsContentAsset' });

const loading = ref(false);
const uploading = ref(false);
const list = ref<CmsAsset[]>([]);
const total = ref(0);
const pageNo = ref(1);
const pageSize = ref(12);
const assetType = ref<string | undefined>(undefined);

const typeOptions = [
  { label: '图片', value: 'IMAGE' },
  { label: 'PDF', value: 'PDF' },
  { label: '视频', value: 'VIDEO' },
  { label: '其他文件', value: 'FILE' },
];

function resolveType(file: File) {
  const name = file.name.toLowerCase();
  if (file.type.startsWith('image/')) return 'IMAGE';
  if (file.type === 'application/pdf' || name.endsWith('.pdf')) return 'PDF';
  if (file.type.startsWith('video/') || /\\.(mp4|mov|avi|mkv)$/.test(name)) return 'VIDEO';
  return 'FILE';
}

async function load() {
  loading.value = true;
  try {
    const result = await getAssetPage({
      assetType: assetType.value,
      pageNo: pageNo.value,
      pageSize: pageSize.value,
    });
    list.value = result.list ?? [];
    total.value = Number(result.total ?? 0);
  } finally {
    loading.value = false;
  }
}

async function handleBeforeUpload(file: File) {
  uploading.value = true;
  try {
    const url = await uploadFile({ directory: 'cms-asset', file });
    await saveAsset({
      assetType: resolveType(file),
      mime: file.type,
      name: file.name,
      size: file.size,
      url: String(url),
    });
    message.success('素材已上传');
    await load();
  } catch (error) {
    console.error(error);
    message.error('上传失败，请检查文件服务配置');
  } finally {
    uploading.value = false;
  }
  return false;
}

async function remove(id: number) {
  await deleteAsset(id);
  message.success('素材已删除');
  await load();
}

function formatSize(size?: number) {
  if (!size) return '-';
  if (size < 1024) return `${size} B`;
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`;
  return `${(size / 1024 / 1024).toFixed(2)} MB`;
}

function changePage(page: number) {
  pageNo.value = page;
  void load();
}

onMounted(load);
</script>

<template>
  <div class="cms-asset-page">
    <Card :bordered="false">
      <template #title>素材库</template>
      <template #extra>
        <Space>
          <Select
            v-model:value="assetType"
            :options="typeOptions"
            allow-clear
            placeholder="素材类型"
            style="width: 140px"
            @change="changePage(1)"
          />
          <Upload :before-upload="handleBeforeUpload" :show-upload-list="false" multiple>
            <Button :loading="uploading" type="primary">上传素材</Button>
          </Upload>
        </Space>
      </template>

      <Spin :spinning="loading">
        <Empty v-if="!list.length" description="暂无素材，点击右上角上传" />
        <div v-else class="asset-grid">
          <div v-for="item in list" :key="item.id" class="asset-card">
            <div class="asset-thumb">
              <img v-if="item.assetType === 'IMAGE'" :alt="item.name" :src="item.url" />
              <span v-else class="asset-type">{{ item.assetType }}</span>
            </div>
            <div class="asset-meta">
              <strong :title="item.name">{{ item.name }}</strong>
              <span>{{ formatSize(item.size) }} · {{ item.creator || '-' }}</span>
            </div>
            <Space class="asset-actions">
              <a :href="item.url" rel="noopener noreferrer" target="_blank">预览</a>
              <Popconfirm title="确认删除该素材？" @confirm="remove(item.id)">
                <a class="danger">删除</a>
              </Popconfirm>
            </Space>
          </div>
        </div>
      </Spin>

      <div v-if="total > pageSize" class="asset-pager">
        <Button :disabled="pageNo <= 1" size="small" @click="changePage(pageNo - 1)">上一页</Button>
        <Tag>{{ pageNo }}</Tag>
        <Button :disabled="pageNo * pageSize >= total" size="small" @click="changePage(pageNo + 1)">下一页</Button>
      </div>
    </Card>
  </div>
</template>

<style scoped>
.asset-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 16px;
}

.asset-card {
  padding: 12px;
  border: 1px solid #eef1f6;
  border-radius: 10px;
  transition: box-shadow 0.2s;
}

.asset-card:hover {
  box-shadow: 0 6px 18px rgb(10 37 64 / 10%);
}

.asset-thumb {
  display: grid;
  height: 120px;
  overflow: hidden;
  background: #f4f6f9;
  border-radius: 8px;
  place-items: center;
}

.asset-thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.asset-type {
  font-weight: 700;
  color: #1a4480;
  letter-spacing: 1px;
}

.asset-meta {
  display: flex;
  flex-direction: column;
  gap: 2px;
  margin: 8px 0;
  font-size: 12.5px;
  color: #8a93a3;
}

.asset-meta strong {
  overflow: hidden;
  font-size: 14px;
  color: #1f2937;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.danger {
  color: #b03a2e;
}

.asset-pager {
  display: flex;
  gap: 10px;
  align-items: center;
  justify-content: center;
  margin-top: 16px;
}
</style>