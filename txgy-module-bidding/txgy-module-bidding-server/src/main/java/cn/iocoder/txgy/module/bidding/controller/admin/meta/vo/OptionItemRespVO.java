package cn.iocoder.txgy.module.bidding.controller.admin.meta.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 统一候选选项项（GET /bidding/meta/options 的 data 元素）。
 *
 * <p>契约出处：《24-统一候选接口-options-契约-2026-09-15》§2。
 * <p>为什么字段这样定：
 * <ul>
 *   <li>{@code value} 用字符串：雪花 ID 超过 JS Number 安全整数（2^53），用数字下发会在前端被静默改写，
 *       导致"看到的是 A、提交的是 B"——这是最危险的一类错；统一以十进制字符串下发，前端原样回传。</li>
 *   <li>{@code extra} 放编号类信息（统一社会信用代码/证书编号/工号），让用户能区分同名对象；</li>
 *   <li>{@code disabled} 让前端置灰而不是"从列表里消失"——被禁用项通常正是用户要找的那条（如已过期证照），
 *       直接过滤会让人以为数据丢了；</li>
 *   <li>{@code status} 原样回传业务状态 code，前端可按 {@code /bidding/meta/dicts} 取中文名与标签色。</li>
 * </ul>
 */
@Schema(description = "管理后台 - 统一候选选项项")
@Data
public class OptionItemRespVO {

    @Schema(description = "选项值：雪花 ID 十进制字符串（前端原样回传，勿转 Number）",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private String value;

    @Schema(description = "展示名（前端直接显示）",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "陕西XX建筑工程有限公司")
    private String label;

    @Schema(description = "附加说明：编号/信用代码/发证机关等，用于区分同名对象", example = "91610000MA6TX1234X")
    private String extra;

    @Schema(description = "是否不可选（如离职人员、失效证照）：true 时前端置灰但保留展示", example = "false")
    private Boolean disabled;

    @Schema(description = "业务状态 code（原样回传，配合 /bidding/meta/dicts 取中文名与标签色）", example = "ACTIVE")
    private String status;
}
