package cn.iocoder.txgy.module.bidding.controller.admin.personnel.vo;

import cn.iocoder.txgy.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 员工证书「按人维度」分页查询入参（五页改造缺接口契约 Q9，2026-09-18）。
 *
 * <p>规范出处：Q9 契约——入参 deptName/certType/certStatus/expiringDays/分页；
 * 出参为「人维度」（personId/personName/personNo/deptName/certCount/nearestValidTo/certs[]）。
 * <p>为什么这样写：现有 page 按「证书」分页会把人切断（同一人可能跨页），本接口改为
 * 先筛证书取持证人集合、再按人分页——同一人的证书在同一页内完整（验收 §7.3）。
 */
@Schema(description = "管理后台 - 员工证书按人分页查询入参")
@Data
@EqualsAndHashCode(callSuper = true)
public class PersonnelCertPageByPersonReqVO extends PageParam {

    @Schema(description = "归属企业（可空=全部）", example = "1")
    private Long entId;

    @Schema(description = "部门名称（模糊）", example = "工程部")
    private String deptName;

    @Schema(description = "证书类型 code", example = "REG_PRACTICE_CERT")
    private String certType;

    @Schema(description = "证书状态 code：VALID/EXPIRING_SOON/EXPIRED/REVOKED/CANCELLED", example = "VALID")
    private String certStatus;

    @Schema(description = "到期窗口（天）：仅查 valid_to 在 [今天, 今天+N] 内的证书", example = "30")
    private Integer expiringDays;
}
