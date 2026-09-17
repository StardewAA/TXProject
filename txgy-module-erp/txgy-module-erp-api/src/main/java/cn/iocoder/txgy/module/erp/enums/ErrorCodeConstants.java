package cn.iocoder.txgy.module.erp.enums;

import cn.iocoder.txgy.framework.common.exception.ErrorCode;

/**
 * ERP 错误码枚举类
 * <p>
 * erp 系统，使用 1-030-000-000 段
 */
public interface ErrorCodeConstants {

    // ========== ERP 供应商（1-030-100-000） ==========
    ErrorCode SUPPLIER_NOT_EXISTS = new ErrorCode(1_030_100_000, "供应商不存在");
    ErrorCode SUPPLIER_NOT_ENABLE = new ErrorCode(1_030_100_001, "供应商({})未启用");
    ErrorCode SUPPLIER_PARTNER_CODE_DUPLICATE = new ErrorCode(1_030_100_002, "供应商统一编码({})已存在");
    ErrorCode SUPPLIER_PARTNER_CODE_IMMUTABLE = new ErrorCode(1_030_100_003, "供应商统一编码创建后不可修改");

    // ========== ERP 采购订单（1-030-101-000） ==========
    ErrorCode PURCHASE_ORDER_NOT_EXISTS = new ErrorCode(1_030_101_000, "采购订单不存在");
    ErrorCode PURCHASE_ORDER_DELETE_FAIL_APPROVE = new ErrorCode(1_030_101_001, "采购订单({})已审核，无法删除");
    ErrorCode PURCHASE_ORDER_PROCESS_FAIL = new ErrorCode(1_030_101_002, "反审核失败，只有已审核的采购订单才能反审核");
    ErrorCode PURCHASE_ORDER_APPROVE_FAIL = new ErrorCode(1_030_101_003, "审核失败，只有未审核的采购订单才能审核");
    ErrorCode PURCHASE_ORDER_NO_EXISTS = new ErrorCode(1_030_101_004, "生成采购单号失败，请重新提交");
    ErrorCode PURCHASE_ORDER_UPDATE_FAIL_APPROVE = new ErrorCode(1_030_101_005, "采购订单({})已审核，无法修改");
    ErrorCode PURCHASE_ORDER_NOT_APPROVE = new ErrorCode(1_030_101_006, "采购订单未审核，无法操作");
    ErrorCode PURCHASE_ORDER_ITEM_IN_FAIL_PRODUCT_EXCEED = new ErrorCode(1_030_101_007, "采购订单项({})超过最大允许入库数量({})");
    ErrorCode PURCHASE_ORDER_PROCESS_FAIL_EXISTS_IN = new ErrorCode(1_030_101_008, "反审核失败，已存在对应的采购入库单");
ErrorCode PURCHASE_ORDER_ITEM_RETURN_FAIL_IN_EXCEED = new ErrorCode(1_030_101_009, "采购订单项({})超过最大允许退货数量({})");
    ErrorCode PURCHASE_ORDER_PROCESS_FAIL_EXISTS_RETURN = new ErrorCode(1_030_101_010, "反审核失败，已存在对应的采购退货单");

    // ========== ERP 商城缺货采购需求（1-030-104-000） ===========
    ErrorCode MALL_PURCHASE_DEMAND_NOT_EXISTS = new ErrorCode(1_030_104_000, "商城缺货采购需求不存在");
    ErrorCode MALL_PURCHASE_DEMAND_REQUEST_CONFLICT = new ErrorCode(1_030_104_001, "商城缺货采购请求与已受理请求不一致");
    ErrorCode MALL_PURCHASE_DEMAND_TENANT_MISMATCH = new ErrorCode(1_030_104_002, "商城缺货采购请求的租户不匹配");
    ErrorCode MALL_PURCHASE_DEMAND_QUANTITY_INVALID = new ErrorCode(1_030_104_003, "商城缺货采购明细({})的数量不合法");
    ErrorCode MALL_PURCHASE_DEMAND_STATUS_INVALID = new ErrorCode(1_030_104_004, "商城缺货采购需求不能从状态({})变更为({})");
    ErrorCode MALL_PURCHASE_DEMAND_RESULT_REASON_REQUIRED = new ErrorCode(1_030_104_005, "商城缺货采购需求状态({})必须填写原因说明");
    ErrorCode PURCHASE_ORDER_SUBMIT_FAIL_STATUS = new ErrorCode(1_030_101_011, "采购订单({})当前状态无法提交审批");
    ErrorCode PURCHASE_ORDER_UPDATE_FAIL_PROCESS = new ErrorCode(1_030_101_012, "采购订单({})正在审批，无法修改");

