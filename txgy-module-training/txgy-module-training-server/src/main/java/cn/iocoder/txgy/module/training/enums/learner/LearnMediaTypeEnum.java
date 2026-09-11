package cn.iocoder.txgy.module.training.enums.learner;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * 学员学习 - 可学习媒资类型
 *
 * <p>学习会话按该类型建模：视频/直播/回放走心跳计时，文档走翻页上报+心跳计时。</p>
 *
 * @author 云培训学院课题小组
 */
@Getter
@AllArgsConstructor
public enum LearnMediaTypeEnum {

    DOCUMENT("document", "文档"),
    VIDEO("video", "视频"),
    LIVE("live", "直播"),
    REPLAY("replay", "直播回放");

    private final String type;
    private final String name;

    public static LearnMediaTypeEnum getByType(String type) {
        if (type == null) {
            return null;
        }
        return Arrays.stream(values()).filter(item -> item.getType().equals(type)).findFirst().orElse(null);
    }

}
