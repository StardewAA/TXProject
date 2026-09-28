package cn.iocoder.txgy.module.product.api.shop.dto;

import lombok.Data;

import java.util.Collections;
import java.util.Set;

/**
 * 后台商城经营账号的数据范围。
 *
 * <p>{@code unrestricted=true} 仅表示当前租户内不限制企业/店铺，仍然受租户上下文和
 * 控制器权限码约束。空 {@code shopIds} 在受限模式下表示没有任何店铺权限，不能解释为全量。</p>
 */
@Data
public class ProductAdminShopScopeRespDTO {

    private Long tenantId;
    private Long adminUserId;
    private Boolean unrestricted;
    private Set<Long> enterpriseIds = Collections.emptySet();
    private Set<Long> shopIds = Collections.emptySet();

}
