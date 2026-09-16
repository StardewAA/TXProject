package cn.iocoder.txgy.module.training.controller.admin.resource.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Schema(description = "管理后台 - AI 试题导入确认 Request VO")
@Data
public class AiQuestionImportReqVO {

    @Schema(description = "解析出的试题列表", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<AiQuestionItemVO> items;

    @Schema(description = "所属分类 ID（整批试题统一归入该分类，与新增试题的分类一致）")
    private Long categoryId;

    @Schema(description = "标签 ID 集合（整批试题统一打上这些标签，与新增试题的标签一致）")
    private List<Long> tagIds;

}
