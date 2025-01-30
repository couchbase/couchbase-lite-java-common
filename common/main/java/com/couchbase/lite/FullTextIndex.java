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

import com.couchbase.lite.internal.QueryLanguage;
import com.couchbase.lite.internal.core.C4Collection;
import com.couchbase.lite.internal.utils.StringUtils;


/**
 * Index for Full-Text search
 */
public final class FullTextIndex extends Index {
    @NonNull
    private final List<FullTextIndexItem> indexItems;
    @Nullable
    private String language = Locale.getDefault().getLanguage();
    private boolean ignoreDiacrits = Defaults.FullTextIndex.IGNORE_ACCENTS;

    FullTextIndex(@NonNull FullTextIndexItem... indexItems) {
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

    @Nullable
    public String getLanguage() { return language; }

    /**
     * Set true to ignore accents/diacritical marks. The default is false.
     */
    @NonNull
    public FullTextIndex ignoreAccents(boolean ignoreAccents) {
        this.ignoreDiacrits = ignoreAccents;
        return this;
    }

    public boolean isIgnoringAccents() { return ignoreDiacrits; }

    @NonNull
    @Override
    List<Object> getJson() {
        final List<Object> items = new ArrayList<>();
        for (FullTextIndexItem item: indexItems) { items.add(item.expression.asJSON()); }
        return items;
    }

    @Override
    void createIndex(@NonNull String name, @NonNull C4Collection c4Collection)
        throws LiteCoreException, CouchbaseLiteException {
        c4Collection
            .createFullTextIndex(name, QueryLanguage.JSON.getCode(), getIndexSpec(), language, ignoreDiacrits, null);
    }
}
