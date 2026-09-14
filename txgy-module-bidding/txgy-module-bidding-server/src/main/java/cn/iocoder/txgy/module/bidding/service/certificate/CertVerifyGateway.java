package cn.iocoder.txgy.module.bidding.service.certificate;

import cn.iocoder.txgy.module.bidding.api.cert.vo.EccertVerifyFileReqVO;
import cn.iocoder.txgy.module.bidding.api.cert.vo.EccertVerifyFileRespVO;
import cn.iocoder.txgy.module.bidding.api.cert.vo.EccertVerifyInfoReqVO;
import cn.iocoder.txgy.module.bidding.api.cert.vo.EccertVerifyInfoRespVO;

/**
 * 电子证照核验网关（GB/T 36906 外部共享服务客户端端口）。
 *
 * <p>规范出处：《资质管理/资质管理规范文件/GBT+36906-2018.md》GB/T 36906—2018 §6.4/§6.5
 * （verifyinfo 证照信息验证 / verifyfile 证照文件验证，head/data JSON 报文）＋§5.2.2 应用认证。
 * <p>设计遵循/针对：业务层面向本端口编程（开发规范 §6 外部集成接口隔离）；实现可选择
 * 真实 HTTP 通道或本地演示通道（配置 bidding.cert.verify.mock 切换）；报文对象复用 api/cert/vo
 * 规范 VO（05-规范VO表设计 §4.3），保证与 GB/T 36906 字段一一照录。
 * <p>注意：GB/T 36906 另有 query/queryinfo/downloadfile/getdownloadurl（信息/文件获取，§6.2/§6.3），
 * 属“共享服务提供方”能力，由本模块对外提供服务时在外部接口层落地（后续里程碑），本端口只声明核验调用。
 */
public interface CertVerifyGateway {

    /**
     * 证照信息验证（GB/T 36906 §6.4 verifyinfo）：待验证证照数据（键=GB/T 36903—2018 英文名）。
     */
    EccertVerifyInfoRespVO verifyInfo(EccertVerifyInfoReqVO request);

    /**
     * 证照文件验证（GB/T 36906 §6.5 verifyfile）：待验证电子证照原件/加注件内容（GB/T 36905 OFD）。
     */
    EccertVerifyFileRespVO verifyFile(EccertVerifyFileReqVO request);
}