    // ========== ERP 采购入库（1-030-102-000） ==========
    ErrorCode PURCHASE_IN_NOT_EXISTS = new ErrorCode(1_030_102_000, "采购入库单不存在");
    ErrorCode PURCHASE_IN_DELETE_FAIL_APPROVE = new ErrorCode(1_030_102_001, "采购入库单({})已审核，无法删除");
    ErrorCode PURCHASE_IN_PROCESS_FAIL = new ErrorCode(1_030_102_002, "反审核失败，只有已审核的入库单才能反审核");
    ErrorCode PURCHASE_IN_APPROVE_FAIL = new ErrorCode(1_030_102_003, "审核失败，只有未审核的入库单才能审核");
    ErrorCode PURCHASE_IN_NO_EXISTS = new ErrorCode(1_030_102_004, "生成入库单失败，请重新提交");
    ErrorCode PURCHASE_IN_UPDATE_FAIL_APPROVE = new ErrorCode(1_030_102_005, "采购入库单({})已审核，无法修改");
    ErrorCode PURCHASE_IN_NOT_APPROVE = new ErrorCode(1_030_102_006, "采购入库单未审核，无法操作");
    ErrorCode PURCHASE_IN_FAIL_PAYMENT_PRICE_EXCEED = new ErrorCode(1_030_102_007, "付款金额({})超过采购入库单总金额({})");
    ErrorCode PURCHASE_IN_PROCESS_FAIL_EXISTS_PAYMENT = new ErrorCode(1_030_102_008, "反审核失败，已存在对应的付款单");

    // ========== ERP 采购退货（1-030-103-000） ==========
    ErrorCode PURCHASE_RETURN_NOT_EXISTS = new ErrorCode(1_030_103_000, "采购退货单不存在");
    ErrorCode PURCHASE_RETURN_DELETE_FAIL_APPROVE = new ErrorCode(1_030_103_001, "采购退货单({})已审核，无法删除");
    ErrorCode PURCHASE_RETURN_PROCESS_FAIL = new ErrorCode(1_030_103_002, "反审核失败，只有已审核的退货单才能反审核");
    ErrorCode PURCHASE_RETURN_APPROVE_FAIL = new ErrorCode(1_030_103_003, "审核失败，只有未审核的退货单才能审核");
    ErrorCode PURCHASE_RETURN_NO_EXISTS = new ErrorCode(1_030_103_004, "生成退货单失败，请重新提交");
    ErrorCode PURCHASE_RETURN_UPDATE_FAIL_APPROVE = new ErrorCode(1_030_103_005, "采购退货单({})已审核，无法修改");
    ErrorCode PURCHASE_RETURN_NOT_APPROVE = new ErrorCode(1_030_103_006, "采购退货单未审核，无法操作");
    ErrorCode PURCHASE_RETURN_FAIL_REFUND_PRICE_EXCEED = new ErrorCode(1_030_103_007, "退款金额({})超过采购退货单总金额({})");
    ErrorCode PURCHASE_RETURN_PROCESS_FAIL_EXISTS_REFUND = new ErrorCode(1_030_103_008, "反审核失败，已存在对应的退款单");

    // ========== ERP 客户（1-030-200-000）==========
    ErrorCode CUSTOMER_NOT_EXISTS = new ErrorCode(1_020_200_000, "客户不存在");
    ErrorCode CUSTOMER_NOT_ENABLE = new ErrorCode(1_020_200_001, "客户({})未启用");
    ErrorCode CUSTOMER_PARTNER_CODE_DUPLICATE = new ErrorCode(1_020_200_002, "客户统一编码({})已存在");
    ErrorCode CUSTOMER_PARTNER_CODE_IMMUTABLE = new ErrorCode(1_020_200_003, "客户统一编码创建后不可修改");

