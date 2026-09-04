package cn.iocoder.txgy.module.mes.dal.dataobject.pro.project;

import cn.iocoder.txgy.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.txgy.module.mes.enums.DictTypeConstants;
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
     * 备注
     */
    private String remark;

}
