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

import com.couchbase.lite.internal.utils.Preconditions;


/**
 * Based class for Query Functions.
 */
@SuppressWarnings({"PMD.TooManyMethods", "PMD.AbstractClassWithoutAbstractMethod"})
abstract class AbstractFunction {

    //---------------------------------------------
    // Aggregation
    //---------------------------------------------

    /**
     * Creates an AVG(expr) function expression that returns the average of all the number values
     * in the group of the values expressed by the given expression.
     * <p>
     * The expression.
     *
     * @return The AVG(expr) function.
     */
    @NonNull
    public static Expression avg(@NonNull Expression operand) { return sexpr("AVG()", operand); }

    /**
     * Creates a COUNT(expr) function expression that returns the count of all values
     * in the group of the values expressed by the given expression.
     * Null expression is count *
     *
     * @param operand The expression.
     * @return The COUNT(expr) function.
     */
    @NonNull
    public static Expression count(@Nullable Expression operand) {
        return sexpr("COUNT()", (operand != null) ? operand : Expression.value("."));
    }

    /**
     * Creates a MIN(expr) function expression that returns the minimum value
     * in the group of the values expressed by the given expression.
     *
     * @param operand The expression.
     * @return The MIN(expr) function.
     */
    @NonNull
    public static Expression min(@NonNull Expression operand) { return sexpr("MIN()", operand); }

    /**
     * Creates a MAX(expr) function expression that returns the maximum value
     * in the group of the values expressed by the given expression.
     *
     * @param operand The expression.
     * @return The MAX(expr) function.
     */
    @NonNull
    public static Expression max(@NonNull Expression operand) { return sexpr("MAX()", operand); }

    /**
     * Creates a SUM(expr) function expression that return the sum of all number values
     * in the group of the values expressed by the given expression.
     *
     * @param operand The expression.
     * @return The SUM(expr) function.
     */
    @NonNull
    public static Expression sum(@NonNull Expression operand) { return sexpr("SUM()", operand); }

    //---------------------------------------------
    // Math
    //---------------------------------------------

    /**
     * Creates an ABS(expr) function that returns the absolute value of the given numeric
     * expression.
     *
     * @param operand The expression.
     * @return The ABS(expr) function.
     */
    @NonNull
    public static Expression abs(@NonNull Expression operand) { return sexpr("ABS()", operand); }

    /**
     * Creates an ACOS(expr) function that returns the inverse cosine of the given numeric
     * expression.
     *
     * @param operand The expression.
     * @return The ACOS(expr) function.
     */
    @NonNull
    public static Expression acos(@NonNull Expression operand) { return sexpr("ACOS()", operand); }

    /**
     * Creates an ASIN(expr) function that returns the inverse sin of the given numeric
     * expression.
     *
     * @param operand The expression.
     * @return The ASIN(expr) function.
     */
    @NonNull
    public static Expression asin(@NonNull Expression operand) { return sexpr("ASIN()", operand); }

    /**
     * Creates an ATAN(expr) function that returns the inverse tangent of the numeric
     * expression.
     *
     * @param operand The expression.
     * @return The ATAN(expr) function.
     */
    @NonNull
    public static Expression atan(@NonNull Expression operand) { return sexpr("ATAN()", operand); }

    /**
     * Returns the angle theta from the conversion of rectangular coordinates (x, y)
     * to polar coordinates (r, theta).
     *
     * @param x the abscissa coordinate
     * @param y the ordinate coordinate
     * @return the theta component of the point (r, theta) in polar coordinates that corresponds
     * to the point (x, y) in Cartesian coordinates.
     */
    @NonNull
    public static Expression atan2(@NonNull Expression y, @NonNull Expression x) {
        return expr("ATAN2()", Preconditions.assertNotNull(y, "y"), Preconditions.assertNotNull(x, "x"));
    }

