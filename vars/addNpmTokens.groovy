#!/usr/bin/env groovy

void call(String environment, String service) {
  sh """
    set -o errexit
    set -o nounset
    set -o pipefail

    docker run --rm \
      -v ${pwd}:/output \
      -e ENVIRONMENT=${environment} \
      -e SERVICE=${service} \
      -e CONFD_NODES='["http://config-1:2379", "http://config-2:2379", "http://config-3:2379"]' \
      gcr.io/mindmixer-sidewalk/confd:envfile

    npm_tokens=\$(grep -iP '^npm\.' ${service}.env)

    if [[ -n \${npm_tokens} ]]; then
      for token in \${npm_tokens[@]}; do
        token_name=\$(echo \${token} | cut -d '=' -f 1)
        token_value=\$(echo \${token} | cut -d '=' -f 2)
        echo "//\${token_name}/:_authToken=\${token_value}" >> .npmrc
      done
    fi

    rm ${service}.env
  """
}
