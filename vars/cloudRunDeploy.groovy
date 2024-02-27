#!/usr/bin/env groovy

void call(String serviceName, String image, String envfile) {
  sh """
    set -o errexit
    set -o nounset
    set -o pipefail

    if [ -z "${envfile}" ]; then
      gcloud run deploy ${serviceName} --image=${image} 
    else
      gcloud run deploy ${serviceName} --image=${image} --env-vars-file=${envfile}
    fi
  """
}
