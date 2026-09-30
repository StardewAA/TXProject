package cn.iocoder.txgy.module.bidding.dal.dataobject.subject;

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 所属企业（T 轨）。表：bidding_subject_company（DDL 见 sql/history/2026-09-18-subject-company.sql）。
 *
 * <p>业务口径（用户 2026-09-18）：**「所属企业」不是租户**。租户编号在超管的「全局租户选择」里属平台级；
 * 本实体是**租户自己维护的企业名录**——包含本企业（SELF）与其子公司/分公司（SUBSIDIARY/BRANCH），
 * 用于把证照 / 资质按"这笔资产登记在哪家公司名下"归集（持证主体选择、看板与筛选维度）。
 * 由此，租户可以管理自己子公司的证照，而不必依赖平台侧的租户切换。
 *
 * <p>规范出处：《招投标模块开发规范》§3.1（VO 命名）、§4.2（Mapper 继承 BaseMapperX）、
 * §4.4（字典存 code）、§4.5（乐观锁 version + 幂等）、§5 二（字段长度档位）、§5 五（T 轨租户隔离）。
 */
@TableName("bidding_subject_company")
@Data
@EqualsAndHashCode(callSuper = true)
public class SubjectCompanyDO extends TenantBaseDO {

    /** 主键（雪花 ID） */
    private Long id;
    /** 企业名称（对应规范字段「单位名称」；长度档位 128） */
    private String companyName;
    /** 统一社会信用代码（GB32100-2015 18 位；L2） */
    private String uscc;
    /** 企业类型 code（字典 subject_company_type：SELF 本企业 / SUBSIDIARY 子公司 / BRANCH 分公司） */
    private String companyType;
    /** 上级企业 id（顶级为 0；用于"本企业 → 子公司"从属） */
    private Long parentId;
    /** 上级企业名称快照（列表展示免联表；源变更不回改） */
    private String parentName;
    /** 法定代表人姓名（L3，日志脱敏） */
    private String legalPersonName;
    /** 联系人（L3） */
    private String contactName;
    /** 联系电话（L3，列表默认掩码） */
    private String contactPhone;
    /** 行政区划代码 */
    private String regionCode;
    /** 所属行业代码 */
    private String industryCode;
    /** 备注 */
    private String remark;
    /** 是否启用（停用不出现在选择器，不影响历史归属） */
    private Boolean enabled;
    /** 乐观锁版本（@Version 由 MyBatis-Plus 乐观锁拦截器生效） */
    @Version
    private Integer version;
}
