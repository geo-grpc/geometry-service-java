ARG JDK_TAG=8-jdk-slim
ARG JRE_TAG=${JDK_TAG}

FROM echoparklabs/geometry-api-java:${JDK_TAG} as builder

MAINTAINER David Raleigh <david@echoparklabs.io>

RUN apt-get update

COPY ./ /opt/src/geometry-service-java

WORKDIR /opt/src/geometry-service-java

RUN ./gradlew build install


FROM us.gcr.io/echoparklabs/geometry-api-java:${JRE_TAG}
RUN apt-get update

WORKDIR /opt/src/geometry-service-java/build/install
COPY --from=builder /opt/src/geometry-service-java/build/install .

RUN chmod +x /opt/src/geometry-service-java/build/install/epl-geometry-service/bin/geometry-operators-server

EXPOSE 8980

#TODO, I should be able to make a test image and copy from that, right?
WORKDIR /opt/src/geometry-service-java/build/test-results
COPY --from=builder /opt/src/geometry-service-java/build/test-results .

CMD /opt/src/geometry-service-java/build/install/epl-geometry-service/bin/geometry-operators-server
