package cn.iocoder.txgy.module.training.controller.admin.resource.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - 文档页数识别 Response VO")
@Data
public class DocumentPageCountRespVO {

    @Schema(description = "识别到的文档总页数", requiredMode = Schema.RequiredMode.REQUIRED, example = "12")
    private Integer pageCount;

    @Schema(description = "预估阅读时长（分钟）= 总页数 × 10", requiredMode = Schema.RequiredMode.REQUIRED, example = "120")
    private Integer estMinutes;

    @Schema(description = "每页预估阅读分钟数", requiredMode = Schema.RequiredMode.REQUIRED, example = "10")
    private Integer minutesPerPage;

    @Schema(description = "页数是否精确识别：true 精确（PDF/Word 分页信息）false 按内容估算", example = "true")
    private Boolean precise;

}
