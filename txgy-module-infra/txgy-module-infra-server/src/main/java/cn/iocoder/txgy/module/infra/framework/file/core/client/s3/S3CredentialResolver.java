package cn.iocoder.txgy.module.infra.framework.file.core.client.s3;

import cn.hutool.core.util.StrUtil;

import java.util.function.Function;

/**
 * Resolves optional {@code env:VARIABLE_NAME} credential references without persisting secrets
 * in the file configuration database.
 */
final class S3CredentialResolver {

    private static final String ENV_PREFIX = "env:";
    private static final String ENV_NAME_REGEX = "[A-Za-z_][A-Za-z0-9_]*";

    private S3CredentialResolver() {
    }

    static String resolve(String configuredValue, String credentialName) {
        return resolve(configuredValue, credentialName, System::getenv);
    }

    static String resolve(String configuredValue, String credentialName, Function<String, String> envLookup) {
        if (StrUtil.isBlank(configuredValue)) {
            throw new IllegalStateException("S3 " + credentialName + " is not configured");
        }
        if (!configuredValue.startsWith(ENV_PREFIX)) {
            // Keep compatibility with existing storage configurations. New OSS configurations
            // should use env references so credentials are not stored in the database.
            return configuredValue;
        }

        String envName = configuredValue.substring(ENV_PREFIX.length());
        if (!envName.matches(ENV_NAME_REGEX)) {
            throw new IllegalStateException("S3 " + credentialName + " environment variable reference is invalid");
        }
        String resolved = envLookup.apply(envName);
        if (StrUtil.isBlank(resolved)) {
            throw new IllegalStateException("Required S3 credential environment variable is missing: " + envName);
        }
        return resolved;
    }

}
