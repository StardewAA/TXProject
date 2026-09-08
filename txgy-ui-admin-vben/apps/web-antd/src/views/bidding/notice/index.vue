<script lang="ts" setup>
import { NoticeApi } from '#/api/bidding/notice';

import { computed, onMounted, reactive, ref } from 'vue';

import { Button, Drawer, Input, message, Modal, Radio, Select, Space, Switch, Table, Tag, Upload } from 'ant-design-vue';
import { uploadFile } from '#/api/infra/file';
import NoticeHtmlView from './NoticeHtmlView.vue';

import { createNotice, deleteNotice, fetchNoticeDetail, fetchNoticePage, publishNotice, unpublishNotice, updateNotice } from '#/api/bidding/notice';
const createMessage = message;

/* PDF 版式上传（infra 文件服务：保存只带 file_id；对外 URL 由业务系统按可见性签发） */
const pdfUploading = ref(false);
const pdfFileName = ref('');
const pdfSize = ref<number>();
const payloadText = ref('');
const fmtBytes = (n?: number): string => {
  if (n === undefined || n === null) return '';
  if (n < 1024) return n + ' B';
  if (n < 1024 * 1024) return (n / 1024).toFixed(1) + ' KB';
  return (n / 1024 / 1024).toFixed(2) + ' MB';
};
async function onPdfBeforeUpload(file: File): Promise<boolean> {
  if (!/\.pdf$/i.test(file.name)) { createMessage.warning('仅支持 PDF 文件'); return false; }
  pdfUploading.value = true;
  try {
    const res = await uploadFile({ file, directory: 'bidding/notice' });
    const id = (res as unknown as { id?: number }).id;
    if (id === undefined) throw new Error('上传未返回 file_id');
    form.pdfFileId = id;
    pdfFileName.value = file.name;
    pdfSize.value = file.size;
    createMessage.success('版式文件已上传：file_id=' + id);
  } catch (e) {
    createMessage.error('上传失败：' + ((e as Error).message || String(e)));
  } finally {
    pdfUploading.value = false;
  }
  return false;
}

const loading = ref(false);
const rows = ref<NoticeApi.Row[]>([]);
const total = ref(0);
const pageNo = ref(1);
const pageSize = ref(10);
const filters = reactive<{ status?: string; noticeType?: string; internalFlag?: boolean }>({});

const p2 = (n: number) => String(n).padStart(2, '0');
const fmtT = (v?: number | string): string => {
  if (v === undefined || v === null || v === '') return '—';
  const d = new Date(Number(v));
  if (Number.isNaN(+d)) return String(v);
  return d.getFullYear() + '-' + p2(d.getMonth() + 1) + '-' + p2(d.getDate()) + ' ' + p2(d.getHours()) + ':' + p2(d.getMinutes());
};
const statusTag = (s?: string) =>
  s === 'PUBLISHED' ? { color: 'green', text: NoticeApi.NOTICE_STATUS_LABEL[s] || s }
    : s === 'DRAFT' ? { color: 'default', text: NoticeApi.NOTICE_STATUS_LABEL[s] || s }
      : s === 'UNPUBLISHED' ? { color: 'orange', text: NoticeApi.NOTICE_STATUS_LABEL[s] || s }
        : { color: 'gray', text: NoticeApi.NOTICE_STATUS_LABEL[s!] || s || '—' };
const typeName = (row: NoticeApi.Row) => {
  if (row.noticeTypeName) return row.noticeTypeName;
  const hit = NoticeApi.NOTICE_TYPES.find((t) => t.code === row.noticeType);
  return hit ? hit.label : row.noticeType || '—';
};

