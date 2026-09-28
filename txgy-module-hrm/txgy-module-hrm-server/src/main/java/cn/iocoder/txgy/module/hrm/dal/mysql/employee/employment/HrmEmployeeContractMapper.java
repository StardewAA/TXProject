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
     * 更新合同期限与结束日期
     *
     * <p>与 {@code updateById} 不同：这里允许把字段显式更新为 null，
     * 用于「无固定期限 / 以完成一定工作任务为期限」的合同清空此前预置的期限与结束日期。</p>
     *
     * @param id      合同编号
     * @param term    合同期限（年），可为 null
     * @param endTime 合同结束日期，可为 null
     * @return 受影响行数
     */
    default int updateTermAndEndTimeById(Long id, Integer term, LocalDateTime endTime) {
        return update(new LambdaUpdateWrapper<HrmEmployeeContractDO>()
                .eq(HrmEmployeeContractDO::getId, id)
                .set(HrmEmployeeContractDO::getTerm, term)
                .set(HrmEmployeeContractDO::getEndTime, endTime));
    }

    /**
     * 员工离职时，把其「执行中」的合同置为「已终止」（M-02：合同状态随离职终止）
     *
     * @param employeeId 员工编号
     * @return 受影响行数
     */
    default int terminateInProgressByEmployeeId(Long employeeId) {
        return update(new LambdaUpdateWrapper<HrmEmployeeContractDO>()
                .eq(HrmEmployeeContractDO::getEmployeeId, employeeId)
                .eq(HrmEmployeeContractDO::getStatus, HrmEmployeeContractStatusEnum.IN_PROGRESS.getStatus())
                .set(HrmEmployeeContractDO::getStatus, HrmEmployeeContractStatusEnum.TERMINATED.getStatus()));
    }

    /**
     * 合同到期预警清单：在职员工、合同执行中、已开启到期提醒、到期时间落在未来 warnDays 天内
     *
     * <p>到期日按「自然日」比较：合同结束日期存的是当天 00:00，若直接与当前时刻比较，
     * 今天到期（或今天已在预警区间内）的合同会被漏掉，预警清单因此少人；
     * 上界用「today + warnDays」的次日 00:00 开区间，覆盖第 warnDays 天全天。</p>
     *
     * @param warnDays 提前预警天数
     * @return 即将到期的合同列表（按到期时间升序）
     */
    default List<HrmEmployeeContractDO> selectExpireWarningList(int warnDays) {
        LocalDateTime from = java.time.LocalDate.now().atStartOfDay();
        LocalDateTime until = from.plusDays(warnDays + 1L);
        MPJLambdaWrapperX<HrmEmployeeContractDO> query = new MPJLambdaWrapperX<>();
        query.innerJoin(HrmEmployeeDO.class, HrmEmployeeDO::getId,
                HrmEmployeeContractDO::getEmployeeId);
        query.selectAll(HrmEmployeeContractDO.class)
                .eq(HrmEmployeeContractDO::getExpireRemind, true)
                .eq(HrmEmployeeContractDO::getStatus,
                        HrmEmployeeContractStatusEnum.IN_PROGRESS.getStatus())
                .in(HrmEmployeeDO::getEntryStatus, HrmEmployeeEntryStatusEnum.ACTIVE_STATUSES)
                .isNotNull(HrmEmployeeContractDO::getEndTime)
                .ge(HrmEmployeeContractDO::getEndTime, from)
                .lt(HrmEmployeeContractDO::getEndTime, until)
                .orderByAsc(HrmEmployeeContractDO::getEndTime);
        return selectList(query);
    }

    /**
     * 已到期但仍标记「执行中」的合同清单（Q-011 / I-011）
     *
     * <p>到期日按自然日比较：合同结束日期存的是当天 00:00，早于「今天 00:00」即视为已到期。</p>
     *
     * @return 已到期合同列表（按到期时间升序）
     */
    default List<HrmEmployeeContractDO> selectExpiredInProgressList() {
        LocalDateTime todayStart = java.time.LocalDate.now().atStartOfDay();
        return selectList(new LambdaQueryWrapperX<HrmEmployeeContractDO>()
                .eq(HrmEmployeeContractDO::getStatus, HrmEmployeeContractStatusEnum.IN_PROGRESS.getStatus())
                .isNotNull(HrmEmployeeContractDO::getEndTime)
                .lt(HrmEmployeeContractDO::getEndTime, todayStart)
                .orderByAsc(HrmEmployeeContractDO::getEndTime));
    }

    /**
     * 指定区间内到期且状态为「执行中」的合同（I-024：HR 工作台到期日历用）
     *
     * @param endTimes 到期时间区间 [起, 止)
     * @return 合同列表（按到期时间升序）
     */
    default List<HrmEmployeeContractDO> selectInProgressListByEndTimeRange(LocalDateTime[] endTimes) {
        return selectList(new LambdaQueryWrapperX<HrmEmployeeContractDO>()
                .eq(HrmEmployeeContractDO::getStatus, HrmEmployeeContractStatusEnum.IN_PROGRESS.getStatus())
                .isNotNull(HrmEmployeeContractDO::getEndTime)
                .ge(HrmEmployeeContractDO::getEndTime, endTimes[0])
                .lt(HrmEmployeeContractDO::getEndTime, endTimes[1])
                .orderByAsc(HrmEmployeeContractDO::getEndTime));
    }

}
