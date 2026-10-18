package rulescript_test

import "testing"

func TestCommuteVar(t *testing.T) {
	rule := loadRule(t, "CommuteVar")
	e := newEnv(t)
	s, c := e.scan("t", 1)
	a := c[0]
	plusOne := e.f.ConstructPlus(e.v(a), e.num(1))
	before := e.f.ConstructSelect(s, e.filters(e.f.ConstructEq(plusOne, e.v(a))))
	want := e.f.ConstructSelect(s, e.filters(e.f.ConstructEq(e.v(a), plusOne)))
	e.expectRewrite(rule, before, want)

	e.expectNoMatch(rule, e.f.ConstructSelect(s, e.filters(e.f.ConstructEq(e.v(a), plusOne))))
	e.expectNoMatch(rule, e.f.ConstructSelect(s, e.filters(e.f.ConstructEq(e.v(a), e.v(a)))))
	e.expectNoMatch(rule, e.f.ConstructSelect(s, e.filters(e.f.ConstructNe(plusOne, e.v(a)))))
}
