package cn.iocoder.txgy.module.trade.api.delivery;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.module.trade.enums.ApiConstants;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import java.util.List;

/** 商城内部只读运费模板目录；不提供模板修改能力。 */
@FeignClient(name = ApiConstants.NAME, contextId = "supplierDeliveryTemplate")
public interface DeliveryTemplateApi {
    String PATH = ApiConstants.PREFIX + "/delivery-template/list";
    record Template(Long id, String name, Integer chargeMode) {}
    @GetMapping(PATH)
    CommonResult<List<Template>> list();
}
