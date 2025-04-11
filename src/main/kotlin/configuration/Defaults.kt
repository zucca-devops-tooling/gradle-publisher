/*
 * Copyright 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.zuccaops.configuration

object Defaults {
    const val TARGET = "local"
    const val USER_PROPERTY = "mavenUsername"
    const val PASS_PROPERTY = "mavenPassword"
    const val GIT_FOLDER = "."
    val RELEASE_BRANCH_REGEXES =
        listOf(
            """^release/\d+\.\d+\.\d+$""",
            """^v\d+\.\d+\.\d+$""",
        )
}
