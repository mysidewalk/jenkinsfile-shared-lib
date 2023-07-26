#!/usr/bin/env groovy

void call(String etcdpath, String outfile) {
  sh """
    set -o errexit
    set -o nounset
    set -o pipefail

    export ETCDCTL_API=2
    export ETCDCTL_ENDPOINTS='http://config-1:4001,http://config-2:4001,http://config-3:4001'

    etcdctl get $etcdpath > $outfile
  """
}
