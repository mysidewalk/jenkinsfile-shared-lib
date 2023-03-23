#!/usr/bin/env groovy

void call(String service) {
  sh """
    set -o errexit
    set -o nounset
    set -o pipefail

    npm_tokens=\$(grep -iP '^npm\\.' ${service}.env)

    if [[ -n \${npm_tokens} ]]; then
      for token in \${npm_tokens[@]}; do
        token_name=\$(echo \${token} | cut -d '=' -f 1)
        token_value=\$(echo \${token} | cut -d '=' -f 2)
        echo "//\${token_name}/:_authToken=\${token_value}" >> .npmrc
      done
    fi

    rm -f ${service}.env
  """
}