async function load() {
  loading.value = true;
  try {
    const res = await fetchNoticePage({ pageNo: pageNo.value, pageSize: pageSize.value, ...filters });
    rows.value = res.list || [];
    total.value = Number(res.total || 0);
  } catch (e) {
    createMessage.error('加载失败：' + ((e as Error).message || String(e)));
  } finally {
    loading.value = false;
  }
}
function onSearch() { pageNo.value = 1; load(); }
const pages = computed(() => Math.max(1, Math.ceil(total.value / pageSize.value)));
function goPrev() { if (pageNo.value > 1) { pageNo.value -= 1; load(); } }
function goNext() { if (pageNo.value < pages.value) { pageNo.value += 1; load(); } }

/* ============ 编辑器 ============ */
const editorOpen = ref(false);
const editorSaving = ref(false);
const editId = ref<number | string>();
const form = reactive<NoticeApi.SaveParams>({
  noticeType: 'TENDER_NOTICE',
  title: '',
  publicLevel: 'SOCIAL',
  internalFlag: false,
});
const openCreate = () => {
  editId.value = undefined;
  form.id = undefined;
  form.noticeCode = '';
  form.noticeType = 'TENDER_NOTICE';
  form.title = '';
  form.projectCode = '';
  form.projectName = '';
  form.publisherName = '';
  form.publicLevel = 'SOCIAL';
  form.internalFlag = false;
  form.content = '';
  form.pdfFileId = undefined;
  payloadText.value = '';
  pdfFileName.value = '';
  pdfSize.value = undefined;
  editorOpen.value = true;
};
const openEdit = async (row: NoticeApi.Row) => {
  const d = await fetchNoticeDetail(row.id!);
  editId.value = row.id;
  form.id = d.id;
  form.noticeCode = d.noticeCode;
  form.noticeType = d.noticeType || 'TENDER_NOTICE';
  form.title = d.title || '';
  form.projectCode = d.projectCode;
  form.projectName = d.projectName;
  form.publisherName = d.publisherName;
  form.publicLevel = d.publicLevel || 'SOCIAL';
  form.internalFlag = !!d.internalFlag;
  form.content = d.content || '';
  form.pdfFileId = d.pdfFileId;
  pdfSize.value = typeof d.pdfSize === 'number' ? d.pdfSize : undefined;
  pdfFileName.value = '';
  try { payloadText.value = d.payload ? JSON.stringify(d.payload, null, 1) : ''; } catch { payloadText.value = ''; }
  editorOpen.value = true;
};
const editorTitle = computed(() => (editId.value === undefined ? '新建公告（草稿入库）' : '编辑公告'));
function saveEditor() {
  if (!form.title.trim()) { createMessage.warning('请填写公告名称'); return; }
  if (!form.noticeType) { createMessage.warning('请选择公告类型'); return; }
  if (form.internalFlag && form.publicLevel === 'GOVERNMENT') { createMessage.warning('企业内部公告请勿选择政府公开级别'); return; }
  if (payloadText.value.trim()) {
    try {
      form.payload = JSON.parse(payloadText.value) as Record<string, unknown>;
    } catch {
      createMessage.warning('权威数据集 payload 不是合法 JSON，请检查后重试');
      return;
    }
  } else {
    form.payload = undefined;
  }
  editorSaving.value = true;
  const submit = async () => {
    try {
      if (editId.value === undefined) {
        await createNotice({ ...form, payload: form.payload });
        createMessage.success('草稿已创建');
      } else {
        await updateNotice({ ...form, payload: form.payload });
        createMessage.success('已保存（正文变更将追加新版本）');
      }
      editorOpen.value = false;
      load();
    } catch (e) {
      const err = e as Error;
      createMessage.error((err.message || String(err)) + (err.message && /106-03[14]/.test(err.message) ? '（招标人发布门：需平台授权并完善主体档案）' : ''));
    } finally {
      editorSaving.value = false;
    }
  };
  void submit();
}

