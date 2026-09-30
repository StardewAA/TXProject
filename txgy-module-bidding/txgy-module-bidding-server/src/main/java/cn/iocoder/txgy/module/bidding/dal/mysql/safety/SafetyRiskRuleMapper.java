package cn.iocoder.txgy.module.bidding.dal.mysql.safety;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.module.bidding.dal.dataobject.safety.SafetyRiskRuleDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 安许风险规则 Mapper（契约 e2e P1 方案A）。
 *
 * <p>查询条件简单（名称/启用/等级），直接用 BaseMapperX#selectPage 的 LambdaQueryWrapperX，不写自定义 SQL。
 */
@Mapper
public interface SafetyRiskRuleMapper extends BaseMapperX<SafetyRiskRuleDO> {
}
