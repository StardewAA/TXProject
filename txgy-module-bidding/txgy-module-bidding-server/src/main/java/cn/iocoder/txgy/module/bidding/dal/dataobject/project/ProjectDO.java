package cn.iocoder.txgy.module.bidding.dal.dataobject.project;

// [规范依据]《招投标模块开发规范》§5 数据层（bidding_<域>_<实体> 命名、T 轨 TenantBaseDO 租户隔离、JSON 兜底列）
//           + 公共资源交易平台系统数据规范 V2.0 数据集 ProjectVO（项目）
// [业务目标] 项目主档：为"一个项目可追溯全部信息"提供锚点（公告/外部公告/备案/关注/主体均以 project_code 关联）
// [为什么] 既有 notice/external/filing/follow 只存项目号字段、无权威主档（规模/资金来源/审批等无处落），追溯口径不一

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 项目主档（数据集 ProjectVO；权威字段全量落 fieldsJson，列表/检索列单列存储）。
 */
@TableName("bidding_project")
@Data
@EqualsAndHashCode(callSuper = true)
public class ProjectDO extends TenantBaseDO {

    /** 主键（雪花；表 id 无自增） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    /** 项目编号（ProjectVO.PROJECT_CODE；租户内唯一） */
    private String projectCode;
    /** 项目名称（PROJECT_NAME） */
    private String projectName;
    /** 行政区划代码（REGION_CODE，GB/T 2260） */
    private String regionCode;
    /** 投资项目统一代码（INVEST_PROJECT_CODE） */
    private String investProjectCode;
    /** 关联项目编号（RELATE_CODE） */
    private String relateCode;
    /** 建设地点（ADDRESS） */
    private String address;
    /** 法定代表人（LEGAL_PERSON） */
    private String legalPerson;
    /** 行业类别（INDUSTRIES_TYPE，GB/T 4754） */
    private String industriesType;
    /** 资金来源（FUND_SOURCE） */
    private String fundSource;
    /** 出资比例（CONTRIBUTION_SCALE） */
    private String contributionScale;
    /** 项目规模（PROJECT_SCALE） */
    private String projectScale;
    /** 审批部门名称（APPROVAL_DEPT_NAME） */
    private String approvalDeptName;
    /** 审批部门代码（APPROVAL_DEPT_CODE） */
    private String approvalDeptCode;
    /** 审批文号（APPROVAL_DOC_NO） */
    private String approvalDocNo;
    /** 内部管理态：ACTIVE=进行中 / CLOSED=已结束（数据集无状态字段，本模块管理需要自建） */
    private String state;
    /** ProjectVO 数据集全量字段快照（key=规范英文名） */
    private String fieldsJson;
    /** 备注 */
    private String remark;
}
