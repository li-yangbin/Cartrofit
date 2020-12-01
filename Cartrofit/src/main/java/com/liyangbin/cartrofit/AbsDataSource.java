package com.liyangbin.cartrofit;

public abstract class AbsDataSource {

    public abstract ScopeInfo getScopeInfo(Class<?> scopeClass);

    public abstract Command onCreateCommand(Cartrofit.Key key);

    protected final Command inflateCommand(int commandId) {
        Cartrofit.getDefault().getOrCreateCommandById()
    }

    private class InflateSession {
        Cartrofit.ApiRecord<?> record;
    }

    public abstract static class ScopeInfo {
    }
}
