package dev.gameops.domainmail;

import dev.gameops.domainmail.client.InfraiEmailClient;
import dev.gameops.domainmail.config.DomainMailConfig;
import dev.gameops.domainmail.domain.GameMailWorkload;
import dev.gameops.domainmail.service.SendingDomainOnboardingService;

public final class DomainOnboardingCommand {
    private DomainOnboardingCommand() {}

    public static void main(String[] args) {
        if (args.length != 1 || args[0].isBlank()) {
            throw new IllegalArgumentException("usage: DomainOnboardingCommand <sending-domain>");
        }

        var workload = new GameMailWorkload(
                new GameMailWorkload.PlayerGeneratedAsset("map-1842", "player-77"),
                new GameMailWorkload.LiveEvent("season-12", "Citadel Cup"),
                new GameMailWorkload.ModerationQueue("ugc-email-release", 0));
        var service = new SendingDomainOnboardingService(
                new InfraiEmailClient(DomainMailConfig.fromEnvironment()));
        var result = service.onboard(args[0], workload);

        System.out.printf("domain=%s verification=%s release=%s%n",
                result.domain(), result.verificationStatus(), result.decision());
    }
}
