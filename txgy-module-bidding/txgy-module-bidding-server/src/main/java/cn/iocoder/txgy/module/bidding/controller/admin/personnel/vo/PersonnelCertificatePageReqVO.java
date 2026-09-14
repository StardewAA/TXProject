package cn.iocoder.txgy.module.bidding.controller.admin.personnel.vo;

import cn.iocoder.txgy.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 员工证书分页查询入参。
 *
 * <p>规范出处：《招投标模块开发规范》§4.2/§4.6；《电子证件全流程管理_数据库设计.md》v3.3 §九
 * （到期预警按 valid_to 窗口筛选）。
 * <p>业务目标：按人/类型/状态/到期窗口检索证书，支撑"哪些证快到期""谁有某类证"两类高频问询。
 */
@Schema(description = "管理后台 - 员工证书分页查询入参")
@Data
@EqualsAndHashCode(callSuper = true)
public class PersonnelCertificatePageReqVO extends PageParam {

    @Schema(description = "持证员工", example = "1024")
    private Long personId;

    @Schema(description = "归属企业", example = "1")
    private Long entId;

    @Schema(description = "证书类型 code", example = "REG_PRACTICE_CERT")
    private String certType;

    @Schema(description = "证书编号（精确）", example = "610000000001")
    private String certNo;

    @Schema(description = "证书状态 code：VALID/EXPIRING_SOON/EXPIRED/REVOKED/CANCELLED", example = "VALID")
    private String certStatus;

    @Schema(description = "专业（模糊）", example = "建筑")
    private String major;

    @Schema(description = "到期窗口（天）：仅查 valid_to 在 [今天, 今天+N] 内的证书（预警列表用）", example = "30")
    private Integer expiringDays;
}
