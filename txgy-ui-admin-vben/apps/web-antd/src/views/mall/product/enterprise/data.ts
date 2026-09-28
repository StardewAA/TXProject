import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import { getRangePickerDefaultProps } from '#/utils';
export function useFormSchema(): VbenFormSchema[] { return [
 { component:'Input', fieldName:'id', dependencies:{ triggerFields:[''], show:()=>false } },
 { fieldName:'name', label:'企业名称', component:'Input', rules:'required' },
 { fieldName:'creditCode', label:'统一社会信用代码', component:'Input', rules:'required' },
 { fieldName:'licenseUrl', label:'营业执照', component:'ImageUpload', componentProps:{ maxNumber:1 } },
 { fieldName:'contactName', label:'联系人', component:'Input' }, { fieldName:'contactMobile', label:'联系电话', component:'Input' },
 { fieldName:'address', label:'企业地址', component:'Input' }, { fieldName:'introduction', label:'企业简介', component:'Textarea' },
 ]; }
export function useGridFormSchema(): VbenFormSchema[] { return [ {fieldName:'name',label:'企业名称',component:'Input'}, {fieldName:'creditCode',label:'统一社会信用代码',component:'Input'}, {fieldName:'auditStatus',label:'审核状态',component:'Select',componentProps:{options:[{label:'待审核',value:0},{label:'已通过',value:1},{label:'已驳回',value:2},{label:'已停用',value:3}],allowClear:true}}, {fieldName:'createTime',label:'创建时间',component:'RangePicker',componentProps:{...getRangePickerDefaultProps(),allowClear:true}} ]; }
export function useGridColumns(): VxeTableGridOptions['columns'] { return [{field:'name',title:'企业名称',minWidth:180},{field:'creditCode',title:'统一社会信用代码',minWidth:180},{field:'contactName',title:'联系人',width:100},{field:'contactMobile',title:'联系电话',width:140},{field:'auditStatus',title:'审核状态',width:100,formatter:({cellValue})=>['待审核','已通过','已驳回','已停用'][cellValue]??'-'},{field:'createTime',title:'创建时间',width:180,formatter:'formatDateTime'},{title:'操作',width:220,fixed:'right',slots:{default:'actions'}}]; }
