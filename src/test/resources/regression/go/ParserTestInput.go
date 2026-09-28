// Package fixtures provides Go parser regression constructs.
package fixtures

import (
	"fmt"
	"io"
	"math"
)

// Single-line comment

/*
 * Multi-line
 * block comment
 */

// AbstractBase is a base-like struct.
type AbstractBase struct {
	// Field comment
	BaseValue int
}

// GetBaseValue returns the stored base value.
func (a *AbstractBase) GetBaseValue() int {
	return a.BaseValue
}

// MarkerInterface describes a small interface surface.
type MarkerInterface interface {
	Mark()
	DefaultMark()
}

// NestedInterface extends MarkerInterface.
type NestedInterface interface {
	MarkerInterface
	AbstractInterfaceMethod()
}

// NestedEnum is an iota-based enumeration stand-in.
type NestedEnum int

const (
	Alpha NestedEnum = iota
	Beta
	Gamma
)

// IsAlpha reports whether the enum value is Alpha.
func (e NestedEnum) IsAlpha() bool {
	return e == Alpha
}

// ParserTestInput is the main regression type.
type ParserTestInput struct {
	AbstractBase
	/* Field block comment */
	ProtectedTransientField string
	nestedGenericField      map[string][]int
	FinalArrayField         [2]float64
}

const PublicStaticFinalInt = 42

var privateStaticVolatileFlag bool

// NewParserTestInput constructs a ParserTestInput.
func NewParserTestInput() *ParserTestInput {
	return &ParserTestInput{
		AbstractBase:       AbstractBase{BaseValue: 0},
		nestedGenericField: make(map[string][]int),
		FinalArrayField:    [2]float64{1.0, 2.0},
	}
}

// NewParserTestInputWithName constructs with name and values.
func NewParserTestInputWithName(name string, values ...int) *ParserTestInput {
	base := 0
	if values != nil {
		base = len(values)
	}
	return &ParserTestInput{
		AbstractBase:            AbstractBase{BaseValue: base},
		ProtectedTransientField: name,
		nestedGenericField:      make(map[string][]int),
	}
}

func (p *ParserTestInput) Run() {
	assertPositive(PublicStaticFinalInt)
}

func (p *ParserTestInput) Call() string {
	_ = p.DoWork(true, 1, 2, 'x', 3, 4, 5.0, 6.0, nil)
	return "ok"
}

func (p *ParserTestInput) GenericMethod(value interface{}) interface{} {
	return value
}

func (p *ParserTestInput) DoWork(
	flag bool, b byte, s int16, c rune, i int, l int64, f float32, d float64, obj interface{},
) bool {
	if flag {
		return true
	}
	if _, ok := obj.(string); ok {
		return false
	}
	switch i {
	case 0:
	case 1:
		for n := 0; n < 3; n++ {
			if n == 1 {
				continue
			}
			if n == 2 {
				break
			}
		}
	default:
		for s > 0 {
			s--
		}
	}
	if l < 0 {
		_ = fmt.Errorf("negative")
	}
	privateStaticVolatileFlag = math.Abs(float64(i)) > 0
	p.nestedGenericField["key"] = []int{}
	return f > 0.0 && d > 0.0 && c != 0
}

func (p *ParserTestInput) AbstractHook() int {
	return p.GetBaseValue()
}

func (p *ParserTestInput) Mark() {}

func (p *ParserTestInput) DefaultMark() {
	p.Mark()
}

// StaticNested is a nested-type stand-in.
type StaticNested struct {
	nestedField int
}

func NewStaticNested(nestedField int) *StaticNested {
	return &StaticNested{nestedField: nestedField}
}

func (s *StaticNested) GetNestedField() int {
	return s.nestedField
}

func PackagePrivateStaticMethod() {}

func ModuleHelper(path string, w io.Writer) (string, error) {
	_, err := io.WriteString(w, path)
	return path, err
}

func assertPositive(v int) {
	if v <= 0 {
		panic("constant must be positive")
	}
}
