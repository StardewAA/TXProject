<script lang="ts" setup>
import { computed, ref } from 'vue';
import { useRouter } from 'vue-router';

import { Page } from '@vben/common-ui';

import { Button, Input, Select, Space, Table, Tag, message } from 'ant-design-vue';

import { cmsContentConfigs } from '../content/config';

defineOptions({ name: 'CmsHome' });

const router = useRouter();
const keyword = ref('');
const queryType = ref('standard');

const moduleOptions = [
  { label: '标准规范库', value: 'standard' },
  { label: '材料参数库', value: 'material' },
  { label: '工艺案例库', value: 'process' },
  { label: '定额资料库', value: 'quota' },
  { label: '政策资讯', value: 'policy' },
];

const allRecords = computed(() =>
  moduleOptions.flatMap((option) => {
    const config = cmsContentConfigs[option.value];
    return config.records.map((record) => ({
      ...record,
      groupLabel: option.label,
      moduleKey: option.value,
      title:
        record.title ||
        record.name ||
        record.standardNo ||
        '未命名内容',
    }));
  }),
);

const filteredRecords = computed(() => {
  const value = keyword.value.trim().toLowerCase();
  return allRecords.value.filter((record) => {
    const matchedModule = queryType.value === 'all' || record.moduleKey === queryType.value;
    const matchedKeyword =
      !value ||
      Object.values(record).some((item) =>
        String(item ?? '').toLowerCase().includes(value),
      );
    return matchedModule && matchedKeyword;
  });
});

const columns = [
  { dataIndex: 'groupLabel', key: 'groupLabel', title: '所属模块', width: 130 },
  { dataIndex: 'title', key: 'title', title: '内容名称', width: 320 },
  { dataIndex: 'code', key: 'code', title: '编号/分类', width: 160 },
  { dataIndex: 'status', key: 'status', title: '状态', width: 110 },
  { dataIndex: 'updatedAt', key: 'updatedAt', title: '更新时间', width: 130 },
  { dataIndex: 'action', key: 'action', title: '操作', width: 260 },
];

const statusColor: Record<string, string> = {
  已发布: 'success',
  待审核: 'warning',
  已驳回: 'error',
  草稿: 'default',
};

function recordTitle(record: Record<string, any>) {
  return (
    record.title ||
    record.name ||
    record.standardNo ||
    '该条内容'
  );
}

function handleQuery() {
  message.success(`查询到 ${filteredRecords.value.length} 条内容`);
}

function handleAudit() {
  keyword.value = '待审核';
  message.success('已筛选出待审核内容');
}

function handleCreate() {
  message.info('新增接口已预留，等待后端联调后开放');
}

function handleDelete(record: Record<string, any>) {
  message.info(`已选择删除：${recordTitle(record)}，接口联调后生效`);
}

function handleEdit(record: Record<string, any>) {
  message.info(`已选择编辑：${recordTitle(record)}，接口联调后打开`);
}

function handleSubmitAudit(record: Record<string, any>) {
  message.info(`已提交审核：${recordTitle(record)}，接口联调后生效`);
}
</script>

<template>
  <Page>
    <div class="cms-home">
      <section class="cms-search-hero">
        <div class="cms-search-heading">
          <p>CMS 智能管理</p>
          <h1>标准、材料、工艺、定额、政策统一查询与管理</h1>
        </div>

        <div class="cms-big-search">
          <Select
            v-model:value="queryType"
            :options="[{ label: '全部模块', value: 'all' }, ...moduleOptions]"
            class="cms-search-select"
          />
          <Input
            v-model:value="keyword"
            class="cms-search-input"
            placeholder="输入标准号、名称、编号或关键词"
            size="large"
            @press-enter="handleQuery"
          />
          <Button type="primary" size="large" @click="handleQuery">查询</Button>
        </div>

        <div class="cms-search-actions">
          <Button type="primary" @click="handleQuery">查询</Button>
          <Button @click="handleAudit">审核</Button>
          <Button type="primary" ghost @click="handleCreate">新增</Button>
          <Button danger @click="message.info('请先在下方结果中选择要删除的内容')">
            删除
          </Button>
        </div>
      </section>

      <section class="cms-home-block">
        <div class="cms-block-heading">
          <h2>查询结果</h2>
          <span>{{ filteredRecords.length }} 条</span>
        </div>
        <Table
          :columns="columns"
          :data-source="filteredRecords"
          :pagination="{ pageSize: 8, showSizeChanger: true }"
          row-key="id"
          size="middle"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'code'">
              {{
                record.standardNo ||
                record.category ||
                record.resourceType ||
                record.quotaType ||
                record.department ||
                '-'
              }}
            </template>
            <template v-else-if="column.key === 'status'">
              <Tag :color="statusColor[record.status] || 'default'">
                {{ record.status }}
              </Tag>
            </template>
            <template v-else-if="column.key === 'action'">
              <Space :size="4">
                <Button size="small" type="link" @click="handleEdit(record)">
                  查看/编辑
                </Button>
                <Button size="small" type="link" @click="handleSubmitAudit(record)">
                  提交审核
                </Button>
                <Button size="small" danger type="link" @click="handleDelete(record)">
                  删除
                </Button>
              </Space>
            </template>
          </template>
        </Table>
      </section>

      <section class="cms-home-block">
        <div class="cms-block-heading">
          <h2>CMS 管理入口</h2>
          <span>左侧菜单快速进入</span>
        </div>
        <div class="cms-entry-grid">
          <button type="button" @click="router.push('/cms/expert/review')">
            专家管理
          </button>
          <button type="button" @click="router.push('/cms/consulting/knowledge')">
            咨询服务
          </button>
          <button type="button" @click="router.push('/cms/crawler/source')">
            政策采集管理
          </button>
          <button type="button" @click="router.push('/cms/statistics/content')">
            统计分析
          </button>
        </div>
      </section>
    </div>
  </Page>
</template>

<style scoped>
.cms-home {
  display: grid;
  gap: 16px;
}

.cms-search-hero {
  padding: 28px 30px;
  border: 1px solid #bfdbfe;
  border-radius: 18px;
  background:
    linear-gradient(120deg, rgb(37 99 235 / 14%), transparent 58%),
    #fff;
}

.cms-search-heading p {
  margin: 0 0 6px;
  color: #1d4ed8;
  font-size: 13px;
  font-weight: 700;
}

.cms-search-heading h1 {
  margin: 0;
  color: #0f172a;
  font-size: 26px;
  line-height: 1.3;
}

.cms-big-search {
  display: flex;
  max-width: 960px;
  margin-top: 24px;
  gap: 12px;
}

.cms-search-select {
  width: 160px;
}

.cms-search-input {
  flex: 1;
}

.cms-search-actions {
  display: flex;
  margin-top: 16px;
  gap: 10px;
}

.cms-home-block {
  padding: 18px 20px 4px;
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  background: #fff;
}

.cms-block-heading {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 14px;
}

.cms-block-heading h2 {
  margin: 0;
  color: #0f172a;
  font-size: 18px;
}

.cms-block-heading span {
  color: #94a3b8;
  font-size: 13px;
}

.cms-entry-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 14px;
}

.cms-entry-grid button {
  padding: 12px;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  background: #f8fafc;
  color: #334155;
  cursor: pointer;
  font-size: 14px;
}

.cms-entry-grid button:hover {
  border-color: #93c5fd;
  color: #1d4ed8;
}

@media (max-width: 900px) {
  .cms-big-search {
    flex-wrap: wrap;
  }

  .cms-search-select,
  .cms-search-input {
    width: 100%;
  }

  .cms-entry-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
