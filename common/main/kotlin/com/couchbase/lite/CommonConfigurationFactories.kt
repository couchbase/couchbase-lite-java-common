//
// Copyright (c) 2021 Couchbase, Inc All rights reserved.
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


val IndexConfigurationFactory: IndexConfiguration? = null
fun IndexConfiguration?.create(
    type: AbstractIndex.IndexType? = null,
    expressions: List<String>? = null
) = IndexConfiguration(
    type ?: this?.indexType,
    expressions ?: this?.expressions
)

val LogFileConfigurationFactory: LogFileConfiguration? = null
fun LogFileConfiguration?.create(
    directory: String? = null,
    maxSize: Long? = null,
    maxRotateCount: Int? = null,
    usePlainText: Boolean? = null
) = LogFileConfiguration(
    directory ?: this?.directory ?: error("Must specify a database"),
    maxSize ?: this?.maxSize,
    maxRotateCount ?: this?.maxRotateCount,
    usePlainText ?: this?.usesPlaintext(),
    true
)

val ValueIndexConfigurationFactory: ValueIndexConfiguration? = null
fun ValueIndexConfiguration?.create(
    expressions: List<String>? = null
) = ValueIndexConfiguration(
    expressions ?: this?.expressions
)
