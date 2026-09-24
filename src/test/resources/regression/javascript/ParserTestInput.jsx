/**
 * Comprehensive JSX parser test input covering programming constructs for regression testing.
 *
 * Plain JSX component plus classes and functions (no TypeScript types).
 */

import * as React from 'react';
import { basename } from 'path';

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

  constructor(name) {
    super(0);
    this.protectedTransientField = name || null;
    this.nestedGenericField = new Map();
  }

  run() {
    if (ParserTestInput.PUBLIC_STATIC_FINAL_INT <= 0) {
      throw new Error('constant must be positive');
    }
  }

  genericMethod(value) {
    return value;
  }

  doWork(flag, i, obj) {
    if (flag) {
      return true;
    }
    if (typeof obj === 'string') {
      return false;
    }
    for (let n = 0; n < 3; n++) {
      if (n === 1) {
        continue;
      }
      if (n === i) {
        break;
      }
    }
    this.nestedGenericField.set('key', []);
    return true;
  }

  abstractHook() {
    return this.getBaseValue();
  }

  mark() {
    // empty
  }
}

/**
 * Nested-type stand-in.
 */
export class StaticNested {
  constructor(nestedField) {
    this.nestedField = nestedField;
  }

  getNestedField() {
    return this.nestedField;
  }
}

/** Nested enum stand-in. */
export const NestedEnum = Object.freeze({
  Alpha: 'ALPHA',
  Beta: 'BETA',
  Gamma: 'GAMMA',
});

/**
 * Simple React-like functional component returning JSX.
 */
export function ParserTestInputView(props) {
  const model = new ParserTestInput(props.title);
  model.run();
  const label = basename(props.title || 'fixture');

  return (
    <div className="parser-test-input">
      <h1>{label}</h1>
      <p>Count: {props.count || 0}</p>
      <span>{NestedEnum.Alpha}</span>
    </div>
  );
}

/** Minimal JSX helper. */
export function TinyBadge() {
  return <div>Hi</div>;
}

/** Package-level function. */
export function packagePrivateStaticMethod() {
  // empty
}

/** Module helper with parameters. */
export function moduleHelper(path) {
  return basename(path);
}
