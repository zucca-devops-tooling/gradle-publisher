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
        stage('Publish to Artifactory') {
            environment {
                JFROG_CREDENTIALS = credentials('jfrog-user-pass')
                OSSRH_CREDENTIALS = credentials('OSSRH_CREDENTIALS')
                GPG_SECRET_KEY = credentials('GPG_SECRET_KEY')
                GPG_KEY_ID = credentials('GPG_KEY_ID')
                GPG_KEY_PASS = credentials('GPG_KEY_PASS')
            }
            steps {
                sh """
                    ./gradlew publish --info \
                        -PjfrogUser=$JFROG_CREDENTIALS_USR \
                        -PjfrogPassword=$JFROG_CREDENTIALS_PSW \
                        -PossrhUser=$OSSRH_CREDENTIALS_USR \
                        -PossrhPassword=$OSSRH_CREDENTIALS_PSW
                """
            }
        }
    }
}