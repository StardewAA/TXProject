<!--
  标书智能体 · 技能管理（全局资源）
  定位：平台内置原子技能的统一管理台——新增/编辑/停用，绑定适用智能体类型，是否自动推荐挂载。
  契约：src/api/bidding/bidAgent/platform.ts（对应 BidAgentSkillController）
  设计：Material Design 规范 · vben Page + Ant Card/Table/Form
-->
<script lang="ts" setup>
import { onMounted, reactive, ref } from 'vue';

import { Page } from '@vben/common-ui';
import { IconifyIcon } from '@vben/icons';

import {
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
  deleteBidAgentSkill,
  getBidAgentSkillPage,
  saveBidAgentSkill,
  type BidAgentPlatformApi,
} from '#/api/bidding/bidAgent/platform';

const skills = ref<BidAgentPlatformApi.Skill[]>([]);
const total = ref(0);
const loading = ref(false);
const query = reactive({
  code: undefined as string | undefined,
  name: undefined as string | undefined,
  status: undefined as number | undefined,
  pageNo: 1,
  pageSize: 10,
});

// 新增/编辑共用表单；applicableTaskTypes 用数组承载，保存时合并为逗号串
const editing = ref(false);
const form = reactive<{
  id?: number;
  code: string;
  name: string;
  description?: string;
  promptFragment?: string;
  knowledgeRoles?: string;
  toolWhitelist?: string;
  applicableTaskTypes: string[];
  autoAttach: number;
  status: number;
  sort: number;
  remark?: string;
}>({
  code: '',
  name: '',
  applicableTaskTypes: [],
  autoAttach: 1,
  status: 0,
  sort: 0,
});

const TASK_TYPE_OPTIONS = ['PARSE', 'OUTLINE', 'DRAFT', 'FILL'].map((v) => ({ label: v, value: v }));

/**
 * 平台预置技能（tenantId=0）由平台统一维护：后端 get/save/delete 走租户过滤，
 * 租户点编辑/删除会直接报「技能不存在」。故列表里必须显式只读。
 */
function isPlatformSkill(record: BidAgentPlatformApi.Skill): boolean {
  return record.tenantId === 0;
}
const TASK_TYPE_COLORS: Record<string, string> = {
  PARSE: 'chip-blue',
  OUTLINE: 'chip-purple',
  DRAFT: 'chip-amber',
  FILL: 'chip-green',
};

async function loadSkills() {
  loading.value = true;
  try {
    const res = await getBidAgentSkillPage({ ...query });
    skills.value = res.list ?? [];
    total.value = res.total;
  } catch (error: any) {
    message.error(error?.message ?? '技能列表加载失败');
  } finally {
    loading.value = false;
  }
}

function resetForm() {
  Object.assign(form, {
    id: undefined,
    code: '',
    name: '',
    description: undefined,
    promptFragment: undefined,
    knowledgeRoles: undefined,
    toolWhitelist: undefined,
    applicableTaskTypes: [],
    autoAttach: 1,
    status: 0,
    sort: 0,
    remark: undefined,
  });
  editing.value = false;
}

function startCreate() {
  resetForm();
  editing.value = true;
}

function startEdit(record: BidAgentPlatformApi.Skill) {
  if (isPlatformSkill(record)) {
    message.info('平台预置技能不可编辑；如需调整请复制为租户技能（改用不同编码新增）');
    return;
  }
  Object.assign(form, {
    ...record,
    applicableTaskTypes: record.applicableTaskTypes ? record.applicableTaskTypes.split(',').filter(Boolean) : [],
  });
  editing.value = true;
}

async function handleSave() {
  if (!form.code || !form.name) {
    message.warning('技能编码与名称必填');
    return;
  }
  try {
    await saveBidAgentSkill({
      ...form,
      applicableTaskTypes: form.applicableTaskTypes.length ? form.applicableTaskTypes.join(',') : undefined,
    });
    message.success(form.id ? '技能已更新' : '技能已新增');
    editing.value = false;
    await loadSkills();
  } catch (error: any) {
    message.error(error?.message ?? '技能保存失败');
  }
}

function handleDelete(record: BidAgentPlatformApi.Skill) {
  if (isPlatformSkill(record)) {
    message.info('平台预置技能不可删除');
    return;
  }
  Modal.confirm({
    title: `删除技能「${record.name}」？`,
    content: '删除后不影响已挂载该技能的智能体（关联会随之失效），确认继续？',
    okText: '删除',
    okType: 'danger',
    cancelText: '取消',
    async onOk() {
      try {
        await deleteBidAgentSkill(record.id as number);
        message.success('已删除');
        await loadSkills();
      } catch (error: any) {
        message.error(error?.message ?? '删除失败');
      }
    },
  });
}

onMounted(loadSkills);
</script>

