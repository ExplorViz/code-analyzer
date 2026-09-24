/**
 * Comprehensive TypeScript parser test input for regression testing.
 *
 * Includes imports, interfaces, classes, functions, types, and comments.
 */

import { basename } from 'path';
import type { Readable } from 'stream';
import * as mathHelpers from './mathHelpers';

// Single-line comment

/* Single-line block comment */

/*
 * Multi-line
 * block comment
 */

/** Abstract base class (abstract methods; class-level `abstract` omitted due to grammar ambiguity with abstractDeclaration). */
export class AbstractBase {
  private readonly baseValue: number;

  protected constructor(baseValue: number) {
    this.baseValue = baseValue;
  }

  public getBaseValue(): number {
    return this.baseValue;
  }

  public abstract abstractHook(): number;
}

/** Marker-style interface. */
export interface MarkerInterface {
  mark(): void;
  defaultMark(): void;
}

/** Callable-like interface. */
export interface CallableMarker {
  call(): string;
}

/** Nested-style interface. */
export interface NestedInterface extends MarkerInterface {
  abstractInterfaceMethod(): void;
}

/** Nested enum. */
export enum NestedEnum {
  Alpha,
  Beta,
  Gamma,
}

/** Top-level enum. */
export enum TopLevelEnum {
  One,
  Two,
  Three,
}

/** Point type alias. */
export type Point = {
  x: number;
  y: number;
};

/** Main regression type. */
export class ParserTestInput
  extends AbstractBase
  implements MarkerInterface, CallableMarker
{
  /** Field documentation comment. */
  public static readonly PUBLIC_STATIC_FINAL_INT: number = 42;

  private static privateStaticVolatileFlag: boolean = false;

  protected protectedTransientField: string | null = null;

  private readonly finalArrayField: number[] = [1.0, 2.0];

  private nestedGenericField: Map<string, number[]> = new Map();

  #secretField: string = 'secret';

  public constructor() {
    super(0);
    this.nestedGenericField = new Map();
  }

  public static withName(name: string | null, ...values: number[]): ParserTestInput {
    const instance = new ParserTestInput();
    instance.protectedTransientField = name;
    void values;
    return instance;
  }

  public run(): void {
    if (ParserTestInput.PUBLIC_STATIC_FINAL_INT <= 0) {
      throw new Error('constant must be positive');
    }
  }

  public call(): string {
    return this.doWork(true, 1, 2, 'x', 3, 4, 5.0, 6.0, null) ? 'ok' : 'no';
  }

  public genericMethod<T>(value: T): T {
    return value;
  }

  protected doWork(
    flag: boolean,
    b: number,
    s: number,
    c: string,
    i: number,
    l: number,
    f: number,
    d: number,
    obj: unknown,
  ): boolean {
    if (flag) {
      return true;
    }
    if (typeof obj === 'string') {
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

  public abstractHook(): number {
    return this.getBaseValue();
  }

  public mark(): void {}

  public defaultMark(): void {
    this.mark();
  }
}

/** Nested-type stand-in. */
export class StaticNested {
  private nestedField: number;

  public constructor(nestedField: number) {
    this.nestedField = nestedField;
  }

  public getNestedField(): number {
    return this.nestedField;
  }
}

export function packagePrivateStaticMethod(): void {}

export function moduleHelper(path: string, _ignored?: Readable): string {
  return basename(path) + String(mathHelpers?.IDENTITY ?? 1);
}

export function nestedEnumIsAlpha(value: NestedEnum): boolean {
  return value === NestedEnum.Alpha;
}

namespace Utils {
  export function helper(): number {
    return 1;
  }
}
