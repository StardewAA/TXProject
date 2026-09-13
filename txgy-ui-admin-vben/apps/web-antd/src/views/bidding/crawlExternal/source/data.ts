import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';

import { fetchCrawlParserList } from '#/api/bidding/crawlExternal';

import { CHANNEL_LABEL, channelText, FETCH_TYPE_LABEL, FETCH_TYPE_OPTIONS, fetchTypeText, INTERVAL_LABEL, intervalText, MIN_INTERVAL_SECONDS } from '../labels';
import { fmtDt } from '../shared';

/** 顶部检索表单 */
export function useGridFormSchema(): VbenFormSchema[] {
  return [
    { fieldName: 'sourceName', label: '源名称', component: 'Input', componentProps: { placeholder: '模糊检索', allowClear: true } },
    { fieldName: 'sourceCode', label: '源 code', component: 'Input', componentProps: { placeholder: '精确匹配', allowClear: true } },
    { fieldName: 'enabled', label: '状态', component: 'Select', componentProps: { options: [{ label: '已启用', value: true }, { label: '停用', value: false }], allowClear: true, placeholder: '全部' } },
  ];
}

/** 新增/编辑采集源表单（编辑时 sourceCode 由页面层锁定不可改） */
export function useFormSchema(isEdit: boolean): VbenFormSchema[] {
  return [
    {
      fieldName: 'id',
      component: 'Input',
      dependencies: { triggerFields: [''], show: () => false },
    },
    {
      fieldName: 'sourceName',
      label: '源名称',
      component: 'Input',
      componentProps: { placeholder: '如：中国采购与招标网' },
      rules: 'required',
    },
    {
      fieldName: 'sourceCode',
      label: '源 code（唯一）',
      component: 'Input',
      componentProps: { placeholder: '如 EBIDDING，一旦创建不可修改', disabled: isEdit },
      rules: 'required',
    },
    {
      fieldName: 'baseUrl',
      label: '站点域名/入口',
      component: 'Input',
      componentProps: { placeholder: 'https://example.com', type: 'url' },
      rules: 'required',
    },
    {
      fieldName: 'channelCode',
      label: '下载通道适配器',
      component: 'Select',
      componentProps: { options: [{ label: 'GENERIC · 通用 HTTP 下载器', value: 'GENERIC' }] },
      rules: 'required',
    },
    {
      fieldName: 'fetchType',
      label: '获取方式',
      component: 'Select',
      componentProps: {
        options: [
          ...FETCH_TYPE_OPTIONS.map((o) => ({ ...o })),
        ],
        placeholder: '默认 CRAWLER（存量兼容）',
      },
      // 05-外部信息获取改造-前端跟进 §三：fetchType=CRAWLER/API；后端缺省 CRAWLER
    },
    {
      fieldName: 'apiConfigJson',
      label: 'API 接口配置（JSON）',
      component: 'Textarea',
      componentProps: {
        rows: 8,
        placeholder:
          'fetchType=API 时必填：method/url/分页参数/rowsPath 按对方平台接口声明；headers 静态鉴权头；mapping/urlPrefix 可选（后端 05-外部信息获取改造 §三）',
      },
      dependencies: {
        triggerFields: ['fetchType'],
        if: (v: Record<string, unknown>) => (v.fetchType ?? 'CRAWLER') === 'API',
        required: (v: Record<string, unknown>) => (v.fetchType ?? 'CRAWLER') === 'API',
      },
    },
    {
      fieldName: 'allowedHosts',
      label: '额外放行域名',
      component: 'Input',
      componentProps: { placeholder: '附件 CDN / 文件服务器域名，半角逗号分隔；留空=仅 baseUrl 域' },
    },
    {
      fieldName: 'entryUrlsJson',
      label: '入口/栏目 URL（JSON 数组）',
      component: 'Textarea',
      componentProps: { placeholder: '如 ["/zhaobiao/gglist?p=1"]；留空=从首页链接发现。\n栏目对象可带窗口字段（04-契约 §3）：[{"url":"/zhaobiao/gglist?p=1","lookbackDays":1,"fullLookbackDays":60,"maxPages":10}] —— lookbackDays=每轮增量回溯天（缺省仅最新页）、fullLookbackDays=首跑全量回溯天（缺省回退全局 60）、maxPages=单轮最大翻页', rows: 3, autoSize: { minRows: 3, maxRows: 8 } },
    },
    {
      /* 解析器为后端 ParserRegistry 注册的可插拔 SPI（9 个），code 属系统标识 → 只能选不能手填 */
      fieldName: 'parserCodeDefault',
      label: '默认解析/策略码',
      component: 'ApiSelect',
      componentProps: {
        api: fetchCrawlParserList,
        labelField: 'name',
        valueField: 'code',
        allowClear: true,
        showSearch: true,
        placeholder: '下拉选择解析器；留空=按 URL 规则自动匹配',
      },
    },
    {
      fieldName: 'fetchIntervalSeconds',
      label: INTERVAL_LABEL,
      component: 'InputNumber',
      componentProps: {
        min: MIN_INTERVAL_SECONDS,
        max: 604800,
        addonAfter: '秒',
        // 提示写清归属：这个值同时约束"列表轮询"与"原文下载"（见 labels.ts 文件头）
        placeholder: '默认 3600；下限 120（后端强校验）—— 列表轮询与原文下载共用此值',
      },
    },
    {
      fieldName: 'robotsOk',
      label: '允许按 robots 抓取',
      component: 'Switch',
      componentProps: { checkedChildren: '允许', unCheckedChildren: '禁止' },
    },
    {
      fieldName: 'remark',
      label: '备注',
      component: 'Textarea',
      componentProps: { rows: 2, placeholder: '选填' },
    },
  ];
}

/** 表格列 */
export function useGridColumns(): VxeTableGridOptions['columns'] {
  return [
    { field: 'sourceCode', title: '源 code', width: 120, showOverflow: true },
    { field: 'sourceName', title: '源名称', minWidth: 150, showOverflow: true },
    { field: 'baseUrl', title: '入口站点', minWidth: 200, showOverflow: true },
    { field: 'fetchType', title: FETCH_TYPE_LABEL, width: 170, formatter: ({ cellValue }) => fetchTypeText(cellValue as null | string) },
    { field: 'channelCode', title: CHANNEL_LABEL, width: 170, formatter: ({ cellValue }) => channelText(cellValue as null | string) },
    { field: 'enabled', title: '启用(/go 白名单)', width: 170, slots: { default: 'col_enabled' } },
    {
      field: 'fetchIntervalSeconds',
      title: INTERVAL_LABEL,
      width: 130,
      formatter: ({ cellValue }) => intervalText(cellValue as null | number),
    },
    { field: 'remark', title: '备注', minWidth: 140, showOverflow: true },
    {
      field: 'createTime',
      title: '创建时间',
      width: 160,
      formatter: ({ cellValue }) => fmtDt(cellValue as null | number | string),
    },
    {
      field: 'operation',
      title: '操作',
      width: 210,
      fixed: 'right',
      slots: { default: 'operation' },
    },
  ];
}
