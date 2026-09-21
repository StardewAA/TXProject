package cn.iocoder.txgy.module.crm.controller.admin.aftersale;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.collection.MapUtils;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.crm.controller.admin.aftersale.vo.ticket.CrmAfterSaleTicketPageReqVO;
import cn.iocoder.txgy.module.crm.controller.admin.aftersale.vo.ticket.CrmAfterSaleTicketRespVO;
import cn.iocoder.txgy.module.crm.controller.admin.aftersale.vo.ticket.CrmAfterSaleTicketSaveReqVO;
import cn.iocoder.txgy.module.crm.dal.dataobject.aftersale.CrmAfterSaleTicketDO;
import cn.iocoder.txgy.module.crm.dal.dataobject.aftersale.CrmAfterSaleWarrantyDO;
import cn.iocoder.txgy.module.crm.dal.dataobject.contract.CrmContractDO;
import cn.iocoder.txgy.module.crm.dal.dataobject.customer.CrmCustomerDO;
import cn.iocoder.txgy.module.crm.service.aftersale.CrmAfterSaleTicketService;
import cn.iocoder.txgy.module.crm.service.aftersale.CrmAfterSaleWarrantyService;
import cn.iocoder.txgy.module.crm.service.contract.CrmContractService;
import cn.iocoder.txgy.module.crm.service.customer.CrmCustomerService;
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

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;
import static cn.iocoder.txgy.framework.common.util.collection.CollectionUtils.convertSet;
import static cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "管理后台 - CRM 售后问题工单")
@RestController
@RequestMapping("/crm/after-sale/ticket")
@Validated
public class CrmAfterSaleTicketController {

    @Resource
    private CrmAfterSaleTicketService ticketService;
    @Resource
    private CrmAfterSaleWarrantyService warrantyService;
    @Resource
    private CrmContractService contractService;
    @Resource
    private CrmCustomerService customerService;
    @Resource
    private AdminUserApi adminUserApi;

    @PostMapping("/create")
    @Operation(summary = "创建问题工单")
    @PreAuthorize("@ss.hasPermission('crm:after-sale-ticket:create')")
    public CommonResult<Long> createTicket(@Valid @RequestBody CrmAfterSaleTicketSaveReqVO createReqVO) {
        return success(ticketService.createTicket(createReqVO, getLoginUserId()));
    }

    @PutMapping("/update")
    @Operation(summary = "更新问题工单")
    @PreAuthorize("@ss.hasPermission('crm:after-sale-ticket:update')")
    public CommonResult<Boolean> updateTicket(@Valid @RequestBody CrmAfterSaleTicketSaveReqVO updateReqVO) {
        ticketService.updateTicket(updateReqVO, getLoginUserId());
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除问题工单")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('crm:after-sale-ticket:delete')")
    public CommonResult<Boolean> deleteTicket(@RequestParam("id") Long id) {
        ticketService.deleteTicket(id, getLoginUserId());
        return success(true);
    }

    @PostMapping("/assign")
    @Operation(summary = "指派工单处理人")
    @PreAuthorize("@ss.hasPermission('crm:after-sale-ticket:update')")
    public CommonResult<Boolean> assignTicket(@RequestParam("id") Long id,
                                              @RequestParam("assigneeUserId") Long assigneeUserId) {
        ticketService.assignTicket(id, assigneeUserId, getLoginUserId());
        return success(true);
    }

