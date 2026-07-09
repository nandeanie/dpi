package com.dpiengine.model;

/**
 * Application classification assigned to a flow once enough information (SNI, HTTP Host,
 * or well-known port) has been observed. Mirrors the AppType enum from the original C++
 * engine's types.h.
 */
public enum AppType {
    UNKNOWN("Unknown"),
    HTTP("HTTP"),
    HTTPS("HTTPS"),
    DNS("DNS"),
    GOOGLE("Google"),
    YOUTUBE("YouTube"),
    FACEBOOK("Facebook"),
    INSTAGRAM("Instagram"),
    TIKTOK("TikTok"),
    TWITTER("Twitter/X"),
    NETFLIX("Netflix"),
    AMAZON("Amazon"),
    GITHUB("GitHub"),
    MICROSOFT("Microsoft"),
    APPLE("Apple"),
    WHATSAPP("WhatsApp"),
    SPOTIFY("Spotify"),
    TWITCH("Twitch"),
    REDDIT("Reddit");

    private final String displayName;

    AppType(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
