<script lang="ts" setup>
import type { ImManagerGroupRequestApi } from '#/api/im/manager/group/request';

import { ref } from 'vue';

import { DICT_TYPE } from '@vben/constants';

import { ElDescriptions, ElDescriptionsItem, ElDrawer } from 'element-plus';

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
  <ElDrawer v-model="visible" destroy-on-close title="加群申请详情" width="700">
    <ElDescriptions border :column="2">
      <ElDescriptionsItem label="编号">{{ detail.id }}</ElDescriptionsItem>
      <ElDescriptionsItem label="处理结果">
        <DictTag
          :type="DICT_TYPE.IM_GROUP_REQUEST_HANDLE_RESULT"
          :value="detail.handleResult"
        />
      </ElDescriptionsItem>
      <ElDescriptionsItem label="群">
        {{ formatGroupLabel(detail.groupName, detail.groupId) }}
      </ElDescriptionsItem>
      <ElDescriptionsItem label="申请人 / 被邀请人">
        {{ formatUserLabel(detail.userNickname, detail.userId) }}
      </ElDescriptionsItem>
      <ElDescriptionsItem label="邀请人">
        <template v-if="detail.inviterUserId">
          {{ formatUserLabel(detail.inviterNickname, detail.inviterUserId) }}
        </template>
        <span v-else>主动申请</span>
      </ElDescriptionsItem>
      <ElDescriptionsItem label="加入来源">
        <DictTag
          :type="DICT_TYPE.IM_GROUP_ADD_SOURCE"
          :value="detail.addSource"
        />
      </ElDescriptionsItem>
      <ElDescriptionsItem label="申请理由">
        {{ detail.applyContent || '-' }}
      </ElDescriptionsItem>
      <ElDescriptionsItem label="处理人">
        <template v-if="detail.handleUserId">
          {{ formatUserLabel(detail.handleNickname, detail.handleUserId) }}
        </template>
        <span v-else>-</span>
      </ElDescriptionsItem>
      <ElDescriptionsItem label="处理理由">
        {{ detail.handleContent || '-' }}
      </ElDescriptionsItem>
      <ElDescriptionsItem label="处理时间">
        {{ formatDateTimeText(detail.handleTime) }}
      </ElDescriptionsItem>
      <ElDescriptionsItem label="申请时间">
        {{ formatDateTimeText(detail.createTime) }}
      </ElDescriptionsItem>
    </ElDescriptions>
  </ElDrawer>
</template>
