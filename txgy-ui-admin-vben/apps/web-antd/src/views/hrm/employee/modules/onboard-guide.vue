<script lang="ts" setup>
import type { HrmEmployeeApi } from '#/api/hrm/employee';

import { ref } from 'vue';
import { useRouter } from 'vue-router';

import { useVbenModal } from '@vben/common-ui';

import { Alert, Button, Progress, Spin, Tag } from 'ant-design-vue';

import { getEmployeeOnboardGuide } from '#/api/hrm/employee';

/**
 * 员工上岗向导（I-020）
 *
 * <p>把「入职后必须配齐的四件事」聚合成一份清单：后台账号、薪资（薪资组 + 薪资档案）、
 * 考勤组、社保参保。每项给出完成状态与现状说明，并提供一键跳转到对应配置页，
 * 目标是把上岗配置从「四个模块来回找」压缩成一条清单走完。</p>
 */
defineOptions({ name: 'HrmEmployeeOnboardGuide' });

const router = useRouter();
const loading = ref(false);
const guide = ref<HrmEmployeeApi.OnboardGuide>();

const [Modal, modalApi] = useVbenModal({
  footer: false,
  onOpenChange(isOpen) {
    if (!isOpen) {
      guide.value = undefined;
      return;
    }
    const payload = modalApi.getData<{ employeeId?: number }>();
    load(payload?.employeeId);
  },
  title: '上岗向导',
});

async function load(employeeId?: number) {
  if (!employeeId) {
    return;
  }
  loading.value = true;
  try {
    guide.value = await getEmployeeOnboardGuide(employeeId);
  } finally {
    loading.value = false;
  }
}

function go(path?: string) {
  if (!path) {
    return;
  }
  modalApi.close();
  router.push(path);
}

function statusColor(done?: boolean, statusText?: string) {
  if (done) {
    return 'success';
  }
  return statusText === '部分就绪' ? 'warning' : 'error';
}
</script>

<template>
  <Modal class="w-[720px]">
    <Spin :spinning="loading">
      <div class="mb-3 flex items-center gap-3">
        <span class="text-base font-medium">
          {{ guide?.employeeName || '' }}
          <span class="text-muted-foreground text-xs">
            {{ guide?.jobNumber || '' }}
          </span>
        </span>
        <Progress
          v-if="guide"
          :percent="
            guide.totalCount
              ? Math.round(((guide.readyCount || 0) / guide.totalCount) * 100)
              : 0
          "
          class="ml-auto w-[260px]"
          size="small"
        />
      </div>

      <Alert
        v-if="guide?.ready"
        class="mb-3"
        message="上岗配置已全部就绪，可以直接排班、算薪与参保"
        show-icon
        type="success"
      />
      <Alert
        v-else-if="guide"
        class="mb-3"
        :message="`还有 ${(guide.totalCount || 0) - (guide.readyCount || 0)} 项未完成，按下面清单逐项补齐即可`"
        show-icon
        type="warning"
      />

      <div class="flex flex-col gap-3">
        <div
          v-for="item in guide?.items || []"
          :key="item.key"
          class="rounded-md border p-3"
        >
          <div class="flex items-center gap-2">
            <span class="font-medium">{{ item.title }}</span>
            <Tag :color="statusColor(item.done, item.statusText)">
              {{ item.statusText }}
            </Tag>
            <Button
              v-if="!item.done"
              class="ml-auto"
              size="small"
              type="primary"
              @click="go(item.actionPath)"
            >
              {{ item.actionText }}
            </Button>
            <Button
              v-else
              class="ml-auto"
              size="small"
              type="link"
              @click="go(item.actionPath)"
            >
              去看看
            </Button>
          </div>
          <div class="text-muted-foreground mt-1 text-xs">
            {{ item.detail }}
          </div>
        </div>
      </div>
    </Spin>
  </Modal>
</template>
