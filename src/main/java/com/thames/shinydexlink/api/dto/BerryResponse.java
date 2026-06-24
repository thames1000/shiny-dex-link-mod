package com.thames.shinydexlink.api.dto;

public final class BerryResponse {
    public boolean success;
    public String message;
    public int added;
    public int total;
    public int received;
    public int ignored;

    public static BerryResponse success() {
        BerryResponse response = new BerryResponse();
        response.success = true;
        response.message = "OK";
        return response;
    }
}
