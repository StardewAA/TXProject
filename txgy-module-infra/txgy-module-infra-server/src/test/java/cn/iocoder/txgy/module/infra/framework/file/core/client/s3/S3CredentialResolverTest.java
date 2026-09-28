package cn.iocoder.txgy.module.infra.framework.file.core.client.s3;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class S3CredentialResolverTest {

    @Test
    void resolvesEnvironmentReference() {
        assertEquals("test-access-key", S3CredentialResolver.resolve(
                "env:OSS_ACCESS_KEY", "access key", Map.of("OSS_ACCESS_KEY", "test-access-key")::get));
    }

    @Test
    void rejectsMissingEnvironmentReferenceWithoutExposingCredential() {
        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                S3CredentialResolver.resolve("env:OSS_ACCESS_SECRET", "access secret", ignored -> null));

        assertEquals("Required S3 credential environment variable is missing: OSS_ACCESS_SECRET",
                exception.getMessage());
    }

    @Test
    void keepsLegacyLiteralCredentialsCompatible() {
        assertEquals("legacy-test-value", S3CredentialResolver.resolve(
                "legacy-test-value", "access key", ignored -> null));
    }

    @Test
    void rejectsMalformedEnvironmentReference() {
        assertThrows(IllegalStateException.class, () ->
                S3CredentialResolver.resolve("env:OSS_ACCESS_SECRET;echo", "access secret", ignored -> "x"));
    }

}
