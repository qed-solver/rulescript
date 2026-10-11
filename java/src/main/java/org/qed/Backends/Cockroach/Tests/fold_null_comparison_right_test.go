package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestFoldNullComparisonRight(t *testing.T) {
	rule := loadRule(t, "FoldNullComparisonRight")
	e := newEnv(t)
	s, cols := e.scan("u", 1)
	nullBool := e.f.ConstructNull(types.Bool)
	left := e.f.ConstructGt(e.v(cols[0]), e.num(0))
	e.expectRewrite(rule, e.f.ConstructSelect(s, e.filters(e.f.ConstructEq(left, nullBool))), e.f.ConstructSelect(s, e.filters(nullBool)))
	e.expectNoMatch(rule, e.f.ConstructSelect(s, e.filters(e.f.ConstructEq(nullBool, left))))
	e.expectNoMatch(rule, e.f.ConstructSelect(s, e.filters(e.f.ConstructEq(left, e.f.ConstructFalse()))))
}
