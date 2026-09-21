package cn.iocoder.txgy.module.mes.dal.dataobject.pro.workorder;

import cn.iocoder.txgy.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.txgy.module.mes.dal.dataobject.md.client.MesMdClientDO;
import cn.iocoder.txgy.module.mes.dal.dataobject.md.item.MesMdItemDO;
import cn.iocoder.txgy.module.mes.dal.dataobject.md.vendor.MesMdVendorDO;
import cn.iocoder.txgy.module.mes.dal.dataobject.pro.project.MesProProjectDO;
import cn.iocoder.txgy.module.mes.enums.pro.MesProWorkOrderSourceTypeEnum;
import cn.iocoder.txgy.module.mes.enums.pro.MesProWorkOrderStatusEnum;
import cn.iocoder.txgy.module.mes.enums.pro.MesProWorkOrderTypeEnum;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import cn.iocoder.txgy.module.mes.enums.DictTypeConstants;

/**
 * MES 生产工单 DO
 *
 * @author OPENLAB BS
 */
@TableName("mes_pro_work_order")
@KeySequence("mes_pro_work_order_seq") // 用于 Oracle、PostgreSQL、Kingbase、DB2、H2 数据库的主键自增。如果是 MySQL 等数据库，可不写。
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MesProWorkOrderDO extends BaseDO {

    /**
     * 父工单编号，空值
     */
    public static final Long PARENT_ID_NULL = 0L;

    /**
     * 编号
     */
    @TableId
    private Long id;
    /**
     * 工单编码
     */
    private String code;
    /**
     * 工单名称
     */
    private String name;
    /**
     * 工单类型
     *
     * 字典 {@link DictTypeConstants#MES_PRO_WORK_ORDER_TYPE}
     * 枚举 {@link MesProWorkOrderTypeEnum}
     */
    private Integer type;
    /**
     * 来源类型
     *
     * 字典 {@link DictTypeConstants#MES_PRO_WORK_ORDER_SOURCE_TYPE}
     * 枚举 {@link MesProWorkOrderSourceTypeEnum}
     */
    private Integer orderSourceType;
    /**
     * 来源单据编号
     */
    private String orderSourceCode;
    /**
     * 所属项目编号
     *
     * 关联 {@link MesProProjectDO#getId()}，空值表示未挂接项目
     */
    private Long projectId;
    /**
     * 产品编号
     *
     * 关联 {@link MesMdItemDO#getId()}
     */
    private Long productId;
    /**
     * 生产数量
     */
    private BigDecimal quantity;
    /**
     * 已生产数量
     */
    private BigDecimal quantityProduced;
    /**
     * 调整数量
     */
    private BigDecimal quantityChanged;
    /**
     * 已排产数量
     */
    private BigDecimal quantityScheduled;
    /**
     * 客户编号
     *
     * 关联 {@link MesMdClientDO#getId()}
     */
    private Long clientId;
    /**
     * 供应商编号
     *
     * 关联 {@link MesMdVendorDO#getId()}
     */
    private Long vendorId;
    /**
     * 批次号
     */
    private String batchCode;
    /**
     * 需求日期
     */
    private LocalDateTime requestDate;
    /**
     * 父工单编号
     *
     * 关联 {@link MesProWorkOrderDO#getId()}
     */
    private Long parentId;
    /**
     * 完成时间
     */
    private LocalDateTime finishDate;
    /**
     * 取消时间
     */
    private LocalDateTime cancelDate;
    /**
     * 工单状态
     *
     * 字典 {@link DictTypeConstants#MES_PRO_WORK_ORDER_STATUS}
     * 枚举 {@link MesProWorkOrderStatusEnum}
     */
    private Integer status;
    /**
     * 下发人
     */
    private Long issueBy;
    /**
     * 下发时间
     */
    private LocalDateTime issueTime;
    /**
     * WMS 领料出库单号（MES 向 WMS 申请生产领料后回填）
     */
    private String wmsShipmentOrderNo;
    /**
     * WMS 领料状态：0 未申请；1 申请中；2 已出库；3 出库失败；4 已取消
     */
    private Integer wmsShipmentStatus;
    /**
     * WMS 领料申请版本：每次向 WMS 申请领料递增（含缺料恢复后的重新申请）
     *
     * 用作集成报文 requestId / eventVersion 与 WMS 出库单号的去重依据，
     * 避免"同一工单多次领料"因 requestId、出库单号固定而被 WMS 幂等校验或唯一键拒绝。
     */
    private Integer wmsShipmentVersion;
    /**
     * WMS 完工入库单号（MES 完工后向 WMS 申请成品入库回填）
     */
    private String wmsReceiptOrderNo;
    /**
     * WMS 入库状态：0 未申请；1 申请中；2 已入库；3 入库失败；4 已取消
     */
    private Integer wmsReceiptStatus;

    /**
     * BPM 审批流程实例编号（提交审批后回填；审批通过是工单可下发的前置条件）
     */
    private String processInstanceId;

    /**
     * 备注
     */
    private String remark;

}
