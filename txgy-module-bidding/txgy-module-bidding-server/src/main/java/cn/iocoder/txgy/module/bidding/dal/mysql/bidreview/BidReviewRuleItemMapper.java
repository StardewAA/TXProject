package cn.iocoder.txgy.module.bidding.dal.mysql.bidreview;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.bidding.controller.admin.bidreview.vo.BidReviewRuleItemPageReqVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.bidreview.BidReviewRuleItemDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 智慧标书审查规则项 Mapper。
 *
 * <p>规范依据：§5.6（命中 uk_rule_code / idx_set_enable，不做全表扫描）。
 */
@Mapper
public interface BidReviewRuleItemMapper extends BaseMapperX<BidReviewRuleItemDO> {

    /** 规则集下的启用规则（审查用）。 */
    default List<BidReviewRuleItemDO> selectEnabledListByRuleSetId(Long ruleSetId) {
        return selectList(new LambdaQueryWrapperX<BidReviewRuleItemDO>()
                .eq(BidReviewRuleItemDO::getRuleSetId, ruleSetId)
                .eq(BidReviewRuleItemDO::getEnable, true)
                .orderByAsc(BidReviewRuleItemDO::getSort)
                .orderByAsc(BidReviewRuleItemDO::getId));
    }

    /** 规则集下全部规则（管理端维护用）。 */
    default List<BidReviewRuleItemDO> selectListByRuleSetId(Long ruleSetId) {
        return selectList(new LambdaQueryWrapperX<BidReviewRuleItemDO>()
                .eq(BidReviewRuleItemDO::getRuleSetId, ruleSetId)
                .orderByAsc(BidReviewRuleItemDO::getSort)
                .orderByAsc(BidReviewRuleItemDO::getId));
    }

    default PageResult<BidReviewRuleItemDO> selectPage(BidReviewRuleItemPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<BidReviewRuleItemDO>()
                .eqIfPresent(BidReviewRuleItemDO::getRuleSetId, reqVO.getRuleSetId())
                .likeIfPresent(BidReviewRuleItemDO::getRuleName, reqVO.getRuleName())
                .eqIfPresent(BidReviewRuleItemDO::getCategory, reqVO.getCategory())
                .eqIfPresent(BidReviewRuleItemDO::getRuleType, reqVO.getRuleType())
                .orderByAsc(BidReviewRuleItemDO::getSort));
    }
}
