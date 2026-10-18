package rulescript_test

import "testing"

func TestFoldUnary(t *testing.T) {
	rule := loadRule(t, "FoldUnary")
	e := newEnv(t)
	src, cols := e.scan("t", 3)
	notTrue := e.f.ConstructNot(e.f.ConstructTrue())
	before := e.f.ConstructSelect(src, e.filters(notTrue))
	want := e.f.ConstructSelect(src, e.filters(e.f.ConstructFalse()))
	e.expectRewrite(rule, before, want)
	e.expectNoMatch(rule, e.f.ConstructSelect(src, e.filters(e.f.ConstructNot(e.f.ConstructFalse()))))
	e.expectNoMatch(rule, e.f.ConstructSelect(src, e.filters(e.f.ConstructTrue())))
	e.expectNoMatch(rule, e.f.ConstructSelect(src, e.filters(notTrue, e.f.ConstructEq(e.v(cols[0]), e.num(1)))))
}
