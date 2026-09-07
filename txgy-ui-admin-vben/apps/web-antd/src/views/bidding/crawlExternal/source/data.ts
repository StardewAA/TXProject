import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';

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
      fieldName: 'allowedHosts',
      label: '额外放行域名',
      component: 'Input',
      componentProps: { placeholder: '附件 CDN / 文件服务器域名，半角逗号分隔；留空=仅 baseUrl 域' },
    },
    {
      fieldName: 'entryUrlsJson',
      label: '入口/栏目 URL（JSON 数组）',
      component: 'Textarea',
      componentProps: { placeholder: '如 ["/zhaobiao/gglist?p=1"]；留空=从首页链接发现', rows: 3, autoSize: { minRows: 3, maxRows: 6 } },
    },
    {
      fieldName: 'parserCodeDefault',
      label: '默认解析/策略码',
      component: 'Input',
      componentProps: { placeholder: '留空=按 URL 规则自动匹配解析器' },
    },
    {
      fieldName: 'fetchIntervalSeconds',
      label: '抓取间隔（秒）',
      component: 'InputNumber',
      componentProps: { min: 60, max: 604800, addonAfter: '秒', placeholder: '默认 3600' },
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
    { field: 'channelCode', title: '下载通道', width: 110 },
    { field: 'enabled', title: '启用(/go 白名单)', width: 170, slots: { default: 'col_enabled' } },
    {
      field: 'fetchIntervalSeconds',
      title: '抓取间隔',
      width: 110,
      formatter: ({ cellValue }) => (cellValue == null ? '—' : String(cellValue) + ' s'),
    },
    { field: 'remark', title: '备注', minWidth: 140, showOverflow: true },
    {
      field: 'createTime',
      title: '创建时间',
      width: 160,
      formatter: ({ cellValue }) => fmtDt(cellValue as number | string | null),
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
