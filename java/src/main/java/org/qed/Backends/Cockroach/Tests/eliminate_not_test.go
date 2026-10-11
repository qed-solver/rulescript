package rulescript_test

import (
	"testing"
)

func TestEliminateNot(t *testing.T) {
	rule := loadRule(t, "EliminateNot")
	e := newEnv(t)
	s, cols := e.scan("u", 2)
	pred := e.f.ConstructEq(e.v(cols[0]), e.num(1))
	before := e.f.ConstructSelect(s, e.filters(e.f.ConstructNot(e.f.ConstructNot(pred))))
	want := e.f.ConstructSelect(s, e.filters(pred))
	e.expectRewrite(rule, before, want)
	e.expectNoMatch(rule, e.f.ConstructSelect(s, e.filters(e.f.ConstructNot(pred))))
	e.expectNoMatch(rule, want)
}

func TestSQLEliminateNot(t *testing.T) {
	expectSQLRewrites(t, "EliminateNot", "SELECT x FROM u WHERE NOT NOT (x > 0)")
}
