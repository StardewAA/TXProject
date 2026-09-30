<!--
  标书智能体 · MCP 服务管理（全局资源，安全层）
  定位：外部 MCP 服务在此统一注册/自检；智能体编辑页只引用、不注册、不存密钥。
  契约：src/api/bidding/bidAgent/platform.ts（对应 BidAgentMcpController）
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
  checkBidAgentMcp,
  deleteBidAgentMcp,
  getBidAgentMcpPage,
  parseToolSnapshot,
  saveBidAgentMcp,
  type BidAgentPlatformApi,
} from '#/api/bidding/bidAgent/platform';

const servers = ref<BidAgentPlatformApi.McpServer[]>([]);
const total = ref(0);
const loading = ref(false);
const query = reactive({
  name: undefined as string | undefined,
  transport: undefined as string | undefined,
  enabled: undefined as number | undefined,
  pageNo: 1,
  pageSize: 10,
});

const editing = ref(false);
const form = reactive<BidAgentPlatformApi.McpServer>({
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

// 连通性测试结果
const checking = ref(false);
const checkOpen = ref(false);
const checkResult = ref<BidAgentPlatformApi.McpCheckResp>();

const TRANSPORT_OPTIONS = [
  { label: 'streamable-http', value: 'streamable-http' },
  { label: 'sse', value: 'sse' },
  { label: 'stdio', value: 'stdio' },
];

const TRANSPORT_HINT: Record<string, string> = {
  'streamable-http': '通过 HTTP POST 双向流式通信，主流 MCP 协议',
  sse: 'Server-Sent Events 单向流，适合只读场景',
  stdio: '本地进程 stdin/stdout，适合开发与本机集成',
};

async function loadServers() {
  loading.value = true;
  try {
    const res = await getBidAgentMcpPage({ ...query });
    servers.value = res.list ?? [];
    total.value = res.total;
  } catch (error: any) {
    message.error(error?.message ?? 'MCP 服务列表加载失败');
  } finally {
    loading.value = false;
  }
}

function resetForm() {
  Object.assign(form, {
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
  editing.value = false;
}

function startCreate() {
  resetForm();
  editing.value = true;
}

function startEdit(record: BidAgentPlatformApi.McpServer) {
  Object.assign(form, {
    ...record,
    // env/headers 脱敏回显：留空或 **** 表示不修改（后端据此跳过更新）
    env: record.envMask ?? undefined,
    headers: record.headersMask ?? undefined,
  });
  editing.value = true;
}

async function handleSave() {
  if (!form.name) {
    message.warning('MCP 服务名必填');
    return;
  }
  try {
    await saveBidAgentMcp({ ...form });
    message.success(form.id ? 'MCP 服务已更新' : 'MCP 服务已新增');
    editing.value = false;
    await loadServers();
  } catch (error: any) {
    message.error(error?.message ?? 'MCP 服务保存失败');
  }
}

function handleDelete(record: BidAgentPlatformApi.McpServer) {
  Modal.confirm({
    title: `删除 MCP 服务「${record.name}」？`,
    content: '删除后已挂载该服务的智能体关联会失效，确认继续？',
    okText: '删除',
    okType: 'danger',
    cancelText: '取消',
    async onOk() {
      try {
        await deleteBidAgentMcp(record.id as number);
        message.success('已删除');
        await loadServers();
      } catch (error: any) {
        message.error(error?.message ?? '删除失败');
      }
    },
  });
}

async function handleCheck(record: BidAgentPlatformApi.McpServer) {
  checking.value = true;
  try {
    checkResult.value = await checkBidAgentMcp({ id: record.id as number });
    checkOpen.value = true;
  } catch (error: any) {
    message.error(error?.message ?? '自检失败');
  } finally {
    checking.value = false;
  }
}

onMounted(loadServers);
</script>

<template>
  <Page auto-content-height>
    <div class="page-header">
      <div class="page-header-main">
        <div class="page-eyebrow">GLOBAL RESOURCE · SECURITY</div>
        <h1 class="page-title">MCP 服务管理</h1>
        <p class="page-desc">外部 MCP 服务统一注册与密钥托管；智能体页只引用、不存密钥</p>
      </div>
      <div class="page-header-actions">
        <Button type="primary" @click="startCreate">
          <template #icon><IconifyIcon icon="lucide:plus" /></template>
          注册服务
        </Button>
      </div>
    </div>

    <!-- 筛选 -->
    <Card :bordered="false" class="surface-card filter-card">
      <Space wrap>
        <Input v-model:value="query.name" placeholder="服务名" style="width: 180px" allow-clear @press-enter="loadServers" />
        <Select
          v-model:value="query.transport"
          :options="TRANSPORT_OPTIONS"
          allow-clear
          placeholder="传输方式"
          style="width: 180px"
        />
        <Select
          v-model:value="query.enabled"
          :options="[
            { label: '启用', value: 1 },
            { label: '停用', value: 0 },
          ]"
          allow-clear
          placeholder="状态"
          style="width: 120px"
        />
        <Button type="primary" @click="loadServers">
          <template #icon><IconifyIcon icon="lucide:search" /></template>
          查询
        </Button>
      </Space>
    </Card>

    <!-- 列表 -->
    <Card :bordered="false" class="surface-card">
      <template #title>
        <div class="card-title">
          <IconifyIcon icon="lucide:plug-zap" class="card-title-icon" />
          <span>已注册 MCP 服务</span>
          <Tag class="card-title-tag">共 {{ total }} 个</Tag>
        </div>
      </template>
      <Table
        :data-source="servers"
        :loading="loading"
        :pagination="{
          current: query.pageNo,
          pageSize: query.pageSize,
          total,
          onChange: (p: number) => {
            query.pageNo = p;
            loadServers();
          },
        }"
        :row-key="(row: BidAgentPlatformApi.McpServer) => String(row.id)"
        size="middle"
      >
        <Table.Column title="服务名" :width="200">
          <template #default="{ record }">
            <div class="cell-strong">{{ record.name }}</div>
            <div class="cell-sub">#{{ record.id }}</div>
          </template>
        </Table.Column>
        <Table.Column title="传输" data-index="transport" :width="150">
          <template #default="{ record }">
            <Tag class="chip-blue">{{ record.transport }}</Tag>
          </template>
        </Table.Column>
        <Table.Column title="端点 / 命令" :width="240">
          <template #default="{ record }">
            <code v-if="record.url" class="code-text cell-ellipsis" :title="record.url">{{ record.url }}</code>
            <code v-else-if="record.command" class="code-text">{{ record.command }}</code>
            <span v-else class="cell-sub">—</span>
          </template>
        </Table.Column>
        <Table.Column title="工具数" :width="100">
          <template #default="{ record }">
            <Tag class="chip-purple">
              <IconifyIcon icon="lucide:wrench" class="chip-icon" />
              {{ parseToolSnapshot(record.toolSnapshotJson).length }}
            </Tag>
          </template>
        </Table.Column>
        <Table.Column title="状态" :width="100">
          <template #default="{ record }">
            <span class="status-dot" :class="record.enabled === 1 ? 'is-on' : 'is-off'" />
            <span>{{ record.enabled === 1 ? '启用' : '停用' }}</span>
          </template>
        </Table.Column>
        <Table.Column title="最近自检" data-index="lastCheckResult">
          <template #default="{ record }">
            <span class="cell-ellipsis" :title="record.lastCheckResult ?? ''">
              {{ record.lastCheckResult || '—' }}
            </span>
          </template>
        </Table.Column>
        <Table.Column title="操作" :width="180" align="right">
          <template #default="{ record }">
            <Space size="small">
              <Button type="link" size="small" :loading="checking" @click="handleCheck(record)">测试</Button>
              <Button type="link" size="small" @click="startEdit(record)">编辑</Button>
              <Button type="link" size="small" danger @click="handleDelete(record)">删除</Button>
            </Space>
          </template>
        </Table.Column>
      </Table>
    </Card>

    <!-- 编辑 / 新增 -->
    <Modal
      v-model:open="editing"
      :title="form.id ? '编辑 MCP 服务' : '注册 MCP 服务'"
      :width="720"
      ok-text="保存"
      cancel-text="取消"
      @ok="handleSave"
    >
      <Form layout="vertical" class="form-grid">
        <Form.Item label="服务名" required>
          <Input v-model:value="form.name" placeholder="如：filesystem-mcp" />
        </Form.Item>
        <Form.Item label="传输方式">
          <Select v-model:value="form.transport" :options="TRANSPORT_OPTIONS" />
        </Form.Item>
        <Form.Item label="超时（秒）">
          <InputNumber v-model:value="form.timeoutSeconds" :min="1" style="width: 100%" />
        </Form.Item>
        <Form.Item label="状态">
          <div class="switch-row">
            <span class="switch-label">{{ form.enabled === 1 ? '启用' : '停用' }}</span>
            <Switch v-model:checked="form.enabled" :checked-value="1" :un-checked-value="0" checked-children="启用" un-checked-children="停用" />
          </div>
        </Form.Item>

        <!-- transport 条件字段：Material 风格的卡片分组 -->
        <Form.Item label=" " :colon="false" class="form-grid-full" />

        <template v-if="form.transport === 'stdio'">
          <Form.Item label="命令" class="form-grid-full">
            <Input v-model:value="form.command" placeholder="如 npx / node" />
          </Form.Item>
          <Form.Item label="参数（JSON 数组字符串）" class="form-grid-full">
            <Input v-model:value="form.args" placeholder='["-y", "@org/mcp-server"]' />
          </Form.Item>
          <Form.Item label="环境变量（JSON 对象字符串；留空/**** 不修改）" class="form-grid-full">
            <Textarea v-model:value="form.env" :rows="4" placeholder='{"API_KEY":"..."}' />
          </Form.Item>
        </template>
        <template v-else>
          <Form.Item :label="form.transport === 'sse' ? 'SSE 端点地址' : 'streamable-http 端点地址'" class="form-grid-full">
            <Input v-model:value="form.url" placeholder="https://host/mcp 或 /sse" />
          </Form.Item>
          <Form.Item label="请求头（JSON 对象字符串；留空/**** 不修改）" class="form-grid-full">
            <Textarea v-model:value="form.headers" :rows="4" placeholder='{"Authorization":"Bearer ..."}' />
          </Form.Item>
        </template>

        <div class="callout form-grid-full">
          <IconifyIcon icon="lucide:shield-check" class="callout-icon" />
          <div class="callout-body">
            <div class="callout-title">安全提示</div>
            <div class="callout-desc">
              MCP 服务密钥、请求头仅在此处维护；智能体页只引用 <code>name</code>，绝不存储敏感凭据。
              编辑时留空或 <code>****</code> 表示保持原值不变。
            </div>
          </div>
        </div>

        <Form.Item label="备注" class="form-grid-full">
          <Textarea v-model:value="form.remark" :rows="2" placeholder="用途、限速、维护负责人等" />
        </Form.Item>
      </Form>
    </Modal>

    <!-- 连通性自检 -->
    <Modal v-model:open="checkOpen" title="连通性自检结果" :footer="null" :width="600">
      <Alert
        :type="checkResult?.ok ? 'success' : 'error'"
        show-icon
        :message="checkResult?.message ?? ''"
        class="mb-3"
      />
      <div v-if="checkResult?.tools?.length" class="check-tools-head">已发现工具（{{ checkResult.tools.length }}）</div>
      <Table
        v-if="checkResult?.tools?.length"
        :data-source="checkResult.tools"
        :pagination="false"
        size="small"
        :row-key="(r: { name: string }) => r.name"
      >
        <Table.Column title="工具名" data-index="name" :width="200">
          <template #default="{ record }">
            <code class="code-text">{{ record.name }}</code>
          </template>
        </Table.Column>
        <Table.Column title="描述" data-index="description" />
      </Table>
      <div v-else class="check-empty">
        <IconifyIcon icon="lucide:package-open" class="check-empty-icon" />
        <p class="check-empty-text">未发现可用工具，请确认服务地址、传输协议、鉴权头是否正确</p>
      </div>
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
.cell-sub {
  font-size: 11.5px;
  color: hsl(var(--muted-foreground));
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

.chip-success,
.chip-danger,
.chip-neutral,
.chip-blue,
.chip-purple {
  border-radius: 999px;
  padding: 1px 10px;
  font-weight: 500;
  border: 0;
  font-size: 12px;
}
.chip-blue { background: hsl(212 90% 95%); color: hsl(212 80% 38%); }
.chip-purple { background: hsl(265 80% 95%); color: hsl(265 60% 40%); }
.chip-icon {
  margin-right: 4px;
  font-size: 11px;
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

.callout {
  display: flex;
  gap: 12px;
  padding: 12px 14px;
  background: hsl(var(--primary) / 0.05);
  border: 1px solid hsl(var(--primary) / 0.18);
  border-radius: 10px;
  margin-bottom: 12px;
}
.callout-icon {
  color: hsl(var(--primary));
  font-size: 18px;
  flex: none;
  padding-top: 1px;
}
.callout-title {
  font-weight: 600;
  font-size: 13.5px;
  color: hsl(var(--foreground));
  margin-bottom: 2px;
}
.callout-desc {
  font-size: 12.5px;
  color: hsl(var(--muted-foreground));
  line-height: 1.55;
}
.callout-desc code {
  font-family: var(--vben-font-mono, 'SF Mono', Consolas, monospace);
  background: hsl(var(--muted) / 0.6);
  padding: 0 4px;
  border-radius: 3px;
  font-size: 11.5px;
}

.check-tools-head {
  font-size: 13px;
  color: hsl(var(--muted-foreground));
  margin-bottom: 8px;
}
.check-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 24px 12px;
  color: hsl(var(--muted-foreground));
}
.check-empty-icon {
  font-size: 36px;
  margin-bottom: 8px;
}
.check-empty-text {
  font-size: 13px;
  text-align: center;
  margin: 0;
  max-width: 360px;
}

@media (max-width: 720px) {
  .form-grid { grid-template-columns: 1fr; }
}
</style>