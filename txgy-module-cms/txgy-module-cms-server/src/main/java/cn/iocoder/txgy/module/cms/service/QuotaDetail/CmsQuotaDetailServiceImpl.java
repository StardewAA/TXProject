package cn.iocoder.txgy.module.cms.service.QuotaDetail;

import cn.iocoder.txgy.module.cms.controller.admin.QuotaDetail.vo.*;
import cn.iocoder.txgy.module.cms.dal.dataobject.QuotaDetail.CmsQuotaDetailDO;
import cn.iocoder.txgy.module.cms.dal.mysql.QuotaDetail.CmsQuotaDetailMapper;
import cn.iocoder.txgy.module.cms.enums.ErrorCodeConstants;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import jakarta.annotation.Resource;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.cms.enums.ErrorCodeConstants.*;

/**
 * 定额明细表 Service 实现类
 *
 * @author yuting_feng
 */
@Service
@Validated
public class CmsQuotaDetailServiceImpl implements CmsQuotaDetailService {

    @Resource
    private CmsQuotaDetailMapper quotadetailMapper;

    @Override
    public Long createQuotaDetail(CmsQuotaDetailSaveReqVO createReqVO) {
        CmsQuotaDetailDO record = BeanUtils.toBean(createReqVO, CmsQuotaDetailDO.class);
        quotadetailMapper.insert(record);
        return record.getDetailId();
    }

    @Override
    public void updateQuotaDetail(CmsQuotaDetailSaveReqVO updateReqVO) {
        validateQuotaDetailExists(updateReqVO.getDetailId());
        CmsQuotaDetailDO updateObj = BeanUtils.toBean(updateReqVO, CmsQuotaDetailDO.class);
        quotadetailMapper.updateById(updateObj);
    }

    @Override
    public void deleteQuotaDetail(Long detailId) {
        validateQuotaDetailExists(detailId);
        quotadetailMapper.deleteById(detailId);
    }

    private void validateQuotaDetailExists(Long detailId) {
        if (quotadetailMapper.selectById(detailId) == null) {
            throw exception(QUOTA_DETAIL_NOT_EXISTS);
        }
    }

    @Override
    public CmsQuotaDetailDO getQuotaDetail(Long detailId) {
        return quotadetailMapper.selectById(detailId);
    }

    @Override
    public PageResult<CmsQuotaDetailDO> getQuotaDetailPage(CmsQuotaDetailPageReqVO pageReqVO) {
        return quotadetailMapper.selectPage(pageReqVO);
    }

    @Override
    public java.util.List<CmsQuotaDetailDO> getQuotaDetailList() {
        return quotadetailMapper.selectList(new LambdaQueryWrapperX<CmsQuotaDetailDO>()
                .orderByDesc(CmsQuotaDetailDO::getDetailId));
    }
}
