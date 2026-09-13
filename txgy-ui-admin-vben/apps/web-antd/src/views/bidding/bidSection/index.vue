<script setup lang="ts">
/* 标段（包）台账（菜单 6772 / component bidding/bidSection/index / componentName BiddingBidSection
 *  权限 bidding:bid-section:query、登记 bidding:bid-section:create）
 * 契约：api/bidding/project 的标段接口（page/get/get-by-code/save/trace）
 * 口径：
 *   · 「所属招标项目」必填：标段须归属已登记的招标项目（后端校验）；
 *   · 无删除接口（标段为主数据，只能登记/修改），故不提供删除动作；
 *   · 时间字段为 epoch 毫秒 → 过 fmtDateTime；追溯一次取回标段 → 招标项目 → 项目 + 公告。 */
import { computed, h, onMounted, reactive, ref } from 'vue';

import { useAccess } from '@vben/access';
import { useAccessStore } from '@vben/stores';

import { message } from 'ant-design-vue';

import {
  getBidSection,
  getBidSectionPage,
  type ProjectApi,
  saveBidSection,
  traceBidSection,
} from '#/api/bidding/project';

import { fmtDateTime } from '../certificate/format';
import CopyText from '../components/CopyText.vue';

import '../certificate/cert.css';

const { hasAccessByCodes } = useAccess();
const accessStore = useAccessStore();

function can(code: string): boolean {
  const codes = accessStore.accessCodes ?? [];
  if (codes.length === 0) return true;
  return codes.includes('*') || codes.includes('*:*:*') || hasAccessByCodes([code]);
}

const query = reactive<ProjectApi.BidSectionPageParams>({
  pageNo: 1,
  pageSize: 10,
  bidSectionCode: undefined,
  bidSectionName: undefined,
  tenderProjectCode: undefined,
  state: undefined,
});
const rows = ref<ProjectApi.BidSection[]>([]);
const total = ref(0);
const loading = ref(false);

async function load() {
  loading.value = true;
  try {
    const page = await getBidSectionPage({ ...query });
    rows.value = page.list ?? [];
    total.value = page.total ?? 0;
  } catch {
    rows.value = [];
    total.value = 0;
  } finally {
    loading.value = false;
  }
}

function resetQuery() {
  query.bidSectionCode = undefined;
  query.bidSectionName = undefined;
  query.tenderProjectCode = undefined;
  query.state = undefined;
  query.pageNo = 1;
  void load();
}

onMounted(load);

const kpi = computed(() => {
  const list = rows.value;
  const active = list.filter((r) => (r.state ?? 'ACTIVE') === 'ACTIVE').length;
  const priced = list.filter((r) => !!r.contractReckonPrice).length;
  return { active, closed: list.length - active, priced };
});

const columns = [
  { title: '标段编号', customRender: ({ text }: { text?: null | number | string }) => h(CopyText, { text, label: '标段编号' }),  dataIndex: 'bidSectionCode', key: 'bidSectionCode', width: 200 },
  { title: '标段名称', dataIndex: 'bidSectionName', key: 'bidSectionName', width: 300 },
  { title: '所属招标项目', dataIndex: 'tenderProjectCode', key: 'tenderProjectCode', width: 200 },
  { title: '统一交易码', dataIndex: 'unifiedDealCode', key: 'unifiedDealCode', width: 180 },
  { title: '合同估算价', dataIndex: 'contractReckonPrice', key: 'contractReckonPrice', width: 150 },
  { title: '工期', dataIndex: 'timeLimit', key: 'timeLimit', width: 110 },
  { title: '状态', dataIndex: 'state', key: 'state', width: 100 },
  { title: '登记时间', dataIndex: 'createTime', key: 'createTime', width: 170 },
  { title: '操作', key: 'action', width: 150, fixed: 'right' as const },
];

const traceOpen = ref(false);
const traceLoading = ref(false);
const trace = ref<null | ProjectApi.Trace>(null);
const currentRow = ref<null | ProjectApi.BidSection>(null);

