/**
 * Comprehensive C++ parser test input for regression testing (CPP14-compatible).
 *
 * Includes includes, structs, enums, classes, methods, inheritance, and comments.
 */

#include <cmath>
#include <cstdlib>
#include <cstdio>
#include <map>
#include <string>
#include <vector>
#include "fixtures_support.hpp"

// Single-line comment

/* Single-line block comment */

/*
 * Multi-line
 * block comment
 */

enum NestedEnum {
    NESTED_ALPHA = 0,
    NESTED_BETA = 1,
    NESTED_GAMMA = 2
};

enum TopLevelEnum {
    TOP_ONE = 1,
    TOP_TWO = 2,
    TOP_THREE = 3
};

class MarkerInterface {
public:
    virtual ~MarkerInterface() {}
    virtual void mark() = 0;
    virtual void defaultMark() { mark(); }
};

class CallableMarker {
public:
    virtual ~CallableMarker() {}
    virtual std::string call() = 0;
};

class AbstractBase {
private:
    int baseValue;

protected:
    explicit AbstractBase(int baseValue) : baseValue(baseValue) {}

public:
    virtual ~AbstractBase() {}
    int getBaseValue() const { return baseValue; }
    virtual int abstractHook() = 0;
};

/**
 * Main regression type.
 */
class ParserTestInput : public AbstractBase, public MarkerInterface, public CallableMarker {
public:
    /** Field documentation comment. */
    static const int PUBLIC_STATIC_FINAL_INT = 42;

private:
    static bool privateStaticVolatileFlag;
    std::string protectedTransientField;
    double finalArrayField[2];
    std::map<std::string, std::vector<int> > nestedGenericField;

public:
    ParserTestInput()
        : AbstractBase(0), protectedTransientField(), nestedGenericField() {
        finalArrayField[0] = 1.0;
        finalArrayField[1] = 2.0;
    }

    ParserTestInput(const std::string &name, const std::vector<int> &values)
        : AbstractBase(static_cast<int>(values.size())),
          protectedTransientField(name),
          nestedGenericField() {
        finalArrayField[0] = 1.0;
        finalArrayField[1] = 2.0;
    }

    void run() {
        if (PUBLIC_STATIC_FINAL_INT <= 0) {
            std::abort();
        }
    }

    virtual std::string call() {
        return doWork(true, 1, 2, 'x', 3, 4L, 5.0f, 6.0, 0) ? "ok" : "no";
    }

    template <typename T>
    T genericMethod(T value) {
        return value;
    }

    bool doWork(
        bool flag, unsigned char b, short s, char c,
        int i, long l, float f, double d, const void *obj) {
        if (flag) { return true; }
        if (obj != 0) { return false; }

        switch (i) {
            case 0:
                break;
            case 1:
                for (int n = 0; n < 3; n++) {
                    if (n == 1) { continue; }
                    if (n == 2) { break; }
                }
                break;
            default:
                while (s > 0) { s--; }
                do { b++; } while (b < 3);
                break;
        }

        if (l < 0L) { /* error path */ }
        privateStaticVolatileFlag = std::fabs(static_cast<double>(i)) > 0.0;
        nestedGenericField["key"] = std::vector<int>();
        return f > 0.0f && d > 0.0 && c != '\0';
    }

    virtual int abstractHook() { return getBaseValue(); }
    virtual void mark() {}

    class StaticNested {
    private:
        int nestedField;
    public:
        explicit StaticNested(int nestedField) : nestedField(nestedField) {}
        int getNestedField() const { return nestedField; }
    };
};

bool ParserTestInput::privateStaticVolatileFlag = false;

void packagePrivateStaticMethod() {}

std::string moduleHelper(const std::string &path, MarkerInterface *ignored) {
    (void)ignored;
    return path;
}

bool nestedEnumIsAlpha(NestedEnum value) {
    return value == NESTED_ALPHA;
}
