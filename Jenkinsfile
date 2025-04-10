pipeline {
    agent any

    environment {
        GRADLE_OPTS = '-Dorg.gradle.jvmargs="-Xmx2g -XX:+HeapDumpOnOutOfMemoryError"'
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Build & Test') {
            steps {

                withCredentials([
                    file(credentialsId: 'GPG_SECRET_KEY', variable: 'GPG_ASC_PATH'),
                    string(credentialsId: 'GPG_KEY_ID', variable: 'GPG_KEY_ID'),
                    string(credentialsId: 'GPG_KEY_PASS', variable: 'GPG_KEY_PASS'),
                    usernamePassword(credentialsId: 'jfrog-credentials', usernameVariable: 'JFROG_CREDENTIALS_USR', passwordVariable: 'JFROG_CREDENTIALS_PSW'),
                    usernamePassword(credentialsId: 'OSSRH_CREDENTIALS', usernameVariable: 'OSSRH_USER', passwordVariable: 'OSSRH_PASS')
                ]) {
                    sh '''
                        echo "[DEBUG] GPG_ASC_PATH: $GPG_ASC_PATH"
                        echo "[DEBUG] GPG_KEY_ID is set: ${GPG_KEY_ID:+yes}"
                        echo "[DEBUG] GPG_KEY_PASS is set: ${GPG_KEY_PASS:+yes}"
                        echo "[DEBUG] OSSRH_USER is set: ${OSSRH_USER:+yes}"
                        echo "[DEBUG] OSSRH_PASS is set: ${OSSRH_PASS:+yes}"
                        echo "[DEBUG] JFROG_CREDENTIALS_USR is set: ${JFROG_CREDENTIALS_USR:+yes}"
                        echo "[DEBUG] JFROG_CREDENTIALS_PSW is set: ${JFROG_CREDENTIALS_PSW:+yes}"
                    '''
                    sh """
                        ./gradlew clean build test --refresh-dependencies \
                            -PjfrogUser=$JFROG_CREDENTIALS_USR \
                            -PjfrogPassword=$JFROG_CREDENTIALS_PSW \
                            -PmavenCentralUsername=$OSSRH_USER \
                            -PmavenCentralPassword=$OSSRH_PASS \
                            -Psigning.secretKeyFile=$GPG_ASC_PATH \
                            -Psigning.password=$GPG_KEY_PASS \
                            -Psigning.keyId=$GPG_KEY_ID \

                    """
                }
            }
        }
        stage('Publish to Maven Central') {
            steps {
                withCredentials([
                    file(credentialsId: 'GPG_SECRET_KEY', variable: 'GPG_ASC_PATH'),
                    string(credentialsId: 'GPG_KEY_ID', variable: 'GPG_KEY_ID'),
                    string(credentialsId: 'GPG_KEY_PASS', variable: 'GPG_KEY_PASS'),
                    usernamePassword(credentialsId: 'jfrog-credentials', usernameVariable: 'JFROG_CREDENTIALS_USR', passwordVariable: 'JFROG_CREDENTIALS_PSW'),
                    usernamePassword(credentialsId: 'OSSRH_CREDENTIALS', usernameVariable: 'OSSRH_USER', passwordVariable: 'OSSRH_PASS')
                ]) {
                    sh """
                        ./gradlew publish publishToMavenCentralPortal --info \
                            -Psigning.secretKeyFile=$GPG_ASC_PATH \
                            -Psigning.password=$GPG_KEY_PASS \
                            -Psigning.keyId=$GPG_KEY_ID \
                            -PmavenCentralUsername=$OSSRH_USER \
                            -PmavenCentralPassword=$OSSRH_PASS \
                            -PjfrogUser=$JFROG_CREDENTIALS_USR \
                            -PjfrogPassword=$JFROG_CREDENTIALS_PSW
                    """
                }
            }
        }
    }
}