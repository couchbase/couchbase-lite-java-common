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

import android.support.annotation.Nullable;

import java.security.cert.Certificate;
import java.util.Date;
import java.util.List;


public class AbstractTLSIdentity {
    public static final String CERT_ATTR_COMMON_NAME = "CN";
    public static final String CERT_ATTR_PSEUDONYM = "pseudonym";
    public static final String CERT_ATTR_GIVEN_NAME = "GN";
    public static final String CERT_ATTR_SURNAME = "SN";
    public static final String CERT_ATTR_ORGANIZATION = "O";
    public static final String CERT_ATTR_ORGANIZATION_UNIT = "OU";
    public static final String CERT_ATTR_POSTAL_ADDRESS = "postalAddress";
    public static final String CERT_ATTR_LOCALITY = "locality";
    public static final String CERT_ATTR_POSTAL_CODE = "postalCode";
    public static final String CERT_ATTR_STATE_OR_PROVINCE = "ST";
    public static final String CERT_ATTR_COUNTRY = "C";
    public static final String CERT_ATTR_EMAIL_ADDRESS = "rfc822Name";
    public static final String CERT_ATTR_HOSTNAME = "dNSName";
    public static final String CERT_ATTR_URL = "uniformResourceIdentifier";
    public static final String CERT_ATTR_IP_ADDRESS = "iPAddress";
    public static final String CERT_ATTR_REGISTERED_ID = "registeredID";

    @Nullable
    private final List<Certificate> certs;

    @Nullable
    private final Date expiration;

    protected AbstractTLSIdentity(@Nullable List<Certificate> certs, @Nullable Date expiration) {
        this.certs = certs;
        this.expiration = expiration;
    }

    @Nullable
    public List<Certificate> getCerts() { return certs; }

    @Nullable
    public Date getExpiration() { return expiration; }
}
