<script lang="ts" setup>
import type { Rule } from 'ant-design-vue/es/form';

import type { HrmInsuranceSchemeApi } from '#/api/hrm/insurance/scheme';

import { computed, reactive, ref } from 'vue';

import { confirm, useVbenModal } from '@vben/common-ui';
import { DICT_TYPE } from '@vben/constants';
import { getDictLabel } from '@vben/hooks';

import {
  Alert,
  Button,
  Checkbox,
  Dropdown,
  Form,
  Input,
  InputNumber,
  Menu,
  message,
  Radio,
  Select,
  Table,
} from 'ant-design-vue';

import {
  createInsuranceScheme,
  getInsuranceScheme,
  updateInsuranceScheme,
} from '#/api/hrm/insurance/scheme';
import {
  getSalaryRateLimitList,
  HrmSalaryRateLimitBizType,
} from '#/api/hrm/salary/config/rate-limit';
import {
  getInsuranceStandardProjectList,
  getInsuranceStandardTypeList,
} from '#/api/hrm/insurance/standard';
import { AreaCascader } from '#/components/area';
import { $t } from '#/locales';
import {
  HrmInsuranceProjectType,
  HrmInsuranceSchemeType,
} from '#/views/hrm/utils/constants';
import {
  formatHrmInsuranceProjectName,
  formatHrmMoney,
} from '#/views/hrm/utils/format';

defineOptions({ name: 'HrmInsuranceSchemeForm' });

const emit = defineEmits(['success']);

const SOCIAL_PROJECT_TYPES = [
  HrmInsuranceProjectType.ENDOWMENT,
  HrmInsuranceProjectType.MEDICAL,
  HrmInsuranceProjectType.UNEMPLOYMENT,
  HrmInsuranceProjectType.EMPLOYMENT_INJURY,
  HrmInsuranceProjectType.MATERNITY,
  HrmInsuranceProjectType.SUPPLEMENTARY_MEDICAL,
  HrmInsuranceProjectType.SUPPLEMENTARY_ENDOWMENT,
  HrmInsuranceProjectType.DISABILITY,
];
const PROVIDENT_FUND_PROJECT_TYPES = [HrmInsuranceProjectType.PROVIDENT_FUND];

const formType = ref<'create' | 'update'>('create');
const formRef = ref();
const standardLoading = ref(false);
const insuranceTypeList = ref<{ code: string; label: string; value: string }[]>(
  [],
);
const formData = ref<HrmInsuranceSchemeApi.InsuranceScheme>(
  createDefaultFormData(),
);
/**
 * 各参保项目的比例上限（%），来自「薪资管理 → 比例上限」配置。
 * key 为参保项目类型编码；未配置时按 100% 兜底，上限不再写死。
 */
const rateLimits = ref<
  Record<string, { corporate?: number; personal?: number }>
>({});

const dialogTitle = computed(() =>
  formType.value === 'create'
    ? $t('ui.actionTitle.create', ['参保方案'])
    : $t('ui.actionTitle.edit', ['参保方案']),
);

const formRules = reactive<Record<string, Rule[]>>({
  name: [{ required: true, message: '方案名称不能为空', trigger: 'blur' }],
  areaId: [{ required: true, message: '参保城市不能为空', trigger: 'change' }],
  type: [{ required: true, message: '方案类型不能为空', trigger: 'change' }],
  projectList: [{ validator: validateProjectList, trigger: 'change' }],
});

const projectSections = computed(() => [
  {
    key: 'social' as const,
    label: '社保',
    projects:
      formData.value.projectList?.filter((project) =>
        isSocialProject(project.type),
      ) || [],
    options: getProjectOptions(SOCIAL_PROJECT_TYPES),
    customType: HrmInsuranceProjectType.CUSTOM_SOCIAL_SECURITY,
  },
  {
    key: 'providentFund' as const,
    label: '公积金',
    projects:
      formData.value.projectList?.filter((project) =>
        isProvidentFundProject(project.type),
      ) || [],
    options: getProjectOptions(PROVIDENT_FUND_PROJECT_TYPES),
    customType: HrmInsuranceProjectType.CUSTOM_PROVIDENT_FUND,
  },
]);

