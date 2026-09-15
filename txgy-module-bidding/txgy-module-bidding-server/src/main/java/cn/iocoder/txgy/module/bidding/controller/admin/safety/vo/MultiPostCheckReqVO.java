package cn.iocoder.txgy.module.bidding.controller.admin.safety.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 多岗兼任合规校验入参。
 *
 * <p>规范出处：用户业务口径"兼任两个及以上岗位的，必须取得另一岗位的考核合格证书方可上岗"；
 * 《电子资质七大模块技术方案.md》§1.3（POST /bidding/personnel/multi-post-check）。
 * <p>业务目标：上岗前拦住"无证兼任"。
 */
@Schema(description = "管理后台 - 多岗兼任合规校验入参")
@Data
public class MultiPostCheckReqVO {

    @Schema(description = "员工档案主键", requiredMode = Schema.RequiredMode.REQUIRED, example = "100")
    @NotNull(message = "员工不能为空")
    private Long personId;

    @Schema(description = "主岗位 code（KeyPostEnum）", requiredMode = Schema.RequiredMode.REQUIRED, example = "PROJECT_MANAGER")
    @NotBlank(message = "主岗位不能为空")
    private String primaryPost;

    @Schema(description = "兼任岗位 code（KeyPostEnum，须与主岗位不同）", requiredMode = Schema.RequiredMode.REQUIRED,
            example = "SAFETY_OFFICER")
    @NotBlank(message = "兼任岗位不能为空")
    private String secondaryPost;
}
