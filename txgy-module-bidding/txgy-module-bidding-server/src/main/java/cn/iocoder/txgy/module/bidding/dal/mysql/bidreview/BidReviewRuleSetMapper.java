package cn.iocoder.txgy.module.bidding.dal.mysql.bidreview;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.bidding.controller.admin.bidreview.vo.BidReviewRuleSetPageReqVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.bidreview.BidReviewRuleSetDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 智慧标书审查规则集 Mapper。
 *
 * <p>设计遵循/针对：平台默认规则集（default_flag=true）是个人端"未传招标文件"时的兜底；
 * 按 tender_file_sha256 复用同份招标文件解析结果，避免重复调模型抽规则。
 */
@Mapper
public interface BidReviewRuleSetMapper extends BaseMapperX<BidReviewRuleSetDO> {

    default BidReviewRuleSetDO selectByCode(String ruleSetCode) {
        return selectOne(BidReviewRuleSetDO::getRuleSetCode, ruleSetCode);
    }

    /** 平台默认规则集（个人端兜底用）。 */
    default BidReviewRuleSetDO selectDefaultSet() {
        return selectOne(new LambdaQueryWrapperX<BidReviewRuleSetDO>()
                .eq(BidReviewRuleSetDO::getDefaultFlag, true)
                .eq(BidReviewRuleSetDO::getEnable, true)
                .orderByAsc(BidReviewRuleSetDO::getId)
                .last("LIMIT 1"));
    }

    /** 按招标文件哈希复用已解析规则集。 */
    default BidReviewRuleSetDO selectByTenderSha(String tenderFileSha256) {
        return selectOne(new LambdaQueryWrapperX<BidReviewRuleSetDO>()
                .eq(BidReviewRuleSetDO::getTenderFileSha256, tenderFileSha256)
                .eq(BidReviewRuleSetDO::getParseStatus, "SUCCESS")
                .orderByDesc(BidReviewRuleSetDO::getId)
                .last("LIMIT 1"));
    }

    default PageResult<BidReviewRuleSetDO> selectPage(BidReviewRuleSetPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<BidReviewRuleSetDO>()
                .likeIfPresent(BidReviewRuleSetDO::getRuleSetName, reqVO.getRuleSetName())
                .eqIfPresent(BidReviewRuleSetDO::getSource, reqVO.getSource())
                .eqIfPresent(BidReviewRuleSetDO::getEnable, reqVO.getEnable())
                .orderByDesc(BidReviewRuleSetDO::getId));
    }
}
