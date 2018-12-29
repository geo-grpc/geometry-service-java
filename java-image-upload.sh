#!/usr/bin/env bash

echo counter 1
echo $1

echo image-name 2
echo $2

echo registry-name 3
echo $3

echo vcs-name 4
echo $4

echo jdk-tag 5
echo $5

echo jre-tag 6
echo $6

echo Dockerfile name 7
echo $7

echo docker build --no-cache --rm=true --quiet --build-arg JDK_TAG=$5 --build-arg JRE_TAG=$6 -t $2:$6-build-$1 -f $7 .
docker build --no-cache --rm=true --quiet --build-arg JDK_TAG=$5 --build-arg JRE_TAG=$6 -t $2:$6-build-$1 -f $7 .

echo docker push $2:$6-build-$1
docker push $2:$6-build-$1

echo docker tag $2:$6-build-$1 $2:$6
docker tag $2:$6-build-$1 $2:$6

echo docker push $2:$6
docker push $2:$6

## push to echoparklabs docker hub
#echo docker tag $2:$6 $3/$4:$6
#docker tag $2:$6 $3/$4:$6
#
#echo docker push $3/$4:$6
#docker push $3/$4:$6
#
#echo docker rmi $3/$4:$6
#docker rmi $3/$4:$6
## push to echoparklabs docker hub

echo docker rmi $2:$6
docker rmi $2:$6

echo docker rmi $2:$6-build-$1
docker rmi $2:$6-build-$1
