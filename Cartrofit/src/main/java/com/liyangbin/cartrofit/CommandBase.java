package com.liyangbin.cartrofit;

import com.liyangbin.cartrofit.annotation.CarValue;
import com.liyangbin.cartrofit.annotation.Category;
import com.liyangbin.cartrofit.annotation.Get;
import com.liyangbin.cartrofit.annotation.Scope;
import com.liyangbin.cartrofit.annotation.Set;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;

abstract class CommandImpl implements Command, Cloneable {
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

    void addInterceptor(Interceptor interceptor, boolean toBottom) {
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

interface UnTrackable extends Command {
    void untrack(Object obj);
}

abstract class CommandGroup extends CommandImpl {
    ArrayList<CommandImpl> childrenCommand = new ArrayList<>();

    void addChildCommand(CommandImpl command) {
        childrenCommand.add(command);
    }

    @Override
    boolean requireSource() {
        return false;
    }
}

class CommandSet extends CommandImpl {
    BuildInValue buildInValue;
    Converter<Object, ?> argConverter;

    @Override
    void onInit(Annotation annotation) {
        Set set = (Set) annotation;
        this.propertyId = set.id();
        buildInValue = BuildInValue.build(set.value());
        resolveArea(set.area());

        if (buildInValue != null) {
            return;
        }
        resolveArgConverter(key.getSetClass());
    }

    private void resolveArgConverter(Class<?> userArgClass) {
        if (buildInValue != null) {
            return;
        }
        Class<?> carArgClass;
        try {
            carArgClass = source.extractValueType(propertyId);
        } catch (Exception e) {
            throw new CartrofitGrammarException(e);
        }
        Converter<?, ?> converter = store.find(this, userArgClass, carArgClass);
        userDataClass = userArgClass;
        if (converter != null) {
            argConverter = (Converter<Object, ?>) converter;
        }
    }

    Object collectArgs(Object parameter) {
        if (buildInValue != null) {
            return buildInValue.extractValue(source.extractValueType(propertyId));
        }
        return argConverter != null && parameter != null ?
                argConverter.convert(parameter) : parameter;
    }

    @Override
    public Object invoke(Object parameter) {
        source.set(propertyId, area, collectArgs(parameter));
        return null;
    }

    @Override
    public CommandType getType() {
        return CommandType.SET;
    }

    @Override
    public Class<?> getInputType() {
        return key.getSetClass();
    }

    @Override
    String toCommandString() {
        String stable = "id:0x" + Integer.toHexString(getPropertyId())
                + (area != Scope.GLOBAL_AREA_ID ? " area:0x" + Integer.toHexString(area) : "");
        return stable + super.toCommandString();
    }
}

class CommandGet extends CommandImpl {

    Converter<Object, ?> resultConverter;
    CarType type;

    @Override
    void onInit(Annotation annotation) {
        Get get = (Get) annotation;
        propertyId = get.id();
        type = get.type();
        if (type == CarType.ALL) {
            throw new CartrofitGrammarException("Can not use type ALL mode in Get operation");
        }
        resolveArea(get.area());
        resolveResultConverter(key.getGetClass());
    }

    private void resolveResultConverter(Class<?> userReturnClass) {
        Class<?> carReturnClass;
        try {
            carReturnClass = type == CarType.AVAILABILITY ?
                    boolean.class : source.extractValueType(propertyId);
        } catch (Exception e) {
            throw new CartrofitGrammarException(e);
        }
        Converter<?, ?> converter = store.find(this, carReturnClass, userReturnClass);
        resultConverter = (Converter<Object, ?>) converter;
        userDataClass = userReturnClass;
    }

    @Override
    public Object invoke(Object parameter) {
        Object obj = source.get(propertyId, area, type);
        return resultConverter != null ? resultConverter.convert(obj) : obj;
    }

    @Override
    public CommandType getType() {
        return CommandType.GET;
    }

    @Override
    String toCommandString() {
        String stable = "id:0x" + Integer.toHexString(getPropertyId())
                + (area != Scope.GLOBAL_AREA_ID ? " area:0x" + Integer.toHexString(area) : "");
        if (type != CarType.VALUE) {
            stable += " valueType:" + type;
        }
        return stable + super.toCommandString();
    }
}

class BuildInValue {
    int intValue;
    int[] intArray;

    boolean booleanValue;
    boolean[] booleanArray;

    long longValue;
    long[] longArray;

    byte byteValue;
    byte[] byteArray;

    float floatValue;
    float[] floatArray;

    String stringValue;
    String[] stringArray;

    static BuildInValue build(CarValue value) {
        if (CarValue.EMPTY_VALUE.equals(value.string())) {
            return null;
        }
        BuildInValue result = new BuildInValue();

        result.intValue = value.Int();
        result.intArray = value.IntArray();

        result.booleanValue = value.Boolean();
        result.booleanArray = value.BooleanArray();

        result.byteValue = value.Byte();
        result.byteArray = value.ByteArray();

        result.floatValue = value.Float();
        result.floatArray = value.FloatArray();

        result.longValue = value.Long();
        result.longArray = value.LongArray();

        result.stringValue = value.string();
        result.stringArray = value.stringArray();

        return result;
    }

    Object extractValue(Class<?> clazz) {
        if (String.class == clazz) {
            return stringValue;
        } else if (String[].class == clazz) {
            return stringArray;
        }
        else if (int.class == clazz) {
            return intValue;
        } else if (int[].class == clazz) {
            return intArray;
        }
        else if (byte.class == clazz) {
            return byteValue;
        } else if (byte[].class == clazz) {
            return byteArray;
        }
        else if (float.class == clazz) {
            return floatValue;
        } else if (float[].class == clazz) {
            return floatArray;
        }
        else if (long.class == clazz) {
            return longValue;
        } else if (long[].class == clazz) {
            return longArray;
        }
        else {
            throw new CartrofitGrammarException("invalid type:" + clazz);
        }
    }
}