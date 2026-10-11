package rulescript_test

import (
	"testing"
)

func TestNormalizeInConst(t *testing.T) {
	rule := loadRule(t, "NormalizeInConst")
	e := newEnv(t)
	s, cols := e.scan("t", 3)
	eqA := e.f.ConstructEq(e.v(cols[0]), e.v(cols[1]))
	eqB := e.f.ConstructEq(e.v(cols[0]), e.v(cols[2]))
	beforeOr := e.f.ConstructOr(e.f.ConstructOr(e.f.ConstructOr(eqA, eqB), eqA), eqB)
	before := e.f.ConstructSelect(s, e.filters(beforeOr))
	want := e.f.ConstructSelect(s, e.filters(e.f.ConstructOr(eqA, eqB)))
	e.expectRewrite(rule, before, want)
	e.expectNoMatch(rule, e.f.ConstructSelect(s, e.filters(e.f.ConstructOr(e.f.ConstructOr(e.f.ConstructOr(eqA, eqB), eqB), eqA))))
	e.expectNoMatch(rule, e.f.ConstructSelect(s, e.filters(e.f.ConstructOr(eqA, eqB))))
}

func TestNormalizeInConstSQL(t *testing.T) {
	sql := "SELECT x FROM (VALUES (1, 2, 3), (4, 5, 6)) AS v(x,a,b) WHERE x=a OR x=b OR x=a OR x=b"
	plan, fired := planFor(t, sql, nil, "NormalizeInConst")
	if len(fired) == 0 {
		t.Fatalf("NormalizeInConst did not fire on %s\n%s", sql, plan)
	}
}

func TestSQLNormalizeInConst(t *testing.T) {
	expectSQLFires(t, "NormalizeInConst", "SELECT * FROM (SELECT a, b, c FROM t) q WHERE a = b OR a = c OR a = b OR a = c")
}
