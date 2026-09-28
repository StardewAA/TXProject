package cn.iocoder.txgy.module.bidding.controller.admin.ca.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * **官方 CA 互认签章回调响应 VO**——严格照官方格式返回。
 *
 * <p>依据：官方《签章验签工具接入指南》§4.2.2「响应参数」+ 响应示例：
 * <pre>
 * { "code": 200, "success": true, "data": null, "msg": "成功接收签章组件回调", "errorCode": null }
 * </pre>
 *
 * <p>为什么不能复用本模块的 {@code CommonResult}：官方回调方按**自己的结构**解析响应
 * （顶层 code/success/data/msg/errorCode），返回 CommonResult 结构会导致对方判为失败并重试/报错。
 */
@Schema(description = "官方 CA 互认签章回调响应（字段对齐官方 §4.2.2）")
@Data
public class CaShareCallbackRespVO {

    @Schema(description = "响应码", example = "200")
    private Integer code;

    @Schema(description = "是否成功", example = "true")
    private Boolean success;

    @Schema(description = "响应数据（官方示例为 null）")
    private Object data;

    @Schema(description = "响应消息", example = "成功接收签章组件回调")
    private String msg;

    @Schema(description = "错误码（联系产品获取）；成功时为 null")
    private String errorCode;

    /** 成功响应（官方示例口径）。 */
    public static CaShareCallbackRespVO ok() {
        CaShareCallbackRespVO resp = new CaShareCallbackRespVO();
        resp.setCode(200);
        resp.setSuccess(true);
        resp.setData(null);
        resp.setMsg("成功接收签章组件回调");
        resp.setErrorCode(null);
        return resp;
    }

    /** 失败响应（HTTP 仍 200，由 success=false + errorCode 表达，符合官方结构）。 */
    public static CaShareCallbackRespVO fail(String msg) {
        CaShareCallbackRespVO resp = new CaShareCallbackRespVO();
        resp.setCode(500);
        resp.setSuccess(false);
        resp.setData(null);
        resp.setMsg(msg);
        resp.setErrorCode(null);
        return resp;
    }
}
