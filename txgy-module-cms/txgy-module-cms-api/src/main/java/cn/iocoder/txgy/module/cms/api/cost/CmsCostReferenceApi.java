package cn.iocoder.txgy.module.cms.api.cost;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.cms.dto.cost.CmsCostSearchReqDTO;
import cn.iocoder.txgy.module.cms.dto.price.CmsPriceDTO;
import cn.iocoder.txgy.module.cms.dto.qualification.CmsQualificationDTO;
import cn.iocoder.txgy.module.cms.dto.quota.CmsQuotaDTO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** 资质、定额、价格分开查询；业务校验与 HTTP/Feign 暴露均待实现。 */
public interface CmsCostReferenceApi {
    PageResult<CmsQualificationDTO> searchQualifications(@NotNull @Valid CmsCostSearchReqDTO request);
    CmsQualificationDTO getQualification(@NotNull @Positive Long id);
    PageResult<CmsQuotaDTO> searchQuotas(@NotNull @Valid CmsCostSearchReqDTO request);
    CmsQuotaDTO getQuota(@NotNull @Positive Long id);
    PageResult<CmsPriceDTO> searchPrices(@NotNull @Valid CmsCostSearchReqDTO request);
    CmsPriceDTO getPrice(@NotNull @Positive Long id);
}
