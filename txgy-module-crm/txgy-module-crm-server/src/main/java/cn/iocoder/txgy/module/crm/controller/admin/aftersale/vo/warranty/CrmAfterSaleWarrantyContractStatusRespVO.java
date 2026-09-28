package cn.iocoder.txgy.module.crm.controller.admin.aftersale.vo.warranty;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDate;

/**
 * 管理后台 - 合同的质保档案状态 Response VO
 *
 * <p>用于「一个合同仅允许创建一份质保档案」的识别：
 * 查询一批合同的质保档案状态，{@link #warrantyId} 为空即表示该合同尚未创建质保档案，
 * 可以发起创建；否则前端应禁止选择并提示「该合同已创建质保档案，不可重复创建」。
 *
 * @author txgycloud
 */
@Schema(description = "管理后台 - 合同的质保档案状态 Response VO")
@Data
@Accessors(chain = true)
public class CrmAfterSaleWarrantyContractStatusRespVO {

    @Schema(description = "合同编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "6104")
    private Long contractId;

    @Schema(description = "质保档案编号（为空表示该合同尚未创建质保档案）", example = "1")
    private Long warrantyId;

    @Schema(description = "质保编号", example = "WB20260920000001")
    private String warrantyNo;

    @Schema(description = "工程项目名称", example = "西安热力供热管网保温改造")
    private String projectName;

    @Schema(description = "质保状态：1质保中 2即将到期 3已到期终止", example = "1")
    private Integer status;

    @Schema(description = "质保到期日期", example = "2027-09-19")
    private LocalDate warrantyEndDate;

}
