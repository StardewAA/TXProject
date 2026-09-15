package cn.iocoder.txgy.module.cms.dal.mysql.QuotaDetail;

import cn.iocoder.txgy.module.cms.controller.admin.QuotaDetail.vo.CmsQuotaDetailPageReqVO;
import cn.iocoder.txgy.module.cms.dal.dataobject.QuotaDetail.CmsQuotaDetailDO;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import org.apache.ibatis.annotations.Mapper;

/**
 * 定额明细表 Mapper
 *
 * @author yuting_feng
 */
@Mapper
public interface CmsQuotaDetailMapper extends BaseMapperX<CmsQuotaDetailDO> {

    default PageResult<CmsQuotaDetailDO> selectPage(CmsQuotaDetailPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<CmsQuotaDetailDO>()
                .likeIfPresent(CmsQuotaDetailDO::getItemName, reqVO.getItemName())
                .eqIfPresent(CmsQuotaDetailDO::getQuotaId, reqVO.getQuotaId())
                .orderByDesc(CmsQuotaDetailDO::getDetailId));
    }

}
