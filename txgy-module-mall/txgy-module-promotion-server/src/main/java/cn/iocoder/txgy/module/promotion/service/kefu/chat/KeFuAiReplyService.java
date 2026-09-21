package cn.iocoder.txgy.module.promotion.service.kefu.chat;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.module.ai.api.customer.AiCustomerServiceApi;
import cn.iocoder.txgy.module.ai.api.customer.dto.AiCustomerServiceReplyReqDTO;
import cn.iocoder.txgy.module.product.api.spu.ProductSpuApi;
import cn.iocoder.txgy.module.product.api.spu.dto.ProductSpuRespDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Fail-closed AI adapter. Provider errors never break the persisted客服 conversation. */
@Service
@RequiredArgsConstructor
@Slf4j
public class KeFuAiReplyService {

    private final AiCustomerServiceApi customerServiceApi;
    private final ProductSpuApi products;

    @Value("${mall.customer-service.ai-enabled:false}")
    private boolean enabled;

    public record Answer(String text, List<Long> productIds) {}

    public Answer reply(String scope, long shopId, String question, String context) {
        if (!enabled || StrUtil.isBlank(question)) {
            return null;
        }
        try {
            List<ProductSpuRespDTO> candidates = products.getRecommendSpuList("SHOP".equals(scope) ? shopId : 0L, 50)
                    .getCheckedData();
            candidates = candidates == null ? List.of() : candidates;
            AiCustomerServiceReplyReqDTO request = new AiCustomerServiceReplyReqDTO();
            request.setScope(scope);
            request.setShopId(shopId);
            request.setQuestion(question.strip());
            request.setContext(promptContext(candidates, context));
            var response = customerServiceApi.reply(request).getCheckedData();
            if (response == null || StrUtil.isBlank(response.getReply())) return null;
            Set<Long> allowed = candidates.stream().map(ProductSpuRespDTO::getId)
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
            List<Long> selected = response.getRecommendedProductIds() == null ? List.of()
                    : response.getRecommendedProductIds().stream().filter(allowed::contains).distinct().limit(3).toList();
            return new Answer(response.getReply().strip(), selected);
        } catch (ServiceException ex) {
            // Only log the normalized service error; prompts, cards, member data and credentials stay out of logs.
            log.warn("[reply][AI客服暂不可用 code({}) message({})]", ex.getCode(), ex.getMessage());
            return null;
        } catch (Exception ex) {
            // Do not log prompts, cards, member data, provider responses or credentials.
            log.warn("[reply][AI客服暂不可用 type({})]", ex.getClass().getSimpleName());
            return null;
        }
    }

    private static String promptContext(List<ProductSpuRespDTO> candidates, String context) {
        StringBuilder result = new StringBuilder("当前租户可售商品候选（推荐只能使用以下编号，价格单位为分）：\n");
        for (ProductSpuRespDTO item : candidates) {
            if (item == null || item.getId() == null || StrUtil.isBlank(item.getName())) continue;
            String name = StrUtil.subWithLength(item.getName().replace('\n', ' ').replace('\r', ' '), 0, 80);
            result.append("ID=").append(item.getId()).append("；名称=").append(name)
                    .append("；价格=").append(item.getPrice() == null ? "以页面为准" : item.getPrice()).append('\n');
            if (result.length() >= 2800) break;
        }
        result.append("其他客服上下文：\n").append(StrUtil.nullToEmpty(context));
        return StrUtil.subWithLength(result.toString(), 0, 4000);
    }

    boolean isEnabled() {
        return enabled;
    }
}
