package cn.iocoder.txgy.module.bidding.service.bidreview;

import cn.iocoder.txgy.module.bidding.controller.admin.bidreview.vo.BidReviewAiProbeRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.bidreview.vo.BidReviewConfigRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.bidreview.vo.BidReviewConfigUpdateReqVO;

/**
 * 智慧标书审查 · AI 配置服务（页面可视、运行时可调）。
 *
 * <p>方案依据：§5.2（config/get、config/update、config/ai-probe）、§6.4（密钥不回显）。
 */
public interface BidReviewConfigService {

    BidReviewConfigRespVO getConfig();

    void updateConfig(BidReviewConfigUpdateReqVO reqVO);

    BidReviewAiProbeRespVO probe();
}
