//
// Copyright (c) 2023 Couchbase, Inc All rights reserved.
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
package com.couchbase.lite.internal.logging;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import com.couchbase.lite.LogDomain;
import com.couchbase.lite.LogLevel;
import com.couchbase.lite.internal.utils.Preconditions;


public abstract class AbstractLogSink {
    @NonNull
    protected static Set<LogDomain> aggregateDomains(@NonNull LogDomain domain1, @Nullable LogDomain... domains) {
        final Set<LogDomain> set = new HashSet<>();
        set.add(domain1);
        if (domains != null) { set.addAll(Arrays.asList(domains)); }
        return set;
    }

    @NonNull
    protected static String listDomains(@NonNull Set<LogDomain> domains) {
        final StringBuilder domainStr = new StringBuilder("[");
        boolean first = true;
        for (LogDomain domain: domains) {
            if (!first) { domainStr.append(", "); }
            domainStr.append(domain);
            first = false;
        }
        return domainStr.append(']').toString();
    }


    private final LogLevel level;
    private final Set<LogDomain> domains;

    // Base constructor.  A Logger has its filter set for life
    protected AbstractLogSink(@NonNull LogLevel level, @NonNull Set<LogDomain> domains) {
        this.level = Preconditions.assertNotNull(level, "level");
        this.domains = Preconditions.assertNotNull(domains, "domains");
    }

    @NonNull
    public final LogLevel getLevel() { return level; }

    @NonNull
    public final Set<LogDomain> getDomains() { return new HashSet<>(domains); }

    protected abstract void writeLog(@NonNull LogLevel level, @NonNull LogDomain domain, @NonNull String message);

    protected final void log(@NonNull LogLevel level, @NonNull LogDomain domain, @NonNull String message) {
        if ((this.level.compareTo(level) <= 0) && domains.contains(domain)) { writeLog(level, domain, message); }
    }

    protected final boolean similarLevels(@NonNull AbstractLogSink other) { return level == other.level; }

    protected final boolean similarDomains(@NonNull AbstractLogSink other) {
        return (domains.size() == other.domains.size()) && domains.containsAll(other.domains);
    }

    protected boolean isLegacy() { return false; }
}
