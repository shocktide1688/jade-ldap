package com.jade.ldap.server;

import com.unboundid.ldap.listener.interceptor.InMemoryInterceptedSimpleBindRequest;
import com.unboundid.ldap.listener.interceptor.InMemoryInterceptedSimpleBindResult;
import com.unboundid.ldap.listener.interceptor.InMemoryOperationInterceptor;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.ResultCode;

final class BindSecurityInterceptor extends InMemoryOperationInterceptor {
    private static final String LOCK_RECORDED = "jade.ldap.lock-recorded";
    private final LdapBindSecurityService security;

    BindSecurityInterceptor(LdapBindSecurityService security) {
        this.security = security;
    }

    @Override
    public void processSimpleBindRequest(InMemoryInterceptedSimpleBindRequest operation) throws LDAPException {
        String bindDn = operation.getRequest().getBindDN();
        if (security.isLocked(bindDn)) {
            security.recordLockedAttempt(bindDn, operation.getConnectedAddress(),
                    ResultCode.INVALID_CREDENTIALS.intValue());
            operation.setProperty(LOCK_RECORDED, Boolean.TRUE);
            throw new LDAPException(ResultCode.INVALID_CREDENTIALS, "Invalid credentials");
        }
    }

    @Override
    public void processSimpleBindResult(InMemoryInterceptedSimpleBindResult operation) {
        if (Boolean.TRUE.equals(operation.getProperty(LOCK_RECORDED))) return;
        ResultCode code = operation.getResult().getResultCode();
        boolean success = ResultCode.SUCCESS.equals(code);
        security.record(operation.getRequest().getBindDN(), operation.getConnectedAddress(), code.intValue(),
                success, success ? "success" : "invalid_credentials");
    }
}
