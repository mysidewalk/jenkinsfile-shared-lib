#!/usr/bin/env groovy

@Library('jenkinsfile-shared-lib') import deploymentType


class parameter implements Serializable {
  private static String ACTION = 'ACTION'
  private static String TAG = 'TAG'
  private static String TAG_DEFAULT_VALUE = 'none'
  private static String TAG_DESCRIPTION = "Git and GCR Docker image tag name. (e.g. 2017-01-30-v1) Only used by PROD_PREDEPLOY."
  private static String TAG_MESSAGE = 'TAG_MESSAGE'
  private static String TAG_MESSAGE_DEFAULT_VALUE = 'release candidate'
  private static String TAG_MESSAGE_DESCRIPTION = "Git tag message. (e.g. 'This is the first release of reports.') Only used by PROD_PREDEPLOY."
}
