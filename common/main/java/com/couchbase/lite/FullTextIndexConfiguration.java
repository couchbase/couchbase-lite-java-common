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
package com.couchbase.lite;


import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;


public class FullTextIndexConfiguration extends IndexConfiguration {
    private String textLanguage = Locale.getDefault().getLanguage();
    private boolean ignoreDiacritics;

    public FullTextIndexConfiguration(@NonNull String... expressions) {
        super(IndexType.FULL_TEXT, Arrays.asList(expressions));
    }

    FullTextIndexConfiguration(@NonNull List<String> expressions) { super(IndexType.FULL_TEXT, expressions); }

    /**
     * The language code which is an ISO-639 language such as "en", "fr", etc.
     * Setting the language code affects how word breaks and word stems are parsed.
     * Without setting the value, the current locale's language will be used. Setting
     * a null or "" value to disable the language features.
     */
    @NonNull
    public FullTextIndexConfiguration setLanguage(@Nullable String language) {
        this.textLanguage = language;
        return this;
    }

    /**
     * Set the true value to ignore accents/diacritical marks. The default value is false.
     */
    @NonNull
    public FullTextIndexConfiguration ignoreAccents(boolean ignoreAccents) {
        this.ignoreDiacritics = ignoreAccents;
        return this;
    }

    @NonNull
    @Override
    String getLanguage() { return textLanguage; }

    @Override
    boolean isIgnoringDiacritics() { return ignoreDiacritics; }
}
