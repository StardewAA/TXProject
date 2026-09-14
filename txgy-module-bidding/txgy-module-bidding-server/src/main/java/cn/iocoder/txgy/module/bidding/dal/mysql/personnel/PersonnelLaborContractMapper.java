package cn.iocoder.txgy.module.bidding.dal.mysql.personnel;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.module.bidding.dal.dataobject.personnel.PersonnelLaborContractDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDate;
import java.util.List;

/**
 * 员工劳动合同 Mapper。
 *
 * <p>规范出处：用户业务口径"核验劳动合同主体是否与申报企业完全一致"；《劳动合同法》第十条（书面劳动合同）。
 * <p>设计遵循/针对：有效合同判定口径为"状态 ACTIVE 且（无终止日 或 终止日 ≥ 基准日）"，
 * 与服务层核验口径保持一致（一处定义、两处复用）。
 * <p>业务目标：取覆盖核验基准日的有效合同，作为劳动关系证据。
 */
@Mapper
public interface PersonnelLaborContractMapper extends BaseMapperX<PersonnelLaborContractDO> {

    /**
     * 覆盖指定日期的有效合同（履行中，且起止区间包含该日；end_date 为空视为无固定期限）。
     * 取数口径写成"区间包含"而非"状态为 ACTIVE"，避免状态未及时维护导致的误判。
     */
    default PersonnelLaborContractDO selectEffectiveByPerson(Long personId, LocalDate asOf) {
        return selectOne(new LambdaQueryWrapper<PersonnelLaborContractDO>()
                .eq(PersonnelLaborContractDO::getPersonId, personId)
                .eq(PersonnelLaborContractDO::getContractStatus, "ACTIVE")
                .le(PersonnelLaborContractDO::getStartDate, asOf)
                .and(w -> w.isNull(PersonnelLaborContractDO::getEndDate)
                        .or().ge(PersonnelLaborContractDO::getEndDate, asOf))
                .orderByDesc(PersonnelLaborContractDO::getStartDate)
                .last("LIMIT 1"));
    }

    /** 某员工合同列表（全景展示）。 */
    default List<PersonnelLaborContractDO> selectByPerson(Long personId) {
        return selectList(new LambdaQueryWrapper<PersonnelLaborContractDO>()
                .eq(PersonnelLaborContractDO::getPersonId, personId)
                .orderByDesc(PersonnelLaborContractDO::getStartDate));
    }
}
