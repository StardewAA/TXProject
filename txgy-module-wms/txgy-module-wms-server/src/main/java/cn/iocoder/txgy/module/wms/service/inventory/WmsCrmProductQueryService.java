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
}
