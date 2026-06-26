package com.thames.shinydexlink.api.dto;

/**
 * Backend reply to a hunt-progress fetch. When {@link #found} is true, {@link #hunt} carries the
 * saved progress to resume from; otherwise it is null and the hunt starts fresh.
 */
public final class HuntFetchResponse {
    public boolean success;
    public boolean found;
    public String message;
    public HuntProgressDto hunt;

    public static HuntFetchResponse notFound() {
        HuntFetchResponse response = new HuntFetchResponse();
        response.success = true;
        response.found = false;
        response.message = "No saved hunt";
        return response;
    }
}
