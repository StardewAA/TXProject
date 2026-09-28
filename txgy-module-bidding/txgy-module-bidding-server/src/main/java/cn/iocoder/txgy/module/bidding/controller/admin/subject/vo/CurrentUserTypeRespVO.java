package cn.iocoder.txgy.module.bidding.controller.admin.subject.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 当前登录用户的"用户类型"（契约《门户投标个人用户提示与资格门-2026-09-29》3.1 方案 A）。
 *
 * <p>[为什么需要] 个人用户与企业员工被投标入口拒绝的**原因不同**（功能未开放 vs 企业档案未完善），
 * 前端必须能分辨才能给对症提示。此前前端只能靠 {@code accessStore.tenantId === 0} 自行推断 ——
 * 可实现但脆弱（租户编号是基础设施细节，不该成为业务分流的依据）。后端显式下发后即可去掉该推断。
 *
 * <p>[判据与 PERSONAL_TENANT_ID 同源] {@code tenantId == 0 → PERSONAL}，否则 {@code ENTERPRISE}。
 * [为什么同时保留错误码方案] 用户类型决定"提示什么"，错误码决定"为什么被拒"；
 * 二者互补：前端可在点击前按 userType 预判按钮态，点击后再按错误码给精确原因（码优先）。
 */
@Schema(description = "管理后台 - 当前登录用户类型")
@Data
public class CurrentUserTypeRespVO {

    @Schema(description = "用户类型：PERSONAL 个人用户（保留租户 0）/ ENTERPRISE 企业员工", example = "ENTERPRISE")
    private String userType;

    @Schema(description = "当前租户编号（个人用户恒为 0）", example = "1")
    private Long tenantId;

    @Schema(description = "是否为个人用户（= userType=='PERSONAL' 的便捷布尔）", example = "false")
    private Boolean personal;

    @Schema(description = "本租户是否已建立主体档案（个人用户恒为 false）", example = "true")
    private Boolean subjectProfileBuilt;
}
