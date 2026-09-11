<script setup lang="ts">
/* 前台 · 项目公告管理 — 第二级：某项目下的招标项目列表
 * 入口：第一级点击项目行；本页提供「创建招标项目」（自动带入 PROJECT_CODE 归属，可挂多个附件）；
 * 点击招标项目行进入第三级（该招标项目下的标段）。
 * 数据：GET /bidding/tender-project/page?projectCode=…；写：POST /bidding/tender-project/save。 */
import { computed, onMounted, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { Button, Drawer, Input, message, Popconfirm, Table, Tag } from 'ant-design-vue';

import { getProjectByCode, getTenderProjectPage, saveTenderProject, type ProjectApi, deleteTenderProject } from '#/api/bidding/project';

import AttachmentList from '../../../bidding/vo/AttachmentList.vue';
import EntityFormModal from '../../../bidding/vo/EntityFormModal.vue';
import { loadAttachments } from '../../../bidding/vo/attachmentStore';
import { TENDER_PROJECT_DEF } from '../../../bidding/vo/entityForms';

import './panel.css';

const route = useRoute();
const router = useRouter();
const projectCode = computed(() => String(route.params.projectCode ?? ''));

const loading = ref(false);
const rows = ref<ProjectApi.TenderProject[]>([]);
const total = ref(0);
const pageNo = ref(1);
const pageSize = ref(10);
const query = reactive<{ tenderProjectCode: string; tenderProjectName: string }>({ tenderProjectCode: '', tenderProjectName: '' });
const projectName = ref('');

const editorOpen = ref(false);
const editing = ref<ProjectApi.TenderProject | null>(null);
const preset = computed<Record<string, string>>(() => ({ PROJECT_CODE: projectCode.value }));

const attDrawerOpen = ref(false);
const attOwner = ref<ProjectApi.TenderProject | null>(null);
const attList = computed(() => (attOwner.value ? loadAttachments('tenderProject', attOwner.value.tenderProjectCode) : []));

async function load(): Promise<void> {
  loading.value = true;
  try {
    const res = await getTenderProjectPage({
      pageNo: pageNo.value,
      pageSize: pageSize.value,
      projectCode: projectCode.value || undefined,
      tenderProjectCode: query.tenderProjectCode || undefined,
      tenderProjectName: query.tenderProjectName || undefined,
    });
    rows.value = res?.list ?? [];
    total.value = res?.total ?? 0;
  } catch (e) {
    message.error('加载招标项目失败：' + ((e as Error).message || String(e)));
  } finally {
    loading.value = false;
  }
}
async function loadProjectName(): Promise<void> {
  if (!projectCode.value) return;
  try {
    const p = await getProjectByCode(projectCode.value);
    projectName.value = p?.projectName ?? '';
  } catch {
    projectName.value = '';
  }
}
onMounted(async () => {
  await loadProjectName();
  await load();
});

function create(): void {
  editing.value = null;
  editorOpen.value = true;
}
function edit(r: ProjectApi.TenderProject): void {
  editing.value = r;
  editorOpen.value = true;
}
function openAttachments(r: ProjectApi.TenderProject): void {
  attOwner.value = r;
  attDrawerOpen.value = true;
}
async function remove(r: ProjectApi.TenderProject): Promise<void> {
  try {
    await deleteTenderProject(r.id!);
    message.success('已删除招标项目：' + r.tenderProjectCode);
    void load();
  } catch (e) {
    message.error('删除失败：' + ((e as Error).message || String(e)));
  }
}
/** 第三级：该招标项目下的标段列表 */
function drill(r: ProjectApi.TenderProject): void {
  void router.push(
    '/portal/bidding/projects/' + encodeURIComponent(projectCode.value) +
      '/tender-projects/' + encodeURIComponent(r.tenderProjectCode) + '/sections',
  );
}
function attCount(r: ProjectApi.TenderProject): number {
  return loadAttachments('tenderProject', r.tenderProjectCode).length;
}
async function doSave(data: Record<string, unknown>): Promise<unknown> {
  return saveTenderProject(data as unknown as ProjectApi.TenderProject);
}
</script>

<template>
  <div class="pn">
    <div class="pn-crumb">
      <a @click="router.push('/portal/bidding/projects')">项目</a>
      <span>/</span>
      <span class="pn-cur">{{ projectCode }}{{ projectName ? ' · ' + projectName : '' }}</span>
      <span>/</span>
      <span>招标项目</span>
    </div>

    <div class="pn-head">
      <div>
        <h1 class="pn-title">招标项目</h1>
        <p class="pn-sub">
          当前项目：<b>{{ projectCode }}</b>{{ projectName ? '（' + projectName + '）' : '' }}。
          一个项目可建多个招标项目；字段照录 VO 规范 TenderProjectVO（28 项），新建时自动带入归属项目编号，可挂多个附件。
          点击招标项目行进入其下的标段（包）。
        </p>
      </div>
      <div class="pn-acts">
        <Button type="primary" @click="create">创建招标项目</Button>
        <Button @click="load">刷新</Button>
      </div>
    </div>

    <div class="pn-card">
      <div class="pn-card-h">
        <span class="pn-card-t">检索</span>
        <Input v-model:value="query.tenderProjectCode" placeholder="招标项目编号" style="width: 200px" allow-clear @press-enter="load" />
        <Input v-model:value="query.tenderProjectName" placeholder="招标项目名称（模糊）" style="width: 220px" allow-clear @press-enter="load" />
        <Button type="primary" @click="load">查询</Button>
        <span class="pn-dim">共 {{ total }} 个招标项目</span>
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
          <Table.Column title="招标项目编号" data-index="tenderProjectCode" :width="200">
            <template #default="{ record }">
              <a class="pn-link pn-code" @click="drill(record)">{{ record.tenderProjectCode }}</a>
            </template>
          </Table.Column>
          <Table.Column title="招标项目名称" data-index="tenderProjectName" :ellipsis="true" />
          <Table.Column title="招标人" data-index="tendererName" :width="160" :ellipsis="true" />
          <Table.Column title="招标方式" data-index="tenderMode" :width="110" />
          <Table.Column title="资格审查" data-index="qulificationCheckMode" :width="110" />
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
          <Table.Column title="操作" :width="230" fixed="right">
            <template #default="{ record }">
              <Button size="small" type="link" @click="drill(record)">标段</Button>
              <Button size="small" type="link" @click="edit(record)">编辑</Button>
              <Popconfirm title="删除招标项目？存在标段时后端会拒绝。" @confirm="remove(record)">
                <Button size="small" type="link" danger>删除</Button>
              </Popconfirm>
            </template>
          </Table.Column>
        </Table>
        <div v-if="!loading && !rows.length" class="pn-empty">该项目下暂无招标项目，点右上「创建招标项目」开始。</div>
      </div>
    </div>

    <EntityFormModal v-model:open="editorOpen" :def="TENDER_PROJECT_DEF" :record="editing" :preset="preset" :save="doSave" @saved="load" />

    <Drawer v-model:open="attDrawerOpen" :title="'招标项目附件 · ' + (attOwner?.tenderProjectCode ?? '')" :width="760">
      <div class="pn-tip">招标项目附件：上传/预览可用，GUID 按规范写入 ATTACHMENT_CODE；后端附件集接口就绪后改为按实体读取。</div>
      <AttachmentList :list="attList" title="本招标项目附件" />
    </Drawer>
  </div>
</template>
