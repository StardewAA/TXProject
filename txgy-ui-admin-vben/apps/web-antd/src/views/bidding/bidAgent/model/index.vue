<!--
  标书智能体 · 云端模型管理（全局资源）
  定位：所有智能体的模型兜底配置——baseUrl/model/apiKey 统一托管，智能体页只做按需覆盖。
  契约：src/api/bidding/bidAgent/platform.ts（对应 BidAgentModelController）
  设计：Material Design 规范 · vben Page + Ant Card/Table/Form
-->
<script lang="ts" setup>
import { onMounted, reactive, ref } from 'vue';

import { Page } from '@vben/common-ui';
import { IconifyIcon } from '@vben/icons';

import {
  Alert,
  Button,
  Card,
  Form,
  FormItem,
  Input,
  InputNumber,
  Modal,
  Select,
  Space,
  Switch,
  Table,
  Tag,
  Textarea,
  message,
} from 'ant-design-vue';

import {
  listBidAgentModel,
  saveBidAgentModel,
  testBidAgentModel,
  type BidAgentPlatformApi,
} from '#/api/bidding/bidAgent/platform';

const models = ref<BidAgentPlatformApi.ModelConfig[]>([]);
const loading = ref(false);

const editing = ref(false);
const form = reactive<BidAgentPlatformApi.ModelConfig>({
  name: '',
  provider: 'openai-compat',
  baseUrl: '',
  model: '',
  apiKey: undefined,
  temperature: undefined,
  maxTokens: undefined,
  timeoutSeconds: 180,
  maxRetries: 3,
  enabled: 1,
  remark: undefined,
});

// 连通性测试结果
const testing = ref(false);
const testOpen = ref(false);
const testResult = ref<BidAgentPlatformApi.ModelTestResp>();

async function loadModels() {
  loading.value = true;
  try {
    models.value = await listBidAgentModel();
  } catch (error: any) {
    message.error(error?.message ?? '云端模型列表加载失败');
  } finally {
    loading.value = false;
  }
}

function resetForm() {
  Object.assign(form, {
    id: undefined,
    name: '',
    provider: 'openai-compat',
    baseUrl: '',
    model: '',
    apiKey: undefined,
    temperature: undefined,
    maxTokens: undefined,
    timeoutSeconds: 180,
    maxRetries: 3,
    enabled: 1,
    remark: undefined,
  });
  editing.value = false;
}

function startCreate() {
  resetForm();
  editing.value = true;
}

function startEdit(record: BidAgentPlatformApi.ModelConfig) {
  Object.assign(form, { ...record, apiKey: undefined }); // apiKey 留空=不修改
  editing.value = true;
}

async function handleSave() {
  if (!form.name || !form.baseUrl || !form.model) {
    message.warning('名称、端点地址、模型名必填');
    return;
  }
  try {
    await saveBidAgentModel({ ...form });
    message.success(form.id ? '云端模型已更新' : '云端模型已新增');
    editing.value = false;
    await loadModels();
  } catch (error: any) {
    message.error(error?.message ?? '云端模型保存失败');
  }
}

async function handleTest(record: BidAgentPlatformApi.ModelConfig) {
  testing.value = true;
  try {
    testResult.value = await testBidAgentModel({ id: record.id as number });
    testOpen.value = true;
  } catch (error: any) {
    message.error(error?.message ?? '连通性测试失败');
  } finally {
    testing.value = false;
  }
}

onMounted(loadModels);
</script>

