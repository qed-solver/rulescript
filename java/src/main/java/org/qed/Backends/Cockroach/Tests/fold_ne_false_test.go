package rulescript_test

import "testing"

func TestFoldNeFalse(t *testing.T) {
	rule := loadRule(t, "FoldNeFalse")
	e := newEnv(t)
	s, sc := e.scan("t", 3)
	left := e.f.ConstructEq(e.v(sc[0]), e.num(1))
	before := e.f.ConstructSelect(s, e.filters(e.f.ConstructNe(left, e.f.ConstructFalse())))
	after := e.f.ConstructSelect(s, e.filters(e.f.ConstructEq(e.v(sc[0]), e.num(1))))
	e.expectRewrite(rule, before, after)
	e.expectNoMatch(rule, e.f.ConstructSelect(s, e.filters(e.f.ConstructNe(e.v(sc[1]), e.num(1)))))
	e.expectNoMatch(rule, e.f.ConstructSelect(s, e.filters(e.f.ConstructEq(left, e.f.ConstructFalse()))))
}
