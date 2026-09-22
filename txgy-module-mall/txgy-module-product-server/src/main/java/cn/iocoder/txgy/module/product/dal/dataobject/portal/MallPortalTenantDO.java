package cn.iocoder.txgy.module.product.dal.dataobject.portal;

import cn.iocoder.txgy.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * 商城平台公开租户目录。
 *
 * <p>{@code tenantId} 是被展示的经营租户，不是当前登录租户的数据分区字段。
 * 该表只保存门户公开元数据，不承载企业、店铺、商品或交易数据。</p>
 */
@TableName("mall_portal_tenant")
@KeySequence("mall_portal_tenant_seq")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class MallPortalTenantDO extends BaseDO {

    @TableId
    private Long id;
    private Long tenantId;
    private String tenantSlug;
    private String tenantName;
    private String tenantLogo;
    private String mainCategories;
    /** 0 开放，1 关闭。 */
    private Integer openStatus;
    private Integer sort;
}
