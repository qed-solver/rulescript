package rulescript_test

import "testing"

func TestFoldEqFalse(t *testing.T) {
	rule := loadRule(t, "FoldEqFalse")
	e := newEnv(t)
	s, sc := e.scan("t", 3)
	pred := e.f.ConstructEq(e.v(sc[0]), e.num(1))
	before := e.f.ConstructSelect(s, e.filters(e.f.ConstructEq(pred, e.f.ConstructFalse())))
	want := e.f.ConstructSelect(s, e.filters(e.f.ConstructNot(pred)))
	e.expectRewrite(rule, before, want)
	e.expectNoMatch(rule, e.f.ConstructSelect(s, e.filters(e.f.ConstructEq(pred, e.f.ConstructTrue()))))
	e.expectNoMatch(rule, e.f.ConstructSelect(s, e.filters(e.f.ConstructNe(pred, e.f.ConstructFalse()))))
	e.expectNoMatch(rule, e.f.ConstructSelect(s, e.filters(e.f.ConstructNot(pred))))
}
