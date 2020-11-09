Cartrofit是什么?
===============
### Cartrofit = CarPropertyService + Retrofit
该框架允许你以类似Retrofit注解标记的方式来访问CarPropertyService

先声明业务接口
```java
@Scope(value = Car.HVAC_SERVICE)
public interface HvacPanelApi {
    /**
    * 接口声明
    */
}
```
使用前先定义Cartrofit数据源，如下所示
```java
    Cartrofit.builder()
         .addDataSource(new HvacDataSource(this))
         .addInterceptor(new CartrofitLogger())
         .buildAsDefault();
```
然后在使用的地方使用java动态代理来获取接口实现，如下所示
```java
    HvacPanelApi hvacApi = Cartrofit.from(HvacPanelApi.class);
```

Cartrofit的接口声明方式
====================

### 1. Set Get
使用Set和Get注解来访问CarPropertyService中的属性, 等价于setProperty和getProperty

如下所示
```java
    @Set(id = CarHvacManager.ID_ZONED_TEMP_SETPOINT, area = DRIVER_ZONE_ID)
    void setDriverTemperature(int temperature);

    @Get(id = CarHvacManager.ID_ZONED_TEMP_SETPOINT, area = DRIVER_ZONE_ID)
    int getDriverTemperature();
```
上述两个接口分别等效于调用
```java
   CarHvacManager.setIntProperty(CarHvacManager.ID_ZONED_TEMP_SETPOINT, DRIVER_ZONE_ID, temperature)
   CarHvacManager.getIntProperty(CarHvacManager.ID_ZONED_TEMP_SETPOINT, DRIVER_ZONE_ID);
```

### 2. Track
使用Track注解来订阅CarPropertyService中的CarPropertyValue变化事件
如下所示
```java
    @Track(id = CarHvacManager.ID_ZONED_TEMP_SETPOINT, area = HvacPanelApi.DRIVER_ZONE_ID)
    Flow<Integer> trackDriverTemperature();
```
Flow是框架内置的被观察数据源的默认类型，除了该类型之外， Cartrofit默认支持RxJava，DataBinding，LiveData三种数据源
类型（需要在使用方模块的build.gradle添加对应配置），如下所示
```java
    @Track(id = CarHvacManager.ID_ZONED_TEMP_SETPOINT, area = HvacPanelApi.PASSENGER_ZONE_ID)
    Observable<Integer> trackPassengerTemperature();

    @Track(id = CarHvacManager.ID_ZONED_AC_ON, area = SEAT_ALL, sticky = StickyType.ON)
    ObservableBoolean trackACState();

    @Track(id = CarHvacManager.ID_WINDOW_DEFROSTER_ON, area = VehicleAreaWindow.WINDOW_REAR_WINDSHIELD, sticky = StickyType.ON)
    LiveData<Boolean> trackRearDefrosterState();
```

### 3. Inject, In, Out
使用Inject注解来与CarService同时交互多组数据, 可以配合上In注解指定该次数据注入方向是从App层到CarService,
也可以配合Out注解指定该次注入方向是从CarService到App, 或者, 可以同时使用InOut注解来同时注入两个方向上的多组数据

如下所示
```java
    @Inject
    void getSeatWarmLevel(@Out WarmInfo info);
```
WarmInfo由使用方自行定义， 如下所示
```java
    class WarmInfo {
        @Category(FLOAT_TO_INT)
        @Delegate(HvacPanelApiId.getDriverSeatWarmerLevel)
        public int driverLevel;

        @Category(FLOAT_TO_INT)
        @Delegate(HvacPanelApiId.getPassengerSeatWarmerLevel)
        public int passengerLevel;
    }
```
该例子相当于同时从CarService获取主驾驶温度和副驾驶温度两个数据并且自动为使用方包装成了WarmInfo这个类型

### 4. Interceptor与Converter
使用Interceptor来来拦截特定的一个或者多个注解接口, 比如可以使用interceptor来检查入参格式, 检查返回结果, 检查回调数据类型,
或者也可以统一的将某些Set操作转移到工作线程, 将订阅回调转移至ui线程

如下所示
```java
    Cartrofit.builder().
    addApiCallback(new ApiCallback() {
          @Override
          public void onApiCreate(Class<?> apiClass, ApiBuilder builder) {
               builder.intercept(new SetCommandDispatcher())
                     .apply(Constraint.of(CommandType.SET));

               builder.intercept(new ReceiveCommandDispatcher())
                     .apply(Constraint.of(CommandType.RECEIVE));
          }
    })

    private static class SetCommandDispatcher implements Interceptor {
        @Override
        public Object process(Command command, Object parameter) {
            AsyncTask.execute(() -> {
                Log.i("cartrofit", "Async execute->" + command + " parameter:" + parameter);
                command.invoke(parameter);
            });
            return null;
        }
    }
```

