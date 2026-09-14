package cn.iocoder.txgy.module.bidding.api.enums;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标在全量 VO 字段上，说明这个字段属于哪个公开级别。
 * 出接口的时候靠它裁剪：政务公开的字段不能流向公共服务大厅、ES 这些公众侧。
 */
@Target({ElementType.FIELD, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface PublicLevel {

    PublicLevelEnum value();
}
