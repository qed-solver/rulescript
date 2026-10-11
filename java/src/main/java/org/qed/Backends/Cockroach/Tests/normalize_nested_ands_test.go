package rulescript_test

import (
	"testing"
)

func TestNormalizeNestedAnds(t *testing.T) {
	rule := loadRule(t, "NormalizeNestedAnds")
	e := newEnv(t)
	s, cols := e.scan("u", 2)
	p := e.f.ConstructGt(e.v(cols[0]), e.num(0))
	q := e.f.ConstructLt(e.v(cols[1]), e.num(5))
	r := e.f.ConstructNe(e.v(cols[0]), e.num(1))
	before := e.f.ConstructSelect(s, e.filters(e.f.ConstructAnd(p, e.f.ConstructAnd(q, r))))
	want := e.f.ConstructSelect(s, e.filters(e.f.ConstructAnd(e.f.ConstructAnd(p, q), r)))
	e.expectRewrite(rule, before, want)
	e.expectNoMatch(rule, e.f.ConstructSelect(s, e.filters(e.f.ConstructAnd(e.f.ConstructAnd(p, q), r))))
	e.expectNoMatch(rule, e.f.ConstructSelect(s, e.filters(e.f.ConstructOr(p, e.f.ConstructAnd(q, r)))))
}

func TestSQLNormalizeNestedAnds(t *testing.T) {
	expectSQLFires(t, "NormalizeNestedAnds", "SELECT x FROM u WHERE x > 0 AND (y < 5 AND x <> 1)")
}
