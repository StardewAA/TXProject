package cn.iocoder.txgy.module.crm.dal.dataobject.aftersale;

import cn.iocoder.txgy.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * CRM 售后回访任务 DO（执行实例）
 *
 * @author txgycloud
 */
@TableName("crm_after_sale_visit_task")
@KeySequence("crm_after_sale_visit_task_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CrmAfterSaleVisitTaskDO extends BaseDO {

    /**
     * 编号，主键自增
     */
    @TableId
    private Long id;
    /**
     * 来源计划编号，0=手动创建任务
     */
    private Long planId;
    /**
     * 周期标识：周期型任务为周期序号（C1、C2...），到期型为 EXPIRE；手动任务为空
     *
     * <p>与计划编号联合约束「同一回访计划同一周期仅能生成一条回访任务」；
     * 为空（手动任务）时不参与该约束。
     */
    private String cycleKey;
    /**
     * 业务编号：按质保档案独立递增（同一档案下首条为 1，后续 2、3…），
     * 编号不回收、不复用（任务完成/逾期/删除后编号仍被占用）；
     * 仅用于页面展示与回访记录绑定任务，数据库唯一性由主键 id 保证
     */
    private Integer bizNo;
    /**
     * 质保档案编号
     */
    private Long warrantyId;
    /**
     * 客户编号（冗余）
     */
    private Long customerId;
    /**
     * 合同编号（冗余）
     */
    private Long contractId;
    /**
     * 处理人（售后人员或业务员）
     */
    private Long assigneeUserId;
    /**
     * 计划执行日期（计划回访日期）
     */
    private LocalDate taskDate;
    /**
     * 窗口期截止日期 = 计划执行日期(taskDate) + 售后设置「回访任务逾期判定天数」
     *
     * <p>⚠️ 本字段仅为创建/编辑时的**留痕快照**：锁定与逾期判定一律按当前售后设置实时计算
     * （见 {@code CrmAfterSaleVisitTaskService#calcWindowEndDate}），
     * 售后设置调整后对存量任务立即生效，不依赖本字段。
     *
     * <p>时间轴三段式判定（today 为当前日期）：
     * <ul>
     *   <li>未到计划回访日期：today &lt; taskDate，硬时间锁，禁止提交回访记录
     *       （售后设置开启「允许提前提交回访记录」时放行）；</li>
     *   <li>窗口期内：taskDate ≤ today ≤ 窗口期截止日期，任务状态正常（待处理），允许正常回访；</li>
     *   <li>已逾期：today &gt; 窗口期截止日期，任务标记「已逾期」，仍允许提交，但需弹窗二次确认。</li>
     * </ul>
     */
    private LocalDate windowEndDate;
    /**
     * 任务状态：1=待处理 2=已完成 3=已逾期
     */
    private Integer status;
    /**
     * 实际完成时间
     */
    private LocalDateTime finishTime;
    /**
     * 临期提醒发送日期（防重复）
     */
    private LocalDate overdueSentDate;
    /**
     * 任务备注/回访提示
     */
    private String remark;

}
