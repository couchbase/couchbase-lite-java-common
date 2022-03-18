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
package com.couchbase.lite.internal.utils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;

import com.couchbase.lite.LogDomain;
import com.couchbase.lite.internal.support.Log;


/**
 * A simple state machine.
 * <p>
 * This class is not thread safe!
 *
 * @param <T> states.
 */
public final class StateMachine<T extends Enum<T>> {
    public static class Builder<S extends Enum<S>> {
        @NonNull
        private final LogDomain domain;
        @NonNull
        private final S initialState;
        @Nullable
        private final S failureState;
        @NonNull
        private final EnumMap<S, EnumSet<S>> transitions;

        /**
         * State machine builder.
         * The error state is treated specially: it is always legal to transition into the error state
         * and never legal to transfer out of it.
         *
         * @param klass     The class of the enum of states.
         * @param initState The start state for the machine.
         * @param failState The machine failure state.
         */
        public Builder(@NonNull Class<S> klass, @NonNull LogDomain log, @NonNull S initState, @Nullable S failState) {
            this.transitions = new EnumMap<>(klass);
            this.domain = log;
            this.initialState = initState;
            this.failureState = failState;
        }

        /**
         * Add arcs to the DAG.
         * I believe that the use of varargs is, in fact, safe.
         *
         * @param source  of the arcs.
         * @param target1 the end of the first arc
         * @param targets the ends of other arcs
         */
        @NonNull
        @SafeVarargs
        public final Builder<S> addTransition(@NonNull S source, @NonNull S target1, @NonNull S... targets) {
            if (source == failureState) {
                throw new IllegalArgumentException("transitions from the failure state are illegal");
            }

            transitions.put(source, EnumSet.of(target1, targets));

            return this;
        }

        /**
         * Create the state machine.
         *
         * @return a new state machine instance.
         */
        @NonNull
        public StateMachine<S> build() { return new StateMachine<>(domain, initialState, failureState, transitions); }
    }

    @NonNull
    private final LogDomain domain;
    @Nullable
    private final T failureState;
    @NonNull
    private final EnumMap<T, EnumSet<T>> transitions;
    @NonNull
    private T state;

    private StateMachine(
        @NonNull LogDomain domain,
        @NonNull T initialState,
        @Nullable T failureState,
        @NonNull EnumMap<T, EnumSet<T>> transitions) {
        this.domain = domain;
        this.state = initialState;
        this.failureState = failureState;
        this.transitions = transitions;
    }

    @NonNull
    @Override
    public String toString() { return ClassUtils.objId(this); }

    /**
     * Verify expected state.
     *
     * @param expected expected states.
     * @return true if the current state at the time of the call, is one of the expected states.
     */
    @SafeVarargs
    public final boolean assertState(@NonNull T... expected) {
        Preconditions.assertPositive(expected.length, "expected states length");

        for (T s: expected) {
            if (s == state) { return true; }
        }

        if (state != failureState) {
            Log.d(
                domain,
                "StateMachine%s: unexpected state %s %s",
                new Exception(),
                this,
                state,
                Arrays.toString(expected));
        }

        return false;
    }

    /**
     * Set the new state.
     * If it is legal to transition to the new state, from the current state, do so,
     * returning the now-previous state.  If the transition is illegal do nothing and return null.
     *
     * @param nextState the requested new state
     * @return the previous state, if the transition succeeds; null otherwise.
     */
    public boolean setState(@NonNull T nextState) {
        final EnumSet<T> legalStates = transitions.get(state);
        if ((nextState == failureState) || ((legalStates != null) && (legalStates.contains(nextState)))) {
            Log.d(domain, "StateMachine%s: transition %s => %s", this, state, nextState);
            state = nextState;
            return true;
        }

        if (state != failureState) {
            Log.d(
                domain,
                "StateMachine%s: no transition: %s => %s %s",
                this,
                new Exception(),
                state,
                nextState,
                legalStates);
        }

        return false;
    }

    /**
     * For testing use only
     *
     * @return the current machine state
     */
    @VisibleForTesting
    @NonNull
    public T getCurrentState() { return state; }
}
