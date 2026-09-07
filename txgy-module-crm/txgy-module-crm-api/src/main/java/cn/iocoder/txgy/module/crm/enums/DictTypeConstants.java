package cn.iocoder.txgy.module.crm.enums;

/**
 * CRM 字典类型的枚举类
 *
 * @author OPENLAB BS
 */
public interface DictTypeConstants {

    String CRM_CUSTOMER_INDUSTRY = "crm_customer_industry"; // CRM 客户所属行业（合并：下游+上游，用于展示）
    String CRM_CUSTOMER_CATEGORY = "crm_customer_category"; // CRM 客户类别（下游客户/上游供应商）
    String CRM_CUSTOMER_INDUSTRY_DOWNSTREAM = "crm_customer_industry_downstream"; // CRM 下游客户行业
    String CRM_CUSTOMER_INDUSTRY_UPSTREAM = "crm_customer_industry_upstream"; // CRM 上游供应商行业
    String CRM_CUSTOMER_LEVEL = "crm_customer_level"; // CRM 客户等级
    String CRM_CUSTOMER_SOURCE = "crm_customer_source"; // CRM 客户来源
    String CRM_AUDIT_STATUS = "crm_audit_status"; // CRM 审批状态
    String CRM_PRODUCT_UNIT = "crm_product_unit"; // CRM 产品单位
    String CRM_PRODUCT_STATUS = "crm_product_status"; // CRM 产品状态
    String CRM_FOLLOW_UP_TYPE = "crm_follow_up_type"; // CRM 跟进方式
    String CRM_RECEIVABLE_RETURN_TYPE = "crm_receivable_return_type"; // CRM 回款方式

}
