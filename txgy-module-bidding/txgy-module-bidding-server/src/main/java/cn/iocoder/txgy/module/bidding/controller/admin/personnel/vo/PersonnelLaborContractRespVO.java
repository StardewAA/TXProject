package cn.iocoder.txgy.module.bidding.controller.admin.personnel.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;

/**
 * 员工劳动合同出参。
 *
 * <p>规范出处：用户业务口径（劳动合同主体一致性核验）；《劳动合同法》第十条。
 * <p>业务目标：展示劳动关系证据，与社保共同支撑"人证合一"结论。
 */
@Schema(description = "管理后台 - 员工劳动合同出参")
@Data
public class PersonnelLaborContractRespVO {

    @Schema(description = "主键")
    private Long id;
    @Schema(description = "用人单位（本企业主体 id）")
    private Long entId;
    @Schema(description = "员工")
    private Long personId;
    @Schema(description = "劳动合同编号")
    private String contractNo;
    @Schema(description = "合同类型 code")
    private String contractType;
    @Schema(description = "签订日期")
    private LocalDate signDate;
    @Schema(description = "合同起始日")
    private LocalDate startDate;
    @Schema(description = "合同终止日（空=无固定期限）")
    private LocalDate endDate;
    @Schema(description = "合同状态 code")
    private String contractStatus;
    @Schema(description = "合同岗位")
    private String positionName;
    @Schema(description = "合同扫描件文件 id")
    private Long fileId;
    @Schema(description = "备注")
    private String remark;
}
