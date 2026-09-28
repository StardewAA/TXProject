package cn.iocoder.txgy.module.bidding.dal.mysql.ruleconfig;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.module.bidding.dal.dataobject.ruleconfig.BiddingRuleConfigAuditDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 规则读取审计 Mapper（只追加）。
 *
 * <p>规范出处：《电子资质七大模块技术方案.md》§8.2；等保 2.0 三级（日志表仅授 INSERT）。
 * <p>设计遵循/针对：与 history Mapper 同口径——只暴露查询/插入，从 Mapper 层杜绝对审计记录的改写；
 * 因此本接口不声明任何 update/delete 语义方法。
 * <p>业务目标：读取审计不可被业务代码顺手修改或删除。
 */
@Mapper
public interface BiddingRuleConfigAuditMapper extends BaseMapperX<BiddingRuleConfigAuditDO> {
}
