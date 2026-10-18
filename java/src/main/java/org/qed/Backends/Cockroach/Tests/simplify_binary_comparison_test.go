package rulescript_test

import "testing"

func TestSimplifyBinaryComparison(t *testing.T) {
	rule := loadRule(t, "SimplifyBinaryComparison")
	e := newEnv(t)

	input, cols := e.scan("nn", 1)
	before := e.f.ConstructSelect(input, e.filters(e.f.ConstructEq(e.v(cols[0]), e.v(cols[0]))))
	want := input
	e.expectRewrite(rule, before, want)

	otherInput, otherCols := e.scan("nn", 1)
	nearMiss := e.f.ConstructSelect(otherInput, e.filters(e.f.ConstructEq(e.v(otherCols[0]), e.num(7))))
	e.expectNoMatch(rule, nearMiss)
}
