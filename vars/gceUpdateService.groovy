#!/usr/bin/env groovy

def call(Set<String> instances, String image, String service) {
  echo "Update service '${service}' to use docker image ${image} on GCE instance(s): ${instances.sort().join(', ')}"
  psshPullImage(instances, image)
  pssh(instances, "sudo systemctl restart ${service}.service")
  pssh(instances, "sudo docker rmi ${image}-prerelease || true")
}
