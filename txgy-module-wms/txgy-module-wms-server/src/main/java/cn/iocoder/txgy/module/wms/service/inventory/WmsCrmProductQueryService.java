package cn.iocoder.txgy.module.wms.service.inventory;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.wms.controller.admin.crm.vo.WmsCrmProductPageReqVO;
import cn.iocoder.txgy.module.wms.controller.admin.crm.vo.WmsCrmProductRespVO;
import cn.iocoder.txgy.module.wms.dal.mysql.inventory.WmsCrmProductReadMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * CRM 产品管理的只读数据源：WMS 仓库库存。
 *
 * <p>一个 SKU 一行，实时读取，不落 CRM 本地表；WMS 删除的商品与规格不会出现在查询结果中。</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WmsCrmProductQueryService {
    private final WmsCrmProductReadMapper mapper;

    public PageResult<WmsCrmProductRespVO> getPage(WmsCrmProductPageReqVO q) {
        if (q.getPageNo() == null || q.getPageNo() < 1 || q.getPageSize() == null
                || q.getPageSize() < 1 || q.getPageSize() > 200) {
            throw new IllegalArgumentException("pageNo>=1, pageSize=1..200");
        }
        var page = mapper.selectProductPage(new Page<>(q.getPageNo(), q.getPageSize()),
                TenantContextHolder.getRequiredTenantId(), q);
        return new PageResult<>(page.getRecords(), page.getTotal());
    }

    /**
     * 按 SKU 编号查询单个产品；WMS 不存在时返回 null
     */
    public WmsCrmProductRespVO getBySkuId(Long skuId) {
        if (skuId == null) {
            return null;
        }
        return mapper.selectBySkuId(TenantContextHolder.getRequiredTenantId(), skuId);
    }

    /**
     * 按 SKU 编号批量查询；WMS 已删除的规格不会返回，调用方据此判断产品是否仍然有效
     */
    public List<WmsCrmProductRespVO> getBySkuIds(Collection<Long> skuIds) {
        if (skuIds == null || skuIds.isEmpty()) {
            return Collections.emptyList();
        }
        return mapper.selectBySkuIds(TenantContextHolder.getRequiredTenantId(), skuIds);
    }

}
