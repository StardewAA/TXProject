<script lang="ts" setup>
import type { ImManagerFriendApi } from '#/api/im/manager/friend';

import { ref } from 'vue';

import { DICT_TYPE } from '@vben/constants';

import {
  ElDescriptions,
  ElDescriptionsItem,
  ElDrawer,
} from 'element-plus';

import { DictTag } from '#/components/dict-tag';
import {
  formatDateTimeText,
  formatUserLabel,
} from '#/views/im/manager/utils/format';

const visible = ref(false);
const detail = ref<ImManagerFriendApi.Friend>({} as ImManagerFriendApi.Friend);

/** 打开详情 */
function open(row: ImManagerFriendApi.Friend) {
  detail.value = row;
  visible.value = true;
}

defineExpose({ open });
</script>

<template>
  <ElDrawer v-model="visible" destroy-on-close title="好友关系详情" width="700">
    <ElDescriptions border :column="2">
      <ElDescriptionsItem label="编号">{{ detail.id }}</ElDescriptionsItem>
      <ElDescriptionsItem label="状态">
        <DictTag
          :type="DICT_TYPE.IM_FRIEND_STATUS"
          :value="detail.status"
        />
      </ElDescriptionsItem>
      <ElDescriptionsItem label="用户">
        {{ formatUserLabel(detail.userNickname, detail.userId) }}
      </ElDescriptionsItem>
      <ElDescriptionsItem label="好友">
        {{ formatUserLabel(detail.friendNickname, detail.friendUserId) }}
      </ElDescriptionsItem>
      <ElDescriptionsItem label="备注">
        {{ detail.displayName || '-' }}
      </ElDescriptionsItem>
      <ElDescriptionsItem label="添加来源">
        <DictTag
          :type="DICT_TYPE.IM_FRIEND_ADD_SOURCE"
          :value="detail.addSource"
        />
      </ElDescriptionsItem>
      <ElDescriptionsItem label="免打扰">
        <DictTag
          :type="DICT_TYPE.INFRA_BOOLEAN_STRING"
          :value="detail.silent"
        />
      </ElDescriptionsItem>
      <ElDescriptionsItem label="置顶">
        <DictTag
          :type="DICT_TYPE.INFRA_BOOLEAN_STRING"
          :value="detail.pinned"
        />
      </ElDescriptionsItem>
      <ElDescriptionsItem label="拉黑">
        <DictTag
          :type="DICT_TYPE.INFRA_BOOLEAN_STRING"
          :value="detail.blocked"
        />
      </ElDescriptionsItem>
      <ElDescriptionsItem label="添加时间">
        {{ formatDateTimeText(detail.addTime) }}
      </ElDescriptionsItem>
      <ElDescriptionsItem label="删除时间">
        {{ formatDateTimeText(detail.deleteTime) }}
      </ElDescriptionsItem>
      <ElDescriptionsItem label="创建时间">
        {{ formatDateTimeText(detail.createTime) }}
      </ElDescriptionsItem>
    </ElDescriptions>
  </ElDrawer>
</template>
