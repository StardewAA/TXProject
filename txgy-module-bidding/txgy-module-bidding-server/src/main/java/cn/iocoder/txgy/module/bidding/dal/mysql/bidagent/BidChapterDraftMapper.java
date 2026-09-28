package cn.iocoder.txgy.module.bidding.dal.mysql.bidagent;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.bidding.dal.dataobject.bidagent.BidChapterDraftDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 章节草稿 Mapper
 */
@Mapper
public interface BidChapterDraftMapper extends BaseMapperX<BidChapterDraftDO> {

    default BidChapterDraftDO selectByProjectIdAndNodeIdAndVersion(Long projectId, String nodeId, Integer version) {
        return selectOne(new LambdaQueryWrapperX<BidChapterDraftDO>()
                .eq(BidChapterDraftDO::getProjectId, projectId)
                .eq(BidChapterDraftDO::getNodeId, nodeId)
                .eq(BidChapterDraftDO::getVersion, version));
    }
}
