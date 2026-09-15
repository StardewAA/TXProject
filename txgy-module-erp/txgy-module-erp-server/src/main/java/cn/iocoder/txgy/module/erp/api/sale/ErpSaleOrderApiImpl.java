package cn.iocoder.txgy.module.erp.api.sale;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.module.erp.api.sale.dto.ErpSaleOrderItemRespDTO;
import cn.iocoder.txgy.module.erp.api.sale.dto.ErpSaleOrderRespDTO;
import cn.iocoder.txgy.module.erp.dal.dataobject.product.ErpProductDO;
import cn.iocoder.txgy.module.erp.dal.dataobject.sale.ErpCustomerDO;
import cn.iocoder.txgy.module.erp.dal.dataobject.sale.ErpSaleOrderDO;
import cn.iocoder.txgy.module.erp.dal.dataobject.sale.ErpSaleOrderItemDO;
import cn.iocoder.txgy.module.erp.dal.mysql.product.ErpProductMapper;
import cn.iocoder.txgy.module.erp.dal.mysql.sale.ErpSaleOrderMapper;
import cn.iocoder.txgy.module.erp.service.sale.ErpCustomerService;
import cn.iocoder.txgy.module.erp.service.sale.ErpSaleOrderService;
import cn.iocoder.txgy.module.system.api.user.AdminUserApi;
import cn.iocoder.txgy.module.system.api.user.dto.AdminUserRespDTO;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

/**
 * 销售订单 RPC API 实现类
 *
 * 只读查询，供 MES 等下游模块获取订单基础信息。客户名称、销售员姓名在此提前组装，
 * 避免下游模块反向依赖 ERP 内部数据结构。
 *
 * @author OPENLAB BS
 */
@RestController
@Validated
public class ErpSaleOrderApiImpl implements ErpSaleOrderApi {

    @Resource
    private ErpSaleOrderService saleOrderService;
    @Resource
    private ErpSaleOrderMapper saleOrderMapper;
    @Resource
    private ErpProductMapper erpProductMapper;
    @Resource
    private ErpCustomerService customerService;
    @Resource
    private AdminUserApi adminUserApi;

    @Override
    public CommonResult<ErpSaleOrderRespDTO> getSaleOrder(Long id) {
        ErpSaleOrderDO saleOrder = saleOrderService.getSaleOrder(id);
        if (saleOrder == null) {
            return success(null);
        }
        return success(buildRespDTO(saleOrder));
    }

    @Override
    public CommonResult<ErpSaleOrderRespDTO> getSaleOrderByNo(String no) {
        ErpSaleOrderDO saleOrder = saleOrderMapper.selectByNo(no);
        if (saleOrder == null) {
            return success(null);
        }
        return success(buildRespDTO(saleOrder));
    }

    private ErpSaleOrderRespDTO buildRespDTO(ErpSaleOrderDO saleOrder) {
        ErpSaleOrderRespDTO dto = new ErpSaleOrderRespDTO();
        dto.setId(saleOrder.getId());
        dto.setNo(saleOrder.getNo());
        dto.setStatus(saleOrder.getStatus());
        dto.setCustomerId(saleOrder.getCustomerId());
        dto.setSaleUserId(saleOrder.getSaleUserId());
        dto.setOrderTime(saleOrder.getOrderTime());
        dto.setTotalCount(saleOrder.getTotalCount());
        dto.setTotalPrice(saleOrder.getTotalPrice());
        dto.setRemark(saleOrder.getRemark());
        // 组装客户名称
        if (saleOrder.getCustomerId() != null) {
            ErpCustomerDO customer = customerService.getCustomer(saleOrder.getCustomerId());
            if (customer != null) {
                dto.setCustomerName(customer.getName());
            }
        }
        // 组装销售员姓名
        if (saleOrder.getSaleUserId() != null) {
            AdminUserRespDTO user = adminUserApi.getUser(saleOrder.getSaleUserId()).getCheckedData();
            if (user != null) {
                dto.setSaleUserName(user.getNickname());
            }
        }
        // 组装明细行（供 MES 按订单行生成生产工单）
        List<ErpSaleOrderItemDO> itemDOList = saleOrderService.getSaleOrderItemListByOrderId(saleOrder.getId());
        if (CollUtil.isNotEmpty(itemDOList)) {
            List<ErpSaleOrderItemRespDTO> itemDTOList = new ArrayList<>();
            for (ErpSaleOrderItemDO itemDO : itemDOList) {
                ErpSaleOrderItemRespDTO itemDTO = new ErpSaleOrderItemRespDTO();
                itemDTO.setId(itemDO.getId());
                itemDTO.setProductId(itemDO.getProductId());
                itemDTO.setProductUnitId(itemDO.getProductUnitId());
                itemDTO.setCount(itemDO.getCount());
                ErpProductDO product = erpProductMapper.selectById(itemDO.getProductId());
                if (product != null) {
                    itemDTO.setProductName(product.getName());
                    itemDTO.setProductBarCode(product.getBarCode());
                    itemDTO.setMaterialCode(product.getMaterialCode());
                }
                itemDTOList.add(itemDTO);
            }
            dto.setItems(itemDTOList);
        }
        return dto;
    }

    private List<ErpSaleOrderItemRespDTO> buildSaleOrderItems(Long orderId) {
        List<ErpSaleOrderItemDO> items = saleOrderItemMapper.selectList(
                ErpSaleOrderItemDO::getOrderId, orderId);
        if (items.isEmpty()) {
            return List.of();
        }
        // 批量组装产品名称与单位名称
        Map<Long, ErpProductDO> productMap = productMapper.selectByIds(
                        items.stream().map(ErpSaleOrderItemDO::getProductId)
                                .filter(Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(ErpProductDO::getId, Function.identity()));
        Map<Long, ErpProductUnitDO> unitMap = productUnitMapper.selectByIds(
                        items.stream().map(ErpSaleOrderItemDO::getProductUnitId)
                                .filter(Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(ErpProductUnitDO::getId, Function.identity()));
        return items.stream().map(item -> {
            ErpSaleOrderItemRespDTO itemDto = new ErpSaleOrderItemRespDTO();
            itemDto.setProductId(item.getProductId());
            itemDto.setCount(item.getCount());
            itemDto.setTotalPrice(item.getTotalPrice());
            ErpProductDO product = productMap.get(item.getProductId());
            if (product != null) {
                itemDto.setProductName(product.getName());
            }
            ErpProductUnitDO unit = unitMap.get(item.getProductUnitId());
            if (unit != null) {
                itemDto.setProductUnitName(unit.getName());
            }
            return itemDto;
        }).collect(Collectors.toList());
    }

}
