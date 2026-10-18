package rulescript_test

import "testing"

func TestSimplifyIsCondition(t *testing.T) {
	rule := loadRule(t, "SimplifyIsCondition")
	e := newEnv(t)
	input, cols := e.scan("nn", 2)
	left, right := e.v(cols[0]), e.v(cols[1])
	other := e.f.ConstructEq(left, e.num(5))
	filtered := e.f.ConstructSelect(input, e.filters(other))
	before := e.f.ConstructSelect(filtered, e.filters(e.f.ConstructIs(left, right)))
	wantFiltered := e.f.ConstructSelect(input, e.filters(other))
	want := e.f.ConstructSelect(wantFiltered, e.filters(e.f.ConstructEq(left, right)))
	e.expectRewrite(rule, before, want)

	input2, cols2 := e.scan("t", 2)
	left2, right2 := e.v(cols2[0]), e.v(cols2[1])
	nearMiss := e.f.ConstructSelect(input2, e.filters(e.f.ConstructIs(right2, left2)))
	e.expectNoMatch(rule, nearMiss)

}