    @PostMapping("/update-status")
    @Operation(summary = "更新工单状态（待处理/处理中/已完成/已关闭）")
    @PreAuthorize("@ss.hasPermission('crm:after-sale-ticket:update')")
    public CommonResult<Boolean> updateTicketStatus(@RequestParam("id") Long id,
                                                    @RequestParam("status") Integer status,
                                                    @RequestParam(value = "content", required = false) String content) {
        ticketService.updateTicketStatus(id, status, content, getLoginUserId());
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得问题工单")
    @Parameter(name = "id", description = "编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('crm:after-sale-ticket:query')")
    public CommonResult<CrmAfterSaleTicketRespVO> getTicket(@RequestParam("id") Long id) {
        CrmAfterSaleTicketRespVO ticket = BeanUtils.toBean(ticketService.getTicket(id),
                CrmAfterSaleTicketRespVO.class);
        // 与分页接口保持一致：带出客户名称、合同/质保编号与人员昵称，供详情页展示
        fillTicketExtraInfo(List.of(ticket));
        return success(ticket);
    }

    @GetMapping("/page")
    @Operation(summary = "获得问题工单分页")
    @PreAuthorize("@ss.hasPermission('crm:after-sale-ticket:query')")
    public CommonResult<PageResult<CrmAfterSaleTicketRespVO>> getTicketPage(@Valid CrmAfterSaleTicketPageReqVO pageReqVO) {
        // remindType = 工单超时预警 时用于待办，归属范围按当前登录人（本人 + 下属）过滤
        PageResult<CrmAfterSaleTicketDO> pageResult = ticketService.getTicketPage(pageReqVO, getLoginUserId());
        PageResult<CrmAfterSaleTicketRespVO> respPage = BeanUtils.toBean(pageResult, CrmAfterSaleTicketRespVO.class);
        if (CollUtil.isEmpty(respPage.getList())) {
            return success(respPage);
        }
        // 拼接客户、合同、质保与人员昵称
        fillTicketExtraInfo(respPage.getList());
        return success(respPage);
    }

    @GetMapping("/remind-count")
    @Operation(summary = "获得「工单超时预警」待办数量（实时查询，不落库）")
    @PreAuthorize("@ss.hasPermission('crm:after-sale-ticket:query')")
    public CommonResult<Long> getTicketOverdueCount() {
        return success(ticketService.getTicketOverdueCount(getLoginUserId()));
    }

    /**
     * 填充工单的扩展展示字段：客户名称、合同编号、质保编号、处理人与创建人昵称
     *
     * <p>分页与详情（/get）接口共用，保证各入口展示口径一致
     */
    private void fillTicketExtraInfo(List<CrmAfterSaleTicketRespVO> list) {
        if (CollUtil.isEmpty(list)) {
            return;
        }
        Map<Long, CrmCustomerDO> customerMap = customerService.getCustomerMap(
                convertSet(list, CrmAfterSaleTicketRespVO::getCustomerId));
        Map<Long, CrmContractDO> contractMap = contractService.getContractMap(
                convertSet(list, CrmAfterSaleTicketRespVO::getContractId));
        Map<Long, CrmAfterSaleWarrantyDO> warrantyMap = warrantyService.getWarrantyMap(
                list.stream().map(CrmAfterSaleTicketRespVO::getWarrantyId).filter(java.util.Objects::nonNull).collect(
                        java.util.stream.Collectors.toSet()));
        Set<Long> userIds = new HashSet<>(list.stream().map(CrmAfterSaleTicketRespVO::getAssigneeUserId)
                .filter(java.util.Objects::nonNull).toList());
        list.forEach(ticket -> {
            if (StrUtil.isNumeric(ticket.getCreator())) {
                userIds.add(Long.valueOf(ticket.getCreator()));
            }
        });
        Map<Long, AdminUserRespDTO> userMap = adminUserApi.getUserMap(userIds);
        list.forEach(ticket -> {
            MapUtils.findAndThen(customerMap, ticket.getCustomerId(),
                    customer -> ticket.setCustomerName(customer.getName()));
            MapUtils.findAndThen(contractMap, ticket.getContractId(),
                    contract -> ticket.setContractNo(contract.getNo()));
            MapUtils.findAndThen(warrantyMap, ticket.getWarrantyId(),
                    warranty -> ticket.setWarrantyNo(warranty.getNo()));
            MapUtils.findAndThen(userMap, ticket.getAssigneeUserId(),
                    user -> ticket.setAssigneeNickname(user.getNickname()));
            if (StrUtil.isNumeric(ticket.getCreator())) {
                MapUtils.findAndThen(userMap, Long.valueOf(ticket.getCreator()),
                        user -> ticket.setCreatorNickname(user.getNickname()));
            }
        });
    }

}
