package cn.iocoder.txgy.module.im.api.group;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.module.im.api.group.dto.ImConsultGroupCreateReqDTO;
import cn.iocoder.txgy.module.im.enums.ApiConstants;
import jakarta.validation.Valid;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/** 业务模块创建隔离咨询会话的 RPC 接口。 */
@FeignClient(name = ApiConstants.NAME)
public interface ImGroupApi {

    String PREFIX = ApiConstants.PREFIX + "/group";

    @PostMapping(PREFIX + "/create-consult-group")
    CommonResult<Long> createConsultGroup(@Valid @RequestBody ImConsultGroupCreateReqDTO reqDTO);
}