function createProject(type: number): HrmInsuranceSchemeApi.Project {
  return {
    type,
    name: isCustomProject(type) ? '' : getProjectTypeName(type),
    baseAmount: 0,
    corporateRate: 0,
    personalRate: 0,
    corporateAmount: 0,
    personalAmount: 0,
  };
}

/** 读取比例上限配置 */
async function loadRateLimits() {
  const map: Record<string, { corporate?: number; personal?: number }> = {};
  try {
    const list = await getSalaryRateLimitList(
      HrmSalaryRateLimitBizType.INSURANCE_SCHEME,
    );
    for (const item of list || []) {
      if (!item?.itemCode) continue;
      // status === 0 表示启用；停用表示该项不做上限校验
      if ((item.status ?? 0) !== 0) {
        // 停用：不给上限（undefined），表单不做上限约束
        map[item.itemCode] = { corporate: undefined, personal: undefined };
        continue;
      }
      map[item.itemCode] = {
        corporate:
          item.corporateRateLimit === null || item.corporateRateLimit === undefined
            ? 100
            : Number(item.corporateRateLimit),
        personal:
          item.personalRateLimit === null || item.personalRateLimit === undefined
            ? 100
            : Number(item.personalRateLimit),
      };
    }
  } catch {
    // 配置读取失败时按兜底上限 100% 处理，不阻塞表单
  }
  rateLimits.value = map;
}

/** 公司缴纳比例上限；返回 undefined 表示该项已停用上限校验 */
function getCorporateLimit(type?: number): number | undefined {
  if (type === undefined) return 100;
  return rateLimits.value[String(type)]?.corporate ?? 100;
}

/** 个人缴纳比例上限；返回 undefined 表示该项已停用上限校验 */
function getPersonalLimit(type?: number): number | undefined {
  if (type === undefined) return 100;
  return rateLimits.value[String(type)]?.personal ?? 100;
}

function createDefaultFormData(): HrmInsuranceSchemeApi.InsuranceScheme {
  return {
    name: '',
    householdType: '',
    type: HrmInsuranceSchemeType.PROPORTION,
    projectList: [
      HrmInsuranceProjectType.ENDOWMENT,
      HrmInsuranceProjectType.MEDICAL,
      HrmInsuranceProjectType.UNEMPLOYMENT,
      HrmInsuranceProjectType.EMPLOYMENT_INJURY,
      HrmInsuranceProjectType.MATERNITY,
      HrmInsuranceProjectType.PROVIDENT_FUND,
    ].map((type) => createProject(type)),
  };
}

function getProjectTypeName(type?: number) {
  return getDictLabel(DICT_TYPE.HRM_INSURANCE_PROJECT_TYPE, type) || '';
}

function isCustomProject(type?: number) {
  return (
    type === HrmInsuranceProjectType.CUSTOM_SOCIAL_SECURITY ||
    type === HrmInsuranceProjectType.CUSTOM_PROVIDENT_FUND
  );
}

function isSocialProject(type?: number) {
  return type !== undefined && type < HrmInsuranceProjectType.PROVIDENT_FUND;
}

function isProvidentFundProject(type?: number) {
  return type !== undefined && type >= HrmInsuranceProjectType.PROVIDENT_FUND;
}

function getProjectOptions(types: number[]) {
  return types.map((type) => ({
    label: getProjectTypeName(type),
    value: type,
  }));
}

function isProjectTypeUsed(type: number) {
  return (
    formData.value.projectList?.some((project) => project.type === type) ||
    false
  );
}

function addProject(type: number) {
  formData.value.projectList = [
    ...(formData.value.projectList || []),
    createProject(type),
  ];
}

function removeProject(project: HrmInsuranceSchemeApi.Project) {
  formData.value.projectList = (formData.value.projectList || []).filter(
    (item) => item !== project,
  );
}

function handleProjectChecked(checked: boolean, type: number) {
  const project = formData.value.projectList?.find(
    (item) => item.type === type,
  );
  if (checked) {
    if (!project) {
      addProject(type);
    }
    return;
  }
  if (project) {
    removeProject(project);
  }
}

