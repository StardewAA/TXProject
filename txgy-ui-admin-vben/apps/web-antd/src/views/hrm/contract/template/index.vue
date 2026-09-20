<script lang="ts" setup>
import type { HrmContractTemplateApi } from '#/api/hrm/contract/template';
import type { Rule } from 'ant-design-vue/es/form';

import { computed, onMounted, reactive, ref } from 'vue';

import { useAccess } from '@vben/access';
import { downloadFileFromUrl } from '@vben/utils';

import {
  Button,
  Card,
  Empty,
  Form,
  FormItem,
  Input,
  InputNumber,
  message,
  Modal,
  Popconfirm,
  Select,
  Space,
  Switch,
  Tag,
} from 'ant-design-vue';

import { getCompanyProfileList } from '#/api/hrm/company';
import {
  deleteContractTemplate,
  getContractTemplateDownloadUrl,
  getContractTemplateList,
  saveContractTemplate,
  updateContractTemplate,
} from '#/api/hrm/contract/template';
import { FileUpload } from '#/components/upload';
import { HrmEmployeeContractTypeOptions } from '#/views/hrm/utils/constants';

defineOptions({ name: 'HrmContractTemplate' });

const { hasAccessByCodes } = useAccess();
const canCreate = hasAccessByCodes(['hrm:contract:template:create']);
const canUpdate = hasAccessByCodes(['hrm:contract:template:update']);
const canDelete = hasAccessByCodes(['hrm:contract:template:delete']);
const canDownload = hasAccessByCodes(['hrm:contract:template:download']);

/** 模板分类：与后端 HrmTemplateCategoryEnum 保持一致 */
const CATEGORY_OPTIONS = [
  { label: '劳动合同', value: 1 },
  { label: '个人电子档案', value: 2 },
  { label: '培训协议', value: 3 },
];
const typeOptions = HrmEmployeeContractTypeOptions.map((item) => ({
  label: item.label,
  value: item.value as number,
}));
const typeLabelMap = new Map<number, string>(
  typeOptions.map((item) => [item.value, item.label]),
);

const loading = ref(false);
const saving = ref(false);
const list = ref<HrmContractTemplateApi.ContractTemplate[]>([]);
const employerOptions = ref<Array<{ label: string; value: number }>>([]);
const activeCategory = ref<number | undefined>(undefined);

const modalOpen = ref(false);
const isEdit = ref(false);
const formRef = ref();
const formState = reactive<Record<string, any>>({});
const docxUrls = ref<string[]>([]);
const pdfUrls = ref<string[]>([]);
const pngUrls = ref<string[]>([]);

const previewOpen = ref(false);
const previewTitle = ref('');
const previewUrl = ref('');

const rules: Record<string, Rule[]> = {
  name: [{ required: true, message: '请输入模板名称', trigger: 'blur' }],
  category: [{ required: true, message: '请选择模板分类', trigger: 'change' }],
};

/** 按分类分组，用于分组展示缩略图卡片 */
const groups = computed(() =>
  CATEGORY_OPTIONS.map((option) => ({
    ...option,
    items: list.value
      .filter((item) => item.category === option.value)
      .sort((a, b) => (a.sort ?? 0) - (b.sort ?? 0)),
  })).filter((group) => group.items.length > 0),
);

const visibleGroups = computed(() =>
  activeCategory.value === undefined
    ? groups.value
    : groups.value.filter((group) => group.value === activeCategory.value),
);

const totalCount = computed(() => list.value.length);

async function getList() {
  loading.value = true;
  try {
    list.value = (await getContractTemplateList()) ?? [];
  } finally {
    loading.value = false;
  }
}

async function getEmployerOptions() {
  const profiles = (await getCompanyProfileList()) ?? [];
  employerOptions.value = profiles
    .filter((item) => item.id !== undefined)
    .map((item) => ({ label: item.name as string, value: item.id as number }));
}

function resetForm(data?: HrmContractTemplateApi.ContractTemplate) {
  Object.keys(formState).forEach((key) => delete formState[key]);
  Object.assign(
    formState,
    data ?? {
      category: activeCategory.value ?? 1,
      status: 0,
      version: 1,
      defaultStatus: false,
      sort: 0,
    },
  );
  docxUrls.value = data?.fileUrl ? [data.fileUrl] : [];
  pdfUrls.value = data?.previewUrl ? [data.previewUrl] : [];
  pngUrls.value = data?.thumbnailUrl ? [data.thumbnailUrl] : [];
}

function handleCreate(category?: number) {
  isEdit.value = false;
  resetForm();
  if (category !== undefined) formState.category = category;
  modalOpen.value = true;
}

