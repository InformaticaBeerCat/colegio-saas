package cl.colegiosaas.site;

import java.util.Objects;

public record SocialLink(Network network, String url) {

    public SocialLink {
        Objects.requireNonNull(network, "network");
        Objects.requireNonNull(url, "url");
    }

    public enum Network { FACEBOOK, INSTAGRAM, YOUTUBE, TIKTOK, X, LINKEDIN }
}
