<script lang="ts" setup>
import type { UploadFile } from 'ant-design-vue';
import type { FileType } from 'ant-design-vue/es/upload/interface';

import type { ErpRecognitionApi } from '#/api/erp/recognition';

import dayjs from 'dayjs';
import timezone from 'dayjs/plugin/timezone.js';
import { computed, onMounted, ref } from 'vue';

import { confirm } from '@vben/common-ui';
import { downloadFileFromBlobPart } from '@vben/utils';

import {
  Button,
  InputNumber,
  message,
  Segmented,
  Select,
  Tag,
  Upload,
} from 'ant-design-vue';

import {
  confirmRecognitionTask,
  correctRecognitionResult,
  createRecognitionTask,
  deleteRecognitionTask,
  downloadRecognitionTemplate,
  getRecognitionTask,
  getRecognitionTaskPage,
  importRecognitionExcel,
  importRecognitionTask,
  retryRecognitionTask,
} from '#/api/erp/recognition';
import { getAccountSimpleList } from '#/api/erp/finance/account';
import { getProductSimpleList } from '#/api/erp/product/product';
import { getSupplierSimpleList } from '#/api/erp/purchase/supplier';
import { getCustomerSimpleList } from '#/api/erp/sale/customer';
import { getWarehouseSimpleList } from '#/api/erp/stock/warehouse';
import { getSimpleUserList } from '#/api/system/user';
import { uploadFile } from '#/api/infra/file';

defineOptions({ name: 'ErpPurchaseOrderRecognitionImport' });

dayjs.extend(timezone);

const emit = defineEmits(['success']);

type RecognitionBizType =
  | 'PURCHASE_ORDER'
  | 'PURCHASE_RETURN'
  | 'SUPPLIER'
  | 'SALE_ORDER';

const props = withDefaults(
  defineProps<{
    bizType?: RecognitionBizType;
    title?: string;
  }>(),
  {
    bizType: 'PURCHASE_ORDER',
    title: '采购订单图片识别',
  },
);

const isSupplier = computed(() => props.bizType === 'SUPPLIER');
const isPurchaseReturn = computed(() => props.bizType === 'PURCHASE_RETURN');
const isSaleOrder = computed(() => props.bizType === 'SALE_ORDER');
const canExcel = computed(
  () => props.bizType === 'PURCHASE_ORDER' || props.bizType === 'SALE_ORDER',
);
const documentLabel = computed(() =>
  isSupplier.value
    ? '供应商资料'
    : isPurchaseReturn.value
      ? '采购退货'
      : isSaleOrder.value
        ? '销售订单'
        : '采购订单',
);

const statusOptions = [
  { label: '全部状态', value: undefined },
  { label: '待复核', value: 'REVIEW_REQUIRED' },
  { label: '已确认', value: 'CONFIRMED' },
  { label: '已导入', value: 'IMPORTED' },
  { label: '识别失败', value: 'FAILED' },
];

const statusText: Record<string, string> = {
  CREATED: '已创建',
  PROCESSING: '识别中',
  REVIEW_REQUIRED: '待复核',
  CONFIRMED: '已确认',
  IMPORTED: '已导入',
  FAILED: '识别失败',
};

const tasks = ref<ErpRecognitionApi.Task[]>([]);
const total = ref(0);
const loading = ref(false);
const uploading = ref(false);
const deletingTaskId = ref<number>();
const retryingTaskId = ref<number>();
const importMode = ref<'document' | 'excel'>('document');
const saving = ref(false);
const pageNo = ref(1);
const pageSize = ref(10);
const status = ref<string>();
const keyword = ref('');
const fileList = ref<UploadFile[]>([]);
const selectedFile = ref<File>();
const selectedTask = ref<ErpRecognitionApi.Task>();
const editDocument = ref<ErpRecognitionApi.Document>(emptyDocument());
const suppliers = ref<any[]>([]);
const products = ref<any[]>([]);
const warehouses = ref<any[]>([]);
const accounts = ref<any[]>([]);
const customers = ref<any[]>([]);
const saleUsers = ref<any[]>([]);

