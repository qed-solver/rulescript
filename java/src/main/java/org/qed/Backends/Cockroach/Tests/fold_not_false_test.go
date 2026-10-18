package rulescript_test

import (
	"testing"
)

func TestFoldNotFalse(t *testing.T) {
	rule := loadRule(t, "FoldNotFalse")
	e := newEnv(t)
	s, sc := e.scan("t", 3)
	before := e.f.ConstructSelect(s, e.filters(e.f.ConstructNot(e.f.ConstructFalse())))
	e.expectRewrite(rule, before, s)
	e.expectNoMatch(rule, e.f.ConstructSelect(s, e.filters(e.f.ConstructNot(e.f.ConstructEq(e.v(sc[0]), e.num(1))))))
}
