package cn.iocoder.txgy.module.mes.enums.pro;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * MES 工单关联的 WMS 领料/入库集成状态
 *
 * @author OPENLAB BS
 */
@Getter
@AllArgsConstructor
public enum MesProWorkOrderWmsStatusEnum {

    NOT_APPLIED(0, "未申请"),
    APPLYING(1, "申请中"),
    DONE(2, "已完成"),
    FAILED(3, "失败"),
    CANCELLED(4, "已取消");

    private final Integer status;
    private final String desc;

}
