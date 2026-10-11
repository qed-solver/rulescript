package rulescript_test

import (
	"testing"
)

func TestExtractRedundantConjunct(t *testing.T) {
	rule := loadRule(t, "ExtractRedundantConjunct")
	e := newEnv(t)
	s, cols := e.scan("u", 2)
	a := e.f.ConstructGt(e.v(cols[0]), e.num(0))
	b := e.f.ConstructLt(e.v(cols[1]), e.num(5))
	c := e.f.ConstructNe(e.v(cols[0]), e.num(1))
	d := e.f.ConstructGt(e.v(cols[0]), e.num(2))
	before := e.f.ConstructSelect(s, e.filters(e.f.ConstructOr(e.f.ConstructAnd(a, b), e.f.ConstructAnd(a, c))))
	want := e.f.ConstructSelect(s, e.filters(a, e.f.ConstructOr(b, c)))
	e.expectRewrite(rule, before, want)
	e.expectNoMatch(rule, e.f.ConstructSelect(s, e.filters(e.f.ConstructOr(e.f.ConstructAnd(a, b), e.f.ConstructAnd(d, c)))))
	e.expectNoMatch(rule, e.f.ConstructSelect(s, e.filters(e.f.ConstructAnd(e.f.ConstructOr(a, b), e.f.ConstructAnd(a, c)))))
}

func TestSQLExtractRedundantConjunct(t *testing.T) {
	expectSQLFires(t, "ExtractRedundantConjunct", "SELECT x FROM u WHERE (x > 0 AND y < 5) OR (x > 0 AND x <> 1)")
}
