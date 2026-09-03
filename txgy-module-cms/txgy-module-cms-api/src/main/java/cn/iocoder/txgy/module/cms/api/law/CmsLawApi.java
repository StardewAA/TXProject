package cn.iocoder.txgy.module.cms.api.law;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.cms.dto.law.CmsLawSearchReqDTO;
import cn.iocoder.txgy.module.cms.dto.law.CmsLawSearchRespDTO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 法规查询 Java 契约；本次尚未暴露 HTTP / Feign 接口。
 * 实现必须从认证上下文获取租户，并校验条款的可见范围与版本。
 */
public interface CmsLawApi {

    PageResult<CmsLawSearchRespDTO> search(@Valid CmsLawSearchReqDTO request);

    CmsLawSearchRespDTO getArticle(@NotNull @Positive Long articleId);

}
