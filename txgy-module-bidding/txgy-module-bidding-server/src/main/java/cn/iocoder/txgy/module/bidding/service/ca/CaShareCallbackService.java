package cn.iocoder.txgy.module.bidding.service.ca;

import cn.iocoder.txgy.module.bidding.controller.admin.ca.vo.CaShareSealCallbackReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.ca.vo.CaShareSealClientCallbackReqVO;

/**
 * 官方 CA 互认签章回调处理（对接官方签章验签工具的回调落地）。
 *
 * <p>契约来源：官方《签章验签工具接入指南》§4.2（docs/接口互认/07-签章验签工具接入指南-交易系统侧.md）。
 *
 * <p><b>两种回调的差异（必须分别处理）</b>：
 * <ul>
 *   <li><b>客户端</b>（§4.2.1）：扁平结构；触发场景=「点击完成签章」或「手动关闭程序」；
 *       callbackRequestType 取值 exitSignature / signedAndVerified；带机器指纹（ip/mac/cpu/硬盘）；</li>
 *   <li><b>H5 组件</b>（§4.2.2）：action + data 包裹；action 取值 sign / allDone；
 *       data.status 10 进行中 / 20 已完成 / 30 失败；带对象存储 FileKey 与业务流水号 transactionId。</li>
 * </ul>
 *
 * <p><b>落库口径</b>：以"待签文件 Hash"为主键匹配本模块台账
 * （签章前登记时已记录原文指纹），回写签后 Hash / 签章数量 / 签署状态；匹配不到只记日志不报错
 * （官方回调可能来自其他交易系统的文件，沉默跳过优于让官方回调失败）。
 */
public interface CaShareCallbackService {

    /**
     * 处理官方 H5 签章组件回调（§4.2.2）。
     *
     * @param reqVO 官方回调报文（字段逐字对齐官方）
     */
    void handleH5Callback(CaShareSealCallbackReqVO reqVO);

    /**
     * 处理官方客户端签章工具回调（§4.2.1）。
     *
     * @param reqVO 官方回调报文（字段逐字对齐官方）
     */
    void handleClientCallback(CaShareSealClientCallbackReqVO reqVO);
}