async function openTrace(r: ProjectApi.BidSection) {
  currentRow.value = r;
  traceOpen.value = true;
  traceLoading.value = true;
  trace.value = null;
  try {
    trace.value = await traceBidSection(r.bidSectionCode);
  } catch {
    trace.value = null;
  } finally {
    traceLoading.value = false;
  }
}

const fieldEntries = computed(() => {
  const f = (currentRow.value?.fields ?? {}) as Record<string, unknown>;
  return Object.entries(f).filter(([, v]) => v !== null && v !== undefined && v !== '');
});

const formOpen = ref(false);
const saving = ref(false);
const formRef = ref();
const form = reactive<ProjectApi.BidSection>({ bidSectionCode: '', tenderProjectCode: '', bidSectionName: '' });
const isEdit = computed(() => !!form.id);
const rules = {
  bidSectionCode: [{ required: true, message: '标段编号不能为空', trigger: 'blur' }],
  tenderProjectCode: [{ required: true, message: '所属招标项目编号不能为空', trigger: 'blur' }],
  bidSectionName: [{ required: true, message: '标段名称不能为空', trigger: 'blur' }],
};

function onCreate() {
  Object.assign(form, {
    id: undefined,
    bidSectionCode: '',
    tenderProjectCode: '',
    bidSectionName: '',
    unifiedDealCode: undefined,
    bidEctionNum: undefined,
    bidTenderMode: undefined,
    regionCode: undefined,
    traPlaceCode: undefined,
    bidSectionContent: undefined,
    bidSectionType: undefined,
    contractReckonPrice: undefined,
    currencyCode: undefined,
    priceUnit: undefined,
    timeLimit: undefined,
    bidQualification: undefined,
    isWebBidding: undefined,
    isNomeetBidding: undefined,
    state: 'ACTIVE',
    remark: undefined,
  });
  formOpen.value = true;
}

async function onEdit(r: ProjectApi.BidSection) {
  Object.assign(form, { ...r });
  if (r.id) {
    try {
      Object.assign(form, await getBidSection(r.id));
    } catch {
      /* 详情取不到时退回列表行数据 */
    }
  }
  formOpen.value = true;
}

async function submit() {
  try {
    await formRef.value?.validate();
  } catch {
    return;
  }
  saving.value = true;
  try {
    await saveBidSection({ ...form });
    message.success(isEdit.value ? '已保存' : '登记成功');
    formOpen.value = false;
    void load();
  } catch {
    // 失败原因由请求层统一 toast（含 400 字段级错误提示）
  } finally {
    saving.value = false;
  }
}
</script>

