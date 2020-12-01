package com.liyangbin.cartrofit;

import com.liyangbin.cartrofit.annotation.Category;
import com.liyangbin.cartrofit.annotation.Scope;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public abstract class CommandImpl implements Command, Cloneable {
    int propertyId;
    int area;
    Cartrofit.ApiRecord<?> record;
    Cartrofit.ConverterStore store;
    InterceptorChain chain;
    DataSource source;
    Class<?> userDataClass;
    String[] category;
    Cartrofit.Key key;
    int id;

    final void init(Cartrofit.ApiRecord<?> record, Annotation annotation, Cartrofit.Key key) {
        this.record = record;
        this.source = record.source;
        if (this.source == null && requireSource()) {
            throw new CartrofitGrammarException("Declare Scope in your api class:" + record.clazz);
        }
        this.key = key;

        if (getType() != CommandType.INJECT) {
            this.id = record.loadId(this);
            Category category = key.getAnnotation(Category.class);
            if (category != null) {
                this.category = category.value();
            }
            this.chain = record.getInterceptorByKey(this);
            this.store = record.getConverterByKey(this);
            if (key.field != null) {
                key.field.setAccessible(true);
            }
        }
        onInit(annotation);
    }

    boolean requireSource() {
        return true;
    }

    void onInit(Annotation annotation) {
    }

    void copyFrom(CommandImpl owner) {
        this.record = owner.record;
        this.source = owner.source;
        this.key = owner.key;
        this.propertyId = owner.propertyId;
        this.area = owner.area;
        this.category = owner.category;

        this.chain = owner.chain;
        this.store = owner.store;
        this.id = owner.id;
    }

    final void resolveArea(int userDeclaredArea) {
        if (userDeclaredArea != Scope.DEFAULT_AREA_ID) {
            this.area = userDeclaredArea;
        } else {
            if (this.record.apiArea != Scope.DEFAULT_AREA_ID) {
                this.area = record.apiArea;
            } else {
                this.area = Scope.GLOBAL_AREA_ID;
            }
        }
    }

    CommandImpl shallowCopy() {
        try {
            return (CommandImpl) delegateTarget().clone();
        } catch (CloneNotSupportedException error) {
            throw new RuntimeException(error);
        }
    }

    @Override
    public int getId() {
        return id;
    }

    @Override
    public Method getMethod() {
        return key.method;
    }

    @Override
    public Field getField() {
        return key.field;
    }

    @Override
    public String getName() {
        return delegateTarget().key.getName();
    }

    @Override
    public int getPropertyId() {
        return delegateTarget().propertyId;
    }

    @Override
    public int getArea() {
        return delegateTarget().area;
    }

    @Override
    public String[] getCategory() {
        return category;
    }

    @Override
    public Class<?> getInputType() {
        return null;
    }

    @Override
    public Class<?> getOutputType() {
        return userDataClass;
    }

    @Override
    public final String toString() {
        return "Cmd " + getType() + " 0x" + Integer.toHexString(hashCode())
                + " [" + toCommandString() + "]";
    }

    public void addInterceptor(Interceptor interceptor, boolean toBottom) {
        if (chain != null) {
            if (toBottom) {
                chain.addInterceptorToBottom(interceptor);
            } else {
                chain.addInterceptor(interceptor);
            }
        } else {
            chain = new InterceptorChain(interceptor);
        }
    }

    CommandImpl delegateTarget() {
        return this;
    }

    boolean isReturnFlow() {
        return false;
    }

    void overrideFromDelegate(CommandFlow delegateCommand) {
    }

    String toCommandString() {
        return key.toString();
    }

    final Object invokeWithChain(Object parameter) {
        if (chain != null) {
            return chain.doProcess(this, parameter);
        } else {
            return invoke(parameter);
        }
    }
}
