package cn.iocoder.txgy.module.hrm.dal.mysql.salary.slip;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.hrm.controller.admin.salary.vo.slip.sendrecord.HrmSalarySlipSendRecordPageReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.salary.slip.HrmSalarySlipSendRecordDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface HrmSalarySlipSendRecordMapper extends BaseMapperX<HrmSalarySlipSendRecordDO> {

    default PageResult<HrmSalarySlipSendRecordDO> selectPage(HrmSalarySlipSendRecordPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<HrmSalarySlipSendRecordDO>()
                .eqIfPresent(HrmSalarySlipSendRecordDO::getYear, reqVO.getYear())
                .eqIfPresent(HrmSalarySlipSendRecordDO::getMonth, reqVO.getMonth())
                .orderByDesc(HrmSalarySlipSendRecordDO::getYear)
                .orderByDesc(HrmSalarySlipSendRecordDO::getMonth));
    }

}
