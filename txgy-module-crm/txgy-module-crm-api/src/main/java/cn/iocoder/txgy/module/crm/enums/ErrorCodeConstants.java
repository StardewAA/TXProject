package cn.iocoder.txgy.module.crm.enums;

import cn.iocoder.txgy.framework.common.exception.ErrorCode;

/**
 * CRM 错误码枚举类
 * <p>
 * crm 系统，使用 1-020-000-000 段
 */
public interface ErrorCodeConstants {

    // ========== 合同管理 1-020-000-000 ==========
    ErrorCode CONTRACT_NOT_EXISTS = new ErrorCode(1_020_000_000, "合同不存在");
    ErrorCode CONTRACT_UPDATE_FAIL_NOT_DRAFT = new ErrorCode(1_020_000_001, "合同更新失败，原因：合同不是草稿状态");
    ErrorCode CONTRACT_SUBMIT_FAIL_NOT_DRAFT = new ErrorCode(1_020_000_002, "合同提交审核失败，原因：合同没处在未提交状态");
    ErrorCode CONTRACT_UPDATE_AUDIT_STATUS_FAIL_NOT_PROCESS = new ErrorCode(1_020_000_003, "更新合同审核状态失败，原因：合同不是审核中状态");
    ErrorCode CONTRACT_NO_EXISTS = new ErrorCode(1_020_000_004, "生成合同序列号重复，请重试");
    ErrorCode CONTRACT_DELETE_FAIL = new ErrorCode(1_020_000_005, "删除合同失败，原因：有被回款所使用");

    // ========== 线索管理 1-020-001-000 ==========
    ErrorCode CLUE_NOT_EXISTS = new ErrorCode(1_020_001_000, "线索不存在");
    ErrorCode CLUE_TRANSFORM_FAIL_ALREADY = new ErrorCode(1_020_001_001, "线索已经转化过了，请勿重复转化");
    ErrorCode CLUE_IMPORT_LIST_IS_EMPTY = new ErrorCode(1_020_001_002, "导入线索数据不能为空！");
    ErrorCode CLUE_CREATE_NAME_NOT_NULL = new ErrorCode(1_020_001_003, "线索名称不能为空！");

    // ========== 商机管理 1-020-002-000 ==========
    ErrorCode BUSINESS_NOT_EXISTS = new ErrorCode(1_020_002_000, "商机不存在");
    ErrorCode BUSINESS_DELETE_FAIL_CONTRACT_EXISTS = new ErrorCode(1_020_002_001, "商机已关联合同，不能删除");
    ErrorCode BUSINESS_UPDATE_STATUS_FAIL_END_STATUS = new ErrorCode(1_020_002_002, "更新商机状态失败，原因：已经是结束状态");
    ErrorCode BUSINESS_UPDATE_STATUS_FAIL_STATUS_EQUALS = new ErrorCode(1_020_002_003, "更新商机状态失败，原因：已经是该状态");
    ErrorCode BUSINESS_CONTACT_CUSTOMER_NOT_MATCH = new ErrorCode(1_020_002_004, "商机关联的联系人不属于该客户");

    // ========== 联系人管理 1-020-003-000 ==========
    ErrorCode CONTACT_NOT_EXISTS = new ErrorCode(1_020_003_000, "联系人不存在");
    ErrorCode CONTACT_DELETE_FAIL_CONTRACT_LINK_EXISTS = new ErrorCode(1_020_003_002, "联系人已关联合同，不能删除");
    ErrorCode CONTACT_UPDATE_OWNER_USER_FAIL = new ErrorCode(1_020_003_003, "更新联系人负责人失败");

