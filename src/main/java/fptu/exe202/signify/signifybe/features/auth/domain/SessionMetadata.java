package fptu.exe202.signify.signifybe.features.auth.domain;

import fptu.exe202.signify.signifybe.common.AuthValidation;

/** Untrusted display metadata, never used as proof of identity or authorization. */
public record SessionMetadata(String deviceName, String ipAddress, String userAgent) {
    public SessionMetadata {
        deviceName = AuthValidation.formatMetadata(deviceName, AuthValidation.DEVICE_NAME_MAX_LENGTH);
        ipAddress = AuthValidation.formatMetadata(ipAddress, AuthValidation.IP_ADDRESS_MAX_LENGTH);
        userAgent = AuthValidation.formatMetadata(userAgent, AuthValidation.USER_AGENT_MAX_LENGTH);
    }

}
