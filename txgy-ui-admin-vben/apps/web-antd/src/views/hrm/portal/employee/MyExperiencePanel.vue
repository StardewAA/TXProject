<script lang="ts" setup>
import type { HrmEmployeeEducationExperienceApi } from '#/api/hrm/employee/education-experience';
import type { HrmEmployeeWorkExperienceApi } from '#/api/hrm/employee/work-experience';

import { computed, onMounted, ref, watch } from 'vue';

import { useAccess } from '@vben/access';
import { confirm } from '@vben/common-ui';
import { formatDate } from '@vben/utils';

import {
  Button,
  Card,
  Checkbox,
  DatePicker,
  Empty,
  Form,
  FormItem,
  Input,
  message,
  Modal,
  Select,
  Space,
  Spin,
  Table,
  Tag,
} from 'ant-design-vue';
import dayjs from 'dayjs';

import {
  createMyEducationExperience,
  deleteMyEducationExperience,
  getEmployeeEducationExperienceList,
  updateMyEducationExperience,
} from '#/api/hrm/portal/employee/education-experience';
import {
  createMyWorkExperience,
  deleteMyWorkExperience,
  getEmployeeWorkExperienceList,
  updateMyWorkExperience,
} from '#/api/hrm/portal/employee/work-experience';
import {
  HrmEmployeeEducation,
} from '#/views/hrm/utils/constants';

/**
 * 员工端「我的档案」—— 教育经历 / 工作经历 自助维护
 *
 * 员工自己补录后，写的就是后台同一条员工记录（HR 立即能看到）；
 * 后端还会给直属上级发复核提醒，并在档案备注留痕。
 */
defineOptions({ name: 'HrmPortalMyExperiencePanel' });

const props = defineProps<{ type: 'education' | 'work' }>();

const { hasAccessByCodes } = useAccess();
const canEdit = hasAccessByCodes(['hrm:portal:employee:update']);

const loading = ref(false);
const saving = ref(false);
const modalOpen = ref(false);
const editingId = ref<number>();
const educationList = ref<HrmEmployeeEducationExperienceApi.EmployeeEducationExperience[]>([]);
const workList = ref<HrmEmployeeWorkExperienceApi.EmployeeWorkExperience[]>([]);
const formRef = ref();

const isEducation = computed(() => props.type === 'education');
const title = computed(() => (isEducation.value ? '教育经历' : '工作经历'));

const educationOptions = [
  { label: '初中', value: HrmEmployeeEducation.JUNIOR_HIGH_SCHOOL },
  { label: '中专', value: HrmEmployeeEducation.TECHNICAL_SECONDARY_SCHOOL },
  { label: '中职', value: HrmEmployeeEducation.SECONDARY_VOCATIONAL_SCHOOL },
  { label: '技校', value: HrmEmployeeEducation.TECHNICAL_SCHOOL },
  { label: '高中', value: HrmEmployeeEducation.HIGH_SCHOOL },
  { label: '大专', value: HrmEmployeeEducation.COLLEGE },
  { label: '本科', value: HrmEmployeeEducation.BACHELOR },
  { label: '硕士', value: HrmEmployeeEducation.MASTER },
  { label: '博士', value: HrmEmployeeEducation.DOCTOR },
  { label: '其他', value: HrmEmployeeEducation.OTHER },
];

const teachingOptions = [
  { label: '全日制', value: 1 },
  { label: '成人教育', value: 2 },
  { label: '远程教育', value: 3 },
  { label: '自学考试', value: 4 },
  { label: '其他', value: 5 },
];

const educationForm = ref<HrmEmployeeEducationExperienceApi.EmployeeEducationExperience>({});
const workForm = ref<HrmEmployeeWorkExperienceApi.EmployeeWorkExperience>({});

const educationColumns = [
  { dataIndex: 'education', title: '学历', width: 90, customRender: ({ text }: any) =>
      educationOptions.find((item) => item.value === text)?.label || '-' },
  { dataIndex: 'graduateSchool', title: '毕业院校' },
  { dataIndex: 'major', title: '专业' },
  { dataIndex: 'admissionTime', title: '入学', width: 120,
    customRender: ({ text }: any) => (text ? formatDate(Number(text)) : '-') },
  { dataIndex: 'graduationTime', title: '毕业', width: 120,
    customRender: ({ text }: any) => (text ? formatDate(Number(text)) : '-') },
  { dataIndex: 'firstDegree', title: '第一学历', width: 100,
    customRender: ({ text }: any) => (text ? '是' : '否') },
  { dataIndex: 'action', title: '操作', width: 140, fixed: 'right' as const },
];

const workColumns = [
  { dataIndex: 'workUnit', title: '工作单位' },
  { dataIndex: 'postName', title: '职务', width: 140 },
  { dataIndex: 'startTime', title: '开始', width: 120,
    customRender: ({ text }: any) => (text ? formatDate(Number(text)) : '-') },
  { dataIndex: 'endTime', title: '结束', width: 120,
    customRender: ({ text }: any) => (text ? formatDate(Number(text)) : '-') },
  { dataIndex: 'remark', title: '工作内容' },
  { dataIndex: 'action', title: '操作', width: 140, fixed: 'right' as const },
];

async function load() {
  loading.value = true;
  try {
    if (isEducation.value) {
      educationList.value = (await getEmployeeEducationExperienceList()) ?? [];
    } else {
      workList.value = (await getEmployeeWorkExperienceList()) ?? [];
    }
  } finally {
    loading.value = false;
  }
}

function openCreate() {
  editingId.value = undefined;
  educationForm.value = { firstDegree: educationList.value.length === 0 };
  workForm.value = {};
  modalOpen.value = true;
}

