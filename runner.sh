#!/usr/bin/env bash

set -e
set -o pipefail

scale=${1:-3}
file=${2:-test.properties}

echo uploading '-->' ${file}
echo scaling '-->' ${scale}

# deployment base address
url=http://localhost:4567
echo ping
while [[ "$(curl -s -o /dev/null -w ''%{http_code}'' ${url}/ping)" != "200" ]]; do sleep 0.1; done
echo deploy
curl ${url}/deploy
echo scale
curl ${url}/scale/${scale}
echo sanity
while [[ "$(curl -s -o /dev/null -w ''%{http_code}'' ${url}/agents/sanity/${scale})" != "200" ]]; do sleep 0.1; done
echo create tests
curl -X POST ${url}/create-tests/
echo check ssh
while [[ "$(curl -s -o /dev/null -w ''%{http_code}'' ${url}/check-ssh)" != "200" ]]; do sleep 0.1; done
echo upload scenario
curl -X POST ${url}/upload/ -F "file=@${file}"
echo prepare
curl ${url}/prepare
echo finally run
curl ${url}/run