    // ========== ERP 销售订单（1-030-201-000） ==========
    ErrorCode SALE_ORDER_NOT_EXISTS = new ErrorCode(1_020_201_000, "销售订单不存在");
    ErrorCode SALE_ORDER_DELETE_FAIL_APPROVE = new ErrorCode(1_020_201_001, "销售订单({})已审核，无法删除");
    ErrorCode SALE_ORDER_PROCESS_FAIL = new ErrorCode(1_020_201_002, "反审核失败，只有已审核的销售订单才能反审核");
    ErrorCode SALE_ORDER_APPROVE_FAIL = new ErrorCode(1_020_201_003, "审核失败，只有未审核的销售订单才能审核");
    ErrorCode SALE_ORDER_NO_EXISTS = new ErrorCode(1_020_201_004, "生成销售单号失败，请重新提交");
    ErrorCode SALE_ORDER_UPDATE_FAIL_APPROVE = new ErrorCode(1_020_201_005, "销售订单({})已审核，无法修改");
    ErrorCode SALE_ORDER_NOT_APPROVE = new ErrorCode(1_020_201_006, "销售订单未审核，无法操作");
    ErrorCode SALE_ORDER_ITEM_OUT_FAIL_PRODUCT_EXCEED = new ErrorCode(1_020_201_007, "销售订单项({})超过最大允许出库数量({})");
    ErrorCode SALE_ORDER_PROCESS_FAIL_EXISTS_OUT = new ErrorCode(1_020_201_008, "反审核失败，已存在对应的销售出库单");
    ErrorCode SALE_ORDER_ITEM_RETURN_FAIL_OUT_EXCEED = new ErrorCode(1_020_201_009, "销售订单项({})超过最大允许退货数量({})");
    ErrorCode SALE_ORDER_PROCESS_FAIL_EXISTS_RETURN = new ErrorCode(1_020_201_010, "反审核失败，已存在对应的销售退货单");
    ErrorCode SALE_ORDER_SUBMIT_FAIL_STATUS = new ErrorCode(1_020_201_011, "销售订单({})当前状态无法提交审批");
    ErrorCode SALE_ORDER_UPDATE_FAIL_PROCESS = new ErrorCode(1_020_201_012, "销售订单({})正在审批，无法修改");

    // ========== ERP 销售出库（1-030-202-000） ==========
    ErrorCode SALE_OUT_NOT_EXISTS = new ErrorCode(1_020_202_000, "销售出库单不存在");
    ErrorCode SALE_OUT_DELETE_FAIL_APPROVE = new ErrorCode(1_020_202_001, "销售出库单({})已审核，无法删除");
    ErrorCode SALE_OUT_PROCESS_FAIL = new ErrorCode(1_020_202_002, "反审核失败，只有已审核的出库单才能反审核");
    ErrorCode SALE_OUT_APPROVE_FAIL = new ErrorCode(1_020_202_003, "审核失败，只有未审核的出库单才能审核");
    ErrorCode SALE_OUT_NO_EXISTS = new ErrorCode(1_020_202_004, "生成出库单失败，请重新提交");
    ErrorCode SALE_OUT_UPDATE_FAIL_APPROVE = new ErrorCode(1_020_202_005, "销售出库单({})已审核，无法修改");
    ErrorCode SALE_OUT_NOT_APPROVE = new ErrorCode(1_020_202_006, "销售出库单未审核，无法操作");
    ErrorCode SALE_OUT_FAIL_RECEIPT_PRICE_EXCEED = new ErrorCode(1_020_202_007, "收款金额({})超过销售出库单总金额({})");
    ErrorCode SALE_OUT_PROCESS_FAIL_EXISTS_RECEIPT = new ErrorCode(1_020_202_008, "反审核失败，已存在对应的收款单");

    // ========== ERP 销售退货（1-030-203-000） ==========
    ErrorCode SALE_RETURN_NOT_EXISTS = new ErrorCode(1_020_203_000, "销售退货单不存在");
    ErrorCode SALE_RETURN_DELETE_FAIL_APPROVE = new ErrorCode(1_020_203_001, "销售退货单({})已审核，无法删除");
    ErrorCode SALE_RETURN_PROCESS_FAIL = new ErrorCode(1_020_203_002, "反审核失败，只有已审核的退货单才能反审核");
    ErrorCode SALE_RETURN_APPROVE_FAIL = new ErrorCode(1_020_203_003, "审核失败，只有未审核的退货单才能审核");
    ErrorCode SALE_RETURN_NO_EXISTS = new ErrorCode(1_020_203_004, "生成退货单失败，请重新提交");
    ErrorCode SALE_RETURN_UPDATE_FAIL_APPROVE = new ErrorCode(1_020_203_005, "销售退货单({})已审核，无法修改");
    ErrorCode SALE_RETURN_NOT_APPROVE = new ErrorCode(1_020_203_006, "销售退货单未审核，无法操作");
    ErrorCode SALE_RETURN_FAIL_REFUND_PRICE_EXCEED = new ErrorCode(1_020_203_007, "退款金额({})超过销售退货单总金额({})");
    ErrorCode SALE_RETURN_PROCESS_FAIL_EXISTS_REFUND = new ErrorCode(1_020_203_008, "反审核失败，已存在对应的退款单");

