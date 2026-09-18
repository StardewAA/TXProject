import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';

import { DICT_TYPE } from '@vben/constants';
import { useUserStore } from '@vben/stores';
import { erpPriceInputFormatter, erpPriceMultiply } from '@vben/utils';

import { z } from '#/adapter/form';
import { getSimpleBusinessList } from '#/api/crm/business';
import {
  getContactListByCustomer,
  getSimpleContactList,
} from '#/api/crm/contact';
import { getCustomerSimpleList } from '#/api/crm/customer';
import { getSimpleUserList } from '#/api/system/user';

/** 新增/修改的表单 */
export function useFormSchema(): VbenFormSchema[] {
  const userStore = useUserStore();
  return [
    {
      fieldName: 'id',
      component: 'Input',
      dependencies: {
        triggerFields: [''],
        show: () => false,
      },
    },
    {
      fieldName: 'no',
      label: '合同编号',
      component: 'Input',
      componentProps: {
        placeholder: '保存时自动生成',
        disabled: true,
      },
    },
    {
      fieldName: 'name',
      label: '合同名称',
      component: 'Input',
      rules: 'required',
      componentProps: {
        placeholder: '请输入合同名称',
      },
    },
    {
      fieldName: 'ownerUserId',
      label: '负责人',
      component: 'ApiSelect',
      componentProps: {
        api: getSimpleUserList,
        labelField: 'nickname',
        valueField: 'id',
      },
      dependencies: {
        triggerFields: ['id'],
        disabled: (values) => values.id,
      },
      defaultValue: userStore.userInfo?.id,
      rules: 'required',
    },
    {
      fieldName: 'customerId',
      label: '客户名称',
      component: 'ApiSelect',
      rules: 'required',
      componentProps: (_values, form) => ({
        api: getCustomerSimpleList,
        labelField: 'name',
        valueField: 'id',
        placeholder: '请选择客户',
        // 一键引用回填：选择客户后，自动带出该客户的主要联系人为签约联系人
        async onChange(customerId: number) {
          if (!customerId) {
            return;
          }
          try {
            // 可选增强：客户无数据权限时静默降级，不弹全局错误提示
            const contacts = await getContactListByCustomer(customerId, true);
            const mainContact =
              contacts?.find((item) => item.master) ?? contacts?.[0];
            if (mainContact) {
              form.setFieldValue('signContactId', mainContact.id);
            }
          } catch {
            // 客户暂无联系人时忽略，不影响正常填写
          }
        },
      }),
    },
    {
      fieldName: 'businessId',
      label: '商机名称',
      component: 'Select',
      componentProps: {
        options: [],
        placeholder: '请选择商机',
      },
      dependencies: {
        triggerFields: ['customerId'],
        disabled: (values) => !values.customerId,
        async componentProps(values) {
          if (!values.customerId) {
            return {
              options: [],
              placeholder: '请选择客户',
            };
          }
          const res = await getSimpleBusinessList();
          const list = res.filter(
            (item) => item.customerId === values.customerId,
          );
          return {
            options: list.map((item) => ({
              label: item.name,
              value: item.id,
            })),
            placeholder: '请选择商机',
          };
        },
      },
    },
    {
      component: 'Divider',
      fieldName: 'templateDivider',
      label: '',
      renderComponentContent: () => ({ default: () => ['合同模板与项目信息'] }),
      formItemClass: 'col-span-3',
    },
    {
      fieldName: 'templateType',
      label: '合同模板',
      component: 'Select',
      defaultValue: 1,
      rules: 'required',
      componentProps: {
        options: [
          { label: '采购合同（我方为甲方）', value: 1 },
          { label: '购销合同（我方为乙方）', value: 2 },
        ],
        placeholder: '请选择合同模板',
      },
    },
    {
      fieldName: 'signPlace',
      label: '签订地点',
      component: 'Input',
      componentProps: { placeholder: '请输入签订地点' },
    },
    {
      fieldName: 'signDate',
      label: '签订日期',
      component: 'DatePicker',
      componentProps: {
        showTime: false,
        format: 'YYYY-MM-DD',
        valueFormat: 'x',
        placeholder: '请选择签订日期',
      },
    },
    {
      fieldName: 'projectNo',
      label: '项目编号',
      component: 'Input',
      componentProps: { placeholder: '请输入项目编号' },
      dependencies: {
        triggerFields: ['templateType'],
        show: (values) => values.templateType !== 2,
      },
    },
    {
      fieldName: 'projectName',
      label: '项目名称',
      component: 'Input',
      componentProps: { placeholder: '采购/建设项目名称' },
    },
    {
      fieldName: 'projectArea',
      label: '地名',
      component: 'Input',
      componentProps: { placeholder: '如：汶川' },
      dependencies: {
        triggerFields: ['templateType'],
        show: (values) => values.templateType === 2,
      },
    },
    {
      fieldName: 'attachmentCopies',
      label: '附件份数',
      component: 'InputNumber',
      componentProps: { class: '!w-full', min: 0, placeholder: '附件份数' },
      dependencies: {
        triggerFields: ['templateType'],
        show: (values) => values.templateType === 2,
      },
    },
    {
      fieldName: 'attachmentPages',
      label: '附件页数',
      component: 'InputNumber',
      componentProps: { class: '!w-full', min: 0, placeholder: '附件页数' },
      dependencies: {
        triggerFields: ['templateType'],
        show: (values) => values.templateType === 2,
      },
    },
    {
      fieldName: 'contractCopies',
      label: '合同份数',
      component: 'InputNumber',
      componentProps: { class: '!w-full', min: 1, placeholder: '合同份数' },
    },
    {
      fieldName: 'gpcName',
      label: '政府采购中心',
      component: 'Input',
      componentProps: { placeholder: '如：四川' },
      dependencies: {
        triggerFields: ['templateType'],
        show: (values) => values.templateType !== 2,
      },
    },
    {
      fieldName: 'orderDate',
      label: '下单日期',
      component: 'DatePicker',
      rules: 'required',
      componentProps: {
        showTime: false,
        format: 'YYYY-MM-DD',
        valueFormat: 'x',
        placeholder: '请选择下单日期',
      },
    },
    {
      fieldName: 'startTime',
      label: '合同开始时间',
      component: 'DatePicker',
      componentProps: {
        showTime: false,
        format: 'YYYY-MM-DD',
        valueFormat: 'x',
        placeholder: '请选择合同开始时间',
      },
    },
    {
      fieldName: 'endTime',
      label: '合同结束时间',
      component: 'DatePicker',
      componentProps: {
        showTime: false,
        format: 'YYYY-MM-DD',
        valueFormat: 'x',
        placeholder: '请选择合同结束时间',
      },
    },
    {
      fieldName: 'signUserId',
      label: '公司签约人',
      component: 'ApiSelect',
      componentProps: {
        api: getSimpleUserList,
        labelField: 'nickname',
        valueField: 'id',
      },
      defaultValue: userStore.userInfo?.id,
    },
    {
      fieldName: 'signContactId',
      label: '客户签约人',
      component: 'Select',
      componentProps: {
        options: [],
        placeholder: '请选择客户签约人',
      },
      dependencies: {
        triggerFields: ['customerId'],
        disabled: (values) => !values.customerId,
        async componentProps(values) {
          if (!values.customerId) {
            return {
              options: [],
              placeholder: '请选择客户',
            };
          }
          const res = await getSimpleContactList();
          const list = res.filter(
            (item) => item.customerId === values.customerId,
          );
          return {
            options: list.map((item) => ({
              label: item.name,
              value: item.id,
            })),
            placeholder: '请选择客户签约人',
          };
        },
      },
    },
    {
      component: 'Divider',
      fieldName: 'partyADivider',
      label: '',
      renderComponentContent: () => ({ default: () => ['甲方（采购人）信息'] }),
      formItemClass: 'col-span-3',
    },
    {
      fieldName: 'partyAName',
      label: '甲方名称',
      component: 'Input',
      componentProps: { placeholder: '采购人（甲方）单位名称' },
    },
    {
      fieldName: 'partyALegalPerson',
      label: '甲方法定代表人',
      component: 'Input',
      componentProps: { placeholder: '请输入法定代表人' },
    },
    {
      fieldName: 'partyAAgent',
      label: '甲方授权代表',
      component: 'Input',
      componentProps: { placeholder: '授权代表/授权委托人' },
    },
    {
      fieldName: 'partyAAddress',
      label: '甲方地址',
      component: 'Input',
      componentProps: { placeholder: '请输入地址' },
    },
    {
      fieldName: 'partyABankName',
      label: '甲方开户银行',
      component: 'Input',
      componentProps: { placeholder: '请输入开户银行' },
    },
    {
      fieldName: 'partyABankAccount',
      label: '甲方银行账号',
      component: 'Input',
      componentProps: { placeholder: '请输入银行账号' },
    },
    {
      fieldName: 'partyATel',
      label: '甲方电话',
      component: 'Input',
      componentProps: { placeholder: '请输入电话' },
    },
    {
      fieldName: 'partyAFax',
      label: '甲方传真',
      component: 'Input',
      componentProps: { placeholder: '请输入传真' },
    },
    {
      component: 'Divider',
      fieldName: 'partyBDivider',
      label: '',
      renderComponentContent: () => ({ default: () => ['乙方（供应商）信息'] }),
      formItemClass: 'col-span-3',
    },
    {
      fieldName: 'partyBName',
      label: '乙方名称',
      component: 'Input',
      componentProps: { placeholder: '供应商（乙方）单位名称' },
    },
    {
      fieldName: 'partyBLegalPerson',
      label: '乙方法定代表人',
      component: 'Input',
      componentProps: { placeholder: '请输入法定代表人' },
    },
    {
      fieldName: 'partyBAgent',
      label: '乙方授权代表',
      component: 'Input',
      componentProps: { placeholder: '授权代表/授权委托人' },
    },
    {
      fieldName: 'partyBAddress',
      label: '乙方地址',
      component: 'Input',
      componentProps: { placeholder: '请输入地址' },
    },
    {
      fieldName: 'partyBBankName',
      label: '乙方开户银行',
      component: 'Input',
      componentProps: { placeholder: '请输入开户银行' },
    },
    {
      fieldName: 'partyBBankAccount',
      label: '乙方银行账号',
      component: 'Input',
      componentProps: { placeholder: '请输入银行账号' },
    },
    {
      fieldName: 'partyBTel',
      label: '乙方电话',
      component: 'Input',
      componentProps: { placeholder: '请输入电话' },
    },
    {
      fieldName: 'partyBFax',
      label: '乙方传真',
      component: 'Input',
      componentProps: { placeholder: '请输入传真' },
    },
    {
      component: 'Divider',
      fieldName: 'deliveryDivider',
      label: '',
      renderComponentContent: () => ({ default: () => ['交货与验收条款'] }),
      formItemClass: 'col-span-3',
    },
    {
      fieldName: 'qualityStandard',
      label: '质量标准',
      component: 'Input',
      componentProps: { placeholder: '国家（行业）标准，如：国家' },
    },
    {
      fieldName: 'sampleDays',
      label: '送样天数',
      component: 'InputNumber',
      componentProps: { class: '!w-full', min: 0, placeholder: '日内送交样品' },
    },
    {
      fieldName: 'deliveryDays',
      label: '交货期限天数',
      component: 'InputNumber',
      componentProps: { class: '!w-full', min: 0, placeholder: '合同签订生效后' },
    },
    {
      fieldName: 'deliveryArriveDays',
      label: '到货天数',
      component: 'InputNumber',
      componentProps: { class: '!w-full', min: 0, placeholder: '天内交货到指定地点' },
    },
    {
      fieldName: 'installDays',
      label: '安装调试天数',
      component: 'InputNumber',
      componentProps: { class: '!w-full', min: 0, placeholder: '日内完成安装调试' },
    },
    {
      fieldName: 'deliveryDeadline',
      label: '最迟交付日期',
      component: 'DatePicker',
      componentProps: {
        showTime: false,
        format: 'YYYY-MM-DD',
        valueFormat: 'x',
        placeholder: '最迟完成安装调试验收日期',
      },
    },
    {
      fieldName: 'firstAcceptDays',
      label: '初步验收天数',
      component: 'InputNumber',
      componentProps: { class: '!w-full', min: 0 },
    },
    {
      fieldName: 'trialDays',
      label: '试用期天数',
      component: 'InputNumber',
      componentProps: { class: '!w-full', min: 0 },
      dependencies: {
        triggerFields: ['templateType'],
        show: (values) => values.templateType !== 2,
      },
    },
    {
      fieldName: 'finalAcceptDays',
      label: '最终验收天数',
      component: 'InputNumber',
      componentProps: { class: '!w-full', min: 0 },
    },
    {
      fieldName: 'autoAcceptDays',
      label: '视同验收天数',
      component: 'InputNumber',
      componentProps: { class: '!w-full', min: 0, placeholder: '甲方不验收视同合格' },
    },
    {
      fieldName: 'maxRepairTimes',
      label: '维修次数上限',
      component: 'InputNumber',
      componentProps: { class: '!w-full', min: 0, placeholder: '达到则可退货' },
    },
    {
      component: 'Divider',
      fieldName: 'paymentDivider',
      label: '',
      renderComponentContent: () => ({ default: () => ['付款方式'] }),
      formItemClass: 'col-span-3',
    },
    {
      fieldName: 'prepayRatio',
      label: '首付款比例(%)',
      component: 'InputNumber',
      componentProps: { class: '!w-full', min: 0, max: 100, precision: 2 },
    },
    {
      fieldName: 'prepayAmount',
      label: '首付款金额',
      component: 'InputNumber',
      componentProps: { class: '!w-full', min: 0, precision: 2 },
    },
    {
      fieldName: 'prepayDays',
      label: '首付支付天数',
      component: 'InputNumber',
      componentProps: { class: '!w-full', min: 0 },
    },
    {
      fieldName: 'acceptPayRatio',
      label: '验收款比例(%)',
      component: 'InputNumber',
      componentProps: { class: '!w-full', min: 0, max: 100, precision: 2 },
    },
    {
      fieldName: 'acceptPayAmount',
      label: '验收款金额',
      component: 'InputNumber',
      componentProps: { class: '!w-full', min: 0, precision: 2 },
    },
    {
      fieldName: 'acceptPayDays',
      label: '验收款支付天数',
      component: 'InputNumber',
      componentProps: { class: '!w-full', min: 0, placeholder: '购销合同为工作日' },
    },
    {
      fieldName: 'depositRatio',
      label: '保证金比例(%)',
      component: 'InputNumber',
      componentProps: { class: '!w-full', min: 0, max: 100, precision: 2 },
    },
    {
      fieldName: 'depositAmount',
      label: '保证金金额',
      component: 'InputNumber',
      componentProps: { class: '!w-full', min: 0, precision: 2 },
    },
    {
      fieldName: 'depositMonths',
      label: '保证金月数',
      component: 'InputNumber',
      componentProps: { class: '!w-full', min: 0, placeholder: '验收合格满N个月后退还' },
      dependencies: {
        triggerFields: ['templateType'],
        show: (values) => values.templateType !== 2,
      },
    },
    {
      fieldName: 'depositDays',
      label: '保证金退还天数',
      component: 'InputNumber',
      componentProps: { class: '!w-full', min: 0 },
    },
    {
      component: 'Divider',
      fieldName: 'afterSaleDivider',
      label: '',
      renderComponentContent: () => ({ default: () => ['售后服务'] }),
      formItemClass: 'col-span-3',
    },
    {
      fieldName: 'warrantyYears',
      label: '质保年数',
      component: 'InputNumber',
      componentProps: { class: '!w-full', min: 0 },
    },
    {
      fieldName: 'responseHours',
      label: '响应小时数',
      component: 'InputNumber',
      componentProps: { class: '!w-full', min: 0, placeholder: '小时内响应到场' },
    },
    {
      fieldName: 'repairHours',
      label: '维修小时数',
      component: 'InputNumber',
      componentProps: { class: '!w-full', min: 0, placeholder: '小时内完成维修' },
    },
    {
      fieldName: 'repairTimes',
      label: '维修次数',
      component: 'InputNumber',
      componentProps: { class: '!w-full', min: 0 },
    },
    {
      fieldName: 'reportRepairDays',
      label: '报修天数',
      component: 'InputNumber',
      componentProps: { class: '!w-full', min: 0, placeholder: '报修天内无法解决则提供代用品' },
      dependencies: {
        triggerFields: ['templateType'],
        show: (values) => values.templateType === 2,
      },
    },
    {
      fieldName: 'remark',
      label: '备注',
      component: 'Textarea',
      componentProps: {
        placeholder: '请输入备注',
        rows: 4,
      },
    },
    {
      fieldName: 'product',
      label: '产品清单',
      component: 'Input',
      formItemClass: 'col-span-3',
    },
    {
      fieldName: 'totalProductPrice',
      label: '产品总金额',
      component: 'InputNumber',
      componentProps: {
        class: '!w-full',
        min: 0,
        precision: 2,
        placeholder: '请输入产品总金额',
      },
      rules: z.number().min(0).optional().default(0),
    },
    {
      fieldName: 'discountPercent',
      label: '整单折扣（%）',
      component: 'InputNumber',
      componentProps: {
        class: '!w-full',
        min: 0,
        precision: 2,
        placeholder: '请输入整单折扣',
      },
      rules: z.number().min(0).max(100).optional().default(0),
    },
    {
      fieldName: 'totalPrice',
      label: '折扣后金额',
      component: 'InputNumber',
      componentProps: {
        class: '!w-full',
        min: 0,
        precision: 2,
        disabled: true,
      },
      dependencies: {
        triggerFields: ['totalProductPrice', 'discountPercent'],
        trigger(values, form) {
          const discountPrice =
            erpPriceMultiply(
              values.totalProductPrice,
              values.discountPercent / 100,
            ) ?? 0;
          form.setFieldValue(
            'totalPrice',
            values.totalProductPrice - discountPrice,
          );
        },
      },
    },
  ];
}

