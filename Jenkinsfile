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

// URR bump version and automated RFC pull request creation
def urrBranch = "stable-"
def branchName = UUID.randomUUID().toString()
def cred = env.GITHUB_CREDENTIALS
def token = getToken(cred)

// Jenkins version
def jenkinsVersion = ""
def version = ""

// Bump commands
def commands = ""

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
                            commands = 'mvn versions:set-property -Dproperty=jenkins.version -DnewVersion=' + jenkinsVersion + ' && mvn versions:set -DnewVersion=' + version + ' && mvn envelope:validate'
                        }
                    }
                } finally {

                    if (runTests) {
                        junit healthScaleFactor: 20.0, testResults: '**/target/surefire-reports/*.xml'
                    }
                }
            }
        }

        if(!isPR() && isNotMaster()) {

            // Release a new private core signed war
            stage('Release') {
		cbpjcReleaseSign {
                    branchName = env.BRANCH_NAME
                    skipApproval = true
                }
            }

            // Generate a new PR against URR with bumped version
            stage('Bump version on URR') {
                pullRequest(
                    branchName: branchName,
                    destinationBranchName: urrBranch,
                    url: 'https://github.com/cloudbees/unified-release.git',
                    commands: commands,
                    message: 'Automated bump version',
                    token: token
                )
            }
        }
    }
}

def getToken(credentialId) {
    def credentials = com.cloudbees.plugins.credentials.CredentialsProvider.lookupCredentials(
        com.cloudbees.plugins.credentials.common.StandardUsernameCredentials.class,
        Jenkins.instance,
        null,
        null
    );
    for (c in credentials) {
        if (c.id == credentialId) {
            return c.password
        }
    }
    
    return null
}

boolean isPR() {
    return (env.BRANCH_NAME.startsWith('PR-') && env.CHANGE_TARGET != null)
}

boolean isNotMaster() {
    return (env.BRANCH_NAME.startsWith('cb-') && !env.BRANCH_NAME.equals('cb-master'))
}