<template>
  <Page auto-content-height>
    <div class="page-header">
      <div class="page-header-main">
        <div class="page-eyebrow">GLOBAL RESOURCE</div>
        <h1 class="page-title">技能管理</h1>
        <p class="page-desc">平台内置原子技能：智能体编辑页只引用、不维护，这里统一管理</p>
      </div>
      <div class="page-header-actions">
        <Button type="primary" @click="startCreate">
          <template #icon><IconifyIcon icon="lucide:plus" /></template>
          新增技能
        </Button>
      </div>
    </div>

    <!-- 筛选 -->
    <Card :bordered="false" class="surface-card filter-card">
      <Space wrap>
        <Input v-model:value="query.code" placeholder="技能编码" style="width: 160px" allow-clear @press-enter="loadSkills" />
        <Input v-model:value="query.name" placeholder="技能名称" style="width: 180px" allow-clear @press-enter="loadSkills" />
        <Select
          v-model:value="query.status"
          :options="[
            { label: '启用', value: 0 },
            { label: '停用', value: 1 },
          ]"
          allow-clear
          placeholder="状态"
          style="width: 120px"
        />
        <Button type="primary" @click="loadSkills">
          <template #icon><IconifyIcon icon="lucide:search" /></template>
          查询
        </Button>
      </Space>
    </Card>

    <!-- 列表 -->
    <Card :bordered="false" class="surface-card">
      <template #title>
        <div class="card-title">
          <IconifyIcon icon="lucide:layers" class="card-title-icon" />
          <span>技能列表</span>
          <Tag class="card-title-tag">共 {{ total }} 条</Tag>
        </div>
      </template>
      <Table
        :data-source="skills"
        :loading="loading"
        :pagination="{
          current: query.pageNo,
          pageSize: query.pageSize,
          total,
          onChange: (p: number) => {
            query.pageNo = p;
            loadSkills();
          },
        }"
        :row-key="(row: BidAgentPlatformApi.Skill) => String(row.id)"
        size="middle"
      >
        <Table.Column title="技能" :width="240">
          <template #default="{ record }">
            <div class="cell-strong">
              {{ record.name }}
              <Tag v-if="isPlatformSkill(record)" class="chip-neutral skill-origin">平台预置</Tag>
            </div>
            <div class="cell-sub"><code>{{ record.code }}</code></div>
          </template>
        </Table.Column>
        <Table.Column title="描述" data-index="description">
          <template #default="{ record }">
            <span class="cell-ellipsis" :title="record.description">{{ record.description || '—' }}</span>
          </template>
        </Table.Column>
        <Table.Column title="适用任务类型" :width="260">
          <template #default="{ record }">
            <Space size="small" wrap>
              <Tag
                v-for="t in (record.applicableTaskTypes || '').split(',').filter(Boolean)"
                :key="t"
                :class="TASK_TYPE_COLORS[t] || 'chip-neutral'"
              >
                {{ t }}
              </Tag>
            </Space>
          </template>
        </Table.Column>
        <Table.Column title="工具白名单" data-index="toolWhitelist" :width="220">
          <template #default="{ record }">
            <span class="cell-ellipsis" :title="record.toolWhitelist ?? ''">
              <code v-if="record.toolWhitelist">{{ record.toolWhitelist }}</code>
              <span v-else class="cell-sub">不限</span>
            </span>
          </template>
        </Table.Column>
        <Table.Column title="自动挂载" :width="100">
          <template #default="{ record }">
            <Tag :class="record.autoAttach === 1 ? 'chip-success' : 'chip-neutral'">
              {{ record.autoAttach === 1 ? '推荐' : '手动' }}
            </Tag>
          </template>
        </Table.Column>
        <Table.Column title="状态" :width="100">
          <template #default="{ record }">
            <span class="status-dot" :class="record.status === 0 ? 'is-on' : 'is-off'" />
            <span>{{ record.status === 0 ? '启用' : '停用' }}</span>
          </template>
        </Table.Column>
        <Table.Column title="操作" :width="140" align="right">
          <template #default="{ record }">
            <Space size="small">
              <Button type="link" size="small" :disabled="isPlatformSkill(record)" @click="startEdit(record)">
                编辑
              </Button>
              <Button type="link" size="small" danger :disabled="isPlatformSkill(record)" @click="handleDelete(record)">
                删除
              </Button>
            </Space>
          </template>
        </Table.Column>
      </Table>
    </Card>

    <Modal v-model:open="editing" :title="form.id ? '编辑技能' : '新增技能'" :width="780" @ok="handleSave" ok-text="保存" cancel-text="取消">
      <Form layout="vertical" class="form-grid">
        <Form.Item label="技能编码（唯一）" required>
          <Input v-model:value="form.code" :disabled="Boolean(form.id)" placeholder="建议英文短码，如 bid-outline-summary" />
        </Form.Item>
        <Form.Item label="技能名称" required>
          <Input v-model:value="form.name" placeholder="如：大纲摘要" />
        </Form.Item>
        <Form.Item label="排序">
          <InputNumber v-model:value="form.sort" :min="0" style="width: 100%" />
        </Form.Item>
        <Form.Item label="描述" class="form-grid-full">
          <Textarea v-model:value="form.description" :rows="2" placeholder="技能用途简述，将显示在智能体编辑页" />
        </Form.Item>
        <Form.Item label="提示词片段（注入 system prompt）" class="form-grid-full">
          <Textarea v-model:value="form.promptFragment" :rows="5" placeholder="追加到 Agent system prompt 的内容" />
        </Form.Item>
        <Form.Item label="适用任务类型（可多选）">
          <Select v-model:value="form.applicableTaskTypes" :options="TASK_TYPE_OPTIONS" mode="multiple" placeholder="PARSE/OUTLINE/DRAFT/FILL" />
        </Form.Item>
        <Form.Item label="建议知识库角色（逗号分隔）">
          <Input v-model:value="form.knowledgeRoles" placeholder="TENDER,FACT,TEMPLATE" />
        </Form.Item>
        <Form.Item label="建议/限定工具白名单（逗号分隔）" class="form-grid-full">
          <Input v-model:value="form.toolWhitelist" placeholder="bid-retrieve,bid-docx" />
        </Form.Item>
        <Form.Item label="自动推荐挂载">
          <div class="switch-row">
            <span class="switch-label">{{ form.autoAttach === 1 ? '推荐（智能体启用时默认挂载）' : '手动（需在智能体页显式勾选）' }}</span>
            <Switch v-model:checked="form.autoAttach" :checked-value="1" :un-checked-value="0" />
          </div>
        </Form.Item>
        <Form.Item label="状态">
          <div class="switch-row">
            <span class="switch-label">{{ form.status === 0 ? '启用' : '停用' }}</span>
            <Switch v-model:checked="form.status" :checked-value="0" :un-checked-value="1" checked-children="启用" un-checked-children="停用" />
          </div>
        </Form.Item>
        <Form.Item label="备注" class="form-grid-full">
          <Textarea v-model:value="form.remark" :rows="2" />
        </Form.Item>
      </Form>
    </Modal>
  </Page>