<template>
  <Page auto-content-height>
    <!-- 页面标题区：Material Design H1 + 简短描述 -->
    <div class="page-header">
      <div class="page-header-main">
        <div class="page-eyebrow">GLOBAL RESOURCE</div>
        <h1 class="page-title">云端模型管理</h1>
        <p class="page-desc">智能体的模型兜底配置：未在智能体页覆盖时，统一走这里启用的模型</p>
      </div>
      <div class="page-header-actions">
        <Button type="primary" @click="startCreate">
          <template #icon><IconifyIcon icon="lucide:plus" /></template>
          新增模型
        </Button>
      </div>
    </div>

    <!-- 模型列表卡片 -->
    <Card :bordered="false" class="surface-card">
      <template #title>
        <div class="card-title">
          <IconifyIcon icon="lucide:cpu" class="card-title-icon" />
          <span>已配置模型</span>
          <Tag class="card-title-tag">{{ models.length }} 条</Tag>
        </div>
      </template>
      <Table
        :data-source="models"
        :loading="loading"
        :pagination="false"
        :row-key="(row: BidAgentPlatformApi.ModelConfig) => String(row.id)"
        size="middle"
      >
        <Table.Column title="名称" data-index="name" :width="160">
          <template #default="{ record }">
            <div class="cell-strong">{{ record.name }}</div>
            <div class="cell-sub">#{{ record.id }}</div>
          </template>
        </Table.Column>
        <Table.Column title="提供商" data-index="provider" :width="140">
          <template #default="{ record }">
            <Tag class="chip-neutral">{{ record.provider }}</Tag>
          </template>
        </Table.Column>
        <Table.Column title="模型" data-index="model" :width="180">
          <template #default="{ record }">
            <code class="code-text">{{ record.model }}</code>
          </template>
        </Table.Column>
        <Table.Column title="端点" data-index="baseUrl">
          <template #default="{ record }">
            <span class="cell-ellipsis" :title="record.baseUrl">{{ record.baseUrl }}</span>
          </template>
        </Table.Column>
        <Table.Column title="API Key" :width="160">
          <template #default="{ record }">
            <Tag v-if="record.apiKeyConfigured" class="chip-success">{{ record.apiKeyMask }}</Tag>
            <Tag v-else class="chip-danger">未配置</Tag>
          </template>
        </Table.Column>
        <Table.Column title="状态" :width="90">
          <template #default="{ record }">
            <span class="status-dot" :class="record.enabled === 1 ? 'is-on' : 'is-off'" />
            <span>{{ record.enabled === 1 ? '启用' : '停用' }}</span>
          </template>
        </Table.Column>
        <Table.Column title="操作" :width="160" align="right">
          <template #default="{ record }">
            <Space size="small">
              <Button type="link" size="small" :loading="testing" @click="handleTest(record)">测试</Button>
              <Button type="link" size="small" @click="startEdit(record)">编辑</Button>
            </Space>
          </template>
        </Table.Column>
      </Table>
    </Card>

    <Modal v-model:open="editing" :title="form.id ? '编辑云端模型' : '新增云端模型'" :width="680" @ok="handleSave" ok-text="保存" cancel-text="取消">
      <Form layout="vertical" class="form-grid">
        <Form.Item label="名称" required>
          <Input v-model:value="form.name" placeholder="请输入模型名称（如：默认 Qwen）" />
        </Form.Item>
        <Form.Item label="提供商">
          <Select
            v-model:value="form.provider"
            :options="[
              { label: 'openai-compat（OpenAI 兼容协议）', value: 'openai-compat' },
              { label: 'dashscope（阿里云百炼）', value: 'dashscope' },
            ]"
          />
        </Form.Item>
        <Form.Item label="模型名" required>
          <Input v-model:value="form.model" placeholder="如 qwen-plus / gpt-4o" />
        </Form.Item>
        <Form.Item label="状态">
          <div class="switch-row">
            <span class="switch-label">{{ form.enabled === 1 ? '启用' : '停用' }}</span>
            <Switch
              v-model:checked="form.enabled"
              :checked-value="1"
              :un-checked-value="0"
              checked-children="启用"
              un-checked-children="停用"
            />
          </div>
        </Form.Item>
        <Form.Item label="端点地址" required>
          <Input v-model:value="form.baseUrl" placeholder="https://dashscope.aliyuncs.com/compatible-mode/v1" />
        </Form.Item>
        <Form.Item label="API Key（留空=不修改）">
          <Input.Password v-model:value="form.apiKey" placeholder="已配置时留空即保持原 Key" />
        </Form.Item>
        <Form.Item label="温度（0 ~ 2）">
          <InputNumber v-model:value="form.temperature" :min="0" :max="2" :step="0.1" style="width: 100%" />
        </Form.Item>
        <Form.Item label="最大 Token">
          <InputNumber v-model:value="form.maxTokens" :min="1" style="width: 100%" />
        </Form.Item>
        <Form.Item label="超时（秒）">
          <InputNumber v-model:value="form.timeoutSeconds" :min="1" style="width: 100%" />
        </Form.Item>
        <Form.Item label="最大重试">
          <InputNumber v-model:value="form.maxRetries" :min="0" style="width: 100%" />
        </Form.Item>
        <Form.Item label="备注" class="form-grid-full">
          <Textarea v-model:value="form.remark" :rows="2" placeholder="可选：内部用途、限额说明等" />
        </Form.Item>
      </Form>
    </Modal>

    <Modal v-model:open="testOpen" title="连通性测试" :footer="null" :width="520">
      <Alert
        :type="testResult?.ok ? 'success' : 'error'"
        show-icon
        :message="testResult?.message ?? ''"
        :description="testResult?.ok ? '已与云端模型建立有效通道，可在智能体页引用。' : '请检查端点、API Key、模型名是否正确。'"
      />
    </Modal>
  </Page>
