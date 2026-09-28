import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { HrmRecruitPostApi } from '#/api/hrm/recruit/post';
import type { DescriptionItemSchema } from '#/components/description';

import { h, markRaw } from 'vue';

import { DICT_TYPE } from '@vben/constants';
import { formatDate, handleTree } from '@vben/utils';

import { getSimpleDeptList } from '#/api/system/dept';
import { getRecruitChannelSimpleList } from '#/api/hrm/recruit/channel';
import { getRecruitingDemandSimpleList } from '#/api/hrm/recruit/demand';
import { getRecruitPostTypeList } from '#/api/hrm/recruit/post/type';
import { AreaCascader } from '#/components/area';
import { DictTag } from '#/components/dict-tag';
import EmployeeSelect from '#/views/hrm/employee/components/employee-select.vue';
import {
  AGE_UNLIMITED_VALUE,
  HrmEmployeeEntryStatus,
  HrmRecruitJobNature,
  HrmRecruitJobNatureOptions,
  HrmRecruitPostEducation,
  HrmRecruitPostEducationOptions,
  HrmRecruitPostStatus,
  HrmRecruitSalaryUnit,
  HrmRecruitWorkTime,
  SALARY_NEGOTIABLE_UNIT_VALUE,
  SALARY_NEGOTIABLE_VALUE,
} from '#/views/hrm/utils/constants';
import {
  formatRecruitPostAge,
  formatRecruitPostProgress,
  formatRecruitPostSalary,
  formatRecruitPostSchedule,
} from '#/views/hrm/utils/format';

const optionLabel = (options: readonly { label: string; value: number }[], value: unknown) =>
  options.find((item) => item.value === Number(value))?.label ?? '-';

import AgeRangeField from './modules/age-range-field.vue';
import AddressPickerField from './modules/address-picker-field.vue';
import SalaryRangeField from './modules/salary-range-field.vue';

/** 列表搜索表单 */
export function useGridFormSchema(): VbenFormSchema[] {
  return [
    {
      fieldName: 'postName',
      label: '职位名称',
      component: 'Input',
      componentProps: {
        placeholder: '请输入职位名称',
        allowClear: true,
      },
    },
    {
      fieldName: 'jobNature',
      label: '职位类型',
      component: 'Select',
      componentProps: {
        options: HrmRecruitJobNatureOptions,
        placeholder: '请选择职位类型',
        allowClear: true,
      },
    },
    {
      fieldName: 'areaId',
      label: '工作城市',
      component: markRaw(AreaCascader),
      componentProps: {
        placeholder: '请选择工作城市',
        allowClear: true,
        showSearch: true,
      },
    },
    {
      fieldName: 'deptId',
      label: '用人部门',
      component: 'ApiTreeSelect',
      componentProps: {
        api: async () => handleTree(await getSimpleDeptList()),
        labelField: 'name',
        valueField: 'id',
        childrenField: 'children',
        placeholder: '请选择用人部门',
        allowClear: true,
        treeDefaultExpandAll: true,
      },
    },
    {
      fieldName: 'ownerEmployeeId',
      label: '招聘负责人',
      component: markRaw(EmployeeSelect),
      componentProps: {
        placeholder: '请选择招聘负责人',
        entryStatus: HrmEmployeeEntryStatus.ACTIVE,
        allowClear: true,
      },
    },
  ];
}

