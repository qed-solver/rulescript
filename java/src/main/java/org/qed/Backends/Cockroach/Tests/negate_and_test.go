package rulescript_test

import (
	"testing"
)

func TestNegateAnd(t *testing.T) {
	rule := loadRule(t, "NegateAnd")
	e := newEnv(t)
	scan, cols := e.scan("t", 3)
	left := e.f.ConstructGt(e.v(cols[1]), e.num(5))
	right := e.f.ConstructLt(e.v(cols[2]), e.num(9))
	before := e.f.ConstructSelect(scan, e.filters(e.f.ConstructNot(e.f.ConstructAnd(left, right))))
	want := e.f.ConstructSelect(scan, e.filters(e.f.ConstructOr(e.f.ConstructNot(left), e.f.ConstructNot(right))))
	e.expectRewrite(rule, before, want)

	nearMiss := e.f.ConstructSelect(scan, e.filters(
		e.f.ConstructNot(e.f.ConstructAnd(left, right)),
		e.f.ConstructEq(e.v(cols[0]), e.num(4)),
	))
	e.expectNoMatch(rule, nearMiss)
}
