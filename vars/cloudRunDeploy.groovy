#!/usr/bin/env groovy

void call(String serviceName, String region, String image, String envfile='', String extraLabels='') {
  sh """
    set -o errexit
    set -o nounset
    set -o pipefail

    labels="mangedby=jenkins"

    if [ -z "${extraLabels}" ]; then
      labels+="${extraLabels}"
    fi

    if [ -z "${envfile}" ]; then
      gcloud run deploy ${serviceName} \
        --quiet \
        --image=${image} \
        --update-labels=\${labels} \
        --region=${region}
    else
      gcloud run deploy ${serviceName} \
        --quiet \
        --image=${image} \
        --update-labels=\${labels} \
        --region=${region} \
        --env-vars-file=${envfile}
    fi
  """
}
