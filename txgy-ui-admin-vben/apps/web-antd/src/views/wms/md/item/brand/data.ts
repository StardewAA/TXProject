import type { VbenFormApi, VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';

import { h, markRaw } from 'vue';

import { generateWmsCode } from '@vben/constants';

import { Button } from 'ant-design-vue';

import { z } from '#/adapter/form';
import { FileUpload } from '#/components/upload';
import { WmsItemCategorySelect } from '#/views/wms/md/item/category/components';
import { WmsMerchantSelect } from '#/views/wms/md/merchant/components';

/** 新增/修改商品品牌的表单 */
export function useFormSchema(formApi?: VbenFormApi): VbenFormSchema[] {
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
      fieldName: 'code',
      label: '品牌编号',
      component: 'Input',
      componentProps: {
        maxLength: 20,
        placeholder: '请输入品牌编号',
      },
      rules: z.string().min(1, '品牌编号不能为空').max(20),
      suffix: () => {
        return h(
          Button,
          {
            type: 'default',
            onClick: () => {
              formApi?.setFieldValue('code', generateWmsCode('B'));
            },
          },
          { default: () => '生成' },
        );
      },
    },
    {
      fieldName: 'name',
      label: '品牌名称',
      component: 'Input',
      componentProps: {
        maxLength: 30,
        placeholder: '请输入品牌名称',
      },
      rules: z.string().min(1, '品牌名称不能为空').max(30),
    },
    {
      fieldName: 'categoryId',
      label: '关联商品分类',
      component: markRaw(WmsItemCategorySelect),
      help: '该品牌适用的商品分类，如「FL01 防腐涂料」',
    },
    {
      fieldName: 'supplierId',
      label: '主要供应商',
      component: markRaw(WmsMerchantSelect),
      componentProps: {
        supplier: true,
        placeholder: '请选择主要供应商',
      },
      help: '用于品控溯源，对应往来企业中的供应商',
    },
    {
      fieldName: 'description',
      label: '品牌说明',
      component: 'Textarea',
      componentProps: {
        maxLength: 500,
        rows: 3,
        placeholder: '请输入品牌说明',
      },
      formItemClass: 'col-span-2',
    },
    {
      fieldName: 'qualificationFiles',
      label: '资质文件',
      component: markRaw(FileUpload),
      componentProps: {
        accept: ['pdf'],
        maxNumber: 10,
        maxSize: 20,
        multiple: true,
        directory: 'wms/brand-qualification',
        showDescription: true,
      },
      formItemClass: 'col-span-2',
      help: '支持上传品牌授权书、质量保证书、行业资质证书等 PDF 文件',
    },
  ];
}

/** 列表的搜索表单 */
export function useGridFormSchema(): VbenFormSchema[] {
  return [
    {
      fieldName: 'code',
      label: '品牌编号',
      component: 'Input',
      componentProps: {
        allowClear: true,
        placeholder: '请输入品牌编号',
      },
    },
    {
      fieldName: 'name',
      label: '品牌名称',
      component: 'Input',
      componentProps: {
        allowClear: true,
        placeholder: '请输入品牌名称',
      },
    },
  ];
}

/** 列表的字段 */
export function useGridColumns(): VxeTableGridOptions['columns'] {
  return [
    {
      field: 'code',
      title: '品牌编号',
      width: 120,
    },
    {
      field: 'name',
      title: '品牌名称',
      minWidth: 140,
    },
    {
      field: 'categoryName',
      title: '关联商品分类',
      minWidth: 140,
    },
    {
      field: 'supplierName',
      title: '主要供应商',
      minWidth: 180,
    },
    {
      field: 'description',
      title: '品牌说明',
      minWidth: 200,
    },
    {
      field: 'qualificationFiles',
      title: '资质文件',
      minWidth: 200,
      slots: { default: 'qualificationFiles' },
    },
    {
      field: 'createTime',
      title: '创建时间',
      width: 180,
      formatter: 'formatDateTime',
    },
    {
      title: '操作',
      width: 160,
      fixed: 'right',
      slots: { default: 'actions' },
    },
  ];
}
