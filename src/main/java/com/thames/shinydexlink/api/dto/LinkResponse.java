package com.thames.shinydexlink.api.dto;

public final class LinkResponse {
    public boolean success;
    public String message;
    public String linkedAccountId;

    public static LinkResponse mock(String message) {
        LinkResponse response = new LinkResponse();
        response.success = true;
        response.message = message;
        response.linkedAccountId = "mock-account";
        return response;
    }
}
