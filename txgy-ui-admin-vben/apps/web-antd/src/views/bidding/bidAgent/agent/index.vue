<!--
  标书智能体 · 智能体管理
  - 改提示词 / 换模型 / 挂技能与 MCP / 试跑验证。
  - 生效口径：五类智能体提示词均后台可编辑，保存后生效；底座内置提示词仅作兜底。
    技能 / MCP 与提示词相互独立，所有智能体都可配置。
  - 设计：Material Design 规范 · vben Page + Ant Tabs/Card/Form
-->
<script lang="ts" setup>
import { computed, onMounted, reactive, ref } from 'vue';

import { Page } from '@vben/common-ui';
import { IconifyIcon } from '@vben/icons';

import {
  Alert,
  Button,
  Card,
  CheckboxGroup,
  Divider,
  Empty,
  Form,
  Input,
  InputNumber,
  message,
  Modal,
  Select,
  Space,
  Switch,
  Table,
  TabPane,
  Tabs,
  Tag,
  Textarea,
} from 'ant-design-vue';

import {
  type BidAgentApi,
  listBidAgentProfile,
  previewBidAgent,
  saveBidAgentProfile,
} from '#/api/bidding/bidAgent';
import {
  type BidAgentPlatformApi,
  checkBidAgentMcp,
  deleteBidAgentMcp,
  deleteBidAgentSkill,
  getBidAgentMcpPage,
  getBidAgentSkillPage,
  saveBidAgentMcp,
  saveBidAgentSkill,
  listBidAgentProfileMcp,
  listBidAgentProfileSkills,
  parseToolSnapshot,
  saveBidAgentProfileMcp,
  saveBidAgentProfileSkills,
} from '#/api/bidding/bidAgent/platform';

const profiles = ref<BidAgentApi.Profile[]>([]);
const editOpen = ref(false);
const isReadonly = ref(false);
const saving = ref(false);

// —— 基础与提示词 / 模型参数（knowledgeRoles 用数组承载，保存时合并逗号串） ——
const form = reactive<{
  description?: string;
  id?: number;
  knowledgeRoles: string[];
  maxTokens?: number;
  model?: string;
  name: string;
  remark?: string;
  skillAutoAttach: number;
  status: number;
  systemPrompt?: string;
  taskType: string;
  temperature?: number;
}>({ taskType: '', name: '', knowledgeRoles: [], skillAutoAttach: 1, status: 0 });

// —— 技能挂载 ——
const allSkills = ref<BidAgentPlatformApi.Skill[]>([]);
const selectedSkillIds = ref<number[]>([]);

// —— MCP 引用 ——
const mcpServers = ref<BidAgentPlatformApi.McpServer[]>([]);
const mcpSelections = reactive<Record<string, { bound: boolean; toolNames: string[] }>>({});

// —— 技能管理 ——
const skillEditing = ref(false);
const skillForm = reactive<{
  applicableTaskTypes: string[];
  autoAttach: number;
  code: string;
  description?: string;
  id?: number;
  knowledgeRoles?: string;
  name: string;
  promptFragment?: string;
  remark?: string;
  sort: number;
  status: number;
  toolWhitelist?: string;
}>({
  code: '',
  name: '',
  applicableTaskTypes: [],
  autoAttach: 1,
  status: 0,
  sort: 0,
});
const TASK_TYPE_OPTIONS = ['PARSE', 'OUTLINE', 'DRAFT', 'FILL'].map((v) => ({ label: v, value: v }));

function isPlatformSkill(record: BidAgentPlatformApi.Skill): boolean {
  return record.tenantId === 0;
}