/** 列表字段 */
export function useGridColumns(): VxeTableGridOptions<HrmRecruitPostApi.RecruitPost>['columns'] {
  return [
    {
      field: 'postName',
      title: '职位名称',
      minWidth: 180,
      fixed: 'left',
      slots: { default: 'postName' },
    },
    { field: 'deptName', title: '用人部门', minWidth: 120 },
    {
      field: 'jobNature',
      title: '职位类型',
      width: 100,
      formatter: ({ cellValue }) => optionLabel(HrmRecruitJobNatureOptions, cellValue),
    },
    {
      field: 'areaName',
      title: '工作城市',
      minWidth: 160,
      formatter: ({ cellValue }) => cellValue || '-',
    },
    { field: 'recruitNum', title: '招聘人数', width: 100 },
    {
      field: 'hasEntryNum',
      title: '已入职人数',
      width: 110,
      formatter: ({ cellValue }) => cellValue ?? 0,
    },
    {
      field: 'recruitSchedule',
      title: '招聘进度',
      width: 100,
      formatter: ({ row }) => formatRecruitPostSchedule(row),
    },
    {
      field: 'workTime',
      title: '工作经验',
      width: 110,
      formatter: ({ cellValue }) => cellValue === 0 || cellValue === null || cellValue === undefined ? '不限' : `${cellValue} 年`,
    },
    {
      field: 'educationRequire',
      title: '学历要求',
      width: 120,
      formatter: ({ cellValue }) => optionLabel(HrmRecruitPostEducationOptions, cellValue),
    },
    {
      field: 'minSalary',
      title: '薪资范围',
      minWidth: 160,
      formatter: ({ row }) => formatRecruitPostSalary(row),
    },
    {
      field: 'minAge',
      title: '年龄要求',
      width: 110,
      formatter: ({ row }) => formatRecruitPostAge(row),
    },
    {
      field: 'latestEntryTime',
      title: '最迟到岗时间',
      width: 120,
      formatter: ({ cellValue }) =>
        cellValue ? (formatDate(cellValue) as string) : '-',
    },
    { field: 'ownerEmployeeName', title: '招聘负责人', minWidth: 120 },
    { field: 'postTypeName', title: '职位类型', minWidth: 120 },
    { field: 'channelName', title: '发布渠道', minWidth: 120 },
    {
      field: 'interviewEmployeeNames',
      title: '面试官',
      minWidth: 160,
      formatter: ({ cellValue }) =>
        Array.isArray(cellValue) && cellValue.length > 0
          ? cellValue.join('、')
          : '-',
    },
    {
      field: 'status',
      title: '状态',
      width: 100,
      cellRender: {
        name: 'CellDict',
        props: { type: DICT_TYPE.HRM_RECRUIT_POST_STATUS },
      },
    },
    {
      title: '操作',
      width: 160,
      fixed: 'right',
      slots: { default: 'actions' },
    },
  ];
}