/** 列表的搜索表单 */
export function useGridFormSchema(): VbenFormSchema[] {
  return [
    {
      fieldName: 'no',
      label: '合同编号',
      component: 'Input',
      componentProps: {
        placeholder: '请输入合同编号',
        allowClear: true,
      },
    },
    {
      fieldName: 'name',
      label: '合同名称',
      component: 'Input',
      componentProps: {
        placeholder: '请输入合同名称',
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
  ];
}

export function useGridColumns(): VxeTableGridOptions['columns'] {
  return [
    {
      title: '合同编号',
      field: 'no',
      minWidth: 180,
      fixed: 'left',
    },
    {
      title: '合同名称',
      field: 'name',
      minWidth: 160,
      fixed: 'left',
      slots: { default: 'name' },
    },
    {
      title: '客户名称',
      field: 'customerName',
      minWidth: 120,
      slots: { default: 'customerName' },
    },
    {
      title: '商机名称',
      field: 'businessName',
      minWidth: 130,
      slots: { default: 'businessName' },
    },
    {
      title: '合同金额（元）',
      field: 'totalPrice',
      minWidth: 140,
      formatter: 'formatAmount2',
    },
    {
      title: '下单时间',
      field: 'orderDate',
      minWidth: 120,
      formatter: 'formatDateTime',
    },
    {
      title: '合同开始时间',
      field: 'startTime',
      minWidth: 120,
      formatter: 'formatDateTime',
    },
    {
      title: '合同结束时间',
      field: 'endTime',
      minWidth: 120,
      formatter: 'formatDateTime',
    },
    {
      title: '客户签约人',
      field: 'signContactName',
      minWidth: 130,
      slots: { default: 'signContactName' },
    },
    {
      title: '公司签约人',
      field: 'signUserName',
      minWidth: 130,
    },
    {
      title: '备注',
      field: 'remark',
      minWidth: 200,
    },
    {
      title: '已回款金额（元）',
      field: 'totalReceivablePrice',
      minWidth: 140,
      formatter: 'formatAmount2',
    },
    {
      title: '未回款金额（元）',
      field: 'unReceivablePrice',
      minWidth: 140,
      formatter: ({ row }) => {
        return erpPriceInputFormatter(
          row.totalPrice - row.totalReceivablePrice,
        );
      },
    },
    {
      title: '最后跟进时间',
      field: 'contactLastTime',
      minWidth: 180,
      formatter: 'formatDateTime',
    },
    {
      title: '负责人',
      field: 'ownerUserName',
      minWidth: 120,
    },
    {
      title: '所属部门',
      field: 'ownerUserDeptName',
      minWidth: 100,
    },
    {
      title: '更新时间',
      field: 'updateTime',
      minWidth: 180,
      formatter: 'formatDateTime',
    },
    {
      title: '创建时间',
      field: 'createTime',
      minWidth: 180,
      formatter: 'formatDateTime',
    },
    {
      title: '创建人',
      field: 'creatorName',
      minWidth: 120,
    },
    {
      title: '合同状态',
      field: 'auditStatus',
      fixed: 'right',
      minWidth: 120,
      cellRender: {
        name: 'CellDict',
        props: { type: DICT_TYPE.CRM_AUDIT_STATUS },
      },
    },
    {
      title: '操作',
      field: 'actions',
      fixed: 'right',
      minWidth: 130,
      slots: { default: 'actions' },
    },
  ];
}
