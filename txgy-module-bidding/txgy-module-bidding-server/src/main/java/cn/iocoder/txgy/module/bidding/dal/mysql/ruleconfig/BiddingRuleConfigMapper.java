package cn.iocoder.txgy.module.bidding.dal.mysql.ruleconfig;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.module.bidding.dal.dataobject.ruleconfig.BiddingRuleConfigDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 规则配置 Mapper。
 *
 * <p>规范出处：《电子资质七大模块技术方案.md》§8.1（三级回落解析 + 版本）；《招投标模块开发规范》§5 六-1（UK 含 tenant_id 与 deleted）。
 * <p>设计遵循/针对：查询全部命中 `uk_rule_key`(tenant_id,rule_key,version_no,deleted) 与 `idx_rule_group`(rule_group,status,deleted)，
 * 不做全表扫描；取"当前生效版本"用 version_no 倒序 + LIMIT 1，避免把历史版本拉进内存再排序。
 * <p>业务目标：解析规则只走索引，保证每次业务判定的取配置开销恒定（配置读取在热路径上）。
 */
@Mapper
public interface BiddingRuleConfigMapper extends BaseMapperX<BiddingRuleConfigDO> {

    /** 取某租户下某键的"最新一行"（不限状态，供发布/回滚时定位当前版本）。 */
    default BiddingRuleConfigDO selectLatest(Long tenantId, String ruleKey) {
        return selectOne(new LambdaQueryWrapper<BiddingRuleConfigDO>()
                .eq(BiddingRuleConfigDO::getTenantId, tenantId)
                .eq(BiddingRuleConfigDO::getRuleKey, ruleKey)
                .orderByDesc(BiddingRuleConfigDO::getVersionNo)
                .last("LIMIT 1"));
    }

    /** 取某键的全部版本（版本历史页用，倒序）。 */
    default List<BiddingRuleConfigDO> selectVersions(Long tenantId, String ruleKey) {
        return selectList(new LambdaQueryWrapper<BiddingRuleConfigDO>()
                .eq(BiddingRuleConfigDO::getTenantId, tenantId)
                .eq(BiddingRuleConfigDO::getRuleKey, ruleKey)
                .orderByDesc(BiddingRuleConfigDO::getVersionNo));
    }

    /** 按分组取启用中的配置（前端配置页/字典式读取用）。 */
    default List<BiddingRuleConfigDO> selectByGroup(Long tenantId, String ruleGroup) {
        return selectList(new LambdaQueryWrapper<BiddingRuleConfigDO>()
                .eq(BiddingRuleConfigDO::getTenantId, tenantId)
                .eq(ruleGroup != null && !ruleGroup.isBlank(), BiddingRuleConfigDO::getRuleGroup, ruleGroup)
                .in(BiddingRuleConfigDO::getStatus, "PUBLISHED", "GRAY")
                .orderByAsc(BiddingRuleConfigDO::getRuleKey)
                .orderByDesc(BiddingRuleConfigDO::getVersionNo));
    }

    /** 取某租户下的全部生效配置（四级回落中的"租户级"一层，一次取回减少往返）。 */
    default List<BiddingRuleConfigDO> selectAllPublished(Long tenantId) {
        return selectList(new LambdaQueryWrapper<BiddingRuleConfigDO>()
                .eq(BiddingRuleConfigDO::getTenantId, tenantId)
                .in(BiddingRuleConfigDO::getStatus, "PUBLISHED", "GRAY")
                .orderByAsc(BiddingRuleConfigDO::getRuleKey)
                .orderByDesc(BiddingRuleConfigDO::getVersionNo));
    }
}
