package com.thames.shinydexlink.config;

public final class ShinyDexConfig {
    public boolean enabled = true;
    public String serverId = "cobbleverse-main";
    public String apiBaseUrl = "mock";
    public String serverToken = "change-me";
    public boolean syncUnlinkedPlayers = false;
    public boolean retryFailedEvents = true;
    public int retryIntervalSeconds = 60;
    public boolean logSuccessfulSyncs = true;
    public boolean announceShinySyncToPlayer = true;
    public int requestTimeoutSeconds = 10;
    public int linkCooldownSeconds = 15;
    public int testCooldownSeconds = 30;

    public boolean isMockApi() {
        return apiBaseUrl == null || apiBaseUrl.isBlank() || "mock".equalsIgnoreCase(apiBaseUrl.trim());
    }

    public void normalize() {
        if (serverId == null || serverId.isBlank()) {
            serverId = "cobbleverse-main";
        }
        if (apiBaseUrl == null) {
            apiBaseUrl = "mock";
        }
        if (serverToken == null) {
            serverToken = "";
        }
        retryIntervalSeconds = Math.max(5, retryIntervalSeconds);
        requestTimeoutSeconds = Math.max(1, requestTimeoutSeconds);
        linkCooldownSeconds = Math.max(0, linkCooldownSeconds);
        testCooldownSeconds = Math.max(0, testCooldownSeconds);
    }
}
