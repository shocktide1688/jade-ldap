package com.jade.ldap.server;

import com.unboundid.ldap.listener.interceptor.InMemoryInterceptedAddResult;
import com.unboundid.ldap.listener.interceptor.InMemoryInterceptedDeleteResult;
import com.unboundid.ldap.listener.interceptor.InMemoryInterceptedModifyDNResult;
import com.unboundid.ldap.listener.interceptor.InMemoryInterceptedModifyResult;
import com.unboundid.ldap.listener.interceptor.InMemoryOperationInterceptor;
import com.unboundid.ldap.sdk.ResultCode;

final class DirectoryPersistenceInterceptor extends InMemoryOperationInterceptor {

    private final Runnable persistRequest;

    DirectoryPersistenceInterceptor(Runnable persistRequest) {
        this.persistRequest = persistRequest;
    }

    @Override
    public void processAddResult(InMemoryInterceptedAddResult operation) {
        persistOnSuccess(operation.getResult().getResultCode());
    }

    @Override
    public void processDeleteResult(InMemoryInterceptedDeleteResult operation) {
        persistOnSuccess(operation.getResult().getResultCode());
    }

    @Override
    public void processModifyResult(InMemoryInterceptedModifyResult operation) {
        persistOnSuccess(operation.getResult().getResultCode());
    }

    @Override
    public void processModifyDNResult(InMemoryInterceptedModifyDNResult operation) {
        persistOnSuccess(operation.getResult().getResultCode());
    }

    private void persistOnSuccess(ResultCode resultCode) {
        if (ResultCode.SUCCESS.equals(resultCode)) {
            persistRequest.run();
        }
    }
}
