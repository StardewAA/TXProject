package cn.iocoder.txgy.module.cms.framework.operatelog;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * CMS 操作日志注解
 *
 * 标注在 Controller 写操作方法上（新增/修改/删除/提交审核），
 * 由 {@link CmsOperateLogAspect} 拦截并异步写入 system_operate_log。
 *
 * @author yuting_feng
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CmsOperateLog {

    /**
     * 操作模块，如：标准规范库 / 材料参数库
     */
    String type();

    /**
     * 操作类型，如：新增 / 修改 / 删除 / 提交审核
     */
    String subType();

}