const selectedValidation = computed(
  () => selectedTask.value?.validation ?? { passed: false, errors: [], warnings: [] },
);
const canEdit = computed(() =>
  ['REVIEW_REQUIRED', 'CONFIRMED'].includes(selectedTask.value?.status ?? ''),
);
const canConfirm = computed(
  () =>
    selectedTask.value?.status === 'REVIEW_REQUIRED' &&
    selectedValidation.value.passed === true,
);
const canImport = computed(() => selectedTask.value?.status === 'CONFIRMED');
const isExcelMode = computed(
  () => canExcel.value && importMode.value === 'excel',
);
const uploadAccept = computed(() =>
  isExcelMode.value ? '.xls,.xlsx' : '.jpg,.jpeg,.png,.webp,.pdf',
);

function emptyDocument(): ErpRecognitionApi.Document {
  return {
    documentType: props.bizType,
    documentNo: '',
    documentDate: '',
    orderNo: '',
    returnTime: '',
    accountId: undefined,
    discountPercent: 0,
    supplierId: undefined,
    supplierName: '',
    customerId: undefined,
    customerName: '',
    saleUserId: undefined,
    contact: '',
    mobile: '',
    telephone: '',
    email: '',
    fax: '',
    taxNo: '',
    taxPercent: undefined,
    bankName: '',
    bankAccount: '',
    bankAddress: '',
    remark: '',
    items: [],
  };
}

function cloneDocument(document?: ErpRecognitionApi.Document) {
  const result = JSON.parse(JSON.stringify(document ?? emptyDocument()));
  result.items ??= [];
  return result as ErpRecognitionApi.Document;
}

function statusColor(value?: string) {
  return value === 'FAILED'
    ? 'error'
    : value === 'IMPORTED'
      ? 'success'
      : value === 'CONFIRMED'
        ? 'processing'
        : value === 'REVIEW_REQUIRED'
          ? 'warning'
          : 'default';
}

function taskDisplayNo(task: ErpRecognitionApi.Task) {
  const index = tasks.value.findIndex((item) => item.id === task.id);
  return index < 0 ? task.id : (pageNo.value - 1) * pageSize.value + index + 1;
}

function formatTime(value?: unknown) {
  if (value === undefined || value === null || value === '') return '-';
  if (Array.isArray(value)) {
    const [year, month, day, hour = 0, minute = 0, second = 0] = value;
    if (![year, month, day].every((item) => typeof item === 'number')) {
      return value.join('-');
    }
    return `${year}-${String(month).padStart(2, '0')}-${String(day).padStart(
      2,
      '0',
    )} ${String(hour).padStart(2, '0')}:${String(minute).padStart(
      2,
      '0',
    )}:${String(second).padStart(2, '0')}`;
  }
  if (typeof value === 'number') {
    return dayjs(value).tz('Asia/Shanghai').format('YYYY-MM-DD HH:mm:ss');
  }
  if (value instanceof Date) {
    return dayjs(value).tz('Asia/Shanghai').format('YYYY-MM-DD HH:mm:ss');
  }
  if (typeof value === 'string') {
    if (/^\d+$/.test(value)) {
      return dayjs(Number(value)).tz('Asia/Shanghai').format('YYYY-MM-DD HH:mm:ss');
    }
    const normalized = value.replace('T', ' ');
    return /(?:Z|[+-]\d{2}:?\d{2})$/.test(value)
      ? dayjs(value).tz('Asia/Shanghai').format('YYYY-MM-DD HH:mm:ss')
      : normalized.slice(0, 19);
  }
  return String(value).replace('T', ' ').slice(0, 19);
}

async function loadTasks() {
  loading.value = true;
  try {
    const result = await getRecognitionTaskPage({
      pageNo: pageNo.value,
      pageSize: pageSize.value,
      status: status.value,
      keyword: keyword.value || undefined,
      bizType: props.bizType,
    });
    tasks.value = result.list;
    total.value = result.total;
    const firstTask = tasks.value[0];
    if (!selectedTask.value && firstTask) {
      await openTask(firstTask);
    }
  } finally {
    loading.value = false;
  }
}

async function openTask(task: ErpRecognitionApi.Task) {
  if (!task.id) return;
  const detail = await getRecognitionTask(task.id);
  selectedTask.value = detail;
  editDocument.value = cloneDocument(detail.document);
}

function beforeUpload(file: FileType) {
  selectedFile.value = file as unknown as File;
  fileList.value = [file as UploadFile];
  return false;
}

