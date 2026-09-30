<script lang="ts" setup>
import type { SystemMenuApi } from '#/api/system/menu';
import type { SystemTenantPackageApi } from '#/api/system/tenant-package';

import { computed, ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';
import { handleTree } from '@vben/utils';

import { Checkbox, message, Spin, Tree as AntTree } from 'ant-design-vue';

import { useVbenForm } from '#/adapter/form';
import { getSimpleMenusList } from '#/api/system/menu';
import {
  createTenantPackage,
  getTenantPackage,
  updateTenantPackage,
} from '#/api/system/tenant-package';
import { $t } from '#/locales';

import { useFormSchema } from '../data';

const emit = defineEmits(['success']);
const formData = ref<SystemTenantPackageApi.TenantPackage>();
const getTitle = computed(() => {
  return formData.value
    ? $t('ui.actionTitle.edit', ['套餐'])
    : $t('ui.actionTitle.create', ['套餐']);
});
const menuTree = ref<SystemMenuApi.Menu[]>([]); // 菜单树
const menuLoading = ref(false); // 加载菜单列表
const isAllSelected = ref(false); // 全选状态
const isExpanded = ref(false); // 展开状态
const expandedKeys = ref<number[]>([]); // 展开的节点
// 使用 antd Tree（height 开启虚拟滚动），勾选状态单独维护
const checkedKeys = ref<number[]>([]);
const halfCheckedKeys = ref<number[]>([]);
/** 记录所有父节点 id，用于从完整 menuIds 中过滤出叶子节点 */
const parentIdSet = computed(() => {
  const ids = new Set<number>();
  const walk = (nodes: SystemMenuApi.Menu[]) => {
    nodes.forEach((node) => {
      if (node.children && node.children.length > 0) {
        ids.add(node.id);
        walk(node.children);
      }
    });
  };
  walk(menuTree.value);
  return ids;
});
const treeFieldNames = { children: 'children', key: 'id', title: 'name' };

const [Form, formApi] = useVbenForm({
  commonConfig: {
    componentProps: {
      class: 'w-full',
    },
    formItemClass: 'col-span-2',
    labelWidth: 80,
  },
  layout: 'horizontal',
  schema: useFormSchema(),
  showDefaultActions: false,
});

const [Modal, modalApi] = useVbenModal({
  async onConfirm() {
    const { valid } = await formApi.validate();
    if (!valid) {
      return;
    }
    modalApi.lock();
    // 提交表单：勾选 + 半选（祖先）合并为完整 menuIds
    const data =
      (await formApi.getValues()) as SystemTenantPackageApi.TenantPackage;
    data.menuIds = [...checkedKeys.value, ...halfCheckedKeys.value];
    try {
      await (formData.value
        ? updateTenantPackage(data)
        : createTenantPackage(data));
      // 关闭并提示
      await modalApi.close();
      emit('success');
      message.success($t('ui.actionMessage.operationSuccess'));
    } finally {
      modalApi.unlock();
    }
  },
  async onOpenChange(isOpen: boolean) {
    if (!isOpen) {
      formData.value = undefined;
      return;
    }
    // 加载菜单列表
    await loadMenuTree();
    // 加载数据
    const data = modalApi.getData<SystemTenantPackageApi.TenantPackage>();
    if (!data || !data.id) {
      return;
    }
    modalApi.lock();
    try {
      const detail = await getTenantPackage(data.id);
      formData.value = detail;
      // 只勾选叶子节点，antd Tree 会自动联动选中祖先节点
      const menuIds = detail.menuIds ?? [];
      checkedKeys.value = menuIds.filter((id) => !parentIdSet.value.has(id));
      halfCheckedKeys.value = [];
      await formApi.setValues({ ...detail });
    } finally {
      modalApi.unlock();
    }
  },
});

/** 加载菜单树 */
async function loadMenuTree() {
  menuLoading.value = true;
  try {
    const data = await getSimpleMenusList();
    menuTree.value = handleTree(data) as SystemMenuApi.Menu[];
  } finally {
    menuLoading.value = false;
  }
}

/** 勾选变化：记录全选与半选（祖先）节点 */
function handleCheck(checked: unknown, info: any) {
  checkedKeys.value = (checked as number[]) ?? [];
  halfCheckedKeys.value = (info?.halfCheckedKeys as number[]) ?? [];
  isAllSelected.value =
    checkedKeys.value.length === getAllNodeIds(menuTree.value).length;
}

/** 全选/全不选 */
function handleSelectAll() {
  isAllSelected.value = !isAllSelected.value;
  if (isAllSelected.value) {
    checkedKeys.value = getAllNodeIds(menuTree.value);
    halfCheckedKeys.value = [];
  } else {
    checkedKeys.value = [];
    halfCheckedKeys.value = [];
  }
}

/** 展开/折叠所有节点 */
function handleExpandAll() {
  isExpanded.value = !isExpanded.value;
  expandedKeys.value = isExpanded.value ? getAllNodeIds(menuTree.value) : [];
}

/** 递归获取所有节点 ID */
function getAllNodeIds(nodes: any[], ids: number[] = []): number[] {
  nodes.forEach((node: any) => {
    ids.push(node.id);
    if (node.children && node.children.length > 0) {
      getAllNodeIds(node.children, ids);
    }
  });
  return ids;
}
</script>
<template>
  <Modal :title="getTitle" class="w-2/5">
    <Form class="mx-6">
      <template #menuIds="slotProps">
        <Spin :spinning="menuLoading" wrapper-class-name="w-full">
          <!-- antd Tree：设置 height 后启用虚拟滚动，1395+ 节点也流畅 -->
          <AntTree
            class="max-h-96 overflow-y-auto"
            checkable
            :height="380"
            :tree-data="menuTree"
            :field-names="treeFieldNames"
            :checked-keys="checkedKeys"
            :expanded-keys="expandedKeys"
            :selectable="false"
            @check="handleCheck"
            @expand="(keys: any) => (expandedKeys = keys as number[])"
          />
        </Spin>
      </template>
    </Form>
    <template #prepend-footer>
      <div class="flex flex-auto items-center">
        <Checkbox :checked="isAllSelected" @change="handleSelectAll">
          全选
        </Checkbox>
        <Checkbox :checked="isExpanded" @change="handleExpandAll">
          全部展开
        </Checkbox>
      </div>
    </template>
  </Modal>
</template>
