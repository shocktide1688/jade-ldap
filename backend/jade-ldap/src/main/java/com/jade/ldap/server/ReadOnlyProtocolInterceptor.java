package com.jade.ldap.server;

import com.unboundid.ldap.listener.interceptor.InMemoryInterceptedAddRequest;
import com.unboundid.ldap.listener.interceptor.InMemoryInterceptedDeleteRequest;
import com.unboundid.ldap.listener.interceptor.InMemoryInterceptedModifyDNRequest;
import com.unboundid.ldap.listener.interceptor.InMemoryInterceptedModifyRequest;
import com.unboundid.ldap.listener.interceptor.InMemoryOperationInterceptor;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.ResultCode;

final class ReadOnlyProtocolInterceptor extends InMemoryOperationInterceptor {

    private static LDAPException denied() {
        return new LDAPException(
                ResultCode.INSUFFICIENT_ACCESS_RIGHTS,
                "Directory writes must use the authenticated Jade administration API");
    }

    @Override
    public void processAddRequest(InMemoryInterceptedAddRequest operation) throws LDAPException {
        throw denied();
    }

    @Override
    public void processDeleteRequest(InMemoryInterceptedDeleteRequest operation) throws LDAPException {
        throw denied();
    }

    @Override
    public void processModifyRequest(InMemoryInterceptedModifyRequest operation) throws LDAPException {
        throw denied();
    }

    @Override
    public void processModifyDNRequest(InMemoryInterceptedModifyDNRequest operation) throws LDAPException {
        throw denied();
    }
}
