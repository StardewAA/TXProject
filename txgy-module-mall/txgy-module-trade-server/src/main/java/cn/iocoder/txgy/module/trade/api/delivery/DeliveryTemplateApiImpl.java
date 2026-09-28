package cn.iocoder.txgy.module.trade.api.delivery;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.module.trade.service.delivery.DeliveryExpressTemplateService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
public class DeliveryTemplateApiImpl implements DeliveryTemplateApi {
    @Resource private DeliveryExpressTemplateService service;
    @Override public CommonResult<List<Template>> list() {
        return CommonResult.success(service.getDeliveryExpressTemplateList().stream()
                .map(t -> new Template(t.getId(), t.getName(), t.getChargeMode())).toList());
    }
}
