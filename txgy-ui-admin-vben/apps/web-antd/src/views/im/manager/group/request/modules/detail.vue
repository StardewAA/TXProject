<script lang="ts" setup>
import type { ImManagerGroupRequestApi } from '#/api/im/manager/group/request';

import { ref } from 'vue';

import { DICT_TYPE } from '@vben/constants';

import { Descriptions, DescriptionsItem, Drawer } from 'ant-design-vue';

import { DictTag } from '#/components/dict-tag';
import {
  formatDateTimeText,
  formatGroupLabel,
  formatUserLabel,
} from '#/views/im/manager/utils/format';

const visible = ref(false);
const detail = ref<ImManagerGroupRequestApi.GroupRequest>(
  {} as ImManagerGroupRequestApi.GroupRequest,
);

/** 打开详情 */
function open(row: ImManagerGroupRequestApi.GroupRequest) {
  detail.value = row;
  visible.value = true;
}

defineExpose({ open });
</script>

<template>
  <Drawer
    v-model:open="visible"
    destroy-on-close
    title="加群申请详情"
    width="700"
  >
    <Descriptions bordered :column="2">
      <DescriptionsItem label="编号">{{ detail.id }}</DescriptionsItem>
      <DescriptionsItem label="处理结果">
        <DictTag
          :type="DICT_TYPE.IM_GROUP_REQUEST_HANDLE_RESULT"
          :value="detail.handleResult"
        />
      </DescriptionsItem>
      <DescriptionsItem label="群">
        {{ formatGroupLabel(detail.groupName, detail.groupId) }}
      </DescriptionsItem>
      <DescriptionsItem label="申请人 / 被邀请人">
        {{ formatUserLabel(detail.userNickname, detail.userId) }}
      </DescriptionsItem>
      <DescriptionsItem label="邀请人">
        <template v-if="detail.inviterUserId">
          {{ formatUserLabel(detail.inviterNickname, detail.inviterUserId) }}
        </template>
        <span v-else>主动申请</span>
      </DescriptionsItem>
      <DescriptionsItem label="加入来源">
        <DictTag
          :type="DICT_TYPE.IM_GROUP_ADD_SOURCE"
          :value="detail.addSource"
        />
      </DescriptionsItem>
      <DescriptionsItem label="申请理由">
        {{ detail.applyContent || '-' }}
      </DescriptionsItem>
      <DescriptionsItem label="处理人">
        <template v-if="detail.handleUserId">
          {{ formatUserLabel(detail.handleNickname, detail.handleUserId) }}
        </template>
        <span v-else>-</span>
      </DescriptionsItem>
      <DescriptionsItem label="处理理由">
        {{ detail.handleContent || '-' }}
      </DescriptionsItem>
      <DescriptionsItem label="处理时间">
        {{ formatDateTimeText(detail.handleTime) }}
      </DescriptionsItem>
      <DescriptionsItem label="申请时间">
        {{ formatDateTimeText(detail.createTime) }}
      </DescriptionsItem>
    </Descriptions>
  </Drawer>
</template>
