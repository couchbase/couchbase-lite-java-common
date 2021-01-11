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

import android.support.annotation.GuardedBy;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;

import com.couchbase.lite.LogDomain;
import com.couchbase.lite.internal.support.Log;


public class StateMachine<T extends Enum<T>> {
    private static final LogDomain TAG = LogDomain.DATABASE;

    public static class Builder<S extends Enum<S>> {
        @NonNull
        private final S initialState;
        @Nullable
        private final S errorState;
        @NonNull
        private final EnumMap<S, EnumSet<S>> transitions;

        /**
         * State machine builder.
         * The error state is treated specially: it is always legal to transition into the error state
         * and never legal to transfer out of it.
         *
         * @param klass        The class of the enum of states.
         * @param initialState The start state for the machine.
         * @param errorState   The machine error state.
         */
        public Builder(@NonNull Class<S> klass, @NonNull S initialState, @Nullable S errorState) {
            this.initialState = initialState;
            this.errorState = errorState;
            transitions = new EnumMap<>(klass);
        }

        /**
         * Add arcs to the DAG.
         *
         * @param source  of the arcs.
         * @param target1 the end of the first arc
         * @param targets the ends of the other arcs
         */
        public void addTransition(@NonNull S source, @NonNull S target1, @NonNull S... targets) {
            if (source == errorState) {
                throw new IllegalArgumentException("transitions from the error state are illegal");
            }

            transitions.put(source, EnumSet.of(target1, targets));
        }

        /**
         * Create the state machine.
         *
         * @return a new state machine instance.
         */
        public StateMachine<S> build() { return new StateMachine<>(initialState, errorState, transitions); }
    }


    @Nullable
    private final T errorState;
    @NonNull
    private final EnumMap<T, EnumSet<T>> transitions;

    @GuardedBy("transitions")
    private T state;

    private StateMachine(@NonNull T initialState, @Nullable T errorState, @NonNull EnumMap<T, EnumSet<T>> transitions) {
        state = initialState;
        this.errorState = errorState;
        this.transitions = transitions;
    }

    @NonNull
    @Override
    public String toString() { return ClassUtils.objId(this); }

    /**
     * Verify expected state.
     *
     * @param loc      a string that identifies the location of the call
     * @param expected expected states.
     * @return true if the current state at the time of the call, is one of the expected states.
     */
    public boolean checkState(@NonNull String loc, @NonNull T... expected) {
        if (expected.length <= 0) { throw new IllegalArgumentException("no expected states specified"); }

        final T curState;
        synchronized (transitions) {
            curState = state;
            for (T s: expected) {
                if (s == curState) { return true; }
            }
        }

        if (curState != errorState) {
            Log.v(
                TAG,
                "StateMachine %s@%s expected state %s but found %s",
                new Exception(),
                this,
                loc,
                Arrays.toString(expected),
                curState);
        }

        return false;
    }

    /**
     * Set the new state.
     * If it is legal to transition to the new state, from the current state, do so,
     * returning the now-previous state.  If the transition is illegal do nothing and return null.
     *
     * @param loc       a string that identifies the location of the call
     * @param nextState the requested new state
     * @return the previous state, if the transtion succeeds; null otherwise.
     */
    @Nullable
    public T setState(@NonNull String loc, @NonNull T nextState) {
        final T prevState;
        final EnumSet<T> legalStates;
        final boolean legal;
        synchronized (transitions) {
            prevState = state;

            legalStates = transitions.get(prevState);
            legal = ((nextState == errorState) || ((legalStates != null) && (legalStates.contains(nextState))));

            if (legal) { state = nextState; }
        }

        if (legal) {
            Log.d(TAG, "%s@%s: transition %s => %s", this, loc, nextState, prevState);
            return prevState;
        }

        if (prevState != errorState) {
            Log.v(
                TAG,
                "%s@%s: transition to %s for %s => %s",
                new Exception(),
                this,
                loc,
                nextState,
                prevState,
                legalStates,
                Thread.currentThread());
        }

        return null;
    }
}
