package cn.iocoder.txgy.module.mes.dal.dataobject.pro.project;

import cn.iocoder.txgy.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.txgy.module.mes.enums.DictTypeConstants;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * MES 项目 DO
 *
 * 项目是生产工单的归属维度，以项目为源头驱动工单下达与生产。
 *
 * @author OPENLAB BS
 */
@TableName("mes_pro_project")
@KeySequence("mes_pro_project_seq") // 用于 Oracle、PostgreSQL、Kingbase、DB2、H2 数据库的主键自增。如果是 MySQL 等数据库，可不写。
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MesProProjectDO extends BaseDO {

    /**
     * 编号
     */
    @TableId
    private Long id;
    /**
     * 项目编码
     */
    private String code;
    /**
     * 项目名称
     */
    private String name;
    /**
     * 来源单据编号（如销售订单号/报价单号）
     */
    private String orderSourceCode;
    /**
     * 项目来源类型
     *
     * 1 销售订单、2 库存备库、3 其他
     * 字典 {@link DictTypeConstants#MES_PRO_PROJECT_SOURCE_TYPE}
     */
    private Integer sourceType;
    /**
     * 项目状态
     *
     * 字典 {@link DictTypeConstants#MES_PRO_PROJECT_STATUS}
     */
    private Integer status;
    /**
     * 生产进度百分比（0-100）
     *
     * 口径：SUM(工单已生产数量) / SUM(工单计划数量，不含已取消)，与工单统计 SQL 一致。
     * 由报工审批通过 / 工单完工 / 工单取消 / 挂接解绑工单时自动回写（refreshProjectStatus）。
     */
    private BigDecimal progress;
    /**
     * 项目优先级
     *
     * 1 紧急、2 高、3 中、4 低
     * 字典 {@link DictTypeConstants#MES_PRO_PROJECT_PRIORITY}
     */
    private Integer priority;
    /**
     * 负责人
     */
    private String owner;
    /**
     * 负责人用户编号
     *
     * 关联系统用户，保存时校验存在并自动回填负责人姓名，避免随意填写
     */
    private Long ownerUserId;
    /**
     * 责任部门
     */
    private String dept;
    /**
     * 计划开始时间
     */
    private LocalDateTime planStartTime;
    /**
     * 计划结束时间
     */
    private LocalDateTime planEndTime;
    /**
     * 实际开始时间
     */
    private LocalDateTime actualStartTime;
    /**
     * 实际结束时间
     */
    private LocalDateTime actualEndTime;
    /**
     * 工期天数
     *
     * 由计划开始/结束时间自动计算（自然日），无需人工填写
     */
    private Integer durationDays;
    /**
     * 备注
     */
    private String remark;

}
