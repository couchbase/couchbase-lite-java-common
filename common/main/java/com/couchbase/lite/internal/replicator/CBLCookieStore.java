//
// Copyright (c) 2020 Couchbase, Inc.
//
// Licensed under the Couchbase License Agreement (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
// https://info.couchbase.com/rs/302-GJY-034/images/2017-10-30_License_Agreement.pdf
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
package com.couchbase.lite.internal.replicator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.net.URI;
import java.util.List;


/**
 * Interface for CookieStore
 */
public interface CBLCookieStore {
    void setCookies(@NonNull URI uri, @NonNull List<String> cookies, boolean acceptParentDomain);

    @Nullable
    String getCookies(@NonNull URI uri);
}