function openEdit(row: any) {
  editingId.value = row.id;
  if (isEducation.value) {
    educationForm.value = { ...row };
  } else {
    workForm.value = { ...row };
  }
  modalOpen.value = true;
}

async function handleRemove(row: any) {
  await confirm(`确认删除这条${title.value}吗？`);
  if (isEducation.value) {
    await deleteMyEducationExperience(row.id);
  } else {
    await deleteMyWorkExperience(row.id);
  }
  message.success('已删除');
  await load();
}

async function handleSave() {
  try {
    await formRef.value?.validate();
  } catch {
    return;
  }
  saving.value = true;
  try {
    if (isEducation.value) {
      const payload = { ...educationForm.value, id: editingId.value };
      await (editingId.value
        ? updateMyEducationExperience(payload)
        : createMyEducationExperience(payload));
    } else {
      const payload = { ...workForm.value, id: editingId.value };
      await (editingId.value
        ? updateMyWorkExperience(payload)
        : createMyWorkExperience(payload));
    }
    message.success('已保存，HR 在后台即可看到（并会收到复核提醒）');
    modalOpen.value = false;
    await load();
  } finally {
    saving.value = false;
  }
}

onMounted(load);
watch(() => props.type, load);
</script>

<template>
  <Card size="small">
    <div class="mb-3 flex items-center justify-between">
      <Space>
        <span class="font-medium">我的{{ title }}</span>
        <Tag v-if="!canEdit" color="default">由 HR 维护</Tag>
      </Space>
      <Button v-if="canEdit" type="primary" @click="openCreate">新增{{ title }}</Button>
    </div>

    <Spin :spinning="loading">
      <template v-if="isEducation ? educationList.length : workList.length">
        <Table
          :columns="isEducation ? educationColumns : workColumns"
          :data-source="isEducation ? educationList : workList"
          :pagination="false"
          :row-key="(row: any) => row.id"
          :scroll="{ x: 900 }"
          size="small"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.dataIndex === 'action'">
              <Button v-if="canEdit" size="small" type="link" @click="openEdit(record)">
                编辑
              </Button>
              <Button v-if="canEdit" danger size="small" type="link" @click="handleRemove(record)">
                删除
              </Button>
              <span v-if="!canEdit" class="text-muted-foreground">-</span>
            </template>
          </template>
        </Table>
      </template>
      <Empty v-else :description="`暂无${title}，可点右上角新增`" />
    </Spin>

    <Modal
      v-model:open="modalOpen"
      :confirm-loading="saving"
      :title="editingId ? `编辑${title}` : `新增${title}`"
      @ok="handleSave"
    >
      <Form ref="formRef" :model="isEducation ? educationForm : workForm" layout="vertical">
        <template v-if="isEducation">
          <FormItem
            label="学历"
            name="education"
            :rules="[{ required: true, message: '请选择学历' }]"
          >
            <Select v-model:value="educationForm.education" :options="educationOptions" />
          </FormItem>
          <FormItem
            label="毕业院校"
            name="graduateSchool"
            :rules="[{ required: true, message: '请填写毕业院校' }]"
          >
            <Input v-model:value="educationForm.graduateSchool" />
          </FormItem>
          <FormItem label="专业" name="major" :rules="[{ required: true, message: '请填写专业' }]">
            <Input v-model:value="educationForm.major" />
          </FormItem>
          <Space class="w-full" align="start">
            <FormItem label="入学日期" name="admissionTime">
              <DatePicker
                :value="educationForm.admissionTime ? dayjs(Number(educationForm.admissionTime)) : undefined"
                value-format="x"
                @update:value="(value: any) => (educationForm.admissionTime = value)"
              />
            </FormItem>
            <FormItem label="毕业日期" name="graduationTime">
              <DatePicker
                :value="educationForm.graduationTime ? dayjs(Number(educationForm.graduationTime)) : undefined"
                value-format="x"
                @update:value="(value: any) => (educationForm.graduationTime = value)"
              />
            </FormItem>
          </Space>
          <FormItem label="教学方式" name="teachingMethods">
            <Select
              v-model:value="educationForm.teachingMethods"
              :options="teachingOptions"
              placeholder="请选择"
            />
          </FormItem>
          <FormItem name="firstDegree">
            <Checkbox v-model:checked="educationForm.firstDegree">第一学历</Checkbox>
          </FormItem>
        </template>

        <template v-else>
          <FormItem
            label="工作单位"
            name="workUnit"
            :rules="[{ required: true, message: '请填写工作单位' }]"
          >
            <Input v-model:value="workForm.workUnit" />
          </FormItem>
          <FormItem label="职务" name="postName">
            <Input v-model:value="workForm.postName" />
          </FormItem>
          <Space class="w-full" align="start">
            <FormItem label="开始时间" name="startTime">
              <DatePicker
                :value="workForm.startTime ? dayjs(Number(workForm.startTime)) : undefined"
                value-format="x"
                @update:value="(value: any) => (workForm.startTime = value)"
              />
            </FormItem>
            <FormItem label="结束时间" name="endTime">
              <DatePicker
                :value="workForm.endTime ? dayjs(Number(workForm.endTime)) : undefined"
                value-format="x"
                @update:value="(value: any) => (workForm.endTime = value)"
              />
            </FormItem>
          </Space>
          <FormItem label="工作内容" name="remark">
            <Input.TextArea v-model:value="workForm.remark" :maxlength="300" :rows="3" show-count />
          </FormItem>
        </template>
      </Form>
    </Modal>
  </Card>
</template>
