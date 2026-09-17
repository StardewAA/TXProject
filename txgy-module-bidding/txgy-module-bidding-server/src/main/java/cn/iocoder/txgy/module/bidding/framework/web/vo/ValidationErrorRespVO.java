package cn.iocoder.txgy.module.bidding.framework.web.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 参数校验失败的 data 载体（契约 §0.4 的 data）。
 *
 * <p>规范出处：契约《电子证照模块-前端接口契约-v1.0.md》§0.4（fieldErrors + traceId）。
 * <p>设计遵循/针对：平台统一异常处理器把校验错误拍平成一句 msg（其余模块沿用，不受影响）；
 * 本模块通过"限定包路径 + 最高优先级"的独立 Advice 额外返回结构化 fieldErrors 与 traceId，
 * 使契约中"字段级错误 + 追踪 ID"可落地，且**不改变平台既有行为**。
 * <p>业务目标：前端按字段报错、用户报障带 traceId，一次定位问题。
 */
@Schema(description = "参数校验失败的 data")
@Data
public class ValidationErrorRespVO {

    @Schema(description = "链路追踪 ID（同响应头 X-Trace-Id；用户报障时须提供）", requiredMode = Schema.RequiredMode.REQUIRED,
            example = "8f1c2d3e4a5b6c7d")
    private String traceId;

    @Schema(description = "字段级错误列表", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<FieldErrorRespVO> fieldErrors;
}
