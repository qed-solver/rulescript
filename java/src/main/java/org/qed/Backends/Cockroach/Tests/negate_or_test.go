package rulescript_test

import (
	"testing"
)

func TestNegateOr(t *testing.T) {
	rule := loadRule(t, "NegateOr")
	e := newEnv(t)
	s, cols := e.scan("u", 2)
	left := e.f.ConstructEq(e.v(cols[0]), e.num(1))
	right := e.f.ConstructEq(e.v(cols[1]), e.num(2))
	before := e.f.ConstructSelect(s, e.filters(e.f.ConstructNot(e.f.ConstructOr(left, right))))
	want := e.f.ConstructSelect(s, e.filters(e.f.ConstructNot(left), e.f.ConstructNot(right)))
	e.expectRewrite(rule, before, want)
	e.expectNoMatch(rule, e.f.ConstructSelect(s, e.filters(e.f.ConstructOr(left, right))))
	e.expectNoMatch(rule, e.f.ConstructSelect(s, e.filters(e.f.ConstructNot(e.f.ConstructAnd(left, right)))))
}

func TestSQLNegateOr(t *testing.T) {
	expectSQLRewrites(t, "NegateOr", "SELECT x FROM u WHERE NOT ((x > 0) OR (y > 0))")
}