function addCustomProject(type: number) {
  addProject(type);
}

async function getInsuranceTypeList(areaId: number) {
  standardLoading.value = true;
  try {
    const data = await getInsuranceStandardTypeList(areaId);
    if (formData.value.areaId !== areaId) {
      return;
    }
    insuranceTypeList.value = data.map((item) => ({
      code: item.code,
      label: item.name,
      value: item.code,
    }));
    const selectedType = data.find(
      (item) =>
        item.name === formData.value.householdType &&
        item.code !== formData.value.householdType,
    );
    if (selectedType) {
      formData.value.householdType = selectedType.code;
    }
  } finally {
    standardLoading.value = false;
  }
}

async function handleAreaChange(areaId?: number) {
  formData.value.householdType = '';
  insuranceTypeList.value = [];
  resetStandardProjectValues();
  if (areaId) {
    await getInsuranceTypeList(areaId);
  }
}

async function handleHouseTypeChange() {
  const areaId = formData.value.areaId;
  const typeCode = formData.value.householdType;
  if (!areaId || !typeCode) {
    return;
  }
  standardLoading.value = true;
  try {
    const projects = await getInsuranceStandardProjectList({
      areaId,
      typeCode,
    });
    if (
      formData.value.areaId !== areaId ||
      formData.value.householdType !== typeCode
    ) {
      return;
    }
    const customProjects =
      formData.value.projectList?.filter((project) =>
        isCustomProject(project.type),
      ) || [];
    formData.value.projectList = [
      ...projects.map((project) => ({
        ...project,
        id: undefined,
        schemeId: undefined,
        name: getProjectTypeName(project.type),
      })),
      ...customProjects,
    ];
  } finally {
    standardLoading.value = false;
  }
}

function resetStandardProjectValues() {
  formData.value.projectList?.forEach((project) => {
    if (isCustomProject(project.type)) {
      return;
    }
    project.baseAmount = 0;
    project.corporateRate = 0;
    project.personalRate = 0;
    project.corporateAmount = 0;
    project.personalAmount = 0;
  });
}

function validateProjectList(
  _rule: unknown,
  value: HrmInsuranceSchemeApi.Project[],
) {
  if (!value?.some((project) => isSocialProject(project.type))) {
    return Promise.reject(new Error('请至少添加一个社保项目'));
  }
  if (value.some((project) => !project.name?.trim())) {
    return Promise.reject(new Error('参保项目名称不能为空'));
  }
  // 比例上限校验：按「薪资管理 → 比例上限」配置逐项校验（不是写死 100%）
  if (formData.value.type === HrmInsuranceSchemeType.PROPORTION) {
    for (const project of value) {
      const projectName =
        project.name?.trim() || getProjectTypeName(project.type) || '该项目';
      const corporateRate = Number(project.corporateRate || 0);
      const personalRate = Number(project.personalRate || 0);
      const corporateLimit = getCorporateLimit(project.type);
      const personalLimit = getPersonalLimit(project.type);
      if (corporateLimit !== undefined && corporateRate > corporateLimit) {
        return Promise.reject(
          new Error(
            `「${projectName}」公司缴纳比例填写 ${corporateRate}% 超过该项上限 ${corporateLimit}%，请到「薪资管理 → 比例上限」调整上限后再保存`,
          ),
        );
      }
      if (personalLimit !== undefined && personalRate > personalLimit) {
        return Promise.reject(
          new Error(
            `「${projectName}」个人缴纳比例填写 ${personalRate}% 超过该项上限 ${personalLimit}%，请到「薪资管理 → 比例上限」调整上限后再保存`,
          ),
        );
      }
    }
  }
  return Promise.resolve();
}

function calculateAmount(
  project: HrmInsuranceSchemeApi.Project,
  type: 'corporate' | 'personal',
) {
  const proportion =
    type === 'corporate' ? project.corporateRate : project.personalRate;
  return Number(project.baseAmount || 0) * Number(proportion || 0) * 0.01;
}

