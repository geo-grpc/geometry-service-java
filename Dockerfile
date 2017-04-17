FROM java:openjdk-8u111-alpine

COPY ./build/install /opt/src/geometry-service-java

WORKDIR /opt/src/geometry-service-java

RUN chmod +x /opt/src/geometry-service-java/geometry-service/bin/geometry-operators-server

EXPOSE 8980

CMD ["/opt/src/geometry-service-java/geometry-service/bin/geometry-operators-server"]