<script lang="ts" setup>
import type { ImManagerFriendApi } from '#/api/im/manager/friend';

import { ref } from 'vue';

import { DICT_TYPE } from '@vben/constants';

import { Descriptions, DescriptionsItem, Drawer } from 'ant-design-vue';

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
  <Drawer
    v-model:open="visible"
    destroy-on-close
    title="好友关系详情"
    width="700"
  >
    <Descriptions bordered :column="2">
      <DescriptionsItem label="编号">{{ detail.id }}</DescriptionsItem>
      <DescriptionsItem label="状态">
        <DictTag :type="DICT_TYPE.IM_FRIEND_STATUS" :value="detail.status" />
      </DescriptionsItem>
      <DescriptionsItem label="用户">
        {{ formatUserLabel(detail.userNickname, detail.userId) }}
      </DescriptionsItem>
      <DescriptionsItem label="好友">
        {{ formatUserLabel(detail.friendNickname, detail.friendUserId) }}
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
      <DescriptionsItem label="免打扰">
        <DictTag
          :type="DICT_TYPE.INFRA_BOOLEAN_STRING"
          :value="detail.silent"
        />
      </DescriptionsItem>
      <DescriptionsItem label="置顶">
        <DictTag
          :type="DICT_TYPE.INFRA_BOOLEAN_STRING"
          :value="detail.pinned"
        />
      </DescriptionsItem>
      <DescriptionsItem label="拉黑">
        <DictTag
          :type="DICT_TYPE.INFRA_BOOLEAN_STRING"
          :value="detail.blocked"
        />
      </DescriptionsItem>
      <DescriptionsItem label="添加时间">
        {{ formatDateTimeText(detail.addTime) }}
      </DescriptionsItem>
      <DescriptionsItem label="删除时间">
        {{ formatDateTimeText(detail.deleteTime) }}
      </DescriptionsItem>
      <DescriptionsItem label="创建时间">
        {{ formatDateTimeText(detail.createTime) }}
      </DescriptionsItem>
    </Descriptions>
  </Drawer>
</template>
