package cn.iocoder.txgy.module.wms.controller.admin.crm.vo;

import cn.iocoder.txgy.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "CRM 商品只读查询参数")
public class WmsCrmProductPageReqVO extends PageParam {
    @Schema(description = "商品编码，精确匹配")
    private String materialCode;
    @Schema(description = "商品名称，包含匹配")
    private String materialName;
    @Schema(description = "SKU 编码，精确匹配")
    private String skuCode;
    @Schema(description = "SKU 编号，精确匹配")
    private Long skuId;
}