    /**
     * Creates a CEIL(expr) function that returns the ceiling value of the given numeric
     * expression.
     *
     * @param operand The expression.
     * @return The CEIL(expr) function.
     */
    @NonNull
    public static Expression ceil(@NonNull Expression operand) { return sexpr("CEIL()", operand); }

    /**
     * Creates a COS(expr) function that returns the cosine of the given numeric expression.
     *
     * @param operand The expression.
     * @return The COS(expr) function.
     */
    @NonNull
    public static Expression cos(@NonNull Expression operand) { return sexpr("COS()", operand); }

    /**
     * Creates a DEGREES(expr) function that returns the degrees value of the given radiants
     * value expression.
     *
     * @param operand The expression.
     * @return The DEGREES(expr) function.
     */
    @NonNull
    public static Expression degrees(@NonNull Expression operand) { return sexpr("DEGREES()", operand); }

    /**
     * Creates a E() function that return the value of the mathematical constant 'e'.
     *
     * @return The E() constant function.
     */
    @NonNull
    public static Expression e() { return expr("E()", (Expression) null); }

    /**
     * Creates a EXP(expr) function that returns the value of 'e' power by the given numeric
     * expression.
     *
     * @param operand The expression.
     * @return The EXP(expr) function.
     */
    @NonNull
    public static Expression exp(@NonNull Expression operand) { return sexpr("EXP()", operand); }

    /**
     * Creates a FLOOR(expr) function that returns the floor value of the given
     * numeric expression.
     *
     * @param operand The expression.
     * @return The FLOOR(expr) function.
     */
    @NonNull
    public static Expression floor(@NonNull Expression operand) { return sexpr("FLOOR()", operand); }

    /**
     * Creates a LN(expr) function that returns the natural log of the given numeric expression.
     *
     * @param operand The expression.
     * @return The LN(expr) function.
     */
    @NonNull
    public static Expression ln(@NonNull Expression operand) { return sexpr("LN()", operand); }

    /**
     * Creates a LOG(expr) function that returns the base 10 log of the given numeric expression.
     *
     * @param operand The expression.
     * @return The LOG(expr) function.
     */
    @NonNull
    public static Expression log(@NonNull Expression operand) { return sexpr("LOG()", operand); }

    /**
     * Creates a PI() function that returns the mathematical constant Pi.
     *
     * @return The PI() constant function.
     */
    @NonNull
    public static Expression pi() { return expr("PI()", (Expression) null); }

    /**
     * Creates a POWER(base, exponent) function that returns the value of the given base
     * expression power the given exponent expression.
     *
     * @param base The base expression.
     * @param exp  The exponent expression.
     * @return The POWER(base, exponent) function.
     */
    @NonNull
    public static Expression power(@NonNull Expression base, @NonNull Expression exp) {
        return expr("POWER()", Preconditions.assertNotNull(base, "base"), Preconditions.assertNotNull(exp, "exponent"));
    }

    /**
     * Creates a RADIANS(expr) function that returns the radians value of the given degrees
     * value expression.
     *
     * @param operand The expression.
     * @return The RADIANS(expr) function.
     */
    @NonNull
    public static Expression radians(@NonNull Expression operand) { return sexpr("RADIANS()", operand); }

    /**
     * Creates a ROUND(expr) function that returns the rounded value of the given numeric
     * expression.
     *
     * @param operand The expression.
     * @return The ROUND(expr) function.
     */
    @NonNull
    public static Expression round(@NonNull Expression operand) { return sexpr("ROUND()", operand); }

    /**
     * Creates a ROUND(expr, digits) function that returns the rounded value to the given
     * number of digits of the given numeric expression.
     *
     * @param operand The numeric expression.
     * @param digits  The number of digits.
     * @return The ROUND(expr, digits) function.
     */
    @NonNull
    public static Expression round(@NonNull Expression operand, @NonNull Expression digits) {
        return expr(
            "ROUND()",
            Preconditions.assertNotNull(operand, "operand"),
            Preconditions.assertNotNull(digits, "digits"));
    }

