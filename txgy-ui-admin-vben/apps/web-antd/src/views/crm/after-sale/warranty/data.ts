import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';

import { DICT_TYPE } from '@vben/constants';
import { getDictOptions } from '@vben/hooks';

import {
  AFTER_SALE_WARRANTY_STATUS_EXPIRED,
  AFTER_SALE_WARRANTY_STATUS_EXPIRING,
  AFTER_SALE_WARRANTY_STATUS_IN_WARRANTY,
  getAfterSaleWarrantyContractStatus,
} from '#/api/crm/after-sale/warranty';
import { getContractSimpleList } from '#/api/crm/contract';
import { getCustomerSimpleList } from '#/api/crm/customer';
import { getSimpleUserList } from '#/api/system/user';

/**
 * 售后设置「质保到期提前提醒天数」的兜底值
 *
 * 与后端 `CrmAfterSaleWarrantyServiceImpl#resolveWarrantyWarnDays` 的默认值保持一致，
 * 仅用于保存前的「预计状态」预览；最终状态以后端保存后的重算结果为准。
 */
const WARRANTY_WARN_DAYS_FALLBACK = 30;

/** 质保状态名称（与后端 CrmAfterSaleWarrantyStatusEnum 文案保持一致） */
const WARRANTY_STATUS_NAME: Record<number, string> = {
  [AFTER_SALE_WARRANTY_STATUS_IN_WARRANTY]: '质保中',
  [AFTER_SALE_WARRANTY_STATUS_EXPIRING]: '即将到期',
  [AFTER_SALE_WARRANTY_STATUS_EXPIRED]: '已到期终止',
};

/** 日期 → yyyy-MM-dd */
function formatDate(date: Date): string {
  const month = `${date.getMonth() + 1}`.padStart(2, '0');
  const day = `${date.getDate()}`.padStart(2, '0');
  return `${date.getFullYear()}-${month}-${day}`;
}

/**
 * 由「完工验收日期 + 质保年限」推算质保到期日
 *
 * 口径与后端 `calculateEndDate` 一致：验收日期 + 年限 - 1 天。
 */
function calcEndDateByYears(
  acceptDate?: string,
  years?: number,
): Date | undefined {
  if (!acceptDate || !years) {
    return undefined;
  }
  const date = new Date(`${acceptDate}T00:00:00`);
  if (Number.isNaN(date.getTime())) {
    return undefined;
  }
  date.setFullYear(date.getFullYear() + Number(years));
  date.setDate(date.getDate() - 1);
  return date;
}

/** 取最终生效的质保到期日：优先用手填的到期日，未填则按验收日期 + 年限自动计算 */
function resolveEndDate(values: Record<string, any>): Date | undefined {
  if (values?.warrantyEndDate) {
    const date = new Date(`${values.warrantyEndDate}T00:00:00`);
    if (!Number.isNaN(date.getTime())) {
      return date;
    }
  }
  return calcEndDateByYears(values?.projectAcceptDate, values?.warrantyYears);
}

/**
 * 按到期日预估质保状态（判定规则与后端 `calculateTargetStatus` 一致）
 *
 * 到期日当天仍算有效，次日起才判定为已到期；进入提前提醒天数区间为「即将到期」。
 */
function calcStatusByEndDate(endDate?: Date): number | undefined {
  if (!endDate) {
    return undefined;
  }
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  const end = new Date(endDate);
  end.setHours(0, 0, 0, 0);
  if (end.getTime() < today.getTime()) {
    return AFTER_SALE_WARRANTY_STATUS_EXPIRED;
  }
  const limit = new Date(today);
  limit.setDate(limit.getDate() + WARRANTY_WARN_DAYS_FALLBACK);
  return end.getTime() <= limit.getTime()
    ? AFTER_SALE_WARRANTY_STATUS_EXPIRING
    : AFTER_SALE_WARRANTY_STATUS_IN_WARRANTY;
}

/**
 * 「预计质保状态」预览文案
 *
 * 让用户在保存前就能看到状态会变成什么，避免「为什么状态自己变了」的疑问。
 */
