#!/usr/bin/env groovy

String call(String deploymentType, String branchName) {
  BRANCH_TO_ENVIRONMENT = [
    (branch.EDGE): environment.EDGE,
    (branch.MASTER): environment.STAGE,
  ]
  if (deploymentType in [deploymentType.PROD_DEPLOY, deploymentType.PROD_PREDEPLOY]) {
    result = environment.PROD
  }
  else {
    result = BRANCH_TO_ENVIRONMENT[$branchName] ?: environment.LOCAL
  }
  result
}
