#!/usr/bin/env groovy

def call(String image) {
  echo "Deleting ${image}:${tag}."
  sh "docker rmi ${image}:${tag}"
  sh "gcloud container images untag -q ${image}:${tag}"
}
