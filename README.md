# MyRPCFromZero

从零开始，手写一个RPC，跟随着这篇文档以及数个迭代版本的代码，由简陋到逐渐完备，让所有人都能看懂并且写出一个RPC框架。

本文档与代码都是本人第一次手写RPC的心路历程，会有理解的偏差与代码上的不完善，但更是由于这样，有着与新手对同样问题的疑惑，也许会使新手更容易理解这样做的缘故是啥。 

另外期待与你的**合作**：代码，帮助文档甚至rpc框架功能的完备

**学习建议**：

- 一定要实际上手敲代码
- 每一版本都有着对应独立的代码与文档，结合来看
- 每一版本前有一个背景知识，建议先掌握其相关概念再上手代码
- 每一个版本都有着要解决的问题与此版本的最大痛点，带着问题去写代码，并且与上个版本的代码进行比较差异



## RPC的概念

#### 背景知识

- RPC的基本概念，核心功能

![image-20200805001037799](http://ganghuan.oss-cn-shenzhen.aliyuncs.com/img/image-20200805124759206.png)

常见的RPC框架

#### Duboo基本功能

1. **远程通讯**
2. 基于接口方法的透明远程过程调用
3. 负载均衡
4. 服务注册中心

#### RPC过程

client 调用远程方法-> request序列化 -> 协议编码 -> 网络传输-> 服务端 -> 反序列化request -> 调用本地方法得到response -> 序列化 ->编码->…..



------



## 版本迭代过程

### 目录

从0开始的RPC的迭代过程：

- [version0版本](#0.一个最简单的RPC调用)：以不到百行的代码完成一个RPC例子
- [version1版本](#1.MyRPC版本1)：完善通用消息格式（request，response），客户端的动态代理完成对request消息格式的封装
- [version2版本](#2.MyRPC版本2)：支持服务端暴露多个服务接口， 服务端程序抽象化，规范化
- [version3版本](#3.MyRPC版本3)：使用高性能网络框架netty的实现网络通信，以及客户端代码的重构
- [version4版本](#4.MyRPC版本4)：自定义消息格式，支持多种序列化方式（java原生， json…）
- [version5版本](#5.MyRPC版本5):   服务器注册与发现的实现，zookeeper作为注册中心
- [version6版本](#MyRPC版本6):   负载均衡的策略的实现
- [version7版本](#7.MyRPC版本7):   客户端缓存服务地址列表, zookeeper监听服务提供者状态，更新客户端缓存**（待实现）**
- [version8版本](#8.MyRPC版本8)： 跨语言的RPC通信（protobuf）**（待实现）**



------



### 0.一个最简单的RPC调用

#### **背景知识**

- java基础
- java socket编程入门
- 项目使用maven搭建，暂时只引入了lombok包

#### 本节问题

- **什么是RPC，怎么完成一个RPC?**

一个RPC**最最最简单**的过程是客户端**调用**服务端的的一个方法, 服务端返回执行方法的返回值给客服端。接下来我会以一个从数据库里取数据的例子来进行一次模拟RPC过程的一个完整流程。

**假定**有以下这样一个服务：

服务端：

1. 有一个User表

 	2. UserServiceImpl 实现了UserService接口
 	3. UserService里暂时只有一个功能: getUserByUserId(Integer id)

客户端：

​	 传一个Id给服务端，服务端查询到User对象返回给客户端

#### 过程

1. 首先我们得有User对象，这是客户端与服务端都已知的，客户端需要得到这个pojo对象数据，服务端需要操作这个对象

```java
@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class User implements Serializable {
    // 客户端和服务端共有的
    private Integer id;
    private String userName;
    private Boolean sex;
}
```

2. 定义客户端需要调用，服务端需要提供的服务接口

```java
public interface UserService {
    // 客户端通过这个接口调用服务端的实现类
    User getUserByUserId(Integer id);
}
```

3. 服务端需要实现Service接口的功能

```java
public class UserServiceImpl implements UserService {
    @Override
    public User getUserByUserId(Integer id) {
        System.out.println("客户端查询了"+id+"的用户");
        // 模拟从数据库中取用户的行为
        Random random = new Random();
        User user = User.builder().userName(UUID.randomUUID().toString())
                .id(id)
                .sex(random.nextBoolean()).build();
        return user;
    }
}
```

4. 客户端建立Socket连接，传输Id给服务端，得到返回的User对象

```java
public class RPCClient {
    public static void main(String[] args) {
        try {
            // 建立Socket连接
            Socket socket = new Socket("127.0.0.1", 8899);
            ObjectOutputStream objectOutputStream = new ObjectOutputStream(socket.getOutputStream());
            ObjectInputStream objectInputStream = new ObjectInputStream(socket.getInputStream());
            // 传给服务器id
            objectOutputStream.writeInt(new Random().nextInt());
            objectOutputStream.flush();
            // 服务器查询数据，返回对应的对象
            User user  = (User) objectInputStream.readObject();
            System.out.println("服务端返回的User:"+user);
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
            System.out.println("客户端启动失败");
        }
    }
}
```

5. 服务端以BIO的方式监听Socket，如有数据，调用对应服务的实现类执行任务，将结果返回给客户端

```java
public class RPCServer {
    public static void main(String[] args) {
        UserServiceImpl userService = new UserServiceImpl();
        try {
            ServerSocket serverSocket = new ServerSocket(8899);
            System.out.println("服务端启动了");
            // BIO的方式监听Socket
            while (true){
                Socket socket = serverSocket.accept();
                // 开启一个线程去处理
                new Thread(()->{
                    try {
                        ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
                        ObjectInputStream ois = new ObjectInputStream(socket.getInputStream());
                        // 读取客户端传过来的id
                        Integer id = ois.readInt();
                        User userByUserId = userService.getUserByUserId(id);
                        // 写入User对象给客户端
                        oos.writeObject(userByUserId);
                        oos.flush();
                    } catch (IOException e){
                        e.printStackTrace();
                        System.out.println("从IO中读取数据错误");
                    }
                }).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("服务器启动失败");
        }
    }
}
```

#### 结果：

![image-20200805001024797](http://ganghuan.oss-cn-shenzhen.aliyuncs.com/img/image-20200805001024797.png)

![image-20200805124759206](http://ganghuan.oss-cn-shenzhen.aliyuncs.com/img/image-20200805001037799.png)

#### 总结：

这个例子以不到百行的代码，实现了客户端与服务端的一个远程过程调用，非常适合上手，当然它是**及其不完善**的，甚至连消息格式都没有统一，我们将在接下来的版本更新中逐渐完善它。

#### 此RPC的最大痛点：

1. 只能调用服务端Service唯一确定的方法，如果有两个方法需要调用呢?（Reuest需要抽象）
2. 返回值只支持User对象，如果需要传一个字符串或者一个Dog，String对象呢（Response需要抽象）
3. 客户端不够通用，host，port， 与调用的方法都特定（需要抽象）



------

