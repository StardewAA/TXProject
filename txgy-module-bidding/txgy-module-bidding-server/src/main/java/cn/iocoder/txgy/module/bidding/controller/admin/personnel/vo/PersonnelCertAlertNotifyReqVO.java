package cn.iocoder.txgy.module.bidding.controller.admin.personnel.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 员工证书批量到期提醒入参（五页改造缺接口契约 Q12，2026-09-28）。
 *
 * <p>规范出处：Q12 契约——personIds[] 或 certIds[]，产出通知（幂等，避免重复推送）。
 * <p>为什么这样写：二选一入参，后端把 personIds 展开为 person 名下全部在册证书；
 * 逐证写通知发件箱（同日同证不重复），幂等口径与 Q15 催办一致。
 */
@Schema(description = "管理后台 - 员工证书批量到期提醒入参")
@Data
public class PersonnelCertAlertNotifyReqVO {

    @Schema(description = "员工 id 列表（与 certIds 二选一；展开为该人名下全部在册证书）")
    private List<Long> personIds;

    @Schema(description = "证书 id 列表（与 personIds 二选一）")
    private List<Long> certIds;

    @Schema(description = "提醒文案（可选；前端已发布字段。传了则作为通知正文，不传由后端按模板生成）",
            example = "您的安全生产考核合格证将于 30 天后到期，请及时续期")
    private String message;
}
