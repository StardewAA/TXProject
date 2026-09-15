package cn.iocoder.txgy.module.cms.controller.admin;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

/**
 * CMS 骨架验证接口
 *
 * 用于验证 cms-server 的注册、网关路由与租户上下文是否正常，后续可删除。
 */
@Tag(name = "管理后台 - CMS 骨架验证")
@RestController
@RequestMapping("/cms")
@Validated
public class CmsPingController {

    @GetMapping("/ping")
    @Operation(summary = "ping 测试")
    public CommonResult<String> ping() {
        return success("pong");
    }

}
