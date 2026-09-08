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
    { field: 'name', title: '职位类型', minWidth: 240, treeNode: true },
    { field: 'parentId', title: '父级编号', width: 120 },
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
          const tree = handleTree(
            await getRecruitPostTypeList(),
          ) as PostTypeTreeNode[];
          disableCurrentSubtree(tree, getCurrentId());
          return tree;
        },
        labelField: 'name',
        valueField: 'id',
        childrenField: 'children',
        placeholder: '请选择父级类型（不选为顶级）',
        allowClear: true,
        treeDefaultExpandAll: true,
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