    // ========== ERP 仓库 1-030-400-000 ==========
    ErrorCode WAREHOUSE_NOT_EXISTS = new ErrorCode(1_030_400_000, "仓库不存在");
    ErrorCode WAREHOUSE_NOT_ENABLE = new ErrorCode(1_030_400_001, "仓库({})未启用");
    ErrorCode WAREHOUSE_WMS_CODE_DUPLICATE = new ErrorCode(1_030_400_002, "WMS 仓库编码({})已绑定其它 ERP 仓库");

    // ========== ERP 其它入库单 1-030-401-000 ==========
    ErrorCode STOCK_IN_NOT_EXISTS = new ErrorCode(1_030_401_000, "其它入库单不存在");
    ErrorCode STOCK_IN_DELETE_FAIL_APPROVE = new ErrorCode(1_030_401_001, "其它入库单({})已审核，无法删除");
    ErrorCode STOCK_IN_PROCESS_FAIL = new ErrorCode(1_030_401_002, "反审核失败，只有已审核的入库单才能反审核");
    ErrorCode STOCK_IN_APPROVE_FAIL = new ErrorCode(1_030_401_003, "审核失败，只有未审核的入库单才能审核");
    ErrorCode STOCK_IN_NO_EXISTS = new ErrorCode(1_030_401_004, "生成入库单失败，请重新提交");
    ErrorCode STOCK_IN_UPDATE_FAIL_APPROVE = new ErrorCode(1_030_401_005, "其它入库单({})已审核，无法修改");

    // ========== ERP 其它出库单 1-030-402-000 ==========
    ErrorCode STOCK_OUT_NOT_EXISTS = new ErrorCode(1_030_402_000, "其它出库单不存在");
    ErrorCode STOCK_OUT_DELETE_FAIL_APPROVE = new ErrorCode(1_030_402_001, "其它出库单({})已审核，无法删除");
    ErrorCode STOCK_OUT_PROCESS_FAIL = new ErrorCode(1_030_402_002, "反审核失败，只有已审核的出库单才能反审核");
    ErrorCode STOCK_OUT_APPROVE_FAIL = new ErrorCode(1_030_402_003, "审核失败，只有未审核的出库单才能审核");
    ErrorCode STOCK_OUT_NO_EXISTS = new ErrorCode(1_030_402_004, "生成出库单失败，请重新提交");
    ErrorCode STOCK_OUT_UPDATE_FAIL_APPROVE = new ErrorCode(1_030_402_005, "其它出库单({})已审核，无法修改");

    // ========== ERP 库存调拨单 1-030-403-000 ==========
    ErrorCode STOCK_MOVE_NOT_EXISTS = new ErrorCode(1_030_402_000, "库存调拨单不存在");
    ErrorCode STOCK_MOVE_DELETE_FAIL_APPROVE = new ErrorCode(1_030_402_001, "库存调拨单({})已审核，无法删除");
    ErrorCode STOCK_MOVE_PROCESS_FAIL = new ErrorCode(1_030_402_002, "反审核失败，只有已审核的调拨单才能反审核");
    ErrorCode STOCK_MOVE_APPROVE_FAIL = new ErrorCode(1_030_402_003, "审核失败，只有未审核的调拨单才能审核");
    ErrorCode STOCK_MOVE_NO_EXISTS = new ErrorCode(1_030_402_004, "生成调拨号失败，请重新提交");
    ErrorCode STOCK_MOVE_UPDATE_FAIL_APPROVE = new ErrorCode(1_030_402_005, "库存调拨单({})已审核，无法修改");

