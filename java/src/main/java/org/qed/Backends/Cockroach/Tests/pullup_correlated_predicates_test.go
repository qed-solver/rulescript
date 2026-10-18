package rulescript_test

import "testing"

func TestPullupCorrelatedPredicates(t *testing.T) {
	e := newEnv(t)
	rule := loadRule(t, "PullupCorrelatedPredicates")

	outer, outerCols := e.scan("t", 3)
	inner, innerCols := e.scan("u", 2)
	pulled := e.f.ConstructEq(e.v(outerCols[0]), e.num(1))
	local := e.f.ConstructEq(e.v(innerCols[1]), e.num(2))
	outerCond := e.f.ConstructEq(e.v(outerCols[1]), e.num(3))
	existsInput := e.f.ConstructSelect(inner, e.filters(pulled, local))
	before := e.f.ConstructSelect(outer, e.filters(outerCond, e.exists(existsInput)))

	wantInner := e.f.ConstructSelect(inner, e.filters(local))
	want := e.f.ConstructSelect(outer, e.filters(e.f.ConstructAnd(outerCond, e.f.ConstructAnd(pulled, e.exists(wantInner)))))
	e.expectRewrite(rule, before, want)

	nearMissInput := e.f.ConstructSelect(inner, e.filters(pulled))
	nearMiss := e.f.ConstructSelect(outer, e.filters(outerCond, e.exists(nearMissInput)))
	e.expectNoMatch(rule, nearMiss)
}
