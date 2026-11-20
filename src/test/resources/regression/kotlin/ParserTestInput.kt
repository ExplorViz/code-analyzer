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

/**
 * Only direct class-body properties are fields; locals in initializers, constructors, accessors
 * and lambdas are not. Extension properties are typed by their declared type, not the receiver.
 */
class PropertyScopes(seed: Int) {
    val lazyValue: Int by lazy {
        val lazyLocal = seed * 2
        lazyLocal
    }

    var counter: Int = 0
        get() {
            val getterLocal = field
            return getterLocal
        }
        set(value) {
            val setterLocal = value
            field = setterLocal
        }

    val String.lastChar: Char
        get() = this[length - 1]

    private val task = object : Runnable {
        val anonymousState = 1
    }

    init {
        val initLocal = seed
        counter = initLocal
    }

    constructor(seed: Int, extra: Int) : this(seed + extra) {
        val constructorLocal = extra
        counter = constructorLocal
    }

    fun increment() {
        val incrementLocal = counter
        counter = incrementLocal + 1
    }
}

/**
 * A bare supertype is an interface; only a constructor call names the superclass.
 */
class RunnableTask : Runnable {
    val state: Int = 1

    override fun run() {
        val runLocal = state
        println(runLocal)
    }
}

class InterfaceFirst : CallableMarker, AbstractBase(1) {
    val label: String = "first"

    override fun call(): String = label

    override fun abstractHook(): Int = 1
}

object SingletonBase : AbstractBase(2), Serializable {
    override fun abstractHook(): Int = 2
}

object DelegatingSingleton : CallableMarker by InterfaceFirst()

/** Local classes with the same name in different functions are both kept. */
fun firstLocalScope(): Int {
    class LocalType(val first: Int)
    return LocalType(1).first
}

fun secondLocalScope(): String {
    class LocalType(val second: String) {
        fun describe(): String = second
    }
    return LocalType("2").describe()
}

/** Secondary constructors and init blocks after a bare supertype and after an explicit delegation. */
class BareSupertypeBody : CallableMarker {
    val label: String

    init {
        val initLocal = "init"
        label = initLocal
    }

    constructor(label: String, suffix: Int) : this(label + suffix)

    constructor(prefix: String) {
        this.label = prefix
    }

    override fun call(): String = label
}

class DelegatedBody(impl: CallableMarker) : CallableMarker by impl {
    val extra: Int

    init {
        extra = 1
    }

    constructor() : this(InterfaceFirst())

    fun describe(): String = call()
}

/** Object literals are anonymous classes; their members do not belong to the enclosing class. */
class AnonymousOwner {
    val listener = object : Runnable {
        val runs: Int = 0

        override fun run() {
            println(runs)
        }
    }

    fun createMarker(): CallableMarker {
        return object : AbstractBase(3), CallableMarker {
            override fun call(): String = "anonymous"

            override fun abstractHook(): Int = 3
        }
    }

    fun ownerMethod(): Int = 1
}

val topLevelRunnable = object : Runnable {
    override fun run() {
        println("top level")
    }
}
