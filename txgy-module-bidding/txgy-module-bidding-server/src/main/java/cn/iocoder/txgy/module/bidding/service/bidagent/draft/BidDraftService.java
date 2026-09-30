package cn.iocoder.txgy.module.bidding.service.bidagent.draft;

import cn.iocoder.txgy.module.bidding.api.dto.bidagent.BidChapterDraftReqDTO;

/**
 * 章节草稿服务（按 (projectId, nodeId, version) 幂等）
 */
public interface BidDraftService {

    /**
     * 回写章节草稿
     *
     * @return 草稿编号
     */
    Long saveChapter(BidChapterDraftReqDTO reqDTO);
}