function handleEdit(record: HrmContractTemplateApi.ContractTemplate) {
  isEdit.value = true;
  resetForm({ ...record });
  modalOpen.value = true;
}

async function handleSubmit() {
  await formRef.value?.validate();
  if (docxUrls.value.length === 0) {
    message.warning('请先上传模板文件（docx）');
    return;
  }
  saving.value = true;
  try {
    const payload = {
      ...formState,
      fileUrl: docxUrls.value[0],
      previewUrl: pdfUrls.value[0] ?? undefined,
      thumbnailUrl: pngUrls.value[0] ?? undefined,
    };
    if (isEdit.value) {
      await updateContractTemplate(payload);
    } else {
      await saveContractTemplate(payload);
    }
    message.success(isEdit.value ? '模板已更新' : '模板已新增');
    modalOpen.value = false;
    await getList();
  } finally {
    saving.value = false;
  }
}

/** 预览：用浏览器内嵌 PDF 查看器（导入模板时已生成 PDF 预览件） */
function handlePreview(record: HrmContractTemplateApi.ContractTemplate) {
  if (!record.previewUrl) {
    message.warning('该模板未生成预览文件，请直接下载 docx 查看');
    return;
  }
  previewTitle.value = record.name ?? '模板预览';
  previewUrl.value = record.previewUrl;
  previewOpen.value = true;
}

async function handleDownload(record: HrmContractTemplateApi.ContractTemplate) {
  if (!record.id) return;
  const rawUrl = await getContractTemplateDownloadUrl(record.id);
  if (!rawUrl) {
    message.warning('该模板未上传文件');
    return;
  }
  // 文件服务返回的是绝对地址（http://127.0.0.1:48080/...），局域网访问时会指向访客本机。
  // 统一转成相对路径，交给当前页面所在网关代理，localhost 与局域网都能下载。
  const fileUrl = rawUrl.replace(/^https?:\/\/[^/]+/, '');
  const suffix = fileUrl.slice(fileUrl.lastIndexOf('.'));
  downloadFileFromUrl({
    source: fileUrl,
    fileName: (record.name ?? '模板') + suffix,
  });
}

async function handleDelete(id?: number) {
  if (!id) return;
  await deleteContractTemplate(id);
  message.success('删除成功');
  await getList();
}

onMounted(async () => {
  await Promise.all([getList(), getEmployerOptions()]);
});
</script>