/** 新增/修改表单 */
export function useFormSchema(): VbenFormSchema[] {
  return [
    {
      fieldName: 'id',
      component: 'Input',
      dependencies: {
        triggerFields: [''],
        show: () => false,
      },
    },
    {
      fieldName: 'postName',
      label: '职位名称',
      component: 'Input',
      rules: 'required',
      componentProps: {
        placeholder: '请输入职位名称',
        maxlength: 255,
        allowClear: true,
      },
    },
    {
      fieldName: 'deptId',
      label: '用人部门',
      component: 'ApiTreeSelect',
      componentProps: {
        api: async () => handleTree(await getSimpleDeptList()),
        labelField: 'name',
        valueField: 'id',
        childrenField: 'children',
        placeholder: '请选择用人部门',
        allowClear: true,
        treeDefaultExpandAll: true,
      },
    },
    {
      fieldName: 'jobNature',
      label: '职位类型',
      component: 'Select',
      rules: 'required',
      defaultValue: HrmRecruitJobNature.FULL_TIME,
      componentProps: {
        options: HrmRecruitJobNatureOptions,
        placeholder: '请选择职位类型',
        allowClear: true,
      },
    },
    {
      fieldName: 'postTypeId',
      label: '职位分类',
      component: 'ApiTreeSelect',
      componentProps: {
        api: async () => handleTree(await getRecruitPostTypeList()),
        labelField: 'name',
        valueField: 'id',
        childrenField: 'children',
        placeholder: '请选择职位分类',
        allowClear: true,
        treeDefaultExpandAll: false,
      },
    },
    {
      fieldName: 'channelId',
      label: '发布渠道',
      component: 'ApiSelect',
      rules: 'required',
      componentProps: {
        api: getRecruitChannelSimpleList,
        labelField: 'name',
        valueField: 'id',
        placeholder: '请选择发布渠道',
        allowClear: true,
      },
    },
    {
      fieldName: 'demandId',
      label: '关联用工需求',
      component: 'ApiSelect',
      componentProps: {
        api: getRecruitingDemandSimpleList,
        labelField: 'demandName',
        valueField: 'id',
        placeholder: '选择用工需求单（录用后自动回写到岗人数）',
        allowClear: true,
        showSearch: true,
      },
      help: '关联后：候选人转员工时自动把需求单的「已到岗人数」+1，到齐自动标记需求完成',
    },
    {
      fieldName: 'areaId',
      label: '工作城市',
      component: markRaw(AreaCascader),
      componentProps: {
        placeholder: '请选择工作城市',
        allowClear: true,
        showSearch: true,
      },
    },
    {
      fieldName: 'workAddress',
      label: '详细工作地址',
      component: markRaw(AddressPickerField),
      formItemClass: 'col-span-2',
      componentProps: (values, formApi) => ({ values, formApi }),
    },
    {
      fieldName: 'longitude',
      component: 'InputNumber',
      dependencies: { triggerFields: [''], show: () => false },
    },
    {
      fieldName: 'latitude',
      component: 'InputNumber',
      dependencies: { triggerFields: [''], show: () => false },
    },
    {
      fieldName: 'recruitNum',
      label: '招聘人数',
      component: 'InputNumber',
      componentProps: {
        placeholder: '请输入招聘人数',
        min: 0,
        class: 'w-full',
      },
    },
    {
      fieldName: 'reason',
      label: '招聘原因',
      component: 'Input',
      componentProps: {
        placeholder: '请输入招聘原因',
        maxlength: 255,
        allowClear: true,
      },
    },
    {
      fieldName: 'workTime',
      label: '工作年限',
      component: 'InputNumber',
      defaultValue: HrmRecruitWorkTime.UNLIMITED - 1,
      componentProps: {
        min: 0,
        max: 99,
        precision: 0,
        placeholder: '请输入工作年限（年）',
        allowClear: true,
        class: 'w-full',
      },
    },
    {
      fieldName: 'educationRequire',
      label: '学历要求',
      component: 'Select',
      defaultValue: HrmRecruitPostEducation.UNLIMITED,
      componentProps: {
        options: HrmRecruitPostEducationOptions,
        placeholder: '请选择学历要求',
        allowClear: true,
      },
    },
    // 薪资范围：视觉上合并为一项；关联字段隐藏但保留 fieldName，供提交映射使用
    {
      fieldName: 'minSalary',
      label: '薪资范围',
      component: markRaw(SalaryRangeField),
      formItemClass: 'items-start',
      description: '最低薪资不能大于最高薪资；勾选“面议”后无需填写范围。',
      componentProps: (values, formApi) => ({
        values,
        formApi,
      }),
    },
    {
      fieldName: 'maxSalary',
      component: 'InputNumber',
      dependencies: { triggerFields: [''], show: () => false },
    },
    {
      fieldName: 'salaryUnit',
      component: 'Select',
      defaultValue: HrmRecruitSalaryUnit.MONTH,
      dependencies: { triggerFields: [''], show: () => false },
    },
    {
      fieldName: 'salaryNegotiable',
      component: 'Checkbox',
      defaultValue: false,
      dependencies: { triggerFields: [''], show: () => false },
    },
    {
      fieldName: 'latestEntryTime',
      label: '最迟到岗时间',
      component: 'DatePicker',
      componentProps: {
        showTime: true,
        valueFormat: 'x',
        format: 'YYYY-MM-DD HH:mm:ss',
        placeholder: '请选择时间',
        class: 'w-full',
        allowClear: true,
      },
    },
    // 年龄要求：视觉上合并为一项；关联字段隐藏但保留 fieldName
    {
      fieldName: 'minAge',
      label: '年龄要求',
      component: markRaw(AgeRangeField),
      formItemClass: 'items-start',
      description: '最小年龄不能大于最大年龄；勾选“不限”后无需填写范围。',
      componentProps: (values, formApi) => ({
        values,
        formApi,
      }),
    },
    {
      fieldName: 'maxAge',
      component: 'InputNumber',
      dependencies: { triggerFields: [''], show: () => false },
    },
    {
      fieldName: 'ageUnlimited',
      component: 'Checkbox',
      defaultValue: false,
      dependencies: { triggerFields: [''], show: () => false },
    },
    {
      fieldName: 'ownerEmployeeId',
      label: '招聘负责人',
      component: markRaw(EmployeeSelect),
      componentProps: {
        placeholder: '请选择招聘负责人',
        entryStatus: HrmEmployeeEntryStatus.ACTIVE,
        allowClear: true,
      },
    },
    {
      fieldName: 'interviewEmployeeIds',
      label: '面试官',
      component: markRaw(EmployeeSelect),
      defaultValue: [],
      componentProps: {
        placeholder: '请选择面试官',
        entryStatus: HrmEmployeeEntryStatus.ACTIVE,
        multiple: true,
        allowClear: true,
      },
    },
    {
      fieldName: 'description',
      label: '职位描述',
      component: 'Textarea',
      formItemClass: 'col-span-2',
      componentProps: {
        placeholder: '请输入职位描述',
        maxlength: 4000,
        rows: 4,
        showCount: true,
        allowClear: true,
      },
    },
  ];
}

