package cn.iocoder.txgy.module.bidding.service.deposit;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.bidding.controller.admin.deposit.vo.BidDepositPageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.deposit.vo.BidDepositRefundReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.deposit.vo.BidDepositSaveReqVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.deposit.BidDepositDO;

/**
 * 投标保证金服务。
 *
 * <p>业务目标：登记投标保证金（登记时校验金额 ≤ 估算价 2%，条例第26条）、查询、分页。
 */
public interface BidDepositService {

    /** 登记保证金（含 2% 限额校验；同一标段同一主体幂等）。 */
    Long save(BidDepositSaveReqVO reqVO);

    /** 按主键查询。 */
    BidDepositDO get(Long id);

    /** 分页查询。 */
    PageResult<BidDepositDO> page(BidDepositPageReqVO reqVO);

    /** 退还保证金（记录退还时间/状态；已退还不可重复退还）。 */
    Long refund(BidDepositRefundReqVO reqVO);
}
