#!/bin/bash

mvn -s settings.xml release:prepare release:perform -B \
    -Dgpg.passphrase="${GPG_PASS}" \
    -Dresume=false \
    -Dtag=${RELEASE_VERSION} \
    -DreleaseVersion=${RELEASE_VERSION} \
    -DdevelopmentVersion=${DEVELOPMENT_VERSION} \
    -Darguments='--settings=settings.xml -DskipTests=true -Dgpg.passphrase="${GPG_PASS}"'
