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
            }
            steps {
                sh """
                    ./gradlew clean build test \
                        -PjfrogUser=$JFROG_CREDENTIALS_USR \
                        -PjfrogPassword=$JFROG_CREDENTIALS_PSW
                """
            }
        }

        stage('Publish to Artifactory') {
            environment {
                JFROG_CREDENTIALS = credentials('jfrog-user-pass')
            }
            steps {
                sh """
                    ./gradlew publish \
                        -PjfrogUser=$JFROG_CREDENTIALS_USR \
                        -PjfrogPassword=$JFROG_CREDENTIALS_PSW
                """
            }
        }
    }
}