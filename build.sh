#!/bin/bash
echo "Build triggered by event ${TRAVIS_EVENT_TYPE}"
echo "Git branch ${TRAVIS_BRANCH}"
if [ "${TRAVIS_EVENT_TYPE}" = "pull_request" ]; then
	./mvnw clean test
else
	./mvnw clean deploy -Dcirrus.registry=registry.cirrus.ibm.com -Dcirrus.project=$CIRRUS_PROJECT -Dcirrus.username=$CIRRUS_USER -Dcirrus.password=$CIRRUS_PWD -Dops.gateway=$OPS_GATEWAY -Dops.gatewayuser=$OPS_GATEWAY_USER -Dops.gatewaypwd=$OPS_GATEWAY_PWD -Dbuild.number=$TRAVIS_BUILD_NUMBER -Dbuild.id=$TRAVIS_BUILD_ID -Dbuild.log=$TRAVIS_BUILD_WEB_URL -Dworkload.repo=$WORKLOAD_REPO -Dworkload.repouser=$ARTIFACTORY_USER -Dworkload.repopassword=$ARTIFACTORY_PWD
fi