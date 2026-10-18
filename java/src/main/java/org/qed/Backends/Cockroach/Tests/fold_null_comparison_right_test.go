package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestFoldNullComparisonRight(t *testing.T) {
	rule := loadRule(t, "FoldNullComparisonRight")
	e := newEnv(t)
	s, cols := e.scan("t", 1)
	boolLeft := e.f.ConstructEq(e.v(cols[0]), e.num(1))
	before := e.f.ConstructSelect(s, e.filters(e.f.ConstructEq(boolLeft, e.f.ConstructNull(types.Bool))))
	want := e.f.ConstructSelect(s, e.filters(e.f.ConstructNull(types.Bool)))
	e.expectRewrite(rule, before, want)
	e.expectNoMatch(rule, e.f.ConstructSelect(s, e.filters(e.f.ConstructNe(boolLeft, e.f.ConstructNull(types.Bool)))))
	e.expectNoMatch(rule, e.f.ConstructSelect(s, e.filters(e.f.ConstructEq(e.f.ConstructNull(types.Bool), boolLeft))))
	e.expectNoMatch(rule, e.f.ConstructSelect(s, e.filters(e.f.ConstructEq(boolLeft, e.f.ConstructEq(e.v(cols[0]), e.num(2))))))
}
