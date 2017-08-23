#!/usr/bin/env groovy

def call(Set<String> hosts, String command) {
  sh """
    pssh \
      -H "${hosts.sort().join(' ')}" \
      -O StrictHostKeyChecking=no \
      -O UserKnownHostsFile=/dev/null \
      -t 120 \
      --inline \
      "${command}"
  """
}