    // ========== 回款 1-020-004-000 ==========
    ErrorCode RECEIVABLE_NOT_EXISTS = new ErrorCode(1_020_004_000, "回款不存在");
    ErrorCode RECEIVABLE_UPDATE_FAIL_EDITING_PROHIBITED = new ErrorCode(1_020_004_001, "更新回款失败，原因：禁止编辑");
    ErrorCode RECEIVABLE_DELETE_FAIL = new ErrorCode(1_020_004_002, "删除回款失败，原因： 被回款计划所使用，不允许删除");
    ErrorCode RECEIVABLE_SUBMIT_FAIL_NOT_DRAFT = new ErrorCode(1_020_004_003, "回款提交审核失败，原因：回款没处在未提交状态");
    ErrorCode RECEIVABLE_UPDATE_AUDIT_STATUS_FAIL_NOT_PROCESS = new ErrorCode(1_020_004_004, "更新回款审核状态失败，原因：回款不是审核中状态");
    ErrorCode RECEIVABLE_NO_EXISTS = new ErrorCode(1_020_004_005, "生成回款序列号重复，请重试");
    ErrorCode RECEIVABLE_CREATE_FAIL_CONTRACT_NOT_APPROVE = new ErrorCode(1_020_004_006, "创建回款失败，原因：合同不是审核通过状态");
    ErrorCode RECEIVABLE_CREATE_FAIL_PRICE_EXCEEDS_LIMIT = new ErrorCode(1_020_004_007, "创建回款失败，原因：回款金额超出合同金额，目前剩余可退：{} 元");
    ErrorCode RECEIVABLE_DELETE_FAIL_IS_APPROVE = new ErrorCode(1_020_004_008, "删除回款失败，原因：回款审批已通过");

    // ========== 回款计划 1-020-005-000 ==========
    ErrorCode RECEIVABLE_PLAN_NOT_EXISTS = new ErrorCode(1_020_005_000, "回款计划不存在");
    ErrorCode RECEIVABLE_PLAN_UPDATE_FAIL = new ErrorCode(1_020_006_000, "更想回款计划失败，原因：已经有对应的还款");
    ErrorCode RECEIVABLE_PLAN_EXISTS_RECEIVABLE = new ErrorCode(1_020_006_001, "回款计划已经有对应的回款，不能使用");

    // ========== 客户管理 1_020_006_000 ==========
    ErrorCode CUSTOMER_NOT_EXISTS = new ErrorCode(1_020_006_000, "客户不存在");
    ErrorCode CUSTOMER_OWNER_EXISTS = new ErrorCode(1_020_006_001, "客户【{}】已存在所属负责人");
    ErrorCode CUSTOMER_LOCKED = new ErrorCode(1_020_006_002, "客户【{}】状态已锁定");
    ErrorCode CUSTOMER_ALREADY_DEAL = new ErrorCode(1_020_006_003, "客户已交易");
    ErrorCode CUSTOMER_IN_POOL = new ErrorCode(1_020_006_004, "客户【{}】放入公海失败，原因：已经是公海客户");
    ErrorCode CUSTOMER_LOCKED_PUT_POOL_FAIL = new ErrorCode(1_020_006_005, "客户【{}】放入公海失败，原因：客户已锁定");
    ErrorCode CUSTOMER_UPDATE_OWNER_USER_FAIL = new ErrorCode(1_020_006_006, "更新客户【{}】负责人失败, 原因：系统异常");
    ErrorCode CUSTOMER_LOCK_FAIL_IS_LOCK = new ErrorCode(1_020_006_007, "锁定客户失败，它已经处于锁定状态");
    ErrorCode CUSTOMER_UNLOCK_FAIL_IS_UNLOCK = new ErrorCode(1_020_006_008, "解锁客户失败，它已经处于未锁定状态");
    ErrorCode CUSTOMER_LOCK_EXCEED_LIMIT = new ErrorCode(1_020_006_009, "锁定客户失败，超出锁定规则上限");
    ErrorCode CUSTOMER_OWNER_EXCEED_LIMIT = new ErrorCode(1_020_006_010, "操作失败，超出客户数拥有上限");
    ErrorCode CUSTOMER_DELETE_FAIL_HAVE_REFERENCE = new ErrorCode(1_020_006_011, "删除客户失败，有关联{}");
    ErrorCode CUSTOMER_IMPORT_LIST_IS_EMPTY = new ErrorCode(1_020_006_012, "导入客户数据不能为空！");
    ErrorCode CUSTOMER_CREATE_NAME_NOT_NULL = new ErrorCode(1_020_006_013, "客户名称不能为空！");
    ErrorCode CUSTOMER_NAME_EXISTS = new ErrorCode(1_020_006_014, "已存在名为【{}】的客户！");
    ErrorCode CUSTOMER_UPDATE_DEAL_STATUS_FAIL = new ErrorCode(1_020_006_015, "更新客户的成交状态失败，原因：已经是该状态，无需更新");
    ErrorCode CUSTOMER_FETCH_WEB_PAGE_FAIL = new ErrorCode(1_020_006_016, "网页抓取失败：{}");
    ErrorCode CRM_AI_CLASSIFY_FAIL = new ErrorCode(1_020_006_017, "AI 意图识别失败：{}");

