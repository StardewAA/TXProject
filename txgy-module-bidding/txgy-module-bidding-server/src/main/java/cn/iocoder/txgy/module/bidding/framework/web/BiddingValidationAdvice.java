package cn.iocoder.txgy.module.bidding.framework.web;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.module.bidding.framework.web.vo.FieldErrorRespVO;
import cn.iocoder.txgy.module.bidding.framework.web.vo.ValidationErrorRespVO;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.ArrayList;
import java.util.List;

/**
 * 电子证照模块专属的参数校验异常处理器（返回字段级错误 + traceId）。
 *
 * <p>规范出处：契约《电子证照模块-前端接口契约-v1.0.md》§0.4（异常契约：字段级校验错误与追踪 ID）；
 * 《招投标模块开发规范》§4.5（参数校验走 Jakarta Validation）。
 * <p>设计遵循/针对：① **限定包路径** basePackages = cn.iocoder.txgy.module.bidding.controller，
 * 只接管本模块 Controller，平台统一异常处理器对其余模块保持不变（不越界改公共行为）；
 * ② 用 @Order(HIGHEST_PRECEDENCE) 优先于平台处理器命中，保证契约承诺的 data.fieldErrors 真的返回；
 * ③ 仍把首条错误写进 msg，兼容"只读 msg 弹 toast"的老前端，属于向后兼容而非二选一。
 * <p>业务目标：前端能把校验错误精确落到表单字段（而不是一句全局提示），用户一次改对。
 */
@Slf4j
@RestControllerAdvice(basePackages = "cn.iocoder.txgy.module.bidding.controller")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class BiddingValidationAdvice {

    /** 请求体校验失败（@RequestBody + @Valid）。 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public CommonResult<ValidationErrorRespVO> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        return buildFromSpringErrors(ex.getBindingResult().getFieldErrors());
    }

    /** 表单/查询参数绑定校验失败。 */
    @ExceptionHandler(BindException.class)
    public CommonResult<ValidationErrorRespVO> handleBind(BindException ex) {
        return buildFromSpringErrors(ex.getBindingResult().getFieldErrors());
    }

    /** 方法参数级校验失败（@RequestParam/@PathVariable 上的约束注解）。 */
    @ExceptionHandler(ConstraintViolationException.class)
    public CommonResult<ValidationErrorRespVO> handleConstraintViolation(ConstraintViolationException ex) {
        List<FieldErrorRespVO> errors = new ArrayList<>();
        for (ConstraintViolation<?> violation : ex.getConstraintViolations()) {
            String path = String.valueOf(violation.getPropertyPath());
            int idx = path.lastIndexOf('.');
            String field = idx >= 0 ? path.substring(idx + 1) : path;
            errors.add(new FieldErrorRespVO(field, violation.getMessage(), violation.getInvalidValue()));
        }
        return build(errors);
    }

    /**
     * 组装统一信封。
     *
     * <p>为什么 msg 只取第一条：toast 场景下多字段拼一长串反而看不清；完整清单在 data.fieldErrors 中，
     * 前端做字段级展示时读取它即可。
     */
    private CommonResult<ValidationErrorRespVO> build(List<FieldErrorRespVO> errors) {
        String traceId = BiddingTraceContext.get();
        ValidationErrorRespVO data = new ValidationErrorRespVO();
        data.setTraceId(traceId);
        data.setFieldErrors(errors);
        String firstMessage = errors.isEmpty() ? "请求参数不正确" : errors.get(0).getMessage();
        log.warn("[bidding-validation][fail] traceId={} 字段级错误 {} 处，首条：{}",
                traceId, errors.size(), firstMessage);
        // 信封结构固定为 {code,msg,data}：平台 CommonResult 无三参 error 重载，故先构造再挂 data
        CommonResult<ValidationErrorRespVO> result = CommonResult.error(400, "请求参数不正确:" + firstMessage);
        result.setData(data);
        return result;
    }

    /** Spring 的字段错误 → 契约结构（保留被拒原值便于前端调试，但提示语不暴露内部细节）。 */
    private CommonResult<ValidationErrorRespVO> buildFromSpringErrors(java.util.List<FieldError> fieldErrors) {
        List<FieldErrorRespVO> errors = new ArrayList<>();
        for (FieldError error : fieldErrors) {
            errors.add(new FieldErrorRespVO(error.getField(), error.getDefaultMessage(), error.getRejectedValue()));
        }
        return build(errors);
    }
}
