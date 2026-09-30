package cn.iocoder.txgy.module.bidding.dal.dataobject.project;

// [规范依据]《招投标模块开发规范》§5 数据层 + 数据规范 V2.0 数据集 TenderProjectVO（招标项目）
// [业务目标] 招标项目主档：承载追溯主键 tender_project_code（公告/标段/备案/关注以其关联），并落招标人/代理/方式/监督部门等权威字段
// [为什么] tender_project_code 是跨域追溯的第二主键（notice.tender_project_code、filing.tender_project_code、follow_key 维度之一），
//           此前无主档导致按编号追溯只能拼装字符串、无法校验存在性与归属

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;


/**
 * 招标项目主档（数据集 TenderProjectVO）。
 */
@TableName("bidding_tender_project")
@Data
@EqualsAndHashCode(callSuper = true)
public class TenderProjectDO extends TenantBaseDO {

    /** 主键（雪花） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    /** 招标项目编号（TENDER_PROJECT_CODE；租户内唯一，全链路追溯主键） */
    private String tenderProjectCode;
    /** 所属项目编号（PROJECT_CODE → bidding_project.project_code） */
    private String projectCode;
    /** 招标项目名称（TENDER_PROJECT_NAME） */
    private String tenderProjectName;
    /** 招标项目类型（TENDER_PROJECT_TYPE） */
    private String tenderProjectType;
    /** 行政区划（REGION_CODE） */
    private String regionCode;
    /** 招标内容（TENDER_CONTENT） */
    private String tenderContent;
    /** 招标人名称（TENDERER_NAME） */
    private String tendererName;
    /** 招标人代码类型（TENDERER_CODE_TYPE） */
    private String tendererCodeType;
    /** 招标人代码（TENDERER_CODE） */
    private String tendererCode;
    /** 招标代理机构名称（TENDER_AGENCY_NAME） */
    private String tenderAgencyName;
    /** 代理机构代码类型（TENDER_AGENCY_CODE_TYPE） */
    private String tenderAgencyCodeType;
    /** 代理机构代码（TENDER_AGENCY_CODE） */
    private String tenderAgencyCode;
    /** 招标方式（TENDER_MODE） */
    private String tenderMode;
    /** 资格审查方式（QULIFICATION_CHECK_MODE；规范原文拼写照录，勿改） */
    private String qulificationCheckMode;
    /** 招标组织形式（TENDER_ORGANIZE_FORM） */
    private String tenderOrganizeForm;
    /** 监督部门名称（SUPERVISE_DEPT_NAME） */
    private String superviseDeptName;
    /** 监督部门代码（SUPERVISE_DEPT_CODE） */
    private String superviseDeptCode;
    /** 审批部门名称（APPROVE_DEPT_NAME） */
    private String approveDeptName;
    /** 审批部门代码（APPROVE_DEPT_CODE） */
    private String approveDeptCode;
    /** 内部管理态：ACTIVE/CLOSED */
    private String state;
    /** TenderProjectVO 全量字段快照 */
    private String fieldsJson;
    /** 备注 */
    private String remark;
}
