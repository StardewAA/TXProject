<!--
  标书智能体 · 知识库管理
  定位：招投标专员的调优台——资料 / 切片 / 召回 / 评测四个 Tab。
  契约：src/api/bidding/bidAgent/index.ts（对应 BidAgentKnowledgeController）
  设计：Material Design 规范 · vben Page + Ant Tabs/Card/Table/Form
-->
<script lang="ts" setup>
import { computed, onMounted, reactive, ref } from 'vue';

import { Page } from '@vben/common-ui';
import { IconifyIcon } from '@vben/icons';

import {
  Alert,
  Button,
  Card,
  Col,
  DatePicker,
  Input,
  InputNumber,
  message,
  Modal,
  Popconfirm,
  Row,
  Select,
  Space,
  Steps,
  Switch,
  Table,
  TabPane,
  Tabs,
  Tag,
  Textarea,
  Upload,
} from 'ant-design-vue';

import {
  type BidAgentApi,
  deleteBidAgentEvalCase,
  deleteBidAgentMaterial,
  deleteBidAgentSegment,
  editBidAgentSegment,
  getBidAgentEvalCasePage,
  getBidAgentMaterialPage,
  getBidAgentSegmentPage,
  mergeBidAgentSegment,
  reindexBidAgentMaterial,
  retrieveBidAgent,
  runBidAgentEval,
  saveBidAgentEvalCase,
  saveBidAgentMaterial,
  splitBidAgentSegment,
} from '#/api/bidding/bidAgent';
import { uploadBiddingFile } from '#/api/bidding/file';
import { getBidSectionPage, getTenderProjectPage, type ProjectApi, saveBidSection } from '#/api/bidding/project';

const tab = ref('material');

const docTypeOptions = [
  { label: '招标文件 TENDER', value: 'TENDER' },
  { label: '企业资料 FACT', value: 'FACT' },
  { label: '技术母本 TEMPLATE', value: 'TEMPLATE' },
  { label: '报价数据 PRICING', value: 'PRICING' },
];
const docTypeColor: Record<string, string> = {
  TENDER: 'chip-blue',
  FACT: 'chip-purple',
  TEMPLATE: 'chip-amber',
  PRICING: 'chip-green',
};
const statusOptions = [
  { label: '待处理', value: 0 },
  { label: '已入库', value: 1 },
  { label: '失败', value: 2 },
];
const modeOptions = [
  { label: '混合（向量 + 关键词）', value: 'hybrid' },
  { label: '仅向量', value: 'vector' },
  { label: '仅关键词 BM25', value: 'bm25' },
];

function fail(error: any, fallback: string) {
  message.error(error?.message ?? fallback);
}

function fmtTime(v?: null | number | string) {
  if (!v) return '';
  if (typeof v === 'number') return new Date(v).toLocaleString();
  return String(v).replace('T', ' ').slice(0, 19);
}

// ============ 标段 / 招标项目（资料管理内嵌「标段管理」） ============
const sectionOptions = ref<Array<{ label: string; value: string }>>([]);
const sectionList = ref<ProjectApi.BidSection[]>([]);
const tenderOptions = ref<Array<{ label: string; value: string }>>([]);
const sectionForm = reactive({ bidSectionCode: '', bidSectionName: '', tenderProjectCode: '' });

async function loadSections() {
  try {
    const res = await getBidSectionPage({ pageNo: 1, pageSize: 200 });
    sectionList.value = res.list ?? [];
    sectionOptions.value = sectionList.value.map((item) => ({
      label: `${item.bidSectionCode ?? ''} ${item.bidSectionName ?? ''}`.trim(),
      value: String(item.bidSectionCode ?? ''),
    }));
  } catch {
    sectionOptions.value = [];
    sectionList.value = [];
  }
  syncSectionOptionsFromMaterials();
}

function syncSectionOptionsFromMaterials() {
  const options = new Map(sectionOptions.value.map((item) => [String(item.value), item]));
  for (const material of materials.value) {
    const code = material.bidSectionCode?.trim();
    if (code && !options.has(code)) {
      options.set(code, { label: code, value: code });
    }
  }
  sectionOptions.value = [...options.values()];
}

async function loadTenderProjects() {
  try {
    const res = await getTenderProjectPage({ pageNo: 1, pageSize: 200 });
    tenderOptions.value = (res.list ?? []).map((item) => ({
      label: `${item.tenderProjectCode ?? ''} ${item.tenderProjectName ?? ''}`.trim(),
      value: String(item.tenderProjectCode ?? ''),
    }));
  } catch {
    tenderOptions.value = [];
  }
}

async function saveSection() {
  if (!sectionForm.bidSectionCode || !sectionForm.bidSectionName || !sectionForm.tenderProjectCode) {
    message.warning('请填写标段编号、标段名称并选择所属招标项目');
    return;
  }
  try {
    await saveBidSection({
      bidSectionCode: sectionForm.bidSectionCode,
      tenderProjectCode: sectionForm.tenderProjectCode,
      bidSectionName: sectionForm.bidSectionName,
    });
    message.success('标段已登记');
    Object.assign(sectionForm, { bidSectionCode: '', bidSectionName: '', tenderProjectCode: '' });
    await loadSections();
  } catch (error) {
    fail(error, '标段登记失败');
  }
}

// ============ 资料管理 ============
const materials = ref<BidAgentApi.Material[]>([]);
const materialTotal = ref(0);
const materialQuery = reactive<{
  bidSectionCode?: string;
  docType?: string;
  indexStatus?: number;
  name?: string;
  pageNo: number;
  pageSize: number;
}>({ docType: undefined, bidSectionCode: undefined, indexStatus: undefined, name: undefined, pageNo: 1, pageSize: 10 });

const registerForm = reactive<{ bidSectionCode?: string; docType: string; projectId?: number; validUntil?: string }>({
  docType: 'TENDER',
  bidSectionCode: undefined,
  projectId: undefined,
  validUntil: undefined,
});
const uploading = ref(false);
const uploadStage = ref(-1); // -1 空闲；0 解析 1 切片 2 向量化 3 完成
const uploadStepTitles = ['文件解析', '切片生成', '建立索引', '入库完成'];
const uploadStepItems = computed<any[]>(() =>
  uploadStepTitles.map((title, i) => ({
    title,
    status:
      uploadStage.value === 3
        ? 'finish'
        : uploadStage.value < 0
          ? 'wait'
          : i === uploadStage.value
            ? 'process'
            : i < uploadStage.value
              ? 'finish'
              : 'wait',
  })),
);
let uploadTimer: number | undefined;

async function loadMaterials() {
  try {
    const res = await getBidAgentMaterialPage(materialQuery);
    materials.value = res.list;
    materialTotal.value = res.total;
    syncSectionOptionsFromMaterials();
  } catch (error) {
    fail(error, '材料列表加载失败');
  }
}

