package rulescript_test

import "testing"

func TestFilterReduceExpressions(t *testing.T) {
	rule := loadRule(t, "FilterReduceExpressions")
	e := newEnv(t)
	s, cols := e.scan("t", 1)
	before := e.f.ConstructSelect(s, e.filters(e.f.ConstructFalse()))
	e.expectRewrite(rule, before, e.empty(cols...))
	nearMiss := e.f.ConstructSelect(s, e.filters(e.f.ConstructEq(e.v(cols[0]), e.num(1))))
	e.expectNoMatch(rule, nearMiss)
}
