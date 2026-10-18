package rulescript_test

import (
	"testing"
)

func TestNormalizeInConst(t *testing.T) {
	rule := loadRule(t, "NormalizeInConst")
	e := newEnv(t)
	input, cols := e.scan("t", 3)
	x := e.v(cols[0])
	a := e.v(cols[1])
	b := e.v(cols[2])
	eqA := e.f.ConstructEq(x, a)
	eqB := e.f.ConstructEq(x, b)
	before := e.f.ConstructSelect(input, e.filters(e.f.ConstructOr(
		e.f.ConstructOr(e.f.ConstructOr(eqA, eqB), eqA), eqB,
	)))
	want := e.f.ConstructSelect(input, e.filters(e.f.ConstructOr(eqA, eqB)))
	if e.format(before) == e.format(want) {
		t.Log("factory normalized repeated filter predicates at construction")
	} else {
		e.expectRewrite(rule, before, want)
	}
	nearMiss := e.f.ConstructSelect(input, e.filters(e.f.ConstructOr(
		e.f.ConstructOr(e.f.ConstructOr(eqA, eqB), e.f.ConstructEq(a, x)), eqB,
	)))
	e.expectNoMatch(rule, nearMiss)
}