function handleRemove() {
  fileList.value = [];
  selectedFile.value = undefined;
}

async function submitUpload() {
  const file = selectedFile.value ?? fileList.value[0]?.originFileObj;
  if (!file) {
    message.warning(
      isExcelMode.value
        ? `请选择${documentLabel.value} Excel 文件`
        : `请选择${documentLabel.value}图片或 PDF 文件`,
    );
    return;
  }
  uploading.value = true;
  try {
    const uploaded = await uploadFile({
      file,
      directory: 'erp/recognition',
    });
    const sourceFileUrl = uploaded;
    if (!sourceFileUrl) {
      throw new Error('文件上传成功，但未获得文件地址');
    }
    const taskId = isExcelMode.value
      ? await importRecognitionExcel(file, props.bizType, sourceFileUrl)
      : await createRecognitionTask({
          bizType: props.bizType,
          sourceFileUrl,
        });
    fileList.value = [];
    selectedFile.value = undefined;
    message.success(
      isExcelMode.value
        ? `Excel 导入任务 ${taskId} 已创建，请复核结果`
        : `识别任务 ${taskId} 已创建`,
    );
    await loadTasks();
    await openTask({ id: taskId });
  } catch (error: any) {
    message.error(error?.message || '开始识别失败，请检查文件和识别服务配置');
  } finally {
    uploading.value = false;
  }
}

async function handleDownloadTemplate() {
  try {
    const data = await downloadRecognitionTemplate(props.bizType);
    downloadFileFromBlobPart({
      fileName: isSaleOrder.value
        ? '销售订单导入模板.xls'
        : '采购订单导入模板.xls',
      source: data,
    });
  } catch {
    message.error('模板下载失败，请稍后重试');
  }
}

async function retryTask(task: ErpRecognitionApi.Task) {
  if (!task.id) return;
  retryingTaskId.value = task.id;
  try {
    await retryRecognitionTask(task.id);
    message.success('已重新提交识别');
    await loadTasks();
    await openTask(task);
  } finally {
    retryingTaskId.value = undefined;
  }
}

async function deleteTask(task: ErpRecognitionApi.Task) {
  if (
    !task.id ||
    !['REVIEW_REQUIRED', 'FAILED', 'IMPORTED'].includes(task.status ?? '')
  ) {
    return;
  }
  const imported = task.status === 'IMPORTED';
  await confirm(
    imported
      ? `确认删除已导入识别任务 #${taskDisplayNo(task)}？只删除识别任务记录，不会删除已生成的业务单据。`
      : `确认删除识别任务 #${taskDisplayNo(task)}？删除后只移除识别任务记录，不影响已上传原文件。`,
  );
  deletingTaskId.value = task.id;
  try {
    await deleteRecognitionTask(task.id);
    if (selectedTask.value?.id === task.id) {
      selectedTask.value = undefined;
      editDocument.value = emptyDocument();
    }
    message.success('识别任务已删除');
    await loadTasks();
  } finally {
    deletingTaskId.value = undefined;
  }
}

function addItem() {
  editDocument.value.items ??= [];
  editDocument.value.items.push({
    productId: undefined,
    orderItemId: undefined,
    warehouseId: isPurchaseReturn.value ? warehouses.value[0]?.id : undefined,
    count: undefined,
    productPrice: undefined,
    taxPercent: undefined,
    productName: '',
    productCode: '',
    remark: '',
  });
}

function removeItem(index: number) {
  editDocument.value.items?.splice(index, 1);
}

function productLabel(product: any) {
  return [product.name, product.standard, product.barCode]
    .filter(Boolean)
    .join(' / ');
}

function syncSupplierName(value: any) {
  const supplier = suppliers.value.find((item) => item.id === value);
  editDocument.value.supplierName = supplier?.name ?? '';
}

function syncCustomerName(value: any) {
  const customer = customers.value.find((item) => item.id === value);
  editDocument.value.customerName = customer?.name ?? '';
}

function syncProduct(item: ErpRecognitionApi.Item, value: any) {
  const product = products.value.find((entry) => entry.id === value);
  item.productName = product?.name;
  item.productCode = product?.barCode;
  item.productUnitId = product?.unitId;
}

