package rulescript_test

import "testing"

func TestEliminateNot(t *testing.T) {
	rule := loadRule(t, "EliminateNot")
	e := newEnv(t)
	s, sc := e.scan("t", 3)
	cond := e.f.ConstructEq(e.v(sc[0]), e.num(1))
	before := e.f.ConstructSelect(s, e.filters(e.f.ConstructNot(e.f.ConstructNot(cond))))
	want := e.f.ConstructSelect(s, e.filters(cond))
	e.expectRewrite(rule, before, want)
	e.expectNoMatch(rule, e.f.ConstructSelect(s, e.filters(cond)))
	e.expectNoMatch(rule, e.f.ConstructSelect(s, e.filters(e.f.ConstructNot(cond))))
}