<template>
  <div class="ct-page">
    <div class="ct-head">
      <div>
        <div class="ct-title">标段（包）台账</div>
        <div class="ct-sub">标段须归属已登记的招标项目；追溯按标段维度取回招标项目、项目与公告</div>
      </div>
      <div class="ct-actions">
        <a-button v-if="can('bidding:bid-section:create')" type="primary" @click="onCreate">登记标段</a-button>
        <a-button @click="load">刷新</a-button>
      </div>
    </div>

    <div class="ct-kpis">
      <div class="ct-kpi k-blue">
        <div class="k-label">标段总数</div>
        <div class="k-num">{{ total }}</div>
        <div class="k-note">按当前筛选条件</div>
      </div>
      <div class="ct-kpi k-green">
        <div class="k-label">本页在建</div>
        <div class="k-num">{{ kpi.active }}</div>
        <div class="k-note">state=ACTIVE</div>
      </div>
      <div class="ct-kpi">
        <div class="k-label">本页有估算价</div>
        <div class="k-num">{{ kpi.priced }}</div>
        <div class="k-note">contractReckonPrice 非空</div>
      </div>
    </div>

    <div class="ct-panel">
      <div class="ct-filter">
        <a-input v-model:value="query.bidSectionCode" placeholder="标段编号" style="width: 200px" allow-clear @press-enter="load" />
        <a-input v-model:value="query.bidSectionName" placeholder="标段名称（模糊）" style="width: 220px" allow-clear @press-enter="load" />
        <a-input v-model:value="query.tenderProjectCode" placeholder="所属招标项目编号" style="width: 210px" allow-clear @press-enter="load" />
        <a-select
          v-model:value="query.state"
          placeholder="管理态"
          style="width: 130px"
          allow-clear
          :options="[
            { label: '在建（ACTIVE）', value: 'ACTIVE' },
            { label: '已关闭（CLOSED）', value: 'CLOSED' },
          ]"
          @change="load"
        />
        <a-button type="primary" @click="load">查询</a-button>
        <a-button @click="resetQuery">重置</a-button>
      </div>
      <div class="ct-panel-b">
        <a-table
          class="ct-table"
          :columns="columns"
          :data-source="rows"
          :loading="loading"
          :pagination="{
            current: query.pageNo,
            pageSize: query.pageSize,
            total,
            showSizeChanger: true,
            showTotal: (t: number) => `共 ${t} 条`,
          }"
          :scroll="{ x: 1500 }"
          row-key="id"
          size="middle"
          @change="(p: any) => { query.pageNo = p.current; query.pageSize = p.pageSize; void load(); }"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'bidSectionCode'">
              <div class="ct-id">{{ record.bidSectionCode }}</div>
            </template>
            <template v-else-if="column.key === 'contractReckonPrice'">
              {{ record.contractReckonPrice || '—' }}
              <span v-if="record.currencyCode" class="ct-panel-meta">{{ record.currencyCode }}</span>
            </template>
            <template v-else-if="column.key === 'state'">
              <a-tag :color="(record.state ?? 'ACTIVE') === 'ACTIVE' ? 'green' : 'default'">
                {{ (record.state ?? 'ACTIVE') === 'ACTIVE' ? '在建' : '已关闭' }}
              </a-tag>
            </template>
            <template v-else-if="column.key === 'createTime'">{{ fmtDateTime(record.createTime) }}</template>
            <template v-else-if="column.key === 'action'">
              <a-space size="small">
                <a @click="openTrace(record)">追溯</a>
                <a v-if="can('bidding:bid-section:create')" @click="onEdit(record)">编辑</a>
              </a-space>
            </template>
          </template>
          <template #emptyText><div class="ct-empty">暂无标段，点右上「登记标段」建档</div></template>
        </a-table>
      </div>
    </div>

    <a-drawer v-model:open="traceOpen" class="bid-ind" :width="1000" title="标段全景追溯" destroy-on-close>
      <a-spin :spinning="traceLoading">
        <template v-if="trace">
          <div class="ct-panel-meta mb-3">
            查询：{{ trace.queryCode || currentRow?.bidSectionCode }}（{{ trace.queryType || 'BID_SECTION' }}）
            <span v-for="(v, k) in trace.stats ?? {}" :key="k" style="margin-left: 12px">{{ k }}：{{ v }}</span>
          </div>

          <div class="ct-panel-t mb-2">所属项目</div>
          <a-descriptions :column="2" size="small" bordered class="mb-3">
            <a-descriptions-item label="项目编号">{{ trace.project?.projectCode || '—' }}</a-descriptions-item>
            <a-descriptions-item label="项目名称">{{ trace.project?.projectName || '—' }}</a-descriptions-item>
            <a-descriptions-item label="地区编码">{{ trace.project?.regionCode || '—' }}</a-descriptions-item>
            <a-descriptions-item label="法人">{{ trace.project?.legalPerson || '—' }}</a-descriptions-item>
          </a-descriptions>

          <div class="ct-panel-t mb-2">招标项目（{{ trace.tenderProjects?.length ?? 0 }}）</div>
          <a-table
            class="ct-table mb-3"
            :columns="[
              { title: '招标项目编号', customRender: ({ text }: { text?: null | number | string }) => h(CopyText, { text, label: '招标项目编号' }), dataIndex: 'tenderProjectCode', key: 'code', width: 200 },
              { title: '名称', dataIndex: 'tenderProjectName', key: 'name' },
              { title: '招标方式', dataIndex: 'tenderMode', key: 'mode', width: 120 },
              { title: '招标人', dataIndex: 'tendererName', key: 'tenderer', width: 200 },
            ]"
            :data-source="trace.tenderProjects ?? []"
            :pagination="false"
            row-key="id"
            size="small"
          >
            <template #emptyText><div class="ct-empty">无关联招标项目</div></template>
          </a-table>

          <div class="ct-panel-t mb-2">公告（{{ trace.notices?.length ?? 0 }}）</div>
          <a-table
            class="ct-table mb-3"
            :columns="[
              { title: '公告编号', customRender: ({ text }: { text?: null | number | string }) => h(CopyText, { text, label: '公告编号' }), dataIndex: 'noticeCode', key: 'code', width: 200 },
              { title: '标题', dataIndex: 'title', key: 'title' },
              { title: '类型', dataIndex: 'noticeTypeName', key: 'type', width: 130 },
              { title: '发布时间', dataIndex: 'publishTime', key: 'publishTime', width: 170 },
            ]"
            :data-source="trace.notices ?? []"
            :pagination="false"
            row-key="id"
            size="small"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'publishTime'">{{ fmtDateTime(record.publishTime) }}</template>
            </template>
            <template #emptyText><div class="ct-empty">无关联公告</div></template>
          </a-table>

          <div class="ct-panel-t mb-2">标段数据集字段</div>
          <a-descriptions v-if="fieldEntries.length" :column="2" size="small" bordered>
            <a-descriptions-item v-for="[k, v] in fieldEntries" :key="k" :label="k">
              {{ typeof v === 'object' ? JSON.stringify(v) : v }}
            </a-descriptions-item>
          </a-descriptions>
          <div v-else class="ct-tip">该记录未带数据集字段（fields 为空）</div>
        </template>
        <div v-else-if="!traceLoading" class="ct-empty">未取到追溯数据</div>
      </a-spin>
    </a-drawer>

    <a-modal
      v-model:open="formOpen"
      :title="isEdit ? `编辑标段 #${form.id ?? ''}` : '登记标段'"
      :width="860"
      :confirm-loading="saving"
      ok-text="保存"
      cancel-text="取消"
      destroy-on-close
      @ok="submit"
    >
      <a-form ref="formRef" :model="form" :rules="rules" layout="vertical">
        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item label="标段编号" name="bidSectionCode"><a-input v-model:value="form.bidSectionCode" /></a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="所属招标项目编号" name="tenderProjectCode">
              <a-input v-model:value="form.tenderProjectCode" placeholder="须为已登记的招标项目编号" />
            </a-form-item>
          </a-col>
          <a-col :span="24">
            <a-form-item label="标段名称" name="bidSectionName"><a-input v-model:value="form.bidSectionName" /></a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="统一交易码" name="unifiedDealCode"><a-input v-model:value="form.unifiedDealCode" /></a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="标段类型" name="bidSectionType"><a-input v-model:value="form.bidSectionType" /></a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="招标方式" name="bidTenderMode"><a-input v-model:value="form.bidTenderMode" /></a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="合同估算价" name="contractReckonPrice">
              <a-input v-model:value="form.contractReckonPrice" placeholder="按数据集口径填写" />
            </a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="币种" name="currencyCode"><a-input v-model:value="form.currencyCode" /></a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="价格单位" name="priceUnit"><a-input v-model:value="form.priceUnit" /></a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="工期" name="timeLimit"><a-input v-model:value="form.timeLimit" /></a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="地区编码" name="regionCode"><a-input v-model:value="form.regionCode" /></a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="交易地点码" name="traPlaceCode"><a-input v-model:value="form.traPlaceCode" /></a-form-item>
          </a-col>
          <a-col :span="24">
            <a-form-item label="投标资格要求" name="bidQualification">
              <a-textarea v-model:value="form.bidQualification" :rows="2" />
            </a-form-item>
          </a-col>
          <a-col :span="24">
            <a-form-item label="标段内容" name="bidSectionContent">
              <a-textarea v-model:value="form.bidSectionContent" :rows="2" />
            </a-form-item>
          </a-col>
          <a-col :span="24">
            <a-form-item label="备注" name="remark"><a-textarea v-model:value="form.remark" :rows="2" /></a-form-item>
          </a-col>
        </a-row>
      </a-form>
    </a-modal>
  </div>
</template>
