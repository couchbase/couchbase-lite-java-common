//
// Copyright (c) 2020, 2017 Couchbase, Inc All rights reserved.
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;


/**
 * Index for Full-Text search
 */
public class FullTextIndex extends Index {
    @NonNull
    private final List<FullTextIndexItem> indexItems;
    @NonNull
    private String language = Locale.getDefault().getLanguage();
    private boolean ignoreDiacritics;

    FullTextIndex(@NonNull FullTextIndexItem... indexItems) {
        super(IndexType.FULL_TEXT);
        this.indexItems = Arrays.asList(indexItems);
    }

    /**
     * The language code which is an ISO-639 language such as "en", "fr", etc.
     * Setting the language code affects how word breaks and word stems are parsed.
     * Without setting the value, the current locale's language will be used. Setting
     * a nil or "" value to disable the language features.
     */
    @NonNull
    public FullTextIndex setLanguage(@NonNull String language) {
        this.language = language;
        return this;
    }

    /**
     * Set the true value to ignore accents/diacritical marks. The default value is false.
     */
    @NonNull
    public FullTextIndex ignoreAccents(boolean ignoreAccents) {
        this.ignoreDiacritics = ignoreAccents;
        return this;
    }

    @NonNull
    @Override
    String getLanguage() { return language; }

    @Override
    boolean isIgnoringDiacritics() { return ignoreDiacritics; }

    @NonNull
    @Override
    List<Object> getJson() {
        final List<Object> items = new ArrayList<>();
        for (FullTextIndexItem item: indexItems) { items.add(item.expression.asJSON()); }
        return items;
    }
}
