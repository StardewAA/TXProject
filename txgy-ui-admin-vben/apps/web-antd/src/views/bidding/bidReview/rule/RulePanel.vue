<script lang="ts" setup>
/**
 * 管理端 · 标书审查 - 审查规则库（菜单 60007200）
 *
 * 方案依据：《标书审查-管理端重新设计》§4.3.3。本次四处改动：
 *   1) 枚举（类别/类型/检查方式/级别）改从 bid_review_* 字典取，页面不再硬编码中文；
 *   2) 规则项列表改后端分页（原来是 pageSize:100 + 不分页硬拉）；
 *   3) 规则项补列：启用状态、满分、出处（字段本来就有，页面没展示）；
 *   4) 规则集补列：更新时间（判断"规则昨天有没有被改"）。
 *
 * 规则集来源（PLATFORM/TENDER_DOC/MIXED）没有对应字典，仍用本地选项——
 * 它是规则集的来源分类，不属于 bid_review_* 枚举段。
 */
import { onMounted, reactive, ref } from 'vue';

import {
  Button,
  Card,
  Form,
  FormItem,
  Input,
  InputNumber,
  message,
  Modal,
  Select,
  Space,
  Switch,
  Table,
  Tag,
  Textarea,
  Drawer,
} from 'ant-design-vue';

import {
  type BidReviewApi,
  createRuleItem,
  createRuleSet,
  deleteRuleItem,
  deleteRuleSet,
  getRuleItemPage,
  getRuleSet,
  getRuleSetPage,
  updateRuleItem,
  updateRuleSet,
} from '#/api/bidding/bidReview';
import { fmtDateTime } from '#/utils/datetime';

import BidReviewDictTag from '../components/BidReviewDictTag.vue';
import { useBiddingDict } from '../composables/useBiddingDict';
import '../panel.css';

const { dictLabel, dictOptions } = useBiddingDict();

/** 规则集来源：非字典枚举（见文件头说明） */
const sourceOptions = [
  { value: 'PLATFORM', label: '平台规则库' },
  { value: 'TENDER_DOC', label: '招标文件解析' },
  { value: 'MIXED', label: '混合' },
];

/* ===================== 规则集 ===================== */
const loading = ref(false);
const sets = ref<BidReviewApi.RuleSet[]>([]);
const total = ref(0);
const query = reactive<BidReviewApi.RuleSetPageReq>({
  pageNo: 1,
  pageSize: 10,
  ruleSetName: undefined,
  source: undefined,
});

const setFormOpen = ref(false);
const setForm = reactive<Partial<BidReviewApi.RuleSet>>({});
const savingSet = ref(false);

async function loadSets() {
  loading.value = true;
  try {
    const page = await getRuleSetPage(query);
    sets.value = page.list ?? [];
    total.value = Number(page.total ?? 0);
  } finally {
    loading.value = false;
  }
}

function searchSets() {
  query.pageNo = 1;
  void loadSets();
}

function openSetForm(record?: BidReviewApi.RuleSet) {
  Object.keys(setForm).forEach((k) => delete (setForm as any)[k]);
  Object.assign(setForm, record ?? { source: 'PLATFORM', enable: true, defaultFlag: false });
  setFormOpen.value = true;
}

async function saveSet() {
  if (!setForm.ruleSetName) {
    message.warning('请填写规则集名称');
    return;
  }
  savingSet.value = true;
  try {
    if (setForm.id) {
      await updateRuleSet(setForm);
    } else {
      await createRuleSet(setForm);
    }
    message.success('已保存');
    setFormOpen.value = false;
    await loadSets();
  } catch (error: any) {
    message.error(error?.message ?? '保存失败');
  } finally {
    savingSet.value = false;
  }
}

function removeSet(record: BidReviewApi.RuleSet) {
  Modal.confirm({
    title: `确认删除规则集「${record.ruleSetName}」？`,
    content: '被审查任务引用过的规则集不允许删除。',
    async onOk() {
      await deleteRuleSet(record.id);
      message.success('已删除');
      await loadSets();
    },
  });
}

