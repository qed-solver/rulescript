package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestRejectNullsRightJoin(t *testing.T) {
	e := newEnv(t)
	rule := loadRule(t, "RejectNullsRightJoin")

	left, leftCols := e.scan("u", 1)
	right, _ := e.scan("t", 1)
	on := memo.FiltersExpr{e.f.ConstructFiltersItem(e.f.ConstructEq(e.v(leftCols[0]), e.num(1)))}
	before := e.f.ConstructSelect(
		e.f.ConstructFullJoin(left, right, on, &memo.JoinPrivate{}),
		e.filters(e.f.ConstructIsNot(e.v(leftCols[0]), memo.NullSingleton)),
	)

	wantOn := memo.FiltersExpr{e.f.ConstructFiltersItem(e.f.ConstructEq(e.v(leftCols[0]), e.num(1)))}
	want := e.f.ConstructSelect(
		e.f.ConstructLeftJoin(left, right, wantOn, &memo.JoinPrivate{}),
		e.filters(e.f.ConstructIsNot(e.v(leftCols[0]), memo.NullSingleton)),
	)
	e.expectRewrite(rule, before, want)

	missLeft, missLeftCols := e.scan("u", 1)
	missRight, missRightCols := e.scan("t", 1)
	missOn := memo.FiltersExpr{e.f.ConstructFiltersItem(e.f.ConstructEq(e.v(missLeftCols[0]), e.num(1)))}
	nearMiss := e.f.ConstructSelect(
		e.f.ConstructFullJoin(missLeft, missRight, missOn, &memo.JoinPrivate{}),
		e.filters(e.f.ConstructIsNot(e.v(missRightCols[0]), memo.NullSingleton)),
	)
	e.expectNoMatch(rule, nearMiss)
}
