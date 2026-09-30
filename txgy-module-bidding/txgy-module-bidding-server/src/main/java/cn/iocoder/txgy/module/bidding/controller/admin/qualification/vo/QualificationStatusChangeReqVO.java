package cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo;

// [规范依据]《后端变更请求-企业资质状态机入口与删除能力-2026-10-01》§2 B1
// [业务目标] 状态机显式入口：LIMITED（限制）/ REVOKED（吊销）只能经本接口产生（其余态由流程/扫描推导），
//            reason 必填留痕；乐观锁 version 与 PUT /update 同口径，防并发覆盖。

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - 企业资质状态流转 Request（B1：LIMITED/REVOKED 显式入口）")
@Data
public class QualificationStatusChangeReqVO {

    @Schema(description = "资质 id（必填）", requiredMode = Schema.RequiredMode.REQUIRED, example = "123")
    @NotNull(message = "资质 id 不能为空")
    private Long id;

    @Schema(description = "目标态：LIMITED（限制）| REVOKED（吊销）；其余态由流程/扫描推导，不接受直填",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "LIMITED")
    @NotBlank(message = "目标状态不能为空")
    private String targetStatus;

    @Schema(description = "乐观锁版本（必填；与 PUT /update 同口径）", requiredMode = Schema.RequiredMode.REQUIRED, example = "7")
    @NotNull(message = "乐观锁版本不能为空")
    private Integer version;

    @Schema(description = "原因（必填；非流程态必须留原因），长度 ≤ 200",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "串标被行政处罚，责令整改")
    @NotBlank(message = "变更原因不能为空")
    @Size(max = 200, message = "变更原因长度不能超过 200")
    private String reason;
}