package com.titradeck.app.chemistry

import java.math.BigInteger

/** Exact arithmetic keeps balancing independent of floating-point rounding. */
internal class Rational private constructor(val numerator: BigInteger, val denominator: BigInteger) {
    operator fun plus(other: Rational) = of(numerator * other.denominator + other.numerator * denominator, denominator * other.denominator)
    operator fun minus(other: Rational) = of(numerator * other.denominator - other.numerator * denominator, denominator * other.denominator)
    operator fun times(other: Rational) = of(numerator * other.numerator, denominator * other.denominator)
    operator fun div(other: Rational): Rational {
        require(!other.isZero) { "不能除以零" }
        return of(numerator * other.denominator, denominator * other.numerator)
    }
    operator fun unaryMinus() = of(-numerator, denominator)
    val isZero get() = numerator.signum() == 0

    companion object {
        val ZERO = Rational(BigInteger.ZERO, BigInteger.ONE)
        val ONE = Rational(BigInteger.ONE, BigInteger.ONE)
        fun of(value: Int) = of(BigInteger.valueOf(value.toLong()), BigInteger.ONE)
        fun of(n: BigInteger, d: BigInteger): Rational {
            require(d.signum() != 0) { "不能除以零" }
            if (n.signum() == 0) return ZERO
            val gcd = n.gcd(d)
            val sign = if (d.signum() < 0) BigInteger.valueOf(-1) else BigInteger.ONE
            val numerator = n / gcd * sign
            val denominator = d / gcd * sign
            require(numerator.bitLength() <= 4096 && denominator.bitLength() <= 4096) { "方程式过于复杂，请减少物种或下标" }
            return Rational(numerator, denominator)
        }
    }
}
