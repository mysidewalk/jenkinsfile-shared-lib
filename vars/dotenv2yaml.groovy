#!/usr/bin/env groovy

void call(String envfile, String outfile) {
  sh """
    set -o errexit
    set -o nounset
    set -o pipefail

    # temporarily remove PORT from envfile for Cloud Run
    sed -i '/^PORT=/d' ${envfile}
    cat ${envfile} | docker run --rm -i gcr.io/mindmixer-sidewalk/dotenv-to-yaml > ${outfile}
  """
}
