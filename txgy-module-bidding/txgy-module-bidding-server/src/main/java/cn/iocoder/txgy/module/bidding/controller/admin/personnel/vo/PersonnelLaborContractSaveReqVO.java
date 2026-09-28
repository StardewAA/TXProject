package cn.iocoder.txgy.module.bidding.controller.admin.personnel.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

/**
 * 员工劳动合同登记入参。
 *
 * <p>规范出处：用户业务口径"核验劳动合同主体是否与申报企业完全一致"；《劳动合同法》第十条（书面劳动合同）。
 * <p>业务目标：与社保共同构成劳动关系双证据，核验时两者都要过。
 */
@Schema(description = "管理后台 - 员工劳动合同登记入参")
@Data
public class PersonnelLaborContractSaveReqVO {

    @Schema(description = "用人单位（本企业主体 id）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "用人单位不能为空")
    private Long entId;

    @Schema(description = "员工", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "员工不能为空")
    private Long personId;

    @Schema(description = "劳动合同编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "HT2026001")
    @NotBlank(message = "劳动合同编号不能为空")
    private String contractNo;

    @Schema(description = "合同类型 code：FIXED/OPEN/COMPLETE", example = "FIXED")
    private String contractType;

    @Schema(description = "签订日期")
    private LocalDate signDate;

    @Schema(description = "合同起始日", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "合同起始日不能为空")
    private LocalDate startDate;

    @Schema(description = "合同终止日（空=无固定期限）")
    private LocalDate endDate;

    @Schema(description = "合同状态 code：ACTIVE/EXPIRED/TERMINATED", example = "ACTIVE")
    private String contractStatus;

    @Schema(description = "合同岗位", example = "项目经理")
    private String positionName;

    @Schema(description = "合同扫描件文件 id")
    private Long fileId;

    @Schema(description = "备注")
    private String remark;
}