    // ========== ERP 库存盘点单 1-030-403-000 ==========
    ErrorCode STOCK_CHECK_NOT_EXISTS = new ErrorCode(1_030_403_000, "库存盘点单不存在");
    ErrorCode STOCK_CHECK_DELETE_FAIL_APPROVE = new ErrorCode(1_030_403_001, "库存盘点单({})已审核，无法删除");
    ErrorCode STOCK_CHECK_PROCESS_FAIL = new ErrorCode(1_030_403_002, "反审核失败，只有已审核的盘点单才能反审核");
    ErrorCode STOCK_CHECK_APPROVE_FAIL = new ErrorCode(1_030_403_003, "审核失败，只有未审核的盘点单才能审核");
    ErrorCode STOCK_CHECK_NO_EXISTS = new ErrorCode(1_030_403_004, "生成盘点号失败，请重新提交");
    ErrorCode STOCK_CHECK_UPDATE_FAIL_APPROVE = new ErrorCode(1_030_403_005, "库存盘点单({})已审核，无法修改");

    // ========== ERP 产品库存 1-030-404-000 ==========
    ErrorCode STOCK_COUNT_NEGATIVE = new ErrorCode(1_030_404_000, "操作失败，产品({})所在仓库({})的库存：{}，小于变更数量：{}");
    ErrorCode STOCK_COUNT_NEGATIVE2 = new ErrorCode(1_030_404_001, "操作失败，产品({})所在仓库({})的库存不足");

    // ========== ERP 产品 1-030-500-000 ==========
    ErrorCode PRODUCT_NOT_EXISTS = new ErrorCode(1_030_500_000, "产品不存在");
    ErrorCode PRODUCT_NOT_ENABLE = new ErrorCode(1_030_500_001, "产品({})未启用");
    ErrorCode PRODUCT_MATERIAL_CODE_DUPLICATE = new ErrorCode(1_030_500_002, "统一物料编码({})已存在");
    ErrorCode PRODUCT_MATERIAL_CODE_IMMUTABLE = new ErrorCode(1_030_500_003, "统一物料编码创建后不可修改");

    // ========== ERP 产品分类 1-030-501-000 ==========
    ErrorCode PRODUCT_CATEGORY_NOT_EXISTS = new ErrorCode(1_030_501_000, "产品分类不存在");
    ErrorCode PRODUCT_CATEGORY_EXITS_CHILDREN = new ErrorCode(1_030_501_001, "存在存在子产品分类，无法删除");
    ErrorCode PRODUCT_CATEGORY_PARENT_NOT_EXITS = new ErrorCode(1_030_501_002,"父级产品分类不存在");
    ErrorCode PRODUCT_CATEGORY_PARENT_ERROR = new ErrorCode(1_030_501_003, "不能设置自己为父产品分类");
    ErrorCode PRODUCT_CATEGORY_NAME_DUPLICATE = new ErrorCode(1_030_501_004, "已经存在该分类名称的产品分类");
    ErrorCode PRODUCT_CATEGORY_PARENT_IS_CHILD = new ErrorCode(1_030_501_005, "不能设置自己的子分类为父分类");
    ErrorCode PRODUCT_CATEGORY_EXITS_PRODUCT = new ErrorCode(1_030_502_002, "存在产品使用该分类，无法删除");

    // ========== ERP 产品单位 1-030-502-000 ==========
    ErrorCode PRODUCT_UNIT_NOT_EXISTS = new ErrorCode(1_030_502_000, "产品单位不存在");
    ErrorCode PRODUCT_UNIT_NAME_DUPLICATE = new ErrorCode(1_030_502_001, "已存在该名字的产品单位");
    ErrorCode PRODUCT_UNIT_EXITS_PRODUCT = new ErrorCode(1_030_502_002, "存在产品使用该单位，无法删除");
    ErrorCode PRODUCT_UNIT_CODE_DUPLICATE = new ErrorCode(1_030_502_003, "统一单位编码({})已存在");
    ErrorCode PRODUCT_UNIT_CODE_IMMUTABLE = new ErrorCode(1_030_502_004, "统一单位编码创建后不可修改");

    // ========== ERP 结算账户 1-030-600-000 ==========
    ErrorCode ACCOUNT_NOT_EXISTS = new ErrorCode(1_030_600_000, "结算账户不存在");
    ErrorCode ACCOUNT_NOT_ENABLE = new ErrorCode(1_030_600_001, "结算账户({})未启用");