    /**
     * Creates a SIGN(expr) function that returns the sign (1: positive, -1: negative, 0: zero)
     * of the given numeric expression.
     *
     * @param operand The expression.
     * @return The SIGN(expr) function.
     */
    @NonNull
    public static Expression sign(@NonNull Expression operand) { return sexpr("SIGN()", operand); }

    /**
     * Creates a SIN(expr) function that returns the sin of the given numeric expression.
     *
     * @param operand The numeric expression.
     * @return The SIN(expr) function.
     */
    @NonNull
    public static Expression sin(@NonNull Expression operand) { return sexpr("SIN()", operand); }

    /**
     * Creates a SQRT(expr) function that returns the square root of the given numeric expression.
     *
     * @param operand The numeric expression.
     * @return The SQRT(expr) function.
     */
    @NonNull
    public static Expression sqrt(@NonNull Expression operand) { return sexpr("SQRT()", operand); }

    /**
     * Creates a TAN(expr) function that returns the tangent of the given numeric expression.
     *
     * @param operand The numeric expression.
     * @return The TAN(expr) function.
     */
    @NonNull
    public static Expression tan(@NonNull Expression operand) { return sexpr("TAN()", operand); }

    /**
     * Creates a TRUNC(expr) function that truncates all of the digits after the decimal place
     * of the given numeric expression.
     *
     * @param operand The numeric expression.
     * @return The trunc function.
     */
    @NonNull
    public static Expression trunc(@NonNull Expression operand) { return sexpr("TRUNC()", operand); }

    /**
     * Creates a TRUNC(expr, digits) function that truncates the number of the digits after
     * the decimal place of the given numeric expression.
     *
     * @param operand The numeric expression.
     * @param digits  The number of digits to truncate.
     * @return The TRUNC(expr, digits) function.
     */
    @NonNull
    public static Expression trunc(@NonNull Expression operand, @NonNull Expression digits) {
        return expr(
            "TRUNC()",
            Preconditions.assertNotNull(operand, "operand"),
            Preconditions.assertNotNull(digits, "digits"));
    }

    //---------------------------------------------
    // String
    //---------------------------------------------

    /**
     * Creates a CONTAINS(expr, substr) function that evaluates whether the given string
     * expression conatins the given substring expression or not.
     *
     * @param operand   The string expression.
     * @param substring The substring expression.
     * @return The CONTAINS(expr, substr) function.
     */
    @NonNull
    public static Expression contains(@NonNull Expression operand, @NonNull Expression substring) {
        return expr(
            "CONTAINS()",
            Preconditions.assertNotNull(operand, "operand"),
            Preconditions.assertNotNull(substring, "substring"));
    }

    /**
     * Creates a LENGTH(expr) function that returns the length of the given string expression.
     *
     * @param operand The string expression.
     * @return The LENGTH(expr) function.
     */
    @NonNull
    public static Expression length(@NonNull Expression operand) { return sexpr("LENGTH()", operand); }

    /**
     * Creates a LOWER(expr) function that returns the lowercase string of the given string
     * expression.
     *
     * @param operand The string expression.
     * @return The LOWER(expr) function.
     */
    @NonNull
    public static Expression lower(@NonNull Expression operand) { return sexpr("LOWER()", operand); }

    /**
     * Creates a LTRIM(expr) function that removes the whitespace from the beginning of the
     * given string expression.
     *
     * @param operand The string expression.
     * @return The LTRIM(expr) function.
     */
    @NonNull
    public static Expression ltrim(@NonNull Expression operand) { return sexpr("LTRIM()", operand); }

    /**
     * Creates a RTRIM(expr) function that removes the whitespace from the end of the
     * given string expression.
     *
     * @param operand The string expression.
     * @return The RTRIM(expr) function.
     */
    @NonNull
    public static Expression rtrim(@NonNull Expression operand) { return sexpr("RTRIM()", operand); }

