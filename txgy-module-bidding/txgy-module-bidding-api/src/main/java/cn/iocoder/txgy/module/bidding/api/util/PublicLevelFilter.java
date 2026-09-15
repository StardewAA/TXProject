package cn.iocoder.txgy.module.bidding.api.util;

import cn.iocoder.txgy.module.bidding.api.enums.PublicLevel;
import cn.iocoder.txgy.module.bidding.api.enums.PublicLevelEnum;

import java.lang.reflect.RecordComponent;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 按公开级别裁字段的小工具：接口出参只留调用方该看的字段。
 * 政务公开字段到不了大厅 / ES；没标 @PublicLevel 的字段一律当内部字段，不往外给。
 */
public final class PublicLevelFilter {

    private PublicLevelFilter() {
    }

    /** 取出某个 VO 里指定公开级别的字段，返回"英文字段名 -> 值"。 */
    public static Map<String, Object> toMap(Object vo, PublicLevelEnum level) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (vo == null) {
            return result;
        }
        try {
            for (RecordComponent component : vo.getClass().getRecordComponents()) {
                PublicLevel annotation = component.getAnnotation(PublicLevel.class);
                if (annotation != null && annotation.value() == level) {
                    result.put(component.getName(), component.getAccessor().invoke(vo));
                }
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("公开级别字段裁剪失败", e);
        }
        return result;
    }

    /** 判断 VO 的某个字段是否属于指定公开级别（没标注就当不公开）。 */
    public static boolean isVisible(Object vo, String fieldName, PublicLevelEnum level) {
        if (vo == null) {
            return false;
        }
        for (RecordComponent component : vo.getClass().getRecordComponents()) {
            if (component.getName().equals(fieldName)) {
                PublicLevel annotation = component.getAnnotation(PublicLevel.class);
                return annotation != null && annotation.value() == level;
            }
        }
        return false;
    }
}
