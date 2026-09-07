package cn.iocoder.txgy.module.bidding.service.crawlexternal;
// 规范依据：设计文档 v0.6 §三（批次汇总/调度与人工跑批）；《招投标模块开发规范》§4.2

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.bidding.controller.admin.crawlexternal.vo.CrawlJobPageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.crawlexternal.vo.CrawlJobRespVO;

/**
 * 采集批次查询（G 轨，管理面只读；批次创建由调度/人工触发流程负责）。
 */
public interface CrawlJobService {

    /** 批次分页 */
    PageResult<CrawlJobRespVO> getJobPage(CrawlJobPageReqVO reqVO);

    /** 批次详情 */
    CrawlJobRespVO getJob(Long id);

}
