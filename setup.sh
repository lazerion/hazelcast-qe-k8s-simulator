#!/usr/bin/env bash

check_dependencies() {
    for dep in curl mvn kubectl minikube; do
    if hash ${dep} 2>/dev/null; then
        echo ${dep} installed...
    else
        echo please install ${dep}, exiting...
        exit
    fi
    done
}

print_usage(){
    echo "usage: $program_name [-d] =<value> "
    echo "  -d if true skips build of provisioner docker image build default: true"
    echo "  -h print usage"
    exit 1
}

set -e
set -o pipefail
program_name=$0

for i in "$@"
do
case ${i} in
    -d=*)
    skip="${i#*=}"
    shift # past argument=value
    ;;
    -h)
    print_usage
    shift # past argument with no value
    ;;
    *)
          # unknown option
    print_usage
    ;;
esac
done

if [ "$skip" == "" ]; then
    skip="true"
fi

cwd=$(pwd)

check_dependencies

eval $(minikube docker-env)

echo building base agent image
cd ${cwd}
cd ./agent-image/
docker build . -t agent:10

echo building provisioner image
cd ${cwd}
cd ./provisioner-image/
docker build . -t provisioner-base:8

if [ "$skip" != "true" ]; then
    cd ${cwd}
    cd ./provisioner/
    echo preparing image of provisioner
    mvn -q -B package docker:build -Dmaven.test.skip=true
fi

cd ${cwd}
kubectl apply -f fabric8.yaml
kubectl apply -f deployment.yaml
kubectl apply -f service.yaml
sleep 10
kubectl port-forward --pod-running-timeout=1m0s deployment/provisioner 4567:4567