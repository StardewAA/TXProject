package cn.iocoder.txgy.module.wms.controller.admin.md.item.vo.quality;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "管理后台 - WMS 商品质检报告创建 Request VO")
public class WmsItemQualityReportSaveReqVO {
    @Schema(description = "商品编号")
    private Long itemId;
    @NotNull(message = "质检状态不能为空")
    private Integer status;
    @Size(max = 9, message = "质检图片最多上传 9 张")
    private List<@Size(min = 1, max = 1024, message = "图片地址长度必须为 1 至 1024 个字符") String> imageUrls;
    @Size(max = 500, message = "异常说明不能超过 500 个字符")
    private String remark;
}
