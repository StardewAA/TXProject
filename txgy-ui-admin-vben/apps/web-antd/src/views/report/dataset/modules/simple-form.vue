<script lang="ts" setup>
import type { ReportDatasetApi } from '#/api/report/dataset';

import { computed, ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { message } from 'ant-design-vue';

import {
  createSimpleDataset,
  getEnabledDatasetList,
  getSimpleCatalog,
} from '#/api/report/dataset';

import { CATEGORY_OPTIONS, buildDatasetCode } from '../code';
import { DEFAULT_BIZ_GROUPS } from '../data';

  getSimpleCatalog,
} from '#/api/report/dataset';

const emit = defineEmits(['success']);

/** 白名单目录 */
const catalog = ref<ReportDatasetApi.CatalogTable[]>([]);
const catalogLoading = ref(false);
/** 表单状态 */
const saving = ref(false);
/** 分类（域），用于生成数据集编码 */
const category = ref('MES');
const name = ref('');
const tableName = ref('');
const fields = ref<string[]>([]);
const dateField = ref<string | undefined>(undefined);
const owner = ref('');
const remark = ref('');
/** 业务分组（可多选、可新建） */
const bizGroups = ref<string[]>([]);
/** 分组候选项：预置分组 + 已有数据集用过的分组 */
const groupOptions = ref<string[]>([...DEFAULT_BIZ_GROUPS]);

/** 加载分组候选项（下拉里可直接输入新分组名） */
async function loadGroupOptions() {
  try {
    const list = await getEnabledDatasetList();
    const merged = new Set<string>(DEFAULT_BIZ_GROUPS);
    list.forEach((item) => {
      (item.bizGroups ?? []).forEach((group) => merged.add(group));
    });
    groupOptions.value = [...merged];
  } catch {
    // 拉取失败时退回预置候选项，不阻塞表单
  }
}

/** 当前选中表的列定义 */
const currentColumns = computed(() => {
  const table = catalog.value.find((item) => item.table === tableName.value);
  return table?.columns ?? [];
});

/** 可作为日期筛选列的候选 */
const dateColumns = computed(() =>
  currentColumns.value.filter((column) => column.isDate),
);

/** 切换表时清空已勾选字段 */
function handleTableChange() {
  fields.value = [];
  dateField.value = undefined;
}

/** 加载白名单目录 */
async function loadCatalog() {
  catalogLoading.value = true;
  try {
    catalog.value = await getSimpleCatalog();
  } finally {
    catalogLoading.value = false;
  }
}

const [Modal, modalApi] = useVbenModal({
  title: '新建报表数据集',
  title: '简单模式新建数据集（无需写 SQL）',
  class: 'w-[720px]',
  async onConfirm() {
    if (!name.value.trim()) {
      message.warning('请填写数据集名称');
      return;
    }
    if (!tableName.value) {
      message.warning('请选择数据表');
      return;
    }
    if (fields.value.length === 0) {
      message.warning('请至少勾选一个字段');
      return;
    }
    saving.value = true;
    modalApi.lock();
    try {
      // 自动生成数据集编码：RPT-[域]-[主题]-[序号]，不再让用户手填
      const existing = await getEnabledDatasetList();
      const code = buildDatasetCode(category.value, bizGroups.value, existing);
      await createSimpleDataset({
        code,
        category: category.value,
      await createSimpleDataset({
        name: name.value.trim(),
        tableName: tableName.value,
        fields: fields.value,
        dateField: dateField.value,
        bizGroups: bizGroups.value,
        owner: owner.value.trim(),
        remark: remark.value.trim(),
      });
      await modalApi.close();
      emit('success');
      message.success('数据集创建成功，可在「报表中心」查看');
    } finally {
      saving.value = false;
      modalApi.unlock();
    }
  },
  async onOpenChange(isOpen: boolean) {
    if (!isOpen) {
      category.value = 'MES';
      name.value = '';
      tableName.value = '';
      fields.value = [];
      dateField.value = undefined;
      bizGroups.value = [];
      owner.value = '';
      remark.value = '';
      return;
    }
    if (catalog.value.length === 0) {
      await loadCatalog();
    }
    void loadGroupOptions();
  },
});
</script>

<template>
  <Modal :confirm-disabled="saving">
    <a-spin :spinning="catalogLoading">
      <a-form class="mx-2 pr-2" layout="vertical">
        <a-form-item label="数据集名称" required>
          <a-input
            v-model:value="name"
            :maxlength="50"
            placeholder="例如：生产报工台账"
            show-count
          />
        </a-form-item>

        <a-form-item required label="分类（域）">
          <a-select
            v-model:value="category"
            :options="CATEGORY_OPTIONS.map((item) => ({ label: item, value: item }))"
            placeholder="请选择业务域"
          />
        </a-form-item>

        <a-form-item required label="数据表（白名单）">
          <a-select
            v-model:value="tableName"
            :options="
              catalog.map((item) => ({ label: item.label, value: item.table }))
            "
            placeholder="请选择业务表"
            show-search
            @change="handleTableChange"
          />
          <div class="mt-1 text-xs text-gray-400">
            仅开放业务上安全、结构稳定的表；不在目录里的表不能建报表
          </div>
        </a-form-item>

        <a-form-item label="所属分组（可多选、可输入新分组）">
          <a-select
            v-model:value="bizGroups"
            :options="
              groupOptions.map((group) => ({ label: group, value: group }))
            "
            mode="tags"
            placeholder="留空则归入「自定义」"
          />
        </a-form-item>

        <a-form-item v-if="tableName" required label="勾选字段（表头自动使用中文名）">
          <a-checkbox-group v-model:value="fields" class="w-full">
            <a-row>
              <a-col
                v-for="column in currentColumns"
                :key="column.field"
                :lg="8"
                :md="12"
                :sm="12"
                :span="8"
                :xs="24"
              >
                <a-checkbox :value="column.field">
                  {{ column.label }}
                  <span class="text-xs text-gray-400">{{ column.field }}</span>
                </a-checkbox>
              </a-col>
            </a-row>
          </a-checkbox-group>
        </a-form-item>

        <a-form-item v-if="dateColumns.length > 0" label="日期筛选列（可选）">
          <a-select
            v-model:value="dateField"
            allow-clear
            :options="
              dateColumns.map((column) => ({
                label: `${column.label}（${column.field}）`,
                value: column.field,
              }))
            "
            placeholder="选择后报表中心可按日期区间筛选"
          />
        </a-form-item>

        <a-form-item label="责任人">
          <a-input v-model:value="owner" :maxlength="30" />
        </a-form-item>

        <a-form-item label="备注">
          <a-input v-model:value="remark" :maxlength="100" />
        </a-form-item>

        <a-alert
          class="mb-2"
          message="提交后将自动创建报表数据集，可在「报表中心」查看"
          message="提交后由服务端自动生成 SQL，并自动带上租户隔离、行数上限与只读校验"
          show-icon
          type="info"
        />
      </a-form>
    </a-spin>
  </Modal>
</template>
