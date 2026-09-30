package cn.iocoder.txgy.module.bidding.dal.mysql.contract;

// 规范依据：《招投标模块开发规范》§5 数据层（Mapper 继承 BaseMapperX，单表零 SQL 优先）

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.bidding.controller.admin.contract.vo.ContractPageReqVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.contract.ContractDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 工程合同 Mapper。
 *
 * <p>业务目标：合同登记/查询/分页。
 */
@Mapper
public interface ContractMapper extends BaseMapperX<ContractDO> {

    /** 合同台账分页（按招标项目/标段过滤）。 */
    default PageResult<ContractDO> selectPage(ContractPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<ContractDO>()
                .eqIfPresent(ContractDO::getTenderProjectCode, reqVO.getTenderProjectCode())
                .eqIfPresent(ContractDO::getBidSectionCode, reqVO.getBidSectionCode())
                .orderByDesc(ContractDO::getId));
    }

    /** 按招标项目取最新签订时间的合同（保证金退还 5 日时限的事实锚点，条例第58条）。 */
    default ContractDO selectLatestByProject(String tenderProjectCode) {
        return selectOne(new LambdaQueryWrapperX<ContractDO>()
                .eq(ContractDO::getTenderProjectCode, tenderProjectCode)
                .isNotNull(ContractDO::getSignTime)
                .orderByDesc(ContractDO::getSignTime)
                .last("LIMIT 1"));
    }
}
