/*
 * Comprehensive C parser test input covering programming constructs for regression testing.
 *
 * Includes includes, structs, enums, functions, fields, and comments.
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <math.h>

/* Single-line block comment */

/*
 * Multi-line
 * block comment
 */

// Single-line comment

#define PUBLIC_STATIC_FINAL_INT 42

enum NestedEnum {
    NESTED_ALPHA,
    NESTED_BETA,
    NESTED_GAMMA
};

enum TopLevelEnum {
    TOP_ONE,
    TOP_TWO,
    TOP_THREE
};

struct AbstractBase {
    int base_value;
};

struct StaticNested {
    int nested_field;
};

struct ParserTestInput {
    int base_value;
    /* Field block comment */
    char *transient_field;
    double final_array_field[2];
    int nested_count;
};

static int private_static_volatile_flag = 0;

int nested_enum_is_alpha(int value) {
    return value == 0;
}

void abstract_base_init(struct AbstractBase *base, int base_value) {
    base->base_value = base_value;
}

int abstract_base_get_base_value(struct AbstractBase *base) {
    return base->base_value;
}

void parser_test_input_init(struct ParserTestInput *input) {
    input->base_value = 0;
    input->transient_field = NULL;
    input->final_array_field[0] = 1.0;
    input->final_array_field[1] = 2.0;
    input->nested_count = 0;
}

void parser_test_input_run(struct ParserTestInput *input) {
    if (PUBLIC_STATIC_FINAL_INT <= 0) {
        return;
    }
    (void)input;
}

int parser_test_input_do_work(struct ParserTestInput *input, int flag, int i) {
    int n;
    if (flag) {
        return 1;
    }
    switch (i) {
        case 0:
            break;
        case 1:
            for (n = 0; n < 3; n++) {
                if (n == 2) {
                    break;
                }
            }
            break;
        default:
            while (i > 0) {
                i = i - 1;
            }
            break;
    }
    private_static_volatile_flag = i > 0;
    input->nested_count = 1;
    return private_static_volatile_flag;
}

int parser_test_input_abstract_hook(struct ParserTestInput *input) {
    return input->base_value;
}

void static_nested_init(struct StaticNested *nested, int nested_field) {
    nested->nested_field = nested_field;
}

int static_nested_get_nested_field(struct StaticNested *nested) {
    return nested->nested_field;
}

void package_private_static_method(void) {
}

int module_helper(int value) {
    if (value < 0) {
        return 0 - value;
    }
    return value;
}

int top_level_enum_is_one(int value) {
    return value == 0;
}
