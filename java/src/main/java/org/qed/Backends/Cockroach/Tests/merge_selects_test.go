package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestMergeSelects(t *testing.T) {
	rule := loadRule(t, "MergeSelects")
	e := newEnv(t)
	input, cols := e.scan("t", 3)
	inner := e.f.ConstructFiltersItem(e.f.ConstructLt(e.v(cols[1]), e.num(20)))
	outer := e.f.ConstructFiltersItem(e.f.ConstructGt(e.v(cols[2]), e.num(5)))
	before := e.f.ConstructSelect(e.f.ConstructSelect(input, memo.FiltersExpr{inner}), memo.FiltersExpr{outer})
	want := e.f.ConstructSelect(input, memo.FiltersExpr{inner, outer})
	e.expectRewrite(rule, before, want)

	nearMiss := e.f.ConstructSelect(input, memo.FiltersExpr{inner, outer})
	e.expectNoMatch(rule, nearMiss)
}
