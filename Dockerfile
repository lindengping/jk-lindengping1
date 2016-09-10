FROM tomcat:7-jre7

MAINTAINER lindengping lindengping@yihecloud.com

WORKDIR /program

COPY target/lindengping1-0.0.1-SNAPSHOT.jar /program/lindengping1.jar

#CMD ["java","-jar","/program/lindengping1.jar"]
