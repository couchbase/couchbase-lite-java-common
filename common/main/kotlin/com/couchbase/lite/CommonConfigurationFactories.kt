//
// Copyright (c) 2020 Couchbase, Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
package com.couchbase.lite

import com.couchbase.lite.internal.getCollectionConfigs
import com.couchbase.lite.internal.logging.Log


/**
 * Configuration factory for new CollectionConfigurations
 *
 * Usage:
 *      val collConfig = CollectionConfigurationFactory.newConfig(...)
 */
val CollectionConfigurationFactory: CollectionConfiguration? = null

/**
 *
 * @see com.couchbase.lite.CollectionConfiguration
 */
fun CollectionConfiguration?.newConfig(
    channels: List<String>? = null,
    documentIDs: List<String>? = null,
    pullFilter: ReplicationFilter? = null,
    pushFilter: ReplicationFilter? = null,
    conflictResolver: ConflictResolver? = null
): CollectionConfiguration {
    val config = CollectionConfiguration()

    (channels ?: this?.channels)?.let { config.channels = it }
    (documentIDs ?: this?.documentIDs)?.let { config.documentIDs = it }
    (pushFilter ?: this?.pushFilter)?.let { config.pushFilter = it }
    (pullFilter ?: this?.pullFilter)?.let { config.pullFilter = it }
    (conflictResolver ?: this?.conflictResolver)?.let { config.conflictResolver = it }

    return config
}

/**
 * Configuration factory for new FullTextIndexConfigurations
 *
 * Usage:
 *      val fullTextIndexConfig = FullTextIndexConfigurationFactory.newConfig(...)
 */
val FullTextIndexConfigurationFactory: FullTextIndexConfiguration? = null

/**
 * Create a FullTextIndexConfiguration, overriding the receiver's
 * values with the passed parameters:
 *
 * @param expressions (required) the expressions to be matched.
 *
 * @see com.couchbase.lite.FullTextIndexConfiguration
 */
fun FullTextIndexConfiguration?.newConfig(
    vararg expressions: String = emptyArray(),
    language: String? = null,
    ignoreAccents: Boolean? = null
): FullTextIndexConfiguration {
    val config = FullTextIndexConfiguration(
        (if (expressions.isNotEmpty()) expressions.asList() else this?.expressions)?.toMutableList()
            ?: throw IllegalArgumentException("A FullTextIndexConfiguration must specify expressions")
    )

    (language ?: this?.language)?.let { config.language = language }
    (ignoreAccents ?: this?.isIgnoringAccents)?.let { config.ignoreAccents(it) }

    return config
}

/**
 * Configuration factory for new ValueIndexConfigurations
 *
 * Usage:
 *     val valIndexConfig = ValueIndexConfigurationFactory.newConfig(...)
 */
val ValueIndexConfigurationFactory: ValueIndexConfiguration? = null

/**
 * Create a ValueIndexConfiguration, overriding the receiver's
 * values with the passed parameters:
 *
 * @param expressions (required) the expressions to be matched.
 *
 * @see com.couchbase.lite.ValueIndexConfiguration
 */
fun ValueIndexConfiguration?.newConfig(vararg expressions: String = emptyArray()) = ValueIndexConfiguration(
    (if (expressions.isNotEmpty()) expressions.asList() else this?.expressions)?.toMutableList()
        ?: throw IllegalArgumentException("A ValueIndexConfiguration must specify expressions")
)

/**
 * Configuration factory for new LogFileConfigurations
 *
 * Usage:
 *      val logFileConfig = LogFileConfigurationFactory.newConfig(...)
 */
val LogFileConfigurationFactory: LogFileConfiguration? = null

/**
 * Create a LogFileConfiguration, overriding the receiver's
 * values with the passed parameters:
 *
 * @param directory (required) the directory in which the logs files are stored.
 * @param maxSize the max size of the log file in bytes.
 * @param maxRotateCount the number of rotated logs that are saved.
 * @param usePlainText whether or not to log in plaintext.
 *
 * @see com.couchbase.lite.LogFileConfiguration
 */
fun LogFileConfiguration?.newConfig(
    directory: String? = null,
    maxSize: Long? = null,
    maxRotateCount: Int? = null,
    usePlainText: Boolean? = null
): LogFileConfiguration {
    val config = LogFileConfiguration(
        directory ?: this?.directory
        ?: throw IllegalArgumentException("A LogFileConfiguration must specify a directory")
    )

    (maxSize ?: this?.maxSize)?.let { config.maxSize = it }
    (maxRotateCount ?: this?.maxRotateCount)?.let { config.maxRotateCount = it }
    (usePlainText ?: this?.usesPlaintext())?.let { config.setUsePlaintext(it) }

    return config
}

/**
 * Create a FullTextIndexConfiguration, overriding the receiver's
 * values with the passed parameters:
 *
 * @param expressions (required) the expressions to be matched.
 *
 * @see com.couchbase.lite.FullTextIndexConfiguration
 * @deprecated Use FullTextIndexConfiguration?.newConfig(vararg expressions: String, language: String?, ignoreAccents: Boolean?)
 */
