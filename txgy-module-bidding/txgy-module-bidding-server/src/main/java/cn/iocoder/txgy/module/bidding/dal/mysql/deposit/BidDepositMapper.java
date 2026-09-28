package cn.iocoder.txgy.module.bidding.dal.mysql.deposit;

// 规范依据：《招投标模块开发规范》§5 数据层（Mapper 继承 BaseMapperX，单表零 SQL 优先）

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.bidding.controller.admin.deposit.vo.BidDepositPageReqVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.deposit.BidDepositDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 投标保证金 Mapper。
 *
 * <p>业务目标：保证金登记/查询；按标段+主体幂等去重（同一标段同一主体只登记一次）。
 */
@Mapper
public interface BidDepositMapper extends BaseMapperX<BidDepositDO> {

    /** 按标段+主体查保证金（幂等：同一标段同一主体只登记一次）。 */
    default BidDepositDO selectBySectionAndSubject(String bidSectionCode, Long subjectId) {
        return selectOne(BidDepositDO::getBidSectionCode, bidSectionCode,
                BidDepositDO::getSubjectId, subjectId);
    }

    /** 保证金台账分页（招标方按项目/标段/主体过滤）。 */
    default PageResult<BidDepositDO> selectPage(BidDepositPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<BidDepositDO>()
                .eqIfPresent(BidDepositDO::getTenderProjectCode, reqVO.getTenderProjectCode())
                .eqIfPresent(BidDepositDO::getBidSectionCode, reqVO.getBidSectionCode())
                .eqIfPresent(BidDepositDO::getSubjectId, reqVO.getSubjectId())
                .orderByDesc(BidDepositDO::getId));
    }
}
