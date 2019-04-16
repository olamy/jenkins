/*
 * This Jenkinsfile is intended to run on https://rosie.gauntlet.cloudbees.com and may fail anywhere else.
 * It makes assumptions about plugins being installed, labels mapping to nodes that can build what is needed, etc.
 *
 * This Jenkinsfile is a subset of the community one version 2.73
 *
 * Dependencies:
 *  - https://github.com/cloudbees/rosie-libs
 */

@Library('rosie-libs') _

// TEST FLAG - to make it easier to turn on/off unit tests for speeding up access to later stuff.
def runTests = true

// Release private signed war pipeline
def RELEASE_PRIVATE_SIGNED_WAR = 'distributables/release/release-private-jenkins-signed-war'

// URR bump version and automated RFC pull request creation
def urrBranch = "stable-"
def branchName = UUID.randomUUID().toString()
def cred = env.GITHUB_CREDENTIALS
def configFile = """
github.com:
- user: cloudbeesrosieci
  oauth_token: ${env.GITHUB_CREDENTIALS_TOKEN}
  protocol: https
"""

// Jenkins version
def jenkinsVersion = ""
def version = ""


properties([buildDiscarder(logRotator(numToKeepStr: '15', artifactNumToKeepStr: '15'))])

node('private-core-template-maven3.5.4') {
    timestamps {
        // First stage is actually checking out the source. Since we're using Multibranch
        // currently, we can use "checkout scm".
        stage('Checkout') {
            checkout scm
        }

        // Now run the actual build.
        stage('Build / Test') {
            timeout(time: 360, unit: 'MINUTES') {
                try {
                    environment.withMavenSettings {
                        def m2repo = "${pwd tmp: true}/m2repo"
                        // See below for what this method does - we're passing an arbitrary environment
                        // variable to it so that JAVA_OPTS and MAVEN_OPTS are set correctly.
                        List mvnEnv = ["JAVA_OPTS=-Xmx1536m -Xms512m", "MAVEN_OPTS=-Xmx1536m -Xms512m"]

                        // Invoke the maven run within the environment we've created
                        withEnv(mvnEnv) {
                            // -Dmaven.repo.local=… tells Maven to create a subdir in the temporary directory for the local Maven repository
                            sh """
                                mvn -Pdebug -U clean verify ${runTests ? '-Dmaven.test.failure.ignore' : '-DskipTests'} -V -B -Dmaven.repo.local=$m2repo -s settings.xml -e
                                cp -a target/*.pom pom.xml
                            """
                            def pom = readMavenPom()
                            jenkinsVersion = pom.version?.replaceAll('-SNAPSHOT', '')
                            version = jenkinsVersion.replaceAll('-cb-','.') + '-SNAPSHOT'
                            urrBranch += jenkinsVersion.substring(0,5)
                        }
                    }
                } finally {

                    if (runTests) {
                        junit healthScaleFactor: 20.0, testResults: '**/target/surefire-reports/*.xml'
                    }
                }
            }
        }

        // Release a new private core signed war
        stage('Release') {
            build job: 
                RELEASE_PRIVATE_SIGNED_WAR,
                parameters: [  string(name: 'BRANCH', value: env.BRANCH_NAME),
                               booleanParam(name: 'skipTests', value: true),
                               booleanParam(name: 'skipApproval', value: true)]
                               // TODO: needs to include skipApproval as input parameter
        }

        // Generate a new PR against URR with bumped version
        stage('Bump version on URR') {
            dir("/root/.config"){
               writeFile file: "hub", text: configFile
            }
           
            sh """
                wget https://github.com/github/hub/releases/download/v2.10.0/hub-linux-amd64-2.10.0.tgz
                tar -xvzf hub-linux-amd64-2.10.0.tgz
            """
           
            dir("hub-linux-amd64-2.10.0/code") {
                 // Needed to get the envelope plugin
                withMaven(mavenSettingsConfig: 'ci-release-jobs') {
                    git credentialsId: env.GITHUB_CREDENTIALS, url: 'https://github.com/cloudbees/unified-release.git', branch: urrBranch
                    withCredentials([usernamePassword(credentialsId: cred, passwordVariable: 'GIT_PASSWORD', usernameVariable: 'GIT_USERNAME')]) {
                        sh """
                            git config user.name "${GIT_USERNAME}"
                            git config user.email 'operations+cloudbeesrosieci@cloudbees.com'
                            git checkout -b ${branchName}
                            mvn versions:set-property -Dproperty=jenkins.version -DnewVersion=${jenkinsVersion}
                            mvn versions:set -DnewVersion=${version}
                            mvn envelope:validate
                            git add .
                            git commit -m '[automated] Bump version'
                            ../bin/hub pull-request -m "Automated bump version" -h cloudbeesrosieci:${branchName}
                        """
                   }
                }
            }
        }
    }
}
