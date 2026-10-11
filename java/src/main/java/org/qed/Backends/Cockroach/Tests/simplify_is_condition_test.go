package rulescript_test

import (
	"testing"
)

func TestSimplifyIsCondition(t *testing.T) {
	rule := loadRule(t, "SimplifyIsCondition")
	e := newEnv(t)
	s, cols := e.scan("u", 2)
	inner := e.f.ConstructSelect(s, e.filters(e.f.ConstructEq(e.v(cols[1]), e.num(1))))
	before := e.f.ConstructSelect(inner, e.filters(e.f.ConstructIs(e.v(cols[0]), e.v(cols[1]))))
	want := e.f.ConstructSelect(inner, e.filters(e.f.ConstructEq(e.v(cols[0]), e.v(cols[1]))))
	e.expectRewrite(rule, before, want)

	e.expectNoMatch(rule, e.f.ConstructSelect(s, e.filters(e.f.ConstructIs(e.v(cols[0]), e.v(cols[1])))))
	e.expectNoMatch(rule, e.f.ConstructSelect(inner, e.filters(e.f.ConstructEq(e.v(cols[0]), e.v(cols[1])))))
}