使用Converter来转换特定的数据结构, 很多情况下每个底层信号对应的CarPropertyValue都有不同的数据类型, 比如int, byte, byte数组,
但是每个app在具体的业务场景下关心的数据接口很可能不一样, 比如某个配置按钮的高亮与否只会关心boolean值, 车速现在是多少是个float值,
这种情况下使用Converter来统一的转变数据类型为特定的上层场景服务

如下所示
```java
    class SeatWarmerApiCreateHelper implements ApiCallback {
        @Override
        public void onApiCreate(Class<?> apiClass, ApiBuilder builder) {
            builder.convert(float.class)
                    .to(int.class)
                    .by(Float::intValue)
                    .apply(Constraint.of(SeatWarmerApi.FLOAT_TO_INT));
        }
    }
```

### 5. Combine
在一些情况下上层app更多需要的是多个信号的组合判断, 比如某个按钮只会在某一个信号为On另一个信号为Off的情况下才会允许用户操作,
这种情况下使用Combine注解来组合多个Get或者Track操作, 需要在每一个Combine命令上使用Converter来组合多个信号的合并结果

如下所示
```java
    @Combine(elements = {TemperatureApiId.trackDriverTemperature, TemperatureApiId.trackPassengerTemperature})
    Observable<TempInfo> trackTempChange();

    class TempInfo {
        public int driverTemp;
        public int passengerTemp;

        public TempInfo(int driverTemp, int passengerTemp) {
            this.driverTemp = driverTemp;
            this.passengerTemp = passengerTemp;
        }
    }

    class CreateHelper implements ApiCallback {
        @Override
        public void onApiCreate(Class<?> apiClass, ApiBuilder builder) {
            builder.combine(int.class, int.class)
                    .to(TemperatureApi.TempInfo.class)
                    .by(TemperatureApi.TempInfo::new)
                    .apply(trackTempChange);
        }
    }
```
该例子中同时监听主驾侧温度和副驾侧温度，当有任何一侧温度发生变化时候，同时将两个数据拼装成TempInfo类型回调给使用方

### 6. Register
更加复杂的订阅业务需要使用Register来自定义接口, 比如某个app业务需要订阅CarService的三个信号, 而且每个信号的订阅事件都对应着不一样的业务逻辑,
当然也可以让应用层去调用三个Track命令, 但是使用Register自定义后的接口可以让代码看起来更加贴合上层开发的编码习惯

如下所示
```java
    @Register
    void registerWarmChangeCallback(OnWarmLevelChangeCallback callback);

    @UnTrack(SeatWarmerApiId.registerWarmChangeCallback)
    void unregisterWarmChangeCallback(OnWarmLevelChangeCallback callback);

    interface OnWarmLevelChangeCallback {
        @Category(FLOAT_TO_INT)
        @Track(id = CarHvacManager.ID_ZONED_SEAT_TEMP, area = HvacPanelApi.DRIVER_ZONE_ID, sticky = StickyType.ON)
        void onDriverLevelChange(int level);

        @Category(FLOAT_TO_INT)
        @Track(id = CarHvacManager.ID_ZONED_SEAT_TEMP, area = HvacPanelApi.PASSENGER_ZONE_ID, sticky = StickyType.ON)
        void onPassengerLevelChange(int level);
    }
```
registerWarmChangeCallback代表向CarService注册座椅加热档位变化回调，回调类型OnWarmLevelChangeCallback由使用方自定义，
其中onDriverLevelChange代表主驾侧温度变化档位值回调， onPassengerLevelChange代表副驾侧温度变化档位值回调

### 7. Delegate
代理注解，该注解允许调用方直接封装另外模块中的已经定义好的业务接口，使得使用方可以面向接口定义接口，
该功能需要被调用方在定义已有业务接口的同时声明GenerateId注解，并且在build.gradle中声明注解处理器模块processorLib

如下所示为面向实现的接口声明方
```java
@GenerateId
@Scope(value = Car.HVAC_SERVICE)
public interface HvacPanelApi {

    @Get(id = CarHvacManager.ID_ZONED_SEAT_TEMP, area = DRIVER_ZONE_ID)
    float getDriverSeatWarmerLevel();

    @Get(id = CarHvacManager.ID_ZONED_SEAT_TEMP, area = PASSENGER_ZONE_ID)
    float getPassengerSeatWarmerLevel();
}
```
下面是引用方
```java
    class WarmInfo {
        @Category(FLOAT_TO_INT)
        @Delegate(HvacPanelApiId.getDriverSeatWarmerLevel)
        public int driverLevel;

        @Category(FLOAT_TO_INT)
        @Delegate(HvacPanelApiId.getPassengerSeatWarmerLevel)
        public int passengerLevel;
    }
```
这个例子中Delegate可以帮助引用方使用现成的获取主副驾侧座椅加热档位值接口，从而免去了自己重复定义Get注解的工作，
当主副驾侧座椅加热对应的底层信号发生变化时，只需要改变HvacPanelApi这个类即可，WarmInfo这个类不需要修改