async function handleUpload(
  file: File,
  docType: string,
  bidSectionCode?: string,
  validUntil?: string,
  projectId?: number,
) {
  const resolvedBidSectionCode = bidSectionCode?.trim() || (docType === 'TENDER' ? 'DEFAULT' : undefined);
  if (docType === 'TENDER' && !bidSectionCode) {
    message.info('未选择标段，本次将归入默认标段（DEFAULT）');
  }
  uploading.value = true;
  uploadStage.value = 0;
  if (uploadTimer) window.clearInterval(uploadTimer);
  let stage = 0;
  uploadTimer = window.setInterval(() => {
    stage += 1;
    if (stage <= 2) uploadStage.value = stage;
  }, 900);
  try {
    const uploaded = await uploadBiddingFile(file, 'bidding/bid-agent');
    await saveBidAgentMaterial({
      docType,
      name: file.name,
      bidSectionCode: resolvedBidSectionCode,
      // 报价数据归属项目：与一键生成同口径（空=默认 0）
      projectId: docType === 'PRICING' ? projectId ?? 0 : undefined,
      fileId: uploaded.fileId,
      fileUrl: uploaded.url,
      fileName: file.name,
      fileSize: uploaded.size,
      validUntil: validUntil || undefined,
    });
    uploadStage.value = 3;
    message.success(docType === 'PRICING' ? '报价数据已解析入库' : '入库完成');
    await loadMaterials();
  } catch (error) {
    uploadStage.value = -1;
    fail(error, docType === 'PRICING' ? '报价 Excel 解析入库失败' : '上传入库失败');
  } finally {
    if (uploadTimer) window.clearInterval(uploadTimer);
    uploadTimer = undefined;
    uploading.value = false;
  }
}

function handleBeforeUpload(file: any) {
  void handleUpload(
    file as File,
    registerForm.docType,
    registerForm.bidSectionCode,
    registerForm.validUntil,
    registerForm.projectId,
  );
  return false;
}

async function handleReindex(id: number) {
  try {
    await reindexBidAgentMaterial(id);
    message.success('已触发重建索引，请稍后刷新查看结果');
    await loadMaterials();
  } catch (error) {
    fail(error, '重建索引失败');
  }
}

async function handleDeleteMaterial(id: number) {
  try {
    await deleteBidAgentMaterial(id);
    message.success('已删除（含向量与切片台账）');
    await loadMaterials();
  } catch (error) {
    fail(error, '删除失败');
  }
}

// ============ 切片管理 ============
const segments = ref<BidAgentApi.Segment[]>([]);
const segmentMaterial = ref<BidAgentApi.Material>();
const selectedSegmentKeys = ref<(number | string)[]>([]);
const segmentEditVisible = ref(false);
const segmentEditForm = reactive<{ content: string; id?: number; }>({ id: undefined, content: '' });

async function loadSegments(material: BidAgentApi.Material) {
  try {
    const res = await getBidAgentSegmentPage({ materialId: material.id as number, pageNo: 1, pageSize: 200 });
    segments.value = res.list ?? [];
    segmentMaterial.value = material;
    selectedSegmentKeys.value = [];
    tab.value = 'segment';
  } catch (error) {
    fail(error, '切片加载失败');
  }
}

async function reloadSegments() {
  if (!segmentMaterial.value) return;
  const res = await getBidAgentSegmentPage({
    materialId: segmentMaterial.value.id as number,
    pageNo: 1,
    pageSize: 200,
  });
  segments.value = res.list ?? [];
}

function openEditSegment(row: BidAgentApi.Segment) {
  segmentEditForm.id = row.id;
  segmentEditForm.content = row.content ?? '';
  segmentEditVisible.value = true;
}

async function saveSegmentEdit() {
  if (!segmentEditForm.id || !segmentEditForm.content?.trim()) {
    message.warning('切片内容不能为空');
    return;
  }
  try {
    await editBidAgentSegment({ id: segmentEditForm.id, content: segmentEditForm.content });
    message.success('切片已更新');
    segmentEditVisible.value = false;
    await reloadSegments();
  } catch (error) {
    fail(error, '切片编辑失败');
  }
}

async function handleSplitSegment(row: BidAgentApi.Segment) {
  try {
    await splitBidAgentSegment(row.id as number);
    message.success('已拆分');
    await reloadSegments();
  } catch (error) {
    fail(error, '拆分失败');
  }
}

async function handleMergeSegments() {
  if (selectedSegmentKeys.value.length < 2) {
    message.warning('请至少勾选两条相邻切片');
    return;
  }
  try {
    await mergeBidAgentSegment(selectedSegmentKeys.value.map((key) => Number(key)));
    message.success('已合并');
    selectedSegmentKeys.value = [];
    await reloadSegments();
  } catch (error) {
    fail(error, '合并失败');
  }
}

async function handleDeleteSegments() {
  if (!selectedSegmentKeys.value.length) {
    message.warning('请先勾选要删除的切片');
    return;
  }
  try {
    await deleteBidAgentSegment(selectedSegmentKeys.value.map((key) => Number(key)));
    message.success('已删除');
    selectedSegmentKeys.value = [];
    await reloadSegments();
  } catch (error) {
    fail(error, '删除失败');
  }
}

function onSegmentSelect(keys: (number | string)[]) {
  selectedSegmentKeys.value = keys;
}

// ============ 召回调试 ============
const retrieveForm = reactive<{
  bidSectionCode?: string;
  docType?: string;
  minScore?: number;
  mode: string;
  query: string;
  topK: number;
}>({ query: '', docType: undefined, bidSectionCode: undefined, topK: 8, minScore: undefined, mode: 'hybrid' });
const retrieveResult = ref<BidAgentApi.RetrieveResp>();
const retrieving = ref(false);

async function handleRetrieve() {
  if (!retrieveForm.query?.trim()) {
    message.warning('请输入要验证的业务问题');
    return;
  }
  if (retrieving.value) return;
  retrieving.value = true;
  retrieveResult.value = undefined;
  try {
    retrieveResult.value = await retrieveBidAgent({
      ...retrieveForm,
      query: retrieveForm.query.trim(),
      bidSectionCode: retrieveForm.bidSectionCode?.trim() || undefined,
    });
  } catch (error: any) {
    const msg = error?.response?.data?.msg ?? error?.data?.msg ?? error?.message ?? '召回验证失败';
    message.error(msg);
  } finally {
    retrieving.value = false;
  }
}

function jumpToSegment(sourceDocName?: null | string) {
  const material = materials.value.find((item) => item.name === sourceDocName);
  if (!material) {
    message.warning('未找到对应材料，请先在资料管理确认该文件已入库');
    return;
  }
  loadSegments(material);
}

