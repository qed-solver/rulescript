package rulescript_test

import (
	"testing"
)

func TestFoldEqTrue(t *testing.T) {
	rule := loadRule(t, "FoldEqTrue")
	e := newEnv(t)
	src, cols := e.scan("t", 3)
	left := e.f.ConstructGt(e.v(cols[1]), e.num(0))
	before := e.f.ConstructSelect(src, e.filters(e.f.ConstructEq(left, e.f.ConstructTrue())))
	want := e.f.ConstructSelect(src, e.filters(left))
	e.expectRewrite(rule, before, want)
	e.expectNoMatch(rule, e.f.ConstructSelect(src, e.filters(e.f.ConstructEq(left, e.f.ConstructFalse()))))
	e.expectNoMatch(rule, e.f.ConstructSelect(src, e.filters(e.f.ConstructNe(left, e.f.ConstructTrue()))))
}
