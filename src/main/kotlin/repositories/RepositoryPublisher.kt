package dev.zucca_ops.repositories

import org.gradle.api.publish.PublishingExtension

interface RepositoryPublisher {
    fun configurePublishingRepository(publishingExtension: PublishingExtension)

    fun shouldPublish(): Boolean
}