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
package com.couchbase.lite.internal.utils;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;


public final class JSONUtils {
    private JSONUtils() { }

    private static final ThreadLocal<SimpleDateFormat> DATE_FORMAT = new ThreadLocal<SimpleDateFormat>() {
        protected synchronized SimpleDateFormat initialValue() {
            final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ENGLISH);
            sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
            return sdf;
        }
    };

    @SuppressWarnings("PMD.AvoidStringBufferField")
    public static class Marshaller {
        private final StringBuilder buf = new StringBuilder();

        public Marshaller writeValue(@Nullable Object val) throws JSONException {
            if (val == null) { writeNull(); }
            else if (val instanceof Boolean) { writeBoolean((Boolean) val); }
            else if (val instanceof Number) { writeNumber((Number) val); }
            else if (val instanceof String) { writeString((String) val); }
            else if (val instanceof Date) { writeString(toJSON((Date) val)); }
            else if (val instanceof List<?>) { writeArray((List<?>) val); }
            else if (val instanceof Map<?, ?>) { writeMap((Map<?, ?>) val); }
            return this;
        }

        public Marshaller writeArray(@Nullable List<?> list) throws JSONException {
            if (list == null) {
                writeValue(null);
                return this;
            }

            boolean first = true;
            startArray();
            for (Object item: list) {
                if (first) { first = false; }
                else { nextMember(); }
                writeValue(item);
            }
            endArray();

            return this;
        }

        public Marshaller writeMap(@Nullable Map<?, ?> map) throws JSONException {
            if (map == null) {
                writeValue(null);
                return this;
            }

            boolean first = true;
            startObject();
            for (Map.Entry<?, ?> entry: map.entrySet()) {
                final Object k = entry.getKey();
                if (k == null) { throw new JSONException("Object key is null"); }

                if (first) { first = false; }
                else { nextMember(); }

                writeKey(k.toString());
                writeValue(entry.getValue());
            }
            endObject();

            return this;
        }

        public Marshaller startObject() {
            buf.append('{');
            return this;
        }

        public Marshaller writeKey(@Nullable String key) {
            buf.append('"').append(key).append("\":");
            return this;
        }

        public Marshaller endObject() {
            buf.append('}');
            return this;
        }

        public Marshaller startArray() {
            buf.append('[');
            return this;
        }

        public Marshaller endArray() {
            buf.append(']');
            return this;
        }

        public Marshaller nextMember() {
            buf.append(',');
            return this;
        }

        public Marshaller writeJSON(String val) {
            buf.append(val);
            return this;
        }

        public Marshaller writeString(String val) {
            buf.append('"').append(val).append('"');
            return this;
        }

        public Marshaller writeNumber(Number val) {
            buf.append(val);
            return this;
        }

        public Marshaller writeBoolean(Boolean val) {
            buf.append((val) ? "true" : "false");
            return this;
        }

        public Marshaller writeNull() {
            buf.append("null");
            return this;
        }

        @NonNull
        public String toString() { return buf.toString(); }
    }

    public static JSONObject toJSON(Map<?, ?> map) throws JSONException {
        if (map == null) { return null; }

        final JSONObject json = new JSONObject();
        for (Map.Entry<?, ?> entry: map.entrySet()) {
            final Object k = entry.getKey();
            if (k == null) { throw new JSONException("Object key is null"); }
            final String key = k.toString();
            final Object val = entry.getValue();
            if (val == null) { json.put(key, JSONObject.NULL); }
            else if (val instanceof Map<?, ?>) { json.put(key, toJSON((Map<?, ?>) val)); }
            else if (val instanceof List<?>) { json.put(key, toJSON((List<?>) val)); }
            else { json.put(key, val); }
        }

        return json;
    }

    public static JSONArray toJSON(List<?> list) throws JSONException {
        if (list == null) { return null; }

        final JSONArray json = new JSONArray();
        for (Object value: list) {
            if (value == null) { json.put(JSONObject.NULL); }
            else if (value instanceof Map<?, ?>) { json.put(toJSON((Map<?, ?>) value)); }
            else if (value instanceof List<?>) { json.put(toJSON((List<?>) value)); }
            else { json.put(value); }
        }

        return json;
    }

    public static String toJSON(Date date) { return DATE_FORMAT.get().format(date); }

    public static Map<String, Object> fromJSON(JSONObject json) throws JSONException {
        if (json == null) { return null; }

        final Map<String, Object> result = new HashMap<>();
        final Iterator<String> itr = json.keys();
        while (itr.hasNext()) {
            final String key = itr.next();
            final Object value = json.get(key);
            if (value instanceof JSONObject) { result.put(key, fromJSON((JSONObject) value)); }
            else if (value instanceof JSONArray) { result.put(key, fromJSON((JSONArray) value)); }
            else { result.put(key, value); }
        }

        return result;
    }

    public static List<Object> fromJSON(JSONArray json) throws JSONException {
        if (json == null) { return null; }

        final List<Object> result = new ArrayList<>();
        for (int i = 0; i < json.length(); i++) {
            final Object value = json.get(i);
            if (value instanceof JSONObject) { result.add(fromJSON((JSONObject) value)); }
            else if (value instanceof JSONArray) { result.add(fromJSON((JSONArray) value)); }
            else { result.add(value); }
        }

        return result;
    }

    public static Date toDate(String json) {
        if (json == null) { return null; }

        try { return DATE_FORMAT.get().parse(json); }
        catch (ParseException ignore) { }

        return null;
    }
}