</template>

<style scoped>
.page-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  padding: 4px 4px 18px;
  border-bottom: 1px solid hsl(var(--border));
  margin-bottom: 18px;
}
.page-header-main { min-width: 0; }
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

.surface-card {
  border-radius: 12px;
  box-shadow: 0 1px 2px hsl(var(--border) / 0.4), 0 1px 1px hsl(var(--border) / 0.2);
  margin-bottom: 16px;
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
.filter-card :deep(.ant-card-body) {
  padding: 14px 20px;
}
.card-title {
  display: flex;
  align-items: center;
  gap: 8px;
}
.card-title-icon { color: hsl(var(--primary)); font-size: 16px; }
.card-title-tag {
  margin-left: 4px;
  font-weight: 500;
  border-radius: 999px;
  padding: 0 8px;
  background: hsl(var(--primary) / 0.08);
  color: hsl(var(--primary));
  border: 0;
}

.cell-strong { font-weight: 600; color: hsl(var(--foreground)); }
.skill-origin {
  margin-left: 6px;
  font-size: 11px;
  padding: 0 7px;
  vertical-align: middle;
}
.cell-sub {
  font-size: 11.5px;
  color: hsl(var(--muted-foreground));
}
.cell-sub code {
  font-family: var(--vben-font-mono, 'SF Mono', Consolas, monospace);
  font-size: 11.5px;
}
.cell-ellipsis {
  display: inline-block;
  max-width: 320px;
  overflow: hidden;
  text-overflow: ellipsis;
  vertical-align: middle;
  white-space: nowrap;
}

.chip-success,
.chip-danger,
.chip-neutral,
.chip-blue,
.chip-purple,
.chip-amber,
.chip-green {
  border-radius: 999px;
  padding: 1px 10px;
  font-weight: 500;
  border: 0;
  font-size: 12px;
}
.chip-success { background: hsl(var(--success) / 0.12); color: hsl(var(--success)); }
.chip-danger  { background: hsl(var(--destructive) / 0.12); color: hsl(var(--destructive)); }
.chip-neutral { background: hsl(var(--muted) / 0.5); color: hsl(var(--muted-foreground)); }
.chip-blue    { background: hsl(212 90% 95%); color: hsl(212 80% 38%); }
.chip-purple  { background: hsl(265 80% 95%); color: hsl(265 60% 40%); }
.chip-amber   { background: hsl(35 95% 92%); color: hsl(30 80% 38%); }
.chip-green   { background: hsl(150 65% 92%); color: hsl(150 60% 28%); }

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
.status-dot.is-off { background: hsl(var(--muted-foreground)); }

.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 20px;
}
.form-grid :deep(.ant-form-item) { margin-bottom: 16px; }
.form-grid-full { grid-column: 1 / -1; }
.switch-row {
  display: flex;
  align-items: center;
  gap: 10px;
  height: 32px;
}
.switch-label { font-size: 13px; color: hsl(var(--muted-foreground)); }

@media (max-width: 720px) {
  .form-grid { grid-template-columns: 1fr; }
}
</style>