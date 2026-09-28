package cn.iocoder.txgy.module.bidding.service.safety;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.bidding.controller.admin.safety.vo.SafetyRiskRulePageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.safety.vo.SafetyRiskRuleRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.safety.vo.SafetyRiskRuleSaveReqVO;

/**
 * 安许风险规则服务（契约《电子证照域e2e暴露问题-2026-09-21》P1 方案 A）。
 *
 * <p>与 rule-config 的边界：rule-config 是阈值键值（ruleKey/value/legalFloor）；本服务管**规则实体**
 * （名称/触发条件/影响范围/启用禁用），ruleKey 字段可选挂钩阈值键，不复制阈值语义。
 */
public interface SafetyRiskRuleService {

    /** 规则分页（ruleName 模糊 / enabled / riskLevel 过滤）。 */
    PageResult<SafetyRiskRuleRespVO> page(SafetyRiskRulePageReqVO reqVO);

    /** 新增规则（enabled 缺省=启用）；返回主键。 */
    Long create(SafetyRiskRuleSaveReqVO reqVO);

    /** 修改规则（id + version 必传，乐观锁 CAS）。 */
    void update(SafetyRiskRuleSaveReqVO reqVO);

    /** 启用/禁用（乐观锁不强制：前端契约该动作为具名开关；实现上走 @Version 兜底并发）。 */
    void updateStatus(Long id, Boolean enabled);

    /** 删除规则（逻辑删除）。 */
    void delete(Long id);
}
