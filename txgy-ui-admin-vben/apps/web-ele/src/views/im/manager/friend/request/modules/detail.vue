<script lang="ts" setup>
import type { ImManagerFriendRequestApi } from '#/api/im/manager/friend/request';

import { ref } from 'vue';

import { DICT_TYPE } from '@vben/constants';

import { ElDescriptions, ElDescriptionsItem, ElDrawer } from 'element-plus';

import { DictTag } from '#/components/dict-tag';
import {
  formatDateTimeText,
  formatUserLabel,
} from '#/views/im/manager/utils/format';

const visible = ref(false);
const detail = ref<ImManagerFriendRequestApi.FriendRequest>(
  {} as ImManagerFriendRequestApi.FriendRequest,
);

/** 打开详情 */
function open(row: ImManagerFriendRequestApi.FriendRequest) {
  detail.value = row;
  visible.value = true;
}

defineExpose({ open });
</script>

<template>
  <ElDrawer v-model="visible" destroy-on-close title="好友申请详情" width="700">
    <ElDescriptions border :column="2">
      <ElDescriptionsItem label="编号">{{ detail.id }}</ElDescriptionsItem>
      <ElDescriptionsItem label="处理结果">
        <DictTag
          :type="DICT_TYPE.IM_FRIEND_REQUEST_HANDLE_RESULT"
          :value="detail.handleResult"
        />
      </ElDescriptionsItem>
      <ElDescriptionsItem label="发起方">
        {{ formatUserLabel(detail.fromNickname, detail.fromUserId) }}
      </ElDescriptionsItem>
      <ElDescriptionsItem label="接收方">
        {{ formatUserLabel(detail.toNickname, detail.toUserId) }}
      </ElDescriptionsItem>
      <ElDescriptionsItem label="申请理由">
        {{ detail.applyContent || '-' }}
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
      <ElDescriptionsItem label="处理理由">
        {{ detail.handleContent || '-' }}
      </ElDescriptionsItem>
      <ElDescriptionsItem label="申请时间">
        {{ formatDateTimeText(detail.createTime) }}
      </ElDescriptionsItem>
      <ElDescriptionsItem label="处理时间">
        {{ formatDateTimeText(detail.handleTime) }}
      </ElDescriptionsItem>
    </ElDescriptions>
  </ElDrawer>
</template>
