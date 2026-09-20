package cn.iocoder.txgy.module.wms.controller.admin.sharedwarehouse.vo;

import cn.iocoder.txgy.module.wms.controller.admin.md.warehouse.vo.WmsWarehousePageReqVO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Schema(description = "管理后台 - 共享仓库分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class WmsSharedWarehousePageReqVO extends WmsWarehousePageReqVO {

    @Schema(description = "来源系统：WMS/MALL/ERP。不传则返回全部实物仓", example = "MALL")
    private String sourceSystem;

}
