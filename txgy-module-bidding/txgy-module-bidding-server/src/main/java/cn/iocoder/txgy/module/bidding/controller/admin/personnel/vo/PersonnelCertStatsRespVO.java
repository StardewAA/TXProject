package cn.iocoder.txgy.module.bidding.controller.admin.personnel.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 员工证书看板统计出参（五页改造缺接口契约 Q10，2026-09-18）。
 *
 * <p>规范出处：Q10 契约——出参 holderCount/certTotal/expiring/expired/unverified；
 * 入参 entId + 可选 deptName。
 * <p>口径：holderCount=持证员工数（按 person_id 去重）；certTotal=当前有效证书数（is_current=true）；
 * expiring/expired 按 cert_status（EXPIRING_SOON/EXPIRED）；unverified=未验真（platform_verified 为空或 false）；
 * 与 page 同源（is_current=true 过滤），保证看板数字与列表条数一致。
 */
@Schema(description = "管理后台 - 员工证书看板统计出参")
@Data
public class PersonnelCertStatsRespVO {

    @Schema(description = "持证员工数（person_id 去重）", example = "5")
    private Long holderCount;

    @Schema(description = "证书总数（当前有效）", example = "12")
    private Long certTotal;

    @Schema(description = "临期预警（EXPIRING_SOON）", example = "2")
    private Long expiring;

    @Schema(description = "异常过期（EXPIRED）", example = "1")
    private Long expired;

    @Schema(description = "未验真（platform_verified 空或 false）", example = "3")
    private Long unverified;
}
