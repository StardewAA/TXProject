<script lang="ts" setup>
import type { HrmEmploymentTypeApi } from '#/api/hrm/employment-type';

import { computed, ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { message } from 'ant-design-vue';

import { getEnabledEmploymentTypeList } from '#/api/hrm/employment-type';
import { onboardRecruitOffer } from '#/api/hrm/recruit/offer';

const emit = defineEmits(['success']);

const offerId = ref<number>();
const plannedOnboardDate = ref<string>();
const employmentTypeId = ref<number>();
const employmentTypes = ref<HrmEmploymentTypeApi.EmploymentType[]>([]);
const loading = ref(false);

const selectedEmploymentType = computed(() =>
  employmentTypes.value.find((item) => item.id === employmentTypeId.value),
);

const selectOptions = computed(() =>
  employmentTypes.value.map((item) => ({
    label: item.contractTemplateId
      ? `${item.name}（${item.contractTemplateName ?? '已绑定模板'}）`
      : `${item.name}（未绑定合同模板）`,
    value: item.id,
  })),
);

const filterOption = (input: string, option: any) => {
  return String(option?.label ?? '')
    .toLowerCase()
    .includes(input.toLowerCase());
};

/** 计划入职时间：默认取 Offer 的计划入职日期 09:00 */
function resolveEntryTime() {
  return plannedOnboardDate.value
    ? new Date(`${plannedOnboardDate.value}T09:00:00`).getTime()
    : Date.now();
}

const [Modal, modalApi] = useVbenModal({
  async onConfirm() {
    if (!employmentTypeId.value) {
      message.warning('请选择用工形式');
      return;
    }
    if (
      selectedEmploymentType.value &&
      !selectedEmploymentType.value.contractTemplateId
    ) {
      message.error(
        `用工形式「${selectedEmploymentType.value.name}」尚未绑定默认合同模板，请先到「员工管理 → 用工形式」补齐`,
      );
      return;
    }
    modalApi.lock();
    loading.value = true;
    try {
      await onboardRecruitOffer({
        offerId: offerId.value!,
        employmentTypeId: employmentTypeId.value,
        entryStatus: 1,
        entryTime: resolveEntryTime(),
        status: 5,
        type: 2,
      });
      emit('success');
      await modalApi.close();
      message.success(
        '已转为员工：自动生成工号，并按所选用工形式的模板生成合同、发送待签署通知',
      );
    } finally {
      loading.value = false;
      modalApi.unlock();
    }
  },
  async onOpenChange(isOpen: boolean) {
    if (!isOpen) {
      employmentTypeId.value = undefined;
      return;
    }
    const data = modalApi.getData<{
      offerId: number;
      plannedOnboardDate?: string;
    }>();
    offerId.value = data?.offerId;
    plannedOnboardDate.value = data?.plannedOnboardDate;
    employmentTypeId.value = undefined;
    modalApi.lock();
    try {
      employmentTypes.value = await getEnabledEmploymentTypeList();
    } finally {
      modalApi.unlock();
    }
  },
});
</script>

<template>
  <Modal title="转为员工" class="w-[620px]">
    <a-form class="mx-4" layout="vertical">
      <a-form-item required>
        <template #label>
          <span>用工形式</span>
          <span class="text-muted-foreground ml-2 text-xs">
            决定签哪份合同、按什么方式计薪
          </span>
        </template>
        <a-select
          v-model:value="employmentTypeId"
          :filter-option="filterOption"
          :options="selectOptions"
          allow-clear
          placeholder="请选择用工形式，例如：临时工 / 劳务用工 / 日结工"
          show-search
        />
      </a-form-item>

      <a-form-item label="计划入职日期">
        <a-input :value="plannedOnboardDate || '未设置（按当前时间）'" disabled />
      </a-form-item>

      <a-alert
        v-if="
          selectedEmploymentType && !selectedEmploymentType.contractTemplateId
        "
        class="mb-3"
        show-icon
        type="warning"
      >
        <template #message>
          该用工形式未绑定默认合同模板，请先到「员工管理 → 用工形式」配置后再转员工
        </template>
      </a-alert>

      <div class="text-muted-foreground text-xs leading-5">
        转员工后系统会：① 按 Offer 信息自动建档并生成唯一工号；②
        按所选用工形式绑定的模板生成合同文件；③ 自动开通员工账号（用户名＝工号）并分配「员工」角色；④
        向员工发送待签署通知。
      </div>
    </a-form>
  </Modal>
</template>
