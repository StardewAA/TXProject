<script lang="ts" setup>
import type { HrmEmployeeConfigApi } from '#/api/hrm/employee/config';
import type { HrmPortalEmployeeApi } from '#/api/hrm/portal/employee';

import { onActivated, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';

import { Page } from '@vben/common-ui';
import { IconifyIcon } from '@vben/icons';

import { Button, Card, Spin, Tabs } from 'ant-design-vue';

import { getEmployee } from '#/api/hrm/portal/employee';
import { getEmployeeFieldConfigList } from '#/api/hrm/portal/employee/field-config';
import { checkHrmPortalAccess } from '#/views/hrm/utils/employee';

import EmployeeBaseInfo from './EmployeeBaseInfo.vue';
import EmployeeForm from './EmployeeForm.vue';
import EmployeePostInfo from './EmployeePostInfo.vue';
import MyExperiencePanel from './MyExperiencePanel.vue';

defineOptions({ name: 'HrmPortalEmployee' });

const router = useRouter();
const accessible = ref(false);
const loading = ref(false);
const activeTab = ref('base');
const employee = ref<HrmPortalEmployeeApi.PortalEmployee>(
  {} as HrmPortalEmployeeApi.PortalEmployee,
);
const fieldConfigList = ref<HrmEmployeeConfigApi.FieldConfig[]>([]);
const baseInfoRef = ref<InstanceType<typeof EmployeeBaseInfo>>();
const postInfoRef = ref<InstanceType<typeof EmployeePostInfo>>();
const employeeFormRef = ref<InstanceType<typeof EmployeeForm>>();

/** 获得当前员工档案和字段配置 */
async function loadEmployee() {
  loading.value = true;
  try {
    const [employeeData, fields] = await Promise.all([
      getEmployee(),
      getEmployeeFieldConfigList(),
    ]);
    employee.value = employeeData;
    fieldConfigList.value = fields;
  } finally {
    loading.value = false;
  }
}

/** 刷新当前员工档案页面 */
async function refreshEmployee() {
  await loadEmployee();
  await (activeTab.value === 'base'
    ? baseInfoRef.value?.getList()
    : postInfoRef.value?.getQuitInfo());
}

/** 打开员工档案编辑表单 */
function openEmployeeForm() {
  employeeFormRef.value?.open(employee.value, fieldConfigList.value);
}

/** 页面激活时刷新员工档案 */
/** 员工端「我的档案」初始化：首次进入用 onMounted 兜底（未被 KeepAlive 缓存时 onActivated 不触发） */
async function initPortalPage() {
  accessible.value = await checkHrmPortalAccess(router);
  if (!accessible.value) {
    return;
  }
  await loadEmployee();
}

onMounted(initPortalPage);
onActivated(() => {
  if (accessible.value) {
    void loadEmployee();
  }
});
</script>

<template>
  <Page v-if="accessible">
    <Card :style="{ marginBottom: '15px' }">
      <div class="flex items-center justify-between">
        <span class="text-lg font-semibold">我的档案</span>
        <Button @click="refreshEmployee">
          <IconifyIcon icon="lucide:refresh-cw" class="mr-1" />
          刷新
        </Button>
      </div>
    </Card>

    <Spin :spinning="loading">
      <Tabs v-model:active-key="activeTab">
        <Tabs.TabPane key="base" tab="基本信息">
          <EmployeeBaseInfo
            ref="baseInfoRef"
            :employee="employee"
            :field-config-list="fieldConfigList"
            @edit="openEmployeeForm"
          />
        </Tabs.TabPane>
        <Tabs.TabPane key="post" force-render tab="岗位信息">
          <EmployeePostInfo ref="postInfoRef" :employee="employee" />
        </Tabs.TabPane>
        <!-- 员工自助补录：保存后写的就是后台同一条员工记录，HR 立即可见并收到复核提醒 -->
        <Tabs.TabPane key="education" force-render tab="教育经历">
          <MyExperiencePanel type="education" />
        </Tabs.TabPane>
        <Tabs.TabPane key="work" force-render tab="工作经历">
          <MyExperiencePanel type="work" />
        </Tabs.TabPane>
      </Tabs>
    </Spin>

    <EmployeeForm ref="employeeFormRef" @success="loadEmployee" />
  </Page>
</template>
