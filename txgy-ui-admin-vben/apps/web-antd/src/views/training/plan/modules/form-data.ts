import type { VbenFormSchema } from '#/adapter/form';

import { handleTree } from '@vben/utils';

import { getSimpleDeptList } from '#/api/system/dept';

import { CLASS_TYPE_OPTIONS } from '../data';
import { loadPostOptions } from '../post-options';

/** 常用岗位（定岗-四定之一，也可手工输入）
 *  数据来源：云培训学院-岗位工种表.xlsx（56 项，按序列 OP/TC/QC/MG/SP 排序）
 *  修改时间：2026-09-14
 */
export const POST_OPTIONS = [
  // ===== 操作序列 OP =====
  '表面处理工（喷砂/抛丸）',
  '防腐涂装工',
  '储罐防腐工',
  '3PE/粉末防腐工',
  '补口补伤工',
  '内涂施工工',
  '特种防腐工（玻璃钢/陶瓷）',
  '保温工',
  '保冷工（绝热施工）',
  '铁皮加工安装工',
  '冬防保温工',
  '内衬施工工',
  '油管修复工',
  '油杆修复工',
  '摩擦焊操作工',
  '管道修复补强工',
  '套管修复工',
  '装备钻头修复工',
  '井口作业防腐工',
  '防蜡防垢作业工',
  '管线安装工',
  '起重吊装指挥/司索',
  '电焊工',
  '电工（维修电工）',
  '架子工',
  // ===== 技术序列 TC =====
  '防腐工艺技术员',
  '保温绝热技术员',
  '内衬修复技术员',
  '材料工程师',
  '焊接工艺技术员',
  '标准与资料员',
  // ===== 质检检测序列 QC =====
  '防腐涂层检验员',
  '保温工程检验员',
  '超声检测员（UT）',
  '射线检测员（RT）',
  '磁粉/渗透检测员（MT/PT）',
  '涡流/漏磁检测员',
  '管材管件检测员',
  '压力容器/管道检验员',
  '储罐检验员',
  '电气防雷防爆检测员',
  '气密性与气体检测员',
  '理化试验员',
  '计量检定员',
  '设施设备检测员',
  // ===== 管理序列 MG =====
  '班组长',
  '工段长/施工负责人',
  '项目经理',
  '生产调度',
  '培训师/内部讲师',
  // ===== 支持序列 SP =====
  'HSE安全员',
  '设备管理员',
  '材料采购员',
  '仓储管理员',
  '数字化/MES专员',
  '商务与投标专员',
];

/**
 * 培训班表单 schema
 * @param courseOptions 上架课程下拉（加载自 /training/course/page status=3）
 */
export function useFormSchema(
  courseOptions: { label: string; value: number }[],
): VbenFormSchema[] {
  return [
    {
      fieldName: 'name',
      label: '培训班名称',
      component: 'Input',
      rules: 'required',
      componentProps: {
        placeholder: '请输入培训班名称，如：2026 年管道工岗前培训（一期）',
        maxlength: 200,
      },
    },
    {
      fieldName: 'type',
      label: '培训类型',
      component: 'Select',
      rules: 'required',
      componentProps: {
        placeholder: '请选择培训类型',
        options: CLASS_TYPE_OPTIONS,
      },
    },
    {
      fieldName: 'projectId',
      label: '工程项目编号',
      component: 'Input',
      componentProps: {
        placeholder: '关联项目编号（岗前/复工培训用于上岗资格核查）',
        maxlength: 64,
      },
    },
    {
      fieldName: 'projectName',
      label: '工程项目名称',
      component: 'Input',
      componentProps: {
        placeholder: '关联项目名称，可空',
        maxlength: 200,
      },
    },
    {
      fieldName: 'courseIds',
      label: '绑定课程(定课)',
      component: 'Select',
      componentProps: {
        mode: 'multiple',
        placeholder: '仅可选择已上架课程，开班后不可调整',
        options: courseOptions,
        optionFilterProp: 'label',
      },
    },
    {
      fieldName: 'targetPostsText',
      label: '定岗',
      component: 'Select',
      componentProps: {
        mode: 'tags',
        placeholder: '结业后岗位，可输入回车新增',
        options: POST_OPTIONS.map((item) => ({ label: item, value: item })),
      },
    },
    {
      fieldName: 'startTime',
      label: '开班时间',
      component: 'DatePicker',
      componentProps: {
        showTime: true,
        format: 'YYYY-MM-DD HH:mm:ss',
        valueFormat: 'x',
        placeholder: '选择开班时间',
      },
    },
    {
      fieldName: 'deadline',
      label: '培训截止时间',
      component: 'DatePicker',
      rules: 'required',
      componentProps: {
        showTime: true,
        format: 'YYYY-MM-DD HH:mm:ss',
        valueFormat: 'x',
        placeholder: '选择培训截止时间（开班必须设置）',
      },
    },
    {
      fieldName: 'scopeDeptIds',
      label: '参训部门',
      component: 'ApiTreeSelect',
      componentProps: {
        allowClear: true,
        api: async () => handleTree(await getSimpleDeptList()),
        labelField: 'name',
        valueField: 'id',
        childrenField: 'children',
        multiple: true,
        treeCheckable: true,
        showCheckedStrategy: 'SHOW_PARENT',
        treeDefaultExpandAll: true,
        placeholder: '按部门圈选参训人员（可多选）',
      },
    },
    {
      fieldName: 'scopeIncludeChildDept',
      label: '含下级部门',
      component: 'Switch',
      defaultValue: true,
      componentProps: {
        checkedChildren: '是',
        unCheckedChildren: '否',
      },
    },
    {
      fieldName: 'scopePostIds',
      label: '参训岗位',
      component: 'ApiSelect',
      componentProps: {
        allowClear: true,
        // 培训业务岗位工种（已同步为系统岗位）+ 其他系统岗位，支持名称/编码搜索
        api: loadPostOptions,
        mode: 'multiple',
        optionFilterProp: 'label',
        placeholder: '按岗位圈选参训人员（可多选，支持岗位名称/编码搜索）',
      },
    },
    {
      fieldName: 'remark',
      label: '备注',
      component: 'InputTextArea',
      componentProps: {
        placeholder: '备注，可空',
        maxlength: 500,
        rows: 2,
      },
    },
  ];
}
