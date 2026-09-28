package cn.iocoder.txgy.module.bidding.controller.admin.opening;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.framework.idempotent.core.annotation.Idempotent;
import cn.iocoder.txgy.module.bidding.controller.admin.opening.vo.OpeningChantReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.opening.vo.OpeningCreateReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.opening.vo.OpeningEnterReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.opening.vo.OpeningMemberRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.opening.vo.OpeningRespVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.opening.BiddingBidOpeningDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.opening.BiddingOpeningMemberDO;
import cn.iocoder.txgy.module.bidding.service.opening.BidOpeningService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - bidding 开标房间（在线开标室：成员列表 + 进房 CA 身份认证）")
@RestController
@RequestMapping("/bidding/opening")
@Validated
public class BidOpeningController {

    @Resource
    private BidOpeningService bidOpeningService;

    @PostMapping("/create")
    @PreAuthorize("@ss.hasPermission('bidding:opening:create')")
    @Operation(summary = "创建开标房间（一条招标项目一条开标记录）")
    @Idempotent
    public CommonResult<Long> create(@Valid @RequestBody OpeningCreateReqVO reqVO) {
        return success(bidOpeningService.createOpening(reqVO).getId());
    }

    @PostMapping("/open")
    @PreAuthorize("@ss.hasPermission('bidding:opening:manage')")
    @Operation(summary = "开放房间（WAITING→DECRYPTING，成员可进房）")
    @Idempotent
    public CommonResult<OpeningRespVO> open(@RequestParam("openingId") Long openingId,
                                            @RequestParam(value = "operatorName", required = false) String operatorName) {
        return success(BeanUtils.toBean(bidOpeningService.openRoom(openingId, operatorName), OpeningRespVO.class));
    }

    @PostMapping("/enter")
    @PreAuthorize("@ss.hasPermission('bidding:opening:enter')")
    @Operation(summary = "成员进房（进房身份认证对接外部 CA SDK；通过后 AUTHED 进房）")
    @Idempotent
    public CommonResult<OpeningMemberRespVO> enter(@Valid @RequestBody OpeningEnterReqVO reqVO) {
        return success(BeanUtils.toBean(bidOpeningService.enterRoom(reqVO), OpeningMemberRespVO.class));
    }

    @PostMapping("/leave")
    @PreAuthorize("@ss.hasPermission('bidding:opening:enter')")
    @Operation(summary = "成员离房")
    @Idempotent
    public CommonResult<Boolean> leave(@RequestParam("openingId") Long openingId, @RequestParam("userId") Long userId) {
        bidOpeningService.leaveRoom(openingId, userId);
        return success(true);
    }

    @PostMapping("/chant")
    @PreAuthorize("@ss.hasPermission('bidding:opening:manage')")
    @Operation(summary = "唱标（宣读全部投标人报价；DECRYPTING→OPENING）")
    @Idempotent
    public CommonResult<OpeningRespVO> chant(@Valid @RequestBody OpeningChantReqVO reqVO) {
        return success(BeanUtils.toBean(bidOpeningService.chant(reqVO), OpeningRespVO.class));
    }

    @PostMapping("/finish")
    @PreAuthorize("@ss.hasPermission('bidding:opening:manage')")
    @Operation(summary = "结束开标（OPENING→DONE，房间关闭）")
    @Idempotent
    public CommonResult<OpeningRespVO> finish(@RequestParam("openingId") Long openingId) {
        return success(BeanUtils.toBean(bidOpeningService.finishOpening(openingId), OpeningRespVO.class));
    }

    @GetMapping("/members")
    @PreAuthorize("@ss.hasPermission('bidding:opening:query')")
    @Operation(summary = "房间成员列表（显示房间里有谁）")
    public CommonResult<List<OpeningMemberRespVO>> members(@RequestParam("openingId") Long openingId) {
        return success(BeanUtils.toBean(bidOpeningService.listMembers(openingId), OpeningMemberRespVO.class));
    }

    @GetMapping("/get")
    @PreAuthorize("@ss.hasPermission('bidding:opening:query')")
    @Operation(summary = "查开标房间（按招标项目编号）")
    public CommonResult<OpeningRespVO> getByProject(@RequestParam("tenderProjectCode") @Parameter(description = "招标项目编号") String tenderProjectCode) {
        return success(BeanUtils.toBean(bidOpeningService.getByTenderProject(tenderProjectCode), OpeningRespVO.class));
    }

    @GetMapping("/page")
    @PreAuthorize("@ss.hasPermission('bidding:opening:query')")
    @Operation(summary = "开标记录分页（按标段过滤；契约《投标与资格预审多文件化-2026-10-02》R5：标段详情页聚合开标记录）")
    public CommonResult<List<OpeningRespVO>> page(@RequestParam(value = "bidSectionCode", required = false)
                                                  @Parameter(description = "标段编号（空=返回全部开标记录）") String bidSectionCode) {
        return success(BeanUtils.toBean(bidOpeningService.listBySection(bidSectionCode), OpeningRespVO.class));
    }
}
