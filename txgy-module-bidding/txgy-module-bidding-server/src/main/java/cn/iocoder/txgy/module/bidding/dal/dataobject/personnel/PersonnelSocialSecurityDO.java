package cn.iocoder.txgy.module.bidding.dal.dataobject.personnel;

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 员工社保缴纳记录。表：personnel_social_security（DDL sql/history/2026-09-13-personnel-qualification.sql）。
 *
 * <p>规范出处：用户业务口径"核验员工社保缴纳单位、劳动合同主体是否与申报企业完全一致，杜绝人证分离和挂证"；
 * 《电子证件全流程管理_数据库设计.md》v3.3 表 15-3（social_security_status/social_security_tenant）。
 * <p>为什么这样写：一致性不是"有没有社保"而是"缴在哪家主体、缴到哪个月"，所以按"人 × 缴纳单位 × 月份"
 * 一行一条落库（UK 保证同月同单位不重复），核验时比对 ent_id/payer_credit_code 与申报企业即可判定挂证。
 * <p>业务目标：为资质申报与投标提供社保主体一致性的可核验证据链。
 */
@TableName("personnel_social_security")
@Data
@EqualsAndHashCode(callSuper = true)
public class PersonnelSocialSecurityDO extends TenantBaseDO {

    private Long id;
    /** 缴纳单位（本企业主体 id，一致性核验锚点） */
    private Long entId;
    private Long personId;
    /** 缴纳单位名称（照抄社保明细） */
    private String payerName;
    /** 缴纳单位统一社会信用代码（与申报企业不一致即判人证分离） */
    private String payerCreditCode;
    /** 缴纳月份（yyyy-MM） */
    private String payMonth;
    /** 缴纳状态 code：NORMAL/SUSPENDED/TRANSFERRED/NONE */
    private String payStatus;
    private BigDecimal payBase;
    private BigDecimal payAmount;
    private String socialNo;
    /** 数据来源 code */
    private String sourceChannel;
    /** 参保证明文件 id */
    private Long evidenceFileId;
    private String remark;
    @Version
    private Integer version;
}
