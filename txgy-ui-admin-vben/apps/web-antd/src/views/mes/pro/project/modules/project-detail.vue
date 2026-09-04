<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { MesProProjectApi } from '#/api/mes/pro/project';
import type { MesProWorkOrderApi } from '#/api/mes/pro/workorder';
import type { MesProProjectMemberApi } from '#/api/mes/pro/project-member';

import { computed, nextTick, ref } from 'vue';

import { useAccess } from '@vben/access';
import { useVbenModal } from '@vben/common-ui';
import { DICT_TYPE } from '@vben/constants';

import {
  Button,
  Descriptions,
  Form,
  FormItem,
  Input,
  message,
  Modal,
  Progress,
  Select,
  Space,
  Table,
  Tag,
} from 'ant-design-vue';

import { useVbenVxeGrid } from '#/adapter/vxe-table';
import {
  getProject,
  getProjectStatistics,
  unbindWorkOrders,
} from '#/api/mes/pro/project';
import {
  createProjectMember,
  deleteProjectMember,
  getProjectMemberList,
} from '#/api/mes/pro/project-member';
import { getWorkOrderPage } from '#/api/mes/pro/workorder';
import WorkOrderForm from '#/views/mes/pro/workorder/modules/form.vue';

import BindWorkOrder from './bind-work-order.vue';

defineOptions({ name: 'MesProProjectDetail' });

const emit = defineEmits<{ success: [] }>();

const open = ref(false); // 弹窗是否打开
const projectId = ref<number>(); // 项目编号
const project = ref<MesProProjectApi.Project>(); // 项目信息
const statistics = ref<MesProProjectApi.ProjectStatistics>(); // 进度统计
const bindWorkOrderRef = ref<InstanceType<typeof BindWorkOrder>>(); // 挂接工单弹窗

/** 有效工单数（总-已取消） */
const validWorkOrderTotal = computed(
  () =>
    (statistics.value?.workOrderTotal ?? 0) -
    (statistics.value?.canceledCount ?? 0),
);
/** 工单完成率 */
const workOrderRate = computed(() => {
  const total = validWorkOrderTotal.value;
  if (total <= 0) {
    return 0;
  }
  return Math.round(
    ((statistics.value?.finishedCount ?? 0) / total) * 100,
  );
});
/** 数量完成率 */
const quantityRate = computed(() => {
  const total = statistics.value?.quantityTotal ?? 0;
  if (!total) {
    return 0;
  }
  return Math.round(
    (((statistics.value?.quantityProducedTotal ?? 0) as number) / total) * 100,
  );
});

/** 优先级文案 */
const priorityText: Record<number, string> = {
  1: '紧急',
  2: '高',
  3: '中',
  4: '低',
};

/** 时间展示（兼容时间戳与字符串） */
function formatTime(value?: number | string) {
  if (value === undefined || value === null || value === '') {
    return '-';
  }
  return typeof value === 'number' ? new Date(value).toLocaleString() : value;
}

/** 项目工单列表列 */
const workOrderColumns: VxeTableGridOptions<MesProWorkOrderApi.WorkOrder>['columns'] =
  [
    { field: 'code', title: '工单编码', width: 170, fixed: 'left' },
    { field: 'name', title: '工单名称', minWidth: 160 },
    {
      field: 'status',
      title: '工单状态',
      width: 110,
      cellRender: {
        name: 'CellDict',
        props: { type: DICT_TYPE.MES_PRO_WORK_ORDER_STATUS },
      },
    },
    { field: 'productName', title: '产品', minWidth: 120 },
    { field: 'quantity', title: '数量', width: 90 },
    {
      field: 'requestDate',
      title: '需求日期',
      width: 120,
      formatter: 'formatDateTime',
    },
    {
      title: '操作',
      width: 100,
      fixed: 'right',
      slots: { default: 'actions' },
    },
  ];

/** 生产工单表格 */
const [Grid, gridApi] = useVbenVxeGrid({
  gridOptions: {
    columns: workOrderColumns,
    height: 'auto',
    proxyConfig: {
      ajax: {
        query: async ({ page }) => {
          return await getWorkOrderPage({
            pageNo: page.currentPage,
            pageSize: page.pageSize,
            projectId: projectId.value,
          });
        },
      },
    },
    rowConfig: {
      keyField: 'id',
      isHover: true,
    },
    toolbarConfig: {
      refresh: true,
    },
  } as VxeTableGridOptions<MesProWorkOrderApi.WorkOrder>,
});

/** 加载项目信息与进度统计 */
async function loadData() {
  if (!projectId.value) {
    return;
  }
  project.value = await getProject(projectId.value);
  statistics.value = await getProjectStatistics(projectId.value);
  await loadMembers();
}

// ==================== 参与成员 ====================

const { hasAccessByCodes } = useAccess();
/** 是否具备成员维护权限（新增/删除） */
const memberEditable = hasAccessByCodes([
  'mes:pro-project-member:create',
  'mes:pro-project-member:delete',
]);

