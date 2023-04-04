#!/usr/bin/env groovy

void call(String service) {
  sh """
    set -o errexit
    set -o nounset
    set -o pipefail

    npm_tokens=\$(grep -iP '^npm\\.' ${service}.env)

    if [[ -n \${npm_tokens} ]]; then
      for token in \${npm_tokens[@]}; do
        echo "//\${token%%=*}/:_authToken=\${token##*=}" >> .npmrc
      done
    fi
  """
}