@Deprecated(
    "Use FullTextIndexConfiguration?.newConfig(vararg expressions: String, language: String?, ignoreAccents: Boolean?)",
    replaceWith = ReplaceWith("FullTextIndexConfiguration?.newConfig(vararg expressions: String, language: String?, ignoreAccents: Boolean?)")
)
fun FullTextIndexConfiguration?.create(
    vararg expressions: String = emptyArray(),
    language: String? = null,
    ignoreAccents: Boolean? = null
) = this.newConfig(*expressions, language = language, ignoreAccents = ignoreAccents)

/**
 * Create a ValueIndexConfiguration, overriding the receiver's
 * values with the passed parameters:
 *
 * @param expressions (required) the expressions to be matched.
 *
 * @see com.couchbase.lite.ValueIndexConfiguration
 * @deprecated Use ValueIndexConfiguration?.newConfig(vararg expressions: String)
 */
@Deprecated(
    "Use ValueIndexConfiguration?.newConfig(vararg expressions: String)",
    replaceWith = ReplaceWith("ValueIndexConfiguration?.newConfig(vararg expressions: String)")
)
fun ValueIndexConfiguration?.create(vararg expressions: String = emptyArray()) = this.newConfig(*expressions)

/**
 * Create a LogFileConfiguration, overriding the receiver's
 * values with the passed parameters:
 *
 * @param directory (required) the directory in which the logs files are stored.
 * @param maxSize the max size of the log file in bytes.
 * @param maxRotateCount the number of rotated logs that are saved.
 * @param usePlainText whether or not to log in plaintext.
 *
 * @see com.couchbase.lite.LogFileConfiguration
 * @deprecated Use LogFileConfiguration?.newConfig(String?, Long?, Int?, Boolean?)
 */
@Deprecated(
    "Use LogFileConfiguration?.newConfig(String?, Long?, Int?, Boolean?)",
    replaceWith = ReplaceWith("LogFileConfiguration?.newConfig(String?, Long?, Int?, Boolean?)")
)
fun LogFileConfiguration?.create(
    directory: String? = null,
    maxSize: Long? = null,
    maxRotateCount: Int? = null,
    usePlainText: Boolean? = null
) = this.newConfig(directory, maxSize, maxRotateCount, usePlainText)


// If the source config contains anything other than exactly the
// database default collection, we are about to lose information.
internal fun checkDbCollections(db: Database, collections: Set<Collection>?) {
    val colls = collections ?: emptySet()
    if ((colls.size != 1) || (!colls.contains(db.defaultCollection))) {
        Log.d(LogDomain.LISTENER, "Copy to deprecated config loses collection information")
    }
}

internal fun copyReplConfig(
    src: ReplicatorConfiguration?,
    dst: ReplicatorConfiguration,
    type: ReplicatorType?,
    continuous: Boolean?,
    authenticator: Authenticator?,
    headers: Map<String, String>?,
    maxAttempts: Int?,
    maxAttemptWaitTime: Int?,
    heartbeat: Int?,
    enableAutoPurge: Boolean?,
    acceptParentDomainCookies: Boolean?,
) {
    (type ?: src?.type)?.let { dst.setType(it) }
    (continuous ?: src?.isContinuous)?.let { dst.setContinuous(it) }
    (authenticator ?: src?.authenticator)?.let { dst.setAuthenticator(it) }
    (headers ?: src?.headers)?.let { dst.headers = it }
    (maxAttempts ?: src?.maxAttempts)?.let { dst.maxAttempts = it }
    (maxAttemptWaitTime ?: src?.maxAttemptWaitTime)?.let { dst.maxAttemptWaitTime = it }
    (heartbeat ?: src?.heartbeat)?.let { dst.heartbeat = it }
    (enableAutoPurge ?: src?.isAutoPurgeEnabled)?.let { dst.setAutoPurgeEnabled(it) }
    (acceptParentDomainCookies ?: src?.isAcceptParentDomainCookies)?.let { dst.setAcceptParentDomainCookies(it) }
}

@Suppress("DEPRECATION")
internal fun copyLegacyReplConfig(
    src: ReplicatorConfiguration?,
    dst: ReplicatorConfiguration,
    pinnedServerCertificate: ByteArray?,
    channels: List<String>?,
    documentIDs: List<String>?,
    pushFilter: ReplicationFilter?,
    pullFilter: ReplicationFilter?,
    conflictResolver: ConflictResolver?
) {
    (pinnedServerCertificate ?: src?.pinnedServerCertificate)?.let { dst.setPinnedServerCertificate(it) }

    // copy the default collection configuration, if it exists
    val srcConfig = src?.database?.defaultCollection?.let { getCollectionConfigs(src)?.get(it) }
    (channels ?: srcConfig?.channels)?.let { dst.channels = it }
    (documentIDs ?: srcConfig?.documentIDs)?.let { dst.documentIDs = it }
    (pushFilter ?: srcConfig?.pushFilter)?.let { dst.pushFilter = it }
    (pullFilter ?: srcConfig?.pullFilter)?.let { dst.pullFilter = it }
    (conflictResolver ?: srcConfig?.conflictResolver)?.let { dst.conflictResolver = it }
}
