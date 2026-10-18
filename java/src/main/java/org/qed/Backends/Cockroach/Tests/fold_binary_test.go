package rulescript_test

import "testing"

func TestFoldBinary(t *testing.T) {
	rule := loadRule(t, "FoldBinary")
	e := newEnv(t)
	s, _ := e.scan("t", 3)
	before := e.f.ConstructSelect(s, e.filters(e.f.ConstructAnd(e.f.ConstructTrue(), e.f.ConstructFalse())))
	e.expectRewrite(rule, before, e.f.ConstructSelect(s, e.filters(e.f.ConstructFalse())))
	e.expectNoMatch(rule, e.f.ConstructSelect(s, e.filters(e.f.ConstructOr(e.f.ConstructTrue(), e.f.ConstructFalse()))))
	e.expectNoMatch(rule, e.f.ConstructSelect(s, e.filters(e.f.ConstructAnd(e.f.ConstructTrue(), e.f.ConstructTrue()))))
}
