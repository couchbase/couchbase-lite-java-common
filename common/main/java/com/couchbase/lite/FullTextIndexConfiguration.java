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
package com.couchbase.lite;


import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import com.couchbase.lite.internal.QueryLanguage;
import com.couchbase.lite.internal.core.C4Collection;
import com.couchbase.lite.internal.utils.StringUtils;


/**
 * Full Text Index Configuration
 */
public final class FullTextIndexConfiguration extends IndexConfiguration {
    @Nullable
    private String language = Locale.getDefault().getLanguage();
    private boolean ignoreDiacrits = Defaults.FullTextIndex.IGNORE_ACCENTS;

    public FullTextIndexConfiguration(@NonNull String... expressions) { this(Arrays.asList(expressions)); }

    FullTextIndexConfiguration(@NonNull List<String> expressions) { super(expressions); }

    /**
     * The language code which is an ISO-639 language such as "en", "fr", etc.
     * Setting the language code affects how word breaks and word stems are parsed.
     * If not explicitly set, the current locale's language will be used. Setting
     * a null, empty, or unrecognized value will disable the language features.
     */
    @NonNull
    public FullTextIndexConfiguration setLanguage(@Nullable String language) {
        this.language = (StringUtils.isEmpty(language)) ? null : language;
        return this;
    }

    @Nullable
    public String getLanguage() { return language; }

    /**
     * Set the true value to ignore accents/diacritical marks. The default value is false.
     */
    @NonNull
    public FullTextIndexConfiguration ignoreAccents(boolean ignoreAccents) {
        this.ignoreDiacrits = ignoreAccents;
        return this;
    }

    public boolean isIgnoringAccents() { return ignoreDiacrits; }

    @Override
    void createIndex(@NonNull String name, @NonNull C4Collection c4Collection) throws LiteCoreException {
        c4Collection.createFullTextIndex(name, QueryLanguage.N1QL.getCode(), getIndexSpec(), language, ignoreDiacrits);
    }
}
