#!/usr/bin/env bash

set -ex

sed -i 's@http://nexus-private@https://nexus-private@g' gradle.properties
./gradlew test
