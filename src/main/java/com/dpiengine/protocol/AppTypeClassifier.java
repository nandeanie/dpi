package com.dpiengine.protocol;

import com.dpiengine.model.AppType;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Maps a server hostname (from SNI or an HTTP Host header) to a coarse-grained AppType.
 * Matching is a simple substring lookup against a small, ordered table of known domain
 * fragments, the same approach the original engine used, just made easier to extend.
 */
public final class AppTypeClassifier {

    private static final Map<String, AppType> DOMAIN_FRAGMENTS = new LinkedHashMap<>();

    static {
        DOMAIN_FRAGMENTS.put("youtube", AppType.YOUTUBE);
        DOMAIN_FRAGMENTS.put("ytimg", AppType.YOUTUBE);
        DOMAIN_FRAGMENTS.put("googlevideo", AppType.YOUTUBE);
        DOMAIN_FRAGMENTS.put("facebook", AppType.FACEBOOK);
        DOMAIN_FRAGMENTS.put("fbcdn", AppType.FACEBOOK);
        DOMAIN_FRAGMENTS.put("instagram", AppType.INSTAGRAM);
        DOMAIN_FRAGMENTS.put("tiktok", AppType.TIKTOK);
        DOMAIN_FRAGMENTS.put("twitter", AppType.TWITTER);
        DOMAIN_FRAGMENTS.put("x.com", AppType.TWITTER);
        DOMAIN_FRAGMENTS.put("netflix", AppType.NETFLIX);
        DOMAIN_FRAGMENTS.put("nflxvideo", AppType.NETFLIX);
        DOMAIN_FRAGMENTS.put("amazon", AppType.AMAZON);
        DOMAIN_FRAGMENTS.put("github", AppType.GITHUB);
        DOMAIN_FRAGMENTS.put("microsoft", AppType.MICROSOFT);
        DOMAIN_FRAGMENTS.put("windows", AppType.MICROSOFT);
        DOMAIN_FRAGMENTS.put("apple", AppType.APPLE);
        DOMAIN_FRAGMENTS.put("icloud", AppType.APPLE);
        DOMAIN_FRAGMENTS.put("whatsapp", AppType.WHATSAPP);
        DOMAIN_FRAGMENTS.put("spotify", AppType.SPOTIFY);
        DOMAIN_FRAGMENTS.put("twitch", AppType.TWITCH);
        DOMAIN_FRAGMENTS.put("reddit", AppType.REDDIT);
        DOMAIN_FRAGMENTS.put("google", AppType.GOOGLE);
        DOMAIN_FRAGMENTS.put("gstatic", AppType.GOOGLE);
    }

    public AppType classifyByHostname(String hostname) {
        String lower = hostname.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, AppType> entry : DOMAIN_FRAGMENTS.entrySet()) {
            if (lower.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return AppType.HTTPS;
    }

    public AppType classifyByWellKnownPort(int destinationPort, int sourcePort) {
        if (destinationPort == 53 || sourcePort == 53) {
            return AppType.DNS;
        }
        if (destinationPort == 443 || sourcePort == 443) {
            return AppType.HTTPS;
        }
        if (destinationPort == 80 || sourcePort == 80) {
            return AppType.HTTP;
        }
        return AppType.UNKNOWN;
    }
}