    // ========== CRM AI 文件（图片 / 文档）识别 1-020-006-021 ==========
    ErrorCode CRM_AI_EXTRACT_FAIL = new ErrorCode(1_020_006_021, "文件识别失败：{}");
    ErrorCode SUPPLIER_IMPORT_FILE_EMPTY = new ErrorCode(1_020_006_018, "供应商导入失败，原因：上传文件为空");
    ErrorCode SUPPLIER_IMPORT_PARSE_FAIL = new ErrorCode(1_020_006_019, "供应商导入失败，原因：文档解析失败（{}），请确认为 .docx 格式的 Word 文档");
    ErrorCode SUPPLIER_IMPORT_NO_DATA = new ErrorCode(1_020_006_020, "供应商导入失败，原因：文档中未识别到供应商数据，请确认表格表头包含「供应商名称」列");

    // ========== 权限管理 1_020_007_000 ==========
    ErrorCode CRM_PERMISSION_NOT_EXISTS = new ErrorCode(1_020_007_000, "数据权限不存在");
    ErrorCode CRM_PERMISSION_DENIED = new ErrorCode(1_020_007_001, "{}操作失败，原因：没有权限");
    ErrorCode CRM_PERMISSION_MODEL_TRANSFER_FAIL_OWNER_USER_EXISTS = new ErrorCode(1_020_007_003, "{}操作失败，原因：转移对象已经是该负责人");
    ErrorCode CRM_PERMISSION_DELETE_FAIL = new ErrorCode(1_020_007_004, "删除数据权限失败，原因：批量删除权限的时候，只能属于同一个 bizId 下");
    ErrorCode CRM_PERMISSION_DELETE_DENIED = new ErrorCode(1_020_007_006, "删除数据权限失败，原因：没有权限");
    ErrorCode CRM_PERMISSION_DELETE_SELF_PERMISSION_FAIL_EXIST_OWNER = new ErrorCode(1_020_007_007, "删除数据权限失败，原因：不能删除负责人");
    ErrorCode CRM_PERMISSION_CREATE_FAIL = new ErrorCode(1_020_007_008, "创建数据权限失败，原因：所加用户已有权限");
    ErrorCode CRM_PERMISSION_CREATE_FAIL_EXISTS = new ErrorCode(1_020_007_009, "同时添加数据权限失败，原因：用户【{}】已有模块【{}】数据【{}】的【{}】权限");

    // ========== 产品 1_020_008_000 ==========
    ErrorCode PRODUCT_NOT_EXISTS = new ErrorCode(1_020_008_000, "产品不存在");
    ErrorCode PRODUCT_NO_EXISTS = new ErrorCode(1_020_008_001, "产品编号已存在");
    ErrorCode PRODUCT_NOT_ENABLE = new ErrorCode(1_020_008_002, "产品【{}】已禁用");
    ErrorCode PRODUCT_WMS_READ_ONLY = new ErrorCode(1_020_008_003,
            "产品数据全部实时取自 WMS 仓库库存，不支持手动新增、修改、删除，请在 WMS 中维护");

