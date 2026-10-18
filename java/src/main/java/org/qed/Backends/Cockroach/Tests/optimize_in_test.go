package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestOptimizeIn(t *testing.T) {
	rule := loadRule(t, "OptimizeIn")
	e := newEnv(t)
	scan, cols := e.scan("t", 2)
	left := e.v(cols[0])
	right := e.v(cols[1])
	eq := e.f.ConstructEq(left, right)
	before := e.f.ConstructSelect(scan, memo.FiltersExpr{
		e.f.ConstructFiltersItem(e.f.ConstructOr(eq, eq)),
	})
	want := e.f.ConstructSelect(scan, memo.FiltersExpr{
		e.f.ConstructFiltersItem(e.f.ConstructEq(left, right)),
	})
	e.expectRewrite(rule, before, want)
	nearMiss := e.f.ConstructSelect(scan, memo.FiltersExpr{
		e.f.ConstructFiltersItem(e.f.ConstructOr(eq, e.f.ConstructEq(left, left))),
	})
	e.expectNoMatch(rule, nearMiss)
}
