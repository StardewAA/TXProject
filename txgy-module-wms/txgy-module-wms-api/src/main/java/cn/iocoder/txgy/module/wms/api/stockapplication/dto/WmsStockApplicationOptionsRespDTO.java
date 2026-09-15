package cn.iocoder.txgy.module.wms.api.stockapplication.dto;
import lombok.Data;
import java.util.List;
@Data
public class WmsStockApplicationOptionsRespDTO {
    private List<Warehouse> warehouses;
    private List<Sku> skus;
    @Data public static class Warehouse {
        private String code;
        private String name;
        private Integer status;
    }
    @Data public static class Sku {
        private String materialCode;
        private String materialName;
        private String skuCode;
        private String skuName;
        private String unitName;
    }
}
