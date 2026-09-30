package cn.iocoder.txgy.module.bidding.service.bidreview;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.bidding.controller.admin.bidreview.vo.BidReviewRuleItemPageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.bidreview.vo.BidReviewRuleItemSaveReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.bidreview.vo.BidReviewRuleSetPageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.bidreview.vo.BidReviewRuleSetSaveReqVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.bidreview.BidReviewRuleItemDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.bidreview.BidReviewRuleSetDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.bidreview.BidReviewTaskDO;

import java.util.List;

/**
 * 智慧标书审查 · 规则库服务（平台规则库 + 招标文件解析规则集）。
 *
 * <p>方案依据：§2.2（规则双轨）、§5.2（规则集/规则项接口）。
 */
public interface BidReviewRuleService {

    /** 为任务确定规则集：指定 > 招标文件解析（含哈希复用）> 平台默认规则集。 */
    BidReviewRuleSetDO resolveRuleSetForTask(BidReviewTaskDO task, String tenderText);

    /** 平台默认规则集（不存在则用内置种子创建）。 */
    BidReviewRuleSetDO getOrCreateDefaultRuleSet();

    Long createRuleSet(BidReviewRuleSetSaveReqVO reqVO);
    void updateRuleSet(BidReviewRuleSetSaveReqVO reqVO);
    void deleteRuleSet(Long id);
    void enableRuleSet(Long id, Boolean enable);
    BidReviewRuleSetDO getRuleSet(Long id);
    PageResult<BidReviewRuleSetDO> getRuleSetPage(BidReviewRuleSetPageReqVO reqVO);
    List<BidReviewRuleItemDO> getRuleItems(Long ruleSetId);

    Long createRuleItem(BidReviewRuleItemSaveReqVO reqVO);
    void updateRuleItem(BidReviewRuleItemSaveReqVO reqVO);
    void deleteRuleItem(Long id);
    PageResult<BidReviewRuleItemDO> getRuleItemPage(BidReviewRuleItemPageReqVO reqVO);
}
