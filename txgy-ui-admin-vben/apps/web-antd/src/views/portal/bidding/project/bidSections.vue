<script setup lang="ts">
/* 前台 · 项目公告管理 — 第三级：某招标项目下的标段（包）列表
 * 入口：第二级点击招标项目行；本页提供「创建标段」（自动带入 TENDER_PROJECT_CODE 归属，可挂多个附件）；
 * 点击标段行进入第四级（该标段下的公告列表，含创建公告入口）。
 * 数据：GET /bidding/bid-section/page?tenderProjectCode=…；写：POST /bidding/bid-section/save。 */
import { computed, onMounted, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { Button, Drawer, Input, message, Table, Tag } from 'ant-design-vue';

import { getBidSectionPage, getTenderProjectByCode, saveBidSection, type ProjectApi } from '#/api/bidding/project';

import AttachmentList from '../../../bidding/vo/AttachmentList.vue';
import EntityFormModal from '../../../bidding/vo/EntityFormModal.vue';
import { loadAttachments } from '../../../bidding/vo/attachmentStore';
import { BID_SECTION_DEF } from '../../../bidding/vo/entityForms';

import './panel.css';

const route = useRoute();
const router = useRouter();
const projectCode = computed(() => String(route.params.projectCode ?? ''));
const tenderProjectCode = computed(() => String(route.params.tenderProjectCode ?? ''));

const loading = ref(false);
const rows = ref<ProjectApi.BidSection[]>([]);
const total = ref(0);
const pageNo = ref(1);
const pageSize = ref(10);
const query = reactive<{ bidSectionCode: string; bidSectionName: string }>({ bidSectionCode: '', bidSectionName: '' });
const tenderProjectName = ref('');

const editorOpen = ref(false);
const editing = ref<ProjectApi.BidSection | null>(null);
const preset = computed<Record<string, string>>(() => ({ TENDER_PROJECT_CODE: tenderProjectCode.value }));

const attDrawerOpen = ref(false);
const attOwner = ref<ProjectApi.BidSection | null>(null);
const attList = computed(() => (attOwner.value ? loadAttachments('bidSection', attOwner.value.bidSectionCode) : []));

async function load(): Promise<void> {
  loading.value = true;
  try {
    const res = await getBidSectionPage({
      pageNo: pageNo.value,
      pageSize: pageSize.value,
      tenderProjectCode: tenderProjectCode.value || undefined,
      bidSectionCode: query.bidSectionCode || undefined,
      bidSectionName: query.bidSectionName || undefined,
    });
    rows.value = res?.list ?? [];
    total.value = res?.total ?? 0;
  } catch (e) {
    message.error('加载标段失败：' + ((e as Error).message || String(e)));
  } finally {
    loading.value = false;
  }
}
async function loadParentName(): Promise<void> {
  if (!tenderProjectCode.value) return;
  try {
    const t = await getTenderProjectByCode(tenderProjectCode.value);
    tenderProjectName.value = t?.tenderProjectName ?? '';
  } catch {
    tenderProjectName.value = '';
  }
}
onMounted(async () => {
  await loadParentName();
  await load();
});

function create(): void {
  editing.value = null;
  editorOpen.value = true;
}
function edit(r: ProjectApi.BidSection): void {
  editing.value = r;
  editorOpen.value = true;
}
function openAttachments(r: ProjectApi.BidSection): void {
  attOwner.value = r;
  attDrawerOpen.value = true;
}
/** 标段删除：BidSectionController 未提供 /delete，前端不做任何删除动作，避免误删上级数据。
 *  已向后端反馈需补 delete 接口（含"存在公告时拒绝"的追溯链保护）。 */
function remove(r: ProjectApi.BidSection): void {
  message.warning('标段删除接口后端尚未提供（BidSectionController 缺 /delete）：' + r.bidSectionCode + ' 需由后端补齐后再删除');
}
/** 第四级：该标段下的公告列表（创建公告入口在该页） */
function drill(r: ProjectApi.BidSection): void {
  void router.push('/portal/bidding/sections/' + encodeURIComponent(r.bidSectionCode) + '/notices');
}
function attCount(r: ProjectApi.BidSection): number {
  return loadAttachments('bidSection', r.bidSectionCode).length;
}
async function doSave(data: Record<string, unknown>): Promise<unknown> {
  return saveBidSection(data as unknown as ProjectApi.BidSection);
}
</script>

<template>
  <div class="pn">
    <div class="pn-crumb">
      <a @click="router.push('/portal/bidding/projects')">项目</a>
      <span>/</span>
      <a @click="router.push('/portal/bidding/projects/' + encodeURIComponent(projectCode) + '/tender-projects')">{{ projectCode }}</a>
      <span>/</span>
      <span class="pn-cur">{{ tenderProjectCode }}{{ tenderProjectName ? ' · ' + tenderProjectName : '' }}</span>
      <span>/</span>
      <span>标段（包）</span>
    </div>

    <div class="pn-head">
      <div>
        <h1 class="pn-title">标段（包）</h1>
        <p class="pn-sub">
          当前招标项目：<b>{{ tenderProjectCode }}</b>{{ tenderProjectName ? '（' + tenderProjectName + '）' : '' }}。
          一个招标项目可拆多个标段；字段照录 VO 规范 BidSectionVO（28 项），新建时自动带入归属招标项目编号，可挂多个附件。
          <b>点击标段行进入该标段的公告列表，并在那里创建公告</b>。
        </p>
      </div>
      <div class="pn-acts">
        <Button type="primary" @click="create">创建标段</Button>
        <Button @click="load">刷新</Button>
      </div>
    </div>

    <div class="pn-card">
      <div class="pn-card-h">
        <span class="pn-card-t">检索</span>
        <Input v-model:value="query.bidSectionCode" placeholder="标段（包）编号" style="width: 200px" allow-clear @press-enter="load" />
        <Input v-model:value="query.bidSectionName" placeholder="标段名称（模糊）" style="width: 220px" allow-clear @press-enter="load" />
        <Button type="primary" @click="load">查询</Button>
        <span class="pn-dim">共 {{ total }} 个标段</span>
      </div>
      <div class="pn-card-b">
        <Table
          class="pn-table"
          row-key="id"
          size="small"
          :loading="loading"
          :data-source="rows"
          :pagination="{
            current: pageNo,
            pageSize,
            total,
            showSizeChanger: true,
            showTotal: (t: number) => '共 ' + t + ' 条',
            onChange: (p: number, ps: number) => { pageNo = p; pageSize = ps; load(); },
          }"
        >
          <Table.Column title="标段（包）编号" data-index="bidSectionCode" :width="200">
            <template #default="{ record }">
              <a class="pn-link pn-code" @click="drill(record)">{{ record.bidSectionCode }}</a>
            </template>
          </Table.Column>
          <Table.Column title="标段名称" data-index="bidSectionName" :ellipsis="true" />
          <Table.Column title="标段内容" data-index="bidSectionContent" :ellipsis="true" />
          <Table.Column title="合同估算价" data-index="contractReckonPrice" :width="130" />
          <Table.Column title="工期" data-index="timeLimit" :width="90" />
          <Table.Column title="状态" :width="90">
            <template #default="{ record }">
              <Tag :color="record.state === 'CLOSED' ? 'default' : 'green'">{{ record.state === 'CLOSED' ? '已结束' : '进行中' }}</Tag>
            </template>
          </Table.Column>
          <Table.Column title="附件" :width="80">
            <template #default="{ record }">
              <a class="pn-link" @click="openAttachments(record)">{{ attCount(record) }} 个</a>
            </template>
          </Table.Column>
          <Table.Column title="操作" :width="250" fixed="right">
            <template #default="{ record }">
              <Button size="small" type="link" @click="drill(record)">公告</Button>
              <Button size="small" type="link" @click="edit(record)">编辑</Button>
              <Button size="small" type="link" danger @click="remove(record)">删除</Button>
            </template>
          </Table.Column>
        </Table>
        <div v-if="!loading && !rows.length" class="pn-empty">该招标项目下暂无标段，点右上「创建标段」开始；标段是发公告的最小单元。</div>
      </div>
    </div>

    <EntityFormModal v-model:open="editorOpen" :def="BID_SECTION_DEF" :record="editing" :preset="preset" :save="doSave" @saved="load" />

    <Drawer v-model:open="attDrawerOpen" :title="'标段附件 · ' + (attOwner?.bidSectionCode ?? '')" :width="760">
      <div class="pn-tip">标段附件：招标文件、资格预审文件、控制价文件等可按采集项挂多个；GUID 按规范写入 ATTACHMENT_CODE。</div>
      <AttachmentList :list="attList" title="本标段附件" />
    </Drawer>
  </div>
</template>
