/**
 * Comprehensive TSX parser test input covering programming constructs for regression testing.
 *
 * Includes types, interfaces, classes, functions, and a simple React-like component returning JSX.
 */

import * as React from 'react';
import { basename } from 'path';

// Single-line comment

/* Single-line block comment */

/*
 * Multi-line
 * block comment
 */

/** Props for the sample component. */
export interface ParserTestInputProps {
  title: string;
  count?: number;
}

/** Marker-style interface. */
export interface MarkerInterface {
  mark(): void;
}

/** Nested enum. */
export enum NestedEnum {
  Alpha = 'ALPHA',
  Beta = 'BETA',
  Gamma = 'GAMMA',
}

/**
 * Abstract base class.
 */
export abstract class AbstractBase {
  private readonly baseValue: number;

  protected constructor(baseValue: number) {
    this.baseValue = baseValue;
  }

  public getBaseValue(): number {
    return this.baseValue;
  }

  public abstract abstractHook(): number;
}

/**
 * Main regression type.
 */
export class ParserTestInput extends AbstractBase implements MarkerInterface {
  /** Field documentation comment. */
  public static readonly PUBLIC_STATIC_FINAL_INT: number = 42;

  protected protectedTransientField: string | null = null;

  private nestedGenericField: Map<string, number[]> = new Map();

  public constructor(name?: string) {
    super(0);
    this.protectedTransientField = name ?? null;
  }

  public run(): void {
    if (ParserTestInput.PUBLIC_STATIC_FINAL_INT <= 0) {
      throw new Error('constant must be positive');
    }
  }

  public genericMethod<T>(value: T): T {
    return value;
  }

  public doWork(flag: boolean, i: number, obj: unknown): boolean {
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

  public abstractHook(): number {
    return this.getBaseValue();
  }

  public mark(): void {
    // empty
  }
}

/**
 * Nested-type stand-in.
 */
export class StaticNested {
  private nestedField: number;

  public constructor(nestedField: number) {
    this.nestedField = nestedField;
  }

  public getNestedField(): number {
    return this.nestedField;
  }
}

/**
 * Simple React-like functional component returning JSX.
 */
export function ParserTestInputView(props: ParserTestInputProps): React.ReactElement {
  const model = new ParserTestInput(props.title);
  model.run();
  const label = basename(props.title || 'fixture');

  return (
    <div className="parser-test-input">
      <h1>{label}</h1>
      <p>Count: {props.count ?? 0}</p>
      <span>{NestedEnum.Alpha}</span>
    </div>
  );
}

/** Package-level function. */
export function packagePrivateStaticMethod(): void {
  // empty
}

/** Module helper with parameters. */
export function moduleHelper(path: string): string {
  return basename(path);
}