const saveCaseVisible = ref(false);
const retrieveCaseForm = reactive<{
  bidSectionCode?: string;
  docType?: string;
  expectBlockIds?: string;
  expectKeywords?: string;
  name: string;
  query: string;
  topK: number;
}>({
  name: '',
  query: '',
  docType: undefined,
  bidSectionCode: undefined,
  expectBlockIds: undefined,
  expectKeywords: undefined,
  topK: 8,
});

function openSaveCase() {
  if (!retrieveForm.query?.trim()) {
    message.warning('请先输入并验证一个业务问题');
    return;
  }
  if (!retrieveResult.value?.hit) {
    message.warning('请先执行一次成功召回的验证，再保存测试用例');
    return;
  }
  const firstMatchedBlockIds = retrieveResult.value.chunks?.[0]?.sourceBlockIds ?? [];
  retrieveCaseForm.name = retrieveForm.query.slice(0, 40);
  retrieveCaseForm.query = retrieveForm.query.trim();
  retrieveCaseForm.docType = retrieveForm.docType;
  retrieveCaseForm.bidSectionCode = retrieveForm.bidSectionCode;
  retrieveCaseForm.topK = retrieveForm.topK;
  retrieveCaseForm.expectBlockIds = firstMatchedBlockIds.join(', ');
  retrieveCaseForm.expectKeywords = undefined;
  saveCaseVisible.value = true;
}

async function saveRetrieveCase() {
  if (!retrieveCaseForm.name || !retrieveCaseForm.query) {
    message.warning('用例名称与标准问题必填');
    return;
  }
  if (!retrieveCaseForm.expectKeywords?.trim() && !retrieveCaseForm.expectBlockIds?.trim()) {
    message.warning('请填写期望关键词或期望溯源块号，避免保存为未标注用例');
    return;
  }
  try {
    await saveBidAgentEvalCase({
      name: retrieveCaseForm.name,
      query: retrieveCaseForm.query,
      docType: retrieveCaseForm.docType,
      bidSectionCode: retrieveCaseForm.bidSectionCode,
      topK: retrieveCaseForm.topK,
      expectKeywords: retrieveCaseForm.expectKeywords?.trim() || undefined,
      expectBlockIds: retrieveCaseForm.expectBlockIds?.trim() || undefined,
    });
    message.success('已保存为测试用例（精度评测）');
    saveCaseVisible.value = false;
    await loadEvalCases();
  } catch (error) {
    fail(error, '保存用例失败');
  }
}

// ============ 精度评测 ============
const evalCases = ref<BidAgentApi.EvalCase[]>([]);
const evalCaseTotal = ref(0);
const evalResult = ref<BidAgentApi.EvalRunResp>();
const runningEval = ref(false);
const evalTopK = ref(3);

function clearEvalResult() {
  evalResult.value = undefined;
}
const caseForm = reactive<BidAgentApi.EvalCase>({
  name: '',
  query: '',
  docType: undefined,
  bidSectionCode: undefined,
  topK: 10,
  expectKeywords: undefined,
  expectBlockIds: undefined,
  enabled: 1,
  remark: undefined,
});
const caseEditing = ref(false);

async function loadEvalCases() {
  try {
    const res = await getBidAgentEvalCasePage({ pageNo: 1, pageSize: 200 });
    evalCases.value = [...res.list].sort((a, b) => Number(b.createTime ?? 0) - Number(a.createTime ?? 0));
    evalCaseTotal.value = res.total;
  } catch (error) {
    fail(error, '用例列表加载失败');
  }
}

function resetCaseForm() {
  Object.assign(caseForm, {
    id: undefined,
    name: '',
    query: '',
    docType: undefined,
    bidSectionCode: undefined,
    topK: 10,
    expectKeywords: undefined,
    expectBlockIds: undefined,
    enabled: 1,
    remark: undefined,
  });
  caseEditing.value = false;
}

function editCase(record: BidAgentApi.EvalCase) {
  Object.assign(caseForm, { ...record });
  caseEditing.value = true;
}

async function saveCase() {
  if (!caseForm.name || !caseForm.query) {
    message.warning('用例名称与标准问题必填');
    return;
  }
  try {
    await saveBidAgentEvalCase({ ...caseForm });
    message.success(caseForm.id ? '用例已更新' : '用例已新增');
    resetCaseForm();
    await loadEvalCases();
  } catch (error) {
    fail(error, '用例保存失败');
  }
}

const channelLabelMap: Record<string, string> = {
  rerank: 'Rerank 重排',
  vector: '向量检索',
  bm25: '关键词 BM25',
  mixed: '混合融合',
  none: '无结果',
};

function channelLabel(channel?: null | string) {
  return channel ? channelLabelMap[channel] ?? channel : '—';
}

function formatRate(value?: null | number) {
  return value == null ? '—' : `${(value * 100).toFixed(2)}%`;
}

const evalChannelSummary = computed(() => {
  const counts = evalResult.value?.channelCounts ?? {};
  const parts = Object.entries(counts)
    .filter(([, count]) => count > 0)
    .map(([channel, count]) => `${channelLabel(channel)} ${count} 条`);
  return parts.length > 0 ? parts.join('，') : '—';
});

const evalScoreDescription = computed(() => {
  if (!evalResult.value) return '';
  if (evalResult.value.scoreAvailable && evalResult.value.avgScore != null) {
    return `本次分数口径：${channelLabel(evalResult.value.scoreType)}。`;
  }
  if (evalResult.value.scoreType === 'bm25') {
    return '本次实际使用关键词 BM25，BM25 只提供相关度排名，不产生相似度分数，因此平均命中分数不适用。';
  }
  return '本次未产生可用的数值分数，平均命中分数不参与质量判断。';
});

async function handleRunEval() {
  if (runningEval.value) return;
  const runTopK = Number(evalTopK.value);
  if (!Number.isInteger(runTopK) || runTopK < 1 || runTopK > 50) {
    message.warning('TopK 必须是 1～50 的整数');
    return;
  }
  runningEval.value = true;
  try {
    evalResult.value = await runBidAgentEval({ topK: runTopK });
    const rate = evalResult.value?.caseHitRate;
    const topKText = `全局 TopK=${runTopK}`;
    message.success(
      rate == null
        ? `跑批完成：暂无可计分的已标注用例（${topKText}）`
        : `跑批完成：用例命中率 = ${formatRate(rate)}（${topKText}）`,
    );
  } catch (error) {
    fail(error, '跑批失败');
  } finally {
    runningEval.value = false;
  }
}

async function handleDeleteCase(id: number) {
  try {
    await deleteBidAgentEvalCase(id);
    await loadEvalCases();
  } catch (error) {
    fail(error, '用例删除失败');
  }
}

