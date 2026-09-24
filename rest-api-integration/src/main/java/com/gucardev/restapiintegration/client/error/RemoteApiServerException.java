package com.gucardev.restapiintegration.client.error;

/** 5xx: the remote side failed. Often temporary, so it is worth retrying. */
public class RemoteApiServerException extends RemoteApiException {

    public RemoteApiServerException(String message, int status, String responseBody) {
        super(message, status, responseBody);
    }
}