Cartrofit的其他辅助功能
=======================

### 1. Scope与DataSource
Scope定义作用域，指定这个接口是用来作用于CarService中的哪一个Manager，比如CarHvacManager

如下所示
```java
@GenerateId
@Scope(value = Car.HVAC_SERVICE, onCreate = SeatWarmerApiCreateHelper.class)
public interface SeatWarmerApi {

    /*
    * 一旦在最外层接口类中定义了Scope注解， 那么定义在该接口内部的其他类则默认享有同样的Scope
    * OnWarmLevelChangeCallback类的Scope与SeatWarmerApi保持一致
    */
    interface OnWarmLevelChangeCallback {
        @Category(FLOAT_TO_INT)
        @Track(id = CarHvacManager.ID_ZONED_SEAT_TEMP, area = HvacPanelApi.DRIVER_ZONE_ID, sticky = StickyType.ON)
        void onDriverLevelChange(int level);

        @Category(FLOAT_TO_INT)
        @Track(id = CarHvacManager.ID_ZONED_SEAT_TEMP, area = HvacPanelApi.PASSENGER_ZONE_ID, sticky = StickyType.ON)
        void onPassengerLevelChange(int level);
    }
}
```
DataSource用来向这个Scope提供真实的数据源

如下所示
```java
@Scope(Car.HVAC_SERVICE)
public class HvacDataSource implements DataSource {
}
```

### 2. ApiBuilder与Constraint
这两个类帮助你扩展定义已有的业务接口，可以在Scope的onCreate属性中声明一个ApiBuilder

如下所示
```java
@GenerateId
@Scope(value = Car.HVAC_SERVICE, onCreate = CreateHelper.class)
public interface TemperatureApi {
}
class CreateHelper implements ApiCallback {
    @Override
    public void onApiCreate(Class<?> apiClass, ApiBuilder builder) {
        builder.combine(int.class, int.class)
                .to(TemperatureApi.TempInfo.class)
                .by(TemperatureApi.TempInfo::new)
                .apply(trackTempChange);

        /*
         *ApiBuilder为TemperatureApi增加了一个双向Converter
         *该Converter支持float与int两种数据类型的互相转化
         */
        builder.convert(Float.class)
                .to(int.class)
                .by(new TwoWayConverter<Float, Integer>() {
                    @Override
                    public Integer fromCar2App(Float value) {
                        return value.intValue();
                    }

                    @Override
                    public Float fromApp2Car(Integer integer) {
                        return integer.floatValue();
                    }
                })
                /*
                * Constraint声明该Converter应用于下列接口
                */
                .apply(getDriverTemperature, getPassengerTemperature,
                        setPassengerTemperature, setDriverTemperature,
                        trackDriverTemperature, trackPassengerTemperature);
    }
}
```

### 3. Category
使用了Category注解的接口可以被ApiBuilder统一作用

如下所示
```java

    @Category(INT_TO_FLOAT)
    @Delegate(HvacPanelApiId.setPassengerSeatWarmerLevel)
    void setPassengerSeatWarmerLevel(int level);

class SeatWarmerApiCreateHelper implements ApiCallback {
    @Override
    public void onApiCreate(Class<?> apiClass, ApiBuilder builder) {
        builder.convert(int.class)
                .to(float.class)
                .by(Integer::floatValue)
                .apply(Constraint.of(SeatWarmerApi.INT_TO_FLOAT));
    }
}
```

### 4. CarValue
待补充

### 5. CarType
使用CarType来声明你想要的数据是哪一种

如下所示
```java
    @Get(id = CarHvacManager.ID_ZONED_TEMP_SETPOINT, area = HvacPanelApi.DRIVER_ZONE_ID, type = CarType.AVAILABILITY)
    boolean isDriverTemperatureControlAvailable();
```
该接口等效与以下调用
```java
    public boolean isTemperatureControlAvailable(int zone) {
        if (mHvacManager != null) {
            try {
                return mHvacManager.isPropertyAvailable(
                        CarHvacManager.ID_ZONED_TEMP_SETPOINT, zone);
            } catch (android.car.CarNotConnectedException e) {
                Log.e(TAG, "Car not connected in isTemperatureControlAvailable");
            }
        }

        return false;
    }
```

### 6. Track超时
待补充

### 7. InjectReceiver
待补充

### 8. 在Register注解中使用返回值
待补充

### 9. UnTrack
对应着Track或者Register注解，帮助调用方解除注册
```java
    @UnTrack(SeatWarmerApiId.registerWarmChangeCallback)
    void unregisterWarmChangeCallback(OnWarmLevelChangeCallback callback);
```



