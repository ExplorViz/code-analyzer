import Foundation

// Single-line comment

/* Single-line block comment */

/*
 * Multi-line
 * block comment
 */

/**
 Comprehensive Swift parser test input covering programming constructs for regression testing.

 Includes import, class, struct, protocol, enum, methods, and global functions.
 */

/// Marker-style protocol.
protocol MarkerInterface {
    func mark()
    func defaultMark()
}

extension MarkerInterface {
    func defaultMark() {
        mark()
    }
}

/// Callable-like protocol.
protocol CallableMarker {
    func call() -> String
}

/// Nested-style protocol.
protocol NestedInterface: MarkerInterface {
    func abstractInterfaceMethod()
}

/// Abstract base-like class.
class AbstractBase {
    private let baseValue: Int

    init(baseValue: Int) {
        self.baseValue = baseValue
    }

    func getBaseValue() -> Int {
        return baseValue
    }

    func abstractHook() -> Int {
        return baseValue
    }
}

/// Nested enum with methods.
enum NestedEnum {
    case alpha
    case beta
    case gamma

    func isAlpha() -> Bool {
        return self == .alpha
    }
}

/// Top-level enum.
enum TopLevelEnum {
    case one
    case two
    case three
}

/// Public nested-type stand-in as a struct.
struct StaticNested {
    private var nestedField: Int

    init(nestedField: Int) {
        self.nestedField = nestedField
    }

    func getNestedField() -> Int {
        return nestedField
    }
}

/// Main regression type.
class ParserTestInput: AbstractBase, MarkerInterface, CallableMarker {
    /// Field documentation comment.
    static let publicStaticFinalInt: Int = 42

    private static var privateStaticVolatileFlag: Bool = false

    internal var internalTransientField: String?

    private let finalArrayField: [Double] = [1.0, 2.0]

    private var nestedGenericField: [String: [Int]] = [:]

    /// No-arg constructor.
    init() {
        super.init(baseValue: 0)
        self.nestedGenericField = [:]
    }

    /// Constructor with parameters.
    init(name: String?, values: [Int]) {
        super.init(baseValue: values.count)
        self.internalTransientField = name
    }

    func run() {
        assert(ParserTestInput.publicStaticFinalInt > 0)
    }

    func call() -> String {
        return doWork(flag: true, b: 1, s: 2, c: "x", i: 3, l: 4, f: 5.0, d: 6.0, obj: nil) ? "ok" : "no"
    }

    func genericMethod<T>(_ value: T) -> T {
        return value
    }

    func doWork(
        flag: Bool,
        b: UInt8,
        s: Int16,
        c: Character,
        i: Int,
        l: Int64,
        f: Float,
        d: Double,
        obj: Any?
    ) -> Bool {
        if flag {
            return true
        } else if obj is String {
            return false
        }

        switch i {
        case 0:
            break
        case 1:
            for n in 0..<3 {
                if n == 1 {
                    continue
                }
                if n == 2 {
                    break
                }
            }
        default:
            var shortCounter = s
            while shortCounter > 0 {
                shortCounter -= 1
            }
            var byteCounter = b
            repeat {
                byteCounter = byteCounter &+ 1
            } while byteCounter < 3
        }

        if l < 0 {
            // throw stand-in
        }
        ParserTestInput.privateStaticVolatileFlag = abs(i) > 0
        nestedGenericField["key"] = []
        return f > 0.0 && d > 0.0 && c != "\0"
    }

    override func abstractHook() -> Int {
        return getBaseValue()
    }

    func mark() {
        // empty
    }
}

/// Package-level function.
func packagePrivateStaticMethod() {
    // empty
}

/// Module helper with parameters.
func moduleHelper(path: String, ignored: MarkerInterface) -> String {
    return (path as NSString).lastPathComponent
}
