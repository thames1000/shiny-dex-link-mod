package com.thames.shinydexlink.api.dto;

/** Backend acknowledgement of a hunt-progress push. */
public final class HuntSyncResponse {
    public boolean success;
    public String message;
    public int stored;

    public boolean accepted() {
        return success;
    }

    public static HuntSyncResponse success() {
        HuntSyncResponse response = new HuntSyncResponse();
        response.success = true;
        response.message = "OK";
        return response;
    }
}
