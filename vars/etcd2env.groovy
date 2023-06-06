#!/usr/bin/env groovy

void call(String service, String environment, String envfile) {
  sh """
    set -o errexit
    set -o nounset
    set -o pipefail

    docker run --rm gcr.io/mindmixer-sidewalk/etcd2env \
        python generate_env_vars.py config1.c.mindmixer-sidewalk.internal ${service} ${environment} > ${envfile}
  """
}