/** 项目成员列表 */
const members = ref<MesProProjectMemberApi.ProjectMember[]>([]);

/** 成员角色文案 */
const memberRoleText: Record<number, string> = {
  1: '项目经理',
  2: '生产负责',
  3: '技术负责',
  4: '质检负责',
  5: '其他',
};

/** 成员表格列 */
const memberColumns = [
  { title: '成员姓名', dataIndex: 'userName', key: 'userName' },
  { title: '项目角色', dataIndex: 'role', key: 'role', width: 120 },
  { title: '备注', dataIndex: 'remark', key: 'remark' },
  { title: '操作', key: 'action', width: 80 },
];

/** 角色下拉选项 */
const memberRoleOptions = [
  { label: '项目经理', value: 1 },
  { label: '生产负责', value: 2 },
  { label: '技术负责', value: 3 },
  { label: '质检负责', value: 4 },
  { label: '其他', value: 5 },
];

/** 加载项目成员 */
async function loadMembers() {
  if (!projectId.value) {
    return;
  }
  members.value = await getProjectMemberList(projectId.value);
}

/** 添加成员弹窗 */
const memberModalOpen = ref(false);
const memberForm = ref<MesProProjectMemberApi.ProjectMember>({
  userName: '',
  role: undefined,
  remark: '',
});

function openMemberModal() {
  memberForm.value = { userName: '', role: undefined, remark: '' };
  memberModalOpen.value = true;
}

function closeMemberModal() {
  memberModalOpen.value = false;
}

/** 提交新增成员 */
async function submitMember() {
  if (!memberForm.value.userName) {
    message.warning('请输入成员姓名');
    return;
  }
  await createProjectMember({
    projectId: projectId.value,
    userName: memberForm.value.userName,
    role: memberForm.value.role,
    remark: memberForm.value.remark,
  });
  message.success('成员已添加');
  closeMemberModal();
  await loadMembers();
}

/** 移除成员 */
async function handleDeleteMember(row: MesProProjectMemberApi.ProjectMember) {
  if (!row.id) {
    return;
  }
  await deleteProjectMember(row.id);
  message.success('成员已移除');
  await loadMembers();
}

/** 打开项目详情弹窗 */
async function openModal(row: MesProProjectApi.Project) {
  projectId.value = row.id;
  open.value = true;
  await nextTick();
  await loadData();
  await gridApi.query();
}

/** 关闭项目详情弹窗 */
function closeModal() {
  open.value = false;
}

/** 刷新弹窗内数据（工单列表 + 统计） */
async function reloadAll() {
  await Promise.all([loadData(), gridApi.query()]);
  emit('success');
}

/** 解除工单的项目关联 */
async function handleUnbind(row: MesProWorkOrderApi.WorkOrder) {
  if (!row.id) {
    return;
  }
  await unbindWorkOrders([row.id]);
  message.success('已解除该工单的项目关联');
  await reloadAll();
}

/** 打开挂接工单弹窗 */
function openBindWorkOrder() {
  if (projectId.value) {
    bindWorkOrderRef.value?.open(projectId.value);
  }
}

/** 从项目下达工单（复用生产工单创建弹窗，预填所属项目） */
const [OrderFormModal, orderFormModalApi] = useVbenModal({
  connectedComponent: WorkOrderForm,
});
function handleCreateWorkOrder() {
  orderFormModalApi.setData({
    formType: 'create',
    projectId: projectId.value,
  });
  orderFormModalApi.open();
}
function handleWorkOrderSuccess() {
  reloadAll();
}

defineExpose({ open: openModal });
</script>

