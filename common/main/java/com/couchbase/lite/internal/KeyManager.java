//
// Copyright (c) 2020 Couchbase, Inc All rights reserved.
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
package com.couchbase.lite.internal;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.math.BigInteger;
import java.security.KeyPair;
import java.util.Date;


public abstract class KeyManager {
    public enum KeyAlgorithm {RSA}

    public enum KeySize {
        BIT_512(512), BIT_768(768), BIT_1024(1024), BIT_2048(2048), BIT_3072(3072), BIT_4096(4096);

        final int len;

        KeySize(int len) { this.len = len; }

        public int getLen() { return len; }
    }

    public enum CertUsage {
        UNSPECIFIED(0x00),        //< No specified usage (not generally useful)
        TLS_CLIENT(0x80),         //< TLS (SSL) client cert
        TLS_SERVER(0x40),         //< TLS (SSL) server cert
        EMAIL(0x20),              //< Email signing and encryption
        OBJECT_SIGNING(0x10),     //< Signing arbitrary data
        TLS_CA(0x04),             //< CA for signing TLS cert requests
        EMAIL_CA(0x02),           //< CA for signing email cert requests
        OBJECT_SIGNING_CA(0x01);  //< CA for signing object-signing cert requests

        final byte code;

        CertUsage(int code) { this.code = (byte) code; }

        public byte getCode() { return code; }
    }

    //-------------------------------------------------------------------------
    // Native callback methods
    //-------------------------------------------------------------------------

    // This method is called by reflection.  Don't change its signature.
    @SuppressWarnings("unused")
    @NonNull
    static byte[] getKeyDataCallback(long keyToken) {
        return CouchbaseLiteInternal.getKeyManager().getKeyData(keyToken);
    }

    // This method is called by reflection.  Don't change its signature.
    @SuppressWarnings("unused")
    @NonNull
    static byte[] decryptCallback(long keyToken, @NonNull byte[] data) {
        return CouchbaseLiteInternal.getKeyManager().decrypt(keyToken, data);
    }

    // This method is called by reflection.  Don't change its signature.
    @SuppressWarnings("unused")
    @NonNull
    static byte[] signKeyCallback(long keyToken, int digestAlgorithm, @NonNull byte[] data) {
        return CouchbaseLiteInternal.getKeyManager().signKey(keyToken, digestAlgorithm, data);
    }

    // This method is called by reflection.  Don't change its signature.
    @SuppressWarnings("unused")
    static void freeCallback(long keyToken) { CouchbaseLiteInternal.getKeyManager().free(keyToken); }


    @Nullable
    public abstract KeyPair generateKeyPair(
        @NonNull String alias,
        @NonNull KeyAlgorithm algorithm,
        @NonNull KeySize keySize,
        @NonNull BigInteger serial,
        @NonNull Date expiration);

    @NonNull
    public abstract byte[] getKeyData(long keyToken);

    @NonNull
    public abstract byte[] decrypt(long keyToken, @NonNull byte[] data);

    @NonNull
    public abstract byte[] signKey(long keyToken, int digestAlgorithm, @NonNull byte[] data);

    public abstract void free(long keyToken);
}
