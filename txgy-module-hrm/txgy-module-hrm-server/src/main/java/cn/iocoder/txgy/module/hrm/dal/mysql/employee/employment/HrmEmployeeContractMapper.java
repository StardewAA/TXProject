package cn.iocoder.txgy.module.hrm.dal.mysql.employee.employment;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.map.MapUtil;
import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.MPJLambdaWrapperX;
import cn.iocoder.txgy.module.hrm.dal.dataobject.employee.employment.HrmEmployeeContractDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.employee.info.HrmEmployeeDO;
import cn.iocoder.txgy.module.hrm.enums.employee.employment.HrmEmployeeContractStatusEnum;
import cn.iocoder.txgy.module.hrm.enums.employee.info.HrmEmployeeEntryStatusEnum;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface HrmEmployeeContractMapper extends BaseMapperX<HrmEmployeeContractDO> {

    default List<HrmEmployeeContractDO> selectListByEmployeeId(Long employeeId) {
        return selectList(new LambdaQueryWrapperX<HrmEmployeeContractDO>()
                .eq(HrmEmployeeContractDO::getEmployeeId, employeeId)
                .orderByAsc(HrmEmployeeContractDO::getSort)
                .orderByDesc(HrmEmployeeContractDO::getId));
    }

    /**
     * 按来源 Offer 查询合同草稿
     */
    default HrmEmployeeContractDO selectBySourceOfferId(Long sourceOfferId) {
        return selectOne(new LambdaQueryWrapperX<HrmEmployeeContractDO>()
                .eq(HrmEmployeeContractDO::getSourceOfferId, sourceOfferId)
                .orderByDesc(HrmEmployeeContractDO::getId)
                .last("LIMIT 1"));
    }

    default Long selectCountByEndTimeBetween(LocalDateTime[] endTimes) {
        MPJLambdaWrapperX<HrmEmployeeContractDO> query = new MPJLambdaWrapperX<>();
        query.innerJoin(HrmEmployeeDO.class, HrmEmployeeDO::getId,
                HrmEmployeeContractDO::getEmployeeId);
        query.eq(HrmEmployeeContractDO::getExpireRemind, true)
                .eq(HrmEmployeeContractDO::getStatus,
                        HrmEmployeeContractStatusEnum.IN_PROGRESS.getStatus())
                .in(HrmEmployeeDO::getEntryStatus, HrmEmployeeEntryStatusEnum.ACTIVE_STATUSES)
                .betweenIfPresent(HrmEmployeeContractDO::getEndTime, endTimes);
        query.selectFunc("COUNT(DISTINCT %s)",
                arg -> arg.accept(HrmEmployeeContractDO::getEmployeeId), "count");
        List<Map<String, Object>> result = selectMaps(query);
        return MapUtil.getLong(CollUtil.getFirst(result), "count");
    }

    @SuppressWarnings("UnusedReturnValue")
    default int updateTermById(Long id, Integer term) {
        return update(new LambdaUpdateWrapper<HrmEmployeeContractDO>()
                .eq(HrmEmployeeContractDO::getId, id)
                .set(HrmEmployeeContractDO::getTerm, term));
    }

    /**
     * 合同到期预警清单：在职员工、合同执行中、已开启到期提醒、到期时间落在未来 warnDays 天内
     *
     * @param warnDays 提前预警天数
     * @return 即将到期的合同列表（按到期时间升序）
     */
    default List<HrmEmployeeContractDO> selectExpireWarningList(int warnDays) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime deadline = now.plusDays(warnDays);
        MPJLambdaWrapperX<HrmEmployeeContractDO> query = new MPJLambdaWrapperX<>();
        query.innerJoin(HrmEmployeeDO.class, HrmEmployeeDO::getId,
                HrmEmployeeContractDO::getEmployeeId);
        query.selectAll(HrmEmployeeContractDO.class)
                .eq(HrmEmployeeContractDO::getExpireRemind, true)
                .eq(HrmEmployeeContractDO::getStatus,
                        HrmEmployeeContractStatusEnum.IN_PROGRESS.getStatus())
                .in(HrmEmployeeDO::getEntryStatus, HrmEmployeeEntryStatusEnum.ACTIVE_STATUSES)
                .isNotNull(HrmEmployeeContractDO::getEndTime)
                .ge(HrmEmployeeContractDO::getEndTime, now)
                .le(HrmEmployeeContractDO::getEndTime, deadline)
                .orderByAsc(HrmEmployeeContractDO::getEndTime);
        return selectList(query);
    }

}