function resetSkillForm() {
  Object.assign(skillForm, {
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
  skillEditing.value = false;
}

function startCreateSkill() {
  resetSkillForm();
  skillEditing.value = true;
}

function startEditSkill(record: BidAgentPlatformApi.Skill) {
  if (isPlatformSkill(record)) {
    message.info('平台预置技能不可编辑；如需调整请复制为租户技能');
    return;
  }
  Object.assign(skillForm, {
    ...record,
    applicableTaskTypes: record.applicableTaskTypes ? record.applicableTaskTypes.split(',').filter(Boolean) : [],
  });
  skillEditing.value = true;
}

async function saveSkillInline() {
  if (!skillForm.code || !skillForm.name) {
    message.warning('技能编码与名称必填');
    return;
  }
  try {
    await saveBidAgentSkill({
      ...skillForm,
      applicableTaskTypes: skillForm.applicableTaskTypes.length ? skillForm.applicableTaskTypes.join(',') : undefined,
    });
    message.success(skillForm.id ? '技能已更新' : '技能已新增');
    skillEditing.value = false;
    await loadSkillOptions();
  } catch (error: any) {
    message.error(error?.message ?? '技能保存失败');
  }
}

function deleteSkillInline(record: BidAgentPlatformApi.Skill) {
  if (isPlatformSkill(record)) {
    message.info('平台预置技能不可删除');
    return;
  }
  Modal.confirm({
    title: `删除技能「${record.name}」？`,
    content: '删除后已挂载该技能的智能体关联会失效，确认继续？',
    okText: '删除',
    okType: 'danger',
    cancelText: '取消',
    async onOk() {
      try {
        await deleteBidAgentSkill(record.id as number);
        message.success('技能已删除');
        await loadSkillOptions();
      } catch (error: any) {
        message.error(error?.message ?? '技能删除失败');
      }
    },
  });
}

// —— MCP 服务管理 ——
const mcpEditing = ref(false);
const mcpChecking = ref(false);
const mcpCheckOpen = ref(false);
const mcpCheckResult = ref<BidAgentPlatformApi.McpCheckResp>();
const MCP_TRANSPORT_OPTIONS = [
  { label: 'streamable-http', value: 'streamable-http' },
  { label: 'sse', value: 'sse' },
  { label: 'stdio', value: 'stdio' },
];
const mcpForm = reactive<BidAgentPlatformApi.McpServer>({
  name: '',
  transport: 'streamable-http',
  command: undefined,
  args: undefined,
  env: undefined,
  url: undefined,
  headers: undefined,
  timeoutSeconds: 60,
  enabled: 1,
  remark: undefined,
});

function resetMcpForm() {
  Object.assign(mcpForm, {
    id: undefined,
    name: '',
    transport: 'streamable-http',
    command: undefined,
    args: undefined,
    env: undefined,
    url: undefined,
    headers: undefined,
    timeoutSeconds: 60,
    enabled: 1,
    remark: undefined,
  });
  mcpEditing.value = false;
}

function startCreateMcp() {
  resetMcpForm();
  mcpEditing.value = true;
}

function startEditMcp(record: BidAgentPlatformApi.McpServer) {
  Object.assign(mcpForm, {
    ...record,
    env: record.envMask ?? undefined,
    headers: record.headersMask ?? undefined,
  });
  mcpEditing.value = true;
}

async function saveMcpInline() {
  if (!mcpForm.name) {
    message.warning('MCP 服务名必填');
    return;
  }
  try {
    await saveBidAgentMcp({ ...mcpForm });
    message.success(mcpForm.id ? 'MCP 服务已更新' : 'MCP 服务已新增');
    mcpEditing.value = false;
    await loadMcpOptions();
  } catch (error: any) {
    message.error(error?.message ?? 'MCP 服务保存失败');
  }
}

function deleteMcpInline(record: BidAgentPlatformApi.McpServer) {
  Modal.confirm({
    title: `删除 MCP 服务「${record.name}」？`,
    content: '删除后已挂载该服务的智能体关联会失效，确认继续？',
    okText: '删除',
    okType: 'danger',
    cancelText: '取消',
    async onOk() {
      try {
        await deleteBidAgentMcp(record.id as number);
        message.success('MCP 服务已删除');
        await loadMcpOptions();
      } catch (error: any) {
        message.error(error?.message ?? 'MCP 服务删除失败');
      }
    },
  });
}

async function checkMcpInline(record: BidAgentPlatformApi.McpServer) {
  mcpChecking.value = true;
  try {
    mcpCheckResult.value = await checkBidAgentMcp({ id: record.id as number });
    mcpCheckOpen.value = true;
  } catch (error: any) {
    message.error(error?.message ?? 'MCP 连通性测试失败');
  } finally {
    mcpChecking.value = false;
  }
}
// —— 调试测试 ——
const previewing = ref(false);
const previewInput = ref('');
const previewOutput = ref('');
const previewSource = ref('');

const KNOWLEDGE_ROLE_OPTIONS = ['TENDER', 'FACT', 'TEMPLATE'].map((v) => ({ label: v, value: v }));
const ROLE_COLORS: Record<string, string> = {
  TENDER: 'chip-blue',
  FACT: 'chip-purple',
  TEMPLATE: 'chip-amber',
};

// 只读工具白名单：内置工具 = 已选技能 toolWhitelist 并集；外部工具 = 已挂载 MCP 授权工具并集
const builtinTools = computed(() => {
  const set = new Set<string>();
  for (const id of selectedSkillIds.value) {
    const skill = allSkills.value.find((s) => s.id === id);
    (skill?.toolWhitelist ?? '')
      .split(',')
      .map((t) => t.trim())
      .filter(Boolean)
      .forEach((t) => set.add(t));
  }
  return [...set];
});

const mcpTools = computed(() => {
  const set = new Set<string>();
  for (const server of mcpServers.value) {
    const sel = mcpSelections[String(server.id)];
    if (!sel?.bound) {
      continue;
    }
    const names = sel.toolNames.length ? sel.toolNames : parseToolSnapshot(server.toolSnapshotJson).map((t) => t.name);
    names.forEach((t) => set.add(t));
  }
  return [...set];
});

async function loadProfiles() {
  profiles.value = await listBidAgentProfile();
}

async function openEdit(profile: BidAgentApi.Profile) {
  isReadonly.value = profile.editable !== 1;
  if (isReadonly.value) {
    message.info('该智能体提示词被锁定为只读；技能 / MCP 仍可配置');
  }
  Object.assign(form, {
    id: profile.id,
    taskType: profile.taskType,
    name: profile.name,
    description: profile.description ?? undefined,
    systemPrompt: profile.systemPrompt ?? undefined,
    model: profile.model ?? undefined,
    temperature: profile.temperature ?? undefined,
    maxTokens: profile.maxTokens ?? undefined,
    knowledgeRoles: profile.knowledgeRoleList ?? [],
    skillAutoAttach: profile.skillAutoAttach ?? 1,
    status: profile.status ?? 0,
    remark: profile.remark ?? undefined,
  });
  previewOutput.value = '';
  previewSource.value = '';
  editOpen.value = true;
  await Promise.all([loadSkillOptions(), loadMcpOptions()]);
}

async function loadSkillOptions() {
  try {
    const res = await getBidAgentSkillPage({ pageNo: 1, pageSize: 200 });
    allSkills.value = res.list ?? [];
  } catch {
    allSkills.value = [];
  }
  if (!form.id) {
    selectedSkillIds.value = [];
    return;
  }
  try {
    const mounted = await listBidAgentProfileSkills(form.id, form.taskType);
    selectedSkillIds.value = mounted.map((s) => s.id);
  } catch {
    selectedSkillIds.value = [];
  }
}

async function loadMcpOptions() {
  try {
    const res = await getBidAgentMcpPage({ pageNo: 1, pageSize: 200 });
    mcpServers.value = res.list ?? [];
  } catch {
    mcpServers.value = [];
  }
  let bindings: BidAgentPlatformApi.ProfileMcpBinding[] = [];
  if (form.id) {
    try {
      bindings = await listBidAgentProfileMcp(form.id);
    } catch {
      bindings = [];
    }
  }
  Object.keys(mcpSelections).forEach((key) => delete mcpSelections[key]);
  for (const server of mcpServers.value) {
    const bound = bindings.find((b) => b.mcpServerId === server.id);
    mcpSelections[String(server.id)] = { bound: Boolean(bound), toolNames: bound?.toolNames ?? [] };
  }
}

function toggleMcpBound(server: BidAgentPlatformApi.McpServer) {
  const sel = mcpSelections[String(server.id)];
  if (!sel) {
    return;
  }
  sel.bound = !sel.bound;
  // 首次挂载默认授权全部工具，之后可按需勾选子集
  if (sel.bound && !sel.toolNames.length) {
    sel.toolNames = parseToolSnapshot(server.toolSnapshotJson).map((t) => t.name);
  }
}

function mcpToolsOf(server: BidAgentPlatformApi.McpServer): string[] {
  return mcpSelections[String(server.id)]?.toolNames ?? [];
}

function setMcpTools(server: BidAgentPlatformApi.McpServer, toolNames: string[]) {
  const sel = mcpSelections[String(server.id)];
  if (sel) {
    sel.toolNames = toolNames;
  }
}

async function handleSave() {
  saving.value = true;
  try {
    let pid = form.id as number | undefined;
    if (!isReadonly.value) {
      pid = await saveBidAgentProfile({
        id: form.id,
        taskType: form.taskType,
        name: form.name,
        description: form.description,
        systemPrompt: form.systemPrompt,
        model: form.model,
        temperature: form.temperature,
        maxTokens: form.maxTokens,
        knowledgeRoles: form.knowledgeRoles.length ? form.knowledgeRoles.join(',') : undefined,
        skillAutoAttach: form.skillAutoAttach,
        status: form.status,
        remark: form.remark,
        // 不传 toolWhitelist：工具白名单由运行时按技能并集自动合成（禁止手写）
      });
    }
    if (pid) {
      await saveBidAgentProfileSkills(pid, selectedSkillIds.value);
      const bindings: BidAgentPlatformApi.ProfileMcpBinding[] = mcpServers.value
        .filter((s) => mcpSelections[String(s.id)]?.bound)
        .map((s) => {
          const sel = mcpSelections[String(s.id)];
          return { mcpServerId: s.id as number, toolNames: sel?.toolNames ?? [] };
        });
      await saveBidAgentProfileMcp(pid, bindings);
    }
    message.success('已保存，下次生成任务时生效');
    editOpen.value = false;
    await loadProfiles();
  } catch (error: any) {
    message.error(error?.message ?? '保存失败');
  } finally {
    saving.value = false;
  }
}

async function handlePreview() {
  if (!previewInput.value) {
    message.warning('先粘贴一段招标文件片段');
    return;
  }
  if (previewing.value) return;
  previewing.value = true;
  try {
    const res = await previewBidAgent({ taskType: form.taskType, input: previewInput.value, withRetrieve: false });
    previewOutput.value = res.output ?? '';
    previewSource.value = res.promptSource ?? '';
  } finally {
    previewing.value = false;
  }
}

onMounted(loadProfiles);
</script>

<template>
  <Page auto-content-height>
    <!-- 页面标题 -->
    <div class="page-header">
      <div class="page-header-main">
        <div class="page-eyebrow">AGENT PROFILES</div>
        <h1 class="page-title">智能体管理</h1>
      </div>
    </div>

    <Card :bordered="false" class="surface-card">
      <template #title>
        <div class="card-title">
          <IconifyIcon icon="lucide:bot" class="card-title-icon" />
          <span>已注册的智能体</span>
          <Tag class="card-title-tag">共 {{ profiles.length }} 个</Tag>
        </div>
      </template>
      <Table :data-source="profiles" :pagination="false" size="middle" row-key="taskType">
        <Table.Column title="智能体" :width="180">
          <template #default="{ record }">
            <div class="cell-strong">{{ record.name }}</div>
            <div class="cell-sub">{{ record.taskType }}</div>
          </template>
        </Table.Column>
        <Table.Column title="职责" data-index="description">
          <template #default="{ record }">
            <span class="cell-ellipsis" :title="record.description ?? ''">{{ record.description || '—' }}</span>
          </template>
        </Table.Column>
        <Table.Column title="模型 / 温度" :width="200">
          <template #default="{ record }">
            <code v-if="record.model" class="code-text">{{ record.model }}</code>
          </template>
        </Table.Column>
        <Table.Column title="挂载知识库角色" :width="240">
          <template #default="{ record }">
            <Space size="small" wrap>
              <Tag
                v-for="role in record.knowledgeRoleList || []"
                :key="role"
                :class="ROLE_COLORS[role] || 'chip-neutral'"
              >
                {{ role }}
              </Tag>
              <span v-if="!(record.knowledgeRoleList || []).length" class="cell-sub">未挂载</span>
            </Space>
          </template>
        </Table.Column>
        <Table.Column title="生效方式" :width="160">
          <template #default="{ record }">
            <Tag :class="record.editable === 1 ? 'chip-success' : 'chip-neutral'">
              {{ record.editable === 1 ? '后台可编辑' : '只读锁定' }}
            </Tag>
          </template>
        </Table.Column>
        <Table.Column title="状态" :width="100">
          <template #default="{ record }">
            <span class="status-dot" :class="record.status === 0 ? 'is-on' : 'is-off'"></span>
            <span>{{ record.status === 0 ? '启用' : '停用' }}</span>
          </template>
        </Table.Column>
        <Table.Column title="操作" :width="140" align="right">
          <template #default="{ record }">
            <Button type="link" size="small" @click="openEdit(record)">
              <template #icon><IconifyIcon icon="lucide:settings-2" /></template>
              配置
            </Button>
          </template>
        </Table.Column>
      </Table>
    </Card>

    <!-- 编辑弹窗 -->
    <Modal
      v-model:open="editOpen"
      :title="`配置智能体：${form.name || form.taskType}`"
      :width="1040"
      :footer="null"
      :destroy-on-close="true"
    >
      <Alert
        v-if="isReadonly"
        class="callout-info mb-4"
        type="info"
        show-icon
        message="只读提示词"
        description="该智能体提示词被锁定为只读，不可修改；技能 / MCP 仍可在此配置"
      />

      <Tabs class="agent-tabs" :tab-bar-style="{ marginBottom: '20px' }">
        <!-- Tab1：基础与提示词 -->
        <TabPane key="basic" tab="基础与提示词">
          <Form layout="vertical" class="form-grid">
            <Form.Item label="智能体名称">
              <Input v-model:value="form.name" :disabled="isReadonly" placeholder="如：标书大纲生成" />
            </Form.Item>
            <Form.Item label="任务类型">
              <Input v-model:value="form.taskType" disabled />
            </Form.Item>
            <Form.Item label="状态">
              <div class="switch-row">
                <span class="switch-label">{{ form.status === 0 ? '启用' : '停用' }}</span>
                <Switch
                  v-model:checked="form.status"
                  :checked-value="0"
                  :un-checked-value="1"
                  :disabled="isReadonly"
                  checked-children="开启"
                  un-checked-children="停用"
                />
              </div>
            </Form.Item>
            <Form.Item label="业务说明" class="form-grid-full">
              <Textarea v-model:value="form.description" :rows="2" :disabled="isReadonly" placeholder="本智能体在标书生成流程中负责什么" />
            </Form.Item>
            <Form.Item label="系统提示词（底层系统 Prompt）" class="form-grid-full">
              <Textarea
                v-model:value="form.systemPrompt"
                :rows="12"
                :disabled="isReadonly"
                placeholder="留空使用内置默认提示词"
              />
            </Form.Item>
            <Form.Item label="备注" class="form-grid-full">
              <Textarea v-model:value="form.remark" :rows="2" :disabled="isReadonly" />
            </Form.Item>
          </Form>
        </TabPane>

        <!-- Tab2：运行与工具配置 -->
        <TabPane key="tools" tab="运行与工具配置">
          <!-- 内置技能 -->
          <Card :bordered="false" class="tool-card">
            <template #title>
              <div class="card-title">
                <IconifyIcon icon="lucide:layers" class="card-title-icon" />
                <span>技能挂载与管理</span>
                <Tag class="card-title-tag">{{ selectedSkillIds.length }} / {{ allSkills.length }}</Tag>
                <Button type="link" size="small" style="margin-left: auto" @click="startCreateSkill">新增技能</Button>
              </div>
            </template>
            <Form layout="vertical">
              <Form.Item label="挂载技能（多选，禁止手写工具名）">
                <Select
                  v-model:value="selectedSkillIds"
                  :options="allSkills.map((s) => ({ label: `${s.name}（${s.code}）`, value: s.id as number }))"
                  mode="multiple"
                  placeholder="选择要挂载的原子技能"
                  option-filter-prop="label"
                  style="width: 100%"
                />
              </Form.Item>
              <Form.Item label="自动挂载推荐技能">
                <div class="switch-row">
                  <span class="switch-label">{{ form.skillAutoAttach === 1 ? '推荐技能自动挂载' : '仅手动挂载' }}</span>
                  <Switch
                    v-model:checked="form.skillAutoAttach"
                    :checked-value="1"
                    :un-checked-value="0"
                    :disabled="isReadonly"
                  />
                </div>
              </Form.Item>
            </Form>
            <Table
              class="management-table"
              :data-source="allSkills"
              :pagination="false"
              :scroll="{ y: 220 }"
              size="small"
              :row-key="(row: BidAgentPlatformApi.Skill) => String(row.id)"
            >
              <Table.Column title="技能" :width="220">
                <template #default="{ record }">
                  <div class="cell-strong">
                    {{ record.name }}
                    <Tag v-if="isPlatformSkill(record)" class="chip-neutral skill-origin">平台预置</Tag>
                  </div>
                  <div class="cell-sub"><code>{{ record.code }}</code></div>
                </template>
              </Table.Column>
              <Table.Column title="适用任务" data-index="applicableTaskTypes" />
              <Table.Column title="状态" :width="90" align="center">
                <template #default="{ record }">
                  <Tag :class="record.status === 0 ? 'chip-success' : 'chip-neutral'">
                    {{ record.status === 0 ? '启用' : '停用' }}
                  </Tag>
                </template>
              </Table.Column>
              <Table.Column title="操作" :width="120" align="right">
                <template #default="{ record }">
                  <Space size="small">
                    <Button type="link" size="small" :disabled="isPlatformSkill(record)" @click="startEditSkill(record)">编辑</Button>
                    <Button type="link" size="small" danger :disabled="isPlatformSkill(record)" @click="deleteSkillInline(record)">删除</Button>
                  </Space>
                </template>
              </Table.Column>
            </Table>
          </Card>

          <!-- MCP 服务与挂载 -->
          <Card :bordered="false" class="tool-card mt-4">
            <template #title>
              <div class="card-title">
                <IconifyIcon icon="lucide:plug-zap" class="card-title-icon" />
                <span>MCP 服务与挂载</span>
                <Tag class="card-title-tag">高级能力</Tag>
                <Button type="link" size="small" style="margin-left: auto" @click="startCreateMcp">注册服务</Button>
              </div>
            </template>
            <Alert
              class="callout-info mb-3"
              type="info"
              show-icon
              message="MCP 服务可在本卡片直接注册、编辑、删除和测试；这里同时配置当前智能体的挂载与工具授权。未勾选工具 = 授权全部工具。"
            />
            <Empty v-if="!mcpServers.length" description="暂无 MCP 服务，可点击右上角「注册服务」新增" />
            <div v-else class="mcp-grid">
              <div
                v-for="server in mcpServers"
                :key="server.id"
                class="mcp-card"
                :class="{ 'is-bound': mcpSelections[String(server.id)]?.bound }"
              >
                <div class="mcp-card-head">
                  <Switch
                    :checked="mcpSelections[String(server.id)]?.bound"
                    @change="toggleMcpBound(server)"
                  />
                  <div class="mcp-card-meta">
                    <div class="cell-strong">{{ server.name }}</div>
                    <div class="cell-sub">{{ server.url || server.command || server.transport }}</div>
                  </div>
                  <Tag class="chip-blue">{{ server.transport }}</Tag>
                  <Space size="small" class="mcp-card-actions">
                    <Button type="link" size="small" :loading="mcpChecking" @click="checkMcpInline(server)">测试</Button>
                    <Button type="link" size="small" @click="startEditMcp(server)">编辑</Button>
                    <Button type="link" size="small" danger @click="deleteMcpInline(server)">删除</Button>
                  </Space>
                </div>
                <template v-if="mcpSelections[String(server.id)]?.bound">
                  <Divider class="mcp-divider" />
                  <div class="mcp-tools-head">授权工具子集</div>
                  <CheckboxGroup
                    v-if="parseToolSnapshot(server.toolSnapshotJson).length"
                    :value="mcpToolsOf(server)"
                    :options="parseToolSnapshot(server.toolSnapshotJson).map((t) => ({ label: t.name, value: t.name }))"
                    @change="(vals: unknown[]) => setMcpTools(server, vals as string[])"
                  />
                  <div v-else class="cell-sub">该服务暂未发现工具（请先执行「测试」）</div>
                </template>
              </div>
            </div>
          </Card>

          <!-- 工具白名单只读预览 -->
          <Card :bordered="false" class="tool-card mt-4">
            <template #title>
              <div class="card-title">
                <IconifyIcon icon="lucide:list-checks" class="card-title-icon" />
                <span>工具白名单</span>
                <Tag class="card-title-tag">系统自动合成 · 只读</Tag>
              </div>
            </template>
            <Space direction="vertical" style="width: 100%" :size="14">
              <div>
                <div class="tool-section-label">
                  <IconifyIcon icon="lucide:layers" class="tool-section-icon" />
                  内置工具（来自选中技能）
                </div>
                <div class="tool-chip-wrap">
                  <Tag v-for="t in builtinTools" :key="t" class="chip-blue">{{ t }}</Tag>
                  <span v-if="!builtinTools.length" class="cell-sub">无</span>
                </div>
              </div>
              <div>
                <div class="tool-section-label">
                  <IconifyIcon icon="lucide:plug-zap" class="tool-section-icon" />
                  外部 MCP 工具（已授权子集）
                </div>
                <div class="tool-chip-wrap">
                  <Tag v-for="t in mcpTools" :key="t" class="chip-purple">{{ t }}</Tag>
                  <span v-if="!mcpTools.length" class="cell-sub">无</span>
                </div>
              </div>
            </Space>
          </Card>

          <!-- 模型基础参数 -->
          <Card :bordered="false" class="tool-card mt-4">
            <template #title>
              <div class="card-title">
                <IconifyIcon icon="lucide:cpu" class="card-title-icon" />
                <span>模型基础参数</span>
              </div>
            </template>
            <Form layout="vertical" class="form-grid">
              <Form.Item label="模型（留空 = 默认）">
                <Input v-model:value="form.model" :disabled="isReadonly" placeholder="如：qwen-plus" />
              </Form.Item>
              <Form.Item label="温度">
                <InputNumber v-model:value="form.temperature" :min="0" :max="2" :step="0.1" :disabled="isReadonly" style="width: 100%" />
              </Form.Item>
              <Form.Item label="最大 Token">
                <InputNumber v-model:value="form.maxTokens" :min="256" :disabled="isReadonly" style="width: 100%" placeholder="留空=全局云端模型配置" />
              </Form.Item>
              <Form.Item label="知识库角色（多选）">
                <Select
                  v-model:value="form.knowledgeRoles"
                  :options="KNOWLEDGE_ROLE_OPTIONS"
                  mode="multiple"
                  :disabled="isReadonly"
                  placeholder="TENDER/FACT/TEMPLATE"
                />
              </Form.Item>
            </Form>
          </Card>
        </TabPane>

        <!-- Tab3：调试测试 -->
        <TabPane key="debug" tab="试跑验证">
          <Alert
            type="warning"
            show-icon
            class="callout-warning mb-4"
            message="试跑前置"
            description="请先在「智能体管理 → 云端模型」配置并启用可用的 base-url、API Key 和模型名；超时或限流会自动重试。"
          />
          <Card :bordered="false" class="surface-card">
            <div class="debug-row">
              <div class="debug-label">输入片段</div>
              <Textarea
                v-model:value="previewInput"
                :rows="6"
                placeholder="粘贴一段招标文件片段，用当前提示词试跑"
              />
              <div class="debug-actions">
                <Button type="primary" :loading="previewing" @click="handlePreview">
                  <template #icon><IconifyIcon icon="lucide:play" /></template>
                  试跑
                </Button>
              </div>
            </div>
            <div v-if="previewSource" class="debug-source">
              <IconifyIcon icon="lucide:info" />
              提示词来源：
              <Tag :class="previewSource === 'BID_AGENT' ? 'chip-success' : 'chip-neutral'">
                {{ previewSource === 'BID_AGENT' ? '后台配置' : 'Agent 内置基线' }}
              </Tag>
            </div>
            <div v-if="previewOutput" class="debug-output">
              <div class="debug-label">输出</div>
              <div class="debug-output-body">{{ previewOutput }}</div>
            </div>
          </Card>
        </TabPane>

        <!-- Tab4：版本与审计 -->
        <TabPane key="audit" tab="版本与审计">
          <Empty description="版本与审计链路后端未建，后续接入">
            <template #image>
              <IconifyIcon icon="lucide:clock" style="font-size: 48px; color: hsl(var(--muted-foreground))" />
            </template>
          </Empty>
        </TabPane>
      </Tabs>

      <div class="modal-footer">
        <Space>
          <Button @click="editOpen = false">取消</Button>
          <Button type="primary" :loading="saving" @click="handleSave">
            <template #icon><IconifyIcon icon="lucide:save" /></template>
            保存
          </Button>
        </Space>
      </div>
    </Modal>
    <!-- 技能新增/编辑（智能体管理内嵌） -->
    <Modal
      v-model:open="skillEditing"
      :title="skillForm.id ? '编辑技能' : '新增技能'"
      :width="780"
      ok-text="保存"
      cancel-text="取消"
      @ok="saveSkillInline"
    >
      <Form layout="vertical" class="form-grid">
        <Form.Item label="技能编码（唯一）" required>
          <Input v-model:value="skillForm.code" :disabled="Boolean(skillForm.id)" placeholder="建议英文短码，如 bid-outline-summary" />
        </Form.Item>
        <Form.Item label="技能名称" required>
          <Input v-model:value="skillForm.name" placeholder="如：大纲摘要" />
        </Form.Item>
        <Form.Item label="排序">
          <InputNumber v-model:value="skillForm.sort" :min="0" style="width: 100%" />
        </Form.Item>
        <Form.Item label="描述" class="form-grid-full">
          <Textarea v-model:value="skillForm.description" :rows="2" placeholder="技能用途简述" />
        </Form.Item>
        <Form.Item label="提示词片段（注入 system prompt）" class="form-grid-full">
          <Textarea v-model:value="skillForm.promptFragment" :rows="5" />
        </Form.Item>
        <Form.Item label="适用任务类型（可多选）">
          <Select v-model:value="skillForm.applicableTaskTypes" :options="TASK_TYPE_OPTIONS" mode="multiple" />
        </Form.Item>
        <Form.Item label="建议知识库角色（逗号分隔）">
          <Input v-model:value="skillForm.knowledgeRoles" placeholder="TENDER,FACT,TEMPLATE" />
        </Form.Item>
        <Form.Item label="建议/限定工具白名单（逗号分隔）" class="form-grid-full">
          <Input v-model:value="skillForm.toolWhitelist" placeholder="bid-retrieve,bid-docx" />
        </Form.Item>
        <Form.Item label="自动推荐挂载">
          <div class="switch-row">
            <span class="switch-label">{{ skillForm.autoAttach === 1 ? '推荐自动挂载' : '仅手动挂载' }}</span>
            <Switch v-model:checked="skillForm.autoAttach" :checked-value="1" :un-checked-value="0" />
          </div>
        </Form.Item>
        <Form.Item label="状态">
          <div class="switch-row">
            <span class="switch-label">{{ skillForm.status === 0 ? '启用' : '停用' }}</span>
            <Switch v-model:checked="skillForm.status" :checked-value="0" :un-checked-value="1" />
          </div>
        </Form.Item>
        <Form.Item label="备注" class="form-grid-full">
          <Textarea v-model:value="skillForm.remark" :rows="2" />
        </Form.Item>
      </Form>
    </Modal>

    <!-- MCP 服务新增/编辑（智能体管理内嵌） -->
    <Modal
      v-model:open="mcpEditing"
      :title="mcpForm.id ? '编辑 MCP 服务' : '注册 MCP 服务'"
      :width="780"
      ok-text="保存"
      cancel-text="取消"
      @ok="saveMcpInline"
    >
      <Form layout="vertical" class="form-grid">
        <Form.Item label="服务名" required>
          <Input v-model:value="mcpForm.name" placeholder="如：filesystem-mcp" />
        </Form.Item>
        <Form.Item label="传输方式">
          <Select v-model:value="mcpForm.transport" :options="MCP_TRANSPORT_OPTIONS" />
        </Form.Item>
        <Form.Item label="超时（秒）">
          <InputNumber v-model:value="mcpForm.timeoutSeconds" :min="1" style="width: 100%" />
        </Form.Item>
        <Form.Item label="状态">
          <div class="switch-row">
            <span class="switch-label">{{ mcpForm.enabled === 1 ? '启用' : '停用' }}</span>
            <Switch v-model:checked="mcpForm.enabled" :checked-value="1" :un-checked-value="0" />
          </div>
        </Form.Item>
        <template v-if="mcpForm.transport === 'stdio'">
          <Form.Item label="命令" class="form-grid-full">
            <Input v-model:value="mcpForm.command" placeholder="如 npx / node" />
          </Form.Item>
          <Form.Item label="参数（JSON 数组字符串）" class="form-grid-full">
            <Input v-model:value="mcpForm.args" placeholder='["-y", "@org/mcp-server"]' />
          </Form.Item>
          <Form.Item label="环境变量（JSON 对象字符串；留空/**** 不修改）" class="form-grid-full">
            <Textarea v-model:value="mcpForm.env" :rows="4" placeholder='{"API_KEY":"..."}' />
          </Form.Item>
        </template>
        <template v-else>
          <Form.Item label="服务端点地址" class="form-grid-full">
            <Input v-model:value="mcpForm.url" placeholder="https://host/mcp 或 /sse" />
          </Form.Item>
          <Form.Item label="请求头（JSON 对象字符串；留空/**** 不修改）" class="form-grid-full">
            <Textarea v-model:value="mcpForm.headers" :rows="4" placeholder='{"Authorization":"Bearer ..."}' />
          </Form.Item>
        </template>
        <Form.Item label="备注" class="form-grid-full">
          <Textarea v-model:value="mcpForm.remark" :rows="2" />
        </Form.Item>
      </Form>
    </Modal>

    <!-- MCP 连通性结果 -->
    <Modal v-model:open="mcpCheckOpen" title="MCP 连通性测试结果" :footer="null" :width="620">
      <Alert
        :type="mcpCheckResult?.ok ? 'success' : 'error'"
        show-icon
        :message="mcpCheckResult?.message ?? ''"
        class="mb-3"
      />
      <Table
        v-if="mcpCheckResult?.tools?.length"
        :data-source="mcpCheckResult.tools"
        :pagination="false"
        size="small"
        :row-key="(row: { name: string }) => row.name"
      >
        <Table.Column title="工具名" data-index="name" :width="200" />
        <Table.Column title="描述" data-index="description" />
      </Table>
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
.tool-card :deep(.ant-card-head) {
  padding: 12px 20px;
  min-height: 48px;
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
.cell-sub { font-size: 11.5px; color: hsl(var(--muted-foreground)); }
.cell-ellipsis {
  display: inline-block;
  max-width: 100%;
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

.chip-success,
.chip-danger,
.chip-neutral,
.chip-blue,
.chip-purple,
.chip-amber {
  border-radius: 999px;
  padding: 1px 10px;
  font-weight: 500;
  border: 0;
  font-size: 12px;
}
.chip-success { background: hsl(var(--success) / 0.12); color: hsl(var(--success)); }
.chip-neutral { background: hsl(var(--muted) / 0.5); color: hsl(var(--muted-foreground)); }
.chip-blue    { background: hsl(212 90% 95%); color: hsl(212 80% 38%); }
.chip-purple  { background: hsl(265 80% 95%); color: hsl(265 60% 40%); }
.chip-amber   { background: hsl(35 95% 92%); color: hsl(30 80% 38%); }

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

/* ============ 表单网格 ============ */
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

/* ============ Agent 配置 Tabs ============ */
.agent-tabs :deep(.ant-tabs-tab) {
  padding: 8px 14px;
  font-size: 13.5px;
}
.agent-tabs :deep(.ant-tabs-tab + .ant-tabs-tab) { margin: 0 0 0 8px; }

/* ============ 内嵌管理表格 ============ */
.management-table { margin-top: 16px; }
.skill-origin {
  margin-left: 6px;
  font-size: 11px;
  padding: 0 7px;
  vertical-align: middle;
}

/* ============ MCP 卡片 ============ */
.mcp-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 12px;
}
.mcp-card {
  border: 1px solid hsl(var(--border));
  border-radius: 10px;
  padding: 12px 14px;
  background: hsl(var(--card));
  transition: border-color 0.18s, box-shadow 0.18s;
}
.mcp-card.is-bound {
  border-color: hsl(var(--primary) / 0.5);
  box-shadow: 0 0 0 3px hsl(var(--primary) / 0.08);
}
.mcp-card-head {
  display: flex;
  align-items: center;
  gap: 10px;
}
.mcp-card-meta { flex: 1; min-width: 0; }
.mcp-card-actions {
  flex: none;
  white-space: nowrap;
}
.mcp-divider { margin: 12px 0 8px; }
.mcp-tools-head {
  font-size: 12.5px;
  color: hsl(var(--muted-foreground));
  margin-bottom: 8px;
}

/* ============ 工具分组 ============ */
.tool-section-label {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: hsl(var(--muted-foreground));
  margin-bottom: 8px;
}
.tool-section-icon { font-size: 14px; color: hsl(var(--primary)); }
.tool-chip-wrap {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

/* ============ 调试区 ============ */
.debug-row { margin-bottom: 12px; }
.debug-label {
  font-size: 13px;
  font-weight: 600;
  color: hsl(var(--foreground));
  margin-bottom: 6px;
}
.debug-actions {
  margin-top: 10px;
  display: flex;
  justify-content: flex-end;
}
.debug-source {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  background: hsl(var(--muted) / 0.4);
  border-radius: 8px;
  font-size: 12.5px;
  color: hsl(var(--muted-foreground));
  margin: 8px 0 14px;
}
.debug-output-body {
  margin-top: 6px;
  padding: 12px 14px;
  background: hsl(var(--muted) / 0.3);
  border-radius: 8px;
  border-left: 3px solid hsl(var(--primary));
  font-size: 13px;
  line-height: 1.65;
  white-space: pre-wrap;
  word-break: break-word;
}

/* ============ Callout ============ */
.callout-info { border-radius: 10px; }
.callout-warning { border-radius: 10px; }

/* ============ 弹窗底部 ============ */
.modal-footer {
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid hsl(var(--border));
  display: flex;
  justify-content: flex-end;
}
</style>