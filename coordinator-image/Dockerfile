FROM maven:3.6.2-jdk-8

RUN mkdir -p /root/.ssh
ADD id_rsa /root/.ssh/

ENV HZ_HOME /home/qe/hazelcast/
WORKDIR $HZ_HOME
RUN mkdir -p /home/qe/hazelcast/simulator

RUN apt-get update -y && \
    apt-get upgrade -y && \
    apt-get install git -y

RUN git clone https://github.com/lazerion/hazelcast-simulator.git && \
    cd hazelcast-simulator && \
    git checkout tags/0.11 && \
    mvn -q -B install -DskipTests

RUN cd hazelcast-simulator && \
    ls -ll && \
    chmod +x ./install.sh && \
    bash -c "./install.sh /home/qe/hazelcast/simulator" && \
    cd /home/qe/hazelcast/simulator && \
    cd hazelcast-simulator-0.11 && \
    bash -c "./bin/simulator-wizard --install"

RUN apt-get install rsync -y

CMD ["java", "-version"]
