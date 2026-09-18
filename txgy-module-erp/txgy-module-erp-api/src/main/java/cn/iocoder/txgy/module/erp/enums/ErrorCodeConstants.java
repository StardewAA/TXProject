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
    ErrorCode SUPPLIER_USER_DUPLICATE = new ErrorCode(1_030_100_004, "登录账号已绑定供应商({})，一个账号只能绑定一家供应商");
    ErrorCode SUPPLIER_CREDIT_NOT_EXISTS = new ErrorCode(1_030_100_004, "供应商资信档案不存在");
    ErrorCode SUPPLIER_CREDIT_BLOCKED = new ErrorCode(1_030_100_005, "供应商({})当前不具备采购资格：{}");
    ErrorCode SUPPLIER_CREDIT_CATEGORY_NOT_ADMITTED = new ErrorCode(1_030_100_006, "供应商({})未准入当前采购品类：{}");
    ErrorCode SUPPLIER_CREDIT_QUALIFICATION_EXPIRED = new ErrorCode(1_030_100_007, "供应商({})存在已过期资质：{}");
    ErrorCode SUPPLIER_CREDIT_VERSION_CONFLICT = new ErrorCode(1_030_100_008, "供应商资信数据版本过旧，当前版本为{}");
    ErrorCode SUPPLIER_CREDIT_STATUS_INVALID = new ErrorCode(1_030_100_009, "供应商资信状态不合法：{}");

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
    ErrorCode PURCHASE_ORDER_REVERSE_APPROVE_FAIL_STATUS = new ErrorCode(1_030_101_013, "反审批失败，只有审批中或已审批的采购订单才能反审批");

    // ========== ERP 图片识别（1-030-105-000） ==========
    ErrorCode RECOGNITION_TASK_NOT_EXISTS = new ErrorCode(1_030_105_000, "图片识别任务不存在");
    ErrorCode RECOGNITION_TASK_STATE_INVALID = new ErrorCode(1_030_105_001, "当前图片识别任务状态({})不允许该操作");
    ErrorCode RECOGNITION_RESULT_VERSION_EXPIRED = new ErrorCode(1_030_105_002, "识别结果已更新，请刷新后重新操作");
    ErrorCode RECOGNITION_UNSUPPORTED_BIZ_TYPE = new ErrorCode(1_030_105_003, "暂不支持识别业务类型({})");

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
    ErrorCode PURCHASE_IN_WMS_ALREADY_SUBMITTED = new ErrorCode(1_030_102_009, "采购入库单({})已提交 WMS，请勿重复申请");
    ErrorCode PURCHASE_IN_MATERIAL_NOT_RAW = new ErrorCode(1_030_102_010, "采购入库单({})明细存在非原材料物料，无法申请入库到 WMS");

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
    ErrorCode SALE_ORDER_REVERSE_APPROVE_FAIL_STATUS = new ErrorCode(1_020_201_013, "反审批失败，只有审批中或已审批的销售订单才能反审批");

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
    ErrorCode WAREHOUSE_WMS_CODE_NOT_CONFIGURED = new ErrorCode(1_030_400_003, "仓库({})未配置 WMS 仓库编码，无法申请入库到 WMS");

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
    ErrorCode STOCK_MOVE_CONFIRM_STATUS_INVALID = new ErrorCode(1_030_402_006, "调拨单当前状态不允许确认");
    ErrorCode STOCK_MOVE_CONFIRM_USER_INVALID = new ErrorCode(1_030_402_007, "只有对应仓库负责人才能确认调拨");
    ErrorCode STOCK_MOVE_WAREHOUSE_MANAGER_MISSING = new ErrorCode(1_030_402_008, "调拨仓库未配置负责人，无法发起确认");

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
    ErrorCode ACCOUNT_HAS_RECORDS = new ErrorCode(1_030_600_002, "结算账户下存在账务记录，无法删除");

    // ========== ERP 付款单 1-030-601-000 ==========
    ErrorCode FINANCE_PAYMENT_NOT_EXISTS = new ErrorCode(1_030_601_000, "付款单不存在");
    ErrorCode FINANCE_PAYMENT_DELETE_FAIL_APPROVE = new ErrorCode(1_030_601_001, "付款单({})已审核，无法删除");
    ErrorCode FINANCE_PAYMENT_PROCESS_FAIL = new ErrorCode(1_030_601_002, "反审核失败，只有已审核的付款单才能反审核");
    ErrorCode FINANCE_PAYMENT_APPROVE_FAIL = new ErrorCode(1_030_601_003, "审核失败，只有未审核的付款单才能审核");
    ErrorCode FINANCE_PAYMENT_NO_EXISTS = new ErrorCode(1_030_601_004, "生成付款单号失败，请重新提交");
    ErrorCode FINANCE_PAYMENT_UPDATE_FAIL_APPROVE = new ErrorCode(1_030_601_005, "付款单({})已审核，无法修改");
    ErrorCode FINANCE_PAYMENT_AMOUNT_INVALID = new ErrorCode(1_030_601_006, "付款金额非法，明细重复或付款金额超出可付/预付范围");

    // ========== ERP 收款单 1-030-602-000 ==========
    ErrorCode FINANCE_RECEIPT_NOT_EXISTS = new ErrorCode(1_030_602_000, "收款单不存在");
    ErrorCode FINANCE_RECEIPT_DELETE_FAIL_APPROVE = new ErrorCode(1_030_602_001, "收款单({})已审核，无法删除");
    ErrorCode FINANCE_RECEIPT_PROCESS_FAIL = new ErrorCode(1_030_602_002, "反审核失败，只有已审核的收款单才能反审核");
    ErrorCode FINANCE_RECEIPT_APPROVE_FAIL = new ErrorCode(1_030_602_003, "审核失败，只有未审核的收款单才能审核");
    ErrorCode FINANCE_RECEIPT_NO_EXISTS = new ErrorCode(1_030_602_004, "生成收款单号失败，请重新提交");
    ErrorCode FINANCE_RECEIPT_UPDATE_FAIL_APPROVE = new ErrorCode(1_030_602_005, "收款单({})已审核，无法修改");
    ErrorCode FINANCE_RECEIPT_AMOUNT_INVALID = new ErrorCode(1_030_602_006, "收款金额非法，明细重复或收款金额超出可收/预收范围");

    // ========== ERP 物流（1-030-800-000） ==========
    ErrorCode LOGISTICS_EXCEPTION_NOT_EXISTS = new ErrorCode(1_030_800_000, "物流异常单不存在");
    ErrorCode LOGISTICS_EVENT_NOT_PENDING_COMPENSATION = new ErrorCode(1_030_800_001, "物流事件不处于待补偿状态");
    ErrorCode LOGISTICS_ATTACHMENT_INVALID = new ErrorCode(1_030_800_002, "物流附件无效");
    ErrorCode LOGISTICS_NODE_INVALID = new ErrorCode(1_030_800_003, "物流节点无效");
    ErrorCode LOGISTICS_RECEIPT_INVALID = new ErrorCode(1_030_800_004, "物流回执无效");
    ErrorCode LOGISTICS_RELATION_NOT_EXISTS = new ErrorCode(1_030_800_005, "物流关联关系不存在");
    ErrorCode LOGISTICS_REQUEST_CONFLICT = new ErrorCode(1_030_800_006, "物流请求冲突，请勿重复提交");
    ErrorCode LOGISTICS_SOURCE_ORDER_NOT_EXISTS = new ErrorCode(1_030_800_007, "物流来源单据不存在");
    ErrorCode LOGISTICS_SOURCE_ORDER_NOT_APPROVED = new ErrorCode(1_030_800_008, "物流来源单据未审批通过");
    ErrorCode LOGISTICS_SOURCE_ORDER_TYPE_INVALID = new ErrorCode(1_030_800_009, "物流来源单据类型无效");
    ErrorCode LOGISTICS_TASK_CONFLICT = new ErrorCode(1_030_800_010, "物流任务状态冲突");
    ErrorCode LOGISTICS_TENANT_MISMATCH = new ErrorCode(1_030_800_011, "物流租户不匹配");

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

    // ========== ERP 采购申请（1-030-700-000） ==========
    ErrorCode PURCHASE_REQUEST_NOT_EXISTS = new ErrorCode(1_030_700_000, "采购申请不存在");
    ErrorCode PURCHASE_REQUEST_NO_EXISTS = new ErrorCode(1_030_700_001, "生成采购申请编号失败，请重新提交");
    ErrorCode PURCHASE_REQUEST_UPDATE_FAIL_STATUS = new ErrorCode(1_030_700_002, "采购申请({})当前状态无法修改");
    ErrorCode PURCHASE_REQUEST_DELETE_FAIL_STATUS = new ErrorCode(1_030_700_003, "采购申请({})当前状态无法删除");
    ErrorCode PURCHASE_REQUEST_SUBMIT_FAIL_STATUS = new ErrorCode(1_030_700_004, "采购申请({})当前状态无法提交审批");
    ErrorCode PURCHASE_REQUEST_CONVERT_FAIL_STATUS = new ErrorCode(1_030_700_005, "采购申请({})未审批通过，无法转询价");
    ErrorCode PURCHASE_REQUEST_ITEM_COUNT_INVALID = new ErrorCode(1_030_700_006, "采购申请明细数量必须大于零");
    ErrorCode PURCHASE_REQUEST_REVERSE_APPROVE_FAIL_STATUS = new ErrorCode(1_030_700_007, "反审批失败，只有审批中或已审批的采购申请才能反审批");

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
    ErrorCode INQUIRY_QUOTE_DEADLINE_PASSED = new ErrorCode(1_030_710_014, "询价单({})已超过报价截止时间");
    ErrorCode INQUIRY_QUOTE_ITEMS_INCOMPLETE = new ErrorCode(1_030_710_015, "报价明细必须完整覆盖询价明细，且数量不能超过询价数量");
    ErrorCode AWARD_COUNT_MISMATCH = new ErrorCode(1_030_710_016, "定标数量必须等于对应询价数量");
    // 反审批（团队 master 引入，占用 017/018）
    ErrorCode INQUIRY_REVERSE_APPROVE_FAIL_STATUS = new ErrorCode(1_030_710_017, "反审批失败，只有审批中或已审批且未发布的询价单才能反审批");
    ErrorCode AWARD_REVERSE_APPROVE_FAIL_STATUS = new ErrorCode(1_030_710_018, "反审批失败，只有审批中或已定标（未生成采购订单）的询价单才能反审批");
    // 供应商数据隔离（合并时让出 017/018，顺延至 019~021，避免与上面的反审批编号冲突）
    ErrorCode INQUIRY_SUPPLIER_NOT_INVITED = new ErrorCode(1_030_710_019, "供应商({})未被邀请参与该询价单");
    ErrorCode INQUIRY_QUOTE_SUPPLIER_MISMATCH = new ErrorCode(1_030_710_020, "报价供应商与当前账号所属供应商({})不一致");
    ErrorCode INQUIRY_NOT_VISIBLE_FOR_SUPPLIER = new ErrorCode(1_030_710_021, "无权查看该询价单");

    // ========== ERP 造价结果（1-030-720-000） ==========
    ErrorCode COST_RESULT_NOT_EXISTS = new ErrorCode(1_030_720_000, "造价结果不存在");
    ErrorCode COST_RESULT_NO_EXISTS = new ErrorCode(1_030_720_001, "生成造价结果编号失败，请重新提交");
    ErrorCode COST_RESULT_VERSION_DUPLICATE = new ErrorCode(1_030_720_002, "项目当前造价版本已经存在");
    ErrorCode COST_RESULT_UPDATE_FAIL_STATUS = new ErrorCode(1_030_720_003, "造价结果({})当前状态无法修改");
    ErrorCode COST_RESULT_SUBMIT_FAIL_STATUS = new ErrorCode(1_030_720_004, "造价结果({})当前状态无法提交审批");
    ErrorCode COST_RESULT_MAPPING_INCOMPLETE = new ErrorCode(1_030_720_005, "造价结果仍有未完成映射的材料");
    ErrorCode COST_RESULT_CONVERT_FAIL_STATUS = new ErrorCode(1_030_720_006, "造价结果({})未审批通过，无法转换");
    // 反审批（团队 master 引入，占用 007）
    ErrorCode COST_RESULT_REVERSE_APPROVE_FAIL_STATUS = new ErrorCode(1_030_720_007, "反审批失败，只有审批中或已审批且未转换的造价结果才能反审批");
    // 造价转销售订单（合并时让出 007，顺延至 008/009，避免与上面的反审批编号冲突）
    ErrorCode COST_RESULT_CONVERT_CUSTOMER_REQUIRED = new ErrorCode(1_030_720_008, "造价结果转销售订单时必须指定客户");
    ErrorCode COST_RESULT_CONVERT_NO_MAPPED_ITEM = new ErrorCode(1_030_720_009, "造价结果没有已完成物料映射的明细，无法转销售订单");

}
