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

// Private core jenkins branch to release private signed war
def branch = env.BRANCH_NAME

// URR bump version and automated RFC pull request creation
def urrBranch = "stable-"
def urrBranchName = UUID.randomUUID().toString()
def cred = env.GITHUB_CREDENTIALS
def token = getToken(cred)

// Jenkins version
def jenkinsVersion = ""

// URR version
def urrVersion = ""

// Check if it is a release
def isRelease = false

// Bump commands
def commands = ""

// URR repo
def urrRepo = 'https://github.com/cloudbees/unified-release.git'

// Just to use the env variable when sending emails
def id = env.BUILD_NUMBER
def name = env.JOB_BASE_NAME

// Exclusion list of changes to abort
def exclusions = ["Jenkinsfile","README.md","NECTARIZE.md","CONTRIBUTING.md"]

// Abort flag based on check of changes against exclusion list
def abort = true;

properties([buildDiscarder(logRotator(numToKeepStr: '15', artifactNumToKeepStr: '15'))])

node('private-core-template-maven3.5.4') {
    try {
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
                                if (isPR()) {
                                    // Check changes against exclusion list
                                    sh """
                                        touch changes
                                        git diff remotes/origin/${env.CHANGE_TARGET} --name-only > changes
                                        less changes
                                    """
                                    def changes = readFile "changes"
                                    println exclusions
                                    changes.tokenize().each { change ->
                                        if (!exclusions.contains(change)) {
                                            println change
                                            abort = false
                                        }
                                    }

                                    if (abort) {
                                        println "Changes does not affect to perform a new release of private core"
                                        currentBuild.result = 'SUCCESS'
                                        return
                                    }

                                    sh """
                                        rm -rf changes
                                    """
                                }

                                // -Dmaven.repo.local=… tells Maven to create a subdir in the temporary directory for the local Maven repository
                                sh """
                                    mvn -Pdebug -U clean verify ${runTests ? '-Dmaven.test.failure.ignore' : '-DskipTests'} -V -B -Dmaven.repo.local=$m2repo -s settings.xml -e
                                    cp -a target/*.pom pom.xml
                                """
                                isRelease = ( sh(script: "git log --format=%s -1 | grep --fixed-string '[maven-release-plugin]'", returnStatus: true) == 0 )
                                def pom = readMavenPom()
                                jenkinsVersion = pom.version?.replaceAll('-SNAPSHOT', '')
                                urrBranch += jenkinsVersion.substring(0,5)
                                
                                if (jenkinsVersion.endsWith("cb-1")) {
                                    // there is a new core version, so to bump URR is needed
                                    urrVersion = jenkinsVersion.substring(0,7) + ".1-SNAPSHOT"
                                    commands = 'mvn versions:set-property -Dproperty=jenkins.version -DnewVersion=' + jenkinsVersion + ' && mvn versions:set -DnewVersion=' + urrVersion + ' && mvn envelope:validate'
                                    println "URR VERSION: " + urrVersion
                                } else {
                                    commands = 'mvn versions:set-property -Dproperty=jenkins.version -DnewVersion=' + jenkinsVersion + ' && mvn envelope:validate'
                                }

                                println "JENKINS VERSION: " + jenkinsVersion
                                println "URR BRANCH: " + urrBranch
                                println "COMMANDS: " + commands
                                println "RELEASE: " + isRelease
                            }
                        }
                    } finally {
                        if (runTests && !abort) {
                            junit healthScaleFactor: 20.0, testResults: '**/target/surefire-reports/*.xml'
                        }
                    }
                }
            }

            if(!isRelease && !isPR() && isCB()) {

                // Release a new private core signed war
                stage('Release') {
                   cbpjcReleaseSign {
                        branchName = branch
                        skipApproval = true
                   }
                }

                // Generate a new PR against URR with bumped version
                stage('Bump version on URR') {
                   pullRequest(
                        branchName: urrBranchName,
                        destinationBranchName: urrBranch,
                        url: urrRepo,
                        commands: commands,
                        message: 'Automated bump version',
                        token: token
                    )
                }
            }
        }
    } finally {
        if (currentBuild.result == 'UNSTABLE') {
            email(id,name)
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

boolean isCB() {
    return (env.BRANCH_NAME.startsWith('cb-') && !env.BRANCH_NAME.equals('cb-master'))
}

def email(id, name) {
    emailNotification {
        recipient          = 'release-team-notifications@cloudbees.com'
        subject            = "Private Jenkins builder failed - ${name} #${id}"
        template           = 'reporting-job'
        placeholders       = [
            description: "Please have a look at the following job:"
        ]
    }
}
