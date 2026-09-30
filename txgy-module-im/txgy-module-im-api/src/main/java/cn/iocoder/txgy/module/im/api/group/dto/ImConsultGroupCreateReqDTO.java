package cn.iocoder.txgy.module.im.api.group.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/** 咨询会话建群请求：业务模块一单一群，保证会话与租户、订单强绑定。 */
@Data
public class ImConsultGroupCreateReqDTO implements Serializable {

    /** 群主用户编号（通常为专家） */
    @NotNull(message = "群主不能为空")
    private Long ownerUserId;

    /** 初始成员（客户），不含群主 */
    @NotEmpty(message = "会话成员不能为空")
    private List<Long> memberUserIds;

    /** 群名称 */
    @NotBlank(message = "群名称不能为空")
    private String name;

    /** 群公告，用于承载订单号等溯源信息 */
    private String notice;
}