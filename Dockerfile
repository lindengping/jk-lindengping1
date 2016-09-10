FROM docker.io/openjdk:8-alpine

MAINTAINER lindengping lindengping@yihecloud.com

WORKDIR /program

COPY target/lindengping1-0.0.1-SNAPSHOT.jar /program/lindengping1.jar

CMD ["java" " -jar /program/lindengping1.jar"]