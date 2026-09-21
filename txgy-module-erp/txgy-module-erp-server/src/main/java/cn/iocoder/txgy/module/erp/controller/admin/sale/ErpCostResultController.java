package cn.iocoder.txgy.module.erp.controller.admin.sale;
import cn.iocoder.txgy.framework.common.pojo.*;
import cn.iocoder.txgy.framework.common.util.collection.MapUtils;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.erp.controller.admin.product.vo.product.ErpProductRespVO;
import cn.iocoder.txgy.module.erp.controller.admin.sale.vo.cost.*;
import cn.iocoder.txgy.module.erp.dal.dataobject.sale.*;
import cn.iocoder.txgy.module.erp.service.product.ErpProductService;
import cn.iocoder.txgy.module.erp.service.sale.ErpCostResultService;
import jakarta.annotation.Resource; import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank; import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize; import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;
import static cn.iocoder.txgy.framework.common.util.collection.CollectionUtils.*;
import static cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
@RestController @RequestMapping("/erp/cost-result") @Validated
public class ErpCostResultController {
 @Resource private ErpCostResultService service; @Resource private ErpProductService productService;
 @PostMapping("/create") @PreAuthorize("@ss.hasPermission('erp:cost-result:create')") public CommonResult<Long> create(@Valid @RequestBody ErpCostResultSaveReqVO v){return success(service.create(v));}
 @PutMapping("/update") @PreAuthorize("@ss.hasPermission('erp:cost-result:update')") public CommonResult<Boolean> update(@Valid @RequestBody ErpCostResultSaveReqVO v){service.update(v);return success(true);}
 @DeleteMapping("/delete") @PreAuthorize("@ss.hasPermission('erp:cost-result:delete')") public CommonResult<Boolean> delete(@RequestParam("ids") List<Long> ids){service.delete(ids);return success(true);}
 @PutMapping("/submit") @PreAuthorize("@ss.hasPermission('erp:cost-result:submit')") public CommonResult<Boolean> submit(@RequestParam("id") Long id){service.submit(id,getLoginUserId());return success(true);}
 @PutMapping("/reverse-approve") @PreAuthorize("@ss.hasPermission('erp:cost-result:reverse-approve')")
 public CommonResult<Boolean> reverseApprove(@RequestParam("id") Long id,
                                             @RequestParam("reason") @NotBlank(message = "反审批原因不能为空")
                                             @Size(max = 200, message = "反审批原因不能超过 200 个字符") String reason) {
     service.reverseApprove(id, getLoginUserId(), reason);
     return success(true);
 }
 @PutMapping("/convert") @PreAuthorize("@ss.hasPermission('erp:cost-result:convert')") public CommonResult<Long> convert(@Valid @RequestBody ErpCostResultConvertReqVO v){return success(service.convert(v));}
 @GetMapping("/get") @PreAuthorize("@ss.hasPermission('erp:cost-result:query')") public CommonResult<ErpCostResultRespVO> get(@RequestParam("id") Long id){ErpCostResultDO r=service.get(id);return success(r==null?null:build(r));}
 @GetMapping("/page") @PreAuthorize("@ss.hasPermission('erp:cost-result:query')") public CommonResult<PageResult<ErpCostResultRespVO>> page(@Valid ErpCostResultPageReqVO v){PageResult<ErpCostResultDO> p=service.getPage(v);return success(BeanUtils.toBean(p,ErpCostResultRespVO.class,vo->{ErpCostResultDO r=p.getList().stream().filter(x->x.getId().equals(vo.getId())).findFirst().orElseThrow();vo.setItems(build(r).getItems());}));}
 private ErpCostResultRespVO build(ErpCostResultDO r){ErpCostResultRespVO vo=BeanUtils.toBean(r,ErpCostResultRespVO.class);List<ErpCostResultItemDO> items=service.getItems(List.of(r.getId()));Map<Long,ErpProductRespVO> products=productService.getProductVOMap(items.stream().map(ErpCostResultItemDO::getProductId).filter(Objects::nonNull).collect(java.util.stream.Collectors.toSet()));vo.setItems(BeanUtils.toBean(items,ErpCostResultRespVO.Item.class,i->MapUtils.findAndThen(products,i.getProductId(),p->i.setProductName(p.getName()).setProductUnitName(p.getUnitName()))));return vo;}
}
