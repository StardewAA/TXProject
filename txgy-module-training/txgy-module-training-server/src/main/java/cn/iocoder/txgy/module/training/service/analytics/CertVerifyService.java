package cn.iocoder.txgy.module.training.service.analytics;

import java.util.Map;

/**
 * 97 证书公开验真 Service 接口（免登录公共访问）
 *
 * @author 云培训学院课题小组
 */
public interface CertVerifyService {

    /**
     * 公开验真：输入证书编号，仅返回公开验真字段（不泄露学员隐私档案）
     *
     * @param certCode  证书编号
     * @param verifyIp  验真来源 IP
     * @param userAgent 验真来源 UA
     * @return 证书基础信息、是否有效、吊销状态与原因
     */
    Map<String, Object> verify(String certCode, String verifyIp, String userAgent);

}