async function toggleCaseEnabled(record: BidAgentApi.EvalCase) {
  try {
    await saveBidAgentEvalCase({ ...record, enabled: record.enabled === 1 ? 0 : 1 });
    await loadEvalCases();
  } catch (error) {
    fail(error, '用例状态切换失败');
  }
}

onMounted(async () => {
  await Promise.all([loadMaterials(), loadEvalCases(), loadSections(), loadTenderProjects()]);
});
</script>

<template>
  <Page auto-content-height>
    <!-- 页面标题 -->
    <div class="page-header">
      <div class="page-header-main">
        <h1 class="page-title">知识库管理</h1>
      </div>
    </div>

    <!-- Tabs 容器：Material 风格的卡片化 tabs -->
    <Card :bordered="false" class="surface-card">
      <Tabs v-model:active-key="tab" class="kb-tabs" :tab-bar-style="{ marginBottom: '20px' }">
        <!-- ============ Tab1 资料管理 ============ -->
        <TabPane key="material" tab="资料管理">
          <div class="material-workspace">
            <div class="material-primary">
              <Card :bordered="false" class="module-card action-card">
                <template #title>
                  <div class="card-title">
                    <span class="step-index">01</span>
                    <IconifyIcon icon="lucide:upload-cloud" class="card-title-icon" />
                    <span>上传入库</span>
                  </div>
                </template>
                <div class="control-stack">
                  <Select
                    v-model:value="registerForm.docType"
                    :options="docTypeOptions"
                    aria-label="材料角色"
                    class="form-control"
                  />
                  <Select
                    v-model:value="registerForm.bidSectionCode"
                    :options="sectionOptions"
                    :disabled="registerForm.docType !== 'TENDER'"
                    allow-clear
                    show-search
                    aria-label="所属标段"
                    class="form-control"
                    placeholder="所属标段"
                  />
                  <InputNumber
                    v-if="registerForm.docType === 'PRICING'"
                    v-model:value="registerForm.projectId"
                    :min="0"
                    :precision="0"
                    aria-label="项目编号"
                    class="form-control"
                    placeholder="项目编号（空=默认 0）"
                  />
                  <DatePicker
                    v-model:value="registerForm.validUntil"
                    value-format="YYYY-MM-DD"
                    aria-label="有效期"
                    class="form-control"
                    placeholder="有效期（可空）"
                  />
                  <Upload
                    :show-upload-list="false"
                    :disabled="uploading"
                    :before-upload="handleBeforeUpload"
                    class="primary-action"
                  >
                    <Button type="primary" :loading="uploading" block>
                      <template #icon><IconifyIcon icon="lucide:upload" /></template>
                      选择文件并登记入库
                    </Button>
                  </Upload>
                </div>
                <Steps
                  v-if="uploading"
                  class="mt-4"
                  size="small"
                  :current="uploadStage < 0 ? 0 : uploadStage"
                  :items="uploadStepItems"
                />
                <div v-if="!uploading" class="kb-helper">
                  <IconifyIcon icon="lucide:info" class="kb-helper-icon" />
                  <span>
                    入库失败会在列表「入库结果」写明原因，修正后点该行的「重建索引」重试。
                    <template v-if="registerForm.docType === 'PRICING'">
                      报价数据请按「投标标书_报价数据标准模板.xlsx」填写，上传后自动解析入库。
                    </template>
                  </span>
                </div>
              </Card>

              <Card :bordered="false" class="module-card action-card">
                <template #title>
                  <div class="card-title">
                    <span class="step-index">02</span>
                    <IconifyIcon icon="lucide:folder-tree" class="card-title-icon" />
                    <span>标段管理</span>
                  </div>
                </template>
                <div class="control-grid control-grid--section">
                  <Input
                    v-model:value="sectionForm.bidSectionCode"
                    aria-label="标段编号"
                    class="form-control"
                    placeholder="标段编号"
                  />
                  <Input
                    v-model:value="sectionForm.bidSectionName"
                    aria-label="标段名称"
                    class="form-control"
                    placeholder="标段名称"
                  />
                  <Select
                    v-model:value="sectionForm.tenderProjectCode"
                    :options="tenderOptions"
                    show-search
                    aria-label="所属招标项目"
                    class="form-control"
                    placeholder="所属招标项目"
                  />
                  <Button class="primary-action--button" type="primary" @click="saveSection">
                    <template #icon><IconifyIcon icon="lucide:plus" /></template>
                    登记标段
                  </Button>
                </div>
                <Table
                  class="module-table"
                  :data-source="sectionList"
                  :pagination="false"
                  :scroll="{ x: 720, y: 280 }"
                  size="small"
                  row-key="bidSectionCode"
                >
                  <Table.Column title="标段编号" data-index="bidSectionCode" :width="180">
                    <template #default="{ record }">
                      <code class="code-text">{{ record.bidSectionCode }}</code>
                    </template>
                  </Table.Column>
                  <Table.Column title="标段名称" data-index="bidSectionName" />
                  <Table.Column title="所属招标项目" data-index="tenderProjectCode" :width="200">
                    <template #default="{ record }">
                      <code class="code-text">{{ record.tenderProjectCode }}</code>
                    </template>
                  </Table.Column>
                </Table>
              </Card>
            </div>

            <Card :bordered="false" class="module-card material-ledger">
              <template #title>
                <div class="card-title">
                  <IconifyIcon icon="lucide:files" class="card-title-icon" />
                  <span>已入库资料</span>
                  <Tag class="card-title-tag">共 {{ materialTotal }} 份</Tag>
                </div>
              </template>

              <div class="filter-grid">
                <Select
                  v-model:value="materialQuery.bidSectionCode"
                  :options="sectionOptions"
                  allow-clear
                  show-search
                  aria-label="所属标段"
                  class="form-control"
                  placeholder="所属标段"
                />
                <Select
                  v-model:value="materialQuery.docType"
                  :options="docTypeOptions"
                  allow-clear
                  aria-label="材料角色"
                  class="form-control"
                  placeholder="材料角色"
                />
                <Select
                  v-model:value="materialQuery.indexStatus"
                  :options="statusOptions"
                  allow-clear
                  aria-label="入库状态"
                  class="form-control"
                  placeholder="入库状态"
                />
                <Input
                  v-model:value="materialQuery.name"
                  aria-label="文件名"
                  class="form-control"
                  placeholder="文件名"
                  allow-clear
                />
                <Button class="filter-action" type="primary" @click="loadMaterials">
                  <template #icon><IconifyIcon icon="lucide:search" /></template>
                  查询
                </Button>
              </div>

              <Table
                class="module-table"
                :data-source="materials"
                :pagination="{
                  current: materialQuery.pageNo,
                  pageSize: materialQuery.pageSize,
                  total: materialTotal,
                  onChange: (p: number) => {
                    materialQuery.pageNo = p;
                    loadMaterials();
                  },
                }"
                :row-key="(row: BidAgentApi.Material) => String(row.id)"
                :scroll="{ x: 1200 }"
                size="middle"
              >
                <Table.Column title="文件名" data-index="name" :min-width="220">
                  <template #default="{ record }">
                    <div class="cell-strong">{{ record.name }}</div>
                  </template>
                </Table.Column>
                <Table.Column title="角色" data-index="docType" :width="110">
                  <template #default="{ record }">
                    <Tag :class="docTypeColor[record.docType] || 'chip-neutral'">{{ record.docType }}</Tag>
                  </template>
                </Table.Column>
                <Table.Column title="所属标段" data-index="bidSectionCode" :width="160">
                  <template #default="{ record }">
                    <code v-if="record.bidSectionCode" class="code-text">{{ record.bidSectionCode }}</code>
                    <span v-else class="cell-sub">—</span>
                  </template>
                </Table.Column>
                <Table.Column title="切片数" :width="90" align="center">
                  <template #default="{ record }">
                    <Tag class="chip-neutral">{{ record.segmentCount || 0 }}</Tag>
                  </template>
                </Table.Column>
                <Table.Column title="入库状态" :width="120">
                  <template #default="{ record }">
                    <span
                      class="status-dot"
                      :class="record.indexStatus === 1 ? 'is-on' : record.indexStatus === 2 ? 'is-danger' : 'is-pending'"
                    ></span>
                    <span>{{ record.indexStatus === 1 ? '已入库' : record.indexStatus === 2 ? '失败' : '待处理' }}</span>
                  </template>
                </Table.Column>
                <Table.Column title="入库结果" data-index="indexMessage">
                  <template #default="{ record }">
                    <span class="cell-ellipsis" :title="record.indexMessage ?? ''">{{ record.indexMessage || '—' }}</span>
                  </template>
                </Table.Column>
                <Table.Column title="入库时间" :width="170">
                  <template #default="{ record }">{{ fmtTime(record.createTime) }}</template>
                </Table.Column>
                <Table.Column title="操作" :width="220" align="right">
                  <template #default="{ record }">
                    <Space size="small">
                      <Button type="link" size="small" @click="loadSegments(record)">看切片</Button>
                      <Popconfirm
                        title="重新解析入库会按原文件重新切片，丢弃人工修改，确认继续？"
                        @confirm="handleReindex(record.id)"
                      >
                        <Button type="link" size="small">重建索引</Button>
                      </Popconfirm>
                      <Popconfirm title="删除后切片一并清理，确认？" @confirm="handleDeleteMaterial(record.id)">
                        <Button type="link" size="small" danger>删除</Button>
                      </Popconfirm>
                    </Space>
                  </template>
                </Table.Column>
              </Table>
            </Card>
          </div>
        </TabPane>
        <!-- ============ Tab2 切片管理 ============ -->
        <TabPane key="segment" tab="切片管理">
          <div class="segment-header">
            <div>
              <div class="cell-sub">当前材料</div>
              <div class="segment-material">{{ segmentMaterial?.name || '—' }}</div>
            </div>
            <Space>
              <Popconfirm
                title="合并会拼接选中切片并删除其余，确认？"
                :disabled="selectedSegmentKeys.length < 2"
                @confirm="handleMergeSegments"
              >
                <Button :disabled="selectedSegmentKeys.length < 2">
                  <template #icon><IconifyIcon icon="lucide:combine" /></template>
                  合并选中（{{ selectedSegmentKeys.length }}）
                </Button>
              </Popconfirm>
              <Popconfirm title="删除选中的切片，确认？" @confirm="handleDeleteSegments">
                <Button danger :disabled="!selectedSegmentKeys.length">
                  <template #icon><IconifyIcon icon="lucide:trash-2" /></template>
                  删除选中
                </Button>
              </Popconfirm>
            </Space>
          </div>

          <Table
            :data-source="segments"
            :pagination="false"
            :row-key="(row: BidAgentApi.Segment) => String(row.id)"
            :row-selection="{ selectedRowKeys: selectedSegmentKeys, onChange: onSegmentSelect }"
            size="middle"
          >
            <Table.Column title="#" data-index="chunkNo" :width="70" align="center">
              <template #default="{ record }">
                <code class="code-text">{{ record.chunkNo }}</code>
              </template>
            </Table.Column>
            <Table.Column title="溯源块号" data-index="sourceBlockIds" :width="200">
              <template #default="{ record }">
                <code v-if="record.sourceBlockIds" class="code-text cell-ellipsis" :title="record.sourceBlockIds">{{ record.sourceBlockIds }}</code>
                <span v-else class="cell-sub">—</span>
              </template>
            </Table.Column>
            <Table.Column title="页码" data-index="pageNo" :width="80" align="center">
              <template #default="{ record }">
                <span v-if="record.pageNo">P{{ record.pageNo }}</span>
                <span v-else class="cell-sub">—</span>
              </template>
            </Table.Column>
            <Table.Column title="切片内容">
              <template #default="{ record }">
                <div class="segment-cell">{{ record.content }}</div>
              </template>
            </Table.Column>
            <Table.Column title="人工修改" :width="110" align="center">
              <template #default="{ record }">
                <Tag v-if="record.edited === 1" class="chip-amber">已修改</Tag>
                <span v-else class="cell-sub">机器切片</span>
              </template>
            </Table.Column>
            <Table.Column title="操作" :width="140" align="right">
              <template #default="{ record }">
                <Space size="small">
                  <Button type="link" size="small" @click="openEditSegment(record)">编辑</Button>
                  <Button type="link" size="small" @click="handleSplitSegment(record)">拆分</Button>
                </Space>
              </template>
            </Table.Column>
          </Table>
        </TabPane>

        <!-- ============ Tab3 召回调试 ============ -->
        <TabPane key="retrieve" tab="召回调试">
          <Card :bordered="false" class="surface-card mb-4">
            <Space wrap :style="{ marginBottom: '12px' }">
              <Input
                v-model:value="retrieveForm.query"
                placeholder="例如：安全生产许可证要求"
                style="width: 340px"
                allow-clear
              />
              <Select v-model:value="retrieveForm.docType" :options="docTypeOptions" allow-clear placeholder="角色" style="width: 180px" />
              <Select v-model:value="retrieveForm.bidSectionCode" :options="sectionOptions" allow-clear show-search placeholder="标段（可空）" style="width: 240px" />
              <Select v-model:value="retrieveForm.mode" :options="modeOptions" style="width: 200px" />
              <InputNumber v-model:value="retrieveForm.topK" :min="1" :max="50" addon-before="TopK" />
              <InputNumber
                v-model:value="retrieveForm.minScore"
                :min="0"
                :max="1"
                :step="0.05"
                addon-before="阈值"
                placeholder="配置"
              />
              <Button type="primary" :loading="retrieving" @click="handleRetrieve">
                <template #icon><IconifyIcon icon="lucide:search" /></template>
                验证召回
              </Button>
              <Button @click="openSaveCase">
                <template #icon><IconifyIcon icon="lucide:bookmark-plus" /></template>
                保存为测试用例
              </Button>
            </Space>

            <Alert
              v-if="retrieveResult && !retrieveResult.hit"
              type="warning"
              show-icon
              class="callout-warning"
              message="未命中"
              description="资料可能未入库、标段选错，或问法与原文差异过大（前台遇到同样情况会转人工）"
            />
          </Card>

          <div v-if="retrieveResult">
            <div class="section-head">
              <div>
                <h3 class="section-title">召回结果</h3>
                <p class="section-sub">
                  命中 {{ retrieveResult.chunks?.length ?? 0 }} 条，检索耗时 {{ retrieveResult.elapsedMs ?? '-' }} ms
                </p>
              </div>
            </div>
            <Table :data-source="retrieveResult.chunks || []" :pagination="false" size="middle" row-key="chunkId">
              <Table.Column title="命中方式" data-index="matchedBy" :width="130">
                <template #default="{ record }">
                  <Tag :class="record.matchedBy === 'rerank' ? 'chip-purple' : record.matchedBy === 'hybrid' ? 'chip-blue' : 'chip-neutral'">
                    {{ record.matchedBy || '—' }}
                  </Tag>
                </template>
              </Table.Column>
              <Table.Column title="分数" :width="110">
                <template #default="{ record }">
                  <span v-if="record.score != null" class="score-bar">
                    <span class="score-bar-bg">
                      <span class="score-bar-fill" :style="{ width: `${Math.min(100, record.score * 100)}%` }"></span>
                    </span>
                    <span class="score-bar-text">{{ record.score.toFixed(4) }}</span>
                  </span>
                  <span v-else class="cell-sub">—</span>
                </template>
              </Table.Column>
              <Table.Column title="来源" :width="220">
                <template #default="{ record }">
                  <Button type="link" size="small" @click="jumpToSegment(record.sourceDocName)">
                    {{ record.sourceDocName }}
                  </Button>
                </template>
              </Table.Column>
              <Table.Column title="页码" data-index="pageNo" :width="80" align="center">
                <template #default="{ record }">
                  <span v-if="record.pageNo">P{{ record.pageNo }}</span>
                  <span v-else class="cell-sub">—</span>
                </template>
              </Table.Column>
              <Table.Column title="片段" data-index="content">
                <template #default="{ record }">
                  <div class="retrieve-cell">{{ record.content }}</div>
                </template>
              </Table.Column>
            </Table>
          </div>
        </TabPane>

        <!-- ============ Tab4 精度评测 ============ -->
        <TabPane key="eval" tab="精度评测">
          <!-- KPI 卡片 -->
          <Row :gutter="12" class="mb-4">
            <Col :span="8">
              <Card :bordered="false" class="kpi-card">
                <div class="kpi-label">已标注用例命中率</div>
                <div
                  class="kpi-value"
                  :class="evalResult?.caseHitRate == null ? '' : evalResult.caseHitRate >= 0.8 ? 'kpi-good' : 'kpi-warn'"
                >
                  {{ formatRate(evalResult?.caseHitRate) }}
                </div>
                <div class="kpi-foot">
                  命中 {{ evalResult?.hitCount ?? '—' }} / {{ evalResult?.evaluatedCount ?? '—' }} 条已标注用例
                </div>
              </Card>
            </Col>
            <Col :span="8">
              <Card :bordered="false" class="kpi-card">
                <div class="kpi-label">平均命中相关度分</div>
                <div class="kpi-value kpi-blue">
                  <template v-if="evalResult?.scoreAvailable && evalResult?.avgScore != null">
                    {{ evalResult.avgScore.toFixed(4) }}
                  </template>
                  <span v-else-if="evalResult" class="kpi-unavailable">不适用</span>
                  <span v-else>—</span>
                </div>
                <div class="kpi-foot">{{ evalResult?.scoreAvailable ? `分数口径：${channelLabel(evalResult.scoreType)}` : '当前通道不产生相似度分数' }}</div>
              </Card>
            </Col>
            <Col :span="8">
              <Card :bordered="false" class="kpi-card">
                <div class="kpi-label">平均检索耗时</div>
                <div class="kpi-value kpi-purple">
                  {{ evalResult?.avgLatencyMs == null ? '—' : evalResult.avgLatencyMs }}
                  <span v-if="evalResult?.avgLatencyMs != null" class="kpi-unit">ms</span>
                </div>
                <div class="kpi-foot">本次 TopK={{ evalResult?.topK ?? evalTopK }}；实际通道：{{ evalResult ? evalChannelSummary : '—' }}</div>
              </Card>
            </Col>
          </Row>

          <Alert
            v-if="evalResult && evalResult.scoreAvailable === false"
            type="info"
            show-icon
            class="callout-warning mb-4"
            message="本次批次没有可用的相似度分数"
            :description="evalScoreDescription"
          />

          <Space class="mb-3">
            <Button type="primary" :loading="runningEval" @click="handleRunEval">
              <template #icon><IconifyIcon icon="lucide:play" /></template>
              批量跑测（全部启用用例）
            </Button>
            <InputNumber
              v-model:value="evalTopK"
              :min="1"
              :max="50"
              addon-before="全局 TopK"
              style="width: 160px"
              @change="clearEvalResult"
            />
            <Button @click="resetCaseForm">
              <template #icon><IconifyIcon icon="lucide:plus" /></template>
              新增用例
            </Button>
            <span v-if="evalResult" class="cell-sub">
              命中 {{ evalResult.hitCount }} / {{ evalResult.evaluatedCount }} 条已标注用例；
              未标注 {{ evalResult.unlabeledCount }} 条，共 {{ evalResult.totalCount }} 条
            </span>
          </Space>

          <!-- 用例表单：内嵌可编辑 -->
          <Card
            v-if="caseEditing || caseForm.id"
            :bordered="false"
            class="surface-card mb-4"
          >
            <template #title>
              <div class="card-title">
                <IconifyIcon icon="lucide:list-checks" class="card-title-icon" />
                <span>{{ caseForm.id ? '编辑用例' : '新增用例' }}</span>
              </div>
            </template>
            <Space direction="vertical" style="width: 100%" :size="12">
              <Space wrap>
                <Input v-model:value="caseForm.name" placeholder="用例名称" style="width: 220px" />
                <Input v-model:value="caseForm.query" placeholder="标准问题（检索词）" style="width: 360px" />
                <Select v-model:value="caseForm.docType" :options="docTypeOptions" allow-clear placeholder="角色" style="width: 170px" />
                <Select v-model:value="caseForm.bidSectionCode" :options="sectionOptions" allow-clear show-search placeholder="标段（可空）" style="width: 230px" />
                <InputNumber v-model:value="caseForm.topK" :min="1" :max="50" addon-before="用例 TopK" title="批量跑测时统一使用工具栏全局 TopK" />
              </Space>
              <Space wrap>
                <Input v-model:value="caseForm.expectKeywords" placeholder="期望关键词（逗号分隔）" style="width: 320px" />
                <Input v-model:value="caseForm.expectBlockIds" placeholder="期望溯源块号（逗号分隔）" style="width: 320px" />
                <Button type="primary" @click="saveCase">
                  <template #icon><IconifyIcon icon="lucide:save" /></template>
                  保存用例
                </Button>
                <Button @click="resetCaseForm">取消</Button>
              </Space>
            </Space>
          </Card>

          <Table :data-source="evalResult?.cases || evalCases" :pagination="false" size="middle">
            <Table.Column title="用例" data-index="name" :min-width="180">
              <template #default="{ record }">
                <div class="cell-strong">{{ record.name }}</div>
              </template>
            </Table.Column>
            <Table.Column title="标准问题" data-index="query">
              <template #default="{ record }">
                <span class="cell-ellipsis" :title="record.query">{{ record.query }}</span>
              </template>
            </Table.Column>
            <Table.Column title="是否命中" :width="110" align="center">
              <template #default="{ record }">
                <Tag v-if="record.evaluated === false" class="chip-neutral">未标注</Tag>
                <Tag v-else-if="record.hit !== undefined" :class="record.hit ? 'chip-success' : 'chip-danger'">
                  {{ record.hit ? '命中' : '未命中' }}
                </Tag>
                <Tag v-else :class="record.enabled === 1 ? 'chip-blue' : 'chip-neutral'">
                  {{ record.enabled === 1 ? '启用' : '停用' }}
                </Tag>
              </template>
            </Table.Column>
            <Table.Column title="命中通道" :width="120" align="center">
              <template #default="{ record }">
                <Tag v-if="record.matchedBy" class="chip-neutral">{{ channelLabel(record.matchedBy) }}</Tag>
                <span v-else class="cell-sub">—</span>
              </template>
            </Table.Column>
            <Table.Column title="检索分数" :width="100" align="center">
              <template #default="{ record }">
                <code v-if="record.score != null" class="code-text">{{ record.score.toFixed(4) }}</code>
                <span v-else class="cell-sub">—</span>
              </template>
            </Table.Column>
            <Table.Column title="TopK" :width="80" align="center">
              <template #default="{ record }">
                <code v-if="record.topK != null" class="code-text">{{ record.topK }}</code>
                <span v-else class="cell-sub">—</span>
              </template>
            </Table.Column>
            <Table.Column title="耗时" :width="100" align="center">
              <template #default="{ record }">
                <span v-if="record.latencyMs != null">{{ record.latencyMs }} ms</span>
                <span v-else class="cell-sub">—</span>
              </template>
            </Table.Column>
            <Table.Column title="未命中原因 / 命中块号">
              <template #default="{ record }">
                <span v-if="record.missReason">{{ record.missReason }}</span>
                <span v-else-if="record.matchedBlockIds">{{ (record.matchedBlockIds || []).join(', ') }}</span>
                <span v-else class="cell-sub">—</span>
              </template>
            </Table.Column>
            <Table.Column title="启用" :width="90" align="center">
              <template #default="{ record }">
                <Switch
                  v-if="record.id && record.hit === undefined"
                  :checked="record.enabled === 1"
                  size="small"
                  @change="toggleCaseEnabled(record)"
                />
              </template>
            </Table.Column>
            <Table.Column title="操作" :width="140" align="right">
              <template #default="{ record }">
                <Space size="small">
                  <Button v-if="record.id" type="link" size="small" @click="editCase(record)">编辑</Button>
                  <Button v-if="record.id" type="link" size="small" danger @click="handleDeleteCase(record.id)">删除</Button>
                </Space>
              </template>
            </Table.Column>
          </Table>
        </TabPane>
      </Tabs>
    </Card>

    <!-- 切片编辑弹窗 -->
    <Modal v-model:open="segmentEditVisible" title="编辑切片" :width="720" ok-text="保存并同步向量" cancel-text="取消" @ok="saveSegmentEdit">
      <Textarea
        v-model:value="segmentEditForm.content"
        :rows="10"
        placeholder="保存后立即生效"
      />
    </Modal>

    <!-- 保存为测试用例弹窗 -->
    <Modal v-model:open="saveCaseVisible" title="保存为测试用例" :width="640" ok-text="保存" cancel-text="取消" @ok="saveRetrieveCase">
      <Space direction="vertical" style="width: 100%" :size="12">
        <Input v-model:value="retrieveCaseForm.name" placeholder="用例名称" />
        <Input v-model:value="retrieveCaseForm.query" placeholder="标准问题（检索词）" />
        <Space wrap>
          <Select v-model:value="retrieveCaseForm.docType" :options="docTypeOptions" allow-clear placeholder="角色" style="width: 180px" />
          <Select v-model:value="retrieveCaseForm.bidSectionCode" :options="sectionOptions" allow-clear show-search placeholder="标段（可空）" style="width: 240px" />
          <InputNumber v-model:value="retrieveCaseForm.topK" :min="1" :max="50" addon-before="topK" />
        </Space>
        <Input v-model:value="retrieveCaseForm.expectKeywords" placeholder="期望关键词（逗号分隔，可选）" />
        <Input v-model:value="retrieveCaseForm.expectBlockIds" placeholder="期望溯源块号（逗号分隔，可选）" />
      </Space>
    </Modal>
  </Page>
