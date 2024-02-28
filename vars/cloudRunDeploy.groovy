#!/usr/bin/env groovy

void call(String serviceName, String image, String envfile, String region) {
  sh """
    set -o errexit
    set -o nounset
    set -o pipefail

    if [ -z "${envfile}" ]; then
      gcloud run deploy ${serviceName} --image=${image} --region=${region}
    else
      gcloud run deploy ${serviceName} --image=${image} --region=${region} --env-vars-file=${envfile}
    fi
  """
}
