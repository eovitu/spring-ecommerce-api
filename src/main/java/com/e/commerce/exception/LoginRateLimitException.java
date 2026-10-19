package com.e.commerce.exception;

public class LoginRateLimitException extends RuntimeException {
    private final long retryAfter;

    public LoginRateLimitException(long retryAfter) {
        super("Tente novamente mais tarde");
        this.retryAfter = retryAfter;
    }

    public long getRetryAfter() {
        return retryAfter;
    }
}
