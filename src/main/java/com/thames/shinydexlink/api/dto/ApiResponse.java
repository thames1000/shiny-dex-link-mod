package com.thames.shinydexlink.api.dto;

public final class ApiResponse {
    public boolean success;
    public boolean duplicate;
    public String message;
    public Updated updated;

    public static ApiResponse success() {
        ApiResponse response = new ApiResponse();
        response.success = true;
        response.duplicate = false;
        response.message = "OK";
        response.updated = new Updated();
        response.updated.normalCaught = true;
        response.updated.shinyCaught = false;
        response.updated.newDexEntry = false;
        return response;
    }

    public boolean accepted() {
        return success || duplicate;
    }

    public static final class Updated {
        public boolean normalCaught;
        public boolean shinyCaught;
        public boolean newDexEntry;
    }
}
