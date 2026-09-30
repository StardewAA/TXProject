import type { VbenFormSchema } from '#/adapter/form';

/** 天数类输入框 */
function daysField(fieldName: string, label: string, help?: string) {
  return {
    fieldName,
    label,
    component: 'InputNumber',
    componentProps: {
      class: '!w-full',
      min: 0,
      precision: 0,
      ...(help ? { placeholder: help } : {}),
    },
  } as VbenFormSchema;
}

/** 比例类输入框（%） */
function ratioField(fieldName: string, label: string, help?: string) {
  return {
    fieldName,
    label,
    component: 'InputNumber',
    componentProps: {
      class: '!w-full',
      min: 0,
      precision: 2,
      ...(help ? { placeholder: help } : {}),
    },
  } as VbenFormSchema;
}

/** 文本类输入框 */
function textField(fieldName: string, label: string, help?: string) {
  return {
    fieldName,
    label,
    component: 'Input',
    componentProps: {
      ...(help ? { placeholder: help } : {}),
    },
  } as VbenFormSchema;
}

/** 分组标题 */
function divider(fieldName: string, label: string) {
  return {
    component: 'Divider',
    fieldName,
    label: '',
    renderComponentContent: () => ({ default: () => [label] }),
    formItemClass: 'col-span-3',
  } as VbenFormSchema;
}

export const schema: VbenFormSchema[] = [
  divider('notifyDivider', '合同到期提醒'),
  {
    component: 'RadioGroup',
    fieldName: 'notifyEnabled',
    label: '提前提醒设置',
    componentProps: {
      options: [
        { label: '提醒', value: true },
        { label: '不提醒', value: false },
      ],
    },
    defaultValue: true,
  },
  {
    component: 'InputNumber',
    fieldName: 'notifyDays',
    label: '提醒天数',
    componentProps: {
      class: '!w-full',
      min: 0,
      precision: 0,
    },
    renderComponentContent: () => ({
      addonBefore: () => '提前',
      addonAfter: () => '天提醒',
    }),
    dependencies: {
      triggerFields: ['notifyEnabled'],
      show: (values) => values.notifyEnabled,
      trigger(values) {
        if (!values.notifyEnabled) {
          values.notifyDays = undefined;
        }
      },
    },
  },

  // ==================== 我方主体信息 ====================
  divider('ourDivider', '我方主体信息（合同签署方）'),
  textField('ourCompanyName', '公司名称', '我方公司全称'),
  textField('ourLegalPerson', '法定代表人', '请输入法定代表人'),
  textField('ourAgent', '授权代表', '授权代表/委托代理人'),
  textField('ourAddress', '公司地址', '请输入地址'),
  textField('ourBankName', '开户银行', '如：中国工商银行xx支行'),
  textField('ourBankAccount', '银行账号', '请输入银行账号'),
  textField('ourTel', '电话', '请输入电话'),
  textField('ourFax', '传真', '请输入传真'),

  // ==================== 条款默认值 ====================
  divider('clauseDivider', '条款默认值（新建合同自动带出）'),
  textField('defaultGpcName', '政府采购中心', '如：四川'),
  textField('defaultQualityStandard', '质量标准', '如：国家（行业）标准'),
  daysField('defaultSampleDays', '送样天数'),
  daysField('defaultDeliveryDays', '交货期限天数'),
  daysField('defaultDeliveryArriveDays', '到货天数'),
  daysField('defaultInstallDays', '安装调试验收天数'),
  daysField('defaultFirstAcceptDays', '初步验收天数'),
  daysField('defaultTrialDays', '试用期天数'),
  daysField('defaultFinalAcceptDays', '最终验收天数'),
  daysField('defaultAutoAcceptDays', '视同验收天数'),
  daysField('defaultMaxRepairTimes', '维修次数上限'),
  daysField('defaultContractCopies', '合同份数'),
  ratioField('defaultPrepayRatio', '首付款比例(%)'),
  daysField('defaultPrepayDays', '首付支付天数'),
  ratioField('defaultAcceptPayRatio', '验收款比例(%)'),
  daysField('defaultAcceptPayDays', '验收款支付天数'),
  ratioField('defaultDepositRatio', '保证金比例(%)'),
  daysField('defaultDepositMonths', '保证金月数'),
  daysField('defaultDepositDays', '保证金退还天数'),
  daysField('defaultWarrantyYears', '质保年数'),
  daysField('defaultResponseHours', '响应小时数'),
  daysField('defaultRepairHours', '维修小时数'),
  daysField('defaultRepairTimes', '维修次数'),
  daysField('defaultReportRepairDays', '报修天数'),

  // ==================== 采购合同违约责任 ====================
  divider(
    'purchasePenaltyDivider',
    '违约责任默认值（采购合同：我方为甲方）',
  ),
  ratioField('pBuyerRejectRatio', '甲方拒收违约金比例(%)'),
  ratioField('pBuyerOverdueWan', '甲方逾期付款违约金(万分之/天)'),
  daysField('pBuyerOverdueDays', '甲方逾期可终止天数'),
  ratioField('pSellerQuality', '乙方质量不合格违约金比例(%)'),
  ratioField('pSellerOverdueWan', '乙方逾期交货违约金(万分之/天)'),
  daysField('pSellerOverdueDays', '乙方逾期可终止天数'),
  ratioField('pSellerCompensate', '乙方终止合同赔偿比例(%)'),
  daysField('pSellerReplaceDays', '检测不合格更换天数'),
  ratioField('pSellerTerminate', '逾期未更换赔偿比例(%)'),
  ratioField('pSellerDefect', '权利瑕疵违约金比例(%)'),

  // ==================== 购销合同违约责任 ====================
  divider('salesPenaltyDivider', '违约责任默认值（购销合同：我方为乙方）'),
  ratioField('sBuyerLateRatio', '甲方逾期滞纳金比例(%/日)'),
  ratioField('sBuyerBreachRatio', '甲方违约金比例(%)'),
  ratioField('sSellerLateRatio', '乙方逾期滞纳金比例(%/日)'),
  ratioField('sSellerBreachRatio', '乙方违约金比例(%)'),
];