function getSectionSummary(
  projects: HrmInsuranceSchemeApi.Project[],
  column: 'corporateAmount' | 'personalAmount',
) {
  const type = column === 'corporateAmount' ? 'corporate' : 'personal';
  return formatHrmMoney(
    projects.reduce(
      (total, project) =>
        total +
        (formData.value.type === HrmInsuranceSchemeType.PROPORTION
          ? calculateAmount(project, type)
          : Number(project[column] || 0)),
      0,
    ),
  );
}

const [Modal, modalApi] = useVbenModal({
  async onConfirm() {
    await formRef.value?.validate();
    modalApi.lock();
    try {
      if (formType.value === 'create') {
        await createInsuranceScheme(formData.value);
      } else {
        await confirm('编辑参保方案后，不会变更现有参保信息，确定提交吗？');
        await updateInsuranceScheme(formData.value);
      }
      message.success($t('ui.actionMessage.operationSuccess'));
      await modalApi.close();
      emit('success');
    } finally {
      modalApi.unlock();
    }
  },
  async onOpenChange(isOpen: boolean) {
    if (!isOpen) {
      formData.value = createDefaultFormData();
      insuranceTypeList.value = [];
      return;
    }
    const data = modalApi.getData<{ id?: number; type: 'create' | 'update' }>();
    formType.value = data?.type || 'create';
    await loadRateLimits();
    if (data?.id) {
      formData.value = await getInsuranceScheme(data.id);
      if (formData.value.areaId) {
        await getInsuranceTypeList(formData.value.areaId);
      }
    } else {
      formData.value = createDefaultFormData();
    }
  },
});
</script>