async function saveCorrection() {
  const task = selectedTask.value;
  const document = editDocument.value;
  if (!task?.id || task.resultVersion === undefined) return;
  if (isSupplier.value) {
    if (!document.supplierName?.trim()) {
      message.warning('请填写供应商名称');
      return;
    }
  } else {
    if (
      (isSaleOrder.value ? !document.customerId : !document.supplierId) ||
      !document.items?.length ||
      (isPurchaseReturn.value
        ? !document.returnTime || !document.orderNo || !document.accountId
        : !document.documentDate)
    ) {
      message.warning(
        isPurchaseReturn.value
          ? '请完善退货时间、关联采购订单、供应商、结算账户和退货明细'
          : isSaleOrder.value
            ? '请完善订单日期、客户和销售明细'
            : '请完善订单日期、供应商和采购明细',
      );
      return;
    }
    if (
      document.items.some(
        (item) =>
          !item.productId ||
          !item.count ||
          item.count <= 0 ||
          (isPurchaseReturn.value && !item.warehouseId),
      )
    ) {
      message.warning(
        isPurchaseReturn.value
          ? '请为每条明细选择产品、仓库并填写大于 0 的数量'
          : '请为每条明细选择产品并填写大于 0 的数量',
      );
      return;
    }
  }
  saving.value = true;
  try {
    await correctRecognitionResult({
      taskId: task.id,
      resultVersion: task.resultVersion,
      documentNo: document.documentNo,
      documentDate: document.documentDate,
      orderNo: document.orderNo,
      returnTime: document.returnTime,
      accountId: document.accountId,
      discountPercent: document.discountPercent,
      supplierId: document.supplierId,
      supplierName: document.supplierName,
      customerId: document.customerId,
      customerName: document.customerName,
      saleUserId: document.saleUserId,
      contact: document.contact,
      mobile: document.mobile,
      telephone: document.telephone,
      email: document.email,
      fax: document.fax,
      taxNo: document.taxNo,
      taxPercent: document.taxPercent,
      bankName: document.bankName,
      bankAccount: document.bankAccount,
      bankAddress: document.bankAddress,
      remark: document.remark,
      items: document.items ?? [],
    });
    message.success('识别结果修正已保存');
    await refreshSelected(task.id);
  } finally {
    saving.value = false;
  }
}

async function confirmTask() {
  const task = selectedTask.value;
  if (!task?.id || task.resultVersion === undefined) return;
  await confirmRecognitionTask({
    taskId: task.id,
    resultVersion: task.resultVersion,
  });
  message.success('识别结果已确认');
  await refreshSelected(task.id);
  await loadTasks();
}

async function importDraft() {
  const task = selectedTask.value;
  if (!task?.id || task.resultVersion === undefined) return;
  const targetId = await importRecognitionTask({
    taskId: task.id,
    resultVersion: task.resultVersion,
  });
  const successText = isSupplier.value
    ? `已创建供应商，编号：${targetId}`
    : isPurchaseReturn.value
      ? `已生成采购退货草稿，编号：${targetId}`
      : isSaleOrder.value
        ? `已生成未审核销售订单草稿，编号：${targetId}`
        : `已生成未审核采购订单草稿，编号：${targetId}`;
  message.success(successText);
  await refreshSelected(task.id);
  await loadTasks();
  emit('success');
}

async function refreshSelected(taskId: number) {
  await openTask({ id: taskId });
}

function previewFile() {
  const url = selectedTask.value?.sourceFileUrl;
  if (url) window.open(url, '_blank', 'noopener,noreferrer');
}

async function loadMasterData() {
  [
    suppliers.value,
    products.value,
    warehouses.value,
    accounts.value,
    customers.value,
    saleUsers.value,
  ] =
    await Promise.all([
    getSupplierSimpleList(),
    getProductSimpleList(),
    getWarehouseSimpleList(),
    getAccountSimpleList(),
    getCustomerSimpleList(),
    getSimpleUserList(),
  ]);
}

onMounted(async () => {
  await Promise.all([loadMasterData(), loadTasks()]);
});
</script>

