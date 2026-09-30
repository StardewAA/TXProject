package cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 预警处理请求（把到期预警标记为"已处理"）。
 *
 * <p>规范出处：契约《电子证照模块-前端接口契约-v1.0.md》§3.21（预警清单：**可看、可跳转、可闭环处理**）；
 * cert_alert_records（handled / handled_by / handled_at / handle_note，v3.3 §十五）。
 *
 * <p>为什么请求里必须带 {@code version}：处理动作是"读出来 → 改状态 → 写回去"，
 * 两个经办人同时打开同一条预警时，后提交者若不校验版本就会**覆盖**先提交者的处理备注。
 * 故版本号由前端从清单行带回来，服务端 CAS 校验，冲突报 {@code 1_060_109_xxx CERT_ALERT_RECORD_MODIFIED}
 * ——宁可让用户重看一遍，也不静默丢一条处理意见。
 *
 * <p>为什么没有"处理人"字段：处理人一律取登录态（服务端注入），不由前端传——
 * 前端传"处理人"等于允许代人签字，审计上不可接受。
 *
 * <p>业务目标：让预警清单能真正闭环（处理完能销账），否则清单只增不减，几周后没人再看。
 */
@Schema(description = "管理后台 - 预警处理请求")
@Data
public class QualificationAlertHandleReqVO {

    @Schema(description = "预警记录 id", requiredMode = Schema.RequiredMode.REQUIRED, example = "7001")
    @NotNull(message = "预警记录 id 不能为空")
    private Long alertId;

    @Schema(description = "版本号（从清单行原样带回，用于并发校验）", requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
    @NotNull(message = "版本号不能为空")
    private Integer version;

    @Schema(description = "处理备注（如「已续期，新证有效期至 2027-06-30」）", example = "已续期")
    @Size(max = 512, message = "处理备注不能超过 512 个字符")
    private String handleNote;
}