<template>
  <Modal :title="dialogTitle" class="w-[1120px]">
    <Form
      ref="formRef"
      :model="formData"
      :rules="formRules"
      class="mx-4"
      label-width="118px"
    >
      <div class="grid grid-cols-3 gap-4">
        <Form.Item label="方案名称" name="name">
          <Input
            v-model:value="formData.name"
            :maxlength="64"
            placeholder="请输入方案名称"
          />
        </Form.Item>
        <Form.Item label="参保城市" name="areaId">
          <AreaCascader
            v-model:value="formData.areaId"
            :change-on-select="true"
            :selectable-levels="[2, 3]"
            allow-clear
            class="w-full"
            placeholder="请选择参保城市"
            @update:model-value="handleAreaChange"
          />
        </Form.Item>
        <Form.Item label="可选参保方案" name="householdType">
          <Select
            v-model:value="formData.householdType"
            :loading="standardLoading"
            :options="insuranceTypeList"
            allow-clear
            class="w-full"
            placeholder="请选择参保方案"
            @change="handleHouseTypeChange"
          />
        </Form.Item>
      </div>
      <Form.Item label="方案类型" name="type">
        <Radio.Group
          v-model:value="formData.type"
          button-style="solid"
          option-type="button"
        >
          <Radio.Button :value="HrmInsuranceSchemeType.PROPORTION">
            设置参保基数和比例
          </Radio.Button>
          <Radio.Button :value="HrmInsuranceSchemeType.AMOUNT">
            仅设置参保金额
          </Radio.Button>
        </Radio.Group>
      </Form.Item>
      <Alert
        class="mb-4"
        message="比例模式：公司或个人缴纳金额 = 参保基数 × 对应比例；金额模式直接填写公司和个人缴纳金额。"
        show-icon
        type="info"
      />

      <Form.Item name="projectList" label-width="0">
        <div
          v-for="section in projectSections"
          :key="section.key"
          :class="section.key === 'social' ? '' : 'mt-5'"
          class="w-full"
        >
          <div class="mb-3 flex items-center justify-between">
            <div class="flex items-center text-base font-semibold">
              <span class="bg-primary mr-2.5 h-[18px] w-1 rounded-sm"></span>
              {{ section.label }}
            </div>
            <Dropdown :trigger="['click']">
              <Button>
                <span class="mr-1">+</span>
                添加项目
              </Button>
              <template #overlay>
                <Menu>
                  <Menu.Item
                    v-for="option in section.options"
                    :key="option.value"
                    @click.stop
                  >
                    <Checkbox
                      :checked="isProjectTypeUsed(option.value)"
                      @change="
                        (event) =>
                          handleProjectChecked(
                            event.target.checked,
                            option.value,
                          )
                      "
                    >
                      {{ option.label }}
                    </Checkbox>
                  </Menu.Item>
                  <Menu.Divider />
                  <Menu.Item @click="addCustomProject(section.customType)">
                    + 其他
                  </Menu.Item>
                </Menu>
              </template>
            </Dropdown>
          </div>
          <Table
            :data-source="section.projects"
            :pagination="false"
            bordered
            size="small"
          >
            <Table.Column key="name" title="项目名称" :min-width="150">
              <template #default="{ record }">
                <Input
                  v-if="isCustomProject(record.type)"
                  v-model:value="record.name"
                  :maxlength="64"
                  placeholder="请输入项目名称"
                />
                <span v-else>{{ formatHrmInsuranceProjectName(record) }}</span>
              </template>
            </Table.Column>
            <Table.Column key="baseAmount" title="默认基数" width="140">
              <template #default="{ record }">
                <InputNumber
                  v-model:value="record.baseAmount"
                  :controls="false"
                  :min="0"
                  :precision="2"
                  class="w-full"
                />
              </template>
            </Table.Column>
            <template
              v-if="formData.type === HrmInsuranceSchemeType.PROPORTION"
            >
              <Table.Column
                key="corporateRate"
                title="公司缴纳比例"
                width="140"
              >
                <template #default="{ record }">
                  <InputNumber
                    v-model:value="record.corporateRate"
                    :controls="false"
                    :min="0"
                    :precision="2"
                    addon-after="%"
                    class="w-full"
                  />
                </template>
              </Table.Column>
              <Table.Column key="personalRate" title="个人缴纳比例" width="140">
                <template #default="{ record }">
                  <InputNumber
                    v-model:value="record.personalRate"
                    :controls="false"
                    :min="0"
                    :precision="2"
                    addon-after="%"
                    class="w-full"
                  />
                </template>
              </Table.Column>
            </template>
            <Table.Column key="corporateAmount" title="公司金额" width="140">
              <template #default="{ record }">
                <InputNumber
                  v-if="formData.type === HrmInsuranceSchemeType.AMOUNT"
                  v-model:value="record.corporateAmount"
                  :controls="false"
                  :min="0"
                  :precision="2"
                  class="w-full"
                />
                <span v-else>{{
                  formatHrmMoney(calculateAmount(record, 'corporate'))
                }}</span>
              </template>
            </Table.Column>
            <Table.Column key="personalAmount" title="个人金额" width="140">
              <template #default="{ record }">
                <InputNumber
                  v-if="formData.type === HrmInsuranceSchemeType.AMOUNT"
                  v-model:value="record.personalAmount"
                  :controls="false"
                  :min="0"
                  :precision="2"
                  class="w-full"
                />
                <span v-else>{{
                  formatHrmMoney(calculateAmount(record, 'personal'))
                }}</span>
              </template>
            </Table.Column>
            <Table.Column key="actions" align="center" title="操作" width="80">
              <template #default="{ record }">
                <Button danger type="link" @click="removeProject(record)">
                  删除
                </Button>
              </template>
            </Table.Column>
            <template #summary>
              <Table.Summary fixed>
                <Table.Summary.Row>
                  <Table.Summary.Cell index="0">总计</Table.Summary.Cell>
                  <Table.Summary.Cell index="1" />
                  <Table.Summary.Cell
                    v-if="formData.type === HrmInsuranceSchemeType.PROPORTION"
                    index="2"
                  />
                  <Table.Summary.Cell
                    v-if="formData.type === HrmInsuranceSchemeType.PROPORTION"
                    index="3"
                  />
                  <Table.Summary.Cell index="4">
                    {{ getSectionSummary(section.projects, 'corporateAmount') }}
                  </Table.Summary.Cell>
                  <Table.Summary.Cell index="5">
                    {{ getSectionSummary(section.projects, 'personalAmount') }}
                  </Table.Summary.Cell>
                  <Table.Summary.Cell index="6" />
                </Table.Summary.Row>
              </Table.Summary>
            </template>
          </Table>
        </div>
      </Form.Item>
    </Form>
  </Modal>
</template>
