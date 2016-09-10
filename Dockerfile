FROM docker.io/openjdk:8-alpine

MAINTAINER lindengping lindengping@yihecloud.com

WORKDIR /program

COPY docker/startup.sh /program/startup.sh
RUN chmod +x /program/startup.sh

COPY target/lindengping1-0.0.1-SNAPSHOT.jar /program/lindengping1.jar

CMD /program/startup.sh
