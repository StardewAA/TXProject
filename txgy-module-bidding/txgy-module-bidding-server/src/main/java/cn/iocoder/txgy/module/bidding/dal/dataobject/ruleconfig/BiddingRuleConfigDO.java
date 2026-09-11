package cn.iocoder.txgy.module.bidding.dal.dataobject.ruleconfig;

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 全局规则配置（表 bidding_rule_config）。
 *
 * <p>规范出处：《电子资质七大模块技术方案.md》§8.1；DDL sql/2026-09-16-rule-config-center.sql；
 * 契约《电子证照模块-前端接口契约-v1.0.md》§1.2（GET /bidding/meta/configs 的字段口径）。
 * <p>设计遵循/针对：① tenant_id=0 为平台默认行，租户级行按"租户 → 灰度 → 平台 → 代码兜底"四级回落解析；
 * ② 每次发布 version_no 自增，业务表记 rule_version 以便评估结果可复算；
 * ③ status 参与解析的仅 PUBLISHED/GRAY（RuleConfigStatusEnum.isResolvable）。
 * <p>业务目标：一切数值参数（阈值/天数/比例/次数）集中于此，代码禁止硬编码；运营改配置即改行为。
 */
@TableName("bidding_rule_config")
@Data
@EqualsAndHashCode(callSuper = true)
public class BiddingRuleConfigDO extends TenantBaseDO {

    private Long id;
    /** 规则键（如 cert.expiring.window.days；点分命名） */
    private String ruleKey;
    /** 分组 code（RuleConfigGroupEnum） */
    private String ruleGroup;
    /** 值类型 code（RuleConfigValueTypeEnum） */
    private String valueType;
    /** 配置值（JSON 文本；标量也写 JSON，如 60 / "SM4-GCM" / [90,60,30,7]） */
    private String configValue;
    /** 允许边界（JSON，如 {"min":1,"max":365}）；发布时校验，防误配把业务配死 */
    private String legalFloor;
    /** 人话说明（可直接作为前端提示文案） */
    private String description;
    /** 法规/文档出处（争议时可追溯） */
    private String regulationRef;
    /** 版本号（每次发布 +1） */
    private Integer versionNo;
    /** 状态 code（RuleConfigStatusEnum） */
    private String status;
    /** 灰度范围（JSON：{"tenantIds":[...]} 或 {"percent":10}） */
    private String grayScope;
    /** 生效开始（NULL=立即） */
    private LocalDateTime effectiveFrom;
    /** 生效结束（NULL=长期） */
    private LocalDateTime effectiveTo;
    @Version
    private Integer version;
}
