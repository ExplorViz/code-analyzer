<?php
/**
 * Comprehensive PHP parser test input for regression testing.
 *
 * Includes namespace, use, class, interface, enum, methods, and global functions.
 */

namespace ExplorViz\Code\Analysis\Fixtures;

use ExplorViz\Code\Analysis\Fixtures\Support\Closeable;
use ExplorViz\Code\Analysis\Fixtures\Support\IOException;

// Single-line comment

/* Single-line block comment */

/*
 * Multi-line
 * block comment
 */

/** Abstract base class. */
abstract class AbstractBase
{
    /** @var int */
    private $baseValue;

    protected function __construct(int $baseValue)
    {
        $this->baseValue = $baseValue;
    }

    public function getBaseValue(): int
    {
        return $this->baseValue;
    }

    abstract public function abstractHook(): int;
}

/** Marker interface. */
interface MarkerInterface
{
    public function mark(): void;
    public function defaultMark(): void;
}

/** Nested-style interface. */
interface NestedInterface extends MarkerInterface
{
    public function abstractInterfaceMethod(): void;
}

/** Top-level enum. */
enum TopLevelEnum
{
    case ONE;
    case TWO;
    case THREE;
}

/** Nested enum. */
enum NestedEnum
{
    case ALPHA;
    case BETA;
    case GAMMA;

    public function isAlpha(): bool
    {
        return $this === NestedEnum::ALPHA;
    }
}

/**
 * Main regression type.
 */
class ParserTestInput extends AbstractBase implements MarkerInterface
{
    /** Field documentation comment. */
    public const PUBLIC_STATIC_FINAL_INT = 42;

    /** @var bool */
    private static $privateStaticVolatileFlag = false;

    /** @var string|null */
    protected $protectedTransientField;

    /** @var float[] */
    private $finalArrayField = [1.0, 2.0];

    /** @var array<string, int[]> */
    private $nestedGenericField;

    public function __construct()
    {
        parent::__construct(0);
        $this->nestedGenericField = [];
    }

    /** @param int ...$values */
    public static function withName(?string $name, int ...$values): self
    {
        $instance = new self();
        $instance->protectedTransientField = $name;
        $instance->nestedGenericField['values'] = $values;
        return $instance;
    }

    public function run(): void
    {
        assert(self::PUBLIC_STATIC_FINAL_INT > 0);
    }

    public function call(): string
    {
        return $this->doWork(true, 1, 2, 'x', 3, 4, 5.0, 6.0, null) ? 'ok' : 'no';
    }

    /** @param mixed $value @return mixed */
    public function genericMethod($value)
    {
        return $value;
    }

    protected function doWork(
        bool $flag, int $b, int $s, string $c, int $i, int $l, float $f, float $d, $obj
    ): bool {
        if ($flag) { return true; }
        if (is_string($obj)) { return false; }

        switch ($i) {
            case 0:
                break;
            case 1:
                for ($n = 0; $n < 3; $n++) {
                    if ($n === 1) { continue; }
                    if ($n === 2) { break; }
                }
                break;
            default:
                while ($s > 0) { $s--; }
                do { $b++; } while ($b < 3);
                break;
        }

        try {
            if ($l < 0) { throw new IOException('negative'); }
        } catch (IOException $ex) {
            throw $ex;
        } finally {
            self::$privateStaticVolatileFlag = abs($i) > 0;
        }

        $this->nestedGenericField['key'] = [];
        return $f > 0.0 && $d > 0.0 && $c !== "\0";
    }

    public function abstractHook(): int
    {
        return $this->getBaseValue();
    }

    public function mark(): void {}

    public function defaultMark(): void
    {
        $this->mark();
    }
}

class StaticNested
{
    /** @var int */
    private $nestedField;

    public function __construct(int $nestedField)
    {
        $this->nestedField = $nestedField;
    }

    public function getNestedField(): int
    {
        return $this->nestedField;
    }
}

function package_private_static_method(): void {}

function module_helper(string $path, Closeable $ignored): string
{
    return basename($path);
}
