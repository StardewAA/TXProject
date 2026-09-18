package cn.iocoder.txgy.module.crm.controller.admin.search;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.module.crm.controller.admin.search.vo.CrmGlobalSearchRespVO;
import cn.iocoder.txgy.module.crm.service.search.CrmGlobalSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

/**
 * 管理后台 - CRM 全局检索
 *
 * <p>顶部全局搜索：一次输入公司名 / 手机号 / 电话，跨模块返回线索、客户、联系人、商机、合同数据
 *
 * @author txgycloud
 */
@Tag(name = "管理后台 - CRM 全局检索")
@RestController
@RequestMapping("/crm/global-search")
@Validated
public class CrmGlobalSearchController {

    @Resource
    private CrmGlobalSearchService globalSearchService;

    @GetMapping("/all")
    @Operation(summary = "全局检索", description = "按公司名 / 手机号 / 电话 / 编号跨模块检索")
    @Parameter(name = "keyword", description = "关键字", required = true, example = "长庆")
    @Parameter(name = "limit", description = "每个模块返回数量，默认 5", example = "5")
    @PreAuthorize("@ss.hasPermission('crm:customer:query')")
    public CommonResult<CrmGlobalSearchRespVO> searchAll(@RequestParam("keyword") String keyword,
                                                         @RequestParam(value = "limit", required = false) Integer limit) {
        return success(globalSearchService.searchAll(keyword, limit));
    }

}