/* ===================== 规则项（后端分页） ===================== */
const itemsOpen = ref(false);
const currentSet = ref<BidReviewApi.RuleSet | null>(null);
const items = ref<BidReviewApi.RuleItem[]>([]);
const itemsTotal = ref(0);
const itemsLoading = ref(false);
const itemsQuery = reactive({ pageNo: 1, pageSize: 10, category: undefined as string | undefined });

const itemFormOpen = ref(false);
const itemForm = reactive<Partial<BidReviewApi.RuleItem>>({});
const savingItem = ref(false);

async function openItems(record: BidReviewApi.RuleSet) {
  currentSet.value = await getRuleSet(record.id);
  itemsOpen.value = true;
  itemsQuery.pageNo = 1;
  itemsQuery.category = undefined;
  await loadItems();
}

async function loadItems() {
  if (!currentSet.value) return;
  itemsLoading.value = true;
  try {
    const page = await getRuleItemPage({
      pageNo: itemsQuery.pageNo,
      pageSize: itemsQuery.pageSize,
      ruleSetId: currentSet.value.id,
      category: itemsQuery.category,
    });
    items.value = page.list ?? [];
    itemsTotal.value = Number(page.total ?? 0);
  } finally {
    itemsLoading.value = false;
  }
}

function openItemForm(record?: BidReviewApi.RuleItem) {
  Object.keys(itemForm).forEach((k) => delete (itemForm as any)[k]);
  Object.assign(
    itemForm,
    record ?? {
      ruleSetId: currentSet.value?.id,
      category: 'FORM',
      ruleType: 'DISQUALIFY',
      checkMode: 'AI',
      severity: 'HIGH',
      enable: true,
      sort: itemsTotal.value + 1,
    },
  );
  itemFormOpen.value = true;
}

async function saveItem() {
  if (!itemForm.ruleName) {
    message.warning('请填写规则名称');
    return;
  }
  savingItem.value = true;
  try {
    if (itemForm.id) {
      await updateRuleItem(itemForm);
    } else {
      await createRuleItem(itemForm);
    }
    message.success('已保存');
    itemFormOpen.value = false;
    await loadItems();
    await loadSets();
  } catch (error: any) {
    message.error(error?.message ?? '保存失败');
  } finally {
    savingItem.value = false;
  }
}

function removeItem(record: BidReviewApi.RuleItem) {
  Modal.confirm({
    title: `确认删除规则「${record.ruleName}」？`,
    async onOk() {
      await deleteRuleItem(record.id);
      message.success('已删除');
      await loadItems();
      await loadSets();
    },
  });
}

onMounted(loadSets);
</script>

