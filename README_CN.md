<p align="center">
  <img src="https://raw.githubusercontent.com/ZhouYixun/sonic-server/main/logo.png">
</p>
<p align="center">🎉Sonic云真机测试平台Agent端</p>
<p align="center">
  <a href="https://github.com/ZhouYixun/sonic-agent/blob/main/README.md">  
    English
  </a>
  <span>| 简体中文</span>
</p>
<p align="center">
  <a href="#">  
    <img src="https://img.shields.io/badge/release-v1.0.0-orange">
  </a>
  <a href="#">  
    <img src="https://img.shields.io/badge/platform-windows|macosx|linux-success">
  </a>
</p>
<p align="center">
  <a href="#">  
    <img src="https://img.shields.io/github/commit-activity/m/ZhouYixun/sonic-agent">
  </a>
  <a href="https://hub.docker.com/repository/docker/zhouyixun/sonic-agent-linux">  
    <img src="https://img.shields.io/docker/pulls/zhouyixun/sonic-agent-linux">
  </a>
  <a href="https://github.com/ZhouYixun/sonic-server/blob/main/LICENSE">  
    <img src="https://img.shields.io/github/license/ZhouYiXun/sonic-server?color=green&label=license&logo=license&logoColor=green">
  </a>
</p>

## 背景

#### 什么是Sonic？

> 如今，自动化测试、远程控制等技术已经逐渐成熟。其中 [Appium](https://github.com/appium/appium) 在自动化领域可以说是领头者，[STF](https://github.com/openstf/stf) 则是远程控制的始祖。很久前就开始有了一个想法，是否可以在一个平台上，提供解决所有客户端（Android、iOS、Windows、Mac、Web应用）的测试方案，于是，Sonic云真机测试平台由此诞生。

#### 愿景

> Sonic当前的愿景是能帮助中小型企业解决在客户端自动化或远控方面缺少工具和测试手段的问题。
>
>  如果你想参与其中，欢迎加入！💪
>
> 如果你想支持，可以给我一个star。⭐

## 打包方式

```
mvn package -P{你的平台}
```

例如

```
mvn package -Pwindows-x86_64
```

## 部署方式

### Docker模式

> 仅Linux和Mac可用！
>
> [点击这里!](https://hub.docker.com/repository/docker/zhouyixun/sonic-agent-linux)

### jar模式

|  变量名   | 描述  |
|  ----  | ----  |
| RABBITMQ_HOST  | RabbitMQ 服务host,默认 **localhost** |
| RABBITMQ_PORT  | RabbitMQ 服务port,默认 **5672** |
| RABBITMQ_USERNAME  | RabbitMQ 服务用户名,默认 **sonic** |
| RABBITMQ_PASSWORD  | RabbitMQ 服务用户密码,默认 **sonic** |
| RABBITMQ_VHOST  | RabbitMQ service 虚拟host,默认 **sonic** |
| SONIC_FOLDER_URL  | Sonic-server-folder 经gateway反向代理后url,默认 **http://localhost:8094/api/folder** |
| SONIC_AGENT_HOST  | Agent本地运行IPv4,默认 **127.0.0.1** |
| SONIC_AGENT_KEY  | 创建的已知Agent key,默认随机生成一个key（当然这个key不可用） |

文件夹: mini,chromeDriver and language

```
java -jar -D{变量1} -D{变量2} sonic-agent-linux_86.jar
```

## 开源许可协议

[MIT License](LICENSE)