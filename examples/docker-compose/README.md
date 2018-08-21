## Client Services talking to Geometry Service

This is just a quick demo of two client services talking to one geometry service. `docker-compose` demos the output of each of the containers as they run and process information.

```bash
gcloud docker -a
docker pull us.gcr.io/echoparklabs/geometry-service-java:latest
docker build -t us.gcr.io/echoparklabs/geometry-client:latest -f Dockerfile_client ./
docker-compose up
```