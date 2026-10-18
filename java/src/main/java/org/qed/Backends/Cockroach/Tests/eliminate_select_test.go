package rulescript_test

import (
	"testing"
)

func TestEliminateSelect(t *testing.T) {
	rule := loadRule(t, "EliminateSelect")
	e := newEnv(t)
	s, cols := e.scan("t", 3)
	before := e.f.ConstructSelect(s, e.filters())
	e.expectRewrite(rule, before, s)
	e.expectRewrite(rule, e.f.ConstructSelect(s, e.filters(e.f.ConstructTrue())), s)
	e.expectNoMatch(rule, e.f.ConstructSelect(s, e.filters(e.f.ConstructFalse())))
	e.expectNoMatch(rule, e.f.ConstructSelect(s, e.filters(e.f.ConstructEq(e.v(cols[0]), e.num(1)))))
}
