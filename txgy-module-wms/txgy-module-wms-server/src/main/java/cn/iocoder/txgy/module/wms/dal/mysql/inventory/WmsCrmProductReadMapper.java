package cn.iocoder.txgy.module.wms.dal.mysql.inventory;

import cn.iocoder.txgy.module.wms.controller.admin.crm.vo.WmsCrmProductPageReqVO;
import cn.iocoder.txgy.module.wms.controller.admin.crm.vo.WmsCrmProductRespVO;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.*;

/** Only selects master data; both joined tables are constrained to the current tenant. */
@Mapper
public interface WmsCrmProductReadMapper {
    @Select("""
        <script>
        SELECT i.id item_id, s.id sku_id, i.code material_code, i.name material_name,
               s.code sku_code, s.name sku_name, i.unit unit_name
        FROM wms_item_sku s
        JOIN wms_item i ON i.id=s.item_id AND i.tenant_id=s.tenant_id AND i.deleted=0
        WHERE s.tenant_id=#{tenantId} AND s.deleted=0 AND i.status=0
        <if test="q.materialCode != null and q.materialCode != ''">AND i.code=#{q.materialCode}</if>
        <if test="q.materialName != null and q.materialName != ''">AND i.name LIKE CONCAT('%',#{q.materialName},'%')</if>
        <if test="q.skuCode != null and q.skuCode != ''">AND s.code=#{q.skuCode}</if>
        ORDER BY i.id,s.id
        </script>
        """)
    IPage<WmsCrmProductRespVO> selectProductPage(Page<WmsCrmProductRespVO> page,
            @Param("tenantId") Long tenantId, @Param("q") WmsCrmProductPageReqVO request);
}
