package cn.iocoder.txgy.module.crm.controller.admin.clue.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 线索导入 Response VO
 */
@Schema(description = "管理后台 - 线索导入 Response VO")
@Data
@Builder
public class CrmClueImportRespVO {

    @Schema(description = "创建成功的线索名数组", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<String> createClueNames;

    @Schema(description = "更新成功的线索名数组", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<String> updateClueNames;

    @Schema(description = "导入失败的线索集合，key 为线索名，value 为失败原因", requiredMode = Schema.RequiredMode.REQUIRED)
    private Map<String, String> failureClueNames;

}
