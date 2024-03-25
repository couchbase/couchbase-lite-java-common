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

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONException;

import com.couchbase.lite.internal.core.C4Query;
import com.couchbase.lite.internal.logging.Log;
import com.couchbase.lite.internal.utils.ClassUtils;
import com.couchbase.lite.internal.utils.JSONUtils;


abstract class BuilderQuery extends AbstractQuery {

    // NOTE:
    // https://sqlite.org/lang_select.html

    // SELECT
    private Select select;
    // FROM
    private DataSource from; // FROM table-or-subquery
    private Joins joins;     // FROM join-clause
    // WHERE
    private Expression where; // WHERE expr
    // GROUP BY
    private GroupBy groupBy; // GROUP BY expr(s)
    private Having having; // Having expr
    // ORDER BY
    private OrderBy orderBy; // ORDER BY ordering-term(s)
    // LIMIT
    private Limit limit; // LIMIT expr

    //---------------------------------------------
    // API - public methods
    //---------------------------------------------

    @NonNull
    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" + ClassUtils.objId(this) + ", json=" + marshalAsJSONSafely() + "}";
    }

    //---------------------------------------------
    // Protected access
    //---------------------------------------------

    /**
     * Get the database to which this query will be applied.
     *
     * @return the query database.
     */
    @NonNull
    @Override
    protected final AbstractDatabase getDatabase() { return from.getDatabase(); }

    @GuardedBy("lock")
    @NonNull
    @Override
    protected final C4Query prepQueryLocked(@NonNull AbstractDatabase db) throws CouchbaseLiteException {
        final String json = marshalAsJSONSafely();
        Log.d(DOMAIN, "JSON query: %s", json);
        if (json == null) { throw new CouchbaseLiteException("Failed to generate JSON query."); }

        try { return db.createJsonQuery(json); }
        catch (LiteCoreException e) { throw CouchbaseLiteException.convertException(e); }
    }

    //---------------------------------------------
    // Package access
    //---------------------------------------------

    void setSelect(Select select) { this.select = select; }

    void setFrom(@NonNull DataSource from) { this.from = from; }

    void setJoins(Joins joins) { this.joins = joins; }

    void setWhere(Expression where) { this.where = where; }

    void setGroupBy(GroupBy groupBy) { this.groupBy = groupBy; }

    void setHaving(Having having) { this.having = having; }

    void setOrderBy(OrderBy orderBy) { this.orderBy = orderBy; }

    void setLimit(Limit limit) { this.limit = limit; }

    void copy(BuilderQuery query) {
        this.select = query.select;
        this.from = query.from;
        this.joins = query.joins;
        this.where = query.where;
        this.groupBy = query.groupBy;
        this.having = query.having;
        this.orderBy = query.orderBy;
        this.limit = query.limit;
        try { this.setParameters(query.getParameters()); }
        catch (CouchbaseLiteException e) { throw new IllegalArgumentException("Failed copying query parameters", e); }
    }

    //---------------------------------------------
    // Private methods
    //---------------------------------------------

    @Nullable
    private String marshalAsJSONSafely() {
        try { return marshalAsJSON(); }
        catch (JSONException e) { Log.w(LogDomain.QUERY, "Failed marshalling query as JSON query", e); }
        return null;
    }

    @SuppressWarnings({"PMD.CyclomaticComplexity", "PMD.NPathComplexity"})
    @NonNull
    private String marshalAsJSON() throws JSONException {
        final Map<String, Object> json = new HashMap<>();

        // DISTINCT:
        if (select != null && select.isDistinct()) { json.put("DISTINCT", "true"); }

        // result-columns / SELECT-RESULTS
        if (select != null && select.hasSelectResults()) { json.put("WHAT", select.asJSON()); }

        final List<Object> froms = new ArrayList<>();

        final Map<String, Object> as = from.asJSON();
        if (!as.isEmpty()) { froms.add(as); }

        if (joins != null) { froms.addAll((List<?>) joins.asJSON()); }

        if (!froms.isEmpty()) { json.put("FROM", froms); }

        if (where != null) { json.put("WHERE", where.asJSON()); }

        if (groupBy != null) { json.put("GROUP_BY", groupBy.asJSON()); }

        if (having != null) {
            final Object havingJson = having.asJSON();
            if (havingJson != null) { json.put("HAVING", havingJson); }
        }

        if (orderBy != null) { json.put("ORDER_BY", orderBy.asJSON()); }

        if (limit != null) {
            final List<?> limits = (List<?>) limit.asJSON();
            json.put("LIMIT", limits.get(0));
            if (limits.size() > 1) { json.put("OFFSET", limits.get(1)); }
        }

        return JSONUtils.toJSON(json).toString();
    }
}