<template>
  <div>
    <Card :body-style="{ paddingBottom: '8px' }">
      <div class="flex flex-wrap items-center justify-between gap-3">
        <Space wrap>
          <Button
            :type="activeCategory === undefined ? 'primary' : 'default'"
            @click="activeCategory = undefined"
          >
            全部（{{ totalCount }}）
          </Button>
          <Button
            v-for="option in CATEGORY_OPTIONS"
            :key="option.value"
            :type="activeCategory === option.value ? 'primary' : 'default'"
            @click="activeCategory = option.value"
          >
            {{ option.label }}
          </Button>
        </Space>
        <Button type="primary" :disabled="!canCreate" @click="handleCreate()">
          新增模板
        </Button>
      </div>
    </Card>

    <div v-if="loading" class="py-10 text-center">加载中…</div>
    <Empty v-else-if="visibleGroups.length === 0" class="py-10" description="暂无模板" />

    <Card
      v-for="group in visibleGroups"
      :key="group.value"
      :title="group.label"
      :style="{ marginTop: '12px' }"
    >
      <template #extra>
        <Space>
          <span style="color: #999">共 {{ group.items.length }} 份</span>
          <Button size="small" :disabled="!canCreate" @click="handleCreate(group.value)">
            新增
          </Button>
        </Space>
      </template>

      <div class="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4 2xl:grid-cols-5">
        <div
          v-for="item in group.items"
          :key="item.id"
          class="border-border hover:border-primary flex flex-col rounded-md border transition-colors"
        >
          <!-- 缩略图：点击直接预览 -->
          <button
            type="button"
            class="bg-muted flex h-[210px] w-full items-center justify-center overflow-hidden rounded-t-md"
            :title="'点击预览：' + (item.name ?? '')"
            @click="handlePreview(item)"
          >
            <img
              v-if="item.thumbnailUrl"
              :alt="item.name ?? '模板缩略图'"
              class="max-h-full max-w-full object-contain"
              :src="item.thumbnailUrl"
            />
            <span v-else style="color: #999">暂无缩略图</span>
          </button>

          <div class="flex flex-1 flex-col gap-1 p-3">
            <div class="truncate font-medium" :title="item.name ?? ''">
              {{ item.name }}
            </div>
            <div class="flex flex-wrap gap-1">
              <Tag v-if="item.contractType" color="blue">
                {{ typeLabelMap.get(item.contractType) ?? '其他' }}
              </Tag>
              <Tag v-if="item.employType && item.employType !== '不限'" color="cyan">
                {{ item.employType }}
              </Tag>
              <Tag>v{{ item.version ?? 1 }}</Tag>
              <Tag v-if="item.status === 0" color="green">启用</Tag>
              <Tag v-else>停用</Tag>
              <Tag v-if="item.defaultStatus" color="gold">默认</Tag>
            </div>
            <div class="text-muted-foreground truncate text-xs" :title="item.sourceDir ?? ''">
              来源：{{ item.sourceDir ?? '-' }}
            </div>
            <Space class="mt-1" :size="4" wrap>
              <Button type="link" size="small" @click="handlePreview(item)">预览</Button>
              <Button
                type="link"
                size="small"
                :disabled="!canDownload"
                @click="handleDownload(item)"
              >
                下载
              </Button>
              <Button
                type="link"
                size="small"
                :disabled="!canUpdate"
                @click="handleEdit(item)"
              >
                编辑
              </Button>
              <Popconfirm
                title="确认删除该模板？"
                :disabled="!canDelete"
                @confirm="handleDelete(item.id)"
              >
                <Button type="link" size="small" danger :disabled="!canDelete">删除</Button>
              </Popconfirm>
            </Space>
          </div>
        </div>
      </div>
    </Card>

    <!-- 预览弹窗：浏览器内嵌 PDF 查看器 -->
    <Modal
      v-model:open="previewOpen"
      :footer="null"
      :title="'模板预览：' + previewTitle"
      width="960px"
    >
      <div v-if="previewUrl">
        <iframe :src="previewUrl" style="border: 0; height: 74vh; width: 100%"></iframe>
        <div class="mt-2 text-right">
          <a :href="previewUrl" rel="noopener" target="_blank">在新窗口打开</a>
        </div>
      </div>
      <Empty v-else description="未生成预览文件" />
    </Modal>

    <Modal
      v-model:open="modalOpen"
      :confirm-loading="saving"
      :title="isEdit ? '编辑模板' : '新增模板'"
      width="760px"
      @ok="handleSubmit"
    >
      <Form ref="formRef" :label-col="{ span: 6 }" :model="formState" :rules="rules">
        <FormItem label="模板分类" name="category">
          <Select v-model:value="formState.category" :options="CATEGORY_OPTIONS" />
        </FormItem>
        <FormItem label="模板名称" name="name">
          <Input v-model:value="formState.name" placeholder="如：固定期限劳动合同" />
        </FormItem>
        <FormItem v-if="formState.category === 1" label="合同类型">
          <Select
            v-model:value="formState.contractType"
            :allow-clear="true"
            :options="typeOptions"
            placeholder="请选择合同类型"
          />
        </FormItem>
        <FormItem label="适用用工形式">
          <Input
            v-model:value="formState.employType"
            placeholder="如：正式员工 / 临时工 / 劳务派遣；留空表示不限"
          />
        </FormItem>
        <FormItem label="适用用工主体">
          <Select
            v-model:value="formState.employerId"
            :allow-clear="true"
            :options="employerOptions"
            placeholder="留空表示不限用工主体"
          />
        </FormItem>
        <FormItem label="模板文件 docx" required>
          <FileUpload v-model="docxUrls" :disabled="!canCreate && !canUpdate" :max-number="1" />
        </FormItem>
        <FormItem label="预览文件 PDF">
          <FileUpload v-model="pdfUrls" :disabled="!canCreate && !canUpdate" :max-number="1" />
        </FormItem>
        <FormItem label="缩略图 PNG">
          <FileUpload v-model="pngUrls" :disabled="!canCreate && !canUpdate" :max-number="1" />
        </FormItem>
        <FormItem label="显示顺序">
          <InputNumber v-model:value="formState.sort" :min="0" />
        </FormItem>
        <FormItem label="状态">
          <Select
            v-model:value="formState.status"
            :options="[
              { label: '启用', value: 0 },
              { label: '停用', value: 1 },
            ]"
          />
        </FormItem>
        <FormItem label="设为默认模板">
          <Switch v-model:checked="formState.defaultStatus" />
        </FormItem>
        <FormItem label="模板变量清单">
          <Input
            v-model:value="formState.variables"
            placeholder="如：${companyName},${employeeName},${jobNumber}"
          />
        </FormItem>
        <FormItem label="变更说明">
          <Input
            v-model:value="formState.changeNote"
            placeholder="如：按 2026 版示范文本修订第三条"
          />
        </FormItem>
        <FormItem label="备注">
          <Input v-model:value="formState.remark" />
        </FormItem>
      </Form>
    </Modal>
  </div>
</template>
