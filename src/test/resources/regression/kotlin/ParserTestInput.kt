package net.explorviz.code.analysis.fixtures

import java.io.Closeable
import java.io.IOException
import java.io.Serializable
import java.util.ArrayList
import java.util.HashMap
import java.util.List
import java.util.Map
import kotlin.math.abs

/**
 * Comprehensive Kotlin parser test input covering programming constructs for regression testing.
 *
 * Includes type declarations, methods, constructors, fields, comments, and inheritance.
 */
@Deprecated("regression fixture")
class ParserTestInput : AbstractBase, Serializable, Runnable, CallableMarker {
    // Single-line comment

    /* Single-line block comment */

    /*
     * Multi-line
     * block comment
     */

    /** Field KDoc comment. */
    companion object {
        const val PUBLIC_STATIC_FINAL_INT: Int = 42

        @JvmStatic
        private var privateStaticVolatileFlag: Boolean = false

        fun packagePrivateStaticMethod() {
            // empty
        }
    }

    protected var protectedTransientField: String? = null

    private val finalArrayField: DoubleArray = doubleArrayOf(1.0, 2.0)

    private var nestedGenericField: Map<String, List<Int>> = HashMap()

    /** No-arg constructor. */
    constructor() : super(0) {
        this.nestedGenericField = HashMap()
    }

    /** Constructor with parameters. */
    protected constructor(name: String?, vararg values: Int) : super(values.size) {
        this.protectedTransientField = name
    }

    override fun run() {
        val localCheck = PUBLIC_STATIC_FINAL_INT
        require(localCheck > 0) { "constant must be positive" }
    }

    override fun call(): String {
        return if (doWork(true, 1, 2, 'x', 3, 4L, 5.0f, 6.0, null)) "ok" else "no"
    }

    fun <T : Number> genericMethod(value: T): T {
        return value
    }

    protected fun doWork(
        flag: Boolean,
        b: Byte,
        s: Short,
        c: Char,
        i: Int,
        l: Long,
        f: Float,
        d: Double,
        obj: Any?,
    ): Boolean {
        if (flag) {
            return true
        } else if (obj is String) {
            return false
        }

        when (i) {
            0 -> { }
            1 -> {
                for (n in 0 until 3) {
                    if (n == 1) continue
                    if (n == 2) break
                }
            }
            else -> {
                var shortCounter = s.toInt()
                while (shortCounter > 0) {
                    shortCounter--
                }
                var byteCounter = b.toInt()
                do {
                    byteCounter++
                } while (byteCounter < 3)
            }
        }

        try {
            if (l < 0L) {
                throw IOException("negative")
            }
        } catch (ex: IOException) {
            throw ex
        } finally {
            privateStaticVolatileFlag = abs(i) > 0
        }

        nestedGenericField = nestedGenericField + ("key" to ArrayList())
        return f > 0.0f && d > 0.0 && c != '\u0000'
    }

    override fun abstractHook(): Int {
        return getBaseValue()
    }

    /** Public nested class. */
    class StaticNested(private var nestedField: Int) {
        fun getNestedField(): Int = nestedField
    }

    /** Nested interface. */
    interface NestedInterface : Serializable {
        fun abstractInterfaceMethod()
    }

    /** Nested enum. */
    enum class NestedEnum {
        ALPHA,
        BETA,
        GAMMA;

        fun isAlpha(): Boolean = this == ALPHA
    }
}

/**
 * Abstract base class.
 */
abstract class AbstractBase(private val baseValue: Int) {
    fun getBaseValue(): Int = baseValue

    abstract fun abstractHook(): Int
}

/**
 * Marker interface with multiple method kinds.
 */
interface CallableMarker {
    fun call(): String
}

interface MarkerInterface : Closeable {
    fun mark()

    fun defaultMark() {
        mark()
    }
}

/**
 * Top-level enum.
 */
enum class TopLevelEnum {
    ONE,
    TWO,
    THREE
}

/**
 * Data class for compact type coverage.
 */
data class PointData(val x: Int, val y: Int)

/**
 * Object singleton helper.
 */
object ModuleHelper {
    fun moduleHelper(path: String): String = path
}