    // ========== ERP 付款单 1-030-601-000 ==========
    ErrorCode FINANCE_PAYMENT_NOT_EXISTS = new ErrorCode(1_030_601_000, "付款单不存在");
    ErrorCode FINANCE_PAYMENT_DELETE_FAIL_APPROVE = new ErrorCode(1_030_601_001, "付款单({})已审核，无法删除");
    ErrorCode FINANCE_PAYMENT_PROCESS_FAIL = new ErrorCode(1_030_601_002, "反审核失败，只有已审核的付款单才能反审核");
    ErrorCode FINANCE_PAYMENT_APPROVE_FAIL = new ErrorCode(1_030_601_003, "审核失败，只有未审核的付款单才能审核");
    ErrorCode FINANCE_PAYMENT_NO_EXISTS = new ErrorCode(1_030_601_004, "生成付款单号失败，请重新提交");
    ErrorCode FINANCE_PAYMENT_UPDATE_FAIL_APPROVE = new ErrorCode(1_030_601_005, "付款单({})已审核，无法修改");

    // ========== ERP 收款单 1-030-602-000 ==========
    ErrorCode FINANCE_RECEIPT_NOT_EXISTS = new ErrorCode(1_030_602_000, "收款单不存在");
    ErrorCode FINANCE_RECEIPT_DELETE_FAIL_APPROVE = new ErrorCode(1_030_602_001, "收款单({})已审核，无法删除");
    ErrorCode FINANCE_RECEIPT_PROCESS_FAIL = new ErrorCode(1_030_602_002, "反审核失败，只有已审核的收款单才能反审核");
    ErrorCode FINANCE_RECEIPT_APPROVE_FAIL = new ErrorCode(1_030_602_003, "审核失败，只有未审核的收款单才能审核");
    ErrorCode FINANCE_RECEIPT_NO_EXISTS = new ErrorCode(1_030_602_004, "生成收款单号失败，请重新提交");
    ErrorCode FINANCE_RECEIPT_UPDATE_FAIL_APPROVE = new ErrorCode(1_030_602_005, "收款单({})已审核，无法修改");

    // ========== ERP/WMS 跨模块集成 1-030-900-000 ==========
    ErrorCode COLLABORATION_B_DUPLICATE = new ErrorCode(1_030_900_000, "协作单 B 已接收");
    ErrorCode COLLABORATION_B_STATE_INVALID = new ErrorCode(1_030_900_001, "协作单 B 状态不允许当前操作");
    ErrorCode WMS_CALLBACK_ORDER_NOT_EXISTS = new ErrorCode(1_030_900_002, "WMS 回调对应的 ERP 单据不存在");
    ErrorCode WMS_CALLBACK_STATE_STALE = new ErrorCode(1_030_900_003, "WMS 回调版本或状态已过期");
    ErrorCode COLLABORATION_B_CONFIRM_REQUIRED = new ErrorCode(1_030_900_004, "协作单 B 尚未业务确认");
    ErrorCode COLLABORATION_B_ALREADY_CONFIRMED = new ErrorCode(1_030_900_005, "协作单 B 已完成业务确认");
    ErrorCode COLLABORATION_B_NOT_EXISTS = new ErrorCode(1_030_900_006, "协作单 B 不存在");
    ErrorCode COLLABORATION_B_CONFIRM_ITEM_INVALID = new ErrorCode(1_030_900_007, "协作单 B 确认明细与接收明细不一致");
    ErrorCode COLLABORATION_B_TYPE_INVALID = new ErrorCode(1_030_900_008, "协同单类型不支持");

    // ========== ERP/MES 跨模块集成 1-030-910-000 ==========
    ErrorCode INTEGRATION_TENANT_MISMATCH = new ErrorCode(1_030_910_000, "集成请求的租户编号与当前上下文不一致");
    ErrorCode PRODUCTION_PROGRESS_SOURCE_INVALID = new ErrorCode(1_030_910_001, "生产进度回传的来源模块不合法（仅支持 MES）");
    ErrorCode PRODUCTION_PROGRESS_QUANTITY_INVALID = new ErrorCode(1_030_910_002, "完工数量不能为空且不能为负数");
    ErrorCode PRODUCTION_PROGRESS_MATERIAL_NOT_FOUND = new ErrorCode(1_030_910_003, "物料编码({})在 ERP 不存在，无法回写生产进度");
    ErrorCode PRODUCTION_PROGRESS_ORDER_ITEM_NOT_FOUND = new ErrorCode(1_030_910_004, "销售订单中不存在物料({})的订单行，无法回写生产进度");