<template>
  <Modal
    v-model:open="open"
    title="项目管理-详情"
    width="80%"
    :footer="null"
    @cancel="closeModal"
  >
    <template v-if="project">
      <!-- 项目基本信息 -->
      <Descriptions :column="4" bordered size="small" class="mb-3">
        <Descriptions.Item label="项目编码">
          <span class="font-medium">{{ project.code || '-' }}</span>
        </Descriptions.Item>
        <Descriptions.Item label="项目名称">
          {{ project.name || '-' }}
        </Descriptions.Item>
        <Descriptions.Item label="来源单据编号">
          {{ project.orderSourceCode || '-' }}
        </Descriptions.Item>
        <Descriptions.Item label="项目状态">
          <Tag
            v-if="project.status !== undefined"
            :color="project.status === 2 ? 'green' : project.status === 1 ? 'blue' : project.status === 4 ? 'default' : project.status === 3 ? 'orange' : 'default'"
          >
            {{ project.status === 0 ? '未开始' : project.status === 1 ? '进行中' : project.status === 2 ? '已完成' : project.status === 3 ? '已暂停' : '已取消' }}
          </Tag>
          <span v-else>-</span>
        </Descriptions.Item>
        <Descriptions.Item label="优先级">
          {{ project.priority ? priorityText[project.priority] : '-' }}
        </Descriptions.Item>
        <Descriptions.Item label="负责人">
          {{ project.owner || '-' }}
        </Descriptions.Item>
        <Descriptions.Item label="责任部门">
          {{ project.dept || '-' }}
        </Descriptions.Item>
        <Descriptions.Item label="计划开始时间">
          {{ formatTime(project.planStartTime) }}
        </Descriptions.Item>
        <Descriptions.Item label="计划结束时间">
          {{ formatTime(project.planEndTime) }}
        </Descriptions.Item>
        <Descriptions.Item label="实际开始时间">
          {{ formatTime(project.actualStartTime) }}
        </Descriptions.Item>
        <Descriptions.Item label="实际结束时间">
          {{ formatTime(project.actualEndTime) }}
        </Descriptions.Item>
        <Descriptions.Item label="工期(天)">
          {{ project.durationDays ?? '-' }}
        </Descriptions.Item>
        <Descriptions.Item label="创建时间" :span="2">
          {{ new Date(project.createTime ?? 0).toLocaleString() }}
        </Descriptions.Item>
        <Descriptions.Item label="备注" :span="2">
          {{ project.remark || '-' }}
        </Descriptions.Item>
      </Descriptions>
      <!-- 生产进度 -->
      <div class="mb-3 rounded-md bg-gray-50 p-3">
        <div class="mb-1 text-sm font-medium">生产进度</div>
        <div class="mb-2 text-sm">
          工单完成率：{{ statistics?.finishedCount ?? 0 }} /
          {{ validWorkOrderTotal }}（{{ workOrderRate }}%）
          &nbsp;&nbsp;|&nbsp;&nbsp; 数量完成率：{{
            statistics?.quantityProducedTotal ?? 0
          }}
          / {{ statistics?.quantityTotal ?? 0 }}（{{ quantityRate }}%）
        </div>
        <Progress
          :percent="workOrderRate"
          :stroke-color="{ '0%': '#108ee9', '100%': '#87d068' }"
          class="mb-2"
        />
        <Space wrap>
          <Tag>总工单：{{ statistics?.workOrderTotal ?? 0 }}</Tag>
          <Tag>草稿：{{ statistics?.prepareCount ?? 0 }}</Tag>
          <Tag color="processing">
            进行中：{{ statistics?.confirmedCount ?? 0 }}
          </Tag>
          <Tag color="success">已完成：{{ statistics?.finishedCount ?? 0 }}</Tag>
          <Tag color="default">
            已取消：{{ statistics?.canceledCount ?? 0 }}
          </Tag>
        </Space>
      </div>
      <!-- 参与成员 -->
      <div class="mb-3 rounded-md bg-gray-50 p-3">
        <div class="mb-2 flex items-center justify-between">
          <span class="text-sm font-medium">参与成员</span>
          <Button
            v-if="memberEditable"
            size="small"
            type="primary"
            @click="openMemberModal"
          >
            添加成员
          </Button>
        </div>
        <Table
          :columns="memberColumns"
          :data-source="members"
          :pagination="false"
          row-key="id"
          size="small"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'role'">
              {{ memberRoleText[record.role] || '-' }}
            </template>
            <template v-if="column.key === 'action'">
              <Button
                v-if="memberEditable"
                danger
                size="small"
                type="link"
                @click="handleDeleteMember(record)"
              >
                移除
              </Button>
            </template>
          </template>
        </Table>
      </div>
    </template>
    <!-- 项目工单列表与操作 -->
    <div class="mb-2 flex items-center justify-between">
      <span class="text-sm font-medium">项目生产工单</span>
      <Space>
        <Button type="primary" @click="handleCreateWorkOrder">
          下达工单
        </Button>
        <Button @click="openBindWorkOrder"> 挂接已有工单 </Button>
      </Space>
    </div>
    <Grid />
  </Modal>

  <!-- 挂接已有工单弹窗 -->
  <BindWorkOrder ref="bindWorkOrderRef" @success="handleWorkOrderSuccess" />
  <!-- 工单创建弹窗（下达工单） -->
  <OrderFormModal @success="handleWorkOrderSuccess" />

  <!-- 添加项目成员弹窗 -->
  <Modal
    v-model:open="memberModalOpen"
    title="添加项目成员"
    @cancel="closeMemberModal"
    @ok="submitMember"
  >
    <Form :label-col="{ span: 5 }" :wrapper-col="{ span: 16 }">
      <FormItem label="成员姓名">
        <Input
          v-model:value="memberForm.userName"
          placeholder="请输入成员姓名"
        />
      </FormItem>
      <FormItem label="项目角色">
        <Select
          v-model:value="memberForm.role"
          :options="memberRoleOptions"
          allow-clear
          placeholder="请选择项目角色"
        />
      </FormItem>
      <FormItem label="备注">
        <Input
          v-model:value="memberForm.remark"
          placeholder="请输入备注"
        />
      </FormItem>
    </Form>
  </Modal>
</template>
