package cn.iocoder.txgy.module.training.service.analytics;

import cn.iocoder.txgy.module.training.dal.dataobject.analytics.CertVerifyRecordDO;
import cn.iocoder.txgy.module.training.dal.mysql.analytics.CertVerifyRecordMapper;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.HashMap;
import java.util.Map;

/**
 * 97 证书公开验真 Service 实现（框架骨架）
 *
 * @author 云培训学院课题小组
 */
@Service
@Validated
public class CertVerifyServiceImpl implements CertVerifyService {

    @Resource
    private CertVerifyRecordMapper certVerifyRecordMapper;

    @Override
    @PermitAll
    public Map<String, Object> verify(String certCode, String verifyIp, String userAgent) {
        // TODO[模块九-证书验真] 真实验真：按证书编号查询证书有效性/吊销状态，
        //  仅返回公开验真字段（不泄露学员其他隐私档案）
        try {
            CertVerifyRecordDO record = new CertVerifyRecordDO();
            record.setCertCode(certCode);
            record.setVerifyResult(0);
            record.setVerifyIp(verifyIp);
            record.setUserAgent(userAgent);
            certVerifyRecordMapper.insert(record);
        } catch (Exception ex) {
            // 免登录场景无租户上下文，留痕失败不阻塞验真响应；后续按 ignore-table 方案修正
        }
        Map<String, Object> result = new HashMap<>();
        result.put("certCode", certCode);
        result.put("valid", false);
        result.put("revoked", false);
        result.put("message", "验真功能开发中，当前未查询到证书信息");
        return result;
    }

}
