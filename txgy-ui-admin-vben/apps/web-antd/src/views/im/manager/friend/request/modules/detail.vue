<script lang="ts" setup>
import type { ImManagerFriendRequestApi } from '#/api/im/manager/friend/request';

import { ref } from 'vue';

import { DICT_TYPE } from '@vben/constants';

import { Descriptions, DescriptionsItem, Drawer } from 'ant-design-vue';

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
  <Drawer
    v-model:open="visible"
    destroy-on-close
    title="好友申请详情"
    width="700"
  >
    <Descriptions bordered :column="2">
      <DescriptionsItem label="编号">{{ detail.id }}</DescriptionsItem>
      <DescriptionsItem label="处理结果">
        <DictTag
          :type="DICT_TYPE.IM_FRIEND_REQUEST_HANDLE_RESULT"
          :value="detail.handleResult"
        />
      </DescriptionsItem>
      <DescriptionsItem label="发起方">
        {{ formatUserLabel(detail.fromNickname, detail.fromUserId) }}
      </DescriptionsItem>
      <DescriptionsItem label="接收方">
        {{ formatUserLabel(detail.toNickname, detail.toUserId) }}
      </DescriptionsItem>
      <DescriptionsItem label="申请理由">
        {{ detail.applyContent || '-' }}
      </DescriptionsItem>
      <DescriptionsItem label="备注">
        {{ detail.displayName || '-' }}
      </DescriptionsItem>
      <DescriptionsItem label="添加来源">
        <DictTag
          :type="DICT_TYPE.IM_FRIEND_ADD_SOURCE"
          :value="detail.addSource"
        />
      </DescriptionsItem>
      <DescriptionsItem label="处理理由">
        {{ detail.handleContent || '-' }}
      </DescriptionsItem>
      <DescriptionsItem label="申请时间">
        {{ formatDateTimeText(detail.createTime) }}
      </DescriptionsItem>
      <DescriptionsItem label="处理时间">
        {{ formatDateTimeText(detail.handleTime) }}
      </DescriptionsItem>
    </Descriptions>
  </Drawer>
</template>
