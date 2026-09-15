package cn.iocoder.txgy.module.cms.service.QuotaDetail;

import cn.iocoder.txgy.module.cms.controller.admin.QuotaDetail.vo.*;
import cn.iocoder.txgy.module.cms.dal.dataobject.QuotaDetail.CmsQuotaDetailDO;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import jakarta.validation.Valid;

/**
 * 定额明细表 Service 接口
 *
 * @author yuting_feng
 */
public interface CmsQuotaDetailService {

    Long createQuotaDetail(@Valid CmsQuotaDetailSaveReqVO createReqVO);

    void updateQuotaDetail(@Valid CmsQuotaDetailSaveReqVO updateReqVO);

    void deleteQuotaDetail(Long detailId);

    CmsQuotaDetailDO getQuotaDetail(Long detailId);

    PageResult<CmsQuotaDetailDO> getQuotaDetailPage(CmsQuotaDetailPageReqVO pageReqVO);

    java.util.List<CmsQuotaDetailDO> getQuotaDetailList();
}
