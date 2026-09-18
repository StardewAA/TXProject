<script lang="ts" setup>
/**
 * AI 聊天角色 - MES 数据权限配置弹窗
 *
 * 功能：
 * 1. 按业务域（生产工单/生产任务/报工/工艺/库存/质检/设备…）配置该角色
 *    「是否允许查询」「是否允许敏感生产数据」「数据范围」；
 * 2. 把角色授权给指定用户（用户 → 角色 → 权限）；
 * 3. 保存后整体提交后端，后端在 AI 对话时做强制校验（越权直接拦截）。
 */
import type { AiChatPermissionApi } from '#/api/ai/permission';

import { computed, ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { message, Select, Switch } from 'ant-design-vue';

import {
  getBizTypes,
  getRolePermission,
  updateRolePermission,
} from '#/api/ai/permission';
import { requestClient } from '#/api/request';

defineOptions({ name: 'AiChatRolePermissionModal' });

const emit = defineEmits<{ success: [] }>();

/** 当前配置的角色 */
const roleId = ref<number>();
/** 全部 MES 业务域（后端枚举字典） */
const bizTypes = ref<AiChatPermissionApi.BizType[]>([]);
/** 业务域权限映射：key = 业务域编码 */
const permMap = ref<Record<string, AiChatPermissionApi.Permission>>({});
/** 绑定的用户编号列表 */
const userIds = ref<number[]>([]);
/** 用户下拉选项 */
const userOptions = ref<{ label: string; value: number }[]>([]);
/** 保存中状态 */
const saving = ref(false);

/** 数据范围下拉选项 */
const scopeOptions = [
  { label: '全部数据', value: 'ALL' },
  { label: '本部门数据', value: 'DEPT' },
  { label: '仅本人相关', value: 'SELF' },
];

/** 是否至少开启了一个业务域（保存前校验用） */
const hasAnyEnabled = computed(() =>
  Object.values(permMap.value).some((item) => item.canQuery),
);

/** 初始化某个业务域的默认权限 */
function defaultPermission(bizType: string): AiChatPermissionApi.Permission {
  return { bizType, canQuery: false, canSensitive: false, dataScope: 'ALL' };
}

/** 加载业务域字典 + 该角色已有权限 + 系统用户列表 */
async function loadData(id: number) {
  roleId.value = id;
  // 1. 全部业务域
  bizTypes.value = await getBizTypes();
  // 2. 该角色已有权限配置（缺失的项用默认值补齐，保证表格完整可编辑）
  const result = await getRolePermission(id);
  const map: Record<string, AiChatPermissionApi.Permission> = {};
  bizTypes.value.forEach((biz) => {
    map[biz.type] = defaultPermission(biz.type);
  });
  (result?.permissions ?? []).forEach((item) => {
    map[item.bizType] = { ...map[item.bizType], ...item };
  });
  permMap.value = map;
  userIds.value = result?.userIds ?? [];
  // 3. 系统用户精简列表（用于「绑定用户」下拉）
  const users =
    await requestClient.get<{ id: number; nickname: string }[]>(
      '/system/user/simple-list',
    );
  userOptions.value = (users ?? []).map((user) => ({
    label: user.nickname,
    value: user.id,
  }));
}

/** 保存权限配置 */
async function handleSave() {
  if (!roleId.value) return;
  if (!hasAnyEnabled.value) {
    message.warning('请至少为一个业务域开启「允许查询」');
    return;
  }
  saving.value = true;
  try {
    await updateRolePermission({
      roleId: roleId.value,
      // 全量提交所有业务域的配置（后端整体覆盖保存）
      permissions: Object.values(permMap.value),
      userIds: userIds.value,
    });
    message.success('权限配置已保存');
    emit('success');
    modalApi.close();
  } finally {
    saving.value = false;
  }
}

const [Modal, modalApi] = useVbenModal({
  class: 'w-[760px]',
  fullscreenButton: false,
  title: 'MES 数据权限配置',
  onConfirm: handleSave,
  onOpenChange(isOpen) {
    if (!isOpen) return;
    const id = modalApi.getData()?.roleId as number;
    if (id) {
      loadData(id);
    }
  },
});
</script>

<template>
  <Modal :confirm-disabled="saving">
    <div class="max-h-[60vh] overflow-y-auto pr-1">
      <div class="mb-3 rounded bg-blue-50 p-3 text-xs leading-5">
        按业务域配置该 AI 角色的数据权限。未开启「允许查询」的业务域，用户提问涉及时会
        <span class="font-bold">直接被后端拦截</span>（不经大模型判断）。
      </div>

      <!-- 业务域权限表格 -->
      <table class="w-full text-sm">
        <thead>
          <tr class="border-b text-left">
            <th class="py-2">MES 业务域</th>
            <th class="w-24 text-center">允许查询</th>
            <th class="w-28 text-center">允许敏感数据</th>
            <th class="w-36">数据范围</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="biz in bizTypes"
            :key="biz.type"
            class="border-b last:border-b-0"
          >
            <td class="py-2">{{ biz.name }}</td>
            <td class="text-center">
              <!-- 注意：不能用可选链 v-model（permMap[biz.type]?.x），rolldown 编译可选链赋值会报错；
                   permMap 在 loadData 时已为所有业务域补齐默认值，可安全直接绑定 -->
              <Switch v-model:checked="permMap[biz.type].canQuery" />
            </td>
            <td class="text-center">
              <Switch
                v-model:checked="permMap[biz.type].canSensitive"
                :disabled="!permMap[biz.type]?.canQuery"
              />
            </td>
            <td>
              <Select
                v-model:value="permMap[biz.type].dataScope"
                :options="scopeOptions"
                class="w-32"
                size="small"
              />
            </td>
          </tr>
        </tbody>
      </table>

      <!-- 绑定用户 -->
      <div class="mt-4">
        <div class="mb-2 text-sm font-medium">
          绑定用户（该角色授权给哪些用户使用）
        </div>
        <Select
          v-model:value="userIds"
          :options="userOptions"
          class="w-full"
          mode="multiple"
          placeholder="选择使用该 AI 角色的用户"
        />
      </div>
    </div>
  </Modal>
</template>
