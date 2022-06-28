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


/**
 * Configuration factory for new CollectionConfigurations
 *
 * Usage:
 *      val collConfig = CollectionConfigurationFactory.create(...)
 */
val CollectionConfigurationFactory: CollectionConfiguration? = null

/**
 *
 * @see com.couchbase.lite.CollectionConfiguration
 */
fun CollectionConfiguration?.create(
    channels: List<String>?,
    documentIDs: List<String>?,
    pullFilter: ReplicationFilter?,
    pushFilter: ReplicationFilter?,
    conflictResolver: ConflictResolver?
): CollectionConfiguration {
    return CollectionConfiguration(
        channels ?: this?.channels,
        documentIDs ?: this?.documentIDs,
        pushFilter ?: this?.pushFilter,
        pullFilter ?: this?.pullFilter,
        conflictResolver ?: this?.conflictResolver
    )
}

/**
 * Configuration factory for new FullTextIndexConfigurations
 *
 * Usage:
 *
 *      val fullTextIndexConfig = FullTextIndexConfigurationFactory.create(...)
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
fun FullTextIndexConfiguration?.create(
    vararg expressions: String = emptyArray(),
    language: String? = null,
    ignoreAccents: Boolean? = null
) = FullTextIndexConfiguration(
    language ?: this?.language,
    ignoreAccents ?: this?.isIgnoringAccents() ?: false,
    if (!expressions.isEmpty()) expressions.asList() else this?.expressions
        ?: error("Must specify an expression")
)

/**
 * Configuration factory for new ValueIndexConfigurations
 *
 * Usage:
 *
 *     val valIndexConfig = ValueIndexConfigurationFactory.create(...)
 */
val ValueIndexConfigurationFactory: ValueIndexConfiguration? = null

/**
 * Create a FullTextIndexConfiguration, overriding the receiver's
 * values with the passed parameters:
 *
 * @param expressions (required) the expressions to be matched.
 *
 * @see com.couchbase.lite.ValueIndexConfiguration
 */
fun ValueIndexConfiguration?.create(
    vararg expressions: String = emptyArray()
) = ValueIndexConfiguration(
    if (!expressions.isEmpty()) expressions.asList() else this?.expressions
        ?: error("Must specify an expression")
)

/**
 * Configuration factory for new LogFileConfigurations
 *
 * Usage:
 *
 *      val logFileConfig = LogFileConfigurationFactory.create(...)
 */
val LogFileConfigurationFactory: LogFileConfiguration? = null

/**
 * Create a FullTextIndexConfiguration, overriding the receiver's
 * values with the passed parameters:
 *
 * @param directory (required) the directory in which the logs files are stored.
 * @param maxSize the max size of the log file in bytes.
 * @param maxRotateCount the number of rotated logs that are saved.
 * @param usePlainText whether or not to log in plaintext.
 *
 * @see com.couchbase.lite.LogFileConfiguration
 */
fun LogFileConfiguration?.create(
    directory: String? = null,
    maxSize: Long? = null,
    maxRotateCount: Int? = null,
    usePlainText: Boolean? = null
) = LogFileConfiguration(
    directory ?: this?.directory ?: error("Must specify a db directory"),
    maxSize ?: this?.maxSize,
    maxRotateCount ?: this?.maxRotateCount,
    usePlainText ?: this?.usesPlaintext(),
    true // always read only
)
