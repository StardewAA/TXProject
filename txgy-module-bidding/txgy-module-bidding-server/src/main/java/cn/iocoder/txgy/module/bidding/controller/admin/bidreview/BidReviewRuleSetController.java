package cn.iocoder.txgy.module.bidding.controller.admin.bidreview;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.framework.idempotent.core.annotation.Idempotent;
import cn.iocoder.txgy.module.bidding.controller.admin.bidreview.vo.BidReviewRuleItemRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.bidreview.vo.BidReviewRuleSetPageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.bidreview.vo.BidReviewRuleSetRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.bidreview.vo.BidReviewRuleSetSaveReqVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.bidreview.BidReviewRuleSetDO;
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
 * 智慧标书审查 · 管理端规则集接口。
 *
 * <p>方案依据：§2.2（平台规则库 + 项目级规则）、§5.2。
 */
@Tag(name = "管理后台 - 智慧标书审查规则集")
@RestController
@RequestMapping("/bidding/bid-review/rule-set")
@Validated
public class BidReviewRuleSetController {

    @Resource
    private BidReviewRuleService ruleService;

    @GetMapping("/page")
    @Operation(summary = "规则集分页")
    @PreAuthorize("@ss.hasPermission('bidding:bid-review:rule:query')")
    public CommonResult<PageResult<BidReviewRuleSetRespVO>> getPage(@Valid BidReviewRuleSetPageReqVO reqVO) {
        PageResult<BidReviewRuleSetDO> page = ruleService.getRuleSetPage(reqVO);
        PageResult<BidReviewRuleSetRespVO> result = new PageResult<>(
                page.getList().stream().map(this::toResp).toList(), page.getTotal());
        return success(result);
    }

    @GetMapping("/get")
    @Operation(summary = "规则集详情（含规则项明细）")
    @Parameter(name = "id", description = "规则集 id", required = true)
    @PreAuthorize("@ss.hasPermission('bidding:bid-review:rule:query')")
    public CommonResult<BidReviewRuleSetRespVO> get(@RequestParam("id") Long id) {
        BidReviewRuleSetRespVO resp = toResp(ruleService.getRuleSet(id));
        resp.setItems(ruleService.getRuleItems(id).stream()
                .map(item -> BeanUtils.toBean(item, BidReviewRuleItemRespVO.class)).toList());
        return success(resp);
    }

    @PostMapping("/create")
    @Operation(summary = "新建规则集")
    @PreAuthorize("@ss.hasPermission('bidding:bid-review:rule:update')")
    @Idempotent
    public CommonResult<Long> create(@Valid @RequestBody BidReviewRuleSetSaveReqVO reqVO) {
        return success(ruleService.createRuleSet(reqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "修改规则集")
    @PreAuthorize("@ss.hasPermission('bidding:bid-review:rule:update')")
    @Idempotent
    public CommonResult<Boolean> update(@Valid @RequestBody BidReviewRuleSetSaveReqVO reqVO) {
        ruleService.updateRuleSet(reqVO);
        return success(true);
    }

    @PostMapping("/enable")
    @Operation(summary = "启用/停用规则集")
    @Parameter(name = "id", description = "规则集 id", required = true)
    @PreAuthorize("@ss.hasPermission('bidding:bid-review:rule:update')")
    @Idempotent
    public CommonResult<Boolean> enable(@RequestParam("id") Long id, @RequestParam("enable") Boolean enable) {
        ruleService.enableRuleSet(id, enable);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除规则集（被任务引用时禁止删除）")
    @Parameter(name = "id", description = "规则集 id", required = true)
    @PreAuthorize("@ss.hasPermission('bidding:bid-review:rule:update')")
    @Idempotent
    public CommonResult<Boolean> delete(@RequestParam("id") Long id) {
        ruleService.deleteRuleSet(id);
        return success(true);
    }

    private BidReviewRuleSetRespVO toResp(BidReviewRuleSetDO ruleSet) {
        return BeanUtils.toBean(ruleSet, BidReviewRuleSetRespVO.class);
    }
}