function buildStatusPreviewText(values: Record<string, any>): string {
  const end = resolveEndDate(values);
  if (!end) {
    return '填好「完工验收日期」与「质保年限」后自动推算';
  }
  const status = calcStatusByEndDate(end);
  const name = status ? WARRANTY_STATUS_NAME[status] : '-';
  return `${formatDate(end)} → 预计「${name}」（保存后由系统按到期日最终判定）`;
}

/** 新增/修改的表单 */
export function useFormSchema(): VbenFormSchema[] {
  return [
    {
      component: 'Input',
      fieldName: 'id',
      dependencies: {
        triggerFields: [''],
        show: () => false,
      },
    },
    {
      component: 'ApiSelect',
      fieldName: 'customerId',
      label: '客户名称',
      rules: 'required',
      componentProps: {
        api: getCustomerSimpleList,
        labelField: 'name',
        valueField: 'id',
        placeholder: '请选择客户',
        allowClear: true,
      },
    },
    {
      component: 'Select',
      fieldName: 'contractId',
      label: '关联合同',
      rules: 'required',
      dependencies: {
        triggerFields: ['customerId', 'id'],
        // 编辑时不允许更换来源合同（后端同样会忽略该字段）；创建时需先选客户
        disabled: (values) => !values.customerId || !!values.id,
        async componentProps(values) {
          if (!values.customerId) {
            return { options: [], placeholder: '请先选择客户' };
          }
          const contracts = await getContractSimpleList(values.customerId);
          // 业务约束：一个合同仅允许一份质保档案。
          // 这里批量查出这批合同的质保档案状态，已创建的合同在选项上标注并置灰，
          // 从根本上避免操作人员选中已建档案的合同（后端同样会拦截并给出提示）。
          const statusList = await getAfterSaleWarrantyContractStatus(
            contracts.map((item: any) => item.id),
          );
          const statusMap: Record<number, any> = {};
          statusList.forEach((item) => {
            statusMap[item.contractId] = item;
          });
          return {
            options: contracts.map((item: any) => {
              const exists = statusMap[item.id];
              return {
                label: exists
                  ? `${item.name}（已创建质保档案${
                      exists.warrantyNo ? `：${exists.warrantyNo}` : ''
                    }）`
                  : item.name,
                value: item.id,
                disabled: !!exists,
              };
            }),
            placeholder: '请选择合同（已创建质保档案的合同不可重复选择）',
            allowClear: true,
            showSearch: true,
          } as any;
        },
      },
    },
    {
      component: 'Input',
      fieldName: 'projectName',
      label: '工程项目名称',
      rules: 'required',
      componentProps: {
        placeholder: '请输入工程项目名称',
        allowClear: true,
        maxLength: 128,
      },
    },
    {
      component: 'DatePicker',
      fieldName: 'projectAcceptDate',
      label: '完工验收日期',
      rules: 'required',
      componentProps: {
        placeholder: '请选择项目完工验收日期',
        valueFormat: 'YYYY-MM-DD',
        format: 'YYYY-MM-DD',
      },
    },
    {
      component: 'InputNumber',
      fieldName: 'warrantyYears',
      label: '质保年限',
      rules: 'required',
      componentProps: {
        class: '!w-full',
        min: 1,
        max: 5,
        precision: 0,
        placeholder: '请输入质保年限（1-5 年）',
      },
      defaultValue: 1,
    },
    {
      component: 'DatePicker',
      fieldName: 'warrantyEndDate',
      label: '质保到期日期',
      componentProps: {
        placeholder: '留空则按验收日期 + 年限 - 1 天自动计算',
        valueFormat: 'YYYY-MM-DD',
        format: 'YYYY-MM-DD',
      },
    },
    {
      component: 'Input',
      fieldName: 'statusPreview',
      label: '预计质保状态',
      // 只读预览：随「验收日期 / 年限 / 到期日」实时变化。
      // 该字段仅用于界面提示，提交前会被剔除，后端不会接收（SaveReqVO 中无此字段）。
      dependencies: {
        triggerFields: ['projectAcceptDate', 'warrantyYears', 'warrantyEndDate'],
        componentProps(values) {
          return {
            disabled: true,
            placeholder: buildStatusPreviewText(values),
          };
        },
      },
    },
    {
      component: 'ApiSelect',
      fieldName: 'ownerUserId',
      label: '业务负责人',
      componentProps: {
        api: getSimpleUserList,
        labelField: 'nickname',
        valueField: 'id',
        placeholder: '请选择业务负责人',
        allowClear: true,
      },
    },
    {
      component: 'ApiSelect',
      fieldName: 'serviceUserId',
      label: '售后责任人',
      componentProps: {
        api: getSimpleUserList,
        labelField: 'nickname',
        valueField: 'id',
        placeholder: '请选择售后责任人',
        allowClear: true,
      },
    },
    {
      component: 'Select',
      fieldName: 'status',
      label: '质保状态',
      // 只读：状态由「质保到期日期」自动判定，保存时后端会按最新到期日重算并覆盖。
      // 需要人工纠偏（如提前终止质保）请走列表 / 详情页的「修正状态」，那里会记录操作日志。
      componentProps: {
        disabled: true,
        options: getDictOptions(
          DICT_TYPE.CRM_AFTER_SALE_WARRANTY_STATUS,
          'number',
        ),
        placeholder: '由系统按到期日自动判定',
      },
    },
    {
      component: 'Textarea',
      fieldName: 'warrantyScope',
      label: '质保范围',
      componentProps: {
        placeholder: '请输入质保范围（防腐部位/介质环境/责任边界等）',
        rows: 3,
        maxLength: 500,
      },
      formItemClass: 'col-span-2',
    },
    {
      component: 'Textarea',
      fieldName: 'remark',
      label: '备注',
      componentProps: {
        placeholder: '请输入备注',
        rows: 3,
        maxLength: 500,
      },
      formItemClass: 'col-span-2',
    },
  ];
}

