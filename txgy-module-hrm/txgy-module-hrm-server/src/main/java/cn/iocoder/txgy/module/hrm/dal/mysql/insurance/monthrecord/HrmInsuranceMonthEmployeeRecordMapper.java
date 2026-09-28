package cn.iocoder.txgy.module.hrm.dal.mysql.insurance.monthrecord;

import cn.hutool.core.map.MapUtil;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.collection.CollectionUtils;
import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.MPJLambdaWrapperX;
import cn.iocoder.txgy.module.hrm.controller.admin.insurance.vo.monthrecord.employee.HrmInsuranceMonthEmployeeRecordPageReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.insurance.monthrecord.HrmInsuranceMonthEmployeeRecordDO;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.List;
import java.util.Map;

@Mapper
public interface HrmInsuranceMonthEmployeeRecordMapper extends BaseMapperX<HrmInsuranceMonthEmployeeRecordDO> {

    /**
     * 按员工 + 年月查询月度社保记录（社保代扣取数用）
     */
    default HrmInsuranceMonthEmployeeRecordDO selectByEmployeeIdAndYearMonth(Long employeeId,
                                                                            Integer year, Integer month) {
        return selectOne(new cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX<
                HrmInsuranceMonthEmployeeRecordDO>()
                .eq(HrmInsuranceMonthEmployeeRecordDO::getEmployeeId, employeeId)
                .eq(HrmInsuranceMonthEmployeeRecordDO::getYear, year)
                .eq(HrmInsuranceMonthEmployeeRecordDO::getMonth, month)
                .orderByDesc(HrmInsuranceMonthEmployeeRecordDO::getId)
                .last("LIMIT 1"));
    }

    /**
     * 按年月查询全部员工月度社保记录（月度工资表批量取数用）
     */
    default java.util.List<HrmInsuranceMonthEmployeeRecordDO> selectListByYearMonth(Integer year, Integer month) {
        return selectList(new cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX<
                HrmInsuranceMonthEmployeeRecordDO>()
                .eq(HrmInsuranceMonthEmployeeRecordDO::getYear, year)
                .eq(HrmInsuranceMonthEmployeeRecordDO::getMonth, month));
    }

    default HrmInsuranceMonthEmployeeRecordDO selectByMonthRecordIdAndEmployeeId(Long monthRecordId, Long employeeId) {
        return selectLastOne(new LambdaQueryWrapperX<HrmInsuranceMonthEmployeeRecordDO>()
                .eq(HrmInsuranceMonthEmployeeRecordDO::getMonthRecordId, monthRecordId)
                .eq(HrmInsuranceMonthEmployeeRecordDO::getEmployeeId, employeeId)
                .orderByAsc(HrmInsuranceMonthEmployeeRecordDO::getId));
    }

    default List<HrmInsuranceMonthEmployeeRecordDO> selectListByMonthRecordId(Long monthRecordId) {
        return selectList(new LambdaQueryWrapperX<HrmInsuranceMonthEmployeeRecordDO>()
                .eq(HrmInsuranceMonthEmployeeRecordDO::getMonthRecordId, monthRecordId)
                .orderByDesc(HrmInsuranceMonthEmployeeRecordDO::getId));
    }

    default List<HrmInsuranceMonthEmployeeRecordDO> selectListByEmployeeIdAndYear(
            Long employeeId, Integer year) {
        return selectList(new LambdaQueryWrapperX<HrmInsuranceMonthEmployeeRecordDO>()
                .eq(HrmInsuranceMonthEmployeeRecordDO::getEmployeeId, employeeId)
                .eqIfPresent(HrmInsuranceMonthEmployeeRecordDO::getYear, year)
                .orderByDesc(HrmInsuranceMonthEmployeeRecordDO::getYear)
                .orderByDesc(HrmInsuranceMonthEmployeeRecordDO::getMonth)
                .orderByDesc(HrmInsuranceMonthEmployeeRecordDO::getId));
    }

