package rulescript_test

import (
	"testing"
)

func TestNegateAnd(t *testing.T) {
	rule := loadRule(t, "NegateAnd")
	e := newEnv(t)
	s, cols := e.scan("u", 2)
	p := e.f.ConstructGt(e.v(cols[0]), e.num(0))
	q := e.f.ConstructLt(e.v(cols[1]), e.num(5))
	before := e.f.ConstructSelect(s, e.filters(e.f.ConstructNot(e.f.ConstructAnd(p, q))))
	want := e.f.ConstructSelect(s, e.filters(e.f.ConstructOr(e.f.ConstructNot(p), e.f.ConstructNot(q))))
	e.expectRewrite(rule, before, want)
	e.expectNoMatch(rule, e.f.ConstructSelect(s, e.filters(e.f.ConstructAnd(p, q))))
	e.expectNoMatch(rule, e.f.ConstructSelect(s, e.filters(e.f.ConstructNot(e.f.ConstructOr(p, q)))))
}

func TestSQLNegateAnd(t *testing.T) {
	expectSQLFires(t, "NegateAnd", "SELECT x FROM u WHERE NOT (x > 0 AND y < 5)")
}
