package rulescript_test

import "testing"

func TestFoldNeTrue(t *testing.T) {
	rule := loadRule(t, "FoldNeTrue")
	e := newEnv(t)
	s, cols := e.scan("t", 3)
	gt := e.f.ConstructGt(e.v(cols[1]), e.num(5))
	before := e.f.ConstructSelect(s, e.filters(e.f.ConstructNe(gt, e.f.ConstructTrue())))
	want := e.f.ConstructSelect(s, e.filters(e.f.ConstructNot(gt)))
	e.expectRewrite(rule, before, want)
	e.expectNoMatch(rule, e.f.ConstructSelect(s, e.filters(e.f.ConstructEq(gt, e.f.ConstructTrue()))))
	e.expectNoMatch(rule, e.f.ConstructSelect(s, e.filters(e.f.ConstructNe(gt, e.f.ConstructFalse()), e.f.ConstructEq(e.v(cols[2]), e.num(1)))))
}
