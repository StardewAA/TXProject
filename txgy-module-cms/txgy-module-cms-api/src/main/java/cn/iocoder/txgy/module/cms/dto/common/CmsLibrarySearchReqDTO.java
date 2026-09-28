package cn.iocoder.txgy.module.cms.dto.common;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 资料库公共查询条件，允许无关键词的分页分类浏览。
 * 不开放 tenantId、原始 Redis 查询语句或任意过滤表达式。
 * 普通用户只能查询已发布且有权访问的数据，管理端后续另设接口。
 */
@Data
public class CmsLibrarySearchReqDTO {
    @Size(max = 200)
    private String keyword;
    @Size(max = 64)
    private String industryCode;
    @Size(max = 64)
    private String regionCode;
    @Size(max = 64)
    private String scenarioCode;
    @NotNull
    @Min(1)
    private Integer pageNo = 1;
    @NotNull
    @Min(1)
    @Max(100)
    private Integer pageSize = 10;
}
