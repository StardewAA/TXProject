package cn.iocoder.txgy.module.ai.service.permission.bo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

/**
 * AI 权限校验结果 BO
 *
 * <p>由后端规则校验层 {@link cn.iocoder.txgy.module.ai.service.permission.AiPermissionGuard} 返回。
 * allowed = false 时，Controller/Service 直接返回 denyMessage，
 * 不调用大模型、不查任何业务数据。
 *
 * @author 天信管业
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiPermissionCheckResultBO {

    /**
     * 是否允许本次提问
     */
    private Boolean allowed;

    /**
     * 拒绝话术（allowed = false 时返回给用户）
     */
    private String denyMessage;

    /**
     * 拒绝原因（内部日志/审计用，不返回给用户，避免泄露权限结构）
     */
    private String denyReason;

    /**
     * 本次提问命中的业务域编码集合
     */
    private Set<String> hitBizTypes;

    /**
     * 越权的业务域名称（用于提示用户具体缺哪项权限）
     */
    private Set<String> deniedBizNames;

    /**
     * 是否命中敏感数据请求
     */
    private Boolean sensitive;

    /**
     * 构造「允许」结果
     *
     * @param hitBizTypes 命中的业务域
     * @return 校验结果
     */
    public static AiPermissionCheckResultBO allow(Set<String> hitBizTypes) {
        return AiPermissionCheckResultBO.builder()
                .allowed(true)
                .hitBizTypes(hitBizTypes)
                .sensitive(false)
                .build();
    }

    /**
     * 构造「拒绝」结果
     *
     * @param denyMessage 拒绝话术
     * @param denyReason  拒绝原因（内部）
     * @return 校验结果
     */
    public static AiPermissionCheckResultBO deny(String denyMessage, String denyReason) {
        return AiPermissionCheckResultBO.builder()
                .allowed(false)
                .denyMessage(denyMessage)
                .denyReason(denyReason)
                .build();
    }
}