/** 职位状态变更表单 */
export function useStopFormSchema(): VbenFormSchema[] {
  return [
    {
      fieldName: 'stopReason',
      label: '状态变更原因',
      component: 'Textarea',
      rules: 'required',
      componentProps: {
        placeholder: '请输入状态变更原因',
        maxlength: 255,
        rows: 3,
        showCount: true,
        allowClear: true,
      },
    },
  ];
}

/** 详情页头部字段 */
export function useHeaderSchema(): DescriptionItemSchema[] {
  return [
    { field: 'deptName', label: '用人部门' },
    {
      field: 'jobNature',
      label: '职位类型',
      render: (val) =>
        val === undefined || val === null
          ? '-'
          : optionLabel(HrmRecruitJobNatureOptions, val),
    },
    { field: 'areaName', label: '工作城市' },
    { field: 'ownerEmployeeName', label: '招聘负责人' },
    {
      field: 'recruitSchedule',
      label: '招聘进度',
      render: (_val, data) =>
        formatRecruitPostProgress(data as HrmRecruitPostApi.RecruitPost),
    },
  ];
}

/** 详情基本信息 */
export function useBasicInfoSchema(): DescriptionItemSchema[] {
  return [
    { field: 'postName', label: '职位名称' },
    { field: 'deptName', label: '用人部门' },
    {
      field: 'jobNature',
      label: '职位类型',
      render: (val) =>
        val === undefined || val === null
          ? '-'
          : optionLabel(HrmRecruitJobNatureOptions, val),
    },
    { field: 'areaName', label: '工作城市' },
    {
      field: 'recruitNum',
      label: '招聘人数',
      render: (val) => (val === undefined || val === null ? '-' : String(val)),
    },
    {
      field: 'hasEntryNum',
      label: '已入职人数',
      render: (val) => String(val ?? 0),
    },
    { field: 'reason', label: '招聘原因', span: 2 },
    {
      field: 'workTime',
      label: '工作年限',
      render: (val) =>
        val === undefined || val === null
          ? '-'
          : val === 0 ? '不限' : `${val} 年`,
    },
    {
      field: 'educationRequire',
      label: '学历要求',
      render: (val) =>
        val === undefined || val === null
          ? '-'
          : optionLabel(HrmRecruitPostEducationOptions, val),
    },
    {
      field: 'minSalary',
      label: '薪资范围',
      render: (_val, data) =>
        formatRecruitPostSalary(data as HrmRecruitPostApi.RecruitPost),
    },
    {
      field: 'minAge',
      label: '年龄要求',
      render: (_val, data) =>
        formatRecruitPostAge(data as HrmRecruitPostApi.RecruitPost),
    },
    {
      field: 'latestEntryTime',
      label: '最迟到岗时间',
      render: (val) => (val ? (formatDate(val) as string) : '-'),
    },
    { field: 'ownerEmployeeName', label: '招聘负责人' },
    { field: 'postTypeName', label: '职位类型' },
    { field: 'channelName', label: '发布渠道' },
    {
      field: 'interviewEmployeeNames',
      label: '面试官',
      span: 2,
      render: (val) =>
        Array.isArray(val) && val.length > 0 ? val.join('、') : '-',
    },
    {
      field: 'status',
      label: '状态',
      render: (val) =>
        val === undefined || val === null
          ? '-'
          : h(DictTag, { type: DICT_TYPE.HRM_RECRUIT_POST_STATUS, value: val }),
    },
    {
      field: 'stopReason',
      label: '状态变更原因',
      span: 2,
      show: (values) =>
        values.status === HrmRecruitPostStatus.PAUSED ||
        values.status === HrmRecruitPostStatus.CLOSED,
    },
    {
      field: 'createTime',
      label: '创建时间',
      render: (val) => (val ? (formatDate(val) as string) : '-'),
    },
  ];
}

export {
  AGE_UNLIMITED_VALUE,
  HrmRecruitPostStatus,
  HrmRecruitSalaryUnit,
  SALARY_NEGOTIABLE_UNIT_VALUE,
  SALARY_NEGOTIABLE_VALUE,
};
