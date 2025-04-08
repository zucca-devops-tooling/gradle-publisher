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
            environment {
                JFROG_CREDENTIALS = credentials('jfrog-user-pass')
                OSSRH_CREDENTIALS = credentials('OSSRH_CREDENTIALS')
            }
            steps {
                sh """
                    ./gradlew clean build test --refresh-dependencies \
                        -PjfrogUser=$JFROG_CREDENTIALS_USR \
                        -PjfrogPassword=$JFROG_CREDENTIALS_PSW \
                        -PossrhUser=$OSSRH_CREDENTIALS_USR \
                        -PossrhPassword=$OSSRH_CREDENTIALS_PSW

                """
            }
        }
        stage('Publish to Maven Central') {
            steps {
                withCredentials([
                    file(credentialsId: 'GPG_SECRET_KEY', variable: 'GPG_FILE_PATH'),
                    string(credentialsId: 'GPG_KEY_ID', variable: 'GPG_KEY_ID'),
                    string(credentialsId: 'GPG_KEY_PASS', variable: 'GPG_KEY_PASS'),
                    usernamePassword(credentialsId: 'jfrog-user-pass', usernameVariable: 'JFROG_CREDENTIALS_USR', passwordVariable: 'JFROG_CREDENTIALS_PSW'),
                    usernamePassword(credentialsId: 'OSSRH_CREDENTIALS', usernameVariable: 'OSSRH_USER', passwordVariable: 'OSSRH_PASS')
                ]) {
                    sh """
                        ./gradlew publish --info \
                            -PjfrogUser=$JFROG_CREDENTIALS_USR \
                            -PjfrogPassword=$JFROG_CREDENTIALS_PSW \
                            -Psigning.keyId=$GPG_KEY_ID \
                            -Psigning.password=$GPG_KEY_PASS \
                            -Psigning.secretKeyRingFile=$GPG_FILE_PATH \
                            -PmavenCentralUsername=$OSSRH_USER \
                            -PmavenCentralPassword=$OSSRH_PASS
                    """
                }
            }
        }
    }
}