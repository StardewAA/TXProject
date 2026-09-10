package cn.iocoder.txgy.module.crm.controller.admin.aftersale;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.collection.MapUtils;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.crm.controller.admin.aftersale.vo.ticketnote.CrmAfterSaleTicketNotePageReqVO;
import cn.iocoder.txgy.module.crm.controller.admin.aftersale.vo.ticketnote.CrmAfterSaleTicketNoteRespVO;
import cn.iocoder.txgy.module.crm.dal.dataobject.aftersale.CrmAfterSaleTicketNoteDO;
import cn.iocoder.txgy.module.crm.service.aftersale.CrmAfterSaleTicketNoteService;
import cn.iocoder.txgy.module.system.api.user.AdminUserApi;
import cn.iocoder.txgy.module.system.api.user.dto.AdminUserRespDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - CRM 售后工单流转记录")
@RestController
@RequestMapping("/crm/after-sale/ticket-note")
@Validated
public class CrmAfterSaleTicketNoteController {

    @Resource
    private CrmAfterSaleTicketNoteService ticketNoteService;
    @Resource
    private AdminUserApi adminUserApi;

    @GetMapping("/get")
    @Operation(summary = "获得工单流转记录")
    @Parameter(name = "id", description = "编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('crm:after-sale-ticket:query')")
    public CommonResult<CrmAfterSaleTicketNoteRespVO> getTicketNote(@RequestParam("id") Long id) {
        return success(BeanUtils.toBean(ticketNoteService.getTicketNote(id), CrmAfterSaleTicketNoteRespVO.class));
    }

    @GetMapping("/page")
    @Operation(summary = "获得工单流转记录分页")
    @PreAuthorize("@ss.hasPermission('crm:after-sale-ticket:query')")
    public CommonResult<PageResult<CrmAfterSaleTicketNoteRespVO>> getTicketNotePage(
            @Valid CrmAfterSaleTicketNotePageReqVO pageReqVO) {
        PageResult<CrmAfterSaleTicketNoteDO> pageResult = ticketNoteService.getTicketNotePage(pageReqVO);
        PageResult<CrmAfterSaleTicketNoteRespVO> respPage = BeanUtils.toBean(pageResult,
                CrmAfterSaleTicketNoteRespVO.class);
        fillOperatorNickname(respPage.getList());
        return success(respPage);
    }

    @GetMapping("/list")
    @Operation(summary = "获得指定工单的流转记录列表（时间正序）")
    @Parameter(name = "ticketId", description = "工单编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('crm:after-sale-ticket:query')")
    public CommonResult<List<CrmAfterSaleTicketNoteRespVO>> getTicketNoteList(
            @RequestParam("ticketId") Long ticketId) {
        List<CrmAfterSaleTicketNoteDO> list = ticketNoteService.getTicketNoteListByTicketId(ticketId);
        List<CrmAfterSaleTicketNoteRespVO> respList = BeanUtils.toBean(list, CrmAfterSaleTicketNoteRespVO.class);
        fillOperatorNickname(respList);
        return success(respList);
    }

    private void fillOperatorNickname(List<CrmAfterSaleTicketNoteRespVO> list) {
        if (CollUtil.isEmpty(list)) {
            return;
        }
        Map<Long, AdminUserRespDTO> userMap = adminUserApi.getUserMap(
                list.stream().map(CrmAfterSaleTicketNoteRespVO::getOperatorUserId).filter(Objects::nonNull).toList());
        list.forEach(note -> MapUtils.findAndThen(userMap, note.getOperatorUserId(),
                user -> note.setOperatorNickname(user.getNickname())));
    }

}
