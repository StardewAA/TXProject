package cn.iocoder.txgy.module.bidding.controller.admin.meta.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 规则配置批量响应（契约 §1.2 的 data）。
 *
 * <p>规范出处：契约《电子证照模块-前端接口契约-v1.0.md》§1.2。
 * <p>为什么用对象包一层而不是直接返回数组：后续要加"配置版本戳/缓存过期时间"等元信息时不会破坏契约（数组无法扩展字段）。
 * <p>业务目标：前端一次拿到渲染配置页所需的全部信息，并具备向前兼容的扩展位。
 */
@Schema(description = "管理后台 - 规则配置批量响应")
@Data
public class MetaConfigsRespVO {

    @Schema(description = "配置项列表", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<RuleConfigItemRespVO> items;
}
