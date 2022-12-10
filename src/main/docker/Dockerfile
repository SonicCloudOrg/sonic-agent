FROM registry.cn-hongkong.aliyuncs.com/sonic-cloud/sonic-agent-linux-base:v1.0.0

ADD /mini /root/mini
ADD /plugins /root/plugins
ADD /src/main/docker/config /root/config
ADD /target/sonic-agent-linux-x86_64.jar /root
RUN ln -sf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime
ENTRYPOINT ["/root/jdk-15.0.2+7/bin/java","-server","-Dfile.encoding=utf-8","-XX:-UseGCOverheadLimit","-XX:+DisableExplicitGC","-XX:SurvivorRatio=1","-XX:LargePageSizeInBytes=128M","-XX:SoftRefLRUPolicyMSPerMB=0","-Djava.security.egd=file:/dev/./urandom","--add-exports=java.naming/com.sun.jndi.ldap=ALL-UNNAMED","-jar","sonic-agent-linux-x86_64.jar"]
