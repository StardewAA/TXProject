package cn.iocoder.txgy.module.crm.dal.mysql.aftersale;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.crm.controller.admin.aftersale.vo.visittask.CrmAfterSaleVisitTaskPageReqVO;
import cn.iocoder.txgy.module.crm.dal.dataobject.aftersale.CrmAfterSaleVisitTaskDO;
import cn.iocoder.txgy.module.crm.enums.aftersale.CrmAfterSaleVisitTaskStatusEnum;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

/**
 * 售后回访任务 Mapper
 *
 * @author txgycloud
 */
@Mapper
public interface CrmAfterSaleVisitTaskMapper extends BaseMapperX<CrmAfterSaleVisitTaskDO> {

    default PageResult<CrmAfterSaleVisitTaskDO> selectPage(CrmAfterSaleVisitTaskPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<CrmAfterSaleVisitTaskDO>()
                .eqIfPresent(CrmAfterSaleVisitTaskDO::getPlanId, reqVO.getPlanId())
                .eqIfPresent(CrmAfterSaleVisitTaskDO::getWarrantyId, reqVO.getWarrantyId())
                .eqIfPresent(CrmAfterSaleVisitTaskDO::getCustomerId, reqVO.getCustomerId())
                .eqIfPresent(CrmAfterSaleVisitTaskDO::getAssigneeUserId, reqVO.getAssigneeUserId())
                .eqIfPresent(CrmAfterSaleVisitTaskDO::getStatus, reqVO.getStatus())
                .betweenIfPresent(CrmAfterSaleVisitTaskDO::getTaskDate, reqVO.getTaskDate())
                .orderByAsc(CrmAfterSaleVisitTaskDO::getTaskDate));
    }

    // ========== 待办：任务提醒（回访任务；含临期与已逾期，实时查询，不落库） ==========

    /**
     * 待办「任务提醒」分页查询
     *
     * <p>实时判定条件：
     * <ul>
     *   <li>任务状态 ∈ {待处理, 已逾期}（已完成 / 删除后条件不再命中，刷新即消失）——
     *       既覆盖「快到点还没做」的临期任务，也覆盖「已经逾期仍未完成」的任务，
     *       保证逾期任务不会被漏掉、必须被持续提醒直到完成；</li>
     *   <li>处理人 ∈ 归属范围（当前登录人本人 + 其下属）；</li>
     *   <li>计划回访日期 ≤ 今天 + 售后设置「任务临期提醒提前天数」
     *       （提醒触发点 = 计划回访日期 - 提前提醒天数，到点即进入待办）
     *       —— ⚠️ 该上限仅约束「待处理」任务；已逾期任务刻意不再受此约束
     *       （逾期任务的任务日期必然早于今天，若仍加上限条件会再次被漏掉）。</li>
     * </ul>
     * 按计划回访日期升序，最紧急的（含已逾期）排在最前。
     *
     * <p>⚠️ 用 {@code le} 而不是 {@code between(今天, 今天+天数)}：
     * 「计划回访日期已过、但仍在窗口期内」的待处理任务同样需要提醒（越拖越接近逾期），
     * 用 between 会把它们排除在待办之外，导致这类任务一路拖到窗口期结束被标记逾期。
     *
     * @param reqVO           分页查询
     * @param assigneeUserIds 归属范围（本人 + 下属负责的任务处理人）
     * @param today           当前日期
     * @param remindDays      售后设置「回访任务临期提醒提前天数」
     */
    default PageResult<CrmAfterSaleVisitTaskDO> selectPageByRemind(CrmAfterSaleVisitTaskPageReqVO reqVO,
                                                                  Collection<Long> assigneeUserIds, LocalDate today, Integer remindDays) {
        if (CollUtil.isEmpty(assigneeUserIds)) {
            return PageResult.empty();
        }
        int days = remindDays == null || remindDays < 0 ? 0 : remindDays;
        return selectPage(reqVO, new LambdaQueryWrapperX<CrmAfterSaleVisitTaskDO>()
                // 未完成的都要提醒：待处理（临期 / 已过计划日期）+ 已逾期
                .in(CrmAfterSaleVisitTaskDO::getStatus,
                        CrmAfterSaleVisitTaskStatusEnum.PENDING.getStatus(),
                        CrmAfterSaleVisitTaskStatusEnum.OVERDUE.getStatus())
                .in(CrmAfterSaleVisitTaskDO::getAssigneeUserId, assigneeUserIds)
                // 已逾期：无条件纳入；待处理：计划回访日期 ≤ 今天 + 提前提醒天数
                .and(wrapper -> wrapper
                        .eq(CrmAfterSaleVisitTaskDO::getStatus, CrmAfterSaleVisitTaskStatusEnum.OVERDUE.getStatus())
                        .or(sub -> sub
                                .eq(CrmAfterSaleVisitTaskDO::getStatus, CrmAfterSaleVisitTaskStatusEnum.PENDING.getStatus())
                                .le(CrmAfterSaleVisitTaskDO::getTaskDate, today.plusDays(days))))
                .orderByAsc(CrmAfterSaleVisitTaskDO::getTaskDate));
    }

    /**
     * 待办「任务提醒」数量（与 {@link #selectPageByRemind} 同一口径；含临期与已逾期）
     */
    default Long selectCountByRemind(Collection<Long> assigneeUserIds, LocalDate today, Integer remindDays) {
        if (CollUtil.isEmpty(assigneeUserIds)) {
            return 0L;
        }
        int days = remindDays == null || remindDays < 0 ? 0 : remindDays;
        return selectCount(new LambdaQueryWrapperX<CrmAfterSaleVisitTaskDO>()
                .in(CrmAfterSaleVisitTaskDO::getStatus,
                        CrmAfterSaleVisitTaskStatusEnum.PENDING.getStatus(),
                        CrmAfterSaleVisitTaskStatusEnum.OVERDUE.getStatus())
                .in(CrmAfterSaleVisitTaskDO::getAssigneeUserId, assigneeUserIds)
                .and(wrapper -> wrapper
                        .eq(CrmAfterSaleVisitTaskDO::getStatus, CrmAfterSaleVisitTaskStatusEnum.OVERDUE.getStatus())
                        .or(sub -> sub
                                .eq(CrmAfterSaleVisitTaskDO::getStatus, CrmAfterSaleVisitTaskStatusEnum.PENDING.getStatus())
                                .le(CrmAfterSaleVisitTaskDO::getTaskDate, today.plusDays(days)))));
    }

    default List<CrmAfterSaleVisitTaskDO> selectListByStatusAndDateLe(Integer status, LocalDate date) {
        return selectList(new LambdaQueryWrapperX<CrmAfterSaleVisitTaskDO>()
                .eq(CrmAfterSaleVisitTaskDO::getStatus, status)
                .le(CrmAfterSaleVisitTaskDO::getTaskDate, date));
    }

    /**
     * 查询指定状态、且计划回访日期早于指定日期的任务
     *
     * <p>逾期标记用法：窗口期截止日期 = 计划回访日期 + 逾期判定天数，
     * 故传入「今天 - 逾期判定天数」作为阈值，即 task_date &lt; 阈值 等价于 已超出窗口期；
     * 窗口期按当前售后配置实时计算，不由数据库快照（window_end_date）参与判定。
     */
    default List<CrmAfterSaleVisitTaskDO> selectListByStatusAndDateLt(Integer status, LocalDate date) {
        return selectList(new LambdaQueryWrapperX<CrmAfterSaleVisitTaskDO>()
                .eq(CrmAfterSaleVisitTaskDO::getStatus, status)
                .lt(CrmAfterSaleVisitTaskDO::getTaskDate, date));
    }

    /**
     * 查询指定状态、未发送过临期提醒、且任务日期不晚于指定日期的任务
     */
    default List<CrmAfterSaleVisitTaskDO> selectListByStatusAndRemindNotSent(Integer status, LocalDate date) {
        return selectList(new LambdaQueryWrapperX<CrmAfterSaleVisitTaskDO>()
                .eq(CrmAfterSaleVisitTaskDO::getStatus, status)
                .isNull(CrmAfterSaleVisitTaskDO::getOverdueSentDate)
                .le(CrmAfterSaleVisitTaskDO::getTaskDate, date));
    }

    /**
     * 取消指定质保档案下「计划回访日期晚于指定日期」的未完成任务（质保自动流转为「已到期终止」时调用）
     *
     * <p>质保到期后，到期日之后本不该再有回访任务（生成时已按质保到期日限制），
     * 存量数据可能因「到期日被调整」「数据导入」而产生，这里统一收口为「已取消」：
     * 取消后不再出现在「任务提醒」待办中，同时保留完整痕迹。
     *
     * @param warrantyId 质保档案编号
     * @param afterDate  质保到期日（严格晚于该日期的未完成任务会被取消）
     * @return 更新的条数
     */
    default int cancelUnfinishedAfterDate(Long warrantyId, LocalDate afterDate) {
        CrmAfterSaleVisitTaskDO updateObj = new CrmAfterSaleVisitTaskDO();
        updateObj.setStatus(CrmAfterSaleVisitTaskStatusEnum.CANCELED.getStatus());
        return update(updateObj, new LambdaQueryWrapperX<CrmAfterSaleVisitTaskDO>()
                .eq(CrmAfterSaleVisitTaskDO::getWarrantyId, warrantyId)
                .in(CrmAfterSaleVisitTaskDO::getStatus,
                        CrmAfterSaleVisitTaskStatusEnum.PENDING.getStatus(),
                        CrmAfterSaleVisitTaskStatusEnum.OVERDUE.getStatus())
                .gt(CrmAfterSaleVisitTaskDO::getTaskDate, afterDate));
    }

    default List<CrmAfterSaleVisitTaskDO> selectListByPlanId(Long planId) {
        return selectList(new LambdaQueryWrapperX<CrmAfterSaleVisitTaskDO>()
                .eq(CrmAfterSaleVisitTaskDO::getPlanId, planId));
    }

    default long selectCountByPlanIdAndStatus(Long planId, Integer status) {
        return selectCount(new LambdaQueryWrapperX<CrmAfterSaleVisitTaskDO>()
                .eq(CrmAfterSaleVisitTaskDO::getPlanId, planId)
                .eq(CrmAfterSaleVisitTaskDO::getStatus, status));
    }

    default List<CrmAfterSaleVisitTaskDO> selectListByWarrantyIds(Collection<Long> warrantyIds) {
        return selectList(new LambdaQueryWrapperX<CrmAfterSaleVisitTaskDO>()
                .in(CrmAfterSaleVisitTaskDO::getWarrantyId, warrantyIds));
    }

    /**
     * 统计指定计划、指定周期的任务数量（用于幂等判断）
     */
    default long selectCountByPlanIdAndCycleKey(Long planId, String cycleKey) {
        return selectCount(new LambdaQueryWrapperX<CrmAfterSaleVisitTaskDO>()
                .eq(CrmAfterSaleVisitTaskDO::getPlanId, planId)
                .eq(CrmAfterSaleVisitTaskDO::getCycleKey, cycleKey));
    }

    /**
     * 统计指定计划、指定计划日期的任务数量（历史数据无周期标识时兜底去重）
     */
    default long selectCountByPlanIdAndTaskDate(Long planId, LocalDate taskDate) {
        return selectCount(new LambdaQueryWrapperX<CrmAfterSaleVisitTaskDO>()
                .eq(CrmAfterSaleVisitTaskDO::getPlanId, planId)
                .eq(CrmAfterSaleVisitTaskDO::getTaskDate, taskDate));
    }

    /**
     * 查询指定质保档案下已使用的最大业务编号
     *
     * <p>仅统计「未删除」的任务：编号在该质保档案范围内从 1 开始连续递增，
     * 不同质保档案之间互相独立（编号可重复）；
     * 任务删除后由 {@link #resequenceBizNoByWarrantyId} 重排补齐，不会跳号
     */
    @Select("SELECT COALESCE(MAX(biz_no), 0) FROM crm_after_sale_visit_task "
            + "WHERE warranty_id = #{warrantyId} AND deleted = 0")
    Integer selectMaxBizNoByWarrantyId(@Param("warrantyId") Long warrantyId);

    /**
     * 查询指定质保档案下的任务列表（按业务编号升序），供回访记录表单绑定任务使用
     */
    default List<CrmAfterSaleVisitTaskDO> selectListByWarrantyId(Long warrantyId) {
        return selectList(new LambdaQueryWrapperX<CrmAfterSaleVisitTaskDO>()
                .eq(CrmAfterSaleVisitTaskDO::getWarrantyId, warrantyId)
                .orderByAsc(CrmAfterSaleVisitTaskDO::getBizNo)
                .orderByAsc(CrmAfterSaleVisitTaskDO::getId));
    }

    /**
     * 重排指定质保档案下「未删除」任务的业务编号，使其从 1 开始严格连续递增（不跳号）
     *
     * <p>任务删除后调用：保证同一档案内编号始终为 1、2、3…，删除中间任务时后续编号前移
     */
    @Update("UPDATE crm_after_sale_visit_task t " +
            "JOIN (SELECT id, ROW_NUMBER() OVER (PARTITION BY warranty_id ORDER BY biz_no, id) AS rn " +
            "      FROM crm_after_sale_visit_task " +
            "      WHERE warranty_id = #{warrantyId} AND deleted = 0) x ON x.id = t.id " +
            "SET t.biz_no = x.rn")
    int resequenceBizNoByWarrantyId(@Param("warrantyId") Long warrantyId);

}
