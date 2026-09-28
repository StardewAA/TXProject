package cn.iocoder.txgy.module.promotion.controller.app.bargain;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.enums.CommonStatusEnum;
import cn.iocoder.txgy.framework.common.pojo.PageParam;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.product.api.spu.ProductSpuApi;
import cn.iocoder.txgy.module.product.api.spu.dto.ProductSpuRespDTO;
import cn.iocoder.txgy.module.promotion.controller.app.bargain.vo.activity.AppBargainActivityDetailRespVO;
import cn.iocoder.txgy.module.promotion.controller.app.bargain.vo.activity.AppBargainActivityRespVO;
import cn.iocoder.txgy.module.promotion.convert.bargain.BargainActivityConvert;
import cn.iocoder.txgy.module.promotion.dal.dataobject.bargain.BargainActivityDO;
import cn.iocoder.txgy.module.promotion.enums.bargain.BargainRecordStatusEnum;
import cn.iocoder.txgy.module.promotion.service.bargain.BargainActivityService;
import cn.iocoder.txgy.module.promotion.service.bargain.BargainRecordService;
import cn.iocoder.txgy.module.promotion.service.price.PublicProductVisibility;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;
import static cn.iocoder.txgy.framework.common.util.collection.CollectionUtils.convertList;

@Tag(name = "用户 App - 砍价活动")
@RestController
@RequestMapping("/promotion/bargain-activity")
@Validated
public class AppBargainActivityController {

    @Resource
    private BargainActivityService bargainActivityService;
    @Resource
    private BargainRecordService bargainRecordService;

    @Resource
    private ProductSpuApi spuApi;

    @Resource
    private PublicProductVisibility visibility;

    @GetMapping("/list")
    @Operation(summary = "获得砍价活动列表", description = "用于小程序首页")
    @Parameter(name = "count", description = "需要展示的数量", example = "6")
    @PermitAll
    public CommonResult<List<AppBargainActivityRespVO>> getBargainActivityList(
            @RequestParam(name = "count", defaultValue = "6") Integer count) {
        var visible = visibility.first(count,
                bargainActivityService::getBargainActivityPage, BargainActivityDO::getSpuId);
        return success(BargainActivityConvert.INSTANCE.convertAppList(visible.list(), visible.spus()));
    }

    @GetMapping("/page")
    @Operation(summary = "获得砍价活动分页")
    @PermitAll
    public CommonResult<PageResult<AppBargainActivityRespVO>> getBargainActivityPage(PageParam pageReqVO) {
        var visible = visibility.page(pageReqVO, bargainActivityService::getBargainActivityPage, BargainActivityDO::getSpuId);
        PageResult<BargainActivityDO> result = visible.page();
        if (CollUtil.isEmpty(result.getList())) {
            return success(PageResult.empty(result.getTotal()));
        }
        // 拼接数据
        List<ProductSpuRespDTO> spuList = visible.spus();
        return success(BargainActivityConvert.INSTANCE.convertAppPage(result, spuList));
    }

    @GetMapping("/get-detail")
    @Operation(summary = "获得砍价活动详情")
    @Parameter(name = "id", description = "活动编号", example = "1")
    @PermitAll
    public CommonResult<AppBargainActivityDetailRespVO> getBargainActivityDetail(@RequestParam("id") Long id) {
        BargainActivityDO activity = bargainActivityService.getBargainActivity(id);
        if (activity == null || CommonStatusEnum.isDisable(activity.getStatus())
                || !visibility.isSaleable(activity.getSpuId())) {
            return success(null);
        }
        // 拼接数据
        Integer successUserCount = bargainRecordService.getBargainRecordUserCount(id, BargainRecordStatusEnum.SUCCESS.getStatus());
        ProductSpuRespDTO spu = spuApi.getSpu(activity.getSpuId()).getCheckedData();
        if (spu == null || !Boolean.TRUE.equals(spu.getSaleable())) return success(null);
        return success(BargainActivityConvert.INSTANCE.convert(activity, successUserCount, spu));
    }

}