    // ========== 产品分类 1_020_009_000 ==========
    ErrorCode PRODUCT_CATEGORY_NOT_EXISTS = new ErrorCode(1_020_009_000, "产品分类不存在");
    ErrorCode PRODUCT_CATEGORY_EXISTS = new ErrorCode(1_020_009_001, "产品分类已存在");
    ErrorCode PRODUCT_CATEGORY_USED = new ErrorCode(1_020_009_002, "产品分类已关联产品");
    ErrorCode PRODUCT_CATEGORY_PARENT_NOT_EXISTS = new ErrorCode(1_020_009_003, "父分类不存在");
    ErrorCode PRODUCT_CATEGORY_PARENT_NOT_FIRST_LEVEL = new ErrorCode(1_020_009_004, "父分类不能是二级分类");
    ErrorCode PRODUCT_CATEGORY_EXISTS_CHILDREN = new ErrorCode(1_020_009_005, "存在子分类，无法删除");

    // ========== 商机状态 1_020_010_000 ==========
    ErrorCode BUSINESS_STATUS_TYPE_NOT_EXISTS = new ErrorCode(1_020_010_000, "商机状态组不存在");
    ErrorCode BUSINESS_STATUS_TYPE_NAME_EXISTS = new ErrorCode(1_020_010_001, "商机状态组的名称已存在");
    ErrorCode BUSINESS_STATUS_UPDATE_FAIL_USED = new ErrorCode(1_020_010_002, "已经被使用的商机状态组，无法进行更新");
    ErrorCode BUSINESS_STATUS_DELETE_FAIL_USED = new ErrorCode(1_020_010_002, "已经被使用的商机状态组，无法进行删除");
    ErrorCode BUSINESS_STATUS_NOT_EXISTS = new ErrorCode(1_020_010_003, "商机状态不存在");

    // ========== 客户公海规则设置 1_020_012_000 ==========
    ErrorCode CUSTOMER_LIMIT_CONFIG_NOT_EXISTS = new ErrorCode(1_020_012_001, "客户限制配置不存在");

    // ========== 跟进记录 1_020_013_000 ==========
    ErrorCode FOLLOW_UP_RECORD_NOT_EXISTS = new ErrorCode(1_020_013_000, "跟进记录不存在");
    ErrorCode FOLLOW_UP_RECORD_DELETE_DENIED = new ErrorCode(1_020_013_001, "删除跟进记录失败，原因：没有权限");

    // ========== 报价 1_020_014_000 ==========
    ErrorCode QUOTATION_NOT_EXISTS = new ErrorCode(1_020_014_000, "报价不存在");
    ErrorCode QUOTATION_NO_EXISTS = new ErrorCode(1_020_014_001, "生成报价序列号重复，请重试");
    ErrorCode QUOTATION_UPDATE_FAIL_NOT_DRAFT = new ErrorCode(1_020_014_002, "报价更新失败，原因：报价不是草稿状态");
    ErrorCode QUOTATION_SUBMIT_FAIL_NOT_DRAFT = new ErrorCode(1_020_014_003, "报价提交审核失败，原因：报价没处在未提交状态");
    ErrorCode QUOTATION_UPDATE_AUDIT_STATUS_FAIL_NOT_PROCESS = new ErrorCode(1_020_014_004, "更新报价审核状态失败，原因：报价不是审核中状态");
    ErrorCode QUOTATION_DELETE_FAIL_HAS_CONTRACT = new ErrorCode(1_020_014_005, "删除报价失败，原因：报价已生成合同，不能删除");
    ErrorCode QUOTATION_CREATE_CONTRACT_FAIL_NOT_APPROVE = new ErrorCode(1_020_014_006, "转为合同失败，原因：报价不是审批通过状态");
    ErrorCode QUOTATION_CREATE_CONTRACT_FAIL_EXISTS = new ErrorCode(1_020_014_007, "转为合同失败，原因：报价已生成合同");
    ErrorCode QUOTATION_TYPE_INVALID = new ErrorCode(1_020_014_008, "保存报价失败，原因：报价类型不正确");
    ErrorCode QUOTATION_PURCHASE_SUPPLIER_REQUIRED = new ErrorCode(1_020_014_009, "保存报价失败，原因：采购报价必须选择上游供应商");
    ErrorCode QUOTATION_PURCHASE_SUPPLIER_INVALID = new ErrorCode(1_020_014_010, "保存报价失败，原因：所选单位不是「上游供应商」，采购报价仅可选择上游供应商");
    ErrorCode QUOTATION_PURCHASE_BUSINESS_NOT_ALLOWED = new ErrorCode(1_020_014_011, "保存报价失败，原因：采购报价不允许关联商机");
    ErrorCode QUOTATION_SALES_CUSTOMER_REQUIRED = new ErrorCode(1_020_014_012, "保存报价失败，原因：购销报价必须选择客户");
    ErrorCode QUOTATION_SALES_CUSTOMER_INVALID = new ErrorCode(1_020_014_013, "保存报价失败，原因：所选单位是「上游供应商」，购销报价请选择下游客户");
    ErrorCode QUOTATION_CREATE_CONTRACT_FAIL_RELATION_INVALID = new ErrorCode(1_020_014_014, "转为合同失败，原因：报价类型与往来单位不匹配");
    ErrorCode QUOTATION_TYPE_UPDATE_FAIL_HAS_CONTRACT = new ErrorCode(1_020_014_015, "更新报价失败，原因：报价已生成合同，不能修改报价类型");

