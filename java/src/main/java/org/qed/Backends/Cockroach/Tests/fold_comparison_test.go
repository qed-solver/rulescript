package rulescript_test

import (
	"testing"
)

func TestFoldComparison(t *testing.T) {
	rule := loadRule(t, "FoldComparison")
	e := newEnv(t)
	s, _ := e.scan("t", 1)
	before := e.f.ConstructSelect(s, e.filters(e.f.ConstructEq(e.f.ConstructTrue(), e.f.ConstructFalse())))
	want := e.f.ConstructSelect(s, e.filters(e.f.ConstructFalse()))
	e.expectRewrite(rule, before, want)
	e.expectNoMatch(rule, e.f.ConstructSelect(s, e.filters(e.f.ConstructNe(e.f.ConstructTrue(), e.f.ConstructFalse()))))
	e.expectNoMatch(rule, e.f.ConstructSelect(s, e.filters(e.f.ConstructEq(e.f.ConstructTrue(), e.f.ConstructTrue()))))
}
