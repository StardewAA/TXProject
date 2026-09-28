package cn.iocoder.txgy.module.cms.service.Quota;

import cn.iocoder.txgy.module.cms.controller.admin.Quota.vo.*;
import cn.iocoder.txgy.module.cms.dal.dataobject.Quota.CmsQuotaDO;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import jakarta.validation.Valid;

/**
 * 资质定额资料库 Service 接口
 *
 * @author yuting_feng
 */
public interface CmsQuotaService {

    Long createQuota(@Valid CmsQuotaSaveReqVO createReqVO);

    void updateQuota(@Valid CmsQuotaSaveReqVO updateReqVO);

    void deleteQuota(Long quotaId);

    CmsQuotaDO getQuota(Long quotaId);

    PageResult<CmsQuotaDO> getQuotaPage(CmsQuotaPageReqVO pageReqVO);

    java.util.List<CmsQuotaDO> getQuotaList();
}
