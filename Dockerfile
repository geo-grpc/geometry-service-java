FROM us.gcr.io/echoparklabs/geometry-api-java:latest as builder

COPY ./ /opt/src/geometry-service-java

WORKDIR /opt/src/geometry-service-java

RUN ./gradlew build install



FROM us.gcr.io/echoparklabs/geometry-api-java:latest

WORKDIR /opt/src/geometry-service-java/build/install

COPY --from=builder /opt/src/geometry-service-java/build/install .

RUN chmod +x /opt/src/geometry-service-java/build/install/geometry-service/bin/geometry-operators-server

EXPOSE 8980

#TODO, I should be able to make a test image and copy from that, right?
WORKDIR /opt/src/geometry-service-java/build/test-results
COPY --from=builder /opt/src/geometry-service-java/build/test-results .

CMD /opt/src/geometry-service-java/build/install/geometry-service/bin/geometry-operators-server
