框架介绍
========
Cartrofit是什么?
---------------
### Cartrofit = CarPropertyService + Retrofit
该框架允许你以注解标记的方式来访问CarPropertyService

Cartrofit有哪些功能？
-------------------

###1. Set Get
使用Set和Get注解来访问CarPropertyService中的属性, 等价于setProperty和getProperty

用法如下所示
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

###2. Track
使用Track注解来订阅CarPropertyService中的CarPropertyValue变化事件

###3. Inject, In, Out
使用Inject注解来与CarService同时交互多组数据, 可以配合上In注解指定该次数据注入方向是从App层到CarService,
也可以配合Out注解指定该次注入方向是从CarService到App, 或者, 可以同时使用InOut注解来同时注入两个方向上的多组数据

###4. Interceptor与Converter
使用Interceptor来来拦截特定的一个或者多个注解接口, 比如可以使用interceptor来检查入参格式, 检查返回结果, 检查回调数据类型,
或者也可以统一的将某些Set操作转移到工作线程, 将订阅回调转移至ui线程

使用Converter来转换特定的数据结构, 很多情况下每个底层信号对应的CarPropertyValue都有不同的数据类型, 比如int, byte, byte数组,
但是每个app在具体的业务场景下关心的数据接口很可能不一样, 比如某个配置按钮的高亮与否只会关心boolean值, 车速现在是多少是个float值,
这种情况下使用Converter来统一的转变数据类型为特定的上层场景服务

###5. Combine
在一些情况下上层app更多需要的是多个信号的组合判断, 比如某个按钮只会在某一个信号为On另一个信号为Off的情况下才会允许用户操作,
这种情况下使用Combine注解来组合多个Get或者Track操作, 需要在每一个Combine命令上使用Converter来组合多个信号的合并结果

###6. Register
更加复杂的订阅业务需要使用Register来自定义接口, 比如某个app业务需要订阅CarService的三个信号, 而且每个信号的订阅事件都对应着不一样的业务逻辑,
当然也可以让应用层去调用三个Track命令, 但是使用Register自定义后的接口可以让代码看起来更加贴合上层开发的编码习惯


