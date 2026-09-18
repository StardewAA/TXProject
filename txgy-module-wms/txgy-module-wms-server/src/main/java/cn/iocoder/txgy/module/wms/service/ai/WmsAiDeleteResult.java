package cn.iocoder.txgy.module.wms.service.ai;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * AI 删除主数据操作的结果封装。
 *
 * <p>用于把底层删除逻辑的多种结果（成功 / 需要确认 / 未找到 / 业务阻断）
 * 统一转成自然语言返回给用户。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WmsAiDeleteResult {

    /** 是否删除成功 */
    private boolean success;

    /** 是否需要用户二次确认（如分类下存在子分类时） */
    private boolean needConfirm;

    /** 目标对象编号（需要确认时使用） */
    private Long targetId;

    /** 目标对象名称 */
    private String targetName;

    /** 需要确认时，涉及的子分类名称列表 */
    private List<String> children;

    /** 返回给用户的自然语言说明 */
    private String message;

    public static WmsAiDeleteResult success(String message) {
        return new WmsAiDeleteResult(true, false, null, null, null, message);
    }

    public static WmsAiDeleteResult confirm(String message, Long targetId, String targetName, List<String> children) {
        return new WmsAiDeleteResult(false, true, targetId, targetName, children, message);
    }

    public static WmsAiDeleteResult fail(String message) {
        return new WmsAiDeleteResult(false, false, null, null, null, message);
    }

    /** 未找到等价于 fail，此处仅为语义清晰保留 */
    public static WmsAiDeleteResult notFound(String message) {
        return fail(message);
    }

}
