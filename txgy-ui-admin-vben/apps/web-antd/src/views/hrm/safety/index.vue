<script lang="ts" setup>
import type { HrmSafetyApi } from '#/api/hrm/safety';

import { onMounted, ref, watch } from 'vue';

import { Page } from '@vben/common-ui';
import { openWindow } from '@vben/utils';

import {
  Button,
  Card,
  message,
  Select,
  Space,
  Table,
  Tabs,
  Tag,
} from 'ant-design-vue';

import {
  deleteHealthCheck,
  deleteSafetyEducation,
  getHealthCheckPage,
  getSafetyEducationPage,
  HrmHealthCheckResultOptions,
  HrmHealthCheckTypeOptions,
  HrmSafetyEducationLevelOptions,
} from '#/api/hrm/safety';

import EducationForm from './modules/education-form.vue';
import HealthForm from './modules/health-form.vue';

defineOptions({ name: 'HrmSafety' });

const activeTab = ref('education');
const loading = ref(false);
const educations = ref<HrmSafetyApi.SafetyEducation[]>([]);
const healthChecks = ref<HrmSafetyApi.HealthCheck[]>([]);
const educationTotal = ref(0);
const healthTotal = ref(0);
const educationFormRef = ref<InstanceType<typeof EducationForm>>();
const healthFormRef = ref<InstanceType<typeof HealthForm>>();

const educationQuery = ref({ pageNo: 1, pageSize: 10, level: undefined as any });
const healthQuery = ref({ pageNo: 1, pageSize: 10, result: undefined as any, checkType: undefined as any });

async function loadEducations() {
  loading.value = true;
  try {
    const page = await getSafetyEducationPage(educationQuery.value);
    educations.value = page.list;
    educationTotal.value = page.total;
  } finally {
    loading.value = false;
  }
}

async function loadHealthChecks() {
  loading.value = true;
  try {
    const page = await getHealthCheckPage(healthQuery.value);
    healthChecks.value = page.list;
    healthTotal.value = page.total;
  } finally {
    loading.value = false;
  }
}

watch(
  () => [educationQuery.value.level, educationQuery.value.pageNo],
  () => loadEducations(),
);
watch(
  () => [
    healthQuery.value.result,
    healthQuery.value.checkType,
    healthQuery.value.pageNo,
  ],
  () => loadHealthChecks(),
);

onMounted(async () => {
  await loadEducations();
  await loadHealthChecks();
});

async function handleDeleteEducation(id?: number) {
  if (!id) return;
  await deleteSafetyEducation(id);
  message.success('删除成功');
  await loadEducations();
}

async function handleDeleteHealth(id?: number) {
  if (!id) return;
  await deleteHealthCheck(id);
  message.success('删除成功');
  await loadHealthChecks();
}

</script>

