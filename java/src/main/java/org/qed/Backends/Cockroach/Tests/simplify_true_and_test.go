package rulescript_test

import (
	"testing"
)

func TestSimplifyTrueAnd(t *testing.T) {
	rule := loadRule(t, "SimplifyTrueAnd")
	e := newEnv(t)

	input, cols := e.scan("t", 3)
	right := e.f.ConstructEq(e.v(cols[1]), e.num(7))
	before := e.f.ConstructSelect(input, e.filters(e.f.ConstructAnd(e.f.ConstructTrue(), right)))
	want := e.f.ConstructSelect(input, e.filters(right))
	e.expectRewrite(rule, before, want)

	input2, cols2 := e.scan("u", 2)
	nearMiss := e.f.ConstructSelect(input2, e.filters(e.f.ConstructOr(e.v(cols2[1]), e.f.ConstructTrue())))
	e.expectNoMatch(rule, nearMiss)
}
