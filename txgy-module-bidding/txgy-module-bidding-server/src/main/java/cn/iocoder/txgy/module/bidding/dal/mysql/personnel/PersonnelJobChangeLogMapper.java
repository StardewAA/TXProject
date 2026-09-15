package cn.iocoder.txgy.module.bidding.dal.mysql.personnel;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.module.bidding.dal.dataobject.personnel.PersonnelJobChangeLogDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDate;

/**
 * 人员受聘企业变更流水 Mapper。
 *
 * <p>规范出处：《电子资质七大模块技术方案.md》§1.1(d)/§1.3（12 个月变更次数统计）。
 * <p>设计遵循/针对：统计"窗口内变更次数"直接在 SQL 层 count（命中 idx_job_change_person），
 * 不把流水拉进内存再过滤——人员基数大时后者会拖垮 Job。
 * <p>业务目标：频繁变更判定一次查询出结果。
 */
@Mapper
public interface PersonnelJobChangeLogMapper extends BaseMapperX<PersonnelJobChangeLogDO> {

    /** 统计某人在窗口内的受聘企业变更次数（仅计 TRANSFER，离职/复职不算换单位）。 */
    default long countChanges(Long personId, LocalDate windowStart) {
        return selectCount(new LambdaQueryWrapper<PersonnelJobChangeLogDO>()
                .eq(PersonnelJobChangeLogDO::getPersonId, personId)
                .eq(PersonnelJobChangeLogDO::getChangeType, "TRANSFER")
                .ge(PersonnelJobChangeLogDO::getEffectiveDate, windowStart));
    }

    /** 取窗口内首条变更日期（用于回填统计窗口起点）。 */
    default LocalDate selectEarliestChangeDate(Long personId, LocalDate windowStart) {
        PersonnelJobChangeLogDO first = selectOne(new LambdaQueryWrapper<PersonnelJobChangeLogDO>()
                .eq(PersonnelJobChangeLogDO::getPersonId, personId)
                .ge(PersonnelJobChangeLogDO::getEffectiveDate, windowStart)
                .orderByAsc(PersonnelJobChangeLogDO::getEffectiveDate)
                .last("LIMIT 1"));
        return first == null ? null : first.getEffectiveDate();
    }
}
