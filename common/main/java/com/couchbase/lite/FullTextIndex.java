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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import com.couchbase.lite.internal.utils.StringUtils;


/**
 * Index for Full-Text search
 */
public class FullTextIndex extends Index {
    @NonNull
    private final List<FullTextIndexItem> indexItems;
    @Nullable
    private String language = Locale.getDefault().getLanguage();
    private boolean ignoreDiacritics;

    FullTextIndex(@NonNull FullTextIndexItem... indexItems) {
        super(IndexType.FULL_TEXT);
        this.indexItems = Arrays.asList(indexItems);
    }

    /**
     * The language code which is an ISO-639 language such as "en", "fr", etc.
     * Setting the language code affects how word breaks and word stems are parsed.
     * If not explicitly set, the current locale's language will be used. Setting
     * a null, empty, or unrecognized value will disable the language features.
     */
    @NonNull
    public FullTextIndex setLanguage(@Nullable String language) {
        this.language = (StringUtils.isEmpty(language)) ? null : language;
        return this;
    }

    /**
     * Set true to ignore accents/diacritical marks. The default is false.
     */
    @NonNull
    public FullTextIndex ignoreAccents(boolean ignoreAccents) {
        this.ignoreDiacritics = ignoreAccents;
        return this;
    }

    @Nullable
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