</template>

<style scoped>
/* ============ 页面标题 ============ */
.page-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  padding: 4px 4px 16px;
  border-bottom: 1px solid hsl(var(--border));
  margin-bottom: 16px;
}
.page-header-main { min-width: 0; }
.page-title {
  font-size: 24px;
  font-weight: 700;
  line-height: 1.25;
  margin: 0;
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
.mt-4 { margin-top: 16px; }
.mt-3 { margin-top: 12px; }
.mb-4 { margin-bottom: 16px; }
.mb-3 { margin-bottom: 12px; }

.card-title {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  min-width: 0;
}
.card-title-icon { color: hsl(var(--primary)); font-size: 16px; }
.step-index {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 28px;
  height: 24px;
  border: 1px solid hsl(var(--border));
  border-radius: 6px;
  background: hsl(var(--card));
  color: hsl(var(--muted-foreground));
  font-size: 11px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  letter-spacing: 0.06em;
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

/* ============ Tabs 容器 ============ */
.kb-tabs :deep(.ant-tabs-nav) {
  margin-bottom: 16px !important;
}
.kb-tabs :deep(.ant-tabs-tab) {
  padding: 8px 14px;
  font-size: 13.5px;
}
.kb-tabs :deep(.ant-tabs-tab + .ant-tabs-tab) {
  margin: 0 0 0 8px;
}
/* ============ 资料管理工作台 ============ */
.material-workspace {
  --primary-action-width: 178px;
  min-width: 0;
}
.material-primary {
  display: grid;
  grid-template-columns: repeat(12, minmax(0, 1fr));
  gap: 16px;
  align-items: stretch;
  margin-bottom: 16px;
}
.module-card {
  overflow: hidden;
  border: 1px solid hsl(var(--border));
  border-radius: 12px;
  background: hsl(var(--card));
  box-shadow: none;
}
.module-card :deep(.ant-card-head) {
  min-height: 52px;
  padding: 0 20px;
  border-bottom: 1px solid hsl(var(--border));
  background: hsl(var(--muted) / 0.22);
}
.module-card :deep(.ant-card-head-title) { font-size: 14px; font-weight: 600; }
.module-card :deep(.ant-card-body) { padding: 20px; }
.action-card {
  display: flex;
  flex-direction: column;
  grid-column: 1 / -1;
  min-width: 0;
  height: auto;
}
.action-card :deep(.ant-card-body) {
  display: flex;
  flex: 1;
  flex-direction: column;
}
.control-stack,
.control-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}
.form-control {
  width: 100%;
  min-width: 0;
}
.primary-action {
  display: block;
  grid-column: 1 / -1;
  width: var(--primary-action-width);
}
.primary-action--button {
  grid-column: 1 / -1;
  width: var(--primary-action-width);
  justify-self: start;
}
.action-card .kb-helper { margin-top: auto; }
.module-table { margin-top: 16px; }
.filter-grid {
  display: grid;
  grid-template-columns: minmax(160px, 1fr) minmax(140px, 0.85fr) minmax(120px, 0.7fr) minmax(160px, 1fr) auto;
  gap: 12px;
  align-items: center;
  margin-bottom: 16px;
}
.filter-action { min-width: 88px; }

/* ============ 区块标题 ============ */
.section-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  margin-bottom: 12px;
}
.section-title {
  font-size: 15px;
  font-weight: 600;
  margin: 0 0 2px;
  color: hsl(var(--foreground));
}
.section-sub {
  font-size: 12.5px;
  color: hsl(var(--muted-foreground));
  margin: 0;
}

