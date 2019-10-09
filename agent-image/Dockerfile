FROM jdeathe/centos-ssh:2.6.1
RUN yum install rsync -y
RUN yum install dstat -y
RUN yum install java-1.8.0-openjdk-devel -y
RUN echo "app-admin ALL=(ALL) NOPASSWD: ALL" >> /etc/sudoers
CMD ["/usr/bin/supervisord", "--configuration=/etc/supervisord.conf"]