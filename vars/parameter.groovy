#!/usr/bin/env groovy

class parameter implements Serializable {
  private static String ACTION = 'ACTION'
  private static String ACTION_CHOICES = "none\n${deploymentType.ABANDON_PREDEPLOY}\n${deploymentType.EDGE_DEPLOY}\n${deploymentType.PROD_PREDEPLOY}\n${deploymentType.PROD_DEPLOY}"
  private static String ACTION_DESCRIPTION = """${deploymentType.ABANDON_PREDEPLOY}
  1) Removes git tag "latest-prerelease"
  2) Removes GCR tags "latest-prerelease"
  3) Removes "latest-prerelease" image from GCE instances
  Must be on branch "master" to run this deployment.

${deploymentType.EDGE_DEPLOY}
  1) Pulls "gamma-prerelease" docker images on edge GCE instance
  2) Promotes GCR docker images labeled "gamma-prerelease" to "gamma"
  3) Adds git tag "gamma" to "gamma-prerelease" tagged commit
  4) Restarts edge GCE services to use new "gamma" docker images
  Must be on branch "edge" to run this deployment.

${deploymentType.PROD_PREDEPLOY}
  1) Promotes GCR docker images labeled "beta" to "latest-prerelease"
  2) Adds git tag "latest-prerelease" to "beta" tagged commit
  3) Adds GCR tag of "TAG" to docker images
  4) Adds git tag of "TAG" with "TAG_MESSAGE" to "latest-prerelease" tagged commit
  5) Pulls "latest-prerelease" docker images on prod GCE instances
  6) Locks stage by preventing new image builds of master until PROD_DEPLOY or ABANDON_PREDEPLOY have been run
  Must be on branch "master" to run this deployment.

${deploymentType.PROD_DEPLOY}
  1) Promotes GCR docker images labeled "latest-prerelease" to "latest"
  2) Restarts prod GCE services to use new "latest" docker images
  Must be on branch "master" to run this deployment."""
  private static String TAG = 'TAG'
  private static String TAG_DEFAULT_VALUE = 'none'
  private static String TAG_DESCRIPTION = 'Git and GCR Docker image tag name. (e.g. 2017-01-30-v1) Only used by ${deploymentType.PROD_PREDEPLOY}.'
  private static String TAG_MESSAGE = 'TAG_MESSAGE'
  private static String TAG_MESSAGE_DEFAULT_VALUE = 'release candidate'
  private static String TAG_MESSAGE_DESCRIPTION = 'Git tag message. (e.g. "This is the first release of reports.") Only used by ${deploymentType.PROD_PREDEPLOY}.'
}
