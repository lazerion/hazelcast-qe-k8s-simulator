#!/usr/bin/env bash

set -e
set -o pipefail

# todo need to refine to speed up setup
kubectl delete --all deployments
kubectl delete --all pods
kubectl delete --all services