    // ========== ERP 采购申请（1-030-700-000） ==========
    ErrorCode PURCHASE_REQUEST_NOT_EXISTS = new ErrorCode(1_030_700_000, "采购申请不存在");
    ErrorCode PURCHASE_REQUEST_NO_EXISTS = new ErrorCode(1_030_700_001, "生成采购申请编号失败，请重新提交");
    ErrorCode PURCHASE_REQUEST_UPDATE_FAIL_STATUS = new ErrorCode(1_030_700_002, "采购申请({})当前状态无法修改");
    ErrorCode PURCHASE_REQUEST_DELETE_FAIL_STATUS = new ErrorCode(1_030_700_003, "采购申请({})当前状态无法删除");
    ErrorCode PURCHASE_REQUEST_SUBMIT_FAIL_STATUS = new ErrorCode(1_030_700_004, "采购申请({})当前状态无法提交审批");
    ErrorCode PURCHASE_REQUEST_CONVERT_FAIL_STATUS = new ErrorCode(1_030_700_005, "采购申请({})未审批通过，无法转询价");
    ErrorCode PURCHASE_REQUEST_ITEM_COUNT_INVALID = new ErrorCode(1_030_700_006, "采购申请明细数量必须大于零");

    // ========== ERP 询价与定标（1-030-710-000） ==========
    ErrorCode INQUIRY_NOT_EXISTS = new ErrorCode(1_030_710_000, "询价单不存在");
    ErrorCode INQUIRY_NO_EXISTS = new ErrorCode(1_030_710_001, "生成询价单编号失败，请重新提交");
    ErrorCode INQUIRY_UPDATE_FAIL_STATUS = new ErrorCode(1_030_710_002, "询价单({})当前状态无法修改");
    ErrorCode INQUIRY_SUBMIT_FAIL_STATUS = new ErrorCode(1_030_710_003, "询价单({})当前状态无法提交审批");
    ErrorCode INQUIRY_PUBLISH_FAIL_STATUS = new ErrorCode(1_030_710_004, "询价单({})未审批通过，无法发布");
    ErrorCode INQUIRY_CLOSE_FAIL_STATUS = new ErrorCode(1_030_710_005, "询价单({})当前状态无法截止报价");
    ErrorCode INQUIRY_DEADLINE_INVALID = new ErrorCode(1_030_710_006, "报价截止时间必须晚于当前时间");
    ErrorCode INQUIRY_QUOTE_NOT_EXISTS = new ErrorCode(1_030_710_007, "报价不存在");
    ErrorCode INQUIRY_QUOTE_STATUS_INVALID = new ErrorCode(1_030_710_008, "当前询价状态不允许维护报价");
    ErrorCode INQUIRY_QUOTE_DUPLICATE = new ErrorCode(1_030_710_009, "供应商当前报价版本已经存在");
    ErrorCode AWARD_SELECTION_INVALID = new ErrorCode(1_030_710_010, "每条询价明细必须且只能选择一个中标报价");
    ErrorCode AWARD_SUBMIT_FAIL_STATUS = new ErrorCode(1_030_710_011, "询价单({})当前状态无法提交定标审批");
    ErrorCode AWARD_CONFIRM_FAIL_STATUS = new ErrorCode(1_030_710_012, "询价单({})定标尚未审批通过");
    ErrorCode AWARD_ALREADY_CONFIRMED = new ErrorCode(1_030_710_013, "询价单({})已经生成采购订单");

    // ========== ERP 造价结果（1-030-720-000） ==========
    ErrorCode COST_RESULT_NOT_EXISTS = new ErrorCode(1_030_720_000, "造价结果不存在");
    ErrorCode COST_RESULT_NO_EXISTS = new ErrorCode(1_030_720_001, "生成造价结果编号失败，请重新提交");
    ErrorCode COST_RESULT_VERSION_DUPLICATE = new ErrorCode(1_030_720_002, "项目当前造价版本已经存在");
    ErrorCode COST_RESULT_UPDATE_FAIL_STATUS = new ErrorCode(1_030_720_003, "造价结果({})当前状态无法修改");
    ErrorCode COST_RESULT_SUBMIT_FAIL_STATUS = new ErrorCode(1_030_720_004, "造价结果({})当前状态无法提交审批");
    ErrorCode COST_RESULT_MAPPING_INCOMPLETE = new ErrorCode(1_030_720_005, "造价结果仍有未完成映射的材料");
    ErrorCode COST_RESULT_CONVERT_FAIL_STATUS = new ErrorCode(1_030_720_006, "造价结果({})未审批通过，无法转换");

}
