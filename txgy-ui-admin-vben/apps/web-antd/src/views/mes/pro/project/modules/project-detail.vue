<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { MesProProjectApi } from '#/api/mes/pro/project';
import type { MesProWorkOrderApi } from '#/api/mes/pro/workorder';

import { computed, nextTick, ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';
import { DICT_TYPE } from '@vben/constants';

import {
  Button,
  Descriptions,
  message,
  Modal,
  Progress,
  Space,
  Tag,
} from 'ant-design-vue';

import { useVbenVxeGrid } from '#/adapter/vxe-table';
import {
  getProject,
  getProjectStatistics,
  unbindWorkOrders,
} from '#/api/mes/pro/project';
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
</template>
