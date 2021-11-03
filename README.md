<p align="center">
  <img src="https://raw.githubusercontent.com/ZhouYixun/sonic-server/main/logo.png">
</p>
<p align="center">🎉Agent of Sonic Cloud Real Machine Testing Platform</p>
<p align="center">
  <span>English |</span>
  <a href="https://github.com/ZhouYixun/sonic-agent/blob/main/README_CN.md">  
     简体中文
  </a>
</p>
<p align="center">
  <a href="#">  
    <img src="https://img.shields.io/badge/release-v1.1.0-orange">
  </a>
  <a href="#">  
    <img src="https://img.shields.io/badge/platform-windows|macosx|linux-success">
  </a>
</p>
<p align="center">
  <a href="#">  
    <img src="https://img.shields.io/github/commit-activity/m/ZhouYixun/sonic-agent">
  </a>
  <a href="#">  
    <img src="https://img.shields.io/github/downloads/ZhouYixun/sonic-agent/total">
  </a>
  <a href="https://github.com/ZhouYixun/sonic-server/blob/main/LICENSE">  
    <img src="https://img.shields.io/github/license/ZhouYiXun/sonic-server?color=green&label=license&logo=license&logoColor=green">
  </a>
</p>

### Official Website
[Sonic Official Website](http://zhouyixun.gitee.io/sonic-official-website)
## Background

#### What is sonic ?

> Nowadays, automatic testing, remote control and other technologies have gradually matured. [Appium](https://github.com/appium/appium) can be said to be the leader in the field of automation, and [STF](https://github.com/openstf/stf) is the ancestor of remote control. A long time ago, I began to have an idea about whether to provide test solutions for all clients (Android, IOS, windows, MAC and web applications) on one platform. Therefore, sonic cloud real machine testing platform was born.

#### Vision

> Sonic's vision is to help small and medium-sized enterprises solve the problem of lack of tools and testing means in client automation or remote control.
>
>If you want to participate, welcome to join! 💪
>
>If you want to support, you can give me a star. ⭐

## How to package

```
mvn package -P{your_platform}
```

For Example

```
mvn package -Pwindows-x86_64
```

## Deployment mode

### Docker Mode

> Can only be used on Linux or Mac！
>
> [Click Here!](https://hub.docker.com/repository/docker/zhouyixun/sonic-agent-linux)

### jar Mode

|  ENV Name   | Description  |
|  ----  | ----  |
| RABBITMQ_HOST  | RabbitMQ service host,default **localhost** |
| RABBITMQ_PORT  | RabbitMQ service port,default **5672** |
| RABBITMQ_USERNAME  | RabbitMQ service username,default **sonic** |
| RABBITMQ_PASSWORD  | RabbitMQ service password,default **sonic** |
| RABBITMQ_VHOST  | RabbitMQ service virtual-host,default **sonic** |
| SONIC_FOLDER_URL  | Sonic-server-folder url,default **http://localhost:8094/api/folder** |
| SONIC_AGENT_HOST  | IPv4 running locally,default **127.0.0.1** |
| SONIC_AGENT_KEY  | Agent's key for sonic-server,default random key |

Folder: mini,chromeDriver and language

```
java -jar -D{your_env1} -D{your_env2} sonic-agent-linux_86.jar
```

## LICENSE

[MIT License](LICENSE)
