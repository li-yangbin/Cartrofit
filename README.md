[![](https://jitpack.io/v/com.gitee.li-yangbin/cartrofit.svg)](https://jitpack.io/#com.gitee.li-yangbin/cartrofit)

Cartrofit是什么?
===============
### Cartrofit是一套可以由App自行扩展的安卓运行时注解解释框架，该框架通过读取App自定义的注解接口来执行模板代码（类似Retrofit）
目前该框架只支持访问CarPropertyService与安卓原生广播，但是App也可以通过扩展核心库来自定义其他类型的功能。
一般来说适合被扩展的功能应当满足这一特性：__即该功能通过调用有限的接口来访问无限的资源。__

### 目录引用说明

首先需要在根目录下声明jitpack库
```groovy
    allprojects {
        repositories {
            google()
            jcenter()
            maven { url 'https://jitpack.io' }
        }
    }
```

core 框架所在主目录, 应该作为依赖被使用方模块在build.gradle中导入
```groovy
    implementation 'com.gitee.li-yangbin.cartrofit:core:latest-version'
```

carproperty 用来访问CarProperty的扩展库, 如果需要使用该功能的话应该作为依赖被使用方模块在build.gradle中导入
```groovy
    implementation 'com.gitee.li-yangbin.cartrofit:carproperty:latest-version'
```

broadcast 用来发送或者收取安卓广播的扩展库, 如果需要使用该功能的话应该作为依赖被使用方模块在build.gradle中导入
```groovy
    implementation 'com.gitee.li-yangbin.cartrofit:broadcast:latest-version'
```

processorLib 框架部分功能所依赖的注解处理器, 同样应该作为依赖被使用方模块在build.gradle中导入
```groovy
    annotationProcessor 'com.gitee.li-yangbin.cartrofit:processorlib:latest-version'
```

hvacDemo 由原生android-automotive的空调App修改而来, 做测试和demo展示的作用, 仅供参考

CarProperty接口声明方式
=====================
先声明业务接口(以访问CarHvacManager作为例子)[查看代码](https://gitee.com/li-yangbin/cartrofit/blob/master/hvacDemo/src/main/java/com/android/car/hvac/api/HvacPanelApi.java)
```java
    @CarPropertyScope(Car.HVAC_SERVICE)
    public interface HvacPanelApi {
        /**
        * 接口声明
        */
    }
```
使用前在Application先初始化Cartrofit，如下所示
```java
    Cartrofit.register(new HvacContext(this));
```
其中HvacContext是框架自带的实现，支持Android-automotive空调访问调用，App需要结合自身情况考虑做扩展实现

然后在使用的地方使用java动态代理来获取接口实现，如下所示
```java
    HvacPanelApi hvacApi = Cartrofit.from(HvacPanelApi.class);
```

### 1. Set Get
使用Set和Get注解来访问CarHvacManager中的属性, 等价于setProperty和getProperty

如下所示
```java
    @Set(propId = CarHvacManager.ID_ZONED_TEMP_SETPOINT, area = DRIVER_ZONE_ID)
    void setDriverTemperature(int temperature);

    @Get(propId = CarHvacManager.ID_ZONED_TEMP_SETPOINT, area = DRIVER_ZONE_ID)
    int getDriverTemperature();
```
上述两个接口分别等效于调用
```java
   CarHvacManager.setIntProperty(CarHvacManager.ID_ZONED_TEMP_SETPOINT, DRIVER_ZONE_ID, temperature)
   CarHvacManager.getIntProperty(CarHvacManager.ID_ZONED_TEMP_SETPOINT, DRIVER_ZONE_ID);
```

### 2. Track
使用Track注解来订阅CarHvacManager中的CarPropertyValue变化事件
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

### 3. Register
使用Register注解可以自动的将消息源数据（Observable，Flow，LiveData）转化为回调接口的注册

如下所示
```java
    @Register
    void registerWarmChangeCallback(OnWarmLevelChangeCallback callback);

    interface OnWarmLevelChangeCallback {
        @Track(propId = CarHvacManager.ID_ZONED_SEAT_TEMP, area = HvacPanelApi.DRIVER_ZONE_ID)
        void onDriverLevelChange(int level);

        @Track(propId = CarHvacManager.ID_ZONED_SEAT_TEMP, area = HvacPanelApi.PASSENGER_ZONE_ID)
        void onPassengerLevelChange(int level);
    }
```
registerWarmChangeCallback代表向CarService注册座椅加热档位变化回调，回调类型OnWarmLevelChangeCallback由使用方自定义，
其中onDriverLevelChange代表主驾侧温度变化档位值回调， onPassengerLevelChange代表副驾侧温度变化档位值回调

当只注册单个property变化回调时，可以省略Register注解，代码如下所示
```java
    @Track(propId = CarHvacManager.ID_ZONED_FAN_DIRECTION, area = HvacPanelApi.SEAT_ALL)
    void trackFanDirection(OnDanDirectionChangCallback callback);

    interface OnDanDirectionChangCallback {
        void onChange(int state);
    }
```

### 4. Unregister
使用Unregister注解可以反注册因为调用了Register接口从而保存在框架之内的Callback对象，代码如下所示
```java
    @Track(propId = CarHvacManager.ID_ZONED_FAN_SPEED_SETPOINT, area = HvacPanelApi.SEAT_ALL)
    void registerFanSpeedChangeCallback(FanSpeedBarController.OnFanSpeedChangeCallback callback);

    @Unregister(FanSpeedApiId.registerFanSpeedChangeCallback)
    void unregisterFanSpeedChangeCallback(FanSpeedBarController.OnFanSpeedChangeCallback callback);
```
registerFanSpeedChangeCallback为注册空调风速变化接口，unregisterFanSpeedChangeCallback为反注册前者的接口
其中FanSpeedApiId.registerFanSpeedChangeCallback这个Id来自于框架自带的注解处理器processorLib，需要将其在
build.gradle中导入，并且在对应的业务接口开头声明GenerateId注解，然后手动触发一次build方可生成

### 5. Delegate
代理注解，该注解允许调用方直接封装另外模块中的已经定义好的业务接口，使得使用方可以面向接口定义接口，
该功能需要被调用方在定义已有业务接口的同时声明GenerateId注解，并且在build.gradle中声明注解处理器模块processorLib

如下所示为面向实现的接口声明方
```java
    @GenerateId
    @Scope(value = Car.HVAC_SERVICE)
    public interface HvacPanelApi {
        @Get(propId = CarHvacManager.ID_ZONED_SEAT_TEMP, area = DRIVER_ZONE_ID)
        float getDriverSeatWarmerLevel();
    
        @Get(propId = CarHvacManager.ID_ZONED_SEAT_TEMP, area = PASSENGER_ZONE_ID)
        float getPassengerSeatWarmerLevel();
    }
```
下面是引用方
```java
    public interface SeatWarmerApi {
        @Delegate(HvacPanelApiId.getDriverSeatWarmerLevel)
        int getDriverSeatWarmerLevel();
    
        @Delegate(HvacPanelApiId.getPassengerSeatWarmerLevel)
        int getPassengerSeatWarmerLevel();
    }
```
这个例子中Delegate可以帮助引用方使用现成的获取主副驾侧座椅加热档位值接口，从而免去了自己重复定义Get注解的工作，
当主副驾侧座椅加热对应的底层信号发生变化时，只需要改变HvacPanelApi这个类即可，SeatWarmerApi这个类不需要修改

相关模块功能说明
=============

### 1. CarAbstractContext
所有CarProperty相关功能的访问上下文根类，每一个子模块都需要依赖一个特定的上下文，
目前框架默认提供的实现包括HvacContext(对应CarHvacManager), CarSensorContext(对应CarSensorManager),
 CabinContext(对应CarCabinManager)， PropertyContext(对应CarPropertyManager) 以及
 VendorExtensionContext(对应CarVendorExtensionManager)， App需要做的是在对应的应用程序初始化之时，创建
 对应的Context并且将其加入到默认上下文环境中 [查看初始化代码](https://gitee.com/li-yangbin/cartrofit/blob/master/hvacDemo/src/main/java/com/android/car/hvac/HvacApplication.java)

### 2. 防抖功能
除了CarSensorContext之外的其他Context实现了Property的防抖功能(set对应property值之后主动忽略来自Can网络的数据帧跳动)并且默认已经打开，
App需要调用setDebounceMillis方法根据实际情况来调节防抖时间阈值，代码如下所示
```java
    HvacContext hvacContext = new HvacContext(this);
    hvacContext.setDebounceMillis(1500);
```

### 3. 超时恢复功能
除了CarSensorContext之外的其他Context实现了Property的超时恢复功能并且默认关闭，App需要调用setTimeoutMillis方法根据实际情况来调节防抖时间阈值，
然后在给UI侧使用的监听代码打开对应的restoreIfTimeout开关，代码如下所示
```java
    HvacContext hvacContext = new HvacContext(this);
    hvacContext.setTimeOutMillis(1500);

    public interface HvacPanelApi {
    
        // 绑定此LiveData的UI控件会在收不到任何反馈的Can数据之时自动恢复原状
        @Track(propId = CarHvacManager.ID_ZONED_AIR_RECIRCULATION_ON, area = SEAT_ALL, restoreIfTimeout = true)
        LiveData<Boolean> trackAirCirculation();
    }
```

### 4. 粘滞数据派发
所有的CarAbstractContext都支持粘滞数据派发(缓存当前property值并且在新的监听接口注册之时主动派发该值)，该功能默认关闭，
App需要调用如下代码来打开该功能
```java
    HvacContext hvacContext = new HvacContext(this);
    hvacContext.setStickySupport(true);
```

### 5. 异常处理（以CarNotConnectedException为例）
所有的CartrofitContext会默认安静处理非运行时异常(运行时异常RuntimeException框架不做任何处理)，
App可以根据具体的接口场景来决定是否要自行处理异常，具体代码如下所示
```java
    public interface HvacPanelApi {
        @Set(propId = CarHvacManager.ID_ZONED_HVAC_POWER_ON, area = SEAT_ALL)
        void setHvacPowerState(boolean onOff) throws CarNotConnectedException;
    }
```
若调用该接口发生CarNotConnectedException，框架检测到接口有声明该种类型异常就不会自行处理，转而会抛出给App处理
这种情况就需要app在调用这个接口的时候自行使用try-catch语法来处理对应异常.

Broadcast接口声明方式
===================

先声明业务接口[查看代码](https://gitee.com/li-yangbin/cartrofit/blob/master/hvacDemo/src/main/java/com/android/car/hvac/TimeTickerApi.java)
```java
    @Broadcast
    public interface TimeTickerApi {
        /**
        * 接口声明
        */
    }

    // 从默认的上下文环境中获得TimeTickerApi动态代理实例
    TimeTickerApi api = Cartrofit.from(TimeTickerApi.class);
```
Broadcast注解支持普通广播与本地广播，使用本地广播需要将
>androidx.localbroadcastmanager:localbroadcastmanager:1.0.0

添加进app的build.gradle之中，并且按照如下方式声明接口
```java
    @Broadcast(isLocal = true)
    public interface TestLocalBroadcast {
        /**
         * 接口声明
         */
    }
```

### 1. Send
使用Send注解发送广播，代码如下所示
```java
    @Broadcast(isLocal = true)
    @GenerateId
    public interface TestLocalBroadcast {
    
        @Send(action = "action.test")
        void sendTestAction();
    }
```
调用接口sendTestAction()等价于调用如下java代码
```java
    Intent intent = new Intent("action.test");
    LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
```
除了设置Action之外，Send语法同时支持设置例如packageName, className等属性[查看代码](https://gitee.com/li-yangbin/cartrofit/blob/master/broadcast/src/main/java/com/liyangbin/cartrofit/broadcast/Send.java)

### 2.Receive
使用Receive注解来监听广播，代码如下所示
```java
    @Broadcast
    public interface TimeTickerApi {
        @Receive(action = Intent.ACTION_TIME_TICK)
        void registerTimeTickListener(Runnable action);
    }
```
调用接口registerTimeTickListener(Runnable)等价于调用如下java代码
```java
    context.registerReceiver(new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            
        }
    }, new IntentFilter(Intent.ACTION_TIME_TICK));
```
除了设置action之外，Receive同样支持其他属性设置[查看代码](https://gitee.com/li-yangbin/cartrofit/blob/master/broadcast/src/main/java/com/liyangbin/cartrofit/broadcast/Receive.java)

### 3. 配合Register注解使用Receive
配合Register注解使用Receive同时监听多种广播，代码如下所示
```java
    @Broadcast
    public interface TimeTickerApi {
        @Register
        void registerTimeChangeListener(TimeChangeListener listener);
    }

    interface TimeChangeListener {
        @Receive(action = Intent.ACTION_TIME_TICK)
        void onTimeTick();
    
        @Receive(action = Intent.ACTION_TIMEZONE_CHANGED)
        void onTimeZoneChange(@Extra(key = "time-zone") String zoneId);
    }
```
调用接口registerTimeTickListener(Runnable)等价于调用如下java代码
```java
    IntentFilter filter = new IntentFilter();
    filter.addAction(Intent.ACTION_TIME_TICK);
    filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
    context.registerReceiver(new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (Intent.ACTION_TIME_TICK.equals(action)) {
                // onTimeTick 时间变化
            } else if (Intent.ACTION_TIMEZONE_CHANGED.equals(action)) {
                String zoneId = intent.getStringExtra("time-zone");
                // onTimeZoneChange 时区变化
            }
        }
    }, filter);
```
注意其中Extra注解的使用，在回调接口中声明Extra注解相当于在原生的onReceive()中访问intent对应的extra键值对

### 3. 使用Unregister来进行广播解注册
代码如下所示
```java
    @Broadcast
    @GenerateId
    public interface TimeTickerApi {
        @Register
        void registerTimeChangeListener(TimeChangeListener listener);
    
        @Unregister(TimeTickerApiId.registerTimeChangeListener)
        void unregisterTimeChangeListener(TimeChangeListener listener);
    }
```
App需要将processorLib在
build.gradle中导入，并且在对应的业务接口开头声明GenerateId注解，然后手动触发一次build方可生成，
当这个TimeTickerApi之中所有的TimeChangeListener都被解注册之后，框架会为其解注册内部的广播接收器(BroadcastReceiver)
，所以App需要根据特定情况来解注册来防止内存泄露