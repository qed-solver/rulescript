package rulescript_test

import (
	"testing"
)

func TestFilterReduceExpressions(t *testing.T) {
	rule := loadRule(t, "FilterReduceExpressions")
	e := newEnv(t)
	s, c := e.scan("t", 1)

	before := e.f.ConstructSelect(s, e.filters(e.f.ConstructFalse()))
	e.expectRewrite(rule, before, e.empty(c...))

	e.expectNoMatch(rule, e.f.ConstructSelect(s, e.filters(e.f.ConstructTrue())))
	e.expectNoMatch(rule, e.f.ConstructSelect(s, e.filters(e.f.ConstructGt(e.v(c[0]), e.num(0)))))
	e.expectNoMatch(rule, s)
}
