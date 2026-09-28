/**
 * Comprehensive JavaScript parser test input covering programming constructs for regression testing.
 *
 * Plain JS version: classes, functions, imports, and comments (no TypeScript types).
 */

import { basename } from 'path';
import * as mathHelpers from './mathHelpers';

// Single-line comment

/* Single-line block comment */

/*
 * Multi-line
 * block comment
 */

/**
 * Abstract base class.
 */
export class AbstractBase {
  /**
   * @param {number} baseValue
   */
  constructor(baseValue) {
    this._baseValue = baseValue;
  }

  getBaseValue() {
    return this._baseValue;
  }

  abstractHook() {
    return this._baseValue;
  }
}

/**
 * Main regression type.
 */
export class ParserTestInput extends AbstractBase {
  /** Field documentation comment. */
  static PUBLIC_STATIC_FINAL_INT = 42;

  static privateStaticVolatileFlag = false;

  #hidden = null;

  /**
   * No-arg constructor.
   */
  constructor() {
    super(0);
    this.protectedTransientField = null;
    this.finalArrayField = [1.0, 2.0];
    this.nestedGenericField = new Map();
    this.#hidden = 'hidden';
  }

  /**
   * Named constructor with parameters.
   * @param {string|null} name
   * @param {...number} values
   */
  static withName(name, ...values) {
    const instance = new ParserTestInput();
    instance.protectedTransientField = name;
    instance.nestedGenericField = new Map();
    void values;
    return instance;
  }

  run() {
    if (ParserTestInput.PUBLIC_STATIC_FINAL_INT <= 0) {
      throw new Error('constant must be positive');
    }
  }

  call() {
    return this.doWork(true, 1, 2, 'x', 3, 4, 5.0, 6.0, null) ? 'ok' : 'no';
  }

  genericMethod(value) {
    return value;
  }

  doWork(flag, b, s, c, i, l, f, d, obj) {
    if (flag) {
      return true;
    } else if (typeof obj === 'string') {
      return false;
    }

    switch (i) {
      case 0:
        break;
      case 1:
        for (let n = 0; n < 3; n++) {
          if (n === 1) {
            continue;
          }
          if (n === 2) {
            break;
          }
        }
        break;
      default:
        while (s > 0) {
          s--;
        }
        do {
          b++;
        } while (b < 3);
        break;
    }

    try {
      if (l < 0) {
        throw new Error('negative');
      }
    } catch (ex) {
      throw ex;
    } finally {
      ParserTestInput.privateStaticVolatileFlag = Math.abs(i) > 0;
    }

    this.nestedGenericField.set('key', []);
    return f > 0.0 && d > 0.0 && c !== '\0';
  }

  abstractHook() {
    return this.getBaseValue();
  }

  mark() {
    // empty
  }

  defaultMark() {
    this.mark();
  }
}

/**
 * Nested-type stand-in.
 */
export class StaticNested {
  /**
   * @param {number} nestedField
   */
  constructor(nestedField) {
    this.nestedField = nestedField;
  }

  getNestedField() {
    return this.nestedField;
  }
}

/** Nested enum stand-in via frozen object. */
export const NestedEnum = Object.freeze({
  Alpha: 'ALPHA',
  Beta: 'BETA',
  Gamma: 'GAMMA',
});

/** Top-level enum stand-in. */
export const TopLevelEnum = Object.freeze({
  One: 1,
  Two: 2,
  Three: 3,
});

/** Package-level function. */
export function packagePrivateStaticMethod() {
  // empty
}

/**
 * Module helper with parameters.
 * @param {string} path
 * @param {unknown} [_ignored]
 */
export function moduleHelper(path, _ignored) {
  return basename(path) + String(mathHelpers?.IDENTITY ?? 1);
}

export function nestedEnumIsAlpha(value) {
  return value === NestedEnum.Alpha;
}
