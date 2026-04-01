package com.api.finance.bff.excetlions;


// quando o session_id nao existe no redis
public class SessionNotFoundException extends RuntimeException {
    public SessionNotFoundException(String message) {
        super(message);
    }
}