    // ========== 商机管理 1_020_002_000 报价关联校验 ==========
    ErrorCode BUSINESS_DELETE_FAIL_QUOTATION_EXISTS = new ErrorCode(1_020_002_005, "商机已关联报价，不能删除");

    // ========== 商机管理 1_020_002_000 标准化阶段（阶段/状态组/赢单/输单） ==========
    ErrorCode BUSINESS_UPDATE_FAIL_CLOSED = new ErrorCode(1_020_002_006, "更新商机失败，原因：商机已关闭（已赢单/已丢单），关键字段不可修改");
    ErrorCode BUSINESS_STAGE_INVALID = new ErrorCode(1_020_002_007, "保存商机失败，原因：商机阶段不正确");
    ErrorCode BUSINESS_WIN_FAIL_CLOSED = new ErrorCode(1_020_002_008, "赢单失败，原因：商机已关闭（当前状态：{}）");
    ErrorCode BUSINESS_LOSE_FAIL_CLOSED = new ErrorCode(1_020_002_009, "输单失败，原因：商机已关闭（当前状态：{}）");
    ErrorCode BUSINESS_STATE_INVALID = new ErrorCode(1_020_002_010, "更新商机状态组失败，原因：状态组不正确");
    ErrorCode BUSINESS_UPDATE_STATE_FAIL_SAME = new ErrorCode(1_020_002_011, "更新商机状态组失败，原因：已经是该状态组");
    ErrorCode BUSINESS_UPDATE_STATE_FAIL_NOT_SUPPORT = new ErrorCode(1_020_002_012, "更新商机状态组失败，原因：不允许直接设置为已赢单/已丢单，请使用「赢单」「输单」按钮");
    ErrorCode BUSINESS_UPDATE_STATUS_FAIL_USE_BUTTON = new ErrorCode(1_020_002_013, "更新商机状态失败，原因：赢单/输单请使用「赢单」「输单」按钮操作");

    // ========== 业绩目标配置 1_020_011_000 ==========
    ErrorCode PERFORMANCE_CONFIG_NOT_EXISTS = new ErrorCode(1_020_011_000, "业绩目标配置不存在");
    ErrorCode PERFORMANCE_CONFIG_EXISTS = new ErrorCode(1_020_011_001, "该对象当前年份的业绩目标配置已存在");
    ErrorCode PERFORMANCE_CONFIG_TYPE_INVALID = new ErrorCode(1_020_011_002, "目标对象类型不正确");

