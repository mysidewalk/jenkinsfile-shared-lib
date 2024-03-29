#!/usr/bin/env groovy

void call(String serviceName, String region, String image, String envfile='') {
  sh """
    set -o errexit
    set -o nounset
    set -o pipefail

    GIT_REVISION=\$(git rev-parse HEAD)

    if [ -z "${envfile}" ]; then
      gcloud run deploy ${serviceName} \
        --quiet \
        --image=${image} \
        --update-labels=managed-by=jenkins,commit-sha=\$GIT_REVISION \
        --region=${region}
    else
      gcloud run deploy ${serviceName} \
        --quiet \
        --image=${image} \
        --update-labels=managed-by=jenkins,commit-sha=\$GIT_REVISION \
        --region=${region} \
        --env-vars-file=${envfile}
    fi
  """
}
