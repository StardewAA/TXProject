package cn.iocoder.txgy.module.hrm.dal.mysql.salary.piece;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.hrm.controller.admin.salary.vo.piece.HrmSalaryPieceRecordPageReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.salary.piece.HrmSalaryPieceRecordDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.List;

@Mapper
public interface HrmSalaryPieceRecordMapper extends BaseMapperX<HrmSalaryPieceRecordDO> {

    default List<HrmSalaryPieceRecordDO> selectListByEmployeeIdsAndMonth(
            Collection<Long> employeeIds, Integer year, Integer month) {
        return selectList(new LambdaQueryWrapperX<HrmSalaryPieceRecordDO>()
                .in(HrmSalaryPieceRecordDO::getEmployeeId, employeeIds)
                .eq(HrmSalaryPieceRecordDO::getYear, year)
                .eq(HrmSalaryPieceRecordDO::getMonth, month)
                .orderByAsc(HrmSalaryPieceRecordDO::getId));
    }

    default List<HrmSalaryPieceRecordDO> selectListByEmployeeIdAndMonth(
            Long employeeId, Integer year, Integer month) {
        return selectList(new LambdaQueryWrapperX<HrmSalaryPieceRecordDO>()
                .eq(HrmSalaryPieceRecordDO::getEmployeeId, employeeId)
                .eq(HrmSalaryPieceRecordDO::getYear, year)
                .eq(HrmSalaryPieceRecordDO::getMonth, month)
                .orderByAsc(HrmSalaryPieceRecordDO::getId));
    }

    default cn.iocoder.txgy.framework.common.pojo.PageResult<HrmSalaryPieceRecordDO> selectPage(
            HrmSalaryPieceRecordPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<HrmSalaryPieceRecordDO>()
                .eqIfPresent(HrmSalaryPieceRecordDO::getEmployeeId, reqVO.getEmployeeId())
                .eqIfPresent(HrmSalaryPieceRecordDO::getYear, reqVO.getYear())
                .eqIfPresent(HrmSalaryPieceRecordDO::getMonth, reqVO.getMonth())
                .likeIfPresent(HrmSalaryPieceRecordDO::getItemName, reqVO.getItemName())
                .orderByDesc(HrmSalaryPieceRecordDO::getId));
    }

}
