FROM tomcat:7-jre7

MAINTAINER lindengping lindengping@yihecloud.com

WORKDIR /program

COPY target/lindengping1-0.0.1-SNAPSHOT.jar /program/lindengping1.jar

#CMD ["java","-cp","/program/lindengping1.jar","com.yihe.jk.main.TopK"]
