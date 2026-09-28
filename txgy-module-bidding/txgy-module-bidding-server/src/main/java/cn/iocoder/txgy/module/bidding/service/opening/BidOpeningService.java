package cn.iocoder.txgy.module.bidding.service.opening;

import cn.iocoder.txgy.module.bidding.controller.admin.opening.vo.OpeningCreateReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.opening.vo.OpeningEnterReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.opening.vo.OpeningChantReqVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.opening.BiddingBidOpeningDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.opening.BiddingOpeningMemberDO;

import java.util.List;

/**
 * 开标房间服务（在线开标室：成员列表 + 进房身份认证）。
 *
 * <p>用户裁定 2026-09-18：开标视频不做；房间必须做好——显示房间里有谁（成员列表）、
 * 进房间的身份认证（对接外部 CA SDK，CaClientFacade 占位，SDK 到位后替换）。
 * 房间生命周期：WAITING（未开放）→ DECRYPTING（解密中）→ OPENING（唱标中）→ DONE（已结束）。
 */
public interface BidOpeningService {

    /** 创建开标房间（招标项目一条开标记录；平台方主持人在 PENDING_OPEN 后调用）。 */
    BiddingBidOpeningDO createOpening(OpeningCreateReqVO reqVO);

    /** 开放房间（WAITING→DECRYPTING，成员可进房）。 */
    BiddingBidOpeningDO openRoom(Long openingId, String operatorName);

    /** 成员进房（身份认证：外部 CA SDK 占位——默认通过，SDK 到位后调 CaClientFacade 验签/验证书）。 */
    BiddingOpeningMemberDO enterRoom(OpeningEnterReqVO reqVO);

    /** 成员离房。 */
    void leaveRoom(Long openingId, Long userId);

    /** 唱标（解密完成、宣读报价；DECRYPTING→OPENING，落 openList JSON）。 */
    BiddingBidOpeningDO chant(OpeningChantReqVO reqVO);

    /** 结束开标（OPENING→DONE，房间关闭）。 */
    BiddingBidOpeningDO finishOpening(Long openingId);

    /** 房间成员列表（显示房间里有谁）。 */
    List<BiddingOpeningMemberDO> listMembers(Long openingId);

    /** 查开标房间（按招标项目）。 */
    BiddingBidOpeningDO getByTenderProject(String tenderProjectCode);

    /**
     * 开标记录分页（按标段过滤；契约《投标与资格预审多文件化-2026-10-02》R5：标段详情页按标段聚合开标记录；
     * bidSectionCode 为空即不带标段条件，返回全部。tenant 隔离由拦截器统一处理）。
     */
    List<BiddingBidOpeningDO> listBySection(String bidSectionCode);

    /** 查开标房间（按 id）。 */
    BiddingBidOpeningDO getOpening(Long openingId);
}
