#!/usr/bin/env groovy

class deploymentType implements Serializable {
  private static String ABANDON_PREDEPLOY = 'ABANDON_PREDEPLOY'
  private static String ABANDON_PREDEPLOY_DESCRIPTION =
"""${ABANDON_PREDEPLOY}
  1) Removes git tag "latest-prerelease"
  2) Removes GCR tags "latest-prerelease"
  3) Removes "latest-prerelease" image from GCE instances
  Must be on branch "master" to run this deployment.
"""
  private static String EDGE_DEPLOY = 'EDGE_DEPLOY'
  private static String EDGE_DEPLOY_DESCRIPTION =
"""${EDGE_DEPLOY}
  1) Pulls "gamma-prerelease" docker images on edge GCE instance
  2) Promotes GCR docker images labeled "gamma-prerelease" to "gamma"
  3) Adds git tag "gamma" to "gamma-prerelease" tagged commit
  4) Restarts edge GCE services to use new "gamma" docker images
  Must be on branch "edge" to run this deployment.
"""
  private static String PROD_DEPLOY = 'PROD_DEPLOY'
  private static String PROD_DEPLOY_DESCRIPTION =
"""${PROD_DEPLOY}
  1) Promotes GCR docker images labeled "beta" to "latest-prerelease"
  2) Adds git tag "latest-prerelease" to "beta" tagged commit
  3) Adds GCR tag of "TAG" to docker images
  4) Adds git tag of "TAG" with "TAG_MESSAGE" to "latest-prerelease" tagged commit
  5) Pulls "latest-prerelease" docker images on prod GCE instances
  6) Locks stage by preventing new image builds of master until PROD_DEPLOY or ABANDON_PREDEPLOY have been run
  Must be on branch "master" to run this deployment.
"""
  private static String PROD_PREDEPLOY = 'PROD_PREDEPLOY'
  private static String PROD_PREDEPLOY_DESCRIPTION =
"""${PROD_PREDEPLOY}
  1) Promotes GCR docker images labeled "latest-prerelease" to "latest"
  2) Restarts prod GCE services to use new "latest" docker images
  Must be on branch "master" to run this deployment.
"""
}
