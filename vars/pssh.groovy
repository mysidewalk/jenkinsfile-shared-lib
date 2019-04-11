#!/usr/bin/env groovy

void call(Set<String> hosts, String command) {
  sh """
    pssh \
      -H "${hosts.sort().join(' ')}" \
      -O StrictHostKeyChecking=no \
      -O UserKnownHostsFile=/dev/null \
      -t 300 \
      --inline \
      "${command}"
  """
}
