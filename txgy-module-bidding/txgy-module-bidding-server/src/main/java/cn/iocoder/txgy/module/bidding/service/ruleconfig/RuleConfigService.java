package cn.iocoder.txgy.module.bidding.service.ruleconfig;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.bidding.controller.admin.meta.vo.RuleConfigItemRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.ruleconfig.vo.RuleConfigAuditPageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.ruleconfig.vo.RuleConfigAuditRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.ruleconfig.vo.RuleConfigHistoryRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.ruleconfig.vo.RuleConfigSaveReqVO;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 规则配置服务（动态参数的唯一读取入口）。
 *
 * <p>规范出处：《电子资质七大模块技术方案.md》§8.1（解析优先级 / 版本 / 灰度 / 回滚）；契约 §1.2。
 * <p>为什么业务代码只能通过本接口取数值：一旦允许"直接在 Service 里写常量"，动态配置就名存实亡；
 * 所有阈值必须经此读取，才能保证"改配置即改行为"，也才能在业务表落 rule_version 以复算历史判定。
 * <p>业务目标：数值参数集中受控，业务代码零硬编码；任何一次判定都可追溯到当时的规则版本。
 */
public interface RuleConfigService {

    /** 取生效配置项（含来源与版本）；配置缺失时回落代码兜底，永不返回 null。 */
    RuleConfigItemRespVO getItem(String ruleKey);

    /**
     * 解析规则并写入**读取审计**（业务判定点专用）。
     *
     * <p>规范出处：《电子资质七大模块技术方案.md》§8.2（审计与追溯）；《电子证件全流程管理_数据库设计.md》v3.3 §13；
     * 等保 2.0 三级（日志保留 ≥3 年、日志表仅授 INSERT）。
     *
     * <p>为什么与 {@link #getItem} 分开：规则读取在热路径上（含缓存命中），逐次落库会把一次内存读变成一次 DB 写。
     * 因此只有**业务判定点**（安许 A/B/C 风险重算、人员风险判定等）才调用本方法留下"当时依据哪一版、命中哪一层"的证据。
     *
     * <p>为什么写入是尽力而为的：审计是旁证，不能因为日志表写失败导致业务取不到规则值（取不到值=判定失败）；
     * 失败按 ERROR 告警，避免"争议时拿不出记录"被静默掩盖。
     *
     * @param ruleKey  规则键
     * @param bizScene 业务场景（如 SAFETY_RISK_EVAL / PERSONNEL_RISK），便于按场景统计与举证
     * @return 与 {@link #getItem} 相同的解析结果
     */
    RuleConfigItemRespVO resolveWithAudit(String ruleKey, String bizScene);

    /**
     * 规则读取审计分页（事后追责/争议举证）。
     *
     * <p>规范出处：《电子资质七大模块技术方案.md》§8.2。
     * <p>业务目标：一次查询还原"某次判定读了哪些规则、命中了哪一层、谁读的"。
     */
    PageResult<RuleConfigAuditRespVO> getAuditPage(RuleConfigAuditPageReqVO reqVO);

    /** 按分组取生效配置（前端配置页/字典式读取）。group 为空则返回全部分组。 */
    List<RuleConfigItemRespVO> listByGroup(String group);

    /** 取整数配置（缺失击穿兜底；仍无则抛配置不存在）。 */
    int getInt(String ruleKey);

    /** 取长整数配置。 */
    long getLong(String ruleKey);

    /** 取小数配置（如相似度阈值 0.70）。 */
    BigDecimal getDecimal(String ruleKey);

    /** 取布尔开关。 */
    boolean getBool(String ruleKey);

    /** 取字符串配置（如算法标识 SM4-GCM）。 */
    String getString(String ruleKey);

    /** 取整数数组配置（如多级预警阈值 [90,60,30,7]）。 */
    List<Integer> getIntList(String ruleKey);

    /** 取"字符串 → 整数"映射配置（如各证照类别的法定下限）。 */
    Map<String, Integer> getIntMap(String ruleKey);

    /** 保存/发布配置（含类型校验、边界校验、跨键一致性校验、历史留痕）。返回配置主键。 */
    Long saveOrPublish(RuleConfigSaveReqVO reqVO);

    /** 回滚到指定历史版本（以"新版本复制旧值"实现，保证历史不可改写）。返回新配置主键。 */
    Long rollback(String ruleKey, Integer targetVersion, String changeReason);

    /** 版本历史（倒序，含哈希链）。 */
    List<RuleConfigHistoryRespVO> history(String ruleKey);

    /** 清空本地缓存（发布/回滚后调用；多实例场景由各实例按 TTL 自然过期或经广播失效）。 */
    void evictCache();

    /**
     * 校验"预警天数是否满足法定下限"（方案 §3 拦截逻辑；契约 §1.2 legalFloor 的后端裁判）。
     *
     * <p>为什么放在规则配置服务而不是预警服务：下限本身就是配置（可按证照类别差异化且会随法规更新），
     * 校验逻辑与配置同源，避免两处各写一份阈值比较。
     *
     * @param certCategory 证照类别 code（如 SEWAGE_PERMIT）
     * @param thresholds   待保存的预警档位（取最大值与下限比较）
     */
    void validateAlertThresholds(String certCategory, List<Integer> thresholds);
}
