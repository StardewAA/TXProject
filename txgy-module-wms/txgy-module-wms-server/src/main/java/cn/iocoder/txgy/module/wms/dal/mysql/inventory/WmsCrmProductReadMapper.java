package cn.iocoder.txgy.module.wms.dal.mysql.inventory;

import cn.iocoder.txgy.module.wms.controller.admin.crm.vo.WmsCrmProductPageReqVO;
import cn.iocoder.txgy.module.wms.controller.admin.crm.vo.WmsCrmProductRespVO;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.*;

import java.util.Collection;
import java.util.List;

/**
 * CRM 只读读取 WMS 商品（含仓库库存）。
 *
 * <p>CRM 产品管理的数据源：一个 SKU 一行，库存数量取自 {@code wms_inventory} 按 SKU 汇总，
 * 销售单价取自 {@code wms_item_sku}。只读取主数据，不新增、修改、冻结、扣减或调整 WMS 库存。</p>
 */
@Mapper
public interface WmsCrmProductReadMapper {

    /**
     * CRM 产品列：库存数量按 SKU 汇总当前租户下所有仓库的可用库存
     */
    String COLUMNS = """
            SELECT i.id item_id, s.id sku_id, i.code material_code, i.name material_name,
                   s.code sku_code, s.name sku_name, i.unit unit_name,
                   s.selling_price selling_price,
                   COALESCE(inv.quantity, 0) quantity
            FROM wms_item_sku s
                     JOIN wms_item i
                          ON i.id = s.item_id AND i.tenant_id = s.tenant_id AND i.deleted = 0
                     LEFT JOIN (SELECT sku_id, SUM(quantity) quantity
                                FROM wms_inventory
                                WHERE deleted = 0 AND tenant_id = #{tenantId}
                                GROUP BY sku_id) inv ON inv.sku_id = s.id
            WHERE s.tenant_id = #{tenantId} AND s.deleted = 0 AND i.status = 0
            """;

    @Select("""
            <script>
            """ + COLUMNS + """
            <if test="q.materialCode != null and q.materialCode != ''">AND i.code=#{q.materialCode}</if>
            <if test="q.materialName != null and q.materialName != ''">AND i.name LIKE CONCAT('%',#{q.materialName},'%')</if>
            <if test="q.skuCode != null and q.skuCode != ''">AND s.code=#{q.skuCode}</if>
            <if test="q.skuId != null">AND s.id=#{q.skuId}</if>
            ORDER BY i.id,s.id
            </script>
            """)
    IPage<WmsCrmProductRespVO> selectProductPage(Page<WmsCrmProductRespVO> page,
                                                 @Param("tenantId") Long tenantId,
                                                 @Param("q") WmsCrmProductPageReqVO request);

    @Select("""
            <script>
            """ + COLUMNS + """
            AND s.id IN
            <foreach collection="skuIds" item="skuId" open="(" separator="," close=")">#{skuId}</foreach>
            ORDER BY i.id,s.id
            </script>
            """)
    List<WmsCrmProductRespVO> selectBySkuIds(@Param("tenantId") Long tenantId,
                                             @Param("skuIds") Collection<Long> skuIds);

    @Select("""
            """ + COLUMNS + """
            AND s.id = #{skuId}
            """)
    WmsCrmProductRespVO selectBySkuId(@Param("tenantId") Long tenantId, @Param("skuId") Long skuId);

}