<template>
  <div class="flex h-full min-h-0 flex-col gap-4 p-1">
      <section class="border-border bg-card shrink-0 border p-4">
        <div class="flex flex-wrap items-center gap-3">
          <Segmented
            v-if="canExcel"
            v-model:value="importMode"
            :options="[
              { label: '图片/PDF识别', value: 'document' },
              { label: 'Excel导入', value: 'excel' },
            ]"
          />
          <Upload
            v-model:file-list="fileList"
            :before-upload="beforeUpload"
            :max-count="1"
            :accept="uploadAccept"
            @remove="handleRemove"
          >
            <Button :loading="uploading" type="primary">
              <span class="icon-[lucide--upload] mr-1"></span>
              {{ isExcelMode ? '选择 Excel 文件' : `上传${documentLabel}` }}
            </Button>
          </Upload>
          <Button v-if="isExcelMode" @click="handleDownloadTemplate">
            <span class="icon-[lucide--download] mr-1"></span>
            下载导入模板
          </Button>
          <Button
            :disabled="!fileList.length"
            :loading="uploading"
            @click="submitUpload"
          >
            {{ isExcelMode ? '开始导入' : '开始识别' }}
          </Button>
          <span class="text-muted-foreground text-sm">
            {{
              isExcelMode
                ? `支持 XLS、XLSX；每行一条${isSaleOrder ? '销售' : '采购'}明细，订单头字段可只填写第一行`
                : '支持 JPG、PNG、WEBP、PDF；识别完成后需人工复核，确认后才生成 ERP 草稿'
            }}
          </span>
        </div>
      </section>

      <div class="grid min-h-0 flex-1 grid-cols-1 gap-4 xl:grid-cols-[minmax(420px,0.9fr)_minmax(620px,1.4fr)]">
        <section class="border-border bg-card flex min-h-0 flex-col border">
          <div class="border-border flex items-center gap-2 border-b p-3">
            <input
              v-model="keyword"
              class="border-border bg-background h-8 flex-1 border px-2 text-sm"
              placeholder="按文件地址或任务编号搜索"
              @keyup.enter="loadTasks"
            />
            <Select
              v-model:value="status"
              class="w-32"
              :options="statusOptions"
              @change="loadTasks"
            />
            <Button @click="loadTasks">
              <span class="icon-[lucide--refresh-cw]"></span>
            </Button>
          </div>
          <div class="min-h-0 flex-1 overflow-auto">
            <table class="w-full text-left text-sm">
              <thead class="bg-muted/40 sticky top-0">
                <tr>
                  <th class="p-3">任务</th>
                  <th class="p-3">状态</th>
                  <th class="p-3">创建时间</th>
                  <th class="p-3 text-right">操作</th>
                </tr>
              </thead>
              <tbody>
                <tr
                  v-for="(task, index) in tasks"
                  :key="task.id"
                  class="border-border hover:bg-muted/30 cursor-pointer border-b"
                  :class="{ 'bg-primary/5': selectedTask?.id === task.id }"
                  @click="openTask(task)"
                >
                  <td class="max-w-48 p-3">
                    <div class="font-medium">#{{ (pageNo - 1) * pageSize + index + 1 }}</div>
                    <div class="text-muted-foreground truncate text-xs">
                      {{ task.sourceFileUrl }}
                    </div>
                  </td>
                  <td class="p-3">
                    <Tag :color="statusColor(task.status)">
                      {{ statusText[task.status ?? ''] ?? task.status }}
                    </Tag>
                  </td>
                  <td class="text-muted-foreground whitespace-nowrap p-3 text-xs">
                    {{ formatTime(task.createTime) }}
                  </td>
                  <td class="p-3 text-right">
                    <Button
                      v-if="task.status === 'REVIEW_REQUIRED'"
                      type="link"
                      size="small"
                      @click.stop="openTask(task)"
                    >
                      复核
                    </Button>
                    <Button
                      v-if="task.status === 'FAILED'"
                      type="link"
                      size="small"
                      :loading="retryingTaskId === task.id"
                      @click.stop="retryTask(task)"
                    >
                      重试
                    </Button>
                    <Button
                      v-if="['REVIEW_REQUIRED', 'FAILED', 'IMPORTED'].includes(task.status ?? '')"
                      danger
                      type="link"
                      size="small"
                      :loading="deletingTaskId === task.id"
                      @click.stop="deleteTask(task)"
                    >
                      删除
                    </Button>
                  </td>
                </tr>
                <tr v-if="!loading && !tasks.length">
                  <td colspan="4" class="text-muted-foreground p-8 text-center">
                    暂无识别任务
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
          <div class="border-border border-t p-3">
            <a-pagination
              v-model:current="pageNo"
              v-model:page-size="pageSize"
              :total="total"
              size="small"
              show-size-changer
              @change="loadTasks"
              @show-size-change="loadTasks"
            />
          </div>
        </section>

        <section class="border-border bg-card flex min-h-0 flex-col border">
          <template v-if="selectedTask">
            <div class="border-border flex flex-wrap items-center justify-between gap-2 border-b p-3">
              <div>
                <div class="font-semibold">识别任务 #{{ taskDisplayNo(selectedTask) }}</div>
                <div class="text-muted-foreground text-xs">
                  {{ selectedTask.documentType || documentLabel }} · 结果版本
                  {{ selectedTask.resultVersion ?? 0 }}
                </div>
              </div>
              <div class="flex flex-wrap gap-2">
                <Button @click="previewFile">
                  <span class="icon-[lucide--external-link] mr-1"></span>
                  查看原文件
                </Button>
                <Button
                  :disabled="!canEdit"
                  :loading="saving"
                  @click="saveCorrection"
                >
                  保存修正
                </Button>
                <Button
                  v-if="canConfirm"
                  type="primary"
                  @click="confirmTask"
                >
                  确认结果
                </Button>
                <Button
                  v-if="canImport"
                  type="primary"
                  @click="importDraft"
                >
                  生成 ERP 草稿
                </Button>
              </div>
            </div>

            <div
              v-if="selectedTask.status === 'FAILED'"
              class="m-3 border border-red-200 bg-red-50 p-3 text-sm text-red-700"
            >
              {{ selectedTask.errorMessage || '识别失败，请检查 OCR 服务配置后重试' }}
            </div>

            <div class="min-h-0 flex-1 overflow-auto p-4">
              <div
                v-if="selectedTask.document"
                class="grid grid-cols-1 gap-3 md:grid-cols-2"
              >
                <label v-if="!isSupplier" class="text-sm">
                  <span class="mb-1 block">外部单号</span>
                  <input
                    v-model="editDocument.documentNo"
                    :disabled="!canEdit"
                    class="border-border bg-background h-9 w-full border px-2"
                  />
                </label>
                <label v-if="!isSupplier && !isPurchaseReturn" class="text-sm">
                  <span class="mb-1 block">订单日期</span>
                  <input
                    v-model="editDocument.documentDate"
                    :disabled="!canEdit"
                    class="border-border bg-background h-9 w-full border px-2"
                    placeholder="YYYY-MM-DD"
                  />
                </label>
                <label v-if="isPurchaseReturn" class="text-sm">
                  <span class="mb-1 block">退货时间</span>
                  <input
                    v-model="editDocument.returnTime"
                    :disabled="!canEdit"
                    class="border-border bg-background h-9 w-full border px-2"
                    placeholder="YYYY-MM-DD 或 YYYY-MM-DDTHH:mm:ss"
                  />
                </label>
                <label v-if="isPurchaseReturn" class="text-sm">
                  <span class="mb-1 block">关联采购订单号</span>
                  <input
                    v-model="editDocument.orderNo"
                    :disabled="!canEdit"
                    class="border-border bg-background h-9 w-full border px-2"
                    placeholder="请输入已审核采购订单号"
                  />
                </label>
                <label v-if="isSupplier" class="text-sm md:col-span-2">
                  <span class="mb-1 block">供应商名称</span>
                  <input
                    v-model="editDocument.supplierName"
                    :disabled="!canEdit"
                    class="border-border bg-background h-9 w-full border px-2"
                  />
                </label>
                <label v-if="!isSupplier && !isSaleOrder" class="text-sm md:col-span-2">
                  <span class="mb-1 block">供应商</span>
                  <Select
                    v-model:value="editDocument.supplierId"
                    :disabled="!canEdit"
                    class="w-full"
                    show-search
                    :options="
                      suppliers.map((item) => ({
                        label: item.name,
                        value: item.id,
                      }))
                    "
                    @change="syncSupplierName"
                  />
                </label>
                <label v-if="isSaleOrder" class="text-sm md:col-span-2">
                  <span class="mb-1 block">客户</span>
                  <Select
                    v-model:value="editDocument.customerId"
                    :disabled="!canEdit"
                    class="w-full"
                    show-search
                    :options="
                      customers.map((item) => ({
                        label: item.name,
                        value: item.id,
                      }))
                    "
                    @change="syncCustomerName"
                  />
                </label>
                <label v-if="isSaleOrder" class="text-sm">
                  <span class="mb-1 block">销售员</span>
                  <Select
                    v-model:value="editDocument.saleUserId"
                    :disabled="!canEdit"
                    class="w-full"
                    show-search
                    allow-clear
                    :options="
                      saleUsers.map((item) => ({
                        label: item.nickname,
                        value: item.id,
                      }))
                    "
                  />
                </label>
                <template v-if="isSupplier">
                  <label class="text-sm">
                    <span class="mb-1 block">联系人</span>
                    <input v-model="editDocument.contact" :disabled="!canEdit" class="border-border bg-background h-9 w-full border px-2" />
                  </label>
                  <label class="text-sm">
                    <span class="mb-1 block">手机号码</span>
                    <input v-model="editDocument.mobile" :disabled="!canEdit" class="border-border bg-background h-9 w-full border px-2" />
                  </label>
                  <label class="text-sm">
                    <span class="mb-1 block">联系电话</span>
                    <input v-model="editDocument.telephone" :disabled="!canEdit" class="border-border bg-background h-9 w-full border px-2" />
                  </label>
                  <label class="text-sm">
                    <span class="mb-1 block">电子邮箱</span>
                    <input v-model="editDocument.email" :disabled="!canEdit" class="border-border bg-background h-9 w-full border px-2" />
                  </label>
                  <label class="text-sm">
                    <span class="mb-1 block">传真</span>
                    <input v-model="editDocument.fax" :disabled="!canEdit" class="border-border bg-background h-9 w-full border px-2" />
                  </label>
                  <label class="text-sm">
                    <span class="mb-1 block">纳税人识别号</span>
                    <input v-model="editDocument.taxNo" :disabled="!canEdit" class="border-border bg-background h-9 w-full border px-2" />
                  </label>
                  <label class="text-sm">
                    <span class="mb-1 block">税率(%)</span>
                    <InputNumber
                      v-model:value="editDocument.taxPercent"
                      :disabled="!canEdit"
                      :min="0"
                      :max="100"
                      :precision="2"
                      class="w-full"
                    />
                  </label>
                  <label class="text-sm">
                    <span class="mb-1 block">开户行</span>
                    <input v-model="editDocument.bankName" :disabled="!canEdit" class="border-border bg-background h-9 w-full border px-2" />
                  </label>
                  <label class="text-sm">
                    <span class="mb-1 block">开户账号</span>
                    <input v-model="editDocument.bankAccount" :disabled="!canEdit" class="border-border bg-background h-9 w-full border px-2" />
                  </label>
                  <label class="text-sm">
                    <span class="mb-1 block">开户地址</span>
                    <input v-model="editDocument.bankAddress" :disabled="!canEdit" class="border-border bg-background h-9 w-full border px-2" />
                  </label>
                </template>
                <label v-if="!isSupplier" class="text-sm">
                  <span class="mb-1 block">结算账户</span>
                  <Select
                    v-model:value="editDocument.accountId"
                    :disabled="!canEdit"
                    class="w-full"
                    :options="
                      accounts.map((item) => ({
                        label: item.no ? `${item.name} / ${item.no}` : item.name,
                        value: item.id,
                      }))
                    "
                  />
                </label>
                <label v-if="!isSupplier && (isPurchaseReturn || isSaleOrder)" class="text-sm">
                  <span class="mb-1 block">优惠率(%)</span>
                  <InputNumber
                    v-model:value="editDocument.discountPercent"
                    :disabled="!canEdit"
                    :min="0"
                    :max="100"
                    :precision="2"
                    class="w-full"
                  />
                </label>
                <label class="text-sm md:col-span-2">
                  <span class="mb-1 block">备注</span>
                  <input
                    v-model="editDocument.remark"
                    :disabled="!canEdit"
                    class="border-border bg-background h-9 w-full border px-2"
                  />
                </label>
              </div>

              <div v-if="selectedTask.document && !isSupplier" class="mt-6">
                <div class="mb-2 flex items-center justify-between">
                  <span class="font-semibold">{{
                    isPurchaseReturn
                      ? '退货明细'
                      : isSaleOrder
                        ? '销售明细'
                        : '采购明细'
                  }}</span>
                  <Button v-if="canEdit" size="small" @click="addItem">
                    <span class="icon-[lucide--plus] mr-1"></span>
                    添加明细
                  </Button>
                </div>
                <div class="border-border overflow-x-auto border">
                  <table class="w-full min-w-[900px] text-sm">
                    <thead class="bg-muted/40">
                      <tr>
                        <th class="p-2 text-left">产品</th>
                        <th v-if="isPurchaseReturn" class="w-40 p-2 text-left">
                          仓库
                        </th>
                        <th class="w-28 p-2 text-left">数量</th>
                        <th class="w-32 p-2 text-left">单价</th>
                        <th class="w-28 p-2 text-left">税率(%)</th>
                        <th class="w-40 p-2 text-left">备注</th>
                        <th class="w-20 p-2"></th>
                      </tr>
                    </thead>
                    <tbody>
                      <tr
                        v-for="(item, index) in editDocument.items"
                        :key="index"
                        class="border-border border-t"
                      >
                        <td class="p-2">
                          <Select
                            v-model:value="item.productId"
                            :disabled="!canEdit"
                            class="w-full"
                            show-search
                            :options="
                              products.map((product) => ({
                                label: productLabel(product),
                                value: product.id,
                              }))
                            "
                            @change="syncProduct(item, $event)"
                          />
                          <div class="text-muted-foreground mt-1 text-xs">
                            {{ item.productName || item.productCode || '-' }}
                          </div>
                        </td>
                        <td v-if="isPurchaseReturn" class="p-2">
                          <Select
                            v-model:value="item.warehouseId"
                            :disabled="!canEdit"
                            class="w-full"
                            show-search
                            :options="
                              warehouses.map((warehouse) => ({
                                label: warehouse.name,
                                value: warehouse.id,
                              }))
                            "
                          />
                        </td>
                        <td class="p-2">
                          <InputNumber
                            v-model:value="item.count"
                            :disabled="!canEdit"
                            :min="0"
                            class="w-full"
                          />
                        </td>
                        <td class="p-2">
                          <InputNumber
                            v-model:value="item.productPrice"
                            :disabled="!canEdit"
                            :min="0"
                            :precision="2"
                            class="w-full"
                          />
                        </td>
                        <td class="p-2">
                          <InputNumber
                            v-model:value="item.taxPercent"
                            :disabled="!canEdit"
                            :min="0"
                            :max="100"
                            :precision="2"
                            class="w-full"
                          />
                        </td>
                        <td class="p-2">
                          <input
                            v-model="item.remark"
                            :disabled="!canEdit"
                            class="border-border bg-background h-8 w-full border px-2"
                          />
                        </td>
                        <td class="p-2 text-right">
                          <Button
                            v-if="canEdit"
                            danger
                            type="link"
                            size="small"
                            @click="removeItem(index)"
                          >
                            删除
                          </Button>
                        </td>
                      </tr>
                    </tbody>
                  </table>
                </div>
              </div>

              <div
                v-if="selectedTask.validation"
                class="mt-6 grid grid-cols-1 gap-3 md:grid-cols-2"
              >
                <div
                  v-if="selectedValidation.errors?.length"
                  class="border border-red-200 bg-red-50 p-3 text-sm text-red-700"
                >
                  <div class="mb-1 font-medium">需要处理的问题</div>
                  <div v-for="error in selectedValidation.errors" :key="error">
                    {{ error }}
                  </div>
                </div>
                <div
                  v-if="selectedValidation.warnings?.length"
                  class="border border-yellow-200 bg-yellow-50 p-3 text-sm text-yellow-700"
                >
                  <div class="mb-1 font-medium">提示</div>
                  <div
                    v-for="warning in selectedValidation.warnings"
                    :key="warning"
                  >
                    {{ warning }}
                  </div>
                </div>
              </div>
            </div>
          </template>
          <div v-else class="text-muted-foreground flex flex-1 items-center justify-center">
            请选择左侧识别任务
          </div>
        </section>
      </div>
  </div>
</template>
