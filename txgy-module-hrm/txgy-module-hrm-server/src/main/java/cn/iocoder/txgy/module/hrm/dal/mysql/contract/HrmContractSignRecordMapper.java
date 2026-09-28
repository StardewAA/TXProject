package cn.iocoder.txgy.module.hrm.dal.mysql.contract;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.hrm.controller.admin.contract.vo.HrmContractSignRecordPageReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.contract.HrmContractSignRecordDO;
import cn.hutool.core.collection.CollUtil;
import org.apache.ibatis.annotations.Mapper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Mapper
public interface HrmContractSignRecordMapper extends BaseMapperX<HrmContractSignRecordDO> {

    default PageResult<HrmContractSignRecordDO> selectPage(HrmContractSignRecordPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<HrmContractSignRecordDO>()
                .eqIfPresent(HrmContractSignRecordDO::getContractId, reqVO.getContractId())
                .eqIfPresent(HrmContractSignRecordDO::getEmployeeId, reqVO.getEmployeeId())
                .likeIfPresent(HrmContractSignRecordDO::getContractNo, reqVO.getContractNo())
                .eqIfPresent(HrmContractSignRecordDO::getSignWay, reqVO.getSignWay())
                .eqIfPresent(HrmContractSignRecordDO::getStatus, reqVO.getStatus())
                .betweenIfPresent(HrmContractSignRecordDO::getSignTime, reqVO.getSignTime())
                .orderByDesc(HrmContractSignRecordDO::getId));
    }

    default List<HrmContractSignRecordDO> selectListByContractId(Long contractId) {
        return selectList(new LambdaQueryWrapperX<HrmContractSignRecordDO>()
                .eq(HrmContractSignRecordDO::getContractId, contractId)
                .orderByDesc(HrmContractSignRecordDO::getId));
    }

    default HrmContractSignRecordDO selectByContractIdAndStatus(Long contractId, Integer status) {
        return selectOne(new LambdaQueryWrapperX<HrmContractSignRecordDO>()
                .eq(HrmContractSignRecordDO::getContractId, contractId)
                .eq(HrmContractSignRecordDO::getStatus, status)
                .orderByDesc(HrmContractSignRecordDO::getId)
                .last("LIMIT 1"));
    }

    default List<HrmContractSignRecordDO> selectListByContractIds(Collection<Long> contractIds) {
        if (CollUtil.isEmpty(contractIds)) {
            return new ArrayList<>();
        }
        return selectList(new LambdaQueryWrapperX<HrmContractSignRecordDO>()
                .in(HrmContractSignRecordDO::getContractId, contractIds)
                .orderByDesc(HrmContractSignRecordDO::getId));
    }

}