/* ============ 单元格 ============ */
.cell-strong { font-weight: 600; color: hsl(var(--foreground)); }
.cell-sub {
  font-size: 11.5px;
  color: hsl(var(--muted-foreground));
}
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

/* ============ Chips ============ */
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

/* ============ 状态点 ============ */
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
.status-dot.is-pending {
  background: hsl(35 90% 50%);
  box-shadow: 0 0 0 3px hsl(35 90% 50% / 0.18);
}
.status-dot.is-danger {
  background: hsl(var(--destructive));
  box-shadow: 0 0 0 3px hsl(var(--destructive) / 0.18);
}

/* ============ 切片表格单元格 ============ */
.segment-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  background: hsl(var(--muted) / 0.4);
  border-radius: 10px;
  margin-bottom: 12px;
}
.segment-material {
  font-weight: 600;
  font-size: 14px;
  color: hsl(var(--foreground));
  margin-top: 2px;
}
.segment-cell {
  max-height: 96px;
  overflow: auto;
  white-space: pre-wrap;
  font-size: 13px;
  line-height: 1.55;
  color: hsl(var(--foreground));
  padding: 4px 8px;
  background: hsl(var(--muted) / 0.3);
  border-radius: 6px;
}
.retrieve-cell {
  max-height: 120px;
  overflow: auto;
  white-space: pre-wrap;
  font-size: 13px;
  line-height: 1.55;
  color: hsl(var(--foreground));
  padding: 8px 10px;
  background: hsl(var(--muted) / 0.3);
  border-radius: 6px;
  border-left: 3px solid hsl(var(--primary));
}