<template>
  <div class="br-page">
    <Card size="small" title="审查规则库">
      <template #extra>
        <Space>
          <Input
            v-model:value="query.ruleSetName"
            allow-clear
            placeholder="规则集名称"
            style="width: 200px"
            @press-enter="searchSets"
          />
          <Select
            v-model:value="query.source"
            allow-clear
            placeholder="来源"
            style="width: 150px"
            :options="sourceOptions"
          />
          <Button @click="searchSets">查询</Button>
          <Button type="primary" @click="openSetForm()">新建规则集</Button>
        </Space>
      </template>

      <Table
        size="small"
        row-key="id"
        :loading="loading"
        :data-source="sets"
        :scroll="{ x: 1200 }"
        :pagination="{
          current: query.pageNo,
          pageSize: query.pageSize,
          total,
          showSizeChanger: true,
          showTotal: (t: number) => `共 ${t} 条`,
          onChange: (p: number, s: number) => ((query.pageNo = p), (query.pageSize = s), loadSets()),
        }"
      >
        <a-table-column title="规则集名称" :width="240">
          <template #default="{ record }">
            <div>{{ record.ruleSetName }}</div>
            <div class="br-sub">{{ record.ruleSetCode }}</div>
          </template>
        </a-table-column>
        <a-table-column title="来源" :width="130">
          <template #default="{ record }">
            {{ sourceOptions.find((s) => s.value === record.source)?.label ?? record.source }}
          </template>
        </a-table-column>
        <a-table-column title="规则数" :width="90" data-index="ruleCnt" />
        <a-table-column title="默认" :width="80">
          <template #default="{ record }">
            <Tag v-if="record.defaultFlag" color="blue">默认</Tag>
            <span v-else>—</span>
          </template>
        </a-table-column>
        <a-table-column title="状态" :width="90">
          <template #default="{ record }">
            <Tag :color="record.enable ? 'green' : 'default'">{{ record.enable ? '启用' : '停用' }}</Tag>
          </template>
        </a-table-column>
        <a-table-column title="更新时间" :width="170">
          <template #default="{ record }">
            {{ fmtDateTime(record.updateTime ?? record.createTime) }}
          </template>
        </a-table-column>
        <a-table-column title="说明" :width="200" data-index="remark" />
        <a-table-column title="操作" :width="180" fixed="right">
          <template #default="{ record }">
            <Space size="small">
              <a @click="openItems(record)">规则项</a>
              <a @click="openSetForm(record)">编辑</a>
              <a class="br-danger" @click="removeSet(record)">删除</a>
            </Space>
          </template>
        </a-table-column>
      </Table>
    </Card>

    <!-- 规则集表单 -->
    <Modal
      v-model:open="setFormOpen"
      :title="setForm.id ? '编辑规则集' : '新建规则集'"
      :confirm-loading="savingSet"
      @ok="saveSet"
    >
      <Form layout="vertical">
        <FormItem label="规则集名称" required>
          <Input v-model:value="setForm.ruleSetName" placeholder="如：平台默认审查规则集" />
        </FormItem>
        <FormItem label="来源">
          <Select v-model:value="setForm.source" :options="sourceOptions" style="width: 220px" />
        </FormItem>
        <FormItem label="设为平台默认（个人端未上传招标文件时使用）">
          <Switch v-model:checked="setForm.defaultFlag" />
        </FormItem>
        <FormItem label="启用">
          <Switch v-model:checked="setForm.enable" />
        </FormItem>
        <FormItem label="说明">
          <Textarea v-model:value="setForm.remark" :rows="2" />
        </FormItem>
      </Form>
    </Modal>

    <!-- 规则项抽屉（后端分页） -->
    <Drawer v-model:open="itemsOpen" :title="`规则项 - ${currentSet?.ruleSetName ?? ''}`" width="1180">
      <Form layout="inline" class="br-query">
        <FormItem label="类别">
          <Select
            v-model:value="itemsQuery.category"
            allow-clear
            placeholder="全部"
            style="width: 160px"
            :options="dictOptions('bid_review_rule_category')"
            @change="((itemsQuery.pageNo = 1), loadItems())"
          />
        </FormItem>
        <FormItem>
          <Space>
            <Button type="primary" size="small" @click="openItemForm()">新增规则项</Button>
            <span class="br-sub">共 {{ itemsTotal }} 条</span>
          </Space>
        </FormItem>
      </Form>

      <Table
        size="small"
        row-key="id"
        :loading="itemsLoading"
        :data-source="items"
        :scroll="{ x: 1300 }"
        :pagination="{
          current: itemsQuery.pageNo,
          pageSize: itemsQuery.pageSize,
          total: itemsTotal,
          showSizeChanger: true,
          showTotal: (t: number) => `共 ${t} 条`,
          onChange: (p: number, s: number) => ((itemsQuery.pageNo = p), (itemsQuery.pageSize = s), loadItems()),
        }"
      >
        <a-table-column title="编码" data-index="ruleCode" :width="90" />
        <a-table-column title="规则名称" :width="220">
          <template #default="{ record }">
            <div>{{ record.ruleName }}</div>
            <div v-if="record.requirement" class="br-sub">{{ record.requirement }}</div>
          </template>
        </a-table-column>
        <a-table-column title="类别" :width="110">
          <template #default="{ record }">
            {{ dictLabel('bid_review_rule_category', record.category) }}
          </template>
        </a-table-column>
        <a-table-column title="类型" :width="100">
          <template #default="{ record }">
            {{ dictLabel('bid_review_rule_type', record.ruleType) }}
          </template>
        </a-table-column>
        <a-table-column title="检查方式" :width="110">
          <template #default="{ record }">
            {{ dictLabel('bid_review_check_mode', record.checkMode) }}
          </template>
        </a-table-column>
        <a-table-column title="级别" :width="90">
          <template #default="{ record }">
            <BidReviewDictTag dict-type="bid_review_severity" :value="record.severity" />
          </template>
        </a-table-column>
        <a-table-column title="满分" :width="80">
          <template #default="{ record }">{{ record.scoreMax ?? '—' }}</template>
        </a-table-column>
        <a-table-column title="出处" :width="160">
          <template #default="{ record }">{{ record.sourceRef || '—' }}</template>
        </a-table-column>
        <a-table-column title="状态" :width="80">
          <template #default="{ record }">
            <Tag :color="record.enable ? 'green' : 'default'">{{ record.enable ? '启用' : '停用' }}</Tag>
          </template>
        </a-table-column>
        <a-table-column title="操作" :width="120" fixed="right">
          <template #default="{ record }">
            <Space size="small">
              <a @click="openItemForm(record)">编辑</a>
              <a class="br-danger" @click="removeItem(record)">删除</a>
            </Space>
          </template>
        </a-table-column>
      </Table>
    </Drawer>

    <!-- 规则项表单 -->
    <Modal
      v-model:open="itemFormOpen"
      :title="itemForm.id ? '编辑规则项' : '新增规则项'"
      width="720"
      :confirm-loading="savingItem"
      @ok="saveItem"
    >
      <Form layout="vertical">
        <div class="br-form-row">
          <FormItem label="规则编码（留空自动生成）">
            <Input v-model:value="itemForm.ruleCode" placeholder="如 P001" style="width: 220px" />
          </FormItem>
          <FormItem label="规则名称" required>
            <Input v-model:value="itemForm.ruleName" placeholder="如 投标函是否盖章并签字" style="width: 380px" />
          </FormItem>
        </div>
        <div class="br-form-row">
          <FormItem label="类别">
            <Select
              v-model:value="itemForm.category"
              style="width: 180px"
              :options="dictOptions('bid_review_rule_category')"
            />
          </FormItem>
          <FormItem label="类型">
            <Select
              v-model:value="itemForm.ruleType"
              style="width: 160px"
              :options="dictOptions('bid_review_rule_type')"
            />
          </FormItem>
          <FormItem label="检查方式">
            <Select
              v-model:value="itemForm.checkMode"
              style="width: 170px"
              :options="dictOptions('bid_review_check_mode')"
            />
          </FormItem>
        </div>
        <div class="br-form-row">
          <FormItem label="严重级别（决定是否废标）">
            <Select
              v-model:value="itemForm.severity"
              style="width: 220px"
              :options="dictOptions('bid_review_severity')"
            />
          </FormItem>
          <FormItem label="满分（评分项填）">
            <InputNumber v-model:value="itemForm.scoreMax" :min="0" style="width: 140px" />
          </FormItem>
          <FormItem label="启用">
            <Switch v-model:checked="itemForm.enable" />
          </FormItem>
        </div>
        <FormItem label="要求原文（来自招标文件/平台规则）">
          <Textarea v-model:value="itemForm.requirement" :rows="3" />
        </FormItem>
        <FormItem label="检查点提示（规则类用关键词，多个用 | 分隔）">
          <Input v-model:value="itemForm.evidenceHint" placeholder="如：投标函|盖章|签字" />
        </FormItem>
        <FormItem label="出处（条款号/页码）">
          <Input v-model:value="itemForm.sourceRef" placeholder="如：第三章 投标人须知" />
        </FormItem>
      </Form>
    </Modal>
  </div>
</template>

<style scoped>
.br-danger {
  color: #ff4d4f;
}

.br-form-row {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
}
</style>
