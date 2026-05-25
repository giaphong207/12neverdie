package com.auction.shared.networkMessage.response;

import java.io.Serializable;

/**
 * Phản hồi của server cho SetAutoBidRequest.
 */
public class SetAutoBidResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    private final boolean success;
    private final String message;

    public SetAutoBidResponse(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
}