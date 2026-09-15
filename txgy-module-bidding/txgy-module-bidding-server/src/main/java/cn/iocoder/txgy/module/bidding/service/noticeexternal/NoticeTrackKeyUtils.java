package cn.iocoder.txgy.module.bidding.service.noticeexternal;
// [依据] 前端配合说明 BR-1：外部公告下发“可关注维度键”（project/tender/section），
// 详情/列表 VO 统一按本工具拆分标段键列表；分隔符兼容半角分号/逗号（落库规范口径=半角分号，见 FollowServiceImpl.collectMatchKeys）。

import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 外部公告跟踪维度键工具：bidSectionCodes（"S1;S2"）→ 键列表，供 VO 下发与关注匹配共用。
 */
public final class NoticeTrackKeyUtils {

    private NoticeTrackKeyUtils() {
    }

    /** 拆分标段编号（逗号/分号分隔，trim 后去空；无文本返回空列表）。 */
    public static List<String> splitSectionCodes(String bidSectionCodes) {
        List<String> keys = new ArrayList<>();
        if (!StringUtils.hasText(bidSectionCodes)) {
            return keys;
        }
        for (String section : bidSectionCodes.split("[,;]")) {
            String trimmed = section.trim();
            if (StringUtils.hasText(trimmed)) {
                keys.add(trimmed);
            }
        }
        return keys;
    }

}