    /**
     * Creates a TRIM(expr) function that removes the whitespace from the beginning and
     * the end of the given string expression.
     *
     * @param operand The string expression.
     * @return The TRIM(expr) function.
     */
    @NonNull
    public static Expression trim(@NonNull Expression operand) { return sexpr("TRIM()", operand); }

    /**
     * Creates a UPPER(expr) function that returns the uppercase string of the given string expression.
     *
     * @param operand The string expression.
     * @return The UPPER(expr) function.
     */
    @NonNull
    public static Expression upper(@NonNull Expression operand) { return sexpr("UPPER()", operand); }

    /**
     * Creates a MILLIS_TO_STR(expr) function that will convert a numeric input representing
     * milliseconds since the Unix epoch into a full ISO8601 date and time
     * string in the device local time zone.
     *
     * @param operand The string expression.
     * @return The MILLIS_TO_STR(expr) function.
     */
    @NonNull
    public static Expression millisToString(@NonNull Expression operand) { return sexpr("MILLIS_TO_STR()", operand); }

    /**
     * Creates a MILLIS_TO_UTC(expr) function that will convert a numeric input representing
     * milliseconds since the Unix epoch into a full ISO8601 date and time
     * string in UTC time.
     *
     * @param operand The string expression.
     * @return The MILLIS_TO_UTC(expr) function.
     */
    @NonNull
    public static Expression millisToUTC(@NonNull Expression operand) { return sexpr("MILLIS_TO_UTC()", operand); }

    /**
     * Creates a STR_TO_MILLIS(expr) that will convert an ISO8601 datetime string
     * into the number of milliseconds since the unix epoch.
     * Valid date strings must start with a date in the form YYYY-MM-DD (time
     * only strings are not supported).
     * <p>
     * Times can be of the form HH:MM, HH:MM:SS, or HH:MM:SS.FFF.  Leading zero is
     * not optional (i.e. 02 is ok, 2 is not).  Hours are in 24-hour format.  FFF
     * represents milliseconds, and *trailing* zeros are optional (i.e. 5 == 500).
     * <p>
     * Time zones can be in one of three forms:
     * (+/-)HH:MM
     * (+/-)HHMM
     * Z (which represents UTC)
     *
     * @param operand The string expression.
     * @return The STR_TO_MILLIS(expr) function.
     */
    @NonNull
    public static Expression stringToMillis(@NonNull Expression operand) { return sexpr("STR_TO_MILLIS()", operand); }

    /**
     * Creates a STR_TO_UTC(expr) that will convert an ISO8601 datetime string
     * into a full ISO8601 UTC datetime string.
     * Valid date strings must start with a date in the form YYYY-MM-DD (time
     * only strings are not supported).
     * <p>
     * Times can be of the form HH:MM, HH:MM:SS, or HH:MM:SS.FFF.  Leading zero is
     * not optional (i.e. 02 is ok, 2 is not).  Hours are in 24-hour format.  FFF
     * represents milliseconds, and *trailing* zeros are optional (i.e. 5 == 500).
     * <p>
     * Time zones can be in one of three forms:
     * (+/-)HH:MM
     * (+/-)HHMM
     * Z (which represents UTC)
     *
     * @param operand The string expression.
     * @return The STR_TO_UTC(expr) function.
     */
    @NonNull
    public static Expression stringToUTC(@NonNull Expression operand) { return sexpr("STR_TO_UTC()", operand); }

    @NonNull
    private static Expression.FunctionExpression sexpr(@NonNull String expr, @NonNull Expression operand) {
        return new Expression.FunctionExpression(expr, Preconditions.assertNotNull(operand, "operand expression"));
    }

    @NonNull
    private static Expression.FunctionExpression expr(@NonNull String expr, @NonNull Expression... operands) {
        return new Expression.FunctionExpression(expr, operands);
    }
}
