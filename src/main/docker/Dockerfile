FROM ubuntu:bionic-20220315

ENV DEBIAN_FRONTEND=noninteractive

ENV SDK_VERSION=commandlinetools-linux-7583922_latest \
    ANDROID_BUILD_TOOLS_VERSION=29.0.3 \
    ATD_VERSION=1.2 \
    CHROME_VERSION=google-chrome-stable

WORKDIR /root

RUN apt-get -qqy update && \
    apt-get -qqy --no-install-recommends install \
    ca-certificates \
    usbmuxd \
    zip \
    unzip \
    curl \
    wget \
    libqt5webkit5 \
    libgconf-2-4 \
    gnupg \
    salt-minion \
    libasound2-dev freeglut3-dev libgtk2.0-dev libusb-dev zlib1g libffi-dev libbz2-dev zlib1g-dev \
  && rm -rf /var/lib/apt/lists/*

RUN wget -O java.tar.gz https://github.com/AdoptOpenJDK/openjdk15-binaries/releases/download/jdk-15.0.2%2B7/OpenJDK15U-jdk_x64_linux_hotspot_15.0.2_7.tar.gz && \
    tar zxvf java.tar.gz && rm java.tar.gz && \
    chmod a+x -R /root/jdk-15.0.2+7 && \
    chown -R root:root /root/jdk-15.0.2+7

ENV JAVA_HOME="/root/jdk-15.0.2+7" \
    PATH=$PATH:$JAVA_HOME/bin

ENV ANDROID_HOME=/root

RUN mkdir -p $ANDROID_HOME/cmdline-tools && \
    cd $ANDROID_HOME/cmdline-tools && \
    wget -O tools.zip https://dl.google.com/android/repository/${SDK_VERSION}.zip && \
    unzip tools.zip && rm tools.zip && \
    mv ./* ./latest && \
    chmod a+x -R $ANDROID_HOME && \
    chown -R root:root $ANDROID_HOME

ENV PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest:$ANDROID_HOME/cmdline-tools/latest/bin

RUN mkdir -p ~/.android && \
    touch ~/.android/repositories.cfg && \
    echo y | sdkmanager "platform-tools" && \
    echo y | sdkmanager "build-tools;$ANDROID_BUILD_TOOLS_VERSION"

ENV PATH=$PATH:$ANDROID_HOME/platform-tools:$ANDROID_HOME/build-tools

RUN wget -q -O - https://dl-ssl.google.com/linux/linux_signing_key.pub | apt-key add - \
  && echo "deb http://dl.google.com/linux/chrome/deb/ stable main" >> /etc/apt/sources.list.d/google-chrome.list \
  && apt-get update -qqy \
  && apt-get -qqy install \
    ${CHROME_VERSION} \
  && rm /etc/apt/sources.list.d/google-chrome.list

RUN curl -sL https://deb.nodesource.com/setup_12.x | bash && \
    apt-get -qqy install nodejs && \
    npm install -g adbkit --unsafe-perm=true --allow-root && \
    exit 0 && \
    npm cache clean && \
    apt-get remove --purge -y npm && \
    apt-get autoremove --purge -y && \
    rm -rf /var/lib/apt/lists/* /tmp/* /var/tmp/* && \
    apt-get clean

RUN mkdir -p mini webview plugins config

ADD /mini /root/mini
ADD /webview /root/webview
ADD /plugins /root/plugins
ADD /src/main/docker/config /root/config
ADD /target/sonic-agent-linux-x86_64.jar /root
RUN ln -sf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime
ENTRYPOINT ["/root/jdk-15.0.2+7/bin/java","-server","-Dfile.encoding=utf-8","-XX:-UseGCOverheadLimit","-XX:+DisableExplicitGC","-XX:SurvivorRatio=1","-XX:LargePageSizeInBytes=128M","-XX:SoftRefLRUPolicyMSPerMB=0","-Djava.security.egd=file:/dev/./urandom","--add-exports=java.naming/com.sun.jndi.ldap=ALL-UNNAMED","-jar","sonic-agent-linux-x86_64.jar"]
