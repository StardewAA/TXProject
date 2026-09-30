package cn.iocoder.txgy.module.bpm.util;

import cn.hutool.core.util.StrUtil;

import java.util.Collections;
import java.util.List;

public final class BpmProcessDefinitionKeyUtils {

    private BpmProcessDefinitionKeyUtils() {
    }

    public static List<String> parseKeys(String keys) {
        if (StrUtil.isBlank(keys)) {
            return Collections.emptyList();
        }
        return StrUtil.splitTrim(keys, ',').stream()
                .filter(StrUtil::isNotBlank)
                .distinct()
                .toList();
    }

}
