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
                    usernamePassword(credentialsId: 'jfrog-credentials', usernameVariable: 'JFROG_USER', passwordVariable: 'JFROG_PASS'),
                    usernamePassword(credentialsId: 'OSSRH_CREDENTIALS', usernameVariable: 'OSSRH_USER', passwordVariable: 'OSSRH_PASS')
                ]) {
                     sh """#!/bin/bash

                         ./gradlew clean build --refresh-dependencies --info \\
                             -PmavenCentralUsername=\$OSSRH_USER \\
                             -PmavenCentralPassword=\$OSSRH_PASS \\
                             -PjfrogUser=\$JFROG_USER \\
                             -PjfrogPassword=\$JFROG_PASS
                     """
                }
            }
        }
        stage('Publish to Maven Central') {
            steps {
                withCredentials([
                    file(credentialsId: 'GPG_SECRET_KEY', variable: 'GPG_KEY_PATH'),
                    string(credentialsId: 'GPG_KEY_ID', variable: 'GPG_KEY_ID'),
                    string(credentialsId: 'GPG_KEY_PASS', variable: 'GPG_KEY_PASS'),
                    usernamePassword(credentialsId: 'jfrog-credentials', usernameVariable: 'JFROG_USER', passwordVariable: 'JFROG_PASS'),
                    usernamePassword(credentialsId: 'OSSRH_CREDENTIALS', usernameVariable: 'OSSRH_USER', passwordVariable: 'OSSRH_PASS')
                ]) {
                    sh """#!/bin/bash
                        set -euo pipefail

                        echo "🔐 Reading secret key into memory..."
                        export GPG_ASC_ARMOR="\$(cat \$GPG_KEY_PATH)"


                        ./gradlew publish publishToMavenCentralPortal --info \\
                            "-Psigning.keyId=\$GPG_KEY_ID" \\
                            "-Psigning.password=\$GPG_KEY_PASS" \\
                            "-PmavenCentralUsername=\$OSSRH_USER" \\
                            "-PmavenCentralPassword=\$OSSRH_PASS" \\
                            "-PjfrogUser=\$JFROG_USER" \\
                            "-PjfrogPassword=\$JFROG_PASS"
                    """
                }
            }
        }
    }
}