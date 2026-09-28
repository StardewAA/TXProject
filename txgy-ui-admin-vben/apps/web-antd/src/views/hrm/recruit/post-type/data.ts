import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { HrmRecruitPostTypeApi } from '#/api/hrm/recruit/post/type';

import { CommonStatusEnum } from '@vben/constants';
import { handleTree } from '@vben/utils';

import { getRecruitPostTypeList } from '#/api/hrm/recruit/post/type';

type PostTypeTreeNode = HrmRecruitPostTypeApi.RecruitPostType & {
  children?: PostTypeTreeNode[];
  disabled?: boolean;
};

// 新增职位类型只展示 HRM 招聘标准目录，历史系统通用职位分类不混入这棵树。
export const STANDARD_ROOT_NAMES = new Set([
  '施工技能类（一线操作岗）',
  '工程技术类',
  '项目管理类',
  '质量检验类',
  '生产制造类',
  '采购与供应链类',
  '市场营销类',
  '职能管理类',
  '国际业务类',
  '培训生/储备类',
]);

function disableSubtree(node: PostTypeTreeNode) {
  node.disabled = true;
  node.children?.forEach(disableSubtree);
}

function disableCurrentSubtree(
  nodes: PostTypeTreeNode[],
  currentId?: number,
) {
  if (!currentId) return;
  nodes.forEach((node) => {
    if (node.id === currentId) {
      disableSubtree(node);
      return;
    }
    if (node.children?.length) {
      disableCurrentSubtree(node.children, currentId);
    }
  });
}

export function useGridColumns(
  onStatusChange: (
    status: number,
    row: HrmRecruitPostTypeApi.RecruitPostType,
  ) => Promise<boolean | undefined>,
): VxeTableGridOptions<HrmRecruitPostTypeApi.RecruitPostType>['columns'] {
  return [
    { field: 'name', title: '职位类型', minWidth: 300, treeNode: true },
    { field: 'levelLabel', title: '层级', width: 110 },
    { field: 'parentLabel', title: '所属分类', minWidth: 220 },
    { field: 'sort', title: '排序', width: 90 },
    {
      field: 'status',
      title: '状态',
      width: 100,
      align: 'center',
      cellRender: {
        name: 'CellSwitch',
        attrs: { beforeChange: onStatusChange },
        props: {
          checkedValue: CommonStatusEnum.ENABLE,
          unCheckedValue: CommonStatusEnum.DISABLE,
        },
      },
    },
    { field: 'createTime', title: '创建时间', width: 180, formatter: 'formatDateTime' },
    { title: '操作', width: 180, fixed: 'right', slots: { default: 'actions' } },
  ];
}

export function useFormSchema(
  getCurrentId: () => number | undefined = () => undefined,
): VbenFormSchema[] {
  return [
    { fieldName: 'id', component: 'Input', dependencies: { triggerFields: [''], show: () => false } },
    {
      fieldName: 'name',
      label: '类型名称',
      component: 'Input',
      rules: 'required',
      componentProps: { placeholder: '请输入职位类型名称', maxlength: 255, allowClear: true },
    },
    {
      fieldName: 'parentId',
      label: '父级类型',
      component: 'ApiTreeSelect',
      componentProps: {
        api: async () => {
          const tree = handleTree(await getRecruitPostTypeList()) as PostTypeTreeNode[];
          // 保持“一级分类 -> 二级分类 -> 具体职位”的层级，隐藏旧的非 HRM 根节点。
          const standardTree = tree.filter((node) => STANDARD_ROOT_NAMES.has(node.name));
          disableCurrentSubtree(standardTree, getCurrentId());
          return standardTree;
        },
        labelField: 'name',
        valueField: 'id',
        childrenField: 'children',
        placeholder: '请选择父级类型（不选为顶级）',
        allowClear: true,
        // 首次打开只显示一级分类，用户逐级展开到二级分类和具体职位。
        treeDefaultExpandAll: false,
      },
    },
    {
      fieldName: 'sort',
      label: '显示顺序',
      component: 'InputNumber',
      componentProps: { min: 0, class: 'w-full', placeholder: '请输入显示顺序' },
    },
  ];
}
