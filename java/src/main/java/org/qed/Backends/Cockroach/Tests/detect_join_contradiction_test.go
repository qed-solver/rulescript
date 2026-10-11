package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestDetectJoinContradiction(t *testing.T) {
	rule := loadRule(t, "DetectJoinContradiction")
	e := newEnv(t)
	l, lc := e.scan("u", 1)
	r, rc := e.scan("t", 1)
	falseCond := e.f.ConstructFalse()
	rest := e.f.ConstructEq(e.v(lc[0]), e.v(rc[0]))
	before := e.f.ConstructInnerJoin(l, r, e.filters(falseCond, rest), &memo.JoinPrivate{})
	want := e.f.ConstructInnerJoin(l, r, e.filters(falseCond), &memo.JoinPrivate{})
	e.expectRewrite(rule, before, want)
	e.expectNoMatch(rule, e.f.ConstructInnerJoin(l, r, e.filters(rest, falseCond), &memo.JoinPrivate{}))
	e.expectNoMatch(rule, e.f.ConstructLeftJoin(l, r, e.filters(falseCond, rest), &memo.JoinPrivate{}))
}

func TestSQLDetectJoinContradiction(t *testing.T) {
	expectSQLRewrites(t, "DetectJoinContradiction", "SELECT * FROM (VALUES (1)) AS l(a) JOIN (VALUES (1)) AS r(b) ON FALSE AND a = b")
}
