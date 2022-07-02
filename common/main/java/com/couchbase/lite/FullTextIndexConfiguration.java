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

import com.couchbase.lite.internal.utils.StringUtils;


/**
 * Full Text Index Configuration
 */
public class FullTextIndexConfiguration extends IndexConfiguration {
    @Nullable
    private String language = Locale.getDefault().getLanguage();
    private boolean ignoreDiacrits;

    public FullTextIndexConfiguration(@NonNull String... expressions) { this(Arrays.asList(expressions)); }

    FullTextIndexConfiguration(@NonNull List<String> expressions) { super(IndexType.FULL_TEXT, expressions); }

    // For Kotlin
    FullTextIndexConfiguration(
        @Nullable String language,
        boolean ignoreDiacritics,
        @NonNull List<String> expressions) {
        super(IndexType.FULL_TEXT, expressions);
        this.language = language;
        this.ignoreDiacrits = ignoreDiacritics;
    }

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
    @Override
    public String getLanguage() { return language; }

    /**
     * Set the true value to ignore accents/diacritical marks. The default value is false.
     */
    @NonNull
    public FullTextIndexConfiguration ignoreAccents(boolean ignoreAccents) {
        this.ignoreDiacrits = ignoreAccents;
        return this;
    }

    @Override
    public boolean isIgnoringAccents() { return ignoreDiacrits; }
}