<template>
  <Page auto-content-height>
    <Card :bordered="false">
      <Tabs v-model:active-key="activeTab">
        <Tabs.TabPane key="education" tab="三级安全教育">
          <Space class="mb-3" wrap>
            <Select
              v-model:value="educationQuery.level"
              :options="[...HrmSafetyEducationLevelOptions]"
              allow-clear
              class="min-w-[130px]"
              placeholder="全部级别"
            />
            <Button type="primary" @click="educationFormRef?.open()">
              新增教育记录
            </Button>
            <span class="text-muted-foreground text-xs">
              记录公司级 / 项目级 / 班组级三级安全教育与考核结果，作为员工的安全资质档案
            </span>
          </Space>
          <Table
            :columns="[
              { title: '姓名', dataIndex: 'employeeName', width: 100 },
              { title: '工号', dataIndex: 'jobNumber', width: 110 },
              { title: '级别', dataIndex: 'levelName', width: 90 },
              { title: '教育日期', dataIndex: 'educationDate', width: 115 },
              { title: '教育人', dataIndex: 'educatorName', width: 100 },
              { title: '学时', dataIndex: 'hours', width: 80 },
              { title: '成绩', dataIndex: 'examScore', width: 80 },
              { title: '是否合格', key: 'qualified', width: 100 },
              { title: '附件', key: 'files', width: 120 },
              { title: '操作', key: 'action', width: 140, fixed: 'right' },
            ]"
            :data-source="educations"
            :loading="loading"
            :pagination="{
              current: educationQuery.pageNo,
              pageSize: educationQuery.pageSize,
              total: educationTotal,
              onChange: (page: number) => (educationQuery.pageNo = page),
            }"
            :row-key="(r: HrmSafetyApi.SafetyEducation) => String(r.id)"
            :scroll="{ x: 1400 }"
            bordered
            size="small"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'qualified'">
                <Tag :color="record.qualified ? 'success' : 'error'">
                  {{ record.qualified ? '合格' : '不合格' }}
                </Tag>
              </template>
              <template v-else-if="column.key === 'files'">
                <Button
                  v-for="url in record.fileUrls || []"
                  :key="url"
                  type="link"
                  @click="openWindow(url)"
                >
                  查看
                </Button>
                <span v-if="!record.fileUrls?.length">-</span>
              </template>
              <template v-else-if="column.key === 'action'">
                <Button type="link" @click="educationFormRef?.open(record)">
                  编辑
                </Button>
                <Button danger type="link" @click="handleDeleteEducation(record.id)">
                  删除
                </Button>
              </template>
            </template>
          </Table>
        </Tabs.TabPane>

        <Tabs.TabPane key="health" tab="体检与职业健康">
          <Space class="mb-3" wrap>
            <Select
              v-model:value="healthQuery.checkType"
              :options="[...HrmHealthCheckTypeOptions]"
              allow-clear
              class="min-w-[130px]"
              placeholder="全部类型"
            />
            <Select
              v-model:value="healthQuery.result"
              :options="[...HrmHealthCheckResultOptions]"
              allow-clear
              class="min-w-[130px]"
              placeholder="全部结论"
            />
            <Button type="primary" @click="healthFormRef?.open()">新增体检记录</Button>
            <span class="text-muted-foreground text-xs">
              防腐保温涉及粉尘、噪声、有害气体岗位，在岗期间需做职业健康检查
            </span>
          </Space>
          <Table
            :columns="[
              { title: '姓名', dataIndex: 'employeeName', width: 100 },
              { title: '工号', dataIndex: 'jobNumber', width: 110 },
              { title: '体检类型', dataIndex: 'checkTypeName', width: 100 },
              { title: '体检日期', dataIndex: 'checkDate', width: 115 },
              { title: '体检机构', dataIndex: 'institution', width: 200 },
              { title: '结论', key: 'result', width: 100 },
              { title: '复查日期', dataIndex: 'recheckDate', width: 115 },
              { title: '有效期至', dataIndex: 'validUntil', width: 115 },
              { title: '是否有效', key: 'valid', width: 100 },
              { title: '附件', key: 'files', width: 120 },
              { title: '操作', key: 'action', width: 140, fixed: 'right' },
            ]"
            :data-source="healthChecks"
            :loading="loading"
            :pagination="{
              current: healthQuery.pageNo,
              pageSize: healthQuery.pageSize,
              total: healthTotal,
              onChange: (page: number) => (healthQuery.pageNo = page),
            }"
            :row-key="(r: HrmSafetyApi.HealthCheck) => String(r.id)"
            :scroll="{ x: 1400 }"
            bordered
            size="small"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'result'">
                <Tag
                  :color="
                    record.result === 1
                      ? 'success'
                      : record.result === 2
                        ? 'error'
                        : 'warning'
                  "
                >
                  {{ record.resultName }}
                </Tag>
              </template>
              <template v-else-if="column.key === 'valid'">
                <Tag :color="record.valid ? 'success' : 'default'">
                  {{ record.valid ? '有效' : '已过期' }}
                </Tag>
              </template>
              <template v-else-if="column.key === 'files'">
                <Button
                  v-for="url in record.fileUrls || []"
                  :key="url"
                  type="link"
                  @click="openWindow(url)"
                >
                  查看
                </Button>
                <span v-if="!record.fileUrls?.length">-</span>
              </template>
              <template v-else-if="column.key === 'action'">
                <Button type="link" @click="healthFormRef?.open(record)">编辑</Button>
                <Button danger type="link" @click="handleDeleteHealth(record.id)">
                  删除
                </Button>
              </template>
            </template>
          </Table>
        </Tabs.TabPane>
      </Tabs>
    </Card>
    <EducationForm ref="educationFormRef" @success="loadEducations" />
    <HealthForm ref="healthFormRef" @success="loadHealthChecks" />
  </Page>
</template>
