package cn.iocoder.txgy.module.bidding.dal.mysql.bidreview;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.bidding.dal.dataobject.bidreview.BidReviewContentDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 智慧标书审查版本化内容 Mapper。
 *
 * <p>规范依据：§5.3 第 2 款（追加式版本）。取"最新版本"用 version 倒序 + LIMIT 1，
 * 不把历史版本拉进内存再排序。
 */
@Mapper
public interface BidReviewContentMapper extends BaseMapperX<BidReviewContentDO> {

    default BidReviewContentDO selectLatest(Long bizId, String contentType) {
        return selectOne(new LambdaQueryWrapperX<BidReviewContentDO>()
                .eq(BidReviewContentDO::getBizId, bizId)
                .eq(BidReviewContentDO::getContentType, contentType)
                .orderByDesc(BidReviewContentDO::getVersion)
                .last("LIMIT 1"));
    }

    /** 当前最大版本号（追加新版本时 +1）。 */
    default Integer selectMaxVersion(Long bizId, String contentType) {
        BidReviewContentDO latest = selectLatest(bizId, contentType);
        return latest == null || latest.getVersion() == null ? 0 : latest.getVersion();
    }
}