</template>

<style scoped>
/* ============ Material Design 通用：页面标题 ============ */
.page-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  padding: 4px 4px 18px;
  border-bottom: 1px solid hsl(var(--border));
  margin-bottom: 18px;
}
.page-header-main {
  min-width: 0;
}
.page-eyebrow {
  font-size: 11px;
  letter-spacing: 2px;
  text-transform: uppercase;
  color: hsl(var(--primary));
  font-weight: 600;
  margin-bottom: 4px;
}
.page-title {
  font-size: 24px;
  font-weight: 700;
  line-height: 1.25;
  margin: 0 0 4px;
  letter-spacing: 0.2px;
}
.page-desc {
  color: hsl(var(--muted-foreground));
  margin: 0;
  font-size: 13px;
  max-width: 720px;
}
.page-header-actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}

/* ============ 卡片 ============ */
.surface-card {
  border-radius: 12px;
  box-shadow: 0 1px 2px hsl(var(--border) / 0.4), 0 1px 1px hsl(var(--border) / 0.2);
}
.surface-card :deep(.ant-card-head) {
  border-bottom: 1px solid hsl(var(--border));
  padding: 14px 20px;
  min-height: 56px;
}
.surface-card :deep(.ant-card-head-title) {
  font-weight: 600;
  font-size: 15px;
}
.surface-card :deep(.ant-card-body) {
  padding: 16px 20px 20px;
}
.card-title {
  display: flex;
  align-items: center;
  gap: 8px;
}
.card-title-icon {
  color: hsl(var(--primary));
  font-size: 16px;
}
.card-title-tag {
  margin-left: 4px;
  font-weight: 500;
  border-radius: 999px;
  padding: 0 8px;
  background: hsl(var(--primary) / 0.08);
  color: hsl(var(--primary));
  border: 0;
}

/* ============ 单元格样式 ============ */
.cell-strong {
  font-weight: 600;
  color: hsl(var(--foreground));
}
.cell-sub {
  font-size: 11.5px;
  color: hsl(var(--muted-foreground));
  font-family: var(--vben-font-mono, 'SF Mono', Consolas, monospace);
}
.cell-ellipsis {
  display: inline-block;
  max-width: 320px;
  overflow: hidden;
  text-overflow: ellipsis;
  vertical-align: middle;
  white-space: nowrap;
}
.code-text {
  font-family: var(--vben-font-mono, 'SF Mono', Consolas, monospace);
  font-size: 12.5px;
  padding: 2px 6px;
  background: hsl(var(--muted) / 0.5);
  border-radius: 4px;
}

/* ============ 标签 / 状态点 ============ */
.chip-success,
.chip-danger,
.chip-neutral {
  border-radius: 999px;
  padding: 1px 10px;
  font-weight: 500;
  border: 0;
}
.chip-success {
  background: hsl(var(--success) / 0.12);
  color: hsl(var(--success));
}
.chip-danger {
  background: hsl(var(--destructive) / 0.12);
  color: hsl(var(--destructive));
}
.chip-neutral {
  background: hsl(var(--muted) / 0.5);
  color: hsl(var(--muted-foreground));
}
.status-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin-right: 6px;
  vertical-align: middle;
}
.status-dot.is-on {
  background: hsl(var(--success));
  box-shadow: 0 0 0 3px hsl(var(--success) / 0.18);
}
.status-dot.is-off {
  background: hsl(var(--muted-foreground));
}

/* ============ 表单网格 ============ */
.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 20px;
}
.form-grid :deep(.ant-form-item) {
  margin-bottom: 16px;
}
.form-grid-full {
  grid-column: 1 / -1;
}
.switch-row {
  display: flex;
  align-items: center;
  gap: 10px;
  height: 32px;
}
.switch-label {
  font-size: 13px;
  color: hsl(var(--muted-foreground));
}

@media (max-width: 720px) {
  .form-grid {
    grid-template-columns: 1fr;
  }
}
</style>