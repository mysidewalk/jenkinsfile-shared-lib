#!/usr/bin/env groovy

void call(String service, String environment) {
  sh """
    set -o errexit
    set -o nounset
    set -o pipefail

    docker run --rm \
      -v \$(pwd):/output \
      -e ENVIRONMENT=${environment} \
      -e SERVICE=${service} \
      -e CONFD_NODES='["http://config-1:2379", "http://config-2:2379", "http://config-3:2379"]' \
      gcr.io/mindmixer-sidewalk/confd:envfile
  """
}
