package dev.gameops.domainmail.client;

public interface SendingDomainGateway {
    void requestVerification(String domain, String idempotencyKey);
    String verificationStatus(String domain);
}