    default List<HrmInsuranceMonthEmployeeRecordDO> selectListByMonthRecordIdAndStatus(Long monthRecordId,
                                                                                        Integer status) {
        return selectList(new LambdaQueryWrapperX<HrmInsuranceMonthEmployeeRecordDO>()
                .eq(HrmInsuranceMonthEmployeeRecordDO::getMonthRecordId, monthRecordId)
                .eq(HrmInsuranceMonthEmployeeRecordDO::getStatus, status)
                .orderByDesc(HrmInsuranceMonthEmployeeRecordDO::getId));
    }

    default List<HrmInsuranceMonthEmployeeRecordDO> selectListByYearAndMonthAndStatus(Integer year, Integer month,
                                                                                       Integer status) {
        return selectList(new LambdaQueryWrapperX<HrmInsuranceMonthEmployeeRecordDO>()
                .eq(HrmInsuranceMonthEmployeeRecordDO::getYear, year)
                .eq(HrmInsuranceMonthEmployeeRecordDO::getMonth, month)
                .eq(HrmInsuranceMonthEmployeeRecordDO::getStatus, status)
                .orderByDesc(HrmInsuranceMonthEmployeeRecordDO::getId));
    }

    default PageResult<HrmInsuranceMonthEmployeeRecordDO> selectPage(
            HrmInsuranceMonthEmployeeRecordPageReqVO reqVO, Collection<Long> employeeIds,
            Collection<Long> schemeIds) {
        return selectPage(reqVO, new LambdaQueryWrapperX<HrmInsuranceMonthEmployeeRecordDO>()
                .eq(HrmInsuranceMonthEmployeeRecordDO::getMonthRecordId, reqVO.getMonthRecordId())
                .eqIfPresent(HrmInsuranceMonthEmployeeRecordDO::getEmployeeId, reqVO.getEmployeeId())
                .eqIfPresent(HrmInsuranceMonthEmployeeRecordDO::getSchemeId, reqVO.getSchemeId())
                .eqIfPresent(HrmInsuranceMonthEmployeeRecordDO::getStatus, reqVO.getStatus())
                .inIfPresent(HrmInsuranceMonthEmployeeRecordDO::getEmployeeId, employeeIds)
                .inIfPresent(HrmInsuranceMonthEmployeeRecordDO::getSchemeId, schemeIds)
                .orderByDesc(HrmInsuranceMonthEmployeeRecordDO::getId));
    }

    default Long selectCountBySchemeId(Long schemeId) {
        return selectCount(HrmInsuranceMonthEmployeeRecordDO::getSchemeId, schemeId);
    }

    default Map<Long, Long> selectCountMapBySchemeIds(Collection<Long> schemeIds) {
        List<Map<String, Object>> result = selectMaps(new MPJLambdaWrapperX<HrmInsuranceMonthEmployeeRecordDO>()
                .selectAs(HrmInsuranceMonthEmployeeRecordDO::getSchemeId, "schemeId")
                .selectCount(HrmInsuranceMonthEmployeeRecordDO::getId, "count")
                .in(HrmInsuranceMonthEmployeeRecordDO::getSchemeId, schemeIds)
                .groupBy(HrmInsuranceMonthEmployeeRecordDO::getSchemeId));
        return CollectionUtils.convertMap(result,
                record -> MapUtil.getLong(record, "schemeId"),
                record -> MapUtil.getLong(record, "count"));
    }

    @SuppressWarnings("UnusedReturnValue")
    default int updateBySchemeId(Long schemeId, HrmInsuranceMonthEmployeeRecordDO updateObj) {
        return update(updateObj, new LambdaUpdateWrapper<HrmInsuranceMonthEmployeeRecordDO>()
                .eq(HrmInsuranceMonthEmployeeRecordDO::getSchemeId, schemeId));
    }

    default void deleteByMonthRecordId(Long monthRecordId) {
        delete(HrmInsuranceMonthEmployeeRecordDO::getMonthRecordId, monthRecordId);
    }

}
