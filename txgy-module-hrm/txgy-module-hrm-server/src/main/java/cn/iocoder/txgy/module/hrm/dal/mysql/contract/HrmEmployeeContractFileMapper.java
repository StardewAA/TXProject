package cn.iocoder.txgy.module.hrm.dal.mysql.contract;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.hrm.dal.dataobject.contract.HrmEmployeeContractFileDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface HrmEmployeeContractFileMapper extends BaseMapperX<HrmEmployeeContractFileDO> {

    default List<HrmEmployeeContractFileDO> selectListByContractId(Long contractId) {
        return selectList(new LambdaQueryWrapperX<HrmEmployeeContractFileDO>()
                .eq(HrmEmployeeContractFileDO::getContractId, contractId)
                .orderByAsc(HrmEmployeeContractFileDO::getFileType)
                .orderByDesc(HrmEmployeeContractFileDO::getVersion));
    }

}
