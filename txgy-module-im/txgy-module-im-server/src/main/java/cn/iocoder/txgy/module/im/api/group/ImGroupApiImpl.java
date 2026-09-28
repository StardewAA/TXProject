package cn.iocoder.txgy.module.im.api.group;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.module.im.api.group.dto.ImConsultGroupCreateReqDTO;
import cn.iocoder.txgy.module.im.service.group.ImGroupService;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

/** IM 群会话 RPC 实现：供业务模块（专家咨询等）创建隔离会话。 */
@RestController
@Validated
public class ImGroupApiImpl implements ImGroupApi {

    @Resource
    private ImGroupService groupService;

    @Override
    public CommonResult<Long> createConsultGroup(ImConsultGroupCreateReqDTO reqDTO) {
        return success(groupService.createConsultGroup(reqDTO));
    }

    @Override
    public CommonResult<Boolean> closeConsultGroup(Long groupId) {
        groupService.closeConsultGroup(groupId);
        return success(true);
    }
}