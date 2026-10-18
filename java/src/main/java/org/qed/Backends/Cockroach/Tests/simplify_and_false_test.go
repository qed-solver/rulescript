package rulescript_test

import (
	"testing"

)

func TestSimplifyAndFalse(t *testing.T) {
	rule := loadRule(t, "SimplifyAndFalse")
	e := newEnv(t)
	scan, cols := e.scan("t", 3)
	left := e.f.ConstructEq(e.v(cols[1]), e.num(7))
	before := e.f.ConstructSelect(scan, e.filters(e.f.ConstructAnd(left, e.f.ConstructFalse())))
	want := e.f.ConstructSelect(scan, e.filters(e.f.ConstructFalse()))
	e.expectRewrite(rule, before, want)

	nearMissScan, nearMissCols := e.scan("t", 3)
	nearMiss := e.f.ConstructSelect(nearMissScan, e.filters(e.f.ConstructAnd(
		e.f.ConstructEq(e.v(nearMissCols[1]), e.num(7)),
		e.f.ConstructTrue(),
	)))
	e.expectNoMatch(rule, nearMiss)
}
