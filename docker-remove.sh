#!/usr/bin/env bash

if [ "$#" -ne 1 ]; then
  echo "Must provide Container ID"
  exit 1
fi
docker stop $1
docker rm $1
docker rmi tombot:1.0
