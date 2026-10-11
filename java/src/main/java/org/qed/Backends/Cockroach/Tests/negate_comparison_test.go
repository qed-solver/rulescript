package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestNegateComparison(t *testing.T) {
	rule := loadRule(t, "NegateComparison")
	e := newEnv(t)
	l, lc := e.scan("u", 1)
	r, rc := e.scan("t", 1)
	join := e.f.ConstructInnerJoin(l, r, nil, &memo.JoinPrivate{})
	eq := e.f.ConstructEq(e.v(lc[0]), e.v(rc[0]))
	before := e.f.ConstructSelect(join, e.filters(e.f.ConstructNot(eq)))
	want := e.f.ConstructSelect(join, e.filters(e.f.ConstructNe(e.v(lc[0]), e.v(rc[0]))))
	e.expectRewrite(rule, before, want)
	e.expectNoMatch(rule, e.f.ConstructSelect(join, e.filters(eq)))
	e.expectNoMatch(rule, e.f.ConstructSelect(join, e.filters(e.f.ConstructNot(e.f.ConstructNe(e.v(lc[0]), e.v(rc[0]))))))
}

func TestSQLNegateComparison(t *testing.T) {
	expectSQLRewrites(t, "NegateComparison", "SELECT a, b FROM (VALUES (1)) AS l(a), (VALUES (2)) AS r(b) WHERE NOT (a = b)")
}
