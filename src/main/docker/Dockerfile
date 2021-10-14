FROM ubuntu:bionic-20200311

ENV DEBIAN_FRONTEND=noninteractive

ENV SDK_VERSION=sdk-tools-linux-3859397 \
    ANDROID_BUILD_TOOLS_VERSION=31.0.0 \
    APPIUM_VERSION=1.22.0 \
    ATD_VERSION=1.2

WORKDIR /root

RUN apt-get -qqy update && \
    apt-get -qqy --no-install-recommends install \
    openjdk-8-jdk \
    ca-certificates \
    zip \
    unzip \
    curl \
    wget \
    libqt5webkit5 \
    libgconf-2-4 \
    gnupg \
    salt-minion \
  && rm -rf /var/lib/apt/lists/*

ENV JAVA_HOME="/usr/lib/jvm/java-8-openjdk-amd64/jre" \
    PATH=$PATH:$JAVA_HOME/bin

RUN wget -O java.tar.gz https://download.oracle.com/otn-pub/java/jdk/16.0.2+7/d4a915d82b4c4fbb9bde534da945d746/jdk-16.0.2_linux-x64_bin.tar.gz?AuthParam=1634195311_0d15009c3d955574b75fd6d51664b996 && \
    tar zxvf java.tar.gz && rm java.tar.gz && \
    chmod a+x -R /root/jdk-16.0.2 && \
    chown -R root:root /root/jdk-16.0.2

ARG ANDROID_PLATFORM_VERSION="android-25"
ENV ANDROID_HOME=/root

RUN wget -O tools.zip https://dl.google.com/android/repository/${SDK_VERSION}.zip && \
    unzip tools.zip && rm tools.zip && \
    chmod a+x -R $ANDROID_HOME && \
    chown -R root:root $ANDROID_HOME

ENV PATH=$PATH:$ANDROID_HOME/tools:$ANDROID_HOME/tools/bin

RUN mkdir -p ~/.android && \
    touch ~/.android/repositories.cfg && \
    echo y | sdkmanager "platform-tools" && \
    echo y | sdkmanager "build-tools;$ANDROID_BUILD_TOOLS_VERSION" && \
    echo y | sdkmanager "platforms;$ANDROID_PLATFORM_VERSION"

ENV PATH=$PATH:$ANDROID_HOME/platform-tools:$ANDROID_HOME/build-tools

RUN curl -sL https://deb.nodesource.com/setup_12.x | bash && \
    apt-get -qqy install nodejs && \
    npm install -g appium@${APPIUM_VERSION} --unsafe-perm=true --allow-root && \
    exit 0 && \
    npm cache clean && \
    apt-get remove --purge -y npm && \
    apt-get autoremove --purge -y && \
    rm -rf /var/lib/apt/lists/* /tmp/* /var/tmp/* && \
    apt-get clean

ADD /mini /root/
ADD /chromeDriver /root/
ADD /language /root/
ADD /sonic-agent.jar /root/
RUN ln -sf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime
ENTRYPOINT ["/root/jdk-16.0.2/bin/java","-server","-Xmx800M","-Xms800M","-Xmn300M","-Xss256K","-XX:-UseGCOverheadLimit","-XX:+DisableExplicitGC","-XX:SurvivorRatio=1","-XX:LargePageSizeInBytes=128M","-XX:SoftRefLRUPolicyMSPerMB=0","-Djava.security.egd=file:/dev/./urandom","-jar","/root/sonic-agent.jar"]