/* ============ 状态动作 ============ */
function doPublish(row: NoticeApi.Row) {
  Modal.confirm({
    title: '发布公告',
    content: '「' + row.title + '」发布后将在招投标大厅对外公示（内部公告仅本租户可见）；对外法律文书如需修改需撤下后重新发布（历史版本保留）。发布需已关联正文与 PDF 版式文件。是否发布？',
    okText: '发布',
    cancelText: '取消',
    onOk: async () => {
      try {
        await publishNotice(row.id!);
        createMessage.success('已发布');
        load();
      } catch (e) {
        const err = e as Error;
        createMessage.error((err.message || String(err)) + (/106-03[14]/.test(err.message || '') ? '（发布门未过：先授权+完善档案，或补正文/PDF）' : ''));
      }
    },
  });
}
function doUnpublish(row: NoticeApi.Row) {
  Modal.confirm({
    title: '撤下公告',
    content: '「' + row.title + '」撤下后大厅将不再展示（历史版本保留）；如需再次对外请重新发布。确定撤下？',
    okText: '撤下',
    cancelText: '取消',
    onOk: async () => {
      try { await unpublishNotice(row.id!); createMessage.success('已撤下'); load(); } catch (e) { createMessage.error((e as Error).message || String(e)); }
    },
  });
}
function doDelete(row: NoticeApi.Row) {
  Modal.confirm({
    title: '删除公告',
    content: '「' + row.title + '」将被逻辑删除（仅草稿/已撤下可删）。确定删除？',
    okText: '删除',
    okButtonProps: { danger: true },
    cancelText: '取消',
    onOk: async () => {
      try { await deleteNotice(row.id!); createMessage.success('已删除'); load(); } catch (e) { createMessage.error((e as Error).message || String(e)); }
    },
  });
}

/* ============ 详情 ============ */
const detailOpen = ref(false);
const detail = ref<NoticeApi.Detail>();
async function openDetail(row: NoticeApi.Row) {
  detailOpen.value = true;
  detail.value = undefined;
  try { detail.value = await fetchNoticeDetail(row.id!); } catch (e) { createMessage.error((e as Error).message || String(e)); }
}

onMounted(load);
</script>

