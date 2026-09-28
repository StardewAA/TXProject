package cn.iocoder.txgy.module.bidding.controller.admin.personnel.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * 员工证书批量续期入参（五页改造契约 Q11，2026-09-28）。
 *
 * <p>规范出处：Q11 契约——items[{id, version, newValidTo, renewalType}] 或 ids[]+统一新有效期；
 * 返回逐条结果，单个失败不影响其余。
 * <p>2026-09-28 对账补齐：前端已发布类型（personnel/index.ts 的 {@code BatchRenewReq}）两种模式都发，
 * 故本 VO 同时承载：① {@code items[]}（逐条，各自新有效期；带 version 做 CAS）；
 * ② {@code certIds[] + newValidTo}（统一批量，前端只勾选不动日期时用）。
 * <p>为什么"二选一"校验放在服务层而不是注解：注解表达不了"或"关系（items 为空但传了 certIds 是合法的），
 * 服务层统一判非法并抛 {@code PERSONNEL_CERT_RENEW_INVALID}，前端能拿到明确原因而不是 400 参数校验失败。
 */
@Schema(description = "管理后台 - 员工证书批量续期入参")
@Data
public class PersonnelCertBatchRenewReqVO {

    @Schema(description = "续期条目（逐条执行，失败不影响其余；与 certIds 二选一）")
    @Valid
    private List<PersonnelCertRenewItemReqVO> items;

    @Schema(description = "统一批量：证书主键列表（与 items 二选一；须同时给 newValidTo）")
    private List<Long> certIds;

    @Schema(description = "统一批量：新的有效期止（与 certIds 联用）", example = "2029-12-31")
    private LocalDate newValidTo;

    @Schema(description = "统一批量：续期类型 code（可选）", example = "RENEWAL")
    private String renewalType;

    @Schema(description = "统一批量：备注（可选；当前实现把类型写入证书备注留痕）")
    private String remark;
}
