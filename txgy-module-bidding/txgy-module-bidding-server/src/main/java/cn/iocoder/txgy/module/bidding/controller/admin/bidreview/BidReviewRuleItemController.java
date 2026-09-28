package cn.iocoder.txgy.module.bidding.controller.admin.bidreview;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.framework.idempotent.core.annotation.Idempotent;
import cn.iocoder.txgy.module.bidding.controller.admin.bidreview.vo.BidReviewRuleItemPageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.bidreview.vo.BidReviewRuleItemRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.bidreview.vo.BidReviewRuleItemSaveReqVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.bidreview.BidReviewRuleItemDO;
import cn.iocoder.txgy.module.bidding.service.bidreview.BidReviewRuleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

/**
 * 智慧标书审查 · 管理端规则项接口。
 *
 * <p>方案依据：§5.2。rule_code 是 AI 输出白名单，规则项维护必须由管理端掌控。
 */
@Tag(name = "管理后台 - 智慧标书审查规则项")
@RestController
@RequestMapping("/bidding/bid-review/rule-item")
@Validated
public class BidReviewRuleItemController {

    @Resource
    private BidReviewRuleService ruleService;

    @GetMapping("/page")
    @Operation(summary = "规则项分页")
    @PreAuthorize("@ss.hasPermission('bidding:bid-review:rule:query')")
    public CommonResult<PageResult<BidReviewRuleItemRespVO>> getPage(@Valid BidReviewRuleItemPageReqVO reqVO) {
        PageResult<BidReviewRuleItemDO> page = ruleService.getRuleItemPage(reqVO);
        PageResult<BidReviewRuleItemRespVO> result = new PageResult<>(
                page.getList().stream().map(item -> BeanUtils.toBean(item, BidReviewRuleItemRespVO.class)).toList(),
                page.getTotal());
        return success(result);
    }

    @PostMapping("/create")
    @Operation(summary = "新建规则项")
    @PreAuthorize("@ss.hasPermission('bidding:bid-review:rule:update')")
    @Idempotent
    public CommonResult<Long> create(@Valid @RequestBody BidReviewRuleItemSaveReqVO reqVO) {
        return success(ruleService.createRuleItem(reqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "修改规则项")
    @PreAuthorize("@ss.hasPermission('bidding:bid-review:rule:update')")
    @Idempotent
    public CommonResult<Boolean> update(@Valid @RequestBody BidReviewRuleItemSaveReqVO reqVO) {
        ruleService.updateRuleItem(reqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除规则项")
    @Parameter(name = "id", description = "规则项 id", required = true)
    @PreAuthorize("@ss.hasPermission('bidding:bid-review:rule:update')")
    @Idempotent
    public CommonResult<Boolean> delete(@RequestParam("id") Long id) {
        ruleService.deleteRuleItem(id);
        return success(true);
    }
}
