package cn.iocoder.txgy.module.wms.api.integration;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 商城、MES 与 WMS 共用的计量单位接口契约；只归一同义名称，不做数量换算。 */
public final class WmsUnitCodeDictionary {

    public record Unit(String code, String name, List<String> aliases) {}

    private static final List<Unit> UNITS = List.of(
            unit("PC", "件", "件", "个", "只", "支", "枚"),
            unit("SET", "套", "套"), unit("GRP", "组", "组"),
            unit("BOX", "箱", "箱"), unit("CASE", "盒", "盒"),
            unit("BAG", "袋", "袋", "包"), unit("BUCKET", "桶", "桶"),
            unit("ROLL", "卷", "卷"), unit("SHEET", "张", "张", "片"),
            unit("BLOCK", "块", "块"), unit("BUNDLE", "捆", "捆", "扎"),
            unit("BOTTLE", "瓶", "瓶"), unit("CAN", "罐", "罐"),
            unit("TUBE", "管", "管"), unit("PALLET", "托盘", "托盘", "托"),
            unit("UNIT", "台", "台"),
            unit("M", "米", "m", "米", "公尺"),
            unit("M2", "平方米", "m²", "m2", "㎡", "平方米", "平米"),
            unit("M3", "立方米", "m³", "m3", "立方米", "方"),
            unit("CM", "厘米", "cm", "厘米", "公分"),
            unit("MM", "毫米", "mm", "毫米"),
            unit("KM", "千米", "km", "千米", "公里"),
            unit("KG", "千克", "kg", "千克", "公斤"),
            unit("G", "克", "g", "克"), unit("MG", "毫克", "mg", "毫克"),
            unit("TNE", "吨", "t", "吨", "公吨"),
            unit("L", "升", "l", "L", "升", "公升"),
            unit("ML", "毫升", "ml", "mL", "毫升"),
            unit("PAIR", "双", "双", "对"), unit("DOZ", "打", "打"),
            unit("KWH", "千瓦时", "kwh", "kW·h", "千瓦时", "度")
    );
    private static final Map<String, Unit> BY_ALIAS = buildAliasIndex();

    private WmsUnitCodeDictionary() {}

    public static Unit resolve(String value) {
        return value == null ? null : BY_ALIAS.get(normalize(value));
    }

    public static boolean isSupportedCode(String code) {
        Unit unit = resolve(code);
        return unit != null && unit.code().equals(code);
    }

    public static List<Unit> all() {
        return UNITS;
    }

    private static Unit unit(String code, String name, String... aliases) {
        return new Unit(code, name, List.of(aliases));
    }

    private static Map<String, Unit> buildAliasIndex() {
        Map<String, Unit> result = new LinkedHashMap<>();
        for (Unit unit : UNITS) {
            putUnique(result, unit.code(), unit);
            putUnique(result, unit.name(), unit);
            unit.aliases().forEach(alias -> putUnique(result, alias, unit));
        }
        return Map.copyOf(result);
    }

    private static void putUnique(Map<String, Unit> result, String alias, Unit unit) {
        Unit previous = result.putIfAbsent(normalize(alias), unit);
        if (previous != null && !previous.code().equals(unit.code())) {
            throw new IllegalStateException("计量单位别名重复：" + alias);
        }
    }

    private static String normalize(String value) {
        return value.strip().replace(" ", "").toLowerCase(Locale.ROOT);
    }
}
