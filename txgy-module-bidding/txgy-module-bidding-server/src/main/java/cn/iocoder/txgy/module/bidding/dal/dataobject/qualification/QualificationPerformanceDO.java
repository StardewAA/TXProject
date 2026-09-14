package cn.iocoder.txgy.module.bidding.dal.dataobject.qualification;

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 企业工程业绩台账。表：qualification_performance（DDL sql/2026-09-09-…sql）。
 *
 * <p>规范出处：《电子证件全流程管理_数据库设计.md》v3.3 表 15-4 + §17.6（qual_id 必填归属资质，按资质聚合业绩核查）、
 * scale_metrics 规模指标 JSON、completion_date 年限窗口、is_platform_verified 四库一平台可线上核验。
 */
@TableName("qualification_performance")
@Data
@EqualsAndHashCode(callSuper = true)
public class QualificationPerformanceDO extends TenantBaseDO {

    private Long id;
    private Long entId;
    /** 归属资质主档（§17.6 必填） */
    private Long qualId;
    private String projectName;
    private String projectType;
    /** 规模指标 JSON */
    private String scaleMetrics;
    private BigDecimal contractAmount;
    private LocalDate completionDate;
    /** GENERAL 总承包 / SUB 分包 */
    private String role;
    private String qualityResult;
    private String ownerName;
    /** 是否已录入四库一平台 */
    private Boolean isPlatformVerified;
    /** 佐证文件 id 列表 JSON */
    private String evidenceFileIds;
}