    // ========== 售后质保档案 1_020_014_000 ==========
    ErrorCode AFTER_SALE_WARRANTY_NOT_EXISTS = new ErrorCode(1_020_014_000, "质保档案不存在");
    ErrorCode AFTER_SALE_WARRANTY_NO_EXISTS = new ErrorCode(1_020_014_001, "生成质保编号重复，请重试");
    ErrorCode AFTER_SALE_WARRANTY_CREATE_FAIL_CONTRACT_NOT_APPROVE = new ErrorCode(1_020_014_002, "创建质保档案失败，原因：合同不是审核通过状态");
    ErrorCode AFTER_SALE_WARRANTY_YEARS_INVALID = new ErrorCode(1_020_014_003, "质保年限不正确，仅支持 1-5 年");
    ErrorCode AFTER_SALE_WARRANTY_END_DATE_INVALID = new ErrorCode(1_020_014_004, "质保到期日期不能早于项目完工验收日期");
    ErrorCode AFTER_SALE_WARRANTY_DELETE_FAIL_EXISTS_TASK = new ErrorCode(1_020_014_005, "删除质保档案失败，原因：已存在回访任务，不允许删除");
    ErrorCode AFTER_SALE_WARRANTY_CREATE_FAIL_CONTRACT_EXISTS = new ErrorCode(1_020_014_006, "创建质保档案失败，原因：该合同已创建质保档案，不可重复创建");
    ErrorCode AFTER_SALE_WARRANTY_STATUS_INVALID = new ErrorCode(1_020_014_007, "修正质保状态失败，原因：质保状态不正确");
    ErrorCode AFTER_SALE_WARRANTY_UPDATE_CONFLICT = new ErrorCode(1_020_014_008, "保存失败，原因：该质保档案刚被他人修改过，请刷新页面后重试");
    ErrorCode AFTER_SALE_WARRANTY_END_DATE_REQUIRED = new ErrorCode(1_020_014_009, "保存失败，原因：无法确定质保到期日期（请检查完工验收日期与质保年限）");

    // ========== 售后设置 1_020_015_000 ==========
    ErrorCode AFTER_SALE_CONFIG_NOT_EXISTS = new ErrorCode(1_020_015_000, "售后设置不存在");

    // ========== 售后回访计划 1_020_016_000 ==========
    ErrorCode AFTER_SALE_VISIT_PLAN_NOT_EXISTS = new ErrorCode(1_020_016_000, "回访计划不存在");
    ErrorCode AFTER_SALE_VISIT_PLAN_NOT_ENABLE = new ErrorCode(1_020_016_001, "回访计划不是启用状态，无法生成回访任务");
    ErrorCode AFTER_SALE_VISIT_PLAN_CYCLE_INVALID = new ErrorCode(1_020_016_002, "回访计划的周期配置不正确");
    ErrorCode AFTER_SALE_VISIT_PLAN_UPDATE_FAIL_NOT_ENABLE = new ErrorCode(1_020_016_003, "更新回访计划失败，原因：计划已生成回访任务，不可编辑周期配置");
    ErrorCode AFTER_SALE_VISIT_PLAN_DELETE_FAIL_EXISTS_TASK = new ErrorCode(1_020_016_004, "删除回访计划失败，原因：计划已生成回访任务，不允许删除");
    ErrorCode AFTER_SALE_VISIT_PLAN_DATE_RANGE_INVALID = new ErrorCode(1_020_016_005, "回访计划的生效结束日期不能早于生效开始日期");
    ErrorCode AFTER_SALE_VISIT_PLAN_WARRANTY_EXISTS = new ErrorCode(1_020_016_006, "该质保档案已绑定回访计划，不能重复创建");
    ErrorCode AFTER_SALE_VISIT_PLAN_START_DATE_BEFORE_ACCEPT_DATE = new ErrorCode(1_020_016_007, "回访计划开始日期不能早于关联质保档案的完工日期");

    // ========== 售后回访任务 1_020_017_000 ==========
    ErrorCode AFTER_SALE_VISIT_TASK_NOT_EXISTS = new ErrorCode(1_020_017_000, "回访任务不存在");
    ErrorCode AFTER_SALE_VISIT_TASK_FINISH_FAIL_STATUS = new ErrorCode(1_020_017_001, "完成回访任务失败，原因：回访任务已处于已完成状态");
    ErrorCode AFTER_SALE_VISIT_TASK_OPERATE_DENIED = new ErrorCode(1_020_017_002, "回访任务操作失败，原因：不是任务的处理人");
    ErrorCode AFTER_SALE_VISIT_TASK_WARRANTY_NOT_EXISTS = new ErrorCode(1_020_017_003, "回访任务关联的质保档案不存在");
    ErrorCode AFTER_SALE_VISIT_TASK_DELETE_FAIL_FINISH = new ErrorCode(1_020_017_004, "删除回访任务失败，原因：任务已完成，不允许删除");
    ErrorCode AFTER_SALE_VISIT_TASK_UPDATE_FAIL_FINISH = new ErrorCode(1_020_017_005, "更新回访任务失败，原因：任务已完成，不允许编辑");
    ErrorCode AFTER_SALE_VISIT_TASK_FINISH_FAIL_RECORD_NOT_FILLED = new ErrorCode(1_020_017_006, "完成回访任务失败，原因：关联的回访记录尚未填写完成");
    ErrorCode AFTER_SALE_VISIT_TASK_NOT_ARRIVED = new ErrorCode(1_020_017_007, "提交回访记录失败，原因：未到计划回访日期（{}），暂不允许提交回访");

