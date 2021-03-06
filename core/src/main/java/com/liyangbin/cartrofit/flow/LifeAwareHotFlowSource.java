package com.liyangbin.cartrofit.flow;

import java.util.ArrayList;
import java.util.Objects;

public abstract class LifeAwareHotFlowSource<T> implements FlowSource<T> {
    private ArrayList<Flow.Injector<T>> safeInjectors;
    private boolean isActive;

    @Override
    public void startWithInjector(Flow.Injector<T> injector) {
        boolean activeAware;
        synchronized (this) {
            if (safeInjectors == null) {
                safeInjectors = new ArrayList<>();
            } else {
                safeInjectors = new ArrayList<>(safeInjectors);
            }
            activeAware = safeInjectors.size() == 0;
            if (activeAware) {
                isActive = true;
            }
            safeInjectors.add(Objects.requireNonNull(injector));
        }
        if (activeAware) {
            onActive();
        }
    }

    public final int getSubscriberCount() {
        return safeInjectors.size();
    }

    public final boolean isActive() {
        return isActive;
    }

    public void onActive() {
    }

    @Override
    public void finishWithInjector(Flow.Injector<T> injector) {
        boolean inactiveAware = false;
        synchronized (this) {
            if (safeInjectors == null) {
                return;
            }
            safeInjectors = new ArrayList<>(safeInjectors);
            if (safeInjectors.remove(injector)) {
                inactiveAware = safeInjectors.size() == 0;
            }
            if (inactiveAware) {
                isActive = false;
            }
        }
        if (inactiveAware) {
            onInactive();
        }
    }

    public void onInactive() {
    }

    public void publish(T value) {
        ArrayList<Flow.Injector<T>> injectors;
        synchronized (this) {
            if (safeInjectors == null) {
                return;
            }
            injectors = safeInjectors;
        }
        for (int i = 0; i < injectors.size(); i++) {
            injectors.get(i).send(value);
        }
    }

    public void publishError(Throwable error) {
        ArrayList<Flow.Injector<T>> injectors;
        synchronized (this) {
            if (safeInjectors == null) {
                return;
            }
            injectors = safeInjectors;
        }
        for (int i = 0; i < injectors.size(); i++) {
            injectors.get(i).error(error);
        }
    }

    @Override
    public final boolean isHot() {
        return true;
    }
}
