package cn.iocoder.txgy.module.bidding.service.ruleconfig;

import cn.iocoder.txgy.module.bidding.controller.admin.meta.vo.DictItemRespVO;

import java.util.List;
import java.util.Map;

/**
 * 业务字典服务（契约 §1.1 的数据来源）。
 *
 * <p>规范出处：契约《电子证照模块-前端接口契约-v1.0.md》§1.1；用户硬约束"枚举值必须支持动态配置"。
 * <p>为什么字典要有"缺失兜底"：运营漏配某类型时，若接口返回空数组，前端下拉会变成空、用户无法操作；
 * 因此按枚举类兜底出 code 列表（label=code），保证功能可用且"新枚举值不会消失"，同时暴露配置缺口。
 * <p>业务目标：前端枚举展示与后台配置始终一致，配置缺口不影响功能可用性。
 */
public interface MetaDictService {

    /** 取多个类型的字典；types 为空则返回全部受支持类型。 */
    Map<String, List<DictItemRespVO>> getDicts(List<String> dictTypes);

    /** 全部受支持的字典类型 code（前端可据此做"字典自检"）。 */
    List<String> supportedTypes();
}
