package cn.iocoder.txgy.module.hrm.dal.mysql.insurance.monthrecord;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.hrm.dal.dataobject.insurance.monthrecord.HrmInsuranceMonthRecordDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface HrmInsuranceMonthRecordMapper extends BaseMapperX<HrmInsuranceMonthRecordDO> {

    default HrmInsuranceMonthRecordDO selectByIdForUpdate(Long id) {
        return selectOneForUpdate(HrmInsuranceMonthRecordDO::getId, id);
    }

    default HrmInsuranceMonthRecordDO selectByYearMonth(Integer year, Integer month) {
        // 取最新一条：数据库排序 + LIMIT 1（M-12）
        return selectOne(new LambdaQueryWrapperX<HrmInsuranceMonthRecordDO>()
                .eq(HrmInsuranceMonthRecordDO::getYear, year)
                .eq(HrmInsuranceMonthRecordDO::getMonth, month)
                .orderByDesc(HrmInsuranceMonthRecordDO::getId)
                .last("LIMIT 1"));
    }

    default HrmInsuranceMonthRecordDO selectLast() {
        // 取最近月份一条：数据库排序 + LIMIT 1（M-12）
        return selectOne(new LambdaQueryWrapperX<HrmInsuranceMonthRecordDO>()
                .orderByDesc(HrmInsuranceMonthRecordDO::getYear)
                .orderByDesc(HrmInsuranceMonthRecordDO::getMonth)
                .orderByDesc(HrmInsuranceMonthRecordDO::getId)
                .last("LIMIT 1"));
    }

    default HrmInsuranceMonthRecordDO selectLastForUpdate() {
        return selectFirstOneForUpdate(new LambdaQueryWrapperX<HrmInsuranceMonthRecordDO>()
                .orderByDesc(HrmInsuranceMonthRecordDO::getYear)
                .orderByDesc(HrmInsuranceMonthRecordDO::getMonth)
                .orderByDesc(HrmInsuranceMonthRecordDO::getId));
    }

    default List<HrmInsuranceMonthRecordDO> selectListByYear(Integer year) {
        return selectList(new LambdaQueryWrapperX<HrmInsuranceMonthRecordDO>()
                .eqIfPresent(HrmInsuranceMonthRecordDO::getYear, year)
                .orderByDesc(HrmInsuranceMonthRecordDO::getYear)
                .orderByDesc(HrmInsuranceMonthRecordDO::getMonth));
    }

}
