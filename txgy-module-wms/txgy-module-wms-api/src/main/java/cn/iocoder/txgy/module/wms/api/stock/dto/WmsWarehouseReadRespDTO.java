package cn.iocoder.txgy.module.wms.api.stock.dto;
import lombok.Data;
@Data
public class WmsWarehouseReadRespDTO {
    private String code;
    private String name;
    private String warehouseType;
    private Integer status;
    private String address;
    private String principal;
    private String remark;
    private Integer sort;
}
