package cn.iocoder.txgy.framework.excel.core.convert;

import cn.hutool.core.convert.Convert;
import cn.iocoder.txgy.framework.ip.core.Area;
import cn.iocoder.txgy.framework.ip.core.utils.AreaUtils;
import cn.idev.excel.converters.Converter;
import cn.idev.excel.enums.CellDataTypeEnum;
import cn.idev.excel.metadata.GlobalConfiguration;
import cn.idev.excel.metadata.data.ReadCellData;
import cn.idev.excel.metadata.data.WriteCellData;
import cn.idev.excel.metadata.property.ExcelContentProperty;
import lombok.extern.slf4j.Slf4j;

/**
 * Excel 数据地区转换器
 *
 * @author HUIHUI
 */
@Slf4j
public class AreaConvert implements Converter<Object> {

    @Override
    public Class<?> supportJavaTypeKey() {
        throw new UnsupportedOperationException("暂不支持，也不需要");
    }

    @Override
    public CellDataTypeEnum supportExcelTypeKey() {
        throw new UnsupportedOperationException("暂不支持，也不需要");
    }

    @Override
    public Object convertToJavaData(ReadCellData readCellData, ExcelContentProperty contentProperty,
                                    GlobalConfiguration globalConfiguration) {
        // 解析地区编号
        String label = readCellData.getStringValue();
        Area area = AreaUtils.parseArea(label);
        if (area == null) {
            log.error("[convertToJavaData][label({}) 解析不掉]", label);
            return null;
        }
        // 将 value 转换成对应的属性
        Class<?> fieldClazz = contentProperty.getField().getType();
        return Convert.convert(fieldClazz, area.getId());
    }

    /**
     * 写出 Excel 时的转换（QA 修复 2026-09-23）
     *
     * <p>此前未实现该方向：导入模板/导出只要碰到带 {@code @ExcelProperty(converter = AreaConvert.class)}
     * 的列，FastExcel 就会回调本方法，而接口默认实现直接抛
     * {@code UnsupportedOperationException: The current operation is not supported by the current converter}，
     * 表现为「员工导入模板下载」返回 500 系统异常。</p>
     *
     * <p>处理口径：空值输出空字符串；能按地区编号解析出地区时输出可读名称（便于用户对照填写），
     * 解析不出时退回原始值，绝不因单个单元格导致整个模板/导出失败。</p>
     */
    @Override
    public WriteCellData<String> convertToExcelData(Object object, ExcelContentProperty contentProperty,
                                                    GlobalConfiguration globalConfiguration) {
        if (object == null) {
            return new WriteCellData<>("");
        }
        try {
            Area area = AreaUtils.getArea(Convert.toInt(object));
            if (area != null && area.getName() != null) {
                return new WriteCellData<>(area.getName());
            }
        } catch (Exception ex) {
            log.warn("[convertToExcelData][value({}) 解析地区失败，回退原始值]", object, ex);
        }
        return new WriteCellData<>(String.valueOf(object));
    }

}
