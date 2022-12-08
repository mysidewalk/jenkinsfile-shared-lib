#!/usr/bin/env groovy

void call(String etcdPrefix) {
  sh """
    set -o errexit
    set -o nounset
    set -o pipefail

    export ETCDCTL_API=2
    export ETCDCTL_ENDPOINTS='http://config-1:4001,http://config-2:4001,http://config-3:4001'
    npm_tokens=\$(etcdctl ls ${etcdPrefix} | rev | cut -d '/' -f 1 | rev)

    if [[ -n \${npm_tokens} ]]; then
      for token_name in \${npm_tokens[@]}; do
        token_value=\$(etcdctl get ${etcdPrefix}/\${token_name})
        echo "//\${token_name}/:_authToken=\${token_value}" >> .npmrc
      done
    fi
  """
}