    // ========== 售后回访记录 1_020_018_000 ==========
    ErrorCode AFTER_SALE_VISIT_RECORD_NOT_EXISTS = new ErrorCode(1_020_018_000, "回访记录不存在");
    ErrorCode AFTER_SALE_VISIT_RECORD_DELETE_DENIED = new ErrorCode(1_020_018_001, "删除回访记录失败，原因：没有权限");
    ErrorCode AFTER_SALE_VISIT_RECORD_OPERATE_DENIED = new ErrorCode(1_020_018_002, "回访记录操作失败，原因：不是登记人本人，且没有管理员权限");
    ErrorCode AFTER_SALE_VISIT_RECORD_TIME_FUTURE = new ErrorCode(1_020_018_003, "回访时间不能晚于今天");
    ErrorCode AFTER_SALE_VISIT_RECORD_TIME_BEFORE_ACCEPT = new ErrorCode(1_020_018_004, "回访时间不能早于项目完工验收日期");
    ErrorCode AFTER_SALE_VISIT_RECORD_TASK_EXISTS = new ErrorCode(1_020_018_005, "该回访任务已存在回访记录，请直接编辑原记录");

    // ========== 售后问题工单 1_020_019_000 ==========
    ErrorCode AFTER_SALE_TICKET_NOT_EXISTS = new ErrorCode(1_020_019_000, "问题工单不存在");
    ErrorCode AFTER_SALE_TICKET_NO_EXISTS = new ErrorCode(1_020_019_001, "生成工单编号重复，请重试");
    ErrorCode AFTER_SALE_TICKET_UPDATE_STATUS_FAIL_INVALID = new ErrorCode(1_020_019_002, "更新工单状态失败，原因：工单当前状态不允许该操作");
    ErrorCode AFTER_SALE_TICKET_ASSIGN_FAIL_DENIED = new ErrorCode(1_020_019_003, "指派工单失败，原因：没有权限");
    ErrorCode AFTER_SALE_TICKET_OPERATE_DENIED = new ErrorCode(1_020_019_004, "工单操作失败，原因：不是工单的处理人或创建人");
    ErrorCode AFTER_SALE_TICKET_DELETE_DENIED = new ErrorCode(1_020_019_005, "删除工单失败，原因：工单已处理，不允许删除");
    ErrorCode AFTER_SALE_TICKET_CREATE_FAIL_CUSTOMER_NOT_EXISTS = new ErrorCode(1_020_019_006, "创建工单失败，原因：客户不存在");
    ErrorCode AFTER_SALE_TICKET_UPDATE_FAIL_FINISH = new ErrorCode(1_020_019_007, "更新工单失败，原因：工单已完成或已关闭，不允许编辑");
    ErrorCode AFTER_SALE_TICKET_FINISH_NEED_RECORD = new ErrorCode(1_020_019_008, "完成工单失败，原因：请先填写工单记录（处理结果）");

    // ========== 售后转商机关联 1_020_020_000 ==========
    ErrorCode AFTER_SALE_BUSINESS_REL_NOT_EXISTS = new ErrorCode(1_020_020_000, "售后转商机关联不存在");
    ErrorCode AFTER_SALE_BUSINESS_CREATE_FAIL_WARRANTY_NOT_EXISTS = new ErrorCode(1_020_020_001, "生成商机失败，原因：质保档案不存在");

}
