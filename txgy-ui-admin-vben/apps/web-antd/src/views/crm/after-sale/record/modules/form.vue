<script lang="ts" setup>
import type { CrmAfterSaleVisitRecordApi } from '#/api/crm/after-sale/record';

import { computed, ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { message } from 'ant-design-vue';
import dayjs from 'dayjs';

import { useVbenForm } from '#/adapter/form';
import {
  createAfterSaleVisitRecord,
  getAfterSaleVisitRecord,
  updateAfterSaleVisitRecord,
} from '#/api/crm/after-sale/record';
import {
  getAfterSaleVisitTask,
  getAfterSaleVisitTaskSimpleList,
} from '#/api/crm/after-sale/task';
import { getAfterSaleWarranty } from '#/api/crm/after-sale/warranty';
import { $t } from '#/locales';

import { useFormSchema } from '../data';

const emit = defineEmits(['success']);
const formData = ref<CrmAfterSaleVisitRecordApi.VisitRecord>();
const getTitle = computed(() => {
  return formData.value?.id
    ? $t('ui.actionTitle.edit', ['回访记录'])
    : $t('ui.actionTitle.create', ['回访记录']);
});

/** 关联质保档案的完工验收日期（毫秒时间戳），用于限制「回访时间」范围 */
const acceptDateTs = ref<number>();
/**
 * 「关联任务编号」是否锁定（置灰不可编辑）：
 * 1）编辑已有回访记录时锁定；
 * 2）由任务列表「填写回访记录」跳转而来时锁定；
 * 仅「回访记录模块内新增」时允许选择
 */
const taskIdLocked = ref(false);
/** 上一次的质保档案编号：避免表单值变化时重复请求 */
let lastWarrantyId: number | undefined;

/** 加载关联质保档案的完工验收日期 */
async function loadAcceptDate(warrantyId?: number) {
  acceptDateTs.value = undefined;
  if (!warrantyId) {
    return;
  }
  try {
    const warranty = await getAfterSaleWarranty(warrantyId);
    if (warranty?.projectAcceptDate) {
      acceptDateTs.value = dayjs(warranty.projectAcceptDate)
        .startOf('day')
        .valueOf();
    }
  } catch {
    acceptDateTs.value = undefined;
  }
}

/**
 * 新增回访记录时，选择「质保档案」后自动带出该档案下的关联任务编号
 *
 * 编号为该质保档案范围内的**独立业务编号**（从 1 开始），默认取最新一条「未完成」任务
 * （待处理/已逾期），若该档案下任务均已完成则取最新一条；用户可直接修改该编号。
 * 编辑已有记录、或由任务列表跳转填写时该字段锁定，不自动覆盖
 */
async function autoFillTaskId(warrantyId?: number) {
  if (!warrantyId || formData.value?.id || taskIdLocked.value) {
    return;
  }
  try {
    const tasks = await getAfterSaleVisitTaskSimpleList(warrantyId);
    if (!tasks || tasks.length === 0) {
      return;
    }
    const unfinished = tasks.filter((item) => item.status !== 2);
    const candidates = unfinished.length > 0 ? unfinished : tasks;
    // 优先带出「已可提交」的任务（计划回访日期 ≤ 今天，即窗口期内或已逾期），
    // 避免自动绑定尚未到计划回访日期的任务、提交时才被硬时间锁拦截；无可用任务时取最新一条
    const today = dayjs().startOf('day');
    const reachable = candidates.filter(
      (item) =>
        item.taskDate && !dayjs(item.taskDate).startOf('day').isAfter(today),
    );
    const target = reachable.at(0) ?? candidates.at(-1);
    if (target) {
      // taskId 为隐藏字段：界面展示与修改的是业务编号（taskBizNo）
      await formApi.setValues({
        taskId: target.id,
        taskBizNo: target.bizNo,
      });
    }
  } catch {
    // 自动带出失败不阻断填写
  }
}

/**
 * 校验回访时间：不得晚于今天，且不得早于项目完工验收日期
 *
 * @returns 校验不通过时返回错误提示文案，通过返回 null
 */
function validateVisitTime(visitTime?: number | string): null | string {
  if (visitTime === '' || visitTime === null || visitTime === undefined) {
    return null;
  }
  const target = dayjs(visitTime);
  if (!target.isValid()) {
    return '回访时间格式不正确';
  }
  // 1. 不能晚于今天（含当天任意时刻）
  if (target.isAfter(dayjs().endOf('day'))) {
    return '回访时间不能晚于今天';
  }
  // 2. 不能早于项目完工验收日期
  if (
    acceptDateTs.value !== undefined &&
    target.isBefore(dayjs(acceptDateTs.value).startOf('day'))
  ) {
    return '回访时间不能早于项目完工验收日期';
  }
  return null;
}

const [Form, formApi] = useVbenForm({
  commonConfig: {
    componentProps: {
      class: 'w-full',
    },
  },
  layout: 'horizontal',
  wrapperClass: 'grid-cols-2',
  schema: useFormSchema(
    () => acceptDateTs.value,
    () => taskIdLocked.value,
  ),
  showDefaultActions: false,
  // 切换质保档案时：刷新完工验收日期（限制回访时间范围）+ 自动带出关联任务编号
  handleValuesChange(values) {
    const warrantyId = (values as CrmAfterSaleVisitRecordApi.VisitRecord)
      ?.warrantyId;
    if (warrantyId !== lastWarrantyId) {
      lastWarrantyId = warrantyId;
      void loadAcceptDate(warrantyId);
      void autoFillTaskId(warrantyId);
    }
  },
});

const [Modal, modalApi] = useVbenModal({
  async onConfirm() {
    const { valid } = await formApi.validate();
    if (!valid) {
      return;
    }
    const data =
      (await formApi.getValues()) as CrmAfterSaleVisitRecordApi.VisitRecord;
    // 回访时间校验：不晚于今天、不早于项目完工验收日期（新增与编辑均生效）
    const timeError = validateVisitTime(data.visitTime);
    if (timeError) {
      message.error(timeError);
      return;
    }
    // 「关联任务编号」为质保档案内的独立业务编号，需按所属质保档案反查回任务主键后再提交
    const bizNo = data.taskBizNo;
    if (bizNo === null || bizNo === undefined || (bizNo as any) === '') {
      data.taskId = undefined; // 留空表示不关联回访任务
    } else {
      if (!data.warrantyId) {
        message.error('请先选择关联质保档案，再填写关联任务编号');
        return;
      }
      modalApi.lock();
      try {
        const tasks = await getAfterSaleVisitTaskSimpleList(data.warrantyId);
        const matched = (tasks ?? []).find((item) => item.bizNo === bizNo);
        if (!matched) {
          message.error(
            `该质保档案下不存在编号为 ${bizNo} 的回访任务，请核对后重新填写`,
          );
          return;
        }
        data.taskId = matched.id;
      } finally {
        modalApi.unlock();
      }
    }
    modalApi.lock();
    try {
      await (formData.value?.id
        ? updateAfterSaleVisitRecord(data)
        : createAfterSaleVisitRecord(data));
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
      lastWarrantyId = undefined;
      acceptDateTs.value = undefined;
      taskIdLocked.value = false;
      return;
    }
    // 打开时先按「可选择」初始化，再按模式决定是否锁定
    taskIdLocked.value = false;
    const data = modalApi.getData<
      CrmAfterSaleVisitRecordApi.VisitRecord & { taskId?: number }
    >();
    // 1. 编辑已有记录：任务编号锁定不可修改（防止改绑已归档的回访任务）
    if (data?.id) {
      // 任务编号锁定：仅通过 taskIdLocked 驱动 schema 的 disabled，
      // 不能再用 updateSchema 覆盖 taskId（否则会覆盖其 options 加载逻辑，导致只显示任务 ID）
      taskIdLocked.value = true;
      await formApi.updateSchema([
        { fieldName: 'warrantyId', componentProps: { disabled: false } },
      ]);
      modalApi.lock();
      try {
        formData.value = await getAfterSaleVisitRecord(data.id);
        await formApi.setValues(formData.value);
        // 回填「关联任务编号」（业务编号，仅查看）：由已绑定的任务反查得到
        if (formData.value?.taskId) {
          try {
            const task = await getAfterSaleVisitTask(formData.value.taskId);
            await formApi.setValues({ taskBizNo: task?.bizNo });
          } catch {
            // 任务不存在时忽略
          }
        }
        // 加载关联质保档案的完工验收日期，作为「回访时间」的下限
        lastWarrantyId = formData.value?.warrantyId;
        await loadAcceptDate(lastWarrantyId);
      } finally {
        modalApi.unlock();
      }
      return;
    }
    // 2. 由任务列表「填写回访记录」跳转而来的新增模式：自动回填任务编号与质保档案并置灰，
    //    此时记录尚未入库、任务状态保持「待处理」；若用户取消关闭则不产生任何数据
    if (data?.taskId) {
      // 标记锁定：任务编号下拉在质保档案联动刷新后仍保持置灰
      taskIdLocked.value = true;
      modalApi.lock();
      try {
        // 质保档案置灰不可改；任务编号由 taskIdLocked 驱动 disabled，
        // 不在此处 updateSchema（否则会覆盖其 options 加载逻辑，导致下拉只显示任务 ID）
        await formApi.updateSchema([
          { fieldName: 'warrantyId', componentProps: { disabled: true } },
        ]);
        await formApi.setValues({
          taskId: data.taskId,
          warrantyId: data.warrantyId,
        });
        // 回填该任务的业务编号（仅展示，字段已锁定）
        try {
          const tasks = await getAfterSaleVisitTaskSimpleList(data.warrantyId);
          const matched = (tasks ?? []).find((item) => item.id === data.taskId);
          if (matched) {
            await formApi.setValues({ taskBizNo: matched.bizNo });
          }
        } catch {
          // 忽略回填失败
        }
        lastWarrantyId = data.warrantyId;
        await loadAcceptDate(data.warrantyId);
      } finally {
        modalApi.unlock();
      }
    }
  },
});
</script>

<template>
  <Modal :title="getTitle" class="w-2/3">
    <Form class="mx-4" />
  </Modal>
</template>
