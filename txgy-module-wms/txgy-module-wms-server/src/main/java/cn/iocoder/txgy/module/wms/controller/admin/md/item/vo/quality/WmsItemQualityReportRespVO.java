package cn.iocoder.txgy.module.wms.controller.admin.md.item.vo.quality;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Schema(description = "管理后台 - WMS 商品质检报告 Response VO")
public class WmsItemQualityReportRespVO {
    private Long id;
    private Long itemId;
    private Integer status;
    private List<String> imageUrls;
    private String remark;
    private Boolean current;
    private String creator;
    private String creatorName;
    private LocalDateTime createTime;
}
