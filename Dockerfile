FROM us.gcr.io/echoparklabs/geometry-api-java:latest

COPY ./ /opt/src/geometry-service-java

WORKDIR /opt/src/geometry-service-java

RUN ./gradlew build install

RUN chmod +x /opt/src/geometry-service-java/build/install/geometry-service/bin/geometry-operators-server

EXPOSE 8980

CMD ["/opt/src/geometry-service-java/build/install/geometry-service/bin/geometry-operators-server"]