<template>
  <div class="bid-ind ind-page">
        <div class="ind-stripes"></div>
    <div class="ind-head">
      <div class="min-w-0">
        <div class="ind-title">公告 / 公示管理 · 发布工作台 <span class="ind-mark">NOTICE</span></div>
        <div class="ind-sub">本企业自营公告（T 轨）。招标人发布门：创建/发布需平台授权并完善主体档案；发布后关注订阅者将收到动态。对外法律文书修订需撤下后重新发布（历史版本保留）。</div>
      </div>
      <div class="ind-actions">
        <Button type="primary" size="large" @click="openCreate">＋ 新建公告（草稿）</Button>
        <Button size="large" @click="load">刷新</Button>
      </div>
    </div>
    <div class="ind-body">

    <div class="ind-filterbar">
      <Select v-model:value="filters.status" placeholder="状态" allow-clear style="width: 140px">
        <Select.Option v-for="(t, c) in NoticeApi.NOTICE_STATUS_LABEL" :key="c" :value="c">{{ t }}</Select.Option>
      </Select>
      <Select v-model:value="filters.noticeType" placeholder="公告类型" allow-clear style="width: 160px">
        <Select.Option v-for="t in NoticeApi.NOTICE_TYPES" :key="t.code" :value="t.code">{{ t.label }}</Select.Option>
      </Select>
      <Radio.Group v-model:value="filters.internalFlag">
        <Radio.Button :value="undefined">全部</Radio.Button>
        <Radio.Button :value="true">仅内部公告</Radio.Button>
        <Radio.Button :value="false">仅社会公开</Radio.Button>
      </Radio.Group>
      <Button type="primary" ghost @click="onSearch">查询</Button>
    </div>

    <div class="rounded-md border border-gray-200 bg-white">
      <Table
        :data-source="rows"
        :loading="loading"
        :pagination="false"
        row-key="id"
        size="middle"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'title'">
            <span class="font-medium text-gray-800">{{ record.title }}</span>
            <Tag v-if="record.internalFlag" class="ml-1">内部</Tag>
            <Tag v-else-if="record.publicLevel" class="ml-1" color="geekblue">{{ record.publicLevel === 'GOVERNMENT' ? '政府公开' : '社会公开' }}</Tag>
          </template>
          <template v-else-if="column.key === 'type'">{{ typeName(record as NoticeApi.Row) }}</template>
          <template v-else-if="column.key === 'status'">
            <Tag :color="statusTag(record.status).color">{{ statusTag(record.status).text }}</Tag>
          </template>
          <template v-else-if="column.key === 'action'">
            <Space>
              <a class="text-xs text-blue-600" @click="openDetail(record as NoticeApi.Row)">详情</a>
              <template v-if="record.status === 'DRAFT' || record.status === 'UNPUBLISHED'">
                <a class="text-xs text-blue-600" @click="openEdit(record as NoticeApi.Row)">编辑</a>
                <a class="text-xs text-green-600" @click="doPublish(record as NoticeApi.Row)">{{ record.status === 'DRAFT' ? '发布' : '重新发布' }}</a>
                <a class="text-xs text-gray-400" @click="doDelete(record as NoticeApi.Row)">删除</a>
              </template>
              <template v-else-if="record.status === 'PUBLISHED'">
                <a class="text-xs text-orange-500" @click="doUnpublish(record as NoticeApi.Row)">撤下</a>
              </template>
              <span v-else class="text-xs text-gray-300">只读</span>
            </Space>
          </template>
          <template v-else-if="column.key === 'publishTime'">{{ fmtT(record.publishTime) }}</template>
        </template>
        <template #emptyText><div class="py-8 text-xs text-gray-300">暂无公告 — 点右上「新建公告」（发布前需主体档案完善 + 平台授权）</div></template>
      </Table>
      <div class="flex items-center justify-end gap-2 px-3 py-2 text-xs text-gray-400">
        <span>共 {{ total }} 条</span>
        <Button size="small" :disabled="pageNo <= 1" @click="goPrev">上一页</Button>
        <span>{{ pageNo }} / {{ pages }}</span>
        <Button size="small" :disabled="pageNo >= pages" @click="goNext">下一页</Button>
      </div>
    </div>

    </div>

    <!-- 新建/编辑弹窗 -->
    <Modal v-model:open="editorOpen" :title="editorTitle" :footer="null" width="760" destroy-on-close>
      <div class="grid grid-cols-2 gap-3">
        <div class="col-span-2">
          <div class="mb-1 text-xs font-medium text-gray-600">公告名称（标题）*</div>
          <Input v-model:value="form.title" placeholder="如 榆林炼油厂 2026 大修消缺项目施工招标公告" />
        </div>
        <div>
          <div class="mb-1 text-xs font-medium text-gray-600">公告类型 *</div>
          <Select v-model:value="form.noticeType" style="width: 100%">
            <Select.Option v-for="t in NoticeApi.NOTICE_TYPES" :key="t.code" :value="t.code">{{ t.label }}</Select.Option>
          </Select>
        </div>
        <div>
          <div class="mb-1 text-xs font-medium text-gray-600">发布单位</div>
          <Input v-model:value="form.publisherName" placeholder="本企业名称快照（可留空由后端取）" />
        </div>
        <div>
          <div class="mb-1 text-xs font-medium text-gray-600">项目编号</div>
          <Input v-model:value="form.projectCode" placeholder="关联招标项目编号（可选）" />
        </div>
        <div>
          <div class="mb-1 text-xs font-medium text-gray-600">项目名称</div>
          <Input v-model:value="form.projectName" placeholder="招标项目名称（可选）" />
        </div>
        <div>
          <div class="mb-1 text-xs font-medium text-gray-600">公开范围</div>
          <Select v-model:value="form.publicLevel" style="width: 100%">
            <Select.Option value="SOCIAL">社会公开（大厅可见）</Select.Option>
            <Select.Option value="GOVERNMENT">政府公开</Select.Option>
          </Select>
        </div>
        <div class="flex items-end pb-1">
          <div class="flex items-center gap-2 text-xs text-gray-600"><Switch v-model:checked="form.internalFlag" size="small" /> 企业内部公告（仅本租户可见，不进入大厅）</div>
        </div>
        <div class="col-span-2">
          <div class="mb-1 text-xs font-medium text-gray-600">正文（发布前必填；实质变更自动追加版本）</div>
          <Input.TextArea v-model:value="form.content" :rows="8" placeholder="公告正文全文…" />
        </div>
        <div class="col-span-2">
          <div class="mb-1 flex items-center gap-3 text-xs font-medium text-gray-600">
            <span>公告版式文件（PDF，发布前必填）— 文件服务上传，保存只带 file_id；URL 由业务系统按可见性签发</span>
            <Upload :show-upload-list="false" accept=".pdf" :before-upload="onPdfBeforeUpload">
              <Button size="small" :loading="pdfUploading">{{ form.pdfFileId !== undefined ? '重新上传 PDF' : '上传 PDF' }}</Button>
            </Upload>
          </div>
          <div v-if="form.pdfFileId !== undefined || pdfFileName" class="text-xs text-gray-500">
            file_id #{{ form.pdfFileId ?? '—' }}<template v-if="pdfFileName"> · {{ pdfFileName }}</template><template v-if="pdfSize"> · {{ fmtBytes(pdfSize) }}</template>
            <span v-if="form.pdfFileId !== undefined" class="ml-2 text-orange-500">（{{ form.internalFlag || form.publicLevel === 'GOVERNMENT' ? '限定可见' : '社会公开公告可免费查阅' }}）</span>
          </div>
        </div>
        <div class="col-span-2">
          <div class="mb-1 flex items-center justify-between text-xs font-medium text-gray-600">
            <span>权威数据集字段 payload（key=规范英文名，JSON 对象）</span>
            <span class="font-normal text-orange-500">发布时按数据集规整：缺失补 null、未知键剔除；字典接口落地后改动态表单</span>
          </div>
          <Input.TextArea v-model:value="payloadText" :rows="5" placeholder='{"TENDER_PROJECT_CODE":"…","NOTICE_NAME":"…"}' />
        </div>
        <div class="col-span-2 text-right">
          <Space>
            <Button @click="editorOpen = false">取消</Button>
            <Button type="primary" :loading="editorSaving" @click="saveEditor">{{ editId === undefined ? '保存为草稿' : '保存修改' }}</Button>
          </Space>
        </div>
      </div>
    </Modal>

    <!-- 详情抽屉 -->
    <Drawer v-model:open="detailOpen" :title="detail ? detail.title : '公告详情'" :width="680">
      <template v-if="detail">
        <div class="mb-3 flex flex-wrap items-center gap-2 text-xs">
          <Tag :color="statusTag(detail.status).color">{{ statusTag(detail.status).text }}</Tag>
          <Tag color="blue">{{ typeName(detail) }}</Tag>
          <span class="text-gray-400">{{ detail.noticeCode }}</span>
          <span class="text-gray-400">版本 v{{ detail.contentVersion ?? '—' }}</span>
        </div>
        <div class="mb-2 grid grid-cols-2 gap-x-4 gap-y-1 text-xs text-gray-500">
          <div>项目：{{ detail.projectCode || '—' }} {{ detail.projectName || '' }}</div>
          <div>发布单位：{{ detail.publisherName || '—' }}</div>
          <div>发布时间：{{ fmtT(detail.publishTime) }}</div>
          <div>公开级别：{{ detail.publicLevel || '—' }}<span v-if="detail.internalFlag">（内部）</span></div>
        </div>
        <div class="rounded-md border border-gray-100 p-3">
          <NoticeHtmlView :detail="detail" :show-head="false" />
        </div>
      </template>
    </Drawer>
  </div>
</template>


