#!/usr/bin/env bash
docker run --env-file=.env -p 49161:8080 -d tombot:1.0