#!/usr/bin/env groovy

void call(Set<String> hosts, String image) {
  echo "Pulling docker image '${image}' on host(s): ${hosts.sort().join(', ')}"
  pssh(hosts, "sudo docker pull ${image}")
}
