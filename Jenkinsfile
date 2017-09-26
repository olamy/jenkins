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

properties([buildDiscarder(logRotator(numToKeepStr: '15', artifactNumToKeepStr: '15'))])

node('pct-agent-template') {
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
                        // See below for what this method does - we're passing an arbitrary environment
                        // variable to it so that JAVA_OPTS and MAVEN_OPTS are set correctly.
                        List mvnEnv = ["JAVA_OPTS=-Xmx1536m -Xms512m", "MAVEN_OPTS=-Xmx1536m -Xms512m"]

                        // Invoke the maven run within the environment we've created
                        withEnv(mvnEnv) {
                            // -Dmaven.repo.local=… tells Maven to create a subdir in the temporary directory for the local Maven repository
                            def mvnCmd = "mvn -Pdebug -U clean verify ${runTests ? '-Dmaven.test.failure.ignore' : '-DskipTests'} -V -B -Dmaven.repo.local=${pwd tmp: true}/m2repo -s settings.xml -e"
                            sh mvnCmd
                        }
                    }
                } finally {
                    if (runTests) {
                        junit healthScaleFactor: 20.0, testResults: '**/target/surefire-reports/*.xml'
                    }
                }
            }
        }
    }
}
