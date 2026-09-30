package cn.iocoder.txgy.module.bidding.controller.admin.prequal.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Schema(description = "管理后台 - 资格审查委员会保存 Request")
@Data
public class PrequalCommitteeSaveReqVO {

    @Schema(description = "资格预审ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "资格预审ID不能为空")
    private Long prequalId;

    @Schema(description = "委员会成员（整单覆盖保存）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "委员会成员不能为空")
    @Valid
    private List<MemberItem> members;

    @Schema(description = "委员会成员项")
    @Data
    public static class MemberItem {

        @Schema(description = "成员姓名", requiredMode = Schema.RequiredMode.REQUIRED)
        @jakarta.validation.constraints.NotBlank(message = "成员姓名不能为空")
        private String memberName;

        @Schema(description = "成员类别（TENDERER_REP/TECH_EXPERT/ECON_EXPERT）", requiredMode = Schema.RequiredMode.REQUIRED, example = "TECH_EXPERT")
        @jakarta.validation.constraints.NotBlank(message = "成员类别不能为空")
        private String memberCategory;

        @Schema(description = "委员会内角色（LEADER/MEMBER）", example = "MEMBER")
        private String memberRole;

        @Schema(description = "所在单位")
        private String memberOrg;

        @Schema(description = "职称/职务")
        private String memberTitle;

        @Schema(description = "排序")
        private Integer sort;

        @Schema(description = "备注")
        private String remark;
    }
}
