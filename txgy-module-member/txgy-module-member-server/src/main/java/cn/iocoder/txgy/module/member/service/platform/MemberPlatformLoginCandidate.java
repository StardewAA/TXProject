package cn.iocoder.txgy.module.member.service.platform;

import lombok.Data;

/** Internal result only. The source tenant is derived from a READY canonical link, never from the client. */
@Data
public class MemberPlatformLoginCandidate {

    private Long platformAccountId;
    private String platformNo;
    private String credentialType;
    private String credentialIdentifier;
    private Long canonicalMemberId;
    private Long sourceTenantId;
}