/** 列表的搜索表单 */
export function useGridFormSchema(): VbenFormSchema[] {
  return [
    {
      fieldName: 'no',
      label: '质保编号',
      component: 'Input',
      componentProps: {
        placeholder: '请输入质保编号',
        allowClear: true,
      },
    },
    {
      fieldName: 'projectName',
      label: '项目名称',
      component: 'Input',
      componentProps: {
        placeholder: '请输入工程项目名称',
        allowClear: true,
      },
    },
    {
      fieldName: 'customerId',
      label: '客户',
      component: 'ApiSelect',
      componentProps: {
        api: getCustomerSimpleList,
        labelField: 'name',
        valueField: 'id',
        placeholder: '请选择客户',
        allowClear: true,
      },
    },
    {
      fieldName: 'status',
      label: '质保状态',
      component: 'Select',
      componentProps: {
        allowClear: true,
        placeholder: '请选择质保状态',
        options: getDictOptions(
          DICT_TYPE.CRM_AFTER_SALE_WARRANTY_STATUS,
          'number',
        ),
      },
    },
  ];
}

/** 列表的字段 */
export function useGridColumns(): VxeTableGridOptions['columns'] {
  return [
    {
      field: 'no',
      title: '质保编号',
      minWidth: 160,
      fixed: 'left',
    },
    {
      field: 'projectName',
      title: '工程项目名称',
      minWidth: 200,
    },
    {
      field: 'customerName',
      title: '客户名称',
      minWidth: 160,
    },
    {
      field: 'contractNo',
      title: '合同编号',
      minWidth: 160,
    },
    {
      field: 'projectAcceptDate',
      title: '完工验收日期',
      minWidth: 140,
    },
    {
      field: 'warrantyYears',
      title: '质保年限',
      minWidth: 100,
    },
    {
      field: 'warrantyEndDate',
      title: '质保到期日期',
      minWidth: 140,
    },
    {
      field: 'remainDays',
      title: '剩余天数',
      minWidth: 110,
    },
    {
      field: 'status',
      title: '质保状态',
      minWidth: 120,
      cellRender: {
        name: 'CellDict',
        props: { type: DICT_TYPE.CRM_AFTER_SALE_WARRANTY_STATUS },
      },
    },
    {
      field: 'createTime',
      title: '创建时间',
      formatter: 'formatDateTime',
      minWidth: 180,
    },
    {
      title: '操作',
      width: 220,
      fixed: 'right',
      slots: { default: 'actions' },
    },
  ];
}
