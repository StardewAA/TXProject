package cn.iocoder.txgy.module.bidding.service.notice;

import cn.iocoder.txgy.module.bidding.api.enums.PublicLevel;
import cn.iocoder.txgy.module.bidding.api.enums.PublicLevelEnum;
import cn.iocoder.txgy.module.bidding.enums.NoticeTypeEnum;

import java.lang.reflect.RecordComponent;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 公告载荷（payload）规整工具：保证"权威字段一个不能少"。
 *
 * <p>入参只允许出现对应数据集 VO（record）的组件字段，字段名照录规范英文名称（含拼写瑕疵）；
 * 输出为按数据集声明顺序排列的规范字段全集（缺失字段补 null，未知字段剔除），
 * 用于新建/修改前的校验与入库快照、详情返回的结构化形态。
 */
public final class NoticePayloadUtils {

    private NoticePayloadUtils() {
    }

    /**
     * 把业务侧提交的原始字段集规整为该公告类型对应数据集的规范字段全集。
     *
     * @param type 公告类型（决定权威数据集，见 {@link NoticeTypeEnum}）
     * @param raw  业务侧原始字段（key 用规范英文名称；value 统一转字符串，null 保留）
     * @return 按数据集声明顺序的规范字段全集（LinkedHashMap）
     * @throws IllegalArgumentException type 为空或非已登记类型时抛出
     */
    public static LinkedHashMap<String, Object> canonicalize(NoticeTypeEnum type, Map<String, Object> raw) {
        if (type == null) {
            throw new IllegalArgumentException("公告类型不能为空或未登记（NoticeTypeEnum）");
        }
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        for (RecordComponent component : type.getDatasetClass().getRecordComponents()) {
            String name = component.getName();
            Object value = raw == null ? null : raw.get(name);
            if (value == null) {
                result.put(name, null);
            } else if (value instanceof String string) {
                result.put(name, string);
            } else {
                result.put(name, String.valueOf(value));
            }
        }
        return result;
    }

    /**
     * 社会公开子集（权威规则：出参按调用方公开级别裁剪，见 05 接口设计文档规则 1）。
     *
     * <p>只保留数据集 VO 中标为 {@code @PublicLevel(SOCIAL)} 的组件；未标或政务公开一律不对外。
     *
     * @param type      公告类型（决定权威数据集）
     * @param canonical 规整后的字段全集（见 {@link #canonicalize}）
     * @return 社会公开字段子集（顺序照数据集声明）
     */
    public static LinkedHashMap<String, Object> socialSubset(NoticeTypeEnum type,
                                                             LinkedHashMap<String, Object> canonical) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        if (type == null || canonical == null) {
            return result;
        }
        for (RecordComponent component : type.getDatasetClass().getRecordComponents()) {
            PublicLevel publicLevel = component.getAnnotation(PublicLevel.class);
            if (publicLevel != null && publicLevel.value() == PublicLevelEnum.SOCIAL
                    && canonical.containsKey(component.getName())) {
                result.put(component.getName(), canonical.get(component.getName()));
            }
        }
        return result;
    }
}
