package cn.iocoder.txgy.module.wms.dal.dataobject.md.item;

import cn.iocoder.txgy.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * WMS 商品品牌 DO
 *
 * @author OPENLAB BS
 */
@TableName("wms_item_brand")
@KeySequence("wms_item_brand_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WmsItemBrandDO extends BaseDO {

    /**
     * 主键编号
     */
    @TableId
    private Long id;
    /**
     * 品牌编号
     */
    private String code;
    /**
     * 品牌名称
     */
    private String name;
    /**
     * 关联商品分类编号
     *
     * 关联 {@link WmsItemCategoryDO#getId()}
     */
    private Long categoryId;
    /**
     * 主要供应商编号
     *
     * 关联 {@link cn.iocoder.txgy.module.wms.dal.dataobject.md.merchant.WmsMerchantDO#getId()}
     */
    private Long supplierId;
    /**
     * 品牌说明
     */
    private String description;
    /**
     * 资质文件地址，多个以逗号分隔
     */
    private String qualificationFiles;

}
