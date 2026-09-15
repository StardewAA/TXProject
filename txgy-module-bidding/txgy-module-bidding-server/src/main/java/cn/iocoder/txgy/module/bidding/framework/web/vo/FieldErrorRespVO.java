package cn.iocoder.txgy.module.bidding.framework.web.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 字段级校验错误（契约 §0.4 的 data.fieldErrors[] 元素）。
 *
 * <p>规范出处：契约《电子证照模块-前端接口契约-v1.0.md》§0.4（异常契约：字段名 + 提示 + 被拒值）。
 * <p>为什么必须给"字段名"而不只是给一句话：前端需要把提示**定位到具体表单项**（高亮 + 表单项下方红字），
 * 只给整句 msg 时用户要在十几个字段里自己找，这是表单类接口最常见的体验缺陷。
 * <p>业务目标：参数校验失败可被前端精确消费，减少用户来回试错。
 */
@Schema(description = "字段级校验错误")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FieldErrorRespVO {

    @Schema(description = "出错字段名（与请求体字段名一致，前端据此高亮表单项）", requiredMode = Schema.RequiredMode.REQUIRED,
            example = "certName")
    private String field;

    @Schema(description = "该字段的用户可读提示（直接显示在表单项下方）", requiredMode = Schema.RequiredMode.REQUIRED,
            example = "证照名称不能为空")
    private String message;

    @Schema(description = "被拒绝的原值（仅用于调试，勿直接回显）")
    private Object rejectedValue;
}
