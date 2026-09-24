package com.gucardev.restapiintegration.client.error;

/** 4xx: the request itself is wrong (bad input, bad credentials). Retrying cannot help. */
public class RemoteApiClientException extends RemoteApiException {

    public RemoteApiClientException(String message, int status, String responseBody) {
        super(message, status, responseBody);
    }
}
