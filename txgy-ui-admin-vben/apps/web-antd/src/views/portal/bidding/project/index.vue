<script setup lang="ts">
/* 前台 · 项目公告管理 — 第一级：项目列表（工作台）
 * 层级：项目（本页）→ 招标项目 → 标段 → 公告；点项目行进入其下的招标项目列表。
 * 创建入口：本页提供「创建项目」（EntityFormModal，按 ProjectVO 规范字段 + 可挂多个附件）。
 * 数据：GET /bidding/project/page（权限 bidding:project:query）；写：POST /bidding/project/save。
 * 附件：项目实体当前后端无附件持久化接口，附件明细本地留存、GUID 照规范写入 ATTACHMENT_CODE（详见 attachmentStore.ts）。 */
import { computed, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';

import { Button, Drawer, Input, message, Popconfirm, Table, Tag } from 'ant-design-vue';

import { deleteProject, getProjectPage, type ProjectApi, saveProject } from '#/api/bidding/project';

import AttachmentList from '../../../bidding/vo/AttachmentList.vue';
import { loadAttachments } from '../../../bidding/vo/attachmentStore';
import EntityFormModal from '../../../bidding/vo/EntityFormModal.vue';
import { PROJECT_DEF } from '../../../bidding/vo/entityForms';

import './panel.css';

const router = useRouter();

const loading = ref(false);
const rows = ref<ProjectApi.Project[]>([]);
const total = ref(0);
const pageNo = ref(1);
const pageSize = ref(10);
const query = reactive<{ projectCode: string; projectName: string }>({ projectCode: '', projectName: '' });

const editorOpen = ref(false);
const editing = ref<null | ProjectApi.Project>(null);

const attDrawerOpen = ref(false);
const attOwner = ref<null | ProjectApi.Project>(null);
const attList = computed(() => (attOwner.value ? loadAttachments('project', attOwner.value.projectCode) : []));

async function load(): Promise<void> {
  loading.value = true;
  try {
    const res = await getProjectPage({
      pageNo: pageNo.value,
      pageSize: pageSize.value,
      projectCode: query.projectCode || undefined,
      projectName: query.projectName || undefined,
    });
    rows.value = res?.list ?? [];
    total.value = res?.total ?? 0;
  } catch (e) {
    message.error('加载项目列表失败：' + ((e as Error).message || String(e)));
  } finally {
    loading.value = false;
  }
}
onMounted(load);

function search(): void {
  pageNo.value = 1;
  void load();
}
function reset(): void {
  query.projectCode = '';
  query.projectName = '';
  search();
}
function createProject(): void {
  editing.value = null;
  editorOpen.value = true;
}
function editProject(r: ProjectApi.Project): void {
  editing.value = r;
  editorOpen.value = true;
}
function openAttachments(r: ProjectApi.Project): void {
  attOwner.value = r;
  attDrawerOpen.value = true;
}
async function remove(r: ProjectApi.Project): Promise<void> {
  try {
    await deleteProject(r.id!);
    message.success('已删除项目：' + r.projectCode);
    void load();
  } catch (e) {
    message.error('删除失败：' + ((e as Error).message || String(e)));
  }
}
/** 进入第二级：该项目的招标项目列表 */
function drill(r: ProjectApi.Project): void {
  void router.push('/portal/bidding/projects/' + encodeURIComponent(r.projectCode) + '/tender-projects');
}
function attCount(r: ProjectApi.Project): number {
  return loadAttachments('project', r.projectCode).length;
}
async function doSave(data: Record<string, unknown>): Promise<unknown> {
  return saveProject(data as unknown as ProjectApi.Project);
}
</script>

<template>
  <div class="pn">
    <div class="pn-head">
      <div>
        <h1 class="pn-title">项目公告管理 · 项目</h1>
        <p class="pn-sub">
          业务层级：<b>项目</b> → 招标项目 → 标段（包） → 公告/公示。点击项目行查看该项目下的全部招标项目。
          项目字段严格照录 VO 规范 ProjectVO（42 项），平台注入参数（平台码/时间戳/统一交易标识码）由平台维护；
          项目可挂多个附件。
        </p>
      </div>
      <div class="pn-acts">
        <Button type="primary" @click="createProject">创建项目</Button>
        <Button @click="load">刷新</Button>
      </div>
    </div>

    <div class="pn-card pn-trace">
      <div class="pn-card-h">
        <span class="pn-card-t">检索</span>
        <Input v-model:value="query.projectCode" placeholder="项目编号（前缀）" style="width: 200px" allow-clear @press-enter="search" />
        <Input v-model:value="query.projectName" placeholder="项目名称（模糊）" style="width: 220px" allow-clear @press-enter="search" />
        <Button type="primary" @click="search">查询</Button>
        <Button @click="reset">重置</Button>
        <span class="pn-dim">共 {{ total }} 个项目</span>
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
            showTotal: (t: number) => `共 ${ t } 条`,
            onChange: (p: number, ps: number) => { pageNo = p; pageSize = ps; load(); },
          }"
        >
          <Table.Column title="项目编号" data-index="projectCode" :width="200">
            <template #default="{ record }">
              <a class="pn-link pn-code" @click="drill(record)">{{ record.projectCode }}</a>
            </template>
          </Table.Column>
          <Table.Column title="项目名称" data-index="projectName" :ellipsis="true" />
          <Table.Column title="行政区划" data-index="regionCode" :width="110" />
          <Table.Column title="行业类别" data-index="industriesType" :width="110" />
          <Table.Column title="资金来源" data-index="fundSource" :width="120" :ellipsis="true" />
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
              <Button size="small" type="link" @click="drill(record)">招标项目</Button>
              <Button size="small" type="link" @click="editProject(record)">编辑</Button>
              <Popconfirm title="删除项目？存在招标项目时后端会拒绝。" @confirm="remove(record)">
                <Button size="small" type="link" danger>删除</Button>
              </Popconfirm>
            </template>
          </Table.Column>
        </Table>
        <div v-if="!loading && !rows.length" class="pn-empty">
          暂无项目。点右上「创建项目」建立第一个项目，之后逐级创建招标项目与标段，最后在标段下创建公告。
        </div>
      </div>
    </div>

    <!-- 创建/编辑项目（VO 规范字段 + 多附件） -->
    <EntityFormModal v-model:open="editorOpen" :def="PROJECT_DEF" :record="editing" :save="doSave" @saved="load" />

    <!-- 附件查看 -->
    <Drawer v-model:open="attDrawerOpen" :title="`项目附件 · ${ attOwner?.projectCode ?? ''}`" :width="760">
      <div class="pn-tip">
        项目实体后端暂未提供附件持久化接口：附件明细保存在本地（按项目编号），GUID 已按规范写入 payload.ATTACHMENT_CODE。
        上传与预览均可用；后端提供附件集接口后改为按实体读取。
      </div>
      <AttachmentList :list="attList" title="本项目附件" />
    </Drawer>
  </div>
</template>
