package com.liyangbin.cartrofit;

import com.liyangbin.cartrofit.annotation.Category;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public abstract class CommandBase implements Command, Cloneable {
    Cartrofit.ConverterStore store;
    InterceptorChain chain;
//    DataSource source;
    Class<?> userDataClass;
    String[] category;
    Cartrofit.Key key;

    int id;

    final void init(AbsDataSource.Description description, Cartrofit.Key key) {
//        if (this.source == null && requireSource()) {
//            throw new CartrofitGrammarException("Declare Scope in your api class:" + record.clazz);
//        }
        this.key = key;

        if (getType() != CommandType.INJECT) {
            this.id = key.record.loadId(this);
            Category category = key.getAnnotation(Category.class);
            if (category != null) {
                this.category = category.value();
            }
            this.chain = key.record.getInterceptorByKey(this);
            this.store = key.record.getConverterByKey(this);
            if (key.field != null) {
                key.field.setAccessible(true);
            }
        }
        onInit(description.annotation);
    }

    boolean requireSource() {
        return true;
    }

    void onInit(Annotation annotation) {
    }

    void copyFrom(CommandBase owner) {
//        this.record = owner.record;
//        this.source = owner.source;
        this.key = owner.key;
        this.category = owner.category;
        this.chain = owner.chain;
        this.store = owner.store;
        this.id = owner.id;
    }

    CommandBase shallowCopy() {
        try {
            return (CommandBase) delegateTarget().clone();
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

    CommandBase delegateTarget() {
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