/* ============ 召回分数条 ============ */
.score-bar {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}
.score-bar-bg {
  display: inline-block;
  width: 80px;
  height: 6px;
  background: hsl(var(--muted) / 0.6);
  border-radius: 999px;
  overflow: hidden;
}
.score-bar-fill {
  display: block;
  height: 100%;
  background: linear-gradient(90deg, hsl(212 90% 60%), hsl(150 65% 45%));
  border-radius: 999px;
}
.score-bar-text {
  font-family: var(--vben-font-mono, 'SF Mono', Consolas, monospace);
  font-size: 12px;
  color: hsl(var(--muted-foreground));
}

/* ============ KPI 卡片 ============ */
.kpi-card {
  border-radius: 12px;
  background: hsl(var(--card));
}
.kpi-label {
  font-size: 12.5px;
  color: hsl(var(--muted-foreground));
  margin-bottom: 6px;
}
.kpi-value {
  font-size: 30px;
  font-weight: 700;
  letter-spacing: -0.5px;
  line-height: 1.1;
}
.kpi-unavailable {
  font-size: 18px;
  font-weight: 600;
  color: hsl(var(--muted-foreground));
}
.kpi-value.kpi-good { color: hsl(var(--success)); }
.kpi-value.kpi-warn { color: hsl(30 90% 45%); }
.kpi-value.kpi-blue { color: hsl(212 80% 48%); }
.kpi-value.kpi-purple { color: hsl(265 60% 50%); }
.kpi-unit {
  font-size: 14px;
  font-weight: 500;
  color: hsl(var(--muted-foreground));
  margin-left: 2px;
}
.kpi-foot {
  font-size: 11.5px;
  color: hsl(var(--muted-foreground));
  margin-top: 8px;
}

/* ============ Callout 信息条 ============ */
.callout-warning {
  border-radius: 10px;
}

/* ============ Helper 说明条 ============ */
.kb-helper {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: hsl(var(--muted) / 0.4);
  border-radius: 8px;
  font-size: 12.5px;
  color: hsl(var(--muted-foreground));
}
.kb-helper-icon {
  color: hsl(var(--primary));
  font-size: 14px;
  flex: none;
}
/* ============ 响应式 ============ */

@media (max-width: 900px) {
  .filter-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .filter-action { grid-column: 1 / -1; width: 100%; }
}
@media (max-width: 640px) {
  .page-header { padding-inline: 0; }
  .control-stack,
  .control-grid,
  .filter-grid { grid-template-columns: minmax(0, 1fr); }
  .primary-action,
  .primary-action--button { width: 100%; }
  .module-card :deep(.ant-card-head) { padding-inline: 14px; }
  .module-card :deep(.ant-card-body) { padding-inline: 14px; }
}
</style>
