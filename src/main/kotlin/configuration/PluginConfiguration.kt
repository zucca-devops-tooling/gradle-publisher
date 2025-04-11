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

import org.gradle.api.model.ObjectFactory
import javax.inject.Inject

open class PluginConfiguration
    @Inject
    constructor(
        objects: ObjectFactory,
    ) {
        val dev = objects.newInstance(RepositoryConfig::class.java)
        val prod = objects.newInstance(RepositoryConfig::class.java)

        fun dev(configure: RepositoryConfig.() -> Unit) {
            dev.configure()
        }

        fun prod(configure: RepositoryConfig.() -> Unit) {
            prod.configure()
        }

        // Shared fallback credentials
        var usernameProperty: String? = Defaults.USER_PROPERTY
        var passwordProperty: String? = Defaults.PASS_PROPERTY

        var gitFolder: String = Defaults.GIT_FOLDER
        var releaseBranchPatterns: List<String> = Defaults.RELEASE_BRANCH_REGEXES
    }
