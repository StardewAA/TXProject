package cn.iocoder.txgy.module.mes.dal.dataobject.pro.project;

import cn.iocoder.txgy.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.txgy.module.mes.enums.DictTypeConstants;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * MES 项目里程碑 DO
 *
 * 里程碑是项目的阶段节点，用于拆解项目计划并跟踪阶段完成情况。
 *
 * @author OPENLAB BS
 */
@TableName("mes_pro_project_milestone")
@KeySequence("mes_pro_project_milestone_seq") // 用于 Oracle、PostgreSQL、Kingbase、DB2、H2 数据库的主键自增。如果是 MySQL 等数据库，可不写。
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MesProProjectMilestoneDO extends BaseDO {

    /**
     * 编号
     */
    @TableId
    private Long id;
    /**
     * 项目编号
     */
    private Long projectId;
    /**
     * 里程碑名称
     */
    private String name;
    /**
     * 计划完成日期
     */
    private LocalDateTime planDate;
    /**
     * 实际完成日期
     */
    private LocalDateTime actualDate;
    /**
     * 里程碑状态
     *
     * 0 未开始、1 进行中、2 已完成
     * 字典 {@link DictTypeConstants#MES_PRO_PROJECT_MILESTONE_STATUS}
     */
    private Integer status;
    /**
     * 排序（升序）
     */
    private Integer sort;
    /**
     * 备注
     */
    private String remark;

}
