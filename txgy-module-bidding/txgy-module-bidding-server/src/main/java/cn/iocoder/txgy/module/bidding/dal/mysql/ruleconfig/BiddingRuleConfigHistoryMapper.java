package cn.iocoder.txgy.module.bidding.dal.mysql.ruleconfig;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.module.bidding.dal.dataobject.ruleconfig.BiddingRuleConfigHistoryDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 规则变更历史 Mapper（只追加）。
 *
 * <p>规范出处：《电子资质七大模块技术方案.md》§8.2（哈希链审计）；《电子证件全流程管理_数据库设计.md》v3.3 §13。
 * <p>设计遵循/针对：只提供查询与插入（不提供 update/delete 语义方法），从 Mapper 层就杜绝对审计记录的改写；
 * 取链尾用 id 倒序 + LIMIT 1（id 单调，等价于取最后一条）。
 * <p>业务目标：审计链不可被业务代码顺手修改或删除。
 */
@Mapper
public interface BiddingRuleConfigHistoryMapper extends BaseMapperX<BiddingRuleConfigHistoryDO> {

    /** 取某键最近一条历史（哈希链的链尾，用于计算 prev_hash）。 */
    default BiddingRuleConfigHistoryDO selectLatest(String ruleKey) {
        return selectOne(new LambdaQueryWrapper<BiddingRuleConfigHistoryDO>()
                .eq(BiddingRuleConfigHistoryDO::getRuleKey, ruleKey)
                .orderByDesc(BiddingRuleConfigHistoryDO::getId)
                .last("LIMIT 1"));
    }

    /** 取某键的版本历史（倒序）。 */
    default List<BiddingRuleConfigHistoryDO> selectByRuleKey(String ruleKey) {
        return selectList(new LambdaQueryWrapper<BiddingRuleConfigHistoryDO>()
                .eq(BiddingRuleConfigHistoryDO::getRuleKey, ruleKey)
                .orderByDesc(BiddingRuleConfigHistoryDO::getId));
    }
}
